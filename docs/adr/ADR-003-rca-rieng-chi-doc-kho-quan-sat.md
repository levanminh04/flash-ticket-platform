# ADR-003 — RCA riêng, chỉ đọc kho quan sát

- Trạng thái: `Chấp nhận`
- Ngày: 2026-09-04
- Người quyết định: Lê Văn Minh (`GOV-089`, `GOV-093`)
- Gate: `B11-C`; chuẩn telemetry tại `B16`
- Liên kết phương án: áp cho kiến trúc `PA-6`
- Tác động lên A1–A6: **Không — giữ nguyên vai RCA và FlashTicket đã nêu.**

## Bối cảnh

FlashTicket phải tích hợp và chạy cơ chế RCA; cơ chế chỉ đọc và không tự sửa nghiệp vụ. Cần chọn giữa tiến trình riêng và đồng vị trí mà không biến RCA thành service nghiệp vụ hoặc nguồn quyết định ranh giới.

## Các phương án đã cân nhắc

- đồng vị trí RCA với một service nghiệp vụ: tiết kiệm tiến trình nhưng ghép tải và quyền truy cập;
- một đơn vị RCA riêng đọc kho quan sát;
- một hệ RCA riêng có quyền đọc trực tiếp từng database nghiệp vụ: vi phạm ranh giới chỉ đọc kho quan sát và tăng bề mặt quyền.

## Quyết định

Chạy cơ chế RCA và lớp giải thích trong một đơn vị triển khai riêng. Đơn vị dùng credential chỉ đọc riêng trên kho dữ liệu quan sát; không có quyền ghi dữ liệu nghiệp vụ, không truy cập trực tiếp schema nghiệp vụ và không gọi API ghi.

Hình thức đưa kết quả tới người có quyền vẫn là `RES-039`, chưa được ADR này chốt.

Lớp giải thích gọi Gemini qua API sau bước xếp hạng (`GOV-093`); không chạy mô hình ngôn ngữ lớn cục bộ. Chỉ dữ liệu đã lọc/che trường nhạy cảm và bằng chứng cần thiết mới được gửi ra ngoài. Gemini không được đổi thứ hạng RCA; lỗi, timeout hoặc quota của API không được làm mất bảng xếp hạng và provenance mà cơ chế đã tạo.

## Hệ quả

Tích cực: cách ly tài nguyên/quyền; có thể dừng hoặc nâng cấp RCA mà không chạm đường giao dịch; ranh giới bảo mật kiểm được.

Tiêu cực: thêm một tiến trình phải cấp tài nguyên; phụ thuộc chất lượng collector/kho quan sát; phải quản lý credential riêng và độ trễ dữ liệu; lớp giải thích phụ thuộc kết nối, quota và chính sách dữ liệu của Gemini API.

## Cách kiểm chứng sau chấp nhận

- kiểm cấu hình quyền chứng minh chỉ có thao tác đọc kho quan sát;
- thử âm tính: RCA không kết nối được database/API ghi nghiệp vụ;
- kiểm API key không nằm trong repository và payload Gemini không chứa trường nhạy cảm chưa che;
- kiểm khi Gemini lỗi/timeout, bảng xếp hạng RCA vẫn được trả ra;
- `B16` chứng minh tín hiệu quy được về thành phần sinh ra nó;
- chạy lại phép thử độc lập của cửa nối RCA.
