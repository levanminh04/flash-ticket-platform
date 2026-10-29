package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_order_payment_snapshots", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class OrderPaymentSnapshot {
  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "buyer_subject", nullable = false)
  private String buyerSubject;

  @Column(name = "expected_amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal expectedAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "source_order_version", nullable = false)
  private Long sourceOrderVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "purchase_snapshot", nullable = false, columnDefinition = "jsonb")
  private PurchaseSnapshot purchaseSnapshot;

  @Column(name = "snapshot_hash", nullable = false)
  private String snapshotHash;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;
}
