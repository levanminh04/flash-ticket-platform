package com.flashticket.booking_service.config;

import com.flashticket.booking_service.exception.ErrorClassifier;
import com.flashticket.booking_service.security.KeycloakRealmRoleConverter;
import com.flashticket.booking_service.web.error.ProblemResponses;
import jakarta.servlet.DispatcherType;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

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
                    .requestMatchers(HttpMethod.GET, "/api/v1/events/*/availability")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/orders/**")
                    .hasRole("BUYER")
                    .requestMatchers("/internal/v1/**")
                    .hasRole("INTERNAL_SERVICE")
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
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
  AuthenticationEntryPoint problemAuthenticationEntryPoint(ProblemResponses responses) {
    return (request, response, exception) -> {
      response.setHeader(
          HttpHeaders.WWW_AUTHENTICATE,
          exception instanceof OAuth2AuthenticationException
              ? "Bearer error=\"invalid_token\""
              : "Bearer");
      responses.write(
          request,
          response,
          responses.response(
              request,
              HttpStatus.UNAUTHORIZED,
              ErrorClassifier.of("UNAUTHENTICATED", exception),
              List.of()));
    };
  }

  @Bean
  AccessDeniedHandler problemAccessDeniedHandler(ProblemResponses responses) {
    return (request, response, exception) -> {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"");
      responses.write(
          request,
          response,
          responses.response(
              request,
              HttpStatus.FORBIDDEN,
              ErrorClassifier.of("ACCESS_DENIED", exception),
              List.of()));
    };
  }

  @Bean
  JwtAuthenticationConverter keycloakJwtAuthenticationConverter(
      KeycloakRealmRoleConverter keycloakRealmRoleConverter) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(keycloakRealmRoleConverter);
    return converter;
  }

  @Bean
  KeycloakRealmRoleConverter keycloakRealmRoleConverter() {
    return new KeycloakRealmRoleConverter();
  }
}
