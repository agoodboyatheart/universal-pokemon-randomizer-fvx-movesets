package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Report-only calibration harness for the species learnset redesign
 * (project_memory/species-learnset-vanilla-alignment-spec.md). Makes no assertions - it prints the same
 * tables project_memory/modern-levelup-learnset-structure-report.md measured against real Gen 6/7 data,
 * for VANILLA and RANDOMIZED output of the same ROM side by side, so a lever's effect can be read off
 * directly and compared to what vanilla actually does.
 * <p>
 * Vanilla is collected from the same ROM rather than quoted from the report so the comparison is not
 * confounded by generation: an older game's smaller movepool shifts every one of these figures.
 * <p>
 * Gen 4+ only. Before gen 4 a move's category is derived from its type, so the physical/special table is
 * meaningless and the category lean cannot be measured at all.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*SpeciesLearnsetProfile*" }</pre>
 */
public class SpeciesLearnsetProfileRandomizerTest {

    private static final long MAX_ROM_BYTES = 6L * 1024 * 1024 * 1024;
    private static final long MIN_ROM_BYTES = 10L * 1024 * 1024;
    private static final long SEED = 20260731L;

    @Test
    public void profileSensibleMovesetsAgainstVanilla() {
        profile(loadAnyRomOfGenerationAtLeast(4));
    }

    // Gen 4 carries only a single Iron Fist species, which is far too thin to read the ability-coherence
    // lever off. Gen 5 has enough holders of the flagged-move-class abilities for the number to mean
    // something.
    @Test
    public void profileSensibleMovesetsOnALaterGeneration() {
        profile(loadAnyRomOfGenerationAtLeast(5));
    }

    private void profile(RomHandler rom) {
        Map<Integer, List<MoveLearnt>> vanillaSnapshot = snapshotLearnsets(rom);
        Profile vanilla = collect(rom, vanillaSnapshot);

        Settings s = new Settings();
        s.setMovesetsMod(Settings.MovesetsMod.RANDOM_PREFER_SAME_TYPE);
        s.setSensibleMovesets(true);
        s.setMovesetsForceGoodDamaging(false);
        new SpeciesMovesetRandomizer(rom, s, new Random(SEED)).randomizeMovesLearnt();

        Profile randomized = collect(rom, rom.getMovesLearnt());

        System.out.println();
        System.out.println("=== Species learnset profile: " + rom.getROMName()
                + " (gen " + rom.generationOfPokemon() + "), seed " + SEED + " ===");
        System.out.println("Prefer Same Type + Sensible Movesets, Force Good Damaging off.");
        Profile.printComparison(vanilla, randomized);
    }

    private Map<Integer, List<MoveLearnt>> snapshotLearnsets(RomHandler rom) {
        Map<Integer, List<MoveLearnt>> snapshot = new HashMap<>();
        for (Map.Entry<Integer, List<MoveLearnt>> e : rom.getMovesLearnt().entrySet()) {
            List<MoveLearnt> copy = new ArrayList<>();
            for (MoveLearnt ml : e.getValue()) {
                copy.add(new MoveLearnt(ml.move, ml.level));
            }
            snapshot.put(e.getKey(), copy);
        }
        return snapshot;
    }

