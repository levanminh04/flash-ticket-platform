package com.flashticket.booking_service.persistence;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.availability.repository.EventSalesSnapshotRepository;
import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.availability.service.impl.AvailabilityServiceImpl;
import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.dto.message.AcceptPayment;
import com.flashticket.booking_service.dto.message.BookingIssuanceResult;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.request.ApplyPromotionRequest;
import com.flashticket.booking_service.dto.request.CreateOrderRequest;
import com.flashticket.booking_service.dto.request.OrderSelection;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.dto.snapshot.*;
import com.flashticket.booking_service.entity.*;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.enums.*;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.exception.BusinessException;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.mapper.MappingValueConverter;
import com.flashticket.booking_service.mapper.OrderMapperImpl;
import com.flashticket.booking_service.messaging.BookingRabbitErrorHandler;
import com.flashticket.booking_service.messaging.SalesConfigurationListener;
import com.flashticket.booking_service.order.OrderExpiryWorker;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import com.flashticket.booking_service.order.service.impl.OrderServiceImpl;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MethodRabbitListenerEndpoint;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

@DataJpaTest(
    properties = {
      "spring.autoconfigure.exclude=",
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.properties.hibernate.default_schema=booking_schema"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = BookingPersistenceIT.BookingConfigInitializer.class)
@Import({
  AvailabilityServiceImpl.class,
  com.flashticket.booking_service.promotion.service.impl.PromotionServiceImpl.class,
  OrderServiceImpl.class,
  OrderMapperImpl.class,
  MappingValueConverter.class,
  JacksonAutoConfiguration.class,
  BookingJsonConfig.class,
  ValidationAutoConfiguration.class,
  com.flashticket.booking_service.messaging.service.impl.MessagingServiceImpl.class,
  com.flashticket.booking_service.mapper.OutboxMessageMapperImpl.class,
  BookingPersistenceIT.RelayRabbitTestConfiguration.class
})
@Testcontainers
@EnableResilientMethods
class BookingPersistenceIT {
  static final String RELAY_QUEUE =
      System.getenv().getOrDefault("BOOKING_TEST_RABBIT_QUEUE", "payment.decision.relay.test");
  static final String RELAY_EXCHANGE =
      System.getenv().getOrDefault("BOOKING_TEST_RABBIT_EXCHANGE", "business.ex");

  @Container
  static final GenericContainer<?> RELAY_RABBIT =
      new GenericContainer<>(
              org.testcontainers.utility.DockerImageName.parse("rabbitmq:4.3.6-management"))
          .withExposedPorts(5672)
          .withEnv("RABBITMQ_DEFAULT_USER", "booking_test")
          .withEnv("RABBITMQ_DEFAULT_PASS", "test-only")
          .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  public static class RelayRabbitTestConfiguration {
    @org.springframework.context.annotation.Bean(destroyMethod = "destroy")
    public CachingConnectionFactory relayRabbitConnectionFactory() {
      var connection =
          new CachingConnectionFactory(
              System.getenv().getOrDefault("BOOKING_TEST_RABBIT_HOST", RELAY_RABBIT.getHost()),
              System.getenv("BOOKING_TEST_RABBIT_HOST") == null
                  ? RELAY_RABBIT.getMappedPort(5672)
                  : Integer.parseInt(System.getenv().getOrDefault("RABBITMQ_PORT", "5672")));
      connection.setUsername(
          System.getenv("BOOKING_TEST_RABBIT_HOST") == null
              ? "booking_test"
              : System.getenv("RABBITMQ_USERNAME"));
      connection.setPassword(
          System.getenv("BOOKING_TEST_RABBIT_HOST") == null
              ? "test-only"
              : System.getenv("RABBITMQ_PASSWORD"));
      connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
      connection.setPublisherReturns(true);
      connection.setConnectionTimeout(5000);
      return connection;
    }

    @org.springframework.context.annotation.Bean
    public RabbitTemplate relayRabbitTemplate(
        CachingConnectionFactory relayRabbitConnectionFactory, JsonMapper objectMapper) {
      var template = new RabbitTemplate(relayRabbitConnectionFactory);
      template.setMessageConverter(new JacksonJsonMessageConverter(objectMapper));
      template.setMandatory(true);
      var admin = new RabbitAdmin(relayRabbitConnectionFactory);
      var exchange = new org.springframework.amqp.core.DirectExchange(RELAY_EXCHANGE, true, false);
      var queue = new Queue(RELAY_QUEUE, true, false, false, Map.of("x-queue-type", "quorum"));
      admin.declareExchange(exchange);
      admin.declareQueue(queue);
      admin.declareBinding(
          org.springframework.amqp.core.BindingBuilder.bind(queue)
              .to(exchange)
              .with("booking.payment-decision.v1"));
      admin.declareBinding(
          org.springframework.amqp.core.BindingBuilder.bind(queue)
              .to(exchange)
              .with("booking.order-completed.v1"));
      return template;
    }
  }

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
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean OrderRepository orders;
  @Autowired com.flashticket.booking_service.messaging.service.MessagingService messagingService;
  @Autowired RabbitTemplate relayRabbitTemplate;
  @Autowired com.flashticket.booking_service.promotion.service.PromotionService promotionService;
  @Autowired OrderService orderService;
  @Autowired AvailabilityService salesService;
  @Autowired EventSalesSnapshotRepository snapshots;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void selectsExpiredPendingOrderIdsWithBoundedCursor() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      for (int i = 0; i < 5; i++)
        orderService.createOrder(
            "query-buyer-" + i, UUID.randomUUID(), createOrderRequest(config, 1));
      var cutoff =
          jdbc.queryForObject(
                  "select max(expires_at) from booking_schema.tbl_orders where event_id=?",
                  java.sql.Timestamp.class,
                  config.eventId())
              .toInstant();
      var ids =
          jdbc.queryForList(
              "select id from booking_schema.tbl_orders where event_id=? order by id",
              UUID.class,
              config.eventId());
      jdbc.update(
          "update booking_schema.tbl_orders set lifecycle_state='CANCELLED' where id=?",
          ids.get(1));
      var page = org.springframework.data.domain.PageRequest.of(0, 2);
      assertThat(orders.findExpiredOrderIds(OrderState.PENDING_PAYMENT, cutoff, null, page))
          .containsExactly(ids.get(0), ids.get(2));
      assertThat(orders.findExpiredOrderIds(OrderState.PENDING_PAYMENT, cutoff, ids.get(2), page))
          .containsExactly(ids.get(3), ids.get(4));
      assertThat(orders.findExpiredOrderIds(OrderState.PENDING_PAYMENT, cutoff, ids.get(4), page))
          .isEmpty();
      Instant firstExpiry =
          jdbc.queryForObject(
                  "select min(expires_at) from booking_schema.tbl_orders where event_id=?",
                  java.sql.Timestamp.class,
                  config.eventId())
              .toInstant();
      assertThat(
              orders.findExpiredOrderIds(
                  OrderState.PENDING_PAYMENT, firstExpiry.minusNanos(1000), null, page))
          .isEmpty();
      var index =
          Arrays.stream(Order.class.getAnnotation(Table.class).indexes())
              .filter(i -> i.name().equals("tbl_orders_expiry_idx"))
              .findFirst()
              .orElseThrow();
      // Suite dùng DDL SQL sẵn; chạy DDL index từ annotation trên PostgreSQL test để kiểm tính hợp
      // lệ.
      jdbc.execute(
          "create index if not exists "
              + index.name()
              + " on booking_schema.tbl_orders ("
              + index.columnList()
              + ")");
      assertThat(
              jdbc.queryForObject(
                  "select indexdef from pg_indexes where schemaname='booking_schema' and indexname='tbl_orders_expiry_idx'",
                  String.class))
          .contains("lifecycle_state, expires_at, id");
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void expiresUnfrozenQuantityOrderAndReleasesHeldResources() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofSeconds(1));
      for (boolean voucher : List.of(false, true)) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        if (voucher) {
          var promo =
              new PromotionConfig(
                  UUID.randomUUID(),
                  "SAVE",
                  Instant.now().minusSeconds(60),
                  Instant.now().plusSeconds(3600),
                  "10",
                  1,
                  new PromotionConfig.PercentDiscount("PERCENT", 10));
          config = with(with(config, "promotions", List.of(promo)), "sourceConfigVersion", "2");
          salesService.synchronizeSalesConfiguration(
              new Envelope<>(
                  UUID.randomUUID(),
                  "event.sales-configured.v1",
                  1,
                  config.eventId(),
                  "2",
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  config));
        }
        var order =
            orderService.createOrder(
                "expiry-buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        if (voucher)
          promotionService.applyPromotionToOrder(
              "expiry-buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () ->
                    Boolean.TRUE.equals(
                        jdbc.queryForObject(
                            "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                            Boolean.class,
                            order.getOrderId())));
        long inboxBefore =
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_inbox_messages", Long.class);
        orderService.expireOrder(order.getOrderId());
        var stored =
            jdbc.queryForMap(
                "select * from booking_schema.tbl_orders where id=?", order.getOrderId());
        assertThat(stored.get("lifecycle_state")).isEqualTo("EXPIRED");
        assertThat(stored.get("frozen_purchase_snapshot")).isNull();
        assertThat(
                jdbc.queryForObject(
                    "select held from booking_schema.tbl_ticket_type_inventory where event_id=?",
                    Long.class,
                    config.eventId()))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select held from booking_schema.tbl_purchase_limits where event_id=?",
                    Integer.class,
                    config.eventId()))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select state from booking_schema.tbl_reservations where order_id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo("RELEASED");
        assertThat(
                jdbc.queryForList(
                    "select state from booking_schema.tbl_reservation_allocations where order_id=?",
                    String.class,
                    order.getOrderId()))
            .containsOnly("RELEASED");
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                    Long.class,
                    order.getOrderId()))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_inbox_messages", Long.class))
            .isEqualTo(inboxBefore);
        if (voucher) {
          assertThat(
                  jdbc.queryForObject(
                      "select state from booking_schema.tbl_promotion_usages where event_id=?",
                      String.class,
                      config.eventId()))
              .isEqualTo("RELEASED");
          assertThat(
                  jdbc.queryForObject(
                      "select held from booking_schema.tbl_promotion_inventory where event_id=?",
                      Long.class,
                      config.eventId()))
              .isZero();
        }
        var before = captureOrderCreationState(config.eventId());
        orderService.expireOrder(order.getOrderId());
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      }
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void expiryWorkerExpiresTenOrdersThroughTheProductionBatchAndTransactionalService()
      throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_orders where lifecycle_state='PENDING_PAYMENT' and expires_at<=clock_timestamp()",
                  Long.class))
          .as("isolated database has no unrelated expired orders")
          .isZero();
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofSeconds(1));
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var orderIds = new ArrayList<UUID>();
      for (int i = 0; i < 10; i++) {
        var order =
            orderService.createOrder(
                "expiry-worker-buyer-" + i, UUID.randomUUID(), createOrderRequest(config, 1));
        orderIds.add(order.getOrderId());
      }
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  jdbc.queryForObject(
                          "select count(*) from booking_schema.tbl_orders where event_id=? and lifecycle_state='PENDING_PAYMENT' and expires_at<=clock_timestamp()",
                          Long.class,
                          config.eventId())
                      == 10);

      var worker = new OrderExpiryWorker(orders, orderService);
      org.springframework.test.util.ReflectionTestUtils.setField(worker, "batchSize", 20);
      worker.expireDueOrders();

      for (UUID orderId : orderIds) {
        assertThat(
                jdbc.queryForObject(
                    "select lifecycle_state from booking_schema.tbl_orders where id=?",
                    String.class,
                    orderId))
            .isEqualTo("EXPIRED");
        assertThat(
                jdbc.queryForObject(
                    "select state from booking_schema.tbl_reservations where order_id=?",
                    String.class,
                    orderId))
            .isEqualTo("RELEASED");
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_reservation_allocations where order_id=? and state<>'RELEASED'",
                    Long.class,
                    orderId))
            .isZero();
      }
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_ticket_type_inventory where event_id=?",
                  Long.class,
                  config.eventId()))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_purchase_limits where event_id=? and held<>0",
                  Long.class,
                  config.eventId()))
          .isZero();

      var stateAfterFirstBatch = captureOrderCreationState(config.eventId());
      worker.expireDueOrders();
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(stateAfterFirstBatch);
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void expiresFrozenOrderAndReleasesHeldPromotion() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofSeconds(2));
      var base = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promo =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "10",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config = with(with(base, "promotions", List.of(promo)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      promotionService.applyPromotionToOrder(
          "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
      orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var money =
          jdbc.queryForMap(
              "select subtotal,discount,total,frozen_purchase_snapshot::text as frozen from booking_schema.tbl_orders where id=?",
              order.getOrderId());
      jdbc.update(
          "update booking_schema.tbl_promotion_inventory set ends_at=clock_timestamp()-interval '1 second' where event_id=?",
          config.eventId());
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      orderService.expireOrder(order.getOrderId());
      assertThat(
              jdbc.queryForMap(
                  "select subtotal,discount,total,frozen_purchase_snapshot::text as frozen from booking_schema.tbl_orders where id=?",
                  order.getOrderId()))
          .isEqualTo(money);
      var released =
          jdbc.queryForMap(
              "select held,used from booking_schema.tbl_promotion_inventory where event_id=?",
              config.eventId());
      assertThat(released).containsEntry("held", 0L).containsEntry("used", 0L);
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_promotion_usages where event_id=?",
                  String.class,
                  config.eventId()))
          .isEqualTo("RELEASED");
      orderService.expireOrder(order.getOrderId());
      assertThat(
              jdbc.queryForMap(
                  "select held,used from booking_schema.tbl_promotion_inventory where event_id=?",
                  config.eventId()))
          .isEqualTo(released);
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void expiresStandingOrderWithoutChangingTicketTypePool() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMillis(300));
      var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.STANDING, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var pools =
          jdbc.queryForList(
              "select * from booking_schema.tbl_ticket_type_inventory where event_id=?",
              config.eventId());
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      orderService.expireOrder(order.getOrderId());
      assertThat(
              jdbc.queryForMap(
                  "select held,purchased from booking_schema.tbl_sector_inventory where event_id=?",
                  config.eventId()))
          .containsEntry("held", 0L)
          .containsEntry("purchased", 0L);
      assertThat(
              jdbc.queryForList(
                  "select * from booking_schema.tbl_ticket_type_inventory where event_id=?",
                  config.eventId()))
          .isEqualTo(pools);
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("EXPIRED");
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void expiresSeatedOrderWithoutReleasingAnotherReservation() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMillis(300));
      var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 4, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 1));
      UUID seatId = config.seats().getFirst().seatId();
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      orderService.expireOrder(order.getOrderId());
      assertThat(
              jdbc.queryForMap(
                  "select state,current_reservation_id,reservation_generation from booking_schema.tbl_seat_inventory where seat_id=?",
                  seatId))
          .containsEntry("state", "AVAILABLE")
          .containsEntry("current_reservation_id", null)
          .containsEntry("reservation_generation", 1L);
      var second =
          orderService.createOrder("buyer2", UUID.randomUUID(), createOrderRequest(config, 1));
      var before = captureOrderCreationState(config.eventId());
      orderService.expireOrder(order.getOrderId());
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "select current_reservation_id from booking_schema.tbl_seat_inventory where seat_id=?",
                  UUID.class,
                  seatId))
          .isEqualTo(
              jdbc.queryForObject(
                  "select id from booking_schema.tbl_reservations where order_id=?",
                  UUID.class,
                  second.getOrderId()));
      // Trạng thái ghế hidden theo schema thật phải UNAVAILABLE và không còn chủ giữ.
      jdbc.update(
          "update booking_schema.tbl_seat_inventory set is_hidden=true,state='UNAVAILABLE',current_reservation_id=null where seat_id=?",
          seatId);
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          second.getOrderId())));
      var inconsistent = captureOrderCreationState(config.eventId());
      assertThatThrownBy(() -> orderService.expireOrder(second.getOrderId()))
          .isInstanceOf(BusinessException.class);
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(inconsistent);
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void skipsUnexpiredAndTerminalOrdersDuringExpiry() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 30, 5);
      var pending =
          orderService.createOrder("pending", UUID.randomUUID(), createOrderRequest(config, 1));
      var before = captureOrderCreationState(config.eventId());
      orderService.expireOrder(pending.getOrderId());
      orderService.expireOrder(UUID.randomUUID());
      assertThatThrownBy(() -> orderService.expireOrder(null))
          .isInstanceOf(BusinessException.class);
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      for (String state :
          List.of("ISSUING", "COMPLETED", "ISSUANCE_FAILED", "EXPIRED", "CANCELLED")) {
        var order =
            orderService.createOrder(state, UUID.randomUUID(), createOrderRequest(config, 1));
        if (List.of("ISSUING", "COMPLETED", "ISSUANCE_FAILED").contains(state)) {
          var frozen = orderService.preparePurchaseSnapshot(state, order.getOrderId());
          orderService.acceptPayment(
              new Envelope<>(
                  UUID.randomUUID(),
                  "booking.accept-payment.v1",
                  1,
                  order.getOrderId(),
                  frozen.sourceOrderVersion(),
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  new AcceptPayment(
                      order.getOrderId(),
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      frozen.total(),
                      frozen.currency(),
                      frozen.sourceOrderVersion())));
        }
        jdbc.update(
            "update booking_schema.tbl_orders set lifecycle_state=? where id=?",
            state,
            order.getOrderId());
        var unchanged = captureOrderCreationState(config.eventId());
        orderService.expireOrder(order.getOrderId());
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(unchanged);
      }
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void salesConfigurationAndInventoriesCommitTogether() {
    try {
      // Test đếm toàn bảng cần database test rỗng, không phụ thuộc thứ tự chạy.
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var config = with(sales(), "promotions", List.of(promotion()));
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, config);
      assertThat(org.springframework.aop.support.AopUtils.isAopProxy(salesService)).isTrue();
      salesService.synchronizeSalesConfiguration(message);
      assertThat(
              jdbc.queryForObject(
                  "select source_config_version from booking_schema.tbl_event_sales_snapshots where event_id=?",
                  Long.class,
                  EVENT))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "select configured_capacity from booking_schema.tbl_ticket_type_inventory where ticket_type_id=?",
                  Long.class,
                  TICKET))
          .isEqualTo(20L);
      assertThat(
              jdbc.queryForObject(
                  "select configured_limit from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  MESSAGE))
          .isEqualTo(10L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages where message_id=?",
                  Long.class,
                  MESSAGE))
          .isEqualTo(1L);
      // Dùng broker cô lập để kiểm đúng JSON converter + method adapter + AUTO ACK.
      try (var rabbit =
          new GenericContainer<>(
                  org.testcontainers.utility.DockerImageName.parse("rabbitmq:4.3.6-management"))
              .withExposedPorts(5672)
              .withEnv("RABBITMQ_DEFAULT_USER", "booking_test")
              .withEnv("RABBITMQ_DEFAULT_PASS", "test-only")
              .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1))) {
        rabbit.start();
        var connection = new CachingConnectionFactory(rabbit.getHost(), rabbit.getMappedPort(5672));
        connection.setUsername("booking_test");
        connection.setPassword("test-only");
        try {
          var admin = new RabbitAdmin(connection);
          String queue = "booking.sales-configured.test", dlq = queue + ".dlq";
          admin.declareQueue(new Queue(dlq, true, false, false, Map.of("x-queue-type", "quorum")));
          admin.declareQueue(
              new Queue(
                  queue,
                  true,
                  false,
                  false,
                  Map.of(
                      "x-queue-type",
                      "quorum",
                      "x-delivery-limit",
                      3,
                      "x-dead-letter-exchange",
                      "",
                      "x-dead-letter-routing-key",
                      dlq)));
          var converter = new JacksonJsonMessageConverter(context.getBean(JsonMapper.class));
          var method =
              SalesConfigurationListener.class.getMethod(
                  "handleSalesConfiguredEvent", Envelope.class);
          var methods =
              new org.springframework.messaging.handler.annotation.support
                  .DefaultMessageHandlerMethodFactory();
          methods.afterPropertiesSet();
          var endpoint = new MethodRabbitListenerEndpoint();
          endpoint.setId("sales-config-integration");
          endpoint.setBean(new SalesConfigurationListener(salesService));
          endpoint.setMethod(method);
          endpoint.setQueueNames(queue);
          endpoint.setMessageHandlerMethodFactory(methods);
          var factory = new SimpleRabbitListenerContainerFactory();
          factory.setConnectionFactory(connection);
          factory.setMessageConverter(converter);
          factory.setAcknowledgeMode(
              AcknowledgeMode.valueOf(method.getAnnotation(RabbitListener.class).ackMode()));
          factory.setPrefetchCount(1);
          factory.setErrorHandler(new BookingRabbitErrorHandler());
          var container = factory.createListenerContainer(endpoint);
          container.afterPropertiesSet();
          var template = new RabbitTemplate(connection);
          template.setMessageConverter(converter);
          container.start();
          try {
            var second =
                new Envelope<>(
                    UUID.randomUUID(),
                    message.messageType(),
                    1,
                    EVENT,
                    "2",
                    NOW,
                    EVENT,
                    null,
                    null,
                    with(config, "sourceConfigVersion", "2"));
            template.convertAndSend("", queue, second);
            Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(
                    () ->
                        assertThat(snapshots.findById(EVENT).orElseThrow().getSourceConfigVersion())
                            .isEqualTo(2L));
            // Dừng container xong: delivery đã commit phải được ACK, không quay lại queue.
            container.stop();
            assertThat(admin.getQueueInfo(queue).getMessageCount()).isZero();
            container.start();
            template.convertAndSend("", queue, with(second, "schemaVersion", 2));
            Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(() -> template.receive(dlq) != null);
            assertThat(
                    jdbc.queryForObject(
                        "select count(*) from booking_schema.tbl_inbox_messages", Long.class))
                .isEqualTo(2L);
            assertThat(snapshots.findById(EVENT).orElseThrow().getSourceConfigVersion())
                .isEqualTo(2L);
          } finally {
            container.stop();
            container.destroy();
          }
        } finally {
          connection.destroy();
        }
      } catch (NoSuchMethodException exception) {
        throw new AssertionError(exception);
      }
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void seatMapUsesSectorPoolsAndSeatStates() {
    try {
      UUID standing = UUID.randomUUID(),
          standingType = UUID.randomUUID(),
          hiddenSeat = UUID.randomUUID();
      var config = with(sales(), "mode", SalesMode.SEAT_MAP);
      config =
          with(
              config,
              "ticketTypes",
              List.of(
                  new SalesConfiguration.TicketTypeConfig(
                      TICKET, "Seated", "100", Currency.VND, SECTOR, null, null),
                  new SalesConfiguration.TicketTypeConfig(
                      standingType, "Standing", "50", Currency.VND, standing, null, null),
                  new SalesConfiguration.TicketTypeConfig(
                      UUID.randomUUID(),
                      "Standing VIP",
                      "70",
                      Currency.VND,
                      standing,
                      null,
                      null)));
      config =
          with(
              config,
              "sectors",
              List.of(
                  new SalesConfiguration.SectorConfig(SECTOR, SectorType.SEATED, "2"),
                  new SalesConfiguration.SectorConfig(standing, SectorType.STANDING, "100")));
      var hidden =
          new SalesConfiguration.SeatConfig(hiddenSeat, SECTOR, TICKET, "A2", "A", "2", true);
      config =
          with(
              config,
              "seats",
              List.of(
                  new SalesConfiguration.SeatConfig(SEAT, SECTOR, TICKET, "A1", "A", "1", false),
                  hidden));
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, config);
      salesService.synchronizeSalesConfiguration(message);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_ticket_type_inventory", Long.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_sector_inventory", Long.class))
          .isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select configured_capacity from booking_schema.tbl_sector_inventory where sector_id=?",
                  Long.class,
                  standing))
          .isEqualTo(100L);
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_seat_inventory where seat_id=?",
                  String.class,
                  hiddenSeat))
          .isEqualTo("UNAVAILABLE");
      var updated =
          with(
              with(config, "sourceConfigVersion", "2"),
              "seats",
              List.of(with(hidden, "isHidden", false)));
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              message.messageType(),
              1,
              EVENT,
              "2",
              NOW,
              EVENT,
              null,
              null,
              updated));
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_seat_inventory where seat_id=?",
                  String.class,
                  SEAT))
          .isEqualTo("UNAVAILABLE");
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_seat_inventory where seat_id=?",
                  String.class,
                  hiddenSeat))
          .isEqualTo("AVAILABLE");
      var anotherEvent = UUID.randomUUID();
      var reusedIds = with(config, "eventId", anotherEvent);
      assertThatThrownBy(
              () ->
                  salesService.synchronizeSalesConfiguration(
                      new Envelope<>(
                          UUID.randomUUID(),
                          message.messageType(),
                          1,
                          anotherEvent,
                          "1",
                          NOW,
                          EVENT,
                          null,
                          null,
                          reusedIds)))
          .isInstanceOf(BusinessException.class);
      assertThat(snapshots.findById(anotherEvent)).isEmpty();
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedInventoryWriteRollsBackSnapshotAndInbox() {
    try {
      // Test đếm toàn bảng cần database test rỗng, không phụ thuộc thứ tự chạy.
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var config = with(sales(), "mode", SalesMode.SEAT_MAP);
      config =
          with(
              config,
              "ticketTypes",
              List.of(with(sales().ticketTypes().getFirst(), "sectorId", SECTOR)));
      config =
          with(
              config,
              "sectors",
              List.of(new SalesConfiguration.SectorConfig(SECTOR, SectorType.SEATED, "2")));
      var seatConfig =
          new SalesConfiguration.SeatConfig(SEAT, SECTOR, TICKET, "A1", "A", "1", false);
      config =
          with(config, "seats", List.of(seatConfig, with(seatConfig, "seatId", UUID.randomUUID())));
      config = with(config, "promotions", List.of(promotion()));
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, config);
      // Hai ID khác nhau nhưng trùng label sector: lỗi unique thật khi flush/commit.
      assertThatThrownBy(() -> salesService.synchronizeSalesConfiguration(message))
          .isInstanceOf(DataIntegrityViolationException.class);
      for (String table :
          List.of(
              "tbl_event_sales_snapshots",
              "tbl_sector_inventory",
              "tbl_seat_inventory",
              "tbl_promotion_inventory",
              "tbl_inbox_messages"))
        assertThat(jdbc.queryForObject("select count(*) from booking_schema." + table, Long.class))
            .as(table)
            .isZero();
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replayedMessageDoesNotResetInventoryCounters() {
    try {
      // Test đếm toàn bảng cần database test rỗng, không phụ thuộc thứ tự chạy.
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, sales());
      salesService.synchronizeSalesConfiguration(message);
      jdbc.update(
          "update booking_schema.tbl_ticket_type_inventory set held=3,purchased=7 where ticket_type_id=?",
          TICKET);
      salesService.synchronizeSalesConfiguration(message);
      salesService.synchronizeSalesConfiguration(with(message, "messageId", UUID.randomUUID()));
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_ticket_type_inventory where ticket_type_id=?",
                  Long.class,
                  TICKET))
          .isEqualTo(3L);
      assertThat(
              jdbc.queryForObject(
                  "select purchased from booking_schema.tbl_ticket_type_inventory where ticket_type_id=?",
                  Long.class,
                  TICKET))
          .isEqualTo(7L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages", Long.class))
          .isEqualTo(2L);
      assertThat(snapshots.findById(EVENT).orElseThrow().getRowVersion()).isZero();
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void reusedMessageIdWithDifferentPayloadIsRejected() {
    try {
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, sales());
      salesService.synchronizeSalesConfiguration(message);
      String originalHash =
          jdbc.queryForObject(
              "select payload_hash from booking_schema.tbl_inbox_messages where message_id=?",
              String.class,
              MESSAGE);
      var conflicting = with(message, "data", with(sales(), "purchaseLimit", 6));
      assertThatThrownBy(() -> salesService.synchronizeSalesConfiguration(conflicting))
          .isInstanceOfSatisfying(
              BusinessException.class,
              ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_ID_CONFLICT));
      assertThat(
              jdbc.queryForObject(
                  "select payload_hash from booking_schema.tbl_inbox_messages where message_id=?",
                  String.class,
                  MESSAGE))
          .isEqualTo(originalHash);
      assertThat(
              jdbc.queryForObject(
                  "select row_version from booking_schema.tbl_inbox_messages where message_id=?",
                  Long.class,
                  MESSAGE))
          .isZero();
      // Receipt ID gán sẵn/version null phải INSERT và fail, không merge đè hash cũ.
      var duplicate = new InboxMessage();
      var id = new InboxMessageId();
      id.setConsumerName("booking.sales-configured");
      id.setMessageId(MESSAGE);
      duplicate.setId(id);
      duplicate.setPayloadHash("different-hash");
      duplicate.setProcessedAt(NOW);
      var inboxRepository =
          context.getBean(
              com.flashticket.booking_service.messaging.repository.InboxMessageRepository.class);
      assertThatThrownBy(() -> inboxRepository.save(duplicate))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(
              jdbc.queryForObject(
                  "select payload_hash from booking_schema.tbl_inbox_messages where message_id=?",
                  String.class,
                  MESSAGE))
          .isEqualTo(originalHash);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void staleConfigurationDoesNotOverwriteNewerSnapshot() {
    try {
      // Test đếm toàn bảng cần database test rỗng, không phụ thuộc thứ tự chạy.
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var config = with(sales(), "sourceConfigVersion", "5");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "5", NOW, EVENT, null, null, config));
      var stale = with(config, "sourceConfigVersion", "3");
      stale =
          with(
              stale,
              "ticketTypes",
              List.of(with(config.ticketTypes().getFirst(), "configuredQuantity", "2")));
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              EVENT,
              "3",
              NOW,
              EVENT,
              null,
              null,
              stale));
      assertThat(snapshots.findById(EVENT).orElseThrow().getSourceConfigVersion()).isEqualTo(5L);
      assertThat(
              jdbc.queryForObject(
                  "select configured_capacity from booking_schema.tbl_ticket_type_inventory where ticket_type_id=?",
                  Long.class,
                  TICKET))
          .isEqualTo(20L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages", Long.class))
          .isEqualTo(2L);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void sameVersionWithDifferentPayloadIsRejected() {
    try {
      // Test đếm toàn bảng cần database test rỗng, không phụ thuộc thứ tự chạy.
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, sales());
      salesService.synchronizeSalesConfiguration(message);
      var conflicting =
          with(
              with(message, "messageId", UUID.randomUUID()),
              "data",
              with(sales(), "purchaseLimit", 6));
      assertThatThrownBy(() -> salesService.synchronizeSalesConfiguration(conflicting))
          .isInstanceOfSatisfying(
              BusinessException.class,
              ex ->
                  assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SALES_CONFIGURATION_CONFLICT));
      assertThat(snapshots.findById(EVENT).orElseThrow().getPurchaseLimit()).isEqualTo(5);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages", Long.class))
          .isEqualTo(1L);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void newerConfigurationPreservesHeldAndPurchasedCounters() {
    try {
      // Test đếm toàn bảng cần database test rỗng, không phụ thuộc thứ tự chạy.
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var config = with(sales(), "promotions", List.of(promotion()));
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, config));
      jdbc.update(
          "update booking_schema.tbl_ticket_type_inventory set held=3,purchased=7 where ticket_type_id=?",
          TICKET);
      jdbc.update(
          "update booking_schema.tbl_promotion_inventory set held=1,used=2 where promotion_id=?",
          MESSAGE);
      var updated =
          with(
              with(config, "sourceConfigVersion", "2"),
              "ticketTypes",
              List.of(with(config.ticketTypes().getFirst(), "configuredQuantity", "30")));
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              EVENT,
              "2",
              NOW,
              EVENT,
              null,
              null,
              updated));
      assertThat(
              jdbc.queryForObject(
                  "select configured_capacity-held-purchased from booking_schema.tbl_ticket_type_inventory where ticket_type_id=?",
                  Long.class,
                  TICKET))
          .isEqualTo(20L);
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  MESSAGE))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "select used from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  MESSAGE))
          .isEqualTo(2L);
      var invalid =
          with(
              with(updated, "sourceConfigVersion", "3"),
              "ticketTypes",
              List.of(with(config.ticketTypes().getFirst(), "configuredQuantity", "5")));
      assertThatThrownBy(
              () ->
                  salesService.synchronizeSalesConfiguration(
                      new Envelope<>(
                          UUID.randomUUID(),
                          "event.sales-configured.v1",
                          1,
                          EVENT,
                          "3",
                          NOW,
                          EVENT,
                          null,
                          null,
                          invalid)))
          .isInstanceOf(BusinessException.class);
      assertThat(snapshots.findById(EVENT).orElseThrow().getSourceConfigVersion()).isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages", Long.class))
          .isEqualTo(2L);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentConfigurationsKeepTheNewestCommittedVersion() throws Exception {
    try {
      // Test đếm toàn bảng cần database test rỗng, không phụ thuộc thứ tự chạy.
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var futures = new ArrayList<Future<?>>();
        for (String version : List.of("3", "5")) {
          var config = with(sales(), "sourceConfigVersion", version);
          var message =
              new Envelope<>(
                  UUID.randomUUID(),
                  "event.sales-configured.v1",
                  1,
                  EVENT,
                  version,
                  NOW,
                  EVENT,
                  null,
                  null,
                  config);
          futures.add(
              executor.submit(
                  () -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS))
                      throw new AssertionError("Test start barrier timed out");
                    try {
                      salesService.synchronizeSalesConfiguration(message);
                    } catch (DataIntegrityViolationException | ConcurrencyFailureException race) {
                      // Mô phỏng redelivery: gọi lại proxy để mở transaction mới sau rollback.
                      salesService.synchronizeSalesConfiguration(message);
                    }
                    return null;
                  }));
        }
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (var future : futures) future.get(20, TimeUnit.SECONDS);
      }
      assertThat(snapshots.findById(EVENT).orElseThrow().getSourceConfigVersion()).isEqualTo(5L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_event_sales_snapshots", Long.class))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages", Long.class))
          .isEqualTo(2L);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void configurationCannotRemoveReservedSeatsOrReviveCancelledEvent() {
    try {
      var keys = new TransactionTemplate(transactionManager).execute(status -> persistGraph());
      UUID reservation = (UUID) keys.get("reservationRepository");
      jdbc.update(
          "update booking_schema.tbl_seat_inventory set state='HELD',current_reservation_id=?,reservation_generation=1 where seat_id=?",
          reservation,
          SEAT);
      var config =
          with(
              snapshots.findById(EVENT).orElseThrow().getConfigurationSnapshot(),
              "sourceConfigVersion",
              "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              EVENT,
              "2",
              NOW,
              EVENT,
              null,
              null,
              config));
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_seat_inventory where seat_id=?",
                  String.class,
                  SEAT))
          .isEqualTo("HELD");
      assertThat(
              jdbc.queryForObject(
                  "select current_reservation_id from booking_schema.tbl_seat_inventory where seat_id=?",
                  UUID.class,
                  SEAT))
          .isEqualTo(reservation);
      assertThat(
              jdbc.queryForObject(
                  "select reservation_generation from booking_schema.tbl_seat_inventory where seat_id=?",
                  Long.class,
                  SEAT))
          .isEqualTo(1L);
      var removed = with(with(config, "sourceConfigVersion", "3"), "seats", List.of());
      assertThatThrownBy(
              () ->
                  salesService.synchronizeSalesConfiguration(
                      new Envelope<>(
                          UUID.randomUUID(),
                          "event.sales-configured.v1",
                          1,
                          EVENT,
                          "3",
                          NOW,
                          EVENT,
                          null,
                          null,
                          removed)))
          .isInstanceOf(BusinessException.class);
      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
          EVENT);
      var revived = with(config, "sourceConfigVersion", "3");
      assertThatThrownBy(
              () ->
                  salesService.synchronizeSalesConfiguration(
                      new Envelope<>(
                          UUID.randomUUID(),
                          "event.sales-configured.v1",
                          1,
                          EVENT,
                          "3",
                          NOW,
                          EVENT,
                          null,
                          null,
                          revived)))
          .isInstanceOf(BusinessException.class);
      assertThat(snapshots.findById(EVENT).orElseThrow().getEventStatus())
          .isEqualTo(EventState.CANCELLED);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void snapshotRowVersionIsManagedByJpa() {
    try {
      // Mô phỏng DB trước thay đổi: có receipt/snapshot cũ nhưng chưa có cột version.
      jdbc.execute("alter table booking_schema.tbl_event_sales_snapshots drop column row_version");
      jdbc.execute("alter table booking_schema.tbl_inbox_messages drop column row_version");
      seedSqlEvent();
      jdbc.update(
          "insert into booking_schema.tbl_inbox_messages(consumer_name,message_id,payload_hash) values ('legacy',?,'old-hash')",
          MESSAGE);
      var updater = new org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean();
      updater.setDataSource(context.getBean(javax.sql.DataSource.class));
      updater.setJpaVendorAdapter(
          new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter());
      updater.setManagedTypes(
          org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(
              EventSalesSnapshot.class.getName(), InboxMessage.class.getName()));
      updater.setJpaPropertyMap(
          Map.of(
              "hibernate.hbm2ddl.auto",
              "update",
              "hibernate.default_schema",
              "booking_schema",
              "hibernate.type.json_format_mapper",
              "jackson3"));
      try {
        updater.afterPropertiesSet();
        assertThat(
                jdbc.queryForObject(
                    "select row_version from booking_schema.tbl_event_sales_snapshots where event_id=?",
                    Long.class,
                    EVENT))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select row_version from booking_schema.tbl_inbox_messages where message_id=?",
                    Long.class,
                    MESSAGE))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from information_schema.columns where table_schema='booking_schema' and table_name in ('tbl_event_sales_snapshots','tbl_inbox_messages') and column_name='row_version' and is_nullable='NO' and column_default='0'",
                    Long.class))
            .isEqualTo(2L);
      } finally {
        updater.destroy();
      }
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      var message =
          new Envelope<>(
              MESSAGE, "event.sales-configured.v1", 1, EVENT, "1", NOW, EVENT, null, null, sales());
      salesService.synchronizeSalesConfiguration(message);
      var stale = snapshots.findById(EVENT).orElseThrow();
      assertThat(stale.getRowVersion()).isZero();
      var updated = with(sales(), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              EVENT,
              "2",
              NOW,
              EVENT,
              null,
              null,
              updated));
      var current = snapshots.findById(EVENT).orElseThrow();
      assertThat(current.getSourceConfigVersion()).isEqualTo(2L);
      assertThat(current.getRowVersion()).isGreaterThan(stale.getRowVersion());
      stale.setPurchaseLimit(9);
      assertThatThrownBy(() -> snapshots.save(stale))
          .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
      // Test này DROP/ADD cột thật: bỏ connection cache plan cũ trước test tiếp theo.
      context
          .getBean(com.zaxxer.hikari.HikariDataSource.class)
          .getHikariPoolMXBean()
          .softEvictConnections();
    }
  }

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

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void createsQuantityOrderAndHoldsTicketTypeInventory() {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
    UUID key = UUID.randomUUID();
    var result = orderService.createOrder("buyer-quantity", key, createOrderRequest(config, 2));
    assertThat(result.getState()).isEqualTo(OrderState.PENDING_PAYMENT);
    assertThat(result.getSubtotal()).isEqualTo("200");
    assertThat(result.getItems()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_ticket_type_inventory where ticket_type_id=?",
                Long.class,
                config.ticketTypes().getFirst().ticketTypeId()))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject=?",
                Integer.class,
                config.eventId(),
                "buyer-quantity"))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_reservation_allocations where event_id=? and resource_kind='TICKET_TYPE' and quantity=2",
                Long.class,
                config.eventId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_idempotency_requests where actor_subject=? and request_key=? and resource_id=?",
                Long.class,
                "buyer-quantity",
                key,
                result.getOrderId()))
        .isEqualTo(1L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void standingTicketTypesShareOneSectorPool() {
    var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.STANDING, 20, 10);
    var selection =
        new OrderSelection.StandingSelection(
            config.sectors().getFirst().sectorId(),
            List.of(
                new OrderSelection.QuantityItem(config.ticketTypes().get(0).ticketTypeId(), 2),
                new OrderSelection.QuantityItem(config.ticketTypes().get(1).ticketTypeId(), 3)));
    var request = new CreateOrderRequest(config.eventId(), "buyer@example.com", selection);
    var result = orderService.createOrder("buyer-standing", UUID.randomUUID(), request);
    assertThat(result.getItems()).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_sector_inventory where sector_id=?",
                Long.class,
                config.sectors().getFirst().sectorId()))
        .isEqualTo(5L);
    assertThat(
            jdbc.queryForObject(
                "select sum(quantity) from booking_schema.tbl_reservation_allocations where event_id=? and resource_kind='STANDING_SECTOR'",
                Long.class,
                config.eventId()))
        .isEqualTo(5L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_ticket_type_inventory where event_id=?",
                Long.class,
                config.eventId()))
        .isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void seatedOrderGroupsItemsAndHoldsIndividualSeats() {
    var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 3, 5);
    var result =
        orderService.createOrder("buyer-seated", UUID.randomUUID(), createOrderRequest(config, 2));
    assertThat(result.getItems()).hasSize(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and state='HELD' and current_reservation_id is not null and reservation_generation=1",
                Long.class,
                config.eventId()))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_order_item_seats where event_id=?",
                Long.class,
                config.eventId()))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_reservation_allocations where event_id=? and resource_kind='SEAT' and quantity=1",
                Long.class,
                config.eventId()))
        .isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_sector_inventory where sector_id=?",
                Long.class,
                config.sectors().getFirst().sectorId()))
        .isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsInvalidSelectionsWithoutPartialHolds() {
    var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 3, 5);
    var request = createOrderRequest(config, 1);
    orderService.createOrder("seat-owner", UUID.randomUUID(), request);
    var before = captureOrderCreationState(config.eventId());
    assertThatThrownBy(() -> orderService.createOrder("other-buyer", UUID.randomUUID(), request))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SEAT_UNAVAILABLE));
    var wrongSector =
        new CreateOrderRequest(
            config.eventId(),
            "buyer@example.com",
            new OrderSelection.SeatedSelection(
                UUID.randomUUID(), List.of(config.seats().getFirst().seatId())));
    assertThatThrownBy(
            () -> orderService.createOrder("other-buyer", UUID.randomUUID(), wrongSector))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_SELECTION));
    assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsOrdersOutsidePublishedSalesWindow() {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    jdbc.update(
        "update booking_schema.tbl_event_sales_snapshots set event_status='DRAFT' where event_id=?",
        config.eventId());
    var before = captureOrderCreationState(config.eventId());
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    "buyer-closed", UUID.randomUUID(), createOrderRequest(config, 1)))
        .isInstanceOf(BusinessException.class);
    assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
    jdbc.update(
        "update booking_schema.tbl_event_sales_snapshots set event_status='PUBLISHED', sale_ends_at=clock_timestamp()-interval '1 second' where event_id=?",
        config.eventId());
    assertThatThrownBy(
            () ->
                orderService.createOrder(
                    "buyer-closed", UUID.randomUUID(), createOrderRequest(config, 1)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.EVENT_NOT_ON_SALE));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replayReturnsTheSameOrderWithoutExtendingExpiry() {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    var request = createOrderRequest(config, 2);
    UUID key = UUID.randomUUID();
    var first = orderService.createOrder("buyer-replay", key, request);
    var before = captureOrderCreationState(config.eventId());
    var replay = orderService.createOrder("buyer-replay", key, request);
    assertThat(replay.getOrderId()).isEqualTo(first.getOrderId());
    assertThat(replay.getExpiresAt()).isEqualTo(first.getExpiresAt());
    assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void reusedKeyWithDifferentInputIsRejected() {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    UUID key = UUID.randomUUID();
    orderService.createOrder("buyer-conflict", key, createOrderRequest(config, 1));
    var before = captureOrderCreationState(config.eventId());
    assertThatThrownBy(
            () -> orderService.createOrder("buyer-conflict", key, createOrderRequest(config, 2)))
        .isInstanceOf(BusinessException.class)
        .satisfies(
            ex ->
                assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT));
    assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void idempotencyKeysAreScopedByBuyerAndOperation() {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    UUID key = UUID.randomUUID();
    var request = createOrderRequest(config, 1);
    var first = orderService.createOrder("buyer-one", key, request);
    var second = orderService.createOrder("buyer-two", key, request);
    assertThat(second.getOrderId()).isNotEqualTo(first.getOrderId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_idempotency_requests where request_key=?",
                Long.class,
                key))
        .isEqualTo(2L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentSameKeyCreatesOnlyOneOrder() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    var request = createOrderRequest(config, 1);
    UUID key = UUID.randomUUID();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return orderService.createOrder("buyer-race", key, request);
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return orderService.createOrder("buyer-race", key, request);
              });
      start.countDown();
      assertThat(first.get(15, TimeUnit.SECONDS).getOrderId())
          .isEqualTo(second.get(15, TimeUnit.SECONDS).getOrderId());
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_orders where event_id=?",
                Long.class,
                config.eventId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_ticket_type_inventory where ticket_type_id=?",
                Long.class,
                config.ticketTypes().getFirst().ticketTypeId()))
        .isEqualTo(1L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentSameKeyAcrossEventsCannotOverwriteReceipt() throws Exception {
    var firstConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    var secondConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    UUID key = UUID.randomUUID();
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                try {
                  return orderService.createOrder(
                      "buyer-cross-event", key, createOrderRequest(firstConfig, 1));
                } catch (BusinessException ex) {
                  return ex;
                }
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                try {
                  return orderService.createOrder(
                      "buyer-cross-event", key, createOrderRequest(secondConfig, 1));
                } catch (BusinessException ex) {
                  return ex;
                }
              });
      start.countDown();
      var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
      assertThat(outcomes.stream().filter(OrderView.class::isInstance)).hasSize(1);
      assertThat(
              outcomes.stream()
                  .filter(BusinessException.class::isInstance)
                  .map(BusinessException.class::cast)
                  .map(BusinessException::getErrorCode))
          .containsExactly(ErrorCode.IDEMPOTENCY_CONFLICT);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_idempotency_requests where actor_subject=? and request_key=?",
                Long.class,
                "buyer-cross-event",
                key))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_orders where event_id in (?, ?)",
                Long.class,
                firstConfig.eventId(),
                secondConfig.eventId()))
        .isEqualTo(1L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRequestsCannotHoldTheSameSeat() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 3, 5);
    var request = createOrderRequest(config, 1);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                try {
                  return orderService.createOrder("seat-buyer-one", UUID.randomUUID(), request);
                } catch (RuntimeException ex) {
                  return ex;
                }
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                try {
                  return orderService.createOrder("seat-buyer-two", UUID.randomUUID(), request);
                } catch (RuntimeException ex) {
                  return ex;
                }
              });
      start.countDown();
      var outcomes = List.of(first.get(45, TimeUnit.SECONDS), second.get(45, TimeUnit.SECONDS));
      assertThat(outcomes.stream().filter(OrderView.class::isInstance)).hasSize(1);
      assertThat(
              outcomes.stream()
                  .filter(RuntimeException.class::isInstance)
                  .map(RuntimeException.class::cast)
                  .map(ErrorCode::from))
          .singleElement()
          .isIn(ErrorCode.SEAT_UNAVAILABLE, ErrorCode.RESOURCE_BUSY);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_orders where event_id=?",
                Long.class,
                config.eventId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and state='HELD'",
                Long.class,
                config.eventId()))
        .isEqualTo(1L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void lockTimeoutIsScopedToOrderTransaction() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 2, 5);
    var before = captureOrderCreationState(config.eventId());
    String originalTimeout = jdbc.queryForObject("show lock_timeout", String.class);
    try (var connection = jdbc.getDataSource().getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "select seat_id from booking_schema.tbl_seat_inventory where seat_id=? for update")) {
        statement.setObject(1, config.seats().getFirst().seatId());
        statement.executeQuery();
      }
      long started = System.nanoTime();
      Throwable failure =
          catchThrowable(
              () ->
                  orderService.createOrder(
                      "buyer-timeout", UUID.randomUUID(), createOrderRequest(config, 1)));
      long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);
      assertThat(failure).isNotNull();
      assertThat(ErrorCode.from(failure)).isEqualTo(ErrorCode.RESOURCE_BUSY);
      assertThat(elapsedSeconds).isBetween(4L, 12L);
      connection.rollback();
    }
    assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
    assertThat(jdbc.queryForObject("show lock_timeout", String.class)).isEqualTo(originalTimeout);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              orders.configureTransactionLockTimeout("5s");
              assertThat(jdbc.queryForObject("show lock_timeout", String.class)).isEqualTo("5s");
            });
    assertThat(jdbc.queryForObject("show lock_timeout", String.class)).isEqualTo(originalTimeout);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRequestsCannotOversellQuantityOrStandingPool() throws Exception {
    for (var mode : List.of(SalesMode.QUANTITY, SalesMode.SEAT_MAP)) {
      var config =
          seedOrderCreationConfiguration(
              mode, mode == SalesMode.SEAT_MAP ? SectorType.STANDING : null, 1, 5);
      var request = createOrderRequest(config, 1);
      var start = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var first =
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return orderService.createOrder("pool-buyer-one", UUID.randomUUID(), request);
                  } catch (RuntimeException ex) {
                    return ex;
                  }
                });
        var second =
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return orderService.createOrder("pool-buyer-two", UUID.randomUUID(), request);
                  } catch (RuntimeException ex) {
                    return ex;
                  }
                });
        start.countDown();
        var results = List.of(first.get(45, TimeUnit.SECONDS), second.get(45, TimeUnit.SECONDS));
        assertThat(results.stream().filter(OrderView.class::isInstance)).hasSize(1);
        assertThat(
                results.stream()
                    .filter(RuntimeException.class::isInstance)
                    .map(RuntimeException.class::cast)
                    .map(ErrorCode::from))
            .singleElement()
            .isIn(ErrorCode.INSUFFICIENT_QUANTITY, ErrorCode.RESOURCE_BUSY);
      }
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_orders where event_id=?",
                  Long.class,
                  config.eventId()))
          .isEqualTo(1L);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentOrdersCannotExceedBuyerPurchaseLimit() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 1);
    var request = createOrderRequest(config, 1);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                try {
                  return orderService.createOrder("buyer-limit", UUID.randomUUID(), request);
                } catch (RuntimeException ex) {
                  return ex;
                }
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                try {
                  return orderService.createOrder("buyer-limit", UUID.randomUUID(), request);
                } catch (RuntimeException ex) {
                  return ex;
                }
              });
      start.countDown();
      var results = List.of(first.get(45, TimeUnit.SECONDS), second.get(45, TimeUnit.SECONDS));
      assertThat(results.stream().filter(OrderView.class::isInstance)).hasSize(1);
      assertThat(
              results.stream()
                  .filter(RuntimeException.class::isInstance)
                  .map(RuntimeException.class::cast)
                  .map(ErrorCode::from))
          .singleElement()
          .isIn(ErrorCode.PURCHASE_LIMIT_EXCEEDED, ErrorCode.RESOURCE_BUSY);
    }
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject=?",
                Integer.class,
                config.eventId(),
                "buyer-limit"))
        .isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedCommitRollsBackOrderReservationInventoryAndReceipt() {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    var before = captureOrderCreationState(config.eventId());
    jdbc.execute(
        "alter table booking_schema.tbl_idempotency_requests add constraint test_receipt_failure check (actor_subject <> 'rollback-case')");
    try {
      assertThatThrownBy(
              () ->
                  orderService.createOrder(
                      "rollback-case", UUID.randomUUID(), createOrderRequest(config, 1)))
          .isInstanceOf(RuntimeException.class);
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
    } finally {
      jdbc.execute(
          "alter table booking_schema.tbl_idempotency_requests drop constraint test_receipt_failure");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void changingConfigurationDoesNotRepriceExistingOrder() {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    var first =
        orderService.createOrder(
            "buyer-old-price", UUID.randomUUID(), createOrderRequest(config, 1));
    var newType = with(config.ticketTypes().getFirst(), "unitPrice", "300");
    var updated = with(with(config, "sourceConfigVersion", "2"), "ticketTypes", List.of(newType));
    salesService.synchronizeSalesConfiguration(
        new Envelope<>(
            UUID.randomUUID(),
            "event.sales-configured.v1",
            1,
            config.eventId(),
            "2",
            Instant.now(),
            UUID.randomUUID(),
            null,
            null,
            updated));
    var second =
        orderService.createOrder(
            "buyer-new-price", UUID.randomUUID(), createOrderRequest(updated, 1));
    assertThat(first.getSubtotal()).isEqualTo("100");
    assertThat(second.getSubtotal()).isEqualTo("300");
    assertThat(
            jdbc.queryForObject(
                "select subtotal from booking_schema.tbl_orders where id=?",
                BigDecimal.class,
                first.getOrderId()))
        .isEqualByComparingTo("100");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentFirstOrdersCreateOnePurchaseLimit() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 5);
    var request = createOrderRequest(config, 1);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return orderService.createOrder("buyer-first-race", UUID.randomUUID(), request);
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return orderService.createOrder("buyer-first-race", UUID.randomUUID(), request);
              });
      start.countDown();
      assertThat(first.get(45, TimeUnit.SECONDS).getOrderId())
          .isNotEqualTo(second.get(45, TimeUnit.SECONDS).getOrderId());
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject=?",
                Long.class,
                config.eventId(),
                "buyer-first-race"))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject=?",
                Integer.class,
                config.eventId(),
                "buyer-first-race"))
        .isEqualTo(2);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replayAfterWaitingForPurchaseLimitReturnsOriginalOrder() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 10, 1);
    jdbc.update(
        "insert into booking_schema.tbl_purchase_limits (event_id,buyer_subject,configured_limit,held,purchased,source_config_version,row_version) values (?,?,1,0,0,1,0)",
        config.eventId(),
        "buyer-replay-wait");
    var request = createOrderRequest(config, 1);
    UUID key = UUID.randomUUID();
    try (var connection = jdbc.getDataSource().getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "select event_id from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject=? for update")) {
        statement.setObject(1, config.eventId());
        statement.setString(2, "buyer-replay-wait");
        statement.executeQuery();
      }
      var first =
          executor.submit(() -> orderService.createOrder("buyer-replay-wait", key, request));
      var second =
          executor.submit(() -> orderService.createOrder("buyer-replay-wait", key, request));
      // Xác nhận request đã thực sự chờ khóa trước khi thả transaction giữ row.
      Awaitility.await()
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofMillis(100))
          .until(
              () -> {
                try (var statement =
                    connection.prepareStatement(
                        "select exists (select 1 from pg_locks waiting join pg_locks relation_lock on relation_lock.pid=waiting.pid where not waiting.granted and relation_lock.relation=to_regclass(?))")) {
                  statement.setString(1, "booking_schema." + "tbl_purchase_limits");
                  try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getBoolean(1);
                  }
                }
              });
      connection.commit();
      assertThat(first.get(45, TimeUnit.SECONDS).getOrderId())
          .isEqualTo(second.get(45, TimeUnit.SECONDS).getOrderId());
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_orders where event_id=?",
                Long.class,
                config.eventId()))
        .isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "select held from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject=?",
                Integer.class,
                config.eventId(),
                "buyer-replay-wait"))
        .isEqualTo(1);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void differentSeatsCanBeReservedConcurrently() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 2, 5);
    var requestA = createOrderRequest(config, 1);
    var requestB =
        new CreateOrderRequest(
            config.eventId(),
            "buyer@example.com",
            new OrderSelection.SeatedSelection(
                config.sectors().getFirst().sectorId(), List.of(config.seats().get(1).seatId())));
    try (var connection = jdbc.getDataSource().getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "select seat_id from booking_schema.tbl_seat_inventory where seat_id=? for update")) {
        statement.setObject(1, config.seats().getFirst().seatId());
        statement.executeQuery();
      }
      var first =
          executor.submit(
              () -> orderService.createOrder("buyer-seat-a", UUID.randomUUID(), requestA));
      Awaitility.await()
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofMillis(100))
          .until(
              () -> {
                try (var statement =
                    connection.prepareStatement(
                        "select exists (select 1 from pg_locks waiting join pg_locks relation_lock on relation_lock.pid=waiting.pid where not waiting.granted and relation_lock.relation=to_regclass(?))")) {
                  statement.setString(1, "booking_schema." + "tbl_seat_inventory");
                  try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getBoolean(1);
                  }
                }
              });
      var second =
          executor.submit(
              () -> orderService.createOrder("buyer-seat-b", UUID.randomUUID(), requestB));
      var secondOrder = second.get(45, TimeUnit.SECONDS);
      // Ghế B đã commit khi khóa ghế A vẫn được transaction test giữ.
      assertThat(connection.getAutoCommit()).isFalse();
      assertThat(secondOrder.getOrderId()).isNotNull();
      connection.commit();
      assertThat(first.get(45, TimeUnit.SECONDS).getOrderId())
          .isNotEqualTo(secondOrder.getOrderId());
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and state='HELD'",
                Long.class,
                config.eventId()))
        .isEqualTo(2L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void configurationUpdateWaitsForActiveBooking() throws Exception {
    var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 2, 5);
    var updated = with(with(config, "sourceConfigVersion", "2"), "eventTitle", "Updated concert");
    var message =
        new Envelope<>(
            UUID.randomUUID(),
            "event.sales-configured.v1",
            1,
            config.eventId(),
            "2",
            Instant.now(),
            UUID.randomUUID(),
            null,
            null,
            updated);
    try (var connection = jdbc.getDataSource().getConnection();
        var executor = Executors.newFixedThreadPool(2)) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "select seat_id from booking_schema.tbl_seat_inventory where seat_id=? for update")) {
        statement.setObject(1, config.seats().getFirst().seatId());
        statement.executeQuery();
      }
      var booking =
          executor.submit(
              () ->
                  orderService.createOrder(
                      "buyer-config-wait", UUID.randomUUID(), createOrderRequest(config, 1)));
      Awaitility.await()
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofMillis(100))
          .until(
              () -> {
                try (var statement =
                    connection.prepareStatement(
                        "select exists (select 1 from pg_locks waiting join pg_locks relation_lock on relation_lock.pid=waiting.pid where not waiting.granted and relation_lock.relation=to_regclass(?))")) {
                  statement.setString(1, "booking_schema." + "tbl_seat_inventory");
                  try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getBoolean(1);
                  }
                }
              });
      var consumer =
          executor.submit(
              () -> {
                salesService.synchronizeSalesConfiguration(message);
                return null;
              });
      Awaitility.await()
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofMillis(100))
          .until(
              () -> {
                try (var statement =
                    connection.prepareStatement(
                        "select exists (select 1 from pg_locks waiting join pg_locks relation_lock on relation_lock.pid=waiting.pid where not waiting.granted and relation_lock.relation=to_regclass(?))")) {
                  statement.setString(1, "booking_schema." + "tbl_event_sales_snapshots");
                  try (var result = statement.executeQuery()) {
                    result.next();
                    return result.getBoolean(1);
                  }
                }
              });
      assertThat(consumer.isDone()).isFalse();
      connection.commit();
      assertThat(booking.get(45, TimeUnit.SECONDS).getOrderId()).isNotNull();
      consumer.get(45, TimeUnit.SECONDS);
    }
    assertThat(
            jdbc.queryForObject(
                "select source_config_version from booking_schema.tbl_event_sales_snapshots where event_id=?",
                Long.class,
                config.eventId()))
        .isEqualTo(2L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void saleDecisionUsesDatabaseTimeAfterWaitingForSnapshotLock() throws Exception {
    for (boolean lockSnapshot : List.of(true, false)) {
      var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 2, 5);
      try (var connection = jdbc.getDataSource().getConnection();
          var executor = Executors.newSingleThreadExecutor()) {
        connection.setAutoCommit(false);
        String lockSql =
            lockSnapshot
                ? "select event_id from booking_schema.tbl_event_sales_snapshots where event_id=? for update"
                : "select seat_id from booking_schema.tbl_seat_inventory where seat_id=? for update";
        try (var statement = connection.prepareStatement(lockSql)) {
          statement.setObject(
              1, lockSnapshot ? config.eventId() : config.seats().getFirst().seatId());
          statement.executeQuery();
        }
        // Đặt giờ đóng bán ở DB và giữ khóa để request phải chờ qua mốc này.
        if (lockSnapshot) {
          try (var statement =
              connection.prepareStatement(
                  "update booking_schema.tbl_event_sales_snapshots set sale_ends_at=clock_timestamp()+interval '3 seconds' where event_id=?")) {
            statement.setObject(1, config.eventId());
            statement.executeUpdate();
          }
        } else {
          jdbc.update(
              "update booking_schema.tbl_event_sales_snapshots set sale_ends_at=clock_timestamp()+interval '3 seconds' where event_id=?",
              config.eventId());
        }
        var order =
            executor.submit(
                () ->
                    orderService.createOrder(
                        "buyer-clock", UUID.randomUUID(), createOrderRequest(config, 1)));
        String table = lockSnapshot ? "tbl_event_sales_snapshots" : "tbl_seat_inventory";
        Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofMillis(100))
            .until(
                () -> {
                  try (var statement =
                      connection.prepareStatement(
                          "select exists (select 1 from pg_locks waiting join pg_locks relation_lock on relation_lock.pid=waiting.pid where not waiting.granted and relation_lock.relation=to_regclass(?))")) {
                    statement.setString(1, "booking_schema." + table);
                    try (var result = statement.executeQuery()) {
                      result.next();
                      return result.getBoolean(1);
                    }
                  }
                });
        if (lockSnapshot) {
          // Request đã bắt đầu và đang chờ khóa: đóng bán ngay trước khi nhả khóa.
          // Không phải chờ sát timeout 5s chỉ để tạo điều kiện hết giờ bán.
          try (var statement =
              connection.prepareStatement(
                  "update booking_schema.tbl_event_sales_snapshots set sale_ends_at=clock_timestamp() where event_id=?")) {
            statement.setObject(1, config.eventId());
            statement.executeUpdate();
          }
        } else {
          Awaitility.await()
              .atMost(Duration.ofSeconds(4))
              .pollInterval(Duration.ofMillis(100))
              .until(
                  () ->
                      Boolean.TRUE.equals(
                          jdbc.queryForObject(
                              "select clock_timestamp() >= sale_ends_at from booking_schema.tbl_event_sales_snapshots where event_id=?",
                              Boolean.class,
                              config.eventId())));
        }
        connection.commit();
        assertThatThrownBy(() -> order.get(45, TimeUnit.SECONDS))
            .hasCauseInstanceOf(BusinessException.class)
            .satisfies(
                ex ->
                    assertThat(((BusinessException) ex.getCause()).getErrorCode())
                        .isEqualTo(ErrorCode.EVENT_NOT_ON_SALE));
      }
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_orders where event_id=?",
                  Long.class,
                  config.eventId()))
          .isZero();
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void appliesFixedAndPercentPromotionAtomically() throws Exception {
    try {

      for (int scenario = 0; scenario < 3; scenario++) {
        boolean percent = scenario != 0;

        var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        if (scenario == 2)
          baseConfig =
              with(
                  baseConfig,
                  "ticketTypes",
                  List.of(with(baseConfig.ticketTypes().getFirst(), "unitPrice", "100005")));
        var promotionConfig =
            new PromotionConfig(
                UUID.randomUUID(),
                "SAVE",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                "2",
                1,
                (percent
                    ? new PromotionConfig.PercentDiscount("PERCENT", 10)
                    : new PromotionConfig.FixedDiscount("FIXED", "10")));
        var config =
            with(
                with(baseConfig, "promotions", List.of(promotionConfig)),
                "sourceConfigVersion",
                "2");
        salesService.synchronizeSalesConfiguration(
            new Envelope<>(
                UUID.randomUUID(),
                "event.sales-configured.v1",
                1,
                config.eventId(),
                "2",
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                config));
        var order =
            orderService.createOrder(
                "buyer", UUID.randomUUID(), createOrderRequest(config, scenario == 2 ? 1 : 2));

        var result =
            promotionService.applyPromotionToOrder(
                "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
        assertThat(result.getDiscount()).isEqualTo(scenario == 2 ? "10001" : percent ? "20" : "10");
        assertThat(result.getTotal()).isEqualTo(scenario == 2 ? "90004" : percent ? "180" : "190");
        assertThat(result.getExpiresAt()).isEqualTo(order.getExpiresAt());
        assertThat(
                jdbc.queryForObject(
                    "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                    Long.class,
                    promotionConfig.promotionId()))
            .isEqualTo(1L);
        assertThat(
                jdbc.queryForObject(
                    "select state from booking_schema.tbl_promotion_usages where promotion_id=?",
                    String.class,
                    promotionConfig.promotionId()))
            .isEqualTo("HELD");
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsPromotionBusinessViolations() throws Exception {
    try {

      for (var expected :
          List.of(
              ErrorCode.PROMOTION_NOT_FOUND,
              ErrorCode.PROMOTION_OUTSIDE_WINDOW,
              ErrorCode.PROMOTION_EXHAUSTED,
              ErrorCode.PROMOTION_ALREADY_USED,
              ErrorCode.ORDER_ALREADY_HAS_PROMOTION,
              ErrorCode.NON_POSITIVE_TOTAL)) {

        var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var promotionConfig =
            new PromotionConfig(
                UUID.randomUUID(),
                "SAVE",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                "2",
                1,
                (expected == ErrorCode.NON_POSITIVE_TOTAL
                    ? new PromotionConfig.FixedDiscount("FIXED", "200")
                    : new PromotionConfig.PercentDiscount("PERCENT", 10)));
        var config =
            with(
                with(baseConfig, "promotions", List.of(promotionConfig)),
                "sourceConfigVersion",
                "2");
        salesService.synchronizeSalesConfiguration(
            new Envelope<>(
                UUID.randomUUID(),
                "event.sales-configured.v1",
                1,
                config.eventId(),
                "2",
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                config));
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

        String code = "SAVE";
        if (expected == ErrorCode.PROMOTION_NOT_FOUND) code = "MISSING";
        if (expected == ErrorCode.PROMOTION_OUTSIDE_WINDOW)
          jdbc.update(
              "update booking_schema.tbl_promotion_inventory set starts_at=clock_timestamp()+interval '1 hour',ends_at=clock_timestamp()+interval '2 hours' where promotion_id=?",
              promotionConfig.promotionId());
        if (expected == ErrorCode.PROMOTION_EXHAUSTED)
          jdbc.update(
              "update booking_schema.tbl_promotion_inventory set used=configured_limit where promotion_id=?",
              promotionConfig.promotionId());
        if (expected == ErrorCode.PROMOTION_ALREADY_USED) {
          var other =
              orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 1));
          promotionService.applyPromotionToOrder(
              "buyer", other.getOrderId(), new ApplyPromotionRequest("SAVE"));
        }
        if (expected == ErrorCode.ORDER_ALREADY_HAS_PROMOTION) {
          promotionService.applyPromotionToOrder(
              "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
          code = "OTHER";
        }
        String requestedCode = code;
        long before =
            jdbc.queryForObject(
                "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                Long.class,
                promotionConfig.promotionId());
        assertThatThrownBy(
                () ->
                    promotionService.applyPromotionToOrder(
                        "buyer", order.getOrderId(), new ApplyPromotionRequest(requestedCode)))
            .isInstanceOfSatisfying(
                BusinessException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        assertThat(
                jdbc.queryForObject(
                    "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                    Long.class,
                    promotionConfig.promotionId()))
            .isEqualTo(before);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replaysSamePromotionWithoutHoldingAgain() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      var first =
          promotionService.applyPromotionToOrder(
              "buyer", order.getOrderId(), new ApplyPromotionRequest(" SAVE "));
      var replay =
          promotionService.applyPromotionToOrder(
              "buyer", order.getOrderId(), new ApplyPromotionRequest("save"));
      assertThat(replay.getTotal()).isEqualTo(first.getTotal());
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_promotion_usages where promotion_id=?",
                  Long.class,
                  promotionConfig.promotionId()))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotionConfig.promotionId()))
          .isEqualTo(1L);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsPromotionForUnpayableOrForeignOrder() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      assertThatThrownBy(
              () ->
                  promotionService.applyPromotionToOrder(
                      "other", order.getOrderId(), new ApplyPromotionRequest("SAVE")))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
      orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      assertThatThrownBy(
              () ->
                  promotionService.applyPromotionToOrder(
                      "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE")))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_FROZEN));
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotionConfig.promotionId()))
          .isZero();

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesLastPromotionUseAcrossOrders() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "1",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      var other =
          orderService.createOrder("other", UUID.randomUUID(), createOrderRequest(config, 1));
      var start = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var first =
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return promotionService.applyPromotionToOrder(
                        "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
                  } catch (RuntimeException e) {
                    return e;
                  }
                });
        var second =
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return promotionService.applyPromotionToOrder(
                        "other", other.getOrderId(), new ApplyPromotionRequest("SAVE"));
                  } catch (RuntimeException e) {
                    return e;
                  }
                });
        start.countDown();
        var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        assertThat(outcomes.stream().filter(OrderView.class::isInstance)).hasSize(1);
        assertThat(
                outcomes.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .map(BusinessException::getErrorCode))
            .containsExactly(ErrorCode.PROMOTION_EXHAUSTED);
      }
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotionConfig.promotionId()))
          .isEqualTo(1L);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void preventsSameBuyerHoldingPromotionOnTwoOrders() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      var other =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 1));
      var start = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var first =
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return promotionService.applyPromotionToOrder(
                        "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
                  } catch (RuntimeException e) {
                    return e;
                  }
                });
        var second =
            executor.submit(
                () -> {
                  start.await();
                  try {
                    return promotionService.applyPromotionToOrder(
                        "buyer", other.getOrderId(), new ApplyPromotionRequest("SAVE"));
                  } catch (RuntimeException e) {
                    return e;
                  }
                });
        start.countDown();
        var outcomes = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        assertThat(outcomes.stream().filter(OrderView.class::isInstance)).hasSize(1);
        assertThat(
                outcomes.stream()
                    .filter(BusinessException.class::isInstance)
                    .map(BusinessException.class::cast)
                    .map(BusinessException::getErrorCode))
            .containsExactly(ErrorCode.PROMOTION_ALREADY_USED);
      }
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotionConfig.promotionId()))
          .isEqualTo(1L);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollsBackPromotionWhenTransactionFails() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      assertThatThrownBy(
              () ->
                  new TransactionTemplate(transactionManager)
                      .execute(
                          status -> {
                            promotionService.applyPromotionToOrder(
                                "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
                            // Lỗi SQL sau các thay đổi thật: transaction phải rollback cả
                            // usage/counter/tiền.
                            jdbc.update(
                                "update booking_schema.tbl_orders set total=-1 where id=?",
                                order.getOrderId());
                            return null;
                          }))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotionConfig.promotionId()))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_promotion_usages where promotion_id=?",
                  Long.class,
                  promotionConfig.promotionId()))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select total from booking_schema.tbl_orders where id=?",
                  BigDecimal.class,
                  order.getOrderId()))
          .isEqualByComparingTo("200");

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void preparesSnapshotForEverySelectionMode() throws Exception {
    try {

      for (int mode = 0; mode < 3; mode++) {
        var config =
            seedOrderCreationConfiguration(
                mode == 0 ? SalesMode.QUANTITY : SalesMode.SEAT_MAP,
                mode == 0 ? null : mode == 1 ? SectorType.STANDING : SectorType.SEATED,
                10,
                5);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var snapshot = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        assertThat(snapshot.total()).isEqualTo(order.getTotal());
        assertThat(snapshot.promotion()).isNull();
        assertThat(snapshot.items().stream().mapToInt(PurchaseSnapshot.Item::quantity).sum())
            .isEqualTo(2);
        assertThat(snapshot.items().stream().flatMap(i -> i.seats().stream()))
            .hasSize(mode == 2 ? 2 : 0);
        assertThat(snapshot.expiresAt()).isEqualTo(order.getExpiresAt());
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replaysPreparedSnapshotWithoutChangingMoneyOrVersion() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      promotionService.applyPromotionToOrder(
          "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
      try (var executor = Executors.newFixedThreadPool(2)) {
        var a =
            executor.submit(
                () -> orderService.preparePurchaseSnapshot("buyer", order.getOrderId()));
        var b =
            executor.submit(
                () -> orderService.preparePurchaseSnapshot("buyer", order.getOrderId()));
        var first = a.get(15, TimeUnit.SECONDS);
        assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(first);
        assertThat(orderService.preparePurchaseSnapshot("buyer", order.getOrderId()))
            .isEqualTo(first);
        assertThat(first.sourceOrderVersion()).isEqualTo("1");
        assertThat(first.total()).isEqualTo("180");
        assertThat(first.promotion().discount()).isEqualTo("20");
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesPromotionAndSnapshotPreparation() throws Exception {
    try {

      for (boolean promotionFirst : List.of(true, false)) {

        var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var promotionConfig =
            new PromotionConfig(
                UUID.randomUUID(),
                "SAVE",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                "2",
                1,
                new PromotionConfig.PercentDiscount("PERCENT", 10));
        var config =
            with(
                with(baseConfig, "promotions", List.of(promotionConfig)),
                "sourceConfigVersion",
                "2");
        salesService.synchronizeSalesConfiguration(
            new Envelope<>(
                UUID.randomUUID(),
                "event.sales-configured.v1",
                1,
                config.eventId(),
                "2",
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                config));
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

        try (var executor = Executors.newSingleThreadExecutor()) {
          var waiting = new java.util.concurrent.atomic.AtomicReference<Future<Object>>();
          new TransactionTemplate(transactionManager)
              .executeWithoutResult(
                  status -> {
                    if (promotionFirst)
                      promotionService.applyPromotionToOrder(
                          "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
                    else orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
                    int blocker = jdbc.queryForObject("select pg_backend_pid()", Integer.class);
                    waiting.set(
                        executor.submit(
                            () -> {
                              try {
                                return promotionFirst
                                    ? orderService.preparePurchaseSnapshot(
                                        "buyer", order.getOrderId())
                                    : promotionService.applyPromotionToOrder(
                                        "buyer",
                                        order.getOrderId(),
                                        new ApplyPromotionRequest("SAVE"));
                              } catch (RuntimeException e) {
                                return e;
                              }
                            }));
                    Awaitility.await()
                        .atMost(Duration.ofSeconds(3))
                        .until(
                            () ->
                                jdbc.queryForObject(
                                    "select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))",
                                    Boolean.class,
                                    blocker));
                  });
          Object result = waiting.get().get(10, TimeUnit.SECONDS);
          if (promotionFirst) assertThat(((PurchaseSnapshot) result).total()).isEqualTo("180");
          else
            assertThat(result)
                .isInstanceOfSatisfying(
                    BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PRICE_FROZEN));
        }
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsSnapshotForExpiredCancelledOrForeignOrder() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      assertThatThrownBy(() -> orderService.preparePurchaseSnapshot("other", order.getOrderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
      orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      jdbc.update(
          "update booking_schema.tbl_orders set lifecycle_state='EXPIRED' where id=?",
          order.getOrderId());
      assertThatThrownBy(() -> orderService.preparePurchaseSnapshot("buyer", order.getOrderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE));
      jdbc.update(
          "update booking_schema.tbl_orders set lifecycle_state='PENDING_PAYMENT' where id=?",
          order.getOrderId());
      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
          config.eventId());
      assertThatThrownBy(() -> orderService.preparePurchaseSnapshot("buyer", order.getOrderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE));

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void preparesSnapshotWhenConfigurationVersionChangesWithoutRepricingOrder() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      var newer = with(config, "sourceConfigVersion", "3");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "3",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              newer));
      var snapshot = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      assertThat(snapshot.sourceConfigVersion()).isEqualTo("3");
      assertThat(snapshot.total()).isEqualTo("200");
      assertThat(
              jdbc.queryForObject(
                  "select source_config_version from booking_schema.tbl_orders where id=?",
                  Long.class,
                  order.getOrderId()))
          .isEqualTo(2L);
      var newest = with(newer, "sourceConfigVersion", "4");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "4",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              newest));
      assertThat(orderService.preparePurchaseSnapshot("buyer", order.getOrderId()))
          .isEqualTo(snapshot);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollsBackInvalidSnapshotPreparation() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      jdbc.update(
          "update booking_schema.tbl_orders set discount=5,total=195 where id=?",
          order.getOrderId());
      assertThatThrownBy(() -> orderService.preparePurchaseSnapshot("buyer", order.getOrderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFIG_NOT_READY));
      assertThat(
              jdbc.queryForObject(
                  "select payment_frozen_at is null and payment_snapshot_version is null and frozen_purchase_snapshot is null from booking_schema.tbl_orders where id=?",
                  Boolean.class,
                  order.getOrderId()))
          .isTrue();

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void checksDeadlineAfterWaitingForOrderLock() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      Object target =
          org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
      Object original =
          org.springframework.test.util.ReflectionTestUtils.getField(
              target, "reservationHoldDuration");
      try {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "reservationHoldDuration", Duration.ofSeconds(3));
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 1));
        try (var connection = jdbc.getDataSource().getConnection();
            var executor = Executors.newSingleThreadExecutor()) {
          connection.setAutoCommit(false);
          try (var lock =
              connection.prepareStatement(
                  "select id from booking_schema.tbl_orders where id=? for update")) {
            lock.setObject(1, order.getOrderId());
            lock.executeQuery().close();
          }
          var result =
              executor.submit(
                  () -> {
                    try {
                      return orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
                    } catch (RuntimeException e) {
                      return e;
                    }
                  });
          Awaitility.await()
              .atMost(Duration.ofSeconds(2))
              .until(
                  () ->
                      jdbc.queryForObject(
                          "select exists(select 1 from pg_locks where not granted)",
                          Boolean.class));
          Awaitility.await()
              .atMost(Duration.ofSeconds(4))
              .until(
                  () ->
                      jdbc.queryForObject(
                          "select clock_timestamp() >= ?",
                          Boolean.class,
                          java.sql.Timestamp.from(order.getExpiresAt())));
          connection.commit();
          assertThat(result.get(10, TimeUnit.SECONDS))
              .isInstanceOfSatisfying(
                  BusinessException.class,
                  e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));
        }
      } finally {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "reservationHoldDuration", original);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void scopesPromotionAndSnapshotLockTimeoutToTransaction() throws Exception {
    try {

      for (boolean snapshot : List.of(true, false)) {

        var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var promotionConfig =
            new PromotionConfig(
                UUID.randomUUID(),
                "SAVE",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                "2",
                1,
                new PromotionConfig.PercentDiscount("PERCENT", 10));
        var config =
            with(
                with(baseConfig, "promotions", List.of(promotionConfig)),
                "sourceConfigVersion",
                "2");
        salesService.synchronizeSalesConfiguration(
            new Envelope<>(
                UUID.randomUUID(),
                "event.sales-configured.v1",
                1,
                config.eventId(),
                "2",
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                config));
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

        String original = jdbc.queryForObject("show lock_timeout", String.class);
        try (var connection = jdbc.getDataSource().getConnection();
            var executor = Executors.newSingleThreadExecutor()) {
          connection.setAutoCommit(false);
          try (var lock =
              connection.prepareStatement(
                  "select id from booking_schema.tbl_orders where id=? for update")) {
            lock.setObject(1, order.getOrderId());
            lock.executeQuery().close();
          }
          var result =
              executor.submit(
                  () -> {
                    try {
                      return snapshot
                          ? orderService.preparePurchaseSnapshot("buyer", order.getOrderId())
                          : promotionService.applyPromotionToOrder(
                              "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
                    } catch (RuntimeException e) {
                      return e;
                    }
                  });
          Object outcome = result.get(10, TimeUnit.SECONDS);
          assertThat(outcome).isInstanceOf(RuntimeException.class);
          assertThat(ErrorCode.from((RuntimeException) outcome)).isEqualTo(ErrorCode.RESOURCE_BUSY);
          assertThat(((RuntimeException) outcome).getMessage()).contains("lock timeout");
          connection.rollback();
        }
        assertThat(jdbc.queryForObject("show lock_timeout", String.class)).isEqualTo(original);
        assertThat(
                jdbc.queryForObject(
                    "select payment_frozen_at from booking_schema.tbl_orders where id=?",
                    java.sql.Timestamp.class,
                    order.getOrderId()))
            .isNull();
        assertThat(
                jdbc.queryForObject(
                    "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                    Long.class,
                    promotionConfig.promotionId()))
            .isZero();
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsMissingAmbiguousOrInconsistentPromotionConfiguration() throws Exception {
    try {

      var baseConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotionConfig =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config =
          with(
              with(baseConfig, "promotions", List.of(promotionConfig)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));

      jdbc.update(
          "update booking_schema.tbl_promotion_inventory set source_config_version=1 where promotion_id=?",
          promotionConfig.promotionId());
      assertThatThrownBy(
              () ->
                  promotionService.applyPromotionToOrder(
                      "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE")))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SALES_CONFIGURATION_CONFLICT));
      jdbc.update(
          "delete from booking_schema.tbl_promotion_inventory where promotion_id=?",
          promotionConfig.promotionId());
      assertThatThrownBy(
              () ->
                  promotionService.applyPromotionToOrder(
                      "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE")))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONFIG_NOT_READY));

      var duplicate = with(promotionConfig, "promotionId", UUID.randomUUID());
      var ambiguous =
          with(
              with(config, "promotions", List.of(promotionConfig, duplicate)),
              "sourceConfigVersion",
              "3");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "3",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              ambiguous));
      assertThatThrownBy(
              () ->
                  promotionService.applyPromotionToOrder(
                      "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE")))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SALES_CONFIGURATION_CONFLICT));
      var removed = with(with(ambiguous, "promotions", List.of()), "sourceConfigVersion", "4");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "4",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              removed));
      assertThatThrownBy(
              () ->
                  promotionService.applyPromotionToOrder(
                      "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE")))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PROMOTION_NOT_FOUND));
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void acceptsPaymentForEveryInventoryMode() throws Exception {
    try {

      for (int mode = 0; mode < 3; mode++) {
        var config =
            seedOrderCreationConfiguration(
                mode == 0 ? SalesMode.QUANTITY : SalesMode.SEAT_MAP,
                mode == 0 ? null : mode == 1 ? SectorType.STANDING : SectorType.SEATED,
                20,
                5);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        var command =
            new com.flashticket.booking_service.dto.message.AcceptPayment(
                order.getOrderId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                purchase.total(),
                purchase.currency(),
                "1");
        var message =
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                "1",
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                command);
        assertThat(orderService.acceptPayment(message).outcome())
            .isEqualTo(
                com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.ACCEPTED);
        assertThat(
                jdbc.queryForObject(
                    "select lifecycle_state from booking_schema.tbl_orders where id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo("ISSUING");
        assertThat(
                jdbc.queryForObject(
                    "select state from booking_schema.tbl_reservations where order_id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo("COMMITTED");
        assertThat(
                jdbc.queryForList(
                    "select state from booking_schema.tbl_reservation_allocations where order_id=?",
                    String.class,
                    order.getOrderId()))
            .containsOnly("COMMITTED");
        assertThat(
                jdbc.queryForObject(
                    "select held from booking_schema.tbl_purchase_limits where event_id=?",
                    Integer.class,
                    config.eventId()))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select purchased from booking_schema.tbl_purchase_limits where event_id=?",
                    Integer.class,
                    config.eventId()))
            .isEqualTo(2);
        if (mode == 2) {
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and state='PURCHASED' and current_reservation_id is not null",
                      Long.class,
                      config.eventId()))
              .isEqualTo(2L);
          assertThat(
                  jdbc.queryForList(
                      "select reservation_generation from booking_schema.tbl_seat_inventory where event_id=? and state='PURCHASED'",
                      Long.class,
                      config.eventId()))
              .containsOnly(1L);
        } else {
          String table = mode == 0 ? "tbl_ticket_type_inventory" : "tbl_sector_inventory";
          assertThat(
                  jdbc.queryForObject(
                      "select sum(purchased) from booking_schema." + table + " where event_id=?",
                      Long.class,
                      config.eventId()))
              .isEqualTo(2L);
          assertThat(
                  jdbc.queryForObject(
                      "select sum(held) from booking_schema." + table + " where event_id=?",
                      Long.class,
                      config.eventId()))
              .isZero();
        }
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void commitsPromotionUsageWithAcceptedPayment() throws Exception {
    try {

      var base = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotion =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config = with(with(base, "promotions", List.of(promotion)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      promotionService.applyPromotionToOrder(
          "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              "1");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      orderService.acceptPayment(message);
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_promotion_usages where promotion_id=?",
                  String.class,
                  promotion.promotionId()))
          .isEqualTo("USED");
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotion.promotionId()))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select used from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotion.promotionId()))
          .isEqualTo(1L);
      assertThat(
              jdbc.queryForObject(
                  "select total from booking_schema.tbl_orders where id=?",
                  BigDecimal.class,
                  order.getOrderId()))
          .isEqualByComparingTo("180");

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replaysAcceptPaymentWithoutChangingResources() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    var oldDuration =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    org.springframework.test.util.ReflectionTestUtils.setField(
        target, "reservationHoldDuration", Duration.ofSeconds(3));
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      var decision = orderService.acceptPayment(message);
      Awaitility.await()
          .atMost(Duration.ofSeconds(8))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      var before = captureOrderCreationState(config.eventId());
      assertThat(orderService.acceptPayment(message)).isEqualTo(decision);
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      var newMessage = with(message, "messageId", UUID.randomUUID());
      assertThat(orderService.acceptPayment(newMessage)).isEqualTo(decision);
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                  Long.class,
                  order.getOrderId()))
          .isEqualTo(2L);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.accept-payment' and response_payload->>'orderId'=?",
                  Long.class,
                  order.getOrderId().toString()))
          .isEqualTo(2L);

    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", oldDuration);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsConflictingAcceptPaymentMessages() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      orderService.acceptPayment(message);
      assertThatThrownBy(
              () -> orderService.acceptPayment(with(message, "correlationId", UUID.randomUUID())))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getErrorCode())
                      .isEqualTo(ErrorCode.MESSAGE_ID_CONFLICT));
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                  Long.class,
                  order.getOrderId()))
          .isEqualTo(1L);
      var other =
          orderService.createOrder("buyer2", UUID.randomUUID(), createOrderRequest(config, 1));
      var frozen = orderService.preparePurchaseSnapshot("buyer2", other.getOrderId());
      var otherCommand =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              other.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              frozen.total(),
              frozen.currency(),
              "1");
      var conflicting =
          new Envelope<>(
              message.messageId(),
              message.messageType(),
              1,
              other.getOrderId(),
              "1",
              message.occurredAt(),
              message.correlationId(),
              null,
              null,
              otherCommand);
      assertThatThrownBy(() -> orderService.acceptPayment(conflicting))
          .isInstanceOf(BusinessException.class);
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  other.getOrderId()))
          .isEqualTo("PENDING_PAYMENT");

      UUID raceId = UUID.randomUUID();
      var firstCandidate = with(message, "messageId", raceId);
      var secondCandidate = with(conflicting, "messageId", raceId);
      int successes = 0, failures = 0;
      try (var executor = Executors.newFixedThreadPool(2)) {
        var candidates =
            List.of(
                executor.submit(() -> orderService.acceptPayment(firstCandidate)),
                executor.submit(() -> orderService.acceptPayment(secondCandidate)));
        for (var future : candidates) {
          try {
            future.get(15, TimeUnit.SECONDS);
            successes++;
          } catch (ExecutionException exception) {
            assertThat(exception.getCause())
                .isInstanceOfAny(DataIntegrityViolationException.class, BusinessException.class);
            if (exception.getCause() instanceof BusinessException business)
              assertThat(business.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_ID_CONFLICT);
            failures++;
          }
        }
      }
      assertThat(successes).isEqualTo(1);
      assertThat(failures).isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.accept-payment' and message_id=?",
                  Integer.class,
                  raceId))
          .isEqualTo(1);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void returnsDuplicateForAnotherAcceptedCharge() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      var another = with(message, "messageId", UUID.randomUUID());
      another = with(another, "data", with(command, "chargeId", UUID.randomUUID()));
      final var second = another;
      try (var executor = Executors.newFixedThreadPool(2)) {
        var firstFuture = executor.submit(() -> orderService.acceptPayment(message));
        var secondFuture = executor.submit(() -> orderService.acceptPayment(second));
        var decisions =
            List.of(firstFuture.get(15, TimeUnit.SECONDS), secondFuture.get(15, TimeUnit.SECONDS));
        assertThat(
                decisions.stream()
                    .map(com.flashticket.booking_service.dto.message.PaymentDecision::outcome)
                    .toList())
            .containsExactlyInAnyOrder(
                com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.ACCEPTED,
                com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.DUPLICATE);
        assertThat(decisions.get(0).acceptedChargeId())
            .isEqualTo(decisions.get(1).acceptedChargeId());
      }
      assertThat(
              jdbc.queryForObject(
                  "select purchased from booking_schema.tbl_purchase_limits where event_id=?",
                  Integer.class,
                  config.eventId()))
          .isEqualTo(2);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void releasesExpiredReservationWhenPaymentArrivesLate() throws Exception {
    try {

      Object target =
          org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
      var oldDuration =
          org.springframework.test.util.ReflectionTestUtils.getField(
              target, "reservationHoldDuration");
      try {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "reservationHoldDuration", Duration.ofSeconds(3));
        for (int mode = 0; mode < 3; mode++) {
          var config =
              seedOrderCreationConfiguration(
                  mode == 0 ? SalesMode.QUANTITY : SalesMode.SEAT_MAP,
                  mode == 0 ? null : mode == 1 ? SectorType.STANDING : SectorType.SEATED,
                  20,
                  5);
          var order =
              orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
          var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
          var command =
              new com.flashticket.booking_service.dto.message.AcceptPayment(
                  order.getOrderId(),
                  UUID.randomUUID(),
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  "1");
          var message =
              new Envelope<>(
                  UUID.randomUUID(),
                  "booking.accept-payment.v1",
                  1,
                  order.getOrderId(),
                  "1",
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  command);
          Awaitility.await()
              .atMost(Duration.ofSeconds(8))
              .until(
                  () ->
                      Boolean.TRUE.equals(
                          jdbc.queryForObject(
                              "select clock_timestamp()>=expires_at from booking_schema.tbl_orders where id=?",
                              Boolean.class,
                              order.getOrderId())));
          assertThat(orderService.acceptPayment(message).outcome())
              .isEqualTo(com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.LATE);
          var before = captureOrderCreationState(config.eventId());
          assertThat(
                  orderService
                      .acceptPayment(with(message, "messageId", UUID.randomUUID()))
                      .outcome())
              .isEqualTo(com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.LATE);
          assertThat(
                  jdbc.queryForObject(
                      "select held from booking_schema.tbl_purchase_limits where event_id=?",
                      Integer.class,
                      config.eventId()))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "select purchased from booking_schema.tbl_purchase_limits where event_id=?",
                      Integer.class,
                      config.eventId()))
              .isZero();
          assertThat(
                  jdbc.queryForList(
                      "select state from booking_schema.tbl_reservation_allocations where order_id=?",
                      String.class,
                      order.getOrderId()))
              .containsOnly("RELEASED");
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and state in ('HELD','PURCHASED')",
                      Long.class,
                      config.eventId()))
              .isZero();
        }
      } finally {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "reservationHoldDuration", oldDuration);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsPaymentForCancelledEventOrOrder() throws Exception {
    try {

      var base = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var promotion =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "2",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config = with(with(base, "promotions", List.of(promotion)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              config));
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      promotionService.applyPromotionToOrder(
          "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              "1");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
          config.eventId());
      assertThat(orderService.acceptPayment(message).outcome())
          .isEqualTo(com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.CANCELLED);
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("CANCELLED");
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_promotion_usages where promotion_id=?",
                  String.class,
                  promotion.promotionId()))
          .isEqualTo("RELEASED");
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promotion.promotionId()))
          .isZero();
      assertThat(
              orderService.acceptPayment(with(message, "messageId", UUID.randomUUID())).outcome())
          .isEqualTo(com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.CANCELLED);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsMismatchedFrozenPaymentSnapshot() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      var before = captureOrderCreationState(config.eventId());
      for (var invalid :
          List.of(with(command, "amount", "1"), with(command, "sourceOrderVersion", "2"))) {
        var bad =
            new Envelope<>(
                UUID.randomUUID(),
                message.messageType(),
                1,
                order.getOrderId(),
                invalid.sourceOrderVersion(),
                Instant.now(),
                message.correlationId(),
                null,
                null,
                invalid);
        assertThatThrownBy(() -> orderService.acceptPayment(bad))
            .isInstanceOf(BusinessException.class)
            .satisfies(
                ex ->
                    assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_SNAPSHOT_MISMATCH));
      }
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                  Long.class,
                  order.getOrderId()))
          .isZero();

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollsBackAcceptPaymentWhenPersistenceFails() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      var before = captureOrderCreationState(config.eventId());
      assertThatThrownBy(
              () ->
                  new TransactionTemplate(transactionManager)
                      .execute(
                          status -> {
                            orderService.acceptPayment(message);
                            jdbc.update(
                                "update booking_schema.tbl_orders set total=-1 where id=?",
                                order.getOrderId());
                            return null;
                          }))
          .isInstanceOf(DataIntegrityViolationException.class);
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                  Long.class,
                  order.getOrderId()))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.accept-payment' and message_id=?",
                  Long.class,
                  message.messageId()))
          .isZero();

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void checksPaymentDeadlineAfterInventoryLockWait() throws Exception {
    try {

      Object target =
          org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
      var oldDuration =
          org.springframework.test.util.ReflectionTestUtils.getField(
              target, "reservationHoldDuration");
      try {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "reservationHoldDuration", Duration.ofSeconds(3));

        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        var command =
            new com.flashticket.booking_service.dto.message.AcceptPayment(
                order.getOrderId(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                purchase.total(),
                purchase.currency(),
                purchase.sourceOrderVersion());
        var message =
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                "1",
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                command);

        try (var connection = jdbc.getDataSource().getConnection();
            var executor = Executors.newSingleThreadExecutor()) {
          connection.setAutoCommit(false);
          try (var st =
              connection.prepareStatement(
                  "select ticket_type_id from booking_schema.tbl_ticket_type_inventory where event_id=? for update")) {
            st.setObject(1, config.eventId());
            st.executeQuery();
          }
          var future = executor.submit(() -> orderService.acceptPayment(message));
          Awaitility.await()
              .atMost(Duration.ofSeconds(3))
              .until(
                  () ->
                      jdbc.queryForObject(
                          "select exists(select 1 from pg_locks waiting join pg_locks rel on rel.pid=waiting.pid where not waiting.granted and rel.relation=to_regclass('booking_schema.tbl_ticket_type_inventory'))",
                          Boolean.class));
          Awaitility.await()
              .atMost(Duration.ofSeconds(4))
              .until(
                  () ->
                      jdbc.queryForObject(
                          "select clock_timestamp()>=expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId()));
          connection.commit();
          assertThat(future.get(10, TimeUnit.SECONDS).outcome())
              .isEqualTo(com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.LATE);
        }
      } finally {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "reservationHoldDuration", oldDuration);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsInconsistentReservationResources() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      jdbc.update(
          "update booking_schema.tbl_reservation_allocations set quantity=3 where order_id=?",
          order.getOrderId());
      var before = captureOrderCreationState(config.eventId());
      assertThatThrownBy(() -> orderService.acceptPayment(message))
          .isInstanceOf(BusinessException.class)
          .satisfies(
              ex ->
                  assertThat(((BusinessException) ex).getErrorCode())
                      .isEqualTo(ErrorCode.RESERVATION_DATA_CONFLICT));
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void acknowledgesAcceptPaymentOnlyAfterDatabaseCommit() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      var connection = (CachingConnectionFactory) relayRabbitTemplate.getConnectionFactory();
      var admin = new RabbitAdmin(connection);
      String queue = "accept-payment-" + UUID.randomUUID(), dlq = queue + ".dlq";
      admin.declareQueue(new Queue(dlq, true, false, false, Map.of("x-queue-type", "quorum")));
      admin.declareQueue(
          new Queue(
              queue,
              true,
              false,
              false,
              Map.of(
                  "x-queue-type",
                  "quorum",
                  "x-delivery-limit",
                  3,
                  "x-dead-letter-exchange",
                  "",
                  "x-dead-letter-routing-key",
                  dlq)));
      var method =
          com.flashticket.booking_service.messaging.AcceptPaymentListener.class.getMethod(
              "handleAcceptPayment", Envelope.class);
      var methods =
          new org.springframework.messaging.handler.annotation.support
              .DefaultMessageHandlerMethodFactory();
      methods.afterPropertiesSet();
      var endpoint = new MethodRabbitListenerEndpoint();
      endpoint.setId(queue);
      endpoint.setBean(
          new com.flashticket.booking_service.messaging.AcceptPaymentListener(orderService));
      endpoint.setMethod(method);
      endpoint.setQueueNames(queue);
      endpoint.setMessageHandlerMethodFactory(methods);
      var factory = new SimpleRabbitListenerContainerFactory();
      factory.setConnectionFactory(connection);
      factory.setMessageConverter(relayRabbitTemplate.getMessageConverter());
      factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
      factory.setPrefetchCount(1);
      factory.setErrorHandler(new BookingRabbitErrorHandler());
      var container = factory.createListenerContainer(endpoint);
      container.afterPropertiesSet();
      container.start();

      try {
        relayRabbitTemplate.convertAndSend("", queue, message);
        Awaitility.await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () ->
                    "ISSUING"
                        .equals(
                            jdbc.queryForObject(
                                "select lifecycle_state from booking_schema.tbl_orders where id=?",
                                String.class,
                                order.getOrderId())));
        container.stop();
        assertThat(admin.getQueueInfo(queue).getMessageCount()).isZero();
        container.start();
        relayRabbitTemplate.convertAndSend("", queue, message);
        relayRabbitTemplate.convertAndSend("", queue, with(message, "schemaVersion", 2));
        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> relayRabbitTemplate.receive(dlq) != null);
        container.stop();
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                    Long.class,
                    order.getOrderId()))
            .isEqualTo(1L);
      } finally {
        container.stop();
        container.destroy();
        admin.deleteQueue(queue);
        admin.deleteQueue(dlq);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void publishesPaymentDecisionWithConfirmAndPersistentEnvelope() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      orderService.acceptPayment(message);
      UUID responseId =
          UUID.nameUUIDFromBytes(
              ("booking.payment-decision:" + message.messageId())
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));

      messagingService.publishBookingMessage(responseId);
      var received = relayRabbitTemplate.receive(RELAY_QUEUE, 5000);
      assertThat(received).isNotNull();
      assertThat(received.getMessageProperties().getReceivedDeliveryMode())
          .isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
      assertThat(received.getMessageProperties().getMessageId()).isEqualTo(responseId.toString());
      var json = context.getBean(JsonMapper.class).readTree(received.getBody());
      assertThat(json.get("messageType").asText()).isEqualTo("booking.payment-decision.v1");
      assertThat(json.get("causationId").asText()).isEqualTo(message.messageId().toString());
      assertThat(json.get("data").get("outcome").asText()).isEqualTo("ACCEPTED");
      assertThat(
              jdbc.queryForObject(
                  "select published_at from booking_schema.tbl_outbox_messages where message_id=?",
                  java.sql.Timestamp.class,
                  responseId))
          .isNotNull();

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void retriesUnconfirmedOrReturnedPaymentDecision() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      orderService.acceptPayment(message);
      UUID responseId =
          UUID.nameUUIDFromBytes(
              ("booking.payment-decision:" + message.messageId())
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));

      Object target =
          org.springframework.test.util.AopTestUtils.getUltimateTargetObject(messagingService);
      var originalTemplate =
          org.springframework.test.util.ReflectionTestUtils.getField(target, "rabbitTemplate");
      var originalTimeout =
          org.springframework.test.util.ReflectionTestUtils.getField(target, "confirmTimeout");
      var originalExchange =
          org.springframework.test.util.ReflectionTestUtils.getField(target, "exchange");
      try {
        var admin = new RabbitAdmin(relayRabbitTemplate.getConnectionFactory());
        admin.declareExchange(
            new org.springframework.amqp.core.DirectExchange(RELAY_EXCHANGE + ".unroutable"));
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "exchange", RELAY_EXCHANGE + ".unroutable");
        messagingService.publishBookingMessage(responseId);
        assertThat(
                jdbc.queryForObject(
                    "select last_error_code from booking_schema.tbl_outbox_messages where message_id=?",
                    String.class,
                    responseId))
            .isEqualTo("MESSAGE_PUBLISH_FAILED");
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "exchange", originalExchange);
        for (int scenario = 0; scenario < 3; scenario++) {
          var mock = org.mockito.Mockito.mock(RabbitTemplate.class);
          org.mockito.Mockito.when(mock.getMessageConverter())
              .thenReturn(relayRabbitTemplate.getMessageConverter());
          if (scenario == 0)
            org.mockito.Mockito.doAnswer(
                    call -> {
                      ((org.springframework.amqp.rabbit.connection.CorrelationData)
                              call.getArgument(3))
                          .getFuture()
                          .complete(
                              new org.springframework.amqp.rabbit.connection.CorrelationData
                                  .Confirm(false, "test nack"));
                      return null;
                    })
                .when(mock)
                .send(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(org.springframework.amqp.core.Message.class),
                    org.mockito.ArgumentMatchers.any(
                        org.springframework.amqp.rabbit.connection.CorrelationData.class));
          if (scenario == 2)
            org.mockito.Mockito.doThrow(
                    new org.springframework.amqp.AmqpConnectException(
                        new java.net.ConnectException("test broker down")))
                .when(mock)
                .send(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.any(org.springframework.amqp.core.Message.class),
                    org.mockito.ArgumentMatchers.any(
                        org.springframework.amqp.rabbit.connection.CorrelationData.class));
          org.springframework.test.util.ReflectionTestUtils.setField(
              target, "rabbitTemplate", mock);
          org.springframework.test.util.ReflectionTestUtils.setField(
              target, "confirmTimeout", Duration.ofMillis(50));
          jdbc.update(
              "update booking_schema.tbl_outbox_messages set next_attempt_at=clock_timestamp() where message_id=?",
              responseId);
          messagingService.publishBookingMessage(responseId);
        }
        assertThat(
                jdbc.queryForObject(
                    "select attempts from booking_schema.tbl_outbox_messages where message_id=?",
                    Integer.class,
                    responseId))
            .isEqualTo(4);
        assertThat(
                jdbc.queryForObject(
                    "select published_at is null and next_attempt_at>clock_timestamp() from booking_schema.tbl_outbox_messages where message_id=?",
                    Boolean.class,
                    responseId))
            .isTrue();
      } finally {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "rabbitTemplate", originalTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "confirmTimeout", originalTimeout);
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "exchange", originalExchange);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replaysPublishedButUncommittedOutboxMessageWithSameId() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      orderService.acceptPayment(message);
      UUID responseId =
          UUID.nameUUIDFromBytes(
              ("booking.payment-decision:" + message.messageId())
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));

      new TransactionTemplate(transactionManager)
          .execute(
              status -> {
                messagingService.publishBookingMessage(responseId);
                status.setRollbackOnly();
                return null;
              });
      var first = relayRabbitTemplate.receive(RELAY_QUEUE, 5000);
      assertThat(
              jdbc.queryForObject(
                  "select published_at is null from booking_schema.tbl_outbox_messages where message_id=?",
                  Boolean.class,
                  responseId))
          .isTrue();
      messagingService.publishBookingMessage(responseId);
      var second = relayRabbitTemplate.receive(RELAY_QUEUE, 5000);
      assertThat(first).isNotNull();
      assertThat(second).isNotNull();
      assertThat(first.getBody()).isEqualTo(second.getBody());
      assertThat(first.getMessageProperties().getMessageId())
          .isEqualTo(second.getMessageProperties().getMessageId());
      // Dedupe receiver phải dựa cùng messageId; đây không phải consumer Payment thật.
      assertThat(
              new HashSet<>(
                  List.of(
                      first.getMessageProperties().getMessageId(),
                      second.getMessageProperties().getMessageId())))
          .hasSize(1);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesConcurrentOutboxPublishersAndSkipsPublishedRows() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      orderService.acceptPayment(message);
      UUID responseId =
          UUID.nameUUIDFromBytes(
              ("booking.payment-decision:" + message.messageId())
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));

      try (var executor = Executors.newFixedThreadPool(2)) {
        var first = executor.submit(() -> messagingService.publishBookingMessage(responseId));
        var second = executor.submit(() -> messagingService.publishBookingMessage(responseId));
        first.get(15, TimeUnit.SECONDS);
        second.get(15, TimeUnit.SECONDS);
      }
      assertThat(relayRabbitTemplate.receive(RELAY_QUEUE, 5000)).isNotNull();
      messagingService.publishBookingMessage(responseId);
      assertThat(relayRabbitTemplate.receive(RELAY_QUEUE, 200)).isNull();
      assertThat(
              jdbc.queryForObject(
                  "select published_at from booking_schema.tbl_outbox_messages where message_id=?",
                  java.sql.Timestamp.class,
                  responseId))
          .isNotNull();

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void deliversAcceptPaymentDecisionThroughRealRabbitMq() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      var command =
          new com.flashticket.booking_service.dto.message.AcceptPayment(
              order.getOrderId(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              purchase.total(),
              purchase.currency(),
              purchase.sourceOrderVersion());
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              UUID.randomUUID(),
              null,
              null,
              command);

      var connection = (CachingConnectionFactory) relayRabbitTemplate.getConnectionFactory();
      var admin = new RabbitAdmin(connection);
      String queue = "accept-payment-" + UUID.randomUUID(), dlq = queue + ".dlq";
      admin.declareQueue(new Queue(dlq, true, false, false, Map.of("x-queue-type", "quorum")));
      admin.declareQueue(
          new Queue(
              queue,
              true,
              false,
              false,
              Map.of(
                  "x-queue-type",
                  "quorum",
                  "x-delivery-limit",
                  3,
                  "x-dead-letter-exchange",
                  "",
                  "x-dead-letter-routing-key",
                  dlq)));
      var method =
          com.flashticket.booking_service.messaging.AcceptPaymentListener.class.getMethod(
              "handleAcceptPayment", Envelope.class);
      var methods =
          new org.springframework.messaging.handler.annotation.support
              .DefaultMessageHandlerMethodFactory();
      methods.afterPropertiesSet();
      var endpoint = new MethodRabbitListenerEndpoint();
      endpoint.setId(queue);
      endpoint.setBean(
          new com.flashticket.booking_service.messaging.AcceptPaymentListener(orderService));
      endpoint.setMethod(method);
      endpoint.setQueueNames(queue);
      endpoint.setMessageHandlerMethodFactory(methods);
      var factory = new SimpleRabbitListenerContainerFactory();
      factory.setConnectionFactory(connection);
      factory.setMessageConverter(relayRabbitTemplate.getMessageConverter());
      factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
      factory.setPrefetchCount(1);
      factory.setErrorHandler(new BookingRabbitErrorHandler());
      var container = factory.createListenerContainer(endpoint);
      container.afterPropertiesSet();
      container.start();

      try {
        relayRabbitTemplate.convertAndSend("", queue, message);
        UUID responseId =
            UUID.nameUUIDFromBytes(
                ("booking.payment-decision:" + message.messageId())
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Awaitility.await()
            .atMost(Duration.ofSeconds(15))
            .until(
                () ->
                    jdbc.queryForObject(
                            "select count(*) from booking_schema.tbl_outbox_messages where message_id=?",
                            Integer.class,
                            responseId)
                        == 1);
        var worker =
            new com.flashticket.booking_service.messaging.BookingOutboxWorker(
                context.getBean(
                    com.flashticket.booking_service.messaging.repository.OutboxMessageRepository
                        .class),
                messagingService);
        org.springframework.test.util.ReflectionTestUtils.setField(worker, "batchSize", 20);
        worker.publishDueBookingMessages();
        var received = relayRabbitTemplate.receive(RELAY_QUEUE, 5000);
        assertThat(received).isNotNull();
        assertThat(
                context
                    .getBean(JsonMapper.class)
                    .readTree(received.getBody())
                    .get("data")
                    .get("orderId")
                    .asText())
            .isEqualTo(order.getOrderId().toString());
        assertThat(
                jdbc.queryForObject(
                    "select published_at from booking_schema.tbl_outbox_messages where message_id=?",
                    java.sql.Timestamp.class,
                    responseId))
            .isNotNull();
        // Cả bốn outcome đi qua command queue -> transaction -> outbox -> queue trả lời.
        Object target =
            org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
        var oldDuration =
            org.springframework.test.util.ReflectionTestUtils.getField(
                target, "reservationHoldDuration");
        try {
          for (var outcome : List.of("DUPLICATE", "CANCELLED", "LATE")) {
            Envelope<com.flashticket.booking_service.dto.message.AcceptPayment> next;
            if (outcome.equals("DUPLICATE")) {
              next =
                  with(
                      with(message, "messageId", UUID.randomUUID()),
                      "data",
                      with(command, "chargeId", UUID.randomUUID()));
            } else {
              org.springframework.test.util.ReflectionTestUtils.setField(
                  target,
                  "reservationHoldDuration",
                  outcome.equals("LATE") ? Duration.ofSeconds(3) : Duration.ofMinutes(15));
              var nextConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
              var nextOrder =
                  orderService.createOrder(
                      "buyer-" + outcome, UUID.randomUUID(), createOrderRequest(nextConfig, 1));
              var nextPurchase =
                  orderService.preparePurchaseSnapshot("buyer-" + outcome, nextOrder.getOrderId());
              var nextCommand =
                  new com.flashticket.booking_service.dto.message.AcceptPayment(
                      nextOrder.getOrderId(),
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      nextPurchase.total(),
                      nextPurchase.currency(),
                      "1");
              next =
                  new Envelope<>(
                      UUID.randomUUID(),
                      message.messageType(),
                      1,
                      nextOrder.getOrderId(),
                      "1",
                      Instant.now(),
                      UUID.randomUUID(),
                      null,
                      null,
                      nextCommand);
              if (outcome.equals("CANCELLED"))
                jdbc.update(
                    "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
                    nextConfig.eventId());
              else
                Awaitility.await()
                    .atMost(Duration.ofSeconds(8))
                    .until(
                        () ->
                            jdbc.queryForObject(
                                "select clock_timestamp()>=expires_at from booking_schema.tbl_orders where id=?",
                                Boolean.class,
                                nextOrder.getOrderId()));
            }
            relayRabbitTemplate.convertAndSend("", queue, next);
            UUID nextId =
                UUID.nameUUIDFromBytes(
                    ("booking.payment-decision:" + next.messageId())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .until(
                    () ->
                        jdbc.queryForObject(
                                "select count(*) from booking_schema.tbl_outbox_messages where message_id=?",
                                Integer.class,
                                nextId)
                            == 1);
            worker.publishDueBookingMessages();
            var reply = relayRabbitTemplate.receive(RELAY_QUEUE, 5000);
            assertThat(reply).isNotNull();
            assertThat(
                    context
                        .getBean(JsonMapper.class)
                        .readTree(reply.getBody())
                        .get("data")
                        .get("outcome")
                        .asText())
                .isEqualTo(outcome);
          }
        } finally {
          org.springframework.test.util.ReflectionTestUtils.setField(
              target, "reservationHoldDuration", oldDuration);
        }

      } finally {
        container.stop();
        container.destroy();
        admin.deleteQueue(queue);
        admin.deleteQueue(dlq);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void completesOrderAfterSuccessfulIssuance() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object oldDuration =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    org.springframework.test.util.ReflectionTestUtils.setField(
        target, "reservationHoldDuration", Duration.ofSeconds(3));
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  purchase.sourceOrderVersion())));
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.COMPLETED,
              2,
              null);
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "ticket.issued.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);

      var before = captureOrderCreationState(config.eventId());
      var counters =
          jdbc.queryForList(
              "select * from booking_schema.tbl_ticket_type_inventory where event_id=?",
              config.eventId());
      orderService.processBookingIssuanceResult(message);
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("COMPLETED");
      assertThat(
              jdbc.queryForList(
                  "select * from booking_schema.tbl_ticket_type_inventory where event_id=?",
                  config.eventId()))
          .isEqualTo(counters);
      assertThat(captureOrderCreationState(config.eventId()).get("reservations"))
          .isEqualTo(before.get("reservations"));
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.issuance-result'",
                  Long.class))
          .isEqualTo(1L);
      var response =
          jdbc.queryForMap(
              "select * from booking_schema.tbl_outbox_messages where message_type='booking.order-completed.v1'");
      assertThat(response.get("correlation_id")).isEqualTo(message.correlationId());
      assertThat(response.get("causation_id")).isEqualTo(message.messageId());
      assertThat(response.get("aggregate_id")).isEqualTo(order.getOrderId());
      assertThat(
              jdbc.queryForObject(
                  "select payload->>'issuanceId' from booking_schema.tbl_outbox_messages where message_type='booking.order-completed.v1'",
                  String.class))
          .isEqualTo(result.issuanceId().toString());
      // Timestamp giữ chỗ không còn phân xử sau khi charge đã được accept.
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () ->
                  jdbc.queryForObject(
                      "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                      Boolean.class,
                      order.getOrderId()));
      orderService.processBookingIssuanceResult(with(message, "messageId", UUID.randomUUID()));

    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", oldDuration);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replaysIssuanceResultWithoutRepeatingEffects() throws Exception {
    try {

      for (boolean completed : List.of(true, false)) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        UUID chargeId = UUID.randomUUID();
        orderService.acceptPayment(
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    chargeId,
                    UUID.randomUUID(),
                    purchase.total(),
                    purchase.currency(),
                    purchase.sourceOrderVersion())));
        var result =
            new BookingIssuanceResult(
                order.getOrderId(),
                chargeId,
                UUID.randomUUID(),
                BookingIssuanceResult.State.COMPLETED,
                2,
                null);
        var message =
            new Envelope<>(
                UUID.randomUUID(),
                "ticket.issued.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                result);

        var selected =
            completed
                ? message
                : with(
                    with(message, "messageType", "booking.issuance-failed.v1"),
                    "data",
                    new BookingIssuanceResult(
                        order.getOrderId(),
                        chargeId,
                        result.issuanceId(),
                        BookingIssuanceResult.State.FAILED,
                        0,
                        "ERROR"));
        orderService.processBookingIssuanceResult(selected);
        var before = captureOrderCreationState(config.eventId());
        orderService.processBookingIssuanceResult(selected);
        var newId =
            with(
                with(selected, "messageId", UUID.randomUUID()),
                "data",
                with(selected.data(), "issuanceId", UUID.randomUUID()));
        orderService.processBookingIssuanceResult(newId);
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.issuance-result' and message_id in (?,?)",
                    Long.class,
                    selected.messageId(),
                    newId.messageId()))
            .isEqualTo(2L);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=? and message_type='booking.order-completed.v1'",
                    Long.class,
                    order.getOrderId()))
            .isEqualTo(completed ? 2L : 0L);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsConflictingIssuanceResults() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  purchase.sourceOrderVersion())));
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.COMPLETED,
              2,
              null);
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "ticket.issued.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);

      var before = captureOrderCreationState(config.eventId());
      for (var invalid :
          List.of(
              with(message, "aggregateVersion", "2"),
              with(message, "data", with(result, "chargeId", UUID.randomUUID())),
              with(message, "data", with(result, "ticketCount", 1)))) {
        assertThatThrownBy(() -> orderService.processBookingIssuanceResult(invalid))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ISSUANCE_RESULT_CONFLICT));
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      }
      assertThatThrownBy(
              () ->
                  orderService.processBookingIssuanceResult(
                      with(message, "messageType", "booking.issuance-failed.v1")))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_MESSAGE));
      orderService.processBookingIssuanceResult(message);
      assertThatThrownBy(
              () ->
                  orderService.processBookingIssuanceResult(
                      with(message, "correlationId", UUID.randomUUID())))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_ID_CONFLICT));
      var failed =
          with(
              with(
                  with(message, "messageId", UUID.randomUUID()),
                  "messageType",
                  "booking.issuance-failed.v1"),
              "data",
              new BookingIssuanceResult(
                  order.getOrderId(),
                  chargeId,
                  result.issuanceId(),
                  BookingIssuanceResult.State.FAILED,
                  0,
                  "ERROR"));
      assertThatThrownBy(() -> orderService.processBookingIssuanceResult(failed))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ISSUANCE_RESULT_CONFLICT));
      jdbc.update(
          "update booking_schema.tbl_orders set lifecycle_state='ISSUING' where id=?",
          order.getOrderId());
      orderService.processBookingIssuanceResult(failed);
      assertThatThrownBy(
              () ->
                  orderService.processBookingIssuanceResult(
                      with(message, "messageId", UUID.randomUUID())))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ISSUANCE_RESULT_CONFLICT));

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void waitsForPaymentAcceptanceBeforeIssuanceResult() throws Exception {
    try {

      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.COMPLETED,
              2,
              null);
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "ticket.issued.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);
      var before = captureOrderCreationState(config.eventId());
      assertThatThrownBy(() -> orderService.processBookingIssuanceResult(message))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ISSUANCE_RESULT_NOT_READY));
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.issuance-result'",
                  Long.class))
          .isZero();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              "1",
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  "1")));
      orderService.processBookingIssuanceResult(message);
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("COMPLETED");

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void preservesCancellationWhenIssuanceResultArrives() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  purchase.sourceOrderVersion())));
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.COMPLETED,
              2,
              null);
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "ticket.issued.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);

      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
          config.eventId());
      orderService.processBookingIssuanceResult(message);
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("CANCELLED");
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_outbox_messages where message_type='booking.order-completed.v1'",
                  Long.class))
          .isZero();
      var failed =
          with(
              with(
                  with(message, "messageId", UUID.randomUUID()),
                  "messageType",
                  "booking.issuance-failed.v1"),
              "data",
              new BookingIssuanceResult(
                  order.getOrderId(),
                  chargeId,
                  result.issuanceId(),
                  BookingIssuanceResult.State.FAILED,
                  0,
                  "ERROR"));
      orderService.processBookingIssuanceResult(failed);
      orderService.processBookingIssuanceResult(with(failed, "messageId", UUID.randomUUID()));
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("CANCELLED");
      assertThat(
              jdbc.queryForObject(
                  "select sum(purchased) from booking_schema.tbl_ticket_type_inventory where event_id=?",
                  Long.class,
                  config.eventId()))
          .isZero();
      assertThatThrownBy(
              () ->
                  orderService.createOrder(
                      "other", UUID.randomUUID(), createOrderRequest(config, 1)))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_ON_SALE));

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void releasesPurchasedInventoryAfterIssuanceFailure() throws Exception {
    try {

      for (int mode = 0; mode < 3; mode++) {
        var config =
            seedOrderCreationConfiguration(
                mode == 0 ? SalesMode.QUANTITY : SalesMode.SEAT_MAP,
                mode == 0 ? null : mode == 1 ? SectorType.STANDING : SectorType.SEATED,
                20,
                10);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        UUID chargeId = UUID.randomUUID();
        orderService.acceptPayment(
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    chargeId,
                    UUID.randomUUID(),
                    purchase.total(),
                    purchase.currency(),
                    purchase.sourceOrderVersion())));
        var result =
            new BookingIssuanceResult(
                order.getOrderId(),
                chargeId,
                UUID.randomUUID(),
                BookingIssuanceResult.State.FAILED,
                0,
                "ISSUANCE_REJECTED");
        var message =
            new Envelope<>(
                UUID.randomUUID(),
                "booking.issuance-failed.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                result);

        if (mode == 0) {
          jdbc.update(
              "update booking_schema.tbl_ticket_type_inventory set purchased=1 where event_id=?",
              config.eventId());
          var before = captureOrderCreationState(config.eventId());
          assertThatThrownBy(() -> orderService.processBookingIssuanceResult(message))
              .isInstanceOfSatisfying(
                  BusinessException.class,
                  e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DATA_CONFLICT));
          assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
          jdbc.update(
              "update booking_schema.tbl_ticket_type_inventory set purchased=2 where event_id=?",
              config.eventId());
        }
        orderService.processBookingIssuanceResult(message);
        assertThat(
                jdbc.queryForObject(
                    "select lifecycle_state from booking_schema.tbl_orders where id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo("ISSUANCE_FAILED");
        assertThat(
                jdbc.queryForObject(
                    "select state from booking_schema.tbl_reservations where order_id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo("RELEASED");
        assertThat(
                jdbc.queryForList(
                    "select state from booking_schema.tbl_reservation_allocations where order_id=?",
                    String.class,
                    order.getOrderId()))
            .containsOnly("RELEASED");
        assertThat(
                jdbc.queryForObject(
                    "select purchased from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject='buyer'",
                    Integer.class,
                    config.eventId()))
            .isZero();
        if (mode == 2) {
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and state='PURCHASED'",
                      Long.class,
                      config.eventId()))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and current_reservation_id is not null",
                      Long.class,
                      config.eventId()))
              .isZero();
        } else {
          String table = mode == 0 ? "tbl_ticket_type_inventory" : "tbl_sector_inventory";
          assertThat(
                  jdbc.queryForObject(
                      "select sum(purchased) from booking_schema." + table + " where event_id=?",
                      Long.class,
                      config.eventId()))
              .isZero();
        }
        orderService.createOrder("next", UUID.randomUUID(), createOrderRequest(config, 2));
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=? and message_type='booking.order-completed.v1'",
                    Long.class,
                    order.getOrderId()))
            .isZero();
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void releasesUsedPromotionOnlyOnceAfterIssuanceFailure() throws Exception {
    try {

      var base = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
      var promo =
          new PromotionConfig(
              UUID.randomUUID(),
              "SAVE",
              Instant.now().minusSeconds(60),
              Instant.now().plusSeconds(3600),
              "10",
              1,
              new PromotionConfig.PercentDiscount("PERCENT", 10));
      var config = with(with(base, "promotions", List.of(promo)), "sourceConfigVersion", "2");
      salesService.synchronizeSalesConfiguration(
          new Envelope<>(
              UUID.randomUUID(),
              "event.sales-configured.v1",
              1,
              config.eventId(),
              "2",
              Instant.now(),
              config.eventId(),
              null,
              null,
              config));

      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      promotionService.applyPromotionToOrder(
          "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  purchase.sourceOrderVersion())));
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.FAILED,
              0,
              "ISSUANCE_REJECTED");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.issuance-failed.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);

      assertThat(
              jdbc.queryForObject(
                  "select used from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  Long.class,
                  promo.promotionId()))
          .isEqualTo(1L);
      orderService.processBookingIssuanceResult(message);
      var before =
          jdbc.queryForMap(
              "select * from booking_schema.tbl_promotion_inventory where promotion_id=?",
              promo.promotionId());
      orderService.processBookingIssuanceResult(with(message, "messageId", UUID.randomUUID()));
      assertThat(
              jdbc.queryForMap(
                  "select * from booking_schema.tbl_promotion_inventory where promotion_id=?",
                  promo.promotionId()))
          .isEqualTo(before);
      assertThat(before.get("used")).isEqualTo(0L);
      assertThat(
              jdbc.queryForObject(
                  "select state from booking_schema.tbl_promotion_usages where promotion_id=?",
                  String.class,
                  promo.promotionId()))
          .isEqualTo("RELEASED");

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void releasesPurchasedInventoryAfterSalesClose() throws Exception {
    try {

      for (boolean cancelled : List.of(false, true)) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        UUID chargeId = UUID.randomUUID();
        orderService.acceptPayment(
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    chargeId,
                    UUID.randomUUID(),
                    purchase.total(),
                    purchase.currency(),
                    purchase.sourceOrderVersion())));
        var result =
            new BookingIssuanceResult(
                order.getOrderId(),
                chargeId,
                UUID.randomUUID(),
                BookingIssuanceResult.State.FAILED,
                0,
                "ISSUANCE_REJECTED");
        var message =
            new Envelope<>(
                UUID.randomUUID(),
                "booking.issuance-failed.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                result);

        if (cancelled)
          jdbc.update(
              "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
              config.eventId());
        else
          jdbc.update(
              "update booking_schema.tbl_event_sales_snapshots set sale_starts_at=now()-interval '2 hours',sale_ends_at=now()-interval '1 hour' where event_id=?",
              config.eventId());
        orderService.processBookingIssuanceResult(message);
        assertThat(
                jdbc.queryForObject(
                    "select sum(purchased) from booking_schema.tbl_ticket_type_inventory where event_id=?",
                    Long.class,
                    config.eventId()))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select purchased from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject='buyer'",
                    Integer.class,
                    config.eventId()))
            .isZero();
        assertThatThrownBy(
                () ->
                    orderService.createOrder(
                        "next", UUID.randomUUID(), createOrderRequest(config, 1)))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_ON_SALE));
        var before = captureOrderCreationState(config.eventId());
        orderService.processBookingIssuanceResult(with(message, "messageId", UUID.randomUUID()));
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void protectsNewSeatReservationFromStaleIssuanceFailure() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 4, 10);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  purchase.sourceOrderVersion())));
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.FAILED,
              0,
              "ISSUANCE_REJECTED");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.issuance-failed.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);

      orderService.processBookingIssuanceResult(message);
      var second =
          orderService.createOrder("next", UUID.randomUUID(), createOrderRequest(config, 2));
      var before = captureOrderCreationState(config.eventId());
      orderService.processBookingIssuanceResult(with(message, "messageId", UUID.randomUUID()));
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      // Mô phỏng trạng thái cũ chưa đánh dấu RELEASED: ownership phải bảo vệ R2.
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                jdbc.update(
                    "update booking_schema.tbl_orders set lifecycle_state='ISSUING' where id=?",
                    order.getOrderId());
                jdbc.update(
                    "update booking_schema.tbl_reservations set state='COMMITTED' where order_id=?",
                    order.getOrderId());
                jdbc.update(
                    "update booking_schema.tbl_reservation_allocations set state='COMMITTED' where order_id=?",
                    order.getOrderId());
                jdbc.update(
                    "update booking_schema.tbl_purchase_limits set purchased=2 where event_id=? and buyer_subject='buyer'",
                    config.eventId());
              });
      var corruptBefore = captureOrderCreationState(config.eventId());
      assertThatThrownBy(
              () ->
                  orderService.processBookingIssuanceResult(
                      with(message, "messageId", UUID.randomUUID())))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DATA_CONFLICT));
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(corruptBefore);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_seat_inventory s join booking_schema.tbl_reservations r on r.id=s.current_reservation_id where r.order_id=? and s.state='HELD'",
                  Long.class,
                  second.getOrderId()))
          .isEqualTo(2L);

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesConcurrentIssuanceResults() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  purchase.sourceOrderVersion())));
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.FAILED,
              0,
              "ISSUANCE_REJECTED");
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "booking.issuance-failed.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);

      var executor = Executors.newFixedThreadPool(2);
      try {
        var gate = new CountDownLatch(1);
        var otherMessage = with(message, "messageId", UUID.randomUUID());
        var a =
            executor.submit(
                () -> {
                  gate.await();
                  orderService.processBookingIssuanceResult(message);
                  return true;
                });
        var b =
            executor.submit(
                () -> {
                  gate.await();
                  orderService.processBookingIssuanceResult(otherMessage);
                  return true;
                });
        gate.countDown();
        assertThat(a.get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(b.get(20, TimeUnit.SECONDS)).isTrue();
        assertThat(
                jdbc.queryForObject(
                    "select sum(purchased) from booking_schema.tbl_ticket_type_inventory where event_id=?",
                    Long.class,
                    config.eventId()))
            .isZero();
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.issuance-result'",
                    Long.class))
            .isEqualTo(2L);
        // Hai queue gửi kết quả trái nhau cùng lúc: một nhánh thắng, nhánh còn lại conflict.
        var contested =
            orderService.createOrder("contested", UUID.randomUUID(), createOrderRequest(config, 2));
        var contestedPurchase =
            orderService.preparePurchaseSnapshot("contested", contested.getOrderId());
        UUID contestedCharge = UUID.randomUUID();
        orderService.acceptPayment(
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                contested.getOrderId(),
                "1",
                Instant.now(),
                contested.getOrderId(),
                null,
                null,
                new AcceptPayment(
                    contested.getOrderId(),
                    contestedCharge,
                    UUID.randomUUID(),
                    contestedPurchase.total(),
                    contestedPurchase.currency(),
                    "1")));
        var success =
            new Envelope<>(
                UUID.randomUUID(),
                "ticket.issued.v1",
                1,
                contested.getOrderId(),
                "1",
                Instant.now(),
                contested.getOrderId(),
                null,
                null,
                new BookingIssuanceResult(
                    contested.getOrderId(),
                    contestedCharge,
                    UUID.randomUUID(),
                    BookingIssuanceResult.State.COMPLETED,
                    2,
                    null));
        var failure =
            with(
                with(
                    with(success, "messageId", UUID.randomUUID()),
                    "messageType",
                    "booking.issuance-failed.v1"),
                "data",
                new BookingIssuanceResult(
                    contested.getOrderId(),
                    contestedCharge,
                    success.data().issuanceId(),
                    BookingIssuanceResult.State.FAILED,
                    0,
                    "ERROR"));
        var resultGate = new CountDownLatch(1);
        var decisions = new ArrayList<Future<ErrorCode>>();
        for (var input : List.of(success, failure)) {
          decisions.add(
              executor.submit(
                  () -> {
                    resultGate.await();
                    try {
                      orderService.processBookingIssuanceResult(input);
                      return null;
                    } catch (BusinessException exception) {
                      return exception.getErrorCode();
                    }
                  }));
        }
        resultGate.countDown();
        assertThat(
                Arrays.asList(
                    decisions.get(0).get(20, TimeUnit.SECONDS),
                    decisions.get(1).get(20, TimeUnit.SECONDS)))
            .containsExactlyInAnyOrder(null, ErrorCode.ISSUANCE_RESULT_CONFLICT);
        String terminalState =
            jdbc.queryForObject(
                "select lifecycle_state from booking_schema.tbl_orders where id=?",
                String.class,
                contested.getOrderId());
        assertThat(
                jdbc.queryForObject(
                    "select purchased from booking_schema.tbl_purchase_limits where event_id=? and buyer_subject='contested'",
                    Integer.class,
                    config.eventId()))
            .isEqualTo("COMPLETED".equals(terminalState) ? 2 : 0);

        // Cùng messageId nhưng khác order không được ghi đè inbox của transaction thắng.
        UUID reusedId = UUID.randomUUID();
        var conflicting = new ArrayList<Envelope<BookingIssuanceResult>>();
        for (int i = 0; i < 2; i++) {
          String buyer = "receipt-racer-" + i;
          var candidate =
              orderService.createOrder(buyer, UUID.randomUUID(), createOrderRequest(config, 2));
          var frozen = orderService.preparePurchaseSnapshot(buyer, candidate.getOrderId());
          UUID charge = UUID.randomUUID();
          orderService.acceptPayment(
              new Envelope<>(
                  UUID.randomUUID(),
                  "booking.accept-payment.v1",
                  1,
                  candidate.getOrderId(),
                  "1",
                  Instant.now(),
                  candidate.getOrderId(),
                  null,
                  null,
                  new AcceptPayment(
                      candidate.getOrderId(),
                      charge,
                      UUID.randomUUID(),
                      frozen.total(),
                      frozen.currency(),
                      "1")));
          conflicting.add(
              new Envelope<>(
                  reusedId,
                  "ticket.issued.v1",
                  1,
                  candidate.getOrderId(),
                  "1",
                  Instant.now(),
                  candidate.getOrderId(),
                  null,
                  null,
                  new BookingIssuanceResult(
                      candidate.getOrderId(),
                      charge,
                      UUID.randomUUID(),
                      BookingIssuanceResult.State.COMPLETED,
                      2,
                      null)));
        }
        var receiptGate = new CountDownLatch(1);
        var receiptResults = new ArrayList<Future<RuntimeException>>();
        for (var input : conflicting) {
          receiptResults.add(
              executor.submit(
                  () -> {
                    receiptGate.await();
                    try {
                      orderService.processBookingIssuanceResult(input);
                      return null;
                    } catch (RuntimeException exception) {
                      return exception;
                    }
                  }));
        }
        receiptGate.countDown();
        var outcomes =
            Arrays.asList(
                receiptResults.get(0).get(20, TimeUnit.SECONDS),
                receiptResults.get(1).get(20, TimeUnit.SECONDS));
        assertThat(outcomes.stream().filter(Objects::isNull).count()).isEqualTo(1L);
        var losing = outcomes.stream().filter(Objects::nonNull).findFirst().orElseThrow();
        if (losing instanceof BusinessException business)
          assertThat(business.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_ID_CONFLICT);
        else assertThat(losing).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_orders where id in (?,?) and lifecycle_state='COMPLETED'",
                    Long.class,
                    conflicting.get(0).aggregateId(),
                    conflicting.get(1).aggregateId()))
            .isEqualTo(1L);
      } finally {
        executor.shutdownNow();
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollsBackIssuanceResultWhenPersistenceFails() throws Exception {
    try {

      for (boolean completed : List.of(true, false)) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        UUID chargeId = UUID.randomUUID();
        orderService.acceptPayment(
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    chargeId,
                    UUID.randomUUID(),
                    purchase.total(),
                    purchase.currency(),
                    purchase.sourceOrderVersion())));
        var result =
            new BookingIssuanceResult(
                order.getOrderId(),
                chargeId,
                UUID.randomUUID(),
                BookingIssuanceResult.State.COMPLETED,
                2,
                null);
        var message =
            new Envelope<>(
                UUID.randomUUID(),
                "ticket.issued.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                result);

        var selected =
            completed
                ? message
                : with(
                    with(message, "messageType", "booking.issuance-failed.v1"),
                    "data",
                    new BookingIssuanceResult(
                        order.getOrderId(),
                        chargeId,
                        result.issuanceId(),
                        BookingIssuanceResult.State.FAILED,
                        0,
                        "ERROR"));
        var before = captureOrderCreationState(config.eventId());
        long outboxBefore =
            jdbc.queryForObject(
                "select count(*) from booking_schema.tbl_outbox_messages", Long.class);
        assertThatThrownBy(
                () ->
                    new TransactionTemplate(transactionManager)
                        .executeWithoutResult(
                            status -> {
                              orderService.processBookingIssuanceResult(selected);
                              jdbc.execute(
                                  "insert into booking_schema.tbl_inbox_messages (consumer_name,message_id,payload_hash,processed_at) values ('force-rollback',gen_random_uuid(),null,now())");
                            }))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_outbox_messages", Long.class))
            .isEqualTo(outboxBefore);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.issuance-result' and message_id=?",
                    Long.class,
                    selected.messageId()))
            .isZero();
        orderService.processBookingIssuanceResult(selected);
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void acknowledgesIssuanceResultOnlyAfterCommit() throws Exception {
    try {

      for (boolean completed : List.of(true, false)) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        UUID chargeId = UUID.randomUUID();
        orderService.acceptPayment(
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    chargeId,
                    UUID.randomUUID(),
                    purchase.total(),
                    purchase.currency(),
                    purchase.sourceOrderVersion())));
        var result =
            new BookingIssuanceResult(
                order.getOrderId(),
                chargeId,
                UUID.randomUUID(),
                BookingIssuanceResult.State.COMPLETED,
                2,
                null);
        var message =
            new Envelope<>(
                UUID.randomUUID(),
                "ticket.issued.v1",
                1,
                order.getOrderId(),
                purchase.sourceOrderVersion(),
                Instant.now(),
                order.getOrderId(),
                null,
                null,
                result);

        var selected =
            completed
                ? message
                : with(
                    with(message, "messageType", "booking.issuance-failed.v1"),
                    "data",
                    new BookingIssuanceResult(
                        order.getOrderId(),
                        chargeId,
                        result.issuanceId(),
                        BookingIssuanceResult.State.FAILED,
                        0,
                        "ERROR"));
        var connection = (CachingConnectionFactory) relayRabbitTemplate.getConnectionFactory();
        var admin = new RabbitAdmin(connection);
        String queue = "issuance-test-" + UUID.randomUUID(), dlq = queue + ".dlq";
        admin.declareQueue(new Queue(dlq, true, false, false, Map.of("x-queue-type", "quorum")));
        admin.declareQueue(
            new Queue(
                queue,
                true,
                false,
                false,
                Map.of(
                    "x-queue-type",
                    "quorum",
                    "x-delivery-limit",
                    3,
                    "x-dead-letter-exchange",
                    "",
                    "x-dead-letter-routing-key",
                    dlq)));
        var methods =
            new org.springframework.messaging.handler.annotation.support
                .DefaultMessageHandlerMethodFactory();
        methods.afterPropertiesSet();
        var endpoint = new MethodRabbitListenerEndpoint();
        endpoint.setId(queue);
        endpoint.setBean(
            new com.flashticket.booking_service.messaging.BookingIssuanceResultListener(
                orderService));
        endpoint.setMethod(
            com.flashticket.booking_service.messaging.BookingIssuanceResultListener.class.getMethod(
                completed ? "handleTicketsIssued" : "handleIssuanceFailed", Envelope.class));
        endpoint.setQueueNames(queue);
        endpoint.setMessageHandlerMethodFactory(methods);
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connection);
        factory.setMessageConverter(relayRabbitTemplate.getMessageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        factory.setErrorHandler(new BookingRabbitErrorHandler());
        var container = factory.createListenerContainer(endpoint);
        container.afterPropertiesSet();
        try {
          container.start();
          relayRabbitTemplate.convertAndSend("", queue, selected);
          Awaitility.await()
              .atMost(Duration.ofSeconds(15))
              .until(
                  () ->
                      jdbc.queryForObject(
                              "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.issuance-result' and message_id=?",
                              Long.class,
                              selected.messageId())
                          == 1);
          container.stop();
          assertThat(admin.getQueueInfo(queue).getMessageCount()).isZero();
          assertThat(
                  jdbc.queryForObject(
                      "select lifecycle_state from booking_schema.tbl_orders where id=?",
                      String.class,
                      order.getOrderId()))
              .isEqualTo(completed ? "COMPLETED" : "ISSUANCE_FAILED");
          container.start();
          relayRabbitTemplate.convertAndSend("", queue, selected);
          relayRabbitTemplate.convertAndSend(
              "",
              queue,
              with(
                  selected,
                  "messageType",
                  completed ? "booking.issuance-failed.v1" : "ticket.issued.v1"));
          Awaitility.await()
              .atMost(Duration.ofSeconds(10))
              .until(() -> relayRabbitTemplate.receive(dlq) != null);
          container.stop();
          assertThat(admin.getQueueInfo(queue).getMessageCount()).isZero();
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.issuance-result' and message_id=?",
                      Long.class,
                      selected.messageId()))
              .isEqualTo(1L);
        } finally {
          container.stop();
          container.destroy();
          admin.deleteQueue(queue);
          admin.deleteQueue(dlq);
        }
      }

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void relaysOrderCompletedThroughRabbitMq() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 10);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      var purchase = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
      UUID chargeId = UUID.randomUUID();
      orderService.acceptPayment(
          new Envelope<>(
              UUID.randomUUID(),
              "booking.accept-payment.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              new AcceptPayment(
                  order.getOrderId(),
                  chargeId,
                  UUID.randomUUID(),
                  purchase.total(),
                  purchase.currency(),
                  purchase.sourceOrderVersion())));
      var result =
          new BookingIssuanceResult(
              order.getOrderId(),
              chargeId,
              UUID.randomUUID(),
              BookingIssuanceResult.State.COMPLETED,
              2,
              null);
      var message =
          new Envelope<>(
              UUID.randomUUID(),
              "ticket.issued.v1",
              1,
              order.getOrderId(),
              purchase.sourceOrderVersion(),
              Instant.now(),
              order.getOrderId(),
              null,
              null,
              result);

      orderService.processBookingIssuanceResult(message);
      var worker =
          new com.flashticket.booking_service.messaging.BookingOutboxWorker(
              context.getBean(
                  com.flashticket.booking_service.messaging.repository.OutboxMessageRepository
                      .class),
              messagingService);
      org.springframework.test.util.ReflectionTestUtils.setField(worker, "batchSize", 20);
      worker.publishDueBookingMessages();
      var types = new HashSet<String>();
      for (int i = 0; i < 2; i++) {
        var received = relayRabbitTemplate.receive(RELAY_QUEUE, 10000);
        assertThat(received).isNotNull();
        assertThat(received.getMessageProperties().getReceivedDeliveryMode())
            .isEqualTo(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
        var json = JsonMapper.builder().build().readTree(received.getBody());
        types.add(json.get("messageType").asString());
        if ("booking.order-completed.v1".equals(json.get("messageType").asString())) {
          assertThat(json.get("causationId").asString()).isEqualTo(message.messageId().toString());
          assertThat(json.get("data").get("issuanceId").asString())
              .isEqualTo(result.issuanceId().toString());
        }
      }
      assertThat(types)
          .containsExactlyInAnyOrder("booking.payment-decision.v1", "booking.order-completed.v1");
      UUID responseId =
          jdbc.queryForObject(
              "select message_id from booking_schema.tbl_outbox_messages where message_type='booking.order-completed.v1'",
              UUID.class);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from booking_schema.tbl_outbox_messages where published_at is not null",
                  Long.class))
          .isEqualTo(2L);
      worker.publishDueBookingMessages();
      assertThat(relayRabbitTemplate.receive(RELAY_QUEUE)).isNull();
      jdbc.update(
          "update booking_schema.tbl_outbox_messages set published_at=null,next_attempt_at=now()-interval '1 second' where message_id=?",
          responseId);
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                messagingService.publishBookingMessage(responseId);
                status.setRollbackOnly();
              });
      var first = relayRabbitTemplate.receive(RELAY_QUEUE, 10000);
      assertThat(first).isNotNull();
      assertThat(
              jdbc.queryForObject(
                  "select published_at from booking_schema.tbl_outbox_messages where message_id=?",
                  java.sql.Timestamp.class,
                  responseId))
          .isNull();
      messagingService.publishBookingMessage(responseId);
      var second = relayRabbitTemplate.receive(RELAY_QUEUE, 10000);
      assertThat(second).isNotNull();
      assertThat(second.getMessageProperties().getMessageId())
          .isEqualTo(first.getMessageProperties().getMessageId());

      var admin = new RabbitAdmin(relayRabbitTemplate.getConnectionFactory());
      var binding =
          new org.springframework.amqp.core.Binding(
              RELAY_QUEUE,
              org.springframework.amqp.core.Binding.DestinationType.QUEUE,
              RELAY_EXCHANGE,
              "booking.order-completed.v1",
              Map.of());
      admin.removeBinding(binding);
      try {
        jdbc.update(
            "update booking_schema.tbl_outbox_messages set published_at=null,next_attempt_at=now()-interval '1 second' where message_id=?",
            responseId);
        messagingService.publishBookingMessage(responseId);
        var pending =
            jdbc.queryForMap(
                "select * from booking_schema.tbl_outbox_messages where message_id=?", responseId);
        assertThat(pending.get("published_at")).isNull();
        assertThat(pending.get("attempts")).isEqualTo(1);
        assertThat(pending.get("last_error_code")).isEqualTo("MESSAGE_PUBLISH_FAILED");
        assertThat(relayRabbitTemplate.receive(RELAY_QUEUE)).isNull();
      } finally {
        admin.declareBinding(binding);
      }
      jdbc.update(
          "update booking_schema.tbl_outbox_messages set next_attempt_at=now()-interval '1 second' where message_id=?",
          responseId);
      messagingService.publishBookingMessage(responseId);
      var retry = relayRabbitTemplate.receive(RELAY_QUEUE, 10000);
      assertThat(retry).isNotNull();
      assertThat(retry.getMessageProperties().getMessageId()).isEqualTo(responseId.toString());

    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
      new RabbitAdmin(relayRabbitTemplate.getConnectionFactory()).purgeQueue(RELAY_QUEUE);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesConcurrentExpiryForTheSameOrder() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMillis(300));
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      var ready = new CountDownLatch(2);
      var start = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var futures = new ArrayList<Future<?>>();
        for (int i = 0; i < 2; i++)
          futures.add(
              executor.submit(
                  () -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    orderService.expireOrder(order.getOrderId());
                    return null;
                  }));
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (var future : futures) future.get(10, TimeUnit.SECONDS);
      } finally {
        start.countDown();
      }
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("EXPIRED");
      assertThat(
              jdbc.queryForMap(
                  "select held,purchased from booking_schema.tbl_ticket_type_inventory where event_id=?",
                  config.eventId()))
          .containsEntry("held", 0L)
          .containsEntry("purchased", 0L);
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_purchase_limits where event_id=?",
                  Integer.class,
                  config.eventId()))
          .isZero();
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void arbitratesExpiryAgainstAcceptPaymentAfterLockWait() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      for (int scenario = 0; scenario < 3; scenario++) {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target, "reservationHoldDuration", Duration.ofSeconds(scenario == 0 ? 10 : 2));
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var frozen = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        var payment =
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                frozen.sourceOrderVersion(),
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    frozen.total(),
                    frozen.currency(),
                    frozen.sourceOrderVersion()));
        int current = scenario;
        if (scenario == 1)
          Awaitility.await()
              .atMost(Duration.ofSeconds(5))
              .until(
                  () ->
                      Boolean.TRUE.equals(
                          jdbc.queryForObject(
                              "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                              Boolean.class,
                              order.getOrderId())));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
          if (scenario < 2) {
            var first =
                executor.submit(
                    () ->
                        new TransactionTemplate(transactionManager)
                            .execute(
                                status -> {
                                  Object result;
                                  if (current == 0) result = orderService.acceptPayment(payment);
                                  else {
                                    orderService.expireOrder(order.getOrderId());
                                    result = "expired";
                                  }
                                  locked.countDown();
                                  try {
                                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                                  } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(e);
                                  }
                                  return result;
                                }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var second =
                executor.submit(
                    () -> {
                      if (current == 0) {
                        orderService.expireOrder(order.getOrderId());
                        return null;
                      }
                      return orderService.acceptPayment(payment);
                    });
            try {
              Awaitility.await()
                  .atMost(Duration.ofSeconds(3))
                  .until(
                      () ->
                          jdbc.queryForObject(
                                  "select count(*) from pg_locks where not granted", Long.class)
                              > 0);
            } finally {
              release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            var outcome = second.get(10, TimeUnit.SECONDS);
            if (current == 1)
              assertThat(
                      ((com.flashticket.booking_service.dto.message.PaymentDecision) outcome)
                          .outcome())
                  .isEqualTo(
                      com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.LATE);
          } else {
            // Payment đã giữ order nhưng chờ inventory đến quá deadline; không được ACCEPTED.
            try (var blocker = jdbc.getDataSource().getConnection()) {
              blocker.setAutoCommit(false);
              try (var statement =
                  blocker.prepareStatement(
                      "select ticket_type_id from booking_schema.tbl_ticket_type_inventory where event_id=? for update")) {
                statement.setObject(1, config.eventId());
                statement.executeQuery().close();
              }
              var paid = executor.submit(() -> orderService.acceptPayment(payment));
              try {
                Awaitility.await()
                    .atMost(Duration.ofSeconds(3))
                    .until(
                        () ->
                            jdbc.queryForObject(
                                    "select count(*) from pg_locks where not granted", Long.class)
                                > 0);
                var expired =
                    executor.submit(
                        () -> {
                          orderService.expireOrder(order.getOrderId());
                          return null;
                        });
                Awaitility.await()
                    .atMost(Duration.ofSeconds(4))
                    .until(
                        () ->
                            Boolean.TRUE.equals(
                                jdbc.queryForObject(
                                    "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                                    Boolean.class,
                                    order.getOrderId())));
                blocker.commit();
                assertThat(paid.get(10, TimeUnit.SECONDS).outcome())
                    .isEqualTo(
                        com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.LATE);
                expired.get(10, TimeUnit.SECONDS);
              } finally {
                blocker.rollback();
              }
            }
          }
        } finally {
          release.countDown();
        }
        assertThat(
                jdbc.queryForObject(
                    "select lifecycle_state from booking_schema.tbl_orders where id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo(scenario == 0 ? "ISSUING" : "EXPIRED");
        assertThat(
                jdbc.queryForMap(
                    "select held,purchased from booking_schema.tbl_ticket_type_inventory where event_id=?",
                    config.eventId()))
            .containsEntry("held", 0L)
            .containsEntry("purchased", scenario == 0 ? 2L : 0L);
      }
      // Chỉ biên equality dùng spy clock; query/khóa và transaction vẫn là PostgreSQL thật.
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMinutes(15));
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("equality", UUID.randomUUID(), createOrderRequest(config, 1));
      Instant expiresAt =
          jdbc.queryForObject(
                  "select expires_at from booking_schema.tbl_orders where id=?",
                  java.sql.Timestamp.class,
                  order.getOrderId())
              .toInstant();
      org.mockito.Mockito.doReturn(expiresAt).when(orders).currentDatabaseTime();
      orderService.expireOrder(order.getOrderId());
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("EXPIRED");
    } finally {
      org.mockito.Mockito.reset(orders);
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollsBackExpiryWhenReservationDataConflicts() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofSeconds(1));
      for (int scenario = 0; scenario < 6; scenario++) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var promo =
            new PromotionConfig(
                UUID.randomUUID(),
                "SAVE",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                "10",
                1,
                new PromotionConfig.PercentDiscount("PERCENT", 10));
        config = with(with(config, "promotions", List.of(promo)), "sourceConfigVersion", "2");
        salesService.synchronizeSalesConfiguration(
            new Envelope<>(
                UUID.randomUUID(),
                "event.sales-configured.v1",
                1,
                config.eventId(),
                "2",
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                config));
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        promotionService.applyPromotionToOrder(
            "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
        if (scenario == 0)
          jdbc.update(
              "update booking_schema.tbl_ticket_type_inventory set held=0 where event_id=?",
              config.eventId());
        if (scenario == 1)
          jdbc.update(
              "update booking_schema.tbl_purchase_limits set held=0 where event_id=?",
              config.eventId());
        if (scenario == 2)
          jdbc.update(
              "update booking_schema.tbl_reservations set state='RELEASED' where order_id=?",
              order.getOrderId());
        if (scenario == 3)
          jdbc.update(
              "update booking_schema.tbl_promotion_usages set state='USED' where event_id=?",
              config.eventId());
        if (scenario == 4)
          jdbc.update(
              "update booking_schema.tbl_promotion_inventory set held=0 where event_id=?",
              config.eventId());
        if (scenario == 5) {
          orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
          jdbc.update(
              "update booking_schema.tbl_orders set accepted_charge_id=?,accepted_at=payment_frozen_at where id=?",
              UUID.randomUUID(),
              order.getOrderId());
        }
        Awaitility.await()
            .atMost(Duration.ofSeconds(5))
            .until(
                () ->
                    Boolean.TRUE.equals(
                        jdbc.queryForObject(
                            "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                            Boolean.class,
                            order.getOrderId())));
        var before = captureOrderCreationState(config.eventId());
        var usageBefore =
            jdbc.queryForList(
                "select * from booking_schema.tbl_promotion_usages where event_id=?",
                config.eventId());
        assertThatThrownBy(() -> orderService.expireOrder(order.getOrderId()))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DATA_CONFLICT));
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
        assertThat(
                jdbc.queryForList(
                    "select * from booking_schema.tbl_promotion_usages where event_id=?",
                    config.eventId()))
            .isEqualTo(usageBefore);
      }
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollsBackExpiryWhenCommitFailsAndRetriesSafely() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMillis(300));
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      jdbc.execute(
          """
          create function booking_schema.expiry_test_reject() returns trigger language plpgsql as $$
          begin
            if new.lifecycle_state = 'EXPIRED' then
              raise exception 'expiry commit rejected' using errcode = '23514';
            end if;
            return new;
          end $$
          """);
      jdbc.execute(
          "create constraint trigger expiry_test_commit_failure after update on booking_schema.tbl_orders deferrable initially deferred for each row execute function booking_schema.expiry_test_reject()");
      var before = captureOrderCreationState(config.eventId());
      assertThatThrownBy(() -> orderService.expireOrder(order.getOrderId()))
          .isInstanceOf(RuntimeException.class)
          .hasStackTraceContaining("expiry commit rejected");
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      jdbc.execute("drop trigger expiry_test_commit_failure on booking_schema.tbl_orders");
      orderService.expireOrder(order.getOrderId());
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("EXPIRED");
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_ticket_type_inventory where event_id=?",
                  Long.class,
                  config.eventId()))
          .isZero();
    } finally {
      jdbc.execute(
          "drop trigger if exists expiry_test_commit_failure on booking_schema.tbl_orders");
      jdbc.execute("drop function if exists booking_schema.expiry_test_reject()");
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void preservesEventCancellationDuringOrderExpiry() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMillis(300));
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order = orderService.createOrder("due", UUID.randomUUID(), createOrderRequest(config, 2));
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMinutes(15));
      var unexpired =
          orderService.createOrder("later", UUID.randomUUID(), createOrderRequest(config, 1));
      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED',sale_ends_at=clock_timestamp()-interval '1 second' where event_id=?",
          config.eventId());
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      orderService.expireOrder(order.getOrderId());
      orderService.expireOrder(unexpired.getOrderId());
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  order.getOrderId()))
          .isEqualTo("CANCELLED");
      assertThat(
              jdbc.queryForObject(
                  "select lifecycle_state from booking_schema.tbl_orders where id=?",
                  String.class,
                  unexpired.getOrderId()))
          .isEqualTo("PENDING_PAYMENT");
      assertThat(
              jdbc.queryForObject(
                  "select held from booking_schema.tbl_ticket_type_inventory where event_id=?",
                  Long.class,
                  config.eventId()))
          .isEqualTo(1L);
      var before = captureOrderCreationState(config.eventId());
      orderService.expireOrder(order.getOrderId());
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesExpiryWithPromotionAndSnapshotPreparation() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofSeconds(2));
      for (boolean snapshot : List.of(false, true)) {
        for (boolean expiryFirst : List.of(false, true)) {
          var base = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
          var promo =
              new PromotionConfig(
                  UUID.randomUUID(),
                  "SAVE",
                  Instant.now().minusSeconds(60),
                  Instant.now().plusSeconds(3600),
                  "10",
                  1,
                  new PromotionConfig.PercentDiscount("PERCENT", 10));
          var config = with(with(base, "promotions", List.of(promo)), "sourceConfigVersion", "2");
          salesService.synchronizeSalesConfiguration(
              new Envelope<>(
                  UUID.randomUUID(),
                  "event.sales-configured.v1",
                  1,
                  config.eventId(),
                  "2",
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  config));
          var order =
              orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
          if (snapshot)
            promotionService.applyPromotionToOrder(
                "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
          if (expiryFirst)
            Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(
                    () ->
                        Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                                Boolean.class,
                                order.getOrderId())));
          var locked = new CountDownLatch(1);
          var release = new CountDownLatch(1);
          try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                executor.submit(
                    () ->
                        new TransactionTemplate(transactionManager)
                            .execute(
                                status -> {
                                  if (expiryFirst) orderService.expireOrder(order.getOrderId());
                                  else if (snapshot)
                                    orderService.preparePurchaseSnapshot(
                                        "buyer", order.getOrderId());
                                  else
                                    promotionService.applyPromotionToOrder(
                                        "buyer",
                                        order.getOrderId(),
                                        new ApplyPromotionRequest("SAVE"));
                                  locked.countDown();
                                  try {
                                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                                  } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(e);
                                  }
                                  return null;
                                }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var second =
                executor.submit(
                    () -> {
                      try {
                        if (!expiryFirst) {
                          orderService.expireOrder(order.getOrderId());
                          return null;
                        }
                        return snapshot
                            ? orderService.preparePurchaseSnapshot("buyer", order.getOrderId())
                            : promotionService.applyPromotionToOrder(
                                "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
                      } catch (RuntimeException exception) {
                        return exception;
                      }
                    });
            try {
              Awaitility.await()
                  .atMost(Duration.ofSeconds(3))
                  .until(
                      () ->
                          jdbc.queryForObject(
                                  "select count(*) from pg_locks where not granted", Long.class)
                              > 0);
              if (!expiryFirst)
                Awaitility.await()
                    .atMost(Duration.ofSeconds(4))
                    .until(
                        () ->
                            Boolean.TRUE.equals(
                                jdbc.queryForObject(
                                    "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                                    Boolean.class,
                                    order.getOrderId())));
            } finally {
              release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            Object outcome = second.get(10, TimeUnit.SECONDS);
            if (expiryFirst) assertThat(outcome).isInstanceOf(BusinessException.class);
            else assertThat(outcome).isNull();
          } finally {
            release.countDown();
          }
          assertThat(
                  jdbc.queryForObject(
                      "select lifecycle_state from booking_schema.tbl_orders where id=?",
                      String.class,
                      order.getOrderId()))
              .isEqualTo("EXPIRED");
          assertThat(
                  jdbc.queryForObject(
                      "select held from booking_schema.tbl_promotion_inventory where event_id=?",
                      Long.class,
                      config.eventId()))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from booking_schema.tbl_promotion_usages where event_id=? and state='HELD'",
                      Long.class,
                      config.eventId()))
              .isZero();
          if (expiryFirst)
            assertThat(
                    jdbc.queryForObject(
                        "select payment_frozen_at from booking_schema.tbl_orders where id=?",
                        java.sql.Timestamp.class,
                        order.getOrderId()))
                .isNull();
        }
      }
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void cancelsPendingOrdersAcrossInventoryModesAndFreezeStates() throws Exception {
    try {
      for (int mode = 0; mode < 3; mode++) {
        for (boolean voucher : List.of(false, true)) {
          for (boolean frozen : List.of(false, true)) {
            var config =
                seedOrderCreationConfiguration(
                    mode == 0 ? SalesMode.QUANTITY : SalesMode.SEAT_MAP,
                    mode == 0 ? null : mode == 1 ? SectorType.STANDING : SectorType.SEATED,
                    20,
                    5);
            if (voucher) {
              var promo =
                  new PromotionConfig(
                      UUID.randomUUID(),
                      "SAVE",
                      Instant.now().minusSeconds(60),
                      Instant.now().plusSeconds(3600),
                      "10",
                      1,
                      new PromotionConfig.PercentDiscount("PERCENT", 10));
              config = with(with(config, "promotions", List.of(promo)), "sourceConfigVersion", "2");
              salesService.synchronizeSalesConfiguration(
                  new Envelope<>(
                      UUID.randomUUID(),
                      "event.sales-configured.v1",
                      1,
                      config.eventId(),
                      "2",
                      Instant.now(),
                      UUID.randomUUID(),
                      null,
                      null,
                      config));
            }
            var order =
                orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
            if (voucher)
              promotionService.applyPromotionToOrder(
                  "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
            if (frozen) orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
            var money =
                jdbc.queryForMap(
                    "select subtotal,discount,total,expires_at,payment_frozen_at,frozen_purchase_snapshot::text as frozen from booking_schema.tbl_orders where id=?",
                    order.getOrderId());
            if (voucher)
              jdbc.update(
                  "update booking_schema.tbl_promotion_inventory set ends_at=clock_timestamp()-interval '1 second' where event_id=?",
                  config.eventId());
            assertThat(orderService.cancelOrder("buyer", order.getOrderId()).getState())
                .isEqualTo(OrderState.CANCELLED);
            assertThat(
                    jdbc.queryForMap(
                        "select subtotal,discount,total,expires_at,payment_frozen_at,frozen_purchase_snapshot::text as frozen from booking_schema.tbl_orders where id=?",
                        order.getOrderId()))
                .isEqualTo(money);
            assertThat(
                    jdbc.queryForObject(
                        "select state from booking_schema.tbl_reservations where order_id=?",
                        String.class,
                        order.getOrderId()))
                .isEqualTo("RELEASED");
            assertThat(
                    jdbc.queryForList(
                        "select state from booking_schema.tbl_reservation_allocations where order_id=?",
                        String.class,
                        order.getOrderId()))
                .containsOnly("RELEASED");
            assertThat(
                    jdbc.queryForMap(
                        "select held,purchased from booking_schema.tbl_purchase_limits where event_id=?",
                        config.eventId()))
                .containsEntry("held", 0)
                .containsEntry("purchased", 0);
            if (mode == 0)
              assertThat(
                      jdbc.queryForMap(
                          "select held,purchased from booking_schema.tbl_ticket_type_inventory where event_id=?",
                          config.eventId()))
                  .containsEntry("held", 0L)
                  .containsEntry("purchased", 0L);
            else if (mode == 1)
              assertThat(
                      jdbc.queryForMap(
                          "select held,purchased from booking_schema.tbl_sector_inventory where event_id=?",
                          config.eventId()))
                  .containsEntry("held", 0L)
                  .containsEntry("purchased", 0L);
            else {
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and (state <> 'AVAILABLE' or current_reservation_id is not null)",
                          Long.class,
                          config.eventId()))
                  .isZero();
              assertThat(
                      jdbc.queryForObject(
                          "select count(*) from booking_schema.tbl_seat_inventory where event_id=? and reservation_generation=1",
                          Long.class,
                          config.eventId()))
                  .isEqualTo(2);
            }
            if (voucher) {
              assertThat(
                      jdbc.queryForMap(
                          "select held,used from booking_schema.tbl_promotion_inventory where event_id=?",
                          config.eventId()))
                  .containsEntry("held", 0L)
                  .containsEntry("used", 0L);
              assertThat(
                      jdbc.queryForObject(
                          "select state from booking_schema.tbl_promotion_usages where event_id=?",
                          String.class,
                          config.eventId()))
                  .isEqualTo("RELEASED");
            }
            assertThat(
                    jdbc.queryForObject(
                        "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                        Long.class,
                        order.getOrderId()))
                .isZero();
          }
        }
      }
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void replaysCancellationWithoutReleasingNewReservations() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.SEAT_MAP, SectorType.SEATED, 4, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 1));
      var start = new CountDownLatch(1);
      try (var executor = Executors.newFixedThreadPool(2)) {
        var first =
            executor.submit(
                () -> {
                  start.await();
                  return orderService.cancelOrder("buyer", order.getOrderId());
                });
        var second =
            executor.submit(
                () -> {
                  start.await();
                  return orderService.cancelOrder("buyer", order.getOrderId());
                });
        start.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(second.get(10, TimeUnit.SECONDS).getState()).isEqualTo(OrderState.CANCELLED);
      } finally {
        start.countDown();
      }
      orderService.createOrder("next", UUID.randomUUID(), createOrderRequest(config, 1));
      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
          config.eventId());
      var before = captureOrderCreationState(config.eventId());
      Instant afterExpiry = order.getExpiresAt().plusSeconds(1);
      org.mockito.Mockito.doReturn(afterExpiry).when(orders).currentDatabaseTime();
      assertThat(orderService.cancelOrder("buyer", order.getOrderId()).getState())
          .isEqualTo(OrderState.CANCELLED);
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      assertThat(
              jdbc.queryForObject(
                  "select reservation_generation from booking_schema.tbl_seat_inventory where seat_id=?",
                  Long.class,
                  config.seats().getFirst().seatId()))
          .isEqualTo(2);
    } finally {
      org.mockito.Mockito.reset(orders);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void hidesOrdersFromOtherBuyersAndRejectsInvalidCancellationInputs() throws Exception {
    try {
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("owner", UUID.randomUUID(), createOrderRequest(config, 1));
      var before = captureOrderCreationState(config.eventId());
      for (UUID id : List.of(order.getOrderId(), UUID.randomUUID()))
        assertThatThrownBy(() -> orderService.cancelOrder("other", id))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_FOUND));
      for (String subject : Arrays.asList(null, "", " "))
        assertThatThrownBy(() -> orderService.cancelOrder(subject, order.getOrderId()))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
      assertThatThrownBy(() -> orderService.cancelOrder("owner", null))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsUncancellableStatesAndExpiredReservations() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      for (int terminal = 0; terminal < 3; terminal++) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var frozen = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        UUID charge = UUID.randomUUID();
        orderService.acceptPayment(
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                frozen.sourceOrderVersion(),
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    charge,
                    UUID.randomUUID(),
                    frozen.total(),
                    frozen.currency(),
                    frozen.sourceOrderVersion())));
        if (terminal > 0)
          orderService.processBookingIssuanceResult(
              new Envelope<>(
                  UUID.randomUUID(),
                  terminal == 1 ? "ticket.issued.v1" : "booking.issuance-failed.v1",
                  1,
                  order.getOrderId(),
                  frozen.sourceOrderVersion(),
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  new BookingIssuanceResult(
                      order.getOrderId(),
                      charge,
                      UUID.randomUUID(),
                      terminal == 1
                          ? BookingIssuanceResult.State.COMPLETED
                          : BookingIssuanceResult.State.FAILED,
                      terminal == 1 ? 2 : 0,
                      terminal == 1 ? null : "REJECTED")));
        var before = captureOrderCreationState(config.eventId());
        assertThatThrownBy(() -> orderService.cancelOrder("buyer", order.getOrderId()))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      }
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofMillis(300));
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 1));
      Awaitility.await()
          .atMost(Duration.ofSeconds(5))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                          Boolean.class,
                          order.getOrderId())));
      for (boolean expire : List.of(false, true)) {
        if (expire) orderService.expireOrder(order.getOrderId());
        var before = captureOrderCreationState(config.eventId());
        assertThatThrownBy(() -> orderService.cancelOrder("buyer", order.getOrderId()))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      }
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      var cancelledConfig = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var pending =
          orderService.createOrder(
              "buyer", UUID.randomUUID(), createOrderRequest(cancelledConfig, 1));
      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
          cancelledConfig.eventId());
      var before = captureOrderCreationState(cancelledConfig.eventId());
      assertThatThrownBy(() -> orderService.cancelOrder("buyer", pending.getOrderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_ON_SALE));
      assertThat(captureOrderCreationState(cancelledConfig.eventId())).isEqualTo(before);
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void arbitratesBuyerCancellationAgainstAcceptPayment() throws Exception {
    try {
      for (boolean paymentFirst : List.of(false, true)) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        var frozen = orderService.preparePurchaseSnapshot("buyer", order.getOrderId());
        var payment =
            new Envelope<>(
                UUID.randomUUID(),
                "booking.accept-payment.v1",
                1,
                order.getOrderId(),
                frozen.sourceOrderVersion(),
                Instant.now(),
                UUID.randomUUID(),
                null,
                null,
                new AcceptPayment(
                    order.getOrderId(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    frozen.total(),
                    frozen.currency(),
                    frozen.sourceOrderVersion()));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
          var first =
              executor.submit(
                  () ->
                      new TransactionTemplate(transactionManager)
                          .execute(
                              status -> {
                                if (paymentFirst) orderService.acceptPayment(payment);
                                else orderService.cancelOrder("buyer", order.getOrderId());
                                locked.countDown();
                                try {
                                  assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                                } catch (InterruptedException e) {
                                  Thread.currentThread().interrupt();
                                  throw new IllegalStateException(e);
                                }
                                return null;
                              }));
          assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
          var second =
              executor.submit(
                  () -> {
                    try {
                      return paymentFirst
                          ? orderService.cancelOrder("buyer", order.getOrderId())
                          : orderService.acceptPayment(payment);
                    } catch (RuntimeException e) {
                      return e;
                    }
                  });
          try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(
                    () ->
                        jdbc.queryForObject(
                                "select count(*) from pg_locks where not granted", Long.class)
                            > 0);
          } finally {
            release.countDown();
          }
          first.get(10, TimeUnit.SECONDS);
          Object result = second.get(10, TimeUnit.SECONDS);
          if (paymentFirst)
            assertThat(result)
                .isInstanceOfSatisfying(
                    BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_CANCELLABLE));
          else
            assertThat(
                    ((com.flashticket.booking_service.dto.message.PaymentDecision) result)
                        .outcome())
                .isEqualTo(
                    com.flashticket.booking_service.dto.message.PaymentDecision.Outcome.CANCELLED);
        } finally {
          release.countDown();
        }
        assertThat(
                jdbc.queryForObject(
                    "select lifecycle_state from booking_schema.tbl_orders where id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo(paymentFirst ? "ISSUING" : "CANCELLED");
        assertThat(
                jdbc.queryForMap(
                    "select held,purchased from booking_schema.tbl_ticket_type_inventory where event_id=?",
                    config.eventId()))
            .containsEntry("held", 0L)
            .containsEntry("purchased", paymentFirst ? 2L : 0L);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_inbox_messages where consumer_name='booking.accept-payment'",
                    Long.class))
            .isEqualTo(paymentFirst ? 2 : 1);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                    Long.class,
                    order.getOrderId()))
            .isEqualTo(1);
      }
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void arbitratesBuyerCancellationAgainstExpiryAndDeadline() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationHoldDuration");
    try {
      for (boolean expiryFirst : List.of(false, true)) {
        org.springframework.test.util.ReflectionTestUtils.setField(
            target,
            "reservationHoldDuration",
            expiryFirst ? Duration.ofMillis(300) : Duration.ofMinutes(15));
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        if (expiryFirst)
          Awaitility.await()
              .atMost(Duration.ofSeconds(5))
              .until(
                  () ->
                      Boolean.TRUE.equals(
                          jdbc.queryForObject(
                              "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                              Boolean.class,
                              order.getOrderId())));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
          var first =
              executor.submit(
                  () ->
                      new TransactionTemplate(transactionManager)
                          .execute(
                              status -> {
                                if (expiryFirst) orderService.expireOrder(order.getOrderId());
                                else orderService.cancelOrder("buyer", order.getOrderId());
                                locked.countDown();
                                try {
                                  assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                                } catch (InterruptedException e) {
                                  Thread.currentThread().interrupt();
                                  throw new IllegalStateException(e);
                                }
                                return null;
                              }));
          assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
          var second =
              executor.submit(
                  () -> {
                    try {
                      if (expiryFirst) return orderService.cancelOrder("buyer", order.getOrderId());
                      orderService.expireOrder(order.getOrderId());
                      return null;
                    } catch (RuntimeException e) {
                      return e;
                    }
                  });
          try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(
                    () ->
                        jdbc.queryForObject(
                                "select count(*) from pg_locks where not granted", Long.class)
                            > 0);
          } finally {
            release.countDown();
          }
          first.get(10, TimeUnit.SECONDS);
          Object result = second.get(10, TimeUnit.SECONDS);
          if (expiryFirst)
            assertThat(result)
                .isInstanceOfSatisfying(
                    BusinessException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));
          else assertThat(result).isNull();
        } finally {
          release.countDown();
        }
        assertThat(
                jdbc.queryForObject(
                    "select lifecycle_state from booking_schema.tbl_orders where id=?",
                    String.class,
                    order.getOrderId()))
            .isEqualTo(expiryFirst ? "EXPIRED" : "CANCELLED");
        assertThat(
                jdbc.queryForObject(
                    "select held from booking_schema.tbl_ticket_type_inventory where event_id=?",
                    Long.class,
                    config.eventId()))
            .isZero();
      }
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", Duration.ofSeconds(2));
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("waiting", UUID.randomUUID(), createOrderRequest(config, 1));
      var before = captureOrderCreationState(config.eventId());
      try (var blocker = jdbc.getDataSource().getConnection();
          var executor = Executors.newSingleThreadExecutor()) {
        blocker.setAutoCommit(false);
        try (var statement =
            blocker.prepareStatement(
                "select ticket_type_id from booking_schema.tbl_ticket_type_inventory where event_id=? for update")) {
          statement.setObject(1, config.eventId());
          statement.executeQuery().close();
        }
        var waiting =
            executor.submit(
                () -> {
                  try {
                    return orderService.cancelOrder("waiting", order.getOrderId());
                  } catch (RuntimeException e) {
                    return e;
                  }
                });
        try {
          Awaitility.await()
              .atMost(Duration.ofSeconds(3))
              .until(
                  () ->
                      jdbc.queryForObject(
                              "select count(*) from pg_locks where not granted", Long.class)
                          > 0);
          Awaitility.await()
              .atMost(Duration.ofSeconds(4))
              .until(
                  () ->
                      Boolean.TRUE.equals(
                          jdbc.queryForObject(
                              "select clock_timestamp() >= expires_at from booking_schema.tbl_orders where id=?",
                              Boolean.class,
                              order.getOrderId())));
        } finally {
          blocker.rollback();
        }
        assertThat(waiting.get(10, TimeUnit.SECONDS))
            .isInstanceOfSatisfying(
                BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));
      }
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
      orderService.expireOrder(order.getOrderId());
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      var equality =
          orderService.createOrder("equality", UUID.randomUUID(), createOrderRequest(config, 1));
      var equalityBefore = captureOrderCreationState(config.eventId());
      Instant expiresAt =
          jdbc.queryForObject(
                  "select expires_at from booking_schema.tbl_orders where id=?",
                  java.sql.Timestamp.class,
                  equality.getOrderId())
              .toInstant();
      org.mockito.Mockito.doReturn(expiresAt).when(orders).currentDatabaseTime();
      assertThatThrownBy(() -> orderService.cancelOrder("equality", equality.getOrderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_EXPIRED));
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(equalityBefore);
    } finally {
      org.mockito.Mockito.reset(orders);
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationHoldDuration", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void serializesBuyerCancellationWithPromotionAndSnapshot() throws Exception {
    try {
      for (boolean snapshot : List.of(false, true)) {
        for (boolean cancelFirst : List.of(false, true)) {
          var base = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
          var promo =
              new PromotionConfig(
                  UUID.randomUUID(),
                  "SAVE",
                  Instant.now().minusSeconds(60),
                  Instant.now().plusSeconds(3600),
                  "10",
                  1,
                  new PromotionConfig.PercentDiscount("PERCENT", 10));
          var config = with(with(base, "promotions", List.of(promo)), "sourceConfigVersion", "2");
          salesService.synchronizeSalesConfiguration(
              new Envelope<>(
                  UUID.randomUUID(),
                  "event.sales-configured.v1",
                  1,
                  config.eventId(),
                  "2",
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  config));
          var order =
              orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
          if (snapshot)
            promotionService.applyPromotionToOrder(
                "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
          var locked = new CountDownLatch(1);
          var release = new CountDownLatch(1);
          try (var executor = Executors.newFixedThreadPool(2)) {
            var first =
                executor.submit(
                    () ->
                        new TransactionTemplate(transactionManager)
                            .execute(
                                status -> {
                                  if (cancelFirst)
                                    orderService.cancelOrder("buyer", order.getOrderId());
                                  else if (snapshot)
                                    orderService.preparePurchaseSnapshot(
                                        "buyer", order.getOrderId());
                                  else
                                    promotionService.applyPromotionToOrder(
                                        "buyer",
                                        order.getOrderId(),
                                        new ApplyPromotionRequest("SAVE"));
                                  locked.countDown();
                                  try {
                                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                                  } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException(e);
                                  }
                                  return null;
                                }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var second =
                executor.submit(
                    () -> {
                      try {
                        if (!cancelFirst)
                          return orderService.cancelOrder("buyer", order.getOrderId());
                        return snapshot
                            ? orderService.preparePurchaseSnapshot("buyer", order.getOrderId())
                            : promotionService.applyPromotionToOrder(
                                "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
                      } catch (RuntimeException e) {
                        return e;
                      }
                    });
            try {
              Awaitility.await()
                  .atMost(Duration.ofSeconds(3))
                  .until(
                      () ->
                          jdbc.queryForObject(
                                  "select count(*) from pg_locks where not granted", Long.class)
                              > 0);
            } finally {
              release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
            Object result = second.get(10, TimeUnit.SECONDS);
            if (cancelFirst)
              assertThat(result)
                  .isInstanceOfSatisfying(
                      BusinessException.class,
                      e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_PAYABLE));
            else assertThat(((OrderView) result).getState()).isEqualTo(OrderState.CANCELLED);
          } finally {
            release.countDown();
          }
          assertThat(
                  jdbc.queryForObject(
                      "select lifecycle_state from booking_schema.tbl_orders where id=?",
                      String.class,
                      order.getOrderId()))
              .isEqualTo("CANCELLED");
          assertThat(
                  jdbc.queryForObject(
                      "select held from booking_schema.tbl_promotion_inventory where event_id=?",
                      Long.class,
                      config.eventId()))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "select count(*) from booking_schema.tbl_promotion_usages where event_id=? and state='HELD'",
                      Long.class,
                      config.eventId()))
              .isZero();
        }
      }
      var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
      var order =
          orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 1));
      jdbc.update(
          "update booking_schema.tbl_event_sales_snapshots set event_status='CANCELLED' where event_id=?",
          config.eventId());
      var before = captureOrderCreationState(config.eventId());
      assertThatThrownBy(() -> orderService.cancelOrder("buyer", order.getOrderId()))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EVENT_NOT_ON_SALE));
      assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
    } finally {
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rollsBackBuyerCancellationOnResourceAndCommitFailures() throws Exception {
    Object target =
        org.springframework.test.util.AopTestUtils.getUltimateTargetObject(orderService);
    Object previous =
        org.springframework.test.util.ReflectionTestUtils.getField(
            target, "reservationLockTimeout");
    try {
      for (int failure = 0; failure < 3; failure++) {
        var config = seedOrderCreationConfiguration(SalesMode.QUANTITY, null, 20, 5);
        if (failure == 1) {
          var promo =
              new PromotionConfig(
                  UUID.randomUUID(),
                  "SAVE",
                  Instant.now().minusSeconds(60),
                  Instant.now().plusSeconds(3600),
                  "10",
                  1,
                  new PromotionConfig.PercentDiscount("PERCENT", 10));
          var promoted =
              with(with(config, "promotions", List.of(promo)), "sourceConfigVersion", "2");
          salesService.synchronizeSalesConfiguration(
              new Envelope<>(
                  UUID.randomUUID(),
                  "event.sales-configured.v1",
                  1,
                  config.eventId(),
                  "2",
                  Instant.now(),
                  UUID.randomUUID(),
                  null,
                  null,
                  promoted));
        }
        var order =
            orderService.createOrder("buyer", UUID.randomUUID(), createOrderRequest(config, 2));
        if (failure == 1)
          promotionService.applyPromotionToOrder(
              "buyer", order.getOrderId(), new ApplyPromotionRequest("SAVE"));
        if (failure == 0)
          jdbc.update(
              "update booking_schema.tbl_ticket_type_inventory set held=0 where event_id=?",
              config.eventId());
        if (failure == 1) {
          jdbc.execute(
              """
              create function booking_schema.cancel_test_reject() returns trigger language plpgsql as $$
              begin
                if new.lifecycle_state = 'CANCELLED' then
                  raise exception 'cancel commit rejected' using errcode = '23514';
                end if;
                return new;
              end $$
              """);
          jdbc.execute(
              "create constraint trigger cancel_test_commit_failure after update on booking_schema.tbl_orders deferrable initially deferred for each row execute function booking_schema.cancel_test_reject()");
        }
        var before = captureOrderCreationState(config.eventId());
        var promotionBefore =
            jdbc.queryForList(
                "select * from booking_schema.tbl_promotion_inventory where event_id=?",
                config.eventId());
        var usageBefore =
            jdbc.queryForList(
                "select * from booking_schema.tbl_promotion_usages where event_id=?",
                config.eventId());
        if (failure == 2) {
          org.springframework.test.util.ReflectionTestUtils.setField(
              target, "reservationLockTimeout", "250ms");
          try (var blocker = jdbc.getDataSource().getConnection();
              var executor = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var statement =
                blocker.prepareStatement(
                    "select ticket_type_id from booking_schema.tbl_ticket_type_inventory where event_id=? for update")) {
              statement.setObject(1, config.eventId());
              statement.executeQuery().close();
            }
            try {
              var waiting =
                  executor.submit(
                      () -> {
                        try {
                          return orderService.cancelOrder("buyer", order.getOrderId());
                        } catch (RuntimeException e) {
                          return e;
                        }
                      });
              assertThat(waiting.get(5, TimeUnit.SECONDS)).isInstanceOf(RuntimeException.class);
            } finally {
              blocker.rollback();
            }
          }
          org.springframework.test.util.ReflectionTestUtils.setField(
              target, "reservationLockTimeout", previous);
        } else if (failure == 0)
          assertThatThrownBy(() -> orderService.cancelOrder("buyer", order.getOrderId()))
              .isInstanceOfSatisfying(
                  BusinessException.class,
                  e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESERVATION_DATA_CONFLICT));
        else
          assertThatThrownBy(() -> orderService.cancelOrder("buyer", order.getOrderId()))
              .isInstanceOf(RuntimeException.class)
              .hasStackTraceContaining("cancel commit rejected");
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(before);
        assertThat(
                jdbc.queryForList(
                    "select * from booking_schema.tbl_promotion_inventory where event_id=?",
                    config.eventId()))
            .isEqualTo(promotionBefore);
        assertThat(
                jdbc.queryForList(
                    "select * from booking_schema.tbl_promotion_usages where event_id=?",
                    config.eventId()))
            .isEqualTo(usageBefore);
        if (failure == 0)
          jdbc.update(
              "update booking_schema.tbl_ticket_type_inventory set held=2 where event_id=?",
              config.eventId());
        if (failure == 1)
          jdbc.execute("drop trigger cancel_test_commit_failure on booking_schema.tbl_orders");
        assertThat(orderService.cancelOrder("buyer", order.getOrderId()).getState())
            .isEqualTo(OrderState.CANCELLED);
        if (failure == 1) {
          assertThat(
                  jdbc.queryForMap(
                      "select held,used from booking_schema.tbl_promotion_inventory where event_id=?",
                      config.eventId()))
              .containsEntry("held", 0L)
              .containsEntry("used", 0L);
          assertThat(
                  jdbc.queryForObject(
                      "select state from booking_schema.tbl_promotion_usages where event_id=?",
                      String.class,
                      config.eventId()))
              .isEqualTo("RELEASED");
        }
        var after = captureOrderCreationState(config.eventId());
        orderService.cancelOrder("buyer", order.getOrderId());
        assertThat(captureOrderCreationState(config.eventId())).isEqualTo(after);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from booking_schema.tbl_outbox_messages where aggregate_id=?",
                    Long.class,
                    order.getOrderId()))
            .isZero();
      }
    } finally {
      jdbc.execute(
          "drop trigger if exists cancel_test_commit_failure on booking_schema.tbl_orders");
      jdbc.execute("drop function if exists booking_schema.cancel_test_reject()");
      org.springframework.test.util.ReflectionTestUtils.setField(
          target, "reservationLockTimeout", previous);
      jdbc.execute(
          "truncate booking_schema.tbl_event_sales_snapshots, booking_schema.tbl_inbox_messages, booking_schema.tbl_outbox_messages cascade");
    }
  }

  private SalesConfiguration seedOrderCreationConfiguration(
      SalesMode mode, SectorType sectorType, long capacity, int purchaseLimit) {
    Instant now =
        jdbc.queryForObject("select clock_timestamp()", java.sql.Timestamp.class).toInstant();
    UUID eventId = UUID.randomUUID();
    UUID firstType = UUID.randomUUID();
    UUID secondType = UUID.randomUUID();
    UUID sectorId = mode == SalesMode.SEAT_MAP ? UUID.randomUUID() : null;
    var types = new ArrayList<SalesConfiguration.TicketTypeConfig>();
    types.add(
        new SalesConfiguration.TicketTypeConfig(
            firstType,
            "Standard",
            "100",
            Currency.VND,
            sectorId,
            mode == SalesMode.QUANTITY ? Long.toString(capacity) : null,
            null));
    if (mode == SalesMode.SEAT_MAP)
      types.add(
          new SalesConfiguration.TicketTypeConfig(
              secondType, "VIP", "200", Currency.VND, sectorId, null, null));
    var sectors =
        mode == SalesMode.SEAT_MAP
            ? List.of(
                new SalesConfiguration.SectorConfig(sectorId, sectorType, Long.toString(capacity)))
            : List.<SalesConfiguration.SectorConfig>of();
    var seats = new ArrayList<SalesConfiguration.SeatConfig>();
    if (sectorType == SectorType.SEATED)
      for (int i = 1; i <= capacity; i++)
        seats.add(
            new SalesConfiguration.SeatConfig(
                UUID.randomUUID(),
                sectorId,
                i % 2 == 1 ? firstType : secondType,
                "A" + i,
                "A",
                Integer.toString(i),
                false));
    var config =
        new SalesConfiguration(
            1,
            eventId,
            "organizer",
            "1",
            SalesConfiguration.EventStatus.PUBLISHED,
            "Concert",
            now.plusSeconds(7200),
            now.plusSeconds(10800),
            null,
            mode,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            purchaseLimit,
            types,
            sectors,
            seats,
            List.of());
    salesService.synchronizeSalesConfiguration(
        new Envelope<>(
            UUID.randomUUID(),
            "event.sales-configured.v1",
            1,
            eventId,
            "1",
            now,
            UUID.randomUUID(),
            null,
            null,
            config));
    return config;
  }

  private CreateOrderRequest createOrderRequest(SalesConfiguration configuration, int quantity) {
    OrderSelection selection;
    if (configuration.mode() == SalesMode.QUANTITY) {
      selection =
          new OrderSelection.QuantitySelection(
              List.of(
                  new OrderSelection.QuantityItem(
                      configuration.ticketTypes().getFirst().ticketTypeId(), quantity)));
    } else if (configuration.sectors().getFirst().kind() == SectorType.STANDING) {
      selection =
          new OrderSelection.StandingSelection(
              configuration.sectors().getFirst().sectorId(),
              List.of(
                  new OrderSelection.QuantityItem(
                      configuration.ticketTypes().getFirst().ticketTypeId(), quantity)));
    } else {
      selection =
          new OrderSelection.SeatedSelection(
              configuration.sectors().getFirst().sectorId(),
              configuration.seats().stream()
                  .limit(quantity)
                  .map(SalesConfiguration.SeatConfig::seatId)
                  .toList());
    }
    return new CreateOrderRequest(configuration.eventId(), "buyer@example.com", selection);
  }

  private Map<String, Object> captureOrderCreationState(UUID eventId) {
    Map<String, Object> state = new LinkedHashMap<>();
    state.put(
        "orders",
        jdbc.queryForList(
            "select * from booking_schema.tbl_orders where event_id=? order by id", eventId));
    state.put(
        "items",
        jdbc.queryForList(
            "select * from booking_schema.tbl_order_items where event_id=? order by id", eventId));
    state.put(
        "reservations",
        jdbc.queryForList(
            "select * from booking_schema.tbl_reservations where event_id=? order by id", eventId));
    state.put(
        "allocations",
        jdbc.queryForList(
            "select * from booking_schema.tbl_reservation_allocations where event_id=? order by id",
            eventId));
    state.put(
        "seats",
        jdbc.queryForList(
            "select * from booking_schema.tbl_seat_inventory where event_id=? order by seat_id",
            eventId));
    state.put(
        "assignments",
        jdbc.queryForList(
            "select * from booking_schema.tbl_order_item_seats where event_id=? order by order_item_id,seat_id",
            eventId));
    state.put(
        "ticketInventory",
        jdbc.queryForList(
            "select * from booking_schema.tbl_ticket_type_inventory where event_id=? order by ticket_type_id",
            eventId));
    state.put(
        "sectors",
        jdbc.queryForList(
            "select * from booking_schema.tbl_sector_inventory where event_id=? order by sector_id",
            eventId));
    state.put(
        "limits",
        jdbc.queryForList(
            "select * from booking_schema.tbl_purchase_limits where event_id=? order by buyer_subject",
            eventId));
    state.put(
        "receipts",
        jdbc.queryForList(
            "select r.* from booking_schema.tbl_idempotency_requests r join booking_schema.tbl_orders o on o.id=r.resource_id where o.event_id=? order by r.actor_subject,r.operation,r.request_key",
            eventId));
    return state;
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
    var graphConfig = with(sales(), "mode", SalesMode.SEAT_MAP);
    graphConfig =
        with(
            graphConfig,
            "ticketTypes",
            List.of(with(sales().ticketTypes().getFirst(), "sectorId", SECTOR)));
    graphConfig =
        with(
            graphConfig,
            "sectors",
            List.of(new SalesConfiguration.SectorConfig(SECTOR, SectorType.SEATED, "10")));
    graphConfig =
        with(
            graphConfig,
            "seats",
            List.of(
                new SalesConfiguration.SeatConfig(SEAT, SECTOR, TICKET, "A1", "A", "1", false)));
    event.setConfigurationSnapshot(with(graphConfig, "promotions", List.of(promotion())));
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
