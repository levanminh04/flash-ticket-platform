# Task C — Các hướng nghiên cứu độc lập và khả thi

> **CURRENT DECISION — 2026-09-22:** [Task C Phase 2](task-c-research-decision-lock.md) đã COMPLETE theo [RCA-001–017](RESEARCH-DECISIONS.md): C1 primary, C2 optional, C3/C4 giữ vai trò hỗ trợ, C5/LLM là năng lực dự kiến bắt buộc. **Toàn bộ phần bên dưới là snapshot Phase 1 ngày 21/09**, giữ nguyên bằng chứng và trạng thái chờ lựa chọn tại thời điểm đó; không dùng nó để mở lại quyết định hiện tại. Bước tiếp theo theo [CURRENT-STATE](CURRENT-STATE.md); Task D chưa bắt đầu.

- Trạng thái: `DRAFT` — review Phase 1 hoàn tất; chờ con người chọn hướng, không phải phê duyệt nghiên cứu.
- Loại: `FORMATION`; ý định: `EXECUTE`. Ngày tổng hợp: 2026-09-21; discovery và kiểm nguồn: 2026-09-20.
- Người quyết định: Lê Văn Minh; người hướng dẫn phản biện khi trình. AI không tự phê duyệt.
- Phạm vi: Task C Phase 1, từ sinh ứng viên đến shortlist. Các bước normalization/gates mang số phase nội bộ trong yêu cầu; không nhầm với **C Phase 2 — Research Decision Lock** cần Minh chọn.

**Kết luận điều hành — TASK-C INFERENCE:** giữ **hai lựa chọn có thể kiểm chứng**, theo thứ tự ID trung tính: **C1 — lợi ích tăng thêm khi dùng quan hệ service trong các pipeline được kiểm**, và **C2 — lợi ích đó thay đổi thế nào theo ngân sách quan sát event-time**. Cả hai chỉ ở mức **DEFENSIBLE BUT INCREMENTAL**, cùng task known-window root-service ranking. Không hướng nào đã chứng minh tính mới phương pháp hoặc được bảo đảm đủ đóng góp luận văn. C3/C4 chỉ là thí nghiệm phụ tùy nhu cầu; C5 hoãn RQ chính. Red Team đã thử bác cả hai survivor qua đủ 18 mục; không còn FATAL ở claim hẹp, vẫn giữ rủi ro contribution MAJOR để Minh/giảng viên cân nhắc.

Kết quả này đủ để lựa chọn trọng tâm và khóa scope ở C Phase 2; **không** là kết quả thí nghiệm, phương pháp đã chọn, hoặc tuyên bố hoàn thành mọi nhiệm vụ detection/FlashTicket của đề tài.

## 1. Cơ sở và quyền sử dụng kết quả

Nguồn nhiệm vụ hiện hành là [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md) và [phân công](../project/roles.md). Đề tài gồm xây dựng, đo hệ thống bán vé phân tán và nghiên cứu cơ chế giám sát/chẩn đoán bằng log, trace, metrics; thực nghiệm công khai trước rồi kiểm chứng trên FlashTicket. Một câu hỏi RCA hẹp không thay toàn bộ nhiệm vụ, không miễn đánh giá detection hoặc đánh giá giao dịch dưới tải.

**Quy ước:** phát biểu nguồn là `FACT` kèm nhãn VERIFIED — TASK A/B/MACHINE hoặc PRIMARY-SOURCE FACT. Hợp đồng, nhận định khả thi và shortlist là `CANDIDATE / TASK-C INFERENCE`, chưa phải kết quả đo hay `DECIDED`. Tham số/phương pháp chưa chọn là `OPEN`. Việc đã hoàn thành review không đồng nghĩa hướng được duyệt hoặc đã chứng minh tính mới.

| Nguồn | Phiên bản/phạm vi sử dụng |
|---|---|
| [Task A](task-a-ban-do-bang-chung-doc-lap.md) | Bản DRAFT, cutoff 2026-09-20; 11 phương pháp. Đã đọc toàn bộ; không bắt đầu lại survey. |
| [Task B](D:/Project/flash-ticket-rca-research/dataset-audit/TASK-B-RCAEval-audit.md) | §12 CLOSED có hiệu lực; §§1–11/pre-close là lịch sử. Đã đọc báo cáo và toàn bộ 11 reviewer A–J/resource liên quan. |
| [Ledger](D:/Project/flash-ticket-rca-research/task-c/task-c-evidence-ledger.md) | Định vị từng A-*, B-*, M-*, R-*, P-* và giới hạn suy luận. |
| [Inventory](D:/Project/flash-ticket-rca-research/task-c/task-c-input-inventory.json) | 65 đầu vào gồm báo cáo, JSON, manifest và 22 script audit; hash xác nhận lại khi tiếp tục. Không chạy lại raw audit. |
| [Machine check](D:/Project/flash-ticket-rca-research/task-c/task-c-machine-evidence-check.json) | Kiểm nhẹ tổng số ca/span/parent/coverage và metadata hash trên kết quả lưu sẵn; không nhận là fresh raw rehash. |
| [Kiểm nguồn hẹp](D:/Project/flash-ticket-rca-research/task-c/task-c-targeted-verification.md) | TV-01–04: Eadro, DejaVu, BARO, MicroRank; chỉ giải các điểm tác động đến gate. |

Revision RCAEval: `afeacb11bcc94dadfd1c8f483ee4377b2b8b614e`; metadata SHA-256 `c49a288920dbba2e8e724679a14636d5c7eb2b45426bba14007ef79a6c0ab1bb`. Đây là identity nguồn của Task B, không phải tuyên bố đã lưu toàn bộ corpus tại máy.

## 2. Những sự thật giới hạn mọi hướng

