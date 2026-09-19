# B8 — Bảng yêu cầu chức năng và phi chức năng

> **Cập nhật ngữ cảnh 18/09:** đề tài/nhiệm vụ theo [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md), nhóm bốn người; mobile là phần phụ theo PRJ-035. Nội dung nghiệp vụ/bất biến và baseline duyệt bên dưới không được đổi trong đợt này. Các phiên bản A1–A6 và DH-* được dẫn trong hồ sơ duyệt là đầu vào tại thời điểm duyệt; bản diễn giải A1–A6 hiện hành đang REVIEW_READY. Không coi bản mới tự được phê duyệt, không dùng lời khai cũ để hạ nhiệm vụ xây dựng/đánh giá DT18.

- Phiên bản: `B8-v0.13`
- Trạng thái: `APPROVED`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-09-14 (GOV-144); các mốc trước ở nhật ký phiên bản.
- Đầu vào và phiên bản: `docs/domain/B6-use-cases.md` — `B6-v0.15`, `APPROVED` 2026-09-14 (GOV-144); `docs/domain/B7-aggregates-and-invariants.md` — `B7-v0.13`, `APPROVED` cùng ngày; giữ 30 use case, đã đồng bộ quyết định tại phiên bản mới; `docs/evidence/external-survey/B1-public-ticketing-survey-2026-08-09.md` — `APPROVED` ngày 2026-08-13; `docs/evidence/incident-diagnosis/B1-current-diagnosis-baseline.md` — `APPROVED` ngày 2026-08-13; `docs/research/A2-problem-statement.md` — `A2-v0.2` và `docs/research/A4-research-questions-draft.md` — `A4-v0.2`, cả hai `APPROVED` 2026-08-27 sau tái baseline theo `DH-TEN`; `docs/research/A3-research-objectives.md` — `A3-v0.5`, `DRAFT`; **B8 chỉ truy về `MT-1`–`MT-4`** ở cột `Nguồn gốc` của `NFR-01`, `NFR-03`, `NFR-04`, `NFR-05` — đã kiểm: không yêu cầu nào dẫn `MT-5`, nên việc `RES-041` sửa câu chữ `MT-5` không đụng một yêu cầu nào; nội dung `MT-1`–`MT-4` được B8 dẫn **không đổi** so với `A3-v0.3` *(Rà 2026-08-29 — `GOV-052`: `A4` nay là `A4-v0.3`; `v0.3` chỉ sửa một câu khai sai về nơi chốt ngưỡng, **ba câu hỏi nghiên cứu không đổi một chữ**, nên đầu vào thực chất của tài liệu này không đổi.)* *(Rà 2026-08-29 — `GOV-052`: `A3` nay là `A3-v0.6`; `v0.6` chuyển `MT-5` sang Vòng 1 (`RES-045`) và đổi **vai** của `MT-1`–`MT-3` (`RES-046`) mà **không đổi một chữ câu chữ nào của `MT-1`–`MT-4`**, nên phần được tài liệu này dẫn không đổi.)*
- Nguồn truy vết hỗ trợ: `docs/glossary.md` — `B2-v0.12`, `APPROVED` 2026-08-27; `docs/domain/B3-business-processes.md` — `B3-v0.11`, `APPROVED` 2026-08-27; `docs/domain/B4-domain-event-map.md` — `B4-v0.15`, `APPROVED` 2026-08-27; `docs/domain/B5-bounded-context-map.md` — `B5-v0.14`, `APPROVED` 2026-08-27, nguồn của `B5-OPEN-07`; `BC-CAND-08` đã bị gỡ theo `RES-034` nên còn **bảy** context, **bảy context còn lại và bản đồ quan hệ không đổi**; và từng quyết định được dẫn trong `docs/project/decision-register.md`
- Phân lớp: `FORMATION`

> **Đường dẫn canonical:** `docs/domain/B8-requirements.md`, được khai trong bảng đường dẫn của Tầng B.

> **Cổng phê duyệt:** cả chuỗi `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12` và `B6-v0.12` đã được duyệt ngày 2026-08-22; B8-v0.8 được duyệt sau chúng, đúng thứ tự cổng.

> **Lịch sử lúc trình ngày 2026-09-14, trước GOV-144:** v0.13 nhận B6-v0.15 và B7-v0.13 (`REVIEW_READY`) cùng BIZ-153–158, GOV-127–142, PRJ-024. Khai đầu vào/duyệt ở phía trên ghi nền lịch sử v0.12; bản mới chưa APPROVED. Chỉ lan truyền quyết định đã xác nhận, không đổi NFR hoặc sinh kiến trúc từ yêu cầu quan sát. Mã FR được giữ ổn định.


> Phê duyệt hiện hành: Lê Văn Minh, ngày 2026-09-14, GOV-144 — “duyệt toàn bộ sau đó push lên giúp tôi”. Các dòng kể lần soạn/duyệt trước giữ vai trò lịch sử; phiên bản này đã APPROVED. Phê duyệt tài liệu không đổi kết quả NOT RUN thành PASS.

## 1. Mục đích và giới hạn

B8 chuyển các use case của B6 thành **yêu cầu chức năng** có mã, và chuyển vấn đề cùng mục tiêu của Tầng A thành **yêu cầu phi chức năng** có cách đo. Mã ở đây dùng để nối yêu cầu với thiết kế và với kiểm thử bằng tìm kiếm văn bản.

B8 **không** quyết định:

- service, schema, API, hợp đồng hay đơn vị triển khai;
- cơ chế kỹ thuật để đạt một yêu cầu phi chức năng;
- **ngưỡng số** của bất kỳ yêu cầu phi chức năng nào — theo Tầng B, con số chỉ được gắn ở B9 sau khi dựng kịch bản chất lượng.

Mỗi yêu cầu phi chức năng ở §3 vì vậy có cột **cách đo** chứ không có cột giá trị.

Theo Tầng B, không cần duy trì ma trận truy vết đầy đủ cho mọi thao tác CRUD. Các ca chỉ liệt kê ở B6 nhận đúng một yêu cầu chức năng gộp; các ca được đặc tả đầy đủ được tách thêm yêu cầu cho từng quy tắc mang bất biến, vì đó là những chỗ cần một ca kiểm thử riêng.

## 2. Yêu cầu chức năng

### 2.1 Vòng đời sự kiện

