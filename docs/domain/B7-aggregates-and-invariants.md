# B7 — Mô hình miền theo context, aggregate ứng viên và bất biến

- Phiên bản: `B7-v0.7`
- Trạng thái: `APPROVED`
- Phân lớp: `FORMATION`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-08-22, sau chuỗi `B2-v0.10` → `B5-v0.12`
- Đầu vào và phiên bản: `docs/glossary.md` — `B2-v0.10`, `APPROVED` ngày 2026-08-22; `docs/domain/B5-bounded-context-map.md` — `B5-v0.12`, `APPROVED` ngày 2026-08-22
- Nguồn truy vết hỗ trợ: `docs/domain/B3-business-processes.md` — `B3-v0.10`, `APPROVED` ngày 2026-08-22; `docs/domain/B4-domain-event-map.md` — `B4-v0.14`, `APPROVED` ngày 2026-08-22; và từng quyết định được dẫn trong `docs/project/decision-register.md`

> **Cổng phê duyệt:** bốn tạo tác thượng nguồn đã được Lê Văn Minh duyệt lại đúng thứ tự `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12` ngày 2026-08-22; B7-v0.7 được duyệt sau chúng.

B7-v0.7 dùng chuỗi đầu vào `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12`, kế thừa nội dung nghiệp vụ đã được duyệt ở `B2-v0.9 → B3-v0.9 → B4-v0.13 → B5-v0.11`. `E05` chỉ đặt tên cho quan hệ theo dõi organizer vốn đã có trong B2/B4/B5, không tạo aggregate, bất biến hay ranh giới context mới. Các quyết định ngày 2026-08-22 chỉ đóng nghĩa nghiệp vụ và sửa biểu diễn; aggregate vẫn là `CANDIDATE` chờ Lê Văn Minh duyệt toàn bộ B7.

`B7-v0.6` và `B7-v0.7` xử lý kết quả vòng kiểm toán ngày 2026-08-22. Vòng đó đọc lại toàn văn, mở cả tám nguồn sơ đồ và đối chiếu **nghĩa** của từng khái niệm với B2-v0.9 chứ không chỉ đối chiếu tên lớp. Phép kiểm từ vựng cũ chỉ so tên nên không bắt được các chỗ trùng tên lệch nghĩa, sai bội số và thiếu đối tượng nghiệp vụ; §7 và §8 nay tách hai phép kiểm này.

## 1. Mục tiêu và ranh giới của B7

B7 kiểm tra tám bounded context ứng viên của B5 bằng hai câu hỏi:

1. Những đối tượng nghiệp vụ nào cần thay đổi cùng nhau để bảo vệ một bất biến?
2. Phần thay đổi bên trong mỗi aggregate ứng viên có thể hoàn tất trong một giao dịch cục bộ hay không?

Kết quả tại B7 chỉ là **mô hình miền và ranh giới nhất quán ứng viên**. Aggregate không đồng nghĩa với service, schema, bảng hay một cơ sở dữ liệu. Những lựa chọn vật lý đó chỉ được xem xét tại B11–B13.

Các trạng thái bằng chứng trong tài liệu này được dùng như sau:

| Nội dung | Trạng thái |
|---|---|
| Quy tắc nghiệp vụ đã được Lê Văn Minh xác nhận và các `INV-01`–`INV-11` trong B4-v0.13 | Giữ nguyên trạng thái của từng nguồn; không diễn giải phê duyệt nghiệp vụ thành quyết định kiến trúc |
| Tên aggregate root, thành phần nằm trong aggregate và ranh giới giao dịch | `CANDIDATE` |
| Điểm chưa đủ dữ kiện hoặc còn chờ gate sau | `OPEN` |

Không có mã context hay mã aggregate nào được dùng làm tên nghiệp vụ trong biểu đồ. Mã chỉ phục vụ truy vết trong bảng; nhãn chính luôn là tên tiếng Việt đã có ở B2-v0.9.

## 2. Cách thử ranh giới aggregate

Một aggregate ứng viên được coi là qua phép thử B7 khi:

- aggregate root là lối duy nhất làm thay đổi trạng thái bên trong ranh giới đó;
- bất biến được gán cho aggregate có thể được kiểm tra và cập nhật trong một giao dịch cục bộ;
- đối tượng tăng trưởng không giới hạn không bị đưa vào aggregate chỉ để tạo cảm giác “mọi thứ cùng một giao dịch”;
- thay đổi vượt ra ngoài ranh giới được nêu rõ, không bị che giấu dưới tên “giao dịch cục bộ”.

Một nghiệp vụ có thể đi qua nhiều aggregate. Điều đó không tự động chứng minh rằng các aggregate phải được nhập thành một cụm lớn. B7 tách rõ:

- **bất biến cục bộ:** root tự bảo vệ được;
- **bất biến xuyên aggregate:** từng root vẫn bảo vệ phần trạng thái của mình, còn cách phối hợp và phục hồi được đưa sang B9–B11;
- **thiếu dữ kiện:** giữ `OPEN`, không tự đặt thêm thuật ngữ hoặc tự sửa B2–B5.

Nếu một aggregate riêng lẻ không qua phép thử giao dịch, B7 phải sửa ranh giới aggregate trước. Chỉ khi có bằng chứng cho thấy ngôn ngữ hoặc trách nhiệm của context trong B5 là nguyên nhân thì mới leo thang như một nghi vấn lỗi thượng nguồn. Vòng soạn này phát hiện **một** lỗi thượng nguồn và đã leo thang đúng cách: `Yêu cầu hủy sự kiện` có vòng đời riêng ở B3/B4 nhưng thiếu mục từ ở B2-v0.9. Lê Văn Minh chọn bổ sung mục từ; `B2-v0.10` khắc phục, và B7 mới đặt được ranh giới cho nó tại §4.1. B7 không tự thêm thuật ngữ trước khi B2 được sửa.

## 3. Tổng quan aggregate root ứng viên

