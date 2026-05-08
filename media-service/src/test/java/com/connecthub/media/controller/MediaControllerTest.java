package com.connecthub.media.controller;

import com.connecthub.media.dto.request.LinkMessageRequest;
import com.connecthub.media.dto.response.MediaFileResponse;
import com.connecthub.media.dto.response.PresignedUrlResponse;
import com.connecthub.media.exception.BadRequestException;
import com.connecthub.media.exception.ForbiddenException;
import com.connecthub.media.exception.GlobalExceptionHandler;
import com.connecthub.media.exception.ResourceNotFoundException;
import com.connecthub.media.security.AuthenticatedUser;
import com.connecthub.media.service.MediaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer unit tests for MediaController.
 * Security filters are disabled — authentication is injected via MockMvc's
 * {@code .with(user(...))} support.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MediaController — Unit Tests")
class MediaControllerTest {

        private MockMvc      mockMvc;
        private ObjectMapper objectMapper;

        @Mock private MediaService mediaService;
        @InjectMocks private MediaController mediaController;

        private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();
        private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();

    // ── shared fixtures ────────────────────────────────────────────────────────

    private AuthenticatedUser regularUser;
    private AuthenticatedUser adminUser;
    private MediaFileResponse sampleImageResponse;
    private MediaFileResponse sampleFileResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        validator.afterPropertiesSet();

        regularUser = new AuthenticatedUser(100L, "user@test.com", "USER");
        adminUser   = new AuthenticatedUser(1L,   "admin@test.com", "PLATFORM_ADMIN");

                SecurityContextHolder.getContext().setAuthentication(authToken(regularUser));

        mockMvc = MockMvcBuilders.standaloneSetup(mediaController)
                .setControllerAdvice(globalExceptionHandler)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .build();

        sampleImageResponse = MediaFileResponse.builder()
                .id(1L).uploaderId(100L).roomId(10L)
                .filename("photo.jpg").originalName("photo.jpg")
                .url("https://cdn.example.com/photo.jpg")
                .thumbnailUrl("https://cdn.example.com/thumb.jpg")
                .mimeType("image/jpeg").sizeKb(512L)
                .mediaType("IMAGE").width(1920).height(1080)
                .uploadedAt(LocalDateTime.now())
                .build();

