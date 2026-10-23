package com.flashticket.payment_service.entity.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class EventRefundItemId implements Serializable {
  private static final long serialVersionUID = 1L;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;
}