| Bounded context ứng viên | Aggregate root ứng viên | Phạm vi nhất quán chính | Kết quả thử giao dịch |
|---|---|---|---|
| Vòng đời sự kiện và cấu hình bán | `Sự kiện bán vé` | Vòng đời, cửa sổ mở bán, chế độ bán và tỷ lệ phí đã duyệt của một lần tổ chức | Đạt trong một sự kiện; đổi chế độ bán còn cần phối hợp xóa cấu hình không tương thích khi ở `DRAFT` |
| Vòng đời sự kiện và cấu hình bán | `Yêu cầu hủy sự kiện` | Vòng đời của một yêu cầu xin hủy: đã gửi, được xác nhận hoặc bị từ chối, kèm lý do từ chối khi có | Đạt cho chuyển trạng thái cục bộ; việc đưa sự kiện sang `CANCELLED` và lan hậu quả thuộc aggregate khác và `HOT-01` |
| Vòng đời sự kiện và cấu hình bán | `Địa điểm` | Thông tin địa điểm dùng lại giữa các sự kiện | Đạt; hệ thống không biết/kiểm sức chứa vật lý và không gắn nó thành bất biến nguồn cung (`BIZ-149`) |
| Vòng đời sự kiện và cấu hình bán | `Phân loại sự kiện` | Danh mục tối giản dùng để phân loại | Đạt |
| Vòng đời sự kiện và cấu hình bán | `Sector` | Cấu trúc một khu và tập ghế của khu đó | Đạt ở mức nghiệp vụ; kích thước sơ đồ ghế cần B10 đánh giá |
| Vòng đời sự kiện và cấu hình bán | `Loại vé` | Giá và cấu hình một loại vé của sự kiện | Đạt |
| Vòng đời sự kiện và cấu hình bán | `Khuyến mãi` | Quy tắc, mã và thời gian hiệu lực của một khuyến mãi | Đạt; lượt dùng thực tế không nằm trong aggregate cấu hình này |
| Mua vé và cam kết nguồn cung | `Đơn hàng` | Dòng đơn, một giữ chỗ chung và số tiền phải thanh toán | Đạt cho cấu trúc đơn; việc giữ nhiều nguồn cung đi qua các aggregate khác |
| Mua vé và cam kết nguồn cung | `Ghế`, `Loại vé`, `Sector` theo nghĩa cam kết nguồn cung | Ghế định danh, nguồn cung `QUANTITY` và nguồn cung đứng được giữ/chốt/trả độc lập | Đạt cho từng đơn vị nguồn cung; `Khả dụng` chỉ là kết quả xét, không phải root |
| Mua vé và cam kết nguồn cung | `Giới hạn mua` | Phần hạn mức đang giữ và đã mua của một tài khoản trong một sự kiện | Đạt với khóa nghiệp vụ `(tài khoản, sự kiện)` |
| Mua vé và cam kết nguồn cung | `Khuyến mãi` theo nghĩa sử dụng | Tổng lượt đang giữ và đã chốt của một khuyến mãi | Đạt cục bộ cho trần tổng lượt; đây là mô hình khác với cấu hình khuyến mãi ở context sự kiện |
| Mua vé và cam kết nguồn cung | `Lượt dùng khuyến mãi` | Quyền dùng của một tài khoản đối với một khuyến mãi | Đạt với khóa nghiệp vụ `(khuyến mãi, tài khoản)` |
| Thanh toán và hoàn tiền | `Xác nhận thanh toán` | Lần thanh toán đang xử lý và đúng một kết quả thu hợp lệ của một đơn | Đạt với khóa nghiệp vụ là đơn hàng; lịch sử lần thử không nằm trong tập con tăng vô hạn của aggregate |
| Thanh toán và hoàn tiền | `Yêu cầu hoàn tiền` | Một yêu cầu logic và kết quả thực hiện hiện tại cho một khoản thu | Đạt với khóa nghiệp vụ là khoản thu được hoàn; thử lại cập nhật cùng yêu cầu/kết quả logic |
| Quyền tham dự và kiểm soát vào cửa | `Phát hành vé` | Tập vé được phép sinh từ một đơn đã thu tiền | Đạt về giao dịch cục bộ với khóa nghiệp vụ là đơn hàng; ranh giới vẫn là `CANDIDATE` vì các vé khác nhau trong cùng đơn có thể tranh chấp trên cùng root khi check-in |
| Đối soát và chi trả | `Chi trả` | Trạng thái đối soát và dấu `Đã chi trả` duy nhất của một sự kiện; bản thân việc chuyển tiền diễn ra ngoài hệ thống | Đạt với khóa nghiệp vụ là sự kiện |
| Giao nhận thông tin vé | `Gửi vé` | Tiến trình gửi/gửi lại thông tin của cùng tập vé đã phát hành | Đạt; không sở hữu hiệu lực của vé |
| Hồ sơ tài khoản và quyền nghiệp vụ | `Hồ sơ nghiệp vụ ứng dụng` | Hồ sơ nghiệp vụ tối thiểu tách khỏi danh tính | Đạt với tên tổ chức và mô tả ngắn; kiểu dữ liệu/schema chờ B12/B13 |
| Hồ sơ tài khoản và quyền nghiệp vụ | `Đăng ký organizer` | Vòng đời `PENDING`–`ACTIVE`–`REJECTED` của một hồ sơ đăng ký, kèm lý do từ chối khi có | Đạt cho chuyển trạng thái cục bộ; điều kiện `ACTIVE` phụ thuộc xác nhận cấp role từ nguồn danh tính theo `BIZ-140`, nên cửa sổ lỗi giữa hai phía vẫn là `OPEN` cho B9–B11/B13 |
| Hồ sơ tài khoản và quyền nghiệp vụ | `Theo dõi organizer` | Quan hệ theo dõi giữa một buyer và một organizer | Đạt ở mức quan hệ tối giản của `E05`; không tự thêm điều kiện hồ sơ organizer phải `ACTIVE`, cách lưu/tính tổng người theo dõi chờ B12/B13 |
| Chẩn đoán sự cố | `Sự cố` | Cụm context chẩn đoán, nguyên nhân khả dĩ và bước kiểm tra của một sự cố | `CANDIDATE` tạm thời; workflow vẫn `OPEN` tại B16–B19 |

## 4. Mô hình theo từng bounded context

### 4.1. Vòng đời sự kiện và cấu hình bán

Nguồn biểu đồ: `docs/diagrams/src/B7-01-event-and-sales-class-model.puml`.

Bội số trong hình bám đúng vòng đời đã duyệt: `A01` tạo bản nháp trước rồi `A02` mới cấu hình, nên quan hệ sự kiện–loại vé là `0..*` chứ không phải `1..*`; và vì B4 §8.4 ghi rõ “quy tắc gắn nhiều phân loại” chưa được suy ra, quan hệ sự kiện–phân loại là `0..1`, không phải bắt buộc đúng một.

#### Aggregate `Sự kiện bán vé`

