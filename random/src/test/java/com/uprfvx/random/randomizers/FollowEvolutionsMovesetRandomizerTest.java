package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.cueh.CopyUpEvolutionsHelper;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ROM-driven validation for the species learnset "Follow Evolutions" setting.
 * <p>
 * Named to match the {@code *Randomizer*Test} filter so it runs under the {@code testROMs} Gradle task. Does NOT
 * extend {@link RandomizerTest} - loads a ROM in {@code roms/} itself, by content, and skips itself if none are
 * loadable (same pattern as {@link SensibleMovesetsRandomizerTest}).
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*FollowEvolutions*" }</pre>
 */
public class FollowEvolutionsMovesetRandomizerTest {

    private static final long MAX_ROM_BYTES = 6L * 1024 * 1024 * 1024;

    @Test
    public void evolvedSpeciesInheritsPreEvolutionsMovesUpToItsOwnSlotCount() {
        RomHandler rom = loadAnyRom();
        List<Species[]> evoPairs = collectEvoPairs(rom);

        Settings on = new Settings();
        on.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        on.setMovesetsFollowEvolutions(true);
        // Sensible Movesets deliberately left off/default here - Follow Evolutions must not require it.
        // See sensibleMovesetsOnDoesNotBreakFollowEvolutionsInheritance() for the "both on" case.
        new SpeciesMovesetRandomizer(rom, on, new Random(20260726L)).randomizeMovesLearnt();

        assertFollowEvolutionsInvariant(rom, evoPairs);
    }

    @Test
    public void sensibleMovesetsOnDoesNotBreakFollowEvolutionsInheritance() {
        RomHandler rom = loadAnyRom();
        List<Species[]> evoPairs = collectEvoPairs(rom);

        // Follow Evolutions and Sensible Movesets are independent toggles - the inheritance copy must hold
        // exactly the same way whether or not Sensible Movesets' power-curve weighting is also on, since that
        // weighting only ever applies to independently-randomized slots (basic species, and any leftover slots
        // beyond what a pre-evolution could fill), never to the inherited slots themselves.
        Settings on = new Settings();
        on.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        on.setMovesetsFollowEvolutions(true);
        on.setSensibleMovesets(true);
        new SpeciesMovesetRandomizer(rom, on, new Random(20260726L)).randomizeMovesLearnt();

        assertFollowEvolutionsInvariant(rom, evoPairs);
    }

    /**
     * Same (from, to) pairs the production code walks (copySplitEvos=false, matching
     * SpeciesMovesetRandomizer.randomizeMovesLearnt's call), collected before randomizing so the test's
     * definition of "which pairs should inherit" can never drift from the implementation's.
     */
    private List<Species[]> collectEvoPairs(RomHandler rom) {
        List<Species[]> evoPairs = new ArrayList<>();
        CopyUpEvolutionsHelper<Species> helper = new CopyUpEvolutionsHelper<>(rom.getSpeciesSetInclFormes());
        helper.apply(true, false, pk -> {
        }, (evFrom, evTo, isFinalEvo) -> evoPairs.add(new Species[] { evFrom, evTo }), null, pk -> {
        });
        assumeTrue(!evoPairs.isEmpty(), "No evolution pairs found on this ROM");
        return evoPairs;
    }

    private void assertFollowEvolutionsInvariant(RomHandler rom, List<Species[]> evoPairs) {
        Map<Integer, List<MoveLearnt>> movesets = rom.getMovesLearnt();
        int pairsChecked = 0;
        for (Species[] pair : evoPairs) {
            Species evFrom = pair[0];
            Species evTo = pair[1];
            if (evFrom.isEssentiallyCosmetic() || evTo.isEssentiallyCosmetic()) {
                continue; // cosmetic formes copy their base forme's moveset instead - not this invariant
            }
            List<MoveLearnt> fromMoves = movesets.get(evFrom.getNumber());
            List<MoveLearnt> toMoves = movesets.get(evTo.getNumber());
            if (fromMoves == null || toMoves == null) {
                continue;
            }
            pairsChecked++;

            int copyCount = Math.min(fromMoves.size(), toMoves.size());
            for (int i = 0; i < copyCount; i++) {
                assertEquals(fromMoves.get(i).move, toMoves.get(i).move,
                        evTo.getName() + " slot " + i + " should inherit " + evFrom.getName()
                                + "'s move at the same index (copy bound=" + copyCount + ")");
            }
            for (MoveLearnt ml : toMoves) {
                assertTrue(ml.move > 0, evTo.getName() + " has an unfilled move slot after Follow Evolutions");
            }
        }
        assumeTrue(pairsChecked > 0, "No non-cosmetic evolution pairs with resolvable movesets on this ROM");
        System.out.println("Follow Evolutions: verified " + pairsChecked + " evolution pair(s)");
    }

    @Test
    public void followEvolutionsOffStillProducesOneMovePerSlot() {
        RomHandler rom = loadAnyRom();

        Settings off = new Settings();
        off.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        off.setMovesetsFollowEvolutions(false);
        new SpeciesMovesetRandomizer(rom, off, new Random(20260726L)).randomizeMovesLearnt();

        for (Map.Entry<Integer, List<MoveLearnt>> entry : rom.getMovesLearnt().entrySet()) {
            for (MoveLearnt ml : entry.getValue()) {
                assertTrue(ml.move > 0, "Species " + entry.getKey() + " has an unfilled move slot");
            }
        }
    }

    private RomHandler loadAnyRom() {
        String romsDir = System.getProperty("romsPath");
        assumeTrue(romsDir != null, "romsPath not set");
        File dir = new File(romsDir);
        assumeTrue(dir.isDirectory(), "roms dir missing: " + romsDir);
        File[] files = dir.listFiles();
        assumeTrue(files != null && files.length > 0, "roms dir empty: " + romsDir);
        List<File> candidates = new ArrayList<>(Arrays.asList(files));
        candidates.sort(Comparator.comparingLong(File::length));

        RomHandler rom = null;
        for (File f : candidates) {
            if (!f.isFile() || f.getName().equalsIgnoreCase("readme.txt") || f.length() > MAX_ROM_BYTES) {
                continue;
            }
            rom = tryLoad(f.getAbsolutePath());
            if (rom != null) {
                break;
            }
        }
        assumeTrue(rom != null, "No loadable ROM found in " + romsDir);
        return rom;
    }

    private RomHandler tryLoad(String path) {
        for (Generation gen : new HashSet<>(Generation.GAME_TO_GENERATION.values())) {
            try {
                RomHandler.Factory factory = gen.createFactory();
                if (factory.isLoadable(path)) {
                    RomHandler rh = factory.create();
                    rh.loadRom(path);
                    // Sets restrictions to not restrict, same as RandomizerTest.loadROM.
                    rh.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());
                    return rh;
                }
            } catch (Throwable e) {
                System.out.println("  [gen " + gen.getNumber() + " load error for " + new File(path).getName()
                        + "]: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return null;
    }
}
