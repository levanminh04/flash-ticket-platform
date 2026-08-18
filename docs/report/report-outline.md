# Khung nội dung báo cáo

**Trạng thái:** Khung nội dung sống; chưa đánh số chương cho tới khi nhận mẫu ĐATN hiện hành và xác nhận của giảng viên hướng dẫn.

## Bối cảnh và vấn đề

- Bối cảnh hệ thống đặt vé sự kiện trực tuyến.
- Tính cấp thiết của nhất quán và độ tin cậy trong vòng đời vé.
- Hiện trạng quy trình chẩn đoán lỗi thủ công.
- Giới hạn của khảo sát hệ thống bên ngoài.

**Nguồn dự kiến:** A1, A2, B1 và bằng chứng khảo sát.

## Mục tiêu, phạm vi và phương pháp

- Mục tiêu tổng quát và mục tiêu đo được.
- Phạm vi nghiệp vụ và phần loại trừ.
- Câu hỏi nghiên cứu.
- Phương pháp thu thập bằng chứng và đánh giá.

**Nguồn dự kiến:** A3–A7.

## Cơ sở lựa chọn

Chỉ trình bày khái niệm thực sự được dùng trong quyết định hoặc phép đánh giá: phân rã microservice, nhất quán phân tán, Saga/Outbox, idempotency, logging có cấu trúc, Drain và LLM API.

## Phân tích yêu cầu và nghiệp vụ

- Tác nhân, use case và đặc tả use case.
- Quy trình nghiệp vụ có nhánh thất bại.
- Từ điển miền, mô hình miền và bất biến.
- Yêu cầu chức năng, ASR và kịch bản chất lượng.
- Thiết kế tương tác cho các luồng chính sau khi phạm vi được chốt.

**Nguồn dự kiến:** B2–B10.

## Thiết kế hệ thống

- C4 System Context và Container.
- Lập luận ranh giới context, sau đó lập luận riêng cách gộp/tách thành service vật lý.
- Sở hữu dữ liệu, schema độc lập và ERD đích của từng service.
- API, sự kiện và các luồng Saga được chọn.
- Check-in trực tuyến, idempotency và xử lý cạnh tranh.
- Logging, Drain, context builder, khử nhạy cảm và trợ lý chẩn đoán.
- Kiến trúc triển khai trên hai EC2 sau khi so sánh/đo thử.
- UML cần thiết: use case, hoạt động, tuần tự, lớp, trạng thái và triển khai theo đúng mục đích từng hình.

**Nguồn dự kiến:** B5/B7 cho ranh giới khái niệm và bất biến; B10/B11 cùng ADR cho kiến trúc/service vật lý; B12–B18 cho dữ liệu, hợp đồng, tương tác, logging và trợ lý. B5.5 là sổ đối chiếu nội bộ, không phải nguồn sinh lập luận hoặc nguồn trình bày của báo cáo.

## Hiện thực

- Thành phần đã xây và phương pháp phát triển phần mềm được áp dụng thực tế.
- Cách các service/module, schema, API/sự kiện và cơ chế nhất quán hiện thực kiến trúc đích.
- Sai lệch có ý nghĩa giữa thiết kế đã chấp nhận và hệ thống cuối, cùng ADR thay thế nếu có.

Phần này mô tả trạng thái cuối, không kể lịch sử chuyển file/package, bảng hay commit. Báo cáo không cần đưa ra tuyên bố về tỷ lệ mã viết mới; nguồn và phạm vi tái sử dụng chỉ được trình bày khi biểu mẫu chính thức yêu cầu hoặc khi được hỏi trực tiếp.

## Kiểm thử và đánh giá

- Kiểm thử chức năng và hợp đồng.
- Kiểm thử bất biến: oversell, callback/thông điệp lặp, check-in đồng thời.
- Hiệu năng, độ trễ, thông lượng và khả năng phục hồi trong cấu hình được công bố.
- Đánh giá chất lượng trợ lý trên tập ca lỗi thật/kiểm soát được.
- Giới hạn hiệu lực và các kết quả không đạt.

**Nguồn dự kiến:** B15, B17–B19 và `experiments/`.

## Kết luận và hướng phát triển

- Đối chiếu mục tiêu với kết quả.
- Đóng góp kỹ thuật và đóng góp của từng thành viên.
- Hạn chế, rủi ro còn lại và hướng phát triển.
