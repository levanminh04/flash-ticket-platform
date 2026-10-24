package com.flashticket.booking_service.availability.repository;

import com.flashticket.booking_service.entity.EventSalesSnapshot;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSalesSnapshotRepository extends JpaRepository<EventSalesSnapshot, UUID> {}
