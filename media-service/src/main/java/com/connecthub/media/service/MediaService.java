package com.connecthub.media.service;

import com.connecthub.media.dto.response.MediaFileResponse;
import com.connecthub.media.dto.response.PresignedUrlResponse;
import com.connecthub.media.entity.MediaFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MediaService {

    // ── Upload ─────────────────────────────────────────────

    /**
     * Uploads an image to S3, generates a thumbnail, and persists a MediaFile record.
     * Allowed types: image/jpeg, image/png, image/gif, image/webp.
     */
    MediaFileResponse uploadImage(MultipartFile file, Long uploaderId, Long roomId);

    /**
     * Uploads a document or binary file to S3 and persists a MediaFile record.
     * Allowed types: application/pdf, application/vnd.openxmlformats-*, application/zip.
     */
    MediaFileResponse uploadFile(MultipartFile file, Long uploaderId, Long roomId);

    // ── Fetch ──────────────────────────────────────────────

    MediaFileResponse getFileById(Long mediaId);

    List<MediaFileResponse> getFilesByRoom(Long roomId);

    List<MediaFileResponse> getImagesByRoom(Long roomId);

    List<MediaFileResponse> getFilesByUploader(Long uploaderId);

    List<MediaFileResponse> getAllFiles();

    // ── Pre-signed URL ─────────────────────────────────────

    /**
     * Generates a time-limited pre-signed S3 URL for downloading a private file.
     * Expiry is controlled by {@code app.aws.s3.presigned-url-expiry-hours}.
     */
    PresignedUrlResponse generatePresignedUrl(Long mediaId, Long requesterId);

    // ── Metadata update ────────────────────────────────────

    /**
     * Associates a media file with its chat message after the message is created.
     * Called by message-service (or websocket-handler) immediately after send.
     */
    MediaFileResponse linkToMessage(Long mediaId, Long messageId);

    // ── Delete ─────────────────────────────────────────────

    /** Deletes file from S3 (and thumbnail if present) and removes the DB record. */
    void deleteFile(Long mediaId, Long requesterId);

    void adminDeleteFile(Long mediaId);

    // ── Stats ──────────────────────────────────────────────

    long getFileCount(Long roomId);

    long getTotalStorageKb();
}
