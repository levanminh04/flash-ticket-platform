"""Write bounded private leases. No application admin endpoint is exposed."""
import argparse
import datetime as dt
import json
import os
import uuid
from pathlib import Path

SERVICES = {"booking-service", "event-service"}  # Ticket không còn hook (11/2026)
ACTIONS = {"http.error", "http.delay", "pool.hold", "off"}

def write_control(root, service, action, seconds=30, target=None, subject="", value=0, fixture=None):
    if service not in SERVICES or action not in ACTIONS or not 1 <= seconds <= 300:
        raise ValueError("Invalid service/action or lease outside 1..300 seconds")
    if action == "pool.hold" and (service != "booking-service" or not 1 <= value <= 5):
        raise ValueError("Only Booking supports holding 1..5 connections")
    if action == "http.delay" and not 1 <= value <= 30000:
        raise ValueError("Delay must be 1..30000 milliseconds")
    if action not in {"off", "pool.hold"}:
        target = str(uuid.UUID(str(target)))
        if not fixture:
            raise ValueError("A prepared fixture manifest is required")
        records = fixture.get("orders", []) if service != "event-service" else fixture.get("events", [])
        key = "orderId" if service != "event-service" else "eventId"
        if not any(record.get(key) == target for record in records):
            raise ValueError("Target does not belong to prepared fixtures")
        if service != "event-service" and (not subject or fixture.get("buyerSubject") != subject):
            raise ValueError("The fixture owner must match the buyer")
    control = {"leaseId": str(uuid.uuid4()),
               "expiresAt": (dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=seconds)).isoformat(),
               "action": action, "value": value, "subject": subject}
    if target:
        control["targetId"] = target
    directory = Path(root) / "private" / "controls" / service
    directory.mkdir(parents=True, exist_ok=True)
    path = directory / "control.json"
    temporary = directory / ("control-" + uuid.uuid4().hex + ".tmp")
    descriptor = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o640)
    if os.name == "posix" and os.geteuid() == 0:
        os.fchown(descriptor, 10001, 10001)
    with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
        json.dump(control, stream)
        stream.write("\n")
    temporary.replace(path)
    with (Path(root) / "private" / "control-journal.jsonl").open("a", encoding="utf-8") as stream:
        stream.write(json.dumps({"service": service, **control}) + "\n")
    return path, control

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2] / ".local" / "rca-test")
    parser.add_argument("--service", required=True, choices=sorted(SERVICES))
    parser.add_argument("--action", required=True, choices=sorted(ACTIONS))
    parser.add_argument("--seconds", type=int, default=30)
    parser.add_argument("--target")
    parser.add_argument("--subject", default="")
    parser.add_argument("--value", type=int, default=0)
    parser.add_argument("--fixture", type=Path)
    args = parser.parse_args()
    if os.environ.get("FT_TEST_ENVIRONMENT") != "true":
        parser.error("Set FT_TEST_ENVIRONMENT=true for the isolated environment")
    fixture = json.loads(args.fixture.read_text(encoding="utf-8")) if args.fixture else None
    path, control = write_control(args.root, args.service, args.action, args.seconds,
                                  args.target, args.subject, args.value, fixture)
    print(json.dumps({"path": str(path), "leaseId": control["leaseId"], "expiresAt": control["expiresAt"]}))

if __name__ == "__main__":
    main()
