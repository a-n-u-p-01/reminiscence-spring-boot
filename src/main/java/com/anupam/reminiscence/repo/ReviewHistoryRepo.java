package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.ReviewHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewHistoryRepo extends JpaRepository<ReviewHistoryEntity, UUID> {

    @Query("SELECT r FROM ReviewHistoryEntity r WHERE r.userId = :userId AND r.reviewedAt BETWEEN :start AND :end ORDER BY r.reviewedAt DESC")
    List<ReviewHistoryEntity> findHistoricalReviewsByDate(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT r.reviewedAt FROM ReviewHistoryEntity r WHERE r.userId = :userId AND r.reviewedAt >= :since")
    List<Instant> findAllReviewTimestampsSince(@Param("userId") UUID userId, @Param("since") Instant since);
}