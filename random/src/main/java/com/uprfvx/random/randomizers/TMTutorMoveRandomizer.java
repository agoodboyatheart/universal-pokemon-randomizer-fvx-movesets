package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.*;

/**
 * A randomizer for which moves are found in TMs and at Move Tutors.
 * For compatibility with Pokemon, see {@link TMHMTutorCompatibilityRandomizer}.
 */
public class TMTutorMoveRandomizer extends Randomizer {

    /**
     * Share of the TM pool that should be damaging rather than status moves.
     * <p>
     * Vanilla holds this remarkably steady: Gen 5-7 all land on 66-67% to the percentage point
     * (USUM 67.0, ORAS 67.0, B2W2 66.3), and everything since Gen 2 sits in a 62-67% band. A plain
     * uniform draw instead inherits whatever the ROM's own move roster happens to be - measured at
     * 64% on Ultra Sun, and the gap is wider on earlier generations.
     * <p>
     * Deliberately this class's own constant rather than a shared one on {@link Randomizer}: giving
     * each subsystem its own constants is what keeps the trainer, species and TM branches from
     * colliding on a duplicate definition when they are merged into the combined build.
     */
    public static double TM_DAMAGING_SHARE = 0.67;

    /**
     * Share of the Move Tutor pool that should be damaging. Measured independently of the TM pool
     * and lands in the same place - USUM 66.7%, ORAS 68.6% - even though the two pools never share
     * a single move in any game ever shipped.
     */
    public static double TUTOR_DAMAGING_SHARE = 0.667;

    /**
     * Exponent on {@code (accuracy / 100)} when weighting a damaging TM/tutor pick.
     * <p>
     * Accuracy, not power, is what actually distinguishes the vanilla TM pool from the moves it left
     * behind: the pool runs 96.6 mean accuracy with only 7.7% of moves below 90%, against 94.8 and
     * 15.4% for the rest of the roster. A uniform draw inherits the roster's rate - measured at 14.3%
     * on Ultra Sun, roughly double vanilla's 7.5%.
     * <p>
     * This shapes a global roster nobody is obliged to teach from, and does not rank moves against a
     * curated quality list, so it stays on the composition side of the standing hands-off-on-quality
     * decision for player-facing pools.
     * <p>
     * Calibrated against vanilla Ultra Sun: exponent 3.0 overshot to 4.6% sub-90 (a pool more
     * reliable than vanilla's own), 1.7 lands on 7.4% against vanilla's 7.5%.
     */
    public static double TM_ACCURACY_EXPONENT = 1.7;

    /**
     * Centre of the base-power band a damaging TM/tutor pick is pulled toward. Vanilla's TM pool is a
     * mid-power workhorse catalogue: mean 81.4, median 80, p25 60, p75 90, with 41 of 62 damaging TMs
     * between 60 and 99.
     */
    public static double TM_POWER_CENTER = 80;

    /**
     * Width of that band <b>below</b> the centre. Narrower than the upper half: vanilla's damaging TM
     * distribution is asymmetric, with p25 at 60 (20 BP below the median) but a tail running all the
     * way to 250.
     */
    public static double TM_POWER_SIGMA_LOW = 28;

    /**
     * Width of the band <b>above</b> the centre. A symmetric curve was tried first and rejected by
     * measurement: at sigma 30-35 it reproduced the 60-99 band share almost exactly (65.8% against
     * vanilla's 66.1%) while starving the high tail - 0.6 moves per run in the 140-159 band against
     * vanilla's 2.0, and 0.1 at 240-259 against vanilla's 1.0. Those three moves are the whole of
     * vanilla's mean-over-median gap, and losing them held mean BP at 75.8 against vanilla's 81.4.
     * The band is not one width; don't re-derive this as a single sigma.
     */
    public static double TM_POWER_SIGMA_HIGH = 45;

    /**
     * Floor under the power-band weight, so extreme-power moves are thinned but never excluded -
     * vanilla's own TM list carries a 250 BP move, and removing the tail outright would trade away
     * exactly the surprise this fork exists to protect.
     */
    public static double TM_POWER_BAND_FLOOR = 0.05;

