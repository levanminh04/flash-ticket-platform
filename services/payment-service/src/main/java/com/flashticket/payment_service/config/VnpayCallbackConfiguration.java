package com.flashticket.payment_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Exact provider routes have their own authentication scheme; buyer JWT chain stays unchanged. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "flashticket.payment.vnpay",
    name = "callbacks-enabled",
    havingValue = "true")
public class VnpayCallbackConfiguration {
  @Bean
  @Order(0)
  SecurityFilterChain vnpayCallbackSecurity(HttpSecurity http) throws Exception {
    return http.securityMatcher("/api/v1/payments/vnpay/ipn", "/api/v1/payments/vnpay/return")
        .csrf(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .requestCache(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/payments/vnpay/ipn",
                        "/api/v1/payments/vnpay/return")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .build();
  }
}
