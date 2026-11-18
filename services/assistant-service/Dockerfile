# syntax=docker/dockerfile:1
# CI verify JAR trước khi build image; node chỉ pull image đã verify.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088 AS extract
WORKDIR /builder
COPY target/assistant-service-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
RUN apk add --no-cache curl jq \
    && addgroup -S -g 10001 app && adduser -S -D -u 10001 -G app app
WORKDIR /app
LABEL io.flashticket.runtime-tools="curl,jq"
COPY --from=extract --chown=10001:10001 /builder/extracted/dependencies/ ./
COPY --from=extract --chown=10001:10001 /builder/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=10001:10001 /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=10001:10001 /builder/extracted/application/ ./
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Duser.timezone=UTC"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
