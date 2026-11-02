package com.flashticket.ticket_service.worker;

import com.flashticket.ticket_service.qr.QrImages;
import com.flashticket.ticket_service.qr.QrTokens;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.json.JsonMapper;

/** Self-contained MIME: each QR is a CID PNG, with no external image request. */
@Component
@ConditionalOnProperty(prefix = "ticket.features", name = "business-enabled", havingValue = "true")
public final class TicketEmailComposer {
  private final QrTokens tokens;
  private final JsonMapper json;
  private final DateTimeFormatter dates;

  public record StoredTicket(
      UUID id, String display, String state, byte[] ciphertext, String keyVersion) {
    public StoredTicket {
      ciphertext = ciphertext.clone();
    }

    @Override
    public byte[] ciphertext() {
      return ciphertext.clone();
    }

    @Override
    public String toString() {
      return "StoredTicket[id=" + id + "]";
    }
  }

  @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "Shares Spring-managed JSON infrastructure; no dependency accessor is exposed.")
  public TicketEmailComposer(
      QrTokens tokens,
      JsonMapper json,
      @Value("${ticket.delivery.display-zone:Asia/Ho_Chi_Minh}") String zone) {
    this.tokens = tokens;
    this.json = json;
    this.dates = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z").withZone(ZoneId.of(zone));
  }

  public void compose(
      MimeMessage message,
      String from,
      String to,
      UUID order,
      String link,
      List<StoredTicket> tickets)
      throws MessagingException {
    if (tickets.isEmpty()) throw new IllegalStateException("DELIVERY_DATA_UNAVAILABLE");
    var html =
        new StringBuilder(
                "<!doctype html><html lang=\"vi\"><body style=\"font-family:Arial,sans-serif;color:#172033\"><h1>Vé của bạn đã được phát hành</h1><p>Đơn: ")
            .append(order)
            .append("</p>");
    var plain = new StringBuilder("Vé của đơn ").append(order).append(" đã được phát hành.\n");
    var images = new ArrayList<byte[]>();
    for (StoredTicket ticket : tickets) {
      if (!List.of("VALID", "USED").contains(ticket.state()))
        throw new IllegalStateException("DELIVERY_DATA_UNAVAILABLE");
      images.add(
          QrImages.png(tokens.reveal(ticket.id(), ticket.ciphertext(), ticket.keyVersion())));
      var display = json.readTree(ticket.display());
      String title = display.path("eventTitle").asText();
      String starts = display.path("eventStartsAt").asText();
      String date = starts.isBlank() ? "" : dates.format(Instant.parse(starts));
      String venue = display.path("venueName").isNull() ? "" : display.path("venueName").asText();
      String type = display.path("ticketTypeName").asText();
      String seat = display.path("seatLabel").isNull() ? "" : display.path("seatLabel").asText();
      html.append("<section style=\"border:1px solid #dbe1ea;padding:20px;margin:16px 0\"><h2>")
          .append(escape(title))
          .append("</h2><p>")
          .append(escape(date))
          .append("</p><p>")
          .append(escape(venue))
          .append("</p><p>Loại vé: ")
          .append(escape(type))
          .append("</p><p>Ghế/khu vực: ")
          .append(escape(seat))
          .append("</p><p>Mã vé: ")
          .append(ticket.id())
          .append("</p><p>Trạng thái: ")
          .append(escape(ticket.state()))
          .append("</p><img width=\"300\" height=\"300\" alt=\"QR vé\" src=\"cid:qr-")
          .append(ticket.id())
          .append("@flashticket\"></section>");
      plain
          .append(title)
          .append(" — ")
          .append(date)
          .append(" — ")
          .append(venue)
          .append("\n")
          .append(type)
          .append(" ")
          .append(seat)
          .append("\nMã vé: ")
          .append(ticket.id())
          .append("\n");
    }
    html.append(
            "<p>Giữ kín email và ảnh QR. Mỗi vé chỉ check-in thành công một lần; ảnh QR không thay thế việc kiểm tra trạng thái vé tại cổng.</p><p><a href=\"")
        .append(escape(link))
        .append("\">Xem vé của tôi</a></p></body></html>");
    plain.append("Đăng nhập để xem và tải vé: ").append(link);
    var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");
    helper.setFrom(from);
    helper.setTo(to);
    helper.setSubject("Thông tin vé của bạn");
    helper.setText(plain.toString(), html.toString());
    for (int i = 0; i < tickets.size(); i++)
      helper.addInline(
          "qr-" + tickets.get(i).id() + "@flashticket",
          new ByteArrayResource(images.get(i)),
          "image/png");
  }

  private static String escape(String text) {
    return HtmlUtils.htmlEscape(text);
  }
}
