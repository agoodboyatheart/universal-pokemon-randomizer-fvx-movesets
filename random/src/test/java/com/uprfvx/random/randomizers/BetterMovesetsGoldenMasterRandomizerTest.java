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
        BOSS L55 HITMONLEE (FIGHTING): 136,36,156,118
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,90
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,92,149
        BOSS L24 RAICHU (ELECTRIC): 84,98,92,69
        BOSS L37 KOFFING (POISON): 123,85,92,108
        BOSS L43 WEEZING (POISON): 123,126,156,120
        BOSS L42 RAPIDASH (FIRE): 126,23,115,45
        BOSS L38 VENOMOTH (BUG/POISON): 141,72,50,164
        BOSS L53 CLOYSTER (WATER/ICE): 59,61,164,43
        BOSS L56 LAPRAS (WATER/ICE): 58,85,156,56
        BOSS L55 HAUNTER (GHOST/POISON): 122,87,92,94
        BOSS L56 DRAGONAIR (DRAGON): 61,59,156,85
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,21,86,85
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,115,156
        IMP  L15 ABRA (PSYCHIC): 69,99,115,118
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,16,164,102
        IMP  L18 KADABRA (PSYCHIC): 93,99,156,69
        IMP  L16 RATICATE (NORMAL): 158,55,156,39
        IMP  L25 WARTORTLE (WATER): 61,5,164,115
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 99,140,115,95
        IMP  L35 ALAKAZAM (PSYCHIC): 60,161,50,66
        IMP  L40 VENUSAUR (GRASS/POISON): 22,33,164,77
        IMP  L45 RHYHORN (GROUND/ROCK): 91,157,92,126
        IMP  L45 GYARADOS (WATER/FLYING): 56,58,92,87
        IMP  L47 GYARADOS (WATER/FLYING): 56,34,156,126
        IMP  L61 ARCANINE (FIRE): 53,44,97,46
        IMP  L63 ARCANINE (FIRE): 53,91,156,34
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,34,164,91
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
        REG  L39 DUGTRIO (GROUND): 89,157,28,102
        REG  L33 HAUNTER (GHOST/POISON): 122,72,109,102
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 189,197,216,168
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,228,29
        BOSS L31 PILOSWINE (ICE/GROUND): 89,246,156,181
        BOSS L37 DRAGONAIR (DRAGON): 239,53,86,113
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 168,77,207,246
        BOSS L46 MACHAMP (FIGHTING): 238,89,184,92
        BOSS L40 ARIADOS (BUG/POISON): 188,101,50,60
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,85,174,48
        BOSS L42 OMASTAR (ROCK/WATER): 55,58,174,131
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,58,105,61
        BOSS L41 JUMPLUFF (GRASS/FLYING): 38,78,93,72
        BOSS L33 ARIADOS (BUG/POISON): 188,91,101,60
        BOSS L45 MAGMAR (FIRE): 53,231,238,168
        BOSS L77 BLASTOISE (WATER): 56,59,156,54
        BOSS L58 ARCANINE (FIRE): 126,242,231,91
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,244
        IMP  L12 GASTLY (GHOST/POISON): 122,168,114,180
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,203
        IMP  L20 ZUBAT (POISON/FLYING): 16,141,18,98
        IMP  L32 MEGANIUM (GRASS): 22,29,73,246
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,129,86,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,86,129,205
        IMP  L35 HAUNTER (GHOST/POISON): 247,109,212,192
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,92,168
        IMP  L43 GENGAR (GHOST/POISON): 247,8,114,9
        IMP  L43 ALAKAZAM (PSYCHIC): 94,192,113,168
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,105,9
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,50,174
        IMP  L50 TYPHLOSION (FIRE): 126,66,156,179
        IMP  L50 FERALIGATR (WATER): 56,29,46,58
        REG  L10 CHIKORITA (GRASS): 75,246,230,104
        REG  L20 QUAGSIRE (WATER/GROUND): 55,246,213,8
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 17,211,237,28
        REG  L25 NINETALES (FIRE): 52,185,180,213
        REG  L31 RHYDON (GROUND/ROCK): 189,157,9,184
        REG  L18 GROWLITHE (FIRE): 83,29,182,174
        REG  L23 GOLDEEN (WATER): 30,64,174,182
        REG  L28 TENTACOOL (WATER/POISON): 188,202,229,61
        REG  L28 POLIWHIRL (WATER): 61,3,104,95
        REG  L32 ONIX (ROCK/GROUND): 91,88,104,201
        REG  L6 VOLTORB (ELECTRIC): 205,203,182,33
        REG  L31 FURRET (NORMAL): 129,247,111,179
        REG  L42 GOLDUCK (WATER): 91,8,94,174
        REG  L23 PIKACHU (ELECTRIC): 9,3,156,217
        REG  L25 ELECTRODE (ELECTRIC): 205,182,216,129
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,263,281,247
        BOSS L53 WALREIN (ICE/WATER): 58,156,216,352
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,157,184,23
        BOSS L43 SEALEO (ICE/WATER): 59,352,227,290
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,91,133,46
        BOSS L50 KABUTOPS (ROCK/WATER): 61,62,282,72
        BOSS L46 HITMONCHAN (FIGHTING): 327,317,197,170
        BOSS L50 MANECTRIC (ELECTRIC): 87,33,86,231
        BOSS L46 GROWLITHE (FIRE): 257,91,97,290
        BOSS L45 KANGASKHAN (NORMAL): 5,89,92,7
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,34,58
        BOSS L58 SKARMORY (STEEL/FLYING): 65,168,191,38
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 317,113,34,94
        BOSS L56 LAPRAS (WATER/ICE): 59,231,109,38
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,92,317
        IMP  L34 MIGHTYENA (DARK): 44,305,92,231
        IMP  L40 GOLBAT (POISON/FLYING): 188,310,18,168
        IMP  L20 GROVYLE (GRASS): 71,228,225,280
        IMP  L29 LOMBRE (WATER/GRASS): 352,7,73,175
        IMP  L18 SLUGMA (FIRE): 52,157,92,281
        IMP  L29 PELIPPER (WATER/FLYING): 17,58,168,263
        IMP  L31 MARSHTOMP (WATER/GROUND): 91,33,92,301
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,92,48
        IMP  L47 ROSELIA (GRASS/POISON): 247,237,202,188
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,263,168
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 85,205,214,156
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 223,53,241,98
        IMP  L34 GROVYLE (GRASS): 348,228,73,332
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,157,240,196
        IMP  L15 MUDKIP (WATER): 352,91,174,45
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 145,8,216,21
        REG  L26 MIGHTYENA (DARK): 44,310,28,237
        REG  L33 MACHOP (FIGHTING): 280,265,157,92
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,247,156,106
        REG  L35 PLUSLE (ELECTRIC): 85,223,164,218
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,203,86,129
        REG  L30 KOFFING (POISON): 123,85,203,216
        REG  L6 SEEDOT (GRASS): 331,205,206,91
        REG  L11 MARILL (WATER): 55,189,39,129
        REG  L26 LOMBRE (WATER/GRASS): 71,154,267,216
        REG  L29 XATU (PSYCHIC/FLYING): 65,202,101,297
        REG  L29 ZUBAT (POISON/FLYING): 16,44,92,218
        REG  L34 PELIPPER (WATER/FLYING): 17,239,213,129
        REG  L5 KYOGRE (WATER): 352,351,86,196
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,446,111,189
        BOSS L49 SCIZOR (BUG/STEEL): 442,168,113,203
        BOSS L52 HIPPOWDON (GROUND): 89,242,446,36
        BOSS L57 MAGMORTAR (FIRE): 394,85,164,103
        BOSS L58 SPIRITOMB (GHOST/DARK): 247,352,261,289
        BOSS L20 CHERRIM (GRASS): 345,74,312,263
        BOSS L29 MACHOKE (FIGHTING): 27,371,92,398
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,157,219,43
        BOSS L44 SNEASEL (DARK/ICE): 8,280,156,98
        BOSS L48 WEAVILE (DARK/ICE): 400,279,115,263
        BOSS L66 WHISCASH (WATER/GROUND): 401,209,133,37
        BOSS L69 RAPIDASH (FIRE): 126,231,204,98
        BOSS L72 ALAKAZAM (PSYCHIC): 94,247,278,412
        BOSS L78 GARCHOMP (DRAGON/GROUND): 407,280,182,163
        BOSS L58 MAGMORTAR (FIRE): 126,223,109,270
        IMP  L7 STARLY (NORMAL/FLYING): 365,28,466,168
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 326,89,219,246
        IMP  L27 GROTLE (GRASS): 75,290,14,414
        IMP  L34 STARAVIA (NORMAL/FLYING): 36,332,297,228
        IMP  L36 STARAPTOR (NORMAL/FLYING): 36,211,355,228
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,444,334,282
        IMP  L47 RAPIDASH (FIRE): 53,24,156,104
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,211,355,17
        IMP  L25 KADABRA (PSYCHIC): 94,412,227,7
        IMP  L27 GROTLE (GRASS): 75,37,156,447
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,282,182,410
        IMP  L69 RAPIDASH (FIRE): 315,398,204,38
        IMP  L73 SNORLAX (NORMAL): 34,9,204,280
        IMP  L83 SNORLAX (NORMAL): 34,264,228,428
        IMP  L60 SKUNTANK (POISON/DARK): 242,38,262,269
        REG  L5 STARLY (NORMAL/FLYING): 365,228,310,164
        REG  L29 ZUBAT (POISON/FLYING): 365,428,290,445
        REG  L36 SWINUB (ICE/GROUND): 91,157,213,36
        REG  L6 GEODUDE (ROCK/GROUND): 205,33,360,335
        REG  L21 CARNIVINE (GRASS): 331,189,275,79
        REG  L21 DRIFLOON (GHOST/FLYING): 466,282,373,213
        REG  L36 MURKROW (DARK/FLYING): 65,399,180,375
        REG  L39 MURKROW (DARK/FLYING): 399,239,119,259
        REG  L58 PELIPPER (WATER/FLYING): 56,17,366,402
        REG  L32 EEVEE (NORMAL): 290,44,445,28
        REG  L48 SEAKING (WATER): 127,31,445,182
        REG  L42 GOLBAT (POISON/FLYING): 16,369,95,213
        REG  L23 BUIZEL (WATER): 291,317,316,228
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,435,49,161
        REG  L56 EMPOLEON (WATER/STEEL): 145,324,48,334
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 9,337,366,428
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 143,412,322,399
        BOSS L72 Lucario (FIGHTING/STEEL): 430,444,92,399
        BOSS L28 Flaaffy (ELECTRIC): 528,324,109,215
        BOSS L48 Haxorus (DRAGON): 530,89,104,332
        BOSS L50 Cofagrigus (GHOST): 247,399,50,277
        BOSS L67 Simipour (WATER): 56,154,269,270
        BOSS L76 Clefable (NORMAL): 304,473,133,345
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,332,282,98
        BOSS L49 Carracosta (WATER/ROCK): 444,58,504,411
        BOSS L56 Lucario (FIGHTING/STEEL): 430,421,46,242
        BOSS L73 Golurk (GROUND/GHOST): 414,444,334,223
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 428,404,156,168
        BOSS L75 Arcanine (FIRE): 53,406,241,179
        BOSS L75 Glaceon (ICE): 59,231,92,343
        IMP  L8 Tepig (FIRE): 52,33,156,174
        IMP  L48 Cryogonal (ICE): 59,324,109,163
        IMP  L23 Pansage (GRASS): 412,512,447,490
        IMP  L31 Tranquill (NORMAL/FLYING): 496,16,164,257
        IMP  L39 Unfezant (NORMAL/FLYING): 13,211,269,381
        IMP  L46 Cryogonal (ICE): 59,430,258,113
        IMP  L55 Unfezant (NORMAL/FLYING): 416,369,234,403
        IMP  L55 Simisear (FIRE): 257,417,216,276
        IMP  L62 Unfezant (NORMAL/FLYING): 253,332,234,95
        IMP  L62 Flygon (GROUND/DRAGON): 91,369,406,126
        IMP  L65 Unfezant (NORMAL/FLYING): 143,257,164,273
        IMP  L65 Eelektross (ELECTRIC): 528,242,113,401
        IMP  L41 Simisear (FIRE): 7,91,241,154
        IMP  L48 Unfezant (NORMAL/FLYING): 253,332,95,257
        IMP  L74 Klinklang (STEEL): 430,528,86,324
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 27,398,25,96
        REG  L63 Hitmonchan (FIGHTING): 327,418,92,203
        REG  L56 Unfezant (NORMAL/FLYING): 365,211,369,273
        REG  L47 Boldore (ROCK): 408,89,104,174
        REG  L45 Swinub (ICE/GROUND): 556,44,263,316
        REG  L32 Scolipede (BUG/POISON): 40,41,218,14
        REG  L65 Hitmontop (FIGHTING): 370,332,203,170
        REG  L52 Amoonguss (GRASS/POISON): 402,310,474,77
        REG  L64 Archeops (ROCK/FLYING): 88,512,156,366
        REG  L54 Metang (STEEL/PSYCHIC): 309,523,201,8
        REG  L60 Wooper (WATER/GROUND): 401,24,482,341
        REG  L67 Emboar (FIRE/FIGHTING): 249,36,535,89
        REG  L47 Krookodile (GROUND/DARK): 44,280,212,200
        REG  L25 Litwick (GHOST/FIRE): 52,412,216,114
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,583,184,305
        BOSS L41 Weezing (POISON): 188,351,390,247
        BOSS L5 Zigzagoon (NORMAL): 173,228,214,156
        BOSS L51 Dusclops (GHOST): 247,94,261,288
        BOSS L52 Froslass (ICE/GHOST): 8,87,86,324
        BOSS L57 Claydol (GROUND/PSYCHIC): 89,479,113,229
        BOSS L14 Machop (FIGHTING): 27,282,510,479
        BOSS L28 Slaking (NORMAL): 498,185,303,468
        BOSS L44 Whiscash (WATER/GROUND): 56,340,201,248
        BOSS L70 Sharpedo (WATER/DARK): 503,428,555,37
        BOSS L71 Dusknoir (GHOST): 247,264,50,207
        BOSS L73 Altaria (DRAGON/FLYING): 406,126,538,45
        BOSS L77 Carbink (ROCK/FAIRY): 585,88,201,263
        BOSS L57 Cradily (ROCK/GRASS): 402,89,109,482
        BOSS L57 Milotic (WATER): 56,58,95,54
        IMP  L18 Slugma (FIRE): 510,263,220,157
        IMP  L31 Wailmer (WATER): 352,340,174,58
        IMP  L18 Wailmer (WATER): 352,499,392,496
        IMP  L31 Shroomish (GRASS): 402,188,78,313
        IMP  L37 Swellow (NORMAL/FLYING): 290,168,97,45
        IMP  L37 Wailord (WATER): 291,58,133,442
        IMP  L46 Delcatty (NORMAL): 38,185,156,451
        IMP  L24 Shroomish (GRASS): 402,263,77,447
        IMP  L24 Slugma (FIRE): 510,281,88,263
        IMP  L32 Sharpedo (WATER/DARK): 399,340,182,362
        IMP  L55 Camerupt (FIRE/GROUND): 53,497,174,430
        IMP  L50 Blaziken (FIRE/FIGHTING): 315,496,97,530
        IMP  L50 Sceptile (GRASS): 437,9,406,1
        IMP  L64 Altaria (DRAGON/FLYING): 407,89,538,53
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 280,9,50,501
        REG  L4 Zigzagoon (NORMAL): 33,351,493,164
        REG  L25 Slugma (FIRE): 52,246,216,220
        REG  L39 Claydol (GROUND/PSYCHIC): 529,479,451,94
        REG  L36 Golbat (POISON/FLYING): 188,247,202,129
        REG  L34 Golbat (POISON/FLYING): 314,202,213,599
        REG  L33 Roselia (GRASS/POISON): 188,247,590,73
        REG  L43 Solrock (ROCK/PSYCHIC): 444,257,373,442
        REG  L49 Jellicent (WATER/GHOST): 61,202,506,148
        REG  L37 Skarmory (STEEL/FLYING): 65,372,28,196
        REG  L41 Clamperl (WATER): 330,59,300,237
        REG  L39 Tentacruel (WATER/POISON): 352,371,62,112
        REG  L48 Honchkrow (DARK/FLYING): 413,372,289,290
        REG  L53 Flygon (GROUND/DRAGON): 91,369,28,525
        REG  L23 Grimer (POISON): 398,8,286,425
        REG  L51 Mightyena (DARK): 399,231,590,33
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,479,227,523
        BOSS L47 Bewear (NORMAL/FIGHTING): 38,337,156,395
        BOSS L56 Probopass (ROCK/STEEL): 408,605,277,7
        BOSS L41 Golisopod (BUG/WATER): 534,398,191,675
        BOSS L66 Froslass (ICE/GHOST): 247,324,86,673
        BOSS L66 Mandibuzz (DARK/FLYING): 492,19,156,373
        BOSS L57 Dugtrio (GROUND/STEEL): 442,37,156,400
        BOSS L52 Sableye (DARK/GHOST): 492,196,277,472
        BOSS L65 Crobat (POISON/FLYING): 19,141,174,247
        BOSS L64 Masquerain (BUG/FLYING): 679,182,78,503
        BOSS L66 Hydreigon (DARK/DRAGON): 406,457,366,168
        BOSS L65 Gyarados (WATER/FLYING): 340,523,349,200
        BOSS L64 Camerupt (FIRE/GROUND): 436,430,133,246
        BOSS L70 Mewtwo (PSYCHIC): 473,247,219,396
        BOSS L63 Crabominable (FIGHTING/ICE): 665,89,334,223
        IMP  L6 Pichu (ELECTRIC): 351,574,227,604
        IMP  L15 Glaceon (ICE): 524,343,258,673
        IMP  L27 Salandit (POISON/FIRE): 481,406,92,343
        IMP  L28 Noibat (FLYING/DRAGON): 314,94,92,253
        IMP  L41 Noivern (FLYING/DRAGON): 19,399,92,103
        IMP  L70 Primarina (WATER/FAIRY): 664,237,227,472
        IMP  L67 Muk (POISON/DARK): 242,247,151,612
        IMP  L53 Zoroark (DARK): 492,326,184,369
        IMP  L68 Zoroark (DARK): 675,343,197,490
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 85,3,164,411
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 94,209,204,175
        IMP  L68 Snorlax (NORMAL): 38,57,7,157
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 435,324,417,447
        IMP  L51 Shiinotic (GRASS/FAIRY): 605,451,668,267
        IMP  L20 Poipole (POISON): 51,64,92,263
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 317,590,162,496
        REG  L69 Lapras (WATER/ICE): 56,573,104,54
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 209,574,321,97
        REG  L35 Marowak (FIRE/GHOST): 708,168,203,197
        REG  L5 Yungoos (NORMAL): 317,218,283,496
        REG  L5 Yungoos (NORMAL): 371,216,92,162
        REG  L55 Espeon (PSYCHIC): 60,324,287,605
        REG  L5 Yungoos (NORMAL): 371,216,237,317
        REG  L33 Zubat (POISON/FLYING): 17,369,207,44
        REG  L30 Minior (ROCK/FLYING): 444,94,129,446
        REG  L27 Trumbeak (NORMAL/FLYING): 365,280,282,103
        REG  L62 Persian (NORMAL): 304,400,421,164
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
