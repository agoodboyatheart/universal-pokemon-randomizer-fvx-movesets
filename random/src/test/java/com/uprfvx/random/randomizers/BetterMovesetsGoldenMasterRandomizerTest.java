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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,23,156,157
        BOSS L45 NIDOKING (POISON/GROUND): 89,30,115,58
        BOSS L55 HITMONLEE (FIGHTING): 136,129,156,96
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,164,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,129,156,104
        BOSS L24 RAICHU (ELECTRIC): 84,98,164,69
        BOSS L37 KOFFING (POISON): 123,85,92,33
        BOSS L43 WEEZING (POISON): 123,33,156,126
        BOSS L42 RAPIDASH (FIRE): 126,36,115,39
        BOSS L38 VENOMOTH (BUG/POISON): 141,94,78,72
        BOSS L53 CLOYSTER (WATER/ICE): 57,131,164,62
        BOSS L56 LAPRAS (WATER/ICE): 57,85,115,54
        BOSS L55 HAUNTER (GHOST/POISON): 122,85,92,72
        BOSS L56 DRAGONAIR (DRAGON): 59,34,86,126
        BOSS L62 DRAGONITE (DRAGON/FLYING): 129,57,164,59
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,18,102
        IMP  L15 ABRA (PSYCHIC): 69,99,86,148
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 19,16,92,28
        IMP  L18 KADABRA (PSYCHIC): 93,99,50,86
        IMP  L16 RATICATE (NORMAL): 158,61,156,39
        IMP  L25 WARTORTLE (WATER): 61,44,164,66
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,92,156
        IMP  L35 ALAKAZAM (PSYCHIC): 60,34,105,104
        IMP  L40 VENUSAUR (GRASS/POISON): 75,15,73,115
        IMP  L45 RHYHORN (GROUND/ROCK): 91,157,164,87
        IMP  L45 GYARADOS (WATER/FLYING): 56,59,115,87
        IMP  L47 GYARADOS (WATER/FLYING): 57,34,156,43
        IMP  L61 ARCANINE (FIRE): 53,91,97,46
        IMP  L63 ARCANINE (FIRE): 53,91,97,129
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,69,115,34
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,18,28
        REG  L18 MANKEY (FIGHTING): 2,69,102,157
        REG  L19 RATTATA (NORMAL): 158,61,156,92
        REG  L37 VULPIX (FIRE): 53,91,129,156
        REG  L29 WEEZING (POISON): 123,85,102,156
        REG  L31 CLOYSTER (WATER/ICE): 128,38,164,110
        REG  L26 MANKEY (FIGHTING): 66,6,102,156
        REG  L30 HORSEA (WATER): 57,58,108,129
        REG  L29 FEAROW (NORMAL/FLYING): 65,31,119,45
        REG  L70 GYARADOS (WATER/FLYING): 57,85,70,115
        REG  L17 MACHOP (FIGHTING): 2,69,92,102
        REG  L28 EKANS (POISON): 40,44,157,72
        REG  L39 DUGTRIO (GROUND): 91,10,45,164
        REG  L33 HAUNTER (GHOST/POISON): 122,72,95,149
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,197,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,15,211,168
        BOSS L31 PILOSWINE (ICE/GROUND): 58,246,46,70
        BOSS L37 DRAGONAIR (DRAGON): 239,53,86,113
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,202,115,218
        BOSS L46 MACHAMP (FIGHTING): 238,89,197,70
        BOSS L40 ARIADOS (BUG/POISON): 188,101,156,60
        BOSS L47 DRAGONITE (DRAGON/FLYING): 19,211,114,196
        BOSS L42 OMASTAR (ROCK/WATER): 57,58,174,131
        BOSS L47 STARMIE (WATER/PSYCHIC): 127,58,105,106
        BOSS L41 JUMPLUFF (GRASS/FLYING): 72,93,78,216
        BOSS L33 ARIADOS (BUG/POISON): 188,101,50,228
        BOSS L45 MAGMAR (FIRE): 7,238,109,94
        BOSS L77 BLASTOISE (WATER): 56,29,46,114
        BOSS L58 ARCANINE (FIRE): 53,225,219,29
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,92
        IMP  L12 GASTLY (GHOST/POISON): 122,202,168,149
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,182
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,109,185
        IMP  L32 MEGANIUM (GRASS): 22,89,73,246
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,129,86,48
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,156,199
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,192,92
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,174,218
        IMP  L43 GENGAR (GHOST/POISON): 247,202,109,7
        IMP  L43 ALAKAZAM (PSYCHIC): 94,168,50,207
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,113,223
        IMP  L46 ALAKAZAM (PSYCHIC): 94,192,182,134
        IMP  L50 TYPHLOSION (FIRE): 53,205,92,91
        IMP  L50 FERALIGATR (WATER): 56,163,240,59
        REG  L10 CHIKORITA (GRASS): 75,246,45,175
        REG  L20 QUAGSIRE (WATER/GROUND): 189,246,55,8
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 129,185,18,104
        REG  L25 NINETALES (FIRE): 52,185,207,237
        REG  L31 RHYDON (GROUND/ROCK): 89,223,179,207
        REG  L18 GROWLITHE (FIRE): 52,44,92,29
        REG  L23 GOLDEEN (WATER): 127,60,30,213
        REG  L28 TENTACOOL (WATER/POISON): 57,196,207,156
        REG  L28 POLIWHIRL (WATER): 61,70,170,156
        REG  L32 ONIX (ROCK/GROUND): 88,249,33,213
        REG  L6 VOLTORB (ELECTRIC): 33,205,129,203
        REG  L31 FURRET (NORMAL): 70,247,179,116
        REG  L42 GOLDUCK (WATER): 127,8,50,213
        REG  L23 PIKACHU (ELECTRIC): 84,98,204,186
        REG  L25 ELECTRODE (ELECTRIC): 205,49,92,218
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,34,269,289
        BOSS L53 WALREIN (ICE/WATER): 58,352,156,157
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,317,92,222
        BOSS L43 SEALEO (ICE/WATER): 58,291,258,90
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,89,38,222
        BOSS L50 KABUTOPS (ROCK/WATER): 127,202,92,282
        BOSS L46 HITMONCHAN (FIGHTING): 136,157,197,229
        BOSS L50 MANECTRIC (ELECTRIC): 85,242,174,290
        BOSS L46 GROWLITHE (FIRE): 53,38,97,46
        BOSS L45 KANGASKHAN (NORMAL): 146,247,50,231
        BOSS L45 ALTARIA (DRAGON/FLYING): 19,211,46,182
        BOSS L58 SKARMORY (STEEL/FLYING): 65,129,191,207
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,89,95,149
        BOSS L56 LAPRAS (WATER/ICE): 58,94,92,85
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,157,156,203
        IMP  L34 MIGHTYENA (DARK): 44,36,184,91
        IMP  L40 GOLBAT (POISON/FLYING): 188,44,174,109
        IMP  L20 GROVYLE (GRASS): 331,9,156,182
        IMP  L29 LOMBRE (WATER/GRASS): 57,75,7,230
        IMP  L18 SLUGMA (FIRE): 52,157,182,111
        IMP  L29 PELIPPER (WATER/FLYING): 19,290,97,211
        IMP  L31 MARSHTOMP (WATER/GROUND): 291,5,174,196
        IMP  L22 ZUBAT (POISON/FLYING): 17,310,269,182
        IMP  L47 ROSELIA (GRASS/POISON): 188,290,235,320
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,126,97,263
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 87,161,156,205
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 7,157,156,91
        IMP  L34 GROVYLE (GRASS): 348,280,97,15
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,157,174,352
        IMP  L15 MUDKIP (WATER): 291,23,164,196
        REG  L21 GEODUDE (ROCK/GROUND): 88,290,335,201
        REG  L26 MARILL (WATER): 127,8,204,47
        REG  L26 MIGHTYENA (DARK): 44,91,237,289
        REG  L33 MACHOP (FIGHTING): 27,157,96,339
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,263,89,201
        REG  L35 PLUSLE (ELECTRIC): 209,98,268,182
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,205,218,115
        REG  L30 KOFFING (POISON): 188,168,218,261
        REG  L6 SEEDOT (GRASS): 331,91,106,133
        REG  L11 MARILL (WATER): 352,205,189,287
        REG  L26 LOMBRE (WATER/GRASS): 71,310,8,92
        REG  L29 XATU (PSYCHIC/FLYING): 94,65,297,207
        REG  L29 ZUBAT (POISON/FLYING): 17,211,289,202
        REG  L34 PELIPPER (WATER/FLYING): 17,168,58,254
        REG  L5 KYOGRE (WATER): 352,351,111,347
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,249,156,335
        BOSS L49 SCIZOR (BUG/STEEL): 442,168,14,432
        BOSS L52 HIPPOWDON (GROUND): 89,242,303,352
        BOSS L57 MAGMORTAR (FIRE): 436,94,261,5
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,290,262,352
        BOSS L20 CHERRIM (GRASS): 345,205,241,235
        BOSS L29 MACHOKE (FIGHTING): 280,9,227,371
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,70,182,320
        BOSS L44 SNEASEL (DARK/ICE): 8,57,115,247
        BOSS L48 WEAVILE (DARK/ICE): 400,420,97,10
        BOSS L66 WHISCASH (WATER/GROUND): 56,58,133,222
        BOSS L69 RAPIDASH (FIRE): 394,263,261,224
        BOSS L72 ALAKAZAM (PSYCHIC): 428,264,115,247
        BOSS L78 GARCHOMP (DRAGON/GROUND): 414,290,182,337
        BOSS L58 MAGMORTAR (FIRE): 257,238,241,270
        IMP  L7 STARLY (NORMAL/FLYING): 31,168,297,365
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 93,430,219,185
        IMP  L27 GROTLE (GRASS): 75,34,156,44
        IMP  L34 STARAVIA (NORMAL/FLYING): 365,211,18,97
        IMP  L36 STARAPTOR (NORMAL/FLYING): 19,290,18,370
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,421,339,290
        IMP  L47 RAPIDASH (FIRE): 394,24,204,237
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,168,92,369
        IMP  L25 KADABRA (PSYCHIC): 94,324,50,168
        IMP  L27 GROTLE (GRASS): 75,15,174,148
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,157,164,175
        IMP  L69 RAPIDASH (FIRE): 315,224,95,97
        IMP  L73 SNORLAX (NORMAL): 431,442,164,85
        IMP  L83 SNORLAX (NORMAL): 34,264,57,428
        IMP  L60 SKUNTANK (POISON/DARK): 398,249,184,154
        REG  L5 STARLY (NORMAL/FLYING): 365,228,310,432
        REG  L29 ZUBAT (POISON/FLYING): 19,168,212,269
        REG  L36 SWINUB (ICE/GROUND): 89,44,70,246
        REG  L6 GEODUDE (ROCK/GROUND): 205,249,175,360
        REG  L21 CARNIVINE (GRASS): 402,371,275,164
        REG  L21 DRIFLOON (GHOST/FLYING): 16,247,244,216
        REG  L36 MURKROW (DARK/FLYING): 371,314,375,297
        REG  L39 MURKROW (DARK/FLYING): 19,263,269,466
        REG  L58 PELIPPER (WATER/FLYING): 16,168,362,445
        REG  L32 EEVEE (NORMAL): 290,231,273,182
        REG  L48 SEAKING (WATER): 127,64,196,213
        REG  L42 GOLBAT (POISON/FLYING): 413,44,213,257
        REG  L23 BUIZEL (WATER): 55,8,70,249
        REG  L42 MAGNETON (ELECTRIC/STEEL): 87,430,199,319
        REG  L56 EMPOLEON (WATER/STEEL): 56,58,65,324
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 9,414,366,304
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 314,257,322,430
        BOSS L72 Lucario (FIGHTING/STEEL): 231,421,334,299
        BOSS L28 Flaaffy (ELECTRIC): 85,324,178,34
        BOSS L48 Haxorus (DRAGON): 200,411,523,371
        BOSS L50 Cofagrigus (GHOST): 247,94,277,347
        BOSS L67 Simipour (WATER): 127,441,240,276
        BOSS L76 Clefable (NORMAL): 70,309,47,340
        BOSS L28 Emolga (ELECTRIC/FLYING): 85,15,366,324
        BOSS L49 Carracosta (WATER/ROCK): 503,444,442,92
        BOSS L56 Lucario (FIGHTING/STEEL): 430,198,156,410
        BOSS L73 Golurk (GROUND/GHOST): 89,263,397,490
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,282,262,304
        BOSS L75 Arcanine (FIRE): 315,24,234,528
        BOSS L75 Glaceon (ICE): 59,485,197,203
        IMP  L8 Tepig (FIRE): 52,33,261,360
        IMP  L48 Cryogonal (ICE): 59,324,163,164
        IMP  L23 Pansage (GRASS): 331,343,526,282
        IMP  L31 Tranquill (NORMAL/FLYING): 365,211,92,237
        IMP  L39 Unfezant (NORMAL/FLYING): 19,211,95,297
        IMP  L46 Cryogonal (ICE): 58,430,151,398
        IMP  L55 Unfezant (NORMAL/FLYING): 19,369,182,211
        IMP  L55 Simisear (FIRE): 257,91,421,67
        IMP  L62 Unfezant (NORMAL/FLYING): 19,13,366,43
        IMP  L62 Flygon (GROUND/DRAGON): 89,200,156,49
        IMP  L65 Unfezant (NORMAL/FLYING): 19,211,95,297
        IMP  L65 Eelektross (ELECTRIC): 528,29,489,207
        IMP  L41 Simisear (FIRE): 53,490,133,15
        IMP  L48 Unfezant (NORMAL/FLYING): 19,211,355,516
        IMP  L74 Klinklang (STEEL): 430,528,156,86
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 276,418,523,193
        REG  L63 Hitmonchan (FIGHTING): 264,418,170,218
        REG  L56 Unfezant (NORMAL/FLYING): 19,211,369,516
        REG  L47 Boldore (ROCK): 444,414,356,446
        REG  L45 Swinub (ICE/GROUND): 556,317,426,249
        REG  L32 Scolipede (BUG/POISON): 342,224,15,191
        REG  L65 Hitmontop (FIGHTING): 136,157,33,89
        REG  L52 Amoonguss (GRASS/POISON): 71,492,499,263
        REG  L64 Archeops (ROCK/FLYING): 17,88,216,369
        REG  L54 Metang (STEEL/PSYCHIC): 309,228,182,332
        REG  L60 Wooper (WATER/GROUND): 291,89,231,213
        REG  L67 Emboar (FIRE/FIGHTING): 292,53,372,528
        REG  L47 Krookodile (GROUND/DARK): 492,401,490,328
        REG  L25 Litwick (GHOST/FIRE): 52,123,373,182
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,91,184,162
        BOSS L41 Weezing (POISON): 188,247,174,458
        BOSS L5 Zigzagoon (NORMAL): 497,168,86,196
        BOSS L51 Dusclops (GHOST): 247,451,50,7
        BOSS L52 Froslass (ICE/GHOST): 247,358,269,524
        BOSS L57 Claydol (GROUND/PSYCHIC): 529,246,113,472
        BOSS L14 Machop (FIGHTING): 27,371,227,418
        BOSS L28 Slaking (NORMAL): 498,421,281,53
        BOSS L44 Whiscash (WATER/GROUND): 56,209,201,414
        BOSS L70 Sharpedo (WATER/DARK): 291,36,164,58
        BOSS L71 Dusknoir (GHOST): 247,264,262,212
        BOSS L73 Altaria (DRAGON/FLYING): 19,257,47,585
        BOSS L77 Carbink (ROCK/FAIRY): 408,33,334,94
        BOSS L57 Cradily (ROCK/GRASS): 444,72,105,362
        BOSS L57 Milotic (WATER): 503,406,114,442
        IMP  L18 Slugma (FIRE): 510,496,262,88
        IMP  L31 Wailmer (WATER): 503,499,392,216
        IMP  L18 Wailmer (WATER): 352,196,392,290
        IMP  L31 Shroomish (GRASS): 412,358,77,33
        IMP  L37 Swellow (NORMAL/FLYING): 19,228,432,287
        IMP  L37 Wailord (WATER): 323,34,174,499
        IMP  L46 Delcatty (NORMAL): 514,451,47,322
        IMP  L24 Shroomish (GRASS): 402,358,219,263
        IMP  L24 Slugma (FIRE): 510,246,220,254
        IMP  L32 Sharpedo (WATER/DARK): 400,89,184,162
        IMP  L55 Camerupt (FIRE/GROUND): 126,414,446,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 53,10,182,91
        IMP  L50 Sceptile (GRASS): 348,490,46,501
        IMP  L64 Altaria (DRAGON/FLYING): 406,126,182,290
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 428,7,109,345
        REG  L4 Zigzagoon (NORMAL): 237,351,493,164
        REG  L25 Slugma (FIRE): 52,499,106,220
        REG  L39 Claydol (GROUND/PSYCHIC): 89,60,322,247
        REG  L36 Golbat (POISON/FLYING): 314,369,104,228
        REG  L34 Golbat (POISON/FLYING): 188,332,92,103
        REG  L33 Roselia (GRASS/POISON): 345,326,398,182
        REG  L43 Solrock (ROCK/PSYCHIC): 88,315,373,322
        REG  L49 Jellicent (WATER/GHOST): 57,101,196,54
        REG  L37 Skarmory (STEEL/FLYING): 65,290,385,232
        REG  L41 Clamperl (WATER): 55,58,109,34
        REG  L39 Tentacruel (WATER/POISON): 330,40,392,229
        REG  L48 Honchkrow (DARK/FLYING): 64,372,101,257
        REG  L53 Flygon (GROUND/DRAGON): 337,276,242,586
        REG  L23 Grimer (POISON): 398,8,325,207
        REG  L51 Mightyena (DARK): 371,305,290,180
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 27,418,156,479
        BOSS L47 Bewear (NORMAL/FIGHTING): 359,89,526,269
        BOSS L56 Probopass (ROCK/STEEL): 408,414,442,269
        BOSS L41 Golisopod (BUG/WATER): 534,530,191,249
        BOSS L66 Froslass (ICE/GHOST): 58,242,191,87
        BOSS L66 Mandibuzz (DARK/FLYING): 413,247,18,216
        BOSS L57 Dugtrio (GROUND/STEEL): 91,421,92,29
        BOSS L52 Sableye (DARK/GHOST): 492,264,220,428
        BOSS L65 Crobat (POISON/FLYING): 413,141,114,440
        BOSS L64 Masquerain (BUG/FLYING): 324,710,78,170
        BOSS L66 Hydreigon (DARK/DRAGON): 407,401,115,523
        BOSS L65 Gyarados (WATER/FLYING): 127,693,86,253
        BOSS L64 Camerupt (FIRE/GROUND): 284,707,397,38
        BOSS L70 Mewtwo (PSYCHIC): 473,264,164,317
        BOSS L63 Crabominable (FIGHTING/ICE): 665,146,133,157
        IMP  L6 Pichu (ELECTRIC): 84,497,273,604
        IMP  L15 Glaceon (ICE): 524,352,46,204
        IMP  L27 Salandit (POISON/FIRE): 481,237,92,337
        IMP  L28 Noibat (FLYING/DRAGON): 19,257,156,92
        IMP  L41 Noivern (FLYING/DRAGON): 406,352,236,314
        IMP  L70 Primarina (WATER/FAIRY): 585,401,227,581
        IMP  L67 Muk (POISON/DARK): 441,693,262,237
        IMP  L53 Zoroark (DARK): 539,326,156,421
        IMP  L68 Zoroark (DARK): 399,326,262,180
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 85,324,204,683
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,343,113,473
        IMP  L68 Snorlax (NORMAL): 34,200,133,402
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 87,574,277,237
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,202,668,478
        IMP  L20 Poipole (POISON): 51,406,204,497
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 496,279,269,92
        REG  L69 Lapras (WATER/ICE): 58,442,193,352
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 9,3,179,175
        REG  L35 Marowak (FIRE/GHOST): 708,707,59,444
        REG  L5 Yungoos (NORMAL): 33,279,371,216
        REG  L5 Yungoos (NORMAL): 497,168,43,269
        REG  L55 Espeon (PSYCHIC): 473,129,313,287
        REG  L5 Yungoos (NORMAL): 33,371,104,259
        REG  L33 Zubat (POISON/FLYING): 314,141,202,212
        REG  L30 Minior (ROCK/FLYING): 205,369,442,512
        REG  L27 Trumbeak (NORMAL/FLYING): 65,369,249,350
        REG  L62 Persian (NORMAL): 6,400,402,583
        REG  L14 Rattata (DARK/NORMAL): 44,196,154,156
        """);
    }

    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void betterMovesetsGoldenMaster(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        String actual = canonicalBlock(romHandler, gameName);

        if (Boolean.getBoolean("golden.record")) {
            printPasteReadyBlock(gameName, actual);
            return;
        }
        String expected = EXPECTED.get(gameName);
        if (expected == null) {
            printPasteReadyBlock(gameName, actual);
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
            return IntStream.range(0, size).toArray();
        }
        int[] idx = new int[sample];
        for (int i = 0; i < sample; i++) {
            idx[i] = (int) Math.round((double) i * (size - 1) / (sample - 1));
        }
        return idx;
    }

    private static String joinMoves(int[] moves) {
        return Arrays.stream(moves).mapToObj(Integer::toString).collect(Collectors.joining(","));
    }

    /** Prints the paste-ready {@code EXPECTED.put(...)} block for the re-bless workflow (see class doc). */
    private static void printPasteReadyBlock(String gameName, String actual) {
        System.out.println("\nEXPECTED.put(\"" + gameName + "\", \"\"\"\n" + actual + "\n\"\"\");");
    }

    private static String typeStr(Species pk) {
        Type t2 = pk.getSecondaryType(false);
        return pk.getPrimaryType(false) + (t2 == null ? "" : "/" + t2);
    }
}
