package com.connecthub.media.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.connecthub.media.dto.response.MediaFileResponse;
import com.connecthub.media.dto.response.PresignedUrlResponse;
import com.connecthub.media.entity.MediaFile;
import com.connecthub.media.exception.BadRequestException;
import com.connecthub.media.exception.ForbiddenException;
import com.connecthub.media.exception.ResourceNotFoundException;
import com.connecthub.media.repository.MediaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaServiceImpl — Unit Tests")
class MediaServiceImplTest {

    // ──────────────────────────────────────────────────────────────────────────
    // Mocks & SUT
    // ──────────────────────────────────────────────────────────────────────────

    @Mock private MediaRepository mediaRepository;
    @Mock private Cloudinary      cloudinary;
    @Mock private Uploader        uploader;

    @InjectMocks
    private MediaServiceImpl mediaService;

    // ──────────────────────────────────────────────────────────────────────────
    // Fixtures
    // ──────────────────────────────────────────────────────────────────────────

    private MediaFile testImage;
    private MediaFile testFile;
    private MediaFile testFileRaw;  // raw URL variant (non-image Cloudinary resource)

    @BeforeEach
    void setUp() {
        // Inject @Value fields
        ReflectionTestUtils.setField(mediaService, "allowedImageTypes", "image/jpeg,image/png,image/gif,image/webp");
        ReflectionTestUtils.setField(mediaService, "allowedDocTypes",   "application/pdf,application/zip,text/plain");
        ReflectionTestUtils.setField(mediaService, "maxFileSizeMb",     25L);
        ReflectionTestUtils.setField(mediaService, "thumbnailWidth",    320);
        ReflectionTestUtils.setField(mediaService, "thumbnailHeight",   240);
        ReflectionTestUtils.setField(mediaService, "thumbnailFormat",   "jpg");

        testImage = MediaFile.builder()
                .id(1L).uploaderId(100L).roomId(10L)
                .cloudinaryPublicId("pub-img-1")
                .thumbnailCloudinaryPublicId("thumb-img-1")
                .filename("photo.png").originalName("photo.png")
                .url("https://res.cloudinary.com/demo/image.png")
                .thumbnailUrl("https://res.cloudinary.com/demo/thumb.png")
                .mimeType("image/png").sizeKb(512L)
                .mediaType(MediaFile.MediaType.IMAGE)
                .build();

        testFile = MediaFile.builder()
                .id(2L).uploaderId(100L).roomId(10L)
                .cloudinaryPublicId("pub-doc-2")
                .filename("doc.pdf").originalName("doc.pdf")
                .url("https://res.cloudinary.com/demo/doc.pdf")
                .mimeType("application/pdf").sizeKb(1024L)
                .mediaType(MediaFile.MediaType.FILE)
                .build();

        testFileRaw = MediaFile.builder()
                .id(3L).uploaderId(200L).roomId(20L)
                .cloudinaryPublicId("pub-raw-3")
                .filename("archive.zip").originalName("archive.zip")
                .url("https://res.cloudinary.com/demo/raw/upload/archive.zip")  // raw URL
                .mimeType("application/zip").sizeKb(2048L)
                .mediaType(MediaFile.MediaType.FILE)
                .build();
    }

    // ─────────────────────────── helpers ──────────────────────────────────────

    /** Builds a valid 1×1 JPEG byte array so Thumbnailator and ImageIO can actually decode it. */
    private byte[] singlePixelJpeg() throws Exception {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", bos);
        return bos.toByteArray();
    }

    /** Builds a valid 100×100 PNG byte array. */
    private byte[] smallPng() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private Map<String, String> cloudinaryResult(String publicId, String url) {
        return Map.of("public_id", publicId, "secure_url", url);
    }

