# Trạng thái triển khai plan

- **Cập nhật:** 2026-08-22 (Lê Văn Minh duyệt toàn bộ B2–B8 sau vòng kiểm toán; cổng B9 đã mở)
- **Giai đoạn hiện tại:** Giai đoạn 2 **đã hoàn tất phần phân tích miền** — cả bảy tạo tác `B2-v0.10`, `B3-v0.10`, `B4-v0.14`, `B5-v0.12`, `B6-v0.12`, `B7-v0.7`, `B8-v0.8` đều `APPROVED` ngày 2026-08-22, duyệt đúng thứ tự chuỗi (`GOV-023`). **Có thể bắt đầu B9** tại `docs/quality-scenarios/B9-quality-scenarios.md`

## Giai đoạn 0

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| Repository ĐATN riêng | Hoàn thành | `flash-ticket-platform` là không gian hiện thực và đánh giá chính thức |
| Không đưa `.env`/bí mật vào repo mới | Hoàn thành bước đầu | `.gitignore`, `.env.example`; tiếp tục quét trước mỗi lần nhập mã |
| Bộ plan A/B/C, tài liệu chủ và B5.5 | Đã hòa giải phương pháp | Giai đoạn 2 chỉ tạo mô hình miền ứng viên; B11-A/B/C mới hình thành, kiểm tra khả thi và chốt kiến trúc |
| Nơi ghi quyết định và mẫu ADR | Hoàn thành | `docs/adr/`, ADR-000 |
| Quy ước ký hiệu/đặt tên | Hoàn thành | Tầng C mục 3.1–3.3 |
| Bảng phân vai | Hoàn thành baseline | Ba thành viên đã xác nhận phân công ban đầu; phạm vi backend cụ thể Tuyến hỗ trợ sẽ chốt khi chia workload |
| Chiến lược tái sử dụng frontend | `USER_CONFIRMED` | Dùng frontend hiện tại làm nền nhưng điều chỉnh theo mô hình đích; không cho frontend ràng buộc phương án tách service ở B11-A; kiểm tra khả năng tái sử dụng tại B11-B/Giai đoạn 5 (`PRJ-004`, `PRJ-005`) |
| Khung báo cáo không khóa số chương | Hoàn thành bước đầu | `docs/report/report-outline.md`; vòng đời ba bản Word được phân loại tại `docs/report/README.md` |
| Xin đề cương và file mẫu ĐATN hiện hành | Chưa gửi | Nội dung soạn sẵn tại `docs/coordination/questions-for-advisor.md` |

## Điều kiện chuyển sang Giai đoạn 1

Có thể bắt đầu các công việc khảo sát/phân tích không phụ thuộc mẫu trình bày ngay. Cổng Giai đoạn 0 chỉ hoàn tất đầy đủ sau khi:

1. Nhóm gửi câu hỏi về đề cương/file mẫu cho cô Liên.
2. **Đạt:** Ba thành viên đã xác nhận bảng phân vai ban đầu.

## Giai đoạn 1

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B1 — Khảo sát luồng công khai | `APPROVED` baseline | `evidence/external-survey/B1-public-ticketing-survey-2026-08-09.md`; đủ dùng cho B2, ảnh công khai là bổ sung không chặn |
| B1 — Quy trình chẩn đoán hiện tại | `APPROVED` baseline, bằng chứng còn giới hạn | Quy trình và INC-01 đã được xác nhận; cần thêm tối thiểu 2 ca/tái hiện hoặc công bố giới hạn một ca và không đo thời gian cải thiện |
| A1 — Bối cảnh và tính cấp thiết | `APPROVED` baseline | Nhấn mạnh hậu quả nghiệp vụ trước, khó khăn phân tán sau; tên “trợ lý chẩn đoán sự cố” đã chốt; chuẩn trích dẫn chờ mẫu khoa |
| A2 — Phát biểu vấn đề | `APPROVED` baseline | Chủ đồ án đã duyệt câu chữ; tái kiểm tra sau B10 và khi B11-C có tác động |
| A4 — Câu hỏi nghiên cứu | `APPROVED` phạm vi baseline | Giữ một câu trung tâm và hai câu phụ; ngưỡng chờ B9–B10, cách trình bày chờ mẫu/giảng viên |

## Điều kiện hoàn tất Giai đoạn 1

1. **Đạt:** Chủ đồ án đã duyệt A1, A2 và phạm vi A4 ở mức baseline; sau B10 phải tái kiểm tra trước khi chốt A1–A6.
2. **Đạt:** Khảo sát B1 công khai được xác nhận đủ dùng; ảnh có thể bổ sung sau.
3. **Chưa đạt:** Baseline chẩn đoán cần thêm ít nhất 2 ca thật/tái hiện được; nếu không có, chủ đồ án phải xác nhận giới hạn một ca và không đo thời gian cải thiện.
4. **Đạt:** Phát biểu vấn đề không phụ thuộc tên công nghệ.

