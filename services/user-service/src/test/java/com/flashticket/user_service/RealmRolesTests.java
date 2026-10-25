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
            .claim(
                "realm_access",
                Map.of("roles", List.of("BUYER", "BUYER", "SUPER_ADMIN", 123, "INTERNAL_SERVICE")))
            .build();
    assertThat(new RealmRoles().convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_BUYER", "ROLE_INTERNAL_SERVICE");
  }
}
