# B6 — Use case và đặc tả

- Phiên bản: `B6-v0.12`
- Trạng thái: `APPROVED`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-08-22, sau chuỗi `B2-v0.10` → `B5-v0.12`
- Đầu vào và phiên bản: `docs/domain/B3-business-processes.md` — `B3-v0.10`, `APPROVED` ngày 2026-08-22; `docs/domain/B4-domain-event-map.md` — `B4-v0.14`, `APPROVED` ngày 2026-08-22; `docs/glossary.md` — `B2-v0.10`, `APPROVED` ngày 2026-08-22, dùng làm ràng buộc thuật ngữ; `docs/project/decision-register.md` — đọc riêng trạng thái của từng ID được dẫn

> **Cổng phê duyệt:** chuỗi `B2 → B3 → B4 → B5` đã được Lê Văn Minh duyệt lại đúng thứ tự ngày 2026-08-22 sau khi `B2-v0.10` bổ sung mục từ **Yêu cầu hủy sự kiện**; B6 được duyệt sau chuỗi đó.
- Phân lớp: `FORMATION`

> **Đường dẫn canonical:** `docs/domain/B6-use-cases.md`, được khai trong bảng đường dẫn của Tầng B.

## 1. Mục đích và giới hạn

B6 rút các tương tác giữa tác nhân và hệ thống từ bốn quy trình đã duyệt ở B3, rồi đặc tả đầy đủ những ca cốt lõi và những ca có bất biến hoặc nhánh lỗi đáng chú ý. Mục tiêu là tạo đầu vào cho B8 (FR/NFR) và cho kiểm thử về sau.

B6 **không** quyết định:

- service vật lý, tiến trình triển khai hoặc đơn vị đóng gói;
- schema, bảng, quyền sở hữu dữ liệu hoặc ERD;
- API, endpoint, payload, topic hay hợp đồng tích hợp;
- aggregate, ranh giới giao dịch hoặc cơ chế nhất quán phân tán;
- tên trạng thái kỹ thuật mới ngoài những tên đã được B2/B3 chốt.

**Ràng buộc thuật ngữ.** Mọi khái niệm và danh từ nghiệp vụ dùng trong tài liệu này đều lấy từ B2-v0.10. **Tác nhân nghiệp vụ** — người mua, nhà tổ chức, quản trị viên — cũng lấy từ B2. **Tác nhân là hệ thống ngoài hoặc nguồn kích hoạt không phải người** truy về B3 hoặc B4, vì B2 là từ điển khái niệm nghiệp vụ chứ không phải danh mục hệ thống ngoài. Từ mô tả thông thường không cần đưa vào B2. Khi gặp một khái niệm nghiệp vụ chưa có tên chuẩn, B6 ghi `OPEN` và đề xuất cập nhật B2 chứ không tự khóa nghĩa mới.

**Quan hệ với mobile.** Check-in trực tuyến trên mobile không tạo một bộ use case riêng. Nó là cùng một use case với tác nhân và điều kiện thiết bị tương ứng, theo đúng quy định của Tầng B và B3 §5.

**Quan hệ với chatbot.** Chatbot hỗ trợ mua vé cũng **không** tạo bộ use case riêng và không phải một tác nhân nghiệp vụ mới. Nó là một kênh để buyer thực hiện `UC-24` và `UC-25`, và để buyer khởi tạo ý định mua rồi đi tiếp bằng đúng quy tắc của `UC-10`. Mọi thay đổi trạng thái vẫn đi qua các lệnh ở dòng B của B4, theo đúng B4 §8.2.

## 2. Tác nhân

| Tác nhân | Nguồn B2 | Vai trò trong B6 |
|---|---|---|
| **Người mua** (*buyer*) | B2 §2 | Tìm và xem sự kiện đã công bố, tạo và hủy đơn, thanh toán, nhận và tải vé của chính mình |
| **Nhà tổ chức** (*organizer*) | B2 §2 | Quản lý vòng đời sự kiện thuộc mình, cấu hình bán và khuyến mãi, gửi yêu cầu hủy, check-in, xem báo cáo đối soát của sự kiện mình |
| **Quản trị viên** (*admin*) | B2 §2 | Kiểm duyệt sự kiện và nhập tỷ lệ phí nền tảng, xử lý yêu cầu hủy, đối soát và đánh dấu chi trả |
| **Cổng thanh toán** | B3 §3.1 | Hệ thống ngoài; gửi kết quả thanh toán và kết quả hoàn tiền, có thể lặp hoặc đến muộn |
| **Mốc thời gian nghiệp vụ** | B4 §2 | Không phải người dùng; kích hoạt các chuyển trạng thái theo thời gian như hết hạn đơn, bắt đầu và kết thúc sự kiện |
| **Người dùng chưa có tài khoản** | `E01`; `BIZ-132` | Chỉ xuất hiện ở `UC-21`; sau khi đăng ký thì trở thành người mua |
| **Nguồn danh tính** | `PRJ-003`; `E03` | Hệ thống ngoài quản lý đăng nhập và vai trò; việc duyệt hồ sơ organizer chỉ hoàn tất khi nó xác nhận đã cấp vai trò |

Người xuất trình vé **không** phải tác nhân có hồ sơ: `BIZ-073` chốt sản phẩm không mô hình hóa danh tính người tham dự.

## 3. Danh mục use case và truy vết về B4

Cột cuối là phép thử bắt buộc của B6: mọi use case phải truy được về ít nhất một sự kiện miền ở B4-v0.13. Không truy được thì hoặc use case thừa, hoặc B4 còn thiếu sự kiện.

### 3.1 Quy trình 1 — Vòng đời sự kiện

| Mã | Use case | Tác nhân chính | Sự kiện miền B4 | Mức đặc tả đề xuất |
|---|---|---|---|---|
| `UC-01` | Tạo bản nháp sự kiện, gồm chọn địa điểm và gắn phân loại | Organizer | `A01`; địa điểm và phân loại theo B4 §8.4 | Liệt kê |
| `UC-02` | Cấu hình bán, loại vé, nguồn cung và khuyến mãi | Organizer | `A02` | **Đầy đủ** |
| `UC-03` | Gửi sự kiện để duyệt | Organizer | `A03` | Liệt kê |
| `UC-04` | Duyệt sự kiện và nhập tỷ lệ phí nền tảng | Admin | `A04` | **Đầy đủ** |
| `UC-05` | Trả sự kiện về bản nháp | Admin | `A05` | Liệt kê |
| `UC-06` | Công bố sự kiện đã duyệt | Organizer | `A06` | Liệt kê |
| `UC-07` | Gửi yêu cầu hủy sự kiện | Organizer | `A09` | Liệt kê |
| `UC-08` | Xử lý yêu cầu hủy hoặc chủ động hủy sự kiện | Admin | `A10`, `A11` | **Đầy đủ** |
| `UC-09` | Hủy trực tiếp sự kiện chưa có đơn thu tiền | Organizer | `A11` | Liệt kê |

### 3.2 Quy trình 2 — Mua vé

| Mã | Use case | Tác nhân chính | Sự kiện miền B4 | Mức đặc tả đề xuất |
|---|---|---|---|---|
| `UC-10` | Chọn vé và tạo đơn kèm giữ chỗ | Buyer | `B01` | **Đầy đủ** |
| `UC-11` | Áp dụng mã khuyến mãi cho đơn | Buyer | `B02` | **Đầy đủ** |
| `UC-12` | Thanh toán đơn | Buyer; cổng thanh toán là tác nhân phụ | `B03`, `B04`, `B05` | **Đầy đủ** |
| `UC-13` | Hủy đơn của chính mình | Buyer | `B08` | **Đầy đủ** |
| `UC-14` | Phát hành vé rồi cho buyer xem và tải lại | Không có tác nhân chính ở bước phát hành; buyer là tác nhân ở bước xem/tải | `B06`, `B07` | **Đầy đủ** |

### 3.3 Quy trình 3 — Hoàn tiền, đối soát và chi trả

| Mã | Use case | Tác nhân chính | Sự kiện miền B4 | Mức đặc tả đề xuất |
|---|---|---|---|---|
| `UC-15` | Xử lý hoàn tiền cho một khoản thu | Không có tác nhân chính; được gọi bởi `UC-08`, `UC-12`, `UC-14` | `C01`–`C04` | **Đầy đủ** |
| `UC-16` | Theo dõi tiến độ hoàn của một sự kiện bị hủy | Admin | `C05`, `C06` | Liệt kê |
| `UC-17` | Xem báo cáo đối soát của sự kiện | Organizer, admin | `C07` | Liệt kê |
| `UC-18` | Xác nhận đối soát | Admin | `C09` | Liệt kê |
| `UC-19` | Đánh dấu sự kiện đã chi trả | Admin | `C10` | **Đầy đủ** |

