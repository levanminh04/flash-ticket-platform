# Thiết kế deploy Keycloak HTTP và PostgreSQL

- Ngày: 2026-10-03.
- Skill: `superpowers:brainstorming`.
- Phạm vi: cấu hình deploy hiện có và theme login bằng CSS.
- Phân loại tài liệu: FORMATION; không thay quyết định kiến trúc đã duyệt.
- Trạng thái: người dùng yêu cầu triển khai inline ngày 2026-10-03; các giá trị tuning khởi điểm là CANDIDATE do agent chọn, chưa có benchmark.
- CPU/instance/placement AWS và ngưỡng nghiệm thu tải: OPEN.

## 1. Mục tiêu và yêu cầu đã xác nhận

| Yêu cầu | Trạng thái | Nguồn |
| --- | --- | --- |
| Keycloak dùng HTTP trực tiếp, chưa cần Nginx | USER_CONFIRMED | Yêu cầu setup hiện tại |
| Cấu hình phục vụ tải | USER_CONFIRMED | Yêu cầu setup hiện tại; chưa có ngưỡng tải được xác nhận |
| Có CSS để đổi theme | USER_CONFIRMED | Yêu cầu setup hiện tại |
| Setup lại PostgreSQL | USER_CONFIRMED | Yêu cầu setup hiện tại |
| Note mô tả những gì đã setup trong hai Compose | USER_CONFIRMED | Yêu cầu setup hiện tại |
| Bỏ nội dung backup khỏi Note | USER_CONFIRMED | Yêu cầu trước đó; không suy ra bỏ yêu cầu phục hồi của dự án |
| Lưu nội dung Superpowers vào Markdown trong folder specs theo format chuẩn | USER_CONFIRMED | Yêu cầu lưu tài liệu hiện tại |

## 2. Bối cảnh và giới hạn

Hai Compose hiện nằm trong `deploy/`; `.env` chứa cấu hình môi trường và secret. Runtime baseline mô tả Stage 1 dùng EC2 #1 t3.small cho infrastructure, nhưng CPU/RAM và placement cho lần setup này chưa được người dùng xác nhận lại.

Thiết kế này không tạo service, schema nghiệp vụ, HA hoặc reverse proxy mới. Database/role phải theo B12; không dùng tài khoản bootstrap PostgreSQL làm runtime credential của Keycloak. Không thay đổi hoặc xóa dữ liệu volume hiện có để ép init lại.

HTTP trực tiếp là lựa chọn thử tạm của người dùng. Listener HTTP và yêu cầu SSL của realm là hai cấu hình riêng; phải kiểm khả năng đăng nhập từ laptop, không suy ra chỉ bật HTTP là đủ.

## 3. Phương án đề xuất — CANDIDATE

### 3.1. Keycloak

- Giữ production command `start`, bật HTTP cổng 8080, hostname lấy từ `.env` và khớp endpoint thực tế.
- Dùng PostgreSQL vendor `postgres`, database `keycloak_db`, runtime role `keycloak_app`; kiểm schema/grants đang có trước khi cấu hình.
- Bỏ fallback mật khẩu đã biết; thiếu secret thì Compose báo lỗi thay vì chạy với credential mặc định.
- Chọn memory limit, connection pool và hàng đợi request sau khi xác nhận tài nguyên máy. Giới hạn hàng đợi để tránh tích lũy request vô hạn; khi vượt ngưỡng có thể trả 503.
- Không tự thêm metrics, cluster hoặc các option tối ưu preview.

### 3.2. PostgreSQL và kết nối

- Publish `127.0.0.1:5432:5432` cho quản trị qua SSH tunnel.
- Nếu hai container cùng host: kết nối bằng hostname service `postgres` trên Docker network chung.
- Nếu khác host: cần endpoint private riêng cho runtime; mapping loopback không cung cấp kết nối xuyên EC2.
- Connection limit và bộ nhớ PostgreSQL phải khớp tổng pool của các ứng dụng và tài nguyên máy; giữ các thiết lập bảo đảm tính bền vững dữ liệu.
- Kiểm database/role/volume hiện có trước khi tạo hoặc sửa bootstrap; thay env không tự đổi credential trong cluster đã khởi tạo.

### 3.3. Theme CSS

- Đề xuất theme login `flash-ticket`, kế thừa theme Keycloak có sẵn; chưa chốt màu sắc hoặc thiết kế giao diện cụ thể.
- Thêm `theme.properties` và CSS riêng, mount read-only vào thư mục theme của Keycloak.
- Giữ theme cache bật khi chạy để tránh giảm hiệu năng; Note hướng dẫn chọn theme trong Admin Console và áp dụng thay đổi CSS.
- Không sửa trực tiếp theme đóng gói trong image hoặc thay renderer/templates khi CSS đủ đáp ứng.

### 3.4. Note và vận hành

