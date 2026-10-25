package com.flashticket.api_gateway.routing;

import com.flashticket.api_gateway.security.VnpayCallbackProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/** Provider callbacks deliberately do not inherit buyer GET retry/fallback filters. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VnpayCallbackProperties.class)
public class VnpayCallbackRouteConfiguration {
  private static final String IPN = "/api/v1/payments/vnpay/ipn";
  private static final String RETURN = "/api/v1/payments/vnpay/return";

  @Bean
  RouteLocator vnpayCallbackRoutes(
      RouteLocatorBuilder builder, VnpayCallbackProperties properties) {
    var routes = builder.routes();
    if (properties.vnpayCallbacksEnabled()) {
      routes.route(
          "vnpay-ipn", r -> r.path(IPN).and().method(HttpMethod.GET).uri("lb://payment-service"));
      routes.route(
          "vnpay-return",
          r -> r.path(RETURN).and().method(HttpMethod.GET).uri("lb://payment-service"));
    }
    return routes.build();
  }

  @Bean
  @Order(0)
  @ConditionalOnProperty(
      prefix = "flashticket.gateway",
      name = "vnpay-callbacks-enabled",
      havingValue = "true")
  SecurityWebFilterChain vnpayCallbackSecurity(ServerHttpSecurity http) {
    return http.securityMatcher(ServerWebExchangeMatchers.pathMatchers(IPN, RETURN))
        .csrf(c -> c.disable())
        .httpBasic(c -> c.disable())
        .formLogin(c -> c.disable())
        .requestCache(c -> c.disable())
        .authorizeExchange(
            c -> c.pathMatchers(HttpMethod.GET, IPN, RETURN).permitAll().anyExchange().denyAll())
        .build();
  }
}
