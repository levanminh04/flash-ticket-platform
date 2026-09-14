# Bảng quyết định đã xác nhận — dữ liệu, vòng đời và tiền

- Phiên bản: `B12-B14-review-v0.6`
- Trạng thái: `APPROVED`; người duyệt: Lê Văn Minh; ngày duyệt: 2026-09-14 (GOV-144).
- Ngày soạn: 2026-09-13.
- Phân lớp: thân bài `FORMATION`; §3 là phụ lục `COMPARISON` có nguồn legacy chỉ để kiểm tái sử dụng.
- Nguồn thiết kế: B8-v0.12 APPROVED; B11-C-v0.3 APPROVED; B12-v0.1 DRAFT; decision-register BIZ-153–158, GOV-101–142, PRJ-010–024.
- Đây là phiếu trình lựa chọn, không phải B13/B14 đã hoàn tất. Sau duyệt phải cập nhật nguồn B12, hợp đồng B13, sequence B14 và tài sản dẫn xuất. Không có câu nào ở đây phê duyệt lại năm service hoặc sinh một Saga mới.
- Các chi tiết đề xuất bên dưới là `CANDIDATE` trừ phần được chỉ rõ đã `USER_CONFIRMED` theo sổ. D-08 đã chốt giữ profile riêng; nguồn clone bị bỏ, công cụ tạo hình được giữ; giữ renderer/adapter, lưu nguyên tử, ghế ẩn lưu bền và ISSUING đã xác nhận. Không coi các xác nhận này là duyệt toàn bộ cột, S-06 hoặc M-01–07.

## 1. Đọc bộ hiện hành

Bảy câu hỏi Q-01–07 đã được giải quyết bằng xác nhận của Minh; không cần chọn lại. Q-07 bị loại khỏi phạm vi, không phải đã được xử lý. GOV-143 cho phép rà và đồng bộ toàn bộ danh sách tác động ngày 2026-09-14; không đồng nghĩa AI được phê duyệt gate.

| Muốn xem | Đọc |
|---|---|
| Dùng lại / sửa / bỏ gì từ FE/BE | §3 D-01–12 và bằng chứng tọa độ/ghế ẩn |
| Order–tiền–vé chuyển thế nào | §4 S-01–06; chi tiết thứ tự tại [B14](B14-main-flow-sequences.md) |
| Quy ước tính tiền đã chọn | §5 M-01–07 |
| Toàn bộ cột/type và init tạo mới | [B12](B12-data-ownership-and-schema.md), [SQL](data/postgres/README.md) |
| Payload và ánh xạ FE | [B13](B13-api-and-event-contracts.md) |
| Khi nào code, tạo project gì | [Readiness](implementation-readiness.md) |

Tên cột, DTO, timeout và persistence là chi tiết kỹ thuật CANDIDATE của bản tích hợp, không gắn nhãn USER_CONFIRMED thay cho người dùng. B6/B7/B8 đã đồng bộ và trình REVIEW_READY; chưa tự APPROVED bộ B12–B16.

## 2. Những phần không mở lại

Năm owner event/booking/payment/ticket/user; một Saga do Payment điều phối; hủy event một chiều có retry; không truy cập DB xuyên owner; mới hoàn toàn, không chuyển tài khoản cũ; follow/unfollow đơn giản; VND; Java 21/Boot 4.x; hướng OTel/Prometheus/Loki/Tempo/Grafana. Không hai organizer cộng tác sửa; không thêm cơ chế phát hiện sửa JSON ngầm. Không bỏ kiểm đúng amount/quantity, ownership, callback lặp và check-in cạnh tranh đã có ở B8.

B12-v0.2 được đồng bộ cùng B13/B14, bao gồm persistence phối hợp. Số bảng hiện hành, toàn bộ cột và giới hạn kiểm chứng đọc ở B12/validation; không dùng con số 36 của baseline cũ như số đích. Có thiết kế và DDL không có nghĩa dịch vụ/E2E đã chạy.

**Ưu tiên bắt buộc:** luồng chính đúng, dữ liệu quan sát dùng được, phục vụ thử tải/chèn lỗi/RCA; không tái thiết kế nghiệp vụ phụ chỉ để mô hình gọn/đẹp hơn (PRJ-016–021). Chọn chi tiết kỹ thuật đơn giản trong phạm vi đã chốt; hỏi trước khi thay hành vi, bỏ/hoãn chức năng đã duyệt. Không hỏi lại người dùng những điều xác minh được từ code.

## 3. COMPARISON — FE/BE cũ, chỉ trình logic chưa chốt

Nguồn legacy: `D:/Project/flash-ticket-system`, chỉ đọc. Phạm vi đã kiểm là các kiểu/payload và đoạn thực hiện layout/seat map, booking/payment, promotion, follow, phát hành và scheduler liên quan tới các điểm mở dưới đây; không tuyên bố đã audit toàn bộ repository hoặc chạy FE/BE cũ. Không dùng tên package/bảng cũ để tạo ranh giới đích.

