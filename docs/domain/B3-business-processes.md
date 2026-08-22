# B3 — Quy trình nghiệp vụ

- Phiên bản: `B3-v0.10`
- Trạng thái: `APPROVED`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-08-22, sau `B2-v0.10` (bản `B3-v0.9` được duyệt ngày 2026-08-21)
- Đầu vào và phiên bản: `docs/glossary.md` — `B2-v0.10`, `APPROVED` ngày 2026-08-22; `docs/project/decision-register.md` — dùng đúng các ID được dẫn tại từng quy trình và đọc trạng thái riêng của mỗi dòng
- Phân lớp: `FORMATION`

> **Vì sao có `B3-v0.10`:** bản này **không đổi nội dung nghiệp vụ** so với `B3-v0.9`. `B2-v0.10` chỉ bổ sung mục từ **Yêu cầu hủy sự kiện** cho khái niệm mà B3 §2 vốn đã mô tả, không sửa nghĩa nào đang dùng ở đây. Tài liệu đã trở lại `REVIEW_READY` vì quy tắc chuỗi `B2 → B3 → B4 → B5` không cho một tạo tác giữ `APPROVED` khi đầu vào bắt buộc chưa được duyệt lại, đúng như vòng bỏ `SUPER_ADMIN` ngày 2026-08-21, rồi được Lê Văn Minh duyệt lại ngày 2026-08-22.

## 1. Mục đích và giới hạn

Tài liệu mô tả hành vi nghiệp vụ đầu-cuối để ba thành viên thống nhất tác nhân, điều kiện, đường đi chính và nhánh lỗi trước khi lập dòng thời gian sự kiện miền ở B4. Đây chưa phải thiết kế đơn vị triển khai, cơ sở dữ liệu, hợp đồng tích hợp hoặc cơ chế giao dịch phân tán. Các luồng phối hợp chỉ được mô tả theo kết quả nghiệp vụ; việc có dùng Saga hay mẫu kiến trúc nào thuộc B10/B11.

Phạm vi gồm bốn quy trình:

1. Tạo, duyệt, công bố, mở bán và hủy sự kiện.
2. Cấu hình nguồn cung, chọn vé, giữ chỗ, tạo/hủy đơn, thanh toán và nhận vé.
3. Hoàn tiền, hủy một sự kiện, đối soát và chi trả.
4. Organizer check-in trực tuyến cho sự kiện của mình.

Các năng lực tài khoản, địa điểm, phân loại, tìm kiếm và theo dõi organizer được giữ ở mức hỗ trợ tối thiểu theo B2-v0.9. B3 không mở thêm một quy trình đầu-cuối chỉ để mô tả chi tiết các năng lực này; B4 phải chuyển tiếp chúng sang B5 mà không biến phần còn `OPEN` hoặc quyết định kỹ thuật chờ gate sau thành quy tắc đã chốt.

## 2. Quy trình 1 — Quản lý vòng đời sự kiện bán vé

### 2.1 Tác nhân

- Nhà tổ chức: tạo, sửa, cấu hình khuyến mãi, gửi duyệt, công bố và yêu cầu hủy sự kiện của mình.
- Quản trị viên: duyệt/từ chối sự kiện cùng cấu hình thương mại, nhập tỷ lệ phí nền tảng và xác nhận, từ chối hoặc chủ động hủy trong phạm vi cho phép; không có giao diện CRUD khuyến mãi riêng.
- Người mua: xem sự kiện đã công bố và giao dịch khi đang mở bán.

### 2.2 Tiền điều kiện

- Nhà tổ chức có quyền quản lý sự kiện.
- Một sự kiện biểu diễn đúng một lần tổ chức, có `eventStartAt`, `eventEndAt`, địa điểm và một cửa sổ bán chung.
- Cửa sổ bán thỏa `saleStartAt < saleEndAt <= eventEndAt`.
- Sự kiện chọn đúng một `salesMode`: `QUANTITY` hoặc `SEAT_MAP`.
- Giá mọi loại vé phải lớn hơn 0; giới hạn mua là trường bắt buộc organizer phải nhập. Giá trị hợp lệ chi tiết của giới hạn mua chưa được suy rộng ngoài quyết định nguồn.

### 2.3 Luồng chính