- **Bên trong ranh giới:** `Trạng thái sự kiện`, `Cửa sổ mở bán`, `Chế độ bán`.
- **Bất biến phải giữ cục bộ:**
  - một sự kiện là đúng một lần tổ chức tại một thời gian và địa điểm, thuộc đúng một organizer (`BIZ-021`, `BIZ-135`);
  - vòng đời bền vững chỉ dùng `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `PUBLISHED`, `CANCELLED`; bị từ chối duyệt thì trở về `DRAFT` (`BIZ-143`–`BIZ-145`);
  - `saleStartAt < saleEndAt <= eventEndAt` (`BIZ-006`);
  - chỉ bán khi đã duyệt, đã công bố, chưa hủy và đang trong cửa sổ mở bán (`BIZ-004`, `BIZ-007`);
  - dùng đúng một chế độ `QUANTITY` hoặc `SEAT_MAP`; chỉ đổi khi `DRAFT`, không chuyển đổi cấu hình âm thầm (`BIZ-016`, `BIZ-018`–`BIZ-020`);
  - tỷ lệ phí nền tảng đã được admin nhập khi duyệt không được sửa âm thầm sau phê duyệt (`INV-11`, `BIZ-034`, `BIZ-142`).
- **Thay đổi vượt aggregate:** sector, ghế, loại vé và khuyến mãi được cấu hình qua các aggregate riêng. Khi đổi chế độ bán, sự kiện đang ở `DRAFT` nên chưa thể bán; quá trình xóa cấu hình không tương thích có thể được phối hợp và phục hồi mà không làm phát sinh cam kết bán mới.

#### Aggregate `Yêu cầu hủy sự kiện`

Vòng kiểm toán ngày 2026-08-22 phát hiện `B7-v0.5` bỏ sót đối tượng này. Nó có vòng đời riêng ở B3/B4 và không thể gộp vào `Sự kiện bán vé`, vì `BIZ-098` cho **bản thân yêu cầu** một trạng thái `REJECTED` trong khi trạng thái sự kiện giữ nguyên. `B2-v0.10` bổ sung mục từ tương ứng.

- **Bên trong ranh giới:** sự kiện được xin hủy, người gửi, trạng thái xử lý của yêu cầu và lý do từ chối khi có.
- **Bất biến phải giữ cục bộ:**
  - chỉ phát sinh khi sự kiện đã có đơn thu tiền; sự kiện chưa có đơn thu tiền được organizer hủy trực tiếp, không qua yêu cầu (`BIZ-008`, `BIZ-009`);
  - khi admin từ chối, yêu cầu chuyển `REJECTED`, lý do là **tùy chọn**, trạng thái sự kiện không đổi và không luồng hoàn tiền nào được khởi động (`BIZ-097`–`BIZ-099`);
  - hệ thống chỉ chấp nhận hủy khi `now < eventStartAt`, nên một yêu cầu được xác nhận sau mốc đó không dẫn tới hủy (`BIZ-083`).
- **Thay đổi vượt aggregate:** việc chuyển sự kiện sang `CANCELLED` thuộc `Sự kiện bán vé`; hậu quả lan tới đơn, vé, hoàn tiền và đối soát là `HOT-01`, chờ B9–B11.
- **`OPEN`:** organizer có được gửi lại yêu cầu sau khi bị từ chối hay không, và một sự kiện có được có nhiều yêu cầu theo thời gian hay không — `BIZ-097`–`BIZ-099` không chốt. Hình dùng bội số `0..*` để **không** tiền-chốt theo hướng nào. `B6-v0.12` và `B8-v0.8` đã được duyệt trong khi điểm này còn mở, nghĩa là hai tài liệu cố ý im lặng về việc gửi lại. Muốn chốt “có” thì cần một quyết định nghiệp vụ riêng của Lê Văn Minh và mở lại B6/B8; owner Lê Văn Minh, không gắn vào gate kỹ thuật nào.

#### Các aggregate cấu hình còn lại

| Root | Thành phần/quan hệ chính | Bất biến cục bộ |
|---|---|---|
| `Địa điểm` | Được nhiều `Sự kiện bán vé` tham chiếu | Một địa điểm có thể được dùng lại cho nhiều sự kiện (`BIZ-125`). Hệ thống không biết và không kiểm sức chứa vật lý; organizer tự quyết định nguồn cung (`BIZ-149`). |
| `Phân loại sự kiện` | Được nhiều `Sự kiện bán vé` tham chiếu | Giữ năng lực phân loại ở mức tối giản (`BIZ-126`). |
| `Sector` | Chứa tập `Ghế`; được `Loại vé` tham chiếu trong `SEAT_MAP` | Sector chỉ có hành vi `SEATED` hoặc `STANDING`; không có hành vi `VIP_BOX`; một sector có thể có nhiều loại vé (`BIZ-002`, `BIZ-003`, `BIZ-017`). |
| `Loại vé` | Thuộc một sự kiện; có thể gắn một sector trong `SEAT_MAP` | Giá phải lớn hơn 0 (`BIZ-015`). |
| `Khuyến mãi` | Thuộc đúng một sự kiện | Mã duy nhất trong một sự kiện; chỉ sửa khi sự kiện `DRAFT`; cấu hình khóa từ lúc gửi duyệt; hiệu lực tự động theo thời gian (`BIZ-039`–`BIZ-042`, `BIZ-069`, `BIZ-081`, `BIZ-092`–`BIZ-095`). Quy tắc một mã cho mỗi đơn, không cộng dồn và số tiền cuối lớn hơn 0 (`BIZ-044`–`BIZ-046`) là bất biến của `Đơn hàng`, không của aggregate cấu hình này. |

`Tìm sự kiện` là khả năng đọc các sự kiện đã công bố (`BIZ-127`), không phải aggregate ghi riêng.

### 4.2. Mua vé và cam kết nguồn cung

Nguồn biểu đồ: `docs/diagrams/src/B7-02-order-and-availability-class-model.puml`.

#### Aggregate `Đơn hàng`

- **Bên trong ranh giới:** `Dòng đơn hàng`, đúng một `Giữ chỗ` và `Số tiền phải thanh toán`. B2 định nghĩa `Giá sau giảm` và `Số tiền phải thanh toán` ra **cùng một số tiền** của đơn, chỉ khác góc nhìn: `Giá sau giảm` là giá trị sau tối đa một mã, còn `Số tiền phải thanh toán` là nghĩa vụ phải trả. Hình vì vậy để `Giá sau giảm` xác định giá trị cho `Số tiền phải thanh toán` thay vì lồng hai value object chứa cùng một con số.
- **Bất biến phải giữ cục bộ:**
  - đơn thuộc đúng một sự kiện; với `SEAT_MAP`, mọi dòng thuộc cùng một sector (`INV-02`, `BIZ-001`, `BIZ-080`);
  - đơn có đúng một giữ chỗ và một thời điểm hết hạn chung (`INV-03`, `BIZ-029`);
  - mỗi đơn dùng tối đa một khuyến mãi và số tiền sau giảm phải lớn hơn 0 (`BIZ-044`–`BIZ-047`);
  - buyer chỉ hủy đơn của mình khi còn giữ chỗ và chưa có thanh toán thành công; hủy lặp không tạo tác dụng phụ (`BIZ-071`, `BIZ-090`, `BIZ-091`);
  - hoàn khoản thu trùng/đến muộn không tạo trạng thái nghiệp vụ riêng cho đơn và không làm đơn/vé hợp lệ mất hiệu lực (`BIZ-150`); tên/chuyển trạng thái cụ thể chờ B12/B13 theo `BIZ-130`.

#### Các aggregate nguồn cung `Ghế`, `Loại vé` và `Sector`

`Khả dụng` trong B2 là **kết quả xét**, không có danh tính riêng và không được dùng làm aggregate root. Trạng thái nhận cam kết nằm ở ba root theo loại nguồn cung:

- `Ghế` bảo vệ độc quyền của một ghế định danh trong sector `SEATED`;
- `Loại vé` bảo vệ số lượng còn lại khi sự kiện bán theo `QUANTITY`;
- `Sector` bảo vệ số lượng còn lại của sector `STANDING` trong `SEAT_MAP`.

Theo đúng B2, `Khả dụng` được xét trên **bốn** nhóm đầu vào: trạng thái bán, tồn kho, giữ chỗ và giới hạn mua. Ba root trên chỉ cung cấp nhóm tồn kho; `Giữ chỗ` và `Giới hạn mua` cũng tham gia phép xét, còn điều kiện mở bán đến từ context vòng đời sự kiện. Hình `B7-02` vẽ đủ các đầu vào nằm trong context này và ghi chú phần đến từ context khác, để `Khả dụng` không bị thu hẹp thành đồng nghĩa của `Tồn kho vé còn lại` — hai mục từ mà B2 phân biệt dứt khoát.

Đây là mô hình theo ngôn ngữ “có thể bán/đã cam kết” của context mua vé, khác với cấu hình cùng tên trong context sự kiện. `INV-01` được bảo vệ cục bộ ở từng root, giữ nguyên câu chữ B4-v0.13: ghế không nằm trong hai giữ chỗ/đơn còn hiệu lực và **số lượng khả dụng không được âm**. Giữ, chốt mua hoặc trả lại cùng một cam kết phải hội tụ về một kết quả; callback/hủy/hết hạn lặp không được trừ hoặc cộng nguồn cung lần hai (`INV-06`, `BIZ-062`, `BIZ-065`, `BIZ-066`, `BIZ-085`, `BIZ-086`, `BIZ-090`).

#### Aggregate `Giới hạn mua`

- Khóa nghiệp vụ là một tài khoản trong một sự kiện.
- Số vé đang giữ cộng số vé đã mua không vượt mức organizer cấu hình (`INV-04`, `BIZ-025`–`BIZ-028`, `BIZ-075`).
- Hết hạn, hủy trước thanh toán hoặc lỗi phát hành hợp lệ chỉ trả phần đã chiếm đúng một lần (`INV-06`, `BIZ-064`, `BIZ-085`, `BIZ-090`).

#### Aggregate `Khuyến mãi` theo nghĩa sử dụng và `Lượt dùng khuyến mãi`

- `Khuyến mãi` trong context này chỉ bảo vệ trần tổng lượt đang giữ/đã chốt. Cấu hình mã, cách giảm và thời gian hiệu lực vẫn thuộc mô hình khuyến mãi ở context sự kiện.
- Mỗi `Lượt dùng khuyến mãi` đại diện quyền dùng của đúng một tài khoản với đúng một mã; root này bảo vệ quy tắc một tài khoản dùng một lần và chuyển trạng thái giữ/chốt/trả (`BIZ-042`, `BIZ-043`, `BIZ-047`, `BIZ-048`, `BIZ-070`).
- Một đơn chạm tối đa một cặp `Khuyến mãi`–`Lượt dùng khuyến mãi` vì không được cộng dồn nhiều mã.
- Việc giữ lượt cần cập nhật cả trần tổng và quyền của tài khoản. Đây là thay đổi xuyên hai aggregate, cần B9–B11 chỉ ra cách chống vượt trần và chống dùng lặp khi cạnh tranh.

#### Kết luận cho `HOT-02` / `B5-OPEN-02`

B7 **thu hẹp** điểm mở bằng các vai trò ranh giới: `Đơn hàng`; root nguồn cung là `Ghế`/`Loại vé`/`Sector`; `Giới hạn mua`; `Khuyến mãi` theo nghĩa tổng lượt; và `Lượt dùng khuyến mãi` theo tài khoản. Không nhập toàn bộ vào một aggregate khổng lồ vì một đơn có thể giữ nhiều ghế/dòng nguồn cung, còn hạn mức và lượt khuyến mãi được dùng chung giữa nhiều đơn.

Việc tạo đơn vẫn là một thay đổi xuyên nhiều aggregate. Các bất biến cứng không cho phép bán vượt nguồn cung, vượt hạn mức hoặc vượt lượt khuyến mãi, nên B9–B11 phải chỉ ra cách phối hợp, thất bại và thử lại. B7 không chọn giao thức, Saga, service hay cơ sở dữ liệu cho việc đó.

### 4.3. Thanh toán và hoàn tiền

Nguồn biểu đồ: `docs/diagrams/src/B7-03-payment-and-refund-class-model.puml`.

#### Aggregate `Xác nhận thanh toán`

- **Bên trong ranh giới:** tối đa một `Lần thanh toán` đang xử lý và kết quả thu hợp lệ của một đơn. Lần thử kết thúc có thể được thay thế bởi lần thử tiếp theo theo thứ tự; lịch sử vận hành không nằm trong tập con tăng không giới hạn của aggregate. `Callback/IPN` là đầu vào có thể lặp tác động lên root.
- Chỉ một kết quả thu tiền hợp lệ được giữ cho đơn; thu thừa được nhận diện riêng và đưa sang hoàn tiền (`INV-05`, `BIZ-031`, `BIZ-032`).
- Callback lặp phải hội tụ về cùng kết quả. Khoản thu đến sau khi đơn không còn chấp nhận thanh toán được coi là `Thanh toán đến muộn` và không làm hồi sinh đơn (`BIZ-086`, `BIZ-091`, `BIZ-106`).

#### Aggregate `Yêu cầu hoàn tiền`

- Khóa nghiệp vụ là khoản thu cần hoàn; cùng một khoản thu chỉ có một yêu cầu logic và một kết quả hoàn thành (`INV-08`, `BIZ-101`).
- Yêu cầu hoàn phát sinh từ **bốn** nguyên nhân đã được B4-v0.13 `C01` chốt: thanh toán đến muộn (`B10`), thanh toán trùng (`B11`), phát hành vé thất bại sau thu tiền (`B12`) và hủy sự kiện (`C05`). Hình `B7-03` chỉ vẽ được hai nguyên nhân phát sinh trong context này; hai nguyên nhân còn lại đến từ context khác và được ghi bằng lời trong hình, không bị loại khỏi mô hình.
- **Danh tính của “khoản thu” còn `OPEN`.** “Khoản thu” là cụm mô tả được B3 dùng xuyên suốt nhưng **không** phải mục từ riêng của B2-v0.9, đúng như B6 §4.6 đã ghi. Trong mô hình hiện tại, khoản thu hợp lệ của một đơn được `Xác nhận thanh toán` giữ, còn từng giao dịch thu thừa chỉ xuất hiện dưới dạng kết quả `Thanh toán trùng` chưa mang số tiền hay định danh riêng. Vì `BIZ-013` yêu cầu hoàn **từng** giao dịch thừa độc lập, cách định danh từng khoản thu phải được chốt ở B12/B13; B7 không tự đặt thêm mục từ.
- Không hoàn một phần; số tiền hoàn bằng toàn bộ số tiền thực thu cần hoàn của khoản thu (`BIZ-011`–`BIZ-014`).
- Yêu cầu hoàn đã hoàn thành không được tạo lại; yêu cầu đang xử lý hoặc còn thử lại được tiếp tục trên cùng yêu cầu và kết quả logic thay vì tích lũy danh sách lần hoàn không giới hạn.
- Hoàn khoản thu trùng hoặc đến muộn không tạo trạng thái nghiệp vụ riêng cho đơn, không làm đơn/vé hợp lệ mất hiệu lực và không đổi tồn kho hoặc giới hạn mua (`BIZ-065`, `BIZ-102`–`BIZ-105`, `BIZ-150`).
- Kết quả hoàn được liên kết với đơn theo B2 §5 và B4 `C03`; đây là liên kết tham chiếu, không đưa `Đơn hàng` vào trong ranh giới của aggregate này.
- `Chính sách hoàn tiền` là **quy tắc cố định của nền tảng** (`BIZ-033`), được từng yêu cầu hoàn tham chiếu chứ không phải value object do mỗi yêu cầu sở hữu.

Thay đổi từ xác nhận thu tiền sang chốt giữ chỗ, phát hành vé hoặc hoàn tiền đi qua aggregate/context khác. `HOT-03` vì vậy được tách thành phần idempotency cục bộ đã có root bảo vệ và phần phối hợp lỗi xuyên aggregate còn phải phân tích ở B9–B11.

### 4.4. Quyền tham dự và kiểm soát vào cửa

Nguồn biểu đồ: `docs/diagrams/src/B7-04-ticket-and-check-in-class-model.puml`.

#### Aggregate `Phát hành vé`

- Khóa nghiệp vụ là đơn đã có xác nhận thu hợp lệ.
- **Bên trong ranh giới:** tập `Vé` và mỗi `Mã vé/QR` được sinh cho đơn đó.
- Số quyền tham dự hợp lệ không vượt số vé đã mua; thử lại phát hành không tạo thêm quyền (`INV-07`).
- Lỗi phát hành sau thu tiền làm vô hiệu mọi vé dở dang của cùng đơn; lỗi `Gửi vé` không phải lỗi phát hành (`BIZ-060`, `BIZ-063`).
- Hoàn tiền không được mô hình hóa thành trạng thái vé; hoàn khoản thu thừa không vô hiệu vé hợp lệ (`BIZ-103`–`BIZ-105`).

#### Bất biến check-in

- Một `Vé` có tối đa một `Check-in thành công`, kể cả nhiều thiết bị gửi `Yêu cầu check-in` đồng thời (`INV-09`, `HOT-04`, `BIZ-087`).
- Chỉ chấp nhận trong `Cửa sổ check-in`: `eventStartAt <= now <= eventEndAt` (`BIZ-067`).
- Organizer chỉ quét vé của sự kiện thuộc mình; không có hoàn tác, check-out hoặc tái vào cửa (`BIZ-068`, `BIZ-082`).
- Buyer chỉ xem/tải QR của vé đã phát hành thuộc đơn của mình; organizer chỉ có quyền quét; admin không có quyền xem QR thô (`BIZ-076`–`BIZ-079`).
- `Yêu cầu check-in` có thể mang mã tham chiếu của một vé, nhưng mọi thay đổi trạng thái của `Vé` trong mô hình ứng viên này phải đi qua aggregate root `Phát hành vé`; không tác nhân hay yêu cầu bên ngoài nào được sửa trực tiếp entity `Vé`.

Đặt các vé của một đơn dưới root `Phát hành vé` giúp `INV-07` và `INV-09` cùng nằm trong một ranh giới giao dịch có kích thước bị giới hạn bởi `Giới hạn mua`. Đánh đổi là hai yêu cầu check-in trên **hai vé khác nhau nhưng cùng một đơn** vẫn có thể tranh chấp trên cùng root dù chúng không chia sẻ quy tắc nghiệp vụ ở cấp vé.

Ranh giới này vì vậy tiếp tục là `CANDIDATE`, chưa phải kết luận tối ưu về hiệu năng. B9/B10 phải tách hai phép thử: (1) nhiều thiết bị quét cùng một vé để kiểm `INV-09`; và (2) nhiều thiết bị quét các vé khác nhau của cùng một đơn để đo tranh chấp và độ trễ. Nếu phép thử thứ hai cho thấy ảnh hưởng vật chất, B7 phải được mở lại để so sánh `Vé` làm aggregate root riêng; khi đó `INV-07` sẽ trở thành phối hợp giữa lần phát hành và nhiều aggregate vé, nên không được tách chỉ để loại bỏ tranh chấp mà bỏ qua bất biến phát hành.

`B5-OPEN-04` đã được Lê Văn Minh đóng: vô hiệu hóa lặp của cùng nguyên nhân được hấp thụ bởi chuyển trạng thái đơn điệu của `Vé`, không tạo thêm hậu quả nghiệp vụ (`BIZ-147`). Đây là quy tắc cục bộ của root ứng viên, không mở thêm kịch bản mạng chập chờn khi hủy sự kiện (`BIZ-148`).

### 4.5. Đối soát và chi trả

Nguồn biểu đồ: `docs/diagrams/src/B7-05-reconciliation-and-payout-class-model.puml`.

#### Aggregate `Chi trả`

- **Phạm vi tên gọi.** B2 định nghĩa `Chi trả` là *một lần chuyển tiền cho organizer, thực hiện ngoài hệ thống*, và tách riêng `Đã chi trả` là *trạng thái admin đánh dấu thủ công*. Root ở đây giữ đúng phần nằm trong hệ thống: trạng thái đối soát và dấu `Đã chi trả` của một sự kiện. Nó **không** sở hữu việc chuyển tiền, vì hệ thống không quan sát được việc đó.
- Khóa nghiệp vụ là sự kiện; một sự kiện có tối đa một lần được đánh dấu `Đã chi trả` (`INV-10`, `BIZ-037`, `BIZ-038`).
- Chỉ được mở sau khi sự kiện kết thúc, không còn `Lần thanh toán` đang chờ và không còn `Yêu cầu hoàn tiền` đang xử lý (`BIZ-050`–`BIZ-052`).
- Admin phải hoàn tất `Đối soát thủ công` với báo cáo cổng thanh toán trước khi đánh dấu; sai lệch phát hiện sau đó xử lý ngoài hệ thống và không mở lại vòng đời (`BIZ-053`, `BIZ-054`). Theo đúng định nghĩa B2, chính `Đối soát thủ công` là nơi admin xác nhận không còn `Lần thanh toán` và `Yêu cầu hoàn tiền` đang xử lý, nên hình gắn hai điều kiện đó vào quy tắc này thay vì vào root `Chi trả`.
- `Số tiền chi trả = Doanh thu thực thu - Phí nền tảng`; doanh thu thực thu đã phản ánh khuyến mãi và hoàn tiền, còn phí dùng tỷ lệ đã cố định khi sự kiện được duyệt (`BIZ-036`, `BIZ-059`, `BIZ-142`).

`Sổ cái đối soát chỉ đọc` là read model phục vụ đối chiếu, không phải aggregate ghi và không trở thành nguồn sự thật thay cho thanh toán/hoàn tiền. Nó tổng hợp từ **khoản thu hợp lệ** — tức `Xác nhận thanh toán` — và kết quả hoàn, chứ không tổng hợp từ `Lần thanh toán`: B2 định nghĩa `Doanh thu thực thu` là tổng tiền *giao dịch bán hợp lệ*, còn một lần thanh toán chỉ là một lần khởi tạo và có thể thất bại. `Lần thanh toán` chỉ được dùng làm điều kiện “không còn lần đang chờ” của `Chi trả` (`BIZ-051`). Chiều tính tiền cũng đi từ read model sang giá trị chi trả: `Số tiền chi trả` lấy `Doanh thu thực thu` rồi trừ `Phí nền tảng`. Nơi sở hữu dữ liệu vật lý của tỷ lệ phí vẫn `OPEN` tại `B5-OPEN-08`/`BIZ-123` và thuộc B12.

### 4.6. Giao nhận thông tin vé

Nguồn biểu đồ: `docs/diagrams/src/B7-06-ticket-delivery-class-model.puml`.

#### Aggregate `Gửi vé`

- Mỗi instance theo dõi việc gửi và gửi lại thông tin của cùng một tập vé đã phát hành; thử lại không tạo `Vé` hay `Mã vé/QR` mới.
- Gửi thất bại không làm thay đổi hiệu lực vé và không tự kích hoạt hoàn tiền (`BIZ-060`).
- Vé đã phát hành vẫn có thể được tải lại hoặc gửi lại (`BIZ-061`).

Aggregate này chỉ giữ trạng thái giao nhận. Quyền tham dự vẫn do `Phát hành vé` bảo vệ; đây là ranh giới ngôn ngữ “vé đã phát hành” khác với “thông tin vé đã gửi”.

### 4.7. Hồ sơ tài khoản và quyền nghiệp vụ

Nguồn biểu đồ: `docs/diagrams/src/B7-07-account-and-business-rights-class-model.puml`.

#### Aggregate `Hồ sơ nghiệp vụ ứng dụng`

- Hồ sơ nghiệp vụ tối thiểu tách khỏi `Danh tính và vòng đời tài khoản` do Keycloak quản lý (`PRJ-003`, `BIZ-131`).
- `E01` chỉ đặt tên cho kết quả đăng ký tài khoản và quyền `BUYER` mặc định; nó không quyết định thời điểm/cách tạo hồ sơ ứng dụng và không làm hồ sơ này trở thành nguồn danh tính.
- Keycloak là nơi quyết định role đăng nhập; hồ sơ ứng dụng chỉ giữ dữ kiện nghiệp vụ cần thiết, không tự trở thành nguồn danh tính thứ hai.
- Tập trường nghiệp vụ tối thiểu của hồ sơ organizer theo `BIZ-151` gồm tên tổ chức, mô tả ngắn và lý do từ chối khi có; không yêu cầu giấy tờ kinh doanh, tài khoản ngân hàng hoặc bộ nhận diện thương hiệu. Ba trường này **không** nằm cùng một ranh giới: `Hồ sơ nghiệp vụ ứng dụng` giữ tên tổ chức và mô tả ngắn, còn lý do từ chối thuộc `Đăng ký organizer` vì nó là kết quả của một lần xét duyệt cụ thể.
- Tài khoản tự đăng ký nhận `BUYER`; một tài khoản có thể đồng thời có `BUYER` và `ORGANIZER`; bộ role trong phạm vi chỉ có `BUYER`, `ORGANIZER`, `ADMIN` (`BIZ-109`, `BIZ-132`, `BIZ-134`, `BIZ-146`).
- Không xác minh email/số điện thoại, không social login và không xét khóa tài khoản giữa chừng trong phạm vi (`BIZ-119`, `BIZ-124`, `BIZ-129`).

#### Aggregate `Đăng ký organizer`

- Dùng ba trạng thái `PENDING`, `ACTIVE`, `REJECTED`; việc nộp hồ sơ sinh kết quả `E02` và chưa cấp thêm vai trò (`BIZ-134`, `BIZ-136`).
- Từ chối sinh kết quả `E04`, bắt buộc có lý do; lý do được lưu trong chính hồ sơ đăng ký, và `REJECTED` là kết quả cuối, không sửa/gửi lại (`BIZ-137`, `BIZ-138`).
- Duyệt sinh kết quả `E03` chỉ sau khi Keycloak xác nhận đã cấp role `ORGANIZER`, rồi hồ sơ mới chuyển `ACTIVE` (`BIZ-140`).
- Chỉ hồ sơ `ACTIVE` được công khai; không có luồng thu hồi role organizer trong phạm vi (`BIZ-139`, `BIZ-141`).

**Điều kiện nhất quán ứng viên từ `BIZ-140`.** Root `Đăng ký organizer` chỉ được chuyển sang `ACTIVE` sau khi nhận xác nhận rằng đúng tài khoản đã được cấp role `ORGANIZER`. Đây là điều kiện nghiệp vụ đã được xác nhận, nhưng ranh giới aggregate ứng dụng không thể gộp thao tác cấp role ở Keycloak và thay đổi hồ sơ cục bộ thành một giao dịch. Trường hợp Keycloak đã cấp role nhưng việc ghi `ACTIVE` thất bại, hoặc kết quả cấp role chưa xác định khi thử lại, phải được B9/B10 mô tả như lỗi từng phần và được B11/B13 quyết định cách phối hợp/hợp đồng. B7 không tự chọn retry, bù trừ, Saga hay luồng thu hồi role; điều kiện và cách bảo vệ này giữ trạng thái `CANDIDATE`/`OPEN` tương ứng.

Dòng `E01`–`E05` không tạo aggregate hoặc bất biến mới tại B7. `E01`–`E04` hoàn thiện truy vết vòng đời tài khoản/hồ sơ organizer; `E05` đặt tên cho việc ghi nhận quan hệ theo dõi đã có từ `BIZ-128`. Chúng không làm thay đổi kết luận về tám bounded context ứng viên.

#### Aggregate `Theo dõi organizer` và quyền sở hữu

- `Theo dõi organizer` là quan hệ tối giản giữa buyer và organizer (`E05`, `BIZ-128`). B7 không suy thêm điều kiện hồ sơ organizer phải `ACTIVE`: `BIZ-141` chỉ chốt phạm vi hồ sơ được hiển thị công khai, còn B6/B8 chưa chốt điều kiện này cho thao tác theo dõi. Cách lưu hay tính tổng người theo dõi không phải bất biến B7 và chờ B12/B13.
- **Đây hiện là root duy nhất chưa mang bất biến nào.** Ứng viên tự nhiên là “một cặp `(buyer, organizer)` chỉ tồn tại một lần, thao tác theo dõi lặp hội tụ về cùng quan hệ”, nhưng `BIZ-128` chỉ chốt năng lực ở mức tối giản và B4 `E05` ghi rõ ngay cả việc bỏ theo dõi cũng cần một quyết định riêng. B7 vì vậy ghi `OPEN` thay vì tự phát biểu bất biến; xem §6.
- `Sở hữu sự kiện` giữ nghĩa một sự kiện thuộc đúng một **tài khoản organizer** và không có chuyển quyền trong phạm vi (`BIZ-114`, `BIZ-135`). Hồ sơ đăng ký organizer không phải chủ sở hữu sự kiện. Bất biến “đúng một owner” được bảo vệ trong aggregate `Sự kiện bán vé`; context tài khoản chỉ cung cấp tham chiếu tài khoản/role để kiểm quyền.
- Biểu đồ dùng đúng tập trường nghiệp vụ đã chốt tại `BIZ-151`; kiểu dữ liệu, schema và hợp đồng vẫn chờ B12/B13.

### 4.8. Chẩn đoán sự cố

Nguồn biểu đồ: `docs/diagrams/src/B7-08-incident-diagnosis-class-model.puml`.

`Sự cố` là aggregate root **ứng viên tạm thời** để gom `Context chẩn đoán`, `Nguyên nhân khả dĩ` và `Bước kiểm tra tiếp theo` cho cùng một sự cố. `Dấu vết vận hành`, `Log có cấu trúc`, `Mã tương quan` và `Mẫu log Drain` chỉ là đầu vào đọc; mô hình không ghi ngược vào hệ thống nguồn.

`Trợ lý chẩn đoán sự cố` chỉ hỗ trợ liên kết dấu vết, đề xuất nguyên nhân khả dĩ và bước kiểm tra. Nó không tự kết luận nguyên nhân cuối cùng và không tự sửa hệ thống (`PRJ-001`, `PRJ-002`).

Ranh giới này chưa qua phép thử cuối vì workflow T01–T04 và tập ca đánh giá còn `OPEN` (`B4-OPEN-02`, `B5-OPEN-05`). Không dùng aggregate tạm này để chốt dữ liệu, service hay API trước B16–B19.

## 5. Truy vết `INV-01`–`INV-11`

| Bất biến B4 | Aggregate chịu trách nhiệm chính ở B7 | Phần được bảo vệ cục bộ | Phần còn vượt aggregate/gate sau |
|---|---|---|---|
| `INV-01` | `Ghế`, `Loại vé`, `Sector` theo nghĩa nguồn cung | Ghế không bị giữ hai lần; số lượng khả dụng của một nguồn cung không âm | Một đơn giữ nhiều nguồn cung cần phối hợp ở B9–B11; phép xét khả dụng còn dùng giữ chỗ và giới hạn mua |
| `INV-02` | `Đơn hàng` | Một sự kiện/đơn; cùng sector với `SEAT_MAP` | Không còn điểm mở ở B7 |
| `INV-03` | `Đơn hàng` | Một giữ chỗ và một hạn chung | Chốt/trả nguồn cung phải phối hợp với root `Ghế`, `Loại vé` hoặc `Sector` tương ứng với loại nguồn cung; `Khả dụng` chỉ là kết quả xét |
| `INV-04` | `Giới hạn mua` | Tổng đang giữ + đã mua theo tài khoản/sự kiện | Tạo/hủy đơn phải phối hợp với `Đơn hàng` |
| `INV-05` | `Xác nhận thanh toán` | Một kết quả thu hợp lệ cho đơn | Từng khoản thu thừa đi sang `Yêu cầu hoàn tiền` |
| `INV-06` | `Đơn hàng`, các root nguồn cung, `Giới hạn mua`, `Khuyến mãi`, `Lượt dùng khuyến mãi` | Mỗi root dùng chuyển trạng thái hội tụ để không ghi nhận hai lần | Thứ tự, retry và phục hồi xuyên root ở B9–B11 |
| `INV-07` | `Phát hành vé`; `Gửi vé` chỉ tham chiếu | Một lần phát hành logic/đơn, đúng số quyền; gửi lại không sinh quyền | Thu tiền, trả tài nguyên và hoàn kỹ thuật đi qua context khác |
| `INV-08` | `Yêu cầu hoàn tiền` | Một yêu cầu logic và một hoàn thành/khoản thu | Lô hoàn N đơn khi hủy sự kiện còn `B5-OPEN-03` |
| `INV-09` | `Phát hành vé` | Một vé tối đa một check-in thành công | Cơ chế tranh chấp cụ thể ở B9–B11 |
| `INV-10` | `Chi trả` | Một lần đánh dấu đã chi trả/sự kiện | Dữ liệu đọc từ thanh toán/hoàn tiền là xuyên context |
| `INV-11` | `Sự kiện bán vé`; `Chi trả` tiêu thụ giá trị đã duyệt | Không sửa âm thầm tỷ lệ sau duyệt | Sở hữu dữ liệu vật lý chờ B12 |

## 6. Kết quả xử lý hotspot và điểm mở

| Điểm mở/hotspot | Kết quả tại B7-v0.7 | Trạng thái và bước tiếp |
|---|---|---|
| `HOT-01`, `B4-OPEN-06`, `B5-OPEN-03` — hủy sự kiện kéo theo N đơn/hoàn tiền | B7 xác định các root tham gia nhưng không biến fan-out N đơn thành một giao dịch aggregate | Cơ chế phối hợp chờ B11; không mở một kịch bản chất lượng riêng về mạng chập chờn khi hủy sự kiện nếu không phục vụ trục nghiên cứu (`BIZ-148`) |
| `HOT-02`, `B4-OPEN-05`, `B5-OPEN-02` — nguồn cung/giữ chỗ/đơn | Đã thu hẹp thành `Đơn hàng`; root nguồn cung `Ghế`/`Loại vé`/`Sector`; `Giới hạn mua`; `Khuyến mãi`; `Lượt dùng khuyến mãi`; không nhập thành aggregate lớn | Ranh giới aggregate là `CANDIDATE`; phối hợp nguyên tử/retry còn `OPEN` cho B9–B11 |
| `HOT-03` — callback trùng/muộn, lỗi sau thu tiền | `Xác nhận thanh toán` và `Yêu cầu hoàn tiền` bảo vệ idempotency cục bộ; phát hành/trả tài nguyên là xuyên aggregate | Còn phân tích thất bại tại B9/B10 và phương án tại B11. Thêm một điểm `OPEN`: cách định danh từng “khoản thu” — khóa nghiệp vụ của `Yêu cầu hoàn tiền` — chưa có mục từ B2 và chưa có lớp biểu diễn, trong khi `BIZ-013` đòi hoàn từng giao dịch thừa độc lập; owner Lê Văn Minh, gate B12/B13 |
| `HOT-04` — check-in cạnh tranh | Trạng thái sử dụng được bảo vệ qua root `Phát hành vé`; chỉ một chuyển đổi thành công cho mỗi vé; yêu cầu ngoài không sửa trực tiếp entity `Vé` | Ranh giới là `CANDIDATE`; B9/B10 kiểm cả tranh chấp cùng vé và tranh chấp giữa các vé khác nhau trong cùng đơn, rồi mới quyết định có cần mở lại ranh giới trước B11-A hay không; cách khóa/ghi cụ thể chờ B11–B13 |
| `B4-OPEN-07`, `B5-OPEN-04` — vô hiệu hóa lặp | Chuyển trạng thái đơn điệu trong `Vé`; lần lặp không tạo thêm hậu quả | Đã đóng bởi `BIZ-147`; không mở rộng theo `BIZ-148` |
| `B4-OPEN-02`, `B5-OPEN-05` — workflow chẩn đoán | Chỉ có aggregate `Sự cố` tạm thời, không chốt lưu trữ hay API | `OPEN`; B16–B19 |
| `B4-OPEN-03`, `B5-OPEN-06` — mốc thời gian kỹ thuật | B7 chỉ giữ cửa sổ nghiệp vụ, không đặt timer/event kỹ thuật | `OPEN`; B11/B13 |
| `B4-OPEN-04`, `B5-OPEN-07` — audit | Danh sách thao tác bắt buộc lưu dấu vết đã đóng tại `BIZ-152`; B7 không thêm lớp audit vào aggregate miền | Trường/payload/lưu giữ vẫn `OPEN` cho B13/B16 |
| `B4-OPEN-08`, `B5-OPEN-08`, `BIZ-123` — nơi giữ tỷ lệ phí | B7 chỉ gán bất biến nghĩa nghiệp vụ cho `Sự kiện bán vé` | `OPEN`; B12 sau B11-C |
| `B5-OPEN-10` — sức chứa địa điểm và nguồn cung | Không tạo bất biến tổng sức chứa; hệ thống không biết/kiểm sức chứa vật lý | Đã đóng bởi `BIZ-149` |
| `B5-OPEN-11` — tổng người theo dõi | Không ảnh hưởng ranh giới `Theo dõi organizer` | Cách lưu/tính chờ B12/B13. Kèm theo: root `Theo dõi organizer` chưa có bất biến nào được `BIZ-128` chốt — kể cả tính duy nhất của cặp `(buyer, organizer)`; owner Lê Văn Minh, cần một quyết định nghiệp vụ riêng trước khi B7 phát biểu bất biến |
| `B5-OPEN-12` — trường hồ sơ organizer | Aggregate dùng tên tổ chức, mô tả ngắn và lý do từ chối khi có | Đã đóng phần nghiệp vụ bởi `BIZ-151`; kiểu dữ liệu/schema/hợp đồng chờ B12/B13 |
| `B4-OPEN-01`, `E03`, `BIZ-140` — Keycloak cấp role và hồ sơ chuyển `ACTIVE` | Root `Đăng ký organizer` chặn chuyển `ACTIVE` khi chưa có xác nhận cấp role; việc cấp role bên ngoài và ghi hồ sơ cục bộ không nằm trong một giao dịch aggregate | Điều kiện cục bộ là `CANDIDATE`; lỗi từng phần/thử lại còn `OPEN` cho B9/B10, phương án phối hợp và hợp đồng chờ B11/B13; không tự thêm luồng thu hồi role |
| `BIZ-130` — trạng thái đơn | B7 giữ nghĩa `BIZ-150`, không đặt enum trạng thái | Tên/chuyển trạng thái và cách biểu diễn còn `OPEN` cho B12/B13; không chặn B7/B8 |
| `Yêu cầu hủy sự kiện` chưa có nơi bảo vệ — phát hiện tại vòng kiểm toán `B7-v0.6` | Lê Văn Minh chọn mô hình hóa và bổ sung mục từ B2. `B2-v0.10` thêm mục từ, `B7-v0.7` thêm aggregate ứng viên `Yêu cầu hủy sự kiện` tại §4.1 với bất biến truy về `BIZ-008`, `BIZ-009`, `BIZ-083`, `BIZ-097`–`BIZ-099` | Đã đóng phần ranh giới. Còn `OPEN`: có được gửi lại yêu cầu sau khi bị từ chối hay không. B6/B8 đã được duyệt khi điểm này còn mở nên hai tài liệu cố ý im lặng; chốt “có” cần một quyết định nghiệp vụ riêng và mở lại B6/B8. Chuỗi `B2 → B5` đã được duyệt lại ngày 2026-08-22 |

## 7. Kiểm tra từ vựng và giới hạn thiết kế

Phép kiểm từ vựng gồm **hai phần tách biệt**. Gộp chúng làm một là nguyên nhân khiến vòng trước bỏ sót lỗi: một lớp có thể trùng tên mục từ B2 mà vẫn mang nghĩa khác.

**Phần 1 — tên.** Tám biểu đồ dùng 57 tên lớp duy nhất; cả 57 đều là mục từ nguyên văn của B2-v0.10. B7 không thêm lớp có tên kỹ thuật như service, controller, repository, database, message, queue, API hoặc Saga.

**Phần 2 — nghĩa.** Từng lớp phải khớp cả định nghĩa lẫn cột “phân biệt dứt khoát với” của B2. Vòng `B7-v0.6` sửa bốn chỗ trước đây chỉ khớp tên:

| Khái niệm | Nghĩa B2 bị lệch ở `B7-v0.5` | Cách xử lý từ `B7-v0.6` |
|---|---|---|
| `Khả dụng` | B2 xét trên trạng thái bán, tồn kho, giữ chỗ **và giới hạn mua**; hình chỉ vẽ tồn kho | Bổ sung giữ chỗ và giới hạn mua vào phép xét; ghi rõ điều kiện mở bán đến từ context khác |
| `Tồn kho vé còn lại` | Bị dùng thay cho “số lượng khả dụng” khi phát biểu `INV-01`, dù B2 nói tồn kho “chỉ là một đầu vào” của khả dụng | Trả `INV-01` về đúng câu chữ B4-v0.13 |
| `Chi trả` | B2 định nghĩa là lần chuyển tiền **ngoài hệ thống**; root lại giữ trạng thái đối soát trong hệ thống | Giữ tên nhưng nói rõ phạm vi root là trạng thái đối soát và dấu `Đã chi trả` |
| `Chính sách hoàn tiền` | B2 là quy tắc cố định cấp nền tảng; hình cho mỗi `Yêu cầu hoàn tiền` sở hữu một bản | Chuyển thành quy tắc được tham chiếu |

Bốn khái niệm `Ghế`, `Loại vé`, `Sector` và `Khuyến mãi` cố ý mang hai mô hình ở hai context. Đây **không** phải lệch nghĩa: B5 §4 đã ghi ranh giới ngôn ngữ tương ứng, và §3/§4.2 nêu rõ mô hình nào thuộc context nào.

Những khái niệm chỉ dùng làm read model, yêu cầu, quy tắc, kết quả, nguồn danh tính/quyền, đầu vào đọc hoặc tham chiếu đều được ghi stereotype rõ ràng; chúng không bị nâng thành aggregate root chỉ để sơ đồ trông đầy đủ. Đặc biệt:

- `Tìm sự kiện` là khả năng đọc, không phải aggregate ghi;
- `Sổ cái đối soát chỉ đọc` là read model;
- `Yêu cầu check-in` là yêu cầu tác động lên vé, không sở hữu trạng thái sử dụng;
- `Danh tính và vòng đời tài khoản` là nguồn danh tính/role bên ngoài hồ sơ nghiệp vụ ứng dụng;
- `Theo dõi organizer` và `Sở hữu sự kiện` tham chiếu tài khoản/vai trò tương ứng; chúng không lấy hồ sơ đăng ký organizer làm nguồn danh tính hay chủ sở hữu;
- `Dấu vết vận hành`, `Log có cấu trúc`, `Mã tương quan` và `Mẫu log Drain` đều là đầu vào đọc của nhánh chẩn đoán và dùng chung một stereotype.

## 8. Điều kiện chuyển `REVIEW_READY`

Mỗi ô dưới đây được suy lại theo **nội dung hiện tại của `B7-v0.7`**, không kế thừa kết quả của vòng trước:

- [x] Tám nguồn biểu đồ lớp phân tích parse và render được.
- [x] Mọi **tên lớp** đều truy được về B2-v0.10 — 57/57, đếm lại trên bản `v0.7`.
- [x] Mọi **nghĩa** của lớp đều khớp định nghĩa B2-v0.10; bốn chỗ lệch nghĩa của `v0.5` đã được sửa và ghi lại tại §7.
- [x] Mọi đối tượng nghiệp vụ có vòng đời riêng ở B3/B4 đều có nơi bảo vệ ở B7. `Yêu cầu hủy sự kiện` — chỗ bỏ sót của `v0.5` — đã có aggregate ứng viên tại §4.1.
- [x] `INV-01`–`INV-11` không bị bỏ sót và được phát biểu đúng câu chữ B4-v0.13 §10.
- [x] Bội số trong tám hình không phát biểu quy tắc nghiệp vụ nào chưa được duyệt; hai bội số sai của `v0.5` ở `B7-01` đã được sửa.
- [x] Không có service/schema/Saga/ADR hoặc bằng chứng hiện thực tham khảo đi vào lập luận hình thành. Stereotype «Domain Service» của `Trợ lý chẩn đoán sự cố` là khái niệm chiến thuật mức phân tích, không phải đơn vị triển khai; nguồn danh tính được dẫn theo `PRJ-003` đã `USER_CONFIRMED`.
- [x] Tám hình tuân Tầng C §3.2.1 và `GOV-020`: không dùng màu nền để mang nghĩa, phân nhóm bằng tiêu đề khung và stereotype nên vẫn đọc được khi in thang xám.
- [x] Tác giả B6 đã rà chéo B7; tác giả B7 đã xử lý các phát hiện về `Khả dụng`, ranh giới phát hành/check-in, tài khoản–hồ sơ organizer và tác động của `E05` mà không tự đóng điểm mở.
- [x] Tác động của B4-v0.13/B5-v0.11 đã được đánh giá: các quyết định mới không đổi tập context hoặc ranh giới aggregate; `BIZ-147` chỉ khóa chuyển trạng thái vé đơn điệu.
- [x] Chuỗi đầu vào được duyệt đúng thứ tự ở cả hai vòng: `B4-v0.13` trước `B5-v0.11`, rồi `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12` ngày 2026-08-22.
- [x] Lê Văn Minh đã đóng riêng `B5-OPEN-04`, `B5-OPEN-10` và phần nghiệp vụ của `B5-OPEN-12`.
- [x] Lê Văn Minh đã quyết cách xử lý `Yêu cầu hủy sự kiện`: mô hình hóa tại B7 và bổ sung mục từ B2. Hệ quả là `B2-v0.10` → `B3-v0.10` → `B4-v0.14` → `B5-v0.12` cùng trở lại `REVIEW_READY`.
- [x] Lê Văn Minh đã duyệt lại chuỗi `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12` đúng thứ tự ngày 2026-08-22.
- [x] Sau chuỗi trên, Lê Văn Minh đã duyệt toàn bộ `B7-v0.7` thành `APPROVED` ngày 2026-08-22; AI không tự đánh dấu thay.

**Bằng chứng xác minh ngày 2026-08-22, đo lại trên `B7-v0.7`:** cả tám nguồn `B7-01`–`B7-08` parse (`-checkonly`) và render thành công bằng **PlantUML 1.2026.6**, mã thoát `0`; kích thước và tỷ lệ rộng/cao lần lượt là 1073×1464 (**0,73**), 1353×1025 (**1,32**), 688×1240 (**0,55**), 1103×735 (**1,50**), 1103×1079 (**1,02**), 601×360 (**1,67**), 1305×803 (**1,63**), 1286×698 (**1,84**) — đều nằm trong khoảng đọc được trên A4 dọc hoặc ngang, không hình nào còn nhãn chồng lên lớp khác. `B7-03` chuyển sang dạng dọc sau khi `Chính sách hoàn tiền` ra khỏi ranh giới aggregate và thêm liên kết với `Đơn hàng`. `B7-05` được bố cục lại sau khi đo: hai điều kiện “không còn tiền treo” chuyển từ cạnh của `Chi trả` sang `Đối soát thủ công` và mô tả bằng ghi chú, nên hết chồng nhãn và tỷ lệ về 1,02. `B7-01` tăng lên 1073×1464 sau khi nhận thêm aggregate `Yêu cầu hủy sự kiện`, tỷ lệ gần như không đổi. Phép kiểm từ vựng đếm lại bằng script được 57 tên lớp duy nhất, đủ 57/57 xuất hiện nguyên văn trong B2-v0.10; phép kiểm nghĩa được ghi riêng tại §7.

*Số đo của `B7-v0.5` — dãy 0,63 · 1,04 · 1,24 · 1,49 · 0,76 · 1,66 · 1,62 · 1,84 đo bằng PlantUML 1.2025.4 — được giữ trong nhật ký để so sánh, không còn là số hiện hành.*

AI không tự chuyển B7 thành `APPROVED`. Lê Văn Minh là người duyệt nội dung và ngày duyệt; `B7-v0.7` được duyệt ngày 2026-08-22 sau khi chuỗi `B2 → B3 → B4 → B5` được duyệt lại.

Chuỗi `B4-v0.11 → B5-v0.9` đã được Lê Văn Minh duyệt trực tiếp (`GOV-021`); `B4-v0.13 → B5-v0.11` lan truyền các lựa chọn nghiệp vụ ngày 2026-08-22 và cũng được ghi với Lê Văn Minh là người duyệt. Nếu một đầu vào thay đổi vật chất về sau, B7 phải trở lại vòng đánh giá tác động và đồng bộ.

## 9. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B7-v0.7` | 2026-08-22 | Bổ sung aggregate ứng viên `Yêu cầu hủy sự kiện` vào context vòng đời sự kiện, sau khi Lê Văn Minh chọn mô hình hóa và `B2-v0.10` thêm mục từ; đóng điểm `OPEN` về ranh giới và giữ `OPEN` về việc gửi lại yêu cầu; đồng bộ khai đầu vào sang chuỗi `B2-v0.10 → B5-v0.12`; đếm lại 57 tên lớp | Leo thang lỗi thượng nguồn và lan truyền |
| `B7-v0.6` | 2026-08-22 | Xử lý vòng kiểm toán đọc toàn văn: sửa note bốn nguyên nhân hoàn tiền ở `B7-03`; ghi `OPEN` cho `Yêu cầu hủy sự kiện` và cho tính duy nhất của quan hệ theo dõi; trả `INV-01` về câu chữ B4; bổ sung giữ chỗ/giới hạn mua vào phép xét `Khả dụng`; tách nơi giữ lý do từ chối; làm rõ phạm vi root `Chi trả`; sửa nguồn tổng hợp của sổ cái và chiều tính số tiền chi trả; sửa hai bội số ở `B7-01`; đưa `Chính sách hoàn tiền` ra ngoài ranh giới aggregate; bỏ màu nền phân nhóm theo Tầng C §3.2.1; tách §7 thành phép kiểm tên và phép kiểm nghĩa; đo lại tám hình | Sửa sau kiểm toán |
| `B7-v0.5` | 2026-08-22 | Đồng bộ B4-v0.13/B5-v0.11; đóng các điểm vô hiệu vé lặp, sức chứa và trường hồ sơ; giới hạn lịch sử trong aggregate thanh toán/hoàn; sửa ký pháp/bội số/bố cục tám sơ đồ và chuyển sang `REVIEW_READY` | Lan truyền quyết định và sửa sau rà trực quan |
| `B7-v0.4` | 2026-08-21 | Đồng bộ B4-v0.11/B5-v0.9 và `E05`; sửa mô hình theo dõi/sở hữu để tham chiếu tài khoản và role thay vì hồ sơ đăng ký organizer; bỏ điều kiện `ACTIVE` chưa được chốt cho thao tác theo dõi; ghi rõ điều kiện nhất quán và khoảng hở lỗi Keycloak–hồ sơ theo `BIZ-140`; chuyển cổng `REVIEW_READY` thành checklist có trạng thái | Sửa sau rà chéo vòng tiếp theo |
| `B7-v0.3` | 2026-08-21 | Xử lý rà chéo B6/B7: làm rõ đánh đổi giữa bất biến phát hành theo đơn và check-in theo vé; buộc yêu cầu check-in đi qua aggregate root; bổ sung phép thử tranh chấp cho B9/B10; sửa câu chữ không coi `Khả dụng` là thành phần; ghi bằng chứng tám biểu đồ render và 56/56 tên lớp truy được về B2 | Hiệu đính sau rà chéo |
| `B7-v0.2` | 2026-08-21 | Đồng bộ đầu vào B4-v0.10/B5-v0.8 và truy vết `E01`–`E04` vào vòng đời tài khoản/organizer; không đổi tập context, aggregate, bất biến hoặc biểu đồ | Lan truyền từ B4/B5 |
| `B7-v0.1` | 2026-08-21 | Bản đầu: kiểm thử tám bounded context bằng aggregate ứng viên, truy vết `INV-01`–`INV-11`, xử lý hotspot và tạo tám nguồn biểu đồ lớp phân tích | Tạo mới |
