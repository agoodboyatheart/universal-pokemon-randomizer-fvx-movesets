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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,38,156,39
        BOSS L45 NIDOKING (POISON/GROUND): 89,59,164,36
        BOSS L55 HITMONLEE (FIGHTING): 26,5,156,96
        BOSS L12 GEODUDE (ROCK/GROUND): 69,99,156,118
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,129,156,106
        BOSS L24 RAICHU (ELECTRIC): 84,34,92,66
        BOSS L37 KOFFING (POISON): 124,85,164,108
        BOSS L43 WEEZING (POISON): 124,126,92,108
        BOSS L42 RAPIDASH (FIRE): 52,36,115,164
        BOSS L38 VENOMOTH (BUG/POISON): 141,72,78,13
        BOSS L53 CLOYSTER (WATER/ICE): 58,131,92,128
        BOSS L56 LAPRAS (WATER/ICE): 59,61,47,94
        BOSS L55 HAUNTER (GHOST/POISON): 101,138,156,95
        BOSS L56 DRAGONAIR (DRAGON): 85,59,92,43
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,38,86,87
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,18,115
        IMP  L15 ABRA (PSYCHIC): 69,99,156,86
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,129,92,115
        IMP  L18 KADABRA (PSYCHIC): 93,99,115,118
        IMP  L16 RATICATE (NORMAL): 158,61,164,102
        IMP  L25 WARTORTLE (WATER): 55,69,92,5
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,95,149
        IMP  L35 ALAKAZAM (PSYCHIC): 93,91,92,118
        IMP  L40 VENUSAUR (GRASS/POISON): 72,34,92,14
        IMP  L45 RHYHORN (GROUND/ROCK): 89,23,92,126
        IMP  L45 GYARADOS (WATER/FLYING): 61,85,156,58
        IMP  L47 GYARADOS (WATER/FLYING): 56,126,164,102
        IMP  L61 ARCANINE (FIRE): 53,34,97,43
        IMP  L63 ARCANINE (FIRE): 126,38,46,97
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,91,115,129
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,28,18
        REG  L18 MANKEY (FIGHTING): 69,6,157,156
        REG  L19 RATTATA (NORMAL): 98,61,104,92
        REG  L37 VULPIX (FIRE): 53,34,115,39
        REG  L29 WEEZING (POISON): 123,33,85,104
        REG  L31 CLOYSTER (WATER/ICE): 62,55,104,38
        REG  L26 MANKEY (FIGHTING): 69,66,154,118
        REG  L30 HORSEA (WATER): 61,58,38,92
        REG  L29 FEAROW (NORMAL/FLYING): 65,36,164,119
        REG  L70 GYARADOS (WATER/FLYING): 55,58,85,164
        REG  L17 MACHOP (FIGHTING): 69,2,118,92
        REG  L28 EKANS (POISON): 40,157,137,36
        REG  L39 DUGTRIO (GROUND): 89,157,38,45
        REG  L33 HAUNTER (GHOST/POISON): 101,109,102,122
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,189,197,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,113,228
        BOSS L31 PILOSWINE (ICE/GROUND): 189,246,197,34
        BOSS L37 DRAGONAIR (DRAGON): 82,53,97,192
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 72,246,182,23
        BOSS L46 MACHAMP (FIGHTING): 67,91,227,126
        BOSS L40 ARIADOS (BUG/POISON): 188,168,184,91
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,249,197,29
        BOSS L42 OMASTAR (ROCK/WATER): 55,62,114,131
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,61,240,213
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,29,174,133
        BOSS L33 ARIADOS (BUG/POISON): 141,101,184,154
        BOSS L45 MAGMAR (FIRE): 52,231,109,2
        BOSS L77 BLASTOISE (WATER): 56,58,46,54
        BOSS L58 ARCANINE (FIRE): 172,245,241,231
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,180
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,173
        IMP  L20 HAUNTER (GHOST/POISON): 122,168,156,173
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,18,98
        IMP  L32 MEGANIUM (GRASS): 22,246,115,34
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,129,174,237
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 84,161,174,203
        IMP  L35 HAUNTER (GHOST/POISON): 101,109,92,85
        IMP  L35 HAUNTER (GHOST/POISON): 101,85,95,207
        IMP  L43 GENGAR (GHOST/POISON): 122,9,156,8
        IMP  L43 ALAKAZAM (PSYCHIC): 93,168,113,203
        IMP  L43 ALAKAZAM (PSYCHIC): 93,7,113,105
        IMP  L46 ALAKAZAM (PSYCHIC): 60,9,50,134
        IMP  L50 TYPHLOSION (FIRE): 126,66,182,174
        IMP  L50 FERALIGATR (WATER): 56,91,46,44
        REG  L10 CHIKORITA (GRASS): 202,33,14,216
        REG  L20 QUAGSIRE (WATER/GROUND): 91,249,240,92
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,17,193,216
        REG  L25 NINETALES (FIRE): 52,185,180,109
        REG  L31 RHYDON (GROUND/ROCK): 205,189,23,192
        REG  L18 GROWLITHE (FIRE): 83,44,46,129
        REG  L23 GOLDEEN (WATER): 129,196,39,203
        REG  L28 TENTACOOL (WATER/POISON): 61,62,182,188
        REG  L28 POLIWHIRL (WATER): 145,3,240,170
        REG  L32 ONIX (ROCK/GROUND): 91,88,182,33
        REG  L6 VOLTORB (ELECTRIC): 205,207,174,129
        REG  L31 FURRET (NORMAL): 129,223,213,111
        REG  L42 GOLDUCK (WATER): 10,238,216,50
        REG  L23 PIKACHU (ELECTRIC): 9,189,203,179
        REG  L25 ELECTRODE (ELECTRIC): 205,103,218,33
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,184,305
        BOSS L53 WALREIN (ICE/WATER): 62,89,182,227
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,263,92,216
        BOSS L43 SEALEO (ICE/WATER): 301,352,258,38
        BOSS L44 CAMERUPT (FIRE/GROUND): 189,317,92,33
        BOSS L50 KABUTOPS (ROCK/WATER): 317,163,14,43
        BOSS L46 HITMONCHAN (FIGHTING): 183,317,339,203
        BOSS L50 MANECTRIC (ELECTRIC): 87,34,164,44
        BOSS L46 GROWLITHE (FIRE): 172,231,156,97
        BOSS L45 KANGASKHAN (NORMAL): 4,223,219,126
        BOSS L45 ALTARIA (DRAGON/FLYING): 332,211,114,126
        BOSS L58 SKARMORY (STEEL/FLYING): 332,31,46,216
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 88,129,113,207
        BOSS L56 LAPRAS (WATER/ICE): 196,182,321,351
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,280,334,36
        IMP  L34 MIGHTYENA (DARK): 168,305,184,36
        IMP  L40 GOLBAT (POISON/FLYING): 17,247,109,104
        IMP  L20 GROVYLE (GRASS): 331,225,156,9
        IMP  L29 LOMBRE (WATER/GRASS): 202,154,73,352
        IMP  L18 SLUGMA (FIRE): 52,157,151,203
        IMP  L29 PELIPPER (WATER/FLYING): 17,58,182,351
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,317,36,231
        IMP  L22 ZUBAT (POISON/FLYING): 332,247,269,289
        IMP  L47 ROSELIA (GRASS/POISON): 76,129,156,104
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,47,332
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,161,164,218
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 69,332,164,98
        IMP  L34 GROVYLE (GRASS): 72,9,182,228
        IMP  L34 MARSHTOMP (WATER/GROUND): 91,33,174,8
        IMP  L15 MUDKIP (WATER): 352,173,156,196
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 352,91,290,47
        REG  L26 MIGHTYENA (DARK): 168,247,207,289
        REG  L33 MACHOP (FIGHTING): 233,91,157,104
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,247,149,113
        REG  L35 PLUSLE (ELECTRIC): 351,189,5,69
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,205,216,319
        REG  L30 KOFFING (POISON): 124,168,269,108
        REG  L6 SEEDOT (GRASS): 202,91,206,73
        REG  L11 MARILL (WATER): 352,196,39,287
        REG  L26 LOMBRE (WATER/GRASS): 202,196,230,216
        REG  L29 XATU (PSYCHIC/FLYING): 94,202,211,216
        REG  L29 ZUBAT (POISON/FLYING): 17,129,18,102
        REG  L34 PELIPPER (WATER/FLYING): 17,58,218,102
        REG  L5 KYOGRE (WATER): 352,196,184,129
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,397,216,33
        BOSS L49 SCIZOR (BUG/STEEL): 442,400,201,318
        BOSS L52 HIPPOWDON (GROUND): 89,423,281,352
        BOSS L57 MAGMORTAR (FIRE): 394,264,269,5
        BOSS L58 SPIRITOMB (GHOST/DARK): 247,318,417,269
        BOSS L20 CHERRIM (GRASS): 345,205,320,74
        BOSS L29 MACHOKE (FIGHTING): 27,7,92,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,247,73,38
        BOSS L44 SNEASEL (DARK/ICE): 185,163,258,8
        BOSS L48 WEAVILE (DARK/ICE): 371,154,92,232
        BOSS L66 WHISCASH (WATER/GROUND): 56,263,182,157
        BOSS L69 RAPIDASH (FIRE): 315,36,164,398
        BOSS L72 ALAKAZAM (PSYCHIC): 428,247,92,357
        BOSS L78 GARCHOMP (DRAGON/GROUND): 239,89,92,28
        BOSS L58 MAGMORTAR (FIRE): 436,85,156,270
        IMP  L7 STARLY (NORMAL/FLYING): 31,365,297,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 89,397,157,326
        IMP  L27 GROTLE (GRASS): 402,34,174,414
        IMP  L34 STARAVIA (NORMAL/FLYING): 98,257,182,17
        IMP  L36 STARAPTOR (NORMAL/FLYING): 31,370,355,17
        IMP  L48 HERACROSS (BUG/FIGHTING): 279,400,334,89
        IMP  L47 RAPIDASH (FIRE): 394,24,97,231
        IMP  L42 STARAPTOR (NORMAL/FLYING): 17,36,92,237
        IMP  L25 KADABRA (PSYCHIC): 60,7,269,244
        IMP  L27 GROTLE (GRASS): 72,263,92,74
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,400,334,421
        IMP  L69 RAPIDASH (FIRE): 53,23,204,213
        IMP  L73 SNORLAX (NORMAL): 263,402,18,247
        IMP  L83 SNORLAX (NORMAL): 263,89,204,247
        IMP  L60 SKUNTANK (POISON/DARK): 228,163,262,188
        REG  L5 STARLY (NORMAL/FLYING): 98,365,28,164
        REG  L29 ZUBAT (POISON/FLYING): 17,185,428,259
        REG  L36 SWINUB (ICE/GROUND): 420,290,92,201
        REG  L6 GEODUDE (ROCK/GROUND): 205,175,360,189
        REG  L21 CARNIVINE (GRASS): 345,388,73,371
        REG  L21 DRIFLOON (GHOST/FLYING): 310,132,203,107
        REG  L36 MURKROW (DARK/FLYING): 168,257,237,213
        REG  L39 MURKROW (DARK/FLYING): 372,196,355,17
        REG  L58 PELIPPER (WATER/FLYING): 403,362,392,211
        REG  L32 EEVEE (NORMAL): 98,247,273,28
        REG  L48 SEAKING (WATER): 127,398,290,39
        REG  L42 GOLBAT (POISON/FLYING): 188,228,48,141
        REG  L23 BUIZEL (WATER): 453,129,316,92
        REG  L42 MAGNETON (ELECTRIC/STEEL): 351,430,278,203
        REG  L56 EMPOLEON (WATER/STEEL): 61,196,48,446
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,247
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 314,257,86,412
        BOSS L72 Lucario (FIGHTING/STEEL): 430,399,46,9
        BOSS L28 Flaaffy (ELECTRIC): 451,497,109,182
        BOSS L48 Haxorus (DRAGON): 525,401,349,398
        BOSS L50 Cofagrigus (GHOST): 101,412,347,114
        BOSS L67 Simipour (WATER): 55,512,58,242
        BOSS L76 Clefable (NORMAL): 304,309,115,361
        BOSS L28 Emolga (ELECTRIC/FLYING): 84,369,496,403
        BOSS L49 Carracosta (WATER/ROCK): 362,34,182,58
        BOSS L56 Lucario (FIGHTING/STEEL): 231,198,197,8
        BOSS L73 Golurk (GROUND/GHOST): 523,157,219,409
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,514,227,400
        BOSS L75 Arcanine (FIRE): 481,231,92,24
        BOSS L75 Glaceon (ICE): 196,485,215,28
        IMP  L8 Tepig (FIRE): 488,249,281,33
        IMP  L48 Cryogonal (ICE): 62,76,109,398
        IMP  L23 Pansage (GRASS): 345,512,320,154
        IMP  L31 Tranquill (NORMAL/FLYING): 332,263,355,197
        IMP  L39 Unfezant (NORMAL/FLYING): 365,263,95,257
        IMP  L46 Cryogonal (ICE): 59,324,151,258
        IMP  L55 Unfezant (NORMAL/FLYING): 365,211,297,182
        IMP  L55 Simisear (FIRE): 488,441,156,44
        IMP  L62 Unfezant (NORMAL/FLYING): 314,211,234,496
        IMP  L62 Flygon (GROUND/DRAGON): 337,91,92,185
        IMP  L65 Unfezant (NORMAL/FLYING): 143,253,269,297
        IMP  L65 Eelektross (ELECTRIC): 85,491,369,276
        IMP  L41 Simisear (FIRE): 83,411,281,317
        IMP  L48 Unfezant (NORMAL/FLYING): 314,297,92,211
        IMP  L74 Klinklang (STEEL): 544,528,508,393
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 27,299,170,216
        REG  L63 Hitmonchan (FIGHTING): 183,418,92,203
        REG  L56 Unfezant (NORMAL/FLYING): 332,369,98,516
        REG  L47 Boldore (ROCK): 444,523,201,213
        REG  L45 Swinub (ICE/GROUND): 556,89,316,34
        REG  L32 Scolipede (BUG/POISON): 41,371,398,263
        REG  L65 Hitmontop (FIGHTING): 136,529,67,116
        REG  L52 Amoonguss (GRASS/POISON): 474,492,148,380
        REG  L64 Archeops (ROCK/FLYING): 457,523,225,432
        REG  L54 Metang (STEEL/PSYCHIC): 309,523,113,8
        REG  L60 Wooper (WATER/GROUND): 523,401,482,24
        REG  L67 Emboar (FIRE/FIGHTING): 276,479,241,53
        REG  L47 Krookodile (GROUND/DARK): 328,228,446,332
        REG  L25 Litwick (GHOST/FIRE): 101,83,247,286
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 372,162,182,343
        BOSS L41 Weezing (POISON): 188,510,114,458
        BOSS L5 Zigzagoon (NORMAL): 351,182,213,352
        BOSS L51 Dusclops (GHOST): 325,185,182,216
        BOSS L52 Froslass (ICE/GHOST): 247,496,191,269
        BOSS L57 Claydol (GROUND/PSYCHIC): 326,529,201,218
        BOSS L14 Machop (FIGHTING): 612,523,182,227
        BOSS L28 Slaking (NORMAL): 154,523,46,185
        BOSS L44 Whiscash (WATER/GROUND): 330,428,133,349
        BOSS L70 Sharpedo (WATER/DARK): 372,89,97,352
        BOSS L71 Dusknoir (GHOST): 247,263,174,59
        BOSS L73 Altaria (DRAGON/FLYING): 365,605,114,215
        BOSS L77 Carbink (ROCK/FAIRY): 585,408,397,446
        BOSS L57 Cradily (ROCK/GRASS): 412,479,174,235
        BOSS L57 Milotic (WATER): 503,225,95,574
        IMP  L18 Slugma (FIRE): 488,88,182,123
        IMP  L31 Wailmer (WATER): 362,523,240,304
        IMP  L18 Wailmer (WATER): 250,310,174,392
        IMP  L31 Shroomish (GRASS): 331,474,77,235
        IMP  L37 Swellow (NORMAL/FLYING): 17,211,156,98
        IMP  L37 Wailord (WATER): 503,499,34,310
        IMP  L46 Delcatty (NORMAL): 38,528,219,313
        IMP  L24 Shroomish (GRASS): 202,474,77,263
        IMP  L24 Slugma (FIRE): 510,611,151,88
        IMP  L32 Sharpedo (WATER/DARK): 362,44,184,129
        IMP  L55 Camerupt (FIRE/GROUND): 488,430,495,317
        IMP  L50 Blaziken (FIRE/FIGHTING): 257,264,297,332
        IMP  L50 Sceptile (GRASS): 331,409,14,89
        IMP  L64 Altaria (DRAGON/FLYING): 407,523,538,54
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,8,261,530
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 53,414,207,334
        REG  L39 Claydol (GROUND/PSYCHIC): 414,263,379,322
        REG  L36 Golbat (POISON/FLYING): 305,257,355,18
        REG  L34 Golbat (POISON/FLYING): 332,211,269,48
        REG  L33 Roselia (GRASS/POISON): 345,247,267,390
        REG  L43 Solrock (ROCK/PSYCHIC): 473,496,149,88
        REG  L49 Jellicent (WATER/GHOST): 61,399,506,482
        REG  L37 Skarmory (STEEL/FLYING): 232,65,385,404
        REG  L41 Clamperl (WATER): 503,290,112,109
        REG  L39 Tentacruel (WATER/POISON): 51,362,103,367
        REG  L48 Honchkrow (DARK/FLYING): 413,372,86,182
        REG  L53 Flygon (GROUND/DRAGON): 407,522,213,257
        REG  L23 Grimer (POISON): 474,325,207,213
        REG  L51 Mightyena (DARK): 168,33,289,373
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 27,479,282,418
        BOSS L47 Bewear (NORMAL/FIGHTING): 276,317,46,371
        BOSS L56 Probopass (ROCK/STEEL): 408,521,334,7
        BOSS L41 Golisopod (BUG/WATER): 42,530,164,496
        BOSS L66 Froslass (ICE/GHOST): 420,29,269,85
        BOSS L66 Mandibuzz (DARK/FLYING): 332,317,355,185
        BOSS L57 Dugtrio (GROUND/STEEL): 91,317,446,430
        BOSS L52 Sableye (DARK/GHOST): 421,605,105,168
        BOSS L65 Crobat (POISON/FLYING): 143,369,95,98
        BOSS L64 Masquerain (BUG/FLYING): 324,503,483,218
        BOSS L66 Hydreigon (DARK/DRAGON): 242,430,115,444
        BOSS L65 Gyarados (WATER/FLYING): 127,423,349,406
        BOSS L64 Camerupt (FIRE/GROUND): 315,444,397,430
        BOSS L70 Mewtwo (PSYCHIC): 93,126,219,401
        BOSS L63 Crabominable (FIGHTING/ICE): 419,157,258,428
        IMP  L6 Pichu (ELECTRIC): 451,574,204,175
        IMP  L15 Glaceon (ICE): 524,496,197,45
        IMP  L27 Salandit (POISON/FIRE): 188,337,261,3
        IMP  L28 Noibat (FLYING/DRAGON): 314,257,156,48
        IMP  L41 Noivern (FLYING/DRAGON): 512,352,97,48
        IMP  L70 Primarina (WATER/FAIRY): 664,58,392,585
        IMP  L67 Muk (POISON/DARK): 44,305,262,380
        IMP  L53 Zoroark (DARK): 675,53,262,197
        IMP  L68 Zoroark (DARK): 539,326,343,340
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 528,473,277,324
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 451,574,204,278
        IMP  L68 Snorlax (NORMAL): 38,7,204,707
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 521,231,164,589
        IMP  L51 Shiinotic (GRASS/FAIRY): 202,451,113,148
        IMP  L20 Poipole (POISON): 474,64,204,92
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 371,164,213,237
        REG  L69 Lapras (WATER/ICE): 362,529,496,335
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 84,282,324,186
        REG  L35 Marowak (FIRE/GHOST): 708,125,479,282
        REG  L5 Yungoos (NORMAL): 279,590,33,168
        REG  L5 Yungoos (NORMAL): 279,213,496,371
        REG  L55 Espeon (PSYCHIC): 60,605,478,156
        REG  L5 Yungoos (NORMAL): 168,218,497,351
        REG  L33 Zubat (POISON/FLYING): 19,310,129,289
        REG  L30 Minior (ROCK/FLYING): 444,605,182,129
        REG  L27 Trumbeak (NORMAL/FLYING): 19,280,237,350
        REG  L62 Persian (NORMAL): 372,247,231,583
        REG  L14 Rattata (DARK/NORMAL): 168,33,382,179
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
