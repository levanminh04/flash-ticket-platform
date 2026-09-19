# Trạng thái triển khai plan

- **Cập nhật hiện hành 2026-09-18:** tên/nhiệm vụ theo [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md), nhóm **Minh, Sơn, Tuấn, Tuyến** theo [roles.md](roles.md). Minh lead giai đoạn đầu và chính RCA; mobile chỉ làm khi thực sự thừa thời gian (`PRJ-035`).
- **Giai đoạn hiện tại:** Giai đoạn 5 — đầu hiện thực. `B11-C-v0.3` và baseline B12–B16 đã duyệt (GOV-144). Commit `091fca1` ngày 18/09 đã tạo 5 project nghiệp vụ + API Gateway, Config Server, Discovery Server; mã ứng dụng hiện là bộ khung. Không dùng dòng “chưa tạo application” trong mốc 14/09 bên dưới làm hiện trạng.
- **Kiểm chứng:** chưa có bằng chứng build/service/E2E/tải/RCA FlashTicket PASS trong lượt cập nhật ngữ cảnh; không kiểm AWS trực tiếp. Kế hoạch được duyệt không đồng nghĩa kết quả đã đạt.
- **Mục tiêu/phạm vi:** A1-v0.3, A2-v0.3, A3-v0.7, A4-v0.4, A5-v0.4, A6-v0.7 `REVIEW_READY` sau DT18; các phê duyệt cũ không được gắn cho diễn giải mới. Giữ thiết kế kỹ thuật đã duyệt; giao thức đo phần mở rộng của nhiệm vụ còn OPEN tại A3/A6.
- **Nghiên cứu:** A7-v0.3, A8-v0.3, A9-v0.4 `DRAFT`; A10-v0.5 và E1-v0.3 `REVIEW_READY`. E1 kiểm định 11 CSV/990 dòng kết quả; MyRCA là CANDIDATE, hiệu quả chưa được chứng minh. Các phiên bản cũ bên dưới là lịch sử.

## Lịch sử theo mốc — không dùng thay khối hiện hành ở trên

