package com.connecthub.admin.bootstrap;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByRole(User.UserRole.PLATFORM_ADMIN)) {
            User user = User.builder()
                    .email(environment.getProperty("admin.default.email", "admin@connecthub.com"))
                    .passwordHash(passwordEncoder.encode(environment.getProperty("admin.default.password", "Admin@1234")))
                    .username(environment.getProperty("admin.default.username", "superadmin"))
                    .fullName(environment.getProperty("admin.default.fullName", "Platform Administrator"))
                    .role(User.UserRole.PLATFORM_ADMIN)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            userRepository.save(user);
            log.info("Default Platform Admin created");
        } else {
            log.info("Platform Admin already exists. Skipping.");
        }
    }
}