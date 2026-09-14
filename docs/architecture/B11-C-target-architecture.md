# B11-C — Chốt kiến trúc đích và chuẩn bị ADR

- Phiên bản: `B11-C-v0.3`
- Trạng thái: `APPROVED`
- Người duyệt: **Lê Văn Minh**
- Ngày duyệt: **2026-09-04**
- Ngày mở: **2026-09-01** · cập nhật lựa chọn: **2026-09-04**
- Phân lớp: **`FORMATION`**
- Đầu vào trực tiếp: `B11-A-v0.5` `APPROVED` (`GOV-084`) và `B11-B-v0.6` `APPROVED` (`GOV-085`)
- Đi vào báo cáo: phần Kiến trúc — phương án được chọn, động lực, hệ quả và cách kiểm chứng

> **Lựa chọn đã được người thật xác nhận.** `PA-6`, năm service, một Saga, RCA riêng chỉ đọc và bố trí hai máy được chốt tại `GOV-086`–`GOV-090`. Phạm vi ảnh nhận diện nghĩa hẹp được chốt tại `GOV-091`.
>
> **Gate đã đóng (`GOV-099`).** `GOV-097` thu hồi điều kiện benchmark tải của `GOV-092`: B11 chốt bằng ngân sách tài nguyên và giới hạn heap/container; thử tải thật chuyển sang Giai đoạn 5–6. `GOV-098` cho phép nâng instance AWS tạm thời nếu cấu hình hiện tại thiếu tài nguyên, không mở lại lựa chọn `PA-6`. Tác động lên `A6-v0.6` đã được rà lại và phép thử độc lập RCA đạt.
>
> **Điều kiện cách ly đã thỏa.** B11-C nhận bằng chứng hiện thực qua `B11-B-v0.6` đã duyệt; lượt hình thành kiến trúc này không mở repository cũ và không dùng cấu trúc cũ để sinh hoặc sửa phương án.

---

## 1. Phạm vi quyết định

`B11-C` chốt phương án, service vật lý, phạm vi Saga, vị trí RCA và bố trí triển khai. Quyền sở hữu dữ liệu/schema vẫn thuộc `B12`; API/event thuộc `B13`; sequence Saga thuộc `B14`; chuẩn log/metric/trace thuộc `B16`.

Không nội dung nào dưới đây được đọc thành quyết định schema, payload, endpoint, topic, công nghệ lưu trữ hay ngưỡng hiệu năng nghiệp vụ.

## 2. Kiểm tra điều kiện và trạng thái lựa chọn

| Điều kiện | Bằng chứng | Kết quả |
|---|---|---|
| Tập phương án độc lập đã duyệt | `B11-A-v0.5`, `GOV-084` | **Đạt** |
| Đối chiếu khả thi đã duyệt | `B11-B-v0.6`, `GOV-085` | **Đạt** |
| Phương án đích | `GOV-086` | **`PA-6` — `USER_CONFIRMED`** |
| Service, Saga, RCA, bố trí hai máy | `GOV-087`–`GOV-090` | **Đã chốt ở B11-C** |
| Ngân sách tài nguyên | `GOV-097`, `GOV-098` | **Đạt cho gate B11 — mục tiêu khoảng 6 GiB cấp phát mỗi máy, công bố heap/container; thử tải thật ở Giai đoạn 5–6** |
| Tác động A1–A6 | §7 | **Đã rà và đồng bộ `A6-v0.6`; phần thay đổi do B11 đã được Lê Văn Minh duyệt lại** |
| Phép thử độc lập RCA | §8 | **Đạt — đã chạy lại trên bản chốt** |

Ngân sách tài nguyên và quyền nâng instance là quyết định phạm vi vận hành, không phải bằng chứng chịu tải. Kiến trúc được chấp nhận mà không gắn một tuyên bố throughput/latency chưa đo.

## 3. Kiến trúc đích được chọn

### 3.1 Năm service nghiệp vụ

| Service đích | Ranh giới `PA-6` | Trách nhiệm |
|---|---|---|
| `event-service` | `RG-1` | Sự kiện, địa điểm, phân loại, yêu cầu hủy và cấu hình bán — loại vé/sector/khuyến mãi theo nghĩa cấu hình |
| `booking-service` | `RG-2` | Nguồn cung, giữ chỗ, đơn hàng, giới hạn mua và sử dụng khuyến mãi |
| `payment-service` | `RG-3` | Thanh toán, hoàn tiền, đối soát và hồ sơ chi trả sự kiện |
| `ticket-service` | `RG-4` | Phát hành/giao nhận vé và kiểm soát vào cửa |
| `user-service` | `RG-5` | Hồ sơ tài khoản, đăng ký/following organizer và quyền nghiệp vụ |

