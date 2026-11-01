# B14 — Thứ tự giao dịch, thử lại và hội tụ luồng chính

- Phiên bản: B14-v0.3; ngày 2026-10-29; trạng thái: REVIEW_READY; phân lớp FORMATION.
- Người duyệt/ngày duyệt bản này: chưa có. B14-v0.1 được Lê Văn Minh duyệt 2026-09-14 (GOV-144), sau B13-v0.1. Nguồn: B11-C-v0.3 APPROVED; B6-v0.15/B7-v0.13/B8-v0.13 APPROVED; B12-v0.2 APPROVED; B13-v0.3 REVIEW_READY; GOV-156 hiện hành, GOV-131–143, GOV-149/150 và PRJ-024 theo phần còn hiệu lực. Bản này không được APPROVED trước B13-v0.3.
- Chỉ triển khai chi tiết trong năm owner và một Saga Payment đã chọn. Hủy event là phản ứng một chiều có retry. Đây là thiết kế, chưa phải kết quả chạy dịch vụ.

Hiện hành theo GOV-156 và [ảnh ClickUp 61 bước](../evidence/use-case-diagrams/2026-10-29-clickup-booking-flow.md): FE → Payment → Booking lấy snapshot; Ticket → Payment → Booking commit COMPLETED → Payment hoàn tất Saga. Thay thế hướng start/result của GOV-149/150; bằng chứng 03/10 là lịch sử ảnh khác. Ảnh happy path không bãi bỏ nhánh lỗi/bù trừ.

## 1. Ranh giới nguyên tử và thứ tự khóa

Mỗi hàng dưới là một transaction **trong một database**. Không mở transaction rồi gọi HTTP/cổng/SMTP và giữ khóa trong khi chờ mạng.

| Owner | Thứ tự và phạm vi |
|---|---|
| Event | Khóa event → cấu hình con theo ID tăng dần → lưu/xóa nháp → outbox; submit/cancel lấy cùng khóa event |
| Booking | Khóa event_sales_snapshots → orders nếu có → purchase_limits → pool/seat theo ID tăng dần → promotion inventory/usage → allocations → outbox/inbox. Tạo mới khóa cặp idempotency trước tài nguyên; mọi handler dùng cùng thứ tự |
| Payment | Khóa event_finance_snapshots khi đã biết event → order_payment_snapshots → saga/attempt/charge → refund; ghi outbox/inbox cùng transaction. Một event row có thể là điểm nóng, phải đo rồi tối ưu, không bỏ guard để giảm contention |
| Ticket | Khóa event_access_snapshots → issuance → ticket; check-in và cancel tuân cùng thứ tự. Delivery gọi SMTP ngoài transaction |

Booking dùng thời điểm `clock_timestamp()` **sau khi lấy khóa cần phân xử**, không dùng `now()` khởi đầu transaction hoặc thời gian callback ở cổng. Deadline là `decisionAt < expires_at`; bằng hạn là hết hạn. Check-in dùng cả hai biên `starts_at <= decisionAt <= ends_at` theo BIZ-067, khác quy tắc deadline.

Khóa snapshot event độc quyền là lựa chọn đơn giản ban đầu, có thể giới hạn throughput các đơn cùng event. Khi hiện thực nên dùng khóa chia sẻ cho đường chỉ đọc cấu hình, độc quyền khi cập nhật/hủy, và khóa riêng các tài nguyên tranh chấp; phải giữ thứ tự trên và chạy race tests. Đây là tối ưu kỹ thuật có kiểm chứng, không thay invariant.

## 2. Tạo đơn, áp mã và đóng băng tiền