    /**
     * Minimum stored power for a move to be treated as having a real base power. Fixed-damage and
     * variable-power moves (Seismic Toss, Night Shade, Psywave, ...) carry a sentinel power of 1;
     * they are exempted from the power-band weight entirely rather than being suppressed as though
     * they were 1 BP moves. Vanilla Ultra Sun has five of them among its TMs.
     */
    private static final int MIN_REAL_BASE_POWER = 2;

    /**
     * Target physical share of the damaging half of a TM/tutor pool.
     * <p>
     * The TM layer is deliberately the most category-balanced pool in the game - 52/48 in USUM,
     * 51/49 in ORAS - against 62/38 for the rest of the move roster and 63/37 for level-up slots.
     * A uniform draw inherits the roster's lean instead, measured at 59.9/40.1 on Ultra Sun.
     */
    public static double TM_PHYSICAL_SHARE = 0.52;

    /**
     * Weight multiplier applied to whichever category is currently behind {@link #TM_PHYSICAL_SHARE}.
     * A running-deficit weight rather than a hard partition, so a pool with few moves of one category
     * in some type can never starve the draw.
     */
    public static double TM_CATEGORY_BALANCE_BONUS = 3.0;

    /**
     * First generation whose TM pool guarantees at least one damaging move of every type.
     * <p>
     * This is a measured invariant, not a preference: from Gen 4 onward every type in the game has at
     * least one damaging TM, and the 92-TM expansion is what bought that. Before it the guarantee
     * genuinely did not hold - Red/Blue shipped no Bug, Ghost or Poison TM at all, and Gold/Silver no
     * Water TM - so Gen 1-3 is left alone rather than being given a coverage floor vanilla never had.
     * <p>
     * Applies to TMs only. Vanilla tutor pools do <i>not</i> cover every type (USUM's misses Rock,
     * Ghost and Fairy), and forcing them to would erase a real difference between the two pools.
     */
    private static final int GEN_OF_TYPE_COVERAGE_GUARANTEE = 4;

    /**
     * Share of damaging Move Tutor picks aimed at the high-power band instead of the normal one.
     * <p>
     * The tutor pool is not a second TM pool: its power distribution is visibly bimodal, a 60-99 body
     * plus a distinct 120-160 spike. Measured on real ROMs, vanilla puts 13-18% of its damaging
     * tutors at 120 BP or above against 9-10% of its TMs in Gen 6/7.
     * <p>
     * Note this targets the <i>fork's own vanilla tutor lists</i> (Ultra Sun 15.8%), not the research
     * report's 24.5% - that figure comes from a 78-move pool derived from PokeAPI, where the ROM
     * actually carries 67 tutors.
     */
    public static double TUTOR_CAPSTONE_SHARE = 0.10;

    /** Centre of the tutor pool's high-power second mode. The report locates the spike at 120-160. */
    public static double TUTOR_CAPSTONE_CENTER = 140;

    /**
     * True while picking a tutor slot that should aim at the capstone band rather than the body.
     * Set immediately before the draw, same pattern as {@link #categoryInNeed}.
     */
    private boolean capstonePick;

    /**
     * The category currently under its target share, or null when category balancing is off.
     * <p>
     * Set immediately before each weighted draw. Gen 1-3 always leave this null: there a move's
     * category is derived from its <i>type</i>, so steering category would silently become a type
     * lever and fight the type-coverage floor rather than doing anything meaningful.
     */
    private MoveCategory categoryInNeed;

    private boolean tmChangesMade;
    private boolean tutorChangesMade;

    public TMTutorMoveRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    /**
     * The move lists one TM/tutor draw picks from, kept in sync as moves are consumed.
     * <p>
     * {@code goodDamaging} backs the existing player-facing "Force % Good Damaging Moves" option and
     * is a narrower set than {@code attacking} - it applies {@link Move#isGoodDamaging}, which
     * screens out weak and unreliable moves. The damaging/status quota deliberately draws from the
     * wider {@code attacking} list instead, so that hitting vanilla's 67/33 split does not smuggle in
     * a power bias: vanilla's damaging two-thirds includes plenty of weak moves.
     */
    private static final class PickPool {
        final List<Move> usable;
        final List<Move> goodDamaging;
        final List<Move> attacking;
        final List<Move> status;

