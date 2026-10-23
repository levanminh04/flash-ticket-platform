package com.flashticket.payment_service.mapper;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.entity.PaymentAttempt;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@org.springframework.context.annotation.Import(
    com.flashticket.payment_service.config.PaymentReadRepositoryTestConfiguration.class)
@SpringBootTest
class MapperBeansTest {
  @Test
  void rejectsMissingPurchaseBeforeCreatingSnapshot() {
    assertThatThrownBy(() -> snapshotMapper.toEntity(null, "supplied-hash", Instant.EPOCH))
        .isInstanceOf(com.flashticket.payment_service.dto.common.ContractValidationException.class);
  }

  @Autowired PaymentStartMapper paymentStartMapper;
  @Autowired RefundMapper refundMapper;
  @Autowired PaymentViewMapper paymentViewMapper;
  @Autowired OrderPaymentSnapshotMapper snapshotMapper;
  @Autowired SnapshotJsonMapper snapshotJsonMapper;
  @Autowired MessageJsonMapper messageJsonMapper;
  @Autowired MoneyMapper moneyMapper;
  @Autowired VersionMapper versionMapper;

  @Test
  void injectsMappersAndPreservesPaymentWireValues() throws Exception {
    var attempt = PaymentMappersTest.empty(PaymentAttempt.class);
    attempt.setId(UUID.randomUUID());
    attempt.setOrderId(UUID.randomUUID());
    attempt.setState(PaymentAttemptState.UNKNOWN);
    attempt.setExpectedAmount(new BigDecimal("9999999999999999999"));
    attempt.setCurrency("VND");
    attempt.setPaymentUrl("https://fixture.example/expired");
    var response = paymentStartMapper.toDto(attempt, Instant.EPOCH);
    assertThat(response.amount()).isEqualTo("9999999999999999999");
    assertThat(response.expiresAt()).isEqualTo(Instant.EPOCH);
    assertThat(response.paymentUrl()).isNull();
    assertThat(new BeanWrapperImpl(attempt).isWritableProperty("expectedAmount")).isTrue();
    assertThat(new BeanWrapperImpl(attempt).isWritableProperty("orderSnapshot")).isFalse();
    assertThat(new BeanWrapperImpl(attempt).isWritableProperty("charges")).isFalse();
  }
}
