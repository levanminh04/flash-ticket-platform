package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayApiFixtures.*;
import static com.flashticket.payment_service.gateway.vnpay.VnpayTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.gateway.*;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class VnpayPaymentQueryTest {
  @Test
  void keepsQueryApiErrorsAndAmbiguousPaymentStatusesUnresolved() {
    var p = VnpayTestFixtures.properties();
    for (var status : List.of("00", "01", "02", "04", "05", "06", "07", "09", "88")) {
      var data = response("querydr");
      data.put("vnp_TransactionStatus", status);
      var gateway =
          new VnpayGateway(
              p, VnpayTestFixtures.CLOCK, new VnpayApiClient(p, (u, b, t) -> responseBody(data)));
      var expected =
          switch (status) {
            case "00" -> ProviderPaymentResult.State.SUCCEEDED;
            case "01" -> ProviderPaymentResult.State.PENDING;
            case "02" -> ProviderPaymentResult.State.FAILED;
            default -> ProviderPaymentResult.State.UNKNOWN;
          };
      assertThat(
              gateway
                  .queryPayment(
                      VnpayTestFixtures.context(),
                      UUID.randomUUID(),
                      VnpayTestFixtures.START.plusSeconds(120))
                  .state())
          .as("query transaction status %s", status)
          .isEqualTo(expected);
    }
    for (var code : List.of("02", "03", "91", "94", "97", "99", "88")) {
      var data = response("querydr");
      data.put("vnp_ResponseCode", code);
      data.put("vnp_TransactionStatus", "02");
      var gateway =
          new VnpayGateway(
              p, VnpayTestFixtures.CLOCK, new VnpayApiClient(p, (u, b, t) -> responseBody(data)));
      assertThat(
              gateway
                  .queryPayment(
                      VnpayTestFixtures.context(),
                      UUID.randomUUID(),
                      VnpayTestFixtures.START.plusSeconds(120))
                  .state())
          .as("query API failure %s is not payment failure", code)
          .isEqualTo(ProviderPaymentResult.State.UNKNOWN);
    }
  }

  @Test
  void sendsOriginalPayDateAndChecksSignedQueryOutcome() {
    var captured = new AtomicReference<String>();
    var p = VnpayTestFixtures.properties();
    var r = response("querydr");
    r.put("vnp_TransactionStatus", "01");
    var client =
        new VnpayApiClient(
            p,
            (uri, body, timeout) -> {
              captured.set(body);
              return responseBody(r);
            });
    var gateway = new VnpayGateway(p, VnpayTestFixtures.CLOCK, client);
    var result =
        gateway.queryPayment(
            VnpayTestFixtures.context(),
            UUID.fromString("aaaaaaaa-1111-4222-8333-bbbbbbbbbbbb"),
            VnpayTestFixtures.START.plusSeconds(120));
    var request = JSON.readTree(captured.get());
    assertThat(request.get("vnp_TransactionDate").asString()).isEqualTo("20261025100000");
    assertThat(request.get("vnp_CreateDate").asString()).isEqualTo("20261025100200");
    assertThat(request.get("vnp_RequestId").asString())
        .isEqualTo("aaaaaaaa111142228333bbbbbbbbbbbb");
    assertThat(request.get("vnp_TxnRef").asString()).isEqualTo("11111111222243338444555555555555");
    assertThat(request.get("vnp_SecureHash").asString())
        .isEqualTo(VnpayTestFixtures.golden("querySignature"));
    assertThat(result.state()).isEqualTo(ProviderPaymentResult.State.PENDING);
  }
}
