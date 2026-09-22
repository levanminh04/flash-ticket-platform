# RCA — Artifact map và audit hệ tài liệu

Chủ sở hữu: **Minh**. Ngày **2026-09-22**. Loại `CANONICAL_SYNTHESIS` về **định vị/quyền nguồn**, không sở hữu quyết định khoa học hoặc trạng thái chạy. Inventory được lập trước khi tổ chức lại; [snapshot 59 tệp](D:/Project/flash-ticket-rca-research/program-review/pre-change-inventory.json) giữ hash/size của các tài liệu và audit scripts liên quan. Đây không phải inventory/re-audit mọi byte raw trên máy.

## 1. Hai root và một quyền nguồn cho mỗi loại thông tin

| Logical root | Binding hiện tại | Trách nhiệm |
|---|---|---|
| `P` | `D:/Project/flash-ticket-platform` | Quyết định, phạm vi, contract, synthesis, handoff, report-facing conclusions và trạng thái bền vững |
| `W` | `D:/Project/flash-ticket-rca-research` | Datasets, môi trường, mã/audit, manifests, reviewer chi tiết, kết quả chạy và assets nặng |

**Đánh giá bố trí — CANDIDATE organizational disposition được áp dụng trong phạm vi nhiệm vụ điều phối được giao:** giữ hai root hiện có. W đã có môi trường/raw/audit riêng; chuyển vào P không tăng validity và gây gánh version/backup. P là nơi nhóm đọc quyết định và viết luận văn; external-only decisions làm phiên mới mất quyền nguồn. Không có bằng chứng lợi ích từ việc gộp. Đây là kết quả đánh giá cách tổ chức của agent theo U22 §6, **không gán nguyên phương án cho lời Minh đã xác nhận**; nghĩa vụ xác nhận nằm ở RCA-016. Không di chuyển/xóa tệp trong lần này.

`RESEARCH-DECISIONS` sở hữu câu quyết định con người; C Phase 2 sở hữu RQ/hypothesis/evaluation contract; MASTER sở hữu đường thực hiện; CURRENT sở hữu live state; BOOTSTRAP chỉ dẫn đọc; MAP sở hữu định vị. B digest là dẫn xuất, B CLOSED/raw evidence vẫn phân xử data facts. Không dùng thứ bậc quyết định để bác sự thật quan sát.

Các đường dẫn dưới đây là root-relative. Trong P, `R/` viết tắt `docs/research-rca/`; nhãn L1 chỉ tệp thuộc minimum pack, L2 là mở có mục đích, L3 là bằng chứng chi tiết. Cột **lưu trữ** ghi payload nhỏ/lớn và nơi lưu; **không** tuyên bố đã commit, push hoặc có backup. Đường dẫn tương lai chỉ là output contract, không phải file đã có.

## 2. Canonical artifacts và nguồn quản trị liên quan

