# Task C Phase 2 — Research Decision Lock

- Phiên bản: `TC-P2-v1`, ngày **2026-09-22**. Chủ sở hữu/người quyết định: **Lê Văn Minh**.
- **TASK C PHASE 2: COMPLETE.** Ý định/phạm vi: `USER_CONFIRMED` theo [RCA-001–017](RESEARCH-DECISIONS.md). Đây là ghi nhận quyết định Minh đã cung cấp; không tự nhận giảng viên duyệt hoặc AI duyệt kết quả của mình.
- Lớp tạo tác: `CANONICAL_DECISION / FORMATION`. Công việc Task D: **NOT STARTED; chờ lệnh riêng**.
- Nguồn hiện hành: [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md), U22 trong sổ quyết định, [Task B CLOSED §12](D:/Project/flash-ticket-rca-research/dataset-audit/TASK-B-RCAEval-audit.md), [Task A](task-a-ban-do-bang-chung-doc-lap.md), [Phase 1](task-c-independent-research-shortlist.md). Sự thật dataset không thay đổi theo mong muốn nghiên cứu.

## 1. Quyết định đã khóa và câu hỏi chính

**Primary RQ = C1 (RCA-001).** Trong các pipeline và controls được kiểm cho bài toán xếp hạng service nguyên nhân gốc trên RE2-TT với cửa sổ sự cố đã biết, khi giữ công bằng telemetry, bằng chứng cục bộ, tập ứng viên và điều kiện đánh giá, việc dùng các quan hệ service quan sát được từ trace có mang lại giá trị đo được so với đối chứng không graph và đối chứng cấu trúc thích hợp hay không?

“Giá trị” là utility thực nghiệm có điều kiện theo pipeline/control/budget, không là lượng thông tin nội tại của dataset hay tác dụng của mọi phương pháp graph. Quan hệ quan sát được không tự là quan hệ nhân quả hoặc toàn bộ topology. C1 không bị rút thành graph ON/OFF: Task D phải tách hoặc công khai giới hạn kiểm soát capacity, degree, smoothing, volume, feature, candidate universe và supervision.

**H0, ở mức khái niệm:** sử dụng quan hệ quan sát không tạo cải thiện thực dụng đối với service ranking vượt các đối chứng hợp lệ trong phạm vi phép thử đã định.

**H1, ở mức khái niệm:** quan hệ quan sát tạo cải thiện thực dụng còn tồn tại sau các kiểm soát cần thiết, và kết quả không chỉ là hệ quả của các khác biệt đầu vào/nuisance đã nêu. Chưa đặt số cho “thực dụng”, metric chính, kiểm định hoặc ngưỡng. Task D khóa chúng trước khi xem kết quả test. Không-significant hoặc uncertainty rộng không chứng minh H0 đúng hay các phương pháp tương đương.

Hợp đồng này diễn đạt quyết định C1 của Minh dựa trên H0/H1 Phase 1; các chi tiết định lượng vẫn OPEN. Mục tiêu có kết quả mạnh là mục tiêu chất lượng bằng chứng, không phải cam kết graph sẽ thắng.

## 2. Loại đóng góp và phạm vi các thành phần

Chấp nhận **controlled empirical + engineering/system + reproducible experimental contribution**, cùng các quyết định thiết kế có bằng chứng (RCA-002). Không yêu cầu GNN/causal/ranking algorithm mới. Giá trị cuối cùng được trình bằng pipeline đã kiểm, phép so sánh hợp lệ, hiểu điều kiện thành công/thất bại và bằng chứng chuyển giao; không tự bảo đảm điểm hoặc công bố.

| Thành phần | Vai trò hiện hành | Cách kiểm chứng được phép / điều kiện |
|---|---|---|
| C1 | Mandatory primary research question | Service-root ranking trên RE2-TT; cùng quyền truy cập dữ liệu và tập ứng viên; controls vượt ON/OFF |
| C2 | Optional temporal/evidence-budget extension | Chỉ sau C1 hoàn chỉnh, hợp lệ, ổn định và được chọn làm thêm; không real-time/online/SLA/early-stop claim; không tăng đáng kể độ phức tạp C1 chỉ để dự phòng |
| C3 | Mandatory topic to preserve; implementation/experiment conditional | Operation-aware intermediate evidence hỗ trợ service output/giải thích; ablation representation nếu Task D biện minh. **Không operation-root accuracy trên RE2-TT** |
| C4 | Mandatory topic to preserve; exact ablation conditional | Vị trí/cách graph tham gia scoring/propagation/ranking. So sánh thay scorer phải khai thay đổi đó và kiểm fairness; không nhận là relational-only effect nếu capacity/features cùng đổi |
| C5 | Mandatory system capability | Detection → RCA vẫn trong pipeline đích. RE2-TT chỉ phép đánh giá injection-regime có giới hạn, tách khỏi known-window C1; FlashTicket dùng healthy/fault runs có kiểm soát khi có nhãn phù hợp |
| LLM | Mandatory intended downstream explanation capability | Nhận rank + structured evidence, giải thích/gợi ý kiểm tra; đo faithfulness/unsupported claims/usefulness riêng. Không rerank, tạo nhãn, repair hay chứng thực RCA |
| Demo/UI | Đầu ra minh họa, không tự là kết quả nghiên cứu | Cho thấy rank, evidence, uncertainty và lời giải thích; chỉ chứng minh điều đã chạy. FlashTicket UI/runtime thuộc gate hệ thống tương ứng |

