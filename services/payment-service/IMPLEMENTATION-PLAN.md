# Kế hoạch Payment Service — từ chạy được đến thanh toán hoàn chỉnh

Cập nhật 22/10/2026. Kế hoạch này thay bản trước, ưu tiên từng bước nhỏ cho người bắt đầu. Đây là kế hoạch đề xuất; chưa thực hiện các bước hoặc xác nhận runtime trong lượt lập kế hoạch.

## Điểm xuất phát

- Payment mới có bộ khung Spring Boot, dependency và cấu hình, chưa có nghiệp vụ.
- Config Server đã có ứng dụng native đọc classpath:/config/ và payment-service.yaml. Việc đầu tiên là kiểm tra nó chạy và cấp được cấu hình, không viết lại server từ đầu.
- Repository đã có DDL Payment và tài liệu nói local có 13 bảng. Cần kiểm metadata database đang dùng để biết trạng thái thật.
- Chọn hướng schema trước, entity sau: SQL/migration quản lý bảng; entity ánh xạ Java vào bảng. Không dùng entity để sinh lại database đã có.
- Local theo tài liệu là payment_db, schema payment_schema, bảng tbl_*. DDL gốc chưa có tiền tố; initializer áp script đổi tên sau DDL.
- Test context hiện tắt DataSource/JPA/RabbitMQ. Test đó PASS chưa chứng minh kết nối thật.

## Cách làm

Mỗi lần chỉ copy prompt của một bước. Mỗi prompt yêu cầu đọc phần Quy tắc chung bên dưới nên dùng được trong chat mới. Làm bước 1–7 trước để có nền database và API đọc; bước 8–15 thực hiện khi nền đó ổn. Không cần chờ Booking/Ticket hoàn thiện: dùng test double theo contract, nhưng chỉ công nhận E2E khi tích hợp thật.

Sau mỗi bước, người thực hiện giải thích: đã thêm gì, vì sao cần, bạn chạy/kiểm thế nào, kết quả thực tế và phần chưa chạy. Một test bị chặn vì môi trường phải ghi NOT_RUN, không thay bằng kết quả mock rồi gọi là đã kiểm thật.

## Quy tắc chung cho mọi prompt

1. Đọc AGENTS.md áp dụng; dùng CodeGraph trước tìm/đọc code của repository có index. Kiểm thay đổi có sẵn và giữ công việc của người dùng. Chỉ thực hiện bước được yêu cầu; không tự chạy bước tiếp theo, commit/push/deploy.
2. Giữ stack hiện có. Bước đầu chỉ sửa Payment và cấu hình Config Server trực tiếp liên quan; không viết nghiệp vụ của service khác.
3. Nguồn schema: docs/architecture/data/postgres/30-payment.sql, 00-bootstrap.sql, 90-grants.sql, services/99-table-prefix.sql, services/payment-service/database/README.md và services/LOCAL-DATABASES.md.
4. Nguồn nghiệp vụ: docs/architecture/B13-api-and-event-contracts.md, B14-main-flow-sequences.md, B15-verification-plan.md, B16-observability-baseline.md; đối soát thêm B6/B8/B12 ở bước tài chính.
5. B13/B14/B15 v0.2 đang REVIEW_READY. Luồng start qua Booking và success qua Booking đã được xác nhận, các DTO/route/message sửa là candidate kỹ thuật để review tích hợp. Không tự đổi trạng thái phê duyệt; ghi các điểm chưa thống nhất khi tới bước liên quan. Không tự thêm QR payment hoặc đổi schema để giải quyết B13-OPEN-01.
6. Không xóa volume, drop database, chạy initializer lại trên database đã có hoặc dùng ddl-auto=create/update/create-drop trên database dùng chung. Kiểm runtime trước sửa schema. DB mới dùng SQL khởi tạo đúng thứ tự; DB đã có cần migration có mục tiêu, không chạy lại toàn bộ bootstrap.
7. Dùng tài khoản ứng dụng có quyền phù hợp, không dùng payment_erd chỉ đọc. Không mặc định tài khoản maintenance/owner là tài khoản runtime. Không đọc datastore của service khác.
8. Với entity đã có, dùng ddl-auto=validate khi mapping sẵn sàng; validate không chứng minh toàn bộ constraint/index/schema. Không cần tạo đủ 13 entity ngay. Chọn mapping theo use case, không sinh CRUD công khai cho mọi bảng.
9. Secret lấy từ môi trường; .env không tự được Spring đọc. Không in password, JWT, raw IPN hoặc connection string có secret. Không đưa toàn bộ config response chưa lọc vào log/báo cáo.
10. Tiền VND nguyên đồng; wire format chuỗi theo B13, DB NUMERIC(19,0), Java biểu diễn chính xác và kiểm miền giá trị, không double/float. Snapshot/owner/amount đến từ Booking. Đơn vị VNPay ×100 chỉ nằm trong adapter.
11. Gọi HTTP/provider ngoài transaction giữ khóa. Business state + outbox cùng commit; inbox so hash trước dedupe; ACK sau commit. Mọi tác động tiền phải có cơ chế chống lặp và phục hồi.
12. Timeout không phải terminal FAILED. Return page không chứng minh thu tiền. Ticket COMPLETED/probe không đủ kết thúc Saga: Payment chờ booking.order-completed hợp lệ.
13. Fake provider/stub chỉ trong test hoặc dev cô lập, bị chặn ở production. Test dữ liệu trong database riêng; không tạo endpoint dev công khai để sửa trực tiếp trạng thái thanh toán.
14. Unit test cho rule thuần; PostgreSQL thật cho mapping/constraint/transaction/concurrency; RabbitMQ thật cho publish/ACK/redelivery. Khi bước có sửa code, chạy Maven verification ở application bị ảnh hưởng nếu môi trường cho phép. Không tắt gate để làm xanh.
15. Khi bắt đầu có order, giữ correlationId=orderId xuyên HTTP/message/log. Bổ sung quan sát từng bước theo B16, không để cuối mới làm lại.