### 3.4 Quy trình 4 — Check-in

| Mã | Use case | Tác nhân chính | Sự kiện miền B4 | Mức đặc tả đề xuất |
|---|---|---|---|---|
| `UC-20` | Check-in vé bằng QR | Organizer | `D01`, `D02`, `D03` | **Đầy đủ** |

### 3.5 Năng lực tài khoản

| Mã | Use case | Tác nhân chính | Sự kiện miền B4 | Mức đặc tả đề xuất |
|---|---|---|---|---|
| `UC-21` | Đăng ký tài khoản | Người dùng chưa có tài khoản | `E01` | Liệt kê |
| `UC-22` | Nộp đơn đăng ký trở thành organizer | Buyer | `E02` | Liệt kê |
| `UC-23` | Duyệt hoặc từ chối hồ sơ organizer | Admin | `E03`, `E04` | **Đầy đủ** |

Ba ca trên nay truy vết bình thường về dòng sự kiện hỗ trợ của B4-v0.13. **Không còn ngoại lệ nào** đối với phép thử ở đầu §3.

### 3.6 Năng lực tối giản và truy vấn chỉ đọc

Các ca sau chỉ liệt kê, không đặc tả đầy đủ. Một truy vấn chỉ đọc không tự tạo sự kiện miền, theo đúng B4 §2; cột cuối vì vậy dẫn sự kiện mà truy vấn **đọc kết quả**. Riêng `UC-26` làm thay đổi trạng thái nên có mã sự kiện riêng là `E05`.

| Mã | Use case | Tác nhân chính | Căn cứ |
|---|---|---|---|
| `UC-24` | Tìm sự kiện đã công bố | Buyer | Đọc kết quả của `A06`; `BIZ-127` |
| `UC-25` | Xem chi tiết một sự kiện đã công bố | Buyer | Đọc kết quả của `A06` |
| `UC-26` | Theo dõi một organizer | Buyer | `E05` |
| `UC-27` | Xem hồ sơ organizer công khai | Buyer | Đọc kết quả của `E03`; `BIZ-141` giới hạn ở hồ sơ `ACTIVE` |
| `UC-28` | Xem danh sách đơn và vé của chính mình | Buyer | Đọc kết quả của `B01` và `B06`; quyền theo `BIZ-072` |
| `UC-29` | Xem danh sách sự kiện thuộc chính mình | Organizer | Đọc kết quả của `A01`; quyền sở hữu theo `BIZ-135` |
| `UC-30` | Xem thống kê đọc của sự kiện thuộc chính mình | Organizer | Đọc kết quả của `B01`, `B06` và `C07`; B4 §8.5 |

## 4. Phát hiện phải báo trước khi đặc tả

### 4.1 Truy vết cho năng lực tài khoản — đã giải quyết bằng dòng E của B4

Khi soạn `B6-v0.1`, ba use case `UC-21`, `UC-22`, `UC-23` không truy được về bất kỳ mã sự kiện nào của B4-v0.9: toàn bộ 44 mã khi đó nằm ở dòng A, B, C, D và T, không dòng nào mô tả việc tạo tài khoản, nộp hồ sơ organizer hay duyệt hồ sơ. Codex cũng phát hiện độc lập cùng vấn đề khi soạn B7.

Nội dung nghiệp vụ thì đã có sẵn ở B4 §8.1 dưới dạng văn xuôi, cùng sáu quyết định `USER_CONFIRMED` `BIZ-132`, `BIZ-134`, `BIZ-136`–`BIZ-141`. Cái thiếu chỉ là **mã sự kiện để trỏ tới**.

**Cách xử lý đã chốt.** Lê Văn Minh quyết định bổ sung một **dòng sự kiện hỗ trợ** vào B4 thay vì để lại ngoại lệ. B4-v0.10 thêm §8.1.1 với bốn mã `E01`–`E04`: tài khoản đã được đăng ký, hồ sơ organizer đã được nộp, đã được duyệt, đã bị từ chối.

Dòng E là dòng hỗ trợ, **không** phải mạch nghiệp vụ chính thứ năm. B3-v0.9 vẫn giữ nguyên bốn quy trình và không mở thêm quy trình nào cho năng lực tài khoản.

**Hệ quả:**

- Ba use case truy vết bình thường; phép thử của B6 không còn ngoại lệ nào.
- Vòng rà chéo sau đó bổ sung tiếp `E05` cho quan hệ theo dõi organizer, đưa B4 lên `B4-v0.11` và B5 lên `B5-v0.9`; Lê Văn Minh đã duyệt lại đúng thứ tự hai phiên bản này (`GOV-021`).
- B2-v0.9 và B3-v0.9 **không** bị sửa và giữ nguyên `APPROVED`.
- B7 của Codex cần cập nhật dòng khai phiên bản đầu vào; đó là tệp thuộc làn của Codex nên B6 không đụng tới.

**Vì sao chọn cách này thay vì để ngoại lệ.** Ba luồng tài khoản chắc chắn sẽ được hiện thực. Nếu để ngoại lệ thì chúng không có đặc tả, không có yêu cầu chức năng ở B8 và không có ca kiểm thử truy ngược được. Thời điểm sửa cũng rẻ nhất lúc này vì B6 và B7 đều còn ở `DRAFT`; càng về sau vòng lan càng rộng.

### 4.2 `B6-OPEN-02` — đã đóng phần nghĩa nghiệp vụ; tên trạng thái chờ B12/B13

`BIZ-150` đã khóa nghĩa cần cho B6/B8: hoàn một khoản thu trùng hoặc đến muộn không tạo một trạng thái nghiệp vụ riêng cho đơn và không làm đơn hoặc vé hợp lệ mất hiệu lực. B6 tiếp tục mô tả kết quả bằng lời, không đặt tên trạng thái đơn. `BIZ-130` chỉ còn `OPEN` cho tên/chuyển trạng thái và cách biểu diễn ở B12/B13; nó không còn chặn B6 hoặc B8.

### 4.3 `B6-OPEN-03` — đã đóng: hệ thống không kiểm sức chứa vật lý

Theo `BIZ-149`, organizer tự quyết định nguồn cung vé; hệ thống không biết và không kiểm sức chứa vật lý của địa điểm. `UC-02` vì vậy cố ý không đặt điều kiện kiểm tra giữa địa điểm và nguồn cung. Đây là giới hạn phạm vi đã xác nhận, không còn là thiếu sót cần lấp.

### 4.4 `B6-OPEN-04` — đã đóng phần trường nghiệp vụ

Theo `BIZ-151`, hồ sơ organizer tối thiểu giữ tên tổ chức, mô tả ngắn và lý do từ chối khi có; không yêu cầu giấy tờ kinh doanh, tài khoản ngân hàng hoặc bộ nhận diện thương hiệu. Kiểu dữ liệu, schema và hợp đồng vẫn chờ B12/B13.

### 4.5 `B6-OPEN-05` — đã đóng, là báo động giả

`B6-v0.1` ghi rằng B2 không có mục từ nào cho khái niệm hồ sơ organizer. **Điều đó sai.** Khi kiểm lại, B2-v0.9 có đủ ba mục từ liên quan:

| Mục từ B2 | Nội dung |
|---|---|
| **Hồ sơ nghiệp vụ ứng dụng** | Ứng dụng giữ một hồ sơ nghiệp vụ tối thiểu tách khỏi dữ liệu danh tính do Keycloak quản lý |
| **Đăng ký organizer** | Buyer nộp hồ sơ ở `PENDING`; khi admin duyệt và Keycloak xác nhận cấp role thì hồ sơ thành `ACTIVE` |
| **Từ chối organizer** | Admin phải nhập và hệ thống lưu lý do; hồ sơ chuyển `REJECTED`, không hỗ trợ gửi lại |

Nguyên nhân báo nhầm: phép kiểm của tôi tìm đúng cụm “hồ sơ organizer” làm tên mục từ, trong khi B2 đặt tên mục từ theo **hành vi** là “Đăng ký organizer” và “Từ chối organizer”. Khái niệm có đủ, chỉ khác cách đặt tên.

Mục này đóng lại. B2 không cần sửa gì.

### 4.6 Ghi chú về hai cụm từ mô tả

“Khoản thu” và “nguồn cung” xuất hiện trong phần diễn giải của B2-v0.9 và được B3-v0.9 dùng xuyên suốt, nhưng không phải mục từ riêng. B2 dùng **Hạn mức bán** và **Tồn kho vé còn lại** làm mục từ cho khái niệm nguồn cung. B6 giữ nguyên cách dùng của B3 để không tạo cách gọi thứ ba, và không đề xuất đổi vì đây là từ mô tả chứ không phải khái niệm nghiệp vụ mới.

## 5. Phạm vi đặc tả đầy đủ

