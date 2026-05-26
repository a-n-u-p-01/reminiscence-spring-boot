package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyEntryItemRepo extends JpaRepository<DailyEntryItemEntity, UUID> {

    @Query("""
            SELECT d FROM DailyEntryItemEntity d
            WHERE d.userId = :userId
            AND d.createdAt >= :startOfDay
            AND d.createdAt < :endOfDay
            """)
    Optional<DailyEntryItemEntity> findTodayEntryByUserId(
            @Param("userId") UUID userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    List<DailyEntryItemEntity> findByProcessingStatusNot(String name);

    @Query("SELECT d FROM DailyEntryItemEntity d WHERE d.userId = :userId AND d.createdAt BETWEEN :start AND :end ORDER BY d.createdAt DESC")
    List<DailyEntryItemEntity> findEntriesByDateRange(
            @Param("userId") UUID userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT d.createdAt FROM DailyEntryItemEntity d WHERE d.userId = :userId AND d.createdAt >= :since")
    List<LocalDateTime> findAllEntryTimestampsSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
}