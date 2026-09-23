# Task D — Method and Experimental Protocol Specification

- Phiên bản: **TD-v1.1**, 2026-09-23. Chủ sở hữu/người phê duyệt execution: **Lê Văn Minh**.
- Loại: `FORMATION / CANONICAL_DECISION` về đặc tả; trạng thái tài liệu **REVIEW_READY** sau independent delta review TD-v1.1. Mọi lựa chọn kỹ thuật dưới đây là **CANDIDATE**, được agent đặc tả theo ủy quyền; chưa là `USER_CONFIRMED`, `DECIDED` hoặc human `APPROVED`.
- C1 và vai trò C2–C5/LLM giữ theo [TC-P2-v1](task-c-research-decision-lock.md), [RCA-001–017](RESEARCH-DECISIONS.md). Không có kết quả hiệu năng mới trong D.
- Nguồn nhiệm vụ: [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md); lộ trình/cổng E theo [MRP-v1.1](MASTER-RESEARCH-PROGRAM.md). D–G không chờ FlashTicket.
- Ủy quyền lịch sử cho TD-v1.0: hợp đồng “RCA RESEARCH CONTINUATION EXECUTION CONTRACT”, Minh gửi 22/09 và gửi lại giống byte 23/09, SHA-256 `61bc0d003b97d1a74e5d96fad68945444d4571baf43e0729c898e09ef6bda96e`: kiểm Pre-D, đặc tả/phản biện D, dừng trước E. Lệnh này đáp ứng điều kiện lệnh riêng của RCA-017; không sửa nội dung lịch sử RCA-017. Ủy quyền revision hiện hành ở §0.

## 0. Changelog và quyền nguồn TD-v1.1

Revision theo yêu cầu Minh “Independent Reconciliation + TD-v1.1 Revision”, §§26–30, ngày 23/09: chỉ specification/evidence/synthetic checks; không chạy E–I, không thêm quyết định con người. Nguồn kích hoạt là [hướng dẫn giảng viên do Minh cung cấp](../evidence/advisor-direction/2026-09-23-huong-dan-do-minh-cung-cap.md), **ngày/kênh gửi gốc NOT VERIFIED**, khác thư 22/08. Không sửa thư cũ.

TD-v1.0 được bảo tồn tại commit P `6bd04e625926e923301cc6bb1433682f01fd87fc`, protocol SHA-256 `9822a92081fb5d958fde35b68d274906bd36ee66288017645a7ec6840a2c1c4d`; các ledger/review/validation v1.0 ở W giữ nguyên. Thay đổi v1.1: §§0/1.1 rationale; §2 thêm quyền input logs riêng C5; §4.2 phân vai logs; §5 sửa lý do bỏ hướng (không đổi toán); §7 thêm secondary NDCG; §§8–9 scope clarification; §10 thay C5 metric-only bằng graph-conditioned multimodal candidate và bốn configs hỗ trợ; §§11–13 packet/fixtures/freeze tương ứng; §14 giới hạn claim; §15 public-extension gate và advisor coverage.

**C1 không đổi** RQ, telemetry M+T, candidate/window/fit, local score, L/O/R solver/rewiring/seeds, baselines, split30/60, δ, primary metrics/inference/verdict/failure rules. Không mở C2, scored C3 hoặc cross-placement C4 study. NDCG phụ không thêm confirmatory test. C5 và triggered MTL diagnosis dùng namespace riêng; không lấy kết quả đó thay C1.

Nguồn phản biện trước sửa: [FIRST PASS](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-source-first-review.md). [Reconciliation evidence](D:/Project/flash-ticket-rca-research/task-d/td-v1.1-reconciliation-evidence.md) sở hữu conflict matrix, modality audit, GT matrix, review adjudication và exact file inventory; đây không là protocol thứ hai.

**RESOLVED — xác nhận Minh 23/09, RCA-018–021:** hướng dẫn thuộc giai đoạn RCA là trọng tâm trước đây; còn giá trị cho phương pháp RCA, không dùng để giảm nhẹ xây dựng FlashTicket. Giữ tên và nhiệm vụ DT18, không sửa gate H/B11. Ngày/kênh gửi gốc vẫn NOT VERIFIED. Việc mở rộng public-data validation có gate riêng §15, không làm dataset mới thành điều kiện C1. Bốn xác nhận này là về nguồn/phạm vi, không duyệt các lựa chọn kỹ thuật CANDIDATE.

## 1. Cơ sở, gate và phạm vi kết luận

Pre-D: **READY WITH D-OWNED CONDITIONS**. C lock xác định RQ; [B digest](task-b-dataset-capability-summary.md) và [B CLOSED §12](D:/Project/flash-ticket-rca-research/dataset-audit/TASK-B-RCAEval-audit.md) xác lập target service và telemetry khả dụng. Sửa tối thiểu: notice provenance tại Task A §M; status B2B thành COMPLETE dựa trên B closure. Không mở lại A/B/C, không raw audit lại 67 triệu spans. [Evidence ledger](D:/Project/flash-ticket-rca-research/task-d/task-d-evidence-ledger.md) giữ phân xử F01–F12, primary-source check và input versions; [review](D:/Project/flash-ticket-rca-research/task-d/task-d-independent-review.md) giữ ý kiến độc lập trước khi xem findings cũ và review bản D.

| Phát biểu | Trạng thái / nguồn | Quyền dùng |
|---|---|---|
| C1 primary; contribution empirical/system/reproducibility; valid negative được chấp nhận | USER_CONFIRMED, RCA-001–004 | Không mở lại vì graph có thể thua |
| RE2-TT 90 ca, 5 roots × 6 faults × 3 repeats; metrics/traces 90, logs 89 | FACT, B §12 | Thiết kế phép đo service-root; không tự tạo GT khác |
| Graph observed, parent resolution không phải topology recall; raw joins chưa kiểm full-89 | FACT, B §12 | Giới hạn semantics và obligation E/F |
| Thuật toán, cửa sổ, đối chứng, split, δ, thống kê dưới đây | CANDIDATE, TD-v1.1 | Protocol để Minh duyệt, không lời Minh đã chọn |
| Loader toàn corpus, runtime, hiệu quả, ngữ nghĩa duration, fidelity executable baselines | OPEN, E/F owners | Kiểm trước campaign; không gọi D là bằng chứng đã chạy |

**C1 operationalized:** trên benchmark RE2-TT đã có lịch sử khảo sát/kết quả, đo thay đổi reciprocal rank của published root service khi đưa **bố trí láng giềng vô hướng, nhị phân lấy từ quan hệ trace quan sát trong reference window** vào cùng phép lan truyền điểm cố định, so với local-only và cấu trúc đối chứng. Phạm vi là utility của cơ chế và controls này. Không đo lượng thông tin nội tại, mọi graph method, toàn topology, hướng nhân quả hoặc sự cố production.

Đơn vị ghép cặp là incident; cụm phân tích là scenario `root service × fault`, ba repeats cùng cụm. Output là một score/tie group cho mỗi service trong cùng tập ứng viên. Hai estimands trên tập evaluation đã định:

```text
dL_i = RR(O_i) - RR(L_i)
dR_i = RR(O_i) - mean_j RR(R_ij)           # không RR của mean score
Delta_k = mean_scenario mean_repeat dK_i  # k=L,R; 20 scenarios, 60 cases
```

Thay đổi thông tin: adjacency O/R; L là identity operator. Giữ nguyên records, masks, reference, candidates, score vector, precision, solver, α, không gian tham số và quyền nhãn. Nuisance còn lại: topology ảnh hưởng kernel lan truyền, edge visibility phụ thuộc traffic, reference quan sát chưa chắc healthy, kịch bản/fault/campaign phụ thuộc, historical exposure. Target population trực tiếp là 60 evaluation incidents/20 cells của collection này; 90 ca chỉ là descriptive package tổng thể. Không claim unseen service, unseen fault, independent new campaign hoặc cross-system generalization.

### 1.1 Vì sao dùng cơ chế đơn giản này?

[Task A](task-a-ban-do-bang-chung-doc-lap.md) §§C/D/G/H/L và C Phase1 §§4–5 đã khảo sát kỹ thuật; không chấp nhận suy đoán “A chỉ có tên phương pháp”. Khoảng cần nối là prior limitation → lựa chọn D → quan sát có thể bác bỏ:

| Prior evidence / giới hạn | Lựa chọn CANDIDATE trong D | Điều phép thử có thể nói / không nói |
|---|---|---|
| MicroRCA/MicroRank dùng graph sau local detection; ablation nhiều prior thay representation/capacity | C1 giữ robust M+T evidence và scorer, chỉ thay identity/observed/rewired relation | Đo incremental arrangement utility trong cơ chế cố định; không chứng minh prior thiếu mọi fair ablation |
| Eadro/DéjàVu cần root-supervised fitting; B/C cấm dùng root/fault fit | Zero trained parameters, robust rolling reference, fixed propagation | Khả thi về quyền nhãn; chưa chứng minh cạnh tranh SOTA hoặc tối ưu |
| Graph averaging có thể giảm isolated spike hoặc lan triệu chứng sang neighbor | C5 đặt smoothing trước threshold, matched local control | Kiểm lợi/hại của graph-conditioned score với calibration quy định; không tự là structural-change detector |
| TORAI/ARMOR/Eadro đã dùng multimodal/missingness | Nested C5 node-evidence comparisons, masks và fixed weights | Đo conditional evidence contribution; không novelty hoặc missing-aware learning |
| Parent direction quan sát được nhưng causal direction chưa có GT | C1/C5 cố ý dùng undirected binary graph để giới hạn degrees/controls và số lựa chọn | Mất observed direction là hạn chế có chủ đích; B không buộc bỏ hướng |
| Log-span correlation và duration/status semantics chưa chứng nhận | Exact service/time log counts; trace occurrence; operation support | Bounded volume-change evidence; không semantic log anomaly hay latency localization |

