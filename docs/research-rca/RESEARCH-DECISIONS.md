# RCA — Sổ quyết định nghiên cứu

Chủ sở hữu: **Lê Văn Minh**. Ngày ghi: **2026-09-22**. Loại: `CANONICAL_DECISION`. Chỉ chứa quyết định con người đã xác nhận; lý giải phương pháp và số đo thuộc tạo tác sở hữu tương ứng. Không phải danh sách giả thuyết hay nhật ký reviewer.

## Nguồn và quyền quyết định

Nguồn `U22`: yêu cầu **“MASTER EXECUTION PROMPT — Finish Task C Phase 2 + Establish RCA Master Research Program”** do Minh cung cấp ngày 22/09/2026. Bản gốc: [tệp yêu cầu](<C:/Users/84583/.codex/attachments/20a87370-11e4-425f-850d-6aef6955edcc/Pasted text.txt>); SHA-256 `58359743020A8BC46BE58028AEF8758F1CE3C2552C9EBDCFA8BFF0D1E1F4C9DC`. Các quyết định được lưu ngay tại đây để không phụ thuộc việc attachment còn tồn tại. U22 §1 nói rõ coi các ý này là USER-CONFIRMED; đây là xác nhận của Minh, **không phải bằng chứng giảng viên đã duyệt**.

Sổ này là phần chuyên biệt RCA được dẫn từ [sổ quyết định dự án](../project/decision-register.md). Nội dung nguyên tử bên dưới được duy trì ở một nơi; các tài liệu khác dẫn ID và diễn giải áp dụng, không tạo quyết định cạnh tranh. [Task C Phase 2](task-c-research-decision-lock.md) sở hữu hợp đồng RQ/hypothesis/evaluation; [master program](MASTER-RESEARCH-PROGRAM.md) sở hữu trình tự và cổng thực hiện.

## Quyết định hiện hành

Mọi dòng có ngày **2026-09-22**, người chốt **Lê Văn Minh**, trạng thái **USER_CONFIRMED**, gate **Task C Phase 2**, trừ khi được ghi khác sau này. Cột lý do chỉ tóm tắt lý do trong U22, không thêm điều kiện AI suy ra vào xác nhận.

| ID | Quyết định | Lý do tóm tắt | Nguồn / trích ngắn | Thay thế / được thay bởi |
|---|---|---|---|---|
| RCA-001 | Chọn **C1 làm Primary RQ**: giá trị quan hệ service suy từ trace cho known-window root-service ranking trên RE2-TT với điều kiện so sánh công bằng. | Chọn câu hỏi có thể kiểm bằng dữ liệu đã audit. | U22 §1.2: “PRIMARY RQ = C1.” | Đóng lựa chọn C1/C2 tại Phase 1 §13.1; chưa bị thay |
| RCA-002 | Chấp nhận đóng góp thực nghiệm có kiểm soát, kỹ thuật/hệ thống, khả năng tái lập và quyết định dựa trên bằng chứng; **không yêu cầu novelty phương pháp**. | Ưu tiên pipeline dùng được, kết quả có thể bảo vệ. | U22 §1.1, §14.C | Đóng lựa chọn giá trị ở Phase 1 §13.2; tương thích định vị RES-054, không duyệt MyRCA cũ |
| RCA-003 | Kết quả thực nghiệm âm được chấp nhận nếu phép thử hợp lệ. | Không ép kết quả phải thắng đối chứng. | U22 §1.1: “A negative experimental result is acceptable if the experiment is valid.” | Chưa bị thay |
| RCA-004 | C1 phải phân biệt lợi ích relation khỏi capacity, degree, smoothing, volume, candidate universe, feature và supervision differences. | Graph ON/OFF đơn giản không đủ. | U22 §1.2 | Chưa bị thay; thiết kế exact controls vẫn OPEN |
| RCA-005 | C2 là mở rộng tùy chọn, chỉ xét sau C1 hoàn chỉnh/hợp lệ/ổn định và còn hữu ích; thành công luận văn không phụ thuộc C2. | Giữ một câu hỏi chính. | U22 §1.3 | Thay vai C2 là lựa chọn primary trong shortlist; chưa bị thay |
| RCA-006 | Giữ C3 hiện diện như chủ đề thiết kế/biểu diễn operation và nghiên cứu hỗ trợ; triển khai/ablation có điều kiện theo Task D và giá trị khoa học. | Không bỏ operation evidence chỉ vì không là primary gap. | U22 §1.4, §12 | Làm rõ C3 secondary của Phase 1; chưa bị thay |
| RCA-007 | Chỉ đánh giá operation localization định lượng ở nơi có ground truth phù hợp; FlashTicket có thể cung cấp qua chèn lỗi có kiểm soát sau này. | Không biến operation evidence thành nhãn. | U22 §1.4 | Chưa bị thay; không xác nhận đã có nhãn FlashTicket |
| RCA-008 | Giữ C4 hiện diện như câu hỏi vị trí/cơ chế graph và ablation/comparator hỗ trợ; Task D quyết cơ chế cụ thể. | Hiểu đóng góp của từng công đoạn. | U22 §1.5, §12 | Làm rõ C4 secondary của Phase 1; chưa bị thay |
| RCA-009 | C5 — anomaly detection → RCA — là năng lực hệ thống bắt buộc; mức đánh giá phụ thuộc nhãn của từng môi trường. | Hạn chế benchmark không xóa năng lực phát hiện khỏi hệ cuối. | U22 §1.6, §12 | Làm rõ C5 hoãn primary RQ, không phải bỏ năng lực; chưa bị thay |
| RCA-010 | Lớp LLM/AI giải thích sau ranked structured evidence là năng lực đầu ra dự kiến bắt buộc. | Diễn giải kết quả và gợi ý kiểm tra. | U22 §1.7, §12 | Tiếp tục RES-034; chưa bị thay |
| RCA-011 | LLM không tạo ground truth, tự xếp hạng lại, sửa kết quả RCA thất bại hoặc làm bằng chứng chẩn đoán đúng. | Giữ phép đánh giá RCA độc lập với văn bản giải thích. | U22 §1.7 | Chưa bị thay |
| RCA-012 | RE2-TT là môi trường kiểm chứng công khai cho pipeline hoàn chỉnh; không bắt mọi thành phần có cùng mức đánh giá định lượng. | Ground truth giới hạn điều được tuyên bố. | U22 §1.8 | Mở rõ phạm vi sản phẩm so với một notebook C1; không đổi sự thật Task B |
| RCA-013 | FlashTicket là hệ đích để chuyển giao/kiểm chứng có kiểm soát sau thực nghiệm công khai. | Thực hiện DT18-NV3. | U22 §1.9 | Tiếp tục nhiệm vụ DT18; chưa bị thay |
| RCA-014 | Tasks D–G của nghiên cứu công khai không bị chặn bởi tiến độ FlashTicket; nghiên cứu có thể chạy song song với triển khai của các thành viên. | Giữ độc lập tiến độ và ranh giới adapter. | U22 §0, §1.9 | Không dùng giả định Minh thiếu thời gian để cắt scope; chưa bị thay |
| RCA-015 | Kết quả FlashTicket không hợp thức hóa hồi tố một claim RE2-TT không được dữ liệu hỗ trợ. | Mỗi môi trường có bằng chứng riêng. | U22 §1.9 | Chưa bị thay |
| RCA-016 | Không để quyết định quan trọng chỉ tồn tại ngoài repository; không chép dataset lớn vào repository chỉ để thống nhất hình thức. | Giữ tri thức bền vững và phục hồi ngữ cảnh giữa các phiên. | U22 §6, §14.H–I | Chưa bị thay; cách bố trí hai root do chương trình đánh giá, không gán thành lựa chọn cụ thể Minh đã chốt |
| RCA-017 | Phiên này chỉ hoàn tất C Phase 2 và hồ sơ điều phối; Task D phải chờ lệnh bắt đầu riêng. | Đúng ranh giới ủy quyền. | U22 phần đầu, §15: “Do not start Task D automatically.” | Chưa bị thay |

