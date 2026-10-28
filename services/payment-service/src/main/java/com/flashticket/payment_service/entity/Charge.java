package com.flashticket.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
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

@Entity
@Table(name = "tbl_charges", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Charge {
  public static Charge received(
      PaymentAttempt attempt, String transactionNo, Instant chargedAt, Instant recordedAt) {
    var charge = new Charge();
    charge.id = UUID.randomUUID();
    charge.attemptId = attempt.getId();
    charge.orderId = attempt.getOrderId();
    charge.provider = attempt.getProvider();
    charge.merchantAccount = attempt.getMerchantAccount();
    charge.providerChargeId = transactionNo;
    charge.amount = attempt.getExpectedAmount();
    charge.currency = attempt.getCurrency();
    charge.chargedAt = chargedAt;
    charge.recordedAt = recordedAt;
    return charge;
  }

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

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
      name = "provider_charge_id",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String providerChargeId;

  @Column(
      name = "amount",
      nullable = false,
      precision = 19,
      scale = 0,
      columnDefinition = "\"payment_schema\".\"money_amount\"")
  private BigDecimal amount;

  @Column(
      name = "currency",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"currency_code\"")
  private String currency;

  @Column(name = "charged_at", nullable = false)
  private Instant chargedAt;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Setter(AccessLevel.NONE)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "attempt_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "provider",
        referencedColumnName = "provider",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "merchant_account",
        referencedColumnName = "merchant_account",
        insertable = false,
        updatable = false)
  })
  private PaymentAttempt attempt;

  @Setter(AccessLevel.NONE)
  @OneToOne(mappedBy = "charge", fetch = FetchType.LAZY)
  private PaymentConfirmation confirmation;

  @Setter(AccessLevel.NONE)
  @OneToOne(mappedBy = "charge", fetch = FetchType.LAZY)
  private Refund refund;

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "charge", fetch = FetchType.LAZY)
  private List<PaymentSaga> sagas = new ArrayList<>();
}