α=.5, weights, windows và percentile là convention cố định để phép thử reviewable, **không được suy là lựa chọn tốt nhất**. C1 test informativeness gates, operational failures và support rules là ba tầng khác nhau: dữ liệu có contrast → thực thi được → đủ bằng chứng practical benefit. Các ngưỡng compound ở §7 không là định lý khoa học; point effects và mọi thất bại vẫn phải báo dù verdict INCONCLUSIVE. Comparator package §6 là bounded context, thiếu runnable external graph detector/ranker vẫn hạn chế so sánh ngoài; internal control không thay một SOTA benchmark.

## 2. Data identity và hàng rào thông tin

Dataset `phamquiluan/RCAEval` revision **afeacb11bcc94dadfd1c8f483ee4377b2b8b614e**. Metadata SHA-256 **c49a288920dbba2e8e724679a14636d5c7eb2b45426bba14007ef79a6c0ab1bb**. Full-case B audit: 67,345,051 spans, 20–27 trace services/case, 27-service union, 161 operation pairs; **không dùng union làm vocabulary**. Giữ cả bảy ca parent resolution <90% và ca thiếu logs.

| Lớp quyền | Nội dung | Nơi được đọc |
|---|---|---|
| MODEL_INPUT | Numeric metric bins, trace occurrence counts, case-local opaque service keys, permitted adjacency, masks; C5/MTL profile thêm log-count deviations (§10), C1 không thêm logs | Scorer chỉ nhận arrays/indices đã chuẩn hóa |
| REFERENCE_ONLY | Cùng telemetry trong reference interval của incident | Fit median/IQR và dựng graph; không reference cross-case |
| CALIBRATION_ONLY | Development telemetry cho kiểm tương thích; C5 score quantile không nhãn | E/F; không root/fault-based feature/parameter search |
| EVALUATION_ONLY | Root/fault labels, scenario grouping, known-window boundary qua controller | Evaluator; boundary chỉ chọn intervals Mode A; không chuyển numeric inject_time vào scorer |
| PROVENANCE_ONLY | Revision/hash, repetition/suite/system, case identity mapping, retrieval paths, commands | Manifest/control plane; không numeric/text feature hoặc LLM prompt |
| FORBIDDEN_INPUT | Case/path answer tokens, root_cause.txt và presence, label text, metadata normal/fault counts, full-case end/count oracle, union test graph, historic predictions | Cấm trong model/evidence/LLM; không đọc root_cause.txt trong loader |

Execution architecture: evaluator/retriever tạo opaque `case_handle=SHA256('TD-v1|'+caseID)[:16]`, kiểm collision; ánh xạ handle↔label/path lưu riêng. Handle dùng định tuyến/log, **không** là feature. Service identity exact literal chỉ dùng join, sau đó thay bằng index case-local; tên service không tạo embedding/priors. Shared normalized input có hash; L/O/R cùng hash features/masks/candidates. Worker không có đường dẫn label-bearing, không mount metadata/GT; lỗi đã lọc paths trước khi vào evidence. Case handle không gửi LLM. Numeric inject_time, relative injection offset và tên fault không có trong packet.

E bắt buộc test canary: đổi root/fault/case/path/metadata counts với telemetry giữ nguyên không đổi normalized arrays/ranks. **Giữ pinned execution handle và 32 seed draws cố định trong canary**, chỉ remap metadata/path: handle được cấp một lần, không hash lại modified case string. Worker không nhận chuỗi đó. Đổi service-key permutation phải đồng thời hoán vị arrays và các realized control graphs; output tương ứng, tie metric không phụ thuộc tên. PRNG draw generation không được lén đọc service/root/fault text. Nếu canary thất bại, dừng freeze; không tính kết quả đó là valid negative.

## 3. Exposure và chia dữ liệu

[Exposure ledger](D:/Project/flash-ticket-rca-research/task-d/task-d-exposure-ledger.md) sở hữu lịch sử chi tiết. A/B/C đã thấy schema/metadata/candidates/topology summaries; selected raw RE2-TT `auth/cpu` repeats 1,2 đã được kiểm. **E1 còn báo cáo historical baseline outputs/metrics/strata trên TT90**, gồm phân tích hậu nghiệm gợi ý fusion. Exact raw-revision equivalence chưa được chứng minh vì thiếu old manifests. Không dùng lời “untouched test”, “chưa từng thấy score” hoặc “độc lập hoàn toàn”. D tiếp tục **prospectively frozen evaluation of new contrasts on a previously studied benchmark**; uncertainty không xóa selection dependence lịch sử.

Split xác định trước new outputs, evaluator-only: sắp root labels và fault labels theo byte UTF-8 tăng dần. Với root index r=0..4, development gồm fault index `(2*r)%6` và `(2*r+1)%6`; cả ba repeats đi cùng. Các cells còn lại là evaluation. Không đổi split theo difficulty, coverage, scores hoặc baseline failures.

| Root (evaluator only) | Hai fault development |
|---|---|
| ts-auth-service | cpu, delay |
| ts-order-service | disk, loss |
| ts-route-service | mem, socket |
| ts-train-service | cpu, delay |
| ts-travel-service | disk, loss |

30 development incidents/10 cells; 60 evaluation incidents/20 cells. Mỗi root 6/12 ca dev/eval; cpu/delay/disk/loss mỗi fault 6/9, mem/socket 3/12. **Near-balanced**, không gọi balanced mọi chiều. Hai raw samples đã xem nằm development. Split không tạo holdout service hoặc fault vì mọi root/fault vẫn có mặt ở hai phía. Lý do dùng cells: ngăn một repeat của cùng scenario làm development còn repeat khác bị gọi là held-out scenario; giữ đa dạng faults/roots cho pipeline checks với phần lớn incidents dành new locked contrast.

E/F không được chấm development root accuracy để chọn phương pháp: evaluation-only policy áp cả dev labels. Pilot chỉ dùng input compatibility, invariance fixtures, numerical stability, missingness, resource measurements và unlabeled reference/query diagnostics. Không có supervised training hoặc performance-based hyperparameter tuning. E1 score cũ không chọn feature, alpha, split, cutoff hoặc đối chứng của D.

Trước G, evaluator có thể kiểm schemas/coverage/control mobility trên evaluation inputs trong validation-only job **không sinh ranking hoặc root metrics**, ghi tiếp exposure; technical incompatibility được sửa D có review, không gọi dữ liệu raw-unseen nữa. New case/arm predictions chỉ mở sau freeze. Mọi unseen historical outcome/design exposure mới được phát hiện phải thêm ledger và hạ claim phù hợp, không xóa ca để tái tạo “sạch”.

## 4. Mode A: cửa sổ, candidate và local evidence

**Known-window archival RCA**, không detector, không early/event-time delivery benchmark. Controller nhận supplied boundary τ, xuất reference **[τ−300s, τ)** và query **[τ, τ+300s)**; chia 10s, mỗi phía 30 bins. Khoảng năm phút là horizon cố định để có 30 summary bins mỗi phía, không chọn từ midpoint/full-case duration hoặc kết quả. Không window search ở E. Records ngoài hai intervals không được dùng. Controller che τ và absolute epochs sau slicing; scorer chỉ nhận ordered arrays.

Trace records chọn bằng `startTimeMillis/1000` trong interval. Core **không dùng duration/statusCode**, tránh đơn vị duration và end-of-span availability chưa xác minh; không suy status code là HTTP/error. Đây là snapshot hồi cứu theo start time của record đã thu, không bảo đảm record đã tới collector lúc start. Graph parent/child chỉ được nối nếu **cả hai** thuộc reference. Query graph không tham gia C1. Không history từ incident khác.

**Candidate V:** exact nonempty literal trace.serviceName quan sát trong reference **hoặc query**. Query-only services giữ lại như isolates nếu không có ref edges. Không nhập mọi metric entity (nhiều token là resource chưa định kiểu), không append root từ GT, không lọc xuống injected services. V chung cho mọi arm/baseline service output; toàn case 90/90 target visible của B không bảo đảm target ở horizon này. Missing root nhận RR/Hit=0. Nếu trace hoàn toàn không parse được thì shared-input failure, không lấy GT hoặc metric union cứu V.

### 4.1 Numeric channels và aggregation

