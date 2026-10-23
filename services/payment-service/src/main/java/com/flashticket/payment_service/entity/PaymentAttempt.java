package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.enums.PaymentAttemptState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_payment_attempts", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(
      name = "provider",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String provider;

  @Column(
      name = "merchant_account",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String merchantAccount;

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

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = true)
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private PaymentAttemptState state;

  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  @Column(
      name = "request_hash",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String requestHash;

  @Column(name = "payment_url", nullable = true)
  private String paymentUrl;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Setter(AccessLevel.NONE)
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

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "attempt", fetch = FetchType.LAZY)
  private List<Charge> charges = new ArrayList<>();
}