| Mã | Bằng chứng cũ | Hướng xử lý (phần xác nhận dẫn riêng) | Ảnh hưởng dữ liệu/FE và đánh đổi | Nguồn đích |
|---|---|---|---|---|
| D-01 | `EventLayoutService` dùng một layout/event; FE đọc một layout, không phải danh sách | Giữ đúng một layout cho SEAT_MAP; QUANTITY không dùng layout. DRAFT được chưa có layout, gửi duyệt SEAT_MAP phải đủ cấu hình | Thêm UNIQUE(event_id). Giữ bảng riêng để không trộn payload editor vào events; không thêm version history/layout template trong đợt đầu | B12 §4.1; FR-02/03/07 |
| D-02 | `SeatMapPublishRequest` và `OrganizerSeatPublishPayload` có tọa độ, hàng/số, nhãn, ticketTypeId | Giữ các dữ kiện cần vẽ và chọn ghế; bổ sung cột cụ thể theo §3.1 | Không bắt FE viết lại renderer. Thêm FK ghép bảo vệ seat/type/sector cùng event; type có thể chưa gán khi DRAFT nhưng phải đủ khi gửi duyệt | FR-02/03/05/07; B12 §4.1 |
| D-03 | `buildSeatMapPublishPayload` ghi `sectorType`, capacity, type IDs/names, visible ở cả payload và mapData | Bỏ bản sao nghiệp vụ trong JSON hình học; API trả một chỗ chuẩn cho mỗi dữ kiện. Giữ renderer và ánh xạ tại adapter FE | Giảm nhánh fallback và sai khác cột/JSON. Shape/bounds/points còn JSON; capacity/type/price là cột/quan hệ | B12 §3/4.1; GOV-112 |
| D-04 | `SeatMapSyncService` lưu ghế inactive; `normalizeSeats` lại gán hidden=false khi đọc API; bản nháp local giữ hidden | Giữ lưu toàn bộ hoặc rollback (GOV-126). Giữ ẩn/hiện bền vững, không bán ghế ẩn, khôi phục đúng cờ và không mất type (GOV-127–130). Không mang nhánh sửa cấu hình trái khóa thương mại đã duyệt sang | Ẩn khác xóa. Không ép xóa ghế chỉ để bỏ flag; không thêm cộng tác hoặc lịch sử chỉnh sửa. Phép lưu không được xóa lén quan hệ còn dùng. Cơ chế xóa nháp là chi tiết kỹ thuật, không duyệt lại yêu cầu ẩn | FR-04/07; GOV-111/126–130 |
| D-05 | Form layout có sourceType/sourceId; công cụ tạo nhanh hình học trong editor là đường khác, không cần nguồn clone | Bỏ CLONED_FROM_VENUE/CLONED_FROM_EVENT (GOV-123); giữ công cụ tạo nhanh hình phục vụ seat map (GOV-124) | Không gộp mọi chữ template thành một tính năng. Không tự bỏ import/export hiện có hoặc viết thư viện clone mới. Không cần cột nguồn clone trong dữ liệu đích | PRJ-013/017; GOV-123/124 |
| D-06 | `seatMapEditorUtils` lấy tọa độ từ nhiều alias và suy loại sector; nhận các trường ticketTypeId/ticketTypeIds/tên trùng | Chỉ adapter chuyển API mới sang model renderer; không để backend trả nhiều biến thể cùng nghĩa. Trạng thái availability đọc từ Booking qua hợp đồng | Giảm if/fallback. Thiếu availability hiển thị chưa xác định, không mặc định AVAILABLE. Giá/loại ghế server xác định; FE không làm nguồn giá | FR-18; B12 §4.1/4.2 |
| D-07 | `FollowService` save/delete quan hệ rồi cập nhật statistics.followerCount ở document khác; `UserFollow` đã có compound unique | Giữ quan hệ riêng và unique pair, đổi follow/unfollow thành idempotent; chưa giữ counter followerCount riêng | Một nguồn sự thật, ít lỗi ghi hai document; đọc số lượng dùng count theo index. ID API phải chọn thống nhất identitySubject hoặc profile ID có ánh xạ, không trộn | BIZ-157, GOV-101; B12 §4.5 |
| D-08 | User cũ có organizer profile riêng; B12-v0.1 có bốn collection | Giữ organizer_profiles riêng, không gộp vào application (GOV-122) | Giữ bốn collection. Không xóa/gộp chỉ để giảm một nơi lưu. Xác nhận này không tự sao chép mọi field hoặc mở thêm API profile ngoài yêu cầu đích | B12 §4.5; GOV-122; FR-51–55/60 |
| D-09 | `PaymentGateway` đã là interface nhưng chỉ có tạo URL, verify và parse callback; factory chỉ có VNPAY | Giữ điểm nối adapter; bổ sung query/refund và một fake dùng trong test về sau | Không thêm cổng thanh toán nghiệp vụ hoặc service production. Hợp đồng fake phải đi qua đường ghi nhận thật của Payment, không cập nhật DB từ công cụ tải | B11-C §4; PRJ-014 |
| D-10 | FE `paymentService` chấp nhận nhiều alias paymentUrl/transactionNumber và ép mặc định rỗng/0; Return page gọi hàm confirmVNPayReturn tới IPN | Đặc tả một response chuẩn; FE Return chỉ yêu cầu backend đọc trạng thái, không là nguồn xác nhận tiền. Giữ redirect/polling UI phù hợp | Bỏ đoán field/fallback số tiền 0; có màn hình chờ khi thu xong nhưng vé chưa sẵn sàng. Không kết luận đây là lỗ hổng xác thực chỉ từ lời gọi FE; verifier phía server vẫn quan trọng | FR-24/25/30; B12 §4.3 |
| D-11 | `PromotionService.calculateDiscount` dùng HALF_UP scale 2 và có maxDiscountAmount; order cũ có CONFIRMED/REFUNDED | Không copy scale/enum; dùng đề xuất §4/5. Chưa mang cap giảm giá tối đa sang nếu không có yêu cầu đích | FE đổi format tiền/state mapping. Không mở lại nghĩa đã chốt: hoàn thừa không làm đổi đơn hợp lệ, không hoàn một phần | BIZ-150/158; FR-22/27/35 |
| D-12 | `OrderExpirationService` chia giao dịch theo từng đơn, fixedDelay 60s và batch 50; `TicketIssuanceService` có bộ tạo QR/HMAC | Giữ ý tưởng worker nhỏ theo từng đơn và phần render QR; không chốt lại các hằng 60s/50 hoặc định dạng QR chỉ vì code cũ dùng | Cấu hình worker chốt bằng nhu cầu và test. DDL bảo vệ hạn/định danh, worker không thay phép kiểm hết hạn ngay khi nhận lệnh. QR và cấp lại khóa thuộc B13, không quyết định từ mẫu cũ | B12 §3/4.2/4.4; FR-29/31 |

