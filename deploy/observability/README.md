# Bản sửa a–i và file bị ảnh hưởng

Danh sách tính từ HEAD trước khi sửa. File dùng chung xuất hiện ở mỗi mục liên quan. Cấu hình môi trường và quyền vận hành nằm trong [deploy/app](../app/README.md). Cách chạy thử và tài nguyên Docker nằm trong [deploy/rca-test](../rca-test/README.md).

## a. CD amd64 và arm64

CD chuẩn bị QEMU và Buildx, nhận JAR đã verify, truyền git SHA và build cả hai nền tảng. Spring image cần BuildKit hỗ trợ `ADD --checksum` và quyền tải release agent. Dockerfile tạo quyền đi qua `/otel` cho UID 10001. Keycloak cần thêm dependency JAR từ Maven.

- [.github/workflows/_deploy-service.yml](../../.github/workflows/_deploy-service.yml)
- [infrastructure/api-gateway/.dockerignore](../../infrastructure/api-gateway/.dockerignore)
- [infrastructure/api-gateway/Dockerfile](../../infrastructure/api-gateway/Dockerfile)
- [infrastructure/api-gateway/docker-entrypoint.sh](../../infrastructure/api-gateway/docker-entrypoint.sh)
- [infrastructure/discovery-server/.dockerignore](../../infrastructure/discovery-server/.dockerignore)
- [infrastructure/discovery-server/Dockerfile](../../infrastructure/discovery-server/Dockerfile)
- [infrastructure/discovery-server/docker-entrypoint.sh](../../infrastructure/discovery-server/docker-entrypoint.sh)
- [services/booking-service/.dockerignore](../../services/booking-service/.dockerignore)
- [services/booking-service/Dockerfile](../../services/booking-service/Dockerfile)
- [services/booking-service/docker-entrypoint.sh](../../services/booking-service/docker-entrypoint.sh)
- [services/event-service/.dockerignore](../../services/event-service/.dockerignore)
- [services/event-service/Dockerfile](../../services/event-service/Dockerfile)
- [services/event-service/docker-entrypoint.sh](../../services/event-service/docker-entrypoint.sh)
- [services/keycloak-spi/Dockerfile](../../services/keycloak-spi/Dockerfile)
- [services/payment-service/.dockerignore](../../services/payment-service/.dockerignore)
- [services/payment-service/Dockerfile](../../services/payment-service/Dockerfile)
- [services/payment-service/docker-entrypoint.sh](../../services/payment-service/docker-entrypoint.sh)
- [services/ticket-service/.dockerignore](../../services/ticket-service/.dockerignore)
- [services/ticket-service/Dockerfile](../../services/ticket-service/Dockerfile)
- [services/ticket-service/docker-entrypoint.sh](../../services/ticket-service/docker-entrypoint.sh)
- [services/user-service/.dockerignore](../../services/user-service/.dockerignore)
- [services/user-service/Dockerfile](../../services/user-service/Dockerfile)
- [services/user-service/docker-entrypoint.sh](../../services/user-service/docker-entrypoint.sh)

## b. Giới hạn bộ nhớ

Booking, Event và Payment đặt 1 GiB, Discovery 768 MiB, Keycloak 1536 MiB. Điều kiện tải một giờ vẫn chờ kiểm.

- [deploy/app/booking-service.yml](../../deploy/app/booking-service.yml)
- [deploy/app/discovery-server.yml](../../deploy/app/discovery-server.yml)
- [deploy/app/event-service.yml](../../deploy/app/event-service.yml)
- [deploy/app/keycloak.yml](../../deploy/app/keycloak.yml)
- [deploy/app/payment-service.yml](../../deploy/app/payment-service.yml)
- [services/event-service/.env.example](../../services/event-service/.env.example)

## c. Java agent và thuộc tính tích hợp

Agent 2.33.1 được pin SHA-256 trong image. Wrapper dùng chung identity cho log và OTLP, với instance ID mới khi JVM khởi động. Booking/Gateway tắt Lettuce observation của Spring. Event/Booking/Payment/Ticket bật thuộc tính RabbitMQ bổ sung.

