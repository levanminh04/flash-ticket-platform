package com.flashticket.ticket_service.worker;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.ticket_service.qr.QrImageAssertions;
import com.flashticket.ticket_service.qr.QrTokens;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import tools.jackson.databind.json.JsonMapper;

class TicketEmailPresentationTests {
  @TempDir Path temp;

  private static final String DISPLAY =
      """
      {"eventTitle":"Đêm nhạc Mùa thu 2026", "eventStartsAt":"2026-11-14T12:30:00Z",
       "eventEndsAt":"2026-11-14T15:00:00Z", "venueName":"Nhà hát Thành phố Hồ Chí Minh",
       "ticketTypeName":"Hạng VIP", "seatLabel":"A12"}
      """;

  @Test
  void vietnameseAndReadableEventScheduleSurviveTheMimeWire() throws Exception {
    var result = compose("Asia/Ho_Chi_Minh", "", DISPLAY, "VALID");
    assertThat(result.mail().getSubject()).isEqualTo("Vé điện tử của bạn | Flash Ticket");
    var from = (InternetAddress) result.mail().getFrom()[0];
    assertThat(from.getAddress()).isEqualTo("tickets@example.test");
    assertThat(from.getPersonal()).isEqualTo("Flash Ticket");
    for (String body : List.of(result.html(), result.plain())) {
      assertThat(body)
          .contains(
              "Đêm nhạc Mùa thu 2026",
              "Nhà hát Thành phố Hồ Chí Minh",
              "Hạng VIP",
              "A12",
              "19:30",
              "22:00",
              "14/11/2026",
              "UTC+07:00",
              "Chưa sử dụng")
          .doesNotContain("ICT", "VALID", "null", "�");
    }
    Files.write(Path.of("target/qr-email-official-preview.eml"), result.wire());
  }

  @Test
  void missingOptionalFieldsDoNotLeaveEmptyLabelsOrInventVenueAndSeats() throws Exception {
    var result =
        compose(
            "UTC",
            "",
            """
            {"eventTitle":"Vé tự do", "eventStartsAt":"2026-11-14T12:30:00Z",
             "ticketTypeName":"Vé phổ thông", "venueName":null,"seatLabel":null}
            """,
            "VALID");
    for (String body : List.of(result.html(), result.plain())) {
      assertThat(body)
          .contains("Vé phổ thông", "UTC+00:00")
          .doesNotContain("ĐỊA ĐIỂM", "Địa điểm:", "Ghế", "Kết thúc", "null", "VALID");
    }
    assertThat(result.html()).doesNotContain("href=", "Xem vé của tôi");
    Files.write(Path.of("target/qr-email-official-minimal-preview.eml"), result.wire());
  }

  @Test
  void timezoneComesFromConfigurationIncludingDateChangeAndDaylightOffset() throws Exception {
    var result =
        compose(
            "America/New_York",
            "",
            """
            {"eventTitle":"Sự kiện quốc tế", "eventStartsAt":"2026-07-10T00:30:00Z",
             "ticketTypeName":"Vé tham dự"}
            """,
            "VALID");
    assertThat(result.html()).contains("20:30", "09/07/2026", "UTC-04:00");
    assertThat(result.plain()).contains("20:30", "09/07/2026", "UTC-04:00");
  }

  @Test
  void oneEventSummaryAndSeparateTicketCardsPreserveEachQrAndUsedState() throws Exception {
    var result = compose("Asia/Ho_Chi_Minh", "", DISPLAY, "VALID", "USED");
    assertThat(result.html().split(Pattern.quote("Đêm nhạc Mùa thu 2026"), -1)).hasSize(2);
    assertThat(result.html()).contains("VÉ 1 / 2", "VÉ 2 / 2", "Đã sử dụng");
    assertThat(result.plain()).contains("Đã sử dụng", "không thể check-in lại");
    var images =
        TicketEmailComposerTests.leaves(result.mail()).stream()
            .filter(part -> TicketEmailComposerTests.type(part, "image/png"))
            .toList();
    assertThat(images).hasSize(2);
    for (Part image : images) {
      String cid = image.getHeader("Content-ID")[0];
      assertThat(image.getDisposition()).isEqualToIgnoringCase(Part.INLINE);
      UUID id = UUID.fromString(cid.substring("<qr-".length(), cid.indexOf('@')));
      int index = result.ids().indexOf(id);
      assertThat(index).isNotNegative();
      assertThat(result.html()).contains("cid:qr-" + id + "@flashticket");
      try (var input = image.getInputStream()) {
        assertThat(QrImageAssertions.decode(input.readAllBytes()))
            .isEqualTo(result.tokens().get(index));
      }
    }
    Files.write(Path.of("target/qr-email-official-multiple-preview.eml"), result.wire());
  }

