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
        BOSS L55 HITMONLEE (FIGHTING): 26,36,156,118
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,90
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,161,92,149
        BOSS L24 RAICHU (ELECTRIC): 84,98,92,69
        BOSS L37 KOFFING (POISON): 123,85,92,108
        BOSS L43 WEEZING (POISON): 123,126,156,120
        BOSS L42 RAPIDASH (FIRE): 126,23,115,45
        BOSS L38 VENOMOTH (BUG/POISON): 141,72,50,164
        BOSS L53 CLOYSTER (WATER/ICE): 58,61,164,43
        BOSS L56 LAPRAS (WATER/ICE): 58,85,156,56
        BOSS L55 HAUNTER (GHOST/POISON): 122,87,92,94
        BOSS L56 DRAGONAIR (DRAGON): 61,59,156,85
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,21,86,85
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,129,115,156
        IMP  L15 ABRA (PSYCHIC): 69,99,115,118
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,98,164,102
        IMP  L18 KADABRA (PSYCHIC): 93,99,156,69
        IMP  L16 RATICATE (NORMAL): 158,55,156,39
        IMP  L25 WARTORTLE (WATER): 55,5,164,115
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 99,140,115,95
        IMP  L35 ALAKAZAM (PSYCHIC): 60,161,50,66
        IMP  L40 VENUSAUR (GRASS/POISON): 75,33,164,77
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
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,189,197,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,113,228
        BOSS L31 PILOSWINE (ICE/GROUND): 58,246,197,34
        BOSS L37 DRAGONAIR (DRAGON): 82,53,97,192
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,202,79,23
        BOSS L46 MACHAMP (FIGHTING): 238,89,53,29
        BOSS L40 ARIADOS (BUG/POISON): 188,101,50,60
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,189,156,9
        BOSS L42 OMASTAR (ROCK/WATER): 55,196,114,104
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,85,105,129
        BOSS L41 JUMPLUFF (GRASS/FLYING): 93,78,178,38
        BOSS L33 ARIADOS (BUG/POISON): 188,101,184,91
        BOSS L45 MAGMAR (FIRE): 53,238,156,216
        BOSS L77 BLASTOISE (WATER): 56,58,240,182
        BOSS L58 ARCANINE (FIRE): 53,245,219,203
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,180
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,173
        IMP  L20 HAUNTER (GHOST/POISON): 122,168,156,173
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,18,98
        IMP  L32 MEGANIUM (GRASS): 22,246,115,34
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,182,218,129
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,92,199
        IMP  L35 HAUNTER (GHOST/POISON): 247,114,168,85
        IMP  L35 HAUNTER (GHOST/POISON): 247,109,216,192
        IMP  L43 GENGAR (GHOST/POISON): 247,85,92,94
        IMP  L43 ALAKAZAM (PSYCHIC): 94,168,227,216
        IMP  L43 ALAKAZAM (PSYCHIC): 94,91,105,247
        IMP  L46 ALAKAZAM (PSYCHIC): 94,9,50,8
        IMP  L50 TYPHLOSION (FIRE): 126,9,46,66
        IMP  L50 FERALIGATR (WATER): 56,89,8,246
        REG  L10 CHIKORITA (GRASS): 22,175,45,33
        REG  L20 QUAGSIRE (WATER/GROUND): 91,249,201,205
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 33,228,203,207
        REG  L25 NINETALES (FIRE): 52,98,104,109
        REG  L31 RHYDON (GROUND/ROCK): 89,223,31,7
        REG  L18 GROWLITHE (FIRE): 52,44,104,219
        REG  L23 GOLDEEN (WATER): 60,30,237,114
        REG  L28 TENTACOOL (WATER/POISON): 61,202,203,104
        REG  L28 POLIWHIRL (WATER): 61,168,95,196
        REG  L32 ONIX (ROCK/GROUND): 88,249,201,92
        REG  L6 VOLTORB (ELECTRIC): 129,205,207,237
        REG  L31 FURRET (NORMAL): 29,168,9,213
        REG  L42 GOLDUCK (WATER): 60,196,10,203
        REG  L23 PIKACHU (ELECTRIC): 84,205,216,227
        REG  L25 ELECTRODE (ELECTRIC): 120,205,203,218
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,263,281,247
        BOSS L53 WALREIN (ICE/WATER): 59,157,156,218
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,157,184,290
        BOSS L43 SEALEO (ICE/WATER): 58,89,156,34
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,205,222,126
        BOSS L50 KABUTOPS (ROCK/WATER): 352,263,164,332
        BOSS L46 HITMONCHAN (FIGHTING): 327,228,182,207
        BOSS L50 MANECTRIC (ELECTRIC): 85,290,213,231
        BOSS L46 GROWLITHE (FIRE): 126,242,92,37
        BOSS L45 KANGASKHAN (NORMAL): 25,89,196,87
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,164,126
        BOSS L58 SKARMORY (STEEL/FLYING): 143,38,18,97
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,247,201,92
        BOSS L56 LAPRAS (WATER/ICE): 56,156,203,231
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,188,97,36
        IMP  L34 MIGHTYENA (DARK): 44,305,92,231
        IMP  L40 GOLBAT (POISON/FLYING): 188,211,109,44
        IMP  L20 GROVYLE (GRASS): 331,9,156,92
        IMP  L29 LOMBRE (WATER/GRASS): 75,7,73,168
        IMP  L18 SLUGMA (FIRE): 52,157,113,123
        IMP  L29 PELIPPER (WATER/FLYING): 17,168,97,290
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,263,174,301
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,182,104
        IMP  L47 ROSELIA (GRASS/POISON): 38,191,164,202
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,114,228
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 85,263,86,205
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 53,157,156,28
        IMP  L34 GROVYLE (GRASS): 348,189,182,9
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,33,182,157
        IMP  L15 MUDKIP (WATER): 352,317,182,196
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 145,8,216,21
        REG  L26 MIGHTYENA (DARK): 44,310,28,237
        REG  L33 MACHOP (FIGHTING): 2,89,118,164
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,126,201,94
        REG  L35 PLUSLE (ELECTRIC): 85,34,313,203
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,205,218,102
        REG  L30 KOFFING (POISON): 188,351,205,120
        REG  L6 SEEDOT (GRASS): 331,91,102,156
        REG  L11 MARILL (WATER): 145,196,39,69
        REG  L26 LOMBRE (WATER/GRASS): 71,310,54,263
        REG  L29 XATU (PSYCHIC/FLYING): 94,168,113,98
        REG  L29 ZUBAT (POISON/FLYING): 188,263,104,207
        REG  L34 PELIPPER (WATER/FLYING): 16,97,102,98
        REG  L5 KYOGRE (WATER): 352,196,104,189
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,446,111,189
        BOSS L49 SCIZOR (BUG/STEEL): 211,332,113,168
        BOSS L52 HIPPOWDON (GROUND): 89,242,446,36
        BOSS L57 MAGMORTAR (FIRE): 394,85,164,103
        BOSS L58 SPIRITOMB (GHOST/DARK): 247,352,261,289
        BOSS L20 CHERRIM (GRASS): 331,164,219,290
        BOSS L29 MACHOKE (FIGHTING): 27,9,164,53
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,23,73,157
        BOSS L44 SNEASEL (DARK/ICE): 58,398,92,371
        BOSS L48 WEAVILE (DARK/ICE): 58,185,258,306
        BOSS L66 WHISCASH (WATER/GROUND): 56,209,240,263
        BOSS L69 RAPIDASH (FIRE): 394,38,97,32
        BOSS L72 ALAKAZAM (PSYCHIC): 94,412,105,357
        BOSS L78 GARCHOMP (DRAGON/GROUND): 200,424,317,421
        BOSS L58 MAGMORTAR (FIRE): 315,411,269,270
        IMP  L7 STARLY (NORMAL/FLYING): 365,466,228,239
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 326,451,334,157
        IMP  L27 GROTLE (GRASS): 402,37,133,237
        IMP  L34 STARAVIA (NORMAL/FLYING): 365,36,164,228
        IMP  L36 STARAPTOR (NORMAL/FLYING): 129,365,297,92
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,282,334,36
        IMP  L47 RAPIDASH (FIRE): 394,340,204,207
        IMP  L42 STARAPTOR (NORMAL/FLYING): 36,211,355,310
        IMP  L25 KADABRA (PSYCHIC): 94,324,269,7
        IMP  L27 GROTLE (GRASS): 412,34,446,218
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,228,334,216
        IMP  L69 RAPIDASH (FIRE): 315,224,97,129
        IMP  L73 SNORLAX (NORMAL): 38,242,156,118
        IMP  L83 SNORLAX (NORMAL): 38,442,92,428
        IMP  L60 SKUNTANK (POISON/DARK): 188,242,262,163
        REG  L5 STARLY (NORMAL/FLYING): 365,228,310,164
        REG  L29 ZUBAT (POISON/FLYING): 365,428,290,445
        REG  L36 SWINUB (ICE/GROUND): 91,157,213,36
        REG  L6 GEODUDE (ROCK/GROUND): 205,33,360,335
        REG  L21 CARNIVINE (GRASS): 331,189,275,79
        REG  L21 DRIFLOON (GHOST/FLYING): 314,205,116,129
        REG  L36 MURKROW (DARK/FLYING): 65,372,119,207
        REG  L39 MURKROW (DARK/FLYING): 65,372,466,18
        REG  L58 PELIPPER (WATER/FLYING): 16,239,48,203
        REG  L32 EEVEE (NORMAL): 231,28,204,247
        REG  L48 SEAKING (WATER): 291,340,48,392
        REG  L42 GOLBAT (POISON/FLYING): 314,369,259,428
        REG  L23 BUIZEL (WATER): 55,3,45,49
        REG  L42 MAGNETON (ELECTRIC/STEEL): 443,435,244,319
        REG  L56 EMPOLEON (WATER/STEEL): 430,196,65,453
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 528,337,366,428
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 143,412,322,399
        BOSS L72 Lucario (FIGHTING/STEEL): 231,444,526,496
        BOSS L28 Flaaffy (ELECTRIC): 85,324,215,178
        BOSS L48 Haxorus (DRAGON): 200,523,184,398
        BOSS L50 Cofagrigus (GHOST): 466,94,334,168
        BOSS L67 Simipour (WATER): 56,91,182,270
        BOSS L76 Clefable (NORMAL): 63,345,361,473
        BOSS L28 Emolga (ELECTRIC/FLYING): 209,369,228,310
        BOSS L49 Carracosta (WATER/ROCK): 401,88,164,44
        BOSS L56 Lucario (FIGHTING/STEEL): 238,421,46,89
        BOSS L73 Golurk (GROUND/GHOST): 89,409,428,101
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,317,219,282
        BOSS L75 Arcanine (FIRE): 394,370,241,242
        BOSS L75 Glaceon (ICE): 58,324,273,500
        IMP  L8 Tepig (FIRE): 52,317,174,249
        IMP  L48 Cryogonal (ICE): 59,430,151,282
        IMP  L23 Pansage (GRASS): 402,317,73,388
        IMP  L31 Tranquill (NORMAL/FLYING): 365,211,273,263
        IMP  L39 Unfezant (NORMAL/FLYING): 365,211,269,197
        IMP  L46 Cryogonal (ICE): 58,430,151,398
        IMP  L55 Unfezant (NORMAL/FLYING): 143,98,269,213
        IMP  L55 Simisear (FIRE): 315,157,526,261
        IMP  L62 Unfezant (NORMAL/FLYING): 63,211,164,314
        IMP  L62 Flygon (GROUND/DRAGON): 406,91,182,203
        IMP  L65 Unfezant (NORMAL/FLYING): 416,314,526,45
        IMP  L65 Eelektross (ELECTRIC): 528,53,113,148
        IMP  L41 Simisear (FIRE): 126,421,241,263
        IMP  L48 Unfezant (NORMAL/FLYING): 143,98,92,218
        IMP  L74 Klinklang (STEEL): 430,528,397,11
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 27,398,25,96
        REG  L63 Hitmonchan (FIGHTING): 327,418,92,203
        REG  L56 Unfezant (NORMAL/FLYING): 314,211,213,156
        REG  L47 Boldore (ROCK): 444,249,356,201
        REG  L45 Swinub (ICE/GROUND): 556,44,246,54
        REG  L32 Scolipede (BUG/POISON): 41,89,97,317
        REG  L65 Hitmontop (FIGHTING): 27,332,229,203
        REG  L52 Amoonguss (GRASS/POISON): 402,492,34,499
        REG  L64 Archeops (ROCK/FLYING): 444,257,501,337
        REG  L54 Metang (STEEL/PSYCHIC): 309,157,357,249
        REG  L60 Wooper (WATER/GROUND): 91,503,34,246
        REG  L67 Emboar (FIRE/FIGHTING): 359,157,488,241
        REG  L47 Krookodile (GROUND/DARK): 492,490,337,289
        REG  L25 Litwick (GHOST/FIRE): 481,101,220,164
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,583,184,305
        BOSS L41 Weezing (POISON): 188,351,390,247
        BOSS L5 Zigzagoon (NORMAL): 352,86,218,162
        BOSS L51 Dusclops (GHOST): 466,280,174,290
        BOSS L52 Froslass (ICE/GHOST): 247,577,109,358
        BOSS L57 Claydol (GROUND/PSYCHIC): 326,237,164,247
        BOSS L14 Machop (FIGHTING): 2,418,339,96
        BOSS L28 Slaking (NORMAL): 514,7,303,8
        BOSS L44 Whiscash (WATER/GROUND): 89,340,133,56
        BOSS L70 Sharpedo (WATER/DARK): 56,305,164,36
        BOSS L71 Dusknoir (GHOST): 325,157,262,180
        BOSS L73 Altaria (DRAGON/FLYING): 200,89,182,585
        BOSS L77 Carbink (ROCK/FAIRY): 585,246,277,496
        BOSS L57 Cradily (ROCK/GRASS): 412,362,105,213
        BOSS L57 Milotic (WATER): 503,225,113,95
        IMP  L18 Slugma (FIRE): 510,496,281,108
        IMP  L31 Wailmer (WATER): 352,290,240,205
        IMP  L18 Wailmer (WATER): 352,263,240,174
        IMP  L31 Shroomish (GRASS): 402,474,78,104
        IMP  L37 Swellow (NORMAL/FLYING): 413,290,182,590
        IMP  L37 Wailord (WATER): 323,499,156,428
        IMP  L46 Delcatty (NORMAL): 38,528,204,583
        IMP  L24 Shroomish (GRASS): 331,33,77,447
        IMP  L24 Slugma (FIRE): 510,205,261,115
        IMP  L32 Sharpedo (WATER/DARK): 400,428,182,496
        IMP  L55 Camerupt (FIRE/GROUND): 315,261,23,414
        IMP  L50 Blaziken (FIRE/FIGHTING): 264,481,297,496
        IMP  L50 Sceptile (GRASS): 437,530,92,264
        IMP  L64 Altaria (DRAGON/FLYING): 200,257,114,585
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 370,332,95,427
        REG  L4 Zigzagoon (NORMAL): 33,351,493,164
        REG  L25 Slugma (FIRE): 52,246,216,220
        REG  L39 Claydol (GROUND/PSYCHIC): 529,479,451,94
        REG  L36 Golbat (POISON/FLYING): 188,211,432,310
        REG  L34 Golbat (POISON/FLYING): 16,185,590,156
        REG  L33 Roselia (GRASS/POISON): 412,188,390,79
        REG  L43 Solrock (ROCK/PSYCHIC): 88,89,149,473
        REG  L49 Jellicent (WATER/GHOST): 61,412,63,378
        REG  L37 Skarmory (STEEL/FLYING): 507,157,371,174
        REG  L41 Clamperl (WATER): 352,196,300,48
        REG  L39 Tentacruel (WATER/POISON): 398,145,240,378
        REG  L48 Honchkrow (DARK/FLYING): 65,372,114,297
        REG  L53 Flygon (GROUND/DRAGON): 328,276,211,263
        REG  L23 Grimer (POISON): 398,91,139,164
        REG  L51 Mightyena (DARK): 492,424,184,216
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,479,227,523
        BOSS L47 Bewear (NORMAL/FIGHTING): 38,395,182,421
        BOSS L56 Probopass (ROCK/STEEL): 430,435,220,8
        BOSS L41 Golisopod (BUG/WATER): 534,675,164,522
        BOSS L66 Froslass (ICE/GHOST): 58,242,694,311
        BOSS L66 Mandibuzz (DARK/FLYING): 413,198,184,247
        BOSS L57 Dugtrio (GROUND/STEEL): 414,444,164,188
        BOSS L52 Sableye (DARK/GHOST): 247,280,164,9
        BOSS L65 Crobat (POISON/FLYING): 143,162,355,404
        BOSS L64 Masquerain (BUG/FLYING): 314,56,164,98
        BOSS L66 Hydreigon (DARK/DRAGON): 200,451,269,444
        BOSS L65 Gyarados (WATER/FLYING): 401,525,92,240
        BOSS L64 Camerupt (FIRE/GROUND): 284,430,414,237
        BOSS L70 Mewtwo (PSYCHIC): 428,126,261,447
        BOSS L63 Crabominable (FIGHTING/ICE): 264,419,133,61
        IMP  L6 Pichu (ELECTRIC): 351,574,227,604
        IMP  L15 Glaceon (ICE): 524,343,258,673
        IMP  L27 Salandit (POISON/FIRE): 53,237,261,82
        IMP  L28 Noibat (FLYING/DRAGON): 406,247,432,415
        IMP  L41 Noivern (FLYING/DRAGON): 542,247,97,404
        IMP  L70 Primarina (WATER/FAIRY): 605,57,204,512
        IMP  L67 Muk (POISON/DARK): 441,8,156,372
        IMP  L53 Zoroark (DARK): 492,326,262,383
        IMP  L68 Zoroark (DARK): 400,343,97,332
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 87,237,417,324
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 85,417,86,411
        IMP  L68 Snorlax (NORMAL): 38,667,281,8
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 435,343,273,381
        IMP  L51 Shiinotic (GRASS/FAIRY): 412,188,77,278
        IMP  L20 Poipole (POISON): 51,263,182,45
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,207,259,283
        REG  L69 Lapras (WATER/ICE): 524,246,56,46
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 209,574,21,589
        REG  L35 Marowak (FIRE/GHOST): 708,125,197,479
        REG  L5 Yungoos (NORMAL): 371,207,33,317
        REG  L5 Yungoos (NORMAL): 168,92,496,351
        REG  L55 Espeon (PSYCHIC): 473,324,39,98
        REG  L5 Yungoos (NORMAL): 317,213,237,168
        REG  L33 Zubat (POISON/FLYING): 314,168,599,18
        REG  L30 Minior (ROCK/FLYING): 205,36,523,477
        REG  L27 Trumbeak (NORMAL/FLYING): 64,249,355,488
        REG  L62 Persian (NORMAL): 163,402,289,332
        REG  L14 Rattata (DARK/NORMAL): 154,228,116,279
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
