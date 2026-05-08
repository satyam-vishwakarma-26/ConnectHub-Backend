package com.connecthub.media.controller;

import com.connecthub.media.dto.request.LinkMessageRequest;
import com.connecthub.media.dto.response.ApiResponse;
import com.connecthub.media.dto.response.MediaFileResponse;
import com.connecthub.media.dto.response.PresignedUrlResponse;
import com.connecthub.media.security.AuthenticatedUser;
import com.connecthub.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "File/image upload to S3, thumbnail generation, gallery, pre-signed URLs")
@SecurityRequirement(name = "bearerAuth")
public class MediaController {

    private final MediaService mediaService;

    // ────────────────────────────────────────────────────────
    // UPLOAD
    // ────────────────────────────────────────────────────────

    /**
     * POST /api/media/upload/image?roomId={roomId}
     *
     * Upload flow:
     *  1. Client uploads image (multipart/form-data)
     *  2. Service stores original to Cloudinary under connecthub/images/{uploaderId}/
     *  3. Thumbnailator generates thumbnail, stored under connecthub/thumbnails/{uploaderId}/
     *  4. MediaFile record persisted; response contains url + thumbnailUrl (Cloudinary CDN URLs)
     *  5. Client sends a STOMP CHAT_MESSAGE with type=IMAGE and mediaUrl from this response
     */
    @PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an image (JPEG/PNG/GIF/WebP) — auto-generates thumbnail")
    public ResponseEntity<ApiResponse<MediaFileResponse>> uploadImage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") Long roomId) {

        MediaFileResponse response = mediaService.uploadImage(file, me.getUserId(), roomId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Image uploaded", response));
    }

    /**
     * POST /api/media/upload/file?roomId={roomId}
     *
     * Upload any document (PDF, DOCX, ZIP) or generic file.
     * No thumbnail is generated; type=FILE in the STOMP message.
     */
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document or binary file (PDF/DOCX/ZIP)")
    public ResponseEntity<ApiResponse<MediaFileResponse>> uploadFile(
            @AuthenticationPrincipal AuthenticatedUser me,
            @RequestParam("file") MultipartFile file,
            @RequestParam("roomId") Long roomId) {

        MediaFileResponse response = mediaService.uploadFile(file, me.getUserId(), roomId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("File uploaded", response));
    }

    // ────────────────────────────────────────────────────────
    // FETCH
    // ────────────────────────────────────────────────────────

    @GetMapping("/{mediaId}")
    @Operation(summary = "Get media file metadata by ID")
    public ResponseEntity<ApiResponse<MediaFileResponse>> getFileById(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long mediaId) {

        return ResponseEntity.ok(ApiResponse.success(mediaService.getFileById(mediaId)));
    }

    /**
     * GET /api/media/room/{roomId}
     *
     * Returns all media (images + files) for a room — the shared media gallery.
     */
    @GetMapping("/room/{roomId}")
    @Operation(summary = "Get all media shared in a room (gallery view)")
    public ResponseEntity<ApiResponse<List<MediaFileResponse>>> getFilesByRoom(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        return ResponseEntity.ok(ApiResponse.success(mediaService.getFilesByRoom(roomId)));
    }

    @GetMapping("/room/{roomId}/images")
    @Operation(summary = "Get images-only gallery for a room")
    public ResponseEntity<ApiResponse<List<MediaFileResponse>>> getImagesByRoom(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        return ResponseEntity.ok(ApiResponse.success(mediaService.getImagesByRoom(roomId)));
    }

    @GetMapping("/uploader/{uploaderId}")
    @Operation(summary = "Get all files uploaded by a specific user")
    public ResponseEntity<ApiResponse<List<MediaFileResponse>>> getFilesByUploader(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long uploaderId) {

        return ResponseEntity.ok(ApiResponse.success(mediaService.getFilesByUploader(uploaderId)));
    }

    // ────────────────────────────────────────────────────────
    // PRE-SIGNED DOWNLOAD URL
    // ────────────────────────────────────────────────────────

    /**
     * GET /api/media/{mediaId}/download
     *
     * Generates a time-limited pre-signed S3 URL (24h default) for downloading the file.
     * S3 objects are private; the pre-signed URL grants temporary read access.
     */
    @GetMapping("/{mediaId}/download")
    @Operation(summary = "Generate a pre-signed S3 download URL (24-hour expiry)")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> generatePresignedUrl(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long mediaId) {

        PresignedUrlResponse response = mediaService.generatePresignedUrl(mediaId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ────────────────────────────────────────────────────────
    // LINK TO MESSAGE
    // ────────────────────────────────────────────────────────

    /**
     * PUT /api/media/{mediaId}/link
     *
     * Associates a previously uploaded MediaFile with its chat message.
     * Called by message-service (or websocket-handler) immediately after sendMessage().
     */
    @PutMapping("/{mediaId}/link")
    @Operation(summary = "Link a media file to its chat message (called by message-service)")
    public ResponseEntity<ApiResponse<MediaFileResponse>> linkToMessage(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long mediaId,
            @Valid @RequestBody LinkMessageRequest request) {

        MediaFileResponse response = mediaService.linkToMessage(mediaId, request.getMessageId());
        return ResponseEntity.ok(ApiResponse.success("Media linked to message", response));
    }

    // ────────────────────────────────────────────────────────
    // DELETE
    // ────────────────────────────────────────────────────────

    @DeleteMapping("/{mediaId}")
    @Operation(summary = "Delete a file from S3 and DB — uploader only")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long mediaId) {

        mediaService.deleteFile(mediaId, me.getUserId());
        return ResponseEntity.ok(ApiResponse.success("File deleted", null));
    }

    // ────────────────────────────────────────────────────────
    // STATS
    // ────────────────────────────────────────────────────────

    @GetMapping("/room/{roomId}/count")
    @Operation(summary = "Get total file count for a room")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFileCount(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable Long roomId) {

        long count = mediaService.getFileCount(roomId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("roomId", roomId, "fileCount", count)));
    }

    // ────────────────────────────────────────────────────────
    // PLATFORM ADMIN
    // ────────────────────────────────────────────────────────

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — list all uploaded media files")
    public ResponseEntity<ApiResponse<List<MediaFileResponse>>> getAllFiles() {
        return ResponseEntity.ok(ApiResponse.success(mediaService.getAllFiles()));
    }

    @DeleteMapping("/admin/{mediaId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — force delete any media file")
    public ResponseEntity<ApiResponse<Void>> adminDeleteFile(@PathVariable Long mediaId) {
        mediaService.adminDeleteFile(mediaId);
        return ResponseEntity.ok(ApiResponse.success("File deleted by admin", null));
    }

    @GetMapping("/admin/storage")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @Operation(summary = "Admin — total platform storage used in KB")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTotalStorage() {
        long totalKb = mediaService.getTotalStorageKb();
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("totalStorageKb", totalKb,
                       "totalStorageMb", totalKb / 1024,
                       "totalStorageGb", totalKb / (1024 * 1024))));
    }
}
