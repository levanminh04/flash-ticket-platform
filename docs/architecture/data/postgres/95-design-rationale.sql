-- B12: chú thích thiết kế, không phải migration dữ liệu hoặc thay đổi cấu trúc.
-- Nguồn: B12-v0.2 §2–7; B8-v0.12; decision-register GOV-108–113 và BIZ-153–158.
-- Chạy SAU 10-event, 20-booking, 30-payment, 40-ticket trên baseline local mới.
-- Ví dụ từ thư mục repo, bằng tài khoản triển khai đã có quyền SET ROLE bốn owner:
-- psql -X -v ON_ERROR_STOP=1 -f docs/architecture/data/postgres/95-design-rationale.sql
-- Không dùng runtime app credential; không tự chạy lên AWS. Không chứa secret.
-- Runner gọi tệp này sau 90-grants; catalog test kiểm mọi bảng có rationale.
-- COMMENT ON thay nội dung comment cũ; các giới hạn quan trọng được giữ bên dưới.
-- Không có giao dịch nguyên tử xuyên bốn database; mỗi owner commit riêng.
\set ON_ERROR_STOP on

\connect event_db
BEGIN;
SET LOCAL ROLE event_owner;
SET LOCAL search_path = event_schema, pg_catalog;

COMMENT ON TABLE categories IS 'Mục đích: nhãn phân loại dùng lại cho nhiều sự kiện (FR-64). Tách để tham chiếu thống nhất thay vì lặp tên; đổi lại có lookup/FK. Không suy thêm hệ thống taxonomy phân cấp hoặc nhiều category/event ngoài phạm vi hiện tại.';
COMMENT ON TABLE venues IS 'Mục đích: địa điểm được nhiều sự kiện dùng lại (FR-63). Tách để tránh lặp dữ liệu địa điểm; đổi lại cần tham chiếu. Không lưu hoặc kiểm sức chứa vật lý: nguồn cung thương mại do organizer quyết định.';
COMMENT ON TABLE events IS 'Mục đích: sự kiện và cấu hình đơn trị: cửa sổ bán, sales mode, giới hạn mua, tỷ lệ phí. Không tách các cột cấu hình chỉ vì có tên khác. Không giữ tickets_sold làm nguồn sự thật thứ hai. Trạng thái/cấu hình khóa theo FR-07 cần service transaction; CHECK không tự bảo vệ chuyển trạng thái. B12 còn DRAFT; không suy thêm kiểm sức chứa venue.';
COMMENT ON TABLE event_images IS 'Mục đích: một sự kiện có tập ảnh và thứ tự hiển thị. Tách tập phần tử lặp thay vì thêm image1/image2; đổi lại có bảng con và truy vấn tập ảnh. Lưu tham chiếu object, không nhúng binary ảnh hoặc secret truy cập.';
COMMENT ON TABLE event_layouts IS 'Một layout/event với UNIQUE(event_id); tách dữ liệu editor khỏi events để không kéo hình học vào đường đọc thương mại. Trade-off: thêm lookup, JSON phải theo form B13. Có canvas/ảnh nền/decorations, không clone source hoặc lịch sử template.';
COMMENT ON TABLE event_sectors IS 'Nhiều sector/event, SEATED/STANDING, capacity cấu hình và geometry có kiểu. Trade-off: bảng con/FK và adapter renderer; configured_capacity không phải counter bán của Booking.';
COMMENT ON TABLE event_seats IS 'Ghế cấu hình có stable ID, hàng/số/tọa độ/type/is_hidden. Ẩn vẫn giữ type và không bán; xóa là thao tác khác. Trade-off: nhiều dòng, cần lưu trọn bản nháp; không giữ trạng thái reservation của Booking.';
COMMENT ON TABLE ticket_types IS 'Mục đích: các loại vé và giá cấu hình của sự kiện (FR-02/05). Tách tập nhiều loại vé thay vì lặp cột trong events. Counter bán thuộc Booking; giá dòng đơn giữ snapshot để không bị thay bởi giá hiện tại. Mapping loại vé với ghế cụ thể còn cần hoàn thiện hợp đồng.';
COMMENT ON TABLE promotions IS 'Nhiều mã/event; code trim-uppercase duy nhất, fixed VND nguyên hoặc integer percent; tự hiệu lực theo thời gian. Trade-off: Booking cần bản sao version; không thêm bật/tắt hay maximum discount cap.';
COMMENT ON TABLE event_cancellation_requests IS 'Lịch sử yêu cầu hủy và kết quả trên cùng bản ghi; một PENDING/event bằng partial unique. Gửi lại sau REJECTED; không gửi mới khi CANCELLED (service guard). Trade-off: thêm bảng để không ghi đè lịch sử, không thêm bảng kết quả riêng.';
COMMENT ON COLUMN events.purchase_limit IS 'Quy tắc chung của event, ví dụ 5 vé/tài khoản; không biểu diễn mỗi buyer đang giữ/đã mua bao nhiêu. Trạng thái áp dụng theo buyer/event nằm ở Booking purchase_limits.';
COMMENT ON COLUMN event_layouts.layout_data IS 'Trade-off JSON: thuận tiện biểu diễn hình học nhưng mất kiểm kiểu từng trường ở SQL. Cần một form cố định/DTO validation; object không đủ. Không xây cơ chế riêng chống người âm thầm sửa JSON (GOV-112/113). Không dùng JSON để thay FK và nguồn sự thật tiền/nguồn cung.';
COMMIT;

