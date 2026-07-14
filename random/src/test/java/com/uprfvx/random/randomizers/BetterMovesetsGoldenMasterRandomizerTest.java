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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,38,92,126
        BOSS L45 NIDOKING (POISON/GROUND): 89,85,164,117
        BOSS L55 HITMONLEE (FIGHTING): 26,130,92,118
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,164,111
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,164,117
        BOSS L24 RAICHU (ELECTRIC): 84,129,156,164
        BOSS L37 KOFFING (POISON): 124,153,164,117
        BOSS L43 WEEZING (POISON): 124,85,164,108
        BOSS L42 RAPIDASH (FIRE): 126,63,115,45
        BOSS L38 VENOMOTH (BUG/POISON): 141,94,115,156
        BOSS L53 CLOYSTER (WATER/ICE): 59,153,156,104
        BOSS L56 LAPRAS (WATER/ICE): 56,87,115,54
        BOSS L55 HAUNTER (GHOST/POISON): 101,87,92,94
        BOSS L56 DRAGONAIR (DRAGON): 61,126,92,164
        BOSS L62 DRAGONITE (DRAGON/FLYING): 85,59,115,104
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,164,92,104
        IMP  L15 ABRA (PSYCHIC): 66,161,164,104
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,13,156,28
        IMP  L18 KADABRA (PSYCHIC): 93,161,156,104
        IMP  L16 RATICATE (NORMAL): 129,61,92,164
        IMP  L25 WARTORTLE (WATER): 61,69,164,115
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,156,92
        IMP  L35 ALAKAZAM (PSYCHIC): 94,91,92,117
        IMP  L40 VENUSAUR (GRASS/POISON): 75,63,14,45
        IMP  L45 RHYHORN (GROUND/ROCK): 89,85,156,102
        IMP  L45 GYARADOS (WATER/FLYING): 56,59,115,126
        IMP  L47 GYARADOS (WATER/FLYING): 56,58,156,92
        IMP  L61 ARCANINE (FIRE): 126,38,97,102
        IMP  L63 ARCANINE (FIRE): 53,91,92,164
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,69,115,45
        REG  L11 RATTATA (NORMAL): 129,55,164,104
        REG  L9 PIDGEY (NORMAL/FLYING): 129,156,18,92
        REG  L18 MANKEY (FIGHTING): 69,129,43,104
        REG  L19 RATTATA (NORMAL): 129,61,104,117
        REG  L37 VULPIX (FIRE): 53,91,92,38
        REG  L29 WEEZING (POISON): 124,99,156,92
        REG  L31 CLOYSTER (WATER/ICE): 59,153,110,102
        REG  L26 MANKEY (FIGHTING): 69,129,68,117
        REG  L30 HORSEA (WATER): 61,58,104,117
        REG  L29 FEAROW (NORMAL/FLYING): 65,129,45,164
        REG  L70 GYARADOS (WATER/FLYING): 56,59,102,126
        REG  L17 MACHOP (FIGHTING): 69,2,118,90
        REG  L28 EKANS (POISON): 40,44,43,157
        REG  L39 DUGTRIO (GROUND): 91,38,156,157
        REG  L33 HAUNTER (GHOST/POISON): 101,87,117,109
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,185,156,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,29,113,249
        BOSS L31 PILOSWINE (ICE/GROUND): 89,34,174,104
        BOSS L37 DRAGONAIR (DRAGON): 82,126,174,21
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,188,156,73
        BOSS L46 MACHAMP (FIGHTING): 233,89,156,174
        BOSS L40 ARIADOS (BUG/POISON): 188,101,182,207
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,223,182,59
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,174,213
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,85,105,33
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,38,115,63
        BOSS L33 ARIADOS (BUG/POISON): 188,101,104,202
        BOSS L45 MAGMAR (FIRE): 126,231,197,156
        BOSS L77 BLASTOISE (WATER): 56,58,46,175
        BOSS L58 ARCANINE (FIRE): 53,231,46,97
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,156
        IMP  L12 GASTLY (GHOST/POISON): 122,202,216,168
        IMP  L20 HAUNTER (GHOST/POISON): 247,202,195,149
        IMP  L20 ZUBAT (POISON/FLYING): 16,98,182,141
        IMP  L32 MEGANIUM (GRASS): 202,89,73,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,174,203
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,182,218
        IMP  L35 HAUNTER (GHOST/POISON): 247,87,182,213
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,92,87
        IMP  L43 GENGAR (GHOST/POISON): 247,87,156,195
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,50,192
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,174,134
        IMP  L46 ALAKAZAM (PSYCHIC): 94,8,227,50
        IMP  L50 TYPHLOSION (FIRE): 53,9,197,193
        IMP  L50 FERALIGATR (WATER): 56,231,174,89
        REG  L10 CHIKORITA (GRASS): 202,246,175,189
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,104,8
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,28,237
        REG  L25 NINETALES (FIRE): 52,185,39,207
        REG  L31 RHYDON (GROUND/ROCK): 89,223,92,157
        REG  L18 GROWLITHE (FIRE): 52,91,182,218
        REG  L23 GOLDEEN (WATER): 196,60,218,30
        REG  L28 TENTACOOL (WATER/POISON): 61,202,174,182
        REG  L28 POLIWHIRL (WATER): 61,29,114,95
        REG  L32 ONIX (ROCK/GROUND): 89,231,216,201
        REG  L6 VOLTORB (ELECTRIC): 129,205,216,0
        REG  L31 FURRET (NORMAL): 63,231,223,9
        REG  L42 GOLDUCK (WATER): 59,238,92,203
        REG  L23 PIKACHU (ELECTRIC): 9,29,104,237
        REG  L25 ELECTRODE (ELECTRIC): 29,205,156,218
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,247,336,231
        BOSS L53 WALREIN (ICE/WATER): 58,231,281,164
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,157,203,290
        BOSS L43 SEALEO (ICE/WATER): 58,231,281,55
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,315,182,153
        BOSS L50 KABUTOPS (ROCK/WATER): 157,59,14,201
        BOSS L46 HITMONCHAN (FIGHTING): 264,89,156,170
        BOSS L50 MANECTRIC (ELECTRIC): 85,242,102,38
        BOSS L46 GROWLITHE (FIRE): 126,38,92,242
        BOSS L45 KANGASKHAN (NORMAL): 38,231,203,247
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,53,214,156
        BOSS L58 SKARMORY (STEEL/FLYING): 211,157,43,228
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,58,115,247
        BOSS L56 LAPRAS (WATER/ICE): 59,85,182,195
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,113,153
        IMP  L34 MIGHTYENA (DARK): 44,38,46,247
        IMP  L40 GOLBAT (POISON/FLYING): 188,211,182,213
        IMP  L20 GROVYLE (GRASS): 202,242,164,317
        IMP  L29 LOMBRE (WATER/GRASS): 352,7,235,216
        IMP  L18 SLUGMA (FIRE): 52,157,182,237
        IMP  L29 PELIPPER (WATER/FLYING): 352,351,164,290
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,38,174,287
        IMP  L22 ZUBAT (POISON/FLYING): 332,211,174,102
        IMP  L47 ROSELIA (GRASS/POISON): 345,188,216,247
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,195,119
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 85,63,237,87
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 315,9,92,116
        IMP  L34 GROVYLE (GRASS): 202,98,14,97
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,223,156,59
        IMP  L15 MUDKIP (WATER): 352,263,174,317
        REG  L21 GEODUDE (ROCK/GROUND): 91,263,214,156
        REG  L26 MARILL (WATER): 61,280,21,204
        REG  L26 MIGHTYENA (DARK): 44,305,259,104
        REG  L33 MACHOP (FIGHTING): 264,89,25,116
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,89,247,53
        REG  L35 PLUSLE (ELECTRIC): 85,223,156,68
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,129,319,115
        REG  L30 KOFFING (POISON): 188,60,156,85
        REG  L6 SEEDOT (GRASS): 202,91,111,73
        REG  L11 MARILL (WATER): 352,91,205,111
        REG  L26 LOMBRE (WATER/GRASS): 202,196,310,290
        REG  L29 XATU (PSYCHIC/FLYING): 65,202,297,104
        REG  L29 ZUBAT (POISON/FLYING): 17,247,216,102
        REG  L34 PELIPPER (WATER/FLYING): 17,58,213,254
        REG  L5 KYOGRE (WATER): 352,351,237,219
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,189,156,33
        BOSS L49 SCIZOR (BUG/STEEL): 404,430,14,104
        BOSS L52 HIPPOWDON (GROUND): 89,423,156,38
        BOSS L57 MAGMORTAR (FIRE): 315,89,182,259
        BOSS L58 SPIRITOMB (GHOST/DARK): 247,416,194,389
        BOSS L20 CHERRIM (GRASS): 202,290,73,205
        BOSS L29 MACHOKE (FIGHTING): 280,371,164,9
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,157,113,8
        BOSS L44 SNEASEL (DARK/ICE): 420,280,14,399
        BOSS L48 WEAVILE (DARK/ICE): 400,280,404,421
        BOSS L66 WHISCASH (WATER/GROUND): 89,157,156,209
        BOSS L69 RAPIDASH (FIRE): 394,98,261,237
        BOSS L72 ALAKAZAM (PSYCHIC): 427,247,271,86
        BOSS L78 GARCHOMP (DRAGON/GROUND): 89,398,164,53
        BOSS L58 MAGMORTAR (FIRE): 53,411,270,89
        IMP  L7 STARLY (NORMAL/FLYING): 365,98,164,168
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,157,156,94
        IMP  L27 GROTLE (GRASS): 402,263,164,189
        IMP  L34 STARAVIA (NORMAL/FLYING): 17,257,92,369
        IMP  L36 STARAPTOR (NORMAL/FLYING): 17,370,355,211
        IMP  L48 HERACROSS (BUG/FIGHTING): 264,444,14,445
        IMP  L47 RAPIDASH (FIRE): 53,224,182,340
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,370,18,182
        IMP  L25 KADABRA (PSYCHIC): 60,247,269,278
        IMP  L27 GROTLE (GRASS): 202,44,14,164
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,89,156,332
        IMP  L69 RAPIDASH (FIRE): 394,398,214,156
        IMP  L73 SNORLAX (NORMAL): 387,428,156,182
        IMP  L83 SNORLAX (NORMAL): 387,242,164,156
        IMP  L60 SKUNTANK (POISON/DARK): 398,91,262,182
        REG  L5 STARLY (NORMAL/FLYING): 332,228,31,28
        REG  L29 ZUBAT (POISON/FLYING): 365,211,174,428
        REG  L36 SWINUB (ICE/GROUND): 89,157,218,115
        REG  L6 GEODUDE (ROCK/GROUND): 246,189,446,33
        REG  L21 CARNIVINE (GRASS): 202,44,203,230
        REG  L21 DRIFLOON (GHOST/FLYING): 466,451,207,216
        REG  L36 MURKROW (DARK/FLYING): 399,94,114,373
        REG  L39 MURKROW (DARK/FLYING): 399,247,103,257
        REG  L58 PELIPPER (WATER/FLYING): 56,58,54,290
        REG  L32 EEVEE (NORMAL): 98,231,218,281
        REG  L48 SEAKING (WATER): 291,60,240,237
        REG  L42 GOLBAT (POISON/FLYING): 403,188,18,445
        REG  L23 BUIZEL (WATER): 291,280,445,29
        REG  L42 MAGNETON (ELECTRIC/STEEL): 85,430,113,103
        REG  L56 EMPOLEON (WATER/STEEL): 61,58,89,324
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,428,406,324
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,412,347,285
        BOSS L72 Lucario (FIGHTING/STEEL): 411,299,156,231
        BOSS L28 Flaaffy (ELECTRIC): 451,496,182,219
        BOSS L48 Haxorus (DRAGON): 200,280,14,468
        BOSS L50 Cofagrigus (GHOST): 247,399,114,334
        BOSS L67 Simipour (WATER): 401,8,182,270
        BOSS L76 Clefable (NORMAL): 63,53,86,218
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,324,113,228
        BOSS L49 Carracosta (WATER/ROCK): 401,89,446,276
        BOSS L56 Lucario (FIGHTING/STEEL): 231,370,97,46
        BOSS L73 Golurk (GROUND/GHOST): 414,428,397,477
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 280,348,113,182
        BOSS L75 Arcanine (FIRE): 394,200,97,213
        BOSS L75 Glaceon (ICE): 58,500,174,204
        IMP  L8 Tepig (FIRE): 488,283,92,207
        IMP  L48 Cryogonal (ICE): 58,430,113,512
        IMP  L23 Pansage (GRASS): 345,44,156,317
        IMP  L31 Tranquill (NORMAL/FLYING): 263,211,92,369
        IMP  L39 Unfezant (NORMAL/FLYING): 403,416,234,216
        IMP  L46 Cryogonal (ICE): 62,76,114,153
        IMP  L55 Unfezant (NORMAL/FLYING): 98,369,197,237
        IMP  L55 Simisear (FIRE): 257,276,417,207
        IMP  L62 Unfezant (NORMAL/FLYING): 263,211,234,104
        IMP  L62 Flygon (GROUND/DRAGON): 523,157,468,257
        IMP  L65 Unfezant (NORMAL/FLYING): 98,211,273,95
        IMP  L65 Eelektross (ELECTRIC): 528,202,156,242
        IMP  L41 Simisear (FIRE): 257,421,164,156
        IMP  L48 Unfezant (NORMAL/FLYING): 263,211,273,156
        IMP  L74 Klinklang (STEEL): 544,528,277,92
        REG  L26 Blitzle (ELECTRIC): 209,228,28,156
        REG  L63 Hitmonlee (FIGHTING): 26,89,272,92
        REG  L63 Hitmonchan (FIGHTING): 409,263,197,68
        REG  L56 Unfezant (NORMAL/FLYING): 416,369,237,43
        REG  L47 Boldore (ROCK): 479,263,484,356
        REG  L45 Swinub (ICE/GROUND): 333,276,175,218
        REG  L32 Scolipede (BUG/POISON): 404,89,92,228
        REG  L65 Hitmontop (FIGHTING): 370,444,252,218
        REG  L52 Amoonguss (GRASS/POISON): 202,499,77,156
        REG  L64 Archeops (ROCK/FLYING): 365,369,406,97
        REG  L54 Metang (STEEL/PSYCHIC): 428,309,247,357
        REG  L60 Wooper (WATER/GROUND): 401,8,254,104
        REG  L67 Emboar (FIRE/FIGHTING): 315,411,182,335
        REG  L47 Krookodile (GROUND/DARK): 242,444,259,373
        REG  L25 Litwick (GHOST/FIRE): 481,412,347,445
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 389,304,92,259
        BOSS L41 Weezing (POISON): 499,126,254,399
        BOSS L5 Zigzagoon (NORMAL): 496,168,164,207
        BOSS L51 Dusclops (GHOST): 325,280,220,157
        BOSS L52 Froslass (ICE/GHOST): 196,358,415,180
        BOSS L57 Claydol (GROUND/PSYCHIC): 89,324,164,290
        BOSS L14 Machop (FIGHTING): 490,168,164,216
        BOSS L28 Slaking (NORMAL): 263,523,174,281
        BOSS L44 Whiscash (WATER/GROUND): 401,416,92,428
        BOSS L70 Sharpedo (WATER/DARK): 453,38,97,184
        BOSS L71 Dusknoir (GHOST): 325,264,269,8
        BOSS L73 Altaria (DRAGON/FLYING): 143,290,46,207
        BOSS L77 Carbink (ROCK/FAIRY): 585,94,446,444
        BOSS L57 Cradily (ROCK/GRASS): 157,89,235,51
        BOSS L57 Milotic (WATER): 503,406,164,392
        IMP  L18 Slugma (FIRE): 510,317,156,207
        IMP  L31 Wailmer (WATER): 503,499,182,218
        IMP  L18 Wailmer (WATER): 503,196,156,487
        IMP  L31 Shroomish (GRASS): 412,409,207,290
        IMP  L37 Swellow (NORMAL/FLYING): 98,211,432,213
        IMP  L37 Wailord (WATER): 323,58,164,340
        IMP  L46 Delcatty (NORMAL): 252,247,226,583
        IMP  L24 Shroomish (GRASS): 202,29,590,474
        IMP  L24 Slugma (FIRE): 510,237,156,334
        IMP  L32 Sharpedo (WATER/DARK): 399,423,182,269
        IMP  L55 Camerupt (FIRE/GROUND): 315,414,174,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 490,421,97,104
        IMP  L50 Sceptile (GRASS): 437,264,156,92
        IMP  L64 Altaria (DRAGON/FLYING): 200,211,297,89
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 428,514,113,288
        REG  L4 Zigzagoon (NORMAL): 496,196,204,447
        REG  L25 Slugma (FIRE): 488,499,151,237
        REG  L39 Claydol (GROUND/PSYCHIC): 89,246,278,347
        REG  L36 Golbat (POISON/FLYING): 413,247,259,103
        REG  L34 Golbat (POISON/FLYING): 188,369,213,162
        REG  L33 Roselia (GRASS/POISON): 202,326,247,590
        REG  L43 Solrock (ROCK/PSYCHIC): 157,89,285,290
        REG  L49 Jellicent (WATER/GHOST): 323,399,219,277
        REG  L37 Skarmory (STEEL/FLYING): 442,157,404,364
        REG  L41 Clamperl (WATER): 503,59,334,287
        REG  L39 Tentacruel (WATER/POISON): 61,398,392,92
        REG  L48 Honchkrow (DARK/FLYING): 389,143,119,247
        REG  L53 Flygon (GROUND/DRAGON): 89,242,213,201
        REG  L23 Grimer (POISON): 398,425,106,259
        REG  L51 Mightyena (DARK): 242,231,156,218
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 490,265,227,43
        BOSS L47 Bewear (NORMAL/FIGHTING): 276,442,156,218
        BOSS L56 Probopass (ROCK/STEEL): 408,605,446,201
        BOSS L41 Golisopod (BUG/WATER): 141,675,156,590
        BOSS L66 Froslass (ICE/GHOST): 466,196,191,263
        BOSS L66 Mandibuzz (DARK/FLYING): 399,369,366,416
        BOSS L57 Dugtrio (GROUND/STEEL): 89,421,182,104
        BOSS L52 Sableye (DARK/GHOST): 399,264,261,271
        BOSS L65 Crobat (POISON/FLYING): 413,211,164,162
        BOSS L64 Masquerain (BUG/FLYING): 405,202,366,483
        BOSS L66 Hydreigon (DARK/DRAGON): 406,414,156,269
        BOSS L65 Gyarados (WATER/FLYING): 401,442,164,180
        BOSS L64 Camerupt (FIRE/GROUND): 414,284,174,182
        BOSS L70 Mewtwo (PSYCHIC): 94,129,113,271
        BOSS L63 Crabominable (FIGHTING/ICE): 276,428,339,334
        IMP  L6 Pichu (ELECTRIC): 527,343,164,86
        IMP  L15 Glaceon (ICE): 196,237,92,203
        IMP  L27 Salandit (POISON/FIRE): 481,421,261,28
        IMP  L28 Noibat (FLYING/DRAGON): 314,247,156,259
        IMP  L41 Noivern (FLYING/DRAGON): 314,53,182,156
        IMP  L70 Primarina (WATER/FAIRY): 664,585,182,218
        IMP  L67 Muk (POISON/DARK): 398,7,269,8
        IMP  L53 Zoroark (DARK): 399,304,46,262
        IMP  L68 Zoroark (DARK): 492,369,46,263
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 528,282,278,94
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 94,528,113,115
        IMP  L68 Snorlax (NORMAL): 387,264,207,247
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 528,324,92,411
        IMP  L51 Shiinotic (GRASS/FAIRY): 412,188,73,79
        IMP  L20 Poipole (POISON): 398,406,182,380
        REG  L6 Yungoos (NORMAL): 162,168,590,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 283,317,164,526
        REG  L69 Lapras (WATER/ICE): 420,324,442,156
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 528,282,273,117
        REG  L35 Marowak (FIRE/GHOST): 708,89,446,37
        REG  L5 Yungoos (NORMAL): 162,168,182,156
        REG  L5 Yungoos (NORMAL): 283,317,104,182
        REG  L55 Espeon (PSYCHIC): 500,324,231,207
        REG  L5 Yungoos (NORMAL): 283,168,351,104
        REG  L33 Zubat (POISON/FLYING): 413,428,257,263
        REG  L30 Minior (ROCK/FLYING): 444,442,207,94
        REG  L27 Trumbeak (NORMAL/FLYING): 65,280,45,164
        REG  L62 Persian (NORMAL): 304,369,316,103
        REG  L14 Rattata (DARK/NORMAL): 283,168,351,104
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
