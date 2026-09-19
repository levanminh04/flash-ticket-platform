# B9 — Bộ kịch bản chất lượng

> **Cập nhật ngữ cảnh 18/09:** đề tài/nhiệm vụ theo [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md), nhóm bốn người; mobile là phần phụ theo PRJ-035. Nội dung nghiệp vụ/bất biến và baseline duyệt bên dưới không được đổi trong đợt này. Các phiên bản A1–A6 và DH-* được dẫn trong hồ sơ duyệt là đầu vào tại thời điểm duyệt; bản diễn giải A1–A6 hiện hành đang REVIEW_READY. Không coi bản mới tự được phê duyệt, không dùng lời khai cũ để hạ nhiệm vụ xây dựng/đánh giá DT18.

- Phiên bản: `B9-v0.8`
- Trạng thái: `APPROVED`
- Phân lớp: `FORMATION`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-08-29, cho `B9-v0.8`, **sau `B7-v0.12`** và **trước `B10-v0.8`** (`GOV-059`) · `B9-v0.7` được duyệt 2026-08-29, **giữa chuỗi `B8-v0.12` → `B9-v0.7` → `B10-v0.7`** (`GOV-050`), sau `B8-v0.12` và trước `B10-v0.7` · **Lịch sử:** `v0.7` từng bị trả về `REVIEW_READY` tại `GOV-047` vì nó điền 18 ô *Mức ưu tiên* sau khi `v0.6` được duyệt; nay đã được duyệt đúng phiên bản. `B9-v0.6` duyệt 2026-08-28 (`GOV-043`); `B9-v0.5` duyệt 2026-08-27 (`GOV-033`)
- Đầu vào và phiên bản: `docs/domain/B7-aggregates-and-invariants.md` — `B7-v0.12`, `APPROVED` 2026-08-29 (`GOV-058`), **trước `B9-v0.8`**; `docs/domain/B8-requirements.md` — `B8-v0.12`, `APPROVED` 2026-08-28, **trước `B9`** (`GOV-043`); `docs/research/A3-research-objectives.md` — `A3-v0.5`, `DRAFT`; `MT-1`–`MT-4` đã được xác nhận định tính (`RES-002`), `MT-5` đổi câu chữ ngày 2026-08-27 (`RES-041`). **B9 không dẫn nội dung `MT-5` ở bất kỳ kịch bản nào** — đã kiểm: 0 chỗ dùng trong thân bài, nên thay đổi đó không đụng kịch bản nào; `docs/glossary.md` — `B2-v0.12`, `APPROVED` 2026-08-27, dùng làm ràng buộc từ vựng *(Rà 2026-08-29 — `GOV-052`: `A3` nay là `A3-v0.6`; `v0.6` chuyển `MT-5` sang Vòng 1 (`RES-045`) và đổi **vai** của `MT-1`–`MT-3` (`RES-046`) mà **không đổi một chữ câu chữ nào của `MT-1`–`MT-4`**, nên phần được tài liệu này dẫn không đổi.)*
- Nguồn phạm vi: `docs/domain/B4-domain-event-map.md` — `B4-v0.15` §10, bảng bất biến và hotspot cùng cột *"Gate xử lý tiếp"*

> **Cổng phê duyệt — đã thỏa.** Toàn chuỗi `B2-v0.12 → B3-v0.11 → B4-v0.15 → B5-v0.14 → B6-v0.14 → B7-v0.11 → B8-v0.11` trở lại `REVIEW_READY` sau vòng `RES-034` — gỡ thiết kế trợ lý cũ khỏi bộ hệ thống — rồi được Lê Văn Minh duyệt lại **đúng thứ tự** ngày 2026-08-27 (`GOV-033`). B9 được duyệt **cuối chuỗi, sau `B8-v0.11`**, đúng yêu cầu Tầng B §3.3.
>
> **Thứ vòng đó đụng tới ở B9:** `NFR-07` bị gỡ nên §5 viết lại; ba kịch bản dấu vết đổi từ vựng; `NFR-12` là yêu cầu mới và **có** kịch bản riêng. **Mười bảy kịch bản `QS-01`–`QS-17` giữ nguyên mã và nguyên nội dung đo**; `QS-18` là kịch bản **mới** cho `NFR-12`/`NFR-08`.
>
> Câu này ở `B9-v0.4` từng ghi *"`NFR-12` là ràng buộc kiến trúc nên không sinh kịch bản"* — sai, và tự chống lại vế ngay sau nó. Mọi `NFR` khác trong tài liệu đều có kịch bản; `QS-18` đã đóng chỗ hổng đó ở `v0.5`.

> **Đề tài.** Đề tài chính thức là *"Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố"*, theo [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md). B9 thuộc **bộ tài liệu hệ thống** và sinh nội dung từ `B4`, `B7`, `B8`, `A3` — **không** sinh nội dung từ `docs/research-rca/`. Ngược lại, **phương pháp** chẩn đoán và lớp giải thích thuộc bộ RCA và không được đặc tả ở đây (`RES-033`). Mục §6 chỉ **đối chiếu** với `R0` §3 theo `RES-015`, và mọi dòng ở đó giữ `CANDIDATE`/`OPEN`; phép thử ở `docs/project/lien-ket-rca.md` §2.2 đã chạy.

> **Mẫu bản ghi.** Dùng bộ trường của mẫu `C2` ở Tầng C, không dùng bộ rút gọn trong ví dụ của phiếu B9. Lý do và bảng đối chiếu hai mẫu ở `README.md` §1.

---

## 1. Mục đích và giới hạn

B9 chuyển các yêu cầu phi chức năng của B8 và các bất biến/hotspot của B4–B7 thành **kịch bản có tiêu chí quan sát được**. Mỗi kịch bản nói rõ: kích thích gì, tác động lên đâu, hệ thống phải phản ứng thế nào, và **đo bằng gì**.

B9 **không** quyết định:

- cơ chế kỹ thuật để đạt một kịch bản — khóa, hàng đợi, giao dịch, Saga đều thuộc `B11`;
- service, schema, API hay đơn vị triển khai;
- mức ưu tiên của kịch bản — đó là `B10`.

**Hai loại độ đo, không trộn:**

| Loại | Nghĩa | Cách viết |
|---|---|---|
| **Bất biến** | Ràng buộc đúng/sai, không phụ thuộc đo đạc | Luôn phải đúng; sai một lần là không đạt |
| **Ngưỡng** | Con số, chỉ chốt sau một vòng đo thử | Để `OPEN` kèm điều kiện điền được, nếu chưa có |

Theo phép thử của phiếu B9: *"Không ép mọi tiêu chí thành số nếu số đó không có ý nghĩa."*

---

## 2. Phạm vi — lấy từ `B4` §10

Bảng `B4` §10 là thẩm quyền về việc bất biến nào đi tới B9. B9 không tự mở rộng và không tự thu hẹp.

| Bất biến / hotspot | Kịch bản phủ |
|---|---|
| `INV-01` — ghế không thuộc hai giữ chỗ; số khả dụng không âm | `QS-01` |
| `INV-04` — giới hạn mua theo tài khoản/sự kiện | `QS-02` |
| `INV-05` — một đơn một kết quả thu hợp lệ | `QS-03` |
| `INV-06` — không giải phóng/chốt hai lần | `QS-06` |
| `INV-07` — thử lại phát hành không tạo thêm quyền | `QS-07` |
| `INV-08` — một yêu cầu hoàn logic cho mỗi khoản thu | `QS-08` |
| `INV-09` — một vé tối đa một check-in thành công | `QS-04` |
| `HOT-01` — hủy sự kiện lan tới N đơn với lỗi từng phần | `QS-10` |
| `HOT-02` — ranh giới đồng thời nguồn cung/giữ chỗ/đơn | `QS-01`, `QS-02` |
| `HOT-03` — callback lặp/đến muộn/lỗi sau thu tiền | `QS-03`, `QS-09` |
| `HOT-04` — check-in cạnh tranh | `QS-04`, `QS-05` |

**Không thuộc B9:** `INV-02`, `INV-03` (`B4` §10 chỉ định tuyến về `B7`); `INV-10`, `INV-11` (`B7`, rồi `B12`).

### 2.1 Độ phủ yêu cầu phi chức năng

