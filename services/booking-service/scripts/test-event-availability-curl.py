"""Run the compiled production JAR with disposable dependencies; assert real curl responses.

Requires Docker Desktop, Python stdlib and curl. No Java declarations/test hooks,
shared services, root .env, pip dependencies or tokens in command arguments/logs.
"""

import copy
import datetime as dt
import hashlib
import json
from pathlib import Path
import secrets
import shutil
import subprocess
import time
import urllib.parse
import uuid
import zipfile


ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "services/booking-service/target/availability-curl"
BASE = "http://127.0.0.1:38082"
KC = "http://127.0.0.1:38080/realms/availability-curl"
BROKER = "http://127.0.0.1:35684"
NETWORK = "booking-availability-curl-net"
NAMES = {key: "booking-availability-curl-" + key for key in ["pg", "rabbit", "kc", "redis", "app"]}
CURL = shutil.which("curl.exe") or shutil.which("curl")
CREATED = []
SECRET_FILES = []
CASES = []
HTTP = []
TOKENS = {}
ENV = {}
STARTED = None
NETWORK_CREATED = False


def command(args, data=None, timeout=50):
    result = subprocess.run(args, input=data, capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=timeout)
    if result.returncode:
        # Never echo command/config input, which may contain a JWT or fixture secret.
        raise RuntimeError(f"{args[0]} failed ({result.returncode}): {result.stderr[-900:]}")
    return result.stdout.strip()


def curl(url, method="GET", body=None, token=None, headers=None, form=None, auth=None, record=True):
    header_path = OUT / "last-response.headers"
    settings = ["silent", "show-error", 'max-time = "20"',
                "url = " + json.dumps(url), "request = " + json.dumps(method),
                "dump-header = " + json.dumps(str(header_path).replace("\\", "/")),
                'write-out = "\\n__CURL_META__%{http_code} %{time_total}"']
    if token:
        settings.append("header = " + json.dumps("Authorization: Bearer " + token))
    for key, value in (headers or {}).items():
        settings.append("header = " + json.dumps(key + ": " + value))
    if body is not None:
        settings.extend(['header = "Content-Type: application/json"', "data = " + json.dumps(json.dumps(body))])
    if form is not None:
        settings.extend(['header = "Content-Type: application/x-www-form-urlencoded"',
                         "data = " + json.dumps(urllib.parse.urlencode(form))])
    if auth:
        settings.append("user = " + json.dumps(":".join(auth)))
    raw = command([CURL, "--config", "-"], "\n".join(settings))
    payload, marker, status = raw.rpartition("__CURL_META__")
    assert marker, "curl response is missing transfer metadata"
    status, duration = status.split()
    response = json.loads(payload) if payload else None
    response_headers = {}
    for line in header_path.read_text(encoding="utf-8").splitlines():
        if ":" in line:
            key, value = line.split(":", 1)
            response_headers[key.lower()] = value.strip()
    if record:
        HTTP.append({"method": method, "path": url.removeprefix(BASE), "status": int(status),
                     "seconds": float(duration), "headers": response_headers, "response": response})
    return int(status), response, response_headers


def wait_for(action, seconds=90):
    end = time.monotonic() + seconds
    last = None
    while time.monotonic() < end:
        try:
            value = action()
            if value:
                return value
        except (RuntimeError, ValueError, AssertionError) as exc:
            last = str(exc)[:250]
        time.sleep(0.5)
    raise RuntimeError("Condition timeout: " + str(last))


