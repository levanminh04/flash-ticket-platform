package com.flashticket.ticket_service.worker;

import com.flashticket.ticket_service.application.TicketService;
import com.flashticket.ticket_service.contract.TicketContracts.Envelope;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** A leased claim does not mean published; broker confirmation and a non-return are required. */
@Component
@ConditionalOnExpression(
    "${ticket.features.business-enabled:false} and ${ticket.features.workers-enabled:false}")
public final class TicketOutboxPublisher {
  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final TransactionTemplate transactions;
  private final RabbitTemplate rabbit;
  private final Duration confirmTimeout, lease;
  private final String routingKey;

  private record Pending(
      UUID id,
      UUID aggregate,
      long version,
      String type,
      int schema,
      String data,
      Instant at,
      UUID correlation,
      UUID causation,
      String trace,
      int attempt) {}

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Shares Spring-managed infrastructure; no dependency accessor is exposed.")
  public TicketOutboxPublisher(
      JdbcTemplate jdbc,
      JsonMapper json,
      PlatformTransactionManager manager,
      RabbitTemplate rabbit,
      @Value("${ticket.outbox.confirm-timeout:5s}") Duration confirmTimeout,
      @Value("${ticket.outbox.claim-lease:30s}") Duration lease,
      @Value("${ticket.messaging.result-routing-key:ticket.issuance-result.v1}")
          String routingKey) {
    if (confirmTimeout.isNegative()
        || confirmTimeout.isZero()
        || lease.compareTo(confirmTimeout) <= 0
        || routingKey.isBlank()) throw new IllegalArgumentException("Invalid outbox configuration");
    this.jdbc = jdbc;
    this.json = json;
    this.transactions = new TransactionTemplate(manager);
    this.rabbit = rabbit;
    this.confirmTimeout = confirmTimeout;
    this.lease = lease;
    this.routingKey = routingKey;
  }

  public int publishBatch(int batch) {
    int published = 0;
    for (int i = 0; i < batch; i++) {
      Pending message = claim();
      if (message == null) break;
      String failure = null;
      try {
        if (!TicketService.RESULT.equals(message.type()) || message.schema() != 1)
          throw new IllegalArgumentException("Invalid stored message");
        var data = json.readTree(message.data());
        if (!data.isObject()) throw new IllegalArgumentException("Invalid stored message");
        var envelope =
            new Envelope<>(
                message.id(),
                message.type(),
                message.schema(),
                message.aggregate(),
                Long.toString(message.version()),
                message.at(),
                message.correlation(),
                message.causation(),
                message.trace(),
                data);
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(message.id().toString());
        properties.setType(message.type());
        properties.setCorrelationId(message.correlation().toString());
        if (message.trace() != null) properties.setHeader("traceparent", message.trace());
        var correlation = new CorrelationData(message.id().toString());
        rabbit.send(
            rabbit.getExchange(),
            routingKey,
            new Message(json.writeValueAsBytes(envelope), properties),
            correlation);
        var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!confirm.ack()) failure = "BROKER_NACK";
        else if (correlation.getReturned() != null) failure = "UNROUTABLE";
        else
          published +=
              jdbc.update(
                  "UPDATE ticket_schema.tbl_outbox_messages SET published_at=clock_timestamp(),last_error_code=NULL WHERE message_id=? AND attempts=? AND published_at IS NULL",
                  message.id(),
                  message.attempt());
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        failure = "INTERRUPTED";
      } catch (java.util.concurrent.ExecutionException
          | java.util.concurrent.TimeoutException
          | RuntimeException unavailable) {
        failure = "BROKER_UNAVAILABLE";
      }
      if (failure != null)
        jdbc.update(
            "UPDATE ticket_schema.tbl_outbox_messages SET last_error_code=?,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond') WHERE message_id=? AND attempts=? AND published_at IS NULL",
            failure,
            retryMillis(message.attempt()),
            message.id(),
            message.attempt());
      if (Thread.currentThread().isInterrupted()) break;
    }
    return published;
  }

  private Pending claim() {
    return transactions.execute(
        status -> {
          var rows =
              jdbc.query(
                  "SELECT * FROM ticket_schema.tbl_outbox_messages WHERE published_at IS NULL AND next_attempt_at<=clock_timestamp() ORDER BY occurred_at,message_id FOR UPDATE SKIP LOCKED LIMIT 1",
                  (r, n) ->
                      new Pending(
                          r.getObject("message_id", UUID.class),
                          r.getObject("aggregate_id", UUID.class),
                          r.getLong("aggregate_version"),
                          r.getString("message_type"),
                          r.getInt("schema_version"),
                          r.getString("payload"),
                          r.getTimestamp("occurred_at").toInstant(),
                          r.getObject("correlation_id", UUID.class),
                          r.getObject("causation_id", UUID.class),
                          r.getString("traceparent"),
                          Math.addExact(r.getInt("attempts"), 1)));
          if (rows.isEmpty()) return null;
          var message = rows.getFirst();
          jdbc.update(
              "UPDATE ticket_schema.tbl_outbox_messages SET attempts=?,next_attempt_at=clock_timestamp()+(? * interval '1 millisecond') WHERE message_id=?",
              message.attempt(),
              lease.toMillis(),
              message.id());
          return message;
        });
  }

  static long retryMillis(int attempt) {
    return Math.min(300_000L, 1000L << Math.min(Math.max(0, attempt - 1), 9));
  }
}
