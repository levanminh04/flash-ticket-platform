package com.flashticket.user_service.organizer.service.impl;

import com.flashticket.user_service.mapper.OrganizerProfileMapper;
import com.flashticket.user_service.organizer.repository.OrganizerProfileRepository;
import com.flashticket.user_service.organizer.service.OrganizerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizerProfileServiceImpl implements OrganizerProfileService {
  private final OrganizerProfileRepository organizerProfileRepository;
  private final OrganizerProfileMapper organizerProfileMapper;
}