| Bằng chứng | Kết luận sử dụng trong Task C |
|---|---|
| M-001/002 — VERIFIED MACHINE: 90 RE2-TT = 5 root-service × 6 fault × 3 repeat; metrics/traces 90, logs 89 | Đơn vị nghiên cứu là incident, không phải span. Có lặp kịch bản, chỉ một collection; không suy độc lập từ số lượng file. Presence không chứng minh toàn bộ log/metric schema tương thích. |
| M-003/004/006 — VERIFIED MACHINE: 67.345.051 span; 27 literal services, 161 literal service-operation pairs; 55 loại quan hệ service toàn subset | Các union chỉ là thống kê audit. Không dùng làm vocabulary/topology nhìn trước. Edge từ parent-span resolve trong cùng trace; không tự đổi tên thành CALLS, USES, messaging hoặc causal propagation. |
| M-004 — 65.748.095/66.781.374 parent references resolve; 7 ca dưới 90% | Tỷ lệ resolve không là topology recall; không biết quan hệ hoàn toàn không được thu. Giữ các ca thấp coverage; không repair từ kiến trúc hoặc nhãn. |
| M-005 — toàn case có target trong 20–27 trace services ở 90/90 ca | Không có bảo đảm root xuất hiện ở mọi prefix. Universe từ telemetry được phép, dùng chung giữa comparators; cấm giới hạn về 5 injected labels. |
| B-002/003/006 — service-root GT có; operation/resource/affected-node/path GT thiếu | Chấm service ranking được. Node/service anomaly F1, operation-root accuracy, resource-root/path accuracy không đánh giá được. Operation chỉ làm biểu diễn hoặc bằng chứng phụ. |
| R-002/M-007 — các ca đã kiểm có log–metric bin entity-second khớp; trace join chỉ service+time | DIRECT chỉ là khóa bin tổng hợp, không phải request/event identity. Không suy toàn bộ 89 log cases từ samples. Không gắn log vào span bằng thời gian rồi gọi direct correlation. |
| B-005 — labels và metadata bị giới hạn | Root/fault chỉ evaluator; case/path và root_cause.txt không vào model. Inject_time chỉ được dùng làm boundary ngoài cho RCA-given-incident-window; cấm làm detector input. |
| T-002/B-002 — injection schedule cố định, thiếu onset độc lập | System-window agreement với injection regime không là detection vận hành; phần trước injection không thay healthy controls đa workload dài hạn. |
| M-009 — B2 range-read 89 remote traces + 1 local, không giữ corpus mới | Audit JSON không đủ chạy thí nghiệm 90 ca. Retrieval được pin, chuẩn bị features và chạy baseline thuộc giai đoạn sau khi khóa phương pháp; chưa cài/tải/chạy trong Task C. |

**Hiệu chỉnh provenance:** Task A §M gán các ví dụ thuật toán/metric và LLM cho thư 22/08 quá mạnh. Nguyên văn thư nói AI giải thích, không nêu các tên đó; nguồn LLM và ví dụ hiện hành là yêu cầu Task C §5. DT18 là xác nhận của Minh, không gọi là thư mới của cô. TV-04 cũng bác cách diễn giải MicroRank “bắt buộc chờ thêm năm phút sau alert”: paper mô tả flush detection window để tránh trigger lặp. Giữ nguyên A/B và reviewer gốc; Task C sử dụng các hiệu chỉnh này, không lan truyền sai nguồn.

## 3. Sinh độc lập và chuẩn hóa

Sáu reviewer đều nộp trước khi gộp. Mỗi người nhận ngữ cảnh mới và nguồn A/B, không được xem đề xuất đồng cấp. Main đã thấy hội thoại cũ nên không nhận mình hoàn toàn blind. Độc lập là diễn giải, không phải sáu dataset độc lập. Có bốn slot gồm main: ba reviewer đồng thời, hai đợt discovery; sau đó các reviewer gate riêng, rồi Red Team chạy sau khi giảm tập ứng viên.

| ID | Đề xuất thô | Câu hỏi phân biệt |
|---|---|---|
| C1 | CA-01, CB-01, CD-01 | Quan hệ service thêm thông tin gì khi giữ bằng chứng cục bộ cố định? |
| C2 | CA-02, CB-02, CC-01, CD-02, CE-01, CF-02 | Giá trị graph thay đổi thế nào theo ngân sách bằng chứng event-time? |
| C3 | CC-02 | Giữ operation identity đến muộn có giúp output service rank? |
| C4 | CE-02 | Graph đổi contextual score hay chỉ đổi ranking: hiệu quả service rank khác nhau ra sao? |
| C5 | CF-01 | Graph ở detector có cải thiện detection-regime mà không gây tổn hại diagnosis? |

Đã giữ khác biệt: C1 không đổi local evidence; C4 đổi score; C5 thêm endpoint detector. C3 đổi granularity chứ không là phép thêm graph đơn thuần. C2 có repeated cutoff và availability, không là bản C1 đổi tên. Lý do từng merge ở [normalized universe](D:/Project/flash-ticket-rca-research/task-c/task-c-normalized-candidate-universe.md).

## 4. Hợp đồng chung cho các hướng xếp hạng

Toàn bộ phần này là CANDIDATE / TASK-C INFERENCE cho phạm vi đánh giá; không khóa thuật toán hay tham số.

**Giới hạn suy luận chung, theo phản biện RT-08:** C1/C2 đo utility của observed relations **trong các pipeline, controls và budgets được chọn**. Kết quả âm của một implementation không chứng minh graph không chứa thông tin RCA hoặc mọi phương pháp graph đều vô ích. Kết quả dương không chứng minh lượng thông tin nội tại độc lập với method, graph đúng hoàn chỉnh hoặc cơ chế nhân quả. “Giá trị thông tin” trong tên hướng là hiệu quả thực nghiệm có điều kiện này, không là tuyên bố information-theoretic hay causal identification.

