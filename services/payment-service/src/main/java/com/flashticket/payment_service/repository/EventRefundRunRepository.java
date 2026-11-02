package com.flashticket.payment_service.repository;

import com.flashticket.payment_service.entity.EventRefundRun;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRefundRunRepository extends JpaRepository<EventRefundRun, UUID> {}
