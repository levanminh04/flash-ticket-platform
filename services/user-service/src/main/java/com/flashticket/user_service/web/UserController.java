package com.flashticket.user_service.web;

import com.flashticket.user_service.domain.UserModels.*;
import com.flashticket.user_service.persistence.UserStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
  public record UpdateProfile(@NotBlank @Size(max = 200) String displayName) {}

  public record Apply(
      @NotBlank @Size(max = 200) String organizationName,
      @NotBlank @Size(max = 2000) String shortDescription) {}

  private final UserStore store;

  public UserController(UserStore store) {
    this.store = store;
  }

  @GetMapping("/api/v1/me")
  public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
    return store.ensureUser(subject(jwt), displayName(jwt));
  }

  @PutMapping("/api/v1/me")
  public UserProfile update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfile input) {
    String subject = subject(jwt);
    store.ensureUser(subject, displayName(jwt));
    return store.updateDisplayName(subject, input.displayName().strip());
  }

  @PostMapping("/api/v1/organizer-applications")
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationView apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Apply input) {
    String subject = subject(jwt);
    store.ensureUser(subject, displayName(jwt));
    return ApplicationView.of(
        store.apply(subject, input.organizationName().strip(), input.shortDescription().strip()));
  }

  @GetMapping("/api/v1/organizer-applications")
  public Page<ApplicationView> mine(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String cursor) {
    return page(store, subject(jwt), null, size, cursor);
  }

  @GetMapping("/api/v1/organizer-applications/{id}")
  public ApplicationView application(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    canonicalId(id);
    Application a = store.application(id);
    if (!subject(jwt).equals(a.identitySubject()))
      throw new UserException(404, "APPLICATION_NOT_FOUND", "Application not found");
    return ApplicationView.of(a);
  }

  @GetMapping("/api/v1/organizers/{organizerSubject}")
  public PublicOrganizer organizer(@PathVariable String organizerSubject) {
    return store.publicOrganizer(pathSubject(organizerSubject));
  }

  @PutMapping("/api/v1/organizers/{organizerSubject}/follow")
  public FollowState follow(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String organizerSubject) {
    return store.follow(subject(jwt), pathSubject(organizerSubject));
  }

  @DeleteMapping("/api/v1/organizers/{organizerSubject}/follow")
  public FollowState unfollow(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String organizerSubject) {
    return store.unfollow(subject(jwt), pathSubject(organizerSubject));
  }

  @GetMapping("/api/v1/organizers/{organizerSubject}/follow")
  public FollowState following(
      @AuthenticationPrincipal Jwt jwt, @PathVariable String organizerSubject) {
    return store.followState(subject(jwt), pathSubject(organizerSubject));
  }

  @GetMapping("/internal/v1/users/{identitySubject}")
  public UserProfile internalUser(@PathVariable String identitySubject) {
    return store.user(pathSubject(identitySubject));
  }

  @GetMapping("/internal/v1/organizers/{identitySubject}")
  public PublicOrganizer internalOrganizer(@PathVariable String identitySubject) {
    return store.publicOrganizer(pathSubject(identitySubject));
  }

  static Page<ApplicationView> page(
      UserStore store, String subject, ApplicationStatus status, int size, String cursor) {
    if (size < 1 || size > 100)
      throw new UserException(400, "INVALID_PAGE_SIZE", "Page size must be between 1 and 100");
    if (cursor != null) canonicalId(cursor);
    var rows = store.applications(subject, status, cursor, size + 1);
    boolean more = rows.size() > size;
    var items = rows.stream().limit(size).map(ApplicationView::of).toList();
    return new Page<>(items, more ? items.getLast().id() : null);
  }

  private static String pathSubject(String subject) {
    if (subject == null || subject.isBlank())
      throw new UserException(400, "INVALID_SUBJECT", "Subject must not be blank");
    return subject;
  }

  public static String subject(Jwt jwt) {
    String sub = jwt == null ? null : jwt.getSubject();
    if (sub == null || sub.isBlank())
      throw new UserException(401, "INVALID_SUBJECT", "Token has no subject");
    return sub;
  }

  private static String displayName(Jwt jwt) {
    Object name = jwt.getClaim("name");
    return name instanceof String s && !s.isBlank() && s.length() <= 200 ? s.strip() : null;
  }

  public static void canonicalId(String value) {
    if (value == null
        || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
      throw new UserException(400, "INVALID_ID", "Expected a canonical UUID");
  }
}
