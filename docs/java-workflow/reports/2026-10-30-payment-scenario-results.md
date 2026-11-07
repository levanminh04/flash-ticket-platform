# Kết quả chạy kịch bản Payment thực tế

- Bắt đầu: 2026-10-30T02:28:51.491Z; kết thúc: 2026-10-30T02:29:20.680Z (UTC).
- Nhánh: `codex/gov-156-payment-flow`. Kết quả: **34/34 đạt**, 0 lỗi ngoài dự kiến.
- Payment chạy JAR thật, gọi HTTP thật, PostgreSQL 16.15 và RabbitMQ 4.4.0 thật trong container riêng. Không sửa Java để chạy báo cáo này.
- Booking, issuer/OAuth2 và kết quả Ticket được mô phỏng trong process test. Webhook ký bằng secret test; URL gateway là sandbox.example.test. Không có giao dịch/hoàn tiền thật.
- JWT và credential không chép vào report. UUID, amount, URL, mã lỗi, trạng thái dưới đây là giá trị thu được ở lần chạy này.
- `saga` ở mục `database` là trạng thái truy vấn trực tiếp PostgreSQL; API GET Payment hiện không trả field Saga. Broker publish routed=true chỉ là ACK vận chuyển.
- File JSON cạnh MD lưu toàn bộ dữ liệu gốc. `refund.state=PENDING` là mới ghi nghĩa vụ hoàn, chưa phải tiền đã được hoàn.

Lưu ý khởi chạy đã quan sát: JVM mặc định trên Windows dùng Asia/Saigon, PostgreSQL test trả `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"`. Bộ kịch bản chạy JAR với `-Duser.timezone=UTC`, giống cấu hình Surefire hiện có. Config Server được thay bằng tham số test cục bộ, không sửa cấu hình production.

## Những điểm thực nhận cần chú ý

- Lần chạy chính: **09:28:51 30/10/26 đến 09:29:20 30/10/26**, giờ Việt Nam (UTC+7).
- Đã chạy lại 12 test có sẵn: 9 integration + 3 client, tất cả đạt; cộng với 34 kịch bản HTTP/broker trong bảng dưới. Đây là hai nhóm kiểm chứng khác nhau.
- S04: FE gửi amount=1, API vẫn trả 200 nhưng số tiền là **180000** từ Booking. Field amount thừa được bỏ qua, không được dùng để tính tiền.
- S12/S13: Booking báo hết hạn hay hủy đều được Payment ánh xạ thành **409 PAYMENT_STATE_INVALID**. Hiện response chưa phân biệt hai lý do này. S17, Payment tự phát hiện snapshot hết hạn, trả PAYMENT_EXPIRED.
- S20/S21: webhook lỗi vẫn trả HTTP 200, phải đọc RspCode: 97 sai chữ ký, 04 sai tiền. Chỉ RspCode=00 là xử lý IPN thành công; chưa đồng nghĩa đã phát vé.
- S23–S26: attempt SUCCEEDED xuất hiện trước Saga COMPLETED. GET Payment trả latestAttempt và acceptedChargeId; trạng thái Saga bên dưới lấy từ DB, không phải field được thêm vào response.
- S28/S31/S32/S33: refund nhận được đều PENDING. Chưa có bằng chứng hoàn tiền thật hoặc Saga COMPENSATED.

### Response tạo URL thực nhận — S01

```json
{
  "data": {
    "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
    "attemptState": "PENDING",
    "amount": "180000",
    "currency": "VND",
    "paymentUrl": "https://sandbox.example.test/pay?vnp_Amount=18000000&vnp_Command=pay&vnp_CreateDate=20261030092904&vnp_CurrCode=VND&vnp_ExpireDate=20261030093904&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+b8e06b2d-53ff-4595-9cbf-d17c1c757ab0&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A18085%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Version=2.1.0&vnp_SecureHash=14a5346a410b917828e66327aecc55fff9e31d86a4b9efb2a618361c2fc1800e0748d6d8a7405af5ed9353a949d1e8e7df519a67d207f9b917c3a983f2c7f88a",
    "expiresAt": "2026-10-30T02:39:04.123Z"
  },
  "success": true,
  "message": "Payment created successfully"
}
```

### Response webhook hợp lệ thực nhận — S23

```json
{
  "RspCode": "00",
  "Message": "Confirm success"
}
```

## Bảng kịch bản

| Ca | Kịch bản | Kết quả | HTTP / thông tin quan sát |
|---|---|---|---|
| S01 | Tạo thanh toán từ snapshot có voucher: 200.000 − 20.000 = 180.000 VND | Đạt | 200 |
| S02 | Retry cùng key và IP khác: giữ nguyên attempt, URL và deadline | Đạt | 200 |
| S03 | Key khác khi đang PENDING bị chặn | Đạt | 409 — PAYMENT_IN_PROGRESS |
| S04 | FE gửi amount giả: số tiền không bị FE điều khiển | Đạt | 200 |
| S05 | Thiếu JWT | Đạt | 401 — AUTHENTICATION_REQUIRED |
| S06 | Role ORGANIZER không được tạo payment | Đạt | 403 — ACCESS_DENIED |
| S07 | JWT đã hết hạn | Đạt | 401 — AUTHENTICATION_REQUIRED |
| S08 | Thiếu Idempotency-Key | Đạt | 400 — INVALID_REQUEST |
| S09 | Idempotency-Key không phải UUID | Đạt | 400 — INVALID_REQUEST |
| S10 | Không phải chủ đơn | Đạt | 404 — RESOURCE_NOT_FOUND |
| S11 | Order không tồn tại | Đạt | 404 — RESOURCE_NOT_FOUND |
| S12 | Booking báo đơn hết hạn | Đạt | 409 — PAYMENT_STATE_INVALID |
| S13 | Booking báo đơn bị hủy | Đạt | 409 — PAYMENT_STATE_INVALID |
| S14 | Booking trả 503 | Đạt | 503 — BOOKING_UNAVAILABLE |
| S15 | Booking chậm quá read timeout | Đạt | 503 — BOOKING_UNAVAILABLE |
| S16 | Snapshot sai tổng tiền bị từ chối | Đạt | 422 — INVALID_REQUEST |
| S17 | Snapshot trả 200 nhưng deadline đã qua: Payment tự chặn | Đạt | 409 — PAYMENT_EXPIRED |
| S18 | Snapshot thay đổi sau lần lưu đầu bị conflict | Đạt | 409 — PURCHASE_SNAPSHOT_CONFLICT |
| S19 | Hai POST đồng thời cùng key chỉ tạo một attempt | Đạt | Hai response 200; cùng attemptId; DB có 1 attempt |
| S20 | Webhook sai chữ ký: HTTP 200 nhưng RspCode=97, không ghi tiền | Đạt | 200 — RspCode=97 |
| S21 | Webhook đúng chữ ký nhưng sai amount: RspCode=04 | Đạt | 200 — RspCode=04 |
| S22 | Browser return không ghi nhận thu tiền | Đạt | 302 |
| S23 | Webhook hợp lệ: SUCCEEDED + WAITING_BOOKING, broker ACK chưa tiến Saga | Đạt | 200 — RspCode=00 |
| S24 | Booking ACCEPTED: confirmation + WAITING_TICKETS + IssueTickets | Đạt | WAITING_TICKETS |
| S25 | Ticket success: Payment gửi Booking, vẫn WAITING_TICKETS | Đạt | WAITING_TICKETS |
| S26 | Booking OrderCompleted: lúc này Saga mới COMPLETED | Đạt | 200 |
| S27 | Webhook gửi lặp: RspCode=02, không thêm charge | Đạt | 200 — RspCode=02 |
| S28 | Thu thêm charge khác cùng order: tạo refund DUPLICATE_PAYMENT/PENDING | Đạt | 200 — RspCode=00 |
| S29 | Kết quả cổng chưa rõ: UNKNOWN, không tạo thêm attempt | Đạt | 409 — PAYMENT_IN_PROGRESS |
| S30 | FAILED: retry key cũ trả kết quả cũ; key mới mở attempt mới | Đạt | Cùng key: 200 FAILED, URL null; key mới: 200 PENDING |
| S31 | Tiền đến sau buyer cancel: COMPENSATING + LATE_PAYMENT/PENDING | Đạt | 200 |
| S32 | Ticket terminal FAILED: refund TICKET_ISSUANCE_FAILED/PENDING | Đạt | 200 |
| S33 | Event cancellation marker chặn success và completion đến trễ | Đạt | COMPENSATING |
| S34 | Chưa nhận kết quả Ticket: chưa tự refund hoặc hoàn tất | Đạt | 200 |

