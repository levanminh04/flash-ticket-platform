package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.gateway.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class VnpayPaymentUrlTest {
  @Test
  void hmacMatchesIndependentKnownVectorAndUrlKeepsOriginalIdentity() {
    assertThat(VnpayCodec.hmac("key", "The quick brown fox jumps over the lazy dog"))
        .isEqualTo(
            "b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a");
    var gateway = new VnpayGateway(properties(), CLOCK);
    var url = gateway.createPayment(context());
    assertThat(url.toString()).isEqualTo(golden("payUrl"));
    assertThat(url.toString())
        .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?")
        .contains(
            "vnp_Amount=10000000",
            "vnp_TxnRef=11111111222243338444555555555555",
            "vnp_CreateDate=20261025100000",
            "vnp_ExpireDate=20261025101000",
            "vnp_CurrCode=VND");
    assertThat(
            new VnpayGateway(properties(), Clock.offset(CLOCK, Duration.ofMinutes(1)))
                .createPayment(context()))
        .isEqualTo(url);
    assertThat(VnpayCodec.payQuery(Map.of("vnp_Test", "space + % & =")))
        .isEqualTo("vnp_Test=space+%2B+%25+%26+%3D");
  }

  @Test
  void rejectsExpiredFractionalOverflowAndForeignContexts() {
    var c = context();
    var gateway = new VnpayGateway(properties(), CLOCK);
    for (var amount : List.of("0", "-1", "1.5", "10000000000"))
      assertThatThrownBy(
              () ->
                  gateway.createPayment(
                      new PaymentContext(
                          c.attemptId(),
                          c.orderId(),
                          c.merchantAccount(),
                          new BigDecimal(amount),
                          "VND",
                          c.startedAt(),
                          c.expiresAt())))
          .isInstanceOf(GatewayException.class);
    assertThatThrownBy(
            () ->
                new VnpayGateway(properties(), Clock.fixed(c.expiresAt(), ZoneOffset.UTC))
                    .createPayment(c))
        .isInstanceOf(GatewayException.class);
    assertThatThrownBy(
            () ->
                new VnpayGateway(
                        properties(), Clock.fixed(c.expiresAt().minusMillis(500), ZoneOffset.UTC))
                    .createPayment(c))
        .isInstanceOf(GatewayException.class);
    assertThatThrownBy(
            () ->
                gateway.createPayment(
                    new PaymentContext(
                        c.attemptId(),
                        c.orderId(),
                        "OTHER123",
                        c.amountVnd(),
                        "VND",
                        c.startedAt(),
                        c.expiresAt())))
        .isInstanceOf(GatewayException.class);
  }
}
