# B1 — Baseline quy trình chẩn đoán sự cố hiện tại

- **Ngày kiểm kê:** 2026-08-09
- **Nguồn:** Kinh nghiệm xử lý sự cố do chủ đồ án cung cấp; log và mã nguồn phát triển được kiểm kê nội bộ tại commit `609fa2d37cad69aafa593b7db5b6cedeaf803da5`
- **Trạng thái:** `APPROVED` — baseline quy trình và INC-01; độ bao phủ tập ca và thời gian chẩn đoán còn `OPEN`
- **Người duyệt:** Lê Văn Minh
- **Ngày duyệt:** 2026-08-13

## Nhãn sử dụng

- **BÁO CÁO:** Quy trình chẩn đoán thủ công và điểm nghẽn sau khi nhóm xác nhận; kết quả cuối phải dùng log/ca lỗi sinh từ phiên bản ĐATN.
- **NỘI BỘ:** Đường dẫn file, thống kê tài sản phát triển và ca lỗi khởi động dùng để thiết kế pipeline ban đầu; không đưa vào báo cáo như kết quả đánh giá.
- **`OPEN`:** Thời gian thực tế và tối thiểu 2 ca lỗi bổ sung/tái hiện được; nếu không thu được thì phải công bố giới hạn một ca và không kết luận về rút ngắn thời gian.

## Ranh giới chức năng đã xác nhận

Trợ lý hỗ trợ thu thập và liên kết dấu vết, đề xuất nguyên nhân khả dĩ cùng bước kiểm tra tiếp theo; **không cam kết loại bỏ việc tái hiện lỗi, không tự kết luận nguyên nhân cuối cùng và không tự sửa hệ thống**. Người vận hành hoặc nhà phát triển vẫn phải xác minh bằng chứng, tái hiện khi dữ liệu quan sát chưa đủ và kiểm tra lại sau khi sửa.

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

## 2. Kiểm kê tài sản logging phát triển — NỘI BỘ

| Hạng mục | Kết quả quan sát | Ý nghĩa |
|---|---|---|
| Lời gọi log trong Java | `core-service`: 151 lời gọi/38 file; `user-service`: 70/11; `discovery-service`: 30/13 | Có dữ liệu phát triển để nhận diện loại thông điệp và thiết kế chuẩn mới |
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

Ca này là một **ca kiểm chứng có đáp án gốc**, không đại diện cho mọi loại lỗi. Nó phù hợp làm smoke test cho Drain/context builder vì có phần thông điệp ổn định xen lẫn tên bean/lớp biến đổi và có nguyên nhân thật để đối chiếu. Tuy nhiên, nó chỉ kiểm tra lỗi khởi động một service, chưa kiểm tra context xuyên service và không đủ để kết luận mức hữu ích chung của trợ lý.

## 4. Ca ứng viên cần tái hiện — chưa phải sự cố đã xảy ra

| Mã ứng viên | Lớp | Kịch bản từ audit mã nguồn | Vì sao đáng tái hiện | Trạng thái bằng chứng |
|---|---|---|---|---|
| CAND-01 | Lỗi âm thầm | Frontend chờ vô hạn khi callback thanh toán bị chậm/mất | Có thể không sinh exception rõ nhưng chặn hành trình người mua | Audit code, chưa chạy |
| CAND-02 | CSDL/cạnh tranh | Scheduler hết hạn đơn chạy đồng thời với xác nhận thanh toán | Có thể tạo trạng thái thanh toán thành công nhưng đơn hết hạn | Audit code, chưa chạy |
| CAND-03 | Hạ tầng/message | Hai instance scheduler cùng khôi phục tồn kho | Có nguy cơ cộng tồn kho lặp | Audit code, chưa chạy |
| CAND-04 | Tích hợp | Email/QR không gửi được sau khi giao dịch thành công | Phân biệt thành công nghiệp vụ với lỗi thông báo | Phân tích luồng, chưa chạy |

Các ca ứng viên lấy từ tài liệu audit nội bộ. Chúng chỉ gợi ý kịch bản. Trước khi đưa vào báo cáo như kết quả, nhóm phải tái hiện trên phiên bản ĐATN, lưu workload/cấu hình và xác nhận nguyên nhân bằng test hoặc log.

## 5. Bảng nhóm cần điền khi xử lý ca tiếp theo

| Mã ca | Tín hiệu bắt đầu | Các bước đã làm | Nguồn dữ liệu | Thời gian từng bước | Nguyên nhân thật | Trợ lý có/không |
|---|---|---|---|---|---|---|
| INC-02 | CẦN XÁC NHẬN |  |  |  |  | Không |
| INC-03 | CẦN XÁC NHẬN |  |  |  |  | Không |

Tối thiểu cần thêm 2 ca thật hoặc tái hiện được. Không cần đủ cả bốn lớp nếu hệ thống không có bằng chứng; báo cáo phạm vi tập ca đúng như thực tế.

## 6. BÁO CÁO — Đoạn mô tả baseline có thể sử dụng

Quy trình chẩn đoán thủ công bắt đầu từ mô tả lỗi hoặc hành vi bất thường, sau đó người phát triển xác định thành phần nghi ngờ, cố tái hiện, tìm log trên hệ thống tập trung hoặc máy chủ và lần từ exception/thông điệp về đoạn mã liên quan. Khi log của cùng một giao dịch không có schema và mã tương quan thống nhất xuyên service, việc chọn bản ghi liên quan, dựng lại trình tự và xác định vùng mã phụ thuộc nhiều vào thao tác thủ công. Đây là cơ sở để thiết kế chuẩn logging và pipeline trợ lý chẩn đoán nhằm tự động hóa một phần việc gom dấu vết, dựng context và hình thành giả thuyết có bằng chứng. Trợ lý không thay thế bước xác minh của con người và không bảo đảm loại bỏ việc tái hiện khi log chưa đủ hoặc khi cần kiểm tra bản sửa. Mức cải thiện chỉ được kết luận sau khi đánh giá trên các ca lỗi có nguyên nhân biết trước của phiên bản ĐATN; mô tả “vài chục phút đến vài giờ” không được dùng như số đo nếu chưa có bảng thời gian cụ thể.
