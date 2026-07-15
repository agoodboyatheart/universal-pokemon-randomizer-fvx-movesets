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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,23,156,126
        BOSS L45 NIDOKING (POISON/GROUND): 89,61,115,156
        BOSS L55 HITMONLEE (FIGHTING): 69,34,92,116
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,92,111
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,86,149
        BOSS L24 RAICHU (ELECTRIC): 84,69,92,5
        BOSS L37 KOFFING (POISON): 124,153,156,108
        BOSS L43 WEEZING (POISON): 124,63,164,108
        BOSS L42 RAPIDASH (FIRE): 126,38,156,32
        BOSS L38 VENOMOTH (BUG/POISON): 141,60,18,79
        BOSS L53 CLOYSTER (WATER/ICE): 59,161,115,102
        BOSS L56 LAPRAS (WATER/ICE): 56,87,164,149
        BOSS L55 HAUNTER (GHOST/POISON): 101,94,156,95
        BOSS L56 DRAGONAIR (DRAGON): 59,87,86,43
        BOSS L62 DRAGONITE (DRAGON/FLYING): 35,58,97,82
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,164,18
        IMP  L15 ABRA (PSYCHIC): 5,69,164,156
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 98,16,18,28
        IMP  L18 KADABRA (PSYCHIC): 93,161,115,66
        IMP  L16 RATICATE (NORMAL): 98,61,164,156
        IMP  L25 WARTORTLE (WATER): 61,69,115,104
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,115,95
        IMP  L35 ALAKAZAM (PSYCHIC): 60,34,92,105
        IMP  L40 VENUSAUR (GRASS/POISON): 75,34,14,73
        IMP  L45 RHYHORN (GROUND/ROCK): 89,126,156,90
        IMP  L45 GYARADOS (WATER/FLYING): 61,126,92,102
        IMP  L47 GYARADOS (WATER/FLYING): 56,38,115,43
        IMP  L61 ARCANINE (FIRE): 53,91,164,34
        IMP  L63 ARCANINE (FIRE): 53,91,46,102
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,89,14,82
        REG  L11 RATTATA (NORMAL): 98,129,156,55
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,18,28
        REG  L18 MANKEY (FIGHTING): 69,2,102,164
        REG  L19 RATTATA (NORMAL): 98,61,104,92
        REG  L37 VULPIX (FIRE): 53,91,102,39
        REG  L29 WEEZING (POISON): 124,99,102,156
        REG  L31 CLOYSTER (WATER/ICE): 62,61,36,104
        REG  L26 MANKEY (FIGHTING): 69,129,92,102
        REG  L30 HORSEA (WATER): 61,58,92,38
        REG  L29 FEAROW (NORMAL/FLYING): 65,129,45,18
        REG  L70 GYARADOS (WATER/FLYING): 61,59,164,82
        REG  L17 MACHOP (FIGHTING): 69,2,118,157
        REG  L28 EKANS (POISON): 40,44,137,156
        REG  L39 DUGTRIO (GROUND): 89,34,157,90
        REG  L33 HAUNTER (GHOST/POISON): 101,85,164,102
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,185,156,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,129,113,92
        BOSS L31 PILOSWINE (ICE/GROUND): 89,58,182,30
        BOSS L37 DRAGONAIR (DRAGON): 82,87,97,43
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,23,73,235
        BOSS L46 MACHAMP (FIGHTING): 233,29,174,193
        BOSS L40 ARIADOS (BUG/POISON): 188,101,226,169
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,223,113,53
        BOSS L42 OMASTAR (ROCK/WATER): 61,58,114,48
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,94,105,107
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,38,115,77
        BOSS L33 ARIADOS (BUG/POISON): 188,228,182,50
        BOSS L45 MAGMAR (FIRE): 7,238,182,5
        BOSS L77 BLASTOISE (WATER): 56,58,156,174
        BOSS L58 ARCANINE (FIRE): 172,245,156,104
        IMP  L12 GASTLY (GHOST/POISON): 122,202,149,168
        IMP  L12 GASTLY (GHOST/POISON): 122,202,95,168
        IMP  L20 HAUNTER (GHOST/POISON): 247,202,174,207
        IMP  L20 ZUBAT (POISON/FLYING): 16,185,197,211
        IMP  L32 MEGANIUM (GRASS): 202,231,73,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,86,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,182,212
        IMP  L35 HAUNTER (GHOST/POISON): 247,192,156,212
        IMP  L43 GENGAR (GHOST/POISON): 101,7,114,149
        IMP  L43 ALAKAZAM (PSYCHIC): 93,7,182,223
        IMP  L43 ALAKAZAM (PSYCHIC): 60,7,213,63
        IMP  L46 ALAKAZAM (PSYCHIC): 94,9,182,105
        IMP  L50 TYPHLOSION (FIRE): 53,223,182,33
        IMP  L50 FERALIGATR (WATER): 56,89,156,218
        REG  L10 CHIKORITA (GRASS): 75,246,197,33
        REG  L20 QUAGSIRE (WATER/GROUND): 91,8,111,249
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,213,18
        REG  L25 NINETALES (FIRE): 52,129,219,185
        REG  L31 RHYDON (GROUND/ROCK): 89,223,157,242
        REG  L18 GROWLITHE (FIRE): 52,91,218,43
        REG  L23 GOLDEEN (WATER): 60,196,39,30
        REG  L28 TENTACOOL (WATER/POISON): 61,202,48,218
        REG  L28 POLIWHIRL (WATER): 61,196,111,197
        REG  L32 ONIX (ROCK/GROUND): 89,29,88,174
        REG  L6 VOLTORB (ELECTRIC): 33,129,207,92
        REG  L31 FURRET (NORMAL): 38,8,92,247
        REG  L42 GOLDUCK (WATER): 10,238,58,182
        REG  L23 PIKACHU (ELECTRIC): 9,129,104,174
        REG  L25 ELECTRODE (ELECTRIC): 49,129,205,182
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,263,269,207
        BOSS L53 WALREIN (ICE/WATER): 62,231,46,174
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,23,156,222
        BOSS L43 SEALEO (ICE/WATER): 58,157,156,254
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,157,92,201
        BOSS L50 KABUTOPS (ROCK/WATER): 61,69,156,319
        BOSS L46 HITMONCHAN (FIGHTING): 69,89,156,218
        BOSS L50 MANECTRIC (ELECTRIC): 87,44,46,182
        BOSS L46 GROWLITHE (FIRE): 257,34,182,44
        BOSS L45 KANGASKHAN (NORMAL): 23,231,50,156
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,53,231,64
        BOSS L58 SKARMORY (STEEL/FLYING): 211,38,46,28
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 157,58,347,95
        BOSS L56 LAPRAS (WATER/ICE): 56,87,46,195
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 232,280,115,93
        IMP  L34 MIGHTYENA (DARK): 44,34,269,92
        IMP  L40 GOLBAT (POISON/FLYING): 17,211,269,188
        IMP  L20 GROVYLE (GRASS): 202,228,92,73
        IMP  L29 LOMBRE (WATER/GRASS): 75,7,92,240
        IMP  L18 SLUGMA (FIRE): 52,88,151,164
        IMP  L29 PELIPPER (WATER/FLYING): 352,351,97,54
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,231,156,34
        IMP  L22 ZUBAT (POISON/FLYING): 17,247,174,289
        IMP  L47 ROSELIA (GRASS/POISON): 188,290,156,214
        IMP  L53 ALTARIA (DRAGON/FLYING): 225,53,97,46
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,38,115,48
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 315,280,182,34
        IMP  L34 GROVYLE (GRASS): 202,9,306,280
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,69,164,55
        IMP  L15 MUDKIP (WATER): 352,23,174,111
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,7,156
        REG  L26 MARILL (WATER): 61,280,102,237
        REG  L26 MIGHTYENA (DARK): 44,247,213,28
        REG  L33 MACHOP (FIGHTING): 233,126,118,9
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,89,115,285
        REG  L35 PLUSLE (ELECTRIC): 85,223,237,313
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,129,203,164
        REG  L30 KOFFING (POISON): 124,85,149,290
        REG  L6 SEEDOT (GRASS): 202,91,216,73
        REG  L11 MARILL (WATER): 352,69,287,321
        REG  L26 LOMBRE (WATER/GRASS): 202,263,104,310
        REG  L29 XATU (PSYCHIC/FLYING): 65,185,285,297
        REG  L29 ZUBAT (POISON/FLYING): 332,185,92,141
        REG  L34 PELIPPER (WATER/FLYING): 17,59,214,156
        REG  L5 KYOGRE (WATER): 352,196,203,46
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,173,156,363
        BOSS L49 SCIZOR (BUG/STEEL): 418,276,226,13
        BOSS L52 HIPPOWDON (GROUND): 89,444,303,34
        BOSS L57 MAGMORTAR (FIRE): 257,94,269,157
        BOSS L58 SPIRITOMB (GHOST/DARK): 466,399,92,416
        BOSS L20 CHERRIM (GRASS): 202,290,92,218
        BOSS L29 MACHOKE (FIGHTING): 280,7,92,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,200,73,275
        BOSS L44 SNEASEL (DARK/ICE): 59,421,306,398
        BOSS L48 WEAVILE (DARK/ICE): 400,411,347,237
        BOSS L66 WHISCASH (WATER/GROUND): 414,444,321,263
        BOSS L69 RAPIDASH (FIRE): 172,98,164,204
        BOSS L72 ALAKAZAM (PSYCHIC): 428,411,50,259
        BOSS L78 GARCHOMP (DRAGON/GROUND): 91,424,14,216
        BOSS L58 MAGMORTAR (FIRE): 436,231,261,270
        IMP  L7 STARLY (NORMAL/FLYING): 332,228,355,33
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,94,156,214
        IMP  L27 GROTLE (GRASS): 402,44,321,328
        IMP  L34 STARAVIA (NORMAL/FLYING): 98,228,156,257
        IMP  L36 STARAPTOR (NORMAL/FLYING): 17,370,92,28
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,157,339,91
        IMP  L47 RAPIDASH (FIRE): 394,398,237,23
        IMP  L42 STARAPTOR (NORMAL/FLYING): 98,332,156,218
        IMP  L25 KADABRA (PSYCHIC): 60,247,269,104
        IMP  L27 GROTLE (GRASS): 75,44,219,328
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,400,164,43
        IMP  L69 RAPIDASH (FIRE): 394,231,182,207
        IMP  L73 SNORLAX (NORMAL): 34,402,92,200
        IMP  L83 SNORLAX (NORMAL): 34,276,174,111
        IMP  L60 SKUNTANK (POISON/DARK): 400,421,269,163
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 17,371,310,218
        REG  L36 SWINUB (ICE/GROUND): 333,89,258,36
        REG  L6 GEODUDE (ROCK/GROUND): 246,189,218,111
        REG  L21 CARNIVINE (GRASS): 412,44,290,78
        REG  L21 DRIFLOON (GHOST/FLYING): 247,318,205,373
        REG  L36 MURKROW (DARK/FLYING): 185,94,216,213
        REG  L39 MURKROW (DARK/FLYING): 17,290,297,355
        REG  L58 PELIPPER (WATER/FLYING): 56,403,441,58
        REG  L32 EEVEE (NORMAL): 98,231,445,45
        REG  L48 SEAKING (WATER): 127,282,156,213
        REG  L42 GOLBAT (POISON/FLYING): 305,290,216,269
        REG  L23 BUIZEL (WATER): 362,228,189,290
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,87,33,104
        REG  L56 EMPOLEON (WATER/STEEL): 430,453,240,392
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,219
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,366,322
        BOSS L72 Lucario (FIGHTING/STEEL): 418,370,339,245
        BOSS L28 Flaaffy (ELECTRIC): 521,7,97,280
        BOSS L48 Haxorus (DRAGON): 530,401,82,398
        BOSS L50 Cofagrigus (GHOST): 247,412,347,156
        BOSS L67 Simipour (WATER): 401,512,270,157
        BOSS L76 Clefable (NORMAL): 514,409,182,428
        BOSS L28 Emolga (ELECTRIC/FLYING): 209,403,355,310
        BOSS L49 Carracosta (WATER/ROCK): 479,442,174,91
        BOSS L56 Lucario (FIGHTING/STEEL): 396,299,347,399
        BOSS L73 Golurk (GROUND/GHOST): 325,9,446,356
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 427,7,262,348
        BOSS L75 Arcanine (FIRE): 481,34,261,182
        BOSS L75 Glaceon (ICE): 58,304,226,313
        IMP  L8 Tepig (FIRE): 488,343,174,207
        IMP  L48 Cryogonal (ICE): 58,512,164,430
        IMP  L23 Pansage (GRASS): 202,282,417,447
        IMP  L31 Tranquill (NORMAL/FLYING): 143,211,273,95
        IMP  L39 Unfezant (NORMAL/FLYING): 98,369,164,43
        IMP  L46 Cryogonal (ICE): 59,430,182,229
        IMP  L55 Unfezant (NORMAL/FLYING): 98,211,269,381
        IMP  L55 Simisear (FIRE): 126,421,213,242
        IMP  L62 Unfezant (NORMAL/FLYING): 98,211,234,403
        IMP  L62 Flygon (GROUND/DRAGON): 525,257,468,242
        IMP  L65 Unfezant (NORMAL/FLYING): 263,143,355,297
        IMP  L65 Eelektross (ELECTRIC): 435,401,489,393
        IMP  L41 Simisear (FIRE): 481,157,468,122
        IMP  L48 Unfezant (NORMAL/FLYING): 416,314,156,297
        IMP  L74 Klinklang (STEEL): 544,11,277,164
        REG  L26 Blitzle (ELECTRIC): 351,228,103,148
        REG  L63 Hitmonlee (FIGHTING): 24,299,263,282
        REG  L63 Hitmonchan (FIGHTING): 183,418,514,89
        REG  L56 Unfezant (NORMAL/FLYING): 403,369,234,273
        REG  L47 Boldore (ROCK): 157,263,207,201
        REG  L45 Swinub (ICE/GROUND): 333,157,91,36
        REG  L32 Scolipede (BUG/POISON): 404,188,89,216
        REG  L65 Hitmontop (FIGHTING): 136,418,91,67
        REG  L52 Amoonguss (GRASS/POISON): 474,492,92,402
        REG  L64 Archeops (ROCK/FLYING): 444,525,213,340
        REG  L54 Metang (STEEL/PSYCHIC): 418,8,113,247
        REG  L60 Wooper (WATER/GROUND): 89,401,227,54
        REG  L67 Emboar (FIRE/FIGHTING): 488,372,360,535
        REG  L47 Krookodile (GROUND/DARK): 44,525,411,184
        REG  L25 Litwick (GHOST/FIRE): 247,481,220,371
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,583,46,289
        BOSS L41 Weezing (POISON): 124,60,220,139
        BOSS L5 Zigzagoon (NORMAL): 496,352,104,189
        BOSS L51 Dusclops (GHOST): 325,228,50,216
        BOSS L52 Froslass (ICE/GHOST): 420,358,182,577
        BOSS L57 Claydol (GROUND/PSYCHIC): 529,263,277,285
        BOSS L14 Machop (FIGHTING): 490,317,164,113
        BOSS L28 Slaking (NORMAL): 163,8,156,523
        BOSS L44 Whiscash (WATER/GROUND): 401,209,349,222
        BOSS L70 Sharpedo (WATER/DARK): 56,38,97,104
        BOSS L71 Dusknoir (GHOST): 247,9,347,213
        BOSS L73 Altaria (DRAGON/FLYING): 200,605,349,384
        BOSS L77 Carbink (ROCK/FAIRY): 408,585,397,393
        BOSS L57 Cradily (ROCK/GRASS): 412,482,156,220
        BOSS L57 Milotic (WATER): 503,406,277,442
        IMP  L18 Slugma (FIRE): 510,281,151,237
        IMP  L31 Wailmer (WATER): 362,38,174,487
        IMP  L18 Wailmer (WATER): 503,263,590,205
        IMP  L31 Shroomish (GRASS): 412,409,73,219
        IMP  L37 Swellow (NORMAL/FLYING): 413,211,18,216
        IMP  L37 Wailord (WATER): 323,499,164,37
        IMP  L46 Delcatty (NORMAL): 252,358,273,219
        IMP  L24 Shroomish (GRASS): 331,496,182,77
        IMP  L24 Slugma (FIRE): 488,246,113,108
        IMP  L32 Sharpedo (WATER/DARK): 400,398,182,259
        IMP  L55 Camerupt (FIRE/GROUND): 315,430,261,174
        IMP  L50 Blaziken (FIRE/FIGHTING): 299,530,261,45
        IMP  L50 Sceptile (GRASS): 331,200,14,91
        IMP  L64 Altaria (DRAGON/FLYING): 337,126,538,349
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 428,290,269,581
        REG  L4 Zigzagoon (NORMAL): 237,351,493,164
        REG  L25 Slugma (FIRE): 510,499,385,267
        REG  L39 Claydol (GROUND/PSYCHIC): 473,157,113,397
        REG  L36 Golbat (POISON/FLYING): 17,369,428,98
        REG  L34 Golbat (POISON/FLYING): 512,202,599,48
        REG  L33 Roselia (GRASS/POISON): 437,188,388,164
        REG  L43 Solrock (ROCK/PSYCHIC): 444,442,126,472
        REG  L49 Jellicent (WATER/GHOST): 247,58,216,188
        REG  L37 Skarmory (STEEL/FLYING): 232,413,590,129
        REG  L41 Clamperl (WATER): 503,58,104,112
        REG  L39 Tentacruel (WATER/POISON): 362,58,114,491
        REG  L48 Honchkrow (DARK/FLYING): 65,211,18,101
        REG  L53 Flygon (GROUND/DRAGON): 89,525,231,92
        REG  L23 Grimer (POISON): 474,371,247,496
        REG  L51 Mightyena (DARK): 399,263,382,259
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 280,282,92,227
        BOSS L47 Bewear (NORMAL/FIGHTING): 359,9,14,175
        BOSS L56 Probopass (ROCK/STEEL): 430,9,156,106
        BOSS L41 Golisopod (BUG/WATER): 42,675,191,249
        BOSS L66 Froslass (ICE/GHOST): 8,29,50,374
        BOSS L66 Mandibuzz (DARK/FLYING): 185,369,18,198
        BOSS L57 Dugtrio (GROUND/STEEL): 414,157,216,161
        BOSS L52 Sableye (DARK/GHOST): 421,324,347,398
        BOSS L65 Crobat (POISON/FLYING): 440,17,18,212
        BOSS L64 Masquerain (BUG/FLYING): 314,466,114,244
        BOSS L66 Hydreigon (DARK/DRAGON): 44,430,46,225
        BOSS L65 Gyarados (WATER/FLYING): 57,423,164,104
        BOSS L64 Camerupt (FIRE/GROUND): 126,89,397,254
        BOSS L70 Mewtwo (PSYCHIC): 473,396,86,50
        BOSS L63 Crabominable (FIGHTING/ICE): 665,152,92,179
        IMP  L6 Pichu (ELECTRIC): 451,252,113,216
        IMP  L15 Glaceon (ICE): 196,496,46,258
        IMP  L27 Salandit (POISON/FIRE): 474,168,289,52
        IMP  L28 Noibat (FLYING/DRAGON): 314,399,97,141
        IMP  L41 Noivern (FLYING/DRAGON): 406,53,432,415
        IMP  L70 Primarina (WATER/FAIRY): 585,56,115,133
        IMP  L67 Muk (POISON/DARK): 441,228,397,373
        IMP  L53 Zoroark (DARK): 228,53,468,416
        IMP  L68 Zoroark (DARK): 675,369,92,386
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 85,94,347,393
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 9,280,156,175
        IMP  L68 Snorlax (NORMAL): 263,402,182,58
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 435,63,417,673
        IMP  L51 Shiinotic (GRASS/FAIRY): 412,605,668,478
        IMP  L20 Poipole (POISON): 474,237,324,64
        REG  L6 Yungoos (NORMAL): 162,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 279,104,351,33
        REG  L69 Lapras (WATER/ICE): 56,684,263,90
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 435,231,164,273
        REG  L35 Marowak (FIRE/GHOST): 708,157,172,218
        REG  L5 Yungoos (NORMAL): 279,259,216,237
        REG  L5 Yungoos (NORMAL): 371,207,259,33
        REG  L55 Espeon (PSYCHIC): 500,247,175,28
        REG  L5 Yungoos (NORMAL): 279,92,496,371
        REG  L33 Zubat (POISON/FLYING): 413,228,104,92
        REG  L30 Minior (ROCK/FLYING): 512,444,201,393
        REG  L27 Trumbeak (NORMAL/FLYING): 332,350,211,14
        REG  L62 Persian (NORMAL): 304,492,408,445
        REG  L14 Rattata (DARK/NORMAL): 228,162,269,116
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
