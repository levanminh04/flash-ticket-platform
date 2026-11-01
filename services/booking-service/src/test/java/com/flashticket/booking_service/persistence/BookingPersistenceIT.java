package com.flashticket.booking_service.persistence;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.dto.snapshot.*;
import com.flashticket.booking_service.entity.*;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.enums.*;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.order.repository.OrderRepository;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@DataJpaTest(
    properties = {
      "spring.autoconfigure.exclude=",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.properties.hibernate.default_schema=booking_schema"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = BookingPersistenceIT.BookingConfigInitializer.class)
@Testcontainers
class BookingPersistenceIT {
  static class BookingConfigInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
      var yaml =
          Path.of(System.getProperty("basedir", "."))
              .resolve(
                  "../../infrastructure/config-server/src/main/resources/config/booking-service.yaml");
      try {
        for (var source :
            new YamlPropertySourceLoader().load("booking", new FileSystemResource(yaml))) {
          context.getEnvironment().getPropertySources().addLast(source);
        }
      } catch (java.io.IOException exception) {
        throw new IllegalStateException(exception);
      }
    }
  }

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("booking_db");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeAll
  static void initializeCheckedInSchema() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("CREATE ROLE booking_owner NOLOGIN");
      statement.execute("CREATE SCHEMA booking_schema AUTHORIZATION booking_owner");
    }
    Path repository = Path.of(System.getProperty("basedir", ".")).resolve("../..").normalize();
    POSTGRES.copyFileToContainer(
        MountableFile.forHostPath(
            repository.resolve("docs/architecture/data/postgres/20-booking.sql").toString()),
        "/tmp/20-booking.sql");
    POSTGRES.copyFileToContainer(
        MountableFile.forHostPath(repository.resolve("services/99-table-prefix.sql").toString()),
        "/tmp/99-table-prefix.sql");
    // Use the same script order and psql interpreter as the deployment initializer.
    var result =
        POSTGRES.execInContainer(
            "psql",
            "-X",
            "-v",
            "ON_ERROR_STOP=1",
            "-U",
            POSTGRES.getUsername(),
            "-d",
            POSTGRES.getDatabaseName(),
            "-f",
            "/tmp/20-booking.sql",
            "-f",
            "/tmp/99-table-prefix.sql");
    assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
  }

  @Autowired EntityManager em;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationContext context;
  @Autowired OrderRepository orders;

  @Test
  void inboxJsonbRoundTripsWithJackson3TreeModel() {
    var inbox = new InboxMessage();
    var id = new InboxMessageId();
    id.setConsumerName("jsonb-regression");
    id.setMessageId(MESSAGE);
    inbox.setId(id);
    inbox.setPayloadHash("hash");
    inbox.setProcessedAt(NOW);
    inbox.setResponsePayload(
        jsonMapper()
            .valueToTree(Map.of("amount", "9999999999999999999", "nested", Map.of("ok", true))));
    em.persist(inbox);
    em.flush();
    em.clear();
    var payload = em.find(InboxMessage.class, id).getResponsePayload();
    assertThat(payload.get("amount").asString()).isEqualTo("9999999999999999999");
    assertThat(payload.get("nested").get("ok").asBoolean()).isTrue();
  }

  @Test
  void outboxJsonbRoundTripsWithJackson3TreeModel() {
    var outbox = new OutboxMessage();
    outbox.setMessageId(MESSAGE);
    outbox.setAggregateId(ORDER);
    outbox.setAggregateVersion(1L);
    outbox.setMessageType("booking.order-completed.v1");
    outbox.setSchemaVersion(1);
    outbox.setPayload(jsonMapper().valueToTree(Map.of("ticketCount", 2)));
    outbox.setOccurredAt(NOW);
    outbox.setCorrelationId(EVENT);
    outbox.setAttempts(0);
    outbox.setNextAttemptAt(NOW);
    em.persist(outbox);
    em.flush();
    em.clear();
    assertThat(em.find(OutboxMessage.class, MESSAGE).getPayload().get("ticketCount").asInt())
        .isEqualTo(2);
  }

  @Test
  void allEntityTableNamesResolveInTheCheckedInSchema() {
    List<String> missing = new ArrayList<>();
    for (var type : em.getMetamodel().getEntities()) {
      Table table = type.getJavaType().getAnnotation(Table.class);
      String name = table.schema() + "." + table.name();
      if (jdbc.queryForObject("select to_regclass(?)::text", String.class, name) == null)
        missing.add(name);
    }
    assertThat(missing).as("Entity tables absent from checked-in DDL").isEmpty();
  }

  @Test
  void allTwelveRepositoriesReadTheirPersistedEntityAfterClearingTheContext() {
    var keys = persistGraph();
    em.flush();
    em.clear();
    var repositories = context.getBeansOfType(JpaRepository.class);
    assertThat(repositories).hasSize(12);
    keys.forEach(
        (bean, id) -> {
          @SuppressWarnings("unchecked")
          JpaRepository<Object, Object> repository = context.getBean(bean, JpaRepository.class);
          assertThat(repository.findById(id)).as(bean).isPresent();
        });
    Order order = orders.findById((UUID) keys.get("orderRepository")).orElseThrow();
    assertThat(order.getSubtotal()).isEqualByComparingTo("100");
    assertThat(order.getCurrency()).isEqualTo(Currency.VND);
    assertThat(order.getCreatedAt()).isEqualTo(NOW);
    assertThat(order.getItems()).hasSize(1);
    assertThat(order.getItems().getFirst().getSelectionSnapshot().seats()).containsExactly(seat());
    assertThat(order.getItems().getFirst().getSeatAssignments()).hasSize(1);
    assertThat(order.getItems().getFirst().getSeatAssignments().getFirst().getOrderItem())
        .isSameAs(order.getItems().getFirst());
    assertThat(em.find(Reservation.class, keys.get("reservationRepository")).getAllocations())
        .hasSize(1);
    var outbox = em.find(OutboxMessage.class, MESSAGE);
    assertThat(outbox.getPayload().get("total").asString()).isEqualTo("200");
    var promotion =
        em.find(PromotionInventory.class, (UUID) keys.get("promotionInventoryRepository"));
    assertThat(promotion.getConfigurationSnapshot().schemaVersion()).isEqualTo(1);
  }

  @Test
  void staleVersionCannotOverwriteAConcurrentUpdate() {
    UUID id = (UUID) persistGraph().get("orderRepository");
    em.flush();
    em.clear();
    Order stale = em.find(Order.class, id);
    em.detach(stale);
    Order current = em.find(Order.class, id);
    current.setCustomerEmail("updated@example.com");
    em.flush();
    stale.setCustomerEmail("stale@example.com");
    assertThatThrownBy(() -> orders.saveAndFlush(stale))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
  }

  @ParameterizedTest
  @CsvSource({"-1,0,0", "10,-1,0", "10,0,-1", "10,6,5", "10,0,11"})
  void inventoryCountersCannotBecomeNegativeOrExceedCapacity(
      long capacity, long held, long purchased) {
    seedSqlEvent();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        insert into booking_schema.tbl_ticket_type_inventory
        (ticket_type_id,event_id,configured_capacity,held,purchased,source_config_version)
        values (?,?,?,?,?,1)
        """,
                    TICKET,
                    EVENT,
                    capacity,
                    held,
                    purchased))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void inventoryCannotReferenceAnUnknownEvent() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        insert into booking_schema.tbl_ticket_type_inventory
        (ticket_type_id,event_id,configured_capacity,source_config_version) values (?,?,10,1)
        """,
                    TICKET,
                    EVENT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void inboxKeyIsUniquePerConsumerAndMessage() {
    jdbc.update(
        "insert into booking_schema.tbl_inbox_messages(consumer_name,message_id,payload_hash) values ('consumer',?,'hash')",
        MESSAGE);
    jdbc.update(
        "insert into booking_schema.tbl_inbox_messages(consumer_name,message_id,payload_hash) values ('other',?,'hash')",
        MESSAGE);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into booking_schema.tbl_inbox_messages(consumer_name,message_id,payload_hash) values ('consumer',?,'hash')",
                    MESSAGE))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {199, 300, 500})
  void idempotencyOnlyStoresSuccessfulStatusCodes(int status) {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        insert into booking_schema.tbl_idempotency_requests
        (actor_subject,operation,request_key,request_hash,resource_id,response_status)
        values ('buyer','create',?,'hash',?,?)
        """,
                    MESSAGE,
                    ORDER,
                    status))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void outboxPayloadMustBeAnObject() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    """
        insert into booking_schema.tbl_outbox_messages
        (message_id,aggregate_id,aggregate_version,message_type,payload,occurred_at,correlation_id)
        values (?,?,1,'booking.order-completed.v1','[]'::jsonb,now(),?)
        """,
                    MESSAGE,
                    ORDER,
                    EVENT))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void seedSqlEvent() {
    jdbc.update(
        """
        insert into booking_schema.tbl_event_sales_snapshots
        (event_id,organizer_subject,sales_mode,event_status,sale_starts_at,sale_ends_at,
        purchase_limit,configuration_snapshot,source_config_version,received_at)
        values (?,'organizer','QUANTITY','PUBLISHED',now(),now()+interval '1 hour',
        5,'{"schemaVersion":1}'::jsonb,1,now())
        """,
        EVENT);
  }

  private Map<String, Object> persistGraph() {
    EventSalesSnapshot event = new EventSalesSnapshot();
    event.setEventId(EVENT);
    event.setOrganizerSubject("organizer");
    event.setSalesMode(SalesMode.SEAT_MAP);
    event.setEventStatus(EventState.PUBLISHED);
    event.setSaleStartsAt(NOW);
    event.setSaleEndsAt(NOW.plusSeconds(3600));
    event.setPurchaseLimit(5);
    event.setConfigurationSnapshot(with(sales(), "mode", SalesMode.SEAT_MAP));
    event.setSourceConfigVersion(1L);
    event.setReceivedAt(NOW);
    em.persist(event);
    TicketTypeInventory ticket = new TicketTypeInventory();
    ticket.setTicketTypeId(TICKET);
    ticket.setEventId(EVENT);
    ticket.setConfiguredCapacity(10L);
    ticket.setHeld(0L);
    ticket.setPurchased(0L);
    ticket.setSourceConfigVersion(1L);
    em.persist(ticket);
    SectorInventory sector = new SectorInventory();
    sector.setSectorId(SECTOR);
    sector.setEventId(EVENT);
    sector.setSectorType(SectorType.SEATED);
    sector.setConfiguredCapacity(10L);
    sector.setHeld(0L);
    sector.setPurchased(0L);
    sector.setSourceConfigVersion(1L);
    em.persist(sector);
    SeatInventory seat = new SeatInventory();
    seat.setSeatId(SEAT);
    seat.setEventId(EVENT);
    seat.setSectorId(SECTOR);
    seat.setTicketTypeId(TICKET);
    seat.setIsHidden(false);
    seat.setSectorType(SectorType.SEATED);
    seat.setSeatLabel("A1");
    seat.setState(SeatState.AVAILABLE);
    seat.setReservationGeneration(0L);
    seat.setSourceConfigVersion(1L);
    em.persist(seat);
    Order order = new Order();
    order.setEventId(EVENT);
    order.setBuyerSubject("buyer");
    order.setSalesMode(SalesMode.SEAT_MAP);
    order.setSectorId(SECTOR);
    order.setLifecycleState(OrderState.PENDING_PAYMENT);
    order.setCustomerEmail("buyer@example.com");
    order.setSubtotal(new BigDecimal("100"));
    order.setDiscount(BigDecimal.ZERO);
    order.setTotal(new BigDecimal("100"));
    order.setCurrency(Currency.VND);
    order.setExpiresAt(NOW.plusSeconds(600));
    order.setSourceConfigVersion(1L);
    order.setCreatedAt(NOW);
    order.setUpdatedAt(NOW);
    OrderItem item = new OrderItem();
    item.setEventId(EVENT);
    item.setTicketTypeId(TICKET);
    item.setSectorId(SECTOR);
    item.setSalesMode(SalesMode.SEAT_MAP);
    item.setTicketTypeName("Standard");
    item.setQuantity(1);
    item.setUnitPrice(new BigDecimal("100"));
    item.setLineSubtotal(new BigDecimal("100"));
    item.setCurrency(Currency.VND);
    item.setSelectionSnapshot(new SelectionSnapshot(1, List.of(seat())));
    order.addItem(item);
    em.persist(order);
    OrderItemSeat assignment = new OrderItemSeat();
    var seatId = new OrderItemSeatId();
    seatId.setOrderItemId(item.getId());
    seatId.setSeatId(SEAT);
    assignment.setId(seatId);
    assignment.setOrderId(order.getId());
    assignment.setEventId(EVENT);
    assignment.setSectorId(SECTOR);
    item.addSeatAssignment(assignment);
    em.persist(order);
    Reservation reservation = new Reservation();
    reservation.setOrder(order);
    reservation.setEventId(EVENT);
    reservation.setBuyerSubject("buyer");
    reservation.setState(ReservationState.HELD);
    reservation.setExpiresAt(order.getExpiresAt());
    reservation.setCreatedAt(NOW);
    em.persist(reservation);
    ReservationAllocation allocation = new ReservationAllocation();
    allocation.setOrderId(order.getId());
    allocation.setOrderItem(item);
    allocation.setEventId(EVENT);
    allocation.setResourceKind(ResourceKind.SEAT);
    allocation.setSeatId(SEAT);
    allocation.setQuantity(1);
    allocation.setState(ReservationState.HELD);
    reservation.addAllocation(allocation);
    em.persist(reservation);
    PurchaseLimit limit = new PurchaseLimit();
    var limitId = new PurchaseLimitId();
    limitId.setEventId(EVENT);
    limitId.setBuyerSubject("buyer");
    limit.setId(limitId);
    limit.setConfiguredLimit(5);
    limit.setHeld(0);
    limit.setPurchased(0);
    limit.setSourceConfigVersion(1L);
    em.persist(limit);
    PromotionInventory promotion = new PromotionInventory();
    promotion.setPromotionId(MESSAGE);
    promotion.setEventId(EVENT);
    promotion.setConfiguredLimit(10L);
    promotion.setPerUserLimit(1);
    promotion.setHeld(0L);
    promotion.setUsed(0L);
    promotion.setStartsAt(NOW);
    promotion.setEndsAt(NOW.plusSeconds(3600));
    promotion.setConfigurationSnapshot(new PromotionConfigurationSnapshot(1, promotion()));
    promotion.setSourceConfigVersion(1L);
    em.persist(promotion);
    PromotionUsage usage = new PromotionUsage();
    usage.setPromotion(promotion);
    usage.setEventId(EVENT);
    usage.setReservation(reservation);
    usage.setBuyerSubject("buyer");
    usage.setGeneration(1L);
    usage.setState(PromotionUsageState.HELD);
    usage.setDiscount(BigDecimal.TEN);
    usage.setCurrency(Currency.VND);
    usage.setExpiresAt(reservation.getExpiresAt());
    em.persist(usage);
    InboxMessage inbox = new InboxMessage();
    var inboxId = new InboxMessageId();
    inboxId.setConsumerName("consumer");
    inboxId.setMessageId(MESSAGE);
    inbox.setId(inboxId);
    inbox.setPayloadHash("hash");
    inbox.setProcessedAt(NOW);
    inbox.setResponsePayload(jsonMapper().valueToTree(Map.of("ok", true)));
    em.persist(inbox);
    OutboxMessage outbox = new OutboxMessage();
    outbox.setMessageId(MESSAGE);
    outbox.setAggregateId(order.getId());
    outbox.setAggregateVersion(1L);
    outbox.setMessageType("payment.start-requested.v1");
    outbox.setSchemaVersion(1);
    outbox.setPayload(jsonMapper().valueToTree(purchase()));
    outbox.setOccurredAt(NOW);
    outbox.setCorrelationId(EVENT);
    outbox.setAttempts(0);
    outbox.setNextAttemptAt(NOW);
    em.persist(outbox);
    IdempotencyRequest request = new IdempotencyRequest();
    var requestId = new IdempotencyRequestId();
    requestId.setActorSubject("buyer");
    requestId.setOperation("create");
    requestId.setRequestKey(MESSAGE);
    request.setId(requestId);
    request.setRequestHash("hash");
    request.setResourceId(order.getId());
    request.setResponseStatus(201);
    request.setCreatedAt(NOW);
    em.persist(request);
    Map<String, Object> keys = new LinkedHashMap<>();
    keys.put("eventSalesSnapshotRepository", EVENT);
    keys.put("ticketTypeInventoryRepository", TICKET);
    keys.put("sectorInventoryRepository", SECTOR);
    keys.put("seatInventoryRepository", SEAT);
    keys.put("orderRepository", order.getId());
    keys.put("reservationRepository", reservation.getId());
    keys.put("purchaseLimitRepository", limitId);
    keys.put("promotionInventoryRepository", MESSAGE);
    keys.put("promotionUsageRepository", usage.getId());
    keys.put("inboxMessageRepository", inboxId);
    keys.put("outboxMessageRepository", MESSAGE);
    keys.put("idempotencyRequestRepository", requestId);
    return keys;
  }
}
