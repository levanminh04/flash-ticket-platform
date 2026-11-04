package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.response.PaymentStart;
import com.flashticket.payment_service.dto.response.PaymentView;
import com.flashticket.payment_service.dto.response.RefundView;
import com.flashticket.payment_service.dto.response.StartRequestView;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PaymentMapperConfig.class)
public interface PaymentViewMapper {
  @Mapping(target = "orderId", source = "orderId")
  @Mapping(target = "acceptedChargeId", source = "acceptedChargeId")
  @Mapping(target = "latestAttempt", source = "latestAttempt")
  @Mapping(target = "startRequest", source = "startRequest")
  @Mapping(target = "refunds", source = "refunds")
  PaymentView toDto(
      UUID orderId,
      UUID acceptedChargeId,
      PaymentStart latestAttempt,
      StartRequestView startRequest,
      List<RefundView> refunds);
}
