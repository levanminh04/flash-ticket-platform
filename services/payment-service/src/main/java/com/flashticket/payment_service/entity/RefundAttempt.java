package com.flashticket.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_refund_attempts", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundAttempt {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "refund_id", nullable = false)
  private UUID refundId;

  @Column(
      name = "request_key",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String requestKey;

  @Column(
      name = "provider_refund_id",
      nullable = true,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String providerRefundId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = true)
  private Instant finishedAt;

  @Column(
      name = "outcome",
      nullable = true,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String outcome;

  @Setter(AccessLevel.NONE)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "refund_id",
      referencedColumnName = "id",
      insertable = false,
      updatable = false)
  private Refund refund;
}
