# Trạng thái triển khai plan

- **Cập nhật:** 2026-08-18
- **Giai đoạn hiện tại:** Giai đoạn 2 — B2 `APPROVED (v0.4)` và B3 `APPROVED (v0.2)`; bước tiếp theo là B4 — bản đồ sự kiện miền, trong khi các đầu việc bằng chứng của Giai đoạn 0–1 tiếp tục song song

## Giai đoạn 0

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| Repository ĐATN riêng | Hoàn thành | `flash-ticket-platform` là không gian hiện thực và đánh giá chính thức |
| Không đưa `.env`/bí mật vào repo mới | Hoàn thành bước đầu | `.gitignore`, `.env.example`; tiếp tục quét trước mỗi lần nhập mã |
| Bộ plan A/B/C, tài liệu chủ và B5.5 | Đã hòa giải phương pháp | Giai đoạn 2 chỉ tạo mô hình miền ứng viên; B11-A/B/C mới hình thành, kiểm tra khả thi và chốt kiến trúc |
| Nơi ghi quyết định và mẫu ADR | Hoàn thành | `docs/adr/`, ADR-000 |
| Quy ước ký hiệu/đặt tên | Hoàn thành | Tầng C mục 3.1–3.3 |
| Bảng phân vai | Hoàn thành baseline | Ba thành viên đã xác nhận phân công ban đầu; phạm vi backend cụ thể Tuyến hỗ trợ sẽ chốt khi chia workload |
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

## Giai đoạn 2

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B2 — Từ điển miền | `APPROVED` (`B2-v0.4`) | Lê Văn Minh duyệt ngày 2026-08-18; đây là baseline thuật ngữ cho B3/B4 |
| B3 — Quy trình nghiệp vụ | `APPROVED` (`B3-v0.2`) | Lê Văn Minh duyệt ngày 2026-08-18; gồm tài liệu canonical và bốn tệp `docs/diagrams/src/B3-*.puml` |
| B4 — Bản đồ sự kiện miền | Chưa bắt đầu | Đầu vào B2/B3 đã `APPROVED`; đây là bước tiếp theo |
| B5 → B7 | Chưa bắt đầu | Chỉ thực hiện sau các gate phía trước; giữ ranh giới ở mức khái niệm ứng viên |

## Cổng và giới hạn Giai đoạn 2

- Chuỗi phụ thuộc là `B2 baseline → B3 → B4 → B5 → B7`; cho phép tạo nháp phía sau nhưng không được `APPROVED` khi đầu vào bắt buộc chưa được duyệt.
- AI không tự duyệt tài liệu do chính nó tạo; mỗi tạo tác ghi trạng thái, người duyệt, ngày và phiên bản đầu vào.
- Giai đoạn 2 không tạo service vật lý, Saga, schema đích hoặc ADR kiến trúc và không đọc B5.5 để hình thành phương án.
- Hai trần `≤ 8 service nghiệp vụ` và `≤ 3 Saga` là ràng buộc đánh giá tại B10/B11, không phải số lượng phải dùng hết.
- B2 `APPROVED (v0.4)` và B3 `APPROVED (v0.2)` do Lê Văn Minh phê duyệt ngày 2026-08-18; B4 được phép bắt đầu ở trạng thái `DRAFT`. Giai đoạn 0–1 vẫn còn việc xin mẫu báo cáo và xử lý phạm vi tập ca chẩn đoán, nhưng không chặn B4.
