package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.UserConceptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserConceptRepo extends JpaRepository<UserConceptEntity, UUID> {

    boolean existsByUserIdAndConceptId(UUID userId, UUID conceptId);
    // Standard JPQL Join to fetch user metrics paired directly with static content metadata
    @Query("SELECT uc FROM UserConceptEntity uc WHERE uc.userId = :userId AND uc.nextReviewDate <= :today")
    List<UserConceptEntity> findPendingReviews(@Param("userId") UUID userId, @Param("today") LocalDate today);

    @Query("SELECT count(*) FROM UserConceptEntity uc WHERE uc.userId = :userId AND uc.nextReviewDate <= :today")
    int findPendingReviewsCount(@Param("userId") UUID userId, @Param("today") LocalDate today);

    Optional<UserConceptEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT COUNT(uc) FROM UserConceptEntity uc WHERE uc.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    @Query("SELECT COALESCE(AVG(uc.masteryScore), 0.0) FROM UserConceptEntity uc WHERE uc.userId = :userId")
    Double findAverageMasteryScoreByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(uc) FROM UserConceptEntity uc WHERE uc.userId = :userId AND uc.nextReviewDate = :targetDate")
    long countConceptsDueOnDate(@Param("userId") UUID userId, @Param("targetDate") LocalDate targetDate);

    @Query("SELECT COUNT(uc) FROM UserConceptEntity uc WHERE uc.userId = :userId AND uc.nextReviewDate BETWEEN :start AND :end")
    long countConceptsDueBetweenDates(@Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    Optional<UserConceptEntity> findByUserIdAndConceptId(UUID userId, UUID conceptId);
}