- **Cập nhật kiến trúc 2026-09-04:** `B11-C-v0.3` đã `APPROVED` và đóng B11. Kiến trúc đích là `PA-6` với `event-service`, `booking-service`, `payment-service`, `ticket-service`, `user-service`; một Saga do `payment-service` điều phối; RCA riêng chỉ đọc, lớp giải thích dùng Gemini API; hai máy không HA. `GOV-097` thay benchmark chặn B11 bằng ngân sách mục tiêu khoảng 6 GiB tiến trình/container mỗi máy, thử tải thật ở Giai đoạn 5–6; `GOV-098` cho phép nâng instance AWS tạm thời nếu cần. Bốn ADR đích đều **`Chấp nhận`**.
- **Cập nhật nghiên cứu 2026-09-02:** đã kiểm định 11 tệp CSV/990 ca và tạo `E1-v0.1` `REVIEW_READY`; `A7-v0.3`, `A8-v0.3`, `A9-v0.3` ở `DRAFT`, `A10-v0.4` ở `REVIEW_READY`. `RES-054` chỉ chốt cách định vị **“tổ hợp có kiểm chứng”**; thiết kế MyRCA là `CANDIDATE` (`RES-055`) và hiệu quả/tập node vẫn `OPEN` (`RES-056`). Cập nhật này không thay trạng thái `B11-C`.
- **Cập nhật 2026-09-14 — GOV-144/GOV-145:** Minh duyệt B6-v0.15/B7-v0.13/B8-v0.13 → B12-v0.2 → B13-v0.1 → B14-v0.1 → B16-v0.1, cùng B15/READY-v0.1 và bộ dẫn xuất. Cho phép commit/push bộ hệ thống. 47 bảng SQL/467 cột và 4 collection; kế hoạch kiểm được duyệt, kết quả service/E2E/RCA vẫn NOT RUN.
- **Giai đoạn ghi nhận tại 14/09:** Giai đoạn 5 — hiện thực được phép bắt đầu theo baseline đã duyệt (GOV-144). Chưa tạo application hoặc triển khai AWS. Xem readiness §5 để code luồng chính; đầu vào môi trường còn OPEN được giải quyết ở đúng lát cắt.

  ⚠️ **Giai đoạn 3 còn đúng một việc: chốt `A3` và `A6`** (`A5-v0.3` đã `APPROVED` tại `GOV-054`). Hai giai đoạn **chồng lấn** theo `docs/quy-trinh-lam-viec.md` PHẦN 6 — *"Các giai đoạn có thể chồng lấn"*; việc còn lại **không chặn `B11-A`**, vì `B10-OPEN-11` ghi rõ nội dung dẫn `MT-3` *"đứng được trên `NFR-04` đã duyệt"*, và `GOV-050` đã cho phép `B11-A` coi bảng ưu tiên là đầu vào đã chốt. Khai Giai đoạn 4 ở đây là **khai đúng loại công việc đang làm**, không phải tuyên bố Giai đoạn 3 đã đóng.

  **Khối bên dưới ghi lịch sử Giai đoạn 2 và giữ nguyên.** Giai đoạn 2 **đã hoàn tất phần phân tích miền** — bảy tạo tác `B2-v0.10`, `B3-v0.10`, `B4-v0.14`, `B5-v0.12`, `B6-v0.12`, `B7-v0.7`, `B8-v0.8` được duyệt đúng thứ tự chuỗi ngày 2026-08-22 (`GOV-023`).

  **Hiện hành: `GOV-033` ngày 2026-08-27 — Lê Văn Minh duyệt toàn bộ chuỗi 11 tạo tác đúng thứ tự.** `A1-v0.2`, `A2-v0.2`, `A4-v0.2`, `B2-v0.12`, `B3-v0.11`, `B4-v0.15`, `B5-v0.14`, `B6-v0.14`, `B7-v0.11`, `B8-v0.11`, `B9-v0.5` — tất cả `APPROVED` **tại thời điểm đó**.

  **Phiên bản và trạng thái hiện hành — đọc dòng này chứ không đọc khối trên:** `B7-v0.12` · `B8-v0.12` · `B9-v0.8` · `B10-v0.9`, **cả bốn `APPROVED`** (`GOV-058`, `GOV-059`, `GOV-071`). `B10-v0.9` chỉ hoàn tất lan truyền root `Giữ chỗ`; vẫn **18 kịch bản, 13 · 2 · 3, 15 ASR**.

  ✅ **Hệ quả cho `B11-A`: bảng ưu tiên NAY LÀ đầu vào đã chốt.** `GOV-037` đóng cùng lúc `B10` được duyệt, đúng gate mà chính nó đặt (`GOV-048`). **18 mức ưu tiên — 13 Cao · 2 Trung bình · 3 Thấp — và 15 yêu cầu kiến trúc thôi là `CANDIDATE`.**

  **Cập nhật 2026-08-28, vòng ba phiếu nghiên cứu.** Ba quyết định mới: `MT-5` chuyển sang nhóm Vòng 1 (`RES-045`) — sửa chỗ `A3` và `A6` nói ngược nhau về lớp giải thích; `MT-1`–`MT-3` **đổi vai** thành tiêu chí nghiệm thu sản phẩm (`RES-046`), câu chữ không đổi một chữ; `MT-R1`–`MT-R4` **được Lê Văn Minh xác nhận** (`RES-047`), giảng viên **vẫn chưa**. Phép đối chiếu mục tiêu với danh sách ASR đã chạy được sau khi `B10` duyệt — kết quả tại `A3` §5.1, **mọi mục tiêu phục vụ ít nhất một ASR**, `A3-OPEN-02` đóng. Chiều ngược lại lộ một khoảng trống thật: **`ASR-14` — kiểm quyền theo quan hệ sở hữu — không truy về mục tiêu nào và cũng không nằm trong bốn tiêu chí nghiệm thu**, dù vừa được nâng lên mức Cao. Mở tại `A3-OPEN-07`, cần Lê Văn Minh quyết trước khi chốt `A3`.

  **Giai đoạn 2 đóng. Giai đoạn 3 nay CHỈ CÒN MỘT VIỆC: chốt `A3`, `A5`, `A6`** — ba phiếu này vẫn `DRAFT`, người duyệt và ngày duyệt đều trống. `B10` đã hoàn tất và được duyệt ngày 2026-08-28 (`GOV-043`). Tài liệu quy trình chủ đặt việc chốt mục tiêu, đối tượng và phạm vi nghiên cứu **cùng giai đoạn với `B9`/`B10`**, và ba phiếu đó là phần Mở đầu của quyển báo cáo; `ASR-06` còn đang dẫn `MT-3` của `A3` khi `A3` chưa duyệt (`B10-OPEN-11`).

  **`B10` đã mở được.** Tiêu chí xếp hạng chốt ngày 2026-08-27 tại `GOV-034`: xếp `Cao/Trung bình/Thấp` **theo mức tác động tới kiến trúc** — *“nếu đòi hỏi này đổi thì kiến trúc có phải khác đi không?”*. `GOV-035` ghi rõ mức `Thấp` **không** đồng nghĩa bị bỏ: kịch bản `Thấp` không vào ASR nhưng vẫn là yêu cầu và kịch bản kiểm chứng. Cảnh báo chặn tại phiếu `B10` đã gỡ.

  **`GOV-025` đã đóng** ngày 2026-08-27 tại `GOV-036`: `BIZ-148` giữ nguyên hiệu lực, `QS-10` không đổi, nên **tập kịch bản đem đi xếp hạng chốt ở đúng 18 mục** `QS-01`–`QS-18`. Ba điều kiện của `B10` — tiêu chí (`GOV-034`), nghĩa của mức Thấp (`GOV-035`), tập đầu vào (`GOV-036`) — **đều đã đủ**, và `B10` đã được dựng ở `docs/quality-scenarios/B10-quality-priorities-and-asrs.md`, trạng thái `DRAFT`, **chờ Lê Văn Minh duyệt**. Bản hiện hành là `B10-v0.4`.

  ⚠️ **`GOV-035` từng bị dùng quá phạm vi, và chỗ đó nay đã xử lý xong.** `GOV-035` chỉ nói về **mức `Thấp`**; `B10-v0.3` nới nó sang cả mức **Trung bình** rồi gán cho cùng dòng `USER_CONFIRMED`, khiến `NFR-09` và `NFR-10` bị đẩy khỏi danh sách ASR bằng một luật chưa ai chốt. `v0.4` trả `GOV-035` về đúng phạm vi và mở `B10-OPEN-10`. **`v0.5` đóng điểm đó bằng cách tháo gốc chứ không xác nhận luật:** `GOV-040` đưa `NFR-10` lên mức Cao nên nó có `ASR-14` riêng, và đưa `NFR-09` xuống mức Thấp theo quyết định có chủ ý của chủ đồ án, kèm `GOV-041` buộc công bố mức độ hoàn thiện của nó như **một giới hạn tự nhận** trong báo cáo. Mức Trung bình nay chỉ còn hai dòng, cả hai đã được `ASR-02` dẫn làm bằng chứng phụ.

  **`B10-v0.5` xếp 18 kịch bản thành 13 Cao / 2 Trung bình / 3 Thấp và sinh 15 ASR** *(bản hiện hành là `B10-v0.7`; ba con số và 15 ASR không đổi)* — 10 có động lực là kịch bản mức Cao, 3 từ ràng buộc đã chốt, **2 ràng buộc phủ định** (quyền sở hữu dữ liệu chưa chốt, và năng lực chèn lỗi không được đóng mất). Mức ưu tiên là `CANDIDATE` (`GOV-037`), không phải điều đã được xác nhận. Bốn hotspot của `B4` §10 đều được phủ, gồm `ASR-01` riêng cho `HOT-02` mà `B9-OPEN-06` đang đòi — `HOT-03` được ghi là **phủ có điều kiện** vì `B7` §6 còn một điểm `OPEN` bên trong nó. `v0.4` bổ sung bảng phủ cho **sáu bất biến** `INV-01`, `INV-05`–`INV-09` mà `B4` §10 cũng định tuyến tới `B10`; `v0.3` chỉ lập bảng cho hotspot.

  **Một phát hiện khi dựng `B10`:** phiếu Tầng B liệt kê *"quyền sở hữu dữ liệu"* trong nhóm ràng buộc **đã chốt**, nhưng nó **chưa chốt** — `AGENTS.md` đặt việc này ở `B12`, `B5` §9 ghi rõ đang chờ `B12`, và `GOV-028` còn `OPEN` **chính vì** quy tắc *mỗi service sở hữu schema riêng* đã bị phát biểu trước gate. `ASR-13` do đó viết thành ràng buộc phủ định, đặt ở mục §3.3 riêng: `B11-A` **không được giả định** một cách chia sở hữu nào.

  **Vòng `v0.4` sửa cách xử lý phát hiện đó.** `B10` là tạo tác **cấp 5**, phiếu Tầng B là **cấp 4**; viết đúng ở `B10` và ghi lại phát hiện ở tệp trạng thái này — **cấp 6** — là để một khiếm khuyết của nguồn cấp trên đứng nguyên. `AGENTS.md` buộc *"Edit the authoritative artifact first"*. Nay tách thành hai dòng: **`B10-OPEN-08`** cho việc sửa phiếu Tầng B ở đúng chỗ của nó, và **`B10-OPEN-05`** cho `GOV-028` — kèm phát hiện rằng `GOV-028` **chưa khai một nơi thứ ba** có thẩm quyền cao hơn cả hai nơi nó đã khai: `docs/quy-trinh-lam-viec.md` GIAI ĐOẠN 4, *Điều kiện sẵn sàng*. Cả hai gate **trước `B11-A`**.

  **Ba dòng `OPEN` mới đáng chú ý khác của `v0.4`.** `B10-OPEN-07`: xung đột chưa phân xử về *mã tương quan* — `NFR-06` để mở, nhưng `B9-v0.5` `QS-13` (cũng `APPROVED`) đã chốt nó trong ô bất biến, và `docs/quy-trinh-lam-viec.md` PHẦN 6 — **thẩm quyền cấp 3** — liệt kê nó là điều kiện tối thiểu; `v0.3` tự phân xử theo `NFR-06` và không ghi lại xung đột. `B10-OPEN-09`: ràng buộc `R0` số 8 được khai là không giữ được nhưng không ai mở `OPEN`, trái chính `R0` §3. `B10-OPEN-10`: `NFR-09` và `NFR-10` hiện không có ASR nào, do một luật chưa được xác nhận.

  **Mức thay đổi không đồng đều.** `B3` và `B6` chỉ khai lại phiên bản đầu vào, không đổi một chữ nội dung. Năm tài liệu còn lại có gỡ nội dung thật: từ điển mất 5 mục từ, `B4` mất §8.3, `B5` còn **bảy** context, `B7` còn **bảy** sơ đồ và **48** tên lớp, `B8` mất `NFR-07` và thêm `NFR-12`. **Không nội dung nghiệp vụ bán vé nào bị đụng tới** — thứ bị gỡ đều là từ vựng và ranh giới của trợ lý cũ.

  **B9 đã `APPROVED`** (`B9-v0.7` ngày 2026-08-29, **18** kịch bản; dòng này ghi `B9-v0.5` cho tới `GOV-053`) tại `docs/quality-scenarios/B9-quality-scenarios.md`; theo Tầng B §3.3 nó **không được `APPROVED`** trước khi cả chuỗi được duyệt lại.

