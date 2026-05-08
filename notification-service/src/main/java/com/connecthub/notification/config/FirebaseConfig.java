package com.connecthub.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;

@Configuration
@Slf4j
public class FirebaseConfig {

    @Value("${app.firebase.credentials-path:classpath:firebase-service-account.json}")
    private String credentialsPath;

    private final ResourceLoader resourceLoader;

    public FirebaseConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * Initialises the Firebase Admin SDK once on startup.
     * If the credentials file is missing (dev environment without FCM),
     * initialisation is skipped gracefully — FCM calls will be no-ops.
     */
    @PostConstruct
    public void initialise() {
        if (!FirebaseApp.getApps().isEmpty()) {
            log.info("[Firebase] Already initialised");
            return;
        }

        try {
            Resource resource = resourceLoader.getResource(credentialsPath);
            if (!resource.exists()) {
                log.warn("[Firebase] Credentials file not found at '{}'. " +
                         "FCM push notifications will be disabled.", credentialsPath);
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("[Firebase] Admin SDK initialised successfully");
            }
        } catch (IOException e) {
            log.error("[Firebase] Failed to initialise: {}. FCM will be disabled.", e.getMessage());
        }
    }

    public boolean isInitialised() {
        return !FirebaseApp.getApps().isEmpty();
    }
}
