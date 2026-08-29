# A8 — Khảo sát bộ dữ liệu thực nghiệm đã công bố

- **Phiên bản:** `A8-v0.2`
- **Trạng thái:** `DRAFT` — khảo sát phục vụ báo cáo hai tuần; chưa được duyệt
- **Người duyệt:** — (chờ Lê Văn Minh)
- **Ngày duyệt:** —
- **Đầu vào:** `A7-khai-niem-rca.md` (`A7-v0.2`); nguồn `T-06`–`T-08`, `T-14` trong `source-register.md`; [`docs/evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md`](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) `DH-DATA` — nguyên văn, `FACT`
- **Đi vào báo cáo:** phần Phương pháp — dữ liệu thực nghiệm
- **Ràng buộc:** tài liệu này khảo sát bộ dữ liệu đã công bố. Nó **không** sinh dữ liệu, **không** chốt bộ dữ liệu chính thức của đồ án, **không** quyết định vai trò hệ thống của đồ án.

> Tài liệu này trả lời trực tiếp mục 4 trong thư định hướng: *"Tìm những bộ dữ liệu đã được công bố để thực nghiệm giải pháp."*

---

## 1. Bảng đối chiếu tổng hợp

| Nguồn | Mã | Ca lỗi | Metric | Log | Trace | Nhãn | Dung lượng | Giấy phép |
|---|---|---|---|---|---|---|---|---|
| Artifact ASE 2024 | `T-07` | ~375 hệ thật + dữ liệu tổng hợp | ✔ | ✘ | ✘ | service nguyên nhân | 606,2 MB | CC-BY 4.0 |
| RCAEval RE1 | `T-06` | 375 | ✔ | ✘ | ✘ | service + chỉ số | — | MIT |
| RCAEval RE2 | `T-06` | 270 | ✔ | ✔ | ✔ | service + chỉ số | — | MIT |
| RCAEval RE3 | `T-06` | 90 | ✔ | ✔ | ✔ | service + chỉ số | — | MIT |
| LEMMA-RCA | `T-08` | 51 | ✔ | ✔ | ✔ | nguyên nhân theo ca | 235,5 MB – 765 GB | — |

Tổng ba bộ RCAEval: **735 ca lỗi, 11 loại lỗi, 3 hệ thống**. Trọn bộ tải qua Hugging Face là **3,4 GB**; qua Zenodo là ~5,2 GB nén, giải nén thành **38 GB**.

---

## 2. Hai nguồn giảng viên gửi

### 2.1 Zenodo 13305663 — artifact bài ASE 2024 (`T-07`)

Chín thư mục nén, tổng 606,2 MB.

**Dữ liệu tổng hợp** — sinh bằng mô hình, dùng để kiểm thuật toán phát hiện nhân quả:

| Bộ | Biến thể |
|---|---|
| CIRCA | 10 nút và 50 nút, sinh bằng mô hình VAR có chèn lỗi |
| RCD | 10 nút và 50 nút, chuỗi rời rạc, chèn lỗi bằng cách đổi phân phối xác suất |
| CausIL | 10 nút và 50 nút, sinh từ bộ hồi quy đã huấn luyện, mô phỏng microservice |

**Dữ liệu hệ thật** — thu bằng Prometheus và cAdvisor:

| Hệ thống | Dung lượng |
|---|---|
| Online Boutique | 31,0 MB |
| Sock Shop 1 | 3,5 MB |
| Sock Shop 2 | 79,1 MB |
| Train Ticket | 279,7 MB |

Năm loại lỗi được chèn: CPU hog, memory leak, disk IO stress, network delay, packet loss.

**Phát hiện phải ghi nhận (`FACT`): bộ này chỉ chứa metric. Không có log, không có trace.**

Mục tiêu nghiên cứu số 2 trong thư định hướng yêu cầu *"ánh xạ log giao dịch và dấu vết vận hành (trace) lên đồ thị để phát hiện bất thường"*. Với riêng bộ này, mục tiêu đó **không thực hiện được** — không phải vì thiếu phương pháp mà vì thiếu dữ liệu đầu vào.

Cách xử lý đề xuất ở §3. Đây là điểm cần xác nhận với giảng viên, không tự quyết.

