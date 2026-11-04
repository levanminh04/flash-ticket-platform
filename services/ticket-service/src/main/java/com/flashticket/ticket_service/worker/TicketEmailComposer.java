package com.flashticket.ticket_service.worker;

import com.flashticket.ticket_service.qr.QrImages;
import com.flashticket.ticket_service.qr.QrTokens;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
  private final ZoneId zone;

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
    this.zone = ZoneId.of(zone);
    this.dates =
        DateTimeFormatter.ofPattern("HH:mm · EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
            .withZone(this.zone);
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
    var images = new ArrayList<byte[]>();
    var displays = new ArrayList<Display>();
    for (StoredTicket ticket : tickets) {
      if (!List.of("VALID", "USED").contains(ticket.state()))
        throw new IllegalStateException("DELIVERY_DATA_UNAVAILABLE");
      images.add(
          QrImages.png(tokens.reveal(ticket.id(), ticket.ciphertext(), ticket.keyVersion())));
      var data = json.readTree(ticket.display());
      displays.add(
          new Display(
              text(data, "eventTitle"),
              schedule(text(data, "eventStartsAt")),
              schedule(text(data, "eventEndsAt")),
              text(data, "venueName"),
              text(data, "ticketTypeName"),
              text(data, "seatLabel")));
    }
    // An issuance belongs to one event; shared event information is shown once.
    Display event = displays.getFirst();
    var html = new StringBuilder(header());
    var plain =
        new StringBuilder(
            "Vé của bạn đã được phát hành\n\nCảm ơn bạn đã lựa chọn Flash Ticket.\n\n");
    eventSummary(html, plain, event);
    for (int i = 0; i < tickets.size(); i++) {
      ticketCard(html, plain, tickets.get(i), displays.get(i), i + 1, tickets.size());
    }
    if (!link.isEmpty()) {
      html.append(
          """
          <tr><td class="content-pad" style="padding:0 28px 24px;">
          <table role="presentation" cellpadding="0" cellspacing="0"><tr><td bgcolor="#ff8a43" style="border-radius:4px;">
          """);
      html.append("<a href=\"")
          .append(escape(link))
          .append(
              "\" style=\"display:inline-block;padding:14px 22px;border:1px solid #ff8a43;border-radius:4px;color:#21180e;font-size:16px;line-height:24px;font-weight:700;text-decoration:none;\">Xem vé của tôi</a></td></tr></table></td></tr>");
      plain.append("Đăng nhập để xem và tải vé: ").append(link).append("\n\n");
    }
    footer(html, plain, order);
    var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");
    helper.setFrom("Flash Ticket <" + new InternetAddress(from).getAddress() + ">");
    helper.setTo(to);
    helper.setSubject("Vé điện tử của bạn | Flash Ticket");
    helper.setText(plain.toString(), html.toString());
    for (int i = 0; i < tickets.size(); i++)
      helper.addInline(
          "qr-" + tickets.get(i).id() + "@flashticket",
          new ByteArrayResource(images.get(i)),
          "image/png");
  }

  private record Display(
      String title, String starts, String ends, String venue, String type, String seat) {}

  private static String text(tools.jackson.databind.JsonNode data, String field) {
    var node = data.path(field);
    return node.isNull() || node.isMissingNode() ? "" : node.asText().strip();
  }

  private String schedule(String value) {
    if (value.isBlank()) return "";
    Instant instant = Instant.parse(value);
    String offset = zone.getRules().getOffset(instant).getId();
    if ("Z".equals(offset)) offset = "+00:00";
    return dates.format(instant) + " (UTC" + offset + ")";
  }

  private static String header() {
    return """
        <!doctype html><html lang="vi"><head><meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Vé điện tử của bạn | Flash Ticket</title>
        <style>@media only screen and (max-width:480px){.content-pad{padding-left:16px!important;padding-right:16px!important}.email-heading{font-size:24px!important;line-height:32px!important}}</style>
        </head><body style="margin:0;padding:0;background-color:#f3f1ed;font-family:Arial,Helvetica,sans-serif;color:#21180e;">
        <div style="display:none;font-size:1px;line-height:1px;max-height:0;max-width:0;opacity:0;overflow:hidden;mso-hide:all;">Thông tin sự kiện và mã QR riêng của từng vé trong đơn hàng.</div>
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" bgcolor="#f3f1ed" style="table-layout:fixed;"><tr><td align="center" style="padding:24px 12px;">
        <!--[if mso]><table role="presentation" width="600" cellpadding="0" cellspacing="0"><tr><td><![endif]-->
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" bgcolor="#ffffff" style="table-layout:fixed;max-width:600px;border:1px solid #e3ded6;border-top:5px solid #ff8a43;">
        <tr><td class="content-pad" style="padding:28px 28px 22px;">
        <h1 class="email-heading" style="margin:0 0 10px;font-size:26px;line-height:34px;letter-spacing:-0.5px;overflow-wrap:anywhere;">Vé của bạn đã được phát hành</h1>
        <p style="margin:0;font-size:16px;line-height:25px;color:#6c6257;">Cảm ơn bạn đã lựa chọn Flash Ticket.<br>Thông tin sự kiện và mã QR của bạn ở bên dưới.</p></td></tr>
        """;
  }

  private static void eventSummary(StringBuilder html, StringBuilder plain, Display event) {
    html.append(
        """
        <tr><td class="content-pad" style="padding:0 28px 24px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" bgcolor="#fff7ef" style="table-layout:fixed;border-left:3px solid #ff8a43;"><tr><td style="padding:20px;overflow-wrap:anywhere;word-wrap:break-word;">
        <p style="margin:0 0 8px;color:#9d440b;font-size:12px;line-height:18px;font-weight:700;letter-spacing:1px;">SỰ KIỆN</p>
        """);
    html.append("<h2 style=\"margin:0 0 18px;font-size:23px;line-height:31px;\">")
        .append(escape(event.title()))
        .append("</h2>");
    plain.append(event.title()).append("\n");
    if (!event.starts().isBlank() || !event.ends().isBlank()) {
      html.append(
          "<p style=\"margin:0 0 4px;color:#6c6257;font-size:13px;line-height:20px;\">THỜI GIAN</p>");
      if (!event.starts().isBlank()) {
        html.append("<p style=\"margin:0 0 4px;font-size:16px;line-height:25px;font-weight:700;\">")
            .append(escape(event.starts()))
            .append("</p>");
        plain.append("Bắt đầu: ").append(event.starts()).append("\n");
      }
      if (!event.ends().isBlank()) {
        html.append(
                "<p style=\"margin:0 0 4px;font-size:13px;line-height:21px;color:#6c6257;\">Kết thúc: ")
            .append(escape(event.ends()))
            .append("</p>");
        plain.append("Kết thúc: ").append(event.ends()).append("\n");
      }
    }
    if (!event.venue().isBlank()) {
      html.append(
              "<p style=\"margin:18px 0 4px;color:#6c6257;font-size:13px;line-height:20px;\">ĐỊA ĐIỂM</p><p style=\"margin:0;font-size:16px;line-height:25px;\">")
          .append(escape(event.venue()))
          .append("</p>");
      plain.append("Địa điểm: ").append(event.venue()).append("\n");
    }
    html.append("</td></tr></table></td></tr>");
    plain.append("\n");
  }

  private static void ticketCard(
      StringBuilder html,
      StringBuilder plain,
      StoredTicket ticket,
      Display display,
      int number,
      int total) {
    boolean used = "USED".equals(ticket.state());
    String status = used ? "Đã sử dụng" : "Chưa sử dụng";
    String instruction =
        used
            ? "Vé này đã được sử dụng; không thể check-in lại."
            : "Xuất trình mã QR này để nhân viên quét tại cổng.";
    html.append(
        """
        <tr><td class="content-pad" style="padding:0 28px 24px;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="table-layout:fixed;border:1px solid #ded7ce;">
        <tr><td bgcolor="#faf8f4" style="padding:16px 20px;border-bottom:1px dashed #d5cbbd;overflow-wrap:anywhere;word-wrap:break-word;">
        <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="table-layout:fixed;"><tr>
        """);
    html.append("<td style=\"font-size:13px;line-height:20px;color:#6c6257;\">VÉ ")
        .append(number)
        .append(" / ")
        .append(total)
        .append("</td><td align=\"right\" style=\"font-size:13px;line-height:20px;color:")
        .append(used ? "#9d440b" : "#35664b")
        .append(";font-weight:700;\">")
        .append(status)
        .append(
            "</td></tr></table><h3 style=\"margin:10px 0 4px;font-size:20px;line-height:28px;\">")
        .append(escape(display.type()))
        .append("</h3>");
    plain
        .append("VÉ ")
        .append(number)
        .append(" / ")
        .append(total)
        .append(" — ")
        .append(status)
        .append("\nLoại vé: ")
        .append(display.type())
        .append("\n");
    if (!display.seat().isBlank()) {
      html.append("<p style=\"margin:0;font-size:15px;line-height:23px;\">Ghế ")
          .append(escape(display.seat()))
          .append("</p>");
      plain.append("Ghế: ").append(display.seat()).append("\n");
    }
    html.append(
            "</td></tr><tr><td align=\"center\" style=\"padding:22px 16px 16px;\"><img src=\"cid:qr-")
        .append(ticket.id())
        .append("@flashticket\" alt=\"Mã QR của vé ")
        .append(number)
        .append(
            "\" width=\"240\" height=\"240\" style=\"display:block;width:100%;max-width:240px;height:auto;border:0;background-color:#ffffff;\"><p style=\"margin:12px 0 0;font-size:14px;line-height:22px;color:#6c6257;\">")
        .append(instruction)
        .append("</p></td></tr>");
    html.append(
        """
        <tr><td style="padding:0 20px 18px;"><p style="margin:0;color:#6c6257;font-size:12px;line-height:19px;">Mã vé</p>
        <p style="margin:3px 0 0;color:#6c6257;font-size:12px;line-height:19px;word-break:break-all;">
        """);
    html.append(ticket.id()).append("</p></td></tr></table></td></tr>");
    plain.append("Mã vé: ").append(ticket.id()).append("\n").append(instruction).append("\n\n");
  }

  private static void footer(StringBuilder html, StringBuilder plain, UUID order) {
    String notice =
        "Giữ kín email và ảnh QR. Mỗi vé chỉ check-in thành công một lần. Quyền vào cổng được kiểm tra theo trạng thái vé tại thời điểm quét.";
    html.append(
        """
        <tr><td class="content-pad" style="padding:0 28px 24px;">
        <p style="margin:0 0 6px;font-size:14px;line-height:22px;font-weight:700;">Lưu ý khi sử dụng vé</p>
        <p style="margin:0;font-size:14px;line-height:23px;color:#6c6257;">
        """);
    html.append(notice).append("</p></td></tr>");
    html.append(
        """
        <tr><td class="content-pad" bgcolor="#faf8f4" style="padding:18px 28px;border-top:1px solid #eee9e2;">
        <p style="margin:0 0 4px;font-size:12px;line-height:19px;color:#6c6257;">Mã đơn hàng</p>
        <p style="margin:0;font-size:12px;line-height:19px;color:#6c6257;word-break:break-all;">
        """);
    html.append(order).append("</p></td></tr>");
    html.append(
        """
        </table><!--[if mso]></td></tr></table><![endif]--></td></tr></table></body></html>
        """);
    plain
        .append("Lưu ý khi sử dụng vé\n")
        .append(notice)
        .append("\n\nMã đơn hàng: ")
        .append(order)
        .append("\n");
  }

  private static String escape(String text) {
    return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
  }
}
