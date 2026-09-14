# PostgreSQL baseline — B12-v0.2

`APPROVED` về baseline thiết kế (GOV-144) · `FORMATION` · PostgreSQL **16** là phiên bản ứng viên để kiểm cú pháp, không phải image pin triển khai. Nguồn: [B12](../../B12-data-ownership-and-schema.md) §2–4; [ma trận kiểm](../../B12-validation.md). AI không phê duyệt schema.

## Phạm vi và thứ tự

| Thứ tự | Tệp | Tác dụng |
|---|---|---|
| 1 | `00-bootstrap.sql` | Chỉ cluster thử mới: roles, năm database, schema; Keycloak tự quản bảng của nó |
| 2–5 | `10-event.sql`, `20-booking.sql`, `30-payment.sql`, `40-ticket.sql` | Mỗi owner một transaction DDL riêng, fail-fast |
| 6 | `90-grants.sql` | Cấp rõ SELECT/INSERT/UPDATE cho runtime; bảng mới mặc định không có quyền runtime |
| 7 | `95-design-rationale.sql` | COMMENT cho đủ 47 bảng: mục đích, lỗi tránh được, trade-off và giới hạn |
| Kiểm | `tests/constraints.sql`, `tests/catalog.sql` | Fixture tổng hợp trong transaction rồi rollback; kiểm constraints và catalog quyền |

Không chạy lại init trên database đang có. Nếu init lỗi giữa các database, không có rollback nguyên tử cho cả cluster; giữ log và bỏ **môi trường thử riêng đã xác minh quyền sở hữu**, rồi tạo môi trường mới. Không dùng `DROP`/`TRUNCATE`, `IF NOT EXISTS` hoặc tự sửa dữ liệu để che drift. Không có seed vào baseline.

`*_owner` là NOLOGIN; `*_migrator` chỉ có membership owner tương ứng, `NOINHERIT`, phải `SET ROLE`. Runtime không sở hữu object, không TRUNCATE/DDL/TEMP; DELETE chỉ trên sáu bảng con cấu hình Event và trigger chặn khi event không DRAFT, không vào database khác. Các domain hiện có dùng được cho INSERT/UPDATE; domain/function/bảng được thêm sau cần rà quyền riêng. Keycloak có quyền quản lý schema `keycloak` vì chính nó nâng schema; không được quyền database nghiệp vụ. `rca_observer` chỉ là probe chứng minh không vào database nghiệp vụ, **không** cấp thay credential kho quan sát.

## Chạy kiểm cục bộ

Từ PowerShell, gọi `./docs/architecture/data/postgres/Run-LocalTests.ps1`. Runner chỉ tạo container tên ngẫu nhiên có nhãn sở hữu, network `none`, không publish cổng và không mount thư mục host. Nó ghi PostgreSQL version/image ID thực, tạo baseline, kiểm và chỉ xóa container/anonymous volume thuộc chính run đó. Image `postgres:16` có thể thay đổi; có thể truyền `-Image` với tag 16.x và digest đã kiểm. **Không coi tag major là pin production.**

`trust` chỉ dùng trong container thử cô lập này để kiểm quyền CONNECT thực sự, không bị lỗi mật khẩu che mất. Không sao chép phương thức đó sang môi trường dùng thật. Runner không kết nối AWS hay nhận connection string bên ngoài. Docker không sẵn sàng thì dừng trước tạo tài nguyên và báo chưa chạy.

Nếu dùng PostgreSQL portable cục bộ thay Docker: tạo cluster **mới** trong thư mục tạm được đặt tên riêng; chỉ bind loopback/cổng chưa dùng. Dùng `psql -X -v ON_ERROR_STOP=1 -U <bootstrap>` thực hiện bảy tệp theo thứ tự, rồi `tests/constraints.sql`. Chạy `tests/catalog.sql` một lần trong mỗi database nghiệp vụ. Không đổi database name hard-coded để trỏ vào cluster có dữ liệu. Người vận hành ghi đường dẫn, server version, log, kết quả rồi dừng process đã tạo; không xóa đường dẫn tính toán chưa được kiểm. Credential vận hành thật lấy từ secret store; không truyền mật khẩu vào Git, command history hay log.