- [infrastructure/api-gateway/.dockerignore](../../infrastructure/api-gateway/.dockerignore)
- [infrastructure/api-gateway/.env.example](../../infrastructure/api-gateway/.env.example)
- [infrastructure/api-gateway/Dockerfile](../../infrastructure/api-gateway/Dockerfile)
- [infrastructure/api-gateway/docker-entrypoint.sh](../../infrastructure/api-gateway/docker-entrypoint.sh)
- [infrastructure/api-gateway/src/main/resources/application.yaml](../../infrastructure/api-gateway/src/main/resources/application.yaml)
- [infrastructure/discovery-server/.dockerignore](../../infrastructure/discovery-server/.dockerignore)
- [infrastructure/discovery-server/.env.example](../../infrastructure/discovery-server/.env.example)
- [infrastructure/discovery-server/Dockerfile](../../infrastructure/discovery-server/Dockerfile)
- [infrastructure/discovery-server/docker-entrypoint.sh](../../infrastructure/discovery-server/docker-entrypoint.sh)
- [infrastructure/discovery-server/src/main/resources/application.yaml](../../infrastructure/discovery-server/src/main/resources/application.yaml)
- [services/booking-service/.dockerignore](../../services/booking-service/.dockerignore)
- [services/booking-service/.env.example](../../services/booking-service/.env.example)
- [services/booking-service/Dockerfile](../../services/booking-service/Dockerfile)
- [services/booking-service/docker-entrypoint.sh](../../services/booking-service/docker-entrypoint.sh)
- [services/booking-service/src/main/resources/application.yaml](../../services/booking-service/src/main/resources/application.yaml)
- [services/event-service/.dockerignore](../../services/event-service/.dockerignore)
- [services/event-service/.env.example](../../services/event-service/.env.example)
- [services/event-service/Dockerfile](../../services/event-service/Dockerfile)
- [services/event-service/docker-entrypoint.sh](../../services/event-service/docker-entrypoint.sh)
- [services/event-service/src/main/resources/application.yaml](../../services/event-service/src/main/resources/application.yaml)
- [services/payment-service/.dockerignore](../../services/payment-service/.dockerignore)
- [services/payment-service/.env.example](../../services/payment-service/.env.example)
- [services/payment-service/Dockerfile](../../services/payment-service/Dockerfile)
- [services/payment-service/docker-entrypoint.sh](../../services/payment-service/docker-entrypoint.sh)
- [services/payment-service/src/main/resources/application.yaml](../../services/payment-service/src/main/resources/application.yaml)
- [services/ticket-service/.dockerignore](../../services/ticket-service/.dockerignore)
- [services/ticket-service/.env.example](../../services/ticket-service/.env.example)
- [services/ticket-service/Dockerfile](../../services/ticket-service/Dockerfile)
- [services/ticket-service/docker-entrypoint.sh](../../services/ticket-service/docker-entrypoint.sh)
- [services/ticket-service/src/main/resources/application.yaml](../../services/ticket-service/src/main/resources/application.yaml)
- [services/user-service/.dockerignore](../../services/user-service/.dockerignore)
- [services/user-service/.env.example](../../services/user-service/.env.example)
- [services/user-service/Dockerfile](../../services/user-service/Dockerfile)
- [services/user-service/docker-entrypoint.sh](../../services/user-service/docker-entrypoint.sh)
- [services/user-service/src/main/resources/application.yaml](../../services/user-service/src/main/resources/application.yaml)

## d. Khởi tạo PostgreSQL

Đặt mode 100755 cho script init và thêm SQL quyền đọc Event/Payment. Kiểm init thực tế tạo bốn DB, tài khoản ERD có SELECT và thiếu quyền ghi.

- [deploy/postgres/00-business-databases.sh](../../deploy/postgres/00-business-databases.sh)
- [services/event-service/database/90-erd-reader.sql](../../services/event-service/database/90-erd-reader.sql)
- [services/payment-service/database/90-erd-reader.sql](../../services/payment-service/database/90-erd-reader.sql)

## e. Log JSON B16

Spring dùng schema, whitelist trường và stack không có nội dung exception. Gateway ghi một dòng khi request kết thúc. Các điểm nghiệp vụ ghi sau commit hoặc sau khi xác nhận kết quả xử lý. Log cấp WARN của thư viện giữ logger, severity và stack đã lọc. Hai assertion cấu hình cũ của Ticket/User đã cập nhật theo formatter.

