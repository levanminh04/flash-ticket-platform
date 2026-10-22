package com.flashticket.payment_service.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name="tbl_event_payouts", schema="payment_schema")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class EventPayout {
@Setter
@Id
@Column(name="event_id", nullable=false)
private UUID eventId;
@Setter
@Column(name="organizer_subject", nullable=false ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String organizerSubject;
@Setter
@Column(name="currency", nullable=false ,columnDefinition="\"payment_schema\".\"currency_code\"")
private String currency;
@Setter
@Column(name="valid_gross", nullable=false ,precision=19, scale=0, columnDefinition="\"payment_schema\".\"money_amount\"")
private BigDecimal validGross;
@Setter
@Column(name="valid_refunds", nullable=false ,precision=19, scale=0, columnDefinition="\"payment_schema\".\"money_amount\"")
private BigDecimal validRefunds;
@Setter
@Column(name="fee_amount", nullable=false ,precision=19, scale=0, columnDefinition="\"payment_schema\".\"money_amount\"")
private BigDecimal feeAmount;
@Setter
@Column(name="net_amount", nullable=false ,precision=19, scale=0, columnDefinition="\"payment_schema\".\"money_amount\"")
private BigDecimal netAmount;
@Setter
@Column(name="fee_rate", nullable=false ,precision=9, scale=8)
private BigDecimal feeRate;
@Setter
@Column(name="source_fee_version", nullable=false)
private Long sourceFeeVersion;
@Setter
@Column(name="event_ends_at", nullable=false)
private Instant eventEndsAt;
@Setter
@Column(name="payment_pending_count", nullable=false)
private Long paymentPendingCount;
@Setter
@Column(name="refund_pending_count", nullable=false)
private Long refundPendingCount;
@Setter
@Column(name="as_of", nullable=false)
private Instant asOf;
@Setter
@Column(name="reconciled_by", nullable=true ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String reconciledBy;
@Setter
@Column(name="reconciled_at", nullable=true)
private Instant reconciledAt;
@Setter
@Column(name="reconciliation_evidence", nullable=true ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String reconciliationEvidence;
@Setter
@Column(name="paid_by", nullable=true ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String paidBy;
@Setter
@Column(name="paid_at", nullable=true)
private Instant paidAt;
@Setter
@Version
@Column(name="row_version", nullable=false)
private Long rowVersion;
}
