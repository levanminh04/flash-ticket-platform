package com.flashticket.payment_service;

import com.flashticket.payment_service.finance.repository.*;
import com.flashticket.payment_service.payment.repository.*;
import com.flashticket.payment_service.refund.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@MockitoBean(
    types = {
      OrderPaymentSnapshotRepository.class,
      PaymentAttemptRepository.class,
      ChargeRepository.class,
      PaymentConfirmationRepository.class,
      RefundRepository.class,
      RefundAttemptRepository.class,
      EventRefundRunRepository.class,
      EventRefundItemRepository.class,
      EventFinanceSnapshotRepository.class,
      EventPayoutRepository.class,
      JwtDecoder.class
    })
class PaymentServiceApplicationTests {

  @Test
  void contextLoads() {}
}
