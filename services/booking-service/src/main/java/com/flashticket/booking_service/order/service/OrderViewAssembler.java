package com.flashticket.booking_service.order.service;

import com.flashticket.booking_service.availability.repository.SectorInventoryRepository;
import com.flashticket.booking_service.dto.response.OrderView;
import com.flashticket.booking_service.entity.Order;
import com.flashticket.booking_service.enums.SalesMode;
import com.flashticket.booking_service.enums.SectorType;
import com.flashticket.booking_service.mapper.OrderMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Nạp dữ liệu tham chiếu một lần cho cả nhóm đơn, giữ mapper không thực hiện I/O. */
@Component
public class OrderViewAssembler {
  private final OrderMapper orderMapper;
  private final SectorInventoryRepository sectorInventoryRepository;

  public OrderViewAssembler(
      OrderMapper orderMapper, SectorInventoryRepository sectorInventoryRepository) {
    this.orderMapper = orderMapper;
    this.sectorInventoryRepository = sectorInventoryRepository;
  }

  public OrderView toView(Order order) {
    return order == null ? null : toViews(List.of(order)).getFirst();
  }

  public List<OrderView> toViews(List<Order> orders) {
    return toViews(orders, Map.of());
  }

  public List<OrderView> toViews(List<Order> orders, Map<UUID, String> eventTitleByEventId) {
    var sectorIds = new HashSet<UUID>();
    for (var order : orders) {
      if (order == null) continue;
      for (var item : order.getItems()) {
        var selection = item.getSelectionSnapshot();
        if (item.getSalesMode() == SalesMode.SEAT_MAP
            && item.getSectorId() != null
            && item.getEventId() != null
            && (selection == null || selection.seats() == null || selection.seats().isEmpty()))
          sectorIds.add(item.getSectorId());
      }
    }
    var standingSectors = new HashMap<UUID, UUID>();
    if (!sectorIds.isEmpty()) {
      for (var sector : sectorInventoryRepository.findAllById(sectorIds)) {
        if (sector.getSectorType() == SectorType.STANDING)
          standingSectors.put(sector.getSectorId(), sector.getEventId());
      }
    }
    var context = Map.copyOf(standingSectors);
    return orders.stream()
        .map(
            order ->
                order == null
                    ? null
                    : orderMapper.toBuyerOrderView(
                        order, eventTitleByEventId.get(order.getEventId()), context))
        .toList();
  }
}
