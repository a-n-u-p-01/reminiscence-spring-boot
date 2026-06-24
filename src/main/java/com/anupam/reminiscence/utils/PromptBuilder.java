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
    public static String buildClassificationPrompt(String topic) {
        return """
    You are a topic classifier. Analyze the following topic and decide the most appropriate flashcard format.
    Choose exactly ONE of these types:
    - EXPLANATION: For abstract concepts, principles, theories (e.g., Loose coupling, Dependency injection).
    - LIST: For sets of items, methods, functions, features (e.g., String methods, HTTP status codes).
    - STEPS: For processes, workflows, step-by-step guides (e.g., How to set up Jest, Deploy a React app).
    - COMPARISON: For comparing two or more items (e.g., React vs Vue, SQL vs NoSQL).
    - SUMMARY: For quick, concise overviews (e.g., JavaScript closure, Hoisting).
    - EXAMPLE: For practical code snippets, real-world scenarios (e.g., Singleton pattern in Java).

    Topic: "%s"

    Return ONLY a valid JSON object: {"type": "TYPE_NAME"}
    """.formatted(topic);
    }

    public static String buildTypedFlashcardPrompt(String topic, String type) {
        String typeInstructions = switch (type.toUpperCase()) {
            case "EXPLANATION" -> """
            Structure the flashcard as a Deep Explanation:
            - Answer: Define the concept, explain its importance, give a concrete example. Write in well-structured paragraphs.
            - Question: Ask "Explain what X is and why it is important."
            """;
            case "LIST" -> """
            Structure the flashcard as a List / Enumeration:
            - Answer: Start with a brief definition, then provide all list of key items with a short description for each. and list should cover all items present in topic
            - Question: Ask "What are the key X and what do they do?"
            """;
            case "STEPS" -> """
            Structure the flashcard as a Step-by-Step Guide:
            - Answer: Provide a clear, numbered step-by-step guide.
            - Question: Ask "How do you perform X?"
            """;
            case "COMPARISON" -> """
            Structure the flashcard as a Comparison:
            - Answer: Highlight similarities, differences, pros/cons, and use cases.
            - Question: Ask "What are the differences between A and B?" (or similar).
            """;
            case "SUMMARY" -> """
            Structure the flashcard as a Concise Summary:
            - Answer: Provide a 2-3 sentence crisp summary.
            - Question: Ask "Define X in one sentence." or similar.
            """;
            case "EXAMPLE" -> """
            Structure the flashcard as a Real-world Example:
            - Answer: Show a concrete example (code/pseudocode/scenario) and explain it.
            - Question: Ask "Give a real-world scenario where X applies."
            """;
            default -> """
            Structure the flashcard in a balanced, informative way.
            """;
        };

        return """
    You are a flashcard generation engine for a spaced-repetition learning app.
    Your job is to turn the submitted topic into ONE clear, accurate, and easy-to-remember revision flashcard.

    Topic: "%s"
    Required Type: %s

    %s

    Field "conceptName":
    - The submitted topic, with correct spelling and in Title Case.
    - Keep the same concept that was submitted; only fix spelling and capitalization, never replace it with a different term.

    Field "question":
    - Write ONE clear question that makes the learner recall the WHOLE concept, not just one small fact.
    - Aim for genuine understanding of the idea, not interview trivia.
    - Make the question complete and self-contained, so it makes sense on its own without seeing the topic name.
    - Phrase it the way a learner would naturally review the topic.
    - If the topic is specific, match the question to that level of specificity.
    - Ask only one thing. Do NOT combine multiple questions into one.
    - Adapt the question to the required type (e.g., "What are the key X?" for LIST, "How do you perform X?" for STEPS, etc.).

    Field "answer":
    - Directly and fully answer the question.
    - Use simple, everyday language that makes sense on the very first read.
    - Avoid dense textbook definitions, heavy jargon, and robotic phrasing.
    - Follow the structure specified for the required type.
    - If it aids understanding, include a short, concrete example.
    - Only 2 to 3 sentence. if needed increase the sentece to cover.

    Field "notes":
    - Add extra useful information that is NOT already stated in the answer.
    - Cover five distinct and important points that deepen understanding of the concept, such as common uses, key benefits, typical mistakes, helpful comparisons, or a real-world example.
    - Explain each point clearly in a short sentence, not as a one-word label.
    - Use a simple example wherever it makes a point easier to grasp.
    - Use clear, friendly language that is easy to read even for someone seeing the topic for the first time.
    - Write the notes as plain, connected sentences in a single flowing paragraph: no markdown, no HTML, no bullet symbols, no numbering, and no headings.

    Overall writing style for every card:
    - Clear, calm, and beginner-friendly.
    - Prefer short sentences over long, packed ones.
    - Explain any unavoidable technical term in plain words.

    Return ONLY valid JSON in exactly this shape:

        {
          "conceptName": "string",
          "question": "string",
          "answer": "string",
          "notes": "string"
        }

    Strict requirements:
    - EXACTLY one flashcard, for the submitted topic.
    - Valid JSON only.
    - No markdown.
    - No explanation.
    - No extra text before or after the JSON.
    """.formatted(topic, type, typeInstructions);
    }

    public static String buildFlashcardPrompt(List<String> topics) {

        int count = topics.size();

        String numberedTopics = IntStream.range(0, count)
                .mapToObj(i -> (i + 1) + ". " + topics.get(i))
                .collect(Collectors.joining("\n"));

        return """
    You are a flashcard generation engine for a spaced-repetition learning app.
    Your job is to turn each submitted topic into ONE clear, accurate, and easy-to-remember revision flashcard.

    Submitted topics:
    %s

    Task:
    Generate EXACTLY one flashcard for EACH submitted topic, in the same order they were submitted.
    Total flashcards required: EXACTLY %d (one per topic).

    How to interpret each topic:

    1. Take each topic at its literal meaning.
    2. Keep the exact concept the user intended. Do NOT swap it for a related but different concept.
    3. Do NOT make the concept broader or narrower than what was submitted.
    4. Do NOT invent missing qualifiers, technologies, frameworks, domains, or subtypes that the topic does not mention.
    5. If a topic has a spelling mistake, silently fix the spelling while keeping the intended meaning.

    Field "conceptName":
    - The submitted topic, with correct spelling and in Title Case.
    - Keep the same concept that was submitted; only fix spelling and capitalization, never replace it with a different term.

    Field "question":
    - Write ONE clear question that makes the learner recall the WHOLE concept, not just one small fact.
    - Aim for genuine understanding of the idea, not interview trivia.
    - Make the question complete and self-contained, so it makes sense on its own without seeing the topic name.
    - Phrase it the way a learner would naturally review the topic.
    - If the topic is specific, match the question to that level of specificity.
    - Ask only one thing. Do NOT combine multiple questions into one.

    Field "answer":
    - Directly and fully answer the question.
    - Use simple, everyday language that makes sense on the very first read.
    - Avoid dense textbook definitions, heavy jargon, and robotic phrasing.
    - If it aids understanding, include a short, concrete example.

    Field "notes":
    - Add extra useful information that is NOT already stated in the answer.
    - Cover five distinct and important points that deepen understanding of the concept, such as common uses, key benefits, typical mistakes, helpful comparisons, or a real-world example.
    - Explain each point clearly in a short sentence, not as a one-word label.
    - Use a simple example wherever it makes a point easier to grasp.
    - Use clear, friendly language that is easy to read even for someone seeing the topic for the first time.
    - Write the notes as plain, connected sentences in a single flowing paragraph: no markdown, no HTML, no bullet symbols, no numbering, and no headings.

    Overall writing style for every card:
    - Clear, calm, and beginner-friendly.
    - Prefer short sentences over long, packed ones.
    - Explain any unavoidable technical term in plain words.

    Return ONLY valid JSON in exactly this shape:

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
    - EXACTLY %d flashcards, one per submitted topic, in submission order.
    - Valid JSON only.
    - No markdown.
    - No explanation.
    - No extra text before or after the JSON.
    """.formatted(numberedTopics, count, count);
    }
}