## Lộ trình chi tiết

## Bước 1 — Kiểm tra database và bộ khung

**Để làm gì:** Biết hiện có gì trước khi viết thêm mã.

**Xong khi:** Xác định được database/schema/bảng/quyền hiện có hoặc ghi rõ chưa kết nối được; có kết quả build baseline.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 1; đọc kết quả bước trước, không tự giả định nó đã PASS.

Kiểm tra JDK 21, Maven Wrapper, pom và test hiện có; chạy baseline verify. Đọc tài liệu SQL/initializer, kiểm kết nối và metadata PostgreSQL bằng thao tác chỉ đọc nếu có cấu hình hợp lệ. Báo payment_db/payment_schema có tồn tại không, tên bảng có tbl_ không, tài khoản dùng để kiểm có quyền gì; không in secret. Nếu chưa có DB/schema, chuẩn bị hướng dẫn/script khởi tạo cho môi trường mới hoặc migration có mục tiêu, chưa chạy DDL lên database dùng chung trong bước này. Phân biệt kết quả đọc tài liệu với kiểm runtime thật. Tạo README ngắn để lưu cách chạy và kết quả baseline. Chưa tạo entity.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 2 — Chạy Config Server

**Để làm gì:** Payment có nơi lấy cấu hình khi khởi động.

**Xong khi:** Config Server chạy được, endpoint /payment-service/default trả cấu hình phù hợp; chưa cần Payment chạy.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 2; đọc kết quả bước trước, không tự giả định nó đã PASS.

Đọc ứng dụng và cấu hình Config Server hiện có trước khi sửa. Chạy nó bằng JDK 21, kiểm endpoint /payment-service/default và nguồn cấu hình payment-service.yaml cùng application.yaml dùng chung. Xác nhận cổng mặc định 8888; giải thích cấu hình nào cấp cho Payment và biến môi trường nào được Payment giải quyết lúc chạy. Chỉ bổ sung cấu hình còn thiếu cần thiết. Không hiển thị giá trị secret trong kết quả kiểm. Nếu có sửa code/config, chạy kiểm phù hợp. Ghi lệnh chạy Windows và cách kiểm endpoint đã lọc. Chưa tạo entity hoặc chức năng thanh toán.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 3 — Cho Payment khởi động và nối PostgreSQL

**Để làm gì:** Ứng dụng thực sự kết nối được database, dù chưa có nghiệp vụ.

