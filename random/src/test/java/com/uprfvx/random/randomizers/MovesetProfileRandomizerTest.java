package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Diagnostic (report-only) harness for the Better Movesets boss/regular attack profile - the in-process
 * replacement for the build-package -&gt; GUI -&gt; save-log -&gt; parse loop that calibration previously required.
 * <p>
 * It runs the REAL {@link TrainerMovesetRandomizer} over one ROM per generation and prints the tables that
 * calibration cares about, using the authoritative {@code isBoss()/isImportant()} tier flags (not trainer-name
 * guessing): boss vs regular average attacks / distinct attacking types / useful-vs-junk status, plus the boss
 * attack-count distribution BY LEVEL BAND (which is what surfaces the high-level-boss offensive gap). It asserts
 * nothing - the CI guardrail lives in {@link BetterMovesetsRandomizerTest}; this is a tuning instrument.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MovesetProfile*" }</pre>
 * <b>Sweep mode</b> calibrates a tuning knob without a rebuild: pass a comma-separated value list and the harness
 * re-runs the whole profile once per value, printing each labelled table so the values can be compared directly.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MovesetProfile*" -Dbm.sweep=3,4,5,6 }</pre>
 * Skips (rather than fails) any generation whose ROM is absent from {@code roms/}, and skips entirely if none load.
 */
public class MovesetProfileRandomizerTest {

    private static final long SEED = 20260712L;
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

    /** Fixed/proportional-damage moves that count as attacks despite storing no usable base power. */
    private static final Set<Integer> SYNTHETIC_DAMAGE_MOVES = Set.of(
            MoveIDs.seismicToss, MoveIDs.nightShade, MoveIDs.superFang, MoveIDs.naturesMadness, MoveIDs.endeavor);

    @Test
    public void profileAtCurrentTuning() {
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");
        System.out.printf("%n### Better Movesets profile (bossWildcardDamagingBonus=%.1f, seed %d) ###%n",
                TrainerMovesetRandomizer.bossWildcardDamagingBonus, SEED);
        int loaded = 0;
        for (String[] game : GAMES) {
            RomHandler rom = tryLoad(game[0], game[1]);
            if (rom == null) {
                System.out.println("  (skip - ROM absent: " + game[0] + ")");
                continue;
            }
            loaded++;
            Profile p = computeProfile(rom);
            System.out.printf("%n== %s (gen %d) ==%n", game[0], rom.generationOfPokemon());
            p.printSummary("  ");
            p.printBossByBand("  ");
            p.printThinPoolBosses("  ");
            p.printAceVsTeam("  ");
            p.printWeakStab("  ");
            p.printRoleCoverage("  ");
        }
        assumeTrue(loaded > 0, "No loadable ROM found in " + ROMS_PATH);
    }

    @Test
    public void sweepBossWildcardBonus() {
        sweep("bm.sweep",
                "sweep skipped - pass -Dbm.sweep=<comma-separated bonus values> to calibrate",
                v -> TrainerMovesetRandomizer.bossWildcardDamagingBonus = v,
                () -> TrainerMovesetRandomizer.bossWildcardDamagingBonus,
                "bossWildcardDamagingBonus=%.1f",
                combined -> combined.printBossByBand("  "));
    }

    /**
     * Calibrates the boss STAB hard floor fraction ({@link TrainerMovesetRandomizer#POWER_FLOOR_FRACTION});
     * a small value (e.g. 0.1) effectively disables the cull, a large one (e.g. 0.95) makes it very strict.
     * Note this fraction is shared with the soft floor used by every other slot, so a sweep here also shifts
     * coverage/wildcard/best-damaging picks - watch printSummary alongside printWeakStab for side effects.
     * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MovesetProfile*.sweepStabFloor" -Dbm.stabfloor=0.1,0.5,0.65,0.75,0.85 }</pre>
     */
    @Test
    public void sweepStabFloor() {
        sweep("bm.stabfloor",
                "sweep skipped - pass -Dbm.stabfloor=<comma-separated fractions of centerPower> to calibrate",
                v -> TrainerMovesetRandomizer.POWER_FLOOR_FRACTION = v,
                () -> TrainerMovesetRandomizer.POWER_FLOOR_FRACTION,
                "POWER_FLOOR_FRACTION=%.2f",
                combined -> combined.printWeakStab("  "));
    }

