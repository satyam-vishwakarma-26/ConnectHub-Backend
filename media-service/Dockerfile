# ── Build stage ───────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S connecthub && adduser -S connecthub -G connecthub
USER connecthub

COPY --from=builder /build/target/media-service-1.0.0.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
