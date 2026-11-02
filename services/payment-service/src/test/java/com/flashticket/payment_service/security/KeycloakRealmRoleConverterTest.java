package com.flashticket.payment_service.security;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {
  @Test
  void acceptsOnlyBusinessRolesAndIgnoresMalformedClaims() {
    var converter = new KeycloakRealmRoleConverter();
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .subject("buyer")
            .claim(
                "realm_access",
                Map.of("roles", List.of("BUYER", "BUYER", "ADMIN", "offline_access", 1)))
            .build();
    assertThat(converter.convert(jwt))
        .extracting(a -> a.getAuthority())
        .containsExactly("ROLE_BUYER", "ROLE_ADMIN");
    for (Object claim :
        List.of("bad", Map.of("roles", "BUYER"), Map.of("other", List.of("BUYER")))) {
      assertThat(
              converter.convert(
                  Jwt.withTokenValue("test")
                      .header("alg", "RS256")
                      .claim("realm_access", claim)
                      .build()))
          .isEmpty();
    }
  }
}