## S01 — Tạo thanh toán từ snapshot có voucher: 200.000 − 20.000 = 180.000 VND

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
          "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
          "attemptState": "PENDING",
          "amount": "180000",
          "currency": "VND",
          "paymentUrl": "https://sandbox.example.test/pay?vnp_Amount=18000000&vnp_Command=pay&vnp_CreateDate=20261030092904&vnp_CurrCode=VND&vnp_ExpireDate=20261030093904&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+b8e06b2d-53ff-4595-9cbf-d17c1c757ab0&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A18085%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Version=2.1.0&vnp_SecureHash=14a5346a410b917828e66327aecc55fff9e31d86a4b9efb2a618361c2fc1800e0748d6d8a7405af5ed9353a949d1e8e7df519a67d207f9b917c3a983f2c7f88a",
          "expiresAt": "2026-10-30T02:39:04.123Z"
        },
        "success": true,
        "message": "Payment created successfully"
      }
    }
  },
  "bookingResponseFixture": {
    "schemaVersion": 1,
    "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "eventId": "a6ba5512-0fc1-40f3-84c4-372065a314ee",
    "buyerSubject": "buyer-a",
    "customerEmail": "buyer-a@example.test",
    "sourceConfigVersion": "1",
    "sourceOrderVersion": "1",
    "frozenAt": "2026-10-30T02:29:03.123Z",
    "expiresAt": "2026-10-30T02:39:04.123Z",
    "currency": "VND",
    "eventTitle": "Payment scenario concert",
    "eventStartsAt": "2030-01-02T01:00:00Z",
    "eventEndsAt": "2030-01-02T03:00:00Z",
    "venueName": "Fixture venue",
    "mode": "QUANTITY",
    "sectorId": null,
    "subtotal": "200000",
    "discount": "20000",
    "total": "180000",
    "promotion": {
      "promotionId": "09e94c52-ce14-44d2-9324-2098543e9201",
      "code": "SALE10",
      "discount": "20000"
    },
    "items": [
      {
        "orderItemId": "0e93871d-5d4f-4a88-8692-af7d3df97e61",
        "ticketTypeId": "1676d094-5a40-4e45-9998-01c5729849e0",
        "ticketTypeName": "General",
        "quantity": 2,
        "unitPrice": "100000",
        "lineSubtotal": "200000",
        "sectorId": null,
        "seats": []
      }
    ]
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S02 — Retry cùng key và IP khác: giữ nguyên attempt, URL và deadline

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
        "clientIp": "203.0.113.99"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
          "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
          "attemptState": "PENDING",
          "amount": "180000",
          "currency": "VND",
          "paymentUrl": "https://sandbox.example.test/pay?vnp_Amount=18000000&vnp_Command=pay&vnp_CreateDate=20261030092904&vnp_CurrCode=VND&vnp_ExpireDate=20261030093904&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+b8e06b2d-53ff-4595-9cbf-d17c1c757ab0&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A18085%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Version=2.1.0&vnp_SecureHash=14a5346a410b917828e66327aecc55fff9e31d86a4b9efb2a618361c2fc1800e0748d6d8a7405af5ed9353a949d1e8e7df519a67d207f9b917c3a983f2c7f88a",
          "expiresAt": "2026-10-30T02:39:04.123Z"
        },
        "success": true,
        "message": "Payment created successfully"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S03 — Key khác khi đang PENDING bị chặn

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "24a56d80-7c0b-4fce-bc50-302117998705"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 409,
      "body": {
        "data": {
          "code": "PAYMENT_IN_PROGRESS",
          "message": "A payment attempt is still in progress.",
          "correlationId": "91119263-fc0f-4c15-9d49-50d64ec6c026",
          "violations": []
        },
        "success": false,
        "message": "A payment attempt is still in progress."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S04 — FE gửi amount giả: số tiền không bị FE điều khiển

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "48d669ec-aaa1-4a60-b5dd-8d56130bd4ee"
      },
      "body": {
        "orderId": "f3bed4a9-7625-4d78-b8b8-7531263b638c",
        "amount": "1"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "f3bed4a9-7625-4d78-b8b8-7531263b638c",
          "attemptId": "c5e178ba-a16a-457a-9107-12d84836cfb2",
          "attemptState": "PENDING",
          "amount": "180000",
          "currency": "VND",
          "paymentUrl": "https://sandbox.example.test/pay?vnp_Amount=18000000&vnp_Command=pay&vnp_CreateDate=20261030092905&vnp_CurrCode=VND&vnp_ExpireDate=20261030093905&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+f3bed4a9-7625-4d78-b8b8-7531263b638c&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A18085%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=test&vnp_TxnRef=c5e178ba-a16a-457a-9107-12d84836cfb2&vnp_Version=2.1.0&vnp_SecureHash=5199db2f3c8e08d1c133337b675b90981c66b9e9574023015acc976c132caa857438c4b8669723cf9664f0a300e4fc0c3617fa7b68768759698e329425483e55",
          "expiresAt": "2026-10-30T02:39:05.482Z"
        },
        "success": true,
        "message": "Payment created successfully"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "c5e178ba-a16a-457a-9107-12d84836cfb2",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "48d669ec-aaa1-4a60-b5dd-8d56130bd4ee"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S05 — Thiếu JWT

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Idempotency-Key": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 401,
      "body": {
        "data": {
          "code": "AUTHENTICATION_REQUIRED",
          "message": "A valid bearer token is required.",
          "correlationId": "debc8341-49ae-4339-93ad-91da51b0d3c2",
          "violations": []
        },
        "success": false,
        "message": "A valid bearer token is required."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S06 — Role ORGANIZER không được tạo payment

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 403,
      "body": {
        "data": {
          "code": "ACCESS_DENIED",
          "message": "Access is denied.",
          "correlationId": "82eb1388-18f0-4bcd-b391-4363b8ab2f7a",
          "violations": []
        },
        "success": false,
        "message": "Access is denied."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S07 — JWT đã hết hạn

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 401,
      "body": {
        "data": {
          "code": "AUTHENTICATION_REQUIRED",
          "message": "A valid bearer token is required.",
          "correlationId": "1e9efa0b-1c5a-41ef-9d55-6381e92860f2",
          "violations": []
        },
        "success": false,
        "message": "A valid bearer token is required."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S08 — Thiếu Idempotency-Key

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 400,
      "body": {
        "data": {
          "code": "INVALID_REQUEST",
          "message": "Request parameters are invalid.",
          "correlationId": "c5f6e236-adea-424e-bca5-37f24b2ebd24",
          "violations": []
        },
        "success": false,
        "message": "Request parameters are invalid."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S09 — Idempotency-Key không phải UUID

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "not-a-uuid"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 400,
      "body": {
        "data": {
          "code": "INVALID_REQUEST",
          "message": "Request parameters are invalid.",
          "correlationId": "f1853897-0f58-4f5a-a350-c22cffd46c3f",
          "violations": [
            {
              "field": "Idempotency-Key",
              "code": "INVALID_UUID"
            }
          ]
        },
        "success": false,
        "message": "Request parameters are invalid."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S10 — Không phải chủ đơn

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 404,
      "body": {
        "data": {
          "code": "RESOURCE_NOT_FOUND",
          "message": "Resource was not found.",
          "correlationId": "a81b5525-9c13-46c0-8784-f135dab99656",
          "violations": []
        },
        "success": false,
        "message": "Resource was not found."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S11 — Order không tồn tại

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "0673b7f0-98d5-4dac-b269-3b2dd8fcfd73"
      },
      "body": {
        "orderId": "afe94997-8e2e-44c4-8c95-61d02e0ac488"
      }
    },
    "response": {
      "status": 404,
      "body": {
        "data": {
          "code": "RESOURCE_NOT_FOUND",
          "message": "Resource was not found.",
          "correlationId": "82536a86-4b56-4438-92f4-e2d6e4768742",
          "violations": []
        },
        "success": false,
        "message": "Resource was not found."
      }
    }
  },
  "database": {
    "attempts": [],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S12 — Booking báo đơn hết hạn

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "4f7dfebf-9c61-4454-8d46-10a89896020a"
      },
      "body": {
        "orderId": "dd627d50-cac5-4017-b551-6b142c46181c"
      }
    },
    "response": {
      "status": 409,
      "body": {
        "data": {
          "code": "PAYMENT_STATE_INVALID",
          "message": "Payment cannot be started in its current state.",
          "correlationId": "f92cde3f-5aef-4e48-9db0-3a5abd5bcb1d",
          "violations": []
        },
        "success": false,
        "message": "Payment cannot be started in its current state."
      }
    }
  },
  "bookingMode": "expired",
  "database": {
    "attempts": [],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S13 — Booking báo đơn bị hủy

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "561ffcec-3048-4fa1-b72b-f953eb202e48"
      },
      "body": {
        "orderId": "bab6744a-aa1c-490b-a3ae-638284b8795b"
      }
    },
    "response": {
      "status": 409,
      "body": {
        "data": {
          "code": "PAYMENT_STATE_INVALID",
          "message": "Payment cannot be started in its current state.",
          "correlationId": "0873d0d1-7bb3-4099-8bc8-00370c8a0ece",
          "violations": []
        },
        "success": false,
        "message": "Payment cannot be started in its current state."
      }
    }
  },
  "bookingMode": "cancelled",
  "database": {
    "attempts": [],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S14 — Booking trả 503

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "0512a45d-575e-4e9b-8e68-51bd3f572820"
      },
      "body": {
        "orderId": "147d855b-e1c8-45d3-bd8e-1daff3cdcc24"
      }
    },
    "response": {
      "status": 503,
      "body": {
        "data": {
          "code": "BOOKING_UNAVAILABLE",
          "message": "Booking is temporarily unavailable.",
          "correlationId": "a12ecbe5-c1d2-4d6b-a550-c0739c5f3778",
          "violations": []
        },
        "success": false,
        "message": "Booking is temporarily unavailable."
      }
    }
  },
  "bookingMode": "unavailable",
  "database": {
    "attempts": [],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S15 — Booking chậm quá read timeout

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "834e210b-3dae-4a65-9d6e-45355ba497c0"
      },
      "body": {
        "orderId": "0c494813-871c-4acc-af90-6fab941c22cd"
      }
    },
    "response": {
      "status": 503,
      "body": {
        "data": {
          "code": "BOOKING_UNAVAILABLE",
          "message": "Booking is temporarily unavailable.",
          "correlationId": "9894dd08-e4b2-4d10-9f3e-7e27a111572f",
          "violations": []
        },
        "success": false,
        "message": "Booking is temporarily unavailable."
      }
    }
  },
  "bookingMode": "timeout",
  "database": {
    "attempts": [],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S16 — Snapshot sai tổng tiền bị từ chối

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "be8b50c4-e1cb-4d81-a6b3-77720f9b65bd"
      },
      "body": {
        "orderId": "08673fea-2de9-4b58-99d3-dba4e69aab6e"
      }
    },
    "response": {
      "status": 422,
      "body": {
        "data": {
          "code": "INVALID_REQUEST",
          "message": "Request parameters are invalid.",
          "correlationId": "f0e33b79-395d-409a-8d26-9c44a63993c5",
          "violations": [
            {
              "field": "$.total",
              "code": "TOTAL_MISMATCH"
            }
          ]
        },
        "success": false,
        "message": "Request parameters are invalid."
      }
    }
  },
  "database": {
    "attempts": [],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S17 — Snapshot trả 200 nhưng deadline đã qua: Payment tự chặn

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "b1bdfa2d-9a9a-437c-b7e5-b7cd1f1ecdda"
      },
      "body": {
        "orderId": "6add3004-188c-45c4-a15c-ae2a0717f358"
      }
    },
    "response": {
      "status": 409,
      "body": {
        "data": {
          "code": "PAYMENT_EXPIRED",
          "message": "The order payment window has expired.",
          "correlationId": "ead39b4c-8bf1-4f69-b851-868d2b2b2e12",
          "violations": []
        },
        "success": false,
        "message": "The order payment window has expired."
      }
    }
  },
  "database": {
    "attempts": [],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S18 — Snapshot thay đổi sau lần lưu đầu bị conflict

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      },
      "body": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0"
      }
    },
    "response": {
      "status": 409,
      "body": {
        "data": {
          "code": "PURCHASE_SNAPSHOT_CONFLICT",
          "message": "The stored purchase snapshot differs.",
          "correlationId": "0a82e580-9617-4d65-a67f-35f711b4acb0",
          "violations": []
        },
        "success": false,
        "message": "The stored purchase snapshot differs."
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S19 — Hai POST đồng thời cùng key chỉ tạo một attempt

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "httpResponses": [
    {
      "request": {
        "method": "POST",
        "path": "/api/v1/payments",
        "headers": {
          "Content-Type": "application/json",
          "Authorization": "[test JWT đã che]",
          "Idempotency-Key": "a608ab5b-557f-42ce-a4b6-1c7b575a6e43"
        },
        "body": {
          "orderId": "0d162a17-5f78-44d9-9783-cd130d7ef74b"
        }
      },
      "response": {
        "status": 200,
        "body": {
          "data": {
            "orderId": "0d162a17-5f78-44d9-9783-cd130d7ef74b",
            "attemptId": "127a63e1-0d0e-4413-a1ea-c0805d50ff5a",
            "attemptState": "PENDING",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": "https://sandbox.example.test/pay?vnp_Amount=18000000&vnp_Command=pay&vnp_CreateDate=20261030092909&vnp_CurrCode=VND&vnp_ExpireDate=20261030093909&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+0d162a17-5f78-44d9-9783-cd130d7ef74b&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A18085%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=test&vnp_TxnRef=127a63e1-0d0e-4413-a1ea-c0805d50ff5a&vnp_Version=2.1.0&vnp_SecureHash=3638f498a035011ec7ee4905a9015cc3252b5eccf68b38b3fa42f2f4e79e3e5959778fe1947f96d8d2d7fb7cfd6bfc8da74e6f5498f01e3b01657a8590d3dc65",
            "expiresAt": "2026-10-30T02:39:09.312Z"
          },
          "success": true,
          "message": "Payment created successfully"
        }
      }
    },
    {
      "request": {
        "method": "POST",
        "path": "/api/v1/payments",
        "headers": {
          "Content-Type": "application/json",
          "Authorization": "[test JWT đã che]",
          "Idempotency-Key": "a608ab5b-557f-42ce-a4b6-1c7b575a6e43"
        },
        "body": {
          "orderId": "0d162a17-5f78-44d9-9783-cd130d7ef74b"
        }
      },
      "response": {
        "status": 200,
        "body": {
          "data": {
            "orderId": "0d162a17-5f78-44d9-9783-cd130d7ef74b",
            "attemptId": "127a63e1-0d0e-4413-a1ea-c0805d50ff5a",
            "attemptState": "PENDING",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": "https://sandbox.example.test/pay?vnp_Amount=18000000&vnp_Command=pay&vnp_CreateDate=20261030092909&vnp_CurrCode=VND&vnp_ExpireDate=20261030093909&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+0d162a17-5f78-44d9-9783-cd130d7ef74b&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A18085%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=test&vnp_TxnRef=127a63e1-0d0e-4413-a1ea-c0805d50ff5a&vnp_Version=2.1.0&vnp_SecureHash=3638f498a035011ec7ee4905a9015cc3252b5eccf68b38b3fa42f2f4e79e3e5959778fe1947f96d8d2d7fb7cfd6bfc8da74e6f5498f01e3b01657a8590d3dc65",
            "expiresAt": "2026-10-30T02:39:09.312Z"
          },
          "success": true,
          "message": "Payment created successfully"
        }
      }
    }
  ],
  "database": {
    "attempts": [
      {
        "attemptId": "127a63e1-0d0e-4413-a1ea-c0805d50ff5a",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "a608ab5b-557f-42ce-a4b6-1c7b575a6e43"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S20 — Webhook sai chữ ký: HTTP 200 nhưng RspCode=97, không ghi tiền

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Amount=18000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TransactionNo=676bd4b1-b7eb-409e-a7c4-5167faeab8fa&vnp_PayDate=20261030092909&vnp_SecureHash=invalid-signature",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "97",
        "Message": "Invalid signature"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S21 — Webhook đúng chữ ký nhưng sai amount: RspCode=04

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Amount=100&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TransactionNo=716b5853-8fdb-42ba-a780-12fcb9f09054&vnp_PayDate=20261030092909&vnp_SecureHash=744a88b203189949be7d4a19355f8ac69021db937419e79b3af432dd93b89ae35a3b83e1d84bef4c175aa8884a7fabf44de77413779a7a882dc64f51bd229448",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "04",
        "Message": "Amount invalid"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S22 — Browser return không ghi nhận thu tiền

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/return?vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Amount=100&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TransactionNo=716b5853-8fdb-42ba-a780-12fcb9f09054&vnp_PayDate=20261030092909&vnp_SecureHash=744a88b203189949be7d4a19355f8ac69021db937419e79b3af432dd93b89ae35a3b83e1d84bef4c175aa8884a7fabf44de77413779a7a882dc64f51bd229448",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 302,
      "body": "",
      "location": "https://frontend.example.test/payment?orderId=b8e06b2d-53ff-4595-9cbf-d17c1c757ab0&attemptId=aa9ded20-e384-41f4-a1d1-1ff384873473&status=PENDING&responseCode=00"
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S23 — Webhook hợp lệ: SUCCEEDED + WAITING_BOOKING, broker ACK chưa tiến Saga

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Amount=18000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TransactionNo=698e194a-e419-45e0-99f6-3070fccb7b8c&vnp_PayDate=20261030092910&vnp_SecureHash=294cacb4511d0dc26d268f0be232eba0d0a978141ca1d82791511ea9ad022357a94b1eb62477381550b0296563a0e89cc03b27f660fae3e948f25ca401a10673",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "00",
        "Message": "Confirm success"
      }
    }
  },
  "publicPaymentQuery": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/b8e06b2d-53ff-4595-9cbf-d17c1c757ab0/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
          "acceptedChargeId": null,
          "latestAttempt": {
            "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
            "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:04.123Z"
          },
          "startRequest": null,
          "refunds": []
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "emitted": {
    "data": {
      "amount": "180000",
      "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
      "currency": "VND",
      "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
      "sourceOrderVersion": "1"
    },
    "messageId": "a106eb15-bd0b-47ab-a2bd-44eb6a40557b",
    "occurredAt": "2026-10-30T02:29:10.547470800Z",
    "aggregateId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "causationId": null,
    "messageType": "booking.accept-payment.v1",
    "traceparent": null,
    "correlationId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "schemaVersion": 1,
    "aggregateVersion": "1"
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": {
      "state": "WAITING_BOOKING",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb"
    },
    "chargeCount": 1,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      }
    ]
  }
}
```

## S24 — Booking ACCEPTED: confirmation + WAITING_TICKETS + IssueTickets

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "received": {
    "envelope": {
      "messageId": "41a6baa3-72c0-469a-8936-744b5621f389",
      "messageType": "booking.payment-decision.v1",
      "schemaVersion": 1,
      "aggregateId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "aggregateVersion": "1",
      "occurredAt": "2026-10-30T02:29:11.304Z",
      "correlationId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "causationId": null,
      "traceparent": null,
      "data": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
        "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
        "decisionAt": "2026-10-30T02:29:11.303Z",
        "outcome": "ACCEPTED",
        "acceptedChargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb"
      }
    },
    "brokerResult": {
      "routed": true
    }
  },
  "publicPaymentQuery": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/b8e06b2d-53ff-4595-9cbf-d17c1c757ab0/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
          "acceptedChargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
          "latestAttempt": {
            "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
            "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:04.123Z"
          },
          "startRequest": null,
          "refunds": []
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "emitted": {
    "data": {
      "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
      "purchase": {
        "mode": "QUANTITY",
        "items": [
          {
            "seats": [],
            "quantity": 2,
            "sectorId": null,
            "unitPrice": "100000",
            "orderItemId": "0e93871d-5d4f-4a88-8692-af7d3df97e61",
            "lineSubtotal": "200000",
            "ticketTypeId": "1676d094-5a40-4e45-9998-01c5729849e0",
            "ticketTypeName": "General"
          }
        ],
        "total": "180000",
        "eventId": "a6ba5512-0fc1-40f3-84c4-372065a314ee",
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
        "currency": "VND",
        "discount": "20000",
        "frozenAt": "2026-10-30T02:29:03.123Z",
        "sectorId": null,
        "subtotal": "200000",
        "expiresAt": "2026-10-30T02:39:04.123Z",
        "promotion": {
          "code": "SALE10",
          "discount": "20000",
          "promotionId": "09e94c52-ce14-44d2-9324-2098543e9201"
        },
        "venueName": "Fixture venue",
        "eventTitle": "Payment scenario concert",
        "eventEndsAt": "2030-01-02T03:00:00Z",
        "buyerSubject": "buyer-a",
        "customerEmail": "buyer-a@example.test",
        "eventStartsAt": "2030-01-02T01:00:00Z",
        "schemaVersion": 1,
        "sourceOrderVersion": "1",
        "sourceConfigVersion": "1"
      }
    },
    "messageId": "82211b68-3cd2-48fc-9dd8-6b3b86b386e6",
    "occurredAt": "2026-10-30T02:29:11.389049900Z",
    "aggregateId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "causationId": "41a6baa3-72c0-469a-8936-744b5621f389",
    "messageType": "ticket.issue.v1",
    "traceparent": null,
    "correlationId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "schemaVersion": 1,
    "aggregateVersion": "2"
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": {
      "state": "WAITING_TICKETS",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb"
    },
    "chargeCount": 1,
    "confirmationCount": 1,
    "refunds": [],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      }
    ]
  }
}
```

