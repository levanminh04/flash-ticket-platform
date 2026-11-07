# Hoàn tất tích hợp FlashTicket — ứng dụng local, hạ tầng119

- Ngày: 04/11/2026. Phiên bản: v0.1.
- Pha: **BRAINSTORMING**. Trạng thái thiết kế và khai báo Java: **PENDING**.
- USER_CONFIRMED: user chọn **A** sau khi được trình hai phương án môi trường: ứng dụng local dùng hạ tầng119, hoặc backend triển khai trên119.
- Phạm vi: năm nhóm việc trong báo cáo đối chiếu hai chat, ClickUp, source và runtime: đồng bộ source; môi trường/Gateway; RabbitMQ; nghiệm thu mua vé; đọc đơn/lịch sử, kiểm thử và bàn giao.
- Việc chọn A chỉ chốt môi trường. Chưa duyệt các API chi tiết, C1–C9/M1–M10/U1–U5 dưới đây hoặc cho phép triển khai. Tài liệu này không tự tạo quyền commit/push, đổi runtime119, đóng connection, replay message hay ghi dữ liệu dùng chung.

## 1. Kết quả cần đạt

Buyer dùng frontend local để đăng nhập, xem sự kiện thật, chọn vé/ghế, tạo đơn, áp voucher, thanh toán VNPay Sandbox và xem vé/QR. Giá, hạn giữ chỗ, quyền sở hữu và trạng thái cuối đều do backend xác nhận. Khi reload hoặc mở lại bằng mã đơn, FE đọc được OrderView hiện hành; lịch sử liệt kê đúng các đơn của buyer.

Booking chỉ COMPLETED sau khi nhận kết quả phát vé hợp lệ qua Payment. Payment chỉ hoàn tất Saga sau xác nhận OrderCompleted của Booking. Broker ACK, trạng thái attempt SUCCEEDED và URL Return không thay các xác nhận nghiệp vụ này.

Nghiệm thu dựa trên đúng bộ source/image/config đã ghi nhận và các giao dịch test mới. Test fixture, health UP và số consumer lớn hơn0 là bằng chứng từng phần, không phải E2E PASS.

## 2. Phương án và quyết định

| Phương án đã trình user | Lợi ích | Chi phí/giới hạn | Quyết định |
| --- | --- | --- | --- |
| A: FE và các ứng dụng local; hạ tầng119 | Dễ kiểm đúng bản source hợp nhất, xem log và chạy lại | Cần tunnel/proxy DB và phối hợp các consumer/worker trên hạ tầng dùng chung | **USER_CONFIRMED**, câu trả lời `A` |
| B: FE local, backend trên119 | Gần môi trường bàn giao | Phải triển khai thêm backend, kiểm tài nguyên và mạng119 trước nghiệm thu | Không chọn cho đợt nghiệm thu này |

Các chi tiết dưới đây do agent đề xuất, còn PENDING: runtime local dùng Docker theo compose đang có; Vite ở127.0.0.1:5173; Gateway qua relay127.0.0.1:18081; lịch sử do Booking sở hữu; cursor theo đơn cuối trang; không thêm API list vé trùng với list đơn.

Đây là một đợt tích hợp nhiều phần phụ thuộc nhau, không phải thiết kế lại ranh giới service. Thứ tự thực hiện sẽ được triển khai thành implementation plan riêng sau khi thiết kế được duyệt.

## 3. Phạm vi và giới hạn

### Trong phạm vi

1. Hợp nhất chọn lọc các thay đổi cần cho flow buyer; giữ patch tương thích đã kiểm chứng, Redis availability và code nghiệp vụ Event/Ticket đúng phiên bản.
2. Chạy Config, Discovery, Gateway, Event, Booking, Payment, Ticket và User local; dùng PostgreSQL/MongoDB/Redis/RabbitMQ/Keycloak119 theo endpoint/credential đã xác minh.
3. Hoàn thiện route/security public catalog, discovery, callback Payment, QR và cấu hình Ticket trả kết quả về Payment.
4. Đối chiếu/đồng bộ bindings; điều tra Unacked/DLQ và lập quyết định xử lý có bằng chứng cho từng nhóm.
5. Bổ sung hai API Booking đã được nêu trong B13: đọc đơn và danh sách đơn buyer; nối checkout, lịch sử đơn và chọn đơn để xem vé.
6. Hoàn thiện runner hồi quy, lint, kiểm thử runtime thật và tài liệu kết quả; chuẩn bị bàn giao Git/ClickUp theo quyền được giao khi thực thi.
7. Tối ưu ảnh chatbot/chunk lớn bằng khả năng đang có sau khi flow hoạt động; không tăng giới hạn cảnh báo để gọi là đã tối ưu.

### Ngoài phạm vi chức năng mới