Chatbot là **kênh bán vé**, không phải service nghiệp vụ. Gateway, Keycloak, config/discovery, broker, cache, kho quan sát và RCA là thành phần hỗ trợ/vận hành, không làm thay đổi con số năm service (`GOV-079`, `GOV-087`).

### 3.2 Động lực chọn `PA-6`

- giữ `BC-02` trọn một ranh giới, nên `INV-01`, `INV-02`, `INV-03`, `INV-04`, `INV-06` cục bộ và không phải giải quyết khoảng trống giữ chỗ–đơn của `PA-5`;
- chỉ còn một bất biến xuyên ranh giới đã biết là `INV-11` giữa cấu hình sự kiện và tiền;
- dùng năm service, nằm dưới trần tám và phù hợp giới hạn vận hành hơn các hình dạng nhiều tiến trình;
- chuỗi thu tiền–phát hành có biên rõ để áp một Saga, trong khi thanh toán–hoàn tiền–đối soát ở cùng một service;
- không phương án nào bị `B11-B-v0.6` loại; lý do chọn ở trên xuất phát từ mô hình miền và ASR, không từ khoảng cách tới mã cũ.

### 3.3 Hệ quả phải chấp nhận

- `booking-service` là điểm nóng lớn; `QS-01` phải đo tranh chấp bên trong service thay vì dùng biên tiến trình làm điểm chèn lỗi;
- `event-service` phải cấp bản sao cấu hình thương mại có phiên bản cho các service sử dụng; cơ chế và hợp đồng cụ thể chờ `B13`;
- `INV-11` cần quy tắc đóng băng tỷ lệ phí sau phê duyệt; quyền sở hữu vật lý chờ `B12`;
- một giao dịch mua thành công đi qua ba service nghiệp vụ, nên correlation và dữ liệu quan sát là bắt buộc ở `B16`.

Sơ đồ: `docs/diagrams/src/B11-C-01-target-container.puml`.

## 4. Một Saga được chốt

Phạm vi duy nhất là **thu tiền → phát hành vé → bù trừ** (`GOV-088`):

1. `payment-service` ghi nhận kết quả thu tiền hợp lệ;
2. `ticket-service` phát hành vé;
3. nếu phát hành thất bại sau khi tiền đã thu, luồng bù trừ tạo yêu cầu hoàn tiền và trả tài nguyên/lượt dùng khuyến mãi qua `booking-service`.

Phạm vi Saga và vị trí điều phối đều là `USER_CONFIRMED` (`GOV-088`, `GOV-095`). `payment-service` điều phối vì nó biết tiền đã thực sự được thu và sở hữu tiến trình hoàn tiền. Nó chỉ giữ trạng thái phối hợp và gửi lệnh/sự kiện; không ghi trực tiếp dữ liệu của `booking-service` hay `ticket-service`. Trạng thái chi tiết, idempotency key, outbox/inbox, payload, timeout và sequence là đầu ra `B13`/`B14`.

Luồng hủy sự kiện không phải Saga: tiếp tục là phản ứng một chiều có thử lại tiến theo `ASR-05`. Không luồng phân tán nào khác được tự gắn nhãn Saga.

## 5. Vị trí RCA và quyền truy cập

Cơ chế RCA cùng lớp giải thích chạy trong **một đơn vị triển khai riêng** (`GOV-089`):

- nhận dữ liệu từ kho quan sát tập trung bằng credential chỉ đọc riêng;
- không truy cập trực tiếp schema nghiệp vụ và không gọi đường ghi của service nghiệp vụ;
- không tự sửa hệ thống, không tự kết luận nguyên nhân cuối cùng;
- kết quả xếp hạng, bằng chứng và lời giải thích phải đến được người có quyền; hình thức UI/API vẫn là `RES-039` và không được chốt hộ tại đây.

Lớp giải thích gọi **Gemini API** sau bước xếp hạng (`GOV-093`); không chạy mô hình ngôn ngữ lớn cục bộ. Dữ liệu gửi ra ngoài phải được lọc/che trường nhạy cảm, API key không được ghi vào repository, và lỗi/quota của Gemini không được làm mất kết quả xếp hạng RCA.

RCA là một node vận hành riêng nhưng không phải service nghiệp vụ. Tập node/metric mà RCA xếp hạng vẫn chờ đặc tả đồ thị và `B16`; không được mặc định bằng đúng năm service.

## 6. Bố trí hai máy mục tiêu

