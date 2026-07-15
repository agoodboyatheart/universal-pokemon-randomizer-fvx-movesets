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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,85,164,92
        BOSS L45 NIDOKING (POISON/GROUND): 89,126,115,116
        BOSS L55 HITMONLEE (FIGHTING): 26,38,156,116
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,92
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,92,106
        BOSS L24 RAICHU (ELECTRIC): 84,129,115,45
        BOSS L37 KOFFING (POISON): 124,126,164,102
        BOSS L43 WEEZING (POISON): 124,85,156,153
        BOSS L42 RAPIDASH (FIRE): 126,63,115,92
        BOSS L38 VENOMOTH (BUG/POISON): 141,94,164,18
        BOSS L53 CLOYSTER (WATER/ICE): 62,38,92,110
        BOSS L56 LAPRAS (WATER/ICE): 61,87,115,82
        BOSS L55 HAUNTER (GHOST/POISON): 101,94,164,95
        BOSS L56 DRAGONAIR (DRAGON): 38,126,97,85
        BOSS L62 DRAGONITE (DRAGON/FLYING): 58,38,115,82
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,115,28,102
        IMP  L15 ABRA (PSYCHIC): 66,161,115,118
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,13,156,18
        IMP  L18 KADABRA (PSYCHIC): 93,161,115,50
        IMP  L16 RATICATE (NORMAL): 129,61,164,39
        IMP  L25 WARTORTLE (WATER): 61,69,156,5
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,115,102
        IMP  L35 ALAKAZAM (PSYCHIC): 94,91,105,50
        IMP  L40 VENUSAUR (GRASS/POISON): 75,38,115,102
        IMP  L45 RHYHORN (GROUND/ROCK): 91,85,156,92
        IMP  L45 GYARADOS (WATER/FLYING): 61,87,156,115
        IMP  L47 GYARADOS (WATER/FLYING): 61,85,156,82
        IMP  L61 ARCANINE (FIRE): 53,91,46,115
        IMP  L63 ARCANINE (FIRE): 53,38,97,46
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,91,14,92
        REG  L11 RATTATA (NORMAL): 129,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,115,28,18
        REG  L18 MANKEY (FIGHTING): 69,129,92,43
        REG  L19 RATTATA (NORMAL): 129,61,39,156
        REG  L37 VULPIX (FIRE): 53,91,109,38
        REG  L29 WEEZING (POISON): 124,99,102,104
        REG  L31 CLOYSTER (WATER/ICE): 62,38,48,110
        REG  L26 MANKEY (FIGHTING): 69,129,43,118
        REG  L30 HORSEA (WATER): 61,59,104,108
        REG  L29 FEAROW (NORMAL/FLYING): 65,129,119,45
        REG  L70 GYARADOS (WATER/FLYING): 61,85,43,104
        REG  L17 MACHOP (FIGHTING): 69,2,92,102
        REG  L28 EKANS (POISON): 40,44,137,43
        REG  L39 DUGTRIO (GROUND): 89,38,45,104
        REG  L33 HAUNTER (GHOST/POISON): 101,85,156,164
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,185,156,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,29,113,92
        BOSS L31 PILOSWINE (ICE/GROUND): 58,89,182,203
        BOSS L37 DRAGONAIR (DRAGON): 82,53,86,59
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 202,188,73,121
        BOSS L46 MACHAMP (FIGHTING): 238,29,174,193
        BOSS L40 ARIADOS (BUG/POISON): 188,101,156,50
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,223,113,85
        BOSS L42 OMASTAR (ROCK/WATER): 61,58,114,48
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,85,105,107
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,38,73,235
        BOSS L33 ARIADOS (BUG/POISON): 188,101,226,141
        BOSS L45 MAGMAR (FIRE): 53,223,197,104
        BOSS L77 BLASTOISE (WATER): 56,231,46,44
        BOSS L58 ARCANINE (FIRE): 126,245,46,97
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,218
        IMP  L12 GASTLY (GHOST/POISON): 122,202,95,168
        IMP  L20 HAUNTER (GHOST/POISON): 247,202,174,168
        IMP  L20 ZUBAT (POISON/FLYING): 16,185,18,213
        IMP  L32 MEGANIUM (GRASS): 202,89,73,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,86,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,114,180
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,114,95
        IMP  L43 GENGAR (GHOST/POISON): 247,7,156,244
        IMP  L43 ALAKAZAM (PSYCHIC): 94,8,50,192
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,113,223
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,174,134
        IMP  L50 TYPHLOSION (FIRE): 126,89,46,66
        IMP  L50 FERALIGATR (WATER): 56,89,46,231
        REG  L10 CHIKORITA (GRASS): 202,246,73,207
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,39,156
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,211,16
        REG  L25 NINETALES (FIRE): 52,185,92,174
        REG  L31 RHYDON (GROUND/ROCK): 89,231,242,184
        REG  L18 GROWLITHE (FIRE): 52,225,216,156
        REG  L23 GOLDEEN (WATER): 30,196,64,203
        REG  L28 TENTACOOL (WATER/POISON): 61,62,51,182
        REG  L28 POLIWHIRL (WATER): 61,29,54,182
        REG  L32 ONIX (ROCK/GROUND): 89,231,203,174
        REG  L6 VOLTORB (ELECTRIC): 205,129,207,216
        REG  L31 FURRET (NORMAL): 38,231,7,9
        REG  L42 GOLDUCK (WATER): 238,58,193,244
        REG  L23 PIKACHU (ELECTRIC): 9,98,86,197
        REG  L25 ELECTRODE (ELECTRIC): 29,205,104,207
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,164,281
        BOSS L53 WALREIN (ICE/WATER): 59,231,156,92
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,263,182,336
        BOSS L43 SEALEO (ICE/WATER): 59,157,164,45
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,89,182,237
        BOSS L50 KABUTOPS (ROCK/WATER): 157,280,164,319
        BOSS L46 HITMONCHAN (FIGHTING): 136,89,182,38
        BOSS L50 MANECTRIC (ELECTRIC): 87,242,156,268
        BOSS L46 GROWLITHE (FIRE): 257,242,97,46
        BOSS L45 KANGASKHAN (NORMAL): 252,157,50,207
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,53,182,45
        BOSS L58 SKARMORY (STEEL/FLYING): 211,65,191,218
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,58,347,322
        BOSS L56 LAPRAS (WATER/ICE): 59,85,174,321
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 232,247,97,203
        IMP  L34 MIGHTYENA (DARK): 44,231,46,289
        IMP  L40 GOLBAT (POISON/FLYING): 188,98,174,212
        IMP  L20 GROVYLE (GRASS): 202,225,73,203
        IMP  L29 LOMBRE (WATER/GRASS): 202,252,73,267
        IMP  L18 SLUGMA (FIRE): 52,157,151,241
        IMP  L29 PELIPPER (WATER/FLYING): 17,351,182,207
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,38,174,193
        IMP  L22 ZUBAT (POISON/FLYING): 17,247,174,98
        IMP  L47 ROSELIA (GRASS/POISON): 345,188,164,102
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,89,349,58
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 87,38,156,216
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 126,157,28,280
        IMP  L34 GROVYLE (GRASS): 348,9,156,306
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,231,174,156
        IMP  L15 MUDKIP (WATER): 352,290,164,174
        REG  L21 GEODUDE (ROCK/GROUND): 91,290,335,156
        REG  L26 MARILL (WATER): 61,280,207,21
        REG  L26 MIGHTYENA (DARK): 44,305,218,281
        REG  L33 MACHOP (FIGHTING): 223,126,9,118
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,38,219,207
        REG  L35 PLUSLE (ELECTRIC): 87,231,273,86
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,129,218,205
        REG  L30 KOFFING (POISON): 188,85,139,60
        REG  L6 SEEDOT (GRASS): 202,98,102,106
        REG  L11 MARILL (WATER): 352,69,102,321
        REG  L26 LOMBRE (WATER/GRASS): 202,196,156,175
        REG  L29 XATU (PSYCHIC/FLYING): 65,185,115,207
        REG  L29 ZUBAT (POISON/FLYING): 17,98,269,174
        REG  L34 PELIPPER (WATER/FLYING): 17,59,240,211
        REG  L5 KYOGRE (WATER): 352,351,111,164
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,189,164,175
        BOSS L49 SCIZOR (BUG/STEEL): 418,276,226,404
        BOSS L52 HIPPOWDON (GROUND): 414,444,446,182
        BOSS L57 MAGMORTAR (FIRE): 315,411,92,112
        BOSS L58 SPIRITOMB (GHOST/DARK): 466,399,220,288
        BOSS L20 CHERRIM (GRASS): 412,263,92,267
        BOSS L29 MACHOKE (FIGHTING): 280,371,156,227
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,38,164,203
        BOSS L44 SNEASEL (DARK/ICE): 420,398,115,252
        BOSS L48 WEAVILE (DARK/ICE): 58,280,417,164
        BOSS L66 WHISCASH (WATER/GROUND): 401,58,182,133
        BOSS L69 RAPIDASH (FIRE): 315,231,97,261
        BOSS L72 ALAKAZAM (PSYCHIC): 94,411,227,412
        BOSS L78 GARCHOMP (DRAGON/GROUND): 200,89,46,444
        BOSS L58 MAGMORTAR (FIRE): 257,238,261,374
        IMP  L7 STARLY (NORMAL/FLYING): 98,228,355,104
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,185,164,148
        IMP  L27 GROTLE (GRASS): 402,44,174,115
        IMP  L34 STARAVIA (NORMAL/FLYING): 38,211,355,297
        IMP  L36 STARAPTOR (NORMAL/FLYING): 98,370,18,297
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,444,156,38
        IMP  L47 RAPIDASH (FIRE): 394,224,97,445
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,370,92,211
        IMP  L25 KADABRA (PSYCHIC): 428,7,227,134
        IMP  L27 GROTLE (GRASS): 402,44,156,110
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,370,339,400
        IMP  L69 RAPIDASH (FIRE): 394,231,97,39
        IMP  L73 SNORLAX (NORMAL): 387,157,59,7
        IMP  L83 SNORLAX (NORMAL): 416,276,18,59
        IMP  L60 SKUNTANK (POISON/DARK): 399,91,164,46
        REG  L5 STARLY (NORMAL/FLYING): 98,365,28,189
        REG  L29 ZUBAT (POISON/FLYING): 17,185,428,202
        REG  L36 SWINUB (ICE/GROUND): 420,38,414,316
        REG  L6 GEODUDE (ROCK/GROUND): 246,33,360,213
        REG  L21 CARNIVINE (GRASS): 202,44,380,235
        REG  L21 DRIFLOON (GHOST/FLYING): 314,351,318,237
        REG  L36 MURKROW (DARK/FLYING): 372,211,373,355
        REG  L39 MURKROW (DARK/FLYING): 399,257,94,310
        REG  L58 PELIPPER (WATER/FLYING): 362,59,45,263
        REG  L32 EEVEE (NORMAL): 98,44,281,231
        REG  L48 SEAKING (WATER): 127,59,97,218
        REG  L42 GOLBAT (POISON/FLYING): 403,247,188,95
        REG  L23 BUIZEL (WATER): 362,280,216,317
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,161,48,393
        REG  L56 EMPOLEON (WATER/STEEL): 56,430,54,421
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,157,468,366
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 500,257,355,285
        BOSS L72 Lucario (FIGHTING/STEEL): 396,299,157,406
        BOSS L28 Flaaffy (ELECTRIC): 521,324,215,268
        BOSS L48 Haxorus (DRAGON): 406,401,43,398
        BOSS L50 Cofagrigus (GHOST): 466,399,114,212
        BOSS L67 Simipour (WATER): 401,512,92,207
        BOSS L76 Clefable (NORMAL): 387,282,115,383
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,369,182,148
        BOSS L49 Carracosta (WATER/ROCK): 453,479,92,446
        BOSS L56 Lucario (FIGHTING/STEEL): 136,421,197,398
        BOSS L73 Golurk (GROUND/GHOST): 89,409,182,218
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 409,348,156,472
        BOSS L75 Arcanine (FIRE): 126,528,97,261
        BOSS L75 Glaceon (ICE): 196,324,226,207
        IMP  L8 Tepig (FIRE): 488,343,269,46
        IMP  L48 Cryogonal (ICE): 58,324,277,54
        IMP  L23 Pansage (GRASS): 202,512,468,259
        IMP  L31 Tranquill (NORMAL/FLYING): 98,369,366,218
        IMP  L39 Unfezant (NORMAL/FLYING): 416,211,234,297
        IMP  L46 Cryogonal (ICE): 62,263,114,113
        IMP  L55 Unfezant (NORMAL/FLYING): 143,257,164,211
        IMP  L55 Simisear (FIRE): 315,280,261,269
        IMP  L62 Unfezant (NORMAL/FLYING): 98,143,197,211
        IMP  L62 Flygon (GROUND/DRAGON): 200,89,355,28
        IMP  L65 Unfezant (NORMAL/FLYING): 98,369,234,516
        IMP  L65 Eelektross (ELECTRIC): 528,7,489,242
        IMP  L41 Simisear (FIRE): 315,280,281,253
        IMP  L48 Unfezant (NORMAL/FLYING): 98,211,234,297
        IMP  L74 Klinklang (STEEL): 544,85,508,319
        REG  L26 Blitzle (ELECTRIC): 351,228,24,496
        REG  L63 Hitmonlee (FIGHTING): 26,418,398,203
        REG  L63 Hitmonchan (FIGHTING): 183,7,444,92
        REG  L56 Unfezant (NORMAL/FLYING): 98,211,234,297
        REG  L47 Boldore (ROCK): 408,89,199,164
        REG  L45 Swinub (ICE/GROUND): 556,157,164,316
        REG  L32 Scolipede (BUG/POISON): 404,276,398,431
        REG  L65 Hitmontop (FIGHTING): 183,228,418,197
        REG  L52 Amoonguss (GRASS/POISON): 499,492,412,77
        REG  L64 Archeops (ROCK/FLYING): 457,231,92,98
        REG  L54 Metang (STEEL/PSYCHIC): 309,89,356,357
        REG  L60 Wooper (WATER/GROUND): 401,89,156,58
        REG  L67 Emboar (FIRE/FIGHTING): 280,394,46,528
        REG  L47 Krookodile (GROUND/DARK): 242,276,373,201
        REG  L25 Litwick (GHOST/FIRE): 481,499,219,107
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,422,46,184
        BOSS L41 Weezing (POISON): 499,53,114,63
        BOSS L5 Zigzagoon (NORMAL): 496,168,468,86
        BOSS L51 Dusclops (GHOST): 247,228,114,174
        BOSS L52 Froslass (ICE/GHOST): 466,358,113,86
        BOSS L57 Claydol (GROUND/PSYCHIC): 428,414,347,247
        BOSS L14 Machop (FIGHTING): 490,418,113,272
        BOSS L28 Slaking (NORMAL): 498,359,303,339
        BOSS L44 Whiscash (WATER/GROUND): 414,444,349,321
        BOSS L70 Sharpedo (WATER/DARK): 242,58,97,259
        BOSS L71 Dusknoir (GHOST): 325,89,262,114
        BOSS L73 Altaria (DRAGON/FLYING): 365,76,366,355
        BOSS L77 Carbink (ROCK/FAIRY): 479,94,182,159
        BOSS L57 Cradily (ROCK/GRASS): 412,362,397,112
        BOSS L57 Milotic (WATER): 503,406,156,113
        IMP  L18 Slugma (FIRE): 510,496,261,267
        IMP  L31 Wailmer (WATER): 503,499,164,218
        IMP  L18 Wailmer (WATER): 503,499,182,174
        IMP  L31 Shroomish (GRASS): 412,188,313,29
        IMP  L37 Swellow (NORMAL/FLYING): 413,211,432,63
        IMP  L37 Wailord (WATER): 323,499,174,428
        IMP  L46 Delcatty (NORMAL): 304,231,215,213
        IMP  L24 Shroomish (GRASS): 402,409,156,92
        IMP  L24 Slugma (FIRE): 510,499,174,113
        IMP  L32 Sharpedo (WATER/DARK): 399,89,92,398
        IMP  L55 Camerupt (FIRE/GROUND): 284,430,174,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 257,413,226,261
        IMP  L50 Sceptile (GRASS): 437,409,320,9
        IMP  L64 Altaria (DRAGON/FLYING): 406,211,468,119
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 280,425,227,212
        REG  L4 Zigzagoon (NORMAL): 496,196,493,207
        REG  L25 Slugma (FIRE): 488,499,115,334
        REG  L39 Claydol (GROUND/PSYCHIC): 94,89,379,115
        REG  L36 Golbat (POISON/FLYING): 188,314,202,366
        REG  L34 Golbat (POISON/FLYING): 413,162,188,211
        REG  L33 Roselia (GRASS/POISON): 437,326,42,346
        REG  L43 Solrock (ROCK/PSYCHIC): 444,512,472,218
        REG  L49 Jellicent (WATER/GHOST): 466,482,54,151
        REG  L37 Skarmory (STEEL/FLYING): 232,228,104,404
        REG  L41 Clamperl (WATER): 503,34,300,287
        REG  L39 Tentacruel (WATER/POISON): 61,62,48,112
        REG  L48 Honchkrow (DARK/FLYING): 492,276,195,109
        REG  L53 Flygon (GROUND/DRAGON): 89,231,590,48
        REG  L23 Grimer (POISON): 398,157,374,189
        REG  L51 Mightyena (DARK): 492,583,231,336
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 490,418,339,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 38,421,339,175
        BOSS L56 Probopass (ROCK/STEEL): 408,192,269,218
        BOSS L41 Golisopod (BUG/WATER): 141,442,156,180
        BOSS L66 Froslass (ICE/GHOST): 466,358,191,694
        BOSS L66 Mandibuzz (DARK/FLYING): 413,198,366,212
        BOSS L57 Dugtrio (GROUND/STEEL): 89,157,262,232
        BOSS L52 Sableye (DARK/GHOST): 425,605,236,399
        BOSS L65 Crobat (POISON/FLYING): 403,141,18,253
        BOSS L64 Masquerain (BUG/FLYING): 318,503,564,60
        BOSS L66 Hydreigon (DARK/DRAGON): 675,369,213,34
        BOSS L65 Gyarados (WATER/FLYING): 127,423,92,259
        BOSS L64 Camerupt (FIRE/GROUND): 89,430,261,241
        BOSS L70 Mewtwo (PSYCHIC): 427,411,339,356
        BOSS L63 Crabominable (FIGHTING/ICE): 665,146,164,104
        IMP  L6 Pichu (ELECTRIC): 527,237,227,113
        IMP  L15 Glaceon (ICE): 196,500,156,313
        IMP  L27 Salandit (POISON/FIRE): 474,481,92,230
        IMP  L28 Noibat (FLYING/DRAGON): 314,399,415,216
        IMP  L41 Noivern (FLYING/DRAGON): 314,411,432,586
        IMP  L70 Primarina (WATER/FAIRY): 664,412,227,216
        IMP  L67 Muk (POISON/DARK): 482,280,262,374
        IMP  L53 Zoroark (DARK): 399,326,269,340
        IMP  L68 Zoroark (DARK): 492,369,97,63
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 87,98,277,321
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 85,263,277,324
        IMP  L68 Snorlax (NORMAL): 387,402,281,7
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 85,231,417,347
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,188,236,74
        IMP  L20 Poipole (POISON): 398,406,182,380
        REG  L6 Yungoos (NORMAL): 162,351,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 162,279,43,164
        REG  L69 Lapras (WATER/ICE): 56,324,54,47
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 94,280,231,321
        REG  L35 Marowak (FIRE/GHOST): 708,675,411,89
        REG  L5 Yungoos (NORMAL): 162,168,279,590
        REG  L5 Yungoos (NORMAL): 162,279,164,526
        REG  L55 Espeon (PSYCHIC): 500,247,197,478
        REG  L5 Yungoos (NORMAL): 162,168,351,269
        REG  L33 Zubat (POISON/FLYING): 305,141,599,413
        REG  L30 Minior (ROCK/FLYING): 444,428,477,129
        REG  L27 Trumbeak (NORMAL/FLYING): 332,369,182,119
        REG  L62 Persian (NORMAL): 252,421,133,274
        REG  L14 Rattata (DARK/NORMAL): 168,279,447,98
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
        appendTier(sb, romHandler, "IMPORTANT", Trainer::isImportant);
        appendTier(sb, romHandler, "REGULAR", Trainer::isRegular);
        return sb.toString().strip();
    }

    /** One holder so a picked mon carries its owning trainer (for the tier tag) alongside the Pokemon. */
    private record TierMon(Trainer trainer, TrainerPokemon pokemon) {}

    /**
     * Appends an even spread of {@link #SAMPLE_PER_TIER} of {@code inTier}'s buffed trainer Pokemon to {@code sb}.
     * Only trainers the randomizer actually touches are considered ({@code inTier} holds AND
     * {@link Trainer#shouldNotGetBuffs()} is false). An empty tier is recorded with an explicit {@code (none)} marker
     * so its absence is frozen rather than silently blank.
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
            sb.append(tierOf(m.trainer())).append(" L").append(m.pokemon().getLevel()).append(' ')
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

    private static String tierOf(Trainer tr) {
        if (tr.isBoss()) {
            return "BOSS";
        }
        if (tr.isImportant()) {
            return "IMP ";
        }
        return "REG ";
    }
}