## Giai đoạn 4 — Kiến trúc

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| **B11-A — Tập phương án kiến trúc độc lập** | **`APPROVED`** (`B11-A-v0.5`) | `docs/architecture/B11-A-independent-alternatives.md`. Lê Văn Minh duyệt lại ngày 2026-09-01 (`GOV-084`) sau đúng hai hiệu đính lời giải thích: dẫn `GOV-079` cho phạm vi trần tám và áp cảnh báo tên mang hai nghĩa đủ cho năm phương án `PA-1`, `PA-3`, `PA-4`, `PA-5`, `PA-6`. Sáu phương án `PA-1`–`PA-6`, đường cơ sở `PA-0`, mọi phép đếm và sơ đồ không đổi. |
| Nguồn sơ đồ `B11-A` | Đã dựng và đã nhìn ảnh | `docs/diagrams/src/B11-A-00-system-context.puml` và **sáu** sơ đồ container `B11-A-01`…`-06`. PlantUML 1.2026.6: **7/7** `-checkonly` thoát mã `0`; tỷ lệ rộng/cao 0,95 · 1,55 · 1,17 · 1,62 · 1,68 · 1,29 · **1,55**, cả bảy trong dải Tầng C. Sơ đồ `-06` của `PA-6` thêm ngày 2026-08-30. **Việc nhìn ảnh ở vòng `v0.3` bắt thêm ba lỗi nội dung trên sơ đồ cũ** — `pa3` thiếu 2 nhánh `E5`, `pa4` thiếu 1 nhánh `E5` và mã `E2`, `pa3`/`pa4` chỉ nối 2/7 và 2/5 ranh giới vào kho dữ liệu quan sát — cả ba đã sửa. Legend của cả bảy sửa một **lệch ký pháp thật**: nét đứt từng được khai là *“sự kiện hoặc đọc”* trong khi `E1`–`E4` đọc thuần vẽ nét liền. Khiếm khuyết bố cục còn lại nằm ở một ô tự kiểm chưa tích của `B11-A` §10 |
| **B11-A — thêm phương án thứ năm** | **Xong ngày 2026-08-30**, ở một phiên sạch | `PA-5` — 7 ranh giới có giữ chỗ riêng (`RES-050`, `RES-051`), dựng được sau khi `B7-v0.12` `APPROVED` (`GOV-058`) làm `Giữ chỗ` thành aggregate root. Bốn con số **7 / 10 / 7 / 18**; bất biến **8 cục bộ / 3 xuyên**. ⚠️ **Con số ứng viên Saga của `PA-5` đã đổi ở `v0.3`: `1` hoặc `2`, chưa chốt** — xem dòng dưới |
| **B11-A — sửa `v0.2` và thêm phương án thứ sáu** | **Xong ngày 2026-08-30**, ở một phiên sạch (`GOV-065`) | Ba tầng lỗi đã sửa. **Thẩm quyền:** gỡ bốn chỗ dùng *"giao dịch cục bộ"* theo nghĩa phạm vi giao dịch — đúng lỗi `B7` §2 viết ra để chặn — và hạ bốn chỗ khai *"phải có một chiều đi bằng sự kiện"* xuống **điều kiện cần kiểm ở `B13`**. **Nghĩa:** gỡ cụm *"chính sách chống đầu cơ"* (`GOV-063`), chỗ **duy nhất** làm trái một dòng `USER_CONFIRMED`, và ghi hệ quả — đường cắt then chốt nhất của `PA-5` còn **đúng một** tín hiệu ủng hộ tách; đổi tám chỗ *"hạn mức"* thành `Giới hạn mua`. **Sự kiện:** **10/15** dòng ASR giống nhau chứ không phải 11; điểm sao chép `PA-1` **3→4**, `PA-4` **4→5**; `PA-4` **ba** cặp hai chiều chứ không phải bốn. **Thêm `PA-6`** (`RES-053`) — `BC-02` trọn vẹn, **5 / 8 / 4 / 12**, **10 cục bộ / 1 xuyên**, **1 ứng viên Saga không kèm điều kiện**; nó **đóng** vấn đề giữ chỗ mồ côi mà `PA-5` mở ra |
| **`B11-A-OPEN-01`** | ✅ **ĐÓNG** ngày 2026-08-30 | `GOV-062` — Lê Văn Minh giữ `PA-4` trong tập, tức `RES-050` đọc theo **phạm vi hình dạng đang được thêm vào**. Cách đọc tuyệt đối sẽ xoá **bốn trên năm** phương án và biến `B11-A` thành tài liệu một phương án. Cùng cách đọc này làm `PA-6` hợp lệ |
| **`B11-A-OPEN-02`** | ✅ **ĐÓNG theo đường không chọn `PA-5`** | `PA-6` được chọn tại `GOV-086`, nên kiến trúc đích không dùng đường cắt giữ chỗ–đơn. Khoảng trống mô hình không bị sửa và không sinh Saga thứ hai. |
| **B11-B — Đối chiếu tính khả thi** | **`APPROVED`** (`B11-B-v0.6`) | Kết luận giữ nguyên: không phương án nào bị loại. `B11-B-OPEN-08` đóng bằng `GOV-091`; `-03` đóng như blocker B11 bằng `GOV-097`/`GOV-098`; chỉ `-02` đi tiếp vào `B12`. |
| **B11-C — Chốt kiến trúc và ADR** | **`APPROVED`** (`B11-C-v0.3`) — **đã đóng gate** | `PA-6`; năm service `event`/`booking`/`payment`/`ticket`/`user`; một Saga do `payment-service` điều phối; RCA riêng chỉ đọc, lớp giải thích dùng Gemini API; hai máy không HA. `ADR-001`–`ADR-004` đều `Chấp nhận`. Phần tác động B11 của `A6-v0.6` và phép thử độc lập RCA đã được duyệt/hoàn tất. |
| **B12 — Dữ liệu/schema đích** | **APPROVED B12-v0.2** | 47 bảng SQL/4 collection; dictionary và ERD đồng bộ; quyết định nghiệp vụ tại §9 đã cập nhật. Cấu hình môi trường thật và kiểm service còn thiếu. |
| B13/B14/B15 | APPROVED v0.1 (GOV-144) | Đã có hợp đồng, sequence và kế hoạch kiểm; Saga/outbox/inbox đã phản ánh trong B12. |
| B16/readiness | APPROVED v0.1 (GOV-144) | Chuẩn quan sát và danh sách Spring project/dependency/thứ tự code; chưa nghiệm thu telemetry thật. |

