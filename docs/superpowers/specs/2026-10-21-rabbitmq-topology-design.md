# Thiết kế RabbitMQ cho FlashTicket

Ngày: 2026-10-21  
Trạng thái: Người dùng đã duyệt trong chat ngày 2026-10-21; đã duyệt plan và triển khai; bằng chứng trong progress ledger  
Phạm vi: Docker Compose local, broker và topology

## 1. Nguồn yêu cầu và mục tiêu

Nguồn yêu cầu chính là bảng exchange/queue do người dùng cung cấp trong chat và các sửa đổi tên sau đó. Tài liệu kiến trúc hoặc spec cũ trong repo không được dùng để thay đổi yêu cầu này.

Đã được người dùng xác nhận:

- Có 3 exchange: nghiệp vụ topic, identity direct và DLX direct.
- Mỗi queue chỉ đảm nhiệm một nhiệm vụ, nhận một loại message và có một handler logic; không gom các loại message khác nhiệm vụ để consumer tự phân luồng.
- Bảng 8 queue ban đầu được tách lại theo yêu cầu mới này thành 15 queue xử lý và 15 DLQ; tên và routing key chi tiết đã được chốt qua lần duyệt spec.
- Exchange bỏ `v1`, dùng hậu tố `.ex`.
- Queue bỏ `v1`, thêm hậu tố `.q`.
- DLQ bỏ `v1`, dùng hậu tố `.dlq` trên cùng tên gốc; không ghép `.q.dlq`.
- Tên exchange/queue/DLQ bỏ tiền tố `flash-ticket.`; exchange nghiệp vụ dùng `business.ex` theo xác nhận của người dùng.
- Viết spec trước khi triển khai.
- Đầu ra là Compose chạy từ root sẽ khởi động broker và tự có topology ngay lần đầu; không yêu cầu chạy lệnh init riêng sau `up`.
- Không thêm file script/test harness để kiểm thử. Khi triển khai, kiểm tra qua RabbitMQ Management UI bằng Playwright MCP.
- Topology import trực tiếp từ JSON khi RabbitMQ boot; tài khoản và ACL tạo bằng hook inline trong Compose, không đưa user/secret vào definitions và không tạo `bootstrap.sh`.

Người dùng đã duyệt toàn bộ spec ngày 2026-10-21. Các lựa chọn được trình bày là đề xuất trong những mục dưới đây đã được chấp thuận trong phạm vi task; việc triển khai vẫn chờ duyệt implementation plan và chọn cách thực thi.

Mục tiêu hoàn tất: chạy Compose từ root với duy nhất `.env` gốc, broker tự có đầy đủ exchange, queue, binding và DLQ; restart giữ dữ liệu; quyền publish identity tách khỏi nghiệp vụ; có bằng chứng kiểm thử trên volume mới.

## 2. Nguyên tắc và topology

### 2.1 Exchange

| Mã | Tên chính xác | Type | Vai trò |
| --- | --- | --- | --- |
| E1 | `business.ex` | `topic` | Cấu hình sự kiện, thanh toán, phát vé, hủy và bù trừ |
| E2 | `identity.ex` | `direct` | Keycloak → User; quyền publish riêng |
| E3 | `dlx.ex` | `direct` | Chuyển message lỗi tới đúng DLQ |

Cả 3 exchange durable, không auto-delete. Con số 3 chỉ tính exchange ứng dụng; các exchange hệ thống RabbitMQ vẫn tồn tại.

### 2.2 Queue và DLQ đề xuất: mỗi queue một nhiệm vụ

