# Booking Service Runtime Scaffold Design

- Date: 2026-10-23
- Status: DRAFT — awaiting final design approval
- Scope: `services/booking-service` and its central configuration only

## 1. Intent

Prepare a clean, compilable production scaffold for Booking Service so later business-flow work can start from stable package boundaries and runtime integrations instead of repeatedly deciding structure.

The scaffold configures Spring Security as a Keycloak-backed OAuth2 Resource Server, RabbitMQ, and Redis. It also creates the controller, service, repository, cache, and messaging types that Booking will need. This task deliberately stops before implementing order, inventory, promotion, payment, inbox/outbox, or message-handling behavior.

`ShopApp-BE` is prior art for familiar Controller–Service–Repository naming and constructor injection. Its monolith-specific JWT, mutable DTO, generic Redis service, and application-owned RabbitMQ topology are not copied.

## 2. Goals

1. Keep `BookingServiceApplication` in the root package so component, entity, and repository scanning remain conventional.
2. Organize new behavior by Booking capability while retaining the existing shared `entity`, `dto`, `mapper`, and `validation` packages.
3. Add functional runtime configuration for Keycloak JWT validation, RabbitMQ JSON conversion, and Redis access.
4. Create repository interfaces for every existing Booking entity that needs persistence access.
5. Create controller, service, cache, consumer, and producer shells without implementing business behavior.
6. Reuse Config Server properties and the broker topology already provisioned in `deploy/rabbitmq/definitions.json`.
7. Keep the project compilable under Java 21 and Spring Boot 4.1.1.

## 3. Non-goals

- No business method bodies, transaction orchestration, inventory locking, idempotency handling, inbox/outbox relay, scheduler, or Rabbit listener method.
- No endpoint response implementation and no placeholder endpoint returning fake data.
- No `UnsupportedOperationException` placeholders that could accidentally reach production.
- No new tests, test fixtures, or test dependencies for this task.
- No work outside Booking Service except its Config Server YAML when a required property is missing.
- No refactor of the 59 existing DTO/entity/mapper/validation files merely to achieve a theoretically perfect package tree.
- No parent Maven project, shared library, Spring Modulith dependency, Flyway/Liquibase introduction, or changes to other services.
- No queue, exchange, or binding declaration from application code.

## 4. Constraints and existing decisions

- Booking owns only `booking_db` / `booking_schema`; it must not import repositories or entities from Event, Payment, Ticket, or User.
- External API paths use `/api/v1`; the Gateway already routes Booking paths without stripping this prefix.
- Keycloak is the token issuer. Booking is a servlet Resource Server, not an identity provider and not a password store.
- Business roles are `BUYER`, `ORGANIZER`, and `ADMIN`, carried in Keycloak `realm_access.roles` and mapped to Spring `ROLE_*` authorities.
- RabbitMQ topology is infrastructure-owned and pre-provisioned. `spring.rabbitmq.dynamic=false` stays enabled.
- Rabbit consumers will eventually ACK after successful local commit, but this task does not add active listener methods.
- Redis is an optimization and coordination dependency, never the source of truth for orders or inventory.
- Existing strict JSON configuration and DTO/message shapes remain authoritative.
- The user explicitly requested no tests for this task. Verification is compile/static-format oriented only.

## 5. Approaches considered

### 5.1 Global technical layers

Create top-level `controller`, `service`, `service.impl`, and `repository` packages, following `ShopApp-BE` closely.

Trade-off: familiar and quick initially, but all Booking capabilities accumulate in the same folders. Ownership becomes harder to see as order, availability, reservation, promotion, payment, and messaging grow.

### 5.2 Feature-first layered packages — selected

Use direct feature packages such as `order`, `availability`, `reservation`, `promotion`, and `payment`. Each feature contains its controller/service and a nested repository package. Cross-cutting adapters live in `config`, `security`, `messaging`, and `cache`.

Trade-off: slightly more folders now, but related types stay together and later implementation does not require a disruptive reorganization. This follows Spring Boot's domain-oriented layout guidance while remaining a conventional Controller–Service–Repository application.

### 5.3 Full hexagonal architecture

Separate domain, application ports, inbound adapters, outbound adapters, and infrastructure for every capability.

Trade-off: strongest formal boundaries, but it introduces many empty ports and adapters before Booking has implemented behavior. It is disproportionate for the requested scaffold and makes the code less approachable to a team coming from `ShopApp-BE`.

## 6. Selected package structure

