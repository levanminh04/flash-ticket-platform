# Thư định hướng của giảng viên hướng dẫn — 2026-08-22

> **BẰNG CHỨNG LỊCH SỬ — cập nhật hiệu lực 18/09/2026.** Nguyên văn thư và mã DH-* bên dưới giữ đúng nghĩa tại ngày 22/08. Tên/nhiệm vụ hiện hành đã được Minh thay bằng [nguồn DT18](../project-direction/2026-09-18-de-tai-va-nhiem-vu.md), `PRJ-025/026`. Không dùng tên cũ hoặc lời diễn giải ở §3 làm định hướng hiện hành trái DT18.

- **Loại tạo tác:** bằng chứng gốc, `FACT`
- **Nguồn:** thư điện tử của cô Liên, giảng viên hướng dẫn
- **Ngày nhận:** 2026-08-22
- **Ngày lưu vào repo:** 2026-08-25, do Lê Văn Minh cung cấp nguyên văn
- **Trạng thái:** chép nguyên văn, **không biên tập, không tóm tắt, không diễn giải**

> **Vì sao tệp này tồn tại.** Đây là **văn bản định nghĩa đề tài tại thời điểm 22/08/2026**. Trước ngày 2026-08-25 nó chỉ tồn tại trong hộp thư, trong khi `A6`, `A7`, `A8`, `A9`, `A10` và `R0` đều khai nó là đầu vào — nghĩa là mọi trích dẫn "mục tiêu số 2/3/4" trong repo không kiểm chứng lại được. Việc lưu tệp này đóng `RES-008`, `A6-OPEN-05` và `R0-OPEN-04`.
>
> Mọi tài liệu trích dẫn thư định hướng lịch sử 22/08 phải trỏ về tệp này, không trỏ về trí nhớ hoặc về một bản tóm tắt trung gian.

---

## 1. Nguyên văn

```text
Chào em,

Cô có mấy nhận xét sau:

Đọc báo cáo cô cảm tưởng bản thảo em gửi đây do AI viết còn khá mơ hồ, phần
tổng hợp của các em cũng chưa thể hiện rõ liên kết logic giữa các nội dung
đưa ra. Các em lưu ý vấn đề này trong những nghiên cứu và tiếp theo.

Cô định hướng như sau:

Theo ý tưởng nghiên cứu của nhóm, các em sẽ làm đề tài "Chẩn đoán nguyên nhân
gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"

Mục tiêu nghiên cứu:

Xây dựng mô hình đồ thị phụ thuộc cho hệ thống giao dịch trực tuyến (các dịch
vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác).

Ánh xạ log giao dịch và dấu vết vận hành (trace) lên đồ thị để phát hiện bất
thường.

Đề xuất cơ chế lan truyền và xếp hạng nguyên nhân gốc dựa trên bằng chứng
log/trace.

Tích hợp AI hỗ trợ giải thích để trợ lý có thể diễn giải nguyên nhân và gợi ý
bước xử lý

Dataset thử nghiệm tham khảo:

Tìm những bộ dữ liệu đã được công bố để thực nghiệm giải pháp. Ví dụ có dataset
công khai (Train Ticket, Online Boutique, Sock Shop) mô phỏng hệ thống giao dịch
trực tuyến, có thể dùng làm dữ liệu thử nghiệm.

Dataset Artifact for paper "Root Cause Analysis for Microservice System based on
Causal Inference: How Far Are We?" | Zenodo

LEMMA-RCA: A Large Multi-modal Multi-domain Dataset for Root Cause Analysis

Độ đo thực nghiệm: Khảo sát các độ đo thực nghiệm theo qui chuẩn để thực nghiệm
so sánh giải pháp đề xuất so với các giải pháp đã có cơ sở. Chạy thực nghiệm so
sánh các giải pháp tư vấn khác nhau khi chạy trên cùng bộ dataset thì kết quả
khác nhau ntn.

Các em thực hiện nghiêm túc việc này trong 2 tuần, sau báo cáo cô chi tiết nội
dung trên để cô hướng dẫn tiếp. Cô sẽ định hướng dần.
```

---

## 2. Tách thành các mục có mã để trích dẫn

Bảng này **chỉ đánh số** cho các câu ở §1 để tài liệu khác trích dẫn được. Không thêm chữ nào.

