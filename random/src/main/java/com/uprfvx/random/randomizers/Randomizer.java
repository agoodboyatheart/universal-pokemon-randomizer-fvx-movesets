package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.random.customnames.CustomNamesSet;
import com.uprfvx.random.exceptions.RandomizationException;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.cueh.CopyUpEvolutionsHelper;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.TypeService;

import java.io.IOException;
import java.util.Random;

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

    /**
     * Species already placed by an earlier randomizer in this same game-randomization pass (e.g.
     * Starters before Static, Static before Trade). Empty by default (no-op) so existing callers of
     * any randomizer are unaffected unless {@link #setExternallyClaimedSpecies} is explicitly called.
     */
    protected SpeciesSet externallyClaimedSpecies = new SpeciesSet();

    /**
     * Sets the species this randomizer must avoid picking, because they were already placed by an
     * earlier randomizer in the same pass. Pass {@code null} to clear back to "no exclusion."
     */
    public void setExternallyClaimedSpecies(SpeciesSet claimed) {
        this.externallyClaimedSpecies = claimed == null ? new SpeciesSet() : claimed;
    }

    /**
     * Removes {@link #externallyClaimedSpecies} from {@code pool} in place, unless doing so would
     * leave {@code pool} empty - in which case {@code pool} is left untouched (duplicates allowed
     * for that pick rather than crashing or hanging).
     */
    protected void excludeClaimedWithFallback(SpeciesSet pool) {
        if (externallyClaimedSpecies.isEmpty() || pool.isEmpty()) {
            return;
        }
        SpeciesSet withoutClaimed = new SpeciesSet(pool);
        withoutClaimed.removeAll(externallyClaimedSpecies);
        if (!withoutClaimed.isEmpty()) {
            pool.retainAll(withoutClaimed);
        }
    }

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
