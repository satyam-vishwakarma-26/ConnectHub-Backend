package com.connecthub.payment.repository;

import com.connecthub.payment.entity.WebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookLogRepository extends JpaRepository<WebhookLog, Long> {

    Optional<WebhookLog> findByRazorpayEventId(String eventId);

    List<WebhookLog> findByStatus(String status);
}
