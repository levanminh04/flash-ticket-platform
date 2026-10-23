package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.entity.enums.PaymentSagaState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_payment_sagas", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Setter(AccessLevel.NONE)
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "order_id",
      referencedColumnName = "order_id",
      insertable = false,
      updatable = false)
  private OrderPaymentSnapshot orderSnapshot;

  @Setter(AccessLevel.NONE)
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
