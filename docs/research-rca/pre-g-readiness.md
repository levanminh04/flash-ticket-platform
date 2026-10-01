# Corrective PRE-G readiness và Task F re-review — 30/09/2026

STATUS: **PASS_METADATA_ONLY_WITH_LIMITATIONS — hai blocker PRE-G đã được sửa và phản biện lại; Task F đã re-review PASS. Sẵn sàng để Minh cấp quyền mở G ở bước entry validation; chưa đủ điều kiện chạy final campaign ngay.** Đây là kết quả kiểm kỹ thuật, không tự phê duyệt gate hay cấp quyền G. Chủ sở hữu quyền/quyết định: Lê Văn Minh.

## Nguồn quyền và phiên bản hiện hành

[U30PG — corrective authorization trong receipt PRE-G](../evidence/project-direction/2026-09-29-rca-pre-g-authorization.md) và [RCA-068–070](RESEARCH-DECISIONS.md) ghi việc Minh đồng ý hai sửa lỗi, yêu cầu review lại Task F và đánh giá readiness G. U29PG/RCA-064–067 giữ firewall metadata/synthetic/development. Impact map vẫn đúng 12 tệp; không sửa frozen release hay phương pháp.

TD-v1.3 SHA256 `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`, Task F v1 manifest `18484bc4bb0c1d19e8f6936f12d8365a8ad69e16fffa2bf6ce5968189f0561e5` và v2 `c46b870bccdae1198292abc5850a83a3fa7e7f702286ceeaa07258074391801d` giữ nguyên. [Task F handoff](task-f-handoff.md) là nguồn release; kết quả re-review mới ở [independent review W](D:/Project/flash-ticket-rca-research/results/pre-g/pre-g-independent-review.md).

W run contract/readiness v2 giữ nguyên byte UTF-8 của contract/receipt ban đầu trong snapshot kèm SHA256. Correction contract được đăng ký lúc `2026-09-30T08:55:22.457138+00:00`, trước lượt requalification của coordinator. Source/test hashes và bằng chứng thực chạy nằm trong [receipt v2](D:/Project/flash-ticket-rca-research/results/pre-g/pre-g-readiness.json).

## Finding và closure — FACT

| Finding mới từ adversarial review BLOCKED | Sửa trong phạm vi được giao | Kiểm độc lập sau sửa |
|---|---|---|
| PG-ADV-01: đổi synthetic scope/qualification rồi rehash vẫn được evaluator nhận | Qualification cần exact canonical seal do đường qualified triplet thực sự phát hành trong interpreter; private closure không có đường đăng ký output caller | Từ chối forged scope, arbitrary digest, private sealer, copied qualification với rank/owner/input/candidate thay đổi, kể cả fixture flag; JSON roundtrip hợp lệ cùng interpreter, fresh interpreter bị từ chối |
| PG-ADV-02: HF_ENDPOINT dẫn default client tới API giả nhưng receipt gọi official | Pin `https://huggingface.co`, `token=False`, kiểm endpoint và công bố origin | Subprocess loopback fake server nhận **0 request**; qualifier lấy official pinned metadata và digest đúng |

Review 29/09 ban đầu PASS đã bị lượt adversarial tiếp theo bác bỏ ở hai boundary trên. Bản cũ phía dưới và trong W được giữ làm lịch sử; kết quả mới đến sau sửa/test/review, không viết lại rằng initial implementation đã đúng.

## Validation thực chạy và Task F re-review — FACT

