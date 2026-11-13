package com.flashticket.event_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.flashticket.event_service.application.EventImageService;
import com.flashticket.event_service.application.EventImageTransactions;
import com.flashticket.event_service.domain.EventData.ImageRole;
import com.flashticket.event_service.integration.EventImageStorage;
import com.flashticket.event_service.web.EventProblem;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class EventImageServiceTests {
  @Test
  void anUncertainCommitIsNeverCompensatedEvenWhenASeparateReadWouldReportFalse() {
    var transactions = mock(EventImageTransactions.class);
    var storage = mock(EventImageStorage.class);
    var service = new EventImageService(transactions, storage);
    var event = UUID.randomUUID();
    var file = new MockMultipartFile("file", new byte[] {1});
    var uploaded = new EventImageStorage.Uploaded("url", "asset");
    when(transactions.prepare(event, "owner", "0", null, 0, ImageRole.GALLERY))
        .thenReturn(ImageRole.GALLERY);
    when(storage.upload(event, file)).thenReturn(uploaded);
    when(transactions.attach(event, "owner", "0", null, 0, ImageRole.GALLERY, "url"))
        .thenThrow(
            new org.springframework.transaction.TransactionSystemException(
                "COMMIT outcome unknown"));
    when(transactions.referenced("url")).thenReturn(false);
    assertThatThrownBy(() -> service.upload(event, "owner", "0", null, 0, file))
        .isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
    verify(storage, never()).discard(any());
  }

  @Test
  void retainsUploadWhenCommitOutcomeIsUnknownAndDatabaseCannotConfirmAbsence() {
    var transactions = mock(EventImageTransactions.class);
    var storage = mock(EventImageStorage.class);
    var service = new EventImageService(transactions, storage);
    var event = UUID.randomUUID();
    var file = new MockMultipartFile("file", new byte[] {1});
    var uploaded = new EventImageStorage.Uploaded("url", "asset");
    when(transactions.prepare(event, "owner", "0", null, 0, ImageRole.GALLERY))
        .thenReturn(ImageRole.GALLERY);
    when(storage.upload(event, file)).thenReturn(uploaded);
    var failure = new IllegalStateException("database unreachable");
    when(transactions.attach(event, "owner", "0", null, 0, ImageRole.GALLERY, "url"))
        .thenThrow(failure);
    when(transactions.referenced("url")).thenThrow(new IllegalStateException("still unreachable"));
    assertThatThrownBy(() -> service.upload(event, "owner", "0", null, 0, file)).isSameAs(failure);
    verify(storage, never()).discard(any());
  }

  @Test
  void neverDeletesAnAssetThatWasCommittedDespiteAReportedFailure() {
    var transactions = mock(EventImageTransactions.class);
    var storage = mock(EventImageStorage.class);
    var service = new EventImageService(transactions, storage);
    var event = UUID.randomUUID();
    var file = new MockMultipartFile("file", new byte[] {1});
    var uploaded = new EventImageStorage.Uploaded("url", "asset");
    when(transactions.prepare(event, "owner", "0", null, 0, ImageRole.GALLERY))
        .thenReturn(ImageRole.GALLERY);
    when(storage.upload(event, file)).thenReturn(uploaded);
    when(transactions.attach(event, "owner", "0", null, 0, ImageRole.GALLERY, "url"))
        .thenThrow(EventProblem.conflict("STALE_EVENT_VERSION"));
    when(transactions.referenced("url")).thenReturn(true);
    assertThatThrownBy(() -> service.upload(event, "owner", "0", null, 0, file))
        .isInstanceOf(EventProblem.class);
    verify(storage, never()).discard(any());
  }
}
