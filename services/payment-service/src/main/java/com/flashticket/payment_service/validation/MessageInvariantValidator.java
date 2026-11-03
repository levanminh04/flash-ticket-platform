package com.flashticket.payment_service.validation;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.EventCancellation;
import com.flashticket.payment_service.dto.message.EventState;
import com.flashticket.payment_service.dto.message.FinanceConfiguration;
import com.flashticket.payment_service.dto.message.IssuanceResult;
import com.flashticket.payment_service.dto.message.OrderCompleted;
import com.flashticket.payment_service.dto.message.PaymentDecision;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.hashing.ContractHashService;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/** Message-specific checks after ContractCodec has validated the typed wire contract. */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class MessageInvariantValidator {
  PurchaseSnapshotValidator purchases;
  ContractHashService hashes;

  public MessageInvariantValidator(
      PurchaseSnapshotValidator purchases, ContractHashService hashes) {
    this.purchases = Objects.requireNonNull(purchases);
    this.hashes = Objects.requireNonNull(hashes);
  }

  public void validateStartPayment(Envelope<StartPayment> envelope) {
    if (envelope == null || envelope.data() == null) fail("$", "CONSTRAINT_VIOLATION");
    var data = envelope.data();
    purchases.validate(data.purchase());
    if (!"payment.start-requested.v1".equals(envelope.messageType()))
      fail("$.messageType", "MESSAGE_TYPE_MISMATCH");
    if (!Objects.equals(envelope.messageId(), data.requestId()))
      fail("$.messageId", "MESSAGE_ID_MISMATCH");
    if (!Objects.equals(envelope.aggregateId(), data.purchase().orderId()))
      fail("$.aggregateId", "AGGREGATE_ID_MISMATCH");
    if (!Objects.equals(envelope.correlationId(), data.purchase().orderId()))
      fail("$.correlationId", "CORRELATION_ID_MISMATCH");
    String expected =
        hashes.requestHash(
            data.purchase().orderId(), data.provider(), data.purchase().buyerSubject());
    if (!expected.equals(data.requestHash())) fail("$.data.requestHash", "REQUEST_HASH_MISMATCH");
  }

  private static void fail(String field, String code) {
    throw new ContractValidationException(field, code);
  }

  public void validatePaymentDecision(Envelope<PaymentDecision> envelope) {
    var decision = envelope.data();
    if (!"booking.payment-decision.v1".equals(envelope.messageType())
        || !Objects.equals(envelope.aggregateId(), decision.orderId())
        || !Objects.equals(envelope.correlationId(), decision.orderId())
        || !decision.isAcceptanceConsistent()) fail("$", "PAYMENT_DECISION_MISMATCH");
  }

  public void validateIssuanceResult(Envelope<IssuanceResult> envelope) {
    var result = envelope.data();
    // Ticket hiện đặt type cũ; topology đã deploy định tuyến thất bại bằng
    // ticket.issuance-failed.v1.
    if ((!"ticket.issuance-result.v1".equals(envelope.messageType())
            && !"ticket.issuance-failed.v1".equals(envelope.messageType()))
        || !Objects.equals(envelope.aggregateId(), result.orderId())
        || !Objects.equals(envelope.correlationId(), result.orderId())
        || !result.isResultConsistent()) fail("$", "ISSUANCE_RESULT_MISMATCH");
  }

  public void validateOrderCompleted(Envelope<OrderCompleted> envelope) {
    if (!"booking.order-completed.v1".equals(envelope.messageType())
        || !Objects.equals(envelope.aggregateId(), envelope.data().orderId())
        || !Objects.equals(envelope.correlationId(), envelope.data().orderId()))
      fail("$", "ORDER_COMPLETED_MISMATCH");
  }

  public void validateFinanceConfiguration(Envelope<FinanceConfiguration> envelope) {
    var configuration = envelope.data();
    if (!"event.finance-configured.v1".equals(envelope.messageType())
        || !Objects.equals(envelope.aggregateId(), configuration.eventId())
        || new BigDecimal(configuration.feeRate()).compareTo(BigDecimal.ZERO) < 0
        || new BigDecimal(configuration.feeRate()).compareTo(BigDecimal.ONE) > 0)
      fail("$", "FINANCE_CONFIGURATION_MISMATCH");
  }

  public void validateEventCancellation(Envelope<EventCancellation> envelope) {
    var cancellation = envelope.data();
    if (!"event.cancelled.v1".equals(envelope.messageType())
        || !Objects.equals(envelope.aggregateId(), cancellation.eventId())
        || !Objects.equals(cancellation.sales().eventId(), cancellation.eventId())
        || !Objects.equals(cancellation.access().eventId(), cancellation.eventId())
        || cancellation.access().eventStatus() != EventState.CANCELLED
        || cancellation.sales().eventStatus() != EventState.CANCELLED)
      fail("$", "EVENT_CANCELLATION_MISMATCH");
  }
}
