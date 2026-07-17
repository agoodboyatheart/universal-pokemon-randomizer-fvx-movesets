package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.constants.MoveIDs;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
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
            {"Red", "Pokemon Red"},
            {"Crystal", "Pokemon Crystal"},
            {"Emerald", "Pokemon Emerald"},
            {"Platinum", "Pokemon Platinum"},
            {"Black 2", "Pokemon Black 2"},
            {"Alpha Sapphire", "Pokemon Alpha Sapphire (Europe) (En,Ja,Fr,De,Es,It,Ko) (Rev 2)-decrypted"},
            {"Ultra Sun", "Pokemon Ultra Sun-decrypted"},
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
        }
        assumeTrue(loaded > 0, "No loadable ROM found in " + ROMS_PATH);
    }

    @Test
    public void sweepBossWildcardBonus() {
        String spec = System.getProperty("bm.sweep");
        assumeTrue(spec != null && !spec.isBlank(),
                "sweep skipped - pass -Dbm.sweep=<comma-separated bonus values> to calibrate");
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");

        double original = TrainerMovesetRandomizer.bossWildcardDamagingBonus;
        try {
            for (String token : spec.split(",")) {
                double bonus = Double.parseDouble(token.trim());
                TrainerMovesetRandomizer.bossWildcardDamagingBonus = bonus;
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
                System.out.printf("%n### sweep bossWildcardDamagingBonus=%.1f (across %d ROM(s)) ###%n",
                        bonus, loaded);
                combined.printSummary("  ");
                combined.printBossByBand("  ");
            }
        } finally {
            TrainerMovesetRandomizer.bossWildcardDamagingBonus = original;
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
                int attacks = 0;
                int useful = 0;
                int junk = 0;
                Set<Type> attackTypes = new HashSet<>();
                for (int id : moves) {
                    Move mv = allMoves.get(id);
                    if (isAttack(mv, id)) {
                        attacks++;
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
            }
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
        private static final String[] BAND_LABELS = {"Lv1-15", "Lv16-30", "Lv31-45", "Lv46+"};

        void add(boolean isBoss, int level, int attacks, int distinctTypes, int useful, int junk) {
            Tally t = isBoss ? boss : reg;
            t.add(attacks, distinctTypes, useful, junk);
            if (isBoss) {
                bossBands[bandIndex(level)].add(attacks, distinctTypes, useful, junk);
            }
        }

        void merge(Profile other) {
            boss.merge(other.boss);
            reg.merge(other.reg);
            for (int i = 0; i < bossBands.length; i++) {
                bossBands[i].merge(other.bossBands[i]);
            }
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
}
