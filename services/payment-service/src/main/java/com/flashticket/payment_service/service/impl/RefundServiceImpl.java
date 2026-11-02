package com.flashticket.payment_service.service.impl;

import com.flashticket.payment_service.entity.Charge;
import com.flashticket.payment_service.entity.Refund;
import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.repository.EventRefundItemRepository;
import com.flashticket.payment_service.repository.EventRefundRunRepository;
import com.flashticket.payment_service.repository.RefundAttemptRepository;
import com.flashticket.payment_service.repository.RefundRepository;
import com.flashticket.payment_service.service.RefundService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
  private final RefundRepository refundRepository;
  private final RefundAttemptRepository refundAttemptRepository;
  private final EventRefundRunRepository eventRefundRunRepository;
  private final EventRefundItemRepository eventRefundItemRepository;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Refund ensureRefund(Charge charge, RefundReason reason, Instant createdAt) {
    // Khóa order của caller và unique(charge_id) giữ một nghĩa vụ hoàn, không đổi reason đã lưu.
    return refundRepository
        .findByChargeId(charge.getId())
        .orElseGet(() -> refundRepository.save(Refund.pending(charge, reason, createdAt)));
  }
}
