# U26 — Yêu cầu redesign RCA Task D

Chủ nguồn: Lê Văn Minh. Tiếp nhận 2026-09-26. Loại CANONICAL_SOURCE; FORMATION. Bản dưới giữ nguyên nội dung attachment UTF-8, chỉ thêm header phân biệt provenance. SHA-256 attachment: 9F86A703CB4F6381BAF09DA0370857A0EE8D70432524379B8F1F4C8F7E0E592C. Các quyết định hiện tại ở §3 và quyền chỉnh tài liệu §15 là lời Minh; thuật toán do agent chọn vẫn CANDIDATE. Đây không là phê duyệt Task D hoặc quyền chạy E/commit/push.

---

# MISSION — Independent Pre-Approval Redesign / Red-Team of RCA Task D

Bạn đang làm việc cục bộ với hai workspace:

- Canonical project repository:
  `D:\Project\flash-ticket-platform`
- RCA research/evidence workspace:
  `D:\Project\flash-ticket-rca-research`

Mục tiêu của phiên này là **đánh giá lại từ nền móng và sửa Task D trước khi tôi phê duyệt để chuyển sang Task E**.

Đây không phải yêu cầu “polish TD-v1.1” hoặc “sửa vài tham số”. Hãy coi đây là một **independent pre-approval methodological audit + redesign**.

Tôi muốn giảm tối đa xác suất đi sâu sang E/F/G rồi mới phát hiện rằng:

- bài toán nghiên cứu bị hiểu sai;
- phương pháp lệch định hướng giảng viên;
- anomaly score không có cơ sở;
- graph operator không phù hợp;
- hyperparameter được đặt tùy ý;
- dataset không đủ khả năng kiểm chứng;
- hoặc một kết quả âm bị diễn giải nhầm thành “graph vô ích”.

Task D hiện tại **chưa được tôi duyệt**. Vì vậy đây chính là thời điểm được phép thay đổi mạnh nếu bằng chứng cho thấy cần thiết.

Không bắt đầu Task E trong phiên này.

---

# 1. AUTHORITY, WORKSPACE AND FIRST ACTIONS

Trước mọi kết luận hoặc chỉnh sửa:

1. Làm việc từ:
   `D:\Project\flash-ticket-platform`

2. Đọc đầy đủ root:
   `AGENTS.md`

3. Áp dụng repository skill:
   `.agents\skills\govern-capstone-work\SKILL.md`

4. Đọc các authority/gate mà skill yêu cầu, đặc biệt:
   `.agents\skills\govern-capstone-work\references\project-authority-and-gates.md`

5. Kiểm tra trạng thái Git của **cả hai repository** bằng các lệnh ngắn gọn tương đương:

   - `git status --short`
   - `git branch --show-current`
   - `git rev-parse HEAD`
   - kiểm tra local refs/history liên quan Task D nếu cần.

Không mặc định `main` chứa trạng thái RCA mới nhất.

Đã từng có tình trạng platform default branch và RCA research workspace không cùng một snapshot Task D. Vì vậy phải xác định **source of truth từ trạng thái local hiện tại**, Git history và các canonical status/handoff files trước khi làm việc.

Không reset, clean, stash, checkout đè hoặc xóa thay đổi hiện có của người dùng.

Nếu có unrelated local changes, bảo tồn chúng.

Nếu có xung đột source-of-truth thực sự không thể phân xử bằng authority/history hiện có, ghi rõ blocker. Không tự chọn phiên bản thuận tiện.

---

# 2. CONTEXT LOADING — PROGRESSIVE DISCLOSURE, NOT GREP-DRIVEN REASONING

Không đọc repository bằng cách grep vài từ khóa rồi ghép kết luận.

`rg`, search hoặc grep được dùng để:

- lập inventory;
- tìm file;
- tìm nơi một khái niệm được định nghĩa;
- lần theo references.

Nhưng khi một tài liệu trở thành evidence cho một quyết định quan trọng, phải đọc **đủ toàn bộ tài liệu hoặc toàn bộ section liên quan trong ngữ cảnh**, không kết luận từ search snippet.

Không cần đọc mọi file trong hai repository một cách máy móc. Hãy dùng progressive disclosure: đọc sâu những nguồn có quyền quyết định hoặc có evidence liên quan đến vấn đề đang xét.

Bộ tài liệu tối thiểu phải hiểu trước khi sửa D gồm:

## Platform / canonical RCA control

