package com.flashticket.api_gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("flashticket.gateway")
public record VnpayCallbackProperties(boolean vnpayCallbacksEnabled) {}
