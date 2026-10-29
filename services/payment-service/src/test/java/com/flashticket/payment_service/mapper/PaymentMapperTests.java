package com.flashticket.payment_service.mapper;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.enums.*;
import com.flashticket.payment_service.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentMapperTests {
  @Test
  void mapsAttemptAndSnapshotToPaymentStart() {
    var mapper = new PaymentMapperImpl(new WireValueConverter());
    var id = UUID.randomUUID();
    var attempt = new PaymentAttempt();
    var snapshot = new OrderPaymentSnapshot();
    attempt.setId(UUID.randomUUID());
    attempt.setOrderId(id);
    attempt.setExpectedAmount(new BigDecimal("9007199254740993"));
    attempt.setCurrency(Currency.VND);
    attempt.setState(PaymentAttemptState.UNKNOWN);
    snapshot.setOrderId(id);
    snapshot.setExpiresAt(Instant.parse("2026-11-01T00:15:00Z"));
    var view = mapper.toStart(attempt, snapshot);
    assertThat(view.getOrderId()).isEqualTo(id);
    assertThat(view.getAttemptId()).isEqualTo(attempt.getId());
    assertThat(view.getAmount()).isEqualTo("9007199254740993");
    assertThat(view.getAttemptState()).isEqualTo(PaymentAttemptState.UNKNOWN);
    assertThat(view.getPaymentUrl()).isNull();
    assertThat(view.getExpiresAt()).isEqualTo(snapshot.getExpiresAt());
  }

  @Test
  void rejectsSourcesFromDifferentOrders() {
    var mapper = new PaymentMapperImpl(new WireValueConverter());
    var attempt = new PaymentAttempt();
    attempt.setOrderId(UUID.randomUUID());
    var snapshot = new OrderPaymentSnapshot();
    snapshot.setOrderId(UUID.randomUUID());
    assertThatThrownBy(() -> mapper.toStart(attempt, snapshot))
        .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> mapper.toStart(null, snapshot)).isInstanceOf(BusinessException.class);
  }

  @Test
  void mapsRefundWithoutLeakingLedgerFields() {
    var refund = new Refund();
    refund.setId(UUID.randomUUID());
    refund.setChargeId(UUID.randomUUID());
    refund.setAmount(new BigDecimal("200"));
    refund.setState(RefundState.PROCESSING);
    refund.setReason(RefundReason.EVENT_CANCELLED);
    var view = new PaymentMapperImpl(new WireValueConverter()).toRefundView(refund);
    assertThat(view.getRefundId()).isEqualTo(refund.getId());
    assertThat(view.getChargeId()).isEqualTo(refund.getChargeId());
    assertThat(view.getAmount()).isEqualTo("200");
    assertThat(view.getState()).isEqualTo(RefundState.PROCESSING);
    assertThat(
            java.util.Arrays.stream(view.getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
        .containsExactlyInAnyOrder("refundId", "chargeId", "amount", "state", "reason");
  }

  @Test
  void rejectsFractionalAndOutOfRangeMoney() {
    var converter = new WireValueConverter();
    for (String value : java.util.List.of("1.5", "-1", "10000000000000000000"))
      assertThatThrownBy(() -> converter.toMoney(new BigDecimal(value)))
          .isInstanceOf(BusinessException.class);
    assertThat(converter.toMoney(BigDecimal.ZERO)).isEqualTo("0");
  }
}
