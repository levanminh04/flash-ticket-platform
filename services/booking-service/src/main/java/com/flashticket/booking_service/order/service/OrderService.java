package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.dto.request.CreateOrderRequest;
import com.flashticket.booking_service.dto.response.OrderView;
import java.util.UUID;

public interface OrderService {
  OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request);
}
