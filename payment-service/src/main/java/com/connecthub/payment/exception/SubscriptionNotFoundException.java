package com.connecthub.payment.exception;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException(Long userId) {
        super("No active subscription for user: " + userId);
    }
}
