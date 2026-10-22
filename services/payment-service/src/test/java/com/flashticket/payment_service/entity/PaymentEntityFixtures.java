package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.id.*;
import jakarta.persistence.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

final class PaymentEntityFixtures {
  static final Instant NOW = Instant.parse("2030-01-01T01:00:00Z");
  final OrderPaymentSnapshot snapshot;
  final PaymentAttempt attempt;
  final Charge charge;
  final Refund refund;
  final EventRefundRun run;
  final OutboxMessage outbox;
  final InboxMessage inbox;

  PaymentEntityFixtures(EntityManager em) {
    var eventId = UUID.randomUUID();
    em.persist(
        make(
            EventFinanceSnapshot.class,
            "eventId",
            eventId,
            "cancellationId",
            UUID.randomUUID(),
            "cancelledAt",
            NOW));
    snapshot =
        make(OrderPaymentSnapshot.class, "eventId", eventId, "expiresAt", NOW.plusSeconds(600));
    em.persist(snapshot);
    attempt = make(PaymentAttempt.class, "orderId", snapshot.getOrderId());
    em.persist(attempt);
    em.flush();
    charge = make(Charge.class, "attemptId", attempt.getId(), "orderId", snapshot.getOrderId());
    em.persist(charge);
    em.flush();
    em.persist(make(Charge.class, "attemptId", attempt.getId(), "orderId", snapshot.getOrderId()));
    em.persist(
        make(
            PaymentConfirmation.class,
            "orderId",
            snapshot.getOrderId(),
            "chargeId",
            charge.getId()));
    em.flush();
    refund = make(Refund.class, "chargeId", charge.getId(), "orderId", snapshot.getOrderId());
    em.persist(refund);
    em.flush();
    em.persist(make(RefundAttempt.class, "refundId", refund.getId()));
    em.persist(
        make(
            RefundAttempt.class,
            "refundId",
            refund.getId(),
            "finishedAt",
            NOW.plusSeconds(1),
            "outcome",
            "RETRY"));
    run = make(EventRefundRun.class, "eventId", eventId);
    em.persist(run);
    em.flush();
    em.persist(
        make(
            EventRefundItem.class,
            "id",
            new EventRefundItemId(run.getId(), snapshot.getOrderId()),
            "eventId",
            eventId,
            "confirmedChargeId",
            charge.getId(),
            "refundId",
            refund.getId()));
    var other =
        make(OrderPaymentSnapshot.class, "eventId", eventId, "expiresAt", NOW.plusSeconds(600));
    em.persist(other);
    em.flush();
    em.persist(
        make(
            EventRefundItem.class,
            "id",
            new EventRefundItemId(run.getId(), other.getOrderId()),
            "eventId",
            eventId));
    em.persist(
        make(
            EventPayout.class,
            "eventId",
            eventId,
            "validGross",
            BigDecimal.TEN,
            "validRefunds",
            BigDecimal.ZERO,
            "feeAmount",
            BigDecimal.ONE,
            "netAmount",
            BigDecimal.valueOf(9)));
    em.persist(
        make(PaymentSaga.class, "orderId", snapshot.getOrderId(), "chargeId", charge.getId()));
    outbox = make(OutboxMessage.class);
    em.persist(outbox);
    inbox =
        make(
            InboxMessage.class,
            "id",
            new InboxMessageId("fixture", UUID.randomUUID()),
            "responsePayload",
            JsonMapper.builder().build().readTree("{\"result\":\"fixture\"}"));
    em.persist(inbox);
    em.flush();
  }

  static <T> T make(Class<T> type, Object... overrides) {
    try {
      T value = type.getDeclaredConstructor().newInstance();
      for (Field f : type.getDeclaredFields()) {
        Column col = f.getAnnotation(Column.class);
        if (col == null || col.nullable() || f.isAnnotationPresent(Version.class)) continue;
        f.setAccessible(true);
        Object initial;
        if (f.getType() == UUID.class) initial = UUID.randomUUID();
        else if (f.getType() == String.class)
          initial =
              f.getName().equals("currency")
                  ? "VND"
                  : f.getName().equals("provider")
                      ? "fixture-provider"
                      : f.getName().equals("merchantAccount")
                          ? "fixture-merchant"
                          : "fixture-" + UUID.randomUUID();
        else if (f.getType() == Instant.class) initial = NOW;
        else if (f.getType() == BigDecimal.class)
          initial = f.getName().equals("feeRate") ? new BigDecimal("0.1") : BigDecimal.TEN;
        else if (f.getType() == Long.class)
          initial = f.getName().equals("rowVersion") || f.getName().endsWith("Count") ? 0L : 1L;
        else if (f.getType() == Integer.class)
          initial = f.getName().equals("schemaVersion") ? 1 : 0;
        else if (f.getType() == Boolean.class) initial = false;
        else if (f.getType().isEnum()) initial = f.getType().getEnumConstants()[0];
        else initial = JsonMapper.builder().build().readTree("{\"schemaVersion\":1,\"items\":[]}");
        f.set(value, initial);
      }
      for (int i = 0; i < overrides.length; i += 2) {
        Field f = type.getDeclaredField((String) overrides[i]);
        f.setAccessible(true);
        f.set(value, overrides[i + 1]);
      }
      return value;
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
