# Buyer Assistant

Service Java 21 / Spring Boot 4.1.1, Spring Cloud 2025.1.3, LangChain4j 1.23.0. Assistant gọi Gemini qua proxy OpenAI-compatible có sẵn, không triển khai thêm service. Redis lưu một active conversation theo issuer + subject JWT. Widget và `/chatbot` dùng chung provider frontend.

## Phạm vi

Sáu model tools: `searchEvents`, `getEvent`, `getAvailability`, `getMyOrders`, `getMyOrder`, `prepareQuantityPurchase`. Chỉ gọi GET trên Event/Booking origins được cấu hình, relay JWT của buyer hiện tại; không SQL, service credential, write tool hoặc URL do model chọn. Booking kiểm ownership thêm lần nữa.

Draft chỉ hỗ trợ QUANTITY. Draft không giữ tồn, khóa giá hoặc tạo đơn. Người dùng nhập email trực tiếp vào form React và bấm xác nhận; form gọi `bookingService.createOrder` hiện có với `draftId` làm Idempotency-Key. Email không đi qua chat/model. Chỉ OrderView thực tế mới dẫn sang checkout; chat “đồng ý” không tạo đơn.

## Cấu hình và chạy

Copy [`.env.example`](.env.example) thành `.env` tại service, điền giá trị đã xác nhận và nạp bằng cơ chế env-file của IDE/runtime hiện có. Spring/Maven không tự nạp file `.env` này. Không ghi đè biến bằng CLI/JVM hoặc biến môi trường tạm để bù cấu hình thiếu; không commit secret.

Các biến bắt buộc: `ASSISTANT_SERVICE_PORT`, `KEYCLOAK_ISSUER_URI`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `EUREKA_SERVER_URL`, `ASSISTANT_MODEL_BASE_URL`, `ASSISTANT_MODEL_NAME`, `ASSISTANT_MODEL_API_KEY`, `ASSISTANT_EVENT_BASE_URL`, `ASSISTANT_BOOKING_BASE_URL`, `ASSISTANT_REDIS_KEY_PREFIX`.

- Proxy: `ASSISTANT_MODEL_BASE_URL=https://model.sondimid.cloud/v1` (không gắn `/chat/completions`), `ASSISTANT_MODEL_NAME=gemini-3.8-flash-high`. Nhập key của proxy vào `ASSISTANT_MODEL_API_KEY` trực tiếp trong env-file. Hai biến `ASSISTANT_GEMINI_*` cũ không còn được đọc.
- Không tự đổi port khi xung đột. Base URL của **Event/Booking** là origin `http(s)://host[:port]`, không path nghiệp vụ, userinfo, query hoặc fragment. Buyer API client không follow redirect.
- Dùng `issuer-uri` để discovery JWKS; không runtime audience/jwk-set-uri riêng.
- Prefix Redis riêng theo môi trường, chỉ chữ/số/`:_-`, tối đa 160 ký tự. Không dùng prefix của Booking.
- JWT BUYER không gửi tới proxy. Proxy và upstream nhận prompt và phần kết quả tool được đưa vào ngữ cảnh. Kiểm tool calling nhiều lượt với dữ liệu giả/công khai; xác minh log, retention và chính sách dữ liệu của cả proxy lẫn upstream trước khi gửi thông tin đơn riêng tư. Nếu đã có hội thoại từ client Gemini cũ, kiểm replay trước rollout; không mặc định context hai client tương thích.

Sau khi có JDK 21 và env-file đã nạp đúng:

```bash
mvn -f services/assistant-service/pom.xml spring-boot:run
```

Service đăng ký Eureka với tên `assistant-service`. Gateway dùng route `lb://assistant-service`, timeout riêng 55 giây và time limiter 60 giây; biến nằm trong [Gateway env template](../../infrastructure/api-gateway/.env.example). Việc đổi adapter không bật deploy hoặc thêm workload K3s.

## HTTP contract

Prefix `/api/v1/assistant`, JWT hợp lệ + realm role BUYER; response `Cache-Control: no-store`.

| Endpoint | Hành vi |
|---|---|
| POST `/conversations/current` | Mở phiên hiện có hoặc tạo phiên rỗng; không gọi model/renew phiên cũ. |
| GET `/conversations/current` | Đọc UI transcript, version, lastRequestId, expiry; không trả raw context/tool traces. |
| POST `/messages` | `{conversationId, version, requestId, message}`; không nhận lịch sử/owner/token từ body. |
| POST `/conversations/reset` | `{conversationId, version}`; reset có CAS và fence worker cũ. |

401 thiếu/invalid JWT; 403 không BUYER; 409 busy/stale/conflicting request; 410 hết phiên; 429 quota; 503 dependency/capacity; 504 deadline. Downstream không hợp lệ có thể trả 502. Không echo raw exception/body lỗi.

## Memory, retry và giới hạn

- Redis state/lease/quota cùng owner hash slot; key không chứa subject hoặc JWT thô.
- State TTL 24 giờ chỉ khởi tạo/renew khi tạo mới, reset hoặc commit nguyên lượt thành công. Read/open-existing/replay không renew.
- Một lease 60 giây/owner; commit kiểm conversation + version + live lease, không upsert state đã hết/reset. Release compare-delete không xóa lease mới.
- Mỗi lượt có memory/tools/AI service riêng; framework không ghi Redis sau từng tool. Chỉ outer service commit thành công toàn lượt. Cancel Future không bảo đảm provider HTTP đã dừng hoặc không tính phí.
- Replay thành công gần nhất với requestId + exact normalized hash trả saved response; khác hash conflict. Sau uncertainty đọc GET để reconcile, không tự resend nội dung cũ hoặc tạo key mới.
- Input 2.000 ký tự; reply 8.000; context 30 framework messages/96 KiB; transcript 50 messages/96 KiB, trim nguyên lượt.
- 3 tool-containing model round trips và 6 individual tool executions; output 2.048 tokens; provider retries 0.
- Turn 45 giây; model call 15 giây; HTTP connect/read 2/5 giây; Redis 2 giây. Executor 4 jobs/replica, không queue; 6 new attempts/phút/owner, failures tính quota. Chưa có global daily spend cap.
- Tool result 16 KiB; downstream body 256 KiB sau giải nén; page 5; description 1.000 ký tự. Payload lớn fail closed, không tải seat-map vào model.
- Một draft/lượt, 10 phút, tối đa 10 ticket lines, quantity 1–99/line. Trần này không thay purchase limit của Booking.

Frontend giữ payload/email sau lần confirm đầu trong localStorage theo owner/draft; Web Locks serialize confirm giữa các tab, browser thiếu Web Locks fail closed. Reset/logout che state và vô hiệu callback cũ, không hủy order đã đến Booking. Đọc lại conversation trước confirm giảm stale-tab submits nhưng không tạo transaction chung giữa Assistant reset và Booking POST; reset không phải thu hồi quyền đặt đơn tại Booking.

Text render qua React, không render HTML/model-generated component. Che email/Bearer/JWT nhận diện được không phải DLP toàn diện. Không gửi mật khẩu/token vào chat; Redis TTL không xóa provider retention/backup/log hạ tầng.

## Kiểm chứng

Xem [TESTING.md](TESTING.md) cho bằng chứng, blockers và checklist chưa chạy. [Plan được duyệt](../../docs/java-workflow/plans/2026-11-17-buyer-assistant-plan.md) chứa inventory và ranh giới quyền. Không coi compile/offline tests là bằng chứng proxy thật hỗ trợ tool calling, ownership runtime Booking, browser acceptance, tải hoặc deployment.
