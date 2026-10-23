package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public final class SnapshotJsonMapper {
  private final ContractCodec codec;

  @Named("purchaseToJson")
  public JsonNode toJson(PurchaseSnapshot purchase) {
    return codec.toTree(purchase);
  }

  public PurchaseSnapshot fromJson(JsonNode node) {
    return codec.fromTree(node, PurchaseSnapshot.class);
  }
}