        sampleFileResponse = MediaFileResponse.builder()
                .id(2L).uploaderId(100L).roomId(10L)
                .filename("doc.pdf").originalName("doc.pdf")
                .url("https://cdn.example.com/doc.pdf")
                .mimeType("application/pdf").sizeKb(1024L)
                .mediaType("FILE")
                .uploadedAt(LocalDateTime.now())
                .build();
    }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

    // ── helpers ────────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken authToken(AuthenticatedUser u) {
        return new UsernamePasswordAuthenticationToken(
                u, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole())));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /media/upload/image
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /media/upload/image")
    class UploadImage {

        @Test
        @DisplayName("201 Created — valid image upload")
        void success() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", new byte[100]);

            when(mediaService.uploadImage(any(), eq(100L), eq(10L)))
                    .thenReturn(sampleImageResponse);

            mockMvc.perform(multipart("/media/upload/image")
                            .file(file)
                            .param("roomId", "10")
                            .with(request -> { request.setAttribute(
                                    "SPRING_SECURITY_CONTEXT_ATTR",
                                    SecurityContextHolder.createEmptyContext()); return request; })
                            .principal(authToken(regularUser)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Image uploaded"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.mediaType").value("IMAGE"))
                    .andExpect(jsonPath("$.data.thumbnailUrl").exists());

            verify(mediaService).uploadImage(any(), eq(100L), eq(10L));
        }

        @Test
        @DisplayName("400 Bad Request — service throws BadRequestException")
        void badMimeType_returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "hack.exe", "application/x-msdownload", new byte[10]);

            when(mediaService.uploadImage(any(), anyLong(), anyLong()))
                    .thenThrow(new BadRequestException("File type not allowed"));

            mockMvc.perform(multipart("/media/upload/image")
                            .file(file)
                            .param("roomId", "10")
                            .principal(authToken(regularUser)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /media/upload/file
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /media/upload/file")
    class UploadFile {

        @Test
        @DisplayName("201 Created — valid document upload")
        void success() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.pdf", "application/pdf", new byte[200]);

            when(mediaService.uploadFile(any(), eq(100L), eq(10L)))
                    .thenReturn(sampleFileResponse);

            mockMvc.perform(multipart("/media/upload/file")
                            .file(file)
                            .param("roomId", "10")
                            .principal(authToken(regularUser)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("File uploaded"))
                    .andExpect(jsonPath("$.data.mediaType").value("FILE"));

            verify(mediaService).uploadFile(any(), eq(100L), eq(10L));
        }

        @Test
        @DisplayName("400 — service throws BadRequestException")
        void badFile_returns400() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);

            when(mediaService.uploadFile(any(), anyLong(), anyLong()))
                    .thenThrow(new BadRequestException("File must not be empty."));

            mockMvc.perform(multipart("/media/upload/file")
                            .file(file)
                            .param("roomId", "10")
                            .principal(authToken(regularUser)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/{mediaId}
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/{mediaId}")
    class GetFileById {

        @Test
        @DisplayName("200 OK — media found")
        void found() throws Exception {
            when(mediaService.getFileById(1L)).thenReturn(sampleImageResponse);

            mockMvc.perform(get("/media/1").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.url").value("https://cdn.example.com/photo.jpg"));
        }

        @Test
        @DisplayName("404 Not Found — service throws ResourceNotFoundException")
        void notFound() throws Exception {
            when(mediaService.getFileById(99L))
                    .thenThrow(new ResourceNotFoundException("Media file not found: 99"));

            mockMvc.perform(get("/media/99").principal(authToken(regularUser)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Media file not found: 99"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/room/{roomId}
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/room/{roomId}")
    class GetFilesByRoom {

        @Test
        @DisplayName("200 OK — returns list of files")
        void success() throws Exception {
            when(mediaService.getFilesByRoom(10L))
                    .thenReturn(List.of(sampleImageResponse, sampleFileResponse));

            mockMvc.perform(get("/media/room/10").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("200 OK — empty room returns empty list")
        void emptyRoom() throws Exception {
            when(mediaService.getFilesByRoom(10L)).thenReturn(List.of());

            mockMvc.perform(get("/media/room/10").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/room/{roomId}/images
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/room/{roomId}/images")
    class GetImagesByRoom {

        @Test
        @DisplayName("200 OK — returns image-only list")
        void success() throws Exception {
            when(mediaService.getImagesByRoom(10L)).thenReturn(List.of(sampleImageResponse));

            mockMvc.perform(get("/media/room/10/images").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].mediaType").value("IMAGE"));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/uploader/{uploaderId}
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/uploader/{uploaderId}")
    class GetFilesByUploader {

        @Test
        @DisplayName("200 OK — returns files for uploader")
        void success() throws Exception {
            when(mediaService.getFilesByUploader(100L))
                    .thenReturn(List.of(sampleImageResponse));

            mockMvc.perform(get("/media/uploader/100").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/{mediaId}/download
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/{mediaId}/download")
    class GeneratePresignedUrl {

        @Test
        @DisplayName("200 OK — returns presigned URL response")
        void success() throws Exception {
            PresignedUrlResponse presigned = PresignedUrlResponse.builder()
                    .mediaId(1L)
                    .presignedUrl("https://cdn.example.com/photo.jpg")
                    .expiryHours(24L)
                    .filename("photo.jpg")
                    .build();

            when(mediaService.generatePresignedUrl(1L, 100L)).thenReturn(presigned);

            mockMvc.perform(get("/media/1/download").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.mediaId").value(1))
                    .andExpect(jsonPath("$.data.presignedUrl").value("https://cdn.example.com/photo.jpg"))
                    .andExpect(jsonPath("$.data.expiryHours").value(24));
        }

        @Test
        @DisplayName("404 — media not found")
        void notFound() throws Exception {
            when(mediaService.generatePresignedUrl(99L, 100L))
                    .thenThrow(new ResourceNotFoundException("Media file not found: 99"));

            mockMvc.perform(get("/media/99/download").principal(authToken(regularUser)))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /media/{mediaId}/link
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /media/{mediaId}/link")
    class LinkToMessage {

        @Test
        @DisplayName("200 OK — links media to message")
        void success() throws Exception {
            MediaFileResponse linked = MediaFileResponse.builder()
                    .id(1L).messageId(500L).mediaType("IMAGE")
                    .url("https://cdn.example.com/photo.jpg")
                    .mimeType("image/jpeg").sizeKb(512L)
                    .uploaderId(100L).roomId(10L)
                    .filename("photo.jpg").originalName("photo.jpg")
                    .build();

            when(mediaService.linkToMessage(1L, 500L)).thenReturn(linked);

            LinkMessageRequest req = new LinkMessageRequest();
            req.setMessageId(500L);

            mockMvc.perform(put("/media/1/link")
                            .principal(authToken(regularUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Media linked to message"))
                    .andExpect(jsonPath("$.data.messageId").value(500));
        }

        @Test
        @DisplayName("400 — missing messageId in body (validation)")
        void missingMessageId_returns400() throws Exception {
            // Sending empty body — messageId @NotNull should trigger 400
            mockMvc.perform(put("/media/1/link")
                            .principal(authToken(regularUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("404 — media not found")
        void notFound() throws Exception {
            when(mediaService.linkToMessage(99L, 1L))
                    .thenThrow(new ResourceNotFoundException("Media file not found: 99"));

            LinkMessageRequest req = new LinkMessageRequest();
            req.setMessageId(1L);

            mockMvc.perform(put("/media/99/link")
                            .principal(authToken(regularUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /media/{mediaId}
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /media/{mediaId}")
    class DeleteFile {

        @Test
        @DisplayName("200 OK — owner deletes file")
        void success() throws Exception {
            doNothing().when(mediaService).deleteFile(1L, 100L);

            mockMvc.perform(delete("/media/1").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("File deleted"));

            verify(mediaService).deleteFile(1L, 100L);
        }

        @Test
        @DisplayName("403 — non-owner triggers ForbiddenException")
        void nonOwner_403() throws Exception {
            doThrow(new ForbiddenException("You can only delete your own uploaded files."))
                    .when(mediaService).deleteFile(1L, 100L);

            mockMvc.perform(delete("/media/1").principal(authToken(regularUser)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("404 — file not found")
        void notFound_404() throws Exception {
            doThrow(new ResourceNotFoundException("Media file not found: 99"))
                    .when(mediaService).deleteFile(99L, 100L);

            mockMvc.perform(delete("/media/99").principal(authToken(regularUser)))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/room/{roomId}/count
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/room/{roomId}/count")
    class GetFileCount {

        @Test
        @DisplayName("200 OK — returns count map")
        void success() throws Exception {
            when(mediaService.getFileCount(10L)).thenReturn(7L);

            mockMvc.perform(get("/media/room/10/count").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.roomId").value(10))
                    .andExpect(jsonPath("$.data.fileCount").value(7));
        }

        @Test
        @DisplayName("200 OK — zero count")
        void zeroCount() throws Exception {
            when(mediaService.getFileCount(10L)).thenReturn(0L);

            mockMvc.perform(get("/media/room/10/count").principal(authToken(regularUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fileCount").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/admin/all
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/admin/all  [PLATFORM_ADMIN]")
    class AdminGetAllFiles {

        @Test
        @DisplayName("200 OK — admin retrieves all files")
        void success() throws Exception {
            when(mediaService.getAllFiles())
                    .thenReturn(List.of(sampleImageResponse, sampleFileResponse));

            mockMvc.perform(get("/media/admin/all").principal(authToken(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));

            verify(mediaService).getAllFiles();
        }

        @Test
        @DisplayName("200 OK — no files in system")
        void empty() throws Exception {
            when(mediaService.getAllFiles()).thenReturn(List.of());

            mockMvc.perform(get("/media/admin/all").principal(authToken(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /media/admin/{mediaId}
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /media/admin/{mediaId}  [PLATFORM_ADMIN]")
    class AdminDeleteFile {

        @Test
        @DisplayName("200 OK — admin force-deletes file")
        void success() throws Exception {
            doNothing().when(mediaService).adminDeleteFile(1L);

            mockMvc.perform(delete("/media/admin/1").principal(authToken(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("File deleted by admin"));

            verify(mediaService).adminDeleteFile(1L);
        }

        @Test
        @DisplayName("404 — file not found for admin delete")
        void notFound() throws Exception {
            doThrow(new ResourceNotFoundException("Media file not found: 55"))
                    .when(mediaService).adminDeleteFile(55L);

            mockMvc.perform(delete("/media/admin/55").principal(authToken(adminUser)))
                    .andExpect(status().isNotFound());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /media/admin/storage
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /media/admin/storage  [PLATFORM_ADMIN]")
    class AdminGetStorage {

        @Test
        @DisplayName("200 OK — returns storage map with KB, MB, GB")
        void success() throws Exception {
            // 2 GB worth of KB
            when(mediaService.getTotalStorageKb()).thenReturn(2_097_152L);

            mockMvc.perform(get("/media/admin/storage").principal(authToken(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalStorageKb").value(2_097_152))
                    .andExpect(jsonPath("$.data.totalStorageMb").value(2048))
                    .andExpect(jsonPath("$.data.totalStorageGb").value(2));
        }

        @Test
        @DisplayName("200 OK — zero storage")
        void zeroStorage() throws Exception {
            when(mediaService.getTotalStorageKb()).thenReturn(0L);

            mockMvc.perform(get("/media/admin/storage").principal(authToken(adminUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalStorageKb").value(0));
        }
    }
}
