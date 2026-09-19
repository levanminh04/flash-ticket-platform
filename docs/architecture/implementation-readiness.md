# Sẵn sàng hiện thực — từ bộ thiết kế đến lát cắt chạy được

> **Cập nhật ngữ cảnh 18/09:** theo [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) và [phân công](../project/roles.md). Tám project khung đã có trong commit `091fca1`; chưa có bằng chứng build/E2E đạt. Mobile là phần phụ, chỉ làm khi thực sự thừa thời gian (`PRJ-035`); chuẩn kỹ thuật được duyệt 14/09 giữ nguyên.

- Phiên bản: `READY-v0.1`; ngày: 2026-09-14; trạng thái: `APPROVED`.
- Người duyệt: Lê Văn Minh; ngày duyệt: 2026-09-14 (GOV-144). Phân lớp: `FORMATION`.
- Đầu vào: B11-C-v0.3 `APPROVED`; quyết định GOV-101–142 và PRJ-010–024 còn hiệu lực; B12/B13/B14/B16 trong đợt hoàn thiện hiện tại. B12-v0.2/B13-v0.1/B14-v0.1/B16-v0.1 đã được Minh duyệt theo thứ tự phụ thuộc tại GOV-144.
- Phạm vi: chỉ dẫn chia việc/khởi tạo, không phải bằng chứng build, deploy hoặc đăng nhập thật; việc tạo project khung đã được ghi nhận riêng ngày 18/09.

## 1. Có thể bắt đầu code khi nào?

Không cần thêm một vòng bàn nghiệp vụ Q-01–07. Những lựa chọn đó đã được ghi trong sổ quyết định. Bộ **schema, hợp đồng, sequence và chuẩn quan sát** đã được duyệt tại GOV-144; nhóm có thể bắt đầu lát cắt ở §5. B16 là đầu vào bắt buộc trước hiện thực xuyên service, không đợi cuối mới thêm correlation.

Các việc không phụ thuộc endpoint AWS — dựng project, unit test tiền/trạng thái, DTO/validation, repository với database local mới — không phải đợi hostname/SMTP hoàn tất. Chưa được coi là sẵn sàng nghiệm thu đăng ký, thanh toán sandbox hay triển khai EC2 khi các đầu vào tương ứng ở §6 còn thiếu.

## 2. Bộ Java / Boot / Cloud cụ thể

**Đã chốt:** Java 21, Spring Boot 4.x, Spring Cloud 2025.1.x. “Spring 4.x” ở đây là **Boot**, không phải Spring Framework 4 từ nhiều năm trước. Boot cung cấp cấu hình và bộ dependency; Cloud cung cấp Gateway, Config và discovery. Không có nghĩa mọi service phải dùng mọi thành phần Cloud.

