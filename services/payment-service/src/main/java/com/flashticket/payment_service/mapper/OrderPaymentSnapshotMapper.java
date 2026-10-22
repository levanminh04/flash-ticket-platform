package com.flashticket.payment_service.mapper;
import com.flashticket.payment_service.dto.snapshot.PurchaseSnapshot;
import com.flashticket.payment_service.dto.common.ContractValidationException;
import com.flashticket.payment_service.entity.OrderPaymentSnapshot;
import java.time.Instant;
public final class OrderPaymentSnapshotMapper {
  private final SnapshotJsonMapper json;
  public OrderPaymentSnapshotMapper(SnapshotJsonMapper json){this.json=json;}
  public OrderPaymentSnapshot toEntity(PurchaseSnapshot purchase,String snapshotHash,Instant receivedAt){
    if(snapshotHash==null || snapshotHash.isBlank() || receivedAt==null)throw new ContractValidationException("snapshotMetadata","REQUIRED");
    var tree=json.toJson(purchase);
    return new OrderPaymentSnapshot(purchase.orderId(),purchase.eventId(),purchase.buyerSubject(),new MoneyMapper().toDecimal(purchase.total()),purchase.currency(),purchase.expiresAt(),new VersionMapper().toLong(purchase.sourceOrderVersion()),tree,snapshotHash,receivedAt);
  }
}
