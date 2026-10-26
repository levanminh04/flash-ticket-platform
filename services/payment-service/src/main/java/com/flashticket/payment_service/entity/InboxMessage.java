package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.id.InboxMessageId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(name = "tbl_inbox_messages", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboxMessage {
  public static InboxMessage processed(
      InboxMessageId id, String hash, Instant processedAt, JsonNode response) {
    var message = new InboxMessage();
    message.id = id;
    message.payloadHash = hash;
    message.processedAt = processedAt;
    message.responsePayload = response;
    return message;
  }

  @EmbeddedId @Setter private InboxMessageId id;

  @Column(
      name = "payload_hash",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String payloadHash;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "response_payload", nullable = true, columnDefinition = "jsonb")
  private JsonNode responsePayload;
}
