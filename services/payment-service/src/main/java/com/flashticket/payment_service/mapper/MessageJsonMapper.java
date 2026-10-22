package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.StartPaymentResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

public final class MessageJsonMapper {
  private final ContractCodec codec;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ContractCodec has no mutators; its private JsonMapper is immutable and Validator is thread-safe.")
  public MessageJsonMapper(ContractCodec codec) {
    this.codec = codec;
  }

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
