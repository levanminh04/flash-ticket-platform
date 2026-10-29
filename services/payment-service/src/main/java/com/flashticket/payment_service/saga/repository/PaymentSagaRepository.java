package com.flashticket.payment_service.saga.repository;

import com.flashticket.payment_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSagaRepository extends JpaRepository<PaymentSaga, UUID> {}
