package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.enums.EventRefundItemState;
import com.flashticket.payment_service.entity.id.EventRefundItemId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_event_refund_items", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRefundItem {
  @EmbeddedId @Setter private EventRefundItemId id;

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

  @Setter(AccessLevel.NONE)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "run_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "event_id",
        referencedColumnName = "event_id",
        insertable = false,
        updatable = false)
  })
  private EventRefundRun run;

  @Setter(AccessLevel.NONE)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "event_id",
        referencedColumnName = "event_id",
        insertable = false,
        updatable = false)
  })
  private OrderPaymentSnapshot orderSnapshot;

  @Setter(AccessLevel.NONE)
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumns({
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "confirmed_charge_id",
        referencedColumnName = "charge_id",
        insertable = false,
        updatable = false)
  })
  private PaymentConfirmation confirmation;

  @Setter(AccessLevel.NONE)
  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumns({
    @JoinColumn(
        name = "refund_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "confirmed_charge_id",
        referencedColumnName = "charge_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false)
  })
  private Refund refund;
}