1. **Primary task:** root-service ranking given known incident window trên RE2-TT pinned release. Boundary ngoài là oracle được khai rõ trong định nghĩa task; không gọi autonomous detection. Một incident là đơn vị ghép cặp; mỗi service chỉ xuất hiện một lần trong ranked list.
2. **Graph:** node lấy literal service identity; edge là observed same-trace parent→child service relation. Chỉ dùng telemetry ở history/cutoff hợp lệ; unmatched identity giữ unmatched. Thiếu node/edge không được imputation ngầm; không dùng architecture FlashTicket lấp RE2-TT.
3. **Input:** metrics có mapping entity xác định và trace-derived local evidence; giữ trace local features cho cả nhánh không graph để tách lợi ích relation khỏi lợi ích thêm modality. Logs là block bổ sung có điều kiện schema/availability, không là tiền đề thành công. Cùng nguồn, history, candidate universe, preprocessing và calibration budget giữa các nhánh so sánh.
4. **Labels:** root/fault labels, inject metadata và paths tách khỏi feature pipeline. Không fit/tune mô hình hoặc chọn features/controls bằng root/fault labels theo policy hiện tại. Evaluation labels có thể dùng trong evaluator thiết kế/phân tầng split và chấm sau khi freeze; không điều chỉnh mô hình sau xem kết quả held-out. Phương pháp supervised là closest work, không tự được phép train chỉ vì có nhãn. Telemetry counts/aggregates chỉ trong history/window được phép là features hợp lệ; metadata/file-level counts hoặc aggregates từ tương lai không được dùng như lối tắt.
5. **Baselines:** local statistical deviation/ranking; họ trace-relation RCA có input phù hợp; internal controls với graph-free và structure controls. MicroRCA/CIRCA/MicroRank không mặc định tái lập nguyên bản khi thiếu host/metric structural mapping hoặc khác output. Mọi adapter phải gọi adaptation; full-method comparison và within-method ablation là hai bảng khác nhau. Không lấy số paper trên collection khác làm baseline của RE2-TT.
6. **Metrics:** MRR, Hit@k và phân phối rank theo case, coverage/miss/error counts; paired effect và uncertainty có xét scenario/repeat. NDCG một root chỉ là biến đổi vị trí, không đo severity. Không dùng node F1. Chọn endpoint, k và practical-effect criterion ở Task D trước nhìn final test, không khóa số ở đây.
7. **Statistics:** chia theo incident, cân nhắc group service×fault; không rải windows/prefix cùng case vào các split. Ba repeats không chứng minh độc lập giữa campaign. Mô tả 90 ca là hữu hạn; suy rộng/interval phải nêu giả định sampling/dependence. Seeds/cutoffs/spans không tăng N độc lập; không chọn subgroup hoặc cutoff thuận lợi sau test.
8. **Missingness:** giữ case lỗi chạy và coverage thấp. Primary kết quả tính trên tập ca đã định trước, không chỉ ca target hiện diện. Conditional-on-coverage chỉ phụ. Đối chứng hoán đổi graph kiểm thông tin cấu trúc, không chứng minh cơ chế mất trace thực tế hoặc causal effects trong hệ thật.
9. **Scope:** không topology repair, resource graph, operation target, causal path, multi-root claim, LLM labeling hoặc tự sửa hệ thống. LLM chỉ giải thích packet bằng chứng sau ranker, nêu uncertainty; không là chứng cứ nguyên nhân đã đúng.
10. **Transfer:** public experiment hẹp có thể kết luận riêng RE2-TT; dataset thứ hai không bắt buộc cho claim hẹp. FlashTicket controlled validation vẫn là nhiệm vụ DT18, thực hiện theo gate hệ thống và không tự thêm service/Saga/API. Detector và system performance cần đánh giá riêng, ranking không thay chúng.

## 5. C1 — Giá trị tăng thêm của quan hệ dịch vụ

**RQ:** Trong các pipeline/control được chọn cho known-window service RCA trên RE2-TT, khi giữ telemetry, local evidence và candidate universe như nhau, sử dụng các quan hệ service quan sát được có cải thiện xếp hạng root vượt đối chứng không quan hệ và cấu trúc đối chứng hay không?

| Trường | Hợp đồng CANDIDATE |
|---|---|
| H0 | Quan hệ đúng không cải thiện chất lượng xếp hạng vượt đối chứng cục bộ và cấu trúc đối chứng trong phạm vi đánh giá đã định. |
| H1 | Quan hệ đúng đem lại mức cải thiện thực dụng được đăng ký trước, còn tồn tại sau kiểm soát evidence/capacity/topology nuisance và không chỉ do một service hoặc fault. |
| Potential contribution | Thực nghiệm xác định thông tin quan hệ có giá trị riêng hay điểm tăng chỉ đến từ features/capacity/smoothing. Có thể cho kết quả âm có ích; không tuyên bố phương pháp mới. |
| Closest prior / giới hạn khác biệt | MicroRCA, Eadro, DejaVu, CIRCA đã có graph/context/ablation. TV-01/02/04 bác tính mới của graph-on/off hay held-out testing tự thân. Contrast hẹp giữ local evidence và kiểm structure nuisance không được pack chứng minh đã trả lời đầy đủ; đây là lý do câu hỏi còn đáng kiểm, không là chứng minh chưa ai làm. |
| Dataset / unit / GT | 90 incident RE2-TT pinned; output xếp service; GT published root-service. Fault labels dùng evaluator phân tầng. Không có operation/node/path truth. |
| Graph / input / output | Service relation graph từ parent resolved; cùng metric/trace-local evidence ở mọi nhánh; logs chỉ block phụ hợp lệ. Một danh sách service không lặp và trạng thái coverage/failure mỗi ca. |
| Quantitatively evaluable | Case-level service rank, paired change, coverage, runtime/peak memory; effect theo scenario được khai trước. |
| Not evaluable | Causal propagation, root operation/resource, anomaly-node correctness, production detection/MTTR và chất lượng lời giải thích chỉ từ root label. |
| Comparison / baselines | Shared-evidence local rank, cùng họ relation rank có structure controls; baseline literature tương thích trong bảng riêng. So full algorithms không cô lập được effect relation nếu inputs/capacity khác. |
| Metrics | MRR/Hit@k, rank distributions, paired effect và uncertainty; failure/absence giữ trong denominator. Metric chính và practical-effect criterion thuộc Task D. |
| Required ablations | Không relations nhưng giữ trace-derived features; observed relations; cấu trúc đối chứng giữ node/features và thuộc tính nhiễu phù hợp. Kiểm degree/volume prior; tách thay đổi nguồn telemetry khỏi thay đổi graph. Không đòi giữ mọi thuộc tính cùng lúc khi cấu trúc nhỏ không cho phép; phải công khai cái gì thay đổi. |
| Trace missingness | SECONDARY ROBUSTNESS ANALYSIS; không reconstruction. Coverage strata là mô tả, không can thiệp ngẫu nhiên. |
| Operation | NOT USED trong graph chính; có thể SECONDARY EVIDENCE để diễn giải trace features, không target và không cần đưa C3 vào scope. |
| LLM | Downstream explanation, nhận rank/evidence/coverage; không sửa score hoặc GT, không đưa vào endpoint chính. |
| FlashTicket | Kiểm cùng contrast sau public experiment trên luồng/observability đã có; không chọn số service để phù hợp graph. Vai trò transfer validation, không nơi cứu nhãn thiếu của RE2-TT. |
| Primary risks | Thí nghiệm quá giống ablation cũ; ít scenario; capacity không match; local root quá dễ; graph hầu như đồng nhất qua cases; chi phí preprocessing lớn. |
| Threats to validity | Construct: đo published injected-service ranking, không cơ chế nguyên nhân. Internal: pairing, info/degree/capacity controls, không test tuning. External: một collection, 5 root labels, 6 fault classes; không suy toàn ngành. |
| Falsification | H1 không được hỗ trợ nếu ưu thế mất dưới controls hoặc nằm dưới mức thực dụng; structure controls bằng/tốt hơn thì không quy gain cho đúng relation. Khoảng bất định rộng là inconclusive, không chứng minh H0 đúng. |
| Implementation / research risk | Trung bình cho shared pipeline và graph controls; rủi ro đóng góp cao hơn độ khó dựng graph. Chưa đo wall-clock/RAM, không hứa GPU cần/không cần cho mọi baseline. |
| Task D phải định nghĩa | Feature mapping, reference fitting, mechanism/controls, budgets, split manifests, metric và effect criterion, tie/failure policy, uncertainty và runtime accounting. Không chọn ở Task C. |
| Abandonment | Bỏ nếu không thể làm information/candidate controls hợp lệ hoặc chỉ còn replication graph-on/off không có insight; không thêm GNN/resource/multimodal để bù. Kết quả âm với thiết kế tốt là câu trả lời hợp lệ, không tự động phải bỏ. |

