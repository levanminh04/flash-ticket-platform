# A4 — Nháp câu hỏi nghiên cứu

- **Trạng thái:** `APPROVED` — phạm vi baseline Giai đoạn 1; ngưỡng đo và bản cuối chờ B9–B10
- **Người duyệt:** Lê Văn Minh
- **Ngày duyệt:** 2026-08-13
- **Đi vào báo cáo:** Chỉ đưa vào nếu mẫu báo cáo/giảng viên thấy câu hỏi nghiên cứu giúp mạch đánh giá rõ hơn
- **Nguyên tắc:** Một câu hỏi trung tâm, tối đa hai câu phụ, không chứa tên công nghệ

## Câu hỏi trung tâm

> Trong giới hạn hạ tầng của đồ án, một hệ thống đặt vé phân tán có thể duy trì các bất biến cốt lõi và phục hồi về trạng thái chấp nhận được ở mức độ nào khi gặp yêu cầu đồng thời, thông điệp lặp và lỗi từng phần?

### Bằng chứng cần có

- Số lần bán vượt sau workload đồng thời.
- Số tác dụng phụ lặp khi cùng yêu cầu/callback/message được gửi lại.
- Số lần check-in thành công trên cùng một vé khi nhiều thiết bị gửi gần đồng thời.
- Trạng thái cuối và thời gian phục hồi của các luồng lỗi đã chọn.
- Độ trễ, thông lượng và tỷ lệ lỗi trong cấu hình triển khai được công bố.

## Câu hỏi phụ 1

> Các cách phối hợp trạng thái giữa những bước của vòng đời vé đánh đổi thế nào giữa tính đúng đắn, khả năng phục hồi và chi phí hiệu năng?

**Phục vụ câu trung tâm:** Giải thích vì sao một phương án duy trì được bất biến và cái giá phải trả, thay vì chỉ trả lời “có hoạt động”.

**Bằng chứng:** Kết quả test bất biến, fault injection, p95/p99, thông lượng, tài nguyên, số lần thử lại/bù trừ và trạng thái cuối.

## Câu hỏi phụ 2

> Việc chuẩn hóa và liên kết dấu vết vận hành hỗ trợ xác định vùng lỗi và bước kiểm tra tiếp theo ở mức độ nào trên tập ca sự cố đã biết?

**Phục vụ câu trung tâm:** Bổ sung khả năng giải thích và kiểm tra các sự cố quan sát được trong quá trình đánh giá độ tin cậy; đây là nhánh hỗ trợ, không thay thế trục nhất quán vòng đời vé.

**Bằng chứng tối thiểu:** Tập ca có nguyên nhân thật; tỷ lệ context chứa tín hiệu cần thiết; đánh giá đúng vùng/nguyên nhân; tính hữu ích của bước kiểm tra; số lần bịa bằng chứng/hành động. Thời gian chẩn đoán thủ công so với có hỗ trợ chỉ đo thêm nếu thu được dữ liệu nhất quán.

## BÁO CÁO — Cách sử dụng

Nếu giữ A4, phần Kết luận phải trả lời lần lượt câu trung tâm và hai câu phụ bằng số liệu ở chương Đánh giá. Nếu không đủ bằng chứng cho câu phụ 2, hạ phạm vi thành “đánh giá tính đầy đủ và hữu ích trên tập ca giới hạn”, không tuyên bố rút ngắn thời gian chẩn đoán.

## Kết quả xác nhận và vấn đề còn mở

1. **Đã duyệt phạm vi:** giữ một câu hỏi trung tâm và hai câu hỏi phụ như trên; nhánh trợ lý chẩn đoán tiếp tục là nhánh hỗ trợ cho trục độ tin cậy của vòng đời vé.
2. **`OPEN` về trình bày:** chờ giảng viên/mẫu ĐATN xác nhận có cần một mục “Câu hỏi nghiên cứu” riêng hay chỉ trình bày dưới dạng mục tiêu và nội dung đánh giá.
3. **`OPEN` về ngưỡng:** chưa chốt ngưỡng định lượng ở A4; B9–B10 phải xác định phép đo và ngưỡng có căn cứ trước benchmark.
