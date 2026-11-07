package com.flashticket.booking_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_purchase_limits", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseLimit {
  @EmbeddedId
  @AttributeOverrides({
    @AttributeOverride(name = "eventId", column = @Column(name = "event_id", nullable = false)),
    @AttributeOverride(
        name = "buyerSubject",
        column = @Column(name = "buyer_subject", nullable = false))
  })
  private PurchaseLimitId id;

  @Column(name = "configured_limit", nullable = false)
  private Integer configuredLimit;

  @Column(name = "held", nullable = false)
  private Integer held;

  @Column(name = "purchased", nullable = false)
  private Integer purchased;

  @Column(name = "source_config_version", nullable = false)
  private Long sourceConfigVersion;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
