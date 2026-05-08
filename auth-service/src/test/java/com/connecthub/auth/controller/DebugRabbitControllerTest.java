package com.connecthub.auth.controller;

import com.connecthub.auth.dto.event.EmailEvent;
import com.connecthub.auth.service.impl.EmailProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DebugRabbitControllerTest {

    @Mock
    private EmailProducer emailProducer;

    @Test
    void testRabbit_returnsSuccess() {
        DebugRabbitController controller = new DebugRabbitController(emailProducer);

        String result = controller.testRabbit();

        assertEquals("SUCCESS", result);
        verify(emailProducer, times(1)).publishEmailEvent(any(EmailEvent.class));
    }
}