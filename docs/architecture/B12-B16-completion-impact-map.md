# Phạm vi hoàn thiện bộ thiết kế để bắt đầu code

- Ngày lập: 2026-09-13; phạm vi đã duyệt tại GOV-143; nội dung hoàn thiện và commit/push được duyệt ngày 2026-09-14 tại GOV-144/GOV-145. Danh sách dưới giữ dấu vết phạm vi lượt soạn.
- Loại: PLAN; bản đồ tác động, không quyết định nghiệp vụ hoặc tự duyệt gate.
- Căn cứ: yêu cầu “đồng ý cả 2 mục, giờ hoàn thiện bộ tài liệu”; decision-register GOV-101–142, PRJ-010–024 theo phần còn hiệu lực; B11-C đã duyệt. Không mở lại Q-01–07.
- Mục tiêu: bộ B12/B13/B14 nhất quán, init sạch và ERD khớp, kế hoạch kiểm chứng và quan sát tối thiểu để nhóm code luồng chính; không thêm nghiệp vụ phụ.

## 1. Phạm vi xin duyệt — danh sách tệp chính xác

Các đường dẫn dưới tương đối với D:/Project/flash-ticket-platform. Chỉ sửa phần liên quan quyết định đã chốt; danh sách cho phép cập nhật khi có tác động, không bắt buộc sửa tệp không cần thay. Nếu phát hiện phải đổi tệp ngoài danh sách, trình bổ sung thay vì lan truyền ngầm.

### Nguồn quyết định và yêu cầu — cập nhật trước

- docs/project/decision-register.md
- docs/domain/B6-use-cases.md
- docs/domain/B7-aggregates-and-invariants.md
- docs/domain/B8-requirements.md
- docs/architecture/B12-B14-review-pack.md
- docs/architecture/B12-data-ownership-and-schema.md
- docs/architecture/B12-validation.md

Chỉ đồng bộ các xác nhận (hủy/follow, tiền, email, trạng thái, ghế ẩn, giới hạn Q-07); không đổi service/ownership/đề tài. Tạo tác nguồn bị thay đổi nội dung được trình REVIEW_READY để Minh rà, không AI tự APPROVED.

### Hợp đồng, sequence và chuẩn triển khai — tạo mới

- docs/architecture/B13-api-and-event-contracts.md
- docs/architecture/B14-main-flow-sequences.md
- docs/architecture/B15-verification-plan.md
- docs/architecture/B16-observability-baseline.md
- docs/architecture/implementation-readiness.md

B13 có payload/DTO/JSON cố định, validation, owner, mã lỗi, idempotency và mapping FE cũ; B14 có thứ tự transaction/message, retry/timeout và kết quả cuối. B15 phân biệt test đã chạy với kế hoạch. B16 chốt đề xuất kỹ thuật quan sát theo stack đã chọn; không thiết kế lại phương pháp RCA. Readiness có bộ phiên bản tương thích, danh sách project Spring/dependency theo năm service và hạ tầng cần dựng, thứ tự code lát dọc, giới hạn triển khai thật.

### Schema, quyền và chú thích — cập nhật

- docs/architecture/data/README.md
- docs/architecture/data/test-static.cjs
- docs/architecture/data/postgres/00-bootstrap.sql
- docs/architecture/data/postgres/10-event.sql
- docs/architecture/data/postgres/20-booking.sql
- docs/architecture/data/postgres/30-payment.sql
- docs/architecture/data/postgres/40-ticket.sql
- docs/architecture/data/postgres/90-grants.sql
- docs/architecture/data/postgres/95-design-rationale.sql
- docs/architecture/data/postgres/README.md
- docs/architecture/data/postgres/Run-LocalTests.ps1
- docs/architecture/data/postgres/Run-PortableTests.ps1
- docs/architecture/data/postgres/tests/assertions.sql
- docs/architecture/data/postgres/tests/booking-fixture.sql
- docs/architecture/data/postgres/tests/catalog.sql
- docs/architecture/data/postgres/tests/constraints.sql
- docs/architecture/data/postgres/tests/event-fixture.sql
- docs/architecture/data/postgres/tests/payment-fixture.sql
- docs/architecture/data/postgres/tests/runtime.sql
- docs/architecture/data/postgres/tests/ticket-fixture.sql
- docs/architecture/data/mongodb/schema.cjs
- docs/architecture/data/mongodb/bootstrap.js
- docs/architecture/data/mongodb/README.md
- docs/architecture/data/mongodb/test-static.cjs
- docs/architecture/data/mongodb/test-preflight.cjs
- docs/architecture/data/mongodb/test-live.js

