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
 * ROM-driven validation for Species "Sensible Movesets" (species-tmtutor-moveset-redesign.md P10-P12: a
 * two-band power guideline, deliberately NOT a continuously level-scaled curve - species pacing must never
 * push toward higher power as level rises, only gate rare strong outliers at low level).
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
                "Expected some <=60 BP moves to still appear at level 50+ (species pacing must not push "
                        + "toward higher power as level rises - species-tmtutor-moveset-redesign.md P10), "
                        + "count=" + bands.lowBpAvailableAtHighLevel());
        assertTrue(bands.highBpRateBelowThirty() < bands.highBpRateAtFiftyPlus(),
                "Expected >60 BP moves to be rarer (as a share of picks) below level 30 than at level 50+ "
                        + "(soft ceiling ramping from L30 to L50), rateBelow30=" + bands.highBpRateBelowThirty()
                        + " rateAtFiftyPlus=" + bands.highBpRateAtFiftyPlus());
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
                "Expected the >60 BP soft ceiling to hold even with Force Good Damaging off - Sensible "
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

        // The three P10 invariants this test actually cares about - see the class doc comment. Unlike the
        // old Gaussian curve's "power should climb with level" claim (removed - it's the opposite of this
        // recalibration's goal), none of these assert an average trending upward with level.
        //
        // Rates, not raw counts: a whole dex has far more learnset slots below level 30 than at level 50+
        // (most non-legendary lines top out well below 50), so a raw count of ">60 BP picks below 30" can
        // exceed the raw count "at 50+" even when the underlying RATE at 50+ is much higher - confirmed by
        // running this once with raw counts (below30=318, atFifty=223, i.e. 12% vs 68% once divided by each
        // band's own total). Comparing rates is what actually tests the soft ceiling.
        private int lowBpAtHighLevel = 0; // <=60 BP move picks at level >= 50
        private int belowThirtyTotal = 0, highBpBelowThirty = 0;
        private int fiftyPlusTotal = 0, highBpAtFiftyPlus = 0;

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
