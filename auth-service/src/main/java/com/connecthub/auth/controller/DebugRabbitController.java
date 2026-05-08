package com.connecthub.auth.controller;

import com.connecthub.auth.dto.event.EmailEvent;
import com.connecthub.auth.service.impl.EmailProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.PrintWriter;
import java.io.StringWriter;

@RestController
@RequestMapping("/api/auth/debug")
@RequiredArgsConstructor
public class DebugRabbitController {

    private final EmailProducer emailProducer;

    @GetMapping("/test-rabbit")
    public String testRabbit() {
        try {
            EmailEvent event = EmailEvent.builder()
                    .type(EmailEvent.EmailType.OTP_RESET)
                    .toEmail("test@example.com")
                    .otp("123456")
                    .build();
            emailProducer.publishEmailEvent(event);
            return "SUCCESS";
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            return "ERROR: " + sw.toString();
        }
    }
}
