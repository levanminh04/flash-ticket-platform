package com.flashticket.user_service.organizer.repository;

import com.flashticket.user_service.document.OrganizerProfileDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OrganizerProfileRepository
    extends MongoRepository<OrganizerProfileDocument, String> {}
