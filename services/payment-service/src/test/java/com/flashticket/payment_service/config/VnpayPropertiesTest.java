package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class VnpayPropertiesTest {
  public static VnpayProperties configured() {
    var p = new VnpayProperties();
    p.setEnabled(true);
    p.setTmnCode("TEST1234");
    p.setHashSecret("fixture-secret-never-production");
    p.setReturnUrl("https://merchant.example.test/api/v1/payments/vnpay/return");
    p.setIpnUrl("https://merchant.example.test/api/v1/payments/vnpay/ipn");
    p.setSandboxPayerIp("127.0.0.1");
    p.setApiClientIp("127.0.0.1");
    return p;
  }

  @Test
  void disabledNeedsNoCredentialsButCannotExposeCallbacks() {
    var p = new VnpayProperties();
    assertThatCode(() -> p.validate(new String[0])).doesNotThrowAnyException();
    p.setCallbacksEnabled(true);
    assertThatThrownBy(() -> p.validate(new String[0])).hasMessageContaining("callbacks");
  }

  @Test
  void bindsEnabledConfigWithoutLeakingItsSecret() {
    var binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of("flashticket.payment.vnpay.hash-secret", "binding-sentinel-secret")));
    var p = binder.bind("flashticket.payment.vnpay", Bindable.of(VnpayProperties.class)).get();
    assertThat(p.getHashSecret()).isEqualTo("binding-sentinel-secret");
    assertThat(p.toString()).doesNotContain("binding-sentinel-secret");
    var valid = configured();
    assertThatCode(() -> valid.validate(new String[0])).doesNotThrowAnyException();
    valid.setTmnCode("");
    assertThatThrownBy(() -> valid.validate(new String[0]))
        .hasMessageContaining("tmn-code")
        .hasMessageNotContaining(valid.getHashSecret());
  }

  @Test
  void refusesProductionRedirectHostsAndInvalidOperationalValues() {
    var p = configured();
    assertThatThrownBy(() -> p.validate(new String[] {"prod"})).hasMessageContaining("sandbox");
    for (var url :
        new String[] {
          "https://sandbox.vnpayment.vn.evil.test/paymentv2/vpcpay.html",
          "http://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
          "https://sandbox.vnpayment.vn:444/paymentv2/vpcpay.html",
          "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?x=1"
        }) {
      p.setPaymentUrl(url);
      assertThatThrownBy(() -> p.validate(new String[0])).hasMessageContaining("payment-url");
    }
    var invalidTimeout = configured();
    invalidTimeout.setReadTimeout(Duration.ZERO);
    assertThatThrownBy(() -> invalidTimeout.validate(new String[0]))
        .hasMessageContaining("timeout");
    var invalidIp = configured();
    invalidIp.setSandboxPayerIp("attacker.example.test");
    assertThatThrownBy(() -> invalidIp.validate(new String[0])).hasMessageContaining("payer-ip");
    var invalidCallback = configured();
    invalidCallback.setIpnUrl("https://different.example.test/api/v1/payments/vnpay/ipn");
    assertThatThrownBy(() -> invalidCallback.validate(new String[0]))
        .hasMessageContaining("origin");
  }
}