- `AGENTS.md`
- `.agents/skills/govern-capstone-work/SKILL.md`
- `docs/evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md`
- nguồn hướng dẫn giảng viên hiện được lưu cho RCA
- `docs/research-rca/SESSION-BOOTSTRAP.md`
- `docs/research-rca/CURRENT-STATE.md`
- `docs/research-rca/RESEARCH-DECISIONS.md`
- `docs/research-rca/MASTER-RESEARCH-PROGRAM.md`
- `docs/research-rca/ARTIFACT-MAP.md`
- `docs/research-rca/task-c-research-decision-lock.md`
- `docs/research-rca/task-d-method-and-experiment-specification.md`
- `docs/research-rca/task-d-handoff.md`
- `docs/research-rca/task-b-dataset-capability-summary.md`
- Task A evidence map, đặc biệt phần technical mechanisms, graph placement, anomaly detection, RCA, datasets, metrics, limitations và prohibited claims.

## Research/evidence workspace

Đọc những evidence cần thiết trong:

- `dataset-audit/`
- `audits/rcaeval/`
- `task-c/`
- `task-d/`
- `program-review/`

Đặc biệt chú ý:

- full trace audit;
- metadata audit;
- metrics audit;
- logs/multimodal audit;
- ground-truth/leakage audit;
- graph constructability;
- methodology red-team;
- Task C independent proposals/cross-review;
- Task D evidence ledger;
- exposure ledger;
- TD-v1.1 source-first review;
- reconciliation evidence;
- delta review;
- validation artifacts.

Các path kiểu `D:/Project/flash-ticket-rca-research/...` trong platform docs phải được resolve sang workspace research thực tế, không được bỏ qua chỉ vì đó là absolute path.

---

# 3. CURRENT USER-CONFIRMED RESEARCH DIRECTION

Các quyết định dưới đây là quyết định hiện tại của tôi cho phiên redesign này.

Phải phân biệt chúng với proposal kỹ thuật mà agent sẽ tự chọn.

## 3.1 C1

Giữ **C1 là Primary Research Question**.

C1 tiếp tục nghiên cứu giá trị của observed service relations đối với known-window root-cause service ranking trong một phép so sánh công bằng.

Tuy nhiên:

**C1 không được xem là toàn bộ graph method của đề tài.**

C1 là một controlled scientific experiment về graph contribution trong RCA ranking.

---

## 3.2 C5

C5 **không thay C1 làm Primary RQ**.

Nhưng C5 phải được nâng từ một supporting capability yếu thành một:

**FIRST-CLASS GRAPH-BASED ANOMALY-DETECTION METHOD TRACK**

trong phương pháp tổng thể.

Nó phải được xem xét nghiêm túc theo định hướng giảng viên:

1. dependency graph;
2. graph-based anomaly detection;
3. public datasets;
4. comparison với RCA baselines và các metric phù hợp;
5. LLM explanation.

Không được chỉ gọi một phép smoothing là “graph-based anomaly detection” vì graph có xuất hiện trong công thức.

Phải chứng minh bằng technical lineage và mechanics rằng lựa chọn cuối thực sự là một cách dùng graph hợp lý trong anomaly detection.

Nếu fixed neighbor smoothing hiện tại đủ mạnh sau review, phải chứng minh.

Nếu không đủ, thay nó.

Không được giữ chỉ vì nó đã có trong TD-v1.1.

---

## 3.3 Prior-work-first, custom-second

Thiết kế phương pháp theo nguyên tắc:

**PRIOR-WORK-FIRST, CUSTOM-SECOND.**

Không bắt đầu bằng một công thức tự nghĩ rồi tìm paper để hợp thức hóa.

Đối với từng thành phần quan trọng, phân loại rõ:

- `DIRECTLY_ADOPTED`
- `ADAPTED_FROM_PRIOR_WORK`
- `STANDARD_STATISTICAL_METHOD`
- `STUDY_SPECIFIC_DESIGN`

Nếu là `STUDY_SPECIFIC_DESIGN`, phải có:

- lý do cần thiết;
- alternatives đã xét;
- assumption;
- failure mode;
- parameter policy;
- sensitivity plan;
- backup/robustness mechanism;
- giới hạn claim.

Không có yêu cầu phải phát minh thuật toán mới.

Ưu tiên một method có cơ sở, triển khai được và bảo vệ được hơn một method “mới” nhưng tùy ý.

---

## 3.4 Development labels

Tôi **cho phép sử dụng root/fault labels trên DEVELOPMENT DATA** để:

- chọn method variant;
- chọn hyperparameter;
- calibration có giám sát nếu phương pháp yêu cầu và được khai báo;
- model selection;
- sensitivity-informed selection.

Nhưng:

- labels không được trở thành runtime input;
- final evaluation data không được dùng để tune;
- mọi search space phải được định nghĩa trước final evaluation;
- phương pháp phải freeze trước final evaluation;
- phải mô tả đúng supervision level.

Nếu inference không dùng labels nhưng hyperparameter selection dùng development labels, phải gọi đúng:

**label-guided development / hyperparameter selection**, không tuyên bố hoàn toàn label-free.

Quyết định này thay đổi policy cũ “root/fault labels evaluation-only” trong phạm vi development selection.

Hãy xử lý supersession đúng governance; không âm thầm sửa lịch sử.

---

## 3.5 Sensitivity analysis

Sensitivity analysis là **bắt buộc trước khi freeze final protocol** đối với các lựa chọn có khả năng ảnh hưởng materially đến kết quả.

Không cần exhaustive brute force.

Phải nghiên cứu prior work để xác định:

- parameter nào thực sự quan trọng;
- range hợp lý;
- default/initial value lấy từ đâu;
- sensitivity nào có giá trị khoa học.

Không được search `1..100` trên final evaluation rồi chọn kết quả đẹp nhất.

---

## 3.6 Backup / robustness methods

Không được đợi final test thất bại rồi mới nghĩ phương án cứu.

Trước final evaluation phải có một **small, pre-registered set** các mechanism hợp lý.

Không cần hàng chục thuật toán.

Mục tiêu là tránh để kết luận toàn bộ phụ thuộc vào một operator tự thiết kế.

Ví dụ về tư duy, không phải quyết định bắt buộc:

- một primary graph mechanism có lineage mạnh;
- một secondary mechanism có cơ chế đủ khác để kiểm robustness.

Hãy tự nghiên cứu xem PPR/RWR, subgraph methods, graph forecasting, graph representation learning, GNN hoặc hướng khác có phù hợp hay không.

Không mặc định đề xuất nào của tôi hoặc của TD-v1.1 là đúng.

---

## 3.7 L/O/R

Giữ ý tưởng controlled comparison:

- local/no relation;
- observed relation;
- structural/perturbed control;

nếu sau independent review nó vẫn hợp lệ.

Nhưng:

**L/O/R không được mặc định là toàn bộ graph-method story của đề tài.**

Nó là một experimental-control framework cho C1.

Nếu exact R construction, rewiring budget hoặc null model hiện tại không đủ căn cứ, hãy redesign.

---

## 3.8 Public datasets

RE2-TT vẫn là primary environment hiện tại nếu evidence tiếp tục ủng hộ.

Nhưng phải mở lại compatibility analysis cho public validation.

Đặc biệt bắt buộc đánh giá nghiêm túc:

**RE3-TT**

vì evidence hiện có trong research workspace cho thấy nó có:

- code-level faults;
- metrics;
- logs;
- traces;

và có thể giải quyết một phần concern rằng RE2-TT chủ yếu chứa resource/network faults với metric signal rất mạnh.

Không mặc định RE3-TT phù hợp. Audit schema, graph constructability, GT, timing, modality joins và task compatibility trước.

Sock Shop và LEMMA-RCA cũng phải được xem theo task compatibility thực tế.

Không được dựng graph giả chỉ để ép cùng protocol.

---

## 3.9 Meaning of negative results

Một configuration thất bại **không đồng nghĩa graph thất bại**.

Task D mới phải có failure-attribution logic giúp phân biệt ít nhất:

- implementation invalid;
- local evidence weak;
- telemetry mapping failure;
- graph coverage/quality failure;
- graph representation mismatch;
- graph operator misspecification;
- hyperparameter sensitivity;
- metric-only ceiling;
- insufficient statistical power;
- genuine negative result đối với mechanism đã kiểm;
- broader evidence about graph only when justified.

Không được kết luận:

“graph không có ích”

chỉ từ việc một smoothing formula thua baseline.

---

## 3.10 Execution boundary

Không bắt đầu Task E.

Mục tiêu cuối của phiên này là:

**TD mới → independent review → REVIEW_READY → human approval OPEN.**

AI không tự đánh dấu `APPROVED`.

---

# 4. KNOWN CONCERNS TO ATTACK — HYPOTHESES, NOT PREJUDGED ERRORS

Các điểm sau phải được điều tra độc lập.

