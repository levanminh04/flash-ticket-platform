package com.flashticket.booking_service.testing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Test-only (profile Maven rca-test): applies RcaFaults at one HTTP route, after authentication and
 * before the controller. Business code is not modified; the route is configuration
 * (ft.rca.http.*), so business changes inside the controller do not affect it.
 */
@Configuration(proxyBeanMethods = false)
public class RcaFaultsWebConfig implements WebMvcConfigurer {
  private final RcaFaults faults;
  private final String method;
  private final String pattern;
  private final String variable;
  private final boolean subject;

  public RcaFaultsWebConfig(RcaFaults faults, Environment env) {
    this.faults = faults;
    this.method = env.getProperty("ft.rca.http.method", "GET");
    this.pattern = env.getProperty("ft.rca.http.pattern", "/api/v1/orders/{orderId}");
    this.variable = env.getProperty("ft.rca.http.variable", "orderId");
    this.subject = env.getProperty("ft.rca.http.subject", Boolean.class, true);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(
        new HandlerInterceptor() {
          @Override
          public boolean preHandle(
              HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (!method.equals(request.getMethod())) return true;
            Object best = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            if (best == null || !pattern.equals(best.toString())) return true;
            UUID target = target(request);
            if (target == null) return true;
            faults.http(target, subject ? currentSubject() : "");
            return true;
          }
        });
  }

  private UUID target(HttpServletRequest request) {
    Object vars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (!(vars instanceof Map<?, ?> map) || !(map.get(variable) instanceof String raw)) return null;
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private static String currentSubject() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth instanceof JwtAuthenticationToken jwt ? jwt.getToken().getSubject() : "";
  }
}