- Không thiết kế thêm Saga, queue nghiệp vụ, hệ thống refund, payout, reconciliation, sửa thương mại Event sau mở bán, hoặc UI quản trị/organizer mới.
- Giữ các guard và nghĩa vụ refund hiện có. Việc Tuấn đã có thêm code tài chính không tự đưa toàn bộ chức năng/worker đó vào phạm vi nghiệm thu này.
- Không đổi DB119, vhost hay schema theo suy đoán; không thêm Flyway/Liquibase, JOIN DB xuyên service, DTO library dùng chung hoặc framework điều phối test.
- Không xóa/purge DLQ, reset trạng thái order/Saga hoặc publish lại hàng loạt để làm sạch báo cáo. Timeout không là bằng chứng phát vé FAILED.
- Chưa nghiệm thu production, chịu tải, thiết bị vật lý hoặc hoàn tiền thật. Chỉ dùng VNPay Sandbox trong đợt này.

## 4. Nguồn và baseline

Các quan sát runtime dưới đây thuộc lượt rà soát 04/11, không được dùng thay preflight khi thực thi.

| Mã | Nguồn | Bằng chứng/giới hạn |
| --- | --- | --- |
| E1 | `dev/son` local `b00badedba75c004db808b0f17d7c3ac4cdf1998` + working tree | Có Payment patch đã merge, FE/Gateway chưa commit. Local ahead12/behind2 so với ref remote đã fetch; chưa tự merge |
| E2 | `origin/dev/son` `4a1b6cb6be9c647e4876d1ccac8241524a39ba86` | Có commit `f911c5d` Redis availability và cập nhật config Ticket cần đối chiếu |
| E3 | `origin/dev/tuan` `75e5ea7bbc8f401e66978756d65c35be4df1a515` | Có chức năng tài chính mới nhưng còn khác patch local về result version, checkpoint và recovery |
| E4 | `origin/dev/tuyen` `1bc263a3ed9b485148f1d4fbac7102430478c5a5` | Event/Ticket nghiệp vụ; `TicketSagaMessages` có cấu hình chọn đường trả kết quả. Không dùng Ticket scaffold local làm runtime E2E |
| E5 | [B13](../../architecture/B13-api-and-event-contracts.md) §3/§5/§9; [B14](../../architecture/B14-main-flow-sequences.md); convention dự án | B13-v0.3 đang REVIEW_READY; quyết định user về Payment điều phối, snapshot và ACK trong convention vẫn áp dụng. Tài liệu dẫn xuất không tự nâng trạng thái phê duyệt B13 |
| E6 | [Plan tương thích](../plans/2026-11-02-booking-payment-compatibility-plan.md) và [5 ca Payment](../reports/2026-11-02-payment-user-tests.md) | Patch và test thành phần đã có; Booking/Ticket/OAuth/VNPay trong 5 ca là mô phỏng, chưa E2E |
| E7 | [Tiến độ1.3](../../../frontend/docs/task-1.3-progress.md), [kết quả1.4](../../../frontend/docs/task-1.4-results.md), [checklist1.2](../../../frontend/docs/task-1.2-remaining.md) | Có FE và browser fixtures; báo cáo1.4 còn ghi một số hạn chế trước lúc1.3 được viết, cần cập nhật theo phiên bản |
| E8 | Docker/HTTP/Eureka/Rabbit119 trong lượt rà soát | Catalog401; DB local5432 refused; Eureka chỉ API-GATEWAY; Ticket119 đã có consumer, không còn184 Ready. Payment có10 decision/4 completion Unacked; DLQ cần phân loại |
| E9 | Bản thân OrderController/OrderService/OrderRepository/OrderMapper/OrderView hiện hành | Chưa có GET order/list; mapper và entity/items đã đủ để dùng lại, không cần lớp mapping mới |

Ảnh, JSON và log nghiệm thu phải ghi thời điểm, môi trường, SHA/image digest và orderId/correlationId. Không ghi token, mật khẩu, raw QR, raw IPN hoặc thông tin khách hàng vào tài liệu dùng chung.

## 5. Đồng bộ source và môi trường local

### 5.1 Source tích hợp

