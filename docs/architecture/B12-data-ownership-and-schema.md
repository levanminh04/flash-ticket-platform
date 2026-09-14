# B12 — Thiết kế dữ liệu đích và baseline khởi tạo

- Phiên bản: `B12-v0.2`
- Trạng thái: `APPROVED`
- Ngày mở: 2026-09-07
- Người duyệt: Lê Văn Minh; ngày duyệt: 2026-09-14 (GOV-144)
- Phân lớp: `FORMATION`
- Đầu vào: B5-v0.14/B10-v0.9/B11-C-v0.3 APPROVED; B6-v0.15/B7-v0.13/B8-v0.13 APPROVED đồng bộ quyết định đã xác nhận; GOV-101–143, BIZ-153–158, PRJ-010–024 theo phần còn hiệu lực. B13-v0.1/B14-v0.1 được duyệt sau B12 trong cùng xác nhận GOV-144.
- Thẩm quyền: phiếu B12 tại `../tang-b-quy-trinh-ky-thuat.md`; `../quy-trinh-lam-viec.md` §5; `../../AGENTS.md`.
- Lệnh thực hiện: Lê Văn Minh yêu cầu “ok triển khai công viêc tiếp theo giúp tôi”, sau bản kế hoạch B12 tổng hợp. Đây là quyền tạo bộ nháp và kiểm tra cục bộ; không phải phê duyệt B12 hay lời giải cho các nghiệp vụ còn `OPEN`.

## 1. Phạm vi và trạng thái bằng chứng

Tài liệu này là nguồn thiết kế B12; ERD, script và bảng kiểm là biểu diễn dẫn xuất. Năm owner lấy từ B11-C, dữ liệu và quy tắc ghi lấy từ B5/B7/B8. Tên bảng, kiểu dữ liệu và biện pháp kỹ thuật bên dưới là baseline thiết kế `DECIDED` theo GOV-144; các OPEN về môi trường/kiểm thực thi vẫn giữ nguyên.

| Phát biểu | Trạng thái | Nguồn / quyền sử dụng |
|---|---|---|
| PA-6 gồm event, booking, payment, ticket, user; một Saga do payment điều phối | `DECIDED` | B11-C §3–4; không tạo service/Saga mới |
| Hai máy, không HA; mục tiêu khoảng 6 GiB cấp phát mỗi máy 8 GiB; được nâng instance tạm thời | `DECIDED` | B11-C §6, GOV-096–098; không phải bằng chứng chịu tải |
| Tạo bộ B12 nháp và kiểm tra trong repo | `USER_CONFIRMED` | Yêu cầu thực hiện ngày 2026-09-07; không tự chuyển trạng thái phê duyệt |
| Một PostgreSQL instance, bốn business database; MongoDB riêng cho user; Keycloak database riêng | `USER_CONFIRMED` | GOV-104–107; vị trí triển khai cụ thể vẫn phải kiểm tài nguyên |
| Bootstrap sạch, không chuyển tài khoản/dữ liệu nghiệp vụ cũ | `USER_CONFIRMED` | PRJ-010/011; không xóa hay di trú dữ liệu đang chạy |
| Hủy gửi lại, follow/unfollow, trạng thái đơn, quy ước tiền/email | `USER_CONFIRMED` | BIZ-153–158, GOV-131–142; §9 ghi chi tiết phần kỹ thuật còn kiểm |

Không mở repo cũ/B5.5 để hình thành ranh giới trong tài liệu này. Bằng chứng cấu hình cũ/AWS được giữ trong tạo tác `COMPARISON` riêng, chỉ kiểm khả thi. Không nhập tệp realm nguyên bản hoặc mật khẩu vào Git. Vòng GOV-143 đồng bộ B6/B7/B8 theo quyết định đã xác nhận; không sửa AWS, B11, ADR đã chấp nhận hoặc bộ nghiên cứu.

## 2. Topology, quyền và khả năng khôi phục

| Datastore | Vị trí ứng viên | Owner / runtime principal | Schema |
|---|---|---|---|
| `event_db` | PostgreSQL chung, máy 1 | `event-service` / `event_app` | `event_schema` |
| `booking_db` | PostgreSQL chung, máy 1 | `booking-service` / `booking_app` | `booking_schema` |
| `payment_db` | PostgreSQL chung, máy 1 | `payment-service` / `payment_app` | `payment_schema` |
| `ticket_db` | PostgreSQL chung, máy 1 | `ticket-service` / `ticket_app` | `ticket_schema` |
| `keycloak_db` | PostgreSQL chung, máy 1 | Keycloak / `keycloak_app` | Nội bộ Keycloak; B12 không tạo bảng |
| `user_db` | MongoDB, máy 2 | `user-service` / `user_app` | Bốn collection ở §4.5 |

Một instance không có nghĩa là bốn process PostgreSQL. Bốn database giảm nguy cơ truy vấn nhầm từ một connection; vẫn dùng chung CPU, IO, roles cấp cluster và failure domain. Lựa chọn hiện hành là bốn business database theo GOV-105; không mở lại phương án bốn schema. Chưa có căn cứ cần PostgreSQL instance thứ hai hoặc đổi persistence user sang SQL trong baseline này.

Máy 2 truy cập PostgreSQL qua endpoint riêng được cấu hình theo môi trường; Docker service name chỉ có ý nghĩa trong network tương ứng, không giả định DNS Docker hoạt động xuyên hai máy. Mất máy 1 ảnh hưởng cả transaction plane và dữ liệu Keycloak/event; đây là hệ quả phải kiểm và công bố, không hứa HA.

### 2.1 Ma trận quyền tối thiểu

| Principal | Được phép | Không được phép |
|---|---|---|
| `*_app` của bốn service SQL | SELECT/INSERT/UPDATE các bảng được cấp rõ trong database mình | CONNECT database khác; DDL; cấp quyền; TRUNCATE; sở hữu object; superuser; FDW/dblink |
| `*_owner` | Sở hữu object của đúng một service, `NOLOGIN` | Dùng làm runtime credential |
| `*_migrator` | Đăng nhập triển khai riêng, SET ROLE owner tương ứng | Chạy ứng dụng; được cấp các owner khác |
| `keycloak_app` | Kết nối và quản lý schema nội bộ Keycloak để hỗ trợ cơ chế nâng schema của Keycloak | Database nghiệp vụ; role nghiệp vụ |
| `user_app` | Quyền collection/operation được khai cho `user_db` | Mongo root/admin; database khác; bypass validator |
| Backup / bootstrap | Credential vận hành riêng, giới hạn công việc theo runbook | Chia sẻ với runtime; ghi secret vào repo |
| RCA | Chỉ đọc kho quan sát bằng credential riêng | CONNECT/SELECT tới database nghiệp vụ hoặc Keycloak |

Script cấp quyền phải REVOKE quyền mặc định không cần thiết, khóa `search_path`, thiết lập default privileges dưới đúng owner. Thêm bảng mới phải được kiểm lại quyền. Không coi “CRUD mọi bảng” là yêu cầu; runtime chỉ được DELETE sáu bảng con cấu hình Event (ảnh/layout/sector/seat/type/promotion), có trigger khóa parent và từ chối khi không DRAFT. Không cấp DELETE events, lịch sử, tiền hoặc vé. API vẫn kiểm ownership và xác nhận đổi mode; quyền bảng không tự bảo vệ actor.

Mỗi datastore có volume bền vững. Backup kiểm thử/demo thực hiện ở mốc dừng nhận giao dịch mới, giải quyết hoặc ghi rõ công việc đang chạy, rồi sao lưu các kho và vật liệu khóa cần khôi phục. Lưu bản sao ngoài máy chứa dữ liệu. Restore vào môi trường tách biệt; không giả định nhiều dump độc lập là một snapshot nguyên tử toàn hệ thống.

## 3. Quy ước dữ liệu ứng viên

- ID thực thể nội bộ: UUID. Tham chiếu người dùng: `user_subject`/`organizer_subject` dạng TEXT không rỗng, chứa nguyên `sub` từ issuer Keycloak được cấu hình; không dùng email/username làm khóa. Một realm/issuer theo môi trường; đổi issuer/migration danh tính phải có mapping riêng.
- Thời điểm: TIMESTAMPTZ, lưu/so sánh UTC. Mỗi cửa sổ giữ đúng điều kiện ở B7/B8, không áp dụng một quy tắc nửa mở cho tất cả. Riêng check-in dùng `eventStartAt <= now <= eventEndAt` (B7 §4.4, BIZ-067), bao gồm cả hai đầu mút; cửa sổ bán và hết hạn giữ chỗ theo quy tắc riêng của nguồn. Không dùng CHECK phụ thuộc `now()` để thay worker hết hạn.
- Tiền: NUMERIC(19,0) hữu hạn, currency chỉ VND; API chuỗi tiền, Java BigDecimal. Reject phần lẻ đầu vào trước cast SQL vì PostgreSQL có thể làm tròn khi ép scale. Tỷ lệ giảm nhập nguyên %, nội bộ fraction [0,1]; giảm HALF_UP một lần trên subtotal, phí HALF_UP một lần trên doanh thu hợp lệ event sau hoàn. Tỷ lệ phí lưu NUMERIC(9,8), không dùng float; chi tiết ở review M-01–07.
- `row_version` phục vụ chống ghi đè; `source_config_version` phục vụ nhận diện cấu hình sao chép. Hai giá trị không thay thế nhau.
- Snapshot quyết định (giá, số lượng, currency, cấu hình tham chiếu) không đổi sau khi chốt lựa chọn. ID mềm vẫn cần giữ bên cạnh snapshot để truy vết.
- Liên kết nội bộ owner dùng FK; liên kết ngoài owner là ID mềm/snapshot, không FK/JOIN/view/function/repository xuyên ranh giới.
- Order dùng sáu trạng thái đã chọn: PENDING_PAYMENT, ISSUING, COMPLETED, CANCELLED, EXPIRED, ISSUANCE_FAILED; tiền/hoàn/vé có vòng đời riêng theo review S-01–05. CHECK chặn tên sai, không tự chặn mọi chuyển trạng thái sai. Transaction guard ở B14 phải được hiện thực.

## 4. Danh mục dữ liệu và quy tắc ghi

### 4.1 event-service

Owner: sự kiện, venue/category, cấu hình bố cục/sector/ghế, ticket type, promotion, yêu cầu hủy. ERD: `../diagrams/src/B12-01-event-erd.puml`.

Bảng tối thiểu: `categories`, `venues`, `events`, `event_images`, `event_layouts`, `event_sectors`, `event_seats`, `ticket_types`, `promotions`, `event_cancellation_requests`, `outbox_messages`, `inbox_messages`.

- `events`: organizer subject, category nullable (0..1), venue, nội dung, cửa sổ sự kiện/bán/check-in, purchase limit, sales mode, status, config version, tỷ lệ phí/người/thời điểm duyệt. `saleStartAt < saleEndAt <= eventEndAt`; check-in trùng cửa sổ sự kiện, không có cấu hình giờ check-in độc lập (BIZ-006/067). Không kiểm sức chứa vật lý của venue (FR-63).
- Trạng thái sự kiện: DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, CANCELLED. Từ chối phê duyệt trở về DRAFT, không thêm REJECTED (FR-09).
- Sales mode của event: QUANTITY hoặc SEAT_MAP; loại sector: SEATED hoặc STANDING. Đây là hai trục khác nhau. Quan hệ layout/sector/seat/ticket type phải cùng event; FK ghép hoặc kiểm giao dịch cục bộ bảo vệ điều đó.
- Cấu hình thương mại khóa từ lúc gửi duyệt; tỷ lệ phí do event sở hữu và đóng băng khi duyệt (INV-11, FR-07/08). CHECK một dòng không bảo vệ được “không sửa sau duyệt”; service/transaction phải kiểm chuyển trạng thái.
- Giá loại vé phải lớn hơn 0 (FR-05). Promotion là cấu hình, không chứa counter lượt dùng; mã duy nhất trong event (BIZ-081), mỗi tài khoản chỉ dùng một lần (BIZ-043), không có hạn mức tùy chỉnh lớn hơn một lần/tài khoản. Chuẩn hóa trim + uppercase Locale.ROOT trước ghi; unique(event_id,code) áp trên mã chuẩn. Không tự thêm thao tác bật/tắt ngoài cửa sổ thời gian (FR-06).
- Mỗi cancellation request có ID riêng, actor/time/reason và kết quả; lý do từ chối là tùy chọn (BIZ-099), khác hồ sơ organizer. Không UNIQUE(event_id); không diễn giải cách lưu lịch sử thành quyền gửi lại. BIZ-153–156: một PENDING/event bằng partial unique; cho gửi lại sau REJECTED, giữ lịch sử; service khóa event và chặn gửi khi CANCELLED.
- Thống kê public là read model riêng khi có contract; không giữ counter tickets_sold làm nguồn thứ hai trong events.

