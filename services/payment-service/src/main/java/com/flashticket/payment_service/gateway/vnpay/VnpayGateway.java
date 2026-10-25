package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayWireFormat.*;

import com.flashticket.payment_service.config.VnpayProperties;
import com.flashticket.payment_service.gateway.*;
import java.math.*;
import java.net.URI;
import java.time.*;
import java.util.*;

/** Provider-port facade; each payment operation has a focused implementation. */
public final class VnpayGateway implements PaymentGateway {
  private final VnpayPaymentUrlBuilder urls;
  private final VnpayNotificationVerifier notifications;
  private final VnpayPaymentQuery queries;
  private final VnpayRefundClient refunds;

  public VnpayGateway(VnpayProperties properties, Clock clock) {
    this(properties, clock, new VnpayApiClient(properties));
  }

  public VnpayGateway(VnpayProperties properties, Clock clock, VnpayApiClient api) {
    properties.validate(new String[0]);
    if (!properties.isEnabled()) throw VnpayCodec.invalid();
    Objects.requireNonNull(api);
    var results = new VnpayPaymentResultMapper(properties.getTmnCode());
    var requests = new VnpayApiRequestFactory(properties.getApiClientIp());
    urls = new VnpayPaymentUrlBuilder(properties, clock);
    notifications = new VnpayNotificationVerifier(properties.getHashSecret(), results);
    queries = new VnpayPaymentQuery(properties.getTmnCode(), api, requests, results);
    refunds =
        new VnpayRefundClient(
            properties.getTmnCode(), properties.isRefundEnabled(), api, requests, results);
  }

  @Override
  public URI createPayment(PaymentContext context) {
    return urls.build(context);
  }

  @Override
  public ProviderPaymentResult verifyNotification(String rawQuery) {
    return notifications.verify(rawQuery);
  }

  @Override
  public ProviderPaymentResult queryPayment(
      PaymentContext context, UUID requestId, Instant requestedAt) {
    return queries.query(context, requestId, requestedAt);
  }

  @Override
  public ProviderRefundResult refund(RefundContext context) {
    return refunds.refund(context);
  }

  @Override
  public ProviderRefundResult queryRefund(RefundContext context) {
    return refunds.queryRefund(context);
  }
}
