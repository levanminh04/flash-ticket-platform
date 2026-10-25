package com.flashticket.user_service.document;

import com.flashticket.user_service.enums.OrganizerProfileStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Getter
@Setter
@Document(collection = "tbl_organizer_profiles")
public class OrganizerProfileDocument {
  @MongoId(FieldType.STRING)
  private String id;

  private String identitySubject;
  private String sourceApplicationId;
  private Long sourceVersion;
  private String organizationName;
  private String shortDescription;
  private OrganizerProfileStatus status;
  private String logoUrl;
  private String bannerUrl;
  private Instant refreshedAt;
}
