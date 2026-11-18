package com.djonique.cashbackadvbot.repository;

import com.djonique.cashbackadvbot.domain.PaymentInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PaymentInvoiceRepository extends JpaRepository<PaymentInvoice, Long> {

    Optional<PaymentInvoice> findByPayload(String payload);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PaymentInvoice i
            SET i.status = :paidStatus, i.paidAt = :paidAt
            WHERE i.payload = :payload
              AND i.status = :createdStatus
            """)
    int markPaid(@Param("payload") String payload,
                 @Param("paidStatus") String paidStatus,
                 @Param("createdStatus") String createdStatus,
                 @Param("paidAt") Instant paidAt);

    /*
     * Переводит брошенные счета в EXPIRED. Условие по статусу защищает
     * уже оплаченные счета от случайной перезаписи.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE PaymentInvoice i
            SET i.status = :expiredStatus
            WHERE i.status = :createdStatus
              AND i.createdAt < :threshold
            """)
    int expireCreatedBefore(@Param("threshold") Instant threshold,
                            @Param("expiredStatus") String expiredStatus,
                            @Param("createdStatus") String createdStatus);
}
