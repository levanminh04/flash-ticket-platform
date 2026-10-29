package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_payment_sagas", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class PaymentSaga {
  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "charge_id", nullable = false)
  private UUID chargeId;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private PaymentSagaState state;

  @Column(name = "command_id", nullable = false)
  private UUID commandId;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "last_error_code", nullable = true)
  private String lastErrorCode;

  // Version null trên row mới để Spring Data nhận biết INSERT.
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
