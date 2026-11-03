package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.EventPayout;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventPayoutRepository extends JpaRepository<EventPayout, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from EventPayout p where p.eventId = :eventId")
  Optional<EventPayout> findForUpdateByEventId(@Param("eventId") UUID eventId);
}
