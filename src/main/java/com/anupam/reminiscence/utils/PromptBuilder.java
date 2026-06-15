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
    public static @NonNull String buildFlashcardPrompt(List<String> topics) {

        int count = topics.size();

        String numberedTopics = IntStream.range(0, count)
                .mapToObj(i -> (i + 1) + ". " + topics.get(i))
                .collect(Collectors.joining("\n"));

        return """
You are an expert teacher and learning designer.

Topics:
%s

Generate EXACTLY %d flashcards.

GOAL

Teach understanding, not memorization.

After reading a flashcard, a learner should be able to:

* explain the concept in simple words
* understand why it matters
* recognize it in real situations
* remember it later without rereading

Silently correct spelling mistakes.

QUESTION

Generate the single most educational question for the topic.

Choose the question naturally.

Examples:

* What is X?
* How does X work?
* Why does X matter?
* What problem does X solve?
* When should X be used?
* What happens if X is missing?
* Why does X happen?

Requirements:

* Self-contained
* Natural sounding
* Specific
* Not a template

ANSWER

Exactly 2 lines separated by a single newline.

Line 1:
Simple beginner-friendly explanation.

Line 2:
Practical purpose, effect, or outcome.

INSIGHTS

Generate EXACTLY 5 separate insight lines.

IMPORTANT:
The 5 insights must feel different from each other.

Do NOT force a structure like:

* core idea
* why it exists
* how it works
* analogy
* limitation

for every flashcard.

Instead choose the most educational mix for the topic.

Possible insight types:

* surprising fact
* common misconception
* practical application
* mechanism
* analogy
* tradeoff
* limitation
* comparison
* failure mode
* real-world example
* hidden detail
* performance implication
* mental model
* historical reason
* best practice

Select whichever insight types fit the concept best.

DIVERSITY RULES

Avoid repetitive openings such as:

* Most people think...
* Most people miss...
* It exists because...
* It works by...
* Imagine...

Do not use the same insight structure repeatedly.

Each insight should reveal a different angle.

GOOD EXAMPLE

Binary Search:

Binary search only works when data is already sorted.
Checking a million items may require only about twenty comparisons.
It repeatedly removes half of the remaining search space.
Think of finding a word in a dictionary by jumping near the middle.
Using it on unsorted data produces incorrect results.

BAD EXAMPLE

It exists because...
It works by...
Imagine...
Most people think...
The mistake is...

STYLE

Write like a smart human teacher.

Use:

* concrete details
* simple language
* vivid examples
* memorable explanations

Avoid:

* textbook language
* corporate language
* filler
* generic statements
* marketing phrases

BANNED PHRASES

* improves efficiency
* enhances performance
* saves time
* provides flexibility
* helps developers
* improves scalability
* makes things easier
* optimizes resources
* streamlines processes

Explain the actual effect instead.

FACTUALITY

Do not invent history, motivations, origins, or design decisions.

Prefer:

* what it is
* how it works
* why it matters

over speculative explanations.

OUTPUT

Return ONLY valid JSON:

{
"flashcardList": [
{
"conceptName": "Topic Name",
"question": "string",
"answer": "string",
"notes": "string"
}
]
}

REQUIREMENTS

* EXACTLY %d flashcards
* JSON only
* No markdown
* No extra fields
* No text outside JSON

""".formatted(numberedTopics, count, count);
    }
}