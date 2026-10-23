package com.flashticket.payment_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
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
@Table(name = "tbl_payment_confirmations", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentConfirmation {
  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "charge_id", nullable = false)
  private UUID chargeId;

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

  @Column(name = "confirmed_at", nullable = false)
  private Instant confirmedAt;

  @Setter(AccessLevel.NONE)
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "amount",
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
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "charge_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "amount",
        referencedColumnName = "amount",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "currency",
        referencedColumnName = "currency",
        insertable = false,
        updatable = false)
  })
  private Charge charge;

  @Setter(AccessLevel.NONE)
  @OneToMany(mappedBy = "confirmation", fetch = FetchType.LAZY)
  private List<EventRefundItem> refundItems = new ArrayList<>();
}
