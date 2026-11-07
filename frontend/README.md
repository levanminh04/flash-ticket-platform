# FlashTicket Frontend

Frontend React + TypeScript + Vite được tái sử dụng cho FlashTicket.

## Tech Stack

- **React 18** - UI library
- **TypeScript** - Type safety
- **Vite** - Build tool and dev server
- **TailwindCSS** - Utility-first CSS framework
- **React Router** - Client-side routing
- **React Query** - Server state management
- **Axios** - HTTP client
- **Keycloak-js** - Authentication

## Getting Started

### Install Dependencies

```bash
npm install
```

### Run Development Server

```bash
npm run dev
```

The app will be available at `http://localhost:5173`

### Build for Production

```bash
npm run build
```

### Preview Production Build

```bash
npm run preview
```

## Project Structure

```
src/
├── api/              # API clients and services
├── components/       # Reusable components
│   ├── Layout/
│   ├── Event/
│   ├── Chat/
│   └── common/
├── pages/            # Page components
│   ├── Home/
│   ├── Events/
│   ├── EventDetail/
│   ├── Checkout/
│   ├── MyTickets/
│   ├── Organizer/
│   └── Auth/
├── hooks/            # Custom React hooks
├── context/          # React Context providers
├── utils/            # Utility functions
├── types/            # TypeScript type definitions
├── App.tsx           # Main app component
└── main.tsx          # Entry point
```

## Environment Variables

Create a `.env` file at the repository root (not inside `frontend/`):

```env
VITE_API_GATEWAY_URL=http://127.0.0.1:18081
VITE_KEYCLOAK_URL=http://3.106.109.119:8080
VITE_KEYCLOAK_REALM=flash-ticket
VITE_KEYCLOAK_CLIENT_ID=frontend
```

`frontend/vite.config.ts` reads env values from the repo root, so the same `.env` file can be shared with backend services and `docker-compose`.

Dev gọi `/api` cùng origin, Vite proxy tới Gateway. Keycloak adapter gọi trực tiếp URL Keycloak; không rewrite `fetch`/XHR. Client public phải cho phép callback và web origin `http://localhost:5173` / `http://127.0.0.1:5173`; PKCE S256, không có client secret trong FE.

Trên máy này Gateway chạy port 8081 trong Docker host network, trong khi Windows port 8081 bị ứng dụng khác chiếm. Relay local hiện có thể được chạy bằng:

```powershell
docker compose --env-file .env -f deploy/docker-compose.frontend.local.yml up -d
```

Lệnh chạy từ root repo, cần image local `flash-ticket/db-tunnel-proxy:local` đã có từ cấu hình tunnel. Relay chỉ publish `127.0.0.1:18081`, trỏ tới Gateway trong Docker; các port service giữ nguyên. Nếu môi trường khác truy cập Gateway trực tiếp được, điền URL đúng vào root `.env` thay vì dùng relay này.

Trạng thái tích hợp và các phụ thuộc backend: [docs/integration.md](docs/integration.md). Bốn task UI hoàn tất không đồng nghĩa luồng đặt vé/thanh toán đã nghiệm thu.