> ⚠️ **Cập nhật 2026-08-29, cuối ngày — `B11-A-v0.1` chưa đủ, cần một vòng nữa trước khi duyệt.** Chủ đồ án chốt mục tiêu hình dạng **7 ranh giới nghiệp vụ** với một ranh giới giữ chỗ riêng (`RES-050`, `RES-051`). Tập phương án hiện có **không chứa hình dạng đó**, và nó **chưa dựng được** cho tới khi `Giữ chỗ` thành aggregate root — vì một ranh giới triển khai không được cắt ngang một aggregate.
>
> **Việc phải làm, theo thứ tự — tiến độ tính tới cuối ngày 2026-08-29:**
> 1. ~~Sửa `B7` để nâng `Giữ chỗ` thành root (`RES-052`)~~ — **xong**, ở một phiên sạch.
> 2. ~~Duyệt lại `B7`~~ — **xong**, `B7-v0.12` `APPROVED` (`GOV-058`).
> 3. ~~`B9`, `B10` và cửa nối khai lại đầu vào~~ — **xong**: `B9-v0.8` và `B10-v0.8` đều `REVIEW_READY`, `LK-v0.4` đã cập nhật. `B9` thêm `Giữ chỗ` vào ô *Tạo tác* của `QS-01` và `QS-06`; `B10` không đổi một mức ưu tiên hay một ASR nào.
> 4. ~~Duyệt `B9-v0.8` rồi `B10-v0.8`~~ — **xong** ngày 2026-08-29 (`GOV-059`), đúng thứ tự.
> 5. ~~Cập nhật ledger `B11-A` — thêm root `Giữ chỗ` vào §3.1, sửa định tuyến `INV-03` ở §3.3 — rồi thêm phương án thứ năm kèm sơ đồ container~~ — **xong** ngày 2026-08-30 ở một phiên sạch (`B11-A-v0.2`). Ngoài hai việc đã liệt kê, vòng đó còn phải sửa thêm hai chỗ mà kế hoạch chưa lường: `INV-06` cũng nhận thêm root `Giữ chỗ` ở `B7-v0.12` §5, và `PA-4` mục (2) phải đổi *"bốn root"* thành *"năm root"* theo đó.
> 6. ~~Lê Văn Minh trả lời `B11-A-OPEN-01`~~ — **xong** ngày 2026-08-30 (`GOV-062`).
> 7. ~~Sửa ba tầng lỗi của `v0.2` và thêm `PA-6`~~ — **xong** ngày 2026-08-30 ở một phiên sạch (`B11-A-v0.3`, `GOV-065`).
> 8. ~~Lê Văn Minh duyệt `B11-A` → `B11-B` ở một phiên khác~~ — **xong** ngày 2026-08-31: duyệt tại `GOV-070`, `B11-B-v0.1` soạn tại `GOV-073` ở một phiên khác.
> 9. ~~Sửa hai lời khai `B11-A`, để Lê Văn Minh duyệt lại, rồi duyệt `B11-B-v0.6`~~ — **xong** ngày 2026-09-01: `B11-A-v0.5` `APPROVED` tại `GOV-084`; `B11-B-v0.6` `APPROVED` tại `GOV-085`. Cập nhật 2026-09-04: `B11-A-OPEN-02` đóng do không chọn `PA-5`; `B11-B-OPEN-08` đóng bằng nghĩa hẹp; `B11-B-OPEN-03` đóng như blocker của B11 bằng `GOV-097`/`GOV-098`. **B11-C-v0.3 đã đóng và B12 được phép mở.**
>
> ✅ **Điểm mở mà `v0.2` tạo ra nay đã đóng, và cách nó đóng có hệ quả cho cả tập** — xem `GOV-062` và bảng trên. Khối dưới đây giữ nguyên làm lịch sử của vòng `v0.2`.
>
> ⚠️ **`B11-A-v0.2` mở một điểm mà `v0.1` không có, và nó chặn lượt duyệt.** `R14` — chép từ `RES-050` — đòi ba root `Giới hạn mua`, `Khuyến mãi` tổng lượt và `Lượt dùng khuyến mãi` nằm *"cùng ranh giới với nguồn cung **và** giữ chỗ"*. `PA-4` đặt nguồn cung và giữ chỗ ở **hai** ranh giới khác nhau nên **không có ranh giới nào ứng được với cụm đó** — `PA-4` không thỏa `R14` dù gộp ba root về phía nào. Câu hỏi *`RES-050` có loại `PA-4` khỏi tập hay không* là câu hỏi về **phạm vi của một quyết định `USER_CONFIRMED`**, nên `B11-A` ghi cả hai cách đọc và **không tự phân xử**; `PA-4` được giữ trong tập cho tới khi có câu trả lời, vì kế hoạch được duyệt nói *"thêm phương án thứ năm"* chứ không nói bớt một phương án.
>
> ⚠️ **Một lời khai kèm `RES-050` đã bị ledger `B11-A` đính chính.** Phần lý do trình kèm dòng sổ nói việc gộp ba root làm *"`INV-01`, `INV-04` và `INV-06` đều cục bộ trong một ranh giới"*. Đọc thẳng `B7-v0.12` §5: `INV-06` có **sáu** root và `Đơn hàng` là một trong sáu, nên khi `Đơn hàng` đứng riêng thì `INV-06` **vẫn xuyên ranh giới**. `INV-01` và `INV-04` thì đúng là cục bộ. **Quyết định không sai — chỉ một vế của lời khai kèm theo là quá mạnh**; nguyên văn dòng sổ giữ nguyên theo quy tắc 3, đính chính nằm ở `B11-A` §3.5 kết quả 4.
>
> **Lan truyền nhẹ hơn tưởng:** `INV-03` chỉ được `B4` §10 định tuyến tới `B7`; `B9` §2 ghi nó *"không thuộc B9"*; `B10` §4.2 không định tuyến nó. Nên **không kịch bản chất lượng nào và không yêu cầu kiến trúc nào bị đổi** — `B9` vẫn 18 kịch bản, `B10` vẫn 13/2/3 và 15 ASR.
>
> ⛔ **Ràng buộc phiên.** Phiên ngày 2026-08-29 đã mở repository cũ theo cho phép của chủ đồ án (`GOV-057`), nên nó **không được sản xuất tạo tác `FORMATION`**. Mọi việc sửa `B7`, `B9`, `B10`, `B11-A` phải làm ở **một phiên sạch khác**. Phiên đó chỉ ghi sổ quyết định. ✅ **`B7-v0.12` đã được soạn đúng như vậy:** một phiên khác trong cùng ngày, chưa mở `B5.5` và chưa đọc repository cũ; bằng chứng ở ô tự kiểm tương ứng của `B7` §8. `B9`, `B10` và `B11-A` vẫn còn nguyên ràng buộc này.

**Điều kiện cách ly đã thỏa cho cả `B11-A-v0.1` và `v0.2`.** `v0.1` được soạn ở một phiên **chưa mở hồ sơ đối chiếu hiện thực và chưa đọc repository cũ** — đúng điều kiện `GOV-044` đặt ra và `B10` §8 giữ làm một ô tự kiểm chưa tích; ghi tại `GOV-056`. `v0.2` được soạn ở **một phiên sạch khác** ngày 2026-08-30, cũng chưa mở hai nguồn đó; ghi tại `GOV-060`, kèm khai thẳng một khác biệt — phiên `v0.2` có đọc sổ quyết định ở trạng thái hiện tại nên đã nhìn thấy `GOV-057` và `PRJ-008`, và phép thử cho thấy không mục nào của `PA-5` dẫn về hai dòng đó.

**Bốn lời khai đã bị ledger `B11-A` §3 sửa** *(dòng thứ tư thêm ở `v0.3`: `BC-CAND-02` có **sáu nhóm root / tám tên**, không phải “bảy root” — `v0.2` khai một con số không khớp cách đếm nào, nên `v0.3` viết ra quy tắc đếm)*, ghi lại để không ai đọc theo bản cũ: một cách gộp lõi giao dịch giữ **11/11** bất biến cục bộ chứ không phải 9/11; luồng **hủy sự kiện không phải ứng viên Saga** — `ASR-05` cấm đảo ngược nên nó là phản ứng một chiều có thử lại tiến; và **`INV-06` không thành cục bộ** dưới cách gộp mà `RES-050` chỉ định, vì `Đơn hàng` là một trong sáu root của nó.