Metrics: chỉ map suffix tường minh `latency-90, latency-50, workload, diskio, latency, socket, error, load, cpu, mem` bằng longest exact suffix `_<suffix>`; phần còn lại phải **bằng nguyên serviceName trong V**. Không regex alias/fuzzy match, không đoán `-mongo` là service. Unknown suffix/unmatched entity ghi coverage và không dùng. Danh sách đủ cho sample đã audit; field mới đòi evidence/patch D trước freeze. Không dùng tên root/fault để chọn suffix. Tất cả suffix hợp lệ đều vào như nhau, không chọn sau xem performance.

- Metric bin là median finite observations trong 10s; cần ≥5 distinct seconds. Duplicate identical entity-second rows collapse; conflicting duplicates invalidate **toàn channel đó trong cả reference/query của incident**, score0+mask; các channels khác giữ nguyên, không shared-case failure chỉ vì một channel xung đột. Ghi số conflicting keys; không chọn một dòng tùy ý. Không forward-fill/interpolate.
- Trace channel là `log1p(n)` với n là số distinct `(traceID,spanID)` của service trong bin. Zero là **không có observed spans**, chỉ khi file/window đã nạp thành công; không chứng minh zero actual traffic, mất file không được biến thành0. Same key + different payload làm shared-case preprocessing failure; identical duplicate được collapse và đếm.
- Metric channel đủ reference khi ≥24/30 bins hữu hạn; đủ query khi ≥24/30. Trace count reference chỉ dùng cho service có ≥1 span trong reference; query-only service có evidence unavailable, không coi reference imputed zero là anomaly lớn. Missing modality không được normalize lại trọng số.
- Không loại constant channels. Fit reference median `c` và IQR `a`; quantiles dùng linear interpolation type 7. `scale=max(a,0.01*median(abs(reference)),1e-12)`. Floor quy mô chỉ chống zero-IQR, không xác suất thống kê.

Cho từng channel hợp lệ, `z_t=min(20, abs(query_t-c)/scale)/20`; channel score là percentile 90 của z_t hữu hạn. Missing channel score 0 **kèm unavailable mask**, không gọi healthy. `m_v=max(metric channel scores)` (không channel hợp lệ thì 0), `t_v=trace channel score` (không hợp lệ thì 0). Local score **l_v=(m_v+t_v)/2**. Dữ liệu đầu vào, các m/t và l giữ cùng byte giữa L/O/R; không fitted embeddings hoặc learned parameters.

Max-channel có multiplicity bias và nhiều metric channels có thể ưu tiên một service. Nó không thay giữa C1 arms, nhưng vẫn là giới hạn của local representation. G báo channel counts, saturation fraction, constant/absent-reference channels, all-equal scores, query-only candidates và score ranges theo case. Nếu pipeline chỉ tạo toàn tie/missing, kết quả gọi signal starvation/inconclusive cho C1 informative utility; không dùng label để chọn channel cứu kết quả.

### 4.2 Logs và operation support vẫn có chủ sở hữu

Logs **không vào C1 score**; trong TD-v1.1 chúng còn vào C5/triggered integrated profile theo §10. Phần còn lại của §4.2 là support contract chung, không phủ định log scoring riêng đó. Với C1: thêm join/template choices và một ca thiếu sẽ gây thêm một contrast modality không cần để đo relation utility. F vẫn phải nạp/map logs làm bằng chứng phụ trên service graph, đáp ứng hướng pipeline log/trace/metrics. Chỉ exact `container_name==serviceName`; metric-log join exact `(entity,epoch second)` là bin association, không request/event link. Log-trace chỉ service+time. E/F validate schema, unit, keys, missingness trên mọi file thực dùng; không tuyên bố 89/89 đã raw-verified. Missing logs không đổi core rank/candidate/denominator.

Supporting operation keys là literal `(serviceName,operationName)` từ same intervals; ghi ref/query counts và top 3 theo absolute count-rate difference (tie by opaque key), không semantic endpoint, không root-operation prediction. Chỉ packet evidence, không l_v hoặc adjacency. Log evidence gồm per-service ref/query bin counts, availability, source hash và tối đa3 sample records/service trong query: chọn3 SHA256(`source_file_sha256|original_row_ordinal`) nhỏ nhất trước redaction; không selection theo body text. Trusted adapter redacts credentials/tokens/personal identifiers theo versioned rules trước packet; không LLM-generated sanitization. Không chọn bằng root label, severity keyword hoặc `root_cause.txt`; không cho raw log text thành instruction của LLM.

## 5. Graph, cơ chế ranking và controls

Directed evidence `A_dir(u,v)=1` khi tồn tại resolved `(traceID,parentSpanID)->(traceID,spanID)` **trong cùng reference window**, parent và child khác literal service. Deduplicate relation type; repeated spans không tạo weight. Same-service relations giữ count provenance nhưng không edge graph. Unresolved/null parent không nối/đoán; denominator resolution là nonnull parent references trong selected reference và numerator same-trace parents tìm được trong reference. Báo **unresolved within selected reference, potentially boundary-censored**; không đọc outside-window spans để tách missing/censored, không đồng nhất resolution với topology recall. Giữ directed raw relation list cho evidence.

C1 dùng undirected binary projection `A(u,v)=max(A_dir(u,v),A_dir(v,u))`, diagonal 0. Chọn đối xứng là giới hạn cơ chế để kiểm binary neighbor arrangement với controls đơn giản; **không** phải vì observed parent→child direction vô dụng. B hỗ trợ hướng quan sát, chỉ không chứng minh causal direction. Bỏ hướng có thể mất signal; kết quả không suy sang directional methods. Không thử hai hướng rồi chọn thắng. Tên **TRACE-DERIVED OBSERVED SERVICE RELATION**; undirected projection là processing choice, không assertion CALLS/causal.

Nếu degree d_v>0: `P_vu=A_vu/d_v`; nếu isolate: `P_vv=1`. Primary O giải:

```text
q = (1-alpha)*l + alpha*P*q ; alpha = 0.5
q0=l; repeat qnext=0.5*l+0.5*P*q
stop max_abs(qnext-q)<1e-12; max 100 iterations, float64
rank decreasing round(q,12), retain ties; không score normalization theo tổng
```

L chạy **cùng solver với P=I**, nên q=l. R dùng cùng solver/alpha/input trên adjacency đã perturb. α cố định bằng nhau, không tune bằng nhãn; zero trained parameter ở cả ba. α=.5 giữ local evidence và hạn chế contraction; không nhận là tối ưu. Row-stochastic averaging bảo đảm constant-input constant-output, không tự sinh degree ranking khi l hằng. Không đảm bảo hoàn toàn loại centrality khi l không hằng.

| Arm / mục đích | Giữ | Thay hoặc phá | Giới hạn |
|---|---|---|---|
| L — local/identity | l, V, masks, solver, α, capacity | Loại cross-service mixing | O−L gồm mọi tác dụng của mixing/structure, chưa riêng relation arrangement |
| O — observed | Cùng mọi yếu tố trên | Thêm observed binary adjacency | Chỉ utility cơ chế late smoothing này |
| R — structural reference | Per-node degree, exact component membership, binary weights, l, V, α, capacity | Đổi neighbor identities theo §5.1 | Không giữ spectrum, shortest paths/bottlenecks, motif hay effective smoothing |

**Rất cụ thể về claim:** O−R kiểm arrangement có ích hơn perturbation reference với labeled degrees/components cố định. Không phải semantic correctness test, uniform-random graph null hoặc causal intervention. O−L và O−R cùng dương mới hỗ trợ C1 hẹp. Không dùng weight/direction destruction, GNN hoặc thêm scorer khác vào primary contrast để tăng số phép thử.

### 5.1 Structural control executable rule

32 independent chains per incident, mỗi chain khởi tạo A. PRNG PCG64; seed là 64 bit đầu SHA256(`TD-v1|graph|case_handle|j`), j=0..31; sort opaque edge keys trước sampling. Chọn đều hai distinct current edges, flip orientation mỗi edge với coin .5, đề xuất `(a,d),(c,b)` từ `(a,b),(c,d)`. Chỉ chấp nhận khi bốn endpoints khác nhau, không self/duplicate/existing edges và **exact connected-component node partition giữ nguyên**. Giữ rejected step như self-transition. Thực hiện đúng `200*max(1,edge_count)` proposals/chain, không adaptive stop theo score/overlap. Save final adjacency/hash; không chọn seed tốt/xấu.

Mean **per-chain RR/Hit**, MC SD/SE báo riêng; 32 seeds không tăng N. Chain generation/solver failure nhận RR/Hit0 cho **chain đó**, giữ mẫu số32; ghi planned/completed/failed chains. R resource limit §8 tính **aggregate all32 chains**; hết thời gian thì mọi remaining chain là failure0, không average successful-only. Any failed chain là R arm-specific failure cho informative verdict §7. Control distribution là finite fixed-budget algorithm, không chứng minh mixing/uniformity; cấm permutation p-value. Empty/unswitchable graph giữ nguyên và báo degeneracy, không bỏ case.