- PRE-G **25/25 PASS**, Python 3.12 W `.venv`, `-B`; reviewer độc lập `/root/pre_g_fix_review` chạy lại suite và các probes riêng.
- Task F **70/70 PASS**; verifier **44 references PASS**; Task E math/firewall **34/34 PASS** qua reviewer `/root/task_f_rereview`. Cả **12 source hashes v2**, **10 f09 references**, **11 trường scientific v1/v2** khớp; không tracked drift frozen F/E.
- Runner RCD thật trong pinned Python 3.9 chạy synthetic 600-row frame: seeds **420/421/422**, bins **5**, **3/3 SUCCESS**, bản sao input giữ nguyên; seal và same-interpreter serialized copy hợp lệ. F reviewer còn kiểm exact f06, valid-empty và từ chối forged/tampered runner. Không chạy campaign hoặc script ghi lại Task F receipt.
- Official API chỉ đọc metadata projection `case,dataset,repetition,has_logs,has_traces` và object descriptors ở pinned revision. **30 development/60 final/20 final cells/180 descriptors**, **1,309,388,082 byte khai báo**, identity digest `2b51110896bf23f6f2264e85458b814c1cd7871c50e047d814aeda3943b32d94`; không công bố roster/path từng ca.
- Audit quản trị P **PASS**, một cảnh báo cơ học >3 tệp được bao phủ bởi impact map 12 tệp đã duyệt. Không sửa README, `.idea/`, TD, Task E/F frozen source/config/results hoặc app/API/schema/Saga.

## Câu chốt cho G và điều còn OPEN

**Đã sẵn sàng để Minh cấp quyền mở Task G ở bước kiểm tra đầu vào và chuẩn bị campaign. Chưa đủ điều kiện chạy final campaign ngay.** Không có lệnh chạy G trong lượt corrective này.

Trước campaign, phải có quyền Minh cho exact scope/giới hạn, actual-use raw source/adapter admission, cơ chế prediction lưu bền vững với trusted provenance được xác minh **trước mở nhãn**, và independent validity check của actual G controller/firewall. Những mục này chưa thể được chứng minh bởi PRE-G metadata-only và cần thực hiện trong quyền G được cấp sau.

Adapter vẫn `DEVELOPMENT_QUALIFIED__PRE_G_REQUALIFICATION_REQUIRED`. Receipt metadata không được nâng thành raw `source_identity_verified`. Registry seal chỉ có hiệu lực trong cùng interpreter; bản sao ở tiến trình mới bị từ chối. `root_index` là đối số đã được caller truyền: verify trước dùng/chấm root không chứng minh lúc caller mở label. Đây không là durable attestation hoặc sandbox chống mã độc cùng tiến trình.

Case ID có thể hàm chứa service/fault token; LFS descriptors không xác nhận local raw/schema/clock/joins/features/final conversion. Historical E1 exposure và five-distinct-independent-reviewer **OPEN / NOT FACTUALLY CERTIFIED** giữ nguyên. Không tự xác nhận scientific/five-reviewer gate, raw final-scope, final efficacy hay FlashTicket validation. Không mở G/H/I, raw final60, labels/outcomes trong lượt này.

Phép thử độc lập hai bộ: tạo tác thuộc RCA; nguồn hình thành là U29PG/U30PG, RCA-064–070, TD/F release, receipts/tests và official metadata; không có tạo tác thiết kế hệ thống sinh quyết định RCA hay yêu cầu mới cho app. **PASS trong phạm vi tài liệu**, không là gate acceptance của người thật.

Nội dung cho báo cáo: giữ cả hai defect và closure, phân biệt integrity/issuance trong bộ nhớ với durable provenance, nêu metadata-ID/exposure và raw compatibility còn OPEN. Số object/byte là evidence nguồn, không là độ đo hiệu quả mô hình.

---

## Historical initial readiness — 29/09/2026, qualification conclusion superseded

Phần nguyên bản dưới đây là snapshot trước adversarial finding/correction; mọi câu PASS/21 tests được đọc theo phạm vi lịch sử đó. Trạng thái hiện hành và quyền G lấy từ phần corrective phía trên.

# PRE-G readiness — metadata/source contract và controller ba seed

STATUS: **PASS Ở MỨC METADATA VỚI GIỚI HẠN — PRE-G đúng phạm vi đã duyệt hoàn tất; KHÔNG PHẢI raw final-scope validation; Task G CHƯA ĐƯỢC CẤP QUYỀN.** Chủ sở hữu quyết định: Lê Văn Minh. Ngày 29/09/2026.

## Đầu vào và phiên bản