Init tạo database/schema/bảng mới, không migration dữ liệu cũ; đủ cột/type/NULL/default/constraint/index và comment tradeoff. Bổ sung persistence Saga/outbox/inbox theo hợp đồng trong owner đã chốt, không giữ số 36 bằng mọi giá. Không bảng xử lý Q-07. Giữ organizer_profiles riêng. Quyền xóa DRAFT phải hẹp, không cấp DELETE toàn database. Runner tích hợp script comment và các kiểm mới.

### Sơ đồ và dẫn đường — đồng bộ sau nguồn

- docs/diagrams/src/B12-01-event-erd.puml
- docs/diagrams/src/B12-02-booking-erd.puml
- docs/diagrams/src/B12-03-payment-erd.puml
- docs/diagrams/src/B12-04-ticket-erd.puml
- docs/diagrams/src/B12-05-user-document-map.puml
- docs/diagrams/src/B14-01-order-payment-ticket-sequence.puml (mới)
- docs/diagrams/src/B14-02-expiry-refund-cancellation-sequence.puml (mới)
- docs/diagrams/src/B14-03-ticket-delivery-checkin-sequence.puml (mới)
- docs/diagrams/src/README.md
- docs/README.md
- docs/project/implementation-status.md

## 2. Ngoài phạm vi

Không sửa repo cũ/application code; commit/push nay được cho phép riêng tại GOV-145; không deploy AWS, sửa DNS/security group, nhập realm hoặc ghi datastore đang dùng. Không sửa B11/ADR đã duyệt, phương pháp RCA hoặc AGENTS.md. Realm fixture chỉ đọc trong lượt này; hợp đồng danh tính và công việc cấu hình được ghi tại B13/readiness, không tuyên bố fixture đã sẵn sàng vận hành. Không viết toàn bộ báo cáo tốt nghiệp hoặc xây công cụ tải/dashboard; B15/B16 là kế hoạch/chuẩn tối thiểu.

## 3. Kiểm chứng và tiêu chí bàn giao

1. Không còn câu hỏi Q-01–07 lặp lại như chưa duyệt; ghi đúng Q-07 ngoài phạm vi.
2. B12 có mô tả từng bảng/cột và lý do; SQL/ERD/Mongo khớp; không có payload luồng chính chỉ mô tả là object tùy ý.
3. B13/B14 đủ cho nhóm chia việc: giữ chỗ, tiền đóng băng, thanh toán, phát vé, hết hạn, hoàn, hủy event, delivery và check-in; không transaction xuyên DB.
4. Chạy kiểm tĩnh, kiểm catalog/constraint/quyền trên datastore local thử nghiệm tách biệt bằng runtime sẵn có. Không tự mở Docker. Nếu môi trường không cho chạy một kiểm, khai đúng kiểm chưa chạy và trở ngại cụ thể, không ghi PASS thay.
5. Có test vector tiền, mapping FE/seat-map, state transitions và ca callback lặp; phân biệt test hợp đồng/hàm với E2E khi service chưa tồn tại.
6. Chạy audit governance, kiểm toàn bộ diff trong phạm vi, giữ nguyên thay đổi ngoài lượt.
7. Bàn giao tài liệu REVIEW_READY khi đạt đầu vào/kiểm tương ứng; Minh là người duyệt gate. Kèm danh sách việc code đầu tiên, không chỉ giao thêm một kế hoạch chung.

## 4. Cách thực hiện sau khi duyệt

Thực hiện liên tục theo thứ tự nguồn → contract/sequence → schema/kiểm → ERD/readiness/chỉ mục. Không xin xác nhận lại từng chi tiết kỹ thuật đã nằm trong phạm vi; chỉ dừng nếu xuất hiện mâu thuẫn thực chất hoặc cần quyền/phạm vi mới. Ưu tiên luồng chính và khả năng quan sát; không dành thời gian cho nghiệp vụ phụ đã bị loại.