| Service | Exchange đầu vào | Queue | DLQ | Phạm vi xử lý |
| --- | --- | --- | --- | --- |
| Booking | E1 | `booking.sales-configured.q` | `booking.sales-configured.dlq` | Áp dụng cấu hình bán/nguồn cung/giá/hạn mức/khuyến mãi |
| Booking | E1 | `booking.event-cancelled.q` | `booking.event-cancelled.dlq` | Ghi marker hủy và chặn giao dịch mới |
| Booking | E1 | `booking.accept-payment.q` | `booking.accept-payment.dlq` | Phân xử yêu cầu chấp nhận khoản thu và chốt giữ chỗ |
| Booking | E1 | `booking.tickets-issued.q` | `booking.tickets-issued.dlq` | Xác nhận đủ vé đã phát và hoàn tất đơn |
| Booking | E1 | `booking.issuance-failed.q` | `booking.issuance-failed.dlq` | Ghi thất bại phát vé và xử lý tài nguyên theo yêu cầu từ Payment |
| Payment | E1 | `payment.finance-configured.q` | `payment.finance-configured.dlq` | Áp dụng cấu hình phí đã duyệt |
| Payment | E1 | `payment.event-cancelled.q` | `payment.event-cancelled.dlq` | Ghi marker hủy và khởi động hoàn tiền hàng loạt |
| Payment | E1 | `payment.start.q` | `payment.start.dlq` | Snapshot freeze từ Booking, guard payable, tạo attempt/URL; tách chờ và retry HTTP guard |
| Payment | E1 | `payment.decision.q` | `payment.decision.dlq` | Xử lý quyết định chấp nhận khoản thu do Booking trả về |
| Payment | E1 | `payment.ticket-issuance-failed.q` | `payment.ticket-issuance-failed.dlq` | Xử lý lỗi phát vé từ Ticket và điều phối bù trừ |
| Payment | E1 | `payment.order-completed.q` | `payment.order-completed.dlq` | Kết thúc Saga theo xác nhận đơn hoàn tất từ Booking |
| Ticket | E1 | `ticket.access-configured.q` | `ticket.access-configured.dlq` | Áp dụng organizer và cấu hình cửa sổ check-in |
| Ticket | E1 | `ticket.event-cancelled.q` | `ticket.event-cancelled.dlq` | Ghi marker hủy, chặn phát vé/check-in và khởi động vô hiệu vé |
| Ticket | E1 | `ticket.issuance.q` | `ticket.issuance.dlq` | Phát đủ vé/QR và lưu kết quả; consumer có thể điều chỉnh concurrency riêng |
| User | E2 | `user.identity.q` | `user.identity.dlq` | Đảm bảo hồ sơ theo identitySubject; consumer sau này phải xử lý lặp |

Đề xuất cả 30 queue là quorum, durable, không exclusive, không auto-delete. Broker local chỉ có một node: quorum ở đây không tạo khả năng chịu mất node hay mất volume. Không thêm queue retry hoặc queue nghiệp vụ ngoài danh sách.

Một handler logic không đồng nghĩa chỉ một consumer instance: có thể tăng concurrency hoặc số replica của cùng handler để chia tải. Mỗi queue có backlog, retry/DLQ và cấu hình consumer riêng. Các listener có thể cùng chạy trong một JVM của service, không cần một container cho mỗi queue.

Không cần tự viết dispatcher theo routing key/messageType trong từng listener. Vẫn phải deserialize đúng kiểu, kiểm schema/dữ liệu, idempotency và điều kiện trạng thái nghiệp vụ. Ví dụ handler quyết định thanh toán vẫn cần xử lý các outcome của chính quyết định đó; một nhiệm vụ không có nghĩa mọi nhánh nghiệp vụ đều biến mất.

Tách queue không bảo đảm thứ tự giữa các loại message. Handler cấu hình/hủy sau này vẫn phải kiểm version, marker hủy và transaction phù hợp, thay vì giả định cấu hình luôn tới trước hủy.

### 2.3 Đối chiếu cách làm trong framework và mã nguồn thực tế

