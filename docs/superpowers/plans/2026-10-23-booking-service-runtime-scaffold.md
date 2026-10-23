# Booking Service Runtime Scaffold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the smallest useful Security, RabbitMQ, Redis, and Spring Data repository scaffold to Booking Service without creating behaviorless application layers.

**Architecture:** Keep `BookingServiceApplication` as the scan root, place repositories with their owning feature, and rely on Spring Boot auto-configuration wherever it already provides the runtime integration. Security and RabbitMQ receive only the custom beans FlashTicket actually needs; Redis receives dependency and external configuration only.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Security OAuth2 Resource Server, Spring AMQP 4.1.1, Spring Data JPA, Spring Data Redis/Lettuce, PostgreSQL, Keycloak, RabbitMQ.

**Spec:** `docs/superpowers/specs/2026-10-23-booking-service-scaffold-design.md`

## Global Constraints

- Work only in `booking-service` and `infrastructure/config-server/src/main/resources/config/booking-service.yaml`.
- Do not add controllers, application services, message consumers/producers, cache abstractions, schedulers, business methods, or fake behavior.
- Do not create or run tests for this task, per explicit user request.
- Do not weaken the Maven Enforcer Java range `[21,22)`; final compile evidence requires Java 21.
- Do not define RabbitMQ `Queue`, `Exchange`, `Binding`, or `RabbitAdmin` beans.
- Do not introduce Java native Redis serialization or custom Redis objects before a concrete cache contract exists.
- Do not add repository queries, locks, entity graphs, or repositories for `OrderItem`, `OrderItemSeat`, or `ReservationAllocation`.
- Keep secrets as environment placeholders in Config Server YAML.
- Format with the repository's existing Spotless/Google Java Format configuration.

## Review Focus

Because tests are explicitly out of scope, each item is pinned by compile or static inspection in the owning task.

- Missing/malformed `realm_access.roles` must produce no authorities rather than an exception — inspect the converter implementation in Task 2.
- Keycloak roles outside `BUYER`, `ORGANIZER`, and `ADMIN` must be ignored — inspect the whitelist in Task 2.
- Health checks must remain reachable while all other actuator paths stay protected — inspect matcher ordering in Task 2.
- Application code must not attempt to create broker topology — run the no-topology `rg` gate in Task 3.
- Redis must use external properties and Boot auto-configuration without a custom serializer/config class — run the no-custom-Redis `rg` gate in Task 1.

---

## File Map

**Modify**

- `services/booking-service/pom.xml` — add the Redis starter.
- `infrastructure/config-server/src/main/resources/config/booking-service.yaml` — add Redis connection properties.

**Create**

- `services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java` — servlet resource-server policy and converter wiring.
- `services/booking-service/src/main/java/com/flashticket/booking_service/security/KeycloakRealmRoleConverter.java` — Keycloak realm-role whitelist.
- `services/booking-service/src/main/java/com/flashticket/booking_service/config/RabbitMqConfig.java` — Jackson 3 AMQP message converter.
- `services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/OrderRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/order/repository/IdempotencyRequestRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/availability/repository/EventSalesSnapshotRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/availability/repository/TicketTypeInventoryRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/availability/repository/SectorInventoryRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/availability/repository/SeatInventoryRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/availability/repository/PurchaseLimitRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/reservation/repository/ReservationRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/promotion/repository/PromotionInventoryRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/promotion/repository/PromotionUsageRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/messaging/repository/InboxMessageRepository.java`
- `services/booking-service/src/main/java/com/flashticket/booking_service/messaging/repository/OutboxMessageRepository.java`

### Task 1: Enable Redis through Boot auto-configuration

**Files:**

- Modify: `services/booking-service/pom.xml`
- Modify: `infrastructure/config-server/src/main/resources/config/booking-service.yaml`

**Interfaces:**

- Consumes: Spring Boot dependency management and existing Config Server import.
- Produces: auto-configured `RedisConnectionFactory` and `StringRedisTemplate`; properties under `spring.data.redis`.

- [ ] **Step 1: Add the Redis starter**

Add `org.springframework.boot:spring-boot-starter-data-redis` next to the other production starters in `pom.xml`; do not specify a version and do not add Jedis.

- [ ] **Step 2: Add external Redis properties**

Under the existing `spring` block in `booking-service.yaml`, add:

```yaml
data:
  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD}
    connect-timeout: 2s
    timeout: 2s
```

- [ ] **Step 3: Run static scope checks**

Run:

```powershell
rtk rg -n "spring-boot-starter-data-redis|data:|redis:|REDIS_" services/booking-service/pom.xml infrastructure/config-server/src/main/resources/config/booking-service.yaml
rtk rg -n "class RedisConfig|JdkSerializationRedisSerializer|GenericJackson.*RedisSerializer" services/booking-service/src/main/java
```