Các hành vi cũ trái yêu cầu đã duyệt (ghi DB chéo owner, chuyển nhượng vé, buộc hồ sơ ngoài phạm vi, khóa thương mại không đúng vòng đời) chỉ là việc phải sửa khi tái sử dụng, **không là lựa chọn nghiệp vụ đưa ra duyệt lại**.

### 3.1. Các cột seat map đề nghị chốt tại B12

Đây là lý do cho delta được đưa vào init sạch B12-v0.2, không phải migration ALTER dữ liệu cũ. Không tạo thêm service/bảng để phục vụ cộng tác.

| Bảng | Cột/constraint đề nghị | Ý nghĩa và cách dùng |
|---|---|---|
| event_layouts | `UNIQUE(event_id)` | Một layout cấu hình của event; không đồng nghĩa event DRAFT phải có ngay layout |
| event_layouts | `canvas_width INTEGER NOT NULL`, `canvas_height INTEGER NOT NULL`, cả hai > 0 | Kích thước hệ tọa độ; API giữ backgroundWidth/backgroundHeight nếu muốn giảm đổi FE |
| event_layouts | `background_image_reference TEXT NULL` | Tham chiếu ảnh nền; resolver dựng URL theo môi trường. Không cần vừa publicId vừa URL làm hai nguồn độc lập nếu chỉ dùng một storage adapter |
| event_layouts | `layout_data JSONB NOT NULL` | Form cố định chứa schemaVersion và dữ liệu trình bày không nằm ở cột. Không chứa nguồn giá/counter. Không chứa thêm bản sao đầy đủ các seat đã có cột |
| event_sectors | `code TEXT NULL`, `display_order INTEGER NOT NULL DEFAULT 0`, `color_code VARCHAR(7) NULL` | Mã hiển thị, thứ tự và màu; nếu có màu kiểm #RRGGBB. Không tự tạo uniqueness mã nếu chưa cần |
| event_sectors | `geometry JSONB NOT NULL` | Hình sector: shapeType, bounds và tham số của đúng loại hình; một hợp đồng có kiểu phân biệt, không chấp nhận nhiều alias |
| event_seats | `row_name TEXT NOT NULL`, `seat_number TEXT NOT NULL` | Giữ API và cách hiện hàng/số ghế; seat_label hiện có là nhãn trình bày chính. Quy tắc tạo nhãn cố định ở adapter/server |
| event_seats | `coord_x NUMERIC(10,2) NOT NULL`, `coord_y NUMERIC(10,2) NOT NULL` | Hệ tọa độ canvas nhất quán. Đã kiểm: kéo/resize/read/render không đọc relativeX/Y lưu trong metadata; đề xuất không lưu hai trường tỷ lệ này ở đích, giữ thuật toán tính tại FE (§3.3) |
| event_seats | `ticket_type_id UUID NULL` lúc DRAFT | FK ghép với event/sector; bắt buộc ghế bán được có type khi gửi duyệt. Cần unique key hỗ trợ ở ticket_types; thứ tự tạo FK phải sau bảng được tham chiếu |
| ticket_types | `color_code VARCHAR(7) NULL` | Đề xuất giữ màu nhóm vé phục vụ editor; màu seat lấy từ type, không có cột màu kinh doanh độc lập trên mỗi seat |
| Booking | thêm mapping type của seat vào nguồn cấu hình cục bộ phù hợp | Phải xác minh buyer chọn seat đúng type/giá. Chỉ seat ID và sector hiện tại chưa đủ chứng minh quan hệ này. Cột hay bảng snapshot cấu hình chuẩn chốt cùng payload Event→Booking |

Ghế ẩn đã chốt lưu bền và không bán (GOV-127–130); không hỏi lại “ẩn trong phiên hay lưu DB”. Đề xuất kỹ thuật: một cờ `is_hidden BOOLEAN NOT NULL DEFAULT false` trên cấu hình ghế, API editor cùng nghĩa; không dùng nó thay availability của Booking. Ẩn không xóa type đã gán; khi gửi duyệt kiểm type của ghế bán được. Thao tác xóa là việc khác. Tên cờ là chi tiết kỹ thuật của B12-v0.2, không phải tên do người dùng chỉ định.

Giữ stable IDs khi sửa tọa độ/nhãn. Không yêu cầu ETag/merge/khóa giao diện cộng tác. Transaction lưu cấu hình vẫn cần để không chỉ lưu nửa số ghế khi có lỗi; version phát cấu hình cho Booking vẫn cần cho thông điệp, không phải kịch bản hai organizer cùng sửa.

### 3.2. Kết quả đối chiếu và tiêu chí tích hợp

- Round-trip hàm tọa độ và cờ ẩn đã kiểm ở §3.3. Browser→API→DB đích chưa chạy vì chưa có dịch vụ; B13 đặc tả form để hiện thực phép kiểm này, không nhận kết quả hàm là E2E.
- Quan hệ ticket type/sector ở khu đứng: một pool nguồn cung, không nhân capacity theo số loại vé.
- Đường thanh toán FE phải hiểu trạng thái đang phát vé, không coi đã thu là vé đã sẵn sàng.
- Email đã chọn: điền sẵn từ tài khoản, cho sửa trước tạo đơn và lưu theo đơn (GOV-141/142). Không thêm customerPhone/customerNote chỉ vì request cũ có.
- Snapshot tiền phải có thời điểm đóng băng. Đơn còn áp promotion không được gửi như snapshot thanh toán bất biến cuối cùng.
- Quyền xóa cấu hình DRAFT có kiểm soát: baseline runtime hiện không có DELETE, cần giải quyết bằng cấp quyền hẹp/thao tác giới hạn sau contract, không cấp DELETE toàn database.

