package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.dto.message.AcceptPayment;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.message.PaymentDecision;
import com.flashticket.booking_service.dto.request.CreateOrderRequest;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import java.util.UUID;

public interface OrderService {
  void processBookingIssuanceResult(
      Envelope<com.flashticket.booking_service.dto.message.BookingIssuanceResult> message);

  PaymentDecision acceptPayment(Envelope<AcceptPayment> message);

  OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request);

  PurchaseSnapshot preparePurchaseSnapshot(String buyerSubject, UUID orderId);
}
