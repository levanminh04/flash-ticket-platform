package com.flashticket.event_service.integration;

import java.util.UUID;

/** Event-side proposed read contracts; enable only after agreement with the source owner. */
public interface EventSummaries {
  Financial financial(UUID event, String organizer, boolean requireFresh);

  Sales sales(UUID event, String organizer);

  record Financial(
      Integer schemaVersion,
      UUID eventId,
      String organizerSubject,
      String asOf,
      Boolean hasCollectedOrders,
      String currency,
      String validGross,
      String validRefunds,
      String feeAmount,
      String netAmount) {}

  record Sales(
      Integer schemaVersion,
      UUID eventId,
      String organizerSubject,
      String asOf,
      String purchasedTicketCount) {}
}
