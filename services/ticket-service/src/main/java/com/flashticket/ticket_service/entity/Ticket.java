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
@Table(name = "tbl_tickets", schema = "ticket_schema")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "order_item_id", nullable = false)
  private UUID orderItemId;

  @Column(name = "ordinal", nullable = false)
  private Integer ordinal;

  @Column(name = "holder_subject", nullable = false, columnDefinition = "nonempty_text")
  private String holderSubject;

  @Column(name = "ticket_type_id", nullable = false)
  private UUID ticketTypeId;

  @Column(name = "sector_id", nullable = true)
  private UUID sectorId;

  @Column(name = "seat_id", nullable = true)
  private UUID seatId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "display_snapshot", nullable = false, columnDefinition = "jsonb")
  private TicketDisplaySnapshot displaySnapshot;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private TicketState state;

  @Column(name = "qr_token_ciphertext", nullable = false, columnDefinition = "bytea")
  private byte[] qrTokenCiphertext;

  @Column(name = "qr_key_version", nullable = false, columnDefinition = "nonempty_text")
  private String qrKeyVersion;

  @Column(name = "qr_token_hash", nullable = false, columnDefinition = "nonempty_text")
  private String qrTokenHash;

  @Column(name = "used_at", nullable = true)
  private Instant usedAt;

  @Column(name = "used_by", nullable = true, columnDefinition = "nonempty_text")
  private String usedBy;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "issuance_id", nullable = false)
  private Issuance issuance;

  public byte[] getQrTokenCiphertext() {
    return qrTokenCiphertext == null ? null : qrTokenCiphertext.clone();
  }

  public void setQrTokenCiphertext(byte[] value) {
    qrTokenCiphertext = value == null ? null : value.clone();
  }
}
