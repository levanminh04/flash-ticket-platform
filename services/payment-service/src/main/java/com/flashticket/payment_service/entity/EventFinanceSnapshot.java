package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_event_finance_snapshots", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class EventFinanceSnapshot {
  @Id
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "organizer_subject", nullable = true)
  private String organizerSubject;

  @Column(name = "event_ends_at", nullable = true)
  private Instant eventEndsAt;

  @Column(name = "fee_rate", nullable = true, precision = 9, scale = 8)
  private BigDecimal feeRate;

  @Column(name = "source_fee_version", nullable = true)
  private Long sourceFeeVersion;

  @Column(name = "cancellation_id", nullable = true)
  private UUID cancellationId;

  @Column(name = "cancelled_at", nullable = true)
  private Instant cancelledAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;
}
