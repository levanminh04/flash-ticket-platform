# B15 — Kế hoạch kiểm chứng theo lát dọc

- Phiên bản B15-v0.1; ngày 2026-09-14; APPROVED về kế hoạch; FORMATION; người duyệt Lê Văn Minh, ngày 2026-09-14 (GOV-144).
- Nguồn B11-C-v0.3 APPROVED, B6-v0.15/B7-v0.13/B8-v0.13 APPROVED, B12-v0.2/B13-v0.1/B14-v0.1 và B16-v0.1 APPROVED; không sinh bất biến/ASR/ngưỡng mới.
- Ưu tiên PRJ-016–021: luồng chính đúng → dữ liệu quan sát → thử tải/chèn lỗi/RCA. Không yêu cầu hoàn thiện công cụ tải trước code.

## 1. Kết quả thật nằm ở đâu?

[B12-validation](B12-validation.md) là sổ kết quả lớp dữ liệu hiện hành, gồm lệnh, phiên bản và giới hạn. PASS schema không là PASS service, đồng thời, gateway hoặc RCA. Các ca ứng dụng dưới đây **NOT RUN** cho tới khi có service và báo cáo chạy. Không dùng checklist đã viết như bằng chứng đạt.

## 2. Nhóm kiểm phải chạy khi code

| Mã | Đầu vào/thao tác | Bất biến/kết quả bắt buộc | Giai đoạn |
|---|---|---|---|
| V01 | Tạo QUANTITY, STANDING nhiều loại vé chung sector, SEATED; sai mode/sector/type | Một đơn một event/sector; không trừ sector thêm lần nữa khi đã giữ seat; giá từ backend | Lát Booking đầu |
| V02 | 2 connection giữ ghế cuối, capacity cuối và buyer limit cuối | INV-01/04; đúng một thắng hoặc không vượt phần còn lại; counter khớp allocations | Trước tích hợp payment |
| V03 | Mất response create order, retry cùng key; cùng key khác payload | Cùng order/reservation; khác input 409; không giữ chỗ hai lần | Booking |
| V04 | Áp promotion đồng thời, hết lượt, đã dùng, hết hạn, total <=0 | Sáu lý do FR-22 phân biệt; usage và counter nguyên tử, không giữ mã hai lần | Booking |
| V05 | Giá/giảm 1.5; subtotal100005 giảm10%; 2 đơn100050 phí1% | Reject đầu vào lẻ; discount10001,total90004; phí toàn event2001 chứ không2002 | Unit tiền trước cổng |
| V06 | Freeze, attempt thất bại, sửa mã, retry; mất response freeze | Tiền/email/items snapshot không đổi; không gia hạn; retry giữ mốc/version | Booking/Payment |
| V07 | IPN lặp C1; C1/C2 thực thu khác; IPN invalid signature/amount/attempt | Một confirmation; C2 hoàn riêng; invalid không tạo vé. Không thử/nhận là đã có workflow Q-07 | Payment |
| V08 | Chờ khóa qua expires_at, confirm/expire/cancel cạnh tranh | Dùng decision time sau khóa; đúng hạn là hết hạn; ACCEPTED replay vẫn ACCEPTED; không hồi sinh | B14 race |
| V09 | Crash giữa ghi charge–outbox, broker confirm–published_at, effect–ACK | Retry hội tụ, không mất lệnh và không lặp hiệu ứng; inbox/hash đúng | Sau broker tích hợp |
| V10 | Một đơn nhiều item, lỗi giữa tạo vé; timeout; terminal FAILED rồi lệnh cũ đến | Toàn bộ hoặc không vé; timeout không refund; FAILED fence ngăn completion muộn | Ticket/Payment |
| V11 | Refund timeout sau gateway đã hoàn, nhiều nguyên nhân cùng charge | Một refund logic; query/retry cùng identity, không hoàn hai lần | Payment |
| V12 | Cancel trước config, config cũ sau cancel, cancel giữa acceptance/issuance, worker restart | Cổng hủy đơn điệu; không mở nguồn cung; run không bỏ khoản liên kết tới trễ; công bố độ trễ | Hội tụ liên service |
| V13 | SMTP lỗi, gửi lại key cũ/mới; tải lại QR sau restart/mất key | Không tạo ticket/refund mới vì email; token ổn định; thiếu key lỗi có kiểm soát | Ticket |
| V14 | Hai thiết bị cùng vé, nhiều vé cùng đơn; retry cùngkey; saiowner/window/QR | Một success; audit mỗi receipt + replay liên kết; lý do phân biệt, không offline | Check-in |
| V15 | DRAFT load→edit→save→API reload không local draft | ID/type/tọa độ/màu/shape/hidden giữ đúng; ẩn không xóa hoặc mất type | Adapter FE |
| V16 | Lỗi giữa save sơ đồ, xóa child sau submit, đổi mode | Rollback nguyên bộ; xóa giới hạn DRAFT; ownership ở API | Event |
| V17 | Keycloak đã grant nhưng Mongo chưa ACTIVE; hai admin approve/reject | Không false ACTIVE, worker query/retry đúngsubject; profile rebuild được; giữ BUYER | User/identity |
| V18 | Follow lặp, unfollow lặp, count | Một quan hệ, count từ source, không dual-write lệch counter | User |
| V19 | Payout còn tiền pending, feeconfig thiếu, paid lặp | Từ chối; đúng fee đã duyệt; một lần đánh paid + actor/time/evidence | Payment đọc/đối soát |
| V20 | Một giao dịch xuyên callback/retry/message, secret/QR mẫu trong input | Correlation nối đủ; log/trace có node/time/outcome; không lộ dữ liệu cấm | Theo B16 từ lát đầu |

