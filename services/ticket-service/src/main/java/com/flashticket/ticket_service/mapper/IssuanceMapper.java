package com.flashticket.ticket_service.mapper;

import com.flashticket.ticket_service.dto.response.*;
import com.flashticket.ticket_service.entity.*;
import com.flashticket.ticket_service.enums.IssuanceState;
import java.util.List;
import org.mapstruct.*;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    uses = TicketMapper.class)
public interface IssuanceMapper {
  @IterableMapping(qualifiedByName = "ticketView")
  List<TicketView> toTicketViews(List<Ticket> tickets);

  default OrderTicketsView toView(Issuance issuance) {
    if (issuance == null || issuance.getState() == null)
      throw new IllegalArgumentException("Issuance state is required");
    if (issuance.getState() != IssuanceState.COMPLETED)
      return new OrderTicketsView(issuance.getState(), List.of());
    if (issuance.getExpectedTicketCount() == null
        || issuance.getExpectedTicketCount() < 1
        || issuance.getTickets() == null
        || issuance.getTickets().size() != issuance.getExpectedTicketCount())
      throw new IllegalArgumentException("Incomplete issuance view input");
    return new OrderTicketsView(issuance.getState(), toTicketViews(issuance.getTickets()));
  }
}