### 3.3. COMPARISON — kết quả kiểm tọa độ và ghế ẩn, 2026-09-13

Phạm vi: tìm mọi tham chiếu relativeX/relativeY/coordMetadata trong mã nguồn repo cũ; đọc đường kéo, resize, publish, API response, dựng editor, local draft và buyer renderer. Chạy trực tiếp hàm TypeScript cũ qua TypeScript transpiler đã có, chỉ trong bộ nhớ; không sửa repo cũ, không khởi động browser/BE/DB.

| Điểm | Kết quả FACT | Nguồn trong repo cũ |
|---|---|---|
| FE gửi tỷ lệ | Payload ghi coordX/Y (2 số lẻ) và coordMetadata.relativeX/Y (4 số lẻ), tính từ vị trí ghế và bounds | `frontend/src/components/seat-map/editor/seatMapEditorUtils.ts:1146` |
| Kéo vùng | Cộng delta vào bounds và từng seat.x/y, không đọc tỷ lệ đã lưu | Cùng file, `translateShape:506` |
| Resize vùng/khối ghế | Tính tỷ lệ tức thời từ seat.x/y và previousBounds, rồi đặt vào nextBounds | Cùng file, `scaleSeatsWithinBounds:525`; `useSeatMapEditorState.ts:452/615` |
| Lưu BE | Copy metadata vào JSON; không có consumer tọa độ relative trong các tham chiếu đã tìm | `core-service/.../event/service/SeatMapSyncService.java:759` |
| Đọc API | SeatDto trả coordX/Y, isActive, type; không trả coordMetadata | `core-service/.../event/dto/SeatMapResponse.java:46` |
| Buyer vẽ | Đọc coordX/Y; radius từ sector.mapData.seatLayout, không từ metadata từng ghế | `frontend/src/components/seat-map/runtime/BuyerSeatMapCanvas.tsx:58/281` |
| Tải editor từ API | Đọc coordX/Y nhưng gán hidden=false và manualAdjusted=false | `seatMapEditorUtils.ts:392` |
| Bản nháp local | Serialize document, deserialize giữ hidden; hook ưu tiên local draft nếu có | `seatMapEditorUtils.ts:967/1007`; `useSeatMapEditorState.ts:108` |

Kết quả chạy trực tiếp: payload ghế (150,75) trong bounds (100,50,200,100) tạo tỷ lệ (.25,.25); kéo (+30,-10) ra (180,65); resize sang bounds (200,100,400,200) ra (300,150), không cần metadata tỷ lệ. 100 mẫu resize đạt sai số <1e-9. Fixture API có isActive=false được dựng thành hidden=false — tái hiện lỗi hàm chuyển dữ liệu. Cùng ghế qua serialize/deserialize local giữ hidden=true. Đây là kiểm hàm thật với fixture, không phải test giao diện/DB end-to-end.

**Đề xuất kỹ thuật CANDIDATE:** không lưu relativeX/Y trong payload/DB đích; giữ coordX/Y, bounds và thuật toán kéo/resize cũ. Giữ seatLayout/points và các tham số renderer thực dùng; không xóa toàn bộ metadata chỉ từ kết luận về hai field này. Sửa adapter đọc cờ ẩn và BE không bỏ type khi ẩn. Local draft có thể che lỗi tải API nên bằng chứng round-trip phải phân biệt hai đường, không đánh đồng “trình duyệt còn bản nháp” với “server lưu đúng”. Đề xuất không cần thay renderer hoặc tạo bảng mới.

## 4. FORMATION — đề xuất bảng chuyển trạng thái để duyệt

Nguồn sinh nghĩa là B8 FR-16–48 và B12, không phải enum của Order cũ. Bảng này mô tả kết quả nghiệp vụ cần đạt; không tuyên bố có một transaction SQL xuyên các service. Enum, event và mốc phân xử được duyệt riêng ở B12/B13/B14.

### S-01. Order do Booking sở hữu — sáu trạng thái đã duyệt về nghĩa

Tên/nghĩa, bảng chuyển trạng thái và tổ hợp S-01–S-05 đã được Minh duyệt qua Q-06 (GOV-136), tiếp nối xác nhận ISSUING tại GOV-131. Trạng thái lưu trên cùng bản ghi order; các mốc/guard S-06 còn mở được tách riêng, không được suy là đã hoàn thành.

| Trạng thái | Nghĩa hiển thị |
|---|---|
| PENDING_PAYMENT | Đơn đã tạo, còn giữ chỗ, chưa được chấp nhận thanh toán hợp lệ |
| ISSUING | Thanh toán hợp lệ đã được chấp nhận cho đơn; đang hoàn tất phát hành vé |
| COMPLETED | Đã phát hành đầy đủ vé |
| CANCELLED | Đơn đã bị hủy theo điều kiện buyer/hủy event |
| EXPIRED | Đơn hết hạn trước khi thanh toán hợp lệ được chấp nhận |
| ISSUANCE_FAILED | Đã thu hợp lệ nhưng phát hành không thể hoàn tất; đang/đã xử lý bù trừ |

