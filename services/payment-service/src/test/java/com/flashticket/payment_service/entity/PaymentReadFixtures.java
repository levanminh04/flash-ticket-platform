package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.dto.message.StartPaymentResult;
import com.flashticket.payment_service.dto.message.StartPaymentResultState;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.entity.enums.RefundState;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

public final class PaymentReadFixtures {
  public static final Instant TIME = Instant.parse("2030-01-01T00:00:00Z");

  private PaymentReadFixtures() {}

  public static OrderPaymentSnapshot snapshot(EntityManager em, String owner) {
    var s =
        new OrderPaymentSnapshot(
            UUID.randomUUID(),
            UUID.randomUUID(),
            owner,
            BigDecimal.TEN,
            "VND",
            TIME.plusSeconds(600),
            1L,
            JsonMapper.builder().build().readTree("{\"schemaVersion\":1,\"items\":[]}"),
            "fixture",
            TIME);
    if (em != null) em.persist(s);
    return s;
  }

  public static PaymentAttempt attempt(
      EntityManager em, UUID order, UUID id, PaymentAttemptState state) {
    var a = new PaymentAttempt();
    a.setId(id);
    a.setOrderId(order);
    a.setProvider("VNPAY");
    a.setMerchantAccount("test");
    a.setExpectedAmount(BigDecimal.TEN);
    a.setCurrency("VND");
    a.setStartedAt(TIME);
    a.setState(state);
    a.setRequestKey(UUID.randomUUID());
    a.setRequestHash("fixture");
    a.setPaymentUrl("https://sandbox.example.test/pay");
    if (state == PaymentAttemptState.FAILED || state == PaymentAttemptState.SUCCEEDED)
      a.setFinishedAt(TIME.plusSeconds(1));
    if (em != null) em.persist(a);
    return a;
  }

  public static Charge charge(EntityManager em, PaymentAttempt a) {
    var c = new Charge();
    c.setId(UUID.randomUUID());
    c.setOrderId(a.getOrderId());
    c.setAttemptId(a.getId());
    c.setProvider(a.getProvider());
    c.setMerchantAccount(a.getMerchantAccount());
    c.setProviderChargeId(UUID.randomUUID().toString());
    c.setAmount(BigDecimal.TEN);
    c.setCurrency("VND");
    c.setChargedAt(TIME);
    c.setRecordedAt(TIME);
    if (em != null) em.persist(c);
    return c;
  }

  public static PaymentConfirmation confirm(EntityManager em, Charge c) {
    var p = new PaymentConfirmation();
    p.setOrderId(c.getOrderId());
    p.setChargeId(c.getId());
    p.setAmount(BigDecimal.TEN);
    p.setCurrency("VND");
    p.setConfirmedAt(TIME);
    if (em != null) em.persist(p);
    return p;
  }

  public static Refund refund(EntityManager em, Charge c, UUID id) {
    var r = new Refund();
    r.setId(id);
    r.setOrderId(c.getOrderId());
    r.setChargeId(c.getId());
    r.setAmount(BigDecimal.TEN);
    r.setCurrency("VND");
    r.setState(RefundState.PENDING);
    r.setReason(RefundReason.DUPLICATE_PAYMENT);
    r.setCreatedAt(TIME);
    if (em != null) em.persist(r);
    return r;
  }

  public static InboxMessage inbox(
      UUID request, UUID order, StartPaymentResultState state, UUID attempt) {
    var i = new InboxMessage();
    i.setId(new InboxMessageId("payment.start-requested.v1", request));
    i.setPayloadHash("fixture");
    i.setProcessedAt(TIME);
    i.setResponsePayload(
        JsonMapper.builder()
            .build()
            .valueToTree(
                new StartPaymentResult(
                    request,
                    order,
                    state,
                    attempt,
                    state == StartPaymentResultState.REJECTED ? "ORDER_EXPIRED" : null)));
    return i;
  }
}