- [U29PG](../evidence/project-direction/2026-09-29-rca-pre-g-authorization.md) và `RCA-064`–`RCA-067`: Minh chỉ cho phép kiểm metadata/source-ingestion contract, adapter compatibility không đọc row-level final60, và hoàn thiện/test orchestration ba seed bằng synthetic/development evidence. Không mở raw final60, labels hoặc outcomes.
- [TD-v1.3 đã khóa](task-d-method-and-experiment-specification.md), SHA256 `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`; [Task F corrective handoff](task-f-handoff.md); W `configs/task-f-td13-frozen-release-v2.json`, SHA256 `c46b870bccdae1198292abc5850a83a3fa7e7f702286ceeaa07258074391801d`.
- W `results/pre-g/pre-g-run-contract.json` được ghi trước qualification; `results/pre-g/pre-g-readiness.json` là biên nhận máy, `results/pre-g/pre-g-independent-review.md` là phản biện độc lập. Code/tests đúng bốn tệp W `scripts/pre_g/` và `tests/pre_g/`; file SHA256 nằm trong biên nhận máy.
- Nguồn chính thức RCAEval `phamquiluan/RCAEval` revision `afeacb11bcc94dadfd1c8f483ee4377b2b8b614e`; metadata `cases.parquet` SHA256 `c49a288920dbba2e8e724679a14636d5c7eb2b45426bba14007ef79a6c0ab1bb`.

## Kết quả đã kiểm — FACT

| Cửa kiểm | Bằng chứng thực hiện | Kết luận có thể dùng |
|---|---|---|
| Source/split ở mức metadata | Chỉ project `case`, `dataset`, `repetition`, `has_logs`, `has_traces` từ metadata đã khóa; đối chiếu registry phát triển đóng băng. Kết quả 30 development, 60 final, 20 cell final và 180 đường dẫn telemetry dự kiến. | Cấu trúc split và locator khớp ở mức metadata. Không có case ID/path nào được công bố trong receipt. |
| Nguồn chính thức | `HfApi.dataset_info` và `get_paths_info` tại exact revision trả đủ 180 LFS descriptor, tổng kích thước nguồn khai báo 1,309,388,082 byte; digest tập identity `2b51110896bf23f6f2264e85458b814c1cd7871c50e047d814aeda3943b32d94`. Không gọi download API. | Định danh đối tượng nguồn được xác nhận **chỉ ở mức metadata**; chưa kiểm byte local hay row raw final. |
| Adapter/core đóng băng | Verifier v2 PASS, 44 path/SHA references; adapter/source/receipt byte identity còn khớp. PRE-G suite 21/21 PASS; Task F regression 70/70 PASS. | Interface/nguồn Task F không drift; adapter vẫn có scope `DEVELOPMENT_QUALIFIED__PRE_G_REQUALIFICATION_REQUIRED`, không được nâng thành final-qualified. |
| Controller RCD ba seed | Test synthetic/development kiểm đúng 420/421/422, bins 5, cùng input copy, runner RCD được cấp phát, owner-first, unknown-key coverage, worst-tie padding, seed lỗi = 0 và trung bình ba **metric từng seed**. Synthetic Python 3.9 với runner được chứng thực trả 3/3 seed `SUCCESS` và seal hợp lệ. | Hợp đồng orchestration đã chạy trên fixture, không phải campaign/metric final60. Fixture tự cấp output bị đánh dấu unqualified; evaluator mặc định từ chối. |

[Phản biện độc lập PRE-G](D:/Project/flash-ticket-rca-research/results/pre-g/pre-g-independent-review.md) đã tự chạy lại 21/21 test, truy vấn official metadata và đối chiếu hash; verdict **PASS ở mức metadata với giới hạn**, không chứng nhận raw final-scope hoặc cấp quyền G. Audit quản trị P đạt `PASS` với một cảnh báo cơ học về số tệp thay đổi; [U29PG](../evidence/project-direction/2026-09-29-rca-pre-g-authorization.md) là impact map đã được duyệt cho đúng phạm vi đó.

