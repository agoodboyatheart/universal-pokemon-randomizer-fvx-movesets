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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,87,164,126
        BOSS L45 NIDOKING (POISON/GROUND): 89,126,92,66
        BOSS L55 HITMONLEE (FIGHTING): 24,34,156,116
        BOSS L12 GEODUDE (ROCK/GROUND): 33,99,156,111
        BOSS L21 STARMIE (WATER/PSYCHIC): 94,161,86,104
        BOSS L24 RAICHU (ELECTRIC): 84,129,115,102
        BOSS L37 KOFFING (POISON): 124,87,33,117
        BOSS L43 WEEZING (POISON): 124,87,92,164
        BOSS L42 RAPIDASH (FIRE): 52,130,164,102
        BOSS L38 VENOMOTH (BUG/POISON): 141,94,18,72
        BOSS L53 CLOYSTER (WATER/ICE): 58,63,115,48
        BOSS L56 LAPRAS (WATER/ICE): 59,34,92,82
        BOSS L55 HAUNTER (GHOST/POISON): 101,94,156,109
        BOSS L56 DRAGONAIR (DRAGON): 63,58,92,43
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,59,156,117
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,28,99,129
        IMP  L15 ABRA (PSYCHIC): 100,99,86,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 28,13,129,0
        IMP  L18 KADABRA (PSYCHIC): 93,161,156,118
        IMP  L16 RATICATE (NORMAL): 129,55,158,102
        IMP  L25 WARTORTLE (WATER): 61,69,156,104
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,95,99,0
        IMP  L35 ALAKAZAM (PSYCHIC): 94,38,105,92
        IMP  L40 VENUSAUR (GRASS/POISON): 75,38,73,77
        IMP  L45 RHYHORN (GROUND/ROCK): 89,87,92,39
        IMP  L45 GYARADOS (WATER/FLYING): 61,58,164,92
        IMP  L47 GYARADOS (WATER/FLYING): 56,58,115,164
        IMP  L61 ARCANINE (FIRE): 53,91,46,115
        IMP  L63 ARCANINE (FIRE): 53,44,46,92
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,69,115,164
        REG  L11 RATTATA (NORMAL): 39,129,156,0
        REG  L9 PIDGEY (NORMAL/FLYING): 28,115,129,0
        REG  L18 MANKEY (FIGHTING): 66,2,117,104
        REG  L19 RATTATA (NORMAL): 34,61,104,102
        REG  L37 VULPIX (FIRE): 126,91,156,104
        REG  L29 WEEZING (POISON): 124,153,92,164
        REG  L31 CLOYSTER (WATER/ICE): 62,61,48,110
        REG  L26 MANKEY (FIGHTING): 154,2,117,43
        REG  L30 HORSEA (WATER): 55,58,108,104
        REG  L29 FEAROW (NORMAL/FLYING): 129,143,43,102
        REG  L70 GYARADOS (WATER/FLYING): 56,85,104,92
        REG  L17 MACHOP (FIGHTING): 2,5,157,0
        REG  L28 EKANS (POISON): 40,34,102,117
        REG  L39 DUGTRIO (GROUND): 89,38,92,28
        REG  L33 HAUNTER (GHOST/POISON): 101,72,156,164
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,28,182,197
        BOSS L16 SCYTHER (BUG/FLYING): 210,98,168,249
        BOSS L31 PILOSWINE (ICE/GROUND): 196,44,156,182
        BOSS L37 DRAGONAIR (DRAGON): 225,192,174,213
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,202,73,203
        BOSS L46 MACHAMP (FIGHTING): 223,126,92,216
        BOSS L40 ARIADOS (BUG/POISON): 188,101,182,154
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,53,29,223
        BOSS L42 OMASTAR (ROCK/WATER): 205,59,44,29
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,92,105
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,63,73,104
        BOSS L33 ARIADOS (BUG/POISON): 40,91,50,132
        BOSS L45 MAGMAR (FIRE): 53,9,182,92
        BOSS L77 BLASTOISE (WATER): 56,58,111,231
        BOSS L58 ARCANINE (FIRE): 53,231,46,97
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,104
        IMP  L12 GASTLY (GHOST/POISON): 122,95,244,207
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,174,212
        IMP  L20 ZUBAT (POISON/FLYING): 129,44,174,203
        IMP  L32 MEGANIUM (GRASS): 202,34,14,210
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,92,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,199
        IMP  L35 HAUNTER (GHOST/POISON): 122,87,104,94
        IMP  L35 HAUNTER (GHOST/POISON): 122,85,182,212
        IMP  L43 GENGAR (GHOST/POISON): 247,223,174,218
        IMP  L43 ALAKAZAM (PSYCHIC): 94,91,156,134
        IMP  L43 ALAKAZAM (PSYCHIC): 94,8,105,213
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,115,223
        IMP  L50 TYPHLOSION (FIRE): 53,223,197,104
        IMP  L50 FERALIGATR (WATER): 55,163,92,46
        REG  L10 CHIKORITA (GRASS): 33,45,202,104
        REG  L20 QUAGSIRE (WATER/GROUND): 55,8,201,174
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,168,203,237
        REG  L25 NINETALES (FIRE): 52,129,219,46
        REG  L31 RHYDON (GROUND/ROCK): 205,242,92,249
        REG  L18 GROWLITHE (FIRE): 52,91,218,46
        REG  L23 GOLDEEN (WATER): 64,196,39,92
        REG  L28 TENTACOOL (WATER/POISON): 61,202,48,203
        REG  L28 POLIWHIRL (WATER): 55,196,189,203
        REG  L32 ONIX (ROCK/GROUND): 88,231,103,106
        REG  L6 VOLTORB (ELECTRIC): 33,205,216,0
        REG  L31 FURRET (NORMAL): 98,9,7,247
        REG  L42 GOLDUCK (WATER): 196,93,244,91
        REG  L23 PIKACHU (ELECTRIC): 84,98,182,207
        REG  L25 ELECTRODE (ELECTRIC): 192,29,92,156
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,91,269,216
        BOSS L53 WALREIN (ICE/WATER): 59,231,240,89
        BOSS L26 CAMERUPT (FIRE/GROUND): 52,157,207,263
        BOSS L43 SEALEO (ICE/WATER): 62,157,182,352
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,153,46,102
        BOSS L50 KABUTOPS (ROCK/WATER): 205,280,182,102
        BOSS L46 HITMONCHAN (FIGHTING): 279,9,92,228
        BOSS L50 MANECTRIC (ELECTRIC): 85,63,46,164
        BOSS L46 GROWLITHE (FIRE): 53,91,104,34
        BOSS L45 KANGASKHAN (NORMAL): 146,157,231,352
        BOSS L45 ALTARIA (DRAGON/FLYING): 332,38,349,168
        BOSS L58 SKARMORY (STEEL/FLYING): 65,211,97,191
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 157,153,164,244
        BOSS L56 LAPRAS (WATER/ICE): 352,58,92,203
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,97,203
        IMP  L34 MIGHTYENA (DARK): 44,38,269,244
        IMP  L40 GOLBAT (POISON/FLYING): 332,211,92,109
        IMP  L20 GROVYLE (GRASS): 202,9,182,92
        IMP  L29 LOMBRE (WATER/GRASS): 352,7,156,230
        IMP  L18 SLUGMA (FIRE): 52,157,113,290
        IMP  L29 PELIPPER (WATER/FLYING): 17,351,182,156
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,290,156,352
        IMP  L22 ZUBAT (POISON/FLYING): 332,211,156,202
        IMP  L47 ROSELIA (GRASS/POISON): 188,345,235,38
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,46,244
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 85,38,86,205
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 280,91,14,102
        IMP  L34 GROVYLE (GRASS): 348,280,14,103
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,69,156,193
        IMP  L15 MUDKIP (WATER): 55,91,182,205
        REG  L21 GEODUDE (ROCK/GROUND): 91,280,157,111
        REG  L26 MARILL (WATER): 61,280,102,218
        REG  L26 MIGHTYENA (DARK): 44,247,216,336
        REG  L33 MACHOP (FIGHTING): 280,8,339,157
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,315,237,164
        REG  L35 PLUSLE (ELECTRIC): 87,69,203,38
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,33,48,218
        REG  L30 KOFFING (POISON): 124,126,351,290
        REG  L6 SEEDOT (GRASS): 202,156,102,106
        REG  L11 MARILL (WATER): 352,263,205,91
        REG  L26 LOMBRE (WATER/GRASS): 71,8,267,189
        REG  L29 XATU (PSYCHIC/FLYING): 332,247,102,207
        REG  L29 ZUBAT (POISON/FLYING): 17,290,109,203
        REG  L34 PELIPPER (WATER/FLYING): 332,38,182,156
        REG  L5 KYOGRE (WATER): 352,351,184,317
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,389,156,213
        BOSS L49 SCIZOR (BUG/STEEL): 442,163,182,216
        BOSS L52 HIPPOWDON (GROUND): 89,424,46,218
        BOSS L57 MAGMORTAR (FIRE): 126,9,108,157
        BOSS L58 SPIRITOMB (GHOST/DARK): 466,389,271,196
        BOSS L20 CHERRIM (GRASS): 202,263,164,241
        BOSS L29 MACHOKE (FIGHTING): 280,9,156,207
        BOSS L42 ABOMASNOW (GRASS/ICE): 59,157,200,452
        BOSS L44 SNEASEL (DARK/ICE): 371,231,14,103
        BOSS L48 WEAVILE (DARK/ICE): 400,232,97,98
        BOSS L66 WHISCASH (WATER/GROUND): 401,263,164,414
        BOSS L69 RAPIDASH (FIRE): 53,224,92,445
        BOSS L72 ALAKAZAM (PSYCHIC): 94,8,105,278
        BOSS L78 GARCHOMP (DRAGON/GROUND): 89,157,14,28
        BOSS L58 MAGMORTAR (FIRE): 315,89,156,317
        IMP  L7 STARLY (NORMAL/FLYING): 98,466,239,45
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 94,89,156,286
        IMP  L27 GROTLE (GRASS): 412,44,446,104
        IMP  L34 STARAVIA (NORMAL/FLYING): 290,332,182,216
        IMP  L36 STARAPTOR (NORMAL/FLYING): 332,257,18,104
        IMP  L48 HERACROSS (BUG/FIGHTING): 264,400,339,68
        IMP  L47 RAPIDASH (FIRE): 257,231,97,224
        IMP  L42 STARAPTOR (NORMAL/FLYING): 98,370,97,18
        IMP  L25 KADABRA (PSYCHIC): 60,7,50,148
        IMP  L27 GROTLE (GRASS): 75,44,174,110
        IMP  L61 HERACROSS (BUG/FIGHTING): 280,89,156,164
        IMP  L69 RAPIDASH (FIRE): 257,290,95,340
        IMP  L73 SNORLAX (NORMAL): 416,402,156,203
        IMP  L83 SNORLAX (NORMAL): 387,200,92,276
        IMP  L60 SKUNTANK (POISON/DARK): 398,91,262,46
        REG  L5 STARLY (NORMAL/FLYING): 332,466,98,216
        REG  L29 ZUBAT (POISON/FLYING): 17,466,18,216
        REG  L36 SWINUB (ICE/GROUND): 89,276,203,156
        REG  L6 GEODUDE (ROCK/GROUND): 205,9,33,111
        REG  L21 CARNIVINE (GRASS): 412,44,275,104
        REG  L21 DRIFLOON (GHOST/FLYING): 314,389,263,107
        REG  L36 MURKROW (DARK/FLYING): 389,365,213,253
        REG  L39 MURKROW (DARK/FLYING): 314,399,211,207
        REG  L58 PELIPPER (WATER/FLYING): 403,369,240,97
        REG  L32 EEVEE (NORMAL): 387,91,213,92
        REG  L48 SEAKING (WATER): 291,282,392,290
        REG  L42 GOLBAT (POISON/FLYING): 365,257,164,188
        REG  L23 BUIZEL (WATER): 352,8,98,216
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,85,393,290
        REG  L56 EMPOLEON (WATER/STEEL): 61,65,54,430
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 87,337,113,92
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 94,451,86,58
        BOSS L72 Lucario (FIGHTING/STEEL): 231,406,14,182
        BOSS L28 Flaaffy (ELECTRIC): 451,7,164,280
        BOSS L48 Haxorus (DRAGON): 200,280,269,349
        BOSS L50 Cofagrigus (GHOST): 466,412,277,50
        BOSS L67 Simipour (WATER): 503,283,164,92
        BOSS L76 Clefable (NORMAL): 387,58,92,53
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,228,113,268
        BOSS L49 Carracosta (WATER/ROCK): 479,276,504,231
        BOSS L56 Lucario (FIGHTING/STEEL): 409,247,46,339
        BOSS L73 Golurk (GROUND/GHOST): 89,359,156,85
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 427,409,269,45
        BOSS L75 Arcanine (FIRE): 488,406,92,241
        BOSS L75 Glaceon (ICE): 58,44,485,91
        IMP  L8 Tepig (FIRE): 488,343,249,218
        IMP  L48 Cryogonal (ICE): 58,324,277,263
        IMP  L23 Pansage (GRASS): 402,490,73,91
        IMP  L31 Tranquill (NORMAL/FLYING): 98,332,355,369
        IMP  L39 Unfezant (NORMAL/FLYING): 365,63,355,269
        IMP  L46 Cryogonal (ICE): 58,430,113,207
        IMP  L55 Unfezant (NORMAL/FLYING): 263,257,269,369
        IMP  L55 Simisear (FIRE): 257,490,468,421
        IMP  L62 Unfezant (NORMAL/FLYING): 143,257,92,355
        IMP  L62 Flygon (GROUND/DRAGON): 414,231,164,324
        IMP  L65 Unfezant (NORMAL/FLYING): 416,332,366,526
        IMP  L65 Eelektross (ELECTRIC): 85,63,46,216
        IMP  L41 Simisear (FIRE): 257,283,269,182
        IMP  L48 Unfezant (NORMAL/FLYING): 416,365,164,297
        IMP  L74 Klinklang (STEEL): 544,528,92,216
        REG  L26 Blitzle (ELECTRIC): 351,263,218,228
        REG  L63 Hitmonlee (FIGHTING): 280,523,252,156
        REG  L63 Hitmonchan (FIGHTING): 264,9,97,5
        REG  L56 Unfezant (NORMAL/FLYING): 332,211,156,369
        REG  L47 Boldore (ROCK): 479,523,28,201
        REG  L45 Swinub (ICE/GROUND): 89,196,317,216
        REG  L32 Scolipede (BUG/POISON): 398,371,205,249
        REG  L65 Hitmontop (FIGHTING): 490,444,360,252
        REG  L52 Amoonguss (GRASS/POISON): 202,188,185,310
        REG  L64 Archeops (ROCK/FLYING): 157,89,334,406
        REG  L54 Metang (STEEL/PSYCHIC): 442,89,446,182
        REG  L60 Wooper (WATER/GROUND): 89,59,254,114
        REG  L67 Emboar (FIRE/FIGHTING): 315,442,317,359
        REG  L47 Krookodile (GROUND/DARK): 89,399,332,43
        REG  L25 Litwick (GHOST/FIRE): 247,412,92,496
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,263,182,373
        BOSS L41 Weezing (POISON): 499,53,261,360
        BOSS L5 Zigzagoon (NORMAL): 237,168,271,451
        BOSS L51 Dusclops (GHOST): 325,264,50,59
        BOSS L52 Froslass (ICE/GHOST): 196,351,86,109
        BOSS L57 Claydol (GROUND/PSYCHIC): 89,237,397,326
        BOSS L14 Machop (FIGHTING): 490,479,523,237
        BOSS L28 Slaking (NORMAL): 290,490,281,9
        BOSS L44 Whiscash (WATER/GROUND): 523,209,164,213
        BOSS L70 Sharpedo (WATER/DARK): 503,242,46,97
        BOSS L71 Dusknoir (GHOST): 325,264,182,104
        BOSS L73 Altaria (DRAGON/FLYING): 200,89,366,590
        BOSS L77 Carbink (ROCK/FAIRY): 479,585,182,153
        BOSS L57 Cradily (ROCK/GRASS): 202,362,156,201
        BOSS L57 Milotic (WATER): 56,525,287,442
        IMP  L18 Slugma (FIRE): 510,496,281,106
        IMP  L31 Wailmer (WATER): 503,523,156,54
        IMP  L18 Wailmer (WATER): 503,317,182,496
        IMP  L31 Shroomish (GRASS): 202,474,73,156
        IMP  L37 Swellow (NORMAL/FLYING): 263,257,97,216
        IMP  L37 Wailord (WATER): 323,196,214,156
        IMP  L46 Delcatty (NORMAL): 38,91,156,185
        IMP  L24 Shroomish (GRASS): 202,409,164,78
        IMP  L24 Slugma (FIRE): 510,317,115,213
        IMP  L32 Sharpedo (WATER/DARK): 400,398,156,453
        IMP  L55 Camerupt (FIRE/GROUND): 488,76,261,153
        IMP  L50 Blaziken (FIRE/FIGHTING): 264,317,339,332
        IMP  L50 Sceptile (GRASS): 348,337,92,197
        IMP  L64 Altaria (DRAGON/FLYING): 406,523,349,366
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 264,500,164,278
        REG  L4 Zigzagoon (NORMAL): 343,351,45,196
        REG  L25 Slugma (FIRE): 510,246,496,104
        REG  L39 Claydol (GROUND/PSYCHIC): 414,246,322,471
        REG  L36 Golbat (POISON/FLYING): 305,257,103,114
        REG  L34 Golbat (POISON/FLYING): 17,168,109,188
        REG  L33 Roselia (GRASS/POISON): 202,398,605,156
        REG  L43 Solrock (ROCK/PSYCHIC): 444,324,377,397
        REG  L49 Jellicent (WATER/GHOST): 323,605,247,263
        REG  L37 Skarmory (STEEL/FLYING): 211,157,496,168
        REG  L41 Clamperl (WATER): 352,263,216,334
        REG  L39 Tentacruel (WATER/POISON): 61,482,196,263
        REG  L48 Honchkrow (DARK/FLYING): 399,510,290,247
        REG  L53 Flygon (GROUND/DRAGON): 89,522,211,242
        REG  L23 Grimer (POISON): 398,425,216,107
        REG  L51 Mightyena (DARK): 242,510,514,156
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 280,496,43,317
        BOSS L47 Bewear (NORMAL/FIGHTING): 359,332,269,213
        BOSS L56 Probopass (ROCK/STEEL): 350,7,220,446
        BOSS L41 Golisopod (BUG/WATER): 660,529,92,590
        BOSS L66 Froslass (ICE/GHOST): 196,94,156,373
        BOSS L66 Mandibuzz (DARK/FLYING): 413,198,156,399
        BOSS L57 Dugtrio (GROUND/STEEL): 232,421,182,334
        BOSS L52 Sableye (DARK/GHOST): 185,352,261,8
        BOSS L65 Crobat (POISON/FLYING): 413,141,355,590
        BOSS L64 Masquerain (BUG/FLYING): 405,247,18,218
        BOSS L66 Hydreigon (DARK/DRAGON): 406,411,156,104
        BOSS L65 Gyarados (WATER/FLYING): 401,59,46,164
        BOSS L64 Camerupt (FIRE/GROUND): 414,153,92,216
        BOSS L70 Mewtwo (PSYCHIC): 428,59,182,112
        BOSS L63 Crabominable (FIGHTING/ICE): 665,428,339,213
        IMP  L6 Pichu (ELECTRIC): 84,343,496,204
        IMP  L15 Glaceon (ICE): 524,352,694,237
        IMP  L27 Salandit (POISON/FIRE): 474,141,182,488
        IMP  L28 Noibat (FLYING/DRAGON): 332,263,355,289
        IMP  L41 Noivern (FLYING/DRAGON): 542,352,156,94
        IMP  L70 Primarina (WATER/FAIRY): 585,304,277,196
        IMP  L67 Muk (POISON/DARK): 482,317,164,280
        IMP  L53 Zoroark (DARK): 492,332,271,673
        IMP  L68 Zoroark (DARK): 555,490,97,263
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 94,263,115,218
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 351,324,92,447
        IMP  L68 Snorlax (NORMAL): 387,76,92,104
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 351,280,164,393
        IMP  L51 Shiinotic (GRASS/FAIRY): 76,188,236,235
        IMP  L20 Poipole (POISON): 398,263,324,64
        REG  L6 Yungoos (NORMAL): 497,317,43,371
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 496,168,707,213
        REG  L69 Lapras (WATER/ICE): 127,529,258,590
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 85,496,115,113
        REG  L35 Marowak (FIRE/GHOST): 7,196,241,45
        REG  L5 Yungoos (NORMAL): 283,351,182,371
        REG  L5 Yungoos (NORMAL): 496,317,43,371
        REG  L55 Espeon (PSYCHIC): 94,98,384,28
        REG  L5 Yungoos (NORMAL): 237,317,43,526
        REG  L33 Zubat (POISON/FLYING): 332,211,237,48
        REG  L30 Minior (ROCK/FLYING): 512,442,207,246
        REG  L27 Trumbeak (NORMAL/FLYING): 365,488,45,282
        REG  L62 Persian (NORMAL): 387,185,351,207
        REG  L14 Rattata (DARK/NORMAL): 98,555,39,104
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
