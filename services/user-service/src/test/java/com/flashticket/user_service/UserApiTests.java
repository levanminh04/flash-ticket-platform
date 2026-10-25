package com.flashticket.user_service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import com.mongodb.client.model.IndexOptions;
import java.util.EnumSet;
import java.util.Set;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LocalTestInfrastructure.class)
class UserApiTests {
  @BeforeAll
  static void configureJsonPathWithJackson() {
    // JsonPath 2 uses json-smart by default. Keep the existing API assertions on
    // the patched Jackson implementation instead of retaining an unpatched parser.
    var mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    Configuration.setDefaults(
        new Configuration.Defaults() {
          @Override
          public JsonProvider jsonProvider() {
            return new JacksonJsonProvider(mapper);
          }

          @Override
          public MappingProvider mappingProvider() {
            return new JacksonMappingProvider(mapper);
          }

          @Override
          public Set<Option> options() {
            return EnumSet.noneOf(Option.class);
          }
        });
  }

  @Autowired MockMvc mvc;
  @Autowired MongoTemplate mongo;

  @BeforeEach
  void clearIsolatedTestDatabase() {
    mongo.getDb().drop();
    mongo
        .getCollection("tbl_users")
        .createIndex(new Document("identitySubject", 1), new IndexOptions().unique(true));
    mongo
        .getCollection("tbl_organizer_applications")
        .createIndex(new Document("identitySubject", 1), new IndexOptions().unique(true));
    mongo
        .getCollection("tbl_organizer_profiles")
        .createIndex(new Document("identitySubject", 1), new IndexOptions().unique(true));
    mongo
        .getCollection("tbl_user_follows")
        .createIndex(
            new Document("buyerSubject", 1).append("organizerSubject", 1),
            new IndexOptions().unique(true));
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor buyer(String subject) {
    return jwt()
        .jwt(j -> j.subject(subject).claim("name", "Test buyer"))
        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"));
  }

  @Test
  void verifiedHumanGetsMinimalProfileWithoutEmailOrRoles() throws Exception {
    mvc.perform(get("/api/v1/me").with(buyer("buyer-1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.identitySubject").value("buyer-1"))
        .andExpect(jsonPath("$.displayName").value("Test buyer"))
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.roles").doesNotExist());
    org.assertj.core.api.Assertions.assertThat(mongo.getCollection("tbl_users").find().first())
        .doesNotContainKeys("email", "roles", "_class");
  }

  @Test
  void duplicateApplicationIsConflictAndListIsOwnerFiltered() throws Exception {
    String input = "{\"organizationName\":\"Test org\",\"shortDescription\":\"Description\"}";
    mvc.perform(
            post("/api/v1/organizer-applications")
                .with(buyer("buyer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(input))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING"));
    mvc.perform(
            post("/api/v1/organizer-applications")
                .with(buyer("buyer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(input))
        .andExpect(status().isConflict());
    mvc.perform(get("/api/v1/organizer-applications").with(buyer("buyer-2")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void commandRejectsUnknownFields() throws Exception {
    mvc.perform(
            post("/api/v1/organizer-applications")
                .with(buyer("buyer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"organizationName\":\"Test org\",\"shortDescription\":\"Description\",\"identitySubject\":\"someone-else\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").exists())
        .andExpect(jsonPath("$.correlationId").exists());
  }

  @Test
  void followAndUnfollowRetriesKeepOneRelationWithoutStoredCounter() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(put("/api/v1/organizers/organizer-1/follow").with(buyer("buyer-1")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.following").value(true))
          .andExpect(jsonPath("$.followerCount").value(1));
    }
    org.assertj.core.api.Assertions.assertThat(
            mongo.getCollection("tbl_user_follows").countDocuments())
        .isEqualTo(1);
    for (int i = 0; i < 2; i++) {
      mvc.perform(delete("/api/v1/organizers/organizer-1/follow").with(buyer("buyer-1")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.following").value(false))
          .andExpect(jsonPath("$.followerCount").value(0));
    }
  }

  @Test
  void adminListRejectsInvalidPageSize() throws Exception {
    mvc.perform(
            get("/api/v1/admin/organizer-applications?size=101")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void buyerCannotApproveApplications() throws Exception {
    mvc.perform(
            post("/api/v1/admin/organizer-applications/00000000-0000-4000-8000-000000000001/approve")
                .with(buyer("buyer-1")))
        .andExpect(status().isForbidden());
  }

  @Test
  void keycloakFailureLeavesDurablePendingGrantAndBlocksRejection() throws Exception {
    mvc.perform(
            post("/api/v1/organizer-applications")
                .with(buyer("buyer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"organizationName\":\"Test org\",\"shortDescription\":\"Description\"}"))
        .andExpect(status().isCreated());
    String id = mongo.getCollection("tbl_organizer_applications").find().first().getString("_id");
    var admin =
        jwt().jwt(j -> j.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    mvc.perform(post("/api/v1/admin/organizer-applications/" + id + "/approve").with(admin))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("ROLE_GRANT_UNAVAILABLE"));
    Document pending = mongo.getCollection("tbl_organizer_applications").find().first();
    org.assertj.core.api.Assertions.assertThat(pending.getString("status")).isEqualTo("PENDING");
    org.assertj.core.api.Assertions.assertThat(
            pending.get("roleGrant", Document.class).getString("requestedBySubject"))
        .isEqualTo("admin-1");
    mvc.perform(
            post("/api/v1/admin/organizer-applications/" + id + "/reject")
                .with(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Not accepted\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void userEditedDisplayNameSurvivesJwtCreationFallback() throws Exception {
    mvc.perform(
            put("/api/v1/me")
                .with(buyer("buyer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Edited name\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Edited name"));
    mvc.perform(get("/api/v1/me").with(buyer("buyer-1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Edited name"));
  }

  @Test
  void applicationDetailsAreHiddenFromOtherBuyer() throws Exception {
    mvc.perform(
            post("/api/v1/organizer-applications")
                .with(buyer("buyer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"organizationName\":\"Organization\",\"shortDescription\":\"Description\"}"))
        .andExpect(status().isCreated());
    String id = mongo.getCollection("tbl_organizer_applications").find().first().getString("_id");
    mvc.perform(get("/api/v1/organizer-applications/" + id).with(buyer("buyer-2")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/organizer-applications/" + id).with(buyer("buyer-1")))
        .andExpect(status().isOk());
  }

  @Test
  void unsupportedMethodsAndContentTypesKeepProtocolErrorStatus() throws Exception {
    mvc.perform(post("/api/v1/me").with(buyer("buyer-1")))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(
            put("/api/v1/me")
                .with(buyer("buyer-1"))
                .contentType(MediaType.TEXT_PLAIN)
                .content("not a JSON command"))
        .andExpect(status().isUnsupportedMediaType());
  }

  @Test
  void internalTokenCannotReadHumanProfile() throws Exception {
    mvc.perform(
            get("/api/v1/me")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void humanCannotReadInternalUserEndpoint() throws Exception {
    mvc.perform(
            get("/internal/v1/users/some-subject")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedProfileRequestIsRejected() throws Exception {
    mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }
}
