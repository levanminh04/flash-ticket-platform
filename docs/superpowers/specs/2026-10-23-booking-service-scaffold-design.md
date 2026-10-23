# Booking Service Runtime Scaffold Design

- Date: 2026-10-23
- Status: DRAFT — awaiting final approval
- Scope: Booking Service runtime configuration and persistence entry points

## 1. Intent

Prepare the smallest useful Booking scaffold for later business implementation. Configure Security, RabbitMQ, and Redis integration points, and expose only persistence interfaces that already have a clear owner.

This revision applies the Ponytail review: no empty controller, service, consumer, publisher, or cache abstraction is created merely to reserve a name.

## 2. Goals

- Configure Booking as a Keycloak-backed OAuth2 Resource Server.
- Configure RabbitMQ JSON conversion without declaring broker topology.
- Add Redis support through Boot auto-configuration and central YAML.
- Add Spring Data repositories for aggregate roots or independently accessed state.
- Keep the existing entity, DTO, mapper, enum, and validation packages unchanged.
- Compile under Java 21 without adding tests.

## 3. Non-goals

- No endpoint, application service, message handler, publisher, cache operation, scheduler, transaction flow, locking query, or business method.
- No fake return value, `UnsupportedOperationException`, commented algorithm, or empty interface/implementation pair.
- No repository for `OrderItem`, `OrderItemSeat`, or `ReservationAllocation`; their lifecycle belongs to the owning aggregate.
- No tests or test fixtures for this task.
- No changes to other business services.

## 4. Approaches considered

### Global technical layers

Familiar from `ShopApp-BE`, but global controller/service/repository folders grow into unrelated buckets.

### Feature-first layered packages — selected

Place persistence interfaces under the feature that owns them. Add controller and service types only when the first use case provides real methods.

### Full hexagonal architecture

Rejected for now because empty ports/adapters would add structure without behavior.

## 5. Package structure after this task

```text
com.flashticket.booking_service
├── BookingServiceApplication.java
├── config
│   ├── BookingJsonConfig.java          (existing)
│   ├── RabbitMqConfig.java
│   └── SecurityConfig.java
├── security
│   └── KeycloakRealmRoleConverter.java
├── order.repository
│   ├── OrderRepository.java
│   └── IdempotencyRequestRepository.java
├── availability.repository
│   ├── EventSalesSnapshotRepository.java
│   ├── TicketTypeInventoryRepository.java
│   ├── SectorInventoryRepository.java
│   ├── SeatInventoryRepository.java
│   └── PurchaseLimitRepository.java
├── reservation.repository
│   └── ReservationRepository.java
├── promotion.repository
│   ├── PromotionInventoryRepository.java
│   └── PromotionUsageRepository.java
├── messaging.repository
│   ├── InboxMessageRepository.java
│   └── OutboxMessageRepository.java
├── entity                              (existing)
├── dto                                 (existing)
├── mapper                              (existing)
└── validation                          (existing)
```

No `controller`, service implementation, Rabbit consumer/producer, or cache package is added in this task.

## 6. Configuration

### Security

`SecurityConfig` will:

- use stateless servlet security and disable CSRF for the bearer-token API;
- permit public availability reads;
- reserve Booking order paths for `ROLE_BUYER`;
- permit only the minimal health endpoint and protect other actuator/application requests;
- enable OAuth2 Resource Server JWT using Config Server properties;
- use `KeycloakRealmRoleConverter` to whitelist `BUYER`, `ORGANIZER`, and `ADMIN` from `realm_access.roles`.

Ownership and state authorization remain application-service responsibilities for later work.

### RabbitMQ

`RabbitMqConfig` provides a Jackson JSON message converter and relies on Boot-managed `RabbitTemplate` and listener infrastructure. Connection, manual acknowledgement, prefetch, confirms, returns, exchange, and mandatory publishing remain in `booking-service.yaml`.

It must not define `Queue`, `Exchange`, `Binding`, or `RabbitAdmin` beans because infrastructure already owns the topology.

### Redis

Add `spring-boot-starter-data-redis`. Keep connection properties in Config Server and rely on Boot's Lettuce/`StringRedisTemplate` auto-configuration.

There is no `RedisConfig`, cache service, key namespace, TTL, or locking abstraction until a concrete use case defines them. Java native serialization is not introduced.

## 7. Repository rules

- Extend `JpaRepository<Entity, IdType>` with the entity's actual ID type.
- Add no derived query, JPQL, native SQL, lock annotation, or entity graph.
- Child entities are persisted through their aggregate relationship.
- Add a new repository later only when a transaction requires an independent access path.

## 8. Verification

No tests are authored or run for this task. Acceptance requires:

1. only the configuration and repository files listed above are added;
2. Redis starter is present and no unrelated dependency is introduced;
3. no application-owned Rabbit topology declaration exists;
4. formatting passes;
5. Booking compiles with Java 21 and tests skipped;
6. Config Server keeps secrets as environment placeholders.

The current machine selects JDK 23 while Maven Enforcer requires `[21,22)`. Verification must use an available Java 21 runtime or report that environment blocker; the Enforcer rule is not weakened.

## 9. Research basis

- Spring Boot package structure: <https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html>
- Spring Security JWT Resource Server: <https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html>
- Spring Boot AMQP: <https://docs.spring.io/spring-boot/reference/messaging/amqp.html>
- Spring Data Redis templates: <https://docs.spring.io/spring-data/redis/reference/redis/template.html>

