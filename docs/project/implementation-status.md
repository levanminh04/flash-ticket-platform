# Trạng thái triển khai plan

- **Cập nhật:** 2026-08-09
- **Giai đoạn hiện tại:** Giai đoạn 0 — Nền tảng làm việc

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

## Quyết định đang mở, chưa được tự khóa

- Danh sách service cụ thể và vị trí aggregate tồn kho vé.
- Tối đa ba luồng nào thực sự cần Saga.
- Chính sách hoàn tiền và khoản giữ lại phòng hoàn tiền.
- Cách bố trí các thành phần trên hai EC2.
- Workflow cuối của trợ lý chẩn đoán và tập ca đánh giá.
