# A1 — Bối cảnh và tính cấp thiết

- **Trạng thái:** `APPROVED` — baseline Giai đoạn 1; tái kiểm tra sau B10
- **Người duyệt:** Lê Văn Minh
- **Ngày duyệt:** 2026-08-13
- **Đi vào báo cáo:** Phần Đặt vấn đề/Tính cấp thiết
- **Nguồn:** B1 khảo sát công khai ngày 2026-08-09 và các tài liệu kỹ thuật được ghi trong `source-register.md`

## 1. Mạch lập luận

### Nước 1 — Bài toán nghiệp vụ có hậu quả trực tiếp

Vòng đời một vé đi qua khả dụng, giữ chỗ, thanh toán, phát hành và check-in. Khảo sát các luồng công khai cho thấy nền tảng bán vé phải biểu diễn các trạng thái như hết vé, giữ chỗ có thời hạn, thanh toán chưa hoàn tất, vé điện tử được phát hành sau thanh toán và vé đã sử dụng. Nếu các trạng thái này không phối hợp đúng, người mua và nhà tổ chức có thể chịu hậu quả như bán vượt số lượng, trạng thái thanh toán và phát hành không khớp hoặc một vé được chấp nhận nhiều lần.

### Nước 2 — Kiến trúc phân tán làm tăng chi phí duy trì tính đúng đắn

Khi trách nhiệm và dữ liệu được phân chia giữa nhiều service, một giao dịch nghiệp vụ không còn nằm gọn trong một giao dịch cơ sở dữ liệu. Yêu cầu gửi lại, thông điệp lặp, lỗi mạng hoặc một thành phần dừng giữa chừng có thể khiến các bước hoàn tất không đồng đều. Các cơ chế như idempotency, transactional outbox và Saga đã được công bố; đồ án không tuyên bố sáng tạo chúng mà cần lựa chọn và phối hợp phù hợp với bất biến của vòng đời vé.

### Nước 3 — Hệ thống phải vừa kiểm chứng được tính đúng đắn, vừa chẩn đoán được lỗi

Một kiến trúc chỉ mô tả các service chưa đủ chứng minh hệ thống đáng tin cậy. Các bất biến phải được kiểm tra dưới yêu cầu đồng thời, thông điệp lặp và lỗi từng phần; độ trễ, thông lượng, trạng thái phục hồi và điều kiện chạy phải được công bố. Đồng thời, khi một giao dịch đi qua nhiều service, log rời rạc khó cho biết các bản ghi nào thuộc cùng một luồng. Vì vậy, chuẩn logging có cấu trúc, mã tương quan và cách tạo context là đầu vào cần thiết cho trợ lý chẩn đoán; Drain và LLM chỉ xử lý tốt trong phạm vi chất lượng của đầu vào đó.

### Nước 4 — Giá trị của đồ án nằm ở thiết kế có lập luận và bằng chứng

Đây là đồ án xây dựng và kiểm chứng một hệ thống cụ thể, không phải tuyên bố tìm ra bài toán chưa từng được giải. Giá trị cần bảo vệ gồm: ranh giới service và quyền sở hữu dữ liệu được suy ra từ miền nghiệp vụ; các cơ chế nhất quán gắn với bất biến; kết quả thực nghiệm trong cấu hình triển khai được công bố; và đánh giá trợ lý chẩn đoán trên một tập ca lỗi có nguyên nhân biết trước. Không cần dựng một “khoảng trống nghiên cứu” từ lịch sử mã nguồn hoặc khẳng định tính mới toàn cầu.

## 2. BÁO CÁO — Bản nháp đề xuất

Hệ thống bán vé trực tuyến không kết thúc tại thao tác tạo đơn. Một hành trình hoàn chỉnh đi qua các trạng thái khả dụng, giữ chỗ, thanh toán, phát hành vé và kiểm soát vé tại cổng. Khảo sát các luồng công khai của Ticketbox, TicketGo và Eventbrite cho thấy những trạng thái như hết vé, chỗ đang được giữ, đơn chưa hoàn tất, vé điện tử chỉ được phát hành sau thanh toán và vé đã được sử dụng đều xuất hiện trong vận hành thực tế. Một sự kiện trên Ticketbox còn công bố thời hạn giữ vé và nguyên tắc chỉ chấp nhận người quét mã đầu tiên. Các biểu hiện này cho thấy bán vượt số lượng, ghi nhận lặp và sử dụng vé nhiều lần không chỉ là chi tiết cài đặt mà là các bất biến trực tiếp ảnh hưởng đến quyền lợi người mua và nhà tổ chức.

