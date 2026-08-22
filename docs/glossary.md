# B2 — Từ điển miền

- Phiên bản: `B2-v0.10`
- Trạng thái: `APPROVED`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-08-22 (bản `B2-v0.9` được duyệt ngày 2026-08-21)
- Đầu vào và phiên bản: B1 khảo sát công khai `APPROVED` ngày 2026-08-13; B1 chẩn đoán `APPROVED` ngày 2026-08-13; A1/A2/A4 baseline `APPROVED` ngày 2026-08-13; `docs/project/decision-register.md` — dùng đúng các ID được dẫn tại từng định nghĩa và đọc trạng thái riêng của mỗi dòng
- Phân lớp: `FORMATION` — chỉ dùng yêu cầu, bằng chứng nghiệp vụ và quyết định đã xác nhận để định nghĩa miền đích

## 1. Mục đích và quy tắc sử dụng

Tài liệu này tạo ngôn ngữ chung cho ba thành viên trước khi mô hình hóa quy trình B3. Các định nghĩa mô tả **khái niệm nghiệp vụ**, không mặc nhiên là tên lớp, bảng, API, bounded context hoặc service. Vòng đời, lệnh, sự kiện miền và bất biến chi tiết lần lượt được làm rõ ở B3, B4 và B7.

| Trạng thái | Cách hiểu trong B2 |
|---|---|
| `USER_CONFIRMED` | Phạm vi hoặc nghĩa đã được chủ đồ án xác nhận trực tiếp |
| `CANDIDATE` | Cách diễn đạt đề xuất để cả nhóm duyệt; chưa phải quyết định kiến trúc |
| `OPEN` | Thiếu quyết định hoặc đầu vào; phải có mã, người chốt và gate xử lý |

Dùng **“sự kiện bán vé”** cho đối tượng được tổ chức và bán vé; chỉ dùng **“sự kiện miền”** cho một việc nghiệp vụ đã xảy ra.

## 2. Tác nhân nghiệp vụ

| Thuật ngữ | Định nghĩa thống nhất | Phân biệt dứt khoát với | Trạng thái |
|---|---|---|---|
| **Người mua** (*buyer*) | Tài khoản khởi tạo và thanh toán đơn; không mặc nhiên là người trực tiếp sử dụng mọi vé trong đơn. Sản phẩm không mô hình hóa danh tính người tham dự riêng. | **Người xuất trình vé:** bất kỳ người nào có QR hợp lệ, không phải actor có hồ sơ riêng trong sản phẩm. | `USER_CONFIRMED` |
| **Nhà tổ chức** (*organizer*) | Chủ thể chịu trách nhiệm về nội dung, lịch tổ chức, cấu hình bán và khuyến mãi; tài khoản organizer trực tiếp check-in cho các sự kiện thuộc mình. | **Quản trị viên:** vận hành ở cấp nền tảng. | `USER_CONFIRMED` |
| **Quản trị viên** (*admin*) | Người kiểm duyệt, nhập tỷ lệ phí nền tảng theo thỏa thuận, xác nhận hoặc từ chối yêu cầu hủy khi cần và đánh dấu chi trả thủ công. | **Nhà tổ chức:** không có quyền quản trị toàn nền tảng. | `USER_CONFIRMED` — `BIZ-010`, `BIZ-034`, `BIZ-038`, `BIZ-097`–`BIZ-099` |

### 2.1 Phạm vi tài khoản ở mức tối thiểu

