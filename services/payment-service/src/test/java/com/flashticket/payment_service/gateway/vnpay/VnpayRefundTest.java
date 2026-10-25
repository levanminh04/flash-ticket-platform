package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayApiFixtures.*;
import static com.flashticket.payment_service.gateway.vnpay.VnpayTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.gateway.*;
import java.net.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class VnpayRefundTest {
  @Test
  void refusesNonVndRefundBeforeAnyMoneyRequest() {
    var p = VnpayTestFixtures.properties();
    p.setRefundEnabled(true);
    var calls = new ArrayList<String>();
    var client =
        new VnpayApiClient(
            p,
            (u, b, t) -> {
              calls.add(b);
              return responseBody(response("refund"));
            });
    var gateway = new VnpayGateway(p, VnpayTestFixtures.CLOCK, client);
    var c = VnpayTestFixtures.context();
    var foreign =
        new PaymentContext(
            c.attemptId(),
            c.orderId(),
            c.merchantAccount(),
            c.amountVnd(),
            "USD",
            c.startedAt(),
            c.expiresAt());
    var refund =
        new RefundContext(
            UUID.randomUUID(), foreign, "123456", c.startedAt().plusSeconds(60), "operator");
    assertThatThrownBy(() -> gateway.refund(refund)).isInstanceOf(GatewayException.class);
    assertThat(calls).isEmpty();
  }

  @Test
  void blocksDisabledRefundWithoutNetworkAndKeepsAmbiguousRefundUnknown() {
    var p = VnpayTestFixtures.properties();
    var calls = new ArrayList<String>();
    var client =
        new VnpayApiClient(
            p,
            (u, b, t) -> {
              calls.add(b);
              return responseBody(response("refund"));
            });
    var gateway = new VnpayGateway(p, VnpayTestFixtures.CLOCK, client);
    var refund =
        new RefundContext(
            UUID.fromString("bbbbbbbb-1111-4222-8333-aaaaaaaaaaaa"),
            VnpayTestFixtures.context(),
            "123456",
            VnpayTestFixtures.START.plusSeconds(60),
            "sandbox-operator");
    assertThatThrownBy(() -> gateway.refund(refund)).isInstanceOf(GatewayException.class);
    assertThat(calls).isEmpty();
    assertThat(gateway.queryRefund(refund).state()).isEqualTo(ProviderRefundResult.State.UNKNOWN);
    assertThat(calls).isEmpty();
    p.setRefundEnabled(true);
    assertThat(new VnpayGateway(p, VnpayTestFixtures.CLOCK, client).refund(refund).state())
        .isEqualTo(ProviderRefundResult.State.SUCCEEDED);
    assertThat(calls).hasSize(1);
    assertThat(JSON.readTree(calls.getFirst()).get("vnp_SecureHash").asString())
        .isEqualTo(VnpayTestFixtures.golden("refundSignature"));
    var unavailable =
        new VnpayGateway(
            p,
            VnpayTestFixtures.CLOCK,
            new VnpayApiClient(
                p,
                (u, b, t) -> {
                  throw new GatewayException(GatewayException.Code.UNAVAILABLE);
                }));
    assertThat(unavailable.refund(refund).state()).isEqualTo(ProviderRefundResult.State.UNKNOWN);
  }
}
