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
                {"Red", "Red (U)"},
                {"Crystal", "Crystal (U)"},
                {"Emerald", "Emerald (U)"},
                {"Platinum", "Platinum (U)"},
                {"Black 2", "Black 2 (U)"},
                {"Alpha Sapphire", "Alpha Sapphire"},
                {"Ultra Sun", "Ultra Sun"},
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
        BOSS L55 HITMONLEE (FIGHTING): 136,70,156,96
        BOSS L12 GEODUDE (ROCK/GROUND): 91,69,164,118
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,94,86,129
        BOSS L24 RAICHU (ELECTRIC): 34,66,164,104
        BOSS L37 KOFFING (POISON): 124,85,92,108
        BOSS L43 WEEZING (POISON): 124,120,156,126
        BOSS L42 RAPIDASH (FIRE): 126,36,115,39
        BOSS L38 VENOMOTH (BUG/POISON): 60,72,78,104
        BOSS L53 CLOYSTER (WATER/ICE): 57,131,164,62
        BOSS L56 LAPRAS (WATER/ICE): 57,85,115,54
        BOSS L55 HAUNTER (GHOST/POISON): 101,72,92,85
        BOSS L56 DRAGONAIR (DRAGON): 59,34,86,126
        BOSS L62 DRAGONITE (DRAGON/FLYING): 129,57,115,59
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,19,18,102
        IMP  L15 ABRA (PSYCHIC): 94,69,86,148
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 19,98,92,28
        IMP  L18 KADABRA (PSYCHIC): 69,93,86,118
        IMP  L16 RATICATE (NORMAL): 158,61,156,39
        IMP  L25 WARTORTLE (WATER): 61,44,164,66
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 94,140,92,156
        IMP  L35 ALAKAZAM (PSYCHIC): 60,5,92,91
        IMP  L40 VENUSAUR (GRASS/POISON): 72,38,73,115
        IMP  L45 RHYHORN (GROUND/ROCK): 91,157,164,87
        IMP  L45 GYARADOS (WATER/FLYING): 56,59,115,87
        IMP  L47 GYARADOS (WATER/FLYING): 57,34,156,43
        IMP  L61 ARCANINE (FIRE): 53,91,97,46
        IMP  L63 ARCANINE (FIRE): 53,91,97,129
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,69,115,36
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
        BOSS L7 PIDGEY (NORMAL/FLYING): 19,129,197,168
        BOSS L16 SCYTHER (BUG/FLYING): 15,211,228,168
        BOSS L31 PILOSWINE (ICE/GROUND): 58,246,156,34
        BOSS L37 DRAGONAIR (DRAGON): 225,53,97,29
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,246,73,121
        BOSS L46 MACHAMP (FIGHTING): 238,89,227,168
        BOSS L40 ARIADOS (BUG/POISON): 188,94,184,91
        BOSS L47 DRAGONITE (DRAGON/FLYING): 19,211,86,70
        BOSS L42 OMASTAR (ROCK/WATER): 57,59,114,201
        BOSS L47 STARMIE (WATER/PSYCHIC): 127,196,240,229
        BOSS L41 JUMPLUFF (GRASS/FLYING): 72,38,182,77
        BOSS L33 ARIADOS (BUG/POISON): 188,101,50,91
        BOSS L45 MAGMAR (FIRE): 7,9,92,207
        BOSS L77 BLASTOISE (WATER): 56,59,46,91
        BOSS L58 ARCANINE (FIRE): 126,225,219,34
        IMP  L12 GASTLY (GHOST/POISON): 168,202,174,182
        IMP  L12 GASTLY (GHOST/POISON): 168,95,149,0
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,168
        IMP  L20 ZUBAT (POISON/FLYING): 44,211,129,18
        IMP  L32 MEGANIUM (GRASS): 202,246,73,34
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,129,86,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,168,156,202
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,92,202
        IMP  L43 GENGAR (GHOST/POISON): 247,7,174,70
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,91,112
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,182,29
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,92,91
        IMP  L50 TYPHLOSION (FIRE): 7,66,174,207
        IMP  L50 FERALIGATR (WATER): 56,13,156,242
        REG  L10 CHIKORITA (GRASS): 75,246,197,33
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,250,237
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 16,168,182,193
        REG  L25 NINETALES (FIRE): 52,185,46,207
        REG  L31 RHYDON (GROUND/ROCK): 89,157,223,31
        REG  L18 GROWLITHE (FIRE): 52,91,213,44
        REG  L23 GOLDEEN (WATER): 127,64,182,203
        REG  L28 TENTACOOL (WATER/POISON): 61,188,216,219
        REG  L28 POLIWHIRL (WATER): 61,3,218,203
        REG  L32 ONIX (ROCK/GROUND): 89,249,203,104
        REG  L6 VOLTORB (ELECTRIC): 129,205,207,182
        REG  L31 FURRET (NORMAL): 29,91,197,116
        REG  L42 GOLDUCK (WATER): 57,60,103,154
        REG  L23 PIKACHU (ELECTRIC): 84,205,70,86
        REG  L25 ELECTRODE (ELECTRIC): 33,205,207,104
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,247,184,91
        BOSS L53 WALREIN (ICE/WATER): 57,157,281,196
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,317,182,92
        BOSS L43 SEALEO (ICE/WATER): 59,89,182,352
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,157,182,23
        BOSS L50 KABUTOPS (ROCK/WATER): 127,280,164,157
        BOSS L46 HITMONCHAN (FIGHTING): 327,89,97,102
        BOSS L50 MANECTRIC (ELECTRIC): 85,168,174,70
        BOSS L46 GROWLITHE (FIRE): 257,91,97,92
        BOSS L45 KANGASKHAN (NORMAL): 70,126,50,219
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,126,219,58
        BOSS L58 SKARMORY (STEEL/FLYING): 65,129,164,207
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,263,115,201
        BOSS L56 LAPRAS (WATER/ICE): 57,85,109,193
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,184,9
        IMP  L34 MIGHTYENA (DARK): 44,36,184,91
        IMP  L40 GOLBAT (POISON/FLYING): 188,290,109,17
        IMP  L20 GROVYLE (GRASS): 202,225,156,98
        IMP  L29 LOMBRE (WATER/GRASS): 57,196,235,168
        IMP  L18 SLUGMA (FIRE): 52,157,281,151
        IMP  L29 PELIPPER (WATER/FLYING): 57,168,156,290
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,196,57,69
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,18,228
        IMP  L47 ROSELIA (GRASS/POISON): 72,247,178,188
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,89,19,213
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,290,182,199
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 280,263,241,213
        IMP  L34 GROVYLE (GRASS): 348,332,73,225
        IMP  L34 MARSHTOMP (WATER/GROUND): 91,23,291,218
        IMP  L15 MUDKIP (WATER): 291,91,156,287
        REG  L21 GEODUDE (ROCK/GROUND): 88,290,335,201
        REG  L26 MARILL (WATER): 61,5,205,156
        REG  L26 MIGHTYENA (DARK): 44,91,156,316
        REG  L33 MACHOP (FIGHTING): 2,89,104,317
        REG  L41 SOLROCK (ROCK/PSYCHIC): 205,247,53,218
        REG  L35 PLUSLE (ELECTRIC): 9,34,216,45
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,129,216,319
        REG  L30 KOFFING (POISON): 188,205,261,149
        REG  L6 SEEDOT (GRASS): 331,205,249,218
        REG  L11 MARILL (WATER): 145,205,204,156
        REG  L26 LOMBRE (WATER/GRASS): 331,280,148,291
        REG  L29 XATU (PSYCHIC/FLYING): 94,168,297,290
        REG  L29 ZUBAT (POISON/FLYING): 17,188,207,168
        REG  L34 PELIPPER (WATER/FLYING): 16,168,98,45
        REG  L5 KYOGRE (WATER): 352,351,317,102
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 246,249,446,335
        BOSS L49 SCIZOR (BUG/STEEL): 430,228,355,410
        BOSS L52 HIPPOWDON (GROUND): 414,317,281,276
        BOSS L57 MAGMORTAR (FIRE): 315,85,156,183
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,466,220,164
        BOSS L20 CHERRIM (GRASS): 345,290,164,312
        BOSS L29 MACHOKE (FIGHTING): 27,8,339,418
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,157,156,420
        BOSS L44 SNEASEL (DARK/ICE): 8,306,156,404
        BOSS L48 WEAVILE (DARK/ICE): 8,279,115,252
        BOSS L66 WHISCASH (WATER/GROUND): 57,340,182,196
        BOSS L69 RAPIDASH (FIRE): 315,224,164,45
        BOSS L72 ALAKAZAM (PSYCHIC): 94,324,168,112
        BOSS L78 GARCHOMP (DRAGON/GROUND): 337,424,201,46
        BOSS L58 MAGMORTAR (FIRE): 315,85,241,89
        IMP  L7 STARLY (NORMAL/FLYING): 365,228,92,297
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,246,164,430
        IMP  L27 GROTLE (GRASS): 402,70,219,174
        IMP  L34 STARAVIA (NORMAL/FLYING): 17,211,297,257
        IMP  L36 STARAPTOR (NORMAL/FLYING): 290,211,297,19
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,400,339,70
        IMP  L47 RAPIDASH (FIRE): 394,224,97,290
        IMP  L42 STARAPTOR (NORMAL/FLYING): 19,263,432,369
        IMP  L25 KADABRA (PSYCHIC): 60,7,86,134
        IMP  L27 GROTLE (GRASS): 412,431,133,321
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,282,339,332
        IMP  L69 RAPIDASH (FIRE): 394,340,224,207
        IMP  L73 SNORLAX (NORMAL): 38,89,85,317
        IMP  L83 SNORLAX (NORMAL): 431,228,204,200
        IMP  L60 SKUNTANK (POISON/DARK): 398,91,184,116
        REG  L5 STARLY (NORMAL/FLYING): 365,228,310,432
        REG  L29 ZUBAT (POISON/FLYING): 19,168,212,269
        REG  L36 SWINUB (ICE/GROUND): 58,317,445,316
        REG  L6 GEODUDE (ROCK/GROUND): 205,33,175,335
        REG  L21 CARNIVINE (GRASS): 75,15,213,230
        REG  L21 DRIFLOON (GHOST/FLYING): 16,282,262,92
        REG  L36 MURKROW (DARK/FLYING): 399,332,114,355
        REG  L39 MURKROW (DARK/FLYING): 17,168,211,310
        REG  L58 PELIPPER (WATER/FLYING): 57,17,282,392
        REG  L32 EEVEE (NORMAL): 129,247,203,45
        REG  L48 SEAKING (WATER): 291,282,31,114
        REG  L42 GOLBAT (POISON/FLYING): 188,129,432,17
        REG  L23 BUIZEL (WATER): 291,280,228,164
        REG  L42 MAGNETON (ELECTRIC/STEEL): 209,430,216,199
        REG  L56 EMPOLEON (WATER/STEEL): 61,430,392,33
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 9,414,366,304
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 19,257,18,485
        BOSS L72 Lucario (FIGHTING/STEEL): 238,198,46,245
        BOSS L28 Flaaffy (ELECTRIC): 85,324,178,280
        BOSS L48 Haxorus (DRAGON): 200,523,14,203
        BOSS L50 Cofagrigus (GHOST): 247,94,261,114
        BOSS L67 Simipour (WATER): 127,276,182,270
        BOSS L76 Clefable (NORMAL): 304,85,86,473
        BOSS L28 Emolga (ELECTRIC/FLYING): 528,369,403,98
        BOSS L49 Carracosta (WATER/ROCK): 444,263,334,397
        BOSS L56 Lucario (FIGHTING/STEEL): 231,198,526,272
        BOSS L73 Golurk (GROUND/GHOST): 89,19,164,223
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,282,261,530
        BOSS L75 Arcanine (FIRE): 126,528,234,343
        BOSS L75 Glaceon (ICE): 58,324,164,273
        IMP  L8 Tepig (FIRE): 488,343,174,39
        IMP  L48 Cryogonal (ICE): 59,496,164,512
        IMP  L23 Pansage (GRASS): 331,490,526,43
        IMP  L31 Tranquill (NORMAL/FLYING): 19,211,366,197
        IMP  L39 Unfezant (NORMAL/FLYING): 19,211,366,98
        IMP  L46 Cryogonal (ICE): 59,496,114,218
        IMP  L55 Unfezant (NORMAL/FLYING): 19,211,526,516
        IMP  L55 Simisear (FIRE): 315,157,164,411
        IMP  L62 Unfezant (NORMAL/FLYING): 19,211,366,297
        IMP  L62 Flygon (GROUND/DRAGON): 200,276,366,49
        IMP  L65 Unfezant (NORMAL/FLYING): 143,211,297,234
        IMP  L65 Eelektross (ELECTRIC): 85,317,113,86
        IMP  L41 Simisear (FIRE): 257,276,156,281
        IMP  L48 Unfezant (NORMAL/FLYING): 19,211,366,98
        IMP  L74 Klinklang (STEEL): 430,528,86,324
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 280,228,193,116
        REG  L63 Hitmonchan (FIGHTING): 264,418,496,170
        REG  L56 Unfezant (NORMAL/FLYING): 416,19,273,526
        REG  L47 Boldore (ROCK): 408,89,36,237
        REG  L45 Swinub (ICE/GROUND): 556,38,341,115
        REG  L32 Scolipede (BUG/POISON): 224,40,216,334
        REG  L65 Hitmontop (FIGHTING): 136,229,418,444
        REG  L52 Amoonguss (GRASS/POISON): 412,492,34,474
        REG  L64 Archeops (ROCK/FLYING): 340,457,89,216
        REG  L54 Metang (STEEL/PSYCHIC): 309,228,94,247
        REG  L60 Wooper (WATER/GROUND): 89,196,482,114
        REG  L67 Emboar (FIRE/FIGHTING): 52,276,457,316
        REG  L47 Krookodile (GROUND/DARK): 328,479,510,263
        REG  L25 Litwick (GHOST/FIRE): 510,51,114,101
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,583,281,423
        BOSS L41 Weezing (POISON): 188,247,261,399
        BOSS L5 Zigzagoon (NORMAL): 496,228,182,204
        BOSS L51 Dusclops (GHOST): 247,228,269,196
        BOSS L52 Froslass (ICE/GHOST): 8,44,220,50
        BOSS L57 Claydol (GROUND/PSYCHIC): 529,444,397,229
        BOSS L14 Machop (FIGHTING): 27,418,227,523
        BOSS L28 Slaking (NORMAL): 514,490,164,351
        BOSS L44 Whiscash (WATER/GROUND): 414,209,37,291
        BOSS L70 Sharpedo (WATER/DARK): 127,423,46,428
        BOSS L71 Dusknoir (GHOST): 247,290,156,89
        BOSS L73 Altaria (DRAGON/FLYING): 19,89,47,585
        BOSS L77 Carbink (ROCK/FAIRY): 605,317,334,496
        BOSS L57 Cradily (ROCK/GRASS): 412,482,109,148
        BOSS L57 Milotic (WATER): 57,406,114,442
        IMP  L18 Slugma (FIRE): 510,237,334,104
        IMP  L31 Wailmer (WATER): 127,58,392,340
        IMP  L18 Wailmer (WATER): 352,499,174,156
        IMP  L31 Shroomish (GRASS): 402,358,78,237
        IMP  L37 Swellow (NORMAL/FLYING): 413,211,366,98
        IMP  L37 Wailord (WATER): 57,70,392,89
        IMP  L46 Delcatty (NORMAL): 514,185,219,193
        IMP  L24 Shroomish (GRASS): 331,29,78,409
        IMP  L24 Slugma (FIRE): 510,496,262,174
        IMP  L32 Sharpedo (WATER/DARK): 44,453,164,246
        IMP  L55 Camerupt (FIRE/GROUND): 89,53,164,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 136,317,174,282
        IMP  L50 Sceptile (GRASS): 331,332,242,98
        IMP  L64 Altaria (DRAGON/FLYING): 406,58,297,538
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 280,348,86,7
        REG  L4 Zigzagoon (NORMAL): 237,351,493,164
        REG  L25 Slugma (FIRE): 52,246,92,496
        REG  L39 Claydol (GROUND/PSYCHIC): 91,444,379,447
        REG  L36 Golbat (POISON/FLYING): 17,211,109,599
        REG  L34 Golbat (POISON/FLYING): 16,211,289,18
        REG  L33 Roselia (GRASS/POISON): 71,326,605,178
        REG  L43 Solrock (ROCK/PSYCHIC): 88,428,149,377
        REG  L49 Jellicent (WATER/GHOST): 503,506,164,180
        REG  L37 Skarmory (STEEL/FLYING): 507,157,43,14
        REG  L41 Clamperl (WATER): 352,496,109,392
        REG  L39 Tentacruel (WATER/POISON): 330,168,491,390
        REG  L48 Honchkrow (DARK/FLYING): 19,101,168,212
        REG  L53 Flygon (GROUND/DRAGON): 89,276,406,369
        REG  L23 Grimer (POISON): 398,325,611,510
        REG  L51 Mightyena (DARK): 44,310,281,290
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 27,479,523,418
        BOSS L47 Bewear (NORMAL/FIGHTING): 359,89,46,220
        BOSS L56 Probopass (ROCK/STEEL): 408,414,397,106
        BOSS L41 Golisopod (BUG/WATER): 42,332,156,453
        BOSS L66 Froslass (ICE/GHOST): 8,324,86,335
        BOSS L66 Mandibuzz (DARK/FLYING): 399,19,366,198
        BOSS L57 Dugtrio (GROUND/STEEL): 89,444,182,28
        BOSS L52 Sableye (DARK/GHOST): 247,324,347,252
        BOSS L65 Crobat (POISON/FLYING): 413,141,18,98
        BOSS L64 Masquerain (BUG/FLYING): 679,466,78,56
        BOSS L66 Hydreigon (DARK/DRAGON): 242,451,366,355
        BOSS L65 Gyarados (WATER/FLYING): 127,523,269,525
        BOSS L64 Camerupt (FIRE/GROUND): 315,157,182,36
        BOSS L70 Mewtwo (PSYCHIC): 473,237,277,385
        BOSS L63 Crabominable (FIGHTING/ICE): 665,276,182,228
        IMP  L6 Pichu (ELECTRIC): 351,237,273,604
        IMP  L15 Glaceon (ICE): 524,237,182,216
        IMP  L27 Salandit (POISON/FIRE): 188,53,269,168
        IMP  L28 Noibat (FLYING/DRAGON): 406,399,366,103
        IMP  L41 Noivern (FLYING/DRAGON): 19,399,92,162
        IMP  L70 Primarina (WATER/FAIRY): 664,585,47,263
        IMP  L67 Muk (POISON/DARK): 242,612,184,202
        IMP  L53 Zoroark (DARK): 539,369,97,421
        IMP  L68 Zoroark (DARK): 399,490,184,164
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 9,324,86,381
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,252,204,683
        IMP  L68 Snorlax (NORMAL): 34,276,174,242
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 9,324,94,213
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,324,78,402
        IMP  L20 Poipole (POISON): 474,406,156,496
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 33,0,0,0
        REG  L5 Yungoos (NORMAL): 33,279,351,207
        REG  L69 Lapras (WATER/ICE): 58,442,193,352
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 473,282,115,204
        REG  L35 Marowak (FIRE/GHOST): 53,708,103,155
        REG  L5 Yungoos (NORMAL): 497,279,104,526
        REG  L5 Yungoos (NORMAL): 33,279,259,216
        REG  L55 Espeon (PSYCHIC): 473,304,376,44
        REG  L5 Yungoos (NORMAL): 497,317,259,351
        REG  L33 Zubat (POISON/FLYING): 16,369,474,174
        REG  L30 Minior (ROCK/FLYING): 444,428,356,446
        REG  L27 Trumbeak (NORMAL/FLYING): 64,249,168,45
        REG  L62 Persian (NORMAL): 497,44,415,39
        REG  L14 Rattata (DARK/NORMAL): 33,228,351,590
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
