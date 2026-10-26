FROM quay.io/keycloak/keycloak:26.8.0
COPY --chown=keycloak:keycloak target/keycloak-registration-outbox-1.0.0.jar target/dependency/amqp-client-5.26.0.jar /opt/keycloak/providers/
RUN JAVA_OPTS_APPEND="-Xmx384m" FT_IDENTITY_OUTBOX_ENABLED=false /opt/keycloak/bin/kc.sh build --db=postgres
