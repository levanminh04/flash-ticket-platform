package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.gateway.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class VnpayNotificationTest {
  @Test
  void pinsPayPendingUnknownAndEachRecognizedFailureSeparately() {
    var gateway = new VnpayGateway(properties(), CLOCK);
    for (var status : List.of("01", "02", "04", "05", "06", "07", "09", "88")) {
      var data = notification();
      data.put("vnp_TransactionStatus", status);
      assertThat(gateway.verifyNotification(signed(data)).state())
          .as("PAY status %s", status)
          .isEqualTo(
              status.equals("01")
                  ? ProviderPaymentResult.State.PENDING
                  : ProviderPaymentResult.State.UNKNOWN);
    }
    for (var code : List.of("09", "10", "11", "12", "13", "24", "51", "65", "75", "79")) {
      var data = notification();
      data.put("vnp_ResponseCode", code);
      data.put("vnp_TransactionStatus", "02");
      assertThat(gateway.verifyNotification(signed(data)).state())
          .as("PAY definitive failure %s", code)
          .isEqualTo(ProviderPaymentResult.State.FAILED);
      data.put("vnp_TransactionStatus", "00");
      assertThat(gateway.verifyNotification(signed(data)).state())
          .as("PAY contradictory result %s", code)
          .isEqualTo(ProviderPaymentResult.State.UNKNOWN);
    }
  }

  @Test
  void verifiesNotificationAndChecksPersistedMoneyAndMerchant() {
    var result = new VnpayGateway(properties(), CLOCK).verifyNotification(signed(notification()));
    assertThat(result.state()).isEqualTo(ProviderPaymentResult.State.SUCCEEDED);
    assertThat(result.amountVnd()).isEqualByComparingTo("100000");
    assertThat(result.attemptId()).isEqualTo(ATTEMPT);
    assertThatCode(() -> result.validateAgainst(context())).doesNotThrowAnyException();
    var c = context();
    assertThatThrownBy(
            () ->
                result.validateAgainst(
                    new PaymentContext(
                        c.attemptId(),
                        c.orderId(),
                        c.merchantAccount(),
                        BigDecimal.ONE,
                        c.currency(),
                        c.startedAt(),
                        c.expiresAt())))
        .isInstanceOf(GatewayException.class);
  }

  @Test
  void rejectsAmbiguousTamperedMalformedAndMismatchedNotifications() {
    var gateway = new VnpayGateway(properties(), CLOCK);
    var query = signed(notification());
    for (var bad :
        List.of(
            query.replace("10000000", "10000100"),
            query + "&vnp_Amount=10000000",
            query + "&vnp%5fAmount=10000000",
            "vnp_Test=%GG",
            "vnp_Test=%FF",
            "x=" + "a".repeat(8193)))
      assertThatThrownBy(() -> gateway.verifyNotification(bad))
          .isInstanceOf(GatewayException.class);
    for (var field :
        List.of("vnp_Amount", "vnp_TmnCode", "vnp_TransactionNo", "vnp_TxnRef", "vnp_PayDate")) {
      var m = notification();
      m.put(
          field,
          switch (field) {
            case "vnp_Amount" -> "10000001";
            case "vnp_TmnCode" -> "OTHER123";
            case "vnp_TransactionNo" -> "0";
            case "vnp_TxnRef" -> "bad";
            default -> "20260230120000";
          });
      assertThatThrownBy(() -> gateway.verifyNotification(signed(m)))
          .isInstanceOf(GatewayException.class);
    }
  }

  @Test
  void neverTreatsUnknownFraudOrInconsistentStatusesAsPaid() {
    var gateway = new VnpayGateway(properties(), CLOCK);
    for (var status : List.of("01", "04", "05", "06", "07", "88")) {
      var m = notification();
      m.put("vnp_TransactionStatus", status);
      assertThat(gateway.verifyNotification(signed(m)).state())
          .isNotEqualTo(ProviderPaymentResult.State.SUCCEEDED);
    }
    var failed = notification();
    failed.put("vnp_ResponseCode", "24");
    failed.put("vnp_TransactionStatus", "02");
    assertThat(gateway.verifyNotification(signed(failed)).state())
        .isEqualTo(ProviderPaymentResult.State.FAILED);
    var odd = notification();
    odd.put("vnp_ResponseCode", "88");
    assertThat(gateway.verifyNotification(signed(odd)).state())
        .isEqualTo(ProviderPaymentResult.State.UNKNOWN);
  }
}
