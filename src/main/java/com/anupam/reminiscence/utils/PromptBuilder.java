package com.anupam.reminiscence.utils;

import lombok.Data;

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
    public static String buildFlashcardPrompt(List<String> topics) {

        int count = topics.size();

        String numberedTopics = IntStream.range(0, count)
                .mapToObj(i -> (i + 1) + ". " + topics.get(i))
                .collect(Collectors.joining("\n"));

        return """
            You are a deterministic flashcard generation engine for a memory retention learning app.

            Submitted topics:
            %s

            Task:
            Generate EXACTLY one high-quality revision flashcard for EACH submitted topic.

            Core interpretation rules:

            1. Treat each submitted topic literally in meaning.

            2. Preserve the intended concept exactly.
               Do NOT reinterpret it as a related but different concept.

            3. Do NOT make a concept broader or narrower than submitted.

            4. Do NOT invent missing qualifiers, technologies, frameworks, domains, or subtypes.

            5. If a submitted topic contains spelling mistakes, silently correct the spelling
               while preserving the intended meaning.

            6. Standard abbreviation expansion is allowed ONLY when it represents the exact same concept.
               Examples:
               - JWT → JSON Web Token
               - OOP → Object Oriented Programming

            7. Related concepts are NOT substitutes.
               A related concept must never replace the submitted one.

            Flashcard generation rules:

            - Generate EXACTLY one flashcard per submitted topic
            - Total flashcards required: EXACTLY %d

            Question rules:
            - The question must trigger recall of the WHOLE concept, not one isolated fact
            - Prefer concept-level understanding over interview trivia
            - The question should feel natural for revision
            - The question should be complete and self-contained
            - Prefer "What is X and how/why is it used?" style when appropriate
            - If the topic itself is specific, align the question to that specificity

            Answer rules:
            - Answer must directly answer to the question
            - Answer should simple and small and easy to read not overwhelming
            - Answer should in one paragraph
            - Answer each sentence should specific not a generic answer
            - if required explain with simple example

            Notes rules:
            - Notes must contain additional useful information NOT already in the answer
            - Notes should contains 5 additional important points
            - Note should explained clearly not a short note
            - User example if needed
            - No keyword dumping

            Concept name rules:
            - Correctly spelled
            - Title Case
            - Preserve original meaning

            Return ONLY valid JSON:

            {
              "flashcardList": [
                {
                  "conceptName": "string",
                  "question": "string",
                  "answer": "string",
                  "notes": "string"
                }
              ]
            }

            Strict requirements:
            - EXACTLY %d flashcards
            - JSON only
            - No markdown
            - No explanation
            - No extra text
            """.formatted(numberedTopics, count, count);
    }
}