    private void stubUploader() {
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // uploadImage
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("uploadImage()")
    class UploadImage {

        @Test
        @DisplayName("Happy path — valid JPEG, thumbnail generated, dimensions read, record saved")
        void success_withThumbnailAndDimensions() throws Exception {
            byte[] jpegBytes = singlePixelJpeg();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", jpegBytes);

            stubUploader();
            // First upload: original; second: thumbnail
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("orig-id", "https://cdn.example.com/orig.jpg"))
                    .thenReturn(cloudinaryResult("thumb-id", "https://cdn.example.com/thumb.jpg"));

            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> {
                MediaFile mf = inv.getArgument(0);
                mf.setId(99L);
                return mf;
            });

            MediaFileResponse resp = mediaService.uploadImage(file, 100L, 10L);

            assertThat(resp.getId()).isEqualTo(99L);
            assertThat(resp.getMimeType()).isEqualTo("image/jpeg");
            assertThat(resp.getUrl()).isEqualTo("https://cdn.example.com/orig.jpg");
            assertThat(resp.getThumbnailUrl()).isEqualTo("https://cdn.example.com/thumb.jpg");
            assertThat(resp.getMediaType()).isEqualTo("IMAGE");
            verify(uploader, times(2)).upload(any(byte[].class), anyMap());
            verify(mediaRepository).save(any(MediaFile.class));
        }