Pre-G topology-only validity check: per case report acceptance rate, number distinct final graphs, overlap `|E_R∩E_O|/max(1,|E_O|)`, fraction nodes with any changed neighbor, và component sizes. Case gọi *mobile* khi ≥16 distinct controls và median overlap≤0.8; structural-contrast informative gate yêu cầu ≥80% evaluation cases mobile **và** ≥50% cases của mỗi root stratum mobile (evaluator-only). Các ngưỡng là design adequacy conventions đặt trước outcomes, không giả ngưỡng lý thuyết. Nếu không đạt: full denominator vẫn đo được nhưng toàn C1 classified INCONCLUSIVE vì controls không phân biệt đủ; có thể revise D trước outputs/G, không nới ngưỡng sau G. Các fixed small components/degree-specific effects nằm ngoài phần arrangement bị perturb; báo exact fraction.

Đã kiểm single-swap feasibility từ full-case graph audit là bằng chứng sơ bộ, **không** xác nhận reference-window mobility. E/F thực hiện gate trên graph thật dùng. Nếu final runs O/R tương đương do l hằng, đó là thông tin nhưng không được nói relations không chứa RCA information.

## 6. Baselines: eligibility, fidelity và gói tối thiểu

Within-method C1 table chỉ L/O/R. Separate context table tránh đồng nhất full-method comparison với relation effect. Không dùng paper scores/historical CSV làm kết quả mới. [Bounded primary checks](D:/Project/flash-ticket-rca-research/task-d/task-d-evidence-ledger.md) giữ sections/code discrepancies; tìm kiếm không chứng minh literature-wide absence.

| Family | Task/input/graph/supervision; output/universe | Eligibility và adapter/fidelity |
|---|---|---|
| L/O/R | Same metric+trace count evidence; local or observed/perturbed graph; no root labels; services V | **Mandatory primary controls**, exact TD implementation |
| Local-MAX | Cùng channel scores, q_v=max(m_v,t_v); graph-free, no fitting, V | **Mandatory graph-free context**; tránh chỉ so một fusion yếu, không thay primary L sau thấy điểm |
| BARO ranking component | Known-window numeric metrics; no graph/root supervision; indicator score→service V | **Mandatory eligible adaptation** §6.1; không claim exact end-to-end BARO/reproduced published score |
| MicroRCA | Performance RCA; response times + host/container metrics and placement; PPR; nominally unsupervised | **Literature-only, original driver ineligible unchanged**: target/fault-dependent threshold and hardcoded target list; RE2 graph thiếu host mapping. Không invent host edges hoặc chuyển driver nguyên bản |
| MicroRank | Trace SLO classification, operation/trace graph, graph-weighted spectrum; unsupervised roots; operation output | **Eligible in principle but deferred runnable adaptation**: candidate/output mapping, complete trace/SLO and duration semantics khác. Scope D không phụ thuộc tái lập nó; không gọi generic smoothing là MicroRank |
| Eadro | Logs/KPIs/traces, service graph, joint supervised detector/root head, service candidates of paper | **Ineligible original runnable baseline**, root-label fit trái policy. Closest graph vs FC ablation, không parameter-matched proof |
| DéjàVu | Historical metrics/labels, FDG, supervised failure-unit rank | **Ineligible original runnable baseline**, labels and target granularity khác. Closest no-aggregation/edge-deletion evidence |
| TORAI | Metrics/logs/trace series, severity clusters + learned causal graphs, no root supervision; service/indicator output | **Eligible in principle, literature-only for core package**. Khác representation/scoring/graph, không graph-free control. Full multimodal reproduction không cần để identify C1 |
| CIRCA/RCD | Metric causal/structural approaches, external boundary; no root-label fitting | **Literature-only for this package**; CIRCA requires justified structural mapping, not obtained by renaming trace edges; RCD adds a different causal-discovery contrast |

Minimal core gồm 3 primary arms + 2 context baselines. Đây không là state-of-art superiority study: no claim O beats all RCA; external graph comparator vắng làm giới hạn comparison landscape, không thiếu cùng-cơ-chế structural control. Không cần baseline deep model để tăng capacity. Original artifacts available theo Task A source register; E phải record immutable source snapshot, license, patch, function signature và fidelity. Nếu bắt buộc thay comparator vì incompatibility, sửa D + review trước G; không âm thầm bỏ đối thủ.

### 6.1 BARO-component adapter frozen specification

Implement paper-defined robust ranking equation, **không tùy chọn nhánh code thắng**: trên metric bins hợp lệ tại §4, `b_c=max_query |x−median_ref|/scale_ref`, scale dùng cùng zero-IQR floor §4; không cap 20, không log-transform metric. `b_v=max(mapped b_c)`; missing candidate evidence=0 with mask; all candidates V retained; round score 12 decimals for ties. Tên output `BARO-RANK-adapted-TD1`: dùng known boundary, common 10s bins, restricted trace-derived V, service max pooling và declared zero-IQR handling; không MBOCPD detection. Không thêm trace/log feature vào baseline này; report modality mismatch, không dùng O−BARO để ước lượng C1.

Official paper equation vs artifact variants differ in abs/max and quantiles: E needs fixture with a downward shift, a spike and zero-IQR to demonstrate equation above, plus record delta from pinned artifact. Reimplementation of a cited ranking component là hợp lệ, nhưng không gán là exact upstream executable reproduction. Baseline frozen outputs phải có null/failure reason cho mọi planned incident.

## 7. Metrics, inference, practical effect và falsification

Primary endpoint **tie-aware MRR**, all 60 evaluation incidents. Với GT nằm tie positions a..b:

`RR = sum(1/r for r=a..b)/(b-a+1)`; `Hit@k=max(0,min(b,k)-a+1)/(b-a+1)`.

Không GT/candidate hoặc method failure: RR=Hit=0. Valid all-tie ranking có expected random-tie RR, không failure. Display sort theo opaque key chỉ để ổn định, không ảnh hưởng metrics. Secondary Hit@1/3/5, raw rank/tie-size distribution, target visibility, failure/missingness rates, wall-clock và peak RSS với scope rõ. Không NDCG/node-F1/operation accuracy làm primary; không dùng spans/windows/seeds như N.

**TD-v1.1 secondary NDCG@5:** binary relevance1 cho duy nhất published root, gain `2^rel−1`, discount `1/log2(r+1)`, IDCG=1. Root absent hoặc method failure=0. Với root ở tie a..b, `NDCG@5=sum(1/log2(r+1), r=a..min(b,5))/(b−a+1)`; vị trí>5 góp0, mẫu số gồm toàn tie. Áp cho mọi ranking row kể cả từng R seed trước averaging. Trong top5 strict ranks đây là biến đổi đơn điệu của rank, không thêm graded-severity truth; aggregate có thể khác MRR do discount/cutoff, ví dụ thuần toán A=(1,6), B=(2,3). Đây là descriptive output theo hướng dẫn, không metric thứ hai để cứu primary H1.

Case-weighted mean bằng equal scenario mean do mỗi cell có ba planned repeats; failures không phá denominator. R metric là average 32 metrics rồi paired difference cùng incident. Báo all-90 table descriptive riêng với cột dev/eval và exposure; không pool để cứu main endpoint.

Uncertainty: giữ d_i paired, average three repeats trong cell. **50,000 paired scenario-block bootstrap replicates**, PCG64 seed 20260923: sample 20 scenario cells uniformly with replacement, mang cả vector ba repeats và tất cả arms/controls; statistic như §1. Dùng percentile type7 **97.5% two-sided intervals** cho mỗi ΔL/ΔR (quantiles .0125,.9875), Bonferroni cho joint family hai contrasts, nominal simultaneous coverage≥95% chỉ dưới assumptions bootstrap. Không seed-resampling làm tăng incident N, không per-span test. R seed Monte Carlo mean fixed trong primary; SE Monte Carlo descriptive riêng.

**Không có giả định independence đã được chứng minh:** 20 cells dùng chung deployment/campaign và crossing root/fault. Các intervals là conditional resampling sensitivity under exchangeable-scenario approximation, **không** population confidence hay proof of significance. Report finite-set point estimates trước, interval with assumption next. Bắt buộc leave-one-root-out (5) và leave-one-fault-out (6) paired mean effects, scenario scatter/heatmap, within-scenario repeat range; không gọi30/20 cells độc lập chỉ vì khác label. Sensitivity veto khóa là **sign preservation >0 của tất cả deletion point effects**, không đòi mỗi deletion cũng vượtδ; crossingδ được report heterogeneity nhưng không veto thứ hai. Không dùng t-test/McNemar default; không tìm subgroup thắng.

**Practical threshold δ=0.05 absolute MRR**, đặt trước new outcomes: năm điểm reciprocal-rank/100 cases là criterion thực nghiệm của study, không SLA/operator-utility threshold và không suy từ score E1. Không tune δ sau G. Primary support phải vượt δ ở **cả hai** contrasts; secondary/context/coverage strata chỉ descriptive, không additional confirmatory hypotheses.

Informative-data conventions khóa trước outcomes: *signal starvation* khi ≥80% planned evaluation incidents có V rỗng hoặc mọi `round(l_v,12)` bằng nhau; *insufficient shared input* khi >10% planned evaluation incidents có shared-input preprocessing failure. Bất kỳ **arm-specific execution/numerical failure trong L/O/R** làm relation-attribution verdict INCONCLUSIVE, trong khi operational utility vẫn báo zero như quy định. External context-baseline failure không đổi C1 verdict. Các tỷ lệ/ngưỡng là convention có khai báo; E không được thay sau xem outcomes. **Local ceiling không tự là lý do INCONCLUSIVE**: nếu local đã tốt khiến practical incremental utility không còn, áp đúng bounds/δ và có thể VALID NEGATIVE.

