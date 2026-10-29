package com.flashticket.payment_service.entity;

import com.flashticket.payment_service.enums.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tbl_payment_attempts", schema = "payment_schema")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAttempt {
  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "provider", nullable = false)
  private String provider;

  @Column(name = "merchant_account", nullable = false)
  private String merchantAccount;

  @Column(name = "expected_amount", nullable = false, precision = 19, scale = 0)
  private BigDecimal expectedAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "finished_at", nullable = true)
  private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private PaymentAttemptState state;

  @Column(name = "request_key", nullable = false)
  private UUID requestKey;

  @Column(name = "request_hash", nullable = false)
  private String requestHash;

  @Column(name = "payment_url", nullable = true)
  private String paymentUrl;

  // Version null trên row mới để Spring Data nhận biết INSERT.
  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
