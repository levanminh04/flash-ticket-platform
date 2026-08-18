# BỐI CẢNH VÀ MONG MUỐN — ĐATN FlashTicket

> **Mục đích:** ghi lại bối cảnh thực và mong muốn của chủ đồ án, tổng hợp từ các trao đổi và từ tài liệu do chính chủ đồ án cung cấp.
>
> **Nguyên tắc soạn:** chỉ ghi những gì chủ đồ án đã nói hoặc đã viết. Không thêm phân tích, không thêm đề xuất, không suy diễn. Cột **Nguồn** cho biết thông tin lấy từ đâu để kiểm chứng lại.
>
> **Phân loại sử dụng:** đây là hồ sơ bối cảnh nội bộ, không phải văn bản đưa nguyên trạng vào báo cáo. Mạch báo cáo được viết độc lập theo vấn đề → yêu cầu → phân tích miền → thiết kế đích → hiện thực → đánh giá; lịch sử repository không được dùng làm tính cấp thiết hoặc khoảng trống nghiên cứu.
>
> **Ký hiệu nguồn:** `[TĐ]` = trao đổi trực tiếp · `[ĐH]` = file *ĐỊNH HƯỚNG ĐỒ ÁN TỐT NGHIỆP.pdf* · `[KH]` = file *FlashTicket Automated Error Detection and Remediation Assistant: Design Plan.pdf* · `[TL]` = bộ tài liệu ba tầng (A/B/C, B5.5)

---

## 1. Thông tin định danh

| Mục | Nội dung | Nguồn |
|---|---|---|
| Trường | Học viện Công nghệ Bưu chính Viễn thông (PTIT), Khoa Công nghệ Thông tin 1 | [ĐH] |
| Đề tài | Hệ thống đặt vé sự kiện trực tuyến | [ĐH] |
| Thành viên | Lê Văn Minh (B22DCCN533) · Phạm Văn Tuyến (B22DCCN773) · Phạm Long Nhật (B22DCCN581) | [ĐH] |
| Giảng viên hướng dẫn | Cô Liên | [ĐH] |
| Repository hiện thực ĐATN | github.com/levanminh04/flash-ticket-platform | [TĐ] |
| Nguồn tài sản hiện thực nội bộ | github.com/levanminh04/flash-ticket-system | [ĐH] [TĐ] |
| Hạn nộp | 14/12/2026 | [TL] |
| Hình thức chấm | Chấm điểm cá nhân, một quyển báo cáo, hỏi theo phần | [TL] |

**Phân công theo tài liệu định hướng** [ĐH]:
- 01 thành viên: backend, kiến trúc phân tán, giao dịch đặt vé, kiểm thử độ tin cậy
- 01 thành viên: frontend web (người mua + nhà tổ chức), luồng nghiệp vụ trên web, chức năng AI
- 01 thành viên: ứng dụng mobile và nghiệp vụ check-in trực tuyến (quét QR, dùng lại backend Spring Boot, xử lý nhiều thiết bị)
- Cả ba: phân tích nghiệp vụ, tích hợp, kịch bản kiểm thử, đánh giá kết quả, hoàn thiện báo cáo

**Bổ sung từ trao đổi** [TĐ]: chủ đồ án là người quyết định thiết kế toàn hệ thống.

---

## 2. Hiện trạng

| Mục | Nội dung | Nguồn |
|---|---|---|
| Trạng thái hệ thống | Đã chạy được các luồng chính, nhưng phần lớn mới ở mức happy case | [ĐH] |
| Chưa đánh giá đầy đủ | Cấu trúc dịch vụ, kiểm thử tải, khả năng phục hồi lỗi, tính nhất quán dữ liệu khi tải cao | [ĐH] |
| Cấu trúc mã nguồn | `core-service` là một ứng dụng Spring Boot nguyên khối, mã được tổ chức chủ yếu theo các package nghiệp vụ `event`, `booking`, `payment`, `notification`, `promotion`, `shared`, `common`; ranh giới module chưa được công cụ hoặc cấu hình build cưỡng chế; ngoài ra có `user-service`, `discovery-service` | [TL] |
| Đã triển khai thực tế | Toàn bộ hệ thống đã chạy trọn vẹn trên một máy `m7i-flex.large` (2 vCPU, 8 GiB), không gặp lỗi hết bộ nhớ | [TL] |
| Chưa kiểm chứng | Chưa chạy thí nghiệm tải trên cấu hình đó | [TL] |