\connect booking_db
BEGIN;
SET LOCAL ROLE booking_owner;
SET LOCAL search_path = booking_schema, pg_catalog;

COMMENT ON TABLE event_sales_snapshots IS 'Mục đích: Booking có cấu hình Event cần cho quyết định giữ chỗ, không JOIN database Event hoặc gọi Event mỗi lần. Trade-off: giảm phụ thuộc runtime nhưng thêm dữ liệu sao chép và nguy cơ trễ. Source version nhận diện cấu hình, không phải khóa cộng tác editor; TTL/received_at không chứng minh đã nhận hủy mới nhất. Thiếu cấu hình hợp lệ thì không nhận giữ chỗ. B13 phải chốt cập nhật và điểm hủy hiệu lực; không reset counter bán khi nhận lại snapshot.';
COMMENT ON TABLE orders IS 'Một giao dịch mua, email theo lần mua, S-01 lifecycle riêng tiền/vé. Freeze lưu nguyên PurchaseSnapshot trước attempt đầu, retry không dựng lại từ giá hiện tại. Trade-off: dữ liệu sao chép; tổng items, state transition, deadline cần transaction.';
COMMENT ON TABLE order_items IS 'Mục đích: nhiều dòng lựa chọn trong một đơn; giữ quantity và giá tại lúc mua để lịch sử không đổi theo cấu hình hiện tại. Trade-off: lặp tên/giá có chủ đích và phải kiểm tổng dòng với đơn. Sau tạo đơn không đổi lựa chọn (FR-20). selection_snapshot là dữ liệu bổ trợ có form cố định, không thay cột số tiền có kiểm tra.';
COMMENT ON TABLE reservations IS 'Mục đích: nhận diện chính xác một lượt giữ của đơn, trạng thái và hạn chung để chốt/trả đúng lượt. UNIQUE(order_id) không có nghĩa bỏ định danh giữ chỗ hoặc gộp root đã mô hình hóa. Trade-off: thêm dòng/quan hệ nhưng giảm lẫn lệnh release cũ với lượt mới; tạo đơn và giữ chỗ cùng transaction Booking.';
COMMENT ON TABLE ticket_type_inventory IS 'Mục đích: nguồn cung theo loại vé cho QUANTITY. Counter giúp cập nhật có điều kiện tại điểm tranh chấp thay vì COUNT nhiều đơn; đổi lại phải cập nhật held/purchased cùng giữ chỗ và kiểm đối chiếu. Không dùng counter này để trừ thêm lần nữa cho ghế SEATED hoặc pool STANDING đã có nguồn cung riêng.';
COMMENT ON TABLE sector_inventory IS 'Mục đích: pool dùng chung của sector STANDING, tránh bán vượt khi nhiều loại vé cùng dùng một khu. Trade-off: một điểm khóa nóng/sector và counter cần bảo toàn. Với SEATED, capacity chỉ là giới hạn cấu hình; seat rows sở hữu khả dụng, không trừ đồng thời counter sector và từng ghế.';
COMMENT ON TABLE seat_inventory IS 'Mục đích: trạng thái bán hiện tại cho từng ghế, do Booking sở hữu; Event chỉ sở hữu cấu hình. Cập nhật có điều kiện theo ghế và reservation/generation ngăn nhận hai lần hoặc lệnh trả cũ trả ghế của lượt mới. Trade-off: số dòng lớn và bản sao định danh; không coi cache hoặc hình màu trên FE là bằng chứng ghế đã giữ.';
COMMENT ON TABLE order_item_seats IS 'Mục đích: tập ghế thuộc từng dòng đơn; một cột seat_id không đủ khi quantity lớn hơn một. Quan hệ riêng cho phép FK và chống lặp ghế trong cùng đơn. Đổi lại thêm bảng nối; còn phải kiểm số ghế, loại vé và sector phù hợp trong transaction.';
COMMENT ON TABLE reservation_allocations IS 'Mục đích: ghi lượt giữ đã chiếm nguồn cung nào, bao nhiêu, để chốt/trả đúng tài nguyên thay vì suy từ cấu hình đang đổi. Trade-off: dữ liệu có phần trùng dòng đơn, cần kiểm tổng và mode. FK chỉ bảo vệ liên kết cục bộ; đối chiếu resource với item, tổng allocations và trả đúng một lần cần transaction/service, chưa được chứng minh bằng DDL.';
COMMENT ON TABLE purchase_limits IS 'Mục đích: trạng thái theo từng buyer/event, khác events.purchase_limit là quy tắc chung. Ví dụ hạn mức 5, đã dùng 4: hai request cùng COUNT=4 rồi mỗi request thêm 1 có thể thành 6. Một dòng (event_id,buyer_subject) tạo điểm cập nhật có điều kiện/khóa cục bộ để tuần tự hóa, cùng transaction giữ chỗ và nguồn cung. Chỉ có bảng hoặc CHECK chưa tự ngăn mọi race. Trade-off: đọc/ghi gọn hơn tính tổng mỗi lần nhưng held/purchased là dữ liệu dư thừa; phải chốt/trả đúng một lần và có cách kiểm đối chiếu. Không nhằm chống một người dùng nhiều tài khoản. GOV-108, FR-19, INV-04.';
COMMENT ON TABLE promotion_inventory IS 'Mục đích: Booking giữ tổng lượt khả dụng của mã và bản sao điều kiện để áp mã cùng transaction giữ lượt. Trade-off: giảm gọi Event nhưng có counter dư thừa và bản sao cần version; không reset held/used khi replay cấu hình. configuration_snapshot cần form cố định, không chỉ kiểm object.';
COMMENT ON TABLE promotion_usages IS 'Mục đích: xác định buyer/reservation nào đã giữ/dùng/trả mã, thực thi giới hạn một lần/tài khoản và chống lệnh trả cũ. Chỉ counter tổng không biết ai đã dùng. Trade-off: thêm lịch sử RELEASED và chỉ mục; cần transaction với promotion_inventory. Không tạo yêu cầu audit mới cho mọi lần buyer bị từ chối.';
COMMENT ON COLUMN order_items.selection_snapshot IS 'Snapshot giữ phần hiển thị/lựa chọn tại lúc tạo đơn; đổi lại dư dữ liệu và cần định dạng thống nhất. ID và quantity/price có cột kiểm riêng. Không lấy giá hiện tại để dựng lại lịch sử, không dùng hash như cơ chế chống can thiệp DB.';
COMMENT ON COLUMN promotion_inventory.configuration_snapshot IS 'Bản sao điều kiện giảm giá giúp Booking quyết định cục bộ; đánh đổi là trễ/version. Một form cố định được validate trước ghi. Điều kiện thời gian, loại giảm và số tiền phải khớp cột có kiểu; received_at không phải chứng cứ cấu hình luôn mới nhất.';
COMMIT;

