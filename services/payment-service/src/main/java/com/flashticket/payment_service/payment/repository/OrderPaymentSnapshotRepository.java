package com.flashticket.payment_service.payment.repository;

import com.flashticket.payment_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentSnapshotRepository extends JpaRepository<OrderPaymentSnapshot, UUID> {}
