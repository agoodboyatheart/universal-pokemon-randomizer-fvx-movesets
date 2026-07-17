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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,30,92,39
        BOSS L45 NIDOKING (POISON/GROUND): 89,126,156,61
        BOSS L55 HITMONLEE (FIGHTING): 136,36,156,96
        BOSS L12 GEODUDE (ROCK/GROUND): 99,69,92,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,33,115,104
        BOSS L24 RAICHU (ELECTRIC): 84,5,86,66
        BOSS L37 KOFFING (POISON): 124,87,92,126
        BOSS L43 WEEZING (POISON): 124,85,156,108
        BOSS L42 RAPIDASH (FIRE): 126,34,92,156
        BOSS L38 VENOMOTH (BUG/POISON): 76,94,77,149
        BOSS L53 CLOYSTER (WATER/ICE): 58,36,115,43
        BOSS L56 LAPRAS (WATER/ICE): 56,87,164,109
        BOSS L55 HAUNTER (GHOST/POISON): 101,138,95,102
        BOSS L56 DRAGONAIR (DRAGON): 21,126,92,164
        BOSS L62 DRAGONITE (DRAGON/FLYING): 61,21,164,85
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,129,156,28
        IMP  L15 ABRA (PSYCHIC): 5,69,92,66
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,129,164,156
        IMP  L18 KADABRA (PSYCHIC): 93,161,86,149
        IMP  L16 RATICATE (NORMAL): 129,55,92,156
        IMP  L25 WARTORTLE (WATER): 55,69,92,66
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,164,95
        IMP  L35 ALAKAZAM (PSYCHIC): 60,91,105,66
        IMP  L40 VENUSAUR (GRASS/POISON): 76,34,14,92
        IMP  L45 RHYHORN (GROUND/ROCK): 91,34,156,87
        IMP  L45 GYARADOS (WATER/FLYING): 56,130,115,87
        IMP  L47 GYARADOS (WATER/FLYING): 61,126,156,82
        IMP  L61 ARCANINE (FIRE): 126,91,115,82
        IMP  L63 ARCANINE (FIRE): 53,91,92,46
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,91,14,45
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,115,28
        REG  L18 MANKEY (FIGHTING): 66,6,102,164
        REG  L19 RATTATA (NORMAL): 98,61,104,92
        REG  L37 VULPIX (FIRE): 53,98,39,115
        REG  L29 WEEZING (POISON): 123,33,102,104
        REG  L31 CLOYSTER (WATER/ICE): 62,153,164,92
        REG  L26 MANKEY (FIGHTING): 66,157,2,118
        REG  L30 HORSEA (WATER): 61,59,38,104
        REG  L29 FEAROW (NORMAL/FLYING): 129,64,18,45
        REG  L70 GYARADOS (WATER/FLYING): 56,38,82,115
        REG  L17 MACHOP (FIGHTING): 69,99,118,90
        REG  L28 EKANS (POISON): 40,157,137,44
        REG  L39 DUGTRIO (GROUND): 91,157,164,163
        REG  L33 HAUNTER (GHOST/POISON): 101,72,164,109
        """);
        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,228
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,168,211
        BOSS L31 PILOSWINE (ICE/GROUND): 89,157,174,196
        BOSS L37 DRAGONAIR (DRAGON): 225,126,97,21
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 76,73,246,23
        BOSS L46 MACHAMP (FIGHTING): 233,126,227,216
        BOSS L40 ARIADOS (BUG/POISON): 188,101,174,60
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,53,114,8
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,240,156
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,58,113,107
        BOSS L41 JUMPLUFF (GRASS/FLYING): 72,38,78,174
        BOSS L33 ARIADOS (BUG/POISON): 188,101,50,94
        BOSS L45 MAGMAR (FIRE): 126,9,156,231
        BOSS L77 BLASTOISE (WATER): 56,229,46,110
        BOSS L58 ARCANINE (FIRE): 53,34,241,225
        IMP  L12 GASTLY (GHOST/POISON): 122,202,174,168
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,180
        IMP  L20 HAUNTER (GHOST/POISON): 247,168,95,218
        IMP  L20 ZUBAT (POISON/FLYING): 16,98,197,185
        IMP  L32 MEGANIUM (GRASS): 202,246,77,115
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,33,86,182
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,174,109
        IMP  L35 HAUNTER (GHOST/POISON): 101,85,109,104
        IMP  L43 GENGAR (GHOST/POISON): 101,149,94,9
        IMP  L43 ALAKAZAM (PSYCHIC): 60,8,156,29
        IMP  L43 ALAKAZAM (PSYCHIC): 94,9,227,223
        IMP  L46 ALAKAZAM (PSYCHIC): 60,7,182,223
        IMP  L50 TYPHLOSION (FIRE): 7,89,241,193
        IMP  L50 FERALIGATR (WATER): 56,223,240,89
        REG  L10 CHIKORITA (GRASS): 202,189,203,246
        REG  L20 QUAGSIRE (WATER/GROUND): 189,249,240,201
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,168,207,213
        REG  L25 NINETALES (FIRE): 52,185,95,98
        REG  L31 RHYDON (GROUND/ROCK): 157,23,156,39
        REG  L18 GROWLITHE (FIRE): 52,44,104,129
        REG  L23 GOLDEEN (WATER): 129,64,203,60
        REG  L28 TENTACOOL (WATER/POISON): 61,62,114,51
        REG  L28 POLIWHIRL (WATER): 55,249,182,111
        REG  L32 ONIX (ROCK/GROUND): 157,89,46,201
        REG  L6 VOLTORB (ELECTRIC): 205,218,33,129
        REG  L31 FURRET (NORMAL): 98,223,218,228
        REG  L42 GOLDUCK (WATER): 196,94,113,207
        REG  L23 PIKACHU (ELECTRIC): 84,205,104,227
        REG  L25 ELECTRODE (ELECTRIC): 49,205,182,129
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 168,36,46,218
        BOSS L53 WALREIN (ICE/WATER): 62,263,254,157
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,157,46,23
        BOSS L43 SEALEO (ICE/WATER): 58,231,281,34
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,157,156,241
        BOSS L50 KABUTOPS (ROCK/WATER): 317,280,156,282
        BOSS L46 HITMONCHAN (FIGHTING): 136,263,339,213
        BOSS L50 MANECTRIC (ELECTRIC): 85,168,92,38
        BOSS L46 GROWLITHE (FIRE): 53,168,97,43
        BOSS L45 KANGASKHAN (NORMAL): 146,89,156,332
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,114,164
        BOSS L58 SKARMORY (STEEL/FLYING): 211,38,174,269
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 263,156,182,247
        BOSS L56 LAPRAS (WATER/ICE): 59,85,174,34
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,223,334,97
        IMP  L34 MIGHTYENA (DARK): 168,343,184,207
        IMP  L40 GOLBAT (POISON/FLYING): 332,202,174,38
        IMP  L20 GROVYLE (GRASS): 71,9,219,242
        IMP  L29 LOMBRE (WATER/GRASS): 331,55,235,252
        IMP  L18 SLUGMA (FIRE): 52,92,151,263
        IMP  L29 PELIPPER (WATER/FLYING): 55,239,17,129
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,5,156,240
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,202,310
        IMP  L47 ROSELIA (GRASS/POISON): 202,247,73,38
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,126,92,168
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,63,156,214
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 53,317,92,9
        IMP  L34 GROVYLE (GRASS): 72,242,73,9
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,23,164,8
        IMP  L15 MUDKIP (WATER): 55,317,240,23
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 145,69,216,39
        REG  L26 MIGHTYENA (DARK): 168,310,336,259
        REG  L33 MACHOP (FIGHTING): 233,5,182,227
        REG  L41 SOLROCK (ROCK/PSYCHIC): 317,290,315,322
        REG  L35 PLUSLE (ELECTRIC): 87,69,290,182
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,205,129,203
        REG  L30 KOFFING (POISON): 188,126,87,216
        REG  L6 SEEDOT (GRASS): 202,206,73,207
        REG  L11 MARILL (WATER): 145,91,237,207
        REG  L26 LOMBRE (WATER/GRASS): 202,310,230,8
        REG  L29 XATU (PSYCHIC/FLYING): 65,168,237,216
        REG  L29 ZUBAT (POISON/FLYING): 17,141,104,228
        REG  L34 PELIPPER (WATER/FLYING): 332,58,48,98
        REG  L5 KYOGRE (WATER): 352,351,216,317
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 317,397,335,33
        BOSS L49 SCIZOR (BUG/STEEL): 369,332,182,458
        BOSS L52 HIPPOWDON (GROUND): 89,422,174,203
        BOSS L57 MAGMORTAR (FIRE): 126,85,164,109
        BOSS L58 SPIRITOMB (GHOST/DARK): 425,262,108,228
        BOSS L20 CHERRIM (GRASS): 412,205,235,320
        BOSS L29 MACHOKE (FIGHTING): 2,157,182,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 58,89,164,452
        BOSS L44 SNEASEL (DARK/ICE): 419,98,258,280
        BOSS L48 WEAVILE (DARK/ICE): 372,279,269,420
        BOSS L66 WHISCASH (WATER/GROUND): 426,58,164,222
        BOSS L69 RAPIDASH (FIRE): 394,38,164,76
        BOSS L72 ALAKAZAM (PSYCHIC): 94,264,182,227
        BOSS L78 GARCHOMP (DRAGON/GROUND): 91,424,28,401
        BOSS L58 MAGMORTAR (FIRE): 436,411,156,5
        IMP  L7 STARLY (NORMAL/FLYING): 129,228,355,365
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,89,113,430
        IMP  L27 GROTLE (GRASS): 331,44,164,328
        IMP  L34 STARAVIA (NORMAL/FLYING): 332,228,156,38
        IMP  L36 STARAPTOR (NORMAL/FLYING): 38,228,18,297
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,168,421,30
        IMP  L47 RAPIDASH (FIRE): 394,231,164,38
        IMP  L42 STARAPTOR (NORMAL/FLYING): 36,228,203,370
        IMP  L25 KADABRA (PSYCHIC): 428,247,347,447
        IMP  L27 GROTLE (GRASS): 75,44,219,328
        IMP  L61 HERACROSS (BUG/FIGHTING): 370,89,444,421
        IMP  L69 RAPIDASH (FIRE): 126,98,156,97
        IMP  L73 SNORLAX (NORMAL): 34,242,133,58
        IMP  L83 SNORLAX (NORMAL): 290,7,281,445
        IMP  L60 SKUNTANK (POISON/DARK): 400,398,46,263
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 17,228,369,104
        REG  L36 SWINUB (ICE/GROUND): 333,91,317,34
        REG  L6 GEODUDE (ROCK/GROUND): 317,33,360,445
        REG  L21 CARNIVINE (GRASS): 22,371,78,275
        REG  L21 DRIFLOON (GHOST/FLYING): 247,451,347,114
        REG  L36 MURKROW (DARK/FLYING): 372,65,244,196
        REG  L39 MURKROW (DARK/FLYING): 399,196,119,213
        REG  L58 PELIPPER (WATER/FLYING): 56,196,392,97
        REG  L32 EEVEE (NORMAL): 343,91,204,216
        REG  L48 SEAKING (WATER): 291,340,213,58
        REG  L42 GOLBAT (POISON/FLYING): 403,202,95,428
        REG  L23 BUIZEL (WATER): 352,290,92,316
        REG  L42 MAGNETON (ELECTRIC/STEEL): 435,430,290,103
        REG  L56 EMPOLEON (WATER/STEEL): 61,419,164,54
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,94
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,430,219,247
        BOSS L72 Lucario (FIGHTING/STEEL): 418,198,339,299
        BOSS L28 Flaaffy (ELECTRIC): 84,280,109,36
        BOSS L48 Haxorus (DRAGON): 530,276,349,216
        BOSS L50 Cofagrigus (GHOST): 101,412,417,203
        BOSS L67 Simipour (WATER): 362,280,317,421
        BOSS L76 Clefable (NORMAL): 34,409,322,182
        BOSS L28 Emolga (ELECTRIC/FLYING): 403,282,209,310
        BOSS L49 Carracosta (WATER/ROCK): 444,282,92,110
        BOSS L56 Lucario (FIGHTING/STEEL): 418,89,14,98
        BOSS L73 Golurk (GROUND/GHOST): 414,7,397,264
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 428,332,95,348
        BOSS L75 Arcanine (FIRE): 394,36,234,422
        BOSS L75 Glaceon (ICE): 58,485,273,281
        IMP  L8 Tepig (FIRE): 488,249,269,222
        IMP  L48 Cryogonal (ICE): 62,430,115,512
        IMP  L23 Pansage (GRASS): 22,496,73,282
        IMP  L31 Tranquill (NORMAL/FLYING): 263,332,197,369
        IMP  L39 Unfezant (NORMAL/FLYING): 98,211,273,526
        IMP  L46 Cryogonal (ICE): 196,76,277,334
        IMP  L55 Unfezant (NORMAL/FLYING): 143,263,366,234
        IMP  L55 Simisear (FIRE): 7,512,269,242
        IMP  L62 Unfezant (NORMAL/FLYING): 403,63,156,164
        IMP  L62 Flygon (GROUND/DRAGON): 91,9,201,337
        IMP  L65 Unfezant (NORMAL/FLYING): 416,143,355,45
        IMP  L65 Eelektross (ELECTRIC): 528,369,86,337
        IMP  L41 Simisear (FIRE): 257,512,261,276
        IMP  L48 Unfezant (NORMAL/FLYING): 13,211,366,156
        IMP  L74 Klinklang (STEEL): 544,528,508,319
        REG  L26 Blitzle (ELECTRIC): 351,228,24,213
        REG  L63 Hitmonlee (FIGHTING): 183,282,418,203
        REG  L63 Hitmonchan (FIGHTING): 327,444,526,418
        REG  L56 Unfezant (NORMAL/FLYING): 263,211,273,355
        REG  L47 Boldore (ROCK): 157,36,414,156
        REG  L45 Swinub (ICE/GROUND): 419,317,426,203
        REG  L32 Scolipede (BUG/POISON): 404,523,97,237
        REG  L65 Hitmontop (FIGHTING): 183,332,168,237
        REG  L52 Amoonguss (GRASS/POISON): 412,499,492,74
        REG  L64 Archeops (ROCK/FLYING): 444,421,340,97
        REG  L54 Metang (STEEL/PSYCHIC): 442,317,397,184
        REG  L60 Wooper (WATER/GROUND): 89,401,105,8
        REG  L67 Emboar (FIRE/FIGHTING): 359,488,528,237
        REG  L47 Krookodile (GROUND/DARK): 89,263,28,231
        REG  L25 Litwick (GHOST/FIRE): 101,481,107,286
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,231,164,305
        BOSS L41 Weezing (POISON): 124,60,390,254
        BOSS L5 Zigzagoon (NORMAL): 352,86,343,189
        BOSS L51 Dusclops (GHOST): 425,196,261,280
        BOSS L52 Froslass (ICE/GHOST): 247,577,109,180
        BOSS L57 Claydol (GROUND/PSYCHIC): 326,605,115,471
        BOSS L14 Machop (FIGHTING): 2,418,339,43
        BOSS L28 Slaking (NORMAL): 10,421,133,157
        BOSS L44 Whiscash (WATER/GROUND): 330,340,92,426
        BOSS L70 Sharpedo (WATER/DARK): 372,58,46,362
        BOSS L71 Dusknoir (GHOST): 101,157,114,7
        BOSS L73 Altaria (DRAGON/FLYING): 407,53,92,538
        BOSS L77 Carbink (ROCK/FAIRY): 585,479,219,267
        BOSS L57 Cradily (ROCK/GRASS): 317,72,156,254
        BOSS L57 Milotic (WATER): 56,225,219,523
        IMP  L18 Slugma (FIRE): 510,496,281,174
        IMP  L31 Wailmer (WATER): 362,58,182,38
        IMP  L18 Wailmer (WATER): 352,237,156,214
        IMP  L31 Shroomish (GRASS): 412,263,74,92
        IMP  L37 Swellow (NORMAL/FLYING): 98,413,164,366
        IMP  L37 Wailord (WATER): 323,34,133,499
        IMP  L46 Delcatty (NORMAL): 304,426,86,583
        IMP  L24 Shroomish (GRASS): 402,496,78,313
        IMP  L24 Slugma (FIRE): 488,246,115,496
        IMP  L32 Sharpedo (WATER/DARK): 399,305,184,63
        IMP  L55 Camerupt (FIRE/GROUND): 126,523,174,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 488,136,297,89
        IMP  L50 Sceptile (GRASS): 437,406,97,228
        IMP  L64 Altaria (DRAGON/FLYING): 332,53,468,585
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 264,514,227,605
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,123,207,290
        REG  L39 Claydol (GROUND/PSYCHIC): 60,324,106,322
        REG  L36 Golbat (POISON/FLYING): 474,211,156,164
        REG  L34 Golbat (POISON/FLYING): 332,247,369,599
        REG  L33 Roselia (GRASS/POISON): 412,247,230,343
        REG  L43 Solrock (ROCK/PSYCHIC): 317,523,63,207
        REG  L49 Jellicent (WATER/GHOST): 362,202,506,94
        REG  L37 Skarmory (STEEL/FLYING): 65,168,430,18
        REG  L41 Clamperl (WATER): 330,59,109,590
        REG  L39 Tentacruel (WATER/POISON): 474,62,392,367
        REG  L48 Honchkrow (DARK/FLYING): 492,65,355,297
        REG  L53 Flygon (GROUND/DRAGON): 407,7,332,48
        REG  L23 Grimer (POISON): 491,122,269,216
        REG  L51 Mightyena (DARK): 372,343,336,91
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,526,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 264,707,220,530
        BOSS L56 Probopass (ROCK/STEEL): 430,521,156,161
        BOSS L41 Golisopod (BUG/WATER): 42,332,220,196
        BOSS L66 Froslass (ICE/GHOST): 420,358,92,311
        BOSS L66 Mandibuzz (DARK/FLYING): 492,19,269,334
        BOSS L57 Dugtrio (GROUND/STEEL): 91,444,262,442
        BOSS L52 Sableye (DARK/GHOST): 425,490,105,408
        BOSS L65 Crobat (POISON/FLYING): 440,141,355,332
        BOSS L64 Masquerain (BUG/FLYING): 403,412,564,247
        BOSS L66 Hydreigon (DARK/DRAGON): 406,562,46,304
        BOSS L65 Gyarados (WATER/FLYING): 127,242,349,126
        BOSS L64 Camerupt (FIRE/GROUND): 315,707,201,23
        BOSS L70 Mewtwo (PSYCHIC): 427,53,182,149
        BOSS L63 Crabominable (FIGHTING/ICE): 8,9,339,444
        IMP  L6 Pichu (ELECTRIC): 527,574,156,268
        IMP  L15 Glaceon (ICE): 524,352,321,247
        IMP  L27 Salandit (POISON/FIRE): 481,337,261,3
        IMP  L28 Noibat (FLYING/DRAGON): 314,211,432,44
        IMP  L41 Noivern (FLYING/DRAGON): 19,411,236,421
        IMP  L70 Primarina (WATER/FAIRY): 585,56,47,472
        IMP  L67 Muk (POISON/DARK): 305,228,50,599
        IMP  L53 Zoroark (DARK): 675,53,97,184
        IMP  L68 Zoroark (DARK): 555,247,197,326
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 609,473,92,280
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,98,164,204
        IMP  L68 Snorlax (NORMAL): 263,402,526,590
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 473,435,97,231
        IMP  L51 Shiinotic (GRASS/FAIRY): 605,86,324,72
        IMP  L20 Poipole (POISON): 398,31,164,324
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 497,168,207,92
        REG  L69 Lapras (WATER/ICE): 362,58,109,47
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 521,94,282,343
        REG  L35 Marowak (FIRE/GHOST): 506,89,53,180
        REG  L5 Yungoos (NORMAL): 371,218,496,351
        REG  L5 Yungoos (NORMAL): 279,526,283,33
        REG  L55 Espeon (PSYCHIC): 324,343,94,247
        REG  L5 Yungoos (NORMAL): 351,218,283,162
        REG  L33 Zubat (POISON/FLYING): 19,141,202,92
        REG  L30 Minior (ROCK/FLYING): 512,428,246,523
        REG  L27 Trumbeak (NORMAL/FLYING): 64,369,92,31
        REG  L62 Persian (NORMAL): 304,282,421,45
        REG  L14 Rattata (DARK/NORMAL): 162,228,496,279
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
