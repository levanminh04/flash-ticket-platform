> **Publication checkpoint — 01/10/2026.** RCA-117 ghi yêu cầu của Minh push tiến độ lên P/W. Xuất bản hồ sơ PRE-G/G30–G33 trên nhánh hiện hành; actual final campaign/truth vẫn chưa được cấp quyền. Telemetry/generated cache/host keys giữ local; các audit và HEAD hashes preparation phía dưới là bằng chứng trước publication, không tự là portable replay bundle.

# G33 preparation hoàn tất kiểm kỹ thuật; trình duyệt campaign thật — 01/10/2026

**FACT: PASS_BOUNDED_G33_PREPARATION_TECHNICAL. Đủ bằng chứng chuẩn bị để trình Minh duyệt lượt final; campaign thật vẫn chưa được cấp quyền, G chưa DONE.** Minh đã duyệt G33 preparation tại [RCA-088–091](RESEARCH-DECISIONS.md), đúng map14/cache/key được trình trong checkpoint G32 bên dưới. Kết quả thực kiểm và đề xuất/gate riêng ghi ở RCA-092–116 và hồ sơ W; không tự chuyển quyền preparation thành final prediction hoặc mở root/fault. Câu “G33 preparation chưa duyệt” ở checkpoint G32 là lịch sử đã được RCA-088–091 supersede; những hạn chế khoa học và kết quả G32/G31/G30 vẫn giữ nguyên.

Đánh giá dễ hiểu: **tín hiệu tốt về độ sẵn sàng kỹ thuật**. Các lỗi tích hợp/resource/completeness tìm thấy đã sửa và kiểm lại trong đúng quyền chuẩn bị. Chưa có căn cứ xếp chất lượng chẩn đoán trên dữ liệu thật là tốt, trung bình hoặc xấu, vì chưa chạy final60 và chưa chấm efficacy thật. Kết quả synthetic không phải thắng/thua của phương pháp trên dataset thật.

## Dữ liệu, câu hỏi kiểm và kết quả mới

Danh sách câu hỏi được ghi trước đo tại W `cache/verification-plan-current.json`: (1) full DEV workload/timing có tương ứng source hiện hành; (2) upper input/frame/shard/process resources có dùng được; (3) G33 có giữ toàn nội dung khoa học G32; (4) corruption/completeness/drift có dừng trước truth; (5) fresh verification/pure evaluator có tái lập; (6) baseline6205 tệp/Git/untracked có được bảo toàn. Mỗi lượt giữ đúng data scope; không tính cùng một mẫu60 lần để kiểm danh sách hoặc lưu tệp.

| Dữ liệu | Công việc và số đếm | Kết quả / giới hạn |
|---|---|---|
| Actual telemetry đã admission G30 và conversion G32 | G33 source-only resource accumulation60/60 **ca**; tái dùng180 **tệp** đã pin, không tải corpus mới | Numeric input digests khớp G32;40/40 prefix cũ khớp. Peak2.166.538.240bytes dưới3GiB. Không actual model/prediction/evaluation; không chứng minh RAM của full actual campaign |
| Development30 numeric bundles đã khóa | Một lượt current-source timing30/30 **ca**,180/180 **matched arms** L/O/R của hai rankers | SUCCESS; max full C1 wall42.92569470000308s, common timeout429.2569470000308s theo TD `max(300,10×max)`. Independent30 inputs/checkpoints/7950 unique shards và15360 R draw outputs được kiểm. Không nhãn mới/efficacy |
| Một upper-shape synthetic capacity fixture khác SYN3 | N27/K12, reference/query27×12×30; C5 warmup36/stream252;256 controls/tám detectors | C1/C5 transport/math/resource PASS;2290 frames và2296 unique shards.40 shared references là helper, không40 integrated models. RCD600×184 chỉ kiểm wire encoding, chưa chạy model184 columns |
| Genuine synthetic ba archive khác nhau, candidate6/5/4 |3/3 **case inventories**,1548 **L/O/R numeric outputs**,9 **RCD seed outputs**,24 **C5 detector-cases**/4896 **bins** | SUCCESS trong một lượt issued G33.48 trigger records gồm12 early INSUFFICIENT_HISTORY/36 success;9 distinct integrated computations tái dùng đúng endpoint, không bỏ trigger |
| Synthetic durable seal |3 inventories/5781 **unique shard files** | Full science/masks/quality/models/graphs/controls/bins/triggers được verify; PREPARATION domain và `final_prediction_qualified=false`. Không tạo actual final seal |
| Synthetic fidelity/integrity/evaluator |3/3 **full scientific packets** G33 so G32; fresh pure evaluator replay | Exact scientific equality; không recompute old G32 models. Truth giả callback1 chỉ sau full durable verification; saved evaluator khớp exact JSON-normalized output |
| Isolated synthetic negative read-view copies |10/10 **mục kiểm lỗi** | Tamper/missing shard; missing/duplicate/stale inventory; missing C5 science; wrong DEV flag; source/config/anchor drift đều reject trước truth, callback0. Fresh original seal sau helper vẫn verify5781 shards |
| Purpose unit tests và regression |44 current G33 +381 regression **mục kiểm thử**, zero skips; F verifier44 **references** | PASS. Regression gồm F70/PRE-G25/G30–G31 146/G32 72/E targeted68; không phải425 ca mới hoặc full historical E harness PASS |

τ-only source audit, clocks/units và đúng C1 windows `[τ−300,τ)`/`[τ,τ+300)` của60 actual ca được kế thừa G32 sau hash verification, dùng inject_time thật. G33 actual resource audit kiểm controller accumulation riêng. **Ordinal45 thiếu log-reference vẫn OPEN**, giữ missing masks và denominator; không bỏ ca khó hoặc đoán service owner/root để hoàn thiện universe.

## Đường chạy và bằng chứng khoa học

G33 dùng frozen source/method của G32/F/E, thêm streaming/sharding/compact manifests và controlled final boundary. Workers chỉ nhận numeric arrays, masks/types/adjacency và opaque internal handle khi seed routing cần; không case IDs/path có nhãn/absoluteτ/root/fault/answers. C5 không nhậnτ để chọn trigger. Candidate universe/literal mapping/seed policy/threshold/comparator/failure semantics giữ nguyên. RCD đúng420/421/422, bins5; evaluator lấy trung bình metric của từng seed, không trung bình ranking trước chấm.

Full diagnostics giữ conversion provenance/clock/unit/window coverage, input/missingness/scalers/local evidence, observed and256 random-control graphs/operators/quality, C5 model/fit/calibration/neighborhood masks/predictions/errors/event policy, full trigger-local universe và integrated packet. Verifier buộc exact issued-binding field set, không cho missing key pass vacuously. No-evidence, missing roots và planned failures giữ nguyên; root absent chỉ evaluator-side zero, model không tự append root.

Numeric workers phát bounded frames, controller ghi immutable content-addressed shards, fsync/readback rồi hydrate một scientific role/case trong bound. Partial stdout/stderr/prefix và failed suffix giữ lại. Counter không có phải fail closed; timeout/budget phải kết thúc đúng numeric descendants. Common C1 timeout bao gồm cohort admission charge bảo thủ, get-case/startup/numeric/stream/spill/hydration/assembly và science/issued-binding/numeric-result persistence; inventory/global publication/evaluation có measurement riêng. Postprocessing quá deadline làm planned C1 outputs TIMEOUT, vẫn giữ raw numeric diagnostics.

PREPARATION và FINAL có key/domain riêng ngoài Git/bundle; hiện chỉ preparation key tồn tại. Weak issued execution chỉ fixed production factories, helper không được seal qualification. Global seal chỉ publish sau exclusive stage/fsync/readback/full verification và fresh context; missing/trùng/stale/tamper không tới truth. Lazy final evaluator buộc explicit authorization, final phase/permissions/key, full60 seal và toàn180 raw hashes trước fixed projection. Trusted host/HMAC và polled counters không phải OS sandbox, hard memory cap hoặc independent attestation.

## Hash và receipt kiểm được

W root là `D:/Project/flash-ticket-rca-research/results/task-g/g33-locked-final-campaign`; mọi `cache/` dưới đây là ignored generated preparation evidence.

| Nguồn | SHA256 / receipt |
|---|---|
| TD-v1.3, không sửa | `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971` |
| Current source/test aggregate6 | `e9b4c6b54ae22efb56a0225d7abed68368e6c13d74523ec015d7a12ab1f88337` |
| Frozen preparation run-contract | `10d521f1ef1896ddd99ff9fd872c0b1cd77c5472fa3e5b313b471bae857dbc4d` |
| Genuine preparation-seal.json | `7cb21cda5205353ba563528a653c94118822e75cc05217c1d876ba8d9224cf5b` |
| preparation-evaluation.json, artificial only | `17405e6a8d969a69b315b76fbc31a8474687318cc3d2d01a29276d71bf96ea87` |
| Current development timing | `489dff6e5635991f68286d699177a2a36d5196f63e17782c4b10293f1b0d319c` |
| Independent development audit v2 | `688b7625a8a5a40defd1b97e5989a8ef5c1840ae85e1dfec9c9bd9a39486bfa8` |
| Current upper-shape capacity | `88a5ca7a421278718827e999c61ece7002cedbee6aff8d1533fa24e6cc3fe8c9` |
| Independent capacity/four-attempt fidelity | `129a28f4f3ab647f97afe7df2dcd2b6f87c93607471612f67694afe3100c9076` |
| Full SYN3 G32/G33 fidelity | `7d4029b1636e6de7ef28e795552b9a7e6837dc2e756f975af7dc92dd56857271` |
| Root fresh fullverify/pure replay | `a8853a6a4054d5f1a5879b7af5a9b5acf208d4947feead483f9c952698592a12` |
| Independent sealed SYN/evaluator/cost audit | `0ddc991c1de9956fb0ac20dbdf019d8a74b52c4609c269b32696e38bfa7f05d0` |
| Livecopy10 negative checks | `21501f04f0e4be03b832c55d84b4ae855d8f47659ca2c85f7e09d4d168ddd7a7` |
| Posthelper original fullseal | `5400e2e5695d154aca4c12a6584231f8247c026f388d058d856da7d9f914242b` |
| External preparation cost receipt | `df388b5354467e81c86090009af2a6eb3ab5387a9250a2816689c810e471f1ac` |

Full current code/tests and measurements được reviewer đọc/kiểm riêng, không dùng hash hoặc status cũ thay nội dung. DEV/capacity đo dưới preserved contract7c315; registration10d521 chỉ bổ sung evidence/derived timeout/resource basis, không đổi underlying source/science/environment/permission/limits. Qualification delta được reviewer đối chiếu riêng, không rehash receipt cũ thành context mới. Contract giữ nguyên sau đăng ký để SYN/global seal còn kiểm được; trường status preregistration pending là checkpoint lịch sử, completion ghi ở review/handoff/cache proof, không bind ngược prose review vào sealed bytes.

## Chi phí, tài nguyên và những điều chưa đo

Registered driver `cache/registered-execution-cost-driver.py`, SHA `d8b67f27b2d3f5371f4716c055e0b290b29aa201a5a7452b3647e47faca44007`, đã thực chạy `PREPARATION`: numeric-run wall154.0443118s; durable-publication wall63.7081112s; sealed-evaluation wall20.6615990s. Hai receipt đầu được ghi bền vững trước fixed truth provider; receipt evaluation bind exact evaluation artifact. Các phần này bao gồm nhiều bước đã khai coverage; pure numeric/global write/truth read/pure evaluator/coldIO/selfreceipt-write chưa tách đo thì ghi null, không0. Fit/prediction/control/integrated/per-worker timings có hồ sơ từng phần; internal components có chồng lấn, không cộng làm end-to-end.

