package com.anupam.reminiscence.service.impl;

import com.anupam.reminiscence.constants.ProcessStatus;
import com.anupam.reminiscence.entity.DailyEntryItemEntity;
import com.anupam.reminiscence.repo.DailyEntryItemRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EntryStatusService {

    private final DailyEntryItemRepo dailyEntryItemRepo;

    // This creates a NEW transaction, independent of the one that failed
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(DailyEntryItemEntity entry, String comment) {
        entry.setProcessingStatus(ProcessStatus.FAILED.name());
        entry.setProcessComment(comment);
        entry.setUpdatedAt(Instant.now());

        dailyEntryItemRepo.save(entry);
    }
}