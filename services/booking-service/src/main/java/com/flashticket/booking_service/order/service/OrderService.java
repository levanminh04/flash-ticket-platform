package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.dto.message.AcceptPayment;
import com.flashticket.booking_service.dto.message.Envelope;
import com.flashticket.booking_service.dto.message.PaymentDecision;
import com.flashticket.booking_service.dto.request.CreateOrderRequest;
import com.flashticket.booking_service.dto.response.EventSalesStatisticsPage;
import com.flashticket.booking_service.dto.response.OrderPage;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.dto.response.SalesStatisticsChartView;
import com.flashticket.booking_service.dto.response.SalesStatisticsSummaryView;
import com.flashticket.booking_service.dto.response.SalesSummaryView;
import com.flashticket.booking_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.enums.SalesGrouping;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Sort;

public interface OrderService {
  SalesStatisticsSummaryView getSalesStatisticsSummary(
      String organizerSubject, LocalDate from, LocalDate to);

  SalesStatisticsChartView getSalesStatisticsChart(
      String organizerSubject, LocalDate from, LocalDate to, SalesGrouping groupBy);

  EventSalesStatisticsPage getEventSalesStatistics(
      String organizerSubject,
      LocalDate from,
      LocalDate to,
      int page,
      int size,
      String sortBy,
      Sort.Direction direction);

  SalesSummaryView getEventSalesStatistics(
      String organizerSubject, UUID eventId, LocalDate from, LocalDate to);

  String getSalesEventOrganizerSubject(UUID eventId, String viewerSubject, boolean admin);

  void cancelOrderForEventCancellation(UUID orderId);

  OrderView cancelOrder(String buyerSubject, UUID orderId);

  OrderView getOrder(String buyerSubject, UUID orderId);

  OrderPage getOrders(String buyerSubject, OrderState state, int page, int size);

  SalesSummaryView getSalesSummary(
      String organizerSubject, UUID eventId, LocalDate from, LocalDate to);

  void expireOrder(UUID orderId);

  void processBookingIssuanceResult(
      Envelope<com.flashticket.booking_service.dto.message.BookingIssuanceResult> message);

  PaymentDecision acceptPayment(Envelope<AcceptPayment> message);

  OrderView createOrder(String buyerSubject, UUID idempotencyKey, CreateOrderRequest request);

  PurchaseSnapshot preparePurchaseSnapshot(String buyerSubject, UUID orderId);
}