    private Profile collect(RomHandler rom, Map<Integer, List<MoveLearnt>> learnsets) {
        Profile p = new Profile();
        List<Move> allMoves = rom.getMoves();
        Map<Integer, Species> byId = new HashMap<>();
        for (Species sp : rom.getRestrictedSpeciesService().getAll(true)) {
            byId.put(sp.getNumber(), sp);
        }

        for (Map.Entry<Integer, List<MoveLearnt>> e : learnsets.entrySet()) {
            Species sp = byId.get(e.getKey());
            if (sp == null) {
                continue;
            }
            List<MoveLearnt> moves = e.getValue();
            if (moves.size() < 4) {
                continue;
            }
            Type primary = sp.getPrimaryType(false);
            Type secondary = sp.getSecondaryType(false);

            int statusCount = 0, attackCount = 0, physicalCount = 0, stabCount = 0;
            int punchCount = 0;
            double maxBp = 0;
            double bpSum = 0;
            int bpCount = 0;
            Set<Type> attackingTypes = new TreeSet<>();

            for (MoveLearnt ml : moves) {
                Move mv = allMoves.get(ml.move);
                if (mv == null) {
                    continue;
                }
                int band = Profile.bandOf(ml.level);
                if (mv.category == MoveCategory.STATUS) {
                    statusCount++;
                    p.bandStatus[band]++;
                } else {
                    attackCount++;
                    p.bandAttack[band]++;
                    if (mv.type != null) {
                        attackingTypes.add(mv.type);
                    }
                    if (mv.category == MoveCategory.PHYSICAL) {
                        physicalCount++;
                    }
                    boolean stab = mv.type != null && (mv.type == primary || mv.type == secondary);
                    if (stab) {
                        stabCount++;
                        p.bandStab[band]++;
                    }
                    p.bandStabTotal[band]++;
                    if (mv.isPunchMove) {
                        punchCount++;
                    }
                    if (mv.power > 0) {
                        double ep = mv.power * mv.hitCount;
                        p.bandBpSum[band] += ep;
                        p.bandBpCount[band]++;
                        bpSum += ep;
                        bpCount++;
                        maxBp = Math.max(maxBp, ep);
                    }
                }
            }

            int total = statusCount + attackCount;
            if (total == 0) {
                continue;
            }
            p.speciesStatusShare.add((double) statusCount / total);
            p.speciesDistinctTypes.add(attackingTypes.size());

            // First-STAB / first-attack level per species (species-moveset-pearl-real-rom-comparison-
            // report.md's headline tail measurement - a mean/percentile table over aggregate slots can't
            // see a species with a starved learnset, since one Togekiss disappears into a 40%+ status-
            // share average). Computed by level, not list order, and independent of the band tally above.
            // A level-0 (evolution move) slot is a valid candidate "first" move, reported as level 1.
            int minAttackLevel = Integer.MAX_VALUE;
            int minStabLevel = Integer.MAX_VALUE;
            for (MoveLearnt ml : moves) {
                Move mv2 = allMoves.get(ml.move);
                if (mv2 == null || mv2.category == MoveCategory.STATUS) {
                    continue;
                }
                int lvl = Math.max(1, ml.level);
                minAttackLevel = Math.min(minAttackLevel, lvl);
                if (mv2.type != null && (mv2.type == primary || mv2.type == secondary)) {
                    minStabLevel = Math.min(minStabLevel, lvl);
                }
            }
            if (minAttackLevel == Integer.MAX_VALUE) {
                p.zeroAttackCount++;
            } else {
                p.firstAttackLevel.add((double) minAttackLevel);
            }
            if (minStabLevel == Integer.MAX_VALUE) {
                p.zeroStabCount++;
            } else {
                p.firstStabLevel.add((double) minStabLevel);
            }

            if (attackCount > 0) {
                p.bucketPhysical(sp.getBaseStats().getAttack() - sp.getBaseStats().getSpatk(),
                        physicalCount, attackCount);
                p.bucketStab(stabCount, attackCount);
            }
            if (bpCount > 0) {
                p.bucketBst(sp.getBaseStats().getBST(), bpSum / bpCount, maxBp);
            }
            p.recordPunch(hasIronFist(sp, rom), punchCount);
        }
        return p;
    }

    // Iron Fist is the report's strongest ability-to-move-class signal (24x punch moves by slot count);
    // gen 3+ only, since gen 1-2 have no abilities at all.
    private boolean hasIronFist(Species sp, RomHandler rom) {
        if (rom.generationOfPokemon() < 4) {
            return false;
        }
        return sp.getAbility1() == com.uprfvx.romio.constants.AbilityIDs.ironFist
                || sp.getAbility2() == com.uprfvx.romio.constants.AbilityIDs.ironFist
                || sp.getAbility3() == com.uprfvx.romio.constants.AbilityIDs.ironFist;
    }

    /** Accumulates every table the structure report measured, for one snapshot of a ROM's learnsets. */
    private static final class Profile {
        static final String[] BAND_NAMES = {"0-1", "2-10", "11-20", "21-30", "31-40", "41-50", "51+"};
        static final String[] ATK_SPA_BUCKETS = {"<=-60", "-60..-20", "-20..+20", "+20..+60", ">=+60"};
        static final String[] BST_BUCKETS = {"<300", "300-400", "400-480", "480-540", "540-600", "600+"};

        final int[] bandStatus = new int[BAND_NAMES.length];
        final int[] bandAttack = new int[BAND_NAMES.length];
        final int[] bandStab = new int[BAND_NAMES.length];
        final int[] bandStabTotal = new int[BAND_NAMES.length];
        final double[] bandBpSum = new double[BAND_NAMES.length];
        final int[] bandBpCount = new int[BAND_NAMES.length];