| Verdict | Quy tắc khóa trước G |
|---|---|
| SUPPORTED (bounded empirical support) | Valid pipeline/control mobility; point effects và simultaneous lower bounds của cả ΔL,ΔR >δ; tất cả leave-one-root/fault point effects cho cả contrasts >0; không all-tie signal starvation. Chỉ support conditional mechanism/benchmark, không untouched-test hoặc causal proof |
| VALID NEGATIVE | Valid informative experiment và upper bound của **ít nhất một** required contrast <δ. Nghĩa practical-support conjunction bị bác trong setup; ghi contrast nào. Không gọi equivalence; upper<0 có thể cho evidence harm. Heterogeneous effects vẫn phải công khai |
| INCONCLUSIVE | Intervals cắt δ; support không qua sensitivity; control mobility fail; signal-starvation/insufficient-shared-input conventions ở trên; hoặc có arm-specific L/O/R execution failure. Không đổi non-significant thành no effect |
| INVALID | Leakage, unequal input/candidate/fit budget, altered endpoint/split after outputs without exploratory label, silent failures/exclusions, wrong labels/evaluator, unsupported graph repair. Giữ run; không gọi negative |

Order: INVALID trước; informative-control/data/failure conventions trước NEGATIVE/SUPPORTED; còn lại áp table. Method crashes vẫn là observed operational utility zero. Không chẩn đoán nguyên nhân failure bằng GT để cứu verdict. Sau nhìn results mọi sửa là exploratory trên cùng eval set; không lại tự nhận confirmatory.

## 8. Failure/missingness/resource policy

| Tình huống | Hành vi định trước |
|---|---|
| Low parent resolution, missing ref edge hoặc isolate | Giữ V/ca, use observed resolved edges; isolate self-loop; không repair |
| Empty graph | O=R=L về operator, vẫn chấm; control degeneracy được ghi |
| Missing logs/unknown log keys | C1 rank không đổi; C5/MTL log placeholder0+mask theo §10; packet missing flag; không sample selection |
| Missing metric channel/modal block | Score placeholder0+unavailable mask, fixed modality weights; không impute hoặc gọi healthy |
| Target vắng V | Evaluator-only miss, RR/Hit0; model không biết target vắng |
| Failed preprocessing/corrupt file/missing required trace | Shared-input failure all core arms0; retain planned row, không candidate fallback từ GT |
| Baseline crash/timeout/NaN/Inf/duplicate candidates/incomplete ranked V | Method failure0 for that arm; preserve error sanitized + runtime. Không fill missing services theo root labels |
| Tie/all-zero output hợp lệ | Tie formulas §7; không ngẫu nhiên chọn seed thuận lợi |
| Dataset-invalid evidence | Mark quarantined in-place; primary planned denominator vẫn giữ0; secondary valid-record table có exact reason/denominator. Trước G nếu label/source genuinely corrupt thì revision+review, không tự delete case |

E pilot đo resources trên dev. Freeze common timeout rule: mỗi method limit `max(300s,10*max_dev_case_wall_seconds)` từ successful dev runs; failed dev runs cần diagnosis trước freeze, không dùng để đặt limit thấp. Same host/process CPU policy, record wall time/peak RSS; O/L/R same fixed limit lấy max trong ba arms. Memory limit bằng measured safe host budget recorded before G; nếu không đủ, stream/spill hoặc revise implementation, không bỏ heavy eval cases. Counts cold I/O, shared preprocessing, graph/control generation và solver riêng; publish end-to-end costs có phân bổ shared work, không lấy cached L so với cold O. No speed/SLA claim trong D.

## 9. C3, C4 và C2 disposition

**C3:** IMPLEMENT supporting operation evidence theo §4.2; **DEFER scored operation-aware representation/ablation** trong core D/E/F/G. Lý do khoa học: giữ operation identity để tạo thêm max-features sẽ đổi feature count/sparsity/service operation multiplicity, gây thêm contrast không cần cho relation effect. Packet counts giúp quan sát score aggregate có che operation change mà không thay rank. Nó chưa chứng minh representation utility. Nếu sau valid C1 mở experiment, phải equal-dimensional/count-controlled pooling, matched compute/telemetry và service-only outcome trong protocol revision riêng; không operation-root accuracy trên RE2-TT.

**C4:** IMPLEMENT graph participation **sau local scoring, tại late score mixing/ranking** (§5). L/O/R là ablation cơ chế hiện tại; **DEFER cross-placement comparison** với contextual/neural scorer vì thay scorer/capacity không cô lập stage. Không nói đã thực nghiệm so graph placements. Claim mechanism-specific phải theo lựa chọn này. TD-v1.1 C5 thêm graph tại anomaly scoring là cơ chế riêng của capability detection; không biến bốn configs C5 thành cross-placement C4 study, không so known-window MRR với detector F1 để chọn placement.

**C2:** không mở horizon sweep hoặc early-stop study. C5 có cửa sổ riêng vì task khác, không trở thành C2 kết quả. Sau valid stable C1, chỉ mở bằng scope riêng nếu có ích.

## 10. C5: graph-conditioned detection → integrated RCA

### 10.1 Task, lineage và phân biệt hai vai trace

Mandatory intended capability F/G, endpoint hỗ trợ riêng; **system-level injection-regime archival replay**, không production detector đã kiểm. TD-v1.0 metric-only detector đã được thay trong bản candidate này, không gọi lại cùng thiết kế bằng tên graph-based. DT18 tự nó cho phép hiểu rộng graph-assisted monitoring; kết hợp nguồn giảng viên mới Step2 và Task A §D.3, cách đáp ứng rõ hơn là graph đổi score **trước alert**. Không bắt buộc PageRank/GNN; dùng fixed neighbor averaging để kiểm tác dụng giữ tín hiệu đồng thời ở neighbors và giảm peak cô lập. **max(q)≤max(a)** với row-stochastic P: không gọi smoothing là anomaly amplification, structural anomaly detector hoặc phát hiện topology change.

- `TRACE_STRUCTURE`: traceID/spanID/parentSpanID/serviceName, chỉ reference để dựng graph theo §5. Query-only nodes là isolates. Không future/cross-case/union graph.
- `TRACE_EVIDENCE`: log1p observed distinct span counts mỗi service/bin; không duration/status/error/operation semantics.
- `LOG_EVIDENCE`: log1p observed row count mỗi service/bin theo §10.2; không raw body/template/severity vào score.
- Metric/trace/log scores là độ lệch reference, không label node-anomaly. V và P luôn dùng trace structure, kể cả config M.
- Detector không nhận inject_time, labels, case/path, midpoint/end/count oracle hoặc Mode A scalers/cache. Scorer chỉ arrays/index/masks/P; hai controllers độc lập.

### 10.2 Replay input, missingness và local features

Clock s0 là first observed metric timestamp. Bins `[s0+10j,s0+10(j+1))`; mỗi endpoint t≥s0+360s: reference `[t−360,t−60)` (30 bins), query `[t−60,t)` (6 bins). Chỉ archive records có permitted event/start timestamps trong intervals; đây không khẳng định span đã đến collector lúc start. Future-invariance chỉ so suffix với timestamps≥cutoff; records đến muộn có old timestamp thuộc arrival policy H, không giả đã giải online availability.

**V_t**: literal nonempty trace services trong reference OR query, chung bốn configs; P_t chỉ resolved reference links, undirected binary/row-stochastic/isolate policy §5. Không dùng toàn metric-entity union như detector v1.0: matching metrics bị giới hạn exact V_t. Thay đổi này chỉ ở C5 và công khai nguy cơ bỏ metric-only entities. Báo unmapped metrics/query-only nodes/V turnover; không GT repair.

Metric aggregation/suffix/median/scale/duplicate policy §4.1 áp theo **cặp rolling windows hiện tại**, không invalidate output cũ vì duplicate xuất hiện về sau. ≥24/30 reference và ≥5/6 query finite bins; conflicting trace key within selected windows làm endpoint shared UNAVAILABLE. **Common input-valid mask**: windows loaded/parsed, V_t nonempty và ≥1 valid metric channel mapped vào V_t. Mask này dùng chung mọi config và không phụ thuộc log availability hay score value. Thiếu trace parsing/V/metric tối thiểu thì UNAVAILABLE cho cả bốn, giữ row/coverage; graph không có edge vẫn hợp lệ (P=I). Count absence là zero observed events trong loaded stream, không chứng minh healthy/actual zero.

Log adapter `LOGCOUNT-v1`: validate timestamp unit và `container_name`; exact equality với literal serviceName, không alias, span/edge join hoặc regex extract IDs. Mỗi original row có opaque identity từ source hash+row ordinal; đếm **rows**, không deduplicate hai messages giống nhau, không dùng text chọn rows. Log bins là log1p(n). Count channel chỉ đủ reference nếu ≥1 observed record cho service trong reference và source/time mapping hợp lệ; 30 reference bins và6 query bins đã loaded thì các bin không row là observed zero. Service mới chỉ xuất hiện query: score0+unavailable-reference, không làm anomaly giả từ baseline được điền zero. File vắng/malformed/missing timestamp unit: log block unavailable + reason, không loại incident hoặc thay V. Unmatched rows giữ coverage counts; không gán cho root. Không dùng full-case counts để biết service nào “phải” có log.