Tầng C giới hạn 8–12 ca đặc tả đầy đủ; ca hỗ trợ chỉ liệt kê trong biểu đồ. Chốt **12 ca**, chọn theo mật độ bất biến và nhánh lỗi:

| Mã | Use case | Vì sao cần đặc tả đầy đủ |
|---|---|---|
| `UC-10` | Chọn vé và tạo đơn | Chạm `INV-01`, `INV-02`, `INV-03`, `INV-04`; là tâm của `B5-OPEN-02` |
| `UC-12` | Thanh toán đơn | Chạm `INV-05`, `HOT-03`; callback lặp, đến muộn, thu trùng |
| `UC-14` | Phát hành vé rồi cho buyer xem và tải lại | Chạm `INV-07`; phân biệt phát hành lỗi với gửi lỗi |
| `UC-20` | Check-in vé bằng QR | Chạm `INV-09`, `HOT-04`; hai thiết bị quét đồng thời |
| `UC-15` | Xử lý hoàn tiền một khoản thu | Chạm `INV-08`; một yêu cầu hoàn logic cho mỗi khoản thu |
| `UC-08` | Xử lý yêu cầu hủy hoặc chủ động hủy sự kiện | Chạm `HOT-01`; lan tới đơn, vé, hoàn tiền, đối soát |
| `UC-13` | Hủy đơn của chính mình | Chạm `INV-06`; trả tài nguyên đúng một lần |
| `UC-11` | Áp dụng mã khuyến mãi | Chạm `INV-06`; nhiều điều kiện từ chối phải phân biệt |
| `UC-02` | Cấu hình bán, loại vé, nguồn cung và khuyến mãi | Ràng buộc `salesMode`, một sector cho một đơn, giá và giới hạn mua theo cửa sổ mở bán đã cấu hình |
| `UC-04` | Duyệt sự kiện và nhập tỷ lệ phí | Chạm `INV-11`; tỷ lệ phí cố định sau phê duyệt |
| `UC-19` | Đánh dấu sự kiện đã chi trả | Chạm `INV-10`; đúng một lần `PAID` |
| `UC-23` | Duyệt hoặc từ chối hồ sơ organizer | `BIZ-140` đặt điều kiện hoàn tất phụ thuộc xác nhận từ Keycloak, tạo một nhánh lỗi thật; `BIZ-137` bắt buộc lưu lý do từ chối |

Mười tám ca còn lại chỉ liệt kê — 30 use case trừ 12 ca đặc tả đầy đủ. Trong đó `UC-21` và `UC-22` thuộc năng lực tài khoản; `UC-23` **không** nằm trong nhóm này vì đã được đặc tả đầy đủ tại §6.12.

Mười một ca đầu được Lê Văn Minh chốt ngày 2026-08-21. `UC-23` được bổ sung sau khi dòng E của B4 làm nó truy vết được và đã được Lê Văn Minh xác nhận giữ ở mức đặc tả đầy đủ (`GOV-022`). Tất cả được đặc tả tại §6.

## 6. Đặc tả các ca cốt lõi

Mỗi đặc tả dưới đây mô tả hành vi nghiệp vụ. Không đặc tả nào chỉ định service, schema, API, aggregate hay cơ chế đồng bộ; những điều đó thuộc B7 và B11–B13.

Cột **Bất biến chạm tới** dẫn mã của B4-v0.13 để B7 nhặt được; B6 không tự phát biểu bất biến mới.

### 6.1 `UC-02` — Cấu hình bán, loại vé, nguồn cung và khuyến mãi

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Nhà tổ chức |
| Kích hoạt | Organizer mở sự kiện của mình để cấu hình |
| Mức ưu tiên | Trung bình |
| Tiền điều kiện | Sự kiện thuộc organizer và đang ở `DRAFT` |
| Bất biến chạm tới | `INV-02` (một đơn thuộc một sự kiện và một sector) hình thành từ cấu hình này |
| Truy vết | `A02`; `BIZ-001`–`BIZ-003`, `BIZ-015`–`BIZ-020`, `BIZ-025`, `BIZ-039`–`BIZ-048`, `BIZ-149` |
| Test case tương ứng | Đổi chế độ bán khi còn nháp; từ chối giá không lớn hơn 0; từ chối thiếu giới hạn mua; từ chối sửa sau khi đã gửi duyệt — hiện thực hóa tại B15 |

**Luồng chính**

1. Organizer chọn đúng một `salesMode` cho sự kiện: `QUANTITY` hoặc `SEAT_MAP`.
2. Với `QUANTITY`, organizer khai các loại vé và hạn mức bán tương ứng, không dùng sơ đồ.
3. Với `SEAT_MAP`, organizer khai sơ đồ gồm các sector `SEATED` hoặc `STANDING`; một sector có thể chứa nhiều loại vé. Sector `SEATED` quản lý từng ghế định danh, sector `STANDING` quản lý số lượng trong khu.
4. Organizer nhập giá cho mọi loại vé; mỗi giá phải lớn hơn 0.
5. Organizer nhập giới hạn mua cho sự kiện; đây là trường bắt buộc.
6. Organizer cấu hình khuyến mãi của sự kiện với `startAt` và `endAt`. Hiệu lực được suy ra từ hai mốc này; không có thao tác bật, tạm dừng hay vô hiệu hóa thủ công.
7. Hệ thống lưu cấu hình và giữ sự kiện ở `DRAFT`.

**Luồng thay thế**

- **Đổi `salesMode`.** Organizer phải xác nhận việc xóa phần cấu hình không tương thích. Hệ thống không tự chuyển đổi cấu hình cũ sang mode mới.

**Ngoại lệ**

- Sự kiện không ở `DRAFT`: từ chối thay đổi. Sau khi gửi duyệt, cấu hình thương mại và `salesMode` bị khóa cho tới khi sự kiện quay lại `DRAFT`.
- Giá loại vé không lớn hơn 0: từ chối lưu.
- Thiếu giới hạn mua: từ chối lưu.

**Hậu điều kiện**

- Sự kiện có đúng một `salesMode`, tập loại vé và nguồn cung nhất quán với mode đó, giá hợp lệ và giới hạn mua đã khai.
- Cấu hình sẵn sàng để gửi duyệt.

**Giới hạn của B6 tại ca này**

Organizer quyết định nguồn cung vé. Hệ thống không biết và không kiểm sức chứa vật lý của địa điểm (`BIZ-149`).

### 6.2 `UC-04` — Duyệt sự kiện và nhập tỷ lệ phí nền tảng

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Quản trị viên |
| Kích hoạt | Có sự kiện ở `PENDING_APPROVAL` chờ kiểm duyệt |
| Mức ưu tiên | Trung bình |
| Tiền điều kiện | Sự kiện ở `PENDING_APPROVAL`; thỏa thuận phí đã hoàn tất ngoài hệ thống |
| Bất biến chạm tới | `INV-11` — tỷ lệ phí dùng khi đối soát phải đúng giá trị đã phê duyệt và không bị đổi âm thầm |
| Truy vết | `A04`; `BIZ-034`, `BIZ-035`, `BIZ-142` |
| Test case tương ứng | Duyệt kèm nhập tỷ lệ phí; tỷ lệ không đổi sau phê duyệt; trả về nháp khi từ chối — hiện thực hóa tại B15 |

**Luồng chính**

1. Admin xem nội dung và cấu hình thương mại của sự kiện, gồm cả khuyến mãi.
2. Admin chấp thuận và nhập tỷ lệ phần trăm phí nền tảng theo thỏa thuận đã có ngoài hệ thống.
3. Hệ thống cố định tỷ lệ phí cho sự kiện này.
4. Sự kiện chuyển sang `APPROVED`. Việc phê duyệt không tự công bố và không tự mở bán.

**Luồng thay thế**

- **Từ chối hoặc trả lại.** Chuyển sang `UC-05`: sự kiện quay về `DRAFT`, organizer được sửa rồi gửi duyệt lại. Không tồn tại trạng thái sự kiện bền vững `REJECTED`.

**Ngoại lệ**

- Sự kiện không ở `PENDING_APPROVAL`: từ chối thao tác duyệt.

**Hậu điều kiện**

- Sự kiện ở `APPROVED` và có một tỷ lệ phí đã cố định.
- Đối soát về sau dùng đúng tỷ lệ này; xem `UC-19`.

**Giới hạn của B6 tại ca này**

Admin không có giao diện quản lý khuyến mãi riêng. Nơi lưu giá trị tỷ lệ phí thuộc B12, không thuộc B6.

