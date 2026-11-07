package com.flashticket.ticket_service.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class TicketSecurityConfiguration {
  @Bean
  SecurityFilterChain ticketSecurityFilterChain(HttpSecurity http, JsonMapper json)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET, "/api/v1/orders/*/tickets", "/api/v1/tickets/*/qr")
                    .hasRole("BUYER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/ticket-deliveries")
                    .hasRole("BUYER")
                    .requestMatchers(HttpMethod.POST, "/api/v1/events/*/check-ins")
                    .hasRole("ORGANIZER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/internal/v1/orders/*/issuance",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info")
                    .hasRole("INTERNAL_SERVICE")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                          writeProblem(response, json, 401, "UNAUTHENTICATED");
                        })
                    .accessDeniedHandler(
                        (request, response, error) ->
                            writeProblem(response, json, 403, "ACCESS_DENIED")))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setHeader(
                              HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
                          writeProblem(response, json, 401, "UNAUTHENTICATED");
                        })
                    .accessDeniedHandler(
                        (request, response, error) ->
                            writeProblem(response, json, 403, "ACCESS_DENIED"))
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(ticketJwtAuthenticationConverter())))
        .build();
  }

  @Bean
  JwtAuthenticationConverter ticketJwtAuthenticationConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String subject = jwt.getSubject();
          if (subject == null || subject.isBlank()) return List.of();
          if (!(jwt.getClaim("realm_access") instanceof Map<?, ?> realm)
              || !(realm.get("roles") instanceof Collection<?> roles)
              || roles.stream().anyMatch(role -> !(role instanceof String))) return List.of();
          return roles.stream()
              .map(String.class::cast)
              .filter(Set.of("BUYER", "ORGANIZER", "INTERNAL_SERVICE")::contains)
              .distinct()
              .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
              .toList();
        });
    return converter;
  }

  private static void writeProblem(
      HttpServletResponse response, JsonMapper json, int status, String code) throws IOException {
    UUID correlationId = UUID.randomUUID();
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setHeader("X-Correlation-Id", correlationId.toString());
    response
        .getOutputStream()
        .write(
            json.writeValueAsBytes(
                Map.of(
                    "code",
                    code,
                    "message",
                    code.replace('_', ' '),
                    "correlationId",
                    correlationId,
                    "violations",
                    List.of())));
  }
}
