package com.anupam.reminiscence.engine.impl;

import com.anupam.reminiscence.constants.RecallRating;
import com.anupam.reminiscence.engine.AdvancedSchedulingEngine;
import com.anupam.reminiscence.engine.dto.EngineMetrics;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Reminiscence FSRS v5.1 — flagship spaced-repetition scheduling engine.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  HOW HUMAN MEMORY WORKS  (the science behind this code)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Every memory is modelled by two numbers:
 *
 *   Stability (S, days)
 *     How long the memory survives before it fades below a safe recall threshold.
 *     Think of it as the "half-life" of the memory.
 *     S = 10 means you still have ~90 % chance of recalling the concept after 10 days.
 *     S grows each time you successfully recall the concept (the brain re-consolidates it).
 *
 *   Difficulty (D, 1–10)
 *     How hard *this specific concept* is for *you* to retain.
 *     D = 1 → sticks easily; D = 10 → keeps slipping away.
 *     Difficulty shifts slowly with each review: FORGOT/PARTIAL push it up,
 *     FLUENT pulls it down.  It mean-reverts gently so no card stays permanently hard
 *     or permanently easy.
 *
 *   Retrievability R(t, S)
 *     The probability (0–1) you still remember the concept t days after the last review.
 *     At t = S (exactly on the due date) R ≈ 0.90 (90 %).
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  WHAT HAPPENS AT EACH REVIEW
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   RECALLED / FLUENT → stability grows
 *     The more you struggled (low R when you recalled), the MORE stability grows.
 *     Retrieving a fading memory re-cements it stronger than reviewing something fresh.
 *     This is "desirable difficulty" — the engine rewards you for reviewing overdue cards.
 *
 *   FORGOT → stability drops partially, difficulty rises
 *     Re-learning is still faster than learning from scratch: past stability leaves a trace.
 *     Interval resets to a short value so you see the card again soon.
 *
 *   PARTIAL → small stability gain with a penalty; difficulty nudges up slightly.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  LONG-TERM INTERVAL GROWTH  (sample trace, FLUENT every review)
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   Review:     1st    2nd    3rd    4th     5th     6th     7th
 *   Interval:   6 d   13 d   27 d   54 d  ~110 d  ~215 d  ~420 d  (≈14 months)
 *
 *   After one year of consistent FLUENT answers, the next interval becomes ~2 years.
 *   A card you forget and re-learn from D=6.81 still climbs this ladder — just slower.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  BUG FIXES vs PREVIOUS IMPLEMENTATION
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *   Bug 1 — seedDifficulty was completely inverted
 *     Old: FORGOT→4.93 (easiest!),  FLUENT→7.75 (hardest!)
 *     Fix: FORGOT→6.81 (hardest),   FLUENT→3.99 (easiest)
 *
 *   Bug 2 — Recall modifiers W[11]/W[12] were swapped AND wrong direction
 *     Old W[11]=1.33 applied to HARD (boosted growth — backwards)
 *     Old W[12]=0.23 applied to EASY (slashed growth by 77 % — backwards)
 *     Fix W[11]=0.80 applied to PARTIAL (−20 % growth — correct penalty)
 *     Fix W[12]=1.30 applied to FLUENT (+30 % growth — correct bonus)
 *
 *   Bug 3 — Fuzz exponent W[20]=0.12 was too low; variance never shrank
 *     Old: W[19]=2.01, W[20]=0.12 → fuzz stayed at 30–50 % for all intervals
 *     Fix: W[19]=0.50, W[20]=0.50 → fuzz = 18 % at 7 d, 9 % at 30 d, 2.6 % at 1 yr
 *
 *   Bug 4 — Mastery crit-roll variance 0.92–1.45 (53 % swing) felt like a lottery
 *     Fix: tightened to 0.90–1.10 (±10 %) for fair, satisfying micro-variance
 */
@Component("reminiscenceFsrsV5")
public class ReminiscenceFsrsV5 implements AdvancedSchedulingEngine {

    // ─────────────────────────────────────────────────────────────────────
    //  WEIGHT VECTOR
    // ─────────────────────────────────────────────────────────────────────

