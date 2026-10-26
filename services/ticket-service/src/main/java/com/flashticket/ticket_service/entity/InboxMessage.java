package com.flashticket.ticket_service.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "tbl_inbox_messages", schema = "ticket_schema")
@Getter
@Setter
@NoArgsConstructor
public class InboxMessage {
  @EmbeddedId private InboxMessageId id;

  @Column(name = "payload_hash", nullable = false, columnDefinition = "nonempty_text")
  private String payloadHash;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_payload", columnDefinition = "jsonb")
  private JsonNode responsePayload;
}
