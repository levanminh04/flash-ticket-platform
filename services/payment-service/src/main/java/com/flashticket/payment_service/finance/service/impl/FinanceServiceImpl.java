package com.flashticket.payment_service.finance.service.impl;

import com.flashticket.payment_service.finance.repository.*;
import com.flashticket.payment_service.finance.service.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {
  private final EventFinanceSnapshotRepository eventFinanceSnapshotRepository;
  private final EventPayoutRepository eventPayoutRepository;
}
