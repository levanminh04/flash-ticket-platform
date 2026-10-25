package com.flashticket.user_service.follow.controller;

import com.flashticket.user_service.follow.service.UserFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizers/{subject}/follow")
@RequiredArgsConstructor
public class OrganizerFollowController {
  private final UserFollowService userFollowService;
}
