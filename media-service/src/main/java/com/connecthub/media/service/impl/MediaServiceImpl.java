package com.connecthub.media.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.connecthub.media.dto.response.MediaFileResponse;
import com.connecthub.media.dto.response.PresignedUrlResponse;
import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.exception.BadRequestException;
import com.connecthub.media.exception.ForbiddenException;
import com.connecthub.media.exception.ResourceNotFoundException;
import com.connecthub.media.repository.MediaRepository;
import com.connecthub.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MediaServiceImpl implements MediaService {

    private final MediaRepository mediaRepository;
    private final Cloudinary cloudinary;

    @Value("${app.media.allowed-image-types}")
    private String allowedImageTypes;

    @Value("${app.media.allowed-doc-types}")
    private String allowedDocTypes;

    @Value("${app.media.max-file-size-mb:25}")
    private long maxFileSizeMb;

    @Value("${app.media.thumbnail.width:320}")
    private int thumbnailWidth;

    @Value("${app.media.thumbnail.height:240}")
    private int thumbnailHeight;

    @Value("${app.media.thumbnail.format:jpg}")
    private String thumbnailFormat;

    // ── Upload Image ───────────────────────────────────────

    @Override
    public MediaFileResponse uploadImage(MultipartFile file, Long uploaderId, Long roomId) {
        validateFileSize(file);
        String mimeType = resolveMimeType(file);
        validateMimeType(mimeType, allowedImageTypes, "image");

        Map<?, ?> uploadResult = uploadToCloudinary(file, "images", uploaderId, "image");
        String publicId = uploadResult.get("public_id").toString();
        String url = uploadResult.get("secure_url").toString();

        // Generate thumbnail using Thumbnailator
        String thumbnailPublicId = null;
        String thumbnailUrl   = null;
        int[]  dimensions     = {0, 0};

        try {
            byte[] thumbnailBytes = generateThumbnail(file.getBytes());
            Map<?, ?> thumbResult = uploadBytesToCloudinary(thumbnailBytes, "thumbnails", uploaderId, "image");
            thumbnailPublicId = thumbResult.get("public_id").toString();
            thumbnailUrl = thumbResult.get("secure_url").toString();
            dimensions = readImageDimensions(file.getBytes());
        } catch (Exception e) {
            log.warn("Thumbnail generation failed for file {}: {}", file.getOriginalFilename(), e.getMessage());
        }

        String sanitizedFilename = sanitize(file.getOriginalFilename());
        MediaFile mediaFile = MediaFile.builder()
                .uploaderId(uploaderId)
                .roomId(roomId)
                .cloudinaryPublicId(publicId)
                .filename(sanitizedFilename)
                .originalName(sanitizedFilename)
                .url(url)
                .thumbnailUrl(thumbnailUrl)
                .mimeType(mimeType)
                .sizeKb(file.getSize() / 1024)
                .width(dimensions[0] > 0 ? dimensions[0] : null)
                .height(dimensions[1] > 0 ? dimensions[1] : null)
                .mediaType(MediaFile.MediaType.IMAGE)
                .thumbnailCloudinaryPublicId(thumbnailPublicId)
                .build();

        mediaFile = mediaRepository.save(mediaFile);
        log.info("Image uploaded: id={} uploaderId={} roomId={} sizeKb={}",
                  mediaFile.getId(), uploaderId, roomId, mediaFile.getSizeKb());
        return MediaFileResponse.from(mediaFile);
    }

    // ── Upload File ────────────────────────────────────────

    @Override
    public MediaFileResponse uploadFile(MultipartFile file, Long uploaderId, Long roomId) {
        validateFileSize(file);
        String mimeType = resolveMimeType(file);

        // Accept both image and doc types for generic file upload
        String combined = allowedImageTypes + "," + allowedDocTypes;
        validateMimeType(mimeType, combined, "file");

        String resourceType = (mimeType.startsWith("image/") || mimeType.equals("application/pdf")) ? "image" : "raw";
        Map<?, ?> uploadResult = uploadToCloudinary(file, "files", uploaderId, resourceType);
        String publicId = uploadResult.get("public_id").toString();
        String url = uploadResult.get("secure_url").toString();

        MediaFile.MediaType mediaType = mimeType.startsWith("image/")
                ? MediaFile.MediaType.IMAGE
                : MediaFile.MediaType.FILE;

        MediaFile mediaFile = MediaFile.builder()
                .uploaderId(uploaderId)
                .roomId(roomId)
                .cloudinaryPublicId(publicId)
                .filename(sanitize(file.getOriginalFilename()))
                .originalName(sanitize(file.getOriginalFilename()))
                .url(url)
                .mimeType(mimeType)
                .sizeKb(file.getSize() / 1024)
                .mediaType(mediaType)
                .build();

        mediaFile = mediaRepository.save(mediaFile);
        log.info("File uploaded: id={} uploaderId={} roomId={} type={} sizeKb={}",
                  mediaFile.getId(), uploaderId, roomId, mimeType, mediaFile.getSizeKb());
        return MediaFileResponse.from(mediaFile);
    }

    // ── Fetch ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MediaFileResponse getFileById(Long mediaId) {
        return MediaFileResponse.from(findMedia(mediaId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaFileResponse> getFilesByRoom(Long roomId) {
        return mediaRepository.findByRoomIdOrderByUploadedAtDesc(roomId)
                .stream().map(MediaFileResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaFileResponse> getImagesByRoom(Long roomId) {
        return mediaRepository.findByRoomIdAndMediaTypeOrderByUploadedAtDesc(
                        roomId, MediaFile.MediaType.IMAGE)
                .stream().map(MediaFileResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaFileResponse> getFilesByUploader(Long uploaderId) {
        return mediaRepository.findByUploaderIdOrderByUploadedAtDesc(uploaderId)
                .stream().map(MediaFileResponse::from).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MediaFileResponse> getAllFiles() {
        return mediaRepository.findAll()
                .stream().map(MediaFileResponse::from).collect(Collectors.toList());
    }

    // ── Pre-signed URL ─────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PresignedUrlResponse generatePresignedUrl(Long mediaId, Long requesterId) {
        MediaFile media = findMedia(mediaId);

        // For Cloudinary, we can return the direct URL since it's already public.
        log.debug("URL requested for mediaId={}", mediaId);
        return PresignedUrlResponse.builder()
                .mediaId(mediaId)
                .presignedUrl(media.getUrl())
                .expiryHours(24)
                .filename(media.getOriginalName())
                .build();
    }

    // ── Link to Message ────────────────────────────────────

    @Override
    public MediaFileResponse linkToMessage(Long mediaId, Long messageId) {
        MediaFile media = findMedia(mediaId);
        media.setMessageId(messageId);
        media = mediaRepository.save(media);
        log.debug("MediaFile {} linked to message {}", mediaId, messageId);
        return MediaFileResponse.from(media);
    }

    // ── Delete ─────────────────────────────────────────────

    @Override
    public void deleteFile(Long mediaId, Long requesterId) {
        MediaFile media = findMedia(mediaId);
        if (!media.getUploaderId().equals(requesterId)) {
            throw new ForbiddenException("You can only delete your own uploaded files.");
        }
        deleteFromCloudinaryAndDB(media);
        log.info("File deleted: id={} by userId={}", mediaId, requesterId);
    }

    @Override
    public void adminDeleteFile(Long mediaId) {
        MediaFile media = findMedia(mediaId);
        deleteFromCloudinaryAndDB(media);
        log.info("File force-deleted by admin: id={}", mediaId);
    }

    // ── Stats ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long getFileCount(Long roomId) {
        return mediaRepository.countByRoomId(roomId);
    }

    @Override
    @Transactional(readOnly = true)
    public long getTotalStorageKb() {
        Long total = mediaRepository.sumTotalSizeKb();
        return total != null ? total : 0;
    }

    // ── Private Helpers ────────────────────────────────────

    private MediaFile findMedia(Long mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Media file not found: " + mediaId));
    }

    private void validateFileSize(MultipartFile file) {
        long maxBytes = maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BadRequestException(
                    "File size exceeds the maximum allowed size of " + maxFileSizeMb + "MB.");
        }
        if (file.isEmpty()) {
            throw new BadRequestException("File must not be empty.");
        }
    }

    private String resolveMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return contentType.toLowerCase();
    }

    private void validateMimeType(String mimeType, String allowedCsv, String category) {
        Set<String> allowed = Arrays.stream(allowedCsv.split(","))
                .map(String::trim).collect(Collectors.toSet());
        if (!allowed.contains(mimeType)) {
            throw new BadRequestException(
                    "File type '" + mimeType + "' is not allowed for " + category + " uploads. " +
                    "Allowed: " + allowedCsv);
        }
    }

    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "unnamed";
        return filename.replaceAll("[\\s/\\\\]", "_");
    }

    private Map<?, ?> uploadToCloudinary(MultipartFile file, String folderPrefix, Long uploaderId, String resourceType) {
        try {
            String folder = "connecthub/" + folderPrefix + "/" + uploaderId;
            return cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", resourceType,
                    "public_id", UUID.randomUUID().toString() + "-" + sanitize(file.getOriginalFilename())
            ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    private Map<?, ?> uploadBytesToCloudinary(byte[] bytes, String folderPrefix, Long uploaderId, String resourceType) {
        try {
            String folder = "connecthub/" + folderPrefix + "/" + uploaderId;
            return cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", resourceType,
                    "public_id", UUID.randomUUID().toString()
            ));
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to Cloudinary: " + e.getMessage(), e);
        }
    }

    private byte[] generateThumbnail(byte[] imageBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(new ByteArrayInputStream(imageBytes))
                .size(thumbnailWidth, thumbnailHeight)
                .keepAspectRatio(true)
                .outputFormat(thumbnailFormat)
                .toOutputStream(out);
        return out.toByteArray();
    }

    private int[] readImageDimensions(byte[] imageBytes) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                    new ByteArrayInputStream(imageBytes));
            if (img != null) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (IOException ignored) {}
        return new int[]{0, 0};
    }

    private void deleteFromCloudinaryAndDB(MediaFile media) {
        try {
            String resourceType = (media.getUrl() != null && media.getUrl().contains("/raw/upload/")) ? "raw" : "image";
            cloudinary.uploader().destroy(media.getCloudinaryPublicId(), ObjectUtils.asMap("resource_type", resourceType));
        } catch (Exception e) {
            log.warn("Cloudinary delete failed for publicId {}: {}", media.getCloudinaryPublicId(), e.getMessage());
        }

        if (media.getThumbnailCloudinaryPublicId() != null) {
            try {
                cloudinary.uploader().destroy(media.getThumbnailCloudinaryPublicId(), ObjectUtils.asMap("resource_type", "image"));
            } catch (Exception e) {
                log.warn("Cloudinary thumbnail delete failed for publicId {}: {}",
                          media.getThumbnailCloudinaryPublicId(), e.getMessage());
            }
        }

        mediaRepository.delete(media);
    }
}