**Đã triển khai** [ĐH]: Java/Spring Boot/Spring Cloud Gateway/Eureka/Config Server · ReactJS + TypeScript + Vite · PostgreSQL + MongoDB · Redis + RabbitMQ · Keycloak/OAuth2/JWT · VNPay Sandbox · LangChain4j + RAG + PgVector (mức ban đầu) · Docker + Nginx · AWS EC2

**Dự kiến bổ sung** [ĐH] [TĐ]: ứng dụng di động React Native cho các luồng trực tuyến · JUnit, Mockito, Testcontainers, k6 · Prometheus, Grafana, OpenTelemetry, Loki, Tempo · Cloudflare, rate limiting, WAF. GitHub Actions/GHCR và CI/CD là phần hỗ trợ, thực hiện nếu điều kiện cho phép, không phải mục tiêu nghiên cứu cốt lõi.

---

## 3. Trục nghiên cứu và vấn đề cần giải quyết

**Trục nghiên cứu chính** [ĐH]: nghiên cứu các phương pháp bảo đảm tính nhất quán và độ tin cậy trong toàn bộ vòng đời của vé — từ giữ chỗ, thanh toán, phát hành vé đến kiểm soát vé tại cổng.

**Năm vấn đề chuyên sâu** [ĐH]:
1. Ngăn bán vượt số lượng vé khi nhiều người cùng đặt một ghế hoặc một loại vé trong thời gian ngắn
2. Bảo đảm một giao dịch thanh toán chỉ được ghi nhận một lần khi callback hoặc thông điệp bị gửi lặp
3. Bảo đảm quy trình giữ chỗ – thanh toán – phát hành vé có thể tiếp tục hoặc phục hồi khi một dịch vụ gặp lỗi
4. Ngăn một vé được check-in nhiều lần khi nhiều thiết bị di động quét đồng thời
5. Đánh giá độ trễ, thông lượng, tỷ lệ lỗi và khả năng phục hồi bằng workload tổng hợp và kiểm thử tự động

**Tiêu chí nghiệm thu định hướng** [ĐH] [TĐ]:
- Microservices: chức năng đầy đủ, không bán vượt vé dưới tải cao
- Ứng dụng di động quét QR trực tuyến: hoạt động và một vé không được check-in thành công nhiều lần, kể cả khi nhiều thiết bị gửi yêu cầu gần đồng thời
- Kịch bản kiểm thử & benchmark: oversell = 0, độ trễ, throughput đạt mục tiêu
- Hệ thống giám sát: dashboard thời gian thực, cảnh báo tự động
- Pipeline CI/CD: phần cộng thêm nếu hoàn thành được; không dùng làm điều kiện để kết luận trục nghiên cứu chính thành công hay thất bại

---

## 4. Nỗi đau thực tế dẫn đến tính năng AI

**Quy trình xử lý sự cố hiện tại** [TĐ] [KH]:
> Khi có bug do khách hàng báo cáo hoặc bug âm thầm, phải vào Loki hoặc SSH vào Linux, sau đó **tái hiện lại lỗi và đợi log xuất hiện** để xem tên lỗi. Ngoài ra còn phải **đi dò lại đoạn code bị lỗi**.

**Mô tả trong tài liệu thiết kế** [KH]: quy trình giống công ty — khách báo qua Jira, kỹ sư SSH vào Linux hoặc mở Loki, cố tái hiện lỗi, rồi đợi log hiện ra. Tốn từ vài chục phút đến vài giờ mỗi lần.

**Dữ liệu đang có** [TĐ]: một lượng lớn log của các service; khi có lỗi thì có exception, warning, error trong log.

**Kinh nghiệm nền** [TL]: kinh nghiệm thực tiễn trong quy trình xử lý sự cố phần mềm tại doanh nghiệp.

**Phạm vi mong muốn của tính năng** [TĐ]:
- Hỗ trợ **rút ngắn thời gian dò bug ở một mức nhất định**, không cần triển khai hoành tráng
- Nhắm vào **những bug hay gặp trong hệ thống**: bug âm thầm, bug exception, bug database, bug hạ tầng
- **Không kỳ vọng** hỗ trợ tận gốc cho những bug hoặc sự cố lớn
- Bám sát nghiệp vụ flash ticket với ba vai trò: admin, buyer, organizer

**Hướng kỹ thuật đang cân nhắc** [TĐ]: chuẩn hóa log trước, dùng Drain để gom các dòng log thành mẫu, sau đó chọn ngữ cảnh liên quan và gửi tới LLM API để nhận tư vấn đơn giản. Nhóm chưa chốt chi tiết cách chọn ngữ cảnh, cách hiệu chỉnh Drain và cách đánh giá tính năng.

---

## 5. Mong muốn về kiến trúc và cách làm

