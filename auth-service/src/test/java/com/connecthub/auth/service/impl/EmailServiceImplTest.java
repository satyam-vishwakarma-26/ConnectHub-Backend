package com.connecthub.auth.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private EmailProducer emailProducer;

    @Test
    void sendRegistrationOtpEmail_queuesEvent() {
        EmailServiceImpl service = new EmailServiceImpl(emailProducer);

        service.sendRegistrationOtpEmail("user@test.com", "123456");

        verify(emailProducer, times(1)).publishEmailEvent(any());
    }

    @Test
    void sendOtpEmail_queuesEvent() {
        EmailServiceImpl service = new EmailServiceImpl(emailProducer);

        service.sendOtpEmail("reset@test.com", "654321");

        verify(emailProducer, times(1)).publishEmailEvent(any());
    }

    @Test
    void sendWelcomeEmail_queuesEvent() {
        EmailServiceImpl service = new EmailServiceImpl(emailProducer);

        service.sendWelcomeEmail("welcome@test.com", "newuser");

        verify(emailProducer, times(1)).publishEmailEvent(any());
    }

    @Test
    void sendAccountSuspendedEmail_queuesEvent() {
        EmailServiceImpl service = new EmailServiceImpl(emailProducer);

        service.sendAccountSuspendedEmail("suspended@test.com", "suspendedUser");

        verify(emailProducer, times(1)).publishEmailEvent(any());
    }

    @Test
    void sendAccountDeletedEmail_queuesEvent() {
        EmailServiceImpl service = new EmailServiceImpl(emailProducer);

        service.sendAccountDeletedEmail("deleted@test.com", "deletedUser");

        verify(emailProducer, times(1)).publishEmailEvent(any());
    }

    @Test
    void sendAccountDeletionOtpEmail_queuesEvent() {
        EmailServiceImpl service = new EmailServiceImpl(emailProducer);

        service.sendAccountDeletionOtpEmail("delete@test.com", "789012", "deleteUser");

        verify(emailProducer, times(1)).publishEmailEvent(any());
    }

    @Test
    void sendSelfDeletedAccountEmail_queuesEvent() {
        EmailServiceImpl service = new EmailServiceImpl(emailProducer);

        service.sendSelfDeletedAccountEmail("selfdelete@test.com", "selfDeleteUser");

        verify(emailProducer, times(1)).publishEmailEvent(any());
    }
}