| Mã | Nội dung | Nguồn gốc |
|---|---|---|
| `FR-01` | Organizer tạo được bản nháp sự kiện với nội dung, thời gian, địa điểm, cửa sổ mở bán, giới hạn mua và `salesMode` | `UC-01`; `A01` |
| `FR-02` | Organizer cấu hình được loại vé, nguồn cung và khuyến mãi khi sự kiện còn `DRAFT`; lưu cấu hình trọn vẹn hoặc không đổi. Ghế ẩn được lưu bền, không bán; tải lại phục hồi đúng, ẩn/hiện không tự mất gán loại vé | `UC-02`; `A02`; GOV-126–130 |
| `FR-03` | Hệ thống bắt buộc mỗi sự kiện có đúng một `salesMode`, và từ chối cấu hình không tương thích với mode đang chọn | `UC-02`; `BIZ-001`–`BIZ-003` |
| `FR-04` | Khi organizer đổi `salesMode`, hệ thống yêu cầu xác nhận xóa phần cấu hình không tương thích và không tự chuyển đổi | `UC-02`; `BIZ-019` |
| `FR-05` | Hệ thống từ chối lưu cấu hình có giá loại vé không lớn hơn 0 hoặc thiếu giới hạn mua | `UC-02`; `BIZ-025` |
| `FR-06` | Khuyến mãi tự có hiệu lực theo `startAt`/`endAt`; hệ thống không cung cấp thao tác bật, tạm dừng hay vô hiệu hóa thủ công | `UC-02`; `BIZ-092`–`BIZ-095` |
| `FR-07` | Organizer gửi được sự kiện để duyệt; khi đó cấu hình thương mại và `salesMode` bị khóa | `UC-03`; `A03` |
| `FR-08` | Admin duyệt được sự kiện và nhập tỷ lệ phí nền tảng; tỷ lệ được cố định cho sự kiện đó | `UC-04`; `A04`; `BIZ-142` |
| `FR-09` | Admin trả được sự kiện về `DRAFT`; hệ thống không tạo trạng thái sự kiện bền vững `REJECTED` | `UC-05`; `A05`; `BIZ-145` |
| `FR-10` | Organizer công bố được sự kiện đã duyệt; yêu cầu công bố lặp không tạo thêm tác dụng phụ | `UC-06`; `A06`; `BIZ-088` |
| `FR-11` | Organizer gửi được yêu cầu hủy khi sự kiện đã có đơn thu tiền, và hủy trực tiếp khi chưa có đơn thu tiền. Giữ lịch sử yêu cầu; tối đa một yêu cầu chờ xét/sự kiện; được gửi lại sau từ chối, không gửi mới cho sự kiện đã hủy | `UC-07`, `UC-09`; `A09`, `A11`; BIZ-153–156 |
| `FR-12` | Admin xác nhận hoặc từ chối được yêu cầu hủy, và chủ động hủy được sự kiện | `UC-08`; `A10`, `A11` |
| `FR-13` | Khi từ chối yêu cầu hủy, hệ thống giữ nguyên trạng thái sự kiện và không khởi động bất kỳ xử lý hoàn nào | `UC-08`; `BIZ-097`–`BIZ-099` |
| `FR-14` | Hệ thống chỉ chấp nhận hủy sự kiện khi `now < eventStartAt` | `UC-08`; `BIZ-083` |
| `FR-15` | Khi hủy được xác nhận, hệ thống đóng bán, vô hiệu hóa toàn bộ vé và không đưa nguồn cung trở lại khả dụng; kích hoạt lặp không tạo thêm hậu quả. Chấp nhận độ trễ lan truyền; giao dịch lọt trong khoảng trễ phải hội tụ theo hủy/hoàn, không cam kết dừng tức thời toàn hệ thống | `UC-08`; `B13`, `B14`; `BIZ-147`; GOV-134/135 |

### 2.2 Mua vé

| Mã | Nội dung | Nguồn gốc |
|---|---|---|
| `FR-16` | Buyer chọn được vé và tạo đơn; hệ thống tạo đúng một giữ chỗ và một thời hạn chung cho toàn đơn. Email nhận vé điền sẵn từ tài khoản, cho sửa trước tạo đơn và lưu theo lần mua, không đổi chủ sở hữu | `UC-10`; `B01`; `INV-03`; GOV-141/142 |
| `FR-17` | Với `SEAT_MAP`, hệ thống từ chối đơn chứa lựa chọn thuộc nhiều sector | `UC-10`; `INV-02`; `BIZ-080` |
| `FR-18` | Hệ thống từ chối tạo giữ chỗ khi ghế hoặc số lượng không còn khả dụng | `UC-10`; `INV-01` |
| `FR-19` | Hệ thống từ chối tạo giữ chỗ khi tổng vé đang giữ cộng vé đã mua của tài khoản vượt giới hạn mua của sự kiện | `UC-10`; `INV-04`; `BIZ-075` |
| `FR-20` | Hệ thống không cho đổi lựa chọn trong đơn đã tạo | `UC-10`; `BIZ-030` |
| `FR-21` | Buyer áp dụng được tối đa một mã khuyến mãi cho một đơn, và hệ thống giữ một lượt dùng theo đúng thời hạn đơn | `UC-11`; `B02` |
| `FR-22` | Hệ thống phân biệt và trả riêng từng lý do từ chối mã: không tồn tại, ngoài hiệu lực, hết tổng lượt, tài khoản đã dùng mã, đơn đã có mã, số tiền cuối không lớn hơn 0 | `UC-11`; `BIZ-039`–`BIZ-048` |
| `FR-23` | Buyer khởi tạo được lần thanh toán; không cho tạo lần thử song song khi lần hiện tại chưa kết thúc. Tiền đóng băng từ lần bắt đầu thanh toán đầu tiên, retry cùng đơn giữ nguyên giá/giảm giá/số tiền | `UC-12`; `B03`; `BIZ-031`; GOV-132/133 |
| `FR-24` | Hệ thống xác minh kết quả thanh toán thuộc đúng đơn, đúng lần thử và đúng số tiền, rồi chỉ giữ một kết quả thu hợp lệ cho mỗi đơn | `UC-12`; `INV-05`; `BIZ-032` |
| `FR-25` | Kết quả thanh toán lặp không tạo tác dụng phụ lặp và không phát hành vé lần thứ hai | `UC-12`; `INV-07`; `HOT-03` |
| `FR-26` | Hệ thống xét hạn giữ chỗ tại thời điểm kiểm/chấp nhận thanh toán; đã hết hạn hoặc hủy thì không hồi sinh đơn. Khoản thu hợp lệ đến muộn được hoàn toàn bộ mà không trả tài nguyên lần thứ hai; không dựa riêng vào trạng thái do worker cập nhật | `UC-12`; `B10`; `BIZ-106`; GOV-138 |
| `FR-27` | Hệ thống nhận diện thanh toán trùng, giữ một giao dịch hợp lệ và hoàn từng giao dịch thừa độc lập; việc hoàn không tạo trạng thái nghiệp vụ riêng cho đơn hợp lệ | `UC-12`; `B11`; `BIZ-013`, `BIZ-032`, `BIZ-102`, `BIZ-105`, `BIZ-150` |
| `FR-28` | Buyer hủy được đơn của chính mình khi đơn còn giữ chỗ và chưa có thanh toán thành công; thao tác lặp là idempotent | `UC-13`; `B08`; `BIZ-071` |
| `FR-29` | Khi đơn hết hạn hoặc bị hủy, hệ thống trả giữ chỗ, nguồn cung, phần giới hạn mua và lượt khuyến mãi **đúng một lần** | `UC-13`; `INV-06`; `BIZ-090`, `BIZ-091` |
| `FR-30` | Sau khi thu tiền hợp lệ, hệ thống phát hành các vé riêng lẻ tương ứng với dòng vé của đơn | `UC-14`; `B06` |
| `FR-31` | Buyer đã đăng nhập xem và tải lại được QR của vé thuộc đơn của chính mình; organizer và admin không có chức năng liệt kê hoặc tải QR thô của buyer | `UC-14`; `BIZ-076`–`BIZ-079` |
| `FR-32` | Gửi thông tin vé thất bại không làm vé mất hiệu lực và không kích hoạt hoàn tiền; buyer gửi hoặc tải lại được | `UC-14`; `B07`; `BIZ-060`, `BIZ-061` |
| `FR-33` | Khi không phát hành được đầy đủ vé sau khi đã thu tiền, hệ thống vô hiệu vé dở dang, trả lượt khuyến mãi đúng một lần và tạo yêu cầu hoàn toàn bộ. Tồn kho và giới hạn mua **chỉ** được trả lại nếu sự kiện vẫn còn đủ điều kiện bán | `UC-14`; `B12`; `BIZ-063`, `BIZ-064`, `BIZ-070` |

