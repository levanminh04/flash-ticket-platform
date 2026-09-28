# Development preflight hoàn tất — 28/09/2026

**PASS WITH LIMITATIONS — scoped Task E development theo TD-v1.3 đã hoàn tất.** C1/C5 development30,9 C1 OFAT,7 C5 OFAT×30,40 event settings, integrated8×30 và RCD30×3seed×3bins đều có kết quả; numerical, leakage/evaluator và scientific reviews đều CLOSED. Scientific closure được lưu trước coordinator verdict. Không còn CRITICAL/MAJOR chưa xử lý trong gói development.

[Báo cáo A–J và16câu falsification](D:/Project/flash-ticket-rca-research/results/task-e/development-preflight-report.md) · [Coordinator adjudication](D:/Project/flash-ticket-rca-research/results/task-e/development-preflight-adjudication.json) · [Checkpoint bàn giao](D:/Project/flash-ticket-rca-research/results/task-e/continuation-live-handoff.md).

C1 MRR L=.744206, O=.702405, R=.678235: **chưa chứng minh graph hơn local**. C5 G-MTL F1=.669994,L=.072555,ALL=.690865; lợi thế G−L gần mất khi bin10/lag3, scale1e−12 chi phối loss; không quy chênh lệch riêng cho graph. Integrated G-MTL plannedMRR=.470935,26/30ca có firstpost diagnosis. RCD primaryMRR=.253367, đủ270/270config SUCCESS,0executionfailures. Giữ mọi negative/failed/interrupted attempts; lần cuối tái dùng264COMPLETE, chỉ chạy bù6raw-empty chunks đã bảo toàn.

TD13 SHA256 `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971` giữ nguyên suốt fullrun; method vẫn CANDIDATE, không human-approved/frozen. Formal five-reviewer assurance/human acceptance, final60/F/G/H/I và kiểm chứng FlashTicket còn ngoài scope. Không commit/push; không sửa app/API/schema/Saga; README ngoài scope giữ nguyên. Không mở lại reviewer hoặc chạy lại COMPLETE trừ hash/input/config/TD drift hay finding mới thực chất.

**Các checkpoint bên dưới là lịch sử trước khi hoàn tất; đọc trạng thái hiện hành ở đầu tệp này.**

# Live D/E continuation — 27/09/2026

TD-v1.3 SHA256 `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`; technical CANDIDATE for U27R-authorized development. Targeted amendment review/fixtures passed; automatic E resume is active. Latest recovery:89 development objects verified, all30 loader audits complete, all30 topology cases/180jobs/46,080graphs complete and independently rechecked without regeneration (e27-026). RCD runtime018 and worker024 qualified; C1 cascade027 16testsPASS, C5controller028 15testsPASS. C1smoke031 completed10/10 predetermined cases; C5smoke032 in progress. No quality/winner results yet at this checkpoint. Use [live recovery handoff](D:/Project/flash-ticket-rca-research/results/task-e/continuation-live-handoff.md) and [checkpoint03](D:/Project/flash-ticket-rca-research/results/task-e/continuation-recovery-checkpoint-03-smoke-start.json) for exact current receipts and next actions. Full30 outcomes, mandatory sensitivity and final independent scientific review still required; formal five-reviewer/final freeze/F–I remain unopened. Agent status alone never proves completion.

Historical checkpoint below; do not treat old RETURN TO D/no-execution statements as the live authorized state.

# Task E — Development preflight handoff, 27/09/2026

Owner: Minh. Intent EXECUTE theo [U27](../evidence/project-direction/2026-09-27-rca-development-preflight.md) và [RCA-043–048](RESEARCH-DECISIONS.md). **Kết luận của coordinator: RETURN TO TASK D.** Đây là checkpoint có bằng chứng, không phải Task E PASS, phê duyệt phương pháp hoặc kết luận graph thất bại.

## 1. Quyền, phiên bản và điểm dừng

Minh đã cho phép development giới hạn theo TD-v1.2 trước khi đủ năm agent assurance, giữ phản biện cho đánh giá chính thức, cấm final evaluation/tự đổi phạm vi và duyệt [impact map §4–5](D:/Project/flash-ticket-rca-research/results/task-e/preflight-plan.md). Không cần xin lại quyền cho cùng gói. Tuy nhiên U27 §13 yêu cầu dừng khi protocol chưa thể triển khai duy nhất; không dùng quyền chạy để tự chọn một diễn giải C5.

