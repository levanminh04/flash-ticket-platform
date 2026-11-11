"""Create real test transactions through existing authenticated APIs; retain each checkpoint."""
import argparse
import base64
import datetime as dt
import json
import os
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode, urlsplit
from urllib.request import Request, urlopen
from faultctl import write_control

def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    os.chmod(temporary, 0o640)
    if os.name == "posix" and os.geteuid() == 0:
        os.chown(temporary, 10001, 10001)
    temporary.replace(path)

def unwrap(value):
    return value["data"] if isinstance(value, dict) and "data" in value else value

class RequestFailed(RuntimeError):
    def __init__(self, method, path, status):
        super().__init__(f"{method} {path}: HTTP {status}")
        self.status = status


class Client:
    def __init__(self, base, tokens):
        target = urlsplit(base)
        if target.hostname not in {"127.0.0.1", "localhost", "api-gateway"} or target.scheme != "http":
            raise ValueError("This harness targets the isolated test gateway only")
        self.base, self.tokens = base.rstrip("/"), tokens

    def request(self, method, path, role="buyer", body=None, key=None):
        headers = {}
        if role:
            token = (self.tokens / (role + ".jwt")).read_text(encoding="utf-8").strip()
            headers["Authorization"] = "Bearer " + token
        if key:
            headers["Idempotency-Key"] = key
        data = None if body is None else json.dumps(body).encode()
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with urlopen(request, timeout=35) as response:
                return unwrap(json.load(response))
        except HTTPError as error:
            raise RequestFailed(method, path, error.code) from None

def poll(read, accept, seconds=90):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        last = read()
        if accept(last):
            return last
        time.sleep(1)
    raise TimeoutError("Expected transaction checkpoint was not reached")

def draft():
    now = dt.datetime.now(dt.timezone.utc)
    stamp = lambda value: value.isoformat().replace("+00:00", "Z")
    return {"title": "FlashTicket isolated fixture", "description": "Synthetic test event",
            "categoryId": None, "venueId": None, "mode": "QUANTITY",
            "startsAt": stamp(now + dt.timedelta(days=7)), "endsAt": stamp(now + dt.timedelta(days=8)),
            "saleStartsAt": stamp(now - dt.timedelta(days=1)), "saleEndsAt": stamp(now + dt.timedelta(days=6)),
            "purchaseLimit": 100, "images": [],
            "ticketTypes": [{"ticketTypeId": None, "name": "General", "unitPrice": "120000",
                             "currency": "VND", "configuredQuantity": "10000", "colorCode": "#336699"}],
            "promotions": []}

def catalog(root):
    # Reference rows only: no business transaction/state is manufactured in SQL.
    from verify import dc, checked_container, run, REPO
    env_file = Path(os.environ.get("FT_TEST_ENV_FILE", str(REPO / "deploy/rca-test/.env.example")))
    checked_container(env_file, "postgres")
    path = root / "private" / "draft.json"
    if path.exists():
        raise ValueError("Draft already exists; retain it or choose a separate draft before catalog seeding")
    category = "21905c68-c62b-4ae3-8aa3-41737e8ba171"
    venue = "3cdf986d-4a73-4bf0-b093-550e9f2a26b7"
    sql = ("BEGIN; SET LOCAL ROLE event_owner; "
           f"INSERT INTO event_schema.tbl_categories(id,name) VALUES('{category}','Isolated test') ON CONFLICT(id) DO NOTHING; "
           f"INSERT INTO event_schema.tbl_venues(id,name,address) VALUES('{venue}','Isolated test venue','Synthetic address') ON CONFLICT(id) DO NOTHING; COMMIT;")
    run(dc(env_file, "exec", "-T", "postgres", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "event_db", "-c", sql))
    value = draft()
    value.update(categoryId=category, venueId=venue)
    save(path, value)
    print("Seeded two test reference rows and saved private/draft.json; transaction state is unchanged")


