package com.flashticket.event_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class EventImageConfig {
  @Bean
  @Scope("prototype")
  @ConditionalOnMissingBean(RestClient.Builder.class)
  RestClient.Builder eventImageRestClientBuilder() {
    return RestClient.builder();
  }
}
