# Liên kết giữa bộ tài liệu hệ thống và bộ tài liệu chẩn đoán

- **Phiên bản:** `LK-v0.3`
- **Trạng thái:** `DRAFT`
- **Ngày tạo:** 2026-08-25 · **Sửa:** 2026-08-29 sau đối chiếu `B9-v0.7`/`B10-v0.7` (`GOV-053`) · *lần sửa trước 2026-08-28 sau đối chiếu `B9-v0.5`/`B10-v0.3`*
- **Thuộc bộ:** tài liệu hệ thống (`docs/`)
- **Cửa đối ứng:** [`docs/research-rca/R0-boi-canh-va-rang-buoc.md`](../research-rca/R0-boi-canh-va-rang-buoc.md) §3

> **Vì sao tệp này tồn tại.** Đề tài mà giảng viên đặt là *"Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"*, và `DH-MT1` đặt chính hệ thống của nhóm vào mục tiêu đầu tiên. Hai bộ tài liệu tách nhau để làm việc cho gọn, **không phải để tách đề tài**. Tệp này là **cửa chiều Hệ thống → Nghiên cứu**; `R0` §3 là cửa chiều ngược lại.
>
> Mục đích cụ thể: tránh việc thiết kế hệ thống ở `B9`–`B16` vô tình chặn khả năng tích hợp cơ chế chẩn đoán, phát hiện ra khi đã quá muộn để sửa rẻ.

---

## 1. Bộ hệ thống cấp gì cho bộ nghiên cứu

`DH-MT1` cần một mô hình đồ thị phụ thuộc. Nguồn để dựng nó **đã có sẵn và đã được duyệt**:

| Cần cho đồ thị | Lấy từ | Trạng thái |
|---|---|---|
| Danh sách nút mức năng lực | `docs/domain/B5-bounded-context-map.md` §3 — **bảy** bounded context ứng viên | `B5-v0.14` `APPROVED` 2026-08-27 (`GOV-033`); `BC-CAND-08` đã gỡ theo `RES-034` |
| Bằng chứng cho từng cạnh | `B5` §5.1 — mỗi cạnh truy về một dòng `B4` | Như trên |
| Các tương tác và sự kiện miền | `docs/domain/B4-domain-event-map.md` | `B4-v0.15` `APPROVED` 2026-08-27 (`GOV-033`); §8.3 về trợ lý cũ đã gỡ, bốn dòng sự kiện A/B/C/D không đổi |
| Bất biến cần được bảo vệ, dùng cho lớp lỗi nghiệp vụ | `docs/domain/B7-aggregates-and-invariants.md` | `B7-v0.11` `APPROVED` 2026-08-27 (`GOV-033`); §4.8 về trợ lý cũ đã gỡ, cả 11 bất biến không đổi |
| Nút hạ tầng và hệ ngoài — cơ sở dữ liệu, cổng thanh toán, Keycloak | Chưa có; `B12` chốt quyền sở hữu dữ liệu, `B11` chốt hệ ngoài | Chưa mở |

> **Trạng thái duyệt, đọc kèm bảng trên.** Toàn chuỗi `B2`–`B9` từng trở lại `REVIEW_READY` ngày 2026-08-27 sau vòng `RES-034` — gỡ thiết kế trợ lý cũ khỏi bộ hệ thống — rồi được Lê Văn Minh duyệt lại đúng thứ tự trong cùng ngày (`GOV-033`).
>
> **Thứ đổi:** `BC-CAND-08 Chẩn đoán sự cố` bị gỡ khỏi `B5`, `§4.8` bị gỡ khỏi `B7`. Cả hai đều là **từ vựng workflow của trợ lý cũ**, không phải nút nghiệp vụ.
>
> **Thứ không đổi:** bảy context còn lại, mọi cạnh giữa chúng, toàn bộ aggregate và bất biến của vòng đời vé. Đây đúng là phần bộ RCA dùng để dựng đồ thị `DH-MT1` — nó **không mất gì**. Việc nguồn đã được duyệt không tự nâng đồ thị suy ra: đồ thị vẫn giữ `CANDIDATE` cho tới khi chính nó được duyệt.

> **Cảnh báo bắt buộc đọc.** `B5` §5 ghi rõ: *"Mũi tên không phải API, topic, sự kiện tích hợp, quyền sở hữu dữ liệu hay hướng gọi đồng bộ."* Tức **`B5` là đồ thị ngữ nghĩa, không phải đồ thị phụ thuộc vận hành.** Không được chép `B5` §5 rồi gọi đó là đồ thị của `DH-MT1`. Cách chuyển đổi đúng ghi tại bản nháp báo cáo, phần 1.

---

## 2. Bảng đối chiếu ràng buộc theo gate

