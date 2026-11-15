package com.flashticket.event_service.application;

import com.flashticket.event_service.api.EventViews;
import com.flashticket.event_service.integration.EventImageStorage;
import com.flashticket.event_service.persistence.EventRepository;
import com.flashticket.event_service.web.EventProblem;
import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VenueService {
  private final EventRepository repository;
  private final EventImageStorage storage;

  public VenueService(EventRepository repository, EventImageStorage storage) {
    this.repository = repository;
    this.storage = storage;
  }

  public EventViews.Venue create(
      String name, String address, String description, String googleMapsUrl, MultipartFile image) {
    UUID id = UUID.randomUUID();
    String cleanName = requiredText(name, 255, "name");
    String cleanAddress = requiredText(address, 500, "address");
    String cleanDescription = text(description, 2000, "description");
    String cleanMapsUrl = mapsUrl(googleMapsUrl);
    return repository.createVenue(
        id, cleanName, cleanAddress, cleanDescription, cleanMapsUrl, upload(id, image));
  }

  public EventViews.Venue update(
      UUID id,
      String name,
      String address,
      String description,
      String googleMapsUrl,
      MultipartFile image) {
    String cleanName = name == null ? null : requiredText(name, 255, "name");
    String cleanAddress = address == null ? null : requiredText(address, 500, "address");
    String cleanDescription = text(description, 2000, "description");
    String cleanMapsUrl = mapsUrl(googleMapsUrl);
    if (image != null && !repository.venueExists(id)) throw missing();
    return repository
        .updateVenue(id, cleanName, cleanAddress, cleanDescription, cleanMapsUrl, upload(id, image))
        .orElseThrow(VenueService::missing);
  }

  private String upload(UUID id, MultipartFile image) {
    if (image == null) return null;
    try {
      return storage.uploadVenue(id, image).objectReference();
    } catch (EventProblem problem) {
      if (!"file".equals(problem.field())) throw problem;
      throw new EventProblem(problem.status(), problem.code(), "image");
    }
  }

  private static String requiredText(String value, int max, String field) {
    String clean = text(value, max, field);
    if (clean == null || clean.isEmpty()) throw invalid(field);
    return clean;
  }

  private static String text(String value, int max, String field) {
    if (value == null) return null;
    String clean = value.strip();
    if (clean.length() > max) throw invalid(field);
    return clean;
  }

  private static String mapsUrl(String value) {
    String clean = text(value, 500, "googleMapsUrl");
    if (clean == null || clean.isEmpty()) return clean;
    URI uri;
    try {
      uri = URI.create(clean);
    } catch (IllegalArgumentException ex) {
      throw invalid("googleMapsUrl");
    }
    String path = uri.getPath();
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getUserInfo() != null
        || uri.getPort() != -1
        || path == null
        || !isMapsPath(host, path)) throw invalid("googleMapsUrl");
    return clean;
  }

  private static boolean isMapsPath(String host, String path) {
    if ("maps.app.goo.gl".equalsIgnoreCase(host)) return path.length() > 1;
    if ("maps.google.com".equalsIgnoreCase(host) || "maps.google.com.vn".equalsIgnoreCase(host))
      return true;
    return ("www.google.com".equalsIgnoreCase(host)
            || "google.com".equalsIgnoreCase(host)
            || "www.google.com.vn".equalsIgnoreCase(host)
            || "google.com.vn".equalsIgnoreCase(host))
        && (path.equals("/maps") || path.startsWith("/maps/"));
  }

  private static EventProblem invalid(String field) {
    return new EventProblem(400, "INVALID_REQUEST", field);
  }

  private static EventProblem missing() {
    return new EventProblem(404, "VENUE_NOT_FOUND", null);
  }
}
