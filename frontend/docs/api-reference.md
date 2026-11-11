# Flash Ticket — API Reference cho Frontend

> Tổng hợp từ source tại commit `27262fe` (main). Chỉ gồm API public qua **API Gateway**. `/internal/**` và `/actuator/**` bị gateway chặn.

---

## 0. Thông tin chung

### 0.1 Base URL

| Thành phần | URL |
|---|---|
| API Gateway (FE gọi duy nhất vào đây) | `http://52.64.237.84:8081` |
| Keycloak (đăng nhập) | `http://52.64.237.84:8080` — realm `flash-ticket` |
| Local dev | `http://localhost:8081` |

> Port 8081 (gateway) và 8080 (Keycloak) đã mở ra ngoài, gọi trực tiếp từ trình duyệt được.

Biến cho các ví dụ curl:

```bash
BASE=http://52.64.237.84:8081
TOKEN=eyJhbGciOi...   # access_token từ Keycloak
```

### 0.2 Xác thực (Keycloak OIDC)

- Client FE: `frontend`, public client, **Authorization Code + PKCE (S256)**. Password grant (`directAccessGrants`) đang **tắt**.
- Endpoint OIDC:
  - Authorize: `http://52.64.237.84:8080/realms/flash-ticket/protocol/openid-connect/auth`
  - Token: `http://52.64.237.84:8080/realms/flash-ticket/protocol/openid-connect/token`
  - Logout: `http://52.64.237.84:8080/realms/flash-ticket/protocol/openid-connect/logout`
  - Discovery: `http://52.64.237.84:8080/realms/flash-ticket/.well-known/openid-configuration`
- Issuer (`iss` trong token): `http://52.64.237.84:8080/realms/flash-ticket`. Trang login/đăng ký và mọi link Keycloak trả về đều dùng host public này, không còn `localhost`.
- Token phải lấy từ Keycloak của đúng môi trường: token từ Keycloak chạy local trên máy dev (issuer `http://localhost:8080/...`) gọi gateway server 84 sẽ bị `401`, và ngược lại.
- Gửi token qua header: `Authorization: Bearer <access_token>`.
- Role lấy từ realm roles: `BUYER`, `ORGANIZER`, `ADMIN`. Gateway map sang `ROLE_*`.
- Env FE: `VITE_KEYCLOAK_URL=http://52.64.237.84:8080`, `VITE_KEYCLOAK_REALM=flash-ticket`, `VITE_KEYCLOAK_CLIENT_ID=frontend`, `VITE_API_GATEWAY_URL=http://52.64.237.84:8081`.

> Client `frontend` đã cho phép:
> - Redirect URI: `http://localhost:5173/*`, `http://52.64.237.84:5173/*`, `http://52.64.237.84/*` và Postman.
> - Web origin: `http://localhost:5173`, `http://52.64.237.84:5173`, `http://52.64.237.84`.
> - Redirect sau logout: dùng chung danh sách redirect URI (`+`).
>
> FE chạy ở origin khác thì phải thêm origin đó vào client trên Keycloak.

Muốn test curl nhanh thì lấy token qua Postman (OAuth 2.0 → Authorization Code with PKCE, callback `https://oauth.pstmn.io/v1/callback`).

### 0.3 Phân quyền tại Gateway

| Path | Yêu cầu |
|---|---|
| `OPTIONS /**` | Public (CORS preflight) |
| `GET /api/v1/events`, `GET /api/v1/events/{id}`, `GET /api/v1/events/{id}/availability` | Public |
| `GET /api/v1/categories`, `GET /api/v1/venues`, `GET /api/v1/organizers/{subject}` | Public |
| `GET /api/v1/payments/vnpay/return`, `GET /api/v1/payments/vnpay/ipn` | Public (VNPay gọi) |
| `/api/v1/admin/**` | `ADMIN` |
| `/api/v1/organizer/**` | `ORGANIZER` |
| Còn lại | Đăng nhập (một số endpoint còn kiểm role ở service, ghi ở từng mục) |
| `/internal/**`, `/actuator/**` | Luôn bị từ chối |

### 0.4 Header chung

| Header | Khi nào | Ghi chú |
|---|---|---|
| `Authorization: Bearer <token>` | Mọi API không public | |
| `Content-Type: application/json` | Request có body JSON | Upload ảnh dùng `multipart/form-data` |
| `Idempotency-Key: <UUID v4>` | **Bắt buộc** với: `POST /orders`, `POST /payments`, `POST /orders/{id}/ticket-deliveries`, `POST /events/{id}/check-ins` | Retry cùng request thì dùng lại key cũ. Body khác mà cùng key → `409 IDEMPOTENCY_CONFLICT` |
| `X-Correlation-Id` | Tuỳ chọn | Gateway trả lại trong response header để trace log |

- CORS: gateway trên server 84 cho phép origin `http://localhost:5173`, `http://52.64.237.84:5173`, `http://52.64.237.84` (biến `CORS_ALLOWED_ORIGINS`, mặc định local là `http://localhost:5173`), có cho phép credentials. FE chạy ở origin khác thì phải thêm vào secret `ENV_API_GATEWAY` rồi chạy lại CD. Header được expose cho FE: `X-Correlation-Id`, `X-Request-Id`.
- Rate limit: 10 req/s, burst 20, tính theo IP → `429 RATE_LIMIT_EXCEEDED`.

### 0.5 Quy ước dữ liệu

- **Tiền là chuỗi số nguyên VND**, ví dụ `"150000"`. Không gửi số thực.
- `rowVersion` và `sourceConfigVersion` là **chuỗi**. Lấy từ response gần nhất rồi gửi lại khi update (optimistic lock). Sai version → `409`.
- Thời gian theo ISO-8601 UTC, ví dụ `"2026-12-01T12:00:00Z"`.
- ID là UUID dạng chuẩn, chữ thường.
- Phân trang có hai kiểu:
  - **Cursor** (event, user): `?size=20&cursor=<nextCursor>`. Response là `{ "items": [...], "nextCursor": "..." | null }`. `nextCursor = null` nghĩa là hết trang.
  - **Page** (payment admin): `?page=0&size=20`. Response là `{ items, page, size, totalElements, totalPages }`.
- Booking và Ticket dùng mapper strict: **field lạ trong body bị từ chối (400)**. Những endpoint ghi "body `{}`" phải gửi đúng `{}`.

### 0.6 Định dạng lỗi

