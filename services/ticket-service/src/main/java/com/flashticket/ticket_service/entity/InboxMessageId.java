package com.flashticket.ticket_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class InboxMessageId implements Serializable {
  private static final long serialVersionUID = 1L;

  @Column(name = "consumer_name", nullable = false, columnDefinition = "nonempty_text")
  private String consumerName;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  public InboxMessageId(String consumerName, UUID messageId) {
    this.consumerName = consumerName;
    this.messageId = messageId;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof InboxMessageId id
        && Objects.equals(consumerName, id.consumerName)
        && Objects.equals(messageId, id.messageId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(consumerName, messageId);
  }
}