- [.github/scripts/detect-changes.sh](../../.github/scripts/detect-changes.sh)
- [.github/workflows/_maven-verify.yml](../../.github/workflows/_maven-verify.yml)
- [deploy/app/_common.yml](../../deploy/app/_common.yml)
- [deploy/observability/README.md](../../deploy/observability/README.md)
- [deploy/observability/log.schema.json](../../deploy/observability/log.schema.json)
- [deploy/observability/requirements.txt](../../deploy/observability/requirements.txt)
- [deploy/observability/verify_logs.py](../../deploy/observability/verify_logs.py)
- [infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/observability/LogFields.java](../../infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/observability/LogFields.java)
- [infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/observability/SafeStackTracePrinter.java](../../infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/observability/SafeStackTracePrinter.java)
- [infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/web/CorrelationIdFilter.java](../../infrastructure/api-gateway/src/main/java/com/flashticket/api_gateway/web/CorrelationIdFilter.java)
- [infrastructure/api-gateway/src/main/resources/application.yaml](../../infrastructure/api-gateway/src/main/resources/application.yaml)
- [infrastructure/api-gateway/src/test/java/com/flashticket/api_gateway/ObservabilityTests.java](../../infrastructure/api-gateway/src/test/java/com/flashticket/api_gateway/ObservabilityTests.java)
- [infrastructure/discovery-server/src/main/java/com/flashticket/discovery_server/observability/LogFields.java](../../infrastructure/discovery-server/src/main/java/com/flashticket/discovery_server/observability/LogFields.java)
- [infrastructure/discovery-server/src/main/java/com/flashticket/discovery_server/observability/SafeStackTracePrinter.java](../../infrastructure/discovery-server/src/main/java/com/flashticket/discovery_server/observability/SafeStackTracePrinter.java)
- [infrastructure/discovery-server/src/main/resources/application.yaml](../../infrastructure/discovery-server/src/main/resources/application.yaml)
- [infrastructure/discovery-server/src/test/java/com/flashticket/discovery_server/ObservabilityTests.java](../../infrastructure/discovery-server/src/test/java/com/flashticket/discovery_server/ObservabilityTests.java)
- [services/booking-service/pom.xml](../../services/booking-service/pom.xml)
- [services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/Availability.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/Availability.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderItemView.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderItemView.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderPage.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderPage.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderView.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/OrderView.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/Problem.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/response/Problem.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot/PurchaseSnapshot.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot/PurchaseSnapshot.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot/SalesConfiguration.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/dto/snapshot/SalesConfiguration.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/AcceptPaymentListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/AcceptPaymentListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/BookingIssuanceResultListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/BookingIssuanceResultListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/EventCancellationListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/EventCancellationListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/SalesConfigurationListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/SalesConfigurationListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/observability/BusinessTelemetry.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/observability/BusinessTelemetry.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/observability/LogFields.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/observability/LogFields.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/observability/RabbitTelemetry.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/observability/RabbitTelemetry.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/observability/SafeStackTracePrinter.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/observability/SafeStackTracePrinter.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/order/service/impl/OrderServiceImpl.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/promotion/service/impl/PromotionServiceImpl.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/promotion/service/impl/PromotionServiceImpl.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/web/CorrelationFilter.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/web/CorrelationFilter.java)
- [services/booking-service/src/main/resources/application.yaml](../../services/booking-service/src/main/resources/application.yaml)
- [services/booking-service/src/test/java/com/flashticket/booking_service/ObservabilityTests.java](../../services/booking-service/src/test/java/com/flashticket/booking_service/ObservabilityTests.java)
- [services/event-service/src/main/java/com/flashticket/event_service/observability/EventLogCustomizer.java](../../services/event-service/src/main/java/com/flashticket/event_service/observability/EventLogCustomizer.java)
- [services/event-service/src/main/resources/application.yaml](../../services/event-service/src/main/resources/application.yaml)
- [services/event-service/src/test/java/com/flashticket/event_service/ObservabilityTests.java](../../services/event-service/src/test/java/com/flashticket/event_service/ObservabilityTests.java)
- [services/payment-service/pom.xml](../../services/payment-service/pom.xml)
- [services/payment-service/src/main/java/com/flashticket/payment_service/controller/FinanceController.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/controller/FinanceController.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/messaging/PaymentMessageListener.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/messaging/PaymentMessageListener.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/observability/BusinessTelemetry.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/observability/BusinessTelemetry.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/observability/LogFields.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/observability/LogFields.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/observability/RabbitTelemetry.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/observability/RabbitTelemetry.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/observability/SafeStackTracePrinter.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/observability/SafeStackTracePrinter.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/service/PaymentService.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/service/PaymentService.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/FinanceServiceImpl.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/FinanceServiceImpl.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/PaymentSagaServiceImpl.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/PaymentSagaServiceImpl.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/RefundServiceImpl.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/service/impl/RefundServiceImpl.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/worker/PaymentWorker.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/worker/PaymentWorker.java)
- [services/payment-service/src/main/resources/application.yaml](../../services/payment-service/src/main/resources/application.yaml)
- [services/payment-service/src/test/java/com/flashticket/payment_service/ObservabilityTests.java](../../services/payment-service/src/test/java/com/flashticket/payment_service/ObservabilityTests.java)
- [services/ticket-service/pom.xml](../../services/ticket-service/pom.xml)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/application/TicketService.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/application/TicketService.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/config/TicketLogFields.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/config/TicketLogFields.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/messaging/TicketBusinessListeners.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/messaging/TicketBusinessListeners.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/observability/BusinessTelemetry.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/observability/BusinessTelemetry.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/observability/RabbitTelemetry.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/observability/RabbitTelemetry.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/worker/TicketDeliveryWorker.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/worker/TicketDeliveryWorker.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/worker/TicketOutboxPublisher.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/worker/TicketOutboxPublisher.java)
- [services/ticket-service/src/main/resources/application.yaml](../../services/ticket-service/src/main/resources/application.yaml)
- [services/ticket-service/src/test/java/com/flashticket/ticket_service/ObservabilityTests.java](../../services/ticket-service/src/test/java/com/flashticket/ticket_service/ObservabilityTests.java)
- [services/ticket-service/src/test/java/com/flashticket/ticket_service/TicketLocalConfigurationTests.java](../../services/ticket-service/src/test/java/com/flashticket/ticket_service/TicketLocalConfigurationTests.java)
- [services/user-service/src/main/java/com/flashticket/user_service/identity/KeycloakAdminClient.java](../../services/user-service/src/main/java/com/flashticket/user_service/identity/KeycloakAdminClient.java)
- [services/user-service/src/main/java/com/flashticket/user_service/observability/SafeStackTracePrinter.java](../../services/user-service/src/main/java/com/flashticket/user_service/observability/SafeStackTracePrinter.java)
- [services/user-service/src/main/java/com/flashticket/user_service/observability/UserLogCustomizer.java](../../services/user-service/src/main/java/com/flashticket/user_service/observability/UserLogCustomizer.java)
- [services/user-service/src/main/resources/application.yaml](../../services/user-service/src/main/resources/application.yaml)
- [services/user-service/src/test/java/com/flashticket/user_service/BundledConfigurationTests.java](../../services/user-service/src/test/java/com/flashticket/user_service/BundledConfigurationTests.java)
- [services/user-service/src/test/java/com/flashticket/user_service/ObservabilityTests.java](../../services/user-service/src/test/java/com/flashticket/user_service/ObservabilityTests.java)

