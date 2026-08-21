# Trạng thái triển khai plan

- **Cập nhật:** 2026-08-21 (vòng bỏ `SUPER_ADMIN`)
- **Giai đoạn hiện tại:** Giai đoạn 2 — B2 `APPROVED (v0.9)`, B3 `APPROVED (v0.9)`, B4 `APPROVED (v0.9)` và B5 `APPROVED (v0.7)` theo đúng chuỗi B2 → B3 → B4 → B5; bước tiếp theo có thể mở B7 về aggregate ứng viên và bất biến

## Giai đoạn 0

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| Repository ĐATN riêng | Hoàn thành | `flash-ticket-platform` là không gian hiện thực và đánh giá chính thức |
| Không đưa `.env`/bí mật vào repo mới | Hoàn thành bước đầu | `.gitignore`, `.env.example`; tiếp tục quét trước mỗi lần nhập mã |
| Bộ plan A/B/C, tài liệu chủ và B5.5 | Đã hòa giải phương pháp | Giai đoạn 2 chỉ tạo mô hình miền ứng viên; B11-A/B/C mới hình thành, kiểm tra khả thi và chốt kiến trúc |
| Nơi ghi quyết định và mẫu ADR | Hoàn thành | `docs/adr/`, ADR-000 |
| Quy ước ký hiệu/đặt tên | Hoàn thành | Tầng C mục 3.1–3.3 |
| Bảng phân vai | Hoàn thành baseline | Ba thành viên đã xác nhận phân công ban đầu; phạm vi backend cụ thể Tuyến hỗ trợ sẽ chốt khi chia workload |
| Chiến lược tái sử dụng frontend | `USER_CONFIRMED` | Dùng frontend hiện tại làm nền nhưng điều chỉnh theo mô hình đích; không cho frontend ràng buộc phương án tách service ở B11-A; kiểm tra khả năng tái sử dụng tại B11-B/Giai đoạn 5 (`PRJ-004`, `PRJ-005`) |
| Khung báo cáo không khóa số chương | Hoàn thành bước đầu | `docs/report/report-outline.md`; vòng đời ba bản Word được phân loại tại `docs/report/README.md` |
| Xin đề cương và file mẫu ĐATN hiện hành | Chưa gửi | Nội dung soạn sẵn tại `docs/coordination/questions-for-advisor.md` |

## Điều kiện chuyển sang Giai đoạn 1

Có thể bắt đầu các công việc khảo sát/phân tích không phụ thuộc mẫu trình bày ngay. Cổng Giai đoạn 0 chỉ hoàn tất đầy đủ sau khi:

1. Nhóm gửi câu hỏi về đề cương/file mẫu cho cô Liên.
2. **Đạt:** Ba thành viên đã xác nhận bảng phân vai ban đầu.

## Giai đoạn 1

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B1 — Khảo sát luồng công khai | `APPROVED` baseline | `evidence/external-survey/B1-public-ticketing-survey-2026-08-09.md`; đủ dùng cho B2, ảnh công khai là bổ sung không chặn |
| B1 — Quy trình chẩn đoán hiện tại | `APPROVED` baseline, bằng chứng còn giới hạn | Quy trình và INC-01 đã được xác nhận; cần thêm tối thiểu 2 ca/tái hiện hoặc công bố giới hạn một ca và không đo thời gian cải thiện |
| A1 — Bối cảnh và tính cấp thiết | `APPROVED` baseline | Nhấn mạnh hậu quả nghiệp vụ trước, khó khăn phân tán sau; tên “trợ lý chẩn đoán sự cố” đã chốt; chuẩn trích dẫn chờ mẫu khoa |
| A2 — Phát biểu vấn đề | `APPROVED` baseline | Chủ đồ án đã duyệt câu chữ; tái kiểm tra sau B10 và khi B11-C có tác động |
| A4 — Câu hỏi nghiên cứu | `APPROVED` phạm vi baseline | Giữ một câu trung tâm và hai câu phụ; ngưỡng chờ B9–B10, cách trình bày chờ mẫu/giảng viên |

## Điều kiện hoàn tất Giai đoạn 1

1. **Đạt:** Chủ đồ án đã duyệt A1, A2 và phạm vi A4 ở mức baseline; sau B10 phải tái kiểm tra trước khi chốt A1–A6.
2. **Đạt:** Khảo sát B1 công khai được xác nhận đủ dùng; ảnh có thể bổ sung sau.
3. **Chưa đạt:** Baseline chẩn đoán cần thêm ít nhất 2 ca thật/tái hiện được; nếu không có, chủ đồ án phải xác nhận giới hạn một ca và không đo thời gian cải thiện.
4. **Đạt:** Phát biểu vấn đề không phụ thuộc tên công nghệ.

## Quyết định đang mở, chưa được tự khóa