## Giai đoạn 0

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| Repository ĐATN riêng | Hoàn thành | `flash-ticket-platform` là không gian hiện thực và đánh giá chính thức |
| Không đưa `.env`/bí mật vào repo mới | Hoàn thành bước đầu | `.gitignore`, `.env.example`; tiếp tục quét trước mỗi lần nhập mã |
| Bộ plan A/B/C, tài liệu chủ và B5.5 | Đã hòa giải phương pháp | Giai đoạn 2 chỉ tạo mô hình miền ứng viên; B11-A/B/C mới hình thành, kiểm tra khả thi và chốt kiến trúc |
| Nơi ghi quyết định và mẫu ADR | Hoàn thành | `docs/adr/`, ADR-000 |
| Quy ước ký hiệu/đặt tên | Hoàn thành | Tầng C mục 3.1–3.3 |
| Bảng phân vai | Hoàn thành baseline | Ba thành viên đã xác nhận phân công ban đầu; phạm vi backend cụ thể Tuyến hỗ trợ sẽ chốt khi chia workload |
| Chiến lược tái sử dụng frontend | `USER_CONFIRMED` | Dùng frontend hiện tại làm nền nhưng điều chỉnh theo mô hình đích; không cho frontend ràng buộc phương án tách service ở B11-A; kiểm tra khả năng tái sử dụng tại B11-B/Giai đoạn 5 (`PRJ-004`, `PRJ-005`) |
| Khung báo cáo không khóa số chương | Hoàn thành bước đầu | `docs/report/report-outline.md`. Ba tệp Word cũ **đã loại khỏi phạm vi** ngày 2026-08-26 (`RES-027`), lý do ghi tại `docs/report/README.md` |
| Xin đề cương và file mẫu ĐATN hiện hành | Chưa gửi | Nội dung soạn sẵn tại `docs/coordination/questions-for-advisor.md`; tin nhắn vòng 1 đã sửa tên đề tài theo `DH-TEN` ngày 2026-08-26 |

## Điều kiện chuyển sang Giai đoạn 1

Có thể bắt đầu các công việc khảo sát/phân tích không phụ thuộc mẫu trình bày ngay. Cổng Giai đoạn 0 chỉ hoàn tất đầy đủ sau khi:

1. Nhóm gửi câu hỏi về đề cương/file mẫu cho cô Liên.
2. **Đạt:** Ba thành viên đã xác nhận bảng phân vai ban đầu.

## Giai đoạn 1

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B1 — Khảo sát luồng công khai | `APPROVED` baseline | `evidence/external-survey/B1-public-ticketing-survey-2026-08-09.md`; đủ dùng cho B2, ảnh công khai là bổ sung không chặn |
| B1 — Quy trình chẩn đoán hiện tại | `APPROVED` baseline, bằng chứng còn giới hạn | Quy trình và INC-01 đã được xác nhận; cần thêm tối thiểu 2 ca/tái hiện hoặc công bố giới hạn một ca và không đo thời gian cải thiện |
| A1 — Bối cảnh và tính cấp thiết | `APPROVED` (`A1-v0.2`) | Tái baseline theo `DH-TEN` (`RES-032`): mạch năm nước giữ nguyên bối cảnh nghiệp vụ, đích chuyển sang chẩn đoán bằng đồ thị phụ thuộc. `A1-v0.1` `APPROVED` ngày 2026-08-13 giữ hiệu lực tới `RES-031` |
| A2 — Phát biểu vấn đề | `APPROVED` (`A2-v0.2`) | Tái baseline theo `DH-TEN` (`RES-032`): phát biểu 150 từ viết lại quanh chẩn đoán nguyên nhân gốc; gỡ ô tự kiểm *"không trở thành đề tài độc lập thứ hai"*. Vẫn phải tái kiểm tra sau B10 và khi B11-C có tác động |
| A4 — Câu hỏi nghiên cứu | `APPROVED` (`A4-v0.2`) | Tái baseline theo `DH-TEN` (`RES-032`): vẫn một câu trung tâm và hai câu phụ, nhưng đặt quanh `DH-MT1`–`DH-MT3`; gỡ cụm *"nhánh hỗ trợ"*. Ngưỡng chờ B9–B10, cách trình bày chờ mẫu/giảng viên |
| A3 — Mục tiêu nghiên cứu | `DRAFT` (`A3-v0.6`) | `MT-1`–`MT-4` giữ nguyên cho Vòng 2, `MT-5` sửa cả hai vế ngày 2026-08-27 (`RES-041`); bổ sung `MT-R1`–`MT-R4` cho Vòng 1, chưa được xác nhận |
| A5 — Đối tượng nghiên cứu | **`APPROVED`** (`A5-v0.3`) | Lê Văn Minh duyệt 2026-08-29 (`GOV-054`); `A5-OPEN-03` vẫn mở nhưng thuộc giảng viên nên không chặn. Đối tượng là cơ chế chẩn đoán nguyên nhân gốc; **FlashTicket là phương tiện**, theo `DH-MT1` |
| A6 — Phạm vi | `DRAFT` (`A6-v0.6`) | Đã đồng bộ B11-C-v0.3; phần §4 chịu tác động kiến trúc đã được Lê Văn Minh duyệt lại. Tài liệu vẫn chờ duyệt toàn bộ và chờ giảng viên xác nhận cách đặt phạm vi (`A6-OPEN-01`), nhưng các điểm này không chặn B12 |

> **Giai đoạn 1 đã mở lại phần đối tượng và phạm vi** sau định hướng của giảng viên ngày 2026-08-22 và quyết định tách hai bộ tài liệu ngày 2026-08-24 (`RES-004`, nay đọc theo `RES-028`: hai bộ độc lập về **nhịp làm việc và cổng kiểm soát**, mỗi bộ không được dùng tài liệu bộ kia để tự quyết định phần trách nhiệm riêng — **không** độc lập về đề tài).
>
> **Cập nhật 2026-08-26.** Câu *"`A1`, `A2`, `A4` chưa bị sửa; `B2`–`B8` giữ nguyên `APPROVED`"* **không còn đúng**. `RES-032` đã tái baseline `A1`/`A2`/`A4` và lan truyền sang `B5`–`B8`. Lý do tái baseline được ngay: điều ba phiếu đó chờ là **phản hồi của giảng viên về trục đề tài**, mà chính thư ngày 2026-08-22 là phản hồi đó. Thứ còn chờ cô là cách đặt phạm vi (`A6-OPEN-01`) và việc dùng `RE2` thay bộ cô gửi (`A8-OPEN-04`), **không phải tên trục**.

## Bộ tài liệu nghiên cứu chẩn đoán nguyên nhân gốc

Từ 2026-08-24, bộ này ở `docs/research-rca/` và không nằm trong bảy giai đoạn của Tầng B. Nó độc lập về **nhịp làm việc và cổng kiểm soát**, **không** độc lập về đề tài: `DH-MT1` đặt hệ thống đặt vé vào mục tiêu đầu tiên của chính đề tài chẩn đoán.

