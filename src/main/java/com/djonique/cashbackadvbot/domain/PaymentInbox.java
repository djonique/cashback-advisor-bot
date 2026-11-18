package com.djonique.cashbackadvbot.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/*
 * Inbox успешного платежа: запись создаётся до бизнес-обработки,
 * чтобы факт оплаты не потерялся при сбое БД или приложения.
 */
@Entity
@Table(name = "payment_inbox")
@Getter
@Setter
@NoArgsConstructor
public class PaymentInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_charge_id", nullable = false, length = 255)
    private String telegramChargeId;

    @Column(name = "telegram_chat_id", nullable = false)
    private Long telegramChatId;

    @Column(name = "username", length = 255)
    private String username;

    @Column(name = "invoice_payload", length = 64)
    private String invoicePayload;

    @Column(name = "provider_charge_id", length = 255)
    private String providerChargeId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    // Заполняется БД через DEFAULT now(): Hibernate не должен писать сюда null.
    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