        PickPool(List<Move> usable, Set<Move> notGoodDamaging) {
            this.usable = usable;
            this.goodDamaging = new ArrayList<>(usable);
            this.goodDamaging.removeAll(notGoodDamaging);
            this.attacking = new ArrayList<>();
            this.status = new ArrayList<>();
            for (Move mv : usable) {
                if (mv.category == MoveCategory.STATUS) {
                    status.add(mv);
                } else {
                    attacking.add(mv);
                }
            }
        }

        void remove(Move mv) {
            usable.remove(mv);
            goodDamaging.remove(mv);
            attacking.remove(mv);
            status.remove(mv);
        }

        boolean isEmpty() {
            return usable.isEmpty();
        }
    }

    /**
     * How many of {@code slots} should be filled with damaging moves, per the pool's measured vanilla
     * damaging share. The player's explicit "Force % Good Damaging Moves" setting always wins if it
     * asks for more than the vanilla share would.
     */
    private int damagingQuota(int slots, int forcedGoodDamaging, double vanillaShare) {
        return Math.min(slots, Math.max(forcedGoodDamaging, (int) Math.round(vanillaShare * slots)));
    }

    /**
     * Picks one move for a TM/tutor slot. Falls back to the unrestricted pool whenever the preferred
     * sub-pool is exhausted, so a small or heavily-banned move roster can never make this throw.
     */
    private Move pickPoolMove(PickPool pool, boolean forceGoodDamaging, boolean needDamaging) {
        if (forceGoodDamaging && !pool.goodDamaging.isEmpty()) {
            return weightedPick(pool.goodDamaging);
        }
        if (needDamaging && !pool.attacking.isEmpty()) {
            return weightedPick(pool.attacking);
        }
        if (!needDamaging && !pool.status.isEmpty()) {
            // Status moves have neither a base power nor a meaningful accuracy to weight on.
            return pool.status.get(random.nextInt(pool.status.size()));
        }
        return pool.usable.get(random.nextInt(pool.usable.size()));
    }

