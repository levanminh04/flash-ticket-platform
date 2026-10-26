package com.flashticket.user_service.user.service.impl;

import com.flashticket.user_service.mapper.UserMapper;
import com.flashticket.user_service.user.repository.UserRepository;
import com.flashticket.user_service.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
  private final UserRepository userRepository;
  private final UserMapper userMapper;
}
