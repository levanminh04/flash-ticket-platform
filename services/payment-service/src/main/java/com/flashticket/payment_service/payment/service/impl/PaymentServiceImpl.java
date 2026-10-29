package com.flashticket.payment_service.payment.service.impl;

import com.flashticket.payment_service.payment.repository.*;
import com.flashticket.payment_service.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
  private final OrderPaymentSnapshotRepository orderPaymentSnapshotRepository;
  private final PaymentAttemptRepository paymentAttemptRepository;
  private final ChargeRepository chargeRepository;
  private final PaymentConfirmationRepository paymentConfirmationRepository;
}
