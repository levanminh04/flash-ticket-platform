"""VNPay test double. No real provider, real keys or external callbacks are supported."""
import argparse
import datetime as dt
import hashlib
import hmac
import json
import os
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import parse_qs, quote_plus, urlencode, urlsplit
from urllib.request import urlopen

REFUND_REQUEST = ("vnp_RequestId vnp_Version vnp_Command vnp_TmnCode vnp_TransactionType "
                  "vnp_TxnRef vnp_Amount vnp_TransactionNo vnp_TransactionDate vnp_CreateBy "
                  "vnp_CreateDate vnp_IpAddr vnp_OrderInfo").split()
QUERY_REQUEST = ("vnp_RequestId vnp_Version vnp_Command vnp_TmnCode vnp_TxnRef "
                 "vnp_TransactionDate vnp_CreateDate vnp_IpAddr vnp_OrderInfo").split()
RESPONSE = ("vnp_ResponseId vnp_Command vnp_ResponseCode vnp_Message vnp_TmnCode "
            "vnp_TxnRef vnp_Amount vnp_BankCode vnp_PayDate vnp_TransactionNo "
            "vnp_TransactionType vnp_TransactionStatus vnp_OrderInfo").split()

def signature(secret, body, fields):
    text = "|".join(str(body.get(key, "")) for key in fields)
    return hmac.new(secret.encode(), text.encode(), hashlib.sha512).hexdigest()

def java_urlencode(value):
    return quote_plus(str(value).encode("ascii", "replace"), safe="*-._").replace("~", "%7E")

def callback_signature(secret, body):
    keys = sorted(k for k in body if k not in {"vnp_SecureHash", "vnp_SecureHashType"})
    # Matches VNPayUtil's delimiter position, including optional empty fields.
    text = "".join(k + "=" + java_urlencode(body[k]) + ("&" if i < len(keys)-1 else "")
                   for i, k in enumerate(keys) if body[k] is not None and body[k] != "")
    return hmac.new(secret.encode(), text.encode(), hashlib.sha512).hexdigest()