| Mã | Nội dung nguyên văn |
|---|---|
| `DH-TEN` | Đề tài: *"Chẩn đoán nguyên nhân gốc sự cố giao dịch trực tuyến bằng đồ thị phụ thuộc"* |
| `DH-MT1` | *"Xây dựng mô hình đồ thị phụ thuộc cho hệ thống giao dịch trực tuyến (các dịch vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác)."* |
| `DH-MT2` | *"Ánh xạ log giao dịch và dấu vết vận hành (trace) lên đồ thị để phát hiện bất thường."* |
| `DH-MT3` | *"Đề xuất cơ chế lan truyền và xếp hạng nguyên nhân gốc dựa trên bằng chứng log/trace."* |
| `DH-MT4` | *"Tích hợp AI hỗ trợ giải thích để trợ lý có thể diễn giải nguyên nhân và gợi ý bước xử lý"* |
| `DH-DATA` | *"Tìm những bộ dữ liệu đã được công bố để thực nghiệm giải pháp. Ví dụ có dataset công khai (Train Ticket, Online Boutique, Sock Shop) mô phỏng hệ thống giao dịch trực tuyến, có thể dùng làm dữ liệu thử nghiệm."* |
| `DH-DATA-1` | Nguồn cô gửi: *"Dataset Artifact for paper 'Root Cause Analysis for Microservice System based on Causal Inference: How Far Are We?' | Zenodo"* — tương ứng `T-07` |
| `DH-DATA-2` | Nguồn cô gửi: *"LEMMA-RCA: A Large Multi-modal Multi-domain Dataset for Root Cause Analysis"* — tương ứng `T-08` |
| `DH-DO` | *"Khảo sát các độ đo thực nghiệm theo qui chuẩn để thực nghiệm so sánh giải pháp đề xuất so với các giải pháp đã có cơ sở. Chạy thực nghiệm so sánh các giải pháp tư vấn khác nhau khi chạy trên cùng bộ dataset thì kết quả khác nhau ntn."* |
| `DH-MOC` | *"Các em thực hiện nghiêm túc việc này trong 2 tuần, sau báo cáo cô chi tiết nội dung trên để cô hướng dẫn tiếp. Cô sẽ định hướng dần."* |
| `DH-PB` | Phê bình bản trước: *"cảm tưởng bản thảo em gửi đây do AI viết còn khá mơ hồ, phần tổng hợp của các em cũng chưa thể hiện rõ liên kết logic giữa các nội dung đưa ra."* |

---

## 3. Ba điểm phải đọc đúng, không được diễn giải rộng ra

Mục này ghi **cách đọc**, không ghi kết luận thiết kế. Nó tồn tại vì vòng làm việc trước đã đọc sai một trong ba điểm này.

**3.1 — `DH-MT1` gọi đích danh hệ thống của nhóm.** Cụm *"các dịch vụ đặt vé, thanh toán, xác thực, cơ sở dữ liệu, API đối tác"* mô tả chính FlashTicket. Hệ thống của nhóm là **đối tượng của mục tiêu đầu tiên**, không phải một đề tài tách rời chạy song song.

**3.2 — `DH-DATA` đặt bộ dữ liệu công bố vào vai công cụ đo.** Câu là *"để **thực nghiệm giải pháp**"*, và `DH-DO` là *"so sánh **giải pháp đề xuất** so với các giải pháp đã có cơ sở"*. Cả hai giả định nhóm **có một giải pháp**. Bộ dữ liệu là cái thước, không phải đối tượng nghiên cứu.

**3.3 — `DH-MT2` và `DH-MT3` đều đòi log và trace.** Bộ dữ liệu nào chỉ có metric thì **không kiểm được hai mục tiêu này**. Đây là ràng buộc cứng lên việc chọn bộ dữ liệu, và là lý do bộ chính không thể là một bộ metric-only.

**3.4 — `DH-MOC` giới hạn phạm vi của vòng này.** Cô yêu cầu báo cáo chi tiết *nội dung trên* trong 2 tuần rồi cô hướng dẫn tiếp, và nói rõ *"Cô sẽ định hướng dần"*. Đây là một vòng để cô chỉnh hướng, không phải cam kết toàn bộ đồ án.

---

## 4. Việc tệp này đóng lại

| ID | Trạng thái sau khi có tệp này |
|---|---|
| `RES-008` | Đóng — thư đã là tạo tác trong repo |
| `A6-OPEN-05` | Đóng |
| `R0-OPEN-04` | Đóng |

Các tài liệu đang trích "mục tiêu số 2/3/4" phải được sửa để trỏ về mã `DH-MT2`, `DH-MT3`, `DH-MT4` của tệp này.

---

## 5. Phép tự kiểm

- [x] Chép nguyên văn, không biên tập câu chữ nào.
- [x] Phần đánh mã ở §2 chỉ gán mã, không thêm chữ.
- [x] §3 ghi cách đọc và nói rõ nó không phải kết luận thiết kế.
- [x] Không suy ra quyết định thiết kế nào trong tệp này.
- [x] Sửa `A6`–`A10`, `R0` để trích dẫn trỏ về mã `DH-*`. *(Đã rà lại toàn bộ `docs/research/`, `docs/research-rca/` và `docs/evidence/` ngày 2026-08-29: không còn chỗ nào trích “mục tiêu số 2/3/4” ngoài chính tệp này — `GOV-051`.)*
