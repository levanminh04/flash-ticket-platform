# PRE-G authorization — 29/09/2026

- Trạng thái: `USER_CONFIRMED` — Lê Văn Minh, trong cuộc trao đổi hiện tại.
- Gate: Task F corrective closure → PRE-G validity/freeze readiness.
- Đây là quyền triển khai PRE-G có giới hạn; **không** cấp quyền chạy Task G hoặc mở final outcomes.

## Nguyên văn xác nhận

> duyệt, triển khai PRE-G

Sau khi agent trình bản đồ tác động cụ thể, Minh xác nhận:

> **Duyệt đúng phạm vi. PRE-G chỉ được kiểm metadata/source-ingestion contract, adapter compatibility không đọc row-level final60, và hoàn thiện/test orchestration 3 seed bằng synthetic/development evidence. Không mở raw final60, labels hoặc outcomes. Nếu chỉ đạt metadata-level qualification thì phải ghi đúng limitation đó, không được gọi là raw final-scope validation.**

## Bản đồ tác động được duyệt

Nguồn quyền là xác nhận trên, không phải một kết luận rằng gate đã PASS. Các đường dẫn dưới đây là root-relative; `P = D:/Project/flash-ticket-platform`, `W = D:/Project/flash-ticket-rca-research`.

P:

- `docs/evidence/project-direction/2026-09-29-rca-pre-g-authorization.md` — receipt nguồn quyền này;
- `docs/research-rca/RESEARCH-DECISIONS.md` — quyết định nguyên tử;
- `docs/research-rca/pre-g-readiness.md` — kết quả, giới hạn và next gate;
- `docs/research-rca/ARTIFACT-MAP.md`, `docs/research-rca/CURRENT-STATE.md` — điều hướng và trạng thái dẫn xuất.

W:

- `scripts/pre_g/source_qualification.py`, `scripts/pre_g/controller_contract.py` — kiểm nguồn/adapter contract và bộ điều phối ba seed ở phía controller;
- `tests/pre_g/test_source_qualification.py`, `tests/pre_g/test_controller_contract.py` — synthetic/development fixtures;
- `results/pre-g/pre-g-run-contract.json`, `results/pre-g/pre-g-readiness.json`, `results/pre-g/pre-g-independent-review.md` — run contract, receipt và independent review.

## Giới hạn bất biến

- Chỉ tra metadata nguồn chính thức ở revision đã khóa; không tải hoặc đọc row-level raw final60, nhãn, kết quả, prediction hay điểm final.
- Giữ byte của canonical TD-v1.3, Task F v1/v2 release và `src/rca/**`, completed Task E source/config/results, FlashTicket application/API/schema/Saga/behavior.
- Không chạy Task G campaign, không sửa phương pháp/cấu hình/evaluator/split, không commit hoặc push theo quyền này.
- Bằng chứng metadata-level chỉ được tuyên bố đúng mức đó; raw final-scope validation và actual-use audit còn `OPEN` cho gate được cấp quyền sau này.
- P `README.md` và W `src/rca/.idea/` là thay đổi ngoài phạm vi đã có từ trước, phải giữ nguyên.

Đăng ký quyết định nguyên tử tại `RCA-064`–`RCA-067` trong `docs/research-rca/RESEARCH-DECISIONS.md`. Kết quả PRE-G chỉ được ghi sau kiểm thử và review thực tế.

## Corrective authorization — 30/09/2026

Lê Văn Minh xác nhận trong cùng cuộc trao đổi:

> đồng ý với đề xuất, sau khi sửa xong, thì rà soát lại task F một lần nữa, cuối cùng chốt câu đã sẵn sàng mở G được chưa ?

Ngữ cảnh trực tiếp của “đề xuất” là hai finding của independent adversarial review: caller có thể đổi synthetic seal thành `QUALIFIED_CORE_RUN` rồi rehash; và `HF_ENDPOINT` có thể hướng API mặc định tới nguồn giả nhưng vẫn nhận provenance chính thức. Minh cho phép sửa hai lỗi PRE-G đã được trình, review lại Task F và đánh giá readiness cho G. Đây không phải lệnh chạy G, mở final60 hay thay scientific method.

Impact map giữ đúng 12 tệp ở mục trên: sửa hai module và hai test PRE-G; đăng ký correction trước requalification trong run contract; cập nhật receipt/review/readiness cùng nguồn quyền, quyết định và hai tệp điều hướng. Không mở rộng sang frozen Task F, Task E, TD-v1.3 hoặc ứng dụng FlashTicket. Các nội dung 29/09 và limitation lịch sử phải được bảo toàn, phân biệt với kết quả sửa ngày 30/09.

`RCA-068`–`RCA-070` ghi xác nhận nguyên tử. Chi tiết cách ràng buộc nguồn phát hành seal và pin API endpoint là lựa chọn triển khai trong đề xuất đã được giao, không là thuật toán do Minh xác nhận. Raw compatibility, durable seal-before-label và quyền campaign G vẫn `OPEN`.
