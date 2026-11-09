# R0 — Cửa bàn giao yêu cầu quan sát từ RCA sang FlashTicket

- Phiên bản: `R0-v0.7`; cập nhật: 09/11/2026.
- Phạm vi Minh duyệt: dọn tài liệu RCA cũ, xác định nguồn RCA hiện hành và giữ cửa bàn giao; không sửa nghiệp vụ, API, schema hoặc cấu hình hệ thống.
- Nguồn RCA chính: [repo D:/RCA-flashticket](D:/RCA-flashticket), bắt đầu từ [AGENTS](D:/RCA-flashticket/AGENTS.md), [STATE](D:/RCA-flashticket/docs/STATE.md), [MRP](D:/RCA-flashticket/docs/MRP.md) và [DECISIONS](D:/RCA-flashticket/docs/DECISIONS.md).
- Cửa chiều ngược lại: [Hệ thống → RCA](../project/lien-ket-rca.md). Hai cửa giữ nguyên trách nhiệm; R0 không sở hữu chương trình nghiên cứu.

## 1. Vai trò và nguồn sự thật

Repo RCA sở hữu phương pháp, dataset, code nghiên cứu, giao thức, kết quả và quyết định nghiên cứu. Repo FlashTicket Platform sở hữu nghiệp vụ, kiến trúc, dữ liệu quan sát, vị trí tích hợp, phân quyền và đường cung cấp kết quả. Cả hai cùng phục vụ [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md); không dùng kết quả RCA để tự đổi service, Saga, bất biến, API hoặc schema nghiệp vụ.

Các chương trình, đề xuất MyRCA, phiếu A7–A10 và Task A–G cũ đã được gỡ khỏi bản làm việc. Không dùng mã quyết định RCA-* hoặc trạng thái trong hồ sơ đó làm nguồn điều hành hiện tại. Lịch sử không bị xóa khỏi Git.

## 2. Đầu vào từ nghiên cứu

- [MRP P9/P10](D:/RCA-flashticket/docs/MRP.md): phạm vi tích hợp, nguồn dữ liệu và đánh giá trên FlashTicket.
- [Yêu cầu quan sát P9, R-01–R-10](D:/RCA-flashticket/results/T-040/yeu-cau-quan-sat-p9.md): bộ yêu cầu ban đầu đã chuyển sang P9a; việc được nhóm hệ thống chấp nhận vẫn phải ghi riêng.
- [Hợp đồng M1](D:/RCA-flashticket/results/T-043/hop-dong.json) và code hiện hành trong `D:/RCA-flashticket/src/m1/`: nguồn kỹ thuật cho adapter. Đọc cùng đính chính, quyết định, cấu hình và manifest của lượt sử dụng; không lấy trạng thái cũ trong một artifact làm trạng thái toàn chương trình.

Trước khi dùng kết quả cũ, kiểm nguồn ghim theo manifest phù hợp trong repo RCA. Không sửa snapshot hoặc manifest cũ để khớp tài liệu hệ thống mới.

## 3. Yêu cầu quan sát chuyển sang hệ thống

Bảng này định tuyến tới nguồn P9, không tự phê duyệt yêu cầu kỹ thuật hay xác nhận runtime. Các mục bên dưới giữ `CANDIDATE`/`OPEN` đối với phần bổ sung chưa được hệ thống chấp nhận; phần đã duyệt trong B16 vẫn có hiệu lực theo chính B16.

| Nguồn yêu cầu | Nội dung cần đối chiếu | Nơi xử lý phía hệ thống |
|---|---|---|
| R-01, R-02 | Kiểu span, đích lời gọi, tên thao tác và ánh xạ thiết kế–runtime | B13/B14/B16, instrumentation |
| R-03 | DB logic, instance, thao tác và binding tài nguyên có thời gian hiệu lực | B12/B16, deployment manifest |
| R-04 | Broker/vhost/exchange/routing key/queue, message ID, lần giao và nghĩa của link phát–nhận | B13/B16, RabbitMQ/instrumentation |
| R-05, R-06 | Định danh nhất quán của service/instance/host; log nối với span khi có khóa | B16, logging và thuộc tính tài nguyên |
| R-07 | Manifest, nền bình thường, lịch tải, mốc can thiệp/onset/hồi phục độc lập | B15 và giao thức P10 của repo RCA |
| R-08, R-09 | Sampling/drop/retention, ranh giới trace, parent/link/correlation đúng nghĩa | B13/B14/B16 |
| R-10 | Revision nguồn, image/deployment và thời gian hiệu lực để nối nguồn tĩnh với runtime | CI/deployment manifest, cửa hệ thống → RCA |

Chi tiết trường, căn cứ, phép kiểm và giới hạn đọc tại nguồn P9. Ghi rõ đề xuất nào được chấp nhận, chưa chấp nhận hoặc thiếu bằng chứng; không coi việc dẫn nguồn này là đã triển khai hay nghiệm thu. Thiếu nhãn đánh giá không đồng nghĩa không chạy được chẩn đoán. Không suy quan hệ nhân quả chỉ từ timestamp, correlation hoặc cạnh phụ thuộc.

## 4. Phần còn mở và hồ sơ lịch sử

B16 là baseline quan sát đã duyệt, chưa tự chứng minh đủ đầu vào cho phương pháp hiện hành hoặc đạt hiệu quả RCA. Bổ sung trường, hợp đồng, runtime, người nhận kết quả và giao thức đánh giá phải theo phạm vi Minh duyệt riêng. Lượt dọn này không chọn model LLM, không đóng điểm OPEN và không đổi quyền dữ liệu/thực nghiệm.

Các tham chiếu cũ tới “12 ràng buộc”, số mục 1–12 hoặc `R0-OPEN-*` thuộc phiên bản R0 lịch sử; không ánh xạ ngầm sang R-01–R-10. Khi truy vết quyết định cũ, đọc [R0 tại revision trước đợt dọn](https://github.com/levanminh04/flash-ticket-platform/blob/98d5ad5546244e5e7e29b41dbef92d454f50a7ac/docs/research-rca/R0-boi-canh-va-rang-buoc.md).

Toàn bộ hồ sơ RCA cũ còn trong [lịch sử Git](https://github.com/levanminh04/flash-ticket-platform/tree/98d5ad5546244e5e7e29b41dbef92d454f50a7ac/docs/research-rca). Phần sửa chưa commit trước khi dọn được giữ trong [bản patch](../archive/rca-v1-uncommitted-2026-11-09.patch). Không dùng hồ sơ này để khôi phục một kế hoạch chạy cũ.
