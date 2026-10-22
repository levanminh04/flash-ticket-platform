package com.flashticket.payment_service.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name="tbl_event_finance_snapshots", schema="payment_schema")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class EventFinanceSnapshot {
@Setter
@Id
@Column(name="event_id", nullable=false)
private UUID eventId;
@Setter
@Column(name="organizer_subject", nullable=true ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String organizerSubject;
@Setter
@Column(name="event_ends_at", nullable=true)
private Instant eventEndsAt;
@Setter
@Column(name="fee_rate", nullable=true ,precision=9, scale=8)
private BigDecimal feeRate;
@Setter
@Column(name="source_fee_version", nullable=true)
private Long sourceFeeVersion;
@Setter
@Column(name="cancellation_id", nullable=true)
private UUID cancellationId;
@Setter
@Column(name="cancelled_at", nullable=true)
private Instant cancelledAt;
@Setter
@Column(name="received_at", nullable=false)
private Instant receivedAt;
}
