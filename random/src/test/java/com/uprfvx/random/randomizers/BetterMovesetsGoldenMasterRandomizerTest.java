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
        BOSS L53 CLOYSTER (WATER/ICE): 61,62,92,38
        BOSS L56 LAPRAS (WATER/ICE): 58,61,92,85
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
        IMP  L63 ARCANINE (FIRE): 53,91,46,38
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
        BOSS L31 PILOSWINE (ICE/GROUND): 89,34,182,203
        BOSS L37 DRAGONAIR (DRAGON): 225,126,97,86
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 202,94,73,236
        BOSS L46 MACHAMP (FIGHTING): 238,8,227,126
        BOSS L40 ARIADOS (BUG/POISON): 188,94,169,141
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,87,86,59
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,114,240
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,105,94
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,38,115,235
        BOSS L33 ARIADOS (BUG/POISON): 188,101,226,207
        BOSS L45 MAGMAR (FIRE): 53,238,92,207
        BOSS L77 BLASTOISE (WATER): 56,44,114,156
        BOSS L58 ARCANINE (FIRE): 53,231,46,216
        IMP  L12 GASTLY (GHOST/POISON): 122,202,149,168
        IMP  L12 GASTLY (GHOST/POISON): 122,202,95,168
        IMP  L20 HAUNTER (GHOST/POISON): 247,202,174,207
        IMP  L20 ZUBAT (POISON/FLYING): 16,185,197,211
        IMP  L32 MEGANIUM (GRASS): 76,89,73,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,86,48
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,195,95
        IMP  L35 HAUNTER (GHOST/POISON): 247,87,114,212
        IMP  L43 GENGAR (GHOST/POISON): 247,85,156,244
        IMP  L43 ALAKAZAM (PSYCHIC): 94,9,174,134
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,113,223
        IMP  L46 ALAKAZAM (PSYCHIC): 94,247,174,104
        IMP  L50 TYPHLOSION (FIRE): 126,89,46,66
        IMP  L50 FERALIGATR (WATER): 56,223,197,58
        REG  L10 CHIKORITA (GRASS): 202,246,230,216
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,104,8
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,17,211,92
        REG  L25 NINETALES (FIRE): 52,185,207,91
        REG  L31 RHYDON (GROUND/ROCK): 89,58,53,203
        REG  L18 GROWLITHE (FIRE): 52,225,46,241
        REG  L23 GOLDEEN (WATER): 196,30,240,64
        REG  L28 TENTACOOL (WATER/POISON): 61,196,156,203
        REG  L28 POLIWHIRL (WATER): 61,29,189,168
        REG  L32 ONIX (ROCK/GROUND): 89,29,103,237
        REG  L6 VOLTORB (ELECTRIC): 129,205,182,0
        REG  L31 FURRET (NORMAL): 38,7,203,237
        REG  L42 GOLDUCK (WATER): 223,59,50,237
        REG  L23 PIKACHU (ELECTRIC): 9,98,86,179
        REG  L25 ELECTRODE (ELECTRIC): 29,205,174,216
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,281,247
        BOSS L53 WALREIN (ICE/WATER): 62,38,164,89
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,157,92,184
        BOSS L43 SEALEO (ICE/WATER): 62,89,174,111
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,53,164,116
        BOSS L50 KABUTOPS (ROCK/WATER): 157,38,164,109
        BOSS L46 HITMONCHAN (FIGHTING): 183,38,156,9
        BOSS L50 MANECTRIC (ELECTRIC): 87,231,46,182
        BOSS L46 GROWLITHE (FIRE): 257,231,182,237
        BOSS L45 KANGASKHAN (NORMAL): 252,157,50,44
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,126,182,54
        BOSS L58 SKARMORY (STEEL/FLYING): 143,157,97,28
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,58,115,322
        BOSS L56 LAPRAS (WATER/ICE): 58,56,174,231
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,164,9
        IMP  L34 MIGHTYENA (DARK): 44,231,46,289
        IMP  L40 GOLBAT (POISON/FLYING): 188,202,92,141
        IMP  L20 GROVYLE (GRASS): 202,9,73,91
        IMP  L29 LOMBRE (WATER/GRASS): 352,196,235,175
        IMP  L18 SLUGMA (FIRE): 52,157,113,241
        IMP  L29 PELIPPER (WATER/FLYING): 332,351,156,196
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,59,92,213
        IMP  L22 ZUBAT (POISON/FLYING): 17,185,269,104
        IMP  L47 ROSELIA (GRASS/POISON): 202,188,14,213
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,119,76
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 87,63,203,38
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 126,223,164,179
        IMP  L34 GROVYLE (GRASS): 202,242,97,203
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,223,164,45
        IMP  L15 MUDKIP (WATER): 352,263,92,218
        REG  L21 GEODUDE (ROCK/GROUND): 91,290,335,156
        REG  L26 MARILL (WATER): 61,196,21,218
        REG  L26 MIGHTYENA (DARK): 44,305,156,102
        REG  L33 MACHOP (FIGHTING): 223,126,218,8
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,89,322,115
        REG  L35 PLUSLE (ELECTRIC): 85,69,237,38
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,129,164,92
        REG  L30 KOFFING (POISON): 188,126,139,213
        REG  L6 SEEDOT (GRASS): 202,98,73,133
        REG  L11 MARILL (WATER): 352,69,218,104
        REG  L26 LOMBRE (WATER/GRASS): 202,8,290,92
        REG  L29 XATU (PSYCHIC/FLYING): 65,185,213,211
        REG  L29 ZUBAT (POISON/FLYING): 17,211,218,289
        REG  L34 PELIPPER (WATER/FLYING): 352,59,92,104
        REG  L5 KYOGRE (WATER): 352,196,129,317
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,33,214,156
        BOSS L49 SCIZOR (BUG/STEEL): 442,280,226,405
        BOSS L52 HIPPOWDON (GROUND): 89,157,174,424
        BOSS L57 MAGMORTAR (FIRE): 53,85,261,259
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,425,417,220
        BOSS L20 CHERRIM (GRASS): 202,290,73,312
        BOSS L29 MACHOKE (FIGHTING): 280,398,182,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,280,92,54
        BOSS L44 SNEASEL (DARK/ICE): 185,91,92,97
        BOSS L48 WEAVILE (DARK/ICE): 8,98,115,404
        BOSS L66 WHISCASH (WATER/GROUND): 401,58,164,37
        BOSS L69 RAPIDASH (FIRE): 53,231,182,104
        BOSS L72 ALAKAZAM (PSYCHIC): 427,411,269,219
        BOSS L78 GARCHOMP (DRAGON/GROUND): 200,424,46,38
        BOSS L58 MAGMORTAR (FIRE): 126,411,261,123
        IMP  L7 STARLY (NORMAL/FLYING): 365,228,92,193
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,247,115,347
        IMP  L27 GROTLE (GRASS): 402,44,14,219
        IMP  L34 STARAVIA (NORMAL/FLYING): 38,257,97,193
        IMP  L36 STARAPTOR (NORMAL/FLYING): 332,370,97,355
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,444,14,213
        IMP  L47 RAPIDASH (FIRE): 394,398,92,97
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,369,18,193
        IMP  L25 KADABRA (PSYCHIC): 428,324,156,357
        IMP  L27 GROTLE (GRASS): 402,290,446,328
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,421,156,334
        IMP  L69 RAPIDASH (FIRE): 394,231,241,38
        IMP  L73 SNORLAX (NORMAL): 387,8,281,104
        IMP  L83 SNORLAX (NORMAL): 416,8,174,126
        IMP  L60 SKUNTANK (POISON/DARK): 398,126,156,247
        REG  L5 STARLY (NORMAL/FLYING): 98,365,28,189
        REG  L29 ZUBAT (POISON/FLYING): 365,44,95,310
        REG  L36 SWINUB (ICE/GROUND): 89,157,445,46
        REG  L6 GEODUDE (ROCK/GROUND): 246,33,237,201
        REG  L21 CARNIVINE (GRASS): 402,44,374,235
        REG  L21 DRIFLOON (GHOST/FLYING): 314,318,285,360
        REG  L36 MURKROW (DARK/FLYING): 372,94,114,297
        REG  L39 MURKROW (DARK/FLYING): 65,185,257,375
        REG  L58 PELIPPER (WATER/FLYING): 56,403,369,441
        REG  L32 EEVEE (NORMAL): 387,247,281,91
        REG  L48 SEAKING (WATER): 401,282,300,203
        REG  L42 GOLBAT (POISON/FLYING): 403,211,445,263
        REG  L23 BUIZEL (WATER): 362,280,182,210
        REG  L42 MAGNETON (ELECTRIC/STEEL): 209,161,199,156
        REG  L56 EMPOLEON (WATER/STEEL): 362,58,281,216
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,157,468,366
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,257,355,104
        BOSS L72 Lucario (FIGHTING/STEEL): 136,247,347,430
        BOSS L28 Flaaffy (ELECTRIC): 451,324,215,28
        BOSS L48 Haxorus (DRAGON): 200,89,156,349
        BOSS L50 Cofagrigus (GHOST): 466,94,220,288
        BOSS L67 Simipour (WATER): 503,157,468,447
        BOSS L76 Clefable (NORMAL): 63,309,115,53
        BOSS L28 Emolga (ELECTRIC/FLYING): 209,369,164,204
        BOSS L49 Carracosta (WATER/ROCK): 453,231,504,242
        BOSS L56 Lucario (FIGHTING/STEEL): 231,406,92,8
        BOSS L73 Golurk (GROUND/GHOST): 89,7,277,335
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,9,262,425
        BOSS L75 Arcanine (FIRE): 53,200,164,179
        BOSS L75 Glaceon (ICE): 58,63,281,500
        IMP  L8 Tepig (FIRE): 488,343,156,360
        IMP  L48 Cryogonal (ICE): 62,153,216,324
        IMP  L23 Pansage (GRASS): 202,512,92,317
        IMP  L31 Tranquill (NORMAL/FLYING): 403,263,366,213
        IMP  L39 Unfezant (NORMAL/FLYING): 416,211,355,273
        IMP  L46 Cryogonal (ICE): 62,512,92,163
        IMP  L55 Unfezant (NORMAL/FLYING): 416,257,234,211
        IMP  L55 Simisear (FIRE): 315,411,182,253
        IMP  L62 Unfezant (NORMAL/FLYING): 403,63,182,381
        IMP  L62 Flygon (GROUND/DRAGON): 200,523,156,157
        IMP  L65 Unfezant (NORMAL/FLYING): 98,211,366,526
        IMP  L65 Eelektross (ELECTRIC): 87,53,156,29
        IMP  L41 Simisear (FIRE): 257,280,261,526
        IMP  L48 Unfezant (NORMAL/FLYING): 263,257,104,16
        IMP  L74 Klinklang (STEEL): 544,451,92,103
        REG  L26 Blitzle (ELECTRIC): 351,228,24,496
        REG  L63 Hitmonlee (FIGHTING): 26,418,398,203
        REG  L63 Hitmonchan (FIGHTING): 409,157,501,418
        REG  L56 Unfezant (NORMAL/FLYING): 98,211,234,16
        REG  L47 Boldore (ROCK): 350,89,216,237
        REG  L45 Swinub (ICE/GROUND): 196,157,276,54
        REG  L32 Scolipede (BUG/POISON): 404,398,191,237
        REG  L65 Hitmontop (FIGHTING): 410,228,97,157
        REG  L52 Amoonguss (GRASS/POISON): 499,202,275,492
        REG  L64 Archeops (ROCK/FLYING): 157,89,43,334
        REG  L54 Metang (STEEL/PSYCHIC): 309,89,216,468
        REG  L60 Wooper (WATER/GROUND): 401,8,216,414
        REG  L67 Emboar (FIRE/FIGHTING): 315,411,528,37
        REG  L47 Krookodile (GROUND/DARK): 89,421,401,289
        REG  L25 Litwick (GHOST/FIRE): 481,499,261,114
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,424,156,583
        BOSS L41 Weezing (POISON): 188,247,261,85
        BOSS L5 Zigzagoon (NORMAL): 496,168,164,271
        BOSS L51 Dusclops (GHOST): 325,7,262,180
        BOSS L52 Froslass (ICE/GHOST): 466,358,86,216
        BOSS L57 Claydol (GROUND/PSYCHIC): 428,246,115,356
        BOSS L14 Machop (FIGHTING): 490,418,164,113
        BOSS L28 Slaking (NORMAL): 163,332,303,359
        BOSS L44 Whiscash (WATER/GROUND): 414,209,156,214
        BOSS L70 Sharpedo (WATER/DARK): 56,242,92,162
        BOSS L71 Dusknoir (GHOST): 425,280,92,148
        BOSS L73 Altaria (DRAGON/FLYING): 406,53,46,253
        BOSS L77 Carbink (ROCK/FAIRY): 408,414,397,360
        BOSS L57 Cradily (ROCK/GRASS): 412,157,397,164
        BOSS L57 Milotic (WATER): 401,406,489,442
        IMP  L18 Slugma (FIRE): 510,237,220,611
        IMP  L31 Wailmer (WATER): 503,304,174,205
        IMP  L18 Wailmer (WATER): 503,196,46,156
        IMP  L31 Shroomish (GRASS): 202,188,235,77
        IMP  L37 Swellow (NORMAL/FLYING): 332,290,97,228
        IMP  L37 Wailord (WATER): 291,304,92,205
        IMP  L46 Delcatty (NORMAL): 387,358,226,91
        IMP  L24 Shroomish (GRASS): 402,409,156,388
        IMP  L24 Slugma (FIRE): 488,496,281,317
        IMP  L32 Sharpedo (WATER/DARK): 400,428,182,37
        IMP  L55 Camerupt (FIRE/GROUND): 315,444,261,216
        IMP  L50 Blaziken (FIRE/FIGHTING): 276,421,261,179
        IMP  L50 Sceptile (GRASS): 348,200,73,320
        IMP  L64 Altaria (DRAGON/FLYING): 200,126,215,290
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 490,7,261,339
        REG  L4 Zigzagoon (NORMAL): 496,196,493,207
        REG  L25 Slugma (FIRE): 510,499,218,611
        REG  L39 Claydol (GROUND/PSYCHIC): 414,247,244,317
        REG  L36 Golbat (POISON/FLYING): 413,202,114,305
        REG  L34 Golbat (POISON/FLYING): 188,211,355,44
        REG  L33 Roselia (GRASS/POISON): 398,345,346,42
        REG  L43 Solrock (ROCK/PSYCHIC): 157,428,590,277
        REG  L49 Jellicent (WATER/GHOST): 61,94,148,202
        REG  L37 Skarmory (STEEL/FLYING): 232,413,18,191
        REG  L41 Clamperl (WATER): 503,58,287,300
        REG  L39 Tentacruel (WATER/POISON): 398,263,207,62
        REG  L48 Honchkrow (DARK/FLYING): 492,413,212,114
        REG  L53 Flygon (GROUND/DRAGON): 89,200,48,185
        REG  L23 Grimer (POISON): 124,7,286,426
        REG  L51 Mightyena (DARK): 242,514,373,583
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 490,418,339,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 280,428,43,25
        BOSS L56 Probopass (ROCK/STEEL): 443,85,269,356
        BOSS L41 Golisopod (BUG/WATER): 660,530,339,374
        BOSS L66 Froslass (ICE/GHOST): 420,358,220,673
        BOSS L66 Mandibuzz (DARK/FLYING): 413,198,182,399
        BOSS L57 Dugtrio (GROUND/STEEL): 89,400,262,515
        BOSS L52 Sableye (DARK/GHOST): 425,490,197,8
        BOSS L65 Crobat (POISON/FLYING): 413,404,18,95
        BOSS L64 Masquerain (BUG/FLYING): 405,247,432,97
        BOSS L66 Hydreigon (DARK/DRAGON): 675,126,355,184
        BOSS L65 Gyarados (WATER/FLYING): 401,423,86,37
        BOSS L64 Camerupt (FIRE/GROUND): 315,157,46,89
        BOSS L70 Mewtwo (PSYCHIC): 428,412,86,156
        BOSS L63 Crabominable (FIGHTING/ICE): 411,9,182,61
        IMP  L6 Pichu (ELECTRIC): 527,237,227,113
        IMP  L15 Glaceon (ICE): 196,500,164,92
        IMP  L27 Salandit (POISON/FIRE): 481,337,261,216
        IMP  L28 Noibat (FLYING/DRAGON): 314,237,164,399
        IMP  L41 Noivern (FLYING/DRAGON): 406,586,97,236
        IMP  L70 Primarina (WATER/FAIRY): 585,61,277,216
        IMP  L67 Muk (POISON/DARK): 398,242,174,411
        IMP  L53 Zoroark (DARK): 185,326,156,447
        IMP  L68 Zoroark (DARK): 399,369,262,97
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 94,282,273,381
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 85,231,277,683
        IMP  L68 Snorlax (NORMAL): 387,276,92,111
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 87,263,273,604
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,188,156,148
        IMP  L20 Poipole (POISON): 474,237,156,164
        REG  L6 Yungoos (NORMAL): 162,351,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 162,168,207,279
        REG  L69 Lapras (WATER/ICE): 127,420,215,94
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 94,280,218,347
        REG  L35 Marowak (FIRE/GHOST): 708,442,87,411
        REG  L5 Yungoos (NORMAL): 237,168,213,279
        REG  L5 Yungoos (NORMAL): 162,168,216,351
        REG  L55 Espeon (PSYCHIC): 500,247,92,285
        REG  L5 Yungoos (NORMAL): 237,168,182,104
        REG  L33 Zubat (POISON/FLYING): 413,141,48,18
        REG  L30 Minior (ROCK/FLYING): 157,428,113,115
        REG  L27 Trumbeak (NORMAL/FLYING): 65,350,590,213
        REG  L62 Persian (NORMAL): 252,402,408,231
        REG  L14 Rattata (DARK/NORMAL): 44,343,92,289
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
