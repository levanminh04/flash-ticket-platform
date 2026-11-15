package com.flashticket.event_service.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.flashticket.event_service.web.EventProblem;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SeatMapSvgTests {
  @Test
  void preservesArtworkAndRemovesEditorMetadata() {
    String result =
        new String(
            SeatMapSvg.clean(
                svg("<metadata>private draft</metadata><path d='M0 0L10 10' fill='#fff'/>")),
            StandardCharsets.UTF_8);
    assertTrue(result.contains("<path"));
    assertFalse(result.contains("private draft"));
  }

  @Test
  void rejectsActiveContentAndRemoteReferences() {
    for (String content :
        new String[] {
          "<script>alert(1)</script>",
          "<rect onload='alert(1)'/>",
          "<rect fill='url(https://example.test/a.svg)'/>",
          "<foreignObject/>"
        }) {
      assertThrows(EventProblem.class, () -> SeatMapSvg.clean(svg(content)));
    }
    byte[] doctype =
        "<!DOCTYPE svg SYSTEM 'https://example.test/dtd'><svg xmlns='http://www.w3.org/2000/svg'/>"
            .getBytes(StandardCharsets.UTF_8);
    assertThrows(EventProblem.class, () -> SeatMapSvg.clean(doctype));
  }

  private static byte[] svg(String body) {
    return ("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'>" + body + "</svg>")
        .getBytes(StandardCharsets.UTF_8);
  }
}