Current DEV measured timeout429.2569470000308s thay historical G32 363.504405s/G31 879.991028s cho producer G33 hiện hành. Không lấy receipt thời gian của producer cũ áp cho workload mới. Capacity current C1/C5 đúng hai-process peaks97.755.136/102.121.472bytes; genuine SYN24 worker executions đều quan sát numeric descendants, max worker-tree peak156.913.664bytes và max recorded controller peak179.838.976bytes. Bound một worker, input16MiB/frame4MiB/hydration64MiB, worker512MiB/controller3GiB; đo peak riêng với current-private guard, polling50ms không hard cap.

**OPEN:** actual full integrated/RCD upper-shape model runtime, actual combined campaign/evaluator memory, actual efficacy và final uncertainty/reproduction. Auxiliary/integrated900s là infrastructure policy, không measurement chứng nhận actual success. Source-only60 peak≈2.018GiB và bounded C1/C5 upper-shape cùng SYN3 full packets đủ căn cứ trình controlled final run với planned failure handling; không đảm bảo mọi actual ca thành công hoặc suy tổng runtime60 từ ba mẫu. Hiệu năng measured trên shared local host, không quiet-host benchmark hoặc speedup claim.

## Continuity, independent review và quản trị

Independent AI technical review phản biện actual implementation/resource/authority/completeness/clock/cost/fidelity, đã buộc sửa launcher-only RAM counter, missing controller counter guard, DEV-only scope flag, replay admission0, thiếu full conversion provenance và vacuous issued bindings trước current qualification. [Review đầy đủ](D:/Project/flash-ticket-rca-research/results/task-g/g33-locked-final-campaign/independent-final-review.md) giữ nguyên các checkpoint, immutable prequalification snapshots v1/v2/v3 và post-empirical snapshot. **Không human gate approval hoặc five-reviewer certification.** Final governance/doc review ghi thêm sau runtime evidence, không sửa sealed registration để tạo liên kết vòng.

Mọi attempt còn nguyên: capacity01 memory failure; capacity02/DEV02 launcher-only resource invalid hiện hành; actual source-only01 dừng40/60 do old2GiB, retry02 giữ40 prefix; DEV01 validation failure, DEV03 controlled stop14/30 khi reviewer phát hiện guard gaps, DEV04 current complete30. Unit/schema/diagnostic/import failures cùng pure audit context-drift/rawdict-serialization failures được lưu với reason; chỉ retry đúng lỗi kỹ thuật, không rerun để chọn numeric outcome. Genuine SYN3 current computation/seal là một lượt, không bị restart bởi usage interruption. Phiên resumed giữ checkpoints/logs và xác nhận branch/HEAD/Git trước tiếp tục.

Independent final preparation review đã chốt **PASS_BOUNDED_G33_PREPARATION_TECHNICAL_WITH_DISCLOSED_LIMITATIONS**, memo SHA `a5799a5660814cb6f4538019090eb80cf18944473b8f8501085acd651fe51d81`; immutable snapshot ở W G33cache/independent-final-preparation-review.md. Reviewer tự rehash6202/6202 protected tệp và kiểm Git/untracked/authorization prefix/historical bodies; own receipt SHA `5112b5e5d8a51dd95998860a1b843d1250918f22b1c6c68a4fe5aa9ed786c250` ở cache/independent-final-baseline-continuity-audit.json. [RCA-116](RESEARCH-DECISIONS.md) ghi FACT riêng; đây là AI technical review để trình human campaign decision, không tự mở actual gate hoặc five-reviewer certification.

Governance audit PASS, zero errors và một warning về bảy dirty paths P đã đối chiếu approved map14/thay đổi có trước. Full baseline audit01 PASS6202/6202 protected tệp, 6205 baseline tệp không mất; Pnew0/Wnew8 đúng approved map. Authorization snapshot vẫn prefix byte-exact và historical handoff/CURRENT bodies byte-exact. Branch/HEAD P `codex/rca-research-program`/`f06f978dc4ea184990ae3119adb34e5c462c788a`, W `main`/`62ffe22cbf5b5d4545b1c5281a25c0c90c4ac6d8` giữ nguyên. Audit01 ở W G33cache/final-protected-continuity-audit01.json; final audit02 ghi hash sau independent governance append, không circular binding vào seal. Handoff cập nhật trước CURRENT; đầy đủ diff và untracked paths được kiểm riêng.

Đúng approved map14: P ba authoritative/status documents; W ba `locked_*` modules, ba tests, contract và independent review đã có (**11 tệp preparation**). Ba eventual actual `predictions-seal.json`, `evaluation.json`, `reproduction.json` vẫn **ABSENT**. Generated attempts/helpers/shards/audits chỉ ignored G33cache; preparation key host-local riêng. Không thêm corpus/dependencies/source module hoặc đổi `.gitignore`; P README/ARTIFACT và W `.idea/`/dirty paths có trước giữ nguyên. Không commit/push/PR/reset/checkout/stash.

Tài liệu báo cáo đồ án cần dùng kết quả preparation để mô tả kiểm đầu vào, provenance, diagnostics/control graphs, seal-before-label, resource/failure policy, costs/missing measurements và reproduction boundaries; chưa ghi hiệu quả thật chưa đo. F vẫn development-qualified; historical TT90 outcome exposure được khai; five-reviewer OPEN/NOT FACTUALLY CERTIFIED; FlashTicket validation chưa thực hiện. Public benchmark không thay kiểm chứng hệ thống đích theo DT18; hai bộ tài liệu giữ đúng quyền quyết định và hai cửa nối, không thay yêu cầu/kiến trúc hệ thống.

## Quyết định tiếp theo trình Minh — chưa phải authorization

**CANDIDATE / chờ Minh duyệt:** một registered actual final60 campaign giữ nguyên TD/F scientific settings, gồm20 cells×3 repetitions, primary/secondary L/O/R với256 R draws, ba contextual baselines/RCD420/421/422 bins5 và tám C5 detectors; full scientific shards/attempts/cost/reproduction evidence trong G33 ignored cache và ba actual results trong map14. Planned failures giữ trong denominator, missing root/evaluator zero theo TD, không bỏ ca khó, không threshold/seed/method reselection và không favorable rerun.

Quyền cần Minh cấp mới là **actual final60 predictions/campaign và post-seal root/fault/τ projection chỉ để evaluator**. Projection cố định bảy columns `case,dataset,repetition,root_cause_service,fault,inject_time,time_start`, filter đúng pinned final60 trước đọc/trả closed values; toàn60 planned outputs/failures được seal và180 admitted raw hashes verify trước mở projection. Answers/outcomes và corpus/dependency acquisition tiếp tục đóng. Existing τ-only input audit không cần xin lại. Quyết định được ghi nguyên tử ở RESEARCH-DECISIONS trước đổi final contract flags/phase và tạo own FINAL-domain host key; preparation key/domain không cấp quyền final.

Sau khi có explicit human authorization, chỉ dùng **exact protected driver** tại W `cache/registered-execution-cost-driver.py FINAL` với current registered source/science/resource và thread1/Python environments đã kiểm. Direct evaluator API không tự đòi external cost receipts; vì vậy proposal này buộc dùng driver đã chứng minh thứ tự numeric-cost → global publication-cost → full verification → fixed truth → evaluation-cost. Không tự chạy FINAL trong preparation.

Lượt final phải xuất planned predictions/failures, complete final seal, evaluation/uncertainty theo frozen protocol (paired scenario bootstrap50.000, PCG64 seed20260926, type7, 97.5% cho mỗi hai confirmatory families; MC256 controls) và reproduction: full60 durable audit/pure evaluator replay, numeric ordinals0/29/59 với cùng measured admission charge. Mismatch/partial case giữ nguyên, không silent repair hoặc outcome-selected retry; interrupted partial computation cần explicit recovery decision, completed checkpoints chỉ tái dùng sau exact context/full verification. Independent scientific handoff sau actual outputs mới quyết định evidence complete; **không yêu cầu phương pháp phải thắng để đóng G**.

---

**Toàn checkpoint G32/G31/G30 bên dưới được giữ byte-exact như trước G33; các câu preparation/campaign chờ duyệt phải đọc theo thời điểm và quyền đã ghi ở trên.**

# Task G handoff — kết quả chuẩn bị và phần còn thiếu trước campaign thật

## G32 đã thực kiểm trong phạm vi chuẩn bị — 01/10/2026

**FACT: PASS_BOUNDED_BRIDGE_READINESS_WITH_DISCLOSED_LIMITATIONS. G chưa DONE; chưa đủ điều kiện chạy campaign final ngay.** [RCA-079–083](RESEARCH-DECISIONS.md) cấp đúng map14, bounded cache, readiness key riêng và τ-only input audit. G32 đã hoàn tất các lượt conversion/development/synthetic được giao; không chạy model, prediction hoặc evaluation trên actual final60, không mở root/fault/labels/answers/outcomes và không campaign. Các lựa chọn kỹ thuật được giao và quan sát kết quả ở RCA-084–087 không tự là xác nhận mới của Minh.

### Dữ liệu và lượt thực chạy

| Dữ liệu | Công việc / số đếm | Kết quả và giới hạn |
|---|---|---|
| Actual final telemetry đã admission ở G30 | Conversion-only 60/60 **ca**, tái dùng180/180 **tệp** sau hash verification; không corpus mới | Đủ đầu vào C1/C5/RCD/integrated360 và controller C3 cho60 ca; không phải60 model successes hoặc efficacy |
| Metadata đã pin | Projection roster5 và τ4 columns riêng; kiểm nguồn/units/windows cho60 **ca** | Cửa sổ C1 `[τ−300,τ)`/`[τ,τ+300)` nằm trong archive ở60/60; dùng inject_time thật, không tự origin+720 |
| Development numeric bundles F05 đã khóa | Một lượt mới30/30 **ca**,180/180 **matched arms** L/O/R của hai rankers | Workload C1 full diagnostics, không nhãn mới/efficacy; timeout mới có phép đo tương ứng |
| Synthetic | Ba archive tự tạo khác nhau, candidate counts6/5/4;3/3 **bộ kết quả numeric** | Mỗi mẫu tính một lần;1548 L/O/R outputs,9 RCD seed outputs,24 detector-cases C5 và4896 planned bins SUCCESS |
| Synthetic triggers |48 **trigger records** |12 early INSUFFICIENT_HISTORY giữ nguyên;36 integrated diagnoses SUCCESS, mọi trigger/universe/provenance đều được giữ |
| Pure fixtures và regression |72 G32 +309 regression **mục kiểm thử** PASS | Không phải381 ca mới; F70/PRE-G25/G30–G31 146/E targeted68, zero skips; F verifier44 references PASS |

**Actual coverage limitation:** ordinal45 thiếu log reference (zero rows), trong khi query log có dữ liệu. τ/window/unit và numeric conversion vẫn hợp lệ; “available” không có nghĩa mọi observation đầy đủ. Một modality coverage record còn OPEN; giữ masks/missingness và ca đó trong planned denominator. Không thêm alias/owner đoán hoặc root vào candidate universe; C1 V20–27, C5 V8–27, RCD123–184 eligible metric columns là kết quả conversion, chưa numerical model qualification. Reviewer đối chiếu120 C3 catalogs/6930 sampled sanitized excerpts, declared counts/rates/top3, sample hash + original physical row ordinal; coverage sanitizer theo literal/pattern được khai, không là hiểu toàn bộ prose hay truth certification.