| ID | Mục đích / authority class | Status / owner of truth | Root / path | Producer → consumers | Lưu trữ / fresh session |
|---|---|---|---|---|---|
| DIR-DT18 | Nhiệm vụ chính thức · CANONICAL_DECISION | USER_CONFIRMED, Minh 18/09 | P: `docs/evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md` | Minh → A–K | Nhỏ, repo; **L1** |
| GOV-ROLES | Trách nhiệm nhóm · CANONICAL_DECISION | Hiện hành, không đổi | P: `docs/project/roles.md` | Minh → H/J/K, system | Nhỏ, repo; L2 khi phân công |
| GOV-PROJECT | Đăng ký quyết định dự án · CANONICAL_DECISION | Hiện hành; đăng ký RCA subregister, không chép lại 17 dòng | P: `docs/project/decision-register.md` | Project → mọi task | Lớn về văn bản, repo; L2 đúng ID |
| RCA-DEC | Quyết định con người RCA · CANONICAL_DECISION | RCA-001–017 USER_CONFIRMED; sở hữu câu quyết định | P: `R/RESEARCH-DECISIONS.md` | C Phase 2 → D–K | Nhỏ, repo; L2 xác nhận/đổi quyết định |
| RCA-PLAN | Lộ trình duy nhất · ROADMAP | MRP-v1; tổ chức công việc, không duyệt thuật toán | P: `R/MASTER-RESEARCH-PROGRAM.md` | C/program → D–K | Repo; **L1 các mục chỉ định**, không đọc hết mỗi phiên |
| RCA-STATE | Checkpoint toàn chương trình · CURRENT_STATE | Hiện hành duy nhất | P: `R/CURRENT-STATE.md` | Task đang bàn giao → phiên sau | Nhỏ, repo; **L1** |
| RCA-BOOT | Cách phục hồi · CURRENT_STATE | Navigation, live values lấy từ RCA-STATE | P: `R/SESSION-BOOTSTRAP.md` | Program → mọi phiên | Nhỏ, repo; **L1** |
| RCA-MAP | Ownership/path inventory + document audit · CANONICAL_SYNTHESIS | Tệp này; không duplicate payload | P: `R/ARTIFACT-MAP.md` | Program → mọi task | Repo; L2 tìm artifact |
| RCA-INDEX | Điều hướng bộ RCA · CURRENT_STATE | Chỉ dẫn tới BOOT/STATE, không second roadmap | P: `R/README.md` | Program → người đọc mới | Nhỏ, repo; entry tùy chọn |
| A-SYN | Prior-work evidence/task distinctions · CANONICAL_SYNTHESIS | Work COMPLETE; document DRAFT, cutoff 20/09 | P: `R/task-a-ban-do-bang-chung-doc-lap.md` | A → C/D/J | Repo; L2 đoạn comparator/claim cần dùng |
| B-DIGEST | Data/GT/leakage tóm tắt bền vững · CANONICAL_SYNTHESIS | Dẫn xuất của B CLOSED, không audit mới | P: `R/task-b-dataset-capability-summary.md` | B/program → D–I | Nhỏ, repo; **L1** |
| C-P1 | Shortlist và phân xử Phase 1 · CANONICAL_SYNTHESIS | Phase 1 COMPLETE, historical human-choice status; notice trỏ Phase 2 | P: `R/task-c-independent-research-shortlist.md` | C Phase 1 → C Phase 2/D/J | Repo; L2 lý do/claim gate cụ thể |
| C-P2 | Research Decision Lock + C handoff · CANONICAL_DECISION | TC-P2-v1, COMPLETE theo U22; exact methods OPEN | P: `R/task-c-research-decision-lock.md` | C Phase 2 → D–K | Repo; **L1** |
| REF-SOURCES | Danh mục primary sources chung · DETAILED_EVIDENCE | Một source register cho một luận văn, không đổi lượt này | P: `docs/research/source-register.md` | Survey → A/D/J | Repo; L2 nguồn được trích |
| RCA-R0 | Cửa nghiên cứu → hệ thống · CANONICAL_SYNTHESIS | R0-v0.6 DRAFT; §3/OPENs giữ nguyên, notice hiện hành bổ sung | P: `R/R0-boi-canh-va-rang-buoc.md` | Research → system gates/H | Repo; L2 khi qua cửa |
| SYS-RCA-DOOR | Cửa hệ thống → nghiên cứu · CANONICAL_SYNTHESIS | Hiện hành; giữ nguyên | P: `docs/project/lien-ket-rca.md` | System → H; independence checks | Repo; L2 khi qua cửa |
| SYS-WORKFLOW | Master workflow chung/giai đoạn hệ thống · ROADMAP | Hiện hành; nhịp RCA trỏ RCA-PLAN | P: `docs/quy-trinh-lam-viec.md` | Governance → system, đối chiếu RCA | Repo; theo governing instructions, không RCA roadmap thứ hai |
| REPORT-OUTLINE | Khung tổng hợp luận văn · REPORT_PRESENTATION | Sống, không tự chốt mẫu khoa; update nguồn RCA | P: `docs/report/report-outline.md` | Project/J → K | Repo; L2 J/K |
| REPORT-STYLE | Quy ước trình bày · REPORT_PRESENTATION | Hiện hành cho style; cây file/status nhúng không phải live state | P: `docs/tang-c-quy-uoc-trinh-bay.md` | Project → J/K | Repo; L2 khi soạn báo cáo |

