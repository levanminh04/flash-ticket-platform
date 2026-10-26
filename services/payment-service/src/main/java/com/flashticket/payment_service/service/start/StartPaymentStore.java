package com.flashticket.payment_service.service.start;

import com.flashticket.payment_service.config.VnpayProperties;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.*;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.entity.id.InboxMessageId;
import com.flashticket.payment_service.gateway.*;
import com.flashticket.payment_service.mapper.*;
import com.flashticket.payment_service.repository.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("payment-simulator & !prod & !production")
@RequiredArgsConstructor
public class StartPaymentStore {
  public static final String CONSUMER = "payment.start-requested.v1";
  private final InboxMessageRepository inbox;
  private final OrderPaymentSnapshotRepository snapshots;
  private final PaymentAttemptRepository attempts;
  private final PaymentConfirmationRepository confirmations;
  private final OrderPaymentSnapshotMapper snapshotMapper;
  private final MessageJsonMapper messages;
  private final PaymentGateway gateway;
  private final VnpayProperties vnpay;
  private final Clock clock;
  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public StartPaymentResult replay(UUID messageId, String hash) {
    return inbox
        .findById(new InboxMessageId(CONSUMER, messageId))
        .map(
            existing -> {
              if (!existing.getPayloadHash().equals(hash))
                throw new ContractValidationException("messageId", "MESSAGE_CONTENT_CONFLICT");
              return messages.fromResultJson(existing.getResponsePayload());
            })
        .orElse(null);
  }

  @Transactional(timeout = 15)
  public StartPaymentResult apply(
      Envelope<StartPayment> command, String hash, String purchaseHash, String rejection) {
    // PostgreSQL transaction locks also serialize first-ever snapshot/inbox inserts.
    // Namespace each lock; always acquire message then order to avoid lock cycles.
    lock("start-message:" + command.messageId());
    var old = replay(command.messageId(), hash);
    if (old != null) return old;
    lock("start-order:" + command.aggregateId());
    var data = command.data();
    var purchase = data.purchase();
    var snapshot = snapshots.findById(purchase.orderId());
    if (snapshot.isPresent() && !snapshot.get().getSnapshotHash().equals(purchaseHash))
      throw new ContractValidationException("purchase", "SNAPSHOT_CONFLICT");
    if (snapshot.isEmpty()) {
      entityManager.persist(snapshotMapper.toEntity(purchase, purchaseHash, clock.instant()));
      entityManager.flush();
    }
    var prior = attempts.findByOrderIdAndRequestKey(purchase.orderId(), data.requestKey());
    if (prior.isPresent()) {
      if (!prior.get().getRequestHash().equals(data.requestHash()))
        throw new ContractValidationException("requestKey", "IDEMPOTENCY_CONFLICT");
      return record(
          command,
          hash,
          new StartPaymentResult(
              data.requestId(),
              purchase.orderId(),
              StartPaymentResultState.CREATED,
              prior.get().getId(),
              null));
    }
    var now = clock.instant();
    if (rejection == null && !purchase.expiresAt().isAfter(now.plusSeconds(1)))
      rejection = "ORDER_EXPIRED";
    if (rejection == null && purchase.frozenAt().isAfter(now)) rejection = "INVALID_ORDER_TIME";
    if (rejection == null && confirmations.findChargeIdByOrderId(purchase.orderId()).isPresent())
      rejection = "ORDER_NOT_PAYABLE";
    if (rejection == null && attempts.existsByOrderIdAndFinishedAtIsNull(purchase.orderId()))
      rejection = "PAYMENT_IN_PROGRESS";
    if (rejection == null
        && new BigDecimal(purchase.total()).compareTo(new BigDecimal("9999999999")) > 0)
      rejection = "AMOUNT_OUT_OF_RANGE";
    if (rejection != null)
      return record(
          command,
          hash,
          new StartPaymentResult(
              data.requestId(),
              purchase.orderId(),
              StartPaymentResultState.REJECTED,
              null,
              rejection));
    var attempt =
        PaymentAttempt.pending(
            UUID.randomUUID(),
            purchase.orderId(),
            data.provider().name(),
            vnpay.getTmnCode(),
            new BigDecimal(purchase.total()),
            purchase.currency(),
            now,
            data.requestKey(),
            data.requestHash());
    attempts.saveAndFlush(attempt);
    // VNPay PAY URL creation is local computation: no network wait inside this transaction.
    var context =
        new PaymentContext(
            attempt.getId(),
            attempt.getOrderId(),
            attempt.getMerchantAccount(),
            attempt.getExpectedAmount(),
            attempt.getCurrency(),
            attempt.getStartedAt(),
            purchase.expiresAt());
    attempt.setPaymentUrl(gateway.createPayment(context).toASCIIString());
    return record(
        command,
        hash,
        new StartPaymentResult(
            data.requestId(),
            purchase.orderId(),
            StartPaymentResultState.CREATED,
            attempt.getId(),
            null));
  }

  private void lock(String key) {
    jdbc.queryForObject(
        "select 1 from pg_advisory_xact_lock(hashtextextended(?, 0))", Integer.class, key);
  }

  private StartPaymentResult record(
      Envelope<StartPayment> command, String hash, StartPaymentResult result) {
    entityManager.persist(
        InboxMessage.processed(
            new InboxMessageId(CONSUMER, command.messageId()),
            hash,
            clock.instant(),
            messages.toResultJson(result)));
    entityManager.flush();
    return result;
  }
}
