# A1 — Bối cảnh và tính cấp thiết

- **Phiên bản:** `A1-v0.3`; cập nhật 2026-09-18.
- **Trạng thái:** `REVIEW_READY` — đã đồng bộ định hướng DT18; phần diễn giải cần Minh rà soát, không tự kế thừa phê duyệt phiên bản cũ.
- **Người duyệt:** —; **Ngày duyệt:** —.
- **Đầu vào:** [tên và nhiệm vụ hiện hành](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) (`DT18-TEN`, `DT18-NV1`–`DT18-NV3`, `PRJ-025/026`); `PRJ-035` về mobile; các quyết định nghiệp vụ/kiến trúc còn hiệu lực.
- **Hiệu lực:** tên, nguyên văn nhiệm vụ và ưu tiên mobile là `USER_CONFIRMED`; các cách diễn đạt/ánh xạ bên dưới là `CANDIDATE` trừ phần dẫn rõ quyết định đã có. DH-* chỉ được dùng để truy lịch sử.

## 1. Mạch lập luận

1. **Bán vé có các trạng thái cần phối hợp đúng.** Quản lý sự kiện, loại vé, đặt vé, tồn kho và thanh toán là các chức năng chính của nhiệm vụ DT18-NV1. Các luồng giữ chỗ, phát hành và check-in đã được đặc tả trong bộ hệ thống. Sai lệch tồn kho hoặc xử lý thanh toán lặp ảnh hưởng trực tiếp tới giao dịch.
2. **Phân tán làm tăng yêu cầu phối hợp và kiểm chứng.** Một giao dịch có thể đi qua nhiều thành phần, gặp cạnh tranh, yêu cầu lặp hoặc lỗi từng phần. Ranh giới và cơ chế phối hợp cần có lập luận, sau đó kiểm bằng chức năng, tải và đồng thời; không dùng việc demo chạy được thay kết quả đánh giá.
3. **Vận hành cần liên kết bằng chứng giữa các thành phần.** Log, trace và metrics phản ánh những khía cạnh khác nhau. Đồ thị phụ thuộc biểu diễn dịch vụ/thành phần cùng quan hệ gọi, thông điệp và phụ thuộc dữ liệu để gắn tín hiệu, xác định vùng ảnh hưởng và hỗ trợ xếp hạng nguyên nhân.
4. **Giá trị cần chứng minh là hệ thống và cơ chế hoạt động đến đâu.** Nhiệm vụ yêu cầu xây dựng hệ thống, đo hiệu năng/nhất quán/ổn định, nghiên cứu phương pháp trên dữ liệu công khai trước rồi thử trên hệ bán vé. Kết quả phải chỉ rõ điều kiện hiệu quả và hạn chế; không mặc định thêm đồ thị là tốt hơn.

## 2. BÁO CÁO — Bản nháp đề xuất

Hệ thống bán vé trực tuyến cần phối hợp quản lý sự kiện, loại vé, đặt vé, tồn kho và thanh toán. Khi nhiều yêu cầu cùng tác động lên lượng vé hữu hạn, hệ thống phải giữ dữ liệu nhất quán và trạng thái giao dịch đúng dù có xử lý lặp hoặc lỗi từng phần. Với kiến trúc phân tán, các trách nhiệm được thực hiện ở nhiều thành phần, nên thiết kế cần được kiểm chứng bằng tải và tình huống đồng thời trên cấu hình được công bố.

Bên cạnh tính đúng đắn, việc quan sát và chẩn đoán sự cố cần liên kết được bằng chứng rải rác giữa các thành phần. Đồ thị phụ thuộc cung cấp mô hình quan hệ để ánh xạ log, trace và metrics, xác định vùng ảnh hưởng, phát hiện dấu hiệu bất thường và xếp hạng các thành phần có khả năng gây ra sự cố. Chất lượng của cơ chế phụ thuộc vào dữ liệu và cách đánh giá, không chỉ vào việc dựng được đồ thị.

Đồ án thiết kế, xây dựng và đánh giá một hệ thống bán vé phân tán có ứng dụng mô hình trên. Phương pháp được thực nghiệm trước trên bộ dữ liệu công khai phù hợp, sau đó áp dụng thử trên hệ thống bán vé. Sản phẩm và số đo được trình bày cùng điều kiện chạy, bằng chứng và các trường hợp chưa đạt, nhằm thể hiện năng lực thiết kế và kiểm chứng thay vì tuyên bố tính mới chưa có căn cứ.

## 3. Nguồn và giới hạn

- Phạm vi xây dựng/đánh giá: DT18-NV1; mô hình và dữ liệu quan sát: DT18-NV2; lộ trình và sản phẩm: DT18-NV3.
- Khảo sát B1 chỉ chứng minh các hành vi công khai quan sát được; không suy ra kiến trúc bên trong các nền tảng khảo sát.
- Baseline chẩn đoán B1 là bằng chứng của thời điểm khảo sát, không mô tả mức hoàn thiện mã nguồn hiện hành.
- Không dùng lịch sử repository làm tính cấp thiết; không tuyên bố tính mới hoặc ngưỡng chưa đo.
- `OPEN`: chuẩn trích dẫn/mẫu khoa và Minh duyệt bản diễn giải này trước khi chuyển vào báo cáo.

## Nhật ký phiên bản — lịch sử, không dùng làm ngữ cảnh hiện hành

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A1-v0.3` | 2026-09-18 | Đồng bộ tên/nhiệm vụ DT18, phạm vi xây dựng và đánh giá hệ thống, nhóm bốn người và mobile tùy thời gian; bản diễn giải được đưa về REVIEW_READY | Theo PRJ-025–035 và phạm vi GOV-146, chưa duyệt nội dung mới |
| `A1-v0.2` | 2026-08-26 | Tái baseline theo `DH-TEN`. Giữ mạch bốn nước và bổ sung nước 4 về khoảng trống từ dấu vết tới nguyên nhân; đích của mạch chuyển sang chẩn đoán bằng đồ thị phụ thuộc; hệ đặt vé giữ vai hệ giao dịch được mô hình hóa theo `DH-MT1`; viết lại §2 | Tái baseline (`RES-032`) |
| `A1-v0.1` | 2026-08-13 | Bản đầu, `APPROVED` theo trục nhất quán vòng đời vé | Tạo mới |
