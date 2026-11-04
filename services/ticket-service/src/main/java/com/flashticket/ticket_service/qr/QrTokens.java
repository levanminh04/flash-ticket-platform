package com.flashticket.ticket_service.qr;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public final class QrTokens {
  private static final int TOKEN_BYTES = 32;
  private static final int NONCE_BYTES = 12;
  private static final int TAG_BITS = 128;
  private static final int TOKEN_TEXT_BYTES = 43;
  private static final int MAX_KEY_RING_BYTES = 65_536;
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();

  private final Resource keyRingResource;
  private final String activeKeyVersion;
  private final SecureRandom random = new SecureRandom();

  public QrTokens(
      @Value("${ticket.qr.key-ring-location:}") Resource keyRingResource,
      @Value("${ticket.qr.active-key-version:}") String activeKeyVersion) {
    this.keyRingResource = keyRingResource;
    this.activeKeyVersion = activeKeyVersion;
  }

  public Sealed issue(UUID ticketId) {
    try {
      require(ticketId != null);
      SecretKeySpec key = readKey(activeKeyVersion);
      byte[] entropy = new byte[TOKEN_BYTES];
      random.nextBytes(entropy);
      String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
      Arrays.fill(entropy, (byte) 0);

      byte[] nonce = new byte[NONCE_BYTES];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
      cipher.updateAAD(ticketId.toString().getBytes(StandardCharsets.UTF_8));
      byte[] encrypted = cipher.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
      byte[] payload =
          ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
      return new Sealed(payload, activeKeyVersion, hash(rawToken));
    } catch (GeneralSecurityException | IOException | RuntimeException failure) {
      // Do not propagate JSON, file, key, token, or provider details through exception causes.
      throw new QrUnavailableException();
    }
  }

  public String reveal(UUID ticketId, byte[] ciphertext, String keyVersion) {
    try {
      if (ciphertext == null) throw new QrUnavailableException();
      require(ticketId != null);
      require(ciphertext.length == NONCE_BYTES + TOKEN_TEXT_BYTES + TAG_BITS / 8);
      SecretKeySpec key = readKey(keyVersion);
      byte[] payload = ciphertext.clone();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(TAG_BITS, Arrays.copyOf(payload, NONCE_BYTES)));
      cipher.updateAAD(ticketId.toString().getBytes(StandardCharsets.UTF_8));
      String rawToken =
          new String(
              cipher.doFinal(payload, NONCE_BYTES, payload.length - NONCE_BYTES),
              StandardCharsets.UTF_8);
      require(rawToken.matches("[A-Za-z0-9_-]{43}"));
      byte[] decoded = Base64.getUrlDecoder().decode(rawToken);
      require(
          decoded.length == TOKEN_BYTES
              && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(rawToken));
      Arrays.fill(decoded, (byte) 0);
      return rawToken;
    } catch (GeneralSecurityException | IOException | RuntimeException failure) {
      throw new QrUnavailableException();
    }
  }

  public static String hash(String rawToken) {
    try {
      require(rawToken != null);
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException | RuntimeException failure) {
      throw new QrUnavailableException();
    }
  }

  private SecretKeySpec readKey(String requestedVersion) throws IOException {
    require(requestedVersion != null && !requestedVersion.isBlank());
    if (keyRingResource == null) throw new QrUnavailableException();
    require("file".equals(keyRingResource.getURL().getProtocol()));
    byte[] document;
    try (var input = keyRingResource.getInputStream()) {
      document = input.readNBytes(MAX_KEY_RING_BYTES + 1);
    }
    require(document.length > 0 && document.length <= MAX_KEY_RING_BYTES);
    JsonNode ring;
    try {
      ring = JSON.readTree(document);
    } finally {
      Arrays.fill(document, (byte) 0);
    }
    if (ring == null) throw new QrUnavailableException();
    require(ring.isObject());
    JsonNode keys = ring.get("keys");
    if (keys == null) throw new QrUnavailableException();
    require(keys.isObject() && !keys.isEmpty());
    SecretKeySpec selected = null;
    for (var entry : keys.properties()) {
      require(!entry.getKey().isBlank() && entry.getValue().isString());
      byte[] bytes = Base64.getDecoder().decode(entry.getValue().asString());
      try {
        require(bytes.length == TOKEN_BYTES);
        if (entry.getKey().equals(requestedVersion)) {
          selected = new SecretKeySpec(bytes, "AES");
        }
      } finally {
        Arrays.fill(bytes, (byte) 0);
      }
    }
    if (selected == null) throw new QrUnavailableException();
    return selected;
  }

  private static void require(boolean condition) {
    if (!condition) {
      throw new QrUnavailableException();
    }
  }

  public record Sealed(byte[] ciphertext, String keyVersion, String hash) {
    public Sealed {
      require(ciphertext != null);
      ciphertext = ciphertext.clone();
    }

    @Override
    public byte[] ciphertext() {
      return ciphertext.clone();
    }

    @Override
    public String toString() {
      return "Sealed[redacted]";
    }
  }
}
