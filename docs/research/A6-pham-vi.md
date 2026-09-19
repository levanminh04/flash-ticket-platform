# A6 — Phạm vi

- **Phiên bản:** `A6-v0.7`; cập nhật 2026-09-18.
- **Trạng thái:** `REVIEW_READY` — đã đồng bộ định hướng DT18; phần diễn giải cần Minh rà soát, không tự kế thừa phê duyệt phiên bản cũ.
- **Người duyệt:** —; **Ngày duyệt:** —.
- **Đầu vào:** [tên và nhiệm vụ hiện hành](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) (`DT18-TEN`, `DT18-NV1`–`DT18-NV3`, `PRJ-025/026`); `PRJ-035` về mobile; các quyết định nghiệp vụ/kiến trúc còn hiệu lực.
- **Hiệu lực:** tên, nguyên văn nhiệm vụ và ưu tiên mobile là `USER_CONFIRMED`; các cách diễn đạt/ánh xạ bên dưới là `CANDIDATE` trừ phần dẫn rõ quyết định đã có. DH-* chỉ được dùng để truy lịch sử.

## 1. Phạm vi xây dựng và đánh giá chính

| Nội dung | Nguồn | Bằng chứng cần thu |
|---|---|---|
| Hệ thống bán vé phân tán: sự kiện, loại vé, đặt vé, tồn kho, thanh toán | DT18-NV1; nghiệp vụ chi tiết ở B6/B8 | Chức năng, hợp đồng và bất biến của luồng chính |
| Kiểm thử tải và đồng thời | DT18-NV1 | Thông lượng, thời gian đáp ứng, tỷ lệ lỗi, khả năng mở rộng, nhất quán dữ liệu, độ ổn định giao dịch |
| Đồ thị dịch vụ/thành phần, cạnh gọi/thông điệp/phụ thuộc dữ liệu | DT18-NV2 | Danh sách nút/cạnh có nguồn và quy tắc ánh xạ; không coi mọi cạnh là quan hệ nhân quả |
| Thu thập, ánh xạ log, trace, metrics; vùng ảnh hưởng, bất thường, xếp hạng | DT18-NV2 | Độ phủ dữ liệu, ca phát hiện và chẩn đoán, bằng chứng hỗ trợ từng kết quả |
| Thực nghiệm công khai trước, thử trên FlashTicket sau | DT18-NV3 | Giao thức riêng từng bộ/môi trường, độ đo phù hợp, phân tích ca hiệu quả và hạn chế |
| Giao diện minh họa kết quả phân tích | DT18-NV3 | Kết quả và bằng chứng được trình bày cho người có quyền; chi tiết giao diện còn OPEN |

Không dùng khung cũ “Vòng 1 đo sâu, Vòng 2 chỉ cần chạy được” để hạ nhiệm vụ kiểm chứng hệ thống. Các yêu cầu nghiệp vụ và quyền chỉ đọc đã duyệt vẫn giữ hiệu lực. Lớp giải thích đã chốt tại RES-034 và B11 không tự bị loại khi nhiệm vụ mới không nhắc tên LLM.

### 1.1 Dữ liệu

RE2 vẫn là lựa chọn nội bộ hiện hành (`RES-022`), có metric/log/trace; RE3 là hướng đánh giá tiếp theo chưa mặc định đã chạy. Không dùng một bộ metric-only để tuyên bố kiểm được toàn bộ cơ chế đa nguồn. Năng lực thực sự của dataset/adapter đọc tại `docs/research-rca/A8-khao-sat-dataset.md` và kết quả kiểm định tại `docs/research-rca/E1-kiem-dinh-rcaeval-va-kha-thi-myrca.md`.

### 1.2 Giới hạn kết luận

Kiểm chức năng, đo tải/đồng thời và đánh giá RCA là các loại bằng chứng khác nhau. Kết quả trên một dataset không chứng minh chất lượng trên FlashTicket; một ảnh giao diện không chứng minh độ chính xác. Chưa có phép thử không được ghi đã đạt.

### 1.3 Lộ trình tích hợp và kiểm chứng

| Bước | Nội dung | Điều kiện |
|---|---|---|
| Mô hình và thực nghiệm ban đầu | Mô hình nút/cạnh có nguồn; khảo sát/chạy phương pháp trên dữ liệu công khai | Schema, nhãn và manifest phù hợp |
| Áp dụng trên FlashTicket | Thu dấu vết thật, ánh xạ đồ thị, chạy giám sát/chẩn đoán và minh họa kết quả | Luồng chính chạy đúng, chuẩn B16 được hiện thực và kiểm |
| Đánh giá ca thử FlashTicket | Ghi ca lỗi, vùng ảnh hưởng và nhãn có căn cứ; chấm bằng độ đo phù hợp, nêu giới hạn | Giao thức/độ đo/cỡ tập ca được chốt trước phép đánh giá; không tự bịa ngưỡng |

Phạm vi đánh giá chi tiết ở bước cuối còn OPEN; không tiếp tục dùng câu lịch sử “cô không yêu cầu, nếu còn sức” để bỏ việc áp dụng thử nghiệm và phân tích kết quả của DT18-NV3.

