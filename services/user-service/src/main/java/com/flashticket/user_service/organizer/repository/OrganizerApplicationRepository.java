package com.flashticket.user_service.organizer.repository;

import com.flashticket.user_service.document.OrganizerApplicationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizerApplicationRepository
    extends MongoRepository<OrganizerApplicationDocument, String> {}
