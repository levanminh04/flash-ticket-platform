package com.flashticket.payment_service.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name="tbl_payment_confirmations", schema="payment_schema")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class PaymentConfirmation {
@Setter
@Id
@Column(name="order_id", nullable=false)
private UUID orderId;
@Setter
@Column(name="charge_id", nullable=false)
private UUID chargeId;
@Setter
@Column(name="amount", nullable=false ,precision=19, scale=0, columnDefinition="\"payment_schema\".\"money_amount\"")
private BigDecimal amount;
@Setter
@Column(name="currency", nullable=false ,columnDefinition="\"payment_schema\".\"currency_code\"")
private String currency;
@Setter
@Column(name="confirmed_at", nullable=false)
private Instant confirmedAt;
@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumns({@JoinColumn(name="order_id", referencedColumnName="order_id", insertable=false, updatable=false),
@JoinColumn(name="amount", referencedColumnName="expected_amount", insertable=false, updatable=false),
@JoinColumn(name="currency", referencedColumnName="currency", insertable=false, updatable=false)})
private OrderPaymentSnapshot orderSnapshot;

@OneToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumns({@JoinColumn(name="charge_id", referencedColumnName="id", insertable=false, updatable=false),
@JoinColumn(name="order_id", referencedColumnName="order_id", insertable=false, updatable=false),
@JoinColumn(name="amount", referencedColumnName="amount", insertable=false, updatable=false),
@JoinColumn(name="currency", referencedColumnName="currency", insertable=false, updatable=false)})
private Charge charge;

@OneToMany(mappedBy="confirmation", fetch=FetchType.LAZY)
private List<EventRefundItem> refundItems = new ArrayList<>();

}