| Nội dung | State / bằng chứng |
|---|---|
| Scoped development authorization | USER_CONFIRMED — RCA-043–048; không đổi công thức thành DECIDED |
| P branch/HEAD | FACT — `codex/rca-research-program` / `3d7ec9d824d12c98dc233705ef50908b62adf235` |
| W branch/HEAD | FACT — `main` / `f49859df7664758f1143a1033535da7f6f29d7d6` |
| TD baseline giữ nguyên | FACT — SHA256 `985f1c5fc983422272dfbde8b69ed5ff98ee4fdd1631af075d03788c3770ad54` |
| Hai quy tắc C5 | OPEN — phương pháp vẫn CANDIDATE, owner Minh + người sửa/review D |
| Hiệu quả RCA/demo | OPEN — chưa có actual development predictions hoặc F1/MRR để đánh giá |

P=`D:/Project/flash-ticket-platform`; W=`D:/Project/flash-ticket-rca-research`. Thay đổi lượt này ở local, chưa commit/push. README P đã dirty từ trước được giữ nguyên. D historical handoff/receipts không bị viết lại; quyền mới và trạng thái hiện hành lấy từ U27 và [CURRENT](CURRENT-STATE.md).

## 2. Bằng chứng đã thực hiện

Đọc [adjudication đầy đủ](D:/Project/flash-ticket-rca-research/results/task-e/e27-001-synthetic/adjudication.md) trước khi tiếp tục. Tài liệu này trả lời đủ14 câu falsification của mission và dẫn toàn bộ attempts, cả lần lỗi.

| Phần việc | Kết quả và giới hạn |
|---|---|
| Snapshot trước chạy | Sáu attempts đều có run contract, exact source snapshot, TD/Git/data pin, packages/hardware/config/input scope và logs |
| Math/evaluator hiện tại | [E27-005](D:/Project/flash-ticket-rca-research/results/task-e/e27-005-math-closure/fixture-report.json): **21/21 PASS**, bounded synthetic |
| Input-boundary hiện tại | [E27-006](D:/Project/flash-ticket-rca-research/results/task-e/e27-006-boundary-closure/leakage-audit.json): **13/13 PASS**, synthetic DataFrames/mocked I/O; không full firewall |
| Lần lỗi được giữ | [E27-004](D:/Project/flash-ticket-rca-research/results/task-e/e27-004-math-review/fixture-report.json):21 tests,1 failure do test đòi nhiều đồ thị cuối khác nhau từ4chains; sửa bằng expected adjacency phân tích trước, không đổi TD |
| Wave1 dữ liệu thật | [Loader audit](D:/Project/flash-ticket-rca-research/results/task-e/e27-001-synthetic/loader-audit.json): đọc hai development cases sẵn có, reverify5hashes; không full30 qualification |
| Comparator | [Source pins](D:/Project/flash-ticket-rca-research/baselines/task-e-source-manifest.json),20originalfiles+1patchedcopy; BARO bounded fixtures; **RCD chỉ interface stubs, chưa qualified runtime** |
| Review | [Review receipt](D:/Project/flash-ticket-rca-research/results/task-e/e27-001-synthetic/review.json): actual identities/roles, defects/closure, không blind; không tự chứng nhận đủ formal assurance |

Chưa chạy complete C5 detector/event fixtures, all256 metric aggregation/bootstrap/fold selection, full runtime worker/cache firewall, actual30 loader audit, smoke, model registry hoặc mandatory empirical sensitivity. Không có per-case predictions/sensitivity table giả. Không tải telemetry mới hoặc cài/nâng package.

## 3. Hai điểm cần quay lại D

**D-E27-01 — membership của láng giềng (độ tin cậy cao).** [TD §7](task-d-method-and-experiment-specification.md:158) khóa “same-type applicable degree” nhưng chưa định nghĩa điều kiện vào tập đó. Ví dụ đã thực chạy: hai kênh có24/12bin hữu hạn,23/0cặp lag hợp lệ. Nếu lấy láng giềng có scaler từ prefix, context là mean10/coverage0.5/degree2; nếu đòi nó đủ18rows để fit model riêng, context thành0/0/1. Cùng dữ liệu/graph nhưng đầu vào G/ALL khác nhau. Không fit detector hoặc chọn diễn giải thắng.

Cần D định nghĩa riêng membership/scaler từ prefix, eligibility của own-target model và availability từng bin; khóa denominator nhất quán cho G/ALL và MT/MTL. Không dùng future data, prediction finiteness hoặc outcome để ngầm chọn policy.

