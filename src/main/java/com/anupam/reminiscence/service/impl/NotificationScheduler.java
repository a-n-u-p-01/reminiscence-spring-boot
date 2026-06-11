package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.repo.UserConceptRepo;
import com.anupam.reminiscence.repo.UserRepository;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import com.anupam.reminiscence.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.*;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final PushNotificationService pushService;
    private final UserRepository userRepository;
    private final DailyEntryItemRepo dailyEntryItemRepo;
    private final UserConceptRepo userConceptRepo;

    // Runs every 30 minutes
    @Scheduled(cron = "0 0/30 * * * *")
    @PostConstruct
    public void runDailyEntryCheck() {
        log.info("⏰ Running 30-minute inactivity notification sweep (Evening check)...");

        List<UserEntity> allUsers = userRepository.findAll();

        for (UserEntity user : allUsers) {
            if (user.getPushToken() == null || user.getPushToken().trim().isEmpty()) {
                continue;
            }

            ZoneId zoneId = ZoneId.of(user.getTimezone() != null ? user.getTimezone() : "UTC");
            ZonedDateTime nowInZone = ZonedDateTime.now(zoneId);

            // CHANGED: Only notify if it's after 6:00 PM (18:00) local time
            if (nowInZone.toLocalTime().isAfter(LocalTime.of(18, 0))&& nowInZone.toLocalTime().isBefore(LocalTime.of(19, 0))) {

                Instant startOfDay = nowInZone.toLocalDate().atStartOfDay(zoneId).toInstant();
                Instant endOfDay = nowInZone.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant();

                boolean hasEntryToday = dailyEntryItemRepo.findTodayEntryByUserId(user.getId(), startOfDay, endOfDay).isPresent();

                if (!hasEntryToday) {
                    log.info("🔔 User {} has no entries today past 6 PM. Dispatching notification.", user.getFullName());

                    String firstName = (user.getFullName() != null && !user.getFullName().isEmpty())
                            ? user.getFullName().split(" ")[0] : "there";

                    pushService.sendNotification(
                            user.getPushToken(),
                            "Evening Reflection 🌙",
                            "Hey " + firstName + ", take a quick moment to log what you learned today!",
                            "/notes/new"
                    );
                }
            }
        }
    }

    // 2. NEW: 5 AM check for pending reviews
    @Scheduled(cron = "0 0/30 * * * *") // Runs every 30 mins, logic checks for 5 AM
    @PostConstruct
    public void runDailyRevisionCheck() {
        log.info("⏰ Running 30-minute pending revision notification sweep (Morning check)...");

        List<UserEntity> allUsers = userRepository.findAll();

        for (UserEntity user : allUsers) {
            if (user.getPushToken() == null || user.getPushToken().trim().isEmpty()) continue;

            ZoneId zoneId = ZoneId.of(user.getTimezone() != null ? user.getTimezone() : "UTC");
            ZonedDateTime nowInZone = ZonedDateTime.now(zoneId);

            // Only notify if it's 5:00 AM or later
            if (nowInZone.toLocalTime().isAfter(LocalTime.of(5, 0)) && nowInZone.toLocalTime().isBefore(LocalTime.of(6, 0))) {

                // Check pending reviews for TODAY
                int pendingCount = userConceptRepo.findPendingReviewsCount(user.getId(), LocalDate.now(zoneId));

                if (pendingCount > 0) {
                    log.info("📚 User {} has {} revisions pending. Sending notification.", user.getFullName(), pendingCount);

                    String firstName = (user.getFullName() != null && !user.getFullName().isEmpty())
                            ? user.getFullName().split(" ")[0] : "there";

                    pushService.sendNotification(
                            user.getPushToken(),
                            "Morning "+pendingCount+" Review Ready 📚",
                            "Good morning " + firstName + "! You have " + pendingCount + " concepts ready for your daily revision.",
                            "/revisions"
                    );
                }
            }
        }
    }
}