| # | Mong muốn | Nguồn |
|---|---|---|
| 1 | **Xác định rõ kiến trúc hệ thống ngay từ đầu**, trước khi viết dòng code đầu tiên | [TĐ] |
| 2 | Rút kinh nghiệm: lần trước **không chốt kiến trúc rõ ràng trước mà vừa chốt vừa làm**, đến khi mọi thứ quá lớn thì không đập đi xây lại được | [TĐ] |
| 3 | **Dùng repo mới để hiện thực kiến trúc đích, không ghi đè bản cũ** — đã hoàn thành với `flash-ticket-platform`; tài sản đưa vào phải khớp thiết kế và kiểm thử của phiên bản ĐATN | [TĐ] |
| 4 | Cần **tách service, chủ yếu là `core-service`** | [TĐ] |
| 5 | Ý định ban đầu của hai repo là **so sánh monolith với microservices** | [TĐ] |
| 6 | Lỗi Redis ở IPN **không phải ưu tiên** — sẽ review và sửa lại khi làm bản mới | [TĐ] |
| 7 | **Tài liệu hóa mọi thứ** đang làm | [TĐ] |
| 8 | Cần **một phương pháp làm việc khoa học** | [TĐ] |
| 9 | Thiết kế lại dữ liệu theo ranh giới service: mỗi service sở hữu schema độc lập, không truy vấn hoặc gọi repository trực tiếp sang schema của service khác; giữ lại bảng cũ phù hợp và bổ sung bảng nghiệp vụ/AI khi thật sự phát sinh | [TĐ] |
| 10 | Khảo sát hệ thống bên ngoài ở mức đơn giản, tập trung luồng công khai; không đặt yêu cầu phải tiếp cận chức năng admin/organizer trả phí hoặc không có tài khoản thử nghiệm | [TĐ] |
| 11 | Mobile chủ yếu là một client giao diện dùng lại backend Spring Boot; không triển khai check-in offline | [TĐ] |

---

## 6. Mong muốn về mục tiêu cá nhân

| # | Mong muốn | Nguồn |
|---|---|---|
| 1 | **Học hỏi kỹ thuật** | [TĐ] |
| 2 | **Làm đẹp CV bằng cách thể hiện kỹ năng** | [TĐ] |
| 3 | Đồ án và trục nghiên cứu là "cái cớ" để phù hợp với yêu cầu ĐATN; hai mục tiêu trên mới là đích | [TĐ] |
| 4 | Trường **không bắt sinh viên phải sáng tạo ra cái mới** — đây là đồ án tốt nghiệp, không phải luận án tiến sĩ | [TĐ] |

---

## 7. Mong muốn về cách trao đổi và làm việc

| # | Mong muốn | Nguồn |
|---|---|---|
| 1 | **Đi từng phần một**, không cố nói mọi thứ trong một lần để rồi bị loãng | [TĐ] |
| 2 | **Không trả lời theo từng câu trong đoạn chat** — phải tổng hợp hết tất cả yếu tố để đưa ra giải pháp bao trọn mọi edge case, không vá tạm cho yêu cầu trước đó | [TĐ] |
| 3 | **Mọi lập luận đưa ra đều phải tự phản biện** xem có lỗ hổng nào không | [TĐ] |
| 4 | **Liên kết mọi thứ lại với nhau** — lo ngại công sức thành công cốc sau mỗi câu hỏi mới | [TĐ] |
| 5 | Cần biết rõ **những gì tận dụng lại được, những gì phải bỏ, bổ sung, cắt bớt** | [TĐ] |
| 6 | Không muốn nhận đề xuất bị **rút gọn chỉ vì lo về thời gian**, dẫn tới rối loạn | [TĐ] |

---

## 8. Ràng buộc

| Mục | Nội dung | Nguồn |
|---|---|---|
| Hạ tầng | 2 máy EC2 `m7i-flex.large` (2 vCPU / 8 GiB mỗi máy) | [TĐ] [TL] |
| Tài khoản AWS | **Hai tài khoản AWS riêng biệt** | [TĐ] [TL] |
| Trần phạm vi kiến trúc | Không quá **8 service nghiệp vụ** và không quá **3 luồng Saga** | [TĐ] |
| Nhân lực | Nhóm 3 người | [ĐH] |
| Thời gian | Hạn nộp 14/12/2026 | [TL] |
| Lo ngại đã nêu | Sợ nhiều service và scale instance thì không đủ bộ nhớ | [TĐ] |
| Lo ngại đã nêu | Sợ dồn hết vào một instance thì không demo được end-to-end | [TĐ] |
| Lo ngại đã nêu | Nếu tài liệu nền tảng sai từ đầu thì viết code về sau sẽ là thảm họa, và có thể dẫn tới kết quả xấu cho đồ án của 4 năm đại học | [TĐ] |

