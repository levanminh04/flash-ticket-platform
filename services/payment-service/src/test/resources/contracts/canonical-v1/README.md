# Canonical v1 golden fixtures

Candidate cho review với Booking; chưa phải contract integration đã duyệt.

Expected bytes được tạo độc lập bằng Python 3 stdlib json/hashlib, không gọi Java serializer/hash service. Reference: normalize UUID keys bằng lowercase; timestamp .000Z thành Z; sort items theo orderItemId, seats theo seatId; json.dumps(sort_keys=True, ensure_ascii=False, separators=(",", ":")).encode("utf-8"); hashlib.sha256(bytes).hexdigest(). Các mảng khác giữ nguyên thứ tự.

Canonical files không BOM/newline; null giữ tường minh. Fixtures bao gồm tiền/version dạng chuỗi, UUID viết hoa, timestamp .000Z, Unicode, quotes, slash, backslash, newline và control U+0001. Không dùng generator Java đang kiểm để cập nhật expected. Khi rule thay đổi phải review contract và reference trước.

Control-character hex escapes dùng lowercase (ví dụ U+001B → `\u001b`); golden fixture có cả U+0001 và U+001B để kiểm quy ước này. Unicode scalar không hợp lệ (surrogate lẻ) bị từ chối khi encode canonical UTF-8, không thay bằng `?`; valid surrogate pair được giữ.
