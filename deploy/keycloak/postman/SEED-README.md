# Tài khoản Keycloak — server 119

Ba tài khoản ứng dụng đang bật và đã xác minh email.

| Realm | Username / Email | Password | Role | URL đăng nhập |
|---|---|---|---|---|
| flash-ticket | admin@gmail.com | 123456789a | ADMIN, BUYER | [Login](http://3.106.109.119:8080/admin/flash-ticket/console/) |
| flash-ticket | buyer@gmail.com | 123456789a | BUYER | [Login](http://3.106.109.119:8080/realms/flash-ticket/account/) |
| flash-ticket | organizer@gmail.com | 123456789a | ORGANIZER, BUYER | [Login](http://3.106.109.119:8080/realms/flash-ticket/account/) |
| master | admin@gmail.com | `KC_BOOTSTRAP_ADMIN_PASSWORD` trong `.env` | Quản trị Keycloak (`admin`) | [Login](http://3.106.109.119:8080/admin/master/console/) |

Lưu ý: URL HTTP công khai hiện báo `HTTPS required`.
