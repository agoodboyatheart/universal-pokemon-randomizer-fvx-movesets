package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
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
import java.util.HashMap;
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
        CopyUpEvolutionsHelper helper = new CopyUpEvolutionsHelper(rom.getSpeciesSetInclFormes());
        helper.apply(new CopyUpEvolutionsHelper.Options
                .Builder(pk -> {
                }, (evFrom, evTo, isFinalEvo) -> evoPairs.add(new Species[] { evFrom, evTo }))
                .build());
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

    /**
     * The inherited-moveset repair, on real data. An evolved species whose slot count is at most its
     * pre-evolution's never runs randomizeMovesLearntForSpecies, so before the repair existed its STAB
     * guarantee was never evaluated against its own types at all - real Pearl produced a Scizor
     * (Bug/Steel) holding nothing but the Flying moves Scyther (Bug/Flying) drew. Inheritance is
     * therefore no longer a strict prefix copy under Sensible Movesets + Prefer Same Type: at most one
     * slot per evolved species may be overwritten, and only when the invariant would otherwise fail.
     */
    @Test
    public void preferSameTypeGivesEveryEvolvedSpeciesItsOwnStab() {
        // Prefers a DS ROM: the failure this guards against was found on real Pearl (Scizor, Bug/Steel,
        // inheriting Bug/Flying Scyther's whole 16-slot learnset), and gen 1-3 have far fewer equal-length
        // evolution pairs to exercise it with. Falls back to any ROM so the test still runs without one.
        RomHandler rom = loadDsRomOrAny();
        List<Species[]> evoPairs = collectEvoPairs(rom);
        Map<Integer, List<MoveLearnt>> before = new HashMap<>();
        for (Map.Entry<Integer, List<MoveLearnt>> entry : rom.getMovesLearnt().entrySet()) {
            before.put(entry.getKey(), entry.getValue());
        }

        Settings on = new Settings();
        on.setMovesetsMod(Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE);
        on.setMovesetsFollowEvolutions(true);
        on.setSensibleMovesets(true);
        new SpeciesMovesetRandomizer(rom, on, new Random(20260807L)).randomizeMovesLearnt();

        List<Move> allMoves = rom.getMoves();
        Map<Integer, List<MoveLearnt>> movesets = rom.getMovesLearnt();
        int pairsChecked = 0;
        int repaired = 0;
        int stabAboveFloor = 0;
        List<String> noPoolSpecies = new ArrayList<>();
        for (Species[] pair : evoPairs) {
            Species evFrom = pair[0];
            Species evTo = pair[1];
            if (evFrom.isEssentiallyCosmetic() || evTo.isEssentiallyCosmetic()) {
                continue;
            }
            List<MoveLearnt> fromMoves = movesets.get(evFrom.getNumber());
            List<MoveLearnt> toMoves = movesets.get(evTo.getNumber());
            if (fromMoves == null || toMoves == null) {
                continue;
            }
            pairsChecked++;

            int copyCount = Math.min(fromMoves.size(), toMoves.size());
            int differing = 0;
            for (int i = 0; i < copyCount; i++) {
                if (fromMoves.get(i).move != toMoves.get(i).move) {
                    differing++;
                }
            }
            assertTrue(differing <= 1, evTo.getName() + " diverges from " + evFrom.getName() + " in "
                    + differing + " inherited slots - the repair may only ever overwrite one");
            repaired += differing;

            if (!hasOwnTypeDamagingMove(evTo, allMoves)) {
                // Gen 1 Dragon is the real case: the ROM itself offers no selectable Dragon damaging move,
                // so no repair - and no amount of re-picking on the normal path either - can give Dragonair
                // a Dragon move. The invariant is "STAB whenever the ROM has any to give".
                noPoolSpecies.add(evTo.getName());
                continue;
            }
            int firstStabLevel = firstOwnTypeDamagingLevel(evTo, toMoves, allMoves);
            assertTrue(firstStabLevel != Integer.MAX_VALUE, evTo.getName() + " (" + evTo.getPrimaryType(false)
                    + "/" + evTo.getSecondaryType(false) + ") inherited " + evFrom.getName()
                    + "'s moves and has no move of its own type anywhere");
            if (firstStabLevel > Randomizer.SPECIES_STAB_FLOOR_LEVEL) {
                stabAboveFloor++;
            }
        }
        assumeTrue(pairsChecked > 0, "No non-cosmetic evolution pairs with resolvable movesets on this ROM");
        // Reported, not asserted: a species with no slot at or below the floor level can only be served
        // late, the same last resort ensureEarlyStabFloor takes on the independently-randomized path.
        System.out.println("Follow Evolutions + Prefer Same Type: " + pairsChecked + " pair(s), "
                + repaired + " repaired slot(s), " + stabAboveFloor + " first STAB above level "
                + Randomizer.SPECIES_STAB_FLOOR_LEVEL + ", " + noPoolSpecies.size()
                + " with no on-type damaging move in the ROM at all " + noPoolSpecies);
        assertEquals(before.size(), movesets.size(), "species count changed during randomization");
    }

    // The same predicate createSetsOfMoves uses to admit a move to the Sensible Movesets damaging pool:
    // non-status, real base power, and not one of the moves banned from damaging slots outright.
    private boolean hasOwnTypeDamagingMove(Species pkmn, List<Move> allMoves) {
        for (Move mv : allMoves) {
            if (mv == null || mv.category == MoveCategory.STATUS || mv.power * mv.hitCount <= 0) {
                continue;
            }
            if (GlobalConstants.bannedRandomMoves[mv.number] || GlobalConstants.bannedForDamagingMove[mv.number]) {
                continue;
            }
            if (mv.type == pkmn.getPrimaryType(false) || mv.type == pkmn.getSecondaryType(false)) {
                return true;
            }
        }
        return false;
    }

    private int firstOwnTypeDamagingLevel(Species pkmn, List<MoveLearnt> moves, List<Move> allMoves) {
        int first = Integer.MAX_VALUE;
        for (MoveLearnt ml : moves) {
            if (ml.move <= 0 || ml.move >= allMoves.size()) {
                continue;
            }
            Move mv = allMoves.get(ml.move);
            if (mv == null || mv.category == MoveCategory.STATUS) {
                continue;
            }
            if (mv.type == pkmn.getPrimaryType(false) || mv.type == pkmn.getSecondaryType(false)) {
                first = Math.min(first, ml.level);
            }
        }
        return first;
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

    private RomHandler loadDsRomOrAny() {
        String romsDir = System.getProperty("romsPath");
        if (romsDir != null) {
            File[] files = new File(romsDir).listFiles();
            if (files != null) {
                List<File> dsRoms = new ArrayList<>();
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".nds")) {
                        dsRoms.add(f);
                    }
                }
                dsRoms.sort(Comparator.comparingLong(File::length));
                for (File f : dsRoms) {
                    RomHandler rom = tryLoad(f.getAbsolutePath());
                    if (rom != null) {
                        return rom;
                    }
                }
            }
        }
        return loadAnyRom();
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