### 4.2 booking-service

Owner: nguồn cung, giữ chỗ, đơn, giới hạn mua và sử dụng promotion. ERD: `../diagrams/src/B12-02-booking-erd.puml`.

Bảng tối thiểu: `event_sales_snapshots`, `orders`, `order_items`, `order_item_seats`, `reservations`, `reservation_allocations`, `ticket_type_inventory`, `sector_inventory`, `seat_inventory`, `purchase_limits`, `promotion_inventory`, `promotion_usages`, `idempotency_requests`, `outbox_messages`, `inbox_messages`.

- Order, items, reservation duy nhất/order và phần nguồn cung/giới hạn đang giữ được tạo trong một giao dịch cục bộ. Promotion được áp sau khi tạo order theo FR-21; thao tác áp tạo/cập nhật usage và số tiền trong giao dịch cục bộ riêng, cùng expiry của order. Tổng tiền phải lớn hơn 0 (FR-22); một mã/tài khoản không có hai usage HELD/USED đồng thời, nhưng giữ lịch sử RELEASED để chống lệnh trả cũ tác động vào lượt mới.
- Không thêm giỏ hàng bền vững vào baseline. Lựa chọn trước khi tạo order thuộc client; sau tạo order không sửa lựa chọn (FR-20).
- QUANTITY dùng inventory ticket type; SEATED dùng từng seat; STANDING dùng một inventory sector chung cho các ticket type. Toàn bộ order SEAT_MAP nằm trong một sector (INV-02).
- Reservation/allocations lưu ID, số lượng, nguồn cung và trạng thái. Chốt/trả kiểm reservation ID + expected state và cập nhật các counter liên quan cùng giao dịch. Không cần ba ledger mới nếu mô hình này đủ truy vết.
- `seat_inventory` và `promotion_usages` giữ current reservation/generation để lệnh release cũ không trả một lượt giữ mới. Optimistic version đơn độc không chứng minh idempotency.
- Counter không âm và held + purchased không vượt configured capacity. Các CHECK chỉ bảo vệ dòng đang ghi; kiểm đủ tổng allocations, purchase limit và mode của order thuộc transaction/service.
- Khi hết hạn/hủy order: trả đúng một lần theo FR-29. Khi phát hành lỗi: luôn trả lượt promotion đúng một lần, chỉ trả inventory/purchase limit khi event còn đủ điều kiện bán (FR-33). Khi event đã hủy, không mở lại nguồn cung (FR-15).

### 4.3 payment-service

Owner: thanh toán, hoàn, tiến độ hoàn hủy event, đối soát và đánh dấu chi trả. ERD: `../diagrams/src/B12-03-payment-erd.puml`.

Bảng tối thiểu: `order_payment_snapshots`, `payment_attempts`, `charges`, `payment_confirmations`, `refunds`, `refund_attempts`, `event_refund_runs`, `event_refund_items`, `event_payouts`, `event_finance_snapshots`, `payment_sagas`, `outbox_messages`, `inbox_messages`.

- Một attempt ID có trước khi gọi cổng; chỉ một attempt chưa kết thúc/order (FR-23). Kết quả được kiểm đúng order, attempt, amount/currency (FR-24).
- `charges` biểu diễn khoản thực thu có bằng chứng tin cậy; khóa `(provider, merchant_account, provider_charge_id)` là ứng viên cần xác nhận phạm vi uniqueness của cổng ở B13. Callback lặp cùng charge khác với hai khoản thu thật sự khác nhau. PRJ-024 loại workflow tiếp nhận khoản sai amount/currency hoặc thiếu liên kết. Không tạo confirmation/vé cho đầu vào sai, không ép tiền thành dự kiến; không tuyên bố sổ nội bộ bao hết khoản cổng đã thu. Callback chưa xác thực không được coi là bằng chứng thu tiền.
- Confirmation unique order, charge được chọn unique; chỉ nhận khoản khớp amount/currency dự kiến qua FK ghép với snapshot và charge. Refund unique charge, toàn bộ số thực thu; lịch sử retry hoàn không tạo yêu cầu logic mới. FK ghép cục bộ bảo vệ cùng order/charge và attempt đúng đơn. Không tạo kho ngoại lệ Q-07 hoặc nguyên nhân hoàn thứ năm; đây là giới hạn công bố ở B13.
- Thu thừa/muộn được hoàn độc lập và không thay đổi order/vé hợp lệ. Lý do hoàn: LATE_PAYMENT, DUPLICATE_PAYMENT, TICKET_ISSUANCE_FAILED, EVENT_CANCELLED; nhiều nguyên nhân cho một charge phải hội tụ, quy tắc phân xử thuộc B13/B14.
- Run/item hoàn hủy đếm order, không đếm mọi charge. Item tồn tại cả khi chưa tạo refund; lưu cursor/selection_complete để mẫu số không bị hiểu là đầy đủ trước khi chọn xong. Khoản thu thừa không nằm trong run (FR-38/39).
- Payout giữ snapshot thu hợp lệ/hoàn/phí/net, version phí, mốc đối soát, bằng chứng đối chiếu, actor/time. Chỉ đánh paid một lần khi event kết thúc và không còn payment/refund pending (FR-42/43). Không thiết kế tự chuyển tiền ngân hàng.
- `payment_sagas` lưu đúng một tiến trình điều phối/order (WAITING_BOOKING, WAITING_TICKETS, COMPLETED, COMPENSATING, COMPENSATED). Mỗi SQL owner có outbox/inbox trong cùng DB; không dùng một bảng chung xuyên owner. Booking còn có idempotency_requests để retry tạo đơn không tạo giữ chỗ thứ hai. User không tự thêm Mongo outbox: workflow roleGrant nằm trong application và projection phục hồi từ nguồn, theo B13.

### 4.4 ticket-service

Owner: phát hành, giao nhận, hiệu lực vào cửa. ERD: `../diagrams/src/B12-04-ticket-erd.puml`.

Bảng tối thiểu: `event_access_snapshots`, `issuances`, `tickets`, `ticket_deliveries`, `checkin_attempts`, `outbox_messages`, `inbox_messages`.

- Issuance unique order và giữ purchase snapshot bất biến. Ticket có khóa `(issuance_id, order_item_id, ordinal)`; ordinal > 0. Phát toàn bộ tập vé của một order trong một giao dịch, kiểm đủ số lượng từng dòng trước khi đánh completed.
- Retry cùng input trả cùng tập vé; cùng order/input khác bị từ chối. UNIQUE root không tự chứng minh đúng số ticket con. Không cần bảng slot riêng khi chưa có yêu cầu phát hành từng phần.
- Check-in dùng cập nhật điều kiện nguyên tử, xét issuance/event/owner/window và hiệu lực vé; tối đa một thành công/vé. Tối ưu khóa dòng không biến ticket thành aggregate root mới của B7.
- Lưu mọi yêu cầu check-in, cả bị từ chối (FR-65); audit không chứa QR/JWT thô. Dữ liệu actor/time/result phục vụ điều tra; định dạng log/trace và retention vẫn B16.
- QR có thể tải lại (FR-31): baseline lưu `qr_token_ciphertext`, `qr_key_version`, `qr_token_hash`. Ảnh tạo khi yêu cầu. Khóa cần khôi phục qua restart/restore; không ghi khóa vào database hoặc repository. Hash-only random token không đủ tái tạo.
- Vé không có trạng thái REFUNDED. Delivery lỗi không vô hiệu vé/khởi động hoàn; buyer được tải/gửi lại (FR-32). Không chuyển nhượng, check-out, hoàn tác hoặc check-in offline.

### 4.5 user-service

Owner: hồ sơ nghiệp vụ, application organizer, profile công khai và following. ERD/document map: `../diagrams/src/B12-05-user-document-map.puml`.

Collection: `users`, `organizer_applications`, `organizer_profiles`, `user_follows` trong `user_db`.

- Users liên kết bằng `identitySubject` duy nhất, không email. Không lưu password, refresh token, role như nguồn sự thật thứ hai hoặc field LEGACY_OPTIONAL.
- Application là nguồn của tên tổ chức/mô tả/kết quả duyệt; profile công khai là projection dẫn xuất theo application/version, chỉ ACTIVE được công khai. Không tự thêm API chỉnh profile độc lập.
- Một application/account theo FR-54; PENDING, ACTIVE, REJECTED; REJECTED bắt buộc lý do và không gửi lại. Quy tắc này không được sao chép sang cancellation request.
- ACTIVE chỉ sau Keycloak xác nhận role ORGANIZER, vẫn giữ BUYER. Có chỗ giữ operation ID/kết quả cấp quyền kỹ thuật để B13 xử lý lỗi từng phần; không tự thêm trạng thái nghiệp vụ hoặc Saga.
- Follow có ID riêng, unique(buyerSubject,organizerSubject), PUT/DELETE idempotent; đếm theo index organizerSubject, không lưu followerCount song song. Không thêm điều kiện ACTIVE riêng cho follow (BIZ-157/GOV-101).
- Validator kín (`additionalProperties: false`, khai cả `_id`), reject thiếu trường/field ngoài phạm vi. Không dựa vào transaction xuyên document nếu Mongo chạy standalone. Projection update cần retry/rebuild khi B13 chốt cơ chế.
- Logo/banner organizer không bị gỡ bởi GOV-091; “ảnh nhận diện nghĩa hẹp” chỉ nói avatar người dùng. Không biến logo/banner thành trường bắt buộc của application tối thiểu.

## 5. Ma trận bản sao và xử lý thiếu dữ liệu

| Nguồn → nơi dùng | Dữ liệu / mục đích | Quy tắc ứng viên |
|---|---|---|
| Event → Booking | Cấu hình, purchase limit, mode, windows, nguồn cung, promotion | Source ID + config version; thiếu cấu hình không chấp nhận giữ chỗ |
| Event → Payment | Kết thúc event, fee/approval version, cancellation | Fee snapshot bất biến; thiếu dữ kiện không mở paid |
| Event → Ticket | Organizer owner, cửa sổ vào cửa, cancellation | Owner không chuyển trong phạm vi; thiếu/sai owner từ chối check-in |
| Booking → Payment | Order/attempt expected amount, currency, expiry | So với snapshot đơn đã chốt; không lấy lại giá hiện tại |
| Booking/Payment → Ticket | Purchased lines, holder, xác nhận thu | Input bất biến; cùng order khác payload phải phát hiện |
| Booking/Payment → Event | Thống kê public/organizer | Read model có as_of; có thể trễ, không dùng để cấp nguồn cung/thu tiền |
| Keycloak → User | Subject, xác nhận role; hồ sơ được đảm bảo tồn tại | Upsert subject chống tạo đôi; lỗi cấp role cần phục hồi B13 |

Snapshot lưu source version và thời điểm nhận; không dùng received_at/TTL như bằng chứng đã nhận mọi cancellation. B13/B14-v0.1 đặc tả điểm hủy có hiệu lực, bản tin đảo thứ tự và version trùng khác payload; B12-OPEN-06 ghi quyết định đã xác nhận và giới hạn kiểm thực thi. Tránh vòng gọi đồng bộ. ID không tìm thấy được giữ làm tham chiếu chưa giải quyết; không tự tạo dữ kiện nghiệp vụ mặc định hoặc xóa lịch sử đơn/tiền/vé vì owner tạm không sẵn sàng.

## 6. Danh tính và baseline realm

Keycloak là nguồn danh tính và role; không FK/JOIN vào database nội bộ. Bộ khởi tạo ứng viên ở `data/keycloak/` dựng cấu hình tối thiểu mới, không sửa/export lại tệp nhạy cảm được cung cấp. Dùng một realm flash-ticket; Web/Android public client, Code + PKCE S256; giữ verifyEmail=false theo FR-50. Audience ứng viên flash-ticket-api được kiểm tại gateway và resource server; role + ownership vẫn kiểm trong service.

Chỉ user-service có client Admin API; quyền chi tiết chờ thử nghiệm trên phiên bản Keycloak đã pin. Baseline không cấp realm-admin/impersonation hoặc quyền Admin API chưa kiểm. Chưa có quyền phù hợp thì chức năng cấp organizer chưa vận hành; không ghi là đã hoàn thành. Không nhân bản service account có quyền quản trị sang năm service. Machine token không được tự qua endpoint buyer chỉ vì có role.

Realm fixture chỉ để kiểm cấu hình, không chứa người dùng hay credential. Client secret/signing key phải được tạo ở môi trường chạy. Các URL dùng placeholder theo môi trường; không HTTP public, wildcard origin hay IP cũ. Built-in theme trước; custom provider/listener chỉ được thêm theo contract được duyệt. Import lần đầu khác cập nhật realm đang tồn tại; không tự override realm.