### 2.2 LEMMA-RCA (`T-08`)

51 ca lỗi, hai lĩnh vực:

| Lĩnh vực | Hệ thống | Quy mô | Ca lỗi |
|---|---|---|---|
| CNTT | Nền tảng đánh giá sản phẩm | 216 pod trên 6 nút | 10 lỗi mô phỏng, tính chung cả hai hệ CNTT |
| CNTT | Nền tảng điện toán đám mây | 11 nút | |
| Công nghiệp | SWaT — xử lý nước | 51 cảm biến | 16 tấn công thật |
| Công nghiệp | WADI — phân phối nước | 123 cảm biến/cơ cấu chấp hành | 15 tấn công thật |

Dữ liệu: log hàng triệu bản ghi đã ở dạng template có cấu trúc; metric hơn 100 000 mốc thời gian mỗi hệ; trace gồm chỉ số hiệu năng như độ trễ và tỷ lệ lỗi, thu bằng JMeter. Nguồn thu: Prometheus, ElasticSearch, CloudWatch và cảm biến trực tiếp.

Bộ độ đo: `Precision@K`, `MAP@K`, `MRR`. **Khác bộ độ đo của `T-06`** — không trộn số giữa hai nguồn vào một bảng.

**Đánh giá khả năng dùng:**

| Mặt | Nhận xét |
|---|---|
| Thuận | Có đủ ba loại dữ liệu; là bộ duy nhất trong hai nguồn giảng viên gửi có log và trace |
| Nghịch | Chỉ 51 ca lỗi — nhỏ hơn 735 ca của `T-06` một bậc |
| Nghịch | Phần lớn ca lỗi (31/51) thuộc hệ xử lý nước, **không phải giao dịch trực tuyến**; nằm ngoài phạm vi đề tài |
| Nghịch | Dung lượng lên tới 765 GB ở một số bộ con — vượt khả năng lưu trữ hiện có |

---

## 3. RCAEval — nguồn bổ sung đề xuất

`T-06` do **cùng nhóm tác giả** với `T-07` phát triển (Luan Pham và cộng sự), và là bản mở rộng của chính bộ dữ liệu giảng viên gửi.

| | `T-07` (giảng viên gửi) | RE2 | RE3 |
|---|---|---|---|
| Ca lỗi | ~375 hệ thật | 270 | 90 |
| Metric | ✔ | ✔ 77–376 chỉ số | ✔ 68–322 chỉ số |
| **Log** | ✘ | ✔ 8,6–26,9 triệu dòng | ✔ 1,7–2,7 triệu dòng |
| **Trace** | ✘ | ✔ 39,6–76,7 triệu span | ✔ 4,5–4,7 triệu span |
| Loại lỗi | 5 loại hạ tầng | 6 loại hạ tầng | 5 loại **mức mã nguồn** |
| Nhãn mịn | — | ✔ chỉ số nguyên nhân | ✔ chỉ số nguyên nhân |

Ba hệ thống nguồn giống hệt bộ giảng viên gửi: Online Boutique (12 service), Sock Shop (15), Train Ticket (64, nền tảng Java — `T-14`).

**Đề xuất, ở mức `CANDIDATE`:** dùng RE2 và RE3 để bổ sung phần log và trace mà `T-07` không có, giữ nguyên `T-07` cho phần metric và cho việc đối chiếu với kết quả bài ASE 2024.

Cách trình bày với giảng viên: đây là **bổ sung theo đúng yêu cầu của cô**, dùng nguồn của chính nhóm tác giả cô đã chỉ tới — không phải thay thế nguồn cô gửi.

### 3.1 Cấu trúc dữ liệu

Mỗi ca lỗi là một thư mục:

| File | Nội dung | Có ở |
|---|---|---|
| `metrics.json` | Chuỗi số liệu theo thời gian; cột `time`; tên chỉ số dạng `<service>_<tài nguyên>`, ví dụ `cart_cpu` | RE1, RE2, RE3 |
| `inject_time.txt` | Mốc chèn lỗi, Unix timestamp | RE1, RE2, RE3 |
| `logs.csv` | Log | RE2, RE3 |
| `traces.csv` | Trace | RE2, RE3 |