### Nền/lịch sử được giữ nguyên

| ID | Mục đích / authority class | Status / root-relative path | Producer → consumers | Lưu trữ / fresh session |
|---|---|---|---|---|
| HIST-A7 | Khái niệm · HISTORICAL đối với lựa chọn protocol hiện hành | P: `R/A7-khai-niem-rca.md`, DRAFT nguồn nền | RCA trước Task A → D/J tra có mục đích | Nhỏ, repo; không L1 |
| HIST-A8 | Khảo sát dataset cũ · HISTORICAL | P: `R/A8-khao-sat-dataset.md`; B CLOSED sở hữu capability mới | RCA trước B → truy vết | Repo; không L1 |
| HIST-A9 | Đề xuất metric/protocol cũ · HISTORICAL | P: `R/A9-do-do-thuc-nghiem.md`; không tự khóa metric của D | RCA trước C → D nếu biện minh | Repo; không L1 |
| HIST-A10 | Khảo sát cơ chế cũ · HISTORICAL | P: `R/A10-khao-sat-phuong-phap.md`; không thay A synthesis/current primary source | RCA trước A → truy vết | Repo; không L1 |
| HIST-E1 | Kiểm 11 CSV/990 result rows và proposal MyRCA cũ · HISTORICAL | P: `R/E1-kiem-dinh-rcaeval-va-kha-thi-myrca.md`; giữ giá trị đúng snapshot, không Task E tương lai | RCA cũ → trace claims/report history | Repo; không L1 |

HISTORICAL ở bảng này không tuyên bố mọi định nghĩa/số đo cũ sai. Nó giới hạn quyền dùng để **chọn hướng, dataset capability hoặc protocol hiện hành**. Không xóa/copy lại nội dung. Mã A7–A10 trùng Tầng A được giữ với full path khi trích bên ngoài bộ; không thực hiện đổi tên kéo theo hàng chục tham chiếu.

## 3. Execution/evidence ở W

Mỗi collection trong bảng là một major artifact có manifest/đường dẫn thành viên rõ; không chép hàng nghìn file raw vào tài liệu điều hướng.

