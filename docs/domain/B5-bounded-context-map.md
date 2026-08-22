# B5 — Bản đồ Bounded Context ứng viên

- Phiên bản: `B5-v0.12`
- Trạng thái: `APPROVED`
- Người duyệt: Lê Văn Minh
- Ngày duyệt: 2026-08-22, sau `B2-v0.10` → `B3-v0.10` → `B4-v0.14` (bản `B5-v0.11` cũng được duyệt ngày 2026-08-22)
- Baseline phê duyệt: `B5-v0.9` đã được duyệt sau `B4-v0.11`; `B5-v0.11` lan truyền các quyết định nghiệp vụ được Lê Văn Minh xác nhận ngày 2026-08-22 và đã được duyệt cùng ngày.
- Đầu vào và phiên bản: `docs/glossary.md` — `B2-v0.10`, `APPROVED` ngày 2026-08-22; `docs/domain/B3-business-processes.md` — `B3-v0.10`, `APPROVED` ngày 2026-08-22; `docs/domain/B4-domain-event-map.md` — `B4-v0.14`, `APPROVED` ngày 2026-08-22; `docs/research/A2-problem-statement.md` và `docs/research/A4-research-questions-draft.md` — baseline `APPROVED` ngày 2026-08-13, **chỉ** làm căn cứ cho tiêu chí phân loại cốt lõi/hỗ trợ/chung tại §2 và phải tái kiểm tra sau B10; `docs/project/decision-register.md` — đọc riêng trạng thái của từng dòng được dẫn, không gom dòng `OPEN` với quyết định đã xác nhận
- Phân lớp: `FORMATION`

## 1. Mục đích và giới hạn

B5 gom các sự kiện, chính sách và ngôn ngữ đã được mô hình hóa ở B4 thành các **bounded context ứng viên**. Khi B4 chưa `APPROVED`, kết quả B5 chỉ là bản nháp dẫn xuất và không được coi là đã dùng đầu vào được duyệt. Mục tiêu là chỉ ra nơi một mô hình nghiệp vụ có nghĩa nhất quán và các quan hệ ngữ nghĩa cần tiếp tục kiểm tra ở B7/B10/B11.

Mọi context, tên context và phân loại trong tài liệu này đều ở trạng thái `CANDIDATE`. B5 **không** quyết định:

- một bounded context có trở thành một service hoặc tiến trình triển khai riêng hay không;
- aggregate, transaction hoặc vị trí bảo vệ bất biến nằm ở đâu;
- context/service nào sở hữu schema, bảng hoặc CSDL vật lý;
- quan hệ nào dùng API, sự kiện tích hợp, message broker, Saga hay cơ chế giao dịch phân tán;
- tên class, endpoint, topic, payload hoặc ADR kiến trúc.

B5-v0.12 kế thừa nội dung đã được duyệt của B2-v0.9, B3-v0.9 và B4-v0.13 theo đúng thứ tự, và đã được duyệt lại cùng chuỗi `B2-v0.10 → B3-v0.10 → B4-v0.14` ngày 2026-08-22. Dòng sự kiện hỗ trợ `E01`–`E04` đã được ánh xạ mà không tạo ranh giới mới; `E05` chỉ bổ sung truy vết cho quan hệ theo dõi organizer. Tám context và phân loại kế thừa tập ứng viên đã hình thành từ bằng chứng miền; các quyết định mới chỉ đóng nghĩa nghiệp vụ về tài khoản/organizer, trạng thái sự kiện, sức chứa địa điểm, tính cố định của tỷ lệ phí và kích hoạt vô hiệu vé lặp, không tạo, gộp, tách hoặc đổi phân loại context. A2/A4 baseline chỉ xuất hiện ở §2 làm căn cứ cho tiêu chí phân loại. Nguồn hiện thực và luồng đối chiếu không được dùng làm bằng chứng ở B5; quyết định tái sử dụng frontend chỉ xuất hiện như rào chắn không để giao diện ép khuôn thiết kế đích.

## 2. Phương pháp gom cụm và trạng thái bằng chứng

Một cụm được đề xuất thành context khi các sự kiện trong cụm dùng chung phần lớn các yếu tố sau:

1. ngôn ngữ và vòng đời nghiệp vụ;
2. quy tắc thay đổi trạng thái;
3. tác nhân hoặc chủ thể quyết định quy tắc;
4. nhịp thay đổi và các bất biến cần tiếp tục làm rõ;
5. quan hệ nhân quả đã xuất hiện trong B4.

Các nhãn được dùng như sau:

| Nhãn | Cách dùng trong B5 |
|---|---|
| `FACT` | Sự kiện, quyền hoặc quan hệ đã có nguồn trực tiếp tại B2–B4 |
| `CANDIDATE` | Tên context, cách gom cụm và phân loại cốt lõi/hỗ trợ/chung cần người thật duyệt |
| `OPEN` | Thiếu nghĩa nghiệp vụ hoặc cần gate sau; không được dùng để tạo hướng đặt aggregate/service ưu tiên |

Phân loại **cốt lõi** chỉ áp dụng cho cụm trực tiếp phục vụ trục nhất quán và độ tin cậy trong vòng đời vé ở phạm vi nghiên cứu. **Hỗ trợ** là năng lực cần cho sản phẩm hoặc vận hành nhưng không phải nơi tập trung đo các bất biến chính. **Chung** là năng lực phổ biến, không tạo khác biệt nghiệp vụ riêng của vòng đời vé.

Trục nghiên cứu này không do B5 tự đặt. A2 baseline `APPROVED` phát biểu bài toán là chuỗi *giữ chỗ → thanh toán → phát hành → kiểm soát vào cửa* cùng các bất biến cốt lõi của chuỗi đó; A4 baseline `APPROVED` ghi rõ nhánh trợ lý chẩn đoán là *nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé*.

