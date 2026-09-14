# Keycloak baseline B12

`DRAFT` · `CANDIDATE` · nguồn: [B12-v0.1 §6](../../B12-data-ownership-and-schema.md#6-danh-tính-và-baseline-realm). Đây là fixture cấu hình được viết mới theo whitelist; không phải bản export đã sửa và chưa phải cấu hình identity vận hành hoàn chỉnh.

| Thành phần | Giới hạn của fixture |
|---|---|
| Realm `flash-ticket` | Chỉ định nghĩa BUYER, ORGANIZER, ADMIN; không gán business role mặc định |
| Web và Android | Public client, Authorization Code + PKCE S256; không implicit, direct grant hoặc service account |
| Audience `flash-ticket-api` | Audience chung ứng viên qua scope chỉ gắn cho human clients; không thay kiểm role và ownership |
| User management | Client vô hiệu hóa, service account tắt, không secret/quyền Admin API |
| Tài khoản, khóa, SMTP, theme/listener | Không nhập từ tài sản cũ; Keycloak tạo khóa mới ở môi trường test; không gán provider tùy biến |
| Đăng ký và reset password | Đang tắt trong fixture vì chưa có cơ chế gán BUYER riêng cho người thật/SMTP được kiểm |

`registrationAllowed=false` là **chặn vận hành fixture chưa đủ**, không phải bỏ FR-49/BIZ-132. Sản phẩm vẫn phải cho tự đăng ký và nhận BUYER; B13 phải chốt và thử cách gán role riêng cho người thật, không lây sang machine, rồi mới bật đăng ký. `verifyEmail=false` giữ nguyên FR-50; không dùng hardening để tự bắt người mua xác minh email. Reset password chưa được coi là hoạt động khi SMTP chưa được cung cấp. Không mô tả hai chức năng này là đã hoàn tất.

URL `*.example.invalid` là placeholder không dùng để đăng nhập thật. B13/triển khai phải cung cấp chính xác Web callback/origin và Android verified app link hoặc redirect scheme được duyệt; fixture chưa quyết định contract Android. Không thêm wildcard để làm đăng nhập chạy qua. `sslRequired=all` nghĩa endpoint TLS phải sẵn sàng; không hạ tùy chọn này cho môi trường public. Reverse proxy/hostname/image tag+digest phải được kiểm trước vận hành.

Chạy từ repository root:

```powershell
node docs/architecture/data/keycloak/test-static.cjs
node --check docs/architecture/data/keycloak/test-live.mjs
```

Kiểm tĩnh chỉ kiểm ý định cấu hình. Muốn kiểm server: chọn phiên bản Keycloak và ghi tag/digest; chuẩn bị Keycloak **local hoàn toàn mới** với TLS, database/volume riêng; dùng luồng startup import vào realm chưa tồn tại. Cung cấp fixture qua file import đặt tên theo quy ước Keycloak `flash-ticket-realm.json` ở môi trường thử nghiệm; không đổi tên hoặc sao chép bản export nhạy cảm gốc vào đó. Không chạy CLI import override hoặc sửa realm hiện có. Startup import bỏ qua realm đã tồn tại, nên log “started” không chứng minh fixture vừa được áp dụng. [Keycloak import/export](https://www.keycloak.org/server/importExport)

Sau import, quản trị viên môi trường thử tạo token ngắn hạn có quyền chỉ đọc cấu hình. Truyền token bằng môi trường an toàn; không dán token vào command/history/report. Với `B12_KC_DISPOSABLE_TEST=yes`, `B12_KC_TEST_BASE_URL` là origin loopback kèm port và `B12_KC_TEST_ADMIN_TOKEN` có sẵn:

```powershell
node docs/architecture/data/keycloak/test-live.mjs
```

Script chỉ GET và không in HTTP body/user/token; từ chối endpoint ngoài loopback hoặc redirect. Kiểm read-back này **không** chứng minh Code/PKCE thực sự từ chối verifier sai, JWT aud/issuer/signature/expiration, endpoint role/ownership, đăng ký gán BUYER, cấp ORGANIZER giữ BUYER hoặc phục hồi lỗi cấp quyền. Các ca đó cần browser/client và service thật ở B13/B15. Machine flow vẫn tắt và phải được kiểm từ chối quyền buyer trước khi được bật.

Với `fullScopeAllowed=false`, scope mapping xác định các role được phép xuất hiện; role mapper/audience mapper định hình access token. Không nhầm scope mapping với việc **cấp** role cho user. Gateway và từng resource server phải kiểm issuer, audience, chữ ký, hạn dùng và quyền nghiệp vụ. [Keycloak role scope/audience](https://www.keycloak.org/docs/latest/server_admin/#_audience)

Chưa chọn image pin cuối cùng. Export cũ khai `26.0.0` chỉ là bằng chứng xuất bản cũ, không phải quyết định chọn runtime hiện tại. Không claim đã import khi chỉ test-static PASS. Không chép secret/khóa cũ hoặc cấp realm-admin để bỏ qua B12-OPEN-07.
