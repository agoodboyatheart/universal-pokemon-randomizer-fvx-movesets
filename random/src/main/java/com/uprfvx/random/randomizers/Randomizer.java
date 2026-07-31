package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.customnames.CustomNamesSet;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.cueh.CopyUpEvolutionsHelper;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.TypeService;

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.function.ToDoubleFunction;

/**
 * An abstract superclass for all randomizers acting on a {@link RomHandler}.
 */
public abstract class Randomizer {

    protected final RomHandler romHandler;
    protected final RestrictedSpeciesService rSpecService;
    protected final TypeService typeService;
    protected final CopyUpEvolutionsHelper<Species> copyUpEvolutionsHelper;

    protected final Settings settings;
    protected final Random random;

    protected boolean changesMade;

    public Randomizer(RomHandler romHandler, Settings settings, Random random) {
        this.romHandler = romHandler;
        this.rSpecService = romHandler.getRestrictedSpeciesService();
        this.typeService = romHandler.getTypeService();
        this.copyUpEvolutionsHelper = new CopyUpEvolutionsHelper<>(romHandler::getSpeciesSetInclFormes);

        this.settings = settings;
        this.random = random;
    }

    public boolean isChangesMade() {
        return changesMade;
    }

    protected int applyPercentageLevelModifier(int level, int percentageLevelModifier) {
        int modifiedLevel = (int) Math.round(level * (1 + percentageLevelModifier / 100.0));
        return Math.clamp(modifiedLevel, 1, 100);
    }

