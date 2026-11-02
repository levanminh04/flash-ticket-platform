package com.flashticket.payment_service.security;

import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class KeycloakRealmRoleConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {
  private static final Set<String> ROLES = Set.of("BUYER", "ORGANIZER", "ADMIN");

  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Object realm = jwt.getClaim("realm_access");
    if (!(realm instanceof Map<?, ?> map) || !(map.get("roles") instanceof Collection<?> roles))
      return List.of();
    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(ROLES::contains)
        .distinct()
        .<GrantedAuthority>map(
            r ->
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
        .toList();
  }
}
