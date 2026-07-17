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
        BOSS L16 SCYTHER (BUG/FLYING): 210,13,168,211
        BOSS L31 PILOSWINE (ICE/GROUND): 89,157,174,196
        BOSS L37 DRAGONAIR (DRAGON): 225,126,97,21
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 72,94,236,23
        BOSS L46 MACHAMP (FIGHTING): 233,29,184,203
        BOSS L40 ARIADOS (BUG/POISON): 188,101,50,168
        BOSS L47 DRAGONITE (DRAGON/FLYING): 225,223,113,85
        BOSS L42 OMASTAR (ROCK/WATER): 61,196,168,21
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,87,109,113
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,29,73,39
        BOSS L33 ARIADOS (BUG/POISON): 188,101,156,60
        BOSS L45 MAGMAR (FIRE): 7,5,174,9
        BOSS L77 BLASTOISE (WATER): 56,58,174,39
        BOSS L58 ARCANINE (FIRE): 53,225,92,156
        IMP  L12 GASTLY (GHOST/POISON): 122,202,174,168
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,180
        IMP  L20 HAUNTER (GHOST/POISON): 247,168,95,203
        IMP  L20 ZUBAT (POISON/FLYING): 16,98,197,185
        IMP  L32 MEGANIUM (GRASS): 202,246,77,115
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,33,86,182
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,86,48
        IMP  L35 HAUNTER (GHOST/POISON): 247,94,174,109
        IMP  L35 HAUNTER (GHOST/POISON): 101,85,109,202
        IMP  L43 GENGAR (GHOST/POISON): 247,202,95,180
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,105,168
        IMP  L43 ALAKAZAM (PSYCHIC): 248,7,50,244
        IMP  L46 ALAKAZAM (PSYCHIC): 94,168,156,50
        IMP  L50 TYPHLOSION (FIRE): 7,9,241,29
        IMP  L50 FERALIGATR (WATER): 56,89,184,231
        REG  L10 CHIKORITA (GRASS): 202,189,203,246
        REG  L20 QUAGSIRE (WATER/GROUND): 189,249,237,246
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,211,207,168
        REG  L25 NINETALES (FIRE): 52,91,95,46
        REG  L31 RHYDON (GROUND/ROCK): 157,30,222,201
        REG  L18 GROWLITHE (FIRE): 83,44,219,218
        REG  L23 GOLDEEN (WATER): 60,30,104,64
        REG  L28 TENTACOOL (WATER/POISON): 61,202,114,51
        REG  L28 POLIWHIRL (WATER): 145,249,174,196
        REG  L32 ONIX (ROCK/GROUND): 157,29,175,89
        REG  L6 VOLTORB (ELECTRIC): 205,218,33,129
        REG  L31 FURRET (NORMAL): 29,223,247,9
        REG  L42 GOLDUCK (WATER): 60,196,95,113
        REG  L23 PIKACHU (ELECTRIC): 84,189,39,129
        REG  L25 ELECTRODE (ELECTRIC): 129,205,104,182
        """);
        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 168,36,46,218
        BOSS L53 WALREIN (ICE/WATER): 59,157,46,290
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,52,201,237
        BOSS L43 SEALEO (ICE/WATER): 58,89,258,111
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,317,241,36
        BOSS L50 KABUTOPS (ROCK/WATER): 317,62,240,282
        BOSS L46 HITMONCHAN (FIGHTING): 183,317,164,97
        BOSS L50 MANECTRIC (ELECTRIC): 85,242,182,231
        BOSS L46 GROWLITHE (FIRE): 257,263,97,242
        BOSS L45 KANGASKHAN (NORMAL): 23,157,89,223
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,126,47,211
        BOSS L58 SKARMORY (STEEL/FLYING): 65,263,201,259
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 58,322,285,290
        BOSS L56 LAPRAS (WATER/ICE): 196,85,182,32
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,153,334,280
        IMP  L34 MIGHTYENA (DARK): 168,343,184,207
        IMP  L40 GOLBAT (POISON/FLYING): 188,228,109,332
        IMP  L20 GROVYLE (GRASS): 202,225,219,69
        IMP  L29 LOMBRE (WATER/GRASS): 202,7,235,310
        IMP  L18 SLUGMA (FIRE): 52,88,151,237
        IMP  L29 PELIPPER (WATER/FLYING): 55,239,92,164
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,157,92,69
        IMP  L22 ZUBAT (POISON/FLYING): 16,141,174,216
        IMP  L47 ROSELIA (GRASS/POISON): 72,34,74,218
        IMP  L53 ALTARIA (DRAGON/FLYING): 225,126,97,263
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 209,161,115,192
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 53,280,164,317
        IMP  L34 GROVYLE (GRASS): 348,264,164,242
        IMP  L34 MARSHTOMP (WATER/GROUND): 341,69,164,58
        IMP  L15 MUDKIP (WATER): 55,253,92,301
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,201
        REG  L26 MARILL (WATER): 145,21,280,227
        REG  L26 MIGHTYENA (DARK): 44,91,316,305
        REG  L33 MACHOP (FIGHTING): 264,8,96,5
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,89,322,53
        REG  L35 PLUSLE (ELECTRIC): 87,223,227,218
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,205,164,48
        REG  L30 KOFFING (POISON): 188,53,164,103
        REG  L6 SEEDOT (GRASS): 202,98,73,241
        REG  L11 MARILL (WATER): 55,69,47,102
        REG  L26 LOMBRE (WATER/GRASS): 331,252,267,9
        REG  L29 XATU (PSYCHIC/FLYING): 64,168,101,244
        REG  L29 ZUBAT (POISON/FLYING): 17,202,207,164
        REG  L34 PELIPPER (WATER/FLYING): 332,168,54,290
        REG  L5 KYOGRE (WATER): 352,196,173,156
        """);
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 317,33,446,189
        BOSS L49 SCIZOR (BUG/STEEL): 442,290,92,400
        BOSS L52 HIPPOWDON (GROUND): 89,422,303,231
        BOSS L57 MAGMORTAR (FIRE): 126,85,238,94
        BOSS L58 SPIRITOMB (GHOST/DARK): 425,174,182,399
        BOSS L20 CHERRIM (GRASS): 345,312,267,205
        BOSS L29 MACHOKE (FIGHTING): 69,91,339,279
        BOSS L42 ABOMASNOW (GRASS/ICE): 402,58,73,157
        BOSS L44 SNEASEL (DARK/ICE): 228,421,258,163
        BOSS L48 WEAVILE (DARK/ICE): 420,279,180,252
        BOSS L66 WHISCASH (WATER/GROUND): 414,196,92,157
        BOSS L69 RAPIDASH (FIRE): 394,23,261,32
        BOSS L72 ALAKAZAM (PSYCHIC): 94,411,105,324
        BOSS L78 GARCHOMP (DRAGON/GROUND): 91,163,184,442
        BOSS L58 MAGMORTAR (FIRE): 436,85,156,270
        IMP  L7 STARLY (NORMAL/FLYING): 365,98,297,189
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 326,263,334,237
        IMP  L27 GROTLE (GRASS): 331,328,219,133
        IMP  L34 STARAVIA (NORMAL/FLYING): 332,263,355,257
        IMP  L36 STARAPTOR (NORMAL/FLYING): 98,370,182,218
        IMP  L48 HERACROSS (BUG/FIGHTING): 264,91,92,282
        IMP  L47 RAPIDASH (FIRE): 257,23,204,32
        IMP  L42 STARAPTOR (NORMAL/FLYING): 98,370,97,92
        IMP  L25 KADABRA (PSYCHIC): 60,8,115,134
        IMP  L27 GROTLE (GRASS): 402,44,219,164
        IMP  L61 HERACROSS (BUG/FIGHTING): 264,444,92,224
        IMP  L69 RAPIDASH (FIRE): 394,290,261,32
        IMP  L73 SNORLAX (NORMAL): 38,228,281,89
        IMP  L83 SNORLAX (NORMAL): 34,276,92,317
        IMP  L60 SKUNTANK (POISON/DARK): 400,91,182,188
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 17,202,253,269
        REG  L36 SWINUB (ICE/GROUND): 333,317,207,426
        REG  L6 GEODUDE (ROCK/GROUND): 317,33,104,360
        REG  L21 CARNIVINE (GRASS): 22,189,216,263
        REG  L21 DRIFLOON (GHOST/FLYING): 247,16,107,278
        REG  L36 MURKROW (DARK/FLYING): 228,94,101,259
        REG  L39 MURKROW (DARK/FLYING): 143,94,375,114
        REG  L58 PELIPPER (WATER/FLYING): 403,56,369,254
        REG  L32 EEVEE (NORMAL): 98,91,164,213
        REG  L48 SEAKING (WATER): 127,290,216,207
        REG  L42 GOLBAT (POISON/FLYING): 403,247,18,212
        REG  L23 BUIZEL (WATER): 362,189,196,445
        REG  L42 MAGNETON (ELECTRIC/STEEL): 451,161,86,49
        REG  L56 EMPOLEON (WATER/STEEL): 56,430,157,207
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,94
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,430,322,285
        BOSS L72 Lucario (FIGHTING/STEEL): 410,94,182,242
        BOSS L28 Flaaffy (ELECTRIC): 521,496,86,113
        BOSS L48 Haxorus (DRAGON): 530,411,182,116
        BOSS L50 Cofagrigus (GHOST): 506,399,262,213
        BOSS L67 Simipour (WATER): 56,231,417,270
        BOSS L76 Clefable (NORMAL): 263,451,236,8
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,98,204,86
        BOSS L49 Carracosta (WATER/ROCK): 157,89,504,282
        BOSS L56 Lucario (FIGHTING/STEEL): 430,299,197,398
        BOSS L73 Golurk (GROUND/GHOST): 89,58,397,9
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,8,269,400
        BOSS L75 Arcanine (FIRE): 53,406,46,207
        BOSS L75 Glaceon (ICE): 58,500,92,237
        IMP  L8 Tepig (FIRE): 488,33,269,46
        IMP  L48 Cryogonal (ICE): 59,430,258,229
        IMP  L23 Pansage (GRASS): 331,310,235,496
        IMP  L31 Tranquill (NORMAL/FLYING): 403,211,218,98
        IMP  L39 Unfezant (NORMAL/FLYING): 98,332,92,234
        IMP  L46 Cryogonal (ICE): 58,76,277,115
        IMP  L55 Unfezant (NORMAL/FLYING): 143,257,297,98
        IMP  L55 Simisear (FIRE): 481,157,468,253
        IMP  L62 Unfezant (NORMAL/FLYING): 263,211,234,403
        IMP  L62 Flygon (GROUND/DRAGON): 200,89,468,28
        IMP  L65 Unfezant (NORMAL/FLYING): 416,143,269,156
        IMP  L65 Eelektross (ELECTRIC): 521,401,92,207
        IMP  L41 Simisear (FIRE): 257,76,164,43
        IMP  L48 Unfezant (NORMAL/FLYING): 263,211,526,381
        IMP  L74 Klinklang (STEEL): 544,263,92,334
        REG  L26 Blitzle (ELECTRIC): 351,228,24,213
        REG  L63 Hitmonlee (FIGHTING): 490,228,299,514
        REG  L63 Hitmonchan (FIGHTING): 327,157,237,339
        REG  L56 Unfezant (NORMAL/FLYING): 332,211,366,297
        REG  L47 Boldore (ROCK): 479,29,414,201
        REG  L45 Swinub (ICE/GROUND): 419,317,523,175
        REG  L32 Scolipede (BUG/POISON): 404,228,431,89
        REG  L65 Hitmontop (FIGHTING): 279,91,92,501
        REG  L52 Amoonguss (GRASS/POISON): 72,474,74,230
        REG  L64 Archeops (ROCK/FLYING): 340,282,523,334
        REG  L54 Metang (STEEL/PSYCHIC): 418,247,397,334
        REG  L60 Wooper (WATER/GROUND): 91,8,503,133
        REG  L67 Emboar (FIRE/FIGHTING): 315,442,316,526
        REG  L47 Krookodile (GROUND/DARK): 372,91,337,46
        REG  L25 Litwick (GHOST/FIRE): 83,51,218,477
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 372,305,46,304
        BOSS L41 Weezing (POISON): 499,53,114,139
        BOSS L5 Zigzagoon (NORMAL): 352,493,162,33
        BOSS L51 Dusclops (GHOST): 247,280,109,196
        BOSS L52 Froslass (ICE/GHOST): 506,577,196,324
        BOSS L57 Claydol (GROUND/PSYCHIC): 60,523,322,148
        BOSS L14 Machop (FIGHTING): 2,418,164,523
        BOSS L28 Slaking (NORMAL): 514,228,468,179
        BOSS L44 Whiscash (WATER/GROUND): 401,209,156,426
        BOSS L70 Sharpedo (WATER/DARK): 362,130,97,372
        BOSS L71 Dusknoir (GHOST): 425,89,109,196
        BOSS L73 Altaria (DRAGON/FLYING): 407,257,263,211
        BOSS L77 Carbink (ROCK/FAIRY): 246,414,397,585
        BOSS L57 Cradily (ROCK/GRASS): 402,317,220,414
        BOSS L57 Milotic (WATER): 362,406,95,244
        IMP  L18 Slugma (FIRE): 52,263,262,281
        IMP  L31 Wailmer (WATER): 503,340,392,304
        IMP  L18 Wailmer (WATER): 250,263,182,196
        IMP  L31 Shroomish (GRASS): 402,474,164,264
        IMP  L37 Swellow (NORMAL/FLYING): 413,211,18,586
        IMP  L37 Wailord (WATER): 503,340,46,321
        IMP  L46 Delcatty (NORMAL): 38,426,219,193
        IMP  L24 Shroomish (GRASS): 331,409,204,213
        IMP  L24 Slugma (FIRE): 52,237,220,157
        IMP  L32 Sharpedo (WATER/DARK): 453,317,92,399
        IMP  L55 Camerupt (FIRE/GROUND): 284,430,201,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 7,332,241,282
        IMP  L50 Sceptile (GRASS): 202,530,92,242
        IMP  L64 Altaria (DRAGON/FLYING): 337,257,366,304
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 473,425,219,530
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,499,317,115
        REG  L39 Claydol (GROUND/PSYCHIC): 94,246,377,529
        REG  L36 Golbat (POISON/FLYING): 413,369,104,216
        REG  L34 Golbat (POISON/FLYING): 413,247,428,289
        REG  L33 Roselia (GRASS/POISON): 412,326,230,398
        REG  L43 Solrock (ROCK/PSYCHIC): 444,89,149,218
        REG  L49 Jellicent (WATER/GHOST): 101,362,202,433
        REG  L37 Skarmory (STEEL/FLYING): 413,168,203,317
        REG  L41 Clamperl (WATER): 362,59,207,287
        REG  L39 Tentacruel (WATER/POISON): 398,59,202,290
        REG  L48 Honchkrow (DARK/FLYING): 555,247,289,65
        REG  L53 Flygon (GROUND/DRAGON): 523,7,405,406
        REG  L23 Grimer (POISON): 398,7,107,114
        REG  L51 Mightyena (DARK): 372,422,259,382
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,526,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 264,707,203,38
        BOSS L56 Probopass (ROCK/STEEL): 430,192,182,161
        BOSS L41 Golisopod (BUG/WATER): 42,534,220,398
        BOSS L66 Froslass (ICE/GHOST): 420,577,92,113
        BOSS L66 Mandibuzz (DARK/FLYING): 492,332,184,366
        BOSS L57 Dugtrio (GROUND/STEEL): 91,157,164,161
        BOSS L52 Sableye (DARK/GHOST): 492,9,261,182
        BOSS L65 Crobat (POISON/FLYING): 440,141,174,399
        BOSS L64 Masquerain (BUG/FLYING): 403,61,59,679
        BOSS L66 Hydreigon (DARK/DRAGON): 406,257,92,562
        BOSS L65 Gyarados (WATER/FLYING): 401,423,184,523
        BOSS L64 Camerupt (FIRE/GROUND): 426,317,397,267
        BOSS L70 Mewtwo (PSYCHIC): 94,396,164,385
        BOSS L63 Crabominable (FIGHTING/ICE): 419,9,526,228
        IMP  L6 Pichu (ELECTRIC): 527,574,156,268
        IMP  L15 Glaceon (ICE): 196,247,694,500
        IMP  L27 Salandit (POISON/FIRE): 481,398,261,252
        IMP  L28 Noibat (FLYING/DRAGON): 332,399,97,421
        IMP  L41 Noivern (FLYING/DRAGON): 337,257,97,263
        IMP  L70 Primarina (WATER/FAIRY): 503,58,133,590
        IMP  L67 Muk (POISON/DARK): 372,264,156,499
        IMP  L53 Zoroark (DARK): 492,340,97,490
        IMP  L68 Zoroark (DARK): 539,326,269,247
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 527,324,219,175
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 94,263,227,528
        IMP  L68 Snorlax (NORMAL): 263,707,204,428
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 9,280,97,343
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,188,79,451
        IMP  L20 Poipole (POISON): 474,31,45,406
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 283,351,92,237
        REG  L69 Lapras (WATER/ICE): 362,573,94,321
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 209,282,381,683
        REG  L35 Marowak (FIRE/GHOST): 708,9,7,125
        REG  L5 Yungoos (NORMAL): 168,164,497,317
        REG  L5 Yungoos (NORMAL): 371,207,283,33
        REG  L55 Espeon (PSYCHIC): 248,324,287,447
        REG  L5 Yungoos (NORMAL): 317,526,496,351
        REG  L33 Zubat (POISON/FLYING): 332,305,289,104
        REG  L30 Minior (ROCK/FLYING): 157,428,512,605
        REG  L27 Trumbeak (NORMAL/FLYING): 365,369,432,280
        REG  L62 Persian (NORMAL): 343,369,445,274
        REG  L14 Rattata (DARK/NORMAL): 228,279,39,254
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
