# Trạng thái triển khai plan

- **Cập nhật:** 2026-08-09
- **Giai đoạn hiện tại:** Giai đoạn 1 — Vấn đề và bối cảnh

## Giai đoạn 0

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| Repository ĐATN riêng | Hoàn thành | `flash-ticket-platform`; repository tiền nhiệm được giữ nguyên |
| Không đưa `.env`/bí mật vào repo mới | Hoàn thành bước đầu | `.gitignore`, `.env.example`; tiếp tục quét trước mỗi lần nhập mã |
| Bộ plan A/B/C, tài liệu chủ và B5.5 | Hoàn thành | `docs/` |
| Nơi ghi quyết định và mẫu ADR | Hoàn thành | `docs/adr/`, ADR-000 |
| Quy ước ký hiệu/đặt tên | Hoàn thành | Tầng C mục 3.1–3.3 |
| Bảng phân vai | Có khung, chưa hoàn tất | Cả nhóm cần gắn tên vào từng luồng trong `roles.md` |
| Khung báo cáo không khóa số chương | Hoàn thành bước đầu | `docs/report/report-outline.md` |
| Xin đề cương và file mẫu ĐATN hiện hành | Chưa gửi | Nội dung soạn sẵn tại `docs/coordination/questions-for-advisor.md` |

## Điều kiện chuyển sang Giai đoạn 1

Có thể bắt đầu các công việc khảo sát/phân tích không phụ thuộc mẫu trình bày ngay. Cổng Giai đoạn 0 chỉ hoàn tất đầy đủ sau khi:

1. Nhóm gửi câu hỏi về đề cương/file mẫu cho cô Liên.
2. Ba thành viên xác nhận bảng phân vai ban đầu.

## Giai đoạn 1

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B1 — Khảo sát luồng công khai | Nháp hoàn thành | `evidence/external-survey/B1-public-ticketing-survey-2026-08-09.md`; có nguồn chính thức, chưa có ảnh chụp thủ công |
| B1 — Quy trình chẩn đoán hiện tại | Nháp một phần | `evidence/incident-diagnosis/B1-current-diagnosis-baseline.md`; có 1 ca lỗi thật, cần thêm 2–4 ca/thời gian |
| A1 — Bối cảnh và tính cấp thiết | Nháp hoàn thành | `research/A1-context-and-urgency.md`; cần duyệt cách nhấn mạnh và chuẩn hóa trích dẫn |
| A2 — Phát biểu vấn đề | Nháp hoàn thành | `research/A2-problem-statement.md`; cần chủ đồ án xác nhận câu chữ |
| A4 — Câu hỏi nghiên cứu | Nháp hoàn thành | `research/A4-research-questions-draft.md`; chưa chốt cho tới khi rõ khả năng thu bằng chứng |

## Điều kiện hoàn tất Giai đoạn 1

1. Chủ đồ án duyệt A1, A2 và phạm vi A4.
2. Nhóm xác nhận khảo sát B1 đủ dùng hoặc bổ sung ảnh công khai.
3. Baseline chẩn đoán có thêm ít nhất 2 ca thật/tái hiện được; nếu không có, ghi rõ chỉ có một ca và không đo thời gian cải thiện.
4. Phát biểu vấn đề cuối không phụ thuộc tên công nghệ.

## Quyết định đang mở, chưa được tự khóa

- Danh sách service cụ thể và vị trí aggregate tồn kho vé.
- Tối đa ba luồng nào thực sự cần Saga.
- Chính sách hoàn tiền và khoản giữ lại phòng hoàn tiền.
- Cách bố trí các thành phần trên hai EC2.
- Workflow cuối của trợ lý chẩn đoán và tập ca đánh giá.
