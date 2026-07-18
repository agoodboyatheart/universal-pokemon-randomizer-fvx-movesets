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
        BOSS L45 RHYHORN (GROUND/ROCK): 157,38,156,39
        BOSS L45 NIDOKING (POISON/GROUND): 89,58,164,5
        BOSS L55 HITMONLEE (FIGHTING): 27,34,92,118
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,129,115,86
        BOSS L24 RAICHU (ELECTRIC): 84,5,115,104
        BOSS L37 KOFFING (POISON): 124,85,164,108
        BOSS L43 WEEZING (POISON): 123,126,156,120
        BOSS L42 RAPIDASH (FIRE): 52,23,164,102
        BOSS L38 VENOMOTH (BUG/POISON): 141,72,77,149
        BOSS L53 CLOYSTER (WATER/ICE): 55,131,92,62
        BOSS L56 LAPRAS (WATER/ICE): 56,94,164,45
        BOSS L55 HAUNTER (GHOST/POISON): 122,94,164,92
        BOSS L56 DRAGONAIR (DRAGON): 34,85,86,115
        BOSS L62 DRAGONITE (DRAGON/FLYING): 58,61,97,43
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,99,164,28
        IMP  L15 ABRA (PSYCHIC): 69,99,115,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 98,129,156,104
        IMP  L18 KADABRA (PSYCHIC): 93,99,164,50
        IMP  L16 RATICATE (NORMAL): 158,61,92,39
        IMP  L25 WARTORTLE (WATER): 55,44,164,102
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,164,95
        IMP  L35 ALAKAZAM (PSYCHIC): 93,91,105,69
        IMP  L40 VENUSAUR (GRASS/POISON): 75,36,115,92
        IMP  L45 RHYHORN (GROUND/ROCK): 91,30,156,126
        IMP  L45 GYARADOS (WATER/FLYING): 61,58,156,43
        IMP  L47 GYARADOS (WATER/FLYING): 61,38,156,82
        IMP  L61 ARCANINE (FIRE): 126,36,115,46
        IMP  L63 ARCANINE (FIRE): 53,44,92,43
        IMP  L65 CHARIZARD (FIRE/FLYING): 52,66,92,91
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,28,18
        REG  L18 MANKEY (FIGHTING): 10,69,157,156
        REG  L19 RATTATA (NORMAL): 98,61,104,92
        REG  L37 VULPIX (FIRE): 53,129,46,39
        REG  L29 WEEZING (POISON): 123,85,92,104
        REG  L31 CLOYSTER (WATER/ICE): 61,62,48,164
        REG  L26 MANKEY (FIGHTING): 66,157,2,164
        REG  L30 HORSEA (WATER): 145,38,102,156
        REG  L29 FEAROW (NORMAL/FLYING): 31,64,156,119
        REG  L70 GYARADOS (WATER/FLYING): 56,126,115,43
        REG  L17 MACHOP (FIGHTING): 69,99,90,92
        REG  L28 EKANS (POISON): 40,36,137,156
        REG  L39 DUGTRIO (GROUND): 91,157,102,45
        REG  L33 HAUNTER (GHOST/POISON): 122,72,95,109
        """);
        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,189,197,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,113,228
        BOSS L31 PILOSWINE (ICE/GROUND): 189,246,197,34
        BOSS L37 DRAGONAIR (DRAGON): 82,53,97,192
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 72,121,73,92
        BOSS L46 MACHAMP (FIGHTING): 67,91,113,43
        BOSS L40 ARIADOS (BUG/POISON): 188,101,92,169
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,126,197,85
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,174,131
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,113,107
        BOSS L41 JUMPLUFF (GRASS/FLYING): 72,93,73,78
        BOSS L33 ARIADOS (BUG/POISON): 141,101,226,104
        BOSS L45 MAGMAR (FIRE): 7,9,241,112
        BOSS L77 BLASTOISE (WATER): 56,58,46,229
        BOSS L58 ARCANINE (FIRE): 53,225,241,36
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,180
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,173
        IMP  L20 HAUNTER (GHOST/POISON): 122,168,156,173
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,18,98
        IMP  L32 MEGANIUM (GRASS): 22,246,115,34
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,182,218,129
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,174,205
        IMP  L35 HAUNTER (GHOST/POISON): 247,174,149,85
        IMP  L35 HAUNTER (GHOST/POISON): 247,182,192,94
        IMP  L43 GENGAR (GHOST/POISON): 247,9,95,7
        IMP  L43 ALAKAZAM (PSYCHIC): 93,168,156,192
        IMP  L43 ALAKAZAM (PSYCHIC): 93,91,105,112
        IMP  L46 ALAKAZAM (PSYCHIC): 60,168,115,247
        IMP  L50 TYPHLOSION (FIRE): 126,9,182,91
        IMP  L50 FERALIGATR (WATER): 56,246,174,163
        REG  L10 CHIKORITA (GRASS): 22,246,203,189
        REG  L20 QUAGSIRE (WATER/GROUND): 91,249,213,55
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 33,189,197,174
        REG  L25 NINETALES (FIRE): 52,29,109,156
        REG  L31 RHYDON (GROUND/ROCK): 91,242,179,39
        REG  L18 GROWLITHE (FIRE): 52,225,91,44
        REG  L23 GOLDEEN (WATER): 30,64,92,240
        REG  L28 TENTACOOL (WATER/POISON): 61,202,114,219
        REG  L28 POLIWHIRL (WATER): 145,94,189,196
        REG  L32 ONIX (ROCK/GROUND): 89,157,46,201
        REG  L6 VOLTORB (ELECTRIC): 33,205,182,129
        REG  L31 FURRET (NORMAL): 29,205,9,174
        REG  L42 GOLDUCK (WATER): 196,91,210,95
        REG  L23 PIKACHU (ELECTRIC): 84,3,45,92
        REG  L25 ELECTRODE (ELECTRIC): 205,104,182,129
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,343,281,244
        BOSS L53 WALREIN (ICE/WATER): 62,89,227,317
        BOSS L26 CAMERUPT (FIRE/GROUND): 52,317,23,189
        BOSS L43 SEALEO (ICE/WATER): 301,38,281,157
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,317,46,36
        BOSS L50 KABUTOPS (ROCK/WATER): 157,341,156,163
        BOSS L46 HITMONCHAN (FIGHTING): 327,89,339,9
        BOSS L50 MANECTRIC (ELECTRIC): 351,29,182,156
        BOSS L46 GROWLITHE (FIRE): 172,37,164,219
        BOSS L45 KANGASKHAN (NORMAL): 23,44,182,247
        BOSS L45 ALTARIA (DRAGON/FLYING): 225,126,156,38
        BOSS L58 SKARMORY (STEEL/FLYING): 65,38,191,102
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 138,247,95,34
        BOSS L56 LAPRAS (WATER/ICE): 196,85,182,102
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,334,38
        IMP  L34 MIGHTYENA (DARK): 44,91,182,247
        IMP  L40 GOLBAT (POISON/FLYING): 314,211,92,168
        IMP  L20 GROVYLE (GRASS): 331,225,92,1
        IMP  L29 LOMBRE (WATER/GRASS): 55,252,73,75
        IMP  L18 SLUGMA (FIRE): 52,205,164,102
        IMP  L29 PELIPPER (WATER/FLYING): 17,168,182,211
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,317,92,223
        IMP  L22 ZUBAT (POISON/FLYING): 16,168,18,310
        IMP  L47 ROSELIA (GRASS/POISON): 331,38,78,189
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,47,290
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 87,161,92,156
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 53,24,241,10
        IMP  L34 GROVYLE (GRASS): 348,242,97,5
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,290,240,223
        IMP  L15 MUDKIP (WATER): 55,23,240,189
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 55,290,204,182
        REG  L26 MIGHTYENA (DARK): 44,305,237,281
        REG  L33 MACHOP (FIGHTING): 27,89,67,218
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,89,207,263
        REG  L35 PLUSLE (ELECTRIC): 85,231,227,268
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,33,48,205
        REG  L30 KOFFING (POISON): 188,168,156,247
        REG  L6 SEEDOT (GRASS): 331,206,73,106
        REG  L11 MARILL (WATER): 55,91,207,321
        REG  L26 LOMBRE (WATER/GRASS): 71,189,54,154
        REG  L29 XATU (PSYCHIC/FLYING): 65,168,211,115
        REG  L29 ZUBAT (POISON/FLYING): 17,141,289,207
        REG  L34 PELIPPER (WATER/FLYING): 55,196,98,189
        REG  L5 KYOGRE (WATER): 352,196,184,317
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,33,156,214
        BOSS L49 SCIZOR (BUG/STEEL): 418,458,97,334
        BOSS L52 HIPPOWDON (GROUND): 89,36,174,203
        BOSS L57 MAGMORTAR (FIRE): 315,264,182,241
        BOSS L58 SPIRITOMB (GHOST/DARK): 168,247,261,317
        BOSS L20 CHERRIM (GRASS): 202,320,312,263
        BOSS L29 MACHOKE (FIGHTING): 279,168,92,53
        BOSS L42 ABOMASNOW (GRASS/ICE): 345,89,320,181
        BOSS L44 SNEASEL (DARK/ICE): 420,458,115,185
        BOSS L48 WEAVILE (DARK/ICE): 371,91,258,458
        BOSS L66 WHISCASH (WATER/GROUND): 56,196,164,36
        BOSS L69 RAPIDASH (FIRE): 394,38,97,39
        BOSS L72 ALAKAZAM (PSYCHIC): 94,247,105,272
        BOSS L78 GARCHOMP (DRAGON/GROUND): 337,424,14,163
        BOSS L58 MAGMORTAR (FIRE): 257,411,156,317
        IMP  L7 STARLY (NORMAL/FLYING): 31,228,156,45
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 247,334,115,157
        IMP  L27 GROTLE (GRASS): 412,414,74,34
        IMP  L34 STARAVIA (NORMAL/FLYING): 36,211,297,466
        IMP  L36 STARAPTOR (NORMAL/FLYING): 31,332,97,104
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,157,92,91
        IMP  L47 RAPIDASH (FIRE): 315,231,95,33
        IMP  L42 STARAPTOR (NORMAL/FLYING): 413,370,18,466
        IMP  L25 KADABRA (PSYCHIC): 60,247,269,168
        IMP  L27 GROTLE (GRASS): 402,263,446,44
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,421,14,444
        IMP  L69 RAPIDASH (FIRE): 394,23,204,32
        IMP  L73 SNORLAX (NORMAL): 38,402,281,317
        IMP  L83 SNORLAX (NORMAL): 34,228,281,278
        IMP  L60 SKUNTANK (POISON/DARK): 242,163,262,126
        REG  L5 STARLY (NORMAL/FLYING): 98,365,28,164
        REG  L29 ZUBAT (POISON/FLYING): 17,185,466,289
        REG  L36 SWINUB (ICE/GROUND): 89,420,174,36
        REG  L6 GEODUDE (ROCK/GROUND): 205,33,360,213
        REG  L21 CARNIVINE (GRASS): 22,282,230,78
        REG  L21 DRIFLOON (GHOST/FLYING): 466,318,180,168
        REG  L36 MURKROW (DARK/FLYING): 332,211,216,86
        REG  L39 MURKROW (DARK/FLYING): 64,94,375,18
        REG  L58 PELIPPER (WATER/FLYING): 403,196,216,156
        REG  L32 EEVEE (NORMAL): 98,231,273,445
        REG  L48 SEAKING (WATER): 127,64,213,104
        REG  L42 GOLBAT (POISON/FLYING): 305,413,213,269
        REG  L23 BUIZEL (WATER): 453,280,45,164
        REG  L42 MAGNETON (ELECTRIC/STEEL): 85,324,103,443
        REG  L56 EMPOLEON (WATER/STEEL): 56,232,213,65
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,247
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,430,182,119
        BOSS L72 Lucario (FIGHTING/STEEL): 418,198,339,299
        BOSS L28 Flaaffy (ELECTRIC): 84,324,178,219
        BOSS L48 Haxorus (DRAGON): 200,280,156,421
        BOSS L50 Cofagrigus (GHOST): 247,371,174,285
        BOSS L67 Simipour (WATER): 503,496,164,276
        BOSS L76 Clefable (NORMAL): 304,309,361,94
        BOSS L28 Emolga (ELECTRIC/FLYING): 84,324,207,496
        BOSS L49 Carracosta (WATER/ROCK): 317,89,201,401
        BOSS L56 Lucario (FIGHTING/STEEL): 430,247,197,170
        BOSS L73 Golurk (GROUND/GHOST): 89,9,446,237
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 280,163,50,210
        BOSS L75 Arcanine (FIRE): 315,76,219,422
        BOSS L75 Glaceon (ICE): 524,500,273,249
        IMP  L8 Tepig (FIRE): 488,317,46,249
        IMP  L48 Cryogonal (ICE): 58,430,114,156
        IMP  L23 Pansage (GRASS): 402,154,182,269
        IMP  L31 Tranquill (NORMAL/FLYING): 263,211,355,381
        IMP  L39 Unfezant (NORMAL/FLYING): 263,211,297,314
        IMP  L46 Cryogonal (ICE): 420,430,109,229
        IMP  L55 Unfezant (NORMAL/FLYING): 98,365,92,156
        IMP  L55 Simisear (FIRE): 126,276,133,371
        IMP  L62 Unfezant (NORMAL/FLYING): 332,63,516,257
        IMP  L62 Flygon (GROUND/DRAGON): 89,157,164,257
        IMP  L65 Unfezant (NORMAL/FLYING): 365,496,234,516
        IMP  L65 Eelektross (ELECTRIC): 85,409,182,7
        IMP  L41 Simisear (FIRE): 7,343,269,371
        IMP  L48 Unfezant (NORMAL/FLYING): 143,253,234,273
        IMP  L74 Klinklang (STEEL): 544,11,86,103
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 370,299,263,168
        REG  L63 Hitmonchan (FIGHTING): 327,252,164,444
        REG  L56 Unfezant (NORMAL/FLYING): 314,369,216,211
        REG  L47 Boldore (ROCK): 157,29,92,222
        REG  L45 Swinub (ICE/GROUND): 333,89,113,115
        REG  L32 Scolipede (BUG/POISON): 41,205,188,97
        REG  L65 Hitmontop (FIGHTING): 27,332,193,252
        REG  L52 Amoonguss (GRASS/POISON): 188,72,380,147
        REG  L64 Archeops (ROCK/FLYING): 17,211,44,479
        REG  L54 Metang (STEEL/PSYCHIC): 418,280,97,360
        REG  L60 Wooper (WATER/GROUND): 401,24,523,281
        REG  L67 Emboar (FIRE/FIGHTING): 292,123,269,535
        REG  L47 Krookodile (GROUND/DARK): 89,372,38,269
        REG  L25 Litwick (GHOST/FIRE): 481,123,151,109
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,36,281,28
        BOSS L41 Weezing (POISON): 188,247,269,496
        BOSS L5 Zigzagoon (NORMAL): 352,86,451,237
        BOSS L51 Dusclops (GHOST): 466,612,50,89
        BOSS L52 Froslass (ICE/GHOST): 310,358,86,148
        BOSS L57 Claydol (GROUND/PSYCHIC): 91,444,397,263
        BOSS L14 Machop (FIGHTING): 612,282,164,67
        BOSS L28 Slaking (NORMAL): 154,400,133,351
        BOSS L44 Whiscash (WATER/GROUND): 341,428,92,263
        BOSS L70 Sharpedo (WATER/DARK): 372,398,92,196
        BOSS L71 Dusknoir (GHOST): 425,264,269,196
        BOSS L73 Altaria (DRAGON/FLYING): 337,290,114,195
        BOSS L77 Carbink (ROCK/FAIRY): 157,585,343,94
        BOSS L57 Cradily (ROCK/GRASS): 72,362,164,89
        BOSS L57 Milotic (WATER): 503,59,392,240
        IMP  L18 Slugma (FIRE): 510,237,113,88
        IMP  L31 Wailmer (WATER): 362,523,290,340
        IMP  L18 Wailmer (WATER): 250,499,156,317
        IMP  L31 Shroomish (GRASS): 331,409,14,188
        IMP  L37 Swellow (NORMAL/FLYING): 17,369,164,432
        IMP  L37 Wailord (WATER): 323,58,111,237
        IMP  L46 Delcatty (NORMAL): 3,358,273,196
        IMP  L24 Shroomish (GRASS): 331,474,92,206
        IMP  L24 Slugma (FIRE): 510,88,164,499
        IMP  L32 Sharpedo (WATER/DARK): 503,340,184,305
        IMP  L55 Camerupt (FIRE/GROUND): 488,430,201,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 510,98,174,136
        IMP  L50 Sceptile (GRASS): 72,404,225,306
        IMP  L64 Altaria (DRAGON/FLYING): 365,304,605,200
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,425,109,345
        REG  L4 Zigzagoon (NORMAL): 237,351,493,164
        REG  L25 Slugma (FIRE): 52,499,151,246
        REG  L39 Claydol (GROUND/PSYCHIC): 93,605,529,379
        REG  L36 Golbat (POISON/FLYING): 332,141,218,432
        REG  L34 Golbat (POISON/FLYING): 314,257,109,18
        REG  L33 Roselia (GRASS/POISON): 345,42,73,474
        REG  L43 Solrock (ROCK/PSYCHIC): 444,263,377,149
        REG  L49 Jellicent (WATER/GHOST): 362,466,156,220
        REG  L37 Skarmory (STEEL/FLYING): 232,314,385,404
        REG  L41 Clamperl (WATER): 250,290,104,334
        REG  L39 Tentacruel (WATER/POISON): 145,491,290,112
        REG  L48 Honchkrow (DARK/FLYING): 17,196,168,297
        REG  L53 Flygon (GROUND/DRAGON): 406,89,211,185
        REG  L23 Grimer (POISON): 474,189,286,114
        REG  L51 Mightyena (DARK): 371,91,305,416
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,479,227,523
        BOSS L47 Bewear (NORMAL/FIGHTING): 395,523,46,8
        BOSS L56 Probopass (ROCK/STEEL): 442,85,182,161
        BOSS L41 Golisopod (BUG/WATER): 210,534,334,188
        BOSS L66 Froslass (ICE/GHOST): 420,371,694,94
        BOSS L66 Mandibuzz (DARK/FLYING): 492,198,164,365
        BOSS L57 Dugtrio (GROUND/STEEL): 91,232,334,45
        BOSS L52 Sableye (DARK/GHOST): 492,332,105,252
        BOSS L65 Crobat (POISON/FLYING): 19,440,114,141
        BOSS L64 Masquerain (BUG/FLYING): 314,145,366,60
        BOSS L66 Hydreigon (DARK/DRAGON): 693,430,46,218
        BOSS L65 Gyarados (WATER/FLYING): 127,423,349,82
        BOSS L64 Camerupt (FIRE/GROUND): 414,157,46,184
        BOSS L70 Mewtwo (PSYCHIC): 94,8,92,280
        BOSS L63 Crabominable (FIGHTING/ICE): 223,665,133,523
        IMP  L6 Pichu (ELECTRIC): 351,574,227,604
        IMP  L15 Glaceon (ICE): 196,352,174,92
        IMP  L27 Salandit (POISON/FIRE): 188,52,156,3
        IMP  L28 Noibat (FLYING/DRAGON): 406,253,97,399
        IMP  L41 Noivern (FLYING/DRAGON): 314,399,269,421
        IMP  L70 Primarina (WATER/FAIRY): 585,61,182,58
        IMP  L67 Muk (POISON/DARK): 693,305,182,151
        IMP  L53 Zoroark (DARK): 555,326,92,332
        IMP  L68 Zoroark (DARK): 400,490,184,590
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 521,3,92,589
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 84,252,113,589
        IMP  L68 Snorlax (NORMAL): 38,7,204,707
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 85,168,227,343
        IMP  L51 Shiinotic (GRASS/FAIRY): 605,235,213,451
        IMP  L20 Poipole (POISON): 474,64,92,497
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 497,371,104,216
        REG  L69 Lapras (WATER/ICE): 420,406,351,684
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 473,231,113,673
        REG  L35 Marowak (FIRE/GHOST): 708,125,479,116
        REG  L5 Yungoos (NORMAL): 173,371,156,214
        REG  L5 Yungoos (NORMAL): 173,317,156,182
        REG  L55 Espeon (PSYCHIC): 485,129,273,197
        REG  L5 Yungoos (NORMAL): 279,213,259,162
        REG  L33 Zubat (POISON/FLYING): 16,185,98,369
        REG  L30 Minior (ROCK/FLYING): 444,369,397,156
        REG  L27 Trumbeak (NORMAL/FLYING): 31,280,369,103
        REG  L62 Persian (NORMAL): 154,247,282,269
        REG  L14 Rattata (DARK/NORMAL): 555,279,196,179
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
