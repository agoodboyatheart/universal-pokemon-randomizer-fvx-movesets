package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveLearnt;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ROM-driven validation for Species "Sensible Movesets" Phase 1 (level-appropriate power curve).
 * <p>
 * Named to match the {@code *Randomizer*Test} filter so it runs under the {@code testROMs} Gradle task. Does NOT
 * extend {@link RandomizerTest} - loads each ROM in {@code roms/} itself, by content, and skips itself if none
 * are loadable.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*SensibleMovesets*" }</pre>
 */
public class SensibleMovesetsRandomizerTest {

    private static final long MAX_ROM_BYTES = 6L * 1024 * 1024 * 1024;
    private static final int LOW_LEVEL_CEILING = 20;
    private static final int HIGH_LEVEL_FLOOR = 50;

    @Test
    public void sensibleMovesetsWeightsDamagingPicksTowardLevelAppropriatePower() {
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

        Settings on = new Settings();
        on.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        on.setMovesetsForceGoodDamaging(true);
        on.setMovesetsGoodDamagingPercent(100);
        on.setSensibleMovesets(true);
        new SpeciesMovesetRandomizer(rom, on, new Random(20260725L)).randomizeMovesLearnt();

        double lowSum = 0;
        int lowCount = 0;
        double highSum = 0;
        int highCount = 0;
        // Finer per-level-band diagnostics (report-only; the assertion below still uses the coarse low/high bands).
        int[] bandEdges = {10, 20, 35, 49, Integer.MAX_VALUE};
        String[] bandNames = {"1-10", "11-20", "21-35", "36-49", "50+"};
        double[] bandSum = new double[bandEdges.length];
        int[] bandCount = new int[bandEdges.length];
        int lowOverpowered = 0; // level <=10 slots that rolled effective power > 100
        int lowTotal = 0;
        Map<Integer, List<MoveLearnt>> movesets = rom.getMovesLearnt();
        List<Move> allMoves = rom.getMoves();
        for (List<MoveLearnt> learnt : movesets.values()) {
            for (MoveLearnt ml : learnt) {
                if (ml.level <= 0) {
                    continue;
                }
                Move mv = allMoves.get(ml.move);
                if (mv == null || mv.power <= 0) {
                    continue;
                }
                double effectivePower = mv.power * mv.hitCount;
                for (int b = 0; b < bandEdges.length; b++) {
                    if (ml.level <= bandEdges[b]) {
                        bandSum[b] += effectivePower;
                        bandCount[b]++;
                        break;
                    }
                }
                if (ml.level <= 10) {
                    lowTotal++;
                    if (effectivePower > 100) {
                        lowOverpowered++;
                    }
                }
                if (ml.level <= LOW_LEVEL_CEILING) {
                    lowSum += effectivePower;
                    lowCount++;
                } else if (ml.level >= HIGH_LEVEL_FLOOR) {
                    highSum += effectivePower;
                    highCount++;
                }
            }
        }
        assumeTrue(lowCount > 10 && highCount > 10,
                "Not enough sampled slots in both bands (low=" + lowCount + ", high=" + highCount + ")");
        double lowAvg = lowSum / lowCount;
        double highAvg = highSum / highCount;
        for (int b = 0; b < bandEdges.length; b++) {
            System.out.printf("  band %-6s avg power: %.1f (n=%d)%n", bandNames[b],
                    bandCount[b] == 0 ? 0 : bandSum[b] / bandCount[b], bandCount[b]);
        }
        System.out.printf("  level<=10 slots rolling >100 power: %d/%d (%.1f%%)%n",
                lowOverpowered, lowTotal, lowTotal == 0 ? 0 : 100.0 * lowOverpowered / lowTotal);
        System.out.printf("Low-level (<=%d) avg effective power: %.1f (n=%d)%n", LOW_LEVEL_CEILING, lowAvg, lowCount);
        System.out.printf("High-level (>=%d) avg effective power: %.1f (n=%d)%n", HIGH_LEVEL_FLOOR, highAvg, highCount);
        assertTrue(highAvg > lowAvg,
                "Expected high-level damaging picks to average more power than low-level ones with Sensible "
                        + "Movesets on (low=" + lowAvg + ", high=" + highAvg + ")");
    }

    @Test
    public void sensibleMovesetsOffStillProducesOneMovePerSlot() {
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

        Settings off = new Settings();
        off.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        off.setSensibleMovesets(false);
        new SpeciesMovesetRandomizer(rom, off, new Random(20260725L)).randomizeMovesLearnt();

        for (Map.Entry<Integer, List<MoveLearnt>> entry : rom.getMovesLearnt().entrySet()) {
            for (MoveLearnt ml : entry.getValue()) {
                assertTrue(ml.move > 0, "Species " + entry.getKey() + " has an unfilled move slot");
            }
        }
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
