package com.flashticket.event_service.application;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.integration.EventImageStorage;
import com.flashticket.event_service.web.EventProblem;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EventImageService {
  private final EventImageTransactions transactions;
  private final EventImageStorage storage;

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Intentionally shares Spring-managed transaction/storage services; no accessor exposes them.")
  public EventImageService(EventImageTransactions transactions, EventImageStorage storage) {
    this.transactions = transactions;
    this.storage = storage;
  }

  public EventViews.Owner upload(
      UUID eventId,
      String actor,
      String version,
      UUID imageId,
      Integer position,
      MultipartFile file) {
    transactions.prepare(eventId, actor, version, imageId, position);
    var uploaded = storage.upload(eventId, file); // No DB transaction/lock during HTTP.
    try {
      return transactions.attach(
          eventId, actor, version, imageId, position, uploaded.objectReference());
    } catch (RuntimeException failure) {
      try {
        // Only compensate a domain rejection before any metadata write. A separate read
        // cannot prove a timed-out COMMIT will not subsequently succeed.
        if (!(failure instanceof EventProblem))
          throw new IllegalStateException("Ambiguous transaction outcome");
        if (!transactions.referenced(uploaded.objectReference())) storage.discard(uploaded);
      } catch (RuntimeException cleanup) {
        LoggerFactory.getLogger(EventImageService.class)
            .atWarn()
            .addKeyValue("eventName", "event.image-cleanup-deferred")
            .addKeyValue("eventId", eventId.toString())
            .addKeyValue("errorCode", "IMAGE_CLEANUP_DEFERRED")
            .log("Unattached upload requires storage reconciliation asset={}", uploaded.publicId());
      }
      throw failure;
    }
  }
}
