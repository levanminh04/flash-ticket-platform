import copy
import hashlib
import hmac
import http.client
import json
import threading
import unittest
import uuid
from http.server import ThreadingHTTPServer
from mock_vnpay import Handler, State, REFUND_REQUEST, QUERY_REQUEST, RESPONSE, signature, callback_signature

class MockContractTests(unittest.TestCase):
    def setUp(self):
        self.state = State("test-key-never-a-live-key", "TEST")
        self.attempt = str(uuid.uuid4())
        checkout = {"vnp_TmnCode": "TEST", "vnp_TxnRef": self.attempt,
                    "vnp_Amount": "12000000", "vnp_CreateDate": "20261111120000"}
        checkout["vnp_SecureHash"] = callback_signature(self.state.secret, checkout)
        self.charge = self.state.charge(checkout)

    def request(self, command):
        body = {"vnp_RequestId": uuid.uuid4().hex, "vnp_Version": "2.1.0", "vnp_Command": command,
                "vnp_TmnCode": "TEST", "vnp_TxnRef": self.attempt,
                "vnp_TransactionDate": self.charge["date"], "vnp_CreateDate": "20261111120100",
                "vnp_IpAddr": "127.0.0.1", "vnp_OrderInfo": "Test refund"}
        if command == "refund":
            body.update(vnp_TransactionType="02", vnp_Amount="12000000",
                        vnp_TransactionNo=self.charge["transaction"], vnp_CreateBy="fixture")
        body["vnp_SecureHash"] = signature(self.state.secret, body,
                                          REFUND_REQUEST if command == "refund" else QUERY_REQUEST)
        return body

    def test_refund_query_signatures_and_replay(self):
        body = self.request("refund")
        result = self.state.transaction(body)
        self.assertEqual(result, self.state.transaction(copy.deepcopy(body)))
        expected = hmac.new(self.state.secret.encode(),
                            "|".join(result.get(key, "") for key in RESPONSE).encode(),
                            hashlib.sha512).hexdigest()
        self.assertEqual(expected, result["vnp_SecureHash"])
        query = self.state.transaction(self.request("querydr"))
        self.assertEqual("02", query["vnp_TransactionType"])
        fields = RESPONSE + ["vnp_PromotionCode", "vnp_PromotionAmount"]
        self.assertEqual(signature(self.state.secret, query, fields), query["vnp_SecureHash"])

    def test_http_chunked_refund_and_query(self):
        self.state.refund_poll = True
        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            server.state = self.state
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            connection = http.client.HTTPConnection(*server.server_address, timeout=3)
            try:
                for command, expected in [("refund", "05"), ("querydr", "00")]:
                    body = json.dumps(self.request(command)).encode()
                    chunks = (body[:17], body[17:])
                    connection.request("POST", "/transaction", body=iter(chunks),
                                       headers={"Content-Type": "application/json"}, encode_chunked=True)
                    response = connection.getresponse()
                    result = json.load(response)
                    self.assertEqual(200, response.status)
                    self.assertEqual("02", result["vnp_TransactionType"])
                    self.assertEqual(expected, result["vnp_TransactionStatus"])
                    fields = RESPONSE + (["vnp_PromotionCode", "vnp_PromotionAmount"]
                                         if command == "querydr" else [])
                    self.assertEqual(signature(self.state.secret, result, fields), result["vnp_SecureHash"])
            finally:
                connection.close()
                server.shutdown()
                worker.join(timeout=3)

    def test_wrong_signature_amount_and_unknown_charge_rejected(self):
        bad = self.request("refund")
        bad["vnp_SecureHash"] = "0" * 128
        with self.assertRaises(ValueError):
            self.state.transaction(bad)
        bad = self.request("refund")
        bad["vnp_Amount"] = "1"
        bad["vnp_SecureHash"] = signature(self.state.secret, bad, REFUND_REQUEST)
        with self.assertRaises(ValueError):
            self.state.transaction(bad)
        bad = self.request("querydr")
        bad["vnp_TxnRef"] = str(uuid.uuid4())
        bad["vnp_SecureHash"] = signature(self.state.secret, bad, QUERY_REQUEST)
        with self.assertRaises(ValueError):
            self.state.transaction(bad)

    def test_request_id_cannot_change_semantics(self):
        body = self.request("refund")
        self.state.transaction(body)
        body["vnp_OrderInfo"] = "Changed request"
        body["vnp_SecureHash"] = signature(self.state.secret, body, REFUND_REQUEST)
        with self.assertRaises(ValueError):
            self.state.transaction(body)

    def test_unpaid_query_is_not_a_successful_refund(self):
        response = self.state.transaction(self.request("querydr"))
        self.assertEqual("01", response["vnp_TransactionType"])

if __name__ == "__main__":
    unittest.main()
