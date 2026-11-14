"""Local, isolated verification tools. All probes/control/audit stay outside RCA input."""
import argparse
import base64
import datetime as dt
import hashlib
import json
import os
import secrets
import subprocess
import sys
import time
import uuid
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

REPO = Path(__file__).resolve().parents[2]
APPS = ("api-gateway", "discovery-server", "booking-service", "event-service",
        "payment-service", "ticket-service", "user-service")
COMPOSE = REPO / "deploy" / "rca-test" / "compose.yml"

def run(arguments, check=True):
    result = subprocess.run(arguments, cwd=REPO, capture_output=True, text=True, encoding="utf-8")
    if check and result.returncode:
        raise RuntimeError(f"Command failed ({result.returncode}): {arguments[0]} {arguments[1]}")
    return result

def environment(path):
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            result[key] = value
    if result.get("FT_TEST_ENVIRONMENT") != "true":
        raise ValueError("Only an env file declaring FT_TEST_ENVIRONMENT=true is accepted")
    return result

def save(path, value, mode=0o640):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    os.chmod(temporary, mode)
    if os.name == "posix" and os.geteuid() == 0:
        os.chown(temporary, 10001, 10001)
    temporary.replace(path)

def setup(root, values):
    directories = ["private/keycloak", "private/qr", "private/mock-state", "private/probes",
                   "private/tokens", "public/telemetry", "public/logs"]
    directories += [f"private/{kind}/{svc}" for kind in ("controls", "audit")
                    for svc in ("booking-service", "event-service", "ticket-service")]
    for name in directories:
        path = root / name
        path.mkdir(parents=True, exist_ok=True)
        if os.name == "posix":
            os.chmod(path, 0o770)
            if os.geteuid() == 0:
                os.chown(path, 10001, 10001)
    key_file = root / "private" / "qr" / "key-ring.json"
    if not key_file.exists():
        save(key_file, {"keys": {"bi-test-key": base64.b64encode(secrets.token_bytes(32)).decode()}}, 0o440)
    realm_file = root / "private" / "keycloak" / "realm.json"
    if not realm_file.exists():
        mapper = {"name": "test-audience", "protocol": "openid-connect",
                  "protocolMapper": "oidc-audience-mapper",
                  "config": {"included.custom.audience": "flash-ticket-api",
                             "access.token.claim": "true", "id.token.claim": "false"}}
        roles = ["BUYER", "ORGANIZER", "ADMIN", "INTERNAL_SERVICE"]
        clients = [{"clientId": "ft-bi-test", "publicClient": True, "directAccessGrantsEnabled": True,
                    "standardFlowEnabled": False, "protocol": "openid-connect", "protocolMappers": [mapper]},
                   {"clientId": "internal-service", "secret": values["INTERNAL_SERVICE_CLIENT_SECRET"],
                    "publicClient": False, "serviceAccountsEnabled": True, "standardFlowEnabled": False,
                    "protocol": "openid-connect", "protocolMappers": [mapper]}]
        users = [{"username": name, "enabled": True, "emailVerified": True,
                  "email": name + "@example.test", "firstName": "Test", "lastName": name,
                  "realmRoles": assigned,
                  "credentials": [{"type": "password", "value": values["FT_TEST_USER_PASSWORD"], "temporary": False}]}
                 for name, assigned in [("buyer", ["BUYER"]), ("organizer", ["BUYER", "ORGANIZER"]), ("admin", ["ADMIN"])]]
        users.append({"username": "service-account-internal-service", "enabled": True,
                      "serviceAccountClientId": "internal-service", "realmRoles": ["INTERNAL_SERVICE"]})
        save(realm_file, {"id": "flash-ticket-bi", "realm": "flash-ticket-bi", "enabled": True,
                          "roles": {"realm": [{"name": name} for name in roles]}, "clients": clients, "users": users,
                          "eventsEnabled": True, "eventsListeners": ["jboss-logging"]}, 0o640)
    pg_env = {key: value for key, value in values.items()
              if key.startswith("POSTGRES_") or "_DB_ADMIN_PASSWORD" in key or "_DB_VIEWER_PASSWORD" in key}
    pg_path = root / "private" / "pg-init.env"
    pg_path.write_text("".join(f"{key}={value}\n" for key, value in pg_env.items()), encoding="utf-8")
    os.chmod(pg_path, 0o600)
    print("Prepared test realm, QR key and private control/audit/output directories; no container started")

