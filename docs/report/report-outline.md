# Khung nội dung báo cáo

**Đề tài:** Xây dựng hệ thống bán vé theo kiến trúc phân tán có ứng dụng đồ thị phụ thuộc để giám sát và chẩn đoán sự cố.

**Nguồn:** [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md), Minh xác nhận 18/09/2026. **Phiên bản khung:** v5; khung sống, chưa khóa số chương trước mẫu khoa. Phân công theo [roles.md](../project/roles.md), nhóm Minh, Sơn, Tuấn, Tuyến. Mobile là phần phụ, chỉ làm khi thực sự thừa thời gian.

## Nguyên tắc chi phối

Một đề tài gồm xây dựng/đánh giá hệ bán vé phân tán và ứng dụng đồ thị để giám sát/chẩn đoán. Nội dung báo cáo phải bao phủ DT18-NV1–NV3; không giới hạn hệ thống vào vai bối cảnh hoặc chỉ kiểm chức năng.

Thực nghiệm phương pháp trên dữ liệu công khai trước, thử trên FlashTicket sau. Tách rõ nguồn dữ liệu, cấu hình, nhãn và độ đo; không suy kết quả benchmark thành hiệu quả FlashTicket. Bản báo cáo 05/09 là lịch sử, không dùng tên/nhóm/phạm vi ở bản đó làm hiện hành.

## Bối cảnh và vấn đề

Nhu cầu phối hợp sự kiện, loại vé, đặt vé, tồn kho và thanh toán; tính đúng đắn dưới đồng thời/lỗi từng phần; yêu cầu đánh giá hệ phân tán; khó liên kết bằng chứng vận hành và xác định vùng sự cố.

**Nguồn:** A1-v0.3, A2-v0.3 và khảo sát B1. A1–A6 vừa được đồng bộ DT18, đang REVIEW_READY; không lấy dấu duyệt các phiên bản cũ làm phê duyệt câu chữ mới.

## Mục tiêu, đối tượng, phạm vi và phương pháp

Trình bày nhiệm vụ DT18; mục tiêu/bằng chứng theo A3-v0.7, câu hỏi A4-v0.4 nếu mẫu yêu cầu, đối tượng A5-v0.4 và phạm vi A6-v0.7. Thể hiện các bước khảo sát → phân tích → thiết kế → hiện thực → kiểm chứng, không ép số chương theo khung phương pháp.

## Cơ sở lý thuyết và lựa chọn

Kiến trúc phân tán, bất biến/nhất quán, xử lý lặp và phối hợp giao dịch; quan hệ log–trace–metrics; đồ thị phụ thuộc và giới hạn suy luận nhân quả; nhóm phương pháp phát hiện/chẩn đoán và độ đo phù hợp. Chỉ đưa lý thuyết thực sự dùng.

Nguồn RCA: `docs/research-rca/A7-khai-niem-rca.md`, `docs/research-rca/A8-khao-sat-dataset.md`, `docs/research-rca/A9-do-do-thuc-nghiem.md`, `docs/research-rca/A10-khao-sat-phuong-phap.md`, E1; nguồn chung tại `docs/research/source-register.md`.

## Phân tích và thiết kế hệ thống

Actor/use case và quy trình có nhánh lỗi; mô hình miền/bất biến; yêu cầu và kịch bản chất lượng; ranh giới service có căn cứ; sở hữu dữ liệu; API/sự kiện; Saga và các luồng cạnh tranh; mô hình triển khai; chuẩn quan sát và quyền truy cập. Check-in backend giữ bất biến đã duyệt; app mobile chỉ trình bày nếu thực sự được làm.

**Nguồn:** B2–B16, ADR được chấp nhận. PA-6 và dữ liệu/hợp đồng đã duyệt không bị thay bởi tên đề tài mới. Các sơ đồ giải thích trạng thái đích, không kể lịch sử chuyển mã.

## Đồ thị phụ thuộc và dữ liệu quan sát — DT18-NV2

Nút là dịch vụ/thành phần; cạnh gọi, trao đổi thông điệp hoặc phụ thuộc dữ liệu. Mỗi cạnh có nguồn và ý nghĩa; không chép đồ thị ngữ nghĩa B5 thành đồ thị runtime. Trình bày quy tắc ánh xạ log, trace, metrics; coverage, định danh, thời gian, sampling và dữ liệu thiếu.

