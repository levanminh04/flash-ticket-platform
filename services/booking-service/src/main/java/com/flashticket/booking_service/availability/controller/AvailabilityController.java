package com.flashticket.booking_service.availability.controller;

import com.flashticket.booking_service.availability.service.AvailabilityCacheService;
import com.flashticket.booking_service.dto.response.Availability;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/availability")
@RequiredArgsConstructor
public class AvailabilityController {
  private final AvailabilityCacheService availabilityCacheService;

  @GetMapping
  public ResponseEntity<Availability> getAvailability(@PathVariable UUID eventId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(availabilityCacheService.getAvailability(eventId));
  }
}
