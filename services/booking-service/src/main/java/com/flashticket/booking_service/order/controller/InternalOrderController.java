package com.flashticket.booking_service.order.controller;

import com.flashticket.booking_service.dto.request.FreezePaymentRequest;
import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.booking_service.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderController {
  private final OrderService orderService;

  @PostMapping("/{orderId}/purchase-snapshot")
  public ResponseEntity<PurchaseSnapshot> preparePurchaseSnapshot(
      @PathVariable UUID orderId, @Valid @RequestBody FreezePaymentRequest request) {
    return ResponseEntity.ok(
        orderService.preparePurchaseSnapshot(request.getBuyerSubject(), orderId));
  }
}
