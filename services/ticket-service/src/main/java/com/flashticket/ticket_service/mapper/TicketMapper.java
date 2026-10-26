package com.flashticket.ticket_service.mapper;

import com.flashticket.ticket_service.dto.response.TicketView;
import com.flashticket.ticket_service.entity.Ticket;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class TicketMapper {
  @Named("ticketView")
  public TicketView toView(Ticket ticket) {
    if (ticket == null
        || ticket.getId() == null
        || ticket.getEventId() == null
        || ticket.getOrderItemId() == null
        || ticket.getOrdinal() == null
        || ticket.getOrdinal() < 1
        || ticket.getState() == null
        || ticket.getDisplaySnapshot() == null
        || ticket.getDisplaySnapshot().schemaVersion() != 1
        || ticket.getDisplaySnapshot().ticketTypeName() == null
        || ticket.getDisplaySnapshot().ticketTypeName().isBlank())
      throw new IllegalArgumentException("Incomplete ticket view input");
    return mapToView(ticket);
  }

  @Mapping(target = "ticketId", source = "id")
  @Mapping(target = "ticketTypeName", source = "displaySnapshot.ticketTypeName")
  @Mapping(target = "seatLabel", source = "displaySnapshot.seatLabel")
  protected abstract TicketView mapToView(Ticket ticket);
}
