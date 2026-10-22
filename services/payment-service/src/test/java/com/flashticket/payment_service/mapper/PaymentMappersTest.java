package com.flashticket.payment_service.mapper;
import static org.assertj.core.api.Assertions.*;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.snapshot.*;
import com.flashticket.payment_service.entity.*;
import com.flashticket.payment_service.entity.enums.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

class PaymentMappersTest {
  static <T> T empty(Class<T> type)throws Exception {var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);return ctor.newInstance();}
  @Test void copiesSnapshotWithSuppliedHashAndReceiptTime()throws Exception {
    try(var factory=Validation.buildDefaultValidatorFactory();var in=getClass().getResourceAsStream("/contracts/purchase.json")){
      var codec=new ContractCodec(JsonMapper.builder().build(),factory.getValidator());
      var purchase=codec.read(new String(in.readAllBytes(),StandardCharsets.UTF_8),PurchaseSnapshot.class);
      var jsonMapper=new SnapshotJsonMapper(codec);
      var entity=new OrderPaymentSnapshotMapper(jsonMapper).toEntity(purchase,"supplied-hash",Instant.EPOCH);
      assertThat(entity.getOrderId()).isEqualTo(purchase.orderId());
      assertThat(entity.getExpectedAmount()).isEqualByComparingTo("9999999999999999999");
      assertThat(entity.getSourceOrderVersion()).isEqualTo(Long.MAX_VALUE);
      assertThat(entity.getSnapshotHash()).isEqualTo("supplied-hash");
      assertThat(entity.getReceivedAt()).isEqualTo(Instant.EPOCH);
      assertThat(jsonMapper.fromJson(entity.getPurchaseSnapshot())).isEqualTo(purchase);
    }
  }
  @Test void mapsAttemptWithoutLoadingOrderAssociation()throws Exception {
    var attempt=empty(PaymentAttempt.class);attempt.setId(UUID.randomUUID());attempt.setOrderId(UUID.randomUUID());attempt.setExpectedAmount(BigDecimal.TEN);attempt.setCurrency("VND");attempt.setPaymentUrl("https://fixture.example/pay");
    var mapper=new PaymentStartMapper(new MoneyMapper());
    for(var state:PaymentAttemptState.values()){
      attempt.setState(state);var dto=mapper.toDto(attempt,Instant.EPOCH);
      assertThat(dto.amount()).isEqualTo("10");assertThat(dto.orderId()).isEqualTo(attempt.getOrderId());
      assertThat(dto.paymentUrl()).isEqualTo(state==PaymentAttemptState.PENDING?"https://fixture.example/pay":null);
    }
  }
  @Test void mapsRefundAndExplicitViewInputs()throws Exception {
    var refund=empty(Refund.class);refund.setId(UUID.randomUUID());refund.setChargeId(UUID.randomUUID());refund.setAmount(BigDecimal.TEN);refund.setState(RefundState.PENDING);refund.setReason(RefundReason.EVENT_CANCELLED);
    var dto=new RefundMapper(new MoneyMapper()).toDto(refund);
    assertThat(dto.amount()).isEqualTo("10");assertThat(dto.chargeId()).isEqualTo(refund.getChargeId());
    var orderId=UUID.randomUUID();var chargeId=UUID.randomUUID();var list=new ArrayList<>(List.of(dto));
    var view=new PaymentViewMapper().toDto(orderId,chargeId,null,null,list);list.clear();
    assertThat(view.refunds()).hasSize(1);assertThat(view.latestAttempt()).isNull();assertThat(view.acceptedChargeId()).isEqualTo(chargeId);
  }
}
