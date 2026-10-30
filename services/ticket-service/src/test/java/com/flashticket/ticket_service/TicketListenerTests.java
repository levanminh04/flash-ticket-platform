package com.flashticket.ticket_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.flashticket.ticket_service.application.TicketService;
import com.flashticket.ticket_service.contract.TicketContracts;
import com.flashticket.ticket_service.contract.TicketContracts.*;
import com.flashticket.ticket_service.messaging.TicketBusinessListeners;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class TicketListenerTests {
  private final JsonMapper json = JsonMapper.builder().build();
  private final TicketService tickets = mock(TicketService.class);
  private final Channel channel = mock(Channel.class);
  private final TicketBusinessListeners listener = new TicketBusinessListeners(json, tickets);
  private static final UUID EVENT = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID ORDER = UUID.fromString("10000000-0000-0000-0000-000000000001");

  @Test
  void acknowledgesAccessOnlyAfterTransactionalServiceReturns() throws IOException {
    var envelope = envelope(TicketContracts.ACCESS, EVENT, access("PUBLISHED"));
    listener.access(message(envelope), channel);
    var order = inOrder(tickets, channel);
    order.verify(tickets).access(envelope);
    order.verify(channel).basicAck(17L, false);
    verifyNoMoreInteractions(tickets, channel);
  }

  @Test
  void acknowledgesCancellationOnlyAfterTransactionalServiceReturns() throws IOException {
    var sales =
        json.createObjectNode()
            .put("schemaVersion", 1)
            .put("eventId", EVENT.toString())
            .put("sourceConfigVersion", "2")
            .put("eventStatus", "CANCELLED")
            .put("organizerSubject", "organizer")
            .put("eventTitle", "Concert")
            .put("eventStartsAt", "2026-11-01T12:00:00Z")
            .put("eventEndsAt", "2026-11-01T14:00:00Z")
            .put("mode", "QUANTITY")
            .put("saleStartsAt", "2026-10-01T12:00:00Z")
            .put("saleEndsAt", "2026-11-01T11:00:00Z")
            .put("purchaseLimit", 5);
    sales.putNull("venueName");
    for (String name : List.of("ticketTypes", "sectors", "seats", "promotions"))
      sales.set(name, json.createArrayNode());
    var payload =
        new EventCancellation(
            EVENT,
            UUID.randomUUID(),
            "2",
            Instant.parse("2026-10-30T12:00:00Z"),
            "organizer",
            sales,
            access("CANCELLED"));
    var envelope = envelope(TicketContracts.CANCELLED, EVENT, payload);
    listener.cancellation(message(envelope), channel);
    var order = inOrder(tickets, channel);
    order.verify(tickets).cancel(envelope);
    order.verify(channel).basicAck(17L, false);
    verifyNoMoreInteractions(tickets, channel);
  }

  @Test
  void acknowledgesIssueOnlyAfterTransactionalServiceReturns() throws IOException {
    var envelope = envelope(TicketContracts.ISSUE, ORDER, issue());
    listener.issue(message(envelope), channel);
    var order = inOrder(tickets, channel);
    order.verify(tickets).issue(envelope);
    order.verify(channel).basicAck(17L, false);
    verifyNoMoreInteractions(tickets, channel);
  }

  @Test
  void malformedPayloadIsRejectedWithoutRequeueOrServiceCall() throws IOException {
    listener.access(
        message("{\"data\":\"payload secret\"}".getBytes(StandardCharsets.UTF_8)), channel);
    verify(channel).basicReject(17L, false);
    verifyNoInteractions(tickets);
    verifyNoMoreInteractions(channel);
  }

  @Test
  void permanentServiceContractConflictIsRejectedWithoutRequeue() throws IOException {
    doThrow(new InvalidContractException("INBOX_HASH_CONFLICT")).when(tickets).issue(any());
    listener.issue(message(envelope(TicketContracts.ISSUE, ORDER, issue())), channel);
    verify(tickets).issue(any());
    verify(channel).basicReject(17L, false);
    verifyNoMoreInteractions(tickets, channel);
  }

  @Test
  void transientServiceFailureIsRequeuedWithoutAcknowledgement() throws IOException {
    doThrow(new IllegalStateException("private database details")).when(tickets).access(any());
    listener.access(message(envelope(TicketContracts.ACCESS, EVENT, access("PUBLISHED"))), channel);
    verify(tickets).access(any());
    verify(channel).basicNack(17L, false, true);
    verifyNoMoreInteractions(tickets, channel);
  }

  @Test
  void acknowledgementFailurePropagatesWithoutSecondNackAfterCommit() throws IOException {
    doThrow(new IOException("connection lost")).when(channel).basicAck(17L, false);
    var envelope = envelope(TicketContracts.ACCESS, EVENT, access("PUBLISHED"));
    assertThatThrownBy(() -> listener.access(message(envelope), channel))
        .isInstanceOf(IOException.class);
    var order = inOrder(tickets, channel);
    order.verify(tickets).access(envelope);
    order.verify(channel).basicAck(17L, false);
    verifyNoMoreInteractions(tickets, channel);
  }

  @Test
  void listenersRequireBothExplicitFeatureSwitches() {
    var runner =
        new ApplicationContextRunner()
            .withBean(JsonMapper.class, () -> json)
            .withBean(TicketService.class, () -> tickets)
            .withUserConfiguration(TicketBusinessListeners.class);
    runner.run(context -> assertThat(context).doesNotHaveBean(TicketBusinessListeners.class));
    runner
        .withPropertyValues("ticket.features.business-enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(TicketBusinessListeners.class));
    runner
        .withPropertyValues("ticket.features.listeners-enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(TicketBusinessListeners.class));
    runner
        .withPropertyValues(
            "ticket.features.business-enabled=true", "ticket.features.listeners-enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TicketBusinessListeners.class));
  }

  private Message message(Object envelope) {
    return message(json.writeValueAsBytes(envelope));
  }

  private Message message(byte[] bytes) {
    var properties = new MessageProperties();
    properties.setDeliveryTag(17L);
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    return new Message(bytes, properties);
  }

  private static AccessConfiguration access(String status) {
    return new AccessConfiguration(
        EVENT,
        "organizer",
        status,
        Instant.parse("2026-11-01T12:00:00Z"),
        Instant.parse("2026-11-01T14:00:00Z"),
        "2");
  }

  private static IssueTickets issue() {
    var snapshot =
        new PurchaseSnapshot(
            1,
            ORDER,
            EVENT,
            "buyer",
            "buyer@example.test",
            "2",
            "4",
            Instant.parse("2026-10-30T12:00:00Z"),
            Instant.parse("2026-10-30T12:01:00Z"),
            "VND",
            "Concert",
            Instant.parse("2026-11-01T12:00:00Z"),
            Instant.parse("2026-11-01T14:00:00Z"),
            null,
            "QUANTITY",
            null,
            "200",
            "0",
            "200",
            null,
            List.of(
                new OrderItem(
                    UUID.fromString("40000000-0000-0000-0000-000000000001"),
                    UUID.fromString("50000000-0000-0000-0000-000000000001"),
                    "General",
                    2,
                    "100",
                    "200",
                    null,
                    List.of())));
    return new IssueTickets(
        ORDER, UUID.fromString("30000000-0000-0000-0000-000000000001"), snapshot);
  }

  private static <T> Envelope<T> envelope(String type, UUID aggregate, T payload) {
    return new Envelope<>(
        UUID.randomUUID(),
        type,
        1,
        aggregate,
        "2",
        Instant.parse("2026-10-30T12:00:10Z"),
        aggregate,
        null,
        null,
        payload);
  }
}
