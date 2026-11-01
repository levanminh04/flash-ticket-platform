package com.flashticket.payment_service.config;

import static org.mockito.Mockito.mock;

import com.flashticket.payment_service.repository.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Smoke-test isolation only; never imported by PostgreSQL tests or product configuration. */
@TestConfiguration(proxyBeanMethods = false)
public class PaymentReadRepositoryTestConfiguration {
  @Bean
  org.springframework.transaction.PlatformTransactionManager paymentTransactionManager() {
    return mock(org.springframework.transaction.PlatformTransactionManager.class);
  }

  @Bean
  ChargeRepository chargeRepository() {
    return mock(ChargeRepository.class);
  }

  @Bean
  OrderPaymentSnapshotRepository orderPaymentSnapshotRepository() {
    return mock(OrderPaymentSnapshotRepository.class);
  }

  @Bean
  PaymentAttemptRepository paymentAttemptRepository() {
    return mock(PaymentAttemptRepository.class);
  }

  @Bean
  PaymentConfirmationRepository paymentConfirmationRepository() {
    return mock(PaymentConfirmationRepository.class);
  }

  @Bean
  RefundRepository refundRepository() {
    return mock(RefundRepository.class);
  }

  @Bean
  InboxMessageRepository inboxMessageRepository() {
    return mock(InboxMessageRepository.class);
  }

  @Bean
  EventFinanceSnapshotRepository eventFinanceSnapshotRepository() {
    return mock(EventFinanceSnapshotRepository.class);
  }

  @Bean
  EventPayoutRepository eventPayoutRepository() {
    return mock(EventPayoutRepository.class);
  }

  @Bean
  RefundAttemptRepository refundAttemptRepository() {
    return mock(RefundAttemptRepository.class);
  }

  @Bean
  EventRefundRunRepository eventRefundRunRepository() {
    return mock(EventRefundRunRepository.class);
  }

  @Bean
  EventRefundItemRepository eventRefundItemRepository() {
    return mock(EventRefundItemRepository.class);
  }

  @Bean
  PaymentSagaRepository paymentSagaRepository() {
    return mock(PaymentSagaRepository.class);
  }

  @Bean
  OutboxMessageRepository outboxMessageRepository() {
    return mock(OutboxMessageRepository.class);
  }
}
