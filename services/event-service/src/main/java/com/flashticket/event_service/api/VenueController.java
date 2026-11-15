package com.flashticket.event_service.api;

import com.flashticket.event_service.application.VenueService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/venues")
public class VenueController {
  private final VenueService service;

  public VenueController(VenueService service) {
    this.service = service;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public EventViews.Venue create(
      @RequestParam String name,
      @RequestParam String address,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String googleMapsUrl,
      @RequestParam(required = false) MultipartFile image) {
    return service.create(name, address, description, googleMapsUrl, image);
  }

  @PatchMapping(path = "/{venueId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public EventViews.Venue update(
      @PathVariable UUID venueId,
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String address,
      @RequestParam(required = false) String description,
      @RequestParam(required = false) String googleMapsUrl,
      @RequestParam(required = false) MultipartFile image) {
    return service.update(venueId, name, address, description, googleMapsUrl, image);
  }
}