        final List<Double> speciesStatusShare = new ArrayList<>();
        final List<Integer> speciesDistinctTypes = new ArrayList<>();

        final List<Double> firstAttackLevel = new ArrayList<>();
        final List<Double> firstStabLevel = new ArrayList<>();
        int zeroAttackCount = 0;
        int zeroStabCount = 0;

        final int[] atkSpaPhysical = new int[ATK_SPA_BUCKETS.length];
        final int[] atkSpaTotal = new int[ATK_SPA_BUCKETS.length];
        final int[] atkSpaSpecies = new int[ATK_SPA_BUCKETS.length];

        int stabSlots = 0, attackSlots = 0;

        final double[] bstMeanSum = new double[BST_BUCKETS.length];
        final double[] bstMaxSum = new double[BST_BUCKETS.length];
        final int[] bstCount = new int[BST_BUCKETS.length];

        int ironFistSpecies = 0, otherSpecies = 0;
        int ironFistPunch = 0, otherPunch = 0;

        static int bandOf(int level) {
            if (level <= 1) return 0;
            if (level <= 10) return 1;
            if (level <= 20) return 2;
            if (level <= 30) return 3;
            if (level <= 40) return 4;
            if (level <= 50) return 5;
            return 6;
        }

        void bucketPhysical(int atkMinusSpa, int physical, int attacking) {
            int b = atkMinusSpa <= -60 ? 0 : atkMinusSpa < -20 ? 1 : atkMinusSpa <= 20 ? 2
                    : atkMinusSpa < 60 ? 3 : 4;
            atkSpaPhysical[b] += physical;
            atkSpaTotal[b] += attacking;
            atkSpaSpecies[b]++;
        }

        void bucketStab(int stab, int attacking) {
            stabSlots += stab;
            attackSlots += attacking;
        }

        void bucketBst(int bst, double meanBp, double maxBp) {
            int b = bst < 300 ? 0 : bst < 400 ? 1 : bst < 480 ? 2 : bst < 540 ? 3 : bst < 600 ? 4 : 5;
            bstMeanSum[b] += meanBp;
            bstMaxSum[b] += maxBp;
            bstCount[b]++;
        }

        void recordPunch(boolean ironFist, int punchCount) {
            if (ironFist) {
                ironFistSpecies++;
                ironFistPunch += punchCount;
            } else {
                otherSpecies++;
                otherPunch += punchCount;
            }
        }

        private static double percentile(List<Double> sorted, double p) {
            if (sorted.isEmpty()) {
                return 0;
            }
            int idx = (int) Math.round(p * (sorted.size() - 1));
            return sorted.get(Math.clamp(idx, 0, sorted.size() - 1));
        }

        private static double mean(List<Double> values) {
            return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        }

        private static double max(List<Double> sorted) {
            return sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);
        }

        // zeroCount species are excluded from mean/median/p90/max (there's no "level" to average in) and
        // reported as their own rate instead - mirroring how the report separates "20 species have zero
        // STAB ever" from "the tail among species that do get one runs to level 78".
        private static void printFirstMoveTable(String label, List<Double> vLevels, int vZero,
                                                List<Double> rLevels, int rZero, String zeroLabel) {
            List<Double> vs = new ArrayList<>(vLevels);
            List<Double> rs = new ArrayList<>(rLevels);
            Collections.sort(vs);
            Collections.sort(rs);
            int vTotal = vs.size() + vZero;
            int rTotal = rs.size() + rZero;

            System.out.printf("  %-16s %10s %10s%n", "", "vanilla", "randomized");
            System.out.printf("  %-16s %9.1f%% %9.1f%%%n", zeroLabel + " rate",
                    100 * share(vZero, vTotal), 100 * share(rZero, rTotal));
            System.out.printf("  %-16s %10.1f %10.1f%n", label + " mean", mean(vs), mean(rs));
            System.out.printf("  %-16s %10.1f %10.1f%n", label + " median", percentile(vs, 0.50),
                    percentile(rs, 0.50));
            System.out.printf("  %-16s %10.1f %10.1f%n", label + " p90", percentile(vs, 0.90),
                    percentile(rs, 0.90));
            System.out.printf("  %-16s %10.1f %10.1f%n", label + " max", max(vs), max(rs));
        }