1. Nhà tổ chức tạo sự kiện ở trạng thái `DRAFT`, nhập nội dung, thời gian, địa điểm, cửa sổ bán, giới hạn mua và `salesMode`. Khi còn `DRAFT`, sự kiện chưa công khai và chưa được bán vé.
2. Nhà tổ chức cấu hình loại vé, nguồn cung và khuyến mãi của sự kiện khi sự kiện còn `DRAFT`. Khuyến mãi có hiệu lực tự động theo `startAt`/`endAt`; không có thao tác bật, tạm dừng hoặc vô hiệu hóa thủ công.
3. Khi gửi duyệt, sự kiện chuyển sang `PENDING_APPROVAL`; cấu hình thương mại — gồm khuyến mãi — và `salesMode` bị khóa.
4. Quản trị viên kiểm duyệt nội dung và cấu hình thương mại. Nếu chấp thuận, admin nhập tỷ lệ phần trăm phí nền tảng theo thỏa thuận đã hoàn tất ngoài hệ thống, tỷ lệ này được cố định và sự kiện chuyển sang `APPROVED`.
5. Nhà tổ chức chủ động công bố sự kiện `APPROVED`; sự kiện chuyển sang `PUBLISHED`. Công bố chỉ làm sự kiện hiển thị, không thay đổi cửa sổ bán; lặp lại cùng yêu cầu công bố không tạo thêm tác dụng phụ.
6. Hệ thống cho phép mua khi sự kiện đã duyệt, đã công bố, chưa hủy và thời điểm hiện tại thuộc cửa sổ bán. Nếu `saleEndAt` muộn hơn `eventStartAt`, việc bán tiếp trong lúc sự kiện diễn ra là chủ ý nghiệp vụ.

### 2.4 Nhánh lỗi và biên

- Nếu admin từ chối hoặc trả lại trong bước duyệt, sự kiện về `DRAFT`; không tồn tại trạng thái sự kiện bền vững `REJECTED`. Organizer được sửa nội dung, cấu hình thương mại, khuyến mãi, `salesMode` rồi gửi duyệt lại.
- Khi đổi `salesMode` ở `DRAFT`, organizer phải xác nhận xóa cấu hình không tương thích; hệ thống không tự chuyển đổi cấu hình.
- Organizer được hủy trực tiếp khi chưa có đơn đã thu tiền. Khi đã có đơn thu tiền, organizer chỉ gửi yêu cầu hủy và admin quyết định xác nhận hoặc từ chối. Nếu từ chối, **yêu cầu hủy** chuyển `REJECTED`, lý do từ chối là tùy chọn, trạng thái sự kiện không đổi và luồng hoàn tiền do hủy không được khởi động. Khi hủy hợp lệ, sự kiện chuyển `CANCELLED`. Admin có thể chủ động hủy khi cần.
- Hủy sự kiện và hoàn tiền hàng loạt phát sinh do hủy sự kiện chỉ thuộc phạm vi trước `eventStartAt`. Điều kiện “trước check-in đầu tiên” không tạo thêm ràng buộc độc lập vì check-in chỉ hợp lệ từ `eventStartAt`.
- Ba trường hợp hoàn kỹ thuật của một đơn — thanh toán đến muộn, phát hành vé thất bại sau thu tiền và thanh toán trùng — không bị chặn chỉ vì event đã bắt đầu.
- Không hỗ trợ thao tác hủy đồng thời nhiều sự kiện.

### 2.5 Hậu điều kiện

- Sự kiện được công bố có thể hiển thị độc lập với trạng thái mở bán.
- Sự kiện bị hủy ngừng bán ngay; đơn, giữ chỗ, vé và khoản thu liên quan được chuyển sang Quy trình 3.
- Cấu hình đã gửi duyệt không bị thay đổi ngoài vòng quay lại `DRAFT`.

### 2.6 Dữ liệu nghiệp vụ liên quan

Sự kiện bán vé, trạng thái sự kiện, thời gian/địa điểm, cửa sổ bán, `salesMode`, loại vé, sector/ghế, giới hạn mua, khuyến mãi và thời gian hiệu lực, tỷ lệ phí nền tảng, yêu cầu hủy và kết quả xử lý yêu cầu.

### 2.7 Quyết định nguồn

