# B11-A — Tập phương án kiến trúc độc lập

- Phiên bản: `B11-A-v0.5`
- Trạng thái: `APPROVED`
- Người duyệt: **Lê Văn Minh**
- Ngày duyệt: **2026-09-01**, duyệt lại `B11-A-v0.5` sau đúng hai hiệu đính lời giải thích (`GOV-084`); lượt duyệt `B11-A-v0.4` ngày 2026-08-31 giữ trong lịch sử tại `GOV-070`
- **Điểm mở còn lại sau lượt duyệt:** `B11-A-OPEN-03` được duyệt kèm ở trạng thái mở, rồi **đóng ngay sau đó** ngày 2026-08-31 (`GOV-072`) — `R15` đọc **theo phạm vi**, tập giữ nguyên **sáu** phương án `PA-1`–`PA-6`, không con số nào phải đếm lại. Việc đóng này ghi lại một quyết định của người thật về tạo tác, **không bump phiên bản**, theo tiền lệ `GOV-033`/`GOV-060`. `B11-A-OPEN-02` vẫn `OPEN`, gate `B11-C`, và **không chặn `B11-B`**
- Phân lớp: `FORMATION`
- Đầu vào và phiên bản: **hiện hành** — `docs/domain/B5-bounded-context-map.md` — `B5-v0.14`, `APPROVED` 2026-08-27; `docs/domain/B7-aggregates-and-invariants.md` — **`B7-v0.12`, `APPROVED` 2026-08-29 (`GOV-058`)**; `docs/quality-scenarios/B10-quality-priorities-and-asrs.md` — **`B10-v0.9`, `APPROVED` 2026-08-31 (`GOV-071`)**; `docs/quality-scenarios/B9-quality-scenarios.md` — **`B9-v0.8`, `APPROVED` 2026-08-29 (`GOV-059`)**; `docs/glossary.md` — `B2-v0.12`, `APPROVED` 2026-08-27, **dùng làm ràng buộc từ vựng**; `docs/domain/B4-domain-event-map.md` — `B4-v0.15`, `APPROVED` 2026-08-27, **chỉ** qua các dòng mà `B5` §5.1 và `B7` §5 đã trích
- Ràng buộc hình thành phương án áp thêm ở `v0.2`: `RES-050`, `RES-051` — `USER_CONFIRMED` 2026-08-29, ghi thành `R14` và `R15` ở §2. Điều kiện tiên quyết mà `RES-052` đặt ra đã thỏa tại `GOV-058`
- Đầu vào đổi ở `v0.4`: `docs/quality-scenarios/B10-quality-priorities-and-asrs.md` — **`B10-v0.9`, `APPROVED` 2026-08-31 (`GOV-071`)**, thay `B10-v0.8`. `v0.9` chỉ thêm root `Giữ chỗ` vào hai ô lý do của `QS-01`/`QS-06` và sửa một phép đếm; nó **không đổi mức ưu tiên, không đổi ASR nào**, và `B11-A` **không dẫn `QS-06`** một lần nào — nên việc khai lại đầu vào **không kéo theo phép đếm nào**
- Quyết định áp thêm ở `v0.3`: **`GOV-062`** — `USER_CONFIRMED` 2026-08-30, `R14` đọc **có phạm vi**, `B11-A-OPEN-01` đóng; **`RES-053`** — `CANDIDATE` 2026-08-30, hình dạng 5 ranh giới của chủ đồ án vào tập thành **`PA-6`**; **`GOV-063`**, **`GOV-064`** — hai `FACT` về lỗi nghĩa và xung đột thuật ngữ phải sửa ở vòng này
- Nguồn thẩm quyền cấp trên được dẫn: `AGENTS.md` (cấp 2); `docs/quy-trinh-lam-viec.md` PHẦN 4, PHẦN 5, PHẦN 6, GIAI ĐOẠN 4 (cấp 3); `docs/tang-b-quy-trinh-ky-thuat.md` phiếu `B11`, §3.3, §3.4 (cấp 4); `docs/tang-c-quy-uoc-trinh-bay.md` §3.1–§3.3 (cấp 4)
- ⚠️ **Đầu vào chưa `APPROVED`, dẫn gián tiếp:** `docs/research/A3-research-objectives.md` — `A3-v0.6`, `DRAFT`. `B11-A` **không dẫn `A3` trực tiếp**; nó kế thừa `ASR-06`, mà `ASR-06` đứng được trên `NFR-04` đã duyệt và chỉ dùng `MT-3` làm dẫn chứng phụ. Xem `B10-OPEN-11`
- Đi vào báo cáo: phần Thiết kế — nguyên liệu cho trường *"Các phương án đã cân nhắc"* của mỗi ADR ở `B11-C`

> **Điều kiện cách ly đã thỏa — khai riêng cho từng vòng.** `B11-A-v0.1` được soạn ở một phiên **chưa mở hồ sơ đối chiếu hiện thực và chưa đọc repository cũ**, đúng điều kiện `GOV-044` đặt ra và `B10` §8 giữ làm một ô tự kiểm chưa tích; ghi tại `GOV-056`. **`v0.2` được soạn ở một phiên sạch khác**, cũng chưa mở hồ sơ đối chiếu hiện thực và chưa đọc repository cũ. Không một ranh giới, cách gộp hay phương án nào dưới đây sinh ra từ cấu trúc hiện thực đang có.
>
> **`v0.5` cũng được hiệu đính trong một phiên `FORMATION` sạch:** không mở hồ sơ đối chiếu hiện thực, không đọc repository cũ. Vòng này chỉ sửa đúng hai lời giải thích đã được Lê Văn Minh duyệt; sáu phương án, mọi phép đếm, sơ đồ và kết luận khả thi không đổi.
>
> **`v0.3` cũng được soạn ở một phiên sạch**, cùng điều kiện: không mở hồ sơ đối chiếu hiện thực, không đọc repository cũ. Phiên này **có đọc `PRJ-009`** — dòng sổ ghi hai quan sát về repository cũ — theo yêu cầu của chủ đồ án để biết ranh giới sử dụng của nó. `PRJ-009` tự khai giới hạn: chỉ dùng ở `B11-B`, **cấm dùng làm căn cứ hình thành phương án**, và **cấm dùng làm lý do chọn hay bỏ hình dạng nào** ở `B11-A`. **Phép thử đã chạy và ghi ở §10:** không mục nào của `PA-6` — phương án duy nhất thêm ở vòng này — dẫn về dòng đó; toàn bộ dẫn về `RES-053`, `B5` §3, `B7-v0.12` §3/§5 và §3.2 của chính tài liệu này.
>
> ⚠️ **Một khác biệt giữa hai vòng đầu, khai ra thay vì để im.** Phiên soạn `v0.2` đọc sổ quyết định ở trạng thái hiện tại, trong đó `GOV-057` và `PRJ-008` — hai dòng được ghi **sau** `v0.1` — có nhắc tên vài thành phần của repository cũ. Đọc một dòng sổ không phải là mở hồ sơ đối chiếu hiện thực hay đọc repository, và `GOV-056` đã liệt kê *"sổ quyết định"* trong danh sách nguồn hợp lệ của phiên sạch. **Phép thử đã chạy:** không mục nào của `PA-5` — cách gộp, bảng luồng, bốn con số, giả định sở hữu dữ liệu — dẫn về hai dòng đó; toàn bộ dẫn về `B5` §3, `B7-v0.12` §3 và §5, `B10-v0.8`, và hai ràng buộc `USER_CONFIRMED` `R14`/`R15`.

---

## 1. Tài liệu này quyết định gì và KHÔNG quyết định gì

`B11-A` **tạo, so sánh và ghi** một tập phương án kiến trúc hình thành **chỉ từ** `B5`, `B7`, `B10` và các ràng buộc đã được xác nhận. Nó là **cổng thứ nhất trong ba cổng** của `B11`.

`B11-A` **không** quyết định:

- **phương án nào được chọn** — đó là `B11-C`, sau khi `B11-B` kiểm tính khả thi;
- cơ chế kỹ thuật: khóa, hàng đợi, giao thức, môi giới thông điệp, công nghệ lưu trữ;
- quyền sở hữu dữ liệu vật lý, schema hay bảng — `B12`;
- hợp đồng API và lược đồ sự kiện — `B13`;
- luồng nào **là** Saga và Saga đó chạy ra sao — `B11-C` chốt, `B14` vẽ;
- thành phần nào đặt trên máy nào — chỉ so sánh, chốt sau đo thử (`ASR-11`);
- chuẩn dữ liệu quan sát và cách truyền mã tương quan — `B16`;
- ai dùng kết quả chẩn đoán và bằng hình thức nào — hoãn tới sau `B11`, trước `B15` (`RES-044`, `RES-039`).

**Mọi mệnh đề trong tài liệu này ở trạng thái `CANDIDATE`**, trừ các dòng được trích nguyên văn từ một tạo tác đã `APPROVED` hoặc một quyết định `USER_CONFIRMED`/`DECIDED` — những dòng đó giữ trạng thái của nguồn.

**Bounded context không phải service.** Bảy context của `B5` là ranh giới **mô hình**; tài liệu này gộp chúng thành ranh giới **triển khai**. Hai thứ khác nhau, và `B5` §3 đã ghi rõ *"Số lượng bảy context trong bảng không phải mục tiêu bảy service"*.

---

## 2. Ràng buộc hình thành phương án

Không dòng nào dưới đây là phát minh của `B11-A`; mỗi dòng có nguồn và giữ trạng thái của nguồn. **`R1`–`R13` áp cho mọi phương án. `R14` được đọc theo phạm vi** (`GOV-062`). **`R15` cũng được đọc theo phạm vi** — Lê Văn Minh phân xử ngày 2026-08-31 (`GOV-072`), `B11-A-OPEN-03` đóng. `v0.2` đặt tiêu đề mục này là *“Ràng buộc áp cho mọi phương án”*, và tiêu đề đó tự nó là một khẳng định sai; `v0.3` sửa tiêu đề nhưng lại **thêm một khẳng định mới không có nguồn** — rằng `R15` áp cho mọi phương án — và `v0.4` gỡ nó.

| # | Ràng buộc | Nguồn | Trạng thái |
|---|---|---|---|
| R1 | Không quá **8 ranh giới nghiệp vụ**, không quá **3 Saga** — **trần để đánh giá**, không phải số mục tiêu | `ASR-10`; `quy-trinh` PHẦN 6 | `USER_CONFIRMED` |
| R2 | **Hai máy chủ đã chốt**; cách bố trí **chưa**, chỉ quyết sau so sánh và đo thử | `ASR-11` | `USER_CONFIRMED` |
| R3 | Mọi thành phần quan sát hoặc chẩn đoán **chỉ được đọc**, thực thi bằng **phân quyền thật, không phải quy ước** | `ASR-12`, `NFR-08` | `USER_CONFIRMED` |
| R4 | **Không giả định** một cách chia sở hữu dữ liệu nào đã được quyết; mỗi phương án phải **khai giả định** để `B12` kiểm lại | `ASR-13` | ràng buộc phủ định |
| R5 | **Kỷ luật dữ liệu đã chốt:** chia dữ liệu theo ranh giới, mỗi đơn vị sở hữu phần của mình; giữa các ranh giới **không** khóa ngoại, `JOIN`, repository hay truy vấn trực tiếp; liên kết ngoài miền là ID mềm | `GOV-045`; `boi-canh` §5 dòng 9 | `USER_CONFIRMED` |
| R6 | Phương án **không được làm việc chèn lỗi có kiểm soát trở nên bất khả thi**; phải nêu chèn được **ở đâu, ở mức nào** | `ASR-15`, `RES-043` | ràng buộc phủ định |
| R7 | Mọi bản ghi thuộc cùng một giao dịch truy được qua **một mã tương quan duy nhất**, đi qua **mọi** bước chuyển kể cả bất đồng bộ | `ASR-08`, `NFR-06`, `RES-042` | `USER_CONFIRMED` |
| R8 | **Không có vòng lặp phụ thuộc đồng bộ** (A gọi B, B gọi A) | `quy-trinh` GIAI ĐOẠN 4 | cấp 3 |
| R9 | **Không coi ranh giới `Phát hành vé` là đã chốt**; mỗi phương án nêu **phải sửa gì nếu nó đổi** | `GOV-049` | `DECIDED` |
| R10 | **Không được tạo phương án làm trái một yêu cầu đã duyệt** — kể cả kịch bản mức `Thấp` | `GOV-035` | `USER_CONFIRMED` |
| R11 | Frontend tái sử dụng **không** ràng buộc cách tách ranh giới; người dùng không cần biết backend chia thế nào | `PRJ-004`, `PRJ-005` | `USER_CONFIRMED` |
| R12 | Chatbot hỗ trợ mua vé là **kênh**, không phải một ranh giới nghiệp vụ | `B5` §7; `RES-036` | `FACT` / `USER_CONFIRMED` |
| R13 | Phương án gộp toàn bộ vào **một** ranh giới **không thuộc tập lựa chọn**, chỉ là đường cơ sở chi phí | `GOV-055` | `USER_CONFIRMED` |
| R14 | **`Giới hạn mua`, `Khuyến mãi` theo nghĩa tổng lượt và `Lượt dùng khuyến mãi` gộp cùng ranh giới với nguồn cung và giữ chỗ**, không gộp cùng ranh giới với `Đơn hàng` — ràng buộc này **có phạm vi**, xem ghi chú dưới bảng | `RES-050` | `USER_CONFIRMED` |
| R15 | **Đối soát và chi trả đi cùng ranh giới với thanh toán và hoàn tiền**; **mục tiêu hình dạng là 7 ranh giới nghiệp vụ** — mục tiêu để dựng một phương án, **không** phải kiến trúc đã chốt | `RES-051` | `USER_CONFIRMED` |

> **Về `R1` — phạm vi trần 8.** Lê Văn Minh đã quyết định tại `GOV-079`: **trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán được đếm riêng về tài nguyên vận hành.** Vì vậy mục 7 của từng phương án không cộng chatbot hay cơ chế chẩn đoán vào số ranh giới nghiệp vụ; cách triển khai cụ thể của cơ chế chẩn đoán vẫn chưa được chốt, như cảnh báo ngay dưới đây.

> ⚠️ **Về `R1` — *“một đơn vị triển khai riêng”* cho cơ chế chẩn đoán là `CANDIDATE`, không phải một quyết định.** Mục 7 của cả sáu phương án ghi giống hệt nhau, nên **không quyết định nào trong tập buộc phải như vậy** và lời khai này không phân biệt phương án. Lựa chọn thay thế đã cân nhắc: một **thành phần đồng vị trí** với một ranh giới nghiệp vụ, vẫn giữ **credential chỉ đọc riêng trên kho dữ liệu quan sát** và vẫn không chạm schema nghiệp vụ — tức `ASR-12`/`NFR-08` **không** ép phải có tiến trình riêng, chúng chỉ ép **quyền**. **Vì sao vẫn phải ghi ra:** một tiến trình riêng **tiêu tài nguyên trên hai máy 2 vCPU / 8 GiB**, nên nó chạm thẳng `ASR-11` — đúng dòng đang phân biệt `PA-3`/`PA-5` với phần còn lại. Chốt ở `B11-C` sau đo thử; `B11-A` chỉ khai giả định và giá của nó.

> **Về `R11` — ghi thành lời theo yêu cầu của chính `R11`.** Tập phương án dưới đây được hình thành từ `B5` §3, `B7` §3/§5 và `B10` §3. **Không** cách gộp nào được chọn vì nó khớp một bề mặt giao diện đang có, và **không** phương án nào bị loại vì nó buộc frontend phải sửa.

> **Về `R14` và `R15` — hai ràng buộc mới ở `v0.2`, và ba điều chúng KHÔNG nói.**
>
> 1. **Chúng không chọn kiến trúc.** Cả hai dòng sổ tự khai *"ràng buộc lên cách hình thành phương án ở `B11-A`, KHÔNG phải quyết định kiến trúc"*, và *"con số 7 là mục tiêu hình dạng"*. Việc chọn vẫn thuộc `B11-C` sau `B11-B` (`GOV-018`). `PA-5` vì vậy vào tập so sánh **ngang hàng** với `PA-1`–`PA-4`, không được ưu tiên.
> 2. **`R14` chép nguyên văn dòng sổ, và `GOV-062` đã chốt rằng nó được đọc theo PHẠM VI.** `v0.2` viết ghi chú này bằng một lập luận **sai**, phải thay hẳn chứ không sửa chữ: nó nói *"`PA-1`, `PA-2`, `PA-3` thỏa `R14` một cách hiển nhiên"*. Ba phương án đó **không thỏa** `R14` — cả ba đặt `Giới hạn mua`, `Khuyến mãi` theo nghĩa tổng lượt và `Lượt dùng khuyến mãi` **chung ranh giới với `Đơn hàng`**, đúng thứ vế sau của `R14` cấm.
>
>    **Cách đọc đã được chốt, và lý do buộc phải đọc như vậy.** Nếu `R14` đọc **không điều kiện** thì `PA-1`, `PA-2`, `PA-3` **và** `PA-4` đều vi phạm; chỉ `PA-5` thoả. Cách đọc tuyệt đối vì vậy xoá **bốn trên năm** phương án có mặt lúc `GOV-062` được chốt — và **năm trên sáu** ở tập hiện tại, vì `PA-6` cũng không thoả — tức biến `B11-A` thành tài liệu một phương án, phá chính mục đích của cổng thứ nhất. Lê Văn Minh chọn giữ `PA-4` trong tập (`GOV-062`, `USER_CONFIRMED` 2026-08-30), tức `R14` là ràng buộc lên **hình dạng đang được thêm vào** ở vòng `v0.2`, không phải lệnh áp cho mọi phương án.
>
>    **Hệ quả đọc theo từng hình dạng:**
>    - **`PA-5` được dựng để thi hành `R14`** — nó là hình dạng duy nhất trong tập mà `R14` nói ra điều gì đó không hiển nhiên, và nó thoả.
>    - **`PA-1`, `PA-2`, `PA-3`, `PA-4`, `PA-6` không thoả `R14` đọc tuyệt đối**, và **vẫn ở trong tập** theo cách đọc có phạm vi. `PA-4` là trường hợp gắt nhất — nó đặt nguồn cung ở `RG-1a` còn giữ chỗ ở `RG-1b` nên **không có ranh giới nào ứng được** với cụm *"cùng ranh giới với nguồn cung **và** giữ chỗ"*, dù có gộp ba root về phía nào.
>    - **`PA-6` (`RES-053`) chỉ hợp lệ dưới đúng cách đọc này**, và chủ đồ án tự khai điều đó khi đề xuất.
> 3. **Chúng không mang theo tên service nào.** Hai lựa chọn được trình cho Lê Văn Minh mang nhãn dạng `reservation_service` / `payment_service`; `B11-A` **cố ý không nhập hai nhãn đó**, vì Tầng C §3.2.1 buộc nhãn chính là tên nghiệp vụ tiếng Việt và vì một tên dạng `*_service` gợi sẵn một đơn vị triển khai mà `B11-C` mới được chốt. Thứ được nhập là **nội dung cách gộp**, và nội dung đó truy về `B7-v0.12` §3 và §5.
>
> ⚠️ **Điều kiện tiên quyết để dựng một ranh giới giữ chỗ riêng, kiểm trước khi dùng.** Một ranh giới giữ chỗ riêng chỉ hợp lệ khi `Giữ chỗ` là aggregate root — nếu không, ranh giới ấy **cắt ngang một aggregate**. `RES-052` đặt đúng điều kiện đó và `GOV-058` đã đóng nó: `B7-v0.12` `APPROVED` ngày 2026-08-29. Ledger §3.1 và §3.3 dưới đây đã lan truyền xong.

---

## 3. Ledger bằng chứng

Mục này chạy **trước** khi bất kỳ phương án nào được viết, để tập phương án không trở thành thứ được nghĩ ra trước rồi tìm bằng chứng hợp thức hóa sau — đúng lỗi `GOV-012` sinh ra để chặn.

### 3.1 Bảy năng lực và các root bảo vệ chúng

| Context `B5` §3 | Phân loại | Aggregate root ứng viên (`B7` §3) |
|---|---|---|
| `BC-CAND-01` Vòng đời sự kiện và cấu hình bán | Hỗ trợ | `Sự kiện bán vé`, `Yêu cầu hủy sự kiện`, `Địa điểm`, `Phân loại sự kiện`, `Sector`, `Loại vé`, `Khuyến mãi` (cấu hình) |
| `BC-CAND-02` Mua vé và cam kết nguồn cung | **Cốt lõi** | `Đơn hàng`, **`Giữ chỗ`**, `Ghế`/`Loại vé`/`Sector` (nguồn cung), `Giới hạn mua`, `Khuyến mãi` (sử dụng), `Lượt dùng khuyến mãi` |
| `BC-CAND-03` Thanh toán và hoàn tiền | **Cốt lõi** | `Xác nhận thanh toán`, `Yêu cầu hoàn tiền` |
| `BC-CAND-04` Quyền tham dự và kiểm soát vào cửa | **Cốt lõi** | `Phát hành vé` |
| `BC-CAND-05` Đối soát và chi trả | Hỗ trợ | `Hồ sơ chi trả sự kiện` |
| `BC-CAND-06` Giao nhận thông tin vé | Chung | `Gửi vé` |
| `BC-CAND-07` Hồ sơ tài khoản và quyền nghiệp vụ | Hỗ trợ | `Hồ sơ nghiệp vụ ứng dụng`, `Đăng ký organizer`, `Theo dõi organizer` |

> **Sửa ở `v0.2`, và phát biểu lại cho đếm được ở `v0.3` — `BC-CAND-02` nay có sáu nhóm root ứng viên, tức tám tên root.** `B7-v0.12` nâng `Giữ chỗ` từ thành phần bên trong `Đơn hàng` thành **aggregate root riêng** (`RES-052`, `GOV-058`). Chỉ **một** dòng của bảng đổi; sáu context còn lại và toàn bộ phân loại Cốt lõi/Hỗ trợ/Chung giữ nguyên.
>
> **Quy tắc đếm root, viết ra vì `v0.2` đếm hai kiểu trong cùng một tài liệu.** Một **nhóm root** là một mục ngăn bằng dấu phẩy trong ô *Aggregate root ứng viên*; nhóm nguồn cung `Ghế`/`Loại vé`/`Sector` là **một nhóm mang ba tên**, đúng cách `B7` §4.2 trình bày ba root cùng loại. Theo quy tắc đó, `BC-CAND-02` có **sáu nhóm / tám tên**, và mọi con số root trong tài liệu này đếm theo **nhóm**, có ghi kèm số tên khi cần.
>
> **Các tên xuất hiện ở hai nghĩa là có chủ ý, không phải trùng lặp.** `Sector` và `Loại vé` là root **cấu hình** ở `BC-CAND-01` và là root **cam kết nguồn cung** ở `BC-CAND-02`; `Khuyến mãi` là root **cấu hình** ở `BC-CAND-01` và là root **tổng lượt đang giữ/đã chốt** ở `BC-CAND-02`. `B7` §3 tách bạch hai nghĩa bằng hai dòng riêng với hai phạm vi nhất quán khác nhau. **Hệ quả cho `B11-A`:** một phương án đặt toàn bộ hoặc một phần hai nghĩa của `BC-01` và `BC-02` ở hai ranh giới **không** cắt ngang một aggregate — nó sinh **điểm sao chép và nghĩa vụ phân xử một nguồn ghi ở `B12`**. Điều này áp theo đường cắt thực tế của `PA-1`, `PA-3`, `PA-4`, `PA-5` và `PA-6`; `PA-2` giữ hai context trong cùng một ranh giới.

### 3.2 Mười hai quan hệ xuyên context — nguyên liệu để đếm luồng

Chép **đúng** bảng bằng chứng `B5` §5.1. Không thêm cạnh, không suy ra cạnh.

> **Quy ước hai nghĩa của chữ *“sự kiện”*, ghi ra vì `B2` §1 bắt phân biệt.** `B2-v0.12` §1 ra lệnh: *“Dùng **“sự kiện bán vé”** cho đối tượng được tổ chức và bán vé; **chỉ** dùng **“sự kiện miền”** cho một việc nghiệp vụ đã xảy ra”*. Trong tài liệu này: **`Sự kiện bán vé`** là đối tượng — nghĩa ở `E5` *“Sự kiện bị hủy”*, ở root `Sự kiện bán vé`, và ở tên ranh giới *“Sự kiện và cấu hình bán”*; **`sự kiện miền`** là thông điệp bất đồng bộ — nghĩa ở mọi câu dạng *“một chiều đi bằng sự kiện miền”* và ở ký pháp nét đứt của sáu sơ đồ container. **Các dòng chép nguyên văn từ `B5` §5.1 và `B7` giữ đúng câu chữ nguồn**, kể cả khi nguồn viết chữ trần. `v0.3` dùng chữ trần cho cả hai nghĩa ở chín chỗ và **không dùng cụm `sự kiện miền` một lần nào**.

| # | Từ → Đến | Nội dung ngữ nghĩa |
|---|---|---|
| `E1` | `07` → `01,02,03,04,05` | Định danh tài khoản và điều kiện được phép phát lệnh |
| `E2` | `01` → `02` | Điều kiện bán, cấu hình thương mại, khuyến mãi đã cấu hình |
| `E3` | `01` → `04` | Cửa sổ check-in; vé phải thuộc đúng sự kiện đang quét |
| `E4` | `01` → `05` | Sự kiện đã kết thúc; tỷ lệ phí nhập khi phê duyệt |
| `E5` | `01` → `02,03,04,05` | Sự kiện bị hủy |
| `E6` | `02` → `03` | Nghĩa vụ thanh toán của đơn còn hiệu lực |
| `E7` | `03` → `02` | Thu hợp lệ làm tài nguyên đang giữ được chốt |
| `E8` | `03` → `04` | Xác nhận thu cho phép phát hành; nguyên nhân hoàn quyết hậu quả lên quyền vào cửa |
| `E9` | `03` → `05` | Khoản thu hợp lệ và kết quả hoàn là đầu vào sổ cái |
| `E10` | `04` → `03` | Phát hành thất bại sau thu tiền → yêu cầu hoàn toàn bộ |
| `E11` | `04` → `02` | Phát hành thất bại trả lượt khuyến mãi; trả tồn kho **có điều kiện** |
| `E12` | `04` → `06` | Vé đã phát hành cần được gửi tới buyer |

### 3.3 Định tuyến mười một bất biến

Chép đúng `B7` §5. Cột cuối quyết định một cách gộp giữ được bao nhiêu bất biến **cục bộ**.

| Bất biến | Root chịu trách nhiệm | Context chứa root |
|---|---|---|
| `INV-01` | `Ghế`, `Loại vé`, `Sector` (nguồn cung) | `02` |
| `INV-02` | `Đơn hàng` | `02` |
| `INV-03` | `Giữ chỗ`; `Đơn hàng` giữ tham chiếu; chốt/trả phối hợp root nguồn cung | `02` |
| `INV-04` | `Giới hạn mua` | `02` |
| `INV-05` | `Xác nhận thanh toán` | `03` |
| `INV-06` | `Đơn hàng`, **`Giữ chỗ`**, root nguồn cung, `Giới hạn mua`, `Khuyến mãi`, `Lượt dùng khuyến mãi` | `02` |
| `INV-07` | `Phát hành vé`; `Gửi vé` chỉ tham chiếu | `04` (+`06` tham chiếu) |
| `INV-08` | `Yêu cầu hoàn tiền` | `03` |
| `INV-09` | `Phát hành vé` | `04` |
| `INV-10` | `Hồ sơ chi trả sự kiện` | `05` |
| `INV-11` | `Sự kiện bán vé` **đặt** · `Hồ sơ chi trả sự kiện` **tiêu thụ** | `01` **và** `05` |