        static void printComparison(Profile v, Profile r) {
            List<Double> vs = new ArrayList<>(v.speciesStatusShare);
            List<Double> rs = new ArrayList<>(r.speciesStatusShare);
            Collections.sort(vs);
            Collections.sort(rs);

            System.out.println();
            System.out.println("-- per-species status share (report: mean 37.5%, p10 21.1%, p90 56.2%) --");
            System.out.printf("  %-12s %10s %10s%n", "", "vanilla", "randomized");
            System.out.printf("  %-12s %9.1f%% %9.1f%%%n", "mean", 100 * mean(vs), 100 * mean(rs));
            System.out.printf("  %-12s %9.1f%% %9.1f%%%n", "p10", 100 * percentile(vs, 0.10),
                    100 * percentile(rs, 0.10));
            System.out.printf("  %-12s %9.1f%% %9.1f%%%n", "median", 100 * percentile(vs, 0.50),
                    100 * percentile(rs, 0.50));
            System.out.printf("  %-12s %9.1f%% %9.1f%%%n", "p90", 100 * percentile(vs, 0.90),
                    100 * percentile(rs, 0.90));

            System.out.println();
            System.out.println("-- first-STAB / first-attack level (species-moveset-pearl-real-rom-"
                    + "comparison-report.md headline tail metric) --");
            printFirstMoveTable("first-STAB level", v.firstStabLevel, v.zeroStabCount, r.firstStabLevel,
                    r.zeroStabCount, "zero-STAB");
            printFirstMoveTable("first-attack level", v.firstAttackLevel, v.zeroAttackCount,
                    r.firstAttackLevel, r.zeroAttackCount, "zero-attack");

            System.out.println();
            System.out.println("-- attacking share by level band (report: 61.4/51.5/64.8/63.1/60.2/66.6/69.8) --");
            System.out.printf("  %-8s %10s %10s%n", "band", "vanilla", "randomized");
            for (int b = 0; b < BAND_NAMES.length; b++) {
                System.out.printf("  %-8s %9.1f%% %9.1f%%%n", BAND_NAMES[b],
                        100 * share(v.bandAttack[b], v.bandAttack[b] + v.bandStatus[b]),
                        100 * share(r.bandAttack[b], r.bandAttack[b] + r.bandStatus[b]));
            }

            System.out.println();
            System.out.println("-- STAB share of attacking slots by band (report: 48.4 -> 60.9) --");
            System.out.printf("  %-8s %10s %10s%n", "band", "vanilla", "randomized");
            for (int b = 0; b < BAND_NAMES.length; b++) {
                System.out.printf("  %-8s %9.1f%% %9.1f%%%n", BAND_NAMES[b],
                        100 * share(v.bandStab[b], v.bandStabTotal[b]),
                        100 * share(r.bandStab[b], r.bandStabTotal[b]));
            }
            System.out.printf("  %-8s %9.1f%% %9.1f%%%n", "overall",
                    100 * share(v.stabSlots, v.attackSlots), 100 * share(r.stabSlots, r.attackSlots));

            System.out.println();
            System.out.println("-- distinct attacking types per species (report: mean 3.80, p90 6) --");
            System.out.printf("  %-12s %10s %10s%n", "", "vanilla", "randomized");
            System.out.printf("  %-12s %10.2f %10.2f%n", "mean",
                    v.speciesDistinctTypes.stream().mapToInt(Integer::intValue).average().orElse(0),
                    r.speciesDistinctTypes.stream().mapToInt(Integer::intValue).average().orElse(0));
            System.out.println("  histogram (types -> species):");
            Map<Integer, int[]> hist = new LinkedHashMap<>();
            for (int t : v.speciesDistinctTypes) {
                hist.computeIfAbsent(t, k -> new int[2])[0]++;
            }
            for (int t : r.speciesDistinctTypes) {
                hist.computeIfAbsent(t, k -> new int[2])[1]++;
            }
            for (int t : new TreeSet<>(hist.keySet())) {
                System.out.printf("    %-10d %10d %10d%n", t, hist.get(t)[0], hist.get(t)[1]);
            }

            System.out.println();
            System.out.println("-- mean BP by level band (report: 53.5/41.8/52.0/65.4/79.6/92.5/105.1) --");
            System.out.printf("  %-8s %10s %10s%n", "band", "vanilla", "randomized");
            for (int b = 0; b < BAND_NAMES.length; b++) {
                System.out.printf("  %-8s %10.1f %10.1f%n", BAND_NAMES[b],
                        v.bandBpCount[b] == 0 ? 0 : v.bandBpSum[b] / v.bandBpCount[b],
                        r.bandBpCount[b] == 0 ? 0 : r.bandBpSum[b] / r.bandBpCount[b]);
            }

            System.out.println();
            System.out.println("-- mean / max BP by BST bucket (report: mean 60.9->81.1, max 100->127) --");
            System.out.printf("  %-10s %8s %8s %8s %8s%n", "BST", "van mean", "rnd mean", "van max", "rnd max");
            for (int b = 0; b < BST_BUCKETS.length; b++) {
                System.out.printf("  %-10s %8.1f %8.1f %8.1f %8.1f%n", BST_BUCKETS[b],
                        v.bstCount[b] == 0 ? 0 : v.bstMeanSum[b] / v.bstCount[b],
                        r.bstCount[b] == 0 ? 0 : r.bstMeanSum[b] / r.bstCount[b],
                        v.bstCount[b] == 0 ? 0 : v.bstMaxSum[b] / v.bstCount[b],
                        r.bstCount[b] == 0 ? 0 : r.bstMaxSum[b] / r.bstCount[b]);
            }

            System.out.println();
            System.out.println("-- physical share of attacking slots by Atk-SpA (report: 40/31/57/79.5/90) --");
            System.out.printf("  %-10s %6s %10s %10s%n", "Atk-SpA", "n", "vanilla", "randomized");
            for (int b = 0; b < ATK_SPA_BUCKETS.length; b++) {
                System.out.printf("  %-10s %6d %9.1f%% %9.1f%%%n", ATK_SPA_BUCKETS[b], v.atkSpaSpecies[b],
                        100 * share(v.atkSpaPhysical[b], v.atkSpaTotal[b]),
                        100 * share(r.atkSpaPhysical[b], r.atkSpaTotal[b]));
            }

            System.out.println();
            System.out.println("-- punch moves per species, Iron Fist vs rest (report: 3.79 vs 0.16) --");
            System.out.printf("  %-12s %6s %10s %10s%n", "", "n", "vanilla", "randomized");
            System.out.printf("  %-12s %6d %10.2f %10.2f%n", "Iron Fist", v.ironFistSpecies,
                    share(v.ironFistPunch, v.ironFistSpecies), share(r.ironFistPunch, r.ironFistSpecies));
            System.out.printf("  %-12s %6d %10.2f %10.2f%n", "others", v.otherSpecies,
                    share(v.otherPunch, v.otherSpecies), share(r.otherPunch, r.otherSpecies));
            System.out.println();
        }

