package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.response.PaymentStart;
import com.flashticket.payment_service.entity.PaymentAttempt;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import java.time.Instant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = PaymentMapperConfig.class, uses = MoneyMapper.class)
public interface PaymentStartMapper {
  @Mapping(target = "orderId", source = "attempt.orderId")
  @Mapping(target = "attemptId", source = "attempt.id")
  @Mapping(target = "attemptState", source = "attempt.state")
  @Mapping(target = "amount", source = "attempt.expectedAmount", qualifiedByName = "moneyToWire")
  @Mapping(target = "currency", source = "attempt.currency")
  @Mapping(target = "paymentUrl", source = "attempt", qualifiedByName = "pendingPaymentUrl")
  @Mapping(target = "expiresAt", source = "expiresAt")
  PaymentStart toDto(PaymentAttempt attempt, Instant expiresAt);

  @Named("pendingPaymentUrl")
  default String pendingPaymentUrl(PaymentAttempt attempt) {
    return attempt != null && attempt.getState() == PaymentAttemptState.PENDING
        ? attempt.getPaymentUrl()
        : null;
  }
}