### 2.3 Hoàn tiền, đối soát và chi trả

| Mã | Nội dung | Nguồn gốc |
|---|---|---|
| `FR-34` | Hệ thống duy trì tối đa một yêu cầu hoàn tiền logic cho mỗi khoản thu; kích hoạt lặp hội tụ về yêu cầu hiện có | `UC-15`; `INV-08`; `BIZ-101` |
| `FR-35` | Hệ thống chỉ hoàn toàn bộ khoản thực thu, không hỗ trợ hoàn một phần | `UC-15`; `BIZ-011` |
| `FR-36` | Kết quả hoàn tiền không tự thay đổi trạng thái quyền vào cửa của vé; hoàn khoản thu trùng/đến muộn không tạo trạng thái nghiệp vụ riêng cho đơn và hậu quả lên vé đi theo nguyên nhân gốc | `UC-15`; `BIZ-103`–`BIZ-105`, `BIZ-150` |
| `FR-37` | Khi một lần hoàn thất bại, hệ thống giữ yêu cầu ở trạng thái chưa hoàn tất để thử lại mà không đảo ngược các khoản đã hoàn thành | `UC-15`; `C04` |
| `FR-38` | Khi hủy sự kiện, hệ thống xác định khoản thu còn phải hoàn theo từng đơn; giao dịch thu thừa đi theo nhánh hoàn riêng và không nằm trong lô hoàn do hủy | `UC-08`, `UC-16`; `BIZ-100`–`BIZ-102` |
| `FR-39` | Admin theo dõi được tiến độ hoàn của một sự kiện bị hủy gồm số đơn chờ, thành công và thất bại | `UC-16`; `C06` |
| `FR-40` | Hệ thống tổng hợp sổ cái đối soát chỉ đọc; admin không sửa trực tiếp giá trị tiền | `UC-17`; `C07`; B3 §4.5 bước 1 |
| `FR-41` | Hệ thống tính doanh thu thực thu và phí theo tỷ lệ đã cố định khi duyệt; phí tính một lần trên doanh thu hợp lệ của toàn sự kiện sau giảm giá/hoàn, làm tròn HALF_UP về nguyên đồng, không cộng phí làm tròn từng đơn hoặc tính trên khoản thu thừa | `UC-17`; `INV-11`; `BIZ-142`; GOV-140 |
| `FR-42` | Hệ thống chỉ mở điều kiện chi trả khi sự kiện đã kết thúc, không còn lần thanh toán đang chờ và không còn yêu cầu hoàn đang xử lý | `UC-18`; `C08`; `BIZ-050`–`BIZ-053` |
| `FR-43` | Admin đánh dấu được sự kiện đã chi trả **đúng một lần**, kèm dấu vết người thao tác | `UC-19`; `INV-10`; `BIZ-037`, `BIZ-038` |

### 2.4 Kiểm soát vào cửa

| Mã | Nội dung | Nguồn gốc |
|---|---|---|
| `FR-44` | Organizer quét được QR để check-in cho sự kiện thuộc chính mình trên thiết bị di động | `UC-20`; `D01` |
| `FR-45` | Một vé có **tối đa một** check-in thành công, kể cả khi nhiều thiết bị quét gần đồng thời | `UC-20`; `INV-09`; `HOT-04`; `BIZ-087` |
| `FR-46` | Hệ thống phân biệt và trả riêng từng lý do từ chối check-in: không sở hữu sự kiện, ngoài cửa sổ, QR không xác định được vé, vé thuộc sự kiện khác, vé chưa phát hành hoặc đã hủy hoặc đã sử dụng | `UC-20`; `D03`; `BIZ-067`, `BIZ-068` |
| `FR-47` | Hệ thống không hỗ trợ hoàn tác check-in, check-out hoặc tái vào cửa | `UC-20`; `BIZ-082` |
| `FR-48` | Khi mất kết nối, ứng dụng báo chưa thể xác nhận và cho thử lại; không có chế độ check-in offline | `UC-20`; B3 §5.4 |

### 2.5 Năng lực tài khoản