Fixture tắt tự đăng ký (`registrationAllowed=false`) và reset password, không gán business role mặc định toàn realm. Đây là chặn sử dụng fixture chưa hoàn thiện, không phải thay yêu cầu FR-49: sản phẩm vẫn phải cho đăng ký và nhận BUYER mặc định. B13 cần kiểm cơ chế gán BUYER riêng cho người thật, quyền Admin API và SMTP trước khi bật các luồng tương ứng. FR-50 giữ nguyên `verifyEmail=false`, không social login. Chỉ định nghĩa ba role hiện hành BUYER/ORGANIZER/ADMIN (BIZ-146), không mang SUPER_ADMIN từ lịch sử sang.

## 7. Baseline, lớp bảo vệ và kiểm chứng

Các tệp dẫn xuất ở `data/README.md`, `data/postgres/`, `data/mongodb/`, `data/keycloak/`; đặc tả nghiệm thu ở `B12-validation.md`. Chỉ được chạy trên môi trường local thử nghiệm mới; không có lệnh apply tự động vào AWS hoặc datastore đang có.

| Lớp | Chứng minh được | Chưa chứng minh được |
|---|---|---|
| DDL/validator | Kiểu, NOT NULL, CHECK một dòng, uniqueness, FK nội bộ, grants | State machine, immutability theo vòng đời, tổng nhiều hàng, callback đúng nghiệp vụ |
| Transaction/service | Chốt/trả đúng một lần, chống vượt hạn mức, phát đủ vé, check-in cạnh tranh | Hội tụ xuyên service nếu thiếu hợp đồng/coordination |
| B13/B14 | Hợp đồng, idempotency xuyên biên, thứ tự, timeout, bù trừ, outbox/inbox | Tải/độ trễ chưa đo |
| B15/B16 | Kết quả kiểm thử thật, quan sát, masking, fault injection | Hiệu quả RCA nếu chưa có thực nghiệm riêng |

DDL thử nghiệm không seed dữ liệu vào baseline. Fixture test tách riêng và rollback hoặc dùng database mới. Init fail-fast trên database rỗng; không dùng IF NOT EXISTS để che sai khác schema cũ. Bootstrap database/roles tách khỏi transaction DDL từng service. Không có DROP/TRUNCATE hoặc migration dữ liệu cũ trong init.

Kiểm chéo tên bảng/ERD/owner; catalog không có liên kết xuyên owner; runtime không truy cập database khác; object mới có default privileges đúng; restart/restore giữ dữ liệu và QR đọc được khi khóa có mặt. Kiểm hành vi cạnh tranh và tích hợp được ghi là chưa chạy cho tới khi có service thật.

## 8. Phụ lục đối chiếu RCA và phép thử độc lập

Chỉ mục này dùng `../research-rca/R0-boi-canh-va-rang-buoc.md` (`R0-v0.5`) §3, ràng buộc 6, qua cửa `../project/lien-ket-rca.md`. Ràng buộc vẫn `CANDIDATE`, không sinh ra owner, database, bảng hay tên service.

Kết quả ứng viên: ma trận §2 giữ nguyên năm service ID đã duyệt tại GOV-094; database/schema/principal được ánh xạ về đúng ID. Việc sử dụng cùng ID trong log/resource attribute/metric thực tế chờ B13/B16; không tuyên bố kiểm runtime đạt ở B12.

Phép thử độc lập của bản nguồn: (1) thuộc bộ hệ thống; (2) nguồn hình thành là B5/B7/B8/B10/B11-C và yêu cầu thực hiện; (3) chỉ có nguồn RCA trong phụ lục này; (4) nguồn đó chỉ đối chiếu, không sinh hoặc sửa yêu cầu/bất biến/ranh giới. Đã kiểm lại cùng kết quả tại ngày duyệt 2026-09-14; nguồn R0 vẫn CANDIDATE, GOV-144 chỉ duyệt bộ hệ thống.

## 9. Quyết định đã đóng và phần còn phải kiểm

| ID giữ để truy vết | Kết quả hiện hành | Owner / điều kiện |
|---|---|---|
| B12-OPEN-01 | Đã xác nhận lịch sử/gửi lại/tối đa một PENDING | BIZ-153–156; nguồn B6/B7/B8 đã đồng bộ |
| B12-OPEN-02 | Đã xác nhận follow/unfollow đơn giản | BIZ-157/GOV-101; unique pair và quyền remove Mongo |
| B12-OPEN-03 | Đã xác nhận S-01–05 gồm ISSUING | GOV-131/136; API/sequence B13/B14; chưa chạy service |
| B12-OPEN-04 | VND nguyên, HALF_UP giảm/tổng đơn và phí/tổng event đã xác nhận | GOV-137/139/140; biểu diễn API chuỗi và chuẩn hóa mã là chi tiết kỹ thuật |
| B12-OPEN-05 | Init sạch, 4 business DB + KC DB, Mongo User đã xác nhận | PRJ-010/011, GOV-104–107; không tự phê duyệt toàn bộ schema |
| B12-OPEN-06 | Độ trễ hủy chấp nhận; tiền freeze/deadline đã chọn; Q-07 ngoài phạm vi | GOV-132–138, PRJ-024; B14 đặc tả retry/hội tụ; service tests NOT RUN |
| B12-OPEN-07 | OPEN về cấu hình thật và kiểm quyền danh tính/TLS/secret/backup | Nhóm triển khai; readiness ghi đầu vào, không yêu cầu chọn lại nghiệp vụ |
| B12-OPEN-08 | Đã bổ sung persistence và contract phối hợp | 47 bảng SQL/4 collection; thực thi crash/replay của service vẫn NOT RUN |

B12-v0.2 giữ DRAFT trong lúc upstream đồng bộ còn REVIEW_READY và chờ kiểm tích hợp; không có chuyện bỏ trống cột để tự đóng gate. Người duyệt gate là Minh. Lượt kiểm dữ liệu không chứng minh payment/issuance/check-in E2E hoặc chịu tải. Các trạng thái gate lịch sử B11 không bị viết lại.

### 9.1 Các lỗi rà lại đã sửa trong đợt 2026-09-14

1. Phiếu cũ còn hỏi lại Q-01–07 dù đã xác nhận; bỏ đề xuất email-only và bảng ngoại lệ đã bị thay thế.
2. Tiền còn scale 4, order chỉ TEXT không enum; đổi VND nguyên và enum đã chọn.
3. Seat chưa có tọa độ/type/ẩn; bổ sung và không trộn availability với hidden. geometry.shape phải là object theo B13, không phải string.
4. Thiếu cấu hình giá cục bộ và snapshot freeze đầy đủ: thêm SalesConfiguration có version trong Booking và frozen_purchase_snapshot trên đơn.
5. Thiếu nơi lưu phí/hủy trước khi đối soát: event_finance_snapshots của Payment, không khởi tạo payout giả chỉ để chứa cấu hình.
6. Thiếu durable Saga/outbox/inbox và HTTP retry identity; bổ sung trong đúng owner, không thêm service/Saga.
7. Script comment còn ghi thông tin lỗi thời và chưa được runner gọi; đồng bộ cả comment/runner/test.
8. Ghế ẩn và quyền xóa nháp khác nhau: trigger giới hạn xóa con của DRAFT, API vẫn kiểm ownership; không DELETE tiền/vé/lịch sử.

## 10. Tạo tác dẫn xuất và phần đưa vào báo cáo

- Năm ERD/document map trong `../diagrams/src/B12-01` tới `B12-05`.
- `data/`: SQL, Mongo validators/indexes, realm fixture sạch, hướng dẫn chạy và kiểm.
- `B12-validation.md`: ma trận INV/FR → test, phân biệt kết quả thật và kiểm thử tương lai.
- `B12-legacy-configuration-comparison.md`: COMPARISON riêng; bản cấu hình đã khử nhạy cảm và giới hạn bằng chứng. Ánh xạ bảng/cột legacy chỉ thực hiện sau khi thiết kế đủ ổn định, không bịa từ tên plan cũ.

Báo cáo sau duyệt dùng ownership, ERD, lý do chọn datastore, snapshot và bằng chứng kiểm chứng. Không dùng lịch sử bảng cũ làm lý do chia service; không đưa thông tin cá nhân, secret hoặc cấu hình AWS thật vào bản công khai.

## 11. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi |
|---|---|---|
| B12-v0.2 | 2026-09-14 | Đồng bộ quyết định đã xác nhận, hợp đồng B13/B14, init sạch 47 bảng và 4 collection; bổ sung dictionary đầy đủ; chưa tự duyệt gate |
| B12-v0.1 | 2026-09-07 | Mở DRAFT theo kế hoạch đã được yêu cầu thực hiện; tạo nguồn độc lập trước tài sản dẫn xuất, giữ các OPEN và giới hạn baseline |

## 12. Từ điển đầy đủ cột và ràng buộc SQL — B12-v0.2

**47 bảng SQL**: Event 12, Booking 15, Payment 13, Ticket 7; **4 collection Mongo** ở §13. Trong 47 bảng: 36 bảng nghiệp vụ baseline, 1 projection tài chính Event tại Payment, 8 bảng outbox/inbox, 1 bảng idempotency HTTP Booking, 1 bảng Saga Payment. Không đếm bảng nội bộ do Keycloak tự quản lý.

Đây là từ điển của init tạo mới, không migration. Mỗi dòng ghi đúng một cột. `NULL=Không` bao gồm cột thuộc PRIMARY KEY. `—` nghĩa không có default/ràng buộc riêng trong khai báo cột; FK/CHECK/UNIQUE nhiều cột ở cuối bảng. Kiểu domain: `money_amount` = NUMERIC(19,0), hữu hạn và ≥0; `currency_code` = TEXT chỉ VND; `nonempty_text` = TEXT không trắng. Snapshot JSON phải qua DTO B13; SQL chỉ kiểm phần khung, không tuyên bố kiểm đủ JSON business rules.

### event_schema.categories

Mục đích: nhãn phân loại dùng lại cho nhiều sự kiện (FR-64). Tách để tham chiếu thống nhất thay vì lặp tên; đổi lại có lookup/FK. Không suy thêm hệ thống taxonomy phân cấp hoặc nhiều category/event ngoài phạm vi hiện tại.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `name` | TEXT [không trắng] | Không | `NOT NULL` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

### event_schema.venues

Mục đích: địa điểm được nhiều sự kiện dùng lại (FR-63). Tách để tránh lặp dữ liệu địa điểm; đổi lại cần tham chiếu. Không lưu hoặc kiểm sức chứa vật lý: nguồn cung thương mại do organizer quyết định.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `name` | TEXT [không trắng] | Không | `NOT NULL` |
| `address` | TEXT [không trắng] | Không | `NOT NULL` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

### event_schema.events

Mục đích: sự kiện và cấu hình đơn trị: cửa sổ bán, sales mode, giới hạn mua, tỷ lệ phí. Không tách các cột cấu hình chỉ vì có tên khác. Không giữ tickets_sold làm nguồn sự thật thứ hai. Trạng thái/cấu hình khóa theo FR-07 cần service transaction; CHECK không tự bảo vệ chuyển trạng thái. B12 còn DRAFT; không suy thêm kiểm sức chứa venue.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `organizer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `category_id` | UUID | Có | `REFERENCES categories(id)` |
| `venue_id` | UUID | Có | `REFERENCES venues(id)` |
| `title` | TEXT [không trắng] | Không | `NOT NULL` |
| `description` | TEXT | Không | `NOT NULL DEFAULT ''` |
| `sales_mode` | TEXT | Không | `NOT NULL CHECK (sales_mode IN ('QUANTITY','SEAT_MAP'))` |
| `status` | TEXT | Không | `NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','PUBLISHED','CANCELLED'))` |
| `starts_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `sale_starts_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `sale_ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `checkin_starts_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `checkin_ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `purchase_limit` | INTEGER | Không | `NOT NULL CHECK (purchase_limit > 0)` |
| `fee_rate` | NUMERIC(9,8) | Có | `CHECK (fee_rate BETWEEN 0 AND 1)` |
| `approved_by` | TEXT [không trắng] | Có | — |
| `approved_at` | TIMESTAMPTZ | Có | — |
| `source_config_version` | BIGINT | Không | `NOT NULL DEFAULT 1 CHECK (source_config_version > 0)` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `updated_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `UNIQUE (id, sales_mode)`

- `CHECK (starts_at < ends_at AND sale_starts_at < sale_ends_at AND checkin_starts_at < checkin_ends_at)`

- `CHECK (sale_ends_at <= ends_at)`

- `CHECK (checkin_starts_at = starts_at AND checkin_ends_at = ends_at)`

- `CHECK ((approved_by IS NULL AND approved_at IS NULL AND fee_rate IS NULL) OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND fee_rate IS NOT NULL))`

- `CHECK (status NOT IN ('APPROVED','PUBLISHED') OR approved_at IS NOT NULL)`

- Index: `CREATE INDEX events_organizer_idx ON events (organizer_subject, created_at);`