## 6. C2 — Giá trị đồ thị theo thời gian quan sát

**RQ:** Với boundary sự cố đã biết, trong các pipeline/control được chọn, hiệu quả tăng thêm khi dùng quan hệ service để xếp hạng root thay đổi thế nào khi mọi phương pháp chỉ được nhìn cùng prefix telemetry; graph giúp khi bằng chứng còn ít hay phải chờ tích lũy đủ quan hệ mới có ích?

| Trường | Hợp đồng CANDIDATE |
|---|---|
| H0 | Trong miền budget tiền đăng ký, giá trị tăng thêm của graph không thay đổi theo budget ở mức có ý nghĩa thực dụng sau controls; đường gain phẳng kể cả khi dương không bác H0 về tương tác. |
| H1 | Giá trị tăng thêm của graph thay đổi theo budget ở mức có ý nghĩa thực dụng được định trước, sau controls về candidate coverage/evidence/cost. Có thể tăng, giảm hoặc đổi dấu; không bắt buộc đổi dấu hay có gain dương. Chỉ được nói hữu ích ở miền mà hiệu ứng dương cũng đạt tiêu chí thực dụng riêng. |
| Potential contribution | Thực nghiệm về điều kiện graph có ích dưới evidence budget; xác định giới hạn của kết luận lấy từ full-window ranking. Không phải detector, thuật toán anytime hoặc chính sách dừng mới. |
| Closest prior / giới hạn khác biệt | BARO boundary sensitivity, MicroRank trace-count/graph-weight sensitivity và overhead; CIRCA reference/test windows; TORAI known-window ranking. C2 giữ boundary cố định và cắt đồng bộ dữ liệu/graph để đo **graph value × cutoff**, khác việc chỉ quét window tốt nhất. TV-03/04 không chứng minh literature-wide novelty. |
| Dataset / unit / GT | RE2-TT 90 incident; một đường cutoff là repeated measures trong một case. Root-service GT; injection chỉ external known-window boundary. Không có arrival/onset/stopping truth. |
| Graph / input | Service relations chỉ từ history/prefix được phép; cùng telemetry budgets, feature availability và universe policy ở mọi comparator. Span duration chỉ được xem khi span hoàn tất theo semantics đã kiểm; không lén dùng full-case graph rồi cắt features. |
| Output | Ranked services tại cutoff, đường chênh lệch quality graph–control, observed-candidate coverage và xử lý thất bại; runtime/preprocessing tách riêng. |
| Quantitatively evaluable | Ranking/coverage theo event-time cutoff, paired trajectory effects, chi phí xử lý trên cùng máy khi chạy thật. |
| Not evaluable | Collector latency thật, giảm MTTR, onset detection, thời điểm nên dừng an toàn, root operation/path, production false-alarm rate. Stability không là xác suất đúng. |
| Comparison / baselines | Local known-window statistical family và trace-relation family, cùng prefix/history/universe; cùng reproduction/adaptation limits ở §4. Chất lượng endpoint đầy đủ không được đem so với early cutoff của đối thủ. |
| Metrics | MRR/Hit@k theo cutoff và summary đường cong tiền đăng ký; candidate miss, failure/censoring. Time-to-correct-rank chỉ phân tích hồi cứu, không điều khiển model hoặc online stopping. Không lấy nhiều cutoff làm nhiều incident. |
| Required ablations | Graph-free cùng features tại mỗi cutoff; relation controls; tách candidate availability khỏi conditional rank. Có thể đối chiếu graph history cố định hợp lệ với graph tăng theo prefix như phụ, không dùng full-test topology làm primary. Tách evidence waiting khỏi parsing/graph/ranking compute; báo cold/warm cost công bằng. |
| Trace missingness | KNOWN LIMITATION và SECONDARY ROBUSTNESS ANALYSIS. Evidence ít do cutoff không đồng nghĩa mất trace; không repair. |
| Operation | NOT USED như target/node chính; trace aggregate có operation chỉ là SECONDARY EVIDENCE nếu cùng input giữa nhánh. |
| LLM | Downstream evidence packet theo cutoff, nêu thiếu dữ liệu; không quyết định rank đã đúng hoặc khi nào dừng. |
| FlashTicket | Xác nhận cùng câu hỏi trong hệ thật; muốn claim arrival latency phải có mốc arrival/alert độc lập và protocol khác được duyệt. Public event-time findings vẫn đứng riêng. |
| Primary risks | Prefix không thấy target, graph growth trộn với feature stability, fixed schedule shortcut, offline preprocessing nhìn trước, nhiều cutoff làm tăng chi phí và multiple testing. |
| Threats to validity | Construct: event-time budget không là thời gian operator nhận kết quả. Budget tăng đồng thời đổi volume, graph và có thể candidate set; nếu chưa tách bằng controls hợp lệ thì chỉ kết luận về toàn pipeline/policy theo budget, không quy riêng cho một yếu tố. Internal: same-cutoff, no future span completion/templates/normalization. External: injected faults/cùng collection, không bất thường production tùy ý. |
| Falsification | H1 không được hỗ trợ nếu biến thiên graph-value theo budget không đạt tiêu chí thực dụng hoặc biến mất sau coverage/cost controls; không chọn lại cutoff thuận lợi từ test. Gain dương nhưng phẳng chỉ hỗ trợ main effect kiểu C1, không chứng minh budget interaction. “Không significant” không chứng minh đường phẳng/tương đương; cần uncertainty hoặc equivalence protocol phù hợp được định trước. |
| Implementation / research risk | Trung bình–cao: availability-correct replay khó hơn final-window; repeated I/O có thể chi phối. Rủi ro trượt thành window tuning lớn; không hứa real-time. |
| Task D phải định nghĩa | Grid/horizon và summary chính trước test, span/time units, reference/history policy, coverage denominator, budget/caching, paired-curve uncertainty/multiplicity, baseline mapping. |
| Abandonment | Bỏ nếu không tránh future information, chỉ còn “nhiều dữ liệu tốt hơn” hoặc runtime benchmark; không cứu bằng đổi tên online hay thêm detector/repair. |