| ID | Mục đích / authority class | Status / path trong W | Producer → consumers | Lưu trữ / fresh session |
|---|---|---|---|---|
| W-ENTRY | Cửa vào execution storage · CURRENT_STATE | `README.md`, chỉ trỏ canonical state/program | Program → mọi người dùng W | Nhỏ, external; không live state thứ hai |
| B-CLOSED | Báo cáo capability đầy đủ · CANONICAL_SYNTHESIS về dữ liệu | `dataset-audit/TASK-B-RCAEval-audit.md` **§12 CLOSED** | B → C/D/E khi cần chi tiết | External text; L2/3, B-DIGEST dùng thường ngày |
| B-HISTORY | Lịch sử đóng B · HISTORICAL | `dataset-audit/TASK-B-RCAEval-audit.pre-close.md`; `dataset-audit/task-b-resume-state.md` | B → provenance | External; không chỉ dẫn task hiện tại |
| B-SCOPES | Phạm vi retrieval/audit bổ sung · DETAILED_EVIDENCE | `dataset-audit/TASK-B2-RE2TT-trace-scope.md`; `dataset-audit/TASK-B2B-RE2TT-MULTIMODAL-SCOPE.md` | B2/B2B → C/D/E | External; L3 scope dispute |
| B-META | Official metadata/version · RAW_OR_MACHINE_EVIDENCE | `datasets/rcaeval/metadata/cases.parquet`; `metadata-provenance.json` cùng thư mục | B → deterministic checks/E | External data; không đọc L1 |
| B-SAMPLES | Raw selective cases · RAW_OR_MACHINE_EVIDENCE | `datasets/rcaeval/raw-samples/`, thành viên theo `raw-download-manifest.json` và B2B manifest bên dưới | B/B2B → E có quyền dùng | Lớn/local; không full corpus; L3 |
| B-DOWNLOAD | Danh tính raw sample · RAW_OR_MACHINE_EVIDENCE | `datasets/rcaeval/raw-samples/raw-download-manifest.json`; `audits/rcaeval/b2b-re2tt-multimodal-download-manifest.json` | B/B2B → preservation/retrieval | External nhỏ; L3 retrieval/integrity |
| B-MACHINE | Kết quả check deterministic · RAW_OR_MACHINE_EVIDENCE | `audits/rcaeval/*.json`: metadata-profile/invariants; raw-parquet-inventory/raw-time-alignment/raw-manifest-verification; main-log-multimodal-evidence; re2tt-target-candidate-coverage; re2tt-trace-full-subset-{inventory,audit,progress}; reviewer evidence JSON | B → C/E verifier | External; L3 specific count/schema |
| B-PLAN | Chọn case gốc · DETAILED_EVIDENCE | `audits/rcaeval/raw-sample-plan.md` và `.json` | B → provenance/exposure ledger D | External; L2/3 |
| B-REVIEWS | A–I/full-trace/resource reviews · DETAILED_EVIDENCE | `audits/rcaeval/subagent-*.md`, danh tính role trong B §12 | B → audit disputes | External; không rerun/đọc hết mặc định |
| B-CODE | Audit scripts · IMPLEMENTATION | `scripts/audit/` (22 tệp trong snapshot trước đổi) | B → deterministic reproduction khi có mục đích | External; không method implementation D/F |
| ENV-B | Python environment · IMPLEMENTATION | `.venv/`, dependency identity trong B environment evidence | B → E đánh giá tái sử dụng | Lớn/local; không portable backup tự thân |
| C-BLIND | Sáu reviewer độc lập · DETAILED_EVIDENCE | `task-c/phase-1-independent-candidates/reviewer-{a,b,c,d,e,f}.md` | C discovery → C-P1 | External frozen; L3, không restart |
| C-LEDGER | Sources/claim boundaries · DETAILED_EVIDENCE | `task-c/task-c-evidence-ledger.md` | C → D claim dispute | External; L2 đúng ID |
| C-NORM | 12 proposals → 5 candidates · DETAILED_EVIDENCE | `task-c/task-c-normalized-candidate-universe.md` | C → C-P1 history | External; L3 |
| C-VERIFY | TV-01–04 primary-source correction · DETAILED_EVIDENCE | `task-c/task-c-targeted-verification.md` | C → A/D precise claim | External; L2 khi source affected |
| C-GATES | Feasibility/literature gates · DETAILED_EVIDENCE | `task-c/task-c-feasibility-gate.md`; `task-c/task-c-literature-positioning.md` | C → C-P1/D | External; L2 khi chọn comparator/design |
| C-XREVIEW | Evidence disagreement resolution · DETAILED_EVIDENCE | `task-c/task-c-evidence-cross-review.md` | C → C-P1/D | External; L3 |
| C-RED | Adversarial survivor review · DETAILED_EVIDENCE | `task-c/task-c-red-team.md` | C → C-P1/D/J limitations | External; L2 đúng RT finding |
| C-MACHINE | 65-input snapshot + machine checks · RAW_OR_MACHINE_EVIDENCE | `task-c/task-c-input-inventory.json`; `task-c/task-c-machine-evidence-check.json` | C → integrity dispute | External; giữ snapshot cũ |
| C-VALIDATION | Phase 1 completion validation · RAW_OR_MACHINE_EVIDENCE | `task-c/task-c-final-validation.json`; `task-c/task-c-governance-validation.txt` | C → provenance | External; không sửa hash cũ khi thêm notice mới |
| C-RESUME | Checkpoint lịch sử/quota · HISTORICAL | `task-c/task-c-resume-state.md`; header Phase 2 hiện hành trỏ RCA-STATE | C → recovery/history | External; không roadmap cạnh tranh |
| PROGRAM-REVIEW | Neutral proposal + challenge + closure · DETAILED_EVIDENCE | `program-review/independent-workflow-review.md` | Program independent reviewer → main adjudication | External; L2/3, không scientific GT |
| PROGRAM-BEFORE | Preservation baseline · RAW_OR_MACHINE_EVIDENCE | `program-review/pre-change-inventory.json` | Program → validator | External; 59 relevant files, không raw corpus |
| PROGRAM-CHECK | Package validation · RAW_OR_MACHINE_EVIDENCE | `program-review/program-package-validation.json`; `program-review/program-governance-validation.txt`; `program-review/validate-program-package.ps1` | Program → handoff/recovery | External; kiểm links/UTF-8/preservation/contracts |

