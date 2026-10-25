package com.flashticket.user_service;

import com.flashticket.user_service.follow.repository.UserFollowRepository;
import com.flashticket.user_service.organizer.repository.OrganizerApplicationRepository;
import com.flashticket.user_service.organizer.repository.OrganizerProfileRepository;
import com.flashticket.user_service.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@MockitoBean(
    types = {
      UserRepository.class,
      OrganizerApplicationRepository.class,
      OrganizerProfileRepository.class,
      UserFollowRepository.class,
      JwtDecoder.class
    })
class UserServiceApplicationTests {
  @Test
  void contextLoads() {}
}
