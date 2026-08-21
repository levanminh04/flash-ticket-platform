# B4 — Bản đồ sự kiện miền

- Phiên bản: `B4-v0.9`
- Trạng thái: `APPROVED`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-08-21
- Đầu vào và phiên bản: `docs/glossary.md` — `B2-v0.9`, `APPROVED` ngày 2026-08-21; `docs/domain/B3-business-processes.md` — `B3-v0.9`, `APPROVED` ngày 2026-08-21; `docs/project/decision-register.md` — dùng đúng các ID được dẫn tại từng mục và đọc trạng thái riêng của mỗi dòng
- Phân lớp: `FORMATION`

## 1. Mục đích và giới hạn

B4 chuyển bốn quy trình đầu-cuối ở B3 thành chuỗi **việc nghiệp vụ đã xảy ra**, đồng thời chỉ ra tác nhân/lệnh tạo ra chúng và chính sách phản ứng tiếp theo. Tài liệu này giúp B5 quan sát nhóm sự kiện nào dùng chung ngôn ngữ, quy tắc và nhịp thay đổi; B7 dùng các điểm tranh chấp để làm rõ aggregate và bất biến.

B4 **không** quyết định:

- bounded context cuối cùng hoặc cách gom sự kiện thành context;
- service vật lý, tiến trình triển khai hoặc schema sở hữu dữ liệu;
- sự kiện tích hợp, topic/message broker, API hay payload hợp đồng;
- luồng nào dùng Saga hoặc cơ chế giao dịch phân tán nào;
- vị trí triển khai tồn kho, giữ chỗ, đơn, thanh toán hoặc vé.

Các tên trong cột “Sự kiện miền” là ngôn ngữ phân tích, chưa phải tên class hoặc message ở B13.

## 2. Cách đọc bản đồ

| Thành phần | Cách hiểu trong B4 | Ví dụ |
|---|---|---|
| **Tác nhân/nguồn kích hoạt** | Người, hệ thống ngoài hoặc mốc thời gian khởi đầu một hành vi | Buyer, organizer, admin, cổng thanh toán, `eventEndAt` |
| **Lệnh/điều kiện** | Yêu cầu ở thể mệnh lệnh hoặc điều kiện làm chính sách được xét | “Hủy đơn”, “Xác nhận thanh toán”, “Đã tới thời điểm kết thúc sự kiện” |
| **Sự kiện miền** | Việc có ý nghĩa nghiệp vụ đã xảy ra, viết ở thì quá khứ | “Đơn đã hết hạn”, “Vé đã được phát hành” |
| **Chính sách** | Khi một sự kiện xảy ra và đủ điều kiện thì phản ứng nghiệp vụ tiếp theo được kích hoạt | Khi đơn hết hạn thì trả tài nguyên đang giữ đúng một lần |
| **Mốc thời gian nghiệp vụ** | Sự thật phát sinh do thời gian; B4 không bắt buộc phải lưu thành sự kiện kỹ thuật | Bắt đầu cửa sổ bán, sự kiện kết thúc |
| **Trạng thái suy ra** | Kết quả đánh giá điều kiện hiện tại, không phải một sự kiện miền đã xảy ra và không mặc nhiên cần được lưu hay phát đi | Sự kiện đang khả dụng để bán |
| **Kết quả từ chối** | Kết quả cần trả rõ cho tác nhân nhưng không mặc nhiên làm đổi trạng thái miền. Yêu cầu lưu dấu vết được xác định riêng theo từng luồng, không suy ra cho mọi từ chối | Từ chối QR đã sử dụng |
| **`OPEN`** | Thiếu đầu vào hoặc cần gate sau xử lý; không được tự lấp để làm tài liệu trông hoàn chỉnh | Phạm vi vòng đời tài khoản |

Một truy vấn chỉ đọc không tự tạo sự kiện miền. Ví dụ, việc buyer hoặc chatbot xem danh sách sự kiện đã công bố là một truy vấn; chỉ khi một lệnh làm thay đổi trạng thái như tạo giữ chỗ hoặc hủy đơn thì chuỗi sự kiện nghiệp vụ mới bắt đầu.

## 3. Mạch nghiệp vụ tổng thể

```text
Sự kiện đã được duyệt
  → Sự kiện đã được công bố
  → [đủ điều kiện bán tại thời điểm hiện tại]
  → Giữ chỗ và đơn đã được tạo
  → Thanh toán đã được xác nhận
  → Vé đã được phát hành
  → Vé đã được check-in

Nhánh hết hạn/hủy đơn
  Đơn đã hết hạn hoặc đã bị buyer hủy
  → tài nguyên tạm giữ được trả đúng một lần
  → nếu tiền đến sau đó: yêu cầu hoàn toàn bộ được tạo

Nhánh hủy sự kiện
  Sự kiện đã bị hủy
  → đóng bán + hủy đơn đang giữ + vô hiệu vé
  → với từng khoản thu hợp lệ còn số tiền phải hoàn: tạo mới hoặc tiếp tục đúng một yêu cầu hoàn logic
  → cập nhật đối soát

Nhánh chi trả
  Sự kiện đã kết thúc + không còn lần thanh toán/yêu cầu hoàn đang xử lý
  → đối soát được xác nhận
  → chuyển tiền ngoài hệ thống
  → sự kiện được đánh dấu PAID đúng một lần
```

Sơ đồ chữ trên chỉ tóm tắt quan hệ nhân quả. Các bảng dưới đây mới là bản đồ dùng để rà nhánh lỗi và chuẩn bị cho B5/B7.

## 4. Dòng thời gian A — Vòng đời sự kiện bán vé

