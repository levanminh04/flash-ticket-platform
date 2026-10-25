package com.flashticket.user_service.organizer.controller;

import com.flashticket.user_service.organizer.service.OrganizerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizers")
@RequiredArgsConstructor
public class OrganizerController {
  private final OrganizerProfileService organizerProfileService;
}
