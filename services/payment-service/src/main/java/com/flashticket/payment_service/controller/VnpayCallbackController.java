package com.flashticket.payment_service.controller;

import com.flashticket.payment_service.dto.response.VnpayAck;
import com.flashticket.payment_service.service.vnpay.VnpayCallbackService;
import com.flashticket.payment_service.web.VnpayReturnPage;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(
    prefix = "flashticket.payment.vnpay",
    name = "callbacks-enabled",
    havingValue = "true")
@RequestMapping("/api/v1/payments/vnpay")
public final class VnpayCallbackController {
  private final VnpayCallbackService callbacks;

  public VnpayCallbackController(VnpayCallbackService callbacks) {
    this.callbacks = callbacks;
  }

  @GetMapping(value = "/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<VnpayAck> ipn(HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(callbacks.handleNotification(request.getQueryString()));
  }

  @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> returned(HttpServletRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Referrer-Policy", "no-referrer")
        .header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
        .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
        .body(VnpayReturnPage.render(callbacks.isReturnAuthentic(request.getQueryString())));
  }
}