\connect payment_db
BEGIN;
SET LOCAL ROLE payment_owner;
SET LOCAL search_path = payment_schema, pg_catalog;

COMMENT ON TABLE order_payment_snapshots IS 'Bản mua đóng băng từ lần bắt đầu thanh toán đầu tiên, lưu tại Payment để nhận callback không JOIN Booking. Trade-off: dữ liệu dư và phải kiểm hash/version; giảm phụ thuộc đồng bộ, không là backup hoặc owner thứ hai của đơn.';
COMMENT ON TABLE payment_attempts IS 'Mục đích: từng lần thử khởi tạo thanh toán, chưa phải khoản thực thu. Tách lịch sử nhiều lần thử khỏi đơn và charge để thất bại/timeout không làm mất chứng cứ thu. Trade-off: thêm liên kết và state machine; unfinished attempt duy nhất/order chưa tự định nghĩa khi nào UNKNOWN được coi là kết thúc.';
COMMENT ON TABLE charges IS 'Khoản thực thu đã xác minh có liên kết, gồm khoản muộn/trùng; unique provider/merchant/charge phân biệt redelivery với hai khoản thu. PRJ-024 loại workflow mismatch/orphan. Trade-off: thêm đối chiếu; sổ nội bộ không chứng minh cổng không còn khoản bỏ sót.';
COMMENT ON TABLE payment_confirmations IS 'Mục đích: chọn duy nhất một khoản thu hợp lệ cho một đơn; tách sự thật đã thu khỏi quyền phát vé. FK kiểm amount/currency khớp, không tự chứng minh hạn hay điều kiện nghiệp vụ. Trade-off: thêm bảng 1:1; có thể gộp vào hồ sơ thanh toán với cột/constraint thích hợp nhưng chưa chốt thay thiết kế hiện tại. Hoàn charge thừa không đổi confirmation hợp lệ.';
COMMENT ON TABLE refunds IS 'Mục đích: tối đa một yêu cầu hoàn logic cho mỗi charge và hoàn toàn bộ số thực thu (FR-34/35). Nhiều retry không thành nhiều yêu cầu hoàn. Trade-off: thêm trạng thái bền vững và phân xử các nguyên nhân hội tụ. Không dùng kết quả hoàn để tự đổi quyền vào cửa; quy tắc retry/reason arbitration thuộc B13/B14.';
COMMENT ON TABLE refund_attempts IS 'Mục đích: lịch sử từng lần gọi cổng cho cùng refund, có request key để truy vấn/thử lại an toàn. Một cột last_error trên refunds làm mất lịch sử lần gọi trước. Trade-off: thêm dòng theo retry; timeout chưa chứng minh cổng chưa hoàn, không tạo request hoàn mới vô điều kiện.';
COMMENT ON TABLE event_refund_runs IS 'Mục đích: tiến trình chọn và xử lý các đơn cần hoàn của một lần hủy event; cursor/selection_complete giúp tiếp tục sau restart. Trade-off: thêm trạng thái điều phối một chiều; không phải Saga mới. Chưa chọn xong không được trình mẫu số như tổng cuối cùng.';
COMMENT ON TABLE event_refund_items IS 'Mục đích: theo dõi từng đơn trong run kể cả chưa tạo được refund, để báo số chờ/thành công/thất bại (FR-39). Counter trên run không chỉ ra đơn lỗi hoặc điểm tiếp tục. Trade-off: thêm dòng/order; chỉ liên kết charge được confirmation chọn, thu thừa theo nhánh riêng. Chọn item không bắt buộc đã có refund.';
COMMENT ON TABLE event_payouts IS 'Mục đích: giữ kết quả đối soát/chi trả với fee snapshot, nguồn bằng chứng và actor/time trong owner tiền. Trade-off: số tổng dẫn xuất phải kiểm freshness và công việc pending; CHECK không chứng minh không có giao dịch đang cạnh tranh. Chỉ đánh paid một lần theo FR-42/43, không tự chuyển tiền ngân hàng. Không đưa số payout vào events để tạo writer xuyên owner.';
COMMENT ON COLUMN order_payment_snapshots.purchase_snapshot IS 'Payload mua đã đóng băng phục vụ thanh toán/phát hành; tránh dựng lại từ giá hiện tại. Đổi lại dư dữ liệu và cần một form cố định cùng quy tắc thời điểm freeze. Kiểm object chỉ là hàng rào cú pháp; phải kiểm DTO trước ghi. Không xây cơ chế chống người âm thầm sửa JSON.';
COMMIT;