## S25 — Ticket success: Payment gửi Booking, vẫn WAITING_TICKETS

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "received": {
    "envelope": {
      "messageId": "57b23178-809c-4cd4-85cb-b744e77adefa",
      "messageType": "ticket.issuance-result.v1",
      "schemaVersion": 1,
      "aggregateId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "aggregateVersion": "1",
      "occurredAt": "2026-10-30T02:29:11.931Z",
      "correlationId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "causationId": null,
      "traceparent": null,
      "data": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
        "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
        "issuanceId": "d4a78cca-0db5-4f91-b8d5-d8d9b650d1ff",
        "state": "COMPLETED",
        "ticketCount": 2,
        "failureCode": null
      }
    },
    "brokerResult": {
      "routed": true
    }
  },
  "publicPaymentQuery": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/b8e06b2d-53ff-4595-9cbf-d17c1c757ab0/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
          "acceptedChargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
          "latestAttempt": {
            "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
            "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:04.123Z"
          },
          "startRequest": null,
          "refunds": []
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "emitted": {
    "data": {
      "state": "COMPLETED",
      "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
      "issuanceId": "d4a78cca-0db5-4f91-b8d5-d8d9b650d1ff",
      "failureCode": null,
      "ticketCount": 2
    },
    "messageId": "f5b86dd2-d54f-4986-a58c-d6cd26bd2438",
    "occurredAt": "2026-10-30T02:29:11.985400900Z",
    "aggregateId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "causationId": "57b23178-809c-4cd4-85cb-b744e77adefa",
    "messageType": "booking.issuance-result.v1",
    "traceparent": null,
    "correlationId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
    "schemaVersion": 1,
    "aggregateVersion": "3"
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": {
      "state": "WAITING_TICKETS",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb"
    },
    "chargeCount": 1,
    "confirmationCount": 1,
    "refunds": [],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      },
      {
        "messageType": "booking.issuance-result.v1",
        "published": true
      }
    ]
  }
}
```

## S26 — Booking OrderCompleted: lúc này Saga mới COMPLETED

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "received": {
    "envelope": {
      "messageId": "2b0e3b1a-aac1-4c7e-92fd-bdf0f3739ab6",
      "messageType": "booking.order-completed.v1",
      "schemaVersion": 1,
      "aggregateId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "aggregateVersion": "1",
      "occurredAt": "2026-10-30T02:29:12.702Z",
      "correlationId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
      "causationId": null,
      "traceparent": null,
      "data": {
        "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
        "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
        "issuanceId": "d4a78cca-0db5-4f91-b8d5-d8d9b650d1ff",
        "ticketCount": 2
      }
    },
    "brokerResult": {
      "routed": true
    }
  },
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/b8e06b2d-53ff-4595-9cbf-d17c1c757ab0/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
          "acceptedChargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
          "latestAttempt": {
            "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
            "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:04.123Z"
          },
          "startRequest": null,
          "refunds": []
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": {
      "state": "COMPLETED",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb"
    },
    "chargeCount": 1,
    "confirmationCount": 1,
    "refunds": [],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      },
      {
        "messageType": "booking.issuance-result.v1",
        "published": true
      }
    ]
  }
}
```