1. Gateway/resource server xác thực role và subject. Booking validate selection, email và Idempotency-Key; không nhận giá do FE tính.
2. Transaction Booking: đăng ký khóa `(actor,operation,key)`; nếu bản thành công đã có thì so request_hash và trả lại **cùng order**, không giữ thêm. Hai request đồng thời cùng key được serialize bằng khóa unique; request thua rollback và đọc kết quả đã commit.
3. Đọc cấu hình cục bộ đủ version, kiểm sale window/status. Kiểm mọi item/seat cùng event/sector/type. Upsert purchase_limits bằng unique pair rồi khóa; kiểm held+purchased+quantity <= limit.
4. QUANTITY giữ pool type; STANDING giữ một pool sector chung; SEATED giữ từng seat có type, không hidden và AVAILABLE. Tạo order/items/reservation/allocations, cập nhật tất cả counter và idempotency row cùng transaction. Lỗi bất kỳ rollback trọn vẹn.
5. Áp mã là transaction riêng: khóa order, kiểm chủ đơn/còn giữ chỗ/chưa freeze/chưa áp mã; giữ usage cùng expiry, tính giảm HALF_UP một lần trên subtotal và cập nhật tiền cùng commit; final total phải >0. Không gia hạn đơn. Theo GOV-157, dùng cùng khóa order với bước chuẩn bị snapshot để chặn đua sửa tiền.
6. Theo ảnh 15–21 và chi tiết bổ sung GOV-157: Frontend POST thanh toán với orderId trực tiếp tới Payment. Payment POST nội bộ Booking để chuẩn bị PurchaseSnapshot trong một lần gọi: Booking khóa/kiểm đơn, cố định tiền và lưu snapshot/mốc/version cùng transaction; commit rồi trả snapshot. Retry trên đơn còn payable dùng snapshot đã lưu, không tính lại. Không có API freeze riêng, GET có write, Booking nhận start công khai hoặc message start-requested. Lời gọi HTTP tới Booking nằm ngoài transaction Payment.
7. Theo ảnh 22–26: Payment lưu PurchaseSnapshot và PaymentAttempt trong cùng transaction, commit rồi trả dữ liệu QR/URL cho FE hiển thị. Cùng Idempotency-Key/input trả cùng attempt; không mở hai attempt chưa có kết quả cho cùng order. Giá lấy từ Booking, không từ FE. Cách gọi adapter cổng để có QR/URL không được ảnh mô tả; gọi cổng ngoài transaction, timeout giữ UNKNOWN/query cùng provider identity thay vì tạo attempt mới mù.
8. FE hiển thị dữ liệu thanh toán trả về từ Payment rồi user thanh toán tại cổng (ảnh 26–27). Poll/read Payment chỉ là khả năng phục hồi bổ trợ, không phải luồng thay cho response POST trong ảnh. Định dạng QR/URL còn B13-OPEN-01; cơ chế gộp chuẩn bị snapshot/freeze đã được user duyệt ở GOV-157, không suy từ ảnh đơn thuần.

Crash sau lưu attempt trước gọi cổng cần phục hồi cùng attempt/provider identity, query trước retry khi chưa rõ tác động. Không cần inbox command khởi tạo từ Booking trong flow này. Yêu cầu kiểm V09/V21, chưa phải bằng chứng worker chạy thật.

Mất response tạo thanh toán hoặc tạo đơn không đồng nghĩa thất bại. Retry cùng khóa để lấy kết quả cũ. Không TTL receipt rồi cho cùng khóa tạo order/attempt khác trong thời hạn lưu giao dịch.

## 3. Thu tiền → chấp nhận giữ chỗ → phát vé

Sơ đồ: [B14-01](../diagrams/src/B14-01-order-payment-ticket-sequence.puml).

