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

final class VnpayApiClientTest {
  @Test
  void retainsTheApprovedEndpointAndSecretAfterBindingObjectChanges() {
    var p = VnpayTestFixtures.properties();
    var endpoint = new AtomicReference<URI>();
    var client =
        new VnpayApiClient(
            p,
            (uri, body, timeout) -> {
              endpoint.set(uri);
              return responseBody(response("querydr"));
            });
    p.setApiUrl("https://untrusted.example.test/api");
    p.setHashSecret("changed-after-startup");
    assertThatCode(() -> client.post("querydr", Map.of())).doesNotThrowAnyException();
    assertThat(endpoint.get().toString())
        .isEqualTo("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
  }

  @Test
  void refusesWrongContextCommandSignatureAndDuplicateJsonKeys() {
    var p = VnpayTestFixtures.properties();
    for (var field : List.of("vnp_TxnRef", "vnp_Amount", "vnp_TransactionType", "vnp_Command")) {
      var r = response("querydr");
      r.put(
          field,
          switch (field) {
            case "vnp_TxnRef" -> "aaaaaaaa111142228333bbbbbbbbbbbb";
            case "vnp_Amount" -> "100";
            case "vnp_TransactionType" -> "02";
            default -> "refund";
          });
      var gateway =
          new VnpayGateway(
              p, VnpayTestFixtures.CLOCK, new VnpayApiClient(p, (u, b, t) -> responseBody(r)));
      assertThatThrownBy(
              () ->
                  gateway.queryPayment(
                      VnpayTestFixtures.context(),
                      UUID.randomUUID(),
                      VnpayTestFixtures.START.plusSeconds(120)))
          .isInstanceOf(GatewayException.class);
    }
    for (var body :
        List.of(
            "{\"vnp_Amount\":\"1\",\"vnp_Amount\":\"2\"}",
            "[]",
            "x".repeat(65537),
            responseBody(response("querydr")).replace("10000000", "10000100"))) {
      var client = new VnpayApiClient(p, (u, b, t) -> body);
      assertThatThrownBy(() -> client.post("querydr", Map.of()))
          .isInstanceOf(GatewayException.class);
    }
  }
}