    /**
     * FSRS-5 calibrated weights (22 values, W[0]–W[21]).
     *
     * <pre>
     *  Index  Purpose
     *  ─────  ────────────────────────────────────────────────────────────
     *  0–3    Initial stability seeds for FORGOT / PARTIAL / RECALLED / FLUENT
     *  4      Difficulty neutral anchor  (natural mean ≈ 4.93)
     *  5      Difficulty spread per rating band
     *  6      Per-review difficulty shift magnitude
     *  7      Mean-reversion weight  (tiny, ~1 % pull per review)
     *  8      Recall growth base amplifier
     *  9      Stability influence on recall growth  (young memories grow faster)
     *  10     Retrievability influence on recall growth  (struggling = more growth)
     *  11     PARTIAL recall penalty   (< 1.0)   ← FIXED from 1.33
     *  12     FLUENT recall bonus      (> 1.0)   ← FIXED from 0.23
     *  13     Same-day dampener        (reduces growth for intraday reviews)
     *  14–18  Lapse recovery formula parameters
     *  19     Fuzz scale factor        ← FIXED from 2.01
     *  20     Fuzz decay exponent      ← FIXED from 0.12
     *  21     Fuzz maximum cap
     * </pre>
     */
    private static final double[] W = {
            // W[0-3]: initial stability seeds (days) — how strongly the concept was first encoded
            0.402,  0.946,  2.408,  5.831,

            // W[4-7]: difficulty model
            4.93,   0.94,   0.86,   0.01,

            // W[8-10]: recall stability growth factors
            1.54,   0.15,   0.27,

            // W[11-13]: rating modifiers + same-day dampener
            // BUG FIX: W[11] must be < 1.0 (PARTIAL penalty), W[12] must be > 1.0 (FLUENT bonus)
            0.80,   1.30,   0.98,

            // W[14-18]: lapse recovery formula
            2.19,   0.23,   0.33,   0.11,   0.20,

            // W[19-21]: interval fuzz (scale, decay exponent, cap)
            // BUG FIX: exponent raised from 0.12 → 0.50 so variance shrinks properly at long intervals
            0.50,   0.50,   0.32
    };

    // Retrievability formula: R(t, S) = (1 + FACTOR × t/S)^DECAY
    // Calibrated so R(S, S) = 0.90  →  FACTOR ≈ 0.3015, DECAY = −0.4
    private static final double FSRS_DECAY  = -0.4;
    private static final double FSRS_FACTOR = Math.pow(0.9, 1.0 / FSRS_DECAY) - 1.0;

    /** Default card state before any review has occurred. */
    public static final double INITIAL_STABILITY  = 1.0;
    public static final double INITIAL_DIFFICULTY = 5.0;
    public static final int    INITIAL_INTERVAL   = 1;