**Gateway, Booking, Event, Ticket, User** (`application/problem+json` hoặc `application/json`):

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "correlationId": "7f0c...",
  "violations": [ { "field": "customerEmail", "code": "INVALID_FORMAT" } ]
}
```

**Payment** — mọi response đều bọc trong `ApiResponse`:

```json
// thành công
{ "success": true,  "message": null, "data": { ... } }
// lỗi: data là object Problem
{ "success": false, "message": "...", "data": { "code": "PAYMENT_EXPIRED", "message": "...", "correlationId": "...", "violations": [] } }
```

**Lỗi do Gateway trả:**

| HTTP | code |
|---|---|
| 400 | `INVALID_REQUEST` |
| 401 | `UNAUTHENTICATED` (token thiếu hoặc hết hạn → refresh token rồi login lại) |
| 403 | `ACCESS_DENIED` (sai role) |
| 404 | `ROUTE_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 413 | `PAYLOAD_TOO_LARGE` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` |
| 429 | `RATE_LIMIT_EXCEEDED` |
| 502 | `DEPENDENCY_FAILURE` |
| 503 | `DEPENDENCY_UNAVAILABLE` (service down hoặc circuit breaker mở) |
| 504 | `DEPENDENCY_TIMEOUT` |
| khác | `INTERNAL_ERROR` |

---

## 1. User Service — hồ sơ, organizer, follow

Lỗi riêng: `INVALID_ID` (400), `INVALID_PAGE_SIZE` (400, size phải từ 1 đến 100), `INVALID_SUBJECT` (400/401), `INVALID_IMAGE` (400), `UNSUPPORTED_IMAGE` (415, chỉ nhận PNG/JPEG), `IMAGE_TOO_LARGE` (413, tối đa 2MB), `USER_NOT_FOUND`, `ORGANIZER_NOT_FOUND`, `APPLICATION_NOT_FOUND` (404), `USER_STORAGE_UNAVAILABLE` (503). Validation body lỗi → `400 INVALID_REQUEST` kèm `violations[].code` lấy từ tên constraint (`NotBlank`, `Size`).

### 1.1 `GET /api/v1/me` — hồ sơ người đăng nhập (tự tạo ở lần gọi đầu)

```bash
curl -s "$BASE/api/v1/me" -H "Authorization: Bearer $TOKEN"
```
Response 200:
```json
{ "userId": "uuid", "identitySubject": "keycloak-sub", "displayName": "Nguyễn Văn A" }
```

### 1.2 `PUT /api/v1/me` — đổi tên hiển thị

```bash
curl -s -X PUT "$BASE/api/v1/me" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"displayName":"Nguyễn Văn B"}'
```
- Request: `displayName` bắt buộc, không rỗng, tối đa 200 ký tự.
- Response: `UserProfile`, giống mục 1.1.

### 1.3 `POST /api/v1/organizer-applications` — đăng ký làm organizer

```bash
curl -s -X POST "$BASE/api/v1/organizer-applications" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"organizationName":"ABC Live","shortDescription":"Tổ chức concert"}'
```
- Request: `organizationName` (≤200) và `shortDescription` (≤2000), cả hai bắt buộc.
- Response **201** `ApplicationView`:
```json
{
  "id": "uuid", "identitySubject": "sub", "organizationName": "ABC Live", "shortDescription": "...",
  "status": "PENDING", "rejectionReason": null, "decidedBySubject": null, "decidedAt": null,
  "createdAt": "2026-11-08T03:00:00Z", "version": "0"
}
```
- `status`: `PENDING` | `ACTIVE` | `REJECTED`.
- Khi được duyệt (`ACTIVE`), user nhận role `ORGANIZER`. FE cần **refresh token** để token mới có role này.

### 1.4 `GET /api/v1/organizer-applications?size=20&cursor=` — đơn của tôi
Response: `{ "items": [ApplicationView], "nextCursor": "uuid" | null }`

### 1.5 `GET /api/v1/organizer-applications/{id}` — chi tiết đơn của tôi
Response là `ApplicationView`. Đơn của người khác trả `404 APPLICATION_NOT_FOUND`.

### 1.6 `GET /api/v1/organizers/{organizerSubject}` — trang organizer (public)

```bash
curl -s "$BASE/api/v1/organizers/<sub>"
```
```json
{ "organizerSubject": "sub", "organizationName": "ABC Live", "shortDescription": "...",
  "followerCount": 12, "logoUrl": "https://res.cloudinary.com/...", "bannerUrl": null }
