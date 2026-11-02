package com.flashticket.payment_service.service;

import com.flashticket.payment_service.entity.Charge;
import com.flashticket.payment_service.entity.Refund;
import com.flashticket.payment_service.entity.enums.RefundReason;
import java.time.Instant;

public interface RefundService {
  Refund ensureRefund(Charge charge, RefundReason reason, Instant createdAt);
}