Trace counts dùng distinct key và reference-presence rule §4.1, cùng loaded-bin policy; log masks riêng trace masks. Một ca không logs và các join failures vẫn có trong60 evaluation cases; missing log score0+mask với trọng số cố định. Không reweight, không diễn giải score0 thiếu nguồn là healthy. F phải validate toàn bộ files thực dùng, B mới raw-verified samples; tỷ lệ unmatched/availability theo service/bin/case bắt buộc báo.

Mỗi valid channel fit `c=median(reference), scale=max(IQR, .01*median(abs(reference)),1e-12)`, type7. C5 channel score:
`q90(log1p(abs(query−c)/scale))`, **không C1 cap20/chia20**, không trần1. C5 metric block m=max valid metric-channel scores, t=span-count score, g=log-count score; unavailable block0+mask. Dùng finite float64/numerically stable arithmetic; nonfinite input bị loại theo channel rules, arithmetic/solver nonfinite là method failure, không silently clip. Ảnh hưởng hai lần log cho count channel (log1p count rồi log1p standardized deviation) là deliberate compression ở C5, áp như nhau mọi config; không novelty/calibration claim. C1 vẫn đúng công thức §4.1.

### 10.3 Bốn configs hỗ trợ, không factorial study

Tất cả dùng V_t/P_t source, windows, masks, feature fitting/budget giống nhau; không học parameters, không root tuning. Config IDs luôn giữ tiền tố C5; captions phải nói `M/T/L` là **detector node evidence**, tất cả dùng trace-derived candidate set và các G configs dùng trace-derived topology. Không tên “trace-free metrics-only”.

| Config | Node input a_v (fixed denominator3) | Operator | Câu hỏi hỗ trợ |
|---|---|---|---|
| C5-G-M | m_v/3 | observed P_t | Metric node evidence + trace structure; base của nested comparison |
| C5-G-MT | (m_v+t_v)/3 | observed P_t | Thêm trace occurrence vào metric, điều kiện giữ topology/policy |
| C5-G-MTL | (m_v+t_v+g_v)/3 | observed P_t | Thêm log volume vào M+T; integrated default CANDIDATE, không mặc định thắng |
| C5-L-MTL | (m_v+t_v+g_v)/3 | identity I | Bỏ cross-service mixing với cùng toàn local evidence |

Không M+L, T-only/L-only, log-parser sweep, C5 rewiring hoặc GNN: nested comparisons đủ kiểm incremental T sau M và L sau MT; **không** xác định independent main effects, interaction hoặc log gain khi trace evidence vắng. C5-G-MTL−C5-L-MTL đo graph operator **dưới cùng calibration policy**, không giữ một numeric threshold chung. C5 configs không là bốn primary RQs, không pooled vào C1/inference family.

Mỗi config giải `q=.5*a+.5*P*q`, q0=a, float64 max100 iterations, max_abs change<1e-12; L dùng I. `S_t=max_v q_v`. Thiếu edges/isolate theo §5; failed convergence/nonfinite là config endpoint failure, không thay bằng local output. Raw scores/local arrays/graph hashes/masks được lưu trước decisions. Same a thay P có thể đổi S_t và quyết định; nếu a hằng hoặc P=I thì không đổi. Graph relevance không được suy từ tên: E/F cần fixture giữ local vector và threshold cố định, đổi P làm raw exceedance đổi; kết quả thực tế có thể không tốt hơn.

### 10.4 Calibration và alert state machine

Calibration chỉ30 dev cases; mỗi config fit empirical q99 type7 từ cùng common input-valid endpoints, bỏ warmup/UNAVAILABLE chung. Không exclude bins theo GT. Nếu config numeric failure làm mất shared calibration row thì calibration **FAIL**, sửa implementation hoặc reviewed D revision, không fit trên tập row khác. Cần ≥100 common valid bins, finite threshold, score range>0 và ít nhất một calibration score **strictly above** q99; thiếu điều kiện là CALIBRATION_INADEQUATE trước G, không đổi > thành≥ hoặc tune quantile bằng labels. Quy tắc này kiểm reachability trên archive calibration, không chứng minh detector hiệu quả; E báo min/max/ties và mapping coverage.

Mỗi config có threshold riêng nhưng **cùng** percentile, inputs, fit budget và số lần fit1; đây là so sánh toàn operating policy (operator/evidence + unlabeled calibration), không pure fixed-threshold adjacency effect. Không claim1% production FPR: dev gồm injection regimes và rolling reference có thể contaminated. Không ranking feedback, không metric target để chọn default/winner.

State initial `streak=0,last_trigger=−infinity`; mỗi endpoint: valid score>threshold thì streak+=1, otherwise streak=0; emit nếu `streak>=3 AND t>=last_trigger+300s`, rồi last_trigger=t. Update streak trong refractory; sustained exceedance retrigger đúng expiry. Unavailable/failed endpoint reset streak nhưng không last_trigger. Numeric failures report riêng; không valid zero. State độc lập mỗi config, identical initial rules.

### 10.5 Triggered integrated diagnosis khác primary C1

Tại trigger t: ref `[t−600,t−300)`, query `[t−300,t)`. Không đủ600s recorded history: ngay lập tức `INSUFFICIENT_HISTORY`, không pending/future retry, refractory vẫn áp. Query/reference nguồn hiện có, không inject_time. **Cùng diagnosis profile cho tất cả detector configs**, kể cả G-M: `TD11-INTEGRATED-MTL` dùng V/graph/channel rules Mode A, m/t capped như §4.1, g từ log-count channel với cùng capped z/q90, local `(m+t+g)/3`, observed solver §5. Mọi missing masks/failures giữ; không R ensemble hay comparator thêm trong branch này.

Đây là một profile mới riêng, không thay C1 M+T local score /2. So composed outcomes giữa bốn detector configs có cùng diagnosis function nhưng trigger times/input windows khác; **không** claim M-only end-to-end hoặc pure modality gain của ranker. Không thêm fixed-window MTL-ranking ablation nếu không có scope mới. Operation counts/log excerpts support theo §4.2, không score/edge features. F packet ghi detector config, calibrated threshold, score, evidence masks, trigger/diagnosis reason và profile/version; không injection boundary.

### 10.6 Evaluation và acceptance

Evaluator đọc injection τ sau freeze: mỗi score endpoint t gắn với **bin cuối [t−10,t)**, không toàn query60s. System-bin positive nếu bin start≥τ, negative nếu bin end≤τ, straddling loại khỏi bin metrics và ghi số lượng. Prediction = raw `S_t>threshold` **trước persistence/refractory**. P/R/F1 là **system-bin injection-regime agreement**, không labels node/affected-service/path. Chấm valid scored/evaluable bins mỗi incident, undefined denominator0+flag; macro cả60 cases và pooled confusion totals descriptive, không bin-IID CI. Báo planned/scored/unavailable/failed/excluded counts và separate positive/negative coverage evaluator-only; không bỏ case vì availability. Common-input matched-bin descriptive table nếu numerical failures khác nhau phải ghi selection; all-planned outcomes vẫn là headline.

Báo trigger coverage, first post-injection trigger delay (censored nếu không trigger), pre-injection triggers/observed hour là *replay trigger rate*, không production FPR/true onset latency. Composed RR/Hit/NDCG dùng **first post-injection trigger**, failure/no trigger0 trên60, không chọn trigger sau có rank tốt hơn. Conditional-on-trigger chỉ phụ. Bốn configs báo hết; paired differences F1/coverage/delay và composed ranking mô tả, không hypothesis/CI family mới hoặc aggregate winner rescue C1. Censored delay không điền0 để tính mean; report count và conditional quantiles rõ.

C5-G-MTL có thể tệ hơn L hoặc MT; fixed averaging có thể giảm isolated true fault. Masking/zero placeholders có thể làm logless case chịu calibration từ log-bearing dev; **không** hứa missing-log equivalence về alerts dù raw MT/MTL score trùng khi g=0. Báo matched availability strata descriptive, không discard để làm MTL thắng.

E/F anti-shortcut: metadata remap/shift không đổi detector; time translation cùng clock; crop ở10s boundary so scores sau360s identical history, **decisions** sau380s và cùng carried last_trigger/refractory state. Không yêu cầu unbounded streak counter có cùng số tuyệt đối sau crop: chỉ điều kiện streak≥3 được khôi phục bằng ba endpoints, còn counter có thể đã tích lũy lâu hơn ở replay gốc. Future-suffix invariance áp cho normalized numeric evidence, graph, score, decisions và ranks trước cutoff, với config/calibration/opaque case keys giữ cố định. C1 hashes unchanged; graph intervention before threshold; no-log masks/zero-weight consistency; packet rank immutable dưới LLM.

