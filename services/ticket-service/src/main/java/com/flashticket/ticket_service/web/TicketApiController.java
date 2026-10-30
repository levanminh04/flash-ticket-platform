package com.flashticket.ticket_service.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public class TicketApiController {
  private final TicketOperations operations;

  public TicketApiController(TicketOperations operations) {
    this.operations = operations;
  }

  @GetMapping(
      value = "/api/v1/orders/{orderId}/tickets",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public TicketViews.TicketList listTickets(
      @PathVariable String orderId,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    TicketRequestContext.correlation(request, response);
    return operations.listTickets(TicketRequestContext.uuid(orderId), actor(principal));
  }

  @GetMapping(value = "/api/v1/tickets/{ticketId}/qr", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, String>> qr(
      @PathVariable String ticketId,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    TicketRequestContext.correlation(request, response);
    String token = operations.qr(TicketRequestContext.uuid(ticketId), actor(principal));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("qrToken", token));
  }

  @PostMapping(
      value = "/api/v1/orders/{orderId}/ticket-deliveries",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<TicketViews.DeliveryReceipt> redeliver(
      @PathVariable String orderId,
      @RequestHeader("Idempotency-Key") String requestKey,
      @RequestBody JsonNode body,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    TicketRequestContext.correlation(request, response);
    if (body == null || !body.isObject() || body.size() != 0) throw invalid();
    return ResponseEntity.accepted()
        .body(
            operations.redeliver(
                TicketRequestContext.uuid(orderId),
                actor(principal),
                TicketRequestContext.uuid(requestKey)));
  }

  @PostMapping(
      value = "/api/v1/events/{eventId}/check-ins",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public TicketViews.CheckInResult checkIn(
      @PathVariable String eventId,
      @RequestHeader("Idempotency-Key") String requestKey,
      @RequestBody JsonNode body,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response) {
    var correlation = TicketRequestContext.correlation(request, response);
    if (body == null
        || !body.isObject()
        || body.size() != 1
        || !body.has("qrToken")
        || !body.get("qrToken").isString()) throw invalid();
    String token = body.get("qrToken").asString();
    // Malformed but bounded strings still reach business logic to persist an UNKNOWN_QR audit.
    if (token.length() > 4096) throw invalid();
    return operations.checkIn(
        TicketRequestContext.uuid(eventId),
        actor(principal),
        TicketRequestContext.uuid(requestKey),
        token,
        correlation);
  }

  @GetMapping(
      value = "/internal/v1/orders/{orderId}/issuance",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public Object probe(
      @PathVariable String orderId, HttpServletRequest request, HttpServletResponse response) {
    TicketRequestContext.correlation(request, response);
    return operations.probe(TicketRequestContext.uuid(orderId));
  }

  private static String actor(Principal principal) {
    if (principal instanceof JwtAuthenticationToken authentication) {
      String subject = authentication.getToken().getSubject();
      if (subject != null && !subject.isBlank()) return subject;
    }
    throw new TicketProblem(401, "UNAUTHENTICATED");
  }

  private static TicketProblem invalid() {
    return new TicketProblem(400, "INVALID_REQUEST");
  }
}