def tokens(root, values):
    for role in ("buyer", "organizer", "admin"):
        data = urlencode({"grant_type": "password", "client_id": "ft-bi-test", "username": role,
                          "password": values["FT_TEST_USER_PASSWORD"]}).encode()
        request = Request("http://127.0.0.1:18080/realms/flash-ticket-bi/protocol/openid-connect/token", data=data)
        with urlopen(request, timeout=20) as response:
            token = json.load(response)["access_token"]
        path = root / "private" / "tokens" / (role + ".jwt")
        path.write_text(token, encoding="utf-8")
        os.chmod(path, 0o640)
        if os.name == "posix" and os.geteuid() == 0:
            os.chown(path, 10001, 10001)
    print("Saved three test access tokens privately; token values are not printed")

def dc(env_file, *arguments):
    return ["docker", "compose", "--env-file", str(env_file), "-p", "ft-bi", "-f", str(COMPOSE), *arguments]

def checked_container(env_file, service):
    cid = run(dc(env_file, "ps", "-q", service)).stdout.strip()
    if not cid:
        raise ValueError(f"{service} is not running")
    inspect = json.loads(run(["docker", "inspect", cid]).stdout)[0]
    if inspect["Config"].get("Labels", {}).get("ft.bi.test") != "true":
        raise ValueError("Container does not belong to the test environment")
    return cid, inspect

def inventory(root, env_file):
    ids = run(["docker", "ps", "-aq", "--filter", "label=ft.bi.test=true"]).stdout.split()
    records = []
    for cid in ids:
        item = json.loads(run(["docker", "inspect", cid]).stdout)[0]
        records.append({"containerId": item["Id"], "name": item["Name"], "imageId": item["Image"],
                        "service": item["Config"].get("Labels", {}).get("com.docker.compose.service"),
                        "state": {key: item["State"][key] for key in ("Status", "StartedAt", "OOMKilled", "ExitCode")},
                        "restartCount": item["RestartCount"], "memoryLimit": item["HostConfig"]["Memory"],
                        "cpuQuotaNano": item["HostConfig"]["NanoCpus"],
                        "networkMode": item["HostConfig"]["NetworkMode"],
                        "networks": {name: value["IPAddress"] for name, value in item["NetworkSettings"]["Networks"].items()}})
    changed = run(["git", "diff", "HEAD", "--name-only"]).stdout.splitlines()
    changed += run(["git", "ls-files", "--others", "--exclude-standard"]).stdout.splitlines()
    hashes = {name: hashlib.sha256((REPO / name).read_bytes()).hexdigest()
              for name in sorted(set(changed)) if (REPO / name).is_file()}
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    manifest = {"timestamp": stamp, "commit": run(["git", "rev-parse", "HEAD"]).stdout.strip(),
                "files": hashes, "containers": records}
    path = root / "private" / "probes" / ("inventory-" + stamp + ".json")
    save(path, manifest)
    print(f"Saved {len(records)} container records and source hashes to {path}")
    return records