Không có Task D–K experiment outputs được tuyên bố đã sinh ở bảng này. `results/` hiện là nơi dự kiến nhận runs; `EXPERIMENT_OUTPUT` chỉ áp dụng khi có run thật. Reviewer prose/machine audit không phải benchmark results. Không tìm thấy bản duplicate byte-for-byte nào cần xóa trong phạm vi tổ chức; chồng lấn **quyền diễn giải** được xử lý dưới đây, không đồng nhất với trùng file.

## 4. Hợp đồng định vị artifacts tương lai

Các hàng này **PLANNED, NOT CREATED**. Producer chỉ tạo khi được giao task. `P/R/` là canonical; W code/run có thể lớn và cần manifest/availability. Fresh session đọc canonical handoff của task hiện hành; mở W chỉ theo run ID.

| ID / producer | Canonical path, class | Execution path, class | Consumers |
|---|---|---|---|
| D | `R/task-d-method-and-experiment-specification.md` CANONICAL_DECISION (DRAFT đến khi người có quyền duyệt); `R/task-d-handoff.md` CURRENT_STATE | Review/evidence cần thiết trong W, chưa tạo folder rỗng | E/F/G/H/I |
| E | `R/task-e-handoff.md` CANONICAL_SYNTHESIS | `baselines/`, `environments/` IMPLEMENTATION; `results/task-e/` EXPERIMENT_OUTPUT | F/G/J |
| F | `R/task-f-handoff.md` CANONICAL_SYNTHESIS | `src/rca/`, `tests/`, `configs/` IMPLEMENTATION; `results/task-f/` EXPERIMENT_OUTPUT | G/H/I/J |
| G | `R/task-g-handoff.md` CANONICAL_SYNTHESIS | `results/task-g/<run-id>/` EXPERIMENT_OUTPUT + manifests | H/I/J/K |
| H | `R/task-h-transfer-protocol.md` CANONICAL_DECISION; `R/task-h-handoff.md` CANONICAL_SYNTHESIS | `adapters/flashticket/` IMPLEMENTATION; `results/task-h/` EXPERIMENT_OUTPUT | I/J/K |
| I | `R/task-i-explanation-protocol.md` CANONICAL_DECISION; `R/task-i-handoff.md` CANONICAL_SYNTHESIS | `src/explanation/` IMPLEMENTATION; `results/task-i/` EXPERIMENT_OUTPUT | J/K |
| J | `R/task-j-thesis-evidence.md` CANONICAL_SYNTHESIS; `R/task-j-handoff.md` CURRENT_STATE | `results/task-j/` REPORT_PRESENTATION + reproducibility artifacts | K/report |
| K | `R/task-k-defense-readiness.md` REPORT_PRESENTATION; `R/task-k-handoff.md` CURRENT_STATE | `results/task-k/` REPORT_PRESENTATION; demo/rehearsal evidence | Minh/defense |

