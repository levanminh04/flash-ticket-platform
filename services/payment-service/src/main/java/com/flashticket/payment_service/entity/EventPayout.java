package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_event_payouts", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class EventPayout {
  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "organizer_subject", nullable = false)
  private String organizerSubject;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @Column(name = "valid_gross", nullable = false, precision = 19, scale = 0)
  private BigDecimal validGross;

  @Column(name = "valid_refunds", nullable = false, precision = 19, scale = 0)
  private BigDecimal validRefunds;

  @Column(name = "fee_amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal feeAmount;

  @Column(name = "net_amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal netAmount;

  @Column(name = "fee_rate", nullable = false, precision = 9, scale = 8)
  private BigDecimal feeRate;

  @Column(name = "source_fee_version", nullable = false)
  private Long sourceFeeVersion;

  @Column(name = "event_ends_at", nullable = false)
  private Instant eventEndsAt;

  @Column(name = "payment_pending_count", nullable = false)
  private Long paymentPendingCount;

  @Column(name = "refund_pending_count", nullable = false)
  private Long refundPendingCount;

  @Column(name = "as_of", nullable = false)
  private Instant asOf;

  @Column(name = "reconciled_by", nullable = true)
  private String reconciledBy;

  @Column(name = "reconciled_at", nullable = true)
  private Instant reconciledAt;

  @Column(name = "reconciliation_evidence", nullable = true)
  private String reconciliationEvidence;

  @Column(name = "paid_by", nullable = true)
  private String paidBy;

  @Column(name = "paid_at", nullable = true)
  private Instant paidAt;

  // Version null trên row mới để Spring Data nhận biết INSERT.
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