| Mã | Tác nhân/nguồn kích hoạt | Lệnh hoặc điều kiện | Sự kiện miền/mốc nghiệp vụ | Chính sách hoặc kết quả tiếp theo | Nguồn |
|---|---|---|---|---|---|
| `A01` | Organizer | Tạo sự kiện | **Bản nháp sự kiện đã được tạo** (`DRAFT`) | Organizer được tiếp tục cấu hình nội dung, thời gian, địa điểm và cấu hình bán; sự kiện chưa công khai và chỉ được bán vé khi đạt điều kiện tại `A07` | B3 §2.3; BIZ-007, BIZ-021, BIZ-143 |
| `A02` | Organizer | Cập nhật cấu hình khi sự kiện còn `DRAFT` | **Cấu hình bán đã được thay đổi**; **Khuyến mãi đã được cấu hình** | Cấu hình phải có đúng một `salesMode`: `QUANTITY` không dùng sơ đồ, hoặc `SEAT_MAP` có thể trộn sector `SEATED`/`STANDING` và nhiều loại vé trong một sector; đổi mode phải xác nhận xóa phần không tương thích. Mỗi khuyến mãi thuộc một sự kiện, do organizer quản lý và tự có hiệu lực theo `startAt`/`endAt`; không có thao tác bật/tạm dừng thủ công | BIZ-001–BIZ-003, BIZ-015–BIZ-020, BIZ-025, BIZ-039–BIZ-048, BIZ-069, BIZ-081, BIZ-092–BIZ-095 |
| `A03` | Organizer | Gửi sự kiện để duyệt | **Sự kiện đã được gửi duyệt** (`PENDING_APPROVAL`) | Khóa cấu hình thương mại, khuyến mãi và `salesMode` cho tới khi sự kiện trở lại `DRAFT` | B3 §2.3; BIZ-020, BIZ-094, BIZ-144 |
| `A04` | Admin | Phê duyệt sự kiện/cấu hình thương mại và nhập tỷ lệ phí nền tảng | **Sự kiện đã được phê duyệt** (`APPROVED`) | Cố định tỷ lệ phí đã nhập; sự kiện đủ điều kiện để organizer công bố, nhưng phê duyệt không tự công bố hoặc mở bán. Admin không có giao diện CRUD khuyến mãi riêng | BIZ-004, BIZ-007, BIZ-034, BIZ-096, BIZ-142, BIZ-144 |
| `A05` | Admin | Từ chối hoặc trả lại | **Sự kiện đã được trả về bản nháp** (`DRAFT`) | Organizer được sửa nội dung, cấu hình thương mại, khuyến mãi và `salesMode` rồi gửi duyệt lại; không tạo trạng thái sự kiện bền vững `REJECTED` | B3 §2.4; BIZ-020, BIZ-094, BIZ-145 |
| `A06` | Organizer | Công bố sự kiện đã duyệt | **Sự kiện đã được công bố** (`PUBLISHED`) | Sự kiện được phép hiển thị; yêu cầu công bố lặp không tạo thêm tác dụng phụ | BIZ-004, BIZ-007, BIZ-088, BIZ-144 |
| `A07` | Hệ thống đánh giá điều kiện | Sự kiện đã duyệt, đã công bố, chưa hủy và `now` nằm trong cửa sổ bán đã cấu hình | **Sự kiện đang khả dụng để bán** *(trạng thái suy ra)* | Cho phép yêu cầu tạo giữ chỗ; sự kiện đã bắt đầu vẫn có thể bán nếu điều kiện còn đúng | BIZ-005–BIZ-007, BIZ-058 |
| `A08` | Hệ thống đánh giá điều kiện | Một điều kiện mở bán không còn đúng | **Sự kiện không còn khả dụng để bán** *(trạng thái suy ra)* | Từ chối ý định mua mới; không suy ra rằng mọi đơn cũ đều bị hủy nếu sự kiện chỉ hết cửa sổ bán | BIZ-007; B3 §2.3 |
| `A09` | Organizer | Yêu cầu hủy sự kiện đã có đơn thu tiền | **Yêu cầu hủy sự kiện đã được gửi** | Chờ admin xử lý; chưa đóng bán chỉ từ việc gửi yêu cầu | BIZ-009 |
| `A10` | Admin | Từ chối yêu cầu hủy | **Yêu cầu hủy sự kiện đã bị từ chối** | Yêu cầu chuyển `REJECTED`, lý do là tùy chọn, trạng thái sự kiện không đổi và không khởi động hoàn tiền do hủy | BIZ-097–BIZ-099 |
| `A11` | Organizer hoặc admin | Khi `now < eventStartAt`: organizer hủy trực tiếp nếu chưa có đơn thu tiền, hoặc admin xác nhận yêu cầu/chủ động hủy | **Sự kiện đã bị hủy** (`CANCELLED`) | Đóng bán, hủy đơn chưa thanh toán, vô hiệu vé và khởi tạo `C05` để lọc các khoản thu còn phải hoàn | BIZ-008–BIZ-010, BIZ-066, BIZ-083–BIZ-086, BIZ-097–BIZ-102, BIZ-144 |
| `A12` | Mốc thời gian nghiệp vụ | Đã tới `eventStartAt` | **Sự kiện đã bắt đầu** *(mốc nghiệp vụ)* | Không nhận hủy sự kiện trong phạm vi sản phẩm; mở cửa sổ check-in, nhưng việc bán có thể tiếp tục nếu còn trong cửa sổ bán | BIZ-058, BIZ-067, BIZ-083 |
| `A13` | Mốc thời gian nghiệp vụ | Đã qua `eventEndAt` | **Sự kiện đã kết thúc** *(mốc nghiệp vụ)* | Đóng check-in; xét điều kiện đối soát/chi trả khi không còn lần thanh toán/yêu cầu hoàn đang xử lý | BIZ-050–BIZ-053, BIZ-067 |

### 4.1 Điểm cần giữ khi sang B5/B7

- Công bố và khả dụng để bán là hai khái niệm khác nhau; không gom chỉ vì thường xuất hiện gần nhau.
- `saleStartAt`, `saleEndAt`, `eventStartAt`, `eventEndAt` là bốn mốc có tác động khác nhau.
- Hủy sự kiện là một biến cố lan sang đơn, tài nguyên tạm giữ, vé, hoàn tiền, check-in và đối soát.
- B4 chỉ ghi quan hệ phản ứng sau `A11`; chưa gọi quan hệ đó là Saga và chưa quyết định thành phần nào điều phối.

## 5. Dòng thời gian B — Chọn vé, giữ chỗ, đơn, thanh toán và phát hành vé