**Xong khi:** Payment nhận cấu hình và thực hiện được truy vấn kết nối an toàn; database schema không bị tự sửa.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 3; đọc kết quả bước trước, không tự giả định nó đã PASS.

Cấu hình Payment lấy config từ Config Server, chạy cổng mặc định 8083, nhận PAYMENT_DB_URL/USERNAME/PASSWORD qua môi trường. Xác định đúng quyền runtime và schema từ bước 1. Với DB mới thiếu schema, dùng kế hoạch khởi tạo đã chuẩn bị chỉ trong môi trường dev/test xác định rõ; không ghi đè DB đang có. Kiểm PostgreSQL thật bằng smoke test riêng, không dùng test context đã tắt DataSource để kết luận. Khi chưa có entity, giữ Hibernate không sinh/sửa bảng. Nếu RabbitMQ/Eureka chưa chạy, tạo profile local-foundation có phạm vi rõ để tắt phần chưa dùng và giữ datasource hoạt động; không sửa hành vi mặc định production, không bật profile này mặc định. Ghi rõ các dependency bị cô lập trong profile và sẽ bật lại ở bước tích hợp. Không mở toàn bộ security chỉ để thấy health.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 4 — Entity đầu tiên: OrderPaymentSnapshot

**Để làm gì:** Học cách ánh xạ một bảng sang Java.

**Xong khi:** Lưu/đọc được snapshot thử, đúng kiểu tiền, JSONB và thời gian.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 4; đọc kết quả bước trước, không tự giả định nó đã PASS.

Đọc schema thực của order_payment_snapshots và tên tbl_ tương ứng. Tạo OrderPaymentSnapshot entity và repository tối thiểu, mapping UUID, buyerSubject, expectedAmount NUMERIC(19,0), currency, expiresAt, sourceOrderVersion, purchaseSnapshot JSONB, snapshotHash và receivedAt đúng DDL. Giải thích @Entity/@Table/@Id/@Column và repository ngay trên ví dụ này. Dùng dữ liệu fixture hợp lệ ở DB test riêng để kiểm save/find và dữ liệu đọc lại. Bật ddl-auto=validate cho mapping đã tạo khi phù hợp; không dùng entity sinh bảng. Không tạo toàn bộ entity, không thêm REST CRUD cho snapshot.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 5 — Entity thứ hai: PaymentAttempt

**Để làm gì:** Hiểu một lần thử thanh toán và quan hệ với snapshot đơn.

**Xong khi:** Ghi/đọc attempt được; khóa ngoại và quy tắc một attempt chưa kết thúc được kiểm bằng PostgreSQL.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 5; đọc kết quả bước trước, không tự giả định nó đã PASS.

Tạo PaymentAttempt entity/repository theo DDL và tên bảng runtime. Mapping state, provider/merchant, request key/hash, amount/currency, started/finished, URL và row_version có chủ ý; nếu dùng @Version thì kiểm cơ chế cập nhật tương ứng. Giải thích snapshot là dữ liệu đơn bất biến còn attempt là một lần thanh toán. Tôn trọng composite FK order/amount/currency và partial unique một unfinished attempt/order. Có thể giữ orderId dạng UUID nếu phù hợp, không ép quan hệ hai chiều/cascade. Test DB thật cho snapshot không tồn tại, amount lệch, key trùng, hai attempt PENDING/UNKNOWN cạnh tranh và transition terminal hợp lệ. Chưa gọi cổng thanh toán.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 6 — DTO, validation và xử lý lỗi

**Để làm gì:** Tách dữ liệu API/message khỏi entity và kiểm quy tắc đầu vào.

**Xong khi:** Dữ liệu sai bị từ chối rõ ràng; tiền và snapshot được serialize nhất quán.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 6; đọc kết quả bước trước, không tự giả định nó đã PASS.

Tạo DTO/value object cần cho PaymentView, PaymentStart, PurchaseSnapshot, StartPayment và StartPaymentResult theo B13. API không trả entity trực tiếp. Validate money chuỗi nguyên đồng/giới hạn NUMERIC(19,0), UUID, thời gian UTC, quantity, tổng tiền, ghế/ID trùng và các bất biến snapshot. Viết canonical serialization/hash có fixture trao đổi với Booking, kiểm items/seats đảo thứ tự vẫn cho hash chuẩn theo contract. Tạo Problem error mapping theo B13. Unit test input lẻ/âm/số mũ, trường lạ, null và dữ liệu không nhất quán. Giải thích entity khác DTO thế nào. Chưa tạo public POST start payment tại Payment.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 7 — API đọc trạng thái và bảo mật

