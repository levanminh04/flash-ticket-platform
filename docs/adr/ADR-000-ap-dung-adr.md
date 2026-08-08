# ADR-000 — Áp dụng Architecture Decision Record

- **Trạng thái:** Chấp nhận
- **Ngày:** 2026-08-09
- **Người quyết định:** Lê Văn Minh; các quyết định kiến trúc cụ thể cần được nhóm rà soát theo phân vai

## Bối cảnh

Đồ án có nhiều quyết định liên quan đến ranh giới service, quyền sở hữu dữ liệu, nhất quán phân tán, logging và nhánh trợ lý chẩn đoán. Các quyết định có thể thay đổi sau khi phân tích hoặc đo thử. Nhóm cần lưu được lý do, phương án đã loại và bằng chứng dẫn đến thay đổi để phục vụ triển khai, báo cáo và bảo vệ cá nhân.

## Các phương án đã cân nhắc

| Phương án | Ưu điểm | Nhược điểm |
|---|---|---|
| Dựa vào trí nhớ và lịch sử commit | Không tốn công viết tài liệu riêng | Không tái dựng đầy đủ bối cảnh, phương án đã loại và trách nhiệm quyết định |
| Ghi mọi quyết định trong một tài liệu chung | Ít tệp, dễ bắt đầu | Khó giữ lịch sử; dễ sửa mất lý do cũ; tài liệu dài và khó rà soát |
| ADR đánh số và giữ chuỗi thay thế | Mỗi quyết định có lịch sử và phạm vi rõ | Tốn công nếu lạm dụng cho quyết định nhỏ |

## Quyết định

Dùng ADR đánh số tuần tự trong `docs/adr/` cho các quyết định có phương án cạnh tranh hoặc hệ quả kiến trúc đáng kể. Không lập ADR cho mọi thư viện, tham số hoặc chi tiết nội bộ dễ đảo ngược.

## Phục vụ ASR hoặc yêu cầu nào

Phục vụ yêu cầu minh bạch lập luận, truy được trách nhiệm thiết kế và duy trì sự nhất quán giữa báo cáo, bản vẽ, mã nguồn và kết quả đánh giá.

## Hệ quả tích cực

- Có nguyên liệu đáng tin cậy cho phần lập luận thiết kế trong báo cáo.
- Nhận ra sớm khi một quyết định thay đổi nhưng tài liệu hoặc mã nguồn chưa cập nhật.
- Hỗ trợ giải thích đóng góp cá nhân khi bảo vệ.

## Hệ quả tiêu cực

- Phát sinh chi phí viết và rà soát.
- Có thể trở thành thủ tục hình thức nếu tạo ADR cho quyết định nhỏ hoặc chỉ ghi kết luận mà không có phương án thực sự.

## Kiểm chứng bằng cách nào

Rà soát tại cổng kiến trúc và trước khi viết phần thiết kế: các quyết định lớn phải có ADR, có ít nhất hai phương án thực tế, hệ quả hai chiều và liên kết tới yêu cầu/cách kiểm chứng khi áp dụng được.

## Liên kết

- `../quy-trinh-lam-viec.md`
- `../tang-c-quy-uoc-trinh-bay.md`, mẫu C1
