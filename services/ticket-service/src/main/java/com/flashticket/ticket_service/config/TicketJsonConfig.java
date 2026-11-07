package com.flashticket.ticket_service.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.*;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
public class TicketJsonConfig {
  @Bean
  public JsonMapperBuilderCustomizer ticketJsonMapperCustomizer() {
    return builder ->
        builder
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .withCoercionConfig(
                LogicalType.Enum,
                c -> c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail));
  }
}