| Mã | Tác nhân/nguồn kích hoạt | Lệnh hoặc điều kiện | Sự kiện miền | Chính sách hoặc kết quả tiếp theo | Nguồn |
|---|---|---|---|---|---|
| `B01` | Buyer | Tạo đơn từ lựa chọn hợp lệ | **Giữ chỗ đã được tạo**; **Đơn đã được tạo** | Cam kết nguồn cung và phần giới hạn mua trong một thời hạn chung; một đơn thuộc một sự kiện và, với `SEAT_MAP`, một sector | BIZ-001, BIZ-016–BIZ-017, BIZ-026–BIZ-029, BIZ-080 |
| `B02` | Buyer | Áp dụng một mã hợp lệ | **Khuyến mãi đã được áp dụng**; **Lượt dùng khuyến mãi đã được giữ** | Tính số tiền cuối lớn hơn 0; lượt dùng đi cùng thời hạn đơn | BIZ-039–BIZ-048, BIZ-069, BIZ-081 |
| `B03` | Buyer | Khởi tạo thanh toán | **Lần thanh toán đã được khởi tạo** | Chờ kết quả cổng thanh toán; không tạo song song lần mới khi lần hiện tại chưa kết thúc | BIZ-031; B3 §3.3 |
| `B04` | Cổng thanh toán | Báo lần thử thất bại | **Lần thanh toán đã thất bại** | Nếu đơn còn hiệu lực, buyer được tạo lần thử tiếp theo theo thứ tự | BIZ-031 |
| `B05` | Cổng thanh toán | Gửi callback/IPN hợp lệ đúng đơn, lần thử và số tiền | **Thanh toán đã được xác nhận** | Chỉ giữ một kết quả thu hợp lệ; chốt tài nguyên đã giữ và yêu cầu phát hành vé | BIZ-012–BIZ-013, BIZ-032; B3 §3.3 |
| `B06` | Chính sách sau `B05` | Phát hành toàn bộ vé thành công | **Vé đã được phát hành** | Buyer được xem/tải QR thuộc đơn của mình; thử gửi thông tin vé | BIZ-060–BIZ-061, BIZ-076–BIZ-079 |
| `B07` | Hệ thống | Gửi thành công hoặc thất bại | **Thông tin vé đã được gửi** hoặc **Gửi vé đã thất bại** | Gửi thất bại không làm vé mất hiệu lực và không tự tạo hoàn tiền; cho phép gửi/tải lại | B3 §3.3–§3.4; BIZ-060–BIZ-061 |
| `B08` | Buyer | Hủy đơn còn giữ chỗ, chưa có thanh toán thành công | **Đơn đã bị buyer hủy** | Chặn lần thanh toán mới; trả giữ chỗ, nguồn cung, giới hạn mua và lượt khuyến mãi đúng một lần | BIZ-071, BIZ-090–BIZ-091 |
| `B09` | Mốc thời gian nghiệp vụ | Hết thời hạn chung của đơn/giữ chỗ | **Đơn đã hết hạn**; **Giữ chỗ đã hết hạn** | Chặn lần thanh toán mới; trả tài nguyên tạm giữ đúng một lần | BIZ-028–BIZ-029, BIZ-047–BIZ-048, BIZ-062 |
| `B10` | Cổng thanh toán | Tiền đến sau khi đơn hết hạn hoặc bị hủy, kể cả do sự kiện bị hủy | **Thanh toán đến muộn đã được phát hiện** | Tạo yêu cầu hoàn toàn bộ; không trả tài nguyên lần thứ hai | BIZ-012, BIZ-062, BIZ-086, BIZ-091, BIZ-106 |
| `B11` | Cổng thanh toán/hệ thống | Cùng khoản phải trả bị thu hơn một lần | **Thanh toán trùng đã được phát hiện** | Giữ giao dịch hợp lệ, tạo yêu cầu hoàn cho từng giao dịch thừa; không thay đổi vé/tài nguyên của đơn hợp lệ | BIZ-013, BIZ-032, BIZ-065 |
| `B12` | Chính sách sau `B05` | Không thể phát hành đầy đủ vé sau khi đã thu tiền | **Phát hành vé đã thất bại** | Vô hiệu vé dở dang, trả lượt khuyến mãi đúng một lần, tạo yêu cầu hoàn toàn bộ; chỉ trả tồn kho/giới hạn nếu sự kiện vẫn đủ điều kiện bán | BIZ-012, BIZ-063–BIZ-064, BIZ-070 |
| `B13` | Chính sách sau `A11` | Sự kiện bị hủy khi đơn chưa thanh toán/giữ chỗ còn hoạt động | **Đơn đang giữ đã bị hủy do sự kiện bị hủy** | Chặn thanh toán mới; trả giới hạn mua/lượt khuyến mãi đúng một lần nhưng không mở lại nguồn cung | BIZ-066, BIZ-084–BIZ-086 |
| `B14` | Chính sách sau `A11` hoặc `B12` | Vé không còn quyền vào cửa do sự kiện bị hủy hoặc phát hành thất bại sau khi đã thu tiền | **Vé đã bị vô hiệu hóa** | Giữ vé trong lịch sử với trạng thái tương ứng, ẩn QR thô và từ chối check-in | BIZ-063–BIZ-066, BIZ-079, BIZ-084 |

### 5.1 Kết quả bị từ chối nhưng không đổi trạng thái

Các trường hợp sau phải trả kết quả rõ cho buyer, nhưng B4 không coi chúng là một sự kiện miền mới nếu không có trạng thái nghiệp vụ nào thay đổi. B4 không suy ra yêu cầu audit bền vững cho các từ chối này khi B3 hoặc quyết định nguồn chưa quy định:

- sự kiện không đủ điều kiện bán;
- lựa chọn trộn nhiều sector trong một đơn `SEAT_MAP`;
- ghế/số lượng không khả dụng;
- giới hạn mua theo tài khoản–sự kiện bị vượt;
- mã khuyến mãi không hợp lệ, chưa/đã hết hiệu lực hoặc làm số tiền cuối không lớn hơn 0;
- tài khoản đã dùng cùng mã khuyến mãi trước đó;
- đơn đã có một mã khuyến mãi mà buyer yêu cầu áp dụng mã thứ hai;
- yêu cầu đổi lựa chọn trong đơn đã tạo;
- khởi tạo thanh toán sau khi đơn đã hết hạn hoặc bị hủy;
- callback lặp mang cùng kết quả đã được xử lý.

### 5.2 Điểm cần giữ khi sang B5/B7

- `B01` là một kết quả nghiệp vụ gồm đơn và giữ chỗ cùng thời hạn; B4 không quyết định chúng nằm trong cùng aggregate, transaction hoặc service.
- Các sự kiện `B08`, `B09`, `B10`, `B12`, `B13` cùng chạm quy tắc “trả tài nguyên đúng một lần” nhưng có điều kiện trả khác nhau; `B14` tách riêng hậu quả mất quyền vào cửa khỏi việc trả tài nguyên. Cách hội tụ khi cùng nguyên nhân vô hiệu vé bị kích hoạt lặp vẫn là `B4-OPEN-07`, không phải bất biến đã chốt.
- Callback lặp và thanh toán trùng không đồng nghĩa: callback lặp có thể không phát sinh thêm khoản thu, còn thanh toán trùng có khoản tiền thừa phải hoàn.
- Gửi vé thất bại và phát hành vé thất bại là hai sự kiện khác bản chất.

## 6. Dòng thời gian C — Hoàn tiền, hủy sự kiện, đối soát và chi trả

