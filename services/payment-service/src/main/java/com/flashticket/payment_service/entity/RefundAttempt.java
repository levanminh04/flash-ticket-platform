package com.flashticket.payment_service.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name="tbl_refund_attempts", schema="payment_schema")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class RefundAttempt {
@Setter
@Id
@Column(name="id", nullable=false)
private UUID id;
@Setter
@Column(name="refund_id", nullable=false)
private UUID refundId;
@Setter
@Column(name="request_key", nullable=false ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String requestKey;
@Setter
@Column(name="provider_refund_id", nullable=true ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String providerRefundId;
@Setter
@Column(name="started_at", nullable=false)
private Instant startedAt;
@Setter
@Column(name="finished_at", nullable=true)
private Instant finishedAt;
@Setter
@Column(name="outcome", nullable=true ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String outcome;
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name="refund_id", referencedColumnName="id", insertable=false, updatable=false)
private Refund refund;

}
