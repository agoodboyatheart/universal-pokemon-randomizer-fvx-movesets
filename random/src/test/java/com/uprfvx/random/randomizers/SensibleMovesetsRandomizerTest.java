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
 * ROM-driven validation for Species "Sensible Movesets". 2026-07-29: now uses the SAME hard-ceiling/
 * soft-floor power-banding mechanism as Better Movesets' trainer path (Aaron's explicit direction,
 * replacing the earlier two-band soft-only guideline from species-tmtutor-moveset-redesign.md P10 - a
 * real log showed the soft-only weight couldn't stop a level-1 Minun rolling Volt Tackle AND Solarbeam
 * when its narrowed type/category candidate pool held nothing but high-power moves). See
 * {@link SpeciesMovesetRandomizer#applySpeciesPowerCeiling} / {@code speciesLevelAppropriatenessWeight}
 * and {@link SpeciesMovesetWeightTest} for the pure-function unit tests of the mechanism itself; this
 * class checks it holds in real randomized output.
 * <p>
 * Named to match the {@code *Randomizer*Test} filter so it runs under the {@code testROMs} Gradle task. Does NOT
 * extend {@link RandomizerTest} - loads each ROM in {@code roms/} itself, by content, and skips itself if none
 * are loadable.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*SensibleMovesets*" }</pre>
 */
public class SensibleMovesetsRandomizerTest {

    private static final long MAX_ROM_BYTES = 6L * 1024 * 1024 * 1024;
    // Gen 1/2 ROMs (<=2MB) have short ~4-move learnsets and few species, which starves the level-50+ sample
    // count needed to detect the guideline's effect above noise - prefer a GBA-tier ROM or later.
    private static final long MIN_ROM_BYTES = 10L * 1024 * 1024;

    @Test
    public void sensibleMovesetsKeepsLowBpMovesAvailableAndGatesHighBpMovesByLevel() {
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

        assertTrue(bands.lowBpAvailableAtHighLevel() > 0,
                "Expected some <=60 BP moves to still appear at level 50+ (the soft floor only demotes, "
                        + "never removes, weak moves), count=" + bands.lowBpAvailableAtHighLevel());
        assertTrue(bands.highBpRateBelowThirty() < bands.highBpRateAtFiftyPlus(),
                "Expected >60 BP moves to be rarer (as a share of picks) below level 30 than at level 50+ "
                        + "(the hard ceiling widens continuously with level), rateBelow30="
                        + bands.highBpRateBelowThirty() + " rateAtFiftyPlus=" + bands.highBpRateAtFiftyPlus());
        // The hard ceiling is an exact per-slot cap, unlike the old soft-only weight - verify it actually
        // holds. Restricted to level>=2 slots: level-1 slots can be re-leveled internally by the backfill
        // mechanism (an evolved species' relisted pre-evolution move, weighted against its own evolution
        // level, not level 1 - species-tmtutor-moveset-redesign.md P11/P12) while still being *persisted*
        // at level 1, so checking powerCeiling(1) against those would false-positive. Level>=2 slots are
        // never backfill-remapped, so this is an exact check, only loosened for the rare
        // applySpeciesPowerCeiling empty-pool fallback (a narrow pool with nothing under the ceiling).
        assertTrue(bands.ceilingViolationRateAboveLevelOne() < 0.02,
                "Expected the hard power ceiling to (almost) always hold for level>=2 slots, rate="
                        + bands.ceilingViolationRateAboveLevelOne());
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

        assertTrue(bands.highBpRateBelowThirty() < bands.highBpRateAtFiftyPlus(),
                "Expected the >60 BP hard ceiling to hold even with Force Good Damaging off - Sensible "
                        + "Movesets must not depend on that unrelated, older toggle's budget (see "
                        + "species-power-curve-shuffle-and-scope-review.md Finding A). rateBelow30="
                        + bands.highBpRateBelowThirty() + " rateAtFiftyPlus=" + bands.highBpRateAtFiftyPlus());
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

    /** Accumulates per-band effective power stats across every sampled learnset slot. */
    private static final class PowerBands {
        private static final int[] BAND_EDGES = {10, 20, 35, 49, Integer.MAX_VALUE};
        private static final String[] BAND_NAMES = {"1-10", "11-20", "21-35", "36-49", "50+"};
        private static final double LOW_BP_GUIDELINE = 60.0;

        private final double[] bandSum = new double[BAND_EDGES.length];
        private final int[] bandCount = new int[BAND_EDGES.length];
        private int lowOverpowered = 0, lowTotal = 0;
        private int totalSamples = 0;

        // Rates, not raw counts: a whole dex has far more learnset slots below level 30 than at level 50+
        // (most non-legendary lines top out well below 50), so a raw count of ">60 BP picks below 30" can
        // exceed the raw count "at 50+" even when the underlying RATE at 50+ is much higher. Comparing
        // rates is what actually tests the ceiling's level-scaling.
        private int lowBpAtHighLevel = 0; // <=60 BP move picks at level >= 50
        private int belowThirtyTotal = 0, highBpBelowThirty = 0;
        private int fiftyPlusTotal = 0, highBpAtFiftyPlus = 0;
        private int aboveLevelOneTotal = 0, ceilingViolationsAboveLevelOne = 0;

        void add(int level, double effectivePower) {
            for (int b = 0; b < BAND_EDGES.length; b++) {
                if (level <= BAND_EDGES[b]) {
                    bandSum[b] += effectivePower;
                    bandCount[b]++;
                    break;
                }
            }
            totalSamples++;
            if (level <= 10) {
                lowTotal++;
                if (effectivePower > 100) {
                    lowOverpowered++;
                }
            }
            if (effectivePower <= LOW_BP_GUIDELINE && level >= 50) {
                lowBpAtHighLevel++;
            }
            boolean highBp = effectivePower > LOW_BP_GUIDELINE;
            if (level < 30) {
                belowThirtyTotal++;
                if (highBp) {
                    highBpBelowThirty++;
                }
            } else if (level >= 50) {
                fiftyPlusTotal++;
                if (highBp) {
                    highBpAtFiftyPlus++;
                }
            }
            if (level > 1) {
                aboveLevelOneTotal++;
                if (effectivePower > Randomizer.powerCeiling(level)) {
                    ceilingViolationsAboveLevelOne++;
                }
            }
        }

        void assumeEnoughSamples() {
            assumeTrue(totalSamples > 100, "Not enough sampled slots overall (n=" + totalSamples + ")");
            assumeTrue(belowThirtyTotal > 10 && fiftyPlusTotal > 10,
                    "Not enough sampled slots in both bands (below30=" + belowThirtyTotal + ", atFifty="
                            + fiftyPlusTotal + ")");
        }

        int lowBpAvailableAtHighLevel() {
            return lowBpAtHighLevel;
        }

        double highBpRateBelowThirty() {
            return (double) highBpBelowThirty / belowThirtyTotal;
        }

        double highBpRateAtFiftyPlus() {
            return (double) highBpAtFiftyPlus / fiftyPlusTotal;
        }

        double ceilingViolationRateAboveLevelOne() {
            return aboveLevelOneTotal == 0 ? 0.0 : (double) ceilingViolationsAboveLevelOne / aboveLevelOneTotal;
        }

        void printReport() {
            for (int b = 0; b < BAND_EDGES.length; b++) {
                System.out.printf("  band %-6s avg power: %.1f (n=%d)%n", BAND_NAMES[b],
                        bandCount[b] == 0 ? 0 : bandSum[b] / bandCount[b], bandCount[b]);
            }
            System.out.printf("  level<=10 slots rolling >100 power: %d/%d (%.1f%%)%n",
                    lowOverpowered, lowTotal, lowTotal == 0 ? 0 : 100.0 * lowOverpowered / lowTotal);
            System.out.printf("  <=60 BP picks at level>=50: %d%n", lowBpAtHighLevel);
            System.out.printf("  >60 BP picks below level 30: %d/%d (%.1f%%), at level>=50: %d/%d (%.1f%%)%n",
                    highBpBelowThirty, belowThirtyTotal, 100.0 * highBpRateBelowThirty(),
                    highBpAtFiftyPlus, fiftyPlusTotal, 100.0 * highBpRateAtFiftyPlus());
            System.out.printf("  hard ceiling violations (level>=2): %d/%d (%.2f%%)%n",
                    ceilingViolationsAboveLevelOne, aboveLevelOneTotal, 100.0 * ceilingViolationRateAboveLevelOne());
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
