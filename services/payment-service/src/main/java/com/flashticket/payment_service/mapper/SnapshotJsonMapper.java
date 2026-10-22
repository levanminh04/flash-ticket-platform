package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.ContractCodec;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import tools.jackson.databind.JsonNode;

public final class SnapshotJsonMapper {
  private final ContractCodec codec;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "ContractCodec has no mutators; its private JsonMapper is immutable and Validator is thread-safe.")
  public SnapshotJsonMapper(ContractCodec codec) {
    this.codec = codec;
  }

  public JsonNode toJson(PurchaseSnapshot purchase) {
    return codec.toTree(purchase);
  }

  public PurchaseSnapshot fromJson(JsonNode node) {
    return codec.fromTree(node, PurchaseSnapshot.class);
  }
}