Không mặc định chúng sai; cũng không được bảo vệ chúng chỉ vì đã tồn tại.

### Local anomaly evidence

- `l = (m + t) / 2`
- metric block dùng nhiều channels nhưng trace block có thể chỉ một count channel;
- max-channel aggregation;
- missing modality nhưng denominator giữ nguyên;
- median/IQR normalization;
- scale floors;
- clipping/capping;
- P90;
- window/bin sizes.

Câu hỏi:

**Local evidence hiện tại có đủ technical grounding để làm nền cho kết luận về graph không?**

### C1 graph mechanism

- undirected binary graph;
- bỏ observed direction;
- `q = (1-alpha)l + alpha Pq`;
- `alpha = 0.5`;
- chỉ một propagation mechanism;
- degree/component preserving rewiring;
- 32 chains;
- fixed swap budget;
- mobility gates;
- practical MRR delta threshold.

Câu hỏi:

**Nếu O không thắng L/R, ta có biết graph relations không hữu ích hay chỉ biết exact operator này không hữu ích?**

### C5

- equal modality weights;
- graph smoothing trước threshold;
- `max(q) <= max(a)` property;
- q99;
- consecutive-bin rule;
- refractory interval;
- fixed windows;
- graph-conditioned smoothing có thật sự đáp ứng intent graph-based anomaly detection hay chỉ là graph-assisted denoising.

### Statistics/protocol

- current development/evaluation split;
- scenario/repeat dependence;
- development label policy mới;
- parameter selection;
- sensitivity;
- multiple comparisons;
- practical-effect threshold;
- bootstrap/CI;
- Monte-Carlo control precision.

### Dataset strategy

- RE2-TT metric ceiling;
- fault-family diversity;
- RE3-TT;
- RE2/RE3 Online Boutique nếu relevant;
- Sock Shop;
- LEMMA-RCA;
- exact modality/GT/graph compatibility.

---

# 5. REQUIRED MULTI-AGENT REVIEW

Nếu native Codex multi-agent/subagent support khả dụng, **bắt buộc sử dụng 5 subagents độc lập**.

Coordinator không được tự đóng vai cả 5 reviewer.

Subagents ở vòng đầu **read-only**. Chỉ coordinator được chỉnh canonical artifacts sau synthesis.

Không cho 5 reviewer cùng một thesis phải bảo vệ.

Mỗi reviewer phải có nhiệm vụ riêng và được khuyến khích bác bỏ cả TD-v1.1 lẫn các đề xuất trong prompt này.

## Reviewer A — Advisor Alignment & Research Scope

Câu hỏi chính:

- phương pháp hiện tại có thực sự đi đúng định hướng giảng viên không?
- C1/C5 hiện được đặt đúng vai trò chưa?
- Step 1–5 của cô được đáp ứng thực chất hay chỉ bằng wording?
- graph-based anomaly detection hiện có đủ sức thuyết phục không?
- multi-public-dataset requirement được xử lý đúng chưa?
- title DT18 và hướng RCA có khớp nhau không?

Ưu tiên nguồn:

- DT18;
- advisor source;
- current user decisions;
- Task A/C/D;
- master program.

Không dùng existing Task D reviewer conclusions làm điểm xuất phát.

---

## Reviewer B — Algorithm, Mathematics & Prior-Work Lineage

Đánh giá từ technical first principles và primary literature.

Bắt buộc kiểm:

- anomaly-score construction;
- multimodal fusion;
- graph representation;
- graph propagation/ranking;
- graph-based anomaly detection;
- threshold/calibration;
- missingness behavior.

Với mỗi formula/algorithm:

- nguồn gốc;
- assumptions;
- prior systems đã dùng nó ở task nào;
- parameter defaults/ranges;
- tuning strategy;
- known sensitivity;
- mismatch với RE2/RE3-TT.

Phải truy primary papers và official implementations khi có thể.

Không được dùng Task A synthesis làm nguồn duy nhất cho một technical formula quan trọng.

---

## Reviewer C — Experimental Design, Statistics & Hyperparameters

Độc lập xem xét:

- development/evaluation policy;
- sử dụng labels;
- split;
- repeated scenarios;
- statistical power;
- effect size;
- practical threshold;
- uncertainty;
- multiple comparisons;
- sensitivity;
- hyperparameter selection;
- final freeze;
- valid negative versus inconclusive.

Đặc biệt trả lời:

**Làm thế nào cho phép method selection mà không biến evaluation thành test-set tuning?**

Không mặc định 30/60 hoặc delta=0.05 là đúng.

