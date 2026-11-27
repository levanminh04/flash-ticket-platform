package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flashticket.user_service.domain.UserModels.PublicOrganizer;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.web.UserController;
import com.flashticket.user_service.web.UserException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OrganizerBatchEndpointTests {

  @Test
  void routesBatchFeaturedAndFollowingStatusThroughStore() {
    var store = mock(UserStore.class);
    var controller = new UserController(store);
    var org1 = new PublicOrganizer("org-1", "StarMusic", "Concerts", 42L, null, null);
    when(store.publicOrganizers(List.of("org-1", "org-2"))).thenReturn(List.of(org1));
    when(store.featuredOrganizers(20)).thenReturn(List.of(org1));
    when(store.featuredOrganizers(1)).thenReturn(List.of(org1));
    when(store.followStates("buyer-1", List.of("org-1", "org-2")))
        .thenReturn(Map.of("org-1", true, "org-2", false));

    assertThat(controller.organizersBatch(List.of("org-1", "org-2"))).containsExactly(org1);
    assertThat(controller.featuredOrganizers(99)).containsExactly(org1);
    assertThat(controller.featuredOrganizers(0)).containsExactly(org1);

    var buyerJwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("buyer-1").build();
    assertThat(controller.followingStatus(buyerJwt, List.of("org-1", "org-2")))
        .containsEntry("org-1", true)
        .containsEntry("org-2", false);

    verify(store).featuredOrganizers(20);
    verify(store).featuredOrganizers(1);
    verify(store).followStates("buyer-1", List.of("org-1", "org-2"));

    assertThatThrownBy(() -> controller.followingStatus(null, List.of("org-1")))
        .isInstanceOfSatisfying(UserException.class, ex -> assertThat(ex.status()).isEqualTo(401));
  }
}
