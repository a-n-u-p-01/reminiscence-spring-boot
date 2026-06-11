package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.repo.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class NotificationScheduler {

    private final PushNotificationService pushService;
    private final UserRepository userRepository;

    /**
     * Executes every hour on the hour (e.g., 1:00, 2:00, etc.)
     * Sweeps globally to see which users just hit their 6 PM or 7 PM local windows.
     */
    @PostConstruct
    public void executeHourlyLocalizedNotificationSweep() {
        log.info("Starting localized hourly notification validation sweep...");

        Instant now = Instant.now();
        List<String> target6PmTimezones = new ArrayList<>();
        List<String> target7PmTimezones = new ArrayList<>();

        // 1. Evaluate all globally recognized timezones to check who is crossing the target hour marks
        for (String zoneIdStr : ZoneId.getAvailableZoneIds()) {
            try {
                ZoneId zoneId = ZoneId.of(zoneIdStr);
                ZonedDateTime localTime = ZonedDateTime.ofInstant(now, zoneId);

                // Check if the current time in this zone is exactly between 18:00 and 18:59
                if (localTime.getHour() == 18) {
                    target6PmTimezones.add(zoneIdStr);
                }
                // Check if the current time in this zone is exactly between 19:00 and 19:59
                else if (localTime.getHour() == 19) {
                    target7PmTimezones.add(zoneIdStr);
                }
            } catch (DateTimeException e) {
                // Skip unparseable anomalies safely
            }
        }

        // 2. Process Trigger 1 (6 PM Window)
        if (!target6PmTimezones.isEmpty()) {
            // Revisions are matched against local date. We take the date of the first matched zone as reference.
            LocalDate localToday = LocalDate.now(ZoneId.of(target6PmTimezones.get(0)));

            List<Map<String, String>> revisionTargets = userRepository.findTokensAndNamesForRevisions(target6PmTimezones, localToday);
            processAndDispatch(revisionTargets,
                    "Daily Revision Ready 📚",
                    "Hi %s, you have concepts ready for review. Keep your streak alive!",
                    "/revisions"
            );
        }

        // 3. Process Trigger 2 (7 PM Window)
        if (!target7PmTimezones.isEmpty()) {
            // Note completion uses local midnight boundary calculated in UTC
            ZoneId referenceZone = ZoneId.of(target7PmTimezones.get(0));
            Instant localMidnightUtc = LocalDate.now(referenceZone).atStartOfDay(referenceZone).toInstant();

            List<Map<String, String>> inactiveTargets = userRepository.findTokensAndNamesForInactivity(target7PmTimezones, localMidnightUtc);
            processAndDispatch(inactiveTargets,
                    "Write down your thoughts? ✏️",
                    "Hey %s, you haven't saved any notes today. Take a quick moment to log what you learned!",
                    "/notes/new"
            );
        }
    }

    /**
     * Iterates through target matches safely and formats text payloads cleanly
     */
    private void processAndDispatch(List<Map<String, String>> dataset, String title, String bodyTemplate, String targetRoute) {
        if (dataset == null || dataset.isEmpty()) return;

        for (Map<String, String> entry : dataset) {
            String pushToken = entry.get("pushToken");
            String fullName = entry.get("name");

            String cleanName = "there";
            if (fullName != null && !fullName.trim().isEmpty()) {
                cleanName = fullName.split(" ")[0]; // Grab first name dynamically
            }

            if (pushToken != null && !pushToken.trim().isEmpty()) {
                String dynamicBody = String.format(bodyTemplate, cleanName);
                pushService.sendNotification(pushToken, title, dynamicBody, targetRoute);
            }
        }
    }
}