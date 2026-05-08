package com.connecthub.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "webhook_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long webhookId;

    @Column(nullable = false)
    private String eventType;

    private String razorpayEventId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Builder.Default
    private String status = "RECEIVED";

    private LocalDateTime processedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
