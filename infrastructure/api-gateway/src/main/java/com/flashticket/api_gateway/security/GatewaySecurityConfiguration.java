package com.flashticket.api_gateway.security;

import com.flashticket.api_gateway.web.GatewayErrorWriter;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

/**
 * Vì sao phải dùng @EnableWebFluxSecurity? Vì: MVC security dùng Filter,
 * SecurityFilterChain, @EnableWebSecurity WebFlux security dùng WebFilter,
 * SecurityWebFilterChain, @EnableWebFluxSecurity Gateway chạy trên WebFlux → nên security cũng phải
 * reactive. cố tình thay thế bằng MVC Security, sẽ bị lỗi nghiêm trọng và ứng dụng API Gateway sẽ
 * không thể chạy được.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewayCorsProperties.class)
public class GatewaySecurityConfiguration {

  @Bean
  SecurityWebFilterChain springSecurityFilterChain(
      ServerHttpSecurity http,
      Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakJwtAuthenticationConverter,
      ServerAuthenticationEntryPoint problemAuthenticationEntryPoint,
      ServerAccessDeniedHandler problemAccessDeniedHandler) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(Customizer.withDefaults())
        .authorizeExchange(
            exchanges ->
                exchanges
                    // Công khai: preflight, callback VNPay và các GET đọc công khai.
                    .pathMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .pathMatchers(
                        HttpMethod.GET,
                        "/api/v1/payments/vnpay/return",
                        "/api/v1/payments/vnpay/ipn",
                        "/api/v1/events",
                        "/api/v1/events/*",
                        "/api/v1/events/*/availability",
                        "/api/v1/categories",
                        "/api/v1/venues",
                        "/api/v1/organizers/*")
                    .permitAll()
                    // Không bao giờ lộ ra ngoài.
                    .pathMatchers("/internal/**", "/actuator/**")
                    .denyAll()
                    // Phân quyền thô theo khu vực; quyền chi tiết do từng service kiểm.
                    .pathMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .pathMatchers("/api/v1/organizer/**")
                    .hasRole("ORGANIZER")
                    .anyExchange()
                    .authenticated())
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

  @Bean
  ServerAuthenticationEntryPoint problemAuthenticationEntryPoint(GatewayErrorWriter errors) {
    return (exchange, error) -> {
      exchange
          .getResponse()
          .getHeaders()
          .set(
              HttpHeaders.WWW_AUTHENTICATE,
              error instanceof OAuth2AuthenticationException
                  ? "Bearer error=\"invalid_token\""
                  : "Bearer");
      return errors.write(exchange, HttpStatus.UNAUTHORIZED, error);
    };
  }

  @Bean
  ServerAccessDeniedHandler problemAccessDeniedHandler(GatewayErrorWriter errors) {
    return (exchange, error) -> {
      exchange
          .getResponse()
          .getHeaders()
          .set(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
      return errors.write(exchange, HttpStatus.FORBIDDEN, error);
    };
  }

  @Bean
  Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakJwtAuthenticationConverter(
      KeycloakRealmRoleConverter keycloakRealmRoleConverter) {
    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(keycloakRealmRoleConverter);
    return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
  }

  @Bean
  KeycloakRealmRoleConverter keycloakRealmRoleConverter() {
    return new KeycloakRealmRoleConverter();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(GatewayCorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Correlation-Id",
            "traceparent",
            "tracestate",
            "baggage"));
    configuration.setExposedHeaders(List.of("X-Correlation-Id", "X-Request-Id"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