**Sửa ở `v0.2`, hai dòng.** `B7-v0.12` chuyển root chịu trách nhiệm của `INV-03` từ `Đơn hàng` sang **`Giữ chỗ`**, và thêm `Giữ chỗ` vào tập root của `INV-06`. Cả hai vẫn nằm trong context `02`, nên **cột cuối không đổi ở dòng nào** và bốn phương án `PA-1`–`PA-4` **không phải đếm lại** — mọi cách gộp của chúng để `Đơn hàng` và `Giữ chỗ` ở cùng một ranh giới. Cái mà thay đổi này mở ra là một cách gộp **mới**: tách `Giữ chỗ` khỏi `Đơn hàng` mà không cắt ngang aggregate. `PA-5` ở §4.5 dùng đúng chỗ mở đó.

**Đọc thẳng ra từ bảng:** `INV-11` là bất biến **duy nhất** mà `B7` gán cho **hai** context. Một cách gộp đặt `01` và `05` cùng ranh giới giữ được **cả 11** bất biến cục bộ; mọi cách gộp tách chúng đều để `INV-11` xuyên ranh giới.

**Đọc thứ hai, chỉ dùng được từ `v0.2`.** `INV-06` là bất biến có **tập root lớn nhất** — sáu root, trong đó `Đơn hàng` là một. Vì vậy **mọi** cách gộp tách `Đơn hàng` khỏi năm root còn lại đều để `INV-06` xuyên ranh giới, **kể cả** cách gộp mà `R14` chỉ định. Đây là chỗ ledger phải nói trước khi phương án được viết; xem kết quả 4 ở §3.5.

### 3.4 Bốn nhóm phân loại luồng — định nghĩa dùng chung

`AGENTS.md` cấm đồng nhất một giao dịch nghiệp vụ xuyên ranh giới với một Saga. Vì vậy mỗi luồng được phân loại theo **thứ nó đòi hỏi**, không theo công nghệ hiện thực.

| Nhóm | Định nghĩa | Tính vào trần 3? |
|---|---|---|
| **`N1` — giao dịch cục bộ** | Toàn bộ thay đổi nằm trong một ranh giới | Không |
| **`N2` — đọc/gọi idempotent** | Bên kia chỉ **đọc** một dữ kiện, hoặc nhận một lệnh hội tụ; không có gì phải hoàn tác | Không |
| **`N3` — phản ứng một chiều, thử lại tiến** | Có đổi trạng thái ở bên nhận, nhưng khi lỗi từng phần thì **giữ phần đã xong và thử lại**, **không đảo ngược** | Không |
| **`N4` — ứng viên Saga** | Khi một bước thất bại, **phải hoàn tác** phần đã làm ở một ranh giới **khác** | **Có** |

> **Đọc `N1` cho đúng — bốn nhóm này nói về vị trí thay đổi, không nói về phạm vi giao dịch.** Xếp một luồng vào `N1` nghĩa là nó **không buộc phối hợp xuyên ranh giới**; nó **không** khẳng định rằng mọi thay đổi bên trong ranh giới ấy chạy trong một giao dịch nguyên tử. Phạm vi giao dịch, khóa và giao thức phối hợp thuộc `B12`/`B13`, và `B7` §2 đã cấm che giấu thay đổi vượt ranh giới *“dưới tên ‘giao dịch cục bộ’”*. Mọi ô `Đạt` của `ASR-01` ở §6 phải đọc theo đúng giới hạn này.

> **Quy ước đếm, dùng thống nhất cho cả sáu phương án.** Một **nhóm luồng** là **(a)** một dòng `E1`–`E12` của §3.2, tính là *xuyên ranh giới* khi ít nhất một cặp gửi–nhận của nó nằm ở hai ranh giới khác nhau; **hoặc (b)** một quan hệ **sinh ra do chính phương án cắt bên trong một bounded context**, khi quan hệ ấy nối hai ranh giới của phương án đó.

> **Vì sao vế (b) phải có, và vì sao `v0.3` thiếu nó.** `B5` §5.1 chỉ ghi cạnh **giữa** các context, nên một phương án cắt **bên trong** `BC-02` sinh ra quan hệ mà **không dòng `E*` nào** mô tả. Đúng hai quan hệ như vậy trong tập: *luồng giữ chỗ phân tán* của `PA-4` và *vòng đời giữ chỗ* của `PA-5`. `v0.3` định nghĩa nhóm luồng **chỉ bằng vế (a)** rồi vẫn đếm cả hai quan hệ đó, tức hai con số `9` và `10` tựa vào một luật chưa viết ra. Đọc đúng chữ của `v0.3` thì `PA-4` = 8 và `PA-5` = 9. Vế (b) **không đổi con số nào** — nó viết ra cái luật vốn đã được dùng. **Chuỗi bù trừ `E8`+`E10`+`E11` đếm là MỘT luồng giao dịch**, vì đó là một giao dịch nghiệp vụ chứ không phải ba. **Số hợp đồng** đếm theo **cặp chiều** giữa hai ranh giới có trao đổi.
>
> **Điểm sao chép — định nghĩa dùng chung, `v0.3` nâng lên đây vì `v0.2` để nó nằm trong `PA-5` rồi áp không đều.** Một *điểm sao chép* là chỗ ranh giới nhận phải **giữ một bản sao** dữ liệu do ranh giới khác sở hữu. Một **lệnh mang theo giá trị** không tính — nên `E6`, `E7`, `E8`, `E10`, `E11` **không** sinh điểm sao chép ở bất kỳ phương án nào. **`E12` thì có**, và đó là chỗ định nghĩa dễ đọc nhầm nhất: ranh giới nhận phải **giữ lại** dữ kiện vé đủ để gửi và gửi lại về sau (mục từ `Gửi vé`: *“nếu thất bại, vé vẫn hợp lệ và có thể tải/gửi lại”*), nên nó là bản sao chứ không phải một lệnh dùng xong là hết. Nếu loại `E12` thì `PA-3` = 5 và `PA-5` = 6; **thứ tự giữa các phương án không đổi**. Bản sao *định danh và bộ role* theo `E1` đếm **một** điểm dù nó rót vào mọi ranh giới. Ví dụ đếm đầy đủ ở §4.5 mục (5).
>
> **Bất biến kiểm được, áp cho cả sáu phương án.** `số cặp chiều = số cặp không hướng có trao đổi + số cặp hai chiều`. Kết quả: `PA-1` 3+1=4 · `PA-2` 6+3=9 · `PA-3` 14+2=16 · `PA-4` 10+3=13 · `PA-5` 15+3=18 · `PA-6` 10+2=12.

> **Vì sao `N3` tồn tại và vì sao nó không phải Saga.** `ASR-05` phát biểu nguyên văn: một thao tác lan tới nhiều đơn với lỗi từng phần *"phải giữ phần thất bại ở trạng thái chưa hoàn tất để thử lại, và **không đảo ngược** phần đã hoàn"*. `B9` `QS-10` ô *Phản ứng* nói y hệt. Một luồng đã bị **cấm đảo ngược** thì không thể là luồng cần bù trừ. Xếp nó vào Saga là đếm thừa và trái chính yêu cầu đã duyệt.

### 3.5 Bốn kết quả của ledger

1. **`INV-11` là bất biến duy nhất nhạy với cách gộp `01`/`05`** (§3.3).
2. **Luồng hủy sự kiện `E5` thuộc `N3`, không phải `N4`** (§3.4) → **không** tính vào trần 3 Saga ở bất kỳ phương án nào.
3. **Chuỗi `E8`+`E10`+`E11` thuộc `N4` khi nó xuyên ranh giới.** `B4` `B12` đòi *"vô hiệu vé dở dang, trả lượt khuyến mãi đúng một lần, tạo yêu cầu hoàn toàn bộ"* — đó là hoàn tác phần đã làm.
4. **Cách gộp mà `R14` chỉ định KHÔNG làm `INV-06` cục bộ.** Đây là kết quả `v0.2` phải ghi ra, vì nó **lệch với một câu trong phần lý do** được trình kèm `RES-050` — câu đó nói đặt ba root tranh chấp cùng nguồn cung và giữ chỗ làm *"`INV-01`, `INV-04` và `INV-06` đều cục bộ trong một ranh giới"*. Đọc thẳng `B7-v0.12` §5: `INV-06` có **sáu** root và `Đơn hàng` là một trong sáu, nên khi `Đơn hàng` đứng riêng thì `INV-06` **vẫn xuyên ranh giới**.

   **Ba điều cần phân biệt cho đúng ở đây.** (a) **Quyết định không sai.** `RES-050` chốt **vị trí ba root**, và vị trí đó vẫn làm `INV-01` và `INV-04` cục bộ, vẫn gom **năm trên sáu** root của `INV-06` vào một ranh giới thay vì chia chúng ra hai nơi. (b) **Lời khai kèm theo thì quá mạnh ở đúng một vế** — vế `INV-06`. (c) **Ledger sửa lời khai, không sửa quyết định**; `AGENTS.md` giữ nguyên văn dòng sổ và ghi đính chính ở nơi dùng nó. `PA-5` §4.5 mục (2) vì vậy đếm `INV-06` là **xuyên ranh giới**, và mục (1) ghi rõ lợi ích thật của `R14` là gì.

**Ledger không bác hình dạng nào.** Sáu hình dạng đều truy được về `B5` §3 và giữ khác biệt thật về hệ quả, nên cả sáu đi tiếp.

> **Bốn lời khai đã bị ledger sửa**, ghi lại để không ai đọc theo bản cũ: *"phương án gộp lõi giao dịch giữ 9/11 bất biến"* → thực tế **11/11** (`v0.1`); *"luồng hủy sự kiện là một Saga"* → thực tế thuộc `N3`, không tính vào trần (`v0.1`); *"gộp ba root tranh chấp cùng nguồn cung làm `INV-06` cục bộ"* → thực tế `INV-06` **vẫn xuyên ranh giới** khi `Đơn hàng` đứng riêng (`v0.2`, kết quả 4 ở trên); và *"`BC-CAND-02` có bảy root"* → thực tế **sáu nhóm / tám tên**, xem quy tắc đếm ở §3.1 (`v0.3`).

---

## 4. Tập phương án

Sáu phương án dưới đây là **tập cần người thật duyệt**. Đường cơ sở `PA-0` ở §5 **không** thuộc tập này (`GOV-055`).

**`PA-6` vào tập ở `v0.3` theo `RES-053`** — hình dạng 5 ranh giới do chủ đồ án đề xuất ngày 2026-08-30, ở trạng thái **`CANDIDATE` do chính chủ đồ án tự khai**, vào tập để **so sánh ngang hàng**, **không** phải phương án được chọn. `GOV-018` vẫn buộc việc chọn diễn ra ở `B11-C` sau `B11-B`.

Mỗi phương án dùng cùng một bộ 12 mục. Tên ranh giới là **tiếng Việt nghiệp vụ**; mã `RG-*` chỉ để truy vết (Tầng C §3.2.1).

### 4.1 `PA-1` — Lõi giao dịch gộp · 3 ranh giới

**(1) Cách gộp và sáu phép thử `quy-trinh` §4.2**

| Ranh giới | Năng lực gộp |
|---|---|
| `RG-1` **Lõi giao dịch vé** | `BC-02`, `BC-03`, `BC-04`, `BC-06` |
| `RG-2` **Sự kiện, cấu hình bán và đối soát** | `BC-01`, `BC-05` |
| `RG-3` **Tài khoản và quyền nghiệp vụ** | `BC-07` |

Đường cắt `RG-1` | `RG-2`:

| Phép thử | Tín hiệu ủng hộ gộp | Tín hiệu ủng hộ tách | Chi phí phát sinh (cột *Trượt thì sao*) | Bằng chứng |
|---|---|---|---|---|
| 1 Bất biến | `E7`, `E8`, `E10`, `E11` đều nằm trong `RG-1` | `E2`, `E3` chỉ là đọc điều kiện | — | `B5` §5.1; `B7` §5 |
| 2 Triển khai độc lập | — | Đổi cấu hình bán không buộc triển khai lại lõi | Hai đơn vị phải chốt hợp đồng đọc | `B5` §5.1 `E2` |
| 3 Dữ liệu độc lập | — | — | `RG-1` phải giữ bản sao điều kiện bán, cửa sổ check-in, sở hữu sự kiện | `B5` §5.1 `E2`, `E3` |
| 4 Chu kỳ thay đổi | — | Cấu hình bán đổi theo tính năng; quy tắc tiền/vé đổi theo chính sách | Lý do tách mạnh | `B5` §3 |
| 5 Chu kỳ tải | — | `RG-2` tải thấp và đều; `RG-1` có hai đỉnh | Lý do tách mạnh | `B9` `QS-01`, `QS-04` môi trường |
| 6 Chủ sở hữu quyết định | — | Organizer/admin quyết cấu hình; quy tắc giao dịch do nền tảng | Lý do tách mạnh | `B2` §2 |

Đường cắt gộp `01`+`05` trong `RG-2` — **đây là chỗ đánh đổi có ý thức**:

| Phép thử | Tín hiệu ủng hộ gộp | Tín hiệu ủng hộ tách | Chi phí chấp nhận |
|---|---|---|---|
| 1 Bất biến | `INV-11` có **cả hai đầu** trong cùng ranh giới | — | — |
| 4 Chu kỳ thay đổi | — | Cấu hình sự kiện đổi theo tính năng; đối soát đổi theo chính sách tài chính | `RG-2` mang hai nhịp thay đổi khác nhau |
| 6 Chủ sở hữu quyết định | Admin có mặt ở cả hai (`A04` nhập phí, `C09` xác nhận đối soát) | Organizer quyết cấu hình, admin quyết đối soát | Một ranh giới có hai chủ thể quyết quy tắc |

Soi bằng bốn kiểu tách sai §4.3: không tách theo tầng kỹ thuật, không theo vai trò người dùng, không theo bảng, không theo cảm giác quy mô. **Đạt.**

**(2) Bất biến**

Cục bộ: **11/11** — `INV-01`–`INV-09` trong `RG-1`; `INV-10`, `INV-11` trong `RG-2`. Xuyên ranh giới: **không có**.

**(3) Bảng luồng xuyên ranh giới**

| Luồng | Chiều | Lỗi có thể xảy ra | Thử lại | Bù trừ | Idempotency | Nhóm |
|---|---|---|---|---|---|---|
| `E1` định danh và quyền | `RG-3` → `RG-1`, `RG-2` | không đọc được nguồn quyền | đọc lại | không cần | đọc thuần | `N2` |
| `E2` điều kiện bán, cấu hình thương mại | `RG-2` → `RG-1` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| `E3` cửa sổ check-in, sở hữu sự kiện | `RG-2` → `RG-1` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| `E5` sự kiện bị hủy lan sang đơn/vé/hoàn | `RG-2` → `RG-1` | lỗi từng phần trên N đơn | thử lại tiến | **cấm đảo ngược** (`ASR-05`) | chuyển trạng thái vé đơn điệu (`BIZ-147`) | `N3` |
| `E9` khoản thu và kết quả hoàn vào sổ cái | `RG-1` → `RG-2` | mất/lặp bản ghi | phát lại | không cần — sổ cái là kết quả tổng hợp (`C07`) | hội tụ theo khoản thu | `N3` |

`E4` nội bộ `RG-2`; `E6`, `E7`, `E8`, `E10`, `E11`, `E12` nội bộ `RG-1` → `N1`.

**(4) Ứng viên Saga: `0`.** Chuỗi bù trừ `E8`+`E10`+`E11` nằm trọn trong `RG-1` nên **không buộc phối hợp xuyên ranh giới**, không phải Saga phân tán. Câu này nói về **vị trí thay đổi**; nó không khẳng định chuỗi ấy chạy trong một giao dịch — phạm vi giao dịch thuộc `B12`. Trần 3 còn **dư 3**.

**(5) Bốn con số `quy-trinh` §4.4**

> ⚠️ **Đổi tên một chỉ số ở `v0.3`, và lý do phải ghi ra** (`GOV-064`). `v0.2` gọi chỉ số thứ hai là *"Số luồng giao dịch xuyên ranh giới"*, gần trùng tên với *"Số luồng giao dịch xuyên service"* ở `docs/quy-trinh-lam-viec.md` dòng 292 — một nhãn dễ làm người đọc tưởng mọi quan hệ xuyên service đều chịu trần 3. Chỉ số ở đây **không có trần**: nó đếm nhóm quan hệ `E1`–`E12` xuyên ranh giới, và giá trị của nó là 5 · 7 · 10 · 9 · 10 · 8. Một bản đánh giá ngoài ngày 2026-08-30 đã đọc con số 10 cạnh trần 3 rồi kết luận *"cả năm phương án đều không đạt"* — rủi ro hiểu sai là **thật**, không giả định. Nguồn hiện hành đã làm rõ thứ chịu trần 3 là **luồng thực sự được chốt là Saga**, không phải mọi quan hệ xuyên ranh giới; ở `B11-A`, đó là ứng viên nhóm `N4` ghi tại mục (4) của từng phương án. Nhãn cũ ở `quy-trinh` vẫn cần đọc kèm ngữ cảnh này, nhưng **nghĩa của trần không còn chờ phân xử**.

| Chỉ số | Giá trị |
|---|---|
| Số ranh giới nghiệp vụ | **3** (trần 8) |
| Số nhóm quan hệ xuyên ranh giới | **5** |
| Số điểm cần sao chép dữ liệu | **4** — `RG-1` ← điều kiện bán (`E2`) · `RG-1` ← cửa sổ check-in và sở hữu sự kiện (`E3`) · `RG-2` ← khoản thu và kết quả hoàn làm đầu vào sổ cái (`E9`) · mọi ranh giới ← định danh và bộ role (`E1`, đếm một điểm) |
| Số hợp đồng phải chốt | **4** cặp chiều — ước lượng ở `B11-A`, `B13` mới chốt |

**(6) Giả định về sở hữu dữ liệu** *(khai để `B12` kiểm lại — `ASR-13`)*

`RG-1` sở hữu đơn, giữ chỗ, trạng thái cam kết nguồn cung, khoản thu, yêu cầu hoàn, vé và tiến trình gửi vé. `RG-2` sở hữu sự kiện, cấu hình bán, địa điểm, phân loại, khuyến mãi cấu hình và hồ sơ chi trả sự kiện — **gồm cả tỷ lệ phí nền tảng**. `RG-3` sở hữu hồ sơ nghiệp vụ, đăng ký organizer và quan hệ theo dõi.

⚠️ Vị trí tỷ lệ phí là **giả định của phương án này**, không phải kết luận: `BIZ-123` vẫn `OPEN` và thuộc `B12`.

**(7) Cơ chế chẩn đoán — vị trí và đường lấy dữ liệu**

Một **đơn vị triển khai riêng, nằm ngoài cả ba ranh giới nghiệp vụ**, đếm riêng khỏi trần 8 (xem ghi chú `R1`). Nó đọc **kho dữ liệu quan sát** mà ba ranh giới sinh ra, bằng **credential chỉ đọc riêng trên kho đó**; **không** có đường ghi vào dữ liệu nghiệp vụ và **không** truy cập schema nghiệp vụ (`ASR-12`, `NFR-08`). Chuẩn của dữ liệu quan sát chốt ở `B16`.

**(8) Đường đưa kết quả tới người có quyền** (`ASR-09`, `NFR-12` — vế này **không** hoãn)

Vì cơ chế nằm ở một đơn vị riêng, kết quả **không bị khóa bên trong một ranh giới nghiệp vụ**: đơn vị đó có thể phơi kết quả ra ngoài chính nó cho một chủ thể được xác thực qua nguồn danh tính mà `RG-3` tham chiếu. **Phương án này không đóng mất đường ra.** Hình thức cụ thể — màn hình, API hay cách khác — **vẫn hoãn** (`RES-044`); `B11-A` **không** tạo use case hay giao diện nào (`GOV-019`).

**(9) Chatbot mua vé** — là **kênh**, không phải ranh giới (`R12`). Nó đọc thông tin bán từ năng lực `BC-01` (nằm ở `RG-2`) và gửi ý định mua qua quy tắc của `BC-02` (nằm ở `RG-1`).

**(10) Chèn lỗi và ranh giới `Phát hành vé`**

Chèn lỗi được ở **4 cặp chiều nội bộ** + 2 biên với hệ ngoài (cổng thanh toán, nguồn danh tính) + biên truy cập dữ liệu của ba ranh giới. *(`v0.3` ghi “2 biên nội bộ” — phép đếm gộp `RG-3`→`RG-1` và `RG-3`→`RG-2` làm một, không cùng hạt với các phương án khác. Nay dùng **cặp chiều** cho cả sáu.)* **Ít điểm nhất trong sáu phương án** — `ASR-15` đạt nhưng ở mức thấp nhất.

`GOV-049`: `BC-04` nằm trong `RG-1`. Nếu ranh giới `Phát hành vé` đổi — `Vé` thành root riêng — thì `INV-07` trở thành phối hợp giữa một lần phát hành và nhiều aggregate vé. Đó là **thay đổi nội bộ `RG-1`**, **không đụng hợp đồng xuyên ranh giới nào**. Đổi lại, phép đo `QS-05` phải chạy trên toàn `RG-1` vì lõi lớn.

**(11) Số ứng viên**

Mức service: **3** (cộng 1 đơn vị chẩn đoán, đếm riêng). Mức chỉ số: **phạm vi dự kiến ~30**, **chưa chốt** — tập tín hiệu thuộc `B16`, và câu hỏi này đang do `R0-OPEN-06` quản lý.

**(12) Bố trí hai máy · vòng đồng bộ · rủi ro**

Cách bố trí để so sánh, **không khóa** (`ASR-11`): (a) `RG-1` một máy, `RG-2`+`RG-3`+đơn vị chẩn đoán máy kia; (b) `RG-1`+`RG-3` một máy, `RG-2`+đơn vị chẩn đoán máy kia.

Vòng đồng bộ: cặp `RG-2`→`RG-1` (`E2`, `E3`) và `RG-1`→`RG-2` (`E9`) tồn tại hai chiều. Không thành vòng **nếu** `E9` đi bằng **sự kiện miền** — hợp lý vì `C07` mô tả sổ cái là kết quả **tổng hợp**. Đây là **điều kiện cần kiểm ở `B13`**, không phải ràng buộc `B11-A` chốt; `R8` **đạt với điều kiện đó**.

Rủi ro: `RG-1` gánh 4 context và **10 nhóm root (12 tên root)** theo quy tắc đếm ở §3.1 — khối lớn, khó chia việc cho ba thành viên; ít điểm chèn lỗi nhất; ít ứng viên mức service nhất.

---

### 4.2 `PA-2` — Tách tiền khỏi vé · 4 ranh giới

**(1) Cách gộp**

| Ranh giới | Năng lực gộp |
|---|---|
| `RG-1` **Bán vé: sự kiện, cấu hình bán, nguồn cung và đơn** | `BC-01`, `BC-02` |
| `RG-2` **Tiền: thanh toán, hoàn tiền, đối soát và chi trả** | `BC-03`, `BC-05` |
| `RG-3` **Quyền tham dự, vào cửa và giao nhận vé** | `BC-04`, `BC-06` |
| `RG-4` **Tài khoản và quyền nghiệp vụ** | `BC-07` |

Sáu phép thử cho cắt `RG-1` | `RG-2` | `RG-3`:

| Phép thử | Tín hiệu ủng hộ gộp | Tín hiệu ủng hộ tách | Chi phí phát sinh | Bằng chứng |
|---|---|---|---|---|
| 1 Bất biến | `INV-11` muốn `01` cạnh `05` | `INV-01`–`INV-04`, `INV-06` gom trọn trong `RG-1`; `INV-05`, `INV-08` trọn `RG-2`; `INV-07`, `INV-09` trọn `RG-3` | `INV-11` xuyên ranh giới → phải sao chép tỷ lệ phí | `B7` §5 |
| 2 Triển khai độc lập | — | Đổi quy tắc tiền không buộc triển khai lại lõi bán vé | 3 cặp hợp đồng phải chốt trước khi chia việc | `B5` §5.1 |
| 3 Dữ liệu độc lập | — | Phép xét `Khả dụng` dùng đủ **bốn** nhóm đầu vào của `B2` — trạng thái bán, tồn kho, giữ chỗ, giới hạn mua — và cả bốn nằm trong `RG-1` | `RG-3` cần bản sao **cửa sổ check-in** và sở hữu sự kiện | `B2` mục 3 *Khả dụng*; `B7` §4.2 |
| 4 Chu kỳ thay đổi | — | Quy tắc tiền đổi theo chính sách/cổng ngoài; quy tắc vào cửa đổi theo vận hành sự kiện | — | `B5` §3 |
| 5 Chu kỳ tải | — | Đỉnh mở bán (`RG-1`) khác đỉnh giờ mở cổng (`RG-3`) | — | `B9` `QS-01` và `QS-04` ô *Môi trường* |
| 6 Chủ sở hữu quyết định | — | Organizer quyết cấu hình bán; admin/nền tảng quyết quy tắc tiền | — | `B2` §2 |

Bốn kiểu tách sai §4.3: **không mắc**.

**(2) Bất biến**

Cục bộ: `INV-01`, `INV-02`, `INV-03`, `INV-04`, `INV-06` (`RG-1`); `INV-05`, `INV-08`, `INV-10` (`RG-2`); `INV-07`, `INV-09` (`RG-3`). **Xuyên ranh giới: `INV-11`** — `RG-1` đặt tỷ lệ phí, `RG-2` tiêu thụ. Giá trị **cố định sau phê duyệt** (`BIZ-142`), nên bản sao là bản sao của một giá trị bất biến.

**(3) Bảng luồng xuyên ranh giới**

| Luồng | Chiều | Lỗi có thể xảy ra | Thử lại | Bù trừ | Idempotency | Nhóm |
|---|---|---|---|---|---|---|
| `E1` định danh và quyền | `RG-4` → `RG-1,2,3` | không đọc được | đọc lại | không cần | đọc thuần | `N2` |
| `E3` cửa sổ check-in, sở hữu sự kiện | `RG-1` → `RG-3` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| `E4` mốc kết thúc, tỷ lệ phí | `RG-1` → `RG-2` | bản sao cũ | đọc lại | không cần | giá trị bất biến | `N2` |
| `E6` nghĩa vụ thanh toán của đơn | `RG-1` → `RG-2` | lệnh lặp | phát lại | không cần | hội tụ theo đơn | `N2` |
| `E7` thu hợp lệ → chốt tài nguyên đang giữ | `RG-2` → `RG-1` | chốt lỗi/lặp | phát lại | không cần | `INV-06` hội tụ | `N3` |
| `E5` sự kiện bị hủy | `RG-1` → `RG-2`, `RG-3` | lỗi từng phần | thử lại tiến | **cấm đảo ngược** (`ASR-05`) | `BIZ-147` | `N3` |
| **`E8`+`E10`+`E11` thu tiền → phát hành → khi lỗi thì hoàn và trả lượt** | `RG-2` → `RG-3` → `RG-2`, `RG-1` | phát hành lỗi **sau khi đã thu tiền** | có | **có — hoàn tác xuyên ranh giới** | `INV-07`, `INV-08` | **`N4`** |

`E2` nội bộ `RG-1`; `E9` nội bộ `RG-2`; `E12` nội bộ `RG-3` → `N1`, không tính vào số luồng xuyên ranh giới.

