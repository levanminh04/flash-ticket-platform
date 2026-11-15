# syntax=docker/dockerfile:1
# CI builds and verifies the JAR (mvnw verify) first; the server never builds from source.
FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
RUN apk add --no-cache curl jq \
    && addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app
WORKDIR /app
LABEL io.flashticket.runtime-tools="curl,jq"
COPY --chown=10001:10001 target/*.jar /app/app.jar
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Duser.timezone=UTC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
