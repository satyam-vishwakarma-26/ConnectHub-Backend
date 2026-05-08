package com.connecthub.message.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface MediaStorageService {

    /**
     * Upload an image to media-service/Cloudinary.
     * @param file     the multipart image file
     * @param roomId   the room this image belongs to (use 0 for direct messages — media-service accepts it)
     */
    StoredMedia storeImage(MultipartFile file, Long roomId);

    /**
     * Upload a file to media-service/Cloudinary.
     * @param file     the multipart file
     * @param roomId   the room this file belongs to (use 0 for direct messages)
     */
    StoredMedia storeFile(MultipartFile file, Long roomId);

    Resource loadAsResource(String fileName);

    record StoredMedia(String fileName, String mediaUrl, long sizeKb) {}
}
