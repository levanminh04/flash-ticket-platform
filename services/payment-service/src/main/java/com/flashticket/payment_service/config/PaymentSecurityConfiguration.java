package com.flashticket.payment_service.config;

import com.flashticket.payment_service.security.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PaymentJwtProperties.class)
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
