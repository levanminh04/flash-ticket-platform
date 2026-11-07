package com.flashticket.booking_service.order.repository;

import com.flashticket.booking_service.entity.IdempotencyRequest;
import com.flashticket.booking_service.entity.IdempotencyRequestId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRequestRepository
    extends JpaRepository<IdempotencyRequest, IdempotencyRequestId> {}
