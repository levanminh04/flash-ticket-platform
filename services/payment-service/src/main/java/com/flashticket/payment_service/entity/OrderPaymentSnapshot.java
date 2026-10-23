package com.flashticket.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "tbl_order_payment_snapshots", schema = "payment_schema")
@Getter
@Setter
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

  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(
      name = "buyer_subject",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String buyerSubject;

  @Column(
      name = "expected_amount",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal expectedAmount;

  @Column(
      name = "currency",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"currency_code\"")
  private String currency;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "source_order_version", nullable = false)
  private Long sourceOrderVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "purchase_snapshot", nullable = false, columnDefinition = "jsonb")
  private JsonNode purchaseSnapshot;

  @Column(
      name = "snapshot_hash",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String snapshotHash;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private List<PaymentAttempt> attempts = new ArrayList<>();

  @Setter(AccessLevel.NONE)
  @OneToOne(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private PaymentConfirmation confirmation;

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private List<EventRefundItem> refundItems = new ArrayList<>();

  @Setter(AccessLevel.NONE)
  @OneToOne(mappedBy = "orderSnapshot", fetch = FetchType.LAZY)
  private PaymentSaga saga;
}
