package com.flashticket.booking_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class OrderItemSeatId implements Serializable {
  private static final long serialVersionUID = 1L;

  @Column(name = "order_item_id", nullable = false)
  private UUID orderItemId;

  @Column(name = "seat_id", nullable = false)
  private UUID seatId;

  @Override
  public boolean equals(Object o) {
    return o instanceof OrderItemSeatId id
        && Objects.equals(orderItemId, id.orderItemId)
        && Objects.equals(seatId, id.seatId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderItemId, seatId);
  }
}