def sql(statement):
    return command(["docker", "exec", "-i", NAMES["pg"], "psql", "-X", "-q", "-A", "-t",
                    "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "booking_db"], statement)


def instant(seconds=0):
    return (dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=seconds)).isoformat().replace("+00:00", "Z")


def new_id():
    return str(uuid.uuid4())


def env_file(name, values):
    path = OUT / (name + ".env")
    path.write_text("\n".join(key + "=" + value for key, value in values.items()) + "\n", encoding="utf-8")
    SECRET_FILES.append(path)
    return path


def start(key, image, arguments=None, tail=None, env=None):
    args = ["docker", "run", "--detach", "--rm", "--name", NAMES[key],
            "--network", NETWORK, "--label", "codex.task=booking-availability-curl"]
    if env:
        args.extend(["--env-file", str(env)])
    command(args + (arguments or []) + [image] + (tail or []))
    CREATED.append(key)
    print("Started disposable " + key, flush=True)


def start_app():
    start("app", "maven:3.9.9-eclipse-temurin-23", ["--memory", "768m", "-p", "127.0.0.1:38082:8082",
          "--mount", "type=bind,source=" + str(JAR) + ",target=/runtime/app.jar,readonly",
          "--mount", "type=bind,source=" + str(ROOT / "services/booking-service/src/main/resources/application.yaml")
          + ",target=/runtime/booking-service.yaml,readonly"], ["java", "-jar", "/runtime/app.jar"], BOOKING_ENV)
    wait_for(lambda: curl(BASE + "/actuator/health", record=False)[1].get("status") == "UP", 120)
    print("Production JAR health UP with real PostgreSQL/Rabbit/Redis/Keycloak", flush=True)


def broker(path, body=None):
    status, response, _ = curl(BROKER + path, "POST" if body is not None else "GET", body=body,
                              auth=(ENV["RABBITMQ_USERNAME"], ENV["RABBITMQ_PASSWORD"]), record=False)
    assert 200 <= status < 300, "Disposable broker HTTP " + str(status)
    return response


def publish(kind, aggregate, data, version="1"):
    message = {"messageId": new_id(), "messageType": kind, "schemaVersion": 1,
               "aggregateId": aggregate, "aggregateVersion": version, "occurredAt": instant(),
               "correlationId": new_id(), "causationId": None, "traceparent": None, "data": data}
    result = broker("/api/exchanges/%2F/business.ex/publish", {
        "properties": {"content_type": "application/json", "delivery_mode": 2},
        "routing_key": kind, "payload": json.dumps(message), "payload_encoding": "string"})
    assert result["routed"], "Fixture message was not routed"
    consumer = {"event.sales-configured.v1": "booking.sales-configured",
                "event.cancelled.v1": "booking.event-cancelled",
                "booking.accept-payment.v1": "booking.accept-payment"}[kind]
    wait_for(lambda: sql(f"select count(*) from booking_schema.tbl_inbox_messages where consumer_name='{consumer}' and message_id='{message['messageId']}'") == "1")
    return message


def configuration(mode="QUANTITY", kind=None, capacity=10):
    event, ticket, sector = new_id(), new_id(), new_id()
    return {"schemaVersion": 1, "eventId": event, "organizerSubject": "curl-organizer",
            "sourceConfigVersion": "1", "eventStatus": "PUBLISHED", "eventTitle": "Availability curl fixture",
            "eventStartsAt": instant(7200), "eventEndsAt": instant(10800), "venueName": None,
            "mode": mode, "saleStartsAt": instant(-120), "saleEndsAt": instant(3600), "purchaseLimit": 20,
            "ticketTypes": [{"ticketTypeId": ticket, "name": "Standard", "unitPrice": "100", "currency": "VND",
                             "sectorId": None if mode == "QUANTITY" else sector,
                             "configuredQuantity": str(capacity) if mode == "QUANTITY" else None, "colorCode": None}],
            "sectors": [] if mode == "QUANTITY" else [{"sectorId": sector, "kind": kind, "configuredCapacity": str(capacity)}],
            "seats": [], "promotions": []}


def seed(config):
    publish("event.sales-configured.v1", config["eventId"], config, config["sourceConfigVersion"])
    return config


def selection(config, quantity=1, ticket=None, seats=None):
    ticket = ticket or config["ticketTypes"][0]["ticketTypeId"]
    if config["mode"] == "QUANTITY":
        return {"mode": "QUANTITY", "items": [{"ticketTypeId": ticket, "quantity": quantity}]}
    sector = next(t["sectorId"] for t in config["ticketTypes"] if t["ticketTypeId"] == ticket)
    kind = next(s["kind"] for s in config["sectors"] if s["sectorId"] == sector)
    result = {"mode": "SEAT_MAP", "kind": kind, "sectorId": sector}
    result.update({"seatIds": seats} if kind == "SEATED" else {"items": [{"ticketTypeId": ticket, "quantity": quantity}]})
    return result


def create_order(config, quantity=1, ticket=None, seats=None, expected=201):
    status, body, _ = curl(BASE + "/api/v1/orders", "POST", token=TOKENS["buyer"],
                           headers={"Idempotency-Key": new_id()}, body={"eventId": config["eventId"],
                           "customerEmail": "curl@example.test", "selection": selection(config, quantity, ticket, seats)})
    assert status == expected, f"Order fixture HTTP {status}: {body.get('code')}"
    return body


def accept(order):
    status, snapshot, _ = curl(BASE + "/internal/v1/orders/" + order["orderId"] + "/purchase-snapshot", "POST",
                               token=TOKENS["internal"], body={"buyerSubject": TOKENS["buyer_subject"]})
    assert status == 200, f"Freeze fixture HTTP {status}: {snapshot.get('code')}"
    publish("booking.accept-payment.v1", order["orderId"], {"orderId": order["orderId"], "chargeId": new_id(),
            "attemptId": new_id(), "amount": snapshot["total"], "currency": snapshot["currency"],
            "sourceOrderVersion": snapshot["sourceOrderVersion"]}, snapshot["sourceOrderVersion"])
    assert sql(f"select lifecycle_state from booking_schema.tbl_orders where id='{order['orderId']}'") == "ISSUING"


def availability(config, expected=200, code=None, token=None):
    event_id = config["eventId"] if isinstance(config, dict) else config
    status, body, headers = curl(BASE + "/api/v1/events/" + event_id + "/availability", token=token)
    assert status == expected, f"Availability HTTP {status}, expected {expected}: {body.get('code')}"
    if code:
        assert body["code"] == code, f"Problem code {body.get('code')}, expected {code}"
        assert "application/json" in headers.get("content-type", ""), "Expected the existing Booking Problem JSON media type"
        assert body["correlationId"] == headers["x-correlation-id"]
        assert set(body) == {"code", "message", "correlationId", "violations"}
    if status == 200:
        assert headers.get("cache-control") == "no-store"
        assert body["eventId"] == event_id and body["asOf"]
        assert set(body) == {"eventId", "sourceConfigVersion", "asOf", "ticketTypes", "sectors", "seats"}
        assert all(isinstance(t["available"], str) for t in body["ticketTypes"])
        assert all(isinstance(s["available"], str) for s in body["sectors"])
    return body


def counts(body):
    return {t["ticketTypeId"]: t["available"] for t in body["ticketTypes"]}


def fingerprints():
    tables = sql("select tablename from pg_tables where schemaname='booking_schema' order by tablename").splitlines()
    return {table: sql("select md5(coalesce(jsonb_agg(to_jsonb(t) order by to_jsonb(t)::text)::text,'null')) from booking_schema."
                       + table + " t") for table in tables}


def save(error=None):
    (OUT / "results.json").write_text(json.dumps({"startedAt": STARTED, "finishedAt": instant(),
        "jarSHA256": hashlib.sha256(JAR.read_bytes()).hexdigest(), "runtime": "production JAR, curl, PostgreSQL17, RabbitMQ4.3.6, Redis7.4, Keycloak26.8",
        "passed": len(CASES), "failed": 1 if error else 0, "error": error, "cases": CASES,
        "http": HTTP, "shared119Touched": False, "tokensRecorded": False}, indent=2), encoding="utf-8")


def passed(title, evidence):
    CASES.append({"case": f"A{len(CASES)+1:02}", "description": title, "result": "PASS", "evidence": evidence})
    save()
    print(CASES[-1]["case"] + " PASS: " + title, flush=True)


JAR = ROOT / "services/booking-service/target/booking-service-0.0.1-SNAPSHOT.jar"
BOOKING_ENV = OUT / "booking.env"


def main():
    global STARTED, ENV, NETWORK_CREATED
    assert CURL, "curl is required"
    assert JAR.is_file(), "Build the current production JAR before running"
    with zipfile.ZipFile(JAR) as archive:
        for path in ["availability/controller/AvailabilityController.class", "availability/service/AvailabilityService.class",
                     "availability/service/impl/AvailabilityServiceImpl.class"]:
            compiled = ROOT / "services/booking-service/target/classes/com/flashticket/booking_service" / path
            assert compiled.read_bytes() == archive.read("BOOT-INF/classes/com/flashticket/booking_service/" + path), "Stale JAR"
    OUT.mkdir(parents=True, exist_ok=True)
    STARTED = instant()
    template = Path(__file__).with_name(".env.example")
    ENV = dict(line.split("=", 1) for line in template.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#"))
    password = secrets.token_urlsafe(24)
    ENV.update({key: password for key in ["POSTGRES_PASSWORD", "BOOKING_DB_PASSWORD", "RABBITMQ_DEFAULT_PASS", "RABBITMQ_PASSWORD"]})
    ENV["INTERNAL_SERVICE_CLIENT_SECRET"] = secrets.token_urlsafe(24)
    # Persist every runtime value first; Docker loads the files without CLI overrides.
    pg_env = env_file("pg", {k: ENV[k] for k in ["POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD"]})
    rabbit_env = env_file("rabbit", {k: ENV[k] for k in ["RABBITMQ_DEFAULT_USER", "RABBITMQ_DEFAULT_PASS"]})
    kc_env = env_file("keycloak", {k: ENV[k] for k in ["KC_HOSTNAME", "JAVA_OPTS_KC_HEAP"]})
    env_file("booking", {k: v for k, v in ENV.items() if not k.startswith(("POSTGRES_", "RABBITMQ_DEFAULT_", "KC_", "JAVA_OPTS_KC_"))})
    print("Runtime config written to target/availability-curl/{pg,rabbit,keycloak,booking}.env; secrets hidden", flush=True)
    command(["docker", "network", "create", "--label", "codex.task=booking-availability-curl", NETWORK])
    NETWORK_CREATED = True
    realm_file = OUT / "realm.json"
    SECRET_FILES.append(realm_file)
    buyer_id = new_id()
    realm_file.write_text(json.dumps({"realm": "availability-curl", "enabled": True, "sslRequired": "none",
        "roles": {"realm": [{"name": role} for role in ["BUYER", "INTERNAL_SERVICE"]]},
        "clients": [{"clientId": "curl-public", "publicClient": True, "directAccessGrantsEnabled": True},
                    {"clientId": "internal-service", "secret": ENV["INTERNAL_SERVICE_CLIENT_SECRET"], "serviceAccountsEnabled": True}],
        "users": [{"id": buyer_id, "username": "buyer", "enabled": True, "email": "buyer@example.test", "emailVerified": True,
                   "firstName": "Curl", "lastName": "Buyer", "realmRoles": ["BUYER"],
                   "credentials": [{"type": "password", "value": password, "temporary": False}]},
                  {"username": "service-account-internal-service", "enabled": True,
                   "serviceAccountClientId": "internal-service", "realmRoles": ["INTERNAL_SERVICE"]}]}), encoding="utf-8")
    start("pg", "postgres:17-alpine", env=pg_env)
    start("rabbit", "rabbitmq:4.3.6-management", ["-p", "127.0.0.1:35684:15672"], env=rabbit_env)
    start("redis", "redis:7.4.11-alpine")
    start("kc", "quay.io/keycloak/keycloak:26.8.0", ["--memory", "640m", "-p", "127.0.0.1:38080:8080",
          "--mount", "type=bind,source=" + str(realm_file) + ",target=/opt/keycloak/data/import/realm.json,readonly"],
          ["start-dev", "--import-realm"], kc_env)
    wait_for(lambda: sql("select 1") == "1")
    sql("CREATE ROLE booking_owner NOLOGIN; CREATE ROLE booking_admin LOGIN PASSWORD '" + password
        + "'; GRANT booking_owner TO booking_admin; CREATE SCHEMA booking_schema AUTHORIZATION booking_owner;")
    for source in ["docs/architecture/data/postgres/20-booking.sql", "services/99-table-prefix.sql",
                   "services/booking-service/database/2026-10-31-event-cancellation-indexes.sql"]:
        sql((ROOT / source).read_text(encoding="utf-8"))
    wait_for(lambda: broker("/api/overview"))
    broker("/api/definitions", json.loads((ROOT / "deploy/rabbitmq/definitions.json").read_text(encoding="utf-8")))
    wait_for(lambda: curl(KC + "/.well-known/openid-configuration", record=False)[0] == 200)
    for actor, form in [("buyer", {"grant_type": "password", "client_id": "curl-public", "username": "buyer", "password": password}),
                        ("internal", {"grant_type": "client_credentials", "client_id": "internal-service", "client_secret": ENV["INTERNAL_SERVICE_CLIENT_SECRET"]})]:
        status, body, _ = curl(KC + "/protocol/openid-connect/token", "POST", form=form, record=False)
        assert status == 200, "Keycloak token request failed"
        TOKENS[actor] = body["access_token"]
    TOKENS["buyer_subject"] = buyer_id
    start_app()
    run_cases()


def run_cases():
    availability("invalid-uuid", 400, "INVALID_REQUEST")
    passed("Malformed event UUID", "HTTP400 INVALID_REQUEST")
    availability(new_id(), 503, "CONFIG_NOT_READY")
    passed("Unknown event", "HTTP503 CONFIG_NOT_READY")
    availability(new_id(), 401, "UNAUTHENTICATED", token="invalid.jwt.token")
    passed("Invalid Bearer on public route", "HTTP401 UNAUTHENTICATED")

    quantity = seed(configuration())
    body = availability(quantity)
    assert counts(body) == {quantity["ticketTypes"][0]["ticketTypeId"]: "10"}
    assert body["sectors"] == body["seats"] == [] and body["sourceConfigVersion"] == "1"
    passed("Public QUANTITY fresh availability", "HTTP200, no JWT, 10 available, no-store, contract fields only")
    order = create_order(quantity, 2)
    assert list(counts(availability(quantity)).values()) == ["8"]
    passed("QUANTITY after real HTTP reservation", "HTTP201 order; GET200 available8; held2")
    accept(order)
    assert sql(f"select held||':'||purchased from booking_schema.tbl_ticket_type_inventory where event_id='{quantity['eventId']}'") == "0:2"
    assert list(counts(availability(quantity)).values()) == ["8"]
    passed("QUANTITY after real AcceptPayment consumer", "HTTP freeze + Rabbit consumer; purchased2; GET available8")
    create_order(quantity, 8)
    assert list(counts(availability(quantity)).values()) == ["0"]
    passed("Sold out QUANTITY", "HTTP200 available string0; held8+purchased2=capacity10")

    standing = configuration("SEAT_MAP", "STANDING", 8)
    second = copy.deepcopy(standing["ticketTypes"][0]); second.update({"ticketTypeId": new_id(), "name": "Premium"})
    standing["ticketTypes"].append(second); seed(standing)
    create_order(standing, 2); create_order(standing, 1, second["ticketTypeId"])
    body = availability(standing)
    assert list(counts(body).values()) == ["5", "5"] and body["sectors"][0]["available"] == "5" and body["seats"] == []
    passed("Two STANDING ticket types share one pool", "capacity8-held3=5; each type5, sector5 (not10)")

    mixed = configuration("SEAT_MAP", "SEATED", 4)
    seated_type = mixed["ticketTypes"][0]["ticketTypeId"]
    for index in range(4):
        mixed["seats"].append({"seatId": new_id(), "sectorId": mixed["sectors"][0]["sectorId"], "ticketTypeId": seated_type,
                               "seatLabel": "A" + str(index+1), "rowName": "A", "seatNumber": str(index+1), "isHidden": index == 3})
    mixed["sectors"].extend(copy.deepcopy(standing["sectors"]))
    # New IDs ensure neither metadata nor pool counters leak from another event.
    mixed["sectors"][1]["sectorId"] = new_id()
    standing_type = copy.deepcopy(second); standing_type.update({"ticketTypeId": new_id(), "sectorId": mixed["sectors"][1]["sectorId"]})
    mixed["ticketTypes"].append(standing_type); seed(mixed)
    held_seat = create_order(mixed, seats=[mixed["seats"][0]["seatId"]])
    paid_seat = create_order(mixed, seats=[mixed["seats"][1]["seatId"]]); accept(paid_seat)
    create_order(mixed, 2, standing_type["ticketTypeId"])
    body = availability(mixed)
    assert counts(body) == {seated_type: "1", standing_type["ticketTypeId"]: "6"}
    assert [s["state"] for s in body["seats"]] == ["HELD", "PURCHASED", "AVAILABLE", "UNAVAILABLE"]
    assert [s["available"] for s in body["sectors"]] == ["1", "6"]
    passed("Mixed SEATED/STANDING with all four seat states", "HELD/PURCHASED excluded; hidden UNAVAILABLE; seated1 and standing6")
    status, _, _ = curl(BASE + "/api/v1/orders/" + held_seat["orderId"] + "/cancel", "POST", body={}, token=TOKENS["buyer"])
    assert status == 200
    body = availability(mixed)
    assert counts(body)[seated_type] == "2" and body["seats"][0]["state"] == "AVAILABLE" and body["seats"][1]["state"] == "PURCHASED"
    passed("Availability after buyer cancels held seat", "Real HTTP cancel200; seated available1->2; purchased seat unchanged")

    cancelled = seed(configuration())
    sales = copy.deepcopy(cancelled); sales.update({"eventStatus": "CANCELLED", "sourceConfigVersion": "2"})
    publish("event.cancelled.v1", cancelled["eventId"], {"eventId": cancelled["eventId"], "cancellationId": new_id(),
        "sourceConfigVersion": "2", "cancelledAt": instant(), "actorSubject": "curl-admin", "sales": sales,
        "access": {"eventId": cancelled["eventId"], "organizerSubject": cancelled["organizerSubject"], "eventStatus": "CANCELLED",
                   "eventStartsAt": cancelled["eventStartsAt"], "eventEndsAt": cancelled["eventEndsAt"], "sourceConfigVersion": "2"}}, "2")
    availability(cancelled, 409, "EVENT_NOT_ON_SALE")
    assert sql(f"select configuration_snapshot->>'eventStatus' from booking_schema.tbl_event_sales_snapshots where event_id='{cancelled['eventId']}'") == "PUBLISHED"
    passed("CANCELLED entity gate with old PUBLISHED JSON", "Real cancellation consumer; HTTP409 EVENT_NOT_ON_SALE")
    for state in ["DRAFT", "PENDING_APPROVAL", "APPROVED"]:
        config = configuration(); config["eventStatus"] = state; seed(config)
        availability(config, 409, "EVENT_NOT_ON_SALE")
        passed(state + " gate", "HTTP409 EVENT_NOT_ON_SALE")

    for title, opening, closing in [("Before sale opens", 600, 3600), ("After sale closes", -120, -60)]:
        config = configuration(); config.update({"saleStartsAt": instant(opening), "saleEndsAt": instant(closing)}); seed(config)
        assert list(counts(availability(config)).values()) == ["10"]
        rejected = create_order(config, expected=409); assert rejected["code"] == "EVENT_NOT_ON_SALE"
        passed(title, "Public GET200; actual createOrder409; sale window still enforced for writes")

    retired = configuration()
    extra = copy.deepcopy(retired["ticketTypes"][0]); extra["ticketTypeId"] = new_id()
    retired["ticketTypes"].insert(0, extra); seed(retired)
    removed = retired["ticketTypes"].pop(1)["ticketTypeId"]
    fresh = copy.deepcopy(extra); fresh["ticketTypeId"] = new_id(); retired["ticketTypes"].insert(0, fresh)
    retired["sourceConfigVersion"] = "2"; seed(retired)
    body = availability(retired)
    assert [t["ticketTypeId"] for t in body["ticketTypes"]] == [fresh["ticketTypeId"], extra["ticketTypeId"]]
    assert body["sourceConfigVersion"] == "2" and all(t["available"] == "10" for t in body["ticketTypes"])
    assert sql(f"select count(*) from booking_schema.tbl_ticket_type_inventory where ticket_type_id='{removed}'") == "1"
    passed("Current config order, retired IDs and other-event isolation", "version2; only two current IDs in config order; old DB row retained")

    config = configuration()
    promotion = new_id()
    config["promotions"] = [{"promotionId": promotion, "code": "CURL10", "startsAt": instant(-60), "endsAt": instant(1800),
                             "maximumUses": "10", "maximumUsesPerUser": 1, "discount": {"kind": "PERCENT", "percent": 10}}]
    seed(config); order = create_order(config, 2)
    status, _, _ = curl(BASE + "/api/v1/orders/" + order["orderId"] + "/promotion", "POST", body={"code": "CURL10"}, token=TOKENS["buyer"])
    assert status == 200
    wait_for(lambda: sql("select count(*) from booking_schema.tbl_outbox_messages where published_at is null") == "0", 30)
    before = fingerprints()
    for _ in range(3):
        assert list(counts(availability(config)).values()) == ["8"]
    assert fingerprints() == before
    passed("Repeated GET does not change any Booking table", "All table row fingerprints unchanged, including versions/inbox/outbox/usage")
    print("Waiting for actual30s reservation deadline with expiry worker disabled", flush=True)
    wait_for(lambda: sql(f"select clock_timestamp()>=expires_at from booking_schema.tbl_orders where id='{order['orderId']}'") == "t", 45)
    before = fingerprints(); body = availability(config)
    assert list(counts(body).values()) == ["8"] and fingerprints() == before
    assert sql(f"select state from booking_schema.tbl_reservations where order_id='{order['orderId']}'") == "HELD"
    assert sql(f"select held from booking_schema.tbl_promotion_inventory where promotion_id='{promotion}'") == "1"
    passed("Expired HELD order is not released by GET", "Actual deadline passed; count8 and HELD voucher persist; every table unchanged")
    # Restart only this task's app using a persisted env-file; production expiry worker performs release.
    (OUT / "app-before-expiry.log").write_text(command(["docker", "logs", NAMES["app"]]), encoding="utf-8")
    command(["docker", "rm", "-f", NAMES["app"]]); CREATED.remove("app")
    ENV["BOOKING_EXPIRY_ENABLED"] = "true"
    values = dict(line.split("=", 1) for line in BOOKING_ENV.read_text().splitlines() if line)
    values["BOOKING_EXPIRY_ENABLED"] = "true"; env_file("booking", values)
    start_app()
    wait_for(lambda: sql(f"select lifecycle_state from booking_schema.tbl_orders where id='{order['orderId']}'") == "EXPIRED", 30)
    assert list(counts(availability(config)).values()) == ["10"]
    assert sql(f"select state from booking_schema.tbl_reservations where order_id='{order['orderId']}'") == "RELEASED"
    assert sql(f"select held from booking_schema.tbl_promotion_inventory where promotion_id='{promotion}'") == "0"
    passed("Availability after production expiry worker releases", "Restart with expiry enabled; EXPIRED/RELEASED; available10, voucher held0")


if __name__ == "__main__":
    failure = None
    try:
        main()
        save()
    except Exception as exc:
        failure = type(exc).__name__ + ": " + str(exc)
        if OUT.exists():
            save(failure)
        print("FAILED: " + failure, flush=True)
    finally:
        for key in reversed(CREATED):
            result = subprocess.run(["docker", "logs", NAMES[key]], capture_output=True, text=True, encoding="utf-8", errors="replace")
            (OUT / (key + ".log")).write_text(result.stdout + result.stderr, encoding="utf-8")
            subprocess.run(["docker", "rm", "-fv", NAMES[key]], capture_output=True)
        if NETWORK_CREATED:
            subprocess.run(["docker", "network", "rm", NETWORK], capture_output=True)
        for path in SECRET_FILES:
            path.unlink(missing_ok=True)
        print("Disposed owned containers/network and secret fixture files", flush=True)
    raise SystemExit(1 if failure else 0)
