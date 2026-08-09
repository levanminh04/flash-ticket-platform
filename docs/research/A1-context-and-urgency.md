# A1 — Bối cảnh và tính cấp thiết

- **Trạng thái:** Nháp có nguồn, cần chủ đồ án duyệt cách diễn đạt
- **Đi vào báo cáo:** Phần Đặt vấn đề/Tính cấp thiết
- **Nguồn nội bộ:** B1 khảo sát công khai và baseline chẩn đoán ngày 2026-08-09

## 1. Bốn nước lập luận

### Nước 1 — Vấn đề có thật và có hậu quả

Một hành trình mua vé gồm nhiều mốc trạng thái: khả dụng, giữ chỗ, thanh toán, phát hành và check-in. Khảo sát công khai cho thấy các nền tảng thương mại phải biểu diễn hết vé, giữ/pending, gửi vé sau thanh toán, vé đã sử dụng và hoàn tiền. Với hệ thống phân tán, việc thử lại một thao tác có tác dụng phụ có thể gây kết quả lặp nếu yêu cầu không được nhận diện; AWS mô tả idempotency như một hợp đồng giúp retry mà không tạo thêm tác dụng phụ. Khi thay đổi dữ liệu và phát thông báo là hai thao tác độc lập, lỗi giữa hai bước có thể tạo trạng thái không nhất quán.

### Nước 2 — Giải pháp đã tồn tại

Các kết quả nhìn thấy ở Ticketbox, TicketGo và Eventbrite cho thấy hệ thống thương mại đã xử lý bài toán ở mức sản phẩm, nhưng khảo sát giao diện không cho biết kiến trúc nội bộ. Ở mức kỹ thuật, các cơ chế như giao dịch cục bộ có bù trừ, hộp thư giao dịch, thao tác lặp an toàn và liên kết dấu vết đã được công bố. Drain cũng là phương pháp phân tích log trực tuyến đã được nghiên cứu, không phải đóng góp thuật toán mới của đồ án.

### Nước 3 — Khoảng trống thật của đồ án

Không tuyên bố “chưa có ai giải” hoặc “chưa có tài liệu công khai nào đo” vì chưa có tổng quan hệ thống đủ rộng để chứng minh điều đó. Khoảng trống hợp lệ là **khoảng trống kỹ thuật của chính FlashTicket**: repository tiền nhiệm chưa có ranh giới service/data được lập luận và cưỡng chế rõ, chưa có bằng chứng đo các bất biến dưới workload đồng thời/lỗi từng phần, và chưa có dữ liệu vận hành liên kết xuyên service để hỗ trợ chẩn đoán.

### Nước 4 — Vì sao vẫn đáng thực hiện

- Phối hợp và kiểm chứng các cơ chế dưới cùng workload, dữ liệu và cấu hình triển khai của đồ án.
- Phân tích đánh đổi trong giới hạn hai EC2 thay vì suy rộng từ hạ tầng công nghiệp.
- Đánh giá trợ lý chẩn đoán ở phạm vi nhỏ bằng các ca lỗi có nguyên nhân thật, không hứa tự động sửa lỗi.

## 2. BÁO CÁO — Bản nháp đề xuất

Hệ thống bán vé trực tuyến không kết thúc tại thao tác tạo đơn. Một hành trình hoàn chỉnh đi qua các trạng thái khả dụng, giữ chỗ, thanh toán, phát hành vé và kiểm soát vé tại cổng. Khảo sát các luồng công khai của Ticketbox, TicketGo và Eventbrite cho thấy những trạng thái như hết vé, chỗ đang được giữ, đơn chưa hoàn tất, vé điện tử chỉ được phát hành sau thanh toán và vé đã được sử dụng đều xuất hiện trong vận hành thực tế. Một sự kiện trên Ticketbox còn công bố thời hạn giữ vé và nguyên tắc chỉ chấp nhận người quét mã đầu tiên. Các biểu hiện này cho thấy bán vượt số lượng, ghi nhận lặp và sử dụng vé nhiều lần không chỉ là chi tiết cài đặt mà là các bất biến trực tiếp ảnh hưởng đến quyền lợi người mua và nhà tổ chức.

