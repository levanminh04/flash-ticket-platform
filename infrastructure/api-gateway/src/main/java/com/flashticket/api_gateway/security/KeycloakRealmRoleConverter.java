package com.flashticket.api_gateway.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converts Keycloak business roles from {@code realm_access.roles} to Spring
 * Security authorities. Keycloak default roles and malformed claim values are
 * deliberately ignored.
 */
final class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

	private static final Set<String> BUSINESS_ROLES = Set.of("BUYER", "ORGANIZER", "ADMIN");

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

		return roles.stream().filter(String.class::isInstance).map(String.class::cast).filter(BUSINESS_ROLES::contains)
				.distinct().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
	}
}