def probes(root, env_file):
    checked_container(env_file, "postgres")
    checked_container(env_file, "redis")
    with urlopen("http://127.0.0.1:18474/proxies", timeout=5) as response:
        configured = json.load(response)
    expected = json.loads((COMPOSE.parent / "toxiproxy.json").read_text(encoding="utf-8"))
    for proxy in expected:
        observed = configured[proxy["name"]]
        port = proxy["listen"].rsplit(":", 1)[1]
        listeners = {proxy["listen"], "[::]:" + port}
        if (observed["listen"] not in listeners or observed["upstream"] != proxy["upstream"]
                or observed["enabled"] != proxy["enabled"]):
            raise AssertionError("Proxy map differs from the declared map")
    results = []
    for database, proxy_port in (("booking_db", 15432), ("event_db", 15433)):
        for host, port in (("toxiproxy", proxy_port), ("postgres", 5432)):
            start = time.monotonic_ns()
            command = ["sh", "-c",
                       'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -X -w -U postgres -h "$1" -p "$2" -d "$3" -Atc "SELECT current_database()"',
                       "sh", host, str(port), database]
            result = run(dc(env_file, "exec", "-T", "postgres", *command), check=False)
            results.append({"database": database, "host": host, "port": port, "code": result.returncode,
                            "durationMs": (time.monotonic_ns() - start) / 1e6, "answer": result.stdout.strip()})
    for host, port in (("toxiproxy", 16379), ("toxiproxy", 16380), ("redis", 6379)):
        start = time.monotonic_ns()
        result = run(dc(env_file, "exec", "-T", "redis", "sh", "-c",
                        'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli -h "$1" -p "$2" --raw PING',
                        "sh", host, str(port)), check=False)
        results.append({"cache": "redis", "host": host, "port": port, "code": result.returncode,
                        "durationMs": (time.monotonic_ns() - start) / 1e6, "answer": result.stdout.strip()})
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    save(root / "private" / "probes" / ("paths-" + stamp + ".json"), {"results": results, "proxies": expected})
    with urlopen("http://127.0.0.1:18474/metrics", timeout=5) as response:
        (root / "private" / "probes" / ("proxy-bytes-" + stamp + ".txt")).write_bytes(response.read())
    if any(result["code"] != 0 for result in results):
        raise AssertionError("A direct/proxy probe failed; retained probe results distinguish the paths")
    print("Direct/proxy probes passed; caller JVM/byte/trace evidence still requires real application requests")

def collect(root, env_file):
    sys.path.insert(0, str(REPO / "deploy" / "observability"))
    from verify_logs import validate
    schema = json.loads((REPO / "deploy" / "observability" / "log.schema.json").read_text(encoding="utf-8"))
    records = inventory(root, env_file)
    summaries = []
    for item in records:
        service = item["service"]
        if service not in APPS and service != "keycloak":
            continue
        cid = item["containerId"]
        raw = run(["docker", "logs", cid], check=False)
        text = raw.stdout + raw.stderr
        private = root / "private" / "probes" / (service + "-" + cid[:12] + ".raw.log")
        private.write_text(text, encoding="utf-8")
        lines = []
        for line in text.splitlines():
            if not line.startswith("{"):
                continue
            json.loads(line)  # malformed application JSON is an error, never silently discarded
            lines.append(line)
        path = root / "public" / "logs" / (service + "-" + cid[:12] + ".jsonl")
        path.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
        if service in APPS:
            count = validate(path, schema)
        else:
            count = len(lines)
            if not count:
                raise AssertionError("Keycloak did not emit JSON")
        summaries.append({"service": service, "records": count, "nativeLines": len(text.splitlines()) - len(lines),
                          "containerId": cid})
    save(root / "private" / "probes" / "log-coverage.json", summaries)
    print(json.dumps(summaries))

