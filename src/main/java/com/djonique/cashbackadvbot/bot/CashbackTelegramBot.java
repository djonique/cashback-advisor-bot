package com.djonique.cashbackadvbot.bot;

import com.djonique.cashbackadvbot.bot.handler.CardHandler;
import com.djonique.cashbackadvbot.bot.handler.MenuHandler;
import com.djonique.cashbackadvbot.bot.handler.SetupDialogHandler;
import com.djonique.cashbackadvbot.bot.state.DialogState;
import com.djonique.cashbackadvbot.config.BotProperties;
import com.djonique.cashbackadvbot.domain.PaymentInbox;
import com.djonique.cashbackadvbot.dto.UserRef;
import com.djonique.cashbackadvbot.service.PaymentInboxService;
import com.djonique.cashbackadvbot.service.PaymentService;
import com.djonique.cashbackadvbot.service.UserService;
import com.djonique.cashbackadvbot.service.UserStateService;
import com.djonique.cashbackadvbot.util.LogUtils;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerPreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.payments.PreCheckoutQuery;
import org.telegram.telegrambots.meta.api.objects.payments.SuccessfulPayment;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

import java.util.concurrent.*;
import java.util.stream.IntStream;

@Component
@Slf4j
public class CashbackTelegramBot extends TelegramLongPollingBot {

    private static final String UNKNOWN_TEXT = "Я вас не понял. Нажмите /start";
    private static final String UNKNOWN_COMMAND_TEXT = "Неизвестная команда. Нажмите /start";
    private static final String ERROR_TEXT = "Что-то пошло не так. Попробуйте ещё раз или нажмите /start";
    private static final String PAYMENT_PENDING_TEXT =
            "Платёж получен, но пока не обработан. Мы уже разбираемся — спасибо за поддержку! ❤️";
    private static final String PRIVATE_ONLY_TEXT = "Бот работает только в личных сообщениях";
    private static final String PRIVATE_CHAT_TYPE = "private";
    private static final String DONATION_AMOUNT_INVALID_TEXT =
            "Пожалуйста, введите целое число (например, 150):";
    private static final String DONATION_AMOUNT_TOO_SMALL_TEXT =
            "Минимальная сумма доната — 100 ₽ (ограничение Telegram). Введите другую сумму:";
    private static final String DONATION_AMOUNT_TOO_LARGE_TEXT =
            "Максимальная сумма доната — 100 000 ₽. Введите сумму поменьше.";
    private static final int DONATION_MIN_AMOUNT = 100;
    private static final int DONATION_MAX_AMOUNT = 100_000;
    // Повторы нужны только для записи в inbox: дальше работает fallback-джоба.
    private static final int PAYMENT_RETRY_ATTEMPTS = 3;
    private static final long PAYMENT_RETRY_DELAY_MS = 200L;
    private static final int WORKER_THREADS = 4;
    private static final int WORKER_QUEUE_CAPACITY = 10_000;
    private static final int CHAT_LANES = 32;
    private static final int CHAT_QUEUE_CAPACITY = 64;

    private final BotProperties botProperties;
    private final MenuHandler menuHandler;
    private final SetupDialogHandler setupDialogHandler;
    private final CardHandler cardHandler;
    private final UserService userService;
    private final UserStateService userStateService;
    private final PaymentService paymentService;
    private final PaymentInboxService paymentInboxService;
    private final PaymentNotifier paymentNotifier;
    private final TelegramMessageSender messageSender;
    private final TelegramApiExecutor telegramApiExecutor;

    private final ExecutorService workerPool;
    private final ExecutorService[] chatLanes;

    private volatile BotSession botSession;