**Để làm gì:** Có API đầu tiên gọi được từ HTTP client.

**Xong khi:** Chủ đơn đọc được, người khác bị từ chối; GET không thay đổi dữ liệu.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 7; đọc kết quả bước trước, không tự giả định nó đã PASS.

Hiện thực GET /api/v1/orders/{orderId}/payment và requestId tùy chọn theo B13. Cấu hình JWT Resource Server tại Payment, kiểm issuer/audience/expiry/chữ ký và ownership bằng buyerSubject trong snapshot Booking. Chưa snapshot trả 404 PAYMENT_NOT_READY theo contract. Query requestId phải thuộc đúng order/subject. Khi chưa có inbox, query chưa có kết quả chỉ biểu diễn trạng thái thật theo B13, không bịa CREATED; hoàn thiện phần inbox ở bước 8. GET không freeze/tạo attempt/gọi cổng. Test DTO, 401, ownership, snapshot thiếu, requestId khác đơn và không có ghi dữ liệu. Cung cấp cách thử bằng fixture/token test an toàn; chưa cần frontend.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 8 — Inbox, outbox và RabbitMQ

**Để làm gì:** Nhận/gửi lệnh bền khi ứng dụng hoặc mạng lỗi.

**Xong khi:** Rollback không phát message; redelivery không lặp tác động; mất ACK có thể phục hồi.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 8; đọc kết quả bước trước, không tự giả định nó đã PASS.

Hiện thực inbox/outbox trong Payment theo DDL/B13, Envelope v1, exchange flash-ticket.v1 và queue riêng theo consumer. Business change+outbox cùng transaction; relay dùng publisher confirm và xử lý unroutable message; publish lại giữ messageId/data. Inbox so hash trước dedupe, lưu response_payload và ACK sau commit. Thêm retry/backoff, claim phục hồi và cách xử lý message sai cấu trúc tránh retry vô hạn. Bật RabbitMQ thật cho integration profile; không dùng local-foundation để tuyên bố broker đã chạy. Test PostgreSQL/RabbitMQ cho V09: rollback, duplicate, crash sau confirm trước published_at, sau effect trước ACK. Nối phần inbox vào query PaymentView và giữ correlation/trace.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 9 — Nhận StartPayment với cổng giả

**Để làm gì:** Tạo attempt/URL mà chưa cần VNPay hoặc Booking hoàn thiện.

**Xong khi:** Một request hợp lệ tạo đúng một attempt; poll thấy tiến độ; từ chối và timeout được phân biệt.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 9; đọc kết quả bước trước, không tự giả định nó đã PASS.

Hiện thực consumer payment.start-requested.v1 theo B13/B14. Kiểm requestId=messageId, requestHash, snapshot; dedupe request cũ trước xét unfinished attempt. Gọi guard Booking POST /internal/v1/orders/{id}/freeze-payment bằng service credential ngoài transaction giữ khóa; dùng stub đúng contract trong test khi Booking chưa có. Guard từ chối ghi REJECTED bền, timeout retry cùng command. Tạo PaymentProvider interface và fake test-only đi qua cùng application handler. Thiết kế khả năng phục hồi giữa inbox/attempt/createPayment; không đánh inbox xong khi còn hiệu ứng có thể mất. Hoàn thiện poll WAITING/CREATED/REJECTED và UNKNOWN URL null. Test V21/V25, restart và request cạnh tranh. Không tạo public POST payment-attempts tại Payment.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 10 — Khoản thu và Saga thành công

**Để làm gì:** Nối xác minh thu tiền với Booking và Ticket.

**Xong khi:** Callback lặp không thu nhận hai lần; Saga chỉ hoàn tất sau Booking commit đơn hoàn tất.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 10; đọc kết quả bước trước, không tự giả định nó đã PASS.

