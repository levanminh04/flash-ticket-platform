package com.flashticket.booking_service.entity;

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
public class PurchaseLimitId implements Serializable {
  private static final long serialVersionUID = 1L;
  private UUID eventId;
  private String buyerSubject;

  @Override
  public boolean equals(Object o) {
    return o instanceof PurchaseLimitId id
        && Objects.equals(eventId, id.eventId)
        && Objects.equals(buyerSubject, id.buyerSubject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventId, buyerSubject);
  }
}
