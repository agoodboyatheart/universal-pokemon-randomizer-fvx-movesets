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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,23,164,90
        BOSS L45 NIDOKING (POISON/GROUND): 89,58,156,116
        BOSS L55 HITMONLEE (FIGHTING): 26,25,156,116
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,92,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,33,86,102
        BOSS L24 RAICHU (ELECTRIC): 84,129,156,92
        BOSS L37 KOFFING (POISON): 124,85,92,108
        BOSS L43 WEEZING (POISON): 124,153,164,126
        BOSS L42 RAPIDASH (FIRE): 126,36,115,45
        BOSS L38 VENOMOTH (BUG/POISON): 60,76,18,78
        BOSS L53 CLOYSTER (WATER/ICE): 62,153,156,110
        BOSS L56 LAPRAS (WATER/ICE): 56,87,92,102
        BOSS L55 HAUNTER (GHOST/POISON): 101,94,156,109
        BOSS L56 DRAGONAIR (DRAGON): 126,87,86,32
        BOSS L62 DRAGONITE (DRAGON/FLYING): 59,63,156,102
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,156,115
        IMP  L15 ABRA (PSYCHIC): 66,161,156,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 13,16,18,115
        IMP  L18 KADABRA (PSYCHIC): 93,5,115,86
        IMP  L16 RATICATE (NORMAL): 33,61,92,156
        IMP  L25 WARTORTLE (WATER): 61,69,92,104
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,115,95
        IMP  L35 ALAKAZAM (PSYCHIC): 60,38,50,118
        IMP  L40 VENUSAUR (GRASS/POISON): 76,38,14,74
        IMP  L45 RHYHORN (GROUND/ROCK): 91,157,92,102
        IMP  L45 GYARADOS (WATER/FLYING): 56,58,92,102
        IMP  L47 GYARADOS (WATER/FLYING): 61,34,92,82
        IMP  L61 ARCANINE (FIRE): 126,91,92,46
        IMP  L63 ARCANINE (FIRE): 53,36,97,104
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,66,14,45
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,28,164
        REG  L18 MANKEY (FIGHTING): 66,5,157,156
        REG  L19 RATTATA (NORMAL): 99,55,102,164
        REG  L37 VULPIX (FIRE): 53,36,156,39
        REG  L29 WEEZING (POISON): 123,33,104,156
        REG  L31 CLOYSTER (WATER/ICE): 59,120,48,156
        REG  L26 MANKEY (FIGHTING): 69,2,164,118
        REG  L30 HORSEA (WATER): 61,58,36,164
        REG  L29 FEAROW (NORMAL/FLYING): 64,129,119,156
        REG  L70 GYARADOS (WATER/FLYING): 61,126,82,104
        REG  L17 MACHOP (FIGHTING): 69,5,90,92
        REG  L28 EKANS (POISON): 40,157,43,104
        REG  L39 DUGTRIO (GROUND): 91,157,104,28
        REG  L33 HAUNTER (GHOST/POISON): 101,85,95,164
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
        BOSS L45 MAGMAR (FIRE): 7,9,156,103
        BOSS L77 BLASTOISE (WATER): 56,59,114,240
        BOSS L58 ARCANINE (FIRE): 53,231,156,237
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,149
        IMP  L12 GASTLY (GHOST/POISON): 122,202,95,168
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,213
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,197,141
        IMP  L32 MEGANIUM (GRASS): 202,89,14,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,205,86,33
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,92,182
        IMP  L35 HAUNTER (GHOST/POISON): 101,195,114,94
        IMP  L35 HAUNTER (GHOST/POISON): 101,138,95,87
        IMP  L43 GENGAR (GHOST/POISON): 101,9,174,109
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,113,105
        IMP  L43 ALAKAZAM (PSYCHIC): 94,8,50,112
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,105,92
        IMP  L50 TYPHLOSION (FIRE): 7,9,182,108
        IMP  L50 FERALIGATR (WATER): 56,157,156,197
        REG  L10 CHIKORITA (GRASS): 202,246,14,45
        REG  L20 QUAGSIRE (WATER/GROUND): 189,249,240,92
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,211,28,207
        REG  L25 NINETALES (FIRE): 52,185,98,91
        REG  L31 RHYDON (GROUND/ROCK): 157,223,7,242
        REG  L18 GROWLITHE (FIRE): 52,225,91,182
        REG  L23 GOLDEEN (WATER): 64,92,213,196
        REG  L28 TENTACOOL (WATER/POISON): 61,132,219,216
        REG  L28 POLIWHIRL (WATER): 145,8,54,203
        REG  L32 ONIX (ROCK/GROUND): 157,89,218,201
        REG  L6 VOLTORB (ELECTRIC): 205,104,129,33
        REG  L31 FURRET (NORMAL): 21,228,182,92
        REG  L42 GOLDUCK (WATER): 58,223,244,213
        REG  L23 PIKACHU (ELECTRIC): 84,205,216,213
        REG  L25 ELECTRODE (ELECTRIC): 49,205,182,174
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,281,336
        BOSS L53 WALREIN (ICE/WATER): 58,157,174,237
        BOSS L26 CAMERUPT (FIRE/GROUND): 189,317,164,116
        BOSS L43 SEALEO (ICE/WATER): 62,157,156,227
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,23,157,126
        BOSS L50 KABUTOPS (ROCK/WATER): 61,202,92,201
        BOSS L46 HITMONCHAN (FIGHTING): 136,157,197,97
        BOSS L50 MANECTRIC (ELECTRIC): 87,242,164,203
        BOSS L46 GROWLITHE (FIRE): 126,36,46,242
        BOSS L45 KANGASKHAN (NORMAL): 306,76,50,182
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,53,97,228
        BOSS L58 SKARMORY (STEEL/FLYING): 143,211,191,201
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 89,113,322,38
        BOSS L56 LAPRAS (WATER/ICE): 56,87,174,329
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,36,182,104
        IMP  L34 MIGHTYENA (DARK): 263,247,281,316
        IMP  L40 GOLBAT (POISON/FLYING): 188,247,18,98
        IMP  L20 GROVYLE (GRASS): 71,225,156,213
        IMP  L29 LOMBRE (WATER/GRASS): 331,9,73,54
        IMP  L18 SLUGMA (FIRE): 52,88,281,237
        IMP  L29 PELIPPER (WATER/FLYING): 16,55,164,351
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,58,174,23
        IMP  L22 ZUBAT (POISON/FLYING): 17,310,269,259
        IMP  L47 ROSELIA (GRASS/POISON): 76,34,164,275
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,164,47
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 209,161,115,49
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 126,223,14,45
        IMP  L34 GROVYLE (GRASS): 202,69,73,242
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,231,174,240
        IMP  L15 MUDKIP (WATER): 352,196,156,45
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 55,290,227,39
        REG  L26 MIGHTYENA (DARK): 44,305,310,269
        REG  L33 MACHOP (FIGHTING): 233,38,193,8
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,247,219,315
        REG  L35 PLUSLE (ELECTRIC): 9,98,313,45
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,203,129,205
        REG  L30 KOFFING (POISON): 124,85,261,108
        REG  L6 SEEDOT (GRASS): 202,98,203,207
        REG  L11 MARILL (WATER): 352,189,47,287
        REG  L26 LOMBRE (WATER/GRASS): 352,168,267,71
        REG  L29 XATU (PSYCHIC/FLYING): 332,168,113,98
        REG  L29 ZUBAT (POISON/FLYING): 332,290,207,218
        REG  L34 PELIPPER (WATER/FLYING): 98,59,104,48
        REG  L5 KYOGRE (WATER): 352,196,173,156
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,182,446,33
        BOSS L49 SCIZOR (BUG/STEEL): 442,228,226,98
        BOSS L52 HIPPOWDON (GROUND): 89,424,303,423
        BOSS L57 MAGMORTAR (FIRE): 394,280,182,112
        BOSS L58 SPIRITOMB (GHOST/DARK): 228,220,108,94
        BOSS L20 CHERRIM (GRASS): 345,205,73,74
        BOSS L29 MACHOKE (FIGHTING): 27,8,113,216
        BOSS L42 ABOMASNOW (GRASS/ICE): 412,280,182,8
        BOSS L44 SNEASEL (DARK/ICE): 420,306,115,373
        BOSS L48 WEAVILE (DARK/ICE): 419,231,92,98
        BOSS L66 WHISCASH (WATER/GROUND): 426,428,156,218
        BOSS L69 RAPIDASH (FIRE): 394,290,164,204
        BOSS L72 ALAKAZAM (PSYCHIC): 427,411,148,416
        BOSS L78 GARCHOMP (DRAGON/GROUND): 407,424,14,201
        BOSS L58 MAGMORTAR (FIRE): 257,411,261,259
        IMP  L7 STARLY (NORMAL/FLYING): 332,228,355,189
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 326,324,446,286
        IMP  L27 GROTLE (GRASS): 402,328,113,388
        IMP  L34 STARAVIA (NORMAL/FLYING): 36,228,18,297
        IMP  L36 STARAPTOR (NORMAL/FLYING): 38,211,182,257
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,444,213,228
        IMP  L47 RAPIDASH (FIRE): 394,98,156,204
        IMP  L42 STARAPTOR (NORMAL/FLYING): 416,413,297,370
        IMP  L25 KADABRA (PSYCHIC): 60,247,50,227
        IMP  L27 GROTLE (GRASS): 331,44,113,115
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,400,339,179
        IMP  L69 RAPIDASH (FIRE): 126,38,213,231
        IMP  L73 SNORLAX (NORMAL): 38,9,18,8
        IMP  L83 SNORLAX (NORMAL): 387,200,174,316
        IMP  L60 SKUNTANK (POISON/DARK): 400,263,156,184
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,44,129,310
        REG  L36 SWINUB (ICE/GROUND): 426,59,46,216
        REG  L6 GEODUDE (ROCK/GROUND): 317,33,445,203
        REG  L21 CARNIVINE (GRASS): 75,44,79,388
        REG  L21 DRIFLOON (GHOST/FLYING): 16,351,114,373
        REG  L36 MURKROW (DARK/FLYING): 399,65,207,247
        REG  L39 MURKROW (DARK/FLYING): 399,257,297,290
        REG  L58 PELIPPER (WATER/FLYING): 56,211,255,254
        REG  L32 EEVEE (NORMAL): 290,247,273,39
        REG  L48 SEAKING (WATER): 127,340,104,175
        REG  L42 GOLBAT (POISON/FLYING): 413,290,114,109
        REG  L23 BUIZEL (WATER): 453,189,29,8
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,85,156,199
        REG  L56 EMPOLEON (WATER/STEEL): 362,59,46,280
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,218
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,18,263
        BOSS L72 Lucario (FIGHTING/STEEL): 418,299,339,399
        BOSS L28 Flaaffy (ELECTRIC): 9,324,219,280
        BOSS L48 Haxorus (DRAGON): 530,401,163,276
        BOSS L50 Cofagrigus (GHOST): 101,399,261,50
        BOSS L67 Simipour (WATER): 362,58,182,270
        BOSS L76 Clefable (NORMAL): 387,309,236,428
        BOSS L28 Emolga (ELECTRIC/FLYING): 84,332,164,486
        BOSS L49 Carracosta (WATER/ROCK): 157,89,182,58
        BOSS L56 Lucario (FIGHTING/STEEL): 231,242,197,299
        BOSS L73 Golurk (GROUND/GHOST): 89,444,92,76
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,8,339,104
        BOSS L75 Arcanine (FIRE): 424,245,261,316
        BOSS L75 Glaceon (ICE): 58,247,273,401
        IMP  L8 Tepig (FIRE): 488,249,174,447
        IMP  L48 Cryogonal (ICE): 59,229,156,92
        IMP  L23 Pansage (GRASS): 402,512,269,91
        IMP  L31 Tranquill (NORMAL/FLYING): 143,369,234,211
        IMP  L39 Unfezant (NORMAL/FLYING): 253,211,197,143
        IMP  L46 Cryogonal (ICE): 62,512,156,229
        IMP  L55 Unfezant (NORMAL/FLYING): 253,257,234,355
        IMP  L55 Simisear (FIRE): 481,276,259,91
        IMP  L62 Unfezant (NORMAL/FLYING): 416,211,156,366
        IMP  L62 Flygon (GROUND/DRAGON): 89,53,444,416
        IMP  L65 Unfezant (NORMAL/FLYING): 416,257,197,526
        IMP  L65 Eelektross (ELECTRIC): 435,369,92,157
        IMP  L41 Simisear (FIRE): 481,416,468,259
        IMP  L48 Unfezant (NORMAL/FLYING): 143,211,156,273
        IMP  L74 Klinklang (STEEL): 544,528,508,475
        REG  L26 Blitzle (ELECTRIC): 351,263,24,156
        REG  L63 Hitmonlee (FIGHTING): 370,299,526,418
        REG  L63 Hitmonchan (FIGHTING): 183,157,501,418
        REG  L56 Unfezant (NORMAL/FLYING): 416,257,197,211
        REG  L47 Boldore (ROCK): 444,414,334,335
        REG  L45 Swinub (ICE/GROUND): 426,419,213,175
        REG  L32 Scolipede (BUG/POISON): 224,401,91,92
        REG  L65 Hitmontop (FIGHTING): 136,228,360,97
        REG  L52 Amoonguss (GRASS/POISON): 202,188,148,147
        REG  L64 Archeops (ROCK/FLYING): 340,525,414,253
        REG  L54 Metang (STEEL/PSYCHIC): 309,428,357,356
        REG  L60 Wooper (WATER/GROUND): 91,34,330,227
        REG  L67 Emboar (FIRE/FIGHTING): 126,457,281,335
        REG  L47 Krookodile (GROUND/DARK): 492,421,92,422
        REG  L25 Litwick (GHOST/FIRE): 481,499,151,180
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,231,164,423
        BOSS L41 Weezing (POISON): 124,126,261,220
        BOSS L5 Zigzagoon (NORMAL): 168,86,104,33
        BOSS L51 Dusclops (GHOST): 506,290,156,7
        BOSS L52 Froslass (ICE/GHOST): 247,324,191,148
        BOSS L57 Claydol (GROUND/PSYCHIC): 94,529,164,322
        BOSS L14 Machop (FIGHTING): 27,418,182,43
        BOSS L28 Slaking (NORMAL): 343,280,174,227
        BOSS L44 Whiscash (WATER/GROUND): 56,444,156,340
        BOSS L70 Sharpedo (WATER/DARK): 56,38,182,59
        BOSS L71 Dusknoir (GHOST): 425,8,50,262
        BOSS L73 Altaria (DRAGON/FLYING): 200,211,54,53
        BOSS L77 Carbink (ROCK/FAIRY): 408,605,182,446
        BOSS L57 Cradily (ROCK/GRASS): 402,157,397,362
        BOSS L57 Milotic (WATER): 503,59,105,156
        IMP  L18 Slugma (FIRE): 510,157,174,261
        IMP  L31 Wailmer (WATER): 503,37,182,340
        IMP  L18 Wailmer (WATER): 250,428,46,497
        IMP  L31 Shroomish (GRASS): 412,474,156,204
        IMP  L37 Swellow (NORMAL/FLYING): 290,228,432,119
        IMP  L37 Wailord (WATER): 323,59,156,392
        IMP  L46 Delcatty (NORMAL): 38,428,86,39
        IMP  L24 Shroomish (GRASS): 402,409,92,182
        IMP  L24 Slugma (FIRE): 488,174,104,205
        IMP  L32 Sharpedo (WATER/DARK): 400,56,269,59
        IMP  L55 Camerupt (FIRE/GROUND): 284,414,164,45
        IMP  L50 Blaziken (FIRE/FIGHTING): 299,413,261,272
        IMP  L50 Sceptile (GRASS): 437,406,235,490
        IMP  L64 Altaria (DRAGON/FLYING): 337,53,182,538
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 370,400,182,404
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,246,106,499
        REG  L39 Claydol (GROUND/PSYCHIC): 60,605,89,472
        REG  L36 Golbat (POISON/FLYING): 512,474,590,212
        REG  L34 Golbat (POISON/FLYING): 188,369,355,18
        REG  L33 Roselia (GRASS/POISON): 188,42,247,14
        REG  L43 Solrock (ROCK/PSYCHIC): 157,428,377,149
        REG  L49 Jellicent (WATER/GHOST): 362,101,506,104
        REG  L37 Skarmory (STEEL/FLYING): 413,228,43,191
        REG  L41 Clamperl (WATER): 362,58,287,290
        REG  L39 Tentacruel (WATER/POISON): 362,58,188,48
        REG  L48 Honchkrow (DARK/FLYING): 228,413,18,195
        REG  L53 Flygon (GROUND/DRAGON): 407,91,48,104
        REG  L23 Grimer (POISON): 398,317,254,107
        REG  L51 Mightyena (DARK): 399,424,180,231
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,339,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 25,707,220,9
        BOSS L56 Probopass (ROCK/STEEL): 408,605,277,263
        BOSS L41 Golisopod (BUG/WATER): 710,660,164,529
        BOSS L66 Froslass (ICE/GHOST): 423,324,92,244
        BOSS L66 Mandibuzz (DARK/FLYING): 399,257,417,260
        BOSS L57 Dugtrio (GROUND/STEEL): 414,444,262,515
        BOSS L52 Sableye (DARK/GHOST): 399,263,261,260
        BOSS L65 Crobat (POISON/FLYING): 403,440,156,207
        BOSS L64 Masquerain (BUG/FLYING): 369,56,366,170
        BOSS L66 Hydreigon (DARK/DRAGON): 407,424,366,103
        BOSS L65 Gyarados (WATER/FLYING): 401,263,86,53
        BOSS L64 Camerupt (FIRE/GROUND): 284,707,261,164
        BOSS L70 Mewtwo (PSYCHIC): 427,411,50,63
        BOSS L63 Crabominable (FIGHTING/ICE): 665,9,92,156
        IMP  L6 Pichu (ELECTRIC): 84,574,113,104
        IMP  L15 Glaceon (ICE): 196,247,694,197
        IMP  L27 Salandit (POISON/FIRE): 52,474,269,92
        IMP  L28 Noibat (FLYING/DRAGON): 314,352,97,421
        IMP  L41 Noivern (FLYING/DRAGON): 406,53,355,211
        IMP  L70 Primarina (WATER/FAIRY): 57,412,92,164
        IMP  L67 Muk (POISON/DARK): 242,474,182,151
        IMP  L53 Zoroark (DARK): 228,369,182,411
        IMP  L68 Zoroark (DARK): 675,340,262,43
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 209,324,227,486
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 609,416,115,175
        IMP  L68 Snorlax (NORMAL): 498,402,18,590
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 209,98,97,381
        IMP  L51 Shiinotic (GRASS/FAIRY): 605,73,236,324
        IMP  L20 Poipole (POISON): 51,263,92,218
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,104,213,496
        REG  L69 Lapras (WATER/ICE): 420,442,109,406
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 94,280,521,204
        REG  L35 Marowak (FIRE/GHOST): 708,9,116,67
        REG  L5 Yungoos (NORMAL): 279,43,33,168
        REG  L5 Yungoos (NORMAL): 317,259,496,351
        REG  L55 Espeon (PSYCHIC): 485,247,36,313
        REG  L5 Yungoos (NORMAL): 371,43,496,351
        REG  L33 Zubat (POISON/FLYING): 19,369,599,247
        REG  L30 Minior (ROCK/FLYING): 512,428,393,397
        REG  L27 Trumbeak (NORMAL/FLYING): 365,280,119,45
        REG  L62 Persian (NORMAL): 163,231,164,39
        REG  L14 Rattata (DARK/NORMAL): 33,44,382,351
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