Tên và exact outputs của task được quản lý trong MASTER §4; map chỉ giúp định vị. D/H/I protocol chưa được approved bởi việc khai path. Báo cáo/capability handoff phải chỉ ra version/hash của W evidence, không chỉ link folder không xác định run.

## 5. Kết quả audit hệ tài liệu và xử lý

| Vấn đề quan sát | Tác hại cho phiên mới | Xử lý trong lần này / phần giữ nguyên |
|---|---|---|
| C Phase 1 và resume còn “chờ chọn C1/C2” | Mở lại quyết định Minh đã chọn | C-P2/RCA-DEC mới; Phase 1 chỉ thêm notice; resume thêm current header và phân lịch sử |
| README hướng đọc A7–A10/E1 như authority mới nhất | Lấy proposal MyRCA/metric cũ thay C1/D | Chuyển entry sang BOOT/STATE/C-P2; ghi phạm vi nền/lịch sử của các phiếu cũ |
| R0 §1/2 nói E1 là kết quả mới nhất tại 18/09 | R0 bị dùng làm scientific checkpoint | Thêm notice dated 22/09; §3 cửa hệ thống và các OPEN giữ nguyên |
| Master workflow gọi mình tài liệu duy nhất theo hằng ngày | Có vẻ RCA phải chờ bảy giai đoạn hệ thống | Làm rõ đúng scope hệ thống và dẫn RCA master; không thay system gates |
| Report outline còn chỉ E1/A7–A10 và MyRCA | Viết method/kết quả hiện tại bằng lịch sử | Thay nguồn RCA hiện hành, nêu C1 lock và exact method ở D; giữ outline toàn đề tài |
| Quyết định nằm trong yêu cầu chat/attachment | Mất attachment là mất trí nhớ quyết định | RCA-DEC lưu nội dung nguyên tử; project register đăng ký subregister; attachment/hash là provenance phụ |
| B capability chỉ có báo cáo dài tại W | Phiên mới phải đọc audit chi tiết để biết GT | B-DIGEST lưu compact facts ở P, giữ authority/dispute routing về B CLOSED |
| Dataset, reviewers, scripts chỉ ở W; synthesis chỉ ở P | Không biết tìm evidence/run nào | Map IDs/two roots; không copy payload, không claim đã backup |
| Nhiều “CURRENT AUTHORITY” trong resume lịch sử | Tiếp tục nhầm worker/task đã xong | Header mới trỏ CURRENT-STATE, bao toàn bộ nội dung cũ dưới phần historical; giữ nội dung nguồn |
| A7–A10 trùng mã Tầng A; E1 dễ nhầm Task E | Mở sai độ đo/tài liệu | Full path khi trích; E1 được ghi lịch sử, Task E mới có tên/handoff riêng; không mass rename |
| Cây/status ví dụ trong Tầng C và phiếu nền cũ | Dùng tài liệu trình bày để xác định live state | Ghi authority class rõ; không sửa toàn bộ tài liệu hệ thống trong RCA-only task |
| Audit đầy đủ bị hiểu là toàn corpus đã lưu | Tới E thiếu files hoặc tải thừa | B-DIGEST nói rõ selective storage/range scan; acquisition chỉ khi bước execution được giao |

Không có `DUPLICATE_OR_REDUNDANT` nào được chọn để xóa: mục tiêu là bỏ quyền nguồn cạnh tranh, giữ provenance. “Canonical” trong P là trách nhiệm quản lý; chưa đồng nghĩa mọi tài liệu đã được người hướng dẫn duyệt.

## 6. Workflow review — main adjudication bằng nguồn

