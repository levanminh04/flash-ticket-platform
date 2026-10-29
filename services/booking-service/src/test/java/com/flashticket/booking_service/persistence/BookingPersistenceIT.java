package com.flashticket.booking_service.persistence;

import static com.flashticket.booking_service.support.BookingFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.booking_service.availability.repository.EventSalesSnapshotRepository;
import com.flashticket.booking_service.availability.service.AvailabilityService;
import com.flashticket.booking_service.availability.service.impl.AvailabilityServiceImpl;
import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.dto.message.Envelope;
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
  OrderServiceImpl.class,
  OrderMapperImpl.class,
  MappingValueConverter.class,
  JacksonAutoConfiguration.class,
  BookingJsonConfig.class,
  ValidationAutoConfiguration.class
})
@Testcontainers
@EnableResilientMethods
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
  @Autowired OrderService orderService;
  @Autowired AvailabilityService salesService;
  @Autowired EventSalesSnapshotRepository snapshots;
  @Autowired PlatformTransactionManager transactionManager;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void salesConfigurationAndInventoriesCommitTogether() {
    try {
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
          Awaitility.await()
              .atMost(Duration.ofSeconds(4))
              .pollInterval(Duration.ofMillis(100))
              .until(
                  () -> {
                    try (var statement =
                        connection.prepareStatement(
                            "select clock_timestamp() >= sale_ends_at from booking_schema.tbl_event_sales_snapshots where event_id=?")) {
                      statement.setObject(1, config.eventId());
                      try (var result = statement.executeQuery()) {
                        result.next();
                        return result.getBoolean(1);
                      }
                    }
                  });
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