`BIZ-004`–`BIZ-010`, `BIZ-015`–`BIZ-021`, `BIZ-025`, `BIZ-034`, `BIZ-035`, `BIZ-058`, `BIZ-083`, `BIZ-088`, `BIZ-089`, `BIZ-092`–`BIZ-099`, `BIZ-142`–`BIZ-145`.

## 3. Quy trình 2 — Chọn vé, giữ chỗ, thanh toán và nhận vé

### 3.1 Tác nhân

- Nhà tổ chức: cấu hình sector, loại vé, nguồn cung và khuyến mãi cho sự kiện của mình.
- Người mua: chọn vé, áp dụng mã, tạo/hủy đơn, thanh toán và nhận vé.
- Cổng thanh toán: tiếp nhận lần thử và gửi kết quả có thể lặp hoặc đến muộn.

### 3.2 Tiền điều kiện

- Sự kiện đang đủ điều kiện mở bán theo Quy trình 1.
- Với `QUANTITY`, người mua chọn loại vé và số lượng, không chọn sơ đồ.
- Với `SEAT_MAP`, sơ đồ có thể trộn sector `SEATED` và `STANDING`; một sector có thể có nhiều loại vé, nhưng một đơn chỉ chứa lựa chọn thuộc một sector.
- Sector `SEATED` quản lý từng ghế định danh; sector `STANDING` quản lý số lượng trong khu.
- Một đơn thuộc đúng một sự kiện.
- Một tài khoản không được vượt giới hạn mua toàn sự kiện khi cộng vé đang giữ và vé đã mua. Quy tắc này không ngăn cùng một người sử dụng nhiều tài khoản.
- Khuyến mãi đã được organizer cấu hình cho đúng sự kiện khi sự kiện ở `DRAFT`; đến lúc mua, trạng thái hiệu lực được suy ra từ `startAt`/`endAt` thay vì một nút bật/tạm dừng thủ công.

### 3.3 Luồng chính

1. Người mua chọn một hoặc nhiều dòng vé hợp lệ của cùng một sự kiện. Với sơ đồ, mọi lựa chọn phải thuộc cùng một sector.
2. Hệ thống kiểm tra đồng thời trạng thái mở bán, tồn kho/ghế, giới hạn mua và điều kiện loại vé.
3. Nếu hợp lệ, hệ thống tạo một đơn với đúng một giữ chỗ và một thời hạn chung cho toàn bộ dòng vé; tồn kho và phần giới hạn mua tương ứng được cam kết tạm thời.
4. Người mua có thể áp dụng tối đa một mã khuyến mãi thuộc đúng sự kiện của đơn. Chuỗi mã duy nhất trong phạm vi sự kiện; hệ thống tự xác định hiệu lực theo `startAt`/`endAt`, đồng thời kiểm tra loại giảm, tổng lượt, một lần/tài khoản/mã, không cộng dồn và số tiền cuối lớn hơn 0. Lượt dùng được giữ cùng thời hạn đơn.
5. Người mua khởi tạo một lần thanh toán. Nếu lần đó thất bại và đơn còn hiệu lực, người mua có thể tạo lần thử mới tuần tự.
6. Khi nhận callback/IPN, hệ thống xác minh đúng đơn, lần thử và số tiền. Callback lặp không tạo tác dụng phụ lặp; một đơn chỉ giữ một kết quả thu tiền hợp lệ.
7. Sau xác nhận hợp lệ, hệ thống chốt phần nguồn cung, giới hạn và khuyến mãi đã giữ rồi phát hành các vé riêng lẻ.
8. Buyer đã đăng nhập có thể xem/tải QR của vé đã phát hành thuộc đơn của chính mình; hệ thống đồng thời cố gắng gửi thông tin vé/QR tới buyer. Organizer và admin không có chức năng liệt kê hoặc tải QR thô của buyer.

### 3.4 Nhánh lỗi và biên

