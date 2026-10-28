package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class SnapshotJsonMapper {
  ContractCodec codec;

  @Named("purchaseToJson")
  public JsonNode toJson(PurchaseSnapshot purchase) {
    return codec.toTree(purchase);
  }

  public PurchaseSnapshot fromJson(JsonNode node) {
    return codec.fromTree(node, PurchaseSnapshot.class);
  }
}
