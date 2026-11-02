package com.flashticket.payment_service.security;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class PaymentJwtValidatorsTest {
  final PaymentJwtProperties props =
      new PaymentJwtProperties(
          "https://issuer.example.test", "https://issuer.example.test/jwks", "payment");

  Jwt token(String issuer, List<String> audience, String subject, Instant expires) {
    var b =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .issuer(issuer)
            .audience(audience)
            .issuedAt(Instant.now().minusSeconds(3600));
    if (subject != null) b.subject(subject);
    if (expires != null) b.expiresAt(expires);
    return b.build();
  }

  @Test
  void validatesIssuerAudienceExpiryAndNonblankSubject() {
    var validator = PaymentJwtValidators.create(props);
    var future = Instant.now().plusSeconds(600);
    assertThat(
            validator
                .validate(token(props.issuerUri(), List.of("payment"), "buyer", future))
                .hasErrors())
        .isFalse();
    for (var jwt :
        List.of(
            token("https://wrong.example.test", List.of("payment"), "buyer", future),
            token(props.issuerUri(), List.of("wrong"), "buyer", future),
            token(props.issuerUri(), List.of(), "buyer", future),
            token(props.issuerUri(), List.of("payment"), " ", future),
            token(props.issuerUri(), List.of("payment"), null, future),
            token(props.issuerUri(), List.of("payment"), "buyer", Instant.now().minusSeconds(600)),
            token(props.issuerUri(), List.of("payment"), "buyer", null))) {
      assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }
  }

  @Test
  void rejectsMissingAudienceWithoutThrowing() {
    var jwt =
        Jwt.withTokenValue("test")
            .header("alg", "RS256")
            .issuer(props.issuerUri())
            .subject("buyer")
            .expiresAt(Instant.now().plusSeconds(600))
            .build();
    assertThat(PaymentJwtValidators.create(props).validate(jwt).hasErrors()).isTrue();
  }
}
