package com.flashticket.user_service.document;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Getter
@Setter
@Document(collection = "tbl_users")
public class UserDocument {
  @MongoId(FieldType.STRING)
  private String id;

  private String identitySubject;
  private String displayName;
  private Instant createdAt;
  private Instant updatedAt;
  @Version private Long version;
}