---

## Reviewer D — Dataset / Telemetry / Ground Truth / Public Compatibility

Bắt đầu từ data evidence trước, không bắt đầu từ method mong muốn.

Đánh giá:

- RE2-TT;
- RE3-TT;
- Sock Shop variants;
- Online Boutique nếu có ích;
- LEMMA-RCA.

Với mỗi dataset xác định:

- task có thể đánh giá;
- GT granularity;
- modalities;
- graph constructability;
- time alignment;
- joins;
- fault diversity;
- missingness;
- candidate universe;
- leakage;
- metric ceiling;
- khả năng dùng cho C1;
- khả năng dùng cho graph AD;
- khả năng dùng cho baseline comparison.

Không ép tất cả dataset phải dùng cùng graph.

---

## Reviewer E — Adversarial Falsification, Reproducibility & “Will We Regret This at F/G?”

Vai trò là reviewer khó tính.

Giả sử nhóm đã làm xong F/G và kết quả xấu.

Đi ngược lại tìm:

- decision nào lẽ ra phải phát hiện từ D;
- công thức nào không đủ căn cứ;
- parameter nào quá arbitrary;
- failure mode nào chưa có attribution;
- backup nào chưa pre-register;
- comparator nào thiếu;
- source-of-truth nào có thể khiến implementation sai;
- điểm nào hội đồng có thể hỏi “tại sao lại chọn con số này?” mà nhóm không trả lời được.

Mục tiêu:

**tìm những lý do có thể buộc nhóm đập đi xây lại sau này và kéo chúng về giải quyết ngay tại D.**

---

# 6. INDEPENDENCE PROTOCOL

Để giảm anchoring:

### Pass 1 — Blind-ish independent review

Mỗi reviewer tự hình thành nhận định dựa trên nguồn thuộc nhiệm vụ của mình.

Không đọc kết luận của bốn reviewer còn lại trước khi hoàn thành memo vòng 1.

Nếu có thể, reviewer B/D nên hình thành technical/data assessment từ primary sources trước khi đọc các kết luận cũ trong `task-d/`.

Existing Task D review artifacts có thể đọc **sau initial opinion**, để so xem reviewer mới có tìm được vấn đề khác không.

Mỗi reviewer phải trả:

- findings;
- severity: `CRITICAL | MAJOR | MINOR | NOTE`;
- exact evidence;
- reasoning;
- recommended action;
- alternative;
- điều gì có thể chứng minh reviewer đang sai;
- phần nào trong TD-v1.1 nên giữ.

### Pass 2 — Cross-review

Sau khi coordinator nhận đủ 5 memo vòng 1:

- A phản biện B;
- B phản biện C;
- C phản biện D;
- D phản biện E;
- E phản biện A.

Cross-review phải tập trung vào:

- unsupported assumptions;
- overclaim;
- methodological mismatch;
- unnecessary complexity;
- missing alternative;
- evidence quality.

Không được chỉ “đồng ý”.

### Pass 3 — Reconciliation

Coordinator:

1. tổng hợp điểm đồng thuận;
2. giữ disagreement thực sự;
3. kiểm lại source cho disagreement quan trọng;
4. đọc existing TD-v1.1 reviews/reconciliation;
5. xác định finding nào:
   - đã biết;
   - đã xử lý thật;
   - chỉ được wording-around;
   - hoàn toàn mới.

Không quyết bằng reviewer voting.

Quyết bằng evidence + authority + scientific defensibility.

---

# 7. PARAMETER AND FORMULA PROVENANCE AUDIT

Tạo một **Parameter/Formula Provenance Matrix** cho toàn bộ method trước khi redesign.

Ít nhất gồm:

| Component | Formula / parameter | Current choice | Classification | Primary source / lineage | Why this choice | Alternatives | Selection policy | Sensitivity required? | Failure mode | Final status |
|---|---|---|---|---|---|---|---|---|---|---|

Bao phủ ít nhất:

- time bins;
- reference/query windows;
- median/IQR or alternative;
- clipping;
- scale floor;
- percentile aggregation;
- metric-channel aggregation;
- trace score;
- log score;
- modality fusion;
- missing-modality handling;
- graph direction/weighting;
- graph operator;
- alpha/damping/restart;
- graph controls;
- randomization budget;
- detector threshold;
- streak;
- refractory;
- effect threshold;
- bootstrap/CI;
- comparator parameters.

Không cố tìm citation giả cho study-specific choices.

Nếu không có external basis, ghi đúng:

