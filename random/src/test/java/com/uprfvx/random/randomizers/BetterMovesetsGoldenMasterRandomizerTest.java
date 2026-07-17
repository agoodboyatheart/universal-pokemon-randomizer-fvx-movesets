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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,30,156,126
        BOSS L45 NIDOKING (POISON/GROUND): 89,66,164,6
        BOSS L55 HITMONLEE (FIGHTING): 27,5,92,116
        BOSS L12 GEODUDE (ROCK/GROUND): 99,69,164,102
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,115,104
        BOSS L24 RAICHU (ELECTRIC): 84,66,156,45
        BOSS L37 KOFFING (POISON): 124,85,92,108
        BOSS L43 WEEZING (POISON): 123,126,164,108
        BOSS L42 RAPIDASH (FIRE): 52,23,156,45
        BOSS L38 VENOMOTH (BUG/POISON): 141,76,18,36
        BOSS L53 CLOYSTER (WATER/ICE): 55,161,164,110
        BOSS L56 LAPRAS (WATER/ICE): 61,85,47,109
        BOSS L55 HAUNTER (GHOST/POISON): 101,87,95,94
        BOSS L56 DRAGONAIR (DRAGON): 34,85,86,43
        BOSS L62 DRAGONITE (DRAGON/FLYING): 36,58,92,156
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,16,18,115
        IMP  L15 ABRA (PSYCHIC): 69,99,115,149
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,129,92,115
        IMP  L18 KADABRA (PSYCHIC): 93,99,86,50
        IMP  L16 RATICATE (NORMAL): 158,61,92,102
        IMP  L25 WARTORTLE (WATER): 61,69,115,39
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,164,104
        IMP  L35 ALAKAZAM (PSYCHIC): 60,91,156,86
        IMP  L40 VENUSAUR (GRASS/POISON): 72,34,73,74
        IMP  L45 RHYHORN (GROUND/ROCK): 89,36,92,85
        IMP  L45 GYARADOS (WATER/FLYING): 61,130,164,102
        IMP  L47 GYARADOS (WATER/FLYING): 55,38,115,58
        IMP  L61 ARCANINE (FIRE): 53,91,46,82
        IMP  L63 ARCANINE (FIRE): 53,91,46,156
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,66,92,104
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,28,18
        REG  L18 MANKEY (FIGHTING): 69,6,157,156
        REG  L19 RATTATA (NORMAL): 98,61,104,92
        REG  L37 VULPIX (FIRE): 53,34,115,39
        REG  L29 WEEZING (POISON): 123,33,85,104
        REG  L31 CLOYSTER (WATER/ICE): 62,55,104,38
        REG  L26 MANKEY (FIGHTING): 69,66,154,118
        REG  L30 HORSEA (WATER): 61,58,38,92
        REG  L29 FEAROW (NORMAL/FLYING): 65,36,164,119
        REG  L70 GYARADOS (WATER/FLYING): 55,126,43,87
        REG  L17 MACHOP (FIGHTING): 69,2,92,118
        REG  L28 EKANS (POISON): 40,157,137,104
        REG  L39 DUGTRIO (GROUND): 89,157,38,104
        REG  L33 HAUNTER (GHOST/POISON): 101,72,92,102
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 33,228,156,189
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,228,29
        BOSS L31 PILOSWINE (ICE/GROUND): 89,30,182,44
        BOSS L37 DRAGONAIR (DRAGON): 239,53,86,196
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 72,246,182,23
        BOSS L46 MACHAMP (FIGHTING): 67,7,197,193
        BOSS L40 ARIADOS (BUG/POISON): 188,101,50,60
        BOSS L47 DRAGONITE (DRAGON/FLYING): 239,9,113,126
        BOSS L42 OMASTAR (ROCK/WATER): 55,168,114,62
        BOSS L47 STARMIE (WATER/PSYCHIC): 61,58,109,229
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,93,78,38
        BOSS L33 ARIADOS (BUG/POISON): 141,91,101,94
        BOSS L45 MAGMAR (FIRE): 7,5,241,2
        BOSS L77 BLASTOISE (WATER): 56,8,240,193
        BOSS L58 ARCANINE (FIRE): 172,34,219,242
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,173
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,180
        IMP  L20 HAUNTER (GHOST/POISON): 122,202,149,168
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,174,141
        IMP  L32 MEGANIUM (GRASS): 75,246,115,34
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,86,205,33
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,92,237
        IMP  L35 HAUNTER (GHOST/POISON): 101,109,212,85
        IMP  L35 HAUNTER (GHOST/POISON): 101,202,109,182
        IMP  L43 GENGAR (GHOST/POISON): 101,7,182,244
        IMP  L43 ALAKAZAM (PSYCHIC): 60,168,105,9
        IMP  L43 ALAKAZAM (PSYCHIC): 94,8,7,247
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,105,134
        IMP  L50 TYPHLOSION (FIRE): 126,66,197,98
        IMP  L50 FERALIGATR (WATER): 55,46,242,8
        REG  L10 CHIKORITA (GRASS): 202,33,14,216
        REG  L20 QUAGSIRE (WATER/GROUND): 55,189,205,29
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 16,92,211,185
        REG  L25 NINETALES (FIRE): 52,91,180,203
        REG  L31 RHYDON (GROUND/ROCK): 157,231,222,201
        REG  L18 GROWLITHE (FIRE): 52,225,92,249
        REG  L23 GOLDEEN (WATER): 129,64,39,218
        REG  L28 TENTACOOL (WATER/POISON): 61,202,218,207
        REG  L28 POLIWHIRL (WATER): 55,58,95,3
        REG  L32 ONIX (ROCK/GROUND): 88,89,203,106
        REG  L6 VOLTORB (ELECTRIC): 205,237,203,129
        REG  L31 FURRET (NORMAL): 98,9,179,8
        REG  L42 GOLDUCK (WATER): 60,58,95,193
        REG  L23 PIKACHU (ELECTRIC): 9,205,227,207
        REG  L25 ELECTRODE (ELECTRIC): 205,104,49,29
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 168,263,281,305
        BOSS L53 WALREIN (ICE/WATER): 62,157,258,352
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,205,241,45
        BOSS L43 SEALEO (ICE/WATER): 62,281,205,352
        BOSS L44 CAMERUPT (FIRE/GROUND): 126,157,201,290
        BOSS L50 KABUTOPS (ROCK/WATER): 205,341,109,72
        BOSS L46 HITMONCHAN (FIGHTING): 327,89,197,317
        BOSS L50 MANECTRIC (ELECTRIC): 85,98,182,218
        BOSS L46 GROWLITHE (FIRE): 172,38,97,43
        BOSS L45 KANGASKHAN (NORMAL): 23,223,46,126
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,126,168,31
        BOSS L58 SKARMORY (STEEL/FLYING): 143,263,174,191
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,58,95,317
        BOSS L56 LAPRAS (WATER/ICE): 56,351,219,58
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,334,9
        IMP  L34 MIGHTYENA (DARK): 168,305,184,36
        IMP  L40 GOLBAT (POISON/FLYING): 314,202,109,174
        IMP  L20 GROVYLE (GRASS): 331,225,73,317
        IMP  L29 LOMBRE (WATER/GRASS): 75,252,73,168
        IMP  L18 SLUGMA (FIRE): 52,290,115,203
        IMP  L29 PELIPPER (WATER/FLYING): 352,196,97,332
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,157,156,231
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,182,290
        IMP  L47 ROSELIA (GRASS/POISON): 202,188,74,129
        IMP  L53 ALTARIA (DRAGON/FLYING): 225,53,46,89
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 84,115,104,33
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 53,69,339,34
        IMP  L34 GROVYLE (GRASS): 348,9,219,332
        IMP  L34 MARSHTOMP (WATER/GROUND): 91,23,164,8
        IMP  L15 MUDKIP (WATER): 55,23,92,301
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 55,69,227,196
        REG  L26 MIGHTYENA (DARK): 44,91,46,290
        REG  L33 MACHOP (FIGHTING): 233,53,67,116
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,89,53,201
        REG  L35 PLUSLE (ELECTRIC): 351,205,104,203
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,216,129,205
        REG  L30 KOFFING (POISON): 123,85,149,92
        REG  L6 SEEDOT (GRASS): 202,205,106,213
        REG  L11 MARILL (WATER): 352,69,203,287
        REG  L26 LOMBRE (WATER/GRASS): 352,9,230,207
        REG  L29 XATU (PSYCHIC/FLYING): 94,168,218,115
        REG  L29 ZUBAT (POISON/FLYING): 16,211,98,237
        REG  L34 PELIPPER (WATER/FLYING): 17,239,254,256
        REG  L5 KYOGRE (WATER): 352,196,347,129
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,33,397,164
        BOSS L49 SCIZOR (BUG/STEEL): 418,276,355,445
        BOSS L52 HIPPOWDON (GROUND): 89,424,281,422
        BOSS L57 MAGMORTAR (FIRE): 436,85,109,157
        BOSS L58 SPIRITOMB (GHOST/DARK): 185,347,262,318
        BOSS L20 CHERRIM (GRASS): 345,92,218,33
        BOSS L29 MACHOKE (FIGHTING): 233,91,227,9
        BOSS L42 ABOMASNOW (GRASS/ICE): 402,157,92,420
        BOSS L44 SNEASEL (DARK/ICE): 228,404,258,420
        BOSS L48 WEAVILE (DARK/ICE): 400,279,258,10
        BOSS L66 WHISCASH (WATER/GROUND): 414,444,156,58
        BOSS L69 RAPIDASH (FIRE): 394,31,97,24
        BOSS L72 ALAKAZAM (PSYCHIC): 428,247,347,264
        BOSS L78 GARCHOMP (DRAGON/GROUND): 337,91,164,421
        BOSS L58 MAGMORTAR (FIRE): 257,2,109,270
        IMP  L7 STARLY (NORMAL/FLYING): 31,228,182,365
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 89,201,246,185
        IMP  L27 GROTLE (GRASS): 402,328,92,388
        IMP  L34 STARAVIA (NORMAL/FLYING): 263,257,355,168
        IMP  L36 STARAPTOR (NORMAL/FLYING): 290,228,239,370
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,228,175,91
        IMP  L47 RAPIDASH (FIRE): 53,23,92,224
        IMP  L42 STARAPTOR (NORMAL/FLYING): 413,36,18,466
        IMP  L25 KADABRA (PSYCHIC): 93,324,182,148
        IMP  L27 GROTLE (GRASS): 402,37,235,414
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,444,264,38
        IMP  L69 RAPIDASH (FIRE): 394,340,241,76
        IMP  L73 SNORLAX (NORMAL): 34,264,164,428
        IMP  L83 SNORLAX (NORMAL): 38,89,156,94
        IMP  L60 SKUNTANK (POISON/DARK): 400,53,184,91
        REG  L5 STARLY (NORMAL/FLYING): 98,365,28,164
        REG  L29 ZUBAT (POISON/FLYING): 365,247,185,259
        REG  L36 SWINUB (ICE/GROUND): 333,91,203,115
        REG  L6 GEODUDE (ROCK/GROUND): 317,33,445,213
        REG  L21 CARNIVINE (GRASS): 22,282,275,447
        REG  L21 DRIFLOON (GHOST/FLYING): 314,371,278,129
        REG  L36 MURKROW (DARK/FLYING): 314,189,103,297
        REG  L39 MURKROW (DARK/FLYING): 17,168,109,269
        REG  L58 PELIPPER (WATER/FLYING): 362,351,156,290
        REG  L32 EEVEE (NORMAL): 98,91,28,321
        REG  L48 SEAKING (WATER): 127,340,392,213
        REG  L42 GOLBAT (POISON/FLYING): 413,228,466,164
        REG  L23 BUIZEL (WATER): 453,210,154,189
        REG  L42 MAGNETON (ELECTRIC/STEEL): 87,430,334,156
        REG  L56 EMPOLEON (WATER/STEEL): 56,189,164,48
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,247
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 60,257,86,496
        BOSS L72 Lucario (FIGHTING/STEEL): 396,198,197,399
        BOSS L28 Flaaffy (ELECTRIC): 84,324,156,215
        BOSS L48 Haxorus (DRAGON): 525,89,184,82
        BOSS L50 Cofagrigus (GHOST): 101,412,263,466
        BOSS L67 Simipour (WATER): 56,10,240,270
        BOSS L76 Clefable (NORMAL): 263,473,227,280
        BOSS L28 Emolga (ELECTRIC/FLYING): 351,512,369,228
        BOSS L49 Carracosta (WATER/ROCK): 157,249,240,362
        BOSS L56 Lucario (FIGHTING/STEEL): 418,410,46,406
        BOSS L73 Golurk (GROUND/GHOST): 523,444,156,324
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 409,7,50,86
        BOSS L75 Arcanine (FIRE): 315,523,242,528
        BOSS L75 Glaceon (ICE): 196,38,273,197
        IMP  L8 Tepig (FIRE): 488,249,46,360
        IMP  L48 Cryogonal (ICE): 196,324,156,263
        IMP  L23 Pansage (GRASS): 22,91,526,259
        IMP  L31 Tranquill (NORMAL/FLYING): 98,211,366,403
        IMP  L39 Unfezant (NORMAL/FLYING): 263,211,95,273
        IMP  L46 Cryogonal (ICE): 62,430,164,512
        IMP  L55 Unfezant (NORMAL/FLYING): 332,257,234,297
        IMP  L55 Simisear (FIRE): 315,231,281,276
        IMP  L62 Unfezant (NORMAL/FLYING): 365,211,273,253
        IMP  L62 Flygon (GROUND/DRAGON): 89,9,201,406
        IMP  L65 Unfezant (NORMAL/FLYING): 143,98,164,237
        IMP  L65 Eelektross (ELECTRIC): 9,401,468,393
        IMP  L41 Simisear (FIRE): 53,263,269,371
        IMP  L48 Unfezant (NORMAL/FLYING): 98,369,197,403
        IMP  L74 Klinklang (STEEL): 544,528,156,393
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 136,418,179,96
        REG  L63 Hitmonchan (FIGHTING): 490,523,4,157
        REG  L56 Unfezant (NORMAL/FLYING): 365,369,45,216
        REG  L47 Boldore (ROCK): 350,189,446,201
        REG  L45 Swinub (ICE/GROUND): 89,333,263,104
        REG  L32 Scolipede (BUG/POISON): 42,401,474,207
        REG  L65 Hitmontop (FIGHTING): 136,444,213,170
        REG  L52 Amoonguss (GRASS/POISON): 499,202,380,147
        REG  L64 Archeops (ROCK/FLYING): 512,211,269,88
        REG  L54 Metang (STEEL/PSYCHIC): 309,9,446,317
        REG  L60 Wooper (WATER/GROUND): 401,8,34,89
        REG  L67 Emboar (FIRE/FIGHTING): 53,89,535,484
        REG  L47 Krookodile (GROUND/DARK): 228,523,156,263
        REG  L25 Litwick (GHOST/FIRE): 101,510,220,445
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,583,269,304
        BOSS L41 Weezing (POISON): 499,372,164,148
        BOSS L5 Zigzagoon (NORMAL): 228,590,496,351
        BOSS L51 Dusclops (GHOST): 506,263,109,261
        BOSS L52 Froslass (ICE/GHOST): 419,94,156,577
        BOSS L57 Claydol (GROUND/PSYCHIC): 414,246,182,496
        BOSS L14 Machop (FIGHTING): 27,282,227,510
        BOSS L28 Slaking (NORMAL): 498,490,174,85
        BOSS L44 Whiscash (WATER/GROUND): 341,157,133,330
        BOSS L70 Sharpedo (WATER/DARK): 168,38,240,503
        BOSS L71 Dusknoir (GHOST): 325,89,164,237
        BOSS L73 Altaria (DRAGON/FLYING): 406,89,97,263
        BOSS L77 Carbink (ROCK/FAIRY): 408,605,201,267
        BOSS L57 Cradily (ROCK/GRASS): 479,362,402,611
        BOSS L57 Milotic (WATER): 362,525,182,196
        IMP  L18 Slugma (FIRE): 52,157,151,263
        IMP  L31 Wailmer (WATER): 352,237,182,196
        IMP  L18 Wailmer (WATER): 55,497,164,111
        IMP  L31 Shroomish (GRASS): 402,263,77,74
        IMP  L37 Swellow (NORMAL/FLYING): 17,257,182,237
        IMP  L37 Wailord (WATER): 323,58,89,499
        IMP  L46 Delcatty (NORMAL): 38,247,47,196
        IMP  L24 Shroomish (GRASS): 402,358,219,218
        IMP  L24 Slugma (FIRE): 52,262,246,290
        IMP  L32 Sharpedo (WATER/DARK): 400,162,156,43
        IMP  L55 Camerupt (FIRE/GROUND): 414,317,46,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 257,91,14,264
        IMP  L50 Sceptile (GRASS): 72,512,46,280
        IMP  L64 Altaria (DRAGON/FLYING): 143,310,366,126
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 427,348,109,612
        REG  L4 Zigzagoon (NORMAL): 496,351,493,164
        REG  L25 Slugma (FIRE): 52,414,88,499
        REG  L39 Claydol (GROUND/PSYCHIC): 529,157,216,322
        REG  L36 Golbat (POISON/FLYING): 332,474,212,369
        REG  L34 Golbat (POISON/FLYING): 305,16,207,129
        REG  L33 Roselia (GRASS/POISON): 345,188,267,496
        REG  L43 Solrock (ROCK/PSYCHIC): 88,33,261,83
        REG  L49 Jellicent (WATER/GHOST): 503,202,378,219
        REG  L37 Skarmory (STEEL/FLYING): 507,228,404,430
        REG  L41 Clamperl (WATER): 352,196,34,207
        REG  L39 Tentacruel (WATER/POISON): 145,188,392,207
        REG  L48 Honchkrow (DARK/FLYING): 17,101,212,297
        REG  L53 Flygon (GROUND/DRAGON): 406,89,522,218
        REG  L23 Grimer (POISON): 398,8,92,1
        REG  L51 Mightyena (DARK): 492,423,216,290
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,418,526,501
        BOSS L47 Bewear (NORMAL/FIGHTING): 173,276,156,421
        BOSS L56 Probopass (ROCK/STEEL): 88,521,397,89
        BOSS L41 Golisopod (BUG/WATER): 450,453,164,59
        BOSS L66 Froslass (ICE/GHOST): 466,419,182,373
        BOSS L66 Mandibuzz (DARK/FLYING): 371,63,334,212
        BOSS L57 Dugtrio (GROUND/STEEL): 89,444,262,188
        BOSS L52 Sableye (DARK/GHOST): 421,605,236,399
        BOSS L65 Crobat (POISON/FLYING): 314,168,182,501
        BOSS L64 Masquerain (BUG/FLYING): 16,341,564,466
        BOSS L66 Hydreigon (DARK/DRAGON): 44,423,156,57
        BOSS L65 Gyarados (WATER/FLYING): 401,442,349,406
        BOSS L64 Camerupt (FIRE/GROUND): 436,430,446,707
        BOSS L70 Mewtwo (PSYCHIC): 93,126,156,58
        BOSS L63 Crabominable (FIGHTING/ICE): 8,263,334,335
        IMP  L6 Pichu (ELECTRIC): 527,574,156,268
        IMP  L15 Glaceon (ICE): 196,352,273,204
        IMP  L27 Salandit (POISON/FIRE): 52,406,269,141
        IMP  L28 Noibat (FLYING/DRAGON): 314,421,92,141
        IMP  L41 Noivern (FLYING/DRAGON): 314,257,269,247
        IMP  L70 Primarina (WATER/FAIRY): 57,574,47,608
        IMP  L67 Muk (POISON/DARK): 562,317,220,612
        IMP  L53 Zoroark (DARK): 492,490,269,53
        IMP  L68 Zoroark (DARK): 400,53,197,343
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 528,473,277,231
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 473,574,204,527
        IMP  L68 Snorlax (NORMAL): 498,276,18,53
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 87,574,3,264
        IMP  L51 Shiinotic (GRASS/FAIRY): 72,605,79,113
        IMP  L20 Poipole (POISON): 474,496,92,182
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 162,371,590,237
        REG  L69 Lapras (WATER/ICE): 420,362,47,287
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 351,473,168,203
        REG  L35 Marowak (FIRE/GHOST): 247,24,488,590
        REG  L5 Yungoos (NORMAL): 371,207,162,237
        REG  L5 Yungoos (NORMAL): 317,216,497,351
        REG  L55 Espeon (PSYCHIC): 94,324,526,384
        REG  L5 Yungoos (NORMAL): 371,259,497,279
        REG  L33 Zubat (POISON/FLYING): 17,141,257,162
        REG  L30 Minior (ROCK/FLYING): 157,428,393,605
        REG  L27 Trumbeak (NORMAL/FLYING): 365,249,168,237
        REG  L62 Persian (NORMAL): 263,492,247,369
        REG  L14 Rattata (DARK/NORMAL): 343,279,179,373
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
