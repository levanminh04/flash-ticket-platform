#!/usr/bin/env bash
# Disposable physical PostgreSQL pair for Ticket IT; never accepts ticket_db/live endpoints.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET="$ROOT/services/ticket-service/target"
LABEL=flash-ticket.ticket-replica-fixture
IMAGE=postgres:16.15
fail() { printf '%s\n' "$1" >&2; exit 1; }
dk() { MSYS_NO_PATHCONV=1 docker "$@"; }
load_fixture() {
  local input="$1" key value
  [[ -f "$input" && "$(cd "$(dirname "$input")" && pwd)/$(basename "$input")" == "$TARGET"/ticket-replica.*/replica.env ]] || fail INVALID_FIXTURE_FILE
  while IFS='=' read -r key value || [[ -n "$key" ]]; do
    [[ -z "$key" || "$key" == \#* ]] && continue
    [[ "$key" =~ ^(TICKET_TEST_[A-Z_]+|TICKET_LIST_REPLICA_[A-Z_]+|FIXTURE_[A-Z_]+)$ ]] || fail INVALID_FIXTURE_KEY
    [[ "$value" != *$'\r'* && "$value" != *$'\n'* ]] || fail INVALID_FIXTURE_VALUE
    export "$key=$value"
  done < "$input"
  [[ "${FIXTURE_ID:-}" =~ ^[a-f0-9]{12}$ ]] || fail INVALID_FIXTURE_ID
  [[ "$TICKET_TEST_DB_URL" == "jdbc:postgresql://127.0.0.1:15432/ticket_test_$FIXTURE_ID" || ( "${FIXTURE_SHARED_PRIMARY:-false}" == true && "$TICKET_TEST_DB_URL" == jdbc:postgresql://127.0.0.1:15432/ticket_test_c1 ) ]] || fail INVALID_WRITER_ENDPOINT
  [[ "$TICKET_LIST_REPLICA_JDBC_URL" == "jdbc:postgresql://127.0.0.1:15433/${TICKET_TEST_DB_URL##*/}?targetServerType=secondary" ]] || fail INVALID_READER_ENDPOINT
  [[ "$TICKET_TEST_RABBIT_VHOST" == "ticket_test_$FIXTURE_ID" ]] || fail INVALID_VHOST
}
owned() {
  [[ "$(dk inspect --format "{{index .Config.Labels \"$LABEL\"}}" "$1" 2>/dev/null)" == "$FIXTURE_ID" ]]
}
wait_db() {
  local container="$1" user="$2" db="$3" attempt
  for ((attempt=0;attempt<90;attempt++)); do
    if dk exec "$container" pg_isready -U "$user" -d "$db" >/dev/null 2>&1; then return; fi
    sleep 1
  done
  fail FIXTURE_DATABASE_NOT_READY
}
case "${1:-}" in
prepare)
  shift
  shared_primary=""; shared_rabbit=""
  while (($#)); do
    case "$1" in
      --primary) shared_primary="$2"; shift 2;;
      --rabbit) shared_rabbit="$2"; shift 2;;
      *) fail INVALID_PREPARE_ARGUMENT;;
    esac
  done
  mkdir -p "$TARGET"
  scratch="$(mktemp -d "$TARGET/ticket-replica.XXXXXXXX")"
  chmod 700 "$scratch"
  # Docker CLI trên Windows cần đường dẫn host thật vì dk tắt MSYS argument conversion.
  scratch_host="$scratch"
  if command -v cygpath >/dev/null 2>&1; then scratch_host="$(cygpath -m "$scratch")"; fi
  FIXTURE_ID="$(python -c 'import uuid; print(uuid.uuid4().hex[:12])')"
  prefix="ticket-replica-$FIXTURE_ID"
  password="$(python -c 'import secrets; print(secrets.token_hex(24))')"
  reader_password="$(python -c 'import secrets; print(secrets.token_hex(24))')"
  replication_password="$(python -c 'import secrets; print(secrets.token_hex(24))')"
  rabbit_password="$(python -c 'import secrets; print(secrets.token_hex(24))')"
  database="ticket_test_$FIXTURE_ID"; admin=ticket_ci
  primary="$prefix-writer"; rabbit="$prefix-rabbit"; network="$prefix-net"
  shared=false
  if [[ -n "$shared_primary" ]]; then
    [[ "${GITHUB_ACTIONS:-false}" == true ]] || fail SHARED_PRIMARY_REQUIRES_CI_SERVICE
    [[ "$(dk inspect --format '{{.Config.Image}}' "$shared_primary")" == "$IMAGE" ]] || fail INVALID_CI_PRIMARY_IMAGE
    # GitHub service identity and synthetic DB are part of this job's committed fixture contract.
    primary="$shared_primary"; database=ticket_test_c1; password=ticket_ci_disposable; shared=true
    network="$(dk inspect --format '{{range $name, $net := .NetworkSettings.Networks}}{{$name}}{{end}}' "$primary")"
    [[ -n "$network" && "$network" != *$'\n'* ]] || fail INVALID_CI_NETWORK
  fi
  if [[ -n "$shared_rabbit" ]]; then
    [[ "${GITHUB_ACTIONS:-false}" == true && "$(dk inspect --format '{{.Config.Image}}' "$shared_rabbit")" == rabbitmq:4.3.6-management ]] || fail INVALID_CI_RABBIT
    rabbit="$shared_rabbit"; rabbit_user=guest; rabbit_password=guest
  else
    rabbit_user=ticket_fixture
  fi
  envfile="$scratch/replica.env"
  umask 077
  printf '%s\n' "FIXTURE_ID=$FIXTURE_ID" "FIXTURE_PRIMARY=$primary" "FIXTURE_READER=$prefix-reader" "FIXTURE_RABBIT=$rabbit" "FIXTURE_NETWORK=$network" "FIXTURE_SHARED_PRIMARY=$shared" "FIXTURE_SHARED_RABBIT=$([[ -n "$shared_rabbit" ]] && printf true || printf false)" "TICKET_TEST_DB_URL=jdbc:postgresql://127.0.0.1:15432/$database" "TICKET_TEST_DB_USER=$admin" "TICKET_TEST_DB_PASSWORD=$password" "TICKET_LIST_REPLICA_ENABLED=true" "TICKET_LIST_REPLICA_JDBC_URL=jdbc:postgresql://127.0.0.1:15433/$database?targetServerType=secondary" "TICKET_LIST_REPLICA_USERNAME=ticket_reader" "TICKET_LIST_REPLICA_PASSWORD=$reader_password" "TICKET_LIST_REPLICA_PROBE_INTERVAL=PT1S" "TICKET_LIST_REPLICA_PROBE_QUERY_TIMEOUT=1" "TICKET_LIST_REPLICA_MAX_SAMPLE_AGE=PT2S" "TICKET_LIST_REPLICA_READER_TRANSACTION_TIMEOUT=5" "TICKET_TEST_RABBIT_PORT=5672" "TICKET_TEST_RABBIT_USER=$rabbit_user" "TICKET_TEST_RABBIT_PASSWORD=$rabbit_password" "TICKET_TEST_RABBIT_VHOST=ticket_test_$FIXTURE_ID" > "$envfile"
  # Write identity before creating resources so partial setup can always be cleaned by ownership.
  printf 'Fixture env: %s\n' "$envfile"
  if [[ "$shared" == false ]]; then
    dk network create --label "$LABEL=$FIXTURE_ID" "$network" >/dev/null
    dk volume create --label "$LABEL=$FIXTURE_ID" "$prefix-writer-data" >/dev/null
    printf '%s\n' "POSTGRES_DB=$database" "POSTGRES_USER=$admin" "POSTGRES_PASSWORD=$password" > "$scratch/writer.env"
    dk run -d --name "$primary" --label "$LABEL=$FIXTURE_ID" --network "$network" --env-file "$scratch_host/writer.env" -p 127.0.0.1:15432:5432 -v "$prefix-writer-data:/var/lib/postgresql/data" "$IMAGE" -c wal_level=replica -c max_wal_senders=10 >/dev/null
  fi
  wait_db "$primary" "$admin" "$database"
  [[ "$(dk exec "$primary" psql -U "$admin" -d "$database" -Atqc 'SELECT pg_is_in_recovery()')" == f ]] || fail INVALID_PRIMARY_ROLE
  [[ "$(dk exec "$primary" psql -U "$admin" -d "$database" -Atqc 'SHOW wal_level')" == replica ]] || fail INVALID_PRIMARY_WAL
  dk exec -i "$primary" psql -v ON_ERROR_STOP=1 -U "$admin" -d "$database" >/dev/null <<SQL
CREATE ROLE ticket_owner NOLOGIN;
GRANT ticket_owner TO $admin;
CREATE ROLE ticket_reader LOGIN PASSWORD '$reader_password';
GRANT CONNECT ON DATABASE $database TO ticket_reader;
CREATE ROLE ticket_replicator LOGIN REPLICATION PASSWORD '$replication_password';
SQL
  subnet="$(dk network inspect --format '{{range .IPAM.Config}}{{.Subnet}}{{end}}' "$network")"
  [[ "$subnet" =~ ^[0-9.]+/[0-9]+$ ]] || fail INVALID_FIXTURE_SUBNET
  printf 'host replication ticket_replicator %s scram-sha-256\n' "$subnet" | dk exec -i "$primary" sh -c 'cat >> "$PGDATA/pg_hba.conf"'
  dk exec "$primary" psql -U "$admin" -d "$database" -Atqc 'SELECT pg_reload_conf()' >/dev/null
  primary_address="$(dk inspect --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$primary")"
  [[ "$primary_address" =~ ^[0-9.]+$ ]] || fail INVALID_PRIMARY_ADDRESS
  printf '%s\n' "$primary_address:5432:replication:ticket_replicator:$replication_password" > "$scratch/pgpass"
  dk volume create --label "$LABEL=$FIXTURE_ID" "$prefix-reader-data" >/dev/null
  dk run --rm --name "$prefix-backup" --label "$LABEL=$FIXTURE_ID" --network "$network" -v "$prefix-reader-data:/var/lib/postgresql/data" -v "$scratch_host/pgpass:/seed/pgpass:ro" --entrypoint sh "$IMAGE" -ec 'cp /seed/pgpass /tmp/pgpass; chmod 600 /tmp/pgpass; export PGPASSFILE=/tmp/pgpass; pg_basebackup -h "$1" -U ticket_replicator -D /var/lib/postgresql/data -R -X stream -w; cp /tmp/pgpass /var/lib/postgresql/data/.pgpass; chown -R postgres:postgres /var/lib/postgresql/data; chmod 700 /var/lib/postgresql/data' sh "$primary_address" >/dev/null
  # Persist the password-file path, not a password, in the standby recovery configuration.
  dk run --rm --label "$LABEL=$FIXTURE_ID" -v "$prefix-reader-data:/var/lib/postgresql/data" --entrypoint sh "$IMAGE" -ec 'printf "primary_conninfo = '\''host=%s port=5432 user=ticket_replicator passfile=/var/lib/postgresql/data/.pgpass'\''\n" "$1" >> /var/lib/postgresql/data/postgresql.auto.conf' sh "$primary_address"
  dk run -d --name "$prefix-reader" --label "$LABEL=$FIXTURE_ID" --network "$network" -p 127.0.0.1:15433:5432 -v "$prefix-reader-data:/var/lib/postgresql/data" "$IMAGE" >/dev/null
  wait_db "$prefix-reader" "$admin" "$database"
  dk exec "$primary" psql -v ON_ERROR_STOP=1 -U "$admin" -d "$database" -qc 'CREATE TABLE public.ticket_replica_fixture_marker(id int PRIMARY KEY); INSERT INTO public.ticket_replica_fixture_marker VALUES(1)' >/dev/null
  ready=false
  for ((attempt=0;attempt<60;attempt++)); do
    if [[ "$(dk exec "$prefix-reader" psql -U "$admin" -d "$database" -Atqc 'SELECT pg_is_in_recovery() AND EXISTS(SELECT 1 FROM public.ticket_replica_fixture_marker WHERE id=1)' 2>/dev/null || true)" == t ]]; then ready=true; break; fi
    sleep 1
  done
  [[ "$ready" == true ]] || fail STANDBY_REPLAY_MARKER_NOT_READY
  if [[ -z "$shared_rabbit" ]]; then
    printf '%s\n' "RABBITMQ_DEFAULT_USER=$rabbit_user" "RABBITMQ_DEFAULT_PASS=$rabbit_password" > "$scratch/rabbit.env"
    dk run -d --name "$rabbit" --label "$LABEL=$FIXTURE_ID" --network "$network" --env-file "$scratch_host/rabbit.env" -p 127.0.0.1:5672:5672 rabbitmq:4.3.6-management >/dev/null
  fi
  ready=false
  for ((attempt=0;attempt<90;attempt++)); do
    if dk exec "$rabbit" rabbitmq-diagnostics -q ping >/dev/null 2>&1; then ready=true; break; fi
    sleep 1
  done
  [[ "$ready" == true ]] || fail FIXTURE_RABBIT_NOT_READY
  dk exec "$rabbit" rabbitmqctl add_vhost "ticket_test_$FIXTURE_ID" >/dev/null
  dk exec "$rabbit" rabbitmqctl set_permissions -p "ticket_test_$FIXTURE_ID" "$rabbit_user" '.*' '.*' '.*' >/dev/null
  printf 'Fixture ready: writer/physical standby/private broker vhost; id=%s\n' "$FIXTURE_ID"
  ;;
run)
  (($# >= 3)) || fail 'Usage: run generated-replica.env [Maven test selectors] test|verify'
  load_fixture "$2"; shift 2
  owned "$FIXTURE_READER" || fail READER_OWNERSHIP_MISMATCH
  for argument in "$@"; do
    [[ "$argument" == test || "$argument" == verify || "$argument" =~ ^-Dtest=[A-Za-z0-9_,#+*]+$ ]] || fail INVALID_MAVEN_SELECTION
  done
  cd "$ROOT/services/ticket-service"
  mvn -B -ntp "$@"
  ;;
cleanup)
  (($# == 2)) || fail 'Usage: cleanup generated-replica.env'
  load_fixture "$2"
  for container in "$FIXTURE_READER" "ticket-replica-$FIXTURE_ID-backup" "$FIXTURE_PRIMARY" "$FIXTURE_RABBIT"; do
    if owned "$container"; then dk rm -f "$container" >/dev/null; fi
  done
  if [[ "$FIXTURE_SHARED_RABBIT" == true ]]; then
    [[ "${GITHUB_ACTIONS:-false}" == true ]] || fail SHARED_RABBIT_CLEANUP_REQUIRES_CI
    dk exec "$FIXTURE_RABBIT" rabbitmqctl delete_vhost "$TICKET_TEST_RABBIT_VHOST" >/dev/null
  fi
  for volume in "ticket-replica-$FIXTURE_ID-writer-data" "ticket-replica-$FIXTURE_ID-reader-data"; do
    if [[ "$(dk volume inspect --format "{{index .Labels \"$LABEL\"}}" "$volume" 2>/dev/null || true)" == "$FIXTURE_ID" ]]; then dk volume rm "$volume" >/dev/null; fi
  done
  if [[ "$(dk network inspect --format "{{index .Labels \"$LABEL\"}}" "$FIXTURE_NETWORK" 2>/dev/null || true)" == "$FIXTURE_ID" ]]; then dk network rm "$FIXTURE_NETWORK" >/dev/null; fi
  # Only this invocation's generated files; preserve logs for diagnosis.
  rm -f "$(dirname "$2")/writer.env" "$(dirname "$2")/rabbit.env" "$(dirname "$2")/pgpass" "$2"
  printf 'Fixture cleaned: %s\n' "$FIXTURE_ID"
  ;;
*) fail 'Usage: prepare [--primary CI_ID] [--rabbit CI_ID] | run ENV ... | cleanup ENV';;
esac
