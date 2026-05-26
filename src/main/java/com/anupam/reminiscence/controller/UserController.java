package com.anupam.reminiscence.controller;

import com.anupam.reminiscence.dto.ConceptReviewResponse;
import com.anupam.reminiscence.dto.DailyEntryRequest;
import com.anupam.reminiscence.dto.ReviewRequest;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/entry")
    public ResponseEntity<Void> saveDailyEntry(
            @RequestBody DailyEntryRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) throws JsonProcessingException {
        userService.saveDailyEntry(request.getText(), user.getId());
        return ResponseEntity.accepted().build(); // 202 — saved, processing in background
    }

    /**
     * Fetches all flashcard cards due for memory reviews today
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ConceptReviewResponse>> findAllPending(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) {
        List<ConceptReviewResponse> pendingList = userService.findAllPending(user.getId());
        return ResponseEntity.ok(pendingList); // 200 status with flattened object payload lists
    }
    /**
     * Fetches all flashcard cards due for memory reviews today
     */
    @GetMapping("/pending/count")
    public ResponseEntity<Integer> findAllPendingCount(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) {
        Integer pendingList = userService.findAllPendingCount(user.getId());
        return ResponseEntity.ok(pendingList); // 200 status with flattened object payload lists
    }

    @PostMapping("/concepts/{userConceptId}/review")
    public ResponseEntity<Void> reviewConcept(
            @PathVariable UUID userConceptId,
            @RequestBody ReviewRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) {
        userService.reviewConcept(user.getId(), userConceptId, request.getRating());
        return ResponseEntity.ok().build();
    }
}