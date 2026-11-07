package com.flashticket.ticket_service.issuance.repository;

import com.flashticket.ticket_service.entity.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IssuanceRepository extends JpaRepository<Issuance, UUID> {}