Topic được giữ trong report không bắt buộc phải có một implementation riêng; nếu D không thực hiện ablation C3/C4 thì ghi lý do khoa học rõ. C5/LLM là năng lực bắt buộc dự kiến: lỗi triển khai không được lặng lẽ biến chúng thành optional. Kết quả ranking sai phải còn sai trong evidence packet; lời giải thích chỉ được diễn đạt kết quả và giới hạn.

## 3. Hai môi trường và quyền của ground truth

**RE2-TT — public proving ground (RCA-012):** phạm vi 90 ca ở revision `afeacb11bcc94dadfd1c8f483ee4377b2b8b614e`, theo [tóm tắt capability](task-b-dataset-capability-summary.md). Có thể phát triển pipeline từ telemetry đến anomaly evidence, graph RCA, supporting operation evidence, structured diagnosis và explanation. Primary quantitative target là **root-cause service ranking given known incident window**. Một pipeline đủ thành phần không làm ground truth đầy đủ hơn.

**FlashTicket — transfer/controlled validation (RCA-013–015):** kiểm cùng logic nghiên cứu qua adapter được đặc tả ở D, với healthy/fault runs, nhãn service và operation nếu cơ chế chèn lỗi thực sự xác định đúng mức. Chỉ Task H cần readiness của hệ; D–G tiếp tục độc lập. Thay logic khi chuyển miền phải version và phân biệt adaptation với transfer giữ nguyên. Không dùng kết quả H để sửa nghĩa kết luận RE2-TT. Không sửa bất biến, kiến trúc, API, schema hay B16 trong phiên này.

## 4. Hợp đồng đánh giá và leakage kế thừa

- Một ca sự cố là đơn vị đánh giá; repeat/scenario dependence phải được xử lý trong D. Span/window/seed không tăng số incident độc lập.
- Root-service là target; fault/root labels **evaluation only**, không fit/tune bằng nhãn hoặc train baseline supervised chỉ vì có label. Evaluator có thể phân tầng/chấm theo contract đã khóa. Case ID, path, root text/presence và metadata oracle không vào features/explanation prompts.
- Inject time chỉ làm boundary bên ngoài cho **RCA-given-known-window**; không làm input/calibration shortcut cho end-to-end detector. D phải tách hai chế độ chạy và đánh giá.
- Tập ứng viên lấy từ telemetry được phép, cùng giữa các nhánh; không thu hẹp về năm nhãn tiêm lỗi. Full-case coverage không bảo đảm prefix coverage nếu C2 được mở sau.
- Giữ failures/missing-target/low-coverage trong mẫu số định trước; không repair graph từ kiến trúc/đáp án hoặc impute không khai báo. Logs có một ca thiếu; mức kiểm schema/join metric/log còn theo mẫu, loader E/F phải kiểm đúng dữ liệu thực dùng.
- Họ độ đo service ranking và paired effects/uncertainty được xem xét ở D. Primary metric, tie/miss/error handling, practical-effect criterion, split và statistic chưa chọn. Không sao chép mặc định của A9 lịch sử thành protocol đã khóa.
- Supporting evaluations: C3/C4 nếu hợp lệ; coverage/chi phí/tái lập/failure analysis; C5 injection-regime hoặc healthy/fault truth đúng môi trường; LLM faithfulness tới packet tách khỏi root-ranking correctness. Operation evidence không là quantitative operation target.

## 5. Kết quả âm, falsification và claim bị cấm

RCA-003 chấp nhận kết quả âm hợp lệ: báo graph không cải thiện, tác dụng mất sau controls, hiệu quả chỉ giới hạn ở điều kiện nào, hoặc bằng chứng chưa đủ phân biệt. Không đổi metric/cutoff/candidate/đối chứng sau khi xem final test để cứu H1. Kết quả không phân định được phải báo inconclusive; kết quả sai quy trình là invalid, không gọi valid negative.