τ được đọc qua isolated `case/dataset/repetition/inject_time` projection đã pin, roster qua năm columns không nhãn. Source controller giữ locators, literal mapping và absolute clocks riêng. Worker C1 chỉ nhận ref/query/adjacency/channel types và opaque routing handle; C5 chỉ nhận warmup/stream/adjacency/types/fit mask/relative endpoints, **không nhận τ để chọn trigger**. Known identifiers/clocks/paths/credentials/assigned closed fields được redact khỏi controller log support; prose chẩn đoán còn lại là untrusted telemetry. Không còn coi blanket message redaction là yêu cầu TD bắt buộc: đó là inference ban đầu đã được independent source review đính chính và sửa trước actual audit.

### Diagnostics, chi phí và durable boundary

G32 giữ input/missing masks/scalers/channel-quality/local evidence, selected operator state, all256 random-control graphs/diagnostics và primary/secondary scores. Có observed graph hash, degree/reachability và uniform-personalization diagnostics. C5 giữ full fit/calibration/model/applicability/neighborhood masks, coefficients/ridge diagnostics, residual scales, bin features/predictions/errors/availability và event policy; trigger thành công phải có graph/quality/full diagnostics cùng đúng trigger-local universe. Không bỏ failed/missing prefix hoặc suffix, không đoán owner hoặc sửa root để chấm.

Development max aggregate case wall là36.35044049999851s; nguyên công thức TD cho common C1 timeout **363.5044049999851s**. Independent reviewer rehash/reload30 locked numeric inputs,30 checkpoints,180 arms và15360 R outputs, rồi tái tính frozen timing formula. So AST/dependencies xác nhận controller-only sanitizer/audit changes không đổi DEV factory/worker/wire/diagnostics/timing helpers. **G31 timeout879.991028s chỉ lịch sử**, không dùng cho workload mới. Bound tính cold admission toàn cohort bảo thủ cùng get-case/startup/wire/full worker/output wall; không chứng nhận physically cold I/O từng ca. C5/RCD/integrated/seal ngoài common C1 scope này.

Actual conversion audit đo tổng1195.50797s; max complete conversion515.09103s, gồm integrated360 conversion504.35664s ởordinal30. Cold read/hash max3.14128s, C1 numeric conversion max7.37866s, C1 C3-support conversion max10.97296s, C5 conversion max7.09208s và RCD conversion max0.71412s. Đây là controller/conversion measurements, **không phải actual model runtime**; không cộng maximum của các ca khác nhau thành runtime một ca hoặc dùng integrated conversion làm C1 deadline.

Trên ba synthetic: tách đo C5 fit/prediction (tổng0.1184768s/2.0631124s), control generation, C1 worker/call wall, integrated calls và artifact writes. Artifact-write0.4286336s; external `commit_readiness` call6.2363052s gồm write/readback/verification/publication. Pure receipt-write chưa tách được: null/OPEN, không điền0. Các component có thể chồng lấn.

**Finding sau seal về tên trường chi phí:** SYN `arm_wall_seconds` kế thừa assembler hiện chứa numeric-component sums, chưa full aggregate wall. Không dùng alias đó làm end-to-end hoặc final timeout/budget qualification; full-wall DEV receipt dùng hàm measurement riêng và vẫn đúng. Giữ nguyên sealed results cùng finding; G33 phải sửa tên/coverage và chứng minh overhead attribution trước final. Chi phí synthetic không được suy thành runtime actual60.

Readiness receipt exclusive commit giữ3 **full case artifacts** (18,581,274/15,863,151/13,106,004 bytes), typed binary envelopes giữ NaN/masks, staging và checkpoint history. Fresh-process verifier reopens fixed contract/key, source8/protected57/config/environment và từng artifact; kiểm hash/HMAC/canonical/completeness/model/graph/score bindings trước lazy artificial provider. Callback đúng một lần sau verification. Truth fixture được khai trước computation, gồm một absent-root boundary; pure evaluator tái tính từ sealed artifacts khớp exact saved evaluation, không model rerun. Không population verdict hoặc final efficacy/bootstrap claim trên ba mẫu này. Planned60 orchestration fixture khai synthetic-only, **zero numeric computations**; known-answer tests kiểm đúng3 seeds420/421/422/bins5/mean metric per seed, ties, R256, failures, C5 straddling và frozen50k scenario bootstrap.

| Bằng chứng G32 | SHA256 |
|---|---|
| [Contract](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/run-contract.json) | `7169093d2248814ef7436728665d5379df72aad0940b99c511287098f147a3c6` |
| [Actual conversion audit — ignored cache](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/cache/actual-conversion-attempt-1790813816560279700/conversion-audit.json) | `e191fc4ef00e2162577fb1216883415750ec76c9f728fa07b6a0a668b78d857d` |
| [Development timing — ignored cache](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/cache/development-attempt-1790811530197938600/timing.json) | `490d837a39600f80f34c0f04359b25c9e0e7566612c0f1a3da1aabbf9f3758a0` |
| [Durable readiness receipt](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/readiness.json) | `096101485c64cd7784d4b079da64d0544ea5dc71f5123106b6a17ec4ca438f02` |
| Execution / three-artifact manifest | `6d9fdca53316bb78d610e4a03870aec848b02f7c3af793320a0deb0211fc45fb` / `f3eef76d20ad9d35a0b7994eaa642e1de4293d5b498a71f669e31b360749ece1` |
| [Synthetic evaluation](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/cache/synthetic-seal-evaluation-attempt-1790815349810650500/synthetic-evaluation.json) | `7d94b0b71abefd1a92b251cbd510a704b794f8db14c15ca6165ec1e31cd556a1` |
| [Fresh verification/evaluator reproduction](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/cache/fresh-process-verification-and-evaluator-reproduction.json) | `caae5b34b68e51365516677a135ceb4d9a91e7df6949e52a42a5d378057dc4a6` |

[Independent technical review](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/independent-final-bridge-review.md) giữ immutable prequalification4c7…, preconversion6485… và post-actual-conversionb532… snapshots trong cache. Contract binds preconversion review6485…; actual-use appendb532… không được gọi là snapshot đã preregister trong SYN contract. Reviewer fresh-verifies seal/evaluation, phản biện cost alias và capacity; không human gate approval, không five-reviewer certification. Readiness key có ACL riêng current user/SYSTEM/Administrators; HMAC chỉ trusted-host integrity, không independent attestation hoặc process/filesystem sandbox. Cả `run_final_campaign`, `evaluate_final` và final-receipt request luôn từ chối; sửa flag không cấp quyền campaign.

### Continuity, file set và giới hạn

**Review/quản trị cuối:** independent review hoàn tất technical và governance/document continuity, SHA `a4d591a5595973a3adcbeac72d538cffd1ce91b594e7f86d032691dc6342a5e6`; exact snapshot ở `cache/independent-final-governance-review.md`. Governance audit PASS với một warning về bảy dirty paths P; exact map14 đã được Minh duyệt và các path ngoài ba tệp P là thay đổi có trước được bảo toàn. Root/reviewer đối chiếu14/14 tệp được duyệt,57/57 tệp bảo vệ và8/8 source/tests đúng registered bytes; initial/current Git không mất path cũ, P thêm0/W thêm đúng11 path được duyệt. Authorization snapshot vẫn prefix byte-exact của decisions; historical handoff/CURRENT bodies giữ byte-exact. Branch/HEAD vẫn P `codex/rca-research-program`/`f06f978dc4ea184990ae3119adb34e5c462c788a`, W `main`/`62ffe22cbf5b5d4545b1c5281a25c0c90c4ac6d8`. [Final integrity/file-set audit](D:/Project/flash-ticket-rca-research/results/task-g/g32-final-bridge-readiness/cache/final-map14-code-diff-continuity-audit02.json) lưu hash tệp sau khi chốt bàn giao; không bind ngược review prose vào sealed contract/receipt. Đây là kiểm integrity/tài liệu, không model, conversion hoặc evaluation mới, không human gate approval.

Giữ failed attempts và lý do retry: source serialization/import harness; incorrect frozen evaluator assertion names; combined test attempt03 gọi nhầm Python3.9 gây `zip(strict)` errors, attempt04 chuyển đúng registered Python3.12.10; read-only reporting/schema/canonical-copy assertions; usage/network interruptions. Không sửa source để hỗ trợ runtime sai, không xóa staging/checkpoints hoặc rerun lấy kết quả thuận lợi. Actual conversion và genuine three-fixture computation/seal đều hoàn tất ở lượt thực hiện đầu. Raw contract lineage, original authorization, source-v1/source-v2 snapshots và từng DEV30/actual60/SYN3 checkpoint giữ được đối chiếu qua gián đoạn. Không có dependency installation hoặc corpus acquisition mới.

Đúng approved **14 source/document/result files**, cùng bounded ignored G32 cache và một host-local readiness key. Ba tệp P: decisions → handoff → CURRENT-STATE; W: bốn `final_*` modules, bốn associated tests, G32 contract/readiness/review. Hash TD-v1.3 vẫn `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`; G31 contract/receipt vẫn `97a1d9f5a212a476a9f5e20a83ea0ed695102c6efcd2c856334c2af555da6174` / `13100235f5020d7ac7e5ef25aa1cf2a116bb08779ed564cffd207a92ab658d7b`. G30/G31/PRE-G/F/E/README/`.idea/`/ứng dụng và các thay đổi có trước được bảo toàn; không commit/push/PR hoặc tự approve gate.

**OPEN thực chất trước actual final:** current G32 artifact/readiness APIs chỉ cho≤3 synthetic cases; wholecase JSON/worker stdout và full payload trong RAM không chứng minh lưu/verify được full60 diagnostics. Actual input bound max1,790,285 binary bytes/8,105,876 JSON-array upper bytes không thay proof whole output. C3 catalog/text có kích thước biến đổi và equal-endpoint diagnostics còn lặp khi serialization. Ba synthetic artifacts dưới128MiB cũng không chứng nhận actual scale. Full actual C5/RCD/integrated model runtime chưa đo; auxiliary900s chỉ infrastructure bound. Cost alias đã nêu cần sửa ở final producer. Actual campaign/domain/key, fixed post-seal truth provider, planned60 outputs/failures, final evaluation/uncertainty/reproduction và independent scientific handoff còn OPEN.

F vẫn development-qualified. Historical TT90/E1 exposure giữ; five-distinct-independent-reviewer certification OPEN/NOT FACTUALLY CERTIFIED; FlashTicket validation chưa thực hiện. G31 SYN60 là **cùng một mẫu metrics/traces/logs ba nút** qua60 test slots, không60 tình huống độc lập; durable90 là DEV30+SYN60, không90 ca mới. Historical INCONCLUSIVE do control mobility của đồ thị giả không suy thành dataset thật xấu, phương pháp thất bại hoặc thành công.

### Đề xuất G33 — cần duyệt phạm vi mới trước implementation

