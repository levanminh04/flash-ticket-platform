# Event service — nghiệp vụ bước 1–7

Triển khai `QUANTITY` và `SEAT_MAP`: tạo/sửa bản nháp, gửi duyệt, trả về nháp,
duyệt/công bố, hủy và yêu cầu hủy; đọc/tìm sự kiện; transactional outbox gửi snapshot.
Thống kê có adapter đọc với contract đề xuất, mặc định chưa bật. Cấu hình
ảnh là object reference, không upload hoặc tải URL từ server. Venue/category chỉ
tra cứu dữ liệu có sẵn, chưa có API quản trị hai danh mục này.

## Căn cứ và phạm vi

- [B7 §4.1](../../docs/domain/B7-aggregates-and-invariants.md): trạng thái, thời gian,
  khóa cấu hình, giá, phí duyệt, mã khuyến mãi; không suy ra sức chứa venue.
- [B8](../../docs/domain/B8-requirements.md): FR01–10 và các yêu cầu liên quan tới
  cấu hình, duyệt, công bố, tìm/đọc sự kiện.
- [B12](../../docs/architecture/B12-data-ownership-and-schema.md) và
  [SQL Event](../../docs/architecture/data/postgres/10-event.sql): Event sở hữu
  cấu hình; dùng bảng canonical có tiền tố `tbl_`, không thêm DDL/migration.
- [B13](../../docs/architecture/B13-api-and-event-contracts.md): DTO công khai,
  money/version, lỗi, envelope và ba snapshot. Các endpoint CRUD thuộc owner được
  cụ thể hóa tại đây theo §9; không đổi hợp đồng xuyên service.
- [B14](../../docs/architecture/B14-main-flow-sequences.md),
  [B15](../../docs/architecture/B15-verification-plan.md) và
  [B16](../../docs/architecture/B16-observability-baseline.md): khóa/transaction,
  rollback, concurrency, retry và bằng chứng vận hành.
- Exchange là **`business.ex` theo handoff và chỉ đạo của anh**, ghi đè tên exchange
  lịch sử trong B13; routing key/message type giữ nguyên B13.

Event sở hữu cấu hình; Booking sở hữu tồn kho/giữ chỗ và lượt dùng khuyến mãi.
Đã có HTTP read adapter cho hủy/thống kê, không đọc database của owner khác.
Contract response chưa được Booking/Payment xác nhận: xem
[INTEGRATION-CONTRACTS](INTEGRATION-CONTRACTS.md). Mặc định trả503 khi luồng cần
nguồn chưa sẵn sàng, không giả số0/không có đơn thu tiền. Chưa nghiệm thu consumer
liên service, mua vé, hoàn tiền hoặc B16 đầy đủ.

## API trực tiếp của Event

Prefix `/api/v1`. Xác thực Bearer JWT từ Keycloak: đúng issuer, audience
`flash-ticket-api`, chữ ký, thời hạn và `sub`; role lấy từ `realm_access.roles`.
Không nhận organizer/admin subject qua body. `ADMIN` không tự có quyền sửa thay
organizer; endpoint organizer yêu cầu `ORGANIZER` và đúng chủ sở hữu.

| Method/path sau prefix | Quyền | Body/kết quả |
|---|---|---|
| POST `/organizer/events` | ORGANIZER | Draft → Owner, HTTP 201 |
| PUT `/organizer/events/{id}` | ORGANIZER/chủ sở hữu | `{rowVersion,draft}` → Owner |
| GET `/organizer/events/{id}` | ORGANIZER/chủ sở hữu | Owner |
| GET `/organizer/events` | ORGANIZER | Page Summary của chính mình |
| POST `/organizer/events/{id}/submit` | ORGANIZER/chủ sở hữu | `{rowVersion}` → Owner |
| POST `/organizer/events/{id}/publish` | ORGANIZER/chủ sở hữu | `{rowVersion}` → Owner |
| GET `/admin/events` | ADMIN | Page Summary, mặc định `status=PENDING_APPROVAL` |
| GET `/admin/events/{id}` | ADMIN | Owner để duyệt |
| POST `/admin/events/{id}/return-to-draft` | ADMIN | `{rowVersion}` → Owner |
| POST `/admin/events/{id}/approve` | ADMIN | `{rowVersion,feeRate}` → Owner |
| GET `/events` | Công khai | Page EventPublic, chỉ PUBLISHED/QUANTITY |
| GET `/events/{id}` | Công khai | EventPublic, chỉ PUBLISHED/QUANTITY |
| GET `/categories`, GET `/venues` | Công khai | Mảng `{id,name,address}` |

