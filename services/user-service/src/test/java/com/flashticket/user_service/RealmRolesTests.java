package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.flashticket.user_service.security.RealmRoles;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RealmRolesTests {
  @Test
  void malformedAndMissingRolesNeverAcquireDefaultPermissions() {
    var converter = new RealmRoles();
    assertThat(
            converter.convert(
                Jwt.withTokenValue("test").header("alg", "RS256").subject("buyer").build()))
        .isEmpty();
    assertThat(
            converter.convert(
                Jwt.withTokenValue("test")
                    .header("alg", "RS256")
                    .claim("realm_access", Map.of("roles", "BUYER"))
                    .build()))
        .isEmpty();
  }

  @Test
  void onlyKnownRolesAreMappedAndDuplicatesAreRemoved() {
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .claim("realm_access", Map.of("roles", List.of("BUYER", "BUYER", "SUPER_ADMIN", 123)))
            .build();
    assertThat(new RealmRoles().convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_BUYER");
  }

  @Test
  void internalIdentityCannotAcquireHumanPermissionsFromAccidentalBusinessRoles() {
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .claim(
                "realm_access",
                Map.of("roles", List.of("INTERNAL_SERVICE", "BUYER", "ADMIN", "ORGANIZER")))
            .build();
    assertThat(new RealmRoles().convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_INTERNAL_SERVICE");
  }

  @Test
  void keycloakServiceAccountClaimsCannotAcquireHumanPermissions() {
    for (var marker :
        List.of(
            Map.of("client_id", "user-management"),
            Map.of("preferred_username", "service-account-user-management"))) {
      var jwt =
          Jwt.withTokenValue("test")
              .header("alg", "RS256")
              .claims(c -> c.putAll(marker))
              .claim("realm_access", Map.of("roles", List.of("BUYER", "ADMIN", "ORGANIZER")))
              .build();
      assertThat(new RealmRoles().convert(jwt)).isEmpty();
    }
  }
}
