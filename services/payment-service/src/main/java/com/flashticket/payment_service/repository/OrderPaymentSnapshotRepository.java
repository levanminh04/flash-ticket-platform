package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.OrderPaymentSnapshot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderPaymentSnapshotRepository extends JpaRepository<OrderPaymentSnapshot, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from OrderPaymentSnapshot s where s.orderId = :orderId")
  Optional<OrderPaymentSnapshot> findForUpdateByOrderId(@Param("orderId") UUID orderId);

  interface OrderPaymentAccess {
    UUID getOrderId();

    String getBuyerSubject();

    Instant getExpiresAt();
  }

  @Query(
      "select s.orderId as orderId, s.buyerSubject as buyerSubject, s.expiresAt as expiresAt from OrderPaymentSnapshot s where s.orderId = :orderId")
  Optional<OrderPaymentAccess> findAccessByOrderId(@Param("orderId") UUID orderId);
}
