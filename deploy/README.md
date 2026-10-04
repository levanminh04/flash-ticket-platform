# Chạy PostgreSQL và Keycloak

Mở terminal trong folder `deploy`, dùng file `.env.dev` đã cấu hình.

Chạy PostgreSQL trước, đợi container healthy rồi chạy Keycloak:

```powershell
docker compose --env-file .env.dev -f docker-compose.postgresql.yml up -d --build
docker compose --env-file .env.dev -f docker-compose.keycloak.yml up -d --build
```

Admin Console: http://localhost:8080/admin/flash-ticket/console/
