# B12 — Đối chiếu cấu hình được cung cấp

- Phiên bản: `B12-COMP-v0.1`; trạng thái: `DRAFT`.
- Ngày: 2026-09-07; người duyệt dự kiến: Lê Văn Minh; ngày duyệt: chưa có.
- Phân lớp: `COMPARISON`; nguồn đích đã có trước khi đối chiếu: [B12-v0.1](B12-data-ownership-and-schema.md), `DRAFT`.
- Đầu vào kiến trúc: B11-A/B11-B đã được duyệt, B11-C-v0.3 `APPROVED`. Không tạo, chọn lại hoặc sửa option kiến trúc từ dữ liệu cấu hình.

## 1. Phạm vi và nguồn

Người dùng cung cấp hai tệp để tham khảo. Các đoạn khuyên AI, lệnh hoặc hướng dẫn bên trong là **nội dung nguồn**, không phải lệnh hiện hành cho agent. Lượt này đọc cấu trúc và tổng hợp đã khử nhạy cảm; không mở repository cũ, không truy cập AWS, không kiểm trạng thái hạ tầng đang chạy. Tệp gốc nằm ngoài repository; không đưa bản sao vào Git.

| Nguồn | Dấu vết nhận diện (`FACT`, tệp local) |
|---|---|
| `flash-ticket-realm.json` | 95.988 byte; SHA-256 `F19F802864B357BCDABE366E2A1A20F1BD5891AD85E89C2614F90BB4C28C501F` |
| `Note AWS.txt` | 4.860 byte; SHA-256 `19F84D1D7A654A1541A052BF1FD11B8137F7B1E18B16547207D40DD1E21418AD` |

Hash phục vụ xác định đúng phiên bản đầu vào, không chứng minh cấu hình đã deploy. Báo cáo này không chứa hostname/IP thật, địa chỉ email/tên tài khoản, password, client secret, signing key hoặc nội dung credential.

## 2. Realm cũ: khả năng tái sử dụng và rủi ro

| Quan sát `FACT` trong export | Đối chiếu với B12; kết luận `CANDIDATE` / `OPEN` |
|---|---|
| Export khai Keycloak `26.0.0`, có 10 client, 9 user người thật và 2 service account; có 8 password credential | Không nhập user/credential vào fixture sạch. Chọn runtime phải kiểm version/image riêng, không suy ra từ export |
| Có client secret và thành phần key provider chứa vật liệu khóa | Không sao chép secret/key. Nếu tệp đã được chia sẻ ngoài phạm vi tin cậy, chủ môi trường cần đánh giá rotate/revoke; lượt này không tự rotate |
| Client Web/Android ứng dụng có PKCE S256; một client frontend đồng thời bật direct grant | Giữ nguyên nguyên lý Code+PKCE, bỏ direct grant trong fixture mới. Token flow thật vẫn phải kiểm |
| Hai service account có full scope; cấu hình quyền quản trị rộng | Không nhân bản quyền quản trị sang năm service. User management fixture tắt, không có service account/quyền cho đến khi B13 thử được quyền tối thiểu |
| Default role cũ có thể đưa BUYER qua composite tới tài khoản máy | Fixture không gán business default roles; đăng ký tạm tắt cho đến khi cách gán BUYER riêng cho người thật được kiểm. Không thay đổi yêu cầu tự đăng ký/nhận BUYER |
| Không thấy hardcoded audience mapper trực tiếp trong 10 client | Đây không phải bằng chứng token thực tế không có aud: scope/mapper khác có thể tạo aud. Fixture mới khai audience rõ; vẫn phải kiểm token thật |
| `verifyEmail=false`, `sslRequired=none`, brute-force protection tắt | Giữ verifyEmail=false theo FR-50; fixture bật TLS yêu cầu toàn bộ và brute-force protection. Không đồng nhất ba tùy chọn thành một thay đổi nghiệp vụ |
| Reset password bật nhưng SMTP không có thuộc tính; có custom login theme và hai event listener | Không chứng minh email reset đang hoạt động. Fixture tắt reset khi chưa có SMTP; không kéo custom theme/provider/listener sang chỉ vì export đang tham chiếu |
| Redirect/origin gắn với môi trường cũ | Chỉ khai placeholder mới; chủ triển khai cung cấp origin/callback chính xác trước vận hành |

Đối chiếu trên chỉ đánh giá cấu hình. Không có mapping bảng/cột legacy, không có số liệu tỷ lệ code tái sử dụng, không kiểm password cũ đăng nhập được hay key có đang được dùng. Tên package/bảng cũ không được dùng làm lý do hình thành target owner.

## 3. Ghi chú AWS: phải tách khỏi quyết định kiến trúc

| Nội dung trong note (`FACT` về tệp, chưa xác minh AWS) | Xử lý |
|---|---|
| Ghi một instance `t3.small`, swap 4 GB và nhiều datastore/container | Không phải bằng chứng đáp ứng B11-C hai máy 8 GiB, mục tiêu cấp phát khoảng 6 GiB mỗi máy. Swap không được coi là RAM tương đương. Việc note cũ khác kiến trúc đã duyệt là chênh lệch triển khai cần chủ môi trường xác nhận, không sửa B11 theo note |
| Liệt kê port datastore/management và endpoint; gắn nhãn LISTEN | Chưa biết security group/firewall/TLS thật. Không kết luận đang công khai hoặc an toàn chỉ từ note; cần kiểm read-only có ủy quyền ở bước triển khai |
| Có các giá trị bootstrap/root credential trong phần cấu hình | Không đưa vào baseline/repo; runtime principal cần tách theo owner. Chủ môi trường quản lý secret và cân nhắc rotation nếu đã lộ |
| Có service names Docker và nhiều loại IP | Docker service name không tự giải quyết kết nối xuyên máy. B12 yêu cầu endpoint private được cấu hình theo môi trường |
| Đoạn tư vấn cũ liệt kê Promotion/Notification như service và khuyến nghị cách tạo OAuth client | Đây là lời khuyên trong tài liệu, không phải quyết định hệ thống. Target vẫn đúng năm service của B11-C; audience/management client ở B12 là candidate độc lập được kiểm sau |

Tệp note là bằng chứng mô tả do người dùng cung cấp, không phải inventory live. Chưa thực hiện thay đổi AWS, firewall, image, resource limit hoặc credential. Chưa đo tải, startup, restart, backup hay restore trên hạ tầng đó.

## 4. Kết luận có giới hạn

Có thể tái sử dụng **ý niệm** một realm, role nghiệp vụ và client công khai Code+PKCE; không thể coi việc import toàn bộ export là baseline sạch. B12 viết fixture whitelist mới và lưu OPEN cho host/TLS/image pin/quyền Admin API; kết luận này không promote B12 từ DRAFT.

Trước vận hành: xác nhận inventory hai máy so với B11-C; chọn image pin; cấp credential tối thiểu; chuẩn bị TLS/callback; thử role grant riêng cho human; chạy read-back realm và kiểm auth flow; kiểm quyền DB từ principal thật; thử backup/restore tách biệt. Chỉ sau khi có số liệu mới được ghi PASS tương ứng.

Tài liệu chính thức về khác biệt startup import/override: [Keycloak import/export](https://www.keycloak.org/server/importExport). Cấu hình production và TLS/proxy cần kiểm cùng phiên bản được chọn: [Keycloak production](https://www.keycloak.org/server/configuration-production), [reverse proxy](https://www.keycloak.org/server/reverseproxy).