        @Test
        @DisplayName("PNG with readable dimensions — width/height set on saved entity")
        void success_png_dimensionsSet() throws Exception {
            byte[] pngBytes = smallPng();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "img.png", "image/png", pngBytes);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("o", "https://cdn.example.com/o.png"))
                    .thenReturn(cloudinaryResult("t", "https://cdn.example.com/t.png"));

            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

            MediaFileResponse resp = mediaService.uploadImage(file, 100L, 10L);

            // 100×100 image → width and height should be populated
            assertThat(resp.getWidth()).isEqualTo(100);
            assertThat(resp.getHeight()).isEqualTo(100);
        }

        @Test
        @DisplayName("Thumbnail upload fails silently — original still saved without thumbnail")
        void thumbnailFailure_stillSavesOriginal() throws Exception {
            byte[] jpegBytes = singlePixelJpeg();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpg", "image/jpeg", jpegBytes);

            stubUploader();
            // First upload succeeds; second (thumbnail) throws
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("orig-id", "https://cdn.example.com/orig.jpg"))
                    .thenThrow(new RuntimeException("Cloudinary thumbnail upload error"));

            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> {
                MediaFile mf = inv.getArgument(0);
                mf.setId(77L);
                return mf;
            });

            // Should NOT throw; thumbnail failure is swallowed
            MediaFileResponse resp = mediaService.uploadImage(file, 100L, 10L);

            assertThat(resp.getId()).isEqualTo(77L);
            assertThat(resp.getThumbnailUrl()).isNull();
        }

        @Test
        @DisplayName("Filename is null — sanitize returns 'unnamed'")
        void nullFilename_sanitizedToUnnamed() throws Exception {
            byte[] jpegBytes = singlePixelJpeg();
            MockMultipartFile file = new MockMultipartFile(
                    "file", null, "image/jpeg", jpegBytes);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("id1", "https://cdn.example.com/img.jpg"))
                    .thenReturn(cloudinaryResult("id2", "https://cdn.example.com/thumb.jpg"));
            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

            MediaFileResponse resp = mediaService.uploadImage(file, 100L, 10L);
            assertThat(resp.getFilename()).isEqualTo("unnamed");
        }

        @Test
        @DisplayName("Filename with spaces and slashes — sanitized with underscores")
        void filenameWithSpecialChars_sanitized() throws Exception {
            byte[] jpegBytes = singlePixelJpeg();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "my photo/final.jpg", "image/jpeg", jpegBytes);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("id1", "https://cdn.example.com/img.jpg"))
                    .thenReturn(cloudinaryResult("id2", "https://cdn.example.com/thumb.jpg"));
            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

            MediaFileResponse resp = mediaService.uploadImage(file, 100L, 10L);
            assertThat(resp.getFilename()).isEqualTo("my_photo_final.jpg");
        }

        @Test
        @DisplayName("File too large — throws BadRequestException")
        void fileTooLarge_throws() {
            ReflectionTestUtils.setField(mediaService, "maxFileSizeMb", 0L);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "big.png", "image/png", new byte[1]);
            assertThatThrownBy(() -> mediaService.uploadImage(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("File size exceeds");
        }

        @Test
        @DisplayName("Empty file — throws BadRequestException")
        void emptyFile_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.png", "image/png", new byte[0]);
            assertThatThrownBy(() -> mediaService.uploadImage(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("Disallowed MIME type — throws BadRequestException")
        void disallowedMimeType_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "doc.pdf", "application/pdf", new byte[10]);
            assertThatThrownBy(() -> mediaService.uploadImage(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not allowed for image uploads");
        }

        @Test
        @DisplayName("Null content-type — resolved to application/octet-stream, rejected")
        void nullContentType_resolvedAndRejected() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "weird.bin", null, new byte[10]);
            assertThatThrownBy(() -> mediaService.uploadImage(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("application/octet-stream");
        }

        @Test
        @DisplayName("Blank content-type — resolved to application/octet-stream, rejected")
        void blankContentType_resolvedAndRejected() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "weird.bin", "   ", new byte[10]);
            assertThatThrownBy(() -> mediaService.uploadImage(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Cloudinary original upload throws — RuntimeException propagated")
        void cloudinaryUploadThrows_propagates() throws IOException {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.jpeg", "image/jpeg", new byte[10]);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenThrow(new RuntimeException("Cloudinary down"));

            assertThatThrownBy(() -> mediaService.uploadImage(file, 1L, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Cloudinary");
        }

        @Test
        @DisplayName("GIF and WebP are accepted MIME types")
        void gifAndWebpAccepted() throws Exception {
            for (String mime : List.of("image/gif", "image/webp")) {
                byte[] jpegBytes = singlePixelJpeg();
                MockMultipartFile file = new MockMultipartFile("file", "anim." + mime.split("/")[1], mime, jpegBytes);
                stubUploader();
                when(uploader.upload(any(byte[].class), anyMap()))
                        .thenReturn(cloudinaryResult("id", "https://cdn.example.com/a"))
                        .thenReturn(cloudinaryResult("tid", "https://cdn.example.com/t"));
                when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

                assertThatCode(() -> mediaService.uploadImage(file, 1L, 1L))
                        .doesNotThrowAnyException();
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // uploadFile
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("uploadFile()")
    class UploadFile {

        @Test
        @DisplayName("PDF — resource_type=image, mediaType=FILE")
        void pdf_success() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "report.pdf", "application/pdf", new byte[100]);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("pdf-id", "https://cdn.example.com/report.pdf"));
            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> {
                MediaFile mf = inv.getArgument(0);
                mf.setId(55L);
                return mf;
            });

            MediaFileResponse resp = mediaService.uploadFile(file, 100L, 10L);

            assertThat(resp.getId()).isEqualTo(55L);
            assertThat(resp.getMediaType()).isEqualTo("FILE");
            assertThat(resp.getMimeType()).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("ZIP — resource_type=raw, mediaType=FILE")
        void zip_success() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "bundle.zip", "application/zip", new byte[200]);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("zip-id", "https://cdn.example.com/bundle.zip"));
            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

            MediaFileResponse resp = mediaService.uploadFile(file, 100L, 10L);
            assertThat(resp.getMimeType()).isEqualTo("application/zip");
            assertThat(resp.getMediaType()).isEqualTo("FILE");
        }

        @Test
        @DisplayName("Image MIME via uploadFile — mediaType set to IMAGE")
        void imageMimeViaUploadFile_mediaTypeIsImage() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.png", "image/png", new byte[50]);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("img-id", "https://cdn.example.com/photo.png"));
            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

            MediaFileResponse resp = mediaService.uploadFile(file, 100L, 10L);
            assertThat(resp.getMediaType()).isEqualTo("IMAGE");
        }

        @Test
        @DisplayName("Null content-type — resolved to application/octet-stream, rejected")
        void nullContentType_rejected() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "weird.bin", null, new byte[10]);
            assertThatThrownBy(() -> mediaService.uploadFile(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("Disallowed MIME type — throws BadRequestException")
        void disallowedMime_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "hack.exe", "application/x-msdownload", new byte[10]);
            assertThatThrownBy(() -> mediaService.uploadFile(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not allowed for file uploads");
        }

        @Test
        @DisplayName("File empty — throws BadRequestException")
        void emptyFile_throws() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.pdf", "application/pdf", new byte[0]);
            assertThatThrownBy(() -> mediaService.uploadFile(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must not be empty");
        }

        @Test
        @DisplayName("File too large — throws BadRequestException")
        void fileTooLarge_throws() {
            ReflectionTestUtils.setField(mediaService, "maxFileSizeMb", 0L);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "big.pdf", "application/pdf", new byte[1]);
            assertThatThrownBy(() -> mediaService.uploadFile(file, 1L, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("File size exceeds");
        }

        @Test
        @DisplayName("Filename with backslash — sanitized")
        void filenameWithBackslash_sanitized() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "my\\doc.pdf", "application/pdf", new byte[50]);

            stubUploader();
            when(uploader.upload(any(byte[].class), anyMap()))
                    .thenReturn(cloudinaryResult("id", "https://cdn.example.com/doc.pdf"));
            when(mediaRepository.save(any(MediaFile.class))).thenAnswer(inv -> inv.getArgument(0));

            MediaFileResponse resp = mediaService.uploadFile(file, 1L, 1L);
            assertThat(resp.getFilename()).isEqualTo("my_doc.pdf");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getFileById
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFileById()")
    class GetFileById {

        @Test
        @DisplayName("Found — returns mapped response")
        void found() {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            MediaFileResponse resp = mediaService.getFileById(1L);
            assertThat(resp.getId()).isEqualTo(1L);
            assertThat(resp.getUrl()).isEqualTo("https://res.cloudinary.com/demo/image.png");
        }

        @Test
        @DisplayName("Not found — throws ResourceNotFoundException")
        void notFound_throws() {
            when(mediaRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> mediaService.getFileById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getFilesByRoom
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFilesByRoom()")
    class GetFilesByRoom {

        @Test
        @DisplayName("Returns all files for room")
        void success() {
            when(mediaRepository.findByRoomIdOrderByUploadedAtDesc(10L))
                    .thenReturn(List.of(testImage, testFile));
            assertThat(mediaService.getFilesByRoom(10L)).hasSize(2);
        }

        @Test
        @DisplayName("Empty room — returns empty list")
        void emptyRoom() {
            when(mediaRepository.findByRoomIdOrderByUploadedAtDesc(10L))
                    .thenReturn(Collections.emptyList());
            assertThat(mediaService.getFilesByRoom(10L)).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getImagesByRoom
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getImagesByRoom()")
    class GetImagesByRoom {

        @Test
        @DisplayName("Returns only IMAGE-type files")
        void success() {
            when(mediaRepository.findByRoomIdAndMediaTypeOrderByUploadedAtDesc(
                    10L, MediaFile.MediaType.IMAGE))
                    .thenReturn(List.of(testImage));

            List<MediaFileResponse> result = mediaService.getImagesByRoom(10L);
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getMediaType()).isEqualTo("IMAGE");
        }

        @Test
        @DisplayName("No images in room — returns empty list")
        void empty() {
            when(mediaRepository.findByRoomIdAndMediaTypeOrderByUploadedAtDesc(
                    10L, MediaFile.MediaType.IMAGE))
                    .thenReturn(Collections.emptyList());
            assertThat(mediaService.getImagesByRoom(10L)).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getFilesByUploader
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFilesByUploader()")
    class GetFilesByUploader {

        @Test
        @DisplayName("Returns files for uploader")
        void success() {
            when(mediaRepository.findByUploaderIdOrderByUploadedAtDesc(100L))
                    .thenReturn(List.of(testImage, testFile));
            assertThat(mediaService.getFilesByUploader(100L)).hasSize(2);
        }

        @Test
        @DisplayName("No files for uploader — empty list")
        void empty() {
            when(mediaRepository.findByUploaderIdOrderByUploadedAtDesc(100L))
                    .thenReturn(Collections.emptyList());
            assertThat(mediaService.getFilesByUploader(100L)).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getAllFiles
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllFiles()")
    class GetAllFiles {

        @Test
        @DisplayName("Returns all files")
        void success() {
            when(mediaRepository.findAll()).thenReturn(List.of(testImage, testFile, testFileRaw));
            assertThat(mediaService.getAllFiles()).hasSize(3);
        }

        @Test
        @DisplayName("No files in system — empty list")
        void empty() {
            when(mediaRepository.findAll()).thenReturn(Collections.emptyList());
            assertThat(mediaService.getAllFiles()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // generatePresignedUrl
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generatePresignedUrl()")
    class GeneratePresignedUrl {

        @Test
        @DisplayName("Found — returns response with Cloudinary URL, 24h expiry")
        void success() {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            PresignedUrlResponse resp = mediaService.generatePresignedUrl(1L, 100L);
            assertThat(resp.getMediaId()).isEqualTo(1L);
            assertThat(resp.getPresignedUrl()).isEqualTo(testImage.getUrl());
            assertThat(resp.getExpiryHours()).isEqualTo(24L);
            assertThat(resp.getFilename()).isEqualTo(testImage.getOriginalName());
        }

        @Test
        @DisplayName("Media not found — throws ResourceNotFoundException")
        void notFound_throws() {
            when(mediaRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> mediaService.generatePresignedUrl(99L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // linkToMessage
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("linkToMessage()")
    class LinkToMessage {

        @Test
        @DisplayName("Links message ID and returns updated response")
        void success() {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            when(mediaRepository.save(testImage)).thenReturn(testImage);

            MediaFileResponse resp = mediaService.linkToMessage(1L, 500L);

            assertThat(testImage.getMessageId()).isEqualTo(500L);
            assertThat(resp.getId()).isEqualTo(1L);
            verify(mediaRepository).save(testImage);
        }

        @Test
        @DisplayName("Media not found — throws ResourceNotFoundException")
        void notFound_throws() {
            when(mediaRepository.findById(77L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> mediaService.linkToMessage(77L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // deleteFile
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteFile()")
    class DeleteFile {

        @Test
        @DisplayName("Owner deletes image — Cloudinary called twice (image + thumbnail)")
        void ownerDeletesImage_cloudinaryCalledTwice() throws Exception {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            stubUploader();
            when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

            mediaService.deleteFile(1L, 100L);

            verify(uploader, times(2)).destroy(anyString(), anyMap());
            verify(mediaRepository).delete(testImage);
        }

        @Test
        @DisplayName("Owner deletes file without thumbnail — Cloudinary called once")
        void ownerDeletesFileNoThumbnail_cloudinaryCalledOnce() throws Exception {
            when(mediaRepository.findById(2L)).thenReturn(Optional.of(testFile));
            stubUploader();
            when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

            mediaService.deleteFile(2L, 100L);

            verify(uploader, times(1)).destroy(anyString(), anyMap());
            verify(mediaRepository).delete(testFile);
        }

        @Test
        @DisplayName("Non-owner — throws ForbiddenException, nothing deleted")
        void nonOwner_throws() {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            assertThatThrownBy(() -> mediaService.deleteFile(1L, 999L))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("only delete your own");
            verify(mediaRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Cloudinary destroy fails silently — DB delete still called")
        void cloudinaryDestroyFails_dbDeleteStillCalled() throws Exception {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            stubUploader();
            when(uploader.destroy(anyString(), anyMap())).thenThrow(new RuntimeException("Network error"));

            // Should not throw
            mediaService.deleteFile(1L, 100L);

            verify(mediaRepository).delete(testImage);
        }

        @Test
        @DisplayName("Raw-URL file — resource_type resolved to 'raw' for Cloudinary destroy")
        void rawUrlFile_resourceTypeIsRaw() throws Exception {
            when(mediaRepository.findById(3L)).thenReturn(Optional.of(testFileRaw));
            stubUploader();
            when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of());

            mediaService.deleteFile(3L, 200L);

            verify(uploader).destroy(eq("pub-raw-3"), argThat(params ->
                    "raw".equals(((Map<?, ?>) params).get("resource_type"))
            ));
            verify(mediaRepository).delete(testFileRaw);
        }

        @Test
        @DisplayName("Media not found — throws ResourceNotFoundException")
        void notFound_throws() {
            when(mediaRepository.findById(88L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> mediaService.deleteFile(88L, 1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Thumbnail Cloudinary destroy fails silently — DB delete still called")
        void thumbnailCloudinaryDestroyFails_dbDeleteStillCalled() throws Exception {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            stubUploader();
            // First destroy (main image) succeeds; second (thumbnail) fails
            when(uploader.destroy(anyString(), anyMap()))
                    .thenReturn(Map.of("result", "ok"))
                    .thenThrow(new RuntimeException("thumb delete failed"));

            mediaService.deleteFile(1L, 100L);

            verify(mediaRepository).delete(testImage);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // adminDeleteFile
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("adminDeleteFile()")
    class AdminDeleteFile {

        @Test
        @DisplayName("Admin deletes any file — no ownership check")
        void success() throws Exception {
            when(mediaRepository.findById(1L)).thenReturn(Optional.of(testImage));
            stubUploader();
            when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of("result", "ok"));

            mediaService.adminDeleteFile(1L);

            verify(mediaRepository).delete(testImage);
        }

        @Test
        @DisplayName("Admin deletes another user's file — allowed")
        void adminDeletesOtherUsersFile() throws Exception {
            // testFile uploaderId=100 — admin calls delete directly
            when(mediaRepository.findById(2L)).thenReturn(Optional.of(testFile));
            stubUploader();
            when(uploader.destroy(anyString(), anyMap())).thenReturn(Map.of());

            assertThatCode(() -> mediaService.adminDeleteFile(2L)).doesNotThrowAnyException();
            verify(mediaRepository).delete(testFile);
        }

        @Test
        @DisplayName("Not found — throws ResourceNotFoundException")
        void notFound_throws() {
            when(mediaRepository.findById(55L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> mediaService.adminDeleteFile(55L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getFileCount
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getFileCount()")
    class GetFileCount {

        @Test
        @DisplayName("Returns count from repository")
        void success() {
            when(mediaRepository.countByRoomId(10L)).thenReturn(7L);
            assertThat(mediaService.getFileCount(10L)).isEqualTo(7L);
        }

        @Test
        @DisplayName("Zero files in room")
        void zero() {
            when(mediaRepository.countByRoomId(10L)).thenReturn(0L);
            assertThat(mediaService.getFileCount(10L)).isEqualTo(0L);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getTotalStorageKb
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTotalStorageKb()")
    class GetTotalStorageKb {

        @Test
        @DisplayName("Returns actual KB from repository")
        void success() {
            when(mediaRepository.sumTotalSizeKb()).thenReturn(99999L);
            assertThat(mediaService.getTotalStorageKb()).isEqualTo(99999L);
        }

        @Test
        @DisplayName("Null from repository — returns 0")
        void nullFromRepo_returnsZero() {
            when(mediaRepository.sumTotalSizeKb()).thenReturn(null);
            assertThat(mediaService.getTotalStorageKb()).isEqualTo(0L);
        }
    }
}
