package com.flashticket.payment_service.gateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Authentication by the adapter does not replace validation against the persisted attempt. */
public record ProviderPaymentResult(
    UUID attemptId,
    String merchantAccount,
    BigDecimal amountVnd,
    String currency,
    String providerChargeId,
    Instant paidAt,
    State state,
    String responseCode,
    String transactionStatus) {
  public enum State {
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
  }

  public void validateAgainst(PaymentContext context) {
    if (!attemptId.equals(context.attemptId()))
      throw new GatewayException(GatewayException.Code.ATTEMPT_NOT_FOUND);
    if (!merchantAccount.equals(context.merchantAccount()))
      throw new GatewayException(GatewayException.Code.MERCHANT_MISMATCH);
    if (!"VND".equals(context.currency())
        || !currency.equals(context.currency())
        || amountVnd.compareTo(context.amountVnd()) != 0)
      throw new GatewayException(GatewayException.Code.AMOUNT_MISMATCH);
  }
}
