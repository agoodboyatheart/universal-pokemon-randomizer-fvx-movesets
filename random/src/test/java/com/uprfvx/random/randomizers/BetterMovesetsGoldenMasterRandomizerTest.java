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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,30,156,85
        BOSS L45 NIDOKING (POISON/GROUND): 89,59,164,116
        BOSS L55 HITMONLEE (FIGHTING): 136,5,92,96
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,92
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,161,164,86
        BOSS L24 RAICHU (ELECTRIC): 84,66,92,6
        BOSS L37 KOFFING (POISON): 124,126,164,108
        BOSS L43 WEEZING (POISON): 124,153,92,87
        BOSS L42 RAPIDASH (FIRE): 126,23,164,39
        BOSS L38 VENOMOTH (BUG/POISON): 60,72,78,18
        BOSS L53 CLOYSTER (WATER/ICE): 59,120,115,110
        BOSS L56 LAPRAS (WATER/ICE): 58,87,92,36
        BOSS L55 HAUNTER (GHOST/POISON): 101,85,95,104
        BOSS L56 DRAGONAIR (DRAGON): 59,85,97,43
        BOSS L62 DRAGONITE (DRAGON/FLYING): 58,36,86,61
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,99,92,18
        IMP  L15 ABRA (PSYCHIC): 66,161,86,102
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,13,156,18
        IMP  L18 KADABRA (PSYCHIC): 93,161,86,69
        IMP  L16 RATICATE (NORMAL): 33,55,156,39
        IMP  L25 WARTORTLE (WATER): 145,69,115,164
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,95,102
        IMP  L35 ALAKAZAM (PSYCHIC): 94,5,105,86
        IMP  L40 VENUSAUR (GRASS/POISON): 72,34,73,77
        IMP  L45 RHYHORN (GROUND/ROCK): 89,157,92,23
        IMP  L45 GYARADOS (WATER/FLYING): 56,85,92,58
        IMP  L47 GYARADOS (WATER/FLYING): 61,58,156,115
        IMP  L61 ARCANINE (FIRE): 53,34,92,91
        IMP  L63 ARCANINE (FIRE): 53,91,46,115
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,91,115,82
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,115,28
        REG  L18 MANKEY (FIGHTING): 66,129,102,164
        REG  L19 RATTATA (NORMAL): 98,61,104,92
        REG  L37 VULPIX (FIRE): 53,98,104,109
        REG  L29 WEEZING (POISON): 123,33,102,92
        REG  L31 CLOYSTER (WATER/ICE): 58,36,104,156
        REG  L26 MANKEY (FIGHTING): 69,154,164,43
        REG  L30 HORSEA (WATER): 61,59,102,108
        REG  L29 FEAROW (NORMAL/FLYING): 99,65,45,18
        REG  L70 GYARADOS (WATER/FLYING): 61,87,92,115
        REG  L17 MACHOP (FIGHTING): 66,157,156,99
        REG  L28 EKANS (POISON): 40,157,137,35
        REG  L39 DUGTRIO (GROUND): 91,34,28,104
        REG  L33 HAUNTER (GHOST/POISON): 101,94,164,156
        """);
        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,228
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,168,211
        BOSS L31 PILOSWINE (ICE/GROUND): 89,157,174,196
        BOSS L37 DRAGONAIR (DRAGON): 225,126,97,21
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 72,94,236,23
        BOSS L46 MACHAMP (FIGHTING): 233,29,184,203
        BOSS L40 ARIADOS (BUG/POISON): 188,101,50,168
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,223,113,85
        BOSS L42 OMASTAR (ROCK/WATER): 61,196,168,21
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,109,113
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,29,73,39
        BOSS L33 ARIADOS (BUG/POISON): 188,101,182,207
        BOSS L45 MAGMAR (FIRE): 7,9,5,168
        BOSS L77 BLASTOISE (WATER): 56,8,114,29
        BOSS L58 ARCANINE (FIRE): 126,225,219,156
        IMP  L12 GASTLY (GHOST/POISON): 122,202,174,168
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,180
        IMP  L20 HAUNTER (GHOST/POISON): 247,168,95,218
        IMP  L20 ZUBAT (POISON/FLYING): 16,98,197,185
        IMP  L32 MEGANIUM (GRASS): 202,246,77,115
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,33,86,182
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,174,109
        IMP  L35 HAUNTER (GHOST/POISON): 101,85,109,202
        IMP  L43 GENGAR (GHOST/POISON): 247,7,114,85
        IMP  L43 ALAKAZAM (PSYCHIC): 60,168,105,244
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,113,112
        IMP  L46 ALAKAZAM (PSYCHIC): 94,247,182,218
        IMP  L50 TYPHLOSION (FIRE): 7,9,174,197
        IMP  L50 FERALIGATR (WATER): 56,246,240,196
        REG  L10 CHIKORITA (GRASS): 202,189,203,246
        REG  L20 QUAGSIRE (WATER/GROUND): 189,246,104,8
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,182,218
        REG  L25 NINETALES (FIRE): 52,50,219,91
        REG  L31 RHYDON (GROUND/ROCK): 157,30,182,218
        REG  L18 GROWLITHE (FIRE): 52,225,174,219
        REG  L23 GOLDEEN (WATER): 64,173,114,156
        REG  L28 TENTACOOL (WATER/POISON): 61,62,218,240
        REG  L28 POLIWHIRL (WATER): 145,114,207,196
        REG  L32 ONIX (ROCK/GROUND): 157,231,92,156
        REG  L6 VOLTORB (ELECTRIC): 205,218,237,129
        REG  L31 FURRET (NORMAL): 21,223,8,216
        REG  L42 GOLDUCK (WATER): 29,238,218,240
        REG  L23 PIKACHU (ELECTRIC): 84,129,197,213
        REG  L25 ELECTRODE (ELECTRIC): 49,205,182,207
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 168,36,46,218
        BOSS L53 WALREIN (ICE/WATER): 62,231,258,90
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,46,52,317
        BOSS L43 SEALEO (ICE/WATER): 62,182,317,231
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,23,92,201
        BOSS L50 KABUTOPS (ROCK/WATER): 317,69,92,282
        BOSS L46 HITMONCHAN (FIGHTING): 264,89,164,97
        BOSS L50 MANECTRIC (ELECTRIC): 85,263,46,231
        BOSS L46 GROWLITHE (FIRE): 315,34,182,242
        BOSS L45 KANGASKHAN (NORMAL): 146,247,46,7
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,126,219,54
        BOSS L58 SKARMORY (STEEL/FLYING): 143,38,182,43
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 317,89,115,219
        BOSS L56 LAPRAS (WATER/ICE): 58,87,92,104
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,188,164,247
        IMP  L34 MIGHTYENA (DARK): 168,343,184,207
        IMP  L40 GOLBAT (POISON/FLYING): 188,228,164,289
        IMP  L20 GROVYLE (GRASS): 71,225,73,98
        IMP  L29 LOMBRE (WATER/GRASS): 202,252,235,55
        IMP  L18 SLUGMA (FIRE): 52,281,113,290
        IMP  L29 PELIPPER (WATER/FLYING): 16,351,97,352
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,36,104,58
        IMP  L22 ZUBAT (POISON/FLYING): 16,247,164,290
        IMP  L47 ROSELIA (GRASS/POISON): 202,34,320,188
        IMP  L53 ALTARIA (DRAGON/FLYING): 225,53,97,102
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,161,86,102
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 315,280,14,163
        IMP  L34 GROVYLE (GRASS): 348,9,73,306
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,231,182,34
        IMP  L15 MUDKIP (WATER): 352,189,33,196
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,201
        REG  L26 MARILL (WATER): 145,21,280,227
        REG  L26 MIGHTYENA (DARK): 44,310,305,28
        REG  L33 MACHOP (FIGHTING): 233,25,193,203
        REG  L41 SOLROCK (ROCK/PSYCHIC): 317,89,247,115
        REG  L35 PLUSLE (ELECTRIC): 209,223,227,118
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,319,237,129
        REG  L30 KOFFING (POISON): 188,85,139,104
        REG  L6 SEEDOT (GRASS): 331,203,102,91
        REG  L11 MARILL (WATER): 145,189,156,47
        REG  L26 LOMBRE (WATER/GRASS): 55,331,213,189
        REG  L29 XATU (PSYCHIC/FLYING): 64,202,129,101
        REG  L29 ZUBAT (POISON/FLYING): 17,310,98,141
        REG  L34 PELIPPER (WATER/FLYING): 332,58,203,290
        REG  L5 KYOGRE (WATER): 352,351,156,196
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 205,33,397,207
        BOSS L49 SCIZOR (BUG/STEEL): 404,280,97,163
        BOSS L52 HIPPOWDON (GROUND): 89,38,174,445
        BOSS L57 MAGMORTAR (FIRE): 436,85,269,108
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,317,95,220
        BOSS L20 CHERRIM (GRASS): 345,320,205,33
        BOSS L29 MACHOKE (FIGHTING): 27,371,227,398
        BOSS L42 ABOMASNOW (GRASS/ICE): 402,280,320,89
        BOSS L44 SNEASEL (DARK/ICE): 8,264,14,282
        BOSS L48 WEAVILE (DARK/ICE): 420,458,115,404
        BOSS L66 WHISCASH (WATER/GROUND): 414,209,240,196
        BOSS L69 RAPIDASH (FIRE): 394,398,95,97
        BOSS L72 ALAKAZAM (PSYCHIC): 60,412,227,134
        BOSS L78 GARCHOMP (DRAGON/GROUND): 89,421,182,444
        BOSS L58 MAGMORTAR (FIRE): 436,85,164,270
        IMP  L7 STARLY (NORMAL/FLYING): 33,228,297,365
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 248,247,92,246
        IMP  L27 GROTLE (GRASS): 75,328,219,104
        IMP  L34 STARAVIA (NORMAL/FLYING): 332,369,297,257
        IMP  L36 STARAPTOR (NORMAL/FLYING): 38,369,18,332
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,91,339,175
        IMP  L47 RAPIDASH (FIRE): 394,38,241,231
        IMP  L42 STARAPTOR (NORMAL/FLYING): 36,168,97,28
        IMP  L25 KADABRA (PSYCHIC): 60,324,92,247
        IMP  L27 GROTLE (GRASS): 331,290,14,44
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,332,339,282
        IMP  L69 RAPIDASH (FIRE): 394,23,182,45
        IMP  L73 SNORLAX (NORMAL): 34,228,174,247
        IMP  L83 SNORLAX (NORMAL): 263,85,133,442
        IMP  L60 SKUNTANK (POISON/DARK): 400,163,184,262
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 17,228,369,104
        REG  L36 SWINUB (ICE/GROUND): 333,89,317,258
        REG  L6 GEODUDE (ROCK/GROUND): 189,246,201,216
        REG  L21 CARNIVINE (GRASS): 331,282,207,275
        REG  L21 DRIFLOON (GHOST/FLYING): 247,189,278,168
        REG  L36 MURKROW (DARK/FLYING): 399,211,297,86
        REG  L39 MURKROW (DARK/FLYING): 399,143,103,375
        REG  L58 PELIPPER (WATER/FLYING): 403,196,182,48
        REG  L32 EEVEE (NORMAL): 343,247,203,313
        REG  L48 SEAKING (WATER): 291,340,196,282
        REG  L42 GOLBAT (POISON/FLYING): 403,247,109,18
        REG  L23 BUIZEL (WATER): 55,317,280,98
        REG  L42 MAGNETON (ELECTRIC/STEEL): 209,153,324,278
        REG  L56 EMPOLEON (WATER/STEEL): 362,282,196,300
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,94
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,86,60
        BOSS L72 Lucario (FIGHTING/STEEL): 410,523,164,398
        BOSS L28 Flaaffy (ELECTRIC): 521,324,182,45
        BOSS L48 Haxorus (DRAGON): 337,401,184,400
        BOSS L50 Cofagrigus (GHOST): 506,94,262,417
        BOSS L67 Simipour (WATER): 362,512,240,270
        BOSS L76 Clefable (NORMAL): 304,85,361,383
        BOSS L28 Emolga (ELECTRIC/FLYING): 84,403,113,228
        BOSS L49 Carracosta (WATER/ROCK): 401,157,174,89
        BOSS L56 Lucario (FIGHTING/STEEL): 430,299,197,242
        BOSS L73 Golurk (GROUND/GHOST): 523,409,174,222
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 280,427,109,85
        BOSS L75 Arcanine (FIRE): 488,406,234,442
        BOSS L75 Glaceon (ICE): 59,401,197,273
        IMP  L8 Tepig (FIRE): 488,317,46,33
        IMP  L48 Cryogonal (ICE): 420,430,151,218
        IMP  L23 Pansage (GRASS): 202,317,526,122
        IMP  L31 Tranquill (NORMAL/FLYING): 263,211,355,213
        IMP  L39 Unfezant (NORMAL/FLYING): 253,211,297,269
        IMP  L46 Cryogonal (ICE): 58,324,92,512
        IMP  L55 Unfezant (NORMAL/FLYING): 143,369,297,43
        IMP  L55 Simisear (FIRE): 481,91,182,242
        IMP  L62 Unfezant (NORMAL/FLYING): 403,211,197,207
        IMP  L62 Flygon (GROUND/DRAGON): 525,341,92,157
        IMP  L65 Unfezant (NORMAL/FLYING): 263,332,234,257
        IMP  L65 Eelektross (ELECTRIC): 85,430,113,324
        IMP  L41 Simisear (FIRE): 257,280,281,91
        IMP  L48 Unfezant (NORMAL/FLYING): 332,253,234,45
        IMP  L74 Klinklang (STEEL): 544,528,268,416
        REG  L26 Blitzle (ELECTRIC): 351,228,24,213
        REG  L63 Hitmonlee (FIGHTING): 136,398,299,282
        REG  L63 Hitmonchan (FIGHTING): 327,157,237,339
        REG  L56 Unfezant (NORMAL/FLYING): 403,211,234,13
        REG  L47 Boldore (ROCK): 350,89,430,174
        REG  L45 Swinub (ICE/GROUND): 419,317,213,414
        REG  L32 Scolipede (BUG/POISON): 404,91,334,231
        REG  L65 Hitmontop (FIGHTING): 183,418,203,343
        REG  L52 Amoonguss (GRASS/POISON): 72,474,78,235
        REG  L64 Archeops (ROCK/FLYING): 512,211,468,397
        REG  L54 Metang (STEEL/PSYCHIC): 309,157,218,357
        REG  L60 Wooper (WATER/GROUND): 401,414,237,254
        REG  L67 Emboar (FIRE/FIGHTING): 488,37,280,156
        REG  L47 Krookodile (GROUND/DARK): 242,157,184,188
        REG  L25 Litwick (GHOST/FIRE): 101,52,123,107
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 168,583,164,382
        BOSS L41 Weezing (POISON): 474,53,85,290
        BOSS L5 Zigzagoon (NORMAL): 189,86,216,351
        BOSS L51 Dusclops (GHOST): 247,157,261,114
        BOSS L52 Froslass (ICE/GHOST): 247,358,109,94
        BOSS L57 Claydol (GROUND/PSYCHIC): 529,444,219,229
        BOSS L14 Machop (FIGHTING): 27,523,113,339
        BOSS L28 Slaking (NORMAL): 514,400,303,8
        BOSS L44 Whiscash (WATER/GROUND): 523,444,196,340
        BOSS L70 Sharpedo (WATER/DARK): 168,56,182,89
        BOSS L71 Dusknoir (GHOST): 506,263,109,317
        BOSS L73 Altaria (DRAGON/FLYING): 143,89,349,97
        BOSS L77 Carbink (ROCK/FAIRY): 585,414,446,201
        BOSS L57 Cradily (ROCK/GRASS): 402,482,220,105
        BOSS L57 Milotic (WATER): 362,406,392,216
        IMP  L18 Slugma (FIRE): 52,88,262,263
        IMP  L31 Wailmer (WATER): 362,263,392,340
        IMP  L18 Wailmer (WATER): 250,196,182,497
        IMP  L31 Shroomish (GRASS): 402,264,14,188
        IMP  L37 Swellow (NORMAL/FLYING): 413,257,366,290
        IMP  L37 Wailord (WATER): 291,59,164,335
        IMP  L46 Delcatty (NORMAL): 253,426,215,313
        IMP  L24 Shroomish (GRASS): 402,33,313,474
        IMP  L24 Slugma (FIRE): 510,246,115,106
        IMP  L32 Sharpedo (WATER/DARK): 372,398,240,38
        IMP  L55 Camerupt (FIRE/GROUND): 481,290,261,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 7,421,174,398
        IMP  L50 Sceptile (GRASS): 348,225,320,197
        IMP  L64 Altaria (DRAGON/FLYING): 332,523,114,257
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,425,262,317
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,123,207,290
        REG  L39 Claydol (GROUND/PSYCHIC): 91,324,451,229
        REG  L36 Golbat (POISON/FLYING): 474,428,95,156
        REG  L34 Golbat (POISON/FLYING): 512,257,98,417
        REG  L33 Roselia (GRASS/POISON): 72,247,191,590
        REG  L43 Solrock (ROCK/PSYCHIC): 428,512,244,472
        REG  L49 Jellicent (WATER/GHOST): 506,188,412,219
        REG  L37 Skarmory (STEEL/FLYING): 332,157,191,263
        REG  L41 Clamperl (WATER): 362,58,334,287
        REG  L39 Tentacruel (WATER/POISON): 503,605,282,114
        REG  L48 Honchkrow (DARK/FLYING): 65,399,114,269
        REG  L53 Flygon (GROUND/DRAGON): 337,257,92,157
        REG  L23 Grimer (POISON): 398,325,50,164
        REG  L51 Mightyena (DARK): 492,305,382,180
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,526,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 490,157,220,9
        BOSS L56 Probopass (ROCK/STEEL): 430,87,86,317
        BOSS L41 Golisopod (BUG/WATER): 710,555,14,522
        BOSS L66 Froslass (ICE/GHOST): 420,85,86,109
        BOSS L66 Mandibuzz (DARK/FLYING): 555,403,417,164
        BOSS L57 Dugtrio (GROUND/STEEL): 523,37,157,421
        BOSS L52 Sableye (DARK/GHOST): 425,332,197,7
        BOSS L65 Crobat (POISON/FLYING): 19,228,92,104
        BOSS L64 Masquerain (BUG/FLYING): 403,341,453,412
        BOSS L66 Hydreigon (DARK/DRAGON): 525,430,156,184
        BOSS L65 Gyarados (WATER/FLYING): 340,423,92,56
        BOSS L64 Camerupt (FIRE/GROUND): 426,442,261,446
        BOSS L70 Mewtwo (PSYCHIC): 94,58,219,87
        BOSS L63 Crabominable (FIGHTING/ICE): 419,146,92,157
        IMP  L6 Pichu (ELECTRIC): 527,574,156,268
        IMP  L15 Glaceon (ICE): 524,352,321,247
        IMP  L27 Salandit (POISON/FIRE): 481,337,261,3
        IMP  L28 Noibat (FLYING/DRAGON): 16,280,164,141
        IMP  L41 Noivern (FLYING/DRAGON): 19,53,269,236
        IMP  L70 Primarina (WATER/FAIRY): 605,664,133,581
        IMP  L67 Muk (POISON/DARK): 168,612,151,441
        IMP  L53 Zoroark (DARK): 228,340,182,673
        IMP  L68 Zoroark (DARK): 555,53,347,490
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 209,168,113,590
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 609,411,227,473
        IMP  L68 Snorlax (NORMAL): 34,264,281,402
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 451,94,417,381
        IMP  L51 Shiinotic (GRASS/FAIRY): 202,585,156,79
        IMP  L20 Poipole (POISON): 474,343,164,324
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 351,269,497,168
        REG  L69 Lapras (WATER/ICE): 58,127,45,246
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 85,343,268,186
        REG  L35 Marowak (FIRE/GHOST): 708,675,164,67
        REG  L5 Yungoos (NORMAL): 173,279,214,156
        REG  L5 Yungoos (NORMAL): 351,207,162,237
        REG  L55 Espeon (PSYCHIC): 248,247,608,216
        REG  L5 Yungoos (NORMAL): 279,218,33,351
        REG  L33 Zubat (POISON/FLYING): 512,162,95,259
        REG  L30 Minior (ROCK/FLYING): 317,94,356,89
        REG  L27 Trumbeak (NORMAL/FLYING): 65,249,369,213
        REG  L62 Persian (NORMAL): 304,441,282,103
        REG  L14 Rattata (DARK/NORMAL): 168,196,259,98
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
