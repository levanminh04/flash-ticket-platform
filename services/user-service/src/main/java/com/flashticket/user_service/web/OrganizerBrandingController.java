package com.flashticket.user_service.web;

import com.flashticket.user_service.domain.UserModels.PublicOrganizer;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.service.OrganizerImages;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizers/me")
public class OrganizerBrandingController {
  private final UserStore store;
  private final OrganizerImages images;

  public OrganizerBrandingController(UserStore store, OrganizerImages images) {
    this.store = store;
    this.images = images;
  }

  @PostMapping(path = "/{kind:logo|banner}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public PublicOrganizer upload(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable String kind,
      @RequestPart("file") MultipartFile file) {
    String subject = UserController.subject(jwt);
    store.publicOrganizer(subject); // Check the caller's ACTIVE projection before any paid upload.
    String url = images.upload(file, kind);
    return store.updateBranding(subject, kind.equals("logo"), url);
  }
}
