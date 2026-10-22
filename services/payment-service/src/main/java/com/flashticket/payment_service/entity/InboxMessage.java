package com.flashticket.payment_service.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;
import com.flashticket.payment_service.entity.id.*;

@Entity
@Table(name="tbl_inbox_messages", schema="payment_schema")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class InboxMessage {
@EmbeddedId @Setter private InboxMessageId id;
@Setter
@Column(name="payload_hash", nullable=false ,columnDefinition="\"payment_schema\".\"nonempty_text\"")
private String payloadHash;
@Setter
@Column(name="processed_at", nullable=false)
private Instant processedAt;
@Setter
@JdbcTypeCode(SqlTypes.JSON)
@Column(name="response_payload", nullable=true ,columnDefinition="jsonb")
private JsonNode responsePayload;
}