**Baseline kỹ thuật được duyệt tại GOV-144 để tạo project cùng một bộ:** Java 21, Boot **4.1.1**, Cloud BOM **2025.1.3**, Maven Wrapper do Initializr sinh. Kiểm ngày 2026-09-14: tài liệu Boot 4.1.1 cho phép Java 17–26; trang Cloud hiển thị 2025.1.3 và bảng tương thích cho Boot 4.0.x/4.1.x (4.1 được hỗ trợ từ Cloud 2025.1.2). Đây là kiểm tài liệu tương thích, **chưa phải build ứng dụng đã PASS**. [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Cloud compatibility](https://spring.io/projects/spring-cloud/)

Để Boot parent/BOM quản lý phiên bản Spring Security, Data, Jackson và driver được quản lý; Cloud BOM quản lý Cloud starters. Không chép danh sách version của repo cũ vào năm project mới. Không dùng SNAPSHOT/RC hoặc `latest` trong manifest demo. [Boot managed dependencies](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)

Khi tạo project, phép kiểm bắt buộc là `mvnw verify`, dependency tree, khởi động với Java 21, serialize/deserialize DTO B13, JWT Resource Server, PostgreSQL/Mongo driver và một HTTP→AMQP→consumer trace. Version tương thích trên giấy không chứng minh thư viện QR, mapping, client HTTP hoặc cấu hình Security cũ tương thích. Dùng lại logic đã đối chiếu; sửa adapter/config theo Boot mới. Không đổi về stack cũ chỉ để hết lỗi compile.

## 3. Tạo những project nào trên Spring Initializr?

Chọn **Maven / Java / Jar / Java 21 / Boot 4.1.1**; group đề xuất `vn.flashticket`. Mỗi dòng dưới là một application độc lập. Tên thư mục là đề xuất tổ chức hiện thực, không tạo thêm service nghiệp vụ.

| Thư mục / artifact | Chọn dependency trên Initializr | Phần thêm đúng nhu cầu |
|---|---|---|
| `services/event-service` | Spring Web, Validation, Spring Security, OAuth2 Resource Server, Spring Data JPA, PostgreSQL Driver, Spring for RabbitMQ, Actuator | Publish cấu hình/hủy qua outbox; CRUD toàn bản nháp trong transaction local |
| `services/booking-service` | Như Event | Giữ chỗ/tồn kho/giới hạn mua/khuyến mãi bằng PostgreSQL; scheduler expiry; không dùng Redis làm nguồn sự thật thứ hai |
| `services/payment-service` | Như Event | VNPay adapter, Saga worker, refund/query worker; HTTP client của Spring; không cần thêm production payment-mock service |
| `services/ticket-service` | Như Event, thêm Java Mail Sender | QR library tương thích Java 21, encryption; phát trọn bộ, delivery retry và check-in |
| `services/user-service` | Spring Web, Validation, Spring Security, OAuth2 Resource Server, Spring Data MongoDB, Actuator | HTTP client gọi Keycloak Admin API theo B13; CAS trên application và worker phục hồi projection theo B13/B14; không cần transaction xuyên document trên Mongo standalone; không thêm JPA/PostgreSQL vào service này |

Chọn Spring Web loại MVC cho năm service nghiệp vụ. Không trộn WebFlux vào chúng chỉ vì Gateway dùng WebFlux. JPA không thay các câu SQL có khóa/conditional update cần thiết. Thêm test starter và Spring Security Test; kiểm persistence bằng đúng PostgreSQL/Mongo, không thay hết bằng H2. Trong môi trường không có Docker, runner PostgreSQL portable vẫn dùng được.

| Thành phần hỗ trợ | Tạo bằng Spring Initializr? | Cấu hình ban đầu |
|---|---|---|
| `infrastructure/api-gateway` | Có | Gateway **Server WebFlux**, Security, OAuth2 Resource Server, Actuator; không JPA/Mongo. Starter hiện tại: `spring-cloud-starter-gateway-server-webflux` |
| `infrastructure/config-server` | Có | Config Server, Actuator; kho config chỉ chứa cấu hình không nhạy cảm, endpoint không công khai |
| `infrastructure/discovery-server` | Có, theo bố trí B11-C | Eureka Server, Actuator. Các application tham gia dùng Eureka Discovery Client; không bật discovery trước khi service name/private address rõ |
| PostgreSQL, MongoDB, RabbitMQ, Redis, Keycloak | **Không** | Là phần mềm hạ tầng/container, không phải thư mục Spring service |
| Collector, Prometheus, Loki, Tempo, Grafana | **Không** | Manifest/config quan sát; không biến thành `logging-service` nghiệp vụ |
| RCA và lớp giải thích | Không tạo một Spring CRUD app để lấp chỗ | Đơn vị triển khai riêng đã chốt; hiện thực phương pháp theo bộ RCA, chỉ đọc kho quan sát |

Gateway starter lấy theo [tài liệu Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/starter.html). Config Client và Eureka Client chỉ thêm cho application thực sự nối chúng. Redis chỉ thêm starter khi đã có chức năng cache cụ thể; không cần thêm vào cả năm pom. OTel Java agent nằm trong cấu hình chạy, không phải dependency business chung. Prometheus cần `micrometer-registry-prometheus` với Actuator; không bật hai đường xuất cùng metric.

Có **5 project nghiệp vụ + 3 project Spring hỗ trợ** trong bố trí này. Không tạo Promotion, Notification, Inventory hay Order service riêng. Không thêm Kafka, Kubernetes, service mesh, Elasticsearch hoặc workflow engine chỉ để làm stack phong phú.

## 4. Database và Keycloak — hiểu đúng phần được tận dụng

SQL là **init tạo mới**, không di trú đơn/tài khoản cũ. Bốn database nghiệp vụ dùng credential riêng; database thứ năm `keycloak_db` dành cho Keycloak. Script của nhóm chuẩn bị database/quyền, **Keycloak tự tạo bảng nội bộ** quản lý realm, user, credential, role, session. Không thêm những bảng nội bộ đó vào ERD nghiệp vụ và không cho user-service đọc chúng trực tiếp.

Mongo giữ bốn collection hồ sơ, hồ sơ đăng ký organizer, organizer công khai và follow. `organizer_profiles` vẫn riêng như quyết định đã chốt; không nhập password từ Keycloak sang Mongo.

**Tận dụng Keycloak cũ:** tận dụng role nghiệp vụ và nguyên lý Code + PKCE, không tận dụng tài khoản/password/key/client-secret cũ. Realm fixture mới là whitelist có thể version-control và chỉnh cho môi trường mới. Tệp export nguyên gốc chứa thông tin nhạy cảm nên không commit/import nguyên khối.

Fixture hiện tại cố ý tắt registration và user-management client; callback còn `example.invalid`. Đây là **chưa hoàn thiện cấu hình**, không phải quyết định bỏ FR-49 hay duyệt organizer. B13 mô tả contract; batch tài liệu này không sửa/import fixture. Bước identity cần thay placeholder, cấu hình BUYER chỉ cho người thật, cấp ORGANIZER giữ BUYER bằng quyền Admin API tối thiểu, rồi mới bật đăng ký/client. Reset password tiếp tục hoãn; SMTP gửi vé thuộc Ticket, không phải lý do mở lại reset password.

Pin Keycloak và PostgreSQL **theo cặp được hỗ trợ**, sau đó kiểm import/token thật. PostgreSQL 16.15 là runtime đã dùng kiểm DDL local, không phải bằng chứng mọi bản Keycloak mới đều hỗ trợ PostgreSQL 16. Trang Keycloak hiện hành liệt kê phiên bản DB hỗ trợ riêng: không suy từ export `26.0.0` ra image mới, hoặc từ phiên bản Aurora ra PostgreSQL thường. [Keycloak database](https://www.keycloak.org/server/db)

Startup import bỏ qua realm đã tồn tại; muốn chứng minh cấu hình mới được áp phải import vào realm thử mới và đọc lại cấu hình. Không dùng import override lên realm nhóm đang sử dụng. [Keycloak import/export](https://www.keycloak.org/server/importExport)

## 5. Thứ tự code và điểm hoàn thành của từng lát cắt

| Lát cắt | Công việc có thể chia cho nhóm | Chỉ kết thúc khi |
|---|---|---|
| 0 — nền chạy | Một bộ version/build; credential theo owner; JWT; error envelope; correlation; outbox/inbox worker theo B13/B14 | Một request có log/trace xuyên HTTP và message; rollback business không phát message; service không truy DB khác |
| 1 — sự kiện bán được | Event draft→duyệt→công bố; Booking nhận snapshot; User identity/profile tối thiểu; FE adapter seat map | Reload giữ ghế ẩn và loại vé; không bán hidden; cấu hình version cũ không ghi đè mới |
| 2 — đặt và trả giữ chỗ | Booking tạo đơn, áp mã, hạn mua, expiry/cancel; email đơn | Các ca tranh chấp cuối nguồn cung, trả lặp, deadline đều đạt; money integer/HALF_UP đúng vector B13 |
| 3 — thu tiền và phát vé | Payment attempt/freeze/verify, Saga; Ticket atomic issuance; hoàn muộn/thừa/thất bại xác định | VNPay sandbox chạy một đơn thật trong sandbox; callback lặp không phát thêm vé; timeout không bị coi thành thất bại cuối |
| 4 — giao vé và check-in | Mail/QR tải lại; kiểm API check-in; hủy event một chiều và refund progress. App mobile scan chỉ làm khi thực sự dư thời gian (PRJ-035) | Gửi lỗi không hoàn tiền; hai scan chỉ một thành công; hủy hội tụ, không mở lại nguồn cung |
| 5 — phần phạm vi còn lại | Hồ sơ organizer/follow đơn giản; đọc đơn/đối soát/chi trả/CRUD đã duyệt | Không tự bỏ nghiệp vụ đã duyệt vì ít ưu tiên; không chặn lát cắt 1–4 để tô điểm CRUD |
| 6 — tải, lỗi, RCA | Workload, cổng giả lập, fault injection có kiểm soát và thu evidence | Luồng chính đã đúng; B15/B16 có run manifest, dữ liệu đủ; đánh giá RCA theo bộ RCA |

Phạm vi bốn thành viên lấy tại `docs/project/roles.md`; không tự khôi phục phân công cũ hoặc giao mobile cho Tuyến. Với mỗi lát cắt, chỉ định một người sở hữu hợp đồng và một người review kiểm thử; FE/BE cùng dùng DTO B13, không dịch enum/money mỗi nơi một kiểu.

## 6. AWS đã có gì; còn thiếu gì để chạy thật?

**Staged runtime baseline:** [aws-runtime-baseline.md](aws-runtime-baseline.md) là nguồn vận hành cho `CURRENT` Stage 1 và `TARGET` Stage 2–3. Theo xác nhận hiện hành của chủ đồ án, EC2 #1 `t3.small` là `TEMPORARY_CURRENT` có chủ ý cho PostgreSQL, MongoDB, RabbitMQ, Redis và Keycloak; developer chạy Spring application từ IDE/local JVM, còn Gateway/Eureka/Config Server vẫn local khi cần. Đây không phải topology cuối hay architecture defect. B11-C/B12 vẫn là target hai EC2. OTel Collector, Prometheus, Loki, Tempo và Grafana có EC2 #2 là `DERIVED_TARGET` placement; deployment, capacity, storage, version, limit và telemetry evidence vẫn là `NOT_DEPLOYED`/`RUNTIME_OPEN`. Điều đó không phải B16 acceptance. Không có AWS resource nào được kiểm hoặc sửa trong lượt tài liệu này.

| Đầu vào `OPEN` / owner | Cần cung cấp hoặc kiểm tại bước nào | Chặn gì |
|---|---|---|
| TEAM-AWS: inventory EC2 #1/#2, nhất là EC2 #2 capacity/disk/persistent volume/container runtime và Collector ingest endpoint | Theo thứ tự trong `aws-runtime-baseline.md`: inventory → pin image/version/tag/digest → provisional resource limit → minimum observability runtime → telemetry smoke → đo resource. Prometheus không tự scrape laptop sau NAT, nên application-metric evidence cho topology này là `NOT_RUN` | Telemetry smoke tập trung Stage 1; không chặn local JVM code hoặc được coi là B16 acceptance |
| TEAM-AWS: stable private cross-EC2 connectivity capability | Khi Stage 2 có dependency liên tục xuyên máy, chọn/implement/document/validate cơ chế; phương thức vẫn `OPEN` | AWS integration/benchmark/demo authoritative, **không chặn unit test/local code** |
| Nhóm identity: HTTPS issuer, Web origin/callback, Android redirect chính xác nếu thực hiện app mobile tùy thời gian, Keycloak/DB pin | Import realm thử sạch, registration/PKCE/token/role allow-deny tests | Đăng nhập/đăng ký E2E |
| Nhóm thanh toán: merchant sandbox, secret cấp riêng, callback public HTTPS | Một lượt tạo payment→IPN→query; refund sandbox theo quyền merchant | VNPay E2E; không chặn unit test adapter |
| Nhóm vận hành: SMTP tài khoản gửi và app password mới nếu dùng Gmail | Thử gửi vé cho địa chỉ thử được cho phép; retry có giới hạn | Delivery email; QR tải lại vẫn kiểm riêng |
| Nhóm vận hành: bản backup và lần restore tách biệt | Kiểm database, role, QR key version và realm sau phục hồi | Tuyên bố phục hồi dữ liệu |
| Minh / gate RCA: `RES-039`, tập node/metric `R0-OPEN-06` | Chốt đúng cửa nối/quyền riêng | Nghiệm thu giao kết quả RCA, không chặn mua vé |

Swap không thay RAM; cùng một PostgreSQL instance và hai EC2 không phải HA. Không phát hành tài liệu “đã triển khai” từ danh sách port trong note.

## 7. Secret và backup: mức đơn giản phù hợp nhóm

Config Server trả **cấu hình**, `.env` nạp **giá trị vào process**; cả hai không tự tạo bản sao dữ liệu hay bảo đảm chỉ người đúng quyền đọc được secret. Mất volume PostgreSQL thì có `.env` vẫn mất đơn. Mất khóa QR thì còn bảng vé vẫn không giải mã QR cũ.

Baseline tối thiểu được duyệt trong GOV-144: file secret ngoài Git, quyền đọc chỉ tài khoản chạy/deploy; mỗi service chỉ nhận secret của nó; không ghi secret vào image/log/Actuator; `.env.example` chỉ tên biến. Trên AWS có thể dùng Parameter Store SecureString hoặc Secrets Manager với instance role nếu nhóm đã dùng IAM phù hợp; không bắt buộc dựng Vault/Kubernetes. Chính sách quyền, mã hóa, rotation và truy vết truy cập là chức năng riêng của nơi giữ secret, không suy ra từ chữ “Config Server”. [AWS Secrets Manager](https://docs.aws.amazon.com/secretsmanager/latest/userguide/intro.html), [Parameter Store SecureString](https://docs.aws.amazon.com/systems-manager/latest/userguide/secure-string-parameter-kms-encryption.html)

Giữ riêng bản backup DB/Mongo, cấu hình realm sạch, tài khoản/quyền cần khôi phục và khóa QR theo version; mã hóa bản sao, giới hạn người đọc, không đặt bản duy nhất cùng volume ứng dụng. Với demo nhóm nhỏ: trước mỗi đợt thay đổi dữ liệu lớn tạo bản sao, restore vào môi trường tách biệt, kiểm số bản ghi và tải QR cũ. Đây là đề xuất vận hành, **chưa có số RPO/RTO hoặc lịch backup được chốt**. Restore nhiều datastore cần tạm dừng luồng ghi để lấy mốc nhất quán, hoặc có quy trình hội tụ đã thử; không ghép tùy ý các dump khác thời điểm rồi tuyên bố nhất quán.

Không đưa bootstrap credential trong note vào repo hoặc báo cáo. Nếu giá trị từng được chia sẻ ngoài phạm vi tin cậy, chủ hạ tầng rotate/revoke; tài liệu này không tự thực hiện rotation.

## 8. Kết luận bàn giao

Không còn chờ chốt lại Java/Boot/Cloud family, Q-01–07, profile riêng, ISSUING, ghế ẩn hoặc cách làm tròn. **Review gate đã được Minh duyệt tại GOV-144**; còn các thông tin môi trường ở đúng lát cắt. Bộ phiên bản/cách tổ chức project đã đề xuất được nhận làm baseline; những phiên bản image chưa có giá trị cụ thể vẫn OPEN. Chưa có application build/E2E thì không đánh dấu hoàn thành hiện thực.

Nội dung đưa vào báo cáo: kiến trúc đích, lý do cô lập quyền, tính đúng đắn của transaction, thứ tự hiện thực và kết quả kiểm thực tế; không đưa IP/secret hoặc lịch sử chuyển mã thành lập luận thiết kế.
