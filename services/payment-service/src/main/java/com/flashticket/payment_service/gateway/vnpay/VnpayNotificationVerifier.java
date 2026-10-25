package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayWireFormat.*;

import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.time.*;
import java.util.*;

/** Authenticates raw PAY notifications before result mapping. */
final class VnpayNotificationVerifier {
  private final String secret;
  private final VnpayPaymentResultMapper results;

  VnpayNotificationVerifier(String secret, VnpayPaymentResultMapper results) {
    this.secret = secret;
    this.results = results;
  }

  ProviderPaymentResult verify(String rawQuery) {
    var params = VnpayCodec.rawQuery(rawQuery);
    VnpayCodec.verify(secret, VnpayCodec.payQuery(params), params.get("vnp_SecureHash"));
    return results.map(params, false);
  }
}
