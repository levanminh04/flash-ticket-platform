package com.flashticket.payment_service.config;
import com.flashticket.payment_service.repository.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import static org.mockito.Mockito.mock;

/** Smoke-test isolation only; never imported by PostgreSQL tests or product configuration. */
@TestConfiguration(proxyBeanMethods=false)
public class PaymentReadRepositoryTestConfiguration {
  @Bean OrderPaymentSnapshotRepository orderPaymentSnapshotRepository(){return mock(OrderPaymentSnapshotRepository.class);}
  @Bean PaymentAttemptRepository paymentAttemptRepository(){return mock(PaymentAttemptRepository.class);}
  @Bean PaymentConfirmationRepository paymentConfirmationRepository(){return mock(PaymentConfirmationRepository.class);}
  @Bean RefundRepository refundRepository(){return mock(RefundRepository.class);}
  @Bean InboxMessageRepository inboxMessageRepository(){return mock(InboxMessageRepository.class);}
}
