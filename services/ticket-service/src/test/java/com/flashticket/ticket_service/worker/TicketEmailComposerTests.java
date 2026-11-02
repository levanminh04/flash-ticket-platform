package com.flashticket.ticket_service.worker;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.qr.QrImageAssertions;
import com.flashticket.ticket_service.qr.QrTokens;
import com.flashticket.ticket_service.qr.QrUnavailableException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.json.JsonMapper;

class TicketEmailComposerTests {
  @TempDir Path temp;

  @Test
  void emailContainsAnInlinePngForEachExistingTokenAndEscapesDisplayData() throws Exception {
    var ring = temp.resolve("ring.json");
    Files.writeString(
        ring, "{\"keys\":{\"v1\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}}");
    var tokens = new QrTokens(new FileSystemResource(ring), "v1");
    UUID first = UUID.randomUUID(), second = UUID.randomUUID(), order = UUID.randomUUID();
    var a = tokens.issue(first);
    var b = tokens.issue(second);
    var composer =
        new TicketEmailComposer(tokens, JsonMapper.builder().build(), "Asia/Ho_Chi_Minh");
    var message = new MimeMessage(Session.getInstance(new Properties()));
    String snapshot =
        "{\"eventTitle\":\"<script>unsafe</script> & concert\",\"eventStartsAt\":\"2026-11-02T12:00:00Z\",\"venueName\":\"Hall\",\"ticketTypeName\":\"VIP\",\"seatLabel\":\"A1\"}";
    composer.compose(
        message,
        "tickets@example.test",
        "buyer@example.test",
        order,
        "https://buyer.example.test/orders/" + order + "/tickets",
        List.of(
            new TicketEmailComposer.StoredTicket(first, snapshot, "VALID", a.ciphertext(), "v1"),
            new TicketEmailComposer.StoredTicket(second, snapshot, "VALID", b.ciphertext(), "v1")));
    var output = new ByteArrayOutputStream();
    message.writeTo(output);
    var received =
        new MimeMessage(
            Session.getInstance(new Properties()), new ByteArrayInputStream(output.toByteArray()));
    var parts = leaves(received);
    var html = parts.stream().filter(p -> type(p, "text/html")).findFirst().orElseThrow();
    String body = (String) html.getContent();
    assertThat(body)
        .contains("&lt;script&gt;unsafe&lt;/script&gt;", "VIP", "A1", "19:00", "cid:qr-" + first)
        .doesNotContain(
            "<script>", "cloudinary", "qrserver", tokens.reveal(first, a.ciphertext(), "v1"));
    var images = parts.stream().filter(p -> type(p, "image/png")).toList();
    assertThat(images).hasSize(2);
    var decoded = new HashSet<String>();
    for (Part image : images) {
      assertThat(image.getDisposition()).isEqualToIgnoringCase(Part.INLINE);
      String cid = image.getHeader("Content-ID")[0];
      assertThat(body).contains("cid:" + cid.substring(1, cid.length() - 1));
      try (var input = image.getInputStream()) {
        decoded.add(QrImageAssertions.decode(input.readAllBytes()));
      }
    }
    assertThat(decoded)
        .containsExactlyInAnyOrder(
            tokens.reveal(first, a.ciphertext(), "v1"),
            tokens.reveal(second, b.ciphertext(), "v1"));
    assertThat(received.getAllRecipients()[0].toString()).isEqualTo("buyer@example.test");
    assertThat(parts.stream().filter(p -> type(p, "text/plain"))).hasSize(1);
  }

  @Test
  void unavailableKeyDoesNotProduceAnEmailWithoutQr() throws Exception {
    var tokens = new QrTokens(new FileSystemResource(temp.resolve("missing.json")), "v1");
    var composer = new TicketEmailComposer(tokens, JsonMapper.builder().build(), "UTC");
    assertThatThrownBy(
            () ->
                composer.compose(
                    new MimeMessage(Session.getInstance(new Properties())),
                    "tickets@example.test",
                    "buyer@example.test",
                    UUID.randomUUID(),
                    "https://buyer.example.test",
                    List.of(
                        new TicketEmailComposer.StoredTicket(
                            UUID.randomUUID(), "{}", "VALID", new byte[71], "v1"))))
        .isInstanceOf(QrUnavailableException.class);
  }

