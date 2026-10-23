package com.flashticket.payment_service.config;

import com.flashticket.payment_service.dto.common.ContractCodec;
import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class MappingConfiguration {
  @Bean
  ContractCodec contractCodec(JsonMapper mapper, Validator validator) {
    return new ContractCodec(mapper, validator);
  }
}
