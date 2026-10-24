package com.flashticket.booking_service.support;

import com.flashticket.booking_service.config.BookingJsonConfig;
import com.flashticket.booking_service.dto.message.*;
import com.flashticket.booking_service.dto.snapshot.*;
import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.SalesMode;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

public final class BookingFixtures {
  public static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000001");
  public static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000002");
  public static final UUID TICKET = UUID.fromString("00000000-0000-0000-0000-000000000003");
  public static final UUID SECTOR = UUID.fromString("00000000-0000-0000-0000-000000000004");
  public static final UUID SEAT = UUID.fromString("00000000-0000-0000-0000-000000000005");
  public static final UUID MESSAGE = UUID.fromString("00000000-0000-0000-0000-000000000006");
  public static final Instant NOW = Instant.parse("2026-10-24T00:00:00Z");

  private BookingFixtures() {}

  public static JsonMapper jsonMapper() {
    try (var context =
        new AnnotationConfigApplicationContext(
            JacksonAutoConfiguration.class, BookingJsonConfig.class)) {
      return context.getBean(JsonMapper.class);
    }
  }

  public static SalesConfiguration sales() {
    return new SalesConfiguration(
        1,
        EVENT,
        "organizer",
        "1",
        SalesConfiguration.EventStatus.PUBLISHED,
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
                TICKET, "Standard", "100", Currency.VND, null, "20", null)),
        List.of(),
        List.of(),
        List.of());
  }

  public static PurchaseSnapshot purchase() {
    return new PurchaseSnapshot(
        1,
        ORDER,
        EVENT,
        "buyer",
        "buyer@example.com",
        "1",
        "2",
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
            new PurchaseSnapshot.Item(
                MESSAGE, TICKET, "Standard", 2, "100", "200", null, List.of())));
  }

  public static PromotionConfig promotion() {
    return new PromotionConfig(
        MESSAGE,
        "SAVE",
        NOW,
        NOW.plusSeconds(3600),
        "10",
        1,
        new PromotionConfig.PercentDiscount("PERCENT", 10));
  }

  public static SeatSelectionSnapshot seat() {
    return new SeatSelectionSnapshot(SEAT, "A1", "A", "1");
  }

  public static Map<String, Object> payloads() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("event.sales-configured.v1", sales());
    result.put(
        "event.cancelled.v1",
        new EventCancellation(
            EVENT,
            SEAT,
            "1",
            NOW,
            "admin",
            sales(),
            new EventCancellation.AccessConfiguration(
                EVENT,
                "organizer",
                SalesConfiguration.EventStatus.CANCELLED,
                NOW.plusSeconds(7200),
                NOW.plusSeconds(10800),
                "1")));
    result.put(
        "booking.accept-payment.v1",
        new AcceptPayment(ORDER, SEAT, TICKET, "200", Currency.VND, "2"));
    result.put(
        "booking.payment-decision.v1",
        new PaymentDecision(ORDER, SEAT, NOW, PaymentDecision.Outcome.ACCEPTED, SEAT));
    result.put(
        "payment.start-requested.v1",
        new StartPayment(MESSAGE, SEAT, "hash", StartPayment.Provider.VNPAY, purchase()));
    result.put(
        "ticket.issuance-result.v1",
        new IssuanceResult(ORDER, SEAT, TICKET, IssuanceResult.State.COMPLETED, 2, null));
    result.put("booking.order-completed.v1", new OrderCompleted(ORDER, SEAT, TICKET, 2));
    result.put(
        "booking.issuance-result.v1",
        new BookingIssuanceResult(
            ORDER, SEAT, TICKET, BookingIssuanceResult.State.FAILED, "ERROR"));
    return result;
  }

  public static Envelope<?> envelope(String type, Object payload) {
    return new Envelope<>(MESSAGE, type, 1, ORDER, "2", NOW, EVENT, null, null, payload);
  }

  // Copies records only to make one invalid input at a time; never derives expected outcomes.
  @SuppressWarnings("unchecked")
  public static <T extends Record> T with(T record, String name, Object value) {
    try {
      RecordComponent[] components = record.getClass().getRecordComponents();
      Class<?>[] types = new Class<?>[components.length];
      Object[] values = new Object[components.length];
      boolean found = false;
      for (int i = 0; i < components.length; i++) {
        types[i] = components[i].getType();
        boolean matches = components[i].getName().equals(name);
        found |= matches;
        values[i] = matches ? value : components[i].getAccessor().invoke(record);
      }
      if (!found) throw new IllegalArgumentException("Unknown record component: " + name);
      return (T) record.getClass().getDeclaredConstructor(types).newInstance(values);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalArgumentException(exception);
    }
  }
}
