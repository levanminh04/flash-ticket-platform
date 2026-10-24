# Booking Service Runtime Scaffold Implementation Plan

> **For agentic workers:** Use `superpowers:executing-plans` to implement this plan in the current worktree.

**Goal:** Add the smallest useful Security, RabbitMQ, Redis, and Spring Data repository scaffold to Booking Service without creating behaviorless application layers.

**Architecture:** Keep `BookingServiceApplication` as the scan root, place repositories with their owning feature, and rely on Spring Boot auto-configuration wherever it provides the runtime integration. Add custom beans only for JWT role mapping, HTTP authorization, and RabbitMQ JSON conversion.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Security OAuth2 Resource Server, Spring AMQP 4.1.1, Spring Data JPA, Spring Data Redis/Lettuce, PostgreSQL, Keycloak, RabbitMQ.

**Spec:** `docs/superpowers/specs/2026-10-23-booking-service-scaffold-design.md`

## Constraints

- Change only `booking-service` and `infrastructure/config-server/src/main/resources/config/booking-service.yaml`.
- Do not create or run tests for this task.
- Do not add controllers, application services, message consumers/producers, cache abstractions, schedulers, business methods, or fake behavior.
- Do not define RabbitMQ `Queue`, `Exchange`, `Binding`, or `RabbitAdmin` beans.
- Use Boot Redis auto-configuration; do not add a custom Redis config or serializer.
- Add no repository queries, locks, entity graphs, or repositories for `OrderItem`, `OrderItemSeat`, or `ReservationAllocation`.
- Keep secrets as environment placeholders and retain Maven Enforcer's Java range `[21,22)`.

### Task 1: Add runtime configuration

**Files:**

- Modify: `services/booking-service/pom.xml`
- Modify: `infrastructure/config-server/src/main/resources/config/booking-service.yaml`
- Create: `services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java`
- Create: `services/booking-service/src/main/java/com/flashticket/booking_service/security/KeycloakRealmRoleConverter.java`
- Create: `services/booking-service/src/main/java/com/flashticket/booking_service/config/RabbitMqConfig.java`

- [ ] **Step 1: Enable Redis through Boot auto-configuration**

Add `org.springframework.boot:spring-boot-starter-data-redis` without a version or Jedis dependency. Under the existing `spring` block in Config Server YAML, add:

```yaml
data:
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
    connect-timeout: 2s
    timeout: 2s
```

- [ ] **Step 2: Configure JWT security**

Create a package-private final `KeycloakRealmRoleConverter` implementing `Converter<Jwt, Collection<GrantedAuthority>>`. Read `realm_access.roles` defensively, accept only strings, whitelist `BUYER`, `ORGANIZER`, and `ADMIN`, remove duplicates, prefix accepted roles with `ROLE_`, and return an empty collection for missing or malformed claims.

Create `SecurityConfig` with `@Configuration(proxyBeanMethods = false)` and `@EnableWebSecurity`. Provide these package-private beans:

```java
SecurityFilterChain securityFilterChain(
    HttpSecurity http, JwtAuthenticationConverter keycloakJwtAuthenticationConverter)
    throws Exception

JwtAuthenticationConverter keycloakJwtAuthenticationConverter(
    KeycloakRealmRoleConverter keycloakRealmRoleConverter)

KeycloakRealmRoleConverter keycloakRealmRoleConverter()
```

Disable CSRF, use `SessionCreationPolicy.STATELESS`, enable JWT resource-server support, and order authorization rules as follows:

1. permit `GET /api/v1/events/*/availability`;
2. permit `/actuator/health` and `/actuator/health/**`;
3. require `ROLE_BUYER` for `/api/v1/orders/**`;
4. deny `/actuator/**` and `/internal/v1/**`;
5. authenticate every other request.

Do not add CORS configuration because the API Gateway owns browser CORS.

- [ ] **Step 3: Configure RabbitMQ JSON conversion**

Create `RabbitMqConfig` with `@Configuration(proxyBeanMethods = false)` and one package-private bean:

```java
MessageConverter rabbitMessageConverter(JsonMapper jsonMapper)
```

