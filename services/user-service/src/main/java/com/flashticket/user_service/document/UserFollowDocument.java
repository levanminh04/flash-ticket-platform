package com.flashticket.user_service.document;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

@Getter
@Setter
@Document(collection = "tbl_user_follows")
public class UserFollowDocument {
  @MongoId(FieldType.STRING)
  private String id;

  private String buyerSubject;
  private String organizerSubject;
  private Instant createdAt;
}
