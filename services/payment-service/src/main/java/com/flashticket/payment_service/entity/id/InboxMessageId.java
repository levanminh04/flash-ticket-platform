package com.flashticket.payment_service.entity.id;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;
import lombok.*;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class InboxMessageId implements Serializable {
  private static final long serialVersionUID = 1L;

  @Column(
      name = "consumer_name",
      nullable = false,
      columnDefinition = "\"payment_schema\".\"nonempty_text\"")
  private String consumerName;

  @Column(name = "message_id", nullable = false)
  private UUID messageId;
}
