# A2 — Phát biểu vấn đề

- **Trạng thái:** Bản đề xuất, cần chủ đồ án xác nhận
- **Đi vào báo cáo:** Phần Đặt vấn đề, sau A1
- **Ràng buộc:** Không quá 150 từ và không chứa tên công nghệ

## Các trường cấu thành

| Trường | Nội dung |
|---|---|
| Chủ thể chịu ảnh hưởng | Người mua vé, nhà tổ chức và nhóm vận hành hệ thống |
| Hệ quả | Bán vượt vé, trạng thái thanh toán và phát hành không khớp, ghi nhận lặp, check-in trùng và kéo dài thời gian xác định nguyên nhân |
| Hoàn cảnh | Nhiều yêu cầu gần đồng thời, bước xử lý bị chậm/lỗi hoặc được gửi lại |
| Nguyên nhân kỹ thuật gốc | Trạng thái và bất biến trải qua nhiều thành phần nhưng chưa có cơ chế phối hợp, phục hồi và dấu vết vận hành thống nhất |

## BÁO CÁO — Phát biểu đề xuất

> Trong hệ thống bán vé trực tuyến, một giao dịch đi qua giữ chỗ, thanh toán, phát hành và kiểm soát vào cửa. Khi nhiều yêu cầu xảy ra gần đồng thời hoặc một bước bị chậm, lỗi hay lặp lại, trạng thái thanh toán và phát hành vé có thể không khớp, nhà tổ chức có thể bán vượt số lượng, hoặc một vé được chấp nhận nhiều lần. Việc xác định nguyên nhân cũng khó khăn khi dấu vết của cùng giao dịch phân tán giữa các thành phần. Bài toán của đồ án là thiết kế và kiểm chứng cách phối hợp vòng đời vé để duy trì các bất biến cốt lõi, phục hồi về trạng thái chấp nhận được khi có lỗi và cung cấp đủ bằng chứng vận hành cho việc chẩn đoán trong điều kiện tài nguyên giới hạn.

## Phép tự kiểm

- Dưới giới hạn 150 từ theo cách tách bằng khoảng trắng.
- Không có tên framework, dịch vụ đám mây, mẫu kiến trúc, thuật toán hoặc nhà cung cấp.
- Nêu rõ chủ thể, hậu quả, hoàn cảnh và vấn đề gốc.
- Nhánh chẩn đoán được đặt ở vai trò hỗ trợ vận hành cho cùng hệ thống, không trở thành đề tài độc lập thứ hai.

## CẦN XÁC NHẬN

Chủ đồ án cần xác nhận phát biểu này sau khi A1 được duyệt, để bảo đảm thuật ngữ “trạng thái chấp nhận được” khớp với các trạng thái và cơ chế bù trừ sẽ chốt ở B7/B11.