## Ranh giới kết luận và điều còn OPEN

1. **Không gọi đây là raw final-scope validation.** Không tải/mở Parquet telemetry final60, không đọc root-fault/answer file, explicit label/outcome column hoặc kết quả final, không tạo final prediction/score. Metadata `case` ID theo quy ước RCAEval có thể hàm chứa token dịch vụ/loại lỗi; PRE-G xử lý nó như locator opaque trong controller để kiểm split/identity, không giải mã để chọn mô hình hoặc đưa vào worker/packet. Vì vậy cũng **không** tuyên bố lớp identity hoàn toàn không hàm chứa thông tin nhãn suy diễn.
2. LFS descriptors không chứng minh local raw bytes, schema, clock, missingness, window, joins hay final adapter conversion. Task F adapter hiện chứng thực trên development; actual final raw compatibility/source audit vẫn `OPEN`. Một `PASS_METADATA_ONLY` không được chuyển thành `source_identity_verified` cho raw final.
3. PRE-G chứng minh thứ tự seal→evaluator trong bộ nhớ/fixture. Task G cần một biên nhận seal được lưu bền vững và xác minh **trước khi** evaluator mở nhãn; đây chưa phải bằng chứng actual-use final. Hash của seal phát hiện thay đổi, không tự chứng minh provenance mật mã chống mã độc trong cùng tiến trình.
4. Trong đợt lựa chọn E/đóng băng TD-v1.3 và lượt PRE-G này, không dùng final60 outcomes để tune. Tuy vậy, baseline E1 lịch sử đã xem outcome của toàn bộ 90 ca; vì thế final60 không phải benchmark hoàn toàn chưa từng thấy. Mọi giới hạn/phép đo của TD-v1.3 và tình trạng five-distinct-independent-reviewer `OPEN / NOT FACTUALLY CERTIFIED` giữ nguyên.
5. Task G/final raw/labels/outcomes cần quyền riêng từ Minh và gate actual-use hợp lệ trước run. PRE-G không tự duyệt khoa học, không sửa method/config/split/evaluator hoặc mở G/H/I.

## Phép thử độc lập hai bộ tài liệu (§2.2 `docs/project/lien-ket-rca.md`)

1. Tạo tác này thuộc **bộ nghiên cứu RCA**.
2. Nguồn hình thành: U29PG/RCA-064–067, TD-v1.3, Task F v2/handoff, W frozen development registry, W PRE-G receipts/tests và official RCAEval metadata ở revision đã khóa. Hiến pháp dự án chỉ là quyền quản trị chung.
3. Không dùng một tạo tác thiết kế của **bộ hệ thống** làm nguồn sinh kết luận PRE-G. Không nhập yêu cầu từ R0 qua chiều nghiên cứu→hệ thống.
4. Vì không có nguồn bộ kia làm đầu vào thiết kế, không có kịch bản/yêu cầu/bất biến/ranh giới hệ thống nào được tạo hoặc sửa; phép thử độc lập **PASS trong phạm vi tài liệu PRE-G**, không phải phê duyệt kiến trúc hệ thống.

## Handoff và nội dung cho báo cáo

Minh có thể xem PRE-G là **hoàn tất đúng phạm vi metadata + synthetic/development**. Nếu muốn Task G, cần một ủy quyền mới nêu rõ lúc nào được mở raw final và nhãn, cách kiểm actual-use source/adapter, cách lưu/niêm phong prediction trước nhãn và quyền dừng khi contract sai. Không dùng kết quả PRE-G làm số đo hiệu quả RCA.

Báo cáo tốt nghiệp về sau nên nêu provenance pinned RCAEval, phép chia 30/60 theo cell ba lần lặp, ranh giới data leakage/metadata-ID, cơ chế ba seed và failure denominator, cùng giới hạn E1 exposure và raw final-scope chưa kiểm tại PRE-G. Không đưa số 1,309,388,082 byte hoặc 180 đối tượng vào bảng hiệu quả mô hình; đó chỉ là source metadata.