- Nếu ghế/số lượng không còn khả dụng hoặc giới hạn mua bị vượt, không tạo giữ chỗ.
- Không cho đổi lựa chọn trong đơn đã tạo; muốn vé khác, người mua tạo đơn mới.
- Buyer có thể hủy toàn bộ đơn của chính mình khi đơn còn giữ chỗ và chưa có thanh toán thành công. Hủy lặp là idempotent; giữ chỗ, nguồn cung, phần giới hạn mua và lượt khuyến mãi được trả đúng một lần.
- Khi đơn hết hạn, các tài nguyên tạm giữ cũng được trả đúng một lần. Sau khi đơn hết hạn hoặc bị hủy, không cho tạo lần thanh toán mới.
- Callback hợp lệ đến sau khi đơn hết hạn hoặc bị hủy — kể cả hủy do sự kiện bị hủy — tạo trường hợp thanh toán đến muộn: hoàn toàn bộ khoản thu; không giải phóng giữ chỗ, tồn kho, giới hạn hay lượt khuyến mãi lần thứ hai.
- Nếu phát hành vé thất bại sau thu tiền, vô hiệu hóa mọi vé dở dang, trả lượt khuyến mãi đúng một lần và tạo yêu cầu hoàn toàn bộ. Chỉ trả tồn kho và giới hạn mua nếu sự kiện vẫn đủ điều kiện bán.
- Nếu cùng nghĩa vụ bị thu nhiều lần, giữ một giao dịch hợp lệ và hoàn toàn bộ từng giao dịch thừa; tồn kho, giới hạn và vé của đơn hợp lệ không thay đổi.
- Gửi thông tin vé/QR thất bại không phải lỗi phát hành, không kích hoạt hoàn tiền; vé đã phát hành vẫn hợp lệ và buyer có thể tải/gửi lại.
- Vé đã bị hủy hoặc vô hiệu vẫn hiện trong lịch sử buyer với trạng thái tương ứng nhưng không cho xem hoặc tải QR thô. Hoàn tiền được ghi trên khoản thu/yêu cầu hoàn và liên kết kết quả với đơn, không tạo trạng thái “đã hoàn” riêng cho vé.

### 3.5 Hậu điều kiện

- Thành công: đơn có một khoản thu hợp lệ và các vé riêng lẻ tương ứng; nguồn cung, giới hạn mua và lượt khuyến mãi được chốt.
- Hết hạn/hủy trước thanh toán: không có vé được phát hành và mọi tài nguyên tạm giữ được trả đúng một lần.
- Ngoại lệ sau thu tiền: yêu cầu hoàn tiền được chuyển sang Quy trình 3 mà không tạo tác dụng phụ trùng.

### 3.6 Dữ liệu nghiệp vụ liên quan

Sự kiện, loại vé, sector, ghế/hạn mức, khả dụng, giới hạn mua, khuyến mãi/lượt dùng, đơn/dòng đơn, giữ chỗ, lần thanh toán, xác nhận thanh toán, vé, QR và trạng thái gửi vé.

### 3.7 Quyết định nguồn

`BIZ-001`–`BIZ-003`, `BIZ-011`–`BIZ-020`, `BIZ-023`–`BIZ-032`, `BIZ-039`–`BIZ-048`, `BIZ-060`–`BIZ-065`, `BIZ-069`–`BIZ-081`, `BIZ-083`, `BIZ-086`, `BIZ-090`–`BIZ-096`, `BIZ-103`–`BIZ-106`.

## 4. Quy trình 3 — Hoàn tiền, hủy sự kiện, đối soát và chi trả

### 4.1 Tác nhân

- Hệ thống: phát hiện trường hợp hoàn kỹ thuật, vô hiệu hóa vé và tổng hợp sổ đối soát.
- Nhà tổ chức: gửi yêu cầu hủy khi sự kiện đã có đơn thu tiền và xem báo cáo đối soát.
- Quản trị viên: xác nhận hoặc từ chối yêu cầu hủy, chủ động hủy, đối chiếu báo cáo cổng thanh toán và đánh dấu đã chi trả.
- Cổng thanh toán: thực hiện hoàn tiền và cung cấp báo cáo giao dịch.

### 4.2 Tiền điều kiện

- Chỉ hoàn toàn bộ, không hoàn một phần.
- Chính sách cố định của nền tảng nhận bốn nhóm: thanh toán đến muộn, phát hành vé thất bại sau thu tiền, thanh toán trùng và hủy một sự kiện.
- Hủy sự kiện và hoàn nhiều đơn do hủy chỉ thuộc phạm vi trước `eventStartAt`; ba nhóm hoàn kỹ thuật của một đơn vẫn được xử lý khi phát sinh sau mốc này.

### 4.3 Luồng hoàn một đơn

