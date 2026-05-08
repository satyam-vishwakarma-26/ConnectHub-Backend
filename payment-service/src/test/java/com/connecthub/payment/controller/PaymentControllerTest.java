package com.connecthub.payment.controller;

import com.connecthub.payment.dto.*;
import com.connecthub.payment.security.JwtUtil;
import com.connecthub.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private PaymentController paymentController;

    private final String mockToken = "Bearer test_token";
    private final Long mockUserId = 1L;

    @BeforeEach
    void setUp() {
    }

    private void mockRequestAndJwt() {
        when(request.getHeader("Authorization")).thenReturn(mockToken);
        when(jwtUtil.extractUserId("test_token")).thenReturn(mockUserId);
    }

    @Test
    void extractUserId_MissingToken() {
        when(request.getHeader("Authorization")).thenReturn(null);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> paymentController.createOrder(new CreateOrderRequest()));
        assertEquals("Missing or malformed Authorization header", ex.getMessage());
    }

    @Test
    void extractUserId_MalformedToken() {
        when(request.getHeader("Authorization")).thenReturn("InvalidToken");
        RuntimeException ex = assertThrows(RuntimeException.class, () -> paymentController.createOrder(new CreateOrderRequest()));
        assertEquals("Missing or malformed Authorization header", ex.getMessage());
    }

    @Test
    void getAllPlans() {
        List<com.connecthub.payment.entity.SubscriptionPlan> plans = List.of(new com.connecthub.payment.entity.SubscriptionPlan());
        when(paymentService.getAllPlans()).thenReturn(plans);

        ResponseEntity<?> response = paymentController.getAllPlans();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(plans, response.getBody());
    }

    @Test
    void createOrder() throws Exception {
        mockRequestAndJwt();
        CreateOrderRequest req = new CreateOrderRequest();
        CreateOrderResponse res = new CreateOrderResponse();
        when(paymentService.createOrder(mockUserId, req, mockToken)).thenReturn(res);

        ResponseEntity<CreateOrderResponse> response = paymentController.createOrder(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(res, response.getBody());
    }

    @Test
    void verifyPayment() {
        mockRequestAndJwt();
        VerifyPaymentRequest req = new VerifyPaymentRequest();
        VerifyPaymentResponse res = new VerifyPaymentResponse();
        when(paymentService.verifyPayment(mockUserId, req, mockToken)).thenReturn(res);

        ResponseEntity<VerifyPaymentResponse> response = paymentController.verifyPayment(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(res, response.getBody());
    }

    @Test
    void getMySubscription() {
        mockRequestAndJwt();
        SubscriptionDTO dto = new SubscriptionDTO();
        when(paymentService.getCurrentSubscription(mockUserId)).thenReturn(dto);

        ResponseEntity<SubscriptionDTO> response = paymentController.getMySubscription();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void getMyLimits() {
        mockRequestAndJwt();
        PlanLimitsDTO dto = new PlanLimitsDTO();
        when(paymentService.getUserPlanLimits(mockUserId)).thenReturn(dto);

        ResponseEntity<PlanLimitsDTO> response = paymentController.getMyLimits();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void getUserLimits() {
        PlanLimitsDTO dto = new PlanLimitsDTO();
        when(paymentService.getUserPlanLimits(mockUserId)).thenReturn(dto);

        ResponseEntity<PlanLimitsDTO> response = paymentController.getUserLimits(mockUserId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void cancelSubscription() {
        mockRequestAndJwt();
        doNothing().when(paymentService).cancelSubscription(mockUserId, mockToken);

        ResponseEntity<Map<String, Object>> response = paymentController.cancelSubscription();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("message"));
    }

    @Test
    void toggleAutoRenew() {
        mockRequestAndJwt();
        doNothing().when(paymentService).toggleAutoRenew(mockUserId, true);

        ResponseEntity<Map<String, Object>> response = paymentController.toggleAutoRenew(true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().get("autoRenew"));
    }

    @Test
    void getPaymentHistory() {
        mockRequestAndJwt();
        List<PaymentHistoryDTO> history = List.of(new PaymentHistoryDTO());
        when(paymentService.getPaymentHistory(mockUserId)).thenReturn(history);

        ResponseEntity<?> response = paymentController.getPaymentHistory();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(history, response.getBody());
    }

    @Test
    void getHealth() {
        ResponseEntity<Map<String, Object>> response = paymentController.getHealth();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
    }
}