Bản thân tiêu chí phân loại vẫn là `CANDIDATE`. A2/A4 mới ở mức baseline và chính chúng phải tái kiểm tra sau B10; B9/B10 mới là nơi chốt thuộc tính chất lượng. Nếu B10 làm đổi trục, toàn bộ cột phân loại ở §3 phải được rà lại trước khi B5 giữ nguyên trạng thái.

## 3. Danh sách bounded context ứng viên

| ID | Bounded context ứng viên | Phân loại ứng viên | Bằng chứng trực tiếp từ B4 | Ngôn ngữ và trách nhiệm nhất quán | Giới hạn tại B5 |
|---|---|---|---|---|---|
| `BC-CAND-01` | **Vòng đời sự kiện và cấu hình bán** | `CANDIDATE — Hỗ trợ` | `A01`–`A13`; B4 §8.4 | Bản nháp, thông tin địa điểm và phân loại, cấu hình bán, khuyến mãi được cấu hình, gửi duyệt, phê duyệt, công bố/tìm kiếm, cửa sổ bán, yêu cầu hủy và hủy sự kiện | Không quyết định cách biến trạng thái thời gian thành sự kiện kỹ thuật; không điều phối hậu quả hủy xuyên context; nguồn cung do organizer cấu hình và không bị kiểm theo sức chứa vật lý của địa điểm (`BIZ-149`) |
| `BC-CAND-02` | **Mua vé và cam kết nguồn cung** | `CANDIDATE — Cốt lõi` | `A07`–`A08` là điều kiện đầu vào; `B01`, `B02`, `B08`, `B09`, `B13` | Lựa chọn vé, khả dụng, giữ chỗ, đơn, giới hạn mua, áp dụng/lượt dùng khuyến mãi, hết hạn và hủy đơn | Ranh giới giữa nguồn cung, giữ chỗ và đơn vẫn là `B5-OPEN-02`; không gán aggregate hoặc vị trí triển khai |
| `BC-CAND-03` | **Thanh toán và hoàn tiền** | `CANDIDATE — Cốt lõi` | `B03`–`B05`, `B10`–`B11`, `C01`–`C06` | Lần thanh toán, xác nhận khoản thu, thanh toán đến muộn/trùng, yêu cầu hoàn logic, kết quả hoàn và tiến độ xử lý nhiều khoản thu | Không sở hữu nghĩa quyền vào cửa của vé; không chọn bộ điều phối, retry, Saga hoặc giao thức với cổng thanh toán |
| `BC-CAND-04` | **Quyền tham dự và kiểm soát vào cửa** | `CANDIDATE — Cốt lõi` | `B06`, `B12`, `B14`, `D01`–`D03` | Phát hành vé, quyền QR, vô hiệu vé, yêu cầu/kết quả check-in và trạng thái đã sử dụng | Không coi hoàn tiền là trạng thái vé; ranh giới aggregate và quyết định nguyên tử chờ B7/B9/B10 |
| `BC-CAND-05` | **Đối soát và chi trả** | `CANDIDATE — Hỗ trợ` | `C07`–`C10` | Sổ cái đối soát, doanh thu thực thu, phí nền tảng, điều kiện chi trả, xác nhận đối soát và `PAID` | Không quyết định cách dựng read model, schema hoặc tích hợp báo cáo cổng thanh toán; chuyển tiền thật nằm ngoài hệ thống |
| `BC-CAND-06` | **Giao nhận thông tin vé** | `CANDIDATE — Chung` | `B07`; phân biệt với `B06`/`B12` tại B4 §5.2 | Gửi thông tin vé, giao nhận thất bại và gửi/tải lại | Không tạo hoặc vô hiệu quyền tham dự; không khẳng định đây là service thông báo riêng |
| `BC-CAND-07` | **Hồ sơ tài khoản và quyền nghiệp vụ** | `CANDIDATE — Hỗ trợ` | B4 §8.1 và §8.4 | Buyer mặc định, bộ role/đa vai trò, hồ sơ organizer `PENDING`/`ACTIVE`/`REJECTED`, quan hệ buyer theo dõi organizer, quyền sở hữu đơn/sự kiện và điều kiện được phép phát lệnh | Keycloak quản lý danh tính/vòng đời tài khoản; hồ sơ ứng dụng giữ tên tổ chức, mô tả ngắn và lý do từ chối khi có; B5 không chốt đồng bộ, API, schema hoặc đơn vị triển khai |
| `BC-CAND-08` | **Chẩn đoán sự cố** | `CANDIDATE — Hỗ trợ, có nhánh đánh giá riêng` | B4 §8.3, `T01`–`T04` | Sự cố, dấu vết, context chẩn đoán, nguyên nhân khả dĩ, bước kiểm tra và phản hồi xác minh | `T01`–`T04` vẫn là sự kiện ứng viên; context chỉ đọc và chưa chốt workflow, dữ liệu lưu hoặc đơn vị triển khai |

Số lượng tám context trong bảng **không** phải mục tiêu tám service. B10/B11 phải đánh giá cách gộp/tách vật lý từ B5, B7 và ASR; B5 không tạo ưu tiên triển khai nào.

## 4. Ranh giới ngôn ngữ cần giữ

