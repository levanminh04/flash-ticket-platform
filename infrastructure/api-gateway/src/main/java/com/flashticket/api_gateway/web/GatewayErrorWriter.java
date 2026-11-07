package com.flashticket.api_gateway.web;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.springframework.cloud.gateway.support.ServiceUnavailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** Mọi lỗi do gateway tự sinh ra đều đi qua đây: cùng một body JSON, cùng mã lỗi. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GatewayErrorWriter implements WebExceptionHandler {
  static final String CODE = "gateway.errorCode";
  static final String ERROR_TYPE = "gateway.errorType";
  private static final int MAX_CAUSE_DEPTH = 8;
  private final JsonMapper json = new JsonMapper();

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
    if (exchange.getResponse().isCommitted()) return Mono.error(error);
    return write(exchange, statusOf(error), error);
  }

  public Mono<Void> write(ServerWebExchange exchange, HttpStatusCode status, Throwable cause) {
    if (exchange.getResponse().isCommitted()) return Mono.empty();
    String code = codeOf(status);
    exchange.getAttributes().put(CODE, code);
    if (cause != null) exchange.getAttributes().put(ERROR_TYPE, cause.getClass().getName());
    String correlation = exchange.getAttribute(CorrelationIdFilter.CORRELATION);
    if (correlation == null) correlation = UUID.randomUUID().toString();
    byte[] body =
        json.writeValueAsBytes(
            Map.of(
                "code",
                code,
                "message",
                messageOf(code),
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

  /** Timeout, breaker mở, service từ chối kết nối: không còn fallback nên map tại đây. */
  static HttpStatusCode statusOf(Throwable error) {
    Throwable cause = error;
    for (int depth = 0;
        cause != null && depth < MAX_CAUSE_DEPTH;
        depth++, cause = cause.getCause()) {
      if (cause instanceof ResponseStatusException responseError) {
        return responseError.getStatusCode();
      }
      if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException) {
        return HttpStatus.GATEWAY_TIMEOUT;
      }
      if (cause instanceof ConnectException
          || cause instanceof CallNotPermittedException
          || cause instanceof ServiceUnavailableException) {
        return HttpStatus.SERVICE_UNAVAILABLE;
      }
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  private static String codeOf(HttpStatusCode status) {
    return switch (status.value()) {
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
  }

  private static String messageOf(String code) {
    return switch (code) {
      case "UNAUTHENTICATED" -> "Bạn cần xác thực để thực hiện yêu cầu này.";
      case "ACCESS_DENIED" -> "Bạn không có quyền thực hiện yêu cầu này.";
      case "ROUTE_NOT_FOUND" -> "Không tìm thấy tài nguyên được yêu cầu.";
      case "RATE_LIMIT_EXCEEDED" -> "Quá nhiều yêu cầu, vui lòng thử lại sau.";
      case "DEPENDENCY_UNAVAILABLE" -> "Dịch vụ xử lý tạm thời chưa sẵn sàng.";
      case "DEPENDENCY_TIMEOUT" -> "Dịch vụ xử lý phản hồi quá thời gian cho phép.";
      case "DEPENDENCY_FAILURE" -> "Không thể nhận phản hồi hợp lệ từ dịch vụ xử lý.";
      case "INVALID_REQUEST", "METHOD_NOT_ALLOWED", "PAYLOAD_TOO_LARGE", "UNSUPPORTED_MEDIA_TYPE" ->
          "Yêu cầu không được hỗ trợ hoặc không hợp lệ.";
      default -> "Hệ thống gặp lỗi khi xử lý yêu cầu.";
    };
  }
}
