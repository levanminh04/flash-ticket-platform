package com.flashticket.ticket_service.checkin.service.impl;

import com.flashticket.ticket_service.access.repository.EventAccessSnapshotRepository;
import com.flashticket.ticket_service.checkin.repository.CheckinAttemptRepository;
import com.flashticket.ticket_service.checkin.service.CheckInService;
import com.flashticket.ticket_service.issuance.repository.IssuanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {
  private final CheckinAttemptRepository checkinAttemptRepository;
  private final IssuanceRepository issuanceRepository;
  private final EventAccessSnapshotRepository eventAccessSnapshotRepository;
}
