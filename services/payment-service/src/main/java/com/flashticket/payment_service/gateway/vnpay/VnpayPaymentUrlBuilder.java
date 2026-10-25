package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayWireFormat.*;

import com.flashticket.payment_service.config.VnpayProperties;
import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Builds PAY URLs from persisted attempt inputs. */
final class VnpayPaymentUrlBuilder {
  private final VnpayProperties properties;
  private final Clock clock;

  VnpayPaymentUrlBuilder(VnpayProperties properties, Clock clock) {
    this.properties = properties.snapshot();
    this.clock = Objects.requireNonNull(clock);
  }

  URI build(PaymentContext context) {
    validateMerchant(context, properties.getTmnCode());
    if (!"VND".equals(context.currency()))
      throw new GatewayException(GatewayException.Code.AMOUNT_MISMATCH);
    if (!context.startedAt().isBefore(context.expiresAt())
        || context.startedAt().isAfter(clock.instant())
        || context
            .expiresAt()
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
            .isBefore(clock.instant().plusSeconds(1))) throw VnpayCodec.invalid();
    var params = new TreeMap<String, String>();
    params.put("vnp_Version", "2.1.0");
    params.put("vnp_Command", "pay");
    params.put("vnp_TmnCode", context.merchantAccount());
    params.put("vnp_TxnRef", reference(context.attemptId()));
    params.put("vnp_Amount", amountWire(context.amountVnd()));
    params.put("vnp_CurrCode", "VND");
    params.put("vnp_CreateDate", wireTime(context.startedAt()));
    params.put("vnp_ExpireDate", wireTime(context.expiresAt()));
    params.put("vnp_ReturnUrl", properties.getReturnUrl());
    params.put("vnp_IpAddr", properties.getSandboxPayerIp());
    params.put("vnp_OrderInfo", "Thanh toan ve " + context.orderId());
    params.put("vnp_OrderType", "other");
    params.put("vnp_Locale", "vn");
    var query = VnpayCodec.payQuery(params);
    return URI.create(
        properties.getPaymentUrl()
            + "?"
            + query
            + "&vnp_SecureHash="
            + VnpayCodec.hmac(properties.getHashSecret(), query));
  }
}
