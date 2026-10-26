package com.flashticket.event_service.api;

import com.flashticket.event_service.application.EventService;
import com.flashticket.event_service.web.EventProblem;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class EventController {
  private final EventService service;

  public EventController(EventService service) {
    this.service = service;
  }

  @PostMapping("/organizer/events")
  @ResponseStatus(HttpStatus.CREATED)
  public EventViews.Owner create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EventCommands.Draft input) {
    return service.create(actor(jwt), input);
  }

  @PutMapping("/organizer/events/{id}")
  public EventViews.Owner save(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody EventCommands.Save input) {
    return service.save(id, actor(jwt), input);
  }

  @GetMapping("/organizer/events/{id}")
  public EventViews.Owner own(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.readOwned(id, actor(jwt));
  }

  @GetMapping("/organizer/events")
  public EventViews.Page<EventViews.Summary> ownList(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return service.listOwner(actor(jwt), status, size, cursor);
  }

  @PostMapping("/organizer/events/{id}/submit")
  public EventViews.Owner submit(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody EventCommands.Version input) {
    return service.submit(id, actor(jwt), input.rowVersion());
  }

  @PostMapping("/organizer/events/{id}/publish")
  public EventViews.Owner publish(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody EventCommands.Version input) {
    return service.publish(id, actor(jwt), input.rowVersion());
  }

  @GetMapping("/admin/events")
  public EventViews.Page<EventViews.Summary> adminList(
      @RequestParam(defaultValue = "PENDING_APPROVAL") String status,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return service.listAdmin(status, size, cursor);
  }

  @GetMapping("/admin/events/{id}")
  public EventViews.Owner admin(@PathVariable UUID id) {
    return service.readAdmin(id);
  }

  @PostMapping("/admin/events/{id}/approve")
  public EventViews.Owner approve(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody EventCommands.Approval input) {
    return service.approve(id, actor(jwt), input);
  }

  @PostMapping("/admin/events/{id}/return-to-draft")
  public EventViews.Owner reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody EventCommands.Version input) {
    return service.returnToDraft(id, actor(jwt), input.rowVersion());
  }

  @GetMapping("/events/{id}")
  public EventViews.Public publicEvent(@PathVariable UUID id) {
    return service.readPublic(id);
  }

  @GetMapping("/events")
  public EventViews.Page<EventViews.Public> publicList(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return service.listPublic(q, categoryId, size, cursor);
  }

  @GetMapping("/categories")
  public List<EventViews.Lookup> categories() {
    return service.categories();
  }

  @GetMapping("/venues")
  public List<EventViews.Lookup> venues() {
    return service.venues();
  }

  private static String actor(Jwt jwt) {
    String subject = jwt == null ? null : jwt.getSubject();
    if (subject == null || subject.isBlank()) throw new EventProblem(401, "UNAUTHENTICATED", null);
    return subject;
  }
}