## Xác nhận làm rõ nguồn và phạm vi — 23/09/2026

Nguồn U23: [nguyên văn xác nhận Minh §3](../evidence/advisor-direction/2026-09-23-huong-dan-do-minh-cung-cap.md). Mọi dòng dưới: người chốt **Lê Văn Minh**, ngày **2026-09-23**, trạng thái **USER_CONFIRMED**, gate **TD-v1.1 reconciliation / làm rõ phạm vi DT18**. Không thay RCA-001–017, không duyệt phương pháp hay execution.

| ID | Xác nhận nguyên tử | Nguồn / trích ngắn | Phạm vi ảnh hưởng |
|---|---|---|---|
| RCA-018 | Tên đề tài hiện hành đúng là “Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố”. | U23: tên đề tài “chuẩn” | Tái xác nhận DT18/PRJ-025; không thay tên hoặc nhiệm vụ |
| RCA-019 | Đoạn hướng dẫn giảng viên được cung cấp ở U23 thuộc giai đoạn đề tài trước, khi RCA là đối tượng nghiên cứu chính. | U23: “đoạn hướng dẫn trên là khi đề tài vẫn còn lấy RCA là đối tượng nghiên cứu chính” | Provenance tương đối; không suy ngày/kênh gửi gốc |
| RCA-020 | Hướng dẫn cũ vẫn có giá trị cho phần RCA; RCA không bị bỏ khi đồ án thêm đối tượng nghiên cứu hệ thống. | U23: “hướng dẫn cũ này của cô vẫn còn giá trị … RCA không bỏ” | Cách đọc nguồn phương pháp trong D/Master/A; không xác nhận từng ví dụ là bắt buộc |
| RCA-021 | Không áp dụng nguyên xi hướng dẫn cũ theo cách giảm nhẹ hoặc coi nhẹ việc xây dựng FlashTicket. | U23: “không nên tuân thủ y hệt … giảm nhẹ hay coi nhẹ việc xây dựng hệ thống flash ticket” | D/H/J và diễn giải DT18 giữ nghĩa vụ hệ thống; không thêm API/kiến trúc |

## Quy tắc cập nhật

Thay đổi ý định đã khóa phải có xác nhận mới của Minh, ID mới, nguồn/ngày và liên kết thay thế; giữ dòng cũ. Không thêm đề xuất reviewer hoặc thiết kế Task D chưa được duyệt vào bảng USER_CONFIRMED. Một quyết định triển khai được giao cho agent không tự là lời Minh xác nhận đúng phương án agent chọn. Không dùng sổ này thay các gate kiến trúc/hợp đồng của bộ hệ thống.
