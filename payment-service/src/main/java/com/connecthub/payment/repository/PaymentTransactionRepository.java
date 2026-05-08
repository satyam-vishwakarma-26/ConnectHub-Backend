package com.connecthub.payment.repository;

import com.connecthub.payment.entity.PaymentTransaction;
import com.connecthub.payment.entity.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PaymentTransaction> findByRazorpayOrderId(String orderId);

    Optional<PaymentTransaction> findByRazorpayPaymentId(String paymentId);

    List<PaymentTransaction> findByStatus(TransactionStatus status);

    long countByStatus(TransactionStatus status);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM PaymentTransaction t WHERE t.status = :status")
    Double sumAmountByStatus(@Param("status") TransactionStatus status);
}