```

### 1.7 Follow organizer (đăng nhập)

| Method | Path | Tác dụng |
|---|---|---|
| `GET` | `/api/v1/organizers/{sub}/follow` | Xem trạng thái follow |
| `PUT` | `/api/v1/organizers/{sub}/follow` | Follow (idempotent) |
| `DELETE` | `/api/v1/organizers/{sub}/follow` | Unfollow |

```bash
curl -s -X PUT "$BASE/api/v1/organizers/<sub>/follow" -H "Authorization: Bearer $TOKEN"
```
Response: `{ "organizerSubject": "sub", "following": true, "followerCount": 13 }`

### 1.8 `POST /api/v1/organizers/me/{logo|banner}` — upload logo hoặc banner (organizer ACTIVE)

```bash
curl -s -X POST "$BASE/api/v1/organizers/me/logo" -H "Authorization: Bearer $TOKEN" -F "file=@logo.png"
```
- Multipart, part tên `file`, PNG hoặc JPEG, tối đa 2MB.
- Response: `PublicOrganizer` (mục 1.6) với `logoUrl`/`bannerUrl` mới.

### 1.9 Admin duyệt organizer (`ADMIN`)

| Method | Path | Body | Response |
|---|---|---|---|
| `GET` | `/api/v1/admin/organizer-applications?status=PENDING&size=20&cursor=` | — | `Page<ApplicationView>` |
| `GET` | `/api/v1/admin/organizer-applications/{id}` | — | `ApplicationView` |
| `POST` | `/api/v1/admin/organizer-applications/{id}/approve` | — | `ApplicationView` (status `ACTIVE`) |
| `POST` | `/api/v1/admin/organizer-applications/{id}/reject` | `{"reason":"..."}` (bắt buộc, ≤2000) | `ApplicationView` (status `REJECTED`) |

```bash
curl -s -X POST "$BASE/api/v1/admin/organizer-applications/<id>/reject" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"reason":"Thiếu giấy phép"}'
```

---

## 2. Event Service — sự kiện, danh mục, sơ đồ ghế

Lỗi riêng: `INVALID_REQUEST` (400, `violations[].code = INVALID_FIELD`), `RESOURCE_NOT_FOUND` (404), `IMAGE_TOO_LARGE` (413, ảnh sự kiện tối đa 5MB), `CONFIGURATION_CONFLICT` và `CONCURRENT_EVENT_CHANGE` (409, sai `rowVersion` → tải lại rồi thử lại), `INVALID_EVENT_CONFIGURATION` (422), `STORAGE_UNAVAILABLE` (503), `UNAUTHENTICATED` (401).

### 2.1 Public

#### `GET /api/v1/events?q=&categoryId=&size=20&cursor=` — danh sách sự kiện đang mở

```bash
curl -s "$BASE/api/v1/events?size=20"
curl -s "$BASE/api/v1/events?q=concert&categoryId=<uuid>&size=10&cursor=<nextCursor>"
```
Response: `{ "items": [PublicEvent], "nextCursor": "..." | null }`

#### `GET /api/v1/events/{eventId}` — chi tiết sự kiện
Response `PublicEvent`:
```json
{
  "eventId": "uuid",
  "title": "Concert X",
  "description": "...",
  "startsAt": "2026-12-20T12:00:00Z",
  "endsAt": "2026-12-20T15:00:00Z",
  "venueName": "Nhà hát Lớn",
  "mode": "QUANTITY",
  "images": [ { "imageId": "uuid", "objectReference": "https://...", "displayOrder": 0 } ],
  "ticketTypes": [
    { "ticketTypeId": "uuid", "name": "VIP", "unitPrice": "1500000", "currency": "VND",
      "sectorId": null, "configuredQuantity": 100, "colorCode": "#FF0000" }
  ],
  "seatMap": null
}
```
- `mode`: `QUANTITY` (chọn số lượng) hoặc `SEAT_MAP` (chọn ghế; khi đó `seatMap` có dữ liệu, cấu trúc xem mục 2.4).

#### `GET /api/v1/categories` và `GET /api/v1/venues`
```json
[ { "id": "uuid", "name": "Âm nhạc", "address": null } ]
```
(venue có `address`)

### 2.2 Organizer (`ORGANIZER`) — quản lý sự kiện của mình

Prefix: `/api/v1/organizer/events`

| Method | Path | Body | Response |
|---|---|---|---|
| `POST` | `/` | `Draft` | **201** `OwnerEvent` |
| `GET` | `/?status=DRAFT&size=20&cursor=` | — | `Page<Summary>` |
| `GET` | `/{id}` | — | `OwnerEvent` |
| `PUT` | `/{id}` | `{ "rowVersion": "3", "draft": Draft }` | `OwnerEvent` |
| `POST` | `/{id}/submit` | `{ "rowVersion": "3" }` | `OwnerEvent` (→ `PENDING_APPROVAL`) |
| `POST` | `/{id}/publish` | `{ "rowVersion": "5" }` | `OwnerEvent` (`APPROVED` → `PUBLISHED`) |
| `POST` | `/{id}/cancel` | `{ "rowVersion": "5" }` | `OwnerEvent` (huỷ khi chưa publish) |
| `POST` | `/{id}/cancellation-requests` | `{ "rowVersion": "6", "reason": "..." }` | **201** `CancellationRequest` (xin admin huỷ sự kiện đã publish) |
| `GET` | `/{id}/cancellation-requests?size=&cursor=` | — | `Page<CancellationRequest>` |
| `GET` | `/{id}/seat-map` | — | `SeatMap` |
| `PUT` | `/{id}/seat-map?rowVersion=3` | `SeatMap` | `OwnerEvent` |
| `PUT` | `/{id}/ticket-types` | `{ "rowVersion": "3", "ticketTypes": [ { "ticketTypeId", "sectorId", "name", "unitPrice", "currency", "colorCode" } ] }` | `OwnerEvent` |
| `POST` | `/{id}/sales-mode` | `{ "rowVersion": "3", "mode": "SEAT_MAP", "confirmDeleteIncompatibleConfiguration": true }` | `OwnerEvent` |
| `GET` | `/{id}/statistics` | — | `Statistics` |

**`Draft`** (tạo hoặc sửa):
```json
{
  "title": "Concert X",
  "description": "...",
  "categoryId": "uuid",
  "venueId": "uuid",
  "mode": "QUANTITY",
  "startsAt": "2026-12-20T12:00:00Z",
  "endsAt": "2026-12-20T15:00:00Z",
  "saleStartsAt": "2026-11-10T00:00:00Z",
  "saleEndsAt": "2026-12-20T10:00:00Z",
  "purchaseLimit": 4,
  "images": [ { "imageId": "uuid", "objectReference": "https://...", "displayOrder": 0 } ],
  "ticketTypes": [
    { "ticketTypeId": "uuid-do-FE-sinh", "name": "VIP", "unitPrice": "1500000",
      "currency": "VND", "configuredQuantity": 100, "colorCode": "#FF0000" }
  ],
  "promotions": [
    { "promotionId": "uuid", "code": "SALE10", "startsAt": "...", "endsAt": "...", "maximumUses": 100,
      "discount": { "kind": "PERCENT", "amount": null, "percent": 10 } }
  ]
}
```
- `discount.kind`: `FIXED` (dùng `amount`, chuỗi VND) hoặc `PERCENT` (dùng `percent`).
- `purchaseLimit` > 0: số vé tối đa một người được mua.

```bash
curl -s -X POST "$BASE/api/v1/organizer/events" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d @draft.json
```

**`OwnerEvent`:**
```json
{
  "eventId": "uuid", "organizerSubject": "sub", "title": "...", "description": "...",
  "categoryId": "uuid", "venueId": "uuid", "mode": "QUANTITY", "status": "DRAFT",
  "startsAt": "...", "endsAt": "...", "saleStartsAt": "...", "saleEndsAt": "...",
  "purchaseLimit": 4, "rowVersion": "1", "sourceConfigVersion": "1",
  "feeRate": null, "approvedAt": null,
  "images": [...], "ticketTypes": [...], "promotions": [...], "seatMap": null
}
```
- `status`: `DRAFT` → `PENDING_APPROVAL` → `APPROVED` → `PUBLISHED`. Ngoài ra có `REJECTED` và `CANCELLED`.

**`Summary`:** `{ eventId, title, status, mode, startsAt, endsAt, rowVersion, sourceConfigVersion }`

**`CancellationRequest`:**
```json
{ "requestId": "uuid", "eventId": "uuid", "status": "PENDING", "requestedBy": "sub",
  "requestedAt": "...", "reason": "...", "reviewedBy": null, "reviewedAt": null,
  "reviewReason": null, "eventRowVersion": "6" }
```

**`Statistics`:**
```json
{ "eventId": "uuid", "asOf": "...", "salesAsOf": "...", "financeAsOf": "...",
  "purchasedTicketCount": 120, "currency": "VND",
  "validGross": "18000000", "validRefunds": "0", "feeAmount": "900000", "netAmount": "17100000" }
