#!/usr/bin/env bash
# Static checks on source-controlled configuration.
set -euo pipefail
. "$(dirname "$0")/lib.sh"

# --- keycloak has its own env template with the variables the server compose used to inline
for v in KC_DB_URL KC_DB_USERNAME KC_DB_PASSWORD KC_HOSTNAME KC_HTTP_ENABLED KC_BOOTSTRAP_ADMIN_PASSWORD \
         FT_IDENTITY_RABBITMQ_HOST FT_IDENTITY_RABBITMQ_USERNAME FT_IDENTITY_RABBITMQ_PASSWORD; do
  grep -q "^$v=" "$root/services/keycloak-spi/.env.example" || fail "keycloak .env.example missing $v"
done
# --- Java 21 everywhere
for p in services/booking-service services/ticket-service; do
  grep -q '<java.version>21</java.version>' "$root/$p/pom.xml" || fail "$p: java.version must be 21"
  grep -q '<version>\[21,22)</version>' "$root/$p/pom.xml" || fail "$p: enforcer range must be [21,22)"
done

# --- Eureka status must follow /actuator/health, and the QR key file name must match the server (keys.json)
for p in infrastructure/api-gateway services/user-service services/event-service \
         services/ticket-service services/booking-service services/payment-service; do
  awk '/^eureka:/{e=1;next} /^[^ ]/{e=0}
       e&&/healthcheck:/{h=1;next}
       h==1{ if ($0 ~ /enabled: true/) ok=1; h=0 }
       END{exit !ok}' "$root/$p/src/main/resources/application.yaml" \
    || fail "$p: eureka.client.healthcheck.enabled: true missing"
done
grep -q 'TICKET_QR_KEY_RING_LOCATION=file:/run/ticket-qr/keys.json' "$root/services/ticket-service/.env.example" \
  || fail "ticket .env.example must point to /run/ticket-qr/keys.json"

# --- the four hand-rolled deploy workflows/scripts and the 119 leftovers are gone
for f in .github/workflows/deploy-booking.yml .github/workflows/deploy-event.yml .github/workflows/deploy-ticket.yml \
         .github/workflows/deploy-user.yml deploy/deploy-booking.sh deploy/deploy-event.sh deploy/deploy-ticket.sh \
         deploy/deploy-user.sh deploy/docker-compose.payment.ft119.yml deploy/ngrok/payment.yml deploy/docker-compose.keycloak.yml; do
  [[ ! -e "$root/$f" ]] || fail "$f must be deleted"
done
if grep -rn -i 'FT119\|config-server\|CONFIG_SERVER' "$root/.github"; then fail ".github still mentions 119 or config-server"; fi
# --- every third-party action is pinned to a commit SHA
if grep -rhn 'uses:' "$root/.github/workflows" | grep -v -E '@[0-9a-f]{40}|uses: \./'; then fail "unpinned action found"; fi

echo "test-config OK"