**(4) Ứng viên Saga: `1`** — chuỗi thu tiền → phát hành → bù trừ, xuyên 3 ranh giới. Trần 3 còn **dư 2**.

**(5) Bốn con số §4.4**

| Chỉ số | Giá trị |
|---|---|
| Số ranh giới nghiệp vụ | **4** |
| Số nhóm quan hệ xuyên ranh giới | **7** — `E1`, `E3`, `E4`, `E5`, `E6`, `E7`, và chuỗi `E8`+`E10`+`E11` |
| Số điểm cần sao chép dữ liệu | **3** — `RG-3` ← **cửa sổ check-in** và sở hữu sự kiện (`E3`) · `RG-2` ← tỷ lệ phí và mốc kết thúc (`E4`) · mọi ranh giới ← định danh và bộ role (`E1`, đếm một điểm) |
| Số hợp đồng phải chốt | **9** cặp chiều — ước lượng, `B13` chốt |

**(6) Giả định sở hữu dữ liệu**

`RG-1` sở hữu sự kiện, cấu hình bán, trạng thái cam kết nguồn cung, đơn, giữ chỗ, giới hạn mua, lượt khuyến mãi — **và tỷ lệ phí**. `RG-2` sở hữu khoản thu, yêu cầu hoàn, hồ sơ chi trả sự kiện. `RG-3` sở hữu vé và tiến trình gửi vé. `RG-4` sở hữu hồ sơ nghiệp vụ và đăng ký organizer. ⚠️ Vị trí tỷ lệ phí vẫn là giả định — `BIZ-123` thuộc `B12`.

**(7) Cơ chế chẩn đoán** — như `PA-1`: đơn vị riêng ngoài bốn ranh giới, đọc kho dữ liệu quan sát bằng credential chỉ đọc riêng, không chạm schema nghiệp vụ. Phải tương quan tín hiệu của **4** nguồn thay vì 3.

**(8) Đường đưa kết quả tới người có quyền** — có, cùng lập luận `PA-1`. Không đóng mất đường ra; hình thức hoãn.

**(9) Chatbot** — kênh; đọc thông tin bán từ `BC-01` và gửi ý định mua qua `BC-02`, **cả hai nằm trong `RG-1`** → chatbot chỉ chạm một ranh giới.

**(10) Chèn lỗi và `Phát hành vé`**

Chèn lỗi ở **9 cặp chiều nội bộ** + 2 biên với hệ ngoài + biên truy cập dữ liệu của bốn ranh giới. *(`v0.3` ghi “6 biên nội bộ có hướng”; 6 là số cặp **không hướng**, số **cặp chiều** là 9 — xem bất biến ở §3.4.)*

`GOV-049`: `BC-04` nằm trong `RG-3` cùng `BC-06`. `Vé` thành root riêng → **thay đổi nội bộ `RG-3`**; hợp đồng `RG-2`→`RG-3` không đổi vì nó nói về **đơn đã thu tiền**, không về từng vé.

**(11) Số ứng viên** — mức service **4** (+1 đơn vị chẩn đoán đếm riêng); mức chỉ số **phạm vi dự kiến ~40**, chưa chốt (`B16`, `R0-OPEN-06`).

**(12) Bố trí · vòng đồng bộ · rủi ro**

Bố trí để so sánh: (a) `RG-1`+`RG-2` | `RG-3`+`RG-4`+chẩn đoán; (b) `RG-1` | `RG-2`+`RG-3`+`RG-4`+chẩn đoán.

Vòng đồng bộ: **ba cặp hai chiều** — `RG-1`↔`RG-2` (`E6`/`E7`), `RG-1`↔`RG-3` (`E3`/`E11`), `RG-2`↔`RG-3` (`E8`/`E10`). Mỗi cặp cần **một chiều đi bằng sự kiện miền** thì mới không thành vòng đồng bộ — đó là **điều kiện cần kiểm ở `B13`**, không phải ràng buộc `B11-A` chốt. `R8` **đạt với điều kiện đó**.

Rủi ro: ba cặp hai chiều là chỗ dễ sinh vòng nhất; `INV-11` xuyên ranh giới.

---

### 4.3 `PA-3` — Mỗi bounded context một ranh giới · 7 ranh giới

**(1) Cách gộp** — `RG-01`…`RG-07` tương ứng `BC-CAND-01`…`BC-CAND-07`, không gộp gì.

> **Vì sao phương án này có mặt.** Tầng B §2.2 ghi rõ nguyên tắc *"1 bounded context = 1 service"* **không được áp dụng máy móc**. `PA-3` được đưa vào để **đo cái giá của chính nguyên tắc đó**, không phải vì nó đúng. Nếu bốn con số ở mục 5 cho thấy chi phí không tương xứng thì đó là kết quả có ích, không phải thất bại của phương án.

Sáu phép thử: mọi đường cắt đều **được ủng hộ mạnh bởi phép thử 4 và 6** (mỗi context có nhịp thay đổi và chủ thể quyết quy tắc riêng), và **bị phản đối bởi phép thử 3** (số điểm sao chép cao nhất). Phép thử 1 trung tính: `B7` §5 cho thấy nhóm bất biến `INV-01`–`INV-04`, `INV-06` vốn đã nằm trọn trong `BC-02` nên không bị cắt thêm.

**(2) Bất biến** — cục bộ: `INV-01`–`INV-04`, `INV-06` (`RG-02`); `INV-05`, `INV-08` (`RG-03`); `INV-07`, `INV-09` (`RG-04`); `INV-10` (`RG-05`). Xuyên: **`INV-11`**.

**(3) Bảng luồng** — cả 12 nhóm cạnh của §3.2 đều xuyên ranh giới. Phân loại: `E1`, `E2`, `E3`, `E4`, `E6` → `N2`; `E5`, `E7`, `E9`, `E12` → `N3`; **`E8`+`E10`+`E11` → `N4`**.

**(4) Ứng viên Saga: `1`.** Trần 3 còn **dư 2**.

> **Kết quả đáng chú ý:** số ứng viên Saga **không tăng theo số ranh giới**. Hình dạng bù trừ do **miền** quy định — chỉ chuỗi *thu tiền → phát hành* đòi hoàn tác — chứ không do cách cắt. Cái tăng theo số ranh giới là **hợp đồng** và **điểm sao chép**, mục 5.

**(5) Bốn con số §4.4**

| Chỉ số | Giá trị |
|---|---|
| Số ranh giới nghiệp vụ | **7** (trần 8 — còn dư 1) |
| Số nhóm quan hệ xuyên ranh giới | **10** — cả mười hai nhóm `E*` đều xuyên ranh giới, trong đó `E8`+`E10`+`E11` đếm là một luồng giao dịch |
| Số điểm cần sao chép dữ liệu | **6** |
| Số hợp đồng phải chốt | **16** cặp chiều — ước lượng, `B13` chốt |

**(6) Giả định sở hữu dữ liệu** — mỗi ranh giới sở hữu đúng dữ liệu của các root trong context tương ứng (§3.1). Tỷ lệ phí thuộc `RG-01`, được `RG-05` sao chép. ⚠️ `BIZ-123` thuộc `B12`.

**(7) Cơ chế chẩn đoán** — như `PA-1`: một **đơn vị triển khai riêng ngoài bảy ranh giới**, đếm riêng khỏi trần 8 (ghi chú `R1`), đọc **kho dữ liệu quan sát** bằng **credential chỉ đọc riêng trên kho đó**, **không** có đường ghi vào dữ liệu nghiệp vụ và **không** truy cập schema nghiệp vụ (`ASR-12`, `NFR-08`). Phải tương quan tín hiệu của **7** nguồn. Chuẩn dữ liệu quan sát chốt ở `B16`.

**(8) Đường đưa kết quả tới người có quyền** — có, cùng lập luận.

**(9) Chatbot** — kênh; đọc từ `RG-01`, gửi ý định mua qua `RG-02` → chạm **hai** ranh giới.

**(10) Chèn lỗi và `Phát hành vé`**

Chèn lỗi: 16 cặp chiều nội bộ + 2 biên ngoài + biên dữ liệu của 7 ranh giới. `ASR-15` đạt ở mức cao — chỉ `PA-5` (18 cặp chiều) nhiều hơn.

`GOV-049`: `BC-04` là một ranh giới riêng → thay đổi **cô lập nhất về mặt tiến trình**. Nhưng đây là **một trong hai phương án mà thay đổi có thể chạm một hợp đồng xuyên ranh giới** — `PA-5` cũng vậy, vì cả hai để `BC-04` và `BC-06` ở hai ranh giới: `E12` (`RG-04`→`RG-06`) hiện nói về *"tập vé đã phát hành"*; nếu `Vé` thành root riêng, hạt của hợp đồng đó có thể phải đổi.

**(11) Số ứng viên** — mức service **7** (+1 chẩn đoán); mức chỉ số **phạm vi dự kiến ~70**, chưa chốt.

**(12) Bố trí · vòng đồng bộ · rủi ro**

Bố trí: 7 tiến trình nghiệp vụ + đơn vị chẩn đoán + hạ tầng, trên **2 máy 2 vCPU / 8 GiB, hai tài khoản rời, không cộng tài nguyên được**. Đây là chỗ chạm thẳng lo ngại đã ghi tại `boi-canh` §8: *"Sợ nhiều service và scale instance thì không đủ bộ nhớ"*. **Chưa đo → chưa kết luận được.**

Vòng đồng bộ: **hai cặp hai chiều** — `RG-02`↔`RG-03` (`E6`/`E7`) và `RG-03`↔`RG-04` (`E8`/`E10`). Mỗi cặp cần một chiều đi bằng **sự kiện miền** thì mới không thành vòng — **điều kiện cần kiểm ở `B13`**.

Rủi ro: 16 hợp đồng và 6 điểm sao chép là chi phí lớn nhất; khả năng vừa hạ tầng chưa biết.

---

### 4.4 `PA-4` — Tách nguồn cung khỏi đơn · 5 ranh giới

**(1) Cách gộp**

| Ranh giới | Năng lực gộp |
|---|---|
| `RG-1a` **Sự kiện, cấu hình bán và nguồn cung** | `BC-01` + phần **cam kết nguồn cung** của `BC-02` (`Ghế`, `Loại vé`, `Sector`) |
| `RG-1b` **Đơn hàng, giới hạn mua và lượt khuyến mãi** | Phần còn lại của `BC-02` (`Đơn hàng`, `Giữ chỗ`, `Giới hạn mua`, `Khuyến mãi` theo nghĩa sử dụng, `Lượt dùng khuyến mãi`) |
| `RG-2` **Tiền** | `BC-03`, `BC-05` |
| `RG-3` **Quyền tham dự, vào cửa và giao nhận** | `BC-04`, `BC-06` |
| `RG-4` **Tài khoản và quyền nghiệp vụ** | `BC-07` |

> **Vì sao phương án này có mặt.** `HOT-02` — *"Ranh giới bảo vệ đồng thời giữa nguồn cung, giữ chỗ và đơn chưa được xác định"* — là điểm nóng lớn nhất còn để ngỏ, và `B5-OPEN-02` giao đúng câu hỏi đó cho `B11-A`. `PA-4` là **phương án duy nhất đặt câu hỏi ấy lên bàn** thay vì mặc định giữ nguyên cách gom của `B5`.

Phép thử 1 **phản đối mạnh**: `B7` §4.2 đặt `INV-01`, `INV-03`, `INV-06` trên các root nay bị chia hai phía. Phép thử 5 **ủng hộ**: nguồn cung bị đọc/ghi ở nhịp khác đơn. Chi phí ở cột *Trượt thì sao* của phép thử 1: *"chi phí nhất quán tăng mạnh; chỉ cắt khi có lý do rõ và một Saga/cơ chế phù hợp trong trần đã chốt"*.

⚠️ Soi bằng §4.3: cần kiểm riêng rằng đây **không** phải kiểu *"tách theo bảng CSDL"* — nó tách theo **loại cam kết** (`Ghế`/`Loại vé`/`Sector` là các root nguồn cung mà `B7` §4.2 đã tách sẵn khỏi `Đơn hàng`), không theo bảng. Nhưng `quy-trinh` §4.3 cảnh báo đúng rủi ro của nó: *"đẻ Saga cho cả thao tác lẽ ra là một giao dịch"*.

**(2) Bất biến**

Cục bộ: `INV-01` (`RG-1a`); `INV-02`, `INV-04` (`RG-1b`); `INV-05`, `INV-08`, `INV-10` (`RG-2`); `INV-07`, `INV-09` (`RG-3`).
**Xuyên ranh giới — ba bất biến, nhiều nhất trong tập, ngang `PA-5`:** `INV-03` (`Đơn hàng` và `Giữ chỗ` cùng ở `RG-1b`, nhưng chốt/trả nguồn cung ở `RG-1a`), `INV-06` (root nguồn cung ở `RG-1a`, năm root còn lại ở `RG-1b`), `INV-11`.

**(3) Bảng luồng** — như `PA-2`, cộng **hai** thứ: `E2` nay xuyên ranh giới, và một luồng mới.

| Luồng | Chiều | Lỗi | Thử lại | Bù trừ | Idempotency | Nhóm |
|---|---|---|---|---|---|---|
| `E2` điều kiện bán, cấu hình thương mại, khuyến mãi đã cấu hình | `RG-1a` → `RG-1b` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| **Tạo đơn: giữ nguồn cung + chiếm giới hạn mua + giữ lượt khuyến mãi** | `RG-1b` ↔ `RG-1a` | giữ nguồn cung thất bại **sau khi** giới hạn mua đã bị chiếm | có | **có — phải trả giới hạn mua và lượt khuyến mãi** | `INV-06` | **`N4`** |

**(4) Ứng viên Saga: `2`** — luồng giữ chỗ phân tán + chuỗi thu tiền/phát hành. Trần 3 còn **dư 1**.

**(5) Bốn con số §4.4**

| Chỉ số | Giá trị |
|---|---|
| Số ranh giới nghiệp vụ | **5** |
| Số nhóm quan hệ xuyên ranh giới | **9** — bảy nhóm như `PA-2`, cộng `E2` nay xuyên `RG-1a`→`RG-1b`, cộng luồng giữ chỗ phân tán |
| Số điểm cần sao chép dữ liệu | **5** — ba điểm như `PA-2`, cộng `RG-1b` ← điều kiện bán và cấu hình thương mại (`E2` nay xuyên `RG-1a`→`RG-1b`), cộng `RG-1b` ← **trạng thái cam kết nguồn cung** (`Khả dụng` là **kết quả xét** trên bốn nhóm đầu vào, không phải thứ được sao chép — `PA-4` chia bốn nhóm đó làm hai phía nên **không ranh giới nào** tự tính được `Khả dụng`) |
| Số hợp đồng phải chốt | **13** cặp chiều — ước lượng, `B13` chốt |

⚠️ **Con số 13 tựa vào một giả định định tuyến, khai ra vì `v0.3` để ngầm.** `E7` (*thu hợp lệ → chốt tài nguyên đang giữ*) và `E11` (*phát hành lỗi → trả lượt khuyến mãi và tồn kho*) đều **chạm root nguồn cung**, mà nguồn cung nằm ở `RG-1a`. Phương án này giả định cả hai **đi vào `RG-1b`** — nơi giữ đơn và giữ chỗ — rồi `RG-1b` mới vòng sang `RG-1a` qua chính luồng giữ chỗ phân tán đã đếm. Nếu `B13` định tuyến thẳng tới `RG-1a` thì số cặp chiều thành **14 hoặc 15**. `pa4.puml` mã hoá đúng giả định này; nó là **giả định của phương án**, không phải kết luận, và `B13` kiểm lại.

**(6) Giả định sở hữu dữ liệu** — như `PA-2`, nhưng trạng thái cam kết nguồn cung tách sang `RG-1a`, còn đơn/giữ chỗ/giới hạn mua/lượt khuyến mãi ở `RG-1b`. ⚠️ `BIZ-123` thuộc `B12`.

**(7) Cơ chế chẩn đoán** — như `PA-1`: một **đơn vị triển khai riêng ngoài năm ranh giới**, đếm riêng khỏi trần 8 (ghi chú `R1`), đọc **kho dữ liệu quan sát** bằng **credential chỉ đọc riêng trên kho đó**, **không** có đường ghi vào dữ liệu nghiệp vụ và **không** truy cập schema nghiệp vụ (`ASR-12`, `NFR-08`). Phải tương quan tín hiệu của **5** nguồn. Chuẩn dữ liệu quan sát chốt ở `B16`.

**(8) Đường đưa kết quả tới người có quyền** — có, cùng lập luận.

**(9) Chatbot** — kênh; đọc thông tin bán từ `RG-1a`, gửi ý định mua qua `RG-1b` → chạm **hai** ranh giới.

**(10) Chèn lỗi và `Phát hành vé`**

Chèn lỗi: nhiều hơn `PA-2`, ít hơn `PA-3`. Thêm một biên đắt giá đúng chỗ `HOT-02` — chèn lỗi vào luồng giữ chỗ phân tán là cách trực tiếp để đo `QS-01`.

`GOV-049`: như `PA-2` — thay đổi nội bộ `RG-3`, không đụng hợp đồng xuyên ranh giới.

**(11) Số ứng viên** — mức service **5** (+1 chẩn đoán); mức chỉ số **phạm vi dự kiến ~50**, chưa chốt.

**(12) Bố trí · vòng đồng bộ · rủi ro**

Bố trí: (a) `RG-1a`+`RG-1b` | phần còn lại; (b) `RG-1a`+`RG-1b`+`RG-2` | `RG-3`+`RG-4`+chẩn đoán.

Vòng đồng bộ: **ba cặp hai chiều** — `RG-1a`↔`RG-1b` (cặp mới), `RG-1b`↔`RG-2` (`E6`/`E7`), `RG-2`↔`RG-3` (`E8`/`E10`). `v0.2` ghi *“bốn cặp… chặt nhất trong năm”*; cả hai vế đều sai — bất biến ở §3.4 cho 10 cặp không hướng + 3 cặp hai chiều = 13 cặp chiều, và `PA-2`, `PA-5` cũng có **ba** cặp hai chiều. Mỗi cặp cần một chiều đi bằng **sự kiện miền** thì mới không thành vòng — **điều kiện cần kiểm ở `B13`**.

Rủi ro chính: `ASR-01` — các bất biến chống bán vượt nay xuyên ranh giới, nên **không khẳng định được là đạt trước khi đo**. Đây là chỗ phương án này phải chứng minh lợi ích đủ lớn theo `quy-trinh` §4.2.


---

### 4.5 `PA-5` — Giữ chỗ tách khỏi đơn, tiền gộp cả đối soát · 7 ranh giới

**(1) Cách gộp và sáu phép thử `quy-trinh` §4.2**

| Ranh giới | Năng lực gộp |
|---|---|
| `RG-1` **Sự kiện và cấu hình bán** | `BC-01` — `Sự kiện bán vé`, `Yêu cầu hủy sự kiện`, `Địa điểm`, `Phân loại sự kiện`, `Sector`/`Loại vé` theo nghĩa cấu hình, `Khuyến mãi` theo nghĩa cấu hình |
| `RG-2` **Giữ chỗ, nguồn cung và giới hạn mua** | Phần `BC-02` gồm `Giữ chỗ`, `Ghế`/`Loại vé`/`Sector` theo nghĩa cam kết nguồn cung, `Giới hạn mua`, `Khuyến mãi` theo nghĩa tổng lượt, `Lượt dùng khuyến mãi` |
| `RG-3` **Đơn hàng** | Phần còn lại của `BC-02` — `Đơn hàng` |
| `RG-4` **Tiền: thanh toán, hoàn tiền, đối soát và chi trả** | `BC-03`, `BC-05` |
| `RG-5` **Quyền tham dự và kiểm soát vào cửa** | `BC-04` |
| `RG-6` **Giao nhận thông tin vé** | `BC-06` |
| `RG-7` **Tài khoản và quyền nghiệp vụ** | `BC-07` |

> **Vì sao phương án này có mặt, và nó KHÔNG có mặt vì lý do gì.** Nó có mặt vì `R14` và `R15` là hai ràng buộc `USER_CONFIRMED` mà **không phương án nào trong `PA-1`–`PA-4` dựng ra được hình dạng tương ứng**: `R15` đặt mục tiêu **7 ranh giới nghiệp vụ** với đối soát nằm cùng tiền, còn `PA-3` — phương án 7 ranh giới duy nhất đang có — để đối soát ở một ranh giới riêng và không tách giữ chỗ khỏi đơn. Nó **không** có mặt vì được đánh giá là tốt hơn; §6 xếp nó ngang hàng và `B11-C` mới chọn.

Hình dạng này có **hai đường cắt riêng** cần soi, cộng một đường cắt đã được `PA-3` soi.

Đường cắt thứ nhất, `RG-2` | `RG-3` — **tách giữ chỗ khỏi đơn**:

| Phép thử | Tín hiệu ủng hộ gộp | Tín hiệu ủng hộ tách | Chi phí phát sinh (cột *Trượt thì sao*) | Bằng chứng |
|---|---|---|---|---|
| 1 Bất biến | `INV-03` có hai đầu ở hai phía; `INV-06` có `Đơn hàng` ở `RG-3` và năm root còn lại ở `RG-2` | `INV-01` và `INV-04` **trọn vẹn trong `RG-2`**; `INV-02` trọn vẹn trong `RG-3` | Hai bất biến xuyên ranh giới; cần một luồng tạo đơn có kỷ luật hội tụ | `B7-v0.12` §5 |
| 2 Triển khai độc lập | — | Quy tắc tranh chấp nguồn cung đổi độc lập với quy tắc cấu trúc đơn và giá | Hai đơn vị phải chốt hợp đồng tạo/hủy giữ chỗ trước khi chia việc | `B7-v0.12` §3 |
| 3 Dữ liệu độc lập | Phép xét `Khả dụng` dùng **bốn** nhóm đầu vào của `B2` — trạng thái bán, tồn kho, giữ chỗ, giới hạn mua — và `RG-2` giữ **ba** trong bốn, cộng **một bản sao** *trạng thái bán* lấy từ `RG-1` (đúng điểm sao chép #1 ở mục (5)) | — | `RG-3` phải giữ bản sao trạng thái giữ chỗ để hiển thị và để đóng đơn | `B2` mục 3 *Khả dụng*; `B7` §4.2 |
| 4 Chu kỳ thay đổi | — | **Không còn tín hiệu dùng được** — xem ghi chú ngay dưới bảng | — | `BIZ-075`; `B2-v0.12` mục từ `Giới hạn mua`; `B5` §3 |
| 5 Chu kỳ tải | — | **Đây là phép thử mà `RES-052` gọi đích danh:** lượt giữ chỗ và lượt thành đơn có mức bất đối xứng tải khác nhau | **Mức bất đối xứng CHƯA ĐƯỢC ĐO** — `B7-v0.12` §6 mở đúng điểm này, owner Lê Văn Minh, gate `B11`. Tín hiệu này vì vậy **chưa dùng được làm căn cứ** | `B7-v0.12` §6; `quy-trinh` §4.2 |
| 6 Chủ sở hữu quyết định | Cùng do nền tảng quyết | — | Không phân biệt được hai hướng | `B2` §2 |

> ⚠️ **Phép thử 4 mất tín hiệu ủng hộ tách ở `v0.3`, và hệ quả được ghi ra thay vì lặng lẽ bỏ cụm** (`GOV-063`). `v0.2` ghi tín hiệu ủng hộ tách của phép thử 4 là *"quy tắc giữ chỗ và hạn mức đổi theo **chính sách chống đầu cơ**"*. Ba nguồn bác câu đó: `BIZ-075` (`USER_CONFIRMED` 2026-08-17) — *"sản phẩm **không tuyên bố**… chống đầu cơ"*; mục từ `Giới hạn mua` của `B2-v0.12` — *"**không phải** cơ chế chống đầu cơ"*; và `B5` §3, nguồn mà chính dòng đó dẫn, **không chứa cụm này**. Đây là chỗ **duy nhất trong toàn tài liệu làm trái một dòng `USER_CONFIRMED`**, nên nó bị gỡ.
>
> **Bảng sau khi sửa nói gì.** Đường cắt `RG-2` | `RG-3` — đường cắt then chốt nhất của `PA-5` — nay còn **đúng một** tín hiệu ủng hộ tách: phép thử 2 (*triển khai độc lập*). Phép thử 1 **phản đối**; phép thử 3 **ủng hộ gộp**; phép thử 5 tự khai *"chưa dùng được làm căn cứ"* vì mức bất đối xứng tải chưa được đo; phép thử 6 trung tính. **Bằng chứng cho việc tách giữ chỗ khỏi đơn mỏng hơn nhiều so với những gì `v0.2` thể hiện.** Ghi thẳng ở đây và nhắc lại ở §10, để lượt duyệt biết mình đang cân nhắc gì.

Đường cắt thứ hai, `RG-4` gộp `BC-03`+`BC-05` — **đây là chỗ `R15` chỉ định**:

| Phép thử | Tín hiệu ủng hộ gộp | Tín hiệu ủng hộ tách | Chi phí chấp nhận |
|---|---|---|---|
| 1 Bất biến | `INV-10` cục bộ; `E9` — *"khoản thu hợp lệ và kết quả hoàn là đầu vào sổ cái"* — **nằm trọn trong `RG-4`**, không buộc phối hợp xuyên ranh giới | `INV-11` có đầu kia ở `RG-1` | `INV-11` xuyên ranh giới → phải sao chép tỷ lệ phí |
| 3 Dữ liệu độc lập | Sổ cái tổng hợp từ dữ liệu đã nằm trong `RG-4`; thứ duy nhất phải lấy từ ngoài là tỷ lệ phí, một giá trị **cố định sau phê duyệt** (`BIZ-142`) | — | Một điểm sao chép rẻ |
| 4 Chu kỳ thay đổi | — | Quy tắc thanh toán đổi theo cổng ngoài; quy tắc đối soát đổi theo chính sách tài chính | `RG-4` mang hai nhịp thay đổi |
| 6 Chủ sở hữu quyết định | Admin/nền tảng quyết cả hai | — | — |

Đường cắt `RG-1` | `RG-2` là **đúng đường cắt `PA-3` đã soi** giữa `BC-01` và `BC-02`: được phép thử 4 và 6 ủng hộ mạnh, bị phép thử 3 phản đối. Không chép lại ở đây.

Soi bằng bốn kiểu tách sai §4.3 — **chỗ phải kiểm kỹ nhất của phương án này**:

- **Không tách theo tầng kỹ thuật** và **không tách theo vai trò người dùng**: cả bảy ranh giới đều là năng lực nghiệp vụ, không ranh giới nào tên là *"API"*, *"xử lý"*, *"buyer"* hay *"organizer"*.
- **Không tách theo bảng CSDL:** đường cắt `RG-2` | `RG-3` chạy theo **loại cam kết** — `Giữ chỗ` là cam kết **tạm thời có thời hạn**, `Đơn hàng` là **cấu trúc mua và nghĩa vụ phải trả** — và `B7-v0.12` §3 đã cho hai thứ đó hai root với hai phạm vi nhất quán khác nhau, **trước khi** và **độc lập với** `B11-A`.
- ⚠️ **Rủi ro `quy-trinh` §4.3 cảnh báo — *"đẻ Saga cho cả thao tác lẽ ra là một giao dịch"* — vẫn phải kiểm riêng, không được coi là đã qua.** Kết quả kiểm ở mục (3): luồng tạo đơn **chưa xác định được** là ứng viên Saga hay không. Nó chỉ ở `N3` khi một điều kiện giữ được, và điều kiện ấy **chưa giữ được bằng mô hình miền đã duyệt** — mốc hết hạn mà nó dựa vào được `B2` và `B7` §4.2 neo vào **đơn**. Vì vậy số ứng viên Saga của `PA-5` là **`1` hoặc `2`**, và gate xử lý là **`B11-C`**, không phải `B13`. Xem `B11-A-OPEN-02` ở §8.2. Điều kiện được ghi ra thay vì giấu.

**(2) Bất biến**

Cục bộ — **8**: `INV-01`, `INV-04` (`RG-2`); `INV-02` (`RG-3`); `INV-05`, `INV-08`, `INV-10` (`RG-4`); `INV-07`, `INV-09` (`RG-5`).

Xuyên ranh giới — **3**:

- **`INV-03`** — `Giữ chỗ` ở `RG-2`, `Đơn hàng` giữ tham chiếu ở `RG-3`. Vế *một giữ chỗ mang đúng một mốc hết hạn chung* là **cục bộ trong `RG-2`**; vế *một đơn có đúng một giữ chỗ* xuyên `RG-2`–`RG-3`. Phần *chốt/trả nguồn cung* — vốn xuyên aggregate ở `PA-4` — nay **cục bộ trong `RG-2`**.
- **`INV-06`** — `Đơn hàng` ở `RG-3`; `Giữ chỗ`, root nguồn cung, `Giới hạn mua`, `Khuyến mãi`, `Lượt dùng khuyến mãi` ở `RG-2`. **Năm trên sáu root cùng một ranh giới.**
- **`INV-11`** — `Sự kiện bán vé` ở `RG-1` đặt, `Hồ sơ chi trả sự kiện` ở `RG-4` tiêu thụ.

> **Một chi phí nữa của đường cắt `RG-2` | `RG-3`, `v0.2` bỏ sót: mục từ `Đơn hết hạn`.** `B2-v0.12` định nghĩa `Đơn hết hạn` là *“đơn không còn tiếp tục theo luồng thông thường **tại cùng mốc giữ chỗ hết hạn**”*. `PA-5` đặt đồng hồ hết hạn ở `RG-2` (cùng `Giữ chỗ`) và trạng thái đơn ở `RG-3`, nên **hai thứ mà từ điển buộc xảy ra tại cùng một mốc nằm ở hai ranh giới**. Hệ quả: `RG-3` phải nhận được sự kiện hết hạn và tự chuyển trạng thái đơn hội tụ; khoảng lệch giữa hai bên là một khoảng thời gian **thật**, và trong khoảng đó đơn ở `RG-3` còn trông như đang hiệu lực trong khi nguồn cung ở `RG-2` đã được trả. Đây là **chi phí phải khai**, không phải một lỗi của phương án — nhưng nó cộng vào cùng chỗ với `B11-A-OPEN-02`.

> **Đọc đúng con số 8/3, đừng đọc thành *"bằng `PA-4`"*.** Cả `PA-4` và `PA-5` đều 8 cục bộ / 3 xuyên, nhưng **ba bất biến xuyên ở hai phương án nằm ở hai chỗ khác nhau về chất**. Ở `PA-4`, cái bị cắt là quan hệ **giữ chỗ ↔ nguồn cung**: mỗi lần giữ chỗ phải phối hợp qua ranh giới, và đó chính là lý do `ASR-01` ở `PA-4` là `Chưa biết`. Ở `PA-5`, giữ chỗ, nguồn cung và giới hạn mua **cùng một ranh giới**, nên toàn bộ tranh chấp mà `ASR-01` phát biểu được giải **trong một ranh giới**, không buộc phối hợp xuyên ranh giới; cái bị cắt là quan hệ **đơn ↔ giữ chỗ**, một quan hệ mà `B7-v0.12` mô tả bằng *tham chiếu* chứ không bằng tranh chấp. **Đây là hệ quả thật của `R14`** — chứ không phải việc làm `INV-06` cục bộ, xem kết quả 4 ở §3.5.

**(3) Bảng luồng xuyên ranh giới**

| Luồng | Chiều | Lỗi có thể xảy ra | Thử lại | Bù trừ | Idempotency | Nhóm |
|---|---|---|---|---|---|---|
| `E1` định danh và quyền | `RG-7` → `RG-1,2,3,4,5` | không đọc được nguồn quyền | đọc lại | không cần | đọc thuần | `N2` |
| `E2` điều kiện bán, cấu hình thương mại, khuyến mãi đã cấu hình | `RG-1` → `RG-2` **và** `RG-3` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| `E3` cửa sổ check-in, sở hữu sự kiện | `RG-1` → `RG-5` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| `E4` mốc kết thúc, tỷ lệ phí | `RG-1` → `RG-4` | bản sao cũ | đọc lại | không cần | giá trị bất biến (`BIZ-142`) | `N2` |
| `E5` sự kiện bị hủy | `RG-1` → `RG-2,3,4,5` | lỗi từng phần trên N đơn | thử lại tiến | **cấm đảo ngược** (`ASR-05`) | `BIZ-147` | `N3` |
| `E6` nghĩa vụ thanh toán của đơn | `RG-3` → `RG-4` | lệnh lặp | phát lại | không cần | hội tụ theo đơn | `N2` |
| `E7` thu hợp lệ → chốt tài nguyên đang giữ; đơn chuyển trạng thái | `RG-4` → `RG-2` **và** `RG-3` | chốt lỗi/lặp | phát lại | không cần | `INV-06` hội tụ | `N3` |
| **Vòng đời giữ chỗ: tạo đơn, hủy đơn, giữ chỗ hết hạn** | `RG-3` ↔ `RG-2` | giữ chỗ thành công nhưng đơn không ghi được; hoặc giữ chỗ hết hạn mà đơn chưa biết | có | **không — hội tụ theo mốc hết hạn**, xem điều kiện dưới bảng | `INV-03` (một mốc hết hạn chung), `INV-06` (chuyển trạng thái hội tụ) | **`N3`** |
| `E12` vé đã phát hành cần được gửi | `RG-5` → `RG-6` | mất/lặp lệnh gửi | phát lại | không cần | `INV-07` — gửi lại không sinh quyền | `N3` |
| **`E8`+`E10`+`E11` thu tiền → phát hành → khi lỗi thì hoàn và trả lượt** | `RG-4` → `RG-5` → `RG-4`, `RG-2` | phát hành lỗi **sau khi đã thu tiền** | có | **có — hoàn tác xuyên ranh giới** | `INV-07`, `INV-08` | **`N4`** |

`E9` — *"khoản thu hợp lệ và kết quả hoàn là đầu vào sổ cái"* — **nội bộ `RG-4`** → `N1`, không tính. Đây là thứ `R15` mua được.

> ⚠️ **Điều kiện để luồng vòng đời giữ chỗ là `N3` chứ không phải `N4`, ghi ra vì nó KHÔNG tự đúng.** Xếp nó vào `N3` chỉ hợp lệ khi thiết kế ở `B13` giữ được hai điều: **(a)** `RG-2` cam kết nguồn cung, giới hạn mua và lượt khuyến mãi **mà không phải phối hợp xuyên ranh giới** — hình dạng **gỡ rào cản ranh giới** cho điều đó, vì cả năm root nằm trong `RG-2` theo `R14`, nhưng nó **không bảo đảm** việc phối hợp giữa năm root ấy là một giao dịch: `B7` §4.2 nói thẳng *"B7 không chọn cơ chế phối hợp; đó là B9–B11"*, `B9`/`B10` đã đóng mà không chọn, nên phần còn lại thuộc `B11-C`/`B13`; và **(b)** một giữ chỗ đã tạo mà đơn không hình thành thì **tự hết hiệu lực theo mốc hết hạn của chính nó** (`INV-03`), chứ không cần `RG-3` phát một lệnh nhả có bù trừ. Nếu hình dạng ngược lại được chọn — ghi đơn trước rồi mới xin giữ chỗ — thì luồng này **trở thành `N4`** và số ứng viên Saga của `PA-5` thành **2**.

> ⛔ **Lý do điều kiện (b) chưa giữ được KHÔNG phải là *“chờ `B13`”*, và `v0.2` ghi sai chỗ này.** Vế (b) đòi một giữ chỗ tồn tại **mà đơn chưa hình thành**, rồi tự hết hiệu lực theo mốc hết hạn **của chính nó**. Mô hình miền đã duyệt **không có trạng thái đó**: mục từ `Giữ chỗ` của `B2-v0.12` định nghĩa nó là *“cam kết tạm thời toàn bộ lựa chọn **của một đơn** trong một thời hạn chung”*; mục từ `Giữ chỗ hết hạn` neo mốc hết hạn vào *“mốc hết hạn chung **của đơn**”*; và `B7-v0.12` §4.2 ghi **khóa nghiệp vụ của `Giữ chỗ` là *đơn được giữ***. Tức mốc hết hạn mà điều kiện (b) dựa vào là mốc **của đơn**, và đơn nằm ở `RG-3`. Đây là một khoảng trống **của mô hình**, không phải một lựa chọn giao thức mà `B13` sẽ chọn sau. Gate xử lý là **`B11-C`**, không phải `B13`; xem `B11-A-OPEN-02` ở §8.2.
>
> **Vì sao đây không phải là tự nới lỏng phép phân loại.** §3.4 định nghĩa `N4` là *"khi một bước thất bại, **phải hoàn tác** phần đã làm ở một ranh giới khác"*. Một giữ chỗ hết hạn **không phải hoàn tác** — nó là chuyển trạng thái mà `B7` đã đưa vào mô hình miền từ trước, đúng kiểu `E5` không phải Saga vì `ASR-05` cấm đảo ngược. Phân biệt này dựa vào bằng chứng đã duyệt, không dựa vào mong muốn giảm số Saga.

**(4) Ứng viên Saga: `1` hoặc `2` — chưa chốt.** Chuỗi thu tiền → phát hành → bù trừ (xuyên `RG-4`, `RG-5`, `RG-2`) là ứng viên chắc chắn. Luồng **vòng đời giữ chỗ** là ứng viên thứ hai **có điều kiện**: nó chỉ ở `N3` nếu điều kiện ở mục (3) giữ được, và điều kiện ấy **chưa được chốt ở `B11-A`** — xem `B11-A-OPEN-02` ở §8.2. Trần 3 còn **dư 1 hoặc 2**; dù đọc theo hướng nào, `PA-5` vẫn trong trần.

> **Nhận xét đã ghi ở `PA-3` vẫn đứng; vế thứ hai mà `v0.2` thêm vào thì KHÔNG, và nó bị gỡ ở `v0.3`.** Vế còn đứng: số ứng viên Saga **không tăng theo số ranh giới** — `PA-3` có 7 ranh giới và 1 Saga, bằng `PA-2` với 4 ranh giới, và `PA-6` có 5 ranh giới cũng 1 Saga. Vế bị gỡ: `v0.2` viết *“`PA-5` cắt nhiều hơn `PA-4` nhưng ít ứng viên Saga hơn (1 so với 2)”* rồi rút ra rằng `PA-4` *“cắt khác chỗ”*. Con số 1 của `PA-5` **có điều kiện và điều kiện đó chưa giữ được** (mục (3), `B11-A-OPEN-02`), nên phép so `1 < 2` chưa dùng được làm kết luận có lợi cho `PA-5`. Thứ còn nói được: **cắt ở đâu ảnh hưởng tới số Saga nhiều hơn cắt bao nhiêu** — và điều đó đã đủ bằng chứng từ `PA-2`, `PA-3`, `PA-6` mà không cần dựa vào con số đang mở của `PA-5`.

**(5) Bốn con số `quy-trinh` §4.4**

| Chỉ số | Giá trị |
|---|---|
| Số ranh giới nghiệp vụ | **7** (trần 8 — còn dư 1) |
| Số nhóm quan hệ xuyên ranh giới | **10** — `E1`, `E2`, `E3`, `E4`, `E5`, `E6`, `E7`, `E12`, vòng đời giữ chỗ, và chuỗi `E8`+`E10`+`E11` đếm là một |
| Số điểm cần sao chép dữ liệu | **7** — nhiều nhất trong sáu phương án; liệt kê ở dưới |
| Số hợp đồng phải chốt | **18** cặp chiều — nhiều nhất trong sáu phương án; ước lượng ở `B11-A`, `B13` mới chốt |

Bảy điểm sao chép, liệt kê để đếm lại được:

| # | Ranh giới nhận | Dữ liệu phải giữ bản sao | Từ | Cạnh |
|---|---|---|---|---|
| 1 | `RG-2` | Điều kiện bán và khuyến mãi đã cấu hình | `RG-1` | `E2` |
| 2 | `RG-3` | Cấu hình thương mại/giá và khuyến mãi đã cấu hình | `RG-1` | `E2` |
| 3 | `RG-5` | Cửa sổ check-in và sở hữu sự kiện | `RG-1` | `E3` |
| 4 | `RG-4` | Mốc kết thúc sự kiện và tỷ lệ phí | `RG-1` | `E4` |
| 5 | `RG-6` | Dữ kiện vé đã phát hành đủ để gửi | `RG-5` | `E12` |
| 6 | `RG-3` | Trạng thái giữ chỗ và kết quả xét khả dụng | `RG-2` | vòng đời giữ chỗ |
| 7 | mọi ranh giới | Định danh và bộ role | `RG-7` | `E1` |

> **Định nghĩa dùng để đếm — `v0.1` không viết, `v0.2` viết ở đây, `v0.3` nâng lên §3.4 để nó có hiệu lực cho cả sáu phương án thay vì chỉ cho `PA-5`.** Một *điểm sao chép* là chỗ ranh giới nhận phải **giữ một bản sao** của dữ liệu do ranh giới khác sở hữu. Một **lệnh mang theo giá trị** thì không tính — vì vậy `E6` (*nghĩa vụ thanh toán của đơn*), `E7`, `E8`, `E10` và `E11` **không** sinh điểm sao chép ở bất kỳ phương án nào. Áp đúng định nghĩa này lên `PA-3` cho ra đúng **6** điểm mà `v0.1` đã ghi, nên hai con số so sánh được ở cùng mức hạt.
>
> **Từ `PA-3` (6) sang `PA-5` (7):** `E2` nay rót vào **hai** ranh giới thay vì một (**+1**); `E9` thành nội bộ `RG-4` nên điểm sổ cái biến mất (**−1**); và `RG-3` phải giữ bản sao trạng thái giữ chỗ (**+1**).

**(6) Giả định về sở hữu dữ liệu** *(khai để `B12` kiểm lại — `ASR-13`)*

`RG-1` sở hữu sự kiện, cấu hình bán, địa điểm, phân loại, cấu trúc sector/loại vé theo nghĩa cấu hình, khuyến mãi theo nghĩa cấu hình — **và tỷ lệ phí**. `RG-2` sở hữu trạng thái cam kết nguồn cung, giữ chỗ, giới hạn mua theo tài khoản/sự kiện, tổng lượt khuyến mãi và quyền dùng theo tài khoản. `RG-3` sở hữu đơn, dòng đơn và số tiền phải thanh toán. `RG-4` sở hữu khoản thu, yêu cầu hoàn và hồ sơ chi trả sự kiện. `RG-5` sở hữu vé. `RG-6` sở hữu tiến trình gửi vé. `RG-7` sở hữu hồ sơ nghiệp vụ, đăng ký organizer và quan hệ theo dõi.

⚠️ Vị trí tỷ lệ phí là **giả định của phương án này**, không phải kết luận: `BIZ-123` vẫn `OPEN` và thuộc `B12`. ⚠️ Phương án này còn giả định thêm rằng **cấu trúc sector/ghế được đọc từ `RG-1` chứ không nhân đôi thành hai bản ghi có quyền ghi**; `B12` phải kiểm riêng vì `Sector` và `Loại vé` mang hai nghĩa ở `B7` §3.

> ⚠️ **Ba tên mang hai nghĩa, không phải một — `v0.2` chỉ cảnh báo về một.** Hình dạng nào tách `BC-01` khỏi `BC-02` đều chia đôi ba tên sau, và `B12` phải kiểm từng tên riêng:
>
> | Tên | Nghĩa **cấu hình** (thuộc `BC-01`) | Nghĩa **cam kết / sử dụng** (thuộc `BC-02`) | Mục từ `B2` chứa cả hai nghĩa |
> |---|---|---|---|
> | `Sector`, `Ghế` | Cấu trúc sơ đồ chỗ ngồi | Trạng thái nhận cam kết nguồn cung | mục từ `Sector`, `Ghế` |
> | `Loại vé` | Gói giá và quyền lợi — **và `hạn mức bán`** | Tồn kho còn có thể giữ | mục từ `Loại vé`: *"…quyền lợi và **hạn mức bán**"* |
> | `Khuyến mãi` | Cấu hình giảm giá của organizer — **và `tổng lượt dùng`** | Tổng lượt đang giữ và đã chốt | mục từ `Khuyến mãi`: *"…thời gian hiệu lực và **tổng lượt dùng**"* |
>
> Hai dòng dưới là chỗ `v0.2` bỏ sót: `hạn mức bán` và `tổng lượt dùng` **nằm bên trong** mục từ cấu hình, nhưng giá trị **đang được tiêu thụ** của chúng thuộc phía cam kết. Cảnh báo này áp cho `PA-1`, `PA-3`, `PA-4`, `PA-5` và `PA-6` — năm phương án tách toàn bộ hoặc một phần các nghĩa cấu hình của `BC-01` khỏi các nghĩa cam kết / sử dụng của `BC-02`; `B12` phải kiểm từng tên theo đường cắt thực tế của mỗi phương án.

**(7) Cơ chế chẩn đoán — vị trí và đường lấy dữ liệu**

Như `PA-1`–`PA-4`: một **đơn vị triển khai riêng, nằm ngoài cả bảy ranh giới nghiệp vụ**, đếm riêng khỏi trần 8 (xem ghi chú `R1`). Nó đọc **kho dữ liệu quan sát** bằng **credential chỉ đọc riêng trên kho đó**; **không** có đường ghi vào dữ liệu nghiệp vụ và **không** truy cập schema nghiệp vụ (`ASR-12`, `NFR-08`). Phải tương quan tín hiệu của **7** nguồn — nhiều bằng `PA-3`, và với một đặc điểm riêng: một giao dịch mua vé thành công đi qua **năm** ranh giới (`RG-3` → `RG-2` → `RG-4` → `RG-5` → `RG-6`), nên `R7`/`ASR-08` — một mã tương quan duy nhất qua mọi bước — bị đòi hỏi gắt nhất ở phương án này. Chuẩn của dữ liệu quan sát chốt ở `B16`.

**(8) Đường đưa kết quả tới người có quyền** (`ASR-09`, `NFR-12` — vế này **không** hoãn)

Có, cùng lập luận `PA-1`: cơ chế nằm ở một đơn vị riêng nên kết quả không bị khóa bên trong một ranh giới nghiệp vụ, và nó phơi được kết quả cho một chủ thể được xác thực qua nguồn danh tính mà `RG-7` tham chiếu. **Phương án này không đóng mất đường ra.** Hình thức cụ thể **vẫn hoãn** (`RES-044`); `B11-A` **không** tạo use case hay giao diện nào (`GOV-019`).

**(9) Chatbot mua vé** — là **kênh**, không phải ranh giới (`R12`). Nó đọc thông tin bán từ năng lực `BC-01` (ở `RG-1`) và gửi ý định mua qua quy tắc của `BC-02`, mà `BC-02` nay nằm ở **hai** ranh giới → chatbot chạm **ba** ranh giới, nhiều nhất trong sáu phương án. Đây là một chi phí, không phải một quyết định: `R12` cấm biến chatbot thành ranh giới, không cấm nó chạm nhiều ranh giới.

**(10) Chèn lỗi và ranh giới `Phát hành vé`**

Chèn lỗi được ở **18 cặp chiều nội bộ** + 2 biên với hệ ngoài (cổng thanh toán, nguồn danh tính) + biên truy cập dữ liệu của bảy ranh giới. **Nhiều điểm nhất trong sáu phương án** — `ASR-15` đạt ở mức cao nhất. Đáng chú ý: cặp `RG-3` ↔ `RG-2` là điểm chèn lỗi **trực tiếp vào `HOT-02`**, và cặp `RG-4` → `RG-2` cho phép chèn lỗi vào đúng bước chốt tài nguyên sau khi đã thu tiền — hai chỗ mà `QS-01` và `QS-09` cần đo.

`GOV-049`: `BC-04` là ranh giới riêng `RG-5`, tách khỏi `BC-06` ở `RG-6`. Nếu ranh giới `Phát hành vé` đổi — `Vé` thành root riêng — thì `INV-07` trở thành phối hợp giữa một lần phát hành và nhiều aggregate vé, **nội bộ `RG-5`**. Nhưng như `PA-3`, đây là hình dạng **có thể chạm một hợp đồng xuyên ranh giới**: `E12` (`RG-5`→`RG-6`) hiện nói về *"tập vé đã phát hành"*; nếu `Vé` thành root riêng thì hạt của hợp đồng đó có thể phải đổi. **Không coi ranh giới `Phát hành vé` là đã chốt.**

**(11) Số ứng viên**

Mức service: **7** (cộng 1 đơn vị chẩn đoán, đếm riêng). Mức chỉ số: **phạm vi dự kiến ~70**, **chưa chốt** — tập tín hiệu thuộc `B16`, và câu hỏi này đang do `R0-OPEN-06` quản lý.

**(12) Bố trí hai máy · vòng đồng bộ · rủi ro**

Cách bố trí để so sánh, **không khóa** (`ASR-11`): (a) `RG-2`+`RG-3`+`RG-4` một máy, `RG-1`+`RG-5`+`RG-6`+`RG-7`+đơn vị chẩn đoán máy kia; (b) `RG-1`+`RG-2`+`RG-3` một máy, phần còn lại máy kia. Cả hai đều là **7 tiến trình nghiệp vụ + đơn vị chẩn đoán + hạ tầng trên 2 máy 2 vCPU / 8 GiB, hai tài khoản rời, không cộng tài nguyên được** — chạm thẳng lo ngại đã ghi tại `boi-canh` §8. **Chưa đo → chưa kết luận được**, y như `PA-3`.

Vòng đồng bộ: **ba cặp hai chiều** — `RG-3`↔`RG-2` (tạo/hủy giữ chỗ đi ra, giữ chỗ hết hạn đi về), `RG-3`↔`RG-4` (`E6`/`E7`), `RG-4`↔`RG-5` (`E8`/`E10`). Mỗi cặp cần **một chiều đi bằng sự kiện miền** thì mới không thành vòng — **điều kiện cần kiểm ở `B13`**, không phải ràng buộc `B11-A` chốt. **Bằng `PA-4`** (ba cặp) dù nhiều ranh giới hơn — vì `RG-1` chỉ phát ra và `RG-6` chỉ nhận về.

Rủi ro, xếp theo mức:

1. **Vừa hạ tầng chưa biết** — 7 tiến trình như `PA-3`, và `ASR-11` là `Chưa biết` vì lý do đó.
2. **Chi phí phối hợp cao nhất** — 18 hợp đồng và 7 điểm sao chép, cả hai là mốc trên của tập phương án.
3. **`INV-03` và `INV-06` xuyên `RG-2`–`RG-3`** — luồng tạo đơn phải giữ được điều kiện ở mục (3), nếu không số ứng viên Saga thành 2.
4. **Chia việc cho ba thành viên** — 7 ranh giới trên ba người, tỷ lệ cao nhất trong tập **cùng với `PA-3`** (cũng 7); đây là rủi ro dự án, không phải rủi ro kiến trúc, và `B11-A` chỉ ghi chứ không dùng nó để loại phương án.

---

### 4.6 `PA-6` — Mua vé trọn một ranh giới, tiền gộp cả đối soát · 5 ranh giới

**(1) Cách gộp và sáu phép thử `quy-trinh` §4.2**

| Ranh giới | Năng lực gộp |
|---|---|
| `RG-1` **Sự kiện và cấu hình bán** | `BC-01` |
| `RG-2` **Mua vé và cam kết nguồn cung** | `BC-02` **trọn vẹn** — nguồn cung, `Giữ chỗ`, `Đơn hàng`, `Giới hạn mua`, `Khuyến mãi` theo nghĩa sử dụng, `Lượt dùng khuyến mãi` |
| `RG-3` **Tiền: thanh toán, hoàn tiền, đối soát và chi trả** | `BC-03`, `BC-05` |
| `RG-4` **Quyền tham dự, vào cửa và giao nhận vé** | `BC-04`, `BC-06` |
| `RG-5` **Tài khoản và quyền nghiệp vụ** | `BC-07` |

> **Vì sao phương án này có mặt, và ở trạng thái nào.** Chủ đồ án đề xuất hình dạng này ngày 2026-08-30 và **tự khai đây là phán đoán `CANDIDATE`, không phải quyết định thay `B11-C`** (`RES-053`). Nó vào tập để **so sánh ngang hàng** với `PA-1`–`PA-5`; `GOV-018` giữ nguyên việc chọn ở `B11-C` sau `B11-B`. **Ba lý do chủ đồ án nêu, chép lại chứ không diễn giải:** không tạo đường cắt giữ chỗ–đơn khi **bất đối xứng tải chưa được đo**; ba thành viên và hai máy 2 vCPU / 8 GiB; giữ ba miền cốt lõi *đặt vé → tiền → vé* thành ba đơn vị rõ ràng. Chủ đồ án cũng tự nêu rằng đồ thị phụ thuộc cho `RCA` **không nghèo đi**, vì đồ thị còn gồm cổng thanh toán, nguồn danh tính, hai máy chủ, ranh giới dữ liệu và kho dữ liệu quan sát.
>
> ⚠️ **Hệ quả `R14`, ghi thẳng vì nó là điều kiện hợp lệ của phương án này.** `PA-6` đặt `Đơn hàng` **cùng ranh giới** với `Giới hạn mua`, `Khuyến mãi` theo nghĩa tổng lượt và `Lượt dùng khuyến mãi` — đúng thứ vế sau của `R14` cấm khi đọc tuyệt đối. `PA-6` vì vậy **chỉ hợp lệ dưới cách đọc có phạm vi** của `RES-050`, đúng cách đọc mà `GOV-062` đã xác lập khi giữ `PA-4`. Chủ đồ án **tự phát hiện và tự khai** hệ quả này khi đề xuất; `B11-A` không suy thêm phạm vi nào.

Đường cắt cần soi riêng ở đây là **quyết định KHÔNG cắt bên trong `BC-02`** — tức phép thử ngược của bảng thứ nhất ở §4.5(1):

| Phép thử | Tín hiệu ủng hộ gộp trọn `BC-02` | Tín hiệu ủng hộ tách | Chi phí phát sinh (cột *Trượt thì sao*) | Bằng chứng |
|---|---|---|---|---|
| 1 Bất biến | **`INV-01`, `INV-02`, `INV-03`, `INV-04`, `INV-06` đều trọn vẹn trong `RG-2`** — năm trên mười một bất biến, gồm cả hai bất biến mà `PA-4` và `PA-5` phải để xuyên ranh giới | — | Không có | `B7-v0.12` §5 |
| 2 Triển khai độc lập | — | Quy tắc tranh chấp nguồn cung đổi độc lập với quy tắc cấu trúc đơn và giá | `RG-2` mang hai nhịp triển khai trong một đơn vị | `B7-v0.12` §3 |
| 3 Dữ liệu độc lập | Ba trong bốn nhóm đầu vào của `Khả dụng` — tồn kho, giữ chỗ, giới hạn mua — nằm cùng ranh giới với `Đơn hàng`; chỉ *trạng thái bán* phải sao chép từ `RG-1`. `PA-5` phải sao chép **thêm** trạng thái giữ chỗ sang ranh giới đơn, `PA-6` thì không | — | Một điểm sao chép, cùng loại với mọi phương án tách `BC-01` | `B2` mục 3 *Khả dụng*; `B7` §4.2 |
| 4 Chu kỳ thay đổi | — | — | **Không có tín hiệu dùng được cho cả hai hướng**, cùng lý do đã ghi ở §4.5(1): cụm *"chính sách chống đầu cơ"* bị `BIZ-075` và mục từ `Giới hạn mua` bác (`GOV-063`) | `BIZ-075`; `B2-v0.12`; `B5` §3 |
| 5 Chu kỳ tải | **Đây là phép thử có thẩm quyền, và nó CHƯA ĐƯỢC ĐO** — `B7-v0.12` §6, owner Lê Văn Minh, gate `B11`. Chủ đồ án dẫn đúng điểm mở này làm lý do không cắt | — | Chưa đo thì chưa kết luận được **theo cả hai chiều** | `B7-v0.12` §6; `quy-trinh` §4.2 |
| 6 Chủ sở hữu quyết định | Cùng do nền tảng quyết | — | Không phân biệt được hai hướng | `B2` §2 |

> **Một điều phải nói thẳng về hai bảng này.** Bảng trên và bảng thứ nhất của §4.5(1) đọc **cùng một bộ bằng chứng theo hai chiều ngược nhau**, và sau khi `GOV-063` gỡ cụm *"chống đầu cơ"* thì **không bên nào có một con số đỡ**: phép thử có thẩm quyền cho câu hỏi này là phép thử 5, và nó chưa được đo ở cả hai chiều. Chênh lệch còn lại là chênh lệch về **bất biến** — `PA-6` giữ năm bất biến cục bộ mà `PA-5` phải để hai trong số đó xuyên ranh giới. `B11-A` **ghi chênh lệch đó chứ không kết luận** phương án nào tốt hơn; việc chọn ở `B11-C` sau `B11-B` (`GOV-018`).

Ba đường cắt còn lại **đã được soi ở nơi khác và không chép lại**: `RG-1` | `RG-2` là đúng đường cắt `BC-01` | `BC-02` mà `PA-3` §4.3(1) đã soi; `RG-2` | `RG-3` là đường cắt tiền/vé mà `PA-2` §4.2(1) đã soi; `RG-3` gộp `BC-03`+`BC-05` là đường cắt `R15` chỉ định, đã soi ở §4.5(1) bảng thứ hai; `RG-4` gộp `BC-04`+`BC-06` là cách gộp `PA-2` đã dùng.

Soi bằng bốn kiểu tách sai §4.3: không tách theo tầng kỹ thuật, không theo vai trò người dùng, không theo bảng, không theo cảm giác quy mô — cả năm ranh giới là năng lực nghiệp vụ lấy nguyên từ bảng `B5` §3. **Đạt.**

**(2) Bất biến**

Cục bộ — **10**: `INV-01`, `INV-02`, `INV-03`, `INV-04`, `INV-06` (`RG-2`); `INV-05`, `INV-08`, `INV-10` (`RG-3`); `INV-07`, `INV-09` (`RG-4`).

Xuyên ranh giới — **1**: **`INV-11`** — `Sự kiện bán vé` ở `RG-1` đặt, `Hồ sơ chi trả sự kiện` ở `RG-3` tiêu thụ. Giá trị là tỷ lệ phí, **cố định sau phê duyệt** (`BIZ-142`), nên bản sao là bản sao của một giá trị bất biến.

> **`INV-03` và `INV-06` cục bộ ở `PA-6`, và đó là khác biệt cấu trúc rõ nhất so với `PA-4`/`PA-5`.** Cả sáu nhóm root của `INV-06` — `Đơn hàng`, `Giữ chỗ`, root nguồn cung, `Giới hạn mua`, `Khuyến mãi`, `Lượt dùng khuyến mãi` — nằm trong `RG-2`. Đây là hệ quả trực tiếp của việc giữ trọn `BC-02`, không phải một tối ưu riêng của phương án.

**(3) Bảng luồng xuyên ranh giới**

| Luồng | Chiều | Lỗi có thể xảy ra | Thử lại | Bù trừ | Idempotency | Nhóm |
|---|---|---|---|---|---|---|
| `E1` định danh và quyền | `RG-5` → `RG-1,2,3,4` | không đọc được nguồn quyền | đọc lại | không cần | đọc thuần | `N2` |
| `E2` điều kiện bán, cấu hình thương mại, khuyến mãi đã cấu hình | `RG-1` → `RG-2` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| `E3` cửa sổ check-in, sở hữu sự kiện | `RG-1` → `RG-4` | bản sao cũ | đọc lại | không cần | đọc thuần | `N2` |
| `E4` mốc kết thúc, tỷ lệ phí | `RG-1` → `RG-3` | bản sao cũ | đọc lại | không cần | giá trị bất biến (`BIZ-142`) | `N2` |
| `E5` sự kiện bị hủy | `RG-1` → `RG-2,3,4` | lỗi từng phần trên N đơn | thử lại tiến | **cấm đảo ngược** (`ASR-05`) | `BIZ-147` | `N3` |
| `E6` nghĩa vụ thanh toán của đơn | `RG-2` → `RG-3` | lệnh lặp | phát lại | không cần | hội tụ theo đơn | `N2` |
| `E7` thu hợp lệ → chốt tài nguyên đang giữ | `RG-3` → `RG-2` | chốt lỗi/lặp | phát lại | không cần | `INV-06` hội tụ | `N3` |
| **`E8`+`E10`+`E11` thu tiền → phát hành → khi lỗi thì hoàn và trả lượt** | `RG-3` → `RG-4` → `RG-3`, `RG-2` | phát hành lỗi **sau khi đã thu tiền** | có | **có — hoàn tác xuyên ranh giới** | `INV-07`, `INV-08` | **`N4`** |

`E9` nội bộ `RG-3` (đúng thứ cách gộp `BC-03`+`BC-05` mua được); `E12` nội bộ `RG-4` → `N1`, không tính.

> **`PA-6` đóng vấn đề giữ chỗ mồ côi mà `PA-5` mở ra.** Vì không có ranh giới nào nằm giữa `Giữ chỗ` và `Đơn hàng`, trạng thái *giữ chỗ đã cam kết nguồn cung nhưng chưa có đơn* **không xuất hiện**, và khóa nghiệp vụ *đơn được giữ* của `B7-v0.12` §4.2 không bị đặt vào thế phải hoà giải. `B11-A-OPEN-02` **không áp cho `PA-6`**. Cũng vì lý do đó, mục từ `Đơn hết hạn` — *"tại cùng mốc giữ chỗ hết hạn"* — được giữ **trong một ranh giới**.

**(4) Ứng viên Saga: `1`, không kèm điều kiện** — chỉ chuỗi thu tiền → phát hành → bù trừ, xuyên `RG-3`, `RG-4`, `RG-2`. Trần 3 còn **dư 2**. Đây là điểm khác `PA-5`: con số của `PA-5` là `1 hoặc 2` vì phụ thuộc một điều kiện chưa giữ được, còn con số này không phụ thuộc điều kiện nào.

**(5) Bốn con số `quy-trinh` §4.4**

| Chỉ số | Giá trị |
|---|---|
| Số ranh giới nghiệp vụ | **5** (trần 8) |
| Số nhóm quan hệ xuyên ranh giới | **8** — `E1`, `E2`, `E3`, `E4`, `E5`, `E6`, `E7`, và chuỗi `E8`+`E10`+`E11` đếm là một |
| Số điểm cần sao chép dữ liệu | **4** — `RG-2` ← điều kiện bán, cấu hình thương mại và khuyến mãi đã cấu hình (`E2`) · `RG-4` ← cửa sổ check-in và sở hữu sự kiện (`E3`) · `RG-3` ← mốc kết thúc và tỷ lệ phí (`E4`) · mọi ranh giới ← định danh và bộ role (`E1`, đếm một điểm) |
| Số hợp đồng phải chốt | **12** cặp chiều — ước lượng ở `B11-A`, `B13` mới chốt |

Kiểm bằng bất biến §3.4: **10** cặp không hướng có trao đổi + **2** cặp hai chiều (`RG-2`↔`RG-3`, `RG-3`↔`RG-4`) = **12** cặp chiều. ✅

**(6) Giả định về sở hữu dữ liệu** *(khai để `B12` kiểm lại — `ASR-13`)*

`RG-1` sở hữu sự kiện, cấu hình bán, địa điểm, phân loại, sector/loại vé theo nghĩa cấu hình, khuyến mãi theo nghĩa cấu hình — **và tỷ lệ phí**. `RG-2` sở hữu trạng thái cam kết nguồn cung, giữ chỗ, đơn, dòng đơn, số tiền phải thanh toán, giới hạn mua theo tài khoản/sự kiện, tổng lượt khuyến mãi và quyền dùng theo tài khoản. `RG-3` sở hữu khoản thu, yêu cầu hoàn và hồ sơ chi trả sự kiện. `RG-4` sở hữu vé và tiến trình gửi vé. `RG-5` sở hữu hồ sơ nghiệp vụ, đăng ký organizer và quan hệ theo dõi.

⚠️ Vị trí tỷ lệ phí là **giả định của phương án này**, không phải kết luận: `BIZ-123` vẫn `OPEN` và thuộc `B12`. ⚠️ Như `PA-5`, phương án này giả định thêm rằng **cấu trúc sector/loại vé được đọc từ `RG-1` chứ không nhân đôi thành hai bản ghi có quyền ghi**; `B12` phải kiểm riêng vì `Sector` và `Loại vé` mang hai nghĩa ở `B7` §3.

**(7) Cơ chế chẩn đoán — vị trí và đường lấy dữ liệu**

Như `PA-1`: một **đơn vị triển khai riêng, nằm ngoài cả năm ranh giới nghiệp vụ**, đếm riêng khỏi trần 8 (ghi chú `R1`). Nó đọc **kho dữ liệu quan sát** bằng **credential chỉ đọc riêng trên kho đó**; **không** có đường ghi vào dữ liệu nghiệp vụ và **không** truy cập schema nghiệp vụ (`ASR-12`, `NFR-08`). Phải tương quan tín hiệu của **5** nguồn; một giao dịch mua vé thành công đi qua **ba** ranh giới (`RG-2` → `RG-3` → `RG-4`), ít nhất trong các phương án nhiều ranh giới, nên đòi hỏi của `R7`/`ASR-08` nhẹ hơn `PA-5` một bậc. Chuẩn dữ liệu quan sát chốt ở `B16`.

**(8) Đường đưa kết quả tới người có quyền** (`ASR-09`, `NFR-12` — vế này **không** hoãn)

Có, cùng lập luận `PA-1`: cơ chế nằm ở một đơn vị riêng nên kết quả không bị khóa bên trong một ranh giới nghiệp vụ, và nó phơi được kết quả cho một chủ thể được xác thực qua nguồn danh tính mà `RG-5` tham chiếu. Hình thức cụ thể **vẫn hoãn** (`RES-044`); `B11-A` **không** tạo use case hay giao diện nào (`GOV-019`).

**(9) Chatbot mua vé** — là **kênh**, không phải ranh giới (`R12`). Nó đọc thông tin bán từ năng lực `BC-01` (ở `RG-1`) và gửi ý định mua qua quy tắc của `BC-02` (trọn trong `RG-2`) → chatbot chạm **hai** ranh giới.

**(10) Chèn lỗi và ranh giới `Phát hành vé`**

Chèn lỗi được ở **12 cặp chiều nội bộ** + 2 biên với hệ ngoài (cổng thanh toán, nguồn danh tính) + biên truy cập dữ liệu của năm ranh giới. `ASR-15` đạt ở mức trung bình của tập — nhiều hơn `PA-1`, `PA-2`, ít hơn `PA-3`, `PA-5`. ⚠️ **Một chi phí phải khai:** vì tranh chấp nguồn cung–giữ chỗ–đơn nằm **trong** `RG-2`, `QS-01` **không** có một biên tiến trình để chèn lỗi vào đúng chỗ `HOT-02`; phép đo phải chạy bên trong một ranh giới. `PA-4` và `PA-5` là hai phương án cho biên đó.

`GOV-049`: `BC-04` nằm trong `RG-4` cùng `BC-06`, đúng như `PA-2`. `Vé` thành root riêng → `INV-07` trở thành phối hợp giữa một lần phát hành và nhiều aggregate vé, **nội bộ `RG-4`**; hợp đồng `RG-3`→`RG-4` không đổi vì nó nói về **đơn đã thu tiền**, không về từng vé. **Không coi ranh giới `Phát hành vé` là đã chốt.**

**(11) Số ứng viên**

Mức service: **5** (cộng 1 đơn vị chẩn đoán, đếm riêng). Mức chỉ số: **phạm vi dự kiến ~50**, **chưa chốt** — tập tín hiệu thuộc `B16`, câu hỏi do `R0-OPEN-06` quản lý.

**(12) Bố trí hai máy · vòng đồng bộ · rủi ro**

Cách bố trí để so sánh, **không khóa** (`ASR-11`): (a) `RG-1`+`RG-2` một máy, `RG-3`+`RG-4`+`RG-5`+đơn vị chẩn đoán máy kia; (b) `RG-2` một máy, phần còn lại máy kia. Năm tiến trình nghiệp vụ cộng đơn vị chẩn đoán trên hai máy 2 vCPU / 8 GiB — cùng mức với `PA-4`, thấp hơn `PA-3` và `PA-5` hai tiến trình.

Vòng đồng bộ: **hai cặp hai chiều** — `RG-2`↔`RG-3` (`E6`/`E7`) và `RG-3`↔`RG-4` (`E8`/`E10`). Mỗi cặp cần một chiều đi bằng **sự kiện miền** thì mới không thành vòng — **điều kiện cần kiểm ở `B13`**. Cùng số cặp với `PA-3`, ít hơn `PA-2`, `PA-4`, `PA-5` một cặp.

Rủi ro, xếp theo mức:

1. **`RG-2` gánh trọn `BC-02` — sáu nhóm root, tám tên root — nên mọi tranh chấp nóng của `QS-01` nằm trong một đơn vị.** ⚠️ `v0.3` gọi đây là *“khối lớn nhất trong các phương án nhiều ranh giới”*; **sai theo chính quy tắc đếm ở §3.1**: `RG-1a` của `PA-4` gánh `BC-01` (bảy nhóm) cộng nhóm nguồn cung, tức **tám nhóm / mười tên**, lớn hơn trên cả hai cách đếm. Thứ `RG-2` đứng đầu là **mức tranh chấp**, không phải kích thước — và tài liệu này **không có thước đo mức tranh chấp**, nên câu so sánh đã được gỡ thay vì đổi thước. Đây là chỗ `ASR-06` sẽ đo, và `ASR-06` là `Chưa biết` ở mọi phương án.
2. **`HOT-02` không có biên tiến trình để chèn lỗi** — xem mục (10). `B5-OPEN-02` giao câu hỏi ranh giới nguồn cung/giữ chỗ/đơn cho `B11-A`; `PA-6` trả lời bằng cách **không cắt**, và đó là một câu trả lời hợp lệ chỉ khi phép thử 5 được đo.
3. **`INV-11` xuyên ranh giới** — bản sao tỷ lệ phí, cùng loại chi phí với `PA-2`, `PA-3`, `PA-5`.
4. **Chia việc cho ba thành viên** — 5 ranh giới trên ba người, cùng mức `PA-4`; rủi ro dự án, không phải rủi ro kiến trúc.

---

## 5. `PA-0` — Đường cơ sở chi phí, KHÔNG thuộc tập lựa chọn

Ghi theo `GOV-055`: gộp toàn bộ bảy năng lực vào **một** ranh giới triển khai, dữ liệu vẫn chia theo ranh giới với credential riêng cho từng phần (`R5` giữ nguyên).

| Chỉ số | Giá trị | Đối chiếu |
|---|---|---|
| Bất biến cục bộ | **11/11** | mốc trên |
| Luồng xuyên ranh giới | **0** | mốc dưới |
| Điểm sao chép dữ liệu | **0** | mốc dưới |
| Hợp đồng phải chốt | **0** | mốc dưới |
| Ứng viên Saga | **0** | mốc dưới |
| Ứng viên mức service | **1** | mốc dưới |
| Điểm chèn lỗi | chỉ biên ngoài và biên truy cập dữ liệu | mốc dưới của `ASR-15` |

**Vì sao nó không nằm trong tập lựa chọn.** `docs/boi-canh-va-mong-muon.md` §5 dòng 4 ghi mong muốn *"Cần **tách service**"*, và §11.2 xác nhận §5 **giữ nguyên hiệu lực** sau khi đổi đề tài. `docs/quy-trinh-lam-viec.md` PHẦN 1 mục 3 loại hướng so sánh monolith với microservices khỏi trục nghiên cứu. Lê Văn Minh đã chốt cách xử lý tại `GOV-055`.

**Công dụng duy nhất của mục này:** đọc bốn con số §4.4 của `PA-1`–`PA-6` **so với mốc dưới bằng 0**, để thấy mỗi ranh giới thêm vào tốn thêm bao nhiêu hợp đồng và bao nhiêu điểm sao chép.

---

## 6. Ma trận 15 ASR × 6 phương án

**Ba chiều tách bạch, không cộng điểm.**

- **`Loại`** là thuộc tính của chính ASR: **ràng buộc cứng** = `ASR-10`, `ASR-11`, `ASR-12` (`B10` §3.2) và `ASR-13`, `ASR-15` (`B10` §3.3); **driver so sánh** = mười dòng còn lại.
- **`Kết quả`** là bốn giá trị loại trừ nhau: `Đạt` · `Đạt có chi phí` · `Không đạt` · `Chưa biết`.
- **`Bằng chứng`** nằm ở mục tương ứng của từng phương án ở §4, để ma trận còn đọc được.

> ⚠️ **Tiền đề đọc ô `Đạt` của `ASR-01`, thêm ở `v0.3`.** `Đạt` ở đây nghĩa là **ba vế của `ASR-01` nằm trong cùng một ranh giới nên không buộc phối hợp xuyên ranh giới**. Nó **không** khẳng định rằng ba vế ấy được giữ trong một giao dịch nguyên tử: phạm vi giao dịch, khóa và giao thức phối hợp thuộc `B12`, và `B7` §2 cấm che giấu thay đổi vượt ranh giới *"dưới tên 'giao dịch cục bộ'"*. Cùng tiền đề áp cho mọi ô `Đạt` khác của ma trận này.

> ⛔ **Không quy `Cao`/`Trung bình`/`Thấp` thành trọng số và không cộng điểm.** `B10` §1 nói rõ mức đo **tác động kiến trúc**, không đo mức mong muốn; §3.2 nói *"Một phương án dùng ít hơn không vì thế mà tốt hơn"*. Ma trận này để **đọc đánh đổi**, không để chấm điểm.

| ASR | Loại | `PA-1` | `PA-2` | `PA-3` | `PA-4` | `PA-5` | `PA-6` |
|---|---|---|---|---|---|---|---|
| `ASR-01` nguồn cung và giới hạn mua dưới tranh chấp | driver | Đạt | Đạt | Đạt | **Chưa biết** | Đạt | Đạt |
| `ASR-02` kích hoạt lặp hội tụ một hiệu lực | driver | Đạt | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí |
| `ASR-03` một vé tối đa một check-in | driver | Đạt | Đạt | Đạt | Đạt | Đạt | Đạt |
| `ASR-04` lỗi sau khi đã thu tiền | driver | Đạt | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí |
| `ASR-05` lan nhiều đơn, lỗi từng phần | driver | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí |
| `ASR-06` mức tải mục tiêu | driver | **Chưa biết** | **Chưa biết** | **Chưa biết** | **Chưa biết** | **Chưa biết** | **Chưa biết** |
| `ASR-07` dấu vết đủ tín hiệu | driver | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí |
| `ASR-08` một mã tương quan qua mọi bước | driver | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí |
| `ASR-09` cơ chế chạy được, kết quả đến người có quyền | driver | Đạt | Đạt | Đạt | Đạt | Đạt | Đạt |
| `ASR-14` kiểm quyền theo vai trò **và** sở hữu | driver | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí | Đạt có chi phí |
| `ASR-10` trần 8 service và 3 Saga | **ràng buộc cứng** | Đạt — 3 / 0 | Đạt — 4 / 1 | Đạt — 7 / 1 | Đạt — 5 / 2 | Đạt — 7 / **1–2** | Đạt — 5 / 1 |
| `ASR-11` hai máy, bố trí chưa chốt | **ràng buộc cứng** | Đạt có chi phí | Đạt có chi phí | **Chưa biết** | Đạt có chi phí | **Chưa biết** | Đạt có chi phí |
| `ASR-12` chỉ đọc bằng phân quyền thật | **ràng buộc cứng** | Đạt | Đạt | Đạt | Đạt | Đạt | Đạt |
| `ASR-13` không giả định sở hữu dữ liệu | **ràng buộc cứng** | Đạt | Đạt | Đạt | Đạt | Đạt | Đạt |
| `ASR-15` không đóng mất năng lực chèn lỗi | **ràng buộc cứng** | Đạt | Đạt | Đạt | Đạt | Đạt | Đạt |

> **Bốn ô phải đọc kèm lý do, không đọc trơ — ba ô của cột `PA-5` và một ô của cột `PA-6`.**
>
> - **`ASR-01` = `Đạt`.** `B10-v0.9` §3.1 phát biểu `ASR-01` bằng ba vế — *không ghế nào thuộc hai giữ chỗ còn hiệu lực · số lượng khả dụng không âm · tổng đang giữ cộng đã mua của một tài khoản không vượt giới hạn*. Ở `PA-5` **cả ba vế nằm trọn trong `RG-2`**, nên chúng **không buộc phối hợp xuyên ranh giới** — đọc đúng tiền đề ở đầu §6, câu này **không** khẳng định ba vế ấy được giữ trong một giao dịch. Chúng vẫn trải trên **ít nhất ba nhóm root** (`B10` §3.1 dẫn `INV-01`, `INV-03`, `INV-04`, `INV-06`), `B7-v0.12` khai `INV-03` là bất biến **xuyên aggregate**, và `B7` §5 ghi cho `INV-06`: *"Thứ tự, retry và phục hồi xuyên root ở B9–B11"*. Cơ chế phối hợp giữa các root bên trong `RG-2` vì vậy **vẫn mở**, đúng như §4.5(3) đã ghi. Đây là chỗ `PA-5` khác `PA-4` rõ nhất, và là hệ quả trực tiếp của `R14`.
> - **`ASR-10` = `Đạt — 7 / 1–2`.** `v0.2` ghi ô này là `7 / 1` với một điều kiện đặt ở chú thích; `v0.3` đưa cả hai giá trị vào chính ô, vì điều kiện ấy **chưa giữ được bằng mô hình miền đã duyệt** (`B11-A-OPEN-02`). Cả hai giá trị đều trong trần 3.
> - **`ASR-11` = `Chưa biết`,** cùng lý do với `PA-3`: bảy tiến trình nghiệp vụ cộng đơn vị chẩn đoán trên hai máy chưa được đo.
> - **`PA-6`, `ASR-01` = `Đạt`,** và đây là ô `Đạt` **ít điều kiện nhất** trong cột: cả ba vế của `ASR-01` nằm trong `RG-2` **cùng với `Đơn hàng`**, nên không có bản sao nào phải giữ và không có lệnh nào phải vượt ranh giới cho phép xét khả dụng. Đọc cùng tiền đề ở đầu §6 — nó vẫn không khẳng định atomicity.

**Năm điều ma trận nói ra, ghi thẳng thay vì để người đọc tự suy:**

1. **`ASR-06` là `Chưa biết` ở cả sáu cột.** Ngưỡng chưa có (`B10-OPEN-01`, `B9-OPEN-01`), nên **không phương án nào so sánh được bằng số về hiệu năng ở `B11-A`**. Đây là giới hạn thật của vòng này.
2. **`ASR-14` không phân biệt được phương án nào.** Cả sáu cách gộp đều để `Sự kiện bán vé` (`BC-01`) và `Phát hành vé` (`BC-04`) ở hai ranh giới khác nhau, nên `Yêu cầu check-in` luôn phải lấy dữ kiện sở hữu từ ngoài. `B10` xếp `QS-15` mức **Cao** vì nó *có thể* phân biệt phương án; trong **tập phương án này** thì không. Ghi ra để `B11-C` không dùng nó làm căn cứ chọn.

   > **`ASR-14` đòi ba vế, và `v0.2` chỉ trả lời gián tiếp một vế — `v0.3` trả lời đủ hai vế bắt buộc cho từng phương án.** Nguyên văn `ASR-14`: *"Mỗi phương án phải nêu rõ nó lấy dữ kiện sở hữu **từ đâu**, kiểm **ở đâu**, và **nếu dùng bản sao** thì chấp nhận độ trễ tới mức nào"*. Hai vế đầu là **bắt buộc không điều kiện**; vế thứ ba chỉ bắt buộc khi phương án dùng bản sao.
   >
   > | Phương án | Dữ kiện sở hữu **lấy từ đâu** | **Kiểm ở đâu** |
   > |---|---|---|
   > | `PA-1` | Sở hữu sự kiện từ `RG-2` qua `E3`; vai trò từ `RG-3` qua `E1` | Tại `RG-1`, nơi xử lý `Yêu cầu check-in` |
   > | `PA-2` | Sở hữu sự kiện từ `RG-1` qua `E3`; vai trò từ `RG-4` qua `E1` | Tại `RG-3` |
   > | `PA-3` | Sở hữu sự kiện từ `RG-01` qua `E3`; vai trò từ `RG-07` qua `E1` | Tại `RG-04` |
   > | `PA-4` | Sở hữu sự kiện từ `RG-1a` qua `E3`; vai trò từ `RG-4` qua `E1` | Tại `RG-3` |
   > | `PA-5` | Sở hữu sự kiện từ `RG-1` qua `E3`; vai trò từ `RG-7` qua `E1` | Tại `RG-5` |
   > | `PA-6` | Sở hữu sự kiện từ `RG-1` qua `E3`; vai trò từ `RG-5` qua `E1` | Tại `RG-4` |
   >
   > **Cả sáu đều dùng bản sao**, nên vế thứ ba áp cho cả sáu — và **ngưỡng độ trễ chấp nhận được chưa có ở bất kỳ tài liệu đã duyệt nào**. `B11-A` **không đặt ngưỡng** (đó là `B12`/`B13`); nó ghi vế còn thiếu vào bảng phụ thuộc §8.1 dưới nguyên ID nguồn `ASR-14`. Vì hai vế bắt buộc đã được trả lời và vế thứ ba là một con số thuộc gate sau, ô `ASR-14` giữ **`Đạt có chi phí`** ở cả sáu cột — nếu vế thứ ba cũng bị coi là bắt buộc tại `B11-A` thì ô đúng phải là `Chưa biết`, và lượt duyệt được quyền yêu cầu đọc theo hướng đó.
3. **`ASR-01` là dòng phân biệt sắc nhất** — **năm** phương án `Đạt`, chỉ `PA-4` `Chưa biết`. **Việc thêm `PA-5` rồi `PA-6` làm dòng này *ít* phân biệt hơn trước, không phải nhiều hơn** — sau hai vòng thêm, nó chỉ còn tách được đúng một phương án.
4. **`ASR-11` là dòng phân biệt thứ hai**, và nay tách tập phương án thành hai nhóm rõ: `PA-1`, `PA-2`, `PA-4`, `PA-6` (3–5 tiến trình) so với `PA-3`, `PA-5` (7 tiến trình, `Chưa biết`).
5. **Mười trên mười lăm dòng giống hệt nhau ở cả sáu cột.** Chỉ `ASR-01`, `ASR-02`, `ASR-04` và hai ràng buộc cứng `ASR-10`/`ASR-11` có khác biệt — **năm** dòng phân biệt, **mười** dòng không; `v0.2` ghi con số này là *“mười một”* ở ba chỗ và cả ba đều sai. Điều này nói rằng **tập ASR hiện có không đủ để phân biệt tập phương án**, và `B11-C` sẽ phải chọn phần lớn dựa vào bốn con số §4.4 cộng kết quả `B11-B`, chứ không dựa vào ma trận này. Đây là một giới hạn của vòng, không phải một kết luận về phương án.

---

## 7. Phép kiểm `GOV-035` — không phương án nào làm trái một yêu cầu đã duyệt

`ASR` là cổng chặn việc **thêm** thứ mới vào kiến trúc, **không** phải giấy phép **bỏ** một yêu cầu. Ba kịch bản mức `Thấp` vẫn là yêu cầu chừng nào `B8`/`B9` còn giữ chúng.

| Kịch bản | Yêu cầu | Kết quả kiểm | Căn cứ |
|---|---|---|---|
| `QS-14` khử/che trường nhạy cảm trước khi rời phạm vi kiểm soát | `NFR-09` | **Không phương án nào trong sáu làm trái.** Điểm lọc đặt ở biên ra khỏi phạm vi kiểm soát; số biên khác nhau chỉ đổi số điểm lọc, không đổi cách giải. `PA-5` có nhiều biên nhất nên có nhiều điểm lọc nhất — đó là chi phí, không phải vi phạm | `B10` §2 dòng `QS-14` |
| `QS-16` kết quả từ chối phân biệt được nguyên nhân | `NFR-11`, `FR-22`, `FR-46` | **Không phương án nào trong sáu làm trái.** Đây là quyết định hợp đồng lỗi, chốt ở `B13`; không cách gộp nào chặn nó. Ở `PA-5`, lý do từ chối *"hết vé"*, *"vượt giới hạn mua"* và *"hết lượt khuyến mãi"* đều phát sinh trong `RG-2` nên phân biệt được ngay tại nguồn | `B10` §2 dòng `QS-16` |
| `QS-17` lỗi từng phần khi cấp vai trò organizer | `NFR-03`, `BIZ-140` | **Không phương án nào trong sáu làm trái.** Biên với nguồn danh tính ngoài tồn tại y hệt ở mọi phương án | `B10` §2 dòng `QS-17` |

---

## 8. Điểm mở

### 8.1 Bảng phụ thuộc còn mở — tham chiếu nguyên ID nguồn

`B11-A` **không nhận quyền sở hữu** của các điểm dưới đây và **không cấp mã mới** cho chúng. Bảng này tồn tại để `B11-B`/`B11-C` biết mình đang đứng trên những gì chưa chốt.

| ID nguồn | Nội dung ảnh hưởng tới `B11-A` | Owner / gate của nguồn |
|---|---|---|
| `B10-OPEN-01` | Chưa có ngưỡng → `ASR-06` là `Chưa biết` ở cả sáu cột | Lê Văn Minh / sau vòng đo thử |
| `B10-OPEN-02` | `QS-05` chưa chạy; `GOV-049` đã cắt vòng tròn bằng cách buộc mỗi phương án khai mục 10 | Lê Văn Minh / sau bản triển khai đầu tiên |
| `B10-OPEN-03` | Phạm vi câu hỏi RCA ở `B11-A` — đã thu hẹp còn hai câu theo `RES-044` | Lê Văn Minh / `B11-A` cho hai câu đầu |
| `B10-OPEN-09` | Mỗi bất biến cốt lõi có tín hiệu quan sát được khi bị vi phạm hay không — ảnh hưởng dữ liệu quan sát mà mọi phương án phải sinh | Lê Văn Minh / `B8`, `B16` theo `GOV-019` |
| `B10-OPEN-11` | `A3` còn `DRAFT`; `ASR-06` dẫn `MT-3` | Lê Văn Minh / chốt `A3` ở Giai đoạn 3 |
| `B10-OPEN-12` | Cách định danh **từng khoản thu** chưa chốt → ảnh hưởng trực tiếp cách ranh giới chứa `BC-03` biểu diễn hoàn từng giao dịch thừa | Lê Văn Minh / `B12`, `B13` |
| `B9-OPEN-01`, `B9-OPEN-03`, `B9-OPEN-08` | Ngưỡng; tranh chấp trên root `Phát hành vé`; hai ô ngưỡng của `QS-18` | Lê Văn Minh |
| `B7` §6 — định danh khoản thu; `Theo dõi organizer` chưa có bất biến | Ảnh hưởng mô hình dữ liệu của ranh giới chứa `BC-03` và `BC-07` | Lê Văn Minh / `B12`, `B13` |
| `B7` §6 — **mức bất đối xứng tải giữa lượt giữ chỗ và lượt thành đơn** | **Điểm mở mà `B7-v0.12` mở ra và `B11-A` phải sống chung với nó.** Đây là **phép thử số 5** của `quy-trinh` §4.2 và là lý do thật khiến một hệ thống tách giữ chỗ khỏi đơn (`RES-052`). Nó **chưa được đo**, nên ở §4.5 mục (1) tín hiệu *ủng hộ tách* của phép thử 5 được ghi là **chưa dùng được làm căn cứ**. Cùng với `ASR-06`, đây là điểm mở làm cho `B11-A` **không thể so sánh `PA-5` với `PA-1`–`PA-4` bằng số về tải** | Lê Văn Minh / gate `B11` |
| `B5-OPEN-02`, `-03`, `-06`, `-07`, `-08` | Ranh giới nguồn cung/giữ chỗ/đơn (`PA-4` và `PA-5` đặt lên bàn theo hai đường cắt khác nhau); lô hoàn N đơn; mốc thời gian kỹ thuật; audit; sở hữu tỷ lệ phí | Lê Văn Minh / `B11`–`B13` |
| `B4-OPEN-01`, `-03`, `-04`, `-08` | Đồng bộ hồ sơ danh tính; mốc thời gian; audit; sở hữu tỷ lệ phí | Lê Văn Minh / `B11`–`B13` |
| `BIZ-123`, `BIZ-130` | Sở hữu dữ liệu tỷ lệ phí; tên và cách biểu diễn trạng thái đơn | `B12`, `B13` |
| `GOV-024` | Organizer có được gửi lại yêu cầu hủy không | Lê Văn Minh |
| `RES-039` | Ai dùng kết quả RCA và bằng hình thức nào — gate đã dời sang **sau `B11`, trước `B15`** (`RES-044`) | Lê Văn Minh |
| `R0-OPEN-01` | Mười hai ràng buộc `R0` §3 chưa được xác nhận | Lê Văn Minh / bộ RCA |
| `R0-OPEN-06` | Số ứng viên mức chỉ số — xem §9 | Lê Văn Minh / `B11`, sau khi chốt kiến trúc |
| `ASR-14` — vế thứ ba | **Ngưỡng độ trễ chấp nhận được của bản sao dữ kiện sở hữu chưa có.** Cả sáu phương án đều kiểm quyền sở hữu bằng một bản sao lấy qua `E3`, nên vế *“chấp nhận độ trễ tới mức nào”* áp cho cả sáu; `B11-A` trả lời được hai vế đầu và **không có thẩm quyền đặt ngưỡng** | Lê Văn Minh / `B12`, `B13` |

### 8.2 Sổ `B11-A-OPEN` — **hai điểm đã đóng, một điểm đang mở**

> **`v0.1` khai sổ này rỗng và lời khai đó đúng cho `v0.1`.** `v0.2` mở `B11-A-OPEN-01` vì `R14` sinh ra một câu hỏi mà `B11-A` **không có thẩm quyền tự trả lời**; Lê Văn Minh đã trả lời ngày 2026-08-30 (`GOV-062`) nên điểm đó **đóng**. `v0.3` mở `B11-A-OPEN-02` — một câu hỏi khác hẳn về bản chất: không phải phạm vi của một quyết định, mà là một **khoảng trống của mô hình miền** mà `PA-5` chạm phải.

| ID | Nội dung | Vì sao `B11-A` không tự đóng được | Owner / gate |
|---|---|---|---|
| `B11-A-OPEN-01` | **`RES-050` có loại `PA-4` khỏi tập phương án hay không?** `R14` đòi ba root `Giới hạn mua`, `Khuyến mãi` theo nghĩa tổng lượt và `Lượt dùng khuyến mãi` nằm *"cùng ranh giới với nguồn cung **và** giữ chỗ"*. `PA-4` đặt nguồn cung ở `RG-1a` và giữ chỗ ở `RG-1b`, tức **không có ranh giới nào ứng được với cụm đó** — `PA-4` không thỏa `R14` dù có gộp ba root về phía nào | Đây là câu hỏi về **phạm vi của một quyết định `USER_CONFIRMED`**, không phải một phép phân tích. Hai cách đọc đều đứng được: `RES-050` là ràng buộc **tuyệt đối** lên mọi phương án — thì `PA-4` phải rời tập; hoặc nó là ràng buộc lên **hình dạng đang được thêm vào** — thì `PA-4` ở lại để tiếp tục đo cái giá của `HOT-02`. `AGENTS.md` cấm suy thêm phạm vi cho một dòng `USER_CONFIRMED` và cấm tự lấp một điểm mở, nên `B11-A` ghi cả hai cách đọc và dừng | ✅ **ĐÃ ĐÓNG** ngày 2026-08-30 (`GOV-062`) — Lê Văn Minh chọn **giữ `PA-4` trong tập**, tức cách đọc **có phạm vi** |
| `B11-A-OPEN-02` | **Hoà giải khóa nghiệp vụ của `Giữ chỗ` với một hình dạng có ranh giới giữa giữ chỗ và đơn.** `B7-v0.12` §4.2 đặt khóa nghiệp vụ của `Giữ chỗ` là **đơn được giữ**; `B2-v0.12` neo cả cam kết lẫn mốc hết hạn vào **đơn**. `PA-5` đặt hai thứ đó ở hai ranh giới, nên trạng thái *giữ chỗ đã cam kết nguồn cung nhưng chưa có đơn* **chưa được mô hình hoá** — và đó đúng là trạng thái mà điều kiện (b) ở §4.5 mục (3) cần để luồng vòng đời giữ chỗ ở `N3` thay vì `N4` | `B11-A` **không được tự sửa mô hình miền đã duyệt** để cứu một phương án của chính nó. Ba hướng xử lý, `B11-A` **không xếp hạng**: (a) chấp nhận `PA-5` với **2** ứng viên Saga và bỏ điều kiện đi; (b) đưa vào một khái niệm bền vững mới — *ý định mua* tồn tại trước đơn — hoặc đổi khóa nghiệp vụ của `Giữ chỗ`; (c) không chọn hình dạng có ranh giới giữa giữ chỗ và đơn. **Chỉ hướng (b) mới buộc mở lại `B2`/`B7`**; (a) và (c) không đụng tài liệu nào đã duyệt | Lê Văn Minh / **`B11-C`** |
| `B11-A-OPEN-03` | **`R15` có áp cho mọi phương án không?** `R15` có **hai vế**. Vế B — *“mục tiêu hình dạng là 7 ranh giới”* — tự khai là **mục tiêu**, không phải bộ lọc, nên nó không loại phương án nào. Vế A — *“đối soát và chi trả đi cùng ranh giới với thanh toán và hoàn tiền”* — **không** mang chữ *mục tiêu* và **không tự giới hạn**; chạy phép kiểm thì **`PA-1` không thoả** (`BC-05` nằm cùng `BC-01` ở `RG-2`, còn `BC-03` ở `RG-1`) và **`PA-3` không thoả** (`BC-05` là ranh giới riêng `RG-05`) | Đây là câu hỏi về **phạm vi của một dòng `USER_CONFIRMED`**, đúng loại mà `AGENTS.md` cấm agent tự suy. `GOV-062` chỉ phân xử phạm vi của `RES-050`; nó **không nói gì về `RES-051`**. Hai cách đọc đều đứng được: `RES-051` ràng buộc **hình dạng đang được thêm vào** ở vòng `v0.2` — thì `PA-1`, `PA-3` ở lại; hoặc nó là bộ lọc tuyệt đối — thì hai phương án đó phải rời tập. ⚠️ **`B11-A` chưa từng chạy phép kiểm `R15` trên từng phương án ở bất kỳ mục nào**, trong khi `R14` có cả một ô tự kiểm riêng và một điểm mở riêng; bất đối xứng đó là khiếm khuyết của tài liệu, đã được ghi ra thay vì lấp | ✅ **ĐÃ ĐÓNG** ngày 2026-08-31 (`GOV-072`) — Lê Văn Minh: *"Ràng buộc chốt hôm 29/8 là yêu cầu riêng cho hình dạng tôi đang đặt hàng lúc đó"*. `R15` đọc **theo phạm vi**; tập giữ nguyên **sáu** phương án |

> **`B11-A-OPEN-03` đã đóng theo đúng hướng người soạn khuyến nghị — nhưng nó đóng bằng lựa chọn của Lê Văn Minh, không bằng khuyến nghị đó.** Nguyên văn ngày 2026-08-31: *"Ràng buộc chốt hôm 29/8 là yêu cầu riêng cho hình dạng tôi đang đặt hàng lúc đó"*. Ba lý do dưới đây được trình **trước** khi chủ đồ án phát biểu, và giữ nguyên ở đây làm bằng chứng của lượt phân xử.
>
> **Khuyến nghị đã trình:** `B11-A` **không** tự đóng điểm này. Nhưng vì tài liệu đã phải nêu hệ quả của mỗi hướng, nó ghi luôn hướng mà bằng chứng nghiêng về: **đọc theo phạm vi**, ba lý do. **(1) Đối xứng nguồn** — `RES-050` và `RES-051` cùng ngày 2026-08-29, cùng loại *“Ràng buộc hình thành phương án”*, cùng sinh ra từ một lượt chủ đồ án được trình hai hướng về **hình dạng đang thêm vào**; `GOV-062` đã chốt cách đọc cho dòng thứ nhất. **(2) Không hồi tố** — `PA-1` và `PA-3` có **trước** `RES-051`; dùng một ràng buộc sau để xoá hai phương án trước là đúng thứ `GOV-062` đã từ chối làm với `PA-4`. **(3) Hệ quả thực chất** — đọc tuyệt đối thì mất `PA-1` (3 ranh giới) và `PA-3` (7 ranh giới), tức **hai đầu mút của thang chi phí**, và bốn phương án còn lại đều cụm ở giữa; riêng `PA-3` còn tồn tại **để đo cái giá của nguyên tắc “1 context = 1 service”** mà Tầng B §2.2 cấm áp máy móc. ⚠️ Ba lý do này là **phân tích**, không phải phán quyết; chỉ Lê Văn Minh mới đóng được điểm này.
>
> **Nếu chọn cách đọc tuyệt đối thì cũng không phải soạn lại:** `PA-1` và `PA-3` rời tập, §6 mất hai cột, các lời khai xếp hạng và mốc trên/dưới của bốn con số đếm lại. Không đụng `B2`, `B7`, `B10`.

> **`B11-A-OPEN-01` đóng, và cách nó đóng có hệ quả cho cả tập.** Lê Văn Minh chọn giữ `PA-4` (`GOV-062`). Ba lý do mà `v0.2` ghi khi giữ `PA-4` trong lúc chờ vẫn đứng nguyên — kế hoạch được duyệt tại `implementation-status.md` mô tả việc phải làm là *"thêm phương án thứ năm"*; tiền lệ `GOV-055` cho thấy khi chủ đồ án muốn loại một phương án thì dòng sổ nói thẳng; và giữ thừa một phương án là lỗi **đảo ngược được** ở `B11-C` — nhưng **không phải ba lý do đó đóng điểm này**, mà là lựa chọn của người có thẩm quyền.
>
> **Hệ quả:** `R14` được đọc **có phạm vi** cho toàn tập, nên `PA-6` (`RES-053`) — vốn cũng đặt `Đơn hàng` cùng ba root ấy — vào tập một cách hợp lệ, dưới **đúng cách đọc** này chứ không phải một ngoại lệ mới. Ghi lại vì đây là chỗ dễ đọc thành *"chủ đồ án đã nới `RES-050`"*: `RES-050` **không đổi một chữ**; thứ được chốt là **phạm vi** của nó.

Ngoài dòng trên, `B11-A` **không mở điểm `OPEN` nào khác của riêng nó**. Ba điểm từng được cân nhắc đều **không đúng bản chất một điểm mở mới**:

- **Cơ chế chẩn đoán có nằm trong trần 8 không** — đã được Lê Văn Minh quyết định tại `GOV-079`: trần 8 chỉ đếm service nghiệp vụ; chatbot và cơ chế chẩn đoán được đếm riêng về tài nguyên vận hành. Cách áp dụng đã ghi tại §2.
- **Chatbot mua vé đặt ở đâu** — câu hỏi sai. `B5` §7 đã chốt nó là **kênh** đọc từ `BC-01` và gửi ý định mua qua `BC-02`; mỗi phương án chỉ **mô tả** nó chạm ranh giới nào, và mục 9 của từng phương án đã làm.
- **Số ứng viên mức chỉ số** — đã có `R0-OPEN-06` quản lý, gate là *"`B11`, sau khi chốt kiến trúc"*. Cấp một mã mới ở đây là **nhân bản quyền sở hữu**.

`AGENTS.md` cấm tự lấp để hoàn tất checklist; không dựng sổ `OPEN` cho đủ biểu mẫu, và cũng không giấu một điểm mở thật để giữ sổ đẹp.

---

## 9. Phụ lục — đối chiếu `R0` §3

`RES-015` yêu cầu mọi gate `B9`–`B16` đối chiếu mười hai ràng buộc tại `docs/research-rca/R0-boi-canh-va-rang-buoc.md` §3. `docs/project/lien-ket-rca.md` §2 định tuyến **ràng buộc 1 và 3** tới gate `B11`.

**Đây là phụ lục đối chiếu, không phải một nguồn.** Không ràng buộc nào dưới đây sinh ra hoặc sửa đổi một ranh giới, một phương án hay một yêu cầu ở §2–§8. Toàn bộ giữ `CANDIDATE`/`OPEN` vì `R0` còn `DRAFT` và cả mười hai ràng buộc chưa được xác nhận (`R0-OPEN-01`).

| Ràng buộc `R0` §3 | Liên quan `B11-A`? | Kết quả đối chiếu |
|---|---|---|
| **1** — mỗi log, dấu vết và chỉ số xác định được thành phần đang chạy đã sinh ra nó | Gate `B16` + `B11` | `CANDIDATE`. **Sáu** phương án đều đặt cơ chế chẩn đoán **ngoài** ranh giới nghiệp vụ và đọc kho dữ liệu quan sát, nên không phương án nào **chặn** ràng buộc này. Việc mỗi bản ghi mang định danh thành phần là **chuẩn dữ liệu**, chốt ở `B16` — phần đó **chưa đến gate** ở đây |
| **3** — ghi rõ số ứng viên ở cả hai mức | Gate `B11` | `CANDIDATE`. Mức service **đã ghi** ở mục 11 của từng phương án — 3 / 4 / 7 / 5 / 7 / 5. Mức chỉ số **chưa chốt được** vì tập tín hiệu thuộc `B16`, và câu hỏi đang do **`R0-OPEN-06`** quản lý với gate *"`B11`, sau khi chốt kiến trúc"* — tức `B11-C`, **chưa phải `B11-A`** |
| 2, 4, 5, 6, 7, 8, 9, 10, 11, 12 | Không định tuyến tới `B11` | Thuộc `B13`, `B14`, `B15`, `B16` theo bảng `lien-ket-rca.md` §2 |

> **Không có "ràng buộc không giữ được" tại `B11-A`, nên không mở dòng `OPEN` nào.** `lien-ket-rca.md` §2.1 mở đầu bằng *"**Trước khi chốt một gate**, mở `R0` §3…"*; gate `B11` đóng ở `B11-C`, không phải ở đây. Bước 1 chưa kích hoạt thì bước 3 — *"mở một dòng `OPEN`"* — cũng chưa. Vì lý do đó `B11-A` **không** cập nhật bảng §2 của cửa nối; việc đó thuộc `B11-C`.

**Kết quả phép thử độc lập** (`docs/project/lien-ket-rca.md` §2.2):

1. **Tạo tác này thuộc bộ nào?** Bộ **hệ thống**, dải `B`, phân lớp `FORMATION`.
2. **Nó dẫn những nguồn nào?** `B5-v0.14` (§3, §5.1, §7, §9) · **`B7-v0.12`** (§3, §4.2, §4.4, §5, §6) · **`B10-v0.9`** (§2, §3.1–§3.3, §6) · `B2-v0.12` (từ vựng: `Khả dụng`, `Giữ chỗ`, `Phát hành vé`, `Gửi vé`, `Hồ sơ chi trả sự kiện`, `Mã tương quan`) · `B4-v0.15` (qua `B5` §5.1 và `B7` §5) · **`B9-v0.8`** (`QS-01`, `QS-04`, `QS-09`, `QS-10` ô *Môi trường*/*Phản ứng*) · `B8-v0.12` (`NFR-06`, `NFR-08`, `NFR-12`) · `AGENTS.md` · `quy-trinh-lam-viec.md` · `tang-b`, `tang-c` · `boi-canh-va-mong-muon.md` §5, §8, §11.2 · sổ quyết định: `GOV-011`, `GOV-018`, `GOV-019`, `GOV-035`, `GOV-044`, `GOV-045`, `GOV-049`, `GOV-050`, `GOV-055`, `GOV-056`, **`GOV-058`**, **`GOV-059`**, **`GOV-071`**, `RES-023`, `RES-036`, `RES-042`, `RES-043`, `RES-044`, **`RES-050`**, **`RES-051`**, **`RES-052`**, **`RES-053`**, `GOV-024`, `GOV-027`, `GOV-057`, **`GOV-060`**, **`GOV-061`**, **`GOV-062`**, **`GOV-063`**, **`GOV-064`**, `PRJ-004`, `PRJ-005`, `PRJ-008`, `BIZ-075`, `BIZ-123`, `BIZ-130`, `BIZ-142`, `BIZ-147` · `B8-v0.12` còn được dẫn qua `NFR-03`, `NFR-09`, `NFR-11` và hai yêu cầu chức năng `FR-22`, `FR-46` ở §7 · `docs/project/implementation-status.md` (kế hoạch được duyệt, dẫn ở §8.2).

   ⛔ **`PRJ-009` cố ý KHÔNG nằm trong danh sách trên.** Dòng đó ghi quan sát về repository cũ và tự khai giới hạn sử dụng: chỉ dùng ở `B11-B` để ước lượng tái sử dụng, di trú và khả thi, **cấm dùng làm căn cứ hình thành phương án**. Phiên soạn `v0.3` có đọc dòng đó theo yêu cầu của chủ đồ án để biết ranh giới, và **không mục nào của `PA-1`–`PA-6` dẫn về nó**.
3. **Có nguồn nào thuộc bộ kia không?** Có đúng một: `R0` §3, cộng hai mã `R0-OPEN-01`, `R0-OPEN-06` ở §8.1.
4. **Nếu có — nó nằm ở đâu?** Nằm **trong phụ lục §9 này** và trong bảng phụ thuộc §8.1, mọi dòng `CANDIDATE`/`OPEN`, và **không sinh ra** một phương án, ranh giới, luồng hay yêu cầu nào. **Phép thử:** nếu ngày mai `R0` §3 bị bác toàn bộ thì cả sáu phương án ở §4 **vẫn đứng nguyên** — không mục nào từ (1) tới (12) của chúng dẫn về `R0`. **Đã chạy lại cho `PA-5` ở `v0.2`: đạt**, vì `PA-5` chỉ dẫn `B5`, `B7-v0.12`, `B10-v0.8` và hai ràng buộc `USER_CONFIRMED`. **Và chạy lại cho `PA-6` ở `v0.3`: đạt** — mười hai mục của nó dẫn về `RES-053`, `B5` §3, `B7-v0.12` §5 và §3.2 của chính tài liệu này; **không mục nào dẫn về `R0`**.

---

## 10. Phép tự kiểm

Mỗi ô dưới đây được **suy lại theo nội dung hiện tại của `B11-A-v0.5`**; vòng `v0.4` đã suy lại toàn bộ khối tự kiểm, còn `v0.5` chỉ hiệu đính hai lời giải thích đã được Lê Văn Minh duyệt lại. Không kế thừa mù kết luận của bản nháp trước; ô nào chỉ còn đúng nhờ một lập luận mới thì lập luận đó được ghi ngay trong ô.

**Cách ly nguồn**

- [x] Tài liệu hình thành **chỉ từ** `B5`, `B7`, `B10` và các ràng buộc đã xác nhận. **Cả ba phiên soạn** — `v0.1`, `v0.2`, `v0.3` — **không mở** hồ sơ đối chiếu hiện thực và **không đọc repository cũ** (`GOV-044`, `GOV-056`, `GOV-061`).
- [x] **Phiên soạn `v0.3` có đọc `PRJ-009`, và điều đó được khai chứ không giấu.** `PRJ-009` ghi hai quan sát về repository cũ và **tự khai giới hạn sử dụng**: chỉ dùng ở `B11-B`, **cấm dùng làm căn cứ hình thành phương án**, và **cấm dùng làm lý do chọn hay bỏ hình dạng nào** ở `B11-A`. **Phép thử đã chạy:** `PA-6` — phương án duy nhất được thêm ở vòng này — truy về `RES-053` (đề xuất của chủ đồ án), `B5` §3 (bảy context), `B7-v0.12` §5 (định tuyến bất biến) và §3.2 (mười hai quan hệ); **không mục nào trong mười hai mục của nó dẫn về `PRJ-009`**, và §9 câu 2 ghi rõ dòng đó **cố ý không nằm** trong danh sách nguồn. Nếu `PRJ-009` bị xóa khỏi sổ ngay bây giờ thì `PA-1`–`PA-6` **không đổi một chữ**.
- [x] **Không suy ra service, schema, hợp đồng, topic hay Saga đã chốt.** Tên **năm** ranh giới của `PA-6` là tiếng Việt nghiệp vụ lấy nguyên từ bảng `B5` §3 — `v0.3` viết *“sáu”* ở ô này trong khi chính §4.6 viết “năm” ở hai chỗ; mã `RG-*` chỉ để truy vết (Tầng C §3.2.1).
- [x] **Hai nhãn dạng `*_service` xuất hiện trong `RES-050`/`RES-051` KHÔNG được nhập vào tài liệu này.** Đã grep: mọi lần chuỗi `_service` xuất hiện đều là **văn bản giải thích** nói vì sao hai nhãn đó không được dùng. **Không nhãn nào được dùng làm tên một ranh giới.**

**Ràng buộc và thẩm quyền**

- [x] **`R14` được đọc theo PHẠM VI, và lập luận sai của `v0.2` đã bị thay chứ không sửa chữ.** `v0.2` viết *"`PA-1`, `PA-2`, `PA-3` thỏa `R14` một cách hiển nhiên"* — sai; ba phương án đó **không** thỏa. Ghi chú §2 nay viết lại quanh lập luận đúng: cách đọc tuyệt đối xoá **bốn trên năm** phương án lúc `GOV-062` được chốt, và **năm trên sáu** ở tập hiện tại, nên Lê Văn Minh chọn cách đọc có phạm vi (`GOV-062`).
- [x] **Phạm vi `R15` đã được phân xử.** `v0.3` khai *“`R15` vẫn áp cho mọi phương án”* ở hai chỗ — câu mở đầu §2 và chính ô này — **không nguồn nào nói vậy**, và phép kiểm chưa từng được chạy. Chạy nó thì `PA-1` và `PA-3` **không thoả vế đầu** của `R15`. `v0.4` gỡ hai khẳng định đó và mở `B11-A-OPEN-03`; Lê Văn Minh đóng nó ngày 2026-08-31 (`GOV-072`) bằng cách đọc **theo phạm vi**, nên **tập giữ nguyên sáu phương án** và không con số nào phải đếm lại.
- [x] **Tiêu đề §2 đã sửa.** `v0.2` đặt tên mục là *"Ràng buộc áp cho mọi phương án"* trong khi chính mục đó chứa các ràng buộc **có phạm vi** — tiêu đề tự nó là một khẳng định sai. Nay là *"Ràng buộc hình thành phương án"*. `R14` đã được phân xử là có phạm vi tại `GOV-062`; `R15` cũng đã được phân xử theo phạm vi tại `GOV-072`, nên `B11-A-OPEN-03` đã đóng và tập vẫn đủ sáu phương án.
- [x] **Điều kiện tiên quyết của `RES-052` đã được kiểm bằng nguồn, không bằng trí nhớ.** `B7-v0.12` §8 có ô *"Lê Văn Minh đã duyệt `B7-v0.12` ngày 2026-08-29"* đã tích, và `GOV-058` chép nguyên văn câu duyệt. `B9-v0.8` và `B10-v0.8` `APPROVED` cùng ngày tại `GOV-059`, đúng thứ tự `B7` → `B9` → `B10` mà Tầng B §3.3 buộc.
- [x] **Không khẳng định nào vượt gate `B12` hay `B11-C` — và ô này ở `v0.3` tự nó là một lời khai sai, nay được viết lại kèm phép đếm liệt kê được.** `v0.2` dùng cụm *"giao dịch cục bộ"* theo nghĩa **phạm vi giao dịch** — đúng lỗi `B7` §2 viết ra để chặn. `v0.3` sửa **bốn** chỗ rồi khai *"cả bốn đã đổi"*, nhưng **còn sót hai**: §6 ghi chú ô `ASR-01` của `PA-5`, và note `right of R2` trong `pa5.puml`. Cả hai đã sửa ở `v0.4`. **Danh sách đầy đủ mọi chỗ còn dùng chữ *"cục bộ"*, để đếm lại được:** (a) tên nhóm `N1` ở §3.4 — tên nhóm phân loại, không phải lời khai; (b) ghi chú đọc `N1` ngay dưới bảng §3.4 và tiền đề đầu §6 — hai chỗ **cấm** cách đọc atomicity; (c) từ vựng *bất biến cục bộ* của `B7` §5, dùng ở mục (2) của sáu phương án và trong nhãn hộp sáu sơ đồ; (d) `PA-1` mục (4), `PA-5` mục (2) và mục (3), `PA-6` ô `ASR-01` — bốn chỗ đã rào bằng *"không buộc phối hợp xuyên ranh giới"*. **Không chỗ nào còn khẳng định phạm vi giao dịch.**
- [x] **Sáu chỗ khai *"mỗi cặp hai chiều phải có một chiều đi bằng sự kiện"* đã hạ xuống đúng trạng thái của chúng** — **điều kiện cần kiểm ở `B13`**, không phải ràng buộc `B11-A` chốt. Sáu chỗ là mục (12) của cả sáu phương án. **Hai trong sáu nói thẳng tới `R8`** — `PA-1` và `PA-2`, nơi `R8` nay khai là *"đạt với điều kiện đó"*; bốn chỗ còn lại (`PA-3`, `PA-4`, `PA-5`, `PA-6`) ghi điều kiện mà không nhắc mã. `v0.3` khai *"bốn chỗ"* và liệt kê năm phương án — cả hai con số đều sai, và `PA-4` bị bỏ khỏi danh sách dù nó cũng có ba cặp hai chiều.
- [x] **Không** chọn, xếp hạng hay đề nghị một phương án — **kiểm lại riêng cho `PA-5` và `PA-6`**, hai phương án dễ bị viết thành *"phương án chủ đồ án muốn"* nhất vì cả hai sinh từ một dòng sổ mang tên chủ đồ án. Kết quả: §4.5 và §4.6 đều mở đầu bằng câu nói rõ chúng **không** có mặt vì được đánh giá tốt hơn; §4.6 ghi thẳng rằng bảng sáu phép thử của `PA-6` là **ảnh gương** của bảng `PA-5` và **không bên nào có số đỡ**; §6 đặt cả hai ngang hàng; mục (12) của mỗi phương án liệt kê rủi ro của chính nó. Việc chọn thuộc `B11-C` (`GOV-018`).

**Phép đếm và bất biến kiểm được**

- [x] **Bốn con số `quy-trinh` §4.4 đủ ở cả sáu phương án** — `PA-1` 3/5/**4**/4 · `PA-2` 4/7/3/9 · `PA-3` 7/10/6/16 · `PA-4` 5/9/**5**/13 · `PA-5` 7/10/7/18 · **`PA-6` 5/8/4/12**. **Hai con số điểm sao chép đã được sửa ở `v0.3`:** `PA-1` từ 3 lên **4** (thiếu điểm sổ cái do `E9` xuyên `RG-1`→`RG-2`) và `PA-4` từ 4 lên **5** (thiếu bản sao điều kiện bán do `E2` nay xuyên `RG-1a`→`RG-1b`). Cả hai là **lỗi áp không đều một định nghĩa** viết trong `PA-5`, nên `v0.3` **nâng định nghĩa lên §3.4** để nó có hiệu lực cho cả tài liệu.
- [x] **Bất biến `số cặp chiều = số cặp không hướng có trao đổi + số cặp hai chiều` đúng ở cả sáu.** `PA-1` 3+1=4 · `PA-2` 6+3=9 · `PA-3` 14+2=16 · `PA-4` 10+3=13 · `PA-5` 15+3=18 · `PA-6` 10+2=12. Bất biến này bắt được **ba** lời khai sai của `v0.2` mà phép đếm cặp chiều một mình không bắt được: `PA-4` khai *"bốn cặp hai chiều… chặt nhất trong năm"* (đúng là **ba**, và `PA-2`/`PA-5` cũng ba); `PA-3` khai *"nhiều cặp hai chiều nhất"* (đúng là **hai**, ít nhất trong các phương án nhiều ranh giới — **ngang `PA-6`**, cũng hai); `PA-5` khai *"ít hơn `PA-4`"* (đúng là **bằng**).
- [x] **Cả 11 bất biến được định tuyến ở cả sáu phương án** — `PA-1` 11 cục bộ / 0 xuyên · `PA-2` 10/1 · `PA-3` 10/1 · `PA-4` 8/3 · `PA-5` 8/3 · **`PA-6` 10/1**; mỗi phương án cộng lại đúng 11. Một số chỗ viết bằng dải (`INV-01`–`INV-09`) nên phép đếm bằng script không tự bung ra được — đã đối chiếu bằng tay.
- [x] **Quy tắc đếm root được viết ra ở `v0.3` vì `v0.2` đếm hai kiểu trong cùng một tài liệu.** `v0.2` khai `BC-CAND-02` có *"bảy root"* — con số không khớp cách đếm nào: theo **nhóm** là sáu, theo **tên** là tám. §3.1 nay định nghĩa *nhóm root* và mọi con số root đếm theo nhóm. Hai chỗ dùng con số đó cũng đã sửa: `INV-06` trải trên **sáu nhóm root** (tám tên), và rủi ro của `PA-1` là *"4 context và **10 nhóm root (12 tên root)**"* thay vì *"9 root"* — con số cũ có từ trước khi `Giữ chỗ` thành root.
- [x] **Điểm sao chép của cả sáu phương án được liệt kê hoặc dẫn được về một định nghĩa duy nhất** ở §3.4, và định nghĩa đó nói rõ hai điều `v0.2` để ngầm: **lệnh mang theo giá trị không tính** (nên `E6`, `E7`, `E8`, `E10`, `E11` không sinh điểm ở bất kỳ phương án nào), và **bản sao định danh/role theo `E1` đếm một điểm** dù rót vào mọi ranh giới. Áp định nghĩa này lên `PA-3` vẫn cho ra **6**, khớp `v0.1`.
- [x] **Một lỗi đếm của chính vòng `v0.2` đã bị bắt và sửa trước khi tài liệu đóng**, ghi lại vì nó là bằng chứng rằng định nghĩa được dùng chứ không được trưng: bản liệt kê đầu của `PA-5` có tám mục vì tính cả `RG-4` ← *nghĩa vụ thanh toán của đơn* (`E6`). Sai — `E6` là **lệnh mang theo giá trị**.
- [x] **Cả 15 ASR có mặt trong ma trận §6, nay với 6 cột phương án** — đếm lại: 10 driver + 5 ràng buộc cứng = 15 dòng; 15 × 6 = **90 ô** và không ô nào trống.
- [x] **Con số *"dòng ASR giống hệt nhau"* đã được đếm lại chứ không chép.** Đúng **10 trên 15**, không phải 11: năm dòng phân biệt là `ASR-01`, `ASR-02`, `ASR-04`, `ASR-10`, `ASR-11`; mười dòng còn lại giống hệt ở cả sáu cột. `v0.2` ghi *"mười một"* ở **ba** chỗ (§6, §10, §11) và cả ba đã sửa. Việc thêm `PA-6` **không** đổi con số này.
- [x] **Không cộng điểm và không quy mức ưu tiên thành trọng số.** Bốn giá trị `Kết quả` loại trừ nhau; `Loại` là thuộc tính của ASR, không phải của ô.

**Đối chiếu nghĩa và quyết định đã chốt**

- [x] **Đã đối chiếu từng khái niệm với NGHĨA trong `B2-v0.12`, không chỉ với tên — và vòng này tìm thấy bốn chỗ lệch, không phải không có chỗ nào.** (1) Cụm *"hạn mức"* dùng ở **tám** chỗ với nghĩa `Giới hạn mua`, trong khi từ điển có mục từ `Hạn mức bán` mang **nghĩa ngược** — *"giới hạn tổng nguồn cung cho mọi người"*; đã đổi hết sang `Giới hạn mua`, gồm cả tên `RG-2` của `PA-5` và nhãn trong `pa5.puml`. (2) Lời khai *"`RG-2` giữ **cả bốn** nhóm đầu vào của `Khả dụng`"* sai: *trạng thái bán* thuộc `RG-1`, `RG-2` giữ **ba** cộng một bản sao — đúng như chính điểm sao chép #1 của phương án khai. (3) Lý do từ chối *"hết chỗ"* đổi thành mục từ **`Hết vé`**. (4) Cảnh báo hai nghĩa nay phủ thêm hai chỗ `v0.2` bỏ sót: *hạn mức bán* nằm **trong** mục từ `Loại vé`, và *tổng lượt dùng* nằm **trong** mục từ `Khuyến mãi`.
- [x] **Mục từ `Đơn hết hạn` được đưa vào phép soi, thay vì bỏ quên như `v0.2`.** `B2-v0.12` buộc đơn hết hạn *"tại **cùng mốc** giữ chỗ hết hạn"*. `PA-5` đặt đồng hồ ở `RG-2` và trạng thái đơn ở `RG-3`, nên hai thứ mà từ điển buộc xảy ra cùng lúc nằm ở hai ranh giới — đã ghi thành một dòng chi phí ở §4.5(2). `PA-6` không có vấn đề này vì cả hai nằm trong `RG-2`.
- [x] **Các cụm `B11-A` dùng mà `B2-v0.12` KHÔNG có mục từ đều được truy về một nguồn có thẩm quyền, không tự định nghĩa.** `v0.2` liệt kê ba cụm và **ghi sai nguồn của một cụm**; danh sách nay đủ **mười chín** và nguồn đã sửa. `v0.3` nâng từ ba lên bảy nhưng vẫn thiếu mười hai cụm — trong đó có cụm xuất hiện dày nhất của cả nhóm (*nguồn cung*) và cụm nằm đúng chỗ `B2` **cố ý** bỏ trống (*cơ chế chẩn đoán*):
  >
  > | Cụm không có mục từ `B2` | Nguồn định nghĩa |
  > |---|---|
  > | *ranh giới nghiệp vụ* | `ASR-10`; `quy-trinh` PHẦN 6 — đơn vị bị trần 8 chặn |
  > | *ranh giới triển khai* | **`B7` §4.2 và `B5-OPEN-02`** — `v0.2` ghi nguồn là `quy-trinh` PHẦN 5, **sai**; `B5` §3 nói *"số lượng bảy context không phải mục tiêu bảy service"* và `B5-OPEN-02` giao câu hỏi ranh giới triển khai cho `B11-A` |
  > | *nhóm root* | §3.1 của chính tài liệu này, định nghĩa tại chỗ vì nó là quy tắc đếm chứ không phải khái niệm miền |
  > | *nhóm quan hệ xuyên ranh giới* | §3.4 của chính tài liệu này (`GOV-064`) |
  > | *điểm sao chép* | §3.4 của chính tài liệu này |
  > | *vòng đời giữ chỗ* | Tổ hợp **bốn** mục từ đã có: `Đơn hàng` (bước tạo đơn), `Giữ chỗ`, `Giữ chỗ hết hạn`, `Hủy đơn`. Không phải khái niệm mới. `v0.3` khai ba và bỏ sót `Đơn hàng`, dù chính tên luồng ở §4.5(3) mở đầu bằng chữ *tạo đơn* |
  > | *khoản thu* | **Không** phải cách nói tắt của `Xác nhận thanh toán` — `v0.3` khai vậy và đã sửa. `Xác nhận thanh toán` là **một trên mỗi đơn** (mục từ `B2`: mỗi đơn chỉ giữ một kết quả thu hợp lệ); *khoản thu* là **từng giao dịch thu**, và một đơn có thể có **nhiều** khi xảy ra `Thanh toán trùng`. Nguồn: nghĩa dùng bên trong mục từ `Yêu cầu hoàn tiền` và `Thanh toán trùng`. `B10-OPEN-12` dùng đúng nghĩa nhiều-trên-một-đơn này |
  > | *nguồn cung*, *cam kết nguồn cung*, *root nguồn cung* | `B7` §4.2 — ba root `Ghế`/`Loại vé`/`Sector` mang trạng thái nhận cam kết. `B2` dùng chữ *nguồn cung* bên trong định nghĩa `Chế độ bán`, `Địa điểm`, `Sector đứng` nhưng không lập mục từ |
  > | *kho dữ liệu quan sát*, *dữ liệu quan sát* | `ASR-07`, `ASR-12`, `NFR-08`; chuẩn dữ liệu chốt ở `B16`. `B2` §6 chỉ có `Dấu vết vận hành`, `Log có cấu trúc`, `Mã tương quan` |
  > | *cơ chế chẩn đoán*, *lớp giải thích*, *đơn vị chẩn đoán* | **`AGENTS.md` mục 6** — bảng ba dòng phân biệt trợ lý cũ đã gỡ / cơ chế RCA / lớp giải thích. `B2-v0.11` đã **cố ý gỡ** năm mục từ trợ lý cũ (`RES-034`, `GOV-030`), nên chỗ trống trong từ điển là có chủ ý |
  > | *ranh giới* theo nghĩa **aggregate** | `B7` §2 — nghĩa thứ ba của chữ này, khác *ranh giới nghiệp vụ* và *ranh giới triển khai*. Dùng ở cụm *cắt ngang một aggregate* và *ranh giới `Phát hành vé`* (`GOV-049`) |
  > | *cặp chiều*, *cặp hai chiều*, *cặp không hướng*, *nhóm luồng* | §3.4 của chính tài liệu này, cùng chỗ với *điểm sao chép* |
  > | *hợp đồng*, *vòng đồng bộ* | `quy-trinh` §4.4 và GIAI ĐOẠN 4 (qua `R8`) |
  > | *ứng viên Saga* | `quy-trinh` PHẦN 6; `AGENTS.md` cấm đồng nhất nó với một giao dịch xuyên ranh giới |
  > | *kênh* | `B5` §7; `RES-036` (`R12`) |
  > | *credential*, *idempotency* | Hai từ tiếng Anh còn giữ vì chưa có tương đương đã chốt: *credential* ở `ASR-12`/`NFR-08`, *idempotency* là tiêu đề cột diễn khái niệm mà `B2` đặt bên trong `Callback/IPN`. ⚠️ Tầng C §3.2.1 buộc **nhãn chính** là tiếng Việt — hai từ này chỉ nằm trong văn giải thích và tiêu đề cột, **không** làm tên một ranh giới |
  > | *nguồn danh tính* | `NFR-08`; `B2` §2.1 có *Danh tính và vòng đời tài khoản* nhưng không lập mục từ cho hệ ngoài |
- [x] **`Khuyến mãi` theo nghĩa tổng lượt = `Khuyến mãi` theo nghĩa sử dụng.** `RES-050` dùng cụm thứ nhất, `B7` §3 dùng cụm thứ hai cho **cùng một root**. `R14` giữ nguyên văn dòng sổ; sự tương đương được ghi **một lần** ở §3.1 — ghi chú *“Các tên xuất hiện ở hai nghĩa là có chủ ý”* — chứ **không** ở §2 như `v0.3` khai. ⚠️ Cụm *“tổng lượt”* vẫn là chỗ dễ đọc nhầm: mục từ `Tổng lượt dùng` của `B2` là **trần cấu hình** của mã, còn root mà `R14` nói tới là **tổng lượt đang giữ và đã chốt** ở phía cam kết. `B7` §3 gọi root đó là *“theo nghĩa sử dụng”*; chữ *“tổng lượt”* trong `R14` chép nguyên văn `RES-050` nên không sửa được, và ghi chú này tồn tại để nối hai cách gọi thay vì đổi chữ trong một dòng `USER_CONFIRMED`.
- [x] **Đã đối chiếu mọi khẳng định nghiệp vụ với sổ quyết định, và vòng này tìm thấy đúng một chỗ làm trái một dòng `USER_CONFIRMED`.** Phép thử 4 của đường cắt `RG-2`\|`RG-3` viện *"chính sách chống đầu cơ"*; `BIZ-075` (`USER_CONFIRMED`) và mục từ `Giới hạn mua` đều bác, và `B5` §3 — nguồn được dẫn — không chứa cụm này (`GOV-063`). **Đã gỡ, và hệ quả được ghi ra thay vì lặng lẽ bỏ cụm:** đường cắt then chốt nhất của `PA-5` nay còn **đúng một** tín hiệu ủng hộ tách. Sau lượt rà này, **không dòng nào của `B11-A` làm trái một quyết định `USER_CONFIRMED`**.
- [x] **Ledger §3 chạy trước §4 và đã sửa bốn lời khai** — hai từ `v0.1` (11/11 thay vì 9/11; luồng hủy sự kiện thuộc `N3` thay vì Saga), một ở `v0.2` (*"gộp ba root làm `INV-06` cục bộ"* — thực tế vẫn xuyên), và một ở `v0.3` (*"`BC-CAND-02` có bảy root"* — thực tế sáu nhóm / tám tên). Ledger sửa **lời khai**, không sửa quyết định; nguyên văn dòng sổ giữ nguyên theo quy tắc 3.
- [x] **Không dán nhãn Saga trước khi phân loại.** Mọi luồng xuyên ranh giới đều đi qua bảng §3.4 rồi mới được đếm; chỉ nhóm `N4` tính vào trần 3. `AGENTS.md` cấm đồng nhất giao dịch xuyên ranh giới với Saga — đã tuân.
- [x] **Xung đột thuật ngữ về trần đã được xử lý ở phía `B11-A` và ghi vào sổ, không tự sửa nguồn cấp trên** (`GOV-064`). Chỉ số thứ hai của §4.4 đổi tên thành *"Số nhóm quan hệ xuyên ranh giới"* kèm câu nói rõ nó **không có trần**; nguồn hiện hành xác định trần 3 áp cho **Saga**, không áp cho mọi quan hệ xuyên ranh giới. `docs/quy-trinh-lam-viec.md` dòng 292 giữ nhãn cũ nên phải đọc kèm ngữ cảnh này. Đây là chỗ một bản đánh giá ngoài đã đọc nhầm con số 10 thành *"vượt trần 3"*.

**Nội dung từng phương án**

- [x] Mỗi phương án khai **giả định sở hữu dữ liệu** và nói rõ nó chờ `B12` kiểm (`ASR-13`); không phương án nào trình bày sở hữu dữ liệu như đã quyết. Cảnh báo toàn cục ở §4.5(6) phủ đủ `PA-1`, `PA-3`, `PA-4`, `PA-5`, `PA-6`: `B12` phải kiểm riêng `Sector`/`Ghế`, `Loại vé` và `Khuyến mãi` theo đường cắt thực tế, kể cả khi mục (6) của một phương án chỉ ghi giả định sơ bộ. Vì vậy không được đọc việc `PA-5`/`PA-6` viết chi tiết hơn thành bằng chứng rằng `PA-1`/`PA-3`/`PA-4` đã tự giải xong quyền sở hữu.
- [x] Mỗi phương án trong **sáu** nêu **chèn lỗi ở đâu, ở mức nào** (`ASR-15`) và **phải sửa gì nếu ranh giới `Phát hành vé` đổi** (`GOV-049`); không phương án nào coi ranh giới đó là đã chốt. Lời khai *"phương án duy nhất chạm một hợp đồng xuyên ranh giới"* của `PA-3` đã sửa — `PA-5` cũng vậy, vì cả hai để `BC-04` và `BC-06` ở hai ranh giới.
- [x] Mỗi phương án trong **sáu** trả lời **đúng hai câu** về cơ chế chẩn đoán (`RES-044`) — đặt ở đâu, lấy dữ liệu bằng đường nào — cộng ràng buộc chỉ đọc, **và** vế *"kết quả đến được người có quyền"* vốn **không** được hoãn (`B10-OPEN-03`). `PA-3` và `PA-4` ở `v0.2` chỉ ghi *"đơn vị riêng ngoài N ranh giới"* mà bỏ đường lấy dữ liệu và credential; đã bổ sung để ô `ASR-12` có bằng chứng tại chỗ ở cả sáu cột.
- [x] **Việc đặt cơ chế chẩn đoán ở một tiến trình riêng được khai là `CANDIDATE`, không phải quyết định.** Sáu phương án ghi giống hệt nhau nên lời khai này **không phân biệt phương án nào**; lựa chọn thay thế — thành phần đồng vị trí, vẫn giữ credential chỉ đọc riêng — đã được nêu; và giá của nó được chỉ ra: một tiến trình riêng **tiêu tài nguyên trên hai máy**, tức chạm `ASR-11`, đúng dòng đang phân biệt tập phương án. Chốt ở `B11-C`.
- [x] **`ASR-14` được trả lời đủ hai vế bắt buộc cho từng phương án**, thay vì để ô `Đạt có chi phí` đứng không bằng chứng như `v0.2`. §6 nhận xét 2 có bảng *lấy từ đâu / kiểm ở đâu* cho cả sáu. Vế thứ ba — **ngưỡng độ trễ bản sao** — áp cho cả sáu vì cả sáu dùng bản sao, và `B11-A` **không có thẩm quyền đặt ngưỡng**; nó được ghi vào §8.1 dưới nguyên ID nguồn `ASR-14`, owner Lê Văn Minh, gate `B12`/`B13`. Ô này giữ `Đạt có chi phí` với lý do ghi tại chỗ, và lượt duyệt được quyền yêu cầu hạ xuống `Chưa biết`.
- [x] `R11` được ghi thành lời tại §2: frontend tái sử dụng **không** ràng buộc tập phương án. **Kiểm riêng cho `PA-5` và `PA-6`:** cả hai đều **không** bị thêm vào hay bị điều chỉnh vì lý do frontend. ⚠️ `v0.3` còn xếp hạng — *“`PA-5` đòi frontend đổi nhiều nhất, `PA-6` ít nhất”* — **và xếp hạng đó không có bằng chứng ở §1–§9**: tài liệu không phân tích tác động frontend ở đâu cả, `PA-4` cũng tách `BC-02` làm hai, `PA-3` có bảy ranh giới. Câu xếp hạng đã được gỡ; `R11` cấm dùng frontend làm căn cứ, nên `B11-A` không cần và không được đo việc đó.
- [x] `R12` được tôn trọng: chatbot xuất hiện ở mục 9 của **cả sáu** phương án như một **kênh**, không tạo ranh giới nào. Số ranh giới nó chạm — 2 · 1 · 2 · 2 · 3 · 2 — được ghi là **chi phí**, không dùng làm lý do đổi hình dạng.
- [x] Phép kiểm `GOV-035` đã chạy lại cho ba kịch bản mức `Thấp` tại §7 **trên cả sáu phương án**; không phương án nào làm trái một yêu cầu đã duyệt.

**Điểm mở**

- [x] **`B11-A-OPEN-01` đóng đúng cách:** bằng lựa chọn của Lê Văn Minh (`GOV-062`), không bằng phân tích của `B11-A`. Hệ quả cách đọc được ghi ở §2 và §8.2, và `PA-6` vào tập dưới **đúng** cách đọc đó chứ không phải một ngoại lệ mới.
- [ ] **`B11-A-OPEN-02` mở và CHƯA được trả lời.** Ô để trống có chủ ý. Nó không phải câu hỏi về phạm vi một quyết định như `-01`, mà là một **khoảng trống của mô hình miền**: khóa nghiệp vụ của `Giữ chỗ` là *đơn được giữ* (`B7-v0.12` §4.2), nên trạng thái *giữ chỗ đã cam kết nhưng chưa có đơn* — thứ `PA-5` cần để giữ con số 1 Saga — chưa được mô hình hoá. `B11-A` **không tự sửa `B2`/`B7` để cứu một phương án của chính nó**; ba hướng xử lý được ghi ra **không xếp hạng**, và chỉ một trong ba mới buộc mở lại `B2`/`B7`.
- [x] Bảng phụ thuộc §8.1 tham chiếu **nguyên ID nguồn** và giữ owner/gate của nguồn; không nhân bản quyền sở hữu. Dòng mới ở `v0.3` — ngưỡng độ trễ của `ASR-14` — cũng dùng nguyên ID nguồn.
- [x] Phụ lục `R0` §3 **không sinh nội dung**; phép thử độc lập bốn câu đã chạy tại §9 và câu 4 có phép thử kiểm được. **Đã chạy lại cho `PA-6`: đạt** — kết quả nay được ghi **tại chỗ ở §9 câu 4**, chứ `v0.3` chỉ khai ở ô này còn §9 vẫn dừng ở `PA-5`.
- [x] **Không mở dòng `OPEN` nào cho ràng buộc `R0`**, vì `lien-ket-rca.md` §2.1 bước 1 chưa kích hoạt ở `B11-A` — gate `B11` đóng ở `B11-C`. Lý do ghi tại §9.
- [x] **Năm** lời khai **giới hạn** được nêu thẳng thay vì giấu, và §11 khai **cùng năm dòng** thay vì rút gọn còn ba như `v0.2`. Năm dòng đó truy được về nguồn chứ không về ô này: `ASR-06` (§6 nhận xét 1), `ASR-14` (§6 nhận xét 2), *hợp đồng là ước lượng* (mục (5) của sáu phương án), *10 trên 15 dòng ASR giống nhau* (§6 nhận xét 5), *bất đối xứng tải chưa đo* (§8.1, `B7` §6). `v0.3` để hai mục trỏ vòng vào nhau — §10 nói *“cùng năm dòng với §11”* còn §11 nói *“cùng năm dòng với §10”* — nên không mục nào là nguồn.

**Sơ đồ**

- [x] **Bảy sơ đồ đã dựng và đã MỞ ẢNH RA NHÌN**, không chỉ đọc mã nguồn. PlantUML 1.2026.6 `-checkonly` thoát mã `0` cho cả bảy; tỷ lệ rộng/cao đo được: `-00` **0,95** · `-01` **1,55** · `-02` **1,17** · `-03` **1,62** · `-04` **1,68** · `-05` **1,24** · `-06` **1,55**. Cả bảy nằm trong dải Tầng C. **Dựng lại và đo lại sau vòng sửa `v0.4`:** chỉ `-05` đổi — từ **1,29** xuống **1,24** — vì note `ASR-01` của nó dài thêm hai dòng khi gỡ khẳng định atomicity; sáu tỷ lệ còn lại không đổi. **Việc mở ảnh ra nhìn ở vòng này bắt thêm một lỗi mà đọc mã nguồn không bắt được:** `pa4` còn vẽ cạnh `RG-1a`→`RG-1b` với nhãn *“trạng thái khả dụng”* trong khi thân bài đã đổi sang *“trạng thái cam kết nguồn cung”* — `Khả dụng` là **kết quả xét**, không phải thứ được sao chép. Đã sửa.
- [x] **Việc nhìn ảnh ở vòng `v0.2` đã bắt được hai lỗi NỘI DUNG mà đọc mã nguồn không bắt được**, và cả hai đã sửa từ vòng đó: bản đầu của `pa5` chỉ nối `RG-2` và `RG-3` vào kho dữ liệu quan sát dù mục (7) nói cả bảy ranh giới đều sinh; và `E12` vẽ nét liền trong khi nó là quan hệ bằng sự kiện.
- [x] **Vòng `v0.3` bắt thêm ba lỗi nội dung cùng loại trên các sơ đồ cũ, và cả ba đã sửa.** (1) `pa3` chỉ vẽ **2 trên 4** nhánh `E5` — thiếu nhánh tới `RG-04` và `RG-05` — nên mất luôn phân loại `N3` ở hai đích đó; `pa4` thiếu nhánh `E5` tới `RG-1b`. **Phép đếm cặp chiều mù với lỗi này**: sửa đủ vẫn ra 16 và 13, vì hai nhánh thiếu đi vào cặp chiều đã có. (2) `pa3` và `pa4` chỉ nối **2 trên 7** và **2 trên 5** ranh giới vào kho dữ liệu quan sát, mâu thuẫn với mục (7) của chính chúng; nay dùng một cạnh từ khung như `pa5`. (3) `pa4` thiếu mã `E2` trên cạnh `RG-1a`→`RG-1b` dù `E2` nay xuyên ranh giới, và note của nó khai *"nhiều nhất trong bốn phương án"*.
- [x] **Lệch ký pháp trong legend đã sửa trên cả sáu sơ đồ container, và `v0.2` khai sai chỗ này.** Legend cũ ghi nét đứt là *"quan hệ bằng sự kiện **hoặc đọc**"* trong khi `E1`–`E4` — đọc thuần, `N2` — được vẽ **nét liền**; tức ký pháp tự mâu thuẫn. Quy ước nay chọn dứt khoát: **nét liền = quan hệ đồng bộ ứng viên (gọi hoặc đọc trực tiếp), nét đứt = quan hệ bằng sự kiện ứng viên**, và mọi cạnh trên sáu sơ đồ container đã được soát lại theo đúng quy ước đó. Legend của `-00` **không mang** hai dòng ký pháp này nên nó nằm ngoài phép đếm; `v0.3` khai *“cả bảy”*. Ô này ở `v0.2` khai rằng khiếm khuyết sơ đồ *"không phải khiếm khuyết ký pháp"* — lời khai đó **sai** và đã bị gỡ.
- [x] **Mâu thuẫn biểu kiến giữa hộp `Kênh` và mục (9) đã được gỡ bằng nhãn, không bằng cách đổi số.** Hộp `Kênh` gộp web, ứng dụng di động và chatbot, nên nó nối tới **mọi** ranh giới có bề mặt người dùng — ví dụ `pa5` nối 4 ranh giới trong khi mục (9) nói chatbot chạm 3. **Sáu sơ đồ container nay có một note nói rõ điều đó** — `-00` là sơ đồ ngữ cảnh, không có hộp `Kênh`, nên nó nằm ngoài phép đếm; `v0.3` khai *“cả bảy”*.
- [x] **Chiều callback từ cổng thanh toán đã thêm vào cả sáu sơ đồ container**; trước `v0.3` chỉ sơ đồ ngữ cảnh `-00` có. Các cạnh tới hệ ngoài cũng đã được gắn nhãn nội dung thay vì để trần.
- [ ] **Bố cục còn khiếm khuyết trên các sơ đồ nhiều ranh giới, mô tả đúng mức thay vì giấu.** Trên `-03`, `-04`, `-05` và `-06`: các cạnh `E5` toả từ ranh giới sự kiện cắt qua vùng giữa và một số nhãn `E5 [N3]` nằm rời khỏi cạnh của chúng, phải dò nét đứt mới ghép được; hộp `Kênh` bị đẩy ra ngoài khung `Ranh giới nghiệp vụ` nên ba tới bốn cạnh của nó băng qua gần hết chiều rộng hình; hai cạnh tới `Cổng thanh toán` và `Nguồn danh tính` cắt qua mép khung; và trên `-06` note *"BC-02 giữ trọn trong một ranh giới"* nằm xa hộp `RG-2` mà nó chú thích. **Nội dung mô hình không bị ảnh hưởng** — mọi hộp, cạnh, mã `E*` và nhãn `N*` đều đọc được. Theo tiền lệ `GOV-060`, đây là khiếm khuyết của bản nháp dạng mã và phải hết ở bản dựng lại bằng Visual Paradigm cho báo cáo (Tầng C §3.1, ô kiểm `C8`).

**Vòng `v0.4` — sửa cái mà `v0.3` tự khai sai**

- [x] **Khối tự kiểm §10 được suy lại từ đầu trên văn bản đã chốt, KHÔNG viết cùng lúc với bản sửa.** Đây là nguyên nhân gốc của vòng trước: `v0.3` viết ô tự kiểm song song với việc sửa, nên nhiều ô khai **ý định** chứ không khai **kết quả**. Một vòng rà độc lập suy lại toàn bộ 50 ô và bắt được **6 ô sai · 4 ô lạc hậu · 4 ô tích mà chưa đủ bằng chứng**. Tất cả đã được viết lại hoặc bỏ tích ở `v0.4`.
- [x] **Luật mới, áp từ `v0.4`: một ô tự kiểm KHÔNG được chứa một con số mà chính nó không liệt kê ra.** Cả sáu ô sai của `v0.3` đều mang đúng hình dạng đó — *"bốn chỗ"*, *"sáu ranh giới"*, *"bảy sơ đồ"*, *"hai giả định"*, *"một lần ở §2"* — một con số không kèm danh sách nên không ai kiểm chéo được, kể cả người viết. Ô nói về cụm *"giao dịch cục bộ"* nay liệt kê đủ bốn nhóm chỗ dùng thay vì nêu một con số trần.
- [x] **Hai chỗ khẳng định atomicity còn sót từ `v0.3` đã gỡ.** §6 ghi chú ô `ASR-01` của `PA-5` viết *"chúng được giữ bằng một giao dịch cục bộ"*, và note `right of R2` của `pa5.puml` viết *"Giải được bằng một giao dịch cục bộ"*. Cả hai **trái tiền đề đặt ở đầu §6** và trái chính §4.5(3), vốn đã nói *"không bảo đảm việc phối hợp giữa năm root ấy là một giao dịch"*. Bằng chứng nguồn: `B10` §3.1 cho `ASR-01` dẫn `INV-01`, `INV-03`, `INV-04`, `INV-06`; bốn bất biến đó nằm trên **ít nhất ba nhóm root**, `B7-v0.12` khai `INV-03` là **xuyên aggregate**, và `B7` §5 ghi cho `INV-06` *"Thứ tự, retry và phục hồi xuyên root ở B9–B11"*.
- [x] **Gate của điều kiện (b) đã thống nhất về `B11-C` ở mọi chỗ.** `v0.3` sửa ghi chú §4.5(3) nhưng bỏ sót câu ở §4.5(1), nơi còn viết điều kiện *"phải giữ ở `B13`"* và khẳng định luồng tạo đơn **không** thành ứng viên Saga — trái với con số `1 hoặc 2` mà chính vòng đó chốt.
- [x] **Định nghĩa *nhóm luồng* ở §3.4 được vá để phủ hai quan hệ mà nó vốn đang đếm.** `v0.3` định nghĩa nhóm luồng là *"một dòng `E1`–`E12`"* rồi vẫn đếm *luồng giữ chỗ phân tán* của `PA-4` và *vòng đời giữ chỗ* của `PA-5` — hai quan hệ **không phải dòng `E*` nào**, vì `B5` §5.1 chỉ ghi cạnh giữa các context. Vế (b) mới **không đổi con số nào**; nó viết ra luật vốn đã dùng.
- [x] **Ba lời khai xếp hạng sai đã sửa.** `PA-6` khai `RG-2` là *"khối lớn nhất"* — sai theo chính quy tắc đếm root ở §3.1, vì `RG-1a` của `PA-4` là tám nhóm / mười tên. `PA-5` khai tỷ lệ ranh giới trên đầu người *"cao nhất trong tập"* — **hoà với `PA-3`**. `PA-3` khai hai cặp hai chiều là *"ít nhất"* — **hoà với `PA-6`**.
- [x] **Hạt đếm điểm chèn lỗi đã thống nhất về *cặp chiều* cho cả sáu.** `PA-1` khai *"2 biên nội bộ"* (gộp hai cạnh làm một) và `PA-2` khai *"6 biên nội bộ có hướng"* (6 là số cặp **không hướng**; cặp chiều là 9). Xếp hạng không đổi — `PA-1` vẫn ít nhất, `PA-5` vẫn nhiều nhất — nhưng nay sáu ô so được cùng thước.
- [x] **Hai giả định ngầm được viết ra thay vì để ngầm.** (1) Con số 13 cặp chiều của `PA-4` tựa vào giả định `E7`/`E11` đi vào `RG-1b` rồi mới vòng sang `RG-1a`; định tuyến thẳng thì thành 14–15. (2) `E12` **được** tính là điểm sao chép trong khi năm cạnh lệnh khác thì không — lý do đã ghi tại §3.4, và nếu loại `E12` thì thứ tự giữa các phương án vẫn không đổi.
- [x] **Bốn lỗi nghĩa từ điển mới đã sửa, sau khi đối chiếu từng khái niệm với NGHĨA chứ không với tên.** (1) Chữ *"sự kiện"* trần dùng cho **cả hai** nghĩa mà `B2` §1 bắt phân biệt — đối tượng `Sự kiện bán vé` và thông điệp `sự kiện miền` — ở chín chỗ, và cụm `sự kiện miền` **không xuất hiện một lần nào**; nay có quy ước ghi ở §3.2 và legend sáu sơ đồ đã sửa. (2) *khoản thu* được khai là *"cách nói tắt của `Xác nhận thanh toán`"* — sai, vì `Xác nhận thanh toán` là **một trên mỗi đơn** còn *khoản thu* là **nhiều** khi có `Thanh toán trùng`. (3) Điểm sao chép thứ năm của `PA-4` gọi thứ được sao chép là *"trạng thái khả dụng"* — `Khả dụng` là **kết quả xét** trên bốn nhóm đầu vào, và `PA-4` chia bốn nhóm đó làm hai phía nên **không ranh giới nào** tự tính được nó. (4) Chữ *"cửa sổ"* viết trần ở `PA-2` trong khi `B2` có hai mục từ `Cửa sổ mở bán` và `Cửa sổ check-in`.
- [x] **Danh sách cụm không có mục từ `B2` nâng từ bảy lên mười chín.** `v0.3` nâng từ ba lên bảy và vẫn thiếu mười hai — trong đó có cụm dày nhất của cả nhóm (*nguồn cung*) và cụm nằm đúng chỗ `B2-v0.11` **cố ý** bỏ trống sau khi gỡ trợ lý cũ (*cơ chế chẩn đoán*, `RES-034`/`GOV-030`). Định nghĩa *vòng đời giữ chỗ* cũng sửa từ ba mục từ thành **bốn** — `v0.3` bỏ sót `Đơn hàng` dù chính tên luồng mở đầu bằng chữ *tạo đơn*.
- [x] **`B10-v0.9` được mở ở một vòng riêng, không gộp vào vòng này.** `B10` là tạo tác **đã `APPROVED`**; sửa nó là viết lại một tiền đề phân tích ở §2 của nó, nên nó phải bump phiên bản và xin chữ ký riêng, không được đi kèm một vòng sửa `B11-A`. Nội dung: `QS-01` và `QS-06` thiếu root `Giữ chỗ` sau `B7-v0.12`, và ô tự kiểm khai *"`ASR-01` là dòng duy nhất dẫn `INV-03`"* trong khi `QS-01` cũng dẫn.
- [x] **`B11-A-OPEN-03` — phạm vi `R15` — đã đóng** ngày 2026-08-31 (`GOV-072`), sau lượt duyệt `v0.4` một bước. Xem §8.2.

**Chờ người thật**

- [x] **Lê Văn Minh duyệt tập phương án này thành `APPROVED`** ngày 2026-08-31, nguyên văn: *"tôi Duyệt B11-A"* (`GOV-070`). AI không tự đánh dấu thay (`GOV-011`). Chỉ sau khi ô này được tích thì `B11-B` mới được phép mở hồ sơ đối chiếu hiện thực, và phải ở một phiên khác.
- [x] **Lê Văn Minh duyệt lại `B11-A-v0.5`** ngày 2026-09-01 sau đúng hai hiệu đính lời giải thích (`GOV-084`). Sáu phương án, mọi phép đếm, sơ đồ và kết luận khả thi không đổi.
- [x] **`B11-B-v0.6` đã được Lê Văn Minh duyệt** ngày 2026-09-01 (`GOV-085`); cổng `B11-C` sẵn sàng mở. `B11-C` mới chọn phương án.

---

## 11. Phần dùng cho báo cáo

Sau khi được duyệt, phần Thiết kế có thể dùng:

- **bảng bốn con số `quy-trinh` §4.4 của sáu phương án** đặt cạnh nhau — đây là chỗ cho thấy chi phí của mỗi ranh giới thêm vào, đọc so với mốc 0 ở §5;
- **kết quả `N1`–`N4`**: số ứng viên Saga **không tăng theo số ranh giới**, vì hình dạng bù trừ do miền quy định chứ không do cách cắt. `PA-3` với 7 ranh giới và `PA-6` với 5 ranh giới đều có **1** ứng viên Saga, bằng `PA-2` với 4 ranh giới, còn `PA-4` với 5 ranh giới có **2** — **cái quyết định là cắt ở đâu, không phải cắt bao nhiêu**. ⚠️ **Khi dùng luận điểm này trong báo cáo, KHÔNG được dẫn `PA-5` làm ví dụ:** con số ứng viên Saga của `PA-5` là **1 hoặc 2, chưa chốt**, vì nó phụ thuộc một điều kiện mà mô hình miền đã duyệt chưa đỡ được (`B11-A-OPEN-02`);
- **lập luận `INV-06`**: một bất biến trải trên sáu aggregate root là thứ **mọi cách tách `Đơn hàng` đều phải trả giá**, và việc `B11-A` phát hiện điều này khi đối chiếu một lời khai với `B7` §5 là ví dụ tốt cho phần Phương pháp — nó cho thấy bảng truy vết bất biến làm được việc gì mà một sơ đồ ranh giới không làm được;
- **lập luận `INV-11`**: một bất biến duy nhất đủ để phân biệt hai cách gộp, minh họa rằng ranh giới được suy từ bất biến chứ không từ danh từ (`quy-trinh` §4.1);
- **năm giới hạn tự nhận**, mỗi dòng truy về nguồn của nó chứ không về §10: `ASR-06` chưa có ngưỡng nên chưa so sánh được hiệu năng; `ASR-14` không phân biệt được tập phương án này; con số *hợp đồng* là ước lượng; 10 trên 15 dòng ASR cho kết quả giống nhau ở mọi phương án, nên việc chọn ở `B11-C` chủ yếu dựa vào chi phí phối hợp đo được cộng kết quả `B11-B`; và **mức bất đối xứng tải giữa lượt giữ chỗ và lượt thành đơn chưa được đo**, tức lý do mạnh nhất để tách giữ chỗ khỏi đơn hiện chưa có số đỡ.

Không đưa nguyên trạng mã `RG-*`, `E*`, `N*`, sổ phụ thuộc hay trạng thái governance vào báo cáo nếu chúng không giúp giải thích một quyết định.

---

## 12. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B11-A-v0.5` | 2026-09-01 | **Đúng hai hiệu đính lời giải thích đã được Lê Văn Minh duyệt lại (`GOV-084`).** (1) §2 và §8.2 thôi khai phạm vi trần 8 là *đọc trực tiếp câu chữ*; thay bằng dẫn quyết định `GOV-079`: trần 8 chỉ đếm service nghiệp vụ, chatbot và cơ chế chẩn đoán đếm riêng về tài nguyên vận hành. (2) Cảnh báo ba tên mang hai nghĩa ở §4.5(6) áp đủ cho `PA-1`, `PA-3`, `PA-4`, `PA-5`, `PA-6`. Phiên hiệu đính không mở hồ sơ đối chiếu hiện thực, không đọc repository cũ. **Sáu phương án, mọi phép đếm, sơ đồ và kết luận khả thi không đổi.** | Hiệu đính lời giải thích + duyệt lại |
| `B11-A-v0.4` | 2026-08-31 | **Sửa cái mà `v0.3` tự khai sai; không thêm, không bớt, không xếp hạng phương án nào.** Vòng này bắt đầu từ một bản đánh giá ngoài và **bảy vòng rà độc lập**; kết quả: tập sáu phương án và toàn bộ phép đếm **đứng vững**, nhưng khối tự kiểm §10 thì không. *Lỗi thẩm quyền còn sót:* hai chỗ khẳng định atomicity (§6 ghi chú `ASR-01` của `PA-5`; note `pa5.puml`) và một chỗ còn định tuyến gate về `B13` (§4.5(1)). *Phạm vi:* gỡ khẳng định **không nguồn** rằng `R15` áp cho mọi phương án — câu này do `v0.3` thêm vào — và mở **`B11-A-OPEN-03`**, vì chạy phép kiểm thì `PA-1` và `PA-3` không thoả vế đầu của `R15`; khuyến nghị đọc theo phạm vi được ghi ở `CANDIDATE`, không tự đóng. *Nghĩa:* bốn lỗi từ điển mới, trong đó chữ *"sự kiện"* trần dùng cho cả hai nghĩa mà `B2` §1 bắt phân biệt; danh sách cụm không có mục từ nâng từ bảy lên mười chín. *Định nghĩa:* vá §3.4 để phủ hai quan hệ sinh ra từ đường cắt nội bộ context; viết ra hai giả định ngầm (`PA-4` định tuyến `E7`/`E11`; `E12` là điểm sao chép). *Xếp hạng:* ba lời khai *"nhất"* sai hoặc hoà đã sửa; hạt đếm điểm chèn lỗi thống nhất về **cặp chiều**. *Tự kiểm:* 6 ô sai, 4 lạc hậu, 4 tích thiếu bằng chứng — viết lại hết, kèm **một luật mới**: ô tự kiểm không được chứa con số mà chính nó không liệt kê. **Không con số so sánh nào của sáu phương án đổi**; hai bất biến kiểm chứng vẫn đúng ở cả sáu. | Sửa lỗi và siết tự kiểm |
| `B11-A-v0.3` | 2026-08-30 | **Sửa ba tầng lỗi và thêm phương án thứ sáu.** *Lỗi thẩm quyền:* gỡ bốn chỗ dùng *"giao dịch cục bộ"* theo nghĩa phạm vi giao dịch — thứ `B7` §2 viết ra để chặn — và hạ bốn chỗ khai *"phải có một chiều đi bằng sự kiện"* xuống điều kiện cần kiểm ở `B13`; thêm tiền đề đọc ô `Đạt` của `ASR-01` ở §6 và ghi chú đọc `N1` ở §3.4. *Lỗi nghĩa:* gỡ cụm *"chính sách chống đầu cơ"* — chỗ **duy nhất** làm trái một dòng `USER_CONFIRMED` (`BIZ-075`, `GOV-063`) — và ghi hệ quả: đường cắt `RG-2`\|`RG-3` của `PA-5` còn **đúng một** tín hiệu ủng hộ tách; đổi tám chỗ *"hạn mức"* thành `Giới hạn mua` vì từ điển có mục từ `Hạn mức bán` **nghĩa ngược**; sửa lời khai *"`RG-2` giữ cả bốn"*; đưa mục từ `Đơn hết hạn` và `Hết vé` vào phép soi. *Lỗi sự kiện:* **10 trên 15** dòng ASR giống nhau chứ không phải 11 (sai ở ba chỗ); `BC-CAND-02` có **sáu nhóm root / tám tên** chứ không phải *"bảy root"*, kèm quy tắc đếm viết ra; điểm sao chép của `PA-1` **3 → 4** và `PA-4` **4 → 5** sau khi nâng định nghĩa lên §3.4; `PA-4` **ba** cặp hai chiều chứ không phải bốn; `PA-3` **hai** cặp chứ không phải "nhiều nhất"; `PA-5` **bằng** `PA-4` chứ không ít hơn; `PA-1` gánh **10 nhóm root** chứ không phải 9; `PA-3` không còn là phương án duy nhất chạm hợp đồng xuyên ranh giới; `PA-4` cộng **hai** thứ chứ không phải một. *`PA-5`:* ứng viên Saga thành **`1` hoặc `2` — chưa chốt**, vì mô hình miền đã duyệt neo giữ chỗ và mốc hết hạn vào **đơn** (`B2` mục từ `Giữ chỗ`/`Giữ chỗ hết hạn`; `B7` §4.2 khóa nghiệp vụ *đơn được giữ*) nên trạng thái *giữ chỗ chưa có đơn* chưa được mô hình hoá → mở **`B11-A-OPEN-02`**, gate `B11-C`; gỡ hai kết luận có lợi dựa trên con số 1. *`R14`:* viết lại ghi chú §2 quanh cách đọc **có phạm vi** (`GOV-062`), bỏ lập luận sai *"`PA-1`–`PA-3` thoả hiển nhiên"*, **đóng `B11-A-OPEN-01`**, sửa tiêu đề §2. *Thêm:* **`PA-6`** (`RES-053`) — `BC-01` / `BC-02` trọn / `BC-03`+`BC-05` / `BC-04`+`BC-06` / `BC-07`, đủ 12 mục, **5 / 8 / 4 / 12**, 10 bất biến cục bộ / 1 xuyên, **1 ứng viên Saga không kèm điều kiện**, và nó **đóng** vấn đề giữ chỗ mồ côi mà `PA-5` mở ra. *Bổ sung:* cơ chế chẩn đoán ở tiến trình riêng khai là `CANDIDATE` kèm lựa chọn thay thế và giá trên `ASR-11`; `ASR-14` trả lời đủ hai vế bắt buộc cho cả sáu, vế thứ ba vào §8.1; `PA-3`/`PA-4` mục (7) bổ sung đường lấy dữ liệu và credential. *Sơ đồ:* thêm `B11-A-06-container-pa6.puml`; sửa `pa3` thiếu 2 nhánh `E5`, `pa4` thiếu nhánh `E5` và mã `E2`; `pa3`/`pa4` nối đủ nguồn quan sát; thêm chiều callback trên cả sáu sơ đồ container; sửa lệch ký pháp legend trên cả bảy; `-00` đổi *"bốn phương án"* thành *"sáu"*. Ma trận nay **15 × 6 = 90 ô**. **Không tài liệu đã duyệt nào bị sửa; không quyết định nào bị đổi.** | Sửa lỗi + mở rộng tập phương án |
| `B11-A-v0.2` | 2026-08-30 | **Lan truyền `B7-v0.12` và thêm phương án thứ năm.** *Lan truyền:* khai lại đầu vào sang `B7-v0.12`/`B9-v0.8`/`B10-v0.8` (`GOV-058`, `GOV-059`); ledger §3.1 thêm root `Giữ chỗ` vào `BC-CAND-02`; §3.3 đổi root chịu trách nhiệm của `INV-03` sang `Giữ chỗ` và thêm `Giữ chỗ` vào `INV-06`; `PA-4` mục (2) sửa *"bốn root"* thành *"năm root"*. **Không phương án cũ nào phải đếm lại** vì cả bốn để `Đơn hàng` và `Giữ chỗ` cùng một ranh giới. *Thêm mới:* hai ràng buộc `R14`, `R15` từ `RES-050`, `RES-051`; **`PA-5` — 7 ranh giới, giữ chỗ tách khỏi đơn, đối soát gộp vào tiền** — đủ 12 mục, bốn con số **7 / 10 / 7 / 18**, bất biến **8 cục bộ / 3 xuyên**, **1** ứng viên Saga có điều kiện; ma trận §6 thành 15 × 5 với ba ô của `PA-5` được giải thích riêng; sơ đồ `docs/diagrams/src/B11-A-05-container-pa5.puml` — PlantUML 1.2026.6 `-checkonly` thoát mã `0`, tỷ lệ **0,96**, đã mở ảnh ra nhìn và hai lỗi nội dung phát hiện qua ảnh đã sửa; bốn khiếm khuyết bố cục còn lại ghi ở ô tự kiểm §10 chưa tích. *Ledger sửa một lời khai:* cách gộp mà `RES-050` chỉ định **không** làm `INV-06` cục bộ — `Đơn hàng` là một trong sáu root của nó; nguyên văn dòng sổ giữ nguyên, đính chính ghi ở §3.5 kết quả 4. *Mở một điểm:* **`B11-A-OPEN-01`** — `RES-050` có loại `PA-4` khỏi tập hay không; `B11-A` ghi cả hai cách đọc và **không tự phân xử**, giữ `PA-4` trong tập cho tới lượt duyệt. Tài liệu vẫn `DRAFT`, chờ Lê Văn Minh | Lan truyền + áp ràng buộc `USER_CONFIRMED` |
| `B11-A-v0.1` | 2026-08-29 | Bản đầu. Ledger bằng chứng từ `B5-v0.14`/`B7-v0.11`/`B10-v0.7`; bốn nhóm phân loại luồng `N1`–`N4`; bốn phương án `PA-1`–`PA-4` mỗi phương án 12 mục; đường cơ sở `PA-0` tách khỏi tập lựa chọn theo `GOV-055`; ma trận 15 ASR ba chiều không cộng điểm; phép kiểm `GOV-035`; bảng phụ thuộc còn mở; phụ lục `R0` §3 và phép thử độc lập. **Sơ đồ dạng mã** cho ngữ cảnh hệ thống và cho từng phương án đặt tại `docs/diagrams/src/B11-A-*.puml` — lựa chọn trình bày do Lê Văn Minh chốt ngày 2026-08-29 khi được trình ba hướng; đây là quy ước trình bày nên không ghi vào sổ quyết định | Tạo mới |