| Mã | Tác nhân/nguồn kích hoạt | Lệnh hoặc điều kiện | Sự kiện miền/kết quả | Chính sách hoặc kết quả tiếp theo | Nguồn |
|---|---|---|---|---|---|
| `C01` | Chính sách sau `B10`, `B11`, `B12` hoặc lựa chọn từng khoản thu tại `C05` | Ghi nhận một khoản thu cần hoàn và nguyên nhân cụ thể | **Yêu cầu hoàn tiền đã được tạo hoặc tiếp tục** | Mỗi khoản thu chỉ có một yêu cầu hoàn logic: đã hoàn thành thì không tạo lại; đang xử lý hoặc thất bại còn có thể thử lại thì tiếp tục; chỉ tạo mới khi chưa tồn tại | BIZ-011–BIZ-014, BIZ-033, BIZ-101; B3 §4.3 |
| `C02` | Hệ thống | Gửi lệnh hoàn toàn bộ khoản cần hoàn | **Lệnh hoàn tiền đã được gửi tới cổng thanh toán** | Theo dõi trạng thái từng khoản thu độc lập | B3 §4.3 |
| `C03` | Cổng thanh toán | Xác nhận hoàn thành | **Hoàn tiền đã thành công** | Cập nhật khoản thu/yêu cầu hoàn, liên kết kết quả với đơn và cập nhật sổ đối soát; không tự suy ra việc trả tài nguyên hay vô hiệu vé nếu nguyên nhân gốc không yêu cầu | BIZ-011–BIZ-014, BIZ-059, BIZ-062–BIZ-065, BIZ-103–BIZ-105 |
| `C04` | Cổng thanh toán hoặc lỗi xử lý | Một lần xử lý hoàn chưa thành công | **Lần hoàn tiền đã thất bại** | Giữ yêu cầu hoàn ở trạng thái chưa hoàn tất để thử lại/điều tra; không đảo các khoản đã hoàn thành | B3 §4.4–§4.6 |
| `C05` | Chính sách sau `A11` | Khởi tạo xử lý hoàn cho một sự kiện bị hủy | **Xử lý hoàn của sự kiện đã được khởi tạo** | Chọn N đơn thuộc sự kiện có khoản thu hợp lệ được giữ lại vẫn còn số tiền phải hoàn; với từng khoản thu, tạo mới hoặc tiếp tục `C01`. Giao dịch thu thừa đi theo nhánh thanh toán trùng độc lập và không thuộc lô này; đây không phải thao tác hủy nhiều sự kiện | BIZ-014, BIZ-100–BIZ-102; B3 §4.4 |
| `C06` | Kết quả xử lý từng đơn trong `C05` | Cập nhật tiến độ lô | **Tiến độ hoàn của sự kiện đã được cập nhật** | Thể hiện số đơn chờ, thành công, thất bại; lỗi một đơn không làm mất dấu các đơn còn lại | B3 §4.4 |
| `C07` | Hệ thống | Tổng hợp các khoản bán hợp lệ và khoản đã hoàn; loại khoản thu trùng phải hoàn khỏi doanh thu hợp lệ | **Sổ cái đối soát chỉ đọc đã được cập nhật** | Tính doanh thu thực thu, phí nền tảng và số tiền dự kiến chi trả; admin không sửa trực tiếp số tiền | BIZ-013, BIZ-034–BIZ-036, BIZ-059; B3 §4.5 |
| `C08` | Hệ thống đánh giá điều kiện | Sự kiện đã kết thúc, không còn lần thanh toán/yêu cầu hoàn đang xử lý | **Sự kiện đã đủ điều kiện đối soát/chi trả** *(trạng thái suy ra)* | Cho phép admin đối chiếu báo cáo cổng thanh toán | BIZ-050–BIZ-053 |
| `C09` | Admin | Xác nhận đã kiểm tra báo cáo cổng thanh toán | **Đối soát đã được xác nhận** | Chờ việc chuyển tiền ngoài hệ thống và thao tác đánh dấu `PAID` | BIZ-053 |
| `C10` | Admin | Đánh dấu sau khi tiền đã được chuyển ngoài hệ thống | **Sự kiện đã được đánh dấu `PAID`** | Kết thúc vòng đời chi trả; không có lần chi trả thứ hai hoặc mở lại do sai lệch muộn | BIZ-037–BIZ-038, BIZ-054 |

### 6.1 Điểm cần giữ khi sang B5/B7

- Một sự kiện bị hủy làm phát sinh hoặc tiếp tục việc xử lý hoàn độc lập cho từng khoản thu hợp lệ còn số tiền phải hoàn; trạng thái tổng hợp không được che mất trạng thái từng đơn/yêu cầu. Một khoản thu chỉ có một yêu cầu hoàn logic, còn giao dịch thu thừa vẫn do nhánh thanh toán trùng xử lý độc lập và không thuộc lô hủy sự kiện.
- Hoàn tiền không đồng nghĩa trả tồn kho, giới hạn mua hoặc lượt khuyến mãi; phản ứng phụ thuộc nguyên nhân gốc.
- Sổ đối soát là kết quả đọc/tổng hợp. Việc nó được cập nhật bằng cách nào là quyết định thiết kế sau B11, không phải kết luận B4.
- `PAID` là dấu mốc ghi nhận thủ công một lần; chuyển tiền thật nằm ngoài hệ thống.

## 7. Dòng thời gian D — Organizer check-in trực tuyến

| Mã | Tác nhân/nguồn kích hoạt | Lệnh hoặc điều kiện | Sự kiện miền/kết quả | Chính sách hoặc kết quả tiếp theo | Nguồn |
|---|---|---|---|---|---|
| `D01` | Organizer | Quét QR cho sự kiện thuộc mình | **Yêu cầu check-in đã được tiếp nhận** *(dấu vết thao tác)* | Kiểm tra quyền sở hữu sự kiện, cửa sổ thời gian, sự kiện của vé và trạng thái vé | BIZ-067–BIZ-068, BIZ-077; B3 §5.3 |
| `D02` | Hệ thống | Mọi điều kiện check-in hợp lệ | **Vé đã được check-in** | Chuyển nguyên tử từ chưa sử dụng sang đã sử dụng; không cho thành công lần nữa | BIZ-067, BIZ-073–BIZ-074, BIZ-082, BIZ-087 |
| `D03` | Hệ thống | Một điều kiện check-in không hợp lệ | **Yêu cầu check-in đã bị từ chối** *(kết quả audit, không đổi trạng thái vé)* | Trả lý do phù hợp và giữ dấu vết; không tạo check-out hoặc hoàn tác | BIZ-067–BIZ-068, BIZ-077, BIZ-079, BIZ-082 |

### 7.1 Trường hợp từ chối phải phân biệt

