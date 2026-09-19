package com.flashticket.api_gateway.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTests {

	private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

	@Test
	void mapsOnlyCurrentBusinessRolesFromRealmAccess() {
		Jwt jwt = jwtWithRealmRoles("offline_access", "BUYER", "uma_authorization", "ORGANIZER", "ADMIN");

		assertEquals(List.of("ROLE_BUYER", "ROLE_ORGANIZER", "ROLE_ADMIN"), authorityNames(converter.convert(jwt)));
	}

	@Test
	void ignoresMissingOrMalformedRealmAccessRoles() {
		Jwt missingRoles = Jwt.withTokenValue("token").header("alg", "none").claim("realm_access", Map.of()).build();
		Jwt malformedRoles = Jwt.withTokenValue("token").header("alg", "none")
				.claim("realm_access", Map.of("roles", "BUYER")).build();

		assertEquals(List.of(), authorityNames(converter.convert(missingRoles)));
		assertEquals(List.of(), authorityNames(converter.convert(malformedRoles)));
	}

	private Jwt jwtWithRealmRoles(String... roles) {
		return Jwt.withTokenValue("token").header("alg", "none").claim("realm_access", Map.of("roles", List.of(roles)))
				.build();
	}

	private List<String> authorityNames(Collection<GrantedAuthority> authorities) {
		return authorities.stream().map(GrantedAuthority::getAuthority).toList();
	}
}
