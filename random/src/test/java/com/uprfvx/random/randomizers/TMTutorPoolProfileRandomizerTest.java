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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Report-only profile harness for the TM and Move Tutor layer. <b>No assertions</b> - this is an
 * instrument, not a test. It exists so every tuning decision on
 * {@link TMTutorMoveRandomizer} / {@link TMHMTutorCompatibilityRandomizer} is a measurement rather
 * than an argument.
 * <p>
 * For each ROM it prints VANILLA (read from that same ROM before anything is randomized) next to
 * RANDOMISED (the same ROM after), next to the Gen 7 Ultra Sun figures from the
 * {@code modern-tm-tutor-pool-structure-report} research pass. Comparing randomised output against
 * the report alone would be meaningless on a Gen 1-5 ROM; the vanilla column is the honest baseline
 * and the report column is only there to show which direction vanilla design points.
 * <p>
 * <b>The randomised column pools {@link #REPEATS} seeded runs.</b> A single run is not a
 * measurement: two consecutive unseeded runs of the unmodified code gave a sub-90%-accuracy share of
 * 21.2% and then 7.1% on the same ROM, which is wider than any lever below is expected to move it.
 * Seeds are fixed so a re-run reproduces the previous numbers exactly and a diff means a code change.
 * <p>
 * Deliberately standalone (does not extend {@link RandomizerTest}) so it loads only the ROMs it
 * needs - the shared {@code @BeforeAll} loads the full USA catalogue and fails in this environment.
 * Named {@code *Randomizer*Test} so it runs under the {@code testROMs} Gradle task, which supplies
 * {@code romsPath}, and stays out of the fast {@code test} task.
 * <p>
 * Run: {@code ./gradlew.bat :random:testROMs --tests "*TMTutorPoolProfile*" --rerun-tasks}, then
 * read {@code random/build/test-results/testROMs/TEST-*.xml} - {@code testROMs} sets
 * {@code ignoreFailures=true}, so a green build proves nothing, and {@code skipped=} must be checked
 * against {@code tests=}. Repeat count is overridable with {@code -Dtm.repeats=N} for a quick pass.
 */
public class TMTutorPoolProfileRandomizerTest {

    private static final String ROMS_PATH = System.getProperty("romsPath");

    /** How many seeded randomization runs are pooled into the RANDOMISED column. */
    private static final int REPEATS = Integer.getInteger("tm.repeats", 20);

    /** Fixed so the harness is reproducible - a changed number means changed code, not a new roll. */
    private static final long SEED_BASE = 20260731L;

    /** BP histogram bucket width, matching the report's own tables. */
    private static final int BP_BUCKET = 20;
    private static final int BP_BUCKETS = 13; // 0-19 .. 240-259

    /** The BP threshold the report uses for the tutor pool's high-power second mode. */
    private static final int CAPSTONE_BP = 120;

    /** Accuracy below which the report counts a move as "inaccurate". */
    private static final int LOW_ACCURACY = 90;

    /**
     * Minimum stored power for a move to count toward the BP statistics. Fixed-damage and
     * variable-power moves (Seismic Toss, Night Shade, Psywave, ...) are stored with a sentinel
     * power of 1 rather than a real base power; PokeAPI records them as null BP and the report
     * excludes them. Including them dragged Ultra Sun's vanilla TM mean from 81.4 down to 75.4.
     */
    private static final int MIN_MEASURABLE_BP = 2;

    /** BST bucket edges, matching the report's six compatibility buckets. */
    private static final int[] BST_EDGES = {300, 400, 480, 540, 600};
    private static final String[] BST_LABELS = {"<300", "300-400", "400-480", "480-540", "540-600", "600+"};

    /** Per-move breadth tier edges (share of the dex able to learn it), from the report. */
    private static final double[] TIER_EDGES = {0.95, 0.60, 0.25, 0.10};
    private static final String[] TIER_LABELS = {"universal>=95%", "wide60-95%", "mid25-60%",
            "narrow10-25%", "rare<10%"};

    /** Game name (a {@link Generation} key) to the ROM file's base name in the roms folder. */
    static String[][] gamesToProfile() {
        return new String[][]{
                {"Red", "Red (U)"},
                {"Crystal", "Crystal (U)"},
                {"Emerald", "Emerald (U)"},
                {"Platinum", "Platinum (U)"},
                {"Black 2", "Black 2 (U)"},
                {"Omega Ruby", "Omega Ruby"},
                {"Ultra Sun", "Ultra Sun"},
        };
    }

    /**
     * Lets the tuning constants be swept from the command line without editing and rebuilding, e.g.
     * {@code -Dtm.accExp=1.7 -Dtm.sigma=35 -Dtm.floor=0.10}. The constants are deliberately non-final
     * on the randomizer for exactly this.
     */
    private void applyTuningOverrides() {
        TMTutorMoveRandomizer.TM_DAMAGING_SHARE =
                Double.parseDouble(System.getProperty("tm.damagingShare", "0.67"));
        TMTutorMoveRandomizer.TUTOR_DAMAGING_SHARE =
                Double.parseDouble(System.getProperty("tm.tutorDamagingShare", "0.667"));
        TMTutorMoveRandomizer.TM_ACCURACY_EXPONENT =
                Double.parseDouble(System.getProperty("tm.accExp", "1.7"));
        TMTutorMoveRandomizer.TM_POWER_CENTER =
                Double.parseDouble(System.getProperty("tm.center", "80"));
        TMTutorMoveRandomizer.TM_POWER_SIGMA_LOW =
                Double.parseDouble(System.getProperty("tm.sigmaLow", "28"));
        TMTutorMoveRandomizer.TM_POWER_SIGMA_HIGH =
                Double.parseDouble(System.getProperty("tm.sigmaHigh", "45"));
        TMTutorMoveRandomizer.TM_POWER_BAND_FLOOR =
                Double.parseDouble(System.getProperty("tm.floor", "0.05"));
        System.out.printf("  tuning: damagingShare=%.3f tutorDamagingShare=%.3f accExp=%.2f"
                        + " center=%.0f sigmaLow=%.0f sigmaHigh=%.0f floor=%.2f%n",
                TMTutorMoveRandomizer.TM_DAMAGING_SHARE, TMTutorMoveRandomizer.TUTOR_DAMAGING_SHARE,
                TMTutorMoveRandomizer.TM_ACCURACY_EXPONENT, TMTutorMoveRandomizer.TM_POWER_CENTER,
                TMTutorMoveRandomizer.TM_POWER_SIGMA_LOW, TMTutorMoveRandomizer.TM_POWER_SIGMA_HIGH,
                TMTutorMoveRandomizer.TM_POWER_BAND_FLOOR);
    }

    private RomHandler loadRom(String gameName, String fileBaseName) {
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");
        Generation gen = Generation.GAME_TO_GENERATION.get(gameName);
        String fullRomName = ROMS_PATH + "/" + fileBaseName + gen.getFileSuffix();
        assumeTrue(new File(fullRomName).exists(), "ROM not present: " + fullRomName);
        RomHandler.Factory factory = gen.createFactory();
        assumeTrue(factory.isLoadable(fullRomName), "ROM not loadable: " + fullRomName);
        RomHandler romHandler = factory.create();
        romHandler.loadRom(fullRomName);
        romHandler.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());
        return romHandler;
    }

    @ParameterizedTest
    @MethodSource("gamesToProfile")
    public void profileTMAndTutorLayer(String gameName, String fileBaseName) {
        applyTuningOverrides();
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        List<Move> allMoves = romHandler.getMoves();
        int perfectAccuracy = romHandler.getPerfectAccuracy();
        int generation = romHandler.generationOfPokemon();
        int tmCount = romHandler.getTMCount();
        boolean hasTutors = romHandler.hasMoveTutors();

        header(gameName + "  (gen " + generation + ", " + tmCount + " TMs, "
                + (hasTutors ? romHandler.getMoveTutorMoves().size() + " tutors" : "no tutors")
                + ", " + REPEATS + " pooled runs)");

        // ---- VANILLA snapshot, taken before anything is randomized -------------------------
        List<Integer> vanillaTMs = List.copyOf(romHandler.getTMMoves());
        List<Integer> vanillaTutors = hasTutors ? List.copyOf(romHandler.getMoveTutorMoves()) : List.of();
        List<Map<Species, boolean[]>> vanillaTMCompat = List.of(deepCopy(romHandler.getTMHMCompatibility()));
        List<Map<Species, boolean[]>> vanillaTutorCompat = hasTutors
                ? List.of(deepCopy(romHandler.getMoveTutorCompatibility())) : List.of();
        // Level-up learnsets are left vanilla throughout, so the C4 rows measure the compatibility
        // model in isolation rather than compounding two randomizers.
        Map<Integer, List<MoveLearnt>> learnsets = romHandler.getMovesLearnt();

        // ---- RANDOMISED runs, pooled over REPEATS fixed seeds -------------------------------
        List<List<Integer>> randomTMs = new ArrayList<>();
        List<List<Integer>> randomTutors = new ArrayList<>();
        List<Map<Species, boolean[]>> preferTypeTMCompat = new ArrayList<>();
        List<Map<Species, boolean[]>> preferTypeSanityTMCompat = new ArrayList<>();
        List<Map<Species, boolean[]>> plainRandomTMCompat = new ArrayList<>();
        List<Map<Species, boolean[]>> preferTypeTutorCompat = new ArrayList<>();

        for (int run = 0; run < REPEATS; run++) {
            Random random = new Random(SEED_BASE + run);
            Settings settings = new Settings();
            settings.setTmsMod(Settings.TMsMod.RANDOM);
            settings.setMoveTutorMovesMod(Settings.MoveTutorMovesMod.RANDOM);

            TMTutorMoveRandomizer moveRandomizer = new TMTutorMoveRandomizer(romHandler, settings, random);
            moveRandomizer.randomizeTMMoves();
            moveRandomizer.randomizeMoveTutorMoves();
            randomTMs.add(List.copyOf(romHandler.getTMMoves()));
            randomTutors.add(hasTutors ? List.copyOf(romHandler.getMoveTutorMoves()) : List.of());

            // Compatibility runs under both mods - they produce very different structure today, and
            // the spec's gating table treats them differently, so both need to be visible.
            settings.setTmsHmsCompatibilityMod(Settings.TMsHMsCompatibilityMod.RANDOM_PREFER_TYPE);
            settings.setMoveTutorsCompatibilityMod(Settings.MoveTutorsCompatibilityMod.RANDOM_PREFER_TYPE);
            TMHMTutorCompatibilityRandomizer compat =
                    new TMHMTutorCompatibilityRandomizer(romHandler, settings, random);
            compat.randomizeTMHMCompatibility();
            preferTypeTMCompat.add(deepCopy(romHandler.getTMHMCompatibility()));
            // Same run, plus the existing "TM/Levelup Move Sanity" option, so its contribution can be
            // read off directly rather than argued about. It force-flags the TM of any move the
            // species already learns by level-up - an exact move-ID match.
            compat.ensureTMCompatSanity();
            preferTypeSanityTMCompat.add(deepCopy(romHandler.getTMHMCompatibility()));
            if (hasTutors) {
                compat.randomizeMoveTutorCompatibility();
                preferTypeTutorCompat.add(deepCopy(romHandler.getMoveTutorCompatibility()));
            }

            settings.setTmsHmsCompatibilityMod(Settings.TMsHMsCompatibilityMod.COMPLETELY_RANDOM);
            compat = new TMHMTutorCompatibilityRandomizer(romHandler, settings, random);
            compat.randomizeTMHMCompatibility();
            plainRandomTMCompat.add(deepCopy(romHandler.getTMHMCompatibility()));
        }

        // ---- Content tables (L1 - L6) ------------------------------------------------------
        PoolStats vanillaTM = poolStats(List.of(vanillaTMs), allMoves, perfectAccuracy);
        PoolStats randomTM = poolStats(randomTMs, allMoves, perfectAccuracy);
        // Measured against the VANILLA pool, matching the report's own "roster minus TM/tutor"
        // comparison, and giving a fixed reference that does not move when a lever lands.
        PoolStats leftOut = poolStats(List.of(leftOutOf(vanillaTMs, vanillaTutors, allMoves)),
                allMoves, perfectAccuracy);
        PoolStats vanillaTutor = poolStats(List.of(vanillaTutors), allMoves, perfectAccuracy);
        PoolStats randomTutor = poolStats(randomTutors, allMoves, perfectAccuracy);

        section("L1  damaging / status share of the pool        [report: TM 67/33, tutor 66.7/33.3]");
        row("TM  vanilla", vanillaTM.damagingStatusLine());
        row("TM  randomised", randomTM.damagingStatusLine());
        if (hasTutors) {
            row("Tut vanilla", vanillaTutor.damagingStatusLine());
            row("Tut randomised", randomTutor.damagingStatusLine());
        }

        section("L2  physical / special of the damaging half    [report: TM 52/48; non-TM roster 62/38]"
                + (generation <= 3 ? "   (gen 1-3: category is derived from type, not independent)" : ""));
        row("TM  vanilla", vanillaTM.physSpecLine());
        row("TM  randomised", randomTM.physSpecLine());
        row("    left out (vanilla)", leftOut.physSpecLine());
        if (hasTutors) {
            row("Tut vanilla", vanillaTutor.physSpecLine());
            row("Tut randomised", randomTutor.physSpecLine());
        }

        section("L3  accuracy of damaging moves                 [report: TM 96.6 mean / 7.7% sub-90;"
                + " left out 94.8 / 15.4%]");
        row("TM  vanilla", vanillaTM.accuracyLine());
        row("TM  randomised", randomTM.accuracyLine());
        row("    left out (vanilla)", leftOut.accuracyLine());
        if (hasTutors) {
            row("Tut vanilla", vanillaTutor.accuracyLine());
            row("Tut randomised", randomTutor.accuracyLine());
        }

        section("L4  base power of damaging moves               [report: TM mean 81.4, median 80,"
                + " p25 60, p75 90; 66% in 60-99]");
        row("TM  vanilla", vanillaTM.powerLine());
        row("TM  randomised", randomTM.powerLine());
        row("    left out (vanilla)", leftOut.powerLine());
        if (hasTutors) {
            row("Tut vanilla", vanillaTutor.powerLine());
            row("Tut randomised", randomTutor.powerLine());
        }
        System.out.println();
        System.out.println("      BP histogram of damaging moves, buckets of " + BP_BUCKET
                + " (counts per run)");
        printHistogram("TM  vanilla", vanillaTM);
        printHistogram("TM  randomised", randomTM);
        if (hasTutors) {
            printHistogram("Tut vanilla", vanillaTutor);
            printHistogram("Tut randomised", randomTutor);
        }

        section("L5  damaging moves per type (per run)          [report: every type >=1 from gen 4 on]");
        printTypeCoverage("TM  vanilla", vanillaTM, generation);
        printTypeCoverage("TM  randomised", randomTM, generation);
        if (hasTutors) {
            printTypeCoverage("Tut vanilla", vanillaTutor, generation);
            printTypeCoverage("Tut randomised", randomTutor, generation);
        }

        if (hasTutors) {
            section("L6  tutor pool vs TM pool                      [report: tutor mean BP 89.0 vs 81.4;"
                    + " >=120 BP 24.5% vs 9.7%]");
            row("vanilla    TM / tutor", String.format("mean BP %5.1f / %5.1f    >=%d BP %5.1f%% / %5.1f%%",
                    vanillaTM.bpMean, vanillaTutor.bpMean, CAPSTONE_BP,
                    100 * vanillaTM.capstoneShare, 100 * vanillaTutor.capstoneShare));
            row("randomised TM / tutor", String.format("mean BP %5.1f / %5.1f    >=%d BP %5.1f%% / %5.1f%%",
                    randomTM.bpMean, randomTutor.bpMean, CAPSTONE_BP,
                    100 * randomTM.capstoneShare, 100 * randomTutor.capstoneShare));
        }

        // ---- Compatibility tables (C1 - C6) ------------------------------------------------
        List<Species> species = compatSpecies(vanillaTMCompat.get(0));

        section("C1  TMs learnable per species                  [report: mean 31.5%, median 31%,"
                + " 84% of dex in 20-49 of 100]");
        row("vanilla", densityLine(vanillaTMCompat, species, tmCount));
        row("randomised prefer-type", densityLine(preferTypeTMCompat, species, tmCount));
        row("  + levelup sanity", densityLine(preferTypeSanityTMCompat, species, tmCount));
        row("randomised plain random", densityLine(plainRandomTMCompat, species, tmCount));
        if (hasTutors) {
            int tutorCount = vanillaTutors.size();
            row("tutors vanilla", densityLine(vanillaTutorCompat, species, tutorCount));
            row("tutors prefer-type", densityLine(preferTypeTutorCompat, species, tutorCount));
        }
        System.out.println();
        System.out.println("      per-species TM count histogram, buckets of 10% (species per run)");
        printDensityHistogram("vanilla", vanillaTMCompat, species, tmCount);
        printDensityHistogram("prefer-type", preferTypeTMCompat, species, tmCount);
        printDensityHistogram("plain random", plainRandomTMCompat, species, tmCount);

        section("C2  TM count by BST bucket                     [report: 23.7 / 28.9 / 31.5 / 34.8 /"
                + " 34.9 / 42.3 of 100]");
        printBstLadder("vanilla", vanillaTMCompat, species, tmCount);
        printBstLadder("prefer-type", preferTypeTMCompat, species, tmCount);
        printBstLadder("plain random", plainRandomTMCompat, species, tmCount);
        System.out.println();
        System.out.println("      stand-alone (never evolves, no prevo) vs base-stage-that-evolves"
                + "   [report: 35.8 vs 27.6 of 100]");
        row("vanilla", standAloneLine(vanillaTMCompat, species, tmCount));
        row("prefer-type", standAloneLine(preferTypeTMCompat, species, tmCount));
        row("plain random", standAloneLine(plainRandomTMCompat, species, tmCount));

        section("C3  per-move breadth tiers (moves per run)     [report: 13 universal / 3 wide /"
                + " 26 mid / 45 narrow / 13 rare, span 1.1-97.9%]");
        printBreadthTiers("vanilla", vanillaTMCompat, species, tmCount);
        printBreadthTiers("prefer-type", preferTypeTMCompat, species, tmCount);
        printBreadthTiers("plain random", plainRandomTMCompat, species, tmCount);

        section("C4  learn rate by level-up type identity       [report: 56.8% vs 12.9% (4.39x);"
                + " off-type 48.9% vs 12.8% (3.83x)]");
        Map<Integer, Set<Type>> identity = levelUpAttackingTypes(species, learnsets, allMoves);
        printIdentityTable("vanilla", vanillaTMCompat, species, List.of(vanillaTMs), allMoves, identity);
        printIdentityTable("prefer-type", preferTypeTMCompat, species, randomTMs, allMoves, identity);
        printIdentityTable("+levelup sanity", preferTypeSanityTMCompat, species, randomTMs, allMoves, identity);
        printIdentityTable("plain random", plainRandomTMCompat, species, randomTMs, allMoves, identity);

        section("C5/C6  on-type vs off-type learn rate          [report: damaging 74.4/20.9 (3.56x),"
                + " status 77.8/39.3 (1.98x), Normal-typed 64.7/63.1 (1.03x)]");
        printStabTable("vanilla", vanillaTMCompat, species, List.of(vanillaTMs), allMoves);
        printStabTable("prefer-type", preferTypeTMCompat, species, randomTMs, allMoves);
        printStabTable("plain random", plainRandomTMCompat, species, randomTMs, allMoves);

        System.out.println();
    }

    // ================================ pool statistics ====================================

    /** Everything the content levers (L1-L6) are measured on, pooled across runs. */
    private static final class PoolStats {
        int runs;
        int n, damaging, status, physical, special, bpN, fixedDamage;
        double meanAccuracy, subNinetyShare;
        double bpMean, bpMedian, bpP25, bpP75;
        int bpMax;
        double capstoneShare;
        final int[] bpHist = new int[BP_BUCKETS];
        final Map<Type, Integer> damagingByType = new LinkedHashMap<>();
        /**
         * Damaging-move counts by type for each run separately. L5 is about the tail risk of a run
         * missing a type entirely, which an average across runs hides completely - the pooled mean
         * showed "0 types below 1 per run" for a pool whose individual runs were dropping Ghost and
         * Fairy outright.
         */
        final List<Map<Type, Integer>> damagingByTypePerRun = new ArrayList<>();

        String damagingStatusLine() {
            return String.format("n=%5.1f   damaging %5.1f (%5.1f%%)   status %5.1f (%5.1f%%)"
                            + "   fixed/variable damage %4.1f",
                    perRun(n), perRun(damaging), pct(damaging, n), perRun(status), pct(status, n),
                    perRun(fixedDamage));
        }

        String physSpecLine() {
            return String.format("physical %5.1f (%5.1f%%)   special %5.1f (%5.1f%%)",
                    perRun(physical), pct(physical, physical + special),
                    perRun(special), pct(special, physical + special));
        }

        String accuracyLine() {
            return String.format("mean accuracy %5.1f   sub-%d%% share %5.1f%%",
                    meanAccuracy, LOW_ACCURACY, 100 * subNinetyShare);
        }

        String powerLine() {
            // The 60-99 band is the report's headline shape figure: 41 of 62 damaging TMs sit there.
            int inBand = bpHist[3] + bpHist[4];
            return String.format("n=%5.1f   mean %5.1f   median %3.0f   p25 %3.0f   p75 %3.0f   max %3d"
                            + "   60-99 band %5.1f%%",
                    perRun(bpN), bpMean, bpMedian, bpP25, bpP75, bpMax, pct(inBand, bpN));
        }

        private double perRun(int total) {
            return runs == 0 ? 0 : (double) total / runs;
        }
    }

    private PoolStats poolStats(List<List<Integer>> pools, List<Move> allMoves, int perfectAccuracy) {
        PoolStats s = new PoolStats();
        List<Double> powers = new ArrayList<>();
        double accSum = 0;
        int accN = 0, lowAcc = 0, capstone = 0;

        for (List<Integer> moveIds : pools) {
            s.runs++;
            Map<Type, Integer> thisRunByType = new LinkedHashMap<>();
            s.damagingByTypePerRun.add(thisRunByType);
            for (int id : moveIds) {
                Move mv = allMoves.get(id);
                s.n++;
                if (mv.category == MoveCategory.STATUS) {
                    s.status++;
                    continue;
                }
                s.damaging++;
                if (mv.category == MoveCategory.PHYSICAL) {
                    s.physical++;
                } else {
                    s.special++;
                }
                s.damagingByType.merge(mv.type, 1, Integer::sum);
                thisRunByType.merge(mv.type, 1, Integer::sum);

                // Perfect-accuracy moves use a sentinel, not 100 - count them as never missing.
                double accuracy = (mv.hitratio == perfectAccuracy) ? 100 : mv.hitratio;
                if (accuracy > 0) {
                    accSum += accuracy;
                    accN++;
                    if (accuracy < LOW_ACCURACY) {
                        lowAcc++;
                    }
                }
                if (mv.power >= MIN_MEASURABLE_BP) {
                    powers.add((double) mv.power);
                    s.bpHist[Math.min(BP_BUCKETS - 1, mv.power / BP_BUCKET)]++;
                    s.bpN++;
                    s.bpMax = Math.max(s.bpMax, mv.power);
                    if (mv.power >= CAPSTONE_BP) {
                        capstone++;
                    }
                } else {
                    s.fixedDamage++;
                }
            }
        }

        s.meanAccuracy = accN == 0 ? 0 : accSum / accN;
        s.subNinetyShare = accN == 0 ? 0 : (double) lowAcc / accN;
        s.capstoneShare = powers.isEmpty() ? 0 : (double) capstone / powers.size();
        powers.sort(Comparator.naturalOrder());
        s.bpMean = mean(powers);
        s.bpMedian = percentile(powers, 0.50);
        s.bpP25 = percentile(powers, 0.25);
        s.bpP75 = percentile(powers, 0.75);
        return s;
    }

    /**
     * The moves the vanilla game did NOT put in a TM or tutor - the comparison the report actually
     * makes when it calls accuracy "the real selection filter" (§4b).
     */
    private List<Integer> leftOutOf(List<Integer> tms, List<Integer> tutors, List<Move> allMoves) {
        List<Integer> rest = new ArrayList<>();
        for (int id = 1; id < allMoves.size(); id++) {
            Move mv = allMoves.get(id);
            if (mv == null || mv.name == null || mv.name.isEmpty()) {
                continue;
            }
            if (!tms.contains(id) && !tutors.contains(id)) {
                rest.add(id);
            }
        }
        return rest;
    }

    private void printHistogram(String label, PoolStats stats) {
        StringBuilder sb = new StringBuilder(String.format("      %-16s", label));
        for (int i = 0; i < BP_BUCKETS; i++) {
            if (stats.bpHist[i] > 0) {
                sb.append(String.format("  %d-%d:%.1f", i * BP_BUCKET, i * BP_BUCKET + BP_BUCKET - 1,
                        (double) stats.bpHist[i] / stats.runs));
            }
        }
        System.out.println(sb);
    }

    private void printTypeCoverage(String label, PoolStats stats, int generation) {
        List<Type> types = Type.getAllTypes(generation);
        StringBuilder sb = new StringBuilder();
        for (Type t : types) {
            sb.append(String.format("%s:%.1f ", shortType(t),
                    (double) stats.damagingByType.getOrDefault(t, 0) / stats.runs));
        }
        // The tail risk is per run, not on average: a pool that averages 1.4 Fairy moves still has
        // runs with none, and that is precisely what L5 exists to prevent.
        int totalMissing = 0;
        int runsWithAGap = 0;
        int worstRun = 0;
        for (Map<Type, Integer> runCounts : stats.damagingByTypePerRun) {
            int missingThisRun = 0;
            for (Type t : types) {
                if (runCounts.getOrDefault(t, 0) == 0) {
                    missingThisRun++;
                }
            }
            totalMissing += missingThisRun;
            worstRun = Math.max(worstRun, missingThisRun);
            if (missingThisRun > 0) {
                runsWithAGap++;
            }
        }
        System.out.printf("      %-16s %s%n", label, sb.toString().trim());
        System.out.printf("      %-16s   -> types with ZERO damaging moves: mean %.2f/run, worst run %d,"
                        + " runs with a gap %d/%d (of %d types)%n",
                "", (double) totalMissing / stats.runs, worstRun, runsWithAGap, stats.runs, types.size());
    }

    // ============================ compatibility statistics ================================

    /**
     * The species the compatibility model actually iterates, minus cosmetic formes (which copy their
     * base forme's flags wholesale and would double-count).
     */
    private List<Species> compatSpecies(Map<Species, boolean[]> compat) {
        List<Species> out = new ArrayList<>();
        for (Species pk : compat.keySet()) {
            if (!pk.isEssentiallyCosmetic()) {
                out.add(pk);
            }
        }
        out.sort(Comparator.comparingInt(Species::getNumber));
        return out;
    }

    /** Number of TMs (indices 1..poolSize, i.e. excluding HMs) this species can learn. */
    private int tmCountFor(boolean[] flags, int poolSize) {
        int count = 0;
        for (int i = 1; i <= poolSize && i < flags.length; i++) {
            if (flags[i]) {
                count++;
            }
        }
        return count;
    }

    private String densityLine(List<Map<Species, boolean[]>> compats, List<Species> species, int poolSize) {
        List<Double> shares = new ArrayList<>();
        int zero = 0;
        for (Map<Species, boolean[]> compat : compats) {
            for (Species pk : species) {
                boolean[] flags = compat.get(pk);
                if (flags == null) {
                    continue;
                }
                int count = tmCountFor(flags, poolSize);
                if (count == 0) {
                    zero++;
                }
                shares.add(100.0 * count / poolSize);
            }
        }
        shares.sort(Comparator.naturalOrder());
        return String.format("mean %5.1f%%   median %5.1f%%   p10 %5.1f%%   p90 %5.1f%%"
                        + "   zero-TM species %5.1f/run",
                mean(shares), percentile(shares, 0.50), percentile(shares, 0.10), percentile(shares, 0.90),
                (double) zero / compats.size());
    }

    private void printDensityHistogram(String label, List<Map<Species, boolean[]>> compats,
                                       List<Species> species, int poolSize) {
        int[] hist = new int[11];
        for (Map<Species, boolean[]> compat : compats) {
            for (Species pk : species) {
                boolean[] flags = compat.get(pk);
                if (flags == null) {
                    continue;
                }
                int bucket = (int) (10.0 * tmCountFor(flags, poolSize) / poolSize);
                hist[Math.min(10, bucket)]++;
            }
        }
        StringBuilder sb = new StringBuilder(String.format("      %-14s", label));
        for (int i = 0; i < hist.length; i++) {
            if (hist[i] > 0) {
                sb.append(String.format("  %d-%d%%:%.0f", i * 10, i * 10 + 9,
                        (double) hist[i] / compats.size()));
            }
        }
        System.out.println(sb);
    }

    private int bstBucket(Species pk) {
        int bst = pk.getBSTForPowerLevels();
        for (int i = 0; i < BST_EDGES.length; i++) {
            if (bst < BST_EDGES[i]) {
                return i;
            }
        }
        return BST_EDGES.length;
    }

    private void printBstLadder(String label, List<Map<Species, boolean[]>> compats,
                                List<Species> species, int poolSize) {
        double[] sum = new double[BST_LABELS.length];
        int[] n = new int[BST_LABELS.length];
        for (Map<Species, boolean[]> compat : compats) {
            for (Species pk : species) {
                boolean[] flags = compat.get(pk);
                if (flags == null) {
                    continue;
                }
                int b = bstBucket(pk);
                sum[b] += 100.0 * tmCountFor(flags, poolSize) / poolSize;
                n[b]++;
            }
        }
        StringBuilder sb = new StringBuilder(String.format("      %-14s", label));
        for (int i = 0; i < BST_LABELS.length; i++) {
            sb.append(String.format("  %s: %4.1f%% (n=%d)", BST_LABELS[i],
                    n[i] == 0 ? 0 : sum[i] / n[i], n[i] / compats.size()));
        }
        System.out.println(sb);
    }

    private String standAloneLine(List<Map<Species, boolean[]>> compats, List<Species> species, int poolSize) {
        double standAloneSum = 0, evolvingSum = 0;
        int standAloneN = 0, evolvingN = 0;
        for (Map<Species, boolean[]> compat : compats) {
            for (Species pk : species) {
                boolean[] flags = compat.get(pk);
                if (flags == null || !pk.getEvolutionsTo().isEmpty()) {
                    continue; // base stage only, so the two groups are comparable
                }
                double share = 100.0 * tmCountFor(flags, poolSize) / poolSize;
                if (pk.getEvolutionsFrom().isEmpty()) {
                    standAloneSum += share;
                    standAloneN++;
                } else {
                    evolvingSum += share;
                    evolvingN++;
                }
            }
        }
        return String.format("stand-alone %5.1f%% (n=%d)   evolving base %5.1f%% (n=%d)",
                standAloneN == 0 ? 0 : standAloneSum / standAloneN, standAloneN / compats.size(),
                evolvingN == 0 ? 0 : evolvingSum / evolvingN, evolvingN / compats.size());
    }

    private void printBreadthTiers(String label, List<Map<Species, boolean[]>> compats,
                                   List<Species> species, int poolSize) {
        int[] tiers = new int[TIER_LABELS.length];
        double min = 1.0, max = 0.0;
        for (Map<Species, boolean[]> compat : compats) {
            for (int i = 1; i <= poolSize; i++) {
                int learners = 0;
                for (Species pk : species) {
                    boolean[] flags = compat.get(pk);
                    if (flags != null && i < flags.length && flags[i]) {
                        learners++;
                    }
                }
                double breadth = (double) learners / species.size();
                min = Math.min(min, breadth);
                max = Math.max(max, breadth);
                int tier = TIER_EDGES.length;
                for (int t = 0; t < TIER_EDGES.length; t++) {
                    if (breadth >= TIER_EDGES[t]) {
                        tier = t;
                        break;
                    }
                }
                tiers[tier]++;
            }
        }
        StringBuilder sb = new StringBuilder(String.format("      %-14s", label));
        for (int t = 0; t < TIER_LABELS.length; t++) {
            sb.append(String.format("  %s: %4.1f", TIER_LABELS[t], (double) tiers[t] / compats.size()));
        }
        sb.append(String.format("   span %.1f%%-%.1f%%", 100 * min, 100 * max));
        System.out.println(sb);
    }

    /** The attacking types a species already has from its level-up learnset (report §7c). */
    private Map<Integer, Set<Type>> levelUpAttackingTypes(List<Species> species,
                                                         Map<Integer, List<MoveLearnt>> learnsets,
                                                         List<Move> allMoves) {
        Map<Integer, Set<Type>> out = new HashMap<>();
        for (Species pk : species) {
            Set<Type> types = new HashSet<>();
            List<MoveLearnt> learnset = learnsets.get(pk.getNumber());
            if (learnset != null) {
                for (MoveLearnt ml : learnset) {
                    Move mv = allMoves.get(ml.move);
                    if (mv != null && mv.category != MoveCategory.STATUS) {
                        types.add(mv.type);
                    }
                }
            }
            out.put(pk.getNumber(), types);
        }
        return out;
    }

    private boolean isOnType(Species pk, Move mv) {
        return pk.getPrimaryType(false) == mv.type
                || (pk.getSecondaryType(false) != null && pk.getSecondaryType(false) == mv.type);
    }

    private void printIdentityTable(String label, List<Map<Species, boolean[]>> compats, List<Species> species,
                                    List<List<Integer>> tmMoveLists, List<Move> allMoves,
                                    Map<Integer, Set<Type>> identity) {
        // [hasTypeByLevelUp][onType] -> learned / total
        int[][] learned = new int[2][2];
        int[][] total = new int[2][2];
        for (int run = 0; run < compats.size(); run++) {
            Map<Species, boolean[]> compat = compats.get(run);
            List<Integer> tmMoves = tmMoveLists.get(Math.min(run, tmMoveLists.size() - 1));
            for (Species pk : species) {
                boolean[] flags = compat.get(pk);
                if (flags == null) {
                    continue;
                }
                Set<Type> known = identity.getOrDefault(pk.getNumber(), Set.of());
                for (int i = 0; i < tmMoves.size() && i + 1 < flags.length; i++) {
                    Move mv = allMoves.get(tmMoves.get(i));
                    int has = known.contains(mv.type) ? 1 : 0;
                    int on = isOnType(pk, mv) ? 1 : 0;
                    total[has][on]++;
                    if (flags[i + 1]) {
                        learned[has][on]++;
                    }
                }
            }
        }
        double allHas = pct(learned[1][0] + learned[1][1], total[1][0] + total[1][1]);
        double allNot = pct(learned[0][0] + learned[0][1], total[0][0] + total[0][1]);
        double offHas = pct(learned[1][0], total[1][0]);
        double offNot = pct(learned[0][0], total[0][0]);
        System.out.printf("      %-14s has-type %5.1f%% vs not %5.1f%% (%.2fx)   |   off-type only:"
                        + " has %5.1f%% vs not %5.1f%% (%.2fx)%n",
                label, allHas, allNot, allNot == 0 ? 0 : allHas / allNot,
                offHas, offNot, offNot == 0 ? 0 : offHas / offNot);
    }

    private void printStabTable(String label, List<Map<Species, boolean[]>> compats, List<Species> species,
                                List<List<Integer>> tmMoveLists, List<Move> allMoves) {
        // [isStatus][onType] -> learned / total, plus a separate Normal-typed pair (the null case)
        int[][] learned = new int[2][2];
        int[][] total = new int[2][2];
        int[] normalLearned = new int[2];
        int[] normalTotal = new int[2];
        for (int run = 0; run < compats.size(); run++) {
            Map<Species, boolean[]> compat = compats.get(run);
            List<Integer> tmMoves = tmMoveLists.get(Math.min(run, tmMoveLists.size() - 1));
            for (Species pk : species) {
                boolean[] flags = compat.get(pk);
                if (flags == null) {
                    continue;
                }
                for (int i = 0; i < tmMoves.size() && i + 1 < flags.length; i++) {
                    Move mv = allMoves.get(tmMoves.get(i));
                    int status = mv.category == MoveCategory.STATUS ? 1 : 0;
                    int on = isOnType(pk, mv) ? 1 : 0;
                    total[status][on]++;
                    if (flags[i + 1]) {
                        learned[status][on]++;
                    }
                    if (mv.type == Type.NORMAL) {
                        normalTotal[on]++;
                        if (flags[i + 1]) {
                            normalLearned[on]++;
                        }
                    }
                }
            }
        }
        double normalOn = pct(normalLearned[1], normalTotal[1]);
        double normalOff = pct(normalLearned[0], normalTotal[0]);
        System.out.printf("      %-14s damaging on/off %5.1f%%/%5.1f%% (%.2fx)   status on/off"
                        + " %5.1f%%/%5.1f%% (%.2fx)   Normal-typed on/off %5.1f%%/%5.1f%% (%.2fx)%n",
                label,
                pct(learned[0][1], total[0][1]), pct(learned[0][0], total[0][0]), lift(learned, total, 0),
                pct(learned[1][1], total[1][1]), pct(learned[1][0], total[1][0]), lift(learned, total, 1),
                normalOn, normalOff, normalOff == 0 ? 0 : normalOn / normalOff);
    }

    private double lift(int[][] learned, int[][] total, int row) {
        double off = pct(learned[row][0], total[row][0]);
        return off == 0 ? 0 : pct(learned[row][1], total[row][1]) / off;
    }

    // ================================== small helpers ====================================

    private Map<Species, boolean[]> deepCopy(Map<Species, boolean[]> compat) {
        Map<Species, boolean[]> copy = new LinkedHashMap<>();
        for (Map.Entry<Species, boolean[]> e : compat.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        return copy;
    }

    private static double pct(int part, int whole) {
        return whole == 0 ? 0 : 100.0 * part / whole;
    }

    private static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    /** Nearest-rank percentile on an already-sorted list. */
    private static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private static String shortType(Type t) {
        String name = t.name();
        return name.length() <= 3 ? name : name.substring(0, 3);
    }

    private void header(String title) {
        System.out.println();
        System.out.println("================================================================================");
        System.out.println("  " + title);
        System.out.println("================================================================================");
    }

    private void section(String title) {
        System.out.println();
        System.out.println("  " + title);
    }

    private void row(String label, String body) {
        System.out.printf("      %-24s %s%n", label, body);
    }
}