---

## 9. Những gì đã chốt

| Mục | Nội dung | Nguồn |
|---|---|---|
| Tính năng AI | **Trợ lý chẩn đoán sự cố** theo hướng chỉ đọc: log có cấu trúc → Drain gom mẫu log → chọn ngữ cảnh → gọi LLM API để đưa ra tư vấn đơn giản | [TĐ] [TL] |
| Khung phương pháp | Bộ tài liệu ba tầng A (phương pháp nghiên cứu) / B (quy trình kỹ thuật) / C (quy ước trình bày) | [TL] |
| Trần service | ≤ 8 service nghiệp vụ | [TĐ] |
| Trần Saga | ≤ 3 luồng Saga | [TĐ] |
| Hạ tầng | Sử dụng 2 máy EC2; **chưa chốt** service/thành phần nào đặt trên máy nào | [TĐ] |
| Mobile | Dùng lại backend Spring Boot cho nghiệp vụ check-in trực tuyến; **bỏ check-in offline** | [TĐ] |
| CI/CD | Là phần hỗ trợ, có thì tốt; không phải trục nghiên cứu hoặc tiêu chí bắt buộc | [TĐ] |
| Ranh giới quyền của AI | Trợ lý chẩn đoán sự cố chỉ đọc log/dấu vết/sự kiện, không ghi nghiệp vụ; chatbot mua vé được gọi API công khai | [TL] |

**Nghiệp vụ đã đồng ý bổ sung** [TĐ] — *chủ đồ án xác nhận "đồng ý hoàn toàn"*: kiểm duyệt sự kiện trước khi mở bán · hoàn tiền một đơn · chính sách hủy/hoàn theo sự kiện · giới hạn vé mỗi tài khoản trong một sự kiện · công bố sự kiện idempotent · sổ cái đối soát chỉ đọc. Cụm lịch sử “Saga xuất bản sự kiện” chỉ được hiểu là yêu cầu lặp thao tác không tạo tác dụng phụ; việc có dùng Saga hay không chờ B10/B11.

**Nghiệp vụ đã đồng ý bỏ** [TĐ]: hủy sự kiện hàng loạt (có thể bỏ qua) · hoàn tiền một phần · chargeback · tranh chấp · chuyển nhượng vé · định giá động · đa tiền tệ · tích điểm.

**Làm rõ sau khi chốt nghiệp vụ** [TĐ]: “hủy sự kiện hàng loạt” ở dòng lịch sử trên là thao tác hủy đồng thời nhiều sự kiện và vẫn nằm ngoài phạm vi. Khi **một** sự kiện bị hủy, hệ thống có thể phải xử lý hoàn tiền cho nhiều đơn của chính sự kiện đó; đây là quy trình khác, đã được ghi tại `BIZ-014`.

---

## 10. Những gì còn để ngỏ

| Mục | Nội dung | Nguồn |
|---|---|---|
| H1 | Chính sách hoàn tiền do nền tảng áp đặt hay nhà tổ chức tự đặt | [TL] |
| H3 | Tỉ lệ giữ lại phòng hoàn tiền và mốc mở kỳ đối soát | [TL] |
| — | Bất biến giữ chỗ/chống bán vượt và trách nhiệm khái niệm quanh tồn kho vé; chưa suy ra service, Saga hoặc schema | [TL] |
| — | Ranh giới quyền giữa chatbot mua vé và trợ lý chẩn đoán; cách triển khai vật lý chờ B11 | [TL] |
| — | Cách bố trí hai EC2 sau khi có sơ đồ container, nhu cầu triển khai và kết quả đo thử ban đầu | [TĐ] |
| — | Workflow cuối cùng của trợ lý chẩn đoán: nguồn log, cách tạo context, dữ liệu cần lưu và bộ ca đánh giá | [TĐ] |

**Tình trạng các câu hỏi lịch sử:** H1 đã được đóng bởi `BIZ-033`; H3 đã được đóng bởi `BIZ-036` và `BIZ-050`–`BIZ-054`. Hai dòng H1/H3 được giữ nguyên ở trên để bảo toàn dấu vết câu hỏi từng tồn tại, không còn là đầu vào `OPEN` của Giai đoạn 2.

---

*Tài liệu này chỉ ghi bối cảnh và mong muốn. Mọi phân tích, đề xuất và quyết định thiết kế nằm ở bộ tài liệu ba tầng.*
