# Task A — Bản đồ bằng chứng độc lập về graph-based anomaly detection và RCA cho microservices

- **Trạng thái:** `DRAFT`
- **Loại tạo tác:** `FORMATION` — khảo sát độc lập, chưa chọn phương pháp, câu hỏi nghiên cứu hoặc dataset
- **Ngày chốt nguồn:** 2026-09-20
- **Phạm vi:** literature và metadata/artifact công khai; không tải hoặc audit dữ liệu thô
- **Nguồn định hướng:** email giảng viên do người dùng cung cấp; nhiệm vụ hiện hành `DT18-NV2/DT18-NV3`
- **Chủ sở hữu quyết định tiếp theo:** Lê Văn Minh; AI không tự duyệt tạo tác này

> **Cam kết chống neo.** Bản đồ này được lập từ bài gốc, proceedings, preprint tác giả, repository/artifact chính thức và tài liệu benchmark. Quá trình khảo sát không mở bản thảo phương pháp RCA hiện có của sinh viên, không mở `A10` hoặc `E1`, không dùng đề xuất hay tên cấu trúc nội bộ đã biết từ hội thoại để định hướng tìm nguồn. Các nhận định về khoảng trống chỉ mang trạng thái `CANDIDATE` hoặc `OPEN`.

# Phần I — Tóm tắt điều hành

## 1. Các họ phương pháp đang tồn tại

Mười một phương pháp được chọn đủ để tách các cơ chế chính mà không biến Task A thành một survey quá rộng. Tám phương pháp là danh sách bắt buộc; ba đối chứng bổ sung lấp đúng ba lỗ khái niệm: structural graph anomaly, graph-forecasting anomaly và missing-aware multimodal diagnosis.

1. **Phát hiện thay đổi và xếp hạng độ lệch thống kê:** BARO. Phương pháp này không dùng graph.
2. **Phát hiện cục bộ rồi dùng graph để lan truyền/xếp hạng:** MicroRCA và MicroRank.
3. **Causal discovery/intervention recognition:** RCD và CIRCA. Cả hai cần thời điểm/cửa sổ sự cố đã biết; chúng không phải detector end-to-end.
4. **Học chung detection và localization trên graph:** Eadro dùng telemetry đa nguồn và GAT, nhưng cần nhãn supervised.
5. **RCA đa nguồn khi trace/call graph có blind spot:** TORAI không cần dựng service call graph, nhưng vẫn cần mốc anomaly từ bên ngoài.
6. **Supervised graph localization:** DéjàVu dùng Failure Dependency Graph và GAT để xếp hạng *failure unit* của lỗi tái diễn; không phát hiện incident.
7. **Graph-based anomaly detection đúng nghĩa:** DeepTraLog chấm bất thường cho toàn bộ trace-event graph bằng GGNN + deep SVDD; GDN học graph giữa các chuỗi, dự báo hành vi bình thường rồi dùng residual làm anomaly score.
8. **Missing-aware self-supervised diagnosis:** ARMOR dùng observation mask, learnable missing token và gated graph fusion cho AD, failure triage và instance localization khi thiếu modality/channel/value.

Các họ này giải các bài toán khác nhau. Một phương pháp được gọi chung là “RCA” trong bài báo không có nghĩa nó đã phát hiện incident, tìm được nguyên nhân vật lý, giải thích đường lan truyền và tạo lời giải thích cho con người.

## 2. Anomaly detection khác RCA ranking

**Anomaly detection** trả lời *“đơn vị nào, ở thời điểm nào, có lệch khỏi hành vi bình thường không?”*. Nó cần anomaly score, mô hình normal, ngưỡng/calibration và nhãn phù hợp để đo Precision/Recall/F1.

**RCA ranking** bắt đầu từ một failure case hoặc cửa sổ sự cố và trả lời *“ứng viên nguyên nhân đứng ở vị trí nào?”*. Nó cần tập ứng viên, ground-truth root cause và được đo bằng Hit/AC@k, MRR, NDCG hoặc các độ đo ranking tương đương.

MicroRCA và MicroRank là hai phản ví dụ trực tiếp cho việc nhập hai bước này làm một: detector chạy trước; PageRank/PPR chỉ xuất hiện sau đó trong bước phân bổ bằng chứng và xếp hạng. RCD, CIRCA, TORAI và DéjàVu thậm chí nhận incident/cửa sổ lỗi từ bên ngoài.

## 3. Graph đang được dùng theo những cách nào

Graph có ít nhất sáu vai trò độc lập:

- biểu diễn topology hoặc quan hệ triển khai;
- cung cấp ngữ cảnh feature cho detector;
- làm chính đối tượng cần chấm bất thường;
- lan truyền điểm nghi vấn;
- biểu diễn giả định causal/conditional;
- xếp hạng ứng viên nguyên nhân.

Do đó, báo cáo phải nói **graph tham gia ở công đoạn nào**. Chỉ viết “phương pháp dùng graph” không đủ để xác định đóng góp.

## 4. PageRank thực tế thường làm gì

Trong MicroRCA, Personalized PageRank chạy trên anomalous service–host subgraph để tạo thứ hạng cuối. Trong MicroRank, hai PageRank trên normal/abnormal operation–trace graph tạo trọng số cho spectrum score. Ở cả hai, PageRank **không quyết định trace/call có bất thường hay không**.

Kết luận từ pipeline của hai bài: dùng PageRank sau local detector tạo thành **graph-based RCA ranking**, nhưng không tự biến detector thành **graph-based anomaly detector**. PageRank chỉ là anomaly detector khi abnormality được định nghĩa là độ lệch của PageRank-derived score so với một baseline normal và có calibration/decision rule rõ; điều này không xảy ra trong hai phương pháp trên.

## 5. GNN thực tế đóng vai trò gì

GNN không gắn với một vai trò duy nhất:

- **Eadro:** GAT thay đổi representation dùng chung cho cả binary detector và service localizer.
- **DéjàVu:** GAT chỉ phục vụ supervised fault localization sau khi incident đã được phát hiện.
- **DeepTraLog:** GGNN tạo embedding của trace-event graph; khoảng cách tới hypersphere là anomaly score.
- **GDN:** GAT dự báo giá trị bình thường theo graph học được; residual chuẩn hóa là anomaly score.
- **ARMOR:** graph-encoded self-supervised representation và residual phục vụ detector; cùng representation phục vụ instance ranking khi thiếu telemetry.

Vì vậy “dùng GNN” không phải tiêu chí khoa học. Cần chứng minh graph/GNN tạo lợi ích so với baseline công bằng, với cùng input, label budget và protocol.

## 6. Multimodal RCA đã đi tới đâu

Kết hợp metrics, logs và traces đã có prior work rõ:

- Eadro học representation riêng theo modality, fusion ở representation level, rồi đưa qua GAT và học chung detection/localization.
- TORAI chuyển từng modality thành time series, fusion ở score/severity-vector level, phân cụm rồi causal-rank; thiếu modality được điền 0.
- DeepTraLog gắn log event vào cấu trúc trace và fusion ngay trong graph object.
- ARMOR tách encoder theo modality, dùng mask/missing token và gated fusion trước topology-aware GAT.
- LEMMA-RCA v4 đánh giá metric–log fusion ở hai miền IT; bài hiện hành không mô tả trace là modality phát hành cho bốn sub-dataset.

Do đó, “có ba nguồn telemetry” không còn là khoảng trống. Phần còn có thể audit là cơ chế fusion, giá trị gia tăng của từng nguồn, chất lượng/missingness và tính công bằng của ablation.

## 7. Missing/incomplete telemetry đã được nghiên cứu tới đâu

Vấn đề này không còn hoàn toàn bỏ ngỏ:

- TORAI bỏ trace ngẫu nhiên từ 0% đến 100% số service và có Sock Shop không trace; phương pháp tránh phụ thuộc service call graph.
- DéjàVu bỏ ngẫu nhiên cạnh FDG; mất không quá 10% cạnh chỉ làm giảm nhẹ, nhưng graph sai nhiều có thể tệ hơn bỏ graph.
- Eadro có ablation bỏ từng modality và bỏ GAT, nhưng đó chưa phải mô hình missingness theo thời gian hoặc độ tin cậy.
- MicroRank thừa nhận broken traces/intermittent failures làm giảm độ chính xác.
- CIRCA thừa nhận thiếu meta-metric có thể tạo hidden common cause và phá giả định causal.
- ARMOR kiểm whole-modality collapse và random modality/channel/element masking từ 0–40%, nhưng cần historical archive đủ đầy và không tái dựng edge thiếu.

Phần chưa được giải quyết rõ trong tập nguồn này là: phân biệt “thiếu” với “bình thường”, hiệu chỉnh độ tin cậy của cạnh, tái dựng graph có bất định và đánh giá missingness có cấu trúc. Đây mới là `POTENTIAL GAP`, chưa phải tuyên bố mới.

## 8. Những hướng trông mới nhưng đã có prior work

- graph-based RCA nói chung;
- PageRank/PPR để xếp hạng;
- GNN cho detection hoặc localization;
- metrics + logs + traces;
- operation-level evidence;
- robustness khi trace/call graph thiếu;
- missing-aware multimodal AD/RCL;
- failure unit kết hợp vị trí và loại triệu chứng;
- diễn giải mô hình bằng neighbor/rule/similar incident.

Các hướng này chỉ có thể thành đóng góp nếu câu hỏi hẹp hơn, có baseline sát nhất và chỉ ra điều kiện thất bại chưa được đo.

## 9. Những khoảng trống đáng audit tiếp

Không có gap nào đã đủ bằng chứng để đánh dấu `STRONG CANDIDATE`. Năm câu hỏi đáng audit nhất là:

1. graph giúp hoặc gây hại thế nào khi topology/telemetry sai có cấu trúc;
2. graph có thêm giá trị sau khi giữ nguyên local anomaly score và candidate universe hay không;
3. granularity–observability–label trade-off trên cùng failure cases;
4. score/cạnh có được calibration theo chất lượng evidence hay chỉ dùng hằng số/zero-imputation;
5. lời giải thích có trung thành với evidence/ranking và hữu ích cho người vận hành hay không.

Tất cả vẫn là `NEEDS MORE EVIDENCE` vì prior work gần nhất đã chạm vào từng phần.

## 10. Task B cần kiểm gì ở dataset

Bước tiếp theo không phải chọn PageRank hay GNN. Task B cần xác minh raw schema, định danh operation, liên kết span cha–con, trace coverage, khả năng dựng service/operation graph, nhãn root-cause thật sự, healthy period, candidate coverage và các pattern missingness. Cần giữ riêng ba khái niệm: **benchmark application**, **dataset được sinh từ application**, và **release/snapshot cụ thể**.

# Phần II — Bản đồ bằng chứng chi tiết

## A. Phạm vi tìm kiếm và chất lượng nguồn

### A.1. Phạm vi

