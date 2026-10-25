package com.flashticket.user_service.persistence;

import static com.flashticket.user_service.support.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;

import com.flashticket.user_service.config.MongoConfig;
import com.flashticket.user_service.document.*;
import java.util.Date;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class MongoMappingTests {
  static AnnotationConfigApplicationContext context;
  static MappingMongoConverter converter;

  @BeforeAll
  static void setup() {
    context = new AnnotationConfigApplicationContext(MongoConfig.class, Mapping.class);
    converter = context.getBean(MappingMongoConverter.class);
  }

  @AfterAll
  static void close() {
    context.close();
  }

  @Test
  void writesOnlySchemaFields() {
    assertThat(bson(user()).keySet())
        .containsExactlyInAnyOrder(
            "_id", "identitySubject", "displayName", "createdAt", "updatedAt", "version");
    assertThat(bson(pendingApplication()).keySet())
        .containsExactlyInAnyOrder(
            "_id",
            "identitySubject",
            "organizationName",
            "shortDescription",
            "status",
            "createdAt",
            "updatedAt",
            "version");
    assertThat(bson(profile()).keySet())
        .containsExactlyInAnyOrder(
            "_id",
            "identitySubject",
            "sourceApplicationId",
            "sourceVersion",
            "organizationName",
            "shortDescription",
            "status",
            "refreshedAt");
    assertThat(bson(follow()).keySet())
        .containsExactlyInAnyOrder("_id", "buyerSubject", "organizerSubject", "createdAt");
  }

  @Test
  void omitsOptionalNullsAndNestedClassMetadata() {
    assertThat(bson(pendingApplication()))
        .doesNotContainKeys("roleGrant", "decidedAt", "rejectionReason", "_class");
    var role = bson(activeApplication()).get("roleGrant", Document.class);
    assertThat(role.keySet())
        .containsExactlyInAnyOrder("operationId", "status", "attemptedAt", "confirmedAt");
    assertThat(role.getString("status")).isEqualTo("CONFIRMED");
    assertThat(bson(rejectedApplication()))
        .containsKey("rejectionReason")
        .doesNotContainKey("roleGrant");
    var withoutName = user();
    withoutName.setDisplayName(null);
    assertThat(bson(withoutName)).doesNotContainKey("displayName");
  }

  @Test
  void preservesSubjectDatesAndZeroSourceVersion() {
    var source = bson(user());
    assertThat(source.get("_id")).isInstanceOf(String.class).isEqualTo(ID);
    assertThat(source.get("identitySubject")).isEqualTo("idp|buyer-17");
    assertThat(source.get("createdAt")).isEqualTo(Date.from(NOW));
    assertThat(converter.read(UserDocument.class, source).getCreatedAt()).isEqualTo(NOW);
    assertThat(converter.read(OrganizerProfileDocument.class, bson(profile())).getSourceVersion())
        .isZero();
  }

  @Test
  void hasFourRepositoriesWithCorrectCollections() {
    var mapping = converter.getMappingContext();
    assertThat(mapping.getRequiredPersistentEntity(UserDocument.class).getCollection())
        .isEqualTo("tbl_users");
    assertThat(
            mapping.getRequiredPersistentEntity(OrganizerApplicationDocument.class).getCollection())
        .isEqualTo("tbl_organizer_applications");
    assertThat(mapping.getRequiredPersistentEntity(OrganizerProfileDocument.class).getCollection())
        .isEqualTo("tbl_organizer_profiles");
    assertThat(mapping.getRequiredPersistentEntity(UserFollowDocument.class).getCollection())
        .isEqualTo("tbl_user_follows");
    assertThat(mapping.getRequiredPersistentEntity(UserDocument.class).hasVersionProperty())
        .isTrue();
    assertThat(
            mapping
                .getRequiredPersistentEntity(OrganizerApplicationDocument.class)
                .hasVersionProperty())
        .isTrue();
    assertThat(
            mapping
                .getRequiredPersistentEntity(OrganizerProfileDocument.class)
                .hasVersionProperty())
        .isFalse();
    assertThat(mapping.getRequiredPersistentEntity(UserFollowDocument.class).hasVersionProperty())
        .isFalse();
  }

  static Document bson(Object value) {
    var result = new Document();
    converter.write(value, result);
    return result;
  }

  @Configuration(proxyBeanMethods = false)
  static class Mapping {
    @Bean
    MongoCustomConversions conversions() {
      return MongoCustomConversions.create(adapter -> {});
    }

    @Bean
    MongoMappingContext mappingContext(MongoCustomConversions conversions) {
      var value = new MongoMappingContext();
      value.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
      value.setInitialEntitySet(
          Set.of(
              UserDocument.class,
              OrganizerApplicationDocument.class,
              OrganizerProfileDocument.class,
              UserFollowDocument.class));
      return value;
    }

    @Bean
    MappingMongoConverter converter(
        MongoMappingContext context, MongoCustomConversions conversions) {
      var value = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
      value.setCustomConversions(conversions);
      return value;
    }
  }
}
