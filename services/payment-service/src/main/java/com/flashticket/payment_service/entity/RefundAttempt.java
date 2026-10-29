package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_refund_attempts", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class RefundAttempt {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "refund_id", nullable = false)
  private UUID refundId;

  @Column(name = "request_key", nullable = false)
  private String requestKey;

  @Column(name = "provider_refund_id", nullable = true)
  private String providerRefundId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = true)
  private Instant finishedAt;

  @Column(name = "outcome", nullable = true)
  private String outcome;
}