- Bảo toàn working tree hiện có. Xác định Git common directory để dùng đúng convention; không reset/checkout đè FE và Gateway chưa commit.
- Xử lý phân kỳ dev/son, đưa Redis/config Ticket vào bản tích hợp sau khi kiểm diff. Event/Ticket dùng implementation E4, không giữ hai implementation cạnh nhau gây trùng endpoint/bean.
- Payment lấy bản đã sửa tương thích ở E1 làm mốc so sánh, tích hợp chọn lọc thay đổi cần thiết từ E3. Giữ result version từ snapshot, checkpoint bắt buộc, so conflict trước state early-return và recovery theo command/result đã lưu.
- Không merge nguyên nhánh Payment chỉ để lấy nhãn mới nhất: `PaymentWorker` E3 còn có sendRefunds/pollRefunds/selectEventRefundOrders tự quét DB dùng chung. Các worker tài chính mới không được đưa vào phiên local này bằng một merge ngầm; phạm vi hiện tại giữ worker outbox/Saga E1. Nếu thay đổi này trở thành phụ thuộc thực tế, trình delta thiết kế trước khi đưa vào runtime.
- Ghi manifest source/image/config cho từng module trong báo cáo nghiệm thu. Không coi commit build mới là bằng chứng container đã nạp bản mới.

### 5.2 Thành phần và đường đi

| Thành phần | Vị trí đích và điều kiện |
| --- | --- |
| FE | Vite local127.0.0.1:5173, nạp root `.env`; `/api` proxy Gateway127.0.0.1:18081 |
| Gateway/Discovery/Config | Docker local, tái sử dụng compose/relay hiện có; relay18081 chỉ bind loopback Windows; không dùng port8081 của ứng dụng khác |
| Event/Booking/Payment/Ticket/User | Docker local cùng môi trường Docker host-network hiện có; Config/Discovery phải truy cập được từ chính container |
| PostgreSQL119 | Tunnel/proxy local theo cấu hình env. Kiểm TCP, xác thực và query đọc đúng DB từ container; Windows kết nối được chưa đủ |
| MongoDB119 | Dùng `MONGO_URI` đã xác minh của User, đúng database/authSource; kiểm từ runtime User |
| Redis/RabbitMQ119 | Endpoint, vhost và credential từ env; kiểm Redis PING, queue type/binding/quyền và publisher confirm |
| Keycloak119 | Realm/client frontend hiện có, PKCE/nonce; machine credential riêng cho Payment→Booking |
| VNPay Sandbox | Return/IPN qua URL HTTPS tunnel Payment local; app return về FE127.0.0.1:5173/payment-result |

Không tạo thêm env file cho từng service. Root `.env` chứa cấu hình được sử dụng; `.env.example` chỉ chứa tên biến và ví dụ không bí mật. Local compose override nhận giá trị từ root env, không bù bằng CLI/JVM hoặc biến môi trường tạm. Chỉ thêm tên biến/giá trị đã xác minh và báo khi thực thi.

Compose local phải cho phép EUREKA_CLIENT_ENABLED=true và cùng EUREKA_SERVER_URL. Tách override local khỏi lựa chọn Eureka=false đã chốt riêng cho Booking119; không đổi cấu hình deploy119 để chữa local. Gateway phải tìm đủ instance Event/Booking/Payment/Ticket/User với địa chỉ thực sự truy cập được, không chỉ thấy UP trong Eureka.

Giữ baseline JWT theo convention: issuer discovery, chữ ký, expiry, role và ownership; không thêm audience/JWKS override. Cấu hình Ticket E4 và config-server E2 đang có jwk-set-uri/audiences: phần tích hợp phải bỏ các khai báo runtime này theo convention, vẫn giữ fixture JWKS khi test. Không tắt bảo mật để làm readiness pass.

### 5.3 Phiên kiểm thử trên hạ tầng dùng chung

Trước khi bật listener/worker local, lập danh sách consumer, publisher và scheduler đang dùng cùng queue/DB; ghi phiên bản và người phụ trách. Nhiều consumer cùng phiên bản là cơ chế bình thường của RabbitMQ, nhưng message được một runtime khác xử lý không chứng minh bộ local đã chạy đúng.

Đợt E2E A dùng một phiên đã phối hợp: tạm ngừng các runtime cạnh tranh cùng queue và các worker cùng DB ảnh hưởng giao dịch test, xác nhận connection/channel của đúng bộ local. Việc này phải được thực hiện với người quản lý các runtime liên quan, có danh sách phục hồi và phạm vi tác động cụ thể; không tự đóng connection của máy khác. Nếu chưa phối hợp được thì ghi BLOCKED cho E2E A, vẫn chạy test thành phần độc lập.

Đây không phải cơ chế exactly-once hoặc yêu cầu concurrency=1 của sản phẩm. Khi nghiệm thu xong, khôi phục đúng runtime/config đã ghi; không bật lại producer cũ chưa khớp contract sau khi đổi binding. Không dùng isolated vhost/clone DB như phương án ngầm vì user đã chọn dùng hạ tầng119 hiện hành; nếu cần đổi cách ly môi trường, trình delta.

## 6. Gateway và contract giao dịch

### 6.1 Sửa trong method Gateway hiện có

