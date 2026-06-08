package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.AppInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppInfoRepo extends JpaRepository<AppInfo, Long> {
    Optional<AppInfo> findByInfoName(String appVersion);
}
