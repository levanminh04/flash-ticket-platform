package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.contract.TicketContracts;
import com.flashticket.ticket_service.contract.TicketContracts.*;
import com.flashticket.ticket_service.messaging.TicketBusinessListeners;
import com.flashticket.ticket_service.worker.TicketOutboxPublisher;
import com.rabbitmq.client.Channel;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;

/** Explicit opt-in: no shared AWS DB, broker, buyer email or payment gateway. */
class TicketSagaBrokerIT {
  @TempDir Path temp;
  TicketPostgresIT fixture;
  CachingConnectionFactory connection;
  RabbitTemplate rabbit;
  TicketOutboxPublisher publisher;
  static final String ISSUES = "ticket.issuance.q";
  static final String COMPLETED = "booking.tickets-issued.q";
  static final String FAILED = "payment.ticket-issuance-failed.q";

  @BeforeAll
  static void database() throws Exception {
    String vhost = System.getenv("TICKET_TEST_RABBIT_VHOST");
    if (vhost == null || !vhost.matches("ticket_test_[a-f0-9]+"))
      throw new IllegalStateException("Dedicated test broker vhost required");
    TicketPostgresIT.database();
  }

  @AfterAll
  static void closeDatabase() {
    TicketPostgresIT.closePool();
  }

  @BeforeEach
  void setup() throws Exception {
    fixture = new TicketPostgresIT();
    fixture.temp = temp;
    fixture.setup();
    connection =
        new CachingConnectionFactory(
            "127.0.0.1", Integer.parseInt(System.getenv("TICKET_TEST_RABBIT_PORT")));
    connection.setVirtualHost(System.getenv("TICKET_TEST_RABBIT_VHOST"));
    connection.setUsername(System.getenv("TICKET_TEST_RABBIT_USER"));
    connection.setPassword(System.getenv("TICKET_TEST_RABBIT_PASSWORD"));
    connection.setPublisherConfirmType(ConfirmType.CORRELATED);
    connection.setPublisherReturns(true);
    rabbit = new RabbitTemplate(connection);
    rabbit.setExchange("business.ex");
    rabbit.setMandatory(true);
    var admin = new RabbitAdmin(connection);
    var exchange = new TopicExchange("business.ex", true, false);
    admin.declareExchange(exchange);
    for (String name : new String[] {ISSUES, COMPLETED, FAILED}) {
      var queue = new Queue(name, true);
      admin.declareQueue(queue);
      admin.purgeQueue(name, false);
      String key =
          name.equals(ISSUES)
              ? "ticket.issue.v1"
              : name.equals(COMPLETED) ? "ticket.issued.v1" : "ticket.issuance-failed.v1";
      admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(key));
    }
    publisher = publisher(rabbit);
  }

  @AfterEach
  void closeBroker() {
    if (connection != null) connection.destroy();
  }

  TicketOutboxPublisher publisher(RabbitTemplate transport) {
    return new TicketOutboxPublisher(
        TicketPostgresIT.jdbc,
        TicketPostgresIT.JSON,
        new JdbcTransactionManager(TicketPostgresIT.jdbc.getDataSource()),
        transport,
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        TicketPostgresIT.saga());
  }

  void sendAndConsume(Envelope<IssueTickets> command) throws Exception {
    var props = new MessageProperties();
    props.setContentType("application/json");
    // A producer Java type header must never control Ticket's raw decoder.
    props.setHeader("__TypeId__", "untrusted.producer.Class");
    var confirm = new CorrelationData(UUID.randomUUID().toString());
    rabbit.send(
        "business.ex",
        "ticket.issue.v1",
        new Message(TicketPostgresIT.JSON.writeValueAsBytes(command), props),
        confirm);
    assertThat(confirm.getFuture().get(5, java.util.concurrent.TimeUnit.SECONDS).ack()).isTrue();
    assertThat(confirm.getReturned()).isNull();
    try (var broker = connection.createConnection();
        Channel channel = broker.createChannel(false)) {
      var delivery = channel.basicGet(ISSUES, false);
      assertThat(delivery).isNotNull();
      props.setDeliveryTag(delivery.getEnvelope().getDeliveryTag());
      new TicketBusinessListeners(TicketPostgresIT.JSON, fixture.service)
          .issue(new Message(delivery.getBody(), props), channel);
      assertThat(channel.queueDeclarePassive(ISSUES).getMessageCount()).isZero();
    }
  }

  @Test
  void paymentFixtureIsIssuedAndSuccessIsRoutedOnlyToBooking() throws Exception {
    IssueTickets data;
    try (var input = getClass().getResourceAsStream("/saga/payment-issue-tickets.json")) {
      data = TicketPostgresIT.JSON.readValue(input, IssueTickets.class);
    }
    var p = data.purchase();
    fixture.service.access(
        new Envelope<>(
            UUID.randomUUID(),
            TicketContracts.ACCESS,
            1,
            p.eventId(),
            "1",
            Instant.now(),
            p.eventId(),
            null,
            null,
            new AccessConfiguration(
                p.eventId(),
                "organizer-test",
                "PUBLISHED",
                p.eventStartsAt(),
                p.eventEndsAt(),
                "1")));
    var command =
        new Envelope<>(
            UUID.randomUUID(),
            "ticket.issue.v1",
            1,
            data.orderId(),
            "2",
            Instant.now(),
            data.orderId(),
            UUID.randomUUID(),
            null,
            data);
    sendAndConsume(command);
    sendAndConsume(command); // Commit succeeded, but the producer retried after an ambiguous ACK.
    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    var response = rabbit.receive(COMPLETED, 5000);
    assertThat(response).isNotNull();
    var result = TicketPostgresIT.JSON.readTree(response.getBody());
    assertThat(result.path("messageType").asText()).isEqualTo("ticket.issued.v1");
    assertThat(result.path("aggregateVersion").asText()).isEqualTo("9223372036854775807");
    assertThat(result.path("causationId").asText()).isEqualTo(command.messageId().toString());
    assertThat(result.path("data").path("state").asText()).isEqualTo("COMPLETED");
    assertThat(result.path("data").path("ticketCount").intValue()).isEqualTo(1);
    assertThat(fixture.service.listTickets(data.orderId(), p.buyerSubject()).tickets()).hasSize(1);
    assertThat(rabbit.receive(FAILED)).isNull();
    assertThat(rabbit.receive(COMPLETED)).isNull();
  }

  @Test
  void terminalFailureIsRoutedOnlyToPaymentWithoutCreatingTickets() throws Exception {
    fixture.service.cancel(fixture.cancellation());
    sendAndConsume(fixture.issue());
    assertThat(publisher.publishBatch(10)).isEqualTo(1);
    var response = rabbit.receive(FAILED, 5000);
    assertThat(response).isNotNull();
    var result = TicketPostgresIT.JSON.readTree(response.getBody());
    assertThat(result.path("messageType").asText()).isEqualTo("ticket.issuance-result.v1");
    assertThat(response.getMessageProperties().getReceivedRoutingKey())
        .isEqualTo("ticket.issuance-failed.v1");
    assertThat(result.path("data").path("state").asText()).isEqualTo("FAILED");
    assertThat(result.path("data").path("failureCode").asText()).isEqualTo("EVENT_CANCELLED");
    assertThat(result.path("data").path("ticketCount").intValue()).isZero();
    assertThat(fixture.service.listTickets(fixture.order, fixture.buyer).tickets()).isEmpty();
    assertThat(rabbit.receive(COMPLETED)).isNull();
  }

  @Test
  void mandatoryReturnIsRetriedWithTheOriginalCommittedEnvelope() throws Exception {
    fixture.service.issue(fixture.issue());
    var admin = new RabbitAdmin(connection);
    var binding =
        BindingBuilder.bind(new Queue(COMPLETED))
            .to(new TopicExchange("business.ex"))
            .with("ticket.issued.v1");
    admin.removeBinding(binding);
    assertThat(publisher.publishBatch(1)).isZero();
    assertThat(
            TicketPostgresIT.jdbc.queryForObject(
                "SELECT last_error_code FROM ticket_schema.tbl_outbox_messages", String.class))
        .isEqualTo("UNROUTABLE");
    assertThat(
            TicketPostgresIT.jdbc.queryForObject(
                "SELECT count(*) FROM ticket_schema.tbl_outbox_messages WHERE published_at IS NOT NULL",
                Integer.class))
        .isZero();
    String id =
        TicketPostgresIT.jdbc.queryForObject(
            "SELECT message_id::text FROM ticket_schema.tbl_outbox_messages", String.class);
    admin.declareBinding(binding);
    TicketPostgresIT.jdbc.update(
        "UPDATE ticket_schema.tbl_outbox_messages SET next_attempt_at=clock_timestamp()");
    assertThat(publisher.publishBatch(1)).isEqualTo(1);
    var response = rabbit.receive(COMPLETED, 5000);
    assertThat(response).isNotNull();
    assertThat(response.getMessageProperties().getMessageId()).isEqualTo(id);
    assertThat(TicketPostgresIT.JSON.readTree(response.getBody()).path("messageId").asText())
        .isEqualTo(id);
    assertThat(
            TicketPostgresIT.jdbc.queryForObject(
                "SELECT attempts FROM ticket_schema.tbl_outbox_messages", Integer.class))
        .isEqualTo(2);
  }
}