Mười hai ràng buộc gốc ở `R0` §3. Bảng này chỉ **định tuyến** chúng về đúng gate và theo dõi trạng thái đối chiếu. Nội dung, lý do và hậu quả của từng ràng buộc đọc ở `R0` §3, không chép lại ở đây.

| Gate | Ràng buộc phải đối chiếu | Đã đối chiếu | Ngày | Kết quả |
|---|---|---|---|---|
| `B9` — kịch bản chất lượng | 11 | ☑ | **2026-08-29** | **Đã đóng phần từng `OPEN`.** `QS-12`, `QS-13` phủ **khả năng quan sát**. Phần **khả năng chèn lỗi** không thành kịch bản riêng theo đúng quyết định `RES-043` — nó vào `B10` làm **ràng buộc phủ định `ASR-15`**, không làm `QS`. *(Ô này ghi ngày 2026-08-27 và kết quả `OPEN` cho tới `GOV-053`; đối chiếu lại trên `B9-v0.7`.)* |
| `B10` — ưu tiên và ASR | 11 | ☑ | **2026-08-29** | **Đã đóng.** Khả năng quan sát vào được ASR — `ASR-07`, `ASR-08`, `ASR-09`. **Khả năng chèn lỗi nay có `ASR-15`** (`RES-043`, 2026-08-28), nên `B10-OPEN-06` không còn đối tượng. ⚠️ **`ASR-15` truy về `RES-023` mức 3 và nghĩa vụ của `B15`, KHÔNG truy về `R0`** — đây là **trùng khớp về che phủ**, không phải `R0` sinh ra một ASR. Ghi như vậy để không biến ô này thành cửa thứ ba. *(Ô này ghi *“không có ASR nào bảo vệ”* cho tới `GOV-053`; đối chiếu lại trên `B10-v0.7`.)* |
| `B11` — kiến trúc và triển khai | **1**, 3 | ☐ |  |  |
| `B12` — sở hữu dữ liệu và schema | **6** | ☐ |  |  |
| `B13` — hợp đồng API và sự kiện | **4**, 5, **6** | ☐ |  |  |
| `B14` — sequence các luồng | 10 | ☐ |  |  |
| `B15` — kế hoạch kiểm chứng | 2, 12 | ☐ |  |  |
| `B16` — chuẩn logging | **1**, **6**, 7, 8, 9 | ☐ |  |  |
| `B8` — yêu cầu | 8 | ☐ | | Đã `APPROVED` trước khi có ràng buộc này; đối chiếu khi có vòng sửa tiếp theo |

**In đậm = ba ràng buộc đắt nhất nếu phát hiện muộn**, đúng ba số mà `R0` §3.1 nêu — 4, 1, 6. Một ràng buộc in đậm ở nhiều dòng vì nó thuộc nhiều gate.

**Ba ràng buộc đó là** (`R0` §3.1): số 4 (ngữ cảnh dấu vết trong phong bì thông điệp), số 1 (mỗi log, dấu vết và chỉ số xác định được thành phần đã sinh ra nó), số 6 (một định danh service duy nhất).

> **Lưu ý định tuyến, `LK-v0.2`.** `R0-v0.3` viết lại ba ràng buộc 1, 2 và 5, kéo theo gate của chúng đổi: số 1 chuyển từ *`B11` triển khai* sang **`B16` + `B11`** vì nó nói về dữ liệu quan sát chứ không về cách gộp tiến trình; số 2 chuyển từ *`B11` triển khai* sang **`B15` kiểm chứng** vì nó nói về kế hoạch thực nghiệm. Bảng trên đã theo gate mới.

### 2.1 Cách dùng bảng

1. Trước khi chốt một gate, mở `R0` §3 đọc các ràng buộc được liệt kê ở dòng tương ứng.
2. Ghi kết quả vào cột **Kết quả**: giữ được, hoặc không giữ được kèm hậu quả.
3. Ràng buộc nào không giữ được thì mở một dòng `OPEN` trong sổ quyết định — **không bỏ im lặng**.
4. Đánh dấu ☑ và ghi ngày.

---

## 2.2 Phép thử độc lập — chạy trước khi đóng bất kỳ gate nào

Văn xuôi không chặn được lỗi. Bốn câu dưới đây là phép thử tối thiểu, trả lời được bằng cách đọc chính tạo tác đang làm.

| # | Câu hỏi | Cách trả lời |
|---|---|---|
| 1 | Tạo tác này thuộc bộ nào? | Bộ hệ thống (`docs/`, dải `B`) hay bộ nghiên cứu (`docs/research-rca/`) |
| 2 | Nó dẫn những nguồn nào? | Liệt kê **hết**, từ dòng *"Đầu vào và phiên bản"* và từ mọi trích dẫn trong thân bài |
| 3 | Có nguồn nào thuộc bộ kia không? | Đối chiếu danh sách ở câu 2 với đường dẫn của hai bộ |
| 4 | Nếu có — nó nằm ở đâu? | Chỉ hợp lệ khi nằm trong **mục phụ lục đối chiếu** và mọi dòng ở `CANDIDATE`/`OPEN`. Nếu nó **sinh ra** một kịch bản, yêu cầu, bất biến hay ranh giới thì đó là **vi phạm** — phải gỡ trước khi đóng gate |

