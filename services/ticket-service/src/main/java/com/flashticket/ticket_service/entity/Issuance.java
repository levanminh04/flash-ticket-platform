package com.flashticket.ticket_service.entity;

import com.flashticket.ticket_service.dto.snapshot.*;
import com.flashticket.ticket_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tbl_issuances", schema = "ticket_schema")
@Getter
@Setter
@NoArgsConstructor
public class Issuance {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "buyer_subject", nullable = false, columnDefinition = "nonempty_text")
  private String buyerSubject;

  @Column(name = "payment_confirmation_id", nullable = false, columnDefinition = "nonempty_text")
  private String paymentConfirmationId;

  @Column(name = "customer_email", nullable = false, columnDefinition = "nonempty_text")
  private String customerEmail;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "purchase_snapshot", nullable = false, columnDefinition = "jsonb")
  private PurchaseSnapshot purchaseSnapshot;

  @Column(name = "input_hash", nullable = false, columnDefinition = "nonempty_text")
  private String inputHash;

  @Column(name = "expected_ticket_count", nullable = false)
  private Integer expectedTicketCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private IssuanceState state;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "completed_at", nullable = true)
  private Instant completedAt;

  @Column(name = "failed_at", nullable = true)
  private Instant failedAt;

  @Column(name = "failure_code", nullable = true, columnDefinition = "nonempty_text")
  private String failureCode;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @OneToMany(mappedBy = "issuance", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
  private List<Ticket> tickets = new ArrayList<>();

  public void addTicket(Ticket ticket) {
    Objects.requireNonNull(ticket, "ticket");
    Objects.requireNonNull(eventId, "eventId");
    tickets.add(ticket);
    ticket.setIssuance(this);
    ticket.setEventId(eventId);
  }
}
