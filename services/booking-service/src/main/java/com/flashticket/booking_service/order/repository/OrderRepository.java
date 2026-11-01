package com.flashticket.booking_service.order.repository;

import com.flashticket.booking_service.entity.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {}
