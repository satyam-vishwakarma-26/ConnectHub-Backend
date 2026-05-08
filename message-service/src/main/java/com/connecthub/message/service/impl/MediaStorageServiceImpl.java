package com.connecthub.message.service.impl;

import com.connecthub.message.exception.BadRequestException;
import com.connecthub.message.service.MediaStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@Slf4j
public class MediaStorageServiceImpl implements MediaStorageService {

    private final RestTemplate restTemplate;

    @Value("${app.media-service.url:http://localhost:8084/api}")
    private String mediaServiceUrl;

    /**
     * Use the mediaRestTemplate bean (30-second read timeout) so that
     * Cloudinary uploads don't time out during the inter-service call.
     */
    public MediaStorageServiceImpl(@Qualifier("mediaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public StoredMedia storeImage(MultipartFile file, Long roomId) {
        long effectiveRoomId = (roomId != null && roomId > 0) ? roomId : 0L;
        return uploadToMediaService(file, "/media/upload/image?roomId=" + effectiveRoomId);
    }

    @Override
    public StoredMedia storeFile(MultipartFile file, Long roomId) {
        long effectiveRoomId = (roomId != null && roomId > 0) ? roomId : 0L;
        return uploadToMediaService(file, "/media/upload/file?roomId=" + effectiveRoomId);
    }

    @Override
    public Resource loadAsResource(String fileName) {
        throw new UnsupportedOperationException("Media files are now served via Cloudinary/media-service.");
    }

    private StoredMedia uploadToMediaService(MultipartFile file, String endpoint) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty or missing");
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // Forward the current user's JWT token so media-service can authenticate
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getCredentials() != null) {
                String token = auth.getCredentials().toString();
                if (!token.isBlank()) {
                    headers.setBearerAuth(token);
                }
            }

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            org.springframework.core.io.ByteArrayResource fileResource =
                    new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
                        @Override
                        public String getFilename() {
                            return file.getOriginalFilename() != null
                                    ? file.getOriginalFilename()
                                    : "upload";
                        }
                    };
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String fullUrl = mediaServiceUrl + endpoint;
            log.info("Delegating media upload to media-service: {}", fullUrl);

            ResponseEntity<Map> response = restTemplate.exchange(
                    fullUrl,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map<?, ?> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("data")) {
                Map<?, ?> data = (Map<?, ?>) responseBody.get("data");
                String mediaUrl = (String) data.get("url");
                String fileName = (String) data.get("originalName");
                long sizeKb = data.containsKey("sizeKb")
                        ? ((Number) data.get("sizeKb")).longValue()
                        : 1L;

                log.info("Successfully uploaded media via media-service: {}", mediaUrl);
                return new StoredMedia(fileName, mediaUrl, sizeKb);
            }
            throw new RuntimeException("media-service response missing 'data' field");

        } catch (Exception e) {
            log.error("Failed to upload file to media-service: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to media-service: " + e.getMessage(), e);
        }
    }
}
