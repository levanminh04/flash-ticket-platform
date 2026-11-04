package com.flashticket.ticket_service.config;

import com.flashticket.ticket_service.security.KeycloakRealmRoleConverter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Cấu hình xác thực JWT và quyền truy cập API của Ticket. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  /** Áp dụng JWT stateless, phân quyền từng nhóm API và nối xử lý lỗi Security. */
  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter keycloakJwtAuthenticationConverter,
      AuthenticationEntryPoint problemAuthenticationEntryPoint,
      AccessDeniedHandler problemAccessDeniedHandler)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/orders/*/tickets", "/api/v1/tickets/*/qr")
                    .hasRole("BUYER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/ticket-deliveries")
                    .hasRole("BUYER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/check-ins")
                    .hasRole("ORGANIZER")
                    .requestMatchers(HttpMethod.GET, "/internal/v1/orders/*/issuance")
                    .hasRole("INTERNAL_SERVICE")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(problemAuthenticationEntryPoint)
                    .accessDeniedHandler(problemAccessDeniedHandler))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(problemAuthenticationEntryPoint)
                    .accessDeniedHandler(problemAccessDeniedHandler)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
        .build();
  }

  /** Chuyển lỗi thiếu hoặc sai JWT vào global handler để trả 401 kèm Bearer challenge. */
  @Bean
  AuthenticationEntryPoint problemAuthenticationEntryPoint(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    return (request, response, exception) -> {
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE,
          exception instanceof OAuth2AuthenticationException
              ? "Bearer error=\"invalid_token\""
              : "Bearer");
      resolver.resolveException(request, response, null, exception);
    };
  }

  /** Chuyển lỗi đã xác thực nhưng thiếu quyền vào global handler để trả 403. */
  @Bean
  AccessDeniedHandler problemAccessDeniedHandler(
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    return (request, response, exception) -> {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
      resolver.resolveException(request, response, null, exception);
    };
  }

  /** Gắn bộ đọc role Keycloak khi chuyển JWT thành Authentication của Spring. */
  @Bean
  JwtAuthenticationConverter keycloakJwtAuthenticationConverter(
      KeycloakRealmRoleConverter keycloakRealmRoleConverter) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(keycloakRealmRoleConverter);
    return converter;
  }

  /** Đăng ký bộ chuyển realm role để dùng trong quá trình xác thực JWT. */
  @Bean
  KeycloakRealmRoleConverter keycloakRealmRoleConverter() {
    return new KeycloakRealmRoleConverter();
  }
}
