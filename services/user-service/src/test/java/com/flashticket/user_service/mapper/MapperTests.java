package com.flashticket.user_service.mapper;

import static com.flashticket.user_service.support.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.config.UserJsonConfig;
import org.junit.jupiter.api.*;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

class MapperTests {
  static AnnotationConfigApplicationContext context;

  @BeforeAll
  static void setup() {
    context = new AnnotationConfigApplicationContext("com.flashticket.user_service.mapper");
  }

  @AfterAll
  static void close() {
    context.close();
  }

  @Test
  void mapsPublicProfileWithoutInternalFields() {
    var profile = profile();
    var result = context.getBean(OrganizerProfileMapper.class).toPublicView(profile, 7L);
    assertThat(result.organizerSubject()).isEqualTo("idp|buyer-17");
    assertThat(result.organizationName()).isEqualTo("Tổ chức");
    assertThat(result.followerCount()).isEqualTo(7L);
    try (var jsonContext =
        new AnnotationConfigApplicationContext(
            JacksonAutoConfiguration.class, UserJsonConfig.class)) {
      var tree = jsonContext.getBean(JsonMapper.class).valueToTree(result);
      assertThat(tree.size()).isEqualTo(4);
      assertThat(tree.has("sourceApplicationId") || tree.has("_id") || tree.has("roleGrant"))
          .isFalse();
    }
  }

  @Test
  void doesNotInventFollowerCount() {
    var mapper = context.getBean(OrganizerProfileMapper.class);
    assertThat(mapper.toPublicView(profile(), 0L).followerCount()).isZero();
    assertThat(mapper.toPublicView(profile(), 42L).followerCount()).isEqualTo(42L);
  }

  @Test
  void mapsMeAndApplicationWithoutLeakingGrantData() {
    var me = context.getBean(UserMapper.class).toView(user());
    assertThat(me.identitySubject()).isEqualTo(SUBJECT);
    assertThat(me.displayName()).isEqualTo("Người mua");
    var result = context.getBean(OrganizerApplicationMapper.class).toView(activeApplication());
    assertThat(result.applicationId()).isEqualTo(ID);
    assertThat(result.identitySubject()).isEqualTo(SUBJECT);
    assertThat(result.createdAt()).isEqualTo(NOW);
    assertThat(result.rejectionReason()).isNull();
  }
}
