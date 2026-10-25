package com.flashticket.user_service.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class RealmRoles implements Converter<Jwt, Collection<GrantedAuthority>> {
  private static final Set<String> ALLOWED =
      Set.of("BUYER", "ORGANIZER", "ADMIN", "INTERNAL_SERVICE");

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    if (!(jwt.getClaim("realm_access") instanceof Map<?, ?> realm)
        || !(realm.get("roles") instanceof Collection<?> roles)) return List.of();
    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(ALLOWED::contains)
        .distinct()
        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }
}
