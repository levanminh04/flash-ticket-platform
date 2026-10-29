package com.flashticket.payment_service.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Chuyển realm role trong JWT Keycloak thành authority để Spring kiểm quyền. */
public final class KeycloakRealmRoleConverter
    implements Converter<Jwt, Collection<GrantedAuthority>> {

  // Chỉ chấp nhận các role ứng dụng hỗ trợ, bỏ role mặc định hoặc role lạ của Keycloak.
  private static final Set<String> SUPPORTED_ROLES =
      Set.of("BUYER", "ORGANIZER", "ADMIN", "INTERNAL_SERVICE");

  /** Đọc realm_access.roles, lọc role hợp lệ và thêm tiền tố ROLE_ cho hasRole(). */
  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Object realmAccessClaim = jwt.getClaim("realm_access");
    if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
      return List.of();
    }

    Object rolesClaim = realmAccess.get("roles");
    if (!(rolesClaim instanceof Collection<?> roles)) {
      return List.of();
    }

    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(SUPPORTED_ROLES::contains)
        .distinct()
        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }
}
