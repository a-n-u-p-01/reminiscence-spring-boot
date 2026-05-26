package com.anupam.reminiscence.utils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PromptBuilder {

    public static String buildTopicExtractionPrompt(String text) {
        return """
        You are a concept extraction engine for a developer learning app.

        A user has written a note describing what they studied.

        Your task:
        Extract the distinct learning concepts from the note.

        Rules:

        1. Preserve the user's intended meaning exactly.

        2. Extract concepts that are meaningful units for revision.
           Each extracted item should be something that can reasonably become a flashcard.

        3. Keep semantically different concepts separate, even if they are closely related.

        4. Merge duplicates, aliases, and alternate phrasings when they represent the exact same concept.

        5. Expand well-known abbreviations only when the expansion preserves exact meaning.

        6. Correct obvious spelling mistakes silently without changing intended meaning.

        7. Do not over-fragment concepts into tiny implementation details.

        8. Do not make concepts broader or narrower than what the user intended.

        9. Do not invent concepts not explicitly mentioned or clearly implied.

        10. Return between 1 and 20 concepts.

        User note:
        "%s"

        Return ONLY valid JSON:

        {
          "topics": ["topic1", "topic2"]
        }

        Strict requirements:
        - JSON only
        - No markdown
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
            - Answer must directly answer the question
            - Keep it concise but sufficient to reactivate memory
            - Accurate and easy to understand

            Notes rules:
            - Notes must contain additional useful revision information NOT already in the answer
            - Notes should act like mini revision notes
            - 4 to 6 short clear sentences
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