**C1 và C2 là hai lựa chọn thay thế**, không giao Minh làm cả hai. C1 có estimand giá trị quan hệ ở budget chung; C2 lấy sự thay đổi giá trị đó theo budget làm trọng tâm. Nếu chỉ đo một vài cutoff cho C1, đó là phân tích phụ, không thêm một đóng góp độc lập.

## 7. Kết quả gate và phản biện

| Hướng | Hard data gate | Literature / thesis gate | Vai trò sau khi giảm universe |
|---|---|---|---|
| C1 | SURVIVES WITH RESTRICTED CLAIM | DEFENSIBLE BUT INCREMENTAL; controls phải tách relation information | Đưa vào vòng Red Team như lựa chọn primary có giới hạn |
| C2 | SURVIVES WITH RESTRICTED CLAIM | DEFENSIBLE BUT INCREMENTAL; graph-value × budget, chỉ event-time | Đưa vào vòng Red Team như lựa chọn primary có giới hạn |
| C3 | SURVIVES WITH RESTRICTED CLAIM cho service output | WEAK / NOT A RESEARCH GAP ở formulation hiện tại | Optional secondary experiment; không primary |
| C4 | SURVIVES WITH RESTRICTED CLAIM cho service rank | WEAK / NOT A RESEARCH GAP ở formulation hiện tại | Optional secondary experiment; không primary |
| C5 | SURVIVES WITH RESTRICTED CLAIM cho injection-regime; claim vận hành cần controlled validation | LIKELY ALREADY COVERED; nhãn và scope không đủ cho claim detector rộng | Deferred primary; không bỏ chức năng detection của DT18 |

Đánh giá đầy đủ: [50 câu hỏi hard gate và 13 chiều khả thi](D:/Project/flash-ticket-rca-research/task-c/task-c-feasibility-gate.md), [30 câu hỏi literature gate](D:/Project/flash-ticket-rca-research/task-c/task-c-literature-positioning.md). Không hướng nào đạt STRONG POSITIONING. Đây là phán quyết review về claim hiện tại, không phải kết quả thực nghiệm hoặc phê duyệt của con người.

### 7.1. Cross-review dựa trên bằng chứng

| Vấn đề | Reviewer / nguồn A | Reviewer / nguồn B | Bằng chứng phân xử | Kết luận của main |
|---|---|---|---|---|
| Graph ablation có là gap mới? | CA/CB/CD đề xuất kiểm giá trị relation | Literature gate: graph-on/off đã có | TV-01/02/04; A C.7/C.9/L | Không claim novelty; chỉ C1 contrast thông tin có controls được xem là empirical question incremental |
| C1 và C2 có thực sự khác? | Discovery temporal group muốn graph-value theo horizon | Evidence crosscheck lượt 2: H1 draft chấp nhận gain phẳng, chưa chứng minh tương tác | So trực tiếp RQ/H1 trong draft, không cần raw audit | Đã sửa C2 H0/H1: biến thiên gain theo budget là endpoint; gain phẳng dương chỉ main effect C1; không ép đổi dấu |
| Operation GT thiếu có loại C3? | C-C giữ operation như representation | Feasibility/literature phân biệt data fit và contribution | B §12.7/9; A I/L | Representation chấm service được; C3 bị hạ vì contribution chưa đủ riêng, không vì thiếu operation target |
| Root hiện ở mọi prefix? | Full-case coverage 90/90 dễ bị hiểu quá mức | CD/CF và EC-01 phản bác | COV JSON scope full-case; không có prefix statistic | Giữ target-missing prefixes là miss; cấm thêm root bằng đáp án, không dùng full-case union |
| DIRECT multimodal nghĩa gì? | Task-B samples có L–M direct bins | EC-03 ngăn diễn giải thành log–span/request join | JOIN JSON exact entity-second và zero trace/span matches | DIRECT bin-only trong sample; trace pairs service+time; không toàn-89 hay event correlation |
| Graph semantics và missingness | Trace reviewers có parent relations | B graph/EC-04 giới hạn semantic claims | TRACE semantic-schema matches và parent denominator | Chỉ trace-derived relation; resolution không topology recall, giữ low-coverage, không repair |
| Supervised baseline có được train bằng root labels? | CE-02 gợi ý khi có label budget/bảng riêng | CD/CC/CF và EC-07 phản bác | B §12.10: EVALUATION ONLY không có exception | Không train/tune bằng root labels. Supervised papers là prior, chưa là runnable comparators hiện tại |
| Detection label có độc lập? | CF-01 có hai endpoint | Feasibility và EC-06 giới hạn injection-regime | GT JSON fixed offsets; B §12.9/12 | Chấm regime được, không node/onset/production detection; C5 không vào primary shortlist |
| MicroRank phải đợi năm phút? | Shorthand A và nhiều reviewer | Primary TV-04 phản bác | Official PDF §4.2–4.3, hash trong TV | Bỏ motivation post-alert-wait; giữ prior trace-volume và overhead đã có |
| Algorithm/metric/LLM do thư nêu nguyên văn? | A §M attribution mạnh | CF và EC-09 đối chiếu thư thật | Thư §1; request Task C §5; DT18 | Thuật toán/metric/LLM lấy từ current request; thư chỉ AI; không sửa nguồn cũ hoặc dùng nó thay nhiệm vụ mới |

Không dùng số reviewer đồng ý để phân xử. Các hàng có xung đột nguồn quay về JSON/Task B CLOSED/primary text; khác biệt RQ quay về định nghĩa estimand. Toàn bộ chi tiết ở [evidence cross-review](D:/Project/flash-ticket-rca-research/task-c/task-c-evidence-cross-review.md).

### 7.2. Red Team

[Báo cáo Red Team đầy đủ](D:/Project/flash-ticket-rca-research/task-c/task-c-red-team.md) có 18/18 attacks áp riêng C1/C2 và hai nỗ lực bác bỏ tổng hợp. Main đã đọc toàn bộ, đối chiếu với contracts/nguồn; không lấy verdict reviewer làm phiếu thông qua.