| Máy | Thành phần mục tiêu | Lý do |
|---|---|---|
| **Máy 1 — Transaction plane** | API Gateway; `booking-service`; `payment-service`; `ticket-service`; broker; cache | Gom đường mua vé nhạy độ trễ và luồng Saga; giảm hop cho đường nóng |
| **Máy 2 — Control/diagnosis plane** | `event-service`; `user-service`; Keycloak; config/discovery; adapter chatbot; collector/kho quan sát; RCA + lớp giải thích gọi Gemini API | Cách ly tác vụ chẩn đoán và phần điều khiển khỏi đường giao dịch nóng |

Datastore mới chỉ là **placeholder** trên sơ đồ; `B12` quyết định quyền sở hữu và vị trí vật lý. Kênh chatbot phải gọi qua gateway/hợp đồng công bố, không đi vòng vào dữ liệu. Sao chép cấu hình giữa hai máy đi theo chiều rõ ràng; không tạo vòng phụ thuộc đồng bộ.

Mỗi máy 8 GiB dùng ngân sách mục tiêu khoảng **6 GiB** cho các tiến trình/container, với heap và memory limit được công bố; phần còn lại dành cho hệ điều hành, bộ nhớ ngoài heap và dao động ngắn hạn (`GOV-097`). Bố trí này **không cam kết HA** (`GOV-096`). Nếu cấu hình hiện tại thiếu trong lượt kiểm thử/demo hữu hạn, nhóm nâng instance AWS tạm thời thay vì mở lại kiến trúc (`GOV-098`).

Sơ đồ: `docs/diagrams/src/B11-C-02-deployment-two-machines.puml`.

## 7. Tác động lên A1–A6

Đã đọc toàn văn sáu tạo tác hiện hành. Kết quả lần đầu:

| Tạo tác | Kết quả | Xử lý |
|---|---|---|
| `A1-v0.2` | **Không** — PA-6 không đổi mạch bối cảnh hay vai của FlashTicket | Không sửa |
| `A2-v0.2` | **Không** — bài toán RCA và năm nhóm thành phần của `DH-MT1` không đổi | Không sửa |
| `A3-v0.6` | **Không về nội dung** — mục tiêu/tiêu chí nghiên cứu không đổi; các điểm mở sẵn có giữ nguyên | Khai lại đầu vào sang `A6-v0.6`; không bump phiên bản |
| `A4-v0.3` | **Không** — câu hỏi và bằng chứng cần thu không đổi | Không sửa |
| `A5-v0.3` | **Không** — đối tượng, phương tiện và khách thể không đổi | Không sửa |
| `A6-v0.6` | **Có — đã xử lý nội dung:** ghi bố trí, ngân sách, quyền nâng instance và tách rõ rủi ro nạp `RE2` trên máy nghiên cứu khỏi tài nguyên sản phẩm | Phần cập nhật do B11 đã được Lê Văn Minh duyệt lại; các điểm `OPEN` nghiên cứu khác giữ nguyên |

Kết quả tác động A1–A6 đã đủ cho việc chấp nhận ADR; nó không đóng thay các điểm nghiên cứu còn `OPEN` trong `A6`.

## 8. Phụ lục đối chiếu RCA và phép thử độc lập

### 8.1 Đối chiếu ràng buộc 1 và 3 của `R0` §3

| Ràng buộc | Kết quả B11-C |
|---|---|
| 1 — tín hiệu xác định được thành phần sinh ra nó | **Giữ được về hình dạng:** năm service nghiệp vụ và các node vận hành có định danh riêng; chuẩn trường cụ thể chờ `B16` |
| 3 — ghi số ứng viên ở mức service và mức chỉ số | **Giữ tách hai lớp:** số service nghiệp vụ là 5; tập node RCA được xếp hạng và số chỉ số vẫn `OPEN`, không mặc định bằng 5 |

Hai ràng buộc vẫn là `CANDIDATE` của bộ RCA; bảng này chỉ kiểm tương thích, không dùng chúng để sinh phương án.

### 8.2 Phép thử độc lập

1. Tạo tác thuộc bộ hệ thống: **Có**.
2. Nguồn sinh phương án/ranh giới: chỉ `B11-A-v0.5` đã duyệt và lựa chọn người thật `GOV-086`–`GOV-090`.
3. Nguồn hiện thực chỉ dùng kiểm khả thi: `B11-B-v0.6`; không sinh hoặc xếp hạng phương án.
4. Nguồn bộ RCA chỉ xuất hiện trong phụ lục này: `R0` §3, ở mức `CANDIDATE`/`OPEN`.
5. Nguồn RCA có sinh yêu cầu, bất biến hoặc ranh giới không: **Không**.

Kết quả chạy lại trên `B11-C-v0.3`: **đạt**. Đã ghi vào `docs/project/lien-ket-rca.md` trước khi đóng B11.

## 9. ADR và điều kiện chấp nhận

