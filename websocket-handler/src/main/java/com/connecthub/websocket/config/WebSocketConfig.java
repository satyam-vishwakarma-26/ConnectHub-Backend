package com.connecthub.websocket.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocketConfig — the Java equivalent of Socket.io's server setup.
 *
 * Key decisions:
 *   - /ws          : SockJS endpoint (browsers behind proxies fall back to long-poll)
 *   - /app         : Application destination prefix — clients SEND to /app/chat.*
 *   - /topic       : Broker destination prefix — clients SUBSCRIBE to /topic/room/{id}
 *   - /queue       : User-specific queues — personal alerts for each userId
 *
 * JWT validation happens in the inbound channel interceptor (STOMP CONNECT frame).
 * Once authenticated, the Principal is set on the session — Spring uses it for
 * /queue/user/{userId} routing automatically.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${app.websocket.heartbeat-ms:25000}")
    private long heartbeatMs;

    @Value("${app.websocket.message-size-limit:65536}")
    private int messageSizeLimit;

    @Value("${app.websocket.send-buffer-limit:524288}")
    private int sendBufferLimit;

    @Value("${app.websocket.send-time-limit:10000}")
    private int sendTimeLimit;

    // ── SockJS endpoint ───────────────────────────────────

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","))
                .withSockJS()
                .setHeartbeatTime(heartbeatMs);
    }

    // ── Message broker ────────────────────────────────────

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Inbound: clients SEND to /app/chat.send, /app/chat.typing, /app/chat.read
        registry.setApplicationDestinationPrefixes("/app");

        // Outbound: Spring's in-memory broker routes to subscribers
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{heartbeatMs, heartbeatMs})
                .setTaskScheduler(heartbeatScheduler());

        // User-specific queue prefix: /user/{userId}/queue/...
        registry.setUserDestinationPrefix("/user");
    }

    // ── WebSocket transport limits ────────────────────────

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(messageSizeLimit)
                .setSendBufferSizeLimit(sendBufferLimit)
                .setSendTimeLimit(sendTimeLimit);
    }

    // ── JWT authentication interceptor ────────────────────

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor == null) return message;

                // Only validate on CONNECT frame
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    String token = extractToken(authHeader);

                    if (!StringUtils.hasText(token) || !jwtUtil.isValid(token)) {
                        log.warn("[WS] CONNECT rejected — invalid or missing token");
                        throw new org.springframework.security.access.AccessDeniedException(
                                "Invalid or missing JWT token");
                    }

                    Long   userId = jwtUtil.extractUserId(token);
                    String email  = jwtUtil.extractEmail(token);
                    String role   = jwtUtil.extractRole(token);
                    if (role == null) role = "USER";

                    // Store userId in session attributes for later use
                    Map<String, Object> attrs = accessor.getSessionAttributes();
                    if (attrs != null) {
                        attrs.put("userId", userId);
                        attrs.put("email",  email);
                        attrs.put("role",   role);
                        attrs.put("token",  token);
                    }

                    // Set Spring Security principal — enables /user/{userId}/queue routing
                    UsernamePasswordAuthenticationToken principal =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    token,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    accessor.setUser(principal);

                    log.info("[WS] CONNECT authenticated: userId={} email={}", userId, email);
                }

                return message;
            }
        });
    }

    // ── Heartbeat scheduler ───────────────────────────────

    private TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    // ── Helper ────────────────────────────────────────────

    private String extractToken(String header) {
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return header; // Some clients send the token directly without "Bearer " prefix
    }
}