Trong kiến trúc phân tán, trách nhiệm và dữ liệu của cùng một giao dịch nghiệp vụ có thể nằm ở nhiều service. Lỗi mạng, tiến trình dừng giữa chừng hoặc thông điệp được gửi lại khiến một thao tác có thể hoàn tất ở thành phần này nhưng chưa được phản ánh ở thành phần khác. Tài liệu của AWS về [idempotent API](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/) chỉ ra rằng retry chỉ an toàn khi cùng một ý định không tạo thêm tác dụng phụ; hướng dẫn về [transactional outbox](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) cũng mô tả nguy cơ mất hoặc phát sự kiện không tương ứng với trạng thái dữ liệu khi cập nhật dữ liệu và gửi thông báo là hai thao tác tách rời. Mẫu [Saga](https://doi.org/10.1145/38713.38742) đã được công bố từ lâu cho giao dịch kéo dài. Do đó, đồ án tập trung lựa chọn, phối hợp và kiểm chứng các cơ chế phù hợp với vòng đời vé thay vì tuyên bố sáng tạo những mẫu này.

Khả năng phục hồi chỉ có thể được đánh giá khi hệ thống cung cấp đủ bằng chứng vận hành. Với một luồng đi qua nhiều service, các dòng log riêng lẻ khó cho biết trình tự sự kiện và vùng phát sinh lỗi nếu không có định dạng thống nhất và mã tương quan. Theo [OpenTelemetry](https://opentelemetry.io/docs/concepts/observability-primer/), log có thể được liên kết với trace và span để tăng khả năng quan sát; nghiên cứu [Drain](https://doi.org/10.1109/ICWS.2017.13) cho thấy thông điệp log có thể được gom thành các mẫu trực tuyến để hỗ trợ phân tích. Tuy nhiên, Drain và LLM không thay thế được logging thiếu ngữ cảnh. Vì vậy, đồ án xem chuẩn hóa đầu vào, tạo context và giới hạn quyền chỉ đọc là nền tảng của trợ lý chẩn đoán.

Từ đó, đồ án thiết kế và xây dựng FlashTicket Platform theo các ranh giới nghiệp vụ và quyền sở hữu dữ liệu rõ ràng; kiểm chứng các bất biến cốt lõi dưới yêu cầu đồng thời, thông điệp lặp và một số lỗi từng phần; đồng thời đánh giá mức hữu ích của trợ lý chẩn đoán trên tập ca lỗi giới hạn. Kết quả được trình bày cùng workload, cấu hình triển khai trên hai máy, dữ liệu thử và giới hạn suy rộng. Cách tiếp cận này giúp giá trị của đồ án được bảo vệ bằng lập luận kiến trúc và bằng chứng thực nghiệm, thay vì bằng tuyên bố tính mới hoặc lịch sử phát triển mã nguồn.

## 3. Phép tự kiểm

- Xóa tên công nghệ, mạch “vòng đời vé có trạng thái liên thuộc, lỗi từng phần gây hậu quả, cần kiểm chứng và chẩn đoán” vẫn còn nguyên.
- Không dùng lịch sử repository làm bối cảnh, tính cấp thiết hoặc khoảng trống nghiên cứu.
- Không tuyên bố tính mới toàn cầu.
- Quan sát sản phẩm và nguồn kỹ thuật được phân biệt.
- Chưa đưa ngưỡng hiệu năng khi chưa có QS/benchmark.

## 4. Kết quả xác nhận và vấn đề còn mở

1. **Đã duyệt:** phần tính cấp thiết bắt đầu từ hậu quả nghiệp vụ của vòng đời vé; kiến trúc phân tán được trình bày sau như yếu tố làm tăng độ khó phối hợp và kiểm chứng, không phải lý do tự thân để chọn đề tài.
2. **Đã chốt:** dùng thống nhất tên **“trợ lý chẩn đoán sự cố”**.
3. **`OPEN`:** mẫu trích dẫn của khoa chưa được ban hành do kỳ đồ án chưa bắt đầu. Tạm giữ URL/DOI làm dấu vết nguồn; chuẩn hóa cách đánh số và trình bày sau khi nhận mẫu, không chặn phân tích.
