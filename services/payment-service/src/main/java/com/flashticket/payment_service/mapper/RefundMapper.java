package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.payment.RefundView;
import com.flashticket.payment_service.entity.Refund;

public final class RefundMapper {
  private final MoneyMapper money;

  public RefundMapper(MoneyMapper money) {
    this.money = money;
  }

  public RefundView toDto(Refund refund) {
    return new RefundView(
        refund.getId(),
        refund.getChargeId(),
        money.toWire(refund.getAmount()),
        refund.getState(),
        refund.getReason());
  }
}
