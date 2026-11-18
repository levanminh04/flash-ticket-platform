# syntax=docker/dockerfile:1
# CI builds and verifies the JAR (mvnw verify) first; the server never builds from source.
FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
RUN apk add --no-cache curl jq \
    && addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app
WORKDIR /app
LABEL io.flashticket.runtime-tools="curl,jq"
COPY --chown=10001:10001 target/*.jar /app/app.jar
# Pinned upstream release asset, verified by BuildKit before use.
ADD --checksum=sha256:e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149 --chmod=0444 https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.33.1/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
RUN chmod 0755 /otel
COPY --chmod=0555 docker-entrypoint.sh /app/docker-entrypoint.sh
ARG SERVICE_VERSION=local-dev
ENV SERVICE_VERSION=$SERVICE_VERSION
ENV SERVICE_NAME=api-gateway
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Duser.timezone=UTC"
ENTRYPOINT ["/app/docker-entrypoint.sh"]