**Giới hạn packet hồi cứu:** §4.2 chọn log excerpts bằng full source-file hash nên thêm suffix và rehash có thể đổi sampled excerpts/source evidence IDs của quá khứ dù numeric outputs/ranks không đổi. Không đưa hash/selected text vào detector hoặc ranker, không claim byte-identical packet/prompt/explanation dưới source-file mutation. F ghi cả source version và sampling identity; H muốn prefix-stable online explanation phải version adapter/selection policy và review đúng gate, không lấy packet hồi cứu làm bằng chứng online. Đây không là thay C1 formula/experiment. True arrival latency/independent healthy workloads thuộc H. Không chờ H để làm C1.

## 11. Deterministic evidence packet và LLM boundary

F tạo packet trước gọi LLM, schema `TD-packet-v1.1` (C1 ranking giữ nguyên; thêm metadata C5/MTL, không đổi GT policy):

```text
method: {protocol, mode, code_hash, feature_version, graph_version, config_hash}
ranking: [{service_key, display_name, score, tie_group, rank_interval}]
local_evidence: [{service_key, metric_trace_or_log_channel, ref_summary,
                  query_summary, deviation, availability, evidence_id}]
relations: [{from_key,to_key,kind:'observed_trace_parent',reference_count,evidence_id}]
graph_processing: {projection:'undirected_binary',alpha,coverage,limitations}
support: {operation_counts,log_bin_counts,redacted_samples,source_evidence_ids}
detection: {config_id, score, calibrated_threshold, evidence_masks, trigger_status,
            diagnosis_profile, diagnosis_failure} # absent in Mode A; no injection fields
quality: {missing_modalities,unmapped_keys,unresolved_parents,isolates,
          missing_reference,ties,method_failure,uncertainty_text}
```

Service display names được dùng để người đọc biết thành phần, không root-only marker; không case ID/path, root/fault label/text, numeric injection metadata, expected correct answer hoặc GT-presence flag. Full provenance manifest/paths ở control plane riêng; source evidence IDs là opaque. Scores là suspicion utility, **không calibrated root probabilities**. Directed observed relations và undirected processing phải phân biệt trong text/figure. No calibrated per-case confidence interval được bịa từ graph weights.

LLM chỉ trả `{explanations:[{service_key,evidence_ids,text}],uncertainties,suggested_checks}`; không ranking/score/GT fields. Validate service/evidence references, reject mutations/extra rank assertions; renderer luôn dùng immutable packet rank và score. Raw excerpts là untrusted data, không instructions; disable tools capable of sửa hệ thống. Test packet digest trước/sau identical; nếu explanation đổi thứ tự/ngụ ý sửa rank thì reject text, giữ deterministic report. Wrong ranking vẫn là wrong trong evaluator.

Task I owns model/provider/prompt version và rubric execution: faithfulness to cited evidence, unsupported statements, uncertainty communication, operator usefulness và rank invariance. Correct-sounding text không chữa ranking GT; explanation faithfulness và diagnosis correctness hai outcomes riêng. D không gọi LLM hoặc chấm explanation.

## 12. Research adapter và FlashTicket boundary

Research interface `ObservationBundle`: opaque run handle; time unit/availability metadata; metric `(entity,key,time,value)`; spans `(trace_key,span_key,parent_key,service_key,start_time[,operationName,available_time])`; logs `(entity,time,redacted_body,opaque_record_id)`; masks + source hashes. `WindowRequest(mode,reference,query)` do external controller cấp; `rank(bundle,window,config)->packet` là **trusted adapter/controller facade**, không unprivileged scorer. Nó slice/map/aggregate rồi gọi core `score_core(local_numeric_arrays,masks,service_indices,adjacency,solver_config)`; core không nhận bundle/window/epochs/handle/GT. Randomness controller cung cấp realized R adjacency, không case text cho core. Evaluator labels/incident ledger giữ ở adapter khác, không module import ngược. Public adapter exact mappings theo §§2–4; target adapter H kiểm mapping/clock/availability/fault labels độc lập, version mọi thay đổi logic. Không coercion service target thành operation GT.

Đối chiếu bộ hệ thống, **CANDIDATE/OPEN**, chỉ tại mục này: [R0 §3](R0-boi-canh-va-rang-buoc.md), [cửa hệ thống→nghiên cứu](../project/lien-ket-rca.md) và [roles](../project/roles.md). Minh owns RCA; nhóm Minh/Sơn/Tuấn/Tuyến; mobile optional không phải đầu vào D. Adapter này mô tả consumer research, không thay API/schema/B16/kiến trúc, không sinh service/Saga/invariant. Nhu cầu mới nếu H gặp thiếu telemetry phải qua đúng hai cửa/gate, không tự sửa app.

Phép thử độc lập: (1) thuộc bộ RCA; (2) nguồn formation DT18/C/B/A/primary papers và current authorization; (3) nguồn bộ hệ thống chỉ ở phụ lục đối chiếu này ngoài governance references; (4) không sinh/sửa yêu cầu, invariant hoặc ranh giới hệ thống. **PASS phạm vi tài liệu**, không chứng nhận runtime integration.

## 13. Reproducibility, pilot và freeze

Mỗi run E–I có manifest: run/task ID; TD version/hash; dataset immutable revision và per-file hash/bytes; opaque planned case manifest + separate evaluator map; split/exposure version; code commit **và dirty diff hash**; environment lock/OS/runtime/CPU/RAM; config/seeds/exact command; upstream baseline source SHA/license/adaptation patch; input field allowlist; features/masks/candidates hash; graph/reference provenance và every R seed/adjacency hash; per-case predictions/failures; metric-code version; elapsed/RSS measurement scope; output hashes. No LLM arithmetic. Cache key include source, window/mode, feature/graph/config versions; never shared across mode/split/cutoff without exact identity. No secrets/labels in worker logs.

1. **D:** TD-v1.1 revision, source checks, independent critique, deterministic synthetic fixtures, handoff. No baseline/data campaign executed.
2. **Minh accepts protocol + separately authorizes E:** approval required by master Task D/E and current TD-v1.1 request §§28–30/39, not a new permission gate invented by agent.
3. **E:** pin sources/licenses/env; materialize development manifest first; loader/evaluator/invariance fixtures; reproduce declared ranking component/adapters on dev without root scoring; resource/calibration receipts. No bulk full corpus just for convenience.
4. **F:** implement common pipeline, control kernels, packets, separate C5/log/operation support; actual input schema/join verification. Topology-only eval validation allowed logged per §3; no eval predictions/metrics.
5. **Revision if necessary:** scientific/scoring/control/split change updates TD and review before freeze. Routine code correctness fixes require fixtures/diff, not new RQ vote. New supervision/dataset/scope or app changes require Minh.
6. **Freeze receipt:** authorized protocol acceptance, immutable input/split/exposure manifests, configured constants/baseline variants, source/env hashes, successful invariance/evaluator/control mobility checks, resource limits, labels inaccessible to workers, no unadjudicated material review objections. Record all prior exposure, including E1. Freeze before new eval outcomes.
7. **G:** one locked campaign including failures; evaluator only after predictions sealed. Claim is frozen new contrast on historically exposed benchmark. Post-inspection changes versioned exploratory; preserve old run. H/I follow own scopes, not executed by D.

### 13.1 E receives these acceptance checks, not scientific choices to invent

- Exact 30/60 manifests and all3-repeats grouping; exposure records, no root/fault tuning; input allowlist and canary tests.
- 10s window fixtures at both half-open boundaries; duplicate/missing/unmatched/constant channel cases; no duration/status semantics invention; independent median/IQR and tie fixtures.
- Candidate/feature hashes identical L/O/R; constant-input invariance, isolated/empty graph, convergence; R degree/components preserved, fixed seed reproducibility, no score-guided chain selection.
- Metrics fixtures: root at rank2 RR=.5; tied at ranks1–2 RR=.75 and Hit@1=.5; absent/failure0; uniform3 tie RR=11/18; mean seed RR not RR of mean scores; no seed/shifted window independent N.
- BARO downward-change/spike/floor fixtures, exact adapter label and upstream delta; Local-MAX fixed. Any incompatible source triggers documented D correction before final outcomes.
- Validate corpus files actually used (full trace schema audit remains evidence, not substitute loader tests); joins on all log-bearing files used, counts for unmatched/missing; raw body not joined to span by imagined IDs.
- C5 no-oracle/crop/future-invariance tests and unlabeled calibration; packet rank hash invariant and evidence reference validity.
- C5 state fixture: threshold2, t=360..700 step10, scores3 toàn bộ trừ t=390 unavailable. Bin positives mọi valid t; first trigger380, next680 (300s refractory, gap đã reset streak nhưng không reset last_trigger); t380 diagnosis INSUFFICIENT_HISTORY, t680 có600s history. Cases với no valid bins báo coverage0, per-case P/R/F1 convention0 và undefined flag, vẫn macro denominator60.
- TD-v1.1: separate trace structure/evidence; same C5 masks/V/windows/calibration row IDs; fixed denominator3; expected NDCG tie crossing k=5; log absent/new-service/unmatched/malformed/duplicate-row counts.
- Graph-conditioned score fixture changes S with fixed a/threshold; constant-input invariance/max(q)≤max(a); G-MTL vs L-MTL same feature hash; all4 configs fit same dev endpoint IDs once.
- Calibration q99 reachability failure (constant/tied maximum), no post-label fallback; full numeric failures retained; missing-log raw MT/MTL equality does not assert thresholds/alerts equal.
- Replay graph is rebuilt only from permitted rolling reference, never full-case graph; triggered MTL profile cannot overwrite C1 cache. C1 protection checks compare TD-v1.0 contracts/fixtures, not just a version string.
- No real benchmark result, raw-corpus rehash, full loader compatibility, source pin availability or runtime claim can be marked passed from D's synthetic check.

