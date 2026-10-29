package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_event_refund_items", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class EventRefundItem {
  @EmbeddedId private EventRefundItemId id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "confirmed_charge_id", nullable = true)
  private UUID confirmedChargeId;

  @Column(name = "refund_id", nullable = true)
  private UUID refundId;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private EventRefundItemState state;

  @Column(name = "last_error_code", nullable = true)
  private String lastErrorCode;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
