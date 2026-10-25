package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayWireFormat.*;

import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Queries one persisted payment attempt and validates the returned outcome. */
final class VnpayPaymentQuery {
  private final String merchantAccount;
  private final VnpayApiClient api;
  private final VnpayApiRequestFactory requests;
  private final VnpayPaymentResultMapper results;

  VnpayPaymentQuery(
      String merchantAccount,
      VnpayApiClient api,
      VnpayApiRequestFactory requests,
      VnpayPaymentResultMapper results) {
    this.merchantAccount = merchantAccount;
    this.api = api;
    this.requests = requests;
    this.results = results;
  }

  ProviderPaymentResult query(PaymentContext context, UUID requestId, Instant requestedAt) {
    validateMerchant(context, merchantAccount);
    amountWire(context.amountVnd());
    var data = requests.create("querydr", context, requestId, requestedAt);
    var response = api.post("querydr", data);
    if (!"01".equals(response.get("vnp_TransactionType"))) throw VnpayCodec.invalid();
    var result = results.map(response, true);
    result.validateAgainst(context);
    return result;
  }
}
