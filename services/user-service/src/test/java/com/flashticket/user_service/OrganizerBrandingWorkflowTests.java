package com.flashticket.user_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.flashticket.user_service.domain.UserModels.Application;
import com.flashticket.user_service.persistence.UserStore;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    properties = {
      "user.images.cloud-name=test",
      "user.images.api-key=fixture",
      "user.images.api-secret=fixture"
    })
@AutoConfigureMockMvc
@Import({LocalTestInfrastructure.class, OrganizerBrandingWorkflowTests.Transport.class})
class OrganizerBrandingWorkflowTests {
  private static final AtomicInteger UPLOADS = new AtomicInteger();

  @TestConfiguration(proxyBeanMethods = false)
  static class Transport {
    @Bean
    @Primary
    RestClient.Builder cloudinaryTransport() {
      return RestClient.builder()
          .requestInterceptor(
              (request, body, execution) -> {
                assertThat(request.getURI().toString())
                    .isEqualTo("https://api.cloudinary.com/v1_1/test/image/upload");
                UPLOADS.incrementAndGet();
                var response =
                    new MockClientHttpResponse(
                        "{\"secure_url\":\"https://res.cloudinary.com/test/image/upload/image.png\"}"
                            .getBytes(StandardCharsets.UTF_8),
                        HttpStatus.OK);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                return response;
              });
    }
  }

  @Autowired MockMvc mvc;
  @Autowired UserStore store;
  @Autowired org.springframework.data.mongodb.core.MongoTemplate mongo;
  private Application active;

  @BeforeEach
  void setup() {
    mongo.getDb().drop();
    UPLOADS.set(0);
    var application = store.apply("owner", "Organization", "Description");
    active = store.activate(store.claim(application.id(), "admin"));
    store.project(active);
  }

  private MockMultipartFile image() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    return new MockMultipartFile("file", "image.png", "image/png", output.toByteArray());
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor organizer(String sub) {
    return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
  }

  @Test
  void uploadedBrandingIsPublicAndSurvivesProjectionRecoveryWithoutEditingApplication()
      throws Exception {
    for (String kind : java.util.List.of("logo", "banner")) {
      mvc.perform(multipart("/api/v1/organizers/me/" + kind).file(image()).with(organizer("owner")))
          .andExpect(status().isOk());
    }
    store.project(active);
    assertThat(store.publicOrganizer("owner").logoUrl())
        .isEqualTo("https://res.cloudinary.com/test/image/upload/image.png");
    assertThat(store.publicOrganizer("owner").bannerUrl())
        .isEqualTo("https://res.cloudinary.com/test/image/upload/image.png");
    assertThat(store.application(active.id()).version()).isEqualTo(active.version());
    assertThat(UPLOADS).hasValue(2);
  }

  @Test
  void uploadRequiresOwnerActiveProfileAndOrganizerRoleBeforeAnyProviderCall() throws Exception {
    mvc.perform(multipart("/api/v1/organizers/me/logo").file(image()).with(organizer("other")))
        .andExpect(status().isNotFound());
    mvc.perform(
            multipart("/api/v1/organizers/me/logo")
                .file(image())
                .with(
                    jwt()
                        .jwt(j -> j.subject("owner"))
                        .authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
        .andExpect(status().isForbidden());
    mvc.perform(multipart("/api/v1/organizers/me/logo").file(image()))
        .andExpect(status().isUnauthorized());
    mvc.perform(multipart("/api/v1/organizers/owner/logo").file(image()).with(organizer("other")))
        .andExpect(status().isForbidden());
    assertThat(UPLOADS).hasValue(0);
    assertThat(store.publicOrganizer("owner").logoUrl()).isNull();
  }

  @Test
  void missingFileUsesTheStandardBadRequestResponseWithoutUploading() throws Exception {
    mvc.perform(multipart("/api/v1/organizers/me/banner").with(organizer("owner")))
        .andExpect(status().isBadRequest());
    assertThat(UPLOADS).hasValue(0);
  }
}