Thư viện yêu cầu dữ liệu ở dạng `pandas.DataFrame` với cột bắt buộc tên `time`.

### 3.2 Điều kiện chạy

| Hạng mục | Yêu cầu | Máy đã kiểm |
|---|---|---|
| CPU | 8 nhân | i5-1240P, 12 nhân / 16 luồng ✔ |
| RAM | 16 GB | 15,7 GB ✔ |
| Đĩa | ~50 GB | ổ D còn 73,3 GB ✔ (ổ C chỉ còn 41,6 GB) |
| Hệ điều hành | Ubuntu 20.04 hoặc 22.04 | WSL2 đã có, cần thêm distro Ubuntu ⚠️ |
| Python | 3.12 | hiện 3.9.13, cần cài thêm ⚠️ |

Hai máy EC2 `m7i-flex.large` (2 vCPU / 8 GiB mỗi máy) **không đủ** và không cộng tài nguyên được vì là hai máy rời.

**Khuyến nghị tải qua Hugging Face (3,4 GB), không qua Zenodo (38 GB sau giải nén).** Đặt dữ liệu trên ổ D.

---

## 4. Ba hệ thống sinh dữ liệu

Nhóm **không cần cài đặt** các hệ thống này — dữ liệu đã thu sẵn. Bảng dưới chỉ để hiểu quy mô, phục vụ việc đọc mức sàn ngẫu nhiên theo `A7` §7.

| Hệ thống | Service | Ghi chú |
|---|---|---|
| Online Boutique | 12 | Nhiều ngôn ngữ; dữ liệu nhỏ nhất → **nên chạy thử đầu tiên** |
| Sock Shop | 15 | Go, Java Spring, Node.js |
| Train Ticket | 64 | Nền tảng Java; hệ lớn nhất → **bộ phân định thắng thua** |

---

## 5. Phạm vi loại lỗi — điểm cần ghi nhận

Toàn bộ loại lỗi trong mọi bộ dữ liệu khảo sát ở trên:

> CPU hog · memory leak · disk IO stress · network delay · packet loss · socket · năm loại lỗi mức mã nguồn

**`FACT`: đây đều là lỗi kỹ thuật và lỗi hạ tầng. Không bộ nào chèn lỗi vi phạm bất biến nghiệp vụ.**

Hệ quả ở mức `CANDIDATE`, xử lý tại `A10-CAND-02`: một lớp lỗi nghiệp vụ chưa xuất hiện trong benchmark công khai có thể là khoảng trống đáng khai thác.

> *Sửa 2026-08-29.* Câu này trước đó kết bằng *"phụ thuộc quyết định về vai trò hệ thống của đồ án, hiện là `A7-OPEN-02`"* — **`A7-OPEN-02` đã đóng** theo `RES-032` và `A7` §8 ghi rõ điều đó, nên đây là tham chiếu treo tới một điểm mở không còn tồn tại.
>
> **Vai trò hệ thống nay đã rõ:** `DH-MT1` đặt FlashTicket vào mục tiêu đầu tiên, `A5` §1 xếp nó ở ô **phương tiện**, và `RES-023` chốt lộ trình ba mức. **Điều kiện để khai thác được lớp lỗi này cũng đã có chỗ trú ở bộ hệ thống:** `ASR-15` (`RES-043`, 2026-08-28) cấm phương án kiến trúc làm cho việc **chèn lỗi có kiểm soát** trở nên bất khả thi — không chèn được lỗi thì không tạo được ca vi phạm bất biến có đáp án. Đây là **ghi nhận một chiều**, không phải yêu cầu do tệp này sinh ra: `ASR-15` truy về `RES-023` và `B15`, không truy về bộ RCA.

---

## 6. Vấn đề `OPEN`

