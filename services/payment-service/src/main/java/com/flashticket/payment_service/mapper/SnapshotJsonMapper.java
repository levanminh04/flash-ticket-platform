package com.flashticket.payment_service.mapper;
import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import tools.jackson.databind.JsonNode;
public final class SnapshotJsonMapper {
  private final ContractCodec codec;
  public SnapshotJsonMapper(ContractCodec codec){this.codec=codec;}
  public JsonNode toJson(PurchaseSnapshot purchase){return codec.toTree(purchase);}
  public PurchaseSnapshot fromJson(JsonNode node){return codec.fromTree(node,PurchaseSnapshot.class);}
}
