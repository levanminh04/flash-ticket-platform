package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.gateway.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;

class VnpayConfigurationTest {
  final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(VnpayConfiguration.class);
  static final String[] ENABLED = {
    "flashticket.payment.vnpay.enabled=true",
    "flashticket.payment.vnpay.tmn-code=TEST1234",
    "flashticket.payment.vnpay.hash-secret=key",
    "flashticket.payment.vnpay.return-url=https://merchant.example.test/api/v1/payments/vnpay/return",
    "flashticket.payment.vnpay.ipn-url=https://merchant.example.test/api/v1/payments/vnpay/ipn",
    "flashticket.payment.vnpay.sandbox-payer-ip=127.0.0.1",
    "flashticket.payment.vnpay.api-client-ip=127.0.0.1"
  };

  @Test
  void disabledHasNoGatewayAndEnabledBindsGateway() {
    runner.run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(PaymentGateway.class));
    runner
        .withPropertyValues(ENABLED)
        .run(c -> assertThat(c).hasNotFailed().hasSingleBean(PaymentGateway.class));
  }

  @Test
  void refusesCallbacksWithoutDurableRuntimeHandler() {
    runner
        .withPropertyValues(ENABLED)
        .withPropertyValues("flashticket.payment.vnpay.callbacks-enabled=true")
        .run(c -> assertThat(c).hasFailed());
    runner
        .withPropertyValues(ENABLED)
        .withPropertyValues("spring.profiles.active=production")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void resolvesRemotePlaceholdersFromClientEnvironmentWithoutEmbeddingSecretInYaml()
      throws Exception {
    var source =
        new YamlPropertySourceLoader()
            .load(
                "remote",
                new FileSystemResource(
                    "../../infrastructure/config-server/src/main/resources/config/payment-service.yaml"));
    runner
        .withInitializer(
            c -> {
              c.getEnvironment()
                  .getPropertySources()
                  .addFirst(
                      new MapPropertySource(
                          "payment-environment",
                          Map.of(
                              "PAYMENT_VNPAY_ENABLED",
                              "true",
                              "PAYMENT_VNPAY_TMN_CODE",
                              "TEST1234",
                              "PAYMENT_VNPAY_HASH_SECRET",
                              "sentinel-client-only",
                              "PAYMENT_VNPAY_RETURN_URL",
                              "https://merchant.example.test/api/v1/payments/vnpay/return",
                              "PAYMENT_VNPAY_IPN_URL",
                              "https://merchant.example.test/api/v1/payments/vnpay/ipn",
                              "PAYMENT_VNPAY_SANDBOX_PAYER_IP",
                              "127.0.0.1",
                              "PAYMENT_VNPAY_API_CLIENT_IP",
                              "127.0.0.1")));
              source.forEach(ps -> c.getEnvironment().getPropertySources().addLast(ps));
            })
        .run(
            c -> {
              assertThat(c).hasNotFailed();
              assertThat(c.getBean(VnpayProperties.class).getHashSecret())
                  .isEqualTo("sentinel-client-only");
              assertThat(c.getBean(VnpayProperties.class).toString())
                  .doesNotContain("sentinel-client-only");
            });
    assertThat(source.getFirst().getProperty("flashticket.payment.vnpay.hash-secret"))
        .isEqualTo("${PAYMENT_VNPAY_HASH_SECRET:}");
  }
}
