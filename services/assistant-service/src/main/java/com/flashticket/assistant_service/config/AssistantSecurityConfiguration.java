package com.flashticket.assistant_service.config;

import com.flashticket.assistant_service.exception.AssistantExceptionHandler;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class AssistantSecurityConfiguration {
  private final AssistantExceptionHandler errors;

  @Bean
  public SecurityFilterChain assistantSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/actuator/health/liveness", "/actuator/health/readiness").permitAll()
            .requestMatchers("/internal/**", "/actuator/**").denyAll()
            .requestMatchers("/api/v1/assistant/**").hasRole("BUYER")
            .anyRequest().denyAll())
        .exceptionHandling(config -> config.authenticationEntryPoint(errors::handleUnauthenticated)
            .accessDeniedHandler(errors::handleAccessDenied))
        .oauth2ResourceServer(oauth -> oauth
            .authenticationEntryPoint(errors::handleUnauthenticated)
            .accessDeniedHandler(errors::handleAccessDenied)
            .jwt(jwt -> jwt.jwtAuthenticationConverter(assistantJwtAuthenticationConverter())))
        .build();
  }

  @Bean
  public JwtAuthenticationConverter assistantJwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(this::realmAuthorities);
    return converter;
  }

  private Collection<GrantedAuthority> realmAuthorities(Jwt jwt) {
    Object access = jwt.getClaims().get("realm_access");
    if (!(access instanceof Map<?, ?> realm) || !(realm.get("roles") instanceof List<?> roles)) {
      return List.of();
    }
    return roles.stream().filter(String.class::isInstance).map(String.class::cast)
        .filter(role -> !role.isBlank())
        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role)).toList();
  }
}
