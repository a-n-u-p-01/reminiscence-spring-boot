package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);


    /**
     * Finds push tokens and names for users inside target timezones who have outstanding
     * revisions scheduled for today (or overdue) in their local timeline.
     */
    @Query("SELECT u.pushToken AS pushToken, u.fullName AS name FROM UserEntity u " +
            "WHERE u.pushToken IS NOT NULL " +
            "AND u.timezone IN :timezones " +
            "AND u.id IN (SELECT uc.userId FROM UserConceptEntity uc WHERE uc.nextReviewDate <= :localToday)")
    List<Map<String, String>> findTokensAndNamesForRevisions(@Param("timezones") List<String> timezones,
                                                             @Param("localToday") LocalDate localToday);

    /**
     * Finds push tokens and names for users inside target timezones who have NOT saved
     * a note in DailyEntryItemEntity since their local midnight boundary.
     */
    @Query("SELECT u.pushToken AS pushToken, u.fullName AS name FROM UserEntity u " +
            "WHERE u.pushToken IS NOT NULL " +
            "AND u.timezone IN :timezones " +
            "AND u.id NOT IN (SELECT de.userId FROM DailyEntryItemEntity de WHERE de.createdAt >= :localMidnight)")
    List<Map<String, String>> findTokensAndNamesForInactivity(@Param("timezones") List<String> timezones,
                                                              @Param("localMidnight") Instant localMidnight);
}