\connect ticket_db
BEGIN;
SET LOCAL ROLE ticket_owner;
SET LOCAL search_path = ticket_schema, pg_catalog;

COMMENT ON TABLE event_access_snapshots IS 'Mục đích: Ticket có owner, cửa sổ vào cửa và trạng thái event để check-in không JOIN DB Event. Trade-off: giảm phụ thuộc runtime nhưng thông báo hủy có thể trễ; received_at không chứng minh đã thấy mọi hủy. B13 phải chốt điểm hiệu lực; thiếu dữ kiện hợp lệ thì từ chối. Cửa sổ hiện hành eventStart <= now <= eventEnd, gồm hai đầu mút.';
COMMENT ON TABLE issuances IS 'Một bộ vé/order với input bất biến và terminal fence. Trade-off: thêm root để retry không tạo quyền mới; uniqueness không chứng minh đủ số vé, cần transaction kiểm counts. Timeout không đủ để FAILED và hoàn.';
COMMENT ON TABLE tickets IS 'Mục đích: từng quyền vào cửa, có seat/holder và trạng thái riêng, không gộp theo đơn vì một đơn có nhiều vé. Trade-off: thêm dòng/khóa và snapshot hiển thị. Ciphertext cho phép tải lại QR với khóa ngoài DB có thể restore; hash-only random token không đủ tải lại. Định dạng QR/thuật toán thuộc B13; không lưu QR thô vào audit. Không có trạng thái REFUNDED; kết quả hoàn không tự đổi quyền vào cửa.';
COMMENT ON TABLE ticket_deliveries IS 'Mục đích: từng lần gửi thông tin vé, tách giao nhận khỏi việc phát hành và hiệu lực vé. Một email lỗi không được làm mất vé hoặc khởi động refund (FR-32). Trade-off: thêm lịch sử/lần thử, nhưng tránh gửi lại bị hiểu thành phát thêm vé. Không lưu credential SMTP trong bảng.';
COMMENT ON TABLE checkin_attempts IS 'Mục đích: mọi yêu cầu check-in, gồm cả từ chối, có actor/time/result để điều tra (FR-65). Cột used_at trên ticket chỉ giữ được lần thành công, không thay lịch sử này. Trade-off: số dòng tăng, cần retention B16; không chứa QR/JWT thô. Unique success chưa thay kiểm owner/event/window/state và cập nhật nguyên tử; không hỗ trợ offline hoặc hoàn tác.';
COMMENT ON COLUMN issuances.purchase_snapshot IS 'Bản mua tại lúc phát hành giúp retry và tạo đủ vé không gọi lại giá/cấu hình hiện tại. Trade-off: phải giữ input nhất quán và kiểm tổng ticket con; JSON có form cố định, không chỉ kiểm object. Không nhận PII không cần cho chức năng đã duyệt.';
COMMENT ON COLUMN tickets.display_snapshot IS 'Giữ dữ liệu hiển thị của vé tại lúc phát hành để đổi tên/cấu hình không làm sai lịch sử. Trade-off: lặp dữ liệu và cần form cố định; không dùng snapshot hiển thị thay kiểm trạng thái event/vé hiện hành khi check-in.';
COMMIT;
