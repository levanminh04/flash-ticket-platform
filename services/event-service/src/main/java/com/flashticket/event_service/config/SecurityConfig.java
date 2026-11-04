package com.flashticket.event_service.config;

import com.flashticket.event_service.web.ProblemResponses;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemResponses problems)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/events",
                        "/api/v1/events/*",
                        "/api/v1/categories",
                        "/api/v1/venues")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/organizer/**")
                    .hasRole("ORGANIZER")
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .hasRole("INTERNAL_SERVICE")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                          problems.write(request, response, 401, "UNAUTHENTICATED");
                        })
                    .accessDeniedHandler(
                        (request, response, error) ->
                            problems.write(request, response, 403, "ACCESS_DENIED")))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setHeader(
                              HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
                          problems.write(request, response, 401, "UNAUTHENTICATED");
                        })
                    .accessDeniedHandler(
                        (request, response, error) ->
                            problems.write(request, response, 403, "ACCESS_DENIED"))
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          String subject = jwt.getSubject();
          if (subject == null || subject.isBlank()) return List.of();
          Object realm = jwt.getClaims().get("realm_access");
          if (!(realm instanceof java.util.Map<?, ?> map)
              || !(map.get("roles") instanceof Collection<?> roles)) return List.of();
          if (roles.stream().anyMatch(role -> !(role instanceof String))) return List.of();
          return roles.stream()
              .filter(String.class::isInstance)
              .map(String.class::cast)
              .filter(Set.of("BUYER", "ORGANIZER", "ADMIN", "INTERNAL_SERVICE")::contains)
              .distinct()
              .map(
                  role ->
                      (org.springframework.security.core.GrantedAuthority)
                          new SimpleGrantedAuthority("ROLE_" + role))
              .toList();
        });
    return converter;
  }
}