- organizer không sở hữu sự kiện được chọn;
- `now < eventStartAt` hoặc `now > eventEndAt`;
- QR không xác định được vé;
- vé thuộc sự kiện khác;
- vé chưa phát hành, đã bị hủy/vô hiệu hoặc đã sử dụng.

Mất kết nối trước khi yêu cầu tới hệ thống là trạng thái cục bộ của mobile: ứng dụng báo chưa thể xác nhận và cho phép thử lại khi có kết nối. Trường hợp này không thuộc `D03` và không mặc nhiên có bản ghi audit phía server.

Không có danh tính attendee để đối chiếu. Người xuất trình QR hợp lệ được dùng vé; “không hỗ trợ chuyển nhượng” chỉ có nghĩa không có chức năng chuyển vé giữa tài khoản.

## 8. Năng lực hỗ trợ và quyền — đầu vào để B5 không bỏ sót

Mục này không tạo thêm quy trình chính ngoài B3. Nó ghi những năng lực hỗ trợ mà Tầng B yêu cầu B4/B5 phải nhìn thấy, đồng thời giữ rõ phần nào đã có bằng chứng và phần nào còn `OPEN`.

### 8.1 Hồ sơ tài khoản và quyền

| Dữ kiện đã có | Tác động lên bản đồ |
|---|---|
| Buyer tạo/thanh toán đơn và chỉ xem QR thuộc đơn của mình | Các lệnh ở dòng B cần định danh tài khoản và quyền sở hữu đơn |
| Organizer quản lý/công bố/hủy/check-in sự kiện thuộc mình và xem báo cáo đối soát của sự kiện | Các lệnh ở dòng A/D và truy vấn đối soát cần quan hệ organizer–sự kiện |
| Admin duyệt sự kiện, xác nhận hoặc từ chối yêu cầu hủy, chủ động hủy, đối soát và đánh dấu `PAID` | Các lệnh quản trị cần quyền cấp nền tảng và dấu vết người thao tác |
| Không mô hình hóa attendee định danh | Không tạo lifecycle hồ sơ attendee hoặc sự kiện “chuyển quyền sở hữu vé” |
| Keycloak được dùng cho quản lý danh tính và vòng đời tài khoản | B4 ghi nhận nguồn danh tính ở mức phạm vi; cơ chế đồng bộ, API, schema và hợp đồng vẫn chờ đúng gate sau |
| Một tài khoản có thể mang nhiều vai trò nghiệp vụ | B5 phải nhìn thấy ngôn ngữ quyền đa vai trò nhưng không được tự chốt bộ role hoặc thời điểm cấp role |
| Tài khoản admin đầu tiên do chủ đồ án tự tạo trong database, không có giao diện sản phẩm | B4 không tạo thêm luồng giao diện; từ “database” chưa được diễn giải thành kho dữ liệu kỹ thuật cụ thể |
| Không yêu cầu xác minh email/số điện thoại | Không tạo sự kiện hay nhánh lỗi xác minh trong phạm vi đồ án |
| Không hỗ trợ đăng nhập qua nhà cung cấp mạng xã hội | Không tạo nhánh đăng nhập xã hội hoặc sự kiện liên kết tài khoản ngoài trong phạm vi đồ án |
| Không xét chuyển quyền sở hữu sự kiện hoặc khóa tài khoản giữa chừng | Không tạo dòng thời gian cho hai trường hợp đã loại khỏi phạm vi |
| Tài khoản tự đăng ký nhận `BUYER`; bộ role gồm `BUYER`, `ORGANIZER` và `ADMIN` | B5 được dùng bộ role đầy đủ làm ngôn ngữ quyền, nhưng chưa suy ra cấu trúc token hay cách đồng bộ |
| Buyer nộp hồ sơ organizer `PENDING`; duyệt thành công sau khi Keycloak cấp role làm hồ sơ `ACTIVE` và giữ cả `BUYER`/`ORGANIZER` | B5 nhìn thấy vòng đời organizer tối thiểu và điều kiện hoàn tất duyệt; không mở quy trình chính thứ năm ở B3 |
| Từ chối organizer yêu cầu nhập/lưu lý do, chuyển hồ sơ `REJECTED` và không hỗ trợ gửi lại | Giữ nhánh từ chối ở mức hỗ trợ; không suy ra thêm màn hình ngoài phần đã được chốt |
| Không xây dựng thu hồi role; chỉ hồ sơ `ACTIVE` được công khai | Không tạo dòng thời gian thu hồi; truy vấn công khai phải tôn trọng điều kiện trạng thái nghiệp vụ |
| Mỗi sự kiện thuộc đúng một organizer | Các lệnh quản lý/check-in/đối soát phải kiểm tra quan hệ sở hữu, không chỉ kiểm tra role |

Các quyết định trên đóng phần nghiệp vụ tối thiểu của vòng đời tài khoản/organizer mà B4 cần để ánh xạ quyền. `B4-OPEN-01` chỉ còn tập trường của hồ sơ nghiệp vụ, cách đồng bộ/kho lưu kỹ thuật và nghĩa cụ thể của nơi tạo admin đầu tiên; các phần này chờ đúng gate sau và không chặn bản đồ sự kiện.

Nguồn trực tiếp của mục này là B2-v0.8, bốn quy trình B3-v0.8, `PRJ-003`, `BIZ-109`, `BIZ-111`, `BIZ-114`, `BIZ-119`, `BIZ-124`, `BIZ-129` và `BIZ-131`–`BIZ-141`; đây chưa phải mô hình phân quyền kỹ thuật ở B8/B13.

### 8.2 Chatbot hỗ trợ mua vé

- Việc hỏi/tìm kiếm sự kiện là truy vấn chỉ đọc, không tự tạo sự kiện miền.
- Chatbot có thể quan sát sự kiện đã công bố và trạng thái khả dụng được phép công khai.
- Khi buyer quyết định mua, mọi thay đổi trạng thái phải đi qua cùng lệnh/quy tắc ở dòng B; B4 không tạo một luồng đặt vé riêng cho chatbot.
- Chatbot không được dùng làm lý do để gộp hoặc tách context ở B4; B5 phải đánh giá từ ngôn ngữ, quyền và sự kiện đã có.

Nguồn phạm vi là mục “Ranh giới quyền của AI” trong `docs/boi-canh-va-mong-muon.md` và baseline quyền ở B2-v0.8; B4 không suy ra thêm API hay quyền ghi.

### 8.3 Trợ lý chẩn đoán sự cố

