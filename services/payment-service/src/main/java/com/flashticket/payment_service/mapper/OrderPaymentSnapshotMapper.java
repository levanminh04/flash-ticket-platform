package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.entity.OrderPaymentSnapshot;
import java.time.Instant;
import org.mapstruct.BeanMapping;
import org.mapstruct.BeforeMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    config = PaymentMapperConfig.class,
    uses = {MoneyMapper.class, VersionMapper.class, SnapshotJsonMapper.class})
public interface OrderPaymentSnapshotMapper {
  @BeanMapping(ignoreByDefault = true)
  @Mapping(target = "orderId", source = "purchase.orderId")
  @Mapping(target = "eventId", source = "purchase.eventId")
  @Mapping(target = "buyerSubject", source = "purchase.buyerSubject")
  @Mapping(target = "expectedAmount", source = "purchase.total", qualifiedByName = "moneyToDecimal")
  @Mapping(target = "currency", source = "purchase.currency")
  @Mapping(target = "expiresAt", source = "purchase.expiresAt")
  @Mapping(
      target = "sourceOrderVersion",
      source = "purchase.sourceOrderVersion",
      qualifiedByName = "versionToLong")
  @Mapping(target = "purchaseSnapshot", source = "purchase", qualifiedByName = "purchaseToJson")
  @Mapping(target = "snapshotHash", source = "snapshotHash")
  @Mapping(target = "receivedAt", source = "receivedAt")
  OrderPaymentSnapshot toEntity(PurchaseSnapshot purchase, String snapshotHash, Instant receivedAt);

  @BeforeMapping
  default void validateMetadata(
      PurchaseSnapshot purchase, String snapshotHash, Instant receivedAt) {
    if (purchase == null) throw new ContractValidationException("purchase", "REQUIRED");
    if (snapshotHash == null || snapshotHash.isBlank() || receivedAt == null)
      throw new ContractValidationException("snapshotMetadata", "REQUIRED");
  }
}
