package com.flashticket.api_gateway.routing;

import java.util.function.Function;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.route.builder.UriSpec;
import org.springframework.http.HttpMethod;

/** User-owned additions kept separate from Booking/Payment/Event route definitions. */
final class UserGatewayRoutes {
  private UserGatewayRoutes() {}

  static RouteLocatorBuilder.Builder addTo(
      RouteLocatorBuilder.Builder routes, Function<GatewayFilterSpec, UriSpec> filters) {
    return routes
        .route(
            "user-applications",
            r ->
                r.order(220)
                    .path("/api/v1/organizer-applications")
                    .and()
                    .method(HttpMethod.GET, HttpMethod.POST)
                    .filters(filters)
                    .uri("lb://user-service"))
        .route(
            "user-application-detail",
            r ->
                r.order(230)
                    .path("/api/v1/organizer-applications/*")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(filters)
                    .uri("lb://user-service"))
        .route(
            "user-admin-applications",
            r ->
                r.order(240)
                    .path(
                        "/api/v1/admin/organizer-applications",
                        "/api/v1/admin/organizer-applications/*")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(filters)
                    .uri("lb://user-service"))
        .route(
            "user-admin-decisions",
            r ->
                r.order(250)
                    .path(
                        "/api/v1/admin/organizer-applications/*/approve",
                        "/api/v1/admin/organizer-applications/*/reject")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(filters)
                    .uri("lb://user-service"))
        .route(
            "user-public-organizer",
            r ->
                r.order(260)
                    .path("/api/v1/organizers/*")
                    .and()
                    .method(HttpMethod.GET)
                    .filters(filters)
                    .uri("lb://user-service"))
        .route(
            "user-follow",
            r ->
                r.order(270)
                    .path("/api/v1/organizers/*/follow")
                    .and()
                    .method(HttpMethod.GET, HttpMethod.PUT, HttpMethod.DELETE)
                    .filters(filters)
                    .uri("lb://user-service"))
        .route(
            "user-branding",
            r ->
                r.order(280)
                    .path("/api/v1/organizers/me/logo", "/api/v1/organizers/me/banner")
                    .and()
                    .method(HttpMethod.POST)
                    .filters(filters)
                    .uri("lb://user-service"));
  }
}