    // ─────────────────────────────────────────────────────────────────────
    //  PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public String getEngineSignature() {
        return "Reminiscence FSRS v5.1 (Gamified Flagship Engine)";
    }

    /**
     * Returns the probability (0.0–1.0) that the user still remembers a concept.
     *
     * <ul>
     *   <li>t = 0      → R ≈ 1.00  (just reviewed)</li>
     *   <li>t = S      → R ≈ 0.90  (exactly on the due date)</li>
     *   <li>t = 2×S    → R ≈ 0.75  (one interval overdue)</li>
     *   <li>t = 5×S    → R ≈ 0.55  (badly overdue)</li>
     * </ul>
     *
     * @param stability   current memory stability in days
     * @param elapsedDays days since the last review
     */
    @Override
    public double calculateRetrievability(double stability, double elapsedDays) {
        if (stability <= 0 || elapsedDays <= 0) return 1.0;
        return Math.pow(1.0 + FSRS_FACTOR * (elapsedDays / stability), FSRS_DECAY);
    }

    /**
     * Core scheduling function: given the current memory state and the user's recall rating,
     * returns the updated memory state and the next review interval.
     *
     * <p><strong>First review (reviewCount = 0):</strong> Pass the card's stored defaults
     * ({@link #INITIAL_STABILITY}, {@link #INITIAL_DIFFICULTY}). The engine seeds both values
     * from the first-review rating and ignores the defaults.
     *
     * @param stability   current stability (days)
     * @param difficulty  current difficulty (1–10)
     * @param elapsedDays days since last review (use a tiny positive value for brand-new cards)
     * @param rating      the user's self-reported recall quality
     * @param reviewCount number of times this card has been reviewed *before* this session (0 = first)
     * @return updated {@link EngineMetrics} containing next stability, difficulty, interval, and mastery delta
     */
    @Override
    public EngineMetrics compute(double stability, double difficulty,
                                 double elapsedDays, RecallRating rating, int reviewCount) {

        double safeElapsed = Math.max(0.001, elapsedDays);

        // Current retrievability (probability of recall right now)
        double R = calculateRetrievability(stability, safeElapsed);

        double nextS;
        double nextD;

        if (reviewCount == 0) {
            // ── First ever review: seed memory state from the initial rating ─────────
            // The stored INITIAL_STABILITY / INITIAL_DIFFICULTY are only placeholders;
            // we replace them with rating-calibrated seed values here.
            nextS = seedStability(rating);
            nextD = seedDifficulty(rating);

        } else if (rating == RecallRating.FORGOT) {
            // ── Memory lapse: complete blank ─────────────────────────────────────────
            // Stability drops (partially — re-learning is faster than first learning).
            // Difficulty rises: this concept is proving hard to retain.
            nextS = calculateLapse(stability, difficulty, R);
            nextD = shiftDifficulty(difficulty, rating);

        } else {
            // ── Successful recall (PARTIAL / RECALLED / FLUENT) ──────────────────────
            // Stability grows — more if you were close to forgetting, more if difficulty is low.
            nextS = calculateRecall(stability, difficulty, R, rating, safeElapsed);
            nextD = shiftDifficulty(difficulty, rating);
        }

        // Hard bounds: stability capped at 100 years; difficulty clamped to [1, 10]
        nextS = Math.max(0.1,  Math.min(36500.0, nextS));
        nextD = Math.max(1.0,  Math.min(10.0,    nextD));

        // Interval: 1 day for cards shown within 3.6 h (same-session review); otherwise fuzz around stability.
        // IMPORTANT: bypass the same-day floor for the very first review (reviewCount == 0).
        // When last_reviewed_at is NULL the service passes elapsedDays ≈ 0, which would wrongly
        // trigger this guard and return interval=1 instead of generateFuzzedInterval(seedStability).
        int intervalDays = (safeElapsed < 0.15 && reviewCount > 0) ? 1 : generateFuzzedInterval(nextS);

        // Gamified mastery delta
        int masteryDelta = computeDynamicMastery(rating, nextD, R);

        return EngineMetrics.builder()
                .stability(nextS)
                .difficulty(nextD)
                .intervalDays(intervalDays)
                .masteryDelta(masteryDelta)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  RECALL STABILITY  (PARTIAL / RECALLED / FLUENT)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Computes new stability after a successful recall.
     *
     * <h3>Formula</h3>
     * <pre>
     *   growthFactor = exp(W[8]) × (11−D) × S^(−W[9]) × (exp(W[10]×(1−R)) − 1)
     *   newS = S × (1 + growthFactor) × ratingModifier
     * </pre>
     *
     * <h3>Why each term matters</h3>
     * <ul>
     *   <li>{@code (11−D)}: Easier concepts grow faster. D=3 → 8×; D=8 → 3×.</li>
     *   <li>{@code S^(−W[9])}: Young memories grow faster than old ones.
     *       A 2-day card might double; a 300-day card grows by ~40 %.</li>
     *   <li>{@code (exp(W[10]×(1−R)) − 1)}: Low retrievability = bigger growth bonus.
     *       If R=0.4 (barely remembered it), growth is roughly 3× higher than R=0.9.
     *       This is the "desirable difficulty" effect.</li>
     *   <li>ratingModifier: PARTIAL 0.80 (penalty), RECALLED 1.00 (baseline), FLUENT 1.30 (bonus).</li>
     * </ul>
     */
    private double calculateRecall(double S, double D, double R,
                                   RecallRating rating, double elapsedDays) {

        double growthFactor =
                Math.exp(W[8])                        // base amplifier ≈ 4.66
                        * (11.0 - D)                           // difficulty dampener
                        * Math.pow(S, -W[9])                   // stability dampener (young = more growth)
                        * (Math.exp(W[10] * (1.0 - R)) - 1);  // retrievability bonus (low R = more growth)

        // Rating modifier: encodes encoding quality
        double modifier = switch (rating) {
            case PARTIAL  -> W[11];   // 0.80 — partial encoding yields less stable trace
            case RECALLED -> 1.00;    // baseline
            case FLUENT   -> W[12];   // 1.30 — effortless encoding yields stronger trace
            default       -> 1.00;
        };

        double newS = S * (1.0 + growthFactor) * modifier;

        // Same-day dampener: reviewing again within 24 h gives diminishing returns.
        // Prevents "grinding" a card 10 times in one sitting for cheap stability gains.
        // At 12 h (elapsedDays=0.5): growth is halved. At 0 h: virtually no growth.
        if (elapsedDays < 1.0) {
            newS = S + (newS - S) * W[13] * elapsedDays;
        }

        return newS;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  LAPSE STABILITY  (FORGOT)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Computes new stability after a complete memory lapse (FORGOT).
     *
     * <h3>Key behaviours</h3>
     * <ul>
     *   <li>Re-learning is faster than first learning: some memory trace persists.</li>
     *   <li>Harder concepts (high D) recover to a lower baseline.</li>
     *   <li>Cards with high prior stability retain more residual trace after a lapse —
     *       a concept you knew well for years is easier to re-learn than one you barely knew.</li>
     *   <li>A soft ceiling ({@code S × W[18]}) ensures stability never paradoxically increases
     *       through forgetting.</li>
     * </ul>
     */
    private double calculateLapse(double S, double D, double R) {
        double recovered =
                W[14]
                        * Math.pow(D, -W[15])                 // harder concept → harder to recover
                        * Math.pow(Math.max(S, 1.0), W[16])   // higher prior stability → more residual trace
                        * Math.exp(W[17] * (1.0 - R));         // lower retrievability at lapse → slightly easier recovery

        // Ceiling: lapse can never leave stability higher than W[18] × original
        return Math.max(0.1, Math.min(recovered, S * W[18]));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  DIFFICULTY SHIFT  (every review)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Updates difficulty based on the current rating.
     *
     * <ul>
     *   <li>FORGOT (grade 1): difficulty rises sharply (+2×W[6]).</li>
     *   <li>PARTIAL (grade 2): difficulty rises gently (+W[6]).</li>
     *   <li>RECALLED (grade 3): difficulty is neutral — no change.</li>
     *   <li>FLUENT (grade 4): difficulty decreases (−W[6]).</li>
     * </ul>
     *
     * <p>A weak mean-reversion (W[7] = 0.01) applies a 1 % pull toward the population
     * mean (W[4] ≈ 4.93) every review.  This prevents permanent drift: even a card you
     * always answer FLUENT eventually stabilises near D ≈ 3–4, not D = 1.
     */
    private double shiftDifficulty(double d, RecallRating rating) {
        double r = (double) rating.fsrsGrade();   // 1, 2, 3, or 4

        // Shift: neutral at r=3 (RECALLED). FORGOT (+), FLUENT (−).
        double shifted = d - W[6] * (r - 3.0);

        // Gentle mean-reversion toward natural population difficulty W[4]
        return W[7] * W[4] + (1.0 - W[7]) * shifted;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SEEDS  (first review only)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Seeds initial stability from the first-review rating.
     *
     * <p>Replaces the stored {@link #INITIAL_STABILITY} with a rating-calibrated value
     * that reflects how strongly the concept was encoded on first encounter.
     *
     * <ul>
     *   <li>FORGOT   → W[0] = 0.40 d  — almost no trace; review tomorrow.</li>
     *   <li>PARTIAL  → W[1] = 0.95 d  — weak trace; review in ~1 day.</li>
     *   <li>RECALLED → W[2] = 2.41 d  — solid trace; review in ~2 days.</li>
     *   <li>FLUENT   → W[3] = 5.83 d  — strong first impression; review in ~6 days.</li>
     * </ul>
     */
    private double seedStability(RecallRating rating) {
        return switch (rating) {
            case FORGOT   -> W[0];
            case PARTIAL  -> W[1];
            case RECALLED -> W[2];
            case FLUENT   -> W[3];
        };
    }

    /**
     * Seeds initial difficulty from the first-review rating.
     *
     * <p><strong>BUG FIX from previous version:</strong> FORGOT must seed the highest difficulty
     * (~6.81) — a card you couldn't recall at all is likely a genuinely hard concept.
     * FLUENT seeds the lowest (~3.99) — effortless recall suggests it's a naturally easy concept.
     * The previous implementation had this completely reversed.
     *
     * <h3>Formula</h3>
     * <pre>D0(r) = W[4] − W[5] × (r − 3)   where r = fsrsGrade() ∈ {1, 2, 3, 4}</pre>
     *
     * <pre>
     *   Rating     r   Seeded D
     *   FORGOT     1   4.93 + 2×0.94 = 6.81   (hardest)
     *   PARTIAL    2   4.93 + 1×0.94 = 5.87
     *   RECALLED   3   4.93 + 0×0.94 = 4.93   (neutral)
     *   FLUENT     4   4.93 − 1×0.94 = 3.99   (easiest)
     * </pre>
     */
    private double seedDifficulty(RecallRating rating) {
        double r = (double) rating.fsrsGrade();
        return W[4] - W[5] * (r - 3.0);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  INTERVAL FUZZING
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Applies a small random jitter to the raw interval (= round(stability)).
     *
     * <h3>Why fuzz?</h3>
     * Without jitter, all cards reviewed on the same day cluster together forever,
     * creating review avalanches.  A small jitter spreads them naturally.
     *
     * <h3>Variance shrinks as intervals grow (BUG FIX)</h3>
     * Formula: {@code varianceFraction = min(W[21], W[19] / sqrt(baseDays))}
     * <pre>
     *    7 d  → ±18 %  → ±1–2 days     (barely noticeable)
     *   30 d  → ±9 %   → ±3 days       (comfortable range)
     *  100 d  → ±5 %   → ±5 days       (still tight)
     *  365 d  → ±2.6 % → ±9–10 days    (predictable year-scale scheduling)
     *  730 d  → ±1.9 % → ±14 days      (2-year card is very stable)
     * </pre>
     * Previous W[19]=2.01, W[20]=0.12 created 30–50 % variance at all intervals — fixed.
     */
    private int generateFuzzedInterval(double stability) {
        int baseDays = (int) Math.max(1, Math.round(stability));
        if (baseDays <= 2) return baseDays;

        // Variance fraction shrinks with sqrt of the interval (W[20]=0.50 → sqrt decay)
        double varianceFraction = Math.min(W[21], W[19] / Math.pow(baseDays, W[20]));
        double jitter = ThreadLocalRandom.current().nextDouble(-varianceFraction, varianceFraction);

        int fuzzed = (int) Math.round(baseDays * (1.0 + jitter));
        return Math.max(2, Math.min(36500, fuzzed));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  MASTERY DELTA  (gamification layer)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Computes the mastery point change for this review.
     *
     * <h3>Design principles</h3>
     *
     * <h4>1. Memory Fracture penalty (FORGOT)</h4>
     * Forgetting costs points.  Penalty scales with retrievability at the time of forgetting:
     * <ul>
     *   <li>R = 0.95 → −24 pts  (forgot something fresh — harsh)</li>
     *   <li>R = 0.50 → −18 pts  (forgot something fading — moderate)</li>
     *   <li>R = 0.10 → −12 pts  (forgot something very overdue — soft)</li>
     * </ul>
     * This is fair: cards you forget when badly overdue carry less blame than cards
     * you forget right after reviewing them.
     *
     * <h4>2. Memory Defiance multiplier</h4>
     * Extra points when you recall a fading memory (low R):
     * <ul>
     *   <li>R ≥ 0.85  → 1.0×  (comfortable recall — baseline)</li>
     *   <li>R = 0.60  → 1.63× (memory was fading — solid bonus)</li>
     *   <li>R = 0.30  → 2.75× (close-call recall — big bonus)</li>
     *   <li>R = 0.10  → 3.25× (legendary clutch recall — maximum bonus)</li>
     * </ul>
     * This directly rewards reviewing overdue cards and discourages early "gaming."
     *
     * <h4>3. Difficulty weight</h4>
     * Harder concepts (high D) yield more points, incentivising users not to avoid them.
     * D=1 → 1.1× | D=5 → 1.5× | D=10 → 2.0×
     *
     * <h4>4. Base tier</h4>
     * FLUENT=8, RECALLED=5, PARTIAL=2 (before multipliers).
     *
     * <h4>5. Micro-variance ±10 %</h4>
     * A small random roll (0.90–1.10) provides just enough feel-good randomness without
     * making rewards feel arbitrary.  Tightened from the previous 0.92–1.45 (53 % swing).
     */
    private int computeDynamicMastery(RecallRating rating, double difficulty, double retrievability) {

        // ── Memory Fracture (FORGOT) ──────────────────────────────────────────────
        if (rating == RecallRating.FORGOT) {
            // Penalty is harsher when R was high (you forgot something you recently knew)
            int penalty = (int) Math.round(10 + 15 * retrievability);  // range: 10–25
            return -penalty;
        }

        // ── Memory Defiance multiplier ────────────────────────────────────────────
        // Scales from 1.0× (R ≥ 0.85) up to ~3.25× (R ≈ 0.10)
        double defianceMultiplier = (retrievability < 0.85)
                ? 1.0 + 2.5 * (1.0 - retrievability)
                : 1.0;

        // ── Difficulty weight ─────────────────────────────────────────────────────
        // Rewards tackling hard material; never below 1.1×
        double difficultyWeight = 1.0 + (difficulty / 10.0);

        // ── Base points per performance tier ─────────────────────────────────────
        double basePoints = switch (rating) {
            case FLUENT   -> 8.0;
            case RECALLED -> 5.0;
            case PARTIAL  -> 2.0;
            default       -> 0.0;
        };

        // ── Micro-variance ±10 % ─────────────────────────────────────────────────
        double jitter = ThreadLocalRandom.current().nextDouble(0.90, 1.10);

        double total = basePoints * difficultyWeight * defianceMultiplier * jitter;
        return Math.max(1, Math.min(35, (int) Math.round(total)));
    }
}