## 3. Fake gateway có khó không?

Mức tối thiểu tương đối gọn vì Payment đã có adapter: fake nhận attemptId/amount, giữ kết quả theo key, trả success/failure/unknown có cấu hình; query trả cùng kết quả; refund theo request key. Fake đi qua **đúng handler kiểm kết quả và orchestration** của ứng dụng, không cập nhật thẳng DB để giả thành công. Phải test fake contract trước dùng để đo tải.

Giai đoạn đầu dùng fake in-process cho unit/component tests. Khi thử tải end-to-end mới chạy fake HTTP tách riêng trong môi trường thử; không tính là service nghiệp vụ mới. Cấu hình fake phải bị chặn ở production, credential test riêng và không tạo giao dịch thật. Các khả năng delay, duplicate callback, drop response thêm sau happy path, không dựng simulator lớn ngay.

VNPay sandbox dùng kiểm định dạng, chữ ký, redirect/IPN/query/refund thật theo quyền merchant. Không mặc định được phép bắn tải vào sandbox. Phép thử tải hệ thống nhóm dùng fake kiểm soát; công bố fake không đo độ trễ/nguyên nhân nội bộ VNPay/ngân hàng.

## 4. Thử tải và RCA — làm sau, không bỏ chuẩn dữ liệu hôm nay

Trước mỗi run ghi commit/schema/phiên bản, tài nguyên hai máy, heap/limit, dữ liệu seed tổng hợp, workload, warmup, thời gian đo, concurrency và sampling. Tách workload QUANTITY/STANDING/SEATED, không chỉ một endpoint đọc. Chạy tăng tải có giới hạn, theo dõi oversell/duplicate rights/refund và telemetry loss bên cạnh p50/p95/p99/throughput/error.

Chèn lỗi một thành phần trong môi trường được phép: service stop, DB/broker delay/disconnect, gateway timeout. Ghi thời điểm/định danh can thiệp và sự thật đối chứng trước chấm RCA; không sửa hệ thống bằng cơ chế RCA. Không dùng trace bị sampling mất để kết luận nghiệp vụ chưa chạy. Ngưỡng hiệu năng và phương pháp xếp hạng/đo giải thích giữ ở nguồn B9/B10 và bộ RCA đúng hai cửa nối, không tự chốt ở B15.

## 5. Cách lưu báo cáo test

Mỗi kết quả: test ID, ngày/commit, môi trường, dữ liệu/seed, lệnh, expected/actual, exit code, log đã lọc, hash bộ dữ liệu và giới hạn. Test concurrency phải có connection độc lập và barrier tạo tranh chấp; không gọi tuần tự rồi gọi đó là race. Restore chạy vào môi trường tách biệt, không đè kho nhóm đang dùng. Thất bại giữ nguyên làm bằng chứng và sửa trong phạm vi được duyệt, không loại ca khó khỏi mẫu.

Phần đưa báo cáo: INV→thiết kế lớp bảo vệ→test thật→kết quả/giới hạn; không đưa toàn bộ trạng thái quản trị hoặc secret vào báo cáo. Chưa có service thì các V01–V20 chưa đạt, dù kiểm tĩnh/SQL bên dưới đã PASS.

## Phụ lục đối chiếu R0 và phép thử độc lập — 2026-09-14

R0-v0.5 §3 mục 2/12, qua cửa project/lien-ket-rca.md, vẫn CANDIDATE: kế hoạch có môi trường/run manifest và phần chèn lỗi/nhãn. Harness chưa hiện thực và dữ liệu chưa được thu; việc đo độ phủ/nhãn vẫn OPEN cho Giai đoạn 6, không là blocker code luồng chính. B15 thuộc bộ hệ thống; nguồn hình thành ở header và B12-validation, B13/B14/B16; nguồn RCA chỉ đối chiếu tại phụ lục, không tự sinh phương pháp hoặc ngưỡng đánh giá.