| Khái niệm/phạm vi | Nghĩa đã có bằng chứng | Trạng thái |
|---|---|---|
| **Danh tính và vòng đời tài khoản** | Dùng Keycloak cho quản lý danh tính và vòng đời tài khoản. B2 chưa diễn giải thành cơ chế đồng bộ, schema hoặc API. | `USER_CONFIRMED` — `PRJ-003` |
| **Đa vai trò và bộ role** | Một tài khoản có thể mang nhiều vai trò nghiệp vụ. Bộ vai trò trong phạm vi đồ án gồm `BUYER`, `ORGANIZER` và `ADMIN`; tài khoản tự đăng ký nhận `BUYER` mặc định. | `USER_CONFIRMED` — `BIZ-109`, `BIZ-132`, `BIZ-146` |
| **Tài khoản admin đầu tiên** | Chủ đồ án tự tạo trong database và sản phẩm không phát sinh giao diện cho việc này. Từ “database” chưa được diễn giải thành một kho dữ liệu kỹ thuật cụ thể. | `USER_CONFIRMED` — `BIZ-111` |
| **Hồ sơ nghiệp vụ ứng dụng** | Ứng dụng giữ một hồ sơ nghiệp vụ tối thiểu tách khỏi dữ liệu danh tính do Keycloak quản lý; tập trường cụ thể chờ B6/B8 và B12/B13. | `USER_CONFIRMED` — `BIZ-131` |
| **Đăng ký organizer** | Buyer nộp hồ sơ ở trạng thái `PENDING`. Khi admin duyệt và Keycloak xác nhận cấp role, hồ sơ thành `ACTIVE`, tài khoản nhận thêm `ORGANIZER` và vẫn giữ `BUYER`. Chỉ hồ sơ `ACTIVE` được công khai. | `USER_CONFIRMED` — `BIZ-134`, `BIZ-136`, `BIZ-140`, `BIZ-141` |
| **Từ chối organizer** | Admin phải nhập và hệ thống lưu lý do; hồ sơ chuyển `REJECTED` và không hỗ trợ sửa/gửi lại trong phạm vi đồ án. | `USER_CONFIRMED` — `BIZ-137`, `BIZ-138` |
| **Thu hồi role organizer** | Không xây dựng luồng thu hồi vai trò `ORGANIZER` trong phạm vi đồ án. | `USER_CONFIRMED` — `BIZ-139` |
| **Sở hữu sự kiện** | Mỗi sự kiện thuộc đúng một tài khoản organizer; không xét chuyển quyền sở hữu giữa các organizer. | `USER_CONFIRMED` — `BIZ-135`, `BIZ-114` |
| **Xác minh email/số điện thoại** | Không yêu cầu trong phạm vi đồ án và không phát sinh thêm code cho việc này. | `USER_CONFIRMED` — `BIZ-124` |
| **Đăng nhập qua mạng xã hội** | Không hỗ trợ trong phạm vi đồ án; B2 không suy ra thêm nhà cung cấp đăng nhập ngoài Keycloak. | `USER_CONFIRMED` — `BIZ-129` |
| **Khóa tài khoản giữa chừng** | Không xét trường hợp này trong phạm vi đồ án. | `USER_CONFIRMED` — `BIZ-119` |

Các trường cụ thể của hồ sơ nghiệp vụ, cách đồng bộ kỹ thuật và nơi lưu vật lý chưa được B2 quyết định; chúng chờ B6/B8 và B11–B13, không làm thay đổi các quy tắc nghiệp vụ tối thiểu đã xác nhận ở trên.

## 3. Sự kiện bán vé, cấu hình bán và khả dụng