| Findings | Mức độ / xử lý của main | Kết quả cuối cho Phase 1 |
|---|---|---|
| RT-01/02/03/05/06/07/09/10/11/16 | RESOLVED ở hợp đồng: không novelty/causality/GT/join overclaim; known-window oracle công khai; universe không dựa injected labels; C2 H1 được phân biệt với main effect | Các ràng buộc đã có ở §2/4–6; không sửa nguồn A/B để hợp thức hóa ý tưởng |
| RT-08 — utility bị hiểu là thông tin nội tại hoặc tác dụng mọi graph method | MAJOR → RESOLVED bằng đoạn giới hạn mới §4 và RQ §5–6 | Kết luận chỉ trong selected pipelines/controls/budgets. Một implementation thất bại không chứng minh mọi graph vô ích; graph thắng không chứng minh causal mechanism |
| RT-04/12/13 — leakage, dataset shortcuts, fairness | MODERATE, giữ như ràng buộc triển khai bắt buộc; không claim đã kiểm code chưa có | Same input/cutoff/universe; no label fitting; failures giữ denominator; graph nuisance và changing candidates được nêu rõ. Vi phạm lúc thực nghiệm là invalid result, không được che bằng số trung bình |
| RT-14/15 — compute và khả năng tái lập closest work | MODERATE, không có bằng chứng infeasible nhưng chưa có measurement hoặc exact reproduction | Không hứa laptop/real-time hoặc mọi baseline chạy được; closest prior khác runnable comparator. D/E có trách nhiệm chọn/adapt/đo trong scope đã khóa |
| RT-17 — contribution quá nhỏ | **MAJOR còn là rủi ro thật**, không đổi thành “đã giải” | Không gọi novel/strong. Nếu yêu cầu phương pháp mới thì **cả hai hiện chưa đáp ứng**. Đây là lựa chọn giá trị ở C Phase 2, không thiếu fact có thể giải bằng raw audit trong Task C |
| RT-18 — scope quá lớn | MODERATE, giới hạn một primary RQ, secondary chỉ khi cần giải threat | Không làm cả C1+C2, không tự thêm detector/repair/operation/LLM evaluation thành nhiều contribution; không bỏ nhiệm vụ hệ thống đã duyệt |

**Phán quyết cuối:** C1 và C2 **SURVIVES WITH RESTRICTED CLAIM**; không FATAL còn mở trong định nghĩa hẹp. “Resolved” ở đây là sửa claim/hợp đồng Phase 1, không chứng nhận pipeline tương lai leak-free hoặc hiệu quả. Các chi tiết thuật toán, numeric budget/threshold/cutoff và baseline installation thuộc Task D/E theo yêu cầu gốc, không được bịa để đóng checklist hiện tại.

## 8. Ma trận hỗ trợ lựa chọn, không tính điểm tổng

Mọi đánh giá trong bảng là TASK-C INFERENCE. Thứ tự C1/C2 là ID chuẩn hóa, không là xếp hạng. Không gán điểm 1–5 vì chưa có chi phí thực đo và việc cộng điểm sẽ che trade-off.

| Chiều so sánh | C1 | C2 |
|---|---|---|
| Scientific question clarity | Effect của đúng relation khi giữ local evidence; phân biệt được nuisance | Effect graph thay đổi theo budget; phải đo interaction, không chỉ đường accuracy |
| Evidence of unresolved problem | Contrast hẹp có thể kiểm; graph ablation đã nhiều, novelty chưa chứng minh | Boundary/volume sensitivity đã có; matched-cutoff graph-value khác nhưng incremental |
| Dataset support | Full-case observed graph và service targets đủ cho task hẹp | Timestamps đủ để đặt event-time replay; prefix coverage không đảm bảo và phải chấm miss |
| Ground-truth strength | Một published root-service mỗi case; không independent causal mechanism | Cùng service target; không arrival/onset/stopping labels |
| Evaluation validity | Pairing và same-feature controls; graph nuisance phải được tách | Thêm dependence giữa cutoffs, censoring, future-information và multiplicity |
| Baseline fairness | Shared local evidence dễ kiểm hơn cross-paper algorithms; adapters phải khai | Mọi baseline phải nhận same cutoff; timing và cache khác có thể làm so sánh sai |
| Reproducibility | Pin dữ liệu/graph policy/split, saved per-case output; corpus chưa đầy đủ tại máy | Cần thêm availability semantics, cutoff provenance và replay policy |
| Engineering feasibility | Trung bình, khó ở controls và preprocessing; một owner RCA chính | Trung bình–cao do replay và kiểm nhìn trước; không xây streaming platform mới |
| Compute burden | Đọc/tổng hợp 67M span có thể chi phối; chưa đo CPU/RAM | Nhiều cutoff tăng chi phí; reuse phải giữ availability, không lén cấp dữ liệu tương lai |
| Risk of thesis dead-end | Dễ thành ablation quá mỏng hoặc local baseline đã đủ | Dễ thành window tuning/runtime table; thêm nguy cơ early root absence |
| Supervisor alignment | Graph reasoning, public RCA comparison; chưa chứng minh detector | Graph reasoning theo evidence budget; không là early anomaly detection |
| FlashTicket integration | Kiểm lợi ích relation trong hệ khác sau public data | Kiểm lợi ích theo budget; arrival-time claim cần dữ liệu arrival riêng |
| LLM integration | Giải thích evidence/rank ở sau; không tăng giá trị định lượng của RQ | Giải thích kèm cutoff/coverage; không chọn thời điểm kết luận đúng |
| Scope containment | Một service-ranking task; C3/C4 chỉ nếu cần làm phụ | Một event-time ranking task; loại online stopping, detector và repair |

## 9. Những hướng loại hoặc hoãn

| Hướng | Gate / phán quyết | Lý do chính xác, bằng chứng | Có thể dùng về sau trong phạm vi nào? |
|---|---|---|---|
| C3 / CC-02 — operation representation | Không giữ RQ chính ở literature/thesis gate | Representation testable theo B-003, **không** bị loại vì thiếu operation GT khi output vẫn service. Nhưng formulation hiện tại chỉ là granularity ablation, prior MicroRank/TORAI đã dùng operation; chưa có contribution riêng đủ rõ | Ablation phụ có điều kiện cho RQ đã chọn; không buộc triển khai, không operation accuracy |
| C4 / CE-02 — graph stage cho service ranking | Không giữ RQ chính ở literature/thesis gate | Contextual score và late graph ranking đều có prior; đổi stage dễ đổi capacity/scorer. Chưa nêu điều kiện khoa học riêng vượt implementation comparison | Comparator phụ nếu cần; không gộp ngầm vào C1 cố định local evidence |
| C5 / CF-01 — detector–ranker graph stage | Hoãn khỏi shortlist chính | Literature rộng đã phủ Eadro/ARMOR. RE2-TT chỉ chấm injection-regime agreement, thiếu independent onset/healthy operating runs; bài toán detector thật cần validation bổ sung | Chức năng detection vẫn thuộc nhiệm vụ DT18; một RQ mới về detector phải có câu hỏi/nhãn riêng, không tự động mở ở Task C này |
| Operation/resource/affected-node/propagation localization | REJECT — NOT EVALUABLE / DATA NOT SUPPORTED | B-002/006: thiếu targets và resource identities/semantics. LLM không bù nhãn | Chỉ xem lại với evidence mới đúng đơn vị và scope được duyệt |
| Trace/topology repair làm đóng góp mặc định | Không giữ RQ chính | Parent-resolution thấp không cho complete graph truth hoặc cơ chế mất trace; A-004 có prior missingness | Robustness/limitations có giới hạn; không tạo repair algorithm |
| Ba modality hoặc GNN như “điểm mới” | WEAK / NOT A RESEARCH GAP | A-003/005: đã có nhiều prior; số nguồn/tên công nghệ không là RQ | Lựa chọn phương pháp sau C2 nếu phục vụ RQ, không novelty claim |
| LLM tạo/chấm nguyên nhân; tự sửa hệ thống | Ngoài scope / không có GT hợp lệ | Task C §19, B-002, nhiệm vụ giải thích downstream | Chỉ giải thích kết quả và gợi ý kiểm tra, không quyết định cuối |

