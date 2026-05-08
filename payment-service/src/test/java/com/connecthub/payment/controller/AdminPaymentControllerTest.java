package com.connecthub.payment.controller;

import com.connecthub.payment.dto.PaymentAnalyticsDTO;
import com.connecthub.payment.entity.UserSubscription;
import com.connecthub.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private AdminPaymentController adminPaymentController;

    @Test
    void getAnalytics() {
        PaymentAnalyticsDTO mockDto = new PaymentAnalyticsDTO();
        when(paymentService.getPaymentAnalytics()).thenReturn(mockDto);

        ResponseEntity<PaymentAnalyticsDTO> response = adminPaymentController.getAnalytics();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockDto, response.getBody());
        verify(paymentService, times(1)).getPaymentAnalytics();
    }

    @Test
    void getAllSubscriptions() {
        List<UserSubscription> mockList = List.of(new UserSubscription());
        when(paymentService.getAllSubscriptions()).thenReturn(mockList);

        ResponseEntity<List<UserSubscription>> response = adminPaymentController.getAllSubscriptions();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(mockList, response.getBody());
        verify(paymentService, times(1)).getAllSubscriptions();
    }
}