```text
com.flashticket.booking_service
├── BookingServiceApplication.java
├── config
│   ├── BookingJsonConfig.java                 (existing)
│   ├── RabbitMqConfig.java
│   ├── RedisConfig.java
│   └── SecurityConfig.java
├── security
│   └── KeycloakRealmRoleConverter.java
├── order
│   ├── OrderController.java
│   ├── OrderService.java
│   ├── DefaultOrderService.java
│   └── repository
│       ├── OrderRepository.java
│       ├── OrderItemRepository.java
│       ├── OrderItemSeatRepository.java
│       └── IdempotencyRequestRepository.java
├── availability
│   ├── AvailabilityController.java
│   ├── AvailabilityService.java
│   ├── DefaultAvailabilityService.java
│   └── repository
│       ├── EventSalesSnapshotRepository.java
│       ├── TicketTypeInventoryRepository.java
│       ├── SectorInventoryRepository.java
│       ├── SeatInventoryRepository.java
│       └── PurchaseLimitRepository.java
├── reservation
│   ├── ReservationService.java
│   ├── DefaultReservationService.java
│   └── repository
│       ├── ReservationRepository.java
│       └── ReservationAllocationRepository.java
├── promotion
│   ├── PromotionService.java
│   ├── DefaultPromotionService.java
│   └── repository
│       ├── PromotionInventoryRepository.java
│       └── PromotionUsageRepository.java
├── payment
│   ├── PaymentAttemptController.java
│   ├── PaymentService.java
│   └── DefaultPaymentService.java
├── messaging
│   ├── consumer
│   │   ├── SalesConfigurationConsumer.java
│   │   ├── EventCancellationConsumer.java
│   │   ├── AcceptPaymentConsumer.java
│   │   ├── TicketsIssuedConsumer.java
│   │   └── IssuanceFailedConsumer.java
│   ├── producer
│   │   ├── BookingMessagePublisher.java
│   │   └── RabbitBookingMessagePublisher.java
│   └── repository
│       ├── InboxMessageRepository.java
│       └── OutboxMessageRepository.java
├── cache
│   ├── BookingCacheService.java
│   └── RedisBookingCacheService.java
├── entity                                  (existing)
├── dto                                     (existing)
├── mapper                                  (existing)
└── validation                              (existing)
```

Feature implementation classes use the `DefaultXxxService` name rather than an `impl` subpackage. The interface and its default implementation remain adjacent, which removes navigation-only folders while preserving the interface/implementation convention familiar from `ShopApp-BE`.

## 7. Runtime configuration design

### 7.1 Security

`SecurityConfig` will:

- configure stateless servlet security and disable CSRF for the bearer-token API;
- permit `GET /api/v1/events/{eventId}/availability`;
- require `ROLE_BUYER` for Booking order and payment-attempt paths;
- permit only the minimal health endpoint needed by runtime health checks and keep other actuator endpoints protected;
- authenticate every unmatched application request;
- configure OAuth2 Resource Server JWT support using Boot properties rather than constructing a custom token decoder unnecessarily;
- install `KeycloakRealmRoleConverter` to read and whitelist roles from `realm_access.roles`.

Authentication only establishes identity and broad role. Ownership checks, state checks, and business authorization stay for later service implementation.

### 7.2 RabbitMQ

`RabbitMqConfig` will provide only application-side serialization/customization:

- a Jackson JSON message converter that reuses the application JSON mapper;
- Boot-managed `RabbitTemplate` and listener container configuration through `spring.rabbitmq.*`;
- publisher confirms, returns, mandatory publishing, manual acknowledgement, and prefetch values from Config Server.

It will not expose `Queue`, `Exchange`, `Binding`, or `RabbitAdmin` declarations. Defining those beans would conflict with the existing infrastructure-owned topology.

Consumer and producer types are intentionally inactive shells in this task: no `@RabbitListener` methods and no publish implementation. Their names reserve the intended boundaries without claiming that message processing is operational.

### 7.3 Redis

Add `spring-boot-starter-data-redis` to Booking. `RedisConfig` will rely on Boot's Lettuce connection auto-configuration and expose a string-oriented template/access boundary. Keys and stored JSON are strings; Java native serialization and permissive polymorphic typing are not used.

`BookingCacheService` and `RedisBookingCacheService` remain empty shells for this task. No key scheme, TTL, lock, inventory truth, or cached object contract is invented before a concrete use case is approved.

### 7.4 Central configuration

