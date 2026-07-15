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
        BOSS L45 RHYHORN (GROUND/ROCK): 157,31,92,39
        BOSS L45 NIDOKING (POISON/GROUND): 89,58,164,85
        BOSS L55 HITMONLEE (FIGHTING): 27,130,92,96
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,92,90
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,129,86,156
        BOSS L24 RAICHU (ELECTRIC): 84,66,115,92
        BOSS L37 KOFFING (POISON): 124,85,164,108
        BOSS L43 WEEZING (POISON): 123,87,92,108
        BOSS L42 RAPIDASH (FIRE): 52,38,164,92
        BOSS L38 VENOMOTH (BUG/POISON): 141,60,50,13
        BOSS L53 CLOYSTER (WATER/ICE): 61,161,164,48
        BOSS L56 LAPRAS (WATER/ICE): 56,85,156,149
        BOSS L55 HAUNTER (GHOST/POISON): 122,94,156,92
        BOSS L56 DRAGONAIR (DRAGON): 59,34,86,82
        BOSS L62 DRAGONITE (DRAGON/FLYING): 21,59,97,43
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,99,164,102
        IMP  L15 ABRA (PSYCHIC): 99,66,156,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,99,18,164
        IMP  L18 KADABRA (PSYCHIC): 93,161,156,69
        IMP  L16 RATICATE (NORMAL): 158,61,156,164
        IMP  L25 WARTORTLE (WATER): 55,66,115,39
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,156,92
        IMP  L35 ALAKAZAM (PSYCHIC): 93,91,50,118
        IMP  L40 VENUSAUR (GRASS/POISON): 75,38,73,14
        IMP  L45 RHYHORN (GROUND/ROCK): 89,34,164,87
        IMP  L45 GYARADOS (WATER/FLYING): 56,59,164,82
        IMP  L47 GYARADOS (WATER/FLYING): 61,58,92,102
        IMP  L61 ARCANINE (FIRE): 53,91,156,104
        IMP  L63 ARCANINE (FIRE): 52,63,97,46
        IMP  L65 CHARIZARD (FIRE/FLYING): 83,91,92,66
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,28,164
        REG  L18 MANKEY (FIGHTING): 66,5,157,156
        REG  L19 RATTATA (NORMAL): 99,55,104,39
        REG  L37 VULPIX (FIRE): 52,36,156,39
        REG  L29 WEEZING (POISON): 123,33,104,156
        REG  L31 CLOYSTER (WATER/ICE): 58,36,156,92
        REG  L26 MANKEY (FIGHTING): 69,154,43,92
        REG  L30 HORSEA (WATER): 61,59,156,104
        REG  L29 FEAROW (NORMAL/FLYING): 31,65,119,45
        REG  L70 GYARADOS (WATER/FLYING): 56,85,102,156
        REG  L17 MACHOP (FIGHTING): 69,5,156,104
        REG  L28 EKANS (POISON): 40,157,137,43
        REG  L39 DUGTRIO (GROUND): 89,157,156,34
        REG  L33 HAUNTER (GHOST/POISON): 101,85,164,102
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,214
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,179,168
        BOSS L31 PILOSWINE (ICE/GROUND): 89,157,174,218
        BOSS L37 DRAGONAIR (DRAGON): 82,87,97,43
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 202,93,73,79
        BOSS L46 MACHAMP (FIGHTING): 233,8,227,193
        BOSS L40 ARIADOS (BUG/POISON): 40,101,156,184
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,7,197,113
        BOSS L42 OMASTAR (ROCK/WATER): 55,58,214,156
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,58,104,229
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,38,115,207
        BOSS L33 ARIADOS (BUG/POISON): 141,101,226,132
        BOSS L45 MAGMAR (FIRE): 126,238,197,108
        BOSS L77 BLASTOISE (WATER): 55,59,114,182
        BOSS L58 ARCANINE (FIRE): 172,231,156,92
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,149
        IMP  L12 GASTLY (GHOST/POISON): 122,202,95,168
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,114,213
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,182,141
        IMP  L32 MEGANIUM (GRASS): 75,34,73,213
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,86,182,49
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 87,161,86,174
        IMP  L35 HAUNTER (GHOST/POISON): 122,94,114,104
        IMP  L35 HAUNTER (GHOST/POISON): 101,174,92,87
        IMP  L43 GENGAR (GHOST/POISON): 101,223,114,7
        IMP  L43 ALAKAZAM (PSYCHIC): 60,7,227,207
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,105,244
        IMP  L46 ALAKAZAM (PSYCHIC): 60,9,50,227
        IMP  L50 TYPHLOSION (FIRE): 126,9,213,223
        IMP  L50 FERALIGATR (WATER): 55,157,174,184
        REG  L10 CHIKORITA (GRASS): 202,246,45,14
        REG  L20 QUAGSIRE (WATER/GROUND): 189,249,237,246
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 33,16,207,228
        REG  L25 NINETALES (FIRE): 52,91,185,98
        REG  L31 RHYDON (GROUND/ROCK): 157,30,228,223
        REG  L18 GROWLITHE (FIRE): 83,44,203,104
        REG  L23 GOLDEEN (WATER): 196,30,48,174
        REG  L28 TENTACOOL (WATER/POISON): 61,202,218,240
        REG  L28 POLIWHIRL (WATER): 61,189,170,114
        REG  L32 ONIX (ROCK/GROUND): 88,20,106,89
        REG  L6 VOLTORB (ELECTRIC): 173,205,216,156
        REG  L31 FURRET (NORMAL): 38,247,237,231
        REG  L42 GOLDUCK (WATER): 231,8,50,29
        REG  L23 PIKACHU (ELECTRIC): 84,98,179,86
        REG  L25 ELECTRODE (ELECTRIC): 173,205,156,104
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,36,164,281
        BOSS L53 WALREIN (ICE/WATER): 55,34,164,237
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,205,164,336
        BOSS L43 SEALEO (ICE/WATER): 59,174,104,89
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,89,156,213
        BOSS L50 KABUTOPS (ROCK/WATER): 157,341,201,280
        BOSS L46 HITMONCHAN (FIGHTING): 136,290,339,118
        BOSS L50 MANECTRIC (ELECTRIC): 87,44,316,98
        BOSS L46 GROWLITHE (FIRE): 172,36,182,316
        BOSS L45 KANGASKHAN (NORMAL): 146,247,156,50
        BOSS L45 ALTARIA (DRAGON/FLYING): 225,89,92,310
        BOSS L58 SKARMORY (STEEL/FLYING): 64,157,46,18
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 89,113,322,247
        BOSS L56 LAPRAS (WATER/ICE): 58,87,156,32
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,280,115,9
        IMP  L34 MIGHTYENA (DARK): 44,33,269,247
        IMP  L40 GOLBAT (POISON/FLYING): 188,247,174,17
        IMP  L20 GROVYLE (GRASS): 202,228,156,73
        IMP  L29 LOMBRE (WATER/GRASS): 75,310,235,218
        IMP  L18 SLUGMA (FIRE): 52,189,113,281
        IMP  L29 PELIPPER (WATER/FLYING): 352,351,156,45
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,36,59,55
        IMP  L22 ZUBAT (POISON/FLYING): 16,211,156,98
        IMP  L47 ROSELIA (GRASS/POISON): 76,188,92,207
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,97,237
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,161,156,104
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 223,315,156,164
        IMP  L34 GROVYLE (GRASS): 71,242,97,207
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,8,164,45
        IMP  L15 MUDKIP (WATER): 55,23,156,111
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 145,21,91,227
        REG  L26 MIGHTYENA (DARK): 44,305,269,343
        REG  L33 MACHOP (FIGHTING): 2,290,7,43
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,89,247,111
        REG  L35 PLUSLE (ELECTRIC): 9,223,268,207
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,218,203,205
        REG  L30 KOFFING (POISON): 124,247,213,218
        REG  L6 SEEDOT (GRASS): 331,91,106,207
        REG  L11 MARILL (WATER): 145,205,39,33
        REG  L26 LOMBRE (WATER/GRASS): 331,310,7,237
        REG  L29 XATU (PSYCHIC/FLYING): 65,185,43,203
        REG  L29 ZUBAT (POISON/FLYING): 17,310,185,48
        REG  L34 PELIPPER (WATER/FLYING): 55,63,254,207
        REG  L5 KYOGRE (WATER): 352,196,111,46
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 246,446,207,189
        BOSS L49 SCIZOR (BUG/STEEL): 232,276,226,405
        BOSS L52 HIPPOWDON (GROUND): 328,422,156,33
        BOSS L57 MAGMORTAR (FIRE): 436,85,156,207
        BOSS L58 SPIRITOMB (GHOST/DARK): 425,290,174,220
        BOSS L20 CHERRIM (GRASS): 345,73,74,263
        BOSS L29 MACHOKE (FIGHTING): 2,7,92,189
        BOSS L42 ABOMASNOW (GRASS/ICE): 420,402,73,38
        BOSS L44 SNEASEL (DARK/ICE): 185,420,164,269
        BOSS L48 WEAVILE (DARK/ICE): 185,279,156,216
        BOSS L66 WHISCASH (WATER/GROUND): 89,444,321,291
        BOSS L69 RAPIDASH (FIRE): 394,38,261,340
        BOSS L72 ALAKAZAM (PSYCHIC): 93,411,227,272
        BOSS L78 GARCHOMP (DRAGON/GROUND): 200,328,182,374
        BOSS L58 MAGMORTAR (FIRE): 394,411,182,270
        IMP  L7 STARLY (NORMAL/FLYING): 31,228,355,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,89,156,397
        IMP  L27 GROTLE (GRASS): 402,328,218,290
        IMP  L34 STARAVIA (NORMAL/FLYING): 36,369,92,28
        IMP  L36 STARAPTOR (NORMAL/FLYING): 263,370,156,257
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,30,164,218
        IMP  L47 RAPIDASH (FIRE): 394,36,237,398
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,370,182,216
        IMP  L25 KADABRA (PSYCHIC): 60,412,347,9
        IMP  L27 GROTLE (GRASS): 412,44,113,133
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,444,36,370
        IMP  L69 RAPIDASH (FIRE): 53,38,182,204
        IMP  L73 SNORLAX (NORMAL): 33,242,187,53
        IMP  L83 SNORLAX (NORMAL): 290,126,182,228
        IMP  L60 SKUNTANK (POISON/DARK): 228,38,269,91
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,228,247,202
        REG  L36 SWINUB (ICE/GROUND): 333,276,201,446
        REG  L6 GEODUDE (ROCK/GROUND): 317,189,175,216
        REG  L21 CARNIVINE (GRASS): 202,290,156,218
        REG  L21 DRIFLOON (GHOST/FLYING): 16,466,50,373
        REG  L36 MURKROW (DARK/FLYING): 399,143,92,119
        REG  L39 MURKROW (DARK/FLYING): 143,399,244,247
        REG  L58 PELIPPER (WATER/FLYING): 403,211,392,369
        REG  L32 EEVEE (NORMAL): 98,91,92,273
        REG  L48 SEAKING (WATER): 401,64,32,104
        REG  L42 GOLBAT (POISON/FLYING): 403,428,48,237
        REG  L23 BUIZEL (WATER): 453,228,226,91
        REG  L42 MAGNETON (ELECTRIC/STEEL): 443,84,115,199
        REG  L56 EMPOLEON (WATER/STEEL): 250,59,300,211
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,219
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,18,104
        BOSS L72 Lucario (FIGHTING/STEEL): 327,299,197,515
        BOSS L28 Flaaffy (ELECTRIC): 84,263,182,92
        BOSS L48 Haxorus (DRAGON): 337,89,269,163
        BOSS L50 Cofagrigus (GHOST): 506,412,164,334
        BOSS L67 Simipour (WATER): 503,280,182,270
        BOSS L76 Clefable (NORMAL): 304,247,446,236
        BOSS L28 Emolga (ELECTRIC/FLYING): 332,451,86,268
        BOSS L49 Carracosta (WATER/ROCK): 205,276,92,213
        BOSS L56 Lucario (FIGHTING/STEEL): 410,198,347,203
        BOSS L73 Golurk (GROUND/GHOST): 89,7,164,223
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 427,8,227,262
        BOSS L75 Arcanine (FIRE): 424,370,92,442
        BOSS L75 Glaceon (ICE): 423,485,204,514
        IMP  L8 Tepig (FIRE): 488,317,281,46
        IMP  L48 Cryogonal (ICE): 196,76,277,207
        IMP  L23 Pansage (GRASS): 412,512,156,43
        IMP  L31 Tranquill (NORMAL/FLYING): 143,369,273,197
        IMP  L39 Unfezant (NORMAL/FLYING): 98,257,197,269
        IMP  L46 Cryogonal (ICE): 62,324,164,512
        IMP  L55 Unfezant (NORMAL/FLYING): 143,211,92,164
        IMP  L55 Simisear (FIRE): 126,231,156,468
        IMP  L62 Unfezant (NORMAL/FLYING): 314,257,269,95
        IMP  L62 Flygon (GROUND/DRAGON): 523,444,257,263
        IMP  L65 Unfezant (NORMAL/FLYING): 416,403,156,45
        IMP  L65 Eelektross (ELECTRIC): 85,369,489,525
        IMP  L41 Simisear (FIRE): 7,231,156,213
        IMP  L48 Unfezant (NORMAL/FLYING): 63,211,516,314
        IMP  L74 Klinklang (STEEL): 544,263,475,104
        REG  L26 Blitzle (ELECTRIC): 351,263,24,156
        REG  L63 Hitmonlee (FIGHTING): 27,340,203,157
        REG  L63 Hitmonchan (FIGHTING): 327,5,207,104
        REG  L56 Unfezant (NORMAL/FLYING): 98,211,273,297
        REG  L47 Boldore (ROCK): 350,263,222,430
        REG  L45 Swinub (ICE/GROUND): 419,276,216,36
        REG  L32 Scolipede (BUG/POISON): 224,276,188,213
        REG  L65 Hitmontop (FIGHTING): 136,418,97,252
        REG  L52 Amoonguss (GRASS/POISON): 412,499,74,380
        REG  L64 Archeops (ROCK/FLYING): 88,369,92,184
        REG  L54 Metang (STEEL/PSYCHIC): 93,89,360,97
        REG  L60 Wooper (WATER/GROUND): 330,8,39,114
        REG  L67 Emboar (FIRE/FIGHTING): 7,292,111,33
        REG  L47 Krookodile (GROUND/DARK): 328,337,212,188
        REG  L25 Litwick (GHOST/FIRE): 510,499,148,151
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 44,162,269,373
        BOSS L41 Weezing (POISON): 188,126,114,60
        BOSS L5 Zigzagoon (NORMAL): 496,189,164,216
        BOSS L51 Dusclops (GHOST): 310,280,261,373
        BOSS L52 Froslass (ICE/GHOST): 8,310,164,43
        BOSS L57 Claydol (GROUND/PSYCHIC): 326,63,115,379
        BOSS L14 Machop (FIGHTING): 27,479,92,484
        BOSS L28 Slaking (NORMAL): 154,7,174,179
        BOSS L44 Whiscash (WATER/GROUND): 189,444,263,503
        BOSS L70 Sharpedo (WATER/DARK): 242,58,362,340
        BOSS L71 Dusknoir (GHOST): 310,7,220,286
        BOSS L73 Altaria (DRAGON/FLYING): 337,89,468,119
        BOSS L77 Carbink (ROCK/FAIRY): 408,585,347,115
        BOSS L57 Cradily (ROCK/GRASS): 246,202,220,416
        BOSS L57 Milotic (WATER): 56,239,175,231
        IMP  L18 Slugma (FIRE): 52,164,267,205
        IMP  L31 Wailmer (WATER): 503,340,38,89
        IMP  L18 Wailmer (WATER): 503,523,290,317
        IMP  L31 Shroomish (GRASS): 72,188,156,182
        IMP  L37 Swellow (NORMAL/FLYING): 17,211,182,590
        IMP  L37 Wailord (WATER): 323,38,164,54
        IMP  L46 Delcatty (NORMAL): 290,528,226,358
        IMP  L24 Shroomish (GRASS): 72,263,219,474
        IMP  L24 Slugma (FIRE): 52,281,157,496
        IMP  L32 Sharpedo (WATER/DARK): 44,398,269,56
        IMP  L55 Camerupt (FIRE/GROUND): 436,263,261,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 126,89,226,261
        IMP  L50 Sceptile (GRASS): 437,530,73,218
        IMP  L64 Altaria (DRAGON/FLYING): 225,290,349,366
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 409,8,339,263
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,496,113,174
        REG  L39 Claydol (GROUND/PSYCHIC): 89,444,94,397
        REG  L36 Golbat (POISON/FLYING): 314,310,18,141
        REG  L34 Golbat (POISON/FLYING): 314,211,48,129
        REG  L33 Roselia (GRASS/POISON): 76,326,390,320
        REG  L43 Solrock (ROCK/PSYCHIC): 444,126,106,356
        REG  L49 Jellicent (WATER/GHOST): 247,412,151,482
        REG  L37 Skarmory (STEEL/FLYING): 65,228,174,92
        REG  L41 Clamperl (WATER): 503,59,112,213
        REG  L39 Tentacruel (WATER/POISON): 503,398,132,182
        REG  L48 Honchkrow (DARK/FLYING): 65,310,218,228
        REG  L53 Flygon (GROUND/DRAGON): 523,211,104,48
        REG  L23 Grimer (POISON): 398,91,254,612
        REG  L51 Mightyena (DARK): 44,422,182,207
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 280,398,113,282
        BOSS L47 Bewear (NORMAL/FIGHTING): 37,693,14,213
        BOSS L56 Probopass (ROCK/STEEL): 430,521,164,92
        BOSS L41 Golisopod (BUG/WATER): 42,282,339,249
        BOSS L66 Froslass (ICE/GHOST): 419,416,164,358
        BOSS L66 Mandibuzz (DARK/FLYING): 185,365,432,260
        BOSS L57 Dugtrio (GROUND/STEEL): 232,523,262,251
        BOSS L52 Sableye (DARK/GHOST): 421,7,236,472
        BOSS L65 Crobat (POISON/FLYING): 19,440,18,212
        BOSS L64 Masquerain (BUG/FLYING): 405,58,564,16
        BOSS L66 Hydreigon (DARK/DRAGON): 525,89,355,512
        BOSS L65 Gyarados (WATER/FLYING): 127,200,349,259
        BOSS L64 Camerupt (FIRE/GROUND): 426,481,281,442
        BOSS L70 Mewtwo (PSYCHIC): 93,396,156,324
        BOSS L63 Crabominable (FIGHTING/ICE): 8,152,156,43
        IMP  L6 Pichu (ELECTRIC): 84,574,113,104
        IMP  L15 Glaceon (ICE): 196,500,215,92
        IMP  L27 Salandit (POISON/FIRE): 123,52,269,139
        IMP  L28 Noibat (FLYING/DRAGON): 314,71,355,48
        IMP  L41 Noivern (FLYING/DRAGON): 16,257,415,289
        IMP  L70 Primarina (WATER/FAIRY): 605,664,92,204
        IMP  L67 Muk (POISON/DARK): 242,280,1,53
        IMP  L53 Zoroark (DARK): 228,154,182,262
        IMP  L68 Zoroark (DARK): 185,326,262,253
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 84,411,227,683
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,252,115,216
        IMP  L68 Snorlax (NORMAL): 34,667,281,207
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 209,473,393,98
        IMP  L51 Shiinotic (GRASS/FAIRY): 202,138,147,236
        IMP  L20 Poipole (POISON): 474,343,92,289
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,218,259,497
        REG  L69 Lapras (WATER/ICE): 419,362,54,304
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 609,473,381,175
        REG  L35 Marowak (FIRE/GHOST): 708,707,37,203
        REG  L5 Yungoos (NORMAL): 371,104,237,279
        REG  L5 Yungoos (NORMAL): 351,213,497,371
        REG  L55 Espeon (PSYCHIC): 500,605,39,218
        REG  L5 Yungoos (NORMAL): 317,216,162,279
        REG  L33 Zubat (POISON/FLYING): 17,228,428,164
        REG  L30 Minior (ROCK/FLYING): 246,605,512,201
        REG  L27 Trumbeak (NORMAL/FLYING): 65,249,48,119
        REG  L62 Persian (NORMAL): 154,44,259,204
        REG  L14 Rattata (DARK/NORMAL): 343,168,179,289
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
