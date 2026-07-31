package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class TMHMTutorCompatibilityRandomizer extends Randomizer {

    /**
     * Density used when this ROM's own vanilla compatibility cannot be trusted as a base - see
     * {@link #vanillaDensity}. Set near the Gen 4-7 measured mean rather than any one game's value.
     */
    public static double TMC_DENSITY_FALLBACK = 0.35;

    /**
     * Range a measured vanilla density has to fall in to be used as the base. Outside it the matrix
     * is not describing a normal game - it has already been set to full compatibility, or the ROM
     * carries no compatibility data at all - and {@link #TMC_DENSITY_FALLBACK} is used instead.
     */
    public static double TMC_DENSITY_BASE_MIN = 0.05;
    public static double TMC_DENSITY_BASE_MAX = 0.90;

    /**
     * Relative standard deviation of the per-species budget around what the ladder predicts.
     * <p>
     * This is what reproduces vanilla's <i>spread</i> rather than just its mean. Measured on Ultra
     * Sun, vanilla runs p10 24% / p90 42% of the TM pool; the unmodified model gives 29% / 43%, a
     * distribution far too tight at the bottom, with vanilla's whole 20-29% population missing.
     * Note the Bernoulli draw contributes binomial spread of its own on top of this, which is why
     * this is smaller than the gap alone would suggest - 0.12 overshot, widening p10 to 4-6pp below
     * vanilla's on every ROM.
     */
    public static double TMC_DENSITY_SIGMA = 0.08;

    /** Hard bounds on a species' density, so a tail roll can neither empty nor fill its list. */
    public static double TMC_DENSITY_MIN = 0.02;
    public static double TMC_DENSITY_MAX = 0.95;

    /**
     * Ends of the BST breadth ladder, as multiples of the pool's mean density.
     * <p>
     * Vanilla makes a powerful Pokemon broad rather than merely strong: TM count correlates with BST
     * at r=+0.555. Measured across this project's profile ROMs, the ladder in units of each game's
     * <i>own</i> mean density is 0.73 / 0.92 / 1.00 / 1.09 / 1.12 / 1.35 over the six BST buckets,
     * and gens 2-7 agree on those figures to about +-0.05 despite their absolute densities running
     * from 32% to 47%. The absolute breadth is per-game; this shape is not.
     */
    public static double TMC_BST_MULT_MIN = 0.72;
    public static double TMC_BST_MULT_MAX = 1.15;

    /** BST mapped onto 0..1 for the ladder above. Spans roughly Caterpie to a box legendary. */
    public static double TMC_BST_NORM_LOW = 250;
    public static double TMC_BST_NORM_SPAN = 350;

    /**
     * Extra breadth for a legendary, on top of whatever its BST already earns it.
     * <p>
     * Vanilla's BST ladder is not a straight line: it runs 0.73 / 0.91 / 0.98 / 1.08 / 1.11 of the
     * mean through the first five buckets and then jumps to 1.29 at 600+. A pure BST term cannot
     * produce that step - fitting the jump makes the 540-600 bucket overshoot, and fitting 540-600
     * flattens the top. The step is where the legendaries are (the report measures legendary 38.9
     * and mythical 42.2 against ordinary 30.7), so it is modelled as what it is rather than as a
     * steeper slope.
     */
    public static double TMC_LEGENDARY_MULT = 1.20;

    /**
     * How wide each breadth tier is, as a multiple of the pool's mean density, and how much of the
     * pool falls in each. Ordered universal, wide, mid, narrow, rare.
     * <p>
     * Vanilla compatibility is not smooth - it is tiered, and steeply so. Ultra Sun's 100 TMs split
     * 13 / 3 / 26 / 45 / 13 across "learnable by >=95% of the dex" down to "<10%", spanning 1.4% to
     * 97.7%. Every randomised TM instead lands in the mid tier, spanning only 23.8% to 61.0%: the
     * pool has no universal filler and no rarities, which is the single most visible structural
     * difference between a randomised game and a real one.
     * <p>
     * Expressed as multiples rather than absolute shares deliberately. The two marginals - how many
     * moves a species gets, and how many species a move reaches - are the same matrix seen from two
     * sides, so absolute breadths that suit Ultra Sun's 31.9% density would be wrong for Crystal's
     * 47.4%. The multipliers are weighted to average 1.0, so tiering cannot shift the density.
     */
    public static double[] TMC_TIER_MULTIPLIERS = {7.0, 2.4, 1.2, 0.5, 0.18};
    public static final double[] TMC_TIER_SHARES = {0.13, 0.03, 0.26, 0.45, 0.13};

    /**
     * How strongly Normal-typed and status moves are pulled toward the broad end of the tier list.
     * <p>
     * Vanilla's universal tier is not a random sample of the pool: 11 of Ultra Sun's 13 universal
     * TMs are Normal-typed and 8 are status, and the five damaging ones (Hidden Power, Return,
     * Frustration, Facade, Round) are all deliberately type-neutral. Breadth and type-neutrality are
     * the same design decision, so the tier roll is biased rather than uniform.
     */
    public static double TMC_UNIVERSAL_NORMAL_WEIGHT = 8.0;
    public static double TMC_UNIVERSAL_STATUS_WEIGHT = 2.5;

    /**
     * Broadest tier a Gym Leader's reward TM may be assigned (index into
     * {@link #TMC_TIER_MULTIPLIERS}; 2 = the mid tier).
     * <p>
     * {@code typeLockGymLeaderTMs} rewrites which move sits on a gym's TM, and it necessarily runs
     * <i>after</i> this randomizer - it depends on trainer randomization having assigned the gym its
     * type theme (GameRandomizer :301 against :290). Compatibility for that TM slot is therefore
     * already fixed by the time the move changes. That was harmless when every TM had the same
     * breadth, but a tiered model can hand a gym's reward TM the rare tier and leave it learnable by
     * ~3% of the dex. Gym TMs are floored at the mid tier so a badge reward is always something a
     * team can actually use.
     */
    public static int TMC_GYM_TM_MIN_TIER = 2;

    /**
     * Type lift for a damaging move matching one of the species' types, and the (weaker) lift for a
     * status move that does. Vanilla measures 2.9-3.4x for damaging against 1.8-2.0x for status on
     * the Gen 4-7 profile ROMs.
     */
    public static double TMC_STAB_MULT = 9.0;
    public static double TMC_STATUS_STAB_MULT = 6.0;

    /**
     * Flat weight for a Normal-typed move, applied regardless of the species' own types (C6).
     * Above the off-type baseline of 1.0 because Normal TMs really are the broad ones - vanilla's
     * mean Normal TM reaches 509 of 803 species against 63 for Dragon - but carrying no STAB lift.
     */
    public static double TMC_NORMAL_MULT = 2.5;

    /**
     * Extra breadth for status moves over damaging ones, independent of type (C5). The report
     * measures mean breadth 347.3 for status TMs against 206.1 for damaging, a ratio of 1.68x.
     */
    public static double TMC_STATUS_BREADTH_MULT = 1.25;

    /**
     * Lift for a move whose type the species already has an attacking move of, from its level-up
     * learnset (C4).
     * <p>
     * Vanilla keeps a species' coverage identity consistent across the two layers: if it was given a
     * Fire move by level-up despite not being Fire, it very likely also gets the Fire TM. The report
     * measures this as stronger than STAB for off-type moves.
     * <p>
     * Deliberately modest, because the aggregate statistic this is measured by turns out to be almost
     * entirely reproduced by the STAB and budget terms alone - a species with broad level-up coverage
     * is usually a high-BST species with a large budget, which lifts the ratio without any per-species
     * coherence at all. This term exists for the coherence rather than for the number: within one
     * species, the types it already attacks with should be the types it can be taught.
     */
    public static double TMC_LEVELUP_IDENTITY_MULT = 2.2;

    // ---- Move Tutor pool -----------------------------------------------------------------------
    // The tutor pool takes the same model, but not the same numbers. Everything about a species -
    // its BST ladder, its legendary bonus, the spread around it - carries over unchanged, and is
    // deliberately shared rather than duplicated: vanilla's tutor ladder is 0.73 / 0.87 / 0.98 /
    // 1.10 / 1.09 / 1.37 of the mean on Ultra Sun against the TM pool's 0.73 / 0.91 / 0.98 / 1.08 /
    // 1.11 / 1.29, which is the same shape. What differs is everything about the *pool*.

    /**
     * Tutor breadth tiers, same meaning as {@link #TMC_TIER_MULTIPLIERS} but far more skewed.
     * <p>
     * The tutor pool has essentially no universal tier - Ultra Sun's is one move (Snore, 98.0%), and
     * everything else is below 37% - and a very fat narrow tier: 1 / 0 / 10 / 45 / 11 of its 67
     * tutors, against the TM pool's much flatter 13 / 3 / 26 / 45 / 13 of 100. Tutors are where a
     * game parks its single-species content, and that has to survive randomization or the pool stops
     * being a tutor pool and becomes a second TM list.
     */
    public static double[] TUTORC_TIER_MULTIPLIERS = {14.0, 5.0, 2.0, 0.75, 0.15};
    public static final double[] TUTORC_TIER_SHARES = {0.02, 0.01, 0.14, 0.66, 0.17};

    /**
     * Smallest tutor roster that gets tiered at all. Below this the tiers are skipped and every move
     * shares one breadth.
     * <p>
     * Crystal ships three tutor moves. Slicing five tiers across three moves cannot reproduce a
     * distribution - it just decides which single move becomes the rare one, and on a three-move
     * roster that is a move the player has essentially lost. Vanilla puts all three at 26-34%, i.e.
     * no tiering at all, which is what this reproduces.
     */
    public static int TUTORC_MIN_TIERED_POOL = 10;

    /**
     * Tutor type lifts. <b>Status is the stronger one here, which is the reverse of the TM pool.</b>
     * <p>
     * Vanilla Ultra Sun measures tutor on/off-type at 3.70x for damaging moves and 5.36x for status
     * ones; the same inversion holds on Platinum (3.30x / 8.06x), Black 2 (3.66x / 4.71x) and Omega
     * Ruby (3.70x / 4.53x). The TM pool runs the other way round - 3.42x damaging against 1.93x
     * status - because its status half is dominated by the universal tier of teach-anyone utility
     * moves. Tutor status moves have no such tier: they are things like Heal Bell and Magic Coat,
     * handed to the specific families that thematically own them.
     */
    public static double TUTORC_STAB_MULT = 6.5;
    public static double TUTORC_STATUS_STAB_MULT = 21.0;

    /**
     * Breadth bonus for a Normal-typed tutor move. Unlike {@link #TMC_NORMAL_MULT} this does
     * <i>not</i> replace the type lift - a Normal-type species still gets STAB on it.
     * <p>
     * C6's "Normal is the null case" is a TM-pool fact, not a general one. It holds there because
     * eleven of the thirteen universal TMs are Normal-typed, so every species learns them and the
     * on/off-type ratio collapses to 1.00-1.06x. The tutor pool has no universal tier to flatten it,
     * and vanilla duly measures a real Normal-typed lift of 1.51-1.63x on the Gen 4-7 ROMs. Normal
     * tutors are still somewhat broader than average, which is what this carries.
     */
    public static double TUTORC_NORMAL_MULT = 1.2;

    /**
     * Extra breadth for status tutors over damaging ones. <b>Below 1.0</b> - again the reverse of
     * the TM pool, where status moves are the broad ones. Vanilla Ultra Sun reaches 10.4% of the dex
     * with an off-type status tutor against 15.8% with an off-type damaging one.
     */
    public static double TUTORC_STATUS_BREADTH_MULT = 0.8;

    /**
     * Tutor form of {@link #TMC_LEVELUP_IDENTITY_MULT}, and weaker. Vanilla's off-type level-up
     * identity lift is 2.11x on Ultra Sun tutors against 3.04x on its TMs.
     */
    public static double TUTORC_LEVELUP_IDENTITY_MULT = 1.15;

    /**
     * Multiplier on the chance of learning a move needed early on to avoid a softlock (the HM
     * equivalents). Unchanged from the pre-budget model, and deliberately applied <i>after</i> the
     * budget fit: the guarantee matters more than the budget being exact.
     */
    private static final double TMC_EARLY_REQUIRED_BOOST = 1.8;

    /** Bisection steps used to fit a species' scaling factor. 40 is far past the precision needed. */
    private static final int TMC_FIT_ITERATIONS = 40;

    private boolean tmhmChangesMade;
    private boolean tutorChangesMade;

    /**
     * The parts of the model that differ between the TM pool and the tutor pool. Read from the
     * mutable constants each time a model is built, not cached, so a swept {@code -D} override still
     * reaches it.
     * <p>
     * What is <i>not</i> here is as deliberate as what is: the density base, its spread, the BST
     * ladder and the legendary bonus are all shared, because they describe the species rather than
     * the pool and measurement says they have the same shape in both.
     *
     * @param normalIsNullCase whether a Normal-typed move gets {@code normalMult} <i>instead of</i>
     *                         any type lift (the TM pool, where Normal is universal filler) or
     *                         <i>as well as</i> it (the tutor pool, which has no such filler)
     * @param floorGymTMs      whether {@link #floorGymLeaderTMs} applies - TM pool only, since a gym
     *                         reward is always a TM
     */
    private record PoolTuning(double[] tierMultipliers, double[] tierShares, int minTieredPool,
                              double stabMult, double statusStabMult, double normalMult,
                              boolean normalIsNullCase, double statusBreadthMult,
                              double levelUpIdentityMult, boolean floorGymTMs) {

        static PoolTuning forTMs() {
            return new PoolTuning(TMC_TIER_MULTIPLIERS, TMC_TIER_SHARES, 0,
                    TMC_STAB_MULT, TMC_STATUS_STAB_MULT, TMC_NORMAL_MULT, true,
                    TMC_STATUS_BREADTH_MULT, TMC_LEVELUP_IDENTITY_MULT, true);
        }

        static PoolTuning forTutors() {
            return new PoolTuning(TUTORC_TIER_MULTIPLIERS, TUTORC_TIER_SHARES, TUTORC_MIN_TIERED_POOL,
                    TUTORC_STAB_MULT, TUTORC_STATUS_STAB_MULT, TUTORC_NORMAL_MULT, false,
                    TUTORC_STATUS_BREADTH_MULT, TUTORC_LEVELUP_IDENTITY_MULT, false);
        }
    }

    /**
     * Everything the budget model rolls once per pool, shared by every species in it. Bundled rather
     * than passed as four more parameters, since all of it has the same lifetime.
     *
     * @param budgets    how many moves of the pool each species should end up with (C1, C2)
     * @param breadths   per-move breadth multiplier, by index into the pool (C3)
     * @param levelUpTypes attacking types each species already has by level-up, by species number (C4)
     * @param tuning     which pool's constants this model was built with
     */
    private record BudgetModel(Map<Species, Integer> budgets, double[] breadths,
                               Map<Integer, Set<Type>> levelUpTypes, PoolTuning tuning) {
    }

    public TMHMTutorCompatibilityRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    /**
     * Returns whether TM/HM compatibility has been changed.
     */
    public boolean isTMHMChangesMade() {
        return tmhmChangesMade;
    }

    /**
     * Returns whether Move Tutor compatibility has been changed.
     */
    public boolean isTutorChangesMade() {
        return tutorChangesMade;
    }

    public void randomizeTMHMCompatibility() {
        boolean preferSameType = settings.getTmsHmsCompatibilityMod() == Settings.TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE;
        boolean followEvolutions = settings.isTmsFollowEvolutions();

        // Get current compatibility
        // increase HM chances if required early on
        List<Integer> requiredEarlyOn = romHandler.getEarlyRequiredHMMoves();
        Map<Species, boolean[]> compat = romHandler.getTMHMCompatibility();
        List<Integer> tmHMs = new ArrayList<>(romHandler.getTMMoves());
        tmHMs.addAll(romHandler.getHMMoves());

        // Read while `compat` still holds the ROM's own vanilla data - this is the first step in the
        // pipeline to touch it (GameRandomizer.maybeRandomizeTMHMCompatibility).
        int tmCount = romHandler.getTMCount();
        BudgetModel model = usesBudgetModel(preferSameType)
                ? buildModel(compat, tmHMs, tmCount, PoolTuning.forTMs()) : null;

        if (followEvolutions) {
            copyUpEvolutionsHelper.apply(true, false,
                    pk -> randomizePokemonMoveCompatibility(pk, compat.get(pk), tmHMs, requiredEarlyOn, preferSameType,
                            model, tmCount),
                    (evFrom, evTo, toMonIsFinalEvo) -> copyPokemonMoveCompatibilityUpEvolutions(evFrom, evTo,
                            compat.get(evFrom), compat.get(evTo), tmHMs, preferSameType, model, tmCount));
        } else {
            for (Map.Entry<Species, boolean[]> compatEntry : compat.entrySet()) {
                randomizePokemonMoveCompatibility(compatEntry.getKey(), compatEntry.getValue(), tmHMs, requiredEarlyOn,
                        preferSameType, model, tmCount);
            }
        }

        // Set the new compatibility
        romHandler.setTMHMCompatibility(compat);
        tmhmChangesMade = true;
    }

    /**
     * @param model         the per-pool budget model, or null to use the original per-pair coin flip
     * @param budgetedCount how many leading entries of {@code moveIDs} the budget covers. TMs are
     *                      budgeted; the HMs that follow them in the same array are not, since
     *                      vanilla HM compatibility is a different and much narrower thing (measured
     *                      around 17% on Red against 38% for its TMs) and folding the two together
     *                      drags the TM density below its target.
     */
    private void randomizePokemonMoveCompatibility(Species pkmn, boolean[] moveCompatibilityFlags,
                                                   List<Integer> moveIDs, List<Integer> prioritizedMoves,
                                                   boolean preferSameType, BudgetModel model,
                                                   int budgetedCount) {
        List<Move> moveData = romHandler.getMoves();
        Integer budget = model == null ? null : model.budgets().get(pkmn);
        int budgeted = budget == null ? 0 : Math.min(budgetedCount, moveIDs.size());
        if (budget != null) {
            randomizeToBudget(pkmn, moveCompatibilityFlags, moveIDs, prioritizedMoves, moveData, budget, budgeted,
                    model);
        }
        for (int i = budgeted + 1; i <= moveIDs.size(); i++) {
            int move = moveIDs.get(i - 1);
            Move mv = moveData.get(move);
            double probability = getMoveCompatibilityProbability(
                    pkmn,
                    mv,
                    prioritizedMoves.contains(move),
                    preferSameType
            );
            moveCompatibilityFlags[i] = (this.random.nextDouble() < probability);
        }
    }

    /**
     * Fills one species' compatibility flags so that it ends up with roughly {@code budget} moves,
     * distributed according to {@link #pairWeight}.
     * <p>
     * The two things a compatibility model has to get right pull against each other: how many moves
     * a species gets, and how many species a move reaches. Independent per-pair coin flips can only
     * target the first. Here the relative weights set the shape and a single per-species scaling
     * factor sets the level, so a species' expected count lands on its budget whatever its weights
     * happen to look like - which is also what stops a species with few on-type moves from quietly
     * getting a shorter list than one with many.
     */
    private void randomizeToBudget(Species pkmn, boolean[] moveCompatibilityFlags, List<Integer> moveIDs,
                                   List<Integer> prioritizedMoves, List<Move> moveData, int budget,
                                   int budgetedCount, BudgetModel model) {
        Set<Type> knownTypes = model.levelUpTypes().getOrDefault(pkmn.getNumber(), Set.of());
        double[] weights = new double[budgetedCount];
        for (int i = 0; i < budgetedCount; i++) {
            weights[i] = model.breadths()[i] * pairWeight(pkmn, moveData.get(moveIDs.get(i)), knownTypes, model.tuning());
        }
        double scale = fitScale(weights, budget);
        for (int i = 0; i < budgetedCount; i++) {
            double probability = Math.min(1.0, scale * weights[i]);
            if (prioritizedMoves.contains(moveIDs.get(i))) {
                // Softlock protection outranks the budget: an unreachable HM move is a broken save,
                // a species with one TM too many is not.
                probability = Math.min(1.0, probability * TMC_EARLY_REQUIRED_BOOST);
            }
            moveCompatibilityFlags[i + 1] = this.random.nextDouble() < probability;
        }
    }

    /**
     * The "Follow Evolutions" form of {@link #randomizeToBudget}: the evolution keeps everything its
     * pre-evolution could learn, and then draws whatever its own budget has left over on top.
     * <p>
     * The point of the option is that evolving never takes a move away, so the inherited flags are
     * fixed and only the shortfall is rolled. That makes the budget a target for the <i>line</i>
     * rather than for the species: an evolution whose pre-evolution already met or passed its budget
     * simply gains nothing, which is why the gain is floored at zero rather than allowed to go
     * negative and start removing moves. Vanilla's own ladder is gentle enough for this to be the
     * common case - 30.3 / 31.6 / 35.9 TMs of 100 across the three evolution stages - so most of the
     * ladder is inherited, not re-earned.
     * <p>
     * Weights are the same as for a species drawn from scratch, restricted to the moves the
     * pre-evolution could not learn. The old model instead gave every unlearned move a flat 10%
     * (90% for a type new to this evolution), which ignores the budget entirely and compounds on
     * every step of a three-stage line.
     */
    private void topUpToBudget(Species pkmn, boolean[] fromCompatibilityFlags, boolean[] toCompatibilityFlags,
                               List<Integer> moveIDs, List<Move> moveData, int budget, int budgetedCount,
                               BudgetModel model) {
        Set<Type> knownTypes = model.levelUpTypes().getOrDefault(pkmn.getNumber(), Set.of());
        double[] weights = new double[budgetedCount];
        int inherited = 0;
        for (int i = 0; i < budgetedCount; i++) {
            toCompatibilityFlags[i + 1] = fromCompatibilityFlags[i + 1];
            if (fromCompatibilityFlags[i + 1]) {
                inherited++;
                // Already learnable, so not a candidate - a zero weight also keeps it out of the fit,
                // which would otherwise spend part of the budget on moves it cannot gain.
                weights[i] = 0;
            } else {
                weights[i] = model.breadths()[i] * pairWeight(pkmn, moveData.get(moveIDs.get(i)), knownTypes, model.tuning());
            }
        }

        int gain = budget - inherited;
        if (gain <= 0) {
            return;
        }
        double scale = fitScale(weights, gain);
        for (int i = 0; i < budgetedCount; i++) {
            if (weights[i] > 0 && this.random.nextDouble() < Math.min(1.0, scale * weights[i])) {
                toCompatibilityFlags[i + 1] = true;
            }
        }
    }

    /**
     * Relative likelihood of this species learning this move, before the per-species scaling that
     * turns it into a probability. Only ratios matter here, not the absolute level.
     */
    private double pairWeight(Species pkmn, Move mv, Set<Type> levelUpAttackingTypes, PoolTuning tuning) {
        boolean status = mv.category == MoveCategory.STATUS;
        double weight = status ? tuning.statusBreadthMult() : 1.0;

        boolean normal = mv.type != null && mv.type.equals(Type.NORMAL);
        if (normal) {
            weight *= tuning.normalMult();
            if (tuning.normalIsNullCase()) {
                // C6, TM pool only. Normal is the null case there: vanilla learns Normal-typed TMs
                // at 64.7% on-type against 63.1% off-type, a lift of 1.03x, and the same 1.00-1.06x
                // holds on all seven profile ROMs. Normal TMs are universal filler carrying no type
                // identity, so a Normal-type species gets no advantage on them - it just gets the
                // broad base every species gets. The old model tested the on-type branch first and
                // so handed Normal types 0.9 against 0.5, producing a 1.79x lift on every ROM.
                // The tutor pool has no universal filler and does show a real 1.5-1.6x Normal lift,
                // so it falls through to the ordinary type handling below.
                return weight;
            }
        }
        boolean onType = pkmn.getPrimaryType(false).equals(mv.type)
                || (pkmn.getSecondaryType(false) != null && pkmn.getSecondaryType(false).equals(mv.type));
        if (onType) {
            // C5. STAB is not one rule: vanilla's on/off-type lift is 2.9-3.4x for damaging TMs but
            // only 1.8-2.0x for status ones - type matching is about weapons, not utility. The old
            // model applied a single lift to both, over-typing the status half. The tutor pool
            // inverts this (3.7x damaging against 5.4x status), which is why the two multipliers are
            // per-pool rather than one pair of constants.
            weight *= status ? tuning.statusStabMult() : tuning.stabMult();
        } else if (levelUpAttackingTypes.contains(mv.type)) {
            // C4, and deliberately only for off-type moves. A species nearly always has an attacking
            // move of its own type by level-up, so applying this on top of STAB compounds the two and
            // inflates the STAB lift - measured at 4.00x against vanilla's 3.42x on Ultra Sun when it
            // was applied to both. Off-type is also where the report frames the effect as living: it
            // is what makes a species' *coverage* identity consistent across the two layers.
            weight *= tuning.levelUpIdentityMult();
        }
        return weight;
    }

    /**
     * The attacking types each species already has from its level-up learnset, for C4.
     * <p>
     * Read at the point compatibility runs, which is after the level-up learnsets have themselves
     * been randomized (GameRandomizer :286 against :290), so this reflects the moveset the player
     * will actually see rather than the ROM's original one.
     */
    private Map<Integer, Set<Type>> levelUpAttackingTypes(List<Move> moveData) {
        Map<Integer, Set<Type>> out = new HashMap<>();
        for (Map.Entry<Integer, List<MoveLearnt>> entry : romHandler.getMovesLearnt().entrySet()) {
            Set<Type> types = new HashSet<>();
            for (MoveLearnt ml : entry.getValue()) {
                Move mv = moveData.get(ml.move);
                if (mv != null && mv.category != MoveCategory.STATUS && mv.type != null) {
                    types.add(mv.type);
                }
            }
            out.put(entry.getKey(), types);
        }
        return out;
    }

    /**
     * Finds the scaling factor {@code s} for which {@code sum of min(1, s * weight)} equals
     * {@code budget}. That sum rises monotonically with {@code s}, so a plain bisection converges;
     * the cap at 1 is why it cannot be solved directly.
     *
     * @return the fitted factor, or {@link Double#POSITIVE_INFINITY} when the budget is at or beyond
     *         the number of moves with any weight at all, i.e. every one of them should be learnable.
     */
    private double fitScale(double[] weights, int budget) {
        int usable = 0;
        for (double w : weights) {
            if (w > 0) {
                usable++;
            }
        }
        if (budget >= usable) {
            return Double.POSITIVE_INFINITY;
        }
        double low = 0;
        double high = 1;
        while (cappedSum(weights, high) < budget && high < 1e9) {
            high *= 2;
        }
        for (int i = 0; i < TMC_FIT_ITERATIONS; i++) {
            double mid = 0.5 * (low + high);
            if (cappedSum(weights, mid) < budget) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return 0.5 * (low + high);
    }

    private double cappedSum(double[] weights, double scale) {
        double sum = 0;
        for (double w : weights) {
            sum += Math.min(1.0, scale * w);
        }
        return sum;
    }

    /**
     * Whether the per-species budget model applies. Type-blind randomization is left on the original
     * flat coin flip: someone who picked plain Random asked for no structure, and the budget model's
     * whole output is structure.
     */
    private boolean usesBudgetModel(boolean preferSameType) {
        return preferSameType;
    }

    /**
     * How many moves each species should end up able to learn.
     * <p>
     * The base is <b>this ROM's own vanilla density</b>, not a fixed figure. Vanilla density is not a
     * constant across the series - measured at 47.4% of the pool on Crystal, 41.3% on Emerald, 36.4%
     * on Platinum and 31.9% on Ultra Sun, because the TM <i>count</i> a species gets stays near 20-32
     * while the pool grows from 50 moves to 100. Targeting any single one of those figures would move
     * every other game away from its own design.
     *
     * @param compat   the compatibility matrix, still holding vanilla data
     * @param poolSize how many of the matrix's leading entries the budget covers - the TM count, or
     *                 the tutor count. HMs are excluded; see
     *                 {@link #randomizePokemonMoveCompatibility}.
     */
    /** Rolls everything the budget model needs for one pool. */
    private BudgetModel buildModel(Map<Species, boolean[]> compat, List<Integer> moveIDs, int poolSize,
                                   PoolTuning tuning) {
        List<Move> moveData = romHandler.getMoves();
        return new BudgetModel(computeBudgets(compat, poolSize),
                rollBreadths(moveIDs, moveData, poolSize, tuning),
                levelUpAttackingTypes(moveData), tuning);
    }

    private Map<Species, Integer> computeBudgets(Map<Species, boolean[]> compat, int poolSize) {
        double density = vanillaDensity(compat, poolSize);

        // Ladder multipliers first, so they can be renormalised to leave the mean density alone.
        // Without this the ladder's shape and the pool's overall density could not be tuned apart.
        Map<Species, Double> ladder = new LinkedHashMap<>();
        double total = 0;
        for (Map.Entry<Species, boolean[]> entry : compat.entrySet()) {
            Species pkmn = entry.getKey();
            if (pkmn.isEssentiallyCosmetic()) {
                continue; // copied wholesale from the base forme afterwards; counting it double-weights it
            }
            // No separate stand-alone term: vanilla's +30% for a never-evolving species turns out to
            // be an artefact of those species skewing high-BST, and the ladder alone reproduces it
            // (measured 1.45x against vanilla's 1.31x on Ultra Sun with no stand-alone term at all).
            double multiplier = bstMultiplier(pkmn) * (pkmn.isLegendary() ? TMC_LEGENDARY_MULT : 1.0);
            ladder.put(pkmn, multiplier);
            total += multiplier;
        }
        double meanMultiplier = ladder.isEmpty() ? 1.0 : total / ladder.size();

        Map<Species, Integer> budgets = new LinkedHashMap<>();
        for (Map.Entry<Species, Double> entry : ladder.entrySet()) {
            double share = density * (entry.getValue() / meanMultiplier)
                    * (1 + this.random.nextGaussian() * TMC_DENSITY_SIGMA);
            share = Math.clamp(share, TMC_DENSITY_MIN, TMC_DENSITY_MAX);
            budgets.put(entry.getKey(), (int) Math.round(share * poolSize));
        }
        return budgets;
    }

    /**
     * Assigns each move in the pool a breadth tier, once per run, shared by every species.
     * <p>
     * Moves are put into a weighted random order and then sliced by {@link #TMC_TIER_SHARES}, so the
     * tier sizes are exact while which move lands where still changes seed to seed. Weighting uses
     * the standard {@code U^(1/w)} key, which draws a random order with probability proportional to
     * the weights.
     *
     * @param count how many leading entries of {@code moveIDs} to tier (TMs, excluding HMs)
     */
    private double[] rollBreadths(List<Integer> moveIDs, List<Move> moveData, int count, PoolTuning tuning) {
        double[] tierShares = tuning.tierShares();
        double[] tierMultipliers = tuning.tierMultipliers();
        if (count < tuning.minTieredPool()) {
            // Too few moves to describe a distribution with - see TUTORC_MIN_TIERED_POOL. One flat
            // breadth, which still leaves the budget and the type weights doing their work.
            double[] flat = new double[count];
            Arrays.fill(flat, 1.0);
            return flat;
        }

        Integer[] order = new Integer[count];
        double[] key = new double[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
            Move mv = moveData.get(moveIDs.get(i));
            double weight = 1.0;
            if (mv.type != null && mv.type.equals(Type.NORMAL)) {
                weight *= TMC_UNIVERSAL_NORMAL_WEIGHT;
            }
            if (mv.category == MoveCategory.STATUS) {
                weight *= TMC_UNIVERSAL_STATUS_WEIGHT;
            }
            key[i] = Math.pow(this.random.nextDouble(), 1.0 / weight);
        }
        Arrays.sort(order, (a, b) -> Double.compare(key[b], key[a]));

        double[] breadths = new double[count];
        int assigned = 0;
        for (int tier = 0; tier < tierShares.length; tier++) {
            // The last tier takes whatever is left, so rounding cannot drop or duplicate a move.
            int size = tier == tierShares.length - 1
                    ? count - assigned
                    : Math.min((int) Math.round(tierShares[tier] * count), count - assigned);
            for (int i = 0; i < size; i++) {
                breadths[order[assigned + i]] = tierMultipliers[tier];
            }
            assigned += size;
        }
        if (tuning.floorGymTMs()) {
            floorGymLeaderTMs(breadths, count, tierMultipliers);
        }
        return breadths;
    }

    /**
     * Lifts any Gym Leader reward TM out of the narrow and rare tiers, by swapping its breadth with
     * a randomly chosen non-gym TM that is already at {@link #TMC_GYM_TM_MIN_TIER} or broader.
     * <p>
     * A swap rather than a reassignment, so the tier sizes stay exactly as rolled - the tier counts
     * are the thing C3 is measured on, and quietly inflating the mid tier to protect eight slots
     * would trade one visible error for another.
     */
    private void floorGymLeaderTMs(double[] breadths, int count, double[] tierMultipliers) {
        Map<String, Integer> gymLeaderTMs = romHandler.getGymLeaderTMs();
        if (gymLeaderTMs.isEmpty()) {
            return;
        }
        double minimum = tierMultipliers[Math.min(TMC_GYM_TM_MIN_TIER, tierMultipliers.length - 1)];

        List<Integer> gymIndices = new ArrayList<>();
        for (int tmNumber : gymLeaderTMs.values()) {
            int index = tmNumber - 1;
            if (index >= 0 && index < count && breadths[index] < minimum) {
                gymIndices.add(index);
            }
        }
        if (gymIndices.isEmpty()) {
            return;
        }

        List<Integer> donors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (breadths[i] >= minimum && !gymLeaderTMs.containsValue(i + 1)) {
                donors.add(i);
            }
        }
        Collections.shuffle(donors, this.random);

        for (int i = 0; i < gymIndices.size() && i < donors.size(); i++) {
            int gym = gymIndices.get(i);
            int donor = donors.get(i);
            double swap = breadths[gym];
            breadths[gym] = breadths[donor];
            breadths[donor] = swap;
        }
    }

    /** Mean share of the pool a species can learn, as the ROM itself ships it. */
    private double vanillaDensity(Map<Species, boolean[]> compat, int poolSize) {
        long learnable = 0;
        long pairs = 0;
        for (Map.Entry<Species, boolean[]> entry : compat.entrySet()) {
            if (entry.getKey().isEssentiallyCosmetic()) {
                continue;
            }
            boolean[] flags = entry.getValue();
            for (int i = 1; i <= poolSize && i < flags.length; i++) {
                if (flags[i]) {
                    learnable++;
                }
                pairs++;
            }
        }
        double density = pairs == 0 ? 0 : (double) learnable / pairs;
        if (density < TMC_DENSITY_BASE_MIN || density > TMC_DENSITY_BASE_MAX) {
            // Not a normally-populated matrix - already set to full compatibility, or no data.
            return TMC_DENSITY_FALLBACK;
        }
        return density;
    }

    private double bstMultiplier(Species pkmn) {
        double normalised = Math.clamp(
                (pkmn.getBSTForPowerLevels() - TMC_BST_NORM_LOW) / TMC_BST_NORM_SPAN, 0, 1);
        return TMC_BST_MULT_MIN + (TMC_BST_MULT_MAX - TMC_BST_MULT_MIN) * normalised;
    }


    /**
     * Carries a pre-evolution's compatibility onto its evolution, then adds however much more the
     * evolution's own budget calls for.
     *
     * @param model         the per-pool budget model, or null to use the original flat per-move gain
     * @param budgetedCount how many leading entries of {@code moveIDs} the budget covers, as in
     *                      {@link #randomizePokemonMoveCompatibility}
     */
    private void copyPokemonMoveCompatibilityUpEvolutions(Species evFrom, Species evTo, boolean[] prevCompatibilityFlags,
                                                          boolean[] toCompatibilityFlags, List<Integer> moveIDs,
                                                          boolean preferSameType, BudgetModel model,
                                                          int budgetedCount) {
        List<Move> moveData = romHandler.getMoves();
        Integer budget = model == null ? null : model.budgets().get(evTo);
        int budgeted = budget == null ? 0 : Math.min(budgetedCount, moveIDs.size());
        if (budget != null) {
            topUpToBudget(evTo, prevCompatibilityFlags, toCompatibilityFlags, moveIDs, moveData, budget, budgeted,
                    model);
        }
        for (int i = budgeted + 1; i <= moveIDs.size(); i++) {
            if (!prevCompatibilityFlags[i]) {
                // Slight chance to gain TM/HM compatibility for a move if not learned by an earlier evolution step
                // Without prefer same type: 25% chance
                // With prefer same type:    10% chance, 90% chance for a type new to this evolution
                int move = moveIDs.get(i - 1);
                Move mv = moveData.get(move);
                double probability = 0.25;
                if (preferSameType) {
                    probability = 0.1;
                    if (evTo.getPrimaryType(false).equals(mv.type)
                            && !evTo.getPrimaryType(false).equals(evFrom.getPrimaryType(false)) && !evTo.getPrimaryType(false).equals(evFrom.getSecondaryType(false))
                            || evTo.getSecondaryType(false) != null && evTo.getSecondaryType(false).equals(mv.type)
                            && !evTo.getSecondaryType(false).equals(evFrom.getSecondaryType(false)) && !evTo.getSecondaryType(false).equals(evFrom.getPrimaryType(false))) {
                        probability = 0.9;
                    }
                }
                toCompatibilityFlags[i] = (this.random.nextDouble() < probability);
            }
            else {
                toCompatibilityFlags[i] = prevCompatibilityFlags[i];
            }
        }
    }

    private double getMoveCompatibilityProbability(Species pkmn, Move mv, boolean requiredEarlyOn,
                                                   boolean preferSameType) {
        double probability = 0.5;
        if (preferSameType) {
            if (pkmn.getPrimaryType(false).equals(mv.type)
                    || (pkmn.getSecondaryType(false) != null && pkmn.getSecondaryType(false).equals(mv.type))) {
                probability = 0.9;
            } else if (mv.type != null && mv.type.equals(Type.NORMAL)) {
                probability = 0.5;
            } else {
                probability = 0.25;
            }
        }
        if (requiredEarlyOn) {
            probability = Math.min(1.0, probability * 1.8);
        }
        return probability;
    }

    public void fullTMHMCompatibility() {
        Map<Species, boolean[]> compat = romHandler.getTMHMCompatibility();
        for (Map.Entry<Species, boolean[]> compatEntry : compat.entrySet()) {
            boolean[] flags = compatEntry.getValue();
            for (int i = 1; i < flags.length; i++) {
                flags[i] = true;
            }
        }
        romHandler.setTMHMCompatibility(compat);
    }

    /**
     * if a pokemon learns a move in its moveset and there is a TM of that move, make sure that TM can be learned.
     */
    public void ensureTMCompatSanity() {
        //
        Map<Species, boolean[]> compat = romHandler.getTMHMCompatibility();
        Map<Integer, List<MoveLearnt>> movesets = romHandler.getMovesLearnt();
        List<Integer> tmMoves = romHandler.getTMMoves();
        for (Species pkmn : compat.keySet()) {
            List<MoveLearnt> moveset = movesets.get(pkmn.getNumber());
            boolean[] pkmnCompat = compat.get(pkmn);
            for (MoveLearnt ml : moveset) {
                if (tmMoves.contains(ml.move)) {
                    int tmIndex = tmMoves.indexOf(ml.move);
                    pkmnCompat[tmIndex + 1] = true;
                }
            }
        }
        romHandler.setTMHMCompatibility(compat);
        tmhmChangesMade = true;
    }

    public void ensureTMEvolutionSanity() {
        Map<Species, boolean[]> compat = romHandler.getTMHMCompatibility();
        // Don't do anything with the base, just copy upwards to ensure later evolutions
        // retain learn compatibility
        copyUpEvolutionsHelper.apply(true, true, pk -> {},
                (evFrom, evTo, toMonIsFinalEvo) -> {
                    boolean[] fromCompat = compat.get(evFrom);
                    boolean[] toCompat = compat.get(evTo);
                    for (int i = 1; i < toCompat.length; i++) {
                        toCompat[i] |= fromCompat[i];
                    }
                });
        romHandler.setTMHMCompatibility(compat);
        tmhmChangesMade = true;
    }

    public void fullHMCompatibility() {
        Map<Species, boolean[]> compat = romHandler.getTMHMCompatibility();
        int tmCount = romHandler.getTMCount();
        for (boolean[] flags : compat.values()) {
            for (int i = tmCount + 1; i < flags.length; i++) {
                flags[i] = true;
            }
        }

        // Set the new compatibility
        romHandler.setTMHMCompatibility(compat);
        tmhmChangesMade = true;
    }

    public void copyTMCompatibilityToCosmeticFormes() {
        Map<Species, boolean[]> compat = romHandler.getTMHMCompatibility();

        for (Map.Entry<Species, boolean[]> compatEntry : compat.entrySet()) {
            Species pkmn = compatEntry.getKey();
            boolean[] flags = compatEntry.getValue();
            if (pkmn.isEssentiallyCosmetic()) {
                boolean[] baseFlags = compat.get(pkmn.getConceptualBaseForme());
                for (int i = 1; i < flags.length; i++) {
                    flags[i] = baseFlags[i];
                }
            }
        }

        romHandler.setTMHMCompatibility(compat);
        tmhmChangesMade = true;
    }

    public void randomizeMoveTutorCompatibility() {
        boolean preferSameType = settings.getMoveTutorsCompatibilityMod() == Settings.MoveTutorsCompatibilityMod.RANDOM_PREFER_TYPE;
        boolean followEvolutions = settings.isTutorFollowEvolutions();

        if (!romHandler.hasMoveTutors()) {
            return;
        }
        // Get current compatibility
        Map<Species, boolean[]> compat = romHandler.getMoveTutorCompatibility();
        List<Integer> mts = romHandler.getMoveTutorMoves();

        // Empty list
        List<Integer> priorityTutors = new ArrayList<>();

        // Read while `compat` still holds the ROM's own vanilla tutor data, as on the TM side. The
        // whole roster is budgeted - there is no HM-equivalent tail to leave out - and the per-ROM
        // base is what makes one model fit both a 3-move Crystal roster at 29.2% density and a
        // 67-move Ultra Sun one at 18.0%.
        int tutorCount = mts.size();
        BudgetModel model = usesBudgetModel(preferSameType)
                ? buildModel(compat, mts, tutorCount, PoolTuning.forTutors()) : null;

        if (followEvolutions) {
            copyUpEvolutionsHelper.apply(true, true,
                    pk -> randomizePokemonMoveCompatibility(pk, compat.get(pk), mts, priorityTutors, preferSameType,
                            model, tutorCount),
                    (evFrom, evTo, toMonIsFinalEvo) -> copyPokemonMoveCompatibilityUpEvolutions(evFrom, evTo,
                            compat.get(evFrom), compat.get(evTo), mts, preferSameType, model, tutorCount));
        }
        else {
            for (Map.Entry<Species, boolean[]> compatEntry : compat.entrySet()) {
                randomizePokemonMoveCompatibility(compatEntry.getKey(), compatEntry.getValue(), mts, priorityTutors,
                        preferSameType, model, tutorCount);
            }
        }

        // Set the new compatibility
        romHandler.setMoveTutorCompatibility(compat);
        tutorChangesMade = true;
    }

    public void fullMoveTutorCompatibility() {
        if (!romHandler.hasMoveTutors()) {
            return;
        }
        Map<Species, boolean[]> compat = romHandler.getMoveTutorCompatibility();
        for (Map.Entry<Species, boolean[]> compatEntry : compat.entrySet()) {
            boolean[] flags = compatEntry.getValue();
            for (int i = 1; i < flags.length; i++) {
                flags[i] = true;
            }
        }
        romHandler.setMoveTutorCompatibility(compat);
        tutorChangesMade = true;
    }

    public void ensureMoveTutorCompatSanity() {
        if (!romHandler.hasMoveTutors()) {
            return;
        }
        // if a pokemon learns a move in its moveset
        // and there is a tutor of that move, make sure
        // that tutor can be learned.
        Map<Species, boolean[]> compat = romHandler.getMoveTutorCompatibility();
        Map<Integer, List<MoveLearnt>> movesets = romHandler.getMovesLearnt();
        List<Integer> mtMoves = romHandler.getMoveTutorMoves();
        for (Species pkmn : compat.keySet()) {
            List<MoveLearnt> moveset = movesets.get(pkmn.getNumber());
            boolean[] pkmnCompat = compat.get(pkmn);
            for (MoveLearnt ml : moveset) {
                if (mtMoves.contains(ml.move)) {
                    int mtIndex = mtMoves.indexOf(ml.move);
                    pkmnCompat[mtIndex + 1] = true;
                }
            }
        }
        romHandler.setMoveTutorCompatibility(compat);
        tutorChangesMade = true;
    }

    public void ensureMoveTutorEvolutionSanity() {
        if (!romHandler.hasMoveTutors()) {
            return;
        }
        Map<Species, boolean[]> compat = romHandler.getMoveTutorCompatibility();
        // Don't do anything with the base, just copy upwards to ensure later evolutions retain learn compatibility
        copyUpEvolutionsHelper.apply(true, true, pk -> {},
                (evFrom, evTo, toMonIsFinalEvo) -> {
                    boolean[] fromCompat = compat.get(evFrom);
                    boolean[] toCompat = compat.get(evTo);
                    for (int i = 1; i < toCompat.length; i++) {
                        toCompat[i] |= fromCompat[i];
                    }
                });
        romHandler.setMoveTutorCompatibility(compat);
        tutorChangesMade = true;
    }

    public void copyMoveTutorCompatibilityToCosmeticFormes() {
        Map<Species, boolean[]> compat = romHandler.getMoveTutorCompatibility();

        for (Map.Entry<Species, boolean[]> compatEntry : compat.entrySet()) {
            Species pkmn = compatEntry.getKey();
            boolean[] flags = compatEntry.getValue();
            if (pkmn.isEssentiallyCosmetic()) {
                boolean[] baseFlags = compat.get(pkmn.getConceptualBaseForme());
                for (int i = 1; i < flags.length; i++) {
                    flags[i] = baseFlags[i];
                }
            }
        }

        romHandler.setMoveTutorCompatibility(compat);
        tutorChangesMade = true;
    }
}
