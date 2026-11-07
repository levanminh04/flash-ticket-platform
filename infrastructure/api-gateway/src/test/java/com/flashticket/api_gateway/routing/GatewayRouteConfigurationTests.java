package com.flashticket.api_gateway.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

@ActiveProfiles("test")
@SpringBootTest
class GatewayRouteConfigurationTests {

  @Autowired private RouteLocator routeLocator;
  @Autowired private RedisRateLimiter redisRateLimiter;
  @Autowired private CircuitBreakerRegistry breakers;
  @Autowired private TimeLimiterRegistry timeLimiters;

  /** Toàn bộ endpoint công khai (/api/v1/**) của 5 service, trích từ controller. */
  @ParameterizedTest(name = "{0} {1} -> {2}")
  @CsvSource({
    "GET, /api/v1/admin/cancellation-requests, lb://event-service",
    "POST, /api/v1/admin/cancellation-requests/id-1/approve, lb://event-service",
    "POST, /api/v1/admin/cancellation-requests/id-1/reject, lb://event-service",
    "GET, /api/v1/admin/events, lb://event-service",
    "GET, /api/v1/admin/events/id-1, lb://event-service",
    "POST, /api/v1/admin/events/id-1/approve, lb://event-service",
    "POST, /api/v1/admin/events/id-1/cancel, lb://event-service",
    "GET, /api/v1/admin/events/id-1/cancellation-requests, lb://event-service",
    "POST, /api/v1/admin/events/id-1/return-to-draft, lb://event-service",
    "GET, /api/v1/categories, lb://event-service",
    "GET, /api/v1/events, lb://event-service",
    "GET, /api/v1/events/id-1, lb://event-service",
    "GET, /api/v1/organizer/events, lb://event-service",
    "POST, /api/v1/organizer/events, lb://event-service",
    "GET, /api/v1/organizer/events/id-1, lb://event-service",
    "PUT, /api/v1/organizer/events/id-1, lb://event-service",
    "POST, /api/v1/organizer/events/id-1/cancel, lb://event-service",
    "POST, /api/v1/organizer/events/id-1/cancellation-requests, lb://event-service",
    "GET, /api/v1/organizer/events/id-1/cancellation-requests, lb://event-service",
    "GET, /api/v1/organizer/events/id-1/images, lb://event-service",
    "POST, /api/v1/organizer/events/id-1/images, lb://event-service",
    "PUT, /api/v1/organizer/events/id-1/images/image-1, lb://event-service",
    "PATCH, /api/v1/organizer/events/id-1/images/image-1, lb://event-service",
    "DELETE, /api/v1/organizer/events/id-1/images/image-1, lb://event-service",
    "POST, /api/v1/organizer/events/id-1/publish, lb://event-service",
    "POST, /api/v1/organizer/events/id-1/sales-mode, lb://event-service",
    "GET, /api/v1/organizer/events/id-1/seat-map, lb://event-service",
    "PUT, /api/v1/organizer/events/id-1/seat-map, lb://event-service",
    "GET, /api/v1/organizer/events/id-1/statistics, lb://event-service",
    "POST, /api/v1/organizer/events/id-1/submit, lb://event-service",
    "PUT, /api/v1/organizer/events/id-1/ticket-types, lb://event-service",
    "GET, /api/v1/venues, lb://event-service",
    "GET, /api/v1/admin/organizer-applications, lb://user-service",
    "GET, /api/v1/admin/organizer-applications/id-1, lb://user-service",
    "POST, /api/v1/admin/organizer-applications/id-1/approve, lb://user-service",
    "POST, /api/v1/admin/organizer-applications/id-1/reject, lb://user-service",
    "PUT, /api/v1/me, lb://user-service",
    "GET, /api/v1/me, lb://user-service",
    "POST, /api/v1/organizer-applications, lb://user-service",
    "GET, /api/v1/organizer-applications, lb://user-service",
    "GET, /api/v1/organizer-applications/id-1, lb://user-service",
    "POST, /api/v1/organizers/me/logo, lb://user-service",
    "POST, /api/v1/organizers/me/banner, lb://user-service",
    "GET, /api/v1/organizers/id-1, lb://user-service",
    "DELETE, /api/v1/organizers/id-1/follow, lb://user-service",
    "GET, /api/v1/organizers/id-1/follow, lb://user-service",
    "PUT, /api/v1/organizers/id-1/follow, lb://user-service",
    "GET, /api/v1/events/id-1/availability, lb://booking-service",
    "POST, /api/v1/orders, lb://booking-service",
    "POST, /api/v1/orders/id-1/cancel, lb://booking-service",
    "POST, /api/v1/orders/id-1/promotion, lb://booking-service",
    "POST, /api/v1/admin/events/id-1/payout, lb://payment-service",
    "POST, /api/v1/admin/events/id-1/reconciliation, lb://payment-service",
    "GET, /api/v1/admin/events/id-1/refund-progress, lb://payment-service",
    "GET, /api/v1/admin/refunds, lb://payment-service",
    "POST, /api/v1/admin/refunds/id-1/manual-settlement, lb://payment-service",
    "POST, /api/v1/admin/refunds/id-1/retry, lb://payment-service",
    "GET, /api/v1/events/id-1/reconciliation, lb://payment-service",
    "GET, /api/v1/orders/id-1/payment, lb://payment-service",
    "POST, /api/v1/payments, lb://payment-service",
    "GET, /api/v1/payments/vnpay/ipn, lb://payment-service",
    "GET, /api/v1/payments/vnpay/return, lb://payment-service",
    "POST, /api/v1/events/id-1/check-ins, lb://ticket-service",
    "POST, /api/v1/orders/id-1/ticket-deliveries, lb://ticket-service",
    "GET, /api/v1/orders/id-1/tickets, lb://ticket-service",
    "GET, /api/v1/tickets/id-1/qr, lb://ticket-service"
  })
  void routesEveryServiceEndpointToItsOwner(String method, String path, String expectedUri) {
    Route route = resolve(method, path);
    assertNotNull(route, method + " " + path + " has no route");
    assertEquals(expectedUri, route.getUri().toString(), method + " " + path);
  }

