package com.anupam.reminiscence.controller;

import com.anupam.reminiscence.dto.ConceptExplorerResponse;
import com.anupam.reminiscence.dto.DashboardDTOs.*;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.service.DashboardService;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/metrics")
    public ResponseEntity<MetricsResponse> getMetrics(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(dashboardService.getDashboardMetrics(user.getId()));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Integer>> getHeatmap(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(dashboardService.getMatrixHeatmap(user.getId()));
    }

    @GetMapping("/activity")
    public ResponseEntity<ActivityDetailsResponse> getActivityDetails(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(dashboardService.getDailyActivityDetails(user.getId(), date));
    }

    @GetMapping("/concepts")
    public ResponseEntity<ConceptExplorerResponse> getConceptsList(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "sortNewest", defaultValue = "true") boolean sortNewest,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user
    ) {
        return ResponseEntity.ok(
                dashboardService.getConceptsExplorerList(user.getId(), search, page, sortNewest)
        );
    }
}