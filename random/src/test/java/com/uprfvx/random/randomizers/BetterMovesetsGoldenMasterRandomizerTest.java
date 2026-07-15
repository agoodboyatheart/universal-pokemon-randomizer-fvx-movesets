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
        BOSS L45 RHYHORN (GROUND/ROCK): 91,38,156,117
        BOSS L45 NIDOKING (POISON/GROUND): 89,59,164,157
        BOSS L55 HITMONLEE (FIGHTING): 26,38,156,96
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,164,68
        BOSS L21 STARMIE (WATER/PSYCHIC): 61,161,115,117
        BOSS L24 RAICHU (ELECTRIC): 84,69,86,102
        BOSS L37 KOFFING (POISON): 124,87,92,117
        BOSS L43 WEEZING (POISON): 124,85,164,108
        BOSS L42 RAPIDASH (FIRE): 126,130,164,45
        BOSS L38 VENOMOTH (BUG/POISON): 141,76,79,50
        BOSS L53 CLOYSTER (WATER/ICE): 62,153,92,61
        BOSS L56 LAPRAS (WATER/ICE): 56,85,92,156
        BOSS L55 HAUNTER (GHOST/POISON): 101,87,164,117
        BOSS L56 DRAGONAIR (DRAGON): 126,61,156,102
        BOSS L62 DRAGONITE (DRAGON/FLYING): 63,85,92,58
        IMP  L9 PIDGEY (NORMAL/FLYING): 129,164,28,18
        IMP  L15 ABRA (PSYCHIC): 66,161,92,117
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 129,13,164,28
        IMP  L18 KADABRA (PSYCHIC): 93,161,86,164
        IMP  L16 RATICATE (NORMAL): 129,61,164,117
        IMP  L25 WARTORTLE (WATER): 61,69,156,68
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 140,99,156,95
        IMP  L35 ALAKAZAM (PSYCHIC): 94,63,86,66
        IMP  L40 VENUSAUR (GRASS/POISON): 76,63,14,77
        IMP  L45 RHYHORN (GROUND/ROCK): 89,85,92,157
        IMP  L45 GYARADOS (WATER/FLYING): 56,126,156,92
        IMP  L47 GYARADOS (WATER/FLYING): 61,58,115,117
        IMP  L61 ARCANINE (FIRE): 53,63,97,164
        IMP  L63 ARCANINE (FIRE): 53,91,46,97
        IMP  L65 CHARIZARD (FIRE/FLYING): 126,69,156,14
        REG  L11 RATTATA (NORMAL): 129,55,156,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,117,18,28
        REG  L18 MANKEY (FIGHTING): 69,129,43,117
        REG  L19 RATTATA (NORMAL): 129,61,104,102
        REG  L37 VULPIX (FIRE): 53,91,109,46
        REG  L29 WEEZING (POISON): 124,33,102,156
        REG  L31 CLOYSTER (WATER/ICE): 62,153,164,92
        REG  L26 MANKEY (FIGHTING): 69,129,156,92
        REG  L30 HORSEA (WATER): 61,59,108,102
        REG  L29 FEAROW (NORMAL/FLYING): 129,65,119,117
        REG  L70 GYARADOS (WATER/FLYING): 61,58,43,117
        REG  L17 MACHOP (FIGHTING): 69,2,90,102
        REG  L28 EKANS (POISON): 40,44,137,43
        REG  L39 DUGTRIO (GROUND): 89,38,157,156
        REG  L33 HAUNTER (GHOST/POISON): 101,94,87,102
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,185,156,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,29,113,249
        BOSS L31 PILOSWINE (ICE/GROUND): 89,34,207,59
        BOSS L37 DRAGONAIR (DRAGON): 82,53,86,59
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 94,202,73,188
        BOSS L46 MACHAMP (FIGHTING): 238,126,113,213
        BOSS L40 ARIADOS (BUG/POISON): 188,101,50,154
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,223,21,85
        BOSS L42 OMASTAR (ROCK/WATER): 61,59,114,29
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,61,105,109
        BOSS L41 JUMPLUFF (GRASS/FLYING): 202,63,92,230
        BOSS L33 ARIADOS (BUG/POISON): 188,101,92,50
        BOSS L45 MAGMAR (FIRE): 126,231,182,109
        BOSS L77 BLASTOISE (WATER): 56,59,114,92
        BOSS L58 ARCANINE (FIRE): 126,242,174,218
        IMP  L12 GASTLY (GHOST/POISON): 122,202,92,216
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,95
        IMP  L20 HAUNTER (GHOST/POISON): 247,202,174,156
        IMP  L20 ZUBAT (POISON/FLYING): 16,185,18,213
        IMP  L32 MEGANIUM (GRASS): 202,89,73,77
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 84,129,86,199
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 87,161,86,85
        IMP  L35 HAUNTER (GHOST/POISON): 247,85,195,180
        IMP  L35 HAUNTER (GHOST/POISON): 247,87,114,212
        IMP  L43 GENGAR (GHOST/POISON): 247,7,174,109
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,227,9
        IMP  L43 ALAKAZAM (PSYCHIC): 94,7,113,134
        IMP  L46 ALAKAZAM (PSYCHIC): 94,7,50,134
        IMP  L50 TYPHLOSION (FIRE): 126,9,174,182
        IMP  L50 FERALIGATR (WATER): 56,89,203,58
        REG  L10 CHIKORITA (GRASS): 202,246,230,189
        REG  L20 QUAGSIRE (WATER/GROUND): 91,246,218,39
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 98,228,182,28
        REG  L25 NINETALES (FIRE): 52,185,175,207
        REG  L31 RHYDON (GROUND/ROCK): 89,87,218,157
        REG  L18 GROWLITHE (FIRE): 52,225,216,156
        REG  L23 GOLDEEN (WATER): 60,30,48,203
        REG  L28 TENTACOOL (WATER/POISON): 61,202,48,62
        REG  L28 POLIWHIRL (WATER): 61,196,170,174
        REG  L32 ONIX (ROCK/GROUND): 89,231,218,207
        REG  L6 VOLTORB (ELECTRIC): 129,205,216,237
        REG  L31 FURRET (NORMAL): 98,231,203,228
        REG  L42 GOLDUCK (WATER): 94,58,193,182
        REG  L23 PIKACHU (ELECTRIC): 9,98,156,117
        REG  L25 ELECTRODE (ELECTRIC): 29,205,103,156
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,231,237,38
        BOSS L53 WALREIN (ICE/WATER): 62,157,281,45
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,157,46,290
        BOSS L43 SEALEO (ICE/WATER): 59,157,46,216
        BOSS L44 CAMERUPT (FIRE/GROUND): 315,157,156,133
        BOSS L50 KABUTOPS (ROCK/WATER): 157,341,182,164
        BOSS L46 HITMONCHAN (FIGHTING): 136,89,97,118
        BOSS L50 MANECTRIC (ELECTRIC): 87,242,182,213
        BOSS L46 GROWLITHE (FIRE): 126,231,164,43
        BOSS L45 KANGASKHAN (NORMAL): 38,247,92,46
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,53,114,213
        BOSS L58 SKARMORY (STEEL/FLYING): 65,211,46,43
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,247,115,58
        BOSS L56 LAPRAS (WATER/ICE): 56,85,195,237
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,9,97,216
        IMP  L34 MIGHTYENA (DARK): 44,63,164,182
        IMP  L40 GOLBAT (POISON/FLYING): 17,98,18,188
        IMP  L20 GROVYLE (GRASS): 202,9,102,242
        IMP  L29 LOMBRE (WATER/GRASS): 202,7,252,310
        IMP  L18 SLUGMA (FIRE): 52,157,151,189
        IMP  L29 PELIPPER (WATER/FLYING): 352,351,182,332
        IMP  L31 MARSHTOMP (WATER/GROUND): 341,69,174,240
        IMP  L22 ZUBAT (POISON/FLYING): 17,211,18,202
        IMP  L47 ROSELIA (GRASS/POISON): 202,188,235,74
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,195,97
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 85,38,115,63
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 315,280,14,68
        IMP  L34 GROVYLE (GRASS): 348,242,156,283
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,69,243,25
        IMP  L15 MUDKIP (WATER): 352,290,156,91
        REG  L21 GEODUDE (ROCK/GROUND): 91,290,335,213
        REG  L26 MARILL (WATER): 61,8,5,203
        REG  L26 MIGHTYENA (DARK): 44,91,289,92
        REG  L33 MACHOP (FIGHTING): 264,89,157,164
        REG  L41 SOLROCK (ROCK/PSYCHIC): 157,38,218,207
        REG  L35 PLUSLE (ELECTRIC): 85,98,313,182
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 351,129,207,203
        REG  L30 KOFFING (POISON): 188,126,182,108
        REG  L6 SEEDOT (GRASS): 202,98,104,14
        REG  L11 MARILL (WATER): 352,69,287,240
        REG  L26 LOMBRE (WATER/GRASS): 202,196,189,203
        REG  L29 XATU (PSYCHIC/FLYING): 65,202,347,185
        REG  L29 ZUBAT (POISON/FLYING): 17,211,174,156
        REG  L34 PELIPPER (WATER/FLYING): 17,59,54,97
        REG  L5 KYOGRE (WATER): 352,351,237,244
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,33,397,111
        BOSS L49 SCIZOR (BUG/STEEL): 404,400,97,276
        BOSS L52 HIPPOWDON (GROUND): 89,242,303,424
        BOSS L57 MAGMORTAR (FIRE): 53,263,269,216
        BOSS L58 SPIRITOMB (GHOST/DARK): 425,290,220,148
        BOSS L20 CHERRIM (GRASS): 412,290,312,14
        BOSS L29 MACHOKE (FIGHTING): 280,7,339,104
        BOSS L42 ABOMASNOW (GRASS/ICE): 196,202,113,200
        BOSS L44 SNEASEL (DARK/ICE): 420,252,14,399
        BOSS L48 WEAVILE (DARK/ICE): 400,91,97,14
        BOSS L66 WHISCASH (WATER/GROUND): 401,428,321,416
        BOSS L69 RAPIDASH (FIRE): 394,398,92,213
        BOSS L72 ALAKAZAM (PSYCHIC): 94,324,347,112
        BOSS L78 GARCHOMP (DRAGON/GROUND): 89,424,164,28
        BOSS L58 MAGMORTAR (FIRE): 257,411,92,270
        IMP  L7 STARLY (NORMAL/FLYING): 283,228,466,239
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 430,263,347,148
        IMP  L27 GROTLE (GRASS): 402,44,446,321
        IMP  L34 STARAVIA (NORMAL/FLYING): 38,228,156,182
        IMP  L36 STARAPTOR (NORMAL/FLYING): 283,211,18,182
        IMP  L48 HERACROSS (BUG/FIGHTING): 280,157,156,218
        IMP  L47 RAPIDASH (FIRE): 53,98,340,224
        IMP  L42 STARAPTOR (NORMAL/FLYING): 98,257,182,413
        IMP  L25 KADABRA (PSYCHIC): 60,324,50,289
        IMP  L27 GROTLE (GRASS): 402,44,446,388
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,89,182,334
        IMP  L69 RAPIDASH (FIRE): 394,224,164,39
        IMP  L73 SNORLAX (NORMAL): 387,242,281,442
        IMP  L83 SNORLAX (NORMAL): 387,402,174,278
        IMP  L60 SKUNTANK (POISON/DARK): 398,389,92,182
        REG  L5 STARLY (NORMAL/FLYING): 332,228,310,31
        REG  L29 ZUBAT (POISON/FLYING): 17,211,109,289
        REG  L36 SWINUB (ICE/GROUND): 420,38,115,157
        REG  L6 GEODUDE (ROCK/GROUND): 246,92,216,189
        REG  L21 CARNIVINE (GRASS): 402,44,73,14
        REG  L21 DRIFLOON (GHOST/FLYING): 314,318,50,244
        REG  L36 MURKROW (DARK/FLYING): 399,94,216,355
        REG  L39 MURKROW (DARK/FLYING): 399,257,103,109
        REG  L58 PELIPPER (WATER/FLYING): 56,58,48,355
        REG  L32 EEVEE (NORMAL): 98,231,175,281
        REG  L48 SEAKING (WATER): 127,398,48,213
        REG  L42 GOLBAT (POISON/FLYING): 403,428,211,212
        REG  L23 BUIZEL (WATER): 291,29,317,280
        REG  L42 MAGNETON (ELECTRIC/STEEL): 430,324,92,161
        REG  L56 EMPOLEON (WATER/STEEL): 453,59,334,65
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,157,468,366
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 403,412,355,148
        BOSS L72 Lucario (FIGHTING/STEEL): 370,399,14,232
        BOSS L28 Flaaffy (ELECTRIC): 451,7,215,260
        BOSS L48 Haxorus (DRAGON): 200,276,349,447
        BOSS L50 Cofagrigus (GHOST): 466,94,347,262
        BOSS L67 Simipour (WATER): 503,231,182,468
        BOSS L76 Clefable (NORMAL): 283,7,236,216
        BOSS L28 Emolga (ELECTRIC/FLYING): 209,369,366,98
        BOSS L49 Carracosta (WATER/ROCK): 157,276,446,401
        BOSS L56 Lucario (FIGHTING/STEEL): 232,136,197,530
        BOSS L73 Golurk (GROUND/GHOST): 325,359,446,9
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 428,404,164,220
        BOSS L75 Arcanine (FIRE): 394,245,234,316
        BOSS L75 Glaceon (ICE): 58,63,174,273
        IMP  L8 Tepig (FIRE): 488,496,261,249
        IMP  L48 Cryogonal (ICE): 196,324,151,430
        IMP  L23 Pansage (GRASS): 202,512,73,417
        IMP  L31 Tranquill (NORMAL/FLYING): 98,211,182,369
        IMP  L39 Unfezant (NORMAL/FLYING): 143,211,355,257
        IMP  L46 Cryogonal (ICE): 58,430,92,163
        IMP  L55 Unfezant (NORMAL/FLYING): 143,211,273,269
        IMP  L55 Simisear (FIRE): 53,411,269,272
        IMP  L62 Unfezant (NORMAL/FLYING): 98,211,197,92
        IMP  L62 Flygon (GROUND/DRAGON): 200,276,366,116
        IMP  L65 Unfezant (NORMAL/FLYING): 98,211,273,381
        IMP  L65 Eelektross (ELECTRIC): 528,53,46,156
        IMP  L41 Simisear (FIRE): 126,512,261,283
        IMP  L48 Unfezant (NORMAL/FLYING): 98,211,269,516
        IMP  L74 Klinklang (STEEL): 544,528,199,263
        REG  L26 Blitzle (ELECTRIC): 351,228,24,496
        REG  L63 Hitmonlee (FIGHTING): 280,398,170,104
        REG  L63 Hitmonchan (FIGHTING): 264,418,228,157
        REG  L56 Unfezant (NORMAL/FLYING): 263,369,234,273
        REG  L47 Boldore (ROCK): 408,263,484,199
        REG  L45 Swinub (ICE/GROUND): 556,283,316,174
        REG  L32 Scolipede (BUG/POISON): 224,401,276,226
        REG  L65 Hitmontop (FIGHTING): 370,228,283,193
        REG  L52 Amoonguss (GRASS/POISON): 402,185,74,111
        REG  L64 Archeops (ROCK/FLYING): 365,211,397,401
        REG  L54 Metang (STEEL/PSYCHIC): 309,280,334,357
        REG  L60 Wooper (WATER/GROUND): 503,8,174,219
        REG  L67 Emboar (FIRE/FIGHTING): 126,89,528,316
        REG  L47 Krookodile (GROUND/DARK): 492,231,116,289
        REG  L25 Litwick (GHOST/FIRE): 481,263,107,151
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 242,231,46,304
        BOSS L41 Weezing (POISON): 499,153,85,53
        BOSS L5 Zigzagoon (NORMAL): 343,351,164,189
        BOSS L51 Dusclops (GHOST): 425,264,92,43
        BOSS L52 Froslass (ICE/GHOST): 420,358,194,117
        BOSS L57 Claydol (GROUND/PSYCHIC): 414,444,397,360
        BOSS L14 Machop (FIGHTING): 490,168,227,418
        BOSS L28 Slaking (NORMAL): 263,359,156,332
        BOSS L44 Whiscash (WATER/GROUND): 503,63,92,207
        BOSS L70 Sharpedo (WATER/DARK): 242,162,97,156
        BOSS L71 Dusknoir (GHOST): 425,8,156,9
        BOSS L73 Altaria (DRAGON/FLYING): 365,585,46,366
        BOSS L77 Carbink (ROCK/FAIRY): 246,585,113,393
        BOSS L57 Cradily (ROCK/GRASS): 412,362,446,590
        BOSS L57 Milotic (WATER): 401,59,105,287
        IMP  L18 Slugma (FIRE): 510,263,281,262
        IMP  L31 Wailmer (WATER): 503,59,156,174
        IMP  L18 Wailmer (WATER): 503,499,164,310
        IMP  L31 Shroomish (GRASS): 202,188,73,290
        IMP  L37 Swellow (NORMAL/FLYING): 283,369,92,403
        IMP  L37 Wailord (WATER): 291,59,92,487
        IMP  L46 Delcatty (NORMAL): 252,358,226,218
        IMP  L24 Shroomish (GRASS): 202,474,164,204
        IMP  L24 Slugma (FIRE): 510,246,281,267
        IMP  L32 Sharpedo (WATER/DARK): 400,398,164,364
        IMP  L55 Camerupt (FIRE/GROUND): 284,89,174,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 53,136,226,157
        IMP  L50 Sceptile (GRASS): 348,512,46,267
        IMP  L64 Altaria (DRAGON/FLYING): 406,257,195,104
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 94,89,14,262
        REG  L4 Zigzagoon (NORMAL): 496,196,493,352
        REG  L25 Slugma (FIRE): 510,499,290,611
        REG  L39 Claydol (GROUND/PSYCHIC): 414,428,290,201
        REG  L36 Golbat (POISON/FLYING): 188,413,18,95
        REG  L34 Golbat (POISON/FLYING): 188,211,369,156
        REG  L33 Roselia (GRASS/POISON): 202,247,235,244
        REG  L43 Solrock (ROCK/PSYCHIC): 444,442,113,334
        REG  L49 Jellicent (WATER/GHOST): 466,399,61,271
        REG  L37 Skarmory (STEEL/FLYING): 232,413,269,385
        REG  L41 Clamperl (WATER): 503,58,109,287
        REG  L39 Tentacruel (WATER/POISON): 503,398,103,390
        REG  L48 Honchkrow (DARK/FLYING): 399,413,259,180
        REG  L53 Flygon (GROUND/DRAGON): 200,444,202,366
        REG  L23 Grimer (POISON): 398,7,139,202
        REG  L51 Mightyena (DARK): 389,583,304,422
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 490,7,182,263
        BOSS L47 Bewear (NORMAL/FIGHTING): 409,89,339,213
        BOSS L56 Probopass (ROCK/STEEL): 430,87,602,414
        BOSS L41 Golisopod (BUG/WATER): 141,389,207,57
        BOSS L66 Froslass (ICE/GHOST): 466,311,269,259
        BOSS L66 Mandibuzz (DARK/FLYING): 399,369,355,260
        BOSS L57 Dugtrio (GROUND/STEEL): 442,157,164,28
        BOSS L52 Sableye (DARK/GHOST): 492,428,220,425
        BOSS L65 Crobat (POISON/FLYING): 440,141,156,103
        BOSS L64 Masquerain (BUG/FLYING): 318,247,114,59
        BOSS L66 Hydreigon (DARK/DRAGON): 200,430,115,324
        BOSS L65 Gyarados (WATER/FLYING): 401,87,92,349
        BOSS L64 Camerupt (FIRE/GROUND): 284,442,174,213
        BOSS L70 Mewtwo (PSYCHIC): 94,411,271,115
        BOSS L63 Crabominable (FIGHTING/ICE): 665,152,92,444
        IMP  L6 Pichu (ELECTRIC): 527,237,227,113
        IMP  L15 Glaceon (ICE): 524,324,281,174
        IMP  L27 Salandit (POISON/FIRE): 488,474,92,289
        IMP  L28 Noibat (FLYING/DRAGON): 314,168,415,48
        IMP  L41 Noivern (FLYING/DRAGON): 314,406,236,207
        IMP  L70 Primarina (WATER/FAIRY): 664,304,115,231
        IMP  L67 Muk (POISON/DARK): 242,280,50,139
        IMP  L53 Zoroark (DARK): 492,326,468,92
        IMP  L68 Zoroark (DARK): 492,369,271,286
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 528,98,86,175
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 87,264,115,477
        IMP  L68 Snorlax (NORMAL): 387,57,111,53
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 528,324,182,590
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,188,86,267
        IMP  L20 Poipole (POISON): 474,496,92,64
        REG  L6 Yungoos (NORMAL): 162,351,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 162,279,218,43
        REG  L69 Lapras (WATER/ICE): 57,442,54,248
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 87,282,278,21
        REG  L35 Marowak (FIRE/GHOST): 708,89,416,103
        REG  L5 Yungoos (NORMAL): 162,279,216,590
        REG  L5 Yungoos (NORMAL): 283,279,269,216
        REG  L55 Espeon (PSYCHIC): 94,129,226,115
        REG  L5 Yungoos (NORMAL): 162,279,104,317
        REG  L33 Zubat (POISON/FLYING): 413,162,247,355
        REG  L30 Minior (ROCK/FLYING): 512,442,120,113
        REG  L27 Trumbeak (NORMAL/FLYING): 65,211,119,432
        REG  L62 Persian (NORMAL): 129,399,373,259
        REG  L14 Rattata (DARK/NORMAL): 98,168,269,196
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
