package com.flashticket.user_service.organizer.controller;

import com.flashticket.user_service.organizer.service.OrganizerApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizer-applications")
@RequiredArgsConstructor
public class OrganizerApplicationController {
  private final OrganizerApplicationService organizerApplicationService;
}