### 6.3 `UC-08` — Xử lý yêu cầu hủy hoặc chủ động hủy sự kiện

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Quản trị viên |
| Tác nhân phụ | Nhà tổ chức (người gửi yêu cầu) |
| Kích hoạt | Organizer gửi yêu cầu hủy cho sự kiện đã có đơn thu tiền, hoặc admin chủ động hủy |
| Mức ưu tiên | Cao |
| Tiền điều kiện | `now < eventStartAt` |
| Bất biến chạm tới | `HOT-01` — một lần hủy lan tới nhiều đơn, vé và yêu cầu hoàn với lỗi từng phần |
| Truy vết | `A10`, `A11`, `B13`, `B14`, `C05`, `C06`; `BIZ-097`–`BIZ-102` |
| Test case tương ứng | Từ chối yêu cầu không khởi động hoàn; chặn hủy sau khi sự kiện bắt đầu; mọi vé mất hiệu lực và nguồn cung không mở lại — hiện thực hóa tại B15 |

**Luồng chính**

1. Admin xem yêu cầu hủy và xác nhận.
2. Sự kiện chuyển sang `CANCELLED` và đóng bán ngay.
3. Toàn bộ vé của sự kiện bị vô hiệu hóa; nguồn cung **không** trở lại khả dụng.
4. Mọi đơn chưa thanh toán và giữ chỗ đang hoạt động bị hủy. Hệ thống chặn lần thanh toán mới, trả phần giới hạn mua và lượt khuyến mãi đúng một lần, nhưng không mở lại nguồn cung.
5. Hệ thống chọn từng đơn thuộc sự kiện mà khoản thu hợp lệ được giữ lại vẫn còn số tiền phải hoàn, rồi chuyển sang `UC-15` cho từng khoản thu đó.
6. Hệ thống cho admin theo dõi tiến độ hoàn tiền của sự kiện bị hủy.

**Luồng thay thế**

- **Admin từ chối yêu cầu.** Yêu cầu hủy chuyển `REJECTED`, lý do từ chối là tùy chọn, trạng thái sự kiện không đổi, và quy trình dừng trước khi tạo bất kỳ xử lý hoàn nào.
- **Organizer hủy trực tiếp.** Khi sự kiện chưa có đơn thu tiền, organizer tự hủy mà không cần admin; xem `UC-09`.

**Ngoại lệ**

- `now >= eventStartAt`: hệ thống không chấp nhận hủy sự kiện trong phạm vi sản phẩm.
- Callback thu tiền đến sau khi sự kiện đã hủy: xử lý theo nhánh thanh toán đến muộn ở `UC-12`, không giải phóng tài nguyên lần thứ hai.

**Hậu điều kiện**

- Sự kiện không còn vé hợp lệ để check-in và không mở lại nguồn cung.
- Mỗi khoản thu đủ điều kiện có đúng một yêu cầu hoàn logic; các giao dịch thu thừa do thanh toán trùng đi theo nhánh riêng và không thuộc lô này.

**Giới hạn của B6 tại ca này**

B6 chỉ mô tả kết quả nghiệp vụ. Không bổ sung các kịch bản mạng chập chờn hoặc biến thể kỹ thuật của việc hủy sự kiện khi chúng không phục vụ trục nghiên cứu (`BIZ-148`); cơ chế điều phối vẫn chờ đúng gate kiến trúc.

### 6.4 `UC-10` — Chọn vé và tạo đơn kèm giữ chỗ

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Người mua |
| Kích hoạt | Buyer chọn vé của một sự kiện đang mở bán |
| Mức ưu tiên | Cao |
| Tiền điều kiện | Sự kiện đã duyệt, đã công bố, chưa hủy và `now` nằm trong cửa sổ mở bán |
| Bất biến chạm tới | `INV-01`, `INV-02`, `INV-03`, `INV-04` |
| Truy vết | `B01`; `BIZ-001`, `BIZ-016`, `BIZ-017`, `BIZ-026`–`BIZ-029`, `BIZ-080` |
| Test case tương ứng | Hai buyer tranh cùng một ghế; vượt giới hạn mua; trộn nhiều sector; một đơn một giữ chỗ một hạn chung — hiện thực hóa tại B15 |

**Luồng chính**

1. Buyer chọn một hoặc nhiều dòng vé thuộc cùng một sự kiện. Với `SEAT_MAP`, mọi lựa chọn phải thuộc cùng một sector.
2. Hệ thống kiểm đồng thời: trạng thái mở bán của sự kiện, tồn kho hoặc ghế còn khả dụng, giới hạn mua của tài khoản và điều kiện của từng loại vé.
3. Nếu hợp lệ, hệ thống tạo một đơn với **đúng một giữ chỗ** và **một thời hạn chung** cho toàn bộ dòng vé.
4. Tồn kho và phần giới hạn mua tương ứng được cam kết tạm thời theo thời hạn đó.

**Ngoại lệ** — mỗi trường hợp phải trả kết quả phân biệt được cho buyer

- Sự kiện không còn đủ điều kiện bán.
- Ghế hoặc số lượng không còn khả dụng: không tạo giữ chỗ.
- Giới hạn mua theo tài khoản và sự kiện bị vượt khi cộng vé đang giữ với vé đã mua: không tạo giữ chỗ.
- Lựa chọn trộn nhiều sector trong một đơn `SEAT_MAP`.

**Hậu điều kiện**

- Buyer có một đơn thuộc đúng một sự kiện, có một giữ chỗ và một mốc hết hạn chung.
- Không cho đổi lựa chọn trong đơn đã tạo; muốn vé khác thì tạo đơn mới.

**Giới hạn của B6 tại ca này**

Quy tắc giới hạn mua áp cho tài khoản, không ngăn một người dùng nhiều tài khoản. Ranh giới bảo vệ đồng thời giữa nguồn cung, giữ chỗ và đơn là `B5-OPEN-02`, thuộc B7; B6 không suy ra vị trí kiểm tra hay cơ chế khóa.

### 6.5 `UC-11` — Áp dụng mã khuyến mãi cho đơn

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Người mua |
| Kích hoạt | Buyer nhập mã khuyến mãi cho đơn vừa tạo |
| Mức ưu tiên | Trung bình |
| Tiền điều kiện | Đơn còn hiệu lực và chưa áp mã nào |
| Bất biến chạm tới | `INV-06` — lượt khuyến mãi không được giữ hoặc trả hai lần |
| Truy vết | `B02`; `BIZ-039`–`BIZ-048`, `BIZ-069`, `BIZ-081` |
| Test case tương ứng | Sáu lý do từ chối phân biệt được; giữ lượt đúng một lần; trả lượt khi đơn hết hạn — hiện thực hóa tại B15 |

**Luồng chính**

1. Buyer nhập một mã khuyến mãi.
2. Hệ thống kiểm mã thuộc đúng sự kiện của đơn; chuỗi mã là duy nhất trong phạm vi sự kiện.
3. Hệ thống tự xác định hiệu lực theo `startAt` và `endAt`.
4. Hệ thống kiểm loại giảm, tổng lượt còn lại, quy tắc một lần cho mỗi tài khoản với mỗi mã, và quy tắc không cộng dồn.
5. Hệ thống tính số tiền cuối và yêu cầu số tiền này lớn hơn 0.
6. Nếu mọi điều kiện đạt, mã được áp và một lượt dùng được giữ theo đúng thời hạn của đơn.

**Ngoại lệ** — sáu trường hợp phải phân biệt được, không gộp thành một thông báo chung

- Mã không tồn tại trong sự kiện của đơn.
- Mã chưa tới hiệu lực hoặc đã hết hiệu lực theo `startAt`/`endAt`.
- Mã đã hết tổng lượt.
- Tài khoản đã dùng chính mã này trước đó.
- Đơn đã có một mã và buyer yêu cầu áp mã thứ hai.
- Số tiền cuối sau giảm không lớn hơn 0.

**Hậu điều kiện**

- Đơn có tối đa một mã khuyến mãi được áp.
- Lượt dùng được giữ cùng thời hạn đơn và được trả đúng một lần khi đơn hết hạn, bị hủy hoặc phát hành thất bại.

### 6.6 `UC-12` — Thanh toán đơn

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Người mua |
| Tác nhân phụ | Cổng thanh toán |
| Kích hoạt | Buyer khởi tạo thanh toán cho đơn của mình |
| Mức ưu tiên | Cao |
| Tiền điều kiện | Đơn còn hiệu lực, chưa hết hạn, chưa bị hủy và chưa có kết quả thu hợp lệ |
| Bất biến chạm tới | `INV-05`, `HOT-03` |
| Truy vết | `B03`, `B04`, `B05`, `B10`, `B11`; `BIZ-012`, `BIZ-013`, `BIZ-031`, `BIZ-032`, `BIZ-106` |
| Test case tương ứng | Kết quả thanh toán lặp; tiền về sau khi đơn hết hạn; thu trùng một nghĩa vụ; chặn lần thử song song — hiện thực hóa tại B15 |

**Luồng chính**

