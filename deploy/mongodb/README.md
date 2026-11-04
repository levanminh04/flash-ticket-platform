# MongoDB trên AWS #2

Placement theo B11-C §6, B12 §2 và `docs/architecture/aws-runtime-baseline.md` Stage 2.
Bộ deploy chỉ quản lý MongoDB; không deploy lại Booking, Payment, Keycloak hoặc observability.

## Kết quả vận hành ngày 01/11/2026

Đã chuyển `admin`, `config`, `local`, `user_db` từ AWS #1 sang AWS #2 bằng cold backup,
giữ MongoDB 8.0.28 và FCV 8.0. SHA-256 archive khớp khi truyền; manifest `user_db`
và `admin` khớp dữ liệu, user/role, validator/index trước khi User ghi vào đích.
`tbl_users` có 7 document; applications/profiles/follows đều 0 tại thời điểm chốt.
Runtime đọc được bốn collection và bị từ chối đọc `admin.system.users`.

Mongo mới `flash-ticket-mongodb-mongodb-1` healthy, User health `200 UP`, version
ứng dụng giữ `b6005e6`, consumer/recovery bật; queue User 1 consumer, 0 ready/unacked.
DLQ 1 message giữ nguyên. Config/Booking/Keycloak/PostgreSQL/Redis/Rabbit và năm
container observability giữ nguyên ID, đang chạy, không restart do migration.

Nguồn `ft119-user-user-db-1` và Mongo 8.3 cũ `flashticket-mongodb` trên đích dừng,
restart policy `no`; volume giữ nguyên. File cấu hình cũ thêm profile `legacy-mongodb`.
Backup/cấu hình khôi phục tại `~/.flash-ticket-mongodb-backup/20261101/` trên mỗi máy,
quyền thư mục 0700/file 0600, không nằm trong Git. Lần cắt chuyển cuối tạm dừng User
346 giây; trước đó có khôi phục tạm trong khi chờ xác nhận chuyển credential.

Evidence local: `services/user-service/target/mongo-migration-20261101/` gồm
`restore-target.json`, `cutover-user.json`, `aws1-postflight.json`, `aws2-postflight.json`
và `verified-summary.json`. Test deploy/tunnel Linux: 18 PASS; không chạy lại toàn bộ
luồng nghiệp vụ có ghi dữ liệu. Đây là Mongo placement + runtime smoke, không nghiệm thu toàn Stage 2/B15/B16.

## Cấu hình

- Compose: `docker-compose.mongodb.yml`; tham khảo phần MongoDB deploy trong [.env.example ở root](../../.env.example). File `mongodb.env` thực trên AWS và admin.env quyền0600 vẫn giữ riêng; không dùng nguyên env laptop để deploy.
- Image được pin digest và giữ đúng MongoDB 8.0.28 của nguồn; migration không nâng cấp server/FCV.
- Volume external phải được provision/import trước. Không chạy initializer/bootstrap lại trên dữ liệu được chuyển.
- Chỉ publish `127.0.0.1:27017`; User Service trên cùng host dùng endpoint này.
- Giới hạn Mongo 512 MiB, WiredTiger cache 0.25 GiB; đây là cấu hình integration, chưa chứng minh tải demo.
- File `MONGO_ADMIN_ENV_FILE` nằm ngoài Git, quyền 0600; User dùng tài khoản runtime riêng, không dùng root.
- Network được provision ngoài Compose; alias `mongodb` phục vụ các client Docker được cấu hình đúng quyền.

Đường dẫn vận hành:

```text
/home/ubuntu/flash-ticket-mongodb/deploy/docker-compose.mongodb.yml
/home/ubuntu/flash-ticket-mongodb/deploy/mongodb.env
/home/ubuntu/.config/flash-ticket-mongodb/admin.env
```

```bash
docker compose --project-name flash-ticket-mongodb \
  --env-file /home/ubuntu/flash-ticket-mongodb/deploy/mongodb.env \
  -f /home/ubuntu/flash-ticket-mongodb/deploy/docker-compose.mongodb.yml ps
```

## Quy trình chuyển và khôi phục

1. Kiểm kê nguồn/đích, client, dung lượng và phiên bản; backup cấu hình riêng quyền 0600.
2. Giữ khóa deploy User, dừng writer/consumer, chốt manifest nghiệp vụ và quyền; dừng Mongo nguồn sạch.
3. Cold backup toàn bộ `/data/db`, chuyển qua SSH đã pin host key, kiểm SHA-256 trước restore vào volume mới.
4. Khởi động đúng image nguồn với authorization; đối chiếu hash dữ liệu, users/roles, validator và index.
   `config`/`local` là metadata vận hành: không query collection session bị hạn chế quyền;
   toàn bộ file của chúng vẫn nằm trong cold backup đã kiểm checksum. Startup/session metadata có thể thay đổi khi khởi động.
5. Kiểm quyền runtime đọc được bốn collection và bị từ chối đọc admin; smoke User trên port tạm với consumer/recovery tắt.
6. Chuyển User về port 8085 và bật lại cờ ban đầu; ghi snapshot mới để các lần CI deploy sau giữ endpoint mới.
7. Giữ nguồn và Mongo cũ của đích ở trạng thái dừng, tắt auto-restart, bảo toàn volume/backup.
   Định nghĩa Compose cũ được đưa vào profile `legacy-mongodb` để tránh khởi động lại ngoài ý muốn.

Trước khi User ghi vào đích, có thể khôi phục cấu hình cũ và chạy lại nguồn.
Sau khi đích đã nhận ghi/ACK message, không bật lại bản nguồn cũ trực tiếp:
phải dừng writer và đồng bộ các thay đổi từ đích về nguồn trước, tránh mất dữ liệu hoặc split brain.
Không tự xóa source volume, volume Mongo 8.3 cũ trên đích, backup hay queue DLQ.

## Chạy User từ IDE

Mở tunnel từ laptop tới AWS #2, không còn tới AWS #1:

```bash
ssh -N -i <aws2-openssh-key> -o ExitOnForwardFailure=yes \
  -L 127.0.0.1:17017:127.0.0.1:27017 ubuntu@<aws2-ssh-host>
```

`.env` local có thể giữ URI `127.0.0.1:17017`; credential và `authSource` được giữ sau migration.
User chạy trên AWS #2 dùng `127.0.0.1:27017`. Tunnel systemd của User đặt
`USER_MONGO_TUNNEL_ENABLED=false` và chỉ giữ Config Server AWS #1 qua cổng 18888.
Config Server giữ nguyên YAML `${USER_MONGO_URI:${MONGO_URI}}`; không cần build lại Config.