| Mã | Nội dung | Nguồn gốc |
|---|---|---|
| `FR-49` | Người dùng đăng ký được tài khoản và nhận vai trò `BUYER` mặc định | `UC-21`; `E01`; `BIZ-132` |
| `FR-50` | Hệ thống không yêu cầu xác minh email hoặc số điện thoại và không hỗ trợ đăng nhập qua nhà cung cấp mạng xã hội | `UC-21`; `E01`; `BIZ-124`, `BIZ-129` |
| `FR-51` | Buyer nộp được hồ sơ đăng ký organizer gồm tên tổ chức và mô tả ngắn; hồ sơ ở `PENDING` và chưa cấp thêm vai trò nào | `UC-22`; `E02`; `BIZ-134`, `BIZ-151` |
| `FR-52` | Việc duyệt hồ sơ organizer **chỉ được coi là hoàn tất** sau khi nguồn danh tính xác nhận đã cấp vai trò `ORGANIZER` | `UC-23`; `E03`; `BIZ-140` |
| `FR-53` | Sau khi duyệt, tài khoản giữ vai trò `BUYER` và nhận thêm `ORGANIZER` | `UC-23`; `E03`; `BIZ-134` |
| `FR-54` | Khi từ chối hồ sơ organizer, hệ thống bắt buộc admin nhập và lưu lý do; hồ sơ chuyển `REJECTED` và không hỗ trợ gửi lại. Không yêu cầu giấy tờ kinh doanh, tài khoản ngân hàng hoặc bộ nhận diện thương hiệu | `UC-23`; `E04`; `BIZ-137`, `BIZ-138`, `BIZ-151` |
| `FR-55` | Chỉ hồ sơ organizer `ACTIVE` được hiển thị công khai | `UC-23`, `UC-27`; `BIZ-141` |

### 2.6 Dấu vết thao tác

`B5-OPEN-07` giao cho B8 phần **nghiệp vụ** của yêu cầu dấu vết: thao tác nào bắt buộc để lại vết. Trường dữ liệu, payload và thời gian lưu giữ thuộc B13 và B16.

| Mã | Nội dung | Nguồn gốc |
|---|---|---|
| `FR-65` | Hệ thống lưu dấu vết đủ để điều tra cho **mọi** yêu cầu check-in, cả khi thành công lẫn khi bị từ chối | `UC-20`; B3 §5.5; `D01`, `D03` |
| `FR-66` | Hệ thống lưu dấu vết cho thao tác đánh dấu một sự kiện đã chi trả, gồm người thực hiện | `UC-19`; B3 §4.5 bước 5 |
| `FR-67` | Hệ thống lưu dấu vết cho việc duyệt sự kiện, gồm người duyệt và tỷ lệ phí đã nhập | `UC-04`; B4 §8.1 |
| `FR-68` | Hệ thống lưu dấu vết cho việc xác nhận, từ chối hoặc chủ động hủy một sự kiện, gồm người thực hiện | `UC-08`; B4 §8.1 |

Danh sách trên là **đóng** theo `BIZ-152`, quyết định chốt đúng bốn nhóm thao tác bắt buộc lưu dấu vết. Các kết quả từ chối phía người mua trong luồng đặt vé — hết ghế, vượt giới hạn mua, mã khuyến mãi không hợp lệ — **không** thuộc diện bắt buộc lưu dấu vết, theo đúng `BIZ-152` cùng B4 §2 và B4 §5.1 cấm suy rộng yêu cầu audit cho mọi từ chối. Muốn thêm nhóm nào thì cần một quyết định riêng.

### 2.7 Năng lực tối giản và truy vấn chỉ đọc

Bảy ca chỉ liệt kê ở B6 §3.6 (`UC-24`–`UC-30`) nhận một yêu cầu gộp cho mỗi ca, theo đúng nguyên tắc không lập ma trận truy vết đầy đủ cho thao tác đọc. Hai dòng cuối bảng phục vụ `UC-01`/`UC-02` chứ không thuộc §3.6.

| Mã | Nội dung | Nguồn gốc |
|---|---|---|
| `FR-57` | Buyer tìm được sự kiện đã công bố | `UC-24`; `BIZ-127` |
| `FR-58` | Buyer xem được chi tiết một sự kiện đã công bố | `UC-25` |
| `FR-59` | Buyer theo dõi/bỏ theo dõi được organizer ở mức đơn giản; một cặp có tối đa một quan hệ hiện hành, thao tác lặp không nhân bản | `UC-26`; `BIZ-128`, BIZ-157; GOV-101 |
| `FR-60` | Buyer xem được hồ sơ organizer công khai | `UC-27`; `BIZ-141` |
| `FR-61` | Buyer xem được danh sách đơn và vé của chính mình | `UC-28`; `BIZ-072` |
| `FR-62` | Organizer xem được danh sách sự kiện thuộc chính mình và báo cáo đối soát của các sự kiện đó | `UC-29`, `UC-17` |
| `FR-70` | Organizer xem được thống kê đọc của sự kiện thuộc chính mình, gồm số vé đã bán và số liệu tài chính đã tổng hợp | `UC-30`; B4 §8.5 |
| `FR-63` | Organizer chọn được một địa điểm đã có để dùng cho sự kiện của mình; một địa điểm dùng lại được cho nhiều sự kiện. Hệ thống không biết hoặc kiểm sức chứa vật lý; nguồn cung do organizer quyết định | `UC-01`, `UC-02`; `BIZ-125`, `BIZ-149` |
| `FR-64` | Organizer gắn được nhãn phân loại cho sự kiện của mình | `UC-01`; `BIZ-126` |

### 2.8 Quy ước chung đã xác nhận cho yêu cầu trên

- BIZ-158/GOV-137/GOV-139: chỉ VND; đầu vào giá vé/giảm giá nguyên, không lẻ thập phân; giảm phần trăm tính một lần trên tổng đơn và HALF_UP về nguyên đồng. Ràng buộc tiền không áp lên tọa độ hình học.
- PRJ-024: không hiện thực bảng/workflow/dashboard cho Q-07 (thu sai số tiền hoặc không xác định được liên kết). FR-24 vẫn bắt buộc kiểm chữ ký, đơn, lần thử, số tiền; dữ liệu sai không tạo vé. FR-26/27/34/35 về thu muộn/trùng đã liên kết vẫn giữ nguyên. Báo cáo phải công bố giới hạn, không nhận là đã xử lý trọn mọi ngoại lệ của cổng.
- Tên/chuyển trạng thái đơn–tiền–vé đã được chọn theo GOV-131/136 tại phiếu B12/B13; B8 không tự đặt enum mới.

## 3. Yêu cầu phi chức năng

Theo Tầng B, mục này **chưa gắn con số**. Mỗi dòng phải nói rõ sẽ đo bằng gì; ngưỡng cụ thể được gắn ở B9 và xếp ưu tiên ở B10.

