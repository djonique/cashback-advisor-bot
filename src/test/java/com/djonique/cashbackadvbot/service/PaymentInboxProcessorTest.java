package com.djonique.cashbackadvbot.service;

import com.djonique.cashbackadvbot.bot.PaymentNotifier;
import com.djonique.cashbackadvbot.domain.PaymentInbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentInboxProcessorTest {

    @Mock
    private PaymentInboxService paymentInboxService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentNotifier paymentNotifier;

    @InjectMocks
    private PaymentInboxProcessor processor;

    @Test
    void shouldMarkProcessedAndThank_whenPaymentRegisteredFirstTime() {
        PaymentInbox inbox = inbox(10L, 555L, "charge-1", "RUB");
        when(paymentInboxService.findUnprocessed(anyInt())).thenReturn(List.of(inbox));
        when(paymentService.processPayment(inbox)).thenReturn(true);

        processor.processPending();

        verify(paymentInboxService).markProcessed(10L);
        verify(paymentNotifier).sendThanks(555L, "RUB", "charge-1");
    }

    @Test
    void shouldMarkProcessedWithoutThanks_whenPaymentWasAlreadyRegistered() {
        PaymentInbox inbox = inbox(10L, 555L, "charge-1", "RUB");
        when(paymentInboxService.findUnprocessed(anyInt())).thenReturn(List.of(inbox));
        when(paymentService.processPayment(inbox)).thenReturn(false);

        processor.processPending();

        verify(paymentInboxService).markProcessed(10L);
        verifyNoInteractions(paymentNotifier);
    }

    @Test
    void shouldKeepInboxUnprocessed_whenProcessingFails() {
        PaymentInbox inbox = inbox(10L, 555L, "charge-1", "RUB");
        when(paymentInboxService.findUnprocessed(anyInt())).thenReturn(List.of(inbox));
        when(paymentService.processPayment(inbox))
                .thenThrow(new RuntimeException("db is down"));

        processor.processPending();

        // processed_at не ставим — джоба повторит попытку через минуту.
        verify(paymentInboxService, never()).markProcessed(anyLong());
        verifyNoInteractions(paymentNotifier);
    }

    @Test
    void shouldContinueWithNextPayment_whenOneFails() {
        PaymentInbox broken = inbox(10L, 555L, "charge-1", "RUB");
        PaymentInbox healthy = inbox(11L, 666L, "charge-2", "XTR");
        when(paymentInboxService.findUnprocessed(anyInt())).thenReturn(List.of(broken, healthy));
        when(paymentService.processPayment(broken)).thenThrow(new RuntimeException("boom"));
        when(paymentService.processPayment(healthy)).thenReturn(true);

        processor.processPending();

        verify(paymentInboxService).markProcessed(11L);
        verify(paymentInboxService, never()).markProcessed(10L);
        verify(paymentNotifier).sendThanks(666L, "XTR", "charge-2");
    }

    @Test
    void shouldNotTouchAnything_whenNoPendingPayments() {
        when(paymentInboxService.findUnprocessed(anyInt())).thenReturn(List.of());

        processor.processPending();

        verifyNoInteractions(paymentService, paymentNotifier);
        verify(paymentInboxService, never()).markProcessed(anyLong());
    }

    private PaymentInbox inbox(Long id, Long chatId, String chargeId, String currency) {
        PaymentInbox inbox = new PaymentInbox();
        inbox.setId(id);
        inbox.setTelegramChatId(chatId);
        inbox.setTelegramChargeId(chargeId);
        inbox.setCurrency(currency);
        inbox.setAmount(10_000L);
        return inbox;
    }
}
