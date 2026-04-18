# ConnectHub — auth-service

> **Port:** `8081` | **Base path:** `/api/auth` | **Package:** `com.connecthub.auth`

Real-time Chat Application — Authentication & User Management Microservice.
Handles registration, login, JWT issuance, OAuth2 (Google / GitHub),
profile management, online status, and platform admin operations.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.2, Spring Security 6 |
| Auth | JWT (jjwt 0.12), OAuth2 Client |
| Database | MySQL 8 + Spring Data JPA |
| Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Build | Maven 3.9, Java 17 |
| Container | Docker + Docker Compose |

---

## Quick Start

### Option A — Docker Compose (recommended)

```bash
cd auth-service
docker-compose up --build
```

Service starts at `http://localhost:8081`
Swagger UI at `http://localhost:8081/api/swagger-ui.html`

### Option B — Local (MySQL must be running)

1. Create database:
```sql
CREATE DATABASE connecthub_auth;
```

2. Update `src/main/resources/application.yml` with your MySQL credentials.

3. Run:
```bash
mvn spring-boot:run
```

---

## Project Structure

```
auth-service/
├── src/main/java/com/connecthub/auth/
│   ├── AuthServiceApplication.java       ← main class
│   ├── config/
│   │   ├── SecurityConfig.java           ← Spring Security + JWT + OAuth2
│   │   └── OpenApiConfig.java            ← Swagger setup
│   ├── controller/
│   │   └── AuthController.java           ← all REST endpoints
│   ├── dto/
│   │   ├── request/                      ← RegisterRequest, LoginRequest, etc.
│   │   └── response/                     ← AuthResponse, UserResponse, ApiResponse
│   ├── entity/
│   │   └── User.java                     ← JPA entity with enums
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java   ← maps exceptions → HTTP responses
│   │   └── *.java                        ← custom exception classes
│   ├── repository/
│   │   └── UserRepository.java           ← Spring Data JPA queries
│   ├── security/
│   │   ├── JwtService.java               ← JWT generate / validate
│   │   ├── JwtAuthenticationFilter.java  ← validates Bearer token per request
│   │   ├── CustomUserDetails.java        ← Spring Security principal wrapper
│   │   ├── CustomUserDetailsService.java ← loads user from DB
│   │   ├── CustomOAuth2UserService.java  ← processes OAuth2 login, upserts user
│   │   └── OAuth2AuthenticationSuccessHandler.java
│   └── service/
│       ├── AuthService.java              ← business contract interface
│       └── impl/AuthServiceImpl.java     ← full implementation
└── src/test/
    └── AuthServiceImplTest.java          ← unit tests
```

---

## API Reference

### Public Endpoints (no token required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login, get JWT tokens |
| POST | `/api/auth/refresh` | Refresh access token |

### Secured Endpoints (Bearer JWT required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/logout` | Logout, invalidate refresh token |
| GET | `/api/auth/profile` | Get my profile |
| GET | `/api/auth/profile/{id}` | Get any user by ID |
| PUT | `/api/auth/profile` | Update my profile |
| PUT | `/api/auth/password` | Change password |
| PUT | `/api/auth/status` | Update online status |
| POST | `/api/auth/last-seen` | Record last seen (called by WS handler) |
| GET | `/api/auth/search?q=keyword` | Search users |

### Admin Endpoints (PLATFORM_ADMIN role required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/admin/users` | List all users |
| PUT | `/api/auth/admin/users/{id}/suspend` | Suspend user |
| PUT | `/api/auth/admin/users/{id}/reactivate` | Reactivate user |
| DELETE | `/api/auth/admin/users/{id}` | Delete user |

---

## cURL Examples

### Register
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "username": "alice",
    "password": "secret123",
    "fullName": "Alice Smith"
  }'
```

### Login
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}'
```

### Get Profile (with token)
```bash
curl http://localhost:8081/api/auth/profile \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### Update Status
```bash
curl -X PUT http://localhost:8081/api/auth/status \
  -H "Authorization: Bearer <ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"status":"AWAY"}'
```

### Search Users
```bash
curl "http://localhost:8081/api/auth/search?q=alice" \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### Refresh Token
```bash
curl -X POST http://localhost:8081/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

---

## STOMP Integration

The `websocket-handler` service calls two endpoints on every WebSocket event:

- `POST /api/auth/last-seen` — on WebSocket disconnect to record `lastSeenAt`
- `GET /api/auth/profile/{userId}` — to resolve user info before broadcasting

Both are called with a service-level JWT issued for the `websocket-handler` service account.

---

## OAuth2 Setup

1. Create OAuth apps at [Google Console](https://console.cloud.google.com) and [GitHub Developer Settings](https://github.com/settings/developers).
2. Set redirect URIs to: `http://localhost:8081/api/auth/oauth2/callback/google` (and `/github`).
3. Add your `client-id` and `client-secret` in `application.yml`.
4. On success, user is redirected to `/oauth2/redirect?token=...&refreshToken=...` — your frontend reads the tokens from the URL.

---

## Running Tests

```bash
mvn test
```
