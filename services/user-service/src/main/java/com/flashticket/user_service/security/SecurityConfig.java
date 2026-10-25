package com.flashticket.user_service.security;

import com.flashticket.user_service.web.Problems;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
  @Bean
  SecurityFilterChain security(HttpSecurity http, Problems problems) throws Exception {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new RealmRoles());
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/organizers/*")
                    .permitAll()
                    .requestMatchers("/internal/v1/**")
                    .hasRole("INTERNAL_SERVICE")
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/me")
                    .hasAnyRole("BUYER", "ORGANIZER", "ADMIN")
                    .requestMatchers(
                        "/api/v1/organizer-applications/**",
                        "/api/v1/organizer-applications",
                        "/api/v1/organizers/*/follow")
                    .hasRole("BUYER")
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (r, s, ex) ->
                            problems.write(r, s, 401, "UNAUTHENTICATED", "Authentication required"))
                    .accessDeniedHandler(
                        (r, s, ex) -> problems.write(r, s, 403, "ACCESS_DENIED", "Access denied")))
        .oauth2ResourceServer(
            o ->
                o.authenticationEntryPoint(
                        (r, s, ex) ->
                            problems.write(r, s, 401, "UNAUTHENTICATED", "Authentication required"))
                    .accessDeniedHandler(
                        (r, s, ex) -> problems.write(r, s, 403, "ACCESS_DENIED", "Access denied"))
                    .jwt(j -> j.jwtAuthenticationConverter(converter)))
        .build();
  }
}
