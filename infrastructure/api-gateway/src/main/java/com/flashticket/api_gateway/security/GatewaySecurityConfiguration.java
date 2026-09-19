package com.flashticket.api_gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
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
      Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakJwtAuthenticationConverter) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .cors(Customizer.withDefaults())
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/events/*/availability")
                    .permitAll()
                    .pathMatchers(HttpMethod.GET, "/api/v1/events/*")
                    .permitAll()
                    .pathMatchers(HttpMethod.POST, "/api/v1/orders")
                    .hasRole("BUYER")
                    .pathMatchers(HttpMethod.GET, "/api/v1/organizer/events/*/seat-map")
                    .hasRole("ORGANIZER")
                    .pathMatchers(HttpMethod.PUT, "/api/v1/organizer/events/*/seat-map")
                    .hasRole("ORGANIZER")
                    .pathMatchers(HttpMethod.POST, "/api/v1/events/*/check-ins")
                    .hasRole("ORGANIZER")
                    .pathMatchers("/internal/v1/**")
                    .denyAll()
                    .pathMatchers("/actuator/**")
                    .denyAll()
                    .pathMatchers("/fallback/**")
                    .denyAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2.jwt(
                    jwt -> jwt.jwtAuthenticationConverter(keycloakJwtAuthenticationConverter)))
        .build();
  }

  /**
   * .oauth2ResourceServer(...): biến ứng dụng này thành một Resource Server, Tác dụng chính của nó
   * là chuyên tiếp nhận, kiểm tra và tự đông bắt và giải mã Access Token. tự động chui vào Header
   * của HTTP Request gửi đến Gateway để tìm kiếm chuỗi Authorization: Bearer <chuỗi_jwt> Từ khóa
   * oauth2 đầu tiên đại diện cho đối tượng cấu hình của OAuth2 Resource Server. .jwt(...) nói cho
   * Spring Security biết Ứng dụng này là OAuth2 Resource Server và sẽ xác thực request bằng JWT,
   * biến jwt ở đây đại diện cho đối tượng cấu hình chuyên biệt của JWT. "Sau khi nhận được JWT,
   * thay vì dùng cách đọc quyền (roles) mặc định của Spring, hãy dùng cái Converter mà tôi tự viết
   * (jwtAuthenticationConverter()) để lôi quyền từ trong mảng realm_access của Keycloak ra.
   */
  //                .oauth2ResourceServer(oauth2 -> oauth2
  //                        .jwt(cauhinhJwt -> cauhinhJwt.jwtAuthenticationConverter(
  // thietLapQuyenTuKeycloak() ))
  //                )

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
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            "Authorization",
            "Content-Type",
            "Idempotency-Key",
            "X-Correlation-Id",
            "traceparent",
            "tracestate",
            "baggage"));
    configuration.setExposedHeaders(List.of("X-Correlation-Id"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