> **Một dòng đổi ở `B8-v0.12`: `NFR-06`** (`RES-042`). Việc phải có **một mã tương quan duy nhất** cho mỗi giao dịch chuyển từ *"một trong các cách đạt được"* thành **ràng buộc đã chốt**; chỉ định dạng, cách sinh, cách truyền và cách ghi vào log còn thuộc B16.
>
> **Vì sao đổi.** Ba nguồn đang nói khác nhau và `B10-v0.3` đã tự phân xử mà không ghi lại xung đột. Câu cũ của `NFR-06` để mở; nhưng `B9-v0.5` `QS-13` — **cũng đã `APPROVED`** — chốt cơ chế ngay trong ô bất biến, và `docs/quy-trinh-lam-viec.md` PHẦN 6, **thẩm quyền cấp 3**, liệt kê mã tương quan là điều kiện tối thiểu để tích hợp cơ chế chẩn đoán. Khi phải chọn một nguồn để sửa thì sửa nguồn thấp nhất. Chi tiết lập luận tại `RES-042`.
>
> **Điều này không tiền-chốt kiến trúc:** phương án gộp một tiến trình hay chia nhiều service đều mang được mã tương quan. Nó ràng buộc **dữ liệu quan sát phải sinh ra**, không ràng buộc cách phân rã.
>
> **`B9` `QS-13` và tài liệu quy trình chủ không phải sửa một chữ** — chúng đã đúng từ trước.

> **Ba dòng đổi ở `B8-v0.11`** theo `RES-034`, `RES-037` và `RES-038`. Ở vòng đó, không dòng nào trong `NFR-01`–`NFR-06`, `NFR-10`, `NFR-11` bị đụng tới; `NFR-06` chỉ đổi ở vòng `v0.12` sau này.
>
> - **`NFR-07` đã gỡ.** Nó đo mức hữu ích của lời giải thích — việc của **bộ tài liệu RCA**, nơi đã có sẵn bộ tiêu chí ở tài liệu độ đo. **Giữ khoảng trống số hiệu**, không đánh số lại, để khoảng 40 chỗ đang dẫn `NFR-*` không bị lệch.
> - **`NFR-08` viết trung tính.** Ràng buộc chỉ-đọc vẫn cần vì cơ chế chẩn đoán sẽ chạy trong FlashTicket; nhưng nó không còn gắn với một *"trợ lý"* cụ thể. Đây là một trong ba giới hạn của `PRJ-002` được giữ lại (`RES-034`).
> - **`NFR-09` sửa khái niệm.** `glossary` §6 từng định nghĩa **Context chẩn đoán** là dấu vết *đã* khử nhạy cảm, nên câu cũ — *"Context chẩn đoán phải được khử…"* — dùng khái niệm **ngược nghĩa chính nó**. Mục từ đó nay đã gỡ; câu mới nói thẳng thứ bị lọc là **dấu vết đã chọn và liên kết**. Yêu cầu nghiệp vụ theo `PRJ-007` **không đổi**: vẫn phải lọc trước khi dữ liệu rời phạm vi kiểm soát; tập trường và vị trí lọc chờ B13/B16.
> - **`NFR-12` là dòng mới**, neo nghĩa vụ tích hợp mà `RES-023` đã chốt nhưng chưa tài liệu hệ thống nào ghi. Câu chữ đã được Lê Văn Minh chốt ngày 2026-08-27, `USER_CONFIRMED` tại `RES-038`.

**Phân loại theo nghĩa, không theo nguồn.** Một yêu cầu là **chức năng** khi nó mô tả *một tác nhân làm gì và hệ thống đáp lại thế nào*; là **phi chức năng** khi nó ràng buộc *hệ thống phải có tính chất gì* mà không đặt ra một luồng thao tác mới. Việc bảng này không dòng nào dẫn về use case là **hệ quả** của cách phân loại đó, không phải lý do.

`NFR-12` được xếp phi chức năng theo nghĩa đó, và cùng nhóm với `NFR-11` — dòng cũng nói về **thứ hệ thống phải phơi ra** (*"kết quả từ chối phải phân biệt được nguyên nhân để người dùng và người vận hành xử lý đúng"*) mà không mô tả luồng thao tác nào.

> **Điểm phải theo dõi.** Vế thứ hai của `NFR-12` — *kết quả phải đến được người có quyền sử dụng* — nằm sát ranh giới. Nó còn là ràng buộc **chừng nào chưa có tác nhân và luồng cụ thể**. Khi `RES-039` đóng và chỉ ra ai dùng, dùng bằng cách nào, thì **lúc đó phát sinh use case và có thể phát sinh giao diện** — phải trình tác động và xin xác nhận riêng theo `GOV-019`. **Không** tạo use case trước thời điểm đó: làm vậy là tiền-chốt luồng thao tác trước khi biết ai dùng.