Expected: the first command shows the starter and five Redis properties; the second command returns no matches.

- [ ] **Step 4: Run formatting/XML validation gate**

Run from `services/booking-service` with Java 21:

```powershell
rtk .\mvnw.cmd -DskipTests spotless:check validate
```

Expected: `BUILD SUCCESS`. If `java -version` is not 21, stop and report the environment blocker instead of skipping Enforcer.

- [ ] **Step 5: Commit**

```powershell
rtk git add services/booking-service/pom.xml infrastructure/config-server/src/main/resources/config/booking-service.yaml
rtk git commit -m "build(booking): enable Redis client"
```

### Task 2: Configure servlet JWT security for Booking

**Files:**

- Create: `services/booking-service/src/main/java/com/flashticket/booking_service/security/KeycloakRealmRoleConverter.java`
- Create: `services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java`

**Interfaces:**

- Consumes: `spring.security.oauth2.resourceserver.jwt.*` from Config Server and `Jwt` claim `realm_access.roles`.
- Produces: `Collection<GrantedAuthority> KeycloakRealmRoleConverter.convert(Jwt jwt)`, `SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter)`, and a servlet `JwtAuthenticationConverter` bean.

- [ ] **Step 1: Add the realm-role converter**

Create a package-private final class implementing `Converter<Jwt, Collection<GrantedAuthority>>`. Copy the proven parsing shape from API Gateway: accept only string roles in `realm_access.roles`, whitelist `BUYER`, `ORGANIZER`, `ADMIN`, de-duplicate them, prefix with `ROLE_`, and return an empty list for missing or malformed claims.

- [ ] **Step 2: Add `SecurityConfig`**

Create `@Configuration(proxyBeanMethods = false)` and `@EnableWebSecurity` with these bean signatures:

```java
SecurityFilterChain securityFilterChain(
    HttpSecurity http, JwtAuthenticationConverter keycloakJwtAuthenticationConverter)
    throws Exception

JwtAuthenticationConverter keycloakJwtAuthenticationConverter(
    KeycloakRealmRoleConverter keycloakRealmRoleConverter)

KeycloakRealmRoleConverter keycloakRealmRoleConverter()
```

Configure CSRF disabled, `SessionCreationPolicy.STATELESS`, JWT resource-server support, and matchers in this order:

1. permit `GET /api/v1/events/*/availability`;
2. permit `/actuator/health` and `/actuator/health/**`;
3. require `ROLE_BUYER` for `/api/v1/orders/**`;
4. deny `/actuator/**` and `/internal/v1/**`;
5. authenticate every other request.

Do not add CORS configuration; the API Gateway owns browser CORS.

- [ ] **Step 3: Inspect the security failure cases**

Run:

```powershell
rtk rg -n "realm_access|BUSINESS_ROLES|ROLE_|instanceof|actuator/health|actuator/\*\*|internal/v1|STATELESS|oauth2ResourceServer" services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java services/booking-service/src/main/java/com/flashticket/booking_service/security/KeycloakRealmRoleConverter.java
```

Expected: all three whitelisted roles, defensive `instanceof` claim checks, ordered health/actuator rules, stateless policy, and JWT resource-server configuration are visible.

- [ ] **Step 4: Format and compile production sources**

Run from `services/booking-service` with Java 21:

```powershell
rtk .\mvnw.cmd -DskipTests spotless:apply compile
```

Expected: `BUILD SUCCESS`; no test goal runs.

- [ ] **Step 5: Commit**

```powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/config/SecurityConfig.java services/booking-service/src/main/java/com/flashticket/booking_service/security/KeycloakRealmRoleConverter.java
rtk git commit -m "feat(booking): configure JWT resource server"
```

### Task 3: Configure RabbitMQ JSON conversion without topology ownership

**Files:**

- Create: `services/booking-service/src/main/java/com/flashticket/booking_service/config/RabbitMqConfig.java`

**Interfaces:**

- Consumes: Boot-managed `tools.jackson.databind.json.JsonMapper` already customized by `BookingJsonConfig`.
- Produces: `MessageConverter rabbitMessageConverter(JsonMapper jsonMapper)` returning Spring AMQP 4.1.1 `JacksonJsonMessageConverter`.

- [ ] **Step 1: Add the AMQP converter bean**

Create `@Configuration(proxyBeanMethods = false)` with one package-private bean:

```java
MessageConverter rabbitMessageConverter(JsonMapper jsonMapper)
```

Return `new JacksonJsonMessageConverter(jsonMapper)`. Do not create a second mapper, `RabbitTemplate`, listener factory, topology object, or admin bean; Boot connects the converter to its auto-configured template/container.

- [ ] **Step 2: Run the no-topology gate**

Run:

```powershell
rtk rg -n "new Queue|DirectExchange|TopicExchange|FanoutExchange|BindingBuilder|RabbitAdmin|Declarables" services/booking-service/src/main/java
```