- `GatewayRouteConfiguration.customRouteLocator`: thêm GET chính xác `/api/v1/events`, `/api/v1/categories`, `/api/v1/venues` tới Event, dùng `standardFilters` hiện có. Giữ availability trước detail và method/path riêng của từng API.
- `GatewaySecurityConfiguration.springSecurityFilterChain`: permitAll đúng ba GET catalog; giữ BUYER cho order/payment/ticket, ORGANIZER cho check-in và deny internal qua Gateway.
- Giữ delta Payment đã viết: POST `/api/v1/payments`, GET `/api/v1/payments/vnpay/return|ipn`. Callback public để Payment kiểm chữ ký; không mở public nhóm Payment còn lại.
- Route GET `/api/v1/orders` và `/{orderId}` đã có tại Gateway; bổ sung implementation tại Booking, không tạo route thay thế hoặc chuyển order history sang Payment.

Không thêm class/method Gateway mới; mở rộng assertion trong các test route/security/HTTP hiện có.

### 6.2 Snapshot, tiền và trạng thái

Giữ POST nội bộ Payment→Booking `/internal/v1/orders/{orderId}/purchase-snapshot`, body buyerSubject từ JWT được Payment xác thực, machine token INTERNAL_SERVICE. Không nhận giá từ FE và không freeze bằng GET. Retry dùng snapshot cũ, không tăng version vì Saga đổi trạng thái, không gia hạn hold.

AcceptPayment và kết quả Payment→Booking dùng sourceOrderVersion đã lưu. `ticket.issue.v1` không bị sửa máy móc sang version snapshot: giữ semantics command hiện có; recovery dùng version command gốc. Giữ khóa, local transaction, inbox/outbox, state guards và ACK sau commit đã có.

Payment/Ticket API giữ response wrapper hiện tại của từng owner: Payment ApiResponse; Booking/Ticket DTO trực tiếp. Không thêm generic wrapper để ép các owner cùng hình thức.

## 7. RabbitMQ, cấu hình Ticket và xử lý tồn

### 7.1 Contract dùng chung

Exchange `business.ex`, durable/quorum queues và policy/DLQ theo definitions đã có:

| Producer→consumer | messageType/routing key | Queue |
| --- | --- | --- |
| Payment→Booking | booking.accept-payment.v1 | booking.accept-payment.q |
| Booking→Payment | booking.payment-decision.v1 | payment.decision.q |
| Payment→Ticket | ticket.issue.v1 | ticket.issuance.q |
| Ticket→Payment, cả hai outcome | ticket.issuance-result.v1 | payment.ticket-issuance-failed.q |
| Payment→Booking, COMPLETED | ticket.issued.v1 | booking.tickets-issued.q |
| Payment→Booking, FAILED | booking.issuance-failed.v1 | booking.issuance-failed.q |
| Booking→Payment | booking.order-completed.v1 | payment.order-completed.q |

**Chỉ sửa binding là chưa đủ.** TicketSagaMessages E4 mặc định có thể phát success trực tiếp bằng ticket.issued.v1 và failure bằng routing key cũ. Local env/config phải chốt ba biến đã có:

```dotenv
TICKET_COMPLETED_RESULT_MESSAGE_TYPE=ticket.issuance-result.v1
TICKET_COMPLETED_RESULT_ROUTING_KEY=ticket.issuance-result.v1
TICKET_FAILED_RESULT_ROUTING_KEY=ticket.issuance-result.v1
```

Bật TICKET_BUSINESS_ENABLED/TICKET_LISTENERS_ENABLED/TICKET_WORKERS_ENABLED cho bộ local đã kiểm key ring/config. Không sinh format QR hoặc khóa mới để đọc dữ liệu119 cũ; xác minh active key version và key ring tương ứng. Secret/key material không đưa vào spec hoặc template.

TicketOutboxPublisher không tự đổi type của message đã commit khi config đổi. Kiểm cả pending outbox trước chuyển cấu hình; message cũ lệch type phải được phân loại và có quyết định riêng, không sửa payload/type để vượt guard.

### 7.2 Unacked và DLQ

1. Chụp metadata queue, consumer/channel, Unacked, binding và outbox/inbox/Saga liên quan bằng truy vấn đọc; số liệu rà soát cũ không phải danh sách xử lý hiện tại.
2. Tìm nguyên nhân của từng nhóm: runtime/commit chưa hoàn thành, dữ liệu cũ, sai type/version, conflict thật hoặc lỗi dependency. Không suy từ số Unacked rằng phải ACK thủ công.
3. Sửa nguyên nhân/source/config và xác nhận guard/idempotency trước replay. Connection đóng có thể làm Unacked quay về Ready và bị consume ngay; đó là tác động phải tính trong phiên chuyển đổi.
4. Với message hợp lệ cần phục hồi, replay có chọn lọc giữ ID/payload đã xác minh theo quy trình được giao; với conflict hoặc artifact test cũ, giữ bằng chứng và quyết định xử lý cụ thể. Không yêu cầu tất cả DLQ toàn hệ thống bằng0 để đóng task.
5. Nghiệm thu giao dịch test mới không phát sinh DLQ ngoài ca lỗi chủ động; các tồn cũ có bảng nguyên nhân/owner/hướng xử lý, không bị che bằng purge.

