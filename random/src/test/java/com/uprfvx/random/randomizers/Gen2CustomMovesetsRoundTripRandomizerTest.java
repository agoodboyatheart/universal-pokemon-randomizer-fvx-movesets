package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.romhandlers.Gen2RomHandler;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TEMP verification test (not part of the permanent suite): full save+reload round trip of Gen 2
 * Better Movesets on boss/important trainers, to confirm canGiveCustomMovesetsToBossTrainers()/
 * Important() actually persist to the ROM correctly, not just in-memory. Regular trainers must be
 * byte-identical to their PRE-RANDOMIZATION state (vanilla GSC already hand-authors custom movesets
 * on ~90-125 regular trainers, so "has custom moves" alone isn't a valid regular-trainer invariant -
 * the real invariant is "unchanged by our randomizer", checked against a baseline snapshot).
 */
public class Gen2CustomMovesetsRoundTripRandomizerTest {

    private static final String ROMS_PATH = System.getProperty("romsPath");
    private static final long SEED = 987654321L;

    @ParameterizedTest
    @ValueSource(strings = {"Gold (U).gbc", "Silver (U).gbc", "Crystal (U).gbc"})
    public void bossImportantGetCustomMovesRegularStaysVanilla(String fileName) throws Exception {
        String src = ROMS_PATH + "/" + fileName;
        assumeTrue(new java.io.File(src).exists(), "ROM not present: " + src);

        RomHandler rh = new Gen2RomHandler.Factory().create();
        rh.loadRom(src);

        assertTrue(rh.canGiveCustomMovesetsToBossTrainers(), fileName + ": expected boss custom movesets supported");
        assertTrue(rh.canGiveCustomMovesetsToImportantTrainers(), fileName + ": expected important custom movesets supported");
        assertFalse(rh.canGiveCustomMovesetsToRegularTrainers(), fileName + ": expected regular custom movesets NOT supported");

        // Baseline snapshot of every regular trainer's moves BEFORE Better Movesets touches anything,
        // keyed by list index (stable) rather than ROM offset (shifts once boss/important trainers
        // grow by 4 bytes/mon during save).
        List<Trainer> baselineTrainers = rh.getTrainers();
        Map<Integer, int[][]> regularBaseline = new LinkedHashMap<>();
        for (int i = 0; i < baselineTrainers.size(); i++) {
            Trainer t = baselineTrainers.get(i);
            if (t.isRegular()) {
                regularBaseline.put(i, snapshotMoves(t));
            }
        }

        rh.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());
        Settings s = new Settings();
        s.setBetterBossTrainerMovesets(true);
        s.setBetterImportantTrainerMovesets(true);
        s.setBetterRegularTrainerMovesets(false);
        new TrainerMovesetRandomizer(rh, s, new Random(SEED)).randomizeTrainerMovesets();

        long bossImpCustomBefore = rh.getTrainers().stream()
                .filter(t -> !t.shouldNotGetBuffs() && (t.isBoss() || t.isImportant()))
                .filter(Trainer::pokemonHaveCustomMoves).count();
        assertTrue(bossImpCustomBefore > 0, fileName + ": expected some boss/important trainers to get custom moves in memory");

        String out = "build/test-results/gen2_roundtrip_" + fileName;
        boolean ok = rh.saveRom(out, SEED, false);
        assertTrue(ok, fileName + ": saveRom failed");

        RomHandler reloaded = new Gen2RomHandler.Factory().create();
        reloaded.loadRom(out);

        List<Trainer> before = rh.getTrainers();
        List<Trainer> after = reloaded.getTrainers();
        assertEquals(before.size(), after.size(), fileName + ": trainer count mismatch after reload");

        int checked = 0;
        for (int i = 0; i < before.size(); i++) {
            Trainer b = before.get(i);
            Trainer a = after.get(i);
            if (b.shouldNotGetBuffs() || !(b.isBoss() || b.isImportant()) || !b.pokemonHaveCustomMoves()) {
                continue;
            }
            List<TrainerPokemon> bp = b.getPokemon();
            List<TrainerPokemon> ap = a.getPokemon();
            assertEquals(bp.size(), ap.size(), fileName + ": pokemon count mismatch for " + b.getFullDisplayName());
            for (int j = 0; j < bp.size(); j++) {
                assertArrayEquals(bp.get(j).getMoves(), ap.get(j).getMoves(),
                        fileName + ": moves mismatch for " + b.getFullDisplayName() + " mon " + j);
            }
            checked++;
        }
        assertTrue(checked > 0, fileName + ": no boss/important custom-moveset trainers were actually verified round-trip");
        System.out.println(fileName + ": verified " + checked + " boss/important trainers round-tripped custom moves correctly");

        int regularChecked = 0;
        for (int i = 0; i < after.size(); i++) {
            Trainer a = after.get(i);
            if (!a.isRegular()) continue;
            int[][] baseline = regularBaseline.get(i);
            assertNotNull(baseline, fileName + ": no baseline captured for regular trainer " + a.getFullDisplayName());
            int[][] actual = snapshotMoves(a);
            assertArrayEquals(baseline, actual, fileName + ": regular trainer moves CHANGED by Better Movesets: " + a.getFullDisplayName());
            regularChecked++;
        }
        System.out.println(fileName + ": confirmed " + regularChecked + " regular trainers byte-identical to pre-randomization baseline");
    }

    private static int[][] snapshotMoves(Trainer t) {
        List<TrainerPokemon> pokemon = t.getPokemon();
        int[][] snapshot = new int[pokemon.size()][];
        for (int i = 0; i < pokemon.size(); i++) {
            snapshot[i] = pokemon.get(i).getMoves().clone();
        }
        return snapshot;
    }
}
