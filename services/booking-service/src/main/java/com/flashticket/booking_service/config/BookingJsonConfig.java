package com.flashticket.booking_service.config;

import com.flashticket.booking_service.dto.request.OrderSelection;
import com.flashticket.booking_service.dto.request.OrderSelectionDeserializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
public class BookingJsonConfig {
  @Bean
  JsonMapperBuilderCustomizer bookingJsonMapperCustomizer() {
    return builder ->
        builder
            .addModule(
                new SimpleModule()
                    .addDeserializer(OrderSelection.class, new OrderSelectionDeserializer()))
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
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail))
            .withCoercionConfig(
                LogicalType.Enum,
                c -> c.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail));
  }
}