## f. Tên queue trên consumer span

Booking, Payment và Ticket đọc tên queue từ message properties rồi bổ sung vào span đang hoạt động. Maven chỉ thêm OTel API.

- [services/booking-service/pom.xml](../../services/booking-service/pom.xml)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/AcceptPaymentListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/AcceptPaymentListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/BookingIssuanceResultListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/BookingIssuanceResultListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/EventCancellationListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/EventCancellationListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/messaging/SalesConfigurationListener.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/messaging/SalesConfigurationListener.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/observability/RabbitTelemetry.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/observability/RabbitTelemetry.java)
- [services/booking-service/src/test/java/com/flashticket/booking_service/ObservabilityTests.java](../../services/booking-service/src/test/java/com/flashticket/booking_service/ObservabilityTests.java)
- [services/payment-service/pom.xml](../../services/payment-service/pom.xml)
- [services/payment-service/src/main/java/com/flashticket/payment_service/messaging/PaymentMessageListener.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/messaging/PaymentMessageListener.java)
- [services/payment-service/src/main/java/com/flashticket/payment_service/observability/RabbitTelemetry.java](../../services/payment-service/src/main/java/com/flashticket/payment_service/observability/RabbitTelemetry.java)
- [services/payment-service/src/test/java/com/flashticket/payment_service/ObservabilityTests.java](../../services/payment-service/src/test/java/com/flashticket/payment_service/ObservabilityTests.java)
- [services/ticket-service/pom.xml](../../services/ticket-service/pom.xml)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/messaging/TicketBusinessListeners.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/messaging/TicketBusinessListeners.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/observability/RabbitTelemetry.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/observability/RabbitTelemetry.java)
- [services/ticket-service/src/test/java/com/flashticket/ticket_service/ObservabilityTests.java](../../services/ticket-service/src/test/java/com/flashticket/ticket_service/ObservabilityTests.java)

