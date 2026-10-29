package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_refunds", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class Refund {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "charge_id", nullable = false)
  private UUID chargeId;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

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

  // Version null trên row mới để Spring Data nhận biết INSERT.
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
