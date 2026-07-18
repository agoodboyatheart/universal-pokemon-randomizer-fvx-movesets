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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        BOSS L45 RHYHORN (GROUND/ROCK): 89,31,164,126
        BOSS L45 NIDOKING (POISON/GROUND): 89,37,164,66
        BOSS L55 HITMONLEE (FIGHTING): 26,36,156,118
        BOSS L12 GEODUDE (ROCK/GROUND): 33,69,156,90
        BOSS L21 STARMIE (WATER/PSYCHIC): 55,161,92,149
        BOSS L24 RAICHU (ELECTRIC): 84,98,92,69
        BOSS L37 KOFFING (POISON): 123,85,92,108
        BOSS L43 WEEZING (POISON): 123,126,156,120
        BOSS L42 RAPIDASH (FIRE): 126,23,115,45
        BOSS L38 VENOMOTH (BUG/POISON): 141,72,50,164
        BOSS L53 CLOYSTER (WATER/ICE): 58,61,164,43
        BOSS L56 LAPRAS (WATER/ICE): 58,85,156,56
        BOSS L55 HAUNTER (GHOST/POISON): 122,87,92,94
        BOSS L56 DRAGONAIR (DRAGON): 61,59,156,85
        BOSS L62 DRAGONITE (DRAGON/FLYING): 126,21,86,85
        IMP  L9 PIDGEY (NORMAL/FLYING): 16,129,115,156
        IMP  L15 ABRA (PSYCHIC): 69,99,115,118
        IMP  L18 PIDGEOTTO (NORMAL/FLYING): 16,98,164,102
        IMP  L18 KADABRA (PSYCHIC): 93,99,156,69
        IMP  L16 RATICATE (NORMAL): 158,55,156,39
        IMP  L25 WARTORTLE (WATER): 55,5,164,115
        IMP  L23 EXEGGCUTE (GRASS/PSYCHIC): 99,140,115,95
        IMP  L35 ALAKAZAM (PSYCHIC): 60,161,50,66
        IMP  L40 VENUSAUR (GRASS/POISON): 76,33,164,77
        IMP  L45 RHYHORN (GROUND/ROCK): 91,157,92,126
        IMP  L45 GYARADOS (WATER/FLYING): 56,58,92,87
        IMP  L47 GYARADOS (WATER/FLYING): 56,34,156,126
        IMP  L61 ARCANINE (FIRE): 53,44,97,46
        IMP  L63 ARCANINE (FIRE): 53,91,156,34
        IMP  L65 CHARIZARD (FIRE/FLYING): 53,34,164,91
        REG  L11 RATTATA (NORMAL): 33,55,164,102
        REG  L9 PIDGEY (NORMAL/FLYING): 129,16,28,18
        REG  L18 MANKEY (FIGHTING): 157,2,43,104
        REG  L19 RATTATA (NORMAL): 33,61,104,92
        REG  L37 VULPIX (FIRE): 53,129,46,39
        REG  L29 WEEZING (POISON): 123,85,92,104
        REG  L31 CLOYSTER (WATER/ICE): 61,129,102,156
        REG  L26 MANKEY (FIGHTING): 66,157,10,118
        REG  L30 HORSEA (WATER): 145,38,156,104
        REG  L29 FEAROW (NORMAL/FLYING): 31,64,45,164
        REG  L70 GYARADOS (WATER/FLYING): 56,58,43,44
        REG  L17 MACHOP (FIGHTING): 2,69,92,156
        REG  L28 EKANS (POISON): 40,157,137,164
        REG  L39 DUGTRIO (GROUND): 89,157,28,102
        REG  L33 HAUNTER (GHOST/POISON): 122,72,109,102
        """);

        EXPECTED.put("Crystal", """
        BOSS L7 PIDGEY (NORMAL/FLYING): 129,189,197,216
        BOSS L16 SCYTHER (BUG/FLYING): 210,211,113,228
        BOSS L31 PILOSWINE (ICE/GROUND): 58,246,197,34
        BOSS L37 DRAGONAIR (DRAGON): 82,53,97,192
        BOSS L41 EXEGGUTOR (GRASS/PSYCHIC): 76,94,115,203
        BOSS L46 MACHAMP (FIGHTING): 238,91,227,197
        BOSS L40 ARIADOS (BUG/POISON): 188,101,50,169
        BOSS L47 DRAGONITE (DRAGON/FLYING): 82,7,182,21
        BOSS L42 OMASTAR (ROCK/WATER): 61,62,114,131
        BOSS L47 STARMIE (WATER/PSYCHIC): 94,61,182,85
        BOSS L41 JUMPLUFF (GRASS/FLYING): 76,33,73,207
        BOSS L33 ARIADOS (BUG/POISON): 188,101,92,91
        BOSS L45 MAGMAR (FIRE): 53,238,182,5
        BOSS L77 BLASTOISE (WATER): 56,89,46,114
        BOSS L58 ARCANINE (FIRE): 53,29,97,242
        IMP  L12 GASTLY (GHOST/POISON): 122,202,182,180
        IMP  L12 GASTLY (GHOST/POISON): 122,202,156,173
        IMP  L20 HAUNTER (GHOST/POISON): 122,168,156,173
        IMP  L20 ZUBAT (POISON/FLYING): 16,211,18,98
        IMP  L32 MEGANIUM (GRASS): 22,246,115,34
        IMP  L28 MAGNEMITE (ELECTRIC/STEEL): 85,182,218,129
        IMP  L35 MAGNETON (ELECTRIC/STEEL): 85,161,174,205
        IMP  L35 HAUNTER (GHOST/POISON): 247,174,149,85
        IMP  L35 HAUNTER (GHOST/POISON): 247,182,192,94
        IMP  L43 GENGAR (GHOST/POISON): 247,9,109,168
        IMP  L43 ALAKAZAM (PSYCHIC): 60,7,227,112
        IMP  L43 ALAKAZAM (PSYCHIC): 60,168,227,91
        IMP  L46 ALAKAZAM (PSYCHIC): 94,247,115,113
        IMP  L50 TYPHLOSION (FIRE): 126,89,174,237
        IMP  L50 FERALIGATR (WATER): 56,157,240,242
        REG  L10 CHIKORITA (GRASS): 75,246,73,45
        REG  L20 QUAGSIRE (WATER/GROUND): 91,205,218,213
        REG  L31 PIDGEOTTO (NORMAL/FLYING): 168,193,182,98
        REG  L25 NINETALES (FIRE): 52,98,91,109
        REG  L31 RHYDON (GROUND/ROCK): 89,58,23,192
        REG  L18 GROWLITHE (FIRE): 52,91,241,46
        REG  L23 GOLDEEN (WATER): 30,64,174,240
        REG  L28 TENTACOOL (WATER/POISON): 61,62,240,51
        REG  L28 POLIWHIRL (WATER): 61,189,182,8
        REG  L32 ONIX (ROCK/GROUND): 88,249,237,89
        REG  L6 VOLTORB (ELECTRIC): 129,205,203,33
        REG  L31 FURRET (NORMAL): 129,247,228,8
        REG  L42 GOLDUCK (WATER): 196,60,189,50
        REG  L23 PIKACHU (ELECTRIC): 9,129,111,86
        REG  L25 ELECTRODE (ELECTRIC): 33,205,218,92
        """);

        EXPECTED.put("Emerald", """
        BOSS L41 MIGHTYENA (DARK): 242,263,281,247
        BOSS L53 WALREIN (ICE/WATER): 59,263,227,317
        BOSS L26 CAMERUPT (FIRE/GROUND): 91,52,92,157
        BOSS L43 SEALEO (ICE/WATER): 58,352,46,290
        BOSS L44 CAMERUPT (FIRE/GROUND): 89,263,104,157
        BOSS L50 KABUTOPS (ROCK/WATER): 61,25,201,69
        BOSS L46 HITMONCHAN (FIGHTING): 136,89,197,102
        BOSS L50 MANECTRIC (ELECTRIC): 87,290,156,207
        BOSS L46 GROWLITHE (FIRE): 126,332,92,91
        BOSS L45 KANGASKHAN (NORMAL): 38,91,50,317
        BOSS L45 ALTARIA (DRAGON/FLYING): 337,53,46,263
        BOSS L58 SKARMORY (STEEL/FLYING): 143,38,18,213
        BOSS L60 LUNATONE (ROCK/PSYCHIC): 94,205,106,129
        BOSS L56 LAPRAS (WATER/ICE): 59,85,164,352
        BOSS L78 METAGROSS (STEEL/PSYCHIC): 309,89,92,218
        IMP  L34 MIGHTYENA (DARK): 44,305,92,231
        IMP  L40 GOLBAT (POISON/FLYING): 17,185,174,48
        IMP  L20 GROVYLE (GRASS): 71,225,317,98
        IMP  L29 LOMBRE (WATER/GRASS): 75,154,240,235
        IMP  L18 SLUGMA (FIRE): 52,290,281,151
        IMP  L29 PELIPPER (WATER/FLYING): 352,129,240,211
        IMP  L31 MARSHTOMP (WATER/GROUND): 89,157,290,55
        IMP  L22 ZUBAT (POISON/FLYING): 17,247,164,237
        IMP  L47 ROSELIA (GRASS/POISON): 76,38,164,275
        IMP  L53 ALTARIA (DRAGON/FLYING): 337,53,97,332
        IMP  L53 MAGNETON (ELECTRIC/STEEL): 87,63,115,104
        IMP  L34 COMBUSKEN (FIRE/FIGHTING): 53,9,164,265
        IMP  L34 GROVYLE (GRASS): 348,9,97,1
        IMP  L34 MARSHTOMP (WATER/GROUND): 89,5,182,317
        IMP  L15 MUDKIP (WATER): 55,23,164,111
        REG  L21 GEODUDE (ROCK/GROUND): 88,280,335,263
        REG  L26 MARILL (WATER): 61,8,216,21
        REG  L26 MIGHTYENA (DARK): 44,310,316,46
        REG  L33 MACHOP (FIGHTING): 280,89,34,104
        REG  L41 SOLROCK (ROCK/PSYCHIC): 88,247,149,216
        REG  L35 PLUSLE (ELECTRIC): 9,69,223,45
        REG  L14 MAGNEMITE (ELECTRIC/STEEL): 84,218,115,129
        REG  L30 KOFFING (POISON): 123,247,220,92
        REG  L6 SEEDOT (GRASS): 331,206,106,205
        REG  L11 MARILL (WATER): 352,189,39,207
        REG  L26 LOMBRE (WATER/GRASS): 75,252,203,175
        REG  L29 XATU (PSYCHIC/FLYING): 94,129,101,287
        REG  L29 ZUBAT (POISON/FLYING): 16,98,269,259
        REG  L34 PELIPPER (WATER/FLYING): 55,168,45,332
        REG  L5 KYOGRE (WATER): 352,317,216,86
        """);

        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE (ROCK/GROUND): 88,446,111,189
        BOSS L49 SCIZOR (BUG/STEEL): 211,332,113,168
        BOSS L52 HIPPOWDON (GROUND): 89,242,446,36
        BOSS L57 MAGMORTAR (FIRE): 394,85,164,103
        BOSS L58 SPIRITOMB (GHOST/DARK): 247,352,261,289
        BOSS L20 CHERRIM (GRASS): 331,164,219,290
        BOSS L29 MACHOKE (FIGHTING): 27,9,164,53
        BOSS L42 ABOMASNOW (GRASS/ICE): 452,23,73,157
        BOSS L44 SNEASEL (DARK/ICE): 58,231,258,154
        BOSS L48 WEAVILE (DARK/ICE): 58,404,182,232
        BOSS L66 WHISCASH (WATER/GROUND): 56,414,240,218
        BOSS L69 RAPIDASH (FIRE): 394,31,97,224
        BOSS L72 ALAKAZAM (PSYCHIC): 94,411,269,289
        BOSS L78 GARCHOMP (DRAGON/GROUND): 414,163,92,53
        BOSS L58 MAGMORTAR (FIRE): 315,411,269,168
        IMP  L7 STARLY (NORMAL/FLYING): 365,466,228,239
        IMP  L44 BRONZOR (STEEL/PSYCHIC): 89,164,317,247
        IMP  L27 GROTLE (GRASS): 412,44,235,447
        IMP  L34 STARAVIA (NORMAL/FLYING): 290,332,18,211
        IMP  L36 STARAPTOR (NORMAL/FLYING): 365,211,355,164
        IMP  L48 HERACROSS (BUG/FIGHTING): 370,157,92,14
        IMP  L47 RAPIDASH (FIRE): 394,33,95,241
        IMP  L42 STARAPTOR (NORMAL/FLYING): 38,370,355,369
        IMP  L25 KADABRA (PSYCHIC): 428,451,409,7
        IMP  L27 GROTLE (GRASS): 75,37,133,321
        IMP  L61 HERACROSS (BUG/FIGHTING): 224,282,182,31
        IMP  L69 RAPIDASH (FIRE): 126,231,156,204
        IMP  L73 SNORLAX (NORMAL): 416,94,204,276
        IMP  L83 SNORLAX (NORMAL): 38,89,281,196
        IMP  L60 SKUNTANK (POISON/DARK): 188,168,46,126
        REG  L5 STARLY (NORMAL/FLYING): 365,228,310,164
        REG  L29 ZUBAT (POISON/FLYING): 365,428,290,445
        REG  L36 SWINUB (ICE/GROUND): 91,157,213,36
        REG  L6 GEODUDE (ROCK/GROUND): 205,33,360,335
        REG  L21 CARNIVINE (GRASS): 331,189,275,79
        REG  L21 DRIFLOON (GHOST/FLYING): 314,205,116,129
        REG  L36 MURKROW (DARK/FLYING): 314,310,372,211
        REG  L39 MURKROW (DARK/FLYING): 17,247,18,109
        REG  L58 PELIPPER (WATER/FLYING): 16,55,240,54
        REG  L32 EEVEE (NORMAL): 33,91,203,28
        REG  L48 SEAKING (WATER): 127,398,203,114
        REG  L42 GOLBAT (POISON/FLYING): 17,257,228,290
        REG  L23 BUIZEL (WATER): 291,317,49,3
        REG  L42 MAGNETON (ELECTRIC/STEEL): 443,451,334,393
        REG  L56 EMPOLEON (WATER/STEEL): 250,324,157,89
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom (DRAGON/ELECTRIC): 559,525,355,247
        BOSS L56 Sigilyph (PSYCHIC/FLYING): 143,430,322,119
        BOSS L72 Lucario (FIGHTING/STEEL): 231,317,468,237
        BOSS L28 Flaaffy (ELECTRIC): 85,324,218,34
        BOSS L48 Haxorus (DRAGON): 200,276,182,400
        BOSS L50 Cofagrigus (GHOST): 247,496,220,412
        BOSS L67 Simipour (WATER): 401,371,92,154
        BOSS L76 Clefable (NORMAL): 63,247,86,309
        BOSS L28 Emolga (ELECTRIC/FLYING): 528,496,355,39
        BOSS L49 Carracosta (WATER/ROCK): 444,91,504,34
        BOSS L56 Lucario (FIGHTING/STEEL): 136,91,46,218
        BOSS L73 Golurk (GROUND/GHOST): 89,359,1,94
        BOSS L73 Gallade (PSYCHIC/FIGHTING): 370,348,50,530
        BOSS L75 Arcanine (FIRE): 126,24,46,406
        BOSS L75 Glaceon (ICE): 58,485,247,304
        IMP  L8 Tepig (FIRE): 52,249,281,343
        IMP  L48 Cryogonal (ICE): 59,324,156,54
        IMP  L23 Pansage (GRASS): 412,91,73,421
        IMP  L31 Tranquill (NORMAL/FLYING): 365,98,234,164
        IMP  L39 Unfezant (NORMAL/FLYING): 365,211,234,381
        IMP  L46 Cryogonal (ICE): 58,430,109,163
        IMP  L55 Unfezant (NORMAL/FLYING): 143,263,182,45
        IMP  L55 Simisear (FIRE): 126,421,133,512
        IMP  L62 Unfezant (NORMAL/FLYING): 416,403,197,516
        IMP  L62 Flygon (GROUND/DRAGON): 89,317,9,202
        IMP  L65 Unfezant (NORMAL/FLYING): 416,211,297,403
        IMP  L65 Eelektross (ELECTRIC): 528,340,156,489
        IMP  L41 Simisear (FIRE): 53,276,261,67
        IMP  L48 Unfezant (NORMAL/FLYING): 143,98,526,234
        IMP  L74 Klinklang (STEEL): 430,249,475,521
        REG  L26 Blitzle (ELECTRIC): 351,324,24,213
        REG  L63 Hitmonlee (FIGHTING): 280,418,179,96
        REG  L63 Hitmonchan (FIGHTING): 136,444,216,9
        REG  L56 Unfezant (NORMAL/FLYING): 365,211,257,273
        REG  L47 Boldore (ROCK): 408,523,446,104
        REG  L45 Swinub (ICE/GROUND): 556,89,203,246
        REG  L32 Scolipede (BUG/POISON): 40,404,89,191
        REG  L65 Hitmontop (FIGHTING): 27,228,213,91
        REG  L52 Amoonguss (GRASS/POISON): 412,185,34,148
        REG  L64 Archeops (ROCK/FLYING): 365,369,98,225
        REG  L54 Metang (STEEL/PSYCHIC): 309,249,201,8
        REG  L60 Wooper (WATER/GROUND): 91,58,491,401
        REG  L67 Emboar (FIRE/FIGHTING): 394,317,535,36
        REG  L47 Krookodile (GROUND/DARK): 371,337,421,184
        REG  L25 Litwick (GHOST/FIRE): 52,123,164,286
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena (DARK): 492,305,46,423
        BOSS L41 Weezing (POISON): 188,168,390,247
        BOSS L5 Zigzagoon (NORMAL): 33,228,92,590
        BOSS L51 Dusclops (GHOST): 325,264,269,58
        BOSS L52 Froslass (ICE/GHOST): 247,44,219,577
        BOSS L57 Claydol (GROUND/PSYCHIC): 473,414,201,605
        BOSS L14 Machop (FIGHTING): 27,371,339,418
        BOSS L28 Slaking (NORMAL): 34,400,7,196
        BOSS L44 Whiscash (WATER/GROUND): 414,196,164,209
        BOSS L70 Sharpedo (WATER/DARK): 56,38,184,399
        BOSS L71 Dusknoir (GHOST): 325,89,114,164
        BOSS L73 Altaria (DRAGON/FLYING): 200,523,182,64
        BOSS L77 Carbink (ROCK/FAIRY): 585,88,92,94
        BOSS L57 Cradily (ROCK/GRASS): 412,482,220,89
        BOSS L57 Milotic (WATER): 56,225,95,109
        IMP  L18 Slugma (FIRE): 52,496,113,174
        IMP  L31 Wailmer (WATER): 503,58,340,263
        IMP  L18 Wailmer (WATER): 250,310,46,523
        IMP  L31 Shroomish (GRASS): 402,237,219,358
        IMP  L37 Swellow (NORMAL/FLYING): 413,98,366,257
        IMP  L37 Wailord (WATER): 323,34,133,240
        IMP  L46 Delcatty (NORMAL): 38,247,215,428
        IMP  L24 Shroomish (GRASS): 331,474,164,313
        IMP  L24 Slugma (FIRE): 510,317,261,237
        IMP  L32 Sharpedo (WATER/DARK): 503,305,156,590
        IMP  L55 Camerupt (FIRE/GROUND): 315,23,281,495
        IMP  L50 Blaziken (FIRE/FIGHTING): 315,497,297,413
        IMP  L50 Sceptile (GRASS): 76,523,320,235
        IMP  L64 Altaria (DRAGON/FLYING): 143,297,257,237
        IMP  L81 Gallade (PSYCHIC/FIGHTING): 485,210,50,207
        REG  L4 Zigzagoon (NORMAL): 33,351,493,164
        REG  L25 Slugma (FIRE): 52,246,216,220
        REG  L39 Claydol (GROUND/PSYCHIC): 60,246,106,529
        REG  L36 Golbat (POISON/FLYING): 314,162,98,355
        REG  L34 Golbat (POISON/FLYING): 16,211,141,305
        REG  L33 Roselia (GRASS/POISON): 71,605,42,235
        REG  L43 Solrock (ROCK/PSYCHIC): 444,442,182,373
        REG  L49 Jellicent (WATER/GHOST): 466,412,182,196
        REG  L37 Skarmory (STEEL/FLYING): 64,228,201,334
        REG  L41 Clamperl (WATER): 250,34,287,48
        REG  L39 Tentacruel (WATER/POISON): 188,35,378,112
        REG  L48 Honchkrow (DARK/FLYING): 65,371,297,195
        REG  L53 Flygon (GROUND/DRAGON): 89,9,525,405
        REG  L23 Grimer (POISON): 398,290,247,168
        REG  L51 Mightyena (DARK): 242,510,164,247
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop (FIGHTING): 2,479,227,523
        BOSS L47 Bewear (NORMAL/FIGHTING): 37,395,182,421
        BOSS L56 Probopass (ROCK/STEEL): 430,521,277,605
        BOSS L41 Golisopod (BUG/WATER): 660,280,334,163
        BOSS L66 Froslass (ICE/GHOST): 247,358,258,324
        BOSS L66 Mandibuzz (DARK/FLYING): 413,257,366,198
        BOSS L57 Dugtrio (GROUND/STEEL): 414,228,262,179
        BOSS L52 Sableye (DARK/GHOST): 492,94,261,332
        BOSS L65 Crobat (POISON/FLYING): 413,404,355,48
        BOSS L64 Masquerain (BUG/FLYING): 324,466,18,218
        BOSS L66 Hydreigon (DARK/DRAGON): 407,451,432,103
        BOSS L65 Gyarados (WATER/FLYING): 127,442,349,53
        BOSS L64 Camerupt (FIRE/GROUND): 284,707,46,241
        BOSS L70 Mewtwo (PSYCHIC): 94,58,156,157
        BOSS L63 Crabominable (FIGHTING/ICE): 370,152,258,524
        IMP  L6 Pichu (ELECTRIC): 351,574,227,604
        IMP  L15 Glaceon (ICE): 524,343,258,673
        IMP  L27 Salandit (POISON/FIRE): 53,237,261,82
        IMP  L28 Noibat (FLYING/DRAGON): 314,257,355,421
        IMP  L41 Noivern (FLYING/DRAGON): 542,352,236,528
        IMP  L70 Primarina (WATER/FAIRY): 605,664,47,496
        IMP  L67 Muk (POISON/DARK): 562,8,50,9
        IMP  L53 Zoroark (DARK): 539,421,97,326
        IMP  L68 Zoroark (DARK): 539,490,97,313
        IMP  L42 Raichu (ELECTRIC/PSYCHIC): 528,497,156,203
        IMP  L43 Raichu (ELECTRIC/PSYCHIC): 528,574,156,263
        IMP  L68 Snorlax (NORMAL): 38,157,182,57
        IMP  L59 Raichu (ELECTRIC/PSYCHIC): 435,343,113,478
        IMP  L51 Shiinotic (GRASS/FAIRY): 585,188,74,278
        IMP  L20 Poipole (POISON): 51,263,204,406
        REG  L6 Yungoos (NORMAL): 33,371,279,317
        REG  L16 Magikarp (WATER): 150,33,0,0
        REG  L5 Yungoos (NORMAL): 168,207,259,283
        REG  L69 Lapras (WATER/ICE): 56,420,304,240
        REG  L55 Raichu (ELECTRIC/PSYCHIC): 351,473,168,381
        REG  L35 Marowak (FIRE/GHOST): 708,172,496,45
        REG  L5 Yungoos (NORMAL): 317,218,496,351
        REG  L5 Yungoos (NORMAL): 168,216,162,237
        REG  L55 Espeon (PSYCHIC): 473,324,36,281
        REG  L5 Yungoos (NORMAL): 279,104,283,162
        REG  L33 Zubat (POISON/FLYING): 17,305,496,269
        REG  L30 Minior (ROCK/FLYING): 444,523,477,393
        REG  L27 Trumbeak (NORMAL/FLYING): 65,350,103,488
        REG  L62 Persian (NORMAL): 129,583,269,441
        REG  L14 Rattata (DARK/NORMAL): 44,162,98,207
        """);
    }

    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void betterMovesetsGoldenMaster(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        String actual = canonicalBlock(romHandler, gameName);

        if (Boolean.getBoolean("golden.record")) {
            printPasteReadyBlock(gameName, actual);
            return;
        }
        String expected = EXPECTED.get(gameName);
        if (expected == null) {
            printPasteReadyBlock(gameName, actual);
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
            return IntStream.range(0, size).toArray();
        }
        int[] idx = new int[sample];
        for (int i = 0; i < sample; i++) {
            idx[i] = (int) Math.round((double) i * (size - 1) / (sample - 1));
        }
        return idx;
    }

    private static String joinMoves(int[] moves) {
        return Arrays.stream(moves).mapToObj(Integer::toString).collect(Collectors.joining(","));
    }

    /** Prints the paste-ready {@code EXPECTED.put(...)} block for the re-bless workflow (see class doc). */
    private static void printPasteReadyBlock(String gameName, String actual) {
        System.out.println("\nEXPECTED.put(\"" + gameName + "\", \"\"\"\n" + actual + "\n\"\"\");");
    }

    private static String typeStr(Species pk) {
        Type t2 = pk.getSecondaryType(false);
        return pk.getPrimaryType(false) + (t2 == null ? "" : "/" + t2);
    }
}
