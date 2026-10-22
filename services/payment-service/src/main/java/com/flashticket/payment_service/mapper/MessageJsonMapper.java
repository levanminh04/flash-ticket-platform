package com.flashticket.payment_service.mapper;
import com.flashticket.payment_service.dto.common.*;
import com.flashticket.payment_service.dto.message.StartPaymentResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
public final class MessageJsonMapper {
  private final ContractCodec codec;
  public MessageJsonMapper(ContractCodec codec){this.codec=codec;}
  public <T> JsonNode toEnvelopeJson(Envelope<T> value){return codec.toTree(value);}
  public <T> Envelope<T> fromEnvelopeJson(JsonNode node,TypeReference<Envelope<T>> type){return codec.fromTree(node,type);}
  public JsonNode toResultJson(StartPaymentResult result){return codec.toTree(result);}
  public StartPaymentResult fromResultJson(JsonNode node){return codec.fromTree(node,StartPaymentResult.class);}
}