    /**
     * Picks one move from {@code candidates} at random, with each move's probability proportional
     * to {@code weightFn}. Negative weights are clamped to 0; if every weight is 0 (or the list is
     * a single element) it falls back to a uniform pick. Returns null for a null/empty list.
     */
    protected Move weightedPick(List<Move> candidates, ToDoubleFunction<Move> weightFn) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        double total = 0;
        for (Move mv : candidates) {
            total += Math.max(0.0, weightFn.applyAsDouble(mv));
        }
        if (total <= 0) {
            return candidates.get(random.nextInt(candidates.size()));
        }
        double r = random.nextDouble() * total;
        for (Move mv : candidates) {
            r -= Math.max(0.0, weightFn.applyAsDouble(mv));
            if (r <= 0) {
                return mv;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    // Shared BP tier edges: moves fall into fixed effective-power (power * hitCount, 0 for status) bands.
    protected static final double TIER_LOW_MAX_BP  = 60.0;
    protected static final double TIER_MID_MAX_BP  = 80.0;

    // The effective power (power * hitCount) expected of a damaging move at a given level - BASE at Lv1 rising
    // linearly to MAX by the saturation level. Shared by both moveset randomizers so trainer and species pacing
    // are calibrated against the same curve. Tuning knobs.
    protected static final double LEVEL_POWER_BASE = 45.0;
    protected static final double LEVEL_POWER_MAX = 95.0;
    protected static final double LEVEL_POWER_SATURATION_LEVEL = 50.0;

    protected static double centerPower(int level) {
        double t = Math.min(1.0, level / LEVEL_POWER_SATURATION_LEVEL);
        return LEVEL_POWER_BASE + (LEVEL_POWER_MAX - LEVEL_POWER_BASE) * t;
    }

    // A move is culled above centerPower * a level-scaled ceiling multiplier: tight at low level (a hard
    // ~60 cap), widening at high level so premier nukes (Draco Meteor/Overheat 130) survive and only
    // 150+ self-KO/gimmick moves fall outside. Floored at TIER_LOW_MAX_BP so a low-level pool is never
    // tighter than before. Mirrors Better Movesets' trainer-side power-band ceiling (2026-07-29: species
    // learnsets now use the identical mechanism - see SpeciesMovesetRandomizer.applySpeciesPowerCeiling).
    // Tuning knobs.
    protected static final double POWER_CEILING_MULTIPLIER_LOW = 0.95;
    protected static final double POWER_CEILING_MULTIPLIER_HIGH = 1.63;

    protected static double powerCeiling(int level) {
        double t = Math.min(1.0, level / LEVEL_POWER_SATURATION_LEVEL);
        double mult = POWER_CEILING_MULTIPLIER_LOW + (POWER_CEILING_MULTIPLIER_HIGH - POWER_CEILING_MULTIPLIER_LOW) * t;
        return Math.max(TIER_LOW_MAX_BP, centerPower(level) * mult);
    }

    // Species-quality scaling of the level curve - the species-side counterpart of the trainer path's
    // Boss/Regular tier split. Vanilla moves a species' mean attacking power from 60.9 (BST<300) to 81.1
    // (BST 600+) and its ceiling from ~100 to ~127, so the whole curve shifts rather than just its top.
    // Scale multiplies the ceiling including its TIER_LOW_MAX_BP floor, so a frail species genuinely gets
    // a tighter low-level cap than a pseudo-legendary does. Tuning knobs.
    protected static double centerPower(int level, double speciesPowerScale) {
        return centerPower(level) * speciesPowerScale;
    }

    protected static double powerCeiling(int level, double speciesPowerScale) {
        return powerCeiling(level) * speciesPowerScale;
    }

    // Below centerPower * this fraction a move starts losing pick weight. Shared soft-floor fraction and
    // falloff exponent - species has no Boss/Regular tier split, so it always uses this "Regular"-style
    // gentle exponent (mirrors Better Movesets' trainer path). Non-final so a calibration harness can
    // sweep it.
    protected static double POWER_FLOOR_FRACTION = 0.75;
    protected static final double POWER_FLOOR_EXPONENT_REGULAR = 0.8;

    // Vanilla leans harder on a species' stronger attacking stat than its raw Atk/(Atk+SpA) ratio implies -
    // an Atk 110 / SpA 70 mon is r=0.61 but its measured bucket runs 79.5% physical - so the ratio is
    // amplified before it is used. The bonus is a pick-stage weight rather than a pool filter: filtering
    // the category out of an already type-narrowed pool is what starved the power ceiling and let a level-1
    // Minun roll Volt Tackle (species-power-banding-alignment.md). Tuning knobs.
    protected static double SPECIES_CATEGORY_LEAN_AMPLIFIER = 2.2;
    protected static double SPECIES_CATEGORY_BONUS = 6.0;

    // Inputs to speciesPowerScale. The stand-alone bonus is deliberately larger than the legendary one:
    // vanilla compensates a species that never evolves far more strongly (mean power 73.1 vs 63.1) than it
    // does one that merely has a high BST, and that effect is measurably stronger than evolution stage.
    // Learnset composition, all read off the structure report's measured Gen 6/7 tables. The status share
    // is sampled per species from a distribution rather than fixed: vanilla's own spread runs p10 21% to
    // p90 56%, and collapsing that to a constant would make every seed's learnsets feel alike.
    protected static double SPECIES_STATUS_SHARE_MEAN = 0.375;
    protected static double SPECIES_STATUS_SHARE_SIGMA = 0.13;
    protected static double SPECIES_STATUS_SHARE_MIN = 0.08;
    protected static double SPECIES_STATUS_SHARE_MAX = 0.72;

    // Status is densest at levels 2-10 and thinnest above 50 - vanilla stops handing out utility at the top
    // of a learnset and just gives weapons. Indexed by SPECIES_LEVEL_BANDS.
    protected static final int[] SPECIES_LEVEL_BAND_EDGES = {1, 10, 20, 30, 40, 50, Integer.MAX_VALUE};
    protected static final double[] SPECIES_STATUS_BAND_MULTIPLIER = {1.02, 1.28, 0.93, 0.98, 1.05, 0.88, 0.80};

    // STAB share of attacking slots climbs with level: type identity is delivered early, type power late.
    protected static double SPECIES_STAB_SHARE_BASE = 0.48;
    protected static double SPECIES_STAB_SHARE_RANGE = 0.13;
    protected static double SPECIES_SECONDARY_STAB_CHANCE = 0.444;

    // Off-type Normal filler is front-loaded in vanilla (32% of early attacking slots, ~19% late).
    protected static double SPECIES_GENERIC_FILLER_EARLY = 0.30;
    protected static double SPECIES_GENERIC_FILLER_LATE = 0.15;

    // Coverage moves are the weaker option in vanilla (63.0 BP vs 69.4 for STAB), the reverse of what a
    // boss trainer's coverage slot wants.
    protected static double SPECIES_COVERAGE_CEILING_FACTOR = 0.93;

    // Slots left entirely unstructured - the surprise valve, mirroring Better Movesets' wildcard slot.
    protected static double SPECIES_WILDCARD_SHARE = 0.15;

    // Ability coherence, and priority moves going to species fast enough to use them.
    protected static double SPECIES_ABILITY_AFFINITY_BONUS = 12.0;
    protected static double SPECIES_PRIORITY_SPEED_FLOOR = 40.0;
    protected static double SPECIES_PRIORITY_SPEED_RANGE = 80.0;
    protected static double SPECIES_PRIORITY_BONUS_MAX = 2.5;

    protected static int speciesLevelBand(int level) {
        for (int b = 0; b < SPECIES_LEVEL_BAND_EDGES.length; b++) {
            if (level <= SPECIES_LEVEL_BAND_EDGES[b]) {
                return b;
            }
        }
        return SPECIES_LEVEL_BAND_EDGES.length - 1;
    }

    protected static final double SPECIES_POWER_BST_FLOOR = 300.0;
    protected static final double SPECIES_POWER_BST_CEILING = 600.0;
    protected static double SPECIES_POWER_SCALE_MIN = 0.90;
    protected static double SPECIES_POWER_SCALE_MAX = 1.25;
    protected static double SPECIES_POWER_SCALE_BST_RANGE = 0.30;
    protected static double SPECIES_POWER_SCALE_STANDALONE_BONUS = 0.08;
    protected static double SPECIES_POWER_SCALE_LEGENDARY_BONUS = 0.06;

    protected CustomNamesSet getCustomNames() {
        // This is not in line with how most /data resources are loaded for randomization.
        // Am not certain whether this or the other ways are more elegant, might be up
        // for future unification. This works for now though.
        try {
            return CustomNamesSet.readNamesFromFile();
        } catch (IOException e) {
            throw new RandomizationException("Could not read custom names from file.");
        }
    }
}