H1 bị bác/không được hỗ trợ khi mức gain không đạt criterion đã đăng ký, biến mất sau fair controls, hoặc chỉ được tạo bởi feature/capacity/volume/degree/supervision khác nhau. Nếu không dựng được đối chứng đủ hợp lệ, quay lại Task D trước campaign; nếu phải thay RQ/phạm vi đã khóa, cần quyết định mới của Minh. Kết quả âm trong một pipeline không bác mọi graph RCA.

**Explicit exclusions:** không causal-propagation/complete-topology claim từ parent spans; không tự đặt CALLS/USES/resource/messaging semantics; không node/service anomaly F1, root-operation/resource/path accuracy trên RE2-TT; không production false-alarm/onset/MTTR claim từ lịch tiêm lỗi; không novelty chỉ vì thêm graph/GNN/ba modality; không dùng LLM bù GT hoặc đổi thứ hạng; không tự sửa hệ thống. C2 không là điều kiện thành công. Không nhận dữ liệu đã tải toàn bộ, baseline đã chạy hoặc model đã hiệu quả.

## 6. Handoff sang Task D

| Trường | Giá trị |
|---|---|
| STATUS / PURPOSE | C Phase 2 COMPLETE; khóa lựa chọn con người để có đầu vào ổn định cho thiết kế phương pháp |
| INPUTS ACTUALLY USED | U22; DT18; C Phase 1 canonical/checkpoint; Task B §12 CLOSED; Task A synthesis/task distinctions; nguồn điều phối và trình bày được kê trong ARTIFACT-MAP |
| DECISIONS MADE | RCA-001–017; không chọn thuật toán, baseline implementation hoặc số tham số |
| ARTIFACTS CREATED | Decision lock này, sổ quyết định, master/context package và Task-B capability summary; đường dẫn ở ARTIFACT-MAP |
| EVIDENCE / EXPERIMENTS COMPLETED | Tổng hợp bằng chứng có sẵn và independent workflow challenge; không thí nghiệm mới hoặc raw audit mới |
| KNOWN LIMITATIONS | Contribution acceptance là của Minh, không bảo đảm H1/điểm luận văn; dataset có giới hạn nhãn/semantics; corpus thực nghiệm chưa có đủ tại máy |
| OPEN ITEMS | Exact feature/reference construction, anomaly/graph/ranking mechanisms, controls, metrics/effect size, split/calibration/statistics, baseline adapters, C3/C4 ablations; D/E xử lý đúng gate |
| PROHIBITED INTERPRETATIONS | Không tự duyệt phương pháp/hệ thống; không biến C3/C4 thành discarded hay mandatory extra RQs; không lấy C1 thay detection/LLM |
| NEXT EXACT ACTION | Chờ Minh yêu cầu bắt đầu Task D. Khi có lệnh: đọc minimum pack, viết specification và independent review; không tự chạy baseline/download/install |
| FILES REQUIRED BY NEXT SESSION | SESSION-BOOTSTRAP → CURRENT-STATE → tài liệu này → MASTER §Task D và common contracts → Task-B capability summary. Sổ quyết định chỉ mở ID liên quan; raw evidence chỉ khi có xung đột cụ thể |

## 7. Hiệu lực, supersession và kiểm độc lập hai bộ

Phase 2 thay **trạng thái chờ chọn** ở Phase 1, không viết lại quá trình blind discovery, 12→5 normalization, gates hoặc Red Team. C1 thành primary do lựa chọn hiện tại của Minh, không vì reviewer voting. C2 trở thành optional; C3/C4 giữ supporting design; C5 và LLM là capability theo U22. Dòng MAJOR RT-17 về giá trị luận văn được xử lý ở mức **Minh chấp nhận loại đóng góp**, không biến thành bằng chứng novelty hoặc phê duyệt của giảng viên.

Phép thử theo [hai cửa nối](../project/lien-ket-rca.md): tạo tác thuộc RCA; DT18/U22/A/B/C là nguồn quyết định nghiên cứu. Tham chiếu bộ hệ thống chỉ để đối chiếu trách nhiệm/gate tại mục này: [R0 §3](R0-boi-canh-va-rang-buoc.md) và [cửa hệ thống → nghiên cứu](../project/lien-ket-rca.md). Không nguồn nào ở đây sinh service, Saga, invariant, schema hoặc yêu cầu mới của bộ hệ thống; nhu cầu adapter mới ở mức OPEN tới gate sở hữu. Không tạo cửa nối thứ ba.
