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
    return builder
        .routes()
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
                                "booking",
                                true))
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
                                "ticket",
                                false))
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
                                "event",
                                true))
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
                                "event",
                                true))
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
                                "booking",
                                false))
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
                                "booking",
                                false))
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
                                "booking",
                                false))
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
                                "booking",
                                true))
                    .uri("lb://booking-service"))
        .route(
            "payment-create-attempt",
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
                                "paymentServiceBreaker",
                                "payment",
                                false))
                    .uri("lb://payment-service"))
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
                                "payment",
                                true))
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
                                "ticket",
                                true))
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
                                "ticket",
                                true))
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
                                "ticket",
                                false))
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
                                "user",
                                false))
                    .uri("lb://user-service"))
        .build();
  }

  private GatewayFilterSpec standardFilters(
      GatewayFilterSpec filters,
      RedisRateLimiter redisRateLimiter,
      KeyResolver remoteAddressKeyResolver,
      String circuitBreakerName,
      String fallbackService,
      boolean retryGet) {
    if (retryGet) {
      filters.retry(retry -> retry.setRetries(3).setMethods(HttpMethod.GET));
    }

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
