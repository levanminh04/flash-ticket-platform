package com.flashticket.user_service.dto;

import static com.flashticket.user_service.support.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.config.UserJsonConfig;
import com.flashticket.user_service.dto.request.*;
import com.flashticket.user_service.dto.response.*;
import com.flashticket.user_service.enums.OrganizerApplicationStatus;
import jakarta.validation.*;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class UserDtoTests {
  static ValidatorFactory validators;
  static Validator validator;
  static JsonMapper json;

  @BeforeAll
  static void setup() {
    validators = Validation.buildDefaultValidatorFactory();
    validator = validators.getValidator();
    try (var context =
        new AnnotationConfigApplicationContext(
            JacksonAutoConfiguration.class, UserJsonConfig.class)) {
      json = context.getBean(JsonMapper.class);
    }
  }

  @AfterAll
  static void close() {
    validators.close();
  }

  @Test
  void rejectsUnknownAndWrongTypedRequestFields() {
    for (String input :
        new String[] {
          "{\"organizationName\":\"n\",\"shortDescription\":\"d\",\"role\":\"ADMIN\"}",
          "{\"organizationName\":1,\"shortDescription\":\"d\"}",
          "{\"organizationName\":true,\"shortDescription\":\"d\"}",
          "{\"organizationName\":{},\"shortDescription\":\"d\"}"
        })
      assertThatThrownBy(() -> json.readValue(input, OrganizerApplicationRequest.class))
          .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(
            () ->
                json.readValue(
                    "{\"reason\":\"ok\",\"actor\":\"admin\"}",
                    RejectOrganizerApplicationRequest.class))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void validatesRequiredTextWithoutInventedLengthLimits() {
    assertThat(validator.validate(new OrganizerApplicationRequest(" ", "d"))).isNotEmpty();
    assertThat(validator.validate(new OrganizerApplicationRequest("n", null))).isNotEmpty();
    assertThat(validator.validate(json.readValue("{}", OrganizerApplicationRequest.class)))
        .hasSize(2);
    assertThat(validator.validate(new OrganizerApplicationRequest("n", "x".repeat(10000))))
        .isEmpty();
    assertThat(validator.validate(new RejectOrganizerApplicationRequest(" "))).isNotEmpty();
    assertThat(validator.validate(new OrganizerPublicView("s", "n", "d", -1L))).isNotEmpty();
  }

  @Test
  void roundTripsNullableResponsesAndUtcTime() {
    var me = new MeView(SUBJECT, null);
    assertThat(json.readValue(json.writeValueAsString(me), MeView.class)).isEqualTo(me);
    assertThat(json.readTree(json.writeValueAsString(me)).get("displayName").isNull()).isTrue();
    var view =
        new OrganizerApplicationView(
            ID, SUBJECT, "n", "d", OrganizerApplicationStatus.PENDING, null, NOW, NOW);
    var tree = json.readTree(json.writeValueAsString(view));
    assertThat(tree.get("createdAt").asString()).isEqualTo("2026-10-25T00:00:00.123Z");
    assertThat(tree.get("rejectionReason").isNull()).isTrue();
    assertThat(json.readValue(json.writeValueAsString(view), OrganizerApplicationView.class))
        .isEqualTo(view);
  }

  @Test
  void copiesApplicationPageItems() {
    var source = new ArrayList<OrganizerApplicationView>();
    var page = new OrganizerApplicationPage(source, null);
    source.add(
        new OrganizerApplicationView(
            ID, SUBJECT, "n", "d", OrganizerApplicationStatus.PENDING, null, NOW, NOW));
    assertThat(page.items()).isEmpty();
    assertThat(page.nextCursor()).isNull();
    assertThat(json.readTree(json.writeValueAsString(page)).get("nextCursor").isNull()).isTrue();
    assertThatThrownBy(() -> page.items().add(source.getFirst()))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
