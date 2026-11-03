package com.flashticket.payment_service.config;

import com.flashticket.payment_service.security.*;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentJwtProperties.class)
@EnableMethodSecurity
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class PaymentSecurityConfiguration {
  @Bean
  JwtDecoder paymentJwtDecoder(PaymentJwtProperties properties) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
    decoder.setJwtValidator(PaymentJwtValidators.create(properties));
    return decoder;
  }

  @Bean
  SecurityFilterChain paymentSecurityFilterChain(
      HttpSecurity http, PaymentSecurityProblemHandler problems) throws Exception {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
    return http.csrf(c -> c.disable())
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            c ->
                c.requestMatchers(HttpMethod.GET, "/api/v1/orders/*/payment")
                    .hasRole("BUYER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/payments/vnpay/return",
                        "/api/v1/payments/vnpay/ipn")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/payments")
                    .hasRole("BUYER")
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/admin/refunds",
                        "/api/v1/admin/events/*/refund-progress")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/admin/refunds/*/retry",
                        "/api/v1/admin/refunds/*/manual-settlement")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/internal/v1/events/*/financial-summary")
                    .hasRole("INTERNAL_SERVICE")
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/reconciliation")
                    .hasAnyRole("ADMIN", "ORGANIZER")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/admin/events/*/reconciliation",
                        "/api/v1/admin/events/*/payout")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(c -> c.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .oauth2ResourceServer(
            c ->
                c.authenticationEntryPoint(problems)
                    .accessDeniedHandler(problems)
                    .jwt(j -> j.jwtAuthenticationConverter(converter)))
        .build();
  }
}