    /**
     * Calibrates the boss/important accuracy-reliability exponent
     * ({@link TrainerMovesetRandomizer#ACCURACY_PENALTY_EXPONENT}) — higher pushes boss STAB/coverage/status
     * further toward reliable moves, away from low-accuracy nukes. Watch printWeakStab (target: lower) alongside
     * printSummary (target: boss avg-attacks/breadth must not drop below the BOSS_BREADTH_TOLERANCE margin from
     * regular, per the existing guardrail in BetterMovesetsRandomizerTest).
     * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MovesetProfile*.sweepAccuracyExponent" -Dbm.accuracyexp=2.0,2.5,3.0 }</pre>
     */
    @Test
    public void sweepAccuracyExponent() {
        sweep("bm.accuracyexp",
                "sweep skipped - pass -Dbm.accuracyexp=<comma-separated exponents> to calibrate",
                v -> TrainerMovesetRandomizer.ACCURACY_PENALTY_EXPONENT = v,
                () -> TrainerMovesetRandomizer.ACCURACY_PENALTY_EXPONENT,
                "ACCURACY_PENALTY_EXPONENT=%.2f",
                combined -> combined.printWeakStab("  "));
    }

    /**
     * Calibrates the speed-control status-slot bonus ({@link TrainerMovesetRandomizer#SPEED_CONTROL_BONUS}).
     * Watch printRoleCoverage's speed-control rate (target: meaningfully above baseline without every boss
     * looking identical — cross-check a few individual game blocks via printSummary, not just the combined total).
     * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MovesetProfile*.sweepSpeedControlBonus" -Dbm.speedcontrol=1.0,2.5,4.0,6.0 }</pre>
     */
    @Test
    public void sweepSpeedControlBonus() {
        sweep("bm.speedcontrol",
                "sweep skipped - pass -Dbm.speedcontrol=<comma-separated bonus values> to calibrate",
                v -> TrainerMovesetRandomizer.SPEED_CONTROL_BONUS = v,
                () -> TrainerMovesetRandomizer.SPEED_CONTROL_BONUS,
                "SPEED_CONTROL_BONUS=%.1f",
                combined -> combined.printRoleCoverage("  "));
    }

    /**
     * Calibrates the priority-move wildcard bonus ({@link TrainerMovesetRandomizer#PRIORITY_MOVE_BONUS}). Watch
     * printRoleCoverage's priority-answer rate. Priority moves are typically low BP, so also cross-check
     * printSummary/printBossByBand for any drop in average attack power — the bonus should add a priority OPTION,
     * not crowd out the boss's main damage output.
     * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MovesetProfile*.sweepPriorityBonus" -Dbm.prioritybonus=1.0,2.0,3.0,4.5 }</pre>
     */
    @Test
    public void sweepPriorityBonus() {
        sweep("bm.prioritybonus",
                "sweep skipped - pass -Dbm.prioritybonus=<comma-separated bonus values> to calibrate",
                v -> TrainerMovesetRandomizer.PRIORITY_MOVE_BONUS = v,
                () -> TrainerMovesetRandomizer.PRIORITY_MOVE_BONUS,
                "PRIORITY_MOVE_BONUS=%.1f",
                combined -> combined.printRoleCoverage("  "));
    }

