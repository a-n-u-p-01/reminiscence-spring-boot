package com.anupam.reminiscence.utils;

import lombok.Data;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PromptBuilder {


    public static String buildTopicExtractionPrompt(String text) {
        return """
You are an educational concept extraction engine for a spaced repetition and revision system.

A user submits free-form study notes.

Your task:
Extract ONLY meaningful study concepts suitable for future revision. 

A valid concept must:
- represent a meaningful learnable unit
- have clear educational or semantic value
- be specific enough for revision
- preserve the user’s intended meaning

Extraction principles:
1. Extract only meaningful learnable concepts such as topics, skills, technologies, frameworks, systems, methodologies, theories, and educational subjects.

2. Ignore noise, filler text, incomplete thoughts, generic operational language, and low-confidence concepts.

3. Extract only concepts that are explicitly present or strongly implied; never guess or invent concepts.

4. Preserve the user's intended meaning and semantic scope exactly.

5. Use concise, canonical, revision-friendly concept names.

6. Merge duplicates, equivalent variations, and obvious spelling mistakes.

7. Treat granular technical mechanisms, protocols, and sub-components (e.g., "refresh tokens") as valid, standalone concepts.

8. DO NOT subsume or group specific terms under broader parent categories. If a user lists both a parent system (e.g., "JWT authentication") and a specific component (e.g., "refresh tokens"), you must extract BOTH.

9. Avoid descriptive rewrites, classifications, metadata, or explanatory phrases.

10. When a concept is ambiguous, preserve the original term rather than interpreting it.

11. Prioritize precision and quality over quantity; return an empty list if no meaningful study concepts exist.

12. Take this task strictly and do not miss ANY distinct technical concepts that the user explicitly mentioned.

User note:
"%s"

Return ONLY valid JSON:

{
  "topics": ["topic1", "topic2"]
}

Strict requirements:
- JSON only
- No markdown formatting (no ```json blocks)
- No explanation
- No extra text
""".formatted(text);
    }
    // AI Call 1 — semantic dedup only
    public static String buildDeduplicationPrompt(
            List<String> submittedTopics,
            List<String> candidates
    ) {
        String numberedTopics = IntStream.range(0, submittedTopics.size())
                .mapToObj(i -> (i + 1) + ". " + submittedTopics.get(i))
                .collect(Collectors.joining("\n"));

        String candidateList = candidates.isEmpty()
                ? "None"
                : String.join("\n", candidates);

        return """
            You are a deterministic semantic deduplication engine.

            Existing concepts already stored:
            %s

            Submitted topics:
            %s

            Task:
            Return ONLY submitted topics that are genuinely NEW.

            Decision rules:

            1. If there are NO existing concepts, then ALL submitted topics are new.

            2. A topic is a duplicate ONLY if it represents EXACTLY the same concept
               as an existing concept.

            3. Similar wording does NOT automatically mean duplicate.

            4. A broader concept and a narrower concept are DIFFERENT.

            5. A parent concept and its subtype are DIFFERENT.

            6. Shared words, prefixes, suffixes, or acronyms do NOT automatically
               make concepts duplicates.

            7. Do NOT invent missing qualifiers.

            8. Treat submitted topics literally.

            Examples of exact equivalence:
            abbreviation ↔ full form
            acronym ↔ expanded meaning
            alias ↔ canonical name

            Output rules:
            - Return ONLY submitted topics that are new
            - Preserve original submitted spelling/text
            - Do NOT rename topics
            - Do NOT expand abbreviations
            - Do NOT make topics more specific

            Return ONLY valid JSON:

            {
              "newTopics": ["exact submitted topic text"]
            }

            If none are new:

            {
              "newTopics": []
            }

            Strict JSON only.
            No markdown.
            No explanation.
            """.formatted(candidateList, numberedTopics);
    }

    // AI Call 2 — flashcard generation only
// AI Call 2 — flashcard generation only
    public static @NonNull String buildFlashcardPrompt(List<String> topics) {

        int count = topics.size();

        String numberedTopics = IntStream.range(0, count)
                .mapToObj(i -> (i + 1) + ". " + topics.get(i))
                .collect(Collectors.joining("\n"));

        return """
You are a deterministic flashcard generation engine.
Topics:
%s

Task: Generate EXACTLY %d flashcard(s). Silently correct spelling.

CRITICAL QUALITY RULE:
Be highly specific. Vague phrases like "saves time", "makes it easier", "improves efficiency", or "is beneficial" are strictly BANNED. Write plainly, like a smart peer explaining an idea over coffee.

Question rules:
- Direct, clean, and self-contained. Format: "What is [Topic] and how/why is it used?" (or its core purpose if it is an abstract concept).

Answer rules (Strict 2-line layout separated by a single newline character):
Line 1: Clear, ultra-simple core definition with zero complex jargon (1 sentence).
Line 2: Instead of [the manual/problem state], this allows [the specific optimized outcome].

Notes rules (EXACTLY 5 separate text lines separated by single newlines. NO markdown, NO HTML, NO numbering, NO bullet points, and NO literal text prefixes/labels):
Line 1: Explain the raw, underlying operational mechanism or core logic of how it functions.
Line 2: Explain the single most powerful concrete leverage or advantage it gives you.
Line 3: Provide a vivid, casual everyday metaphor to make the concept instantly relatable in conversation.
Line 4: State the exact hidden trade-off, limitation, or scenario where it fails or should be avoided.
Line 5: Describe the explicit negative symptom, real-world crash, or breakdown that occurs if this is missing or broken.

Return ONLY valid JSON:
{
  "flashcardList": [
    {
      "conceptName": "Title Case matching input exactly",
      "question": "string",
      "answer": "string",
      "notes": "string"
    }
  ]
}

Strict requirements:
- EXACTLY %d flashcards
- JSON only
- No markdown formatting anywhere inside the JSON properties
- No conversational filler text outside the JSON
        """.formatted(numberedTopics, count, count);
    }
}