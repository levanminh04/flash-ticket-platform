package com.flashticket.ticket_service.security;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTests {
  final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

  @Test
  void onlyDistinctBusinessRolesBecomeAuthorities() {
    var jwt =
        token(
            Map.of(
                "roles",
                List.of("BUYER", "ADMIN", "ORGANIZER", "BUYER", "offline_access", "buyer", 42)));
    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_BUYER", "ROLE_ADMIN", "ROLE_ORGANIZER");
  }

  @Test
  void internalServiceRoleBecomesOneAuthorityWithoutAcceptingAliases() {
    var jwt =
        token(
            Map.of(
                "roles",
                List.of(
                    "INTERNAL_SERVICE",
                    "INTERNAL_SERVICE",
                    "internal_service",
                    "ROLE_INTERNAL_SERVICE")));
    assertThat(converter.convert(jwt))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_INTERNAL_SERVICE");
  }

  static Stream<Object> malformed() {
    return Stream.of(
        "bad",
        42,
        Map.of(),
        Map.of("roles", "BUYER"),
        Map.of("roles", 42),
        Map.of("roles", Arrays.asList(null, true, "unknown")));
  }

  @ParameterizedTest
  @MethodSource("malformed")
  void malformedClaimsYieldNoAuthorities(Object claim) {
    assertThat(converter.convert(token(claim))).isEmpty();
  }

  @Test
  void absentRealmClaimYieldsNoAuthorities() {
    var jwt = Jwt.withTokenValue("token").header("alg", "RS256").claim("sub", "buyer").build();
    assertThat(converter.convert(jwt)).isEmpty();
  }

  private Jwt token(Object realm) {
    return Jwt.withTokenValue("token").header("alg", "RS256").claim("realm_access", realm).build();
  }

  @Test
  void rejectsMalformedRoleClaim() {
    var jwt =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture")
            .header("alg", "RS256")
            .subject("caller")
            .claim("realm_access", java.util.Map.of("roles", "BUYER"))
            .build();
    assertThat(new KeycloakRealmRoleConverter().convert(jwt)).isEmpty();
  }
}