| Bước | Transaction / tác động | Khi lặp hoặc lỗi |
|---|---|---|
| P1 | Payment verify IPN/query: chữ ký, merchant, attempt, order, amount/currency, kết quả. Ghi charge bằng provider identity duy nhất | Cùng charge không ghi lần hai. Q-07 sai/liên kết thiếu bị từ chối, không có workflow riêng |
| P2 | Chọn một candidate charge/order trong payment_sagas WAITING_BOOKING + outbox booking.accept-payment | Charge khác của cùng order là thu thừa, hoàn riêng; không đổi candidate đang phân xử |
| B1 | Booking inbox + khóa order; trước hết kiểm accepted_charge_id. Cùng charge đã nhận trả ACCEPTED; khác charge đã nhận trả DUPLICATE | So kết quả cũ trước deadline tránh đổi ACCEPTED thành LATE khi message lặp trễ |
| B2 | Nếu chưa nhận charge, xét event cancellation/state và deadline. Còn hợp lệ: HELD→COMMITTED, held→purchased/used, order→ISSUING, accepted charge/time; response outbox cùng commit | Hết hạn/hủy không hồi sinh. Nếu worker chưa chạy, xử lý expiry/release đúng một lần trong transaction; trả LATE/CANCELLED |
| P3 | Payment nhận ACCEPTED: tạo confirmation unique(order,charge) rồi saga WAITING_TICKETS và outbox ticket.issue | Không tạo confirmation trước quyết định Booking. Nếu event đã có marker hủy, chuyển hoàn/hủy hội tụ, không phát mới |
| T1 | Ticket kiểm source Payment và purchase; khóa snapshot/issuance. Cùng order khác hash từ chối. Cùng input COMPLETED trả cùng tập, FAILED không phát lại | Thiếu config retry; cancellation marker chặn tạo vé hữu hiệu |
| T2 | Tạo đủ vé với ordinal duy nhất, kiểm số vé/item; issuance→COMPLETED + outbox result tới Payment + delivery job cùng transaction | Lỗi giữa chừng rollback; không lộ vé một phần; SMTP ngoài transaction. Payment nhận result là ACK bước 46, không phải Order đã COMPLETED |
| B3 | Payment sau result thành công gửi ticket.issued.v1 tới booking.tickets-issued.q của Booking (ảnh 47). Booking kiểm charge/version/số vé; không lưu/so issuanceId trước đó, chỉ truyền vào xác nhận. Ghi ISSUING→COMPLETED + inbox + outbox OrderCompleted cùng commit; FAILED đi queue booking.issuance-failed.q riêng | Booking commit bước 49 rồi mới xác nhận bước 51; replay không cập nhật hai lần, CANCELLED không hồi sinh |
| P4 | Payment nhận xác nhận Booking đã hoàn tất, kiểm đúng order/charge/issuance rồi Saga→COMPLETED + inbox cùng transaction (52–54) | Không hoàn tất Saga chỉ vì Ticket ACK; mất message/xác nhận thì retry idempotent, không phát thêm vé |

Payment chưa thấy Booking acceptance thì giữ WAITING_BOOKING; chưa có xác nhận order COMPLETED thì giữ WAITING_TICKETS. Result Ticket → Payment → Booking và xác nhận Booking → Payment phải giao lại bền khi crash/mất response. Payment chỉ probe Ticket không đủ để kết thúc Saga, phải hội tụ trạng thái Booking trước. Timeout không phải bằng chứng phát hành thất bại; không tự hoàn tiền chỉ vì chậm.

Thất bại dứt điểm: Ticket transaction khóa issuance, bảo đảm không có bộ COMPLETED, ghi FAILED/failed_at/failure_code và outbox FAILED. Worker phát vé phải kiểm cùng terminal fence trước commit; vì thế lệnh cũ không thể phát thành công sau FAILED. Chỉ kết quả này cho Payment tạo refund TICKET_ISSUANCE_FAILED và outbox Booking thất bại. Không “sau N retry thì hoàn” nếu chưa có fence.

