package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.web.UserController;
import com.flashticket.user_service.web.UserException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class SubjectInputTests {
  @Test
  void blankPathSubjectIsRejectedBeforeStorageAccess() {
    var store = mock(UserStore.class);
    var controller = new UserController(store);
    var buyer = Jwt.withTokenValue("test").header("alg", "RS256").subject("buyer").build();
    assertThatThrownBy(() -> controller.follow(buyer, " "))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> org.assertj.core.api.Assertions.assertThat(e.status()).isEqualTo(400));
    assertThatThrownBy(() -> controller.organizer(" ")).isInstanceOf(UserException.class);
    assertThatThrownBy(() -> controller.internalUser(" ")).isInstanceOf(UserException.class);
    verifyNoInteractions(store);
  }
}
