package com.flashticket.api_gateway.routing;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Handles circuit-breaker forwards without manufacturing a business response. */
@RestController
@RequestMapping("/fallback")
class GatewayFallbackController {

  @RequestMapping("/{service}")
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  void unavailable() {}
}
