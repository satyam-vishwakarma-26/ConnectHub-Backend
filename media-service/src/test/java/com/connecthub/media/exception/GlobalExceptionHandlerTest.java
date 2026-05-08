package com.connecthub.media.exception;

import com.connecthub.media.controller.MediaController;
import com.connecthub.media.exception.GlobalExceptionHandler;
import com.connecthub.media.security.AuthenticatedUser;
import com.connecthub.media.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for GlobalExceptionHandler — exercised through the full Spring MVC stack
 * without a real security filter by piggy-backing on MediaController.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler — Unit Tests")
class GlobalExceptionHandlerTest {

        private MockMvc mockMvc;
        private ObjectMapper objectMapper;

        @Mock private MediaService mediaService;
        @InjectMocks private MediaController mediaController;

        private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();
        private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();

    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        validator.afterPropertiesSet();

        AuthenticatedUser user = new AuthenticatedUser(1L, "user@test.com", "USER");
        principal = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

                SecurityContextHolder.getContext().setAuthentication(principal);

        mockMvc = MockMvcBuilders.standaloneSetup(mediaController)
                .setControllerAdvice(globalExceptionHandler)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .build();
        }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
    }

    // ── ResourceNotFoundException → 404 ───────────────────────────────────────

    @Test
    @DisplayName("ResourceNotFoundException → 404 with error message")
    void resourceNotFound_returns404() throws Exception {
        when(mediaService.getFileById(999L))
                .thenThrow(new ResourceNotFoundException("Media file not found: 999"));

        mockMvc.perform(get("/media/999").principal(principal))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Media file not found: 999"));
    }

    // ── BadRequestException → 400 ─────────────────────────────────────────────

    @Test
    @DisplayName("BadRequestException → 400 with error message")
    void badRequest_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.exe", "application/x-msdownload", new byte[10]);

        when(mediaService.uploadImage(any(), anyLong(), anyLong()))
                .thenThrow(new BadRequestException("File type not allowed"));

        mockMvc.perform(multipart("/media/upload/image")
                        .file(file)
                        .param("roomId", "10")
                        .principal(principal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("File type not allowed"));
    }

    // ── ForbiddenException → 403 ──────────────────────────────────────────────

    @Test
    @DisplayName("ForbiddenException → 403 with error message")
    void forbidden_returns403() throws Exception {
        doThrow(new ForbiddenException("You can only delete your own uploaded files."))
                .when(mediaService).deleteFile(anyLong(), anyLong());

        mockMvc.perform(delete("/media/1").principal(principal))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You can only delete your own uploaded files."));
    }

    // ── UnauthorizedException → 401 ───────────────────────────────────────────

    @Test
    @DisplayName("UnauthorizedException → 401 with error message")
    void unauthorized_returns401() throws Exception {
        when(mediaService.getFileById(1L))
                .thenThrow(new UnauthorizedException("Unauthorized access"));

        mockMvc.perform(get("/media/1").principal(principal))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Unauthorized access"));
    }

    // ── Generic Exception → 500 ───────────────────────────────────────────────

    @Test
    @DisplayName("Unexpected Exception → 500 with generic message")
    void genericException_returns500() throws Exception {
        when(mediaService.getFileById(1L))
                .thenThrow(new RuntimeException("Something exploded"));

        mockMvc.perform(get("/media/1").principal(principal))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }

    // ── MethodArgumentNotValidException → 400 with field errors ──────────────

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 with validation errors map")
    void validationFailure_returns400WithErrors() throws Exception {
        // PUT /media/{id}/link with missing messageId triggers @NotNull
        mockMvc.perform(put("/media/1/link")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))   // no messageId field
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.messageId").exists());
    }
}