- Index: `CREATE INDEX events_public_idx ON events (starts_at, id) WHERE status = 'PUBLISHED';`

- Index: `CREATE INDEX events_category_idx ON events (category_id);`

- Index: `CREATE INDEX events_venue_idx ON events (venue_id);`

### event_schema.event_images

Mục đích: một sự kiện có tập ảnh và thứ tự hiển thị. Tách tập phần tử lặp thay vì thêm image1/image2; đổi lại có bảng con và truy vấn tập ảnh. Lưu tham chiếu object, không nhúng binary ảnh hoặc secret truy cập.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES events(id)` |
| `object_reference` | TEXT [không trắng] | Không | `NOT NULL` |
| `display_order` | INTEGER | Không | `NOT NULL CHECK (display_order >= 0)` |

- `UNIQUE (event_id, display_order)`

### event_schema.event_layouts

Một layout/event với UNIQUE(event_id); tách dữ liệu editor khỏi events để không kéo hình học vào đường đọc thương mại. Trade-off: thêm lookup, JSON phải theo form B13. Có canvas/ảnh nền/decorations, không clone source hoặc lịch sử template.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES events(id)` |
| `name` | TEXT [không trắng] | Không | `NOT NULL` |
| `canvas_width` | INTEGER | Không | `NOT NULL CHECK (canvas_width > 0)` |
| `canvas_height` | INTEGER | Không | `NOT NULL CHECK (canvas_height > 0)` |
| `background_image_reference` | TEXT | Có | — |
| `layout_data` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(layout_data) = 'object' AND layout_data @> '{"schemaVersion":1}' AND jsonb_typeof(layout_data->'decorations') = 'array' AND layout_data ? 'decorations')` |

- `UNIQUE (id, event_id)`

- `UNIQUE (event_id)`

- Index: `CREATE INDEX layouts_event_idx ON event_layouts(event_id);`

### event_schema.event_sectors

Nhiều sector/event, SEATED/STANDING, capacity cấu hình và geometry có kiểu. Trade-off: bảng con/FK và adapter renderer; configured_capacity không phải counter bán của Booking.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL` |
| `layout_id` | UUID | Không | `NOT NULL` |
| `name` | TEXT [không trắng] | Không | `NOT NULL` |
| `sector_type` | TEXT | Không | `NOT NULL CHECK (sector_type IN ('SEATED','STANDING'))` |
| `configured_capacity` | BIGINT | Không | `NOT NULL CHECK (configured_capacity >= 0)` |
| `code` | TEXT | Có | — |
| `display_order` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (display_order >= 0)` |
| `color_code` | VARCHAR(7) | Có | `CHECK (color_code ~ '^#[0-9A-Fa-f]{6}$')` |
| `geometry` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(geometry) = 'object' AND geometry @> '{"schemaVersion":1}' AND geometry ? 'shape' AND jsonb_typeof(geometry->'shape') = 'object')` |

- `FOREIGN KEY (layout_id, event_id) REFERENCES event_layouts(id, event_id)`

- `UNIQUE (id, event_id)`

- `UNIQUE (id, event_id, sector_type)`

- Index: `CREATE INDEX sectors_layout_idx ON event_sectors(layout_id, event_id);`

### event_schema.event_seats

Ghế cấu hình có stable ID, hàng/số/tọa độ/type/is_hidden. Ẩn vẫn giữ type và không bán; xóa là thao tác khác. Trade-off: nhiều dòng, cần lưu trọn bản nháp; không giữ trạng thái reservation của Booking.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL` |
| `sector_id` | UUID | Không | `NOT NULL` |
| `sector_type` | TEXT | Không | `NOT NULL DEFAULT 'SEATED' CHECK (sector_type = 'SEATED')` |
| `seat_label` | TEXT [không trắng] | Không | `NOT NULL` |
| `row_name` | TEXT [không trắng] | Không | `NOT NULL` |
| `seat_number` | TEXT [không trắng] | Không | `NOT NULL` |
| `coord_x` | NUMERIC(10,2) | Không | `NOT NULL CHECK (coord_x <> 'NaN'::numeric)` |
| `coord_y` | NUMERIC(10,2) | Không | `NOT NULL CHECK (coord_y <> 'NaN'::numeric)` |
| `ticket_type_id` | UUID | Có | — |
| `is_hidden` | BOOLEAN | Không | `NOT NULL DEFAULT false` |

- `FOREIGN KEY (sector_id, event_id, sector_type) REFERENCES event_sectors(id, event_id, sector_type)`

- `UNIQUE (sector_id, seat_label)`

- `UNIQUE (id, event_id, sector_id)`

- Bổ sung sau khai bảng: `seats_ticket_type_same_sector_fk FOREIGN KEY (ticket_type_id, event_id, sector_id) REFERENCES ticket_types(id, event_id, sector_id)`.

### event_schema.ticket_types

Mục đích: các loại vé và giá cấu hình của sự kiện (FR-02/05). Tách tập nhiều loại vé thay vì lặp cột trong events. Counter bán thuộc Booking; giá dòng đơn giữ snapshot để không bị thay bởi giá hiện tại. Mapping loại vé với ghế cụ thể còn cần hoàn thiện hợp đồng.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL` |
| `name` | TEXT [không trắng] | Không | `NOT NULL` |
| `sales_mode` | TEXT | Không | `NOT NULL` |
| `sector_id` | UUID | Có | — |
| `color_code` | VARCHAR(7) | Có | `CHECK (color_code ~ '^#[0-9A-Fa-f]{6}$')` |
| `unit_price` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL CHECK (unit_price > 0)` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `configured_quantity` | BIGINT | Có | `CHECK (configured_quantity >= 0)` |

- `FOREIGN KEY (event_id, sales_mode) REFERENCES events(id, sales_mode)`

- `FOREIGN KEY (sector_id, event_id) REFERENCES event_sectors(id, event_id)`

- `CHECK ((sales_mode = 'QUANTITY' AND sector_id IS NULL AND configured_quantity IS NOT NULL) OR (sales_mode = 'SEAT_MAP' AND sector_id IS NOT NULL AND configured_quantity IS NULL))`

- `UNIQUE (id, event_id)`

- `UNIQUE (id, event_id, sector_id)`

- Index: `CREATE INDEX ticket_types_event_idx ON ticket_types(event_id);`

### event_schema.promotions

Nhiều mã/event; code trim-uppercase duy nhất, fixed VND nguyên hoặc integer percent; tự hiệu lực theo thời gian. Trade-off: Booking cần bản sao version; không thêm bật/tắt hay maximum discount cap.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES events(id)` |
| `code` | TEXT [không trắng] | Không | `NOT NULL` |
| `starts_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `discount_kind` | TEXT | Không | `NOT NULL CHECK (discount_kind IN ('FIXED','PERCENT'))` |
| `fixed_discount` | NUMERIC(19,0) [money_amount] | Có | — |
| `currency` | TEXT [VND] | Có | — |
| `discount_rate` | NUMERIC(9,8) | Có | — |
| `maximum_uses` | BIGINT | Không | `NOT NULL CHECK (maximum_uses > 0)` |
| `maximum_uses_per_user` | INTEGER | Không | `NOT NULL DEFAULT 1 CHECK (maximum_uses_per_user = 1)` |

- `CHECK (starts_at < ends_at)`

- `CHECK (code = upper(btrim(code)))`

- `CHECK (discount_rate IS NULL OR discount_rate * 100 = trunc(discount_rate * 100))`

- `CHECK ((discount_kind = 'FIXED' AND fixed_discount IS NOT NULL AND currency IS NOT NULL AND discount_rate IS NULL) OR (discount_kind = 'PERCENT' AND fixed_discount IS NULL AND currency IS NULL AND discount_rate IS NOT NULL AND discount_rate > 0 AND discount_rate <= 1))`

- `UNIQUE (id, event_id)`

- `UNIQUE (event_id, code)`

- Index: `CREATE INDEX promotions_event_idx ON promotions(event_id, starts_at, ends_at);`

### event_schema.event_cancellation_requests

Lịch sử yêu cầu hủy và kết quả trên cùng bản ghi; một PENDING/event bằng partial unique. Gửi lại sau REJECTED; không gửi mới khi CANCELLED (service guard). Trade-off: thêm bảng để không ghi đè lịch sử, không thêm bảng kết quả riêng.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES events(id)` |
| `requested_by` | TEXT [không trắng] | Không | `NOT NULL` |
| `requested_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `reason` | TEXT [không trắng] | Không | `NOT NULL` |
| `status` | TEXT | Không | `NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED'))` |
| `reviewed_by` | TEXT [không trắng] | Có | — |
| `reviewed_at` | TIMESTAMPTZ | Có | — |
| `review_reason` | TEXT [không trắng] | Có | — |

- `CHECK ((status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL) OR (status <> 'PENDING' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))`

- Index: `CREATE INDEX cancellation_event_idx ON event_cancellation_requests(event_id, requested_at);`

- Index: `CREATE UNIQUE INDEX cancellation_one_pending_uq ON event_cancellation_requests(event_id) WHERE status = 'PENDING';`

### event_schema.outbox_messages

Business update and outgoing event/command commit together. Trade-off: relay and duplicate delivery; published_at only after broker confirm. Payload follows B13 named versioned contract, not arbitrary business JSON.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `message_id` | UUID | Không | `PRIMARY KEY` |
| `aggregate_id` | UUID | Không | `NOT NULL` |
| `aggregate_version` | BIGINT | Không | `NOT NULL CHECK (aggregate_version > 0)` |
| `message_type` | TEXT [không trắng] | Không | `NOT NULL` |
| `schema_version` | INTEGER | Không | `NOT NULL DEFAULT 1 CHECK (schema_version = 1)` |
| `payload` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(payload) = 'object')` |
| `occurred_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `correlation_id` | UUID | Không | `NOT NULL` |
| `causation_id` | UUID | Có | — |
| `traceparent` | TEXT | Có | — |
| `published_at` | TIMESTAMPTZ | Có | — |
| `attempts` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (attempts >= 0)` |
| `next_attempt_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `last_error_code` | TEXT | Có | — |

- Index: `CREATE INDEX outbox_pending_idx ON outbox_messages(next_attempt_at, occurred_at) WHERE published_at IS NULL;`

### event_schema.inbox_messages

Deduplication by consumer and message ID in same transaction as effect and response outbox. Compare payload hash on replay; different hash rejects. Trade-off: durable rows/retention; not exactly-once network delivery.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `consumer_name` | TEXT [không trắng] | Không | `NOT NULL` |
| `message_id` | UUID | Không | `NOT NULL` |
| `payload_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `processed_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `response_payload` | JSONB | Có | `CHECK (jsonb_typeof(response_payload) = 'object')` |

- `PRIMARY KEY (consumer_name, message_id)`

### booking_schema.event_sales_snapshots

Mục đích: Booking có cấu hình Event cần cho quyết định giữ chỗ, không JOIN database Event hoặc gọi Event mỗi lần. Trade-off: giảm phụ thuộc runtime nhưng thêm dữ liệu sao chép và nguy cơ trễ. Source version nhận diện cấu hình, không phải khóa cộng tác editor; TTL/received_at không chứng minh đã nhận hủy mới nhất. Thiếu cấu hình hợp lệ thì không nhận giữ chỗ. B13 phải chốt cập nhật và điểm hủy hiệu lực; không reset counter bán khi nhận lại snapshot.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `event_id` | UUID | Không | `PRIMARY KEY` |
| `organizer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `sales_mode` | TEXT | Không | `NOT NULL CHECK (sales_mode IN ('QUANTITY','SEAT_MAP'))` |
| `event_status` | TEXT [không trắng] | Không | `NOT NULL` |
| `sale_starts_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `sale_ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `purchase_limit` | INTEGER | Không | `NOT NULL CHECK (purchase_limit > 0)` |
| `configuration_snapshot` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(configuration_snapshot) = 'object' AND configuration_snapshot @> '{"schemaVersion":1}')` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `received_at` | TIMESTAMPTZ | Không | `NOT NULL` |

- `CHECK (sale_starts_at < sale_ends_at)`

- `UNIQUE (event_id, sales_mode)`

### booking_schema.orders