`RES-028` viết lại ranh giới này theo **quyền quyết định** chứ không theo **quyền đọc**: mỗi bộ được nhận tài liệu của bộ kia qua đúng một cửa, nhưng không được dùng nó để tự quyết định phần thuộc trách nhiệm riêng của mình. Hai cửa là `docs/research-rca/R0-boi-canh-va-rang-buoc.md` §3 và `docs/project/lien-ket-rca.md`. Cách viết này **không nới lỏng** lệnh cách ly `B5.5`/repo cũ ở mục *Legacy implementation quarantine*.

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| R0 — Ngữ cảnh và ràng buộc liên tài liệu | `DRAFT` (`R0-v0.4`) | Mười hai ràng buộc gửi tới `B9`–`B16`; ba ràng buộc 1, 2, 5 đã viết lại thành **yêu cầu quan sát** thay vì quyết định kiến trúc (`RES-029`), nên `R0-OPEN-02` đóng và `R0-OPEN-06` mở. Chờ Lê Văn Minh xác nhận (`R0-OPEN-01`) |
| A7 — Từ vựng nền | `DRAFT` (`A7-v0.3`) | Tách bốn mức dễ lẫn: service hệ thống, thực thể vận hành, operation và telemetry feature; đóng việc xác minh tối đa 376 metric và phân biệt BARO gốc với cấu hình RCAEval |
| A8 — Khảo sát bộ dữ liệu | `DRAFT` (`A8-v0.3`) | Phân biệt Online Boutique 11 service chính chủ với 12 định danh đánh giá, Train Ticket 64 service với ít nhất 68 tên ứng viên; schema trace và giấy phép LEMMA vẫn `OPEN` |
| A9 — Độ đo thực nghiệm | `DRAFT` (`A9-v0.3`) | `AC@1` là độ đo chính; ca thiếu hạng tính thất bại; bootstrap theo cụm `service × fault`, McNemar exact và Holm; seed/runtime/candidate manifest còn thiếu |
| A10 — Khảo sát phương pháp | `REVIEW_READY` (`A10-v0.4`) | Sáu mục chứa bảy phương pháp chạy được; MM-CIRCA chỉ metric + log; không gọi cặp đa nguồn là 2×2; PC+RandomWalk hiện không đánh giá được tác dụng của đồ thị |
| E1 — Kiểm định RCAEval và khả thi MyRCA | `REVIEW_READY` (`E1-v0.1`) | 990 ca đã tính lại, công khai SHA-256, coverage, thống kê cụm và mâu thuẫn; MyRCA chỉ là thiết kế `CANDIDATE`, chờ ablation/held-out trước khi chấp nhận |
| Báo cáo hai tuần Markdown cũ | `HISTORICAL_SUPERSEDED` | Giữ làm lịch sử; kết luận hiện hành ở `E1` và bản Word `_v2` |
| Thực nghiệm MyRCA | Chưa bắt đầu | Tuần 1 dựng adapter/tái lập baseline; tuần 2 ablation/fusion/refusal. Không cần chờ backend để chạy dữ liệu công bố; kiểm thử FlashTicket cần telemetry và fault injection sau |

**Ràng buộc bắt buộc lên Tầng B:** mỗi gate từ `B9` tới `B16` phải đối chiếu `docs/research-rca/R0-boi-canh-va-rang-buoc.md` §3 trước khi chốt, và ghi kết quả đối chiếu vào phần tự kiểm (`RES-015`).

## Điều kiện hoàn tất Giai đoạn 1

1. **Đạt:** Chủ đồ án đã duyệt A1, A2 và phạm vi A4 ở mức baseline; sau B10 phải tái kiểm tra trước khi chốt A1–A6.
2. **Đạt:** Khảo sát B1 công khai được xác nhận đủ dùng; ảnh có thể bổ sung sau.
3. **Chưa đạt:** Baseline chẩn đoán cần thêm ít nhất 2 ca thật/tái hiện được; nếu không có, chủ đồ án phải xác nhận giới hạn một ca và không đo thời gian cải thiện.
4. **Đạt:** Phát biểu vấn đề không phụ thuộc tên công nghệ.

## Sổ quyết định — ảnh chụp lịch sử ngày 2026-08-27

Không dùng số đếm và danh sách bên dưới làm backlog hiện hành. Hủy/follow/trạng thái/tiền/topology/Saga đã có quyết định sau thay thế; xem decision-register và B12 §9. Lịch sử giữ để truy vết, không mở lại lựa chọn.

Đếm lại bằng script trên trạng thái hiện tại của sổ: **32 dòng** mang nhãn `OPEN`/`CANDIDATE`, trong đó **20 dòng đã bị một quyết định sau thay thế** và chỉ giữ nhãn cũ theo quy tắc 3. Còn **12 dòng** mang nhãn mà chưa bị thay thế.

Nhưng con số phải báo cáo là **11 vấn đề thực sự còn mở, cộng một lỗi ghi sổ**: `RES-008` ghi *"thư định hướng chưa được lưu"*, việc đó **đã xong** và `RES-019` tuyên bố đóng nó ở cột bằng chứng — nhưng cột *Thay thế quyết định* để trống nên script vẫn đếm nó là mở.

> **`GOV-026` nay đã lạc số.** Dòng đó ghi *"13 dòng đã bị thay thế vẫn giữ `CANDIDATE`/`OPEN`"* — đúng ở thời điểm viết, nay là **20**. Con số trôi vì mỗi vòng lại thêm dòng thay thế. Không sửa dòng `GOV-026` (quy tắc 3 giữ nguyên bản ghi); ghi số hiện hành ở đây. Trong số các dòng đã bị thay thế, 20 dòng còn mang nhãn `OPEN`/`CANDIDATE` nên mới gây nhầm khi đọc.

| Mã | Nội dung | Chặn gì |
|---|---|---|
| `GOV-037` | Xếp hạng của `B10` là phân tích `CANDIDATE`, chưa phải lựa chọn đã được xác nhận. `v0.4` giữ nguyên phân bố `v0.3`. **`GOV-040` ngày 2026-08-28 chốt mức cho ba dòng cụ thể** — phân bố thành **13 Cao / 2 Trung bình / 3 Thấp** — nên `GOV-037` nay chỉ còn hiệu lực cho **mười lăm dòng còn lại** | Khi duyệt `B10` cho mười lăm dòng còn lại; ba dòng đã chốt tại `GOV-040` |
| `RES-039` | Ai dùng kết quả RCA và bằng hình thức nào | `B6`/`B8` vòng sau; đóng nó có thể phát sinh giao diện → `GOV-019` |
| `GOV-028` | Quy tắc thiết kế đích được phát biểu **trước** gate `B12`, ở hai nơi | Vòng riêng, trước `B11-A` |
| `GOV-029` | Nợ chạy lại toàn bộ `evaluation-cases.md` | Trước khi coi bộ luật là ổn định |
| `GOV-026` | Sổ quyết định khó đọc — 20 dòng đã thay thế vẫn mang trạng thái cũ | Không chặn gate nào; chặn việc đọc nhanh |
| `GOV-024` | Organizer có được gửi lại yêu cầu hủy sau khi bị từ chối không | Nếu chốt "có" thì mở lại `B6`/`B8` |
| `BIZ-123`, `BIZ-130` | Sở hữu dữ liệu tỷ lệ phí; tên và cách biểu diễn trạng thái đơn | `B12`/`B13` |
| `RES-006`, `RES-024` | Đổi vai `MT-1`–`MT-3`; cấu trúc quyển báo cáo cuối | Sau `B10`; sau khi có mẫu ĐATN |
| `RES-009` | Bốn phiếu bộ RCA chiếm mã `A7`–`A10`, trùng nghĩa với Tầng A | Trước Giai đoạn 7 |
| ~~`RES-008`~~ | **Không phải việc còn mở.** Ghi *"thư định hướng chưa được lưu"* — thư **đã được lưu** tại `docs/evidence/advisor-direction/`; `RES-019` tuyên bố đóng nó ở cột bằng chứng nhưng không ghi vào cột *Thay thế quyết định*. Đây là **lỗi ghi sổ**, không phải công việc. Ví dụ cụ thể của `GOV-026` | Không chặn gì |

Ngoài sổ, mỗi tạo tác còn sổ `OPEN` riêng — đáng chú ý: `A3-OPEN-04` (`MT-5` còn phát biểu trục cũ, `RES-002` bảo vệ nguyên văn), `B9-OPEN-01`–`B9-OPEN-08`, `B10-OPEN-01`–`B10-OPEN-06`, `B8-OPEN-01`/`B8-OPEN-05`, `R0-OPEN-01`/`R0-OPEN-03`/`R0-OPEN-05`–`R0-OPEN-07`.

