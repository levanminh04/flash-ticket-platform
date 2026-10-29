package com.flashticket.event_service.api;

import com.flashticket.event_service.application.StatisticsService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class StatisticsController {
  private final StatisticsService service;

  public StatisticsController(StatisticsService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/organizer/events/{id}/statistics")
  public StatisticsService.Statistics read(
      @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return service.read(id, EventController.actor(jwt));
  }
}