def fresh_postgres(root, env_file):
    # Keep this container and volume after stopping it; refuse to overwrite a previous run.
    name, volume = "ft-bi-init-postgres", "ft-bi-init-postgres-data"
    if run(["docker", "container", "inspect", name], check=False).returncode == 0:
        raise ValueError("Previous fresh-init container is retained; inspect its evidence before requesting another run")
    if run(["docker", "volume", "inspect", volume], check=False).returncode == 0:
        raise ValueError("Fresh-init volume already exists; do not reuse it as evidence of fresh initialization")
    config = json.loads(run(dc(env_file, "config", "--format", "json")).stdout)["services"]["postgres"]
    command = ["docker", "run", "-d", "--name", name, "--label", "ft.bi.test=true",
               "--network", "none", "--memory", "1536m", "--env-file", str(root / "private" / "pg-init.env"),
               "--mount", f"type=volume,source={volume},target=/var/lib/postgresql/data"]
    for mount in config["volumes"]:
        if mount["type"] == "bind":
            command += ["--mount", f"type=bind,source={mount['source']},target={mount['target']},readonly"]
    run(command + [config["image"], *config["command"]])
    def psql(sql, database="keycloak_db", check=True):
        return run(["docker", "exec", name, "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres",
                    "-d", database, "-Atc", sql], check=check)
    output = {"container": name, "volume": volume, "databases": {}}
    try:
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            log = run(["docker", "logs", name], check=False)
            if "PostgreSQL init process complete" in log.stdout + log.stderr:
                if psql("SELECT 1", check=False).returncode == 0:
                    break
            time.sleep(2)
        else:
            raise TimeoutError("Fresh initialization did not finish")
        for service in ("event", "booking", "payment", "ticket"):
            schema = service + "_schema"
            table_count = int(psql(f"SELECT count(*) FROM information_schema.tables WHERE table_schema='{schema}'",
                                   service + "_db").stdout.strip())
            if not table_count:
                raise AssertionError("No business tables after initialization")
            permissions = psql(
                f"SELECT bool_and(has_table_privilege('{service}_erd',table_schema||'.'||table_name,'SELECT')),"
                f"bool_or(has_table_privilege('{service}_erd',table_schema||'.'||table_name,'INSERT,UPDATE,DELETE')) "
                f"FROM information_schema.tables WHERE table_schema='{schema}'", service + "_db").stdout.strip()
            if permissions != "t|f":
                raise AssertionError("Reader grants do not match read-only permissions")
            output["databases"][service] = {"tables": table_count, "readerSelect": True, "readerWrite": False}
        psql("SELECT pg_sleep(0.7)")
        holder = subprocess.Popen(["docker", "exec", name, "psql", "-X", "-U", "postgres", "-d", "keycloak_db",
                                   "-c", "SELECT pg_advisory_lock(71011); SELECT pg_sleep(5);"],
                                  cwd=REPO, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            if psql("SELECT count(*) FROM pg_locks WHERE locktype='advisory' AND objid=71011 AND granted").stdout.strip() == "1":
                break
            time.sleep(0.1)
        psql("SELECT pg_advisory_lock(71011)")
        holder.wait(timeout=10)
        logs = run(["docker", "logs", name], check=False)
        text = logs.stdout + logs.stderr
        output["slowQueryLogged"] = "duration:" in text and "pg_sleep(0.7)" in text
        output["lockWaitLogged"] = "still waiting" in text
        if not output["slowQueryLogged"] or not output["lockWaitLogged"]:
            raise AssertionError("Expected slow/lock-wait diagnostics were not logged")
        output["status"] = "PASS"
    finally:
        logs = run(["docker", "logs", name], check=False)
        (root / "private" / "probes" / "fresh-postgres.log").write_text(logs.stdout + logs.stderr, encoding="utf-8")
        save(root / "private" / "probes" / "fresh-postgres.json", output)
        run(["docker", "stop", name], check=False)
    print(json.dumps(output))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["setup", "tokens", "inventory", "probes", "collect", "fresh-postgres", "check-config"])
    parser.add_argument("--root", type=Path, default=REPO / ".local" / "rca-test")
    parser.add_argument("--env-file", type=Path, default=COMPOSE.parent / ".env.example")
    args = parser.parse_args()
    values = environment(args.env_file)
    if args.action == "setup":
        setup(args.root, values)
    elif args.action == "tokens":
        tokens(args.root, values)
    elif args.action == "inventory":
        inventory(args.root, args.env_file)
    elif args.action == "probes":
        probes(args.root, args.env_file)
    elif args.action == "collect":
        collect(args.root, args.env_file)
    elif args.action == "fresh-postgres":
        fresh_postgres(args.root, args.env_file)
    else:
        config = json.loads(run(dc(args.env_file, "config", "--format", "json")).stdout)
        for name, service in config["services"].items():
            if service.get("network_mode") == "host":
                raise AssertionError("Test composition must not use host network")
        print(f"PASS isolated config: {len(config['services'])} default services")

if __name__ == "__main__":
    main()