class State:
    def __init__(self, secret, merchant, state_file=None, audit_file=None, refund_poll=False):
        if len(secret) < 16 or not merchant:
            raise ValueError("A test merchant and a test key of at least 16 characters are required")
        self.secret, self.merchant = secret, merchant
        self.state_file, self.audit_file = state_file, audit_file
        self.refund_poll = refund_poll
        self.lock = threading.RLock()
        self.data = {"charges": {}, "requests": {}, "refunds": {}}
        if state_file and state_file.exists():
            self.data = json.loads(state_file.read_text(encoding="utf-8"))

    def persist(self):
        if self.state_file:
            self.state_file.parent.mkdir(parents=True, exist_ok=True)
            temporary = self.state_file.with_suffix(".tmp")
            temporary.write_text(json.dumps(self.data), encoding="utf-8")
            temporary.replace(self.state_file)

    def charge(self, params):
        if params.get("vnp_TmnCode") != self.merchant or not hmac.compare_digest(
                params.get("vnp_SecureHash", "").lower(), callback_signature(self.secret, params)):
            raise ValueError("INVALID_SIGNATURE")
        attempt = str(uuid.UUID(params["vnp_TxnRef"]))
        amount = int(params["vnp_Amount"])
        if amount <= 0:
            raise ValueError("INVALID_AMOUNT")
        with self.lock:
            existing = self.data["charges"].get(attempt)
            if existing and existing["amount"] != amount:
                raise ValueError("ATTEMPT_CONFLICT")
            if not existing:
                existing = {"amount": amount, "transaction": str(uuid.uuid4().int),
                            "date": dt.datetime.now(dt.timezone(dt.timedelta(hours=7))).strftime("%Y%m%d%H%M%S")}
                self.data["charges"][attempt] = existing
                self.persist()
            return existing

    def transaction(self, body):
        command = body.get("vnp_Command")
        fields = REFUND_REQUEST if command == "refund" else QUERY_REQUEST if command == "querydr" else None
        if fields is None or body.get("vnp_TmnCode") != self.merchant or not hmac.compare_digest(
                body.get("vnp_SecureHash", "").lower(), signature(self.secret, body, fields)):
            raise ValueError("INVALID_SIGNATURE_OR_COMMAND")
        with self.lock:
            charge = self.data["charges"].get(body["vnp_TxnRef"])
            if not charge or charge["date"] != body["vnp_TransactionDate"]:
                raise ValueError("UNKNOWN_CHARGE")
            canonical = json.dumps({key: body.get(key, "") for key in fields}, sort_keys=True)
            prior = self.data["requests"].get(body["vnp_RequestId"])
            if prior:
                if prior["request"] != canonical:
                    raise ValueError("REQUEST_ID_CONFLICT")
                return prior["response"]
            if command == "refund":
                if (body.get("vnp_TransactionType") != "02"
                        or int(body["vnp_Amount"]) != charge["amount"]
                        or body.get("vnp_TransactionNo") != charge["transaction"]):
                    raise ValueError("CHARGE_MISMATCH")
                self.data["refunds"][body["vnp_TxnRef"]] = True
            refunded = self.data["refunds"].get(body["vnp_TxnRef"], False)
            response = {
                "vnp_ResponseId": uuid.uuid4().hex, "vnp_Command": command, "vnp_ResponseCode": "00",
                "vnp_Message": "Test response", "vnp_TmnCode": self.merchant,
                "vnp_TxnRef": body["vnp_TxnRef"], "vnp_Amount": str(charge["amount"]),
                "vnp_BankCode": "TEST", "vnp_PayDate": charge["date"],
                "vnp_TransactionNo": charge["transaction"],
                "vnp_TransactionType": "02" if refunded else "01",
                "vnp_TransactionStatus": "05" if command == "refund" and self.refund_poll else "00",
                "vnp_OrderInfo": body.get("vnp_OrderInfo", "")
            }
            fields = RESPONSE + (["vnp_PromotionCode", "vnp_PromotionAmount"] if command == "querydr" else [])
            response["vnp_SecureHash"] = signature(self.secret, response, fields)
            self.data["requests"][body["vnp_RequestId"]] = {"request": canonical, "response": response}
            self.persist()
            return response

    def audit(self, command, attempt, started, status):
        if self.audit_file:
            self.audit_file.parent.mkdir(parents=True, exist_ok=True)
            record = {"command": command, "attemptId": attempt, "status": status,
                      "receivedMonotonicNs": started, "repliedMonotonicNs": time.monotonic_ns(),
                      "timestamp": dt.datetime.now(dt.timezone.utc).isoformat()}
            with self.lock, self.audit_file.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(record) + "\n")

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Standard HTTP logging includes signed query strings.

    def reply(self, code, body):
        content = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def read_json(self):
        maximum = 65536
        self.connection.settimeout(15)
        transfer = self.headers.get("Transfer-Encoding", "").lower()
        if transfer == "chunked" and not self.headers.get("Content-Length"):
            data = bytearray()
            while True:
                line = self.rfile.readline(128)
                if not line.endswith(b"\r\n"):
                    raise ValueError("INVALID_CHUNK")
                size = int(line.strip(), 16)
                if size < 0 or size > maximum - len(data):
                    raise ValueError("INVALID_LENGTH")
                if size == 0:
                    if self.rfile.read(2) != b"\r\n":
                        raise ValueError("UNEXPECTED_TRAILERS")
                    break
                part = self.rfile.read(size)
                if len(part) != size or self.rfile.read(2) != b"\r\n":
                    raise ValueError("INVALID_CHUNK")
                data.extend(part)
        elif not transfer:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= maximum:
                raise ValueError("INVALID_LENGTH")
            data = self.rfile.read(length)
        else:
            raise ValueError("INVALID_TRANSFER_ENCODING")
        result = json.loads(data)
        if not isinstance(result, dict):
            raise ValueError("INVALID_OBJECT")
        return result

    def do_POST(self):
        if urlsplit(self.path).path != "/transaction":
            return self.reply(404, {"code": "NOT_FOUND"})
        started, body, code = time.monotonic_ns(), {}, 400
        try:
            body = self.read_json()
            result = self.server.state.transaction(body)
            code = 200
            self.reply(code, result)
        except (ValueError, KeyError, TypeError):
            self.reply(code, {"code": "INVALID_TEST_TRANSACTION"})
        finally:
            self.server.state.audit(body.get("vnp_Command", "invalid"),
                                    body.get("vnp_TxnRef", ""), started, code)

    def do_GET(self):
        path = urlsplit(self.path)
        if path.path == "/health":
            return self.reply(200, {"status": "UP"})
        if path.path != "/checkout":
            return self.reply(404, {"code": "NOT_FOUND"})
        try:
            values = parse_qs(path.query, keep_blank_values=True)
            if any(len(value) != 1 for value in values.values()):
                raise ValueError("DUPLICATE_PARAMETER")
            params = {key: value[0] for key, value in values.items()}
            charge = self.server.state.charge(params)
            ipn = {"vnp_TmnCode": self.server.state.merchant, "vnp_TxnRef": params["vnp_TxnRef"],
                   "vnp_Amount": str(charge["amount"]), "vnp_TransactionNo": charge["transaction"],
                   "vnp_ResponseCode": "00", "vnp_TransactionStatus": "00",
                   "vnp_PayDate": charge["date"]}
            ipn["vnp_SecureHash"] = callback_signature(self.server.state.secret, ipn)
            with urlopen(self.server.ipn_url + "?" + urlencode(ipn), timeout=15) as response:
                acknowledgment = json.load(response)
            if acknowledgment.get("RspCode") not in {"00", "02"}:
                raise ValueError("IPN_NOT_ACCEPTED")
            self.reply(200, {"attemptId": params["vnp_TxnRef"], "ipn": acknowledgment})
        except (ValueError, KeyError, TypeError, OSError):
            self.reply(400, {"code": "TEST_CHECKOUT_FAILED"})

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=9090)
    args = parser.parse_args()
    if os.environ.get("FT_TEST_ENVIRONMENT") != "true":
        raise SystemExit("Mock requires FT_TEST_ENVIRONMENT=true")
    ipn = os.environ["VNPAY_IPN_URL"]
    target = urlsplit(ipn)
    if (target.scheme != "http" or target.hostname not in {"payment-service", "api-gateway", "127.0.0.1", "localhost"}
            or target.path != "/api/v1/payments/vnpay/ipn"):
        raise SystemExit("Only the isolated test application's IPN endpoint is allowed")
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    server.state = State(os.environ["VNPAY_HASH_SECRET"], os.environ["VNPAY_TMN_CODE"],
                         Path("/state/transactions.json"), Path("/audit/vnpay.jsonl"),
                         refund_poll=os.environ.get("FT_MOCK_REFUND_POLL") == "true")
    server.ipn_url = ipn
    server.serve_forever()

if __name__ == "__main__":
    main()
