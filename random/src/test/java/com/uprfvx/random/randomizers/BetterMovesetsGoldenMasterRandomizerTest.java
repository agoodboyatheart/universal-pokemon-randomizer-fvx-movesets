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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,30,92,157
        BOSS L45 NIDOKING (POISON/GROUND): 89,85,156,59
        BOSS L55 HITMONLEE (FIGHTING): 26,5,164,156
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,92
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,92,149
        BOSS L24 RAICHU (ELECTRIC): 84,6,115,45
        BOSS L37 KOFFING (POISON): 124,85,156,108
        BOSS L43 WEEZING (POISON): 124,126,156,87
        BOSS L42 RAPIDASH (FIRE): 126,38,164,104
        BOSS L38 VENOMOTH (BUG/POISON): 36,76,92,164
        BOSS L53 CLOYSTER (WATER/ICE): 58,38,92,110
        BOSS L56 LAPRAS (WATER/ICE): 56,85,156,59
        BOSS L55 HAUNTER (GHOST/POISON): 101,156,104,120
        BOSS L56 DRAGONAIR (DRAGON): 87,59,164,34
        BOSS L62 DRAGONITE (DRAGON/FLYING): 21,126,115,58
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,129,18,28
        IMP  L15 ABRA (PSYCHIC): 5,66,115,156
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,16,115,104
        IMP  L18 KADABRA (PSYCHIC): 93,99,115,104
        IMP  L16 RATICATE (NORMAL): 129,61,156,102
        IMP  L25 WARTORTLE (WATER): 55,69,164,66
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,92,156
        IMP  L35 ALAKAZAM (PSYCHIC): 94,91,50,102
        IMP  L40 VENUSAUR (GRASS/POISON): 76,38,73,102
        IMP  L45 RHYHORN (GROUND/ROCK): 157,89,164,85
        IMP  L45 GYARADOS (WATER/FLYING): 61,59,156,43
        IMP  L47 GYARADOS (WATER/FLYING): 56,58,115,104
        IMP  L61 ARCANINE (FIRE): 53,34,156,46
        IMP  L63 ARCANINE (FIRE): 53,38,46,164
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,69,164,43
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,28,164
        REG  L18 MANKEY (FIGHTING): 66,5,157,156
        REG  L19 RATTATA (NORMAL): 99,55,102,164
        REG  L37 VULPIX (FIRE): 53,36,156,39
        REG  L29 WEEZING (POISON): 123,33,104,156
        REG  L31 CLOYSTER (WATER/ICE): 59,120,48,156
        REG  L26 MANKEY (FIGHTING): 69,99,156,43
        REG  L30 HORSEA (WATER): 61,59,104,102
        REG  L29 FEAROW (NORMAL/FLYING): 64,99,45,119
        REG  L70 GYARADOS (WATER/FLYING): 61,126,82,164
        REG  L17 MACHOP (FIGHTING): 69,99,90,157
        REG  L28 EKANS (POISON): 40,72,137,164
        REG  L39 DUGTRIO (GROUND): 89,157,163,104
        REG  L33 HAUNTER (GHOST/POISON): 101,87,164,109
        """);
        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,228
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,226,168
        BOSS L31 PILOSWINE (ICE/GROUND): 89,157,92,156
        BOSS L37 DRAGONAIR (DRAGON): 21,87,86,231
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 202,94,182,121
        BOSS L46 MACHAMP (FIGHTING): 67,89,197,126
        BOSS L40 ARIADOS (BUG/POISON): 188,202,226,81
        BOSS L47 DRAGONITE (DRAGON/FLYING): 7,87,197,223
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,114,182
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,85,113,229
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,38,115,78
        BOSS L33 ARIADOS (BUG/POISON): 188,101,156,202
        BOSS L45 MAGMAR (FIRE): 53,9,174,203
        BOSS L77 BLASTOISE (WATER): 56,231,174,59
        BOSS L58 ARCANINE (FIRE): 53,245,97,237
        IMP  L12 GASTLY (GHOST/POISON): 122,202,114,168
        IMP  L12 GASTLY (GHOST/POISON): 122,202,174,203
        IMP  L20 HAUNTER (GHOST/POISON): 122,168,156,149
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,141,185
        IMP  L32 MEGANIUM (GRASS): 202,231,115,89
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,205,86,33
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,174,86
        IMP  L35 HAUNTER (GHOST/POISON): 101,85,114,94
        IMP  L35 HAUNTER (GHOST/POISON): 101,174,244,94
        IMP  L43 GENGAR (GHOST/POISON): 101,156,237,7
        IMP  L43 ALAKAZAM (PSYCHIC): 60,8,156,134
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,50,227
        IMP  L46 ALAKAZAM (PSYCHIC): 60,9,115,244
        IMP  L50 TYPHLOSION (FIRE): 53,66,92,98
        IMP  L50 FERALIGATR (WATER): 56,157,174,184
        REG  L10 CHIKORITA (GRASS): 22,14,73,246
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,249,219
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,18,28
        REG  L25 NINETALES (FIRE): 52,185,46,109
        REG  L31 RHYDON (GROUND/ROCK): 157,223,231,156
        REG  L18 GROWLITHE (FIRE): 83,249,91,219
        REG  L23 GOLDEEN (WATER): 30,64,213,104
        REG  L28 TENTACOOL (WATER/POISON): 61,196,203,182
        REG  L28 POLIWHIRL (WATER): 61,168,216,8
        REG  L32 ONIX (ROCK/GROUND): 157,231,175,174
        REG  L6 VOLTORB (ELECTRIC): 129,205,216,33
        REG  L31 FURRET (NORMAL): 98,223,218,116
        REG  L42 GOLDUCK (WATER): 63,58,50,95
        REG  L23 PIKACHU (ELECTRIC): 84,173,156,203
        REG  L25 ELECTRODE (ELECTRIC): 49,205,182,156
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,247,281,182
        BOSS L53 WALREIN (ICE/WATER): 58,231,281,89
        BOSS L26 CAMERUPT (FIRE/GROUND): 52,189,46,317
        BOSS L43 SEALEO (ICE/WATER): 62,157,46,34
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,315,92,182
        BOSS L50 KABUTOPS (ROCK/WATER): 61,229,164,282
        BOSS L46 HITMONCHAN (FIGHTING): 327,25,197,228
        BOSS L50 MANECTRIC (ELECTRIC): 87,242,104,29
        BOSS L46 GROWLITHE (FIRE): 315,263,218,231
        BOSS L45 KANGASKHAN (NORMAL): 252,247,46,157
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,126,47,38
        BOSS L58 SKARMORY (STEEL/FLYING): 65,290,156,201
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 58,115,149,263
        BOSS L56 LAPRAS (WATER/ICE): 59,87,195,109
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,188,97,115
        IMP  L34 MIGHTYENA (DARK): 263,247,281,316
        IMP  L40 GOLBAT (POISON/FLYING): 188,202,269,102
        IMP  L20 GROVYLE (GRASS): 71,225,73,98
        IMP  L29 LOMBRE (WATER/GRASS): 75,55,92,196
        IMP  L18 SLUGMA (FIRE): 52,157,281,189
        IMP  L29 PELIPPER (WATER/FLYING): 16,352,196,263
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,157,164,8
        IMP  L22 ZUBAT (POISON/FLYING): 332,310,269,259
        IMP  L47 ROSELIA (GRASS/POISON): 202,290,156,213
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,182,63
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,263,319,63
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 69,126,9,5
        IMP  L34 GROVYLE (GRASS): 348,242,92,231
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,157,156,231
        IMP  L15 MUDKIP (WATER): 352,301,263,317
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 55,290,227,39
        REG  L26 MIGHTYENA (DARK): 44,310,305,316
        REG  L33 MACHOP (FIGHTING): 223,25,164,156
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,247,219,315
        REG  L35 PLUSLE (ELECTRIC): 9,263,313,69
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,182,33,205
        REG  L30 KOFFING (POISON): 188,126,259,104
        REG  L6 SEEDOT (GRASS): 202,206,241,14
        REG  L11 MARILL (WATER): 55,91,69,213
        REG  L26 LOMBRE (WATER/GRASS): 55,189,102,290
        REG  L29 XATU (PSYCHIC/FLYING): 332,168,113,285
        REG  L29 ZUBAT (POISON/FLYING): 17,310,182,98
        REG  L34 PELIPPER (WATER/FLYING): 38,59,218,97
        REG  L5 KYOGRE (WATER): 352,351,102,219
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,182,445,33
        BOSS L49 SCIZOR (BUG/STEEL): 442,280,226,334
        BOSS L52 HIPPOWDON (GROUND): 89,423,281,218
        BOSS L57 MAGMORTAR (FIRE): 436,76,261,223
        BOSS L58 SPIRITOMB (GHOST/DARK): 228,425,218,63
        BOSS L20 CHERRIM (GRASS): 412,235,267,33
        BOSS L29 MACHOKE (FIGHTING): 2,8,113,116
        BOSS L42 ABOMASNOW (GRASS/ICE): 420,290,73,411
        BOSS L44 SNEASEL (DARK/ICE): 419,91,97,306
        BOSS L48 WEAVILE (DARK/ICE): 420,280,14,98
        BOSS L66 WHISCASH (WATER/GROUND): 426,428,92,157
        BOSS L69 RAPIDASH (FIRE): 53,37,164,224
        BOSS L72 ALAKAZAM (PSYCHIC): 427,7,156,374
        BOSS L78 GARCHOMP (DRAGON/GROUND): 89,424,156,337
        BOSS L58 MAGMORTAR (FIRE): 257,85,270,223
        IMP  L7 STARLY (NORMAL/FLYING): 332,228,355,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,89,347,201
        IMP  L27 GROTLE (GRASS): 75,328,14,219
        IMP  L34 STARAVIA (NORMAL/FLYING): 290,369,97,211
        IMP  L36 STARAPTOR (NORMAL/FLYING): 38,370,18,28
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,91,334,400
        IMP  L47 RAPIDASH (FIRE): 394,23,97,32
        IMP  L42 STARAPTOR (NORMAL/FLYING): 413,211,97,297
        IMP  L25 KADABRA (PSYCHIC): 60,409,104,263
        IMP  L27 GROTLE (GRASS): 331,328,115,321
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,282,14,106
        IMP  L69 RAPIDASH (FIRE): 394,398,23,340
        IMP  L73 SNORLAX (NORMAL): 290,442,92,8
        IMP  L83 SNORLAX (NORMAL): 416,200,85,126
        IMP  L60 SKUNTANK (POISON/DARK): 400,421,126,416
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,44,129,310
        REG  L36 SWINUB (ICE/GROUND): 426,59,46,216
        REG  L6 GEODUDE (ROCK/GROUND): 317,33,445,203
        REG  L21 CARNIVINE (GRASS): 345,168,445,104
        REG  L21 DRIFLOON (GHOST/FLYING): 466,351,285,282
        REG  L36 MURKROW (DARK/FLYING): 399,101,347,18
        REG  L39 MURKROW (DARK/FLYING): 65,263,182,180
        REG  L58 PELIPPER (WATER/FLYING): 56,403,45,366
        REG  L32 EEVEE (NORMAL): 98,247,182,313
        REG  L48 SEAKING (WATER): 127,340,39,218
        REG  L42 GOLBAT (POISON/FLYING): 403,228,237,212
        REG  L23 BUIZEL (WATER): 453,189,163,317
        REG  L42 MAGNETON (ELECTRIC/STEEL): 85,430,49,324
        REG  L56 EMPOLEON (WATER/STEEL): 430,157,213,300
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,247
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,366,412
        BOSS L72 Lucario (FIGHTING/STEEL): 327,247,46,398
        BOSS L28 Flaaffy (ELECTRIC): 84,496,182,216
        BOSS L48 Haxorus (DRAGON): 337,91,269,280
        BOSS L50 Cofagrigus (GHOST): 247,399,182,334
        BOSS L67 Simipour (WATER): 401,512,182,270
        BOSS L76 Clefable (NORMAL): 34,53,236,309
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,369,86,98
        BOSS L49 Carracosta (WATER/ROCK): 157,401,397,442
        BOSS L56 Lucario (FIGHTING/STEEL): 410,198,347,182
        BOSS L73 Golurk (GROUND/GHOST): 89,7,446,324
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,400,277,427
        BOSS L75 Arcanine (FIRE): 315,442,156,422
        BOSS L75 Glaceon (ICE): 59,304,182,485
        IMP  L8 Tepig (FIRE): 488,249,174,496
        IMP  L48 Cryogonal (ICE): 420,76,156,151
        IMP  L23 Pansage (GRASS): 202,91,468,44
        IMP  L31 Tranquill (NORMAL/FLYING): 98,369,355,526
        IMP  L39 Unfezant (NORMAL/FLYING): 263,257,297,211
        IMP  L46 Cryogonal (ICE): 59,324,164,54
        IMP  L55 Unfezant (NORMAL/FLYING): 253,211,182,244
        IMP  L55 Simisear (FIRE): 7,91,92,242
        IMP  L62 Unfezant (NORMAL/FLYING): 143,369,366,269
        IMP  L62 Flygon (GROUND/DRAGON): 89,157,92,103
        IMP  L65 Unfezant (NORMAL/FLYING): 63,211,92,403
        IMP  L65 Eelektross (ELECTRIC): 528,401,92,369
        IMP  L41 Simisear (FIRE): 315,231,281,237
        IMP  L48 Unfezant (NORMAL/FLYING): 13,369,381,257
        IMP  L74 Klinklang (STEEL): 544,253,508,393
        REG  L26 Blitzle (ELECTRIC): 351,263,24,156
        REG  L63 Hitmonlee (FIGHTING): 370,299,526,418
        REG  L63 Hitmonchan (FIGHTING): 183,157,501,418
        REG  L56 Unfezant (NORMAL/FLYING): 263,143,234,516
        REG  L47 Boldore (ROCK): 157,89,36,199
        REG  L45 Swinub (ICE/GROUND): 419,157,182,34
        REG  L32 Scolipede (BUG/POISON): 398,231,226,390
        REG  L65 Hitmontop (FIGHTING): 183,228,89,98
        REG  L52 Amoonguss (GRASS/POISON): 402,188,147,263
        REG  L64 Archeops (ROCK/FLYING): 457,340,91,446
        REG  L54 Metang (STEEL/PSYCHIC): 418,228,201,360
        REG  L60 Wooper (WATER/GROUND): 91,21,219,174
        REG  L67 Emboar (FIRE/FIGHTING): 315,528,213,231
        REG  L47 Krookodile (GROUND/DARK): 242,411,116,188
        REG  L25 Litwick (GHOST/FIRE): 481,123,148,496
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,231,164,422
        BOSS L41 Weezing (POISON): 124,60,261,247
        BOSS L5 Zigzagoon (NORMAL): 351,271,168,343
        BOSS L51 Dusclops (GHOST): 247,280,261,114
        BOSS L52 Froslass (ICE/GHOST): 59,358,269,94
        BOSS L57 Claydol (GROUND/PSYCHIC): 94,247,277,590
        BOSS L14 Machop (FIGHTING): 490,523,339,43
        BOSS L28 Slaking (NORMAL): 514,400,303,8
        BOSS L44 Whiscash (WATER/GROUND): 330,444,182,207
        BOSS L70 Sharpedo (WATER/DARK): 362,253,92,207
        BOSS L71 Dusknoir (GHOST): 506,8,269,399
        BOSS L73 Altaria (DRAGON/FLYING): 406,605,46,47
        BOSS L77 Carbink (ROCK/FAIRY): 605,408,156,175
        BOSS L57 Cradily (ROCK/GRASS): 202,362,182,482
        BOSS L57 Milotic (WATER): 362,406,277,95
        IMP  L18 Slugma (FIRE): 52,317,115,385
        IMP  L31 Wailmer (WATER): 362,59,174,290
        IMP  L18 Wailmer (WATER): 55,290,156,499
        IMP  L31 Shroomish (GRASS): 412,474,73,409
        IMP  L37 Swellow (NORMAL/FLYING): 290,257,432,287
        IMP  L37 Wailord (WATER): 503,442,164,34
        IMP  L46 Delcatty (NORMAL): 38,247,215,58
        IMP  L24 Shroomish (GRASS): 72,237,388,358
        IMP  L24 Slugma (FIRE): 52,246,281,115
        IMP  L32 Sharpedo (WATER/DARK): 453,89,164,58
        IMP  L55 Camerupt (FIRE/GROUND): 89,126,397,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 481,136,97,306
        IMP  L50 Sceptile (GRASS): 202,337,14,320
        IMP  L64 Altaria (DRAGON/FLYING): 200,211,156,104
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,282,50,244
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,246,106,499
        REG  L39 Claydol (GROUND/PSYCHIC): 60,605,89,472
        REG  L36 Golbat (POISON/FLYING): 474,413,109,366
        REG  L34 Golbat (POISON/FLYING): 474,202,599,417
        REG  L33 Roselia (GRASS/POISON): 437,188,320,178
        REG  L43 Solrock (ROCK/PSYCHIC): 157,428,442,373
        REG  L49 Jellicent (WATER/GHOST): 323,101,605,506
        REG  L37 Skarmory (STEEL/FLYING): 413,157,46,446
        REG  L41 Clamperl (WATER): 330,263,590,58
        REG  L39 Tentacruel (WATER/POISON): 362,59,240,207
        REG  L48 Honchkrow (DARK/FLYING): 400,101,103,297
        REG  L53 Flygon (GROUND/DRAGON): 407,231,369,28
        REG  L23 Grimer (POISON): 398,612,189,263
        REG  L51 Mightyena (DARK): 492,290,28,207
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,339,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 276,428,37,8
        BOSS L56 Probopass (ROCK/STEEL): 430,85,92,263
        BOSS L41 Golisopod (BUG/WATER): 660,453,191,14
        BOSS L66 Froslass (ICE/GHOST): 420,324,50,358
        BOSS L66 Mandibuzz (DARK/FLYING): 399,413,182,18
        BOSS L57 Dugtrio (GROUND/STEEL): 707,29,92,203
        BOSS L52 Sableye (DARK/GHOST): 282,490,220,67
        BOSS L65 Crobat (POISON/FLYING): 512,369,164,599
        BOSS L64 Masquerain (BUG/FLYING): 679,61,226,60
        BOSS L66 Hydreigon (DARK/DRAGON): 406,257,115,29
        BOSS L65 Gyarados (WATER/FLYING): 57,525,263,85
        BOSS L64 Camerupt (FIRE/GROUND): 707,263,174,444
        BOSS L70 Mewtwo (PSYCHIC): 94,63,347,218
        BOSS L63 Crabominable (FIGHTING/ICE): 419,152,339,146
        IMP  L6 Pichu (ELECTRIC): 84,574,113,496
        IMP  L15 Glaceon (ICE): 524,500,694,496
        IMP  L27 Salandit (POISON/FIRE): 481,474,182,230
        IMP  L28 Noibat (FLYING/DRAGON): 16,173,156,71
        IMP  L41 Noivern (FLYING/DRAGON): 542,257,18,94
        IMP  L70 Primarina (WATER/FAIRY): 710,585,164,392
        IMP  L67 Muk (POISON/DARK): 242,157,50,474
        IMP  L53 Zoroark (DARK): 539,53,182,97
        IMP  L68 Zoroark (DARK): 492,63,182,411
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 521,473,273,98
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 9,324,164,673
        IMP  L68 Snorlax (NORMAL): 498,667,182,59
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 209,411,277,263
        IMP  L51 Shiinotic (GRASS/FAIRY): 202,138,79,74
        IMP  L20 Poipole (POISON): 51,496,64,406
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,104,496,351
        REG  L69 Lapras (WATER/ICE): 127,406,684,32
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 209,231,683,278
        REG  L35 Marowak (FIRE/GHOST): 708,9,116,67
        REG  L5 Yungoos (NORMAL): 317,43,237,279
        REG  L5 Yungoos (NORMAL): 351,216,162,496
        REG  L55 Espeon (PSYCHIC): 500,324,226,605
        REG  L5 Yungoos (NORMAL): 168,43,164,496
        REG  L33 Zubat (POISON/FLYING): 413,228,18,366
        REG  L30 Minior (ROCK/FLYING): 444,36,94,360
        REG  L27 Trumbeak (NORMAL/FLYING): 365,249,218,479
        REG  L62 Persian (NORMAL): 163,231,415,180
        REG  L14 Rattata (DARK/NORMAL): 154,168,351,382
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
