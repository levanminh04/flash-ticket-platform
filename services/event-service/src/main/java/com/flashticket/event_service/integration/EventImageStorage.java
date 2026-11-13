package com.flashticket.event_service.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.event_service.web.EventProblem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

/** Upload bytes only; never fetch a URL supplied by an organizer. */
@Service
public final class EventImageStorage {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProviderImage(
      @JsonProperty("secure_url") String secureUrl, @JsonProperty("public_id") String publicId) {}

  public record Uploaded(String objectReference, String publicId) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Destroyed(String result) {}

  private static final String IMAGE_WEBP = "image/webp";

  private final RestClient rest;
  private final String cloud;
  private final String apiKey;
  private final String apiSecret;
  private final String folder;
  private final int maxBytes;
  private final long maxPixels;

  public EventImageStorage(
      RestClient.Builder builder,
      @Value("${event.images.cloud-name:}") String cloud,
      @Value("${event.images.api-key:}") String apiKey,
      @Value("${event.images.api-secret:}") String apiSecret,
      @Value("${event.images.folder:flash-ticket/events}") String folder,
      @Value("${event.images.max-bytes:5242880}") int maxBytes,
      @Value("${event.images.max-pixels:16000000}") long maxPixels,
      @Value("${event.images.timeout:10s}") Duration timeout) {
    if ((!cloud.isEmpty() && !cloud.matches("[A-Za-z0-9_-]+"))
        || !folder.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")
        || folder.length() > 150
        || maxBytes < 1
        || maxBytes > 20971520
        || maxPixels < 1
        || maxPixels > 64000000
        || timeout.isZero()
        || timeout.isNegative())
      throw new IllegalArgumentException("Invalid Event image configuration");
    this.cloud = cloud;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.folder = folder;
    this.maxBytes = maxBytes;
    this.maxPixels = maxPixels;
    var factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    rest = builder.requestFactory(factory).build();
  }

  public Uploaded upload(UUID eventId, MultipartFile file) {
    byte[] bytes = validate(file);
    if (cloud.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) throw unavailable();
    String publicId = folder + "/" + eventId + "/" + UUID.randomUUID();
    var form = new LinkedMultiValueMap<String, Object>();
    form.add(
        "file",
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return "image";
          }
        });
    form.add("public_id", publicId);
    form.add("overwrite", "false");
    try {
      ProviderImage image =
          rest.post()
              .uri(endpoint("upload"))
              .headers(h -> h.setBasicAuth(apiKey, apiSecret))
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(form)
              .retrieve()
              .body(ProviderImage.class);
      if (image == null || !publicId.equals(image.publicId()) || image.secureUrl() == null)
        throw unavailable();
      URI uri = URI.create(image.secureUrl());
      if (!"https".equals(uri.getScheme())
          || !"res.cloudinary.com".equals(uri.getHost())
          || uri.getUserInfo() != null
          || uri.getPort() != -1
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !uri.getRawPath()
              .matches(
                  "/"
                      + Pattern.quote(cloud)
                      + "/image/upload/(v[0-9]+/)?"
                      + Pattern.quote(publicId)
                      + "\\.(png|jpg|jpeg|webp)")) throw unavailable();
      return new Uploaded(uri.toASCIIString(), publicId);
    } catch (RuntimeException ex) {
      // Do not expose request Authorization, provider response or a credential-bearing cause.
      org.slf4j.LoggerFactory.getLogger(EventImageStorage.class)
          .atWarn()
          .addKeyValue("eventName", "event.image-upload-unconfirmed")
          .addKeyValue("eventId", eventId.toString())
          .addKeyValue("errorCode", "IMAGE_UPLOAD_UNCONFIRMED")
          .log("Verify storage before retrying unconfirmed upload asset={}", publicId);
      throw unavailable();
    }
  }

  /** Compensation for a newly uploaded asset that was verified absent from the DB. */
  public void discard(Uploaded image) {
    if (!image.publicId().matches(Pattern.quote(folder) + "/[a-f0-9-]{36}/[a-f0-9-]{36}"))
      throw new IllegalArgumentException("Unmanaged Event image");
    var form = new LinkedMultiValueMap<String, String>();
    form.add("public_id", image.publicId());
    form.add("invalidate", "true");
    try {
      var response =
          rest.post()
              .uri(endpoint("destroy"))
              .headers(h -> h.setBasicAuth(apiKey, apiSecret))
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Destroyed.class);
      if (response == null
          || response.result() == null
          || !java.util.Set.of("ok", "not found").contains(response.result())) throw unavailable();
    } catch (RuntimeException ex) {
      throw unavailable();
    }
  }

  private String endpoint(String action) {
    return "https://api.cloudinary.com/v1_1/" + cloud + "/image/" + action;
  }

  private byte[] validate(MultipartFile file) {
    if (file == null || file.isEmpty()) throw invalid();
    if (file.getSize() > maxBytes) throw tooLarge();
    String type = file.getContentType();
    if (!MediaType.IMAGE_PNG_VALUE.equals(type)
        && !MediaType.IMAGE_JPEG_VALUE.equals(type)
        && !IMAGE_WEBP.equals(type)) throw new EventProblem(415, "UNSUPPORTED_IMAGE", "file");
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes(maxBytes + 1);
      if (bytes.length > maxBytes) throw tooLarge();
      try (var imageInput = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
        var readers = ImageIO.getImageReaders(imageInput);
        if (!readers.hasNext()) throw invalid();
        var reader = readers.next();
        try {
          reader.setInput(imageInput, true, true);
          String format = reader.getFormatName().toLowerCase(Locale.ROOT);
          if (!(type.equals(MediaType.IMAGE_PNG_VALUE) && format.equals("png"))
              && !(type.equals(MediaType.IMAGE_JPEG_VALUE)
                  && (format.equals("jpeg") || format.equals("jpg")))
              && !(IMAGE_WEBP.equals(type) && format.equals("webp"))) throw invalid();
          if (IMAGE_WEBP.equals(type)) validateStaticWebp(bytes);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || (long) width * height > maxPixels) throw tooLarge();
          if (reader.read(0) == null) throw invalid();
        } finally {
          reader.dispose();
        }
      }
      return bytes;
    } catch (IOException ex) {
      throw invalid();
    }
  }

  private static void validateStaticWebp(byte[] bytes) {
    // ImageIO identifies the RIFF/WEBP signature before this check.
    if (bytes.length < 21) throw invalid();
    long riffSize =
        Integer.toUnsignedLong(
            ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    if (riffSize + 8 != bytes.length) throw invalid();
    // Extended WebP stores its animation flag in the first byte of the VP8X chunk payload.
    boolean extended = bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8' && bytes[15] == 'X';
    if (extended && (bytes[20] & 0x02) != 0) throw invalid();
  }

  private static EventProblem invalid() {
    return new EventProblem(422, "INVALID_IMAGE", "file");
  }

  private static EventProblem tooLarge() {
    return new EventProblem(413, "IMAGE_TOO_LARGE", "file");
  }

  private static EventProblem unavailable() {
    return new EventProblem(503, "IMAGE_STORAGE_UNAVAILABLE", null);
  }
}