1. Buyer khởi tạo một lần thanh toán cho đơn.
2. Hệ thống chuyển yêu cầu tới cổng thanh toán và chờ kết quả. Không tạo lần thử mới song song khi lần hiện tại chưa kết thúc.
3. Cổng thanh toán gửi kết quả về.
4. Hệ thống xác minh kết quả thuộc đúng đơn, đúng lần thử và đúng số tiền.
5. Hệ thống giữ **đúng một** kết quả thu hợp lệ cho đơn.
6. Hệ thống chốt phần nguồn cung, giới hạn mua và lượt khuyến mãi đang giữ, rồi yêu cầu phát hành vé theo `UC-14`.

**Luồng thay thế**

- **Lần thử thất bại.** Nếu đơn còn hiệu lực, buyer tạo lần thử tiếp theo theo thứ tự.

**Ngoại lệ**

- **Callback lặp mang cùng kết quả đã xử lý.** Không tạo tác dụng phụ lặp, không phát hành vé lần thứ hai.
- **Tiền đến sau khi đơn đã hết hạn hoặc bị hủy**, kể cả do sự kiện bị hủy: đây là thanh toán đến muộn. Chuyển sang `UC-15` để hoàn toàn bộ. Không giải phóng giữ chỗ, tồn kho, giới hạn mua hay lượt khuyến mãi lần thứ hai.
- **Cùng một nghĩa vụ bị thu nhiều lần.** Giữ một giao dịch hợp lệ; mỗi giao dịch thừa chuyển sang `UC-15` độc lập. Vé, tồn kho và giới hạn của đơn hợp lệ không thay đổi.
- **Khởi tạo thanh toán sau khi đơn đã hết hạn hoặc bị hủy.** Từ chối, không tạo lần thử mới.

**Hậu điều kiện**

- Thành công: đơn có đúng một khoản thu hợp lệ và các tài nguyên đã giữ được chốt.
- Ngoại lệ sau thu tiền: một yêu cầu hoàn được chuyển sang `UC-15` mà không tạo tác dụng phụ trùng.

**Giới hạn của B6 tại ca này**

B6 không đặt tên trạng thái đơn theo `BIZ-130`, không chọn giao thức với cổng thanh toán và không quyết định cơ chế bảo đảm idempotency. Nghĩa nghiệp vụ đã được khóa tại §4.2; tên/chuyển trạng thái và cách biểu diễn chờ B12/B13.

### 6.7 `UC-13` — Hủy đơn của chính mình

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Người mua |
| Kích hoạt | Buyer chọn hủy đơn của mình |
| Mức ưu tiên | Trung bình |
| Tiền điều kiện | Đơn thuộc chính buyer, còn giữ chỗ và chưa có thanh toán thành công |
| Bất biến chạm tới | `INV-06` |
| Truy vết | `B08`; `BIZ-071`, `BIZ-090`, `BIZ-091` |
| Test case tương ứng | Hủy lặp là idempotent; trả tài nguyên đúng một lần; từ chối hủy khi đã thanh toán thành công — hiện thực hóa tại B15 |

**Luồng chính**

1. Buyer yêu cầu hủy toàn bộ đơn.
2. Hệ thống chặn mọi lần thanh toán mới cho đơn.
3. Hệ thống trả giữ chỗ, nguồn cung, phần giới hạn mua và lượt khuyến mãi **đúng một lần**.

**Luồng thay thế**

- **Hủy lặp.** Thao tác là idempotent: kết quả giống lần đầu, tài nguyên không được trả lần thứ hai.
- **Đơn hết hạn thay vì bị hủy.** Cùng hậu quả trả tài nguyên đúng một lần, nhưng do mốc thời gian kích hoạt chứ không do buyer.

**Ngoại lệ**

- Đơn đã có thanh toán thành công: từ chối hủy. Trường hợp cần hoàn tiền đi theo `UC-15` với nguyên nhân tương ứng.

**Hậu điều kiện**

- Không có vé nào được phát hành cho đơn này.
- Mọi tài nguyên tạm giữ đã được trả đúng một lần.

### 6.8 `UC-14` — Phát hành vé rồi cho buyer xem và tải lại

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Bước phát hành không có tác nhân chính — nó là hệ quả của `UC-12`. Buyer là tác nhân chính của bước xem và tải lại |
| Kích hoạt | Đơn có một khoản thu hợp lệ được xác nhận |
| Mức ưu tiên | Cao |
| Tiền điều kiện | **Bước phát hành:** đơn có một khoản thu hợp lệ. Không có điều kiện nào về phiên đăng nhập của buyer — phát hành là hệ quả của việc thu tiền và phải xảy ra kể cả khi buyer đã rời trình duyệt. **Bước xem và tải lại:** buyer đã đăng nhập và vé thuộc đơn của chính buyer |
| Bất biến chạm tới | `INV-07` — thử lại phát hành hoặc gửi vé không tạo thêm quyền tham dự ngoài số vé đã mua |
| Truy vết | `B06`, `B07`, `B12`; `BIZ-060`, `BIZ-061`, `BIZ-076`–`BIZ-079` |
| Test case tương ứng | Thử lại phát hành không sinh thêm quyền; gửi vé lỗi không làm mất hiệu lực; phát hành lỗi trả tồn kho theo điều kiện; buyer khác không xem được mã vé — hiện thực hóa tại B15 |

**Luồng chính**

1. Hệ thống phát hành các vé riêng lẻ tương ứng với dòng vé của đơn.
2. Buyer xem hoặc tải QR của vé thuộc đơn của chính mình.
3. Hệ thống đồng thời cố gắng gửi thông tin vé và QR tới buyer.

**Luồng thay thế**

- **Gửi thông tin vé thất bại.** Đây không phải lỗi phát hành. Vé đã phát hành vẫn hợp lệ, không kích hoạt hoàn tiền, và buyer được tải lại hoặc yêu cầu gửi lại.

**Ngoại lệ**

- **Không phát hành được đầy đủ vé sau khi đã thu tiền.** Hệ thống vô hiệu hóa mọi vé dở dang của đơn, trả lượt khuyến mãi đúng một lần và chuyển sang `UC-15` để hoàn toàn bộ. Tồn kho và giới hạn mua chỉ được trả nếu sự kiện vẫn đủ điều kiện bán.
- **Vé đã bị hủy hoặc vô hiệu.** Vé vẫn hiện trong lịch sử của buyer với trạng thái tương ứng, nhưng không cho xem hoặc tải QR thô.

**Hậu điều kiện**

- Mỗi vé hợp lệ có đúng một quyền tham dự và sẵn sàng cho `UC-20`.
- Hoàn tiền được ghi trên khoản thu và yêu cầu hoàn, không tạo trạng thái “đã hoàn” riêng cho vé.

**Giới hạn của B6 tại ca này**

Organizer và admin không có chức năng liệt kê hoặc tải QR thô của buyer. Phát hành vé thất bại và gửi vé thất bại là hai sự kiện khác bản chất, không được gộp.

### 6.9 `UC-15` — Xử lý hoàn tiền cho một khoản thu

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Không có — đây là ca **được gọi** bởi `UC-08`, `UC-12` hoặc `UC-14`, thừa hưởng tác nhân của ca gọi nó |
| Tác nhân phụ | Cổng thanh toán — thực hiện lệnh hoàn và trả kết quả |
| Kích hoạt | Một trong bốn nguyên nhân: thanh toán đến muộn, phát hành vé thất bại sau thu tiền, thanh toán trùng, hoặc hủy sự kiện |
| Mức ưu tiên | Cao |
| Tiền điều kiện | Đã xác định được khoản thu cụ thể và nguyên nhân hoàn |
| Bất biến chạm tới | `INV-08` — mỗi khoản thu có tối đa một yêu cầu hoàn logic và một kết quả hoàn thành công |
| Truy vết | `C01`–`C04`; `BIZ-011`–`BIZ-014`, `BIZ-033`, `BIZ-100`–`BIZ-102` |
| Test case tương ứng | Kích hoạt hoàn lặp hội tụ về một yêu cầu; lần hoàn thất bại vẫn thử lại được; hoàn khoản thừa không đụng vé hợp lệ — hiện thực hóa tại B15 |

**Luồng chính**

1. Hệ thống tra xem khoản thu đã có yêu cầu hoàn logic hay chưa và ở trạng thái nào.
2. Nếu chưa tồn tại, hệ thống tạo mới một yêu cầu hoàn cho khoản thu đó.
3. Hệ thống gửi lệnh hoàn **toàn bộ** khoản thực thu cần hoàn tới cổng thanh toán. Không hỗ trợ hoàn một phần.
4. Cổng thanh toán trả kết quả.
5. Hệ thống ghi trạng thái hoàn trên khoản thu và yêu cầu hoàn, liên kết kết quả với đơn, rồi cập nhật dữ liệu đối soát.

**Luồng thay thế**

