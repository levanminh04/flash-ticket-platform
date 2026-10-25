package com.flashticket.payment_service.gateway.vnpay;

import static com.flashticket.payment_service.gateway.vnpay.VnpayTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.gateway.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

final class VnpayGatewayTest {
  @Test
  void retainsStartupConfigurationWhenTheBindingObjectChanges() {
    var p = properties();
    var gateway = new VnpayGateway(p, CLOCK);
    var url = gateway.createPayment(context());
    p.setPaymentUrl("https://untrusted.example.test/pay");
    p.setHashSecret("changed-after-startup");
    assertThat(gateway.createPayment(context())).isEqualTo(url);
    assertThat(gateway.verifyNotification(signed(notification())).state())
        .isEqualTo(ProviderPaymentResult.State.SUCCEEDED);
  }
}
