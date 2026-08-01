package com.uprfvx.random.randomizers;

import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The per-species shape decisions a learnset is built against, sampled once before any slot is filled -
 * the species-side counterpart of {@code TrainerMovesetRandomizer}'s attacker profile.
 * <p>
 * Deliberately sampled rather than derived. Vanilla's status share is a distribution, not a constant
 * (median 36%, p10 21%, p90 56%), and reproducing that spread is what keeps one seed's Golem from
 * playing like every other seed's Golem.
 *
 * @param statusShare    target fraction of level-up slots that should be status moves
 * @param stabTypes      the species' own types, in the order STAB slots should prefer them
 * @param coverageTypes  the committed off-type attacking palette; every coverage slot draws from this,
 *                       which is what stops a learnset spanning eight unrelated types
 * @param powerScale     multiplier on the level power curve for this species' quality tier
 * @param categoryLean   probability that any given attacking slot prefers a physical move
 * @param abilityAffinity move class this species' ability keys off, or null if it has none
 * @param priorityBonus  weight multiplier for priority moves, scaled by base Speed
 */
public record SpeciesLearnsetProfile(
        double statusShare,
        List<Type> stabTypes,
        Set<Type> coverageTypes,
        double powerScale,
        double categoryLean,
        Predicate<Move> abilityAffinity,
        double priorityBonus) {

    // Cumulative distribution of distinct attacking types per species, read off the structure report's
    // histogram (1:35, 2:101, 3:222, 4:217, 5:133, 6:64, 7+:35 out of 807).
    private static final double[] DISTINCT_TYPE_CUMULATIVE =
            {0.0434, 0.1686, 0.4437, 0.7126, 0.8774, 0.9567, 1.0};

    // Bite has no move flag of its own, unlike punch and sound, so Strong Jaw needs the list spelled out.
    private static final Set<Integer> BITE_MOVES = Set.of(MoveIDs.bite, MoveIDs.crunch, MoveIDs.fireFang,
            MoveIDs.iceFang, MoveIDs.thunderFang, MoveIDs.poisonFang, MoveIDs.hyperFang, MoveIDs.psychicFangs);

    /**
     * Abilities whose learnset signal the structure report actually confirmed. Every entry keys off a
     * narrow, explicitly-flagged move class - the report found no signal at all where an ability keys off
     * a broad statistical property instead (Sheer Force, Technician, Prankster, Moxie all sit at ~1.0x
     * lift, Serene Grace is inverted at 0.43x, and Levitate does not suppress Ground moves), so those are
     * deliberately absent rather than overlooked.
     */
    private static final Map<Integer, Predicate<Move>> ABILITY_AFFINITIES = Map.ofEntries(
            Map.entry(AbilityIDs.ironFist, mv -> mv.isPunchMove),
            Map.entry(AbilityIDs.rockHead, mv -> mv.recoilPercent > 0),
            Map.entry(AbilityIDs.reckless, mv -> mv.recoilPercent > 0),
            Map.entry(AbilityIDs.skillLink, mv -> mv.hitCount > 1),
            Map.entry(AbilityIDs.soundproof, mv -> mv.isSoundMove),
            Map.entry(AbilityIDs.strongJaw, mv -> BITE_MOVES.contains(mv.number)),
            Map.entry(AbilityIDs.sturdy, mv -> mv.number == MoveIDs.explosion || mv.number == MoveIDs.selfDestruct),
            Map.entry(AbilityIDs.snowWarning, mv -> mv.number == MoveIDs.hail),
            Map.entry(AbilityIDs.sandStream, mv -> mv.number == MoveIDs.sandstorm),
            Map.entry(AbilityIDs.sandRush, mv -> mv.number == MoveIDs.sandstorm),
            Map.entry(AbilityIDs.drizzle, mv -> mv.number == MoveIDs.rainDance),
            Map.entry(AbilityIDs.swiftSwim, mv -> mv.number == MoveIDs.rainDance),
            Map.entry(AbilityIDs.drought, mv -> mv.number == MoveIDs.sunnyDay),
            // Beneficiaries are taught the weather move so they can enable themselves, exactly as setters are.
            Map.entry(AbilityIDs.chlorophyll, mv -> mv.number == MoveIDs.sunnyDay),
            Map.entry(AbilityIDs.solarPower, mv -> mv.number == MoveIDs.sunnyDay || mv.number == MoveIDs.solarBeam));

    static SpeciesLearnsetProfile of(Species pkmn, List<Type> allTypes, int generation, Random random) {
        double statusShare = Math.clamp(
                Randomizer.SPECIES_STATUS_SHARE_MEAN + random.nextGaussian() * Randomizer.SPECIES_STATUS_SHARE_SIGMA,
                Randomizer.SPECIES_STATUS_SHARE_MIN, Randomizer.SPECIES_STATUS_SHARE_MAX);

        List<Type> stabTypes = new ArrayList<>();
        Type primary = pkmn.getPrimaryType(false);
        Type secondary = pkmn.getSecondaryType(false);
        if (primary != null) {
            stabTypes.add(primary);
        }
        if (secondary != null && secondary != primary) {
            stabTypes.add(secondary);
        }

        return new SpeciesLearnsetProfile(statusShare, stabTypes,
                sampleCoverageTypes(stabTypes, allTypes, random),
                SpeciesMovesetRandomizer.speciesPowerScale(pkmn),
                SpeciesMovesetRandomizer.speciesCategoryLean(pkmn.getBaseStats().getAttackSpecialAttackRatio()),
                abilityAffinityFor(pkmn, generation),
                priorityBonusFor(pkmn));
    }

    // Gen 1-2 have no abilities at all.
    private static Predicate<Move> abilityAffinityFor(Species pkmn, int generation) {
        if (generation < 3) {
            return null;
        }
        List<Predicate<Move>> matches = new ArrayList<>();
        for (int ability : new int[] {pkmn.getAbility1(), pkmn.getAbility2(), pkmn.getAbility3()}) {
            Predicate<Move> affinity = ABILITY_AFFINITIES.get(ability);
            if (affinity != null) {
                matches.add(affinity);
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        return mv -> matches.stream().anyMatch(p -> p.test(mv));
    }

    // Vanilla hands priority moves to species that already outspeed things rather than using them to
    // compensate slow ones - presence climbs from 18.6% to 61.1% across the Speed range.
    private static double priorityBonusFor(Species pkmn) {
        double t = Math.clamp((pkmn.getBaseStats().getSpeed() - Randomizer.SPECIES_PRIORITY_SPEED_FLOOR)
                / Randomizer.SPECIES_PRIORITY_SPEED_RANGE, 0.0, 1.0);
        return 1.0 + Randomizer.SPECIES_PRIORITY_BONUS_MAX * t;
    }

    // The species' STAB types count toward the distinct-type target, so a dual-type gets a narrower
    // off-type palette than a mono-type - which is how vanilla keeps both at the same ~3.8 total.
    private static Set<Type> sampleCoverageTypes(List<Type> stabTypes, List<Type> allTypes, Random random) {
        int target = sampleDistinctTypeCount(random);
        int wanted = Math.max(0, target - stabTypes.size());
        Set<Type> coverage = new LinkedHashSet<>();
        if (wanted == 0 || allTypes.isEmpty()) {
            return coverage;
        }
        List<Type> candidates = new ArrayList<>(allTypes);
        candidates.removeAll(stabTypes);
        Collections.shuffle(candidates, random);
        for (int i = 0; i < Math.min(wanted, candidates.size()); i++) {
            coverage.add(candidates.get(i));
        }
        return coverage;
    }

    private static int sampleDistinctTypeCount(Random random) {
        double roll = random.nextDouble();
        for (int i = 0; i < DISTINCT_TYPE_CUMULATIVE.length; i++) {
            if (roll <= DISTINCT_TYPE_CUMULATIVE[i]) {
                return i + 1;
            }
        }
        return DISTINCT_TYPE_CUMULATIVE.length;
    }

    /** Whichever of the species' types this STAB slot should use; secondary gets a near-even share. */
    Type stabTypeFor(Random random) {
        if (stabTypes.isEmpty()) {
            return null;
        }
        if (stabTypes.size() > 1 && random.nextDouble() < Randomizer.SPECIES_SECONDARY_STAB_CHANCE) {
            return stabTypes.get(1);
        }
        return stabTypes.get(0);
    }
}