| Mã | Nội dung | Sẽ đo bằng | Nguồn gốc |
|---|---|---|---|
| `NFR-01` | Các bất biến cốt lõi của vòng đời vé phải giữ nguyên khi nhiều yêu cầu xảy ra gần đồng thời | Tập ca kiểm thử đồng thời cho từng bất biến `INV-01`, `INV-04`, `INV-05`, `INV-09`; kết quả đạt hoặc không đạt cho từng ca | A2; A3 `MT-1`, `MT-2` |
| `NFR-02` | Thông điệp hoặc thao tác lặp không được tạo tác dụng phụ lặp | Tập ca phát lại: callback thanh toán lặp, kích hoạt hoàn lặp, hủy đơn lặp, quét QR lặp; kiểm trạng thái sau khi phát lại | A2; `HOT-03`; `INV-06`, `INV-07`, `INV-08` |
| `NFR-03` | Khi một bước bị lỗi hoặc chậm, hệ thống phải phục hồi về trạng thái chấp nhận được thay vì để lại trạng thái mâu thuẫn | Tiêm lỗi vào các bước xuyên thành phần rồi kiểm trạng thái cuối so với bất biến; đặc biệt lỗi sau khi đã thu tiền | A2; A3 `MT-2`; `HOT-01` |
| `NFR-04` | Hệ thống đáp ứng được mức tải mục tiêu ở cấu hình được công bố, và nêu rõ chi phí hiệu năng của cách phối hợp trạng thái đã chọn | Thử tải có kiểm soát và tăng dần trên cấu hình công bố; đo độ trễ và thông lượng ở từng mức tải. Không tuyên bố mô phỏng lưu lượng sản xuất; **ngưỡng chốt ở B9/B10** | A3 `MT-3`; `RES-003`; A4 câu hỏi phụ |
| `NFR-05` | Dấu vết vận hành phải hỗ trợ việc lần ra nguyên nhân của một sự cố đã xảy ra khi bằng chứng thu được đủ. Hệ thống **không** cam kết loại bỏ việc tái hiện lỗi; khi dấu vết chưa đủ hoặc khi cần kiểm tra một bản sửa thì vẫn phải tái hiện | Với tập ca lỗi có nguyên nhân biết trước, đo tỷ lệ ca mà dấu vết chứa đủ tín hiệu cần thiết | A3 `MT-4`; `PRJ-002`; B1 chẩn đoán §1 bước 3–6 |
| `NFR-06` | Từ dấu vết thu được, phải dựng lại được trình tự xử lý của cùng một giao dịch xuyên các thành phần. **Mọi bản ghi thuộc cùng một giao dịch phải truy được qua một mã tương quan duy nhất, và mã đó phải đi qua được mọi bước chuyển — kể cả bước chuyển bất đồng bộ, nếu phương án kiến trúc có bước như vậy** (`RES-042`) | Chọn ngẫu nhiên các giao dịch mẫu, kiểm xem có dựng lại được trình tự đầy đủ chỉ từ dấu vết hay không. **Định dạng mã, cách sinh, cách truyền qua từng loại biên và cách ghi vào log vẫn thuộc B16** | B1 chẩn đoán §1 bước 1 và bước 5, nêu đúng điểm nghẽn “thiếu mã giao dịch thống nhất” |
| ~~`NFR-07`~~ | **Đã gỡ ngày 2026-08-27** (`RES-034`). Mức hữu ích của lời giải thích do bộ tài liệu RCA đo, tại `docs/research-rca/A9-do-do-thuc-nghiem.md` §6. Số hiệu giữ trống, không tái sử dụng | — | — |
| `NFR-08` | Mọi thành phần quan sát hoặc chẩn đoán **chỉ được đọc**: không có đường ghi vào dữ liệu nghiệp vụ và không truy cập trực tiếp schema nghiệp vụ | Kiểm quyền của thành phần đó trên mọi nguồn dữ liệu nó chạm tới | `RES-034` — toàn bộ ranh giới chỉ đọc, không chỉ giới hạn 3; `tang-b` §3.4(d); `RES-037` |
| `NFR-09` | **Dấu vết đã chọn và liên kết** phải được khử hoặc che các trường nhạy cảm **trước khi** rời phạm vi kiểm soát của ứng dụng | Dùng bộ dữ liệu kiểm thử có trường nhạy cảm đã biết; kiểm đầu ra rời phạm vi kiểm soát không còn giá trị gốc hoặc đã được che theo quy tắc. Tập trường và vị trí lọc chốt ở B13/B16 | `glossary` §6; `PRJ-007`; `RES-037` |
| `NFR-10` | Mọi lệnh làm đổi trạng thái phải được kiểm quyền theo vai trò **và** theo quan hệ sở hữu, không chỉ theo vai trò | Ca kiểm thử phân quyền chéo: organizer thao tác trên sự kiện của người khác, buyer xem QR của đơn người khác | B4 §8.1; `BIZ-135` |
| `NFR-11` | Kết quả từ chối phải phân biệt được nguyên nhân để người dùng và người vận hành xử lý đúng | Đối chiếu tập lý do trả về với danh sách ngoại lệ đã liệt kê ở `FR-22` và `FR-46` | B3 §3.4, §5.4; B6 §6 |
| `NFR-12` | Hệ thống phải **tích hợp và chạy được** cơ chế chẩn đoán nguyên nhân gốc trên dữ liệu quan sát của chính nó. Kết quả — nguyên nhân đã xếp hạng, bằng chứng, lời giải thích và bước kiểm tra gợi ý — phải **đến được người có quyền sử dụng**. Vị trí triển khai, người sử dụng cụ thể và hình thức cung cấp kết quả quyết định ở gate sau | Kiểm ở `B11` rằng phương án kiến trúc được duyệt có chỗ cho cơ chế này và có đường lấy dữ liệu; kiểm ở `B15` rằng kết quả đến được một người có quyền. Câu chữ `USER_CONFIRMED` tại `RES-038` | `RES-023` mức 2; `RES-038`; điểm mở `RES-039` về ai dùng và bằng hình thức nào |

## 4. Vấn đề `OPEN` và kết quả đã đóng

### 4.1 Vấn đề vẫn `OPEN`

| ID | Vấn đề | Nguồn | Gate xử lý |
|---|---|---|---|
| `B8-OPEN-01` | Ngưỡng số của `NFR-04` và `NFR-05` chưa được gắn. `NFR-07` đã gỡ khỏi bảng (`RES-034`); ngưỡng cho lớp giải thích nay do `A9` §6 của bộ RCA giữ | Quy định của Tầng B: con số chỉ chốt sau khi dựng kịch bản chất lượng | B9, xếp ưu tiên ở B10 |
| `B8-OPEN-05` | Trường dữ liệu, payload, thời gian lưu giữ của dấu vết và tập trường nhạy cảm cần lọc chưa chốt | `B5-OPEN-07`; `B4-OPEN-04`; `NFR-09` | B13, B16. Phần nghiệp vụ — thao tác nào bắt buộc lưu vết theo `BIZ-152` và việc phải khử/che trước khi chuyển ra ngoài theo `PRJ-007` — đã được chốt |

### 4.2 Điểm đã được Lê Văn Minh đóng

| ID lịch sử | Kết quả | Bằng chứng |
|---|---|---|
| `B8-OPEN-02` | Nghĩa nghiệp vụ đã đóng: hoàn khoản thu trùng/đến muộn không tạo trạng thái riêng cho đơn và không làm đơn/vé hợp lệ mất hiệu lực; tên/chuyển trạng thái chờ B12/B13 | `BIZ-150`; `BIZ-130` |
| `B8-OPEN-03` | Hệ thống không biết/kiểm sức chứa vật lý; organizer quyết định nguồn cung | `BIZ-149` |
| `B8-OPEN-04` | Hồ sơ giữ tên tổ chức, mô tả ngắn và lý do từ chối khi có; loại giấy tờ/ngân hàng/nhận diện khỏi phạm vi | `BIZ-151` |
| `B8-OPEN-06` | Giữ khử/che dữ liệu nhạy cảm và khôi phục `NFR-09` | `PRJ-007` |
| — | Danh sách thao tác bắt buộc lưu dấu vết ở §2.6 là danh sách đóng, gồm đúng bốn nhóm | `BIZ-152` |

## 5. Phép tự kiểm và điều kiện chuyển trạng thái