## Giám sát và chẩn đoán

Xác định vùng ảnh hưởng, phát hiện bất thường, xếp hạng thành phần khả nghi; chỉ rõ bằng chứng và giới hạn. MyRCA là phương án nghiên cứu CANDIDATE cho tới khi được kiểm chứng. Lớp giải thích kế thừa RES-034 nhận kết quả đã xếp hạng; quyền chỉ đọc được thực thi thật. Không cam kết bỏ tái hiện lỗi, không tự kết luận nguyên nhân cuối cùng, không tự sửa nghiệp vụ.

## Hiện thực và giao diện minh họa

Thành phần thực sự đã xây, tích hợp và kiểm; giao diện minh họa mô hình/kết quả phân tích theo DT18-NV3. Thể hiện quyền người sử dụng và liên kết từ kết quả tới bằng chứng. Giao diện này là đầu ra chính, không đánh đồng với app mobile hoặc dashboard vận hành nâng cao tùy thời gian.

## Thực nghiệm và đánh giá

### Hệ thống bán vé phân tán — DT18-NV1

- Kiểm các luồng chính và bất biến: tồn kho, tiền, phát hành, xử lý lặp, check-in backend.
- Nhiều mức tải và tình huống đồng thời; công bố workload, dữ liệu seed, thời gian đo, cấu hình/tài nguyên và số lần lặp.
- Thông lượng, thời gian đáp ứng, tỷ lệ lỗi, khả năng mở rộng, tính nhất quán dữ liệu và độ ổn định giao dịch. Chưa có cấu hình đối chiếu thì không kết luận mở rộng tốt.
- Phân tích đánh đổi và ca lỗi. Ngưỡng/giao thức còn OPEN lấy từ nguồn B15/B16/A3, không tự điền vào báo cáo.

### Phát hiện và chẩn đoán — DT18-NV2/NV3

- Công khai trước, FlashTicket sau; mỗi bộ/môi trường có bảng riêng và điều kiện tái lập.
- Chấm phát hiện, vùng ảnh hưởng, xếp hạng và giải thích theo đúng loại đầu ra; độ đo xếp hạng không thay độ đo phát hiện.
- Đối chứng cùng điều kiện và mức sàn phù hợp tập ứng viên; kiểm ảnh hưởng đồ thị và từng nguồn dữ liệu, không chọn cấu hình trên tập test cuối.
- Phân tích kết quả hiệu quả, thất bại, thiếu dữ liệu; runtime/tài nguyên chỉ báo khi đã đo.

Nguồn kết quả: báo cáo run/manifest và E1. Kiểm định 990 dòng không phải 990 sự cố FlashTicket, không chứng minh MyRCA đã đạt.

## Kết luận, giới hạn và đóng góp

Đối chiếu từng nhiệm vụ DT18 với kết quả có bằng chứng; phân biệt sản phẩm đã làm với kế hoạch. Ghi đóng góp thực tế từng thành viên theo bốn phạm vi hiện hành, không sao chép phân công cũ. Báo giới hạn cỡ mẫu/môi trường, telemetry, khử nhạy cảm và suy rộng. Không coi việc chưa làm mobile tùy thời gian là thiếu nhiệm vụ chính.

## Điểm còn OPEN

- Minh rà soát A1–A6 sau cập nhật; chưa có bằng chứng giảng viên duyệt các diễn giải mới.
- Mẫu khoa, số chương và chuẩn trích dẫn.
- Giao thức/ngưỡng thử tải, khả năng mở rộng và ổn định; độ đo phát hiện/vùng ảnh hưởng; tập ca thử FlashTicket.
- Tập node RCA, manifest dữ liệu và đặc tả giao diện kết quả ở đúng nơi sở hữu.

## Lịch sử khung

v4 ngày 28/08 theo thư DH-*; v5 ngày 18/09 theo DT18 và phân công mới. Lịch sử nội dung đầy đủ được giữ trong Git; không dùng lời khai “đã duyệt” của v4 cho bản v5.
