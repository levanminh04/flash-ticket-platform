package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.dto.request.CreateOrderRequest;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import java.util.UUID;

public interface OrderService {
  OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request);

  PurchaseSnapshot preparePurchaseSnapshot(String buyerSubject, UUID orderId);
}
