package com.flashticket.event_service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;

import com.flashticket.event_service.testing.RcaFaults;
import com.flashticket.event_service.testing.RcaFaultsWebConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

class RcaFaultsWebConfigTests {
  private final RcaFaults faults = mock(RcaFaults.class);

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private HandlerInterceptor interceptor() {
    InterceptorRegistry registry = new InterceptorRegistry();
    new RcaFaultsWebConfig(faults, new MockEnvironment()).addInterceptors(registry);
    return new InterceptorRegistryAccess(registry).first();
  }

  private static MockHttpServletRequest request(String method, String pattern, UUID id) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/x");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("id", id.toString()));
    return request;
  }

  @Test
  void configuredRouteCallsFaultsWithPathIdWithoutSubject() throws Exception {
    UUID id = UUID.randomUUID();
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("buyer-1").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    interceptor()
        .preHandle(
            request("GET", "/api/v1/events/{id}", id), new MockHttpServletResponse(), null);
    verify(faults).http(id, "");
  }

  @Test
  void otherRouteOrMethodNeverCallsFaults() throws Exception {
    UUID id = UUID.randomUUID();
    interceptor()
        .preHandle(request("GET", "/api/v1/events", id), new MockHttpServletResponse(), null);
    interceptor()
        .preHandle(
            request("POST", "/api/v1/events/{id}", id), new MockHttpServletResponse(), null);
    verify(faults, never()).http(any(), any());
    verifyNoInteractions(faults);
  }

  /** Reads the registered interceptor through the public registry API used by Spring MVC. */
  private record InterceptorRegistryAccess(InterceptorRegistry registry) {
    HandlerInterceptor first() {
      try {
        var method = InterceptorRegistry.class.getDeclaredMethod("getInterceptors");
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) method.invoke(registry);
        Object first = list.get(0);
        if (first instanceof HandlerInterceptor h) return h;
        var field = first.getClass().getDeclaredField("interceptor");
        field.setAccessible(true);
        return (HandlerInterceptor) field.get(first);
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
