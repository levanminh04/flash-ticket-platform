package com.flashticket.booking_service.order.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class BuyerOrdersChangedEvent {
  private final String buyerSubject;
}
