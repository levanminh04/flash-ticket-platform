package com.flashticket.event_service.api;

import com.flashticket.event_service.application.SeatMapService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizer/events/{id}")
public class SeatMapController {
  private final SeatMapService service;

  public SeatMapController(SeatMapService service) {
    this.service = service;
  }

  @GetMapping("/seat-map")
  public SeatMap read(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return service.read(id, EventController.actor(jwt));
  }

  @PutMapping("/seat-map")
  public SeatMap save(
      @PathVariable UUID id,
      @RequestParam String rowVersion,
      @AuthenticationPrincipal Jwt jwt,
      @RequestBody SeatMap input) {
    return service.save(id, EventController.actor(jwt), rowVersion, input);
  }

  @PutMapping("/ticket-types")
  public EventViews.Owner types(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody EventCommands.SeatTypes input) {
    return service.types(id, EventController.actor(jwt), input);
  }

  @PostMapping("/sales-mode")
  public EventViews.Owner mode(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody EventCommands.Mode input) {
    return service.mode(id, EventController.actor(jwt), input);
  }
}