`STUDY_SPECIFIC_DESIGN`.

Sau đó quyết định:

1. bỏ;
2. giữ fixed với rationale;
3. biến thành development-selected hyperparameter;
4. biến thành sensitivity dimension;
5. thay bằng prior-work default/method.

---

# 8. ALGORITHM SELECTION PRINCIPLE

Task D mới không nên khóa một exact custom formula chỉ vì nó đơn giản.

Hãy nghiên cứu một bounded set các phương pháp phù hợp với:

- dữ liệu thực có;
- supervision được phép;
- compute thực tế;
- thesis scope;
- advisor direction.

Đặc biệt phân biệt:

### Graph for RCA ranking

Graph có thể tham gia:

- score propagation;
- PageRank/PPR/RWR;
- causal/conditional reasoning;
- learned representation;
- hoặc mechanism khác.

### Graph for anomaly detection

Graph có thể tham gia:

- graph-conditioned local anomaly;
- subgraph/structural anomaly;
- graph forecasting residual;
- graph neural representation;
- trace-event graph anomaly;
- hoặc mechanism khác.

Không đồng nhất hai nhiệm vụ.

Không bắt buộc dùng GNN.

Không loại GNN chỉ vì phức tạp nếu một bounded implementation thật sự là lựa chọn tốt nhất.

Không chọn PageRank chỉ vì giảng viên nêu làm ví dụ.

Phải chọn theo evidence.

---

# 9. C1 REDESIGN REQUIREMENT

C1 vẫn phải bảo vệ scientific isolation của relation effect.

Task D mới phải trả lời:

1. local evidence nào được dùng?
2. tại sao local evidence đó đáng tin?
3. graph representation nào?
4. primary graph operator nào?
5. secondary robustness operator nào, nếu cần?
6. L/O/R hoặc control tương đương cô lập cái gì?
7. parameter nào fixed?
8. parameter nào tuned trên development?
9. sensitivity nào bắt buộc?
10. negative result được diễn giải đến mức nào?

Nếu đổi graph operator, vẫn phải giữ fairness giữa graph/no-graph arms.

Không cho graph arm thêm telemetry/candidate/supervision rồi gọi gain là graph gain.

---

# 10. C5 REDESIGN REQUIREMENT

C5 phải trở thành graph-based anomaly-detection track có technical justification rõ.

Task D mới phải chỉ ra:

- graph là gì;
- node/edge features là gì;
- anomaly score được tạo ở đâu;
- graph ảnh hưởng detector bằng mechanism nào;
- training/calibration nào;
- threshold nào;
- supervision nào;
- output nào;
- GT nào chấm được trên từng dataset;
- metric nào hợp lệ;
- limitations.

Phải so với một graph-free matched detector khi có thể để biết graph có tác dụng gì.

Nếu RE2-TT không đủ GT để đánh giá node anomaly, không tạo nhãn giả.

System-level injection-regime evaluation phải giữ đúng giới hạn.

Nếu một dataset khác cung cấp GT tốt hơn cho graph AD, xem xét dùng nó.

---

# 11. DEVELOPMENT / TUNING / FREEZE DESIGN

Thiết kế lại protocol theo nguyên tắc:

```text
prior work
    ↓
candidate methods + parameter ranges
    ↓
development data
    ↓
method / hyperparameter selection
    ↓
sensitivity / robustness checks
    ↓
FREEZE
    ↓
final evaluation
```

Không được:

```text
final evaluation
    ↓
thấy xấu
    ↓
đổi alpha/window/formula
    ↓
chạy lại và chỉ báo run đẹp
```

Nếu dataset nhỏ, reviewer C phải đề xuất cách chống overfit development hợp lý:

- grouped split;
- cross-validation trong development;
- leave-group-out;
- nested selection;
- hoặc phương án khác có căn cứ.

Không mặc định một kỹ thuật.

---

# 12. FAILURE ATTRIBUTION / RESCUE POLICY

Task D mới phải có decision tree trước E.

Ví dụ dạng logic:

```text
Graph method không cải thiện
    |
    +-- implementation/schema invalid?
    |
    +-- local evidence informative?
    |
    +-- root exists in candidate universe?
    |
    +-- graph sufficiently observed?
    |
    +-- operator robust across justified parameter range?
    |
    +-- secondary grounded operator agrees?
    |
    +-- non-graph baseline already near ceiling?
    |
    +-- statistical uncertainty too large?
    |
    +-- only then interpret bounded negative evidence
```

