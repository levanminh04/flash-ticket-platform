package com.flashticket.event_service.application;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.domain.ContractRules;
import com.flashticket.event_service.domain.EventData;
import com.flashticket.event_service.domain.EventData.ImageRole;
import com.flashticket.event_service.observability.EventAudit;
import com.flashticket.event_service.persistence.EventRepository;
import com.flashticket.event_service.web.EventProblem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventImageTransactions {
  private final EventRepository repository;
  private final EventAudit audit;
  private final int maxCount;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "Intentionally shares Spring-managed repository/audit; neither is exposed.")
  public EventImageTransactions(
      EventRepository repository,
      EventAudit audit,
      @Value("${event.images.max-count:20}") int maxCount) {
    this.repository = repository;
    this.audit = audit;
    this.maxCount = maxCount;
  }

  @jakarta.annotation.PostConstruct
  void validateConfiguration() {
    if (maxCount < 1) throw new IllegalArgumentException("Invalid Event image count limit");
  }

  @Transactional(readOnly = true, timeout = 5)
  public ImageRole prepare(
      UUID id, String actor, String version, UUID imageId, Integer position, ImageRole role) {
    var event = writable(id, actor, version, false);
    return validateUpload(event, imageId, position, role);
  }

  @Transactional(timeout = 5)
  public EventViews.Owner attach(
      UUID id,
      String actor,
      String version,
      UUID imageId,
      Integer position,
      ImageRole role,
      String reference) {
    var event = writable(id, actor, version, true);
    validateUpload(event, imageId, position, role);
    var images = new ArrayList<>(event.images());
    if (imageId == null)
      images.add(
          new EventData.Image(
              UUID.randomUUID(), reference, role == ImageRole.BANNER ? 0 : position, role));
    else
      images.replaceAll(
          i ->
              i.id().equals(imageId)
                  ? new EventData.Image(i.id(), reference, i.displayOrder(), i.role())
                  : i);
    return persist(event, actor, images, "upload-image");
  }

  @Transactional(timeout = 5)
  public EventViews.Owner remove(UUID id, String actor, String version, UUID imageId) {
    var event = writable(id, actor, version, true);
    image(event, imageId);
    return persist(
        event,
        actor,
        event.images().stream().filter(i -> !i.id().equals(imageId)).toList(),
        "remove-image");
  }

  @Transactional(timeout = 5)
  public EventViews.Owner reorder(
      UUID id, String actor, String version, UUID imageId, int position) {
    var event = writable(id, actor, version, true);
    var selected = image(event, imageId);
    if (selected.role() != ImageRole.GALLERY || position < 0)
      throw EventProblem.invalid("displayOrder");
    var images =
        event.images().stream()
            .map(
                i ->
                    i.id().equals(imageId)
                        ? new EventData.Image(i.id(), i.objectReference(), position, i.role())
                        : i.role() == ImageRole.GALLERY && i.displayOrder() == position
                            ? new EventData.Image(
                                i.id(), i.objectReference(), selected.displayOrder(), i.role())
                            : i)
            .toList();
    return persist(event, actor, images, "reorder-image");
  }

  @Transactional(readOnly = true, timeout = 5)
  public boolean referenced(String reference) {
    return repository.imageReferenced(reference);
  }

  private EventViews.Owner persist(
      EventData event, String actor, List<EventData.Image> images, String operation) {
    repository.replaceImages(
        event,
        images.stream().sorted(Comparator.comparingInt(EventData.Image::displayOrder)).toList());
    var next = repository.find(event.id(), false).orElseThrow(EventProblem::missing);
    audit.committed(operation, event.status(), next, actor);
    return EventViews.owner(next);
  }

  private ImageRole validateUpload(
      EventData event, UUID imageId, Integer position, ImageRole role) {
    if (imageId != null) return image(event, imageId).role();
    if (event.images().size() >= maxCount)
      throw new EventProblem(422, "IMAGE_LIMIT_REACHED", "images");
    if (role == ImageRole.BANNER) {
      if (event.images().stream().anyMatch(i -> i.role() == ImageRole.BANNER))
        throw EventProblem.conflict("BANNER_ALREADY_EXISTS");
    } else {
      if (position == null || position < 0) throw EventProblem.invalid("displayOrder");
      if (event.images().stream()
          .anyMatch(i -> i.role() == ImageRole.GALLERY && i.displayOrder() == position))
        throw EventProblem.conflict("IMAGE_ORDER_CONFLICT");
    }
    return role;
  }

  private EventData writable(UUID id, String actor, String expected, boolean lock) {
    var event = repository.find(id, lock).orElseThrow(EventProblem::missing);
    if (!event.organizer().equals(actor)) throw EventProblem.missing();
    if (!"DRAFT".equals(event.status())) throw EventProblem.conflict("INVALID_EVENT_STATE");
    if (event.rowVersion() != ContractRules.unsigned(expected, "rowVersion", true))
      throw EventProblem.conflict("STALE_EVENT_VERSION");
    return event;
  }

  private static EventData.Image image(EventData event, UUID id) {
    return event.images().stream()
        .filter(i -> i.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new EventProblem(404, "IMAGE_NOT_FOUND", null));
  }
}
