package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.constants.ProcessStatus;
import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import com.anupam.reminiscence.entity.UserEntity;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import com.anupam.reminiscence.repo.UserConceptRepo;
import com.anupam.reminiscence.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyEntryProcessingScheduler {

    private final DailyEntryItemRepo dailyEntryItemRepo;
    private final ConceptProcessingService conceptProcessingService;
    private final UserConceptRepo userConceptRepo;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0,30 * * * *")// Every 30 minute
    public void processPendingEntries() {

        log.info("Starting daily entry processing job at {}", Instant.now());

        List<DailyEntryItemEntity> pendingEntries = dailyEntryItemRepo.findByProcessingStatusNot(ProcessStatus.SUCCESS.name());

        if (pendingEntries.isEmpty()) {
            log.info("No pending entries found");
            return;
        }

        log.info("Found {} entries to evaluate", pendingEntries.size());

        for (DailyEntryItemEntity entry : pendingEntries) {

            try {

                UserEntity user = userRepository.findById(entry.getUserId()).orElse(null);

                if (user == null) {
                    continue;
                }

                ZoneId zoneId = ZoneId.of(user.getTimezone() == null ? "UTC" : user.getTimezone());

                /*
                 * User current date
                 */
                LocalDate userToday = LocalDate.now(zoneId);

                /*
                 * Date when entry was created
                 * in user's timezone
                 */
                LocalDate entryDate = LocalDate.ofInstant(entry.getCreatedAt(), zoneId);

                /*
                 * Process only after user's day changes.
                 *
                 * Example:
                 * Entry -> June 4
                 * Today -> June 5
                 */
                if (!entryDate.isBefore(userToday)) {
                    continue;
                }

                log.info("Processing entryId={} userId={} timezone={}", entry.getId(), entry.getUserId(), zoneId);

                int pendingReviewCount = userConceptRepo.findPendingReviewsCount(entry.getUserId(), entryDate);

                /*
                 * User never completed reviews.
                 * Skip today's processing.
                 */
                if (pendingReviewCount > 100) {

                    log.info("Skipping entryId={} because user has {} pending reviews", entry.getId(), pendingReviewCount);

                    dailyEntryItemRepo.delete(entry);
                    continue;
                }

                conceptProcessingService.processEntry(entry);

            } catch (Exception e) {

                log.error("Failed processing entryId={} userId={}", entry.getId(), entry.getUserId(), e);
            }
        }

        log.info("Daily entry processing job completed");
    }
}