| Thuật ngữ | Định nghĩa thống nhất | Phân biệt dứt khoát với | Trạng thái |
|---|---|---|---|
| **Sự kiện bán vé** (*ticketed event*) | Đúng một lần tổ chức có thời gian và địa điểm xác định, được organizer cấu hình để công bố và bán quyền tham dự. | **Sự kiện miền:** một việc nghiệp vụ đã xảy ra. | `USER_CONFIRMED` |
| **Địa điểm** (*venue*) | Thông tin nơi tổ chức có thể được dùng lại cho nhiều sự kiện. Địa điểm không đồng nghĩa với sơ đồ ghế, sector hoặc nguồn cung vé được cấu hình cho một sự kiện cụ thể. | **Sơ đồ ghế/cấu hình bán:** thuộc lần tổ chức và cách bán vé của sự kiện. | `USER_CONFIRMED` — `BIZ-125` |
| **Phân loại sự kiện** (*category*) | Nhãn nhóm giúp mô tả và gom các sự kiện cùng chủ đề ở mức tối giản. | **Loại vé:** gói giá/quyền lợi được bán trong một sự kiện. | `USER_CONFIRMED` — `BIZ-126` |
| **Tìm sự kiện** (*search*) | Tìm các sự kiện đã công bố; tiêu chí tìm và cách xếp kết quả chưa được chốt tại B2. | **Mở bán:** sự kiện có thể tìm thấy nhưng chưa chắc đang nhận giao dịch. | `USER_CONFIRMED` — `BIZ-127` |
| **Theo dõi organizer** (*follow*) | Buyer ghi nhận việc mình muốn theo dõi một organizer. Khái niệm tối thiểu này chưa bao hàm thông báo, bảng tin hay cách tính số người theo dõi. | **Vai trò `ORGANIZER`:** quyền nghiệp vụ của tài khoản, không phải quan hệ theo dõi. | `USER_CONFIRMED` — `BIZ-128` |
| **Trạng thái sự kiện** | Vòng đời quản trị dùng `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `PUBLISHED` và `CANCELLED`. `DRAFT` là sự kiện đã được organizer tạo, còn được sửa nội dung/cấu hình bán, chưa công khai và chưa được bán vé. Từ chối/trả lại ở bước duyệt đưa sự kiện về `DRAFT`. | **Kết quả từ chối duyệt:** không phải trạng thái `REJECTED` bền vững riêng; **trạng thái mở bán:** kết quả xét điều kiện giao dịch tại một thời điểm. | `USER_CONFIRMED` — `BIZ-143`–`BIZ-145` |
| **Phê duyệt sự kiện** | Quyết định của admin cho phép sự kiện đi tiếp; admin đồng thời nhập tỷ lệ phí nền tảng theo thỏa thuận ngoài hệ thống. | **Công bố:** làm thông tin hiển thị; phê duyệt không tự công bố hoặc mở bán. | `USER_CONFIRMED` |
| **Công bố sự kiện** (*publish*) | Hành động của organizer làm sự kiện đã duyệt hiển thị cho người dùng. | **Mở bán:** khả năng giao dịch trong cửa sổ bán. | `USER_CONFIRMED` |
| **Yêu cầu hủy sự kiện** | Hồ sơ organizer gửi để xin hủy một sự kiện **đã có đơn thu tiền**, chờ admin xác nhận hoặc từ chối. Yêu cầu có vòng đời riêng: khi bị từ chối thì bản thân yêu cầu chuyển `REJECTED`, lý do từ chối là tùy chọn, trạng thái sự kiện không đổi và không luồng hoàn tiền nào được khởi động. Sự kiện chưa có đơn thu tiền được organizer hủy trực tiếp, không qua yêu cầu. | **Sự kiện đã hủy (`CANCELLED`):** trạng thái của chính sự kiện, chỉ đạt được khi admin xác nhận yêu cầu hoặc chủ động hủy; **`REJECTED` của hồ sơ organizer:** thuộc vòng đời đăng ký organizer, không liên quan. | `USER_CONFIRMED` — `BIZ-008`–`BIZ-010`, `BIZ-083`, `BIZ-097`–`BIZ-099` |
| **Cửa sổ mở bán** | Một khoảng chung cho toàn sự kiện từ `saleStartAt` đến `saleEndAt`, thỏa `saleStartAt < saleEndAt <= eventEndAt`. | **Công bố:** sự kiện có thể hiển thị nhưng chưa tới giờ bán. | `USER_CONFIRMED` |
| **Mở bán** | Trạng thái khả dụng khi sự kiện đã duyệt, đã công bố, chưa hủy và hiện tại nằm trong cửa sổ bán; event đã bắt đầu không tự đóng bán. | **Công bố:** chỉ quyết định khả năng nhìn thấy. | `USER_CONFIRMED` |
| **Chế độ bán** (*sales mode*) | Cách tổ chức nguồn cung, nhận đúng một trong hai giá trị `QUANTITY` hoặc `SEAT_MAP`. | **Loại sector:** cách một khu phân bổ nguồn cung. | `USER_CONFIRMED` |
| **Bán theo số lượng** (`QUANTITY`) | Chế độ không dùng sơ đồ; người mua chọn loại vé và số lượng trong hạn mức khả dụng. | **Bán theo sơ đồ:** người mua chọn trong sector. | `USER_CONFIRMED` |
| **Bán theo sơ đồ** (`SEAT_MAP`) | Chế độ dùng sơ đồ gồm sector ngồi hoặc đứng; một đơn chỉ chứa vé thuộc một sector. | **Bán theo số lượng:** không có sector/ghế để chọn. | `USER_CONFIRMED` |
| **Sector** | Khu vực bán trong sơ đồ, có hành vi `SEATED` hoặc `STANDING` và có thể chứa nhiều loại vé. VIP chỉ là tên/quyền lợi cấu hình, không phải loại sector riêng. | **Loại vé:** gói giá/quyền lợi trong sector. | `USER_CONFIRMED` |
| **Sector ngồi** (`SEATED`) | Sector có ghế định danh; mỗi lựa chọn giữ hoặc bán một ghế cụ thể. | **Sector đứng:** quản lý theo số lượng trong khu. | `USER_CONFIRMED` |
| **Sector đứng** (`STANDING`) | Sector không gán ghế cụ thể; người mua chọn loại vé và số lượng trong giới hạn khu. | **Sector ngồi:** nguồn cung gắn với ghế định danh. | `USER_CONFIRMED` |
| **Loại vé** (*ticket type*) | Gói quyền mua có tên, giá lớn hơn 0, quyền lợi và hạn mức bán; trong sơ đồ, loại vé thuộc một sector. | **Vé:** quyền tham dự riêng lẻ đã phát hành. | `USER_CONFIRMED` |
| **Ghế** (*seat*) | Vị trí định danh trong sector `SEATED`, chỉ được giữ hoặc bán cho một ý định hợp lệ tại một thời điểm. | **Loại vé:** cấu hình giá/quyền lợi. | `USER_CONFIRMED` |
| **Hạn mức bán** | Số lượng tối đa được bán của loại vé hoặc sector đứng. | **Tồn kho còn lại:** phần chưa bán hoặc giữ hợp lệ. | `USER_CONFIRMED` |
| **Tồn kho vé còn lại** | Lượng quyền bán còn có thể giữ sau khi xét phần đã bán và đang giữ hợp lệ. | **Danh sách vé:** tập quyền đã có định danh. | `USER_CONFIRMED` |
| **Khả dụng** | Kết quả xét ghế hoặc số lượng có thể chọn dựa trên trạng thái bán, tồn kho, giữ chỗ và giới hạn mua. | **Tồn kho:** chỉ là một đầu vào. | `USER_CONFIRMED` |
| **Giữ chỗ** (*hold*) | Cam kết tạm thời toàn bộ lựa chọn của một đơn trong một thời hạn chung. | **Đơn hàng:** hồ sơ giao dịch; một đơn có đúng một giữ chỗ. | `USER_CONFIRMED` |
| **Giữ chỗ hết hạn** | Giữ chỗ mất hiệu lực tại mốc hết hạn chung của đơn; nguồn cung, giới hạn mua và lượt khuyến mãi đang giữ được trả lại đúng một lần. | **Hủy đơn:** quyết định kết thúc đơn trước hạn. | `USER_CONFIRMED` |
| **Hết vé** | Không còn lượng khả dụng cho ý định mua mới tại thời điểm xét. | **Ngừng bán:** có thể do trạng thái/thời gian dù còn tồn kho. | `USER_CONFIRMED` |
| **Giới hạn mua** | Giá trị bắt buộc organizer nhập, giới hạn tổng số vé một tài khoản đang giữ hoặc đã mua trong toàn sự kiện. Chỉ bảo đảm theo tài khoản–sự kiện, không phải cơ chế chống đầu cơ hoặc chống dùng nhiều tài khoản. | **Hạn mức bán:** giới hạn tổng nguồn cung cho mọi người. | `USER_CONFIRMED` |
| **Khuyến mãi** | Cấu hình giảm giá đơn giản do organizer tạo và gắn với đúng một sự kiện; chuỗi mã duy nhất trong sự kiện đó. Khuyến mãi giảm theo phần trăm hoặc số tiền cố định, có thời gian hiệu lực và tổng lượt dùng. | **Loại vé:** xác định giá gốc; **mã toàn hệ thống:** không yêu cầu duy nhất giữa các sự kiện. | `USER_CONFIRMED` |
| **Lượt dùng khuyến mãi** | Quyền dùng một mã của một tài khoản; giữ cùng đơn và trả lại đúng một lần nếu đơn hết hạn, bị hủy trước thanh toán hoặc phát hành vé thất bại. | **Tổng lượt dùng:** trần dùng của mã. | `USER_CONFIRMED` |
| **Giá sau giảm** | Số tiền đơn sau tối đa một mã, không cộng dồn và luôn lớn hơn 0. | **Giá loại vé:** đơn giá trước giảm. | `USER_CONFIRMED` |

## 4. Đơn hàng, thanh toán, phát hành và check-in

| Thuật ngữ | Định nghĩa thống nhất | Phân biệt dứt khoát với | Trạng thái |
|---|---|---|---|
| **Đơn hàng** (*order*) | Hồ sơ ý định mua thuộc đúng một sự kiện, có đúng một giữ chỗ và một thời hạn chung; trong sơ đồ, mọi dòng đơn thuộc cùng sector. | **Giữ chỗ:** cam kết nguồn cung; **thanh toán:** ghi nhận tiền. | `USER_CONFIRMED` |
| **Dòng đơn hàng** | Mục chỉ loại vé, số lượng, đơn giá và sector nếu có. Không đổi lựa chọn trong đơn đã tạo; muốn chọn khác phải tạo đơn mới. | **Vé:** quyền tham dự riêng lẻ. | `USER_CONFIRMED` |
| **Đơn hết hạn** | Đơn không còn tiếp tục theo luồng thông thường tại cùng mốc giữ chỗ hết hạn. | **Đơn thanh toán thất bại:** còn có thể thử lại nếu chưa hết hạn. | `USER_CONFIRMED` |
| **Hủy đơn** | Buyer chủ động kết thúc toàn bộ đơn của chính mình khi đơn còn giữ chỗ và chưa có thanh toán thành công; tài nguyên tạm giữ được trả đúng một lần và không được tạo lần thanh toán mới. | **Đơn hết hạn:** hệ thống kết thúc theo thời gian; **hoàn tiền:** xử lý khoản tiền đã thu. | `USER_CONFIRMED` |
| **Số tiền phải thanh toán** | Nghĩa vụ thanh toán sau tối đa một khuyến mãi hợp lệ và phải lớn hơn 0. | **Doanh thu thực thu:** giá trị dùng cho đối soát sau giao dịch. | `USER_CONFIRMED` |
| **Lần thanh toán** | Một lần khởi tạo thanh toán; có thể tạo lần mới tuần tự sau thất bại khi đơn còn hiệu lực. | **Đơn hàng:** bao quát nhiều lần thử. | `USER_CONFIRMED` |
| **Xác nhận thanh toán** | Bằng chứng hợp lệ rằng tiền đã thu cho đúng đơn, lần thử và số tiền; mỗi đơn chỉ giữ một kết quả thu hợp lệ. | **Callback/IPN:** thông điệp có thể lặp. | `USER_CONFIRMED` |
| **Callback/IPN** | Thông báo kết quả từ cổng thanh toán; thông báo lặp không tạo tác dụng phụ lặp. | **Xác nhận thanh toán:** sự thật nghiệp vụ sau xác minh. | `USER_CONFIRMED` |
| **Thanh toán trùng** | Một nghĩa vụ bị thu nhiều hơn một lần; giữ một giao dịch hợp lệ và hoàn từng giao dịch thừa. | **Callback lặp:** không nhất thiết có nhiều lần thu. | `USER_CONFIRMED` |
| **Thanh toán đến muộn** | Khoản thu được xác nhận sau khi đơn không còn chấp nhận thanh toán vì đã hết hạn hoặc đã bị hủy, kể cả do sự kiện bị hủy; khoản thu được hoàn toàn bộ mà không giải phóng tài nguyên lần nữa. | **Thanh toán thất bại:** không có tiền đã thu; **lô hoàn do hủy sự kiện:** xử lý các khoản đã được giữ lại trước khi hủy. | `USER_CONFIRMED` |
| **Phát hành vé** | Tạo quyền tham dự định danh sau thanh toán hợp lệ; nếu thất bại sau thu tiền, vé dở dang bị vô hiệu và đơn được hoàn toàn bộ. | **Gửi vé:** chuyển thông tin vé. | `USER_CONFIRMED` |
| **Gửi vé** | Chuyển mã vé đã phát hành tới người mua; nếu thất bại, vé vẫn hợp lệ và có thể tải/gửi lại. | **Phát hành:** tạo quyền; gửi lỗi không kích hoạt hoàn tiền. | `USER_CONFIRMED` |
| **Vé** | Quyền tham dự riêng lẻ có vòng đời riêng. Sản phẩm không khóa vé vào danh tính buyer và không cung cấp chức năng chuyển vé giữa tài khoản. | **Loại vé:** cấu hình bán; **QR:** cách biểu diễn vé. | `USER_CONFIRMED` |
| **Mã vé/QR** | Dữ liệu hoặc khóa tham chiếu để xác định vé khi vào cửa. | **Vé:** quyền nghiệp vụ. | `USER_CONFIRMED` |
| **Quyền truy cập QR** | Buyer chỉ được xem/tải QR đã phát hành thuộc đơn của mình; organizer chỉ quét cho event của mình, còn organizer/admin không được liệt kê hoặc tải QR thô của buyer. | **Quyền quét:** không đồng nghĩa quyền đọc danh sách QR. | `USER_CONFIRMED` |
| **QR đã vô hiệu** | QR của vé đã bị hủy hoặc vô hiệu không còn được xem/tải; vé vẫn hiện trong lịch sử buyer với trạng thái tương ứng. Hoàn tiền không tự tạo một trạng thái riêng cho vé. | **Trạng thái hoàn tiền:** thuộc khoản thu và yêu cầu hoàn, còn đơn liên kết/tổng hợp kết quả; **xóa lịch sử:** sản phẩm vẫn giữ dấu vết vé. | `USER_CONFIRMED` |
| **Yêu cầu check-in** | Một thiết bị dùng tài khoản organizer gửi mã vé và sự kiện thuộc organizer tới hệ thống để xin ghi nhận sử dụng trực tuyến. Cùng tài khoản có thể hoạt động trên nhiều thiết bị. | **Check-in thành công:** kết quả nguyên tử. | `USER_CONFIRMED` |
| **Cửa sổ check-in** | Khoảng đóng từ `eventStartAt` đến `eventEndAt`; chỉ chấp nhận khi `eventStartAt <= now <= eventEndAt`. | **Cửa sổ mở bán:** có thể bắt đầu trước event và kết thúc trong lúc event diễn ra. | `USER_CONFIRMED` |
| **Check-in thành công** | Chuyển vé hợp lệ từ chưa sử dụng sang đã sử dụng; mỗi vé có tối đa một lần thành công, không hỗ trợ hoàn tác, check-out hoặc tái vào cửa. | **Lượt quét:** yêu cầu có thể bị từ chối. | `USER_CONFIRMED` |
| **Vé đã sử dụng** | Vé đã có một check-in thành công và không thể thành công lần nữa. | **Vé đã quét:** có thể chỉ là yêu cầu bị từ chối. | `USER_CONFIRMED` |

## 5. Hoàn tiền, đối soát và chi trả

| Thuật ngữ | Định nghĩa thống nhất | Phân biệt dứt khoát với | Trạng thái |
|---|---|---|---|
| **Yêu cầu hoàn tiền** | Hồ sơ trả lại toàn bộ số tiền còn phải hoàn của một khoản thu gắn với đơn; mỗi khoản thu có tối đa một yêu cầu hoàn logic và không hỗ trợ hoàn một phần. | **Hoàn tiền:** kết quả dịch chuyển tiền; **đơn hàng:** một đơn có thể liên quan nhiều khoản thu khi xảy ra thanh toán trùng. | `USER_CONFIRMED` |
| **Hoàn tiền** | Trả toàn bộ số tiền cần hoàn của một khoản thu, ghi trạng thái của khoản thu/yêu cầu hoàn và liên kết kết quả với đơn. Quyền vào cửa của vé chỉ thay đổi theo nguyên nhân nghiệp vụ gốc. | **Hủy/vô hiệu vé:** làm mất quyền vào cửa; **hủy đơn:** đơn chưa thu tiền không tạo hoàn tiền. | `USER_CONFIRMED` |
| **Chính sách hoàn tiền** | Quy tắc cố định của nền tảng cho thanh toán đến muộn, phát hành vé lỗi sau thu tiền, thanh toán trùng và hủy sự kiện; organizer không tự cấu hình. Giới hạn trước `eventStartAt` chỉ áp dụng cho hủy event và hoàn phát sinh từ việc hủy, không chặn ba loại hoàn kỹ thuật của một đơn. | **Thao tác hoàn tiền:** xử lý một trường hợp. | `USER_CONFIRMED` |
| **Doanh thu thực thu** | Tổng tiền giao dịch bán hợp lệ sau khuyến mãi và sau hoàn tiền trong phạm vi đối soát. | **Số tiền chi trả:** doanh thu ròng sau phí. | `USER_CONFIRMED` |
| **Phí nền tảng** | Tỷ lệ phần trăm admin nhập khi duyệt theo thỏa thuận ngoài hệ thống, được cố định sau phê duyệt và tính trên doanh thu thực thu. | **Số tiền chi trả:** phần ròng cho organizer; **nơi lưu tỷ lệ:** chờ B12 quyết định. | `USER_CONFIRMED` — `BIZ-034`, `BIZ-059`, `BIZ-142` |
| **Sổ cái đối soát chỉ đọc** | Dữ liệu tổng hợp doanh thu, phí, hoàn tiền và số tiền dự kiến chi trả; không chuyển tiền. | **Cổng thanh toán:** nơi xử lý tiền. | `USER_CONFIRMED` |
| **Đối soát thủ công** | Admin kiểm tra báo cáo cổng thanh toán và xác nhận không còn thanh toán hoặc hoàn tiền đang xử lý sau khi sự kiện kết thúc. | **Chi trả:** chuyển tiền ngoài hệ thống. | `USER_CONFIRMED` |
| **Số tiền chi trả** | Doanh thu thực thu trừ phí nền tảng; không có khoản giữ lại phòng hoàn tiền. | **Doanh thu thực thu:** giá trị trước phí. | `USER_CONFIRMED` |
| **Chi trả** | Một lần chuyển tiền cho organizer sau khi đủ điều kiện đối soát, thực hiện ngoài hệ thống. | **Đánh dấu đã chi trả:** ghi nhận kết quả. | `USER_CONFIRMED` |
| **Đã chi trả** (`PAID`) | Trạng thái admin đánh dấu thủ công đúng một lần; sai lệch phát hiện sau đó xử lý ngoài hệ thống và không mở lại vòng đời. | **Đã đối soát:** đủ điều kiện nhưng chưa chắc đã chuyển tiền. | `USER_CONFIRMED` |

## 6. Sự cố và trợ lý chẩn đoán

| Thuật ngữ | Định nghĩa thống nhất | Phân biệt dứt khoát với | Trạng thái |
|---|---|---|---|
| **Sự cố** | Tình huống hệ thống không cung cấp hoặc có nguy cơ không cung cấp hành vi mong đợi, cần con người xác minh. | **Lỗi log:** một bản ghi, không tự là một sự cố. | `USER_CONFIRMED` |
| **Lỗi âm thầm** | Hành vi sai hoặc luồng đình trệ nhưng không tạo ngoại lệ rõ ràng tại nơi quan sát đầu tiên. | **Lỗi ngoại lệ:** có exception trực tiếp. | `USER_CONFIRMED` |
| **Lỗi ngoại lệ ứng dụng** | Sự cố có exception trong quá trình thực thi ứng dụng. | **Nguyên nhân gốc:** exception chưa chắc là nguyên nhân cuối. | `USER_CONFIRMED` |
| **Lỗi cơ sở dữ liệu** | Sự cố liên quan truy cập, ràng buộc, kết nối hoặc trạng thái dữ liệu. | **Từ chối nghiệp vụ:** hành vi đúng quy tắc. | `USER_CONFIRMED` |
| **Lỗi hạ tầng/tích hợp** | Sự cố do tài nguyên, mạng, tiến trình hoặc hệ thống phụ thuộc. | **Lỗi ứng dụng:** phát sinh trong logic. | `USER_CONFIRMED` |
| **Dấu vết vận hành** | Log, trace, exception và metadata dùng để dựng lại diễn biến. | **Nguyên nhân gốc:** dấu vết là bằng chứng đầu vào. | `USER_CONFIRMED` |
| **Log có cấu trúc** | Bản ghi có trường và định dạng nhất quán để máy xử lý. | **Thông điệp log:** chỉ là phần văn bản. | `USER_CONFIRMED` |
| **Mã tương quan** | Định danh liên kết bản ghi thuộc cùng yêu cầu hoặc luồng nghiệp vụ. | **Mã nghiệp vụ:** định danh đơn, vé, thanh toán. | `USER_CONFIRMED` |
| **Mẫu log Drain** | Cấu trúc ổn định rút ra sau khi tách phần biến đổi của các thông điệp tương tự. | **Nguyên nhân:** mẫu log không tự kết luận nguyên nhân. | `USER_CONFIRMED` |
| **Context chẩn đoán** | Dấu vết đã chọn, liên kết, rút gọn và khử nhạy cảm cho một sự cố. | **Toàn bộ log:** context chỉ giữ phần liên quan. | `USER_CONFIRMED` |
| **Nguyên nhân khả dĩ** | Giả thuyết phù hợp bằng chứng nhưng chưa được con người xác minh. | **Nguyên nhân gốc đã xác nhận:** kết luận cuối. | `USER_CONFIRMED` |
| **Bước kiểm tra tiếp theo** | Hành động đọc/xác minh để củng cố hoặc loại trừ giả thuyết. | **Tự sửa:** trợ lý không chạy lệnh hoặc ghi nghiệp vụ. | `USER_CONFIRMED` |
| **Trợ lý chẩn đoán sự cố** | Công cụ chỉ đọc liên kết dấu vết, đề xuất nguyên nhân khả dĩ và bước kiểm tra; không tự kết luận hay tự sửa. | **Chatbot mua vé:** hỗ trợ hành trình mua. | `USER_CONFIRMED` |

## 7. Thuật ngữ phương pháp

| Thuật ngữ | Định nghĩa dùng trong đồ án | Phân biệt dứt khoát với | Trạng thái |
|---|---|---|---|
| **Sự kiện miền** | Phát biểu ở thì quá khứ về một việc nghiệp vụ đã xảy ra, dùng lập dòng thời gian ở B4. | **Sự kiện bán vé:** đối tượng được tổ chức; **lệnh:** yêu cầu một việc xảy ra. | `USER_CONFIRMED` |
| **Trạng thái nghiệp vụ** | Tình trạng hiện tại có ý nghĩa với quy tắc và hành vi miền. | **Trạng thái kỹ thuật:** tình trạng tiến trình hoặc kết nối. | `USER_CONFIRMED` |
| **Bất biến nghiệp vụ** | Điều kiện phải luôn được bảo vệ kể cả khi đồng thời, thử lại hoặc lỗi từng phần. | **Mục tiêu hiệu năng:** ngưỡng mong muốn. | `USER_CONFIRMED` |

## 8. Sổ vấn đề đã xử lý

Các ID được giữ để bảo toàn dấu vết. `CLOSED` là tình trạng xử lý của vấn đề, không phải trạng thái bằng chứng thứ sáu.

| ID | Câu hỏi lịch sử | Kết quả xử lý |
|---|---|---|
| `B2-01` | Một sự kiện có một hay nhiều lần tổ chức? | `CLOSED → BIZ-021` |
| `B2-02` | Công bố và mở bán có độc lập không? | `CLOSED → BIZ-004`–`BIZ-007` |
| `B2-03` | Người mua và người tham dự có khác nhau không? | `CLOSED → BIZ-072`–`BIZ-074` (thay nghĩa quá mạnh của `BIZ-022`) |
| `B2-04` | Có hỗ trợ sơ đồ ghế và đơn vị bán nào? | `CLOSED → BIZ-001`–`BIZ-003`, `BIZ-016`–`BIZ-020` |
| `B2-05` | Tên chuẩn là phí nền tảng hay hoa hồng? | `CLOSED → BIZ-024` |
| `B3-01` | Giới hạn mua tính theo chủ thể/phạm vi nào? | `CLOSED → BIZ-025`–`BIZ-028`, `BIZ-075` |
| `B3-02` | Một đơn có bao nhiêu giữ chỗ và có đổi lựa chọn không? | `CLOSED → BIZ-029`, `BIZ-030` |
| `B3-03` | Đơn hết hạn và giữ chỗ hết hạn có cùng mốc không? | `CLOSED → BIZ-029` |
| `B3-04` | Một đơn có bao nhiêu lần thanh toán và xử lý kết quả cũ thế nào? | `CLOSED → BIZ-012`, `BIZ-013`, `BIZ-031`, `BIZ-032` |
| `B3-05` | Ai quy định chính sách hoàn tiền và phạm vi nào? | `CLOSED → BIZ-011`–`BIZ-014`, `BIZ-033`, `BIZ-055`–`BIZ-057`, `BIZ-083` |
| `B3-06` | Công thức và thời điểm nhập phí nền tảng là gì? | `CLOSED → BIZ-034`, `BIZ-035`, `BIZ-059` |
| `B3-07` | Có khoản giữ lại không và khi nào mở chi trả? | `CLOSED → BIZ-036`, `BIZ-050`–`BIZ-054` |
| `B3-08` | Vòng đời chi trả và người đánh dấu là gì? | `CLOSED → BIZ-037`, `BIZ-038`, `BIZ-050`–`BIZ-054` |
| `B2-06` | “Đang soạn” và kết quả từ chối duyệt sự kiện có nghĩa gì? | `CLOSED → BIZ-143`–`BIZ-145` |
| `B2-07` | Yêu cầu hủy sự kiện có phải một khái niệm riêng, tách khỏi trạng thái `CANCELLED` của sự kiện không? | `CLOSED → BIZ-097`–`BIZ-099`. Vòng kiểm toán B7 ngày 2026-08-22 phát hiện B2 thiếu mục từ này dù B3/B4 đã dùng và `BIZ-098` cho nó một trạng thái `REJECTED` riêng; mục từ được bổ sung tại `B2-v0.10` |

## 9. Tiêu chí để chuyển B2 thành `APPROVED`

- Ba thành viên mô tả cùng một luồng từ chọn vé đến check-in mà không dùng hai từ khác nhau cho cùng một khái niệm.
- Lê Văn Minh duyệt hoặc yêu cầu chỉnh các định nghĩa `CANDIDATE` và xác nhận cách diễn đạt của các mục `USER_CONFIRMED`.
- Mọi vấn đề làm đổi nghĩa thuật ngữ đã được đóng hoặc ghi `OPEN` nguyên tử; hiện không còn mục `BLOCKS_B2`.
- Không suy từ thuật ngữ sang service, Saga, schema, bảng hoặc hợp đồng kỹ thuật.

Các tiêu chí trên được Lê Văn Minh xác nhận đạt khi duyệt `B2-v0.8` và được xác nhận lại cho `B2-v0.9` ngày 2026-08-21. Các trường dữ liệu và quyết định kỹ thuật được chuyển đúng gate sau, không được xem là khoảng trống chặn B2.

`B2-v0.10` (2026-08-22) chỉ **bổ sung** mục từ **Yêu cầu hủy sự kiện**; không định nghĩa lại, đổi nghĩa hay gỡ bất kỳ mục từ nào đã duyệt. Vì B2 là đầu vào bắt buộc của cả chuỗi, tài liệu và các tạo tác hạ nguồn B3 → B4 → B5 cùng trở lại `REVIEW_READY`, theo đúng cách đã xử lý vòng bỏ `SUPER_ADMIN` ngày 2026-08-21. Lê Văn Minh đã duyệt lại toàn chuỗi `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12` trong cùng ngày 2026-08-22.

## 10. Phần dùng cho báo cáo

Bản `APPROVED` của B2 có thể đưa vào phụ lục “Bảng thuật ngữ” và dùng nhất quán trong phân tích yêu cầu, thiết kế và đánh giá. Trạng thái bằng chứng, sổ vấn đề và hướng dẫn nội bộ không cần đưa nguyên trạng vào báo cáo.