Viết lại `deploy/Note.md` theo cấu hình thực sự đã triển khai trong repo: mapping env, network/port, tài nguyên, DB role, lệnh Compose, SSH tunnel, đăng nhập admin HTTP và đổi CSS. Phân biệt cấu hình đã viết, kiểm tra tĩnh và kiểm chứng runtime. Không ghi secret hoặc kết luận đã chịu tải khi chưa đo.

## 4. Phạm vi thay đổi dự kiến

| File | Nội dung |
| --- | --- |
| `deploy/docker-compose.keycloak.yml` | HTTP, mapping env, tài nguyên/pool/queue, network, mount theme |
| `deploy/docker-compose.postgresql.yml` | Loopback port, network, connection limit và bộ nhớ |
| `deploy/.env` | Biến/giá trị phù hợp môi trường; không đưa secret vào Git |
| File theme mới dưới `deploy/` | Theme properties và CSS login |
| `deploy/Note.md` | Cấu hình thực tế, lý do và hướng dẫn vận hành |

Đường dẫn theme cụ thể và việc cần thêm bootstrap helper còn OPEN, phụ thuộc kiểm trạng thái DB và duyệt phương án. Bản spec này chưa cho phép coi các file trên là đã sửa.

## 5. Lỗi và giới hạn cần xử lý

- Thiếu secret: dừng ở bước resolve Compose.
- DB chưa sẵn sàng hoặc sai credentials/grants: kiểm log/kết nối, không xóa volume để chữa lỗi.
- Đăng nhập HTTP bị chặn bởi realm SSL: kiểm cấu hình realm và đường truy cập trước khi thay chính sách; không tự hạ yêu cầu SSL.
- Vượt ngân sách RAM hoặc connection: giảm cấu hình theo tài nguyên đo được, không tăng pool tùy tiện.
- Request vượt queue limit: ghi rõ phản hồi 503 trong hướng dẫn và kết quả đo.
- Theme chưa được chọn hoặc CSS chưa cập nhật: kiểm mount, realm theme và cache.

## 6. Kiểm chứng và tiêu chí hoàn thành

1. Kiểm cú pháp/interpolation Compose mà không lộ secret trong output bàn giao.
2. Đối chiếu option với phiên bản image được pin.
3. Kiểm network, loopback port, volume, theme và ngân sách memory/connection.
4. Khi có môi trường runtime: kiểm khởi động, kết nối DB, đăng nhập admin từ laptop và tải CSS.
5. Khả năng chịu tải chỉ được xác nhận bằng run có tải mục tiêu, thông lượng, độ trễ, tỷ lệ lỗi và resource use; hiện các giá trị mục tiêu là OPEN.
6. Rà Note so với cấu hình cuối cùng và chạy governance audit.

## 7. Điểm chờ xác nhận — OPEN

- RAM 8 GB đã được người dùng xác nhận; loại instance/CPU và workload cùng máy chưa xác minh.
- Keycloak và PostgreSQL chạy cùng máy hay khác máy trong lần setup này?
- Trạng thái volume, database và runtime role hiện có.
- Ngưỡng tải cần phục vụ và tài nguyên dành riêng cho hai container.
- Đã có yêu cầu thực hiện inline; các giá trị tuning còn cần kiểm bằng inventory và tải thực tế trước nghiệm thu capacity.

## 8. Trạng thái thực hiện

Người dùng đã chọn sửa tại chỗ trên `dev/son` và xác nhận RAM AWS 8 GB. Hai Compose, env, helper khởi tạo DB mới và theme CSS được triển khai trong repo; kiểm smoke bằng dữ liệu giả local, không deploy lên AWS hoặc migrate volume cũ.

Giá trị khởi điểm: Keycloak 2 GiB, PostgreSQL 2 GiB; pool Keycloak min/initial/max 5/5/20; queue 100; PostgreSQL connections 100, shared_buffers 512MB, work_mem 4MB, maintenance_work_mem 128MB. Các giá trị có thể đổi bằng env và chưa là kết quả đo tải.

Khác biệt so với plan: giữ project/volume identity hiện có; network chung có tên `flash-ticket-infra`; không hard-depend Keycloak standalone vào service PostgreSQL ở file khác. Thêm helper fresh-volume chỉ tạo DB/role/schema Keycloak; startup wrapper escape dollar trong password theo image 26.8.0.

Khi tiếp tục Superpowers cho task này, cập nhật spec theo câu trả lời và kết quả duyệt. Kế hoạch thực hiện, nếu cần, là tài liệu riêng; `deploy/Note.md` chỉ ghi cấu hình đã thực sự làm.

## 9. Nguồn

- [AWS runtime baseline](../../architecture/aws-runtime-baseline.md).
- [B12: data ownership và schema](../../architecture/B12-data-ownership-and-schema.md).
- [Keycloak production](https://www.keycloak.org/server/configuration-production).
- [Keycloak container](https://www.keycloak.org/server/containers).
- [Keycloak themes](https://www.keycloak.org/ui-customization/themes).
- [Keycloak realm SSL](https://www.keycloak.org/docs/latest/server_admin/index.html#_ssl_modes).