- [x] Mọi yêu cầu chức năng truy được về ít nhất một use case của `B6-v0.14`; `FR-56` cũ đã được gỡ vì nó là giới hạn phạm vi chứ không phải chức năng.
- [x] Cả 30 use case của `B6-v0.14` đều có ít nhất một yêu cầu chức năng tương ứng; đếm lại `UC-01`–`UC-30` trên cột `Nguồn gốc` không thiếu ca nào. `B6-v0.13` **không thêm, bớt hay đổi** use case nào so với `B6-v0.12`, nên phép đếm giữ nguyên kết quả.
- [x] Số ca ở §2.7 khớp B6 §3.6: **bảy** ca đọc, không phải sáu. Con số cũ có từ trước khi `UC-30` được thêm.
- [x] Danh sách dấu vết ở §2.6 dẫn đúng `BIZ-152`, quyết định đã đóng nó.
- [x] Phần khai “Đầu vào và phiên bản” bao được mọi nguồn mà cột `Nguồn gốc` dẫn tới, gồm B2, B3, B4, B5 và `A3-v0.5`, và mỗi nguồn khai đúng phiên bản cùng trạng thái hiện hành — `B2-v0.12`, `B3-v0.11`, `B4-v0.15`, `B5-v0.14`, `B6-v0.14`, tất cả `APPROVED` ngày 2026-08-27 (`GOV-033`). Trước ngày đó ô này ghi `REVIEW_READY` và **đúng vào lúc đó**; lời khai đã được cập nhật theo trạng thái hiện hành chứ không phải viết lại lịch sử. Ô này từng sai ở `B8-v0.8` vì A3 đã lên `v0.4` mà B8 còn khai `v0.3`; đã sửa tại `B8-v0.9`.
- [x] Mười hai ca được đặc tả đầy đủ ở B6 §6 đều được tách thêm yêu cầu cho từng quy tắc mang bất biến.
- [x] Không yêu cầu phi chức năng nào chứa con số; mỗi dòng đều có cột cách đo.
- [x] Mỗi yêu cầu phi chức năng truy được về Tầng A hoặc về một điểm nghẽn cụ thể trong B1.
- [x] **Không yêu cầu phi chức năng nào khóa sẵn một cơ chế kỹ thuật, trừ đúng một chỗ được chốt có chủ ý và có lý do ghi lại.** Chỗ đó là `NFR-06`: từ `v0.12`, mã tương quan là ràng buộc đã chốt (`RES-042`), không còn là một trong nhiều cách. **Ô này ở `v0.11` ghi *"`NFR-06` phát biểu ở mức kết quả"* và điều đó nay không còn đúng** — ghi rõ thay vì để ô cũ đứng. Phép thử vẫn giữ: một cơ chế chỉ được chốt sớm nếu nó **không phân biệt phương án kiến trúc nào**; mã tương quan thỏa phép thử đó, còn cách truyền qua từng loại biên thì không nên vẫn ở B16.
- [x] `NFR-05` không hứa quá ba giới hạn còn hiệu lực ở `RES-034`: không cam kết bỏ tái hiện lỗi, không coi cơ chế chẩn đoán là bên kết luận cuối cùng. `NFR-07` đã gỡ nên ô này không còn áp cho nó.
- [x] Không dòng nào trong bảng phi chức năng dẫn về một use case — đã đếm lại sau khi thêm `NFR-12`; quy tắc truy vết use case chỉ áp cho §2.
- [x] Yêu cầu dấu vết ở §2.6 là danh sách đóng, không suy rộng cho mọi trường hợp từ chối.
- [x] Không yêu cầu nào chỉ định service, schema, API, hợp đồng hay cơ chế kỹ thuật.
- [x] Không đặt tên trạng thái đơn; giữ đúng nghĩa `BIZ-150` và chuyển phần tên/cách biểu diễn sang B12/B13 theo `BIZ-130`.
- [x] Codex rà chéo B8 vòng một và trả tám phát hiện; tác giả đã sửa toàn bộ trong `B8-v0.2`.
- [x] Codex rà chéo vòng hai; sửa xong ở `B8-v0.3`.
- [x] Codex đã rà B8-v0.6, gồm `FR-70`, các quyết định mới, cổng đầu vào và việc khôi phục `NFR-09`.
- [x] Lê Văn Minh đã rà danh sách yêu cầu và duyệt nguyên trạng, không cắt hoặc bổ sung mã nào.
- [x] Chuỗi `B2 → B3 → B4 → B5` và `B6-v0.12` đã được duyệt trước B8-v0.8, đúng thứ tự cổng.
- [x] Lê Văn Minh đã đóng `B8-OPEN-02`, `B8-OPEN-03`, `B8-OPEN-04`, `B8-OPEN-06`; `B8-OPEN-01` và `B8-OPEN-05` thuộc đúng gate sau.
- [x] Lê Văn Minh đã duyệt B6-v0.12 rồi duyệt toàn bộ B8-v0.8 thành `APPROVED` ngày 2026-08-22; AI không tự đánh dấu thay.
- [x] Lê Văn Minh đã duyệt `B8-v0.11` ngày 2026-08-27 (`GOV-033`), sau `B6-v0.14` và `B7-v0.11`. Bản này gỡ `NFR-07`, viết lại `NFR-08`/`NFR-09` và thêm `NFR-12`.
- [x] **Lê Văn Minh đã rà và duyệt `B8-v0.12` ngày 2026-08-28** (`GOV-043`), **đầu chuỗi**, trước `B9-v0.6` và `B10-v0.5`. AI không tự đánh dấu thay: agent đã từ chối tích ô này từ một câu không trích được thành lời duyệt, và chỉ tích sau khi Lê Văn Minh chọn *"Duyệt cả ba — tôi đã đọc"*.

## 6. Phần dùng cho báo cáo

Sau khi được duyệt, phần Yêu cầu của báo cáo có thể dùng bảng yêu cầu chức năng theo nhóm nghiệp vụ và bảng yêu cầu phi chức năng kèm cách đo. Lập luận đáng giữ là: mỗi yêu cầu chức năng bắt nguồn từ một use case, mà mỗi use case bắt nguồn từ một sự kiện nghiệp vụ đã được mô hình hóa — chuỗi này giải thích vì sao phạm vi hệ thống không được suy từ một giao diện có sẵn.

Không đưa nguyên trạng mã `FR-*`, `NFR-*`, sổ `OPEN` hoặc trạng thái governance vào báo cáo nếu chúng không giúp giải thích một quyết định.

