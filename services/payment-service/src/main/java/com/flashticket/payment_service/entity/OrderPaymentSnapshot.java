package com.flashticket.payment_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "tbl_order_payment_snapshots", schema = "payment_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaymentSnapshot {
  public OrderPaymentSnapshot(
      UUID orderId,
      UUID eventId,
      String buyerSubject,
      BigDecimal expectedAmount,
      String currency,
      Instant expiresAt,
      Long sourceOrderVersion,
      JsonNode purchaseSnapshot,
      String snapshotHash,
      Instant receivedAt) {
    this.orderId = orderId;
    this.eventId = eventId;
    this.buyerSubject = buyerSubject;
    this.expectedAmount = expectedAmount;
    this.currency = currency;
    this.expiresAt = expiresAt;
    this.sourceOrderVersion = sourceOrderVersion;
    this.purchaseSnapshot = purchaseSnapshot;
    this.snapshotHash = snapshotHash;
    this.receivedAt = receivedAt;
  }

  @Setter
  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Setter
  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Setter
  @Column(
      name = "buyer_subject",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String buyerSubject;

  @Setter
  @Column(
      name = "expected_amount",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal expectedAmount;

  @Setter
  @Column(
      name = "currency",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"currency_code\"")
  private String currency;

  @Setter
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Setter
  @Column(name = "source_order_version", nullable = false)
  private Long sourceOrderVersion;

  @Setter
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "purchase_snapshot", nullable = false, columnDefinition = "jsonb")
  private JsonNode purchaseSnapshot;

  @Setter
  @Column(
      name = "snapshot_hash",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String snapshotHash;

  @Setter
  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @OneToMany(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private List<PaymentAttempt> attempts = new ArrayList<>();

  @OneToOne(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private PaymentConfirmation confirmation;

  @OneToMany(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private List<EventRefundItem> refundItems = new ArrayList<>();

  @OneToOne(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private PaymentSaga saga;
}
