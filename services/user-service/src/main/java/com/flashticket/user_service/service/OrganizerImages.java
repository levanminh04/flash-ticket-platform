package com.flashticket.user_service.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.user_service.web.UserException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
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

/** Server-side Cloudinary Upload API; no client-supplied URL is fetched or persisted. */
@Service
public final class OrganizerImages {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Uploaded(@JsonProperty("secure_url") String secureUrl) {}

  private final RestClient rest;
  private final String cloud;
  private final String apiKey;
  private final String apiSecret;
  private final String folder;
  private final int maxBytes;
  private final long maxPixels;

  public OrganizerImages(
      RestClient.Builder builder,
      @Value("${user.images.cloud-name:}") String cloud,
      @Value("${user.images.api-key:}") String apiKey,
      @Value("${user.images.api-secret:}") String apiSecret,
      @Value("${user.images.folder:flash-ticket/organizers}") String folder,
      @Value("${user.images.max-bytes:2097152}") int maxBytes,
      @Value("${user.images.max-pixels:16000000}") long maxPixels,
      @Value("${user.images.timeout:5s}") Duration timeout) {
    if ((!cloud.isEmpty() && !cloud.matches("[A-Za-z0-9_-]+"))
        || !folder.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")
        || maxBytes < 1
        || maxPixels < 1
        || timeout.isZero()
        || timeout.isNegative())
      throw new IllegalArgumentException("Invalid organizer image configuration");
    this.cloud = cloud;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.folder = folder;
    this.maxBytes = maxBytes;
    this.maxPixels = maxPixels;
    var factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    this.rest = builder.requestFactory(factory).build();
  }

  public String upload(MultipartFile file, String kind) {
    if (!kind.equals("logo") && !kind.equals("banner")) throw invalid();
    byte[] bytes = validate(file);
    if (cloud.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) throw unavailable();
    var form = new LinkedMultiValueMap<String, Object>();
    form.add(
        "file",
        new ByteArrayResource(bytes) {
          @Override
          public String getFilename() {
            return "image";
          }
        });
    form.add("folder", folder);
    form.add("public_id", kind + "_" + UUID.randomUUID());
    form.add("overwrite", "false");
    try {
      Uploaded response =
          rest.post()
              .uri("https://api.cloudinary.com/v1_1/" + cloud + "/image/upload")
              .headers(h -> h.setBasicAuth(apiKey, apiSecret))
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(form)
              .retrieve()
              .body(Uploaded.class);
      if (response == null || response.secureUrl() == null) throw unavailable();
      URI uri = URI.create(response.secureUrl());
      if (!"https".equals(uri.getScheme())
          || !"res.cloudinary.com".equals(uri.getHost())
          || uri.getUserInfo() != null
          || uri.getPort() != -1
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !uri.getPath().startsWith("/" + cloud + "/image/upload/")) throw unavailable();
      return uri.toASCIIString();
    } catch (RuntimeException ex) {
      // Never propagate credential-bearing request metadata or provider response bodies.
      throw unavailable();
    }
  }

  private byte[] validate(MultipartFile file) {
    if (file == null || file.isEmpty()) throw invalid();
    if (file.getSize() > maxBytes) throw tooLarge();
    String type = file.getContentType();
    if (!MediaType.IMAGE_PNG_VALUE.equals(type) && !MediaType.IMAGE_JPEG_VALUE.equals(type))
      throw new UserException(415, "UNSUPPORTED_IMAGE", "Use a PNG or JPEG image");
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes(Math.addExact(maxBytes, 1));
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
                  && (format.equals("jpeg") || format.equals("jpg")))) throw invalid();
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

  private static UserException invalid() {
    return new UserException(400, "INVALID_IMAGE", "A valid image file is required");
  }

  private static UserException tooLarge() {
    return new UserException(
        413, "IMAGE_TOO_LARGE", "Image exceeds the configured size or pixel limit");
  }

  private static UserException unavailable() {
    return new UserException(
        503, "IMAGE_STORAGE_UNAVAILABLE", "Image storage unavailable; retry later");
  }
}