  @Test
  void longUntrustedDisplayTextStaysEscapedAndOptionalLinkDoesNotIntroduceMarkup()
      throws Exception {
    String title = "Sự kiện mùa thu & âm nhạc ".repeat(12) + "<script>alert(1)</script>";
    var display = JsonMapper.builder().build().createObjectNode();
    display.put("eventTitle", title);
    display.put("eventStartsAt", "2026-11-14T12:30:00Z");
    display.put("ticketTypeName", "VIP <b>đặc biệt</b>");
    String link = "https://buyer.example.test/orders/abc/tickets?x=1&y=2";
    var result = compose("UTC", link, display.toString(), "VALID");
    assertThat(result.html())
        .contains(
            "&lt;script&gt;alert(1)&lt;/script&gt;",
            "VIP &lt;b&gt;đặc biệt&lt;/b&gt;",
            "Xem vé của tôi")
        .contains("href=\"https://buyer.example.test/orders/abc/tickets?x=1&amp;y=2\"")
        .doesNotContain("<script>", "<b>đặc biệt</b>", "javascript:");
    assertThat(result.plain()).contains(title, link);
    Files.write(Path.of("target/qr-email-official-long-preview.eml"), result.wire());
  }

  private record Composed(
      MimeMessage mail,
      String html,
      String plain,
      byte[] wire,
      List<UUID> ids,
      List<String> tokens) {}

  private Composed compose(String zone, String link, String display, String... states)
      throws Exception {
    var ring = temp.resolve("ring.json");
    Files.writeString(
        ring, "{\"keys\":{\"v1\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}}");
    var qr = new QrTokens(new FileSystemResource(ring), "v1");
    var tickets = new java.util.ArrayList<TicketEmailComposer.StoredTicket>();
    var ids = new java.util.ArrayList<UUID>();
    var raw = new java.util.ArrayList<String>();
    for (String state : states) {
      UUID id = UUID.randomUUID();
      var sealed = qr.issue(id);
      tickets.add(
          new TicketEmailComposer.StoredTicket(id, display, state, sealed.ciphertext(), "v1"));
      ids.add(id);
      raw.add(qr.reveal(id, sealed.ciphertext(), "v1"));
    }
    var message = new MimeMessage(Session.getInstance(new Properties()));
    new TicketEmailComposer(qr, JsonMapper.builder().build(), zone)
        .compose(
            message,
            "tickets@example.test",
            "buyer@example.test",
            UUID.randomUUID(),
            link,
            tickets);
    var wire = new ByteArrayOutputStream();
    message.writeTo(wire);
    var received =
        new MimeMessage(
            Session.getInstance(new Properties()), new ByteArrayInputStream(wire.toByteArray()));
    var parts = TicketEmailComposerTests.leaves(received);
    String html =
        (String)
            parts.stream()
                .filter(p -> TicketEmailComposerTests.type(p, "text/html"))
                .findFirst()
                .orElseThrow()
                .getContent();
    String plain =
        (String)
            parts.stream()
                .filter(p -> TicketEmailComposerTests.type(p, "text/plain"))
                .findFirst()
                .orElseThrow()
                .getContent();
    assertThat(html).doesNotContain(raw);
    assertThat(plain).doesNotContain(raw);
    return new Composed(
        received, html, plain, wire.toByteArray(), List.copyOf(ids), List.copyOf(raw));
  }
}
