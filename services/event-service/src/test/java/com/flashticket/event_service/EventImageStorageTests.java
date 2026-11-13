package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.event_service.integration.EventImageStorage;
import com.flashticket.event_service.web.EventProblem;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

class EventImageStorageTests {
  static final UUID EVENT = UUID.fromString("00000000-0000-4000-8000-000000000001");

  static byte[] png(int width, int height) throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", bytes);
    return bytes.toByteArray();
  }

  static MockClientHttpResponse response(String body, HttpStatus status) {
    var response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    return response;
  }

  static EventImageStorage storage(RestClient.Builder builder, int bytes, long pixels) {
    return new EventImageStorage(
        builder,
        "test",
        "fixture-key",
        "fixture-secret",
        "flash-ticket/events",
        bytes,
        pixels,
        Duration.ofSeconds(2));
  }

  static MockMultipartFile file() throws Exception {
    return new MockMultipartFile("file", "../../secret.png", "image/png", png(2, 2));
  }

  static String publicId(byte[] body) {
    var match =
        Pattern.compile("name=\"public_id\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\n([^\\r]+)")
            .matcher(new String(body, StandardCharsets.ISO_8859_1));
    assertThat(match.find()).isTrue();
    return match.group(1);
  }

  // A 4x2 lossless WebP and a 4x2 two-frame WebP generated with Pillow.
  private static final byte[] WEBP =
      Base64.getDecoder().decode("UklGRhwAAABXRUJQVlA4TA8AAAAvA0AAAAcQ/Y/+ByKi/wEA");

  @Test
  void uploadsStaticWebpAndAcceptsItsCloudinaryUrl() {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (request, body, execution) -> {
                  String id = publicId(body);
                  assertThat(new String(body, StandardCharsets.ISO_8859_1))
                      .contains(new String(WEBP, StandardCharsets.ISO_8859_1));
                  return response(
                      "{\"public_id\":\""
                          + id
                          + "\",\"secure_url\":\"https://res.cloudinary.com/test/image/upload/v42/"
                          + id
                          + ".webp\"}",
                      HttpStatus.OK);
                });
    assertThat(storage(builder, 1024, 100).upload(EVENT, webp(WEBP)).objectReference())
        .endsWith(".webp");
  }

  @Test
  void rejectsAnimatedOrTruncatedWebpBeforeUpload() {
    byte[] animated =
        Base64.getDecoder()
            .decode(
                "UklGRsQAAABXRUJQVlA4WAoAAAACAAAAAwAAAQAAQU5JTQYAAAAAAAAAAABBTk1GSgAAAAAAAAAAAAMAAAEAAGQAAAJW"
                    + "UDggMgAAADABAJ0BKgQAAgABQCYloAADcAD+8ut///mwP/bz/wR6Af//0uD//pcH//S4P/SkAAAAQU5NRkYAAAAAAAA"
                    + "AAAADAAABAABkAAAAVlA4IC4AAAA0AQCdASoEAAIAAAAmJaAAA3AA/vtV4///S4P/+lwf/9Lg/9Lg//rV5Vesq6AA");
    var storage = storage(RestClient.builder(), 1024, 100);
    for (byte[] bytes : List.of(animated, Arrays.copyOf(WEBP, WEBP.length - 3))) {
      assertThatThrownBy(() -> storage.upload(EVENT, webp(bytes)))
          .isInstanceOfSatisfying(EventProblem.class, p -> assertThat(p.status()).isEqualTo(422));
    }
  }

  private static MockMultipartFile webp(byte[] bytes) {
    return new MockMultipartFile("file", "event.webp", "image/webp", bytes);
  }

  @Test
  void uploadsVerifiedBytesToGeneratedEventAssetAndValidatesProviderIdentity() throws Exception {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (request, body, execution) -> {
                  assertThat(request.getURI().toString())
                      .isEqualTo("https://api.cloudinary.com/v1_1/test/image/upload");
                  assertThat(request.getHeaders().getFirst("Authorization")).startsWith("Basic ");
                  String id = publicId(body);
                  assertThat(id).startsWith("flash-ticket/events/" + EVENT + "/");
                  assertThat(new String(body, StandardCharsets.ISO_8859_1))
                      .contains("overwrite")
                      .doesNotContain("../../secret");
                  return response(
                      "{\"public_id\":\""
                          + id
                          + "\",\"secure_url\":\"https://res.cloudinary.com/test/image/upload/v42/"
                          + id
                          + ".png\",\"extra\":true}",
                      HttpStatus.OK);
                });
    var uploaded = storage(builder, 1024, 100).upload(EVENT, file());
    assertThat(uploaded.objectReference())
        .isEqualTo(
            "https://res.cloudinary.com/test/image/upload/v42/" + uploaded.publicId() + ".png");
  }

  @Test
  void rejectsOversizedPixelsAndBytesBeforeProviderCall() throws Exception {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (r, b, e) -> {
                  throw new AssertionError("No network for invalid image");
                });
    var file = file();
    assertThatThrownBy(() -> storage(builder, 1024, 1).upload(EVENT, file))
        .isInstanceOfSatisfying(EventProblem.class, p -> assertThat(p.status()).isEqualTo(413));
    assertThatThrownBy(() -> storage(builder, 8, 100).upload(EVENT, file))
        .isInstanceOfSatisfying(EventProblem.class, p -> assertThat(p.status()).isEqualTo(413));
  }

  @Test
  void rejectsMimeSpoofSvgWebpEmptyAndTruncatedData() throws Exception {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (r, b, e) -> {
                  throw new AssertionError("No network for invalid image");
                });
    for (var file :
        List.of(
            new MockMultipartFile("file", "x.jpg", "image/jpeg", png(2, 2)),
            new MockMultipartFile(
                "file", "x.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile("file", "x.webp", "image/webp", png(2, 2)),
            new MockMultipartFile("file", "x.png", "image/png", new byte[0]),
            new MockMultipartFile("file", "x.png", "image/png", Arrays.copyOf(png(2, 2), 25))))
      assertThatThrownBy(() -> storage(builder, 1024, 100).upload(EVENT, file))
          .isInstanceOf(EventProblem.class);
  }

  @Test
  void rejectsUntrustedUrlOrDifferentPublicIdWithoutLeakingCredentials() throws Exception {
    for (String value :
        List.of(
            "http://res.cloudinary.com/test/image/upload/",
            "https://evil.invalid/",
            "https://res.cloudinary.com/other/image/upload/")) {
      var builder =
          RestClient.builder()
              .requestInterceptor(
                  (r, b, e) ->
                      response(
                          "{\"public_id\":\""
                              + publicId(b)
                              + "\",\"secure_url\":\""
                              + value
                              + publicId(b)
                              + ".png\"}",
                          HttpStatus.OK));
      assertUnavailable(storage(builder, 1024, 100), file());
    }
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (r, b, e) ->
                    response(
                        "{\"public_id\":\"other\",\"secure_url\":\"https://res.cloudinary.com/test/image/upload/other.png\"}",
                        HttpStatus.OK));
    assertUnavailable(storage(builder, 1024, 100), file());
  }

  @Test
  void providerFailuresAndMissingCredentialsReturnSanitized503() throws Exception {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (r, b, e) ->
                    response("fixture-secret private provider response", HttpStatus.BAD_REQUEST));
    assertUnavailable(storage(builder, 1024, 100), file());
    assertUnavailable(
        new EventImageStorage(
            RestClient.builder(),
            "",
            "",
            "",
            "flash-ticket/events",
            1024,
            100,
            Duration.ofSeconds(2)),
        file());
  }

  @Test
  void uploadsActualJpegWithoutChangingItsAspectRatio() throws Exception {
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (request, body, execution) -> {
                  String id = publicId(body);
                  assertThat(new String(body, StandardCharsets.ISO_8859_1))
                      .doesNotContain("transformation");
                  return response(
                      "{\"public_id\":\""
                          + id
                          + "\",\"secure_url\":\"https://res.cloudinary.com/test/image/upload/v2/"
                          + id
                          + ".jpg\"}",
                      HttpStatus.OK);
                });
    assertThat(
            storage(builder, 1024, 100)
                .upload(
                    EVENT,
                    new MockMultipartFile("file", "x.jpg", "image/jpeg", bytes.toByteArray()))
                .objectReference())
        .endsWith(".jpg");
  }

  @Test
  void failedDestroyReplyIsNotReportedAsSuccessfulCompensation() {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (r, b, e) -> response("{\"result\":\"not allowed\"}", HttpStatus.OK));
    var uploaded =
        new EventImageStorage.Uploaded(
            "unused", "flash-ticket/events/" + EVENT + "/" + UUID.randomUUID());
    assertThatThrownBy(() -> storage(builder, 1024, 100).discard(uploaded))
        .isInstanceOfSatisfying(EventProblem.class, p -> assertThat(p.status()).isEqualTo(503));
  }

  @Test
  void rejectsBannerThatIsTooSmallOrHasWrongAspectRatio() throws Exception {
    var storage = storage(RestClient.builder(), 5242880, 16000000);
    var wrongRatio = new MockMultipartFile("file", "x.png", "image/png", png(1280, 800));
    for (var file : List.of(file(), wrongRatio)) {
      assertThatThrownBy(() -> storage.uploadBanner(EVENT, file))
          .isInstanceOfSatisfying(
              EventProblem.class,
              p -> assertThat(p.getMessage()).isEqualTo("INVALID_BANNER_DIMENSIONS"));
    }
  }

  private void assertUnavailable(EventImageStorage storage, MockMultipartFile file) {
    assertThatThrownBy(() -> storage.upload(EVENT, file))
        .isInstanceOfSatisfying(
            EventProblem.class,
            p -> {
              assertThat(p.status()).isEqualTo(503);
              assertThat(p.getCause()).isNull();
              assertThat(p.getMessage()).doesNotContain("fixture-secret", "private provider");
            });
  }

  @Test
  void compensatesOnlyGeneratedAssetAndChecksProviderDestroyResult() {
    AtomicInteger calls = new AtomicInteger();
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (r, b, e) -> {
                  calls.incrementAndGet();
                  assertThat(r.getURI().getPath()).endsWith("/image/destroy");
                  assertThat(new String(b, StandardCharsets.UTF_8))
                      .contains("public_id=flash-ticket%2Fevents%2F");
                  return response("{\"result\":\"ok\"}", HttpStatus.OK);
                });
    storage(builder, 1024, 100)
        .discard(
            new EventImageStorage.Uploaded(
                "unused", "flash-ticket/events/" + EVENT + "/" + UUID.randomUUID()));
    assertThat(calls).hasValue(1);
    assertThatThrownBy(
            () ->
                storage(builder, 1024, 100)
                    .discard(new EventImageStorage.Uploaded("unused", "outside/asset")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
