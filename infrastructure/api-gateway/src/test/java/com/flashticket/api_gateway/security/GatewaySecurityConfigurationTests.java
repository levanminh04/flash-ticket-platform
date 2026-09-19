package com.flashticket.api_gateway.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.handler.DefaultWebFilterChain;

import reactor.core.publisher.Mono;

@SpringBootTest
class GatewaySecurityConfigurationTests {

	@Autowired
	private SecurityWebFilterChain springSecurityFilterChain;

	@Test
	void permitsOnlyTheExplicitPublicEventReadPathsWithoutAuthentication() {
		assertTrue(reachesDownstream(HttpMethod.GET, "/api/v1/events/event-1", null));
		assertTrue(reachesDownstream(HttpMethod.GET, "/api/v1/events/event-1/availability", null));
		assertFalse(reachesDownstream(HttpMethod.POST, "/api/v1/orders", null));
	}

	@Test
	void deniesInternalPathsAtThePublicGateway() {
		assertFalse(reachesDownstream(HttpMethod.GET, "/internal/v1/orders/order-1", null));
	}

	@Test
	void appliesApprovedCoarseRoleChecks() {
		Authentication buyer = authenticatedAs("ROLE_BUYER");
		Authentication organizer = authenticatedAs("ROLE_ORGANIZER");

		assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/orders", buyer));
		assertFalse(reachesDownstream(HttpMethod.POST, "/api/v1/orders", organizer));
		assertTrue(reachesDownstream(HttpMethod.POST, "/api/v1/events/event-1/check-ins", organizer));
	}

	private boolean reachesDownstream(HttpMethod method, String path, Authentication authentication) {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
		AtomicBoolean reachedDownstream = new AtomicBoolean(false);
		List<WebFilter> filters = springSecurityFilterChain.getWebFilters().collectList().block();
		Mono<Void> result = new DefaultWebFilterChain(downstream -> {
			reachedDownstream.set(true);
			return Mono.empty();
		}, filters).filter(exchange);

		if (authentication == null) {
			result.block();
		}
		else {
			result.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)).block();
		}

		return reachedDownstream.get();
	}

	private Authentication authenticatedAs(String authority) {
		return UsernamePasswordAuthenticationToken.authenticated("subject-1", "N/A",
				List.of(new SimpleGrantedAuthority(authority)));
	}
}
