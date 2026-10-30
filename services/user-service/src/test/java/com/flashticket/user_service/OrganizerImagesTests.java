package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.service.OrganizerImages;
import com.flashticket.user_service.web.UserException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestClient;

class OrganizerImagesTests {
  private static MockClientHttpResponse response(String body, HttpStatus status) {
    var response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8), status);
    response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
    return response;
  }

  private static byte[] png(int width, int height) throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
    return output.toByteArray();
  }

  private OrganizerImages storage(RestClient.Builder builder, int maxBytes, long maxPixels) {
    return new OrganizerImages(
        builder,
        "test",
        "fixture-key",
        "fixture-secret",
        "flash-ticket/organizers",
        maxBytes,
        maxPixels,
        Duration.ofSeconds(2));
  }

  @Test
  void uploadsVerifiedImageBytesAndReturnsOnlyTheProviderSecureUrl() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (request, body, execution) -> {
                  calls.incrementAndGet();
                  assertThat(request.getURI().toString())
                      .isEqualTo("https://api.cloudinary.com/v1_1/test/image/upload");
                  assertThat(request.getHeaders().getFirst("Authorization"))
                      .isEqualTo("Basic Zml4dHVyZS1rZXk6Zml4dHVyZS1zZWNyZXQ=");
                  assertThat(new String(body, StandardCharsets.ISO_8859_1))
                      .contains("flash-ticket/organizers", "logo_")
                      .doesNotContain("../../untrusted");
                  return response(
                      "{\"secure_url\":\"https://res.cloudinary.com/test/image/upload/logo.png\",\"extra\":true}",
                      HttpStatus.OK);
                });
    var file = new MockMultipartFile("file", "../../untrusted.png", "image/png", png(2, 2));
    assertThat(storage(builder, 1024, 16).upload(file, "logo"))
        .isEqualTo("https://res.cloudinary.com/test/image/upload/logo.png");
    assertThat(calls).hasValue(1);
  }

  @Test
  void rejectsOversizedDimensionsAndBytesBeforeSendingAnything() throws Exception {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (request, body, execution) -> {
                  throw new AssertionError("Invalid image must never reach the provider");
                });
    var file = new MockMultipartFile("file", "image.png", "image/png", png(4, 4));
    assertThatThrownBy(() -> storage(builder, 1024, 4).upload(file, "banner"))
        .isInstanceOfSatisfying(UserException.class, e -> assertThat(e.status()).isEqualTo(413));
    assertThatThrownBy(() -> storage(builder, 8, 100).upload(file, "banner"))
        .isInstanceOfSatisfying(UserException.class, e -> assertThat(e.status()).isEqualTo(413));
  }

  @Test
  void rejectsMimeSpoofingSvgEmptyAndTruncatedImages() throws Exception {
    var images = storage(RestClient.builder(), 1024, 100);
    for (var file :
        java.util.List.of(
            new MockMultipartFile("file", "image.jpg", "image/jpeg", png(2, 2)),
            new MockMultipartFile(
                "file", "image.svg", "image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8)),
            new MockMultipartFile("file", "image.png", "image/png", new byte[0]),
            new MockMultipartFile(
                "file", "image.png", "image/png", java.util.Arrays.copyOf(png(2, 2), 25)))) {
      assertThatThrownBy(() -> images.upload(file, "logo")).isInstanceOf(UserException.class);
    }
  }

  @Test
  void providerFailureNeverExposesTheResponseOrCredentials() throws Exception {
    var builder =
        RestClient.builder()
            .requestInterceptor(
                (request, body, execution) ->
                    new MockClientHttpResponse(
                        "private provider detail".getBytes(StandardCharsets.UTF_8),
                        HttpStatus.BAD_REQUEST));
    var file = new MockMultipartFile("file", "image.png", "image/png", png(2, 2));
    assertThatThrownBy(() -> storage(builder, 1024, 100).upload(file, "banner"))
        .isInstanceOfSatisfying(
            UserException.class,
            e -> {
              assertThat(e.status()).isEqualTo(503);
              assertThat(e.getMessage())
                  .doesNotContain("fixture-secret", "private provider detail");
              assertThat(e.getCause()).isNull();
            });
  }

  @Test
  void refusesProviderUrlsOutsideTheConfiguredCloudAndHttpsImageDelivery() throws Exception {
    var file = new MockMultipartFile("file", "image.png", "image/png", png(2, 2));
    for (String url :
        java.util.List.of(
            "http://res.cloudinary.com/test/image/upload/logo.png",
            "https://res.cloudinary.com/other/image/upload/logo.png",
            "https://example.com/image.png")) {
      var builder =
          RestClient.builder()
              .requestInterceptor(
                  (request, body, execution) ->
                      response("{\"secure_url\":\"" + url + "\"}", HttpStatus.OK));
      assertThatThrownBy(() -> storage(builder, 1024, 100).upload(file, "logo"))
          .isInstanceOfSatisfying(UserException.class, e -> assertThat(e.status()).isEqualTo(503));
    }
  }
}