`/actuator/health`, health component và `/actuator/info` yêu cầu `INTERNAL_SERVICE`.
Các path khác mặc định bị từ chối. Không bật form login/session. Public endpoint
không cần token; nếu gửi Bearer token sai thì vẫn bị từ chối xác thực.

List dùng `size=20` mặc định, 1–100, `cursor` opaque do server cấp, trả
`{items,nextCursor}`. Sắp xếp `(created_at DESC,id DESC)` và áp filter trước phân
trang. Owner/admin có `status`; public có `q` tìm tiêu đề không phân biệt hoa
thường và `categoryId`. `%`/`_` trong `q` được tìm như ký tự thường. Không có total
count hay bảo đảm snapshot dữ liệu xuyên nhiều request phân trang.

## Payload ghi nháp

POST nhận toàn bộ Draft; PUT nhận `{ "rowVersion": "0", "draft": <Draft> }`.
PUT thay thế toàn bộ cấu hình nháp: child không còn trong mảng sẽ bị xóa. Lần tạo
đặt child ID là null; lần sửa có thể giữ ID server đã cấp trong chính event hoặc
để null để tạo child mới. ID thuộc event khác/không tồn tại bị từ chối.

```json
{
  "title": "Concert",
  "description": "Mô tả",
  "categoryId": null,
  "venueId": null,
  "mode": "QUANTITY",
  "startsAt": "2027-01-01T12:00:00Z",
  "endsAt": "2027-01-01T15:00:00Z",
  "saleStartsAt": "2026-12-01T00:00:00Z",
  "saleEndsAt": "2027-01-01T14:00:00Z",
  "purchaseLimit": 4,
  "images": [{"imageId": null, "objectReference": "events/poster", "displayOrder": 0}],
  "ticketTypes": [{"ticketTypeId": null, "name": "Standard", "unitPrice": "100005", "currency": "VND", "configuredQuantity": "10", "colorCode": "#ABCDEF"}],
  "promotions": [{"promotionId": null, "code": "SAVE10", "startsAt": "2026-12-01T00:00:00Z", "endsAt": "2027-01-01T14:00:00Z", "maximumUses": "5", "discount": {"kind": "PERCENT", "amount": null, "percent": 10}}]
}
```

- Các field của command phải hiện diện, kể cả field nullable; không nhận field
  lạ hoặc tự coercion number/bool thành string. Discount command FIXED dùng
  `{kind:"FIXED",amount:"10000",percent:null}`; PERCENT dùng ví dụ trên. Trong
  response/snapshot, nhánh discount không dùng được bỏ đi đúng union B13.
- UUID phải đúng dạng canonical 36 ký tự. Thời gian dùng UTC `Z`.
- Money là chuỗi nguyên dương tối đa 19 chữ số, currency VND; configuredQuantity
  là chuỗi bigint không âm, maximumUses nguyên dương. Không dùng float cho tiền.
- Title không rỗng; description có thể rỗng. `categoryId`/`venueId` nullable khi
  lưu nháp; ID khác null phải tồn tại. Nháp có thể chưa có ticket type.
- `startsAt < endsAt`, `saleStartsAt < saleEndsAt <= endsAt`; sale có thể kết thúc
  sau lúc bắt đầu sự kiện. Check-in lưu cùng thời gian bắt đầu/kết thúc sự kiện.
- Purchase limit dương, không tự kiểm sức chứa venue. Image displayOrder không
  âm/không trùng, colorCode null hoặc `#RRGGBB`.