| Khái niệm ở context nguồn | Nghĩa tại context liên quan | Điều không được đồng nhất |
|---|---|---|
| **Khuyến mãi được cấu hình** tại `BC-CAND-01` | **Khuyến mãi được áp dụng/lượt dùng được giữ** tại `BC-CAND-02` | Định nghĩa mã và hiệu lực không đồng nghĩa với việc một đơn đã sử dụng mã |
| **Sự kiện khả dụng để bán** tại `BC-CAND-01` | **Vé/ghế/số lượng khả dụng cho một lựa chọn** tại `BC-CAND-02` | Đủ điều kiện mở bán không chứng minh còn nguồn cung cho một yêu cầu cụ thể |
| **Thanh toán đã được xác nhận** tại `BC-CAND-03` | Điều kiện để **vé được phát hành** tại `BC-CAND-04` | Khoản thu hợp lệ không phải bản thân quyền tham dự |
| **Hoàn tiền thành công** tại `BC-CAND-03` | Quyền vào cửa chỉ đổi theo nguyên nhân gốc tại `BC-CAND-04` | Hoàn tiền không tự tạo trạng thái “vé đã hoàn” |
| **Vé đã được phát hành** tại `BC-CAND-04` | **Thông tin vé đã được gửi** tại `BC-CAND-06` | Phát hành thất bại khác giao nhận thất bại |
| **Đối soát/PAID** tại `BC-CAND-05` | Kết quả tổng hợp từ khoản thu/hoàn tại `BC-CAND-03` | Đánh dấu chi trả không sửa lịch sử thanh toán hoặc hoàn tiền |
| **Cửa sổ bán** tại `BC-CAND-01` | **Cửa sổ check-in hợp lệ** tại `BC-CAND-04` | Hai khoảng thời gian khác nhau; sự kiện đã bắt đầu vẫn có thể còn bán nếu cửa sổ bán chưa đóng |
| **Sự kiện đã kết thúc** tại `BC-CAND-01` | **Đủ điều kiện đối soát/chi trả** tại `BC-CAND-05` | Kết thúc sự kiện chỉ là một mốc thời gian; đủ điều kiện còn đòi hỏi không còn lần thanh toán/yêu cầu hoàn đang xử lý |
| **Tỷ lệ phí nền tảng cố định khi phê duyệt** tại `BC-CAND-01` | **Phí nền tảng tính trên doanh thu thực thu** tại `BC-CAND-05` | Nghĩa cố định đã được chốt; quyền sở hữu dữ liệu vật lý còn `OPEN` tại `BIZ-123` và không được B5 suy ra |
| **Danh tính và vòng đời tài khoản** do Keycloak quản lý | **Hồ sơ nghiệp vụ tối thiểu** do ứng dụng cần để thực hiện quy tắc miền | Hồ sơ ứng dụng không trở thành nguồn cấp quyền thay Keycloak; tập trường nghiệp vụ đã chốt ở `BIZ-151`, còn đồng bộ và nơi lưu chờ gate sau |
| **Vai trò organizer của tài khoản** | **Quyền quản lý một sự kiện cụ thể** | Có role không tự chứng minh được quản lý mọi sự kiện; mỗi sự kiện thuộc đúng một organizer và không hỗ trợ chuyển quyền trong phạm vi đồ án |
| **Theo dõi organizer** | **Vai trò `ORGANIZER`** | Quan hệ buyer theo dõi organizer không cấp quyền nghiệp vụ và chưa bao hàm thông báo hay cách tính số người theo dõi |

## 5. Bản đồ quan hệ ngữ nghĩa ứng viên

Sơ đồ sau chỉ thể hiện **sự thật hoặc chính sách cần trao đổi về mặt ngữ nghĩa**. Mũi tên không phải API, topic, sự kiện tích hợp, quyền sở hữu dữ liệu hay hướng gọi đồng bộ.

Nguồn biểu đồ gói UML của B5-v0.12: [`B5-01-bounded-context-map.puml`](../diagrams/src/B5-01-bounded-context-map.puml). Đây là biểu diễn trình bày của chính tám context và các cạnh đã được truy vết tại §5.1; khối chữ bên dưới được giữ làm bản thay thế đọc nhanh trong Markdown. Bản hiển thị dùng tên nghiệp vụ tiếng Việt làm nhãn chính và để mã `BC-CAND-*` ở bảng truy vết thay vì lặp trên hình, đúng giới hạn dành cho báo cáo tại §10. Biểu đồ không đổi trạng thái `CANDIDATE` của bounded context và không biến package thành service vật lý.

```text
[BC-CAND-07 Hồ sơ tài khoản/quyền]
       └─ định danh và điều kiện được phép ─────→ [BC-CAND-01] [BC-CAND-02] [BC-CAND-03] [BC-CAND-04] [BC-CAND-05]

[BC-CAND-01 Vòng đời sự kiện/cấu hình bán]
       ├─ điều kiện bán và cấu hình thương mại ──→ [BC-CAND-02 Mua vé/cam kết nguồn cung]
       ├─ cửa sổ thời gian và định danh sự kiện ─→ [BC-CAND-04 Quyền tham dự/check-in]
       ├─ sự kiện đã kết thúc và tỷ lệ phí ──────→ [BC-CAND-05 Đối soát/chi trả]
       └─ sự kiện bị hủy ────────────────────────→ [BC-CAND-02] [BC-CAND-03] [BC-CAND-04] [BC-CAND-05]

[BC-CAND-02 Mua vé/cam kết nguồn cung]
       └─ nghĩa vụ thanh toán của đơn ───────────→ [BC-CAND-03 Thanh toán/hoàn tiền]

[BC-CAND-03 Thanh toán/hoàn tiền]
       ├─ thu hợp lệ → chốt tài nguyên đang giữ ─→ [BC-CAND-02 Mua vé/cam kết nguồn cung]
       ├─ xác nhận thu hoặc nguyên nhân hoàn ────→ [BC-CAND-04 Quyền tham dự/check-in]
       └─ khoản thu và kết quả hoàn ─────────────→ [BC-CAND-05 Đối soát/chi trả]

[BC-CAND-04 Quyền tham dự/check-in]
       ├─ phát hành lỗi sau khi đã thu ──────────→ [BC-CAND-03 Thanh toán/hoàn tiền]
       ├─ trả lượt khuyến mãi/tồn kho có điều kiện → [BC-CAND-02 Mua vé/cam kết nguồn cung]
       └─ vé đã phát hành cần giao nhận ─────────→ [BC-CAND-06 Giao nhận thông tin vé]

[Dấu vết từ các context nghiệp vụ]
       └─ đầu vào chỉ đọc đã khử nhạy cảm ───────→ [BC-CAND-08 Chẩn đoán sự cố]
```

