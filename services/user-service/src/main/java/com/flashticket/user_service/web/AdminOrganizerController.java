package com.flashticket.user_service.web;

import com.flashticket.user_service.domain.UserModels.ApplicationStatus;
import com.flashticket.user_service.domain.UserModels.ApplicationView;
import com.flashticket.user_service.domain.UserModels.Page;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.service.OrganizerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/organizer-applications")
public class AdminOrganizerController {
  public record Reject(@NotBlank @Size(max = 2000) String reason) {}

  private final UserStore store;
  private final OrganizerService organizers;

  public AdminOrganizerController(UserStore store, OrganizerService organizers) {
    this.store = store;
    this.organizers = organizers;
  }

  @GetMapping
  public Page<ApplicationView> applications(
      @RequestParam(required = false) ApplicationStatus status,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return UserController.page(store, null, status, size, cursor);
  }

  @GetMapping("/{id}")
  public ApplicationView application(@PathVariable String id) {
    UserController.canonicalId(id);
    return ApplicationView.of(store.application(id));
  }

  @PostMapping("/{id}/approve")
  public ApplicationView approve(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    UserController.canonicalId(id);
    return ApplicationView.of(organizers.approve(id, UserController.subject(jwt)));
  }

  @PostMapping("/{id}/reject")
  public ApplicationView reject(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody Reject input) {
    UserController.canonicalId(id);
    return ApplicationView.of(
        organizers.reject(id, UserController.subject(jwt), input.reason().strip()));
  }
}
