package com.flashticket.payment_service.enums;

public enum PaymentSagaState {
  WAITING_BOOKING,
  WAITING_TICKETS,
  COMPLETED,
  COMPENSATING,
  COMPENSATED
}