- **Yêu cầu hoàn đã hoàn thành.** Không tạo lại và không hoàn lần thứ hai.
- **Yêu cầu đang xử lý hoặc đã thất bại nhưng còn thử lại được.** Tiếp tục trên cùng yêu cầu logic thay vì tạo bản sao.

**Ngoại lệ**

- **Lần hoàn thất bại.** Giữ yêu cầu ở trạng thái chưa hoàn tất để thử lại hoặc điều tra; không đảo ngược các khoản đã hoàn thành.
- **Kết quả hoàn lặp.** Không ghi nhận hoàn tiền hai lần.

**Hậu điều kiện**

- Khoản thu có tối đa một kết quả hoàn thành công.
- Việc hoàn tiền **không** tự đổi trạng thái quyền vào cửa của vé; hậu quả lên vé đi theo nguyên nhân gốc.
- Tồn kho và giới hạn mua chỉ được trả theo quy tắc của nguyên nhân, không vì đã có hoàn tiền.

**Giới hạn của B6 tại ca này**

Cách theo dõi, thử lại và kết thúc nhiều yêu cầu hoàn khi hủy một sự kiện là `B5-OPEN-03`, thuộc B7, B9, B10 rồi B11.

### 6.10 `UC-19` — Đánh dấu sự kiện đã chi trả

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Quản trị viên |
| Kích hoạt | Tiền đã được chuyển cho organizer ngoài hệ thống và admin mở thao tác đánh dấu đã chi trả |
| Mức ưu tiên | Thấp |
| Tiền điều kiện | Sự kiện đã kết thúc; không còn lần thanh toán đang chờ; không còn yêu cầu hoàn đang xử lý; đối soát đã được xác nhận ở `UC-18` |
| Bất biến chạm tới | `INV-10` — một sự kiện có tối đa một lần được đánh dấu chi trả |
| Truy vết | `C10`; `BIZ-037`, `BIZ-038`, `BIZ-050`–`BIZ-054` |
| Test case tương ứng | Đánh dấu lặp không tạo lần chi trả thứ hai; từ chối khi còn tiền treo; dấu vết người thao tác được lưu — hiện thực hóa tại B15 |

**Luồng chính**

1. Hệ thống kiểm lại rằng sự kiện vẫn đủ điều kiện chi trả và đối soát đã được xác nhận ở `UC-18`.
2. Admin đánh dấu sự kiện đã chi trả.
3. Hệ thống ghi dấu vết người thao tác và thời điểm.

Việc đối chiếu số liệu với báo cáo cổng thanh toán thuộc `UC-18`, và việc chuyển tiền diễn ra ngoài hệ thống giữa hai use case. `UC-19` chỉ ghi nhận kết quả sau cùng.

**Ngoại lệ**

- **Thao tác lặp.** Không có lần chi trả thứ hai.
- **Sự kiện chưa đủ điều kiện.** Từ chối thao tác.
- **Phát hiện sai lệch sau khi đã đánh dấu.** Xử lý ngoài hệ thống; không mở lại vòng đời chi trả.

**Hậu điều kiện**

- Vòng đời chi trả của sự kiện kết thúc. Không có khoản giữ lại.

**Giới hạn của B6 tại ca này**

Sổ cái là kết quả tổng hợp chỉ đọc; admin không sửa trực tiếp giá trị tiền. Trường dữ liệu, payload và thời gian lưu giữ dấu vết thao tác chờ B8, B13 và B16 theo `B5-OPEN-07`.

### 6.11 `UC-20` — Check-in vé bằng QR

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Nhà tổ chức |
| Kích hoạt | Organizer quét QR của người xuất trình vé tại cổng |
| Mức ưu tiên | Cao |
| Tiền điều kiện | Organizer sở hữu sự kiện được chọn; thiết bị có kết nối tới hệ thống; `eventStartAt <= now <= eventEndAt` |
| Bất biến chạm tới | `INV-09`, `HOT-04` — một vé có tối đa một check-in thành công, kể cả khi nhiều thiết bị quét đồng thời |
| Truy vết | `D01`, `D02`, `D03`; `BIZ-067`, `BIZ-068`, `BIZ-077`, `BIZ-082`, `BIZ-087` |
| Test case tương ứng | Hai thiết bị quét cùng vé gần đồng thời; năm lý do từ chối phân biệt được; mất kết nối cho thử lại; ngoài cửa sổ bị chặn — hiện thực hóa tại B15 |

**Luồng chính**

1. Organizer đăng nhập trên mobile và chọn một sự kiện thuộc chính mình.
2. Organizer quét QR.
3. Hệ thống xác định vé và kiểm quyền sở hữu sự kiện, cửa sổ check-in, việc vé thuộc đúng sự kiện, và trạng thái vé.
4. Hệ thống ghi nhận **nguyên tử** việc chuyển vé từ chưa sử dụng sang đã sử dụng.
5. Mobile hiển thị kết quả cùng loại vé, sector và ghế nếu có.

**Luồng thay thế**

- **Hai thiết bị quét cùng một vé gần đồng thời.** Tối đa một yêu cầu được check-in thành công; yêu cầu còn lại nhận kết quả vé đã sử dụng.
- **Lặp lại cùng một yêu cầu.** Không tạo thêm lượt check-in.
- **Mất kết nối trước khi yêu cầu tới hệ thống.** Ứng dụng báo chưa thể xác nhận và cho phép thử lại khi có kết nối. Không có chế độ offline; đây là trạng thái cục bộ của thiết bị và không phải một lượt từ chối của hệ thống.

**Ngoại lệ** — mỗi trường hợp phải trả lý do phân biệt được

- Organizer không sở hữu sự kiện được chọn.
- `now < eventStartAt` hoặc `now > eventEndAt`.
- QR không xác định được vé.
- Vé thuộc sự kiện khác.
- Vé chưa được phát hành, đã bị hủy hoặc vô hiệu, hoặc đã sử dụng.

**Hậu điều kiện**

- Một vé hợp lệ có tối đa một check-in thành công.
- Mọi yêu cầu thành công hoặc bị từ chối đều có dấu vết đủ để điều tra.

**Giới hạn của B6 tại ca này**

Không hỗ trợ hoàn tác check-in, check-out hoặc tái vào cửa. Không hiển thị danh tính người tham dự vì sản phẩm không quản lý dữ liệu đó theo `BIZ-073`. Vị trí và cơ chế bảo đảm tính nguyên tử thuộc B7, B10 rồi B11; B6 chỉ nêu yêu cầu nghiệp vụ.


### 6.12 `UC-23` — Duyệt hoặc từ chối hồ sơ organizer

| Mục | Nội dung |
|---|---|
| Tác nhân chính | Quản trị viên |
| Tác nhân phụ | Nguồn danh tính (Keycloak) |
| Kích hoạt | Có hồ sơ organizer ở trạng thái `PENDING` chờ xử lý |
| Mức ưu tiên | Thấp |
| Tiền điều kiện | Hồ sơ ở `PENDING`; tài khoản nộp hồ sơ đang có vai trò `BUYER` |
| Bất biến chạm tới | Chưa có mã `INV` riêng. `BIZ-140` đặt một điều kiện hoàn tất phụ thuộc xác nhận từ nguồn danh tính; B7 xét xem nó có cần thành bất biến hay không |
| Truy vết | `E03`, `E04`; `BIZ-134`, `BIZ-136`–`BIZ-141`, `BIZ-151` |
| Test case tương ứng | Nguồn danh tính chưa xác nhận thì không báo đã duyệt; từ chối bắt buộc có lý do; thao tác lặp không cấp vai trò hai lần — hiện thực hóa tại B15 |

**Luồng chính — duyệt**

1. Admin xem tên tổ chức và mô tả ngắn của hồ sơ organizer đang `PENDING`.
2. Admin chấp thuận hồ sơ.
3. Hệ thống yêu cầu nguồn danh tính cấp vai trò `ORGANIZER` cho tài khoản.
4. **Chỉ khi nguồn danh tính xác nhận đã cấp vai trò**, hệ thống mới chuyển hồ sơ sang `ACTIVE` và coi việc duyệt là hoàn tất.
5. Tài khoản giữ nguyên vai trò `BUYER` và nhận thêm `ORGANIZER`.
6. Từ thời điểm này hồ sơ mới được hiển thị công khai.

**Luồng thay thế — từ chối**

1. Admin từ chối hồ sơ và **bắt buộc** nhập lý do.
2. Hệ thống lưu lý do và chuyển hồ sơ sang `REJECTED`.
3. Tài khoản không nhận thêm vai trò nào.
4. `REJECTED` là kết quả cuối trong phạm vi đồ án: không hỗ trợ sửa và gửi lại hồ sơ.

**Ngoại lệ**

