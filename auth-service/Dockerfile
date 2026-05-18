# ── Stage 1: Build ────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
# Download dependencies first (cached layer)
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S connecthub && adduser -S connecthub -G connecthub
USER connecthub

COPY --from=builder /build/target/auth-service-1.0.0.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8081

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