Loại một **RQ chính** không tự hủy chức năng đã duyệt của hệ thống. Không sửa Task A/B, B16, report cũ hoặc các quyết định hệ thống để làm phù hợp shortlist.

## 10. Ranh giới giai đoạn tiếp theo

Các sự thật dataset và lý do loại đã được trả lời; không chuyển chúng thành câu hỏi “cần nghiên cứu thêm”. Giá trị và phạm vi Minh/giảng viên cần chọn được đặt ở cuối báo cáo.

Sau lựa chọn rõ ràng mới thực hiện **Task C Phase 2 — Research Decision Lock**: RQ, hypothesis, scope, contribution claim, target và validation/exclusions. Sau đó Task D định nghĩa method/protocol; Task E mới tái lập/calibrate và xét môi trường cần thiết. Việc để công thức/ngưỡng ở Task D là ranh giới yêu cầu, không phải công việc Task C bị bỏ dở.

## 11. Subagent execution summary

Capacity: **4 concurrent slots gồm main, tối đa 3 workers**. Discovery thực hiện hai đợt, sáu reviewer độc lập; dùng lại phiên A/B/C qua recovery, không biến retry thành thêm reviewer độc lập. Gate được phép xem các đề xuất sau khi cả sáu nộp. Không recursive spawning. Bảng ghi reviewer đã thực thi, không mô phỏng vai trò trong một lượt suy luận của main.

| Execution / vai trò | Bằng chứng thực đọc | Đề xuất / kết quả chính | Phản bác, xung đột và xử lý |
|---|---|---|---|
| C-A / `task_c_reviewer_a` — methodology; có recovery cùng reviewer | A/B đầy đủ, 11 B reviewers, ledger, trace/coverage JSON, governance | CA-01/02 → C1/C2 | Novelty mỏng; controls và falsification thành bắt buộc trong hợp đồng; không chọn winner |
| C-B / `task_c_reviewer_b` — literature; có recovery | A/B, B-A–E trước recovery; I/J/JSON trực tiếp, phần còn lại qua CLOSED/ledger | CB-01/02 → C1/C2 | Không gọi grouped split hoặc thời gian là novelty; kiểm primary TV-01–04 và hạ claim |
| C-C / `task_c_reviewer_c` — dataset; có recovery | A/B/ledger và JSON metadata/coverage/parent/transfer theo provenance bản C | CC-01/02 → C2/C3 | Operation representation không là target; C3 testable nhưng không đủ standalone contribution |
| C-D / `task_c_reviewer_d` — statistics | Request/ledger, A C.1–C.12/J–N, B §12, DT18/roles | CD-01/02 → C1/C2 | Pseudoreplication, uncertainty, nuisance và prefix availability; giữ ở §4–6 |
| C-E / `task_c_reviewer_e` — engineering | A toàn văn, B §12/G, ledger, DT18/roles | CE-01/02 → C2/C4 | Compute không bằng graph size; bốn thành viên không phải bốn RCA full-time. Ngôn ngữ supervised-if-budget bị EC-07 siết theo B evaluation-only |
| C-F / `task_c_reviewer_f` — supervisor alignment | A toàn văn, B §12/I/F, request, ledger, thư lịch sử, DT18/roles | CF-01/02 → C5/C2 | Phát hiện sai attribution thư; EC-09 bổ sung AI≠LLM provenance. Không lấy ranking thay detector |
| `task_c_gate_literature` — contribution gate | A đầy đủ, normalized universe, ledger/TV và request | 30 câu hỏi gate; C1/C2 incremental, C3/C4 phụ, C5 hoãn | Không còn trì hoãn bằng NEEDS TARGETED VERIFICATION; không world-first claim |
| `task_c_gate_feasibility` — hard data/thesis gate; recovery cùng reviewer sau quota | B §12, A I–L, ledger/TV, CC/CD/CE/CF, request, DT18/roles; LG/EC đối chiếu sau phân tích ban đầu | COMPLETE: 50 câu hỏi dữ liệu, 13 chiều luận văn; C1/C2 restricted, C3/C4 phụ, C5 hoãn | Sửa cấm counts quá rộng: counts telemetry trong window hợp lệ được dùng, metadata/future oracle bị cấm. Đồng bộ C2 interaction. Không chạy raw audit |
| `task_c_evidence_crosscheck` — evidence/provenance | B §12, COV/TRACE/GT/JOIN JSON, request §5, thư, DT18, đoạn reviewer | EC-01–10; lượt đọc draft kiểm propagation corrections | Rootlabels không được train; bins không là events; full case không là prefix; correction dùng đúng nguồn |
| `task_c_red_team` — lần chạy bị ngắt | Đã giao input sau reduced-set barrier; không có báo cáo lưu để xác nhận phần thực đọc | Không tính là review hoàn tất hoặc một phiếu xác nhận | Agent không còn trong live list ở lần tiếp tục; thay bằng execution dưới, không làm lại discovery/gates |
| `task_c_red_team_recovered` — thay thế Red Team | Canonical contracts và toàn bộ ledger/universe/FG/LG/TV/EC, request §21 | COMPLETE: 18 attacks/cả hai survivor, hai nỗ lực kill; không sinh candidate mới | RT-08 làm rõ method-specific utility; RT-17 giữ MAJOR contribution risk; không FATAL task hẹp, xem §7.2 |
| `task_c_final_contract_check` — kiểm yêu cầu bàn giao độc lập, chỉ đọc | Request §23–32, canonical draft, universe và raw IDs | Đủ 28 fields/survivor, 14 matrix dimensions, 12→5 mapping, links và no-algorithm/no-winner boundary | Yêu cầu thêm mọi execution, đóng placeholder Red Team và đặt lựa chọn con người ở cuối; main cập nhật |

