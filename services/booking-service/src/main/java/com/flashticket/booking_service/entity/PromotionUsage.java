package com.flashticket.booking_service.entity;

import com.flashticket.booking_service.enums.Currency;
import com.flashticket.booking_service.enums.PromotionUsageState;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tbl_promotion_usages", schema = "booking_schema")
@Getter
@Setter
@NoArgsConstructor
public class PromotionUsage {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "promotion_id", referencedColumnName = "promotion_id", nullable = false)
  private PromotionInventory promotion;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reservation_id", nullable = false)
  private Reservation reservation;

  @Column(name = "buyer_subject", nullable = false)
  private String buyerSubject;

  @Column(name = "generation", nullable = false)
  private Long generation;

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false)
  private PromotionUsageState state;

  @Column(name = "discount", nullable = false, precision = 19, scale = 0)
  private BigDecimal discount;

  @Enumerated(EnumType.STRING)
  @Column(name = "currency", nullable = false)
  private Currency currency;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;
}