| Từ → tới | Nguồn kích hoạt và điều kiện | Hậu quả / lặp |
|---|---|---|
| Chưa có → PENDING_PAYMENT | Buyer tạo đơn; cấu hình bán và nguồn cung/hạn mức hợp lệ | Tạo order/items/reservation/allocations, giữ nguồn cung/hạn mức cùng transaction Booking |
| PENDING_PAYMENT → PENDING_PAYMENT | Áp mã hợp lệ; hoặc lần thanh toán thất bại mà đơn còn hạn | Áp mã giữ lượt và cập nhật tiền cùng transaction; thất bại một attempt không hủy đơn |
| PENDING_PAYMENT → ISSUING | Kết quả thu được xác minh và được chấp nhận theo mốc phân xử S-06 | Chốt giữ chỗ/nguồn cung/giới hạn đúng một lần; Payment điều phối phát hành; không nhận buyer cancel sau mốc này |
| PENDING_PAYMENT → CANCELLED | Buyer hủy đúng điều kiện FR-28; hoặc nhận hủy event | Trả phần đang giữ đúng một lần; nếu event đã hủy thì không mở lại nguồn cung |
| PENDING_PAYMENT → EXPIRED | Đến hạn, chưa có kết quả thanh toán hợp lệ được chấp nhận | Trả đúng một lần; thu tới sau không làm sống lại đơn |
| ISSUING → COMPLETED | Ticket xác nhận đủ bộ vé | Cập nhật projection đơn idempotent; không dùng email gửi thành công làm điều kiện |
| ISSUING → ISSUANCE_FAILED | Ticket xác nhận thất bại dứt điểm theo S-04, không phải chỉ timeout | Vô hiệu phần dở dang, hoàn toàn bộ charge hợp lệ; trả promotion; nguồn cung/hạn mức chỉ trả nếu còn đủ điều kiện bán |
| ISSUING/COMPLETED → CANCELLED | Hủy event hợp lệ được áp dụng | Không mở lại nguồn cung; vé không còn vào cửa; hoàn charge hợp lệ theo nguyên nhân hủy |
| CANCELLED/EXPIRED/ISSUANCE_FAILED → giữ nguyên | Nhận lại lệnh/kết quả cũ, hoặc hoàn tiền tiến triển | Không hồi sinh đơn, không trả tài nguyên lần nữa; trạng thái hoàn thể hiện riêng |
| COMPLETED → giữ nguyên | Hoàn một charge thu thừa/đến muộn | Không làm hỏng đơn/vé hợp lệ (FR-27/36) |

COMPLETED không phải trạng thái hết khả năng thay đổi tuyệt đối: event vẫn có thể bị hủy trước thời gian bắt đầu. EXPIRED, ISSUANCE_FAILED không cần đổi thành REFUNDED; lý do và tiến độ tiền đọc riêng. Khi event bị hủy sau một lỗi phát hành, giữ trạng thái thất bại của đơn và áp quy tắc hủy event ở dữ liệu event/refund; không tạo vòng chuyển qua lại.

### S-02. Payment không dùng một status chung cho mọi ý nghĩa

| Dữ liệu | Biểu diễn đề xuất | Chuyển trạng thái |
|---|---|---|
| payment_attempts | PENDING, UNKNOWN, SUCCEEDED, FAILED | PENDING→UNKNOWN khi chưa rõ kết quả; PENDING/UNKNOWN→SUCCEEDED hoặc FAILED khi có bằng chứng. UNKNOWN vẫn chưa kết thúc, không mở attempt song song. Thất bại ở đây là kết quả lần thử, không xóa charge thực thu phát hiện sau |
| charges | Bản ghi sự thật thực thu | Thêm sau xác minh; không đổi thành FAILED/REFUNDED để xóa lịch sử tiền vào |
| payment_confirmations | Liên kết duy nhất order→charge hợp lệ | Chỉ tạo khi đủ điều kiện; thu thừa không thay liên kết |
| UI payment summary | Có/không có thanh toán hợp lệ + số tiền/khoản hoàn + attempt gần nhất | Projection, không thêm enum bền vững trộn ba bảng |

B12-v0.2 chọn một biểu diễn trạng thái trong DDL; không duy trì hai nguồn trạng thái độc lập. finished_at là mốc kết thúc, không thay nghĩa của kết quả.

### S-03. Refund: tách lần gọi với yêu cầu logic

PENDING → PROCESSING → SUCCEEDED. PROCESSING có thể chuyển FAILED khi lần xử lý có lỗi đã xác định; FAILED → PROCESSING khi retry. **FAILED không là kết thúc vĩnh viễn của nghĩa vụ hoàn** theo FR-37. Timeout chưa rõ kết quả giữ PROCESSING/nhãn kỹ thuật chờ đối chiếu, không tự gửi một khoản hoàn mới. SUCCEEDED không quay lại; nhiều nguyên nhân hội tụ về cùng refund/charge. Lịch sử từng lần gọi nằm ở refund_attempts.

### S-04. Issuance và ticket

- Issuance: PENDING → COMPLETED khi đủ tất cả vé; PENDING → FAILED chỉ khi thất bại đã xác định và việc phát vé không còn có thể commit về sau. Lỗi tạm thời giữ PENDING để retry.
- Retry cùng order và cùng dữ liệu mua trả cùng tập vé. Nếu đã FAILED dứt điểm, không tự phát lại bằng lệnh cũ trong lúc đang bù trừ.
- Ticket: VALID → USED khi check-in hợp lệ; VALID → VOID khi nguyên nhân nghiệp vụ yêu cầu vô hiệu. USED và VOID không trở lại VALID. Với phạm vi hiện tại hủy event chỉ trước start, nên không tạo luồng hoàn tác USED→VOID sau một check-in hợp lệ trong cửa sổ.
- Không có ticket REFUNDED. Delivery không quyết định hiệu lực: gửi email lỗi không làm ticket VOID.

### S-05. Tổ hợp mẫu cần thống nhất FE/BE

