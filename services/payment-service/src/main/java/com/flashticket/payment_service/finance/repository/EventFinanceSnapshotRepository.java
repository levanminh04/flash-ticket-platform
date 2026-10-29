package com.flashticket.payment_service.finance.repository;

import com.flashticket.payment_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventFinanceSnapshotRepository extends JpaRepository<EventFinanceSnapshot, UUID> {}
