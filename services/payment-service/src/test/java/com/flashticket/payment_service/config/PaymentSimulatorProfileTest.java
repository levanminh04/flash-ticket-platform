package com.flashticket.payment_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashticket.payment_service.service.start.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PaymentSimulatorProfileTest {
  @Test
  void standaloneProfileLoadsWithoutAConfigServer() {
    new ApplicationContextRunner()
        .withInitializer(
            new org.springframework.boot.test.context.ConfigDataApplicationContextInitializer())
        .withPropertyValues(
            "spring.config.location=file:src/main/resources/",
            "spring.profiles.active=payment-simulator",
            "CONFIG_SERVER_URL=http://127.0.0.1:1")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(
                      context
                          .getEnvironment()
                          .getProperty("spring.cloud.config.enabled", Boolean.class))
                  .isFalse();
              assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                  .contains("5443/payment_db");
            });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "default",
        "prod",
        "production",
        "payment-simulator,prod",
        "payment-simulator,production"
      })
  void fakeRuntimeCannotActivateOutsideExplicitDevelopmentProfile(String profiles) {
    new ApplicationContextRunner()
        .withPropertyValues("spring.profiles.active=" + profiles)
        .withUserConfiguration(
            PaymentSimulatorConfiguration.class,
            StartPaymentProcessor.class,
            StartPaymentStore.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(BookingPaymentGuard.class);
              assertThat(context).doesNotHaveBean(StartPaymentProcessor.class);
              assertThat(context).doesNotHaveBean("simulatorListener");
              assertThat(context).doesNotHaveBean("simulatorRabbitAdmin");
            });
  }
}