- **Nguồn danh tính chưa xác nhận cấp được vai trò.** Không được trả kết quả đã duyệt hoàn tất và không được chuyển hồ sơ sang `ACTIVE`. Đây là nhánh lỗi chính của ca này: nếu bỏ qua, hệ thống sẽ có hồ sơ hiển thị là đã duyệt trong khi tài khoản chưa thực sự có quyền organizer.
- **Từ chối mà không nhập lý do.** Từ chối thao tác.
- **Hồ sơ không ở `PENDING`.** Từ chối thao tác duyệt hoặc từ chối.
- **Thao tác lặp trên cùng hồ sơ.** Không cấp vai trò lần thứ hai và không tạo thêm bản ghi lý do.

**Hậu điều kiện**

- Hồ sơ ở `ACTIVE` và tài khoản có cả hai vai trò, hoặc hồ sơ ở `REJECTED` kèm lý do đã lưu.
- Chỉ hồ sơ `ACTIVE` xuất hiện ở `UC-27`.

**Giới hạn của B6 tại ca này**

Không mô tả cơ chế đồng bộ với nguồn danh tính, thứ tự ghi, cách thử lại hay cách bù trừ khi một bên thất bại — đó là B7, B10 rồi B11. B6 chỉ chốt tên tổ chức, mô tả ngắn và lý do từ chối ở mức nghiệp vụ; kiểu dữ liệu/schema/hợp đồng chờ B12/B13. Không quyết định thu hồi vai trò vì `BIZ-139` đã loại khỏi phạm vi.

## 7. Phép tự kiểm và điều kiện chuyển trạng thái

- [x] **Mọi** use case đặc tả hoặc liệt kê đều truy được về sự kiện miền B4-v0.13; không còn ngoại lệ nào. Đếm lại trên bản `v0.12`: 9 + 5 + 5 + 1 + 3 + 7 = 30 ca.
- [x] Mười hai đặc tả ở §6 dùng đúng mẫu C3 của Tầng C, gồm cả `Mức ưu tiên` và `Test case tương ứng`.
- [x] Bốn quy trình của B3-v0.9 đều có use case tương ứng, gồm cả nhánh lỗi đáng chú ý.
- [x] Các năng lực hỗ trợ mà B4 §8.2, §8.4 và §8.5 chuyển tiếp — chatbot, địa điểm, phân loại, tìm kiếm, theo dõi, thống kê đọc — đều có chỗ trong B6, dưới dạng ghi chú kênh hoặc ca liệt kê.
- [x] Ba tác nhân nghiệp vụ đều có mục từ trong B2-v0.9. Các tác nhân còn lại là hệ thống ngoài hoặc nguồn kích hoạt không phải người và truy về B3 hoặc B4; §2 liệt kê đủ cả bảy.
- [x] Không khái niệm nghiệp vụ nào được B6 tự đặt tên mới hoặc tự khóa nghĩa.
- [x] Check-in mobile không tạo bộ use case riêng.
- [x] Không use case nào được tuyên bố là service, schema, API, aggregate hoặc đơn vị triển khai.
- [x] Không đặt tên trạng thái đơn; nghĩa nghiệp vụ đã được khóa bởi `BIZ-150`, còn tên/chuyển trạng thái chờ B12/B13 theo `BIZ-130`.
- [x] Không tạo ràng buộc giữa sức chứa vật lý và nguồn cung, đúng `BIZ-149`.
- [x] Hồ sơ organizer dùng đúng tập trường tối thiểu ở `BIZ-151`; không tiền-chốt kiểu dữ liệu/schema/hợp đồng.
- [x] Lê Văn Minh chốt danh sách mười một ca được đặc tả đầy đủ ở §5 ngày 2026-08-21.
- [x] Lê Văn Minh đã duyệt `E01`–`E05` trong B4-v0.11 và chuỗi `B4-v0.11 → B5-v0.9`; B4-v0.13/B5-v0.11 chỉ lan truyền thêm các quyết định mới đã xác nhận.
- [x] `B6-OPEN-05` đã đóng: B2-v0.9 có đủ mục từ cho hồ sơ organizer; báo động ở `B6-v0.1` là sai.
- [x] Mười hai ca ở §6 đều có tiền điều kiện, luồng chính, ngoại lệ phân biệt được và hậu điều kiện.
- [x] Bốn biểu đồ use case dùng đúng ký pháp UML theo Tầng C §3.2.1: một ranh giới hệ thống mỗi hình, tác nhân ngoài, liên kết không mũi tên, không màu mang nghĩa, không mã truy vết trong hình. Vòng kiểm cuối dựng lại thành công bằng PlantUML 1.2025.4 ngày 2026-08-22.
- [x] Mỗi đặc tả ở §6 có cột bất biến; `UC-23` ghi rõ chưa có mã `INV` riêng vì `BIZ-140` chưa được nâng thành bất biến. B6 không tự phát biểu bất biến mới.
- [x] Quyết định phê duyệt dòng E được ghi tại `GOV-021` trong sổ quyết định.
- [x] Lê Văn Minh xác nhận giữ `UC-23` ở mức đặc tả đầy đủ (`GOV-022`).
- [x] Codex rà chéo vòng một, trả bảy phát hiện; sửa xong ở `B6-v0.4`.
- [x] Codex rà chéo vòng hai; sửa xong ở `B6-v0.5`.
- [x] Codex rà chéo vòng ba; sửa xong ở `B6-v0.6`.
- [x] Codex đã rà lại nội dung B6-v0.10, bốn nguồn biểu đồ và phần lan truyền các quyết định ngày 2026-08-22; kết quả dựng hình được kiểm lại trong vòng hợp nhất.
- [x] Số đếm ca liệt kê ở §5 khớp danh mục hiện tại: 30 use case − 12 ca đặc tả đầy đủ = 18 ca chỉ liệt kê. Con số “mười bảy” của `B6-v0.10` là số cũ từ lúc danh mục còn 29 ca, trước khi `UC-30` được thêm.
- [x] Mọi tham chiếu phiên bản B4 trong thân bài đều là `B4-v0.13`; các câu nhắc `B4-v0.11` còn lại chỉ nằm trong phần kể lịch sử.
- [x] `UC-07` và `UC-08` nay dùng đúng mục từ **Yêu cầu hủy sự kiện** của `B2-v0.10`; B6 không phải thêm hay sửa use case nào vì hành vi đã được mô tả từ trước.
- [x] Lê Văn Minh đã duyệt toàn bộ `B6-v0.12` thành `APPROVED` ngày 2026-08-22, sau chuỗi `B2 → B5`; AI không tự đánh dấu thay.

## 8. Sơ đồ nguồn

| Tệp | Nội dung |
|---|---|
| [`B6-01-use-case-event-lifecycle.puml`](../diagrams/src/B6-01-use-case-event-lifecycle.puml) | Quản lý vòng đời sự kiện — 9 use case, 2 tác nhân; tỷ lệ rộng/cao **0,64** |
| [`B6-02-use-case-purchase-and-entry.puml`](../diagrams/src/B6-02-use-case-purchase-and-entry.puml) | Mua vé và kiểm soát vào cửa — 6 use case, 3 tác nhân; tỷ lệ **1,46** |
| [`B6-03-use-case-refund-and-payout.puml`](../diagrams/src/B6-03-use-case-refund-and-payout.puml) | Hoàn tiền, đối soát và chi trả — 5 use case, 3 tác nhân; tỷ lệ **1,05** |
| [`B6-04-use-case-account-and-queries.puml`](../diagrams/src/B6-04-use-case-account-and-queries.puml) | Tài khoản và tra cứu — 10 use case, 5 tác nhân; tỷ lệ **0,71** |

Đây là tệp nháp theo quy ước tại `docs/diagrams/README.md`: PlantUML dùng để nháp, chỉ dựng lại trên Visual Paradigm khi sơ đồ đã đủ ổn định để đưa vào báo cáo.

### 8.1 Quy ước áp dụng

Bốn hình tuân theo Tầng C §3.2.1 và `GOV-020`:

- **Một hình chữ nhật duy nhất là ranh giới hệ thống**, mang tên hệ thống. Không dùng hình chữ nhật để nhóm theo tác nhân hay theo quy trình, vì đó là gán nghĩa khác cho một ký hiệu chuẩn.
- Tác nhân đứng **ngoài** ranh giới, use case nằm **trong**.
- Liên kết tác nhân–use case là **đường thẳng không mũi tên**; hướng ở đây không mang nghĩa. Chỉ quan hệ `<<extend>>` mới dùng mũi tên đứt nét.
- **Không dùng màu nền để phân nhóm.** Hình đen trắng nên phân biệt được khi in thang xám.
- **Không đặt mã truy vết trong hình.** Nhãn là tên nghiệp vụ tiếng Việt; phần giải thích nằm ở văn bản dưới đây.

