package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;

import com.flashticket.event_service.api.SeatMap;
import com.flashticket.event_service.domain.SeatMapRules;
import com.flashticket.event_service.web.EventProblem;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class SeatMapRulesTests {
  final JsonMapper json = JsonMapper.builder().build();

  @Test
  void retainsHiddenUnassignedSeatInValidDraft() {
    var map = json.readValue(SeatMapFixtures.map(false), SeatMap.class);
    SeatMapRules.validate(map, List.of());
    assertThat(map.sectors().getFirst().seats()).hasSize(2);
    assertThat(map.publicView().sectors().getFirst().seats()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "negativeBounds",
        "extraShape",
        "precision",
        "overflow",
        "standingSeats",
        "missingType",
        "negativeCapacity"
      })
  void rejectsInvalidGeometryAndCrossReferences(String fault) {
    String input =
        switch (fault) {
          case "negativeBounds" ->
              SeatMapFixtures.map(false).replace("\"width\":100", "\"width\":-1");
          case "extraShape" ->
              SeatMapFixtures.map(false)
                  .replace(
                      "\"shapeType\":\"rectangle\"", "\"shapeType\":\"rectangle\",\"price\":100");
          case "precision" -> SeatMapFixtures.map(false).replace("1.25", "1.251");
          case "overflow" -> SeatMapFixtures.map(false).replace("1.25", "100000000");
          case "standingSeats" -> SeatMapFixtures.map(false).replace("SEATED", "STANDING");
          case "negativeCapacity" ->
              SeatMapFixtures.map(false)
                  .replace("\"configuredCapacity\":\"2\"", "\"configuredCapacity\":\"-1\"");
          default -> SeatMapFixtures.map(true);
        };
    var map = json.readValue(input, SeatMap.class);
    assertThatThrownBy(() -> SeatMapRules.validate(map, List.of()))
        .isInstanceOf(EventProblem.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"M0 0 L10 20 Z", "M0,0 A10 10 0 0 1 20 20", "M0 0 C1 2 3 4 5 6"})
  void parsesSvgDrawingGrammar(String path) {
    assertThatCode(() -> SeatMapRules.shape(path(path))).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<script>alert(1)</script>",
        "https://bad.test",
        "M0 0 L",
        "M0 0 A1 1 0 4 1 2 2",
        "M0 0L1e999 2",
        "M0 0 X1 2",
        "M0,,0",
        "M,0 0"
      })
  void rejectsMarkupUnknownCommandsAndIncompleteSvg(String path) {
    assertThatThrownBy(() -> SeatMapRules.shape(path(path))).isInstanceOf(EventProblem.class);
  }

  tools.jackson.databind.JsonNode path(String value) {
    return json.readTree(
        "{\"shapeType\":\"path\",\"bounds\":{\"x\":0,\"y\":0,\"width\":10,\"height\":10},\"pathData\":"
            + json.writeValueAsString(value)
            + "}");
  }
}