### 5.1 Bằng chứng cho từng cạnh

Mỗi cạnh trên sơ đồ phải truy được về một dòng cụ thể của B4-v0.14. Các quyết định bổ sung không tạo cạnh mới và không cạnh nào được vẽ từ suy đoán hoặc từ hình dung về cách triển khai.

| Cạnh | Nội dung ngữ nghĩa được trao đổi | Bằng chứng B4 | Trạng thái |
|---|---|---|---|
| `BC-CAND-07` → `01`, `02`, `03`, `04`, `05` | Định danh tài khoản và điều kiện được phép phát lệnh | B4 §8.1: lệnh dòng A/B/D và truy vấn đối soát đều cần định danh, quyền sở hữu đơn/sự kiện hoặc quyền cấp nền tảng | `FACT` |
| `BC-CAND-01` → `BC-CAND-02` | Điều kiện bán, cấu hình thương mại và khuyến mãi đã cấu hình | `A02`, `A07`, `A08` | `FACT` |
| `BC-CAND-01` → `BC-CAND-04` | Cửa sổ check-in và việc vé phải thuộc đúng sự kiện đang quét | `A12` mở cửa sổ check-in; `A13` đóng check-in; `D01` kiểm tra cửa sổ thời gian và sự kiện của vé | `FACT` |
| `BC-CAND-01` → `BC-CAND-05` | Sự kiện đã kết thúc là tiền đề xét đối soát; tỷ lệ phí nền tảng nhập khi phê duyệt là đầu vào tính phí | `A13` xét điều kiện đối soát/chi trả; `C08`; `A04` nhập tỷ lệ phí; `C07` tính phí nền tảng | `FACT` |
| `BC-CAND-01` → `02`, `03`, `04`, `05` | Sự kiện bị hủy lan sang đơn đang giữ, khoản thu, vé và sổ đối soát | `A11`, `B13`, `B14`, `C05`, `C07` | `FACT` |
| `BC-CAND-02` → `BC-CAND-03` | Nghĩa vụ thanh toán của một đơn còn hiệu lực, và việc đơn đã hết hạn/bị hủy chặn lần thanh toán mới | `B03`; `B08`, `B09`; `B10` tiền đến sau khi đơn hết hạn hoặc bị hủy | `FACT` |
| `BC-CAND-03` → `BC-CAND-02` | Thu hợp lệ làm tài nguyên đang giữ được chốt lại | `B05` chốt tài nguyên đã giữ | `FACT` |
| `BC-CAND-03` → `BC-CAND-04` | Xác nhận thu cho phép phát hành vé; nguyên nhân hoàn quyết định hậu quả lên quyền vào cửa | `B05`, `B06`; `C03` chỉ đổi vé theo nguyên nhân gốc | `FACT` |
| `BC-CAND-03` → `BC-CAND-05` | Khoản thu hợp lệ và kết quả hoàn là đầu vào của sổ cái đối soát | `C03`, `C07` | `FACT` |
| `BC-CAND-04` → `BC-CAND-03` | Phát hành vé thất bại sau khi đã thu tiền làm phát sinh yêu cầu hoàn toàn bộ | `B12` → `C01` | `FACT` |
| `BC-CAND-04` → `BC-CAND-02` | Phát hành thất bại trả lượt khuyến mãi đúng một lần, và chỉ trả tồn kho/giới hạn nếu sự kiện vẫn đủ điều kiện bán | `B12` | `FACT` |
| `BC-CAND-04` → `BC-CAND-06` | Vé đã phát hành cần được gửi tới buyer | `B06` → `B07` | `FACT` |
| Dấu vết nghiệp vụ → `BC-CAND-08` | Dấu vết đã khử nhạy cảm làm đầu vào chỉ đọc cho chẩn đoán | B4 §8.3; B4 §9 dòng "Dấu vết vận hành đã được tạo" | `FACT` |

Chiều mũi tên chỉ nói *ai cần biết gì*, không nói ai gọi ai. Việc một cạnh được hiện thực bằng truy vấn đồng bộ, sự kiện tích hợp hay dữ liệu tham chiếu là quyết định của B11–B13.

### 5.2 Quan hệ xuyên context cần bảo vệ ở gate sau

| Quan hệ nghiệp vụ | Bằng chứng B4 | Điều B7/B9/B10 phải làm rõ | B5 chưa quyết định |
|---|---|---|---|
| Sự kiện bị hủy lan sang đơn, vé, hoàn tiền và đối soát | `A11`, `B13`, `B14`, `C05`–`C07` | Bất biến theo nguyên nhân, lỗi từng phần và trạng thái quan sát được | Có dùng Saga/bộ điều phối nào hay không |
| Giữ chỗ đồng thời cam kết nguồn cung, giới hạn mua và có thể có lượt khuyến mãi | `B01`, `B02`, `B08`, `B09`; `INV-01`, `INV-03`, `INV-04`, `INV-06`; `HOT-02` | Phạm vi aggregate, cạnh tranh và giải phóng/chốt đúng một lần | Nguồn cung/giữ chỗ/đơn nằm cùng hay khác service/schema |
| Thanh toán xác nhận dẫn tới phát hành vé; lỗi sau thu dẫn tới hoàn | `B05`, `B06`, `B12`, `C01`; `INV-05`, `INV-07`, `INV-08`, `HOT-03` | Idempotency, thử lại và hậu quả theo nguyên nhân | Giao thức, transaction hoặc mẫu phân tán |
| Vé chỉ check-in thành công tối đa một lần | `D01`–`D03`, `INV-09`, `HOT-04` | Bất biến và kịch bản cạnh tranh nhiều thiết bị | Công nghệ hoặc vị trí quyết định nguyên tử |
| Đối soát và đánh dấu `PAID` chỉ mở khi sự kiện đã kết thúc và không còn tiền treo | `A13`, `C07`–`C10`; `INV-10` | Nguồn sự thật của điều kiện "không còn xử lý tiền treo" và cách bảo vệ đúng một lần `PAID` | Cách dựng read model, vị trí sổ cái hoặc cơ chế kiểm tra điều kiện |
| Tỷ lệ phí nền tảng phải giữ đúng giá trị admin đã phê duyệt và không được sửa âm thầm | `A04`, `C07`, `C08`; `INV-11`; `BIZ-142` | B7 xác định phạm vi bất biến và các thay đổi hợp lệ nếu sau này phát sinh yêu cầu mới | Nơi sở hữu dữ liệu, schema, cách khóa và cơ chế kỹ thuật; `BIZ-123` vẫn `OPEN` cho B12 |
| Dấu vết nghiệp vụ được chọn làm context chẩn đoán | B4 §8.3 và §9 | Quyền đọc, masking, tiêu chí chọn context và đánh giá | Pipeline vật lý, nguồn log, bảng AI hoặc hợp đồng |

