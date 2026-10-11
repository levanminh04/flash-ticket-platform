# FlashTicket — User, Admin và Internal Service

Bản cuối ngày 11/10/2026 đã đồng bộ vào workspace **FlashTicket**. Collection chính: **FlashTicket - User Admin Services**. Import [collection JSON](FlashTicket.postman_collection.json) nếu cần dùng ở workspace khác.

## Chọn environment

| Folder | Environment | OAuth client |
|---|---|---|
| 01 - Client - User | FlashTicket - User | frontend, Authorization Code + PKCE |
| 02 - Admin - User Management | FlashTicket - Admin | frontend cho human admin; user-management cho admin service |
| 03 - Internal Service | FlashTicket - Internal | internal-service, client_credentials |

Import các file trong [environments](environments): User/Admin/Internal có bản example không secret và bản local trên máy. Bốn env Booking/Payment/Event/Ticket đã được bỏ. Các tên Booking/Payment trong template chỉ service nhận, không còn là OAuth client.

**Internal trên cloud để trống clientSecret.** Để chạy ngay trên máy này, import `environments/Internal.local.postman_environment.json` và chọn **FlashTicket - Internal**; nếu Postman hỏi trùng env, dùng bản local đó hoặc nhập giá trị vào local value của env hiện có. Secret tương ứng nằm ở key `INTERNAL_SERVICE_CLIENT_SECRET` trong `deploy/.env.dev` và `deploy/.env`. Không chia sẻ file local/đẩy secret lên shared value.

User/Admin chọn đúng environment ở góc phải trên. Các biến phân trang `first=0`, `max=20` thuộc Admin; `targetUserId`, role ID và dữ liệu edit phải trỏ tới user bạn muốn quản lý. Báo đỏ là biến chưa có giá trị trong env đang chọn.

## User và Admin login

1. Mở request Login OAuth2 trong folder tương ứng.
2. Authorization → **Get New Access Token** → đăng nhập → **Use Token**.
3. **Send** request Login/UserInfo. HTTP 200 mới chạy script lưu `accessToken`, `tokenExpiresAt` và thông tin tài khoản vào env đã chọn.
4. Chạy các API Bearer trong cùng folder. Hết hạn thì login và Send lại.

Dùng callback `https://oauth.pstmn.io/v1/callback`, PKCE S256 và `prompt=login`. Chỉ bấm Use Token chưa lưu vào env. Human admin phải thuộc realm **flash-ticket** và token có `realm-management` roles thích hợp, ví dụ `manage-users`; realm role `ADMIN` riêng lẻ chưa cấp quyền Keycloak Admin REST.

`01 - Get Admin Service Token` là lựa chọn dùng service account **user-management**, vẫn giữ cho tác vụ BE/Admin REST. Request này thay token hiện tại trong env Admin. Muốn dùng lại human admin thì login/Send lại. Internal token không cấp quyền Admin REST.

Lưu ý request có sẵn `06 - Assign BUYER Realm Role`: pre-request hiện gửi BUYER dù body bạn chỉnh ORGANIZER. Customization này được giữ từ trước migration; đừng dùng response thành công của request đó để kết luận đã gán ORGANIZER. Case seed Assign ORGANIZER riêng đã kiểm chứng; xem MIGRATION-RESULTS.

## User tự sửa hồ sơ

Login trong env User → `03 - Get My Account` → `04 - Update My Account`. Request GET lưu hồ sơ và các biến `self...Json`; body update giữ username/email/attributes khi chỉ sửa tên. Không đặt dấu ngoặc kép quanh các biến đã chứa JSON. Account API chỉ sửa tài khoản đang đăng nhập; không cần `manage-users`.

## Internal lấy token

Chọn env Internal → **01 - Get Internal Service Token** → **Send**. Body x-www-form-urlencoded có đủ `grant_type=client_credentials`, `client_id={{clientId}}`, `client_secret={{clientSecret}}`.

Token được lưu vào `accessToken`, expiry vào `tokenExpiresAt` của env Internal sau HTTP 200 và kiểm tra claims. Contract: issuer realm flash-ticket, audience duy nhất **flash-ticket-api**, realm role duy nhất **INTERNAL_SERVICE**, không client roles. Không kiểm tra `azp`. Kiểm tra payload trong Postman phục vụ chẩn đoán; Spring vẫn phải verify chữ ký.

Mỗi attempt hợp lệ xóa token Internal cũ trước khi gửi. Lỗi HTTP/claims không giữ token cũ. Chọn nhầm User/Admin thì request bị chặn, token User/Admin không bị sửa. Xem `lastRequestError`, Test Results và Postman Console khi request bị chặn cục bộ; trường hợp đó không có HTTP response/mã lỗi server. Token hết hạn cần xin lại.

## Template và giới hạn

8 template gọi business service mặc định bị chặn bởi `enableServiceTemplates=false`. Chỉ bật sau khi có endpoint thật và điền base URL/path/body/IDs đúng API. Local skip không chứng minh service trả 200/401/403. Không còn request introspect S2S.

Kết quả migration: [MIGRATION-RESULTS.md](MIGRATION-RESULTS.md). Dữ liệu seed vẫn giữ trên Keycloak/cloud: [SEED-README.md](SEED-README.md). Spring chưa sửa; đọc [INTERNAL-SERVICE-HANDOFF.md](INTERNAL-SERVICE-HANDOFF.md).

## Chạy Keycloak và rollback

Từ root repository, runtime hiện dùng `deploy/.env.dev`:

```powershell
rtk proxy docker compose --env-file deploy/.env.dev -f deploy/docker-compose.keycloak.yml up -d keycloak
```

Import dùng `${INTERNAL_SERVICE_CLIENT_SECRET}`; Compose phải truyền key này. Import startup bỏ qua realm đã tồn tại, không tự cập nhật DB runtime; không xóa PostgreSQL để ép import.

Backup có credential/mappings trước migration nằm ngoài repo tại `C:/Users/sondimid/AppData/Local/Temp/flash-ticket-internal-service-20261011T055317Z`. Giữ riêng, không chia sẻ. Trước cutover có thể restore đúng file/collection/env đã đổi. Sau cutover: recreate từng client theo clientId từ backup; resolve UUID mới, remap roles/scopes/service-account theo tên rồi restore đúng collection/env và xác minh token. Không restore cả DB/realm hoặc giả định UUID cũ còn dùng được. Backup mappings đã đọc kiểm tra; chưa chạy rollback rehearsal. Không chạy lại các script triển khai tạm: chúng đã được dọn sau review.
