package com.flashticket.api_gateway.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("flashticket.gateway.cors")
public record GatewayCorsProperties(List<String> allowedOrigins) {

  public GatewayCorsProperties {
    allowedOrigins =
        allowedOrigins == null
            ? List.of()
            : allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList();
  }

  public List<String> allowedOrigins() {
    return List.copyOf(allowedOrigins);
  }
}
