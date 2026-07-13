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

    // Level->power-tier soft bias, shared by the trainer (Better Movesets) and species (power-curve)
    // moveset randomizers. Moves fall into fixed BP tiers that "unlock" with level; below a tier's
    // unlock level its moves get a soft, shrinking weight so a low-level mon still occasionally rolls
    // one, rather than the old flat level*3 cutoff. Effective power is power * hitCount (0 for status).
    protected static final double TIER_LOW_MAX_BP  = 60.0;  // <=60  = low  tier (always available)
    protected static final double TIER_MID_MAX_BP  = 80.0;  // 61-80 = mid  tier
    protected static final int    TIER_MID_UNLOCK  = 20;    // mid power starts appearing here
    protected static final int    TIER_HIGH_UNLOCK = 35;    // high power (81+) starts appearing here
    protected static final double TIER_SOFTNESS    = 0.8;   // per-level falloff below unlock (0.8^10 ~= 0.11)

    /**
     * Soft level-gate weight in (0,1] for a move of the given effective power on a mon of the given
     * level: 1.0 once the mon's level reaches the move's tier unlock level, and a soft, shrinking
     * fraction below it (so an under-level mon can still occasionally roll up a tier). Multiply an
     * existing power-based selection weight by this to bias picks toward level-appropriate power.
     */
    protected static double levelTierWeight(int level, double effectivePower) {
        int unlock;
        if (effectivePower <= TIER_LOW_MAX_BP) {
            unlock = 0;                 // low tier: always available
        } else if (effectivePower <= TIER_MID_MAX_BP) {
            unlock = TIER_MID_UNLOCK;   // mid tier
        } else {
            unlock = TIER_HIGH_UNLOCK;  // high tier
        }
        if (level >= unlock) {
            return 1.0;
        }
        return Math.pow(TIER_SOFTNESS, unlock - level);
    }

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
