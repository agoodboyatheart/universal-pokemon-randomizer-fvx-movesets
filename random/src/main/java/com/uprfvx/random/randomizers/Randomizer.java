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

    // Below centerPower * this fraction a move starts losing pick weight. Shared soft-floor fraction and
    // falloff exponent - species has no Boss/Regular tier split, so it always uses this "Regular"-style
    // gentle exponent (mirrors Better Movesets' trainer path). Non-final so a calibration harness can
    // sweep it.
    protected static double POWER_FLOOR_FRACTION = 0.75;
    protected static final double POWER_FLOOR_EXPONENT_REGULAR = 0.8;

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