## 2. Phạm vi sản phẩm và phần phụ

Các luồng web/backend đã duyệt tiếp tục được hiện thực theo B12–B16: giữ chỗ, thanh toán, phát hành/giao vé, quản lý và kiểm soát vé. Phân công hiện hành ở `docs/project/roles.md` gồm Minh, Sơn, Tuấn, Tuyến.

### 2.1 Kiểm chứng và ưu tiên

| Mục | Mức hiện hành | Cách đọc |
|---|---|---|
| Tiền, tồn kho, phát vé, xử lý lặp và check-in backend | Giữ đúng theo yêu cầu đã duyệt | Mobile tùy thời gian không tự bỏ bất biến/API backend |
| Kiểm quyền theo vai trò/sở hữu | Bắt buộc theo RES-048 | Nghĩa vụ an toàn, không tự gọi là đóng góp mới |
| Giám sát bằng đồ thị và giao diện minh họa kết quả | Nhiệm vụ DT18-NV2/NV3 | Không dùng RES-049 để bỏ các sản phẩm này |
| Dashboard vận hành/cảnh báo tự động nâng cao | Giữ ưu tiên thấp của RES-049 trong phạm vi không trùng nhiệm vụ mới | Chưa tự tăng thành hệ vận hành hoàn chỉnh |
| Ứng dụng mobile | Phần phụ, chỉ làm khi thực sự thừa thời gian (PRJ-035) | Chưa giao người làm; không chặn hoàn thành phạm vi chính |
| CI baseline | Minh phụ trách (PRJ-031) | Phần hỗ trợ tích hợp; không phải câu hỏi nghiên cứu |

## 3. Ngoài phạm vi và điều kiện mở rộng

- Check-in offline; hoàn tiền một phần, chargeback, tranh chấp; chuyển nhượng vé, định giá động, đa tiền tệ, tích điểm; hủy đồng thời nhiều sự kiện; đăng nhập mạng xã hội và thu hồi role: giữ các loại trừ đã chốt. Hủy một sự kiện vẫn thuộc phạm vi đã duyệt.
- Không đặt mục tiêu người dùng thật/lưu lượng sản xuất, HA hoặc sáng tạo benchmark mới.
- Không tự mở rộng phương pháp học sâu, tự vận hành hệ benchmark ngoài hoặc bộ dữ liệu khác ngoài quyết định ở bộ RCA.
- Chất lượng thẩm mỹ/UX không thành mục tiêu nghiên cứu riêng; giao diện kết quả DT18-NV3 vẫn phải có.

## 4. Giới hạn hạ tầng, dữ liệu, thời gian

| Loại | Nội dung |
|---|---|
| Hạ tầng sản phẩm | 2 máy EC2 `m7i-flex.large` — 2 vCPU / 8 GiB **mỗi máy**, hai tài khoản AWS rời. `B11-C-v0.3` đã chốt bố trí mục tiêu: máy 1 là transaction plane; máy 2 là control/diagnosis plane (`GOV-090`). Ngân sách mục tiêu khoảng **6 GiB cho tiến trình/container trên mỗi máy**, công bố heap/memory limit; thử tải thật ở Giai đoạn 5–6 (`GOV-097`). Không làm HA cho phạm vi đồ án/demo (`GOV-096`). Nếu thiếu tài nguyên trong lượt chạy hữu hạn, nhóm nâng instance AWS tạm thời thay vì mở lại `PA-6` (`GOV-098`) |
| Máy chạy thực nghiệm | i5-1240P 12 nhân/16 luồng, 15,7 GB RAM, ổ D còn 73,3 GB. Cần thêm distro Ubuntu trên WSL2 và Python 3.12 (hiện 3.9.13) |
| Rủi ro dữ liệu nghiên cứu — **không phải ngân sách hai EC2 của sản phẩm** | `RE2` là bộ dữ liệu chính để thử baseline/MyRCA; mốc hai tuần chỉ là vòng thực nghiệm đầu. `RE2` có log và trace nên nặng hơn `RE1` nhiều. Thư viện yêu cầu dữ liệu ở dạng `pandas.DataFrame`; nạp trace cỡ chục triệu span vào **máy nghiên cứu 15,7 GB RAM ở dòng trên** là rủi ro thật. **Phải đo trước khi cam kết** — `A8-OPEN-05` |
| Trần kiến trúc | ≤ 8 service nghiệp vụ, ≤ 3 luồng Saga; đánh giá tại `B10`/`B11` |
| Thời gian | Hạn nộp 14/12/2026. Mốc hai tuần DH-MOC là lịch sử; lịch mới chưa được chốt |


Thông tin máy nghiên cứu là hồ sơ tại thời điểm ghi, chưa đo lại trong đợt cập nhật ngữ cảnh. Không đọc cấu hình mục tiêu thành bằng chứng AWS đang chạy đúng cấu hình.

## 5. Vấn đề OPEN

