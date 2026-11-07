package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.PaymentSaga;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentSagaRepository extends JpaRepository<PaymentSaga, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from PaymentSaga s where s.orderId = :orderId")
  java.util.Optional<PaymentSaga> findForUpdateByOrderId(@Param("orderId") UUID orderId);

  @Query(
      "select s.orderId from PaymentSaga s where s.state in (com.flashticket.payment_service.entity.enums.PaymentSagaState.WAITING_BOOKING, com.flashticket.payment_service.entity.enums.PaymentSagaState.WAITING_TICKETS) and s.nextAttemptAt <= :dueAt order by s.nextAttemptAt, s.orderId")
  java.util.List<UUID> findRecoverableOrderIds(
      @Param("dueAt") java.time.Instant dueAt, org.springframework.data.domain.Pageable pageable);
}