Booking nhận FAILED: nếu đã hủy event giữ CANCELLED; nếu ISSUING thì ISSUANCE_FAILED. Theo xác nhận user ngày 29/10/2026 “cứ đưa về. còn khi đặt thì check event rồi nên ko lo”, trả promotion, inventory đã chốt và purchase limit đúng một lần kể cả event hết giờ bán hoặc CANCELLED; createOrder vẫn kiểm eventStatus/sale window trước khi nhận đơn. Dùng trạng thái allocations/usage và quyền giữ reservation của ghế để không trả hai lần hoặc sửa tài nguyên của reservation mới. Quyết định này chỉ áp dụng nhánh FAILED dứt điểm, không thay fan-out hủy ở §5; Order COMPLETED không bị hạ trạng thái bởi FAILED mâu thuẫn. Refund hoàn tất không tự thay order hoặc ticket state. Chi tiết: [spec issuance-result v0.4](../java-workflow/specs/2026-10-29-booking-issuance-result-design.md), gate EXECUTING-PLANS đã được user xác nhận, kết quả chạy ghi trong plan.

## 4. Hết hạn, buyer hủy và hoàn tiền

Sơ đồ: [B14-02](../diagrams/src/B14-02-expiry-refund-cancellation-sequence.puml).

- Worker tìm đơn PENDING_PAYMENT đến hạn theo index, xử lý từng đơn trong transaction ngắn. Batch size/frequency là cấu hình, không phải mốc quyết định hết hạn.
- Buyer cancel là transaction cục bộ Booking: xác thực chủ đơn → khóa/kiểm PENDING_PAYMENT còn giữ chỗ → CANCELLED + release reservation/allocations, hạn mức và promotion đúng một lần → commit rồi trả thành công. Vi phạm điều kiện rollback, không release một phần. ISSUING/COMPLETED từ chối buyer cancel; payment_frozen_at không tự cấm hủy khi đơn vẫn đủ điều kiện. Hình hủy đơn bước 1–11 khớp rule này. Khoản thu tới sau hủy vẫn đi hoàn muộn, không gọi Payment như điều kiện để commit hủy.
- Confirm và expire/cancel cùng khóa order/tài nguyên. Bên commit trước quyết định; confirm vẫn xét đồng hồ sau khóa. Không hồi sinh EXPIRED/CANCELLED để cấp ghế đã trả cho buyer khác.
- Giải phóng chỉ khi đúng reservation ID + generation + expected state. R1 đã trả, R2 giữ lại ghế: message release R1 không được đụng R2.
- Refund unique(charge_id). Phân xử nguyên nhân: nếu charge khác confirmation thì DUPLICATE_PAYMENT; nếu đúng charge bị hủy event thì EVENT_CANCELLED; lỗi phát hành dứt điểm thì TICKET_ISSUANCE_FAILED; charge không được Booking nhận vì hạn/buyer hủy thì LATE_PAYMENT. Giữ reason đã lưu khi refund tồn tại, các trigger sau chỉ tham chiếu cùng nghĩa vụ; không cần nhiều refund cho nhiều nguyên nhân.
- Transaction claim refund chuyển PROCESSING và ghi refund_attempt request_key trước gọi gateway. Chỉ một unfinished attempt/refund. Gọi ngoài transaction; query cùng key khi timeout. Không gửi refund mới khi chưa rõ kết quả cũ.
- Bằng chứng provider thành công: SUCCEEDED + completed_at, đúng toàn bộ amount charge. Thất bại đã xác định: FAILED, còn nghĩa vụ và retry trên refund hiện có. Retry mạng có thể nhiều lần nhưng không tăng tổng tiền hoàn.
- Saga COMPENSATED sau refund SUCCEEDED và lệnh Booking đã nằm bền trong outbox; delivery/consumer lag vẫn phải theo dõi. Không coi trạng thái coordinator là bằng chứng counter ở mọi nơi đã hội tụ.

## 5. Hủy sự kiện: cổng cục bộ trước, fan-out sau