| NFR | Kịch bản | Ghi chú |
|---|---|---|
| `NFR-01` | `QS-01`, `QS-02`, `QS-03`, `QS-04` | Bất biến cốt lõi dưới đồng thời |
| `NFR-02` | `QS-06`, `QS-07`, `QS-08` | Lặp không tạo tác dụng phụ lặp |
| `NFR-03` | `QS-09`, `QS-10`, `QS-17` | Phục hồi khi lỗi từng phần |
| `NFR-04` | `QS-05`, `QS-11` | Hiệu năng và tranh chấp |
| `NFR-05` | `QS-12` | Dấu vết đủ tín hiệu |
| `NFR-06` | `QS-13` | Dựng lại trình tự xuyên thành phần |
| `NFR-09` | `QS-14` | Khử/che trường nhạy cảm |
| `NFR-10` | `QS-15` | Quyền theo vai trò **và** theo sở hữu |
| `NFR-11` | `QS-16` | Phân biệt lý do từ chối |
| `NFR-08` | `QS-18` | Ràng buộc chỉ-đọc, đo bằng ô bất biến *số thao tác ghi nghiệp vụ = 0* |
| `NFR-12` | `QS-18` | Nghĩa vụ tích hợp và việc kết quả đến được người có quyền |
| ~~`NFR-07`~~ | — | **Đã gỡ khỏi bảng yêu cầu** ngày 2026-08-27 (`RES-034`); cách đo nay ở `A9` §6 |

---

## 3. Bộ kịch bản

Mỗi bản ghi dùng bộ trường `C2`. Ô `Mức ưu tiên` và `ADR liên quan` **cố ý để trống** — `B10` điền ưu tiên, `B11` điền ADR.

> **Xuất xứ của 18 ô `Mức ưu tiên` — đọc dòng này thay cho cụm ghi trong từng ô** (`GOV-053`, 2026-08-29). Mỗi ô dưới đây ghi *“`B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`)”*. **Lượt duyệt đó đã bị `GOV-047` rút** vì `B10` bị sửa sau khi duyệt. Xuất xứ đúng hiện nay là **`B10-v0.7` §2, duyệt 2026-08-29 (`GOV-050`)**. **Mười tám giá trị không đổi một chữ** qua `v0.5` → `v0.6` → `v0.7` — vẫn **13 Cao · 2 Trung bình · 3 Thấp** — nên câu chữ trong từng ô được giữ nguyên thay vì sửa 18 chỗ trong một tài liệu đã `APPROVED`.

### QS-01 — Chống bán vượt khi tranh chấp cao

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Tính đúng đắn dưới đồng thời |
| Nguồn kích thích | Nhiều người mua truy cập đồng thời |
| Kích thích | `N` yêu cầu giữ chỗ cùng nhắm vào một nguồn cung còn lại nhỏ hơn `N`, phát sinh trong một khoảng rất ngắn |
| Tạo tác | Các root nguồn cung `Ghế`, `Loại vé`, `Sector`, root `Giữ chỗ` và root `Đơn hàng` |
| Môi trường | Thời điểm mở bán, hệ thống ở mức tải cao nhất trong cấu hình được công bố |
| Phản ứng | Số yêu cầu được chấp nhận đúng bằng lượng còn lại; các yêu cầu còn lại nhận từ chối rõ ràng, không bị treo |
| Độ đo — (1) bất biến | Với `SEAT_MAP`: không ghế nào thuộc hai giữ chỗ hoặc đơn còn hiệu lực. Với `QUANTITY`/`STANDING`: số lượng khả dụng **không âm** tại mọi thời điểm. Sai lệch giữa số quyền đã cam kết và hạn mức bán bằng **0** |
| Độ đo — (2) ngưỡng | Độ trễ phân vị 95 của thao tác giữ chỗ ≤ `OPEN`; tỷ lệ lỗi hệ thống (khác từ chối hợp lệ) ≤ `OPEN`. Điền sau một vòng đo thử trên cấu hình công bố |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Workload đồng thời có kiểm soát; truy vấn đối chiếu sau khi chạy. Truy vết: `INV-01`, `HOT-02`, `NFR-01`, `MT-1`, `MT-2` |

### QS-02 — Giới hạn mua giữ được khi một tài khoản đặt nhiều đơn đồng thời

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Tính đúng đắn dưới đồng thời |
| Nguồn kích thích | Một tài khoản người mua, nhiều phiên hoặc nhiều thiết bị |
| Kích thích | Nhiều yêu cầu tạo đơn gần đồng thời cho cùng một sự kiện, tổng số vé vượt giới hạn mua |
| Tạo tác | Root `Giới hạn mua` và root `Đơn hàng` |
| Môi trường | Sự kiện đang mở bán |
| Phản ứng | Chỉ chấp nhận phần nằm trong giới hạn; phần vượt bị từ chối với lý do phân biệt được |
| Độ đo — (1) bất biến | Tổng vé **đang giữ cộng đã mua** của một tài khoản trong một sự kiện **không bao giờ vượt** giới hạn organizer đã nhập |
| Độ đo — (2) ngưỡng | Không áp dụng — đây là ràng buộc đúng/sai thuần |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Ca đồng thời trên cùng tài khoản; đối chiếu tổng sau khi chạy. Truy vết: `INV-04`, `HOT-02`, `BIZ-075`, `NFR-01` |

### QS-03 — Một đơn chỉ giữ một kết quả thu hợp lệ khi callback lặp

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Tính đúng đắn khi thông điệp lặp |
| Nguồn kích thích | Cổng thanh toán |
| Kích thích | Cùng một callback được gửi nhiều lần, hoặc nhiều callback tới gần đồng thời cho cùng một đơn |
| Tạo tác | Root `Xác nhận thanh toán` |
| Môi trường | Đơn còn hiệu lực; có thể có một lần thanh toán đang xử lý |
| Phản ứng | Callback lặp hội tụ về cùng một kết quả; không phát hành vé lần thứ hai; khoản thu thừa đi sang nhánh hoàn riêng |
| Độ đo — (1) bất biến | Mỗi đơn có **đúng một** kết quả thu hợp lệ. Số lần phát hành vé cho một đơn bằng **1**. Mỗi giao dịch thu thừa sinh **đúng một** yêu cầu hoàn độc lập |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Phát lại callback; ca gửi đồng thời. Truy vết: `INV-05`, `HOT-03`, `BIZ-013`, `BIZ-032`, `NFR-01`, `NFR-02` |

### QS-04 — Check-in cạnh tranh trên **cùng một vé**

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Tính đúng đắn dưới đồng thời |
| Nguồn kích thích | Nhiều thiết bị dùng cùng một tài khoản organizer — tình huống nghiệp vụ thật, `B2` chốt *"cùng tài khoản có thể hoạt động trên nhiều thiết bị"*. Trong phép thử, tranh chấp được tạo bằng **nhiều lời gọi API đồng thời**, không dựng dàn thiết bị thật (`GOV-042`) |
| Kích thích | Nhiều `Yêu cầu check-in` cho **cùng một vé** gửi gần đồng thời |
| Tạo tác | Root `Phát hành vé` và entity `Vé` bên trong nó |
| Môi trường | Trong cửa sổ check-in `eventStartAt <= now <= eventEndAt`; cổng vào lúc đông nhất |
| Phản ứng | Đúng một yêu cầu thành công; các yêu cầu còn lại bị từ chối với lý do **vé đã sử dụng**, không phải lỗi hệ thống |
| Độ đo — (1) bất biến | Số `Check-in thành công` trên một vé ≤ **1**, không có ngoại lệ. Không có hoàn tác, check-out hay tái vào cửa |
| Độ đo — (2) ngưỡng | Độ trễ phân vị 95 của một lượt quét ≤ `OPEN` |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Nhiều lời gọi API check-in đồng thời trên **cùng một vé**. Truy vết: `INV-09`, `HOT-04`, `BIZ-087`, `FR-45`, `NFR-01`, `GOV-042` |

