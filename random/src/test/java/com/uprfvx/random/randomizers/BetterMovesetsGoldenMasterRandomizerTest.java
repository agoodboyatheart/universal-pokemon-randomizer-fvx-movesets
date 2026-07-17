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
        BOSS L12 GEODUDE (ROCK/GROUND): 205,33,201,216
        BOSS L49 SCIZOR (BUG/STEEL): 418,458,97,228
        BOSS L52 HIPPOWDON (GROUND): 414,423,164,422
        BOSS L57 MAGMORTAR (FIRE): 436,9,109,317
        BOSS L58 SPIRITOMB (GHOST/DARK): 399,94,262,317
        BOSS L20 CHERRIM (GRASS): 345,320,207,205
        BOSS L29 MACHOKE (FIGHTING): 27,371,9,265
        BOSS L42 ABOMASNOW (GRASS/ICE): 420,202,113,317
        BOSS L44 SNEASEL (DARK/ICE): 228,280,269,91
        BOSS L48 WEAVILE (DARK/ICE): 228,264,269,404
        BOSS L66 WHISCASH (WATER/GROUND): 414,209,133,444
        BOSS L69 RAPIDASH (FIRE): 257,398,97,38
        BOSS L72 ALAKAZAM (PSYCHIC): 60,9,227,409
        BOSS L78 GARCHOMP (DRAGON/GROUND): 89,398,184,156
        BOSS L58 MAGMORTAR (FIRE): 257,94,241,270
        IMP  L7 STARLY (NORMAL/FLYING): 365,31,92,193
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 89,334,317,247
        IMP  L27 GROTLE (GRASS): 75,328,115,321
        IMP  L34 STARAVIA (NORMAL/FLYING): 332,228,297,98
        IMP  L36 STARAPTOR (NORMAL/FLYING): 98,257,182,18
        IMP  L48 HERACROSS (BUG/FIGHTING): 279,168,156,421
        IMP  L47 RAPIDASH (FIRE): 257,224,261,218
        IMP  L42 STARAPTOR (NORMAL/FLYING): 413,211,297,216
        IMP  L25 KADABRA (PSYCHIC): 60,247,50,409
        IMP  L27 GROTLE (GRASS): 402,263,164,174
        IMP  L61 HERACROSS (BUG/FIGHTING): 264,89,182,282
        IMP  L69 RAPIDASH (FIRE): 394,36,97,32
        IMP  L73 SNORLAX (NORMAL): 34,89,164,254
        IMP  L83 SNORLAX (NORMAL): 263,276,204,281
        IMP  L60 SKUNTANK (POISON/DARK): 242,91,46,139
        REG  L5 STARLY (NORMAL/FLYING): 98,365,310,207
        REG  L29 ZUBAT (POISON/FLYING): 365,44,164,18
        REG  L36 SWINUB (ICE/GROUND): 91,333,157,46
        REG  L6 GEODUDE (ROCK/GROUND): 205,189,111,360
        REG  L21 CARNIVINE (GRASS): 22,189,263,380
        REG  L21 DRIFLOON (GHOST/FLYING): 314,168,285,50
        REG  L36 MURKROW (DARK/FLYING): 399,247,375,297
        REG  L39 MURKROW (DARK/FLYING): 65,372,180,373
        REG  L58 PELIPPER (WATER/FLYING): 403,441,54,263
        REG  L32 EEVEE (NORMAL): 343,91,39,28
        REG  L48 SEAKING (WATER): 401,398,340,156
        REG  L42 GOLBAT (POISON/FLYING): 305,202,174,212
        REG  L23 BUIZEL (WATER): 453,154,91,445
        REG  L42 MAGNETON (ELECTRIC/STEEL): 435,430,199,161
        REG  L56 EMPOLEON (WATER/STEEL): 362,430,445,213
        """);
        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,94
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 500,257,18,324
        BOSS L72 Lucario (FIGHTING/STEEL): 430,421,46,399
        BOSS L28 Flaaffy (ELECTRIC): 84,324,219,178
        BOSS L48 Haxorus (DRAGON): 525,523,46,179
        BOSS L50 Cofagrigus (GHOST): 101,168,92,506
        BOSS L67 Simipour (WATER): 401,512,92,213
        BOSS L76 Clefable (NORMAL): 343,196,215,309
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,332,355,486
        BOSS L49 Carracosta (WATER/ROCK): 157,91,182,362
        BOSS L56 Lucario (FIGHTING/STEEL): 418,299,46,406
        BOSS L73 Golurk (GROUND/GHOST): 89,409,164,430
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 490,427,109,523
        BOSS L75 Arcanine (FIRE): 126,231,219,168
        BOSS L75 Glaceon (ICE): 196,401,197,263
        IMP  L8 Tepig (FIRE): 52,33,182,222
        IMP  L48 Cryogonal (ICE): 62,324,156,258
        IMP  L23 Pansage (GRASS): 402,343,468,249
        IMP  L31 Tranquill (NORMAL/FLYING): 332,211,273,234
        IMP  L39 Unfezant (NORMAL/FLYING): 416,211,92,355
        IMP  L46 Cryogonal (ICE): 62,430,92,282
        IMP  L55 Unfezant (NORMAL/FLYING): 98,369,269,257
        IMP  L55 Simisear (FIRE): 315,490,272,157
        IMP  L62 Unfezant (NORMAL/FLYING): 253,369,366,381
        IMP  L62 Flygon (GROUND/DRAGON): 525,523,468,76
        IMP  L65 Unfezant (NORMAL/FLYING): 63,257,269,403
        IMP  L65 Eelektross (ELECTRIC): 451,306,489,242
        IMP  L41 Simisear (FIRE): 53,421,468,213
        IMP  L48 Unfezant (NORMAL/FLYING): 253,403,156,92
        IMP  L74 Klinklang (STEEL): 544,263,397,199
        REG  L26 Blitzle (ELECTRIC): 351,228,24,213
        REG  L63 Hitmonlee (FIGHTING): 136,398,299,282
        REG  L63 Hitmonchan (FIGHTING): 327,157,170,229
        REG  L56 Unfezant (NORMAL/FLYING): 263,211,297,273
        REG  L47 Boldore (ROCK): 157,523,446,213
        REG  L45 Swinub (ICE/GROUND): 91,419,34,276
        REG  L32 Scolipede (BUG/POISON): 450,523,289,157
        REG  L65 Hitmontop (FIGHTING): 410,157,228,501
        REG  L52 Amoonguss (GRASS/POISON): 202,474,275,380
        REG  L64 Archeops (ROCK/FLYING): 317,525,213,201
        REG  L54 Metang (STEEL/PSYCHIC): 418,228,115,356
        REG  L60 Wooper (WATER/GROUND): 89,8,503,281
        REG  L67 Emboar (FIRE/FIGHTING): 490,7,241,157
        REG  L47 Krookodile (GROUND/DARK): 492,479,525,156
        REG  L25 Litwick (GHOST/FIRE): 101,310,180,151
        """);
        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 372,583,281,305
        BOSS L41 Weezing (POISON): 474,85,114,458
        BOSS L5 Zigzagoon (NORMAL): 173,352,156,86
        BOSS L51 Dusclops (GHOST): 425,612,261,8
        BOSS L52 Froslass (ICE/GHOST): 420,290,289,247
        BOSS L57 Claydol (GROUND/PSYCHIC): 529,58,113,379
        BOSS L14 Machop (FIGHTING): 2,418,156,317
        BOSS L28 Slaking (NORMAL): 163,490,281,352
        BOSS L44 Whiscash (WATER/GROUND): 503,157,164,36
        BOSS L70 Sharpedo (WATER/DARK): 453,196,92,340
        BOSS L71 Dusknoir (GHOST): 101,612,174,193
        BOSS L73 Altaria (DRAGON/FLYING): 337,53,164,36
        BOSS L77 Carbink (ROCK/FAIRY): 585,408,397,334
        BOSS L57 Cradily (ROCK/GRASS): 412,523,109,388
        BOSS L57 Milotic (WATER): 362,406,219,300
        IMP  L18 Slugma (FIRE): 52,496,115,254
        IMP  L31 Wailmer (WATER): 362,340,182,523
        IMP  L18 Wailmer (WATER): 352,237,392,90
        IMP  L31 Shroomish (GRASS): 331,264,77,148
        IMP  L37 Swellow (NORMAL/FLYING): 143,211,164,18
        IMP  L37 Wailord (WATER): 323,34,92,523
        IMP  L46 Delcatty (NORMAL): 290,247,322,426
        IMP  L24 Shroomish (GRASS): 402,474,204,496
        IMP  L24 Slugma (FIRE): 52,151,123,205
        IMP  L32 Sharpedo (WATER/DARK): 453,423,269,263
        IMP  L55 Camerupt (FIRE/GROUND): 488,430,92,414
        IMP  L50 Blaziken (FIRE/FIGHTING): 126,421,174,226
        IMP  L50 Sceptile (GRASS): 72,225,97,103
        IMP  L64 Altaria (DRAGON/FLYING): 332,257,366,523
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 280,332,204,45
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,499,317,115
        REG  L39 Claydol (GROUND/PSYCHIC): 60,451,479,471
        REG  L36 Golbat (POISON/FLYING): 512,188,212,355
        REG  L34 Golbat (POISON/FLYING): 305,162,164,218
        REG  L33 Roselia (GRASS/POISON): 474,72,320,390
        REG  L43 Solrock (ROCK/PSYCHIC): 317,428,472,315
        REG  L49 Jellicent (WATER/GHOST): 61,482,151,506
        REG  L37 Skarmory (STEEL/FLYING): 442,157,174,355
        REG  L41 Clamperl (WATER): 330,196,216,112
        REG  L39 Tentacruel (WATER/POISON): 330,168,240,182
        REG  L48 Honchkrow (DARK/FLYING): 65,101,207,375
        REG  L53 Flygon (GROUND/DRAGON): 407,231,116,90
        REG  L23 Grimer (POISON): 398,325,107,426
        REG  L51 Mightyena (DARK): 242,91,316,231
        """);
        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,526,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 264,707,203,38
        BOSS L56 Probopass (ROCK/STEEL): 430,192,182,161
        BOSS L41 Golisopod (BUG/WATER): 710,474,240,42
        BOSS L66 Froslass (ICE/GHOST): 419,87,191,244
        BOSS L66 Mandibuzz (DARK/FLYING): 403,492,182,198
        BOSS L57 Dugtrio (GROUND/STEEL): 89,332,201,188
        BOSS L52 Sableye (DARK/GHOST): 555,605,220,425
        BOSS L65 Crobat (POISON/FLYING): 474,428,417,263
        BOSS L64 Masquerain (BUG/FLYING): 405,247,58,168
        BOSS L66 Hydreigon (DARK/DRAGON): 242,423,156,57
        BOSS L65 Gyarados (WATER/FLYING): 340,401,164,59
        BOSS L64 Camerupt (FIRE/GROUND): 481,430,174,707
        BOSS L70 Mewtwo (PSYCHIC): 473,126,113,492
        BOSS L63 Crabominable (FIGHTING/ICE): 419,89,526,428
        IMP  L6 Pichu (ELECTRIC): 527,574,156,268
        IMP  L15 Glaceon (ICE): 196,247,694,500
        IMP  L27 Salandit (POISON/FIRE): 481,398,261,252
        IMP  L28 Noibat (FLYING/DRAGON): 332,399,97,421
        IMP  L41 Noivern (FLYING/DRAGON): 19,257,432,366
        IMP  L70 Primarina (WATER/FAIRY): 664,585,227,113
        IMP  L67 Muk (POISON/DARK): 398,612,156,399
        IMP  L53 Zoroark (DARK): 228,369,262,490
        IMP  L68 Zoroark (DARK): 539,247,92,259
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 527,324,273,321
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,98,164,590
        IMP  L68 Snorlax (NORMAL): 498,89,133,57
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 609,324,92,478
        IMP  L51 Shiinotic (GRASS/FAIRY): 72,188,109,275
        IMP  L20 Poipole (POISON): 474,31,92,406
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 283,351,92,237
        REG  L69 Lapras (WATER/ICE): 362,573,94,321
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 209,282,381,683
        REG  L35 Marowak (FIRE/GHOST): 708,125,220,196
        REG  L5 Yungoos (NORMAL): 317,92,496,279
        REG  L5 Yungoos (NORMAL): 351,207,237,168
        REG  L55 Espeon (PSYCHIC): 500,304,608,347
        REG  L5 Yungoos (NORMAL): 279,590,237,317
        REG  L33 Zubat (POISON/FLYING): 413,141,355,428
        REG  L30 Minior (ROCK/FLYING): 512,605,442,156
        REG  L27 Trumbeak (NORMAL/FLYING): 65,350,168,488
        REG  L62 Persian (NORMAL): 263,196,168,274
        REG  L14 Rattata (DARK/NORMAL): 33,168,92,373
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