Không gọi implementation failure là negative result.

Không gọi one-configuration failure là general graph failure.

---

# 13. PUBLIC DATASET PLAN

Task D mới phải tạo một **task-compatibility matrix**, không chỉ list dataset names.

Ít nhất xem:

- RE2-TT;
- RE3-TT;
- relevant Sock Shop data;
- relevant Online Boutique data nếu cần;
- LEMMA-RCA.

Đối chiếu với advisor Step 3.

Không bắt buộc cả ba hệ có cùng experiment.

Có thể:

- một dataset kiểm RCA graph ranking;
- một dataset kiểm graph AD;
- một dataset kiểm portability/baselines;

nếu đây là cách trung thực nhất với GT và telemetry.

Nhưng phải giải thích rõ scientific role của từng dataset.

---

# 14. BASELINES

Đánh giá lại baseline strategy.

Phân biệt:

- graph-free matched controls;
- structural controls;
- executable RCA baselines;
- executable graph methods;
- literature-only comparisons.

Không dùng paper score từ dataset khác như direct experimental comparator.

Nếu current external graph comparator package quá yếu, tìm một bounded executable comparator có task/data compatibility.

Không cần chạy mọi SOTA.

Cần đủ để hội đồng không thể hợp lý hỏi:

“Các em chỉ so thuật toán graph của mình với chính bản tắt graph của nó, vậy so với graph RCA đã có thì sao?”

---

# 15. EDITING AUTHORIZATION

Sau khi hoàn thành review + reconciliation:

Nếu evidence cho thấy TD-v1.1 cần material revision, bạn được phép tạo revision tiếp theo, dự kiến **TD-v1.2**.

Không cần hỏi lại tôi để thực hiện các chỉnh sửa tài liệu RCA nằm đúng trong scope này.

Trước khi sửa hơn ba file, lập impact map theo project governance.

Canonical source sửa trước, derived files sau.

Các thay đổi có thể bao gồm khi cần:

- `task-d-method-and-experiment-specification.md`
- `task-d-handoff.md`
- `CURRENT-STATE.md`
- `RESEARCH-DECISIONS.md`
- `task-c-research-decision-lock.md`
- `MASTER-RESEARCH-PROGRAM.md`
- artifact/index files liên quan
- new Task D review/evidence artifacts trong research workspace.

### Quan trọng

Việc tôi cho phép development labels là một **USER_CONFIRMED change**.

Không rewrite lịch sử.

Phải:

- tạo decision mới;
- chỉ rõ decision/policy cũ bị supersede ở phạm vi nào;
- giữ historical record.

Các lựa chọn thuật toán mà bạn tự đề xuất vẫn là `CANDIDATE`, không được gắn `USER_CONFIRMED`.

---

# 16. VALIDATION

Sau mutation:

- chạy governance audit theo repository skill;
- chạy Task D validation hiện có;
- update validation nếu revision mới thay contract;
- chạy synthetic/math tests cần thiết;
- inspect toàn bộ diff;
- kiểm tra links/paths;
- kiểm tra không accidental mutation application code/schema/API;
- kiểm tra không chạy Task E;
- kiểm tra không dùng evaluation outcome để chọn method.

Nếu validator cũ encode assumptions đã bị redesign, không “sửa method cho pass validator”.

Update validator có chủ đích và ghi changelog.

---

# 17. STOP / ESCALATION CONDITIONS

Không dừng chỉ vì có technical choice khó. Hãy nghiên cứu và đưa ra CANDIDATE tốt nhất.

Chỉ dừng mutation nếu:

- workspace/source-of-truth conflict vật chất không thể phân xử;
- cần thay đổi application code/API/schema ngoài scope;
- cần destructive Git action;
- evidence cho thấy một `USER_CONFIRMED` decision hiện tại có khả năng làm invalid nghiên cứu và cần tôi supersede nó.

Trong trường hợp cuối:

- hoàn thành toàn bộ phần review còn lại;
- đưa exact evidence;
- đề xuất replacement;
- không tự âm thầm đổi quyết định.

---

# 18. SUCCESS CRITERIA

Không coi nhiệm vụ hoàn thành chỉ vì có TD-v1.2 dài hơn.

Nó chỉ hoàn thành khi:

### Scientific

- phương pháp bám advisor direction một cách thực chất;
- C1 và C5 có vai trò rõ;
- anomaly score có lineage/rationale;
- graph algorithm có lineage/rationale;
- custom choices được nhận diện;
- parameter selection có protocol;
- sensitivity có protocol;
- backup được pre-register;
- negative-result semantics đúng;
- dataset roles được chứng minh.