## 8. Bổ sung đọc đơn và lịch sử Booking

### 8.1 API đề xuất

| API | Input | Output/guard |
| --- | --- | --- |
| GET `/api/v1/orders/{orderId}` | JWT BUYER, UUID orderId | 200 OrderView trực tiếp. Không có hoặc khác chủ:404 ORDER_NOT_FOUND |
| GET `/api/v1/orders?size=20&cursor=<orderId>` | JWT BUYER; size mặc định20, hợp lệ1..100 theo B13; cursor tùy chọn UUID | 200 `{items:OrderView[],nextCursor:string|null}`. Chỉ truy vấn subject trong JWT |

GET không ghi order/reservation/inventory/promotion/snapshot/inbox/outbox, không phát message, không gọi provider, không lazy-expire order. Nếu worker chưa cập nhật EXPIRED thì trả state đang lưu cùng expiresAt; FE chặn thao tác khi deadline đã qua, backend command vẫn là nơi phân xử. Response đọc đơn/danh sách đặt Cache-Control:no-store vì dữ liệu riêng của buyer.

Không thêm buyerSubject/userId/email làm tham số để đổi chủ truy vấn. Anonymous401; role sai403. UUID/size sai hoặc cursor không thuộc một order đọc được của buyer:400 INVALID_REQUEST; dùng cùng lỗi cho cursor không có/khác chủ. Không tiết lộ dữ liệu của anchor.

### 8.2 Cursor và query

- Thứ tự mới trước: `createdAt DESC, id DESC`; id phá hòa khi timestamp bằng nhau.
- Cursor là ID của item cuối trang đã trả, không timestamp từ browser. Backend đọc anchor bằng findById hiện có, kiểm subject, lấy createdAt/id để query trang tiếp. Order hiện không có luồng xóa; nếu anchor không còn đọc được, trả400 và FE tải lại từ đầu.
- Hai query repository đơn giản: trang đầu theo subject và Pageable; trang tiếp theo subject với `createdAt < beforeCreatedAt OR (createdAt = beforeCreatedAt AND id < beforeOrderId)`.
- Lấy size+1 row, trả tối đa size; nextCursor là ID item cuối phần được trả khi còn row dư. Không COUNT tổng, không offset trang sâu, không fetch join collection cùng pagination.
- Map bằng OrderMapper.toView trong service transaction readOnly để đọc lazy items. Query có giới hạn nhưng số truy vấn items còn phải đo với dữ liệu đại diện; không tự thêm index, cache, batch annotation hay projection trước khi có nhu cầu thực đo và delta được duyệt.

### 8.3 Tái sử dụng và ảnh hưởng luồng cũ

OrderRepository.findById, OrderMapper.toView và OrderView được giữ nguyên. Thêm hai method đọc vào OrderService/OrderServiceImpl, không tạo OrderReadService, mapper mới hoặc lấy pessimistic lock từ các method ghi để phục vụ GET.

Luồng create/promotion/cancel/accept/issuance giữ guards, locks và transaction hiện có. Kiểm ownership tại GET là boundary mới thực sự cần; không thêm verify allocation/inventory hoặc kiểm lại toàn bộ snapshot cho thao tác đọc.

## 9. Tích hợp frontend

- `bookingService`: thêm getOrder(orderId, signal?) và getOrders(cursor?, size?, signal?) trả OrderView/OrderListView theo wire format. Tái sử dụng axios interceptor, scope buyer/issuer/gateway và timeout/AbortSignal đang có.
- Checkout đọc server khi mở/reload/quay lại; cached receipt chỉ giúp hiển thị lúc tải, không dùng để quyết định còn payable. Chờ GET thành công trước thao tác tiền; lỗi quyền/network không giữ trạng thái xác nhận cũ. Apply/cancel nhận OrderView mới; không tự tạo lại hold khi thiếu receipt.
- MyOrders đọc danh sách Booking với nút tải thêm theo cursor, mở một đơn để đọc order/payment hiện hành. Không dùng PaymentResultPage history như một danh sách toàn bộ đơn.
- MyTickets lấy danh sách đơn Booking làm điểm chọn đơn, gọi Ticket theo orderId được chọn; chỉ tải QR khi buyer mở vé VALID. Không gọi Ticket cho mọi đơn trên mọi trang hoặc suy có vé từ order COMPLETED. Ticket là nguồn state vé; không hiển thị danh sách chọn đơn như thể đó là toàn bộ vé đã phát.
- Giữ tra cứu mã đơn trực tiếp/deep link, VI/EN, loading/empty/error/retry, bàn phím và 320/390/1440px. Chuyển buyer/logout phải abort request, xóa dữ liệu người trước và revoke QR blob như cơ chế đã có.
- Giữ Payment v1, cùng Idempotency-Key sau mất phản hồi, UNKNOWN không tự thu lại, polling có giới hạn. Không relay IPN từ browser hoặc coi responseCode00 trên URL là thành công.

