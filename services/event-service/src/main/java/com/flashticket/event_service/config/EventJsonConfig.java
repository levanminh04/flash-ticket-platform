package com.flashticket.event_service.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
public class EventJsonConfig {
  @Bean
  JsonMapperBuilderCustomizer eventJson() {
    return builder ->
        builder
            .addModule(
                new tools.jackson.databind.module.SimpleModule()
                    .addDeserializer(java.util.UUID.class, new UuidDeserializer()))
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .withCoercionConfig(
                LogicalType.Textual,
                c ->
                    c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .withCoercionConfig(
                LogicalType.Integer,
                c ->
                    c.setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail));
  }
}