**CANDIDATE / chưa authorize. Chưa đề nghị bật campaign ngay.** Bước còn thiếu cần actual runner/trust domain và full-scale artifact policy riêng; giữ nguyên G32 code/contract/receipt đã seal. Dùng lại G32 issued conversion và frozen numeric method/config/opaque-handle seeds. Thêm streaming workers/content-addressed shards, compact case inventories, sequential full verification trước fixed actual truth reader; mỗi detector/bin/trigger/failure vẫn có planned reference, không giảm denominator. Tách numeric components với measured wall/overhead; transport thay đổi phải có matching timing/fidelity verification trước final registration, không tự tái dùng timeout vì tên workload giống nhau.

| # | Root / tệp mới hoặc cập nhật | Mục đích |
|---|---|---|
|1|P `docs/research-rca/RESEARCH-DECISIONS.md`|Atomic authorization khi Minh duyệt; giữ proposal khác USER_CONFIRMED|
|2|P `docs/research-rca/task-g-handoff.md`|Authoritative implementation/readiness/final handoff|
|3|P `docs/research-rca/CURRENT-STATE.md`|Derived state sau handoff|
|4|W `scripts/task_g/locked_campaign.py`|Own G33 permission/context; streaming numeric orchestration/all planned outputs/failures|
|5|W `scripts/task_g/locked_provenance.py`|Separate domain/key; sharded manifest/stream verification và exclusive global seal|
|6|W `scripts/task_g/locked_evaluation.py`|Fixed pinned truth projection chỉ sau seal; literal root mapping trong từng universe; locked statistics|
|7|W `tests/task_g/test_locked_campaign.py`|Streaming/prefix/resource/cost/failure fidelity|
|8|W `tests/task_g/test_locked_provenance.py`|Complete inventories/shards/tamper/drift/seal-before-truth|
|9|W `tests/task_g/test_locked_evaluation.py`|Known-answer mappings/metric/uncertainty/reproduction boundaries|
|10|W `results/task-g/g33-locked-final-campaign/run-contract.json`|Exact source/config/input/env/resource/permissions và planned coverage trước execution|
|11|W `results/task-g/g33-locked-final-campaign/predictions-seal.json`|Eventual complete final seal, không viết khi prediction gate còn đóng|
|12|W `results/task-g/g33-locked-final-campaign/evaluation.json`|Eventual locked final evaluation sau seal và truth gate|
|13|W `results/task-g/g33-locked-final-campaign/reproduction.json`|Predeclared audit/evaluator/numeric replay coverage, mọi mismatch/attempt retained|
|14|W `results/task-g/g33-locked-final-campaign/independent-final-review.md`|Independent implementation/resource và eventual scientific handoff|

Generated scope đề xuất riêng: ignored G33 cache cho bounded readiness fixtures/matching development timing, rồi **chỉ khi campaign được duyệt** cho60 actual case scientific shards, attempts và reproduction evidence; key G33 riêng ngoài Git/bundle. Không corpus mới/install dependencies, không sửa TD/F/E/PRE-G/G30/G31/G32/app/README/`.idea/`. Không thêm source module nếu unchanged G32 source handle suffices: G33 có context/authorization riêng và không promote `final_prediction_qualified=false` của G32. Future evaluator tự dùng fixed pinned metadata reader sau seal, không gọi G32 actual `map_truth` vốn từ chối; không caller-supplied arbitrary actual labels hoặc đoán owner.

**Minh cần duyệt tiếp:** phạm vi G33 preparation map14/cache/key trên để hoàn thiện phần tài nguyên và final boundary trước. Preparation vẫn đóng actual predictions/root/fault/labels/answers/outcomes/campaign. Khi implementation/streaming/resource/timing/independent readiness đạt, mới trình một registered campaign60 khóa và quyền prediction/campaign cùng post-seal evaluator projection cần thiết; answers/outcomes không tự mở. Không yêu cầu phương pháp phải thắng để đóng G, nhưng phải có planned outputs/failures, seal-before-label, evaluation, uncertainty/reproduction và independent handoff.

**Material cho báo cáo:** phân biệt admission, actual τ/conversion, development timing, bounded synthetic qualification và final efficacy; nêu missing log/unknown owner, masks, costs/alias coverage, trusted-host seal, attempt continuity, control mobility/uncertainty, historical exposure và kiểm chứng FlashTicket còn thiếu.

**Các mục G31/G30 bên dưới là checkpoint lịch sử giữ nguyên. Đề xuất G32/map14/τ-only “chưa duyệt” ở đó đã được RCA-079–083 supersede đúng scope; các kết quả/hạn chế lịch sử không bị thay.**

## G31 hoàn tất trong phạm vi synthetic/development — 30/09/2026

**FACT / scoped verdict: PASS_DURABLE_SYNTHETIC_DEVELOPMENT_READINESS_ONLY. G chưa DONE; actual final chưa sẵn sàng đầy đủ.** [RCA-077–078](RESEARCH-DECISIONS.md) đã duyệt đúng14tệp, cache giới hạn, key readiness riêng và timing-only development30. RCA-074 tiếp tục đóng τ/root/fault/labels/answers/outcomes/predictions/campaign thật. Không tự phê duyệt gate, không commit/push/PR.

Đã triển khai4modules và4tests; nguồn chỉ cấp numeric handles từ synthetic60 cố định hoặc30 numeric bundles F05 đã khóa. Không mở development case-audit labels hoặc chạy model final60. Independent reviewer tái hiện và kiểm lại hai lỗi mới ở lớp tích hợp: metadata chi phí bị chấm như số; timeout chỉ cộng thời gian tính toán thay vì toàn bộ công việc của ca. Đã sửa cả hai, không đổi phương pháp/split/evaluator/thresholds hoặc TD/F/E/PRE-G/ENTRY/app. Các hạn chế được công bố từ trước về provenance, exposure và five-reviewer certification vẫn giữ nguyên.

**Runtime đã thực chạy:** freshdevelopment30 hoàn tất,180/180 matched arms SUCCESS. Mỗi ca giữ256controls cho cả hai rankers trên cùng đồ thị. Signed complete-case wall bound lớn nhất87.99910279999995s; contract đăng ký trước synthetic qualification công thức `max(300,10*max)=879.9910279999995s` (khoảng880s). Đây là mức đo bảo thủ cho toàn ca, tính cả generation/startup/output và cold admission toàn cohort; không gọi nó là phép đo I/O hay chi phí riêng từng arm. Numeric components giữ riêng; C5/RCD/seal không nằm trong deadline C1 này.

Synthetic60 hoàn tất, driver exit0. Root và reviewer độc lập xác minh mới từ đĩa/HMAC đủ90artifacts (SYN60+DEV30), mỗi artifact đúng checkpoint và input fingerprint hiện hành. Trên SYN60:30,960 L/O/R outputs SUCCESS;15,360control draws/operator,180/180 genuine RCD seeds SUCCESS;480detector-cases/86,400finite C5bins. Tái tính độc lập khớp720triggers:240sớm giữ INSUFFICIENT_HISTORY,480integrated diagnoses SUCCESS. Không shared preprocessing failure; không bỏ ca, seed hoặc trigger để cải thiện kết quả.

Sau durable verification, evaluator gọi lazy artificial provider đúng một lần. Fixture dùng τ720s/root giả trong3nodes/20cells×3repeats; không đọc nhãn thật. Output giữ60costreceipts và metadata riêng, không điền chi phí thiếu bằng0. Technical evaluation pipeline PASS; scientific verdict trên fixture **INCONCLUSIVE — INSUFFICIENT_CONTROL_MOBILITY**,0/60mobile vì đồ thị giả quá nhỏ để đổi cạnh. Không chỉnh fixture để đạt kết luận thuận lợi; kết quả này không chứng minh efficacy trên final60.

| Biên nhận / nguồn | SHA256 |
|---|---|
| [G31 contract](D:/Project/flash-ticket-rca-research/results/task-g/g31-campaign-readiness/run-contract.json) | `97a1d9f5a212a476a9f5e20a83ea0ed695102c6efcd2c856334c2af555da6174` |
| [Durable readiness receipt](D:/Project/flash-ticket-rca-research/results/task-g/g31-campaign-readiness/readiness.json) | `13100235f5020d7ac7e5ef25aa1cf2a116bb08779ed564cffd207a92ab658d7b` |
| Execution payload | `f1d74d795b09e4c49382581fb1e8f929a64c0d9498cb5d3e45d79e18b714f10a` |
|90artifact manifest | `aa1b8443a13fb9c13e283c5899e37d21b36eb2b13232d3334d30a9dbc55d55af` |
| [Synthetic evaluation — ignored generated evidence](D:/Project/flash-ticket-rca-research/results/task-g/g31-campaign-readiness/cache/synthetic-evaluation.json) | `ea80a7ecaf3c00ef2aecd157c673f1bac66c0a2e9baf487cd497f28007559489` |

[Independent review](D:/Project/flash-ticket-rca-research/results/task-g/g31-campaign-readiness/independent-campaign-readiness-review.md) giữ nguyên snapshot trước runtime SHA`0e144ab05282c32d25d413f0dc09d2bf127cd876d0fff4df0036ad99bc4d7ac3`, rồi append actual checks. Reviewer xác minh execution90 và saved evaluation; không trực tiếp quan sát root callback, chỉ kiểm code/tests và artifact khai ordering. Readiness domain/key/HMAC chỉ trusted-host integrity, không independent cryptographic attestation hoặc final qualification.

**Validation:** Task G146/146PASS (no skips), independent47tests/interface probePASS; F70/PRE-G25/E math-firewall-replay68PASS. Broad legacy E discovery176 có5harnesserrors do thiếu coordinatorcontract/CLIargument; không sửa closedE và không gọi broad run đó là PASS. Attempt1devtiming dừng có chủ đích14/30 để sửa integration, giữ/recheck28signed+stagingfiles và auditSHA`e5268d2fe352775aac8652954575f095f5941912e6afc82520c5072396cc8497`; không dùng nó chọn timeout. Fresh30pass đăng ký ổn định trước đo, giữ lịch sử raw UTF-8 contracts. Một root aggregate probe dùng sai fieldname rồi được sửa trước artificial callback; không ảnh hưởng predictions/receipt.

**Continuity:** G30 telemetry admission đã hoàn tất60/60 trước G31; không tải lại corpus hoặc chạy lại admission trong bước này. G31 giữ từng complete signed checkpoint; gián đoạn agent không tự làm mất kết quả. Việc tái dùng chỉ hợp lệ khi input/source/config/domain/hash còn khớp; không hứa tái dùng sau thay đổi phương pháp hoặc producer.46protectedreferences,8registeredsource/tests và44Fverifierreferences được kiểm; TD/F/E/PRE-G/ENTRY/README/`.idea/`/app giữ nguyên.

## OPEN — cầu nối và hồ sơ actual final

Cầu nối final telemetry→known-window C1/RCD dùng τ thật, quyền truth provider sau seal và campaign domain/anchor riêng chưa hoàn tất. Full scientific packet/model/scaler/residual/masks/quality provenance, uniform-personalization/degree/reachability diagnostics và separated C5 fit/prediction/receipt-write timings cũng còn OPEN. Numeric endpoints hiện hành không chứng nhận đã có những dữ liệu này. Task F vẫn development-qualified; G30 chỉ xác minh admission/conversion, không tự là final adapter efficacy.