```

### 2.3 Ảnh sự kiện (`ORGANIZER`, timeout 45s)

Prefix: `/api/v1/organizer/events/{eventId}/images`

| Method | Path | Body | Response |
|---|---|---|---|
| `GET` | `/` | — | danh sách ảnh |
| `POST` | `/` | multipart: `rowVersion`, `displayOrder`, `file` | **201** `OwnerEvent` |
| `PUT` | `/{imageId}` | multipart: `rowVersion`, `file` | `OwnerEvent` (thay ảnh) |
| `PATCH` | `/{imageId}` | JSON `{ "rowVersion": "4", "displayOrder": 1 }` | `OwnerEvent` |
| `DELETE` | `/{imageId}?rowVersion=4` | — | `OwnerEvent` |

```bash
curl -s -X POST "$BASE/api/v1/organizer/events/<id>/images" -H "Authorization: Bearer $TOKEN" \
  -F "rowVersion=3" -F "displayOrder=0" -F "file=@poster.jpg"
```

### 2.4 `SeatMap` (sự kiện `mode = SEAT_MAP`)

```json
{
  "schemaVersion": 1, "layoutId": "uuid", "name": "Sân khấu A",
  "canvasWidth": 1200, "canvasHeight": 800, "backgroundImageReference": null,
  "layoutData": { "schemaVersion": 1, "decorations": [ { "id": "stage", "label": "SÂN KHẤU", "colorCode": "#333", "shape": {} } ] },
  "sectors": [
    {
      "sectorId": "uuid", "name": "Khu A", "code": "A", "kind": "SEATED",
      "configuredCapacity": 100, "displayOrder": 0, "colorCode": "#00AAFF",
      "geometry": {
        "schemaVersion": 1, "shape": {},
        "seatLayout": { "mode": "GRID", "rows": 10, "seatsPerRow": 10, "gapX": 4, "gapY": 4,
          "paddingX": 8, "paddingY": 8, "offsetX": 0, "offsetY": 0, "seatRadius": 10,
          "rowStartCharCode": 65, "seatStartNumber": 1 },
        "editorVisible": true, "editorLocked": false
      },
      "seats": [ { "seatId": "uuid", "seatLabel": "A1", "rowName": "A", "seatNumber": 1,
                   "coordX": 100, "coordY": 50, "ticketTypeId": "uuid", "isHidden": false } ]
    }
  ]
}
```
- `kind`: `SEATED` (chọn từng ghế) hoặc `STANDING` (khu đứng, chọn số lượng).
- Ở public view (`PublicEvent.seatMap`) không có các field dành cho editor.

### 2.5 Admin (`ADMIN`)

| Method | Path | Body | Response |
|---|---|---|---|
| `GET` | `/api/v1/admin/events?status=PENDING_APPROVAL&size=20&cursor=` | — | `Page<Summary>` |
| `GET` | `/api/v1/admin/events/{id}` | — | `OwnerEvent` |
| `POST` | `/api/v1/admin/events/{id}/approve` | `{ "rowVersion": "4", "feeRate": "0.05" }` | `OwnerEvent` |
| `POST` | `/api/v1/admin/events/{id}/return-to-draft` | `{ "rowVersion": "4" }` | `OwnerEvent` |
| `POST` | `/api/v1/admin/events/{id}/cancel` | `{ "rowVersion": "4" }` | `OwnerEvent` |
| `GET` | `/api/v1/admin/cancellation-requests?status=PENDING&size=&cursor=` | — | `Page<CancellationRequest>` |
| `GET` | `/api/v1/admin/events/{id}/cancellation-requests` | — | `Page<CancellationRequest>` |
| `POST` | `/api/v1/admin/cancellation-requests/{requestId}/approve` | `{ "rowVersion": "6" }` | `CancellationRequest` (huỷ sự kiện → tự động hoàn tiền) |
| `POST` | `/api/v1/admin/cancellation-requests/{requestId}/reject` | `{ "rowVersion": "6", "reason": "..." }` | `CancellationRequest` |

`feeRate` là phí nền tảng, ví dụ `"0.05"` = 5%.

---

## 3. Booking Service — tồn vé và đặt đơn

Lỗi riêng (`code`):
- **400:** `VALIDATION_FAILED`, `INVALID_REQUEST`. `violations[].code` là một trong `REQUIRED`, `INVALID_FORMAT`, `OUT_OF_RANGE`, `INVALID_VALUE`.
- **404:** `ORDER_NOT_FOUND`.
- **409:** `SEAT_UNAVAILABLE`, `INSUFFICIENT_QUANTITY`, `PURCHASE_LIMIT_EXCEEDED`, `EVENT_NOT_ON_SALE`, `ORDER_EXPIRED`, `ORDER_NOT_PAYABLE`, `ORDER_NOT_CANCELLABLE`, `PAYMENT_IN_PROGRESS`, `PRICE_FROZEN`, `IDEMPOTENCY_CONFLICT`, `CONCURRENT_MODIFICATION`.
- **422:** `INVALID_SELECTION`, `ORDER_AMOUNT_EXCEEDED`, `NON_POSITIVE_TOTAL`, `PROMOTION_NOT_FOUND`, `PROMOTION_OUTSIDE_WINDOW`, `PROMOTION_EXHAUSTED`, `PROMOTION_ALREADY_USED`, `ORDER_ALREADY_HAS_PROMOTION`.
- **503:** `CONFIG_NOT_READY`, `RESOURCE_BUSY`, `DATASTORE_*`, `DEPENDENCY_*`. Lỗi tạm thời, FE retry với backoff và giữ nguyên `Idempotency-Key`.

### 3.1 `GET /api/v1/events/{eventId}/availability` — tồn vé realtime (public, no-store)

```bash
curl -s "$BASE/api/v1/events/<eventId>/availability"
```
```json
{
  "eventId": "uuid", "sourceConfigVersion": "3", "asOf": "2026-11-08T03:00:00Z",
  "ticketTypes": [ { "ticketTypeId": "uuid", "available": "57" } ],
  "sectors":     [ { "sectorId": "uuid", "kind": "STANDING", "available": "200" } ],
  "seats":       [ { "seatId": "uuid", "state": "AVAILABLE" } ]
}
```
- Seat `state`: `AVAILABLE`, `HELD` (đang được người khác giữ), `PURCHASED`, `UNAVAILABLE`.
- FE nên poll endpoint này (ví dụ 3–5 giây một lần) khi đang ở màn chọn vé.

### 3.2 `POST /api/v1/orders` — tạo đơn, giữ vé (đăng nhập + `Idempotency-Key`)

```bash
curl -s -i -X POST "$BASE/api/v1/orders" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "eventId": "uuid",
    "customerEmail": "buyer@example.com",
    "selection": { "mode": "QUANTITY", "items": [ { "ticketTypeId": "uuid", "quantity": 2 } ] }
  }'