| Tình huống | Order | Tiền | Vé |
|---|---|---|---|
| Chưa thanh toán | PENDING_PAYMENT | Chưa có confirmation | Chưa có vé |
| Một attempt thất bại, đơn còn hạn | PENDING_PAYMENT | Attempt FAILED | Chưa có vé |
| Thu hợp lệ, đang phát | ISSUING | Có confirmation | Đang tạo, chưa công bố bộ vé hoàn chỉnh |
| Mua thành công, email lỗi | COMPLETED | Có confirmation | VALID; delivery lỗi riêng |
| Đã dùng vé | COMPLETED | Giữ nguyên | USED cho từng vé đã quét |
| Thu đến sau hết hạn | EXPIRED | Charge và refund riêng, không confirmation hợp lệ cho đơn đã hết hạn | Không phát |
| Thu thừa sau mua thành công | COMPLETED | Confirmation cũ + refund charge thừa | Giữ hiệu lực cũ |
| Phát hành thất bại xác định | ISSUANCE_FAILED | Refund PENDING/PROCESSING/SUCCEEDED riêng | Không còn bộ vé hợp lệ |
| Event hủy trước bắt đầu | CANCELLED cho đơn đang hoạt động/đã hoàn tất | Hoàn charge hợp lệ; charge thừa theo nhánh riêng | VOID; không mở lại nguồn cung |

### S-06. Giao điểm còn phải chốt bằng sequence trước code

Không che các điểm này bằng chữ SUCCESS:

1. Mốc phân xử đã chốt Q-02 (GOV-138): Booking kiểm thời điểm chấp nhận trong transaction so với expires_at và trạng thái đơn; hết hạn/hủy thì không hồi sinh. Payment chỉ phát vé sau kết quả chấp nhận; khoản muộn theo luồng hoàn hiện có. Operation ID, khóa và phát lại kết quả là chi tiết kỹ thuật phải thể hiện trong sequence, không hỏi lại mốc người dùng đã chọn.
2. Chống completion đến sau quyết định compensation: Ticket phải có kết quả terminal đáng tin và rào trạng thái trước khi Payment hoàn vì lỗi phát hành. Timeout riêng lẻ không cho quyền hoàn.
3. Hủy event có độ trễ: đã chấp nhận lan truyền bất đồng bộ và xử lý hủy/hoàn cho giao dịch lọt trong khoảng trễ (GOV-134/135). Sequence kỹ thuật phải thể hiện đúng lựa chọn này và cách Payment hội tụ nguyên nhân hoàn; không hỏi lại có chấp nhận độ trễ hay không.
4. Khoản thu lệch số tiền hoặc không có attempt: case Q-07 đã được loại khỏi phạm vi (PRJ-024), không tạo bảng/luồng tiếp nhận và xử lý ngoại lệ. Vẫn kiểm callback trước chấp nhận; không tạo giả confirmation hoặc phát vé khi không khớp. Không tuyên bố phục hồi/đối chiếu đủ case này. Thu muộn/thu trùng đã liên kết được vẫn thuộc luồng hoàn đã duyệt.
5. Freeze tiền với promotion: chính sách đã chốt đóng băng từ lần bắt đầu thanh toán đầu tiên, không đổi mã sau mốc này và retry cùng số tiền (GOV-132/133). Sequence kỹ thuật phải đặt bước đóng băng trước mở attempt; không hỏi lại chính sách khi attempt thất bại.

§4.1–4.5 trình bày tên/nghĩa trạng thái; sequence, guard, thứ tự khóa và retry hiện đã có tại B13/B14-v0.1. Duyệt cùng ba nguồn để tránh chỉ duyệt enum mà thiếu hành vi; chưa có bằng chứng chạy service.

## 5. FORMATION — quy ước VND đề nghị duyệt

VND đã xác nhận tại BIZ-158; giá vé/giá trị giảm giá nhập số nguyên tại GOV-137. HALF_UP giảm trên tổng đơn và phí trên doanh thu hợp lệ event đã được xác nhận rõ tại GOV-139/140. Đây là quy ước cho mô hình đồ án, không phải tư vấn quy định kế toán/thuế thực tế.

| Mã | Lựa chọn đề xuất | Vì sao / đánh đổi |
|---|---|---|
| M-01 | Mọi giá/tiền nghiệp vụ là số nguyên đồng VND; DB `NUMERIC(19,0)`, Java `BigDecimal`, currency cố định VND | Giữ số chính xác; bỏ scale 4 của baseline. DB có thể làm tròn khi ép scale, nên ứng dụng phải reject số tiền đầu vào có phần lẻ trước khi ghi, không âm thầm làm tròn giá người nhập |
| M-02 | Giá trị phần trăm giảm giá nhập nguyên theo GOV-137, ví dụ 10%, không 10.5%; không bắt buộc biểu diễn tỷ lệ nội bộ thành số nguyên | 10% vẫn có thể tính nội bộ bằng 0.10 với BigDecimal; đây không phải cho nhập tiền lẻ. Đề xuất áp dụng đầu vào nguyên tương tự cho tỷ lệ phí; precision nhập tỷ lệ phí là chi tiết kỹ thuật, không gộp vào xác nhận giá vé/phiếu giảm giá; rounding đã chốt GOV-140 |
| M-03 | Line subtotal = quantity × unit price; subtotal = tổng dòng. Giảm phần trăm tính một lần trên subtotal, HALF_UP về nguyên đồng; giảm cố định không có phần lẻ | Tránh tổng giảm bị lệch vì làm tròn từng vé. Phải reject tổng cuối <= 0 theo FR-22, không tự ép lên 1 VND hoặc tự thêm cap giảm giá ngoài yêu cầu |
| M-04 | Tổng cuối = subtotal − discount. Amount API dùng chuỗi số nguyên thập phân không dấu phân cách, ví dụ "90004" | Đồng nhất giữa Java/JSON/FE và không mất số với NUMERIC(19,0). Đổi lại FE phải dùng helper format/parse có kiểm, không Number(value || 0). Nếu muốn number JSON để ít sửa FE phải chọn lại giới hạn số an toàn rõ ràng |
| M-05 | Phí tính một lần trên doanh thu hợp lệ sau giảm giá và sau hoàn hợp lệ của event; HALF_UP về nguyên đồng. Net = doanh thu đó − phí | Không trộn khoản thu thừa vào doanh thu. Làm tròn mỗi đơn có thể khác tổng event; chọn tổng event khớp một lần đối soát/chi trả. Ví dụ hai đơn 100050, phí 1%: round(200100×1%)=2001, không phải 1001+1001=2002 |
| M-06 | Hoàn toàn bộ đúng amount của charge; không tính lại giá/discount/fee hiện tại | Đây là áp dụng FR-35; không làm tròn lần nữa. Khoản chưa khớp/không liên kết được thuộc giới hạn Q-07; không sửa số tiền để giả khớp đơn |
| M-07 | Adapter VNPay chuyển VND→vnp_Amount bằng ×100 đúng một lần và chuyển ngược có kiểm; không để DB/FE cùng nhân | Hợp đồng VNPay mô tả vnp_Amount Numeric[1,12], nên phải kiểm giới hạn provider ngoài giới hạn DB. Không cho gửi khoản vượt hỗ trợ của cổng; mức 12 chữ số phải được kiểm lại theo hợp đồng merchant trước integration |