Thêm charges, payment_confirmations và payment_sagas theo B13/B14/DDL. Kết quả provider phải được xác minh trước khi ghi charge; provider/merchant/chargeId là identity chống trùng. Chọn candidate charge, ghi WAITING_BOOKING+outbox booking.accept-payment.v1. Chỉ sau PaymentDecision ACCEPTED mới tạo confirmation, ticket.issue.v1 và WAITING_TICKETS. Chỉ booking.order-completed.v1 đúng order/charge/issuance/số vé mới hoàn tất. Ticket COMPLETED/probe chưa đủ. Ràng buộc nguồn message và xử lý replay/late completion. Test bằng Booking/Ticket stub, bao gồm duplicate charge, input sai và transaction crash. Charge thực thu thêm phải lưu/phân loại chờ hoàn bước 11, không bỏ mất hoặc tạo confirmation thứ hai. Chưa coi luồng này đủ production khi bù trừ chưa hoàn thiện.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 11 — Hoàn tiền và hủy sự kiện

**Để làm gì:** Xử lý thu muộn, thu thừa, phát vé thất bại và event bị hủy.

**Xong khi:** Một charge có một nghĩa vụ refund; timeout không hoàn lần hai; khoản đến trễ không bị bỏ.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 11; đọc kết quả bước trước, không tự giả định nó đã PASS.

Làm bước 11 thành hai phần tuần tự có test riêng. Phần A: refunds/refund_attempts, worker claim PROCESSING và request identity bền trước gọi provider; timeout query cùng identity trước retry. Theo B14 phân xử reason và giữ reason đã lưu. Chỉ Ticket terminal FAILED hợp lệ mới cho refund lỗi phát hành, không lấy timeout/404/PENDING làm bằng chứng. Ghi booking.issuance-result.v1 nhánh FAILED; COMPENSATED cần refund thành công và outbox cần thiết đã bền. Phần B: finance config, cancellation marker, event_refund_runs/items; marker tồn tại dù config chưa tới, config cũ không xóa marker. Quét batch phục hồi được; late charge/acceptance vẫn phải hoàn kể cả lượt quét trước đã xong. Không thêm Saga hủy mới. Test V07/V10/V11/V12 bằng fake, concurrency/restart và mất response sau provider hoàn thành.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 12 — Adapter VNPay sandbox

**Để làm gì:** Thay cổng giả bằng giao tiếp provider thật.

**Xong khi:** Có test chữ ký/định dạng và kết quả sandbox cho API merchant được cấp quyền.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 12; đọc kết quả bước trước, không tự giả định nó đã PASS.

Tra tài liệu VNPay chính thức hiện hành cho API/merchant dùng trước khi hiện thực signing, URL, IPN ACK, query/refund; lưu nguồn. Viết adapter PaymentProvider, chỉ adapter chuyển VND ×100 và kiểm giới hạn/chia hết 100 khi decode. Xác minh merchant, attempt/order, amount/currency, result và provider identity trước handler ghi charge. IPN dùng xác minh provider riêng; Return chỉ hiển thị/poll. Không log raw IPN/secret, không tự thêm QR payment chưa chốt. Test encoding/chữ ký, payload sai, duplicate, timeout/query. Chạy sandbox nếu credential test và callback HTTPS hợp lệ có sẵn; nếu thiếu, hoàn tất code/tests độc lập và ghi biến/quyền còn thiếu cùng NOT_RUN. Không đo tải trên sandbox hoặc phát sinh giao dịch production.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 13 — Tổng hợp tài chính và chi trả

**Để làm gì:** Hoàn thiện phần tiền của organizer sau khi luồng mua/hoàn ổn.

**Xong khi:** Phí đúng quy tắc; chưa rõ nghĩa vụ tiền thì không chi trả; đánh paid lặp không gây hiệu ứng hai lần.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 13; đọc kết quả bước trước, không tự giả định nó đã PASS.

Đọc B6/B8/B12 và contract tài chính trước xác định API. Hiện thực financial summary, fee snapshot và event_payouts với quyền/ownership. Phí HALF_UP một lần trên tổng doanh thu hợp lệ event, không cộng phí làm tròn từng đơn. Kiểm điều kiện thời gian/trạng thái/fee config và nghĩa vụ payment/refund còn pending trước payout. Đánh paid có actor/time/evidence, chống lặp; không suy thành tích hợp chuyển khoản ngân hàng tự động. Test V05/V19, thiếu config, refund pending, hai thao tác paid đồng thời. Route/DTO chưa chốt phải ghi rõ là đề xuất review, không tự coi đã được duyệt.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 14 — Tích hợp các service thật