Continue to use `infrastructure/config-server/src/main/resources/config/booking-service.yaml` for datasource, RabbitMQ, Redis, and JWT properties. Secrets stay as environment placeholders. The local `services/booking-service/src/main/resources/application.yaml` remains bootstrap-only.

## 8. Controller and service scaffold rules

- Controllers are package-visible or public final classes with constructor injection and class-level route annotations only.
- No request-handler method is created until its service contract and behavior are implemented; this prevents a route from appearing operational when it is not.
- Service interfaces are initially empty capability boundaries.
- `DefaultXxxService` classes implement their corresponding empty interfaces and may be Spring components, but contain no business methods.
- No fake return values, commented-out algorithms, or `TODO` method bodies are added.
- Later implementation will add methods from B13 incrementally without changing package ownership.

Planned controller route ownership:

| Controller | Route family reserved for later implementation |
|---|---|
| `AvailabilityController` | `GET /api/v1/events/{eventId}/availability` |
| `OrderController` | create/list/detail, apply promotion, and cancel under `/api/v1/orders` |
| `PaymentAttemptController` | `POST /api/v1/orders/{orderId}/payment-attempts` |

## 9. Repository scaffold rules

- One Spring Data repository interface is created per current Booking entity listed in the package structure.
- Each extends `JpaRepository<Entity, IdType>` using the entity's actual identifier type.
- No speculative derived query, JPQL, native SQL, locking annotation, or entity graph is added.
- Repository interfaces are placed with the feature that owns the state, not in a global repository bucket.
- Inbox/outbox repositories live under `messaging.repository` because they support message delivery rather than a user-facing feature.

Queries and lock semantics will be designed together with each transaction and invariant; inventing them in this scaffold would create accidental API commitments.

## 10. Dependency direction

The intended direction is:

```text
controller -> service interface -> later service implementation -> repository
                                              -> cache boundary
                                              -> message publisher boundary
message consumer -> later application service
config/security -> Spring framework integration only
```

Controllers do not access repositories directly. Repositories do not publish messages. Cache adapters do not become a second persistence model. Message consumers do not mutate entities until an application service transaction exists.

## 11. Error and failure behavior

This task creates no executable business operations, so it introduces no new error mapping. Existing `Problem` DTO remains available for later controller advice.

Configuration failures should fail fast:

- invalid/missing JWT issuer, JWK, datasource, broker, or Redis settings are not silently replaced with production-looking defaults;
- broker topology is not auto-created by the service;
- Redis failure must not be hidden by a fake in-memory fallback in this scaffold.

## 12. Verification and acceptance

No tests will be authored or executed as part of this task. Completion requires:

1. all planned production files exist in the selected packages;
2. Booking has the Redis starter and no unnecessary new dependency;
3. no Rabbit topology declaration bean exists;
4. no service/controller/consumer contains business implementation or fake behavior;
5. source formatting passes;
6. Booking compiles with Java 21 using tests skipped;
7. the Git diff is limited to Booking, its central config where necessary, this design, and the later implementation plan.

The current workstation selects JDK 23 while Maven Enforcer requires Java 21. Implementation verification must locate/use an installed Java 21 runtime or report the environment blocker explicitly; it must not permanently weaken the Enforcer rule.

## 13. Research basis

- Spring Boot, “Structuring Your Code”: root application package and domain-oriented subpackages.
  <https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html>
- Spring Security, “OAuth 2.0 Resource Server JWT”: issuer/audience validation, `SecurityFilterChain`, and JWT authority conversion.
  <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>
- Spring Boot, “AMQP”: Boot-managed connection/template and automatic message-converter association.
  <https://docs.spring.io/spring-boot/reference/messaging/amqp.html>
- Spring Data Redis, “Working with Objects through RedisTemplate”: template reuse and avoidance of unsafe Java native serialization.
  <https://docs.spring.io/spring-data/redis/reference/redis/template.html>
- Spring PetClinic Microservices: practical separation of independently deployable Spring services and domain/web packages.
  <https://github.com/spring-petclinic/spring-petclinic-microservices>

## 14. Builder discretion left intentionally

- Exact import ordering and formatting required by the repository's Spotless configuration.
- Package-private versus public visibility where Spring proxying and Java compilation permit either.
- Whether the Redis bean is exposed as `StringRedisTemplate` or the matching `RedisOperations<String, String>` abstraction, provided serialization remains string/JSON based and no duplicate ambiguous bean is created.
- Exact class constructor signatures for empty implementations, provided dependencies do not create cycles or invent behavior.

