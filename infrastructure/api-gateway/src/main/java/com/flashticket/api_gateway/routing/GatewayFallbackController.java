package com.flashticket.api_gateway.routing;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;

import com.flashticket.api_gateway.web.GatewayHttpHandler;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Handles circuit-breaker forwards without manufacturing a business response. */
@RestController
@RequestMapping("/fallback")
class GatewayFallbackController {
  private final GatewayHttpHandler errors;

  GatewayFallbackController(GatewayHttpHandler errors) {
    this.errors = errors;
  }

  @RequestMapping("/{service}")
  Mono<Void> unavailable(ServerWebExchange exchange) {
    Throwable error = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
    Throwable cause = error;
    for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
      if (cause instanceof TimeoutException
          || cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
        return errors.write(exchange, HttpStatus.GATEWAY_TIMEOUT, cause);
      }
    }
    return errors.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, error);
  }
}
