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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,34,164,126
        BOSS L45 NIDOKING (POISON/GROUND): 89,85,92,24
        BOSS L55 HITMONLEE (FIGHTING): 136,36,92,104
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,92,164
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,86,164
        BOSS L24 RAICHU (ELECTRIC): 84,98,86,66
        BOSS L37 KOFFING (POISON): 123,33,92,108
        BOSS L43 WEEZING (POISON): 123,85,164,126
        BOSS L42 RAPIDASH (FIRE): 126,34,92,164
        BOSS L38 VENOMOTH (BUG/POISON): 141,72,164,102
        BOSS L53 CLOYSTER (WATER/ICE): 58,129,164,110
        BOSS L56 LAPRAS (WATER/ICE): 56,36,47,85
        BOSS L55 HAUNTER (GHOST/POISON): 122,85,92,94
        BOSS L56 DRAGONAIR (DRAGON): 85,55,86,115
        BOSS L62 DRAGONITE (DRAGON/FLYING): 61,58,92,97
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,115,18
        IMP  L15 ABRA (PSYCHIC): 69,99,86,118
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,98,92,28
        IMP  L18 KADABRA (PSYCHIC): 93,99,115,69
        IMP  L16 RATICATE (NORMAL): 158,55,92,39
        IMP  L25 WARTORTLE (WATER): 61,66,92,33
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,156,149
        IMP  L35 ALAKAZAM (PSYCHIC): 60,36,156,105
        IMP  L40 VENUSAUR (GRASS/POISON): 22,38,77,104
        IMP  L45 RHYHORN (GROUND/ROCK): 89,126,156,85
        IMP  L45 GYARADOS (WATER/FLYING): 56,85,92,164
        IMP  L47 GYARADOS (WATER/FLYING): 56,87,164,38
        IMP  L61 ARCANINE (FIRE): 53,44,115,43
        IMP  L63 ARCANINE (FIRE): 53,36,46,91
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,66,156,5
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,18,28
        REG  L18 MANKEY (FIGHTING): 2,157,92,43
        REG  L19 RATTATA (NORMAL): 158,61,156,102
        REG  L37 VULPIX (FIRE): 53,91,36,39
        REG  L29 WEEZING (POISON): 123,85,102,33
        REG  L31 CLOYSTER (WATER/ICE): 58,33,115,92
        REG  L26 MANKEY (FIGHTING): 66,5,157,156
        REG  L30 HORSEA (WATER): 61,58,104,156
        REG  L29 FEAROW (NORMAL/FLYING): 31,64,104,119
        REG  L70 GYARADOS (WATER/FLYING): 55,44,164,104
        REG  L17 MACHOP (FIGHTING): 2,69,156,102
        REG  L28 EKANS (POISON): 40,157,72,137
        REG  L39 DUGTRIO (GROUND): 91,163,156,45
        REG  L33 HAUNTER (GHOST/POISON): 122,94,92,156
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,197,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,29,228
        BOSS L31 PILOSWINE (ICE/GROUND): 89,246,156,181
        BOSS L37 DRAGONAIR (DRAGON): 239,53,86,113
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 93,202,73,23
        BOSS L46 MACHAMP (FIGHTING): 238,91,156,214
        BOSS L40 ARIADOS (BUG/POISON): 188,101,60,154
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,53,174,104
        BOSS L42 OMASTAR (ROCK/WATER): 61,196,114,44
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,196,109,85
        BOSS L41 JUMPLUFF (GRASS/FLYING): 72,38,73,77
        BOSS L33 ARIADOS (BUG/POISON): 188,91,184,101
        BOSS L45 MAGMAR (FIRE): 7,9,182,168
        BOSS L77 BLASTOISE (WATER): 56,196,174,29
        BOSS L58 ARCANINE (FIRE): 126,231,219,37
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,92
        IMP  L12 GASTLY (GHOST/POISON): 122,168,114,180
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,203
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,109,228
        IMP  L32 MEGANIUM (GRASS): 22,29,73,246
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,205,92,207
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,174,199
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,156,168
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,174,202
        IMP  L43 GENGAR (GHOST/POISON): 247,9,156,8
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,105,112
        IMP  L43 ALAKAZAM (PSYCHIC): 94,9,105,134
        IMP  L46 ALAKAZAM (PSYCHIC): 94,9,227,91
        IMP  L50 TYPHLOSION (FIRE): 126,9,92,66
        IMP  L50 FERALIGATR (WATER): 56,91,184,242
        REG  L10 CHIKORITA (GRASS): 75,246,73,45
        REG  L20 QUAGSIRE (WATER/GROUND): 189,21,213,201
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 16,168,193,92
        REG  L25 NINETALES (FIRE): 52,185,95,109
        REG  L31 RHYDON (GROUND/ROCK): 205,189,23,92
        REG  L18 GROWLITHE (FIRE): 83,44,249,225
        REG  L23 GOLDEEN (WATER): 30,60,174,92
        REG  L28 TENTACOOL (WATER/POISON): 40,202,48,203
        REG  L28 POLIWHIRL (WATER): 145,168,197,3
        REG  L32 ONIX (ROCK/GROUND): 88,29,106,207
        REG  L6 VOLTORB (ELECTRIC): 33,205,216,174
        REG  L31 FURRET (NORMAL): 163,228,116,207
        REG  L42 GOLDUCK (WATER): 238,231,156,8
        REG  L23 PIKACHU (ELECTRIC): 84,29,217,218
        REG  L25 ELECTRODE (ELECTRIC): 205,33,237,49
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,269,305
        BOSS L53 WALREIN (ICE/WATER): 58,157,258,89
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,317,92,237
        BOSS L43 SEALEO (ICE/WATER): 58,352,281,157
        BOSS L44 CAMERUPT (FIRE/GROUND): 53,91,164,36
        BOSS L50 KABUTOPS (ROCK/WATER): 205,163,201,62
        BOSS L46 HITMONCHAN (FIGHTING): 327,25,97,7
        BOSS L50 MANECTRIC (ELECTRIC): 85,129,46,44
        BOSS L46 GROWLITHE (FIRE): 257,91,46,242
        BOSS L45 KANGASKHAN (NORMAL): 146,89,156,168
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,47,218
        BOSS L58 SKARMORY (STEEL/FLYING): 65,31,174,46
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,88,201,129
        BOSS L56 LAPRAS (WATER/ICE): 58,85,182,38
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,280,92,36
        IMP  L34 MIGHTYENA (DARK): 44,36,281,305
        IMP  L40 GOLBAT (POISON/FLYING): 188,185,109,314
        IMP  L20 GROVYLE (GRASS): 71,225,164,317
        IMP  L29 LOMBRE (WATER/GRASS): 352,75,73,45
        IMP  L18 SLUGMA (FIRE): 52,123,281,151
        IMP  L29 PELIPPER (WATER/FLYING): 352,196,92,17
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,253,240,301
        IMP  L22 ZUBAT (POISON/FLYING): 17,290,174,228
        IMP  L47 ROSELIA (GRASS/POISON): 188,247,191,290
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,89,114,231
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 85,205,164,49
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 7,24,241,157
        IMP  L34 GROVYLE (GRASS): 348,225,97,129
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,317,174,55
        IMP  L15 MUDKIP (WATER): 352,91,92,23
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 55,280,227,104
        REG  L26 MIGHTYENA (DARK): 44,91,263,305
        REG  L33 MACHOP (FIGHTING): 27,91,102,193
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,263,89,113
        REG  L35 PLUSLE (ELECTRIC): 9,189,313,207
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,33,319,102
        REG  L30 KOFFING (POISON): 188,247,139,108
        REG  L6 SEEDOT (GRASS): 331,91,205,182
        REG  L11 MARILL (WATER): 352,196,47,287
        REG  L26 LOMBRE (WATER/GRASS): 331,9,8,310
        REG  L29 XATU (PSYCHIC/FLYING): 94,332,164,185
        REG  L29 ZUBAT (POISON/FLYING): 17,247,269,98
        REG  L34 PELIPPER (WATER/FLYING): 352,239,189,203
        REG  L5 KYOGRE (WATER): 352,196,104,218
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,33,189,213
        BOSS L49 SCIZOR (BUG/STEEL): 442,282,334,416
        BOSS L52 HIPPOWDON (GROUND): 414,422,231,276
        BOSS L57 MAGMORTAR (FIRE): 126,238,269,94
        BOSS L58 SPIRITOMB (GHOST/DARK): 247,318,417,286
        BOSS L20 CHERRIM (GRASS): 345,75,182,205
        BOSS L29 MACHOKE (FIGHTING): 27,418,227,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 412,420,92,280
        BOSS L44 SNEASEL (DARK/ICE): 399,8,404,252
        BOSS L48 WEAVILE (DARK/ICE): 8,458,258,421
        BOSS L66 WHISCASH (WATER/GROUND): 56,340,182,428
        BOSS L69 RAPIDASH (FIRE): 394,24,204,340
        BOSS L72 ALAKAZAM (PSYCHIC): 94,324,105,244
        BOSS L78 GARCHOMP (DRAGON/GROUND): 91,398,14,200
        BOSS L58 MAGMORTAR (FIRE): 53,9,109,112
        IMP  L7 STARLY (NORMAL/FLYING): 365,31,310,297
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,324,113,317
        IMP  L27 GROTLE (GRASS): 75,34,446,44
        IMP  L34 STARAVIA (NORMAL/FLYING): 290,211,182,369
        IMP  L36 STARAPTOR (NORMAL/FLYING): 36,168,18,211
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,421,339,317
        IMP  L47 RAPIDASH (FIRE): 394,24,156,204
        IMP  L42 STARAPTOR (NORMAL/FLYING): 413,370,355,211
        IMP  L25 KADABRA (PSYCHIC): 60,247,113,156
        IMP  L27 GROTLE (GRASS): 402,34,235,414
        IMP  L61 HERACROSS (BUG/FIGHTING): 264,421,339,224
        IMP  L69 RAPIDASH (FIRE): 394,37,204,340
        IMP  L73 SNORLAX (NORMAL): 38,8,182,442
        IMP  L83 SNORLAX (NORMAL): 38,442,276,441
        IMP  L60 SKUNTANK (POISON/DARK): 242,91,269,10
        REG  L5 STARLY (NORMAL/FLYING): 365,228,310,164
        REG  L29 ZUBAT (POISON/FLYING): 17,185,109,129
        REG  L36 SWINUB (ICE/GROUND): 91,420,104,157
        REG  L6 GEODUDE (ROCK/GROUND): 205,33,446,201
        REG  L21 CARNIVINE (GRASS): 75,189,78,207
        REG  L21 DRIFLOON (GHOST/FLYING): 247,451,86,129
        REG  L36 MURKROW (DARK/FLYING): 65,247,129,372
        REG  L39 MURKROW (DARK/FLYING): 64,185,101,355
        REG  L58 PELIPPER (WATER/FLYING): 352,239,203,371
        REG  L32 EEVEE (NORMAL): 129,91,39,204
        REG  L48 SEAKING (WATER): 291,340,60,39
        REG  L42 GOLBAT (POISON/FLYING): 16,228,164,114
        REG  L23 BUIZEL (WATER): 352,91,29,316
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,161,199,351
        REG  L56 EMPOLEON (WATER/STEEL): 430,250,196,324
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 9,337,366,428
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 473,399,277,314
        BOSS L72 Lucario (FIGHTING/STEEL): 327,8,14,247
        BOSS L28 Flaaffy (ELECTRIC): 85,497,156,280
        BOSS L48 Haxorus (DRAGON): 200,89,398,372
        BOSS L50 Cofagrigus (GHOST): 247,412,174,288
        BOSS L67 Simipour (WATER): 56,441,269,512
        BOSS L76 Clefable (NORMAL): 304,409,215,236
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,369,182,92
        BOSS L49 Carracosta (WATER/ROCK): 401,317,334,242
        BOSS L56 Lucario (FIGHTING/STEEL): 430,238,46,207
        BOSS L73 Golurk (GROUND/GHOST): 89,317,397,223
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,157,14,425
        BOSS L75 Arcanine (FIRE): 394,24,164,422
        BOSS L75 Glaceon (ICE): 58,496,46,44
        IMP  L8 Tepig (FIRE): 52,343,261,249
        IMP  L48 Cryogonal (ICE): 59,430,334,512
        IMP  L23 Pansage (GRASS): 345,421,371,343
        IMP  L31 Tranquill (NORMAL/FLYING): 365,211,273,98
        IMP  L39 Unfezant (NORMAL/FLYING): 13,369,273,257
        IMP  L46 Cryogonal (ICE): 59,430,164,258
        IMP  L55 Unfezant (NORMAL/FLYING): 13,211,182,332
        IMP  L55 Simisear (FIRE): 315,490,281,421
        IMP  L62 Unfezant (NORMAL/FLYING): 143,211,526,43
        IMP  L62 Flygon (GROUND/DRAGON): 337,7,164,98
        IMP  L65 Unfezant (NORMAL/FLYING): 143,211,234,45
        IMP  L65 Eelektross (ELECTRIC): 85,29,46,491
        IMP  L41 Simisear (FIRE): 257,280,133,44
        IMP  L48 Unfezant (NORMAL/FLYING): 143,211,273,369
        IMP  L74 Klinklang (STEEL): 430,528,86,334
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 370,299,25,418
        REG  L63 Hitmonchan (FIGHTING): 327,343,216,228
        REG  L56 Unfezant (NORMAL/FLYING): 365,211,98,197
        REG  L47 Boldore (ROCK): 444,414,222,237
        REG  L45 Swinub (ICE/GROUND): 556,341,164,207
        REG  L32 Scolipede (BUG/POISON): 342,41,249,523
        REG  L65 Hitmontop (FIGHTING): 136,418,97,529
        REG  L52 Amoonguss (GRASS/POISON): 72,499,388,275
        REG  L64 Archeops (ROCK/FLYING): 88,525,257,355
        REG  L54 Metang (STEEL/PSYCHIC): 309,280,360,317
        REG  L60 Wooper (WATER/GROUND): 401,523,482,105
        REG  L67 Emboar (FIRE/FIGHTING): 126,457,535,9
        REG  L47 Krookodile (GROUND/DARK): 44,37,414,289
        REG  L25 Litwick (GHOST/FIRE): 83,123,114,156
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,583,281,304
        BOSS L41 Weezing (POISON): 188,60,114,253
        BOSS L5 Zigzagoon (NORMAL): 33,228,468,351
        BOSS L51 Dusclops (GHOST): 247,7,262,174
        BOSS L52 Froslass (ICE/GHOST): 8,358,50,506
        BOSS L57 Claydol (GROUND/PSYCHIC): 428,529,219,379
        BOSS L14 Machop (FIGHTING): 2,479,156,67
        BOSS L28 Slaking (NORMAL): 34,421,468,351
        BOSS L44 Whiscash (WATER/GROUND): 414,340,133,428
        BOSS L70 Sharpedo (WATER/DARK): 56,305,269,399
        BOSS L71 Dusknoir (GHOST): 247,9,174,50
        BOSS L73 Altaria (DRAGON/FLYING): 337,228,349,92
        BOSS L77 Carbink (ROCK/FAIRY): 408,605,277,343
        BOSS L57 Cradily (ROCK/GRASS): 402,246,220,523
        BOSS L57 Milotic (WATER): 401,58,109,496
        IMP  L18 Slugma (FIRE): 510,237,262,106
        IMP  L31 Wailmer (WATER): 352,340,182,499
        IMP  L18 Wailmer (WATER): 352,499,46,45
        IMP  L31 Shroomish (GRASS): 331,474,77,29
        IMP  L37 Swellow (NORMAL/FLYING): 413,228,18,290
        IMP  L37 Wailord (WATER): 323,499,392,487
        IMP  L46 Delcatty (NORMAL): 38,426,86,156
        IMP  L24 Shroomish (GRASS): 331,358,77,29
        IMP  L24 Slugma (FIRE): 510,499,262,216
        IMP  L32 Sharpedo (WATER/DARK): 400,129,92,453
        IMP  L55 Camerupt (FIRE/GROUND): 315,23,201,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 53,280,92,157
        IMP  L50 Sceptile (GRASS): 437,280,219,9
        IMP  L64 Altaria (DRAGON/FLYING): 406,53,538,605
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 370,157,156,45
        REG  L4 Zigzagoon (NORMAL): 33,351,493,164
        REG  L25 Slugma (FIRE): 52,123,611,263
        REG  L39 Claydol (GROUND/PSYCHIC): 529,479,93,322
        REG  L36 Golbat (POISON/FLYING): 17,168,98,305
        REG  L34 Golbat (POISON/FLYING): 314,474,174,18
        REG  L33 Roselia (GRASS/POISON): 71,474,42,74
        REG  L43 Solrock (ROCK/PSYCHIC): 88,442,201,244
        REG  L49 Jellicent (WATER/GHOST): 323,94,271,506
        REG  L37 Skarmory (STEEL/FLYING): 65,317,399,43
        REG  L41 Clamperl (WATER): 250,58,34,92
        REG  L39 Tentacruel (WATER/POISON): 51,352,196,513
        REG  L48 Honchkrow (DARK/FLYING): 185,211,290,373
        REG  L53 Flygon (GROUND/DRAGON): 337,332,211,237
        REG  L23 Grimer (POISON): 398,189,254,104
        REG  L51 Mightyena (DARK): 242,305,310,382
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