## g. Log JSON và trace Keycloak

Keycloak dùng định dạng JSON và tracing native. Cấu hình runtime ở Compose và env mẫu.

- [.env.example](../../.env.example)
- [deploy/app/keycloak.yml](../../deploy/app/keycloak.yml)
- [services/keycloak-spi/Dockerfile](../../services/keycloak-spi/Dockerfile)

## h. Log PostgreSQL

Bật truy vấn chậm và chờ khóa, tắt xuất giá trị bind parameter. Kiểm local có log cho cả hai loại.

- [.env.example](../../.env.example)
- [deploy/docker-compose.postgresql.yml](../../deploy/docker-compose.postgresql.yml)

## i. Công cụ thử lỗi cách ly

Hook Booking/Event/Ticket, lease giới hạn, proxy JDBC/Redis, mock VNPay, công cụ mạng/CPU/DB và fixture/workload trong Compose riêng.

- [.gitignore](../../.gitignore)
- [deploy/rca-test/.env.example](../../deploy/rca-test/.env.example)
- [deploy/rca-test/README.md](../../deploy/rca-test/README.md)
- [deploy/rca-test/compose.yml](../../deploy/rca-test/compose.yml)
- [deploy/rca-test/control.schema.json](../../deploy/rca-test/control.schema.json)
- [deploy/rca-test/cpu.sh](../../deploy/rca-test/cpu.sh)
- [deploy/rca-test/db-down.sh](../../deploy/rca-test/db-down.sh)
- [deploy/rca-test/faultctl.py](../../deploy/rca-test/faultctl.py)
- [deploy/rca-test/fixtures.py](../../deploy/rca-test/fixtures.py)
- [deploy/rca-test/mock_vnpay.py](../../deploy/rca-test/mock_vnpay.py)
- [deploy/rca-test/netem.sh](../../deploy/rca-test/netem.sh)
- [deploy/rca-test/otel-collector.yaml](../../deploy/rca-test/otel-collector.yaml)
- [deploy/rca-test/test_faultctl.py](../../deploy/rca-test/test_faultctl.py)
- [deploy/rca-test/test_mock_vnpay.py](../../deploy/rca-test/test_mock_vnpay.py)
- [deploy/rca-test/toxiproxy.json](../../deploy/rca-test/toxiproxy.json)
- [deploy/rca-test/verify.py](../../deploy/rca-test/verify.py)
- [deploy/rca-test/workload.js](../../deploy/rca-test/workload.js)
- [services/booking-service/src/main/java/com/flashticket/booking_service/order/controller/OrderController.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/order/controller/OrderController.java)
- [services/booking-service/src/main/java/com/flashticket/booking_service/testing/RcaFaults.java](../../services/booking-service/src/main/java/com/flashticket/booking_service/testing/RcaFaults.java)
- [services/booking-service/src/main/resources/application-rca-test.yaml](../../services/booking-service/src/main/resources/application-rca-test.yaml)
- [services/booking-service/src/test/java/com/flashticket/booking_service/RcaFaultsTests.java](../../services/booking-service/src/test/java/com/flashticket/booking_service/RcaFaultsTests.java)
- [services/event-service/src/main/java/com/flashticket/event_service/api/EventController.java](../../services/event-service/src/main/java/com/flashticket/event_service/api/EventController.java)
- [services/event-service/src/main/java/com/flashticket/event_service/testing/RcaFaults.java](../../services/event-service/src/main/java/com/flashticket/event_service/testing/RcaFaults.java)
- [services/event-service/src/main/resources/application-rca-test.yaml](../../services/event-service/src/main/resources/application-rca-test.yaml)
- [services/event-service/src/test/java/com/flashticket/event_service/RcaFaultsTests.java](../../services/event-service/src/test/java/com/flashticket/event_service/RcaFaultsTests.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/application/TicketService.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/application/TicketService.java)
- [services/ticket-service/src/main/java/com/flashticket/ticket_service/testing/RcaFaults.java](../../services/ticket-service/src/main/java/com/flashticket/ticket_service/testing/RcaFaults.java)
- [services/ticket-service/src/main/resources/application-rca-test.yaml](../../services/ticket-service/src/main/resources/application-rca-test.yaml)
- [services/ticket-service/src/test/java/com/flashticket/ticket_service/RcaFaultsTests.java](../../services/ticket-service/src/test/java/com/flashticket/ticket_service/RcaFaultsTests.java)

