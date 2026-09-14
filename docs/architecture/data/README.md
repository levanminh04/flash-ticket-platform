# Bộ baseline dữ liệu B12

`APPROVED` (GOV-144) · baseline thiết kế · nguồn có thẩm quyền: [B12-v0.2](../B12-data-ownership-and-schema.md). Bộ này là tài sản thiết kế để thử cấu trúc và quyền, **chưa phải migration hoặc bản triển khai ứng dụng**. Phạm vi hoàn thiện 49 tệp được duyệt tại GOV-143; nội dung B12-v0.2 sau đó được duyệt tại GOV-144.

## 1. Đọc và chạy theo thứ tự

| Tạo tác | Vai trò |
|---|---|
| [Thiết kế B12](../B12-data-ownership-and-schema.md) | 47 bảng SQL/467 cột, 4 collection, owner và lý do/trade-off; §9 phân biệt quyết định đã chốt với cấu hình triển khai còn thiếu |
| [PostgreSQL](postgres/README.md) | Bootstrap riêng, DDL bốn database nghiệp vụ, database Keycloak, quyền và kiểm thử |
| [MongoDB](mongodb/README.md) | Bốn collection, validator/index, quyền cần provision và fixture test riêng |
| [Keycloak](keycloak/README.md) | Realm fixture viết mới, không credential/tài khoản cũ, các chức năng còn bị khóa |
| [Ma trận kiểm chứng](../B12-validation.md) | 11 INV, ca biên, phân biệt kết quả đã chạy và việc còn chờ service |
| [Đối chiếu cấu hình](../B12-legacy-configuration-comparison.md) | COMPARISON riêng; nguồn đã khử nhạy cảm, không sinh ranh giới đích |

Bốn ERD SQL và document map nằm ở `docs/diagrams/src/B12-01`–`B12-05`. Sơ đồ chỉ chọn các trường chính; SQL/validator chứa chi tiết constraint. Cả hai dẫn xuất từ B12, không thay nguồn nghiệp vụ B7/B8.

## 2. Kiểm tĩnh không cần Docker

Chạy từ repository root:

```powershell
node docs/architecture/data/test-static.cjs
node docs/architecture/data/mongodb/test-static.cjs
node docs/architecture/data/keycloak/test-static.cjs
& .agents/skills/govern-capstone-work/scripts/audit-governance.ps1
```

Script đầu kiểm tên owner/bảng/collection giữa nguồn, DDL và ERD; guard khởi tạo, tham chiếu local và các tạo tác phải có. Đây là kiểm văn bản, **không parse ngữ nghĩa SQL hoặc chứng minh constraint đang có hiệu lực**. Mongo/Keycloak có kiểm tĩnh riêng. Sổ kết quả thật đặt ở B12-validation, không kế thừa PASS sau khi sửa file.

## 3. Kiểm trên server thật

Docker chỉ là một cách tạo môi trường dùng một lần; PostgreSQL/MongoDB chạy cục bộ độc lập cũng có thể dùng nếu bảo đảm cách ly. Không cần sửa Docker Desktop, cài Windows service, đổi firewall hoặc thay cấu hình máy để duyệt tài liệu.

- Chỉ dùng instance/database thử nghiệm mới, địa chỉ loopback hoặc network container biệt lập; không trỏ AWS, tunnel hay database hiện có.
- Bootstrap PG tạo roles/database, nên cần tài khoản quản trị **của instance thử nghiệm**, không phải quyền quản trị database thật. Runtime sử dụng credential riêng; việc chạy DDL bằng superuser không chứng minh runtime bị hạn quyền.
- PG DDL giao dịch riêng từng owner; lỗi bootstrap có thể để lại role/database được tạo trước đó. Không tự chạy lại để “sửa” instance dở; tạo instance thử nghiệm mới và giữ lỗi làm bằng chứng.
- Mongo standalone không có transaction xuyên document; fixture test nằm riêng, không xóa hoặc chèn seed vào baseline. Quyền `user_app` mới là ma trận cần provision, chưa phải người dùng đã được tạo.
- Realm fixture chưa có URL thật, SMTP, cách gán BUYER riêng cho người thật hoặc quyền Admin API đã kiểm. Đăng ký và cấp organizer chưa vận hành; import/read-back JSON không thay kiểm PKCE/token/ownership.
- Ghi runtime version, archive hash hoặc image digest, thời điểm, câu lệnh tái lập và kết quả từng lớp. Bản chạy thử không tự pin phiên bản production.

Không cần gọi cổng thanh toán, AWS, SMTP hay Keycloak đang chạy để kiểm DDL. Các ca race, callback, hết hạn, bù trừ, QR restore và bản sao lỗi thuộc ma trận B12-S; chờ contract/service thật mới chạy và kết luận.

## 4. Phục hồi và ranh giới bảo mật

Không chứa mật khẩu, token, private key hoặc dữ liệu người dùng trong bộ này. Credential triển khai sinh ở môi trường chạy; quyền bootstrap/migrator/runtime/backup tách nhau. Các default privileges của SQL đóng quyền trên object mới: phải cấp bảng mới rõ ràng sau review, không tự mở CRUD toàn schema.

Một PostgreSQL instance vẫn là failure domain chung. Backup từng kho riêng không tạo snapshot nguyên tử xuyên toàn hệ thống: cần mốc dừng giao dịch được công bố, xử lý công việc còn chạy, sao lưu ngoài máy và kiểm restore ở môi trường biệt lập. Khôi phục QR cần cả ciphertext và đúng vật liệu khóa ở ngoài database; chỉ có hash QR không đủ tải lại.

RCA chỉ đọc kho quan sát, không được đọc trực tiếp database nghiệp vụ/Keycloak. Dữ liệu gửi ra dịch vụ ngoài phải được lọc theo NFR-09. Không đưa bản export realm gốc hoặc ghi chú AWS có thông tin nhạy cảm vào Git.

## 5. Điều kiện đi tiếp

Các lựa chọn nghiệp vụ Q-01–07 đã được xử lý tại sổ quyết định. B6/B7/B8 đã đồng bộ trong phạm vi GOV-143 và được Minh duyệt tại GOV-144. B12 đã có persistence Saga/outbox/inbox, hợp đồng [B13](../B13-api-and-event-contracts.md), sequence [B14](../B14-main-flow-sequences.md), [kế hoạch kiểm B15](../B15-verification-plan.md) và [quan sát B16](../B16-observability-baseline.md). Xem [readiness](../implementation-readiness.md) để duyệt đầu vào và bắt đầu code. Không ghi bộ này là đã triển khai AWS, đạt tải hoặc hoàn tất bất biến nghiệp vụ.