## S27 — Webhook gửi lặp: RspCode=02, không thêm charge

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Amount=18000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TransactionNo=698e194a-e419-45e0-99f6-3070fccb7b8c&vnp_PayDate=20261030092910&vnp_SecureHash=294cacb4511d0dc26d268f0be232eba0d0a978141ca1d82791511ea9ad022357a94b1eb62477381550b0296563a0e89cc03b27f660fae3e948f25ca401a10673",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "02",
        "Message": "Order already confirmed"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": {
      "state": "COMPLETED",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb"
    },
    "chargeCount": 1,
    "confirmationCount": 1,
    "refunds": [],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      },
      {
        "messageType": "booking.issuance-result.v1",
        "published": true
      }
    ]
  }
}
```

## S28 — Thu thêm charge khác cùng order: tạo refund DUPLICATE_PAYMENT/PENDING

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=aa9ded20-e384-41f4-a1d1-1ff384873473&vnp_Amount=18000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TransactionNo=c6531745-6140-44cc-a20c-375f8427ab7f&vnp_PayDate=20261030092913&vnp_SecureHash=b47d2c3a182b1a50256f9eedceda0417baf6f7cf5dd3d904a468a083a580e69fc061a0675f8a3e2f45371805c5fd9f9744caa69ef2d6caba7074cd3ad9767478",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "00",
        "Message": "Confirm success"
      }
    }
  },
  "publicPaymentQuery": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/b8e06b2d-53ff-4595-9cbf-d17c1c757ab0/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
          "acceptedChargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb",
          "latestAttempt": {
            "orderId": "b8e06b2d-53ff-4595-9cbf-d17c1c757ab0",
            "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:04.123Z"
          },
          "startRequest": null,
          "refunds": [
            {
              "refundId": "79bb8261-d69b-4bb0-85a4-38dfba00650f",
              "chargeId": "bc124d57-4acf-441e-a0d6-8864eaacc63c",
              "amount": "180000",
              "state": "PENDING",
              "reason": "DUPLICATE_PAYMENT"
            }
          ]
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "aa9ded20-e384-41f4-a1d1-1ff384873473",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "b5ec72c9-8b88-4048-bc7e-0a9cde58e8c8"
      }
    ],
    "saga": {
      "state": "COMPLETED",
      "chargeId": "bac19627-54d4-4f4d-905a-f3fb2c26c2cb"
    },
    "chargeCount": 2,
    "confirmationCount": 1,
    "refunds": [
      {
        "state": "PENDING",
        "reason": "DUPLICATE_PAYMENT",
        "amount": "180000"
      }
    ],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      },
      {
        "messageType": "booking.issuance-result.v1",
        "published": true
      }
    ]
  }
}
```