- Phương pháp bắt buộc: MicroRCA, MicroRank, BARO, RCD, CIRCA, Eadro, TORAI, DéjàVu.
- Ba phương pháp bổ sung có mục đích: DeepTraLog cho whole-graph/structural anomaly detection; GDN cho learned-graph forecasting residual; ARMOR cho missing-aware self-supervised AD/RCL.
- Benchmark/dataset: Train Ticket, Sock Shop, Online Boutique, RCAEval RE1/RE2/RE3 và LEMMA-RCA.
- Không tải dataset, không mở Parquet/CSV thô, không chạy implementation.
- Chỉ dùng kết quả của từng paper trong đúng setup của paper; không so trực tiếp các con số giữa dataset/protocol khác nhau.

### A.2. Nguồn chính

Các định danh dưới đây được đối chiếu với bài gốc và artifact của tác giả. Sổ nguồn dùng chung của dự án được cập nhật tại [`docs/research/source-register.md`](../research/source-register.md); khi artifact hoặc DOI chưa xác minh được, bảng giữ nguyên nhãn `UNVERIFIED`.

| Phương pháp | Bài gốc, tác giả, năm, venue | Artifact chính thức | Chất lượng/giới hạn nguồn |
|---|---|---|---|
| MicroRCA | Li Wu, Johan Tordsson, Erik Elmroth, Odej Kao, *[MicroRCA: Root Cause Localization of Performance Issues in Microservices](https://doi.org/10.1109/NOMS47738.2020.9110353)*, NOMS 2020 | [elastisys/MicroRCA](https://github.com/elastisys/MicroRCA) | Peer-reviewed; dataset tự thu, raw archival package `UNVERIFIED` |
| MicroRank | Guangba Yu, Pengfei Chen, Hongyang Chen, Zijie Guan, Zicheng Huang, Linxiao Jing, Tianjun Weng, Xinmeng Sun, Xiaoyun Li, *[MicroRank: End-to-End Latency Issue Localization with Extended Spectrum Analysis in Microservice Environments](https://doi.org/10.1145/3442381.3449905)*, WWW 2021 | [IntelligentDDS/MicroRank](https://github.com/IntelligentDDS/MicroRank) | Peer-reviewed; paper/code có khác biệt detector cần giữ `UNVERIFIED` |
| BARO | Luan Pham, Huong Ha, Hongyu Zhang, *[BARO: Robust Root Cause Analysis for Microservices via Multivariate Bayesian Online Change Point Detection](https://doi.org/10.1145/3660805)*, PACMSE/FSE 2024 | [code](https://github.com/phamquiluan/baro), [dataset](https://zenodo.org/records/11046533) | Peer-reviewed + artifact/dataset công khai |
| RCD | Azam Ikram, Sarthak Chakraborty, Subrata Mitra, Shiv Saini, Saurabh Bagchi, Murat Kocaoglu, *[Root Cause Analysis of Failures in Microservices through Causal Discovery](https://proceedings.neurips.cc/paper_files/paper/2022/hash/c9fcd02e6445c7dfbad6986abee53d0d-Abstract.html)*, NeurIPS 2022 | [azamikram/rcd](https://github.com/azamikram/rcd) | Peer-reviewed + code/Sock Shop data; production data riêng |
| CIRCA | Mingjie Li, Zeyan Li, Kanglin Yin, Xiaohui Nie, Wenchi Zhang, Kaixin Sui, Dan Pei, *[Causal Inference-Based Root Cause Analysis for Online Service Systems with Intervention Recognition](https://doi.org/10.1145/3534678.3539041)*, KDD 2022 | [NetManAIOps/CIRCA](https://github.com/NetManAIOps/CIRCA) | Peer-reviewed; real cases thuộc ngân hàng/Oracle |
| Eadro | Cheryl Lee, Tianyi Yang, Zhuangbin Chen, Yuxin Su, Michael R. Lyu, *[Eadro: An End-to-End Troubleshooting Framework for Microservices on Multi-source Data](https://arxiv.org/abs/2302.05092)*, ICSE 2023, DOI `10.1109/ICSE48619.2023.00150` | [BEbillionaireUSD/Eadro](https://github.com/BEbillionaireUSD/Eadro) | Peer-reviewed + code/data; hai benchmark tự thu |
| TORAI | Luan Pham, Huong Ha, Xiuzhen Zhang, Hongyu Zhang, *[TORAI: Multi-source Root Cause Analysis for Blind Spots in Microservice Service Call Graph](https://doi.org/10.1145/3808137)*, PACMSE/FSE 2026 | [RCAEval `fse26`](https://github.com/phamquiluan/RCAEval/tree/fse26) | Peer-reviewed + artifact; paper mới, cần kiểm độc lập raw manifest ở Task B |
| DéjàVu | Zeyan Li, Nengwen Zhao, Mingjie Li, Xianglin Lu, Lixin Wang, Dongdong Chang, Xiaohui Nie, Li Cao, Wenchi Zhang, Kaixin Sui, Yanhua Wang, Xu Du, Guoqiang Duan, Dan Pei, *[Actionable and Interpretable Fault Localization for Recurring Failures in Online Service Systems](https://doi.org/10.1145/3540250.3549092)*, ESEC/FSE 2022 | [NetManAIOps/DejaVu](https://github.com/NetManAIOps/DejaVu), [Zenodo 6955909](https://doi.org/10.5281/zenodo.6955909) | Peer-reviewed + replication package; ba hệ production không công khai đầy đủ |
| DeepTraLog | Chenxi Zhang, Xin Peng, Chaofeng Sha, Ke Zhang, Zhenqing Fu, Xiya Wu, Qingwei Lin, Dongmei Zhang, *[DeepTraLog: Trace-Log Combined Microservice Anomaly Detection through Graph-based Deep Learning](https://doi.org/10.1145/3510003.3510180)*, ICSE 2022 | [FudanSELab/DeepTraLog](https://github.com/FudanSELab/DeepTraLog) | Peer-reviewed + code/derived data; một TrainTicket v0.2.0 dataset tự sinh, không có versioned release |
| GDN | Ailin Deng, Bryan Hooi, *[Graph Neural Network-Based Anomaly Detection in Multivariate Time Series](https://doi.org/10.1609/aaai.v35i5.16523)*, AAAI 2021 | [d-ailin/GDN](https://github.com/d-ailin/GDN) | Peer-reviewed + code; CPS sensor data, không phải microservice RCA |
| ARMOR | Wenzhuo Qian, Hailiang Zhao, Ziqi Wang, Zhipeng Gao, Jiayi Chen, Zhiwei Ling, Shuiguang Deng, *[ARMOR: A Robust Self-Supervised Framework for Root Cause Analysis in Microservices under Missing Modality](https://arxiv.org/abs/2603.25538v3)*, bản tác giả được chấp nhận tại ASE 2026 | [Zenodo 19157589](https://zenodo.org/records/19157589) | DOI Version of Record còn placeholder; nguồn mới nhưng trực tiếp đánh giá missingness |

Tuyên bố của paper được ghi là `FACT` trong đúng phạm vi setup. Nhận định ngoài paper được gắn nhãn **REVIEWER ANALYSIS**. Thuộc tính không kiểm được ghi `UNVERIFIED`; câu hỏi cần raw data ghi `OPEN — Task B`.

## B. Taxonomy phương pháp

### B.1. Năm nhiệm vụ không được nhập làm một

| Nhiệm vụ | Câu hỏi | Ground truth tối thiểu | Không tự chứng minh |
|---|---|---|---|
| Fault/anomaly detection | Có bất thường không, ở đơn vị/thời điểm nào? | Nhãn normal/anomaly theo timestamp, window, trace hoặc entity-window | Nguyên nhân gốc |
| Affected-component identification | Thành phần nào biểu hiện triệu chứng? | Nhãn vùng ảnh hưởng hoặc tiêu chí quan sát độc lập | Thành phần bị ảnh hưởng đã gây lỗi |
| Root-cause localization/ranking | Ứng viên nguyên nhân đứng ở đâu? | Root-cause set và candidate universe cho từng failure case | Detector đúng hoặc quan hệ causal đúng |
| Causal explanation | Cơ chế/can thiệp nào tạo hậu quả và lan truyền ra sao? | Giả định causal, graph đúng, bằng chứng can thiệp/kiểm chứng | Correlation/call edge là causal edge |
| Natural-language explanation | Kết quả kỹ thuật được trình bày thế nào cho người vận hành? | Tiêu chí faithfulness, factuality, usefulness | Văn bản trôi chảy là chẩn đoán đúng |

### B.2. Phạm vi mà từng phương pháp thực sự bao phủ

Ký hiệu: `✓` đầu ra/tác vụ trực tiếp; `△` hỗ trợ một phần hoặc phụ thuộc giả định mạnh; `—` không xử lý.

| Method | Detection | Affected component | Root ranking | Causal explanation | Natural-language explanation |
|---|---:|---:|---:|---:|---:|
| MicroRCA | ✓ call latency | △ anomalous subgraph | ✓ service | — | — |
| MicroRank | ✓ abnormal trace | △ covered operations | ✓ service-instance operation | — | — |
| BARO | ✓ incident/change point | △ deviating metric/service | ✓ metric/component | — | — |
| RCD | — | △ observed candidate | ✓ metric | △ intervention target dưới assumptions | — |
| CIRCA | — | ✓ abnormal indicator | ✓ metric | △ graph-conditioned intervention recognition | — |
| Eadro | ✓ window | △ service status | ✓ culprit service | — | — |
| TORAI | — | ✓ symptom/severity group | ✓ service + indicator | △ learned causal graph, không có path proof | — |
| DéjàVu | — | ✓ failure unit | ✓ failure unit | —; có model interpretation | — |
| DeepTraLog | ✓ trace-event graph | — | — | — | — |
| GDN | ✓ time tick | ✓ deviating sensor | —; chỉ diagnostic clue | —; có graph/deviation context | — |
| ARMOR | ✓ system window | △ instance status | ✓ instance | — | — |

## C. Ma trận so sánh và pipeline thực

### C.1. Ma trận bắt buộc

| Method | Problem | Telemetry | Graph | Graph source | Anomaly detector | Anomaly score | Graph role | Propagation | Root-cause ranker | Learning type | Required labels | Dataset | Metrics | Artifact | Limitations |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| MicroRCA | Performance-fault detection + service localization | Call response time; container/host metrics | Service + host nodes; call + placement edges | Istio/Prometheus + deployment | BIRCH theo call edge | Multi-cluster/confidence; threshold artifact `0.045` | `GRAPH_CONSTRUCTION`, `PROPAGATION`, `ROOT_CAUSE_RANKING` | Correlation weights, reversed calls | PPR, personalization từ service anomaly score | `UNSUPERVISED` | Không fault label | Sock Shop tự thu, 95 runs | Precision@k, MAP | Code chính thức | Chỉ fault làm tăng latency; overhead; một testbed |
| MicroRank | Latency anomaly + operation localization | Distributed traces | Operation-call và operation–trace heterogeneous graph | Trace parent/child + coverage | Actual trace latency > expected SLO | Tổng operation SLO; paper/code khác nhau | `GRAPH_CONSTRUCTION`, `ROOT_CAUSE_RANKING` | PageRank evidence flow | Normal/abnormal PPR → weighted spectrum | `UNSUPERVISED` | Normal window, không root label để train | Hipster Shop A/B; production C | Recall@k, EXAM | Code chính thức | Broken traces, latency-only, normal history |
| BARO | Change-point detection + metric/service ranking | Metrics | Không | — | Multivariate BOCPD trên Latency/Errors | Run-length posterior reset/change | Không graph | — | RobustScorer `max \|x-med\|/IQR` | `UNSUPERVISED` | Không fault label | OB/SS/TT, 100 cases/system | P/R/F1; AC@k, Avg@5 | Code + Zenodo | Cause phải hiện trong metrics/SLI; 4 fault types |
| RCD | Intervention-target localization | Metrics; known normal/fault split | Local learned causal neighborhood + F-node | Ψ-PC từ dữ liệu | **NO SEPARATE ANOMALY DETECTOR** | —; CI-test p-value phục vụ ranking | `CAUSAL_REASONING`, `ROOT_CAUSE_RANKING` | Không xuất propagation path | Hierarchical candidates + p-value | `UNSUPERVISED` đối với root labels | Regime label normal/anomalous | Synthetic; Sock Shop; 3 outages | Top-k recall, runtime | Code + SS data | Causal sufficiency/faithfulness; random chunks; known failure time |
| CIRCA | Root-cause indicator ranking | Metrics + graph + external detect time | Structural CBN metric graph | Architecture/call graph + manual T/E/L/S mapping | **NO SEPARATE ANOMALY DETECTOR** | Max standardized regression residual; cutoff 3 | `FEATURE_CONTEXT`, `CAUSAL_REASONING`, `ROOT_CAUSE_RANKING` | Descendant adjustment | Adjusted residual score | `UNSUPERVISED` | Không incident/root label để train | Synthetic; 99 Oracle cases | AC@k, Avg@5, runtime | Code + Figshare | Manual graph; missing parents; real data không phải microservice benchmark |
| Eadro | Joint window detection + culprit-service localization | Logs, KPIs, traces | Directed service invocation graph | Historical traces | Neural binary head | Learned probability; decision threshold implementation `UNVERIFIED` | `FEATURE_CONTEXT`, `ANOMALY_DETECTION`, `ROOT_CAUSE_RANKING` | GAT message passing | Culprit probability head | `SUPERVISED` multi-task | Window anomaly + culprit service | TT 162; SocialNetwork 72 injections | P/R/F1; HR@k, NDCG@k | Code + data | Label-heavy; two simulated datasets; unseen logic faults |
| TORAI | Blind-spot-tolerant coarse/fine RCA | Metrics, log counts, available trace time series | Learned causal graphs trong severity clusters; không service call graph | Ψ-PC từ time series | **NO SEPARATE ANOMALY DETECTOR** | Severity z-like max sau external detect time; missing modality = 0 | `CAUSAL_REASONING`, `ROOT_CAUSE_RANKING` | Causal graphs trong cluster | Cluster severity → CI ranking → FineGrainer | `UNSUPERVISED` đối với root labels | Normal/fault split | OB/SS/TT 270 cases + 10 incidents | AC@k, Avg@5 coarse/fine, runtime | RCAEval `fse26` | Không estimate missingness confidence; phân phối phải đổi; known detect time |
| DéjàVu | Fault localization cho recurring failures | Metrics + historical failures + FDG | Failure-unit nodes; call/deployment dependencies | Trace/CMDB/domain knowledge | **NO SEPARATE ANOMALY DETECTOR** | Sigmoid suspicious score, chỉ dùng để rank | `FEATURE_CONTEXT`, `ROOT_CAUSE_RANKING` | Multi-layer GAT | Neural score per failure unit | `SUPERVISED` | Historical faulty failure-unit labels | 3 production + TT; 601 failures | MAR, Hit/accuracy@k | Replication package | Per-system labels/units; drift; không transfer hệ khác |
| DeepTraLog | Trace-level anomaly detection | Trace spans + logs | Directed attributed trace-event graph | Request/response structure + log attachment | GGNN + deep SVDD | Distance embedding-to-center vs learned radius | `GRAPH_CONSTRUCTION`, `ANOMALY_DETECTION` | GGNN message passing trong TEG | Không có | `UNSUPERVISED`/one-class | Mostly-normal training, trace anomaly test labels only for eval | TT v0.2.0, 132,485 traces | P/R/F1; time | Code + derived data; không có release | Một hệ/dataset; score không localize root cause |
| GDN | Multivariate time-series anomaly detection | Sensor time series | Learned directed sensor graph | Top-k embedding similarity | Graph-attention forecasting | Smoothed max robust-normalized residual; threshold=max validation score | `GRAPH_CONSTRUCTION`, `FEATURE_CONTEXT`, `ANOMALY_DETECTION` | Neighbor attention cho prediction | Không có RCA ranker | `SELF-SUPERVISED` normal-only forecasting | Normal training; labels chỉ eval/threshold set | SWaT, WADI | P/R/F1 | Code chính thức | Không microservice; root-cause chỉ là diagnostic case study |
| ARMOR | Missing-aware AD, failure triage, instance RCL | Metrics, log counts, trace statistics + mask | Historical invocation graph; instance nodes | Historical records/topology | Unified deviation score + streaming POT; persistent delay | Residual + graph embedding projected thành score | `FEATURE_CONTEXT`, `ANOMALY_DETECTION`, `PROPAGATION`, `ROOT_CAUSE_RANKING` | Topology-aware GAT | Cosine similarity instance/global failure signature | `SELF-SUPERVISED`; FT supervised | Normal archive; FT type labels; root labels chỉ eval | D1 210 faults; D2 133 faults | P/R/F1; weighted F1; Top@k, Avg@5 | arXiv + Zenodo | Complete-enough history; synthetic masking; topology drift; DOI `UNVERIFIED` |

### C.2. MicroRCA

- **Problem/input/graph.** MicroRCA phát hiện performance anomaly theo service-call response time rồi định vị service. Graph có node service và host; cạnh service-call và service–host placement lấy từ Istio/Prometheus/deployment. Resource features gồm CPU, memory, disk I/O và network.
- **Detector.** BIRCH không giám sát chạy trên từng response-time edge sau làm trơn/chuẩn hóa. Artifact đánh dấu edge bất thường khi tạo nhiều cluster, với `ad_threshold=0.045`; paper khảo sát khoảng `0.02–0.065`. Normal là cluster hành vi chiếm ưu thế. Đây là tham số chỉnh thủ công, không phải calibration bằng validation có nhãn. Graph chưa tham gia quyết định này.
- **Graph/ranker.** Sau detection, phương pháp lấy anomalous subgraph; anomalous edge dùng hằng số `α=0.55`, normal edges và service–host edges dùng Pearson/resource correlations. Nó đảo call-edge và chạy Personalized PageRank với damping `0.85`, personalization từ service anomaly score; host bị loại khỏi output.
- **Học/output.** `UNSUPERVISED`; không cần fault label. Output là ranked services, không có causal proof hoặc natural-language explanation.
- **Dữ liệu/protocol.** Sock Shop 13 services trên GCE Kubernetes, Locust khoảng 500 users/600 request/s; latency 200 ms, CPU hog, memory leak; 95 runs, mỗi fault-service lặp 5, mỗi injection 1 phút. Precision@k và MAP; baseline random, MonitorRank, Microscope. Paper báo Precision@1 trung bình khoảng 89% và MAP khoảng 97%. Baseline được cấp detector output của MicroRCA, nên so sánh cô lập localizer chứ không phải end-to-end.
- **Giới hạn tác giả.** Chỉ nhắm fault biểu hiện thành response-time tăng; thu metrics có overhead; detector threshold cần điều chỉnh theo hệ.
- **REVIEWER ANALYSIS.** Correlation/topology không chứng minh causal propagation. Một testbed, một replica/service và injection ngắn hạn hạn chế external validity. Kết quả ranking không chứng minh fault mechanism.

### C.3. MicroRank

- **Problem/input/graph.** MicroRank tìm latency issue từ distributed traces và xếp service-instance operation. Nó dựng operation-call graph cùng hai heterogeneous operation–trace graphs cho normal và abnormal trace populations.
- **Detector.** Từ normal window, paper ước lượng `μ_o, σ_o`, đặt operation SLO `μ_o+1.5σ_o`, rồi cộng SLO theo số operation trong trace để tạo expected trace latency. Trace thực vượt expected bị xem là abnormal. Cửa sổ online 30 giây; khi trigger, thu thêm khoảng 5 phút trace.
- **UNVERIFIED discrepancy.** Code có nhánh `μ+σ` cộng 50 ms và system trigger khi số abnormal traces lớn hơn 8, trong khi paper mô tả `1.5σ`. Không có provenance đủ để khẳng định rule nào tạo toàn bộ bảng kết quả.
- **Graph/ranker.** PageRank chạy riêng trên normal/abnormal graph; preference cân trace size, trace-kind rarity/frequency. Hai PageRank scores làm trọng số cho spectrum counts, rồi Ochiai mặc định hoặc công thức spectrum khác tạo suspiciousness. Graph không đổi detector decision.
- **Học/output.** `UNSUPERVISED`, cần normal history, không cần root label để train. Output là operation ranking, không có fault type hay causal chain.
- **Dữ liệu/protocol.** Hipster Shop 10 services: Dataset A có 50 single-fault cases/~8.384M traces; B có 100 two-fault cases/~8.244M traces; bốn fault types resource/network, injection 3 phút. Dataset C production China Mobile có 7 faults/~168K traces và hạ granularity xuống service do thiếu operation name. Recall@k và EXAM; nhiều graph/spectrum/causal baselines. Paper báo A Recall@1 khoảng 94%, B Recall@2 khoảng 66%, C Recall@1 100% trên chỉ 7 faults.
- **Giới hạn tác giả.** Detector không phù hợp mọi hệ; hệ lớn cần nhiều PageRank iterations và có thể giảm accuracy; intermittent failures, broken traces và normal window ngắn làm giảm kết quả.
- **REVIEWER ANALYSIS.** Đây là graph-weighted RCA ranking cho latency, không phải PageRank anomaly detection. Broken trace experiment trong paper không đại diện service hoàn toàn không instrument hoặc missingness có thiên lệch.

### C.4. BARO

- **Problem/input/graph.** BARO là end-to-end change-point detection + metric/component ranking trên metrics. Nó không có graph; covariance đa biến không phải dependency graph.
- **Detector.** Multivariate Bayesian Online Change Point Detection chạy trên Latency và Errors; posterior run length reset/giảm tại change point. Normal segment nằm trước change point. Paper không đặt z-threshold cho detector; hazard/prior calibration ngoài mô tả chính là `UNVERIFIED`.
- **Ranker.** RobustScorer dùng mọi candidate metric: học median/IQR trên normal segment, tính `|x-median|/IQR` sau change point và lấy maximum làm metric score; component suy từ metric ownership.
- **Học/output.** `UNSUPERVISED`; output gồm anomaly time và ranked metrics/components. Không có propagation, causal path hoặc natural-language explanation.
- **Dữ liệu/protocol.** Online Boutique 12 services/49 metrics, Sock Shop 11/46, Train Ticket 64/212; 100 cases/system từ CPU hog, memory leak, network delay, packet loss. Detection P/R/F1 trên pre-injection/injection samples; RCA AC@1/3/5, Avg@5; detector baselines N-Sigma, BIRCH, SPOT, UniBCP; RCA baselines gồm RCD/CIRCA. Detector trung bình khoảng 30–173 giây tùy hệ, max gần 7 phút trên Train Ticket; ranker khoảng 0.01 giây.
- **Giới hạn tác giả.** Cause phải biểu hiện trong monitored metrics và cuối cùng ảnh hưởng Latency/Errors; chỉ bốn fault types và một collection/deployment strategy; logs/traces là future work.
- **REVIEWER ANALYSIS.** High deviation có thể chỉ ra affected component. Khi nguyên nhân nằm ngoài telemetry, gọi output là “root cause” sẽ vượt bằng chứng.

### C.5. RCD

- **Problem/input/graph.** RCD nhận metrics đã chia thành normal `D` và failure `D*`, thêm binary `F-NODE`, xem failure như soft intervention và tìm metric có conditional mechanism đổi.
- **Detector.** **NO SEPARATE ANOMALY DETECTOR**; failure time và regime split là input.
- **Graph/ranker.** RCD chia metric variables thành random chunks (`γ=5`), dùng Ψ-PC/chi-square conditional-independence tests, giữ neighbors của `F-NODE`, hợp và lặp đến top-k. Localized variant chỉ học neighborhood liên quan `F-NODE`, không xuất full causal graph. Finite-sample code xếp p-value nhỏ trước; alpha empirical (`0.01` local start; code thử trong khoảng `0.001–0.1`).
- **Học/output.** Không dùng historical root labels nhưng cần regime label normal/anomalous; phân loại hợp lý là `UNSUPERVISED đối với root labels`. Output ranked metrics, không có incident flag, propagation narrative hoặc natural language.
- **Dữ liệu/protocol.** Synthetic DAG 10–2,500 nodes, 100 runs; Sock Shop 13 services, CPU/memory trên năm services, 50 datasets với 5 phút normal + 5 phút fault, chạy 100 lần/dataset; ba production outages dùng khoảng hai ngày normal. Top-k recall và runtime; baselines Ψ-PC, ε-Diagnosis, AutoMAP, CIRCA.
- **Giới hạn tác giả.** Lý thuyết cần perfect CI oracle, causal sufficiency và extended faithfulness; không xử lý latent confounders; cần normal/failure split; cause ngoài observed boundary chỉ cho directly affected service.
- **REVIEWER ANALYSIS.** “Causal” chỉ đúng dưới assumptions. Random partition làm output stochastic; p-value ranking không phải calibrated causal probability.

### C.6. CIRCA

- **Problem/input/graph.** CIRCA xếp root-cause metric sau khi external detector cung cấp `detect_time`. Structural CBN được dựng từ system architecture/call graph và manual mapping metrics vào Traffic, Errors, Latency, Saturation.
- **Detector/score.** **NO SEPARATE ANOMALY DETECTOR**. Mỗi metric regression theo graph parents trên reference window; residual giả định Gaussian, chuẩn hóa và lấy maximum trong test interval. Cutoff cố định `3` lọc candidate. Graph trực tiếp quyết định parent set nên đổi graph có thể đổi score.
- **Ranker/propagation.** Descendant adjustment đưa evidence từ anomalous descendants lên ancestor candidate rồi xếp adjusted score. Đây là contextual/intervention scoring, không phải random walk/PageRank.
- **Học/output.** `UNSUPERVISED`; không incident/root label để fit. Output ranked root-cause indicators. Không sinh natural-language explanation hoặc counterfactual proof.
- **Dữ liệu/protocol.** Synthetic graphs 50/100/500 nodes; 99 high-AAS Oracle cases trong banking system, 197 metrics và 2,641 structural edges; mặc định reference 120 phút, delay 5, test 10, resample 1 phút. AC@1/3/5, Avg@5 và runtime; graph/scorer baselines. Real-data result báo AC@1 0.404, AC@5 0.763, Avg@5 0.603.
- **Giới hạn tác giả.** Causal assumptions có thể sai; missing meta-metrics tạo hidden common causes; reference normal có thể không đủ; descendant adjustment cần thêm dataset; graph/mapping cần domain knowledge.
- **REVIEWER ANALYSIS.** Real evidence không phải microservice public benchmark. Output nên được gọi observed intervention indicator; physical fault có thể nằm ngoài graph.

### C.7. Eadro

- **Problem/input/graph.** Eadro học chung binary anomaly detection và culprit-service localization. Log được Drain → event occurrence → Hawkes intensity → FC; KPI và trace-callee latency dùng dilated causal convolution + self-attention. Directed service graph lấy từ historical invocations.
- **Fusion/score.** Modality representations được concatenate, FC + GLU intermediate fusion thành node features; GAT tạo dependency-aware representation. Binary head và service-probability head dùng representation sau graph. Vì vậy graph trực tiếp tác động detector và ranker. Paper không mô tả rõ numeric decision threshold hoặc probability calibration: `UNVERIFIED`.
- **Học/output.** `SUPERVISED` multi-task; cần anomaly-window labels và culprit-service labels. Output anomaly flag/probability và ranked services; không có causal graph hoặc natural-language explanation.
- **Dữ liệu/protocol.** TrainTicket 41 active/27 business services và DeathStarBench SocialNetwork 21/14; CPU exhaustion, network jam/delay, packet loss; 162 và 72 injections; normal collection 7 giờ/1.2 giờ; 48,296 và 126,384 traces; chronological 60/40 split. Detection P/R/F1; localization HR@1/3/5, NDCG@3/5; advanced single/multi-source baselines. Ablation bỏ logs, KPIs, traces hoặc thay GAT bằng FC; w/o GAT giảm cả F1 và HR@1.
- **Giới hạn tác giả.** Không xử lý logical/silent issue không tạo observed anomaly; cần nhiều labeled data; chỉ hai benchmark/ba fault groups; thiếu modality có thể chạy nhưng kết quả giảm.
- **REVIEWER ANALYSIS.** Modality ablation không phải missing-aware training hoặc time-varying dropout. Eadro chứng minh multimodal graph joint AD/RCL đã có; “metrics+logs+traces+GNN” không phải gap.

### C.8. TORAI

- **Problem/input/graph.** TORAI làm coarse/fine RCA khi traces/call graph có blind spot. Metrics, Drain log-template counts và available trace latency/status theo operation được chuyển thành time series. Nó **không dựng service call graph**.
- **Detector/score.** **NO SEPARATE ANOMALY DETECTOR**; external `t_A` chia normal/failure. SeverityScorer học mean/std normal, lấy maximum standardized deviation sau `t_A`, gom thành service vector `[ρ_metric,ρ_log,ρ_trace]`; missing modality được điền `0`.
- **Graph/ranker.** GMM + BIC gom services theo severity; cluster xếp theo mean severity. Trong cluster, Ψ-PC học causal DAG trên time-series nodes; CausalRanker xếp bằng conditional-independence score. RankAggregation ghép cluster order và internal order. FineGrainer dùng median/IQR max deviation cho metric/log-template/trace indicator.
- **Học/output.** `UNSUPERVISED đối với root labels`; cần normal/failure split. Output ranked services và indicators; graph là cấu trúc trung gian, không có anomaly flag hoặc natural language.
- **Dữ liệu/protocol.** 270 cases: Online Boutique 11 services/77 metrics/33±9 logs/17 trace ops; Sock Shop 11/74/67±53/no traces; Train Ticket 64/376/163±44/148.3±26. Sáu faults, năm target services, ba repeats. Thêm 10 production incidents. AC@k/Avg@5 coarse/fine, runtime, chín baselines.
- **Missing-trace test.** Xóa trace theo service 0–100%, bước 10%. Online Boutique AC@1 64.4–88.9%; Train Ticket Avg@5 từ 89% xuống 71.6% thấp nhất ở 70%; Sock Shop vốn 100% blind spot. Không tái dựng graph.
- **Giới hạn tác giả.** Root indicator phải có distributional change; kết luận phụ thuộc systems/faults/tools; output có thể ưu tiên affected services khi root không có triệu chứng.
- **REVIEWER ANALYSIS.** Zero-imputation đánh đồng missing với “không thấy anomaly”. Random service-trace removal chưa kiểm missing spans, sampling bias, stale topology hoặc calibrated data-quality confidence.

### C.9. DéjàVu

- **Problem/input/graph.** DéjàVu localize recurring failures sau alert. Failure unit là một metric group/loại triệu chứng tại một component. FDG vô hướng nối failure units bằng call và deployment dependency từ trace/CMDB; kỹ sư có thể sửa.
- **Detector/score.** **NO SEPARATE ANOMALY DETECTOR**. GRU + 1D CNN/GELU/FC tạo unit features trong cửa sổ 20 phút; GAT aggregate FDG context; two-layer dense + sigmoid tạo suspicious score `[0,1]`. Không threshold binary; chỉ rank.
- **Học/output.** `SUPERVISED`; historical faulty/normal labels cho từng failure unit, weighted BCE và class balancing. Output ranked failure units, decision-tree surrogate toàn cục và similar historical incident cục bộ. Đây là model interpretation, không causal/natural-language explanation.
- **Dữ liệu/protocol.** 601 failures: production A 188/710 metrics/102 units; B 158/2,419/189; C 99 real Oracle/2,594/41; TrainTicket D 156/5,724/1,044. Dataset D có 64 services, tám ChaosMesh fault types. Metrics MAR và A@1/2/3/5; baselines matching, traditional ML, random walks; lặp 10 random runs.
- **Incomplete FDG.** Xóa ngẫu nhiên edge theo từng failure, lặp 10. Giảm không quá 10% edge làm suy giảm nhẹ; bỏ quá nhiều có thể tệ hơn `w/o AGG` vì graph sai truyền thông tin sai.
- **Giới hạn tác giả.** Failure-unit taxonomy chưa mã hóa đủ metric patterns/domain rules; model không transfer giữa hệ có định nghĩa unit khác; cần retraining khi drift; non-recurring failure score có thể thấp.
- **REVIEWER ANALYSIS.** Random edge deletion không phải graph reconstruction, missing span hay edge-confidence. DéjàVu là supervised graph localizer, không phải anomaly detector.

### C.10. DeepTraLog

- **Problem/input/graph.** DeepTraLog phát hiện anomalous request trace từ trace spans + associated logs. Nó tạo directed attributed Trace Event Graph (TEG) gồm span/log event nodes và sequence, synchronous request/response, asynchronous request relations.
- **Detector/score.** Drain parse log; event embedding dùng text features; GGNN message passing + soft-attention pool thành graph embedding. One-class deep SVDD học center/radius từ mostly normal training. Score là squared distance tới center trừ `R²`; score lớn hơn 0 nằm ngoài learned hypersphere và bị gắn anomaly. Experimental setting dùng `μ=0.05` cho phần dữ liệu được phép nằm ngoài hypersphere; đây không phải universal calibration.
- **Graph role/output.** Cấu trúc và node attributes cùng quyết định graph embedding, nên graph là chính observation được chấm. Output trace anomaly score/flag; không có service root-cause ranking. Attention không thay thế ground-truth localization.
- **Học.** `UNSUPERVISED` one-class; cho phép một phần outlier trong training, không cần fault label để fit.
- **Dữ liệu/protocol và chi phí.** TrainTicket v0.2.0, 45 services, 14 fault cases/73 fault instances; 132,485 traces và 7,705,050 logs, 23,334 anomalous traces. 60% normal train, 10% normal validation, test gồm 30% normal + toàn anomalous. P/R/F1; baselines TraceAnomaly, MultimodalTrace, DeepLog, LogAnomaly và GRU-SVDD ablation. Báo P=0.930, R=0.978, F1=0.954. Trên RTX 3090/65,490 train traces, 100 epochs mất 67.1 phút; test 56,080 traces mất 77 giây; một trace 800–900 events khoảng 4 ms. Các số thời gian chỉ có ý nghĩa trong đúng hardware/setup đó.
- **Giới hạn tác giả.** Chỉ một benchmark/fault suite; latent anomalies có thể lọt vào normal version; hai baselines được tác giả tự cài do không có implementation.
- **REVIEWER ANALYSIS.** Đây là graph-level anomaly detection thật, nhưng không làm RCA. Kết quả không chứng minh localize service/operation root cause hay xử lý missing telemetry.

### C.11. GDN

- **Problem/input/graph.** GDN phát hiện multivariate time-series anomaly. Node là sensor; directed edge được học bằng top-k cosine similarity giữa sensor embeddings, có thể giới hạn candidate relations bằng prior.
- **Detector/score.** Graph attention dự báo sensor value từ sliding window; normal-only training tối thiểu MSE. Per-sensor residual `|observed-predicted|` được chuẩn hóa bằng median/IQR. System score là max qua sensors rồi moving average. Threshold là maximum smoothed score trên validation normal.
- **Học/output.** `SELF-SUPERVISED` forecasting. Output time-tick anomaly flag/score và deviating sensors; learned edges, attention, predicted-vs-observed hỗ trợ technical inspection, không chứng minh causal root.
- **Dữ liệu/protocol.** SWaT 51 features, 47,515 train/44,986 test/11.97% anomaly; WADI 127, 118,795/17,275/5.99%. Normal training hai tuần, controlled attacks trong test, downsample 10 giây. P/R/F1; PCA, KNN, Feature Bagging, AE, DAGMM, LSTM-VAE, MAD-GAN. GDN F1 0.81 SWaT và 0.57 WADI.
- **Giới hạn tác giả.** Future work cần architecture/online training; bài không đánh giá microservice, missing sensor hay graph uncertainty.
- **REVIEWER ANALYSIS.** GDN là đối chứng Pattern C, không phải microservice RCA baseline mặc định. Sensor có deviation cao có thể là affected sensor.

### C.12. ARMOR

- **Problem/input/graph.** ARMOR làm AD, failure triage và root-cause instance localization khi metrics/logs/traces thiếu. Telemetry được căn thành one-minute time series; encoder ModernTCN tách theo modality. Observation mask, learnable missing token và negative gate bias phân biệt thiếu dữ liệu với zero/healthy-idle. Historical invocation topology đi qua GAT.
- **Detector/score.** Normal-only next-step reconstruction và stochastic modality dropout học representation. Online residual + latent graph embedding tạo unified signature, chiếu thành system deviation score. Streaming peaks-over-threshold/EVT fit trên normal scores tạo threshold; chỉ alert nếu vượt liên tục trong delay window.
- **Ranker/learning.** Instance score là cosine similarity giữa instance signature và time-aggregated global failure signature. AD/RCL `SELF-SUPERVISED`; failure-type classifier dùng labels. Output anomaly, fault type và ranked instances; không causal narrative hoặc natural language.
- **Dữ liệu/protocol.** D1: 46 nodes, 3,714 normal periods, 210 failures/five classes; D2: 18 instances, 12,297 normal periods, 133 failures/six types. Temporal 60/40 split. Chín baselines. AD P/R/F1; FT weighted F1; RCL Top@1/3, Avg@5.
- **Missingness test.** Whole-modality collapse; random modality/channel/element missingness 0–40%, mỗi điểm 10 masks; stochastic modality dropout ở train. Đây là simulated masking trên complete archives.
- **Giới hạn tác giả.** Historical archive phải đủ đầy để làm reconstruction target; persistent absence vượt training dropout chưa giải; topology evolution cần cập nhật; hai controlled datasets chưa bao phủ drift/cascade/timing-sensitive incidents. DOI trong manuscript là placeholder: `UNVERIFIED`.
- **REVIEWER ANALYSIS.** “Missing modality chưa được nghiên cứu” đã sai. Còn mở: topology/edge bị thiếu, biased missingness do load/failure, persistent archive gaps và confidence calibration theo data quality.

## D. Anomaly Detection và RCA Ranking

### D.1. Chuỗi khái niệm

```text
telemetry + baseline normal
        ↓
anomaly score + decision rule              (có sự cố không?)
        ↓
affected-component evidence                 (triệu chứng ở đâu?)
        ↓
candidate generation + root-cause ranking   (nên kiểm ứng viên nào trước?)
        ↓
causal/mechanistic validation                (vì sao và lan truyền thế nào?)
        ↓
natural-language explanation                (trình bày cho người vận hành)
```

Một paper có thể bắt đầu ở bất kỳ đoạn nào. Gọi toàn chuỗi là “RCA” không xóa ranh giới giữa các đầu ra.

### D.2. Năm pattern graph-based anomaly cần phân biệt

| Pattern | Graph đóng góp | Detector output | RCA ranking có tách riêng? | Ví dụ và giới hạn |
|---|---|---|---|---|
| **A. Local detector → graph ranker** | Nhận local scores/flags rồi lan truyền/cân bằng bằng chứng | Local call/trace/window anomaly | Có | MicroRCA, MicroRank. TORAI cũng nhận `t_A` trước causal graph |
| **B. Graph context trực tiếp đổi anomaly score** | Neighbor/message passing đổi representation đưa vào detector | Graph-conditioned probability/score | Có thể là head chung | Eadro; ARMOR. DeepTraLog/GDN cũng thuộc B theo nghĩa rộng |
| **C. GNN dự báo normal → residual anomaly** | Graph xác định expected node behavior | Prediction/reconstruction residual + threshold | Không tự suy ra RCA; cần bước localize/rank | GDN; ARMOR |
| **D. Graph/structure là observation bất thường** | Cấu trúc + thuộc tính toàn graph được embed/chấm | Graph-level score/flag | Thường không | DeepTraLog chấm TEG; không xếp root service |
| **E. Causal/contextual anomaly** | Parent set/causal context định nghĩa deviation có điều kiện | Conditional residual/intervention score | Thường nằm trong RCA sau trigger | CIRCA chấm residual theo parents nhưng nhận incident time; TORAI học causal graph sau `t_A` |

Patterns C và D là các cách cụ thể để thực hiện B; bảng dùng để định vị công đoạn chứ không phải taxonomy loại trừ nhau.

### D.3. Kết luận cho câu hỏi trung tâm

**Không.** Dùng PageRank sau một local anomaly detector không tự làm detector graph-based. Phép thử kỹ thuật là:

> Giữ nguyên telemetry/local score, thay hoặc bỏ graph. Nếu anomaly score hoặc anomaly decision trước bước ranking không đổi, graph không thuộc detector.

Với MicroRCA, BIRCH quyết định call edge bất thường trước khi PPR chạy. Với MicroRank, trace SLO quyết định normal/abnormal trước hai PageRank. Hai pipeline là graph-based RCA ranking thuộc Pattern A. Eadro, GDN, DeepTraLog và ARMOR mới cho các ví dụ nơi graph thực sự đi vào phép tính anomaly score.

## E. Phân tích PageRank/PPR

| Paper | PageRank input | Vai trò | Output | Có phải anomaly detector? |
|---|---|---|---|---|
| MicroRCA | Reversed anomalous service–host graph; edge/correlation weights; personalization từ service anomaly score | Propagation + candidate ranking | Ranked services | **Không**; BIRCH đã quyết định anomaly |
| MicroRank | Normal và abnormal operation–trace graphs, call transitions, trace preferences | Tạo normal/abnormal evidence weights cho spectrum ranker | PageRank weights rồi suspiciousness | **Không**; SLO đã chia trace sets |
| DéjàVu baseline RandomWalk | Metric causal graph hoặc FDG + correlations | Baseline localization | Ranked metric/failure unit | Không; paper chính không dùng PageRank làm detector |

PageRank có thể được dùng theo năm nghĩa khác nhau:

- **centrality:** node quan trọng trong cấu trúc;
- **propagation:** truyền mass theo edge;
- **candidate ranking:** score cuối cho ứng viên;
- **anomaly scoring:** chỉ khi score PageRank được so với baseline normal có rule/ngưỡng rõ;
- **graph refinement:** cập nhật trọng số/cạnh, không tự là detector.

Trong các paper trọng tâm của Task A, MicroRCA và MicroRank dùng ba nghĩa đầu, không dùng nghĩa thứ tư. Centrality cao trong graph khỏe vẫn có thể hoàn toàn bình thường. Vì vậy không được viết “PageRank anomaly detection” nếu chưa định nghĩa normal PageRank behavior, deviation score, calibration và evaluation unit.

## F. Phân tích GNN

| Method | GNN học gì | Graph và feature | Nhãn/training | Score/output | GNN thuộc công đoạn nào | Chi phí/giả định chính |
|---|---|---|---|---|---|---|
| Eadro | Dependency-aware system/service representation | Service invocation graph; fused log/KPI/trace node features | Supervised multi-task; 50 epochs; anomaly + culprit labels | Binary probability + culprit probabilities | Detector **và** localizer | Graph lịch sử đủ đúng; nhiều labels; hai benchmark; wall-clock training cost `UNVERIFIED` |
| DéjàVu | Propagation pattern giữa failure units | FDG; metric-window unit features | Supervised, per-system failure labels; 3,000 epochs | Suspicious score per failure unit | Localizer | Huấn luyện theo từng hệ; retraining khi drift; online localization dưới 1 giây trong setup bài báo |
| DeepTraLog | Whole TEG embedding | Directed span/log event graph | One-class/mostly-normal; GGNN + deep SVDD; 100 epochs | Distance to hypersphere | Detector | 67.1 phút train/77 giây test trên RTX 3090 trong setup bài; một TrainTicket dataset; không root output |
| GDN | Sensor dependency + expected next values | Learned top-k sensor graph; history windows | Normal-only forecasting, up to 50 epochs | Robust-normalized prediction residual | Detector + affected-sensor clue | Fixed sensor universe; CPS domain; threshold từ validation normal; wall-clock cost không chuyển trực tiếp sang microservices |
| ARMOR | Missing-aware fused/topological representation | Historical invocation graph; masked modality embeddings | Self-supervised reconstruction + modality dropout; FT labels riêng | POT-threshold anomaly + cosine instance rank | Detector **và** localizer | Complete-enough archive; explicit mask; topology updates; wall-clock training cost `UNVERIFIED` |

GNN không mặc nhiên “cao cấp hơn”. So sánh khoa học phải giữ input, candidate set, label budget, split và compute budget đủ công bằng, rồi ablate graph/GNN. Eadro và DéjàVu cho thấy graph aggregation có thể giúp nhưng cũng có trường hợp top-1 giảm hoặc graph sai gây hại; deeper GNN còn có over-smoothing ở DeepTraLog/ARMOR.

## G. Phân tích telemetry đa nguồn

| Method/source | Chuẩn hóa từng nguồn | Điểm fusion | Học chung? | Điều đã giải quyết | Điều chưa tự giải quyết |
|---|---|---|---|---|---|
| Eadro | Drain+Hawkes cho logs; DCC+attention cho KPI/trace latency | Intermediate representation: concat → FC → GLU → GAT | Có, supervised multi-task | Ba modality + graph cho AD/RCL | Missing-aware calibration; logical/silent fault; ít labels |
| TORAI | Tất cả thành time series; z-like severity per modality | Score/vector level trước GMM; causal graph sau clustering | Không joint neural | Chạy được khi service thiếu trace/call graph | Missing=0; không graph reconstruction/confidence |
| DeepTraLog | Span/log events thành embeddings | Graph level: logs gắn vào trace structure | Có, one-class graph encoder | Trace+log structural AD | Không metrics, RCA, missingness |
| ARMOR | One-minute alignment, z-score; asymmetric encoders | Mask/missing token + gated attention → GAT | Có, self-supervised backbone | Whole modality/channel/value missingness | Persistent missing archive; edge loss; natural missingness external validity |
| LEMMA-RCA v4 | IT metrics; Drain/keyword/TF-IDF-derived log time series | Baseline-level metric+log combination | Tùy baseline | Public multi-domain metric/log RCA | Paper v4 không chứng minh trace/operation graph availability |

**Kết luận bằng chứng:** “metrics + logs + traces” và “fusion đa nguồn” là `LIKELY ALREADY COVERED`. Phần còn có thể tạo câu hỏi mới phải chỉ rõ ít nhất một cơ chế: alignment, modality reliability, missingness, causal validity, label efficiency hoặc benefit sau ablation. Chỉ tăng số nguồn không đủ.

## H. Missing telemetry và chất lượng graph

| Vấn đề | Prior work gần nhất | Missingness được mô hình ra sao | Có quality/confidence? | Có reconstruction? | Evaluation | Trạng thái sau Task A |
|---|---|---|---|---|---|---|
| Service không có trace | TORAI | Bỏ trace theo service; modality score=0 | Không calibrated | Không; tránh service call graph | 0–100%, bước 10%; SS 100% | `LIKELY ALREADY COVERED` |
| Thiếu whole modality | ARMOR | Observation mask, learnable missing token, gate bias, dropout | Mask/gate có, probability calibration theo quality không | Không | Sáu collapse combinations | `LIKELY ALREADY COVERED` |
| Thiếu channel/value | ARMOR | Random channel/element masks | Gate, không edge confidence | Không | 0–40%, 10 masks/point | `LIKELY ALREADY COVERED` trong synthetic masking |
| FDG thiếu cạnh | DéjàVu | Random edge deletion | Không | Không | Lặp 10; ≤10% giảm nhẹ, nhiều cạnh có thể hại | `LIKELY ALREADY COVERED` trong protocol hẹp |
| Bỏ một modality khỏi model | Eadro | Train/test ablation w/o L/K/T | Không | Không | HR/F1 ablation | Bằng chứng contribution, không missing-aware robustness |
| Broken trace/intermittent failure | MicroRank | Author warning; coverage giảm | Không | Không | Giới hạn/experiment hẹp | Đã nhận diện, chưa đủ cho realistic missingness |
| Missing parent/meta-metric | CIRCA | Hidden common cause phá graph assumption | Không | Không | Author limitation | Rủi ro đã biết, chưa giải quyết |
| Thiếu span nội bộ/cạnh có thiên lệch | Không được kiểm đầy đủ trong tập nguồn | `OPEN` | `OPEN` | `OPEN` | Cần Task B/protocol mới | `NEEDS MORE EVIDENCE` |
| Tái dựng graph + calibrated edge confidence | Không thấy giải pháp tương đương trong tập nguồn giới hạn | `OPEN` | `OPEN` | `OPEN` | Cần search/audit sâu hơn | `NEEDS MORE EVIDENCE`, không tuyên bố gap |

Ba phân biệt bắt buộc:

1. **Trace modality vắng** khác **một số spans/edges vắng**.
2. **Graph cũ/sai** khác **graph không tồn tại**.
3. **Attention weight/gate** khác **calibrated probability rằng edge/evidence đúng**.

TORAI và ARMOR đã làm cho tuyên bố rộng “missing telemetry chưa được nghiên cứu” không còn đứng vững. Khoảng có thể audit tiếp phải hẹp hơn và kiểm cơ chế thiếu thực tế, topology error cùng uncertainty.

## I. Granularity của graph và output

| Method | Node/đơn vị graph | Edge | Detection granularity | Localization granularity | Nhãn hỗ trợ | Điều granularity này không chứng minh |
|---|---|---|---|---|---|---|
| MicroRCA | Service, host | Call, deployment | Service-call edge | Service | Faulty service trong injected run | Không biết operation/code line |
| MicroRank | Service-instance operation + trace | Operation call, operation–trace coverage | Request trace | Operation, hạ xuống service khi data thiếu | Faulty operation/service tùy dataset | Operation rank không phải fault class |
| BARO | Không graph; metric series | — | Timestamp/sample/change point | Metric → owning component | Injected service + indicator | Metric lệch nhất là causal root |
| RCD | Metric + F-node | Learned conditional dependencies | Không detector | Metric; map sang service | Intervention target/fault service | Full propagation path |
| CIRCA | Metric | Structural/causal parent relation | Không detector | Metric/root-cause indicator | Fault indicator | Physical cause ngoài observed graph |
| Eadro | Service | Historical invocation | System observation window | Service | Window anomaly + culprit service | Operation/instance/fault type |
| TORAI | Time-series nodes trong causal graph; service severity groups | Learned causal dependencies | Không detector | Service rồi metric/log template/trace operation indicator | Service + indicator | Indicator là code operation root |
| DéjàVu | Failure unit = component × metric group | Call/deployment dependency | Không detector | Failure unit | Historical faulty unit/class | Transfer được taxonomy sang hệ khác |
| DeepTraLog | Span/log event trong một request TEG | Sequence/request/response | Whole request trace graph | Chỉ event attention, không RCA rank | Trace anomaly | Event attention là root cause |
| GDN | Sensor | Learned dependency | Time tick | Deviating sensor | Attack interval/sensor docs | Sensor bị ảnh hưởng là attack origin |
| ARMOR | Service/VM instance | Historical invocation | System time window | Instance | Timestamp/type/root instance | Operation hoặc causal chain |

Operation-level precedent đã tồn tại ở MicroRank, trace-operation indicators của TORAI và event graph của DeepTraLog. “Graph mịn hơn” không tự tốt hơn vì candidate set lớn hơn, signal thưa hơn, label khó hơn và graph dễ thiếu cạnh hơn. Muốn so granularity phải giữ nguyên failure cases, input budget và root-label semantics; nếu dataset chỉ có service label thì không thể chứng minh operation-level accuracy.

## J. Độ đo đánh giá

### J.1. Metric phải gắn với task và evaluation unit

| Metric | Task/đơn vị phù hợp | Ground truth cần | Điểm cao nghĩa là | Không chứng minh |
|---|---|---|---|---|
| Precision | Binary/multilabel detection theo timestamp, window, trace hoặc entity-window | Positive anomaly labels cùng đơn vị + threshold | Ít false alarms trong các positive predictions | Root cause rank đúng; causal validity |
| Recall | Cùng đơn vị detection | Tất cả positive labels | Bỏ sót ít anomalies | False alarm thấp; ranking tốt |
| F1 | Cùng đơn vị detection, cùng threshold | Precision/Recall hợp lệ | Cân bằng harmonic giữa P/R tại một operating point | Calibration tốt; latency-to-detect; RCA đúng |
| Hit@k/Top@k | Một ranked list/failure case | Root-cause set + candidate universe | Có ít nhất một true root trong top-k | Tìm đủ multi-root; detector đúng |
| RCAEval `AC@k` | Một ranked list/failure case | Root-cause set | Trung bình normalized count của roots trong top-k | Không phải textbook Precision@k trong mọi trường hợp |
| MRR | Một ranked list/failure case | Vị trí true root đầu tiên; miss=0 | Root đầu tiên thường đứng sớm | Chất lượng các root còn lại; explanation |
| NDCG@k | Ranking với binary hoặc graded relevance | Relevance labels + gain/discount/ideal definition | Relevance tập trung ở vị trí đầu | Severity/causal contribution nếu label không chứa chúng |
| MAP/PR@k variants | Ranking với một/nhiều roots | Công thức paper + root set | Chất lượng precision tích lũy theo cutoff | So sánh được nếu hai paper dùng công thức khác |
| Avg@k | Trung bình `AC@1..k` trong RCAEval/BARO/TORAI | `AC@j` hợp lệ | Hiệu năng tổng hợp nhiều cutoff | MRR/NDCG hoặc detection performance |
| EXAM | Effort/rank normalized theo candidate list | Candidate universe đầy đủ | Tỷ lệ danh sách phải kiểm thấp | Top-k success trong candidate universe khác |

Với đúng một root, `Hit@k = Recall@k`; textbook `Precision@k = Hit@k/k`. Với binary relevance và một root, `NDCG@k` chỉ là `1/log2(r+1)` nếu root ở vị trí `r≤k`, ngược lại 0. Vì vậy NDCG hợp lệ nhưng không tự tạo thông tin về “mức độ nguyên nhân”. Nguồn nền của discounted cumulative gain là [Järvelin & Kekäläinen 2002](https://doi.org/10.1145/582415.582418).

### J.2. Protocol quyết định ý nghĩa của metric

- Detection P/R/F1 chỉ hợp lệ nếu paper thực sự chạy detector; không được cấp injection time rồi gọi ranking success là detection.
- RCA metrics phải tính trên từng failure case; hàng triệu spans không phải hàng triệu thí nghiệm độc lập.
- Window cùng incident không được tùy ý rải vào cả train và test. Eadro chia theo thời gian; ARMOR dùng temporal 60/40; các protocol khác nhận known failure window.
- Candidate bị thiếu khỏi universe phải được báo là coverage failure, không xóa case để tăng score.
- “Fine-grained” cần ground truth cùng granularity; service label không chấm được operation/code-line claim.
- Tên giống nhau chưa chắc công thức giống nhau. RCAEval `AC@k`, LEMMA `PR@k`, MicroRCA Precision@k và Eadro HR/NDCG phải giữ nguyên định nghĩa nguồn.

Task A không chọn bộ metric cuối. Nó chỉ kết luận email của cô đang hàm ý ít nhất hai evaluation tasks độc lập: binary anomaly detection và ranked root-cause localization.

## K. Dataset được literature sử dụng

### K.1. Benchmark application không phải một dataset duy nhất

| Tên | Literature use đã xác minh | Telemetry/task/ground truth | Điều không được đồng nhất |
|---|---|---|---|
| Train Ticket | DeepTraLog v0.2.0: 45 services, trace+logs, trace anomaly; DéjàVu: 64 services, metrics/FDG, failure unit; Eadro: 41 active/27 business, triple-source joint AD/RCL; BARO/TORAI/RCAEval: các collection riêng | Thay đổi theo paper: trace anomaly, service/failure-unit ranking, AD/RCL | “Train Ticket” không xác định version, service count, data schema hoặc fault suite |
| Sock Shop | MicroRCA 13 services/95 runs; RCD 13 services/50 datasets; BARO 11 services/100 cases; TORAI 11 services/90 cases và không trace | Metrics/call response time hoặc multi-source tùy collection; labels chủ yếu injected service | Có source code không có nghĩa mọi dataset phái sinh có trace/operation labels |
| Online Boutique/Hipster Shop | MicroRank Hipster Shop 10 services; BARO 12; TORAI 11; RCAEval paper đếm 12 | Trace localization hoặc metric/multisource RCA; injected service/indicator | Tên hệ và quy ước đếm không cố định; `loadgenerator`/support component có thể làm số khác |

### K.2. RCAEval

| Dataset | Paper công bố | Task hỗ trợ | Telemetry theo literature | Ground truth công bố | `OPEN — Task B` |
|---|---|---|---|---|---|
| RE1 | 375 cases, 3 systems, CPU/MEM/DISK/DELAY/LOSS | Metric RCA | Metrics only | Root service + metric indicator | Exact release/hash; healthy length; candidate mapping |
| RE2 | 270 cases, 3 systems, 6 resource/network faults | Multi-source RCA | Metrics/logs/traces ở benchmark level; current artifact docs ghi Sock Shop trace N/A | Root service + indicator | Per-case modality presence; TT span schema; parent linkage; operation stability |
| RE3 | 90 cases, 3 systems, 5 code-level faults | Code-level multi-source RCA | Metrics/logs/traces ở benchmark level; Sock Shop trace N/A | Root service + root indicator/stack/error evidence | Có code-line/operation label thật không; stack trace coverage; exact trace fields |

Bài RCAEval v5 chỉ báo bảng preliminary RCA trên **Train Ticket–RE2**. Không được suy bảng đó thành kết quả cho toàn bộ ba systems/datasets. Coarse-grained là root service; fine-grained là root indicator, không tự đồng nghĩa operation root.

### K.3. LEMMA-RCA hiện hành

Revision v4 ngày 2026-08-25, metadata ghi accepted CIKM 2026, gồm:

- Product Review: six OpenShift nodes, 216 pods, bốn IT fault types, node/pod metrics + Elasticsearch logs, JMeter latency KPI;
- Cloud Computing: sáu IT fault types, CloudWatch metrics/logs, latency/error/utilization KPIs;
- SWaT: 51 sensors, 16 recorded faults trước filtering;
- WADI: 123 sensors/actuators, 15 recorded faults trước filtering; bảng v4 sau segmentation ghi chín fault types;
- baseline evaluation dùng PR@k, MAP@k và MRR.

V4 không mô tả trace như một released modality cho bốn sub-datasets. Nó mô tả metric/log fusion ở hai IT datasets và sensor time series ở OT datasets. Con số “51 cases” của revision cũ không được tự mang sang v4. Topology, trace schema, operation identity và số case sau từng preprocessing rule phải được audit ở Task B nếu muốn dùng.

## L. Ma trận khoảng trống nghiên cứu ứng viên

Không mục nào dưới đây là novelty claim. `POTENTIAL GAP` chỉ có nghĩa đáng kiểm tiếp.

| Candidate gap | Existing closest work | Work đó đã giải | Phần còn có vẻ chưa giải | Evidence strength / classification | Dataset requirement | Complexity | Risk đã được giải |
|---|---|---|---|---|---|---|---|
| Graph tạo giá trị gì ngoài local anomaly score | MicroRCA, Eadro, DéjàVu, GDN ablations | Graph rank/context có thể cải thiện score | Controlled test giữ input/local score/candidate cố định, thay graph đúng/sai/không graph | Trung bình · `NEEDS MORE EVIDENCE` | Cùng cases, graph variants, AD + RCA labels | Trung bình | Trung bình–cao |
| Graph-aware anomaly detection cho microservices | DeepTraLog, Eadro, GDN, ARMOR | B/C/D patterns đã tồn tại | Một formulation hẹp gắn với failure/telemetry cụ thể | Mạnh về prior · `LIKELY ALREADY COVERED` | Timestamp/window/trace anomaly labels | Cao | Cao |
| Granularity–observability trade-off | MicroRank, TORAI, DéjàVu, DeepTraLog | Operation, indicator, failure-unit, event levels đã có | So service/operation/resource trên cùng data/labels/cost | Trung bình · `NEEDS MORE EVIDENCE` | Labels ở nhiều mức + complete candidate mapping | Cao | Trung bình |
| Operation-aware representation | MicroRank, TORAI, DeepTraLog | Operation/trace events đã dùng | Chỉ còn gap nếu định nghĩa/edge/label khác và tạo lợi ích đo được | Mạnh · `LIKELY ALREADY COVERED` | Stable operation ID, parent-child spans | Trung bình–cao | Cao |
| Multimodal metrics–logs–traces | Eadro, TORAI, ARMOR; nhiều work trong LEMMA | Fusion ở representation/score/graph; joint training | Công bằng của modality contribution hoặc reliability-aware fusion | Mạnh · `NOT A RESEARCH GAP` nếu chỉ “dùng ba nguồn” | Aligned modalities + ablations | Cao | Rất cao |
| Missing trace/whole modality | TORAI, ARMOR | 0–100% service trace loss; modality/channel/value masks | Natural biased loss, persistent gaps, internal missing spans | Mạnh · `LIKELY ALREADY COVERED` ở formulation rộng | Missingness masks + natural outages + labels | Cao | Cao |
| Incomplete topology + calibrated edge confidence | DéjàVu random deletion; ARMOR/TORAI không reconstruct | Sensitivity/avoidance đã có | Reconstruct/weight uncertain edges, separate missing vs healthy, calibrate output | Yếu–trung bình · `NEEDS MORE EVIDENCE` | Span/link integrity + known/perturbed graph | Rất cao | Trung bình–cao |
| Shared-resource failure domain | MicroRCA service–host; DéjàVu deployment FDG | Host/deployment edges và cross-component units | Fair value under co-location/dynamic scheduling | Trung bình · `LIKELY ALREADY COVERED` ở ý tưởng rộng | Pod/node placement + fault labels | Trung bình | Cao |
| Faithful natural-language explanation | DéjàVu interpretation; GALA+/LLM incident work | Rules/similar cases và graph-augmented LLM đã có | Faithfulness với ranked evidence, abstention, operator usefulness | Yếu trong survey này · `NEEDS MORE EVIDENCE` | Evidence packets + expert rubric | Trung bình–cao | Cao; cần survey riêng |
| Nhiều dataset hơn | RCAEval, LEMMA, TORAI | Cross-system benchmark đã có | Coverage/realism/label semantics, không phải số lượng | Mạnh · `NOT A RESEARCH GAP` | Dataset audit + external-validity design | Trung bình | Rất cao |

Không có hàng nào đạt `STRONG CANDIDATE`. Trước khi hình thành RQ, cần Task B xác minh dataset có thật sự hỗ trợ task và granularity dự kiến, rồi search hẹp cho hàng còn `NEEDS MORE EVIDENCE`.

## M. Diễn giải mức phù hợp với định hướng giảng viên

> **Đính chính nguồn, 2026-09-23:** bảng dưới giữ nguyên như bằng chứng diễn giải ở Task A, **không dùng cột `DIRECTLY SUPPORTED BY EMAIL` để trích nguyên nghĩa thư 22/08**. [Thư gốc](../evidence/advisor-direction/2026-08-22-dinh-huong-de-tai.md) không nêu PageRank/subgraph/GNN, bộ P/R/F1/MRR/NDCG hoặc lựa chọn LLM cụ thể; thư nêu AI hỗ trợ giải thích và khảo sát độ đo phù hợp. Hướng dẫn cụ thể của Minh ở Task C không phải lời giảng viên trong quá khứ; xem [EC-09](D:/Project/flash-ticket-rca-research/task-c/task-c-evidence-cross-review.md). Nhiệm vụ hiện hành theo [DT18](../evidence/project-direction/2026-09-18-de-tai-va-nhiem-vu.md); vai trò LLM theo [RCA-010/011](RESEARCH-DECISIONS.md). Notice chỉ sửa quyền quy nguồn, không sửa lịch sử khảo sát hoặc trạng thái phê duyệt `DRAFT`.

| Bước trong email | `DIRECTLY SUPPORTED BY EMAIL` | `TECHNICAL INTERPRETATION` từ literature | Điểm cần hỏi lại sau |
|---|---|---|---|
| **1. Xây dependency graph** | Graph giữa các dịch vụ | Phải định nghĩa node, edge, hướng, nguồn cạnh, thời gian hiệu lực, coverage và version. Service graph là mức tối thiểu trực tiếp từ email; operation/resource layers chưa được email chốt | Cô yêu cầu service-only graph hay cho phép/đòi thêm operation/resource nodes? |
| **2. Graph-based anomaly detection; ví dụ PageRank, subgraph, GNN** | Log/trace được ánh xạ và có thuật toán phát hiện bất thường trên graph | Cách thỏa rõ nhất: graph trực tiếp thay anomaly score/decision (B/C), hoặc graph object bị chấm bất thường (D). Local AD + graph ranker chỉ chứng minh graph-based RCA, không tự chứng minh graph-based detector | Cô dùng cụm từ cho detector nghiêm ngặt hay cho toàn pipeline detection→RCA? PageRank là ví dụ thuật toán bắt buộc hay ví dụ khái niệm? |
| **3. Thử public datasets** | Nêu Train Ticket, Sock Shop, LEMMA-RCA | Public data tạo reproducibility và external validation. Phải chỉ exact derived dataset/release, telemetry và labels; ba tên không tương đương ba schema giống nhau | Ba tên là danh mục bắt buộc hay ví dụ? Revision LEMMA nào và task nào cần chạy trên từng bộ? |
| **4. Baselines + P/R/F1/MRR/NDCG** | So graph method với RCA baselines; dùng các metric nêu trong email | Ít nhất có hai tasks: P/R/F1 cho detector theo unit cụ thể; MRR/NDCG cho root ranking. Một run có known injection time không được tính là end-to-end detection | Có bắt buộc báo mọi metric trên mọi dataset không, hay map metric theo task/label availability? |
| **5. LLM explanation** | LLM diễn giải root cause và gợi ý bước xử lý | LLM nên nhận structured ranked evidence sau detector/ranker; đánh giá faithfulness, factuality, abstention và usefulness. Nó không thay ground-truth causal validation | Tiêu chí đánh giá lớp giải thích và mức cho phép sử dụng external knowledge là gì? |

Email trực tiếp yêu cầu hiểu sâu prior work, chỉ ra hạn chế rồi mới tiếp cận phương pháp graph. Email không chọn graph schema, PageRank variant, GNN, detector, dataset release, RQ hoặc novelty claim. Các lựa chọn này phải giữ `OPEN`.

## N. Những tuyên bố chưa được phép đưa ra

1. “Graph-based RCA và graph-based anomaly detection là cùng một bài toán.”
2. “Có PageRank nên detector là graph-based.”
3. “PageRank centrality cao là bất thường.”
4. “Metrics + logs + traces là đóng góp mới.”
5. “GNN là bắt buộc hoặc mặc nhiên tốt hơn statistical/causal baseline.”
6. “Graph chi tiết hơn luôn cho RCA tốt hơn.”
7. “Operation-level RCA chưa có prior work.”
8. “Missing traces/missing modality chưa được nghiên cứu.”
9. “Random edge deletion chứng minh robustness với mọi graph-quality failure.”
10. “Attention/gate weight là calibrated confidence.”
11. “Top-ranked affected component là physical root cause.”
12. “Causal discovery output là causal explanation đã được chứng minh.”
13. “Train Ticket/Sock Shop/Online Boutique là các dataset có schema cố định.”
14. “RCAEval fine-grained label chắc chắn là operation hoặc code-line label.”
15. “LEMMA-RCA hiện có traces/operation graph” khi v4 không mô tả chúng.
16. “Nhiều dataset tự động làm kết luận mạnh hơn.”
17. “Kiến trúc hệ thống mới tạo ra phương pháp RCA mới.”
18. “LLM viết hợp lý nghĩa là explanation trung thành hoặc causal.”
19. Bất kỳ câu “phương pháp đề xuất là mới/tốt hơn” trước khi có baseline, protocol và kết quả.

## O. DATASET AUDIT QUESTIONS — chuyển nguyên vẹn sang Task B

Những câu hỏi sau không thể trả lời đáng tin chỉ từ paper. Task B phải kiểm artifact/raw metadata; không suy đoán.

### O.1. Phiên bản, phả hệ và quyền dùng

1. Exact release, commit, dataset revision và checksum của từng Train Ticket, Sock Shop, Online Boutique, RCAEval RE2/RE3 và LEMMA-RCA candidate là gì?
2. Paper, repository, dataset card và archive có trỏ tới cùng snapshot không?
3. License cho raw telemetry, derived features và redistributable experiment artifacts là gì?
4. Service count dùng cách đếm nào: business services, infrastructure components, pods, instances hay loadgenerator?

### O.2. Trace và khả năng dựng graph

5. RE2-Train Ticket raw trace có stable operation identity hay chỉ có span name biến đổi theo ID/URL?
6. Exact fields và types trong `traces.parquet`/CSV là gì: `traceId`, `spanId`, `parentSpanId`, service, operation, kind, status, duration, resource attributes?
7. Có route template/RPC method ổn định hay chỉ raw URL/path có cardinality cao?
8. Bao nhiêu `parentSpanId` resolve được trong cùng trace; bao nhiêu orphan/root/multiple-parent anomalies?
9. Có thể phân biệt SERVER/CLIENT/PRODUCER/CONSUMER, database, cache và messaging spans không?
10. Service graph edges có thể dựng trực tiếp từ parent-child spans với hướng caller→callee không?
11. Operation graph edges có thể dựng ổn định giữa runs/versions không?
12. Trace sampling rate, head/tail sampling policy và coverage theo service/operation/fault type là gì?
13. Missing spans/services phân bố ngẫu nhiên hay tương quan với load, fault, language, error, sampling hoặc instrumentation?
14. Trace timestamps, metrics và logs có cùng clock/timezone và độ phân giải đủ để align không?

### O.3. Logs, metrics và liên kết đa nguồn

15. Logs có `traceId`/`spanId`/service-instance identity đủ để gắn vào trace graph không?
16. Log template IDs đã có sẵn hay phải parse; parser version/configuration và template stability giữa runs là gì?
17. Metrics map được đến service, pod, container, node, database và shared resource bằng key ổn định nào?
18. Metric names/units/sampling gaps có nhất quán giữa healthy và fault windows không?
19. Mỗi case thật sự có modality nào; tỷ lệ row/span/log missing là bao nhiêu, thay vì suy từ nhãn “multimodal” ở benchmark level?
20. Có thể phân biệt missing telemetry với giá trị 0/không có event không?

### O.4. Normal baseline, incident và labels

21. Healthy data dài bao lâu cho từng entity; có đủ để fit threshold/calibration theo workload regime không?
22. Healthy periods có contamination, warm-up, deploy/change, drift hoặc leakage từ cùng incident không?
23. Ground-truth anomaly time là injection timestamp, first symptom, alert time hay manually curated interval?
24. Detection label có ở timestamp/window/trace/entity level nào?
25. Root-cause ground truth chính xác là service, instance, pod, host, metric, log line/template, operation hay code line?
26. Dataset có nhãn affected components tách khỏi root cause không?
27. Có multi-root cases không; nếu có, root set và relative relevance/severity được ghi thế nào?
28. Candidate universe có chứa mọi true root không; alias/service naming được chuẩn hóa thế nào?
29. Fine-grained indicator trong RE2/RE3 là observed symptom hay injected/verified causal origin?
30. LEMMA-RCA v4 sau preprocessing còn chính xác bao nhiêu cases ở mỗi sub-dataset, và ground truth map đến node/pod/sensor nào?

### O.5. Tính khả thi của thí nghiệm sau này

31. Có thể tạo split theo incident/run và thời gian mà không rò cùng injection/workload vào train/test không?
32. Có đủ cases theo fault type/system để ước lượng uncertainty theo failure case thay vì theo span/row không?
33. Có artifact để dựng graph đúng, graph bị thiếu có kiểm soát và no-graph baseline trên cùng input không?
34. Có natural missingness cases hoặc chỉ có complete archive để tạo synthetic masks?
35. Compute/storage cần để chạy detector/ranker trên exact subset là bao nhiêu; subset nào giữ nguyên task/label semantics mà không cherry-pick?

## P. Tóm tắt thực thi subagent

Đã dùng đúng ba subagent độc lập, không có recursive delegation:

- **Subagent 1 — RCA methods:** MicroRCA, MicroRank, BARO, RCD, CIRCA; kiểm pipeline, paper–artifact discrepancy, dataset/protocol và limitations.
- **Subagent 2 — graph/multimodal:** Eadro, DéjàVu, TORAI; DeepTraLog, GDN và nguồn current ARMOR; kiểm graph roles, missingness và granularity.
- **Subagent 3 — methodology critic:** tách năm tasks, metric semantics, dataset-name traps, supervisor alignment, gap overclaim và red-team.

Main agent kiểm lại bằng nguồn chính, hợp nhất theo một taxonomy và chịu trách nhiệm kết luận. Những điểm được hiệu chỉnh khi tổng hợp:

- ARMOR được thêm làm phương pháp bổ sung thứ ba vì trực tiếp bác bỏ gap rộng về missing modality.
- GDN được giữ làm conceptual control, không xem là microservice RCA evidence.
- LEMMA được đọc theo v4 hiện hành; con số và modality của revision cũ không được carry forward.
- MicroRank paper/code discrepancy và mọi threshold chưa xác minh được giữ `UNVERIFIED`.
- Không subagent nào được chọn phương pháp, RQ hoặc dataset cuối.

## Q. Red-team findings

| Phát biểu cần thách thức | Phán quyết dựa trên literature |
|---|---|
| **“Graph-based RCA = graph-based anomaly detection.”** | **Sai như một đẳng thức.** MicroRCA/MicroRank dùng graph ở ranker; CIRCA/TORAI/DéjàVu còn nhận incident time từ ngoài. |
| **“PageRank means graph anomaly detection.”** | **Sai.** Trong hai paper PageRank trọng tâm, nó truyền/cân bằng evidence và xếp hạng sau detection. |
| **“Multimodal telemetry is novel.”** | **Sai nếu đứng một mình.** Eadro, TORAI, ARMOR và DeepTraLog đã có nhiều fusion forms. |
| **“Fine-grained graph is better.”** | **Chưa có căn cứ.** Finer graph tăng candidate count, sparsity, missing edges và label demand; cần controlled comparison. |
| **“Missing traces are an unexplored problem.”** | **Sai.** TORAI đánh giá 0–100% trace blind spots; ARMOR kiểm missing modality/channel/value. Missing spans/topology uncertainty hẹp hơn vẫn `OPEN`. |
| **“Using a GNN makes the method more advanced.”** | **Không phải tiêu chí khoa học.** GNN là model choice; benefit, labels, compute và failure mode mới là bằng chứng. |
| **“More datasets automatically means stronger science.”** | **Sai.** Dataset không hỗ trợ task/label, lặp cùng fault model hoặc khác metric semantics chỉ tăng số bảng. |
| **“A novel system architecture implies a novel RCA method.”** | **Sai.** Novelty của deployment/application không suy ra novelty của problem formulation, score, estimator hoặc causal reasoning. |

### Điều kiện dừng Task A

Task A đã đạt điều kiện dừng: representative prior work được map; detection và ranking đã tách kỹ thuật; graph roles đã phân loại; overclaims và candidate gaps đã được red-team; câu hỏi raw data được chuyển sang Task B. Không có method, RQ, dataset hoặc implementation nào được chọn.
