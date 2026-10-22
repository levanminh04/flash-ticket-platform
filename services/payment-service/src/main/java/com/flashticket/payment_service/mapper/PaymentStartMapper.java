package com.flashticket.payment_service.mapper;
import com.flashticket.payment_service.dto.payment.PaymentStart;
import com.flashticket.payment_service.entity.PaymentAttempt;
import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import java.time.Instant;
public final class PaymentStartMapper {
  private final MoneyMapper money;
  public PaymentStartMapper(MoneyMapper money){this.money=money;}
  public PaymentStart toDto(PaymentAttempt attempt,Instant expiresAt){
    return new PaymentStart(attempt.getOrderId(),attempt.getId(),attempt.getState(),money.toWire(attempt.getExpectedAmount()),attempt.getCurrency(),attempt.getState()==PaymentAttemptState.PENDING?attempt.getPaymentUrl():null,expiresAt);
  }
}