## 6. Ánh xạ đầy đủ sự kiện và năng lực B4

| Phạm vi B4 | Context ứng viên nhận trách nhiệm ngôn ngữ | Ghi chú |
|---|---|---|
| `A01`–`A13` | `BC-CAND-01` | Hậu quả của `A11` được các context khác phản ứng; B5 không gọi quan hệ đó là Saga |
| `B01`, `B02`, `B08`, `B09`, `B13` | `BC-CAND-02` | `A07`/`A08` là đầu vào điều kiện bán; ranh giới nguồn cung–giữ chỗ–đơn vẫn `OPEN` |
| `B03`–`B05`, `B10`, `B11`, `C01`–`C06` | `BC-CAND-03` | Phân biệt callback lặp, thu trùng và một yêu cầu hoàn logic cho mỗi khoản thu |
| `B06`, `B12`, `B14`, `D01`–`D03` | `BC-CAND-04` | Quyền vào cửa và check-in dùng cùng ngôn ngữ vé; aggregate chờ B7 |
| `C07`–`C10` | `BC-CAND-05` | Sổ cái là kết quả tổng hợp, không phải quyền sửa các khoản thu nguồn; tỷ lệ phí cố định theo `BIZ-142`, còn quyền sở hữu dữ liệu vật lý vẫn `OPEN` tại `BIZ-123` |
| `B07` | `BC-CAND-06` | Giao nhận thất bại không làm vé mất hiệu lực |
| `E01`–`E05` | `BC-CAND-07` | Dòng sự kiện hỗ trợ của vòng đời tài khoản, hồ sơ organizer và quan hệ theo dõi organizer; đặt tên cho các chuyển trạng thái vốn đã có ở B4 §8.1, không tạo context mới và không đổi phân loại `BC-CAND-07` |
| B4 §8.1 | `BC-CAND-07` | Keycloak, bộ role/đa vai trò, vòng đời organizer tối thiểu, không xác minh email/SĐT, không đăng nhập xã hội và các giới hạn phạm vi đã được làm rõ; tập trường hồ sơ theo `BIZ-151`, còn đồng bộ kỹ thuật chờ `B4-OPEN-01` |
| B4 §8.4 — địa điểm, phân loại, tìm sự kiện đã công bố | `BC-CAND-01` | Các khái niệm mô tả/thể hiện sự kiện dùng cùng ngôn ngữ vòng đời công bố; không đồng nhất địa điểm với nguồn cung hoặc sơ đồ ghế |
| B4 §8.4 — buyer theo dõi organizer | `BC-CAND-07` | Đây là quan hệ buyer–organizer, không phải lựa chọn vé/cam kết nguồn cung của `BC-CAND-02`; chi tiết đếm và thông báo chưa được chốt |
| `T01`–`T04` | `BC-CAND-08` | Toàn bộ vẫn ở trạng thái `CANDIDATE` và chỉ đọc |

Không có sự kiện B4 nào bị bỏ khỏi ánh xạ. Các trạng thái suy ra `A07`/`A08` được dùng làm điều kiện đầu vào chứ không bị biến thành sự kiện tích hợp.

## 7. Kênh tương tác và hệ thống ngoài không tự thành context

| Thành phần | Cách ánh xạ ở B5 | Căn cứ |
|---|---|---|
| Chatbot hỗ trợ mua vé | Là kênh đọc thông tin bán từ `BC-CAND-01` và gửi cùng ý định mua qua quy tắc của `BC-CAND-02`; chưa có ngôn ngữ/sự kiện riêng để hình thành context khác | B4 §8.2 |
| Web buyer/organizer/admin | Là kênh tương tác với các context theo quyền tương ứng; không chia context theo vai trò giao diện. Frontend hiện tại được tái sử dụng làm nền và phải thích nghi với mô hình đích; việc tái sử dụng không ràng buộc phương án tách service ở B11-A. B5 không suy ra API gateway hay một bề mặt API cụ thể | B3, B4 §8.1; `PRJ-004`, `PRJ-005` |
| Mobile check-in | Là client của `BC-CAND-04`, không phải một miền nghiệp vụ riêng | B3 §5; B4 dòng D |
| Cổng thanh toán | Hệ thống ngoài tham gia `BC-CAND-03`; không phải bounded context nội bộ được B5 phân rã | B3 §3–§4; B4 dòng B/C |
| LLM API | Hệ thống ngoài được `BC-CAND-08` sử dụng theo ranh giới chỉ đọc; chưa chốt adapter hay hợp đồng | B4 §8.3 |

## 8. Sổ hotspot, nội dung đã làm rõ và vấn đề `OPEN`

### 8.1 Phần đã được xác nhận và chuyển đúng gate

