package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tbl_payment_attempts", schema = "payment_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {
  @Setter
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Setter
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Setter
  @Column(
      name = "provider",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String provider;

  @Setter
  @Column(
      name = "merchant_account",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String merchantAccount;

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
  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Setter
  @Column(name = "finished_at", nullable = true)
  private Instant finishedAt;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private PaymentAttemptState state;

  @Setter
  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  @Setter
  @Column(
      name = "request_hash",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String requestHash;

  @Setter
  @Column(name = "payment_url", nullable = true)
  private String paymentUrl;

  @Setter
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "expected_amount",
        referencedColumnName = "expected_amount",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "currency",
        referencedColumnName = "currency",
        insertable = false,
        updatable = false)
  })
  private OrderPaymentSnapshot orderSnapshot;

  @OneToMany(mappedBy = "attempt", fetch = FetchType.LAZY)
  private List<Charge> charges = new ArrayList<>();
}