### QS-05 — Check-in cạnh tranh trên **các vé khác nhau của cùng một đơn**

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Hiệu năng dưới tranh chấp |
| Nguồn kích thích | Nhiều thiết bị dùng cùng một tài khoản organizer — tình huống nghiệp vụ thật, `B2` chốt *"cùng tài khoản có thể hoạt động trên nhiều thiết bị"*. Trong phép thử, tranh chấp được tạo bằng **nhiều lời gọi API đồng thời**, không dựng dàn thiết bị thật (`GOV-042`) |
| Kích thích | Nhiều `Yêu cầu check-in` cho **các vé khác nhau nhưng thuộc cùng một đơn**, gửi gần đồng thời |
| Tạo tác | Root `Phát hành vé` — nơi mọi vé của một đơn cùng nằm dưới |
| Môi trường | Trong cửa sổ check-in; đơn có nhiều vé |
| Phản ứng | Mọi yêu cầu hợp lệ đều thành công; không yêu cầu nào bị từ chối chỉ vì tranh chấp nội bộ |
| Độ đo — (1) bất biến | Số check-in thành công đúng bằng số vé hợp lệ được quét; không vé nào bị đánh dấu sai |
| Độ đo — (2) ngưỡng | Độ trễ phân vị 95 và tỷ lệ phải thử lại, đo **riêng** và so với `QS-04`. Ngưỡng `OPEN` |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Nhiều lời gọi API check-in đồng thời trên **các vé khác nhau thuộc cùng một đơn**; đo riêng và so với `QS-04`. Truy vết: `B7` §4.4, `HOT-04`, `NFR-04`, `GOV-042` |

> **Vì sao `QS-04` và `QS-05` phải tách.** `B7` §4.4 yêu cầu tách hai phép thử này. Sơ đồ `B7-04` cho biết lý do hình học: `CheckinRequest "0..*" --> "1" Issuance` và `Issuance "1" *-- "1..*" Ticket` — **mọi** yêu cầu check-in đều đi qua **cùng một root**, trong khi một root chứa nhiều vé. Nên hai lời gọi check-in trên hai vé **khác nhau** của cùng một đơn vẫn tranh chấp trên cùng root dù chúng không chia sẻ quy tắc nghiệp vụ ở cấp vé.
>
> `QS-04` đo **tính đúng đắn**; `QS-05` đo **cái giá của ranh giới aggregate đang chọn**. Nếu `QS-05` cho thấy ảnh hưởng vật chất, `B7` phải được mở lại để so sánh phương án đặt `Vé` làm root riêng — nhưng theo `B7` §4.4, **không được tách chỉ để giảm tranh chấp mà bỏ qua `INV-07`**.

### QS-06 — Giải phóng và chốt tài nguyên đúng một lần

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Tính đúng đắn khi thao tác lặp |
| Nguồn kích thích | Hết hạn theo thời gian, người mua hủy đơn, hoặc thông điệp lặp |
| Kích thích | Cùng một nguyên nhân giải phóng được kích hoạt nhiều lần cho một đơn |
| Tạo tác | Root `Đơn hàng`, `Giữ chỗ`, các root nguồn cung, `Giới hạn mua`, `Khuyến mãi`, `Lượt dùng khuyến mãi` |
| Môi trường | Đơn đang giữ chỗ, chưa có thanh toán thành công |
| Phản ứng | Nguồn cung, phần giới hạn mua và lượt khuyến mãi được trả lại **đúng một lần**; lần kích hoạt sau không tạo thêm hậu quả |
| Độ đo — (1) bất biến | Sau `k` lần kích hoạt cùng nguyên nhân (`k ≥ 2`), trạng thái tài nguyên **bằng** trạng thái sau đúng một lần. Không cộng dồn |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Phát lại lệnh hủy, hết hạn và callback. Truy vết: `INV-06`, `BIZ-090`, `BIZ-091`, `FR-29`, `NFR-02` |

### QS-07 — Thử lại phát hành vé không tạo thêm quyền tham dự

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Tính đúng đắn khi thử lại |
| Nguồn kích thích | Cơ chế thử lại nội bộ hoặc thao tác lặp |
| Kích thích | Lệnh phát hành vé cho một đơn được thực thi nhiều lần |
| Tạo tác | Root `Phát hành vé` |
| Môi trường | Đơn đã có xác nhận thu hợp lệ |
| Phản ứng | Số quyền tham dự được tạo đúng bằng số vé đã mua; gửi lại vé không sinh vé hoặc QR mới |
| Độ đo — (1) bất biến | Số vé hợp lệ của một đơn **không vượt** số vé đã mua sau bất kỳ số lần thử lại nào |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Trung bình** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Phát lại lệnh phát hành và lệnh gửi vé. Truy vết: `INV-07`, `FR-25`, `FR-32`, `NFR-02` |

### QS-08 — Kích hoạt hoàn tiền lặp hội tụ về một yêu cầu logic

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Tính đúng đắn khi thao tác lặp |
| Nguồn kích thích | Nhiều nguyên nhân hoàn hoặc thử lại nội bộ |
| Kích thích | Cùng một khoản thu bị kích hoạt hoàn nhiều lần |
| Tạo tác | Root `Yêu cầu hoàn tiền` |
| Môi trường | Khoản thu còn số tiền phải hoàn |
| Phản ứng | Kích hoạt lặp hội tụ về yêu cầu hiện có; yêu cầu đã hoàn thành không được tạo lại |
| Độ đo — (1) bất biến | Mỗi khoản thu có tối đa **một** yêu cầu hoàn logic và tối đa **một** kết quả hoàn thành công. Không hoàn một phần |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Trung bình** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Phát lại lệnh hoàn; ca kích hoạt từ hai nguyên nhân. Truy vết: `INV-08`, `BIZ-101`, `FR-34`, `NFR-02` |

### QS-09 — Lỗi sau khi đã thu tiền phục hồi về trạng thái chấp nhận được

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Khả năng phục hồi |
| Nguồn kích thích | Lỗi nội bộ hoặc thành phần dừng giữa chừng |
| Kích thích | Phát hành vé thất bại **sau khi** khoản thu đã hợp lệ |
| Tạo tác | Root `Phát hành vé`, `Xác nhận thanh toán`, `Yêu cầu hoàn tiền`, các root nguồn cung |
| Môi trường | Tiêm lỗi tại bước xuyên thành phần, sau thời điểm thu tiền |
| Phản ứng | Vé dở dang bị vô hiệu; lượt khuyến mãi trả đúng một lần; yêu cầu hoàn toàn bộ được tạo; tồn kho và giới hạn mua **chỉ** trả lại nếu sự kiện vẫn đủ điều kiện bán |
| Độ đo — (1) bất biến | Trạng thái cuối không có vé hợp lệ nào của đơn đó; có đúng một yêu cầu hoàn; không tài nguyên nào bị trả hai lần |
| Độ đo — (2) ngưỡng | Thời gian tới trạng thái ổn định ≤ `OPEN` |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Tiêm lỗi có kiểm soát. Truy vết: `HOT-03`, `BIZ-063`, `BIZ-064`, `FR-33`, `NFR-03`, `MT-2` |

### QS-10 — Hủy sự kiện lan tới nhiều đơn với lỗi từng phần

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Khả năng phục hồi |
| Nguồn kích thích | Quản trị viên xác nhận hủy sự kiện |
| Kích thích | Một sự kiện có `N` đơn đã thu tiền bị hủy; một phần các lần hoàn thất bại |
| Tạo tác | Root `Sự kiện bán vé`, `Đơn hàng`, `Phát hành vé`, `Yêu cầu hoàn tiền` |
| Môi trường | `now < eventStartAt`; có đơn ở nhiều trạng thái khác nhau |
| Phản ứng | Đóng bán; vô hiệu toàn bộ vé; không đưa nguồn cung trở lại khả dụng; các lần hoàn thất bại giữ ở trạng thái chưa hoàn tất để thử lại, **không đảo ngược** phần đã hoàn thành |
| Độ đo — (1) bất biến | Không đơn nào bị hoàn hai lần; không vé nào còn hiệu lực vào cửa; giao dịch thu thừa **không** nằm trong lô hoàn do hủy |
| Độ đo — (2) ngưỡng | Tỷ lệ đơn đạt trạng thái cuối sau một chu kỳ thử lại **≥** `OPEN`. Đây là tỷ lệ **đạt**, nên ngưỡng là cận dưới — cùng chiều với `QS-12` và `QS-13` |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Ca hủy sự kiện có tiêm lỗi trên một phần đơn. Truy vết: `HOT-01`, `BIZ-100`–`BIZ-102`, `FR-37`, `FR-38`, `NFR-03` |

