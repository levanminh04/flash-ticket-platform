# syntax=docker/dockerfile:1
# CI builds and verifies the JAR (mvnw verify) first; the server never builds from source.
FROM eclipse-temurin:21-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474
RUN addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app
WORKDIR /app
COPY --chown=10001:10001 target/*.jar /app/app.jar
# Pinned upstream release asset, verified by BuildKit before use.
ADD --checksum=sha256:e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149 --chmod=0444 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.1/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN chmod 0755 /otel
COPY --chmod=0555 docker-entrypoint.sh /app/docker-entrypoint.sh
ARG SERVICE_VERSION=local-dev
ENV SERVICE_VERSION=$SERVICE_VERSION
ENV SERVICE_NAME=event-service
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Duser.timezone=UTC"
ENTRYPOINT ["/app/docker-entrypoint.sh"]