Ví dụ M-03: subtotal 100005; giảm 10% = 10000.5 → 10001; total = 90004 VND; adapter gửi `vnp_Amount=9000400`. Đây là ví dụ tính, không phải dữ liệu seed hoặc giao dịch đã chạy.

Ca nghiệm thu tiền: giá nhập có phần lẻ bị từ chối; giá <=0 bị từ chối; tỷ lệ ngoài phạm vi bị từ chối; phần đúng .5 làm tròn HALF_UP; total <=0 bị từ chối; tổng các dòng đúng subtotal; số tiền lớn vẫn giữ nguyên qua JSON; payment và refund round-trip ×100 không lệch; phí sau toàn bộ hoàn hợp lệ bằng 0; khoản thu thừa không làm tăng net.

Nguồn provider: [VNPay PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html), kiểm 2026-09-13. API quy định cách biểu diễn amount; **không** quy định thay nhóm cách làm tròn phí nền tảng. M-03/05 là xác nhận GOV-139/140; M-02 phần biểu diễn kỹ thuật và M-04 là chi tiết triển khai CANDIDATE.

## 6. JSON cố định: giảm biến thể, không tạo hệ thống chống sửa ngầm

Một form cho từng loại payload, không một JSON khổng lồ dùng cho tất cả service. Có tên trường duy nhất, kiểu, required/nullable, enum và version cấu trúc. `schemaVersion` là phiên bản hình dạng; `sourceConfigVersion` là phiên bản nội dung cấu hình Event; chúng không phải khóa cộng tác editor.

Mẫu rút gọn để giải thích tiền (không phải payload gửi được). Payload đầy đủ có email/display/expiry/mode và trường phối hợp được đặc tả tại B13:

```json
{
  "schemaVersion": 1,
  "orderId": "10000000-0000-0000-0000-000000000001",
  "eventId": "20000000-0000-0000-0000-000000000001",
  "buyerSubject": "example-subject",
  "sourceConfigVersion": 1,
  "currency": "VND",
  "subtotal": "100000",
  "discount": "0",
  "total": "100000",
  "items": [
    {
      "orderItemId": "30000000-0000-0000-0000-000000000001",
      "ticketTypeId": "40000000-0000-0000-0000-000000000001",
      "quantity": 1,
      "unitPrice": "100000",
      "lineSubtotal": "100000",
      "sectorId": null,
      "seatIds": []
    }
  ]
}
```

Kiểm tự động đề xuất: loại payload đúng, trường bắt buộc đủ, không alias, quantity dương, sum đúng, ID không trùng, seatIds phù hợp mode và type. Những kiểm này bắt lỗi lập trình/FE cũ gửi sai form, không phải hệ thống điều tra người can thiệp DB. Không thêm hash chain, ký JSON nội bộ hoặc lịch sử mọi lần sửa. Hash input sẵn có nếu còn dùng chỉ phục vụ phát hiện retry cùng định danh khác dữ liệu; việc giữ/bỏ phải chọn trong hợp đồng, không lẫn với chữ ký cổng.

Hình học seat map có nhiều shape trong renderer cũ; form cố định có thể là tagged union theo shapeType, không phải mỗi event tự tạo field mới. Cần liệt kê tham số của các shape thực sự được giữ trước khi viết JSON Schema. Không tuyên bố mẫu purchase ở trên đã giải quyết hình học hoặc đủ dữ liệu email/QR.

## 7. Bộ triển khai và giới hạn

GOV-143 đã duyệt phạm vi trong [bản đồ tác động](B12-B16-completion-impact-map.md). Nguồn đã đồng bộ trước contract/SQL/ERD. Init tạo bảng mới, không chuyển dữ liệu cũ. Script `95-design-rationale.sql` ghi lý do và tradeoff vào catalog; runner gọi cùng bộ init.

B13/B14 có định danh lệnh, bước đóng băng tiền, phân xử expiry, chặn phát hành sau thất bại dứt điểm, retry và hủy event hội tụ. B15 ghi rõ test nào đã chạy; B16/readiness chỉ ra dữ liệu quan sát và việc code đầu tiên. Chưa có service thật thì không tuyên bố concurrency/E2E đạt.

Realm fixture không nằm trong danh sách sửa của lượt này. Không triển khai AWS, không nhập realm, không chạy thử tải hoặc sửa phương pháp RCA. Bản hoàn thiện vẫn cần Minh duyệt gate, không cần chọn lại những nghiệp vụ đã xác nhận.

## 8. Stack quan sát và điều kiện không cản RCA

