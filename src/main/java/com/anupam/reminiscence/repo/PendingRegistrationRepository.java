package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.PendingRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository
        extends JpaRepository<PendingRegistrationEntity, String> {
}