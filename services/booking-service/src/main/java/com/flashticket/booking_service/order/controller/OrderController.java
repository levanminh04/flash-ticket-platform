package com.flashticket.booking_service.order.controller;

import com.flashticket.booking_service.dto.request.ApplyPromotionRequest;
import com.flashticket.booking_service.dto.request.CreateOrderRequest;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.order.service.OrderService;
import com.flashticket.booking_service.promotion.service.PromotionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
  private final OrderService orderService;
  private final PromotionService promotionService;

  @PostMapping("/{orderId}/promotion")
  public ResponseEntity<OrderView> applyPromotionToOrder(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID orderId,
      @Valid @RequestBody ApplyPromotionRequest request) {
    return ResponseEntity.ok(
        promotionService.applyPromotionToOrder(jwt.getSubject(), orderId, request));
  }

  @PostMapping
  public ResponseEntity<OrderView> createOrder(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
      @Valid @RequestBody CreateOrderRequest request) {
    OrderView order = orderService.createOrder(jwt.getSubject(), idempotencyKey, request);
    return ResponseEntity.created(URI.create("/api/v1/orders/" + order.getOrderId())).body(order);
  }
}
