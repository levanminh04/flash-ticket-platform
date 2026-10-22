package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.enums.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "tbl_payment_sagas", schema = "payment_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentSaga {
  @Setter
  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Setter
  @Column(name = "charge_id", nullable = false)
  private UUID chargeId;

  @Setter
  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private PaymentSagaState state;

  @Setter
  @Column(name = "command_id", nullable = false)
  private UUID commandId;

  @Setter
  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Setter
  @Column(name = "last_error_code", nullable = true)
  private String lastErrorCode;

  @Setter
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Setter
  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Setter
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "order_id",
      referencedColumnName = "order_id",
      insertable = false,
      updatable = false)
  private OrderPaymentSnapshot orderSnapshot;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumns({
    @JoinColumn(
        name = "charge_id",
        referencedColumnName = "id",
        insertable = false,
        updatable = false),
    @JoinColumn(
        name = "order_id",
        referencedColumnName = "order_id",
        insertable = false,
        updatable = false)
  })
  private Charge charge;
}
