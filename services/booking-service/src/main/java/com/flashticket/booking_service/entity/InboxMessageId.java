package com.flashticket.booking_service.entity;

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

  @Column(name = "consumer_name", nullable = false)
  private String consumerName;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;

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
