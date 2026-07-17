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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,38,92,85
        BOSS L45 NIDOKING (POISON/GROUND): 89,87,115,37
        BOSS L55 HITMONLEE (FIGHTING): 26,38,92,104
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,92,104
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,161,86,102
        BOSS L24 RAICHU (ELECTRIC): 84,66,92,5
        BOSS L37 KOFFING (POISON): 124,85,92,126
        BOSS L43 WEEZING (POISON): 124,63,156,126
        BOSS L42 RAPIDASH (FIRE): 126,38,156,45
        BOSS L38 VENOMOTH (BUG/POISON): 60,72,156,50
        BOSS L53 CLOYSTER (WATER/ICE): 59,161,115,43
        BOSS L56 LAPRAS (WATER/ICE): 56,94,109,54
        BOSS L55 HAUNTER (GHOST/POISON): 101,164,102,85
        BOSS L56 DRAGONAIR (DRAGON): 59,126,86,115
        BOSS L62 DRAGONITE (DRAGON/FLYING): 85,21,164,32
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,99,115,92
        IMP  L15 ABRA (PSYCHIC): 161,66,156,115
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,129,156,28
        IMP  L18 KADABRA (PSYCHIC): 93,5,50,102
        IMP  L16 RATICATE (NORMAL): 158,61,164,156
        IMP  L25 WARTORTLE (WATER): 145,66,92,102
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,95,104
        IMP  L35 ALAKAZAM (PSYCHIC): 60,36,164,156
        IMP  L40 VENUSAUR (GRASS/POISON): 72,63,77,73
        IMP  L45 RHYHORN (GROUND/ROCK): 91,85,92,164
        IMP  L45 GYARADOS (WATER/FLYING): 56,126,115,87
        IMP  L47 GYARADOS (WATER/FLYING): 61,36,156,59
        IMP  L61 ARCANINE (FIRE): 53,38,156,46
        IMP  L63 ARCANINE (FIRE): 53,91,115,97
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,89,92,69
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,28,164
        REG  L18 MANKEY (FIGHTING): 66,5,157,156
        REG  L19 RATTATA (NORMAL): 129,55,104,39
        REG  L37 VULPIX (FIRE): 53,98,156,46
        REG  L29 WEEZING (POISON): 123,33,156,92
        REG  L31 CLOYSTER (WATER/ICE): 62,153,164,92
        REG  L26 MANKEY (FIGHTING): 69,154,164,43
        REG  L30 HORSEA (WATER): 61,58,92,130
        REG  L29 FEAROW (NORMAL/FLYING): 64,31,43,104
        REG  L70 GYARADOS (WATER/FLYING): 56,85,104,43
        REG  L17 MACHOP (FIGHTING): 66,157,90,164
        REG  L28 EKANS (POISON): 40,157,137,44
        REG  L39 DUGTRIO (GROUND): 89,36,157,28
        REG  L33 HAUNTER (GHOST/POISON): 101,94,72,102
        """);
        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,228
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,226,168
        BOSS L31 PILOSWINE (ICE/GROUND): 89,157,92,156
        BOSS L37 DRAGONAIR (DRAGON): 225,126,86,59
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 76,73,121,188
        BOSS L46 MACHAMP (FIGHTING): 67,7,197,193
        BOSS L40 ARIADOS (BUG/POISON): 188,101,226,202
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,223,92,7
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,174,240
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,196,156,61
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,63,174,235
        BOSS L33 ARIADOS (BUG/POISON): 188,101,92,237
        BOSS L45 MAGMAR (FIRE): 126,231,197,5
        BOSS L77 BLASTOISE (WATER): 56,196,114,223
        BOSS L58 ARCANINE (FIRE): 126,231,241,225
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,173
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,180
        IMP  L20 HAUNTER (GHOST/POISON): 247,202,149,168
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,18,202
        IMP  L32 MEGANIUM (GRASS): 202,34,156,246
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,49,86,237
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,92,199
        IMP  L35 HAUNTER (GHOST/POISON): 101,94,114,87
        IMP  L35 HAUNTER (GHOST/POISON): 247,92,180,202
        IMP  L43 GENGAR (GHOST/POISON): 101,87,95,180
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,105,168
        IMP  L43 ALAKAZAM (PSYCHIC): 60,7,50,92
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,113,8
        IMP  L50 TYPHLOSION (FIRE): 126,66,111,231
        IMP  L50 FERALIGATR (WATER): 56,196,184,203
        REG  L10 CHIKORITA (GRASS): 202,246,14,45
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,92,156
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,213,197
        REG  L25 NINETALES (FIRE): 52,185,219,129
        REG  L31 RHYDON (GROUND/ROCK): 89,37,242,231
        REG  L18 GROWLITHE (FIRE): 52,91,216,219
        REG  L23 GOLDEEN (WATER): 60,64,237,114
        REG  L28 TENTACOOL (WATER/POISON): 61,40,114,229
        REG  L28 POLIWHIRL (WATER): 61,249,111,168
        REG  L32 ONIX (ROCK/GROUND): 157,231,92,203
        REG  L6 VOLTORB (ELECTRIC): 205,216,237,33
        REG  L31 FURRET (NORMAL): 63,231,179,168
        REG  L42 GOLDUCK (WATER): 196,60,50,244
        REG  L23 PIKACHU (ELECTRIC): 84,129,216,111
        REG  L25 ELECTRODE (ELECTRIC): 49,205,156,214
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,36,184,305
        BOSS L53 WALREIN (ICE/WATER): 62,317,174,104
        BOSS L26 CAMERUPT (FIRE/GROUND): 189,290,182,164
        BOSS L43 SEALEO (ICE/WATER): 62,157,174,281
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,315,133,116
        BOSS L50 KABUTOPS (ROCK/WATER): 157,69,14,61
        BOSS L46 HITMONCHAN (FIGHTING): 327,229,339,157
        BOSS L50 MANECTRIC (ELECTRIC): 85,34,174,237
        BOSS L46 GROWLITHE (FIRE): 257,231,46,336
        BOSS L45 KANGASKHAN (NORMAL): 306,231,156,76
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,89,97,38
        BOSS L58 SKARMORY (STEEL/FLYING): 65,211,174,191
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 138,157,322,95
        BOSS L56 LAPRAS (WATER/ICE): 58,231,47,87
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,317,113,111
        IMP  L34 MIGHTYENA (DARK): 168,343,92,247
        IMP  L40 GOLBAT (POISON/FLYING): 188,247,174,213
        IMP  L20 GROVYLE (GRASS): 331,225,73,317
        IMP  L29 LOMBRE (WATER/GRASS): 352,189,164,331
        IMP  L18 SLUGMA (FIRE): 52,88,113,281
        IMP  L29 PELIPPER (WATER/FLYING): 352,16,182,196
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,36,174,157
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,48,185
        IMP  L47 ROSELIA (GRASS/POISON): 76,188,73,102
        IMP  L53 ALTARIA (DRAGON/FLYING): 332,58,168,263
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 209,38,92,103
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 280,315,156,116
        IMP  L34 GROVYLE (GRASS): 72,228,219,264
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,69,164,193
        IMP  L15 MUDKIP (WATER): 55,290,174,196
        REG  L21 GEODUDE (ROCK/GROUND): 88,263,335,201
        REG  L26 MARILL (WATER): 55,189,47,240
        REG  L26 MIGHTYENA (DARK): 44,91,281,244
        REG  L33 MACHOP (FIGHTING): 279,89,193,34
        REG  L41 SOLROCK (ROCK/PSYCHIC): 317,126,244,247
        REG  L35 PLUSLE (ELECTRIC): 87,223,98,313
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,86,182,33
        REG  L30 KOFFING (POISON): 188,126,92,164
        REG  L6 SEEDOT (GRASS): 331,205,164,206
        REG  L11 MARILL (WATER): 145,205,287,227
        REG  L26 LOMBRE (WATER/GRASS): 331,310,230,45
        REG  L29 XATU (PSYCHIC/FLYING): 332,202,207,182
        REG  L29 ZUBAT (POISON/FLYING): 17,141,228,290
        REG  L34 PELIPPER (WATER/FLYING): 332,59,164,54
        REG  L5 KYOGRE (WATER): 352,196,164,129
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,189,397,33
        BOSS L49 SCIZOR (BUG/STEEL): 442,163,334,280
        BOSS L52 HIPPOWDON (GROUND): 89,422,182,281
        BOSS L57 MAGMORTAR (FIRE): 126,85,263,238
        BOSS L58 SPIRITOMB (GHOST/DARK): 228,290,262,317
        BOSS L20 CHERRIM (GRASS): 345,312,321,33
        BOSS L29 MACHOKE (FIGHTING): 2,317,92,7
        BOSS L42 ABOMASNOW (GRASS/ICE): 420,412,182,290
        BOSS L44 SNEASEL (DARK/ICE): 420,231,97,458
        BOSS L48 WEAVILE (DARK/ICE): 419,458,115,411
        BOSS L66 WHISCASH (WATER/GROUND): 56,36,133,89
        BOSS L69 RAPIDASH (FIRE): 394,263,164,218
        BOSS L72 ALAKAZAM (PSYCHIC): 94,411,50,105
        BOSS L78 GARCHOMP (DRAGON/GROUND): 407,91,201,398
        BOSS L58 MAGMORTAR (FIRE): 315,411,92,317
        IMP  L7 STARLY (NORMAL/FLYING): 33,168,156,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,246,334,263
        IMP  L27 GROTLE (GRASS): 72,328,235,110
        IMP  L34 STARAVIA (NORMAL/FLYING): 332,257,297,38
        IMP  L36 STARAPTOR (NORMAL/FLYING): 416,332,156,45
        IMP  L48 HERACROSS (BUG/FIGHTING): 279,157,334,421
        IMP  L47 RAPIDASH (FIRE): 53,231,241,224
        IMP  L42 STARAPTOR (NORMAL/FLYING): 413,228,355,290
        IMP  L25 KADABRA (PSYCHIC): 93,247,115,263
        IMP  L27 GROTLE (GRASS): 75,33,115,388
        IMP  L61 HERACROSS (BUG/FIGHTING): 279,282,332,263
        IMP  L69 RAPIDASH (FIRE): 315,37,39,398
        IMP  L73 SNORLAX (NORMAL): 263,228,156,316
        IMP  L83 SNORLAX (NORMAL): 38,276,174,247
        IMP  L60 SKUNTANK (POISON/DARK): 398,91,262,231
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,141,228,428
        REG  L36 SWINUB (ICE/GROUND): 91,420,34,246
        REG  L6 GEODUDE (ROCK/GROUND): 205,189,203,92
        REG  L21 CARNIVINE (GRASS): 75,371,290,275
        REG  L21 DRIFLOON (GHOST/FLYING): 16,205,285,86
        REG  L36 MURKROW (DARK/FLYING): 228,65,259,375
        REG  L39 MURKROW (DARK/FLYING): 399,101,103,263
        REG  L58 PELIPPER (WATER/FLYING): 56,196,182,403
        REG  L32 EEVEE (NORMAL): 98,231,237,313
        REG  L48 SEAKING (WATER): 127,340,182,156
        REG  L42 GOLBAT (POISON/FLYING): 188,211,355,18
        REG  L23 BUIZEL (WATER): 362,317,263,228
        REG  L42 MAGNETON (ELECTRIC/STEEL): 451,161,218,393
        REG  L56 EMPOLEON (WATER/STEEL): 56,196,213,300
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,247
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 94,324,86,119
        BOSS L72 Lucario (FIGHTING/STEEL): 136,317,46,319
        BOSS L28 Flaaffy (ELECTRIC): 521,496,215,216
        BOSS L48 Haxorus (DRAGON): 530,280,14,400
        BOSS L50 Cofagrigus (GHOST): 506,412,277,216
        BOSS L67 Simipour (WATER): 56,490,468,263
        BOSS L76 Clefable (NORMAL): 304,280,361,309
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,403,204,496
        BOSS L49 Carracosta (WATER/ROCK): 401,89,201,110
        BOSS L56 Lucario (FIGHTING/STEEL): 418,247,339,319
        BOSS L73 Golurk (GROUND/GHOST): 89,359,334,157
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,168,261,398
        BOSS L75 Arcanine (FIRE): 394,370,156,245
        BOSS L75 Glaceon (ICE): 58,231,182,98
        IMP  L8 Tepig (FIRE): 488,249,281,33
        IMP  L48 Cryogonal (ICE): 62,430,156,282
        IMP  L23 Pansage (GRASS): 345,490,320,282
        IMP  L31 Tranquill (NORMAL/FLYING): 98,211,92,355
        IMP  L39 Unfezant (NORMAL/FLYING): 98,257,234,516
        IMP  L46 Cryogonal (ICE): 59,163,277,207
        IMP  L55 Unfezant (NORMAL/FLYING): 263,332,273,211
        IMP  L55 Simisear (FIRE): 488,343,241,447
        IMP  L62 Unfezant (NORMAL/FLYING): 98,143,355,257
        IMP  L62 Flygon (GROUND/DRAGON): 200,91,157,231
        IMP  L65 Unfezant (NORMAL/FLYING): 416,211,197,104
        IMP  L65 Eelektross (ELECTRIC): 528,231,46,104
        IMP  L41 Simisear (FIRE): 126,411,164,343
        IMP  L48 Unfezant (NORMAL/FLYING): 98,257,526,43
        IMP  L74 Klinklang (STEEL): 544,263,475,319
        REG  L26 Blitzle (ELECTRIC): 351,228,24,213
        REG  L63 Hitmonlee (FIGHTING): 26,168,398,89
        REG  L63 Hitmonchan (FIGHTING): 327,228,5,193
        REG  L56 Unfezant (NORMAL/FLYING): 416,143,273,381
        REG  L47 Boldore (ROCK): 350,89,335,475
        REG  L45 Swinub (ICE/GROUND): 419,91,157,446
        REG  L32 Scolipede (BUG/POISON): 450,89,76,207
        REG  L65 Hitmontop (FIGHTING): 279,418,193,501
        REG  L52 Amoonguss (GRASS/POISON): 72,188,203,147
        REG  L64 Archeops (ROCK/FLYING): 444,369,242,46
        REG  L54 Metang (STEEL/PSYCHIC): 309,89,393,324
        REG  L60 Wooper (WATER/GROUND): 91,21,330,482
        REG  L67 Emboar (FIRE/FIGHTING): 7,457,92,335
        REG  L47 Krookodile (GROUND/DARK): 228,337,89,43
        REG  L25 Litwick (GHOST/FIRE): 481,51,109,399
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,583,156,253
        BOSS L41 Weezing (POISON): 499,372,182,194
        BOSS L5 Zigzagoon (NORMAL): 237,352,156,189
        BOSS L51 Dusclops (GHOST): 101,280,109,288
        BOSS L52 Froslass (ICE/GHOST): 419,358,244,87
        BOSS L57 Claydol (GROUND/PSYCHIC): 414,444,115,377
        BOSS L14 Machop (FIGHTING): 2,168,339,479
        BOSS L28 Slaking (NORMAL): 10,7,164,216
        BOSS L44 Whiscash (WATER/GROUND): 341,444,182,37
        BOSS L70 Sharpedo (WATER/DARK): 362,416,97,46
        BOSS L71 Dusknoir (GHOST): 506,264,114,263
        BOSS L73 Altaria (DRAGON/FLYING): 332,126,47,538
        BOSS L77 Carbink (ROCK/FAIRY): 444,414,115,201
        BOSS L57 Cradily (ROCK/GRASS): 202,482,201,235
        BOSS L57 Milotic (WATER): 362,59,114,231
        IMP  L18 Slugma (FIRE): 488,237,174,261
        IMP  L31 Wailmer (WATER): 362,59,392,38
        IMP  L18 Wailmer (WATER): 503,263,90,523
        IMP  L31 Shroomish (GRASS): 412,264,77,263
        IMP  L37 Swellow (NORMAL/FLYING): 586,228,355,403
        IMP  L37 Wailord (WATER): 291,59,240,133
        IMP  L46 Delcatty (NORMAL): 514,358,226,215
        IMP  L24 Shroomish (GRASS): 71,33,78,409
        IMP  L24 Slugma (FIRE): 52,237,281,104
        IMP  L32 Sharpedo (WATER/DARK): 400,428,240,503
        IMP  L55 Camerupt (FIRE/GROUND): 488,317,495,91
        IMP  L50 Blaziken (FIRE/FIGHTING): 257,136,226,400
        IMP  L50 Sceptile (GRASS): 331,612,219,523
        IMP  L64 Altaria (DRAGON/FLYING): 200,89,366,585
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,523,182,8
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 510,499,108,254
        REG  L39 Claydol (GROUND/PSYCHIC): 94,317,218,360
        REG  L36 Golbat (POISON/FLYING): 474,162,366,104
        REG  L34 Golbat (POISON/FLYING): 332,202,218,103
        REG  L33 Roselia (GRASS/POISON): 412,326,390,230
        REG  L43 Solrock (ROCK/PSYCHIC): 473,479,324,277
        REG  L49 Jellicent (WATER/GHOST): 323,399,105,156
        REG  L37 Skarmory (STEEL/FLYING): 442,65,104,148
        REG  L41 Clamperl (WATER): 503,263,48,300
        REG  L39 Tentacruel (WATER/POISON): 503,58,112,202
        REG  L48 Honchkrow (DARK/FLYING): 228,65,213,114
        REG  L53 Flygon (GROUND/DRAGON): 91,200,366,182
        REG  L23 Grimer (POISON): 124,371,325,114
        REG  L51 Mightyena (DARK): 242,263,259,218
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,526,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 409,8,156,421
        BOSS L56 Probopass (ROCK/STEEL): 430,435,182,38
        BOSS L41 Golisopod (BUG/WATER): 42,534,240,282
        BOSS L66 Froslass (ICE/GHOST): 420,358,86,324
        BOSS L66 Mandibuzz (DARK/FLYING): 492,198,156,259
        BOSS L57 Dugtrio (GROUND/STEEL): 707,228,156,29
        BOSS L52 Sableye (DARK/GHOST): 282,324,347,280
        BOSS L65 Crobat (POISON/FLYING): 413,369,355,202
        BOSS L64 Masquerain (BUG/FLYING): 405,61,78,182
        BOSS L66 Hydreigon (DARK/DRAGON): 168,430,432,89
        BOSS L65 Gyarados (WATER/FLYING): 127,200,92,85
        BOSS L64 Camerupt (FIRE/GROUND): 126,426,261,442
        BOSS L70 Mewtwo (PSYCHIC): 248,324,105,85
        BOSS L63 Crabominable (FIGHTING/ICE): 8,152,334,146
        IMP  L6 Pichu (ELECTRIC): 527,574,156,268
        IMP  L15 Glaceon (ICE): 524,497,273,164
        IMP  L27 Salandit (POISON/FIRE): 481,123,182,168
        IMP  L28 Noibat (FLYING/DRAGON): 17,247,97,497
        IMP  L41 Noivern (FLYING/DRAGON): 542,586,18,399
        IMP  L70 Primarina (WATER/FAIRY): 585,664,133,343
        IMP  L67 Muk (POISON/DARK): 398,372,397,164
        IMP  L53 Zoroark (DARK): 539,340,417,490
        IMP  L68 Zoroark (DARK): 539,326,97,247
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 609,324,113,473
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 209,264,204,21
        IMP  L68 Snorlax (NORMAL): 38,89,526,7
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 473,87,97,231
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,235,236,402
        IMP  L20 Poipole (POISON): 474,324,204,216
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,92,259,497
        REG  L69 Lapras (WATER/ICE): 401,58,109,349
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 609,282,673,411
        REG  L35 Marowak (FIRE/GHOST): 708,157,241,253
        REG  L5 Yungoos (NORMAL): 351,104,33,371
        REG  L5 Yungoos (NORMAL): 371,269,162,283
        REG  L55 Espeon (PSYCHIC): 473,605,174,197
        REG  L5 Yungoos (NORMAL): 317,92,237,351
        REG  L33 Zubat (POISON/FLYING): 413,228,590,305
        REG  L30 Minior (ROCK/FLYING): 246,428,356,451
        REG  L27 Trumbeak (NORMAL/FLYING): 64,282,249,119
        REG  L62 Persian (NORMAL): 304,492,85,421
        REG  L14 Rattata (DARK/NORMAL): 98,44,216,382
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
