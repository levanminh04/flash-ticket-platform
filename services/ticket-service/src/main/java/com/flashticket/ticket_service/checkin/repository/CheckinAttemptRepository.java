package com.flashticket.ticket_service.checkin.repository;

import com.flashticket.ticket_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinAttemptRepository extends JpaRepository<CheckinAttempt, UUID> {}