Hướng `structured logging → Drain → context → LLM API` và ranh giới chỉ đọc đã được xác nhận, nhưng workflow chi tiết vẫn để B16–B18. B4 chỉ ghi chuỗi ứng viên để B5 nhìn thấy ngôn ngữ và quyền khác biệt:

Ở B4/B5, **sự cố** là tình huống hệ thống không cung cấp hoặc có nguy cơ không cung cấp hành vi mong đợi và cần con người xác minh. Bốn lớp mục tiêu gồm: lỗi âm thầm, lỗi ngoại lệ ứng dụng, lỗi cơ sở dữ liệu và lỗi hạ tầng/tích hợp. Các thuật ngữ này đã được duyệt tại B2-v0.8; riêng T01–T04 vẫn là chuỗi sự kiện ứng viên vì workflow chẩn đoán cuối còn chờ B16–B19.

| Mã | Nguồn kích hoạt/lệnh ứng viên | Sự kiện ứng viên | Trạng thái bằng chứng | Giới hạn |
|---|---|---|---|---|
| `T01` | Người vận hành yêu cầu chẩn đoán một sự cố | **Yêu cầu chẩn đoán đã được tạo** | `CANDIDATE` | Cách chọn sự cố/trace đầu vào còn `OPEN` |
| `T02` | Chọn, liên kết, rút gọn và khử nhạy cảm dấu vết | **Context chẩn đoán đã được dựng** | `CANDIDATE` | Chưa chốt nguồn log, cửa sổ thời gian hoặc dữ liệu lưu |
| `T03` | Gọi LLM bằng context đã kiểm soát | **Tư vấn chẩn đoán đã được tạo** | `CANDIDATE` | Chỉ gồm nguyên nhân khả dĩ, bằng chứng và bước kiểm tra; không tự sửa |
| `T04` | Con người kiểm tra kết quả | **Tư vấn đã được xác minh hoặc bác bỏ** | `CANDIDATE` | Cách ghi phản hồi và bộ tiêu chí đánh giá chờ B19 |

Các sự kiện nghiệp vụ ở dòng A–D có thể tạo log/dấu vết làm đầu vào chẩn đoán, nhưng điều này **không** biến trợ lý thành chủ sở hữu các sự kiện đó và không cho trợ lý đường ghi ngược vào nghiệp vụ.

Nguồn trực tiếp là `PRJ-001`, `PRJ-002`, bối cảnh tính năng AI trong `docs/boi-canh-va-mong-muon.md` và phạm vi B16–B19 của Tầng B; T01–T04 vẫn là `CANDIDATE`, không phải quyết định kiến trúc hay hợp đồng.

### 8.4 Địa điểm, phân loại, tìm kiếm và theo dõi organizer

Các năng lực sau thuộc phạm vi sản phẩm ở mức tối giản, nhưng không tạo thêm quy trình chính hoặc bounded context mới chỉ vì đã được nêu tên:

| Năng lực | Nghĩa tối thiểu được chuyển tiếp sang B5 | Điều chưa được suy ra |
|---|---|---|
| Địa điểm | Một địa điểm có thể được dùng lại cho nhiều sự kiện | Quan hệ giữa sức chứa địa điểm và nguồn cung vé; schema hoặc nơi sở hữu sơ đồ ghế |
| Phân loại sự kiện | Dùng nhãn nhóm để mô tả và gom sự kiện cùng chủ đề | Cây phân cấp, quy trình quản trị hoặc quy tắc gắn nhiều phân loại |
| Tìm sự kiện | Buyer có thể tìm sự kiện đã công bố | Tiêu chí tìm, xếp hạng, chỉ mục hoặc công nghệ tìm kiếm |
| Theo dõi organizer | Buyer có thể ghi nhận việc theo dõi một organizer | Thông báo, bảng tin, cách lưu hay cách tính số người theo dõi |

Nguồn trực tiếp là `BIZ-125`, `BIZ-126`, `BIZ-127` và `BIZ-128`. B5 phải ánh xạ bằng ngôn ngữ hiện có; nếu ngôn ngữ không đủ thì giữ `OPEN`, không dùng frontend hoặc repo cũ làm lý do hình thành context.

### 8.5 Truy vết các chức năng giao diện không tạo thêm quy trình chính

| Chức năng nhìn thấy trên sản phẩm | Vị trí đã được B4 bao phủ | Lý do không tạo thêm dòng sự kiện |
|---|---|---|
| Organizer tạo và quản lý sự kiện | `A01`–`A06`, `A09`–`A11` | Các lệnh làm đổi trạng thái đã có trong dòng A |
| Organizer cấu hình sơ đồ ghế, sector, loại vé và nguồn cung | `A02`; điều kiện mua ở `B01` | Đây là cấu hình bán của sự kiện; không phải một vòng đời tách rời chỉ vì có màn hình riêng |
| Trang chủ, tìm kiếm, xem chi tiết sự kiện và danh sách quản lý | B4 §2, §8.4; dữ liệu công bố từ `A06` | Truy vấn chỉ đọc không tự tạo sự kiện miền |
| Dashboard và thống kê đọc | `C07`–`C10` đối với số liệu tài chính; các số đếm/hiển thị khác là truy vấn đọc | Chỉ thêm sự kiện miền nếu một lệnh nghiệp vụ làm thay đổi trạng thái, không thêm vì giao diện có biểu đồ |

Nguồn trình bày là `PRJ-006`. Mục này chứng minh độ bao phủ chức năng, không dùng giao diện để hình thành bounded context hoặc đơn vị triển khai.

## 9. Quan hệ sự kiện xuyên quy trình

