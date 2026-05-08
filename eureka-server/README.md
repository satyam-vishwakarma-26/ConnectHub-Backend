# ConnectHub — eureka-server

> **Port:** `8761` | Service Discovery for all ConnectHub microservices

Netflix Eureka service registry. Every microservice registers here on startup.
The API Gateway reads the registry to route requests by service name instead
of hardcoded host:port addresses.

---

## Quick Start

```bash
cd eureka-server
mvn spring-boot:run
```

Dashboard → `http://localhost:8761`
Login: `admin` / `connecthub-eureka-secret`

Or with Docker:
```bash
docker build -t connecthub/eureka-server .
docker run -p 8761:8761 connecthub/eureka-server
```

---

## How each microservice registers

Add these to every service's `pom.xml`:

```xml
<!-- Spring Cloud BOM in <dependencyManagement> -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>2023.0.1</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- Eureka client dependency -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>
```

Add to each service's `application.yml`:

```yaml
eureka:
  client:
    service-url:
      # Include credentials because dashboard is password-protected
      defaultZone: http://admin:connecthub-eureka-secret@localhost:8761/eureka/
  instance:
    prefer-ip-address: true
    # Heartbeat & lease intervals — shorter for faster dev feedback
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

---

## Registered Services

Once all services are running you will see:

| Application | Port |
|---|---|
| AUTH-SERVICE | 8081 |
| ROOM-SERVICE | 8082 |
| MESSAGE-SERVICE | 8083 |
| MEDIA-SERVICE | 8084 |
| PRESENCE-SERVICE | 8085 |
| WEBSOCKET-HANDLER | 8086 |
| NOTIFICATION-SERVICE | 8087 |
| API-GATEWAY | 8080 |

---

## Self-Preservation Mode

Self-preservation is **disabled** in this config (`enable-self-preservation: false`).
This is intentional for development — instances get removed immediately when they stop
sending heartbeats.

**Enable it for production:**
```yaml
eureka:
  server:
    enable-self-preservation: true