| ADR | Nội dung | Trạng thái hiện tại |
|---|---|---|
| `ADR-001` | Chọn PA-6 và năm service nghiệp vụ | `Chấp nhận` |
| `ADR-002` | Một Saga thu tiền–phát hành–bù trừ | `Chấp nhận` |
| `ADR-003` | RCA riêng, chỉ đọc kho quan sát; lớp giải thích dùng Gemini API | `Chấp nhận` |
| `ADR-004` | Bố trí hai máy, không HA, cho phép nâng instance tạm thời | `Chấp nhận` |

Điều kiện chấp nhận đã được thỏa:

1. `GOV-097` thay benchmark tải bằng ngân sách tài nguyên và công bố giới hạn heap/container; thử tải thật được định tuyến sang Giai đoạn 5–6;
2. `GOV-098` cho phép nâng instance tạm thời nếu cấu hình hiện tại thiếu, không đổi `PA-6`;
3. tác động B11 trên `A6-v0.6` đã được Lê Văn Minh duyệt lại;
4. phép thử độc lập đã chạy lại và cửa nối RCA đã cập nhật;
5. Lê Văn Minh duyệt bản đồ tác động, chấp nhận ADR và đóng `B11-C` ngày 2026-09-04 (`GOV-099`).

## 10. Điểm mở còn lại

| ID | Nội dung | Owner / gate |
|---|---|---|
| `B10-OPEN-01` | `ASR-06` chưa có ngưỡng hiệu năng | Lê Văn Minh/vòng đo; benchmark sức chứa không được giả làm ngưỡng nghiệp vụ |
| `R0-OPEN-06` | Tập node/metric được RCA xếp hạng | Bộ RCA/B16 |
| `RES-039` | Người dùng và hình thức nhận kết quả RCA | Gate yêu cầu/giao diện riêng; chưa chốt tại B11-C |

`B11-A-OPEN-02` đóng theo hướng **không chọn đường cắt giữ chỗ–đơn** vì `PA-5` không được chọn. `B11-C-OPEN-01`, `-02`, `-03`, `-04`, `-05`, `-06` đã đóng. `B11-B-OPEN-03` đóng bằng `GOV-097`/`GOV-098`; `B11-B-OPEN-08` đóng bằng `GOV-091`. Quyền sở hữu dữ liệu và hợp đồng tương ứng vẫn chờ đúng gate.

## 11. Phép tự kiểm

- [x] Chỉ chọn trong sáu phương án đã duyệt; không tạo phương án thứ bảy.
- [x] Ghi đúng lựa chọn người thật ở `GOV-086`–`GOV-098`.
- [x] Không chốt schema, payload, endpoint, topic hoặc sequence Saga.
- [x] RCA riêng, chỉ đọc và không bị tính vào năm service nghiệp vụ.
- [x] Đã đọc và ghi kết quả tác động A1–A6; chỉ `A6` có tác động nội dung và đã đồng bộ thành `A6-v0.6`, phần thay đổi do B11 đã được duyệt lại.
- [x] Không dùng nguồn RCA để sinh phương án hoặc ranh giới.
- [x] Đóng `B11-B-OPEN-03` bằng quyết định ngân sách/nâng instance tại `GOV-097`/`GOV-098`; không ghi thành bằng chứng chịu tải.
- [x] Lê Văn Minh duyệt lại phần tác động B11 trong `A6-v0.6`.
- [x] Chạy lại phép thử độc lập và cập nhật cửa nối RCA.
- [x] Lê Văn Minh chấp nhận ADR và duyệt B11-C.

## 12. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `B11-C-v0.3` | 2026-09-04 | Đổi ba tên service (`GOV-094`); chốt `payment-service` điều phối Saga (`GOV-095`); chốt Gemini API, không HA, ngân sách tài nguyên và quyền nâng instance (`GOV-093`, `GOV-096`–`GOV-098`); cập nhật `A6-v0.6`, chạy lại phép thử độc lập và chấp nhận bốn ADR | Đóng gate B11 |
| `B11-C-v0.2` | 2026-09-04 | Ghi lựa chọn `PA-6`, năm service, một Saga, RCA riêng chỉ đọc, bố trí hai máy và nghĩa hẹp của ảnh nhận diện (`GOV-086`–`GOV-092`); tạo bốn ADR ở trạng thái `Đề xuất`; rà tác động A1–A6 và phép thử độc lập. Benchmark vẫn chặn chấp nhận ADR | Chốt lựa chọn, chưa đóng gate |
| `B11-C-v0.1` | 2026-09-01 | Mở cổng sau khi B11-A/B đều `APPROVED`; chưa chọn phương án hoặc tạo ADR | Mở gate |