1. Hệ thống xác định khoản thu cụ thể và nguyên nhân hoàn.
2. Với cùng một khoản thu, hệ thống chỉ duy trì một yêu cầu hoàn tiền logic dù kích hoạt hoặc phản hồi bị lặp: yêu cầu đã hoàn thành không được tạo lại; yêu cầu đang xử lý hoặc thất bại nhưng còn có thể thử lại được tiếp tục; chỉ tạo mới khi chưa tồn tại yêu cầu hoàn cho khoản thu đó.
3. Với lỗi phát hành, mọi vé dở dang của đơn bị vô hiệu hóa và lượt khuyến mãi được trả đúng một lần; với thanh toán trùng, vé và giao dịch hợp lệ được giữ nguyên.
4. Hệ thống gửi yêu cầu hoàn toàn bộ khoản thực thu cần hoàn tới cổng thanh toán.
5. Khi có kết quả hợp lệ, hệ thống ghi trạng thái hoàn của khoản thu/yêu cầu hoàn, liên kết kết quả với đơn và cập nhật dữ liệu đối soát. Việc hoàn tiền không tự đổi trạng thái quyền vào cửa của vé; hậu quả lên vé vẫn theo nguyên nhân gốc như hủy sự kiện hoặc phát hành thất bại.
6. Tồn kho/giới hạn chỉ được trả theo quy tắc của nguyên nhân, không dựa đơn thuần vào việc có hoàn tiền.

### 4.4 Luồng hủy một sự kiện và xử lý nhiều đơn

1. Organizer gửi yêu cầu để admin xử lý nếu sự kiện đã có đơn thu tiền, hoặc admin chủ động hủy. Organizer được hủy trực tiếp khi chưa có đơn thu tiền.
2. Nếu admin từ chối yêu cầu, yêu cầu chuyển `REJECTED`, lý do có thể để trống, trạng thái sự kiện không đổi và quy trình dừng trước khi tạo bất kỳ xử lý hoàn nào.
3. Hệ thống chỉ chấp nhận hủy sự kiện trong phạm vi sản phẩm khi thời điểm hiện tại trước `eventStartAt`.
4. Khi hủy được xác nhận, sự kiện đóng bán; toàn bộ vé của sự kiện bị vô hiệu hóa và nguồn cung không trở lại khả dụng.
5. Mọi đơn chưa thanh toán và giữ chỗ đang hoạt động bị hủy; hệ thống không nhận lần thanh toán mới, trả phần giới hạn mua và lượt khuyến mãi đúng một lần nhưng không mở lại nguồn cung.
6. Hệ thống lấy từng đơn thuộc sự kiện bị hủy mà khoản thu hợp lệ được giữ lại vẫn còn số tiền phải hoàn, rồi gọi lại quy trình hoàn một đơn. Nếu khoản thu đã có yêu cầu hoàn logic thì tiếp tục yêu cầu đó theo trạng thái hiện tại thay vì tạo bản sao; các giao dịch thu thừa do thanh toán trùng không thuộc lô này. Mỗi đơn có trạng thái và khả năng thử lại độc lập; lỗi ở một đơn không làm mất dấu các đơn còn lại.
7. Callback thu tiền đến sau khi sự kiện/đơn đã hủy được hoàn toàn bộ mà không giải phóng tài nguyên lần thứ hai.
8. Kết quả tổng hợp cho biết số đơn chờ xử lý, thành công và thất bại để admin theo dõi.

### 4.5 Luồng đối soát và chi trả

1. Sổ cái chỉ đọc do hệ thống tổng hợp; admin không sửa trực tiếp giá trị tiền. `Doanh thu thực thu = tổng khoản bán hợp lệ sau khuyến mãi - tổng khoản đã hoàn`; khoản thu trùng phải hoàn không được tính là doanh thu hợp lệ.
2. `Phí nền tảng = doanh thu thực thu × tỷ lệ phí cố định khi sự kiện được phê duyệt`; `số tiền chi trả = doanh thu thực thu - phí nền tảng`.
3. Chỉ mở điều kiện chi trả sau khi sự kiện kết thúc, không còn lần thanh toán đang chờ và không còn yêu cầu hoàn đang xử lý.
4. Admin đối chiếu số liệu với báo cáo cổng thanh toán và xác nhận đã kiểm tra.
5. Việc chuyển tiền cho organizer diễn ra ngoài hệ thống. Sau đó admin đánh dấu `PAID` đúng một lần; thao tác cần có dấu vết, còn trường dữ liệu, payload và thời gian lưu giữ chờ B8/B13/B16.
6. Sai lệch phát hiện sau `PAID` xử lý ngoài hệ thống và không mở lại vòng đời chi trả.

