package com.anupam.reminiscence.utils;

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

1. Extract only concepts that represent actual knowledge, skills, technologies, theories, topics, frameworks, systems, methodologies, or educational subjects.

2. Ignore meaningless text, corrupted text, keyboard spam, random fragments, repeated characters, low-signal phrases, filler words, and incomplete thoughts.

3. Ignore generic operational words unless they clearly represent a study concept in context.

4. Ignore concepts with weak semantic confidence.

5. Do not infer concepts that are not explicitly present or strongly implied.

6. Preserve semantic intent exactly.

7. Merge duplicate concepts and alternate phrasings when they refer to the same underlying concept.

8. Correct obvious spelling mistakes silently while preserving meaning.

9. Keep concepts concise, revision-friendly, and human-readable.

10. Do not over-fragment concepts into tiny implementation details.

11. Do not broaden or narrow the scope of the concept beyond the user's intent.

12. Expand abbreviations only when the meaning is unambiguous and universally understood.

13. Return an empty array when no meaningful study concepts exist.

14. The output should prioritize quality over quantity.

15. A concept should be stable enough that a user could meaningfully revise it later as an independent learning item.

16. Preserve concepts in their natural canonical form without adding generated qualifiers, classifications, interpretations, or explanatory suffixes.

17. Avoid transforming concepts into descriptive phrases when the original concise concept form is sufficient.

18. Prefer stable standalone revision topics over contextual or explanatory rewrites.

19. When a concept is ambiguous, preserve the original term instead of generating interpretive expansions.

20. Do not inject semantic categorization into the output.

21. The output should contain only the clean concept itself, not metadata about the concept.

22. Favor concise domain-recognizable terminology over explanatory language.

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