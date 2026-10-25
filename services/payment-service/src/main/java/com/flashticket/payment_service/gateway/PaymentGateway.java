package com.flashticket.payment_service.gateway;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public interface PaymentGateway {
  URI createPayment(PaymentContext context);

  ProviderPaymentResult verifyNotification(String rawQuery);

  ProviderPaymentResult queryPayment(PaymentContext context, UUID requestId, Instant requestedAt);

  ProviderRefundResult refund(RefundContext context);

  ProviderRefundResult queryRefund(RefundContext context);
}
