# B1 — Baseline quy trình chẩn đoán sự cố hiện tại

- **Ngày kiểm kê:** 2026-08-09
- **Nguồn:** Bối cảnh do chủ đồ án cung cấp, log cục bộ và kiểm kê mã nguồn tiền nhiệm tại commit `609fa2d37cad69aafa593b7db5b6cedeaf803da5`
- **Trạng thái:** Đã dựng được quy trình và một ca lỗi thật; thời gian chẩn đoán chưa có số đo

## Nhãn sử dụng

- **BÁO CÁO:** Quy trình hiện tại, điểm nghẽn và số liệu kiểm kê logging sau khi nhóm xác nhận.
- **NỘI BỘ/PHỤ LỤC:** Đường dẫn file, thống kê chi tiết và ca lỗi khởi động.
- **CẦN XÁC NHẬN:** Thời gian thực tế và 2–4 ca lỗi bổ sung do các thành viên từng xử lý.

## 1. Quy trình hiện tại được tái dựng

| Bước | Công việc | Dữ liệu dùng | Điểm nghẽn |
|---|---|---|---|
| 1 | Nhận báo lỗi hoặc nhận thấy hành vi bất thường | Mô tả người dùng, ảnh, trạng thái giao diện | Thiếu mã giao dịch/yêu cầu thống nhất để bắt đầu tìm |
| 2 | Xác định thành phần và khoảng thời gian nghi ngờ | Thời điểm ước lượng, tên service | Có thể phải tìm trên nhiều nguồn |
| 3 | Cố tái hiện lỗi | Dữ liệu thử, thao tác trên hệ thống | Lỗi cạnh tranh hoặc lỗi hạ tầng có thể không lặp lại theo yêu cầu |
| 4 | Mở Loki hoặc SSH vào máy và chờ log xuất hiện | Log ứng dụng/hạ tầng | Phụ thuộc tái hiện; khối lượng log lớn |
| 5 | Tìm exception, warning, error hoặc chuỗi nghiệp vụ | Tìm kiếm văn bản | Thông điệp chưa chuẩn hóa; thiếu liên kết xuyên service |
| 6 | Dò từ log tới đoạn mã liên quan | Tên lớp/phương thức/stack trace | Thực hiện thủ công; log không phải lúc nào cũng chỉ đúng vùng mã |
| 7 | So sánh trạng thái dữ liệu và tái chạy | CSDL, API, log | Có nguy cơ thay đổi dữ liệu nếu kiểm tra trực tiếp không có quy trình |
| 8 | Xác định nguyên nhân và đề xuất sửa | Mã nguồn, cấu hình, kinh nghiệm | Tri thức nằm trong đầu người xử lý, khó tái sử dụng |

Mô tả “vài chục phút đến vài giờ” hiện chỉ là tự báo cáo trong bối cảnh, **không được trình bày như số đo** cho tới khi có bảng thời gian của các ca cụ thể.

## 2. Kiểm kê logging ở repository tiền nhiệm

| Hạng mục | Kết quả quan sát | Ý nghĩa |
|---|---|---|
| Lời gọi log trong Java | `core-service`: 151 lời gọi/38 file; `user-service`: 70/11; `discovery-service`: 30/13 | Hệ thống có tài sản log đáng kể để tái sử dụng |
| `trace_id`, `correlation_id`, `span_id` hoặc MDC | Không tìm thấy trong bốn ứng dụng được kiểm kê | Chưa có bằng chứng về liên kết một giao dịch xuyên service |
| Cấu hình log | Chủ yếu cấu hình level theo package | Chưa thấy cấu hình JSON có schema trường thống nhất |
| Log thực tế tìm được | Một file `discovery-service/error.log`, 116 dòng, 10 dòng ERROR, 6 WARN; file không được Git theo dõi | Có thể dùng làm ca thử ban đầu sau khi khử nhạy cảm |

Các con số là kết quả tìm tĩnh tại commit baseline, không chứng minh mọi log runtime đều thiếu context; chúng chỉ cho thấy chuẩn logging xuyên service chưa được thể hiện trong mã/cấu hình đã kiểm kê.

## 3. Ca lỗi thực tế có bằng chứng

### INC-01 — Discovery service không khởi động