    public CashbackTelegramBot(
            BotProperties botProperties,
            MenuHandler menuHandler,
            SetupDialogHandler setupDialogHandler,
            CardHandler cardHandler,
            UserService userService,
            UserStateService userStateService,
            PaymentService paymentService,
            PaymentInboxService paymentInboxService,
            PaymentNotifier paymentNotifier,
            TelegramMessageSender messageSender,
            TelegramApiExecutor telegramApiExecutor) {
        super(botProperties.getBotToken());
        this.botProperties = botProperties;
        this.menuHandler = menuHandler;
        this.setupDialogHandler = setupDialogHandler;
        this.cardHandler = cardHandler;
        this.userService = userService;
        this.userStateService = userStateService;
        this.paymentService = paymentService;
        this.paymentInboxService = paymentInboxService;
        this.paymentNotifier = paymentNotifier;
        this.messageSender = messageSender;
        this.telegramApiExecutor = telegramApiExecutor;
        this.messageSender.bind(this);
        this.workerPool = new ThreadPoolExecutor(
                WORKER_THREADS,
                WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(WORKER_QUEUE_CAPACITY),
                Thread.ofPlatform().name("bot-worker-", 0).daemon().factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        /*
         * Полосы на чат: сохраняют порядок апдейтов одного чата.
         * Очередь ограничена, чтобы всплеск сообщений не привёл к OOM.
         * При переполнении задачу отбрасываем: блокировать общий поток
         * получения апдейтов нельзя — это остановит обработку всех чатов.
         */
        this.chatLanes = IntStream.range(0, CHAT_LANES)
                .mapToObj(i -> new ThreadPoolExecutor(
                        1,
                        1,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<>(CHAT_QUEUE_CAPACITY),
                        Thread.ofPlatform().name("bot-chat-" + i).daemon().factory(),
                        (task, executor) -> log.warn(
                                "Очередь чата переполнена, update отброшен: lane={}", i)))
                .toArray(ExecutorService[]::new);
    }

    public void bindSession(BotSession session) {
        this.botSession = session;
    }

    @PreDestroy
    void shutdown() {
        BotSession session = botSession;
        if (session != null) {
            try {
                session.stop();
                log.info("BotSession остановлена");
            } catch (RuntimeException e) {
                log.warn("Не удалось остановить BotSession: {}", LogUtils.sanitize(e));
            }
        }
        workerPool.shutdownNow();
        for (ExecutorService lane : chatLanes) {
            lane.shutdownNow();
        }
    }

    @Override
    public String getBotUsername() {
        return botProperties.getBotUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            dispatch(update);
        } catch (Exception e) {
            log.error("Не удалось обработать update id={}: {}",
                    update.getUpdateId(), LogUtils.sanitize(e));
            Long chatId = extractChatId(update);
            if (chatId != null) {
                messageSender.send(chatId, ERROR_TEXT, null);
            }
        }
    }

    private void dispatch(Update update) {
        if (update.hasPreCheckoutQuery()) {
            handlePreCheckout(update.getPreCheckoutQuery());
            return;
        }
        if (rejectNonPrivateChat(update)) {
            return;
        }
        Long chatId = extractChatId(update);
        if (chatId == null) {
            workerPool.submit(() -> process(update));
            return;
        }
        int lane = Math.floorMod(chatId.hashCode(), chatLanes.length);
        chatLanes[lane].execute(() -> process(update));
    }

    private void handlePreCheckout(PreCheckoutQuery query) {
        log.info("Получен PreCheckoutQuery: id={}, currency={}, totalAmount={}",
                query.getId(), query.getCurrency(), query.getTotalAmount());
        Long payerChatId = query.getFrom() != null ? query.getFrom().getId() : null;
        boolean valid;
        try {
            valid = paymentService.validatePreCheckout(
                    query.getInvoicePayload(), query.getTotalAmount(), query.getCurrency(), payerChatId);
        } catch (RuntimeException e) {
            log.error("Ошибка проверки PreCheckoutQuery: id={}: {}",
                    query.getId(), LogUtils.sanitize(e));
            answerPreCheckout(query.getId(), false, "Временная ошибка, попробуйте позже");
            return;
        }
        if (valid) {
            answerPreCheckout(query.getId(), true, null);
        } else {
            log.warn("Отклонён PreCheckoutQuery: id={}, currency={}, totalAmount={}",
                    query.getId(), query.getCurrency(), query.getTotalAmount());
            answerPreCheckout(query.getId(), false, "Некорректный платёж");
        }
    }