1. Event kiểm actor/owner và `decisionAt < starts_at`, chốt CANCELLED + cancellationId/outbox trong cùng transaction. Yêu cầu bị từ chối giữ event nguyên, không phát cancellation. Yêu cầu mới sau REJECTED có ID mới; partial unique chặn hai PENDING.
2. B13 EventCancellation mang full sales/access snapshot CANCELLED cùng version. Booking/Ticket nhận trước config cũ vẫn ghi được cổng chặn; config cũ không hồi sinh. Payment có cancellation marker độc lập với fee config trong event_finance_snapshots, có thể tạo trước khi nhận phí.
3. Mỗi owner commit inbox + cổng hủy trước fan-out. Quét bền theo trạng thái các đơn/issuance còn hoạt động; batch retry có thể quét lại từ đầu phần chưa xử lý. Không dùng cursor trong RAM làm bằng chứng tất cả đã xong.
4. Booking hủy PENDING_PAYMENT/ISSUING/COMPLETED, trả hạn mức/promotion đúng nguyên nhân, không mở nguồn cung. Ticket vô hiệu VALID và chặn check-in ngay bằng snapshot, kể cả batch chưa void hết. Không thực hiện nghiệp vụ hoàn tác USED.
5. Payment tạo event_refund_run duy nhất/cancellation, chọn đơn có confirmation còn nghĩa vụ; mỗi order một item. Chỉ selection_complete khi đã quét hết. Mọi acceptance/charge mới tới sau marker phải tự đi nhánh hoàn và cập nhật item phù hợp, không dựa vào một lượt quét để bắt hết giao dịch đến trễ.
6. Charge thừa xử lý riêng, không tăng mẫu số run. Run completed phải kiểm còn item/refund pending; kết quả thu tới trễ sau đó vẫn được xử lý, không lấy completed làm quyền bỏ callback. UI ghi asOf và “đã quét xong”, không tuyên bố đã biết mọi khoản trên cổng.

GOV-134/135 chấp nhận giao dịch lọt trong độ trễ. Check-in có thể lọt trước khi Ticket nhận marker nếu thông điệp chậm tới sau start; hệ thống không tự undo USED. Phải công bố giới hạn của nhất quán bất đồng bộ này, không âm thầm hứa “không thể có scan lọt” hoặc thêm luồng hi hữu ngoài phạm vi.

## 6. Delivery, tải QR và check-in

Sơ đồ: [B14-03](../diagrams/src/B14-03-ticket-delivery-checkin-sequence.puml).

Delivery là job trong Ticket: requested_by, recipient email frozen, request_key/hash và retry schedule. Worker claim bằng khóa hàng/lease, gửi SMTP ngoài transaction rồi lưu outcome. Mất response SMTP có thể gửi trùng email, không tạo thêm ticket/refund. API gửi lại cùng key trả cùng job; key mới là yêu cầu gửi lại mới. Buyer vẫn xem/tải trong tài khoản khi email lỗi.

Check-in: xác thực actor, lookup token qua hash; khóa snapshot/issuance/ticket, kiểm owner/window/event/issuance COMPLETED/ticket VALID, UPDATE USED và audit cùng commit. Mọi HTTP receipt có audit ID riêng; retry cùng Idempotency-Key tra bản gốc, so hash, ghi replay_of rồi trả kết quả gốc, không thêm một thành công. Scan mới cùng vé trả ALREADY_USED. Cùng ID khác payload không rò kết quả của người khác. Audit không chứa QR/JWT thô.

Không nhầm bản ghi dùng token hash để lookup với bản mã QR để tải lại. Khóa QR ngoài DB phải sống qua restart/restore; thiếu khóa trả lỗi, không phát token mới thay thế âm thầm.

### 6.1 Xem đơn và vé — UC-28

