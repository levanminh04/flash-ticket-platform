package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_charges", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class Charge {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "attempt_id", nullable = false)
  private UUID attemptId;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "provider", nullable = false)
  private String provider;

  @Column(name = "merchant_account", nullable = false)
  private String merchantAccount;

  @Column(name = "provider_charge_id", nullable = false)
  private String providerChargeId;

  @Column(name = "amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @Column(name = "charged_at", nullable = false)
  private Instant chargedAt;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;
}
