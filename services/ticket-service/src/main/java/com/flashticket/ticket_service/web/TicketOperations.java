package com.flashticket.ticket_service.web;

import java.util.UUID;

/** Authenticated HTTP operations; resource ownership is enforced by the business implementation. */
public interface TicketOperations {
  TicketViews.TicketList listTickets(UUID orderId, String actor);

  String qr(UUID ticketId, String actor);

  TicketViews.DeliveryReceipt redeliver(UUID orderId, String actor, UUID requestKey);

  TicketViews.CheckInResult checkIn(
      UUID eventId, String actor, UUID requestKey, String qrToken, UUID correlationId);

  Object probe(UUID orderId);
}