Theo hình `UseCase Xem Đơn Đặt/Sequence.png` bước 1–21: frontend đọc danh sách đơn của buyer hiện tại từ Booking, chọn một đơn rồi đọc chi tiết từ Booking, sau đó đọc vé theo orderId trực tiếp từ Ticket qua API công khai/gateway. Đây là ba truy vấn chỉ đọc; không buộc Booking JOIN/gọi Ticket để trả chi tiết đơn. Booking/Ticket đều kiểm ownership phía backend; biết orderId chưa phải quyền đọc. Ticket chưa phát đủ thì trả PENDING và mảng rỗng theo B13, không tạo vé qua GET. Sau phát hành có thể xem vé dù Payment còn chờ message completion; trạng thái đơn và Saga vẫn hội tụ theo §3.

Tên GetOrdersByUserID/GetOrderDetailByID/GetTicketsByOrderID trong ảnh mô tả truy vấn cục bộ, không cho phép frontend gửi userId tùy ý hoặc đọc database. Route danh sách dự kiến `GET /api/v1/orders` với owner filtering và phân trang theo B13 §9; chi tiết/vé theo B13 §3.

## 7. Outbox/inbox và phục hồi

Relay chọn unpublished theo next_attempt_at; row lock SKIP LOCKED giúp chia việc. Broker publisher confirm rồi mới published_at. Crash giữa hai bước làm redelivery, được inbox/business key hấp thụ. Consumer ACK chỉ sau transaction commit. Retry cùng logical command dùng cùng message ID/data; duplicate message với hash khác là lỗi hợp đồng, không xử lý một phần.

Không bỏ message sau một số retry để cho bảng trông sạch. Backoff có trần, last_error_code đã lọc; lỗi cấu trúc chuyển hàng đợi lỗi kỹ thuật để sửa/replay có kiểm soát. Đây không phải workflow tiền Q-07. Trước code cần hiện thực worker, giao dịch và broker confirms theo bảng; **có bảng outbox chưa phải có outbox chạy đúng**.

## 8. Đối soát, tính phí và điểm kiểm bắt buộc

Payment tính từ confirmation và refund của charge đó; không tổng tất cả attempts/charges. FeeRate lấy source_fee_version đã duyệt, không default zero khi thiếu. Khóa dữ liệu tài chính event khi tính/đánh paid; không còn attempt UNKNOWN/PENDING/refund chưa xong, event đã kết thúc, đối soát thủ công có actor/time/evidence. Giảm/fee dùng quy ước HALF_UP đã chốt. Paid một lần, không tự chuyển tiền ngân hàng.

Do không kiểm soát thời điểm mọi callback ngoài tới và không có HA, đối soát với báo cáo cổng vẫn là bước thật. Sai lệch sau paid nằm ngoài vòng đời đã chọn; không hứa SQL CHECK chứng minh “cổng không còn khoản nào”.

Ma trận phép thử và kết quả ở B12-validation/B15: đảo thứ tự, lặp, crash trước/sau commit, deadline đồng thời, thất bại phát hành có fence, email lỗi và scan lặp. Chưa thực thi các transaction ứng dụng trên service thật. Vật liệu báo cáo là ranh giới nguyên tử, lý do ISSUING, tradeoff snapshot/hủy trễ và bằng chứng chạy thật, không phải lịch sử refactor.

## Phụ lục đối chiếu R0 và phép thử độc lập — 2026-09-14

Qua cửa project/lien-ket-rca.md, R0-v0.5 §3 mục 10 là CANDIDATE: sequence mô tả ranh giới HTTP/message/transaction; chưa vẽ ranh giới span đầy đủ. OPEN thuộc bước instrumentation B16: đội hiện thực đối chiếu span thực và độ phủ từng bước trước thu dữ liệu RCA; thiếu phần này làm đồ thị quan sát thiếu chi tiết. Không chặn việc code luồng chính, không ghi đã giữ trọn mục 10. B14 thuộc bộ hệ thống; nguồn thiết kế là đầu vào header và quyết định trong thân bài. Nguồn RCA chỉ ở phụ lục này, không sinh Saga, service hoặc bất biến.
