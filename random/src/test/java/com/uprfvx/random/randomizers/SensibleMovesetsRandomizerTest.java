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
    // Gen 1/2 ROMs (<=2MB) have short ~4-move learnsets and few species, which starves the level-50+ sample
    // count needed to detect a modest power-curve effect above noise - prefer a GBA-tier ROM or later.
    private static final long MIN_ROM_BYTES = 10L * 1024 * 1024;
    private static final int LOW_LEVEL_CEILING = 20;
    private static final int HIGH_LEVEL_FLOOR = 50;
    // Empirically calibrated (species-movesets-phase1.75-shuffle-fix-plan.md): the broken shuffle/coupling
    // measured ~0.05-0.10 level-power correlation on real ROM data; the fix measured ~0.41-0.44. 0.25 sits
    // comfortably between the two with margin on both sides.
    private static final double MIN_LEVEL_POWER_CORRELATION = 0.25;

    @Test
    public void sensibleMovesetsWeightsDamagingPicksTowardLevelAppropriatePower() {
        RomHandler rom = loadAnyRom();

        Settings on = new Settings();
        on.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        on.setMovesetsForceGoodDamaging(true);
        on.setMovesetsGoodDamagingPercent(100);
        on.setSensibleMovesets(true);
        new SpeciesMovesetRandomizer(rom, on, new Random(20260725L)).randomizeMovesLearnt();

        PowerBands bands = collectPowerBands(rom);
        bands.assumeEnoughSamples();
        bands.printReport();

        assertTrue(bands.highAvg() > bands.lowAvg(),
                "Expected high-level damaging picks to average more power than low-level ones with Sensible "
                        + "Movesets on (low=" + bands.lowAvg() + ", high=" + bands.highAvg() + ")");
        assertTrue(bands.correlation() > MIN_LEVEL_POWER_CORRELATION,
                "Expected a meaningful positive level-power correlation across every sampled slot, not just "
                        + "aggregate bucket averages (which can look curve-like even when the shuffle destroys "
                        + "per-slot correspondence - see species-power-curve-shuffle-and-scope-review.md Finding "
                        + "B). correlation=" + bands.correlation() + ", threshold=" + MIN_LEVEL_POWER_CORRELATION);
    }

    @Test
    public void sensibleMovesetsStillWeightsWithForceGoodDamagingOff() {
        RomHandler rom = loadAnyRom();

        Settings on = new Settings();
        on.setMovesetsMod(Settings.MovesetsMod.COMPLETELY_RANDOM);
        on.setMovesetsForceGoodDamaging(false);
        on.setSensibleMovesets(true);
        new SpeciesMovesetRandomizer(rom, on, new Random(20260725L)).randomizeMovesLearnt();

        PowerBands bands = collectPowerBands(rom);
        bands.assumeEnoughSamples();
        bands.printReport();

        assertTrue(bands.correlation() > MIN_LEVEL_POWER_CORRELATION,
                "Expected a meaningful positive level-power correlation even with Force Good Damaging off - "
                        + "Sensible Movesets must not depend on that unrelated, older toggle's budget (see "
                        + "species-power-curve-shuffle-and-scope-review.md Finding A). correlation="
                        + bands.correlation() + ", threshold=" + MIN_LEVEL_POWER_CORRELATION);
    }

    @Test
    public void sensibleMovesetsOffStillProducesOneMovePerSlot() {
        RomHandler rom = loadAnyRom();

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

    private PowerBands collectPowerBands(RomHandler rom) {
        PowerBands bands = new PowerBands();
        List<Move> allMoves = rom.getMoves();
        for (List<MoveLearnt> learnt : rom.getMovesLearnt().values()) {
            for (MoveLearnt ml : learnt) {
                if (ml.level <= 0) {
                    continue;
                }
                Move mv = allMoves.get(ml.move);
                if (mv == null || mv.power <= 0) {
                    continue;
                }
                bands.add(ml.level, mv.power * mv.hitCount);
            }
        }
        return bands;
    }

    /** Accumulates per-band average effective power across every sampled learnset slot. */
    private static final class PowerBands {
        private static final int[] BAND_EDGES = {10, 20, 35, 49, Integer.MAX_VALUE};
        private static final String[] BAND_NAMES = {"1-10", "11-20", "21-35", "36-49", "50+"};

        private final double[] bandSum = new double[BAND_EDGES.length];
        private final int[] bandCount = new int[BAND_EDGES.length];
        private double lowSum = 0, midLowSum = 0, highSum = 0;
        private int lowCount = 0, midLowCount = 0, highCount = 0;
        private int lowOverpowered = 0, lowTotal = 0;

        // Streaming accumulators for a Pearson correlation between level and effective power across every
        // individual sample. Bucket-average comparisons (lowAvg/midLowAvg/highAvg) are confounded when the
        // shuffle bug is present: it scrambles picks WITHIN a species, but each species' own pool of
        // weighted picks still trends upward with that species' own level range, so aggregate bucket means
        // can look curve-like even with zero real per-slot correspondence. Correlation over the full,
        // unbucketed (level, power) pairs is what actually tests "does this slot's power track this slot's
        // own level" - matches the per-exact-level analysis in species-power-curve-shuffle-and-scope-review.md.
        private long corrN = 0;
        private double corrSumLevel = 0, corrSumPower = 0, corrSumLevelPower = 0, corrSumLevelSq = 0, corrSumPowerSq = 0;

        void add(int level, double effectivePower) {
            for (int b = 0; b < BAND_EDGES.length; b++) {
                if (level <= BAND_EDGES[b]) {
                    bandSum[b] += effectivePower;
                    bandCount[b]++;
                    break;
                }
            }
            corrN++;
            corrSumLevel += level;
            corrSumPower += effectivePower;
            corrSumLevelPower += (double) level * effectivePower;
            corrSumLevelSq += (double) level * level;
            corrSumPowerSq += effectivePower * effectivePower;
            if (level <= 10) {
                lowTotal++;
                if (effectivePower > 100) {
                    lowOverpowered++;
                }
            }
            if (level <= LOW_LEVEL_CEILING) {
                lowSum += effectivePower;
                lowCount++;
            } else if (level >= HIGH_LEVEL_FLOOR) {
                highSum += effectivePower;
                highCount++;
            }
            if (level >= 2 && level <= LOW_LEVEL_CEILING) {
                midLowSum += effectivePower;
                midLowCount++;
            }
        }

        void assumeEnoughSamples() {
            assumeTrue(lowCount > 10 && highCount > 10,
                    "Not enough sampled slots in both bands (low=" + lowCount + ", high=" + highCount + ")");
            assumeTrue(midLowCount > 10, "Not enough sampled slots in the level 2-20 band (n=" + midLowCount + ")");
        }

        double lowAvg() {
            return lowSum / lowCount;
        }

        double midLowAvg() {
            return midLowSum / midLowCount;
        }

        double highAvg() {
            return highSum / highCount;
        }

        /** Pearson correlation between level and effective power across every sampled slot. NaN if degenerate. */
        double correlation() {
            double n = corrN;
            double covariance = corrSumLevelPower - corrSumLevel * corrSumPower / n;
            double levelVariance = corrSumLevelSq - corrSumLevel * corrSumLevel / n;
            double powerVariance = corrSumPowerSq - corrSumPower * corrSumPower / n;
            return covariance / Math.sqrt(levelVariance * powerVariance);
        }

        void printReport() {
            for (int b = 0; b < BAND_EDGES.length; b++) {
                System.out.printf("  band %-6s avg power: %.1f (n=%d)%n", BAND_NAMES[b],
                        bandCount[b] == 0 ? 0 : bandSum[b] / bandCount[b], bandCount[b]);
            }
            System.out.printf("  level<=10 slots rolling >100 power: %d/%d (%.1f%%)%n",
                    lowOverpowered, lowTotal, lowTotal == 0 ? 0 : 100.0 * lowOverpowered / lowTotal);
            System.out.printf("Low-level (<=%d) avg effective power: %.1f (n=%d)%n", LOW_LEVEL_CEILING, lowAvg(), lowCount);
            System.out.printf("Level 2-%d (excluding lv1) avg effective power: %.1f (n=%d)%n", LOW_LEVEL_CEILING, midLowAvg(), midLowCount);
            System.out.printf("High-level (>=%d) avg effective power: %.1f (n=%d)%n", HIGH_LEVEL_FLOOR, highAvg(), highCount);
            System.out.printf("Level-power correlation (all %d samples): %.3f%n", corrN, correlation());
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
            if (!f.isFile() || f.getName().equalsIgnoreCase("readme.txt") || f.length() > MAX_ROM_BYTES
                    || f.length() < MIN_ROM_BYTES) {
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
