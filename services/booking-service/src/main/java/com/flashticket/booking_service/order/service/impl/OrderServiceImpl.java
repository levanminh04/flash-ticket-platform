package com.flashticket.booking_service.order.service.impl;

import com.flashticket.booking_service.mapper.OrderMapper;
import com.flashticket.booking_service.order.repository.IdempotencyRequestRepository;
import com.flashticket.booking_service.order.repository.OrderRepository;
import com.flashticket.booking_service.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
  private final OrderRepository orderRepository;
  private final IdempotencyRequestRepository idempotencyRequestRepository;
  private final OrderMapper orderMapper;
}
