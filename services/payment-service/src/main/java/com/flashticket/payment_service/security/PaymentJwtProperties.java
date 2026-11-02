package com.flashticket.payment_service.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("flashticket.payment.jwt")
public record PaymentJwtProperties(
    @NotBlank String issuerUri, @NotBlank String jwkSetUri, @NotBlank String audience) {}
