package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayWireFormat.*;

import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Submits an explicitly enabled refund once; preserves uncertainty and lookup limits. */
final class VnpayRefundClient {
  private final String merchantAccount;
  private final VnpayApiClient api;
  private final VnpayApiRequestFactory requests;
  private final VnpayPaymentResultMapper results;
  private final boolean refundEnabled;

  VnpayRefundClient(
      String merchantAccount,
      boolean refundEnabled,
      VnpayApiClient api,
      VnpayApiRequestFactory requests,
      VnpayPaymentResultMapper results) {
    this.merchantAccount = merchantAccount;
    this.refundEnabled = refundEnabled;
    this.api = api;
    this.requests = requests;
    this.results = results;
  }

  ProviderRefundResult refund(RefundContext context) {
    if (!refundEnabled) throw new GatewayException(GatewayException.Code.CAPABILITY_UNAVAILABLE);
    validateMerchant(context.payment(), merchantAccount);
    if (!"VND".equals(context.payment().currency()))
      throw new GatewayException(GatewayException.Code.AMOUNT_MISMATCH);
    var data =
        requests.create("refund", context.payment(), context.requestId(), context.requestedAt());
    data.put("vnp_TransactionType", "02");
    data.put("vnp_Amount", amountWire(context.payment().amountVnd()));
    data.put("vnp_TransactionNo", context.providerChargeId());
    data.put("vnp_CreateBy", context.operator());
    try {
      var response = api.post("refund", data);
      if (!"02".equals(response.get("vnp_TransactionType"))) throw VnpayCodec.invalid();
      var result = results.map(response, true);
      result.validateAgainst(context.payment());
      var state =
          switch (result.state()) {
            case SUCCEEDED -> ProviderRefundResult.State.SUCCEEDED;
            case FAILED -> ProviderRefundResult.State.FAILED;
            default -> ProviderRefundResult.State.PROCESSING;
          };
      return new ProviderRefundResult(
          context.requestId(),
          state,
          result.providerChargeId(),
          state == ProviderRefundResult.State.SUCCEEDED ? result.paidAt() : null,
          result.responseCode());
    } catch (GatewayException ex) {
      // The request may already have taken effect; never turn an unreadable response into a new
      // refund.
      return new ProviderRefundResult(
          context.requestId(), ProviderRefundResult.State.UNKNOWN, null, null, ex.code().name());
    }
  }

  ProviderRefundResult queryRefund(RefundContext context) {
    // PAY querydr is not a safe lookup of the original refund request identity.
    return new ProviderRefundResult(
        context.requestId(),
        ProviderRefundResult.State.UNKNOWN,
        null,
        null,
        "REFUND_LOOKUP_UNAVAILABLE");
  }
}