**Ghi kết quả** vào phần tự kiểm của chính tạo tác đó, không ghi ở đây. Bảng §2 chỉ theo dõi trạng thái đối chiếu theo gate.

### Vì sao câu 4 là câu quan trọng nhất

`RES-015` yêu cầu mọi gate `B9`–`B16` **đối chiếu** `R0` §3. Nhưng Tầng B §3.3 chỉ cho tạo tác `FORMATION` hình thành kết luận từ *"…ràng buộc **đã được xác nhận**"*, mà 12 ràng buộc `R0` §3 đang `CANDIDATE` và chưa được xác nhận (`R0-OPEN-01`, `RES-029`).

Hai điều này chỉ tương thích khi tách **đối chiếu** khỏi **dùng làm nguồn sinh nội dung**. Câu 4 là chỗ thực thi sự tách đó.

## 3. Xung đột giữa hai bộ tài liệu

| Xung đột | Chi tiết | Ghi tại |
|---|---|---|
| — | Không còn xung đột nào ở trạng thái mở giữa hai bộ tài liệu | — |

**Xung đột đã đóng.** `LK-v0.1` ghi *"ràng buộc 1 ↔ giới hạn bộ nhớ"*: ràng buộc số 1 bản cũ đòi mỗi service một đơn vị triển khai riêng, va vào giới hạn 2 vCPU / 8 GiB mỗi máy và lo ngại đã ghi trong hồ sơ là *"sợ nhiều service thì không đủ bộ nhớ"*. Xung đột đó **do chính cách viết ràng buộc tạo ra**, không do bản chất bài toán: bộ nghiên cứu cần *quy được tín hiệu về một ứng viên*, không cần *mỗi ứng viên một tiến trình*. `R0-v0.3` viết lại ràng buộc theo đúng nhu cầu thật, nên xung đột biến mất (`RES-030`, `R0-OPEN-02` đã đóng).

Câu hỏi còn lại **không phải** tài nguyên mà là **đọc kết quả**: kiến trúc được duyệt tạo ra bao nhiêu ứng viên, và mức sàn ngẫu nhiên ứng với số đó là bao nhiêu (`R0` §2.2). Ghi tại `R0-OPEN-06`, xử lý ở `B11`.

---

## 4. Trạng thái phê duyệt của chính các ràng buộc

Mười hai ràng buộc ở `R0` §3 hiện ở mức `CANDIDATE` — do phân tích suy ra, **chưa được Lê Văn Minh xác nhận**, và **chưa phải yêu cầu hệ thống** (`R0-OPEN-01`, `RES-029`).

Theo `GOV-019`, ràng buộc nào làm phát sinh hoặc thay đổi mã, hợp đồng, schema hay hành vi ngoài phạm vi đã duyệt thì phải được trình tác động và xác nhận riêng trước khi trở thành yêu cầu.

---

## 5. Phép tự kiểm

- [x] Chỉ định tuyến ràng buộc về gate; không chép lại nội dung của `R0` §3.
- [x] Gate ở bảng §2 khớp cột **Gate** hiện tại của `R0` §3, kể cả ba dòng đã đổi gate ở `R0-v0.3`. `R0-v0.4` chỉ thêm `R0-OPEN-07`, không đổi gate nào.
- [x] Nêu rõ nguồn dựng đồ thị đã có, **và ghi đúng trạng thái duyệt hiện tại của từng nguồn** — cả `B4-v0.15`, `B5-v0.14`, `B7-v0.11` đã `APPROVED` ngày 2026-08-27 (`GOV-033`). **Việc đó không nâng cấp đồ thị.** `AGENTS.md` mục 3 buộc đồ thị suy ra giữ `CANDIDATE` **cho tới khi chính đồ thị được duyệt**, không phải cho tới khi nguồn của nó được duyệt. Nguồn đã duyệt chỉ bỏ đi một cảnh báo về tính ổn định của đầu vào; đồ thị vẫn `CANDIDATE`.
- [x] Cảnh báo `B5` là đồ thị ngữ nghĩa, không phải đồ thị vận hành.
- [x] Xung đột `LK-v0.1` nêu đã đóng ở nguồn — `R0` §3 sửa cách viết ràng buộc — chứ không đóng bằng một phán quyết ở tệp này; phần chưa biết được giữ mở tại `R0-OPEN-06`.
- [x] Ghi rõ ràng buộc còn ở `CANDIDATE`, chưa phải yêu cầu hệ thống.
- [ ] Lê Văn Minh xác nhận `R0` §3.
