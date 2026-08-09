# B1 — Khảo sát đối sánh các luồng bán vé công khai

- **Ngày quan sát:** 2026-08-09
- **Phạm vi:** Luồng người mua và tài liệu trợ giúp/chính sách công khai
- **Hệ thống:** Ticketbox, TicketGo, Eventbrite
- **Trạng thái:** Bản nháp có bằng chứng; không thực hiện giao dịch trả phí

## Nhãn sử dụng

- **BÁO CÁO:** Phương pháp khảo sát, bảng quan sát–giới hạn, kết quả đối sánh và đoạn tổng hợp cuối tài liệu.
- **NỘI BỘ:** URL sự kiện cụ thể có thể thay đổi và ghi chú thao tác chi tiết.
- **CẦN XÁC NHẬN:** Nhóm có muốn bổ sung ảnh chụp thủ công của 1–2 luồng hay chỉ dùng URL/tài liệu chính thức làm bằng chứng.

## 1. Mục đích và phương pháp

Khảo sát nhằm nhận diện các trạng thái và quy tắc nghiệp vụ nhìn thấy từ phía người mua, không nhằm suy đoán kiến trúc của hệ thống thương mại. Chỉ sử dụng trang công khai, tài liệu trợ giúp và chính sách chính thức. Không đăng nhập, không nhập dữ liệu cá nhân, không khởi tạo thanh toán và không truy cập màn hình quản trị/nhà tổ chức cần quyền đặc biệt.

Ba hệ thống được chọn vì Ticketbox và TicketGo phản ánh ngữ cảnh bán vé tại Việt Nam, còn Eventbrite cung cấp tài liệu công khai tương đối đầy đủ để đối chiếu các trạng thái checkout, chỗ ngồi, vé điện tử, check-in và hoàn tiền.

## 2. Ticketbox

### 2.1 Điều quan sát được