### 8.2 Những điều hình không nói được, ghi bằng lời

`UC-15` không có tác nhân chính. Nó luôn được gọi bởi một ca khác — xử lý hủy sự kiện, thanh toán đến muộn hoặc thu trùng, hoặc phát hành vé lỗi — nên thừa hưởng tác nhân của ca gọi nó. Cổng thanh toán chỉ thực hiện lệnh hoàn và trả kết quả, nên nó xuất hiện như tác nhân phụ.

Việc chuyển tiền cho nhà tổ chức diễn ra ngoài hệ thống, giữa `UC-18` và `UC-19`.

Đơn hết hạn theo mốc thời gian là **nhánh thay thế** của `UC-13`, không phải use case riêng và không có tác nhân. Cửa sổ check-in là **tiền điều kiện** của `UC-20`, cũng không phải tác nhân.

`UC-26` nằm trong nhóm tra cứu nhưng làm đổi trạng thái, nên nó có mã sự kiện riêng là `E05`.

Chatbot là một **kênh** để người mua dùng `UC-24`, `UC-25` và khởi tạo ý định mua; nó không phải tác nhân và không có bộ use case riêng.

### 8.3 Lịch sử chỉnh bố cục

Các vòng đầu vẽ toàn bộ danh mục trong hai hình. Tỷ lệ rộng/cao đo được lần lượt là 4,42 và 3,44, sau khi thu gọn nhãn còn 2,38 và 3,43 — đều quá ngang, chữ nhỏ khi đặt lên A4 và nhiều đường liên kết cắt qua nhau.

Nguyên nhân chỉ lộ ra khi dựng hình ra xem thay vì đọc mã nguồn: hai mươi use case với bốn tác nhân trong một hình thì bố cục nào cũng vỡ. Vòng bốn tách theo bốn nhóm nghiệp vụ và sửa lại toàn bộ ký pháp theo quy ước ở trên. Tỷ lệ kiểm lại hiện tại là **0,64 · 1,46 · 1,05 · 0,71**, nằm trong khoảng đọc được trên A4 dọc hoặc ngang.

Vòng trước dựng bằng PlantUML 1.2026.6; vòng hợp nhất ngày 2026-08-22 dựng lại thành công bằng PlantUML 1.2025.4. Bản cuối vẫn phải dựng lại trên Visual Paradigm theo `GOV-020`.

Bốn hình này biểu diễn chức năng theo tác nhân. Chúng **không** được dùng để chia service, và việc tách làm bốn hình là quyết định trình bày chứ không phải một đề xuất ranh giới — Tầng B nêu đây là lỗi phổ biến nhất, ví dụ gom mọi use case của admin thành một “Admin Service”.

## 9. Phần dùng cho báo cáo

Sau khi được duyệt, báo cáo có thể dùng biểu đồ use case theo tác nhân, bảng đặc tả các ca cốt lõi, và lập luận rằng mỗi use case đều bắt nguồn từ một hành vi nghiệp vụ đã được mô hình hóa chứ không từ một màn hình có sẵn.

Không đưa nguyên trạng mã `UC-*`, sổ `OPEN` hoặc trạng thái governance vào báo cáo nếu chúng không giúp giải thích một quyết định.

## 10. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B6-v0.12` | 2026-08-22 | Không đổi nội dung use case. Chỉ đồng bộ khai đầu vào sang `B2-v0.10`/`B3-v0.10`/`B4-v0.14` sau khi B2 bổ sung mục từ **Yêu cầu hủy sự kiện** — khái niệm mà `UC-07`/`UC-08` vốn đã mô tả | Lan truyền trạng thái |
| `B6-v0.11` | 2026-08-22 | Sửa hai điểm do vòng kiểm toán đọc toàn văn phát hiện: số ca chỉ liệt kê ở §5 từ “mười bảy” thành “mười tám” sau khi `UC-30` được thêm, và ba tham chiếu `B4-v0.11` còn sót trong thân bài §3, §3.5, §6 thành `B4-v0.13` | Sửa lỗi biên tập |
| `B6-v0.10` | 2026-08-22 | Đồng bộ B4-v0.13; đóng phần nghĩa của trạng thái đơn, sức chứa địa điểm và trường hồ sơ organizer; giữ UC-23 ở mức đầy đủ; cắt các biến thể lỗi hủy sự kiện quá sâu; chuyển sang `REVIEW_READY` sau rà lại | Lan truyền quyết định và chuẩn bị duyệt |
| `B6-v0.9` | 2026-08-21 | Sửa lỗi biên tập ở §8 do vòng trước để lại: một câu cũ dính vào tiêu đề khiến `### Quy ước áp dụng` không còn là tiêu đề, và một đoạn còn nói hình “quá cao so với chiều rộng” trong khi số đo cho thấy ngược lại. Đánh số §8.1–8.3. Sửa lịch sử rà chéo ở §7 cho đúng ba vòng đã diễn ra | Sửa lỗi biên tập |
| `B6-v0.8` | 2026-08-21 | Vẽ lại bốn biểu đồ use case theo đúng Tầng C §3.2.1 và `GOV-020`: khôi phục ranh giới hệ thống làm hình chữ nhật duy nhất thay vì dùng nó để nhóm; bỏ toàn bộ màu nền phân nhóm để in thang xám vẫn đọc được; bỏ mã truy vết và chú giải khỏi hình, chuyển phần giải thích sang văn bản. Tỷ lệ 0,64 · 1,45 · 1,05 · 0,72 | Sửa trình bày theo quy ước |
| `B6-v0.7` | 2026-08-21 | Vẽ lại toàn bộ biểu đồ use case: tách một hình thành bốn theo bốn quy trình của B3, chuyển sang bố cục trái–phải, dùng đường liên kết thường thay mũi tên có hướng. Dựng và đo bằng PlantUML 1.2026.6; tỷ lệ rộng/cao 0,96 · 1,16 · 1,27 · 0,82, đều đọc được trên A4 | Sửa trình bày |
| `B6-v0.6` | 2026-08-21 | Sửa vòng rà chéo thứ ba: tách tiền điều kiện của `UC-14` để phiên đăng nhập không còn là điều kiện của bước phát hành; sửa cột tác nhân `UC-12`; bổ sung ghi chú kênh chatbot và ca liệt kê `UC-30` cho thống kê đọc; ghi rõ địa điểm và phân loại thuộc `UC-01`; siết câu chữ ô tích về `E05`; ghi kết quả đo sơ đồ mới và dẫn `GOV-020` | Sửa sau rà chéo vòng 3 |
| `B6-v0.5` | 2026-08-21 | Sửa vòng rà chéo thứ hai: gỡ ô tích khẳng định người duyệt đã xác nhận `UC-23` khi chưa có xác nhận; sửa kích hoạt và luồng `UC-19` để không lặp `UC-18`; phân biệt tác nhân nghiệp vụ với tác nhân hệ thống ngoài và bổ sung hai tác nhân còn thiếu; sửa quan hệ trên sơ đồ và tham chiếu `OPEN` đã đóng | Sửa sau rà chéo vòng 2 |
| `B6-v0.4` | 2026-08-21 | Sửa theo bảy phát hiện của vòng rà chéo: đưa 12 đặc tả về đúng mẫu C3 với `Mức ưu tiên` và `Test case tương ứng`; sửa truy vết sáu ca đọc và gắn `UC-26` vào `E05`; `UC-15` và bước phát hành của `UC-14` không còn lấy hệ thống làm tác nhân chính; sửa kích hoạt `UC-19`; sửa số đếm và các tham chiếu phiên bản cũ; đóng `B6-OPEN-05` | Sửa sau rà chéo |
| `B6-v0.3` | 2026-08-21 | Ba ca năng lực tài khoản truy vết về dòng sự kiện hỗ trợ `E01`–`E04` của B4-v0.10, bỏ ngoại lệ truy vết; đặc tả đầy đủ `UC-23`; đóng `B6-OPEN-05` vì báo động sai | Lan truyền từ B4 |
| `B6-v0.2` | 2026-08-21 | Chốt phạm vi mười một ca và viết đặc tả đầy đủ tại §6; chuyển ba ca năng lực tài khoản sang liệt kê với ngoại lệ truy vết được ghi tại §4.1 theo lựa chọn của Lê Văn Minh | Bổ sung nội dung chính |
| `B6-v0.1` | 2026-08-21 | Bản đầu: tác nhân, danh mục 29 use case kèm truy vết về B4-v0.9, biểu đồ use case tổng quan, đề xuất 11 ca đặc tả đầy đủ, và năm mục `OPEN` — trong đó `B6-OPEN-01` ghi nhận B4 chưa có sự kiện miền cho vòng đời tài khoản, `B6-OPEN-05` ghi nhận B2 chưa có mục từ cho hồ sơ organizer | Tạo mới |