Return `new JacksonJsonMessageConverter(jsonMapper)`. Reuse the Boot-managed Jackson 3 mapper; do not create a mapper, `RabbitTemplate`, listener factory, topology object, or admin bean.

- [ ] **Step 4: Format, compile, and commit**

From `services/booking-service`, using Java 21:

```powershell
rtk .\mvnw.cmd -DskipTests spotless:apply compile
```

Then commit the runtime configuration:

```powershell
rtk git add services/booking-service/pom.xml infrastructure/config-server/src/main/resources/config/booking-service.yaml services/booking-service/src/main/java/com/flashticket/booking_service/config services/booking-service/src/main/java/com/flashticket/booking_service/security
rtk git commit -m "feat(booking): configure runtime integrations"
```

### Task 2: Add feature-owned Spring Data repositories

Create these interfaces with only the stated `JpaRepository` extension. Do not add `@Repository` or custom methods.

| Package | Repository | Entity | ID |
|---|---|---|---|
| `order.repository` | `OrderRepository` | `Order` | `UUID` |
| `order.repository` | `IdempotencyRequestRepository` | `IdempotencyRequest` | `IdempotencyRequestId` |
| `availability.repository` | `EventSalesSnapshotRepository` | `EventSalesSnapshot` | `UUID` |
| `availability.repository` | `TicketTypeInventoryRepository` | `TicketTypeInventory` | `UUID` |
| `availability.repository` | `SectorInventoryRepository` | `SectorInventory` | `UUID` |
| `availability.repository` | `SeatInventoryRepository` | `SeatInventory` | `UUID` |
| `availability.repository` | `PurchaseLimitRepository` | `PurchaseLimit` | `PurchaseLimitId` |
| `reservation.repository` | `ReservationRepository` | `Reservation` | `UUID` |
| `promotion.repository` | `PromotionInventoryRepository` | `PromotionInventory` | `UUID` |
| `promotion.repository` | `PromotionUsageRepository` | `PromotionUsage` | `UUID` |
| `messaging.repository` | `InboxMessageRepository` | `InboxMessage` | `InboxMessageId` |
| `messaging.repository` | `OutboxMessageRepository` | `OutboxMessage` | `UUID` |

- [ ] **Step 1: Create all twelve repository interfaces**

Place each interface in its feature's `repository` package and import the existing entity and ID type.

- [ ] **Step 2: Format, compile, and commit**

From `services/booking-service`, using Java 21:

```powershell
rtk .\mvnw.cmd -DskipTests spotless:apply compile
```

Then commit the repositories:

```powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/order services/booking-service/src/main/java/com/flashticket/booking_service/availability services/booking-service/src/main/java/com/flashticket/booking_service/reservation services/booking-service/src/main/java/com/flashticket/booking_service/promotion services/booking-service/src/main/java/com/flashticket/booking_service/messaging
rtk git commit -m "feat(booking): add persistence repositories"
```

### Task 3: Verify final scope and build

- [ ] **Step 1: Check the runtime and scope**

```powershell
rtk java -version
rtk rg -n "class RedisConfig|JdkSerializationRedisSerializer|GenericJackson.*RedisSerializer|new Queue|DirectExchange|TopicExchange|FanoutExchange|BindingBuilder|RabbitAdmin|Declarables|@RestController|@RabbitListener|interface .*Service|class Default.*Service" services/booking-service/src/main/java
rtk rg -n "OrderItemRepository|OrderItemSeatRepository|ReservationAllocationRepository|@Query|@Lock|@EntityGraph" services/booking-service/src/main/java
rtk rg -n "interface .*Repository extends JpaRepository" services/booking-service/src/main/java/com/flashticket/booking_service
```

Expected: Java `21.x`; the first two scans return no matches; the last scan shows exactly twelve repositories.

- [ ] **Step 2: Run the production build without tests**

From `services/booking-service`:

```powershell
rtk .\mvnw.cmd -DskipTests clean verify
```

Expected: `BUILD SUCCESS` with no tests executed.

- [ ] **Step 3: Review the complete implementation diff**

From the repository root:

```powershell
rtk git status --short
rtk git diff f102905..HEAD --check
rtk git diff f102905..HEAD --stat
```

Expected: no whitespace errors and no changes outside the approved Booking/config scope.