  @Test
  void smtpTransportDeliversTheInlineImageWithoutExternalImageFetches() throws Exception {
    var ring = temp.resolve("smtp-ring.json");
    Files.writeString(
        ring, "{\"keys\":{\"v1\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}}");
    var tokens = new QrTokens(new FileSystemResource(ring), "v1");
    UUID id = UUID.randomUUID();
    var sealed = tokens.issue(id);
    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        var server = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
      server.setSoTimeout(10000);
      var wire =
          executor.submit(
              () -> {
                try (var socket = server.accept()) {
                  socket.setSoTimeout(10000);
                  var reader =
                      new java.io.BufferedReader(
                          new java.io.InputStreamReader(
                              socket.getInputStream(),
                              java.nio.charset.StandardCharsets.ISO_8859_1));
                  var writer =
                      new java.io.PrintWriter(
                          new java.io.OutputStreamWriter(
                              socket.getOutputStream(),
                              java.nio.charset.StandardCharsets.ISO_8859_1),
                          true);
                  writer.print("220 localhost test SMTP\r\n");
                  writer.flush();
                  var data = new StringBuilder();
                  String command;
                  while ((command = reader.readLine()) != null) {
                    if (command.equals("DATA")) {
                      writer.print("354 End with dot\r\n");
                      writer.flush();
                      String line;
                      while ((line = reader.readLine()) != null && !line.equals("."))
                        data.append(line.startsWith("..") ? line.substring(1) : line)
                            .append("\r\n");
                      writer.print("250 accepted\r\n");
                      writer.flush();
                    } else if (command.equals("QUIT")) {
                      writer.print("221 bye\r\n");
                      writer.flush();
                      break;
                    } else {
                      writer.print("250 OK\r\n");
                      writer.flush();
                    }
                  }
                  return data.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                }
              });
      var sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
      sender.setHost(server.getInetAddress().getHostAddress());
      sender.setPort(server.getLocalPort());
      sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "5000");
      sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "5000");
      sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "5000");
      var composer = new TicketEmailComposer(tokens, JsonMapper.builder().build(), "UTC");
      var message = sender.createMimeMessage();
      composer.compose(
          message,
          "tickets@example.test",
          "buyer@example.test",
          UUID.randomUUID(),
          "https://buyer.example.test",
          List.of(
              new TicketEmailComposer.StoredTicket(
                  id,
                  "{\"eventTitle\":\"Concert\",\"eventStartsAt\":\"2026-11-02T12:00:00Z\"}",
                  "VALID",
                  sealed.ciphertext(),
                  "v1")));
      sender.send(message);
      byte[] receivedBytes = wire.get(10, java.util.concurrent.TimeUnit.SECONDS);
      Files.write(Path.of("target/qr-email-preview.eml"), receivedBytes);
      var received =
          new MimeMessage(
              Session.getInstance(new Properties()), new ByteArrayInputStream(receivedBytes));
      var image =
          leaves(received).stream().filter(p -> type(p, "image/png")).findFirst().orElseThrow();
      try (var input = image.getInputStream()) {
        assertThat(QrImageAssertions.decode(input.readAllBytes()))
            .isEqualTo(tokens.reveal(id, sealed.ciphertext(), "v1"));
      }
    }
  }

  static boolean type(Part part, String type) {
    try {
      return part.isMimeType(type);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  static List<Part> leaves(Part part) throws Exception {
    if (!part.isMimeType("multipart/*")) return List.of(part);
    var content = (Multipart) part.getContent();
    var result = new ArrayList<Part>();
    for (int i = 0; i < content.getCount(); i++) result.addAll(leaves(content.getBodyPart(i)));
    return result;
  }
}