### 4.6 Nhánh lỗi và biên

- Yêu cầu hoàn hoặc callback hoàn lặp không được hoàn tiền hai lần.
- Yêu cầu hoàn đã hoàn thành không được tạo lại; yêu cầu đang xử lý hoặc thất bại nhưng còn có thể thử lại phải tiếp tục trên cùng yêu cầu logic của khoản thu.
- Thanh toán đến muộn không giải phóng tài nguyên lần nữa.
- Hoàn giao dịch thu trùng không thay đổi đơn/vé/tồn kho/giới hạn của giao dịch hợp lệ.
- Giao dịch thu thừa do thanh toán trùng không được đưa vào lô hoàn do hủy sự kiện.
- Nếu phát hành lỗi nhưng sự kiện không còn đủ điều kiện bán, không đưa nguồn cung về khả dụng; lượt khuyến mãi vẫn được trả đúng một lần.
- Nếu một đơn trong lô hủy event thất bại, giữ nó ở trạng thái cần thử lại/điều tra mà không đảo các đơn đã hoàn thành.
- Không có khoản giữ lại và không có lần chi trả thứ hai.

### 4.7 Hậu điều kiện

- Mỗi khoản thu đủ điều kiện có tối đa một yêu cầu hoàn tiền logic và một kết quả hoàn thành công.
- Event bị hủy không còn vé hợp lệ để check-in và không mở lại nguồn cung bán.
- Mỗi event có tối đa một trạng thái `PAID` sau khi đủ điều kiện và được admin xác nhận.

### 4.8 Dữ liệu nghiệp vụ liên quan

Yêu cầu/kết quả hoàn, giao dịch thu, đơn, giữ chỗ, vé, trạng thái event, số liệu sổ cái, doanh thu thực thu, tỷ lệ phí, số tiền chi trả, xác nhận đối soát và dấu vết người đánh dấu.

### 4.9 Quyết định nguồn

`BIZ-008`–`BIZ-014`, `BIZ-033`–`BIZ-038`, `BIZ-050`–`BIZ-054`, `BIZ-059`, `BIZ-062`–`BIZ-066`, `BIZ-070`, `BIZ-083`–`BIZ-086`, `BIZ-090`, `BIZ-091`, `BIZ-097`–`BIZ-106`, `BIZ-142`.

## 5. Quy trình 4 — Organizer check-in trực tuyến

### 5.1 Tác nhân

- Nhà tổ chức: đăng nhập mobile và quét QR cho sự kiện thuộc chính mình.
- Người xuất trình vé: đưa QR hợp lệ; sản phẩm không tạo hồ sơ hoặc xác minh danh tính attendee.

### 5.2 Tiền điều kiện

- Tài khoản organizer sở hữu sự kiện được chọn.
- Thiết bị có kết nối tới hệ thống; không hỗ trợ check-in offline.
- Thời điểm hiện tại thỏa `eventStartAt <= now <= eventEndAt`.
- Vé thuộc đúng sự kiện, đã phát hành, chưa bị hủy/vô hiệu và chưa sử dụng.

### 5.3 Luồng chính

1. Organizer đăng nhập và chọn một sự kiện thuộc chính mình.
2. Organizer quét QR; organizer không có chức năng liệt kê hoặc tải QR thô của buyer.
3. Hệ thống xác định vé và kiểm tra quyền sở hữu event, cửa sổ check-in, phạm vi sự kiện cùng trạng thái vé.
4. Hệ thống ghi nhận nguyên tử việc chuyển vé từ chưa sử dụng sang đã sử dụng.
5. Mobile hiển thị kết quả và thông tin cần cho kiểm soát: loại vé, sector, ghế nếu có và trạng thái sử dụng. Không hiển thị danh tính người tham dự vì sản phẩm không quản lý dữ liệu đó.

### 5.4 Nhánh lỗi và biên

