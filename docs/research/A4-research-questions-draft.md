# A4 — Câu hỏi nghiên cứu

- **Phiên bản:** `A4-v0.4`; cập nhật 2026-09-18.
- **Trạng thái:** `REVIEW_READY` — đã đồng bộ định hướng DT18; phần diễn giải cần Minh rà soát, không tự kế thừa phê duyệt phiên bản cũ.
- **Người duyệt:** —; **Ngày duyệt:** —.
- **Đầu vào:** [tên và nhiệm vụ hiện hành](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) (`DT18-TEN`, `DT18-NV1`–`DT18-NV3`, `PRJ-025/026`); `PRJ-035` về mobile; các quyết định nghiệp vụ/kiến trúc còn hiệu lực.
- **Hiệu lực:** tên, nguyên văn nhiệm vụ và ưu tiên mobile là `USER_CONFIRMED`; các cách diễn đạt/ánh xạ bên dưới là `CANDIDATE` trừ phần dẫn rõ quyết định đã có. DH-* chỉ được dùng để truy lịch sử.

Câu hỏi dưới đây là `CANDIDATE` sau thay đổi nhiệm vụ; một câu trung tâm và hai câu phụ. Đây là cách tổ chức lập luận, không chia đề tài theo thành viên.

## Câu hỏi trung tâm

> Hệ thống bán vé phân tán được xây dựng đáp ứng tính đúng đắn và chất lượng giao dịch đến mức nào dưới tải và đồng thời, và đồ thị phụ thuộc kết hợp dữ liệu vận hành hỗ trợ giám sát, chẩn đoán sự cố đến mức nào trong các điều kiện được kiểm chứng?

**Bằng chứng:** chức năng/bất biến; thông lượng, thời gian đáp ứng, tỷ lệ lỗi, khả năng mở rộng và độ ổn định; đồ thị có nguồn; kết quả phát hiện và xếp hạng được chấm riêng. Không cộng các độ đo khác nghĩa thành một điểm chung.

## Câu hỏi phụ 1 — hệ thống

> Khi tăng tải hoặc thay đổi mức đồng thời và cấu hình tài nguyên, tính nhất quán, hiệu năng và độ ổn định của các giao dịch chính thay đổi thế nào?

**Bằng chứng:** workload lặp lại được, cấu hình công bố, kiểm tiền/tồn kho/trạng thái, độ trễ/thông lượng/tỷ lệ lỗi và điều kiện ổn định. Mức tải, cấu hình và ngưỡng còn OPEN cho kế hoạch thử; không mặc định hai EC2 là HA.

## Câu hỏi phụ 2 — giám sát và chẩn đoán

> Đồ thị phụ thuộc cùng log, trace và metrics giúp phát hiện bất thường, xác định vùng ảnh hưởng và xếp hạng thành phần khả nghi với chất lượng và giới hạn nào trên dữ liệu công khai và trên hệ thống bán vé?

**Bằng chứng:** giao thức chấm có nhãn/cửa sổ rõ, đối chứng công bằng, kiểm ảnh hưởng từng nguồn và đồ thị, ca đúng/sai/thiếu bằng chứng; giao diện thể hiện đúng kết quả. Các câu hỏi chi tiết MT-R1–R4 vẫn dùng để phân tích cơ chế, không tự chứng minh tính mới.

## Cách sử dụng và điểm mở

- Trả lời bằng các bảng kết quả riêng cho hệ thống, phát hiện và chẩn đoán; không suy từ dataset sang hiệu quả FlashTicket khi chưa có ca thử phù hợp.
- `OPEN`: Minh duyệt câu hỏi mới; giảng viên/mẫu khoa xác nhận có trình bày mục câu hỏi riêng; giao thức và ngưỡng đo sau kiểm thử thăm dò.
- Phê duyệt A4-v0.2/v0.3 giữ giá trị lịch sử, không áp cho câu hỏi viết lại ngày 18/09.

## Nhật ký phiên bản — lịch sử, không dùng làm ngữ cảnh hiện hành

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A4-v0.4` | 2026-09-18 | Đồng bộ tên/nhiệm vụ DT18, phạm vi xây dựng và đánh giá hệ thống, nhóm bốn người và mobile tùy thời gian; bản diễn giải được đưa về REVIEW_READY | Theo PRJ-025–035 và phạm vi GOV-146, chưa duyệt nội dung mới |
| `A4-v0.3` | 2026-08-29 | Sửa một câu khai sai ở *Kết quả xác nhận* điểm 3: `B9`/`B10` xác định **phép đo**, không chốt **ngưỡng** — ngưỡng là đầu ra của vòng đo thử đầu tiên, và `B9-OPEN-01` vẫn mở sau khi `B9` được duyệt. Câu cũ viết trước khi hai gate đó đóng, nên khai nhầm nơi con số được chốt. **Ba câu hỏi nghiên cứu không đổi một chữ** | Sửa lời khai gate |
| `A4-v0.2` | 2026-08-26 | Tái baseline theo `DH-TEN`: câu trung tâm chuyển sang độ chính xác của cơ chế xếp hạng trên đồ thị phụ thuộc; câu phụ 1 đo ảnh hưởng của việc thêm nguồn dữ liệu; câu phụ 2 hỏi điều kiện đưa cơ chế về hệ nhà; gỡ cụm "nhánh hỗ trợ" | Tái baseline (`RES-032`) |
| `A4-v0.1` | 2026-08-13 | Bản đầu, `APPROVED` theo trục bất biến vòng đời vé | Tạo mới |
