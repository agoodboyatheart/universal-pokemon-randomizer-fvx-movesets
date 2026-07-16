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
        BOSS L24 RAICHU (ELECTRIC): 84,6,86,102
        BOSS L37 KOFFING (POISON): 124,153,164,87
        BOSS L43 WEEZING (POISON): 124,63,92,108
        BOSS L42 RAPIDASH (FIRE): 126,36,115,39
        BOSS L38 VENOMOTH (BUG/POISON): 94,76,50,77
        BOSS L53 CLOYSTER (WATER/ICE): 62,38,115,102
        BOSS L56 LAPRAS (WATER/ICE): 61,87,156,149
        BOSS L55 HAUNTER (GHOST/POISON): 101,156,120,94
        BOSS L56 DRAGONAIR (DRAGON): 87,59,164,32
        BOSS L62 DRAGONITE (DRAGON/FLYING): 21,126,115,58
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,129,18,28
        IMP  L15 ABRA (PSYCHIC): 5,66,115,156
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,16,115,104
        IMP  L18 KADABRA (PSYCHIC): 93,161,86,66
        IMP  L16 RATICATE (NORMAL): 99,61,156,102
        IMP  L25 WARTORTLE (WATER): 61,66,156,92
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,92,104
        IMP  L35 ALAKAZAM (PSYCHIC): 60,91,105,69
        IMP  L40 VENUSAUR (GRASS/POISON): 76,38,115,77
        IMP  L45 RHYHORN (GROUND/ROCK): 89,23,156,126
        IMP  L45 GYARADOS (WATER/FLYING): 61,34,156,85
        IMP  L47 GYARADOS (WATER/FLYING): 61,63,92,58
        IMP  L61 ARCANINE (FIRE): 53,91,97,38
        IMP  L63 ARCANINE (FIRE): 53,34,46,104
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,66,14,45
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
        REG  L33 HAUNTER (GHOST/POISON): 101,87,164,109
        """);
        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,168
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,179,168
        BOSS L31 PILOSWINE (ICE/GROUND): 59,157,182,36
        BOSS L37 DRAGONAIR (DRAGON): 21,126,156,192
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 202,188,73,174
        BOSS L46 MACHAMP (FIGHTING): 67,7,113,203
        BOSS L40 ARIADOS (BUG/POISON): 188,101,226,202
        BOSS L47 DRAGONITE (DRAGON/FLYING): 29,53,86,54
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,174,29
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,61,105,174
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,29,73,203
        BOSS L33 ARIADOS (BUG/POISON): 188,202,226,101
        BOSS L45 MAGMAR (FIRE): 7,5,9,238
        BOSS L77 BLASTOISE (WATER): 56,8,237,231
        BOSS L58 ARCANINE (FIRE): 126,231,43,245
        IMP  L12 GASTLY (GHOST/POISON): 122,202,114,95
        IMP  L12 GASTLY (GHOST/POISON): 122,168,182,244
        IMP  L20 HAUNTER (GHOST/POISON): 247,168,114,218
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,141,185
        IMP  L32 MEGANIUM (GRASS): 202,89,115,34
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,49,174,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,92,182
        IMP  L35 HAUNTER (GHOST/POISON): 101,156,114,247
        IMP  L35 HAUNTER (GHOST/POISON): 101,138,95,87
        IMP  L43 GENGAR (GHOST/POISON): 101,7,114,202
        IMP  L43 ALAKAZAM (PSYCHIC): 60,247,105,63
        IMP  L43 ALAKAZAM (PSYCHIC): 94,9,182,113
        IMP  L46 ALAKAZAM (PSYCHIC): 60,7,223,29
        IMP  L50 TYPHLOSION (FIRE): 126,66,46,89
        IMP  L50 FERALIGATR (WATER): 56,58,46,157
        REG  L10 CHIKORITA (GRASS): 202,246,14,45
        REG  L20 QUAGSIRE (WATER/GROUND): 91,8,182,249
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,211,156
        REG  L25 NINETALES (FIRE): 52,185,95,98
        REG  L31 RHYDON (GROUND/ROCK): 157,223,9,222
        REG  L18 GROWLITHE (FIRE): 52,225,174,213
        REG  L23 GOLDEEN (WATER): 60,64,92,48
        REG  L28 TENTACOOL (WATER/POISON): 61,202,114,51
        REG  L28 POLIWHIRL (WATER): 145,249,104,170
        REG  L32 ONIX (ROCK/GROUND): 157,231,156,106
        REG  L6 VOLTORB (ELECTRIC): 205,216,129,33
        REG  L31 FURRET (NORMAL): 29,223,231,92
        REG  L42 GOLDUCK (WATER): 238,60,95,193
        REG  L23 PIKACHU (ELECTRIC): 84,205,186,21
        REG  L25 ELECTRODE (ELECTRIC): 49,205,237,174
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,247,281,63
        BOSS L53 WALREIN (ICE/WATER): 58,157,281,182
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,205,46,184
        BOSS L43 SEALEO (ICE/WATER): 62,157,156,227
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,290,102,157
        BOSS L50 KABUTOPS (ROCK/WATER): 157,280,14,106
        BOSS L46 HITMONCHAN (FIGHTING): 183,157,197,156
        BOSS L50 MANECTRIC (ELECTRIC): 87,242,182,104
        BOSS L46 GROWLITHE (FIRE): 53,37,164,316
        BOSS L45 KANGASKHAN (NORMAL): 146,247,156,8
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,349,53
        BOSS L58 SKARMORY (STEEL/FLYING): 211,143,18,319
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 157,115,34,89
        BOSS L56 LAPRAS (WATER/ICE): 56,87,164,216
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,280,97,36
        IMP  L34 MIGHTYENA (DARK): 263,247,281,316
        IMP  L40 GOLBAT (POISON/FLYING): 188,202,164,228
        IMP  L20 GROVYLE (GRASS): 331,9,73,225
        IMP  L29 LOMBRE (WATER/GRASS): 331,263,14,8
        IMP  L18 SLUGMA (FIRE): 52,205,281,216
        IMP  L29 PELIPPER (WATER/FLYING): 55,239,17,129
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,36,156,193
        IMP  L22 ZUBAT (POISON/FLYING): 16,211,18,164
        IMP  L47 ROSELIA (GRASS/POISON): 202,38,182,275
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,126,92,263
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 209,161,203,192
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 315,9,156,179
        IMP  L34 GROVYLE (GRASS): 348,223,73,92
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,58,164,216
        IMP  L15 MUDKIP (WATER): 352,253,174,164
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 55,290,227,39
        REG  L26 MIGHTYENA (DARK): 44,310,305,316
        REG  L33 MACHOP (FIGHTING): 223,25,164,156
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,247,219,315
        REG  L35 PLUSLE (ELECTRIC): 9,263,313,69
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,205,86,48
        REG  L30 KOFFING (POISON): 124,290,216,261
        REG  L6 SEEDOT (GRASS): 331,205,98,104
        REG  L11 MARILL (WATER): 55,69,227,47
        REG  L26 LOMBRE (WATER/GRASS): 202,9,156,175
        REG  L29 XATU (PSYCHIC/FLYING): 64,185,211,213
        REG  L29 ZUBAT (POISON/FLYING): 16,310,102,259
        REG  L34 PELIPPER (WATER/FLYING): 263,59,216,48
        REG  L5 KYOGRE (WATER): 352,196,189,203
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,182,446,33
        BOSS L49 SCIZOR (BUG/STEEL): 442,228,226,98
        BOSS L52 HIPPOWDON (GROUND): 89,424,303,423
        BOSS L57 MAGMORTAR (FIRE): 394,280,182,112
        BOSS L58 SPIRITOMB (GHOST/DARK): 228,220,363,94
        BOSS L20 CHERRIM (GRASS): 412,205,164,312
        BOSS L29 MACHOKE (FIGHTING): 27,317,156,96
        BOSS L42 ABOMASNOW (GRASS/ICE): 58,452,164,104
        BOSS L44 SNEASEL (DARK/ICE): 420,404,115,458
        BOSS L48 WEAVILE (DARK/ICE): 400,404,97,163
        BOSS L66 WHISCASH (WATER/GROUND): 426,59,92,56
        BOSS L69 RAPIDASH (FIRE): 394,290,97,241
        BOSS L72 ALAKAZAM (PSYCHIC): 60,247,278,263
        BOSS L78 GARCHOMP (DRAGON/GROUND): 200,89,46,53
        BOSS L58 MAGMORTAR (FIRE): 126,157,43,231
        IMP  L7 STARLY (NORMAL/FLYING): 332,228,355,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,89,446,324
        IMP  L27 GROTLE (GRASS): 75,328,235,174
        IMP  L34 STARAVIA (NORMAL/FLYING): 98,228,182,257
        IMP  L36 STARAPTOR (NORMAL/FLYING): 36,211,297,257
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,421,182,228
        IMP  L47 RAPIDASH (FIRE): 394,398,36,224
        IMP  L42 STARAPTOR (NORMAL/FLYING): 416,370,97,104
        IMP  L25 KADABRA (PSYCHIC): 60,247,50,290
        IMP  L27 GROTLE (GRASS): 75,173,156,447
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,400,156,218
        IMP  L69 RAPIDASH (FIRE): 315,263,156,214
        IMP  L73 SNORLAX (NORMAL): 263,89,182,8
        IMP  L83 SNORLAX (NORMAL): 34,276,18,87
        IMP  L60 SKUNTANK (POISON/DARK): 400,398,182,247
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,44,129,310
        REG  L36 SWINUB (ICE/GROUND): 426,59,46,216
        REG  L6 GEODUDE (ROCK/GROUND): 317,33,445,203
        REG  L21 CARNIVINE (GRASS): 412,189,380,374
        REG  L21 DRIFLOON (GHOST/FLYING): 247,314,207,86
        REG  L36 MURKROW (DARK/FLYING): 143,94,18,347
        REG  L39 MURKROW (DARK/FLYING): 143,211,373,119
        REG  L58 PELIPPER (WATER/FLYING): 403,441,402,207
        REG  L32 EEVEE (NORMAL): 98,247,204,445
        REG  L48 SEAKING (WATER): 291,30,164,114
        REG  L42 GOLBAT (POISON/FLYING): 403,369,103,212
        REG  L23 BUIZEL (WATER): 453,3,91,156
        REG  L42 MAGNETON (ELECTRIC/STEEL): 85,161,319,360
        REG  L56 EMPOLEON (WATER/STEEL): 362,58,446,300
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,247
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,18,412
        BOSS L72 Lucario (FIGHTING/STEEL): 509,299,339,399
        BOSS L28 Flaaffy (ELECTRIC): 84,263,113,216
        BOSS L48 Haxorus (DRAGON): 337,91,269,14
        BOSS L50 Cofagrigus (GHOST): 101,399,262,263
        BOSS L67 Simipour (WATER): 362,231,417,270
        BOSS L76 Clefable (NORMAL): 387,309,236,472
        BOSS L28 Emolga (ELECTRIC/FLYING): 451,369,164,310
        BOSS L49 Carracosta (WATER/ROCK): 401,442,156,411
        BOSS L56 Lucario (FIGHTING/STEEL): 430,299,97,179
        BOSS L73 Golurk (GROUND/GHOST): 89,430,174,359
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 409,425,339,427
        BOSS L75 Arcanine (FIRE): 481,422,179,38
        BOSS L75 Glaceon (ICE): 58,485,174,46
        IMP  L8 Tepig (FIRE): 488,249,174,496
        IMP  L48 Cryogonal (ICE): 58,430,156,324
        IMP  L23 Pansage (GRASS): 22,91,92,44
        IMP  L31 Tranquill (NORMAL/FLYING): 263,211,273,369
        IMP  L39 Unfezant (NORMAL/FLYING): 416,403,156,214
        IMP  L46 Cryogonal (ICE): 62,324,151,115
        IMP  L55 Unfezant (NORMAL/FLYING): 98,211,273,369
        IMP  L55 Simisear (FIRE): 481,276,164,421
        IMP  L62 Unfezant (NORMAL/FLYING): 143,211,197,95
        IMP  L62 Flygon (GROUND/DRAGON): 337,9,182,49
        IMP  L65 Unfezant (NORMAL/FLYING): 63,369,366,355
        IMP  L65 Eelektross (ELECTRIC): 521,525,86,280
        IMP  L41 Simisear (FIRE): 257,231,92,411
        IMP  L48 Unfezant (NORMAL/FLYING): 253,257,164,45
        IMP  L74 Klinklang (STEEL): 544,528,508,237
        REG  L26 Blitzle (ELECTRIC): 351,263,24,156
        REG  L63 Hitmonlee (FIGHTING): 370,299,526,418
        REG  L63 Hitmonchan (FIGHTING): 183,157,501,418
        REG  L56 Unfezant (NORMAL/FLYING): 263,143,234,516
        REG  L47 Boldore (ROCK): 157,89,430,104
        REG  L45 Swinub (ICE/GROUND): 419,89,213,316
        REG  L32 Scolipede (BUG/POISON): 224,91,263,213
        REG  L65 Hitmontop (FIGHTING): 370,228,529,104
        REG  L52 Amoonguss (GRASS/POISON): 474,492,78,34
        REG  L64 Archeops (ROCK/FLYING): 457,231,98,334
        REG  L54 Metang (STEEL/PSYCHIC): 418,228,263,280
        REG  L60 Wooper (WATER/GROUND): 89,401,227,482
        REG  L67 Emboar (FIRE/FIGHTING): 411,315,174,157
        REG  L47 Krookodile (GROUND/DARK): 492,276,431,335
        REG  L25 Litwick (GHOST/FIRE): 52,123,373,151
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,231,164,424
        BOSS L41 Weezing (POISON): 188,60,182,263
        BOSS L5 Zigzagoon (NORMAL): 228,218,237,189
        BOSS L51 Dusclops (GHOST): 101,157,94,425
        BOSS L52 Froslass (ICE/GHOST): 506,324,191,109
        BOSS L57 Claydol (GROUND/PSYCHIC): 326,91,115,278
        BOSS L14 Machop (FIGHTING): 27,317,113,92
        BOSS L28 Slaking (NORMAL): 163,359,269,67
        BOSS L44 Whiscash (WATER/GROUND): 89,428,164,263
        BOSS L70 Sharpedo (WATER/DARK): 400,89,46,43
        BOSS L71 Dusknoir (GHOST): 425,157,220,212
        BOSS L73 Altaria (DRAGON/FLYING): 406,89,290,58
        BOSS L77 Carbink (ROCK/FAIRY): 408,585,115,106
        BOSS L57 Cradily (ROCK/GRASS): 202,157,220,201
        BOSS L57 Milotic (WATER): 503,406,182,287
        IMP  L18 Slugma (FIRE): 510,261,207,88
        IMP  L31 Wailmer (WATER): 362,340,182,38
        IMP  L18 Wailmer (WATER): 352,46,487,290
        IMP  L31 Shroomish (GRASS): 202,358,235,74
        IMP  L37 Swellow (NORMAL/FLYING): 290,228,432,287
        IMP  L37 Wailord (WATER): 323,499,92,304
        IMP  L46 Delcatty (NORMAL): 514,273,247,528
        IMP  L24 Shroomish (GRASS): 202,29,73,14
        IMP  L24 Slugma (FIRE): 488,157,151,385
        IMP  L32 Sharpedo (WATER/DARK): 453,89,182,590
        IMP  L55 Camerupt (FIRE/GROUND): 284,89,182,92
        IMP  L50 Blaziken (FIRE/FIGHTING): 299,91,174,398
        IMP  L50 Sceptile (GRASS): 437,157,197,306
        IMP  L64 Altaria (DRAGON/FLYING): 143,407,164,538
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 370,400,227,473
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,246,106,499
        REG  L39 Claydol (GROUND/PSYCHIC): 60,605,89,472
        REG  L36 Golbat (POISON/FLYING): 413,257,48,599
        REG  L34 Golbat (POISON/FLYING): 413,428,162,95
        REG  L33 Roselia (GRASS/POISON): 474,437,346,363
        REG  L43 Solrock (ROCK/PSYCHIC): 428,53,106,207
        REG  L49 Jellicent (WATER/GHOST): 503,399,219,378
        REG  L37 Skarmory (STEEL/FLYING): 65,442,590,385
        REG  L41 Clamperl (WATER): 362,58,392,203
        REG  L39 Tentacruel (WATER/POISON): 482,330,103,219
        REG  L48 Honchkrow (DARK/FLYING): 143,247,269,416
        REG  L53 Flygon (GROUND/DRAGON): 89,211,28,116
        REG  L23 Grimer (POISON): 398,612,50,168
        REG  L51 Mightyena (DARK): 399,231,253,583
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,339,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 359,157,269,218
        BOSS L56 Probopass (ROCK/STEEL): 408,435,277,334
        BOSS L41 Golisopod (BUG/WATER): 127,42,398,282
        BOSS L66 Froslass (ICE/GHOST): 419,358,220,156
        BOSS L66 Mandibuzz (DARK/FLYING): 492,198,18,257
        BOSS L57 Dugtrio (GROUND/STEEL): 707,416,156,334
        BOSS L52 Sableye (DARK/GHOST): 421,399,105,386
        BOSS L65 Crobat (POISON/FLYING): 19,404,269,188
        BOSS L64 Masquerain (BUG/FLYING): 405,61,18,170
        BOSS L66 Hydreigon (DARK/DRAGON): 407,19,432,180
        BOSS L65 Gyarados (WATER/FLYING): 401,200,156,214
        BOSS L64 Camerupt (FIRE/GROUND): 436,707,164,74
        BOSS L70 Mewtwo (PSYCHIC): 94,324,182,490
        BOSS L63 Crabominable (FIGHTING/ICE): 419,146,92,179
        IMP  L6 Pichu (ELECTRIC): 84,574,113,156
        IMP  L15 Glaceon (ICE): 196,247,694,500
        IMP  L27 Salandit (POISON/FIRE): 52,337,261,474
        IMP  L28 Noibat (FLYING/DRAGON): 16,399,366,141
        IMP  L41 Noivern (FLYING/DRAGON): 542,257,366,406
        IMP  L70 Primarina (WATER/FAIRY): 61,605,115,58
        IMP  L67 Muk (POISON/DARK): 188,416,397,220
        IMP  L53 Zoroark (DARK): 539,421,468,180
        IMP  L68 Zoroark (DARK): 492,247,156,411
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 609,416,277,604
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 473,411,86,604
        IMP  L68 Snorlax (NORMAL): 387,667,281,104
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 9,324,321,21
        IMP  L51 Shiinotic (GRASS/FAIRY): 605,138,147,324
        IMP  L20 Poipole (POISON): 474,497,92,218
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,590,216,497
        REG  L69 Lapras (WATER/ICE): 420,401,349,87
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 209,473,97,186
        REG  L35 Marowak (FIRE/GHOST): 708,280,155,37
        REG  L5 Yungoos (NORMAL): 168,164,496,317
        REG  L5 Yungoos (NORMAL): 168,207,497,317
        REG  L55 Espeon (PSYCHIC): 60,324,376,347
        REG  L5 Yungoos (NORMAL): 371,43,496,317
        REG  L33 Zubat (POISON/FLYING): 413,141,355,257
        REG  L30 Minior (ROCK/FLYING): 157,512,397,201
        REG  L27 Trumbeak (NORMAL/FLYING): 365,168,526,280
        REG  L62 Persian (NORMAL): 63,492,244,180
        REG  L14 Rattata (DARK/NORMAL): 162,98,415,44
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
