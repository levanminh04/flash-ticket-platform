package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.response.RefundAdminView;
import com.flashticket.payment_service.dto.response.RefundView;
import com.flashticket.payment_service.entity.Refund;
import com.flashticket.payment_service.entity.RefundAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PaymentMapperConfig.class, uses = MoneyMapper.class)
public interface RefundMapper {
  @Mapping(target = "refundId", source = "id")
  @Mapping(target = "amount", source = "amount", qualifiedByName = "moneyToWire")
  RefundView toDto(Refund refund);

  @Mapping(target = "refundId", source = "refund.id")
  @Mapping(target = "orderId", source = "refund.orderId")
  @Mapping(target = "chargeId", source = "refund.chargeId")
  @Mapping(target = "amount", source = "refund.amount", qualifiedByName = "moneyToWire")
  @Mapping(target = "currency", source = "refund.currency")
  @Mapping(target = "reason", source = "refund.reason")
  @Mapping(target = "state", source = "refund.state")
  @Mapping(target = "createdAt", source = "refund.createdAt")
  @Mapping(target = "completedAt", source = "refund.completedAt")
  @Mapping(target = "settledBy", source = "refund.settledBy")
  @Mapping(target = "externalReference", source = "refund.externalReference")
  @Mapping(target = "lastAttemptStartedAt", source = "lastAttempt.startedAt")
  @Mapping(target = "lastAttemptFinishedAt", source = "lastAttempt.finishedAt")
  @Mapping(target = "lastAttemptOutcome", source = "lastAttempt.outcome")
  RefundAdminView toAdminView(Refund refund, RefundAttempt lastAttempt);
}
