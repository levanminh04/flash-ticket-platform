package com.flashticket.user_service.messaging.identity;

import com.flashticket.user_service.document.UserDocument;
import com.flashticket.user_service.user.repository.UserRepository;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.MongoWriteConcernException;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.resilience.annotation.Retryable;

/**
 * Tạo hồ sơ nếu chưa có, dùng identitySubject làm khóa chống xử lý trùng. Chỉ ghi một document nên
 * dùng tính nguyên tử của MongoDB, không cần transaction nhiều document.
 */
public class IdentityUserRegistrar {
  private final UserRepository userRepository;

  public IdentityUserRegistrar(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  // Một lần đầu và tối đa hai lần thử lại sau 1s/5s cho lỗi DB tạm thời.
  @Retryable(
      includes = {
        TransientDataAccessException.class,
        DataAccessResourceFailureException.class,
        MongoSocketException.class,
        MongoTimeoutException.class,
        MongoWriteConcernException.class
      },
      maxRetries = 2,
      delay = 1000,
      multiplier = 5,
      maxDelay = 5000)
  public void ensureRegistered(UserCreatedV1 event) {
    String identitySubject = event.identitySubject();
    if (userRepository.findByIdentitySubject(identitySubject).isPresent()) {
      return;
    }

    // ID hồ sơ là ID nội bộ; identitySubject là ID user trên Keycloak.
    UserDocument newUser = new UserDocument();
    newUser.setId(UUID.randomUUID().toString());
    newUser.setIdentitySubject(identitySubject);
    newUser.setCreatedAt(event.occurredAt());
    newUser.setUpdatedAt(event.occurredAt());
    newUser.setDisplayName(event.displayName());

    try {
      // version đang null để Spring Data nhận diện user mới và thực hiện insert.
      userRepository.save(newUser);
    } catch (DuplicateKeyException error) {
      // Có thể consumer khác vừa tạo user sau lần find đầu tiên.
      // Chỉ coi là thành công nếu tìm lại được user cùng identitySubject.
      if (userRepository.findByIdentitySubject(identitySubject).isEmpty()) {
        throw error;
      }
    }
  }
}
