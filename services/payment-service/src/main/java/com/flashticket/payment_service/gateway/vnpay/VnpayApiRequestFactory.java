package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayWireFormat.*;

import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Builds query/refund metadata without regenerating persisted identity or PAY dates. */
final class VnpayApiRequestFactory {
  private final String apiClientIp;

  VnpayApiRequestFactory(String apiClientIp) {
    this.apiClientIp = apiClientIp;
  }

  Map<String, String> create(
      String command, PaymentContext context, UUID requestId, Instant requestedAt) {
    var data = new LinkedHashMap<String, String>();
    data.put("vnp_RequestId", reference(requestId));
    data.put("vnp_Version", "2.1.0");
    data.put("vnp_Command", command);
    data.put("vnp_TmnCode", context.merchantAccount());
    data.put("vnp_TxnRef", reference(context.attemptId()));
    data.put("vnp_TransactionDate", wireTime(context.startedAt()));
    data.put("vnp_CreateDate", wireTime(requestedAt));
    data.put("vnp_IpAddr", apiClientIp);
    data.put(
        "vnp_OrderInfo",
        command.equals("refund") ? "Hoan tien " + requestId : "Truy van " + context.orderId());
    return data;
  }
}