def prepare(client, manifest, data, manifest_path):
    event = client.request("POST", "/api/v1/organizer/events", "organizer", data)
    event_id = event["eventId"]
    manifest.setdefault("events", []).append({"eventId": event_id, "status": "DRAFT"})
    save(manifest_path, manifest)
    for route, role, extra in [("submit", "organizer", {}), ("approve", "admin", {"feeRate": "0.05"}),
                                ("publish", "organizer", {})]:
        event = client.request("POST", f"/api/v1/{'admin' if role == 'admin' else 'organizer'}/events/{event_id}/{route}",
                               role, {"rowVersion": event["rowVersion"], **extra})
        manifest["events"][-1]["status"] = event["status"]
        save(manifest_path, manifest)
    public = client.request("GET", f"/api/v1/events/{event_id}", None)
    manifest["events"][-1]["ticketTypeId"] = public["ticketTypes"][0]["ticketTypeId"]
    wait_for_stock(client, manifest["events"][-1])
    return manifest["events"][-1]

def wait_for_stock(client, event):
    def availability():
        try:
            return client.request("GET", f"/api/v1/events/{event['eventId']}/availability", None)
        except RequestFailed as error:
            if error.status not in {404, 503, 504}:
                raise
            return {}
    poll(availability, lambda value: any(item["ticketTypeId"] == event["ticketTypeId"]
                                        and int(item["available"]) > 0
                                        for item in value.get("ticketTypes", [])), 60)


def purchase(client, manifest, event, root, manifest_path, terminal_failure=False):
    if terminal_failure:
        raise SystemExit("--terminal-failure: Ticket không còn hook trong code (11/2026). "
                         "Cách gây thất bại phát vé bằng dữ liệu thật đang mở, xem README.")
    wait_for_stock(client, event)
    selection = {"mode": "QUANTITY", "items": [{"ticketTypeId": event["ticketTypeId"], "quantity": 1}]}
    key = str(uuid.uuid4())
    request = {"eventId": event["eventId"], "customerEmail": "buyer@example.test", "selection": selection}
    order = client.request("POST", "/api/v1/orders", body=request, key=key)
    record = {"orderId": order["orderId"], "eventId": event["eventId"],
              "createKey": key, "state": order["state"]}
    manifest.setdefault("orders", []).append(record)
    save(manifest_path, manifest)
    replay = client.request("POST", "/api/v1/orders", body=request, key=key)
    if replay["orderId"] != record["orderId"]:
        raise AssertionError("Order replay changed its ID")
    if terminal_failure:
        write_control(root, "ticket-service", "ticket.fail", 120,
                      record["orderId"], manifest["buyerSubject"], fixture=manifest)
    try:
        payment_key = str(uuid.uuid4())
        attempt = client.request("POST", "/api/v1/payments", body={"orderId": record["orderId"]}, key=payment_key)
        record.update(attemptId=attempt["attemptId"], paymentKey=payment_key)
        save(manifest_path, manifest)
        target = urlsplit(attempt["paymentUrl"])
        if target.hostname not in {"127.0.0.1", "localhost"} or target.port != int(os.environ.get("FT_MOCK_HTTP_PORT", "19090")) or target.path != "/checkout":
            raise ValueError("Payment URL does not point to the isolated mock")
        with urlopen(attempt["paymentUrl"], timeout=35) as response:
            acknowledgment = json.load(response)
        if acknowledgment["ipn"]["RspCode"] not in {"00", "02"}:
            raise AssertionError("Payment did not accept the signed mock IPN")
        expected = "ISSUANCE_FAILED" if terminal_failure else "COMPLETED"
        observed = poll(lambda: client.request("GET", f"/api/v1/orders/{record['orderId']}"),
                        lambda value: value["state"] == expected)
        tickets = client.request("GET", f"/api/v1/orders/{record['orderId']}/tickets")
        if terminal_failure:
            if tickets["issuanceState"] != "FAILED" or tickets["tickets"]:
                raise AssertionError("Terminal failure must issue zero tickets")
            payment = poll(lambda: client.request("GET", f"/api/v1/orders/{record['orderId']}/payment"),
                           lambda value: any(r["state"] == "SUCCEEDED" for r in value.get("refunds", [])))
            record["refunds"] = [{"refundId": r["refundId"], "state": r["state"]} for r in payment["refunds"]]
        else:
            if tickets["issuanceState"] != "COMPLETED" or len(tickets["tickets"]) != 1:
                raise AssertionError("Paid fixture did not receive exactly one ticket")
            record["ticketIds"] = [ticket["ticketId"] for ticket in tickets["tickets"]]
        # Repeat the same charge, then read state/tickets again; never manufacture a second command.
        with urlopen(attempt["paymentUrl"], timeout=35) as response:
            json.load(response)
        repeated = client.request("GET", f"/api/v1/orders/{record['orderId']}/tickets")
        if repeated != tickets:
            raise AssertionError("Duplicate callback changed the issued ticket set")
        record.update(state=observed["state"], checkpoint="VERIFIED")
        save(manifest_path, manifest)
    finally:
        if terminal_failure:
            write_control(root, "ticket-service", "off")

