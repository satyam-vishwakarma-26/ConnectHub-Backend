package com.connecthub.message.service.impl;

import com.connecthub.message.exception.BadRequestException;
import com.connecthub.message.service.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaStorageServiceImpl Tests")
class MediaStorageServiceImplTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MediaStorageServiceImpl mediaStorageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaStorageService, "mediaServiceUrl", "http://localhost:8084/api");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "test-token")
        );
    }

    @Test
    @DisplayName("storeImage() - Success")
    void storeImage_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

        Map<String, Object> responseData = Map.of(
                "url", "http://localhost/media/test.jpg",
                "originalName", "test.jpg",
                "sizeKb", 100
        );
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of("data", responseData), HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        MediaStorageService.StoredMedia media = mediaStorageService.storeImage(file, 10L);

        assertThat(media).isNotNull();
        assertThat(media.mediaUrl()).isEqualTo("http://localhost/media/test.jpg");
        assertThat(media.fileName()).isEqualTo("test.jpg");
        assertThat(media.sizeKb()).isEqualTo(100L);
    }

    @Test
    @DisplayName("storeFile() - Success")
    void storeFile_success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        Map<String, Object> responseData = Map.of(
                "url", "http://localhost/media/test.txt",
                "originalName", "test.txt",
                "sizeKb", 50
        );
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of("data", responseData), HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        MediaStorageService.StoredMedia media = mediaStorageService.storeFile(file, 10L);

        assertThat(media).isNotNull();
        assertThat(media.mediaUrl()).isEqualTo("http://localhost/media/test.txt");
        assertThat(media.fileName()).isEqualTo("test.txt");
        assertThat(media.sizeKb()).isEqualTo(50L);
    }

    @Test
    @DisplayName("uploadToMediaService() - File empty throws BadRequestException")
    void uploadToMediaService_emptyFile_throwsBadRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> mediaStorageService.storeImage(emptyFile, 10L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("File is empty or missing");
    }

    @Test
    @DisplayName("uploadToMediaService() - Missing data field in response throws RuntimeException")
    void uploadToMediaService_missingData_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of(), HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        assertThatThrownBy(() -> mediaStorageService.storeFile(file, 10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("media-service response missing 'data' field");
    }

    @Test
    @DisplayName("loadAsResource() - Throws UnsupportedOperationException")
    void loadAsResource_throwsException() {
        assertThatThrownBy(() -> mediaStorageService.loadAsResource("test.jpg"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Media files are now served via Cloudinary");
    }

    @Test
    @DisplayName("uploadToMediaService() - Null filename returns upload")
    void uploadToMediaService_nullFilename() {
        MockMultipartFile file = new MockMultipartFile("file", null, "text/plain", "content".getBytes());

        Map<String, Object> responseData = Map.of(
                "url", "http://localhost/media/upload",
                "originalName", "upload",
                "sizeKb", 50
        );
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of("data", responseData), HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        MediaStorageService.StoredMedia media = mediaStorageService.storeFile(file, 10L);

        assertThat(media).isNotNull();
        assertThat(media.fileName()).isEqualTo("upload");
    }

    @Test
    @DisplayName("uploadToMediaService() - Missing sizeKb returns 1L")
    void uploadToMediaService_missingSizeKb() {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "content".getBytes());

        Map<String, Object> responseData = Map.of(
                "url", "http://localhost/media/test.jpg",
                "originalName", "test.jpg"
        );
        ResponseEntity<Map> responseEntity = new ResponseEntity<>(Map.of("data", responseData), HttpStatus.OK);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(responseEntity);

        MediaStorageService.StoredMedia media = mediaStorageService.storeImage(file, 10L);

        assertThat(media).isNotNull();
        assertThat(media.sizeKb()).isEqualTo(1L);
    }
}
