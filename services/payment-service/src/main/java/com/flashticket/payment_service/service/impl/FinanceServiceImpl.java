package com.flashticket.payment_service.service.impl;

import com.flashticket.payment_service.repository.EventFinanceSnapshotRepository;
import com.flashticket.payment_service.repository.EventPayoutRepository;
import com.flashticket.payment_service.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {
  private final EventFinanceSnapshotRepository eventFinanceSnapshotRepository;
  private final EventPayoutRepository eventPayoutRepository;
}