def cancel(client, manifest, event):
    event_id = event["eventId"]
    owner = client.request("GET", f"/api/v1/organizer/events/{event_id}", "organizer")
    client.request("POST", f"/api/v1/admin/events/{event_id}/cancel", "admin",
                   {"rowVersion": owner["rowVersion"]})
    for order in manifest.get("orders", []):
        if order["eventId"] != event_id:
            continue
        payment = poll(lambda: client.request("GET", f"/api/v1/orders/{order['orderId']}/payment"),
                       lambda value: any(r["state"] == "SUCCEEDED" for r in value.get("refunds", [])))
        observed = poll(lambda: client.request("GET", f"/api/v1/orders/{order['orderId']}"),
                        lambda value: value["state"] == "CANCELLED")
        order["state"] = observed["state"]
        order["refunds"] = [{"refundId": r["refundId"], "state": r["state"]} for r in payment["refunds"]]
    event["status"] = "CANCELLED"

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["draft", "catalog", "prepare", "purchase", "cancel"])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2] / ".local" / "rca-test")
    parser.add_argument("--base", default="http://127.0.0.1:18081")
    parser.add_argument("--draft", type=Path)
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--terminal-failure", action="store_true")
    args = parser.parse_args()
    if os.environ.get("FT_TEST_ENVIRONMENT") != "true":
        parser.error("FT_TEST_ENVIRONMENT=true is required")
    if not 1 <= args.count <= 100:
        parser.error("--count must be 1..100")
    manifest_path = args.root / "private" / "fixtures.json"
    if args.action == "catalog":
        catalog(args.root)
        return
    if args.action == "draft":
        path = args.draft or args.root / "private" / "draft.json"
        save(path, draft())
        print(f"Wrote synthetic draft to {path}; select valid category/venue before publishing")
        return
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {"events": [], "orders": []}
    tokens = args.root / "private" / "tokens"
    buyer = (tokens / "buyer.jwt").read_text(encoding="utf-8").strip()
    encoded = buyer.split(".")[1]
    manifest["buyerSubject"] = json.loads(base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)))["sub"]
    client = Client(args.base, tokens)
    try:
        if args.action == "prepare":
            data = json.loads(args.draft.read_text(encoding="utf-8")) if args.draft else draft()
            prepare(client, manifest, data, manifest_path)
        elif args.action == "purchase":
            event = next(e for e in reversed(manifest["events"]) if e["status"] == "PUBLISHED")
            for _ in range(args.count):
                purchase(client, manifest, event, args.root, manifest_path, args.terminal_failure)
        elif args.action == "cancel":
            event = next(e for e in reversed(manifest["events"]) if e["status"] == "PUBLISHED")
            cancel(client, manifest, event)
    except Exception as error:
        manifest["lastFailure"] = {"type": type(error).__name__, "message": str(error)}
        raise
    finally:
        save(manifest_path, manifest)
    print(json.dumps({"events": len(manifest["events"]), "orders": len(manifest["orders"]),
                      "checkpoint": args.action}))

if __name__ == "__main__":
    main()