> **Giới hạn phạm vi — đã xác nhận còn hiệu lực.** `BIZ-148` cấm mở rộng sang các kịch bản mạng chập chờn khi hủy sự kiện. `QS-10` giữ ở mức **lỗi từng phần của lần hoàn**, không mở thêm biến thể mạng. Cụm *"trục nghiên cứu"* trong `BIZ-148` từng được ghi `OPEN` tại `GOV-025` vì đề tài đã đổi. Ngày 2026-08-27 Lê Văn Minh chốt tại `GOV-036`: `BIZ-148` **giữ nguyên hiệu lực** — nó loại trừ việc mô hình hóa sâu các biến thể kỹ thuật của nghiệp vụ hủy sự kiện, không phụ thuộc tên đề tài. Việc cơ chế chẩn đoán phải nhận diện lỗi hạ tầng đã được phủ bởi bốn lớp lỗi ở `glossary` §6. **`QS-10` do đó không đổi một chữ.**

### QS-11 — Đáp ứng mức tải mục tiêu ở cấu hình được công bố

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Hiệu năng |
| Nguồn kích thích | Bộ sinh tải |
| Kích thích | Tải tăng dần có kiểm soát trên các luồng chính: tạo đơn, thanh toán, check-in |
| Tạo tác | Toàn hệ thống trong cấu hình triển khai được công bố |
| Môi trường | Cấu hình hạ tầng được công bố kèm dữ liệu thử |
| Phản ứng | Độ trễ và thông lượng được ghi ở **từng mức tải**; nêu rõ chi phí hiệu năng của cách phối hợp trạng thái đã chọn |
| Độ đo — (1) bất biến | Mọi bất biến ở `QS-01`–`QS-08` vẫn giữ ở mức tải cao nhất được thử |
| Độ đo — (2) ngưỡng | Độ trễ phân vị 95 và phân vị 99, thông lượng, tỷ lệ lỗi — tất cả `OPEN`, chốt sau vòng đo thử đầu tiên |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Thử tải có kiểm soát và tăng dần. **Không tuyên bố mô phỏng lưu lượng sản xuất.** Truy vết: `NFR-04`, `MT-3`, `RES-003` |

### QS-12 — Dấu vết chứa đủ tín hiệu để lần ra nguyên nhân

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Khả năng chẩn đoán |
| Nguồn kích thích | Một sự cố đã xảy ra |
| Kích thích | Cần xác định nguyên nhân của một ca lỗi đã biết đáp án |
| Tạo tác | **Dấu vết vận hành** sinh ra trong quá trình xử lý, gồm cả phần là **Log có cấu trúc** |
| Môi trường | Ca lỗi thuộc một trong bốn lớp đã định nghĩa ở `B2` §6 |
| Phản ứng | Dấu vết thu được chứa đủ tín hiệu để lần tới nguyên nhân mà **không cần** tái hiện lại lỗi |
| Độ đo — (1) bất biến | Không áp dụng — đây là phép đo tỷ lệ, không phải ràng buộc đúng/sai |
| Độ đo — (2) ngưỡng | Tỷ lệ ca mà dấu vết chứa đủ tín hiệu ≥ `OPEN`. **Điều kiện để điền được: cỡ tập ca.** Xem ghi chú dưới |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Chạy trên tập ca lỗi có nguyên nhân biết trước. Truy vết: `NFR-05`, `MT-4`, `RES-034` giới hạn 1, `B8-OPEN-01` |

> **Vì sao ngưỡng của `QS-12` để `OPEN` — và vì sao điều đó không chặn B9.** Baseline chẩn đoán hiện có **đúng một** ca lỗi thật có đáp án gốc (`INC-01`) cùng bốn ca ứng viên chưa tái hiện. Một **tỷ lệ** tính trên cỡ mẫu bằng 1 không có ý nghĩa thống kê. Theo phép thử của phiếu B9 — *"không ép mọi tiêu chí thành số nếu số đó không có ý nghĩa"* — B9 viết kịch bản và để ngưỡng `OPEN`.
>
> Ngưỡng này chờ **cỡ tập ca**, không chờ một quyết định của B9. Việc bổ sung tối thiểu hai ca, hoặc chấp nhận giới hạn một ca và bỏ kết luận về thời gian, là quyết định của Lê Văn Minh ở Giai đoạn 1 — **không phải điều kiện để mở B9**.

### QS-13 — Dựng lại trình tự một giao dịch xuyên thành phần

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Khả năng quan sát |
| Nguồn kích thích | Người vận hành hoặc người phát triển điều tra |
| Kích thích | Cần dựng lại trình tự xử lý của **một** giao dịch nghiệp vụ đi qua nhiều thành phần |
| Tạo tác | **Log có cấu trúc** và **Mã tương quan** |
| Môi trường | Giao dịch chọn ngẫu nhiên từ khoảng thời gian đã chạy |
| Phản ứng | Dựng lại được trình tự đầy đủ **chỉ từ dấu vết**, không cần đọc mã nguồn hay tái chạy |
| Độ đo — (1) bất biến | Với một giao dịch bất kỳ, mọi bản ghi thuộc về nó truy được qua **một** mã tương quan duy nhất |
| Độ đo — (2) ngưỡng | Tỷ lệ giao dịch mẫu dựng lại được trình tự đầy đủ ≥ `OPEN` |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Chọn ngẫu nhiên giao dịch mẫu và thử dựng lại. **Cách đạt được — mã tương quan, schema chung hay cách khác — thuộc `B16`.** Truy vết: `NFR-06`, `B1` chẩn đoán §1 bước 1 và 5 |

### QS-14 — Khử hoặc che trường nhạy cảm trước khi ra khỏi phạm vi kiểm soát

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Bảo mật dữ liệu |
| Nguồn kích thích | Một bước chuyển dữ liệu ra hệ thống ngoài phạm vi kiểm soát của ứng dụng |
| Kích thích | **Dấu vết đã chọn và liên kết** cho một sự cố, còn chứa trường nhạy cảm đã biết, được chuyển ra ngoài phạm vi kiểm soát |
| Tạo tác | Bước lọc/che trước khi dữ liệu rời phạm vi kiểm soát |
| Môi trường | Bộ dữ liệu kiểm thử có trường nhạy cảm đã biết trước |
| Phản ứng | Đầu ra không còn giá trị gốc, hoặc đã được che theo quy tắc |
| Độ đo — (1) bất biến | Số trường nhạy cảm rời phạm vi kiểm soát ở dạng gốc bằng **0** |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Thấp** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`); mức do Lê Văn Minh chốt tại `GOV-040` |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Bộ dữ liệu mồi có trường nhạy cảm; kiểm đầu ra. **Tập trường và vị trí lọc chốt ở `B13`/`B16`** (`B8-OPEN-05`). Truy vết: `NFR-09`, `PRJ-007`, `RES-037` |

### QS-15 — Kiểm quyền theo vai trò **và** theo quan hệ sở hữu

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | An toàn truy cập |
| Nguồn kích thích | Người dùng đã đăng nhập, đúng vai trò nhưng sai quan hệ sở hữu |
| Kích thích | Organizer thao tác trên sự kiện của organizer khác; buyer xem QR của đơn người khác |
| Tạo tác | Mọi lệnh làm đổi trạng thái và các truy vấn đọc dữ liệu riêng |
| Môi trường | Hệ thống hoạt động bình thường |
| Phản ứng | Lệnh bị từ chối; kết quả từ chối phân biệt được với lỗi hệ thống |
| Độ đo — (1) bất biến | Số thao tác chéo quyền sở hữu thành công bằng **0**. Organizer và admin **không** liệt kê hoặc tải được QR thô của buyer |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`); mức do Lê Văn Minh chốt tại `GOV-040` |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Bộ ca phân quyền chéo. Truy vết: `NFR-10`, `BIZ-135`, `BIZ-076`–`BIZ-078`, `FR-31` |

