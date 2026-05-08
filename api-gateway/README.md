# ConnectHub — api-gateway

> **Port:** `8080` | Single entry point for all ConnectHub microservices.
> Built with **Spring Cloud Gateway** (reactive, Netty-based).

---

## What it does

| Responsibility | Detail |
|---|---|
| **Routing** | Forwards requests to services by name via Eureka (`lb://SERVICE-NAME`) |
| **JWT validation** | `AuthenticationFilter` checks Bearer token before forwarding |
| **Header injection** | Adds `X-Auth-User-Id`, `X-Auth-User-Email`, `X-Auth-User-Role` to every request so downstream services don't need to re-validate |
| **CORS** | Global CORS configured once here — no per-service CORS config needed |
| **Rate limiting** | `RequestRateLimiter` filter on public auth endpoints (login, register) |
| **Logging** | `LoggingFilter` prints method, path, status, and latency for every request |
| **Load balancing** | Spring Cloud LoadBalancer picks instances registered in Eureka |
| **Fallbacks** | `FallbackController` returns a structured JSON 503 when a service is down |

---

## Quick Start

**Prerequisites:** Eureka server must be running on `localhost:8761`.

```bash
cd api-gateway
mvn spring-boot:run
```

Or with Docker Compose (starts Eureka + Gateway together):

```bash
docker-compose up --build
```

Gateway at `http://localhost:8080`
Actuator at `http://localhost:8080/actuator/health`
Gateway routes at `http://localhost:8080/actuator/gateway/routes`

---

## Project Structure

```
api-gateway/
├── src/main/java/com/connecthub/gateway/
│   ├── ApiGatewayApplication.java          ← @EnableDiscoveryClient entry point
│   ├── config/
│   │   ├── GatewayConfig.java              ← OPTIONS preflight pass-through bean
│   │   ├── JwtUtil.java                    ← JWT signature + expiry validation
│   │   └── RateLimiterConfig.java          ← KeyResolver beans (user / IP)
│   └── filter/
│       ├── AuthenticationFilter.java       ← Named filter: validates JWT, injects headers
│       ├── LoggingFilter.java              ← Global filter: logs all requests + timing
│       └── FallbackController.java         ← 503 fallback responses per service
├── src/main/resources/
│   └── application.yml                     ← All routes, CORS, Eureka, JWT config
├── Dockerfile
├── docker-compose.yml                      ← Starts Eureka + Gateway together
└── README.md
```

---

## Route Map

Every service is routed by its Eureka application name (`lb://SERVICE-NAME`).

| Path prefix | Forwards to | JWT required |
|---|---|---|
| `POST /api/auth/register` | `AUTH-SERVICE` | No |
| `POST /api/auth/login` | `AUTH-SERVICE` | No |
| `POST /api/auth/refresh` | `AUTH-SERVICE` | No |
| `/api/auth/oauth2/**` | `AUTH-SERVICE` | No |
| `/api/auth/**` | `AUTH-SERVICE` | **Yes** |
| `/api/rooms/**` | `ROOM-SERVICE` | **Yes** |
| `/api/messages/**` | `MESSAGE-SERVICE` | **Yes** |
| `/api/media/**` | `MEDIA-SERVICE` | **Yes** |
| `/api/presence/**` | `PRESENCE-SERVICE` | **Yes** |
| `/api/notifications/**` | `NOTIFICATION-SERVICE` | **Yes** |
| `/ws/**` | `WEBSOCKET-HANDLER` | No (token in STOMP CONNECT header) |

---

## JWT Header Injection

After the `AuthenticationFilter` validates the token it **removes the original
Authorization header** (so upstream services never see the raw JWT) and replaces
it with three safe headers:

```
X-Auth-User-Id:    42
X-Auth-User-Email: alice@example.com
X-Auth-User-Role:  USER
```

Downstream services read these headers instead of re-parsing the JWT:

```java
// In any downstream @RestController
@GetMapping("/profile")
public ResponseEntity<?> profile(
        @RequestHeader("X-Auth-User-Id") Long userId) {
    // No JWT parsing needed here
}
```

---

## Adding a New Service

1. Add Eureka client to the service (see eureka-server README).
2. Add a route block in `application.yml`:

```yaml
- id: my-new-service
  uri: lb://MY-NEW-SERVICE
  predicates:
    - Path=/api/my-resource/**
  filters:
    - AuthenticationFilter
    - RewritePath=/api/my-resource/(?<segment>.*), /api/my-resource/${segment}
```

3. Restart the gateway — no code changes required.

---

## Rate Limiting

The `RequestRateLimiter` on public auth endpoints requires Redis.
Configure Redis in `application.yml` for production:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

Without Redis the filter is skipped (fail-open).

---

## cURL Examples

All requests go through port **8080** — you never call individual service ports directly.

```bash
# Register (public)
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","username":"alice","password":"secret123"}'

# Login (public)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}'

# Get profile (JWT required — gateway validates and forwards)
curl http://localhost:8080/api/auth/profile \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

# Get my rooms (JWT required)
curl http://localhost:8080/api/rooms/my \
  -H "Authorization: Bearer <ACCESS_TOKEN>"

# Check gateway routes
curl http://localhost:8080/actuator/gateway/routes
```
