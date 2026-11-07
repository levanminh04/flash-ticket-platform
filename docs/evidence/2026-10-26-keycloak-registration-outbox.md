# Nghiệm thu đồng bộ đăng ký Keycloak → RabbitMQ → MongoDB

Ngày: 2026-10-26 (Asia/Saigon). Người dùng yêu cầu triển khai trực tiếp trên Keycloak119, không dựng Docker local/stack test riêng. Các lỗi cần ngắt broker hoặc thay quyền DB shared không được fault-inject.

## Runtime và artifact

- Keycloak119: 26.8.0, container `ft119-infra-keycloak-1`, image `flash-ticket/keycloak:26.8.0-registration-outbox`.
- Provider: normal JAR, Java release21, native `OutboxStore`/`OutboxDrainerTask`, timer2s/batch1. Không tạo bảng mới.
- PostgreSQL: `keycloak_db.public.outbox_entry`; RabbitMQ119: `identity.ex` → `identity.user-created.v1` → `user.identity.q`.
- MongoDB119: `user_db.tbl_users`, standalone; unique identitySubject.
- Provider SHA256: `e47b1be5b6dfee875ab433647a128013c2661af0c2a4f79e2e205d8fcf09efd6`.
- AMQP client5.26.0 SHA256: `2e201b3f7fd26883c2845278aba1ecaaaf97be15d23d4cabf8e7e0b9a611b62e`.
- Backup trước triển khai: `/home/ubuntu/flash-ticket-platform/deploy/keycloak/backups/registration-outbox-20261026T145704Z`; DB dump, listener config và inspect private, quyền thư mục/file giới hạn. Không đưa nội dung secret vào Git.
- Override trên119: `/home/ubuntu/flash-ticket-platform/deploy/compose.registration-outbox.yml`; thêm sau các compose hiện hành khi recreate. Không reset DB/realm/volume. Listener giữ jboss-logging, thêm flash-ticket-registration-outbox.
- Consumer nghiệm thu chạy trên Windows kết nối Rabbit/Mongo119 qua tunnel hiện có; cổng HTTP13793, Eureka tắt cho instance nghiệm thu. Bản IDE8085 hiện hữu không bị dừng. Cấu hình KEYCLOAK_IDENTITY_REALM_ID đã thêm vào .env local/worktree; code triển khai nằm trên nhánh codex/keycloak-registration-outbox, chưa nhập dev/son.

## Kết quả quan sát thực tế

Đăng ký qua form Keycloak frontend public client bằng Authorization Code/PKCE. Chỉ gửi POST tới Keycloak; không đi theo redirect tới callback bên ngoài. Không dùng admin CREATE làm bằng chứng.

| Mốc | Giá trị đã kiểm |
|---|---|
| POST form | HTTP302 lúc 2026-10-26T15:03:31Z |
| Realm ID | 443bac7a-5a0e-48c5-aedb-a530dcb35eab |
| Subject tài khoản test | 33ee0c9b-9233-4663-b127-a673e157f0ae |
| Event ID | 95cb143c-fa56-4f18-b94b-e46c6f8f553d |
| Native outbox row ID | 75239f29-c023-4b4a-9734-e1154c2596a0 |
| Outbox state | DELIVERED, attempts1, last_error NULL |
| Mongo | count1; _id 6b6a2328-b331-4e15-b0e5-74d3cbbb2013; cùng identitySubject; version0 |
| Queue sau xử lý | user.identity.q: ready0, unacked0, consumers1; DLQ0 |
| Gửi lại event thực tế | Cùng eventId/payload, broker routed=true; consumer ghi nhận lần2; Mongo vẫn cùng _id/version0/count1 |

Tài khoản test được giữ làm bằng chứng. Mật khẩu ngẫu nhiên dùng trong bộ nhớ, không ghi log/Git. Không export body chứa thông tin tài khoản thật.

## Kiểm chứng tự động

- User baseline trước thay đổi:98tests pass.
- SPI:13tests pass qua `mvn -B -ntp clean verify`: fixture contract, lọc REGISTER/form/realm/error, mapping, đánh dấu rollbackOnly khi capture lỗi, publisher confirm/return/NACK/timeout/retry kết nối ban đầu.
- User:104tests pass qua `mvn -B -ntp spotless:apply verify`; SpotBugs0, formatter pass. Có test poison/schema/realm, Mongo retry3lượt, ACK sau write, ACK lỗi không NACK lại tag cũ.
- Opt-in `IdentityUserRegistrarLiveIT` trên Mongo119:1test pass.8lượt đồng thời tạo đúng1document, validator chấp nhận UUID/version/no _class; replay giữ displayName/version đã sửa. Chỉ xóa document random subject identity-it của test đó.
- Test RED trước implementation: thiếu DTO/listener/publisher/registrar; contract timestamp từng phát hiện serializer Keycloak xuất số, đã sửa JsonFormat STRING và GREEN. Không coi lỗi download/JDK là bằng chứng RED.
- Build User lần bổ sung timeout từng vướng Windows khóa JAR vì consumer nghiệm thu đang dùng; dừng đúng process tự tạo, rebuild verify thành công rồi chạy lại. Không dừng IDE8085.

## Giới hạn chưa kiểm bằng lỗi thật

NOT RUN trên shared119: ép outbox SQL lỗi để quan sát rollback đăng ký, ép rollback sau enqueue, broker outage/restart/crash giữa confirm và commit, Mongo outage, mất ACK thật, retention7ngày theo thời gian thực. Các nhánh tương ứng có kiểm unit/boundary nêu trên và đối chiếu source Keycloak26.8.0; không tuyên bố đã fault-test transaction/HA. Happy-path chứng minh cùng có user/outbox, không tự chứng minh mọi failure atomicity.

Không có tool subagent: đã self-review riêng trên toàn diff theo code-reviewer checklist. Cần review độc lập trước merge nếu nhóm yêu cầu.

## Quyết định khi thực hiện

1. Worktree riêng trên codex/keycloak-registration-outbox từ2917385; cần chọn cách nhập lại dev/son, chưa push/merge.
2. Dùng JDK23 cài sẵn với release21 cho plugin; Keycloak runtime Java21 kiểm chứng nạp/chạy JAR thực tế.
3. Theo chỉ đạo trực tiếp119, không dựng stack Docker local và không fault-inject shared infrastructure; giới hạn bằng chứng lỗi thật được công bố.
4. AMQP5.26.0 thay5.30.0:5.27+ kéo Netty4.2 trong khi Keycloak119 có4.1.138. Dùng client không kéo Netty để tránh shading/bundle thư viện trùng; không dùng tính năng client mới hơn.
5. Insert-only BSON qua collection lấy từ MongoTemplate, write concern w1/journaled; tránh Spring tự tăng @Version trên upsert replay. Trường BSON phải giữ đúng validator; đã kiểm thực tế.
6. Giữ bản IDE8085; cổng18085 bị Windows từ chối bind nên instance nghiệm thu dùng cổng13793 đã kiểm bind/health.

Minor deferred: image build chưa pin health-enabled như override119, nên lần recreate đầu Keycloak tự augmentation bổ sung health; chức năng hoạt động, tối ưu thời gian khởi động có thể làm sau. Không đổi packaging đang chạy chỉ để xử lý cảnh báo tối ưu.