        private static double share(double numerator, double denominator) {
            return denominator == 0 ? 0 : numerator / denominator;
        }
    }

    private RomHandler loadAnyRomOfGenerationAtLeast(int minGeneration) {
        String romsDir = System.getProperty("romsPath");
        assumeTrue(romsDir != null, "romsPath not set");
        File dir = new File(romsDir);
        assumeTrue(dir.isDirectory(), "roms dir missing: " + romsDir);
        File[] files = dir.listFiles();
        assumeTrue(files != null && files.length > 0, "roms dir empty: " + romsDir);
        List<File> candidates = new ArrayList<>(Arrays.asList(files));
        candidates.sort(Comparator.comparingLong(File::length));

        for (File f : candidates) {
            if (!f.isFile() || f.getName().equalsIgnoreCase("readme.txt") || f.length() > MAX_ROM_BYTES
                    || f.length() < MIN_ROM_BYTES) {
                continue;
            }
            RomHandler rom = tryLoad(f.getAbsolutePath());
            if (rom != null && rom.generationOfPokemon() >= minGeneration) {
                System.out.println("  [using " + f.getName() + ", gen " + rom.generationOfPokemon() + "]");
                return rom;
            }
        }
        assumeTrue(false, "No loadable gen " + minGeneration + "+ ROM found in " + romsDir);
        return null;
    }

    private RomHandler tryLoad(String path) {
        for (Generation gen : new HashSet<>(Generation.GAME_TO_GENERATION.values())) {
            try {
                RomHandler.Factory factory = gen.createFactory();
                if (factory.isLoadable(path)) {
                    RomHandler rh = factory.create();
                    rh.loadRom(path);
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
