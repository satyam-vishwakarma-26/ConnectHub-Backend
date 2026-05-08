package com.connecthub.admin.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration-style tests for {@link SecurityConfig}.
 *
 * Uses a full Spring Boot context so that the real security filter chain is exercised.
 * The database / Eureka dependencies are neutralised via properties.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Disable Eureka registration so the test context starts without a registry
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        // In-memory H2 so no MySQL is required
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // Minimal app config
        "app.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
        "app.services.auth-service=http://localhost:9001",
        "app.services.room-service=http://localhost:9002",
        "app.services.message-service=http://localhost:9003",
        "app.services.presence-service=http://localhost:9004",
        "app.services.notification-service=http://localhost:9005"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Unauthenticated request to a protected endpoint must be rejected with 401/403.
     */
    @Test
    void adminEndpoint_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isForbidden());
    }

    /**
     * An invalid / tampered JWT must NOT grant access to admin endpoints.
     */
    @Test
    void adminEndpoint_withInvalidToken_isForbidden() throws Exception {
        mockMvc.perform(get("/admin/analytics")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isForbidden());
    }
}
