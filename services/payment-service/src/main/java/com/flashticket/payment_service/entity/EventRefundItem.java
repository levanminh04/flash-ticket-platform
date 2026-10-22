package com.flashticket.payment_service.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import com.flashticket.payment_service.entity.enums.*;
import com.flashticket.payment_service.entity.id.*;

@Entity
@Table(name="tbl_event_refund_items", schema="payment_schema")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class EventRefundItem {
@EmbeddedId @Setter private EventRefundItemId id;
@Setter
@Column(name="event_id", nullable=false)
private UUID eventId;
@Setter
@Column(name="confirmed_charge_id", nullable=true)
private UUID confirmedChargeId;
@Setter
@Column(name="refund_id", nullable=true)
private UUID refundId;
@Setter
@Enumerated(EnumType.STRING)
@Column(name="state", nullable=false)
private EventRefundItemState state;
@Setter
@Column(name="last_error_code", nullable=true)
private String lastErrorCode;
@Setter
@Column(name="updated_at", nullable=false)
private Instant updatedAt;
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumns({@JoinColumn(name="run_id", referencedColumnName="id", insertable=false, updatable=false),
@JoinColumn(name="event_id", referencedColumnName="event_id", insertable=false, updatable=false)})
private EventRefundRun run;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumns({@JoinColumn(name="order_id", referencedColumnName="order_id", insertable=false, updatable=false),
@JoinColumn(name="event_id", referencedColumnName="event_id", insertable=false, updatable=false)})
private OrderPaymentSnapshot orderSnapshot;

@ManyToOne(fetch = FetchType.LAZY, optional = true)
@JoinColumns({@JoinColumn(name="order_id", referencedColumnName="order_id", insertable=false, updatable=false),
@JoinColumn(name="confirmed_charge_id", referencedColumnName="charge_id", insertable=false, updatable=false)})
private PaymentConfirmation confirmation;

@ManyToOne(fetch = FetchType.LAZY, optional = true)
@JoinColumns({@JoinColumn(name="refund_id", referencedColumnName="id", insertable=false, updatable=false),
@JoinColumn(name="confirmed_charge_id", referencedColumnName="charge_id", insertable=false, updatable=false),
@JoinColumn(name="order_id", referencedColumnName="order_id", insertable=false, updatable=false)})
private Refund refund;

}
