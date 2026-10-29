# Kết quả test Booking qua RabbitMQ dashboard

Ngày chạy: **28/10/2026**. Publish bằng **Playwright MCP**, qua form Publish message của exchange `business.ex`, routing key `event.sales-configured.v1`, content type `application/json`.

Booking chạy Java main class trên máy local, JDK 23, VM option `-Duser.timezone=UTC`; DB đi qua tunnel `127.0.0.1:5432`, broker ở server 119. Đối chiếu sau từng case bằng query DB chỉ đọc, số ACK/delivery, số message DLQ và errorCode trong log.

## Kết quả: 17/17 case PASS

| # | Case | Kết quả đã kiểm |
| --- | --- | --- |
| 1 | QUANTITY version mới | ACK; capacity thành 150/30, promotion limit 15; counter vẫn 0 |
| 2 | Version cũ với messageId mới | ACK; thêm inbox, không đổi snapshot/inventory |
| 3 | Cùng version và cùng data, messageId mới | ACK; thêm inbox, không đổi snapshot/inventory |
| 4 | Cùng version nhưng khác data | DLQ, SALES_CONFIGURATION_CONFLICT; DB giữ nguyên |
| 5 | Cùng messageId nhưng khác data | DLQ, MESSAGE_ID_CONFLICT; receipt/hash cũ giữ nguyên |
| 6 | Envelope schemaVersion = 2 | DLQ, INVALID_MESSAGE; DB giữ nguyên |
| 7 | aggregateId khác data.eventId | DLQ, INVALID_MESSAGE; DB giữ nguyên |
| 8 | List seats = null | DLQ, INVALID_MESSAGE; DB giữ nguyên |
| 9 | SEAT_MAP gồm SEATED và STANDING | ACK; 2 sector, 3 seat: 2 AVAILABLE và 1 hidden UNAVAILABLE; không tạo pool ticket type; lưu promotion FIXED |
| 10 | Bỏ ghế trống, bỏ ẩn ghế khác, bỏ promotion chưa có lượt giữ | ACK; ghế bị bỏ giữ row và thành UNAVAILABLE; ghế bỏ ẩn thành AVAILABLE; capacity STANDING thành 60; promotion cũ giữ row/version 1 |
| 11 | Ghế tham chiếu sector không tồn tại | DLQ, INVALID_MESSAGE; DB giữ nguyên |
| 12 | Đổi sector đã có từ SEATED thành STANDING | DLQ, SALES_CONFIGURATION_CONFLICT; DB giữ nguyên |
| 13 | Event mới dùng ticketTypeId của event khác | DLQ, SALES_CONFIGURATION_CONFLICT; rollback cả snapshot event mới, không sửa inventory cũ |
| 14 | Đổi organizer của event đã có | DLQ, SALES_CONFIGURATION_CONFLICT; DB giữ nguyên |
| 15 | Hủy event bằng version mới | ACK; snapshot thành CANCELLED, source version 3 |
| 16 | Mở lại event CANCELLED | DLQ, SALES_CONFIGURATION_CONFLICT; DB giữ nguyên |
| 17 | JSON thiếu nội dung, không parse được | DLQ, INVALID_MESSAGE; DB giữ nguyên |

## Tổng kết broker và dữ liệu

- Lượt này publish **17 message: 6 ACK, 11 message lỗi vào DLQ đúng dự kiến**.
- Queue chính kết thúc với **Ready = 0, Unacked = 0**. Booking health trả HTTP 200, status UP.
- Đã so toàn bộ row snapshot, ticket/sector/seat/promotion inventory và inbox trong phạm vi ID test trước/sau các case reject: không có thay đổi được commit.
- Tổng counter broker sau cả lượt test trước và lượt này: **19 delivery, 8 ACK**. Hai delivery/ACK đầu thuộc test tạo mới và replay ở lượt trước.
- **Giữ nguyên 11 message trong DLQ** để xem lại, không purge hoặc lấy message ra khỏi queue.

| Event test | ID | Trạng thái cuối |
| --- | --- | --- |
| QUANTITY, tái sử dụng event test lượt trước | `35b55145-98f7-4560-9f0d-8b48b12d81b2` | CANCELLED, version 3 |
| SEAT_MAP hỗn hợp | `32901c7f-28f4-42be-ba1b-4186ba606b60` | PUBLISHED, version 2 |

[Manifest và payload của từng case](D:/DATN/flash-ticket-platform/services/booking-service/target/rabbit-dashboard-cases/manifest.json). [Kết quả chi tiết](D:/DATN/flash-ticket-platform/services/booking-service/target/rabbit-dashboard-cases/results.json). Các file dưới `target` có thể bị xóa khi Maven clean.

Phạm vi chưa kiểm ở lượt này: counter held/purchased/used khác 0; ghế đang HELD/PURCHASED; nhiều consumer cạnh tranh; broker/DB mất kết nối và redelivery sau lỗi tạm thời. Kết quả trên không thay thế các test đó.
