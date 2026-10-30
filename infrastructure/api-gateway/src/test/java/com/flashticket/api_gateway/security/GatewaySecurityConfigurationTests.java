package com.flashticket.api_gateway.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

@SpringBootTest
class GatewaySecurityConfigurationTests {

  @Autowired private SecurityWebFilterChain springSecurityFilterChain;

  @Test
  void permitsOnlyTheExplicitPublicEventReadPathsWithoutAuthentication() {
    assertTrue(reachesDownstream(HttpMethod.GET, "/api/v1/events/event-1", null));
    assertTrue(reachesDownstream(HttpMethod.GET, "/api/v1/events/event-1/availability", null));
    assertFalse(reachesDownstream(HttpMethod.POST, "/api/v1/orders", null));
  }

  @Test
  void deniesInternalPathsAtThePublicGateway() {
    assertFalse(reachesDownstream(HttpMethod.GET, "/internal/v1/orders/order-1", null));
  }

  @Test
  void protectsEventWritesAndAdminReviewsWhileAllowingPublicCatalog() {
    var buyer = authenticatedAs("ROLE_BUYER");
    var organizer = authenticatedAs("ROLE_ORGANIZER");
    var admin = authenticatedAs("ROLE_ADMIN");
    for (String path : List.of("/api/v1/events", "/api/v1/categories", "/api/v1/venues"))
      assertTrue(reachesDownstream(HttpMethod.GET, path, null));
    for (String path :
        List.of("/api/v1/organizer/events", "/api/v1/organizer/events/event-1/cancel")) {
      assertFalse(reachesDownstream(HttpMethod.POST, path, buyer));
      assertTrue(reachesDownstream(HttpMethod.POST, path, organizer));
      assertFalse(reachesDownstream(HttpMethod.POST, path, null));
    }
    assertFalse(
        reachesDownstream(HttpMethod.GET, "/api/v1/organizer/events/event-1/statistics", buyer));
    assertTrue(
        reachesDownstream(
            HttpMethod.GET, "/api/v1/organizer/events/event-1/statistics", organizer));
    for (String path :
        List.of(
            "/api/v1/admin/events/event-1/approve",
            "/api/v1/admin/cancellation-requests/request-1/reject")) {
      assertFalse(reachesDownstream(HttpMethod.POST, path, organizer));
      assertFalse(reachesDownstream(HttpMethod.POST, path, buyer));
      assertTrue(reachesDownstream(HttpMethod.POST, path, admin));
    }
  }

  @Test
  void appliesApprovedCoarseRoleChecks() {
    Authentication buyer = authenticatedAs("ROLE_BUYER");
    Authentication organizer = authenticatedAs("ROLE_ORGANIZER");

    assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/orders", buyer));
    assertFalse(reachesDownstream(HttpMethod.POST, "/api/v1/orders", organizer));
    assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/events/event-1/check-ins", organizer));
  }

  private boolean reachesDownstream(HttpMethod method, String path, Authentication authentication) {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
    AtomicBoolean reachedDownstream = new AtomicBoolean(false);
    List<WebFilter> filters = springSecurityFilterChain.getWebFilters().collectList().block();
    Mono<Void> result =
        new DefaultWebFilterChain(
                downstream -> {
                  reachedDownstream.set(true);
                  return Mono.empty();
                },
                filters)
            .filter(exchange);

    if (authentication == null) {
      result.block();
    } else {
      result.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();
    }

    return reachedDownstream.get();
  }

  @Test
  void publicOrganizerAndProtectedUserWorkflowsUseTheirOwnPolicies() {
    assertTrue(reachesDownstream(HttpMethod.GET, "/api/v1/organizers/subject-1", null));
    assertFalse(reachesDownstream(HttpMethod.GET, "/api/v1/organizers/subject-1/follow", null));
    assertTrue(
        reachesDownstream(
            HttpMethod.PUT, "/api/v1/organizers/subject-1/follow", authenticatedAs("ROLE_BUYER")));
    assertFalse(
        reachesDownstream(
            HttpMethod.PUT,
            "/api/v1/organizers/subject-1/follow",
            authenticatedAs("ROLE_INTERNAL_SERVICE")));
    assertFalse(
        reachesDownstream(
            HttpMethod.POST,
            "/api/v1/admin/organizer-applications/app/approve",
            authenticatedAs("ROLE_BUYER")));
    assertTrue(
        reachesDownstream(
            HttpMethod.POST,
            "/api/v1/admin/organizer-applications/app/approve",
            authenticatedAs("ROLE_ADMIN")));
    assertFalse(
        reachesDownstream(
            HttpMethod.POST, "/api/v1/organizers/me/logo", authenticatedAs("ROLE_BUYER")));
    assertTrue(
        reachesDownstream(
            HttpMethod.POST, "/api/v1/organizers/me/logo", authenticatedAs("ROLE_ORGANIZER")));
  }

  private Authentication authenticatedAs(String authority) {
    return UsernamePasswordAuthenticationToken.authenticated(
        "subject-1", "N/A", List.of(new SimpleGrantedAuthority(authority)));
  }
}
