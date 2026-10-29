package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.response.PaymentStart;
import com.flashticket.payment_service.dto.response.PaymentView;
import com.flashticket.payment_service.entity.OrderPaymentSnapshot;
import com.flashticket.payment_service.entity.PaymentAttempt;
import com.flashticket.payment_service.entity.Refund;
import com.flashticket.payment_service.exception.BusinessException;
import com.flashticket.payment_service.exception.ErrorCode;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = WireValueConverter.class,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PaymentMapper {
  @Mapping(target = "orderId", source = "attempt.orderId")
  @Mapping(target = "attemptId", source = "attempt.id")
  @Mapping(target = "attemptState", source = "attempt.state")
  @Mapping(target = "amount", source = "attempt.expectedAmount", qualifiedByName = "toMoney")
  @Mapping(target = "currency", source = "attempt.currency")
  @Mapping(target = "paymentUrl", source = "attempt.paymentUrl")
  @Mapping(target = "expiresAt", source = "snapshot.expiresAt")
  PaymentStart toStart(PaymentAttempt attempt, OrderPaymentSnapshot snapshot);

  @Mapping(target = "refundId", source = "id")
  @Mapping(target = "amount", source = "amount", qualifiedByName = "toMoney")
  PaymentView.RefundView toRefundView(Refund refund);

  /** Hai nguồn cùng order mới được ghép thành một response. */
  @BeforeMapping
  default void validateStartSources(PaymentAttempt attempt, OrderPaymentSnapshot snapshot) {
    if (attempt == null
        || snapshot == null
        || attempt.getOrderId() == null
        || !attempt.getOrderId().equals(snapshot.getOrderId()))
      throw new BusinessException(ErrorCode.INTERNAL_ERROR);
  }
}
