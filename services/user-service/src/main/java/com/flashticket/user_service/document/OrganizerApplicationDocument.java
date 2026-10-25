package com.flashticket.user_service.document;

import com.flashticket.user_service.enums.OrganizerApplicationStatus;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Getter
@Setter
@Document(collection = "tbl_organizer_applications")
public class OrganizerApplicationDocument {
  @MongoId(FieldType.STRING)
  private String id;

  private String identitySubject;
  private String organizationName;
  private String shortDescription;
  private OrganizerApplicationStatus status;
  private String rejectionReason;
  private String decidedBySubject;
  private Instant decidedAt;
  private RoleGrant roleGrant;
  private Instant createdAt;
  private Instant updatedAt;
  @Version private Long version;
}