| Sự kiện/kết quả nguồn | Chính sách phản ứng | Dòng nhận | Điều phải bảo vệ |
|---|---|---|---|
| Sự kiện đã được công bố và đang khả dụng để bán | Cho phép buyer/chatbot nhìn thấy thông tin bán; buyer có thể yêu cầu tạo đơn | B | Công bố không đồng nghĩa luôn mở bán |
| Đơn/giữ chỗ đã được tạo | Giữ nguồn cung và phần giới hạn mua theo cùng thời hạn của đơn/giữ chỗ | B | Không bán vượt, không vượt giới hạn, không giữ quá hạn |
| Khuyến mãi đã được áp dụng; lượt dùng khuyến mãi đã được giữ | Gắn lượt dùng đã giữ với cùng thời hạn của đơn | B | Không giữ lượt khi chưa áp dụng mã hợp lệ; không giữ quá hạn |
| Thanh toán đã được xác nhận | Chỉ giữ một kết quả thu hợp lệ và phát hành vé | B | Không phát hành trùng do callback lặp |
| Đơn hết hạn hoặc bị buyer hủy | Trả tài nguyên tạm giữ đúng một lần | B | Callback muộn không được trả lần hai |
| Thanh toán đến muộn, trùng hoặc phát hành lỗi | Tạo yêu cầu hoàn phù hợp nguyên nhân | C | Không hoàn trùng; không làm hỏng giao dịch hợp lệ |
| Yêu cầu hủy sự kiện đã bị từ chối | Giữ nguyên trạng thái sự kiện và không khởi động hoàn tiền do hủy | A | Từ chối yêu cầu không đồng nghĩa hủy sự kiện |
| Sự kiện đã bị hủy | Đóng bán, hủy đơn đang giữ, vô hiệu vé; xử lý từng khoản thu hợp lệ còn số tiền phải hoàn và loại giao dịch thu thừa khỏi lô | B, C, D | Không mở lại nguồn cung; một yêu cầu hoàn logic cho mỗi khoản thu |
| Vé đã được phát hành | Cho buyer xem/tải QR và cho phép check-in trong cửa sổ | D | Quyền QR và tối đa một check-in thành công |
| Hoàn tiền đã thành công | Cập nhật khoản thu/yêu cầu hoàn, liên kết kết quả với đơn và cập nhật sổ đối soát; hậu quả lên vé/tài nguyên vẫn theo nguyên nhân gốc | B, C, D | Không tạo trạng thái “đã hoàn” riêng cho vé hoặc làm mất hiệu lực vé hợp lệ khi chỉ hoàn khoản thu trùng |
| Vé đã bị vô hiệu hóa | Giữ lịch sử nhưng ẩn QR thô và từ chối check-in | B, D | Không xóa dấu vết và không để quyền vào cửa còn hiệu lực |
| Sự kiện đã kết thúc và không còn xử lý tiền treo | Mở điều kiện đối soát/chi trả | C | Chỉ một lần `PAID`, không có khoản giữ lại |
| Dấu vết vận hành đã được tạo | Có thể được chọn vào context chẩn đoán sau khi khử nhạy cảm | T | Trợ lý chỉ đọc, không sở hữu/ghi nghiệp vụ |

## 10. Bất biến ứng viên và hotspot cho B7/B9–B10

Đây là danh sách ứng viên/rủi ro nổi bật, không phải bản sao đầy đủ mọi quy tắc cấu hình đã có ở B2. B7 phải đọc trực tiếp B2/B3/B4, viết lại các bất biến cần bảo vệ với phạm vi aggregate rõ ràng; B9–B10 chuyển phần cạnh tranh/lỗi thành kịch bản chất lượng. B4 không gán chúng cho service hoặc schema.

| ID | Bất biến/hotspot | Bằng chứng hiện tại | Gate xử lý tiếp |
|---|---|---|---|
| `INV-01` | Một ghế định danh không thể thuộc hai giữ chỗ/đơn còn hiệu lực; số lượng khả dụng không được âm | B2 mục 3; B3 §3.2–§3.4 | B7, B9, B10 |
| `INV-02` | Một đơn thuộc đúng một sự kiện; với `SEAT_MAP`, mọi dòng thuộc cùng một sector | BIZ-001, BIZ-080 | B7 |
| `INV-03` | Một đơn có đúng một giữ chỗ và một mốc hết hạn chung | BIZ-029 | B7 |
| `INV-04` | Vé đang giữ và đã mua của một tài khoản không vượt giới hạn trong sự kiện | BIZ-025–BIZ-028, BIZ-075 | B7, B9 |
| `INV-05` | Một đơn chỉ giữ một kết quả thu tiền hợp lệ; khoản thu thừa phải được hoàn độc lập | BIZ-013, BIZ-032 | B7, B9, B10 |
| `INV-06` | Tồn kho, giới hạn mua và lượt khuyến mãi không được giải phóng/chốt hai lần do hết hạn, hủy hoặc callback lặp | BIZ-062, BIZ-070, BIZ-085–BIZ-086, BIZ-090–BIZ-091 | B7, B9, B10 |
| `INV-07` | Phát hành vé hoặc gửi vé bị thử lại không được tạo thêm quyền tham dự ngoài số vé đã mua | B3 §3.3–§3.4 | B7, B9, B10 |
| `INV-08` | Mỗi khoản thu đủ điều kiện có tối đa một yêu cầu hoàn tiền logic và một kết quả hoàn thành công; kích hoạt lặp phải hội tụ về yêu cầu hiện có | BIZ-100–BIZ-102; B3 §4.3–§4.7 | B7, B9, B10 |
| `INV-09` | Một vé có tối đa một check-in thành công, kể cả nhiều thiết bị quét đồng thời | BIZ-082, BIZ-087 | B7, B9, B10 |
| `INV-10` | Một sự kiện có tối đa một lần được đánh dấu `PAID` sau khi đủ điều kiện | BIZ-037–BIZ-038, BIZ-050–BIZ-054 | B7 |
| `INV-11` | Tỷ lệ phí dùng khi đối soát phải đúng giá trị đã được admin phê duyệt cho sự kiện và không bị đổi âm thầm sau phê duyệt | BIZ-034, BIZ-059, BIZ-142 | B7 xác định phạm vi aggregate và cách phát biểu bất biến; sở hữu dữ liệu chờ B12 |
| `HOT-01` | Hủy một sự kiện có thể lan tới N đơn/giữ chỗ/vé/yêu cầu hoàn với lỗi từng phần | BIZ-014, BIZ-084–BIZ-086, BIZ-100–BIZ-102 | B7, B9, B10; xem `B4-OPEN-06`; B11 mới xét phương án kiến trúc |
| `HOT-02` | Ranh giới bảo vệ đồng thời giữa nguồn cung, giữ chỗ và đơn chưa được xác định | `INV-01`, `INV-03`, `INV-06` | B7 làm rõ bất biến; B10 nêu ASR; xem `B4-OPEN-05`; B11-A mới tạo phương án |
| `HOT-03` | Thanh toán, phát hành vé và hoàn tiền có callback lặp/đến muộn/lỗi sau khi đã thu | BIZ-012–BIZ-013, BIZ-060–BIZ-065 | B7, B9, B10 |
| `HOT-04` | Check-in cạnh tranh trên cùng vé cần một quyết định nguyên tử để bảo vệ bất biến | BIZ-087 | B7, B9, B10 |

## 11. Sổ vấn đề `OPEN`

