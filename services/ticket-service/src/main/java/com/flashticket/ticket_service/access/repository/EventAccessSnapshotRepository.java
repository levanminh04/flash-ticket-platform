package com.flashticket.ticket_service.access.repository;

import com.flashticket.ticket_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAccessSnapshotRepository extends JpaRepository<EventAccessSnapshot, UUID> {}
