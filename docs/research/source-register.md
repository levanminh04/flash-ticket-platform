# Sổ nguồn cho Giai đoạn 1

**Mục đích:** giữ nguồn, ngày truy cập và giới hạn sử dụng trước khi chuyển sang trích dẫn đánh số trong báo cáo. Đây không phải ma trận truy vết yêu cầu.

## Nguồn khảo sát sản phẩm

| Mã | Nguồn chính thức | Truy cập | Dùng cho | Giới hạn |
|---|---|---|---|---|
| S-01 | [Ticketbox — trang chủ](https://ticketbox.vn/) | 2026-08-09 | Tìm kiếm, danh sách, lối vào vé | Nội dung động |
| S-02 | [Ticketbox — The Starry: VOCAL NIGHT](https://ticketbox.vn/the-starry-vocal-night-26409) | 2026-08-09 | Hạng vé, hết vé, giữ 15 phút, QR/check-in | Quy định của một sự kiện, không đại diện toàn nền tảng |
| S-03 | [Ticketbox — Điều khoản khách hàng](https://static.ticketbox.vn/site/global/content/file_pdf/6_Ticketbox_Dieu_khoan_su_dung_ap_doi_voi_Khach_hang_2023.pdf) | 2026-08-09 | Trình tự nhận vé QR và quy tắc vé | Chính sách có thể được cập nhật |
| S-04 | [TicketGo — Hướng dẫn đặt vé](https://www.ticketgo.vn/page/huong-dan-dat-ve) | 2026-08-09 | Chọn vé, thông tin buyer, thanh toán, nhận vé | Tài liệu hướng dẫn, không phải kiểm thử giao dịch |
| S-05 | [TicketGo — FAQ](https://ticketgo.vn/page/cac-cau-hoi-thuong-gap) | 2026-08-09 | Lỗi thanh toán, nhận mã vé, đổi/hủy | Không cho biết kiến trúc nội bộ |
| S-06 | [TicketGo — Điều khoản khách hàng](https://ticketgo.vn/page/chinh-sach-dieu-khoan-su-dung-cho-khach-hang) | 2026-08-09 | Chính sách hoàn theo sự kiện | Phải phân biệt chính sách nền tảng và organizer |
| S-07 | [Eventbrite — Checkout](https://www.eventbrite.com/help/en-us/articles/333111/) | 2026-08-09 | Chọn vé, checkout, sold out, xác nhận | Tài liệu trợ giúp |
| S-08 | [Eventbrite — Reserved seating](https://www.eventbrite.com/help/en-us/articles/216108/) | 2026-08-09 | Sold/held/available và pending | Tài liệu organizer công khai, không phải quan sát buyer UI |
| S-09 | [Eventbrite — Find tickets](https://www.eventbrite.com/help/en-us/articles/319355/where-are-my-tickets/) | 2026-08-09 | Truy cập vé qua tài khoản/email/app | Có thể khác theo sự kiện/khu vực |
| S-10 | [Eventbrite — Prevent ticket sharing](https://www.eventbrite.com/help/en-us/articles/308105/how-can-i-prevent-attendees-from-sharing-the-same-ticket/) | 2026-08-09 | QR và phản hồi vé đã dùng | Chỉ quan sát hành vi công bố |
| S-11 | [Eventbrite — Organizer Refund Policy Requirements](https://www.eventbrite.com/help/en-us/articles/827759/) | 2026-08-09 | Quan hệ chính sách organizer–platform | Chính sách của Eventbrite, không sao chép thành yêu cầu FlashTicket |
| S-12 | [Eventbrite — Cancelled Event Policy](https://www.eventbrite.com/help/en-us/articles/724340/) | 2026-08-09 | Hoàn tiền khi sự kiện hủy | Cùng giới hạn như S-11 |

## Nguồn kỹ thuật/cơ sở lựa chọn

| Mã | Nguồn | Dùng cho | Lưu ý khi viết báo cáo |
|---|---|---|---|
| T-01 | Malcolm Featonby, [Making retries safe with idempotent APIs](https://aws.amazon.com/builders-library/making-retries-safe-with-idempotent-APIs/), Amazon Builders’ Library | Retry, idempotency key và tác dụng phụ lặp | Kinh nghiệm kỹ thuật của AWS, không phải bằng chứng FlashTicket đã đạt at-most-once |
| T-02 | AWS Prescriptive Guidance, [Transactional outbox pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html) | Rủi ro dual-write và consumer idempotent | Chỉ là phương án ứng viên cho ADR sau này |
| T-03 | Hector Garcia-Molina, Kenneth Salem, [Sagas](https://doi.org/10.1145/38713.38742), ACM SIGMOD Record, 1987 | Nguồn gốc khái niệm Saga | Không viết đồ án “đề xuất Saga” |
| T-04 | OpenTelemetry, [Observability primer](https://opentelemetry.io/docs/concepts/observability-primer/) | Quan hệ log–span–trace và chẩn đoán phân tán | Tài liệu chính thức, dùng cho nguyên tắc instrumentation |
| T-05 | Pinjia He, Jieming Zhu, Zibin Zheng, Michael R. Lyu, [Drain: An Online Log Parsing Approach with Fixed Depth Tree](https://doi.org/10.1109/ICWS.2017.13), ICWS 2017 | Drain là log parser online và cách đánh giá template | Thuật toán có sẵn, **và việc có dùng kỹ thuật gom mẫu log hay không là ứng viên chưa chốt** thuộc bộ RCA (`RES-035`). Nếu dùng, đóng góp của đồ án là áp dụng/hiệu chỉnh/đánh giá — **không** phải bản thân thuật toán. Nếu không dùng, nguồn này chỉ còn vai trích dẫn nền ở phần cơ sở lý thuyết. *Sửa 2026-08-29: ghi chú cũ giả định Drain chắc chắn được dùng.* |

## Nguồn chẩn đoán nguyên nhân gốc

Nhóm nguồn này mở sau định hướng của giảng viên hướng dẫn ngày 2026-08-22. `T-07` và `T-08` là hai nguồn giảng viên gửi trực tiếp.

| Mã | Nguồn | Truy cập | Dùng cho | Giới hạn khi viết báo cáo |
|---|---|---|---|---|
| T-06 | Luan Pham và cộng sự, [RCAEval: A Benchmark for Root Cause Analysis of Microservice Systems with Telemetry Data](https://arxiv.org/abs/2412.17015), WWW 2025; mã nguồn tại [github.com/phamquiluan/RCAEval](https://github.com/phamquiluan/RCAEval) | 2026-08-23 | Định nghĩa `AC@k` và `Avg@k`; cấu trúc RE1/RE2/RE3; hai mức đánh giá thô–mịn; danh sách 15 phương pháp tham chiếu | Số chỉ số của RE2 cần xác minh lại từ bảng 2 theo `A7-OPEN-01`. Không viết đồ án “đề xuất benchmark” |
| T-07 | Luan Pham và cộng sự, [Root Cause Analysis for Microservice System based on Causal Inference: How Far Are We?](https://arxiv.org/abs/2408.13729), ASE 2024; artifact dữ liệu tại [Zenodo 13305663](https://zenodo.org/records/13305663) | 2026-08-23 | Khảo sát 9 phương pháp phát hiện nhân quả và 21 phương pháp RCA; dữ liệu tổng hợp và dữ liệu hệ thật | **Nguồn giảng viên gửi.** Bộ dữ liệu này **chỉ có metric**, không có log và trace |
| T-08 | [LEMMA-RCA: A Large Multi-modal Multi-domain Dataset for Root Cause Analysis](https://arxiv.org/html/2406.05375v1) | 2026-08-23 | Bộ dữ liệu đa phương thức 51 ca lỗi; độ đo `Precision@K`, `MAP@K`, `MRR` | **Nguồn giảng viên gửi.** Một phần dữ liệu thuộc hệ xử lý nước, không phải giao dịch trực tuyến. Bộ độ đo khác `T-06`; không trộn số giữa hai nguồn vào một bảng |
| T-09 | Luan Pham, Huong Ha, Hongyu Zhang, [BARO: Robust Root Cause Analysis for Microservices via Multivariate Bayesian Online Change Point Detection](https://arxiv.org/abs/2405.09330), FSE 2024 | 2026-08-23 | Đại diện họ thống kê thuần: phát hiện điểm đổi đa biến kết hợp kiểm định phi tham số | Chưa đọc toàn văn; khẳng định “không dựng đồ thị” còn `OPEN` theo `A7-OPEN-04` |
| T-10 | Azam Ikram và cộng sự, [Root Cause Analysis of Failures in Microservices through Causal Discovery](https://papers.nips.cc/paper_files/paper/2022/hash/c9fcd02e6445c7dfbad6986abee53d0d-Abstract-Conference.html), NeurIPS 2022 (RCD) | 2026-08-23 | Đại diện họ suy luận nhân quả: coi sự cố như một can thiệp, học phân cấp thay vì học toàn đồ thị | Thuật toán đã công bố; không được viết là “đồ án đề xuất RCD” |
| T-11 | Guangba Yu và cộng sự, [MicroRank: End-to-End Latency Issue Localization with Extended Spectrum Analysis in Microservice Environments](https://dl.acm.org/doi/10.1145/3442381.3449905), WWW 2021 | 2026-08-23 | Đại diện họ đồ thị phụ thuộc dựng từ trace: personalized PageRank kết hợp phân tích phổ | Gần định hướng “đồ thị phụ thuộc” nhất; việc dùng lại không tạo ra tính mới |
| T-12 | Li Wu và cộng sự, [MicroRCA: Root Cause Localization of Performance Issues in Microservices](https://www.semanticscholar.org/paper/MicroRCA:-Root-Cause-Localization-of-Performance-in-Wu-Tordsson/6b537317fadbf2b2e7f51f2e9648ca9d1a9e426f), NOMS 2020 | 2026-08-23 | Đồ thị thuộc tính mô hình hóa lan truyền bất thường xuyên service và máy chủ | Số hiệu năng công bố đo trên bộ dữ liệu riêng của tác giả, **không so trực tiếp** với số của `T-06` |
| T-13 | Zeyan Li, Junjie Chen và cộng sự, [Practical Root Cause Localization for Microservice Systems via Trace Analysis](https://github.com/NetManAIOps/TraceRCA), IWQoS 2021 (TraceRCA) | 2026-08-23 | Đại diện họ dùng trace nhưng không lan truyền: phát hiện trace bất thường, khai phá tập service nghi ngờ, xếp hạng | Phân biệt rõ với `T-11`: không dùng PageRank trên đồ thị |
| T-14 | [FudanSELab/train-ticket](https://github.com/FudanSELab/train-ticket) | 2026-08-23 | Hệ thống benchmark Java lớn nhất được dùng sinh dữ liệu RE1/RE2/RE3 | Chỉ dùng để mô tả quy mô hệ thống sinh dữ liệu; nhóm không cần cài đặt |

Khi nhận mẫu báo cáo, chuyển các mục thực sự được trích dẫn sang định dạng `[n]`; không đưa nguồn chỉ đọc tham khảo nhưng không dùng vào danh mục cuối.