    /**
     * Calibrates the team-level "missing role" pull ({@link TrainerMovesetRandomizer#ROLE_COVERAGE_BONUS}).
     * Compare the small(<3) vs normal split in printRoleCoverage across values: small-team rate should stay flat
     * (the pull is disabled there by design) while normal-team rate should rise with the bonus. If small-team
     * rate rises too, {@link TrainerMovesetRandomizer#ROLE_COVERAGE_MIN_TEAM_SIZE} isn't being read correctly -
     * that's a bug, not a calibration question.
     * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MovesetProfile*.sweepRoleCoverageBonus" -Dbm.rolecoverage=1.0,2.0,3.0,4.0 }</pre>
     */
    @Test
    public void sweepRoleCoverageBonus() {
        sweep("bm.rolecoverage",
                "sweep skipped - pass -Dbm.rolecoverage=<comma-separated bonus values> to calibrate",
                v -> TrainerMovesetRandomizer.ROLE_COVERAGE_BONUS = v,
                () -> TrainerMovesetRandomizer.ROLE_COVERAGE_BONUS,
                "ROLE_COVERAGE_BONUS=%.1f",
                combined -> combined.printRoleCoverage("  "));
    }

    // Re-runs the whole profile once per comma-separated value in the -D<prop> spec, temporarily setting a tuning
    // knob to each so the labelled tables can be compared without a rebuild. The original knob value is restored.
    private void sweep(String prop, String skipHint, DoubleConsumer knobSetter, DoubleSupplier knobGetter,
                       String label, Consumer<Profile> extraTable) {
        String spec = System.getProperty(prop);
        assumeTrue(spec != null && !spec.isBlank(), skipHint);
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");

        double original = knobGetter.getAsDouble();
        try {
            for (String token : spec.split(",")) {
                knobSetter.accept(Double.parseDouble(token.trim()));
                Profile combined = new Profile();
                int loaded = 0;
                for (String[] game : GAMES) {
                    RomHandler rom = tryLoad(game[0], game[1]);
                    if (rom == null) {
                        continue;
                    }
                    loaded++;
                    combined.merge(computeProfile(rom));
                }
                System.out.printf("%n### sweep " + label + " (across %d ROM(s)) ###%n",
                        knobGetter.getAsDouble(), loaded);
                combined.printSummary("  ");
                extraTable.accept(combined);
            }
        } finally {
            knobSetter.accept(original);
        }
    }

    // Runs Better Movesets over one ROM and tallies the attack/status profile by tier and boss level band.
    private Profile computeProfile(RomHandler rom) {
        rom.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());
        Settings s = new Settings();
        s.setBetterBossTrainerMovesets(true);
        s.setBetterImportantTrainerMovesets(true);
        s.setBetterRegularTrainerMovesets(true);
        new TrainerMovesetRandomizer(rom, s, new Random(SEED)).randomizeTrainerMovesets();