### Governance

- USER_CONFIRMED / FACT / CANDIDATE / OPEN không bị trộn;
- historical decisions preserved;
- Task E vẫn NOT STARTED;
- human approval vẫn OPEN.

### Execution readiness

Một engineer ở Task E có thể đọc D và biết chính xác:

- cần reproduce gì;
- cần validate schema gì;
- cần tune gì trên development;
- không được nhìn gì ở final evaluation;
- khi nào freeze;
- khi nào một method bị invalid;
- khi nào fallback/secondary method được dùng;
- cần lưu artifact nào;
- tiêu chí nào khiến phải quay lại D trước final campaign.

---

# 19. FINAL DELIVERABLE

Cuối phiên, trả cho tôi một **Pre-Approval Task D Review Packet** ngắn gọn nhưng đủ quyết định, gồm:

## A. Source-of-truth snapshot

- branch/HEAD hai repo;
- canonical files thực tế đã dùng;
- mọi branch/state conflict phát hiện.

## B. Five-reviewer verdict

Bảng:

| Reviewer | Verdict | Critical/Major findings | What to keep | Required change |
|---|---|---|---|---|

Kèm các disagreement còn thật sau cross-review.

## C. Advisor-alignment matrix

Map Step 1–5 của giảng viên → revised method/tasks/datasets/metrics/status.

## D. Formula/parameter provenance summary

Nêu rõ:

- cái gì adopted;
- adapted;
- standard;
- study-specific;
- cái gì đã bỏ;
- cái gì chuyển sang dev tuning;
- cái gì cần sensitivity.

## E. Revised research architecture

Một sơ đồ ngắn kiểu:

```text
Telemetry
   ↓
Dependency Graph
   ↓
Graph-Based Anomaly Detection   [C5]
   ↓
Incident
   ↓
Graph-Assisted RCA Ranking      [C1-informed]
   ↓
Structured Evidence
   ↓
LLM Explanation
```

nhưng dùng exact architecture mà evidence review cuối cùng ủng hộ, không buộc giữ sơ đồ này nếu sai.

## F. Dataset plan

Vai trò cụ thể của:

- RE2-TT;
- RE3-TT;
- Sock Shop;
- LEMMA-RCA;
- dataset khác nếu có căn cứ mạnh.

## G. Files changed

Liệt kê từng file và lý do.

## H. Validation

Commands/checks + PASS/FAIL.

## I. Remaining OPEN items

Chỉ những gì thực sự chưa thể quyết trước E.

## J. Final recommendation

Chỉ một trong:

- `REVIEW_READY — recommend human approval for Task E`
- `REVIEW_READY WITH BLOCKERS — do not start E`
- `NOT READY — Task D requires another revision`

AI không được tự ghi `APPROVED`.

---

# 20. WORKING STYLE FOR THIS RUN

Đây là long-horizon agent task.

- Lập TODO/plan ngay đầu.
- Theo dõi từng phase đến khi hoàn tất.
- Dùng 5 subagents song song cho các independent workstreams nếu native multi-agent khả dụng.
- Giữ main-agent context tập trung vào decisions/evidence; không đổ toàn bộ noisy logs vào main thread.
- Sau mỗi major phase, kiểm xem có sub-request nào chưa hoàn thành.
- Không dừng sau discovery; hoàn thành review, reconciliation, revision và validation trong cùng run nếu không gặp stop condition thực sự.
- Không hỏi tôi những điều có thể xác minh từ repository, Git history, primary papers hoặc existing data.
- Có quyền đưa ra technical `CANDIDATE` recommendation mạnh.
- Không được sycophantic: nếu đề xuất hiện tại của tôi sai, hãy chỉ ra.
- Không được contrarian cho có: một criticism phải có evidence hoặc reasoning kỹ thuật.
- Tối ưu cho **scientific defensibility + probability of successful downstream execution**, không tối ưu cho việc giữ nguyên TD-v1.1 hay giảm số file phải sửa.

Mục tiêu cuối cùng không phải “làm graph thắng”.

Mục tiêu là:

**trước khi Task E bắt đầu, chúng ta có một phương pháp mà dù kết quả sau này dương hay âm, có thể tin rằng kết quả đó đến từ một thí nghiệm được thiết kế tốt — không phải từ công thức tùy ý, tuning sai, dataset sai, leakage hoặc hiểu sai định hướng giảng viên.**