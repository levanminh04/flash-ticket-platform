package com.flashticket.payment_service.mapper;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.entity.PaymentAttempt;
import com.flashticket.payment_service.entity.Refund;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.entity.enums.RefundState;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@org.springframework.context.annotation.Import(
    com.flashticket.payment_service.config.PaymentReadRepositoryTestConfiguration.class)
@org.springframework.boot.test.context.SpringBootTest
class PaymentMappersTest {
  @org.springframework.beans.factory.annotation.Autowired OrderPaymentSnapshotMapper snapshotMapper;
  @org.springframework.beans.factory.annotation.Autowired PaymentStartMapper startMapper;
  @org.springframework.beans.factory.annotation.Autowired RefundMapper refundMapper;
  @org.springframework.beans.factory.annotation.Autowired PaymentViewMapper viewMapper;

  static <T> T empty(Class<T> type) throws Exception {
    var ctor = type.getDeclaredConstructor();
    ctor.setAccessible(true);
    return ctor.newInstance();
  }

  @Test
  void copiesSnapshotWithSuppliedHashAndReceiptTime() throws Exception {
    try (var factory = Validation.buildDefaultValidatorFactory();
        var in = getClass().getResourceAsStream("/contracts/purchase.json")) {
      var codec = new ContractCodec(JsonMapper.builder().build(), factory.getValidator());
      var purchase =
          codec.read(new String(in.readAllBytes(), StandardCharsets.UTF_8), PurchaseSnapshot.class);
      var jsonMapper = new SnapshotJsonMapper(codec);
      var entity = snapshotMapper.toEntity(purchase, "supplied-hash", Instant.EPOCH);
      assertThat(entity.getOrderId()).isEqualTo(purchase.orderId());
      assertThat(entity.getExpectedAmount()).isEqualByComparingTo("9999999999999999999");
      assertThat(entity.getSourceOrderVersion()).isEqualTo(Long.MAX_VALUE);
      assertThat(entity.getSnapshotHash()).isEqualTo("supplied-hash");
      assertThat(entity.getReceivedAt()).isEqualTo(Instant.EPOCH);
      assertThat(jsonMapper.fromJson(entity.getPurchaseSnapshot())).isEqualTo(purchase);
    }
  }

  @Test
  void mapsAttemptWithoutLoadingOrderAssociation() throws Exception {
    var attempt = empty(PaymentAttempt.class);
    attempt.setId(UUID.randomUUID());
    attempt.setOrderId(UUID.randomUUID());
    attempt.setExpectedAmount(BigDecimal.TEN);
    attempt.setCurrency("VND");
    attempt.setPaymentUrl("https://fixture.example/pay");
    var mapper = startMapper;
    for (var state : PaymentAttemptState.values()) {
      attempt.setState(state);
      var dto = mapper.toDto(attempt, Instant.EPOCH);
      assertThat(dto.amount()).isEqualTo("10");
      assertThat(dto.orderId()).isEqualTo(attempt.getOrderId());
      assertThat(dto.paymentUrl())
          .isEqualTo(state == PaymentAttemptState.PENDING ? "https://fixture.example/pay" : null);
    }
  }

  @Test
  void mapsRefundAndExplicitViewInputs() throws Exception {
    var refund = empty(Refund.class);
    refund.setId(UUID.randomUUID());
    refund.setChargeId(UUID.randomUUID());
    refund.setAmount(BigDecimal.TEN);
    refund.setState(RefundState.PENDING);
    refund.setReason(RefundReason.EVENT_CANCELLED);
    var dto = refundMapper.toDto(refund);
    assertThat(dto.amount()).isEqualTo("10");
    assertThat(dto.chargeId()).isEqualTo(refund.getChargeId());
    var orderId = UUID.randomUUID();
    var chargeId = UUID.randomUUID();
    var list = new ArrayList<>(List.of(dto));
    var view = viewMapper.toDto(orderId, chargeId, null, null, list);
    list.clear();
    assertThat(view.refunds()).hasSize(1);
    assertThat(view.latestAttempt()).isNull();
    assertThat(view.acceptedChargeId()).isEqualTo(chargeId);
  }
}