## 10. Bảng duyệt Java — PENDING

Đây là delta thiết kế cho task mới. Code Event/Ticket được mang từ E4 là **REUSE EXISTING**, không là quyền tạo lại mọi class/method của các service. Nếu giải quyết conflict làm thay đổi signature/annotation hoặc phát sinh khai báo ngoài bảng, trình delta trước khi chốt/viết.

Đường dẫn package Booking dưới `services/booking-service/src/main/java/com/flashticket/booking_service/`; test dưới src/test/java cùng package. Không thêm constructor/dependency vào các bean hiện có.

| ID | Class/file | Loại, annotation và API sinh | Mẫu/ngoại lệ |
| --- | --- | --- | --- |
| C1 | `dto/response/OrderListView.java` | **NEW** final class, `@Getter @AllArgsConstructor`; final `List<OrderView> items`, final `String nextCursor` nullable; chỉ sinh constructor/getItems/getNextCursor | Theo OrderView hiện có; Lombok class, không record/builder/helper |
| C2 | `order/controller/OrderController.java` | EXISTING, thêm M1/M2; giữ @RestController/@RequestMapping/@RequiredArgsConstructor và dependencies | Shop App OrderController.getOrder/getOrders; dùng JWT subject và lỗi đích, không copy catch-all/null của mẫu |
| C3 | `order/service/OrderService.java` | EXISTING, thêm hai chữ ký M3/M4 | Interface nghiệp vụ hiện có; không interface đọc riêng |
| C4 | `order/service/impl/OrderServiceImpl.java` | EXISTING, implement M3/M4; @Override/@Transactional(readOnly=true); không dependency mới | Shop App OrderService + repository, giữ mapper hiện có; readOnly/lazy mapping là cơ chế JPA cần thiết |
| C5 | `order/repository/OrderRepository.java` | EXISTING, thêm M5/M6, không @Lock | Derived query và @Query JPQL đã có trong repo; Pageable giới hạn kết quả |
| C6 | `order/controller/OrderControllerTests.java` | EXISTING test class, thêm U1–U3; không nested class/helper/constructor mới | MockMvc/security/fixtures hiện có |
| C7 | `persistence/BookingPersistenceIT.java` | EXISTING test class, thêm U4/U5, dùng hạ tầng test hiện có | Kiểm DB thật cho ownership, cursor và không đổi tài nguyên; không chạy các fixture này vào DB119 |
| C8 | Hai GatewayConfiguration và các test Gateway hiện có | EXISTING, chỉ sửa thân method/assertions; không annotation/signature mới | Tái sử dụng standardFilters và JWT/security tests |
| C9 | Payment patch E1, Redis E2, Event/Ticket E4 và User hiện có | REUSE; giữ constructors/DTO/ACK/guard. Điều chỉnh env/YAML/binding theo §5–7 | Không chuyển toàn bộ Event/Ticket sang phong cách khác; không lấy reuse làm quyền thêm API Java ngoài bảng |