- Từ chối nếu event không thuộc organizer hoặc thời điểm nằm ngoài cửa sổ check-in.
- Từ chối nếu QR không xác định được vé, vé thuộc sự kiện khác, đã bị hủy/vô hiệu hoặc chưa ở trạng thái hợp lệ.
- Cùng một tài khoản organizer có thể dùng trên nhiều thiết bị. Khi hai thiết bị quét gần đồng thời, tối đa một yêu cầu được check-in thành công; yêu cầu còn lại nhận kết quả vé đã sử dụng.
- Lặp lại cùng yêu cầu không tạo thêm lượt check-in.
- Không hỗ trợ hoàn tác check-in, check-out hoặc tái vào cửa; trường hợp vận hành ngoại lệ xử lý ngoài hệ thống.
- Mất kết nối không chuyển sang chế độ offline; ứng dụng báo không thể xác nhận và cho phép thử lại khi có kết nối.

### 5.5 Hậu điều kiện

- Một vé hợp lệ có tối đa một check-in thành công.
- Mọi yêu cầu thành công hoặc bị từ chối có dấu vết đủ để điều tra; trường dữ liệu, payload và thời gian lưu giữ chưa được chốt tại B3.

### 5.6 Dữ liệu nghiệp vụ liên quan

Quyền sở hữu organizer–sự kiện, vé/QR, cửa sổ check-in, trạng thái sử dụng, yêu cầu/kết quả check-in và dấu vết thao tác.

### 5.7 Quyết định nguồn

`BIZ-056`, `BIZ-067`, `BIZ-068`, `BIZ-073`, `BIZ-074`, `BIZ-077`, `BIZ-082`, `BIZ-087`, `BIZ-089`, `BIZ-103`–`BIZ-105` và phạm vi mobile/check-in trực tuyến đã duyệt.

## 6. Ma trận nối giữa các quy trình

| Kết quả nguồn | Quy trình nhận | Ý nghĩa |
|---|---|---|
| Event đủ điều kiện bán | Quy trình 2 | Người mua mới được tạo giữ chỗ/đơn |
| Đơn hết hạn hoặc buyer hủy trước thanh toán | Quy trình 2 | Trả nguồn cung, giới hạn mua và lượt khuyến mãi đúng một lần |
| Thanh toán đến muộn, thu trùng hoặc phát hành lỗi | Quy trình 3 | Tạo yêu cầu hoàn toàn bộ phù hợp nguyên nhân, không phụ thuộc sự kiện đã bắt đầu hay chưa |
| Event bị hủy | Quy trình 2, 3 và 4 | Hủy đơn đang giữ; xử lý hoàn khoản thu hợp lệ còn số tiền phải hoàn theo đúng một yêu cầu logic; vô hiệu vé và từ chối check-in |
| Vé được phát hành hợp lệ | Quy trình 4 | Có thể check-in khi đúng event, đúng thời gian và chưa sử dụng |
| Event kết thúc, không còn xử lý tiền treo | Quy trình 3 | Admin có thể đối soát và đánh dấu chi trả một lần |

## 7. Điểm cần người duyệt kiểm tra

- Bốn quy trình đã bao phủ đúng phạm vi demo của web, backend và mobile hay chưa.
- Cách diễn đạt trạng thái/nhánh lỗi có phản ánh đúng quyết định nguồn mà không thêm chính sách mới hay không.
- Công thức đối soát và điều kiện `PAID` có đủ dễ giải thích trong báo cáo và khi bảo vệ hay không.
- Không có kết luận nào về đơn vị triển khai, schema, hợp đồng hoặc mẫu giao dịch phân tán ở tài liệu này.

## 8. Sơ đồ và phần dùng cho báo cáo

Bốn biểu đồ hoạt động có swimlane là đầu ra bắt buộc trước khi B3 chuyển sang `REVIEW_READY`:

- `docs/diagrams/src/B3-01-event-lifecycle.puml`
- `docs/diagrams/src/B3-02-order-payment-ticket.puml`
- `docs/diagrams/src/B3-03-refund-reconciliation-payout.puml`
- `docs/diagrams/src/B3-04-online-check-in.puml`

Sau khi B3 được duyệt, nội dung quy trình và sơ đồ có thể dùng trong phần phân tích nghiệp vụ. Các mã quyết định, trạng thái duyệt và ghi chú governance chỉ dùng nội bộ; báo cáo trình bày tác nhân, điều kiện, luồng chính, ngoại lệ và quy tắc nghiệp vụ.
