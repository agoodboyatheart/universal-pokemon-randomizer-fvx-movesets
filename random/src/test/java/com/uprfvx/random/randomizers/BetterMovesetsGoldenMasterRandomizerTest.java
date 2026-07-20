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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,31,164,126
        BOSS L45 NIDOKING (POISON/GROUND): 89,37,164,66
        BOSS L55 HITMONLEE (FIGHTING): 136,5,92,104
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,129,115,149
        BOSS L24 RAICHU (ELECTRIC): 84,5,115,104
        BOSS L37 KOFFING (POISON): 123,85,164,108
        BOSS L43 WEEZING (POISON): 123,126,92,120
        BOSS L42 RAPIDASH (FIRE): 126,23,164,102
        BOSS L38 VENOMOTH (BUG/POISON): 141,60,77,156
        BOSS L53 CLOYSTER (WATER/ICE): 58,131,92,48
        BOSS L56 LAPRAS (WATER/ICE): 56,94,164,45
        BOSS L55 HAUNTER (GHOST/POISON): 122,94,164,85
        BOSS L56 DRAGONAIR (DRAGON): 34,85,86,115
        BOSS L62 DRAGONITE (DRAGON/FLYING): 58,61,97,43
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,115,156
        IMP  L15 ABRA (PSYCHIC): 69,99,115,118
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,16,164,102
        IMP  L18 KADABRA (PSYCHIC): 93,99,164,50
        IMP  L16 RATICATE (NORMAL): 129,61,92,102
        IMP  L25 WARTORTLE (WATER): 61,5,164,69
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,164,95
        IMP  L35 ALAKAZAM (PSYCHIC): 60,91,105,69
        IMP  L40 VENUSAUR (GRASS/POISON): 22,34,115,77
        IMP  L45 RHYHORN (GROUND/ROCK): 91,23,156,87
        IMP  L45 GYARADOS (WATER/FLYING): 56,58,156,82
        IMP  L47 GYARADOS (WATER/FLYING): 56,38,156,43
        IMP  L61 ARCANINE (FIRE): 53,44,156,43
        IMP  L63 ARCANINE (FIRE): 53,44,92,43
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,66,92,91
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,28,18
        REG  L18 MANKEY (FIGHTING): 157,2,43,104
        REG  L19 RATTATA (NORMAL): 33,61,104,92
        REG  L37 VULPIX (FIRE): 53,129,46,39
        REG  L29 WEEZING (POISON): 123,85,92,104
        REG  L31 CLOYSTER (WATER/ICE): 61,129,102,156
        REG  L26 MANKEY (FIGHTING): 66,157,10,118
        REG  L30 HORSEA (WATER): 145,38,156,104
        REG  L29 FEAROW (NORMAL/FLYING): 31,64,45,164
        REG  L70 GYARADOS (WATER/FLYING): 56,58,43,44
        REG  L17 MACHOP (FIGHTING): 2,69,92,156
        REG  L28 EKANS (POISON): 40,157,137,164
        REG  L39 DUGTRIO (GROUND): 89,10,156,104
        REG  L33 HAUNTER (GHOST/POISON): 122,72,95,109
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 228,189,197,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,29,228
        BOSS L31 PILOSWINE (ICE/GROUND): 89,246,156,181
        BOSS L37 DRAGONAIR (DRAGON): 239,53,86,113
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 246,188,140,218
        BOSS L46 MACHAMP (FIGHTING): 238,91,113,168
        BOSS L40 ARIADOS (BUG/POISON): 188,91,184,228
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,9,86,7
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,114,131
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,61,182,85
        BOSS L41 JUMPLUFF (GRASS/FLYING): 6,93,235,38
        BOSS L33 ARIADOS (BUG/POISON): 188,101,182,81
        BOSS L45 MAGMAR (FIRE): 7,2,156,92
        BOSS L77 BLASTOISE (WATER): 56,89,174,8
        BOSS L58 ARCANINE (FIRE): 53,245,182,242
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,244
        IMP  L12 GASTLY (GHOST/POISON): 122,168,114,174
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,203
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,18,216
        IMP  L32 MEGANIUM (GRASS): 22,29,73,246
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,129,86,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,205
        IMP  L35 HAUNTER (GHOST/POISON): 247,192,109,212
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,114,216
        IMP  L43 GENGAR (GHOST/POISON): 247,7,85,94
        IMP  L43 ALAKAZAM (PSYCHIC): 94,192,227,182
        IMP  L43 ALAKAZAM (PSYCHIC): 94,168,113,237
        IMP  L46 ALAKAZAM (PSYCHIC): 94,9,227,50
        IMP  L50 TYPHLOSION (FIRE): 126,154,92,66
        IMP  L50 FERALIGATR (WATER): 56,89,46,103
        REG  L10 CHIKORITA (GRASS): 75,246,197,207
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,104,8
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 33,17,104,185
        REG  L25 NINETALES (FIRE): 52,91,109,29
        REG  L31 RHYDON (GROUND/ROCK): 189,157,9,184
        REG  L18 GROWLITHE (FIRE): 83,225,156,207
        REG  L23 GOLDEEN (WATER): 30,60,203,218
        REG  L28 TENTACOOL (WATER/POISON): 61,58,203,188
        REG  L28 POLIWHIRL (WATER): 145,168,58,237
        REG  L32 ONIX (ROCK/GROUND): 88,249,91,92
        REG  L6 VOLTORB (ELECTRIC): 33,205,216,92
        REG  L31 FURRET (NORMAL): 10,9,228,218
        REG  L42 GOLDUCK (WATER): 154,196,93,193
        REG  L23 PIKACHU (ELECTRIC): 9,21,179,182
        REG  L25 ELECTRODE (ELECTRIC): 33,205,120,174
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,290,281,247
        BOSS L53 WALREIN (ICE/WATER): 58,205,182,352
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,23,46,104
        BOSS L43 SEALEO (ICE/WATER): 58,317,258,89
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,317,184,89
        BOSS L50 KABUTOPS (ROCK/WATER): 205,34,14,58
        BOSS L46 HITMONCHAN (FIGHTING): 327,4,339,9
        BOSS L50 MANECTRIC (ELECTRIC): 85,98,92,242
        BOSS L46 GROWLITHE (FIRE): 126,242,46,332
        BOSS L45 KANGASKHAN (NORMAL): 146,280,182,85
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,46,38
        BOSS L58 SKARMORY (STEEL/FLYING): 65,168,46,201
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 129,157,219,285
        BOSS L56 LAPRAS (WATER/ICE): 56,263,47,174
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 94,247,334,188
        IMP  L34 MIGHTYENA (DARK): 44,305,231,92
        IMP  L40 GOLBAT (POISON/FLYING): 188,202,18,289
        IMP  L20 GROVYLE (GRASS): 331,228,219,129
        IMP  L29 LOMBRE (WATER/GRASS): 352,8,235,202
        IMP  L18 SLUGMA (FIRE): 52,88,241,113
        IMP  L29 PELIPPER (WATER/FLYING): 17,58,240,211
        IMP  L31 MARSHTOMP (WATER/GROUND): 91,157,164,69
        IMP  L22 ZUBAT (POISON/FLYING): 17,173,156,168
        IMP  L47 ROSELIA (GRASS/POISON): 76,38,241,275
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,89,164,31
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 87,161,205,203
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 53,332,182,280
        IMP  L34 GROVYLE (GRASS): 348,9,92,242
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,205,240,36
        IMP  L15 MUDKIP (WATER): 352,317,182,23
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 145,8,216,21
        REG  L26 MIGHTYENA (DARK): 44,305,310,102
        REG  L33 MACHOP (FIGHTING): 280,34,91,156
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,247,106,322
        REG  L35 PLUSLE (ELECTRIC): 9,223,205,164
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,129,164,205
        REG  L30 KOFFING (POISON): 123,168,149,263
        REG  L6 SEEDOT (GRASS): 331,91,207,206
        REG  L11 MARILL (WATER): 352,189,240,196
        REG  L26 LOMBRE (WATER/GRASS): 55,310,263,164
        REG  L29 XATU (PSYCHIC/FLYING): 94,332,98,297
        REG  L29 ZUBAT (POISON/FLYING): 16,185,202,289
        REG  L34 PELIPPER (WATER/FLYING): 17,189,290,182
        REG  L5 KYOGRE (WATER): 352,196,347,351
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,189,446,175
        BOSS L49 SCIZOR (BUG/STEEL): 442,400,201,318
        BOSS L52 HIPPOWDON (GROUND): 89,423,281,352
        BOSS L57 MAGMORTAR (FIRE): 394,264,269,5
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,253,92,174
        BOSS L20 CHERRIM (GRASS): 345,205,312,216
        BOSS L29 MACHOKE (FIGHTING): 27,8,339,418
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,420,320,280
        BOSS L44 SNEASEL (DARK/ICE): 8,332,258,371
        BOSS L48 WEAVILE (DARK/ICE): 400,419,258,247
        BOSS L66 WHISCASH (WATER/GROUND): 401,196,133,37
        BOSS L69 RAPIDASH (FIRE): 53,38,156,340
        BOSS L72 ALAKAZAM (PSYCHIC): 428,168,227,113
        BOSS L78 GARCHOMP (DRAGON/GROUND): 89,280,421,129
        BOSS L58 MAGMORTAR (FIRE): 53,85,214,156
        IMP  L7 STARLY (NORMAL/FLYING): 365,98,164,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 326,324,182,157
        IMP  L27 GROTLE (GRASS): 75,414,174,388
        IMP  L34 STARAVIA (NORMAL/FLYING): 36,228,355,332
        IMP  L36 STARAPTOR (NORMAL/FLYING): 36,310,97,369
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,168,334,445
        IMP  L47 RAPIDASH (FIRE): 315,24,241,216
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,211,297,413
        IMP  L25 KADABRA (PSYCHIC): 94,324,164,227
        IMP  L27 GROTLE (GRASS): 402,33,182,113
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,89,334,203
        IMP  L69 RAPIDASH (FIRE): 394,224,95,39
        IMP  L73 SNORLAX (NORMAL): 34,242,281,428
        IMP  L83 SNORLAX (NORMAL): 416,280,281,126
        IMP  L60 SKUNTANK (POISON/DARK): 399,91,262,247
        REG  L5 STARLY (NORMAL/FLYING): 365,228,310,164
        REG  L29 ZUBAT (POISON/FLYING): 365,428,290,445
        REG  L36 SWINUB (ICE/GROUND): 91,333,203,258
        REG  L6 GEODUDE (ROCK/GROUND): 205,246,189,104
        REG  L21 CARNIVINE (GRASS): 22,44,203,79
        REG  L21 DRIFLOON (GHOST/FLYING): 247,451,278,347
        REG  L36 MURKROW (DARK/FLYING): 365,466,297,18
        REG  L39 MURKROW (DARK/FLYING): 185,239,65,189
        REG  L58 PELIPPER (WATER/FLYING): 56,59,441,211
        REG  L32 EEVEE (NORMAL): 33,247,273,445
        REG  L48 SEAKING (WATER): 291,290,175,156
        REG  L42 GOLBAT (POISON/FLYING): 413,371,290,141
        REG  L23 BUIZEL (WATER): 55,91,182,164
        REG  L42 MAGNETON (ELECTRIC/STEEL): 85,443,161,244
        REG  L56 EMPOLEON (WATER/STEEL): 61,210,175,156
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 9,337,366,428
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 143,412,322,399
        BOSS L72 Lucario (FIGHTING/STEEL): 231,238,334,89
        BOSS L28 Flaaffy (ELECTRIC): 85,324,280,7
        BOSS L48 Haxorus (DRAGON): 337,401,14,157
        BOSS L50 Cofagrigus (GHOST): 247,412,219,94
        BOSS L67 Simipour (WATER): 503,8,156,270
        BOSS L76 Clefable (NORMAL): 304,53,347,247
        BOSS L28 Emolga (ELECTRIC/FLYING): 528,332,204,355
        BOSS L49 Carracosta (WATER/ROCK): 401,34,201,91
        BOSS L56 Lucario (FIGHTING/STEEL): 430,299,97,398
        BOSS L73 Golurk (GROUND/GHOST): 414,157,277,356
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,9,219,523
        BOSS L75 Arcanine (FIRE): 257,370,261,422
        BOSS L75 Glaceon (ICE): 58,485,164,376
        IMP  L8 Tepig (FIRE): 52,33,156,174
        IMP  L48 Cryogonal (ICE): 59,398,164,334
        IMP  L23 Pansage (GRASS): 412,512,490,447
        IMP  L31 Tranquill (NORMAL/FLYING): 365,211,234,355
        IMP  L39 Unfezant (NORMAL/FLYING): 253,332,297,43
        IMP  L46 Cryogonal (ICE): 59,430,258,398
        IMP  L55 Unfezant (NORMAL/FLYING): 143,263,273,369
        IMP  L55 Simisear (FIRE): 315,276,92,321
        IMP  L62 Unfezant (NORMAL/FLYING): 143,369,197,216
        IMP  L62 Flygon (GROUND/DRAGON): 337,276,355,522
        IMP  L65 Unfezant (NORMAL/FLYING): 63,369,366,273
        IMP  L65 Eelektross (ELECTRIC): 435,202,164,393
        IMP  L41 Simisear (FIRE): 481,496,269,282
        IMP  L48 Unfezant (NORMAL/FLYING): 143,211,95,213
        IMP  L74 Klinklang (STEEL): 430,528,86,393
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 27,398,25,96
        REG  L63 Hitmonchan (FIGHTING): 327,418,92,203
        REG  L56 Unfezant (NORMAL/FLYING): 365,211,526,197
        REG  L47 Boldore (ROCK): 444,89,222,335
        REG  L45 Swinub (ICE/GROUND): 89,419,115,316
        REG  L32 Scolipede (BUG/POISON): 224,398,496,401
        REG  L65 Hitmontop (FIGHTING): 27,228,529,170
        REG  L52 Amoonguss (GRASS/POISON): 188,202,388,492
        REG  L64 Archeops (ROCK/FLYING): 457,211,501,446
        REG  L54 Metang (STEEL/PSYCHIC): 232,280,36,397
        REG  L60 Wooper (WATER/GROUND): 401,24,8,414
        REG  L67 Emboar (FIRE/FIGHTING): 276,510,213,526
        REG  L47 Krookodile (GROUND/DARK): 91,479,401,446
        REG  L25 Litwick (GHOST/FIRE): 247,51,104,373
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 399,424,269,304
        BOSS L41 Weezing (POISON): 188,458,182,269
        BOSS L5 Zigzagoon (NORMAL): 228,352,182,451
        BOSS L51 Dusclops (GHOST): 247,280,7,59
        BOSS L52 Froslass (ICE/GHOST): 247,44,258,577
        BOSS L57 Claydol (GROUND/PSYCHIC): 94,414,164,246
        BOSS L14 Machop (FIGHTING): 2,418,164,207
        BOSS L28 Slaking (NORMAL): 34,7,174,185
        BOSS L44 Whiscash (WATER/GROUND): 56,89,182,175
        BOSS L70 Sharpedo (WATER/DARK): 56,423,97,162
        BOSS L71 Dusknoir (GHOST): 247,89,114,216
        BOSS L73 Altaria (DRAGON/FLYING): 200,126,156,574
        BOSS L77 Carbink (ROCK/FAIRY): 605,408,113,218
        BOSS L57 Cradily (ROCK/GRASS): 412,482,446,105
        BOSS L57 Milotic (WATER): 401,406,95,442
        IMP  L18 Slugma (FIRE): 510,611,220,496
        IMP  L31 Wailmer (WATER): 352,237,156,104
        IMP  L18 Wailmer (WATER): 352,290,174,523
        IMP  L31 Shroomish (GRASS): 331,409,182,237
        IMP  L37 Swellow (NORMAL/FLYING): 413,290,366,48
        IMP  L37 Wailord (WATER): 503,58,164,496
        IMP  L46 Delcatty (NORMAL): 38,58,426,351
        IMP  L24 Shroomish (GRASS): 402,474,73,496
        IMP  L24 Slugma (FIRE): 510,246,262,263
        IMP  L32 Sharpedo (WATER/DARK): 44,163,156,180
        IMP  L55 Camerupt (FIRE/GROUND): 53,442,164,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 299,530,164,163
        IMP  L50 Sceptile (GRASS): 400,24,92,332
        IMP  L64 Altaria (DRAGON/FLYING): 200,89,114,349
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 370,157,277,451
        REG  L4 Zigzagoon (NORMAL): 33,351,493,164
        REG  L25 Slugma (FIRE): 53,414,157,106
        REG  L39 Claydol (GROUND/PSYCHIC): 326,247,229,148
        REG  L36 Golbat (POISON/FLYING): 314,44,129,103
        REG  L34 Golbat (POISON/FLYING): 17,141,95,18
        REG  L33 Roselia (GRASS/POISON): 345,326,170,230
        REG  L43 Solrock (ROCK/PSYCHIC): 428,157,89,149
        REG  L49 Jellicent (WATER/GHOST): 61,482,506,20
        REG  L37 Skarmory (STEEL/FLYING): 64,232,18,164
        REG  L41 Clamperl (WATER): 128,58,112,218
        REG  L39 Tentacruel (WATER/POISON): 482,352,218,513
        REG  L48 Honchkrow (DARK/FLYING): 399,17,297,260
        REG  L53 Flygon (GROUND/DRAGON): 200,328,175,369
        REG  L23 Grimer (POISON): 398,325,254,139
        REG  L51 Mightyena (DARK): 492,583,213,424
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 27,479,526,193
        BOSS L47 Bewear (NORMAL/FIGHTING): 359,8,164,707
        BOSS L56 Probopass (ROCK/STEEL): 430,521,397,479
        BOSS L41 Golisopod (BUG/WATER): 660,534,14,524
        BOSS L66 Froslass (ICE/GHOST): 247,8,164,289
        BOSS L66 Mandibuzz (DARK/FLYING): 492,369,19,92
        BOSS L57 Dugtrio (GROUND/STEEL): 442,91,168,497
        BOSS L52 Sableye (DARK/GHOST): 247,408,277,280
        BOSS L65 Crobat (POISON/FLYING): 188,185,92,512
        BOSS L64 Masquerain (BUG/FLYING): 405,314,114,346
        BOSS L66 Hydreigon (DARK/DRAGON): 406,53,269,304
        BOSS L65 Gyarados (WATER/FLYING): 127,340,349,87
        BOSS L64 Camerupt (FIRE/GROUND): 53,426,281,317
        BOSS L70 Mewtwo (PSYCHIC): 473,58,219,396
        BOSS L63 Crabominable (FIGHTING/ICE): 665,428,258,146
        IMP  L6 Pichu (ELECTRIC): 351,574,227,604
        IMP  L15 Glaceon (ICE): 524,496,273,590
        IMP  L27 Salandit (POISON/FIRE): 481,406,269,141
        IMP  L28 Noibat (FLYING/DRAGON): 314,247,97,162
        IMP  L41 Noivern (FLYING/DRAGON): 542,53,432,586
        IMP  L70 Primarina (WATER/FAIRY): 664,59,156,585
        IMP  L67 Muk (POISON/DARK): 398,693,151,207
        IMP  L53 Zoroark (DARK): 675,332,197,383
        IMP  L68 Zoroark (DARK): 399,369,332,104
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 435,280,115,473
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 473,521,204,683
        IMP  L68 Snorlax (NORMAL): 38,402,281,103
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 87,264,324,282
        IMP  L51 Shiinotic (GRASS/FAIRY): 605,237,668,86
        IMP  L20 Poipole (POISON): 51,324,204,237
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 371,279,351,104
        REG  L69 Lapras (WATER/ICE): 127,419,94,684
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 473,209,219,252
        REG  L35 Marowak (FIRE/GHOST): 708,125,479,116
        REG  L5 Yungoos (NORMAL): 351,317,104,168
        REG  L5 Yungoos (NORMAL): 496,371,216,317
        REG  L55 Espeon (PSYCHIC): 473,237,376,477
        REG  L5 Yungoos (NORMAL): 173,168,156,182
        REG  L33 Zubat (POISON/FLYING): 19,141,174,162
        REG  L30 Minior (ROCK/FLYING): 205,369,393,113
        REG  L27 Trumbeak (NORMAL/FLYING): 65,211,479,45
        REG  L62 Persian (NORMAL): 163,399,441,316
        REG  L14 Rattata (DARK/NORMAL): 44,343,104,373
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
