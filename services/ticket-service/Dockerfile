# syntax=docker/dockerfile:1
# Build and verify the JAR with Java 21 before building this runtime image.
FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
RUN addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app
WORKDIR /app
ARG REVISION=local-dev
LABEL org.opencontainers.image.revision=$REVISION
COPY --chown=10001:10001 target/ticket-service-*.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
