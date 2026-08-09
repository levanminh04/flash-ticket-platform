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
| T-05 | Pinjia He, Jieming Zhu, Zibin Zheng, Michael R. Lyu, [Drain: An Online Log Parsing Approach with Fixed Depth Tree](https://doi.org/10.1109/ICWS.2017.13), ICWS 2017 | Drain là log parser online và cách đánh giá template | Thuật toán có sẵn; đóng góp của đồ án là áp dụng/hiệu chỉnh/đánh giá trong phạm vi hệ thống |

Khi nhận mẫu báo cáo, chuyển các mục thực sự được trích dẫn sang định dạng `[n]`; không đưa nguồn chỉ đọc tham khảo nhưng không dùng vào danh mục cuối.
