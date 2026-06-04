package com.anupam.reminiscence.constants;

/**
 * The four recall-quality levels a user self-reports after attempting to recall a concept.
 *
 * <p>These map directly to the FSRS-5 grade scale (1–4).  Lower grades mean the memory
 * trace is weak and the card will return soon; higher grades mean the trace is strong
 * and the interval grows.
 *
 * <p>UI usage: show all four buttons side-by-side after each card flip.
 * Pair each button with its {@link #displayLabel()} and {@link #hint()} so users
 * always pick the most accurate answer.
 */
public enum RecallRating {

    /**
     * Grade 1 — "Fully Forgot"
     * <p>The concept felt completely unfamiliar. You could not recall what it was about.
     * <p>Engine effect: memory lapse — stability drops, difficulty rises, interval resets to ~1 day.
     */
    FORGOT,

    /**
     * Grade 2 — "Little Remembered"
     * <p>You recognised the concept and remembered something about it,
     * but the details were mostly missing.
     * <p>Engine effect: small stability gain (with a penalty vs RECALLED), difficulty nudges up.
     */
    PARTIAL,

    /**
     * Grade 3 — "Remembered"
     * <p>You recalled the main idea correctly and could explain at least one or two important points.
     * <p>Engine effect: baseline stability gain, difficulty remains stable.
     */
    RECALLED,

    /**
     * Grade 4 — "Clearly Remembered"
     * <p>You recalled the concept immediately and remembered most of the important details
     * without effort.
     * <p>Engine effect: maximum stability gain (30 % bonus vs RECALLED), difficulty decreases
     * over time.
     */
    FLUENT;

    // ─────────────────────────────────────────────────────────────────────
    //  UI helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Short human-readable label suitable for a UI button (≤ 20 chars).
     */
    public String displayLabel() {
        return switch (this) {
            case FORGOT   -> "Fully Forgot";
            case PARTIAL  -> "Little Remembered";
            case RECALLED -> "Remembered";
            case FLUENT   -> "Clearly Remembered";
        };
    }

    /**
     * One-sentence hint displayed under the button to help users pick the right rating.
     */
    public String hint() {
        return switch (this) {
            case FORGOT   ->
                    "The concept felt completely unfamiliar — you could not recall what it was about.";
            case PARTIAL  ->
                    "You recognised the concept and remembered something, but details were mostly missing.";
            case RECALLED ->
                    "You recalled the main idea correctly and could explain at least one or two key points.";
            case FLUENT   ->
                    "You recalled the concept immediately with full detail and no effort.";
        };
    }

    /**
     * FSRS grade integer (1–4) used internally by the scheduling formulas.
     */
    public int fsrsGrade() {
        return switch (this) {
            case FORGOT   -> 1;
            case PARTIAL  -> 2;
            case RECALLED -> 3;
            case FLUENT   -> 4;
        };
    }
}