| ID lịch sử | Phần có bằng chứng | Phần vẫn thiếu | Kết quả tại B5-v0.12 |
|---|---|---|---|
| `B5-OPEN-01` | Keycloak, hồ sơ nghiệp vụ tối thiểu, buyer mặc định, bộ role, vòng đời organizer, điều kiện hoàn tất duyệt/công khai, không thu hồi role và quan hệ một organizer–một sự kiện đã được chốt (`PRJ-003`, `BIZ-109`, `BIZ-111`, `BIZ-114`, `BIZ-119`, `BIZ-124`, `BIZ-129`, `BIZ-131`–`BIZ-141`, `BIZ-151`) | Chi tiết đồng bộ/kho lưu kỹ thuật | Đóng phần nghiệp vụ; chi tiết kỹ thuật chờ B11–B13 |
| `B5-OPEN-08` | Admin nhập tỷ lệ phí khi duyệt, phí tính trên doanh thu thực thu và tỷ lệ được cố định sau phê duyệt (`BIZ-034`, `BIZ-059`, `BIZ-142`) | Quyền sở hữu dữ liệu vật lý còn `OPEN` cho B12 (`BIZ-123`) | Đóng phần nghĩa nghiệp vụ; B7 được dùng `INV-11`, còn B12 mới quyết định sở hữu dữ liệu |
| `B5-OPEN-04` | Kích hoạt lặp của cùng nguyên nhân vô hiệu vé được hấp thụ bởi chuyển trạng thái vé đơn điệu | Không còn thiếu nghĩa nghiệp vụ; không mở rộng sang biến thể mạng chập chờn không phục vụ trục nghiên cứu | Đóng bởi `BIZ-147`, `BIZ-148`; B7/B8 dùng quy tắc đã xác nhận |
| `B5-OPEN-10` | Organizer quyết định nguồn cung vé; hệ thống không biết và không kiểm sức chứa vật lý của địa điểm | Không còn thiếu nghĩa nghiệp vụ; schema trường địa điểm không được quyết ở B5 | Đóng bởi `BIZ-149`; không tạo bất biến sức chứa |
| `B5-OPEN-12` | Hồ sơ organizer tối thiểu giữ tên tổ chức, mô tả ngắn và lý do từ chối khi có; không yêu cầu giấy tờ, tài khoản ngân hàng hoặc nhận diện thương hiệu | Kiểu dữ liệu, schema và hợp đồng | Đóng phần trường nghiệp vụ bởi `BIZ-151`; chi tiết dữ liệu chờ B12/B13 |

### 8.2 Vấn đề vẫn `OPEN`

| ID | Vấn đề còn thiếu | Nguồn | Chủ thể/gate xử lý | Tác động tới B5 |
|---|---|---|---|---|
| `B5-OPEN-02` | Ranh giới bảo vệ đồng thời giữa nguồn cung, giữ chỗ và đơn; chưa biết cách đặt aggregate hoặc ranh giới triển khai | `B4-OPEN-05`, `HOT-02`, `INV-01`, `INV-03`, `INV-06` | B7 làm rõ bất biến/aggregate; B9/B10 tạo ASR; B11-A mới tạo phương án | Không chặn bản đồ ứng viên; cấm ghi hướng đặt ưu tiên tại B5 |
| `B5-OPEN-03` | Theo dõi, thử lại và kết thúc N yêu cầu hoàn khi hủy một sự kiện | `B4-OPEN-06`, `HOT-01` | B7/B9/B10; cơ chế kiến trúc chờ B11 | Không chặn; giữ quan hệ nhân quả, không chọn bộ điều phối |
| `B5-OPEN-05` | Workflow cuối của yêu cầu chẩn đoán, chọn trace, lưu phản hồi và tập ca đánh giá | `B4-OPEN-02` | Minh/Nhật; B16–B19 | Không chặn; cấm chốt schema hoặc đơn vị triển khai trợ lý |
| `B5-OPEN-06` | Các mốc thời gian được tính động hay phát thành sự kiện kỹ thuật | `B4-OPEN-03` | B11/B13 | Không chặn; không tạo contract ở B5 |
| `B5-OPEN-07` | Trường audit/payload/lưu giữ cho check-in bị từ chối và thao tác quản trị | `B4-OPEN-04` | Minh/Tuyến; B8/B13/B16 | Không chặn; B5 chỉ giữ nhu cầu dấu vết theo luồng đã xác nhận |
| `B5-OPEN-08` | Dữ liệu vật lý của tỷ lệ phí nền tảng do đâu sở hữu | `B4-OPEN-08`, `BIZ-123` (`OPEN`); nghĩa cố định đã đóng tại `BIZ-142` | B12 sau B11-C | Không chặn context; cấm trình bày schema hoặc vị trí dữ liệu như đã quyết định |
| `B5-OPEN-11` | Số người theo dõi organizer được lưu đếm sẵn hay đếm trực tiếp từ quan hệ theo dõi | `BIZ-128`; B4 §8.4 | B12/B13 sau khi hành vi ở B6/B8 được duyệt | Không chặn ánh xạ năng lực; B5 không quyết định schema hoặc cách tính |

**Về `B5-OPEN-09`.** Mã này được cấp ở `B5-v0.4` trong cùng nhóm `B5-OPEN-09`–`B5-OPEN-12` khi bổ sung venue/category/search/follow, nhưng **phát biểu của nó không xuất hiện ở bất kỳ phiên bản nào được ghi lại**: không có ở §8.1, không có ở §8.2, và tra toàn bộ lịch sử repo cũng chỉ thấy nó trong đúng một dòng nhật ký phiên bản. Vòng kiểm toán ngày 2026-08-22 vì vậy ghi nhận đây là **mã đã cấp nhưng mất phát biểu**, giữ lại để bảo toàn dấu vết đúng như cách B2 §8 giữ ID lịch sử. Nó **không** được tính vào bảy điểm `OPEN` hiện hành ở §8.2. Nếu Lê Văn Minh nhớ ra một khoảng trống thật đứng sau mã này, phải mở lại bằng một phát biểu mới kèm owner và gate, không suy đoán nội dung cũ.

