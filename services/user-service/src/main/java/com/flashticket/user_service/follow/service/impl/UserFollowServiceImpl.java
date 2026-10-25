package com.flashticket.user_service.follow.service.impl;

import com.flashticket.user_service.follow.repository.UserFollowRepository;
import com.flashticket.user_service.follow.service.UserFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserFollowServiceImpl implements UserFollowService {
  private final UserFollowRepository userFollowRepository;
}
