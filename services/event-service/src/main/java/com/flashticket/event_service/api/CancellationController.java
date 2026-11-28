package com.flashticket.event_service.api;

import com.flashticket.event_service.application.*;
import com.flashticket.event_service.persistence.CancellationRepository;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CancellationController {
  private final CancellationService service;
  private final CancellationTransactions transactions;

  public CancellationController(
      CancellationService service, CancellationTransactions transactions) {
    this.service = service;
    this.transactions = transactions;
  }

  @PostMapping("/organizer/events/{id}/cancel")
  public EventViews.Owner direct(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody EventCommands.Version input) {
    return service.direct(id, EventController.actor(jwt), input.rowVersion());
  }

  @PostMapping("/organizer/events/{id}/cancellation-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public CancellationRepository.Request request(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CancellationCommands.Request input) {
    return service.request(id, EventController.actor(jwt), input);
  }

  @GetMapping("/organizer/events/{id}/cancellation-requests")
  public EventViews.Page<CancellationRepository.Request> history(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return transactions.history(id, EventController.actor(jwt), true, null, size, cursor);
  }

  @GetMapping("/admin/cancellation-requests")
  public EventViews.Page<CancellationRepository.Request> pending(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "PENDING") String status,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return transactions.history(null, EventController.actor(jwt), false, status, size, cursor);
  }

  @GetMapping("/admin/events/{id}/cancellation-requests")
  public EventViews.Page<CancellationRepository.Request> adminHistory(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return transactions.history(id, EventController.actor(jwt), false, null, size, cursor);
  }

  @PostMapping("/admin/events/{id}/cancel")
  public EventViews.Owner adminCancel(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody EventCommands.Version input) {
    return transactions.cancel(id, EventController.actor(jwt), input.rowVersion(), false);
  }

  @PostMapping("/admin/cancellation-requests/{id}/approve")
  public CancellationRepository.Request approve(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody EventCommands.Version input) {
    return transactions.review(id, EventController.actor(jwt), input.rowVersion(), true, null);
  }

  @PostMapping("/admin/cancellation-requests/{id}/reject")
  public CancellationRepository.Request reject(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CancellationCommands.Reject input) {
    return transactions.review(
        id, EventController.actor(jwt), input.rowVersion(), false, input.reason());
  }
}