## 7. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B8-v0.13` | 2026-09-14 | Đồng bộ FR theo B6-v0.15/B7-v0.13 và quyết định tiền, email, ghế ẩn, yêu cầu hủy, follow, phạm vi Q-07; NFR giữ nguyên; REVIEW_READY | GOV-143 |
| `B8-v0.12` | 2026-08-28 | **Sửa `NFR-06` theo `RES-042`.** Việc phải có **một mã tương quan duy nhất** cho mỗi giao dịch, đi qua được mọi bước chuyển kể cả bước bất đồng bộ, chuyển từ *"một trong các cách đạt được"* thành **ràng buộc đã chốt**; định dạng, cách sinh, cách truyền và cách ghi vào log vẫn thuộc B16. **Lý do:** ba nguồn đang nói khác nhau — `NFR-06` để mở, `B9-v0.5` `QS-13` đã chốt trong ô bất biến, và tài liệu quy trình chủ (thẩm quyền cấp 3) liệt kê nó là điều kiện tối thiểu; `B10-v0.3` tự phân xử theo `NFR-06` mà không ghi lại xung đột. **Toàn bộ `FR-01`–`FR-70` và mọi `NFR` khác không đổi một chữ.** Tài liệu chuyển `REVIEW_READY`: đây là thay đổi vật chất lên một yêu cầu, không phải sửa câu chữ. **Sửa kèm một lỗi lời khai của `v0.11`:** nhật ký ghi câu chữ `NFR-12` là `CANDIDATE`, trong khi `RES-038` và chính §3 của tài liệu này đều ghi `USER_CONFIRMED` | Phân xử xung đột nguồn |
| `B8-v0.11` | 2026-08-27 | **Gỡ `NFR-07`** (mức hữu ích của hỗ trợ chẩn đoán) — chuyển sang bộ RCA, giữ khoảng trống số hiệu. **Viết trung tính `NFR-08`** thành ràng buộc chỉ-đọc cho mọi thành phần quan sát/chẩn đoán. **Sửa khái niệm `NFR-09`**: thứ bị lọc là *dấu vết đã chọn và liên kết*, không phải *Context chẩn đoán* — mục từ đó định nghĩa sẵn là đã khử nhạy cảm nên câu cũ ngược nghĩa chính nó. **Thêm `NFR-12`** neo nghĩa vụ tích hợp cơ chế RCA vào FlashTicket, câu chữ `USER_CONFIRMED` tại `RES-038` — dòng nhật ký này trước 2026-08-28 ghi nhầm là `CANDIDATE`, ngược với chính §3 của tài liệu và với sổ quyết định. Khai lại đầu vào theo chuỗi mới. **Toàn bộ `FR-01`–`FR-70` và `NFR-01`–`NFR-06`, `NFR-10`, `NFR-11` không đổi một chữ** | Gỡ thiết kế trợ lý cũ |
| `B8-v0.10` | 2026-08-26 | Đồng bộ khai `A2-v0.2`/`A4-v0.2` sau tái baseline theo `DH-TEN` (`RES-032`). Hai phiếu này chỉ được B8 dẫn ở phần nguồn gốc của các yêu cầu phi chức năng; **nội dung `FR`/`NFR` không đổi**. `NFR-07`/`NFR-08` giữ nguyên vì `DH-MT4` vẫn yêu cầu lớp AI giải thích | Lan truyền tái baseline |
| `B8-v0.9` | 2026-08-26 | Đồng bộ khai đầu vào sang `A3-v0.4`. `A3-v0.4` bổ sung nhóm mục tiêu `MT-R1`–`MT-R4` cho bộ nghiên cứu và ghi việc đổi vai `MT-1`–`MT-3` ở mức `CANDIDATE`; **câu chữ `MT-1`–`MT-5` mà B8 dẫn không đổi**, nên không yêu cầu nào của B8 bị ảnh hưởng và tài liệu giữ nguyên `APPROVED` | Lan truyền trạng thái |
| `B8-v0.8` | 2026-08-22 | Đồng bộ khai đầu vào sang `B2-v0.10`/`B3-v0.10`/`B4-v0.14`/`B5-v0.12`/`B6-v0.12` sau khi B2 bổ sung mục từ **Yêu cầu hủy sự kiện**; `FR-11`–`FR-13` và `FR-68` không đổi nội dung vì đã mô tả đúng hành vi từ trước | Lan truyền trạng thái |
| `B8-v0.7` | 2026-08-22 | Sửa theo vòng kiểm toán đọc toàn văn: §2.7 từ “sáu ca” thành “bảy ca” cho đúng B6 §3.6; §2.6 dẫn `BIZ-152` khi tuyên bố danh sách dấu vết là đóng; khai đủ B2/B3/B4/B5 vào phần nguồn truy vết vì cột `Nguồn gốc` dẫn trực tiếp tới chúng; đồng bộ phiên bản đầu vào sang `B6-v0.12` | Sửa sau kiểm toán |
| `B8-v0.6` | 2026-08-22 | Đồng bộ B6-v0.10; đóng nghĩa trạng thái đơn, sức chứa và trường hồ sơ; giữ danh sách audit; khôi phục `NFR-09`; làm rõ thử tải có kiểm soát; chuyển sang `REVIEW_READY` nhưng vẫn chờ B6 được duyệt trước | Lan truyền quyết định và chuẩn bị duyệt |
| `B8-v0.5` | 2026-08-21 | Đồng bộ phiên bản B6 trong ô tự kiểm và ghi đúng lịch sử rà chéo; nêu rõ `B8-v0.4` chưa qua rà chéo | Sửa lỗi biên tập |
| `B8-v0.4` | 2026-08-21 | Bổ sung `FR-70` cho ca thống kê đọc `UC-30` mà B6-v0.6 vừa thêm; đồng bộ phiên bản đầu vào | Lan truyền từ B6 |
| `B8-v0.3` | 2026-08-21 | Sửa vòng rà chéo thứ hai: nêu rõ xung đột giữa việc gỡ `NFR-09` và mục từ **Context chẩn đoán** của B2 thay vì coi là đã khép; gỡ `FR-69` trùng `FR-54`; trả `B8-OPEN-02` về đúng gate B6/B8 thay vì đề xuất hoãn sang B12; sửa số đếm mục `OPEN` | Sửa sau rà chéo vòng 2 |
| `B8-v0.2` | 2026-08-21 | Sửa theo tám phát hiện của vòng rà chéo: `NFR-05` không còn hứa bỏ tái hiện lỗi; `NFR-06` phát biểu ở mức kết quả; `NFR-07` nói rõ là phép đo trên tập giả thuyết; gỡ `NFR-09` theo quyết định phạm vi; `FR-33` bổ sung vế tồn kho theo `BIZ-064`; sửa mã quyết định trích sai ở `FR-27` và `FR-40`; gỡ `FR-56` và tách `FR-63`; bổ sung §2.6 về yêu cầu dấu vết; định tuyến lại bốn mục `OPEN` về đúng gate | Sửa sau rà chéo |
| `B8-v0.1` | 2026-08-21 | Bản đầu: 63 yêu cầu chức năng rút từ 29 use case của B6-v0.3, 11 yêu cầu phi chức năng kèm cách đo và không gắn số, cùng năm mục `OPEN` | Tạo mới |
