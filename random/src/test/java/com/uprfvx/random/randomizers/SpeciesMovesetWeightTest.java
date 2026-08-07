package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the "Sensible Movesets" power-banding mechanism: a hard sliding ceiling
 * (species-side {@code applySpeciesPowerCeiling}, mirroring Better Movesets' trainer-side
 * {@code applyPowerBandFilter}) plus a soft sliding floor ({@code speciesLevelAppropriatenessWeight},
 * reusing the trainer path's Regular-tier falloff exponent). Species learnsets follow the same
 * level-vs-power banding as trainer movesets rather than a bespoke shape.
 * No ROM required.
 */
public class SpeciesMovesetWeightTest {

    private Move damagingMove(int power) {
        Move mv = new Move();
        mv.power = power;
        mv.category = MoveCategory.PHYSICAL;
        return mv; // hitCount defaults to 1
    }

    private Move statusMove() {
        Move mv = new Move();
        mv.power = 0;
        mv.category = MoveCategory.STATUS;
        return mv;
    }

    // --- soft floor (speciesLevelAppropriatenessWeight) ---

    @Test
    public void statusAndFixedDamageMovesAlwaysKeepFullWeight() {
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(statusMove(), 1));
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(statusMove(), 50));
    }

    @Test
    public void movesAtOrAboveTheFloorKeepFullWeight() {
        // speciesCenterPower(1) = 45 + 65*(1/40) = 46.625, floor = 46.625 * 0.75 = 34.97
        Move atFloor = damagingMove(35);
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(atFloor, 1));
    }

    @Test
    public void movesBelowTheFloorAreDemotedByTheLevelScaledExponent() {
        double floor = Randomizer.speciesCenterPower(1) * Randomizer.POWER_FLOOR_FRACTION;
        double expected = Math.pow(20.0 / floor, Randomizer.speciesFloorExponent(1));
        assertEquals(expected, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(damagingMove(20), 1),
                1e-9);
        assertTrue(expected < 1.0);
    }

    @Test
    public void theFloorBitesHarderLateThanEarly() {
        // The same relative shortfall should cost a late-game slot far more weight than an early one, so
        // the top of a learnset reads as a payoff.
        double earlyPenalty = Math.pow(0.5, Randomizer.speciesFloorExponent(1));
        double latePenalty = Math.pow(0.5, Randomizer.speciesFloorExponent(55));
        assertTrue(latePenalty < earlyPenalty / 2,
                "late floor should be much steeper: early=" + earlyPenalty + " late=" + latePenalty);
    }

    @Test
    public void theFloorDoesNotSharpenBeforeTheLateGame() {
        // Steepening it earlier quietly drags every mid-game band up with it.
        assertEquals(Randomizer.SPECIES_FLOOR_EXPONENT_EARLY, Randomizer.speciesFloorExponent(1), 1e-9);
        assertEquals(Randomizer.SPECIES_FLOOR_EXPONENT_EARLY, Randomizer.speciesFloorExponent(40), 1e-9);
        assertTrue(Randomizer.speciesFloorExponent(41) > Randomizer.SPECIES_FLOOR_EXPONENT_EARLY);
    }

    @Test
    public void floorRisesWithLevel() {
        // A 40 BP move sits above the Lv1 floor (34.97) but well below the saturated floor (110*0.75=82.5).
        Move mv = damagingMove(40);
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(mv, 1));
        assertTrue(SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(mv, 50) < 1.0);
    }

    @Test
    public void thePowerCurveSpikesAfterTheLateGameThreshold() {
        // Gentle to 40, then a distinctly steeper climb to saturation - the early slope must stay gentle
        // or every mid-game band inflates with it.
        assertEquals(Randomizer.SPECIES_LEVEL_POWER_MID, Randomizer.speciesCenterPower(40), 1e-9);
        assertEquals(Randomizer.SPECIES_LEVEL_POWER_MAX, Randomizer.speciesCenterPower(55), 1e-9);
        assertEquals(Randomizer.SPECIES_LEVEL_POWER_MAX, Randomizer.speciesCenterPower(80), 1e-9);

        double earlySlope = (Randomizer.speciesCenterPower(40) - Randomizer.speciesCenterPower(20)) / 20.0;
        double lateSlope = (Randomizer.speciesCenterPower(50) - Randomizer.speciesCenterPower(40)) / 10.0;
        assertTrue(lateSlope > earlySlope * 1.5,
                "late slope should clearly outpace early: early=" + earlySlope + " late=" + lateSlope);
    }

    // --- hard ceiling (applySpeciesPowerCeiling) ---

    @Test
    public void ceilingRemovesMovesAboveItAtLowLevel() {
        // powerCeiling(1) = max(60, 46*0.95) = 60.
        List<Move> pool = List.of(damagingMove(40), damagingMove(120));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 1);
        assertEquals(1, capped.size());
        assertEquals(40, capped.get(0).power);
    }

    @Test
    public void thereIsNoCeilingOnceTheLateGameStarts() {
        // Past level 40 a learnset should be free to hand out anything it has.
        List<Move> pool = List.of(damagingMove(120), damagingMove(250));
        assertEquals(2, SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 40).size());
        assertEquals(2, SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 60).size());
        // ...but not one level earlier.
        assertEquals(1, SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 39).size());
    }

    @Test
    public void ceilingFallsBackToWiderPoolWhenNarrowPoolIsAllOverCeiling() {
        // The real Fire-Special/Steel-Physical/Psychic-Special case: a type's whole damaging pool is
        // naturally weak-move-poor (e.g. vanilla Fire Red: only 3/12 Fire-type damaging moves are
        // <=60 BP), but the global damaging pool isn't. powerCeiling(1) = 60.
        List<Move> narrow = List.of(damagingMove(120), damagingMove(150));
        List<Move> wider = List.of(damagingMove(40), damagingMove(120));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(narrow, wider, 1);
        assertEquals(1, capped.size());
        assertEquals(40, capped.get(0).power);
    }

    @Test
    public void ceilingNeverEmptiesWhenBothPoolsAreAllOverCeiling() {
        // Even the wider fallback pool has nothing under the ceiling - must still fall back to the
        // unfiltered narrow pool rather than returning empty and forcing a null pick downstream.
        List<Move> narrow = List.of(damagingMove(120), damagingMove(150));
        List<Move> wider = List.of(damagingMove(130));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(narrow, wider, 1);
        assertFalse(capped.isEmpty());
        assertEquals(2, capped.size());
    }

    @Test
    public void ceilingPrefersNarrowPoolWhenItAlreadySatisfiesTheCeiling() {
        // The wider pool must never override a narrow pool that already has a valid (under-ceiling)
        // candidate - the fallback only kicks in when the narrow pool is exhausted.
        List<Move> narrow = List.of(damagingMove(40));
        List<Move> wider = List.of(damagingMove(35));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(narrow, wider, 1);
        assertEquals(1, capped.size());
        assertEquals(40, capped.get(0).power);
    }

    @Test
    public void ceilingExemptsStatusAndFixedDamageMoves() {
        List<Move> pool = List.of(statusMove());
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 1);
        assertEquals(1, capped.size());
    }

    // --- category lean (speciesCategoryLean / categoryLeanWeight) ---

    private Move specialMove(int power) {
        Move mv = new Move();
        mv.power = power;
        mv.category = MoveCategory.SPECIAL;
        return mv;
    }

    @Test
    public void anEvenAttackerGetsNoCategoryLean() {
        assertEquals(0.5, SpeciesMovesetRandomizer.speciesCategoryLean(0.5), 1e-9);
    }

    @Test
    public void theLeanAmplifiesTheRawStatRatio() {
        // Atk 110 / SpA 70 is r=0.611, but vanilla's matching Atk-SpA bucket runs 79.5% physical.
        double lean = SpeciesMovesetRandomizer.speciesCategoryLean(110.0 / (110 + 70));
        assertTrue(lean > 0.611, "lean should exceed the raw ratio, was " + lean);
        assertTrue(lean < 0.95);
    }

    @Test
    public void theLeanIsSymmetricAboutAnEvenSplit() {
        double physical = SpeciesMovesetRandomizer.speciesCategoryLean(0.7);
        double special = SpeciesMovesetRandomizer.speciesCategoryLean(0.3);
        assertEquals(1.0, physical + special, 1e-9);
    }

    @Test
    public void evenAnExtremeSplitKeepsSomeChanceOfTheOtherCategory() {
        assertEquals(0.95, SpeciesMovesetRandomizer.speciesCategoryLean(1.0), 1e-9);
        assertEquals(0.05, SpeciesMovesetRandomizer.speciesCategoryLean(0.0), 1e-9);
    }

    @Test
    public void thePreferredCategoryIsBonusedAndTheOtherIsNot() {
        assertEquals(Randomizer.SPECIES_CATEGORY_BONUS,
                SpeciesMovesetRandomizer.categoryLeanWeight(damagingMove(60), MoveCategory.PHYSICAL));
        assertEquals(1.0,
                SpeciesMovesetRandomizer.categoryLeanWeight(specialMove(60), MoveCategory.PHYSICAL));
    }

    @Test
    public void statusMovesAreNeverCategoryLeaned() {
        assertEquals(1.0, SpeciesMovesetRandomizer.categoryLeanWeight(statusMove(), MoveCategory.PHYSICAL));
        assertEquals(1.0, SpeciesMovesetRandomizer.categoryLeanWeight(statusMove(), MoveCategory.SPECIAL));
    }

    @Test
    public void aNullPreferenceLeavesEveryMoveUnweighted() {
        // Sensible Movesets off - the lean must not apply at all.
        assertEquals(1.0, SpeciesMovesetRandomizer.categoryLeanWeight(damagingMove(60), null));
        assertEquals(1.0, SpeciesMovesetRandomizer.categoryLeanWeight(specialMove(60), null));
    }

    // --- species power tier (speciesPowerScale) ---

    private Species speciesWithBst(int bst) {
        Species sp = new Species(1);
        int each = bst / 6;
        sp.setBaseStats(new BaseStats(each, each, each, each, each, bst - each * 5));
        return sp;
    }

    @Test
    public void aFrailStandAloneSpeciesSitsAtTheBottomOfTheScalePlusItsCompensation() {
        // A species with no evolutions in either direction is stand-alone by definition, which is what a
        // bare fixture is - so the floor it lands on includes the stand-alone compensation.
        assertEquals(Randomizer.SPECIES_POWER_SCALE_MIN + Randomizer.SPECIES_POWER_SCALE_STANDALONE_BONUS,
                SpeciesMovesetRandomizer.speciesPowerScale(speciesWithBst(250)), 1e-9);
    }

    @Test
    public void aSpeciesThatStillEvolvesGetsNoStandAloneCompensation() {
        Species evolves = speciesWithBst(250);
        Species target = speciesWithBst(500);
        evolves.getEvolutionsFrom().add(new Evolution(evolves, target, EvolutionType.LEVEL, 16));
        assertEquals(Randomizer.SPECIES_POWER_SCALE_MIN,
                SpeciesMovesetRandomizer.speciesPowerScale(evolves), 1e-9);
    }

    @Test
    public void scaleRisesWithBaseStatTotal() {
        double weak = SpeciesMovesetRandomizer.speciesPowerScale(speciesWithBst(300));
        double mid = SpeciesMovesetRandomizer.speciesPowerScale(speciesWithBst(450));
        double strong = SpeciesMovesetRandomizer.speciesPowerScale(speciesWithBst(600));
        assertTrue(weak < mid, "weak=" + weak + " mid=" + mid);
        assertTrue(mid < strong, "mid=" + mid + " strong=" + strong);
    }

    @Test
    public void scaleIsClampedAtBothEnds() {
        double floor = SpeciesMovesetRandomizer.speciesPowerScale(speciesWithBst(1));
        double ceiling = SpeciesMovesetRandomizer.speciesPowerScale(speciesWithBst(1200));
        assertTrue(floor >= Randomizer.SPECIES_POWER_SCALE_MIN);
        assertTrue(ceiling <= Randomizer.SPECIES_POWER_SCALE_MAX);
    }

    @Test
    public void theScaledCeilingTracksTheScale() {
        // A frail species must get a genuinely tighter cap than the shared TIER_LOW_MAX_BP floor implies,
        // otherwise the tier does nothing at low level - where most of the dex actually lives.
        assertTrue(Randomizer.speciesPowerCeiling(1, 0.90) < Randomizer.speciesPowerCeiling(1, 1.0));
        assertTrue(Randomizer.speciesPowerCeiling(30, 1.25) > Randomizer.speciesPowerCeiling(30, 1.0));
    }

    @Test
    public void aScaledSpeciesCeilingFiltersDifferentlyFromAnUnscaledOne() {
        // powerCeiling(1) = 60; at scale 0.90 it is 54, so a 57 BP move clears one and not the other.
        List<Move> pool = List.of(damagingMove(57));
        assertEquals(1, SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 1, 1.0).size());
        List<Move> wider = List.of(damagingMove(30));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, wider, 1, 0.90);
        assertEquals(30, capped.get(0).power, "the 57 BP move should have been culled at scale 0.90");
    }

    // --- slot roles (assignSlotRoles) ---

    private List<MoveLearnt> learnsetOf(int... levels) {
        List<MoveLearnt> moves = new ArrayList<>();
        for (int level : levels) {
            moves.add(new MoveLearnt(1, level));
        }
        return moves;
    }

    private SpeciesLearnsetProfile profileWithStatusShare(double statusShare) {
        return new SpeciesLearnsetProfile(statusShare, List.of(Type.FIRE), Set.of(Type.GROUND), 1.0, 0.5,
                null, 1.0);
    }

    private int countRole(SpeciesMovesetRandomizer.SlotRole[] roles,
                          SpeciesMovesetRandomizer.SlotRole role) {
        int n = 0;
        for (SpeciesMovesetRandomizer.SlotRole r : roles) {
            if (r == role) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void everySlotGetsARole() {
        List<MoveLearnt> moves = learnsetOf(1, 5, 12, 20, 28, 35, 44, 52);
        SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                moves, 0, profileWithStatusShare(0.375), Set.of(), true, new Random(1L), new int[]{0, 0, 0});
        for (int i = 0; i < moves.size(); i++) {
            assertNotNull(roles[i], "slot " + i + " was left unassigned");
        }
    }

    @Test
    public void theStatusShareIsRoughlyHonoured() {
        // 20 slots at 50% status, minus the 15% held back as wildcards.
        List<MoveLearnt> moves = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            moves.add(new MoveLearnt(1, 5 * (i + 1)));
        }
        SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                moves, 0, profileWithStatusShare(0.5), Set.of(), true, new Random(7L), new int[]{0, 0, 0});
        int status = countRole(roles, SpeciesMovesetRandomizer.SlotRole.STATUS);
        assertTrue(status >= 8 && status <= 10, "expected ~10 status slots, got " + status);
    }

    @Test
    public void forceGoodDamagingSlotsAreNeverGivenAStatusRole() {
        List<MoveLearnt> moves = learnsetOf(1, 5, 12, 20, 28, 35, 44, 52);
        Set<Integer> forced = Set.of(2, 3, 4);
        SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                moves, 0, profileWithStatusShare(0.9), forced, true, new Random(3L), new int[]{0, 0, 0});
        for (int i : forced) {
            assertTrue(roles[i] != SpeciesMovesetRandomizer.SlotRole.STATUS,
                    "forced-damaging slot " + i + " was made STATUS");
        }
    }

    @Test
    public void someSlotsAreHeldBackAsWildcards() {
        List<MoveLearnt> moves = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            moves.add(new MoveLearnt(1, 5 * (i + 1)));
        }
        SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                moves, 0, profileWithStatusShare(0.375), Set.of(), true, new Random(11L), new int[]{0, 0, 0});
        assertTrue(countRole(roles, SpeciesMovesetRandomizer.SlotRole.WILDCARD) > 0,
                "the surprise valve should reserve at least one slot on a 20-slot learnset");
    }

    @Test
    public void theEarliestAttackingSlotIsAlwaysStabOrNormalFiller() {
        // Vanilla opens on the species' own type about 45% of the time and on Normal filler otherwise -
        // never on some arbitrary third type.
        int stabOpeners = 0;
        int trials = 400;
        for (long seed = 0; seed < trials; seed++) {
            List<MoveLearnt> moves = learnsetOf(1, 5, 12, 20, 28, 35, 44, 52);
            SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                    moves, 0, profileWithStatusShare(0.375), Set.of(), true, new Random(seed), new int[]{0, 0, 0});
            SpeciesMovesetRandomizer.SlotRole opener = null;
            for (SpeciesMovesetRandomizer.SlotRole role : roles) {
                if (role != SpeciesMovesetRandomizer.SlotRole.STATUS
                        && role != SpeciesMovesetRandomizer.SlotRole.WILDCARD) {
                    opener = role;
                    break;
                }
            }
            assertNotNull(opener, "seed " + seed + " produced no attacking slot at all");
            assertTrue(opener == SpeciesMovesetRandomizer.SlotRole.STAB
                            || opener == SpeciesMovesetRandomizer.SlotRole.FILLER,
                    "seed " + seed + " opened on " + opener + " rather than STAB or Normal filler");
            if (opener == SpeciesMovesetRandomizer.SlotRole.STAB) {
                stabOpeners++;
            }
        }
        double rate = (double) stabOpeners / trials;
        assertTrue(rate > 0.35 && rate < 0.55, "expected ~45% STAB openers, got " + rate);
    }

    @Test
    public void plainRandomNeverProducesTypeStructuredRoles() {
        // Type structure is gated on Prefer Same Type - a player who picked plain Random asked for
        // type-blind learnsets and must still get them.
        List<MoveLearnt> moves = learnsetOf(1, 5, 12, 20, 28, 35, 44, 52);
        SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                moves, 0, profileWithStatusShare(0.375), Set.of(), false, new Random(5L), new int[]{0, 0, 0});
        assertEquals(0, countRole(roles, SpeciesMovesetRandomizer.SlotRole.STAB));
        assertEquals(0, countRole(roles, SpeciesMovesetRandomizer.SlotRole.COVERAGE));
        assertTrue(countRole(roles, SpeciesMovesetRandomizer.SlotRole.ATTACK) > 0);
    }

    @Test
    public void followEvolutionsTailLeavesInheritedSlotsAlone() {
        List<MoveLearnt> moves = learnsetOf(1, 5, 12, 20, 28, 35, 44, 52);
        SpeciesMovesetRandomizer.SlotRole[] roles = SpeciesMovesetRandomizer.assignSlotRoles(
                moves, 5, profileWithStatusShare(0.375), Set.of(), true, new Random(9L), new int[]{0, 0, 0});
        for (int i = 0; i < 5; i++) {
            assertNull(roles[i], "inherited slot " + i + " should not be re-roled");
        }
    }

    // --- ability and priority coherence ---

    @Test
    public void anAbilityAffinityMoveIsBonusedAndOthersAreNot() {
        SpeciesLearnsetProfile punchy = new SpeciesLearnsetProfile(0.375, List.of(Type.FIRE), Set.of(),
                1.0, 0.5, mv -> mv.isPunchMove, 1.0);
        Move punch = damagingMove(75);
        punch.isPunchMove = true;
        assertEquals(Randomizer.SPECIES_ABILITY_AFFINITY_BONUS,
                SpeciesMovesetRandomizer.abilityAffinityWeight(punch, punchy));
        assertEquals(1.0, SpeciesMovesetRandomizer.abilityAffinityWeight(damagingMove(75), punchy));
    }

    @Test
    public void aSpeciesWithNoMatchingAbilityGetsNoAffinityBonus() {
        Move punch = damagingMove(75);
        punch.isPunchMove = true;
        assertEquals(1.0, SpeciesMovesetRandomizer.abilityAffinityWeight(punch, profileWithStatusShare(0.375)));
    }

    @Test
    public void priorityMovesAreBonusedOnlyForFastSpecies() {
        SpeciesLearnsetProfile fast = new SpeciesLearnsetProfile(0.375, List.of(Type.FIRE), Set.of(),
                1.0, 0.5, null, 3.5);
        Move quickAttack = damagingMove(40);
        quickAttack.priority = 1;
        assertEquals(3.5, SpeciesMovesetRandomizer.priorityWeight(quickAttack, fast));
        assertEquals(1.0, SpeciesMovesetRandomizer.priorityWeight(damagingMove(40), fast));
        // A slow species keeps priorityBonus at 1.0, so the same move gets no help.
        assertEquals(1.0, SpeciesMovesetRandomizer.priorityWeight(quickAttack, profileWithStatusShare(0.375)));
    }

    @Test
    public void theSoftFloorAlsoScalesWithTheSpeciesTier() {
        // floor = speciesCenterPower(50) * scale * 0.75; a 90 BP move clears the unscaled floor (82.5) but
        // not the 1.25-scaled one (103.1).
        Move mv = damagingMove(90);
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(mv, 50, 1.0));
        assertTrue(SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(mv, 50, 1.25) < 1.0);
    }
}
