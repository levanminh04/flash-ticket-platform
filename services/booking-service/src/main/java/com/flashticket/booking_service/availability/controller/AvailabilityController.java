package com.flashticket.booking_service.availability.controller;

import com.flashticket.booking_service.availability.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/availability")
@RequiredArgsConstructor
public class AvailabilityController {
  private final AvailabilityService availabilityService;
}
