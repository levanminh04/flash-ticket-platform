package com.flashticket.user_service.follow.repository;

import com.flashticket.user_service.document.UserFollowDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserFollowRepository extends MongoRepository<UserFollowDocument, String> {}
