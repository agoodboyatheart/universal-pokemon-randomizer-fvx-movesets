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
        BOSS L45 RHYHORN (GROUND/ROCK): 157,30,92,91
        BOSS L45 NIDOKING (POISON/GROUND): 89,58,115,116
        BOSS L55 HITMONLEE (FIGHTING): 26,34,164,96
        BOSS L12 GEODUDE (ROCK/GROUND): 99,69,156,102
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,99,115,149
        BOSS L24 RAICHU (ELECTRIC): 84,6,164,69
        BOSS L37 KOFFING (POISON): 124,120,164,87
        BOSS L43 WEEZING (POISON): 124,126,92,108
        BOSS L42 RAPIDASH (FIRE): 126,34,92,32
        BOSS L38 VENOMOTH (BUG/POISON): 63,72,50,18
        BOSS L53 CLOYSTER (WATER/ICE): 58,61,115,48
        BOSS L56 LAPRAS (WATER/ICE): 61,85,47,54
        BOSS L55 HAUNTER (GHOST/POISON): 101,85,95,94
        BOSS L56 DRAGONAIR (DRAGON): 38,58,92,85
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,21,164,87
        IMP  L9 PIDGEY (NORMAL/FLYING): 99,16,115,164
        IMP  L15 ABRA (PSYCHIC): 66,5,115,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,16,92,156
        IMP  L18 KADABRA (PSYCHIC): 93,161,50,118
        IMP  L16 RATICATE (NORMAL): 129,61,164,104
        IMP  L25 WARTORTLE (WATER): 61,69,115,44
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,95,149
        IMP  L35 ALAKAZAM (PSYCHIC): 60,91,105,69
        IMP  L40 VENUSAUR (GRASS/POISON): 72,38,73,45
        IMP  L45 RHYHORN (GROUND/ROCK): 157,30,156,92
        IMP  L45 GYARADOS (WATER/FLYING): 61,34,164,59
        IMP  L47 GYARADOS (WATER/FLYING): 56,38,92,85
        IMP  L61 ARCANINE (FIRE): 53,91,46,102
        IMP  L63 ARCANINE (FIRE): 53,34,97,43
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,91,92,66
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 16,129,28,164
        REG  L18 MANKEY (FIGHTING): 66,129,102,164
        REG  L19 RATTATA (NORMAL): 98,61,104,92
        REG  L37 VULPIX (FIRE): 53,91,102,109
        REG  L29 WEEZING (POISON): 123,33,102,92
        REG  L31 CLOYSTER (WATER/ICE): 58,61,92,38
        REG  L26 MANKEY (FIGHTING): 66,157,2,156
        REG  L30 HORSEA (WATER): 61,58,130,92
        REG  L29 FEAROW (NORMAL/FLYING): 31,65,156,45
        REG  L70 GYARADOS (WATER/FLYING): 56,85,115,164
        REG  L17 MACHOP (FIGHTING): 69,157,90,118
        REG  L28 EKANS (POISON): 40,44,137,90
        REG  L39 DUGTRIO (GROUND): 91,157,156,63
        REG  L33 HAUNTER (GHOST/POISON): 101,149,164,87
        """);
        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,189,156,228
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,226,168
        BOSS L31 PILOSWINE (ICE/GROUND): 89,30,182,157
        BOSS L37 DRAGONAIR (DRAGON): 225,126,97,21
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 72,94,236,23
        BOSS L46 MACHAMP (FIGHTING): 233,29,184,203
        BOSS L40 ARIADOS (BUG/POISON): 188,202,50,203
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,7,114,97
        BOSS L42 OMASTAR (ROCK/WATER): 61,196,201,168
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,113,229
        BOSS L41 JUMPLUFF (GRASS/FLYING): 72,38,133,235
        BOSS L33 ARIADOS (BUG/POISON): 188,101,184,207
        BOSS L45 MAGMAR (FIRE): 7,238,109,29
        BOSS L77 BLASTOISE (WATER): 56,8,39,29
        BOSS L58 ARCANINE (FIRE): 53,225,174,216
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,168
        IMP  L12 GASTLY (GHOST/POISON): 122,202,174,95
        IMP  L20 HAUNTER (GHOST/POISON): 247,168,95,202
        IMP  L20 ZUBAT (POISON/FLYING): 16,98,197,185
        IMP  L32 MEGANIUM (GRASS): 202,246,77,115
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,33,86,237
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,174,109
        IMP  L35 HAUNTER (GHOST/POISON): 101,85,109,202
        IMP  L43 GENGAR (GHOST/POISON): 101,202,109,7
        IMP  L43 ALAKAZAM (PSYCHIC): 60,7,50,247
        IMP  L43 ALAKAZAM (PSYCHIC): 60,9,105,247
        IMP  L46 ALAKAZAM (PSYCHIC): 94,8,113,7
        IMP  L50 TYPHLOSION (FIRE): 126,89,156,9
        IMP  L50 FERALIGATR (WATER): 56,89,184,223
        REG  L10 CHIKORITA (GRASS): 202,189,203,246
        REG  L20 QUAGSIRE (WATER/GROUND): 189,246,240,203
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,92,203
        REG  L25 NINETALES (FIRE): 52,98,109,175
        REG  L31 RHYDON (GROUND/ROCK): 89,242,237,53
        REG  L18 GROWLITHE (FIRE): 83,242,29,207
        REG  L23 GOLDEEN (WATER): 60,64,207,30
        REG  L28 TENTACOOL (WATER/POISON): 61,196,48,40
        REG  L28 POLIWHIRL (WATER): 145,29,213,170
        REG  L32 ONIX (ROCK/GROUND): 157,231,106,203
        REG  L6 VOLTORB (ELECTRIC): 205,182,129,33
        REG  L31 FURRET (NORMAL): 29,228,92,231
        REG  L42 GOLDUCK (WATER): 60,196,174,193
        REG  L23 PIKACHU (ELECTRIC): 9,189,39,3
        REG  L25 ELECTRODE (ELECTRIC): 205,129,103,49
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 168,36,46,218
        BOSS L53 WALREIN (ICE/WATER): 59,157,46,290
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,52,201,237
        BOSS L43 SEALEO (ICE/WATER): 58,89,258,111
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,315,133,336
        BOSS L50 KABUTOPS (ROCK/WATER): 157,341,14,109
        BOSS L46 HITMONCHAN (FIGHTING): 136,5,197,168
        BOSS L50 MANECTRIC (ELECTRIC): 87,231,46,216
        BOSS L46 GROWLITHE (FIRE): 257,34,46,316
        BOSS L45 KANGASKHAN (NORMAL): 146,89,50,85
        BOSS L45 ALTARIA (DRAGON/FLYING): 225,89,97,58
        BOSS L58 SKARMORY (STEEL/FLYING): 143,263,156,214
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 317,248,38,89
        BOSS L56 LAPRAS (WATER/ICE): 58,38,47,56
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,156,188
        IMP  L34 MIGHTYENA (DARK): 168,343,184,207
        IMP  L40 GOLBAT (POISON/FLYING): 332,228,174,203
        IMP  L20 GROVYLE (GRASS): 71,225,219,203
        IMP  L29 LOMBRE (WATER/GRASS): 331,280,14,252
        IMP  L18 SLUGMA (FIRE): 52,88,113,104
        IMP  L29 PELIPPER (WATER/FLYING): 55,239,240,211
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,23,240,223
        IMP  L22 ZUBAT (POISON/FLYING): 332,211,182,185
        IMP  L47 ROSELIA (GRASS/POISON): 202,290,73,275
        IMP  L53 ALTARIA (DRAGON/FLYING): 225,89,216,231
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 192,161,92,103
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 280,53,14,317
        IMP  L34 GROVYLE (GRASS): 348,264,219,317
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,69,182,157
        IMP  L15 MUDKIP (WATER): 55,33,92,91
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,201
        REG  L26 MARILL (WATER): 145,21,280,227
        REG  L26 MIGHTYENA (DARK): 44,91,316,305
        REG  L33 MACHOP (FIGHTING): 264,8,96,5
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,89,322,53
        REG  L35 PLUSLE (ELECTRIC): 209,98,111,227
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,33,164,216
        REG  L30 KOFFING (POISON): 188,87,108,237
        REG  L6 SEEDOT (GRASS): 202,206,205,106
        REG  L11 MARILL (WATER): 145,196,321,164
        REG  L26 LOMBRE (WATER/GRASS): 75,280,154,7
        REG  L29 XATU (PSYCHIC/FLYING): 332,168,203,43
        REG  L29 ZUBAT (POISON/FLYING): 17,310,259,290
        REG  L34 PELIPPER (WATER/FLYING): 332,196,256,254
        REG  L5 KYOGRE (WATER): 352,351,244,104
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,33,156,189
        BOSS L49 SCIZOR (BUG/STEEL): 405,276,219,97
        BOSS L52 HIPPOWDON (GROUND): 89,317,46,263
        BOSS L57 MAGMORTAR (FIRE): 436,89,5,231
        BOSS L58 SPIRITOMB (GHOST/DARK): 425,290,220,288
        BOSS L20 CHERRIM (GRASS): 412,219,312,33
        BOSS L29 MACHOKE (FIGHTING): 279,418,92,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 202,23,92,237
        BOSS L44 SNEASEL (DARK/ICE): 420,399,164,252
        BOSS L48 WEAVILE (DARK/ICE): 419,264,115,398
        BOSS L66 WHISCASH (WATER/GROUND): 401,63,164,104
        BOSS L69 RAPIDASH (FIRE): 315,231,98,398
        BOSS L72 ALAKAZAM (PSYCHIC): 94,63,227,7
        BOSS L78 GARCHOMP (DRAGON/GROUND): 91,421,92,263
        BOSS L58 MAGMORTAR (FIRE): 7,183,269,108
        IMP  L7 STARLY (NORMAL/FLYING): 283,168,297,466
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 324,347,451,89
        IMP  L27 GROTLE (GRASS): 75,44,115,328
        IMP  L34 STARAVIA (NORMAL/FLYING): 290,228,97,28
        IMP  L36 STARAPTOR (NORMAL/FLYING): 290,370,18,211
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,317,14,36
        IMP  L47 RAPIDASH (FIRE): 53,23,261,32
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,413,164,369
        IMP  L25 KADABRA (PSYCHIC): 93,168,113,8
        IMP  L27 GROTLE (GRASS): 75,290,182,156
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,400,164,290
        IMP  L69 RAPIDASH (FIRE): 394,98,164,340
        IMP  L73 SNORLAX (NORMAL): 34,228,204,442
        IMP  L83 SNORLAX (NORMAL): 34,317,18,247
        IMP  L60 SKUNTANK (POISON/DARK): 168,163,156,103
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 332,228,174,237
        REG  L36 SWINUB (ICE/GROUND): 89,333,34,316
        REG  L6 GEODUDE (ROCK/GROUND): 205,33,104,164
        REG  L21 CARNIVINE (GRASS): 22,189,388,230
        REG  L21 DRIFLOON (GHOST/FLYING): 16,205,318,196
        REG  L36 MURKROW (DARK/FLYING): 65,290,114,211
        REG  L39 MURKROW (DARK/FLYING): 65,228,103,263
        REG  L58 PELIPPER (WATER/FLYING): 56,369,254,240
        REG  L32 EEVEE (NORMAL): 98,247,92,91
        REG  L48 SEAKING (WATER): 127,30,340,156
        REG  L42 GOLBAT (POISON/FLYING): 305,247,174,114
        REG  L23 BUIZEL (WATER): 291,91,445,156
        REG  L42 MAGNETON (ELECTRIC/STEEL): 435,324,104,216
        REG  L56 EMPOLEON (WATER/STEEL): 362,430,196,446
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,94
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 485,257,182,58
        BOSS L72 Lucario (FIGHTING/STEEL): 410,245,339,242
        BOSS L28 Flaaffy (ELECTRIC): 451,324,97,268
        BOSS L48 Haxorus (DRAGON): 200,89,46,116
        BOSS L50 Cofagrigus (GHOST): 101,399,174,114
        BOSS L67 Simipour (WATER): 401,441,392,270
        BOSS L76 Clefable (NORMAL): 304,309,361,409
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,324,113,496
        BOSS L49 Carracosta (WATER/ROCK): 444,58,504,453
        BOSS L56 Lucario (FIGHTING/STEEL): 418,317,468,428
        BOSS L73 Golurk (GROUND/GHOST): 101,276,182,58
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 280,317,262,398
        BOSS L75 Arcanine (FIRE): 126,370,164,555
        BOSS L75 Glaceon (ICE): 59,485,197,514
        IMP  L8 Tepig (FIRE): 488,249,92,317
        IMP  L48 Cryogonal (ICE): 420,430,115,114
        IMP  L23 Pansage (GRASS): 331,512,73,154
        IMP  L31 Tranquill (NORMAL/FLYING): 263,332,355,273
        IMP  L39 Unfezant (NORMAL/FLYING): 263,211,355,45
        IMP  L46 Cryogonal (ICE): 62,430,115,163
        IMP  L55 Unfezant (NORMAL/FLYING): 98,257,273,207
        IMP  L55 Simisear (FIRE): 7,343,156,490
        IMP  L62 Unfezant (NORMAL/FLYING): 143,263,526,92
        IMP  L62 Flygon (GROUND/DRAGON): 200,276,468,341
        IMP  L65 Unfezant (NORMAL/FLYING): 332,369,297,253
        IMP  L65 Eelektross (ELECTRIC): 435,409,156,317
        IMP  L41 Simisear (FIRE): 257,282,182,411
        IMP  L48 Unfezant (NORMAL/FLYING): 332,257,273,381
        IMP  L74 Klinklang (STEEL): 544,435,508,319
        REG  L26 Blitzle (ELECTRIC): 351,228,24,213
        REG  L63 Hitmonlee (FIGHTING): 280,299,272,229
        REG  L63 Hitmonchan (FIGHTING): 327,228,170,213
        REG  L56 Unfezant (NORMAL/FLYING): 332,211,366,13
        REG  L47 Boldore (ROCK): 479,29,484,201
        REG  L45 Swinub (ICE/GROUND): 333,523,258,90
        REG  L32 Scolipede (BUG/POISON): 404,523,398,97
        REG  L65 Hitmontop (FIGHTING): 183,98,418,339
        REG  L52 Amoonguss (GRASS/POISON): 499,202,111,230
        REG  L64 Archeops (ROCK/FLYING): 512,242,397,213
        REG  L54 Metang (STEEL/PSYCHIC): 309,228,447,8
        REG  L60 Wooper (WATER/GROUND): 91,491,105,401
        REG  L67 Emboar (FIRE/FIGHTING): 126,528,447,111
        REG  L47 Krookodile (GROUND/DARK): 372,421,422,28
        REG  L25 Litwick (GHOST/FIRE): 52,123,109,373
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,304,281,422
        BOSS L41 Weezing (POISON): 124,372,114,85
        BOSS L5 Zigzagoon (NORMAL): 168,321,496,451
        BOSS L51 Dusclops (GHOST): 425,196,164,373
        BOSS L52 Froslass (ICE/GHOST): 8,85,113,94
        BOSS L57 Claydol (GROUND/PSYCHIC): 94,414,201,263
        BOSS L14 Machop (FIGHTING): 2,371,227,479
        BOSS L28 Slaking (NORMAL): 498,612,182,317
        BOSS L44 Whiscash (WATER/GROUND): 401,426,133,36
        BOSS L70 Sharpedo (WATER/DARK): 503,340,184,523
        BOSS L71 Dusknoir (GHOST): 247,612,220,50
        BOSS L73 Altaria (DRAGON/FLYING): 406,89,355,156
        BOSS L77 Carbink (ROCK/FAIRY): 479,414,115,207
        BOSS L57 Cradily (ROCK/GRASS): 479,362,105,218
        BOSS L57 Milotic (WATER): 362,196,109,263
        IMP  L18 Slugma (FIRE): 488,205,220,263
        IMP  L31 Wailmer (WATER): 503,38,182,104
        IMP  L18 Wailmer (WATER): 503,237,182,205
        IMP  L31 Shroomish (GRASS): 76,358,219,77
        IMP  L37 Swellow (NORMAL/FLYING): 586,228,355,18
        IMP  L37 Wailord (WATER): 323,58,92,428
        IMP  L46 Delcatty (NORMAL): 263,358,92,428
        IMP  L24 Shroomish (GRASS): 202,358,219,290
        IMP  L24 Slugma (FIRE): 510,246,151,611
        IMP  L32 Sharpedo (WATER/DARK): 400,340,156,290
        IMP  L55 Camerupt (FIRE/GROUND): 436,317,92,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 481,530,97,213
        IMP  L50 Sceptile (GRASS): 348,225,92,73
        IMP  L64 Altaria (DRAGON/FLYING): 332,257,114,585
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,348,92,581
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,499,317,115
        REG  L39 Claydol (GROUND/PSYCHIC): 94,246,377,529
        REG  L36 Golbat (POISON/FLYING): 305,512,253,259
        REG  L34 Golbat (POISON/FLYING): 413,168,216,213
        REG  L33 Roselia (GRASS/POISON): 437,188,605,320
        REG  L43 Solrock (ROCK/PSYCHIC): 317,523,182,149
        REG  L49 Jellicent (WATER/GHOST): 323,605,412,182
        REG  L37 Skarmory (STEEL/FLYING): 211,65,228,156
        REG  L41 Clamperl (WATER): 330,59,392,263
        REG  L39 Tentacruel (WATER/POISON): 482,282,58,114
        REG  L48 Honchkrow (DARK/FLYING): 492,94,101,180
        REG  L53 Flygon (GROUND/DRAGON): 337,332,586,341
        REG  L23 Grimer (POISON): 398,91,106,325
        REG  L51 Mightyena (DARK): 242,583,104,590
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,526,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 264,707,203,38
        BOSS L56 Probopass (ROCK/STEEL): 430,192,182,161
        BOSS L41 Golisopod (BUG/WATER): 660,57,191,529
        BOSS L66 Froslass (ICE/GHOST): 420,358,92,311
        BOSS L66 Mandibuzz (DARK/FLYING): 19,555,432,119
        BOSS L57 Dugtrio (GROUND/STEEL): 91,37,182,90
        BOSS L52 Sableye (DARK/GHOST): 421,280,197,398
        BOSS L65 Crobat (POISON/FLYING): 440,168,114,404
        BOSS L64 Masquerain (BUG/FLYING): 403,202,366,18
        BOSS L66 Hydreigon (DARK/DRAGON): 242,414,355,29
        BOSS L65 Gyarados (WATER/FLYING): 401,242,92,196
        BOSS L64 Camerupt (FIRE/GROUND): 436,430,261,397
        BOSS L70 Mewtwo (PSYCHIC): 473,396,277,9
        BOSS L63 Crabominable (FIGHTING/ICE): 419,146,133,168
        IMP  L6 Pichu (ELECTRIC): 527,574,156,268
        IMP  L15 Glaceon (ICE): 196,247,694,500
        IMP  L27 Salandit (POISON/FIRE): 481,398,261,252
        IMP  L28 Noibat (FLYING/DRAGON): 314,44,432,590
        IMP  L41 Noivern (FLYING/DRAGON): 143,257,432,304
        IMP  L70 Primarina (WATER/FAIRY): 585,664,392,195
        IMP  L67 Muk (POISON/DARK): 242,317,164,262
        IMP  L53 Zoroark (DARK): 282,326,182,97
        IMP  L68 Zoroark (DARK): 539,369,14,43
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 527,324,347,604
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 473,411,86,604
        IMP  L68 Snorlax (NORMAL): 498,428,174,411
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 209,343,97,45
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,188,668,79
        IMP  L20 Poipole (POISON): 51,497,204,590
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 283,351,92,237
        REG  L69 Lapras (WATER/ICE): 362,573,94,321
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 209,282,381,683
        REG  L35 Marowak (FIRE/GHOST): 488,125,203,399
        REG  L5 Yungoos (NORMAL): 317,259,237,351
        REG  L5 Yungoos (NORMAL): 168,216,164,33
        REG  L55 Espeon (PSYCHIC): 94,324,477,218
        REG  L5 Yungoos (NORMAL): 371,92,283,162
        REG  L33 Zubat (POISON/FLYING): 19,168,202,259
        REG  L30 Minior (ROCK/FLYING): 512,451,446,347
        REG  L27 Trumbeak (NORMAL/FLYING): 31,65,432,590
        REG  L62 Persian (NORMAL): 343,196,39,386
        REG  L14 Rattata (DARK/NORMAL): 98,279,256,254
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