**CANDIDATE bước tiếp theo G32:** giữ nguyên byte/source/contract/receipt G31 và G30; hoàn thiện bridge/reporting trong modules mới, sử dụng lại frozen numeric functions và lựa chọn TD/F. Kiểm bằng fixtures, nguồn telemetry đã được RCA-073 cho phép và τ-only audit nếu Minh cấp riêng; không mở root/fault/answer/outcome, không final prediction hoặc campaign. G32 readiness không được dùng key/domain để tự cấp quyền chạy cuối.

Exact proposed map14 **chưa được duyệt hoặc thực hiện**:

| Root | Tệp / mục đích |
|---|---|
| P | `docs/research-rca/RESEARCH-DECISIONS.md` — chỉ ghi authorization mới sau khi Minh xác nhận |
| P | `docs/research-rca/task-g-handoff.md` — nguồn kết quả và review |
| P | `docs/research-rca/CURRENT-STATE.md` — trạng thái dẫn xuất |
| W | `scripts/task_g/final_source.py` — admitted telemetry→issued observations, τ-only control và oracle isolation |
| W | `scripts/task_g/final_campaign.py` — fixed bridge/orchestration và đầy đủ diagnostics/costs; execution thật vẫn đóng |
| W | `scripts/task_g/final_provenance.py` — context/domain/artifact schema và durable seal boundary riêng |
| W | `scripts/task_g/final_evaluation.py` — lazy truth bridge, remap root/strata, chỉ synthetic evaluation trong readiness |
| W | `tests/task_g/test_final_source.py` |
| W | `tests/task_g/test_final_campaign.py` |
| W | `tests/task_g/test_final_provenance.py` |
| W | `tests/task_g/test_final_evaluation.py` |
| W | `results/task-g/g32-final-bridge-readiness/run-contract.json` — đăng ký trước qualification |
| W | `results/task-g/g32-final-bridge-readiness/readiness.json` — bằng chứng safe execution/τ-only audit |
| W | `results/task-g/g32-final-bridge-readiness/independent-final-bridge-review.md` |

Generated scope đề xuất: bounded synthetic/development/conversion evidence trong ignored G32cache, readiness host key riêng; tái dùng180telemetry objects hiện có sau exact hash verification, không acquisition mới/dependencies. τ-only dùng declared timing metadata đã khóa và kiểm clocks/windows theo nguồn chính thức; source provenance phải được xác minh trước dùng, không đoán τ từ nhãn hoặc tùy chọn origin+720. Nếu source không tách được τ mà không mở root/fault/answers, giữ OPEN và báo Minh, không tự mở fields còn đóng. Model workers chỉ nhận numeric arrays/local indices; C5 runtime không nhận τ. Test metadata/labels remap, candidate universe/clock/window guards, prefix/future invariance, packet completeness, costs missing/failure semantics và fresh durable verification trước synthetic truth; independent review trước proposal actual final execution.

Cần Minh duyệt map mới và quyền τ-only input audit theo AGENTS impact rule cùng RCA-074 đang đóng quyền đó. Đây là phạm vi chuẩn bị cụ thể, không xin lại quyền G31/RCA-073 và không tự là quyền chạy toàn bộ G. Sau bridge/reporting qualification mới trình contract/permissions chạy actual final: một campaign đã khóa, giữ toàn planned outputs/failures, seal predictions trước mở truth, evaluation/uncertainty/reproduction/independent scientific handoff. Kết quả negative hoặc INCONCLUSIVE hợp lệ vẫn có thể đóng G; không chạy đến khi graph thắng. FlashTicket validation và formal five-reviewer certification không được suy từ dataset readiness.

**Material cho báo cáo:** tách admission/recovery, technical readiness và efficacy; nêu missing/unknown observations, timing bound, HMAC trust model, failed attempts, control mobility và uncertainty theo phạm vi đã đo. Không thay kiểm chứng FlashTicket bằng kết quả public dataset.

**Checkpoint ENTRY bên dưới giữ đúng kết quả đã hoàn tất; “next map14 chưa duyệt” trong checkpoint cũ đã được RCA-077 supersede đúng readiness scope.**

STATUS: **PASS_TELEMETRY_ADMISSION_WITH_LIMITATIONS; DURABLE ENTRY AND RECOVERY CONTINUITY VERIFIED; INDEPENDENT ACTUAL-USE REVIEW PASS; FULL CAMPAIGN RUNNER NOT READY; LABELS AND CAMPAIGN CLOSED.** Ngày 30/09/2026, owner Lê Văn Minh. Technical evidence theo phạm vi thực kiểm, không tự phê duyệt scientific gate.

## Kết quả actual admission sau recovery — 30/09/2026

**FACT:** session18444 exit0, attempt2 hoàn tất **60/60 compatible cases /180/180 verified telemetry objects**, declared bytes1,309,388,082. Tái dùng176objects sau exact bytes/hash verification, tải mới4objects; không tải lại toàn60 hoặc chạy model final. Attempt2 recorded2026-09-30T12:53:44.540642UTC, elapsed4215.737186500002s **cộng dồn từ đầu driver**, cache SHA`3ea8e99a470dc27f6a7f277eea108cdd4802ac7cf543f0f5b03c049b442227a4`, audit SHA`cd82027325f9910588b9970bea033f1ef90194b313ce1766443f32d63eae448c`. Attempt1/failure/partial và planned60 giữ nguyên như recovery chronology bên dưới. Root và independent continuity reviewer `/root/g_recovery_continuity_review` so exact first58 successful audit objects giữa hai attempts: **58/58 giống nguyên object**, canonical digest`2a8f7c0f53b248c483ecb0279c5ba1b2770523adc851265f72b7c27434963933`. Reviewer xác nhận toàn60rows/admission aggregate khớp signed payload,63/63planneditems, source/config/safety/limits giữa attempts giống nguyên object và fresh HOST verifier PASS. Verdict **PASS_RECOVERY_CONTINUITY_FOR_TELEMETRY_ENTRY_ONLY**; không đọc raw values/nhãn hoặc mutation. Retry duration là chênh cumulative clocks **877.3976243s ≈14phút37giây**, không cộng hai elapsed values hoặc dùng admission time làm TD model timeout. Reporting precision này đã được ghi, không sửa immutable cache/receipt.

[Durable entry receipt](D:/Project/flash-ticket-rca-research/results/task-g/g30-entry-validation/entry-validation.json) được exclusive commit2026-09-30T12:53:50.494041UTC sau complete source audit và genuine synthetic RCD3seeds SUCCESS; driver total4221.944858400002s. SHA`570da401927a71b618a40d0a043354d326ccfd4920d47f6fd72dcc0eba928f34`, payload SHA`e58001d2f48a5548fe0820d9d757e0fd54fe5e7b2f6262996e2167006f0aaeed`, matrix SHA`cb1c1f30f7aa46a3ceacf75006df41080b24ef31ec6aa10f65cc25384bdccb18`. Fresh-process host verifier PASS_DURABLE_ENTRY_ONLY /ENTRY_RAW_ADMISSION /HOST_TRUSTED, đủ2planned conditions/63items gồm RAW60 +SYNTHETIC RCD3. Root probe actual HOST receipt với allow_synthetic_fixture=True vẫn reject trước callback, **callback0**. `final_prediction_qualified=false`; không được gọi đây là sealed final predictions hoặc final evaluator qualification. Contract/six registered source-test bytes giữ immutable.

**Input-quality limitations, không phải recovery drift:** cả60 có placeable declared-unit clocks cho metrics/traces/logs, C1/C5/integrated conversion-only và RCD numeric-input conversion-only; đủ120 C5 prefix checks exact-equal. Exact mapping counts trên toàn60:11,491 matched metric keys và10,229 unknown; không thêm alias hoặc đoán owner. Nonfinite elements trong label-free numeric probes:C1261,197/C51,241,143/integrated156,641, giữ missing/masked observations đúng frozen adapter, không tự coi là corrupt hoặc successful finite final signal. Actual τ/windows và eligibility/model numerical guards còn chưa kiểm; raw compatibility không chứng nhận efficacy. Main package versions chỉ post-registration FACT như công bố.

**Independent actual-use review PASS với giới hạn:** `/root/g_entry_adversarial_review` rehash180objects exact1,309,388,082bytes/descriptor digest, kiểm180footer/schema/clock projections và đối chiếu signed footer facts, zero clock null/nonfinite/fractional rows; aggregates metrics86,460rows/traces44,510,813/logs14,457,867. Không in IDs/paths/timestamps/messages. Predeclared ordinals0/29/59: independent C1/C5/integrated numeric digests, RCD input/owner digests và literal-mapping aggregates khớp; đủ6 independent full-versus-prefix-replay-versus-future-truncated equality checks. Reviewer tự đọc hai aggregate attempt caches:58successobjects exact-equal, planned60giữ ở cả hai, retry admission khớp signed payload; fresh HOST verifier PASS và label callback0. Review chỉ authorized telemetry/conversion, không model/actualτ/labels/outcomes. Memo được append sau immutable15,344byte pre-admission snapshot; history/contract/receipt/sixsource/test không sửa. Không có new admission blocker; full-campaign caller/provenance/evaluation/timeout obligations vẫn OPEN.

Post-admission appendix recorded2026-09-30T13:05:01.145225UTC; complete memo26,926UTF8LFbytes SHA`7fc8711a9e05f536d8f0112031edd13172941a2fc6c82a165054c69118f47210`, originalprefix SHA`c8555c50849eca7e88b75f7fd4351e4fca1c7e67c08e0696c3989620208b56b2` unchanged. Final file set đúng13approved text/code files+180localignoredobjects/approvedcache+hostkey.37protectedrefs/sixregisteredhashes/PWHEADs khớp; PythonAST6 vàgitdiffcheckPASS; governanceauditPASS, warningdiff7 yêu cầu impactmap đã được RCA-072 đáp ứng. Không commit/push/PR hoặc self-approval. Assumptions/OPEN được nêu ngay tại checkpoint này. Báo cáo đồ án sau này cần tách recovery/telemetry compatibility khỏi final efficacy, nêu unknown/missing observations, frozen-policy failure handling và các giới hạn provenance/reproducibility; dataset evidence không thay kiểm chứng FlashTicket.

## Checkpoint sau RCA-072–076 trước actual-complete — code đã review, đang kiểm telemetry

[RCA-072–074](RESEARCH-DECISIONS.md) đã duyệt đúng map **13 text/code files**, tối đa **180 telemetry objects / 1,309,388,082 declared bytes** và host-local entry key. Không xin lại quyền telemetry. [RCA-075–076](RESEARCH-DECISIONS.md) yêu cầu tiếp tục đến readiness final và cho dùng subagent để phản biện; nhãn/campaign vẫn đóng.

**FACT:** ba G source modules và ba tests đã được đăng ký SHA trước stable qualification; **49/49 G tests PASS**, không skip. Regression thực chạy: PRE-G **25**, F **70**, E math/firewall/replay **68** đều PASS. F có hai invocation thiếu import/discovery path trước lần đúng; official metadata plan có một invocation thiếu `src` trước khi query API; không gộp các lỗi harness này thành model failures hoặc che khỏi receipt. Genuine G synthetic numeric worker dùng đúng issued RCD Python3.9, seed420/421/422, bins5: cả ba SUCCESS; không dữ liệu final hoặc nhãn. Official metadata-only plan khớp đúng 60/180 và các digest đã khóa.

