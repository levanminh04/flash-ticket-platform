package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_payment_confirmations", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class PaymentConfirmation {
  @Id
  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "charge_id", nullable = false)
  private UUID chargeId;

  @Column(name = "amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @Column(name = "confirmed_at", nullable = false)
  private Instant confirmedAt;
}
