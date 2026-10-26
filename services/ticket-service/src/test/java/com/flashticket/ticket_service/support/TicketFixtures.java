package com.flashticket.ticket_service.support;

import com.flashticket.ticket_service.config.TicketJsonConfig;
import com.flashticket.ticket_service.dto.message.*;
import com.flashticket.ticket_service.dto.snapshot.*;
import com.flashticket.ticket_service.entity.*;
import com.flashticket.ticket_service.enums.Currency;
import com.flashticket.ticket_service.enums.EventState;
import com.flashticket.ticket_service.enums.IssuanceState;
import com.flashticket.ticket_service.enums.SalesMode;
import com.flashticket.ticket_service.enums.TicketState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

public final class TicketFixtures {
  public static final UUID EVENT = id(1),
      ORDER = id(2),
      ITEM = id(3),
      TYPE = id(4),
      SEAT = id(5),
      SECTOR = id(6),
      CHARGE = id(7),
      ISSUANCE = id(8),
      MESSAGE = id(9),
      CAUSE = id(10),
      CORRELATION = id(11);
  public static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

  private TicketFixtures() {}

  private static UUID id(int value) {
    return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
  }

  public static JsonMapper jsonMapper() {
    try (var context =
        new AnnotationConfigApplicationContext(
            JacksonAutoConfiguration.class, TicketJsonConfig.class)) {
      return context.getBean(JsonMapper.class);
    }
  }

  public static PurchaseSnapshot purchase() {
    return new PurchaseSnapshot(
        1,
        ORDER,
        EVENT,
        "buyer-subject",
        "buyer@example.test",
        "1",
        "1",
        NOW,
        NOW.plusSeconds(600),
        Currency.VND,
        "Concert",
        NOW.plusSeconds(7200),
        NOW.plusSeconds(10800),
        null,
        SalesMode.QUANTITY,
        null,
        "200",
        "0",
        "200",
        null,
        List.of(
            new PurchaseSnapshot.Item(ITEM, TYPE, "Standard", 2, "100", "200", null, List.of())));
  }

  public static SalesConfiguration sales() {
    return new SalesConfiguration(
        1,
        EVENT,
        "organizer-subject",
        "1",
        EventState.PUBLISHED,
        "Concert",
        NOW.plusSeconds(7200),
        NOW.plusSeconds(10800),
        null,
        SalesMode.QUANTITY,
        NOW,
        NOW.plusSeconds(3600),
        5,
        List.of(
            new SalesConfiguration.TicketTypeConfig(
                TYPE, "Standard", "100", Currency.VND, null, "20", null)),
        List.of(),
        List.of(),
        List.of());
  }

  public static AccessConfiguration access() {
    return new AccessConfiguration(
        EVENT,
        "organizer-subject",
        EventState.PUBLISHED,
        NOW.plusSeconds(7200),
        NOW.plusSeconds(10800),
        "1");
  }

  public static EventCancellation cancellation() {
    var sales =
        new SalesConfiguration(
            1,
            EVENT,
            "organizer-subject",
            "2",
            EventState.CANCELLED,
            "Concert",
            NOW.plusSeconds(7200),
            NOW.plusSeconds(10800),
            null,
            SalesMode.QUANTITY,
            NOW,
            NOW.plusSeconds(3600),
            5,
            List.of(),
            List.of(),
            List.of(),
            List.of());
    var access =
        new AccessConfiguration(
            EVENT,
            "organizer-subject",
            EventState.CANCELLED,
            NOW.plusSeconds(7200),
            NOW.plusSeconds(10800),
            "2");
    return new EventCancellation(EVENT, MESSAGE, "2", NOW, "admin-subject", sales, access);
  }

  public static IssueTickets issue() {
    return new IssueTickets(ORDER, CHARGE, purchase());
  }

  public static IssuanceResult completed() {
    return new IssuanceResult(ORDER, CHARGE, ISSUANCE, IssuanceResult.State.COMPLETED, 2, null);
  }

  public static IssuanceResult failed() {
    return new IssuanceResult(
        ORDER, CHARGE, ISSUANCE, IssuanceResult.State.FAILED, 0, "ISSUANCE_REJECTED");
  }

  public static Envelope<IssuanceResult> resultEnvelope() {
    return new Envelope<>(
        MESSAGE,
        "ticket.issuance-result.v1",
        1,
        ORDER,
        "1",
        NOW,
        CORRELATION,
        CAUSE,
        "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
        completed());
  }

  public static Ticket ticket() {
    var t = new Ticket();
    t.setId(SEAT);
    t.setEventId(EVENT);
    t.setOrderItemId(ITEM);
    t.setOrdinal(1);
    t.setTicketTypeId(TYPE);
    t.setHolderSubject("buyer-subject");
    t.setState(TicketState.VALID);
    t.setDisplaySnapshot(new TicketDisplaySnapshot(1, "Standard", null));
    t.setQrTokenCiphertext(new byte[] {1, 2, 3});
    t.setQrKeyVersion("TEST_ONLY");
    t.setQrTokenHash("TEST_HASH");
    t.setRowVersion(0L);
    return t;
  }

  public static Issuance completedIssuance() {
    var v = new Issuance();
    v.setId(ISSUANCE);
    v.setOrderId(ORDER);
    v.setEventId(EVENT);
    v.setBuyerSubject("buyer-subject");
    v.setCustomerEmail("buyer@example.test");
    v.setPaymentConfirmationId("TEST_CONFIRMATION");
    v.setInputHash("TEST_INPUT");
    v.setPurchaseSnapshot(purchase());
    v.setExpectedTicketCount(2);
    v.setState(IssuanceState.COMPLETED);
    v.setCreatedAt(NOW);
    v.setCompletedAt(NOW);
    v.setRowVersion(0L);
    v.addTicket(ticket());
    var second = ticket();
    second.setId(SECTOR);
    second.setOrdinal(2);
    second.setQrTokenHash("TEST_HASH_2");
    v.addTicket(second);
    return v;
  }
}
