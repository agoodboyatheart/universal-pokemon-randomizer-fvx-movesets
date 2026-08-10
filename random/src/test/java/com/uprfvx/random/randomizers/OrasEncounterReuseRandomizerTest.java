package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Encounter;
import com.uprfvx.romio.gamedata.EncounterArea;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ROM-level tests for the ORAS DexNav encounter path, which reuses one replacement Species for every
 * Encounter of a Species in a map and so must pick one that is legal at the lowest level any of them
 * has. That path - randomizeMapORAS, and the lowestLevels map this branch added to it - is reached
 * only through WildPokemonZoneMod.NONE on an ORAS ROM, which is why the branch's other new test,
 * WildEncounterRandomizerTest.doNotUsePrematureEvosWorksWithZoneMapping, does not cover it: that one
 * iterates the four zone-mapping modes and never uses NONE.
 * <br><br>
 * This class deliberately exists only on fix-oras-encounter-reuse. The equivalent assertion already
 * lives on master as WildEncounterRandomizerTest.doNotUsePrematureEvosWorks, and it fails there on
 * both ORAS ROMs - that failure is in master's tripwire baseline. Because the tripwire subtracts the
 * union of every branch's known failures, a merge that lost this fix would put those two failures
 * back on local-combined-build and they would be absorbed as already-known rather than reported. A
 * class master has no baseline for cannot be absorbed that way, so a regression here reads as NEW.
 */
public class OrasEncounterReuseRandomizerTest extends RandomizerTest {

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void prematureEvosNotUsedInDexNavMode(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.isORAS());

        Map<Encounter, Species> originalSpecies = snapshotSpecies();

        randomizeDexNavEncounters();

        int replaced = 0;
        for (EncounterArea area : romHandler.getEncounters(true)) {
            for (Encounter enc : area) {
                if (originalSpecies.get(enc) != enc.getSpecies()) {
                    replaced++;
                }
                for (Evolution evo : enc.getSpecies().getEvolutionsTo()) {
                    assertTrue(evo.getEstimatedEvoLvl() <= enc.getLevel(),
                            area.getDisplayName() + ": " + enc.getSpecies().getFullName() + " at level "
                                    + enc.getLevel() + " needs level " + evo.getEstimatedEvoLvl()
                                    + " for " + evo);
                }
            }
        }

        // Without this the assertions above would also hold for a run that replaced nothing at all.
        assertTrue(replaced > 0, "No encounter was replaced - the ORAS DexNav path did not run");
    }

    private void randomizeDexNavEncounters() {
        Settings settings = new Settings();
        settings.setRandomizeWildPokemon(true);
        settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NONE);
        settings.setUseTimeBasedEncounters(true);
        settings.setAllowWildAltFormes(true);
        settings.setBanIrregularAltFormes(true);
        settings.setBlockWildLegendaries(false);
        settings.setBanPrematureEvos(true);

        new WildEncounterRandomizer(romHandler, settings, RND).randomizeEncounters();
    }

    /**
     * getEncounters caches and returns the same list for the life of a prepare(), so the Encounter
     * objects here are the ones the randomizer mutates in place - identity is stable across the run.
     */
    private Map<Encounter, Species> snapshotSpecies() {
        Map<Encounter, Species> snapshot = new IdentityHashMap<>();
        for (EncounterArea area : romHandler.getEncounters(true)) {
            for (Encounter enc : area) {
                snapshot.put(enc, enc.getSpecies());
            }
        }
        return snapshot;
    }
}
