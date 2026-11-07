# Test đặt vé → thanh toán → phát hành vé

Import `booking-payment-ticket.postman_collection.json` và, nếu cần tách cấu hình,
`booking-payment-ticket.postman_environment.json`. Collection có 22 request.
Không có password, client secret, JWT thật hoặc VNPay hash secret trong hai file.

Đã import collection và environment vào workspace FlashTicket:
[mở collection trong Postman](https://go.postman.co/collection/37889324-ad00eb16-b251-416c-b777-d154ae8a7b54).

## Chuẩn bị

- Chạy Booking, Payment, Ticket và các dependency theo cấu hình `.env` của dự án.
  URL mặc định là localhost:8082/8083/8084; sửa tại collection Variables hoặc
  environment template nếu dùng server/tunnel/Gateway. Callback VNPay phải tới
  đúng Payment đang dùng, cùng merchant và môi trường sandbox.
- Có event đang mở bán, snapshot inventory đã đồng bộ và BUYER có hạn mức còn lại.
  Collection không tạo event hoặc bơm message cấu hình thay Event service.
- Điền `eventId`, `customerEmail` và lựa chọn vé bên dưới. Các ID phải thuộc cùng
  event; không lấy ID tùy ý chỉ vì Availability hiển thị còn vé.
- Trong `A01 - Login BUYER`, chọn Authorization → Get New Access Token → Use Token
  → Send. Dùng OAuth2 PKCE của client `frontend`, như collection hiện có.
  Có thể điền token BUYER còn hạn vào **collection** variable `userAccessToken`.
  A01 kiểm UserInfo, issuer, subject, hạn token và role BUYER.

| Kiểu chọn | Biến cần điền |
| --- | --- |
| QUANTITY | `selectionMode=QUANTITY`, `ticketTypeId`, `quantity` |
| Vé đứng | `selectionMode=STANDING`, `sectorId`, `ticketTypeId`, `quantity` |
| Ghế ngồi | `selectionMode=SEATED`, `sectorId`, `seatIdsJson=["<seatId1>","<seatId2>"]` |
| Nhiều loại vé | Điền `selectionJson` theo contract bên dưới; biến này được ưu tiên hơn `selectionMode` |

```json
{
  "mode": "SEAT_MAP",
  "kind": "STANDING",
  "sectorId": "<sectorId>",
  "items": [
    {"ticketTypeId": "<ticketTypeId1>", "quantity": 1},
    {"ticketTypeId": "<ticketTypeId2>", "quantity": 2}
  ]
}
```

`promotionCode` là tùy chọn; để trống thì skip B04. Không gửi subtotal, discount,
total hoặc buyerSubject trong body tạo đơn. Buyer lấy từ JWT, giá lấy từ Booking.
Tiền là chuỗi nguyên VND; assertions dùng BigInt để tránh sai số số tiền lớn.

## Chạy luồng chính

1. Run collection từ đầu. A01 xác nhận token; B01 đọc tồn; B02 tạo order/hold;
   B03 gửi lại cùng key/body; B04 áp voucher nếu có; P01 tạo thanh toán và P02 replay.
   Mặc định `stopAfterPaymentUrl=true` nên Runner dừng ở P02, với
   `flowStatus=PAYMENT_URL_READY`. Đây là hoàn tất chuẩn bị thanh toán.
2. Mở `paymentUrl` trong collection Variables hoặc tab Visualize của P01/P02,
   hoàn tất thanh toán tại VNPay sandbox trước `orderExpiresAt`.
   Collection không tự xác nhận thu tiền, ký IPN giả hoặc gửi yêu cầu tới trang
   thanh toán thay người dùng. Cơ chế IPN/Return tham khảo
   [tài liệu VNPay](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
3. Run riêng folder **03 - Verify payment and tickets**. V01 đợi attempt SUCCEEDED
   và acceptedChargeId; V02 đợi issuance COMPLETED và đủ bộ vé; V03 đọc lại để so
   ID/nội dung bộ vé; V04 đợi Booking COMPLETED. Chỉ sau đó ghi
   `flowStatus=E2E_COMPLETE`. Bộ vé phải đúng event, từng orderItem, số lượng,
   ordinal, seatLabel và state VALID.

Muốn Runner đi liên tục sang phần verify, đặt `stopAfterPaymentUrl=false` và
thanh toán trong lúc Runner chờ. `pollIntervalMs=2000`, `maxPollAttempts=30`
áp dụng riêng cho mỗi bước poll. Hết lượt chờ là assertion fail và dừng luồng,
không tự refund hoặc đánh dấu phát hành FAILED. Chạy lại request poll sau khi
hết lượt sẽ bắt đầu lại bộ đếm; key/order/attempt vẫn giữ nguyên.

Polling chỉ lặp khi dùng Runner/CLI; nút Send đọc một lần.
[Postman quy định setNextRequest chỉ tác động trong collection run](https://learning.postman.com/docs/tests-and-scripts/running-collections/building-workflows/).

## Phần backend còn thiếu

Đối chiếu source ngày 02/11/2026, không phải xác nhận các service đã deploy:

| Bước | Route | Trạng thái trong checkout |
| --- | --- | --- |
| B01 | GET `/api/v1/events/{eventId}/availability` | Có controller/service |
| B02–B04 | POST `/api/v1/orders`, `/{id}/promotion` | Có controller/service |
| P01/P02 | POST `/api/v1/payments` | Có implementation; response `ApiResponse<PaymentStart>` |
| V01 | GET `/api/v1/orders/{id}/payment` | Có implementation; response `ApiResponse<PaymentView>` |
| V02/V03 | GET `/api/v1/orders/{id}/tickets` | Contract B13; Ticket controller/service/issuance hiện là khung |
| V04 | GET `/api/v1/orders/{id}` | Contract B13; OrderController hiện chưa có GET |

V02–V04 được gắn `[CONTRACT]`: kỳ vọng HTTP 200 theo thiết kế và **fail** khi
404/501, không chấp nhận endpoint thiếu như một test pass. Ticket chưa có logic
phát hành nên luồng thật cũng có thể dừng ở V01 chờ Booking acceptance/Ticket,
tùy runtime và các producer/consumer đang chạy.

Route thanh toán trong collection theo code thực tế `/api/v1/payments`;
`/orders/{id}/payment-attempts` ở B13 hiện là CANDIDATE, không được dùng thay thế.
Booking trả OrderView trực tiếp; Payment bọc dữ liệu trong `data` và `success`.
HTTP IPN 200 phải đọc thêm RspCode. Return 302 chỉ điều hướng, không chứng minh
đã thu tiền. acceptedChargeId cũng chưa chứng minh vé đã phát hoặc Saga đã đóng.
Hiện API không công bố trạng thái Saga Payment; muốn nghiệm thu P4 phải kiểm thêm
DB/log/trace đúng order/charge/issuance theo B14.

## Các nhóm chạy riêng

- **04 - Negative checks**: sau B02/P01, kiểm thiếu JWT, thiếu idempotency key,
  cùng order key nhưng đổi email, IPN không chữ ký trả RspCode=97, Return không
  chữ ký trả 302/status=FAILED. N07 tắt follow redirects để đọc đúng HTTP 302.
- **05 - Cancel disposable hold**: tạo order phụ C01 rồi C02/C03 hủy và replay hủy.
  Dùng key/order cleanup riêng. Với SEATED, điền `cleanupSelectionJson` chọn ghế
  khác còn trống; hoặc chọn một QUANTITY/STANDING fixture riêng. Không dùng order
  đã thanh toán làm dữ liệu cleanup.
- **06 - New scenario**: Send R01 riêng để xóa biến runtime, giữ token. R01 không
  gửi HTTP, không tự hủy đơn hoặc hoàn tiền. Sau đó chạy lại từ B01/B02 để tạo
  key/body/hold mới. Chỉnh selection khi đang giữ key cũ không làm đổi body replay.

Không chọn các nhóm 04–06 trong lượt luồng chính. P02 và V04 chủ động dừng run
để tránh chạy nhầm nhóm phụ. Các kiểm tra HTTP/replay không thay thế bài kiểm
race, counter DB, message duplicate, crash/recovery hoặc refund.

Cấu hình đầu vào có thể đặt ở environment. ID, key, token và dữ liệu nối bước
được đọc/ghi tại collection Variables; script đặt local runtime từ collection
để environment cũ không ghi đè nhầm. Không lưu bản export có token thật vào Git.

## Nguồn đối chiếu

- `services/booking-service/.../order/controller/OrderController.java`:
  create/cancel/promotion, header Idempotency-Key và HTTP 201.
- `services/booking-service/.../dto/request/OrderSelection.java`,
  `dto/response/OrderView.java`: các dạng selection và response.
- `services/payment-service/.../controller/PaymentController.java`,
  `service/PaymentService.java`, `dto/response/ApiResponse.java`,
  `util/VNPayUtil.java`: route thật, response envelope, IPN/Return, TxnRef và amount.
- `services/ticket-service/.../ticket/controller/TicketController.java`,
  `issuance/service/impl/IssuanceServiceImpl.java`, `dto/response/TicketView.java`:
  trạng thái khung hiện tại và kiểu dữ liệu vé.
- `docs/architecture/B13-api-and-event-contracts.md` §3 và
  `B14-main-flow-sequences.md` §2–3: Ticket → Payment → Booking COMPLETED → Payment.

Các đường dẫn `...` phía trên bắt đầu từ `src/main/java/com/flashticket/<service_package>`.
Collection này là tạo tác kiểm thử, không cập nhật contract B13/B14 hoặc code dịch vụ.

## Kiểm chứng collection ngày 02/11/2026

Đã chạy bằng Postman CLI 1.73.0 với HTTP fixture server riêng trên 127.0.0.1:

| Kịch bản fixture | Kết quả |
| --- | --- |
| QUANTITY, gồm polling, negative checks và cleanup | 56 assertions pass, exit 0 |
| STANDING, gồm voucher và các nhóm trên | 58 assertions pass, exit 0 |
| SEATED hai ghế, cleanup dùng selection riêng | 56 assertions pass, exit 0 |
| Ticket endpoint trả 404 | Assertion fail, exit 1; không chạy bước order COMPLETED |
| Payment cứ PENDING tới giới hạn poll | Assertion fail, exit 1; không chạy bước Ticket |
| Payment trả sai amount | Assertion fail, exit 1; không chạy bước Ticket |
| Lần đọc vé sau thay ticket ID | Assertion fail, exit 1; không chạy bước order COMPLETED |
| Thiếu eventId | Fail trước khi gửi request ghi dữ liệu, exit 1 |

Đây là bằng chứng scripts, truyền biến và rẽ nhánh của collection. Không phải
giao dịch VNPay sandbox thật, kiểm chứng producer/consumer hay E2E các backend.
Sau import đã đọc lại qua Postman API: đủ 22 request, script cấp collection,
OAuth2 PKCE của A01 và pre-request/assertions của P01 được giữ nguyên.