**D-E27-02 — phạm vi xử lý trace xung đột (độ tin cậy vừa–cao).** [TD §7](task-d-method-and-experiment-specification.md:150) nói vô hiệu bin khi gặp xung đột nhưng chưa nói mask áp cho service/channel nào, ảnh hưởng lên graph-fit và state sau đó. C1/C5 có policy khác nhau không tự là mâu thuẫn. Bằng chứng ở đây là đọc đặc tả và minh họa tĩnh; chưa có hai loader-policy được chạy đối chiếu hoặc tần suất lỗi thật. Cần chốt chronology/mask/graph/persistence, không để future conflict rút lại quyết định quá khứ.

Hai mục vẫn OPEN; **chưa sửa TD hoặc chọn policy**. Chúng không chứng minh C1 hỏng. Việc giữ toàn campaign tại checkpoint thực hiện đúng stop rule U27, không phải đề xuất thiết kế lại toàn bộ RCA.

## 4. Lỗi triển khai đã sửa và điều quan sát được

[Failure ledger](D:/Project/flash-ticket-rca-research/results/task-e/e27-001-synthetic/failures.jsonl) tách rõ lỗi code, lỗi test, thiếu đầu vào và thiếu định nghĩa D. Lỗi làm tròn NumPy biến điểm rất lớn thành nonfinite/tie giả đã sửa và có regression. Sửa loader để xác minh path/hash trước đọc, giữ bad-log support khỏi veto C1 M/T và không để fractional clock hữu hạn nằm rõ ngoài cửa sổ ảnh hưởng bundle bên trong. Clock không xác định vẫn lỗi rõ; C5 vẫn UNQUALIFIED. Reviewer riêng đã kiểm phần sửa và receipts.

Hai ca auth/cpu hiện có đều cho reference graph27dịch vụ/55cạnh. Cpu2 có271,919dòng log,16message null và1,052dòng trùng vẫn được đếm;20/27dịch vụ có log trong120s đầu. Đây là bằng chứng sample có cấu trúc quan sát, không phải bằng chứng graph hữu ích hoặc prevalence toàn tập. Vẫn thiếu84/89telemetryobjects cho development30; đây là acquisition dependency, chưa phải incompatibility.

Các giả định còn giữ để kiểm tiếp: archive được replay theo event time, arrival truth chưa được chứng nhận; same-suffix không tự bảo đảm cùng semantics; API/process isolation phục vụ scientific dataflow, không là OS sandbox chống mã độc. Prior E1 exposure vẫn phải khai, không gọi dữ liệu là untouched.

## 5. Files và bước tiếp theo

[Inventory đầy đủ](D:/Project/flash-ticket-rca-research/results/task-e/e27-001-synthetic/file-inventory.json) ghi paths/hash/purpose. P có6file thuộc gói: nguồn U27 mới; append phân sổ RCA và đăng ký chung; CURRENT; ARTIFACT-MAP; handoff này. W có6module preflight,2test files,registry,environment lock,source snapshots/patch,plan,gitignore và receipts của6attempts. `detection.py` và `run_preflight.py` chưa tạo vì C5 chưa rõ; không có full campaign runner.

**Next:** sửa hẹp D theo gate của D, review và ghi phiên bản mới; sau đó tiếp tục E trong quyền đã có. Hoàn tất các fixtures/firewall/RCD dependencies và audit toàn89objects trước model; kiểm R mobility trên reference graphs; smoke theo rule đã ghi trước outcome, rồi đủ30development+OFAT nếu gates đạt. Không chọn primary mới bằng sensitivity hoặc tự bỏ ca thất bại. Không mở F/G/H/I hay final freeze.

Năm-agent assurance cho đánh giá chính thức còn OPEN. Hai reviewer mới đọc context và verdict đề xuất nên không là blind reproduction; một người sau đó sửa C1loader, người còn lại review delta đó. Không dùng số identity cộng dồn để tuyên bố đã đạt một gate khác.

Đưa vào luận văn sau này: giới hạn của synthetic evidence, failure attribution, reproducibility và các định nghĩa C5 được chốt sau review. Chưa có kết quả hiệu quả mới. [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) vẫn yêu cầu xây dựng và đánh giá hệ thống bán vé phân tán, đồng thời ứng dụng đồ thị cho giám sát/chẩn đoán; không giảm hệ thống thành bối cảnh demo.
