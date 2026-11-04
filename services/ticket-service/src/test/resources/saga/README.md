# Saga contract fixture

`payment-issue-tickets.json` giữ dữ liệu test `IssueTickets.json` từ Payment `dev/tuan`
commit `ed10c36c6c2f05ba4cb8574ecb99ccab782b5c83` (03/11/2026), chỉ đổi whitespace.
Đây là fixture tổng hợp của producer, không phải dữ liệu buyer hoặc dump queue AWS.
Giữ bigint/money biên để phát hiện mất precision/coercion giữa producer và Ticket.

Test broker dùng PostgreSQL database `ticket_test_<hex>` và RabbitMQ vhost cùng prefix,
cả hai chỉ ở loopback. Test tự khai báo topology trong vhost test; ứng dụng production
vẫn `spring.rabbitmq.dynamic=false`.

Chạy `./mvnw -Dtest=TicketSagaBrokerIT test` sau khi cung cấp:

- `TICKET_TEST_DB_URL`, `TICKET_TEST_DB_USER`, `TICKET_TEST_DB_PASSWORD` như TicketPostgresIT.
- `TICKET_TEST_RABBIT_PORT`, `TICKET_TEST_RABBIT_USER`, `TICKET_TEST_RABBIT_PASSWORD`,
  `TICKET_TEST_RABBIT_VHOST=ticket_test_<hex>` của broker test riêng.

Test gọi listener/transaction/outbox Ticket thật qua RabbitMQ thật. Assertion kết quả
đối chiếu wire contract Booking/Payment; không chạy coordinator Payment, transaction
Booking, VNPay hay SMTP thật và không thay thế nghiệm thu E2E ba service.
