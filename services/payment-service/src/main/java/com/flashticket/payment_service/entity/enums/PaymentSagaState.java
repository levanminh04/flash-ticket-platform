package com.flashticket.payment_service.entity.enums;

public enum PaymentSagaState {
  WAITING_BOOKING,
  WAITING_TICKETS,
  COMPLETED,
  COMPENSATING,
  COMPENSATED
}
