package com.flashticket.api_gateway.routing;

import java.net.InetAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class GatewayRouteConfiguration {

  private static final int TEMPORARY_REPLENISH_RATE = 10;
  private static final int TEMPORARY_BURST_CAPACITY = 20;
  private static final int TEMPORARY_REQUESTED_TOKENS = 1;

  @Bean
  RedisRateLimiter redisRateLimiter() {
    return new RedisRateLimiter(
        TEMPORARY_REPLENISH_RATE, TEMPORARY_BURST_CAPACITY, TEMPORARY_REQUESTED_TOKENS);
  }

  /**
   * Uses the direct remote address as the limiter key. This adapts the legacy resolver by handling
   * an absent or unresolved address safely and does not trust client-supplied forwarding headers.
   */
  @Bean
  KeyResolver remoteAddressKeyResolver() {
    return exchange ->
        Mono.justOrEmpty(exchange.getRequest().getRemoteAddress())
            .flatMap(
                address -> {
                  InetAddress resolvedAddress = address.getAddress();
                  return Mono.justOrEmpty(resolvedAddress)
                      .map(InetAddress::getHostAddress)
                      .switchIfEmpty(Mono.justOrEmpty(address.getHostString()));
                })
            .filter(key -> !key.isBlank())
            .defaultIfEmpty("unknown");
  }

  @Bean
  RouteLocator customRouteLocator(
      RouteLocatorBuilder builder,
      RedisRateLimiter redisRateLimiter,
      KeyResolver remoteAddressKeyResolver) {
    return UserGatewayRoutes.addTo(
            builder.routes(),
            filters ->
                standardFilters(
                    filters,
                    redisRateLimiter,
                    remoteAddressKeyResolver,
                    "userServiceBreaker",
                    "user"))
        .route(
            "booking-availability",
            route ->
                route
                    .order(0)
                    .path("/api/v1/events/*/availability")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "bookingServiceBreaker",
                                "booking"))
                    .uri("lb://booking-service"))
        .route(
            "ticket-check-in",
            route ->
                route
                    .order(10)
                    .path("/api/v1/events/*/check-ins")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "ticketServiceBreaker",
                                "ticket"))
                    .uri("lb://ticket-service"))
        .route(
            "event-public-detail",
            route ->
                route
                    .order(20)
                    .path("/api/v1/events/*")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-organizer-seat-map",
            route ->
                route
                    .order(30)
                    .path("/api/v1/organizer/events/*/seat-map")
                    .and()
                    .method(HttpMethod.GET, HttpMethod.PUT)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "booking-create-order",
            route ->
                route
                    .order(40)
                    .path("/api/v1/orders")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "bookingServiceBreaker",
                                "booking"))
                    .uri("lb://booking-service"))
        .route(
            "booking-promote-order",
            route ->
                route
                    .order(50)
                    .path("/api/v1/orders/*/promotion")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "bookingServiceBreaker",
                                "booking"))
                    .uri("lb://booking-service"))
        .route(
            "booking-cancel-order",
            route ->
                route
                    .order(60)
                    .path("/api/v1/orders/*/cancel")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "bookingServiceBreaker",
                                "booking"))
                    .uri("lb://booking-service"))
        .route(
            "booking-read-order",
            route ->
                route
                    .order(70)
                    .path("/api/v1/orders/*")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "bookingServiceBreaker",
                                "booking"))
                    .uri("lb://booking-service"))
        .route(
            "booking-list-orders",
            route ->
                route
                    .order(75)
                    .path("/api/v1/orders")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "bookingServiceBreaker",
                                "booking"))
                    .uri("lb://booking-service"))
        .route(
            "booking-start-payment",
            route ->
                route
                    .order(80)
                    .path("/api/v1/orders/*/payment-attempts")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "bookingServiceBreaker",
                                "booking"))
                    .uri("lb://booking-service"))
        .route(
            "payment-read-order",
            route ->
                route
                    .order(90)
                    .path("/api/v1/orders/*/payment")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "paymentServiceBreaker",
                                "payment"))
                    .uri("lb://payment-service"))
        .route(
            "ticket-order-tickets",
            route ->
                route
                    .order(100)
                    .path("/api/v1/orders/*/tickets")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "ticketServiceBreaker",
                                "ticket"))
                    .uri("lb://ticket-service"))
        .route(
            "ticket-qr",
            route ->
                route
                    .order(110)
                    .path("/api/v1/tickets/*/qr")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "ticketServiceBreaker",
                                "ticket"))
                    .uri("lb://ticket-service"))
        .route(
            "ticket-delivery",
            route ->
                route
                    .order(120)
                    .path("/api/v1/orders/*/ticket-deliveries")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "ticketServiceBreaker",
                                "ticket"))
                    .uri("lb://ticket-service"))
        .route(
            "user-current-user",
            route ->
                route
                    .order(130)
                    .path("/api/v1/me")
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "userServiceBreaker",
                                "user"))
                    .uri("lb://user-service"))
        .route(
            "event-public-catalog",
            route ->
                route
                    .order(140)
                    .path("/api/v1/events", "/api/v1/categories", "/api/v1/venues")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-organizer-collection",
            route ->
                route
                    .order(150)
                    .path("/api/v1/organizer/events")
                    .and()
                    .method(HttpMethod.GET, HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-organizer-detail",
            route ->
                route
                    .order(160)
                    .path("/api/v1/organizer/events/*")
                    .and()
                    .method(HttpMethod.GET, HttpMethod.PUT)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-organizer-commands",
            route ->
                route
                    .order(170)
                    .path(
                        "/api/v1/organizer/events/*/submit",
                        "/api/v1/organizer/events/*/publish",
                        "/api/v1/organizer/events/*/sales-mode",
                        "/api/v1/organizer/events/*/cancel",
                        "/api/v1/organizer/events/*/cancellation-requests")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-organizer-seat-types",
            route ->
                route
                    .order(180)
                    .path("/api/v1/organizer/events/*/ticket-types")
                    .and()
                    .method(HttpMethod.PUT)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-organizer-read-models",
            route ->
                route
                    .order(190)
                    .path(
                        "/api/v1/organizer/events/*/statistics",
                        "/api/v1/organizer/events/*/cancellation-requests")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-admin-reads",
            route ->
                route
                    .order(200)
                    .path(
                        "/api/v1/admin/events",
                        "/api/v1/admin/events/*",
                        "/api/v1/admin/events/*/cancellation-requests",
                        "/api/v1/admin/cancellation-requests")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .route(
            "event-admin-commands",
            route ->
                route
                    .order(210)
                    .path(
                        "/api/v1/admin/events/*/approve",
                        "/api/v1/admin/events/*/return-to-draft",
                        "/api/v1/admin/events/*/cancel",
                        "/api/v1/admin/cancellation-requests/*/approve",
                        "/api/v1/admin/cancellation-requests/*/reject")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(
                        filters ->
                            standardFilters(
                                filters,
                                redisRateLimiter,
                                remoteAddressKeyResolver,
                                "eventServiceBreaker",
                                "event"))
                    .uri("lb://event-service"))
        .build();
  }

  private GatewayFilterSpec standardFilters(
      GatewayFilterSpec filters,
      RedisRateLimiter redisRateLimiter,
      KeyResolver remoteAddressKeyResolver,
      String circuitBreakerName,
      String fallbackService) {
    return filters
        .requestRateLimiter(
            rateLimit ->
                rateLimit.setRateLimiter(redisRateLimiter).setKeyResolver(remoteAddressKeyResolver))
        .circuitBreaker(
            circuitBreaker ->
                circuitBreaker
                    .setName(circuitBreakerName)
                    .setFallbackUri("forward:/fallback/" + fallbackService));
  }
}
