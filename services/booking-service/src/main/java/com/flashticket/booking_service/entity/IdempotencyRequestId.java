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
public class IdempotencyRequestId implements Serializable {
  private static final long serialVersionUID = 1L;

  @Column(name = "actor_subject", nullable = false)
  private String actorSubject;

  @Column(name = "operation", nullable = false)
  private String operation;

  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  @Override
  public boolean equals(Object o) {
    return o instanceof IdempotencyRequestId id
        && Objects.equals(actorSubject, id.actorSubject)
        && Objects.equals(operation, id.operation)
        && Objects.equals(requestKey, id.requestKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(actorSubject, operation, requestKey);
  }
}
