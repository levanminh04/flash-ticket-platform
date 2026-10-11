# Seed cases được giữ lại

Cập nhật 11/10/2026. 7 fixtures trong realm `flash-ticket` được giữ; migration không tạo/xóa user thật. Collection cloud **FlashTicket - Seed Scenarios** và env **FlashTicket - Seed Cases** vẫn ở workspace FlashTicket.

| Case | Username | ID | Trạng thái cuối |
|---|---|---|---|
| buyer1 | seed.20261010.b5be.buyer1@example.com | 7ca62cee-4bcb-4c60-974f-02ba09fabe28 | BUYER; enabled; email verified |
| buyer2 | seed.20261010.b5be.buyer2@example.com | 2ec29771-1f41-45c2-9810-349f1d404f1c | BUYER + ORGANIZER; enabled; email verified |
| organizer | seed.20261010.b5be.organizer@example.com | da0a647a-31c5-4585-b46c-2c80ffc8e711 | BUYER + ORGANIZER; enabled; email verified |
| disabled | seed.20261010.b5be.disabled@example.com | e3d31a3a-3174-44fe-ac2a-786a96c8c1fe | BUYER; disabled; email verified |
| unverified | seed.20261010.b5be.unverified@example.com | 60742ccf-5bd1-48e4-aa18-3ea02b04a342 | BUYER; enabled; email unverified |
| admin | seed.20261010.b5be.admin@example.com | 5d808ffe-abc5-41df-9cac-010ebf3441fc | ADMIN + BUYER; enabled; email verified |
| managed | seed.20261010.b5be.managed@example.com | 70bedfbb-7161-43f4-ae22-7ce196562e84 | BUYER; enabled; email verified |

Các user còn default roles và Account permissions như trước. Admin fixture có thêm realm-management `query-users`, `view-users`, `manage-users`. Mật khẩu fixture được giữ ở biến secret `seedPassword` trong env Seed Cases; không ghi vào tài liệu. Local credential/runner/seed export và báo cáo JSON đã được dọn sau kiểm chứng; khi cần chạy lại, dùng collection cloud.

## Chạy lại qua Postman

1. Chọn đúng env **FlashTicket - Seed Cases**.
2. Trong folder Login, từng case dùng Get New Access Token → Use Token → Send để lưu đúng biến token buyer1/buyer2/organizer/admin/unverified.
3. Folder machine token giữ user-management và internal-service. Điền Internal secret vào local value `internalClientSecret` từ dotenv local; shared value để rỗng.
4. Chạy các folder theo thứ tự. Token human hết hạn thì login lại. Case sai secret cuối folder cố ý trả 401 và xóa Internal token.
5. Chỉ chạy mutation trên fixture ID/prefix trong bảng. Create existing user trả 409 là expected. Không đổi sang env User/Admin/Internal khi chạy seed collection.

## Kết quả

Regression sau cutover: **50 requests, 75 assertions, 0 failures**, Postman CLI exit 0. Bao gồm self read/update tiếng Việt, validation email/password, anonymous/malformed token 401, buyer/organizer Admin REST 403, admin read/update/lifecycle/assign BUYER + ORGANIZER, machine token success và negative grant/secret.

Tạo thêm ORGANIZER không làm mất BUYER đã có. Disabled fixture vẫn disabled, unverified vẫn chưa xác minh email. 8 business templates, DELETE user và gửi email thật chưa chạy; không gọi skip là E2E pass. Kết quả chi tiết và giới hạn: [MIGRATION-RESULTS.md](MIGRATION-RESULTS.md). Bằng chứng trước migration như 59 requests/94 assertions là lịch sử; số 50/75 là bộ regression cuối sau gộp S2S.

Root guard hiện kiểm tra profile/prefix; không tự chứng minh mọi ID bạn chỉnh bằng tay thuộc seed. Trước khi rerun mutation, đối chiếu target IDs với bảng trên. Lượt kiểm chứng đã dùng đúng ID fixtures.