  @Test
  void hasExactlySevenRoutes() {
    assertEquals(
        List.of(
            "booking",
            "event",
            "event-images",
            "payment",
            "payment-vnpay-callbacks",
            "ticket",
            "user"),
        routesById().keySet().stream().sorted().toList());
  }

  @Test
  void vnpayCallbacksBypassRateLimitAndBreakerAndOnlyMatchGet() {
    Route callbacks = routesById().get("payment-vnpay-callbacks");
    assertEquals(0, callbacks.getFilters().size());
    assertEquals("payment-vnpay-callbacks", resolve("GET", "/api/v1/payments/vnpay/ipn").getId());
    assertEquals(
        "payment-vnpay-callbacks", resolve("GET", "/api/v1/payments/vnpay/return").getId());
    assertEquals("payment", resolve("POST", "/api/v1/payments/vnpay/ipn").getId());
  }

  @Test
  void everyOtherRouteHasRateLimiterAndCircuitBreaker() {
    routesById().values().stream()
        .filter(route -> !route.getId().equals("payment-vnpay-callbacks"))
        .forEach(route -> assertEquals(2, route.getFilters().size(), route.getId()));
  }

  @Test
  void eventImagesUseTheLongResponseTimeout() {
    Route images = routesById().get("event-images");
    assertEquals("45000", String.valueOf(images.getMetadata().get("response-timeout")));
    assertEquals("event-images", resolve("POST", "/api/v1/organizer/events/id-1/images").getId());
    assertEquals("event", resolve("PUT", "/api/v1/organizer/events/id-1/seat-map").getId());
  }

  @Test
  void internalActuatorAndFallbackPathsAreNeverRouted() {
    assertNull(resolve("GET", "/internal/v1/users/id-1"));
    assertNull(resolve("POST", "/internal/v1/orders/id-1/purchase-snapshot"));
    assertNull(resolve("GET", "/actuator/health"));
    assertNull(resolve("GET", "/fallback/booking"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventServiceBreaker",
        "eventImageServiceBreaker",
        "bookingServiceBreaker",
        "paymentServiceBreaker",
        "ticketServiceBreaker",
        "userServiceBreaker"
      })
  void everyBreakerSharesTheDefaultConfigAndHasAnExplicitTimeLimit(String name) {
    CircuitBreakerConfig config = breakers.circuitBreaker(name).getCircuitBreakerConfig();
    assertEquals(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED, config.getSlidingWindowType());
    assertEquals(10, config.getSlidingWindowSize());
    assertEquals(5, config.getMinimumNumberOfCalls());
    assertEquals(3, config.getPermittedNumberOfCallsInHalfOpenState());
    assertEquals(50f, config.getFailureRateThreshold());
    assertEquals(10_000L, config.getWaitIntervalFunctionInOpenState().apply(1));
    assertEquals(true, config.isAutomaticTransitionFromOpenToHalfOpenEnabled());
    Duration expected =
        name.equals("eventImageServiceBreaker") ? Duration.ofSeconds(50) : Duration.ofSeconds(25);
    assertEquals(
        expected, timeLimiters.timeLimiter(name).getTimeLimiterConfig().getTimeoutDuration());
  }

  @Test
  void registersTheRedisRateLimiterBean() {
    assertNotNull(redisRateLimiter);
  }

  /** Giống RoutePredicateHandlerMapping: route đầu tiên theo order mà predicate khớp. */
  private Route resolve(String method, String path) {
    var exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.valueOf(method), path).build());
    return routeLocator
        .getRoutes()
        .sort(Comparator.comparingInt(Route::getOrder))
        .concatMap(
            route -> Mono.from(route.getPredicate().apply(exchange)).filter(m -> m).map(m -> route))
        .next()
        .block(Duration.ofSeconds(5));
  }

  private Map<String, Route> routesById() {
    List<Route> routes = routeLocator.getRoutes().collectList().block(Duration.ofSeconds(5));
    assertNotNull(routes);
    return routes.stream().collect(Collectors.toMap(Route::getId, Function.identity()));
  }
}
