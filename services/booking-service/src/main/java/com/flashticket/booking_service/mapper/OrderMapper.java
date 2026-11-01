package com.flashticket.booking_service.mapper;

import com.flashticket.booking_service.dto.response.OrderItemView;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = MappingValueConverter.class)
public interface OrderMapper {
  @Mapping(target = "orderId", source = "id")
  @Mapping(target = "state", source = "lifecycleState")
  @Mapping(target = "items", source = "items")
  OrderView toView(Order order);

  @Mapping(target = "orderItemId", source = "id")
  @Mapping(target = "seats", source = "selectionSnapshot")
  OrderItemView toItemView(OrderItem item);
}