| ID | Vấn đề còn thiếu | Vì sao không tự chốt ở B4 | Chủ thể/gate cần xử lý | Có chặn B4 duyệt? |
|---|---|---|---|---|
| `B4-OPEN-01` | Tập trường tối thiểu của hồ sơ nghiệp vụ ứng dụng, cách đồng bộ/kho lưu kỹ thuật và nghĩa cụ thể của nơi tạo admin đầu tiên | Vòng đời nghiệp vụ, bộ role, điều kiện cấp role/công khai, từ chối và các trường hợp ngoài phạm vi đã được đóng bởi `BIZ-131`–`BIZ-141`; chi tiết dữ liệu/kỹ thuật chưa thuộc thẩm quyền B4 | Lê Văn Minh; trường nghiệp vụ ở B6/B8, đồng bộ/contract ở B11–B13 | Không; không được mở lại phần nghiệp vụ đã chốt hoặc dùng B4 để chốt schema |
| `B4-OPEN-02` | Cách khởi tạo yêu cầu chẩn đoán, chọn trace/sự cố, lưu phản hồi và tập ca đánh giá | PRJ-002 chỉ chốt phạm vi hỗ trợ; workflow cuối vẫn `OPEN` | Minh/Nhật; B16–B19, với quyền sơ bộ được xét ở B5 | Không chặn B4; không được biến T01–T04 thành quyết định cuối |
| `B4-OPEN-03` | Các mốc `saleStartAt`, `saleEndAt`, `eventStartAt`, `eventEndAt` được tính động hay phát thành sự kiện kỹ thuật | Đây là lựa chọn thiết kế/contract, không làm đổi quy tắc nghiệp vụ | Lê Văn Minh; B11/B13 | Không |
| `B4-OPEN-04` | Dữ liệu audit chi tiết cho yêu cầu check-in bị từ chối và thao tác quản trị | B3 chỉ yêu cầu đủ dấu vết, chưa chốt trường/payload/lưu giữ | Lê Văn Minh; Phạm Văn Tuyến rà nhu cầu hiển thị mobile; B8/B13/B16 | Không |
| `B4-OPEN-05` | Cách bảo vệ đồng thời `INV-01`, `INV-03`, `INV-06` khi có nhiều yêu cầu cạnh tranh | B4 chỉ xác định hotspot; chưa có aggregate/ASR và không được chọn vị trí triển khai | Lê Văn Minh; B7/B9/B10, sau đó B11-A | Không; đây là đầu vào bắt buộc cho B7/B10 |
| `B4-OPEN-06` | Cách theo dõi, thử lại và kết thúc xử lý N yêu cầu hoàn theo từng đơn khi hủy sự kiện | B3 chốt kết quả nghiệp vụ nhưng chưa chốt ngưỡng/thời gian/chính sách vận hành | Lê Văn Minh; B7/B9/B10; cơ chế kiến trúc chờ B11 | Không; giữ nguyên hotspot |
| `B4-OPEN-07` | Cùng một nguyên nhân vô hiệu vé bị kích hoạt lặp có cần một bất biến idempotency riêng hay được hấp thụ bởi chuyển trạng thái vé | B3/BIZ-063–BIZ-066 mới chốt hậu quả mất quyền vào cửa, chưa chốt quy tắc hội tụ cho kích hoạt lặp | Lê Văn Minh; xác nhận nhu cầu nghiệp vụ ở B7, chuyển thành kịch bản lỗi ở B9/B10 nếu cần | Không; không được trình bày như yêu cầu đã xác nhận |
| `B4-OPEN-08` | Quyền sở hữu dữ liệu vật lý của tỷ lệ phí nền tảng đặt ở đâu | Tính cố định sau phê duyệt đã được chốt tại `BIZ-142`; `BIZ-123` giữ riêng câu hỏi sở hữu dữ liệu | B12 sau B11-C | Không; B5/B7 được dùng nghĩa cố định nhưng không được chốt schema hoặc nơi sở hữu |

## 12. Phép tự kiểm và điều kiện duyệt

- [x] Bốn quy trình B3 đều có chuỗi sự kiện thành công và thất bại tương ứng.
- [x] Hủy sự kiện được phân biệt với hủy nhiều sự kiện và với hoàn kỹ thuật của một đơn.
- [x] Callback lặp được phân biệt với thanh toán trùng.
- [x] Phát hành vé thất bại được phân biệt với gửi vé thất bại.
- [x] Buyer/attendee, quyền QR và organizer check-in không bị thay đổi bởi phần bổ sung ở B2-v0.8.
- [x] Cấu hình khuyến mãi và nhánh từ chối yêu cầu hủy không bị thay đổi bởi phần bổ sung ở B3-v0.8.
- [x] Chuỗi `C05 → C01` theo BIZ-100–BIZ-102 phân biệt khoản thu hợp lệ còn phải hoàn với giao dịch thu thừa và hội tụ kích hoạt lặp về một yêu cầu hoàn logic.
- [x] Không suy rộng yêu cầu audit cho mọi từ chối buyer hoặc idempotency vô hiệu vé khi chưa có quyết định nguồn; phần còn thiếu được giữ `OPEN`.
- [x] Nhánh trợ lý dùng các thuật ngữ sự cố đã duyệt ở B2-v0.8; T01–T04 vẫn là sự kiện ứng viên và không tiền-chốt detector hay kiến trúc.
- [x] Chatbot, tài khoản, địa điểm, phân loại, tìm kiếm, theo dõi organizer và trợ lý chẩn đoán không bị bỏ khỏi đầu vào B5.
- [x] Không có service, schema, Saga, ADR, topic, API hoặc vị trí aggregate được chốt.
- [x] Lê Văn Minh đã duyệt B2-v0.8 và B3-v0.8 theo đúng thứ tự phase gate ngày 2026-08-21.
- [x] Lê Văn Minh đã rà phần bổ sung, quan hệ nhân quả và danh sách `OPEN` của B4-v0.8.
- [x] Lê Văn Minh xác nhận B4-v0.8 `APPROVED` trước khi B5 dùng làm đầu vào đã duyệt.

## 13. Phần dùng cho báo cáo

Sau khi được duyệt, báo cáo có thể dùng:

- mạch sự kiện chính từ tạo sự kiện đến check-in;
- một bảng rút gọn về các sự kiện quan trọng và chính sách phản ứng;
- các điểm nóng nhất quán như giữ chỗ/tồn kho, callback thanh toán, hoàn tiền khi hủy sự kiện và check-in đồng thời;
- giải thích rằng bản đồ sự kiện là đầu vào lập luận ranh giới miền, không phải sơ đồ service.

Không đưa nguyên trạng mã `A01`–`T04`, trạng thái governance, sổ `OPEN` hoặc toàn bộ bảng chi tiết vào báo cáo nếu chúng không giúp giải thích một quyết định. Tên sự kiện trong B4 không được trình bày như hợp đồng message đã hiện thực trước khi B13 chốt.