## S29 — Kết quả cổng chưa rõ: UNKNOWN, không tạo thêm attempt

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "providerResponse": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=93cea862-567e-44db-a7eb-aa8af6635764&vnp_Amount=18000000&vnp_ResponseCode=07&vnp_TransactionStatus=01&vnp_TransactionNo=7ffffc72-fdc8-4bba-af91-31d53a767fcf&vnp_PayDate=20261030092913&vnp_SecureHash=3b0069fc3a8ae29b81071bcad4abc212ddad696abe55e9837bd9a16341a3c773f299c87c1f9ca5883bb250d73561ad1cafae092afa1730ff3e732c5d0a1a77f2",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "00",
        "Message": "Confirm success"
      }
    }
  },
  "http": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "45532b90-9184-4a9d-adc2-4668db652105"
      },
      "body": {
        "orderId": "28b5647c-0010-4c50-b303-4994456fe74c"
      }
    },
    "response": {
      "status": 409,
      "body": {
        "data": {
          "code": "PAYMENT_IN_PROGRESS",
          "message": "A payment attempt is still in progress.",
          "correlationId": "9e9323f9-0bd3-4a74-a620-a4de4d8a34fd",
          "violations": []
        },
        "success": false,
        "message": "A payment attempt is still in progress."
      }
    }
  },
  "publicPaymentQuery": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/28b5647c-0010-4c50-b303-4994456fe74c/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "28b5647c-0010-4c50-b303-4994456fe74c",
          "acceptedChargeId": null,
          "latestAttempt": {
            "orderId": "28b5647c-0010-4c50-b303-4994456fe74c",
            "attemptId": "93cea862-567e-44db-a7eb-aa8af6635764",
            "attemptState": "UNKNOWN",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:13.668Z"
          },
          "startRequest": null,
          "refunds": []
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "93cea862-567e-44db-a7eb-aa8af6635764",
        "state": "UNKNOWN",
        "amount": "180000",
        "requestKey": "45532b90-9184-4a9d-adc2-4668db652105"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S30 — FAILED: retry key cũ trả kết quả cũ; key mới mở attempt mới

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "providerResponse": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=c6643938-9043-4957-9359-ba9921ccf658&vnp_Amount=18000000&vnp_ResponseCode=24&vnp_TransactionStatus=02&vnp_TransactionNo=bfcdca8a-877a-49a0-90e6-727bf396030a&vnp_PayDate=20261030092914&vnp_SecureHash=c67dac19aa543c2b92e3f07642a2aa3a6d4467fae93abbb527a0229030b7497cedebfb33e92a9b45331746666b00973bae6128b327c92536d92513d1ccaeaf13",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "00",
        "Message": "Confirm success"
      }
    }
  },
  "sameKey": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "f691ddfd-f0ba-479c-b468-7a631c05a3a0"
      },
      "body": {
        "orderId": "a961d316-a6b4-4de4-92c0-c3552a943c2a"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "a961d316-a6b4-4de4-92c0-c3552a943c2a",
          "attemptId": "c6643938-9043-4957-9359-ba9921ccf658",
          "attemptState": "FAILED",
          "amount": "180000",
          "currency": "VND",
          "paymentUrl": null,
          "expiresAt": "2026-10-30T02:39:14.262Z"
        },
        "success": true,
        "message": "Payment created successfully"
      }
    }
  },
  "newKey": {
    "request": {
      "method": "POST",
      "path": "/api/v1/payments",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]",
        "Idempotency-Key": "a6cdbd9f-40cf-44b0-8ba9-67aeb5e5a0e8"
      },
      "body": {
        "orderId": "a961d316-a6b4-4de4-92c0-c3552a943c2a"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "a961d316-a6b4-4de4-92c0-c3552a943c2a",
          "attemptId": "d7ecf784-1ebb-4855-93f9-0b86c006150f",
          "attemptState": "PENDING",
          "amount": "180000",
          "currency": "VND",
          "paymentUrl": "https://sandbox.example.test/pay?vnp_Amount=18000000&vnp_Command=pay&vnp_CreateDate=20261030092914&vnp_CurrCode=VND&vnp_ExpireDate=20261030093914&vnp_IpAddr=127.0.0.1&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+ve+a961d316-a6b4-4de4-92c0-c3552a943c2a&vnp_OrderType=billpayment&vnp_ReturnUrl=http%3A%2F%2F127.0.0.1%3A18085%2Fapi%2Fv1%2Fpayments%2Fvnpay%2Freturn&vnp_TmnCode=test&vnp_TxnRef=d7ecf784-1ebb-4855-93f9-0b86c006150f&vnp_Version=2.1.0&vnp_SecureHash=9dee4f7f2b4072d0eff9d4174e19607c0d007fa8d4370fd12312dfb58a7f7620d86fb572488764f80e4d5f9877a7fdb62854761c09b959df118c9dae27ba851c",
          "expiresAt": "2026-10-30T02:39:14.262Z"
        },
        "success": true,
        "message": "Payment created successfully"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "c6643938-9043-4957-9359-ba9921ccf658",
        "state": "FAILED",
        "amount": "180000",
        "requestKey": "f691ddfd-f0ba-479c-b468-7a631c05a3a0"
      },
      {
        "attemptId": "d7ecf784-1ebb-4855-93f9-0b86c006150f",
        "state": "PENDING",
        "amount": "180000",
        "requestKey": "a6cdbd9f-40cf-44b0-8ba9-67aeb5e5a0e8"
      }
    ],
    "saga": null,
    "chargeCount": 0,
    "confirmationCount": 0,
    "refunds": [],
    "outbox": []
  }
}
```

## S31 — Tiền đến sau buyer cancel: COMPENSATING + LATE_PAYMENT/PENDING

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "providerResponse": {
    "request": {
      "method": "GET",
      "path": "/api/v1/payments/vnpay/ipn?vnp_TmnCode=test&vnp_TxnRef=563da9e8-f738-4683-822c-297356f1ba4e&vnp_Amount=18000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TransactionNo=f5ae9a66-9d5a-49a0-97f0-981503787319&vnp_PayDate=20261030092914&vnp_SecureHash=2dc7c146fa4836cb2320a0bb35e62cf3a5fa15efea1ade9dadc3489286798d96ebaade99ac689347af13371acbcb79aafffda6c277278ba5e93fbf64ca884856",
      "headers": {
        "Content-Type": "application/json"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "RspCode": "00",
        "Message": "Confirm success"
      }
    }
  },
  "received": {
    "envelope": {
      "messageId": "3252eb6f-6900-4407-8246-b782da7cd15d",
      "messageType": "booking.payment-decision.v1",
      "schemaVersion": 1,
      "aggregateId": "3dffd8d2-ea79-479e-9ab6-8769ca689ca7",
      "aggregateVersion": "1",
      "occurredAt": "2026-10-30T02:29:15.042Z",
      "correlationId": "3dffd8d2-ea79-479e-9ab6-8769ca689ca7",
      "causationId": null,
      "traceparent": null,
      "data": {
        "orderId": "3dffd8d2-ea79-479e-9ab6-8769ca689ca7",
        "chargeId": "0d4f2aa2-e813-4e9b-b105-2205bf910785",
        "decisionAt": "2026-10-30T02:29:15.042Z",
        "outcome": "CANCELLED",
        "acceptedChargeId": null
      }
    },
    "brokerResult": {
      "routed": true
    }
  },
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/3dffd8d2-ea79-479e-9ab6-8769ca689ca7/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "3dffd8d2-ea79-479e-9ab6-8769ca689ca7",
          "acceptedChargeId": null,
          "latestAttempt": {
            "orderId": "3dffd8d2-ea79-479e-9ab6-8769ca689ca7",
            "attemptId": "563da9e8-f738-4683-822c-297356f1ba4e",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:14.648Z"
          },
          "startRequest": null,
          "refunds": [
            {
              "refundId": "5b4e9460-cd30-4ae2-8db5-200f8d3209ea",
              "chargeId": "0d4f2aa2-e813-4e9b-b105-2205bf910785",
              "amount": "180000",
              "state": "PENDING",
              "reason": "LATE_PAYMENT"
            }
          ]
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "563da9e8-f738-4683-822c-297356f1ba4e",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "cfd861c6-423d-4e68-93ec-613ea0aa0318"
      }
    ],
    "saga": {
      "state": "COMPENSATING",
      "chargeId": "0d4f2aa2-e813-4e9b-b105-2205bf910785"
    },
    "chargeCount": 1,
    "confirmationCount": 0,
    "refunds": [
      {
        "state": "PENDING",
        "reason": "LATE_PAYMENT",
        "amount": "180000"
      }
    ],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      }
    ]
  }
}
```

