package com.flashticket.ticket_service.issuance.service.impl;

import com.flashticket.ticket_service.access.repository.EventAccessSnapshotRepository;
import com.flashticket.ticket_service.issuance.repository.IssuanceRepository;
import com.flashticket.ticket_service.issuance.service.IssuanceService;
import com.flashticket.ticket_service.mapper.IssuanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IssuanceServiceImpl implements IssuanceService {
  private final IssuanceRepository issuanceRepository;
  private final EventAccessSnapshotRepository eventAccessSnapshotRepository;
  private final IssuanceMapper issuanceMapper;
}
