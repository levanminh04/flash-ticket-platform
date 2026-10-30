package com.flashticket.ticket_service.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.UrlResource;

class QrTokensTests {
  @TempDir Path temporaryDirectory;

  @Test
  void issuesOpaqueTokensWithIndependentAesGcmDecryptionAndStableLookupHash() throws Exception {
    byte[] key = randomKey();
    Path ring = writeRing("v1", key);
    QrTokens tokens = tokens(ring, "v1");
    UUID ticketId = UUID.randomUUID();
    var sealed = tokens.issue(ticketId);
    String raw = tokens.reveal(ticketId, sealed.ciphertext(), sealed.keyVersion());

    assertThat(raw).matches("[A-Za-z0-9_-]{43}");
    assertThat(Base64.getUrlDecoder().decode(raw)).hasSize(32);
    assertThat(sealed.keyVersion()).isEqualTo("v1");
    assertThat(sealed.hash()).isEqualTo(QrTokens.hash(raw)).matches("[0-9a-f]{64}");
    assertThat(sealed.ciphertext()).hasSize(12 + 43 + 16);

    byte[] ciphertext = sealed.ciphertext();
    Cipher independentDecryptor = Cipher.getInstance("AES/GCM/NoPadding");
    independentDecryptor.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key, "AES"),
        new GCMParameterSpec(128, Arrays.copyOf(ciphertext, 12)));
    independentDecryptor.updateAAD(ticketId.toString().getBytes(StandardCharsets.UTF_8));
    assertThat(
            new String(
                independentDecryptor.doFinal(ciphertext, 12, ciphertext.length - 12),
                StandardCharsets.UTF_8))
        .isEqualTo(raw);
    assertThat(sealed.toString()).doesNotContain(raw, Base64.getEncoder().encodeToString(key));
  }

  @Test
  void tokenAndNonceAreFreshForEveryIssuance() throws Exception {
    QrTokens tokens = tokens(writeRing("v1", randomKey()), "v1");
    UUID ticketId = UUID.randomUUID();
    var hashes = new HashSet<String>();
    var nonces = new HashSet<String>();
    for (int i = 0; i < 128; i++) {
      var sealed = tokens.issue(ticketId);
      hashes.add(sealed.hash());
      nonces.add(Base64.getEncoder().encodeToString(Arrays.copyOf(sealed.ciphertext(), 12)));
    }
    assertThat(hashes).hasSize(128);
    assertThat(nonces).hasSize(128);
  }

  @Test
  void sha256UsesUtf8AndLowercaseHex() {
    assertThat(QrTokens.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void persistedCiphertextSurvivesRestartAndKeyRotation() throws Exception {
    byte[] oldKey = randomKey();
    Path ring = writeRing("v1", oldKey);
    QrTokens first = tokens(ring, "v1");
    UUID ticketId = UUID.randomUUID();
    var sealed = first.issue(ticketId);
    String original = first.reveal(ticketId, sealed.ciphertext(), "v1");
    assertThat(tokens(ring, "v1").reveal(ticketId, sealed.ciphertext(), "v1")).isEqualTo(original);

    Files.writeString(
        ring,
        "{\"keys\":{\"v1\":\"" + encoded(oldKey) + "\",\"v2\":\"" + encoded(randomKey()) + "\"}}");
    QrTokens rotated = tokens(ring, "v2");
    assertThat(rotated.issue(UUID.randomUUID()).keyVersion()).isEqualTo("v2");
    assertThat(rotated.reveal(ticketId, sealed.ciphertext(), "v1")).isEqualTo(original);
  }

  @Test
  void missingKeyFailsClosedAndCanRecoverWithoutReplacingTheOriginalToken() throws Exception {
    byte[] key = randomKey();
    Path ring = writeRing("v1", key);
    QrTokens tokens = tokens(ring, "v1");
    UUID ticketId = UUID.randomUUID();
    var sealed = tokens.issue(ticketId);
    String original = tokens.reveal(ticketId, sealed.ciphertext(), "v1");
    Files.delete(ring);
    assertUnavailable(() -> tokens.issue(ticketId));
    assertUnavailable(() -> tokens.reveal(ticketId, sealed.ciphertext(), "v1"));
    writeRing("v1", key);
    assertThat(tokens.reveal(ticketId, sealed.ciphertext(), "v1")).isEqualTo(original);
  }

  @Test
  void tamperingWrongTicketWrongKeyAndUnknownVersionFailClosed() throws Exception {
    Path ring = writeRing("v1", randomKey());
    QrTokens tokens = tokens(ring, "v1");
    UUID ticketId = UUID.randomUUID();
    var sealed = tokens.issue(ticketId);
    byte[] tampered = sealed.ciphertext();
    tampered[tampered.length - 1] ^= 1;
    assertUnavailable(() -> tokens.reveal(ticketId, tampered, "v1"));
    assertUnavailable(() -> tokens.reveal(UUID.randomUUID(), sealed.ciphertext(), "v1"));
    assertUnavailable(() -> tokens.reveal(ticketId, sealed.ciphertext(), "unknown"));
    writeRing("v1", randomKey());
    assertUnavailable(() -> tokens.reveal(ticketId, sealed.ciphertext(), "v1"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not-json-secret",
        "null",
        "{}",
        "{\"keys\":{}}",
        "{\"keys\":{\"v1\":\"secret-invalid-base64\"}}",
        "{\"keys\":{\"v1\":\"YWJj\"}}",
        "{\"keys\":{\"v1\":42}}",
        "{\"keys\":{\"v1\":null}}"
      })
  void invalidKeyRingsReturnOnlySafeGenericFailure(String json) throws Exception {
    Path ring = temporaryDirectory.resolve("keys.json");
    Files.writeString(ring, json);
    assertUnavailable(() -> tokens(ring, "v1").issue(UUID.randomUUID()));
  }

  @Test
  void duplicateKeyVersionsAreRejected() throws Exception {
    Path ring = temporaryDirectory.resolve("keys.json");
    Files.writeString(
        ring,
        "{\"keys\":{\"v1\":\""
            + encoded(randomKey())
            + "\",\"v1\":\""
            + encoded(randomKey())
            + "\"}}");
    assertUnavailable(() -> tokens(ring, "v1").issue(UUID.randomUUID()));
  }

  @Test
  void keyRingsMustBeLocalFilesAndContainTheActiveVersion() throws Exception {
    assertUnavailable(
        () ->
            new QrTokens(new UrlResource("https://127.0.0.1:1/keys"), "v1")
                .issue(UUID.randomUUID()));
    Path ring = writeRing("v1", randomKey());
    assertUnavailable(() -> tokens(ring, "v2").issue(UUID.randomUUID()));
    assertUnavailable(() -> tokens(ring, " ").issue(UUID.randomUUID()));
  }

  @Test
  void malformedCiphertextAndNullInputsFailWithTheSameSafeCode() throws Exception {
    QrTokens tokens = tokens(writeRing("v1", randomKey()), "v1");
    UUID ticketId = UUID.randomUUID();
    assertUnavailable(() -> tokens.reveal(ticketId, new byte[0], "v1"));
    assertUnavailable(() -> tokens.reveal(ticketId, new byte[27], "v1"));
    assertUnavailable(() -> tokens.reveal(ticketId, null, "v1"));
    assertUnavailable(() -> tokens.reveal(null, new byte[71], "v1"));
    assertUnavailable(() -> tokens.issue(null));
  }

  @Test
  void sealedCiphertextIsDefensivelyCopiedOnConstructionAndAccess() {
    byte[] original = {1, 2, 3};
    var sealed = new QrTokens.Sealed(original, "v1", "hash");
    original[0] = 9;
    sealed.ciphertext()[1] = 9;
    assertThat(sealed.ciphertext()).containsExactly((byte) 1, (byte) 2, (byte) 3);
  }

  @Test
  void beanRequiresBusinessFeatureAndResolvesExternalResourceConfiguration() throws Exception {
    var runner = new ApplicationContextRunner().withUserConfiguration(QrTokens.class);
    runner.run(context -> assertThat(context).doesNotHaveBean(QrTokens.class));
    Path ring = writeRing("v1", randomKey());
    runner
        .withPropertyValues(
            "ticket.features.business-enabled=true",
            "ticket.qr.key-ring-location=" + ring.toUri(),
            "ticket.qr.active-key-version=v1")
        .run(
            context -> {
              assertThat(context).hasSingleBean(QrTokens.class);
              assertThat(context.getBean(QrTokens.class).issue(UUID.randomUUID()).keyVersion())
                  .isEqualTo("v1");
            });
  }

  private QrTokens tokens(Path ring, String activeVersion) {
    return new QrTokens(new FileSystemResource(ring), activeVersion);
  }

  private Path writeRing(String version, byte[] key) throws Exception {
    Path ring = temporaryDirectory.resolve("keys.json");
    Files.writeString(ring, "{\"keys\":{\"" + version + "\":\"" + encoded(key) + "\"}}");
    return ring;
  }

  private static String encoded(byte[] key) {
    return Base64.getEncoder().encodeToString(key);
  }

  private static byte[] randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return key;
  }

  private static void assertUnavailable(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
    assertThatThrownBy(operation)
        .isInstanceOfSatisfying(
            QrUnavailableException.class,
            failure -> assertThat(failure.code()).isEqualTo("QR_UNAVAILABLE"))
        .hasMessage("QR_UNAVAILABLE")
        .hasNoCause();
  }
}
