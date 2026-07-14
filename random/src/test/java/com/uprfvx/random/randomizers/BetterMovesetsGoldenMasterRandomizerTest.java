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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,126,156,164
        BOSS L45 NIDOKING (POISON/GROUND): 89,38,115,61
        BOSS L55 HITMONLEE (FIGHTING): 24,38,156,96
        BOSS L12 GEODUDE (ROCK/GROUND): 157,33,99,117
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,86,104
        BOSS L24 RAICHU (ELECTRIC): 84,129,86,102
        BOSS L37 KOFFING (POISON): 124,87,33,117
        BOSS L43 WEEZING (POISON): 124,87,92,164
        BOSS L42 RAPIDASH (FIRE): 52,130,164,102
        BOSS L38 VENOMOTH (BUG/POISON): 141,94,18,104
        BOSS L53 CLOYSTER (WATER/ICE): 58,38,115,48
        BOSS L56 LAPRAS (WATER/ICE): 59,56,92,104
        BOSS L55 HAUNTER (GHOST/POISON): 101,87,156,109
        BOSS L56 DRAGONAIR (DRAGON): 63,58,115,43
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,85,92,104
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,28,99,129
        IMP  L15 ABRA (PSYCHIC): 100,99,86,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 28,13,129,0
        IMP  L18 KADABRA (PSYCHIC): 93,161,156,118
        IMP  L16 RATICATE (NORMAL): 129,55,158,102
        IMP  L25 WARTORTLE (WATER): 61,44,156,69
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,95,99,0
        IMP  L35 ALAKAZAM (PSYCHIC): 94,38,92,105
        IMP  L40 VENUSAUR (GRASS/POISON): 75,38,73,77
        IMP  L45 RHYHORN (GROUND/ROCK): 89,87,92,34
        IMP  L45 GYARADOS (WATER/FLYING): 61,58,164,38
        IMP  L47 GYARADOS (WATER/FLYING): 56,38,115,164
        IMP  L61 ARCANINE (FIRE): 53,91,46,117
        IMP  L63 ARCANINE (FIRE): 53,91,46,63
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,69,115,164
        REG  L11 RATTATA (NORMAL): 39,129,156,0
        REG  L9 PIDGEY (NORMAL/FLYING): 28,115,129,0
        REG  L18 MANKEY (FIGHTING): 66,2,117,104
        REG  L19 RATTATA (NORMAL): 34,61,102,39
        REG  L37 VULPIX (FIRE): 126,91,156,102
        REG  L29 WEEZING (POISON): 124,153,156,0
        REG  L31 CLOYSTER (WATER/ICE): 62,61,48,110
        REG  L26 MANKEY (FIGHTING): 154,2,117,43
        REG  L30 HORSEA (WATER): 55,58,43,104
        REG  L29 FEAROW (NORMAL/FLYING): 129,143,43,102
        REG  L70 GYARADOS (WATER/FLYING): 56,85,104,38
        REG  L17 MACHOP (FIGHTING): 2,5,157,0
        REG  L28 EKANS (POISON): 40,44,102,117
        REG  L39 DUGTRIO (GROUND): 91,34,28,102
        REG  L33 HAUNTER (GHOST/POISON): 101,72,104,149
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,28,182,197
        BOSS L16 SCYTHER (BUG/FLYING): 210,98,168,249
        BOSS L31 PILOSWINE (ICE/GROUND): 196,44,156,63
        BOSS L37 DRAGONAIR (DRAGON): 225,192,174,213
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,202,73,203
        BOSS L46 MACHAMP (FIGHTING): 223,126,92,189
        BOSS L40 ARIADOS (BUG/POISON): 188,101,182,154
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,53,156,249
        BOSS L42 OMASTAR (ROCK/WATER): 205,59,156,55
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,182,109
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,63,73,104
        BOSS L33 ARIADOS (BUG/POISON): 141,101,237,94
        BOSS L45 MAGMAR (FIRE): 53,9,182,237
        BOSS L77 BLASTOISE (WATER): 56,58,182,237
        BOSS L58 ARCANINE (FIRE): 172,91,92,203
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,104
        IMP  L12 GASTLY (GHOST/POISON): 122,95,244,207
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,174,212
        IMP  L20 ZUBAT (POISON/FLYING): 129,44,174,203
        IMP  L32 MEGANIUM (GRASS): 202,34,197,210
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,92,205
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,199
        IMP  L35 HAUNTER (GHOST/POISON): 122,202,94,87
        IMP  L35 HAUNTER (GHOST/POISON): 122,87,182,174
        IMP  L43 GENGAR (GHOST/POISON): 122,7,174,244
        IMP  L43 ALAKAZAM (PSYCHIC): 94,247,105,9
        IMP  L43 ALAKAZAM (PSYCHIC): 94,91,105,29
        IMP  L46 ALAKAZAM (PSYCHIC): 94,91,182,92
        IMP  L50 TYPHLOSION (FIRE): 53,9,174,218
        IMP  L50 FERALIGATR (WATER): 55,196,174,184
        REG  L10 CHIKORITA (GRASS): 33,45,202,104
        REG  L20 QUAGSIRE (WATER/GROUND): 55,8,201,29
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,168,203,237
        REG  L25 NINETALES (FIRE): 52,129,219,46
        REG  L31 RHYDON (GROUND/ROCK): 189,59,218,207
        REG  L18 GROWLITHE (FIRE): 52,44,104,249
        REG  L23 GOLDEEN (WATER): 64,196,203,30
        REG  L28 TENTACOOL (WATER/POISON): 61,196,92,202
        REG  L28 POLIWHIRL (WATER): 55,59,240,182
        REG  L32 ONIX (ROCK/GROUND): 89,231,156,218
        REG  L6 VOLTORB (ELECTRIC): 33,218,129,0
        REG  L31 FURRET (NORMAL): 129,247,9,205
        REG  L42 GOLDUCK (WATER): 223,91,244,29
        REG  L23 PIKACHU (ELECTRIC): 84,98,189,104
        REG  L25 ELECTRODE (ELECTRIC): 29,205,207,92
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 44,91,156,184
        BOSS L53 WALREIN (ICE/WATER): 58,89,227,352
        BOSS L26 CAMERUPT (FIRE/GROUND): 126,157,46,116
        BOSS L43 SEALEO (ICE/WATER): 58,38,182,258
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,153,164,213
        BOSS L50 KABUTOPS (ROCK/WATER): 205,280,182,102
        BOSS L46 HITMONCHAN (FIGHTING): 279,8,92,228
        BOSS L50 MANECTRIC (ELECTRIC): 85,231,86,164
        BOSS L46 GROWLITHE (FIRE): 53,91,332,242
        BOSS L45 KANGASKHAN (NORMAL): 146,223,231,58
        BOSS L45 ALTARIA (DRAGON/FLYING): 332,211,349,168
        BOSS L58 SKARMORY (STEEL/FLYING): 65,211,191,28
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 157,94,156,244
        BOSS L56 LAPRAS (WATER/ICE): 352,58,92,203
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,97,196
        IMP  L34 MIGHTYENA (DARK): 44,91,213,231
        IMP  L40 GOLBAT (POISON/FLYING): 332,211,92,109
        IMP  L20 GROVYLE (GRASS): 202,9,182,92
        IMP  L29 LOMBRE (WATER/GRASS): 352,7,156,230
        IMP  L18 SLUGMA (FIRE): 52,157,164,218
        IMP  L29 PELIPPER (WATER/FLYING): 17,351,182,156
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,157,156,36
        IMP  L22 ZUBAT (POISON/FLYING): 332,211,156,202
        IMP  L47 ROSELIA (GRASS/POISON): 202,188,92,210
        IMP  L53 ALTARIA (DRAGON/FLYING): 225,126,195,237
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,38,164,199
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 223,98,339,317
        IMP  L34 GROVYLE (GRASS): 202,9,164,317
        IMP  L34 MARSHTOMP (WATER/GROUND): 352,196,92,45
        IMP  L15 MUDKIP (WATER): 55,91,182,205
        REG  L21 GEODUDE (ROCK/GROUND): 91,280,157,111
        REG  L26 MARILL (WATER): 61,280,102,218
        REG  L26 MIGHTYENA (DARK): 44,247,216,336
        REG  L33 MACHOP (FIGHTING): 280,157,182,118
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,93,218,322
        REG  L35 PLUSLE (ELECTRIC): 87,98,69,182
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,263,319,156
        REG  L30 KOFFING (POISON): 124,87,194,139
        REG  L6 SEEDOT (GRASS): 202,205,216,241
        REG  L11 MARILL (WATER): 352,196,47,129
        REG  L26 LOMBRE (WATER/GRASS): 71,8,267,189
        REG  L29 XATU (PSYCHIC/FLYING): 332,263,43,211
        REG  L29 ZUBAT (POISON/FLYING): 17,290,109,203
        REG  L34 PELIPPER (WATER/FLYING): 352,58,290,332
        REG  L5 KYOGRE (WATER): 352,351,184,317
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,389,156,213
        BOSS L49 SCIZOR (BUG/STEEL): 442,163,182,216
        BOSS L52 HIPPOWDON (GROUND): 89,424,46,218
        BOSS L57 MAGMORTAR (FIRE): 126,9,185,157
        BOSS L58 SPIRITOMB (GHOST/DARK): 466,389,271,196
        BOSS L20 CHERRIM (GRASS): 202,290,164,205
        BOSS L29 MACHOKE (FIGHTING): 233,89,113,43
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,89,182,219
        BOSS L44 SNEASEL (DARK/ICE): 371,231,14,103
        BOSS L48 WEAVILE (DARK/ICE): 400,232,97,98
        BOSS L66 WHISCASH (WATER/GROUND): 401,444,156,290
        BOSS L69 RAPIDASH (FIRE): 315,224,261,290
        BOSS L72 ALAKAZAM (PSYCHIC): 427,324,156,50
        BOSS L78 GARCHOMP (DRAGON/GROUND): 414,442,14,213
        BOSS L58 MAGMORTAR (FIRE): 53,238,269,270
        IMP  L7 STARLY (NORMAL/FLYING): 98,466,239,45
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,89,92,109
        IMP  L27 GROTLE (GRASS): 412,44,446,104
        IMP  L34 STARAVIA (NORMAL/FLYING): 290,332,182,216
        IMP  L36 STARAPTOR (NORMAL/FLYING): 332,257,18,104
        IMP  L48 HERACROSS (BUG/FIGHTING): 264,400,339,36
        IMP  L47 RAPIDASH (FIRE): 257,231,97,224
        IMP  L42 STARAPTOR (NORMAL/FLYING): 98,370,182,18
        IMP  L25 KADABRA (PSYCHIC): 60,409,50,148
        IMP  L27 GROTLE (GRASS): 75,44,174,110
        IMP  L61 HERACROSS (BUG/FIGHTING): 370,400,317,332
        IMP  L69 RAPIDASH (FIRE): 394,231,92,263
        IMP  L73 SNORLAX (NORMAL): 416,247,182,281
        IMP  L83 SNORLAX (NORMAL): 387,442,281,85
        IMP  L60 SKUNTANK (POISON/DARK): 400,263,46,108
        REG  L5 STARLY (NORMAL/FLYING): 332,466,98,216
        REG  L29 ZUBAT (POISON/FLYING): 17,466,18,216
        REG  L36 SWINUB (ICE/GROUND): 89,290,213,218
        REG  L6 GEODUDE (ROCK/GROUND): 205,9,33,111
        REG  L21 CARNIVINE (GRASS): 402,44,14,416
        REG  L21 DRIFLOON (GHOST/FLYING): 314,389,263,107
        REG  L36 MURKROW (DARK/FLYING): 389,211,213,103
        REG  L39 MURKROW (DARK/FLYING): 17,399,203,114
        REG  L58 PELIPPER (WATER/FLYING): 403,263,240,56
        REG  L32 EEVEE (NORMAL): 387,44,164,189
        REG  L48 SEAKING (WATER): 401,398,290,97
        REG  L42 GOLBAT (POISON/FLYING): 365,428,109,369
        REG  L23 BUIZEL (WATER): 291,196,213,92
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,435,103,113
        REG  L56 EMPOLEON (WATER/STEEL): 362,65,48,232
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 87,337,113,92
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 94,496,86,182
        BOSS L72 Lucario (FIGHTING/STEEL): 231,89,444,94
        BOSS L28 Flaaffy (ELECTRIC): 451,496,86,45
        BOSS L48 Haxorus (DRAGON): 200,280,349,163
        BOSS L50 Cofagrigus (GHOST): 247,496,50,471
        BOSS L67 Simipour (WATER): 401,441,468,263
        BOSS L76 Clefable (NORMAL): 387,196,115,85
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,332,92,269
        BOSS L49 Carracosta (WATER/ROCK): 503,442,182,240
        BOSS L56 Lucario (FIGHTING/STEEL): 411,8,339,430
        BOSS L73 Golurk (GROUND/GHOST): 101,264,156,277
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 427,280,156,204
        BOSS L75 Arcanine (FIRE): 126,528,261,332
        BOSS L75 Glaceon (ICE): 59,247,46,44
        IMP  L8 Tepig (FIRE): 488,33,182,39
        IMP  L48 Cryogonal (ICE): 196,430,115,207
        IMP  L23 Pansage (GRASS): 202,512,468,73
        IMP  L31 Tranquill (NORMAL/FLYING): 332,98,355,213
        IMP  L39 Unfezant (NORMAL/FLYING): 98,257,197,365
        IMP  L46 Cryogonal (ICE): 62,430,113,92
        IMP  L55 Unfezant (NORMAL/FLYING): 403,263,156,269
        IMP  L55 Simisear (FIRE): 126,242,281,43
        IMP  L62 Unfezant (NORMAL/FLYING): 332,416,366,297
        IMP  L62 Flygon (GROUND/DRAGON): 523,444,366,103
        IMP  L65 Unfezant (NORMAL/FLYING): 332,257,92,369
        IMP  L65 Eelektross (ELECTRIC): 87,401,51,512
        IMP  L41 Simisear (FIRE): 488,157,164,253
        IMP  L48 Unfezant (NORMAL/FLYING): 98,365,366,43
        IMP  L74 Klinklang (STEEL): 544,528,92,263
        REG  L26 Blitzle (ELECTRIC): 351,263,218,228
        REG  L63 Hitmonlee (FIGHTING): 280,523,92,203
        REG  L63 Hitmonchan (FIGHTING): 280,89,157,9
        REG  L56 Unfezant (NORMAL/FLYING): 263,332,297,526
        REG  L47 Boldore (ROCK): 444,89,201,29
        REG  L45 Swinub (ICE/GROUND): 89,276,317,58
        REG  L32 Scolipede (BUG/POISON): 398,231,207,182
        REG  L65 Hitmontop (FIGHTING): 370,523,501,237
        REG  L52 Amoonguss (GRASS/POISON): 499,202,92,148
        REG  L64 Archeops (ROCK/FLYING): 17,242,231,207
        REG  L54 Metang (STEEL/PSYCHIC): 309,523,447,249
        REG  L60 Wooper (WATER/GROUND): 401,231,164,156
        REG  L67 Emboar (FIRE/FIGHTING): 488,479,231,283
        REG  L47 Krookodile (GROUND/DARK): 399,188,207,213
        REG  L25 Litwick (GHOST/FIRE): 247,53,496,148
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,91,36,510
        BOSS L41 Weezing (POISON): 188,247,156,360
        BOSS L5 Zigzagoon (NORMAL): 496,168,156,39
        BOSS L51 Dusclops (GHOST): 325,280,92,269
        BOSS L52 Froslass (ICE/GHOST): 420,85,182,92
        BOSS L57 Claydol (GROUND/PSYCHIC): 89,479,397,237
        BOSS L14 Machop (FIGHTING): 490,510,113,317
        BOSS L28 Slaking (NORMAL): 498,185,281,359
        BOSS L44 Whiscash (WATER/GROUND): 401,58,263,426
        BOSS L70 Sharpedo (WATER/DARK): 503,196,213,428
        BOSS L71 Dusknoir (GHOST): 325,451,271,94
        BOSS L73 Altaria (DRAGON/FLYING): 200,523,355,231
        BOSS L77 Carbink (ROCK/FAIRY): 157,414,182,219
        BOSS L57 Cradily (ROCK/GRASS): 412,444,92,482
        BOSS L57 Milotic (WATER): 401,58,92,213
        IMP  L18 Slugma (FIRE): 510,317,281,106
        IMP  L31 Wailmer (WATER): 503,263,46,240
        IMP  L18 Wailmer (WATER): 352,237,174,428
        IMP  L31 Shroomish (GRASS): 202,474,235,264
        IMP  L37 Swellow (NORMAL/FLYING): 413,211,97,216
        IMP  L37 Wailord (WATER): 323,304,156,196
        IMP  L46 Delcatty (NORMAL): 38,185,182,148
        IMP  L24 Shroomish (GRASS): 402,29,73,218
        IMP  L24 Slugma (FIRE): 510,246,281,499
        IMP  L32 Sharpedo (WATER/DARK): 400,428,156,216
        IMP  L55 Camerupt (FIRE/GROUND): 488,76,261,153
        IMP  L50 Blaziken (FIRE/FIGHTING): 264,317,339,156
        IMP  L50 Sceptile (GRASS): 412,400,156,9
        IMP  L64 Altaria (DRAGON/FLYING): 365,211,46,523
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 264,163,92,374
        REG  L4 Zigzagoon (NORMAL): 343,351,45,196
        REG  L25 Slugma (FIRE): 510,499,207,263
        REG  L39 Claydol (GROUND/PSYCHIC): 91,237,104,157
        REG  L36 Golbat (POISON/FLYING): 314,257,218,188
        REG  L34 Golbat (POISON/FLYING): 332,290,269,207
        REG  L33 Roselia (GRASS/POISON): 202,496,40,320
        REG  L43 Solrock (ROCK/PSYCHIC): 444,324,94,397
        REG  L49 Jellicent (WATER/GHOST): 323,605,247,263
        REG  L37 Skarmory (STEEL/FLYING): 232,157,263,97
        REG  L41 Clamperl (WATER): 503,263,156,334
        REG  L39 Tentacruel (WATER/POISON): 503,59,482,112
        REG  L48 Honchkrow (DARK/FLYING): 492,211,289,218
        REG  L53 Flygon (GROUND/DRAGON): 89,242,369,586
        REG  L23 Grimer (POISON): 124,317,290,189
        REG  L51 Mightyena (DARK): 389,423,104,510
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 280,496,43,317
        BOSS L47 Bewear (NORMAL/FIGHTING): 416,276,92,207
        BOSS L56 Probopass (ROCK/STEEL): 408,192,269,164
        BOSS L41 Golisopod (BUG/WATER): 522,442,182,334
        BOSS L66 Froslass (ICE/GHOST): 58,87,182,577
        BOSS L66 Mandibuzz (DARK/FLYING): 403,198,92,269
        BOSS L57 Dugtrio (GROUND/STEEL): 523,416,92,482
        BOSS L52 Sableye (DARK/GHOST): 185,428,92,490
        BOSS L65 Crobat (POISON/FLYING): 19,202,366,247
        BOSS L64 Masquerain (BUG/FLYING): 369,341,18,78
        BOSS L66 Hydreigon (DARK/DRAGON): 406,126,46,57
        BOSS L65 Gyarados (WATER/FLYING): 401,444,46,207
        BOSS L64 Camerupt (FIRE/GROUND): 315,173,261,156
        BOSS L70 Mewtwo (PSYCHIC): 94,58,347,104
        BOSS L63 Crabominable (FIGHTING/ICE): 8,223,164,104
        IMP  L6 Pichu (ELECTRIC): 84,343,496,204
        IMP  L15 Glaceon (ICE): 196,237,694,45
        IMP  L27 Salandit (POISON/FIRE): 398,481,92,141
        IMP  L28 Noibat (FLYING/DRAGON): 314,352,415,366
        IMP  L41 Noivern (FLYING/DRAGON): 200,53,156,207
        IMP  L70 Primarina (WATER/FAIRY): 56,412,156,231
        IMP  L67 Muk (POISON/DARK): 441,280,50,220
        IMP  L53 Zoroark (DARK): 492,411,269,182
        IMP  L68 Zoroark (DARK): 399,369,46,244
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 94,87,277,282
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,94,86,374
        IMP  L68 Snorlax (NORMAL): 387,7,411,242
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 85,324,277,477
        IMP  L51 Shiinotic (GRASS/FAIRY): 412,585,147,109
        IMP  L20 Poipole (POISON): 474,496,182,156
        REG  L6 Yungoos (NORMAL): 497,317,43,371
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 33,317,43,207
        REG  L69 Lapras (WATER/ICE): 58,324,54,56
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 94,85,411,204
        REG  L35 Marowak (FIRE/GHOST): 708,414,116,53
        REG  L5 Yungoos (NORMAL): 237,351,43,168
        REG  L5 Yungoos (NORMAL): 496,317,279,168
        REG  L55 Espeon (PSYCHIC): 94,387,213,182
        REG  L5 Yungoos (NORMAL): 237,168,104,259
        REG  L33 Zubat (POISON/FLYING): 17,44,253,218
        REG  L30 Minior (ROCK/FLYING): 512,157,218,111
        REG  L27 Trumbeak (NORMAL/FLYING): 332,496,249,432
        REG  L62 Persian (NORMAL): 387,185,445,218
        REG  L14 Rattata (DARK/NORMAL): 168,351,156,116
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
