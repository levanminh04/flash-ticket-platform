package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.common.Envelope;
import com.flashticket.payment_service.dto.message.StartPaymentResult;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class MessageJsonMapper {
  ContractCodec codec;

  public <T> JsonNode toEnvelopeJson(Envelope<T> value) {
    return codec.toTree(value);
  }

  public <T> Envelope<T> fromEnvelopeJson(JsonNode node, TypeReference<Envelope<T>> type) {
    return codec.fromTree(node, type);
  }

  public JsonNode toResultJson(StartPaymentResult result) {
    return codec.toTree(result);
  }

  public StartPaymentResult fromResultJson(JsonNode node) {
    return codec.fromTree(node, StartPaymentResult.class);
  }
}