## 9. Phép tự kiểm và điều kiện chuyển trạng thái

- [x] Mỗi context ứng viên truy được về sự kiện, quyền hoặc hotspot cụ thể trong B4-v0.14; phần bổ sung mới không tạo context mới.
- [x] Vòng đời vé, hồ sơ tài khoản, chatbot và trợ lý chẩn đoán sự cố đều được xem xét.
- [x] Khuyến mãi cấu hình được phân biệt với lượt dùng khuyến mãi; phát hành vé được phân biệt với giao nhận vé.
- [x] Hoàn tiền được phân biệt với trạng thái quyền vào cửa của vé.
- [x] Không có context nào được tuyên bố là service, schema, Saga, API, topic hoặc vị trí aggregate.
- [x] Tập context hình thành từ bằng chứng miền của B2, B3 và B4 đã được duyệt tại thời điểm hình thành; các phiên bản sau chỉ đóng thêm nghĩa nghiệp vụ và truy vết, không dùng nguồn hiện thực để tạo context.
- [x] Mọi cạnh trong bản đồ §5 đều có bằng chứng B4 trực tiếp tại §5.1; không cạnh nào được vẽ từ suy đoán.
- [x] Bảng §5.1 và bảng §5.2 nhất quán với nhau: mọi quan hệ nêu trong §5.2 đều xuất hiện trên sơ đồ.
- [x] Biểu đồ gói UML `B5-01-bounded-context-map.puml` phản ánh đúng tám context và các luồng trao đổi ngữ nghĩa ở §5/§5.1, dùng tên nghiệp vụ dễ đọc thay cho mã governance trên phần hiển thị, có chú giải và không biểu diễn ranh giới service hoặc cơ chế tích hợp.
- [x] Mọi hotspot B4 (`HOT-01`–`HOT-04`), mọi vấn đề còn `OPEN` và kết quả đóng `B4-OPEN-07` được truyền sang B5 kèm mã truy vết, owner và gate; không bị tự lấp.
- [x] Tiêu chí phân loại cốt lõi/hỗ trợ/chung được truy về A2/A4 baseline `APPROVED` và vẫn giữ trạng thái `CANDIDATE` chờ B10.
- [x] Lê Văn Minh đồng ý giữ tám context ứng viên, phân loại cốt lõi/hỗ trợ/chung và bản đồ quan hệ ngữ nghĩa; B5-v0.11 không đổi tám context, phân loại hay bản đồ quan hệ so với bản đã đồng ý.
- [x] Lê Văn Minh đã đóng phần nghiệp vụ của `B5-OPEN-01` và `B5-OPEN-12`; chi tiết đồng bộ/dữ liệu chờ đúng gate sau.
- [x] Lê Văn Minh xác nhận tỷ lệ phí cố định theo `BIZ-142`; quyền sở hữu dữ liệu tiếp tục chờ B12 tại `B5-OPEN-08`/`BIZ-123`.
- [x] Lê Văn Minh xác nhận việc `BC-CAND-02` gom nguồn cung, giữ chỗ và đơn chỉ là luận điểm về **ngôn ngữ chung**, không phải ưu tiên đồng vị trí khi triển khai; `B5-OPEN-02` vẫn giữ nguyên.
- [x] `B4-v0.13` đã được duyệt **trước** `B5-v0.11`; lịch sử phê duyệt `B4-v0.11 → B5-v0.9` cũng được xác nhận tại `GOV-021`. Vòng `B2-v0.10` đưa cả chuỗi về `REVIEW_READY`; `B5-v0.12` được duyệt sau `B4-v0.14` trong cùng ngày 2026-08-22.
- [x] Mọi tham chiếu phiên bản B4 trong thân bài trỏ đúng phiên bản hiện hành; ba chỗ còn dẫn `B4-v0.11`/`B4-v0.9` ở `B5-v0.11` đã được sửa.
- [x] `B5-OPEN-09` được ghi rõ là mã đã cấp nhưng mất phát biểu, không bị đếm nhầm vào danh sách `OPEN` hiện hành.
- [x] Lê Văn Minh đóng `B5-OPEN-10`: hệ thống không biết/kiểm sức chứa vật lý và B5 không thêm bất biến sức chứa (`BIZ-149`).
- [x] Lê Văn Minh xác nhận `B5-OPEN-11` được hoãn tới B12/B13; B5 chưa chọn cách tính/lưu số người theo dõi.
- [x] Lê Văn Minh đóng `B5-OPEN-12` ở mức trường nghiệp vụ; kiểu dữ liệu, schema và hợp đồng vẫn chờ B12/B13 (`BIZ-151`).
- [x] `B5-v0.12` chỉ sửa tham chiếu, ghi rõ tình trạng `B5-OPEN-09` và lan truyền trạng thái; không thêm hoặc đổi context, aggregate, service, schema, API, Saga hay cơ chế đồng bộ.

## 10. Phần dùng cho báo cáo

Sau khi B5 được duyệt, báo cáo có thể dùng:

- một bản đồ rút gọn gồm tên context, phân loại và quan hệ ngữ nghĩa chính;
- bảng giải thích các ranh giới ngôn ngữ quan trọng như cấu hình/áp dụng khuyến mãi, thanh toán/quyền tham dự và phát hành/giao nhận vé;
- lập luận rằng bounded context được hình thành từ sự kiện, quy tắc và ngôn ngữ nghiệp vụ, chưa phải danh sách service vật lý.

Không đưa nguyên trạng mã `BC-CAND-*`, sổ governance hoặc toàn bộ danh sách `OPEN` vào báo cáo. Chỉ trình bày các hotspot còn ảnh hưởng trực tiếp đến lập luận thiết kế hoặc giới hạn nghiên cứu.

