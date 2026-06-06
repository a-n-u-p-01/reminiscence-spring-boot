package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.ConceptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConceptRepo extends JpaRepository<ConceptEntity, UUID> {

    Optional<ConceptEntity> findByNormalizedName(String normalizedName);

    // Layer 1: exact match — only returns rows matching submitted topics
    // efficient — hits the unique index on normalized_name
    @Query("SELECT c.normalizedName FROM ConceptEntity c WHERE c.normalizedName IN :normalizedTopics")
    List<String> findExistingNormalizedNames(@Param("normalizedTopics") List<String> normalizedTopics);

    // Layer 2: fuzzy match — one topic at a time, uses pg_trgm similarity
    // requires: CREATE EXTENSION IF NOT EXISTS pg_trgm;
    @Query(value = """
            SELECT normalized_name
            FROM retention.concept
            WHERE similarity(normalized_name, :topic) > 0.4
            """, nativeQuery = true)
    List<String> findFuzzyMatches(@Param("topic") String topic);

    @Query("SELECT c FROM ConceptEntity c WHERE c.normalizedName IN :names")
    List<ConceptEntity> findByNormalizedNameIn(@Param("names") List<String> names);

    @Query("""
    SELECT uc.concept.normalizedName
    FROM UserConceptEntity uc
    WHERE uc.userId = :userId
      AND uc.concept.normalizedName IN :normalizedTopics
    """)
    List<String> findExistingNormalizedNamesUserId(
            @Param("normalizedTopics") List<String> normalizedTopics,
            @Param("userId") UUID userId);

    @Query(value = """
        SELECT c.normalized_name
        FROM retention.concept c
        JOIN retention.user_concept uc
            ON uc.concept_id = c.id
        WHERE uc.user_id = :userId
          AND similarity(c.normalized_name, :topic) > 0.4
        ORDER BY similarity(c.normalized_name, :topic) DESC
        """, nativeQuery = true)
    List<String> findFuzzyMatchesAndUserId(
            @Param("topic") String topic,
            @Param("userId") UUID userId);
}