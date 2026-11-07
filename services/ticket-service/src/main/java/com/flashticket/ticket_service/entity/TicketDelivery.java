package com.flashticket.ticket_service.entity;

import com.flashticket.ticket_service.dto.snapshot.*;
import com.flashticket.ticket_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tbl_ticket_deliveries", schema = "ticket_schema")
@Getter
@Setter
@NoArgsConstructor
public class TicketDelivery {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "issuance_id", nullable = false)
  private UUID issuanceId;

  @Column(name = "requested_by", nullable = false, columnDefinition = "nonempty_text")
  private String requestedBy;

  @Column(name = "channel", nullable = false, columnDefinition = "nonempty_text")
  private String channel;

  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  @Column(name = "request_hash", nullable = false, columnDefinition = "nonempty_text")
  private String requestHash;

  @Column(name = "recipient_email", nullable = false, columnDefinition = "nonempty_text")
  private String recipientEmail;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "finished_at", nullable = true)
  private Instant finishedAt;

  @Column(name = "outcome", nullable = true, columnDefinition = "nonempty_text")
  private String outcome;

  @Column(name = "failure_code", nullable = true)
  private String failureCode;

  @Column(name = "attempts", nullable = false)
  private Integer attempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;
}
