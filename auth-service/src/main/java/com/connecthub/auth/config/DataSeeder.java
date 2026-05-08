package com.connecthub.auth.config;

import com.connecthub.auth.entity.User;
import com.connecthub.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = "phulchandravishwakarma9755@gmail.com";
        if (!userRepository.existsByEmail(adminEmail)) {
            log.info("Seeding default platform admin user: {}", adminEmail);
            User admin = User.builder()
                    .email(adminEmail)
                    .username("platform_admin")
                    .fullName("Platform Administrator")
                    .passwordHash(passwordEncoder.encode("admin@9981"))
                    .role(User.UserRole.PLATFORM_ADMIN)
                    .isActive(true)
                    .status(User.UserStatus.ONLINE)
                    .provider(User.AuthProvider.LOCAL)
                    .build();
            userRepository.save(admin);
            log.info("Default admin seeded successfully.");
        }
    }
}