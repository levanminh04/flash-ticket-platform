package com.flashticket.user_service.organizer.service.impl;

import com.flashticket.user_service.mapper.OrganizerApplicationMapper;
import com.flashticket.user_service.organizer.repository.OrganizerApplicationRepository;
import com.flashticket.user_service.organizer.service.OrganizerApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizerApplicationServiceImpl implements OrganizerApplicationService {
  private final OrganizerApplicationRepository organizerApplicationRepository;
  private final OrganizerApplicationMapper organizerApplicationMapper;
}
