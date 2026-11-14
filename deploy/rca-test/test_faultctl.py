import json
import tempfile
import unittest
import uuid
from pathlib import Path
from faultctl import write_control

class LeaseControlTests(unittest.TestCase):
    def test_owner_bound_and_explicit_revocation(self):
        with tempfile.TemporaryDirectory() as directory:
            order = str(uuid.uuid4())
            fixture = {"buyerSubject": "buyer", "orders": [{"orderId": order}]}
            path, first = write_control(directory, "booking-service", "http.error",
                                        target=order, subject="buyer", fixture=fixture)
            self.assertEqual(first, json.loads(path.read_text(encoding="utf-8")))
            with self.assertRaises(ValueError):
                write_control(directory, "booking-service", "http.error",
                              target=order, subject="other", fixture=fixture)
            with self.assertRaises(ValueError):
                write_control(directory, "booking-service", "pool.hold", seconds=301, value=5)
            _, off = write_control(directory, "booking-service", "off")
            self.assertNotEqual(first["leaseId"], off["leaseId"])
            self.assertEqual("off", json.loads(path.read_text(encoding="utf-8"))["action"])

    def test_no_arbitrary_target_or_wrong_service(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                write_control(directory, "event-service", "http.error",
                              target=str(uuid.uuid4()), fixture={"events": []})
            with self.assertRaises(ValueError):
                write_control(directory, "event-service", "pool.hold", value=1)
            with self.assertRaises(ValueError):
                write_control(directory, "booking-service", "ticket.fail", target=str(uuid.uuid4()))
            self.assertEqual([], list(Path(directory).iterdir()))

if __name__ == "__main__":
    unittest.main()
