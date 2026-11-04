package com.flashticket.api_gateway.web;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Correlation runs before security; error handling leaves downstream status and bodies intact. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GatewayHttpHandler implements WebFilter, WebExceptionHandler {
  private static final String CORRELATION = "X-Correlation-Id";
  private static final String REQUEST = "X-Request-Id";
  private static final String CODE = "gateway.errorCode";
  private static final String ERROR_TYPE = "gateway.errorType";
  private final JsonMapper json = new JsonMapper();

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
              // Booking may bind correlation to orderId after creation; preserve its authoritative
              // value.
              List<String> correlations = headers.getOrEmpty(CORRELATION);
              String downstreamId = correlations.isEmpty() ? null : correlations.getLast();
              headers.set(CORRELATION, validId(downstreamId) ? downstreamId : correlation);
              List<String> requests = headers.getOrEmpty(REQUEST);
              String downstreamRequest = requests.isEmpty() ? null : requests.getLast();
              headers.set(REQUEST, validId(downstreamRequest) ? downstreamRequest : requestId);
              HttpStatusCode status = exchange.getResponse().getStatusCode();
              if (status != null && status.isError()) {
                Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
                LoggerFactory.getLogger(GatewayHttpHandler.class)
                    .atWarn()
                    .addKeyValue("schemaVersion", 1)
                    .addKeyValue("serviceName", "api-gateway")
                    .addKeyValue(
                        "eventName",
                        status.is4xxClientError()
                            ? "gateway.request.rejected"
                            : "gateway.request.failed")
                    .addKeyValue("operation", "http.proxy")
                    .addKeyValue("outcome", status.is4xxClientError() ? "REJECTED" : "FAILED")
                    .addKeyValue("correlationId", headers.getFirst(CORRELATION))
                    .addKeyValue("requestId", requestId)
                    .addKeyValue("routeId", route == null ? "unmatched" : route.getId())
                    .addKeyValue(
                        "dependencyService", route == null ? "none" : route.getUri().getHost())
                    .addKeyValue("httpMethod", exchange.getRequest().getMethod().name())
                    .addKeyValue("httpStatus", status.value())
                    .addKeyValue(
                        "reasonCode", exchange.getAttributeOrDefault(CODE, "DOWNSTREAM_HTTP_ERROR"))
                    .addKeyValue("errorType", exchange.getAttributeOrDefault(ERROR_TYPE, "none"))
                    .addKeyValue(
                        "durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
                    .log("Gateway HTTP request failed.");
              }
              return Mono.empty();
            });
    var response =
        new ServerHttpResponseDecorator(exchange.getResponse()) {
          @Override
          public Mono<Void> setComplete() {
            if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode()) && !isCommitted()) {
              return write(exchange, HttpStatus.TOO_MANY_REQUESTS, null);
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

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
    if (exchange.getResponse().isCommitted()) return Mono.error(error);
    HttpStatusCode status =
        error instanceof ResponseStatusException responseError
            ? responseError.getStatusCode()
            : HttpStatus.INTERNAL_SERVER_ERROR;
    return write(exchange, status, error);
  }

  public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, Throwable cause) {
    if (exchange.getResponse().isCommitted()) return Mono.empty();
    String code =
        switch (status.value()) {
          case 400 -> "INVALID_REQUEST";
          case 401 -> "UNAUTHENTICATED";
          case 403 -> "ACCESS_DENIED";
          case 404 -> "ROUTE_NOT_FOUND";
          case 405 -> "METHOD_NOT_ALLOWED";
          case 413 -> "PAYLOAD_TOO_LARGE";
          case 415 -> "UNSUPPORTED_MEDIA_TYPE";
          case 429 -> "RATE_LIMIT_EXCEEDED";
          case 502 -> "DEPENDENCY_FAILURE";
          case 503 -> "DEPENDENCY_UNAVAILABLE";
          case 504 -> "DEPENDENCY_TIMEOUT";
          default -> "INTERNAL_ERROR";
        };
    String message =
        switch (code) {
          case "UNAUTHENTICATED" -> "Bạn cần xác thực để thực hiện yêu cầu này.";
          case "ACCESS_DENIED" -> "Bạn không có quyền thực hiện yêu cầu này.";
          case "ROUTE_NOT_FOUND" -> "Không tìm thấy tài nguyên được yêu cầu.";
          case "RATE_LIMIT_EXCEEDED" -> "Quá nhiều yêu cầu, vui lòng thử lại sau.";
          case "DEPENDENCY_UNAVAILABLE" -> "Dịch vụ xử lý tạm thời chưa sẵn sàng.";
          case "DEPENDENCY_TIMEOUT" -> "Dịch vụ xử lý phản hồi quá thời gian cho phép.";
          case "DEPENDENCY_FAILURE" -> "Không thể nhận phản hồi hợp lệ từ dịch vụ xử lý.";
          case "INVALID_REQUEST",
              "METHOD_NOT_ALLOWED",
              "PAYLOAD_TOO_LARGE",
              "UNSUPPORTED_MEDIA_TYPE" ->
              "Yêu cầu không được hỗ trợ hoặc không hợp lệ.";
          default -> "Hệ thống gặp lỗi khi xử lý yêu cầu.";
        };
    exchange.getAttributes().put(CODE, code);
    if (cause != null) exchange.getAttributes().put(ERROR_TYPE, cause.getClass().getName());
    String correlation = exchange.getAttribute(CORRELATION);
    if (correlation == null) correlation = UUID.randomUUID().toString();
    byte[] body =
        json.writeValueAsBytes(
            Map.of(
                "code",
                code,
                "message",
                message,
                "correlationId",
                correlation,
                "violations",
                List.of()));
    var response = exchange.getResponse();
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    response.getHeaders().setContentLength(body.length);
    return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
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