[Independent review](D:/Project/flash-ticket-rca-research/program-review/independent-workflow-review.md) dùng một reviewer, không recursive spawn. STEP 1 không được cung cấp layout/A–K của U22; đề xuất đã lưu trước khi mở U22. Hash prefix STEP 1: `080F92C12E8CD8B5D5E3648C5EBC82E81A3D7FADEE16F8AB547D9E3B30E065BC` (30.673 bytes). STEP 2 sau đó kiểm đầy đủ proposal. Quota có ngắt worker nhưng STEP 2 đã lưu; phục hồi chỉ kiểm sửa, không restart review. Main đã đọc findings và phân xử như sau:

| Finding / disagreement | Căn cứ phân xử | Kết quả main |
|---|---|---|
| S2-01: pre-G freeze chưa nêu đủ pre-pilot exposure | U22 scientific validity; E/F có thể đã dùng outcomes chọn thiết kế | **ACCEPTED / RESOLVED:** MASTER §2 thêm exposure boundary trước E, ghi cả audit/development exposure và không tái gắn nhãn “untouched”; E entry dẫn rule. Exact split vẫn D |
| S2-02: “giữ hai root” bị ghi như lựa chọn cụ thể human-confirmed | U22 §6 yêu cầu đánh giá, §11 cho phép challenge | **ACCEPTED / RESOLVED:** RCA-016 chỉ ghi nghĩa vụ explicit; §1 map ghi đánh giá tổ chức của agent, không sửa nguồn con người |
| S2-03: “C2 lock” trùng optional research C2 | C2 đã là candidate temporal; Phase 2 khác nghĩa | **ACCEPTED / RESOLVED:** tên navigation “Task C Phase 2”; version `TC-P2-v1` |
| STEP 1 muốn mọi quyết định ở sổ chung | U22 §8 yêu cầu sổ RCA gọn; AGENTS cần durable project registration | **ADAPTED:** sổ chung đăng ký IDs/owner/source/path, RCA subregister sở hữu 17 statements. Không duplicate hoặc bỏ trace dự án; reviewer STEP 2 đồng ý sửa khuyến nghị |
| STEP 1 muốn current entry tại W | U22 §6/8/9 cần current state trong canonical repository | **NOT ADOPTED:** CURRENT tại P; W README/checkpoints chỉ route. Reviewer STEP 2 chấp nhận lý do; chi tiết run vẫn W |
| Tên A–K dễ bị hiểu thành tuần tự | I dùng F public packets, J/K cần tích lũy sớm; U22 D–G độc lập target | **ADAPTED:** DAG + giải thích I trước H, J draft từ D, H-only readiness; không xóa trách nhiệm A–K |
| Context pack nhiều nguồn/đọc 35 KB master mỗi phiên | U22 §3/8 staged retrieval | **RESOLVED:** sáu tệp L1, master đọc sections, registry/evidence chỉ đúng câu hỏi; không default 65 inputs |
| Nguy cơ reviewer/gates lặp lại cho mỗi edit | U22 §10/11 chỉ dùng independence ở high-impact decisions | **RESOLVED:** MASTER §6 review đúng risk; evidence review đã có được tái dùng, chỉ thay đổi ảnh hưởng mới mở kiểm lại |

Reviewer không phát hiện fatal structural defect trong draft đã đọc. Điều này chỉ là đánh giá tổ chức, không chứng nhận hiệu quả RCA, novelty hoặc approval Task D. Các kiểm closure/link/preservation thực tế được ghi PROGRAM-CHECK, không suy từ reviewer consensus.

## 7. Quy tắc duy trì và kiểm liên kết

Đổi quyết định tại RCA-DEC trước, contract/source kế tiếp, handoff/map/state sau. Sửa một số đo từ W evidence rồi mới sửa digest/claim tại P. Không sửa snapshot validation cũ để hợp thức hóa hash thay đổi hợp lệ. Mỗi tệp mới phải có purpose/owner và consumer, không tạo “summary of summary” không cần thiết.

Thêm dataset/run thì ghi manifest ở W và logical ID/version tại handoff; backup/location/access phải kiểm thật trong E/J/K. Bản đồ này không thay giấy phép dữ liệu và không nhận các local paths dùng được trên máy khác khi chưa remap root.