    /**
     * Draws one move in proportion to {@link #attackingMoveWeight}. Falls back to a uniform draw if
     * every weight in the pool is zero, so the pick can never fail.
     */
    private Move weightedPick(List<Move> pool) {
        double total = 0;
        for (Move mv : pool) {
            total += attackingMoveWeight(mv);
        }
        if (total <= 0) {
            return pool.get(random.nextInt(pool.size()));
        }
        double roll = random.nextDouble() * total;
        for (Move mv : pool) {
            roll -= attackingMoveWeight(mv);
            if (roll < 0) {
                return mv;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Combined selection weight for a damaging TM/tutor candidate: reliability x power band x category balance. */
    private double attackingMoveWeight(Move mv) {
        double weight = accuracyWeight(mv) * powerBandWeight(mv);
        if (categoryInNeed != null && mv.category == categoryInNeed) {
            weight *= TM_CATEGORY_BALANCE_BONUS;
        }
        return weight;
    }

    /**
     * Points {@link #categoryInNeed} at whichever category is currently short of
     * {@link #TM_PHYSICAL_SHARE}, or leaves it null on Gen 1-3 where category is a function of type.
     */
    private void updateCategoryInNeed(int physicalPicked, int damagingPicked) {
        if (romHandler.generationOfPokemon() < 4) {
            categoryInNeed = null;
            return;
        }
        double physicalShare = damagingPicked == 0 ? 0 : (double) physicalPicked / damagingPicked;
        categoryInNeed = physicalShare < TM_PHYSICAL_SHARE ? MoveCategory.PHYSICAL : MoveCategory.SPECIAL;
    }

    private double accuracyWeight(Move mv) {
        double accuracy = (mv.hitratio == romHandler.getPerfectAccuracy()) ? 100 : mv.hitratio;
        if (accuracy <= 0) {
            return 1.0; // no accuracy data for this move; don't penalise it
        }
        return Math.pow(Math.min(accuracy, 100) / 100.0, TM_ACCURACY_EXPONENT);
    }

    private double powerBandWeight(Move mv) {
        if (mv.power < MIN_REAL_BASE_POWER) {
            return 1.0; // fixed/variable damage move - it has no base power to band
        }
        double delta = mv.power - (capstonePick ? TUTOR_CAPSTONE_CENTER : TM_POWER_CENTER);
        double sigma = delta < 0 ? TM_POWER_SIGMA_LOW : TM_POWER_SIGMA_HIGH;
        double weight = Math.exp(-(delta * delta) / (2 * sigma * sigma));
        return Math.max(TM_POWER_BAND_FLOOR, weight);
    }

    /**
     * Returns whether any changes have been made to TM moves.
     */
    public boolean isTMChangesMade() {
        return tmChangesMade;
    }

    /**
     * Returns whether any changes have been made to Move Tutor moves.
     */
    public boolean isTutorChangesMade() {
        return tutorChangesMade;
    }

    public void randomizeTMMoves() {
        boolean noBroken = settings.isBlockBrokenTMMoves();
        boolean preserveField = settings.isKeepFieldMoveTMs();
        double goodDamagingPercentage = settings.isTmsForceGoodDamaging() ? settings.getTmsGoodDamagingPercent() / 100.0 : 0;

        // Pick some random TM moves.
        int tmCount = romHandler.getTMCount();
        List<Move> allMoves = romHandler.getMoves();
        List<Integer> hms = romHandler.getHMMoves();
        List<Integer> oldTMs = romHandler.getTMMoves();
        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? romHandler.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        banned.addAll(romHandler.getIllegalMoves());
        // field moves?
        List<Integer> fieldMoves = romHandler.getFieldMoves();
        int preservedFieldMoveCount = 0;

        if (preserveField) {
            List<Integer> banExistingField = new ArrayList<>(oldTMs);
            banExistingField.retainAll(fieldMoves);
            preservedFieldMoveCount = banExistingField.size();
            banned.addAll(banExistingField);
        }

        // Determine which moves are pickable
        List<Move> usableMoves = new ArrayList<>(allMoves);
        usableMoves.remove(0); // remove null entry
        Set<Move> unusableMoves = new HashSet<>();
        Set<Move> unusableDamagingMoves = new HashSet<>();

        for (Move mv : usableMoves) {
            if (GlobalConstants.bannedRandomMoves[mv.number] || GlobalConstants.zMoves.contains(mv.number) ||
                    hms.contains(mv.number) || banned.contains(mv.number)) {
                unusableMoves.add(mv);
            } else if (GlobalConstants.bannedForDamagingMove[mv.number] || !mv.isGoodDamaging(romHandler.getPerfectAccuracy())) {
                unusableDamagingMoves.add(mv);
            }
        }

        usableMoves.removeAll(unusableMoves);
        PickPool pool = new PickPool(usableMoves, unusableDamagingMoves);

        // pick (tmCount - preservedFieldMoveCount) moves
        List<Integer> pickedMoves = new ArrayList<>();
        int slots = tmCount - preservedFieldMoveCount;

        // Force a certain amount of good damaging moves depending on the percentage
        int goodDamagingLeft = (int) Math.round(goodDamagingPercentage * slots);
        // Fill the rest to vanilla's measured damaging/status split rather than letting the ROM's
        // own move roster decide it by default.
        int damagingLeft = damagingQuota(slots, goodDamagingLeft, TM_DAMAGING_SHARE);
        int physicalPicked = 0;
        int damagingPicked = 0;

        // Reserve one damaging TM per type before drawing freely, so no type is left without an
        // attacking option. Types are shuffled so that if the reservations cannot all be honoured it
        // is not always the same types that lose out.
        if (romHandler.generationOfPokemon() >= GEN_OF_TYPE_COVERAGE_GUARANTEE) {
            List<Type> types = new ArrayList<>(Type.getAllTypes(romHandler.generationOfPokemon()));
            Collections.shuffle(types, random);
            for (Type type : types) {
                if (damagingLeft <= 0 || pickedMoves.size() >= slots) {
                    break;
                }
                List<Move> source = (goodDamagingLeft > 0 && !pool.goodDamaging.isEmpty())
                        ? pool.goodDamaging : pool.attacking;
                List<Move> ofType = new ArrayList<>();
                for (Move mv : source) {
                    if (mv.type == type) {
                        ofType.add(mv);
                    }
                }
                if (ofType.isEmpty()) {
                    continue; // this game has no usable damaging move of that type
                }
                updateCategoryInNeed(physicalPicked, damagingPicked);
                Move chosenMove = weightedPick(ofType);
                pickedMoves.add(chosenMove.number);
                pool.remove(chosenMove);
                damagingLeft--;
                damagingPicked++;
                if (chosenMove.category == MoveCategory.PHYSICAL) {
                    physicalPicked++;
                }
                goodDamagingLeft--;
            }
        }

        while (pickedMoves.size() < slots) {
            updateCategoryInNeed(physicalPicked, damagingPicked);
            Move chosenMove = pickPoolMove(pool, goodDamagingLeft > 0, damagingLeft > 0);
            pickedMoves.add(chosenMove.number);
            pool.remove(chosenMove);
            if (chosenMove.category != MoveCategory.STATUS) {
                damagingLeft--;
                damagingPicked++;
                if (chosenMove.category == MoveCategory.PHYSICAL) {
                    physicalPicked++;
                }
            }
            goodDamagingLeft--;
        }

        // shuffle the picked moves because high goodDamagingPercentage
        // will bias them towards early numbers otherwise

        Collections.shuffle(pickedMoves, random);

        // finally, distribute them as tms
        int pickedMoveIndex = 0;
        List<Integer> newTMs = new ArrayList<>();

        for (int i = 0; i < tmCount; i++) {
            if (preserveField && fieldMoves.contains(oldTMs.get(i))) {
                newTMs.add(oldTMs.get(i));
            } else {
                newTMs.add(pickedMoves.get(pickedMoveIndex++));
            }
        }

        romHandler.setTMMoves(newTMs);
        tmChangesMade = true;
    }

    /**
     * Locks each Gym Leader's reward TM (as defined by {@link RomHandler#getGymLeaderTMs()}) to a
     * random move of that gym's assigned type theme. Status moves of the type are valid picks;
     * damaging bias, if wanted, is handled separately by the "Force % Good Damaging Moves" option.
     * <p>
     * Intended to run <b>after</b> both TM-move randomization and trainer-Pokémon randomization, so
     * that {@code gymThemes} reflects the types actually assigned to the gyms. Does nothing if there
     * is no gym-TM data for the game or no assigned themes.
     *
     * @param gymThemes Map of gym group tag (e.g. {@code "GYM1"}) to the type it was assigned.
     */
    public void typeLockGymLeaderTMs(Map<String, Type> gymThemes) {
        Map<String, Integer> gymLeaderTMs = romHandler.getGymLeaderTMs();
        if (gymLeaderTMs.isEmpty() || gymThemes == null || gymThemes.isEmpty()) {
            return;
        }

        boolean noBroken = settings.isBlockBrokenTMMoves();
        boolean preserveField = settings.isKeepFieldMoveTMs();

        List<Move> allMoves = romHandler.getMoves();
        List<Integer> hms = romHandler.getHMMoves();
        List<Integer> currentTMs = new ArrayList<>(romHandler.getTMMoves());
        // Move Tutor moves are finalized before this step runs, so they must be excluded too -
        // otherwise a type-locked gym TM can teach a move already available from a Tutor.
        List<Integer> currentTutorMoves = romHandler.hasMoveTutors() ?
                romHandler.getMoveTutorMoves() : Collections.emptyList();
        List<Integer> fieldMoves = romHandler.getFieldMoves();
        int tmCount = romHandler.getTMCount();

        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? romHandler.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        banned.addAll(romHandler.getIllegalMoves());

        boolean changed = false;

        for (Map.Entry<String, Integer> entry : gymLeaderTMs.entrySet()) {
            Type type = gymThemes.get(entry.getKey());
            int tmNumber = entry.getValue();
            if (type == null || tmNumber < 1 || tmNumber > tmCount) {
                // No assigned theme for this gym, or the TM doesn't exist in this game.
                continue;
            }
            int tmIndex = tmNumber - 1;
            int oldMove = currentTMs.get(tmIndex);

            // Don't override a preserved field-move TM.
            if (preserveField && fieldMoves.contains(oldMove)) {
                continue;
            }

            // Pool of usable moves of the gym's type, excluding moves already taught by another TM
            // or by a Move Tutor (to keep TM and Tutor moves unique). The move currently on this TM
            // stays eligible.
            List<Move> pool = new ArrayList<>();
            for (Move mv : allMoves) {
                if (mv == null || mv.number == 0 || mv.type != type) {
                    continue;
                }
                if (GlobalConstants.bannedRandomMoves[mv.number] || GlobalConstants.zMoves.contains(mv.number)
                        || hms.contains(mv.number) || banned.contains(mv.number)) {
                    continue;
                }
                if (mv.number != oldMove && currentTMs.contains(mv.number)) {
                    continue;
                }
                if (mv.number != oldMove && currentTutorMoves.contains(mv.number)) {
                    continue;
                }
                pool.add(mv);
            }
            if (pool.isEmpty()) {
                // No suitable same-type move available; leave the TM as it is.
                continue;
            }

            Move chosen = pool.get(random.nextInt(pool.size()));
            if (chosen.number != oldMove) {
                currentTMs.set(tmIndex, chosen.number);
                changed = true;
            }
        }

        if (changed) {
            romHandler.setTMMoves(currentTMs);
            tmChangesMade = true;
        }
    }

    public void randomizeMoveTutorMoves() {
        boolean noBroken = settings.isBlockBrokenTutorMoves();
        boolean preserveField = settings.isKeepFieldMoveTutors();
        double goodDamagingPercentage = settings.isTutorsForceGoodDamaging() ? settings.getTutorsGoodDamagingPercent() / 100.0 : 0;

        if (!romHandler.hasMoveTutors()) {
            return;
        }

        // Pick some random Move Tutor moves, excluding TMs.
        List<Move> allMoves = romHandler.getMoves();
        List<Integer> tms = romHandler.getTMMoves();
        List<Integer> oldMTs = romHandler.getMoveTutorMoves();
        int mtCount = oldMTs.size();
        List<Integer> hms = romHandler.getHMMoves();
        @SuppressWarnings("unchecked")
        List<Integer> banned = new ArrayList<Integer>(noBroken ? romHandler.getGameBreakingMoves() : Collections.EMPTY_LIST);
        banned.addAll(romHandler.getMovesBannedFromLevelup());
        banned.addAll(romHandler.getIllegalMoves());

        // field moves?
        List<Integer> fieldMoves = romHandler.getFieldMoves();
        int preservedFieldMoveCount = 0;
        if (preserveField) {
            List<Integer> banExistingField = new ArrayList<>(oldMTs);
            banExistingField.retainAll(fieldMoves);
            preservedFieldMoveCount = banExistingField.size();
            banned.addAll(banExistingField);
        }

        // Determine which moves are pickable
        List<Move> usableMoves = new ArrayList<>(allMoves);
        usableMoves.remove(0); // remove null entry
        Set<Move> unusableMoves = new HashSet<>();
        Set<Move> unusableDamagingMoves = new HashSet<>();
        // Moves that are legal in themselves and excluded only because a TM already teaches them.
        // Tracked separately so they can be reinstated if the TM-exclusive pool runs out below.
        Set<Move> tmOnlyExcludedMoves = new HashSet<>();

        for (Move mv : usableMoves) {
            if (GlobalConstants.bannedRandomMoves[mv.number] || hms.contains(mv.number)
                    || banned.contains(mv.number) || GlobalConstants.zMoves.contains(mv.number)) {
                unusableMoves.add(mv);
                continue;
            }
            if (GlobalConstants.bannedForDamagingMove[mv.number] || !mv.isGoodDamaging(romHandler.getPerfectAccuracy())) {
                unusableDamagingMoves.add(mv);
            }
            if (tms.contains(mv.number)) {
                unusableMoves.add(mv);
                tmOnlyExcludedMoves.add(mv);
            }
        }

        usableMoves.removeAll(unusableMoves);
        PickPool pool = new PickPool(usableMoves, unusableDamagingMoves);

        // pick (tmCount - preservedFieldMoveCount) moves
        List<Integer> pickedMoves = new ArrayList<>();
        int slots = mtCount - preservedFieldMoveCount;

        // Force a certain amount of good damaging moves depending on the percentage
        int goodDamagingLeft = (int) Math.round(goodDamagingPercentage * slots);
        // The tutor pool is composed to its own measured share, independent of the TM pool's.
        int damagingLeft = damagingQuota(slots, goodDamagingLeft, TUTOR_DAMAGING_SHARE);
        int physicalPicked = 0;
        int damagingPicked = 0;

        for (int i = 0; i < slots; i++) {
            if (pool.isEmpty()) {
                // The TM-exclusive pool ran dry - only reachable on a ROM whose legal move set is
                // tiny relative to its combined TM + Tutor count. Reinstate the moves that were held
                // back purely for overlapping a TM: those are still unpicked, so Tutor moves remain
                // unique among themselves. Only once those are gone do we allow repeating an
                // already-picked move. Without this the pick below would throw on an empty pool.
                List<Move> refilled;
                if (!tmOnlyExcludedMoves.isEmpty()) {
                    refilled = new ArrayList<>(tmOnlyExcludedMoves);
                    tmOnlyExcludedMoves.clear();
                } else {
                    refilled = new ArrayList<>(allMoves);
                    refilled.remove(0); // remove null entry
                    refilled.removeAll(unusableMoves);
                }
                if (refilled.isEmpty()) {
                    // No legal move exists at all; leave the remaining tutors untouched.
                    break;
                }
                pool = new PickPool(refilled, unusableDamagingMoves);
            }
            updateCategoryInNeed(physicalPicked, damagingPicked);
            // Tutors draw from a bimodal power distribution, unlike TMs - some slots deliberately
            // aim at the high-power spike rather than the 60-99 body.
            capstonePick = damagingLeft > 0 && random.nextDouble() < TUTOR_CAPSTONE_SHARE;
            Move chosenMove = pickPoolMove(pool, goodDamagingLeft > 0, damagingLeft > 0);
            capstonePick = false;
            pickedMoves.add(chosenMove.number);
            pool.remove(chosenMove);
            if (chosenMove.category != MoveCategory.STATUS) {
                damagingLeft--;
                damagingPicked++;
                if (chosenMove.category == MoveCategory.PHYSICAL) {
                    physicalPicked++;
                }
            }
            goodDamagingLeft--;
        }

        // shuffle the picked moves because high goodDamagingPercentage
        // will bias them towards early numbers otherwise

        Collections.shuffle(pickedMoves, random);

        // finally, distribute them as tutors
        int pickedMoveIndex = 0;
        List<Integer> newMTs = new ArrayList<>();

        for (Integer oldMT : oldMTs) {
            if (preserveField && fieldMoves.contains(oldMT)) {
                newMTs.add(oldMT);
            } else if (pickedMoveIndex < pickedMoves.size()) {
                newMTs.add(pickedMoves.get(pickedMoveIndex++));
            } else {
                // Fewer moves picked than tutor slots, i.e. the pool ran out entirely above.
                newMTs.add(oldMT);
            }
        }

        romHandler.setMoveTutorMoves(newMTs);
        tutorChangesMade = true;
    }

}
