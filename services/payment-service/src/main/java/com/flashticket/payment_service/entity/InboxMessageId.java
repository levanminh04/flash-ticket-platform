package com.flashticket.payment_service.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class InboxMessageId implements Serializable {
  private static final long serialVersionUID = 1L;

  @Column(name = "consumer_name", nullable = false)
  private String consumerName;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;
}
