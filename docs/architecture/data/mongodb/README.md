# MongoDB baseline B12

`APPROVED` về baseline thiết kế (GOV-144) · nguồn: [B12-v0.2 §4.5](../../B12-data-ownership-and-schema.md#45-user-service). Đây là schema thử nghiệm cho đúng bốn collection. Không di trú dữ liệu hoặc quản lý database đang tồn tại.

`schema.cjs` chỉ chứa dữ liệu validator/index và ma trận quyền ứng viên. `_id` là UUID chuỗi chữ thường; `identitySubject` giữ nguyên subject của issuer đã cấu hình, không lấy email làm khóa. Date là BSON date; version là BSON int/long. Adapter phải ghi đúng kiểu. Các giới hạn kiểu, field và biểu diễn này chưa là hợp đồng API đã duyệt.

| Collection | Nguồn ghi / khóa |
|---|---|
| users | `identitySubject` duy nhất; profile ứng dụng tối thiểu |
| organizer_applications | Một hồ sơ/subject; tên tổ chức, mô tả và quyết định là nguồn sự thật |
| organizer_profiles | Projection ACTIVE theo application/version; unique subject và application |
| user_follows | ID riêng, unique (buyerSubject, organizerSubject); unfollow xóa đúng cặp thuộc buyer |

`roleGrant` là trạng thái thao tác kỹ thuật nằm trong application, không phải trạng thái nghiệp vụ mới. Validator yêu cầu bằng chứng CONFIRMED khi ghi ACTIVE, nhưng không thể chứng minh Keycloak thật sự cấp role. Service phải kiểm phản hồi/đọc lại và cập nhật có điều kiện; timeout hoặc crash phải phục hồi theo B13/B14. Tương tự validator không ngăn REJECTED bị sửa thành PENDING, không kiểm references xuyên collection, không chứng minh projection mới nhất. Những kiểm này thuộc service, không tự thêm transaction xuyên document trên Mongo standalone.

Profile chỉ là projection; đọc public phải kiểm ACTIVE và không trả application nội bộ. Logo/banner tùy chọn không tạo API sửa profile độc lập. Trường ảnh tham chiếu và cách cập nhật theo hợp đồng B13; không lưu nội dung ảnh trong projection.

Chạy kiểm tĩnh từ repository root:

```powershell
node docs/architecture/data/mongodb/test-static.cjs
node docs/architecture/data/mongodb/test-preflight.cjs
node --check docs/architecture/data/mongodb/bootstrap.js
node --check docs/architecture/data/mongodb/test-live.js
```

Kiểm server cần MongoDB local đã được người chạy tạo riêng và ghi lại phiên bản/image. Không dùng AWS, tunnel hoặc Mongo đang có dữ liệu. `bootstrap.js` giới hạn endpoint loopback và tên database `b12_user_test_<suffix>`, từ chối khi có bất kỳ collection nào. Khởi tạo có thể dở dang nếu mất kết nối; dùng database thử nghiệm mới để chạy lại, không sửa tự động database cũ.

`test-preflight.cjs` dùng mock trong bộ nhớ để kiểm mười đường từ chối trước ghi, không kết nối MongoDB; PASS của nó không thay cho kiểm validator/index/quyền từ server thật.

```powershell
$env:B12_MONGO_SCHEMA_FILE = (Resolve-Path 'docs/architecture/data/mongodb/schema.cjs').Path
mongosh 'mongodb://127.0.0.1:27017/b12_user_test_20260907a' --file docs/architecture/data/mongodb/bootstrap.js
mongosh 'mongodb://127.0.0.1:27017/b12_user_test_20260907a' --file docs/architecture/data/mongodb/test-live.js
```

Fixture hoàn toàn tổng hợp và nằm lại trong database thử nghiệm; script không xóa dữ liệu. Trước ghi fixture, `test-live.js` kiểm loopback/tên test database, đúng bốn collection, không có dữ liệu, validator và index khớp nguồn; từ chối nếu chưa bootstrap hoặc schema bị lệch. Kiểm thêm việc chạy bootstrap lần hai phải bị từ chối. Chưa thực thi vào `user_db`; bước triển khai cần provisioning riêng sau duyệt.

`runtimePrivileges` mô tả quyền collection cần cấp cho `user_app`: find/insert/update trên users, applications, profiles; find/insert/remove trên follows để follow/unfollow đơn giản. Không cấp remove cho ba collection còn lại, bypassDocumentValidation, collMod, createIndex, root hoặc database khác. Provisioner/migrator/backup dùng danh tính riêng. Baseline không tạo password hoặc user; quyền thật cần thử bằng principal thật và kiểm quyền bị từ chối trước khi triển khai. Credential RCA không có quyền trên `user_db`.

MongoDB hỗ trợ JSON Schema draft 4 với các khác biệt; `additionalProperties:false` khai cả `_id`. Kiểm bằng server đã pin mới chứng minh các validator được chấp nhận. [MongoDB JSON Schema](https://www.mongodb.com/docs/manual/core/schema-validation/specify-json-schema/)
