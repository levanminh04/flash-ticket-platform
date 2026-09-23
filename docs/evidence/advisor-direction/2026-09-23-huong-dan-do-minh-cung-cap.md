# Hướng dẫn giảng viên do Minh cung cấp trong review TD-v1.1

- Ngày tiếp nhận tại task: **2026-09-23**; ngày/tháng trong tên file là ngày tiếp nhận, không phải ngày giảng viên gửi.
- Nguồn: Minh cung cấp nguyên văn trong yêu cầu “Independent Reconciliation + TD-v1.1 Revision”, §4.
- **Ngày gửi gốc của giảng viên: NOT VERIFIED. Kênh gửi gốc: NOT VERIFIED.**
- Loại: bằng chứng nguồn; chủ sở hữu xác minh provenance/phạm vi: Lê Văn Minh.
- FACT: nội dung dưới đây đã được Minh cung cấp để đối chiếu. Không tự là quyết định thay DT18, không phê duyệt thuật toán, dataset, tham số hoặc execution.
- [Thư lưu ngày 22/08](2026-08-22-dinh-huong-de-tai.md) là nguồn khác, không chứa các ví dụ thuật toán và danh sách độ đo này. Giữ nguyên thư cũ.

## 1. Nguyên văn

Đề tài này sẽ định hướng qua 2 bước: Ưu tiên làm mạnh bước 1 trước.

**1. Nghiên cứu về phương pháp (Tập trung làm mạnh trước)**

- Bước 1. Xây dựng mô hình đồ thị phụ thuộc giữa các dịch vụ.
- Bước 2: Sử dụng graph-based anomaly detection để ánh xạ log/trace lên đồ thị. Trong đó áp dụng thuật toán phát hiện bất thường trên đồ thị (ví dụ: PageRank anomaly, subgraph anomaly detection, GNN-based anomaly detection).
- Bước 3. Triển khai thử nghiệm trên các dataset công khai (Train Ticket, Sock Shop, LEMMA-RCA).
- Bước 4. So sánh phương pháp đồ thị với các phương pháp RCA cơ sở hiện có. Đánh giá bằng các độ đo chuẩn: Precision, Recall, F1-score, MRR, NDCG.
- Bước 5. Tích hợp AI (LLM) để diễn giải nguyên nhân gốc và gợi ý bước xử lý cho người vận hành.

**2. Tích hợp phương pháp vào hệ thống đặt vé thử nghiệm sản phẩm (Phần này là hệ quả làm sau, tận dụng hệ thống cũ cũng được: Sản phẩm minh họa cho áp dụng phương pháp)**

Nếu chỉ dựng 1 thệ thống đặt vé và sử dụng thư viện hàm thì hàm lượng và qui mô đồ án còn nhỏ. Trọng tâm cần phải nghiên cứu phương pháp trước, rồi ứng dụng vào trong sản phẩm, sản phẩm hệ thống chỉ cần tập trung demo những tính năng liên quan tới phát hiện sự cố.

Về báo cáo hiện tại: mới khảo sát sơ qua các phương pháp RCA đã có, chưa sâu kỹ thuật của từng phương pháp.  Sau đó thực nghiệm với 1 dataset -> Các em cần làm rõ để hiểu các phương pháp RCA đã có & mở rộng thực nghiệm ra với các dataset public khác -> Chỉ ra vấn đề còn tồn đọng hạn chế của các phương pháp đã có -> Tiếp cận phương pháp đồ thị (Theo 5 bước cô chỉ ở trên).

## 2. Quyền diễn giải và điểm chưa xác minh

**Đã xác minh phạm vi tương đối bằng xác nhận Minh ngày 23/09/2026:** hướng dẫn thuộc giai đoạn đề tài trước, khi RCA còn là đối tượng nghiên cứu chính. Minh xác nhận tên/phạm vi DT18 hiện hành; hướng dẫn cũ còn giá trị cho nghiên cứu RCA, nhưng không được dùng để giảm nhẹ xây dựng FlashTicket. Ngày/kênh gửi gốc vẫn **NOT VERIFIED**. Không còn xung đột buộc lựa chọn “demo thay hệ thống”.

Diễn giải kỹ thuật, lựa chọn CANDIDATE và coverage/metric/dataset matrix nằm trong [TD-v1.1](../../research-rca/task-d-method-and-experiment-specification.md) và [hồ sơ reconciliation](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-reconciliation-evidence.md). Các diễn giải đó tách khỏi nguyên văn; không gọi PageRank, subgraph và GNN là ba thuật toán bắt buộc. Không suy ba tên dataset thành ba release đã chọn hay đã chạy.

## 3. Xác nhận hiện hành của Minh — nguyên văn, 23/09/2026

“để tôi nói rõ, định hướng đề hiện tại và tên đề tài là "Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố" chuẩn , đoạn hướng dẫn trên là khi đề tài vẫn còn lấy RCA là đối tượng nghiên cứu chính, tuy nhiên với đề tài mới thì RCA không còn là đối tượng mới, nhưng tôi thấy hướng dẫn cũ này của cô vẫn còn giá trị và quan trọng là RCA không bỏ, chỉ là đồ án có thêm 1 đối tượng nghiên cứu khác ngoài RCA thôi nên tôi nghĩ hướng dẫn này vẫn còn hiệu lực, vì vây bạn cũng không nên tuân thủ y hệt như trong hướng dẫn này như  việc giảm nhẹ hay coi nhẹ việc xây dựng hệ thống flash ticket, cảm ơn bạn vì đã hỏi điều này , đây là điều rất cần thiết tránh việc hiểu nhầm và giảm phạm vi đồ án”

Nguồn là câu trả lời trực tiếp của Minh trong task này, không phải lời giảng viên. Lưu câu xác nhận nguyên tử ở [RCA-018–021](../../research-rca/RESEARCH-DECISIONS.md); không sửa RCA-001–017 hoặc suy xác nhận này thành duyệt TD-v1.1, detector, log parser, dataset hay Task E.