Original reports: [thư mục reviewer độc lập](D:/Project/flash-ticket-rca-research/task-c/phase-1-independent-candidates). Main đọc toàn bộ A/B và 11 reviewer B trước discovery; không gán việc đọc đó cho tất cả subagents. Các bản A/B/C trước bị quota sau lưu vẫn hoàn chỉnh; feasibility gate lần này bị ngắt trước lưu nên phải phục hồi hoàn tất. Các trạng thái này đều được ghi trong [resume history](D:/Project/flash-ticket-rca-research/task-c/task-c-resume-state.md).

## 12. Kiểm tra chất lượng và phạm vi bàn giao

| Yêu cầu cuối Task C §32 | Bằng chứng hoàn tất / vị trí |
|---|---|
| Đọc A/B và reviewer bắt buộc | Phase-0 ledger, inventory 65 inputs, provenance từng reviewer; main ingestion toàn bộ |
| Dùng B CLOSED, không pre-close | §1–2, B §12 và đối chiếu EC |
| Operation representation khác operation GT | §2/5/6/9, C3 hard gate |
| Root-service khác anomaly-node label | §2/9, C5 hard gate |
| Không nhập số service TT của collection khác | §2: 27 union và 20–27 full-case candidates đúng pinned release |
| Parent relation không tự thành CALLS/causal edge | §2/4 và EC-04 |
| Trace thiếu không mặc định đóng góp chính | §4–6: robustness/limitation, không repair |
| LLM không tạo GT | §4–6; downstream explanation |
| Không chọn trước khi đủ sáu reviewer | Normalized universe barrier, frozen reports, execution history |
| Không chọn algorithm/threshold | Contracts chỉ mechanism/baseline families, Task D giữ exact method |
| Không cài Ubuntu/WSL/framework | Không có install hoặc baseline execution; Task C chỉ đọc/ghi tài liệu và kiểm nhẹ |
| Mỗi RQ falsifiable | H0/H1/falsification/abandonment §5–6; effect size chưa tùy tiện đặt |
| Mỗi RQ có đường đánh giá hợp lệ | Service GT; all-case denominator; paired structure/cutoff controls; 50-cell hard gate |
| Mỗi hướng loại có lý do | §9; universe giữ nguyên traceability 12 raw → 5 normalized |
| Red Team thử bác mọi survivor | COMPLETE: 18/18 attacks/cả C1 và C2; §7.2 có phản hồi và giới hạn rủi ro chưa thể giải bằng evidence |

**Những gì chưa được xác nhận bằng chạy thí nghiệm:** hiệu quả phương pháp, runtime/RAM, khả năng chạy nguyên bản các baseline, prefix root coverage, compatibility metric/log toàn corpus và mức đóng góp cuối sau khi có kết quả. Task C kết luận về câu hỏi có thể kiểm và cách giới hạn claim; không biến các đầu việc thực thi được giao cho D/E thành kết quả đã đo. Không có giả định rằng bất kỳ hướng nào chắc chắn tạo paper hoặc đạt điểm luận văn.

**Nguồn nên dùng khi viết báo cáo sau này:** task/unit/GT, evidence semantics, closest prior và exact contrast, controls/leakage, failures/negative results, giới hạn external validity, phân biệt nghĩa vụ hệ thống với RQ hẹp. Chỉ đưa gain/accuracy/chi phí vào báo cáo khi đã chạy và kiểm chứng.

**Phạm vi thay đổi:** một báo cáo canonical này và hồ sơ Task C ở research workspace. Giữ nguyên Task A/B, raw data/manifests, audit Python, sáu reviewer discovery, source register, README, B16, ứng dụng và hạ tầng. Không commit/push; không thay decision register hoặc nâng hướng lên APPROVED/DECIDED.

**Kiểm chứng bàn giao:** đối chiếu SHA-256/kích thước của 65 đầu vào với inventory: không thiếu, không thay đổi; sáu báo cáo discovery giữ nguyên bản. Independent contract check xác nhận đủ 28 trường mỗi survivor, 14 chiều so sánh và ánh xạ đầy đủ 12 → 5. Red Team đã lưu đủ 18 mục, xác nhận bản sửa RT-08; còn một MAJOR về giá trị đóng góp và sáu MODERATE về thực thi/tái lập, được trình bày ở §7.2. Đây không là fresh integrity audit của toàn raw corpus hoặc kiểm nghiệm thuật toán.

[Governance audit](D:/Project/flash-ticket-rca-research/task-c/task-c-governance-validation.txt) hoàn tất PASS, một cảnh báo do working tree có năm tệp thay đổi: bốn tệp đã có từ trước và báo cáo Task C mới. Phạm vi Task C đã được người dùng giao hoàn tất; không chỉnh bốn tệp trước đó. [Bản kiểm tra bàn giao và hash các kết quả](D:/Project/flash-ticket-rca-research/task-c/task-c-final-validation.json) ghi các kiểm tra kỹ thuật, đường dẫn và giới hạn kiểm chứng. Kiểm tra hình thức không thay phán quyết khoa học.

**TASK C PHASE 1: COMPLETE.** **C PHASE 2: NOT STARTED — HUMAN DECISION REQUIRED.** Các hướng vẫn là CANDIDATE; review hoàn tất không tự nâng chúng lên DECIDED hoặc APPROVED. Không còn phần review Phase 1 bị bỏ dở do quota.

## 13. Lựa chọn cần Minh/giảng viên chốt

1. **Trọng tâm:** chọn một trong C1 — lợi ích của quan hệ service ở ngân sách quan sát chung, hoặc C2 — lợi ích đó biến thiên thế nào theo ngân sách quan sát. Không mặc định làm cả hai.
2. **Mức đóng góp chấp nhận:** nghiên cứu thực nghiệm có kiểm soát, có thể thu được kết quả âm, hay bắt buộc có phương pháp mới. Với yêu cầu phương pháp mới, cả hai hướng hiện chưa có căn cứ đáp ứng.
3. **Phạm vi kiểm chứng:** giới hạn kết luận công khai ở RE2-TT rồi kiểm chứng trên FlashTicket theo nhiệm vụ hiện hành; chỉ thêm hệ công khai thứ hai nếu chủ động chọn mục tiêu suy rộng và chấp nhận công sức tương ứng. FlashTicket vẫn là phần bắt buộc trong cả hai lựa chọn.