Independent reviewer `/root/g_entry_adversarial_review` thực chạy lại **49/49**, kiểm 37 protected references, probe downloader redirect/hash/overbudget/exclusive publication/reuse và lỗi fsync. [Pre-admission memo](D:/Project/flash-ticket-rca-research/results/task-g/g30-entry-validation/independent-entry-review.md) có exact snapshot SHA `c8555c50849eca7e88b75f7fd4351e4fca1c7e67c08e0696c3989620208b56b2`, 15,344 UTF8 LF bytes. Receipt giữ riêng root-reported RCD/official-query observations. Ba finding mới đã sửa trước admission: second-fsync có thể để biên nhận hợp lệ sau commit lỗi; roster digest khác newline convention đã khóa; lỗi RCD input có thể ghi nhầm C1 failure. Mutable private plan issuer/state lookup trong draft cũng đã bị gỡ trước registered snapshot. Không sửa TD/F/E/PRE-G.

[Entry contract](D:/Project/flash-ticket-rca-research/results/task-g/g30-entry-validation/entry-contract.json) SHA tại lúc mở telemetry gate `33d3a0e297b6bf0f2f8493c50ec5b5442a7bf7b2b0c68b24f495c33c760512be` giữ exact lịch sử đăng ký trước implementation, trước stable checks và trước raw, rồi binds six source/test hashes + exact review bytes/digest + source allowlist/schema + host anchor. Label/τ/answer/outcome/final-prediction/campaign permissions đều false. Gate mở do quyền telemetry RCA-073 và technical pre-admission review; không human scientific approval mới.

Host entry key được tạo exclusive ngoài Git/bundle; ACL protected chỉ current user, SYSTEM và Administrators, ba allow rules, zero broad-read rules; không in secret. HMAC verifier cũng biết secret; assumption là trusted host/runtime/filesystem, không độc lập cryptographic attestation hoặc sandbox. Numeric worker stdin chỉ có values/owners; không tuyên bố OS process bị cô lập khỏi filesystem. Biên nhận ENTRY không thể cấp quyền final evaluator hoặc campaign dù HMAC hợp lệ.

**Declared reproducibility limitation:** main-runtime package versions được reviewer quan sát sau registration/during admission tại2026-09-30T11:50:24UTC: Python3.12.10, NumPy2.5.3, Pandas3.0.6, PyArrow25.0.1, HFHub1.32.0. Đây là post-registration FACT, không sửa running contract để giả rằng đã preregister package versions. Registered RCD qualification identity vẫn được kiểm riêng. Next campaign readiness phải bind exact environment trước qualification/final run.

Acquisition/admission thực tế đang chạy theo allowlist đã review: rehash byte trước footer/schema, schema trước value parse; giữ failure/availability và planned60. C1/RCD origin+720 và integrated endpoint360 chỉ là **label-free conversion probes**, không đọc actual τ hoặc kiểm efficacy. Actual-use kết quả, durable HOST roundtrip và review sau admission sẽ được ghi bổ sung sau khi hoàn tất; checkpoint này chưa tuyên bố raw PASS.

### Recovery chronology — không gộp usage interruption với source failure

Minh yêu cầu tiếp tục sau usage limit và kiểm tính liên tục của output. **FACT:** root quan sát session18444 vẫn chạy sau agent interruption, tiếp tục từ53 lên58 compatible cases. Attempt1 dừng vì `SourceAdmissionError` tại ordinal58 sau176 verified objects; không phải bằng chứng usage limit làm dừng downloader. Cache attempt1 recorded2026-09-30T12:39:07.142402UTC, elapsed3338.3395622000025s, SHA`74ad5e2a1c0583f7e676f3f6a82926d270334c0fc153959027b6641778139986`; case-audit SHA`a3d6cae562e3b3576568184d801b5fc73da3ae4a81d1fa82dd54c2acdece5684`. Giữ đủ60rows gồm58 SUCCESS, ordinal58 FAILURE và pending59 UNAVAILABLE; một partial11,068,909bytes được giữ trong acquisition cache, không coi là verified object. Exception type không xác định network/HTTP/timeout/hash cause; nguyên nhân sâu vẫn OPEN.

Bounded retry2 theo recovery driver hiện hành tái dùng published files chỉ sau exact bytes/SHA verification, tái kiểm raw/schema/conversion cả60 để có một complete issued admission. Không tải lại mọi object hoặc trộn partial vào corpus; không bỏ case/failure, không thay code/contract/frozen inputs giữa attempt. Root rehash37 protected references vàsix registered sources/tests đều khớp; running contract SHA33d3a0... unchanged. Reviewer bị usage limit đã được resume; reviewer continuity riêng được giao so sánh first58 successful audit objects với retry sau complete, chỉ aggregate receipt, không đọc nhãn. Success/failure retry chỉ ghi sau actual completion; chưa có signed receipt tại checkpoint này.

## Phần còn thiếu để toàn bộ G runner sẵn sàng — CANDIDATE map tiếp theo

Approved controller hiện hành có `campaign_implementation=NOT_IMPLEMENTED_IN_ENTRY_PHASE`; `run_campaign` và `evaluate_final` fail closed. Entry completeness chỉ gồm RCD synthetic3 và raw admission60. Vì thế hoàn tất telemetry admission không đủ để nói full G runner ready. Đề xuất giữ nguyên byte ENTRY, PRE-G, F, E và thêm boundary campaign riêng. Đây là implementation readiness, **không chạy final hoặc mở nhãn**.

| Thành phần | Dùng lại | Bổ sung cần kiểm |
|---|---|---|
| C1 | Frozen local/rank/perturb functions và selections | Matched identity-L/O/R cho PPR và diffusion; cùng realized undirected graphs; đủ256 draw scores/graphs/failures; numeric guards/mobility/timeout/recovery |
| Contextual | Local-MAX, BARO, qualified RCD và frozen triplet mapping | Worker600×K numeric-only, copied same input, đúng3seeds/bins5; preserve unknown coverage/worst ties/failed0/mean metric |
| C5/integrated | Frozen C5/replay/integrated functions, nguyên8thresholds | Issued actual-admission-to-observation; đủ mọi bins/triggers/past windows; không dùng τ để chọn trigger trước seal |
| Provenance | ENTRY durability lessons/trusted-host model | Campaign domain/run/anchor riêng; complete planned matrix/artifacts; fsync/readback/exclusive publication; fresh-process verifier trước lazy root/fault provider |
| Evaluation | Pure frozen tie/mapping/regime/event helpers | Planned60/20×3; mean per draw/seed; hai contrasts; paired50kPCG64 bootstrap/type7; leaveouts/scatter/repeat/MC; coverage/verdict/cost |
| Resources | Frozen development cost receipts | Numeric timeout với exact derivation và hashes; bounded worker/memory; giữ crash/failed attempts, không chọn rerun/seed tốt nhất |

Hai **NEW caller correctness obligations**, đã được hai agents tái hiện synthetic độc lập: (1) F `L_scores` là raw local evidence; G phải tính registered identity-L qua selected operator trước rounded-tie evaluator. Vector `[1e12,1e12+.25]` cho raw RR1 nhưng identity PPR d.5 cho tie RR.75. (2) `numerical_channel_failures` có thể để finite local0; G phải nhận failure/INCONCLUSIVE thay vì gọi successful all-tie. Frozen E controller đã thực thi cả hai; không sửa frozen F để giải quyết G caller obligation. F primary `R_scores` cũng không đủ cho secondary diffusion: chấm secondary trên cùng realized graphs. Không dùng Task E development controller nguyên khối vì DEV_IDS và early labels; chỉ tái dùng pure functions.

Known-window τ là quyền riêng: readiness tests dùng fixture τ/provider. Campaign sau này cần explicit τ-only slicing ở trusted controller trước C1 model; root/fault/answers/outcomes vẫn chỉ mở evaluator sau complete durable seal. Origin+720 admission probe không thay actual τ verification.

Exact proposed **14 text/code files**, chưa mutation:

| Root | Path / mục đích |
|---|---|
| P | `docs/research-rca/RESEARCH-DECISIONS.md` — approval mới nguyên tử |
| P | `docs/research-rca/task-g-handoff.md` — actual readiness/review/OPEN |
| P | `docs/research-rca/CURRENT-STATE.md` — trạng thái dẫn xuất |
| W | `scripts/task_g/campaign.py` — full orchestrator và fixed numeric worker, final runtime gate đóng |
| W | `scripts/task_g/campaign_source.py` — admitted source → issued numeric observations |
| W | `scripts/task_g/campaign_provenance.py` — complete campaign artifact/phase/trust boundary |
| W | `scripts/task_g/evaluation.py` — lazy evaluator và endpoint/statistical aggregation |
| W | `tests/task_g/test_campaign.py` |
| W | `tests/task_g/test_campaign_source.py` |
| W | `tests/task_g/test_campaign_provenance.py` |
| W | `tests/task_g/test_evaluation.py` |
| W | `results/task-g/g31-campaign-readiness/run-contract.json` — đăng ký trước qualification |
| W | `results/task-g/g31-campaign-readiness/readiness.json` — safe execution/integrity receipts |
| W | `results/task-g/g31-campaign-readiness/independent-campaign-readiness-review.md` |

Generated scope đề xuất: bounded synthetic/development numerical artifacts dưới ignored `results/task-g/g31-campaign-readiness/cache/` hoặc isolated temporary fixtures; host-local readiness key riêng tại `%LOCALAPPDATA%/FlashTicketRca/task-g-trust/g31-campaign-readiness/issuer.key`, không in secret. Không thêm raw corpus, không thay allowlist180 đã đăng ký cho admission; actual admission chỉ được ghi theo receipt sau khi hoàn tất. Không final predictions/labels/τ/outcomes/campaign; không cài dependency. Readiness key/scope chỉ synthetic/development, không thể tự cấp campaign qualification. Một actual final run cần contract/domain/anchor/permissions riêng sau review và quyền Minh.

Validation đề xuất: analytic identity ties; numerical failures; mean-metric versus mean-score; exact60/20×3; đủ256draws và3seeds; C5 mọi bin/trigger; forced timeout/crash; stale-source/config/anchor/missing/duplicate/tamper/fsync/readback đều callback0; prefix/future-suffix/shared-input invariance; frozen equivalence; independent code/firewall/statistical review.

**FACT / OPEN timeout:** independent read-only cost audit xác nhận existing development receipts không cung cấp matching frozen L/O/R aggregate case walltime gồm đủ256 control generation. [E033 resources](D:/Project/flash-ticket-rca-research/results/task-e/e27-033-c1-development-full/c1-results.json:499116) đo tám locals/chín rank configs/leave-cell requests và cached R; [E022 topology](D:/Project/flash-ticket-rca-research/results/task-e/e27-022-development-topology/topology-summary.json:1265) đo generation/verification/save riêng, undirected200 max99.28398139998899s; [F05](D:/Project/flash-ticket-rca-research/results/task-f/f05-final-development-validation/development-validation.json:1233) loại R ở30cases, chỉ một R smoke không separate elapsed. Hashes lần lượt `eeeb0a09f43281d1df649a1b74e352d484796263bd3fb921aadb1c634e1c1e3d`, `b188513f46e48c20754fe94df1b9fdaebb1a3395b4b4ca635d9abd73dd5a40db`, `9e5031dc3b797596c03a78eca0cccdb607457281bbb45779e1933a50160e8a6f`. Cộng các workload khác nhau cho tenfold1018.6689430006663s chỉ NONMATCHED_CONSERVATIVE_ACCOUNTING, không đăng ký làm exact TD timeout; không tự gán300s.

