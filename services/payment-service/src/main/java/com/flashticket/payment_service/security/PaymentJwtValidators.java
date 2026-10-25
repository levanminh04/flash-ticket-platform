package com.flashticket.payment_service.security;

import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;

public final class PaymentJwtValidators {
  private PaymentJwtValidators() {}

  public static OAuth2TokenValidator<Jwt> create(PaymentJwtProperties p) {
    OAuth2TokenValidator<Jwt> required =
        jwt -> {
          var audience = jwt.getAudience();
          var subject = jwt.getSubject();
          return audience != null
                  && audience.contains(p.audience())
                  && subject != null
                  && !subject.isBlank()
                  && jwt.getExpiresAt() != null
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Required payment claims are invalid", null));
        };
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(p.issuerUri()), required);
  }
}