## Quyết định từng mở tại mốc lịch sử trên

Danh sách sau giữ ngữ cảnh lịch sử; trạng thái hiện hành tại B12 §9/readiness. Không coi năm service hoặc một Saga đã duyệt là câu hỏi cần chọn lại.

- Danh sách service cụ thể và vị trí aggregate tồn kho vé.
- Tối đa ba luồng nào thực sự cần Saga.
- Cách bố trí các thành phần trên hai EC2.
- ~~Workflow cuối của trợ lý chẩn đoán và tập ca đánh giá.~~ **Chuyển giao 2026-08-27** sang bộ tài liệu RCA, mã `R0-OPEN-07` (`RES-034`). Chưa được trả lời, chỉ đổi nơi quản lý.
- Cỡ tập ca chẩn đoán: bổ sung tối thiểu 2 ca hay chấp nhận giới hạn một ca và bỏ kết luận về thời gian.
- Phần backend cụ thể Tuyến sẽ hỗ trợ sau khi nhóm chia workload.
- Cách đồng bộ/kho lưu hồ sơ danh tính nghiệp vụ và nghĩa kỹ thuật của nơi tạo admin đầu tiên; vòng đời và tập trường nghiệp vụ organizer đã được chốt.
- Tên/chuyển trạng thái và cách biểu diễn trạng thái đơn (`BIZ-130`) tại B12/B13; nghĩa hoàn khoản thu trùng/đến muộn đã được khóa. Cách tính số người theo dõi chờ B12/B13.
- Quyền sở hữu dữ liệu vật lý của tỷ lệ phí nền tảng (`BIZ-123`); nghĩa nghiệp vụ cố định sau phê duyệt đã được chốt.
- Trường/payload/thời gian lưu dấu vết và tập trường nhạy cảm cần lọc (`B8-OPEN-05`) tại B13/B16; nghĩa nghiệp vụ và yêu cầu khử/che đã được chốt.

## Giai đoạn 2

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B2 — Từ điển miền | `APPROVED` (`B2-v0.12`) | Duyệt ngày 2026-08-22. `v0.10` **chỉ bổ sung** mục từ `Yêu cầu hủy sự kiện` mà vòng kiểm toán phát hiện thiếu; không đổi nghĩa mục từ nào đang dùng. `v0.11` gỡ 5 mục từ ở §6 — từ vựng trợ lý cũ (`RES-034`); tám mục chung còn nguyên |
| B3 — Quy trình nghiệp vụ | `APPROVED` (`B3-v0.11`) | Duyệt ngày 2026-08-22 sau B2. `v0.10` không đổi nội dung, chỉ đồng bộ khai đầu vào. `v0.11` chỉ khai lại đầu vào; 0 dòng nội dung trợ lý |
| B4 — Bản đồ sự kiện miền | `APPROVED` (`B4-v0.15`) | Duyệt ngày 2026-08-22 sau B3. `v0.14` không thêm/bớt/đổi nghĩa sự kiện nào — `A09`/`A10` vốn đã mô tả yêu cầu hủy. `v0.15` gỡ §8.3 và bảng `T01`–`T04`; bốn dòng sự kiện A/B/C/D không đổi |
| B5 — Bản đồ bounded context | `APPROVED` (`B5-v0.14`) | `v0.12` `APPROVED` ngày 2026-08-22 sau B4. `v0.13` viết lại **một đoạn** ở §2 dẫn `A2`/`A4` cho khớp bản tái baseline (`RES-032`). **Tám context, phân loại cốt lõi/hỗ trợ/chung và bản đồ quan hệ không đổi**. `v0.14` gỡ `BC-CAND-08` → **bảy** context; bảy context còn lại và mọi cạnh không đổi |
| B6 — Use case và đặc tả | APPROVED (B6-v0.15; GOV-144) | Đồng bộ xác nhận hủy/follow/tiền/email/ghế/trạng thái theo GOV-143. Không thay service hoặc 11 bất biến; Minh đã duyệt phiên bản cập nhật tại GOV-144. |
| B7 — Aggregate và bất biến | APPROVED (B7-v0.13; GOV-144) | Đồng bộ xác nhận hủy/follow/tiền/email/ghế/trạng thái theo GOV-143. Không thay service hoặc 11 bất biến; người thật duyệt phiên bản cập nhật trước khi đóng gate phụ thuộc. |
| B8 — Bảng FR/NFR | APPROVED (B8-v0.13; GOV-144) | Đồng bộ xác nhận hủy/follow/tiền/email/ghế/trạng thái theo GOV-143. Không thay service hoặc 11 bất biến; người thật duyệt phiên bản cập nhật trước khi đóng gate phụ thuộc. |
| A3 — Mục tiêu nghiên cứu | `DRAFT` (`A3-v0.5`) | Lê Văn Minh xác nhận `MT-1`–`MT-4` định tính và chốt câu chữ mới của `MT-5` (`RES-041`); ngưỡng MT-3/MT-4/MT-5 cố ý chờ B9/B10 nên không chặn việc bắt đầu B9 sau khi B8 được duyệt |

## Cổng và giới hạn Giai đoạn 2

- Chuỗi phụ thuộc là `B2 baseline → B3 → B4 → B5 → B7`; cho phép tạo nháp phía sau nhưng không được `APPROVED` khi đầu vào bắt buộc chưa được duyệt.
- AI không tự duyệt tài liệu do chính nó tạo; mỗi tạo tác ghi trạng thái, người duyệt, ngày và phiên bản đầu vào.
- Giai đoạn 2 không tạo service vật lý, Saga, schema đích hoặc ADR kiến trúc và không đọc B5.5 để hình thành phương án.
- Hai trần `≤ 8 service nghiệp vụ` và `≤ 3 Saga` là ràng buộc đánh giá tại B10/B11, không phải số lượng phải dùng hết.
- Ngày 2026-08-21, sau khi B2–B5 được duyệt, Lê Văn Minh chốt bỏ vai trò `SUPER_ADMIN` khỏi phạm vi đồ án (`BIZ-146`, thay thế `BIZ-133`). Vai trò này không có nhiệm vụ nào được mô tả trong B2–B5 và không truy được về sự kiện miền nào ở B4. Vì vậy B2 lên `B2-v0.9` và B4 lên `B4-v0.9`, cả hai trở lại `REVIEW_READY` và cần Lê Văn Minh duyệt lại theo thứ tự B2 → B4. B3-v0.9 và B5-v0.7 không đổi nội dung nghiệp vụ — B3 không nhắc bộ vai trò và B5 chỉ dùng cụm “bộ role” chung — nhưng auditor cưỡng chế quy tắc “đầu vào chưa `APPROVED` thì hạ nguồn không được `APPROVED`”, nên cả bốn tài liệu B2 → B5 cùng trở lại `REVIEW_READY`. Lê Văn Minh đã duyệt lại toàn chuỗi B2-v0.9 → B3-v0.9 → B4-v0.9 → B5-v0.7 trong cùng ngày 2026-08-21.
- B2-v0.5, B3-v0.5 và B4-v0.5 từng được Lê Văn Minh duyệt tuần tự ngày 2026-08-18. Vòng hiệu đính ngày 2026-08-20 phát hiện các dòng PRJ/BIZ mới đã bị gắn `USER_CONFIRMED` quá mức, B4/B5 không nhất quán và checklist B5 có dấu tick không có bằng chứng. Sau vòng kiểm toán và duyệt nội dung ngày 2026-08-21, chuỗi đã đạt B2-v0.8, B3-v0.8, B4-v0.8 và B5-v0.6 `APPROVED`; vòng bỏ `SUPER_ADMIN` sau đó nâng phiên bản hiện hành lên B2-v0.9, B3-v0.9, B4-v0.9 và B5-v0.7 rồi được duyệt lại đúng thứ tự. Tám context không được thêm, bớt, đổi tên hay đổi phân loại; các `OPEN` còn lại có owner/gate và không chặn B7. Giai đoạn 0–1 vẫn còn việc xin mẫu báo cáo và xử lý phạm vi tập ca chẩn đoán nhưng không chặn phân tích Giai đoạn 2.
- Lê Văn Minh đã xác nhận trực tiếp chuỗi `B4-v0.11 → B5-v0.9`, gồm `E01`–`E05`; các bản hiệu đính tạm thời chỉ dựa vào commit cũ không còn là kết luận hiện hành (`GOV-021`).
- Ngày 2026-08-22, B4-v0.13/B5-v0.11 lan truyền các lựa chọn đã xác nhận: chuyển trạng thái vé đơn điệu hấp thụ kích hoạt lặp, hệ thống không kiểm sức chứa vật lý, hồ sơ organizer tối giản, danh sách audit đóng và giữ khử/che dữ liệu nhạy cảm. Không lựa chọn service, schema, API, Saga hoặc cơ chế phối hợp nào được đưa vào Giai đoạn 2.

