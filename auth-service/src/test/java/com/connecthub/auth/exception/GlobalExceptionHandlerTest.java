package com.connecthub.auth.exception;

import com.connecthub.auth.dto.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_Returns404() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleNotFound(new ResourceNotFoundException("User not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found", response.getBody().getMessage());
    }

    @Test
    void handleDuplicate_Returns409() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleDuplicate(new DuplicateResourceException("Email exists"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void handleInvalidCreds_Returns401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidCreds(new InvalidCredentialsException("Bad credentials"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void handleInvalidToken_Returns401() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleInvalidToken(new InvalidTokenException("Token expired"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void handleSuspended_Returns403() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleSuspended(new AccountSuspendedException("Account suspended"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void handleBadRequest_Returns400() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBadRequest(new BadRequestException("Invalid input"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void handleAccessDenied_Returns403() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleAccessDenied(new AccessDeniedException("No permission"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void handleGeneral_Returns500() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleGeneral(new RuntimeException("Unexpected error"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}