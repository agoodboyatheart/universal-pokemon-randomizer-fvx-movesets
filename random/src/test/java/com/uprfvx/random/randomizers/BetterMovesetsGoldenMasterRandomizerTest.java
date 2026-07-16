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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,30,92,157
        BOSS L45 NIDOKING (POISON/GROUND): 89,85,156,61
        BOSS L55 HITMONLEE (FIGHTING): 66,130,164,104
        BOSS L12 GEODUDE (ROCK/GROUND): 99,69,164,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,115,86
        BOSS L24 RAICHU (ELECTRIC): 84,66,86,115
        BOSS L37 KOFFING (POISON): 124,153,164,85
        BOSS L43 WEEZING (POISON): 124,63,92,108
        BOSS L42 RAPIDASH (FIRE): 126,36,115,39
        BOSS L38 VENOMOTH (BUG/POISON): 94,76,50,77
        BOSS L53 CLOYSTER (WATER/ICE): 62,38,92,43
        BOSS L56 LAPRAS (WATER/ICE): 56,87,156,115
        BOSS L55 HAUNTER (GHOST/POISON): 101,92,85,120
        BOSS L56 DRAGONAIR (DRAGON): 21,126,97,85
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,58,86,82
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,129,18,28
        IMP  L15 ABRA (PSYCHIC): 5,66,115,156
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,16,115,104
        IMP  L18 KADABRA (PSYCHIC): 93,5,86,104
        IMP  L16 RATICATE (NORMAL): 158,61,156,102
        IMP  L25 WARTORTLE (WATER): 55,69,115,66
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,115,164
        IMP  L35 ALAKAZAM (PSYCHIC): 60,25,156,105
        IMP  L40 VENUSAUR (GRASS/POISON): 76,36,115,102
        IMP  L45 RHYHORN (GROUND/ROCK): 91,85,156,104
        IMP  L45 GYARADOS (WATER/FLYING): 56,126,115,82
        IMP  L47 GYARADOS (WATER/FLYING): 56,58,164,63
        IMP  L61 ARCANINE (FIRE): 53,63,46,82
        IMP  L63 ARCANINE (FIRE): 53,36,164,102
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,91,14,43
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,28,164
        REG  L18 MANKEY (FIGHTING): 66,5,157,156
        REG  L19 RATTATA (NORMAL): 99,55,102,164
        REG  L37 VULPIX (FIRE): 53,36,156,39
        REG  L29 WEEZING (POISON): 123,33,104,156
        REG  L31 CLOYSTER (WATER/ICE): 59,120,48,156
        REG  L26 MANKEY (FIGHTING): 69,99,156,43
        REG  L30 HORSEA (WATER): 61,59,104,102
        REG  L29 FEAROW (NORMAL/FLYING): 64,99,45,119
        REG  L70 GYARADOS (WATER/FLYING): 61,126,82,164
        REG  L17 MACHOP (FIGHTING): 69,99,90,157
        REG  L28 EKANS (POISON): 40,72,137,164
        REG  L39 DUGTRIO (GROUND): 89,157,92,45
        REG  L33 HAUNTER (GHOST/POISON): 101,120,104,85
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
        BOSS L45 MAGMAR (FIRE): 126,231,156,43
        BOSS L77 BLASTOISE (WATER): 56,59,229,89
        BOSS L58 ARCANINE (FIRE): 53,231,43,29
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
        IMP  L43 ALAKAZAM (PSYCHIC): 60,7,227,203
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,105,112
        IMP  L46 ALAKAZAM (PSYCHIC): 60,8,50,134
        IMP  L50 TYPHLOSION (FIRE): 7,9,92,108
        IMP  L50 FERALIGATR (WATER): 56,157,46,240
        REG  L10 CHIKORITA (GRASS): 202,246,14,45
        REG  L20 QUAGSIRE (WATER/GROUND): 189,249,240,92
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,211,28,207
        REG  L25 NINETALES (FIRE): 52,185,98,91
        REG  L31 RHYDON (GROUND/ROCK): 157,223,7,242
        REG  L18 GROWLITHE (FIRE): 83,225,218,104
        REG  L23 GOLDEEN (WATER): 30,60,48,92
        REG  L28 TENTACOOL (WATER/POISON): 61,132,219,216
        REG  L28 POLIWHIRL (WATER): 55,3,54,168
        REG  L32 ONIX (ROCK/GROUND): 157,29,207,106
        REG  L6 VOLTORB (ELECTRIC): 129,205,218,92
        REG  L31 FURRET (NORMAL): 163,231,7,116
        REG  L42 GOLDUCK (WATER): 8,94,238,113
        REG  L23 PIKACHU (ELECTRIC): 84,205,237,3
        REG  L25 ELECTRODE (ELECTRIC): 33,205,92,49
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,281,336
        BOSS L53 WALREIN (ICE/WATER): 59,157,174,254
        BOSS L26 CAMERUPT (FIRE/GROUND): 52,23,46,104
        BOSS L43 SEALEO (ICE/WATER): 62,156,216,231
        BOSS L44 CAMERUPT (FIRE/GROUND): 53,89,182,92
        BOSS L50 KABUTOPS (ROCK/WATER): 157,202,92,43
        BOSS L46 HITMONCHAN (FIGHTING): 327,263,197,228
        BOSS L50 MANECTRIC (ELECTRIC): 85,231,268,290
        BOSS L46 GROWLITHE (FIRE): 53,37,97,46
        BOSS L45 KANGASKHAN (NORMAL): 146,53,46,247
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,53,114,287
        BOSS L58 SKARMORY (STEEL/FLYING): 65,157,191,290
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,58,347,219
        BOSS L56 LAPRAS (WATER/ICE): 56,85,54,290
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,36,97,94
        IMP  L34 MIGHTYENA (DARK): 263,247,281,316
        IMP  L40 GOLBAT (POISON/FLYING): 188,247,18,109
        IMP  L20 GROVYLE (GRASS): 331,225,164,242
        IMP  L29 LOMBRE (WATER/GRASS): 331,189,14,175
        IMP  L18 SLUGMA (FIRE): 52,205,281,104
        IMP  L29 PELIPPER (WATER/FLYING): 55,196,156,17
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,34,164,59
        IMP  L22 ZUBAT (POISON/FLYING): 332,310,18,259
        IMP  L47 ROSELIA (GRASS/POISON): 76,188,74,34
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,114,76
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 209,161,92,218
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 280,7,116,25
        IMP  L34 GROVYLE (GRASS): 348,9,182,103
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,223,182,193
        IMP  L15 MUDKIP (WATER): 55,205,92,301
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 55,290,227,39
        REG  L26 MIGHTYENA (DARK): 44,305,310,269
        REG  L33 MACHOP (FIGHTING): 233,38,193,8
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,89,218,38
        REG  L35 PLUSLE (ELECTRIC): 9,231,313,86
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,102,207,129
        REG  L30 KOFFING (POISON): 188,53,85,261
        REG  L6 SEEDOT (GRASS): 331,205,111,156
        REG  L11 MARILL (WATER): 352,189,321,287
        REG  L26 LOMBRE (WATER/GRASS): 71,8,164,54
        REG  L29 XATU (PSYCHIC/FLYING): 332,101,297,102
        REG  L29 ZUBAT (POISON/FLYING): 16,129,18,102
        REG  L34 PELIPPER (WATER/FLYING): 290,59,97,240
        REG  L5 KYOGRE (WATER): 352,317,347,184
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
        BOSS L44 SNEASEL (DARK/ICE): 419,91,269,421
        BOSS L48 WEAVILE (DARK/ICE): 228,420,97,180
        BOSS L66 WHISCASH (WATER/GROUND): 401,37,237,58
        BOSS L69 RAPIDASH (FIRE): 394,36,241,340
        BOSS L72 ALAKAZAM (PSYCHIC): 60,324,156,269
        BOSS L78 GARCHOMP (DRAGON/GROUND): 91,398,14,182
        BOSS L58 MAGMORTAR (FIRE): 436,85,269,270
        IMP  L7 STARLY (NORMAL/FLYING): 332,228,355,189
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 326,89,446,334
        IMP  L27 GROTLE (GRASS): 402,44,235,110
        IMP  L34 STARAVIA (NORMAL/FLYING): 98,369,18,45
        IMP  L36 STARAPTOR (NORMAL/FLYING): 36,228,18,257
        IMP  L48 HERACROSS (BUG/FIGHTING): 410,89,92,175
        IMP  L47 RAPIDASH (FIRE): 394,231,164,224
        IMP  L42 STARAPTOR (NORMAL/FLYING): 413,369,156,297
        IMP  L25 KADABRA (PSYCHIC): 60,351,269,213
        IMP  L27 GROTLE (GRASS): 75,446,328,263
        IMP  L61 HERACROSS (BUG/FIGHTING): 410,421,92,179
        IMP  L69 RAPIDASH (FIRE): 394,224,39,38
        IMP  L73 SNORLAX (NORMAL): 34,442,281,316
        IMP  L83 SNORLAX (NORMAL): 34,276,281,85
        IMP  L60 SKUNTANK (POISON/DARK): 242,263,262,108
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,44,129,310
        REG  L36 SWINUB (ICE/GROUND): 426,59,46,216
        REG  L6 GEODUDE (ROCK/GROUND): 317,33,445,203
        REG  L21 CARNIVINE (GRASS): 75,44,79,388
        REG  L21 DRIFLOON (GHOST/FLYING): 466,351,148,107
        REG  L36 MURKROW (DARK/FLYING): 65,211,237,18
        REG  L39 MURKROW (DARK/FLYING): 143,263,182,228
        REG  L58 PELIPPER (WATER/FLYING): 403,56,48,441
        REG  L32 EEVEE (NORMAL): 290,231,39,207
        REG  L48 SEAKING (WATER): 291,340,240,445
        REG  L42 GOLBAT (POISON/FLYING): 403,202,114,109
        REG  L23 BUIZEL (WATER): 55,29,213,316
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,324,103,360
        REG  L56 EMPOLEON (WATER/STEEL): 362,324,97,207
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,218
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,18,263
        BOSS L72 Lucario (FIGHTING/STEEL): 418,299,339,399
        BOSS L28 Flaaffy (ELECTRIC): 9,324,219,280
        BOSS L48 Haxorus (DRAGON): 530,401,163,276
        BOSS L50 Cofagrigus (GHOST): 247,399,92,412
        BOSS L67 Simipour (WATER): 56,63,417,67
        BOSS L76 Clefable (NORMAL): 304,500,312,9
        BOSS L28 Emolga (ELECTRIC/FLYING): 332,98,355,351
        BOSS L49 Carracosta (WATER/ROCK): 157,442,397,362
        BOSS L56 Lucario (FIGHTING/STEEL): 231,89,197,468
        BOSS L73 Golurk (GROUND/GHOST): 89,157,164,276
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 500,425,14,288
        BOSS L75 Arcanine (FIRE): 53,231,46,43
        BOSS L75 Glaceon (ICE): 58,247,174,313
        IMP  L8 Tepig (FIRE): 488,249,174,447
        IMP  L48 Cryogonal (ICE): 58,76,151,115
        IMP  L23 Pansage (GRASS): 331,154,73,317
        IMP  L31 Tranquill (NORMAL/FLYING): 263,257,269,218
        IMP  L39 Unfezant (NORMAL/FLYING): 98,211,164,197
        IMP  L46 Cryogonal (ICE): 58,63,164,54
        IMP  L55 Unfezant (NORMAL/FLYING): 13,143,92,45
        IMP  L55 Simisear (FIRE): 315,253,282,276
        IMP  L62 Unfezant (NORMAL/FLYING): 253,257,273,218
        IMP  L62 Flygon (GROUND/DRAGON): 89,242,444,263
        IMP  L65 Unfezant (NORMAL/FLYING): 63,257,269,95
        IMP  L65 Eelektross (ELECTRIC): 435,430,182,157
        IMP  L41 Simisear (FIRE): 315,231,281,104
        IMP  L48 Unfezant (NORMAL/FLYING): 416,369,269,237
        IMP  L74 Klinklang (STEEL): 544,85,508,475
        REG  L26 Blitzle (ELECTRIC): 351,263,24,156
        REG  L63 Hitmonlee (FIGHTING): 370,299,526,418
        REG  L63 Hitmonchan (FIGHTING): 183,157,501,418
        REG  L56 Unfezant (NORMAL/FLYING): 416,257,197,211
        REG  L47 Boldore (ROCK): 444,414,334,335
        REG  L45 Swinub (ICE/GROUND): 426,419,213,175
        REG  L32 Scolipede (BUG/POISON): 474,401,334,111
        REG  L65 Hitmontop (FIGHTING): 183,529,418,203
        REG  L52 Amoonguss (GRASS/POISON): 202,499,74,230
        REG  L64 Archeops (ROCK/FLYING): 157,337,369,397
        REG  L54 Metang (STEEL/PSYCHIC): 309,153,113,237
        REG  L60 Wooper (WATER/GROUND): 330,89,8,105
        REG  L67 Emboar (FIRE/FIGHTING): 126,280,216,374
        REG  L47 Krookodile (GROUND/DARK): 242,431,200,468
        REG  L25 Litwick (GHOST/FIRE): 52,123,373,151
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,231,164,423
        BOSS L41 Weezing (POISON): 124,126,261,220
        BOSS L5 Zigzagoon (NORMAL): 168,86,104,33
        BOSS L51 Dusclops (GHOST): 247,399,262,156
        BOSS L52 Froslass (ICE/GHOST): 419,358,164,43
        BOSS L57 Claydol (GROUND/PSYCHIC): 473,605,397,104
        BOSS L14 Machop (FIGHTING): 27,317,227,501
        BOSS L28 Slaking (NORMAL): 154,359,227,196
        BOSS L44 Whiscash (WATER/GROUND): 503,414,37,428
        BOSS L70 Sharpedo (WATER/DARK): 400,514,92,218
        BOSS L71 Dusknoir (GHOST): 425,9,114,216
        BOSS L73 Altaria (DRAGON/FLYING): 406,211,97,89
        BOSS L77 Carbink (ROCK/FAIRY): 585,94,164,104
        BOSS L57 Cradily (ROCK/GRASS): 444,402,397,109
        BOSS L57 Milotic (WATER): 56,406,182,300
        IMP  L18 Slugma (FIRE): 510,157,174,261
        IMP  L31 Wailmer (WATER): 503,37,182,340
        IMP  L18 Wailmer (WATER): 250,428,46,497
        IMP  L31 Shroomish (GRASS): 412,474,156,204
        IMP  L37 Swellow (NORMAL/FLYING): 403,586,97,366
        IMP  L37 Wailord (WATER): 291,34,92,207
        IMP  L46 Delcatty (NORMAL): 387,428,164,244
        IMP  L24 Shroomish (GRASS): 72,474,92,363
        IMP  L24 Slugma (FIRE): 510,246,262,216
        IMP  L32 Sharpedo (WATER/DARK): 400,340,269,103
        IMP  L55 Camerupt (FIRE/GROUND): 315,414,281,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 276,89,261,163
        IMP  L50 Sceptile (GRASS): 437,200,97,490
        IMP  L64 Altaria (DRAGON/FLYING): 407,126,46,231
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 409,8,339,514
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,246,106,499
        REG  L39 Claydol (GROUND/PSYCHIC): 60,605,89,472
        REG  L36 Golbat (POISON/FLYING): 512,474,590,212
        REG  L34 Golbat (POISON/FLYING): 188,247,289,366
        REG  L33 Roselia (GRASS/POISON): 412,188,267,178
        REG  L43 Solrock (ROCK/PSYCHIC): 157,512,278,149
        REG  L49 Jellicent (WATER/GHOST): 323,94,104,58
        REG  L37 Skarmory (STEEL/FLYING): 413,228,366,18
        REG  L41 Clamperl (WATER): 503,59,263,182
        REG  L39 Tentacruel (WATER/POISON): 188,58,229,112
        REG  L48 Honchkrow (DARK/FLYING): 228,101,375,297
        REG  L53 Flygon (GROUND/DRAGON): 89,276,231,369
        REG  L23 Grimer (POISON): 398,168,8,237
        REG  L51 Mightyena (DARK): 492,583,316,382
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,339,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 25,707,220,9
        BOSS L56 Probopass (ROCK/STEEL): 430,87,397,259
        BOSS L41 Golisopod (BUG/WATER): 534,404,191,416
        BOSS L66 Froslass (ICE/GHOST): 58,577,50,358
        BOSS L66 Mandibuzz (DARK/FLYING): 282,19,355,289
        BOSS L57 Dugtrio (GROUND/STEEL): 91,444,262,482
        BOSS L52 Sableye (DARK/GHOST): 399,398,197,386
        BOSS L65 Crobat (POISON/FLYING): 440,141,355,114
        BOSS L64 Masquerain (BUG/FLYING): 403,61,114,244
        BOSS L66 Hydreigon (DARK/DRAGON): 525,257,269,21
        BOSS L65 Gyarados (WATER/FLYING): 340,89,164,406
        BOSS L64 Camerupt (FIRE/GROUND): 426,257,174,218
        BOSS L70 Mewtwo (PSYCHIC): 473,396,156,285
        BOSS L63 Crabominable (FIGHTING/ICE): 409,228,156,444
        IMP  L6 Pichu (ELECTRIC): 84,574,113,104
        IMP  L15 Glaceon (ICE): 196,247,694,197
        IMP  L27 Salandit (POISON/FIRE): 52,474,269,92
        IMP  L28 Noibat (FLYING/DRAGON): 16,352,48,404
        IMP  L41 Noivern (FLYING/DRAGON): 337,411,366,211
        IMP  L70 Primarina (WATER/FAIRY): 664,605,156,213
        IMP  L67 Muk (POISON/DARK): 242,280,397,151
        IMP  L53 Zoroark (DARK): 539,369,262,383
        IMP  L68 Zoroark (DARK): 675,304,182,447
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 609,473,86,590
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 209,231,227,39
        IMP  L68 Snorlax (NORMAL): 34,667,182,484
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 9,473,273,683
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,138,147,213
        IMP  L20 Poipole (POISON): 398,497,182,64
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,104,213,496
        REG  L69 Lapras (WATER/ICE): 420,321,219,34
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 85,473,273,278
        REG  L35 Marowak (FIRE/GHOST): 708,9,116,67
        REG  L5 Yungoos (NORMAL): 168,526,496,279
        REG  L5 Yungoos (NORMAL): 279,104,33,371
        REG  L55 Espeon (PSYCHIC): 60,605,215,287
        REG  L5 Yungoos (NORMAL): 371,269,496,279
        REG  L33 Zubat (POISON/FLYING): 413,141,202,92
        REG  L30 Minior (ROCK/FLYING): 512,605,109,201
        REG  L27 Trumbeak (NORMAL/FLYING): 65,350,355,249
        REG  L62 Persian (NORMAL): 304,399,204,175
        REG  L14 Rattata (DARK/NORMAL): 33,44,196,382
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