```
`selection` có ba dạng, chọn đúng một (không gửi thừa field):
```jsonc
// 1) Sự kiện mode QUANTITY — KHÔNG có "kind"
{ "mode": "QUANTITY", "items": [ { "ticketTypeId": "uuid", "quantity": 2 } ] }
// 2) SEAT_MAP, khu ngồi — chọn ghế
{ "mode": "SEAT_MAP", "kind": "SEATED", "sectorId": "uuid", "seatIds": ["uuid", "uuid"] }
// 3) SEAT_MAP, khu đứng — chọn số lượng
{ "mode": "SEAT_MAP", "kind": "STANDING", "sectorId": "uuid", "items": [ { "ticketTypeId": "uuid", "quantity": 3 } ] }
```
- `customerEmail` phải là email hợp lệ, tối đa 254 ký tự. Vé sẽ được gửi về địa chỉ này.
- Response **201**, header `Location: /api/v1/orders/{orderId}`, body `OrderView`:
```json
{
  "orderId": "uuid", "eventId": "uuid", "state": "PENDING_PAYMENT",
  "customerEmail": "buyer@example.com", "currency": "VND",
  "subtotal": "3000000", "discount": "0", "total": "3000000",
  "expiresAt": "2026-11-08T03:10:00Z", "paymentFrozenAt": null, "sourceConfigVersion": "3",
  "items": [
    { "orderItemId": "uuid", "ticketTypeId": "uuid", "ticketTypeName": "VIP", "quantity": 2,
      "unitPrice": "1500000", "lineSubtotal": "3000000", "sectorId": null,
      "seats": [ { "seatId": "uuid", "seatLabel": "A1", "rowName": "A", "seatNumber": 1 } ] }
  ]
}
```
- `state`: `PENDING_PAYMENT` → `ISSUING` → `COMPLETED`. Ngoài ra có `CANCELLED`, `EXPIRED` (quá `expiresAt` chưa thanh toán) và `ISSUANCE_FAILED` (phát vé lỗi, sẽ tự hoàn tiền).
- FE hiển thị đồng hồ đếm ngược tới `expiresAt`.

### 3.3 `POST /api/v1/orders/{orderId}/promotion` — áp mã giảm giá

```bash
curl -s -X POST "$BASE/api/v1/orders/<orderId>/promotion" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"code":"SALE10"}'
```
Response: `OrderView` với `discount` và `total` đã cập nhật. Phải áp mã **trước** khi bắt đầu thanh toán; sau đó sẽ gặp `409 PRICE_FROZEN`.

### 3.4 `POST /api/v1/orders/{orderId}/cancel` — huỷ đơn chưa thanh toán

```bash
curl -s -X POST "$BASE/api/v1/orders/<orderId>/cancel" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{}'
```
Body phải đúng là `{}`. Response là `OrderView` với `state = CANCELLED`.

### 3.5 `GET /api/v1/orders/{orderId}` — chi tiết đơn (`BUYER`, chủ đơn)
Trả `OrderView` như `POST /orders`. Đơn của buyer khác hoặc không tồn tại → `404 ORDER_NOT_FOUND`.

### 3.6 `GET /api/v1/orders?page=0&size=10&state=` — danh sách đơn của tôi (`BUYER`)
- `page` ≥ 0 (mặc định 0), `size` 1..100 (mặc định 10), `state` tùy chọn (một `OrderState`); mới nhất trước.
- Response `{ items: OrderView[], page, size, totalElements, totalPages }`. `items[].items[].seats` lấy từ snapshot lúc mua.
- Lỗi: `400 INVALID_REQUEST`, `401`, `403`.

### 3.7 `GET /api/v1/organizer/events/{eventId}/sales-summary?from=&to=` — thống kê bán vé (`ORGANIZER`, chủ sự kiện)
- `from`/`to` dạng `yyyy-MM-dd`, ngày giờ Việt Nam; mặc định từ ngày mở bán đến hôm nay; lọc theo thời điểm **tạo đơn**.
- Response: `eventTitle`, `from`, `to`, `timeZone`, `asOf`, `currency`, `soldOrderCount`, `soldTicketCount`, `soldSubtotal`, `soldDiscount`, `soldRevenue`, `byState[]` (đủ 6 trạng thái: `state, orderCount, ticketCount, subtotal, discount, total`), `byTicketType[]` (`ticketTypeId, ticketTypeName, soldQuantity, lineSubtotal` — chỉ đơn đã bán, trước giảm giá). Tiền là chuỗi số nguyên VND.
- "Đã bán" = `ISSUING` + `COMPLETED`. Kết quả được cache 30 giây; hiển thị `asOf`.
- Lỗi: `404 EVENT_NOT_FOUND`, `400 INVALID_DATE_RANGE`, `400 INVALID_REQUEST`, `401`, `403`.

---

## 4. Payment Service — VNPay, đối soát, hoàn tiền

> Mọi response bọc trong `{ success, message, data }`. Ví dụ dưới đây chỉ ghi phần `data`.

Lỗi riêng (`data.code`):
- **400:** `INVALID_REQUEST`, `PAYMENT_EXPIRED`, `PAYMENT_STATE_INVALID`, `PAYMENT_INVALID_SIGNATURE`. Body sai contract trả **422**.
- **401:** `AUTHENTICATION_REQUIRED`. **403:** `ACCESS_DENIED`.
- **404:** `PAYMENT_NOT_READY`, `RESOURCE_NOT_FOUND`.
- **409:** `PAYMENT_IN_PROGRESS`, `IDEMPOTENCY_CONFLICT`, `PURCHASE_SNAPSHOT_CONFLICT`, `REFUND_ALREADY_SUCCEEDED`, `REFUND_NOT_FAILED`, `REFUND_ATTEMPT_IN_FLIGHT`, `FINANCE_NOT_CONFIGURED`, `EVENT_CANCELLED`, `EVENT_NOT_ENDED`, `PAYMENT_PENDING`, `REFUND_PENDING`, `RECONCILIATION_REQUIRED`, `RECONCILIATION_STALE`, `PAYOUT_ALREADY_PAID`.
- **503:** `BOOKING_UNAVAILABLE`.
- Còn lại: `INTERNAL_ERROR`.

### 4.1 `POST /api/v1/payments` — bắt đầu thanh toán VNPay (`BUYER` + `Idempotency-Key`)

```bash
curl -s -X POST "$BASE/api/v1/payments" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"orderId":"uuid"}'
```
- Request: `orderId` (bắt buộc). `clientIp` tuỳ chọn, thường bỏ trống.
- Response `data` (`PaymentStart`):
```json
{ "orderId": "uuid", "attemptId": "uuid", "attemptState": "PENDING",
  "amount": "3000000", "currency": "VND",
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...", "expiresAt": "..." }
```
- `attemptState`: `PENDING`, `UNKNOWN`, `SUCCEEDED`, `FAILED`.
- FE redirect trình duyệt sang `paymentUrl`: `window.location.href = data.paymentUrl`.
- Nếu nhận `404 PAYMENT_NOT_READY` hoặc `409 PAYMENT_IN_PROGRESS`: đơn đang được chuẩn bị, chờ 1–2 giây rồi gọi lại **cùng `Idempotency-Key`**.

### 4.2 Luồng VNPay trả về (FE không tự gọi)
- `GET /api/v1/payments/vnpay/return`: VNPay redirect người dùng về đây, sau đó backend **302** về trang kết quả của FE (URL cấu hình ở payment-service). **Không dùng tham số trên URL này để kết luận thanh toán thành công.**
- `GET /api/v1/payments/vnpay/ipn`: VNPay gọi server-to-server. Đây mới là nguồn trạng thái cuối.
- Ở trang kết quả, FE poll mục 4.3 cho tới khi `latestAttempt.attemptState` thành `SUCCEEDED`/`FAILED`, rồi poll mục 5.1 để lấy vé.

### 4.3 `GET /api/v1/orders/{orderId}/payment?requestId=` — trạng thái thanh toán của đơn (`BUYER`)

```bash
curl -s "$BASE/api/v1/orders/<orderId>/payment" -H "Authorization: Bearer $TOKEN"
```
```json
{
  "orderId": "uuid",
  "acceptedChargeId": "uuid | null",
  "latestAttempt": { "orderId": "...", "attemptId": "...", "attemptState": "SUCCEEDED", "amount": "3000000",
                     "currency": "VND", "paymentUrl": "...", "expiresAt": "..." },
  "startRequest": { "requestId": "uuid", "state": "CREATED", "failureCode": null },
  "refunds": [ { "refundId": "uuid", "chargeId": "uuid", "amount": "3000000", "state": "PENDING", "reason": "EVENT_CANCELLED" } ]
}
```
- `acceptedChargeId` khác null nghĩa là thanh toán đã được ghi nhận.
- `startRequest.state`: `WAITING`, `CREATED`, `REJECTED` (khi `REJECTED` thì xem `failureCode`).
- `refunds[].state`: `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`.
- `refunds[].reason`: `LATE_PAYMENT`, `DUPLICATE_PAYMENT`, `TICKET_ISSUANCE_FAILED`, `EVENT_CANCELLED`.

### 4.4 Đối soát và chi trả cho organizer

| Method | Path | Role | Body | Response `data` |
|---|---|---|---|---|
| `GET` | `/api/v1/events/{eventId}/reconciliation` | `ADMIN` hoặc `ORGANIZER` (chủ sự kiện) | — | `EventReconciliation` |
| `POST` | `/api/v1/admin/events/{eventId}/reconciliation` | `ADMIN` | `{ "evidence": "Đã đối chiếu sao kê ngày..." }` (bắt buộc, ≤1000) | `EventReconciliation` |
| `POST` | `/api/v1/admin/events/{eventId}/payout` | `ADMIN` | không có | `EventReconciliation` |

`EventReconciliation`:
```json
{
  "eventId": "uuid", "organizerSubject": "sub", "currency": "VND",
  "validGross": "18000000", "validRefunds": "0", "feeAmount": "900000", "netAmount": "17100000",
  "feeRate": "0.05", "sourceFeeVersion": "1", "eventEndsAt": "...", "cancelled": false,
  "paymentPendingCount": 0, "refundPendingCount": 0, "eligibleForReconciliation": true,
  "reconciledBy": null, "reconciledAt": null, "reconciliationEvidence": null,
  "paidBy": null, "paidAt": null, "asOf": "..."
}
```
Thứ tự: sự kiện kết thúc → `eligibleForReconciliation = true` → admin đối soát → admin payout. Làm sai thứ tự sẽ gặp `EVENT_NOT_ENDED`, `PAYMENT_PENDING`, `REFUND_PENDING`, `RECONCILIATION_REQUIRED`, `RECONCILIATION_STALE` hoặc `PAYOUT_ALREADY_PAID`.

### 4.5 Quản lý hoàn tiền (`ADMIN`)

| Method | Path | Body | Response `data` |
|---|---|---|---|
| `GET` | `/api/v1/admin/refunds?state=FAILED&page=0&size=20` | — | `PageResponse<RefundAdmin>` |
| `POST` | `/api/v1/admin/refunds/{refundId}/retry` | — | `RefundAdmin` (chỉ áp dụng cho refund `FAILED`) |
| `POST` | `/api/v1/admin/refunds/{refundId}/manual-settlement` | `{ "externalReference": "Mã CK ngân hàng" }` (bắt buộc, ≤255) | `RefundAdmin` |
| `GET` | `/api/v1/admin/events/{eventId}/refund-progress` | — | `RefundProgress` |

`PageResponse<RefundAdmin>`:
```json
{
  "items": [ {
    "refundId": "uuid", "orderId": "uuid", "chargeId": "uuid", "amount": "3000000", "currency": "VND",
    "reason": "EVENT_CANCELLED", "state": "FAILED", "createdAt": "...", "completedAt": null,
    "settledBy": null, "externalReference": null,
    "lastAttemptStartedAt": "...", "lastAttemptFinishedAt": "...", "lastAttemptOutcome": "..."
  } ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

`RefundProgress` (tiến độ hoàn tiền khi huỷ sự kiện):
```json
{ "eventId": "uuid", "cancellationId": "uuid", "selectionComplete": true,
  "selectedOrderCount": 120, "pendingCount": 3, "refundedCount": 115, "needsAttentionCount": 2,
  "completedAt": null, "asOf": "..." }
```

---

## 5. Ticket Service — vé, QR, gửi mail, check-in

Lỗi: `INVALID_REQUEST` (400), `UNAUTHENTICATED` (401), cùng các lỗi chung của gateway.

### 5.1 `GET /api/v1/orders/{orderId}/tickets` — vé của đơn (`BUYER`)

```bash
curl -s "$BASE/api/v1/orders/<orderId>/tickets" -H "Authorization: Bearer $TOKEN"
```
```json
{
  "issuanceState": "COMPLETED",
  "tickets": [
    { "ticketId": "uuid", "eventId": "uuid", "orderItemId": "uuid", "ordinal": 1,
      "ticketTypeName": "VIP", "seatLabel": "A1", "state": "VALID" }
  ]
}
```
- `issuanceState`: `PENDING` (đang phát, tiếp tục poll), `COMPLETED`, `FAILED`.
- Ticket `state`: `VALID`, `USED` (đã check-in), `VOID` (đã huỷ hoặc hoàn tiền).

### 5.2 `GET /api/v1/tickets/{ticketId}/qr` — mã QR (`BUYER`, no-store)

```bash
# Dạng JSON (FE tự render QR)
curl -s "$BASE/api/v1/tickets/<ticketId>/qr" -H "Authorization: Bearer $TOKEN" -H "Accept: application/json"
# → { "qrToken": "..." }

# Dạng ảnh PNG
curl -s "$BASE/api/v1/tickets/<ticketId>/qr" -H "Authorization: Bearer $TOKEN" -H "Accept: image/png" -o qr.png
```
Thẻ `<img>` không gửi được header Authorization. Để hiển thị PNG, FE dùng `fetch` → `blob` → `URL.createObjectURL`, hoặc lấy `qrToken` rồi render QR phía client. Không cache.

### 5.3 `POST /api/v1/orders/{orderId}/ticket-deliveries` — gửi lại vé qua email (`BUYER` + `Idempotency-Key`)

```bash
curl -s -X POST "$BASE/api/v1/orders/<orderId>/ticket-deliveries" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" -d '{}'
```
Body phải đúng là `{}`. Response **202**: `{ "deliveryId": "uuid", "state": "PENDING", ... }`

### 5.4 `POST /api/v1/events/{eventId}/check-ins` — quét QR tại cổng (`ORGANIZER` + `Idempotency-Key`)

```bash
curl -s -X POST "$BASE/api/v1/events/<eventId>/check-ins" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Idempotency-Key: $(uuidgen)" \
  -d '{"qrToken":"<chuỗi quét được>"}'
```
- Body chỉ có `qrToken` (≤4096 ký tự).
- Response:
```json
{ "attemptId": "uuid", "eventId": "uuid", "ticketId": "uuid | null",
  "result": "SUCCESS", "succeeded": true, "processedAt": "..." }
```
- `result`: `SUCCESS`, `ALREADY_USED`, `VOID`, `WRONG_EVENT`, `UNKNOWN_QR`, `NOT_ISSUED`, `OUTSIDE_WINDOW`, `EVENT_CANCELLED`, `NOT_EVENT_OWNER`. Mỗi giá trị nên có một thông báo riêng trên màn hình scanner.

---

## 6. Luồng tích hợp chính

### 6.1 Người mua (BUYER)
1. `GET /events` → `GET /events/{id}` → poll `GET /events/{id}/availability`.
2. Đăng nhập Keycloak (PKCE) → `GET /me`.
3. `POST /orders` (Idempotency-Key A) → lưu `OrderView`, đếm ngược `expiresAt`.
4. (Tuỳ chọn) `POST /orders/{id}/promotion`.
5. `POST /payments` (Idempotency-Key B) → redirect `paymentUrl`.
6. VNPay → `/vnpay/return` → 302 về trang kết quả của FE.
7. Poll `GET /orders/{id}/payment` cho tới khi thanh toán kết thúc.
8. Poll `GET /orders/{id}/tickets` cho tới khi `issuanceState = COMPLETED` → `GET /tickets/{id}/qr`.
9. (Tuỳ chọn) `POST /orders/{id}/ticket-deliveries`.

### 6.2 Organizer
`POST /organizer-applications` → admin duyệt → refresh token → `POST /organizer/events` → upload ảnh, sơ đồ ghế, loại vé → `submit` → admin `approve` → `publish` → xem `statistics` → check-in tại cổng → xem `reconciliation`.

### 6.3 Admin
Duyệt organizer → duyệt sự kiện (`feeRate`) → xử lý yêu cầu huỷ → theo dõi `refund-progress` và refund `FAILED` (`retry` / `manual-settlement`) → đối soát → payout.

---

## 7. Hạn chế hiện tại cần lưu ý

| # | Vấn đề | Ảnh hưởng FE |
|---|---|---|
| 1 | ~~`GET /api/v1/orders/{id}` là stub, chưa có API danh sách đơn~~ — đã có chi tiết đơn (3.5) và danh sách đơn phân trang (3.6) | Dùng 3.5/3.6 thay cho cache phía FE |
| 2 | `frontend/src/lib/internalServiceClients.ts` mặc định `http://localhost:8080` | Phải sửa thành biến gateway `VITE_API_GATEWAY_URL` |
| 3 | Ticket check-in và QR phụ thuộc `TICKET_BUSINESS_ENABLED=true` | Đang bật trên server 84 |

## Thống kê bán vé dùng chung — 2026-11-10

Bảy endpoint GET do Booking xử lý, gọi riêng phần giao diện cần. Response trả trực tiếp JSON, không bọc thêm `data`.

| Path (sau /api/v1) | Quyền / phạm vi | Response |
|---|---|---|
| /admin/sales/summary | ADMIN, mọi organizer | SalesStatisticsSummaryView |
| /admin/sales/chart | ADMIN, mọi organizer | SalesStatisticsChartView |
| /admin/events/sales-statistics | ADMIN, mọi sự kiện đủ điều kiện | EventSalesStatisticsPage |
| /organizers/{organizerSubject}/sales/summary | ADMIN hoặc ORGANIZER có subject JWT trùng path | SalesStatisticsSummaryView |
| /organizers/{organizerSubject}/sales/chart | ADMIN hoặc ORGANIZER có subject JWT trùng path | SalesStatisticsChartView |
| /organizers/{organizerSubject}/events/sales-statistics | ADMIN hoặc đúng ORGANIZER | EventSalesStatisticsPage |
| /events/{eventId}/sales-statistics | ADMIN hoặc ORGANIZER sở hữu event | SalesSummaryView hiện có |

- Chung: `from?`, `to?` ISO `yyyy-MM-dd`, bao gồm cả hai ngày, mặc định đầu tháng hiện tại / hôm nay tại `Asia/Ho_Chi_Minh`. Lọc **thời điểm tạo đơn**, không phải ngày thanh toán hoặc ngày tổ chức.
- Chart: thêm `groupBy=DAY|WEEK|MONTH`, mặc định DAY. Tuần bắt đầu thứ Hai; tháng bắt đầu ngày 1; trả cả kỳ 0. `periodStart/periodEnd` là kỳ lịch đầy đủ, số liệu chỉ tính phần giao với from/to. Không query riêng từng kỳ.
- Bảng: `page=0`, `size=10` (1..100), `sortBy=soldRevenue|soldOrderCount|soldTicketCount`, `direction=DESC|ASC` (mặc định DESC). Xếp hạng toàn tập trước phân trang; hòa thì eventId ASC. Có dòng 0 và sự kiện CANCELLED chưa từng công bố.
- Phạm vi: snapshot Booking có status hiện tại PUBLISHED hoặc CANCELLED; không lưu lịch sử công bố mới. Đã bán = ISSUING + COMPLETED. Các trạng thái là trạng thái hiện tại của đơn trong khoảng tạo đơn, không phải sổ doanh thu lịch sử.
- Tiền: chuỗi số nguyên VND. `soldRevenue` sau giảm giá; `soldSubtotal` trước giảm; `soldDiscount` tiền giảm.
- Metadata summary/chart/page: `from,to,organizerSubject,timeZone,asOf,currency`; subject null khi toàn hệ thống. Tên organizer do frontend lấy từ User, không trả kèm ở Booking.
- Summary: `soldOrderCount,soldTicketCount,soldSubtotal,soldDiscount,soldRevenue,byState[]`; mỗi state có `state,orderCount,ticketCount,subtotal,discount,total`, luôn đủ sáu state.
- Chart: `groupBy,items[]`; mỗi item có `periodStart,periodEnd,soldOrderCount,soldTicketCount,soldRevenue`.
- Bảng: `items,page,size,totalElements,totalPages`; mỗi item có `eventId,eventTitle,organizerSubject,eventStatus,soldOrderCount,soldTicketCount,soldRevenue`.
- Detail: dùng SalesSummaryView của API organizer cũ, có `byTicketType[]`; API mới mặc định đầu tháng. API `/organizer/events/{eventId}/sales-summary` cũ vẫn mặc định ngày mở bán.
- Lỗi: 400 INVALID_DATE_RANGE / INVALID_REQUEST hoặc sai kiểu tham số; 401 thiếu JWT; 403 sai role/organizer path; 404 EVENT_NOT_FOUND khi event không đủ điều kiện hoặc không thuộc owner. Role/owner lấy từ JWT và DB, không nhận từ query.
- Cache riêng TTL 30 giây qua `BOOKING_SALES_STATISTICS_CACHE_TTL` và `BOOKING_SALES_STATISTICS_CACHE_KEY_PREFIX`; quyền được kiểm trước cả hit, event hit vẫn query owner. `asOf` giữ nguyên trên hit; Redis lỗi fallback DB; không cache lỗi nghiệp vụ. TTL <= 0 tắt cache.
- Cache chỉ theo TTL, chưa chống miss đồng thời/invalidation; load chậm có thể ghi sau load mới hơn. Key ngày mặc định có thể giữ response ngày trước qua nửa đêm tới hết TTL. Không coi độ cũ dữ liệu luôn <=30 giây.
- DB: mỗi báo cáo nhiều query dùng transaction read-only REPEATABLE_READ; không khóa hàng để báo cáo. Query tiền tách query item để tránh nhân tổng.
- Ngoại lệ E1 đã duyệt: chart dùng Spring Data `@Query` không native, gọi PostgreSQL `timezone/to_char` và GROUP BY biểu thức qua Hibernate. Không portable sang DB khác nếu giữ nguyên query.
- Index I1: `orders_created_at_idx(created_at)` trong entity và DDL chuẩn. Server84 đã tạo CONCURRENTLY, valid/ready; bảng thực tế nhỏ vẫn có thể được planner chọn seq scan. Không thêm cột.

Ví dụ chart `GET /api/v1/organizers/owner/sales/chart?from=2026-11-01&to=2026-11-02&groupBy=DAY`:

```json
{
  "from": "2026-11-01", "to": "2026-11-02", "organizerSubject": "owner",
  "timeZone": "Asia/Ho_Chi_Minh", "asOf": "2026-11-10T15:00:00Z",
  "currency": "VND", "groupBy": "DAY",
  "items": [
    {"periodStart": "2026-11-01", "periodEnd": "2026-11-01", "soldOrderCount": 2, "soldTicketCount": 3, "soldRevenue": "500000"},
    {"periodStart": "2026-11-02", "periodEnd": "2026-11-02", "soldOrderCount": 0, "soldTicketCount": 0, "soldRevenue": "0"}
  ]
}
```

## Cache danh sách đơn buyer — 2026-11-10

`GET /api/v1/orders?state=&page=0&size=10` dùng Redis cache-aside theo subject JWT + state/page/size. Giữ nguyên OrderPage/OrderView và phân trang; chỉ cache trang được yêu cầu. BUYER authentication/authorization vẫn chạy trước lookup.

- Data TTL mặc định **24 giờ** (`BOOKING_BUYER_ORDERS_CACHE_TTL=PT24H`); TTL <=0 bypass cache. Prefix `BOOKING_BUYER_ORDERS_CACHE_KEY_PREFIX` mặc định `flash-ticket:dev:booking:buyer-orders:v1:`.
- Subject được encode Base64 URL UTF8, không dùng token/email. Ví dụ buyer-123 → YnV5ZXItMTIz.
- Metadata key: `{prefix}{encodedBuyer}:generation` → UUID hiện tại, không TTL.
- Data key: `{prefix}{encodedBuyer}:data:{generation}:state:ALL:page:0:size:10` → JSON OrderPage, TTL86400s. Lọc state dùng đúng enum thay ALL.
- Sau khi tạo đơn mới hoặc đơn thay đổi, đổi generation **sau commit DB** để mọi trang/bộ lọc của buyer hết hiệu lực. Các key payload cũ tự hết TTL; không quét Redis, không xóa cache buyer khác.
- Invalidate khi tạo mới, buyer hủy, worker hủy do event, hết hạn, nhận tiền/chuyển ISSUING hoặc thanh toán muộn chuyển EXPIRED/CANCELLED, phát vé COMPLETED/ISSUANCE_FAILED, áp voucher và freeze thanh toán. Replay/no-op không đổi dữ liệu không invalidate.
- GET miss gọi OrderService proxy, giữ transaction REPEATABLE_READ hiện tại, kết thúc DB transaction trước Redis SET. Reader giữ generation lấy trước khi đọc DB nên không ghi vào namespace mới sau invalidation.
- Redis đọc/xóa lỗi fallback DB và bỏ ghi cache; JSON hỏng xóa best effort; Redis ghi/invalidate lỗi được log, không giả rollback DB đã commit. Không cache lỗi DB/nghiệp vụ; cache trang rỗng hợp lệ.
- Giới hạn: chưa durable invalidation hoặc singleflight; nếu Redis lỗi hoặc process dừng sau DB commit trước listener, cache cũ có thể còn gần24giờ. Reader đang chạy có thể trả snapshot cũ cho chính request đó. TTL tính từ SET, không cam kết độ cũ dữ liệu luôn <=24giờ. Metadata generation giữ một key nhỏ/buyer; payload cũ được dọn bằng TTL.
- Không thêm cột/index/query trong task cache này. Các field eventTitle/eventStartsAt/createdAt và batch snapshot đã thảo luận chưa được thêm vào OrderView trong thay đổi cache.