Một giao dịch mua, email theo lần mua, S-01 lifecycle riêng tiền/vé. Freeze lưu nguyên PurchaseSnapshot trước attempt đầu, retry không dựng lại từ giá hiện tại. Trade-off: dữ liệu sao chép; tổng items, state transition, deadline cần transaction.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL` |
| `buyer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `sales_mode` | TEXT | Không | `NOT NULL` |
| `sector_id` | UUID | Có | — |
| `lifecycle_state` | TEXT | Không | `NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK (lifecycle_state IN ('PENDING_PAYMENT','ISSUING','COMPLETED','CANCELLED','EXPIRED','ISSUANCE_FAILED'))` |
| `customer_email` | TEXT [không trắng] | Không | `NOT NULL CHECK (customer_email ~ '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$')` |
| `payment_frozen_at` | TIMESTAMPTZ | Có | — |
| `payment_snapshot_version` | BIGINT | Có | `CHECK (payment_snapshot_version > 0)` |
| `frozen_purchase_snapshot` | JSONB | Có | `CHECK (jsonb_typeof(frozen_purchase_snapshot) = 'object' AND frozen_purchase_snapshot @> '{"schemaVersion":1}')` |
| `accepted_charge_id` | UUID | Có | `UNIQUE` |
| `accepted_at` | TIMESTAMPTZ | Có | — |
| `subtotal` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `discount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL DEFAULT 0` |
| `total` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL CHECK (total > 0)` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `expires_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `updated_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `FOREIGN KEY (event_id, sales_mode) REFERENCES event_sales_snapshots(event_id, sales_mode)`

- `CHECK ((sales_mode = 'QUANTITY' AND sector_id IS NULL) OR (sales_mode = 'SEAT_MAP' AND sector_id IS NOT NULL))`

- `CHECK (discount <= subtotal AND total = subtotal - discount)`

- `CHECK (created_at < expires_at)`

- `CHECK ((payment_frozen_at IS NULL) = (payment_snapshot_version IS NULL))`

- `CHECK ((payment_frozen_at IS NULL) = (frozen_purchase_snapshot IS NULL))`

- `CHECK (payment_frozen_at IS NULL OR (created_at <= payment_frozen_at AND payment_frozen_at < expires_at))`

- `CHECK ((accepted_charge_id IS NULL) = (accepted_at IS NULL))`

- `CHECK (accepted_at IS NULL OR (payment_frozen_at IS NOT NULL AND accepted_at < expires_at))`

- `CHECK (lifecycle_state NOT IN ('ISSUING','COMPLETED','ISSUANCE_FAILED') OR accepted_charge_id IS NOT NULL)`

- `UNIQUE (id, event_id)`

- `UNIQUE (id, event_id, buyer_subject)`

- `UNIQUE (id, event_id, sector_id)`

- `UNIQUE (id, currency)`

- `UNIQUE (id, sales_mode)`

- `UNIQUE (id, expires_at)`

- Bổ sung sau khai bảng: `orders_sector_fk FOREIGN KEY (sector_id, event_id) REFERENCES sector_inventory(sector_id, event_id)`.

- Index: `CREATE INDEX orders_buyer_idx ON orders(buyer_subject, created_at);`

- Index: `CREATE INDEX orders_event_idx ON orders(event_id, created_at);`

- Index: `CREATE INDEX orders_expiry_idx ON orders(expires_at);`

### booking_schema.order_items

Mục đích: nhiều dòng lựa chọn trong một đơn; giữ quantity và giá tại lúc mua để lịch sử không đổi theo cấu hình hiện tại. Trade-off: lặp tên/giá có chủ đích và phải kiểm tổng dòng với đơn. Sau tạo đơn không đổi lựa chọn (FR-20). selection_snapshot là dữ liệu bổ trợ có form cố định, không thay cột số tiền có kiểm tra.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `order_id` | UUID | Không | `NOT NULL` |
| `event_id` | UUID | Không | `NOT NULL` |
| `ticket_type_id` | UUID | Không | `NOT NULL` |
| `sector_id` | UUID | Có | — |
| `sales_mode` | TEXT | Không | `NOT NULL` |
| `ticket_type_name` | TEXT [không trắng] | Không | `NOT NULL` |
| `quantity` | INTEGER | Không | `NOT NULL CHECK (quantity > 0)` |
| `unit_price` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL CHECK (unit_price > 0)` |
| `line_subtotal` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `selection_snapshot` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(selection_snapshot) = 'object' AND selection_snapshot @> '{"schemaVersion":1}')` |

- `FOREIGN KEY (order_id, event_id) REFERENCES orders(id, event_id)`

- `FOREIGN KEY (order_id, event_id, sector_id) REFERENCES orders(id, event_id, sector_id)`

- `FOREIGN KEY (order_id, currency) REFERENCES orders(id, currency)`

- `FOREIGN KEY (order_id, sales_mode) REFERENCES orders(id, sales_mode)`

- `CHECK ((sales_mode = 'QUANTITY' AND sector_id IS NULL) OR (sales_mode = 'SEAT_MAP' AND sector_id IS NOT NULL))`

- `CHECK (line_subtotal = quantity * unit_price)`

- `UNIQUE (id, order_id, event_id)`

- `UNIQUE (order_id, ticket_type_id)`

### booking_schema.reservations

Mục đích: nhận diện chính xác một lượt giữ của đơn, trạng thái và hạn chung để chốt/trả đúng lượt. UNIQUE(order_id) không có nghĩa bỏ định danh giữ chỗ hoặc gộp root đã mô hình hóa. Trade-off: thêm dòng/quan hệ nhưng giảm lẫn lệnh release cũ với lượt mới; tạo đơn và giữ chỗ cùng transaction Booking.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `order_id` | UUID | Không | `NOT NULL UNIQUE` |
| `event_id` | UUID | Không | `NOT NULL` |
| `buyer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('HELD','COMMITTED','RELEASED'))` |
| `expires_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `FOREIGN KEY (order_id, event_id, buyer_subject) REFERENCES orders(id, event_id, buyer_subject)`

- `FOREIGN KEY (order_id, expires_at) REFERENCES orders(id, expires_at)`

- `UNIQUE (id, event_id)`

- `UNIQUE (id, order_id, event_id)`

- `UNIQUE (id, event_id, buyer_subject)`

- `UNIQUE (id, expires_at)`

- Index: `CREATE INDEX reservations_expiry_idx ON reservations(expires_at) WHERE state = 'HELD';`

### booking_schema.ticket_type_inventory

Mục đích: nguồn cung theo loại vé cho QUANTITY. Counter giúp cập nhật có điều kiện tại điểm tranh chấp thay vì COUNT nhiều đơn; đổi lại phải cập nhật held/purchased cùng giữ chỗ và kiểm đối chiếu. Không dùng counter này để trừ thêm lần nữa cho ghế SEATED hoặc pool STANDING đã có nguồn cung riêng.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `ticket_type_id` | UUID | Không | `PRIMARY KEY` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES event_sales_snapshots(event_id)` |
| `configured_capacity` | BIGINT | Không | `NOT NULL CHECK (configured_capacity >= 0)` |
| `held` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (held >= 0)` |
| `purchased` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (purchased >= 0)` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `CHECK (purchased <= configured_capacity AND held <= configured_capacity - purchased)`

- `UNIQUE (ticket_type_id, event_id)`

### booking_schema.sector_inventory

Mục đích: pool dùng chung của sector STANDING, tránh bán vượt khi nhiều loại vé cùng dùng một khu. Trade-off: một điểm khóa nóng/sector và counter cần bảo toàn. Với SEATED, capacity chỉ là giới hạn cấu hình; seat rows sở hữu khả dụng, không trừ đồng thời counter sector và từng ghế.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `sector_id` | UUID | Không | `PRIMARY KEY` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES event_sales_snapshots(event_id)` |
| `sector_type` | TEXT | Không | `NOT NULL CHECK (sector_type IN ('SEATED','STANDING'))` |
| `configured_capacity` | BIGINT | Không | `NOT NULL CHECK (configured_capacity >= 0)` |
| `held` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (held >= 0)` |
| `purchased` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (purchased >= 0)` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `CHECK (purchased <= configured_capacity AND held <= configured_capacity - purchased)`

- `UNIQUE (sector_id, event_id)`

- `UNIQUE (sector_id, event_id, sector_type)`

### booking_schema.seat_inventory

Mục đích: trạng thái bán hiện tại cho từng ghế, do Booking sở hữu; Event chỉ sở hữu cấu hình. Cập nhật có điều kiện theo ghế và reservation/generation ngăn nhận hai lần hoặc lệnh trả cũ trả ghế của lượt mới. Trade-off: số dòng lớn và bản sao định danh; không coi cache hoặc hình màu trên FE là bằng chứng ghế đã giữ.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `seat_id` | UUID | Không | `PRIMARY KEY` |
| `event_id` | UUID | Không | `NOT NULL` |
| `sector_id` | UUID | Không | `NOT NULL` |
| `ticket_type_id` | UUID | Có | — |
| `is_hidden` | BOOLEAN | Không | `NOT NULL DEFAULT false` |
| `sector_type` | TEXT | Không | `NOT NULL DEFAULT 'SEATED' CHECK (sector_type = 'SEATED')` |
| `seat_label` | TEXT [không trắng] | Không | `NOT NULL` |
| `state` | TEXT | Không | `NOT NULL DEFAULT 'AVAILABLE' CHECK (state IN ('AVAILABLE','HELD','PURCHASED','UNAVAILABLE'))` |
| `current_reservation_id` | UUID | Có | — |
| `reservation_generation` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (reservation_generation >= 0)` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `FOREIGN KEY (sector_id, event_id, sector_type) REFERENCES sector_inventory(sector_id, event_id, sector_type)`

- `FOREIGN KEY (current_reservation_id, event_id) REFERENCES reservations(id, event_id)`

- `CHECK ((state IN ('HELD','PURCHASED') AND current_reservation_id IS NOT NULL AND reservation_generation > 0) OR (state IN ('AVAILABLE','UNAVAILABLE') AND current_reservation_id IS NULL))`

- `CHECK (NOT is_hidden OR state = 'UNAVAILABLE')`

- `CHECK (state = 'UNAVAILABLE' OR ticket_type_id IS NOT NULL)`

- `UNIQUE (seat_id, event_id)`

- `UNIQUE (seat_id, event_id, sector_id)`

- `UNIQUE (sector_id, seat_label)`

- Index: `CREATE INDEX seats_reservation_idx ON seat_inventory(current_reservation_id) WHERE current_reservation_id IS NOT NULL;`

### booking_schema.order_item_seats

Mục đích: tập ghế thuộc từng dòng đơn; một cột seat_id không đủ khi quantity lớn hơn một. Quan hệ riêng cho phép FK và chống lặp ghế trong cùng đơn. Đổi lại thêm bảng nối; còn phải kiểm số ghế, loại vé và sector phù hợp trong transaction.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `order_item_id` | UUID | Không | `NOT NULL` |
| `order_id` | UUID | Không | `NOT NULL` |
| `event_id` | UUID | Không | `NOT NULL` |
| `sector_id` | UUID | Không | `NOT NULL` |
| `seat_id` | UUID | Không | `NOT NULL` |

- `PRIMARY KEY (order_item_id, seat_id)`

- `UNIQUE (order_id, seat_id)`

- `FOREIGN KEY (order_item_id, order_id, event_id) REFERENCES order_items(id, order_id, event_id)`

- `FOREIGN KEY (order_id, event_id, sector_id) REFERENCES orders(id, event_id, sector_id)`

- `FOREIGN KEY (seat_id, event_id, sector_id) REFERENCES seat_inventory(seat_id, event_id, sector_id)`

### booking_schema.reservation_allocations

Mục đích: ghi lượt giữ đã chiếm nguồn cung nào, bao nhiêu, để chốt/trả đúng tài nguyên thay vì suy từ cấu hình đang đổi. Trade-off: dữ liệu có phần trùng dòng đơn, cần kiểm tổng và mode. FK chỉ bảo vệ liên kết cục bộ; đối chiếu resource với item, tổng allocations và trả đúng một lần cần transaction/service, chưa được chứng minh bằng DDL.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `reservation_id` | UUID | Không | `NOT NULL` |
| `order_id` | UUID | Không | `NOT NULL` |
| `order_item_id` | UUID | Không | `NOT NULL` |
| `event_id` | UUID | Không | `NOT NULL` |
| `resource_kind` | TEXT | Không | `NOT NULL CHECK (resource_kind IN ('TICKET_TYPE','STANDING_SECTOR','SEAT'))` |
| `ticket_type_id` | UUID | Có | — |
| `sector_id` | UUID | Có | — |
| `seat_id` | UUID | Có | — |
| `quantity` | INTEGER | Không | `NOT NULL CHECK (quantity > 0)` |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('HELD','COMMITTED','RELEASED'))` |

- `FOREIGN KEY (reservation_id, order_id, event_id) REFERENCES reservations(id, order_id, event_id)`

- `FOREIGN KEY (order_item_id, order_id, event_id) REFERENCES order_items(id, order_id, event_id)`

- `FOREIGN KEY (ticket_type_id, event_id) REFERENCES ticket_type_inventory(ticket_type_id, event_id)`

- `FOREIGN KEY (sector_id, event_id) REFERENCES sector_inventory(sector_id, event_id)`

- `FOREIGN KEY (seat_id, event_id) REFERENCES seat_inventory(seat_id, event_id)`