| Luồng | Quan sát | Bằng chứng | Giới hạn |
|---|---|---|---|
| Tìm kiếm/danh sách | Trang chủ có ô tìm kiếm, nhóm thể loại, danh sách sự kiện và lối vào “Vé của tôi” | [Trang chủ Ticketbox](https://ticketbox.vn/) | Không đánh giá xếp hạng hoặc cá nhân hóa |
| Chi tiết/loại vé | Trang sự kiện hiển thị thời gian, địa điểm, giá từ, các hạng vé và trạng thái “Hết vé” | [The Starry: VOCAL NIGHT](https://ticketbox.vn/the-starry-vocal-night-26409) | Sự kiện/giá có thể thay đổi sau ngày quan sát |
| Giữ chỗ | Quy định của sự kiện công bố “Thời gian giữ Vé: 15 PHÚT/lượt” | [Trang sự kiện](https://ticketbox.vn/the-starry-vocal-night-26409) | Chỉ quan sát quy tắc công bố; không quan sát bộ đếm sau đăng nhập |
| Giới hạn mua | Sự kiện công bố tối đa 2 vé cho một tài khoản và một lần mua | [Trang sự kiện](https://ticketbox.vn/the-starry-vocal-night-26409) | Là quy tắc của sự kiện khảo sát, không khẳng định áp dụng toàn nền tảng |
| Bắt đầu mua | Chọn “Mua vé ngay” mở yêu cầu đăng nhập/đăng ký | Quan sát giao diện ngày 2026-08-09 | Dừng tại đây; không nhập tài khoản |
| Phát hành vé | Sau giao dịch thành công, mã QR được gửi tới email người mua; điều khoản cũng nêu vé điện tử có mã vạch/QR | [Trang sự kiện](https://ticketbox.vn/the-starry-vocal-night-26409), [Điều khoản khách hàng, trang 4](https://static.ticketbox.vn/site/global/content/file_pdf/6_Ticketbox_Dieu_khoan_su_dung_ap_doi_voi_Khach_hang_2023.pdf) | Không quan sát email/vé thật |
| Check-in trùng | Quy định sự kiện nêu nếu nhiều người dùng cùng mã QR thì người check-in đầu tiên được chấp nhận | [Trang sự kiện](https://ticketbox.vn/the-starry-vocal-night-26409) | Đây là quy tắc công bố; không chứng minh cách bảo đảm nguyên tử hoặc đồng bộ bên trong |
| Đổi/hoàn | Sự kiện khảo sát nêu vé không đổi/trả; điều khoản chung nêu vé đã mua không được trao đổi, thay đổi hoặc hủy | [Trang sự kiện](https://ticketbox.vn/the-starry-vocal-night-26409), [Điều khoản khách hàng](https://static.ticketbox.vn/site/global/content/file_pdf/6_Ticketbox_Dieu_khoan_su_dung_ap_doi_voi_Khach_hang_2023.pdf) | Ngoại lệ do hủy sự kiện hoặc pháp luật cần xem theo chính sách cụ thể |

### 2.2 Suy luận được phép và không được phép

- **Được phép:** miền nghiệp vụ có khái niệm giữ vé có thời hạn, giới hạn số lượng mua, các trạng thái còn/hết vé, phát hành QR sau thanh toán và nguyên tắc chỉ chấp nhận lần sử dụng đầu tiên.
- **Không được phép:** khẳng định Ticketbox dùng Redis, khóa phân tán, Saga, loại cơ sở dữ liệu hoặc thuật toán chống check-in trùng nào.

## 3. TicketGo

| Luồng | Quan sát từ tài liệu chính thức | Bằng chứng | Giới hạn |
|---|---|---|---|
| Tìm sự kiện/chi tiết | Người mua vào trang chi tiết rồi chọn nút mua; FAQ mô tả chọn loại vé, vị trí, ngày và giờ | [Hướng dẫn đặt vé](https://www.ticketgo.vn/page/huong-dan-dat-ve), [FAQ](https://ticketgo.vn/page/cac-cau-hoi-thuong-gap) | Không khảo sát thuật toán tìm kiếm |
| Chọn vé/checkout | Người mua điền họ tên, điện thoại, email; chọn số lượng, mã giảm giá và phương thức thanh toán | [Hướng dẫn đặt vé](https://www.ticketgo.vn/page/huong-dan-dat-ve) | Không gửi biểu mẫu thật |
| Thanh toán | Tài liệu liệt kê thẻ, ngân hàng điện tử, chuyển khoản, ví và QR; có hướng dẫn liên hệ hỗ trợ khi thẻ không thanh toán được | [Hướng dẫn đặt vé](https://www.ticketgo.vn/page/huong-dan-dat-ve), [FAQ](https://ticketgo.vn/page/cac-cau-hoi-thuong-gap) | Không kiểm tra từng phương thức còn hoạt động ở mọi sự kiện |
| Hoàn thành đơn | Sau các bước thanh toán, hệ thống hiển thị chi tiết đơn và gửi thông tin qua email | [Hướng dẫn đặt vé](https://www.ticketgo.vn/page/huong-dan-dat-ve) | Không quan sát trạng thái trung gian hoặc thời gian chờ |
| Nhận vé/check-in | Sau thanh toán thành công, vé điện tử được gửi qua email; tùy sự kiện có thể dùng mã vé hoặc đổi sang vé giấy/vòng tay | [Hướng dẫn đặt vé](https://www.ticketgo.vn/page/huong-dan-dat-ve), [Điều khoản khách hàng](https://ticketgo.vn/page/chinh-sach-dieu-khoan-su-dung-cho-khach-hang) | Không quan sát xử lý QR trùng |
| Hủy/hoàn | Điều khoản nêu chính sách đổi trả/hoàn tiền do ban tổ chức quy định theo sự kiện; nếu sự kiện bị dời/hủy do ban tổ chức, người tham gia có thể yêu cầu hoàn | [Điều khoản khách hàng](https://ticketgo.vn/page/chinh-sach-dieu-khoan-su-dung-cho-khach-hang) | Không suy ra quy trình đối soát hoặc hoàn tiền nội bộ |

Không quan sát được công khai thời gian giữ chỗ, phản ứng khi hai người chọn cùng ghế và xử lý callback thanh toán lặp.

## 4. Eventbrite

| Luồng | Quan sát từ tài liệu chính thức | Bằng chứng | Giới hạn |
|---|---|---|---|
| Tìm/đăng ký | Người mua tìm sự kiện, chọn vé, điền thông tin, chọn thanh toán và nhận trang/email xác nhận | [Checkout on Eventbrite](https://www.eventbrite.com/help/en-us/articles/333111/) | Không thực hiện checkout thật |
| Hết vé | Tài liệu nêu sự kiện “Sold out” hoặc “Unavailable” không thể đăng ký tại thời điểm đó | [Checkout on Eventbrite](https://www.eventbrite.com/help/en-us/articles/333111/) | Không tạo tình huống cạnh tranh |
| Chỗ ngồi/tạm giữ | Tài liệu reserved seating phân biệt ghế sold, held, available; đơn pending sẽ trả ghế về khả dụng nếu không hoàn tất | [Manage your reserved seating event](https://www.eventbrite.com/help/en-us/articles/216108/) | Nguồn là tài liệu công khai cho organizer, không phải ảnh luồng buyer |
| Nhận vé | Vé có thể truy cập từ tài khoản/web/app và email xác nhận | [Find your tickets](https://www.eventbrite.com/help/en-us/articles/319355/where-are-my-tickets/) | Có thể cần tài khoản/email đúng với đơn |
| Check-in trùng | Vé mặc định có QR; ứng dụng organizer hiển thị lỗi nếu vé đã được dùng | [Prevent attendees from sharing tickets](https://www.eventbrite.com/help/en-us/articles/308105/how-can-i-prevent-attendees-from-sharing-the-same-ticket/) | Không chứng minh cơ chế đồng bộ hoặc nhất quán nội bộ |
| Hoàn tiền | Organizer có thể đặt chính sách nhưng phải đáp ứng các trường hợp tối thiểu; sự kiện bị hủy phải hoàn cho người mua | [Organizer Refund Policy Requirements](https://www.eventbrite.com/help/en-us/articles/827759/), [Cancelled Event Policy](https://www.eventbrite.com/help/en-us/articles/724340/) | Chính sách áp dụng cho Eventbrite, không mặc nhiên trở thành yêu cầu của FlashTicket |

## 5. Đối sánh và hàm ý cho phân tích miền

| Khái niệm nhìn thấy | Ticketbox | TicketGo | Eventbrite | Hàm ý cần phân tích, chưa phải quyết định thiết kế |
|---|---|---|---|---|
| Khả dụng/hết vé | Có | Có qua chọn vé | Có | Tồn kho/khả dụng phải có nguồn quyết định rõ |
| Giữ chỗ/pending | Công bố 15 phút ở sự kiện mẫu | Không quan sát | Có trạng thái held/pending trong tài liệu | Cần xác định vòng đời giữ chỗ và hết hạn |
| Thanh toán → phát hành vé | QR sau giao dịch thành công | Vé email sau thanh toán thành công | Xác nhận rồi truy cập vé | Thanh toán và phát hành vé là hai mốc nghiệp vụ cần phối hợp |
| Chống sử dụng vé trùng | Người đầu tiên được chấp nhận | Không quan sát | Quét vé đã dùng trả lỗi | Check-in phải có bất biến “mỗi vé chỉ thành công một lần” |
| Chính sách hoàn | Sự kiện mẫu không hoàn | Phụ thuộc ban tổ chức | Organizer linh hoạt trong giới hạn nền tảng | Cần chốt H1: nền tảng áp đặt hay organizer cấu hình |

## 6. Kết luận B1 và nội dung dùng cho báo cáo

### BÁO CÁO — Đoạn tổng hợp có thể sử dụng

Khảo sát ba nền tảng bán vé cho thấy luồng người mua thường đi qua các mốc tách biệt: xem khả dụng, chọn loại vé hoặc chỗ ngồi, giữ/chờ hoàn tất đơn, thanh toán, nhận vé điện tử và check-in. Các giao diện và tài liệu công khai cũng thể hiện những trạng thái lỗi hoặc biên như hết vé, đơn chưa hoàn tất, thanh toán không thành công, vé không nhận được qua email, vé đã sử dụng và yêu cầu hoàn tiền. Đặc biệt, Ticketbox công bố thời hạn giữ vé ở một sự kiện mẫu và nguyên tắc chỉ người quét mã đầu tiên được chấp nhận; Eventbrite mô tả ghế tạm giữ được trả lại khi đơn không hoàn tất và cảnh báo khi quét vé đã dùng. Những quan sát này xác nhận các bất biến về tồn kho, vòng đời đơn và sử dụng vé là vấn đề nghiệp vụ có thật. Tuy nhiên, chúng không cung cấp bằng chứng về công nghệ hoặc kiến trúc nội bộ của các nền tảng, nên đồ án chỉ dùng chúng để xác định trạng thái/nguyên tắc cần phân tích, không dùng để sao chép kiến trúc.

### NỘI BỘ — Điều chưa được tuyên bố

- Không kết luận nền tảng nào “tốt hơn”.
- Không kết luận cơ chế bên trong của bất kỳ nền tảng nào.
- Không coi quy định của một sự kiện là chính sách toàn nền tảng.
- Không tuyên bố đã kiểm thử thanh toán, cạnh tranh ghế hoặc check-in thật.

### CẦN XÁC NHẬN/BỔ SUNG

Nếu nhóm muốn tăng bằng chứng trực quan, mỗi thành viên có thể chụp 1–2 ảnh giao diện công khai theo đúng URL và ngày quan sát. Không cần mua vé hoặc xin quyền organizer/admin.
