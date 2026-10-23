package com.flashticket.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_event_payouts", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventPayout {
  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(
      name = "organizer_subject",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String organizerSubject;

  @Column(
      name = "currency",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"currency_code\"")
  private String currency;

  @Column(
      name = "valid_gross",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal validGross;

  @Column(
      name = "valid_refunds",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal validRefunds;

  @Column(
      name = "fee_amount",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal feeAmount;

  @Column(
      name = "net_amount",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
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

  @Column(
      name = "reconciled_by",
      nullable = true,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String reconciledBy;

  @Column(name = "reconciled_at", nullable = true)
  private Instant reconciledAt;

  @Column(
      name = "reconciliation_evidence",
      nullable = true,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String reconciliationEvidence;

  @Column(
      name = "paid_by",
      nullable = true,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String paidBy;

  @Column(name = "paid_at", nullable = true)
  private Instant paidAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