- [MassTransit — Endpoint Mapping Strategies](https://masstransit.massient.com/configuration#endpoint-mapping-strategies): khuyến nghị mặc định một loại consumer trên mỗi queue, cho phép cấu hình concurrency/retry riêng và tránh backlog của nhiệm vụ khác. Consumer framework ở đây là loại handler, không phải giới hạn một instance.
- [eShopOnContainers — EventBusRabbitMQ](https://github.com/dotnet-architecture/eShopOnContainers/blob/dev/src/BuildingBlocks/EventBus/EventBusRabbitMQ/EventBusRabbitMQ.cs): dự án tham chiếu của Microsoft dùng một consumer đọc queue, lấy routing key rồi tìm handler tương ứng. Đây là ví dụ cho phương án gom và dispatch, không phải bằng chứng hệ production đang vận hành.
- [Spring AMQP — Multi-method Listeners](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven/method-selection.html): hỗ trợ tự chọn method theo kiểu payload, nên gom queue không bắt buộc phải tự viết switch. Tuy nhiên cách này vẫn dùng chung backlog; spec chọn tách queue theo mong muốn người dùng.

Đây là đối chiếu các phương án đã tồn tại, không phải một chuẩn RabbitMQ bắt buộc. Chi phí của lựa chọn tách là tăng từ 16 lên 30 queue tính cả DLQ, thêm listener/channel và policy cần quản lý. Không thêm MassTransit hoặc thư viện .NET vào dự án Java.

## 3. Routing key đề xuất — cần duyệt cùng spec

Tên routing key chưa được người dùng cung cấp. Bảng này là đề xuất theo các flow người dùng mô tả, không phải yêu cầu kế thừa từ tài liệu cũ. Hậu tố `.v1` ở routing key là phiên bản message được đề xuất; yêu cầu bỏ `v1` áp dụng cho tên exchange, queue và DLQ.

| Exchange | Queue nhận | Routing key cụ thể |
| --- | --- | --- |
| E1 | `booking.sales-configured.q` | `event.sales-configured.v1` |
| E1 | `booking.event-cancelled.q` | `event.cancelled.v1` |
| E1 | `booking.accept-payment.q` | `booking.accept-payment.v1` |
| E1 | `booking.tickets-issued.q` | `ticket.issued.v1` |
| E1 | `booking.issuance-failed.q` | `booking.issuance-failed.v1` |
| E1 | `payment.finance-configured.q` | `event.finance-configured.v1` |
| E1 | `payment.event-cancelled.q` | `event.cancelled.v1` |
| E1 | `payment.start.q` | `payment.start-requested.v1` |
| E1 | `payment.decision.q` | `booking.payment-decision.v1` |
| E1 | `payment.ticket-issuance-failed.q` | `ticket.issuance-failed.v1` |
| E1 | `payment.order-completed.q` | `booking.order-completed.v1` |
| E1 | `ticket.access-configured.q` | `event.access-configured.v1` |
| E1 | `ticket.event-cancelled.q` | `event.cancelled.v1` |
| E1 | `ticket.issuance.q` | `ticket.issue.v1` |
| E2 | `user.identity.q` | `identity.user-created.v1` |

- Tổng cộng 15 binding đầu vào; mỗi queue có đúng một routing key đầu vào và một handler logic cho loại message đó.
- Không dùng wildcard `#` hoặc `*` để bind nghiệp vụ.
- `event.cancelled.v1` tới đúng 3 queue `booking.event-cancelled.q`, `payment.event-cancelled.q`, `ticket.event-cancelled.q`: cùng sự kiện nhưng mỗi service có nhiệm vụ riêng, không dùng chung queue giữa các service.
- Đề xuất thay routing key kết quả Ticket dùng chung bằng `ticket.issued.v1` cho Booking và `ticket.issuance-failed.v1` cho Payment. Ticket phải publish đúng key theo kết quả khi producer được triển khai; broker topic không tự lọc trường trạng thái trong JSON. Không bind cùng message kết quả vào hai queue rồi bắt consumer bỏ qua nhánh không liên quan.
- Khi Payment yêu cầu Booking ghi nhận phát vé thất bại, đề xuất key riêng `booking.issuance-failed.v1`. Đây là thông điệp khác với lỗi phát vé Ticket gửi Payment để bắt đầu bù trừ.
- Chưa chốt payload identity, các loại sự kiện identity khác, khả năng phát lại từ Keycloak hoặc cơ chế phục hồi hồ sơ. Task chỉ chuẩn bị kênh `identity.user-created.v1`; không tuyên bố đã có đồng bộ người dùng đầu-cuối.

## 4. Dead-letter và lưu bền

Đề xuất dùng policy RabbitMQ cho DLX thay vì đóng cứng các `x-dead-letter-*` trong khai báo queue.

Mỗi queue xử lý có một policy khớp chính xác tên queue, gồm:

- `dead-letter-exchange = dlx.ex`.
- `dead-letter-routing-key = <tên DLQ tương ứng>`.
- `dead-letter-strategy = at-least-once`.
- `overflow = reject-publish`, điều kiện cần cho cơ chế dead-letter trên quorum.
- `delivery-limit = 20` để giới hạn việc redelivery; đây không phải lịch retry có backoff.

Mỗi DLQ bind vào E3 bằng đúng tên DLQ của nó. Có 15 binding dead-letter, tổng cộng 30 binding ứng dụng.

Ví dụ:

```text
booking.sales-configured.q
  -- reject/nack với requeue=false -->
dlx.ex
  -- routing key booking.sales-configured.dlq -->
booking.sales-configured.dlq
```

Policy của queue xử lý không được khớp DLQ. DLQ không có TTL, không có DLX quay về queue gốc, không tự xóa hoặc tự phát lại message.

At-least-once có thể tạo bản lặp; không hứa exactly-once. Publisher sau này phải dùng persistent message, publisher confirms và kiểm tra unroutable; consumer ACK sau khi hoàn thành tác động cần lưu. Phần mã ứng dụng đó nằm ngoài task.

Cơ chế policy và quorum dựa trên tài liệu kỹ thuật của RabbitMQ: [Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx), [Quorum Queues](https://www.rabbitmq.com/docs/quorum-queues). Các nguồn này chỉ giải thích cơ chế broker, không quyết định tên hay flow của dự án.

## 5. Phương án triển khai đề xuất

Các lựa chọn đã cân nhắc:

1. **Lựa chọn theo yêu cầu mới: native boot-time JSON import + user hook inline trong Compose.** Topology quản lý tập trung trong Git; `rabbitmq.conf` bật native import, hook Compose chỉ cấp tài khoản/quyền từ env. Không custom image hoặc file bootstrap shell.
2. Để từng service tự khai báo topology: phụ thuộc service đã có publisher/consumer và dễ lệch cấu hình; không chọn cho task hạ tầng này.
3. Tạo thủ công qua Management UI: nhanh cho thử nghiệm nhưng không bảo đảm tái lập; không chọn làm cơ chế setup.

### 5.1 Runtime local

- Image đề xuất `rabbitmq:4.3.6-management`, pin patch, không dùng `latest`. Nhánh/patch này được ghi nhận tại [RabbitMQ Release Information](https://www.rabbitmq.com/release-information) khi viết spec; bước triển khai phải xác minh tag Docker tồn tại.
- Một service `rabbitmq`, hostname cố định `rabbitmq`, node name `rabbit@rabbitmq`.
- Named volume `rabbitmq-data` mount vào `/var/lib/rabbitmq`.
- Cùng network `flash-ticket-infra` và cùng Compose project với PostgreSQL/Keycloak.
- AMQP bind `127.0.0.1:5672`; Management bind `127.0.0.1:15672`.
- `restart: unless-stopped`, stop grace period 30 giây, memory limit mặc định 1 GiB qua env.
- Vhost cố định `/` cho scope local này.
- JVM từ IDE dùng `RABBITMQ_HOST=localhost`; container dùng hostname `rabbitmq`.
- Không tạo Dockerfile hoặc thêm service init riêng.

### 5.2 Khởi tạo và readiness

`deploy/rabbitmq/rabbitmq.conf` bật native import của RabbitMQ:

```ini
definitions.import_backend = local_filesystem
definitions.local.path = /etc/rabbitmq/definitions.json
```

`definitions.json` chứa vhost `/` cùng topology/policy, không có users, permissions, password hoặc hash. RabbitMQ tự import trong quá trình boot; hook Compose không gọi `import_definitions` và không tự tạo từng exchange/queue bằng CLI. [RabbitMQ definitions import](https://www.rabbitmq.com/docs/definitions)

Boot-time import trên node trống bỏ qua việc tạo default user/vhost. Vì vậy `RABBITMQ_DEFAULT_USER/PASS` không được dùng làm cơ chế provision tài khoản trong phương án này; vhost `/` được tạo từ JSON, còn 3 tài khoản tạo riêng bằng native CLI. [RabbitMQ access control](https://www.rabbitmq.com/docs/access-control)

Compose có một hook `post_start` viết inline bằng `sh -ec`, chạy user `rabbitmq`, đọc credential từ `environment`. Docker Compose từ 2.30 hỗ trợ hook; không có file `.sh` bên ngoài. Hook này là logic runtime tạo user, không phải test script. [Docker lifecycle hooks](https://docs.docker.com/compose/how-tos/lifecycle/)

Trình tự khởi tạo:

1. RabbitMQ boot và native import vhost, exchange, queue, binding, policy từ JSON. JSON sai/type xung đột phải làm startup lỗi rõ ràng.
2. Hook xóa marker `/tmp/rabbitmq-users-ready`, chờ `rabbitmqctl wait --pid 1 --timeout 120` trong tối đa 120 giây; không giả định container Started đồng nghĩa import xong.
3. Hook tạo/cập nhật 3 tài khoản, tags và quyền từ env bằng `rabbitmqctl`, không in password. Chỉ xóa `guest` nếu tồn tại sau khi admin mới authenticate thành công.
4. Hook đặt marker sau khi mọi thao tác tài khoản/quyền thành công.
5. Healthcheck yêu cầu node running, AMQP connectivity và marker. `up --wait` chỉ đạt khi native import và user provisioning đều xong.

Chạy lại cùng cấu hình không purge/xóa queue, message hoặc volume. Import có xung đột type/argument phải báo lỗi, không tự xóa queue để sửa.

Khi Docker tự restart container, broker native import lại topology; tài khoản/quyền đã tạo được giữ trong named volume. Compose hook không phải cơ chế chạy trên mọi Docker daemon restart. Nếu user hook thất bại ở lần đầu, container chưa healthy; sửa cấu hình rồi Compose force-recreate riêng RabbitMQ trên cùng volume để chạy lại hook. Không yêu cầu người dùng chạy lệnh init riêng sau lần `up` đầu.

### 5.3 Tài khoản và env

Đề xuất 3 tài khoản cho scope local:

| Tài khoản | Quyền |
| --- | --- |
| Admin vận hành | Management UI và toàn quyền trong vhost `/`; dùng cho setup/kiểm tra, không cấp cho ứng dụng |
| Application | Publish E1 và E3; consume đúng 15 queue xử lý; không publish E2, không consume DLQ, không tự tạo/xóa/bind topology |
| Identity publisher | Chỉ publish E2; không publish E1/E3, không consume và không quản lý topology |

Tài khoản application dùng chung cho các service trong lần setup local này. Đây chỉ là tách quyền publish identity/nghiệp vụ theo yêu cầu, chưa phải ACL riêng cho từng service.

Mọi biến nằm trong `.env` ở root; `.env.example` chỉ chứa tên/default và password trống:

| Biến | Giá trị/ý nghĩa |
| --- | --- |
| `RABBITMQ_HOST` | Giữ `localhost` cho ứng dụng chạy từ IDE |
| `RABBITMQ_PORT` | Giữ `5672` |
| `RABBITMQ_USERNAME` | Application, đề xuất `flash_ticket_app` |
| `RABBITMQ_PASSWORD` | Password application, bắt buộc |
| `RABBITMQ_ADMIN_USERNAME` | Đề xuất `flash_ticket_admin` |
| `RABBITMQ_ADMIN_PASSWORD` | Password admin, bắt buộc |
| `RABBITMQ_IDENTITY_USERNAME` | Đề xuất `flash_ticket_identity` |
| `RABBITMQ_IDENTITY_PASSWORD` | Password identity publisher, bắt buộc |
| `RABBITMQ_MANAGEMENT_PORT` | `15672` |
| `RABBITMQ_MEMORY_LIMIT` | `1g` |

Hook inline trong Compose cấp quyền bằng CLI trong container; password đi qua môi trường, không hardcode trong YAML hoặc nội suy chuỗi vào JSON. Không bật shell xtrace. Không dùng tài khoản `guest`; chỉ loại bỏ `guest` nếu có sau khi đã tạo tài khoản quản trị hợp lệ.

### 5.4 File và lệnh dự kiến

File triển khai sau khi spec và kế hoạch được duyệt:

- `deploy/docker-compose.rabbitmq.yml`: service, env, volume, network, user/ACL hook inline và healthcheck.
- `deploy/rabbitmq/rabbitmq.conf`: bật native boot-time definitions import.
- `deploy/rabbitmq/definitions.json`: vhost `/`, exchange, queue, binding, policy; không tài khoản/quyền/secret.
- `.env.example`, `.env` local và `deploy/README.md`: đồng bộ biến và hướng dẫn từ root.

Chỉ có 3 file runtime mới ở trên. Không tạo `bootstrap.sh`, `verify.ps1`, Java smoke check, test project hoặc test runner. Shell/Docker CLI dùng để kiểm Compose, chạy container và đọc log; phần kiểm tra giao diện/topology/message thực hiện bằng Playwright MCP trên Management UI.

Lệnh chạy từ root sau khi triển khai:

```powershell
docker compose --env-file .env -f deploy/docker-compose.postgresql.yml -f deploy/docker-compose.keycloak.yml -f deploy/docker-compose.rabbitmq.yml up -d --wait rabbitmq
```

README cập nhật các lệnh quản lý stack để dùng cùng tập Compose file khi cần tránh orphan warning. Không dùng `--remove-orphans` để xử lý warning bằng cách xóa các container đang dùng.

## 6. Tiêu chí kiểm thử và nghiệm thu

1. Parse Compose thành công; thiếu password bắt buộc phải dừng với thông báo tên biến, không in secret.
2. Trên named volume mới và network test riêng, một lần `up --wait` hoàn thành native JSON import lẫn user hook và đạt healthy, không có init thủ công.
3. Có đúng tên/type 3 exchange ứng dụng, 15 queue xử lý và 15 DLQ; tên exchange/queue/DLQ không có `.v1` hoặc tiền tố `flash-ticket.`.
4. Có 15 binding đầu vào đúng bảng mục 3 và 15 binding E3 → DLQ; mỗi queue xử lý chỉ có một routing key đầu vào, không có binding nghiệp vụ wildcard. `ticket.issued.v1` chỉ tới queue hoàn tất đơn Booking; `ticket.issuance-failed.v1` chỉ tới queue xử lý lỗi phát vé Payment.
5. Qua Playwright MCP, kiểm tra toàn bộ binding và publish message mẫu bằng Management UI: hủy sự kiện tới đúng 3 queue, vé thành công chỉ tới Booking, vé thất bại chỉ tới Payment, identity tới User. Không coi publish qua UI là kiểm thử AMQP publisher confirms hoặc mandatory return.
6. Kiểm tra policy/DLQ mapping cho cả 15 queue qua UI; chạy thử một queue đại diện bằng Get messages với reject/nack không requeue nếu UI hỗ trợ, xác minh message vào đúng DLQ và `x-death` có queue nguồn. Nếu UI không cung cấp thao tác này, ghi rõ chưa kiểm được chuyển DLQ thực tế; không tự thêm AMQP test harness hoặc giả nhận đã kiểm.
7. Publish một persistent test message trên broker test, restart và kiểm tra message còn trong queue qua UI; đọc và ACK sau kiểm tra. Force-recreate riêng broker trên cùng volume chạy lại native import/user hook không nhân đôi binding hoặc tạo queue tên khác.
8. Qua Admin UI, xác minh tag và configure/write/read permissions của 3 user đúng mục 5.3. App/identity không có management tag nên không dùng việc đăng nhập UI bị từ chối để suy ra AMQP ACL đã đúng. Kiểm enforcement bằng AMQP client không thuộc lần nghiệm thu này.
9. Khởi tạo trên volume mới chỉ có secret trong env vẫn tạo được admin đăng nhập Management; definitions và log không chứa password.
10. Native import hoặc user hook lỗi không được báo healthy; sửa cấu hình và force-recreate riêng broker phải phục hồi được mà không xóa volume.
11. Tài nguyên kiểm thử dùng volume/network riêng và được dọn sau kiểm tra; không xóa volume PostgreSQL, MongoDB hoặc dữ liệu broker đang dùng.

Phương thức kiểm thử ở mục này thay thế yêu cầu test harness trong bản trước theo chỉ dẫn mới của người dùng. Không lưu file test/screenshot/báo cáo vào repo nếu không cần; chỉ báo lại kết quả và giới hạn kiểm chứng.

## 7. Ranh giới task

Task cung cấp broker, topology và credentials local. Không triển khai publisher/consumer nghiệp vụ, Keycloak SPI, đồng bộ hồ sơ thực tế, idempotency/outbox/inbox, retry worker, hoàn tiền hoặc Saga.

Không tự đổi tài liệu hợp đồng cũ để biến routing key đề xuất thành quyết định toàn dự án. Chưa chuyển dữ liệu từ queue cũ sang queue mới; nếu phát hiện broker cũ có message, giữ dữ liệu và trình phương án chuyển riêng trước khi đổi.

Cluster HA, TLS/ingress và deployment AWS không nằm trong lần setup Docker Compose local này.

## 8. Bước tiếp theo sau khi duyệt spec

Người dùng đã duyệt routing key ở mục 3 và lựa chọn runtime/ACL ở mục 5 cùng toàn bộ spec. Viết kế hoạch triển khai, đưa người dùng xem và chọn cách thực thi trước khi sửa Compose hoặc chạy broker.