Expected: no matches.

- [ ] **Step 3: Format and compile production sources**

Run from `services/booking-service` with Java 21:

```powershell
rtk .\mvnw.cmd -DskipTests spotless:apply compile
```

Expected: `BUILD SUCCESS` and the Jackson 3 converter constructor resolves.

- [ ] **Step 4: Commit**

```powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/config/RabbitMqConfig.java
rtk git commit -m "feat(booking): configure RabbitMQ JSON conversion"
```

### Task 4: Add minimal feature-owned Spring Data repositories

**Files:**

- Create: the twelve repository files listed in the File Map.

**Interfaces:**

- Consumes: existing Booking JPA entities and ID types.
- Produces:
  - `OrderRepository extends JpaRepository<Order, UUID>`
  - `IdempotencyRequestRepository extends JpaRepository<IdempotencyRequest, IdempotencyRequestId>`
  - `EventSalesSnapshotRepository extends JpaRepository<EventSalesSnapshot, UUID>`
  - `TicketTypeInventoryRepository extends JpaRepository<TicketTypeInventory, UUID>`
  - `SectorInventoryRepository extends JpaRepository<SectorInventory, UUID>`
  - `SeatInventoryRepository extends JpaRepository<SeatInventory, UUID>`
  - `PurchaseLimitRepository extends JpaRepository<PurchaseLimit, PurchaseLimitId>`
  - `ReservationRepository extends JpaRepository<Reservation, UUID>`
  - `PromotionInventoryRepository extends JpaRepository<PromotionInventory, UUID>`
  - `PromotionUsageRepository extends JpaRepository<PromotionUsage, UUID>`
  - `InboxMessageRepository extends JpaRepository<InboxMessage, InboxMessageId>`
  - `OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID>`

- [ ] **Step 1: Create order and availability repositories**

Create the seven interfaces under `order.repository` and `availability.repository` with only their `JpaRepository` extension; add no `@Repository` annotation or method.

- [ ] **Step 2: Create reservation, promotion, and messaging repositories**

Create the remaining five interfaces under their feature packages with only their `JpaRepository` extension.

- [ ] **Step 3: Run repository scope checks**

Run:

```powershell
rtk rg -n "interface .*Repository extends JpaRepository" services/booking-service/src/main/java/com/flashticket/booking_service
rtk rg -n "OrderItemRepository|OrderItemSeatRepository|ReservationAllocationRepository|@Query|@Lock|@EntityGraph" services/booking-service/src/main/java/com/flashticket/booking_service
```

Expected: exactly twelve repository declarations; the second command returns no matches.

- [ ] **Step 4: Format and compile production sources**

Run from `services/booking-service` with Java 21:

```powershell
rtk .\mvnw.cmd -DskipTests spotless:apply compile
```

Expected: `BUILD SUCCESS`; entity and embedded-ID types resolve.

- [ ] **Step 5: Commit**

```powershell
rtk git add services/booking-service/src/main/java/com/flashticket/booking_service/order services/booking-service/src/main/java/com/flashticket/booking_service/availability services/booking-service/src/main/java/com/flashticket/booking_service/reservation services/booking-service/src/main/java/com/flashticket/booking_service/promotion services/booking-service/src/main/java/com/flashticket/booking_service/messaging
rtk git commit -m "feat(booking): add persistence repositories"
```

### Task 5: Final scope and build verification

**Files:**

- Inspect only; no new production file.

**Interfaces:**

- Consumes: Tasks 1–4.
- Produces: evidence that the approved scaffold compiles and contains no deferred abstractions.

- [ ] **Step 1: Verify Java runtime**

Run:

```powershell
rtk java -version
```

Expected: Java `21.x`. If only JDK 23 is available, report the blocker and do not claim compile success.

- [ ] **Step 2: Verify no out-of-scope production types**

Run:

```powershell
rtk rg --files services/booking-service/src/main/java/com/flashticket/booking_service | Sort-Object
rtk rg -n "@RestController|@RabbitListener|class RedisConfig|interface .*Service|class Default.*Service" services/booking-service/src/main/java/com/flashticket/booking_service
```

Expected: only approved config/security/repository additions; the second command returns no matches.

- [ ] **Step 3: Run the final production build without tests**

Run from `services/booking-service`:

```powershell
rtk .\mvnw.cmd -DskipTests clean verify
```

Expected: `BUILD SUCCESS`. Maven may execute non-test quality plugins; no test goal should execute tests because `-DskipTests` is set.

- [ ] **Step 4: Review final diff**

Run from repository root:

```powershell
rtk git status --short
rtk git diff HEAD~4 --check
rtk git diff HEAD~4 --stat
```

Expected: no whitespace errors and scope limited to the approved Booking/config files plus the already-approved documentation commits.

