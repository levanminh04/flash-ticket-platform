package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayWireFormat.*;

import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Validates and maps authenticated PAY/query fields to provider outcomes. */
final class VnpayPaymentResultMapper {
  private static final Set<String> PAY_FAILURES =
      Set.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79");
  private final String merchantAccount;

  VnpayPaymentResultMapper(String merchantAccount) {
    this.merchantAccount = merchantAccount;
  }

  ProviderPaymentResult map(Map<String, String> params, boolean query) {
    if (!merchantAccount.equals(params.get("vnp_TmnCode")))
      throw new GatewayException(GatewayException.Code.MERCHANT_MISMATCH);
    var ref = params.getOrDefault("vnp_TxnRef", "");
    if (!ref.matches("[0-9a-f]{32}")) throw VnpayCodec.invalid();
    var attempt =
        UUID.fromString(
            ref.substring(0, 8)
                + "-"
                + ref.substring(8, 12)
                + "-"
                + ref.substring(12, 16)
                + "-"
                + ref.substring(16, 20)
                + "-"
                + ref.substring(20));
    var amount = amountVnd(params.get("vnp_Amount"));
    if (params.containsKey("vnp_CurrCode") && !"VND".equals(params.get("vnp_CurrCode")))
      throw new GatewayException(GatewayException.Code.AMOUNT_MISMATCH);
    var response = params.getOrDefault("vnp_ResponseCode", "");
    var status = params.getOrDefault("vnp_TransactionStatus", "");
    if (!response.matches("[0-9]{2}") || !status.matches("[0-9]{2}")) throw VnpayCodec.invalid();
    var state = ProviderPaymentResult.State.UNKNOWN;
    if ("00".equals(response) && "00".equals(status)) state = ProviderPaymentResult.State.SUCCEEDED;
    else if ("00".equals(response) && "01".equals(status))
      state = ProviderPaymentResult.State.PENDING;
    else if ((!query && PAY_FAILURES.contains(response) && "02".equals(status))
        || (query && "00".equals(response) && "02".equals(status)))
      state = ProviderPaymentResult.State.FAILED;
    var chargeId = params.get("vnp_TransactionNo");
    Instant paidAt = null;
    if (params.containsKey("vnp_PayDate") && !params.get("vnp_PayDate").isEmpty())
      paidAt = parseTime(params.get("vnp_PayDate"));
    if (state == ProviderPaymentResult.State.SUCCEEDED
        && (chargeId == null
            || !chargeId.matches("[0-9]{1,15}")
            || new BigInteger(chargeId).signum() == 0
            || paidAt == null)) throw VnpayCodec.invalid();
    return new ProviderPaymentResult(
        attempt, merchantAccount, amount, "VND", chargeId, paidAt, state, response, status);
  }
}
