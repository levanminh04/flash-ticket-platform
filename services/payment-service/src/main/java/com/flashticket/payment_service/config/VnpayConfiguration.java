package com.flashticket.payment_service.config;

import com.flashticket.payment_service.gateway.*;
import com.flashticket.payment_service.gateway.vnpay.VnpayGateway;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class VnpayConfiguration {
  @Bean
  VnpayProperties vnpayProperties(Environment environment) {
    var properties =
        Binder.get(environment)
            .bind("flashticket.payment.vnpay", VnpayProperties.class)
            .orElseGet(VnpayProperties::new);
    properties.validate(environment.getActiveProfiles());
    return properties;
  }

  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock vnpayClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "flashticket.payment.vnpay",
      name = "enabled",
      havingValue = "true")
  PaymentGateway vnpayGateway(VnpayProperties properties, Clock clock) {
    return new VnpayGateway(properties, clock);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "flashticket.payment.vnpay",
      name = "callbacks-enabled",
      havingValue = "true")
  CallbackReadiness vnpayCallbackReadiness(VerifiedPaymentHandler durableHandler) {
    return new CallbackReadiness(durableHandler);
  }

  record CallbackReadiness(VerifiedPaymentHandler durableHandler) {}
}