Có thể dùng `Run-PortableTests.ps1 -BinDir <thư-mục-bin-PostgreSQL-16>` để tự tạo cluster tạm, chạy cùng bộ kiểm, đăng nhập riêng từng principal và dừng đúng instance ở cuối. Không đăng ký Windows service, không đổi firewall, không xóa tệp thử. Runner portable còn kiểm INSERT fixture/UPDATE/SELECT bằng bốn tài khoản app, 37 kết nối trái quyền và `tests/runtime.sql` từ chối DELETE/TRUNCATE/DDL/SET ROLE. Phương thức trust loopback ở đây chỉ bỏ biến số password trong bài kiểm **authorization**, không chứng minh SCRAM/TLS/pg_hba triển khai thật. Không chạy đồng thời với tác vụ không tin cậy trên máy dùng chung.

## Điều đã bảo vệ và khoảng hở còn lại

- B7/B8 đã chốt: giá loại vé và total đơn >0; saleEnd không sau eventEnd; cửa sổ check-in **bao gồm hai đầu eventStart/eventEnd**; mỗi tài khoản dùng một promotion một lần, một mã/đơn; mã duy nhất trong event. Mã promotion lưu uppercase/trim; phần trăm là số nguyên. Lý do từ chối cancellation tùy chọn; partial unique giới hạn một PENDING/event, giữ lịch sử và cho gửi lại sau REJECTED theo BIZ-153–156.
- Booking có composite FK chống item khác event/mode/sector, cùng expiry order/reservation/usage; capacity không âm/vượt trần. Mỗi active promotion claim unique theo reservation và `(promotion,buyer)`. Lệnh trả cũ phải dùng reservation/generation/expected state. Không có implementation transaction giữ/chốt/trả; fixture conditional update chỉ minh họa điều kiện, không chứng minh service cạnh tranh.
- `charges` lưu khoản thu đã xác minh và liên kết hợp lệ; confirmation phải khớp cả order snapshot lẫn charge cùng order. Refund toàn bộ charge đến muộn/thu thừa có liên kết. PRJ-024 loại workflow tiếp nhận mismatch/orphan: callback sai bị từ chối và log an toàn theo B13/B16, không tự tạo bảng intake hay ép tiền khớp đơn. Fixture mismatch chỉ kiểm constraint từ chối confirmation, không là workflow ứng dụng.
- Event cancellation run chứa order, có thể chọn trước khi tạo refund. Chỉ charge đã được confirmation của chính order chọn mới liên kết vào run. Counter/mẫu số được service đối chiếu, không được CHECK nhiều hàng bảo vệ.
- Issuance unique order, ticket identity `(issuance,order_item,ordinal)`, audit tối đa một thành công/vé. Đủ số vé/từng dòng, input bất biến, owner, trạng thái và cửa sổ check-in vẫn cần transaction/service. Ticket fixture là bytes tổng hợp, **không kiểm thuật toán QR**.
- Fixture được chọn để kiểm DDL, không phải dữ liệu demo hợp lệ về toàn bộ vòng đời. Ví dụ fixture có audit thành công khi issuance còn PENDING để cho thấy SQL một mình không kiểm điều kiện phát hành; không đưa fixture này vào seed ứng dụng hoặc dùng nó làm mẫu check-in đúng nghiệp vụ.
- Các CHECK không kiểm immutability sau duyệt, chuyển trạng thái đơn điệu, độ mới snapshot, atomic all-or-nothing xuyên owner, paid write-once hoặc tổng nhiều hàng. Không tuyên bố callback, hoàn tiền, tải/restore, cạnh tranh hay QR khôi phục đã đạt.
- Đã có state order, VND nguyên, snapshot freeze, idempotency, payment_sagas và outbox/inbox cả bốn owner theo B13/B14. Tổng 47 bảng (12/15/13/7). Đây là CREATE mới, không migration tài khoản. Kiểm trên PostgreSQL 16.15 ngày 2026-09-14 ghi ở B12-validation; không thay nghiệm thu ứng dụng.

## Nguồn kỹ thuật

[PostgreSQL — schema và search_path](https://www.postgresql.org/docs/16/ddl-schemas.html), [CHECK/UNIQUE/FK](https://www.postgresql.org/docs/16/ddl-constraints.html), [default privileges theo owner](https://www.postgresql.org/docs/16/sql-alterdefaultprivileges.html). Đây là tham khảo cơ chế; không thay yêu cầu nghiệp vụ hoặc phê duyệt B12.
