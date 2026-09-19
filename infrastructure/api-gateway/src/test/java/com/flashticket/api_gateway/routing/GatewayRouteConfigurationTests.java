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

    assertEquals(14, routes.size());
    assertEquals("lb://booking-service", routes.get("booking-availability").getUri().toString());
    assertEquals("lb://ticket-service", routes.get("ticket-check-in").getUri().toString());
    assertEquals("lb://event-service", routes.get("event-public-detail").getUri().toString());
    assertEquals("lb://event-service", routes.get("event-organizer-seat-map").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-create-order").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-promote-order").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-cancel-order").getUri().toString());
    assertEquals("lb://booking-service", routes.get("booking-read-order").getUri().toString());
    assertEquals("lb://payment-service", routes.get("payment-create-attempt").getUri().toString());
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

  private Map<String, Route> routesById() {
    List<Route> routes = routeLocator.getRoutes().collectList().block(Duration.ofSeconds(5));
    assertNotNull(routes);
    return routes.stream().collect(Collectors.toMap(Route::getId, Function.identity()));
  }
}
