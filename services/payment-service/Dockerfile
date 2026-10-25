# syntax=docker/dockerfile:1
# ──────────────────────────────────────────────────────────────────────────────
# payment-service Dockerfile
# Base: eclipse-temurin:21-jre-alpine (slim JRE, Java 21 required by enforcer)
# Build: multi-stage — Maven build in builder, only JAR in runtime image
# ──────────────────────────────────────────────────────────────────────────────

# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Cache Maven dependencies first
COPY .mvn/ .mvn/
COPY mvnw mvnw.cmd ./
COPY pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline -q

# Copy source and build (Spotless check runs in verify; skip tests here for image build)
COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Non-root user
RUN addgroup -S payment && adduser -S payment -G payment
USER payment

COPY --from=builder /build/target/payment-service-*.jar app.jar

# Expose default Spring Boot port
EXPOSE 8080

# JVM tuning: container-aware heap, virtual threads friendly
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
