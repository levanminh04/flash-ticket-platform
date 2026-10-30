package com.flashticket.booking_service.order;

import com.flashticket.booking_service.enums.OrderState;
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
    prefix = "booking.expiry",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false)
public class OrderExpiryWorker {
  private final OrderRepository orderRepository;
  private final OrderService orderService;

  @Value("${booking.expiry.batch-size:20}")
  private int batchSize;

  private UUID lastOrderId;

  @Scheduled(fixedDelayString = "${booking.expiry.poll-interval:PT5S}")
  public void expireDueOrders() {
    if (batchSize <= 0) throw new IllegalArgumentException("Expiry batch size must be positive");
    var cutoff = orderRepository.currentDatabaseTime();
    var candidates =
        orderRepository.findExpiredOrderIds(
            OrderState.PENDING_PAYMENT, cutoff, lastOrderId, PageRequest.of(0, batchSize));
    for (UUID orderId : candidates) {
      if (Thread.currentThread().isInterrupted()) return;
      try {
        // Gọi qua service proxy: mỗi order commit/rollback xong mới xử lý ID tiếp theo.
        orderService.expireOrder(orderId);
      } catch (RuntimeException exception) {
        LoggerFactory.getLogger(OrderExpiryWorker.class)
            .warn("Expiry orderId={} errorCode={}", orderId, ErrorCode.from(exception));
      }
      // Tiến qua cả ID lỗi để không kẹt ở đầu hàng; cuối vòng sẽ quét lại các đơn còn pending.
      lastOrderId = orderId;
    }
    if (candidates.size() < batchSize) lastOrderId = null;
  }
}