## S32 — Ticket terminal FAILED: refund TICKET_ISSUANCE_FAILED/PENDING

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "received": {
    "envelope": {
      "messageId": "3e245521-2495-4679-a844-d71d1002ff3a",
      "messageType": "ticket.issuance-result.v1",
      "schemaVersion": 1,
      "aggregateId": "7992041d-3688-4a5d-9881-922eb828e57b",
      "aggregateVersion": "1",
      "occurredAt": "2026-10-30T02:29:15.927Z",
      "correlationId": "7992041d-3688-4a5d-9881-922eb828e57b",
      "causationId": null,
      "traceparent": null,
      "data": {
        "orderId": "7992041d-3688-4a5d-9881-922eb828e57b",
        "chargeId": "872b071f-9515-4506-866d-690ec1a251e7",
        "issuanceId": "4d7a6d11-7d85-402c-8489-80825bafb508",
        "state": "FAILED",
        "ticketCount": 0,
        "failureCode": "TERMINAL_ISSUANCE_FAILURE"
      }
    },
    "brokerResult": {
      "routed": true
    }
  },
  "emitted": {
    "data": {
      "state": "FAILED",
      "orderId": "7992041d-3688-4a5d-9881-922eb828e57b",
      "chargeId": "872b071f-9515-4506-866d-690ec1a251e7",
      "issuanceId": "4d7a6d11-7d85-402c-8489-80825bafb508",
      "failureCode": "TERMINAL_ISSUANCE_FAILURE",
      "ticketCount": 0
    },
    "messageId": "c58d428e-8431-4542-ae5e-70e98302b3c5",
    "occurredAt": "2026-10-30T02:29:15.986087400Z",
    "aggregateId": "7992041d-3688-4a5d-9881-922eb828e57b",
    "causationId": "3e245521-2495-4679-a844-d71d1002ff3a",
    "messageType": "booking.issuance-result.v1",
    "traceparent": null,
    "correlationId": "7992041d-3688-4a5d-9881-922eb828e57b",
    "schemaVersion": 1,
    "aggregateVersion": "3"
  },
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/7992041d-3688-4a5d-9881-922eb828e57b/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "7992041d-3688-4a5d-9881-922eb828e57b",
          "acceptedChargeId": "872b071f-9515-4506-866d-690ec1a251e7",
          "latestAttempt": {
            "orderId": "7992041d-3688-4a5d-9881-922eb828e57b",
            "attemptId": "af306427-ed8f-4834-8fb5-9dc681549129",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:15.403Z"
          },
          "startRequest": null,
          "refunds": [
            {
              "refundId": "0eb12864-43a5-4c48-93fe-1e26f2f7fbe2",
              "chargeId": "872b071f-9515-4506-866d-690ec1a251e7",
              "amount": "180000",
              "state": "PENDING",
              "reason": "TICKET_ISSUANCE_FAILED"
            }
          ]
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "af306427-ed8f-4834-8fb5-9dc681549129",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "4f75214a-008c-4238-a8e4-4f7334a821c7"
      }
    ],
    "saga": {
      "state": "COMPENSATING",
      "chargeId": "872b071f-9515-4506-866d-690ec1a251e7"
    },
    "chargeCount": 1,
    "confirmationCount": 1,
    "refunds": [
      {
        "state": "PENDING",
        "reason": "TICKET_ISSUANCE_FAILED",
        "amount": "180000"
      }
    ],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "booking.issuance-result.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      }
    ]
  }
}
```

## S33 — Event cancellation marker chặn success và completion đến trễ

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "fixtureAction": "Đã chèn cancellation marker vào DB test, không gọi Event service",
  "receivedTicket": {
    "envelope": {
      "messageId": "1291c50c-e4b3-407d-8fec-f2acd1ee3487",
      "messageType": "ticket.issuance-result.v1",
      "schemaVersion": 1,
      "aggregateId": "566b8280-0f29-4e4d-8706-5505852ee857",
      "aggregateVersion": "1",
      "occurredAt": "2026-10-30T02:29:17.578Z",
      "correlationId": "566b8280-0f29-4e4d-8706-5505852ee857",
      "causationId": null,
      "traceparent": null,
      "data": {
        "orderId": "566b8280-0f29-4e4d-8706-5505852ee857",
        "chargeId": "72cf66d9-6b1b-4f85-aa07-4624fef06084",
        "issuanceId": "cd927efd-b2a4-4334-ba62-36e24212df22",
        "state": "COMPLETED",
        "ticketCount": 2,
        "failureCode": null
      }
    },
    "brokerResult": {
      "routed": true
    }
  },
  "receivedCompletion": {
    "envelope": {
      "messageId": "9a92eb39-fac0-4b59-b6d4-f8759a726054",
      "messageType": "booking.order-completed.v1",
      "schemaVersion": 1,
      "aggregateId": "566b8280-0f29-4e4d-8706-5505852ee857",
      "aggregateVersion": "1",
      "occurredAt": "2026-10-30T02:29:17.739Z",
      "correlationId": "566b8280-0f29-4e4d-8706-5505852ee857",
      "causationId": null,
      "traceparent": null,
      "data": {
        "orderId": "566b8280-0f29-4e4d-8706-5505852ee857",
        "chargeId": "72cf66d9-6b1b-4f85-aa07-4624fef06084",
        "issuanceId": "cd927efd-b2a4-4334-ba62-36e24212df22",
        "ticketCount": 2
      }
    },
    "brokerResult": {
      "routed": true
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "d915b4b3-ba35-4005-a55b-f77cebd66ec0",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "7727cc7f-5f6f-4e1c-9622-1052f085beab"
      }
    ],
    "saga": {
      "state": "COMPENSATING",
      "chargeId": "72cf66d9-6b1b-4f85-aa07-4624fef06084"
    },
    "chargeCount": 1,
    "confirmationCount": 1,
    "refunds": [
      {
        "state": "PENDING",
        "reason": "EVENT_CANCELLED",
        "amount": "180000"
      }
    ],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      }
    ]
  }
}
```

