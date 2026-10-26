package com.flashticket.user_service.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/** Giữ wire DTO chặt chẽ, không ép số/boolean thành tên hoặc mô tả. */
@Configuration(proxyBeanMethods = false)
public class UserJsonConfig {
  @Bean
  public JsonMapperBuilderCustomizer userJsonMapperCustomizer() {
    return builder ->
        builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withCoercionConfig(
                LogicalType.Textual,
                config ->
                    config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .withCoercionConfig(
                LogicalType.Integer,
                config ->
                    config
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .withCoercionConfig(
                LogicalType.Enum,
                config -> config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail));
  }
}