Next map14 đề xuất thêm **một timing-only pass development30** trong generated cache đã nêu, bằng pinned numeric bundles/frozen selections và cùng proposed final worker/resource policy. Đăng ký bounded workers/threads trước đo; tính identity-L, observed-O, đủ256 chain generation/R scores và primary/secondary, shared generation không tính hai lần; giữ arm totals và aggregate wall, tách cold I/O/shared preprocessing/control/prediction/write. Không cần nhãn/efficacy/τ mới, không raw final, không reselection. Giữ mọi failure/attempt; thiếu matching successful record thì timeout vẫn OPEN. Bind maximum/công thức TD cùng input/source/config/receipt hashes trước final run. Đây là phần đề xuất cần duyệt, chưa đo hoặc tự cấp quyền.

Map này cần Minh duyệt theo AGENTS impact-map rule; nó mở rộng từ entry-only implementation sang full-runner readiness. Không xin lại RCA-072/073, không đổi scientific method/split/evaluator/thresholds và không tự mở campaign. G DONE sau này gồm đủ planned output/failure records, sealed→evaluation đúng thứ tự, uncertainty/reproduction và independent scientific result review/handoff; negative hoặc INCONCLUSIVE hợp lệ vẫn được đóng, không chạy đến khi graph thắng.

## Checkpoint trước RCA-072 — đăng ký entry và đề xuất map đã được duyệt sau đó

Phần phía dưới giữ trạng thái và nhận định tại lần đăng ký ban đầu; các câu chờ approval/raw chưa mở đã được RCA-072/073 supersede đúng phạm vi. Exact original text/hash cũng được lưu trong entry contract; không sửa lịch sử PRE-G/F/E.

## Quyền và impact map của bước hiện hành

[RCA-071](RESEARCH-DECISIONS.md) ghi nguyên văn Minh mở G ở bước kiểm tra đầu vào và chuẩn bị campaign. [Master G](MASTER-RESEARCH-PROGRAM.md) sở hữu contract task; [TD-v1.3](task-d-method-and-experiment-specification.md) và [F handoff](task-f-handoff.md) sở hữu phương pháp/release. [Corrective PRE-G](pre-g-readiness.md) chỉ đạt metadata/synthetic/development qualification.

Impact map ghi trước kiểm tra: đúng **ba tệp P** — sổ `RESEARCH-DECISIONS.md` ghi quyền trước; handoff G này lưu kế hoạch, receipts aggregate và OPEN; `CURRENT-STATE.md` dẫn trạng thái sau. Không tạo/sửa code hoặc results ở W trong bước đăng ký/kiểm tra nguồn hiện có. TD-v1.3, completed E, F v1/v2, `src/rca/`, PRE-G receipts/source/tests, README/`.idea/` và ứng dụng FlashTicket ngoài scope mutation.

Kiểm an toàn hiện hành: rehash frozen release/source/receipt, đối chiếu split bằng đúng metadata projection đã duyệt, stat khả năng có telemetry local mà không mở byte/row, kiểm runtime/host và source boundaries. Không query nhãn, root/fault/τ, answer/outcome, không tải raw hoặc chạy model final. Dùng lại các test/review còn nguyên hash của PRE-G/F; không rerun campaign hoặc sửa historical receipts.

## Inputs đã khóa

| Input | Identity / hiệu lực |
|---|---|
| TD-v1.3 | SHA256 `34fd73f6a84dc6b45834bd7fd4cc7b86e19e54f1011de99735631f027ee18971`; RCA-062 human freeze |
| Task F v1 | Manifest SHA256 `18484bc4bb0c1d19e8f6936f12d8365a8ad69e16fffa2bf6ce5968189f0561e5`, historical release |
| Task F v2 | Manifest SHA256 `c46b870bccdae1198292abc5850a83a3fa7e7f702286ceeaa07258074391801d`, current core; 12 pinned source files |
| RCAEval | `phamquiluan/RCAEval` revision `afeacb11bcc94dadfd1c8f483ee4377b2b8b614e` |
| Cases metadata | SHA256 `c49a288920dbba2e8e724679a14636d5c7eb2b45426bba14007ef79a6c0ab1bb`; projection `case,dataset,repetition,has_logs,has_traces` |
| Corrective PRE-G contract | SHA256 `07cca0a168c7db8952d0e77216e74b370b919bcf91d1586db0e183bee60edd31` |
| Corrective PRE-G receipt | SHA256 `d7cd108c4fdb5880210934d5a0a371ad49ee0bac50c8ba2d85274d2c94710f28` |

## Entry checks và preparation

**FACT:** verifier Fv2 PASS **44 references**, 11 scientific keys v1/v2 giống nguyên object, source/tests PRE-G và 11 outside-scope hashes khớp. Hai comparator đầu giống hoàn toàn; RCD v2 chỉ thêm qualification-contract provenance, mọi existing scientific setting giống v1. Metadata projection cho đúng **30 development / 60 final / 20 cells / 180 objects**. Stat đúng planned paths trong hai raw roots hiện có và proposed G root: **0/180** ở từng root; không mở raw byte/row. Đây không phải tìm kiếm mọi cache trên ổ đĩa hoặc raw compatibility validation.

RCD factory thật được cấp phát và callable được kiểm tại Python **3.9.13**, không gọi prediction. Main runtime **3.12.10**; host RAM 16,849,293,312 byte, 16 logical processors, disk free 72,476,434,432 byte tại thời điểm kiểm. Metadata receipt chính thức được tái dùng vì pinned source/test/contract/metadata hashes không drift; không có network query mới. Dung lượng remote khai báo **1,309,388,082 byte** chỉ là budget acquisition, không runtime/efficacy measurement.

Reviewer `/root/g_entry_contract` độc lập đối chiếu full TD/F/Master và registry/endpoints/failure policy, không có mutation/network/raw/campaign. Reviewer `/root/g_seal_design_review` đọc source và chạy **12/12 controller synthetic tests PASS** trên Python 3.12 `-B`; xác nhận memory-only issuance và caller-supplied root chưa chứng minh label-open order. Đây là design/scope review, chưa phải independent validity của G code chưa được tạo. PRE-G 25/F70/E34 đã PASS trong lượt corrective trước, chỉ tái dùng sau hash check; không báo là vừa chạy lại.

Biên nhận aggregate thực chạy trong phiên (giữ cả hai lỗi invocation/reporting của probe, không quy thành RCD algorithm failure):

```json
{
  "recorded_at_utc": "2026-09-30T10:21:33.877432+00:00",
  "status": "PASS_SAFE_ENTRY_IDENTITY_CHECKS__RAW_AND_DURABLE_ADMISSION_OPEN",
  "P_head": "f06f978dc4ea184990ae3119adb34e5c462c788a",
  "W_head": "62ffe22cbf5b5d4545b1c5281a25c0c90c4ac6d8",
  "release_verifier": {
    "status": "PASS",
    "checked_reference_count": 44
  },
  "scientific_identity": {
    "keys": [
      "td",
      "dataset",
      "split",
      "exposure_ledger",
      "selections",
      "r_control",
      "evaluator",
      "packet",
      "selection_policy",
      "final60_policy",
      "prohibited_runtime_rules"
    ],
    "all_eleven_equal": true,
    "comparators_existing_fields_equal": true,
    "v2_additive_reference": {
      "path": "results/task-e/e27-018-rcd-real-qualification/run-contract.json",
      "sha256": "5e0f4e4b90f9a654411a5a2edd8d22c56275080e6020a1f8d31a03e8b095d408"
    }
  },
  "current_pre_g_source_test_hashes_checked": 4,
  "outside_scope_hashes_checked": 11,
  "metadata_projection": [
    "case",
    "dataset",
    "repetition",
    "has_logs",
    "has_traces"
  ],
  "split": {
    "development": 30,
    "final": 60,
    "cells": 20,
    "telemetry_objects": 180,
    "opaque_final_roster_sha256": "f74ba6f11f55729ead8980cfc2df34be2cdb8fd570abe7656b62e31858d24bae"
  },
  "final_local_availability_by_stat": [
    {
      "root_class": "raw-samples",
      "present_final_objects_by_stat_only": 0,
      "planned": 180
    },
    {
      "root_class": "task-e-development",
      "present_final_objects_by_stat_only": 0,
      "planned": 180
    },
    {
      "root_class": "PROPOSED_NEW_G_ROOT",
      "present_final_objects_by_stat_only": 0,
      "planned": 180
    }
  ],
  "reused_metadata_receipt": {
    "sha256": "d7cd108c4fdb5880210934d5a0a371ad49ee0bac50c8ba2d85274d2c94710f28",
    "revision": "afeacb11bcc94dadfd1c8f483ee4377b2b8b614e",
    "objects": 180,
    "declared_bytes": 1309388082,
    "identity_sha256": "2b51110896bf23f6f2264e85458b814c1cd7871c50e047d814aeda3943b32d94"
  },
  "host": {
    "disk_free_bytes": 72476434432,
    "ram_bytes": 16849293312,
    "logical_processors": 16,
    "main_python": "3.12.10"
  },
  "rcd_qualification_without_predictions": {
    "python": "3.9.13",
    "issued_runner_identity_sha256": "38253ebbfaa53bdad20b66e655ade3fade363fdd9ef71ee2ab6df722ab4fac04",
    "qualified_runner_factory_intact": true,
    "issued_callable": true,
    "predictions_invoked": false
  },
  "safety": {
    "raw_bytes_or_rows_opened": false,
    "explicit_root_fault_tau_columns_read": false,
    "network_queried": false,
    "ids_paths_disclosed": false,
    "final_predictions_or_outcomes": false
  },
  "attempts": [
    {
      "attempt": 1,
      "result": "INVOCATION_ERROR: positional argument rejected; no qualification/model invoked"
    },
    {
      "attempt": 2,
      "result": "REPORTING_ERROR: factory succeeded but requested nonexistent identity key; no prediction invoked"
    },
    {
      "attempt": 3,
      "result": "PASS: exact runner issued and callable verified; canonical identity digest recorded"
    },
    {
      "check": "v1-v2 initial full comparator object inequality",
      "resolution": "v2 adds documented qualification_contract only; eleven scientific keys and existing comparator settings separately verified equal"
    }
  ]
}
```

## Campaign contract đã chuẩn bị — giữ frozen method

