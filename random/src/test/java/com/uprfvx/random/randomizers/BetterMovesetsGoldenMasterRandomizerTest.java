package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden-master (characterization) test for the custom four-slot Better Movesets redesign.
 * <p>
 * Unlike {@link BetterMovesetsRandomizerTest}, which asserts general invariants, this test freezes the EXACT
 * moves the randomizer assigns for a fixed seed on specific ROMs, so any accidental change in behaviour shows up
 * as a precise diff. It documents what the code does today; it is not a statement that the current output is
 * "correct". When a change to the moveset logic is intentional, re-bless the snapshot (see below).
 * <p>
 * It covers <b>one game per generation</b> (Red, Crystal, Emerald, Platinum, Black 2, Alpha Sapphire, Ultra Sun)
 * and, within each game, freezes an <b>even spread of {@link #SAMPLE_PER_TIER} trainer Pokemon from each trainer
 * class</b> (Boss, Important, Regular) so the tier-specific slot logic (coverage optimisation, status slot, the
 * {@code isBossTier} branch) and the generation-specific paths (gen 1's no phys/special split, the 3DS games'
 * large movepools) are all exercised. Spreading across each tier - rather than taking the first N - means both
 * low-level early-game and high-level endgame mons are captured, which matters because the STAB power ceiling
 * scales with level.
 * <p>
 * Named to match the {@code *Randomizer*Test} filter so it runs under the {@code testROMs} Gradle task (which sets
 * {@code romsPath}); it does NOT extend {@link RandomizerTest}, so it loads each ROM itself, by explicit file name
 * (like {@link TMTutorMoveRandomizerTest}). A ROM that is not present in {@code roms/} simply skips its case.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*BetterMovesetsGoldenMaster*" }</pre>
 * <b>Runtime:</b> loading Black 2 and the two multi-GB 3DS dumps (Alpha Sapphire, Ultra Sun) makes a full run take
 * a few minutes - this is an on-demand characterization test, not the inner dev loop.
 * <p>
 * <b>Re-blessing:</b> after an intentional behaviour change (or on a differently-versioned ROM dump), run with
 * {@code -Dgolden.record=true}. Each case then prints a paste-ready {@code EXPECTED.put(...)} block instead of
 * asserting; copy the block(s) you want to freeze into {@link #EXPECTED} and drop the flag. (Extract the blocks
 * from {@code random/build/test-results/testROMs/TEST-*.xml} rather than the console, which can mangle glyphs
 * like the Nidoran male name.)
 */
public class BetterMovesetsGoldenMasterRandomizerTest {

    /** Fixed so the whole run is deterministic; changing it invalidates every frozen snapshot below. */
    private static final long SEED = 20260714L;
    /** How many trainer Pokemon to freeze per trainer class (Boss/Important/Regular), evenly spread across it. */
    private static final int SAMPLE_PER_TIER = 15;

    private static final String ROMS_PATH = System.getProperty("romsPath");

    /**
     * The games to freeze: one per generation. Each row is {@code {gameName, fileBaseName}}, where {@code gameName}
     * is a {@link Generation#GAME_TO_GENERATION} key and {@code fileBaseName} is the ROM file's base name in the
     * {@code roms/} folder (extension added from the generation's suffix). The two 3DS dumps do NOT follow the
     * {@code "Pokemon <Name>"} convention, so their literal decrypted base names are used.
     */
    static String[][] gamesToVerify() {
        return new String[][]{
                {"Red", "Pokemon Red"},
                {"Crystal", "Pokemon Crystal"},
                {"Emerald", "Pokemon Emerald"},
                {"Platinum", "Pokemon Platinum"},
                {"Black 2", "Pokemon Black 2"},
                {"Alpha Sapphire", "Pokemon Alpha Sapphire (Europe) (En,Ja,Fr,De,Es,It,Ko) (Rev 2)-decrypted"},
                {"Ultra Sun", "Pokemon Ultra Sun-decrypted"},
        };
    }

    /**
     * Frozen expected output, keyed by {@code gameName} (the {@link #gamesToVerify} first column). Each value is the
     * canonical block from {@link #canonicalBlock}. Populated by a {@code -Dgolden.record=true} run (see class doc).
     * A game with no frozen block here skips (after printing its paste-ready block) rather than failing.
     */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        // Frozen with SEED on the local ROM dumps; re-bless with -Dgolden.record=true (see class doc).
        EXPECTED.put("Red", """
        BOSS L45 RHYHORN (GROUND/ROCK): 89,23,92,87
        BOSS L45 NIDOKING (POISON/GROUND): 89,126,115,58
        BOSS L55 HITMONLEE (FIGHTING): 136,130,92,96
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,164,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,129,164,149
        BOSS L24 RAICHU (ELECTRIC): 84,66,164,45
        BOSS L37 KOFFING (POISON): 124,85,164,108
        BOSS L43 WEEZING (POISON): 124,85,156,164
        BOSS L42 RAPIDASH (FIRE): 126,23,115,45
        BOSS L38 VENOMOTH (BUG/POISON): 60,63,79,18
        BOSS L53 CLOYSTER (WATER/ICE): 62,36,115,102
        BOSS L56 LAPRAS (WATER/ICE): 56,85,156,104
        BOSS L55 HAUNTER (GHOST/POISON): 101,94,92,164
        BOSS L56 DRAGONAIR (DRAGON): 87,126,164,102
        BOSS L62 DRAGONITE (DRAGON/FLYING): 36,61,115,32
        IMP  L9 PIDGEY (NORMAL/FLYING): 99,16,115,104
        IMP  L15 ABRA (PSYCHIC): 66,161,156,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 99,16,18,115
        IMP  L18 KADABRA (PSYCHIC): 93,99,115,66
        IMP  L16 RATICATE (NORMAL): 98,55,92,164
        IMP  L25 WARTORTLE (WATER): 61,66,164,39
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,92,104
        IMP  L35 ALAKAZAM (PSYCHIC): 60,91,105,25
        IMP  L40 VENUSAUR (GRASS/POISON): 76,36,115,14
        IMP  L45 RHYHORN (GROUND/ROCK): 89,87,156,39
        IMP  L45 GYARADOS (WATER/FLYING): 56,58,92,85
        IMP  L47 GYARADOS (WATER/FLYING): 56,59,92,82
        IMP  L61 ARCANINE (FIRE): 53,36,46,104
        IMP  L63 ARCANINE (FIRE): 126,91,164,82
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,69,14,90
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,28,164
        REG  L18 MANKEY (FIGHTING): 66,5,157,156
        REG  L19 RATTATA (NORMAL): 99,55,102,164
        REG  L37 VULPIX (FIRE): 53,36,156,39
        REG  L29 WEEZING (POISON): 123,33,104,156
        REG  L31 CLOYSTER (WATER/ICE): 59,120,48,156
        REG  L26 MANKEY (FIGHTING): 66,157,2,118
        REG  L30 HORSEA (WATER): 61,59,102,104
        REG  L29 FEAROW (NORMAL/FLYING): 99,65,45,43
        REG  L70 GYARADOS (WATER/FLYING): 61,126,43,102
        REG  L17 MACHOP (FIGHTING): 69,157,118,92
        REG  L28 EKANS (POISON): 40,72,137,43
        REG  L39 DUGTRIO (GROUND): 91,163,28,92
        REG  L33 HAUNTER (GHOST/POISON): 101,149,156,153
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,214
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,179,168
        BOSS L31 PILOSWINE (ICE/GROUND): 89,157,174,216
        BOSS L37 DRAGONAIR (DRAGON): 53,29,97,86
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 202,188,73,244
        BOSS L46 MACHAMP (FIGHTING): 238,89,227,126
        BOSS L40 ARIADOS (BUG/POISON): 188,101,226,92
        BOSS L47 DRAGONITE (DRAGON/FLYING): 29,85,86,114
        BOSS L42 OMASTAR (ROCK/WATER): 61,59,114,110
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,61,105,113
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,29,73,182
        BOSS L33 ARIADOS (BUG/POISON): 188,101,184,202
        BOSS L45 MAGMAR (FIRE): 7,238,174,103
        BOSS L77 BLASTOISE (WATER): 56,58,114,229
        BOSS L58 ARCANINE (FIRE): 53,242,92,245
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,149
        IMP  L12 GASTLY (GHOST/POISON): 122,202,95,168
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,213
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,197,141
        IMP  L32 MEGANIUM (GRASS): 202,89,14,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,205,86,33
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,92,182
        IMP  L35 HAUNTER (GHOST/POISON): 101,195,114,94
        IMP  L35 HAUNTER (GHOST/POISON): 101,138,95,87
        IMP  L43 GENGAR (GHOST/POISON): 101,8,104,202
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,105,218
        IMP  L43 ALAKAZAM (PSYCHIC): 60,8,50,112
        IMP  L46 ALAKAZAM (PSYCHIC): 60,7,227,134
        IMP  L50 TYPHLOSION (FIRE): 7,89,182,179
        IMP  L50 FERALIGATR (WATER): 56,59,46,242
        REG  L10 CHIKORITA (GRASS): 202,246,14,45
        REG  L20 QUAGSIRE (WATER/GROUND): 189,249,240,92
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,211,28,207
        REG  L25 NINETALES (FIRE): 52,185,98,91
        REG  L31 RHYDON (GROUND/ROCK): 157,223,7,242
        REG  L18 GROWLITHE (FIRE): 83,44,91,225
        REG  L23 GOLDEEN (WATER): 64,92,60,30
        REG  L28 TENTACOOL (WATER/POISON): 51,202,219,92
        REG  L28 POLIWHIRL (WATER): 55,249,111,54
        REG  L32 ONIX (ROCK/GROUND): 157,89,213,231
        REG  L6 VOLTORB (ELECTRIC): 173,205,203,156
        REG  L31 FURRET (NORMAL): 38,223,237,111
        REG  L42 GOLDUCK (WATER): 231,58,156,237
        REG  L23 PIKACHU (ELECTRIC): 9,205,21,217
        REG  L25 ELECTRODE (ELECTRIC): 129,205,237,92
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,281,336
        BOSS L53 WALREIN (ICE/WATER): 58,231,281,254
        BOSS L26 CAMERUPT (FIRE/GROUND): 52,33,46,111
        BOSS L43 SEALEO (ICE/WATER): 59,157,174,290
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,38,164,222
        BOSS L50 KABUTOPS (ROCK/WATER): 61,62,156,104
        BOSS L46 HITMONCHAN (FIGHTING): 183,157,92,339
        BOSS L50 MANECTRIC (ELECTRIC): 85,290,86,182
        BOSS L46 GROWLITHE (FIRE): 257,36,182,156
        BOSS L45 KANGASKHAN (NORMAL): 290,89,50,231
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,349,244
        BOSS L58 SKARMORY (STEEL/FLYING): 143,38,156,102
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 157,58,115,216
        BOSS L56 LAPRAS (WATER/ICE): 59,156,56,263
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,8,115,334
        IMP  L34 MIGHTYENA (DARK): 263,247,281,316
        IMP  L40 GOLBAT (POISON/FLYING): 188,290,18,228
        IMP  L20 GROVYLE (GRASS): 331,280,14,102
        IMP  L29 LOMBRE (WATER/GRASS): 75,310,235,45
        IMP  L18 SLUGMA (FIRE): 52,189,182,241
        IMP  L29 PELIPPER (WATER/FLYING): 55,196,182,189
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,157,174,36
        IMP  L22 ZUBAT (POISON/FLYING): 16,182,247,211
        IMP  L47 ROSELIA (GRASS/POISON): 202,290,156,275
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,92,290
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,161,92,103
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 315,69,179,98
        IMP  L34 GROVYLE (GRASS): 348,228,73,69
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,223,174,156
        IMP  L15 MUDKIP (WATER): 55,33,182,301
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 55,91,129,39
        REG  L26 MIGHTYENA (DARK): 168,310,213,33
        REG  L33 MACHOP (FIGHTING): 223,157,34,227
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,247,219,315
        REG  L35 PLUSLE (ELECTRIC): 209,69,207,113
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,182,319,205
        REG  L30 KOFFING (POISON): 124,247,60,108
        REG  L6 SEEDOT (GRASS): 331,205,206,106
        REG  L11 MARILL (WATER): 145,129,205,207
        REG  L26 LOMBRE (WATER/GRASS): 75,9,230,235
        REG  L29 XATU (PSYCHIC/FLYING): 64,185,43,297
        REG  L29 ZUBAT (POISON/FLYING): 16,202,174,129
        REG  L34 PELIPPER (WATER/FLYING): 263,59,216,92
        REG  L5 KYOGRE (WATER): 352,196,86,189
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 205,33,397,213
        BOSS L49 SCIZOR (BUG/STEEL): 442,280,14,458
        BOSS L52 HIPPOWDON (GROUND): 89,242,281,303
        BOSS L57 MAGMORTAR (FIRE): 126,89,164,9
        BOSS L58 SPIRITOMB (GHOST/DARK): 247,263,220,399
        BOSS L20 CHERRIM (GRASS): 345,92,230,263
        BOSS L29 MACHOKE (FIGHTING): 2,91,317,371
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,200,14,320
        BOSS L44 SNEASEL (DARK/ICE): 228,91,14,182
        BOSS L48 WEAVILE (DARK/ICE): 400,421,156,43
        BOSS L66 WHISCASH (WATER/GROUND): 89,416,156,445
        BOSS L69 RAPIDASH (FIRE): 315,38,45,224
        BOSS L72 ALAKAZAM (PSYCHIC): 60,411,105,272
        BOSS L78 GARCHOMP (DRAGON/GROUND): 407,89,14,444
        BOSS L58 MAGMORTAR (FIRE): 436,411,156,270
        IMP  L7 STARLY (NORMAL/FLYING): 98,310,355,332
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 89,446,397,430
        IMP  L27 GROTLE (GRASS): 402,328,115,164
        IMP  L34 STARAVIA (NORMAL/FLYING): 38,211,257,369
        IMP  L36 STARAPTOR (NORMAL/FLYING): 416,370,97,257
        IMP  L48 HERACROSS (BUG/FIGHTING): 410,38,164,216
        IMP  L47 RAPIDASH (FIRE): 394,231,203,38
        IMP  L42 STARAPTOR (NORMAL/FLYING): 98,228,18,97
        IMP  L25 KADABRA (PSYCHIC): 60,351,92,269
        IMP  L27 GROTLE (GRASS): 75,33,182,328
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,91,36,280
        IMP  L69 RAPIDASH (FIRE): 53,340,164,76
        IMP  L73 SNORLAX (NORMAL): 387,7,103,280
        IMP  L83 SNORLAX (NORMAL): 34,58,174,182
        IMP  L60 SKUNTANK (POISON/DARK): 242,38,262,386
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,44,129,310
        REG  L36 SWINUB (ICE/GROUND): 333,276,115,446
        REG  L6 GEODUDE (ROCK/GROUND): 205,189,175,201
        REG  L21 CARNIVINE (GRASS): 345,20,235,380
        REG  L21 DRIFLOON (GHOST/FLYING): 310,132,451,107
        REG  L36 MURKROW (DARK/FLYING): 143,257,101,18
        REG  L39 MURKROW (DARK/FLYING): 399,65,114,373
        REG  L58 PELIPPER (WATER/FLYING): 403,402,416,355
        REG  L32 EEVEE (NORMAL): 98,231,445,91
        REG  L48 SEAKING (WATER): 127,263,58,237
        REG  L42 GOLBAT (POISON/FLYING): 403,257,289,363
        REG  L23 BUIZEL (WATER): 55,8,91,154
        REG  L42 MAGNETON (ELECTRIC/STEEL): 209,161,199,334
        REG  L56 EMPOLEON (WATER/STEEL): 56,58,240,374
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,218
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,18,263
        BOSS L72 Lucario (FIGHTING/STEEL): 418,410,214,156
        BOSS L28 Flaaffy (ELECTRIC): 84,324,86,260
        BOSS L48 Haxorus (DRAGON): 530,91,349,179
        BOSS L50 Cofagrigus (GHOST): 247,94,477,412
        BOSS L67 Simipour (WATER): 401,59,270,276
        BOSS L76 Clefable (NORMAL): 514,126,236,516
        BOSS L28 Emolga (ELECTRIC/FLYING): 84,263,366,237
        BOSS L49 Carracosta (WATER/ROCK): 401,242,504,67
        BOSS L56 Lucario (FIGHTING/STEEL): 418,409,164,198
        BOSS L73 Golurk (GROUND/GHOST): 89,7,397,5
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 409,89,269,381
        BOSS L75 Arcanine (FIRE): 481,37,46,213
        BOSS L75 Glaceon (ICE): 58,485,215,376
        IMP  L8 Tepig (FIRE): 488,343,92,222
        IMP  L48 Cryogonal (ICE): 58,430,113,156
        IMP  L23 Pansage (GRASS): 22,44,73,421
        IMP  L31 Tranquill (NORMAL/FLYING): 403,211,355,381
        IMP  L39 Unfezant (NORMAL/FLYING): 263,257,182,526
        IMP  L46 Cryogonal (ICE): 62,430,151,163
        IMP  L55 Unfezant (NORMAL/FLYING): 98,143,234,237
        IMP  L55 Simisear (FIRE): 53,411,104,441
        IMP  L62 Unfezant (NORMAL/FLYING): 98,211,366,43
        IMP  L62 Flygon (GROUND/DRAGON): 337,276,468,9
        IMP  L65 Unfezant (NORMAL/FLYING): 403,211,269,45
        IMP  L65 Eelektross (ELECTRIC): 209,202,489,162
        IMP  L41 Simisear (FIRE): 126,157,417,67
        IMP  L48 Unfezant (NORMAL/FLYING): 13,369,234,104
        IMP  L74 Klinklang (STEEL): 544,435,508,278
        REG  L26 Blitzle (ELECTRIC): 351,263,24,156
        REG  L63 Hitmonlee (FIGHTING): 370,299,526,418
        REG  L63 Hitmonchan (FIGHTING): 136,157,193,89
        REG  L56 Unfezant (NORMAL/FLYING): 253,143,45,273
        REG  L47 Boldore (ROCK): 444,36,218,213
        REG  L45 Swinub (ICE/GROUND): 333,89,156,113
        REG  L32 Scolipede (BUG/POISON): 188,263,237,91
        REG  L65 Hitmontop (FIGHTING): 370,418,213,360
        REG  L52 Amoonguss (GRASS/POISON): 499,412,147,447
        REG  L64 Archeops (ROCK/FLYING): 444,525,340,397
        REG  L54 Metang (STEEL/PSYCHIC): 418,280,397,97
        REG  L60 Wooper (WATER/GROUND): 401,8,105,219
        REG  L67 Emboar (FIRE/FIGHTING): 394,89,111,457
        REG  L47 Krookodile (GROUND/DARK): 242,337,28,468
        REG  L25 Litwick (GHOST/FIRE): 481,496,151,499
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,583,46,43
        BOSS L41 Weezing (POISON): 499,458,85,60
        BOSS L5 Zigzagoon (NORMAL): 352,493,496,351
        BOSS L51 Dusclops (GHOST): 425,8,261,280
        BOSS L52 Froslass (ICE/GHOST): 419,358,191,104
        BOSS L57 Claydol (GROUND/PSYCHIC): 60,247,92,218
        BOSS L14 Machop (FIGHTING): 27,479,92,484
        BOSS L28 Slaking (NORMAL): 498,400,303,359
        BOSS L44 Whiscash (WATER/GROUND): 503,36,349,92
        BOSS L70 Sharpedo (WATER/DARK): 503,38,399,340
        BOSS L71 Dusknoir (GHOST): 425,8,114,373
        BOSS L73 Altaria (DRAGON/FLYING): 406,605,54,228
        BOSS L77 Carbink (ROCK/FAIRY): 408,585,397,201
        BOSS L57 Cradily (ROCK/GRASS): 444,362,14,203
        BOSS L57 Milotic (WATER): 56,406,114,240
        IMP  L18 Slugma (FIRE): 52,261,334,88
        IMP  L31 Wailmer (WATER): 362,340,38,58
        IMP  L18 Wailmer (WATER): 55,499,164,240
        IMP  L31 Shroomish (GRASS): 402,358,235,14
        IMP  L37 Swellow (NORMAL/FLYING): 290,257,432,119
        IMP  L37 Wailord (WATER): 503,442,164,111
        IMP  L46 Delcatty (NORMAL): 253,528,226,204
        IMP  L24 Shroomish (GRASS): 331,474,73,263
        IMP  L24 Slugma (FIRE): 52,317,261,611
        IMP  L32 Sharpedo (WATER/DARK): 400,38,92,269
        IMP  L55 Camerupt (FIRE/GROUND): 284,414,164,90
        IMP  L50 Blaziken (FIRE/FIGHTING): 299,89,14,276
        IMP  L50 Sceptile (GRASS): 402,91,73,447
        IMP  L64 Altaria (DRAGON/FLYING): 200,257,468,76
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 370,9,14,94
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 510,611,92,241
        REG  L39 Claydol (GROUND/PSYCHIC): 326,414,106,277
        REG  L36 Golbat (POISON/FLYING): 512,228,48,590
        REG  L34 Golbat (POISON/FLYING): 413,369,432,590
        REG  L33 Roselia (GRASS/POISON): 437,188,230,191
        REG  L43 Solrock (ROCK/PSYCHIC): 94,442,322,446
        REG  L49 Jellicent (WATER/GHOST): 101,605,218,59
        REG  L37 Skarmory (STEEL/FLYING): 65,228,430,263
        REG  L41 Clamperl (WATER): 503,59,300,34
        REG  L39 Tentacruel (WATER/POISON): 188,282,112,392
        REG  L48 Honchkrow (DARK/FLYING): 413,101,366,492
        REG  L53 Flygon (GROUND/DRAGON): 407,242,468,28
        REG  L23 Grimer (POISON): 474,168,286,114
        REG  L51 Mightyena (DARK): 242,231,514,336
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 280,398,113,282
        BOSS L47 Bewear (NORMAL/FIGHTING): 38,89,14,156
        BOSS L56 Probopass (ROCK/STEEL): 430,435,220,7
        BOSS L41 Golisopod (BUG/WATER): 42,282,14,421
        BOSS L66 Froslass (ICE/GHOST): 58,577,50,445
        BOSS L66 Mandibuzz (DARK/FLYING): 282,369,355,257
        BOSS L57 Dugtrio (GROUND/STEEL): 91,444,262,203
        BOSS L52 Sableye (DARK/GHOST): 399,428,156,180
        BOSS L65 Crobat (POISON/FLYING): 403,141,114,103
        BOSS L64 Masquerain (BUG/FLYING): 324,56,92,218
        BOSS L66 Hydreigon (DARK/DRAGON): 399,512,86,422
        BOSS L65 Gyarados (WATER/FLYING): 401,37,46,231
        BOSS L64 Camerupt (FIRE/GROUND): 89,53,397,261
        BOSS L70 Mewtwo (PSYCHIC): 94,126,105,477
        BOSS L63 Crabominable (FIGHTING/ICE): 280,9,339,133
        IMP  L6 Pichu (ELECTRIC): 84,574,113,104
        IMP  L15 Glaceon (ICE): 196,500,215,92
        IMP  L27 Salandit (POISON/FIRE): 123,52,269,139
        IMP  L28 Noibat (FLYING/DRAGON): 512,399,355,141
        IMP  L41 Noivern (FLYING/DRAGON): 406,257,236,211
        IMP  L70 Primarina (WATER/FAIRY): 61,605,213,304
        IMP  L67 Muk (POISON/DARK): 474,416,397,380
        IMP  L53 Zoroark (DARK): 539,340,197,247
        IMP  L68 Zoroark (DARK): 282,421,156,43
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 209,282,156,683
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 209,252,277,115
        IMP  L68 Snorlax (NORMAL): 38,276,174,281
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 9,411,97,98
        IMP  L51 Shiinotic (GRASS/FAIRY): 605,113,133,412
        IMP  L20 Poipole (POISON): 398,64,164,31
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,218,259,497
        REG  L69 Lapras (WATER/ICE): 420,442,109,406
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 473,609,381,227
        REG  L35 Marowak (FIRE/GHOST): 708,9,116,67
        REG  L5 Yungoos (NORMAL): 317,43,33,279
        REG  L5 Yungoos (NORMAL): 371,92,496,317
        REG  L55 Espeon (PSYCHIC): 94,605,281,226
        REG  L5 Yungoos (NORMAL): 317,213,237,279
        REG  L33 Zubat (POISON/FLYING): 512,141,428,48
        REG  L30 Minior (ROCK/FLYING): 512,444,360,477
        REG  L27 Trumbeak (NORMAL/FLYING): 65,350,355,249
        REG  L62 Persian (NORMAL): 416,421,87,402
        REG  L14 Rattata (DARK/NORMAL): 154,44,289,515
        """);
    }

    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void betterMovesetsGoldenMaster(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        String actual = canonicalBlock(romHandler, gameName);

        if (Boolean.getBoolean("golden.record")) {
            System.out.println("\nEXPECTED.put(\"" + gameName + "\", \"\"\"\n" + actual + "\n\"\"\");");
            return;
        }
        String expected = EXPECTED.get(gameName);
        if (expected == null) {
            // Not frozen yet - print the paste-ready block, then skip (rather than fail) this case.
            System.out.println("\nEXPECTED.put(\"" + gameName + "\", \"\"\"\n" + actual + "\n\"\"\");");
            assumeTrue(false, "No frozen snapshot for " + gameName
                    + " - run with -Dgolden.record=true and paste the printed block into EXPECTED.");
        }
        assertEquals(expected.strip(), actual.strip(),
                "Better Movesets output drifted for " + gameName + " (seed " + SEED + ")");
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
        return romHandler;
    }

    /**
     * The deterministic fingerprint of a ROM's Better Movesets output: for each trainer class in turn (Boss, then
     * Important, then Regular), an even spread of up to {@link #SAMPLE_PER_TIER} of that class's buffed trainer
     * Pokemon, one line of {@code TIER Llevel Name (types): m1,m2,m3,m4} each (or {@code RESET} when the game is left
     * to fill the natural moveset). Iteration order over trainers and their Pokemon is stable and the spread is pure
     * integer arithmetic, so the fingerprint is stable given the seed.
     */
    private String canonicalBlock(RomHandler romHandler, String gameName) {
        romHandler.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());

        Settings s = new Settings();
        s.setBetterBossTrainerMovesets(true);
        s.setBetterImportantTrainerMovesets(true);
        s.setBetterRegularTrainerMovesets(true);
        new TrainerMovesetRandomizer(romHandler, s, new Random(SEED)).randomizeTrainerMovesets();

        StringBuilder sb = new StringBuilder();
        appendTier(sb, romHandler, "BOSS", Trainer::isBoss);
        appendTier(sb, romHandler, "IMP ", Trainer::isImportant);
        appendTier(sb, romHandler, "REG ", Trainer::isRegular);
        return sb.toString().strip();
    }

    /** One holder so a picked mon carries its owning trainer (for the tier tag) alongside the Pokemon. */
    private record TierMon(Trainer trainer, TrainerPokemon pokemon) {}

    /**
     * Appends an even spread of {@link #SAMPLE_PER_TIER} of {@code inTier}'s buffed trainer Pokemon to {@code sb}.
     * Only trainers the randomizer actually touches are considered ({@code inTier} holds AND
     * {@link Trainer#shouldNotGetBuffs()} is false). An empty tier is recorded with an explicit {@code (none)} marker
     * so its absence is frozen rather than silently blank. {@code tierLabel} is the single source of truth for the
     * per-line tag - every mon here matched {@code inTier}, so it is labelled by the tier it was collected under
     * rather than re-derived per mon.
     */
    private static void appendTier(StringBuilder sb, RomHandler romHandler, String tierLabel,
                                   Predicate<Trainer> inTier) {
        List<TierMon> mons = new ArrayList<>();
        for (Trainer tr : romHandler.getTrainers()) {
            if (tr.shouldNotGetBuffs() || !inTier.test(tr)) {
                continue;
            }
            for (TrainerPokemon tp : tr.getPokemon()) {
                mons.add(new TierMon(tr, tp));
            }
        }
        if (mons.isEmpty()) {
            sb.append("# ").append(tierLabel).append(": (none)\n");
            return;
        }
        for (int idx : evenSpread(mons.size(), SAMPLE_PER_TIER)) {
            TierMon m = mons.get(idx);
            Species pk = m.pokemon().getSpecies();
            String moves = m.pokemon().isResetMoves() ? "RESET" : joinMoves(m.pokemon().getMoves());
            sb.append(tierLabel).append(" L").append(m.pokemon().getLevel()).append(' ')
                    .append(pk.getName()).append(" (").append(typeStr(pk)).append("): ")
                    .append(moves).append('\n');
        }
    }

    /**
     * Indices of an even spread of {@code sample} items across {@code size} (all of them if {@code size <= sample}).
     * Pure integer arithmetic - no RNG - so the choice is deterministic given the tier size.
     */
    private static int[] evenSpread(int size, int sample) {
        if (size <= sample) {
            int[] all = new int[size];
            for (int i = 0; i < size; i++) {
                all[i] = i;
            }
            return all;
        }
        int[] idx = new int[sample];
        for (int i = 0; i < sample; i++) {
            idx[i] = (int) Math.round((double) i * (size - 1) / (sample - 1));
        }
        return idx;
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
}
