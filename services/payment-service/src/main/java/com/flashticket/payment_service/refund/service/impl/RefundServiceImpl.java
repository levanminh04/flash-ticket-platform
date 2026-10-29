package com.flashticket.payment_service.refund.service.impl;

import com.flashticket.payment_service.refund.repository.*;
import com.flashticket.payment_service.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {
  private final RefundRepository refundRepository;
  private final RefundAttemptRepository refundAttemptRepository;
  private final EventRefundRunRepository eventRefundRunRepository;
  private final EventRefundItemRepository eventRefundItemRepository;
}