## Hồ sơ duyệt và bàn giao

- [deploy/app/README.md](../../deploy/app/README.md)
- [docs/project/decision-register.md](../../docs/project/decision-register.md)

## Kiểm local ngày 11/11/2026

Các đầu ra kiểm nằm trong `.local/rca-test`, ngoài Git. Để soát lại, mở `review.patch`
hoặc xem file theo từng mục ở trên. `private/probes` có bằng chứng chi tiết:

- `import-audit.json`: so toàn bộ Java đã sửa với HEAD, không thiếu import ban đầu.
  Các import được Spotless sắp xếp. Lượt sửa trước đã xóa nhầm import Lombok,
  nay đã khôi phục. So lại cả chỗ dùng kiểu dữ liệu mới ngoài block import.
- `maven-summary.json`: kết quả Surefire. Cả 7 ứng dụng Spring và Keycloak SPI chạy
  `mvn -B -ntp verify`. JSON từ 7 ứng dụng qua `verify_logs.py`.
  `actionlint.log` và `shellcheck-2.log` lưu lần kiểm workflow và shell.
- `fresh-postgres.json`: init sạch tạo Event 12 bảng, Booking 15, Payment 13, Ticket 7.
  Cả bốn reader có SELECT, thiếu quyền ghi. Có log slow query và lock wait.
- `build-spring-4.log`, `build-keycloak-2.log`, `build-arm64-discovery-2.log` ở thư mục
  local cấp trên. Build 7 image Spring và Keycloak amd64, build Discovery arm64.
  Discovery arm64 khởi động với agent 2.33.1 và trả health UP qua giả lập.
  GitHub Actions và digest chung trên hai máy đích vẫn chưa kiểm.
- `otel-runtime-summary.json`: OTLP nhận span Redis từ Gateway qua `16380` và Booking
  qua `16379`. Consumer span Booking, Payment và Ticket có `rabbitmq.queue` và
  `messaging.destination.name`, gồm queue phát vé `ticket.issuance.q`.
  `mdc.traceId` của log Keycloak nối được với span Keycloak từ Collector.
  Kiểm Loki chưa chạy.
- `paths-*.json`: JDBC qua từng proxy và trực tiếp đúng DB, Redis trả PONG cả hai
  listener và đường trực tiếp. `proxy-bytes-*.txt` giữ counter proxy riêng.
- `fixture-purchase-2.log` và `private/fixtures.json`: một đơn `COMPLETED`, đúng một
  vé, gửi lại callback giữ nguyên tập vé. Lượt thử terminal ghi Ticket `FAILED` và
  không có vé, Booking `ISSUANCE_FAILED`, Payment `COMPENSATING` với đúng một refund
  lý do `TICKET_ISSUANCE_FAILED`. Mock cũ từ chối HTTP chunked nên refund dừng ở
  `PROCESSING`. Đã sửa đọc chunked và ổn định ngày thanh toán trong mock.
  `java-mock-refund.log` kiểm Java client thật với mock sửa, refund trả `PROCESSING`
  và querydr trả `SUCCEEDED`. Docker Desktop ngừng trước khi chạy lại toàn bộ Saga.

Còn cần lượt chạy stack sau sửa mock để xác nhận refund cuối, event cancel, replay
terminal và nhả tài nguyên qua toàn luồng. Các phép can thiệp có đối chứng cho HTTP,
pool, netem, CPU, DB và pause, tải k6 và kiểm một giờ, ARM thật, Loki và nghiệm thu
B4 vẫn chờ chạy. Báo cáo đồ án chỉ dùng các kết quả đã có file bằng chứng, giữ phần
còn thiếu ở trạng thái OPEN. Các lượt thử lỗi trước khi stack ổn định được giữ ở
log `build-*`, `up-stack-*` và `private/probes/fixture-*.log`.