| Condition / policy | Registry phải giữ |
|---|---|
| Scope | Chỉ RE2-TT final60, 20 cells × 3 repeats; không mở extensions RE3/OB/SS/LEMMA, C2/H/I |
| C1 inputs | Common M/T, V trace-reference UNION query, 300/300 windows, bins10; logs không tham gia primary; không append root |
| C1 local / primary | floor .01, pool q90, fusion availablemean, temporal q90, no cap; PPR undirected damping .5 |
| Secondary | Diffusion undirected alpha .85, cùng frozen local; luôn báo, không thay primary |
| R | 256 chains/representation; 200*max(1,E) proposals; registered PCG64 seed recipe; lưu từng graph/draw/score/failure và metric từng draw rồi mean |
| Comparators | Local-MAX-MT, BARO-RANK-adapted-TD12, RCD-RCAEval-adapted-TD12; RCD seeds420/421/422 bins5 gamma5, same copied numeric input, owner-first/unknown-key coverage/one worst tie, failed-seed0, mean metric từng seed |
| C5 | Primary G/L/ALL-MTL; MT secondary và TV spatial secondary; lambda10, q.95, dùng nguyên 8 numeric thresholds đọc từ frozen manifest, không chép/refit/chọn lại |
| C5 time / integrated | bins5, fit24/cal12, warmup180, lag1, minfit18/mincal9, floor .01/1e-12, residual floor .01, streak3/refractory300; trigger diagnosis past-only360/60, t<360 giữ INSUFFICIENT_HISTORY, first post-injection result/failure/absent0; no R/comparator per trigger |
| Primary endpoint | Tie-aware MRR/60; Hit1/3/5,NDCG5 secondary; missing root/method failure0; valid all-tie không là failure; mọi planned case/condition giữ mẫu số |
| Contrasts / uncertainty | DeltaL O-L, DeltaR O-meanR; 50,000 paired 20-cell block bootstrap/all3 repeats, PCG64seed20260926, type7 .0125/.9875, family2, delta .05; root/fault leaveouts, scatter/repeat ranges; conditional uncertainty, không population significance |
| C5 endpoints | Planned60 macro P/R/F1, raw exceedance trước persistence; straddling bins exclude+count, scored/unavailable/failed/regime coverage; cả observed và finite-scored normal duration; firstscore185/firsttrigger195, trigger at tau là pre-injection |
| Validity / limits | Shared preprocessing >10%, signal starvation >=80% hoặc L/O/R arm execution/numeric failure => attribution INCONCLUSIVE; mobility >=32 distinct draws, median overlap <=.8, >=80% cases và >=50% evaluator root strata cho strong arrangement; không loại ca hay tune sau outcomes |
| Failure / cost | Planned failures0, remaining R draws0 sau timeout; timeout max(300s,10*successful development max aggregate case time) phải được tính từ receipt trước run; cold I/O/shared prep/control/fit/prediction cost riêng |

Conditions được đăng ký trước outcomes; không có kết quả model final ở bảng này. C3 supporting operation evidence giữ nguyên, scored operation/C4 placement ablations vẫn theo disposition đã khóa. Chính sách support/negative/inconclusive/invalid đọc TD §9, không tự chọn gate khi có điểm. Mọi actual G implementation phải đối chiếu bảng này với manifest/TD bằng code, không dùng prose thay configuration nguồn.

## Kế hoạch triển khai cụ thể — CANDIDATE, cần duyệt impact map

Thêm boundary G ở W, giữ byte F/E/PRE-G. `source_admission` tự tạo allowlist từ pinned projected metadata và official descriptors; chỉ nhận đúng metrics/traces/logs, tự rehash trước parse, tự tạo audit, không tin raw dict/cờ do caller khai. G tái sử dụng đúng frozen bundle/replay functions sau admission riêng, không đổi development qualification của F. Final raw schema/clock/joins/masks và prefix conversion phải có audit actual-use; các ca lỗi giữ failure/availability theo protocol, không âm thầm bỏ hoặc sửa dữ liệu.

`controller` chỉ có entry/preparation command ở phase này: default synthetic/development; raw audit phải có explicit permission trong contract. Không final model/prediction/label path được chạy bởi entry command. Chuẩn bị registered condition matrix và genuine RCD worker invocation cho review; actual campaign vẫn cần lệnh riêng. Worker chỉ nhận arrays/masks/indices/config, không mounted labels, source paths hoặc case IDs.

`provenance` lưu exact numerical outputs (gồm `R_scores` và mọi draw/failure; packet summary không đủ), flush/fsync/readback và xác minh completeness trước label-provider callback. Không nhận caller predictions/owner map/qualification SHA để tự ký qualified. Candidate host-local trust: HMAC-SHA256 chuẩn thư viện, key ngẫu nhiên ngoài prediction bundle/Git tại host trust directory, evaluator lấy trust anchor từ cấu hình đã đăng ký trước output, không từ caller bundle. Issuer chỉ commit exact output do trusted controller execution phát hành. Đây là provenance chống ordinary caller forgery, không sandbox chống code/runtime/filesystem bị chiếm quyền. Fresh-process verification phải từ chối thiếu/sai anchor; nếu trust anchor chưa thiết lập thì fail closed, không thêm flag bỏ issuance.

Key/anchor/path không do caller chọn hoặc khởi tạo lại qua public API; tạo key exclusive, không overwrite/rotate âm thầm, khai host ACL/filesystem/runtime là trust assumptions. Evaluator cũng biết HMAC secret, nên đây không là independent cryptographic attestation. Key cho `g30-entry-validation` chỉ phát hành signed `ENTRY_SYNTHETIC_DEVELOPMENT` hoặc `ENTRY_RAW_ADMISSION` receipt đúng phase; không campaign prediction qualification. Final evaluator phải từ chối entry scope dù HMAC đúng. Campaign sau quyền riêng cần registered run/domain/phase/anchor, actual-admission digest, pinned source/config và complete planned matrix riêng.

Exact text/code map đề xuất **13 tệp** (ba tệp P đã dùng trong entry sẽ được cập nhật tiếp, mười tệp W chưa tạo/sửa):

| Root / path | Thay đổi / purpose |
|---|---|
| P `docs/research-rca/RESEARCH-DECISIONS.md` | Ghi approval mới nguyên tử khi Minh chọn scope |
| P `docs/research-rca/task-g-handoff.md` | Kết quả admission, review, OPEN và command tiếp |
| P `docs/research-rca/CURRENT-STATE.md` | Trạng thái dẫn xuất sau source/receipt/review |
| W `.gitignore` | Exclude đúng new G raw/cache root, không untrack lịch sử |
| W `scripts/task_g/source_admission.py` | Trusted allowlist/acquisition/byte/schema/clock/source admission |
| W `scripts/task_g/controller.py` | Entry-only orchestration, frozen condition preparation, worker boundary/evaluator gate |
| W `scripts/task_g/provenance.py` | Issued durable commitment/completeness/trust-anchor verification |
| W `tests/task_g/test_source_admission.py` | Wrong origin/revision/path/hash/audit/schema/clock, synthetic/development-only fixtures |
| W `tests/task_g/test_controller.py` | No final run in entry, frozen matrix, numeric-only workers, evaluator callback order |
| W `tests/task_g/test_provenance.py` | Tamper/forged scope/copy/wrong anchor/missing draws/crash/roundtrip before labels |
| W `results/task-g/g30-entry-validation/entry-contract.json` | Registration trước qualification: source/test/TD/F/metadata/config/env, final_labels=false/final_predictions=false/final_campaign=false; synthetic/development_fixture_execution=true |
| W `results/task-g/g30-entry-validation/entry-validation.json` | Actual aggregate/hash receipts, mọi failure/limitation |
| W `results/task-g/g30-entry-validation/independent-entry-review.md` | Independent code/firewall/admission review; không scientific approval |

Generated payload cũng phải nằm trong map approval: tối đa đúng **180 telemetry objects / 1,309,388,082 declared bytes** ở W `datasets/rcaeval/task-g-final/`, allowlist digest `2b51110896bf23f6f2264e85458b814c1cd7871c50e047d814aeda3943b32d94`; controller-only case locators không công bố trong handoff. Host-local secret key tại `%LOCALAPPDATA%/FlashTicketRca/task-g-trust/g30-entry-validation/issuer.key`, ngoài Git/output bundle, không in key. Bounded fixture temp/cache chỉ dưới temporary test roots hoặc ignored G cache root; không mở broader corpus hoặc cài dependency mới.

Trình tự sau approval: viết contract/source/tests → safe fixtures và forged-source/receipt probes → independent review trước final raw audit → chỉ acquisition/admission raw theo đúng quyền → independent actual-use review → receipts/handoff/current. Trước acquisition, khóa digest review pre-admission, exact reviewed source/test snapshot, prepared raw allowlist và schema projections trong contract. Source/test drift sau review làm qualification cũ mất hiệu lực; không tiếp tục dựa vào prose review cạnh code mới. Review trước raw và sau raw phải có scope/time/hash riêng; nếu bổ sung cùng tệp review/contract thì giữ exact pre-audit snapshot và hash, không viết lại lịch sử hoặc tạo circular hash dependencies. Nếu method cần đổi thì dừng quay D; nếu source/firewall không đạt thì giữ lỗi và không mở campaign. Label-provider test dùng synthetic callback/counter, không final label file. Chưa tạo/sửa bất kỳ planned W tệp/payload/key nào ở lượt này.

Validation cần hoàn tất: forged synthetic rehash/private sealer/copied qualification; wrong origin/revision/bytes/path/self-declared audit; genuine numeric-only runner + first-owner/unknown/worst-tie/zero failures/mean metric; no label callback khi write/fsync/readback/completeness/trust thất bại; same-process và fresh-process trusted receipt, unknown anchor fail; crash preservation/no overwrite; label/path remap, prefix/future-suffix và shared-observation identity trên fixtures. Regression phù hợp F/PRE-G/E và governance audit, source protection hashes, independent actual-use checks; không dùng tests để chứng nhận raw chưa đọc.

Independent document/design delta review: `/root/g_entry_contract` xác nhận registry/configs/metrics/mẫu số giữ TD/F; precision của INCONCLUSIVE và current status được sửa theo nguồn. `/root/g_seal_design_review` xác nhận candidate HMAC khả thi trong trusted-host scope; các clarification anchor/no arbitrary signing/create-exclusive, signed phase, pre-admission review digest và fixture permissions phía trên đã được tích hợp. Hai nhận định này không đóng raw admission hoặc qualification của code chưa tạo.

## OPEN và authorization boundary

1. Actual-use final raw source/schema/clock/joins/window/masks/adapter admission chưa đạt. Task E loader chỉ cho development30; PRE-G raw-audit helper chỉ cho `synthetic-` IDs. Không bỏ guard hoặc đổi qualification scope của F để ép nhận final60.
2. PRE-G seal chỉ có issuance trong cùng interpreter; JSON SHA và timestamp không chứng minh durable producer provenance. G cần trusted producer commitment và evaluator xác minh trước khi **gọi** nguồn nhãn, không nhận `root_index` đã nạp sẵn.
3. Independent validity của actual G controller/firewall phải có trước campaign. Formal five-distinct-reviewer `OPEN / NOT FACTUALLY CERTIFIED`; historical E1 exposure và các TD/F limitations giữ nguyên.
4. Exact impact map triển khai G hơn ba tệp cần Minh duyệt theo AGENTS/skill. Quyền tải/đọc final60 telemetry ở input-admission gate và quyền campaign/label opening phải phân biệt rõ, không suy rộng từ RCA-071.

NEXT EXACT ACTION: Minh duyệt exact map 13 tệp + generated payload/key và cho phép acquisition/đọc **telemetry-only final60** tại admission gate nếu chọn triển khai bước tiếp. Giữ explicit root/fault/tau labels/answer/outcomes/final predictions/campaign đóng trong phase entry. Không xin lại RCA-071; đây là approval mới cho mutation rộng và raw-dependent scope đã trình cụ thể.

Phép thử độc lập hai bộ: tạo tác thuộc RCA, nguồn hình thành là RCA-071/TD/F/PRE-G và Master G; không dùng mô hình hệ thống để sinh quyết định RCA hay tạo yêu cầu app/API/schema/Saga. Kết luận kiểm tài liệu không là human gate acceptance.

Nội dung cho báo cáo: giữ các boundary chưa đạt và kết quả kỹ thuật đúng mức; phân biệt artifact integrity, source admission, durable producer provenance và quyền mở nhãn. Không lấy source metadata làm efficacy.
