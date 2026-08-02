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
        BOSS L55 HITMONLEE (FIGHTING): 136,129,156,96
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,164,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,129,86,115
        BOSS L24 RAICHU (ELECTRIC): 84,98,164,69
        BOSS L37 KOFFING (POISON): 123,85,92,33
        BOSS L43 WEEZING (POISON): 123,33,156,126
        BOSS L42 RAPIDASH (FIRE): 126,36,115,39
        BOSS L38 VENOMOTH (BUG/POISON): 141,94,78,72
        BOSS L53 CLOYSTER (WATER/ICE): 57,131,164,62
        BOSS L56 LAPRAS (WATER/ICE): 57,85,115,54
        BOSS L55 HAUNTER (GHOST/POISON): 122,85,92,72
        BOSS L56 DRAGONAIR (DRAGON): 59,34,86,126
        BOSS L62 DRAGONITE (DRAGON/FLYING): 129,57,115,59
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,18,102
        IMP  L15 ABRA (PSYCHIC): 69,99,86,148
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 19,16,92,28
        IMP  L18 KADABRA (PSYCHIC): 93,99,86,118
        IMP  L16 RATICATE (NORMAL): 158,61,156,39
        IMP  L25 WARTORTLE (WATER): 61,44,164,66
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,92,156
        IMP  L35 ALAKAZAM (PSYCHIC): 60,34,92,91
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
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,197,168
        BOSS L16 SCYTHER (BUG/FLYING): 210,29,113,168
        BOSS L31 PILOSWINE (ICE/GROUND): 89,246,156,181
        BOSS L37 DRAGONAIR (DRAGON): 239,53,86,58
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,202,73,188
        BOSS L46 MACHAMP (FIGHTING): 238,89,184,70
        BOSS L40 ARIADOS (BUG/POISON): 188,91,60,168
        BOSS L47 DRAGONITE (DRAGON/FLYING): 19,211,114,196
        BOSS L42 OMASTAR (ROCK/WATER): 57,58,174,131
        BOSS L47 STARMIE (WATER/PSYCHIC): 127,58,105,229
        BOSS L41 JUMPLUFF (GRASS/FLYING): 72,29,73,203
        BOSS L33 ARIADOS (BUG/POISON): 188,91,184,202
        BOSS L45 MAGMAR (FIRE): 126,168,92,238
        BOSS L77 BLASTOISE (WATER): 56,223,46,58
        BOSS L58 ARCANINE (FIRE): 126,245,241,242
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,173
        IMP  L12 GASTLY (GHOST/POISON): 122,202,168,149
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,207
        IMP  L20 ZUBAT (POISON/FLYING): 16,141,18,98
        IMP  L32 MEGANIUM (GRASS): 22,89,73,246
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,129,174,205
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,199
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,156,202
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,174,218
        IMP  L43 GENGAR (GHOST/POISON): 247,94,182,8
        IMP  L43 ALAKAZAM (PSYCHIC): 94,168,50,105
        IMP  L43 ALAKAZAM (PSYCHIC): 94,168,50,8
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,113,156
        IMP  L50 TYPHLOSION (FIRE): 126,9,182,231
        IMP  L50 FERALIGATR (WATER): 57,37,92,91
        REG  L10 CHIKORITA (GRASS): 75,246,73,45
        REG  L20 QUAGSIRE (WATER/GROUND): 189,246,55,8
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 17,168,129,28
        REG  L25 NINETALES (FIRE): 52,185,207,237
        REG  L31 RHYDON (GROUND/ROCK): 89,223,179,207
        REG  L18 GROWLITHE (FIRE): 52,44,92,29
        REG  L23 GOLDEEN (WATER): 127,30,213,156
        REG  L28 TENTACOOL (WATER/POISON): 61,62,237,92
        REG  L28 POLIWHIRL (WATER): 145,249,54,111
        REG  L32 ONIX (ROCK/GROUND): 88,89,29,213
        REG  L6 VOLTORB (ELECTRIC): 129,205,156,237
        REG  L31 FURRET (NORMAL): 70,247,179,116
        REG  L42 GOLDUCK (WATER): 127,8,50,213
        REG  L23 PIKACHU (ELECTRIC): 84,98,204,186
        REG  L25 ELECTRODE (ELECTRIC): 205,49,92,218
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,34,184,289
        BOSS L53 WALREIN (ICE/WATER): 57,157,281,196
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,52,201,70
        BOSS L43 SEALEO (ICE/WATER): 59,89,182,55
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,157,182,23
        BOSS L50 KABUTOPS (ROCK/WATER): 127,280,164,157
        BOSS L46 HITMONCHAN (FIGHTING): 327,8,164,228
        BOSS L50 MANECTRIC (ELECTRIC): 85,242,92,98
        BOSS L46 GROWLITHE (FIRE): 257,91,97,92
        BOSS L45 KANGASKHAN (NORMAL): 70,317,50,219
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,47,19
        BOSS L58 SKARMORY (STEEL/FLYING): 65,228,46,28
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 88,247,219,157
        BOSS L56 LAPRAS (WATER/ICE): 56,138,47,174
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,184,36
        IMP  L34 MIGHTYENA (DARK): 44,36,184,91
        IMP  L40 GOLBAT (POISON/FLYING): 188,247,109,237
        IMP  L20 GROVYLE (GRASS): 71,225,156,98
        IMP  L29 LOMBRE (WATER/GRASS): 57,196,235,290
        IMP  L18 SLUGMA (FIRE): 52,157,281,151
        IMP  L29 PELIPPER (WATER/FLYING): 57,196,156,211
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,8,57,157
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,18,228
        IMP  L47 ROSELIA (GRASS/POISON): 72,188,178,247
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,182,34
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,38,182,199
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 280,332,241,53
        IMP  L34 GROVYLE (GRASS): 348,332,73,225
        IMP  L34 MARSHTOMP (WATER/GROUND): 91,23,291,58
        IMP  L15 MUDKIP (WATER): 291,91,156,253
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
        REG  L34 PELIPPER (WATER/FLYING): 16,168,240,263
        REG  L5 KYOGRE (WATER): 291,196,203,317
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,249,446,335
        BOSS L49 SCIZOR (BUG/STEEL): 430,98,355,201
        BOSS L52 HIPPOWDON (GROUND): 414,36,281,249
        BOSS L57 MAGMORTAR (FIRE): 315,85,156,183
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,466,220,196
        BOSS L20 CHERRIM (GRASS): 345,205,219,312
        BOSS L29 MACHOKE (FIGHTING): 27,7,339,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,157,156,420
        BOSS L44 SNEASEL (DARK/ICE): 8,210,156,252
        BOSS L48 WEAVILE (DARK/ICE): 8,279,115,306
        BOSS L66 WHISCASH (WATER/GROUND): 56,58,240,209
        BOSS L69 RAPIDASH (FIRE): 394,38,97,398
        BOSS L72 ALAKAZAM (PSYCHIC): 94,168,50,412
        BOSS L78 GARCHOMP (DRAGON/GROUND): 337,89,184,421
        BOSS L58 MAGMORTAR (FIRE): 315,94,70,216
        IMP  L7 STARLY (NORMAL/FLYING): 31,168,432,92
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,246,164,430
        IMP  L27 GROTLE (GRASS): 75,70,219,174
        IMP  L34 STARAVIA (NORMAL/FLYING): 365,36,355,228
        IMP  L36 STARAPTOR (NORMAL/FLYING): 19,211,355,129
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,400,339,89
        IMP  L47 RAPIDASH (FIRE): 394,224,97,156
        IMP  L42 STARAPTOR (NORMAL/FLYING): 19,211,156,263
        IMP  L25 KADABRA (PSYCHIC): 60,247,50,409
        IMP  L27 GROTLE (GRASS): 402,431,235,414
        IMP  L61 HERACROSS (BUG/FIGHTING): 370,157,339,30
        IMP  L69 RAPIDASH (FIRE): 394,340,261,445
        IMP  L73 SNORLAX (NORMAL): 34,280,182,352
        IMP  L83 SNORLAX (NORMAL): 38,352,133,402
        IMP  L60 SKUNTANK (POISON/DARK): 242,70,262,188
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
        REG  L42 MAGNETON (ELECTRIC/STEEL): 351,430,216,92
        REG  L56 EMPOLEON (WATER/STEEL): 56,59,64,156
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 9,414,366,304
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 19,257,18,485
        BOSS L72 Lucario (FIGHTING/STEEL): 238,198,46,245
        BOSS L28 Flaaffy (ELECTRIC): 85,496,109,178
        BOSS L48 Haxorus (DRAGON): 337,89,156,404
        BOSS L50 Cofagrigus (GHOST): 247,399,262,373
        BOSS L67 Simipour (WATER): 56,512,392,270
        BOSS L76 Clefable (NORMAL): 70,309,182,53
        BOSS L28 Emolga (ELECTRIC/FLYING): 528,369,403,98
        BOSS L49 Carracosta (WATER/ROCK): 444,416,334,523
        BOSS L56 Lucario (FIGHTING/STEEL): 231,198,526,164
        BOSS L73 Golurk (GROUND/GHOST): 89,359,397,19
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 280,400,86,304
        BOSS L75 Arcanine (FIRE): 126,370,234,245
        BOSS L75 Glaceon (ICE): 59,247,281,36
        IMP  L8 Tepig (FIRE): 52,317,261,343
        IMP  L48 Cryogonal (ICE): 59,324,151,115
        IMP  L23 Pansage (GRASS): 412,371,269,496
        IMP  L31 Tranquill (NORMAL/FLYING): 365,211,273,98
        IMP  L39 Unfezant (NORMAL/FLYING): 19,211,197,98
        IMP  L46 Cryogonal (ICE): 58,430,164,512
        IMP  L55 Unfezant (NORMAL/FLYING): 19,211,526,257
        IMP  L55 Simisear (FIRE): 315,157,164,411
        IMP  L62 Unfezant (NORMAL/FLYING): 19,211,366,263
        IMP  L62 Flygon (GROUND/DRAGON): 89,185,19,225
        IMP  L65 Unfezant (NORMAL/FLYING): 143,257,355,45
        IMP  L65 Eelektross (ELECTRIC): 435,401,86,525
        IMP  L41 Simisear (FIRE): 481,343,214,156
        IMP  L48 Unfezant (NORMAL/FLYING): 19,369,164,381
        IMP  L74 Klinklang (STEEL): 430,249,435,356
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 280,228,193,116
        REG  L63 Hitmonchan (FIGHTING): 264,418,496,170
        REG  L56 Unfezant (NORMAL/FLYING): 19,211,497,164
        REG  L47 Boldore (ROCK): 408,36,199,89
        REG  L45 Swinub (ICE/GROUND): 556,70,115,246
        REG  L32 Scolipede (BUG/POISON): 342,89,92,42
        REG  L65 Hitmontop (FIGHTING): 280,529,197,168
        REG  L52 Amoonguss (GRASS/POISON): 402,492,74,34
        REG  L64 Archeops (ROCK/FLYING): 365,525,446,184
        REG  L54 Metang (STEEL/PSYCHIC): 232,228,164,523
        REG  L60 Wooper (WATER/GROUND): 89,127,491,148
        REG  L67 Emboar (FIRE/FIGHTING): 315,503,292,372
        REG  L47 Krookodile (GROUND/DARK): 371,422,259,212
        REG  L25 Litwick (GHOST/FIRE): 83,51,213,373
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,583,281,423
        BOSS L41 Weezing (POISON): 188,247,261,290
        BOSS L5 Zigzagoon (NORMAL): 15,228,468,321
        BOSS L51 Dusclops (GHOST): 247,264,89,94
        BOSS L52 Froslass (ICE/GHOST): 247,358,164,242
        BOSS L57 Claydol (GROUND/PSYCHIC): 326,523,219,451
        BOSS L14 Machop (FIGHTING): 2,479,227,590
        BOSS L28 Slaking (NORMAL): 306,185,133,227
        BOSS L44 Whiscash (WATER/GROUND): 56,209,201,414
        BOSS L70 Sharpedo (WATER/DARK): 127,305,92,38
        BOSS L71 Dusknoir (GHOST): 247,228,114,248
        BOSS L73 Altaria (DRAGON/FLYING): 406,126,297,89
        BOSS L77 Carbink (ROCK/FAIRY): 444,414,219,393
        BOSS L57 Cradily (ROCK/GRASS): 412,188,105,444
        BOSS L57 Milotic (WATER): 57,58,114,525
        IMP  L18 Slugma (FIRE): 510,123,334,237
        IMP  L31 Wailmer (WATER): 127,58,392,249
        IMP  L18 Wailmer (WATER): 352,499,156,290
        IMP  L31 Shroomish (GRASS): 402,409,73,474
        IMP  L37 Swellow (NORMAL/FLYING): 413,369,432,228
        IMP  L37 Wailord (WATER): 57,34,392,499
        IMP  L46 Delcatty (NORMAL): 514,451,204,322
        IMP  L24 Shroomish (GRASS): 402,358,73,216
        IMP  L24 Slugma (FIRE): 510,246,220,254
        IMP  L32 Sharpedo (WATER/DARK): 400,89,184,162
        IMP  L55 Camerupt (FIRE/GROUND): 126,414,184,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 7,98,182,136
        IMP  L50 Sceptile (GRASS): 348,490,235,91
        IMP  L64 Altaria (DRAGON/FLYING): 19,523,97,114
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 370,8,14,473
        REG  L4 Zigzagoon (NORMAL): 237,351,493,164
        REG  L25 Slugma (FIRE): 52,246,92,496
        REG  L39 Claydol (GROUND/PSYCHIC): 91,444,379,447
        REG  L36 Golbat (POISON/FLYING): 17,211,109,599
        REG  L34 Golbat (POISON/FLYING): 314,305,213,18
        REG  L33 Roselia (GRASS/POISON): 71,326,605,213
        REG  L43 Solrock (ROCK/PSYCHIC): 444,89,447,373
        REG  L49 Jellicent (WATER/GHOST): 57,101,196,54
        REG  L37 Skarmory (STEEL/FLYING): 65,290,385,232
        REG  L41 Clamperl (WATER): 503,58,48,240
        REG  L39 Tentacruel (WATER/POISON): 330,59,219,240
        REG  L48 Honchkrow (DARK/FLYING): 492,247,269,373
        REG  L53 Flygon (GROUND/DRAGON): 91,332,522,590
        REG  L23 Grimer (POISON): 398,325,269,290
        REG  L51 Mightyena (DARK): 492,423,310,91
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 27,479,156,418
        BOSS L47 Bewear (NORMAL/FIGHTING): 38,409,339,707
        BOSS L56 Probopass (ROCK/STEEL): 408,351,277,199
        BOSS L41 Golisopod (BUG/WATER): 42,534,232,529
        BOSS L66 Froslass (ICE/GHOST): 8,358,694,92
        BOSS L66 Mandibuzz (DARK/FLYING): 492,198,366,260
        BOSS L57 Dugtrio (GROUND/STEEL): 707,228,526,444
        BOSS L52 Sableye (DARK/GHOST): 399,398,197,425
        BOSS L65 Crobat (POISON/FLYING): 413,211,18,599
        BOSS L64 Masquerain (BUG/FLYING): 405,61,78,466
        BOSS L66 Hydreigon (DARK/DRAGON): 407,157,115,512
        BOSS L65 Gyarados (WATER/FLYING): 401,525,349,196
        BOSS L64 Camerupt (FIRE/GROUND): 126,414,46,267
        BOSS L70 Mewtwo (PSYCHIC): 473,85,115,317
        BOSS L63 Crabominable (FIGHTING/ICE): 665,89,156,168
        IMP  L6 Pichu (ELECTRIC): 84,497,273,604
        IMP  L15 Glaceon (ICE): 524,500,694,352
        IMP  L27 Salandit (POISON/FIRE): 481,123,282,252
        IMP  L28 Noibat (FLYING/DRAGON): 314,280,406,369
        IMP  L41 Noivern (FLYING/DRAGON): 406,257,366,586
        IMP  L70 Primarina (WATER/FAIRY): 664,58,47,585
        IMP  L67 Muk (POISON/DARK): 398,282,397,425
        IMP  L53 Zoroark (DARK): 675,369,269,490
        IMP  L68 Zoroark (DARK): 539,369,184,197
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 9,98,86,204
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,324,273,21
        IMP  L68 Snorlax (NORMAL): 34,247,174,441
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 94,574,86,98
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,310,78,109
        IMP  L20 Poipole (POISON): 51,406,182,204
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 33,0,0,0
        REG  L5 Yungoos (NORMAL): 33,279,207,317
        REG  L69 Lapras (WATER/ICE): 58,127,590,219
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 9,3,179,175
        REG  L35 Marowak (FIRE/GHOST): 172,9,506,446
        REG  L5 Yungoos (NORMAL): 33,279,104,156
        REG  L5 Yungoos (NORMAL): 33,279,371,269
        REG  L55 Espeon (PSYCHIC): 485,605,343,321
        REG  L5 Yungoos (NORMAL): 33,279,182,269
        REG  L33 Zubat (POISON/FLYING): 17,98,92,212
        REG  L30 Minior (ROCK/FLYING): 205,605,446,512
        REG  L27 Trumbeak (NORMAL/FLYING): 365,168,497,207
        REG  L62 Persian (NORMAL): 6,85,583,282
        REG  L14 Rattata (DARK/NORMAL): 44,196,98,104
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
