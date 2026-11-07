package com.flashticket.api_gateway.web;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Chạy trước security: gắn X-Correlation-Id / X-Request-Id, log request lỗi, và viết lại body 429
 * của rate limiter. Status và body lỗi của service phía sau được giữ nguyên.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter implements WebFilter {
  public static final String CORRELATION = "X-Correlation-Id";
  public static final String REQUEST = "X-Request-Id";

  private final GatewayErrorWriter errors;

  CorrelationIdFilter(GatewayErrorWriter errors) {
    this.errors = errors;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String correlation = canonicalId(exchange.getRequest().getHeaders().get(CORRELATION));
    String requestId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    exchange.getAttributes().put(CORRELATION, correlation);
    exchange.getAttributes().put(REQUEST, requestId);
    exchange
        .getResponse()
        .beforeCommit(
            () -> {
              var headers = exchange.getResponse().getHeaders();
              // Booking có thể gắn correlation theo orderId sau khi tạo; giữ giá trị của nó.
              List<String> correlations = headers.getOrEmpty(CORRELATION);
              String downstreamId = correlations.isEmpty() ? null : correlations.getLast();
              headers.set(CORRELATION, validId(downstreamId) ? downstreamId : correlation);
              List<String> requests = headers.getOrEmpty(REQUEST);
              String downstreamRequest = requests.isEmpty() ? null : requests.getLast();
              headers.set(REQUEST, validId(downstreamRequest) ? downstreamRequest : requestId);
              HttpStatusCode status = exchange.getResponse().getStatusCode();
              if (status != null && status.isError()) {
                logFailure(exchange, status, headers.getFirst(CORRELATION), requestId, started);
              }
              return Mono.empty();
            });
    var response =
        new ServerHttpResponseDecorator(exchange.getResponse()) {
          @Override
          public Mono<Void> setComplete() {
            if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode()) && !isCommitted()) {
              return errors.write(exchange, HttpStatus.TOO_MANY_REQUESTS, null);
            }
            return super.setComplete();
          }
        };
    return chain.filter(
        exchange
            .mutate()
            .request(request -> request.headers(headers -> headers.set(CORRELATION, correlation)))
            .response(response)
            .build());
  }

  private static void logFailure(
      ServerWebExchange exchange,
      HttpStatusCode status,
      String correlation,
      String requestId,
      long started) {
    Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
    LoggerFactory.getLogger(CorrelationIdFilter.class)
        .atWarn()
        .addKeyValue("schemaVersion", 1)
        .addKeyValue("serviceName", "api-gateway")
        .addKeyValue(
            "eventName",
            status.is4xxClientError() ? "gateway.request.rejected" : "gateway.request.failed")
        .addKeyValue("operation", "http.proxy")
        .addKeyValue("outcome", status.is4xxClientError() ? "REJECTED" : "FAILED")
        .addKeyValue("correlationId", correlation)
        .addKeyValue("requestId", requestId)
        .addKeyValue("routeId", route == null ? "unmatched" : route.getId())
        .addKeyValue("dependencyService", route == null ? "none" : route.getUri().getHost())
        .addKeyValue("httpMethod", exchange.getRequest().getMethod().name())
        .addKeyValue("httpStatus", status.value())
        .addKeyValue(
            "reasonCode",
            exchange.getAttributeOrDefault(GatewayErrorWriter.CODE, "DOWNSTREAM_HTTP_ERROR"))
        .addKeyValue(
            "errorType", exchange.getAttributeOrDefault(GatewayErrorWriter.ERROR_TYPE, "none"))
        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
        .log("Gateway HTTP request failed.");
  }

  private static String canonicalId(List<String> values) {
    return values != null && values.size() == 1 && validId(values.getFirst())
        ? values.getFirst()
        : UUID.randomUUID().toString();
  }

  private static boolean validId(String value) {
    if (value == null || value.length() != 36) return false;
    try {
      return UUID.fromString(value).toString().equals(value);
    } catch (IllegalArgumentException ignored) {
      return false;
    }
  }
}