| ID | Class | Chữ ký đầy đủ đề xuất | Loại và trách nhiệm |
| --- | --- | --- | --- |
| M1 | OrderController | `public ResponseEntity<OrderView> getBuyerOrder(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId)`; `@GetMapping("/{orderId}")` | **NEW**, JWT subject→M3; response no-store |
| M2 | OrderController | `public ResponseEntity<OrderListView> getBuyerOrders(@AuthenticationPrincipal Jwt jwt, @RequestParam(required=false) UUID cursor, @RequestParam(defaultValue="20") int size)`; `@GetMapping` | **NEW**, kiểm size1..100 tại boundary, JWT subject→M4; no-store |
| M3 | OrderService + OrderServiceImpl | `public OrderView getBuyerOrder(String buyerSubject, UUID orderId)`; implementation `@Override @Transactional(readOnly=true)` | **NEW ở cả interface/implementation**, findById→lọc owner→mapper;404 khi không đọc được |
| M4 | OrderService + OrderServiceImpl | `public OrderListView getBuyerOrders(String buyerSubject, UUID cursor, int size)`; implementation `@Override @Transactional(readOnly=true)` | **NEW ở cả interface/implementation**, kiểm owned anchor nếu có, query size+1→map→nextCursor; không helper mới |
| M5 | OrderRepository | `public List<Order> findByBuyerSubjectOrderByCreatedAtDescIdDesc(String buyerSubject, Pageable pageable)` | **NEW**, derived query cho trang đầu; method ghi/lock hiện có không phù hợp thao tác đọc |
| M6 | OrderRepository | `public List<Order> findBuyerOrdersBefore(@Param("buyerSubject") String buyerSubject, @Param("beforeCreatedAt") Instant beforeCreatedAt, @Param("beforeOrderId") UUID beforeOrderId, Pageable pageable)`; `@Query` JPQL theo §8.2, order createdAt/id DESC | **NEW**, query trang tiếp có lọc owner; không SQL native/EntityManager |
| M7 | OrderListView | `public OrderListView(List<OrderView> items, String nextCursor)` | **NEW, Lombok sinh** từ @AllArgsConstructor; không constructor rỗng |
| M8 | OrderListView | `public List<OrderView> getItems()` | **NEW, Lombok sinh** từ @Getter |
| M9 | OrderListView | `public String getNextCursor()` | **NEW, Lombok sinh** từ @Getter |
| M10 | OrderMapper | `public OrderView toView(Order order)` | **REUSE UNCHANGED**, MapStruct hiện có; không mapper mới |
| U1 | OrderControllerTests | `void readsOrderUsingAuthenticatedBuyer() throws Exception`; `@Test` | **NEW**, subject/route/no-store/response và404 theo service |
| U2 | OrderControllerTests | `void listsOrdersUsingAuthenticatedBuyer() throws Exception`; `@Test` | **NEW**, wire items/nextCursor/default size/pass cursor; không nhận owner từ request |
| U3 | OrderControllerTests | `void rejectsUnauthorizedOrInvalidOrderReads() throws Exception`; `@Test` | **NEW**,401/403, UUID/cursor/size sai,400 theo service, không gọi nghiệp vụ khi boundary sai |
| U4 | BookingPersistenceIT | `void readsBuyerOrdersWithoutChangingBookingState()`; `@Test` | **NEW**, own/foreign/missing order, cursor foreign/missing, GET không freeze/expire/release hoặc tạo message |
| U5 | BookingPersistenceIT | `void paginatesBuyerOrdersWithStableCursor()`; `@Test` | **NEW**, nhiều buyer, timestamp trùng, size+1, hết trang, insert mới giữa hai trang, list rỗng |

Các method Java mới chỉ nằm trong M1–M9 và U1–U5 như trên. Không giấu helper/test setup mới trong lambda/anonymous class. Không phát sinh bean hoặc API Lombok khác. Bảng này cần được rà lại theo source đã hợp nhất ở pha writing-plans; phê duyệt thiết kế không miễn gate pha sau.

## 11. Kiểm chứng và điều kiện hoàn tất

| Nhóm | Tiêu chí bắt buộc |
| --- | --- |
| Source/runtime | Manifest đúng SHA/digest/config, không mất patch tương thích; service local không còn là scaffold hoặc image cũ |
| Readiness | TCP/auth/query đúng DB từ container; Mongo/Redis/Rabbit/Keycloak đúng môi trường; health và restart count ổn định ít nhất5 phút; đủ5 business instances trong Eureka và Gateway thực sự gọi được |
| Gateway/auth | Guest catalog/detail/availability200; guest order/profile401; role sai403; internal không qua Gateway; login/reload/refresh/logout giữ PASS; đăng ký mới→identity→profile thật |
| Order/history | GET own order200, foreign/missing404; list chỉ subject hiện tại; cursor/size và số tiền chuỗi đúng; reload checkout đọc server; GET không đổi tài nguyên |
| Booking | QUANTITY/STANDING/SEATED thật; ID/geometry/pool đúng; hai buyer tranh tài nguyên cuối; retry cùng key chỉ giữ một lần; voucher hợp lệ/lỗi/freeze; hủy/expiry release đúng một lần |
| Payment/Ticket | Sandbox thực, callback chữ ký hợp lệ, return trước IPN, callback lặp, pending/UNKNOWN và timeout không bị coi thất bại; đủ vé đúng order/seat; Booking COMPLETED và Saga COMPLETED đúng checkpoint; QR thật chỉ chủ vé đọc được |
| Broker | Success và failure của Ticket đi qua Payment; version snapshot khi gửi Booking/recovery; payload/metadata không mang Java type header; test mới không phát sinh DLQ ngoài dự kiến; tồn cũ có phân loại |
| Hồi quy | Maven verify đúng JDK theo từng module/POM, không bỏ enforcer; test tích hợp dùng DB/broker test riêng; build FE và lint PASS; runner fixtures có cả payment-ticket.browser.js |
| Giao diện | Loading/empty/error/retry, VI/EN, bàn phím, 320/390/1440px; buyer switch/abort/QR blob cleanup không rò dữ liệu |
| Hiệu năng FE | Ghi kích thước asset/chunk trước-sau. Nén/đổi asset chatbot nếu có công cụ phù hợp và lazy-load phần ít dùng; không đổi nhận diện hoặc dùng dependency mới chỉ để hết cảnh báo |
| Bàn giao | Cập nhật integration/1.2/1.3/1.4 và báo cáo kết quả; ClickUp phản ánh evidence, không Complete khi E2E BLOCKED. Commit/push/deploy chỉ theo chuỗi bàn giao thực tế được giao; push Booking có thể kích hoạt auto-deploy119, phải tính tác động trước khi push |

