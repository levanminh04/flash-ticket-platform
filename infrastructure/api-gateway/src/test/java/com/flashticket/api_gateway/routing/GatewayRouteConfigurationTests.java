package com.flashticket.api_gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest
class GatewayRouteConfigurationTests {

  @Autowired
  @Qualifier("customRouteLocator")
  private RouteLocator routeLocator;

  @Autowired private RedisRateLimiter redisRateLimiter;

  @Test
  void mapsOnlyApprovedBootstrapRoutesToTheFiveServiceIds() {
    Map<String, Route> routes = routesById();

    assertEquals(23, routes.size());
    assertEquals("lb://booking-service", routes.get("booking-availability").getUri().toString());
    assertEquals("lb://ticket-service", routes.get("ticket-check-in").getUri().toString());
    assertEquals("lb://event-service", routes.get("event-public-detail").getUri().toString());
    assertEquals("lb://event-service", routes.get("event-organizer-seat-map").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-create-order").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-promote-order").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-cancel-order").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-read-order").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-start-payment").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-list-orders").getUri().toString());
    assertEquals("lb://payment-service", routes.get("payment-read-order").getUri().toString());
    assertEquals("lb://ticket-service", routes.get("ticket-order-tickets").getUri().toString());
    assertEquals("lb://ticket-service", routes.get("ticket-qr").getUri().toString());
    assertEquals("lb://ticket-service", routes.get("ticket-delivery").getUri().toString());
    assertEquals("lb://user-service", routes.get("user-current-user").getUri().toString());
    assertTrue(
        routes.values().stream()
            .noneMatch(route -> route.getUri().toString().contains("CORE-SERVICE")));
    assertTrue(
        routes.values().stream()
            .noneMatch(route -> route.getUri().toString().contains("DISCOVERY-SERVICE")));
  }

  @Test
  void evaluatesNestedEventPathsBeforeTheGenericEventDetailRoute() {
    Map<String, Route> routes = routesById();

    assertTrue(
        routes.get("booking-availability").getOrder()
            < routes.get("event-public-detail").getOrder());
    assertTrue(
        routes.get("ticket-check-in").getOrder() < routes.get("event-public-detail").getOrder());
  }

  @Test
  void registersTheRedisRateLimiterBean() {
    assertNotNull(redisRateLimiter);
  }

  @Test
  void routesEventEditorCancellationAndStatisticsWithoutTakingBookingOrTicketPaths() {
    String[][] routes = {
      {"GET", "/api/v1/events"},
      {"GET", "/api/v1/categories"},
      {"GET", "/api/v1/venues"},
      {"POST", "/api/v1/organizer/events"},
      {"GET", "/api/v1/organizer/events"},
      {"GET", "/api/v1/organizer/events/event-1"},
      {"PUT", "/api/v1/organizer/events/event-1"},
      {"POST", "/api/v1/organizer/events/event-1/submit"},
      {"POST", "/api/v1/organizer/events/event-1/publish"},
      {"POST", "/api/v1/organizer/events/event-1/sales-mode"},
      {"PUT", "/api/v1/organizer/events/event-1/ticket-types"},
      {"POST", "/api/v1/organizer/events/event-1/cancel"},
      {"POST", "/api/v1/organizer/events/event-1/cancellation-requests"},
      {"GET", "/api/v1/organizer/events/event-1/cancellation-requests"},
      {"GET", "/api/v1/organizer/events/event-1/statistics"},
      {"GET", "/api/v1/admin/events"},
      {"GET", "/api/v1/admin/events/event-1"},
      {"POST", "/api/v1/admin/events/event-1/approve"},
      {"POST", "/api/v1/admin/events/event-1/return-to-draft"},
      {"POST", "/api/v1/admin/events/event-1/cancel"},
      {"GET", "/api/v1/admin/cancellation-requests"},
      {"GET", "/api/v1/admin/events/event-1/cancellation-requests"},
      {"POST", "/api/v1/admin/cancellation-requests/request-1/approve"},
      {"POST", "/api/v1/admin/cancellation-requests/request-1/reject"}
    };
    for (var endpoint : routes)
      assertEquals(
          "lb://event-service", target(endpoint[0], endpoint[1]), endpoint[0] + " " + endpoint[1]);
    assertEquals("lb://booking-service", target("GET", "/api/v1/events/event-1/availability"));
    assertEquals("lb://ticket-service", target("POST", "/api/v1/events/event-1/check-ins"));
    assertEquals(null, target("POST", "/api/v1/events/event-1/unapproved-operation"));
    assertEquals(null, target("GET", "/internal/v1/events/event-1/financial-summary"));
  }

  private String target(String method, String path) {
    var exchange =
        org.springframework.mock.web.server.MockServerWebExchange.from(
            org.springframework.mock.http.server.reactive.MockServerHttpRequest.method(
                    org.springframework.http.HttpMethod.valueOf(method), path)
                .build());
    var matches =
        routeLocator
            .getRoutes()
            .filterWhen(
                route -> reactor.core.publisher.Mono.from(route.getPredicate().apply(exchange)))
            .collectList()
            .block(Duration.ofSeconds(5));
    assertNotNull(matches);
    if (matches.isEmpty()) return null;
    assertEquals(1, matches.size(), "Ambiguous route " + path);
    return matches.getFirst().getUri().toString();
  }

  private Map<String, Route> routesById() {
    List<Route> routes = routeLocator.getRoutes().collectList().block(Duration.ofSeconds(5));
    assertNotNull(routes);
    return routes.stream().collect(Collectors.toMap(Route::getId, Function.identity()));
  }
}
