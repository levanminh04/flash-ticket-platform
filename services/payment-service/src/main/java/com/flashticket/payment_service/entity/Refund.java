package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tbl_refunds", schema = "payment_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {
  @Setter
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Setter
  @Column(name = "charge_id", nullable = false)
  private UUID chargeId;

  @Setter
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Setter
  @Column(
      name = "amount",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal amount;

  @Setter
  @Column(
      name = "currency",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"currency_code\"")
  private String currency;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false)
  private RefundReason reason;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private RefundState state;

  @Setter
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Setter
  @Column(name = "completed_at", nullable = true)
  private Instant completedAt;

  @Setter
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

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

  @OneToMany(mappedBy = "refund", fetch = FetchType.LAZY)
  private List<RefundAttempt> attempts = new ArrayList<>();

  @OneToMany(mappedBy = "refund", fetch = FetchType.LAZY)
  private List<EventRefundItem> items = new ArrayList<>();
}