GOV-117–121 chốt hướng OTel/Prometheus/Loki/Tempo/Grafana. B16-v0.1 đã có tuyến Collector và đề xuất retention/sampling/budget; các giá trị kỹ thuật còn CANDIDATE chờ duyệt/đo, không phải kết quả vận hành. Nguồn yêu cầu là B8 NFR-05/06/08/09 và B11-C §5, không tự sinh một phương pháp RCA.

- Mã tương quan nghiệp vụ phải qua được callback, retry và thông điệp; trace ID riêng lẻ không chắc phủ một giao dịch dài nhiều lượt. Không giả định VNPay truyền lại traceparent; liên kết callback bằng attempt/order đã biết.
- Auto instrumentation không biết mọi bước nghiệp vụ: phải thấy giữ chỗ/phát hành/hoàn và cạnh async qua outbox/RabbitMQ. Dữ liệu đủ dựng dependency graph không đồng nghĩa quan hệ đó là quan hệ nhân quả đã được chứng minh.
- Sampling có thể bỏ trace chứa lỗi; giữ dữ liệu của phiên thí nghiệm hữu hạn theo thiết kế đo, không bật 100% vô hạn mặc định. Tỷ lệ cuối thuộc B16 và nhu cầu thực nghiệm qua cửa nối đã quy định.
- Không dùng orderId/traceId/userId làm label metric hoặc indexed label Loki. Giữ chúng trong log fields/structured metadata thích hợp; nhận diện node bằng tên service/instance/host/DB/queue ổn định.
- Collector có thể nghẽn/drop; đồng hồ máy lệch có thể làm sai thứ tự. Theo dõi khả năng thu nhận và đồng bộ thời gian; không coi mất telemetry là bằng chứng nghiệp vụ không xảy ra.
- Cổng ngoài chỉ quan sát được ranh giới lời gọi/response của nhóm; không biết nguyên nhân nội bộ ngân hàng chỉ từ một timeout.
- API kho quan sát cần adapter và phân trang/retention/export dữ liệu phiên đo; ảnh dashboard không là dataset tái lập.
- Không để collector/RCA ghi nghiệp vụ; không đưa secret/QR/JWT vào log hoặc ra API mô hình ngôn ngữ.

Nguồn kiểm ngày 2026-09-13: [CNCF khảo sát 2025](https://www.cncf.io/announcements/2026/01/20/kubernetes-established-as-the-de-facto-operating-system-for-ai-as-production-use-hits-82-in-2025-cncf-annual-cloud-native-survey/), [CNCF bài tổng hợp observability 2026](https://www.cncf.io/blog/2026/05/06/the-tools-are-ready-so-why-are-most-cloud-native-teams-still-running-three-observability-stacks/), [OTel sampling](https://opentelemetry.io/docs/concepts/sampling/), [OTel messaging](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/), [Loki cardinality](https://grafana.com/docs/loki/latest/get-started/labels/cardinality/). Đây là căn cứ mức phổ biến/tính năng và rủi ro kỹ thuật, không chứng minh bộ stack này làm RCA của nhóm chính xác hơn.

## 9. Sổ xác nhận Q-01–07 hiện hành

| Mục | Đã xác nhận | Căn cứ |
|---|---|---|
| Q-01 | Đầu vào giá/giảm nguyên; HALF_UP giảm một lần/tổng đơn, phí một lần/doanh thu hợp lệ event | GOV-137/139/140; BIZ-158 |
| Q-02 | Booking phân xử tại thời điểm kiểm trong transaction so expires_at; hết hạn/hủy không hồi sinh | GOV-138 |
| Q-03 | Đóng băng tiền từ lần bắt đầu thanh toán đầu tiên, retry giữ nguyên | GOV-132/133 |
| Q-04 | Email điền sẵn, cho sửa trước tạo đơn và lưu theo đơn | GOV-141/142 |
| Q-05 | Chấp nhận độ trễ hủy, giao dịch lọt phải hội tụ hủy/hoàn | GOV-134/135 |
| Q-06 | Giữ tên/nghĩa/chuyển trạng thái S-01–05, gồm ISSUING | GOV-131/136 |
| Q-07 | Không xây luồng/bảng ngoại lệ; giữ validation và hoàn muộn/trùng có liên kết | PRJ-024 |

Bằng chứng email ở repo cũ (COMPARISON): SelectTicketPage.tsx:103–107 điền token.email; :1037–1039 cho sửa; :522 gửi customerEmail. BookingService.java:459 lưu email trên order; TicketIssuanceService.java:122 sử dụng nó. CheckoutPage.tsx:479–481 khóa ô ở bước sau, không có nghĩa email chưa từng được sửa.

## 10. Lịch sử và nội dung báo cáo

- v0.1–v0.4: hình thành/giải thích D/S/M và ghi các xác nhận từng phần; không có thay đổi CREATE TABLE trong những lượt đó.
- v0.5: ghi hai xác nhận cuối GOV-139–142; còn các đoạn đề xuất lịch sử trong thân.
- v0.6 (2026-09-14): bỏ các đoạn lịch sử gây hiểu lầm “còn OPEN”, đặc biệt đề xuất Q-04 chỉ email tài khoản và bảng ngoại lệ Q-07 đã bị bỏ; giữ kết quả đối chiếu và S/M để kiểm hợp đồng. Đồng bộ theo GOV-143, không AI duyệt gate.

Báo cáo dùng lập luận yêu cầu→bất biến→owner→dữ liệu→ràng buộc→phép kiểm, nhất là hạn mức buyer/event, snapshot tiền, ISSUING và idempotency. Bằng chứng tái sử dụng ở §3 là hồ sơ kỹ thuật nội bộ, không dùng làm lý do phân rã service. Công bố giới hạn Q-07, độ trễ hủy và test chưa chạy.