| Trường | Giá trị |
|---|---|
| Thời điểm trong log | 2026-05-09 18:59:16 +07:00 |
| Lớp lỗi | Ngoại lệ cấu hình/khởi tạo dependency |
| Tín hiệu | `APPLICATION FAILED TO START`, tiến trình Maven kết thúc với mã 1 |
| Nguyên nhân thể hiện trong log | Một dependency kiểu mô hình hội thoại có hai bean cùng phù hợp nên framework không chọn được bean duy nhất |
| Chuỗi ảnh hưởng | `chatController` → `chatService` → `moodDetector` → dependency không duy nhất |
| Bằng chứng | `D:\Project\flash-ticket-system\discovery-service\error.log` — không sao chép log thô sang repo mới |
| Thời gian phát hiện/chẩn đoán | Không có dữ liệu đáng tin cậy |
| Trạng thái hiện tại | Code baseline sau đó đã chuyển sang cấu hình provider khác; chưa xác định chính xác commit sửa ca này |

Ca này phù hợp để thử Drain ở bước sau vì có phần thông điệp ổn định xen lẫn tên bean/lớp biến đổi. Tuy nhiên, nó chỉ kiểm tra lỗi khởi động một service, chưa kiểm tra context xuyên service.

## 4. Ca ứng viên cần tái hiện — chưa phải sự cố đã xảy ra

| Mã ứng viên | Lớp | Kịch bản từ audit mã nguồn | Vì sao đáng tái hiện | Trạng thái bằng chứng |
|---|---|---|---|---|
| CAND-01 | Lỗi âm thầm | Frontend chờ vô hạn khi callback thanh toán bị chậm/mất | Có thể không sinh exception rõ nhưng chặn hành trình người mua | Audit code, chưa chạy |
| CAND-02 | CSDL/cạnh tranh | Scheduler hết hạn đơn chạy đồng thời với xác nhận thanh toán | Có thể tạo trạng thái thanh toán thành công nhưng đơn hết hạn | Audit code, chưa chạy |
| CAND-03 | Hạ tầng/message | Hai instance scheduler cùng khôi phục tồn kho | Có nguy cơ cộng tồn kho lặp | Audit code, chưa chạy |
| CAND-04 | Tích hợp | Email/QR không gửi được sau khi giao dịch thành công | Phân biệt thành công nghiệp vụ với lỗi thông báo | Phân tích luồng, chưa chạy |

Các ca ứng viên lấy từ `docs/concurrency_audit.md` và `docs/flow_trace_analysis.md` của repository tiền nhiệm. Trước khi đưa vào báo cáo như kết quả, nhóm phải tái hiện, lưu workload/cấu hình và xác nhận nguyên nhân bằng test hoặc log.

## 5. Bảng nhóm cần điền khi xử lý ca tiếp theo

| Mã ca | Tín hiệu bắt đầu | Các bước đã làm | Nguồn dữ liệu | Thời gian từng bước | Nguyên nhân thật | Trợ lý có/không |
|---|---|---|---|---|---|---|
| INC-02 | CẦN XÁC NHẬN |  |  |  |  | Không |
| INC-03 | CẦN XÁC NHẬN |  |  |  |  | Không |

Tối thiểu cần thêm 2 ca thật hoặc tái hiện được. Không cần đủ cả bốn lớp nếu hệ thống không có bằng chứng; báo cáo phạm vi tập ca đúng như thực tế.

## 6. BÁO CÁO — Đoạn mô tả baseline có thể sử dụng

Quy trình chẩn đoán hiện tại bắt đầu từ mô tả lỗi hoặc hành vi bất thường, sau đó người phát triển xác định thành phần nghi ngờ, cố tái hiện, tìm log trên hệ thống tập trung hoặc máy chủ và lần từ exception/thông điệp về đoạn mã liên quan. Kiểm kê repository tiền nhiệm cho thấy các service đã có nhiều lời gọi log, nhưng chưa thấy một schema log có cấu trúc hoặc mã tương quan được áp dụng thống nhất xuyên service. Vì vậy, một giao dịch đi qua nhiều thành phần chưa có đường nối rõ trong dữ liệu vận hành, còn việc chọn log liên quan và ánh xạ về mã nguồn phụ thuộc nhiều vào thao tác thủ công. Đây là baseline để thiết kế chuẩn logging và đánh giá trợ lý chẩn đoán; chưa đủ dữ liệu để kết luận mức thời gian được rút ngắn.