**Để làm gì:** Chứng minh các phần giao tiếp đúng trên môi trường nhóm.

**Xong khi:** Có E2E thật đi từ Booking đến Payment/Ticket và quay lại Booking; phân biệt với kết quả stub.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 14; đọc kết quả bước trước, không tự giả định nó đã PASS.

Đối chiếu contract candidate với phiên bản nhóm đã thống nhất, ghi điểm lệch không tự sửa trạng thái duyệt tài liệu. Bật dependency thật thay profile foundation/stub: Config Server, Eureka theo topology, PostgreSQL, RabbitMQ, Keycloak và Gateway. Kiểm route POST start vào Booking, GET poll vào Payment, quyền service caller và broker. Tạo collection/runbook không chứa secret cho happy path, thu muộn/thừa, refund và event cancel. Chạy các ca Payment thuộc V07–V12,V19,V21,V22,V25 với service thật khi sẵn sàng; phần còn stub/thiếu dependency ghi rõ NOT_RUN. Không nhận kết quả mock là đã kiểm nghiệp vụ owner khác. Chưa đo tải trước khi correctness đạt.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Bước 15 — Quan sát, thử lỗi và bàn giao

**Để làm gì:** Có bằng chứng để chẩn đoán khi giao dịch bị treo hoặc xử lý lặp.

**Xong khi:** Log/trace nối được giao dịch; restart không mất tác động; người khác chạy lại được các phép kiểm.

**Prompt:**

```text
Đọc services/payment-service/IMPLEMENTATION-PLAN.md và áp dụng Quy tắc chung. Thực hiện riêng bước 15; đọc kết quả bước trước, không tự giả định nó đã PASS.

Rà B16 và phần quan sát đã thêm từng bước; hoàn thiện log/trace/metrics cho attempt, callback, inbox/outbox, Saga và refund. Kiểm correlationId=orderId xuyên HTTP/message, không lộ secret/PII theo V20. Chạy kịch bản crash/restart, lost response, duplicate/out-of-order message; lưu evidence môi trường/commit/schema và kết quả. Chỉ thử tải với fake có kiểm soát sau correctness, không dùng VNPay sandbox để đo tải. Hoàn thiện README/runbook cách chạy, test, lỗi thường gặp, các hạn chế và bước khôi phục đã kiểm. Phân biệt kiểm ứng dụng với observability backend chưa triển khai; không tuyên bố có RCA hoàn chỉnh chỉ từ log. Chạy verification cần thiết và báo phần NOT_RUN; không deploy production.

Giải thích thay đổi bằng ngôn ngữ dễ hiểu, cung cấp cách tự kiểm và báo kết quả thực tế. Dừng sau bước này.
```

## Cần phối hợp ở đâu?

- Bước 1–7: tự làm với PostgreSQL, Config Server và identity/test token phù hợp; chưa cần Booking/Ticket chạy thật.
- Bước 8–11: cần RabbitMQ cho integration tests; Booking/Ticket có thể stub theo contract. Thống nhất canonical hash, StartPayment, payable guard, PaymentDecision và OrderCompleted với nhóm.
- Bước 12: cần thông tin merchant sandbox và callback HTTPS để kiểm cổng thật. Thiếu chúng không ngăn viết adapter và test độc lập.
- Bước 14: cần các service thật và phiên bản contract thống nhất để công nhận E2E.

## Ba điều cần hiểu trước khi bắt đầu

- **Database/bảng:** nơi lưu dữ liệu; trong project này do SQL/migration quản lý.
- **Entity:** lớp Java ánh xạ bảng; tạo entity không đồng nghĩa phải sinh lại bảng.
- **Repository → Service → Controller:** repository đọc/ghi; service thực hiện quy tắc và transaction; controller nhận/trả HTTP. Consumer message cũng gọi nghiệp vụ, không phải mọi thao tác đều cần endpoint HTTP.

Mục tiêu gần nhất: hoàn thành bước 1–5, chứng minh Payment lấy được config và lưu/đọc hai bảng đầu tiên trong môi trường thử. Sau đó mới làm DTO và API ở bước 6–7.
