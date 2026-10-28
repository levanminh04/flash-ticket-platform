package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.OrderPaymentSnapshot;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentSnapshotRepository extends JpaRepository<OrderPaymentSnapshot, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select s from OrderPaymentSnapshot s where s.orderId = :orderId")
  Optional<OrderPaymentSnapshot> findForUpdateByOrderId(
      @org.springframework.data.repository.query.Param("orderId") UUID orderId);

  interface OrderPaymentAccess {
    UUID getOrderId();

    String getBuyerSubject();

    Instant getExpiresAt();
  }

  @org.springframework.data.jpa.repository.Query(
      "select s.orderId as orderId, s.buyerSubject as buyerSubject, s.expiresAt as expiresAt from OrderPaymentSnapshot s where s.orderId = :orderId")
  Optional<OrderPaymentAccess> findAccessByOrderId(
      @org.springframework.data.repository.query.Param("orderId") UUID orderId);
}
