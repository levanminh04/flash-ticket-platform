package com.flashticket.booking_service.order;

import com.flashticket.booking_service.dto.projection.ExpiredOrderCandidate;
import com.flashticket.booking_service.enums.OrderState;
import com.flashticket.booking_service.exception.ErrorCode;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import java.time.Instant;
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

  private ExpiredOrderCandidate lastCandidate;
  private Instant cycleCutoff;

  @Scheduled(fixedDelayString = "${booking.expiry.poll-interval:PT5S}")
  public void expireDueOrders() {
    if (batchSize <= 0) throw new IllegalArgumentException("Expiry batch size must be positive");
    // Giữ cùng mốc giờ DB qua các trang để vòng quét có điểm kết thúc.
    var cutoff = cycleCutoff == null ? orderRepository.currentDatabaseTime() : cycleCutoff;
    var page = PageRequest.of(0, batchSize);
    var candidates =
        lastCandidate == null
            ? orderRepository.findExpiredOrderCandidates(OrderState.PENDING_PAYMENT, cutoff, page)
            : orderRepository.findExpiredOrderCandidatesAfter(
                OrderState.PENDING_PAYMENT,
                cutoff,
                lastCandidate.getExpiresAt(),
                lastCandidate.getOrderId(),
                page);
    for (var candidate : candidates) {
      if (Thread.currentThread().isInterrupted()) return;
      var orderId = candidate.getOrderId();
      try {
        // Gọi qua service proxy: mỗi order commit/rollback xong mới xử lý ID tiếp theo.
        orderService.expireOrder(orderId);
      } catch (RuntimeException exception) {
        LoggerFactory.getLogger(OrderExpiryWorker.class)
            .warn("Expiry orderId={} errorCode={}", orderId, ErrorCode.from(exception));
      }
      // Tiến qua cả ID lỗi để không kẹt ở đầu hàng; cuối vòng sẽ quét lại các đơn còn pending.
      lastCandidate = candidate;
      cycleCutoff = cutoff;
    }
    if (candidates.size() < batchSize) {
      lastCandidate = null;
      cycleCutoff = null;
    }
  }
}