## 14. Claim register và bàn giao

| Claim | Evidence needed / allowed phrasing |
|---|---|
| Spec identifies a testable C1 contrast | This protocol + independent review; CANDIDATE design, not empirical success |
| Observed relation arrangement adds practical utility under this smoothing policy | G both contrasts, controls/denominators/sensitivity pass; bounded historical-exposure disclosure |
| Graph does not add practical benefit here | Valid negative rule; not all graph methods/no information in graph |
| Graph-conditioned pipeline detects incidents | F/G four C5 configs: bounded system-bin injection-regime agreement + failures/coverage; graph score participates before alert by contract, measured benefit NOT VERIFIED; H independent controls for operational claims |
| Operation/log evidence | F packet coverage; logs also count deviations in C5/MTL. G nested detector comparisons do not prove log-content anomaly, operation-root accuracy or independent ranker modality gain |
| LLM explanation faithful/useful | I rubric/invariance/human assessment; not root-correctness proof |
| Contribution | Controlled empirical findings, reproducible protocol/pipeline and target transfer evidence when measured; no world-first/new algorithm assertion |

Report-facing material: task/GT distinctions, provenance correction, exact local/graph controls and their remaining confounds, exposure/partition, finite-case effects and scenario dependence, failures/negative/inconclusive outcomes, C3/C4 disposition, C5/LLM separated endpoints, FlashTicket transfer limits. Runtime/cost/quality tables remain OPEN until measured.

Fresh continuation and final checks are in [Task D handoff](task-d-handoff.md); current task state lives only in [CURRENT-STATE](CURRENT-STATE.md). Human acceptance before E remains **OPEN — Minh**. No USER_CONFIRMED scientific decision added by this document.

## 15. Public validation mở rộng và coverage hướng dẫn

### 15.1 Dataset compatibility và gate công khai

C1 primary vẫn RE2-TT; không dataset mới là điều kiện validity của C1. Hướng dẫn cũ có giá trị theo RCA-020, vì vậy **RE2-TT rồi FlashTicket chưa tự là bằng chứng thực nghiệm trên nhiều public datasets**. MRP-v1.1 thêm một điểm quyết định public extension có owner; không thêm campaign hoặc release bắt buộc một cách ngầm.

| Exact candidate | Evidence đã có | Ranking / graph / detection | Vai trò hợp lý và phần chưa đủ |
|---|---|---|---|
| RE2-TT, revision §2 | B CLOSED:90 M/T,89 L; single service root; injection boundary; observed parent relations | C1 known-window rank YES; graph ablation YES; system-bin/graph AD PARTIAL; node F1 NO | Primary frozen contrast + C5 supporting replay; all-log joins/runtime/score utility chưa đo |
| RE2-SS, cùng revision metadata | Metadata90; carts/catalogue/orders/payment/user mỗi18;6 faults mỗi15;3 repeats mỗi30; M/L90, **traces0**. Existing raw sample `re2ss_orders_loss_2`:87,652 log rows,10 entities, exact log–metric entity-second match; không span graph | Service-root/M+L support plausible; same trace-graph C1 **NO**; injection-regime PARTIAL sau schema/evaluator check | CANDIDATE portability of local metric/log adapter hoặc bounded supporting task; không phải replication của graph C1/C5-G. 89 raw cases còn lại chưa certified |
| LEMMA-RCA paper v4 / official repository, **chưa chọn subdataset/release** | Product Review/Cloud Computing metric+log node/pod evidence; paper không xác nhận released span-parent schema; repository có nhắc raw trace không đủ chứng nhận graph | Entity ranking plausible, service mapping/GT cardinality/onset/trace graph OPEN | CANDIDATE compatibility audit; không nhập GT khác granularity, không pool MRR/F1 với TT, không mang số ca revision cũ sang v4 |

Nguồn: B CLOSED§12; W audits/rcaeval/subagent-a-metadata.md, subagent-e-logs-multimodal.md; [paper LEMMA v4 §§3/AppJ](https://arxiv.org/html/2406.05375v4), [official repository](https://github.com/KnowledgeDiscovery/rca_baselines). Kiểm TD-v1.1 chỉ đọc existing metadata/report và tài liệu chính thức, không tải corpus hoặc chạy method.

**Gate PUBLIC-EXTENSION, owner Minh + D/E:** sau khi Minh chấp nhận protocol và giao E, E chỉ kiểm compatibility metadata/docs/source nhỏ trong scope E được giao; muốn retrieve/implement campaign mới phải được scope riêng. Trước khi tuyên bố phần public program đáp ứng yêu cầu mở rộng, phải có addendum do Minh chấp nhận xác định (a) mục tiêu replication nào, (b) exact release/license/cases/GT, (c) graph/node/telemetry/time mapping đủ cho mục tiêu đó, (d) fixed split/exposure/calibration/failure/metrics và comparator contract, (e) scope retrieval/compute. Nếu thiếu graph: không chạy C1 trace-graph hoặc dựng graph giả; chọn supporting component study với claim khác **chỉ sau Minh chấp nhận**, hoặc tìm release graph-compatible theo bounded source audit. F/G execute và J báo riêng từng dataset; không pool bằng tên metric giống nhau.

**OPEN còn thật:** chưa chọn/duyệt dataset thứ hai, chưa chứng minh full graph method trên public system thứ hai; đây là thiếu external evidence/scope acceptance của chương trình, không khuyết công thức C1 và không lý do tự mở rộng Task E hôm nay. Việc miễn thực nghiệm public bổ sung không được agent tự quyết; nếu kết luận không có release phù hợp thì Minh xử lý rõ phạm vi cùng giảng viên trước final J/K. D có owned decision path, không tuyên bố advisor multi-dataset work đã hoàn thành.

### 15.2 Advisor-step coverage

Tất cả implementation bên dưới là **intended CANDIDATE specification**, trừ source facts và human roles đã dẫn. Không hàng nào xác nhận đã chạy E–I.

| Requirement | Diễn giải chính xác / role | Owner | Public implementation/evidence | FlashTicket implementation/evidence | Metric | Status / limitation |
|---|---|---|---|---|---|---|
| Step1 dependency graph | Observed trace service relation, không causal truth; C1 primary + pipeline capability | D/F/G, H | B xác minh keys/relations; §5 reference graph/control | H adapter xác minh identity, timing, observed coverage; không quyết service architecture | Relation coverage diagnostics; downstream MRR, không topology recall thiếu GT | CANDIDATE design; F/H execution OPEN |
| Step2 graph AD | Graph-conditioned score trước decision; supporting C5, không new primary | D/E/F/G, H | §10 G/L-MTL + nested evidence; graph can change score by math | H independent healthy/fault workloads và availability timestamps | P/R/F1 system-bin injection-regime; coverage/replay delay; H metric theo GT thật | Graph role specified, benefit OPEN; không node-F1/production FPR |
| Step2 log/trace mapping + DT18 metrics | Service/time numeric evidence; trace structure riêng; operation/log packet | F/G, H/I | §4/10 counts; M/T90,L89; no log-edge join | H validate adapters qua hai cửa; I explanation | Availability/mapping, nested detector differences, evidence faithfulness | Count anomalies only; no semantic log/operation accuracy |
| Step3 public datasets | RE2-TT primary; thêm validation phải task-compatible, không bắt ba tên cùng schema | D/E/Minh, F/G/J | §15.1 compatibility và owned extension gate | FlashTicket H không thay public second system | Theo exact task/GT; không pool cross-domain | OPEN selected extension; chưa full multi-public result |
| Step4 compare + metrics | C1 L/O/R + Local-MAX/BARO adapted; C5 own matched local control | D/E/F/G | §§6–7/10; exact adaptation, no SOTA claim | H report changed/unchanged mechanisms và target evidence | MRR primary; Hit1/3/5,NDCG5 phụ; P/R/F1 riêng detector | Baseline fidelity/runtime/effects OPEN; external graph method absence hạn chế |
| Step5 LLM | Downstream immutable rank/evidence, suggest checks; mandatory capability | F/I, H | §11 packetv1.1, I rubric/model/prompt later | Run trong FlashTicket theo H/system gate | Faithfulness, unsupported claims, useful checks, rank invariance | OPEN I; không tự sửa/chốt cause hoặc tạo labels |
| Product integration later | DT18 distributed system + measured transactions + RCA integrated; RCA-018–021 bác “demo-only” scope reduction | System team/H/I/J, Minh | Public results đi trước, không thay system evidence | H transfer; system đo throughput/latency/errors/scaling/consistency/stability theo DT18 | System metrics tách RCA metrics | Scope USER_CONFIRMED; runtime/target results OPEN, không app edits từ D |
