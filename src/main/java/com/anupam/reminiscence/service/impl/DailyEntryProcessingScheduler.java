package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.constants.ProcessStatus;
import com.anupam.reminiscence.dto.auth.LoginRequest;
import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import com.anupam.reminiscence.service.impl.ConceptProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailyEntryProcessingScheduler {

    private final DailyEntryItemRepo dailyEntryItemRepo;
    private final ConceptProcessingService conceptProcessingService;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Kolkata")
    public void processPendingEntries() {

        log.info("Starting daily entry processing job at timestamp: {}", Instant.now());

        List<DailyEntryItemEntity> pendingEntries =
                dailyEntryItemRepo.findByProcessingStatusNot(ProcessStatus.SUCCESS.name());

        if (pendingEntries.isEmpty()) {
            log.info("No pending entries found.");
            return;
        }

        log.info("Found {} entries to process", pendingEntries.size());

        for (DailyEntryItemEntity entry : pendingEntries) {
            try {
                log.info("Processing entryId={} userId={}", entry.getId(), entry.getUserId());

                conceptProcessingService.processEntry(entry);

            } catch (Exception e) {
                log.error("Failed processing entryId={} userId={} at timestamp={}",
                        entry.getId(), entry.getUserId(), Instant.now(), e);
            }
        }

        log.info("Daily entry processing job completed at timestamp: {}", Instant.now());
    }
}