# Hợp đồng tích hợp

Lưu đặc tả API và lược đồ sự kiện cốt lõi sau khi ranh giới service và quyền sở hữu dữ liệu được chốt.

- API dùng version ở đường dẫn, ví dụ `/api/v1/...`.
- Sự kiện dùng dạng `<DanhTu><DongTuQuaKhu>`, ví dụ `TicketIssued`.
- Lược đồ sự kiện phải có trường `version` và quy tắc tương thích.
- Không tạo hợp đồng giả định chỉ để lấp đầy thư mục.