    private void answerPreCheckout(String queryId, boolean ok, String errorMessage) {
        try {
            telegramApiExecutor.executeWithRetry(this,
                    sender -> sender.execute(new AnswerPreCheckoutQuery(queryId, ok, errorMessage)));
            log.info("Ответ на PreCheckoutQuery отправлен: id={}, ok={}", queryId, ok);
        } catch (TelegramApiException e) {
            log.error("Не удалось ответить на PreCheckoutQuery: id={}: {}",
                    queryId, LogUtils.sanitize(e));
        }
    }

    private void process(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasSuccessfulPayment()) {
                handleSuccessfulPayment(update.getMessage());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleTextMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                menuHandler.handleCallback(update.getCallbackQuery());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки update id={}: {}",
                    update.getUpdateId(), LogUtils.sanitize(e));
            Long chatId = extractChatId(update);
            if (chatId != null) {
                messageSender.send(chatId, ERROR_TEXT, null);
            }
        }
    }

    /*
     * Inbox-first: сначала фиксируем факт оплаты в payment_inbox, и только потом
     * трогаем бизнес-данные. Если запись в inbox не удалась — деньги Telegram уже
     * списал, поэтому пишем CRITICAL и честно сообщаем пользователю о задержке.
     */
    private void handleSuccessfulPayment(Message message) {
        SuccessfulPayment payment = message.getSuccessfulPayment();
        long totalAmount = payment.getTotalAmount() == null ? 0L : payment.getTotalAmount();
        log.info("Успешный платёж: chatId={}, totalAmount={}, currency={}",
                LogUtils.maskChatId(message.getChatId()), totalAmount, payment.getCurrency());

        PaymentInbox inbox = saveToInbox(message, payment, totalAmount);
        if (inbox == null) {
            messageSender.send(message.getChatId(), PAYMENT_PENDING_TEXT, null);
            return;
        }

        boolean registered;
        try {
            registered = paymentService.processPayment(inbox);
        } catch (RuntimeException e) {
            // processed_at останется NULL — платёж подберёт PaymentInboxProcessor.
            log.error("Платёж в inbox есть, но не обработан; повторит фолбэк-джоба: " +
                            "id={}, chargeId={}: {}",
                    inbox.getId(), inbox.getTelegramChargeId(), LogUtils.sanitize(e));
            messageSender.send(message.getChatId(), PAYMENT_PENDING_TEXT, null);
            return;
        }

        try {
            paymentInboxService.markProcessed(inbox.getId());
        } catch (RuntimeException e) {
            // Деньги уже учтены; просроченную отметку снимет фолбэк-джоба.
            log.error("Не удалось отметить платёж обработанным: id={}, chargeId={}: {}",
                    inbox.getId(), inbox.getTelegramChargeId(), LogUtils.sanitize(e));
        }

        if (registered) {
            paymentNotifier.sendThanks(message.getChatId(), payment.getCurrency(),
                    payment.getTelegramPaymentChargeId());
        } else {
            log.info("Повторный или отложенный successful_payment: chatId={}, chargeId={}",
                    LogUtils.maskChatId(message.getChatId()),
                    payment.getTelegramPaymentChargeId());
        }
    }

    /*
     * Идемпотентная запись в inbox (insert ... on conflict do nothing).
     * Повтор — только здесь: если упала БД, лучший шанс сохранить платёж —
     * короткая серия повторов прямо сейчас, а не через минуту.
     */
    private PaymentInbox saveToInbox(Message message, SuccessfulPayment payment, long totalAmount) {
        String chargeId = payment.getTelegramPaymentChargeId();
        for (int attempt = 1; attempt <= PAYMENT_RETRY_ATTEMPTS; attempt++) {
            try {
                return paymentInboxService.save(
                        message.getChatId(),
                        username(message),
                        chargeId,
                        payment.getProviderPaymentChargeId(),
                        totalAmount,
                        payment.getCurrency(),
                        payment.getInvoicePayload());
            } catch (RuntimeException e) {
                log.error("Не удалось записать платёж в inbox (попытка {}/{}): chatId={}, chargeId={}: {}",
                        attempt, PAYMENT_RETRY_ATTEMPTS,
                        LogUtils.maskChatId(message.getChatId()), chargeId, LogUtils.sanitize(e));
                if (attempt < PAYMENT_RETRY_ATTEMPTS) {
                    sleepQuietly();
                }
            }
        }
        // Худший сценарий: факт оплаты есть только в Telegram — нужна ручная сверка.
        log.error("КРИТИЧНО: платёж не попал в payment_inbox после {} попыток — " +
                        "сверьте вручную в Telegram: chatId={}, chargeId={}, amount={}, currency={}, payload={}",
                PAYMENT_RETRY_ATTEMPTS,
                LogUtils.maskChatId(message.getChatId()),
                chargeId,
                totalAmount,
                payment.getCurrency(),
                payment.getInvoicePayload());
        return null;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(PAYMENT_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleTextMessage(Message message) {
        String text = message.getText().trim();
        String command = resolveCommand(text);
        if (command != null) {
            menuHandler.handleCommand(command, message.getChatId(), username(message));
            return;
        }
        if (text.startsWith("/")) {
            messageSender.send(message.getChatId(), UNKNOWN_COMMAND_TEXT, null);
            return;
        }

        UserRef user = userService.findOrCreate(message.getChatId(), username(message));
        DialogState state = userStateService.getState(user.id());

        if (state == DialogState.ENTERING_DONATION_AMOUNT) {
            handleDonationAmountInput(user, message.getChatId(), text);
        } else if (state == DialogState.CARD_INPUT_MCC
                || state == DialogState.CARD_INPUT_CATEGORY
                || state == DialogState.CARD_INPUT_STORE
                || state == DialogState.IDLE) {
            cardHandler.handleInput(user, text, state);
        } else if (state == DialogState.ENTERING_PERCENT) {
            setupDialogHandler.handlePercentInput(user, text);
        } else {
            messageSender.send(message.getChatId(), UNKNOWN_TEXT, null);
        }
    }

    private void handleDonationAmountInput(UserRef user, Long chatId, String text) {
        int amount;
        try {
            amount = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            messageSender.send(chatId, DONATION_AMOUNT_INVALID_TEXT, null);
            return;
        }

        if (amount < DONATION_MIN_AMOUNT) {
            messageSender.send(chatId, DONATION_AMOUNT_TOO_SMALL_TEXT, null);
            return;
        }
        if (amount > DONATION_MAX_AMOUNT) {
            messageSender.send(chatId, DONATION_AMOUNT_TOO_LARGE_TEXT, null);
            return;
        }

        if (!paymentService.sendFiatInvoice(chatId, "Донат " + amount + " руб", amount, this)) {
            messageSender.send(chatId, ERROR_TEXT, null);
            return;
        }
        userStateService.clearState(user.id());
    }

    private boolean rejectNonPrivateChat(Update update) {
        Message message = extractMessage(update);
        if (message == null || message.getChat() == null) {
            return false;
        }
        String chatType = message.getChat().getType();
        if (PRIVATE_CHAT_TYPE.equals(chatType)) {
            return false;
        }
        log.warn("Отклонено сообщение из неподдерживаемого чата: chatId={}, type={}",
                LogUtils.maskChatId(message.getChatId()), chatType);
        messageSender.send(message.getChatId(), PRIVATE_ONLY_TEXT, null);
        return true;
    }

    private Message extractMessage(Update update) {
        if (update.hasMessage()) {
            return update.getMessage();
        }
        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            if (callbackQuery.getMessage() instanceof Message message) {
                return message;
            }
        }
        return null;
    }

    private String resolveCommand(String text) {
        String command = text;
        int mentionIndex = command.indexOf('@');
        if (command.startsWith("/") && mentionIndex > 0) {
            command = command.substring(0, mentionIndex);
        }

        return switch (command) {
            case "/start" -> "start";
            case "/menu", BotButtons.MAIN_MENU -> "menu";
            case "/settings", BotButtons.SETUP -> "settings";
            case "/help", BotButtons.HELP -> "help";
            case "/donate", BotButtons.SUPPORT -> "donate";
            case "/share" -> "share";
            default -> null;
        };
    }

    private String username(Message message) {
        return message.getFrom() != null ? message.getFrom().getUserName() : null;
    }

    private Long extractChatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return null;
    }
}
