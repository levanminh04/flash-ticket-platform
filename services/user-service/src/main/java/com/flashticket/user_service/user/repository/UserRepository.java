package com.flashticket.user_service.user.repository;

import com.flashticket.user_service.document.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<UserDocument, String> {}