| ID | Vấn đề | Gate xử lý |
|---|---|---|
| `A8-OPEN-01` | Bộ dữ liệu giảng viên gửi không có log/trace trong khi mục tiêu 2 yêu cầu cả hai. Đề xuất bổ sung RE2/RE3 chưa được giảng viên xác nhận | Câu hỏi gửi giảng viên trong báo cáo hai tuần |
| `A8-OPEN-02` | Chưa xác minh cận trên số chỉ số của RE2 từ bảng 2 toàn văn; kế thừa `A7-OPEN-01` | Trước khi đưa số vào báo cáo |
| `A8-OPEN-03` | Chưa xác minh giấy phép của `T-08` | Trước khi sử dụng bộ này |
| `A8-OPEN-04` | **Nội bộ đã chốt bộ chính là `RE2`** (`RES-022`), vì `RE1` và bộ Zenodo cô gửi chỉ có metric nên không kiểm được `DH-MT2`/`DH-MT3`. Điểm còn mở là **xin cô xác nhận** việc dùng `RE2` thay cho bộ cô gửi | Sau phản hồi của giảng viên; câu hỏi số 1 trong báo cáo hai tuần |
| `A8-OPEN-05` | Chưa kiểm chứng thực tế thời gian tải và thời gian chạy trên máy nhóm | `E1`, tuần 1 |
| `A8-OPEN-06` | `RES-022` chốt bộ `RE2` nhưng **chưa chốt hệ nào trong `RE2` ở mức quyết định**. `docs/report/bao-cao-2-tuan-2026-09-05.md` §5.4 đã đề xuất **Online Boutique** với lý do rủi ro bộ nhớ thấp nhất, nhưng đó là bản `DRAFT` chưa nộp và chưa có dòng trong sổ quyết định — nên vẫn ở `CANDIDATE`. Lựa chọn này quyết định số ứng viên, do đó quyết định mức sàn ngẫu nhiên và cách đọc mọi điểm số (`A7` §7, `R0` §2.2). Trùng nội dung với `A10-OPEN-05` | Trước `E1` |

---

## 7. Phép tự kiểm

- [x] Trả lời trực tiếp `DH-DATA` trong tạo tác nguyên văn thư định hướng.
- [x] Phân biệt rõ hai việc: chọn **bộ** (`RE2`, đã chốt ở `RES-022`) và chọn **hệ trong bộ** (chưa chốt, `A8-OPEN-06`).
- [x] Hai nguồn giảng viên gửi được khảo sát riêng và đặt trước nguồn bổ sung.
- [x] Mỗi con số dẫn về một mã nguồn trong `source-register.md`.
- [x] Điểm thiếu log/trace của nguồn giảng viên gửi được ghi là `FACT` kèm hệ quả, không kèm phán xét.
- [x] Đề xuất bổ sung giữ ở `CANDIDATE` tại thời điểm `A8-v0.1`. **Cập nhật:** `RES-022` sau đó chốt `RE2` là bộ chính ở phạm vi nội bộ; phần chờ giảng viên xác nhận vẫn `OPEN` tại `A8-OPEN-04`.
- [x] Điều kiện chạy đối chiếu với cấu hình máy đã kiểm thực tế.
- [x] Chỉ dùng tài liệu đã công bố làm căn cứ; không dùng nguồn đối chiếu cài đặt nào.
- [ ] Xác minh `A8-OPEN-02` và `A8-OPEN-03`.
- [ ] Lê Văn Minh duyệt.

---

## 8. Nhật ký phiên bản

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A8-v0.2` | 2026-08-26 | Khai lại đầu vào trỏ về tạo tác nguyên văn thư định hướng (`DH-DATA`) và `A7-v0.2`; mở `A8-OPEN-06` tách bạch việc chọn **bộ** khỏi việc chọn **hệ trong bộ** — `RES-022` chỉ chốt vế thứ nhất; thêm một ô tự kiểm | Đồng bộ `DH-TEN` + làm rõ phạm vi quyết định |
| `A8-v0.1` | 2026-08-23 | Bản đầu: bảng đối chiếu 5 bộ dữ liệu; khảo sát riêng hai nguồn giảng viên gửi; ghi nhận `T-07` chỉ có metric; đề xuất bổ sung RE2/RE3 ở mức `CANDIDATE`; cấu trúc dữ liệu và điều kiện chạy đối chiếu máy thật; ghi nhận mọi bộ chỉ có lỗi kỹ thuật | Tạo mới theo định hướng giảng viên ngày 2026-08-22 |