Mở rộng runner hiện có, không viết framework kiểm thử khác. `--live` hiện chỉ readiness/auth không tự trở thành full E2E: bổ sung bước thực hiện flow buyer trên event test đã xác minh và báo cáo rõ PASS/FAIL/BLOCKED theo từng bước. Không ký IPN giả rồi gắn nhãn Sandbox thật. Các ca không thể chủ động tạo ở sandbox chỉ được ghi PASS ở lớp mô phỏng/thành phần tương ứng, chưa PASS E2E.

Mỗi giao dịch test lưu orderId/attemptId/chargeId/issuanceId và bằng chứng state/inventory trước-sau đã lọc dữ liệu riêng. Không tự replay dữ liệu cũ để thay cho test mới. DLQ do ca lỗi chủ động phải có messageId và kết quả mong đợi riêng.

## 12. Đối chiếu convention và nguồn tham khảo

- Shop App đã đọc trực tiếp `controllers/OrderController.java` và `services/impl/OrderService.java` tại `C:/Users/sondimid/IdeaProjects/shopapp-backend/shopapp-backend/shopapp`: giữ controller→service→repository, constructor injection; dùng exception/ownership/UUID của FlashTicket, không copy `orElse(null)` hoặc catch-all từ mẫu.
- DTO list theo class Lombok của OrderView. Dùng lại OrderMapper MapStruct đang có, không thêm ModelMapper. Mapping lazy items nằm trong transaction đọc; không lấy lock từ luồng ghi hoặc thêm verify allocation.
- Giữ property @Value hiện có, env-file duy nhất, Booking logging chữ mặc định; không mang lựa chọn logging của Booking sang các service khác.
- [Spring Data query methods](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html): List + Pageable giới hạn dữ liệu mà không cần count. Áp dụng hai query đơn giản ở M5/M6; tự ghép DTO items/nextCursor theo B13, không đổi wire format sang Spring Page.
- [Spring Data transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html): đặt transaction đọc ở service cho toàn bộ lookup/mapping; readOnly không thay kiểm ownership.
- [Ví dụ chính thức CustomerRepository](https://github.com/spring-projects/spring-data-examples/blob/main/jpa/example/src/main/java/example/springdata/jpa/projections/CustomerRepository.java): tham khảo repository với @Query/Pageable và DTO; không copy projection interface/framework khi mapper hiện có đủ dùng. Các tài liệu upstream mô tả API, không thay việc compile/test theo phiên bản thực tế trong POM.

## 13. Phần giao cho người lập plan và điểm còn chờ

- **Chờ user duyệt:** toàn bộ thiết kế v0.1, C1–C9/M1–M10/U1–U5, đặc biệt API đọc đơn/list, cursor và cách dùng danh sách đơn để chọn xem vé.
- **Người lập plan tự quyết trong thiết kế này:** chia commit/task theo phụ thuộc, cách lưu manifest trong báo cáo hiện có, tên file compose local override và câu lệnh verification phù hợp máy/JDK; không được đổi topology/quyền/API hoặc sinh thêm khai báo Java ngoài bảng.
- **Đầu vào phải xác minh khi thực thi:** endpoint/credential Mongo và DB119; machine token Payment; QR key ring; callback domain đang hoạt động; bộ runtime/worker cần phối hợp trong phiên test. Thiếu giá trị đúng thì giữ BLOCKED phần runtime tương ứng, không điền secret suy đoán.
- **Builder check:** phiên này không có công cụ subagent; agent tự đọc lại tài liệu theo builder-check-prompt. Đã làm rõ lựa chọn môi trường, nguồn source, Ticket default routing, shared consumers, Payment refund workers, cursor và giới hạn bằng chứng. Chưa coi nội dung thiết kế là đã triển khai hoặc đã được reviewer độc lập xác nhận.
- Chưa lập implementation plan, chưa sửa application/test/config runtime, chưa commit/push/deploy và chưa ghi ClickUp trong lượt viết spec này.
