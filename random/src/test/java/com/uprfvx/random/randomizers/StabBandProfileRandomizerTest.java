package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Diagnostic (report-only) harness measuring the <b>STAB power band</b> a Boss/Important trainer Pokemon actually
 * has available - the input side of the moveset logic, not its output.
 * <p>
 * Background: the boss damaging floor ({@link TrainerMovesetRandomizer#BOSS_MIN_DAMAGING_POWER}) is a flat 60 while
 * {@link Randomizer#powerCeiling(int)} scales with level, so the two cross at low level and the "acceptable" window
 * collapses to nothing (0 BP wide below Lv10, 3.5 BP at Lv12). Every early boss mon is then forced down the
 * type-rescue path, which spares over-ceiling moves without any upper bound - which is how a Lv12 gym opener ends up
 * with a 100 BP move. Bounding that rescue needs a number, and that number should come from a distribution rather
 * than from one observed seed.
 * <p>
 * For every Boss/Important mon this records the effective power of its cheapest STAB-eligible same-type damaging
 * move against that mon's own ceiling, and reports:
 * <ul>
 *   <li>how often the band is empty at the top (every same-type move is over the ceiling - the rescue fires),</li>
 *   <li>how often it is empty at the bottom (every same-type move is under the flat 60 floor),</li>
 *   <li>the distribution of {@code minEffectivePower / ceiling} for the over-ceiling cases, and</li>
 *   <li>a sweep of candidate rescue bounds R showing how many mons would be left with <b>no</b> same-type option
 *       at all if the rescue were capped at {@code ceiling * R} - the regression that a bound risks causing.</li>
 * </ul>
 * It asserts nothing. Both a vanilla-pool and a fully-randomised-pool configuration are measured, because the
 * failure only exists downstream of learnset/TM randomisation: with vanilla TMs a type keeps its mid-power
 * representatives and the band never collapses the same way.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*StabBandProfile*" }</pre>
 * Seed count defaults to {@link #DEFAULT_SEEDS} and is overridable, since one seed cannot answer a frequency
 * question:
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*StabBandProfile*" -Dbm.seeds=12 }</pre>
 * Skips (rather than fails) any generation whose ROM is absent from {@code roms/}, and skips entirely if none load.
 */
public class StabBandProfileRandomizerTest {

    private static final long BASE_SEED = 20260808L;
    private static final int DEFAULT_SEEDS = 6;
    private static final String ROMS_PATH = System.getProperty("romsPath");

    /** One game per generation, mirroring the golden-master list. {@code {gameName, fileBaseName}}. */
    private static final String[][] GAMES = {
            {"Red", "Red (U)"},
            {"Crystal", "Crystal (U)"},
            {"Emerald", "Emerald (U)"},
            {"Platinum", "Platinum (U)"},
            {"Black 2", "Black 2 (U)"},
            {"Alpha Sapphire", "Alpha Sapphire"},
            {"Ultra Sun", "Ultra Sun"},
    };

    /** Candidate rescue bounds to sweep, as multiples of the mon's own level ceiling. */
    private static final double[] BOUNDS = {1.00, 1.10, 1.20, 1.30, 1.40, 1.50, 1.75, 2.00};

    @Test
    public void profileStabBandAvailability() {
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");
        int seeds = Integer.getInteger("bm.seeds", DEFAULT_SEEDS);

        Band combinedVanilla = new Band();
        Band combinedRandom = new Band();
        int loaded = 0;
        for (String[] game : GAMES) {
            Band gameVanilla = new Band();
            Band gameRandom = new Band();
            boolean any = false;
            for (int i = 0; i < seeds; i++) {
                // One load per (game, seed): the vanilla read is non-mutating, so it is taken from the same handler
                // before the upstream randomizers run. Reloading the multi-GB 3DS dumps twice per seed is the single
                // biggest cost in this harness.
                RomHandler rom = tryLoad(game[0], game[1]);
                if (rom == null) {
                    break;
                }
                any = true;
                rom.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());
                if (i == 0) {
                    // Vanilla pools do not vary by seed, so one pass is the whole control.
                    gameVanilla.merge(measureCurrentPools(rom));
                }
                randomizeUpstream(rom, BASE_SEED + i);
                gameRandom.merge(measureCurrentPools(rom));
            }
            if (!any) {
                System.out.println("  (skip - ROM absent: " + game[0] + ")");
                continue;
            }
            loaded++;
            System.out.printf("%n== %s ==%n", game[0]);
            System.out.print("  VANILLA   ");
            gameVanilla.printSummary("");
            System.out.print("  RANDOMISED");
            gameRandom.printSummary("");
            combinedVanilla.merge(gameVanilla);
            combinedRandom.merge(gameRandom);
        }

        System.out.printf("%n################ COMBINED, VANILLA pools (%d ROM(s)) ################%n", loaded);
        combinedVanilla.printSummary("  ");
        combinedVanilla.printRatioHistogram("  ");
        combinedVanilla.printByBand("  ");

        System.out.printf("%n################ COMBINED, RANDOMISED pools (%d ROM(s), %d seed(s)) ################%n",
                loaded, seeds);
        combinedRandom.printSummary("  ");
        combinedRandom.printRatioHistogram("  ");
        combinedRandom.printBoundSweep("  ");
        combinedRandom.printByBand("  ");
        assumeTrue(loaded > 0, "No loadable ROM found in " + ROMS_PATH);
    }

    // Mirrors the GameRandomizer order: species learnsets, then TM moves, then TM/HM compatibility, then trainer
    // species - every one of which reshapes the candidate pool the moveset logic later sees.
    private void randomizeUpstream(RomHandler rom, long seed) {
        Settings s = new Settings();
        s.setMovesetsMod(Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE);
        s.setTmsMod(Settings.TMsMod.RANDOM);
        s.setTmsHmsCompatibilityMod(Settings.TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE);
        s.setTrainersMod(Settings.TrainersMod.RANDOM);
        new SpeciesMovesetRandomizer(rom, s, new Random(seed)).randomizeMovesLearnt();
        new TMTutorMoveRandomizer(rom, s, new Random(seed)).randomizeTMMoves();
        new TMHMTutorCompatibilityRandomizer(rom, s, new Random(seed)).randomizeTMHMCompatibility();
        new TrainerPokemonRandomizer(rom, s, new Random(seed)).randomizeTrainerPokes();
    }

    // Records each Boss/Important mon's STAB band position against whatever pools the handler currently holds.
    private Band measureCurrentPools(RomHandler rom) {
        Settings s = new Settings();
        s.setBetterBossTrainerMovesets(true);
        s.setBetterImportantTrainerMovesets(true);
        s.setBetterRegularTrainerMovesets(true);
        TrainerMovesetRandomizer tmr = new TrainerMovesetRandomizer(rom, s, new Random(BASE_SEED));
        Band band = new Band();
        for (Trainer tr : rom.getTrainers()) {
            if (!(tr.isBoss() || tr.isImportant()) || tr.shouldNotGetBuffs()) {
                continue;
            }
            for (TrainerPokemon tp : tr.getPokemon()) {
                record(band, tmr, tp);
            }
        }
        return band;
    }

    // Classifies one mon by where its cheapest STAB-eligible same-type damaging move sits relative to its band.
    private void record(Band band, TrainerMovesetRandomizer tmr, TrainerPokemon tp) {
        Species pk = tp.getSpecies();
        int level = tp.getLevel();
        Type t1 = pk.getPrimaryType(false);
        Type t2 = pk.getSecondaryType(false);

        // The raw pool, before the ceiling or the flat floor have removed anything.
        List<Move> raw = tmr.collectUnbandedMoveSelectionPool(tp, false);
        Set<Move> distinct = new LinkedHashSet<>(raw);

        double ceiling = Randomizer.powerCeiling(level);
        double minEp = Double.MAX_VALUE;
        double maxEp = 0;
        double minInBandTop = Double.MAX_VALUE; // cheapest move that is at or under the ceiling
        boolean anyAtOrAboveFloor = false;
        int overCeilingOptions = 0;
        for (Move mv : distinct) {
            if (mv == null || mv.category == MoveCategory.STATUS) {
                continue;
            }
            if (mv.type != t1 && (t2 == null || mv.type != t2)) {
                continue;
            }
            if (TrainerMovesetRandomizer.isStabSlotIneligible(mv)) {
                continue;
            }
            double ep = TrainerMovesetRandomizer.effectivePower(mv, level);
            if (ep <= 0) {
                continue;
            }
            minEp = Math.min(minEp, ep);
            maxEp = Math.max(maxEp, ep);
            if (ep <= ceiling) {
                minInBandTop = Math.min(minInBandTop, ep);
                if (ep >= TrainerMovesetRandomizer.bossMinDamagingPower(level)) {
                    anyAtOrAboveFloor = true;
                }
            } else {
                overCeilingOptions++;
            }
        }

        if (minEp == Double.MAX_VALUE) {
            band.noSameTypeMoveAtAll++;
            band.total++;
            return;
        }
        band.total++;
        if (minInBandTop == Double.MAX_VALUE) {
            // Every same-type option is over the ceiling - this is the case the rescue exists for. Whether
            // "spare only the cheapest" differs from today's "spare the whole type" depends entirely on how many
            // options there are here, so record that alongside the spread it would save.
            band.addOverCeiling(level, minEp / ceiling);
            if (overCeilingOptions > 1) {
                band.overCeilingMultiOption++;
                band.overCeilingSpread.add(maxEp / minEp);
            } else {
                band.overCeilingSingleOption++;
            }
        } else if (!anyAtOrAboveFloor) {
            // Something is under the ceiling but nothing reaches the flat 60 floor: the low-end collapse.
            band.addUnderFloor(level);
        } else {
            band.addHealthy(level);
        }
    }

    private RomHandler tryLoad(String gameName, String fileBaseName) {
        Generation gen = Generation.GAME_TO_GENERATION.get(gameName);
        String full = ROMS_PATH + "/" + fileBaseName + gen.getFileSuffix();
        if (!new File(full).exists()) {
            return null;
        }
        RomHandler.Factory factory = gen.createFactory();
        if (!factory.isLoadable(full)) {
            return null;
        }
        RomHandler rom = factory.create();
        rom.loadRom(full);
        return rom;
    }

    // Tallies of where Boss/Important mons sit relative to their own STAB power band.
    private static final class Band {
        private static final String[] BAND_LABELS = {"Lv1-15", "Lv16-30", "Lv31-45", "Lv46+"};

        int total;
        int noSameTypeMoveAtAll; // no same-type damaging move exists at any power - unfixable by any bound
        int healthy;             // has a same-type move inside [floor, ceiling]
        int underFloor;          // has same-type moves under the ceiling, but all below the flat 60 floor
        int overCeiling;         // every same-type move is above the ceiling (the rescue path)
        // Of those, how many have more than one over-ceiling same-type option: the mons for which sparing only the
        // cheapest differs at all from sparing the whole type. The rest are a no-op under either rule.
        int overCeilingMultiOption;
        int overCeilingSingleOption;
        final List<Double> overCeilingSpread = new ArrayList<>(); // maxEp/minEp among the multi-option mons
        final List<Double> overCeilingRatios = new ArrayList<>();
        final int[] healthyByBand = new int[4];
        final int[] underFloorByBand = new int[4];
        final int[] overCeilingByBand = new int[4];
        // Ratios kept per level band so an early-game-only conclusion is not drawn from an all-levels number.
        @SuppressWarnings("unchecked")
        final List<Double>[] ratiosByBand = new List[]{
                new ArrayList<Double>(), new ArrayList<Double>(), new ArrayList<Double>(), new ArrayList<Double>()};

        void addHealthy(int level) {
            healthy++;
            healthyByBand[bandIndex(level)]++;
        }

        void addUnderFloor(int level) {
            underFloor++;
            underFloorByBand[bandIndex(level)]++;
        }

        void addOverCeiling(int level, double ratio) {
            overCeiling++;
            overCeilingByBand[bandIndex(level)]++;
            overCeilingRatios.add(ratio);
            ratiosByBand[bandIndex(level)].add(ratio);
        }

        void merge(Band o) {
            total += o.total;
            noSameTypeMoveAtAll += o.noSameTypeMoveAtAll;
            healthy += o.healthy;
            underFloor += o.underFloor;
            overCeiling += o.overCeiling;
            overCeilingMultiOption += o.overCeilingMultiOption;
            overCeilingSingleOption += o.overCeilingSingleOption;
            overCeilingSpread.addAll(o.overCeilingSpread);
            overCeilingRatios.addAll(o.overCeilingRatios);
            for (int i = 0; i < 4; i++) {
                healthyByBand[i] += o.healthyByBand[i];
                underFloorByBand[i] += o.underFloorByBand[i];
                overCeilingByBand[i] += o.overCeilingByBand[i];
                ratiosByBand[i].addAll(o.ratiosByBand[i]);
            }
        }

        void printSummary(String indent) {
            if (total == 0) {
                System.out.println(indent + "(no boss/important mons profiled)");
                return;
            }
            System.out.printf("%stotal=%d  healthy(in band)=%d (%.1f%%)  underFloor=%d (%.1f%%)  "
                            + "overCeiling=%d (%.1f%%)  noSameTypeMove=%d (%.1f%%)%n",
                    indent, total, healthy, pct(healthy), underFloor, pct(underFloor),
                    overCeiling, pct(overCeiling), noSameTypeMoveAtAll, pct(noSameTypeMoveAtAll));
        }

        void printRatioHistogram(String indent) {
            if (overCeilingRatios.isEmpty()) {
                return;
            }
            List<Double> sorted = new ArrayList<>(overCeilingRatios);
            sorted.sort(Double::compareTo);
            System.out.printf("%n%sminEffectivePower / ceiling, for the %d over-ceiling mons:%n",
                    indent, sorted.size());
            System.out.printf("%s  min=%.2f  p25=%.2f  p50=%.2f  p75=%.2f  p90=%.2f  p95=%.2f  p99=%.2f  max=%.2f%n",
                    indent, sorted.get(0), percentile(sorted, 25), percentile(sorted, 50), percentile(sorted, 75),
                    percentile(sorted, 90), percentile(sorted, 95), percentile(sorted, 99),
                    sorted.get(sorted.size() - 1));
            int multi = overCeilingMultiOption;
            int single = overCeilingSingleOption;
            System.out.printf("%s  of these, %d (%.1f%%) have ONLY ONE over-ceiling same-type option "
                            + "(spare-cheapest is a no-op there); %d (%.1f%%) have more than one%n",
                    indent, single, 100.0 * single / (single + multi), multi, 100.0 * multi / (single + multi));
            if (!overCeilingSpread.isEmpty()) {
                List<Double> sp = new ArrayList<>(overCeilingSpread);
                sp.sort(Double::compareTo);
                System.out.printf("%s  among the multi-option mons, maxEp/minEp: p25=%.2f p50=%.2f p75=%.2f max=%.2f"
                                + "  (what sparing only the cheapest actually saves)%n",
                        indent, percentile(sp, 25), percentile(sp, 50), percentile(sp, 75), sp.get(sp.size() - 1));
            }
            double[] edges = {1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.75, 2.0, 2.5, 3.0};
            for (int i = 0; i < edges.length; i++) {
                double lo = edges[i];
                double hi = i + 1 < edges.length ? edges[i + 1] : Double.MAX_VALUE;
                long n = sorted.stream().filter(r -> r >= lo && r < hi).count();
                if (n > 0) {
                    System.out.printf("%s  [%.2f, %s) %6d  %s%n", indent, lo,
                            hi == Double.MAX_VALUE ? "inf" : String.format("%.2f", hi), n,
                            "#".repeat((int) Math.min(60, 1 + (60L * n) / sorted.size())));
                }
            }
        }

        // For each candidate bound R: how many of the over-ceiling mons keep a STAB option (their cheapest same-type
        // move is within ceiling*R) versus lose their last one entirely. Losing one is the regression a bound risks.
        void printBoundSweep(String indent) {
            if (overCeilingRatios.isEmpty()) {
                return;
            }
            System.out.printf("%n%srescue bound sweep (over %d over-ceiling mons, %d boss mons total):%n",
                    indent, overCeilingRatios.size(), total);
            System.out.printf("%s  %-8s %-14s %-14s %s%n", indent, "R", "keeps STAB", "LOSES STAB", "% of all boss mons left with no STAB");
            for (double r : BOUNDS) {
                long kept = overCeilingRatios.stream().filter(x -> x <= r).count();
                long lost = overCeilingRatios.size() - kept;
                System.out.printf("%s  %-8.2f %-14d %-14d %.2f%%%n", indent, r, kept, lost, 100.0 * lost / total);
            }
        }

        void printByBand(String indent) {
            System.out.printf("%n%sby level band:%n", indent);
            for (int i = 0; i < 4; i++) {
                int n = healthyByBand[i] + underFloorByBand[i] + overCeilingByBand[i];
                if (n == 0) {
                    continue;
                }
                List<Double> sorted = new ArrayList<>(ratiosByBand[i]);
                sorted.sort(Double::compareTo);
                String med = sorted.isEmpty() ? "n/a" : String.format("%.2f", percentile(sorted, 50));
                System.out.printf("%s  %-8s n=%-6d healthy=%5.1f%%  underFloor=%5.1f%%  overCeiling=%5.1f%%  "
                                + "median over-ceiling ratio=%s%n",
                        indent, BAND_LABELS[i], n, 100.0 * healthyByBand[i] / n, 100.0 * underFloorByBand[i] / n,
                        100.0 * overCeilingByBand[i] / n, med);
            }
        }

        private double pct(int n) {
            return total == 0 ? 0 : 100.0 * n / total;
        }

        private static double percentile(List<Double> sorted, int p) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
            return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
        }

        private static int bandIndex(int level) {
            if (level <= 15) {
                return 0;
            }
            if (level <= 30) {
                return 1;
            }
            if (level <= 45) {
                return 2;
            }
            return 3;
        }
    }
}