        List<Move> allMoves = rom.getMoves();
        Profile p = new Profile();
        for (Trainer tr : rom.getTrainers()) {
            boolean boss = tr.isBoss() || tr.isImportant();
            List<MonProfile> team = new ArrayList<>();
            boolean teamHasSpeedControl = false;
            boolean teamHasPriorityAnswer = false;
            for (TrainerPokemon tp : tr.getPokemon()) {
                Set<Integer> moves = new HashSet<>();
                for (int id : tp.getMoves()) {
                    if (id != 0) {
                        moves.add(id);
                    }
                }
                if (moves.isEmpty()) {
                    continue; // moveless / reset mon skipped the slot logic - no authored profile
                }
                Species sp = tp.getSpecies();
                Type stabType1 = sp.getPrimaryType(false);
                Type stabType2 = sp.getSecondaryType(false);
                int attacks = 0;
                int useful = 0;
                int junk = 0;
                int attackPower = 0;
                double bestStabPower = 0; // strongest same-type readable-power attack this mon carries (its STAB slot)
                Set<Type> attackTypes = new HashSet<>();
                for (int id : moves) {
                    Move mv = allMoves.get(id);
                    if (TrainerMovesetRandomizer.SPEED_CONTROL_MOVES.contains(id)) {
                        teamHasSpeedControl = true;
                    }
                    if (mv.priority > 0 && isAttack(mv, id)) {
                        teamHasPriorityAnswer = true;
                    }
                    if (isAttack(mv, id)) {
                        attacks++;
                        if (mv.power > 1) {
                            attackPower += mv.power; // proportional/fixed-damage attacks store no readable power
                            if (mv.type == stabType1 || (stabType2 != null && mv.type == stabType2)) {
                                bestStabPower = Math.max(bestStabPower, mv.power * mv.hitCount);
                            }
                        }
                        if (mv.type != null) {
                            attackTypes.add(mv.type);
                        }
                    } else if (mv.category == MoveCategory.STATUS) {
                        if (GlobalConstants.goodStatusMoves.contains(id)) {
                            useful++;
                        } else {
                            junk++;
                        }
                    }
                }
                p.add(boss, tp.getLevel(), attacks, attackTypes.size(), useful, junk);
                if (bestStabPower > 0) {
                    // Fixed 0.75-of-centerPower yardstick (the original shared floor) so the weak-STAB rate stays
                    // comparable as the tunable boss fraction/exponent are swept - it is a stable ruler, not the knob.
                    double weakThreshold = TrainerMovesetRandomizer.centerPower(tp.getLevel()) * 0.75;
                    p.addStab(boss, tp.getLevel(), bestStabPower < weakThreshold);
                }
                team.add(new MonProfile(tp.getLevel(), attacks, attackTypes.size(), attackPower));
            }
            p.addTeam(boss, team);
            p.addSpeedControlCoverage(boss, team.size(), teamHasSpeedControl);
            p.addPriorityCoverage(boss, team.size(), teamHasPriorityAnswer);
        }
        return p;
    }

    private static boolean isAttack(Move mv, int id) {
        return mv.power > 1 || SYNTHETIC_DAMAGE_MOVES.contains(id);
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

    // Aggregated attack/status tallies for one or more ROMs, split by tier and (for bosses) by level band.
    private static final class Profile {
        private final Tally boss = new Tally();
        private final Tally reg = new Tally();
        private final Tally[] bossBands = {new Tally(), new Tally(), new Tally(), new Tally()};
        // Content of the two non-attack slots on bosses that stopped at exactly 2 attacks - is the slot the
        // wildcard "wasted" filled with authored utility or filler? Split by band, plus a run total.
        private final ThinBoss thinBoss = new ThinBoss();
        private final ThinBoss[] thinBossBands = {new ThinBoss(), new ThinBoss(), new ThinBoss(), new ThinBoss()};
        private int bossUnder2; // bosses that could not even field 2 attacks (genuinely starved pools)
        private final AceCompare aceCompare = new AceCompare();
        // Weak-STAB tracking: how often a mon's strongest same-type attack falls below the level-appropriate floor.
        private final StabTally bossStab = new StabTally();
        private final StabTally regStab = new StabTally();
        private final StabTally[] bossStabBands = {new StabTally(), new StabTally(), new StabTally(), new StabTally()};
        private final RoleCoverageTally speedControlSmall = new RoleCoverageTally();
        private final RoleCoverageTally speedControlNormal = new RoleCoverageTally();
        private final RoleCoverageTally prioritySmall = new RoleCoverageTally();
        private final RoleCoverageTally priorityNormal = new RoleCoverageTally();
        private static final String[] BAND_LABELS = {"Lv1-15", "Lv16-30", "Lv31-45", "Lv46+"};

        void add(boolean isBoss, int level, int attacks, int distinctTypes, int useful, int junk) {
            Tally t = isBoss ? boss : reg;
            t.add(attacks, distinctTypes, useful, junk);
            if (isBoss) {
                bossBands[bandIndex(level)].add(attacks, distinctTypes, useful, junk);
                if (attacks == 2) {
                    thinBoss.add(useful, junk, distinctTypes);
                    thinBossBands[bandIndex(level)].add(useful, junk, distinctTypes);
                } else if (attacks < 2) {
                    bossUnder2++;
                }
            }
        }

        // Record whether a mon's strongest same-type attack (its STAB slot) sits below the level-appropriate floor.
        // A high boss rate is the "weak STAB on a high-level boss" symptom; regulars are expected to run higher.
        void addStab(boolean isBoss, int level, boolean weak) {
            (isBoss ? bossStab : regStab).add(weak);
            if (isBoss) {
                bossStabBands[bandIndex(level)].add(weak);
            }
        }

        // teamSize < ROLE_COVERAGE_MIN_TEAM_SIZE buckets separately so Task 4's small-team carve-out is directly
        // checkable (small teams should NOT show an inflated rate once #4 lands).
        void addSpeedControlCoverage(boolean isBoss, int teamSize, boolean hasSpeedControl) {
            if (!isBoss || teamSize == 0) {
                return;
            }
            (teamSize < TrainerMovesetRandomizer.ROLE_COVERAGE_MIN_TEAM_SIZE ? speedControlSmall : speedControlNormal)
                    .add(hasSpeedControl);
        }

        void addPriorityCoverage(boolean isBoss, int teamSize, boolean hasPriority) {
            if (!isBoss || teamSize == 0) {
                return;
            }
            (teamSize < TrainerMovesetRandomizer.ROLE_COVERAGE_MIN_TEAM_SIZE ? prioritySmall : priorityNormal)
                    .add(hasPriority);
        }

        // Feed a fully-profiled team into the ace-vs-teammates comparison (boss/important tier, 2+ profiled mons).
        void addTeam(boolean isBoss, List<MonProfile> team) {
            if (isBoss && team.size() >= 2) {
                aceCompare.add(team);
            }
        }

        void merge(Profile other) {
            boss.merge(other.boss);
            reg.merge(other.reg);
            for (int i = 0; i < bossBands.length; i++) {
                bossBands[i].merge(other.bossBands[i]);
            }
            thinBoss.merge(other.thinBoss);
            for (int i = 0; i < thinBossBands.length; i++) {
                thinBossBands[i].merge(other.thinBossBands[i]);
            }
            bossUnder2 += other.bossUnder2;
            aceCompare.merge(other.aceCompare);
            bossStab.merge(other.bossStab);
            regStab.merge(other.regStab);
            for (int i = 0; i < bossStabBands.length; i++) {
                bossStabBands[i].merge(other.bossStabBands[i]);
            }
            speedControlSmall.merge(other.speedControlSmall);
            speedControlNormal.merge(other.speedControlNormal);
            prioritySmall.merge(other.prioritySmall);
            priorityNormal.merge(other.priorityNormal);
        }

        void printSummary(String indent) {
            System.out.println(indent + "BOSS/IMP " + boss.summary());
            System.out.println(indent + "REGULAR  " + reg.summary());
        }

        void printBossByBand(String indent) {
            for (int i = 0; i < bossBands.length; i++) {
                if (bossBands[i].n > 0) {
                    System.out.println(indent + "boss " + BAND_LABELS[i] + " " + bossBands[i].summary());
                }
            }
        }

        // What fills the two non-attack slots of modal-2 bosses: high useful-status / low junk = the "wasted"
        // slot is already authored (accept as authentic); meaningful junk = a pool-aware fix (R2) is warranted.
        // single-atk-type% is a black-box proxy for how often the no-duplicate-attacking-type guard was relevant
        // (a mono-attacking-type boss is the case a same-type-3rd relaxation would target). Report-only.
        void printThinPoolBosses(String indent) {
            System.out.println(indent + "modal-2 boss slots " + thinBoss.summary()
                    + (bossUnder2 > 0 ? "  [+" + bossUnder2 + " boss(es) under 2 attacks]" : ""));
            for (int i = 0; i < thinBossBands.length; i++) {
                if (thinBossBands[i].n > 0) {
                    System.out.println(indent + "  " + BAND_LABELS[i] + " " + thinBossBands[i].summary());
                }
            }
        }

        // After the ace-first assignment order, the highest-level mon should carry AT LEAST the attacking
        // variety/power of its lower-level teammates (which now absorb the team-repeat demotion). ace >= rest
        // confirms the fix; ace < rest is the pre-fix bug (the ace got the dupe scraps). Report-only.
        void printAceVsTeam(String indent) {
            System.out.println(indent + "ACE vs teammates " + aceCompare.summary());
        }

        // Weak-STAB rate: share of mons whose strongest same-type attack is below the level floor (a Lv49 boss on
        // Mud Shot with Earthquake available). Boss is the calibration target; regular is the intended-loose contrast.
        void printWeakStab(String indent) {
            System.out.println(indent + "BOSS/IMP weak-STAB " + bossStab.summary());
            System.out.println(indent + "REGULAR  weak-STAB " + regStab.summary());
            for (int i = 0; i < bossStabBands.length; i++) {
                if (bossStabBands[i].n > 0) {
                    System.out.println(indent + "  boss " + BAND_LABELS[i] + " " + bossStabBands[i].summary());
                }
            }
        }

        void printRoleCoverage(String indent) {
            System.out.println(indent + "boss speed-control answer  small(<" + TrainerMovesetRandomizer.ROLE_COVERAGE_MIN_TEAM_SIZE
                    + ") " + speedControlSmall.summary() + "  normal " + speedControlNormal.summary());
            System.out.println(indent + "boss priority answer       small(<" + TrainerMovesetRandomizer.ROLE_COVERAGE_MIN_TEAM_SIZE
                    + ") " + prioritySmall.summary() + "  normal " + priorityNormal.summary());
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

    // One mon's attacking profile within a team, for the ace-vs-teammates comparison. attackPower is the summed
    // base power of its real-power attacking moves only (proportional/fixed-damage attacks add variety but no
    // readable power, so they are excluded from the power figure).
    private record MonProfile(int level, int attacks, int distinctTypes, int attackPower) {}

    // Aggregates, across boss/important teams, the highest-level mon (the ace) against the mean of its lower-level
    // teammates, to confirm the ace-first assignment order gives the ace the premium/varied picks rather than the
    // duplicate scraps. Per team the ace is the (first) max-level mon; any equal-level mons count as teammates.
    private static final class AceCompare {
        private int teams;
        private double aceTypes;
        private double restTypes;
        private double aceAttacks;
        private double restAttacks;
        private double acePower;
        private double restPower;

        void add(List<MonProfile> team) {
            MonProfile ace = team.get(0);
            for (MonProfile m : team) {
                if (m.level() > ace.level()) {
                    ace = m;
                }
            }
            int restCount = 0;
            double rTypes = 0;
            double rAttacks = 0;
            double rPower = 0;
            for (MonProfile m : team) {
                if (m == ace) {
                    continue; // reference identity - excludes exactly the ace, even if a teammate ties its stats
                }
                restCount++;
                rTypes += m.distinctTypes();
                rAttacks += m.attacks();
                rPower += m.attackPower();
            }
            if (restCount == 0) {
                return; // whole team shared the top level and the ace was the only element - nothing to compare
            }
            teams++;
            aceTypes += ace.distinctTypes();
            aceAttacks += ace.attacks();
            acePower += ace.attackPower();
            restTypes += rTypes / restCount;
            restAttacks += rAttacks / restCount;
            restPower += rPower / restCount;
        }

        void merge(AceCompare other) {
            teams += other.teams;
            aceTypes += other.aceTypes;
            restTypes += other.restTypes;
            aceAttacks += other.aceAttacks;
            restAttacks += other.restAttacks;
            acePower += other.acePower;
            restPower += other.restPower;
        }

        String summary() {
            if (teams == 0) {
                return "(no multi-mon boss teams)";
            }
            return String.format("(teams=%d): atk-types ace %.2f vs rest %.2f | attacks ace %.2f vs rest %.2f "
                            + "| atk-power ace %.0f vs rest %.0f",
                    teams, aceTypes / teams, restTypes / teams, aceAttacks / teams, restAttacks / teams,
                    acePower / teams, restPower / teams);
        }
    }

    // Weak-STAB counts for one tier/band: mons carrying >=1 readable-power same-type attack, and how many of those
    // have their strongest such attack below a fixed 0.75-of-centerPower yardstick.
    private static final class StabTally {
        private int n;
        private int weak;

        void add(boolean isWeak) {
            n++;
            if (isWeak) {
                weak++;
            }
        }

        void merge(StabTally other) {
            n += other.n;
            weak += other.weak;
        }

        String summary() {
            if (n == 0) {
                return "(n=0)";
            }
            return String.format("%d%% (n=%d)", Math.round(100.0 * weak / n), n);
        }
    }

    // Share of boss/important teams that have at least one speed-control move (any SPEED_CONTROL_MOVES member)
    // somewhere on the roster.
    private static final class RoleCoverageTally {
        private int n;
        private int withRole;

        void add(boolean hasRole) {
            n++;
            if (hasRole) {
                withRole++;
            }
        }

        void merge(RoleCoverageTally other) {
            n += other.n;
            withRole += other.withRole;
        }

        String summary() {
            if (n == 0) {
                return "(n=0)";
            }
            return String.format("%d%% (n=%d)", Math.round(100.0 * withRole / n), n);
        }
    }

    // Running totals for a single tier/band: mon count, attack/type/status sums, and the attack-count histogram.
    private static final class Tally {
        private int n;
        private int sumAttacks;
        private int sumTypes;
        private int sumUseful;
        private int sumJunk;
        private final int[] attackDist = new int[5]; // index 0-4 attacks (>4 impossible: 4 move slots)

        void add(int attacks, int distinctTypes, int useful, int junk) {
            n++;
            sumAttacks += attacks;
            sumTypes += distinctTypes;
            sumUseful += useful;
            sumJunk += junk;
            attackDist[Math.min(attacks, 4)]++;
        }

        void merge(Tally other) {
            n += other.n;
            sumAttacks += other.sumAttacks;
            sumTypes += other.sumTypes;
            sumUseful += other.sumUseful;
            sumJunk += other.sumJunk;
            for (int i = 0; i < attackDist.length; i++) {
                attackDist[i] += other.attackDist[i];
            }
        }

        String summary() {
            if (n == 0) {
                return "(n=0)";
            }
            List<String> dist = new ArrayList<>();
            for (int a = 2; a <= 4; a++) {
                dist.add(String.format("%datk %d%%", a, Math.round(100.0 * attackDist[a] / n)));
            }
            return String.format("(n=%d): avg atk %.2f, avg types %.2f, useful-status %.2f, junk %.2f | %s",
                    n, (double) sumAttacks / n, (double) sumTypes / n,
                    (double) sumUseful / n, (double) sumJunk / n, String.join(", ", dist));
        }
    }

    // Non-attack-slot content of bosses that stopped at exactly 2 attacks (2 attacks + 2 non-attack slots).
    private static final class ThinBoss {
        private int n;
        private int sumUseful;
        private int sumJunk;
        private int singleAtkType; // modal-2 bosses whose two attacks share a single attacking type

        void add(int useful, int junk, int distinctAtkTypes) {
            n++;
            sumUseful += useful;
            sumJunk += junk;
            if (distinctAtkTypes <= 1) {
                singleAtkType++;
            }
        }

        void merge(ThinBoss other) {
            n += other.n;
            sumUseful += other.sumUseful;
            sumJunk += other.sumJunk;
            singleAtkType += other.singleAtkType;
        }

        String summary() {
            if (n == 0) {
                return "(none)";
            }
            return String.format("(n=%d): useful-status %.2f, junk-status %.2f, single-atk-type %d%%",
                    n, (double) sumUseful / n, (double) sumJunk / n,
                    Math.round(100.0 * singleAtkType / n));
        }
    }
}
