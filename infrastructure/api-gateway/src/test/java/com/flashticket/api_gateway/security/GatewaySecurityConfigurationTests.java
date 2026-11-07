package com.flashticket.api_gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

@ActiveProfiles("test")
@SpringBootTest
class GatewaySecurityConfigurationTests {
  @Test
  void permitsAuthenticatedImagePatchPreflightFromConfiguredOrigin() {
    var source =
        new GatewaySecurityConfiguration()
            .corsConfigurationSource(new GatewayCorsProperties(List.of("https://frontend.test")));
    var exchange =
        org.springframework.mock.web.server.MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.options(
                    "https://api.test/api/v1/organizer/events/event/images/image")
                .header("Origin", "https://frontend.test")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build());
    var filter = new org.springframework.web.cors.reactive.CorsWebFilter(source);
    filter.filter(exchange, ignored -> reactor.core.publisher.Mono.empty()).block();
    assertEquals(
        org.springframework.http.HttpStatus.OK,
        exchange.getResponse().getStatusCode() == null
            ? org.springframework.http.HttpStatus.OK
            : exchange.getResponse().getStatusCode());
    assertTrue(
        exchange
            .getResponse()
            .getHeaders()
            .getAccessControlAllowMethods()
            .contains(HttpMethod.PATCH));
  }

  @Autowired private SecurityWebFilterChain springSecurityFilterChain;

  @Test
  void publicEndpointsNeedNoToken() {
    for (String path :
        List.of(
            "/api/v1/events",
            "/api/v1/events/event-1",
            "/api/v1/events/event-1/availability",
            "/api/v1/categories",
            "/api/v1/venues",
            "/api/v1/organizers/subject-1",
            "/api/v1/payments/vnpay/return",
            "/api/v1/payments/vnpay/ipn")) {
      assertTrue(reachesDownstream(HttpMethod.GET, path, null), path);
    }
    assertTrue(reachesDownstream(HttpMethod.OPTIONS, "/api/v1/admin/refunds", null));
  }

  @Test
  void everythingElseNeedsAToken() {
    for (String[] endpoint :
        List.of(
            new String[] {"GET", "/api/v1/events/event-1/reconciliation"},
            new String[] {"POST", "/api/v1/orders"},
            new String[] {"GET", "/api/v1/me"},
            new String[] {"POST", "/api/v1/payments"},
            new String[] {"GET", "/api/v1/tickets/ticket-1/qr"},
            new String[] {"GET", "/api/v1/organizers/subject-1/follow"},
            new String[] {"POST", "/api/v1/payments/vnpay/ipn"},
            new String[] {"GET", "/api/v1/payments/vnpay/other"},
            new String[] {"POST", "/api/v1/events"})) {
      assertFalse(
          reachesDownstream(HttpMethod.valueOf(endpoint[0]), endpoint[1], null),
          endpoint[0] + " " + endpoint[1]);
    }
  }

  @Test
  void adminAreaNeedsAdmin() {
    for (String path :
        List.of(
            "/api/v1/admin/events",
            "/api/v1/admin/refunds",
            "/api/v1/admin/events/event-1/payout",
            "/api/v1/admin/organizer-applications")) {
      assertFalse(reachesDownstream(HttpMethod.GET, path, authenticatedAs("ROLE_BUYER")), path);
      assertFalse(reachesDownstream(HttpMethod.GET, path, authenticatedAs("ROLE_ORGANIZER")), path);
      assertTrue(reachesDownstream(HttpMethod.GET, path, authenticatedAs("ROLE_ADMIN")), path);
    }
  }

  @Test
  void organizerAreaNeedsOrganizer() {
    for (String path :
        List.of("/api/v1/organizer/events", "/api/v1/organizer/events/event-1/images/image-1")) {
      assertFalse(reachesDownstream(HttpMethod.GET, path, authenticatedAs("ROLE_BUYER")), path);
      assertFalse(reachesDownstream(HttpMethod.GET, path, authenticatedAs("ROLE_ADMIN")), path);
      assertTrue(reachesDownstream(HttpMethod.GET, path, authenticatedAs("ROLE_ORGANIZER")), path);
    }
  }

  @Test
  void fineGrainedRolesAreLeftToTheServices() {
    var organizer = authenticatedAs("ROLE_ORGANIZER");
    var buyer = authenticatedAs("ROLE_BUYER");
    assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/orders", organizer));
    assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/payments", organizer));
    assertTrue(reachesDownstream(HttpMethod.PUT, "/api/v1/organizers/subject-1/follow", organizer));
    assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/events/event-1/check-ins", buyer));
    assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/organizers/me/logo", buyer));
    assertTrue(reachesDownstream(HttpMethod.GET, "/api/v1/me", buyer));
  }

  @Test
  void internalAndActuatorPathsAreDeniedEvenToAdmin() {
    for (String path :
        List.of(
            "/internal/v1/orders/order-1",
            "/internal/v1/users/subject-1",
            "/actuator/health",
            "/actuator/env")) {
      assertFalse(reachesDownstream(HttpMethod.GET, path, null), path);
      assertFalse(reachesDownstream(HttpMethod.GET, path, authenticatedAs("ROLE_ADMIN")), path);
    }
  }

  @Test
  void fallbackPathHasNoSpecialRuleAnymore() {
    assertTrue(
        reachesDownstream(HttpMethod.GET, "/fallback/booking", authenticatedAs("ROLE_BUYER")));
    assertFalse(reachesDownstream(HttpMethod.GET, "/fallback/booking", null));
  }

  @Test
  void allowsBrowserPreflightOnPathsThatWereNotRoutedBefore() {
    var source =
        new GatewaySecurityConfiguration()
            .corsConfigurationSource(new GatewayCorsProperties(List.of("https://frontend.test")));
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.options("https://api.test/api/v1/admin/refunds/refund-1/retry")
                .header("Origin", "https://frontend.test")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build());
    new org.springframework.web.cors.reactive.CorsWebFilter(source)
        .filter(exchange, ignored -> Mono.empty())
        .block();
    assertEquals(
        "https://frontend.test", exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
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

  private Authentication authenticatedAs(String authority) {
    return UsernamePasswordAuthenticationToken.authenticated(
        "subject-1", "N/A", List.of(new SimpleGrantedAuthority(authority)));
  }
}
