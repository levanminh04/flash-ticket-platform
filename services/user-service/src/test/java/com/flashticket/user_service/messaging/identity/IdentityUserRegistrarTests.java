package com.flashticket.user_service.messaging.identity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.flashticket.user_service.document.UserDocument;
import com.flashticket.user_service.user.repository.UserRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class IdentityUserRegistrarTests {
  @Test
  void emitsAtomicInsertOnlyWriteCompatibleWithClosedValidator() {
    var repository = mock(UserRepository.class);
    when(repository.findByIdentitySubject("subject")).thenReturn(Optional.empty());
    var event =
        new UserCreatedV1(
            1,
            UUID.randomUUID(),
            UserCreatedV1.TYPE,
            Instant.parse("2026-10-26T12:00:00Z"),
            "realm",
            "subject",
            null);
    var registrar = new IdentityUserRegistrar(repository);
    registrar.ensureRegistered(event);
    var user = ArgumentCaptor.forClass(UserDocument.class);
    verify(repository).save(user.capture());
    assertThat(user.getValue().getIdentitySubject()).isEqualTo("subject");
    assertThat(user.getValue().getCreatedAt()).isEqualTo(event.occurredAt());
    assertThat(user.getValue().getUpdatedAt()).isEqualTo(event.occurredAt());
    assertThat(user.getValue().getDisplayName()).isNull();
    assertThat(user.getValue().getVersion()).isNull();
    assertThat(UUID.fromString(user.getValue().getId())).isNotNull();

    // Khi hai lượt xử lý cùng subject, kiểm tra user đã được tạo trước khi bỏ qua lỗi.
    UserDocument existing = new UserDocument();
    existing.setId(UUID.randomUUID().toString());
    existing.setCreatedAt(event.occurredAt());
    existing.setUpdatedAt(event.occurredAt());
    existing.setVersion(0L);
    when(repository.findByIdentitySubject("subject"))
        .thenReturn(Optional.empty(), Optional.of(existing));
    when(repository.save(any(UserDocument.class)))
        .thenThrow(new DuplicateKeyException("duplicate"));
    registrar.ensureRegistered(event);

    when(repository.findByIdentitySubject("subject")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> registrar.ensureRegistered(event))
        .isInstanceOf(DuplicateKeyException.class);

    reset(repository);
    when(repository.findByIdentitySubject("subject")).thenReturn(Optional.of(existing));
    registrar.ensureRegistered(event);
    verify(repository, never()).save(any(UserDocument.class));
  }
}
