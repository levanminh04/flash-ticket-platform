package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.EventFinanceSnapshot;
import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventFinanceSnapshotRepository extends JpaRepository<EventFinanceSnapshot, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from EventFinanceSnapshot e where e.eventId = :eventId")
  java.util.Optional<EventFinanceSnapshot> findForUpdateByEventId(@Param("eventId") UUID eventId);
}