## Cổng sang B9 — đã đạt ngày 2026-08-22

1. **Đạt:** chuỗi `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12` được duyệt lại đúng thứ tự ngày 2026-08-22.
2. **Đạt:** `B6-v0.12` `APPROVED` ngày 2026-08-22.
3. **Đạt:** `B7-v0.7` `APPROVED` ngày 2026-08-22; `B8-v0.8` `APPROVED` sau B6.
4. **Đạt có điều kiện, tính tới 2026-08-26.** Vòng `RES-032` đưa `B5`, `B6`, `B7`, `B8` trở lại `REVIEW_READY`. **Nội dung nghiệp vụ của cả bốn không đổi** — chúng chỉ khai lại phiên bản đầu vào, cộng một đoạn dẫn `A2`/`A4` ở `B5` §2 — nên B9 giữ được bản `DRAFT` đang có. Nhưng theo Tầng B §3.3, **B9 không được `APPROVED`** trước khi Lê Văn Minh duyệt lại chuỗi `B5-v0.13 → B6-v0.13 → B7-v0.10 → B8-v0.10`.
5. **B9 mở.** Đường dẫn canonical là `docs/quality-scenarios/B9-quality-scenarios.md`; đầu vào là B7 (bất biến/aggregate), B8 (FR/NFR kèm cách đo) và A3 `MT-1`–`MT-5`.

Việc phải làm ngay khi vào B9, đã biết trước từ vòng kiểm toán:

- `NFR-05` đo trên tập ca lỗi có nguyên nhân biết trước, mà baseline chẩn đoán vẫn **“Chưa đạt”** ở Giai đoạn 1 (`NFR-07` đã gỡ theo `RES-034`): cần thêm tối thiểu 2 ca thật/tái hiện được, hoặc Lê Văn Minh xác nhận chấp nhận giới hạn một ca và bỏ kết luận về thời gian cải thiện.
- Hai phép thử tranh chấp mà B7 §4.4 yêu cầu tách riêng: nhiều thiết bị quét **cùng một vé** (`INV-09`), và nhiều thiết bị quét **các vé khác nhau của cùng một đơn** để đo tranh chấp trên root `Phát hành vé`.
- `GOV-024` còn `OPEN` nhưng không chặn B9. `A3-v0.5` đã đủ mục tiêu định tính; `A3-OPEN-01` và `B8-OPEN-01` là chính phần ngưỡng B9/B10 phải làm, không phải blocker cần đóng trước B9.

### Kiểm toán bằng chứng đầu vào B9 — 2026-08-22

| Điều kiện | Kết quả kiểm độc lập trên trạng thái hiện tại |
|---|---|
| Độ phủ use case → yêu cầu | Đủ 30/30 use case B6 xuất hiện trong B8; có đúng 12 ca đặc tả đầy đủ, còn 18 ca chỉ liệt kê |
| Độ phủ bất biến | Đủ 11/11 bất biến B4 trong bảng truy vết B7; không thiếu hoặc đổi mã. `INV-01` đã được trả về đúng câu chữ B4 tại `B7-v0.6` |
| Khả năng đo của NFR | Có 11 NFR duy nhất; 11/11 có nội dung, cách đo và nguồn gốc |
| Dẫn quyết định | 125/125 mã `BIZ`/`PRJ`/`GOV`/`RES` được dẫn trong B4–B8/A3 đều tồn tại trong sổ quyết định |
| Định tuyến điểm mở | Đủ 7/7 điểm `OPEN` hiện hành của B4 và 7/7 điểm `OPEN` hiện hành của B5 xuất hiện trong B7 với gate tiếp theo. `B5-OPEN-09` chỉ xuất hiện ở nhật ký phiên bản B5 mà không có ở §8.1 lẫn §8.2 — cần Lê Văn Minh xác nhận trước khi coi con số 7 là đầy đủ |
| Sơ đồ | 4/4 nguồn B6 và 8/8 nguồn B7 parse/render thành công. B7 đo lại bằng PlantUML 1.2026.6 sau khi sửa: tỷ lệ 0,74 · 1,32 · 0,55 · 1,50 · 1,02 · 1,67 · 1,63 · 1,84 |
| Từ vựng | 57/57 tên lớp B7 là mục từ nguyên văn của B2-v0.10. Phép kiểm **nghĩa** được tách riêng tại B7 §7 và đã sửa bốn chỗ trùng tên lệch nghĩa |
| Đầu vào B9 | `A3-v0.6` tồn tại (`DRAFT`); `A5-v0.3` đã `APPROVED`; `A6-v0.6` là `DRAFT`, trong đó phần tác động B11 tại §4 đã được duyệt lại. Đường dẫn canonical của B9 là `docs/quality-scenarios/B9-quality-scenarios.md`; phần ngưỡng còn thiếu chính là đầu ra vòng đo thử |

Kết luận kiểm toán ngày 2026-08-22, sau vòng đọc toàn văn: **một lỗi thượng nguồn đã được leo thang và khắc phục**. `Yêu cầu hủy sự kiện` là sự kiện miền đã duyệt ở B4 `A09`/`A10` với trạng thái `REJECTED` riêng theo `BIZ-098` và yêu cầu dấu vết theo `FR-68`, nhưng thiếu mục từ ở B2-v0.9 nên B7 không có ngôn ngữ để đặt ranh giới. Lê Văn Minh chọn bổ sung mục từ vào B2; `B2-v0.10` khắc phục, `B7-v0.7` thêm aggregate ứng viên tương ứng, và cả chuỗi trở lại `REVIEW_READY` để duyệt lại. Ngoài nó, không còn thiếu tạo tác hoặc mâu thuẫn nội dung chặn B9. Các ô phê duyệt của người thật là cổng còn lại; chúng không được AI tự đánh dấu thay.

Hai điểm ở B5 cũng đã được Lê Văn Minh cho phép sửa và nằm trong `B5-v0.12`: ba tham chiếu `B4-v0.11`/`B4-v0.9` còn sót, và tình trạng của mã `B5-OPEN-09`. Tra toàn bộ lịch sử repo cho thấy phát biểu của `B5-OPEN-09` chưa từng vào commit nào, nên nó được ghi là **mã đã cấp nhưng mất phát biểu** thay vì bị đoán lại nội dung.

Còn một điểm `OPEN` mới, không chặn B9: `BIZ-097`–`BIZ-099` không nói organizer có được gửi lại yêu cầu hủy sau khi bị từ chối hay không. B7 dùng bội số `0..*` để không tiền-chốt; owner Lê Văn Minh, gate B6/B8.
