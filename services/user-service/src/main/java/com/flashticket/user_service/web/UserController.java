package com.flashticket.user_service.web;

import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

import com.flashticket.user_service.domain.UserModels.*;
import com.flashticket.user_service.persistence.UserStore;
import com.flashticket.user_service.service.OrganizerImages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.conversions.Bson;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UserController {
  private static final DateTimeFormatter BIRTH_DATE =
      DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

  public record Apply(
      @NotBlank @Size(max = 200) String organizationName,
      @NotBlank @Size(max = 2000) String shortDescription,
      @NotBlank @Size(max = 254) String contactEmail,
      @NotBlank @Size(max = 20) String contactPhone,
      @Size(max = 500) String websiteUrl,
      @NotBlank @Size(max = 500) String socialPageUrl,
      @NotBlank @Size(max = 500) String headquartersAddress,
      @NotBlank @Size(max = 14) String taxCode) {}

  private final UserStore store;
  private final OrganizerImages images;

  public UserController(UserStore store) {
    this(store, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public UserController(UserStore store, OrganizerImages images) {
    this.store = store;
    this.images = images;
  }

  @GetMapping("/api/v1/me")
  public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
    return withEmail(store.ensureUser(subject(jwt), displayName(jwt)), jwt);
  }

  @PutMapping("/api/v1/me")
  public UserProfile update(
      @AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, Object> input) {
    String subject = subject(jwt);
    List<Bson> changes = new ArrayList<>();
    if (input.containsKey("displayName")) {
      String name = profileText(input.get("displayName"), 200);
      if (name == null) throw invalidProfile();
      changes.add(set("displayName", name));
    }
    for (String field : List.of("dateOfBirth", "address", "phoneNumber")) {
      if (!input.containsKey(field)) continue;
      String value = profileText(input.get(field), field.equals("address") ? 500 : 20);
      if (value != null && field.equals("dateOfBirth")) {
        try {
          if (!value.matches("[0-9]{2}-[0-9]{2}-[0-9]{4}")) throw invalidProfile();
          LocalDate date = LocalDate.parse(value, BIRTH_DATE);
          if (date.getYear() < 1 || date.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
            throw invalidProfile();
        } catch (java.time.format.DateTimeParseException ex) {
          throw invalidProfile();
        }
      }
      if (value != null && field.equals("phoneNumber")) {
        value = value.replaceAll("[ .-]", "");
        if (!value.matches("(?:0|\\+84)(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7}"))
          throw invalidProfile();
        if (value.startsWith("0")) value = "+84" + value.substring(1);
      }
      changes.add(value == null ? unset(field) : set(field, value));
    }
    store.ensureUser(subject, displayName(jwt));
    return withEmail(store.updateProfile(subject, changes), jwt);
  }

  @PutMapping(path = "/api/v1/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public UserProfile uploadAvatar(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
    String subject = subject(jwt);
    store.ensureUser(subject, displayName(jwt));
    String url = images.upload(file, "avatar");
    return withEmail(
        store.updateProfile(subject, new ArrayList<>(List.of(set("avatarUrl", url)))), jwt);
  }

  private static UserException invalidProfile() {
    return new UserException(400, "INVALID_PROFILE", "Invalid profile field");
  }

  private static String profileText(Object value, int maxLength) {
    if (value == null) return null;
    if (!(value instanceof String text) || text.length() > maxLength) throw invalidProfile();
    return text.isBlank() ? null : text.strip();
  }

  private static UserProfile withEmail(UserProfile profile, Jwt jwt) {
    Object email = jwt.getClaim("email");
    return new UserProfile(
        profile.userId(),
        profile.identitySubject(),
        profile.displayName(),
        email instanceof String text && !text.isBlank() ? text : null,
        profile.dateOfBirth(),
        profile.address(),
        profile.phoneNumber(),
        profile.avatarUrl());
  }

  @PostMapping("/api/v1/organizer-applications")
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationView apply(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Apply input) {
    String email = input.contactEmail().strip().toLowerCase(java.util.Locale.ROOT);
    if (!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
      throw invalidApplication();
    String phone = input.contactPhone().strip().replaceAll("[ .-]", "");
    if (!phone.matches(
        "(?:0|\\+84)(?:2[0-9]{9}|(?:3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-9])[0-9]{7})"))
      throw invalidApplication();
    if (phone.startsWith("0")) phone = "+84" + phone.substring(1);
    String website =
        input.websiteUrl() == null || input.websiteUrl().isBlank()
            ? null
            : input.websiteUrl().strip();
    if (website != null && !website.matches("^https://\\S+$")) throw invalidApplication();
    String socialPage = input.socialPageUrl().strip();
    if (!socialPage.matches("^https://\\S+$")) throw invalidApplication();
    String address = input.headquartersAddress().strip();
    String tax = input.taxCode().strip().replaceAll("\\s+", "");
    if (!tax.matches("^[0-9]{10}([0-9]{2}|-?[0-9]{3})?$")) throw invalidApplication();
    String subject = subject(jwt);
    store.ensureUser(subject, displayName(jwt));
    return ApplicationView.of(
        store.apply(
            subject,
            input.organizationName().strip(),
            input.shortDescription().strip(),
            email,
            phone,
            website,
            socialPage,
            address,
            tax));
  }

  private static UserException invalidApplication() {
    return new UserException(400, "INVALID_APPLICATION", "Invalid organizer application field");
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
