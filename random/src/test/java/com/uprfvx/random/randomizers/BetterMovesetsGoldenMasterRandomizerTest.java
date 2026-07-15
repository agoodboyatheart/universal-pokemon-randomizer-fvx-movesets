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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,38,92,87
        BOSS L45 NIDOKING (POISON/GROUND): 89,126,164,102
        BOSS L55 HITMONLEE (FIGHTING): 26,38,92,96
        BOSS L12 GEODUDE (ROCK/GROUND): 99,69,164,111
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,92,149
        BOSS L24 RAICHU (ELECTRIC): 84,69,92,102
        BOSS L37 KOFFING (POISON): 124,153,156,108
        BOSS L43 WEEZING (POISON): 124,85,92,108
        BOSS L42 RAPIDASH (FIRE): 126,38,92,39
        BOSS L38 VENOMOTH (BUG/POISON): 141,76,18,104
        BOSS L53 CLOYSTER (WATER/ICE): 58,38,92,61
        BOSS L56 LAPRAS (WATER/ICE): 58,76,92,85
        BOSS L55 HAUNTER (GHOST/POISON): 101,94,164,95
        BOSS L56 DRAGONAIR (DRAGON): 61,58,156,82
        BOSS L62 DRAGONITE (DRAGON/FLYING): 85,38,164,58
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,92,28,18
        IMP  L15 ABRA (PSYCHIC): 161,69,86,118
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,13,115,156
        IMP  L18 KADABRA (PSYCHIC): 93,161,86,102
        IMP  L16 RATICATE (NORMAL): 129,61,164,39
        IMP  L25 WARTORTLE (WATER): 61,69,115,39
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,156,102
        IMP  L35 ALAKAZAM (PSYCHIC): 94,91,86,105
        IMP  L40 VENUSAUR (GRASS/POISON): 75,38,115,14
        IMP  L45 RHYHORN (GROUND/ROCK): 89,126,156,39
        IMP  L45 GYARADOS (WATER/FLYING): 56,38,92,87
        IMP  L47 GYARADOS (WATER/FLYING): 61,63,164,82
        IMP  L61 ARCANINE (FIRE): 53,91,156,130
        IMP  L63 ARCANINE (FIRE): 53,63,46,92
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,89,14,45
        REG  L11 RATTATA (NORMAL): 129,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,115,28,18
        REG  L18 MANKEY (FIGHTING): 69,129,92,43
        REG  L19 RATTATA (NORMAL): 129,61,39,156
        REG  L37 VULPIX (FIRE): 53,91,92,104
        REG  L29 WEEZING (POISON): 124,99,156,92
        REG  L31 CLOYSTER (WATER/ICE): 59,38,48,110
        REG  L26 MANKEY (FIGHTING): 69,129,118,156
        REG  L30 HORSEA (WATER): 61,59,164,43
        REG  L29 FEAROW (NORMAL/FLYING): 65,129,119,92
        REG  L70 GYARADOS (WATER/FLYING): 61,85,102,82
        REG  L17 MACHOP (FIGHTING): 69,2,90,102
        REG  L28 EKANS (POISON): 40,44,137,156
        REG  L39 DUGTRIO (GROUND): 89,38,104,157
        REG  L33 HAUNTER (GHOST/POISON): 101,85,138,95
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,185,156,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,29,113,92
        BOSS L31 PILOSWINE (ICE/GROUND): 58,89,182,203
        BOSS L37 DRAGONAIR (DRAGON): 82,53,86,59
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 202,94,73,236
        BOSS L46 MACHAMP (FIGHTING): 238,8,227,126
        BOSS L40 ARIADOS (BUG/POISON): 188,94,169,141
        BOSS L47 DRAGONITE (DRAGON/FLYING): 239,87,86,59
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,114,240
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,105,94
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,38,115,235
        BOSS L33 ARIADOS (BUG/POISON): 188,101,92,141
        BOSS L45 MAGMAR (FIRE): 126,231,182,109
        BOSS L77 BLASTOISE (WATER): 56,44,114,39
        BOSS L58 ARCANINE (FIRE): 126,245,92,242
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,218
        IMP  L12 GASTLY (GHOST/POISON): 122,202,95,168
        IMP  L20 HAUNTER (GHOST/POISON): 247,202,174,207
        IMP  L20 ZUBAT (POISON/FLYING): 16,185,197,211
        IMP  L32 MEGANIUM (GRASS): 202,89,73,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,86,48
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,195,95
        IMP  L35 HAUNTER (GHOST/POISON): 247,87,114,212
        IMP  L43 GENGAR (GHOST/POISON): 247,7,182,109
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,227,92
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,174,8
        IMP  L46 ALAKAZAM (PSYCHIC): 94,8,115,237
        IMP  L50 TYPHLOSION (FIRE): 126,223,197,9
        IMP  L50 FERALIGATR (WATER): 56,89,46,240
        REG  L10 CHIKORITA (GRASS): 202,246,230,216
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,39,111
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,16,213
        REG  L25 NINETALES (FIRE): 52,91,180,46
        REG  L31 RHYDON (GROUND/ROCK): 89,242,92,53
        REG  L18 GROWLITHE (FIRE): 52,242,182,225
        REG  L23 GOLDEEN (WATER): 30,196,39,240
        REG  L28 TENTACOOL (WATER/POISON): 61,62,237,202
        REG  L28 POLIWHIRL (WATER): 61,196,54,170
        REG  L32 ONIX (ROCK/GROUND): 89,231,103,157
        REG  L6 VOLTORB (ELECTRIC): 129,205,182,156
        REG  L31 FURRET (NORMAL): 38,7,203,92
        REG  L42 GOLDUCK (WATER): 94,223,104,50
        REG  L23 PIKACHU (ELECTRIC): 9,29,186,174
        REG  L25 ELECTRODE (ELECTRIC): 120,29,203,174
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,281,247
        BOSS L53 WALREIN (ICE/WATER): 59,157,92,89
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,290,336,317
        BOSS L43 SEALEO (ICE/WATER): 62,157,174,254
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,157,164,133
        BOSS L50 KABUTOPS (ROCK/WATER): 157,280,182,109
        BOSS L46 HITMONCHAN (FIGHTING): 183,89,182,157
        BOSS L50 MANECTRIC (ELECTRIC): 85,242,316,38
        BOSS L46 GROWLITHE (FIRE): 315,231,46,97
        BOSS L45 KANGASKHAN (NORMAL): 252,247,50,116
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,349,156
        BOSS L58 SKARMORY (STEEL/FLYING): 143,38,174,97
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 157,58,113,237
        BOSS L56 LAPRAS (WATER/ICE): 56,87,156,59
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,188,164,182
        IMP  L34 MIGHTYENA (DARK): 44,231,46,289
        IMP  L40 GOLBAT (POISON/FLYING): 17,211,164,48
        IMP  L20 GROVYLE (GRASS): 202,225,92,9
        IMP  L29 LOMBRE (WATER/GRASS): 352,8,235,7
        IMP  L18 SLUGMA (FIRE): 52,157,113,123
        IMP  L29 PELIPPER (WATER/FLYING): 332,196,97,104
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,223,174,111
        IMP  L22 ZUBAT (POISON/FLYING): 17,202,174,218
        IMP  L47 ROSELIA (GRASS/POISON): 76,188,92,191
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,126,164,64
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 87,63,86,216
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 126,9,223,64
        IMP  L34 GROVYLE (GRASS): 202,9,97,242
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,223,174,111
        IMP  L15 MUDKIP (WATER): 352,263,164,287
        REG  L21 GEODUDE (ROCK/GROUND): 91,290,335,156
        REG  L26 MARILL (WATER): 61,196,21,218
        REG  L26 MIGHTYENA (DARK): 44,305,156,102
        REG  L33 MACHOP (FIGHTING): 280,89,25,8
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,89,247,322
        REG  L35 PLUSLE (ELECTRIC): 87,223,273,313
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,129,203,48
        REG  L30 KOFFING (POISON): 188,87,139,102
        REG  L6 SEEDOT (GRASS): 202,98,111,203
        REG  L11 MARILL (WATER): 352,69,47,111
        REG  L26 LOMBRE (WATER/GRASS): 202,196,102,252
        REG  L29 XATU (PSYCHIC/FLYING): 65,185,297,102
        REG  L29 ZUBAT (POISON/FLYING): 332,185,290,102
        REG  L34 PELIPPER (WATER/FLYING): 17,58,240,156
        REG  L5 KYOGRE (WATER): 352,351,184,104
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,397,189,33
        BOSS L49 SCIZOR (BUG/STEEL): 442,280,164,98
        BOSS L52 HIPPOWDON (GROUND): 89,444,182,237
        BOSS L57 MAGMORTAR (FIRE): 315,85,269,123
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,466,262,216
        BOSS L20 CHERRIM (GRASS): 402,290,312,230
        BOSS L29 MACHOKE (FIGHTING): 280,371,339,113
        BOSS L42 ABOMASNOW (GRASS/ICE): 420,412,104,280
        BOSS L44 SNEASEL (DARK/ICE): 399,91,97,259
        BOSS L48 WEAVILE (DARK/ICE): 420,411,182,263
        BOSS L66 WHISCASH (WATER/GROUND): 401,58,182,92
        BOSS L69 RAPIDASH (FIRE): 394,224,92,38
        BOSS L72 ALAKAZAM (PSYCHIC): 427,63,50,92
        BOSS L78 GARCHOMP (DRAGON/GROUND): 200,424,164,414
        BOSS L58 MAGMORTAR (FIRE): 257,223,261,270
        IMP  L7 STARLY (NORMAL/FLYING): 365,98,239,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,263,182,218
        IMP  L27 GROTLE (GRASS): 402,44,92,328
        IMP  L34 STARAVIA (NORMAL/FLYING): 38,211,203,257
        IMP  L36 STARAPTOR (NORMAL/FLYING): 17,370,156,92
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,400,14,421
        IMP  L47 RAPIDASH (FIRE): 315,398,261,97
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,370,355,257
        IMP  L25 KADABRA (PSYCHIC): 60,409,115,112
        IMP  L27 GROTLE (GRASS): 202,44,174,182
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,400,43,421
        IMP  L69 RAPIDASH (FIRE): 315,76,203,98
        IMP  L73 SNORLAX (NORMAL): 387,276,174,207
        IMP  L83 SNORLAX (NORMAL): 387,442,92,237
        IMP  L60 SKUNTANK (POISON/DARK): 242,38,92,46
        REG  L5 STARLY (NORMAL/FLYING): 98,365,28,189
        REG  L29 ZUBAT (POISON/FLYING): 314,211,428,212
        REG  L36 SWINUB (ICE/GROUND): 89,276,237,213
        REG  L6 GEODUDE (ROCK/GROUND): 246,33,92,189
        REG  L21 CARNIVINE (GRASS): 402,44,78,189
        REG  L21 DRIFLOON (GHOST/FLYING): 466,371,156,107
        REG  L36 MURKROW (DARK/FLYING): 185,263,244,373
        REG  L39 MURKROW (DARK/FLYING): 65,185,310,375
        REG  L58 PELIPPER (WATER/FLYING): 403,56,366,48
        REG  L32 EEVEE (NORMAL): 98,231,44,273
        REG  L48 SEAKING (WATER): 127,398,340,156
        REG  L42 GOLBAT (POISON/FLYING): 413,211,18,212
        REG  L23 BUIZEL (WATER): 291,196,339,317
        REG  L42 MAGNETON (ELECTRIC/STEEL): 443,85,48,393
        REG  L56 EMPOLEON (WATER/STEEL): 362,324,282,290
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,157,468,366
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 94,257,355,218
        BOSS L72 Lucario (FIGHTING/STEEL): 136,247,347,430
        BOSS L28 Flaaffy (ELECTRIC): 9,263,249,7
        BOSS L48 Haxorus (DRAGON): 200,89,468,231
        BOSS L50 Cofagrigus (GHOST): 247,399,261,347
        BOSS L67 Simipour (WATER): 56,512,156,441
        BOSS L76 Clefable (NORMAL): 387,409,312,215
        BOSS L28 Emolga (ELECTRIC/FLYING): 209,403,86,369
        BOSS L49 Carracosta (WATER/ROCK): 453,89,397,175
        BOSS L56 Lucario (FIGHTING/STEEL): 370,242,197,299
        BOSS L73 Golurk (GROUND/GHOST): 247,276,92,219
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 409,425,86,348
        BOSS L75 Arcanine (FIRE): 315,200,261,237
        BOSS L75 Glaceon (ICE): 196,91,197,401
        IMP  L8 Tepig (FIRE): 488,343,261,222
        IMP  L48 Cryogonal (ICE): 62,324,113,151
        IMP  L23 Pansage (GRASS): 402,490,235,164
        IMP  L31 Tranquill (NORMAL/FLYING): 143,211,366,355
        IMP  L39 Unfezant (NORMAL/FLYING): 416,211,164,297
        IMP  L46 Cryogonal (ICE): 196,163,151,218
        IMP  L55 Unfezant (NORMAL/FLYING): 416,257,164,297
        IMP  L55 Simisear (FIRE): 126,276,182,263
        IMP  L62 Unfezant (NORMAL/FLYING): 98,211,92,355
        IMP  L62 Flygon (GROUND/DRAGON): 523,444,366,103
        IMP  L65 Unfezant (NORMAL/FLYING): 143,369,273,43
        IMP  L65 Eelektross (ELECTRIC): 85,430,113,29
        IMP  L41 Simisear (FIRE): 126,416,261,421
        IMP  L48 Unfezant (NORMAL/FLYING): 416,369,269,92
        IMP  L74 Klinklang (STEEL): 544,263,86,268
        REG  L26 Blitzle (ELECTRIC): 351,228,24,496
        REG  L63 Hitmonlee (FIGHTING): 370,228,193,272
        REG  L63 Hitmonchan (FIGHTING): 136,8,203,501
        REG  L56 Unfezant (NORMAL/FLYING): 143,257,98,234
        REG  L47 Boldore (ROCK): 479,263,156,475
        REG  L45 Swinub (ICE/GROUND): 556,89,92,446
        REG  L32 Scolipede (BUG/POISON): 398,276,103,289
        REG  L65 Hitmontop (FIGHTING): 410,418,526,339
        REG  L52 Amoonguss (GRASS/POISON): 499,492,412,77
        REG  L64 Archeops (ROCK/FLYING): 143,231,366,355
        REG  L54 Metang (STEEL/PSYCHIC): 309,8,357,447
        REG  L60 Wooper (WATER/GROUND): 401,59,39,174
        REG  L67 Emboar (FIRE/FIGHTING): 257,528,374,442
        REG  L47 Krookodile (GROUND/DARK): 399,444,422,339
        REG  L25 Litwick (GHOST/FIRE): 481,247,373,269
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,583,281,424
        BOSS L41 Weezing (POISON): 499,399,174,220
        BOSS L5 Zigzagoon (NORMAL): 343,451,374,189
        BOSS L51 Dusclops (GHOST): 425,8,262,109
        BOSS L52 Froslass (ICE/GHOST): 466,577,191,420
        BOSS L57 Claydol (GROUND/PSYCHIC): 94,605,446,397
        BOSS L14 Machop (FIGHTING): 490,479,156,168
        BOSS L28 Slaking (NORMAL): 514,490,303,174
        BOSS L44 Whiscash (WATER/GROUND): 503,157,92,133
        BOSS L70 Sharpedo (WATER/DARK): 242,89,97,59
        BOSS L71 Dusknoir (GHOST): 325,280,262,58
        BOSS L73 Altaria (DRAGON/FLYING): 200,585,46,290
        BOSS L77 Carbink (ROCK/FAIRY): 479,414,113,334
        BOSS L57 Cradily (ROCK/GRASS): 444,362,235,380
        BOSS L57 Milotic (WATER): 56,406,113,445
        IMP  L18 Slugma (FIRE): 510,237,262,254
        IMP  L31 Wailmer (WATER): 503,58,92,392
        IMP  L18 Wailmer (WATER): 503,263,156,216
        IMP  L31 Shroomish (GRASS): 402,29,14,74
        IMP  L37 Swellow (NORMAL/FLYING): 413,228,432,586
        IMP  L37 Wailord (WATER): 291,499,392,58
        IMP  L46 Delcatty (NORMAL): 304,583,273,426
        IMP  L24 Shroomish (GRASS): 202,496,204,474
        IMP  L24 Slugma (FIRE): 510,237,262,590
        IMP  L32 Sharpedo (WATER/DARK): 400,398,156,46
        IMP  L55 Camerupt (FIRE/GROUND): 284,442,92,156
        IMP  L50 Blaziken (FIRE/FIGHTING): 488,490,182,174
        IMP  L50 Sceptile (GRASS): 202,512,197,280
        IMP  L64 Altaria (DRAGON/FLYING): 406,89,215,384
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,7,50,263
        REG  L4 Zigzagoon (NORMAL): 496,196,493,207
        REG  L25 Slugma (FIRE): 510,499,218,611
        REG  L39 Claydol (GROUND/PSYCHIC): 89,324,322,379
        REG  L36 Golbat (POISON/FLYING): 413,428,305,369
        REG  L34 Golbat (POISON/FLYING): 413,257,213,269
        REG  L33 Roselia (GRASS/POISON): 345,326,213,73
        REG  L43 Solrock (ROCK/PSYCHIC): 157,428,53,220
        REG  L49 Jellicent (WATER/GHOST): 323,466,180,412
        REG  L37 Skarmory (STEEL/FLYING): 232,413,218,18
        REG  L41 Clamperl (WATER): 503,58,300,240
        REG  L39 Tentacruel (WATER/POISON): 482,605,390,330
        REG  L48 Honchkrow (DARK/FLYING): 413,211,180,260
        REG  L53 Flygon (GROUND/DRAGON): 523,185,116,406
        REG  L23 Grimer (POISON): 398,91,374,325
        REG  L51 Mightyena (DARK): 492,231,43,304
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 490,418,339,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 38,409,43,8
        BOSS L56 Probopass (ROCK/STEEL): 443,85,269,356
        BOSS L41 Golisopod (BUG/WATER): 660,530,339,374
        BOSS L66 Froslass (ICE/GHOST): 466,94,220,420
        BOSS L66 Mandibuzz (DARK/FLYING): 413,399,182,198
        BOSS L57 Dugtrio (GROUND/STEEL): 523,444,156,421
        BOSS L52 Sableye (DARK/GHOST): 421,492,156,490
        BOSS L65 Crobat (POISON/FLYING): 440,211,174,416
        BOSS L64 Masquerain (BUG/FLYING): 403,202,564,182
        BOSS L66 Hydreigon (DARK/DRAGON): 399,324,432,590
        BOSS L65 Gyarados (WATER/FLYING): 401,423,85,89
        BOSS L64 Camerupt (FIRE/GROUND): 414,444,261,116
        BOSS L70 Mewtwo (PSYCHIC): 428,126,347,112
        BOSS L63 Crabominable (FIGHTING/ICE): 409,428,164,665
        IMP  L6 Pichu (ELECTRIC): 527,237,227,113
        IMP  L15 Glaceon (ICE): 196,500,164,92
        IMP  L27 Salandit (POISON/FIRE): 481,337,261,216
        IMP  L28 Noibat (FLYING/DRAGON): 314,237,164,399
        IMP  L41 Noivern (FLYING/DRAGON): 406,586,97,236
        IMP  L70 Primarina (WATER/FAIRY): 710,412,113,304
        IMP  L67 Muk (POISON/DARK): 499,444,397,139
        IMP  L53 Zoroark (DARK): 185,263,97,197
        IMP  L68 Zoroark (DARK): 492,304,97,46
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 87,416,417,207
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,231,277,516
        IMP  L68 Snorlax (NORMAL): 304,242,174,276
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 85,252,219,94
        IMP  L51 Shiinotic (GRASS/FAIRY): 202,324,235,310
        IMP  L20 Poipole (POISON): 474,263,182,64
        REG  L6 Yungoos (NORMAL): 162,351,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 162,168,207,279
        REG  L69 Lapras (WATER/ICE): 127,420,215,94
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 94,280,218,347
        REG  L35 Marowak (FIRE/GHOST): 708,9,104,126
        REG  L5 Yungoos (NORMAL): 237,168,207,279
        REG  L5 Yungoos (NORMAL): 162,168,590,351
        REG  L55 Espeon (PSYCHIC): 500,324,273,204
        REG  L5 Yungoos (NORMAL): 162,317,269,168
        REG  L33 Zubat (POISON/FLYING): 413,98,211,213
        REG  L30 Minior (ROCK/FLYING): 512,246,477,115
        REG  L27 Trumbeak (NORMAL/FLYING): 65,488,282,31
        REG  L62 Persian (NORMAL): 252,421,103,402
        REG  L14 Rattata (DARK/NORMAL): 98,279,44,254
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
