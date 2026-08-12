package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.customnames.CustomNamesSet;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.gamedata.Move;
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
    protected final CopyUpEvolutionsHelper copyUpEvolutionsHelper;

    protected final Settings settings;
    protected final Random random;

    protected boolean changesMade;

    public Randomizer(RomHandler romHandler, Settings settings, Random random) {
        this.romHandler = romHandler;
        this.rSpecService = romHandler.getRestrictedSpeciesService();
        this.typeService = romHandler.getTypeService();
        this.copyUpEvolutionsHelper = new CopyUpEvolutionsHelper(romHandler::getSpeciesSetInclFormes);

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
     * to {@code weightFn}. Negative weights are clamped to 0; if every weight is 0 it falls back to
     * a uniform pick. Returns null for a null/empty list.
     * <p>
     * A single-element list is not special-cased: it still takes the weighted path and draws a
     * {@code nextDouble()}. Short-circuiting it would skip that draw and desynchronise every
     * subsequent value in a seeded run.
     * <p>
     * {@code weightFn} must be pure and must never draw from {@code random} - each weight is
     * computed once here and reused for the selection pass.
     */
    protected Move weightedPick(List<Move> candidates, ToDoubleFunction<Move> weightFn) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        // Evaluate each weight ONCE into a scratch array (weightFn is a pure scoring function - it never touches
        // random - so a single pass gives bit-identical results to recomputing it in the selection loop, but at
        // half the calls; this runs per candidate per slot per mon, so the saving is real in the hot path).
        int n = candidates.size();
        double[] weights = new double[n];
        double total = 0;
        int lastPositive = -1;
        for (int i = 0; i < n; i++) {
            double w = Math.max(0.0, weightFn.applyAsDouble(candidates.get(i)));
            weights[i] = w;
            total += w;
            if (w > 0) {
                lastPositive = i;
            }
        }
        if (total <= 0) {
            return candidates.get(random.nextInt(n));
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < n; i++) {
            r -= weights[i];
            // Skipping zero-weight candidates matters at the edges: nextDouble() can return exactly 0.0, which
            // would otherwise hand back the first candidate whatever its weight - e.g. a status move sitting in
            // a boss STAB pool at weight 0, in an attacking slot.
            if (weights[i] > 0 && r <= 0) {
                return candidates.get(i);
            }
        }
        // Reached only if rounding leaves r above 0 after the whole list; total > 0 guarantees a positive weight.
        return candidates.get(lastPositive);
    }

    // Shared BP tier edges: moves fall into fixed effective-power (power * hitCount, 0 for status) bands.
    protected static final double TIER_LOW_MAX_BP  = 60.0;
    protected static final double TIER_MID_MAX_BP  = 80.0;

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
