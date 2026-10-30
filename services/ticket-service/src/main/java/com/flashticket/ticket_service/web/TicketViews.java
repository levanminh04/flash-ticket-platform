package com.flashticket.ticket_service.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TicketViews {
  private TicketViews() {}

  public record TicketView(
      UUID ticketId,
      UUID eventId,
      UUID orderItemId,
      int ordinal,
      String ticketTypeName,
      String seatLabel,
      String state) {}

  public record TicketList(String issuanceState, List<TicketView> tickets) {
    public TicketList {
      tickets = List.copyOf(tickets);
    }
  }

  public record DeliveryReceipt(UUID deliveryId, String state) {}

  public record CheckInResult(
      UUID attemptId,
      UUID eventId,
      UUID ticketId,
      String result,
      boolean succeeded,
      Instant processedAt) {}
}
