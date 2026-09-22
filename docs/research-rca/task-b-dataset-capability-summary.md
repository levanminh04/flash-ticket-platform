# Task B — Tóm tắt capability chuyển giao

Chủ sở hữu: Minh. `CANONICAL_SYNTHESIS`, **FACT — dẫn xuất có giới hạn**. Cập nhật 2026-09-22. Task B **CLOSED**; đây là bản tóm tắt bền vững trong repository, không audit mới và không thay [báo cáo nguồn §12](D:/Project/flash-ticket-rca-research/dataset-audit/TASK-B-RCAEval-audit.md). Khi sai khác, trở về bằng chứng/§12 và sửa bản tóm tắt; không sửa raw để khớp tóm tắt.

Nguồn đọc: Task B §12.1–12.20, và các hiệu chỉnh phạm vi tại [C Phase 1 §2/4](task-c-independent-research-shortlist.md). SHA-256 báo cáo B tại thời điểm tổng hợp: `BE40D10E359DC7DE670D537C9C7000819161ED2739DC0A6280F69219A716C38A`.

| Phạm vi | Sự thật quyết định đối với D–G |
|---|---|
| Dataset/revision | Hugging Face `phamquiluan/RCAEval`, `afeacb11bcc94dadfd1c8f483ee4377b2b8b614e`; metadata SHA `c49a288920dbba2e8e724679a14636d5c7eb2b45426bba14007ef79a6c0ab1bb` |
| Complete metadata — RE2-TT | 90 ca: 5 root-service × 6 fault × 3 repeat. Metrics/traces 90, logs 89. Inject time trong window ở 90/90; không phải independent anomaly-onset truth |
| Full-subset traces | 90 schemas thống nhất, 67.345.051 spans; literal serviceName và cặp serviceName/operationName; parent chỉ resolve trong cùng trace. 27 services/161 operation pairs là **union audit**, không vocabulary/graph nhìn trước |
| Graph | Service identity literal, observed resolved parent→child relation. Không đủ CALLS/USES/messaging/causal semantics, typed resources hay complete topology |
| Coverage | Parent resolution tổng 98,45274372%; 7 ca dưới 90%. Giữ hard cases. Root có trong tập trace services 20–27 ở 90/90 **full cases**, không bảo đảm prefix |
| GT | Root service/fault/case/window có; operation-root/resource-root/affected-node/path absent. Injection target không có kiểm chứng độc lập riêng |
| Task được chấm | Known-window root-service ranking; graph/representation ablations theo service outcome; missingness analysis có giới hạn. Node/service anomaly F1 và operation localization trên RE2-TT **NOT EVALUABLE** |
| Multimodal — raw sample only | Log–metric DIRECT chỉ tại exact entity-second aggregation bin; log–trace và metric–trace SERVICE+TIME. Không request/span-event join. Không suy compatibility tất cả 89 logs từ samples |
| Detection | Chỉ partial injection-regime evaluation; thiếu healthy operational controls đa workload, onset/affected-entity labels độc lập. Known-window RCA không là detector end-to-end |
| Leakage | Case/path/root_cause.txt/presence/oracle metadata forbidden; root/fault EVALUATION ONLY, không fit/tune; inject_time chỉ external boundary cho known-window RCA, forbidden detection input. Telemetry counts trong window hợp lệ khác metadata/future counts |
| Storage thực có | 6 case/20 files gốc + một B2B case/3 files riêng. B2 range-read 89 remote traces và dùng lại 1 local; **không lưu full corpus 90 traces**. Chuẩn bị corpus thực nghiệm phải thuộc bước được cho phép sau này |
| Ngoài pilot | Hai anomaly inject-window ở RE1; không sửa/nhập vào RE2-TT để tăng số ca |

**Handoff:** D đặc tả mapping/evaluator; E/F xác minh loader/schema trên đúng input thực dùng, manifests và leakage isolation trước khi G chạy. Không loại ca thiếu logs hoặc graph resolution thấp để làm đẹp kết quả; không invent nhãn/resources. Thay đổi capability phải có evidence mới và phiên bản summary, không chỉ do quyết định phương pháp.

Tìm artifacts chi tiết, raw manifests, scripts và reviewers qua [ARTIFACT-MAP](ARTIFACT-MAP.md). Không mặc định tải/rerun khi đọc file này.
