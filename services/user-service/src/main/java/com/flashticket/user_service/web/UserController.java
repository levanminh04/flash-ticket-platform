package com.flashticket.user_service.web;

import static com.mongodb.client.model.Updates.set;
import static com.mongodb.client.model.Updates.unset;

import com.flashticket.user_service.domain.UserModels.*;
import com.flashticket.user_service.persistence.UserStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
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
      @NotBlank @Size(max = 2000) String shortDescription) {}

  private final UserStore store;

  public UserController(UserStore store) {
    this.store = store;
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
    if (file.isEmpty() || file.getSize() > 2 * 1024 * 1024) throw invalidAvatar();
    try (var stream = new javax.imageio.stream.MemoryCacheImageInputStream(file.getInputStream())) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw invalidAvatar();
      var reader = readers.next();
      try {
        if (!List.of("jpeg", "png")
            .contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT)))
          throw invalidAvatar();
        reader.setInput(stream, true, true);
        if (reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) throw invalidAvatar();
        var original = reader.read(0);
        int size = Math.min(256, Math.min(original.getWidth(), original.getHeight()));
        var avatar =
            new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int side = Math.min(original.getWidth(), original.getHeight());
        int x = (original.getWidth() - side) / 2;
        int y = (original.getHeight() - side) / 2;
        var graphics = avatar.createGraphics();
        try {
          graphics.drawImage(original, 0, 0, size, size, x, y, x + side, y + side, null);
        } finally {
          graphics.dispose();
        }
        var output = new java.io.ByteArrayOutputStream();
        ImageIO.write(avatar, "jpeg", output);
        String url =
            "data:image/jpeg;base64,"
                + java.util.Base64.getEncoder().encodeToString(output.toByteArray());
        String subject = subject(jwt);
        store.ensureUser(subject, displayName(jwt));
        return withEmail(
            store.updateProfile(subject, new ArrayList<>(List.of(set("avatarUrl", url)))), jwt);
      } finally {
        reader.dispose();
      }
    } catch (IOException ex) {
      throw invalidAvatar();
    }
  }

  private static UserException invalidAvatar() {
    return new UserException(
        400, "INVALID_AVATAR", "Use JPEG or PNG up to 2 MB and 2048 pixels per side");
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