- `CHECK ((resource_kind = 'TICKET_TYPE' AND ticket_type_id IS NOT NULL AND sector_id IS NULL AND seat_id IS NULL) OR (resource_kind = 'STANDING_SECTOR' AND ticket_type_id IS NULL AND sector_id IS NOT NULL AND seat_id IS NULL) OR (resource_kind = 'SEAT' AND ticket_type_id IS NULL AND sector_id IS NULL AND seat_id IS NOT NULL AND quantity = 1))`

- Index: `CREATE UNIQUE INDEX allocations_ticket_type_uq ON reservation_allocations(reservation_id, order_item_id, ticket_type_id) WHERE resource_kind = 'TICKET_TYPE';`

- Index: `CREATE UNIQUE INDEX allocations_sector_uq ON reservation_allocations(reservation_id, order_item_id, sector_id) WHERE resource_kind = 'STANDING_SECTOR';`

- Index: `CREATE UNIQUE INDEX allocations_seat_uq ON reservation_allocations(reservation_id, seat_id) WHERE resource_kind = 'SEAT';`

- Index: `CREATE INDEX allocations_item_idx ON reservation_allocations(order_item_id, order_id, event_id);`

### booking_schema.purchase_limits

Mục đích: trạng thái theo từng buyer/event, khác events.purchase_limit là quy tắc chung. Ví dụ hạn mức 5, đã dùng 4: hai request cùng COUNT=4 rồi mỗi request thêm 1 có thể thành 6. Một dòng (event_id,buyer_subject) tạo điểm cập nhật có điều kiện/khóa cục bộ để tuần tự hóa, cùng transaction giữ chỗ và nguồn cung. Chỉ có bảng hoặc CHECK chưa tự ngăn mọi race. Trade-off: đọc/ghi gọn hơn tính tổng mỗi lần nhưng held/purchased là dữ liệu dư thừa; phải chốt/trả đúng một lần và có cách kiểm đối chiếu. Không nhằm chống một người dùng nhiều tài khoản. GOV-108, FR-19, INV-04.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `event_id` | UUID | Không | `NOT NULL REFERENCES event_sales_snapshots(event_id)` |
| `buyer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `configured_limit` | INTEGER | Không | `NOT NULL CHECK (configured_limit > 0)` |
| `held` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (held >= 0)` |
| `purchased` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (purchased >= 0)` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `PRIMARY KEY (event_id, buyer_subject)`

- `CHECK (purchased <= configured_limit AND held <= configured_limit - purchased)`

### booking_schema.promotion_inventory

Mục đích: Booking giữ tổng lượt khả dụng của mã và bản sao điều kiện để áp mã cùng transaction giữ lượt. Trade-off: giảm gọi Event nhưng có counter dư thừa và bản sao cần version; không reset held/used khi replay cấu hình. configuration_snapshot cần form cố định, không chỉ kiểm object.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `promotion_id` | UUID | Không | `PRIMARY KEY` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES event_sales_snapshots(event_id)` |
| `configured_limit` | BIGINT | Không | `NOT NULL CHECK (configured_limit > 0)` |
| `per_user_limit` | INTEGER | Không | `NOT NULL DEFAULT 1 CHECK (per_user_limit = 1)` |
| `held` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (held >= 0)` |
| `used` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (used >= 0)` |
| `starts_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `configuration_snapshot` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(configuration_snapshot) = 'object' AND configuration_snapshot @> '{"schemaVersion":1}')` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `CHECK (starts_at < ends_at)`

- `CHECK (used <= configured_limit AND held <= configured_limit - used)`

- `UNIQUE (promotion_id, event_id)`

### booking_schema.promotion_usages

Mục đích: xác định buyer/reservation nào đã giữ/dùng/trả mã, thực thi giới hạn một lần/tài khoản và chống lệnh trả cũ. Chỉ counter tổng không biết ai đã dùng. Trade-off: thêm lịch sử RELEASED và chỉ mục; cần transaction với promotion_inventory. Không tạo yêu cầu audit mới cho mọi lần buyer bị từ chối.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `promotion_id` | UUID | Không | `NOT NULL` |
| `event_id` | UUID | Không | `NOT NULL` |
| `reservation_id` | UUID | Không | `NOT NULL` |
| `buyer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `generation` | BIGINT | Không | `NOT NULL CHECK (generation > 0)` |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('HELD','USED','RELEASED'))` |
| `discount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `expires_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `FOREIGN KEY (promotion_id, event_id) REFERENCES promotion_inventory(promotion_id, event_id)`

- `FOREIGN KEY (reservation_id, event_id, buyer_subject) REFERENCES reservations(id, event_id, buyer_subject)`

- `FOREIGN KEY (reservation_id, expires_at) REFERENCES reservations(id, expires_at)`

- `UNIQUE (reservation_id, generation)`

- Index: `CREATE UNIQUE INDEX promotion_one_active_per_reservation_uq ON promotion_usages(reservation_id) WHERE state IN ('HELD','USED');`

- Index: `CREATE UNIQUE INDEX promotion_one_active_per_buyer_uq ON promotion_usages(promotion_id, buyer_subject) WHERE state IN ('HELD','USED');`

- Index: `CREATE INDEX promotion_usage_buyer_idx ON promotion_usages(promotion_id, buyer_subject, state);`

### booking_schema.idempotency_requests

Create-order replay returns original order rather than creating another hold. Record success in order transaction; conflicting payload returns conflict. Trade-off: storage and retention, not a replacement for reservation state guards.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `actor_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `operation` | TEXT [không trắng] | Không | `NOT NULL` |
| `request_key` | UUID | Không | `NOT NULL` |
| `request_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `resource_id` | UUID | Không | `NOT NULL` |
| `response_status` | INTEGER | Không | `NOT NULL CHECK (response_status BETWEEN 200 AND 299)` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `PRIMARY KEY (actor_subject, operation, request_key)`

### booking_schema.outbox_messages

Business update and outgoing event/command commit together. Trade-off: relay and duplicate delivery; published_at only after broker confirm. Payload follows B13 named versioned contract, not arbitrary business JSON.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `message_id` | UUID | Không | `PRIMARY KEY` |
| `aggregate_id` | UUID | Không | `NOT NULL` |
| `aggregate_version` | BIGINT | Không | `NOT NULL CHECK (aggregate_version > 0)` |
| `message_type` | TEXT [không trắng] | Không | `NOT NULL` |
| `schema_version` | INTEGER | Không | `NOT NULL DEFAULT 1 CHECK (schema_version = 1)` |
| `payload` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(payload) = 'object')` |
| `occurred_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `correlation_id` | UUID | Không | `NOT NULL` |
| `causation_id` | UUID | Có | — |
| `traceparent` | TEXT | Có | — |
| `published_at` | TIMESTAMPTZ | Có | — |
| `attempts` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (attempts >= 0)` |
| `next_attempt_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `last_error_code` | TEXT | Có | — |

- Index: `CREATE INDEX outbox_pending_idx ON outbox_messages(next_attempt_at, occurred_at) WHERE published_at IS NULL;`

### booking_schema.inbox_messages

Deduplication by consumer and message ID in same transaction as effect and response outbox. Compare payload hash on replay; different hash rejects. Trade-off: durable rows/retention; not exactly-once network delivery.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `consumer_name` | TEXT [không trắng] | Không | `NOT NULL` |
| `message_id` | UUID | Không | `NOT NULL` |
| `payload_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `processed_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `response_payload` | JSONB | Có | `CHECK (jsonb_typeof(response_payload) = 'object')` |

- `PRIMARY KEY (consumer_name, message_id)`

### payment_schema.event_finance_snapshots

Local immutable fee configuration and monotonic cancellation marker, even when cancellation arrives before finance configuration. Avoids Event DB joins and creating a payout before reconciliation. Trade-off: one small projection per event; missing fee blocks payout, never defaults to zero.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `event_id` | UUID | Không | `PRIMARY KEY` |
| `organizer_subject` | TEXT [không trắng] | Có | — |
| `event_ends_at` | TIMESTAMPTZ | Có | — |
| `fee_rate` | NUMERIC(9,8) | Có | `CHECK (fee_rate BETWEEN 0 AND 1)` |
| `source_fee_version` | BIGINT | Có | `CHECK (source_fee_version > 0)` |
| `cancellation_id` | UUID | Có | `UNIQUE` |
| `cancelled_at` | TIMESTAMPTZ | Có | — |
| `received_at` | TIMESTAMPTZ | Không | `NOT NULL` |

- `CHECK ((cancellation_id IS NULL) = (cancelled_at IS NULL))`

- `CHECK ((organizer_subject IS NULL AND event_ends_at IS NULL AND fee_rate IS NULL AND source_fee_version IS NULL AND cancellation_id IS NOT NULL) OR (organizer_subject IS NOT NULL AND event_ends_at IS NOT NULL AND fee_rate IS NOT NULL AND source_fee_version IS NOT NULL))`

### payment_schema.order_payment_snapshots

Bản mua đóng băng từ lần bắt đầu thanh toán đầu tiên, lưu tại Payment để nhận callback không JOIN Booking. Trade-off: dữ liệu dư và phải kiểm hash/version; giảm phụ thuộc đồng bộ, không là backup hoặc owner thứ hai của đơn.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `order_id` | UUID | Không | `PRIMARY KEY` |
| `event_id` | UUID | Không | `NOT NULL` |
| `buyer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `expected_amount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL CHECK (expected_amount > 0)` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `expires_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `source_order_version` | BIGINT | Không | `NOT NULL CHECK (source_order_version > 0)` |
| `purchase_snapshot` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(purchase_snapshot) = 'object' AND purchase_snapshot @> '{"schemaVersion":1}' AND purchase_snapshot ? 'items' AND jsonb_typeof(purchase_snapshot->'items') = 'array')` |
| `snapshot_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `received_at` | TIMESTAMPTZ | Không | `NOT NULL` |

- `UNIQUE (order_id, event_id)`

- `UNIQUE (order_id, expected_amount, currency)`

- Index: `CREATE INDEX order_snapshots_event_idx ON order_payment_snapshots(event_id);`

### payment_schema.payment_attempts

Mục đích: từng lần thử khởi tạo thanh toán, chưa phải khoản thực thu. Tách lịch sử nhiều lần thử khỏi đơn và charge để thất bại/timeout không làm mất chứng cứ thu. Trade-off: thêm liên kết và state machine; unfinished attempt duy nhất/order chưa tự định nghĩa khi nào UNKNOWN được coi là kết thúc.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY` |
| `order_id` | UUID | Không | `NOT NULL` |
| `provider` | TEXT [không trắng] | Không | `NOT NULL` |
| `merchant_account` | TEXT [không trắng] | Không | `NOT NULL` |
| `expected_amount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `started_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `finished_at` | TIMESTAMPTZ | Có | — |
| `state` | TEXT | Không | `NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','UNKNOWN','SUCCEEDED','FAILED'))` |
| `request_key` | UUID | Không | `NOT NULL` |
| `request_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `payment_url` | TEXT | Có | — |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `FOREIGN KEY (order_id, expected_amount, currency) REFERENCES order_payment_snapshots(order_id, expected_amount, currency)`

- `CHECK ((finished_at IS NULL AND state IN ('PENDING','UNKNOWN')) OR (finished_at IS NOT NULL AND state IN ('SUCCEEDED','FAILED')))`

- `CHECK (finished_at IS NULL OR started_at <= finished_at)`

- `UNIQUE (id, order_id, provider, merchant_account)`

- `UNIQUE (order_id, request_key)`

- Index: `CREATE UNIQUE INDEX payment_one_unfinished_attempt_uq ON payment_attempts(order_id) WHERE finished_at IS NULL;`

- Index: `CREATE INDEX payment_attempts_order_idx ON payment_attempts(order_id, started_at);`

### payment_schema.charges

Khoản thực thu đã xác minh có liên kết, gồm khoản muộn/trùng; unique provider/merchant/charge phân biệt redelivery với hai khoản thu. PRJ-024 loại workflow mismatch/orphan. Trade-off: thêm đối chiếu; sổ nội bộ không chứng minh cổng không còn khoản bỏ sót.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `attempt_id` | UUID | Không | `NOT NULL` |
| `order_id` | UUID | Không | `NOT NULL` |
| `provider` | TEXT [không trắng] | Không | `NOT NULL` |
| `merchant_account` | TEXT [không trắng] | Không | `NOT NULL` |
| `provider_charge_id` | TEXT [không trắng] | Không | `NOT NULL` |
| `amount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `charged_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `recorded_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `FOREIGN KEY (attempt_id, order_id, provider, merchant_account) REFERENCES payment_attempts(id, order_id, provider, merchant_account)`

- `UNIQUE (provider, merchant_account, provider_charge_id)`

- `UNIQUE (id, order_id)`

- `UNIQUE (id, order_id, amount, currency)`

- Index: `CREATE INDEX charges_order_idx ON charges(order_id, charged_at);`

### payment_schema.payment_confirmations