- Promotion code trim/uppercase và duy nhất trong event, `startsAt < endsAt`,
  phần trăm nguyên 1–100 hoặc số tiền FIXED dương; mỗi tài khoản một lượt theo
  policy đã duyệt. Event chỉ lưu cấu hình, Booking mới quản lý giữ/chốt/trả lượt.

Owner trả `rowVersion` (bắt đầu "0"), `sourceConfigVersion` (bắt đầu "1"), cấu
hình đầy đủ và fee/approvedAt nullable. EventPublic giữ đúng B13, không lộ subject,
version, phí hay promotion; QUANTITY có `seatMap:null`, `sectorId:null`.
Owner/admin detail có `seatMap` đầy đủ (gồm hidden) để sửa/xét duyệt; public view
lọc hidden theo B13. Không dùng public endpoint để admin đọc bản nháp.

## SEAT_MAP, đổi mode và ticket types theo sector

- POST Draft có thể dùng mode SEAT_MAP với `ticketTypes:[]`. PUT Draft của
  SEAT_MAP sửa metadata/images/promotions với `ticketTypes:[]`, giữ nguyên layout
  và type theo sector; không dùng DTO loại vé QUANTITY để xóa chúng.
- GET/PUT `/organizer/events/{id}/seat-map` dùng đúng `SeatMapDraft` B13 §7.2.
  PUT thêm query `rowVersion` bắt buộc; body không thêm field ngoài B13. Chỉ DRAFT
  và đúng owner. GET khi chưa có layout trả404 SEAT_MAP_NOT_CONFIGURED.
- Layout/sector/seat IDs do editor tạo phải canonical và ổn định qua save/reload;
  không ghi đè ID thuộc event khác. Seat thiếu type được giữ trong draft. Ghế ẩn
  được lưu, không trả public; SalesConfiguration chỉ có seat đã gán type, kể cả
  hidden có type (Booking đánh UNAVAILABLE), bỏ hidden chưa có type.
- PUT `/organizer/events/{id}/ticket-types` nhận `{rowVersion,ticketTypes}`. Mỗi
  type là `{ticketTypeId,sectorId,name,unitPrice,currency,colorCode}`; ID null để
  tạo mới, ID đã có phải của event. configuredQuantity không áp dụng SEAT_MAP.
  Type phải thuộc sector cùng event; không xóa/move type còn được seat tham chiếu.
- POST `/organizer/events/{id}/sales-mode` nhận
  `{rowVersion,mode,confirmDeleteIncompatibleConfiguration}`. Đổi mode chỉ DRAFT,
  cần confirm=true; xóa layout/sectors/seats/types không tương thích cùng transaction,
  giữ images/promotions. PUT seat-map/Draft không tự chuyển mode.
- STANDING không có seat; dùng configuredCapacity chung. SEATED trước submit phải
  gán type cho mọi ghế không hidden và số ghế không hidden không vượt capacity.
  Hidden vẫn được giữ trong draft; không áp giới hạn tổng rows gồm hidden ngoài
  quy tắc đã có. Giá lớn hơn0; không kiểm sức chứa vật lý venue.
- Shape kiểm đúng nhánh, field lạ bị từ chối; finite numbers, bounds/radii/angles,
  polygon/path và coordinate NUMERIC(10,2). SVG path chỉ grammar vẽ, không XML/script.
  editorVisible/editorLocked chỉ trình bày, không mở/đóng bán.
- Body/lists đều lưu trọn vẹn; lỗi giữa lúc thay layout/ghế/type rollback cả version.

## Hủy sự kiện và thống kê

Các path dưới đây vẫn có prefix `/api/v1`:

| Method/path | Quyền và body |
|---|---|
| POST `/organizer/events/{id}/cancel` | ORGANIZER + owner; `{rowVersion}` |
| POST `/organizer/events/{id}/cancellation-requests` | ORGANIZER + owner; `{rowVersion,reason}` |
| GET `/organizer/events/{id}/cancellation-requests` | ORGANIZER + owner; size/cursor |
| GET `/admin/cancellation-requests` | ADMIN; status mặc định PENDING, size/cursor |
| GET `/admin/events/{id}/cancellation-requests` | ADMIN; size/cursor |
| POST `/admin/events/{id}/cancel` | ADMIN; `{rowVersion}` |
| POST `/admin/cancellation-requests/{id}/approve` | ADMIN; `{rowVersion}` của event |
| POST `/admin/cancellation-requests/{id}/reject` | ADMIN; `{rowVersion,reason}`; reason có thể null |
| GET `/organizer/events/{id}/statistics` | ORGANIZER + owner; không ghi counter |

Organizer được hủy trực tiếp khi Payment xác nhận chưa có đơn thu tiền, hoặc tạo
request khi đã có đơn thu tiền. Admin có thể chủ động hủy mà không cần phân loại.
HTTP đọc Payment ở ngoài transaction; sau đó khóa event, kiểm lại version/quyền/
DB time và tuổi snapshot trước quyết định. Chấp nhận hủy chỉ khi DB time < startsAt.
Summary không khóa Payment; charge đến trễ vẫn phải hội tụ theo B14.

Mỗi request có ID riêng, lý do, người/mốc tạo, người/mốc duyệt và lý do review
nullable. Giữ lịch sử; tối đa một PENDING/event; REJECTED có thể gửi request mới,
CANCELLED không nhận request mới. Reject giữ trạng thái event, không phát cancellation.
Tạo/reject request chỉ tăng rowVersion, không đổi sourceConfigVersion. Approve hoặc
admin hủy chuyển CANCELLED và ghi đúng một EventCancellation cùng transaction.
Admin chủ động hủy cũng đóng request PENDING thành APPROVED. Hủy/approve lặp trả
trạng thái đã có; cancellationId/messageId/data không được tạo lại.

`event.cancelled.v1` có full sales/access snapshot CANCELLED đúng B13; chưa có
config/ticket type vẫn phát snapshot cancellation được. Event không tự đóng đơn,
vô hiệu vé hoặc gọi hoàn tiền; Booking/Ticket/Payment xử lý message theo B14.

Statistics đọc Booking/Payment sau kiểm ownership, trả nguồn và thời điểm dữ liệu;
hai nguồn phải thành công. Chi tiết field/config/error và phần chưa được chốt ở
[contract đề xuất](INTEGRATION-CONTRACTS.md). Default-disabled không ảnh hưởng
editor, duyệt/publish hoặc admin cancel; organizer cancel/request và statistics
sẽ503 DEPENDENCY_CONTRACT_NOT_READY tới khi hai owner thống nhất và cấu hình bật.

## Vòng đời, transaction và outbox

`DRAFT → PENDING_APPROVAL → APPROVED → PUBLISHED`; admin có thể trả
`PENDING_APPROVAL → DRAFT`, không tạo trạng thái event `REJECTED`.

Submit cần ít nhất một ticket type và venue. Cấu hình ticket/promotion/ảnh/thời
gian được khóa từ submit; chỉ DRAFT được PUT. Mỗi lệnh thay đổi khóa row event
trước child, kiểm expected rowVersion và tăng row/config version. Lưu toàn bộ nháp
và các snapshot liên quan trọn vẹn trong một transaction PostgreSQL.

Approve nhận feeRate dạng chuỗi fraction 0–1, tối đa 8 số thập phân ("0.1" = 10%),
lưu người/mốc duyệt và đóng băng phí. Cùng transaction ghi
`event.finance-configured.v1` với sourceFeeVersion tại lần duyệt. Publish ghi
`event.sales-configured.v1` và `event.access-configured.v1`. Gọi publish lại khi
PUBLISHED trả kết quả hiện tại, không tăng version hoặc ghi thêm outbox; không
tạo lại FinanceConfiguration đã đóng băng. Finance có thể tới trước Access;
consumer phải xử lý theo contract/version của chính snapshot.