Trong hệ thống có nhiều thành phần độc lập, lỗi mạng, tiến trình dừng giữa chừng hoặc thông điệp được gửi lại khiến một thao tác có thể hoàn tất ở thành phần này nhưng chưa được phản ánh ở thành phần khác. Tài liệu của AWS về [idempotent API](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/) chỉ ra rằng retry chỉ an toàn khi cùng một ý định không tạo thêm tác dụng phụ; hướng dẫn về [transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) cũng mô tả nguy cơ mất hoặc phát “bóng ma” sự kiện khi cập nhật dữ liệu và gửi thông báo là hai thao tác tách rời. Mẫu [Saga](https://doi.org/10.1145/38713.38742) đã được công bố từ lâu cho giao dịch kéo dài. Do đó, đồ án không tuyên bố sáng tạo các cơ chế này mà tập trung lựa chọn, phối hợp và kiểm chứng chúng trong vòng đời vé của FlashTicket.

Repository tiền nhiệm đã hiện thực nhiều luồng chính nhưng chưa có bằng chứng đầy đủ về tính nhất quán khi yêu cầu đồng thời, thông điệp lặp hoặc một thành phần gặp lỗi. Việc tách dữ liệu theo service còn làm tăng nhu cầu xác định rõ quyền sở hữu, trạng thái bù trừ và cách phục hồi. Đồng thời, kiểm kê hiện trạng cho thấy hệ thống có nhiều lời gọi log nhưng chưa thấy mã tương quan và cấu trúc trường thống nhất xuyên service. Theo [OpenTelemetry](https://opentelemetry.io/docs/concepts/observability-primer/), log trở nên hữu ích hơn khi được liên kết với trace/span; nghiên cứu [Drain](https://doi.org/10.1109/ICWS.2017.13) cho thấy log thô có thể được gom thành các mẫu trực tuyến để hỗ trợ phân tích. Đây là cơ sở thực tiễn để chuẩn hóa đầu vào trước khi thử nghiệm một trợ lý chẩn đoán chỉ đọc.

Vì vậy, giá trị của đồ án không nằm ở tuyên bố bài toán chưa từng được giải quyết, mà ở một nghiên cứu điển hình có thể kiểm chứng: thiết kế lại ranh giới và dữ liệu của FlashTicket, duy trì các bất biến cốt lõi trong điều kiện lỗi/tải được công bố, đo chi phí đánh đổi trên hai máy triển khai và đánh giá mức hữu ích của hỗ trợ chẩn đoán trên một tập ca giới hạn. Kết quả được trình bày cùng cấu hình, dữ liệu và giới hạn suy rộng để có thể bảo vệ bằng bằng chứng thay vì mô tả kiến trúc thuần túy.

## 3. Phép tự kiểm

- Xóa tên công nghệ, mạch “vòng đời vé có trạng thái liên thuộc, lỗi từng phần gây hậu quả, cần kiểm chứng trong ràng buộc cụ thể” vẫn còn nguyên.
- Không tuyên bố tính mới toàn cầu.
- Quan sát sản phẩm và nguồn kỹ thuật được phân biệt.
- Chưa đưa ngưỡng hiệu năng khi chưa có QS/benchmark.

## 4. CẦN XÁC NHẬN

1. Cô Liên có muốn phần tính cấp thiết nhấn mạnh nghiệp vụ đặt vé hay kiến trúc phân tán nhiều hơn.
2. Nhóm có thống nhất gọi nhánh AI là “trợ lý chẩn đoán sự cố” trong toàn báo cáo hay không.
3. Sau khi có mẫu trích dẫn của khoa, chuyển các liên kết trên thành tài liệu tham khảo đánh số.
