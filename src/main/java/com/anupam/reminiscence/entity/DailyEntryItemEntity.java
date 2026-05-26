package com.anupam.reminiscence.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "daily_entry_item", schema = "retention")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyEntryItemEntity {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;

    @Column(name = "raw_topic", nullable = false)
    private String rawTopic;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus;

    @Column(name = "process_comment")
    private String processComment;

    @Column(name = "topic_extracted")
    private String topicExtracted;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
