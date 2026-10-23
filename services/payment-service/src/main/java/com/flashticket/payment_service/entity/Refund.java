package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.enums.RefundReason;
import com.flashticket.payment_service.entity.enums.RefundState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_refunds", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "charge_id", nullable = false)
  private UUID chargeId;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(
      name = "amount",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal amount;

  @Column(
      name = "currency",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"currency_code\"")
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false)
  private RefundReason reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private RefundState state;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at", nullable = true)
  private Instant completedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Setter(AccessLevel.NONE)
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "charge_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "amount",
        referencedColumnName = "amount",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "currency",
        referencedColumnName = "currency",
        insertable = false,
        updatable = false)
  })
  private Charge charge;

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "refund", fetch = FetchType.LAZY)
  private List<RefundAttempt> attempts = new ArrayList<>();

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "refund", fetch = FetchType.LAZY)
  private List<EventRefundItem> items = new ArrayList<>();
}
