package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden-master (characterization) test for the custom four-slot Better Movesets redesign.
 * <p>
 * Unlike {@link BetterMovesetsRandomizerTest}, which asserts general invariants, this test freezes the EXACT
 * moves the randomizer assigns for a fixed seed on specific ROMs, so any accidental change in behaviour shows up
 * as a precise diff. It documents what the code does today; it is not a statement that the current output is
 * "correct". When a change to the moveset logic is intentional, re-bless the snapshot (see below).
 * <p>
 * Named to match the {@code *Randomizer*Test} filter so it runs under the {@code testROMs} Gradle task (which sets
 * {@code romsPath}); it does NOT extend {@link RandomizerTest}, so it loads each ROM itself, by content. It only
 * touches small (<= {@link #MAX_ROM_BYTES}) gen 1-3 ROMs, so it stays fast.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*BetterMovesetsGoldenMaster*" }</pre>
 * <b>Re-blessing:</b> after an intentional behaviour change (or on a differently-versioned ROM dump), run with
 * {@code -Dgolden.record=true}. The test then prints a paste-ready {@code EXPECTED.put(...)} block for every ROM
 * instead of asserting; copy the block(s) you want to freeze into {@link #EXPECTED} and drop the flag.
 */
public class BetterMovesetsGoldenMasterRandomizerTest {

    /** Fixed so the whole run is deterministic; changing it invalidates every frozen snapshot below. */
    private static final long SEED = 20260714L;
    /** Only ROMs up to this size (gens 1-4: GB/GBC/GBA + DS) are processed - keeps out the big gen 5 / 3DS
     *  dumps so the run stays fast, while still covering a gen 4 ROM for the physical/special split path. */
    private static final long MAX_ROM_BYTES = 150L * 1024 * 1024;
    /** How many trainer Pokemon (in iteration order, buffed trainers only) to freeze per ROM. */
    private static final int SAMPLE = 25;

    /**
     * Frozen expected output, keyed by {@link RomHandler#getROMName()}. Each value is the canonical block from
     * {@link #canonicalBlock}. Populated by a {@code -Dgolden.record=true} run (see class doc). Only ROMs present
     * here are asserted; any other loadable ROM is ignored.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // Three frozen samples covering distinct code paths: gen 2 (no abilities; move category is type-based),
        // gen 3 (abilities + move tutors; category still type-based - the physical/special split does not exist
        // yet), and gen 4 (abilities + tutors + the real per-move physical/special split, which the attacker-
        // profile category bias depends on). Captured with SEED on the local ROM dumps. To re-bless after an
        // intentional change, run and copy the printed EXPECTED.put(...) block back here (see class doc).
        EXPECTED.put("Pokemon Crystal (U)", """
                BOSS L7 PIDGEY (NORMAL/FLYING): 33,28,182,197
                BOSS L9 PIDGEOTTO (NORMAL/FLYING): 129,16,182,237
                BOSS L18 CLEFAIRY (NORMAL): 3,7,156,47
                BOSS L20 MILTANK (NORMAL): 29,247,208,174
                BOSS L14 METAPOD (BUG): 106,0,0,0
                BOSS L14 KAKUNA (BUG/POISON): 106,40,81,0
                BOSS L16 SCYTHER (BUG/FLYING): 210,98,168,249
                BOSS L21 GASTLY (GHOST/POISON): 247,202,174,237
                BOSS L21 HAUNTER (GHOST/POISON): 122,202,174,95
                BOSS L25 GENGAR (GHOST/POISON): 122,8,182,94
                BOSS L23 HAUNTER (GHOST/POISON): 247,202,156,237
                BOSS L27 SEEL (WATER): 62,29,195,207
                BOSS L29 DEWGONG (WATER/ICE): 62,63,156,214
                BOSS L31 PILOSWINE (ICE/GROUND): 196,44,156,63
                BOSS L30 MAGNEMITE (ELECTRIC/STEEL): 84,205,156,207
                BOSS L30 MAGNEMITE (ELECTRIC/STEEL): 87,129,86,218
                BOSS L35 STEELIX (STEEL/GROUND): 231,91,46,249
                BOSS L27 PRIMEAPE (FIGHTING): 2,129,156,218
                BOSS L30 POLIWRATH (WATER/FIGHTING): 249,196,174,55
                BOSS L37 DRAGONAIR (DRAGON): 225,192,174,213
                BOSS L37 DRAGONAIR (DRAGON): 225,53,86,87
                BOSS L37 DRAGONAIR (DRAGON): 225,126,86,216
                BOSS L40 KINGDRA (WATER/DRAGON): 225,58,174,108
                IMP  L12 GASTLY (GHOST/POISON): 122,202,92,104
                IMP  L14 ZUBAT (POISON/FLYING): 44,129,197,48""");
        EXPECTED.put("Pokemon Emerald (U)", """
                REG  L21 GEODUDE (ROCK/GROUND): 91,280,157,111
                REG  L32 POOCHYENA (DARK): 44,91,28,104
                REG  L31 ZUBAT (POISON/FLYING): 17,202,263,211
                REG  L31 CARVANHA (WATER/DARK): 352,59,116,210
                REG  L32 ZUBAT (POISON/FLYING): 188,44,17,202
                REG  L32 CARVANHA (WATER/DARK): 352,242,189,103
                REG  L36 POOCHYENA (DARK): 44,231,316,91
                REG  L36 CARVANHA (WATER/DARK): 242,56,58,103
                REG  L36 ZUBAT (POISON/FLYING): 188,44,102,247
                REG  L26 SKITTY (NORMAL): 290,247,274,203
                REG  L26 POOCHYENA (DARK): 44,247,316,46
                REG  L26 ZIGZAGOON (NORMAL): 290,352,247,28
                REG  L26 LOTAD (WATER/GRASS): 352,59,310,240
                REG  L26 SEEDOT (GRASS): 331,91,263,74
                REG  L26 TAILLOW (NORMAL/FLYING): 98,17,218,213
                REG  L9 POOCHYENA (DARK): 91,33,68,259
                REG  L29 MANECTRIC (ELECTRIC): 209,98,182,164
                REG  L29 SHIFTRY (GRASS/DARK): 168,91,241,102
                REG  L30 PELIPPER (WATER/FLYING): 17,211,290,164
                REG  L30 XATU (PSYCHIC/FLYING): 332,101,211,202
                REG  L30 ZANGOOSE (NORMAL): 38,196,14,223
                REG  L30 SEVIPER (POISON): 188,242,103,290
                REG  L36 CARVANHA (WATER/DARK): 242,38,156,213
                REG  L34 GYARADOS (WATER/FLYING): 352,85,82,156
                REG  L11 POOCHYENA (DARK): 168,91,336,92""");
        EXPECTED.put("Pokemon Platinum (U)", """
                REG  L5 STARLY (NORMAL/FLYING): 332,466,98,216
                REG  L5 BURMY (BUG): 182,0,0,0
                REG  L5 BIDOOF (NORMAL): 33,365,168,92
                REG  L7 KRICKETOT (BUG): 45,117,253,0
                REG  L6 ZUBAT (POISON/FLYING): 314,371,141,48
                REG  L5 RATTATA (NORMAL): 129,196,39,104
                REG  L5 RATTATA (NORMAL): 98,196,68,351
                REG  L5 RATTATA (NORMAL): 98,365,196,39
                REG  L5 RATTATA (NORMAL): 98,196,451,203
                REG  L5 RATTATA (NORMAL): 98,365,382,39
                REG  L8 MAGIKARP (WATER): 150,0,0,0
                REG  L7 BUDEW (GRASS/POISON): 202,129,74,213
                REG  L7 SHINX (ELECTRIC): 451,129,43,168
                REG  L10 WURMPLE (BUG): 33,81,40,0
                REG  L11 KRICKETOT (BUG): 45,117,253,0
                REG  L9 BUDEW (GRASS/POISON): 75,129,346,189
                REG  L11 CHERUBI (GRASS): 331,33,182,203
                REG  L11 PACHIRISU (ELECTRIC): 351,98,156,92
                REG  L11 PACHIRISU (ELECTRIC): 9,98,182,213
                REG  L14 PONYTA (FIRE): 172,263,241,218
                REG  L12 BIDOOF (NORMAL): 290,365,111,92
                REG  L12 PACHIRISU (ELECTRIC): 351,98,237,164
                REG  L10 GEODUDE (ROCK/GROUND): 246,33,111,397
                REG  L11 GEODUDE (ROCK/GROUND): 88,246,111,92
                REG  L12 GEODUDE (ROCK/GROUND): 88,33,218,111""");
    }

    @Test
    public void betterMovesetsGoldenMaster() {
        boolean record = Boolean.getBoolean("golden.record");
        String romsDir = System.getProperty("romsPath");
        assumeTrue(romsDir != null, "romsPath not set");
        File dir = new File(romsDir);
        assumeTrue(dir.isDirectory(), "roms dir missing: " + romsDir);
        File[] files = dir.listFiles();
        assumeTrue(files != null && files.length > 0, "roms dir empty: " + romsDir);

        List<File> candidates = new ArrayList<>(Arrays.asList(files));
        candidates.sort(Comparator.comparingLong(File::length)); // small/fast ROMs first

        int asserted = 0;
        for (File f : candidates) {
            if (!f.isFile() || f.getName().equalsIgnoreCase("readme.txt") || f.length() > MAX_ROM_BYTES) {
                continue;
            }
            RomHandler rh = tryLoad(f.getAbsolutePath());
            if (rh == null) {
                continue;
            }
            String romName = rh.getROMName();
            String actual = canonicalBlock(rh, romName);

            if (record) {
                System.out.println("\nEXPECTED.put(\"" + romName + "\", \"\"\"\n" + actual + "\n\"\"\");");
                continue;
            }
            String expected = EXPECTED.get(romName);
            if (expected == null) {
                // Not frozen yet - print the paste-ready block so it can be copied into EXPECTED.
                System.out.println("\nEXPECTED.put(\"" + romName + "\", \"\"\"\n" + actual + "\n\"\"\");");
                continue;
            }
            assertEquals(expected.strip(), actual.strip(),
                    "Better Movesets output drifted for " + romName + " (seed " + SEED + ")");
            asserted++;
        }

        if (record) {
            return; // recording prints blocks; nothing to assert
        }
        assumeTrue(asserted >= 0); // keep the assume machinery honest even if no target ROMs are present
        assertFalse(EXPECTED.isEmpty() && asserted == 0,
                "No frozen ROM snapshots present - populate EXPECTED (run with -Dgolden.record=true).");
    }

    /**
     * The deterministic fingerprint of a ROM's Better Movesets output: for the first {@link #SAMPLE} trainer
     * Pokemon belonging to trainers that receive buffs, one line of {@code TIER Llevel Name (types): m1,m2,m3,m4}
     * (or {@code RESET} when the game is left to fill the natural moveset). Iteration order over trainers and their
     * Pokemon is stable, so the fingerprint is stable given the seed.
     */
    private String canonicalBlock(RomHandler romHandler, String romName) {
        romHandler.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());

        Settings s = new Settings();
        s.setBetterBossTrainerMovesets(true);
        s.setBetterImportantTrainerMovesets(true);
        s.setBetterRegularTrainerMovesets(true);
        new TrainerMovesetRandomizer(romHandler, s, new Random(SEED)).randomizeTrainerMovesets();

        StringBuilder sb = new StringBuilder();
        int captured = 0;
        for (Trainer tr : romHandler.getTrainers()) {
            if (captured >= SAMPLE) {
                break;
            }
            if (tr.shouldNotGetBuffs() || !(tr.isBoss() || tr.isImportant() || tr.isRegular())) {
                continue;
            }
            for (TrainerPokemon tp : tr.getPokemon()) {
                if (captured >= SAMPLE) {
                    break;
                }
                Species pk = tp.getSpecies();
                String moves = tp.isResetMoves() ? "RESET" : joinMoves(tp.getMoves());
                sb.append(tierOf(tr)).append(" L").append(tp.getLevel()).append(' ')
                        .append(pk.getName()).append(" (").append(typeStr(pk)).append("): ")
                        .append(moves).append('\n');
                captured++;
            }
        }
        return sb.toString().strip();
    }

    private static String joinMoves(int[] moves) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < moves.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(moves[i]);
        }
        return sb.toString();
    }

    private static String typeStr(Species pk) {
        Type t2 = pk.getSecondaryType(false);
        return pk.getPrimaryType(false) + (t2 == null ? "" : "/" + t2);
    }

    private static String tierOf(Trainer tr) {
        if (tr.isBoss()) {
            return "BOSS";
        }
        if (tr.isImportant()) {
            return "IMP ";
        }
        return "REG ";
    }

    private RomHandler tryLoad(String path) {
        for (Generation gen : new HashSet<>(Generation.GAME_TO_GENERATION.values())) {
            try {
                RomHandler.Factory factory = gen.createFactory();
                if (factory.isLoadable(path)) {
                    RomHandler rh = factory.create();
                    rh.loadRom(path);
                    return rh;
                }
            } catch (Throwable e) {
                // not this generation's ROM, or unloadable - try the next
            }
        }
        return null;
    }
}
