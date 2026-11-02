package com.flashticket.event_service.api;

import com.flashticket.event_service.application.EventImageService;
import com.flashticket.event_service.application.EventImageTransactions;
import com.flashticket.event_service.application.EventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizer/events/{id}/images")
public class EventImageController {
  public record Order(@NotBlank String rowVersion, @NotNull @PositiveOrZero Integer displayOrder) {}

  private final EventService events;
  private final EventImageService images;
  private final EventImageTransactions transactions;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed application services; no accessor exposes them.")
  public EventImageController(
      EventService events, EventImageService images, EventImageTransactions transactions) {
    this.events = events;
    this.images = images;
    this.transactions = transactions;
  }

  @GetMapping
  public List<EventViews.Image> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return events.readOwned(id, EventController.actor(jwt)).images();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public EventViews.Owner upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam String rowVersion,
      @RequestParam Integer displayOrder,
      @RequestPart("file") MultipartFile file) {
    return images.upload(id, EventController.actor(jwt), rowVersion, null, displayOrder, file);
  }

  @PutMapping(path = "/{imageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public EventViews.Owner replace(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID imageId,
      @RequestParam String rowVersion,
      @RequestPart("file") MultipartFile file) {
    return images.upload(id, EventController.actor(jwt), rowVersion, imageId, null, file);
  }

  @DeleteMapping("/{imageId}")
  public EventViews.Owner remove(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID imageId,
      @RequestParam String rowVersion) {
    return transactions.remove(id, EventController.actor(jwt), rowVersion, imageId);
  }

  @PatchMapping("/{imageId}")
  public EventViews.Owner order(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @PathVariable UUID imageId,
      @Valid @RequestBody Order input) {
    return transactions.reorder(
        id, EventController.actor(jwt), input.rowVersion(), imageId, input.displayOrder());
  }
}
