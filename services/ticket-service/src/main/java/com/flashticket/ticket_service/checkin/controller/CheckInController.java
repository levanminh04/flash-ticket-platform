package com.flashticket.ticket_service.checkin.controller;

import com.flashticket.ticket_service.checkin.service.CheckInService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class CheckInController {
  private final CheckInService checkInService;
}