Outbox lưu `data` và metadata riêng, dựng Envelope schemaVersion=1 khi gửi.
Worker claim một message bằng `FOR UPDATE SKIP LOCKED`, tăng attempts/đặt lease,
commit và nhả khóa DB trước khi chờ Rabbit. Chỉ đánh dấu published sau confirm
ACK và không có mandatory return. Nack/return/timeout/mất kết nối retry backoff;
giữ nguyên messageId, version và data. Attempts là fence, worker cũ hết lease
không được xác nhận thay worker mới. Giao nhận at-least-once; consumer phải dedupe
theo messageId và từ chối version cũ. Không bảo đảm thứ tự giao giữa nhiều worker.
Không declare/sửa topology bằng tài khoản ứng dụng.

| Biến môi trường riêng | Mặc định |
|---|---|
| EVENT_OUTBOX_ENABLED | true |
| EVENT_OUTBOX_POLL_DELAY | 1s |
| EVENT_OUTBOX_BATCH_SIZE | 20 |
| EVENT_OUTBOX_LEASE | 30s |
| EVENT_OUTBOX_CONFIRM_TIMEOUT | 5s |
| EVENT_OUTBOX_INITIAL_RETRY | 1s |
| EVENT_OUTBOX_MAXIMUM_RETRY | 5m |

Lease nên lớn hơn confirm timeout. Khi chỉ kiểm tra kết nối hạ tầng, dùng
`--event.outbox.enabled=false` để không gửi backlog đang có.

Lỗi trả `{code,message,correlationId,violations:[{field,code}]}`: 400 JSON/UUID/
page sai, 401 token sai/thiếu, 403 sai role, 404 không tồn tại/không có quyền đọc,
409 stale version/sai trạng thái/xung đột, 422 sai cấu hình nghiệp vụ/mode chưa hỗ
trợ, 503 storage tạm lỗi. Không trả stacktrace/SQL/credential. Correlation UUID
được kiểm/khởi tạo ở HTTP boundary, có response header và lưu cùng outbox;
traceparent hợp lệ được truyền tiếp. Business log chỉ phát sau commit. Có metric
HTTP theo route bounded, outbox backlog/oldest age/publish outcome; chưa nghiệm
thu đầy đủ B16 collector/dashboards trên EC2 #2.

## Chạy và kiểm chứng

Xem [HANDOFF](HANDOFF.md) cho Stage 1: Gateway/Discovery/Config local, PostgreSQL,
RabbitMQ/Keycloak trên EC2. Chạy JDK 21 với `-Duser.timezone=UTC`, nạp biến vào
process; Spring không tự đọc `.env`. Không cài PostgreSQL local để chạy nghiệp vụ.

```powershell
# Chạy trong services/event-service với JDK 21, Docker engine hoạt động.
.\mvnw.cmd -B -ntp spotless:check verify
```

Test tích hợp dùng PostgreSQL 16.15 Docker **cô lập**, canonical SQL/prefix, role
`event_admin` NOINHERIT và SET ROLE event_owner. Fixture/TRUNCATE chỉ chạy trong
container test; tuyệt đối không dùng URL DB chung cho test. Test bao gồm HTTP
lifecycle/quyền/strict JSON, transaction rollback, concurrent save/submit/publish,
lease fence, real-signed JWT với JWKS test và publisher fault bằng mock.
Confirm/return thực tế với broker chung và consumer liên service chưa được test.

## Handoff tích hợp Gateway

Source Gateway đã bổ sung route/security cho toàn bộ API Event ở trên, bao gồm
API bước1–4. Dùng method/path cụ thể; availability vẫn Booking, check-ins vẫn
Ticket, internal API không public. Áp dụng code Gateway mới cần build JAR, rebuild
image và recreate riêng api-gateway theo Compose; restart image cũ không nạp code
mới. Lượt coding này chưa recreate/deploy Gateway Docker. Không sửa code các
business service khác. Hoàn tất riêng Event không đồng nghĩa đã nghiệm thu mua vé,
đóng bán/vô hiệu vé/hoàn tiền xuyên service.
