package com.flashticket.payment_service.mapper;

import com.flashticket.payment_service.dto.response.RefundView;
import com.flashticket.payment_service.entity.Refund;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = PaymentMapperConfig.class, uses = MoneyMapper.class)
public interface RefundMapper {
  @Mapping(target = "refundId", source = "id")
  @Mapping(target = "amount", source = "amount", qualifiedByName = "moneyToWire")
  RefundView toDto(Refund refund);
}