| ID | Nội dung | Owner / gate |
|---|---|---|
| A6-OPEN-01 | Review diễn giải phạm vi theo DT18; chưa có bằng chứng giảng viên duyệt bản diễn giải | Minh / giảng viên |
| A6-OPEN-03 | Phạm vi đánh giá lớp giải thích và phương pháp dùng LLM | Minh / bộ RCA |
| A6-OPEN-04 | Ngưỡng và cấu hình đo hệ thống/chẩn đoán sau phép đo thử | Minh, nhóm / B15 và bộ RCA |
| A6-OPEN-06 | Chốt manifest dataset/case dùng cuối cùng; E1 đã kiểm 11 CSV/990 dòng nhưng không thay manifest đầu vào đầy đủ | Người thực nghiệm / trước kết luận cuối |
| A6-OPEN-08 | Giao thức thử/đánh giá RCA trên FlashTicket và đặc tả giao diện kết quả theo nhiệm vụ mới | Minh / B15/B16 và cửa nối RCA |

A6-OPEN-02/05 đã đóng trong lịch sử. A6-OPEN-07 và RES-049 đã chốt ưu tiên dashboard nâng cao; phạm vi hiện hành đọc §2.1, không khôi phục câu coi mọi giám sát là tùy thời gian.

## 6. Phép tự kiểm

- Phân biệt xác nhận nguyên văn nhiệm vụ với diễn giải/thiết kế/độ đo chưa duyệt.
- Không đổi các quyết định service/Saga/schema/hợp đồng đã duyệt chỉ vì đổi đề tài.
- Mobile là phần phụ đúng lời Minh; giao diện phân tích RCA là sản phẩm chính DT18-NV3.
- Không tự đánh dấu build, triển khai hay thực nghiệm đã PASS.

## Nhật ký phiên bản — lịch sử, không dùng làm ngữ cảnh hiện hành

| Phiên bản | Ngày | Thay đổi | Loại |
|---|---|---|---|
| `A6-v0.7` | 2026-09-18 | Đồng bộ tên/nhiệm vụ DT18, phạm vi xây dựng và đánh giá hệ thống, nhóm bốn người và mobile tùy thời gian; bản diễn giải được đưa về REVIEW_READY | Theo PRJ-025–035 và phạm vi GOV-146, chưa duyệt nội dung mới |
| `A6-v0.6` | 2026-09-04 | Đồng bộ `B11-C-v0.3`: thay benchmark chặn ADR bằng ngân sách tài nguyên và phép thử thật ở Giai đoạn 5–6; ghi không HA và quyền nâng instance tạm thời; làm rõ rủi ro nạp `RE2` thuộc máy nghiên cứu/MyRCA, không phải hai EC2 sản phẩm. Ba vòng phạm vi, sáu mục Vòng 1 và các điểm `OPEN` nghiên cứu không đổi; phần cập nhật §4 đã được Lê Văn Minh duyệt lại | Lan truyền và duyệt lại tác động B11-C |
| `A6-v0.5` | 2026-09-04 | Đồng bộ đúng một dòng giới hạn hạ tầng sau lựa chọn `B11-C`: ghi bố trí mục tiêu hai máy và giữ rõ điều kiện benchmark trước khi chấp nhận ADR. Ba vòng phạm vi, sáu mục Vòng 1 và mọi mục `OPEN` không đổi; tài liệu vẫn `DRAFT`, chờ Lê Văn Minh duyệt | Lan truyền tác động B11-C |
| `A6-v0.4` | 2026-08-27 | Nói rõ lớp giải thích của `DH-MT4` được đặc tả ở bộ RCA và chạy trong FlashTicket, sau khi thiết kế trợ lý cũ bị gỡ khỏi bộ hệ thống (`RES-034`); đổi bộ tiêu chí ở mục 4 Vòng 1 sang ba tiêu chí của `A9` §6. **Ba vòng phạm vi và sáu mục Vòng 1 không đổi** | Lan truyền `RES-034` |
| `A6-v0.3` | 2026-08-26 | §2 sửa câu mở đầu: ranh giới hai vòng là **mức kiểm chứng**, không phải *"không phục vụ trực tiếp §1"* — cách nói cũ mâu thuẫn với §1 mục 1; đồng bộ trạng thái `B5`–`B8` sang `REVIEW_READY` theo `RES-032`; thêm một ô tự kiểm | Sửa mâu thuẫn nội tại |
| `A6-v0.2` | 2026-08-25 | Viết lại theo thư nguyên văn. **Bỏ khung "hai khối song song"**; Vòng 1 nay là đúng sáu mục của cô, mỗi mục dẫn về mã `DH-*`; thêm ràng buộc loại trừ bộ chỉ có metric; thêm hai lớp lỗi `RE2`/`RE3`; thêm lộ trình ba mức; đóng `A6-OPEN-02` và `A6-OPEN-05`, mở `A6-OPEN-06` | Sửa sau khi có bằng chứng gốc |
| `A6-v0.1` | 2026-08-24 | Bản đầu, khung hai khối song song, viết khi chưa có thư trong repo | Tạo mới |