Mục đích: chọn duy nhất một khoản thu hợp lệ cho một đơn; tách sự thật đã thu khỏi quyền phát vé. FK kiểm amount/currency khớp, không tự chứng minh hạn hay điều kiện nghiệp vụ. Trade-off: thêm bảng 1:1; có thể gộp vào hồ sơ thanh toán với cột/constraint thích hợp nhưng chưa chốt thay thiết kế hiện tại. Hoàn charge thừa không đổi confirmation hợp lệ.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `order_id` | UUID | Không | `PRIMARY KEY REFERENCES order_payment_snapshots(order_id)` |
| `charge_id` | UUID | Không | `NOT NULL UNIQUE` |
| `amount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `confirmed_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `FOREIGN KEY (charge_id, order_id, amount, currency) REFERENCES charges(id, order_id, amount, currency)`

- `FOREIGN KEY (order_id, amount, currency) REFERENCES order_payment_snapshots(order_id, expected_amount, currency)`

- `UNIQUE (order_id, charge_id)`

### payment_schema.refunds

Mục đích: tối đa một yêu cầu hoàn logic cho mỗi charge và hoàn toàn bộ số thực thu (FR-34/35). Nhiều retry không thành nhiều yêu cầu hoàn. Trade-off: thêm trạng thái bền vững và phân xử các nguyên nhân hội tụ. Không dùng kết quả hoàn để tự đổi quyền vào cửa; quy tắc retry/reason arbitration thuộc B13/B14.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `charge_id` | UUID | Không | `NOT NULL UNIQUE` |
| `order_id` | UUID | Không | `NOT NULL` |
| `amount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `reason` | TEXT | Không | `NOT NULL CHECK (reason IN ('LATE_PAYMENT','DUPLICATE_PAYMENT','TICKET_ISSUANCE_FAILED','EVENT_CANCELLED'))` |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('PENDING','PROCESSING','SUCCEEDED','FAILED'))` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `completed_at` | TIMESTAMPTZ | Có | — |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `FOREIGN KEY (charge_id, order_id, amount, currency) REFERENCES charges(id, order_id, amount, currency)`

- `CHECK ((state = 'SUCCEEDED' AND completed_at IS NOT NULL) OR (state <> 'SUCCEEDED' AND completed_at IS NULL))`

- `UNIQUE (id, charge_id, order_id)`

- Index: `CREATE INDEX refunds_order_idx ON refunds(order_id);`

- Index: `CREATE INDEX refunds_pending_idx ON refunds(created_at) WHERE state IN ('PENDING','PROCESSING');`

### payment_schema.refund_attempts

Mục đích: lịch sử từng lần gọi cổng cho cùng refund, có request key để truy vấn/thử lại an toàn. Một cột last_error trên refunds làm mất lịch sử lần gọi trước. Trade-off: thêm dòng theo retry; timeout chưa chứng minh cổng chưa hoàn, không tạo request hoàn mới vô điều kiện.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY` |
| `refund_id` | UUID | Không | `NOT NULL REFERENCES refunds(id)` |
| `request_key` | TEXT [không trắng] | Không | `NOT NULL UNIQUE` |
| `provider_refund_id` | TEXT [không trắng] | Có | — |
| `started_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `finished_at` | TIMESTAMPTZ | Có | — |
| `outcome` | TEXT [không trắng] | Có | — |

- `CHECK ((finished_at IS NULL AND outcome IS NULL) OR (finished_at IS NOT NULL AND outcome IS NOT NULL))`

- `CHECK (finished_at IS NULL OR started_at <= finished_at)`

- Index: `CREATE UNIQUE INDEX refund_one_unfinished_attempt_uq ON refund_attempts(refund_id) WHERE finished_at IS NULL;`

- Index: `CREATE INDEX refund_attempts_parent_idx ON refund_attempts(refund_id, started_at);`

### payment_schema.event_refund_runs

Mục đích: tiến trình chọn và xử lý các đơn cần hoàn của một lần hủy event; cursor/selection_complete giúp tiếp tục sau restart. Trade-off: thêm trạng thái điều phối một chiều; không phải Saga mới. Chưa chọn xong không được trình mẫu số như tổng cuối cùng.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `event_id` | UUID | Không | `NOT NULL` |
| `source_cancellation_id` | UUID | Không | `NOT NULL UNIQUE` |
| `selection_cursor` | TEXT | Có | — |
| `selection_complete` | BOOLEAN | Không | `NOT NULL DEFAULT false` |
| `selected_order_count` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (selected_order_count >= 0)` |
| `started_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `completed_at` | TIMESTAMPTZ | Có | — |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `CHECK (completed_at IS NULL OR selection_complete)`

- `UNIQUE (id, event_id)`

### payment_schema.event_refund_items

Mục đích: theo dõi từng đơn trong run kể cả chưa tạo được refund, để báo số chờ/thành công/thất bại (FR-39). Counter trên run không chỉ ra đơn lỗi hoặc điểm tiếp tục. Trade-off: thêm dòng/order; chỉ liên kết charge được confirmation chọn, thu thừa theo nhánh riêng. Chọn item không bắt buộc đã có refund.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `run_id` | UUID | Không | `NOT NULL` |
| `order_id` | UUID | Không | `NOT NULL` |
| `event_id` | UUID | Không | `NOT NULL` |
| `confirmed_charge_id` | UUID | Có | — |
| `refund_id` | UUID | Có | — |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('SELECTED','REFUND_PENDING','REFUNDED','NEEDS_ATTENTION'))` |
| `last_error_code` | TEXT | Có | — |
| `updated_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `PRIMARY KEY (run_id, order_id)`

- `FOREIGN KEY (run_id, event_id) REFERENCES event_refund_runs(id, event_id)`

- `FOREIGN KEY (order_id, event_id) REFERENCES order_payment_snapshots(order_id, event_id)`

- `FOREIGN KEY (order_id, confirmed_charge_id) REFERENCES payment_confirmations(order_id, charge_id)`

- `FOREIGN KEY (refund_id, confirmed_charge_id, order_id) REFERENCES refunds(id, charge_id, order_id)`

- `CHECK (refund_id IS NULL OR confirmed_charge_id IS NOT NULL)`

- `CHECK (state NOT IN ('REFUND_PENDING','REFUNDED') OR refund_id IS NOT NULL)`

- Index: `CREATE INDEX refund_items_order_idx ON event_refund_items(order_id);`

- Index: `CREATE INDEX refund_items_refund_idx ON event_refund_items(refund_id) WHERE refund_id IS NOT NULL;`

### payment_schema.event_payouts

Mục đích: giữ kết quả đối soát/chi trả với fee snapshot, nguồn bằng chứng và actor/time trong owner tiền. Trade-off: số tổng dẫn xuất phải kiểm freshness và công việc pending; CHECK không chứng minh không có giao dịch đang cạnh tranh. Chỉ đánh paid một lần theo FR-42/43, không tự chuyển tiền ngân hàng. Không đưa số payout vào events để tạo writer xuyên owner.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `event_id` | UUID | Không | `PRIMARY KEY` |
| `organizer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `currency` | TEXT [VND] | Không | `NOT NULL` |
| `valid_gross` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `valid_refunds` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `fee_amount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `net_amount` | NUMERIC(19,0) [money_amount] | Không | `NOT NULL` |
| `fee_rate` | NUMERIC(9,8) | Không | `NOT NULL CHECK (fee_rate BETWEEN 0 AND 1)` |
| `source_fee_version` | BIGINT | Không | `NOT NULL CHECK (source_fee_version > 0)` |
| `event_ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `payment_pending_count` | BIGINT | Không | `NOT NULL CHECK (payment_pending_count >= 0)` |
| `refund_pending_count` | BIGINT | Không | `NOT NULL CHECK (refund_pending_count >= 0)` |
| `as_of` | TIMESTAMPTZ | Không | `NOT NULL` |
| `reconciled_by` | TEXT [không trắng] | Có | — |
| `reconciled_at` | TIMESTAMPTZ | Có | — |
| `reconciliation_evidence` | TEXT [không trắng] | Có | — |
| `paid_by` | TEXT [không trắng] | Có | — |
| `paid_at` | TIMESTAMPTZ | Có | — |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `CHECK (valid_refunds <= valid_gross AND net_amount = valid_gross - valid_refunds - fee_amount)`

- `CHECK ((reconciled_by IS NULL AND reconciled_at IS NULL AND reconciliation_evidence IS NULL) OR (reconciled_by IS NOT NULL AND reconciled_at IS NOT NULL AND reconciliation_evidence IS NOT NULL))`

- `CHECK ((paid_by IS NULL AND paid_at IS NULL) OR (paid_by IS NOT NULL AND paid_at IS NOT NULL AND reconciled_at IS NOT NULL AND paid_at >= reconciled_at AND paid_at >= event_ends_at AND payment_pending_count = 0 AND refund_pending_count = 0))`

### payment_schema.payment_sagas

One approved Payment-orchestrated purchase Saga per order, persisted for restart/retry. Charge is the candidate submitted to Booking; confirmation alone does not prove deadline acceptance. Excess linked charges refund independently. Trade-off: coordinator state plus idempotent participants; no additional business Saga.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `order_id` | UUID | Không | `PRIMARY KEY REFERENCES order_payment_snapshots(order_id)` |
| `charge_id` | UUID | Không | `NOT NULL` |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('WAITING_BOOKING','WAITING_TICKETS','COMPLETED','COMPENSATING','COMPENSATED'))` |
| `command_id` | UUID | Không | `NOT NULL` |
| `next_attempt_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `last_error_code` | TEXT | Có | — |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `updated_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `FOREIGN KEY (charge_id, order_id) REFERENCES charges(id, order_id)`

- Index: `CREATE INDEX saga_pending_idx ON payment_sagas(next_attempt_at) WHERE state NOT IN ('COMPLETED','COMPENSATED');`

### payment_schema.outbox_messages

Business update and outgoing event/command commit together. Trade-off: relay and duplicate delivery; published_at only after broker confirm. Payload follows B13 named versioned contract, not arbitrary business JSON.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `message_id` | UUID | Không | `PRIMARY KEY` |
| `aggregate_id` | UUID | Không | `NOT NULL` |
| `aggregate_version` | BIGINT | Không | `NOT NULL CHECK (aggregate_version > 0)` |
| `message_type` | TEXT [không trắng] | Không | `NOT NULL` |
| `schema_version` | INTEGER | Không | `NOT NULL DEFAULT 1 CHECK (schema_version = 1)` |
| `payload` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(payload) = 'object')` |
| `occurred_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `correlation_id` | UUID | Không | `NOT NULL` |
| `causation_id` | UUID | Có | — |
| `traceparent` | TEXT | Có | — |
| `published_at` | TIMESTAMPTZ | Có | — |
| `attempts` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (attempts >= 0)` |
| `next_attempt_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `last_error_code` | TEXT | Có | — |

- Index: `CREATE INDEX outbox_pending_idx ON outbox_messages(next_attempt_at, occurred_at) WHERE published_at IS NULL;`

### payment_schema.inbox_messages

Deduplication by consumer and message ID in same transaction as effect and response outbox. Compare payload hash on replay; different hash rejects. Trade-off: durable rows/retention; not exactly-once network delivery.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `consumer_name` | TEXT [không trắng] | Không | `NOT NULL` |
| `message_id` | UUID | Không | `NOT NULL` |
| `payload_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `processed_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `response_payload` | JSONB | Có | `CHECK (jsonb_typeof(response_payload) = 'object')` |

- `PRIMARY KEY (consumer_name, message_id)`

### ticket_schema.event_access_snapshots

Mục đích: Ticket có owner, cửa sổ vào cửa và trạng thái event để check-in không JOIN DB Event. Trade-off: giảm phụ thuộc runtime nhưng thông báo hủy có thể trễ; received_at không chứng minh đã thấy mọi hủy. B13 phải chốt điểm hiệu lực; thiếu dữ kiện hợp lệ thì từ chối. Cửa sổ hiện hành eventStart <= now <= eventEnd, gồm hai đầu mút.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `event_id` | UUID | Không | `PRIMARY KEY` |
| `organizer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `event_status` | TEXT [không trắng] | Không | `NOT NULL` |
| `event_starts_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `event_ends_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `source_config_version` | BIGINT | Không | `NOT NULL CHECK (source_config_version > 0)` |
| `received_at` | TIMESTAMPTZ | Không | `NOT NULL` |

- `CHECK (event_starts_at < event_ends_at)`

### ticket_schema.issuances

Một bộ vé/order với input bất biến và terminal fence. Trade-off: thêm root để retry không tạo quyền mới; uniqueness không chứng minh đủ số vé, cần transaction kiểm counts. Timeout không đủ để FAILED và hoàn.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `order_id` | UUID | Không | `NOT NULL UNIQUE` |
| `event_id` | UUID | Không | `NOT NULL REFERENCES event_access_snapshots(event_id)` |
| `buyer_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `payment_confirmation_id` | TEXT [không trắng] | Không | `NOT NULL` |
| `customer_email` | TEXT [không trắng] | Không | `NOT NULL` |
| `purchase_snapshot` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(purchase_snapshot) = 'object' AND purchase_snapshot @> '{"schemaVersion":1}' AND purchase_snapshot ? 'items' AND jsonb_typeof(purchase_snapshot->'items') = 'array')` |
| `input_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `expected_ticket_count` | INTEGER | Không | `NOT NULL CHECK (expected_ticket_count > 0)` |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('PENDING','COMPLETED','FAILED'))` |
| `created_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `completed_at` | TIMESTAMPTZ | Có | — |
| `failed_at` | TIMESTAMPTZ | Có | — |
| `failure_code` | TEXT [không trắng] | Có | — |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `CHECK ((state = 'COMPLETED' AND completed_at IS NOT NULL) OR (state <> 'COMPLETED' AND completed_at IS NULL))`

