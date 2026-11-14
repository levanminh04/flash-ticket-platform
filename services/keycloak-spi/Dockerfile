FROM quay.io/keycloak/keycloak:26.8.0
ARG SERVICE_VERSION=local-dev
LABEL org.opencontainers.image.version=$SERVICE_VERSION
COPY --chown=keycloak:keycloak target/keycloak-registration-outbox-1.0.0.jar target/dependency/amqp-client-*.jar /opt/keycloak/providers/
ENV KC_TRACING_ENABLED=true
RUN JAVA_OPTS_APPEND="-Xmx384m" FT_IDENTITY_OUTBOX_ENABLED=false /opt/keycloak/bin/kc.sh build --db=postgres
