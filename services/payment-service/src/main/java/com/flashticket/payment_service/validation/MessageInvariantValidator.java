package com.flashticket.payment_service.validation;
import com.flashticket.payment_service.hashing.ContractHashService;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.StartPayment;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Message-specific checks after ContractCodec has validated the typed wire contract. */
@Component
public final class MessageInvariantValidator {
  private final PurchaseSnapshotValidator purchases;
  private final ContractHashService hashes;
  public MessageInvariantValidator(PurchaseSnapshotValidator purchases, ContractHashService hashes) {
    this.purchases = Objects.requireNonNull(purchases);
    this.hashes = Objects.requireNonNull(hashes);
  }
  public void validateStartPayment(Envelope<StartPayment> envelope) {
    if (envelope == null || envelope.data() == null) fail("$","CONSTRAINT_VIOLATION");
    var data = envelope.data();
    purchases.validate(data.purchase());
    if (!"payment.start-requested.v1".equals(envelope.messageType())) fail("$.messageType","MESSAGE_TYPE_MISMATCH");
    if (!Objects.equals(envelope.messageId(),data.requestId())) fail("$.messageId","MESSAGE_ID_MISMATCH");
    if (!Objects.equals(envelope.aggregateId(),data.purchase().orderId())) fail("$.aggregateId","AGGREGATE_ID_MISMATCH");
    if (!Objects.equals(envelope.correlationId(),data.purchase().orderId())) fail("$.correlationId","CORRELATION_ID_MISMATCH");
    String expected = hashes.requestHash(data.purchase().orderId(),data.provider(),data.purchase().buyerSubject());
    if (!expected.equals(data.requestHash())) fail("$.data.requestHash","REQUEST_HASH_MISMATCH");
  }
  private static void fail(String field,String code) { throw new ContractValidationException(field,code); }
}