- `CHECK ((state = 'FAILED' AND failed_at IS NOT NULL AND failure_code IS NOT NULL) OR (state <> 'FAILED' AND failed_at IS NULL AND failure_code IS NULL))`

- `UNIQUE (id, event_id)`

- `UNIQUE (id, order_id)`

- Index: `CREATE INDEX issuances_buyer_idx ON issuances(buyer_subject, created_at);`

- Index: `CREATE INDEX issuances_event_idx ON issuances(event_id);`

### ticket_schema.tickets

Mục đích: từng quyền vào cửa, có seat/holder và trạng thái riêng, không gộp theo đơn vì một đơn có nhiều vé. Trade-off: thêm dòng/khóa và snapshot hiển thị. Ciphertext cho phép tải lại QR với khóa ngoài DB có thể restore; hash-only random token không đủ tải lại. Định dạng QR/thuật toán thuộc B13; không lưu QR thô vào audit. Không có trạng thái REFUNDED; kết quả hoàn không tự đổi quyền vào cửa.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `issuance_id` | UUID | Không | `NOT NULL` |
| `event_id` | UUID | Không | `NOT NULL` |
| `order_item_id` | UUID | Không | `NOT NULL` |
| `ordinal` | INTEGER | Không | `NOT NULL CHECK (ordinal > 0)` |
| `holder_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `ticket_type_id` | UUID | Không | `NOT NULL` |
| `sector_id` | UUID | Có | — |
| `seat_id` | UUID | Có | — |
| `display_snapshot` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(display_snapshot) = 'object' AND display_snapshot @> '{"schemaVersion":1}')` |
| `state` | TEXT | Không | `NOT NULL CHECK (state IN ('VALID','USED','VOID'))` |
| `qr_token_ciphertext` | BYTEA | Không | `NOT NULL CHECK (octet_length(qr_token_ciphertext) > 0)` |
| `qr_key_version` | TEXT [không trắng] | Không | `NOT NULL` |
| `qr_token_hash` | TEXT [không trắng] | Không | `NOT NULL UNIQUE` |
| `used_at` | TIMESTAMPTZ | Có | — |
| `used_by` | TEXT [không trắng] | Có | — |
| `row_version` | BIGINT | Không | `NOT NULL DEFAULT 0 CHECK (row_version >= 0)` |

- `FOREIGN KEY (issuance_id, event_id) REFERENCES issuances(id, event_id)`

- `CHECK (seat_id IS NULL OR sector_id IS NOT NULL)`

- `CHECK ((state = 'USED' AND used_at IS NOT NULL AND used_by IS NOT NULL) OR (state <> 'USED' AND used_at IS NULL AND used_by IS NULL))`

- `UNIQUE (issuance_id, order_item_id, ordinal)`

- `UNIQUE (id, issuance_id)`

- Index: `CREATE INDEX tickets_issuance_idx ON tickets(issuance_id);`

### ticket_schema.ticket_deliveries

Mục đích: từng lần gửi thông tin vé, tách giao nhận khỏi việc phát hành và hiệu lực vé. Một email lỗi không được làm mất vé hoặc khởi động refund (FR-32). Trade-off: thêm lịch sử/lần thử, nhưng tránh gửi lại bị hiểu thành phát thêm vé. Không lưu credential SMTP trong bảng.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `issuance_id` | UUID | Không | `NOT NULL REFERENCES issuances(id)` |
| `requested_by` | TEXT [không trắng] | Không | `NOT NULL` |
| `channel` | TEXT [không trắng] | Không | `NOT NULL` |
| `request_key` | UUID | Không | `NOT NULL` |
| `request_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `recipient_email` | TEXT [không trắng] | Không | `NOT NULL` |
| `requested_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `finished_at` | TIMESTAMPTZ | Có | — |
| `outcome` | TEXT [không trắng] | Có | — |
| `failure_code` | TEXT | Có | — |
| `attempts` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (attempts >= 0)` |
| `next_attempt_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |

- `UNIQUE (issuance_id, requested_by, request_key)`

- `CHECK ((finished_at IS NULL AND outcome IS NULL) OR (finished_at IS NOT NULL AND outcome IS NOT NULL))`

- Index: `CREATE INDEX deliveries_issuance_idx ON ticket_deliveries(issuance_id, requested_at);`

### ticket_schema.checkin_attempts

Mục đích: mọi yêu cầu check-in, gồm cả từ chối, có actor/time/result để điều tra (FR-65). Cột used_at trên ticket chỉ giữ được lần thành công, không thay lịch sử này. Trade-off: số dòng tăng, cần retention B16; không chứa QR/JWT thô. Unique success chưa thay kiểm owner/event/window/state và cập nhật nguyên tử; không hỗ trợ offline hoặc hoàn tác.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `id` | UUID | Không | `PRIMARY KEY DEFAULT gen_random_uuid()` |
| `ticket_id` | UUID | Có | `REFERENCES tickets(id)` |
| `request_key` | UUID | Không | `NOT NULL` |
| `replay_of` | UUID | Có | `REFERENCES checkin_attempts(id)` |
| `actor_subject` | TEXT [không trắng] | Không | `NOT NULL` |
| `requested_event_id` | UUID | Không | `NOT NULL` |
| `request_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `requested_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `result_code` | TEXT [không trắng] | Không | `NOT NULL` |
| `succeeded` | BOOLEAN | Không | `NOT NULL` |
| `correlation_id` | TEXT [không trắng] | Không | `NOT NULL` |

- `CHECK (NOT succeeded OR ticket_id IS NOT NULL)`

- `CHECK (replay_of IS NULL OR (replay_of <> id AND NOT succeeded))`

- Index: `CREATE UNIQUE INDEX checkin_one_success_per_ticket_uq ON checkin_attempts(ticket_id) WHERE succeeded;`

- Index: `CREATE UNIQUE INDEX checkin_original_request_uq ON checkin_attempts(actor_subject, request_key) WHERE replay_of IS NULL;`

- Index: `CREATE INDEX checkin_ticket_idx ON checkin_attempts(ticket_id, requested_at);`

- Index: `CREATE INDEX checkin_event_idx ON checkin_attempts(requested_event_id, requested_at);`

### ticket_schema.outbox_messages

Business update and outgoing event/command commit together. Trade-off: relay and duplicate delivery; published_at only after broker confirm. Payload follows B13 named versioned contract, not arbitrary business JSON.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `message_id` | UUID | Không | `PRIMARY KEY` |
| `aggregate_id` | UUID | Không | `NOT NULL` |
| `aggregate_version` | BIGINT | Không | `NOT NULL CHECK (aggregate_version > 0)` |
| `message_type` | TEXT [không trắng] | Không | `NOT NULL` |
| `schema_version` | INTEGER | Không | `NOT NULL DEFAULT 1 CHECK (schema_version = 1)` |
| `payload` | JSONB | Không | `NOT NULL CHECK (jsonb_typeof(payload) = 'object')` |
| `occurred_at` | TIMESTAMPTZ | Không | `NOT NULL` |
| `correlation_id` | UUID | Không | `NOT NULL` |
| `causation_id` | UUID | Có | — |
| `traceparent` | TEXT | Có | — |
| `published_at` | TIMESTAMPTZ | Có | — |
| `attempts` | INTEGER | Không | `NOT NULL DEFAULT 0 CHECK (attempts >= 0)` |
| `next_attempt_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `last_error_code` | TEXT | Có | — |

- Index: `CREATE INDEX outbox_pending_idx ON outbox_messages(next_attempt_at, occurred_at) WHERE published_at IS NULL;`

### ticket_schema.inbox_messages

Deduplication by consumer and message ID in same transaction as effect and response outbox. Compare payload hash on replay; different hash rejects. Trade-off: durable rows/retention; not exactly-once network delivery.

| Cột | Type vật lý | NULL | Default và ràng buộc cột |
|---|---|---|---|
| `consumer_name` | TEXT [không trắng] | Không | `NOT NULL` |
| `message_id` | UUID | Không | `NOT NULL` |
| `payload_hash` | TEXT [không trắng] | Không | `NOT NULL` |
| `processed_at` | TIMESTAMPTZ | Không | `NOT NULL DEFAULT now()` |
| `response_payload` | JSONB | Có | `CHECK (jsonb_typeof(response_payload) = 'object')` |

- `PRIMARY KEY (consumer_name, message_id)`

## 13. Từ điển MongoDB — giữ bốn collection riêng

Kiểu dưới là BSON validator thật tại `data/mongodb/schema.cjs`; trường không bắt buộc phải **vắng mặt** khi không dùng, không gửi null trừ khi validator cho phép. Tất cả object đóng, từ chối trường lạ. UUID được lưu string; ngày là BSON date, không string. Mongo không có FK: service kiểm chủ thể/tham chiếu, sourceVersion giúp phục hồi projection.

### users

| Trường | BSON type | Bắt buộc | Điều kiện |
|---|---|---|---|
| `_id` | string | Có | pattern ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ |
| `createdAt` | date | Có | — |
| `updatedAt` | date | Có | — |
| `version` | int/long | Có | minimum 0 |
| `identitySubject` | string | Có | pattern \S |
| `displayName` | string | Không | pattern \S |

Index: `{"identitySubject":1}` UNIQUE.

### organizer_applications

| Trường | BSON type | Bắt buộc | Điều kiện |
|---|---|---|---|
| `_id` | string | Có | pattern ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ |
| `createdAt` | date | Có | — |
| `updatedAt` | date | Có | — |
| `version` | int/long | Có | minimum 0 |
| `identitySubject` | string | Có | pattern \S |
| `organizationName` | string | Có | pattern \S |
| `shortDescription` | string | Có | pattern \S |
| `status` | enum | Có | PENDING, ACTIVE, REJECTED |
| `rejectionReason` | string | Không | pattern \S |
| `decidedBySubject` | string | Không | pattern \S |
| `decidedAt` | date | Không | — |
| `roleGrant` | object | Không | — |
| `roleGrant.operationId` | string | Có | pattern ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ |
| `roleGrant.status` | enum | Có | REQUESTED, CONFIRMED, FAILED, UNKNOWN |
| `roleGrant.attemptedAt` | date | Có | — |
| `roleGrant.confirmedAt` | date | Không | — |

Index: `{"identitySubject":1}` UNIQUE; `{"status":1,"createdAt":1}`.

### organizer_profiles

| Trường | BSON type | Bắt buộc | Điều kiện |
|---|---|---|---|
| `_id` | string | Có | pattern ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ |
| `identitySubject` | string | Có | pattern \S |
| `sourceApplicationId` | string | Có | pattern ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ |
| `sourceVersion` | int/long | Có | minimum 0 |
| `organizationName` | string | Có | pattern \S |
| `shortDescription` | string | Có | pattern \S |
| `status` | enum | Có | ACTIVE |
| `logoUrl` | string | Không | pattern ^https://\S+$ |
| `bannerUrl` | string | Không | pattern ^https://\S+$ |
| `refreshedAt` | date | Có | — |

Index: `{"identitySubject":1}` UNIQUE; `{"sourceApplicationId":1}` UNIQUE.

### user_follows

| Trường | BSON type | Bắt buộc | Điều kiện |
|---|---|---|---|
| `_id` | string | Có | pattern ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ |
| `buyerSubject` | string | Có | pattern \S |
| `organizerSubject` | string | Có | pattern \S |
| `createdAt` | date | Có | — |

Index: `{"buyerSubject":1,"organizerSubject":1}` UNIQUE; `{"organizerSubject":1}`.

Application PENDING không có decidedBy/decidedAt/rejectionReason; ACTIVE bắt buộc quyết định và roleGrant.CONFIRMED có confirmedAt; REJECTED bắt buộc rejectionReason và không roleGrant. Profile chỉ ACTIVE, giữ riêng theo GOV-122; lỗi ghi projection phục hồi từ application, không duyệt lại hồ sơ. Follow unique cặp và count theo organizer index; chỉ collection follow có remove, không cấp remove hồ sơ/application.
