package com.flashticket.booking_service.mapper;

import com.flashticket.booking_service.dto.response.OrderItemView;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.entity.OrderItem;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Context;
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
  @Mapping(target = "eventTitle", ignore = true)
  OrderView toView(Order order, @Context Map<UUID, UUID> standingSectorEventIds);

  @Mapping(target = "orderId", source = "order.id")
  @Mapping(target = "state", source = "order.lifecycleState")
  @Mapping(target = "items", source = "order.items")
  @Mapping(target = "eventTitle", source = "eventTitle")
  OrderView toBuyerOrderView(
      Order order, String eventTitle, @Context Map<UUID, UUID> standingSectorEventIds);

  @Mapping(target = "orderItemId", source = "id")
  @Mapping(target = "seats", source = "item")
  OrderItemView toItemView(OrderItem item, @Context Map<UUID, UUID> standingSectorEventIds);
}