## Quyết định đang mở, chưa được tự khóa

- Danh sách service cụ thể và vị trí aggregate tồn kho vé.
- Tối đa ba luồng nào thực sự cần Saga.
- Cách bố trí các thành phần trên hai EC2.
- Workflow cuối của trợ lý chẩn đoán và tập ca đánh giá.
- Cỡ tập ca chẩn đoán: bổ sung tối thiểu 2 ca hay chấp nhận giới hạn một ca và bỏ kết luận về thời gian.
- Phần backend cụ thể Tuyến sẽ hỗ trợ sau khi nhóm chia workload.
- Cách đồng bộ/kho lưu hồ sơ danh tính nghiệp vụ và nghĩa kỹ thuật của nơi tạo admin đầu tiên; vòng đời và tập trường nghiệp vụ organizer đã được chốt.
- Tên/chuyển trạng thái và cách biểu diễn trạng thái đơn (`BIZ-130`) tại B12/B13; nghĩa hoàn khoản thu trùng/đến muộn đã được khóa. Cách tính số người theo dõi chờ B12/B13.
- Quyền sở hữu dữ liệu vật lý của tỷ lệ phí nền tảng (`BIZ-123`); nghĩa nghiệp vụ cố định sau phê duyệt đã được chốt.
- Trường/payload/thời gian lưu dấu vết và tập trường nhạy cảm cần lọc (`B8-OPEN-05`) tại B13/B16; nghĩa nghiệp vụ và yêu cầu khử/che đã được chốt.

## Giai đoạn 2

| Đầu ra | Trạng thái | Bằng chứng/việc còn lại |
|---|---|---|
| B2 — Từ điển miền | `APPROVED` (`B2-v0.10`) | Duyệt ngày 2026-08-22. `v0.10` **chỉ bổ sung** mục từ `Yêu cầu hủy sự kiện` mà vòng kiểm toán phát hiện thiếu; không đổi nghĩa mục từ nào đang dùng |
| B3 — Quy trình nghiệp vụ | `APPROVED` (`B3-v0.10`) | Duyệt ngày 2026-08-22 sau B2. `v0.10` không đổi nội dung, chỉ đồng bộ khai đầu vào |
| B4 — Bản đồ sự kiện miền | `APPROVED` (`B4-v0.14`) | Duyệt ngày 2026-08-22 sau B3. `v0.14` không thêm/bớt/đổi nghĩa sự kiện nào — `A09`/`A10` vốn đã mô tả yêu cầu hủy |
| B5 — Bản đồ bounded context | `APPROVED` (`B5-v0.12`) | Duyệt ngày 2026-08-22 sau B4. `v0.12` sửa ba tham chiếu phiên bản B4 còn sót và ghi rõ tình trạng mã `B5-OPEN-09` bị mất phát biểu. Tám context, phân loại và bản đồ quan hệ không đổi |
| B6 — Use case và đặc tả | `APPROVED` (`B6-v0.12`) | Duyệt ngày 2026-08-22 sau chuỗi B2–B5. 30 use case, 12 ca đặc tả đầy đủ và bốn sơ đồ; vòng kiểm toán sửa số ca liệt kê thành 18 và ba tham chiếu `B4-v0.11` còn sót; `v0.12` chỉ đồng bộ khai đầu vào |
| B7 — Aggregate và bất biến | `APPROVED` (`B7-v0.7`) | Duyệt ngày 2026-08-22 sau chuỗi B2–B5. Vòng kiểm toán sửa 17 điểm gồm bốn chỗ trùng tên lệch nghĩa với B2, hai bội số sai và note giới hạn sai số nguyên nhân hoàn tiền. `v0.7` bổ sung aggregate ứng viên `Yêu cầu hủy sự kiện` sau khi B2 có mục từ; 57 lớp trên tám sơ đồ |
| B8 — Bảng FR/NFR | `APPROVED` (`B8-v0.8`) | Duyệt ngày 2026-08-22 sau B6. Đã đóng bốn phiếu nghiệp vụ, khôi phục NFR-09 và làm rõ thử tải có kiểm soát; vòng kiểm toán sửa số ca §2.7 và bổ sung dẫn `BIZ-152`; không được `APPROVED` trước chuỗi B2–B5 và B6-v0.12 |
| A3 — Mục tiêu nghiên cứu | `DRAFT` (`A3-v0.3`) | Lê Văn Minh xác nhận cả năm mục tiêu định tính; ngưỡng MT-3/MT-4/MT-5 cố ý chờ B9/B10 nên không chặn việc bắt đầu B9 sau khi B8 được duyệt |

## Cổng và giới hạn Giai đoạn 2