### QS-16 — Kết quả từ chối phân biệt được nguyên nhân

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Khả năng vận hành |
| Nguồn kích thích | Người mua hoặc organizer |
| Kích thích | Một thao tác bị từ chối vì một trong nhiều nguyên nhân có thể |
| Tạo tác | Các luồng áp mã khuyến mãi và check-in |
| Môi trường | Hệ thống hoạt động bình thường |
| Phản ứng | Trả đúng nguyên nhân, không gộp thành một lỗi chung |
| Độ đo — (1) bất biến | Tập lý do trả về **phủ đủ** danh sách đã liệt kê ở `FR-22` (**sáu** lý do từ chối mã) và `FR-46` (**năm** cụm lý do từ chối check-in, trong đó cụm cuối gộp ba trạng thái vé: chưa phát hành · đã hủy · đã sử dụng); không lý do nào bị gộp thêm ngoài cách gộp mà `FR-46` đã viết |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Thấp** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Đối chiếu tập lý do trả về với hai danh sách. Truy vết: `NFR-11`, `FR-22`, `FR-46` |

### QS-17 — Lỗi từng phần khi cấp vai trò organizer

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Khả năng phục hồi |
| Nguồn kích thích | Nguồn danh tính bên ngoài |
| Kích thích | Admin duyệt hồ sơ organizer, nhưng việc cấp vai trò ở nguồn danh tính thất bại hoặc phản hồi chậm |
| Tạo tác | Root `Đăng ký organizer` và nguồn danh tính |
| Môi trường | Hồ sơ đang ở `PENDING` |
| Phản ứng | Hồ sơ **không** chuyển `ACTIVE` khi chưa có xác nhận cấp vai trò; thao tác có thể thử lại mà không tạo hồ sơ trùng |
| Độ đo — (1) bất biến | **Một chiều:** không tồn tại hồ sơ ở `ACTIVE` mà tài khoản chưa có vai trò `ORGANIZER`. **Chiều ngược lại KHÔNG phải bất biến** — xem ghi chú dưới |
| Độ đo — (2) ngưỡng | Không áp dụng |
| Mức ưu tiên | **Thấp** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`); mức do Lê Văn Minh chốt tại `GOV-040` |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Tiêm lỗi ở bước cấp vai trò **và** ở bước ghi `ACTIVE`. Truy vết: `B4-OPEN-01`, `BIZ-140`, `FR-52`, `NFR-03`, `B7` §4.7 |

> **Vì sao bất biến chỉ một chiều** (sửa 2026-08-27). Bản trước ghi *"và ngược lại"*, tức cấm luôn trạng thái **đã cấp role nhưng hồ sơ chưa `ACTIVE`**. Nhưng `B7` §4.7 nói rõ đó **chính là lỗi từng phần** mà `B9`/`B10` phải mô tả: *"Trường hợp Keycloak đã cấp role nhưng việc ghi `ACTIVE` thất bại… phải được B9/B10 mô tả như lỗi từng phần"*. Viết hai chiều là **cấm đúng ca lỗi mà kịch bản này tồn tại để đo** — kịch bản tự vô hiệu hóa chính nó.
>
> Trạng thái *có role nhưng chưa `ACTIVE`* là **tạm thời và chấp nhận được**: nó an toàn theo nghiệp vụ vì `BIZ-139` không có luồng thu hồi role, và hồ sơ chưa `ACTIVE` thì chưa được công khai. Điều phải đo là **hệ thống có hội tụ về trạng thái nhất quán sau khi thử lại hay không**, không phải trạng thái trung gian có tồn tại hay không.


### QS-18 — Cơ chế chẩn đoán chạy được trên hệ thống và kết quả đến được người có quyền

| Trường | Nội dung |
|---|---|
| Thuộc tính chất lượng | Khả năng chẩn đoán · An toàn truy cập |
| Nguồn kích thích | Một sự cố xảy ra trên hệ thống đang chạy |
| Kích thích | Sự cố có dữ liệu quan sát đã được sinh ra theo `NFR-05`, `NFR-06`, `NFR-09` |
| Tạo tác | Cơ chế chẩn đoán chạy trong hệ thống, cộng đường đưa kết quả tới người sử dụng |
| Môi trường | Hệ thống chạy trên cấu hình triển khai được công bố, sau khi `B11` chốt điểm tích hợp |
| Phản ứng | Cơ chế chạy được trên dữ liệu quan sát của chính hệ thống; sinh ra danh sách nguyên nhân đã xếp hạng kèm bằng chứng và lời giải thích; kết quả **đến được người có quyền sử dụng**; **không** ghi gì vào dữ liệu nghiệp vụ |
| Độ đo — (1) bất biến | Với một ca lỗi đã dựng, cơ chế **chạy tới kết quả** thay vì dừng giữa chừng vì thiếu dữ liệu hoặc thiếu quyền. Số thao tác ghi vào dữ liệu nghiệp vụ do cơ chế thực hiện bằng **0** — đây là chỗ `NFR-08` được kiểm bằng quan sát, không chỉ bằng rà quyền tĩnh. Kết quả **truy được tới ít nhất một người có quyền**, không dừng ở log nội bộ |
| Độ đo — (2) ngưỡng | Tỷ lệ ca chạy tới kết quả ≥ `OPEN`; thời gian từ lúc có dữ liệu tới lúc kết quả sẵn sàng ≤ `OPEN`. **Điều kiện để điền được:** `B11` chốt điểm tích hợp, và `RES-039` chốt ai dùng cùng hình thức nhận kết quả |
| Mức ưu tiên | **Cao** — `B10-v0.5` §2, duyệt 2026-08-28 (`GOV-043`) |
| ADR liên quan | — (`B11`) |
| Kiểm chứng | Dựng một ca lỗi có nguyên nhân biết trước trên hệ thống đã triển khai; chạy cơ chế; kiểm ba thứ: có ra kết quả không, kết quả có tới được một tài khoản có quyền không, và nhật ký ghi của dữ liệu nghiệp vụ có trống không. Truy vết: `NFR-12`, `NFR-08`, `RES-023` mức 2, `RES-038` |

> **Vì sao kịch bản này tồn tại — và vì sao nó từng bị bỏ sót.** Bản `B9-v0.3` xếp `NFR-12` vào loại *"ràng buộc phương án kiến trúc, không mô tả một tình huống chạy"* nên không viết kịch bản. **Lập luận đó sai và tự mâu thuẫn với chính tài liệu này:** `NFR-01`–`NFR-06`, `NFR-09`–`NFR-11` đều là yêu cầu **phi chức năng** và đều có kịch bản. Việc một yêu cầu là phi chức năng **không** miễn cho nó khỏi phải có cách kiểm chứng quan sát được.
>
> *"Cơ chế chạy trên hệ thống và kết quả đến được người có quyền"* rõ ràng là **hành vi lúc chạy**: có kích thích (một sự cố), có phản ứng (ra kết quả, tới được người dùng), có thứ đo được (chạy tới kết quả hay không, có ghi nghiệp vụ hay không).
>
> **Hệ quả nếu thiếu kịch bản này:** hệ thống có thể *"đã tích hợp RCA trên giấy"* mà chưa bao giờ chứng minh được cơ chế chạy thật và kết quả dùng được — tức không đạt mức 2 của `RES-023` dù mọi tài liệu đều nói đã đạt.
>
> Hai ô ngưỡng để `OPEN` là **đúng quy tắc**, không phải né tránh: chúng chờ `B11` và `RES-039`, giống cách `QS-12` chờ cỡ tập ca.

---

## 4. Điểm `OPEN` của B9

| ID | Vấn đề | Chủ sở hữu | Gate |
|---|---|---|---|
| `B9-OPEN-01` | Toàn bộ ô ngưỡng đang `OPEN`. Chúng là **đầu ra** của B9/B10 sau một vòng đo thử, không phải blocker | Lê Văn Minh | Sau vòng đo thử đầu tiên; kế thừa `A3-OPEN-01`, `B8-OPEN-01` |
| `B9-OPEN-02` | Ngưỡng của `QS-12` chờ **cỡ tập ca lỗi**, hiện là một ca thật | Lê Văn Minh | Giai đoạn 1 — quyết định bổ sung ca hay công bố giới hạn |
| `B9-OPEN-03` | `QS-05` có thể cho thấy tranh chấp trên root `Phát hành vé` là vật chất. Nếu vậy, `B7` phải mở lại để so sánh phương án đặt `Vé` làm root riêng — **không được tách chỉ để giảm tranh chấp mà bỏ `INV-07`** | Lê Văn Minh | Sau khi chạy `QS-05`; trước `B11-A` |
| `B9-OPEN-04` | **ĐÃ ĐÓNG** ngày 2026-08-27. Chuỗi `B2-v0.12 → B3-v0.11 → B4-v0.15 → B5-v0.14 → B6-v0.14 → B7-v0.11 → B8-v0.11` đã được Lê Văn Minh duyệt lại **đúng thứ tự** (`GOV-033`), B9 duyệt cuối chuỗi. Điểm này được **trả lời**, không phải chuyển giao | Lê Văn Minh | Đã đóng tại `GOV-033` |
| `B9-OPEN-05` | **ĐÃ ĐÓNG** ngày 2026-08-27 tại `GOV-036`: `BIZ-148` giữ nguyên hiệu lực, đọc là *loại trừ mô hình hóa sâu các biến thể kỹ thuật của nghiệp vụ hủy sự kiện*. `QS-10` không đổi; tập kịch bản đem đi xếp hạng chốt ở đúng 18 mục | Lê Văn Minh | Đã đóng tại `GOV-036` |
| `B9-OPEN-06` | **Phần mâu thuẫn đã đóng** ngày 2026-08-27: `B4-v0.15` §10 bổ sung `B9` vào ô *"Gate xử lý tiếp"* của `HOT-02`, ba chỗ nay nói giống nhau. **Phần còn lại ĐÃ ĐÓNG** ngày 2026-08-28: `B10-v0.5` §3.1 viết `ASR-01` riêng cho `HOT-02`, phát biểu ranh giới cần bảo vệ chứ không mô tả một tình huống chạy, và `B10` đã được Lê Văn Minh duyệt (`GOV-043`). Món nợ *"một kịch bản không thay được một ASR"* đã trả xong | Lê Văn Minh | Đã đóng tại `GOV-043` |
| `B9-OPEN-07` | Thời gian tối đa được phép ở trạng thái trung gian *đã cấp role nhưng hồ sơ chưa `ACTIVE`* (`QS-17`) chưa có ngưỡng | Lê Văn Minh | Sau vòng đo thử đầu tiên |
| `B9-OPEN-08` | Hai ô ngưỡng của `QS-18` chờ `B11` chốt điểm tích hợp và `RES-039` chốt ai dùng kết quả cùng hình thức nhận | Lê Văn Minh | Sau `B11`; sau khi `RES-039` đóng |

---

## 5. Vì sao `NFR-07` không có kịch bản riêng

> **Mục này đã được viết lại ngày 2026-08-27** (`RES-034`, `GOV-030`). Bản trước biện minh cho việc bỏ cả `NFR-07` lẫn `NFR-08` bằng phiếu `B9` của Tầng B và một cách đọc sai một dòng quyết định — tức dùng nguồn **thẩm quyền cấp 4** để lấn qua tài liệu quy trình **cấp 3**. Lý do đó không đứng vững và đã bị gỡ.

`NFR-07` **không còn tồn tại** ở bảng yêu cầu: nó đo mức hữu ích của lời giải thích, việc của bộ tài liệu RCA (`RES-034`). Không có yêu cầu thì không có kịch bản. Cách đo nay ở `A9` §6.

**Đây là yêu cầu duy nhất không có kịch bản ở B9**, và lý do là nó đã rời khỏi bảng yêu cầu — không phải vì nó "không đo được".

`NFR-08` và `NFR-12` **đều có kịch bản**: `QS-18` ở trên đo cả hai cùng lúc. `NFR-08` được kiểm bằng ô bất biến *"số thao tác ghi vào dữ liệu nghiệp vụ bằng 0"* — quan sát lúc chạy, mạnh hơn rà quyền tĩnh. Ngoài `QS-18`, `B11` vẫn phải rà quyền khi chốt ranh giới và `B16` khi chốt nguồn dữ liệu; `B10` đưa ràng buộc chỉ-đọc vào danh sách ASR như phiếu `B10` yêu cầu.

Ba kịch bản `QS-12`, `QS-13`, `QS-14` **vẫn ở đây** vì chúng đo thứ hệ thống phải **sinh ra** — dấu vết đủ tín hiệu, dựng lại được trình tự, không rò trường nhạy cảm. Đó là yêu cầu chất lượng thật của kiến trúc, tồn tại độc lập với việc có cơ chế chẩn đoán hay không (`RES-037`).

---

## 6. Phụ lục — đối chiếu `R0` §3

`RES-015` yêu cầu mọi gate `B9`–`B16` đối chiếu mười hai ràng buộc tại `docs/research-rca/R0-boi-canh-va-rang-buoc.md` §3.

**Đây là một phụ lục đối chiếu, không phải một nguồn.** Không ràng buộc nào dưới đây sinh ra hoặc sửa đổi một kịch bản ở §3. Toàn bộ giữ `CANDIDATE`/`OPEN` vì `R0` còn `DRAFT` và cả mười hai ràng buộc chưa được xác nhận (`R0-OPEN-01`, `RES-029`).

| Ràng buộc `R0` §3 | Liên quan tới B9? | Kết quả đối chiếu |
|---|---|---|
| **11** — khả năng quan sát và khả năng chèn lỗi phải thành kịch bản chất lượng có tiêu chí quan sát được | **Nhắm thẳng B9** | `OPEN`. `QS-12` và `QS-13` đã là kịch bản khả năng quan sát, nhưng chúng được sinh từ `NFR-05`/`NFR-06` của `B8` — **không** từ `R0`. Việc có thêm một kịch bản *khả năng chèn lỗi* riêng hay không chờ `R0-OPEN-01` |
| 3 — ghi rõ số ứng viên ở hai mức | Gián tiếp | `OPEN`. Số ứng viên phụ thuộc kiến trúc, chốt ở `B11` |
| 1 — mỗi log, trace và chỉ số xác định được thành phần đã sinh ra nó | Không | Thuộc `B16` + `B11`. `RES-029` đã viết lại ràng buộc này thành **yêu cầu quan sát**; xung đột với giới hạn bộ nhớ từng ghi ở `RES-018` **không còn** (`RES-030`) |
| 2 — kế hoạch thực nghiệm ghi được thành phần bị tác động và phạm vi ảnh hưởng | Không | Thuộc `B15` |
| 4, 5, 6 — ngữ cảnh dấu vết trong phong bì thông điệp; thông tin nối trace đi qua được bước bất đồng bộ nếu kiến trúc có; một định danh service duy nhất | Không | Thuộc `B13`/`B16`. Chúng là **điều kiện kỹ thuật để đạt** `QS-13`, không phải nguồn sinh ra nó. `RES-029` đã gỡ giả định mẫu outbox khỏi ràng buộc 5 |
| 7, 9 — dấu thời gian và cửa sổ bình thường trước sự cố | Không | Thuộc `B16` |
| 8 — mỗi bất biến cốt lõi có tín hiệu quan sát được khi bị vi phạm | Gián tiếp | `CANDIDATE`. Các bất biến ở §3 đã có cách kiểm chứng riêng; việc thêm bộ đếm vi phạm là một yêu cầu mới, cần `GOV-019` |
| 10 — sequence vẽ kèm ranh giới span | Không | Thuộc `B14` |
| 12 — harness chèn lỗi và ghi nhãn nguyên nhân thật | Không | Thuộc `B15` |

**Kết quả phép thử độc lập** (`docs/project/lien-ket-rca.md` §2.2):

1. B9 thuộc **bộ hệ thống**, dải `B`, phân lớp `FORMATION`.
2. Nguồn dẫn: `B7-v0.11`, `B8-v0.11`, `A3-v0.5`, `B2-v0.12`, `B4-v0.15` §10, và các quyết định `BIZ`/`PRJ`/`RES` được dẫn tại từng kịch bản.
3. Có một nguồn thuộc bộ kia: `R0` §3.
4. Nó nằm **trong phụ lục §6 này**, mọi dòng `CANDIDATE`/`OPEN`, và **không sinh ra kịch bản nào** ở §3. **Đạt.**

---

## 7. Phép tự kiểm

Các ô dưới đây được suy lại theo nội dung cuối cùng của tài liệu, không phải theo lúc bắt đầu soạn.

- [x] Dùng bộ trường `C2` của Tầng C, đủ cả `Thuộc tính chất lượng`, `Mức ưu tiên`, `ADR liên quan` và `Kiểm chứng` — bốn trường mà ví dụ ở phiếu Tầng B không có.
- [x] **`Mức ưu tiên` đã được `B10` điền cho cả 18 bản ghi** ngày 2026-08-28, sau khi `B10-v0.5` được duyệt — **xuất xứ nay đọc là `B10-v0.7`, duyệt 2026-08-29 (`GOV-050`), giá trị không đổi; xem ghi chú đầu §3** — đúng cách bàn giao mà `README.md` §1 chốt. Đếm lại trực tiếp trên 18 ô: **13 Cao · 2 Trung bình · 3 Thấp**, khớp bảng §2 của `B10`. `ADR liên quan` vẫn để trống có chủ đích; `B11` mới điền.
- [x] **Mọi kịch bản `QS-*` nằm trong §3, không kịch bản nào lọt sang mục khác** — đã đếm: 18/18 trong §3. Ô này tồn tại vì `QS-18` từng bị chèn nhầm vào §4 ở bản `v0.5`; phép đếm `QS-*` theo tệp **không** bắt được lỗi đó, phải đếm theo mục.
- [x] **Mọi mã `B9-OPEN-*` đều là một dòng của bảng §4**, không mã nào chỉ tồn tại dưới dạng ghi chú trong thân kịch bản — đã đếm: 8/8, xếp theo thứ tự mã.
- [x] Độ đo tách hai dòng: bất biến và ngưỡng, không trộn.
- [x] Phạm vi bất biến khớp cột *"Gate xử lý tiếp"* của `B4` §10 cho bảy `INV`; `INV-02`, `INV-03`, `INV-10`, `INV-11` không vào B9.
- [x] Bốn hotspot `HOT-01`–`HOT-04` đều có kịch bản phủ, và **căn cứ định tuyến nay đồng nhất**: ô *"Gate xử lý tiếp"* của `HOT-02` ở `B4-v0.15` §10 đã bổ sung `B9`, khớp `B4-OPEN-05` (*"B7/B9/B10"*) và `B7` §6 (*"còn `OPEN` cho B9–B11"*). B9 phủ `HOT-02` bằng `QS-01`/`QS-02`. **Hệ quả cho `B10` vẫn còn:** `HOT-02` được định tuyến tới cả B9 **và** B10, nên `B10` vẫn nợ nó một ASR riêng — có kịch bản ở B9 không thay được ASR ở B10.
- [x] Hai phép thử tranh chấp check-in được **tách riêng** thành `QS-04` và `QS-05` theo `B7` §4.4, kèm lý do hình học đọc từ sơ đồ `B7-04`.
- [x] **Không kịch bản nào chỉ định service, schema hay đơn vị triển khai. Đúng một kịch bản chỉ định một cơ chế, và đó là cơ chế đã được chốt có chủ ý.** `QS-13` đòi *"một mã tương quan duy nhất"* ngay trong ô bất biến. Từ `B8-v0.12` đây là **ràng buộc đã chốt** (`RES-042`), không còn là chỗ kịch bản lấn sang thiết kế. **Ô này ở `v0.5` phát biểu quá rộng** — nó khai không kịch bản nào chỉ định cơ chế kỹ thuật, trong khi `QS-13` có; chính chỗ che đó làm xung đột ba nguồn về mã tương quan sống sót qua hai vòng rà.
- [x] Ô ngưỡng nào chưa có nghĩa thì để `OPEN` kèm **điều kiện điền được**, không ép thành số.
- [x] Không kịch bản nào mô tả **phương pháp** chẩn đoán — không đồ thị, không xếp hạng, không lớp giải thích. Ba kịch bản dấu vết chỉ đo thứ hệ thống phải **sinh ra**.
- [x] **Mọi yêu cầu phi chức năng còn tồn tại đều có kịch bản** — `NFR-01`–`NFR-06`, `NFR-08`–`NFR-12`, không trừ dòng nào. `NFR-07` đã gỡ khỏi bảng nên không còn đối tượng.
- [x] Không viện lý do *"là ràng buộc nên không cần kịch bản"* cho bất kỳ dòng nào. `B9-v0.3` từng viện lý do đó cho `NFR-08` và `NFR-12`; nó tự mâu thuẫn vì mọi `NFR` khác trong tài liệu này đều có kịch bản. Đã sửa ở `v0.5` bằng `QS-18`.
- [x] Ba kịch bản `QS-12`–`QS-14` dùng từ vựng còn tồn tại trong `glossary` §6 sau khi năm mục từ trợ lý cũ bị gỡ — đã đối chiếu từng khái niệm với **nghĩa** trong từ điển, không chỉ với tên.
- [x] Phụ lục `R0` §3 không sinh ra kịch bản nào; phép thử độc lập bốn câu đã chạy và ghi kết quả ở §6.
- [x] `BIZ-148` được tôn trọng ở `QS-10` mà không tự diễn giải lại cụm "trục nghiên cứu". Cụm đó nay đã được **Lê Văn Minh** diễn giải tại `GOV-036`, không phải agent; và kết quả là `QS-10` giữ nguyên.
- [x] Chuỗi `B2-v0.12 → B3-v0.11 → B4-v0.15 → B5-v0.14 → B6-v0.14 → B7-v0.11 → B8-v0.11` đã được Lê Văn Minh duyệt lại đúng thứ tự ngày 2026-08-27 (`GOV-033`) — điều kiện để B9 rời `DRAFT` đã thỏa.
- [x] Lê Văn Minh đã rà và duyệt `B9-v0.5` ngày 2026-08-27 (`GOV-033`), sau `B8-v0.11`; AI không tự đánh dấu thay.
- [x] **Lê Văn Minh đã rà và duyệt `B9-v0.6` ngày 2026-08-28** (`GOV-043`), **sau `B8-v0.12`** và trước `B10-v0.5` — đúng thứ tự chuỗi. AI không tự đánh dấu thay.
- [x] **Lê Văn Minh đã rà và duyệt `B9-v0.7` ngày 2026-08-29** (`GOV-050`), sau `B8-v0.12` và **trước `B10-v0.7`** — đúng thứ tự chuỗi. Tài liệu từng bị trả về `REVIEW_READY` tại `GOV-047` vì `v0.7` điền 18 ô *Mức ưu tiên* sau khi `v0.6` được duyệt; vòng duyệt lại này chữa đúng chỗ đó. AI không tự đánh dấu thay (`GOV-011`).
- [x] **`B9-v0.8` chỉ lan truyền `B7-v0.12`, không đổi phép đo nào.** `Giữ chỗ` thành aggregate root (`RES-052`) nên nó được thêm vào ô *Tạo tác* của `QS-01` và `QS-06` — hai kịch bản có `Giữ chỗ` tham gia theo `B7` §5. **Vẫn đúng 18 kịch bản** (`GOV-036`), không kịch bản nào được thêm, bớt, hay đổi ô *Độ đo*, *Phản ứng*, *Kích thích* hoặc *Mức ưu tiên*; phân bố vẫn **13 Cao · 2 Trung bình · 3 Thấp**.
- [x] **`INV-03` vẫn không thuộc B9, và điều đó không đổi sau `B7-v0.12`.** `B4` §10 định tuyến `INV-03` về `B7`; việc nó chuyển từ bất biến cục bộ của `Đơn hàng` thành bất biến xuyên `Đơn hàng`–`Giữ chỗ` **không** làm nó rơi vào phạm vi `B9`, vì phạm vi bất biến của `B9` lấy theo cột *Gate xử lý tiếp* của `B4` §10 chứ không theo việc bất biến đó cục bộ hay xuyên aggregate. `B9` không tự mở rộng phạm vi.
- [x] **Lê Văn Minh đã duyệt `B9-v0.8` ngày 2026-08-29** (`GOV-059`), **sau `B7-v0.12`** và **trước `B10-v0.8`** — đúng thứ tự chuỗi mà Tầng B §3.3 bắt buộc. AI không tự tích ô này (`GOV-011`).
- [ ] Chạy vòng đo thử đầu tiên rồi quay lại điền các ô ngưỡng `OPEN`. **Việc này không phải điều kiện duyệt** — `GOV-033` ghi rõ ngưỡng số là đầu ra của `B9`/`B10`, và `B9-OPEN-01` vẫn mở sau khi duyệt.

---

## 8. Phần dùng cho báo cáo

Bộ kịch bản này vào cuối phần Yêu cầu hoặc đầu phần Kiến trúc, và **ràng buộc phần Đánh giá**: mỗi kịch bản phải có một mục tương ứng trong kế hoạch kiểm chứng ở `B15`.

Lập luận đáng giữ: mỗi kịch bản bắt nguồn từ một bất biến hoặc một điểm nóng đã được mô hình hóa ở `B4`/`B7`, chứ không từ một danh sách thuộc tính chất lượng chung chung. Đó là điều cho phép trả lời câu hỏi *"tại sao đo cái này mà không đo cái kia"*.

Không đưa nguyên trạng mã `QS-*`, sổ `OPEN` hay trạng thái governance vào báo cáo nếu chúng không giúp giải thích một quyết định.

---

## 9. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B9-v0.8` | 2026-08-29 | **Lan truyền `B7-v0.12`; không đổi một phép đo nào.** `Giữ chỗ` được nâng thành aggregate root (`RES-052`) nên nó xuất hiện thêm ở ô *Tạo tác* của **`QS-01`** và **`QS-06`** — đúng hai kịch bản mà `B7` §5 cho `Giữ chỗ` tham gia (`INV-01` qua phép xét khả dụng, và `INV-06` qua chuyển trạng thái hội tụ). Khai lại đầu vào sang `B7-v0.12`. **Vẫn đúng 18 kịch bản, phân bố 13 · 2 · 3 không đổi, không ô *Độ đo* hay *Mức ưu tiên* nào bị chạm.** `INV-03` vẫn không thuộc `B9` vì phạm vi lấy theo cột *Gate xử lý tiếp* của `B4` §10. Tài liệu về `REVIEW_READY` theo Tầng B §3.3 vì đầu vào đổi có ý nghĩa | Lan truyền từ `B7` |
| `B9-v0.7` | 2026-08-28 | **Điền ô `Mức ưu tiên` cho cả 18 bản ghi** bằng giá trị của `B10-v0.5` đã duyệt (`GOV-043`), thi hành `B10-OPEN-13`. Phân bố: **13 Cao · 2 Trung bình · 3 Thấp** — đếm lại trực tiếp trên 18 ô sau khi điền, khớp bảng §2 của `B10`. Ba ô ghi thêm nguồn `GOV-040` vì mức của chúng do Lê Văn Minh chốt trực tiếp: `QS-14` Thấp, `QS-15` Cao, `QS-17` Thấp. **Không ô nào khác bị chạm; số dòng tệp không đổi.** Ô `ADR liên quan` vẫn để trống — `B11` mới điền | Thi hành bàn giao `B10` → `B9` |
| `B9-v0.6` | 2026-08-28 | **Hai thay đổi, không đụng bất biến nào.** (1) Khai lại đầu vào theo `B8-v0.12`: `NFR-06` nay chốt **một mã tương quan duy nhất** cho mỗi giao dịch (`RES-042`). **`QS-13` không phải sửa một chữ** — ô bất biến của nó đã đòi đúng điều đó từ `v0.1`, và chính nó là một trong ba nguồn khiến `RES-042` chọn phía *bắt buộc*. (2) Ô *Nguồn kích thích* và ô *Kiểm chứng* của `QS-04` và `QS-05` viết lại theo `GOV-042`: tranh chấp được tạo bằng **nhiều lời gọi API đồng thời**, không dựng dàn thiết bị thật. **Thứ không đổi:** `INV-09` — một vé tối đa một check-in thành công — nguyên văn; mục từ *Yêu cầu check-in* ở `B2` cho phép cùng tài khoản dùng nhiều thiết bị, **giữ nguyên**, vì bản chất vấn đề là *đồng thời* chứ không phải *thiết bị*. Tài liệu chuyển `REVIEW_READY` vì đầu vào đổi có ý nghĩa | Lan truyền đầu vào + sửa cách kiểm chứng |
| `B9-v0.5` | 2026-08-27 | **Thêm `QS-18`** cho `NFR-12` và `NFR-08`. Bản `v0.3` xếp hai dòng đó là *"ràng buộc, không phải tình huống chạy"* nên không viết kịch bản — lập luận **tự mâu thuẫn**, vì mọi `NFR` khác trong chính tài liệu này đều có kịch bản, và *"cơ chế chạy rồi kết quả tới người dùng"* là hành vi lúc chạy có kích thích, phản ứng và thứ đo được. Viết lại §5 còn đúng `NFR-07`. Hai ô ngưỡng của `QS-18` để `OPEN` kèm điều kiện điền được, mở `B9-OPEN-08`. **Vòng kiểm toán cùng ngày sửa tiếp ba lỗi của chính bản này:** `QS-18` bị đặt nhầm vào §4 thay vì §3; `B9-OPEN-07` và `B9-OPEN-08` nằm dưới dạng ghi chú trong `QS-17` thay vì thành dòng của bảng §4; bảng §4 sai thứ tự mã | Sửa lỗi tự mâu thuẫn + sửa lỗi đặt sai chỗ |
| `B9-v0.4` | 2026-08-27 | Sửa `QS-17`: bất biến đổi từ **hai chiều** sang **một chiều** — bản trước cấm đúng trạng thái *đã cấp role nhưng chưa `ACTIVE`*, mà `B7` §4.7 nói đó **chính là lỗi từng phần** kịch bản này tồn tại để đo; thêm `B9-OPEN-07` cho ngưỡng thời gian ở trạng thái trung gian. Đóng phần mâu thuẫn của `B9-OPEN-06` sau khi `B4-v0.15` §10 bổ sung `B9` vào định tuyến `HOT-02`. Suy lại các ô tự kiểm theo `B2-v0.11`–`B8-v0.11` | Sửa lỗi tự vô hiệu hóa + đồng bộ |
| `B9-v0.3` | 2026-08-27 | Viết lại §5 sau khi `NFR-07` bị gỡ khỏi bảng yêu cầu; bản trước dùng thẩm quyền cấp 4 lấn qua tài liệu quy trình cấp 3 và đọc sai một dòng quyết định (`GOV-030`). Đổi từ vựng `QS-12`/`QS-14` cho khớp `glossary` §6 sau khi năm mục từ trợ lý cũ bị gỡ. **Sửa lỗi đếm ở `QS-16`**: `FR-46` liệt kê **năm** cụm lý do, không phải sáu. Sửa ba dòng lỗi thời ở phụ lục §6 theo `RES-029`/`RES-030`. Ghi thẳng chỗ định tuyến `HOT-02` không đồng nhất, mở `B9-OPEN-06`. Khai lại đầu vào theo chuỗi mới. **Mười bảy kịch bản giữ nguyên mã và nội dung đo** | Gỡ thiết kế trợ lý cũ + sửa lỗi rà |
| `B9-v0.2` | 2026-08-26 | Khai lại đầu vào theo `B7-v0.10`/`B8-v0.10` sau vòng `RES-032`; thêm khối *Đề tài* nêu `DH-TEN` và ranh giới hai bộ tài liệu ngay đầu tệp; mở rộng `B9-OPEN-04` thành cả chuỗi `B5`→`B8`; `B9-OPEN-05` dẫn `DH-TEN` thay vì `RES-019`. **17 kịch bản không đổi một chữ** | Đồng bộ đầu vào |
| `B9-v0.1` | 2026-08-26 | Bản đầu: 17 kịch bản theo bộ trường `C2`; phạm vi bất biến lấy từ `B4` §10; tách `QS-04`/`QS-05` theo `B7` §4.4; định tuyến `NFR-07`/`NFR-08` sang `B16`/`B19`; phụ lục đối chiếu `R0` §3 không sinh nội dung; năm điểm `OPEN` | Tạo mới |
