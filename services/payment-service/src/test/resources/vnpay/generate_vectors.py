"""Independent Python stdlib fixtures; test key only, no provider/network calls."""
import hashlib
import hmac
import json
from pathlib import Path
from urllib.parse import urlencode

KEY = b"key"
ORDER = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"
REF = "11111111222243338444555555555555"
pay = {
    "vnp_Version": "2.1.0", "vnp_Command": "pay", "vnp_TmnCode": "TEST1234",
    "vnp_TxnRef": REF, "vnp_Amount": "10000000", "vnp_CurrCode": "VND",
    "vnp_CreateDate": "20261025100000", "vnp_ExpireDate": "20261025101000",
    "vnp_ReturnUrl": "https://merchant.example.test/api/v1/payments/vnpay/return",
    "vnp_IpAddr": "127.0.0.1", "vnp_OrderInfo": f"Thanh toan ve {ORDER}",
    "vnp_OrderType": "other", "vnp_Locale": "vn",
}
query = urlencode(sorted(pay.items()))
query_data = "|".join([
    "aaaaaaaa111142228333bbbbbbbbbbbb", "2.1.0", "querydr", "TEST1234", REF,
    "20261025100000", "20261025100200", "127.0.0.1", f"Truy van {ORDER}",
])
refund_data = "|".join([
    "bbbbbbbb111142228333aaaaaaaaaaaa", "2.1.0", "refund", "TEST1234", "02", REF,
    "10000000", "123456", "20261025100000", "sandbox-operator", "20261025100100",
    "127.0.0.1", "Hoan tien bbbbbbbb-1111-4222-8333-aaaaaaaaaaaa",
])


def sign(data):
    return hmac.new(KEY, data.encode("utf-8"), hashlib.sha512).hexdigest()


vectors = {
    "payQuery": query,
    "payUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?" + query + "&vnp_SecureHash=" + sign(query),
    "queryData": query_data, "querySignature": sign(query_data),
    "refundData": refund_data, "refundSignature": sign(refund_data),
}
Path(__file__).with_name("golden-vectors.json").write_text(
    json.dumps(vectors, indent=2, ensure_ascii=True) + "\n", encoding="utf-8"
)