- Danh sách service cụ thể và vị trí aggregate tồn kho vé.
- Tối đa ba luồng nào thực sự cần Saga.
- Cách bố trí các thành phần trên hai EC2.
- Workflow cuối của trợ lý chẩn đoán và tập ca đánh giá.
- Cỡ tập ca chẩn đoán: bổ sung tối thiểu 2 ca hay chấp nhận giới hạn một ca và bỏ kết luận về thời gian.
- Phần backend cụ thể Tuyến sẽ hỗ trợ sau khi nhóm chia workload.
- Tập trường tối thiểu của hồ sơ organizer, cách đồng bộ/kho lưu danh tính nghiệp vụ và nghĩa kỹ thuật của nơi tạo admin đầu tiên; vòng đời nghiệp vụ organizer đã được chốt.
- Mô hình trạng thái đơn (`BIZ-130`), quan hệ sức chứa địa điểm–nguồn cung và cách tính số người theo dõi.
- Quyền sở hữu dữ liệu vật lý của tỷ lệ phí nền tảng (`BIZ-123`); nghĩa nghiệp vụ cố định sau phê duyệt đã được chốt.

## Giai đoạn 2

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B2 — Từ điển miền | `APPROVED` (`B2-v0.9`) | Lê Văn Minh duyệt ngày 2026-08-21; thuật ngữ tài khoản/organizer và trạng thái sự kiện đã được làm rõ, toàn bộ định nghĩa `CANDIDATE` của vòng rà đã được xác nhận |
| B3 — Quy trình nghiệp vụ | `APPROVED` (`B3-v0.9`) | Lê Văn Minh duyệt sau B2-v0.9 ngày 2026-08-21; giữ bốn quy trình cốt lõi, ghi rõ trạng thái sự kiện và tỷ lệ phí cố định sau duyệt |
| B4 — Bản đồ sự kiện miền | `APPROVED` (`B4-v0.9`) | Lê Văn Minh duyệt sau B3-v0.9 ngày 2026-08-21; bổ sung truy vết chức năng giao diện, đóng phần nghiệp vụ của vòng đời organizer và thu hẹp `OPEN` phí về sở hữu dữ liệu |
| B5 — Bản đồ bounded context | `APPROVED` (`B5-v0.7`) | Lê Văn Minh duyệt sau B4-v0.9 ngày 2026-08-21; giữ nguyên tám context/phân loại, đóng `B5-OPEN-01` ở phần nghiệp vụ, giữ trường hồ sơ và các quyết định kỹ thuật ở đúng gate sau |
| B7 — Aggregate và bất biến | Có thể bắt đầu (bản nháp) | Dùng B5-v0.7 và B2-v0.9, cả hai đã `APPROVED` ngày 2026-08-21; không gán aggregate cho service/schema ở Giai đoạn 2 |

## Cổng và giới hạn Giai đoạn 2

- Chuỗi phụ thuộc là `B2 baseline → B3 → B4 → B5 → B7`; cho phép tạo nháp phía sau nhưng không được `APPROVED` khi đầu vào bắt buộc chưa được duyệt.
- AI không tự duyệt tài liệu do chính nó tạo; mỗi tạo tác ghi trạng thái, người duyệt, ngày và phiên bản đầu vào.
- Giai đoạn 2 không tạo service vật lý, Saga, schema đích hoặc ADR kiến trúc và không đọc B5.5 để hình thành phương án.
- Hai trần `≤ 8 service nghiệp vụ` và `≤ 3 Saga` là ràng buộc đánh giá tại B10/B11, không phải số lượng phải dùng hết.
- Ngày 2026-08-21, sau khi B2–B5 được duyệt, Lê Văn Minh chốt bỏ vai trò `SUPER_ADMIN` khỏi phạm vi đồ án (`BIZ-146`, thay thế `BIZ-133`). Vai trò này không có nhiệm vụ nào được mô tả trong B2–B5 và không truy được về sự kiện miền nào ở B4. Vì vậy B2 lên `B2-v0.9` và B4 lên `B4-v0.9`, cả hai trở lại `REVIEW_READY` và cần Lê Văn Minh duyệt lại theo thứ tự B2 → B4. B3-v0.9 và B5-v0.7 không đổi nội dung nghiệp vụ — B3 không nhắc bộ vai trò và B5 chỉ dùng cụm “bộ role” chung — nhưng auditor cưỡng chế quy tắc “đầu vào chưa `APPROVED` thì hạ nguồn không được `APPROVED`”, nên cả bốn tài liệu B2 → B5 cùng trở lại `REVIEW_READY`. Lê Văn Minh đã duyệt lại toàn chuỗi B2-v0.9 → B3-v0.9 → B4-v0.9 → B5-v0.7 trong cùng ngày 2026-08-21.
- B2-v0.5, B3-v0.5 và B4-v0.5 từng được Lê Văn Minh duyệt tuần tự ngày 2026-08-18. Vòng hiệu đính ngày 2026-08-20 phát hiện các dòng PRJ/BIZ mới đã bị gắn `USER_CONFIRMED` quá mức, B4/B5 không nhất quán và checklist B5 có dấu tick không có bằng chứng. Sau vòng kiểm toán và duyệt nội dung ngày 2026-08-21, chuỗi đã đạt B2-v0.8, B3-v0.8, B4-v0.8 và B5-v0.6 `APPROVED`; vòng bỏ `SUPER_ADMIN` sau đó nâng phiên bản hiện hành lên B2-v0.9, B3-v0.9, B4-v0.9 và B5-v0.7 rồi được duyệt lại đúng thứ tự. Tám context không được thêm, bớt, đổi tên hay đổi phân loại; các `OPEN` còn lại có owner/gate và không chặn B7. Giai đoạn 0–1 vẫn còn việc xin mẫu báo cáo và xử lý phạm vi tập ca chẩn đoán nhưng không chặn phân tích Giai đoạn 2.
