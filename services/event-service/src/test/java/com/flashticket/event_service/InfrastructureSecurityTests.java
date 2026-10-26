package com.flashticket.event_service;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.security.oauth2.client.registration.internal.provider=keycloak",
      "spring.security.oauth2.client.registration.internal.client-id=internal-service",
      "spring.security.oauth2.client.registration.internal.client-secret=isolated-test-secret",
      "spring.security.oauth2.client.registration.internal.authorization-grant-type=client_credentials",
      "spring.security.oauth2.client.provider.keycloak.token-uri=http://127.0.0.1:1/token"
    })
@AutoConfigureMockMvc
class InfrastructureSecurityTests {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @Test
  void anonymousRequestGetsBearerChallengeEvenWhenHtmlIsAccepted() throws Exception {
    mvc.perform(get("/infrastructure-smoke/protected").accept(MediaType.TEXT_HTML))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
  }

  @Test
  void invalidBearerTokenIsRejectedByTheDecoder() throws Exception {
    when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid test token"));
    mvc.perform(
            get("/infrastructure-smoke/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
  }

  @Test
  void verifiedBearerTokenReachesTheScaffoldDespiteOutboundOAuthClient() throws Exception {
    Jwt token =
        Jwt.withTokenValue("verified-token").header("alg", "RS256").subject("test-service").build();
    when(decoder.decode("verified-token")).thenReturn(token);
    mvc.perform(
            get("/infrastructure-smoke/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer verified-token"))
        .andExpect(status().isNotFound());
  }
}