- Chuỗi phụ thuộc là `B2 baseline → B3 → B4 → B5 → B7`; cho phép tạo nháp phía sau nhưng không được `APPROVED` khi đầu vào bắt buộc chưa được duyệt.
- AI không tự duyệt tài liệu do chính nó tạo; mỗi tạo tác ghi trạng thái, người duyệt, ngày và phiên bản đầu vào.
- Giai đoạn 2 không tạo service vật lý, Saga, schema đích hoặc ADR kiến trúc và không đọc B5.5 để hình thành phương án.
- Hai trần `≤ 8 service nghiệp vụ` và `≤ 3 Saga` là ràng buộc đánh giá tại B10/B11, không phải số lượng phải dùng hết.
- Ngày 2026-08-21, sau khi B2–B5 được duyệt, Lê Văn Minh chốt bỏ vai trò `SUPER_ADMIN` khỏi phạm vi đồ án (`BIZ-146`, thay thế `BIZ-133`). Vai trò này không có nhiệm vụ nào được mô tả trong B2–B5 và không truy được về sự kiện miền nào ở B4. Vì vậy B2 lên `B2-v0.9` và B4 lên `B4-v0.9`, cả hai trở lại `REVIEW_READY` và cần Lê Văn Minh duyệt lại theo thứ tự B2 → B4. B3-v0.9 và B5-v0.7 không đổi nội dung nghiệp vụ — B3 không nhắc bộ vai trò và B5 chỉ dùng cụm “bộ role” chung — nhưng auditor cưỡng chế quy tắc “đầu vào chưa `APPROVED` thì hạ nguồn không được `APPROVED`”, nên cả bốn tài liệu B2 → B5 cùng trở lại `REVIEW_READY`. Lê Văn Minh đã duyệt lại toàn chuỗi B2-v0.9 → B3-v0.9 → B4-v0.9 → B5-v0.7 trong cùng ngày 2026-08-21.
- B2-v0.5, B3-v0.5 và B4-v0.5 từng được Lê Văn Minh duyệt tuần tự ngày 2026-08-18. Vòng hiệu đính ngày 2026-08-20 phát hiện các dòng PRJ/BIZ mới đã bị gắn `USER_CONFIRMED` quá mức, B4/B5 không nhất quán và checklist B5 có dấu tick không có bằng chứng. Sau vòng kiểm toán và duyệt nội dung ngày 2026-08-21, chuỗi đã đạt B2-v0.8, B3-v0.8, B4-v0.8 và B5-v0.6 `APPROVED`; vòng bỏ `SUPER_ADMIN` sau đó nâng phiên bản hiện hành lên B2-v0.9, B3-v0.9, B4-v0.9 và B5-v0.7 rồi được duyệt lại đúng thứ tự. Tám context không được thêm, bớt, đổi tên hay đổi phân loại; các `OPEN` còn lại có owner/gate và không chặn B7. Giai đoạn 0–1 vẫn còn việc xin mẫu báo cáo và xử lý phạm vi tập ca chẩn đoán nhưng không chặn phân tích Giai đoạn 2.
- Lê Văn Minh đã xác nhận trực tiếp chuỗi `B4-v0.11 → B5-v0.9`, gồm `E01`–`E05`; các bản hiệu đính tạm thời chỉ dựa vào commit cũ không còn là kết luận hiện hành (`GOV-021`).
- Ngày 2026-08-22, B4-v0.13/B5-v0.11 lan truyền các lựa chọn đã xác nhận: chuyển trạng thái vé đơn điệu hấp thụ kích hoạt lặp, hệ thống không kiểm sức chứa vật lý, hồ sơ organizer tối giản, danh sách audit đóng và giữ khử/che dữ liệu nhạy cảm. Không lựa chọn service, schema, API, Saga hoặc cơ chế phối hợp nào được đưa vào Giai đoạn 2.

## Cổng sang B9 — đã đạt ngày 2026-08-22

1. **Đạt:** chuỗi `B2-v0.10 → B3-v0.10 → B4-v0.14 → B5-v0.12` được duyệt lại đúng thứ tự.
2. **Đạt:** `B6-v0.12` `APPROVED`.
3. **Đạt:** `B7-v0.7` `APPROVED`, khóa aggregate/bất biến dùng cho kịch bản đồng thời và tải.
4. **Đạt:** `B8-v0.8` `APPROVED` sau B6.
5. **B9 mở.** Đường dẫn canonical là `docs/quality-scenarios/B9-quality-scenarios.md`; đầu vào là B7 (bất biến/aggregate), B8 (FR/NFR kèm cách đo) và A3 `MT-1`–`MT-5`.

Việc phải làm ngay khi vào B9, đã biết trước từ vòng kiểm toán:

- `NFR-05` và `NFR-07` đo trên tập ca lỗi có nguyên nhân biết trước, mà baseline chẩn đoán vẫn **“Chưa đạt”** ở Giai đoạn 1: cần thêm tối thiểu 2 ca thật/tái hiện được, hoặc Lê Văn Minh xác nhận chấp nhận giới hạn một ca và bỏ kết luận về thời gian cải thiện.
- Hai phép thử tranh chấp mà B7 §4.4 yêu cầu tách riêng: nhiều thiết bị quét **cùng một vé** (`INV-09`), và nhiều thiết bị quét **các vé khác nhau của cùng một đơn** để đo tranh chấp trên root `Phát hành vé`.
- `GOV-024` còn `OPEN` nhưng không chặn B9. `A3-v0.3` đã đủ mục tiêu định tính; `A3-OPEN-01` và `B8-OPEN-01` là chính phần ngưỡng B9/B10 phải làm, không phải blocker cần đóng trước B9.

### Kiểm toán bằng chứng đầu vào B9 — 2026-08-22

| Điều kiện | Kết quả kiểm độc lập trên trạng thái hiện tại |
|---|---|
| Độ phủ use case → yêu cầu | Đủ 30/30 use case B6 xuất hiện trong B8; có đúng 12 ca đặc tả đầy đủ, còn 18 ca chỉ liệt kê |
| Độ phủ bất biến | Đủ 11/11 bất biến B4 trong bảng truy vết B7; không thiếu hoặc đổi mã. `INV-01` đã được trả về đúng câu chữ B4 tại `B7-v0.6` |
| Khả năng đo của NFR | Có 11 NFR duy nhất; 11/11 có nội dung, cách đo và nguồn gốc |
| Dẫn quyết định | 125/125 mã `BIZ`/`PRJ`/`GOV`/`RES` được dẫn trong B4–B8/A3 đều tồn tại trong sổ quyết định |
| Định tuyến điểm mở | Đủ 7/7 điểm `OPEN` hiện hành của B4 và 7/7 điểm `OPEN` hiện hành của B5 xuất hiện trong B7 với gate tiếp theo. `B5-OPEN-09` chỉ xuất hiện ở nhật ký phiên bản B5 mà không có ở §8.1 lẫn §8.2 — cần Lê Văn Minh xác nhận trước khi coi con số 7 là đầy đủ |
| Sơ đồ | 4/4 nguồn B6 và 8/8 nguồn B7 parse/render thành công. B7 đo lại bằng PlantUML 1.2026.6 sau khi sửa: tỷ lệ 0,74 · 1,32 · 0,55 · 1,50 · 1,02 · 1,67 · 1,63 · 1,84 |
| Từ vựng | 57/57 tên lớp B7 là mục từ nguyên văn của B2-v0.10. Phép kiểm **nghĩa** được tách riêng tại B7 §7 và đã sửa bốn chỗ trùng tên lệch nghĩa |
| Đầu vào B9 | A3-v0.3 tồn tại; đường dẫn canonical của B9 là `docs/quality-scenarios/B9-quality-scenarios.md`; phần ngưỡng còn thiếu chính là đầu ra B9/B10 phải tạo |

Kết luận kiểm toán ngày 2026-08-22, sau vòng đọc toàn văn: **một lỗi thượng nguồn đã được leo thang và khắc phục**. `Yêu cầu hủy sự kiện` là sự kiện miền đã duyệt ở B4 `A09`/`A10` với trạng thái `REJECTED` riêng theo `BIZ-098` và yêu cầu dấu vết theo `FR-68`, nhưng thiếu mục từ ở B2-v0.9 nên B7 không có ngôn ngữ để đặt ranh giới. Lê Văn Minh chọn bổ sung mục từ vào B2; `B2-v0.10` khắc phục, `B7-v0.7` thêm aggregate ứng viên tương ứng, và cả chuỗi trở lại `REVIEW_READY` để duyệt lại. Ngoài nó, không còn thiếu tạo tác hoặc mâu thuẫn nội dung chặn B9. Các ô phê duyệt của người thật là cổng còn lại; chúng không được AI tự đánh dấu thay.

Hai điểm ở B5 cũng đã được Lê Văn Minh cho phép sửa và nằm trong `B5-v0.12`: ba tham chiếu `B4-v0.11`/`B4-v0.9` còn sót, và tình trạng của mã `B5-OPEN-09`. Tra toàn bộ lịch sử repo cho thấy phát biểu của `B5-OPEN-09` chưa từng vào commit nào, nên nó được ghi là **mã đã cấp nhưng mất phát biểu** thay vì bị đoán lại nội dung.

Còn một điểm `OPEN` mới, không chặn B9: `BIZ-097`–`BIZ-099` không nói organizer có được gửi lại yêu cầu hủy sau khi bị từ chối hay không. B7 dùng bội số `0..*` để không tiền-chốt; owner Lê Văn Minh, gate B6/B8.