## S34 — Chưa nhận kết quả Ticket: chưa tự refund hoặc hoàn tất

**Đạt điều kiện kiểm tra.** Dữ liệu thực nhận:

```json
{
  "observationWindow": "1 giây sau khi lệnh IssueTickets đã publish, không gửi Ticket result",
  "http": {
    "request": {
      "method": "GET",
      "path": "/api/v1/orders/83d5b519-0c29-419e-ab7e-19cdfa0db9a8/payment",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "[test JWT đã che]"
      }
    },
    "response": {
      "status": 200,
      "body": {
        "data": {
          "orderId": "83d5b519-0c29-419e-ab7e-19cdfa0db9a8",
          "acceptedChargeId": "04ab5808-1463-4d03-800a-4a106d214497",
          "latestAttempt": {
            "orderId": "83d5b519-0c29-419e-ab7e-19cdfa0db9a8",
            "attemptId": "634e4bc5-04f7-4345-a860-c218231b2b9e",
            "attemptState": "SUCCEEDED",
            "amount": "180000",
            "currency": "VND",
            "paymentUrl": null,
            "expiresAt": "2026-10-30T02:39:18.389Z"
          },
          "startRequest": null,
          "refunds": []
        },
        "success": true,
        "message": "Lấy thông tin thanh toán thành công"
      }
    }
  },
  "database": {
    "attempts": [
      {
        "attemptId": "634e4bc5-04f7-4345-a860-c218231b2b9e",
        "state": "SUCCEEDED",
        "amount": "180000",
        "requestKey": "164a5ee6-b517-449e-840a-2218c2fcbee6"
      }
    ],
    "saga": {
      "state": "WAITING_TICKETS",
      "chargeId": "04ab5808-1463-4d03-800a-4a106d214497"
    },
    "chargeCount": 1,
    "confirmationCount": 1,
    "refunds": [],
    "outbox": [
      {
        "messageType": "booking.accept-payment.v1",
        "published": true
      },
      {
        "messageType": "ticket.issue.v1",
        "published": true
      }
    ]
  }
}
```
