package com.anupam.reminiscence.repo;

import com.anupam.reminiscence.entity.ConceptEntity;
import com.anupam.reminiscence.entity.UserConceptEntity;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserConceptSpecifications {

    public static Specification<UserConceptEntity> hasUserIdAndSearchTerm(UUID userId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Match User ID ownership matrix context
            predicates.add(cb.equal(root.get("userId"), userId));

            // 2. Dynamic multi-word matching on the joined Concept name relations
            if (search != null && !search.trim().isEmpty()) {
                Join<UserConceptEntity, ConceptEntity> conceptJoin = root.join("concept");

                // Split search query by spaces into individual words tokens
                String[] tokens = search.trim().toLowerCase().split("\\s+");
                List<Predicate> searchPredicates = new ArrayList<>();

                for (String token : tokens) {
                    // Each structural token must match somewhere within c.name
                    searchPredicates.add(
                            cb.like(cb.lower(conceptJoin.get("name")), "%" + token + "%")
                    );
                }

                // Combine them with logical AND parameters (all distinct query tokens must exist)
                predicates.add(cb.and(searchPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}