package com.flashticket.booking_service.order;

import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "booking.event-cancellation",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class EventCancellationWorker {
  private final OrderRepository orderRepository;
  private final OrderService orderService;

  @Value("${booking.event-cancellation.batch-size:20}")
  private int batchSize;

  private UUID lastOrderId;

  @Scheduled(fixedDelayString = "${booking.event-cancellation.poll-interval:PT5S}")
  public void cancelOrdersForCancelledEvents() {
    if (batchSize <= 0)
      throw new IllegalArgumentException("Cancellation batch size must be positive");
    var candidates =
        orderRepository.findOrderIdsForEventCancellation(lastOrderId, PageRequest.of(0, batchSize));
    for (var orderId : candidates) {
      if (Thread.currentThread().isInterrupted()) return;
      try {
        // Mỗi order có transaction qua proxy; rollback một đơn không hủy các đơn đã xong.
        orderService.cancelOrderForEventCancellation(orderId);
      } catch (RuntimeException exception) {
        LoggerFactory.getLogger(EventCancellationWorker.class)
            .warn("Cancellation orderId={} errorCode={}", orderId, ErrorCode.from(exception));
      }
      // Đi qua ID lỗi rồi quay lại ở vòng sau; DB state mới là công việc còn lại.
      lastOrderId = orderId;
    }
    if (candidates.size() < batchSize) lastOrderId = null;
  }
}