## 11. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B5-v0.1` | 2026-08-18 | Bản đầu: gom sự kiện B4-v0.5 thành tám context ứng viên, ranh giới ngôn ngữ, sơ đồ quan hệ và sổ `OPEN` | Tạo mới |
| `B5-v0.2` | 2026-08-19 | Bổ sung sáu cạnh quan hệ đã có bằng chứng B4 nhưng thiếu trên sơ đồ; thêm bảng bằng chứng cho từng cạnh (§5.1); thêm ba ranh giới ngôn ngữ gồm cửa sổ bán/check-in, kết thúc sự kiện/đủ điều kiện đối soát và tỷ lệ phí nền tảng; mở `B5-OPEN-08`; sửa trích dẫn `INV`/`HOT` tại §5.2; khai nguồn A2/A4 cho tiêu chí phân loại | Sửa truy vết và bổ sung quan hệ |
| `B5-v0.3` | 2026-08-20 | Đã từng ghi nhận Keycloak, hồ sơ nghiệp vụ, bộ role, xét duyệt organizer, quan hệ organizer–sự kiện, giới hạn frontend và tỷ lệ phí như các quyết định đã xác nhận; chuyển `B5-OPEN-01`/`B5-OPEN-08` sang mục đã làm rõ và đưa tài liệu sang `REVIEW_READY`. B5-v0.4 sửa lại các trạng thái bằng chứng này | Lịch sử trước hiệu đính, không dùng làm nguồn hiện hành |
| `B5-v0.4` | 2026-08-20 | Phân loại lại từng quyết định; trả `B5-OPEN-01`/`B5-OPEN-08` về `OPEN`; bổ sung venue/category/search/follow ở mức tối giản; thêm bất biến phí ở §5.2 và `B5-OPEN-09`–`B5-OPEN-12`; bỏ năm dấu tick không có bằng chứng; trả tài liệu về `DRAFT` | Sửa truy vết bằng chứng và trạng thái review |
| `B5-v0.5` | 2026-08-20 | Đồng bộ B2/B3/B4-v0.7; bổ sung quyết định không đăng nhập xã hội; truy ngược giả thuyết phí về `INV-11`/`B4-OPEN-08`; loại nội dung từ luồng đối chiếu khỏi `FORMATION`; không dùng `BIZ-107` để hình thành trách nhiệm đã chốt | Kiểm toán chéo và sửa vi phạm nguồn |
| `B5-v0.6` | 2026-08-21 | Ghi nhận phê duyệt B2–B5 theo chuỗi; đóng phần nghiệp vụ vòng đời organizer và tính cố định của tỷ lệ phí; giữ trường hồ sơ, sở hữu dữ liệu và các hotspot kỹ thuật tại đúng gate; bổ sung truy vết chức năng giao diện từ B4 và nguồn biểu đồ gói UML phản ánh đúng bản đồ đã duyệt; hiệu đính hình để tên nghiệp vụ là nhãn chính, mã governance chỉ nằm ở phần truy vết | Duyệt nội dung, đóng `OPEN` có kiểm soát và hoàn thiện biểu diễn |
| `B5-v0.7` | 2026-08-21 | Không đổi nội dung context, ranh giới hay quan hệ. Chỉ cập nhật khai phiên bản đầu vào sau khi `BIZ-146` bỏ vai trò `SUPER_ADMIN` khỏi B2/B4; tài liệu trở lại `REVIEW_READY` vì chuỗi thượng nguồn phải được duyệt lại | Lan truyền trạng thái |
| `B5-v0.8` | 2026-08-21 | Ánh xạ dòng sự kiện hỗ trợ `E01`–`E04` của B4-v0.10 vào `BC-CAND-07`. Không thêm, bớt, đổi tên hay đổi phân loại context nào; không đổi ranh giới ngôn ngữ hay bản đồ quan hệ | Lan truyền từ B4 |
| `B5-v0.9` | 2026-08-21 | Ánh xạ thêm `E05` — quan hệ theo dõi organizer — của B4-v0.11 vào `BC-CAND-07`; được Lê Văn Minh duyệt sau B4-v0.11. Không thêm, bớt, đổi tên hay đổi phân loại context nào | Lan truyền từ B4 và phê duyệt |
| `B5-v0.10` | 2026-08-21 | Tạm trả trạng thái về `REVIEW_READY` sau khi chỉ đối chiếu commit `8270613`; kết luận này không có đủ bằng chứng hội thoại và được hiệu đính ở v0.11 sau xác nhận trực tiếp của người duyệt | Hiệu đính tạm thời, đã bị thay thế |
| `B5-v0.11` | 2026-08-22 | Khôi phục đúng lịch sử `B5-v0.9` đã được duyệt; đóng `B5-OPEN-04`, `B5-OPEN-10`, `B5-OPEN-12` theo các quyết định đã xác nhận mà không đổi tám context hoặc bản đồ quan hệ | Phê duyệt và lan truyền quyết định |
| `B5-v0.12` | 2026-08-22 | Sửa sau vòng kiểm toán: ba tham chiếu `B4-v0.11`/`B4-v0.9` còn sót trong thân bài trỏ về `B4-v0.14`; ghi rõ tình trạng của mã `B5-OPEN-09` bị mất phát biểu; đồng bộ khai đầu vào sang `B2-v0.10`/`B3-v0.10`/`B4-v0.14` và trở lại `REVIEW_READY` theo quy tắc chuỗi. Không đổi tám context, phân loại hay bản đồ quan hệ | Sửa sau kiểm toán và lan truyền trạng thái |

Các phiên bản từ `B5-v0.6` tới `B5-v0.12` **không** thêm, bớt, đổi tên hoặc đổi phân loại bất kỳ context nào so với `B5-v0.5`, và không tạo hướng đặt aggregate, service, schema, API, Saga hay cơ chế đồng bộ. Quyết định tái sử dụng frontend chỉ là rào chắn phạm vi; nguồn hiện thực và luồng đối chiếu không làm nguồn hình thành mô hình đích ở B5.
