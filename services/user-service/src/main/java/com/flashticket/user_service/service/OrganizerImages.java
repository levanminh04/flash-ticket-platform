package com.flashticket.user_service.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.flashticket.user_service.web.UserException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

  private static final String IMAGE_WEBP = "image/webp";

  private final RestClient rest;
  private final String cloud;
  private final String apiKey;
  private final String apiSecret;
  private final String folder;
  private final String avatarFolder;
  private final int maxBytes;
  private final int brandingMaxBytes;
  private final long maxPixels;

  public OrganizerImages(
      RestClient.Builder builder,
      @Value("${user.images.cloud-name:}") String cloud,
      @Value("${user.images.api-key:}") String apiKey,
      @Value("${user.images.api-secret:}") String apiSecret,
      @Value("${user.images.folder:flash-ticket/organizers}") String folder,
      @Value("${user.images.avatar-folder:flash-ticket/avatars}") String avatarFolder,
      @Value("${user.images.max-bytes:2097152}") int maxBytes,
      @Value("${user.images.branding-max-bytes:5242880}") int brandingMaxBytes,
      @Value("${user.images.max-pixels:16000000}") long maxPixels,
      @Value("${user.images.timeout:5s}") Duration timeout) {
    if ((!cloud.isEmpty() && !cloud.matches("[A-Za-z0-9_-]+"))
        || !folder.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")
        || !avatarFolder.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*")
        || maxBytes < 1
        || brandingMaxBytes < 1
        || maxPixels < 1
        || timeout.isZero()
        || timeout.isNegative())
      throw new IllegalArgumentException("Invalid organizer image configuration");
    this.cloud = cloud;
    this.apiKey = apiKey;
    this.apiSecret = apiSecret;
    this.folder = folder;
    this.avatarFolder = avatarFolder;
    this.maxBytes = maxBytes;
    this.brandingMaxBytes = brandingMaxBytes;
    this.maxPixels = maxPixels;
    var factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    this.rest = builder.requestFactory(factory).build();
  }

  public String upload(MultipartFile file, String kind) {
    if (!kind.equals("logo") && !kind.equals("banner") && !kind.equals("avatar")) throw invalid();
    byte[] bytes = validate(file, kind);
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
    form.add("folder", kind.equals("avatar") ? avatarFolder : folder);
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

  private byte[] validate(MultipartFile file, String kind) {
    if (file == null || file.isEmpty()) throw invalid();
    boolean branding = !kind.equals("avatar");
    int limit = branding ? brandingMaxBytes : maxBytes;
    if (file.getSize() > limit) throw tooLarge();
    String type = file.getContentType();
    boolean webp = branding && IMAGE_WEBP.equals(type);
    if (!MediaType.IMAGE_PNG_VALUE.equals(type)
        && !MediaType.IMAGE_JPEG_VALUE.equals(type)
        && !webp)
      throw new UserException(
          415,
          "UNSUPPORTED_IMAGE",
          branding ? "Use PNG, JPEG or static WebP" : "Use a PNG or JPEG image");
    try (var input = file.getInputStream()) {
      byte[] bytes = input.readNBytes(Math.addExact(limit, 1));
      if (bytes.length > limit) throw tooLarge();
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
              && !(webp && format.equals("webp"))) throw invalid();
          if (webp) validateStaticWebp(bytes);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          if (width < 1 || height < 1 || (long) width * height > maxPixels) throw tooLarge();
          if ((kind.equals("logo") && width != height)
              || (kind.equals("banner") && width * 9L != height * 16L))
            throw new UserException(
                422,
                "INVALID_BRANDING_DIMENSIONS",
                kind.equals("logo") ? "Use a 1:1 logo" : "Use a 16:9 banner");
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
