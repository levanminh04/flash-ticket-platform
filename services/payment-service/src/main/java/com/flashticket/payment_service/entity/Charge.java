package com.flashticket.payment_service.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name="tbl_charges", schema="payment_schema")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Charge {
@Setter
@Id
@Column(name="id", nullable=false)
private UUID id;
@Setter
@Column(name="attempt_id", nullable=false)
private UUID attemptId;
@Setter
@Column(name="order_id", nullable=false)
private UUID orderId;
@Setter
@Column(name="provider", nullable=false ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String provider;
@Setter
@Column(name="merchant_account", nullable=false ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String merchantAccount;
@Setter
@Column(name="provider_charge_id", nullable=false ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String providerChargeId;
@Setter
@Column(name="amount", nullable=false ,precision=19, scale=0, columnDefinition="\"payment_schema\".\"money_amount\"")
private BigDecimal amount;
@Setter
@Column(name="currency", nullable=false ,columnDefinition="\"payment_schema\".\"currency_code\"")
private String currency;
@Setter
@Column(name="charged_at", nullable=false)
private Instant chargedAt;
@Setter
@Column(name="recorded_at", nullable=false)
private Instant recordedAt;
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumns({@JoinColumn(name="attempt_id", referencedColumnName="id", insertable=false, updatable=false),
@JoinColumn(name="order_id", referencedColumnName="order_id", insertable=false, updatable=false),
@JoinColumn(name="provider", referencedColumnName="provider", insertable=false, updatable=false),
@JoinColumn(name="merchant_account", referencedColumnName="merchant_account", insertable=false, updatable=false)})
private PaymentAttempt attempt;

@OneToOne(mappedBy="charge", fetch=FetchType.LAZY)
private PaymentConfirmation confirmation;

@OneToOne(mappedBy="charge", fetch=FetchType.LAZY)
private Refund refund;

@OneToMany(mappedBy="charge", fetch=FetchType.LAZY)
private List<PaymentSaga> sagas = new ArrayList<>();

}
