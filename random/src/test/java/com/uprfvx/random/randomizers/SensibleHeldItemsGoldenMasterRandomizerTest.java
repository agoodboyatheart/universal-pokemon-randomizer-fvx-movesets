package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.*;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Golden-master (characterization) test for {@link RomHandler#getSensibleHeldItemsFor}, Gen 4-7 only
 * (Gen 2/3 have their own simpler, unconsolidated implementations - see the {@code better-held-items}
 * plan). Freezes the EXACT candidate item list for a fixed spread of trainer Pokemon per tier, so any
 * accidental change in behaviour shows up as a precise diff. Mirrors the pattern (and re-bless
 * workflow) used by the {@code better-movesets} branch's {@code BetterMovesetsGoldenMasterRandomizerTest}
 * (not present on this branch) - see {@code -Dgolden.record=true} below for the full workflow.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*SensibleHeldItemsGoldenMaster*" }</pre>
 */
public class SensibleHeldItemsGoldenMasterRandomizerTest {

    private static final int SAMPLE_PER_TIER = 10;
    private static final long SEED = 20260724L;
    private static final String ROMS_PATH = System.getProperty("romsPath");

    static String[][] gamesToVerify() {
        return new String[][]{
                {"Platinum", "Pokemon Platinum"},
                {"Black 2", "Pokemon Black 2"},
                {"Alpha Sapphire", "Pokemon Alpha Sapphire (Europe) (En,Ja,Fr,De,Es,It,Ko) (Rev 2)-decrypted"},
                {"Ultra Sun", "Pokemon Ultra Sun-decrypted"},
        };
    }

    /** Populated by a {@code -Dgolden.record=true} run; see class doc. */
    private static final Map<String, String> EXPECTED = new LinkedHashMap<>();
    static {
        EXPECTED.put("Platinum", """
        BOSS L12 GEODUDE: Babiri Berry,BrightPowder,Choice Band,Chople Berry,Destiny Knot,Ganlon Berry,Hard Stone,Jaboca Berry,Liechi Berry,Mental Herb,Muscle Band,Passho Berry,Passho Berry,Passho Berry,Passho Berry,Pecha Berry,Razor Fang,Rindo Berry,Rindo Berry,Rindo Berry,Rindo Berry,Rock Incense,Shuca Berry,Stone Plate,Yache Berry,Zoom Lens
        BOSS L53 DRAPION: Black Sludge,BrightPowder,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Destiny Knot,Ganlon Berry,Icicle Plate,Insect Plate,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Muscle Band,Muscle Band,Muscle Band,Muscle Band,NeverMeltIce,Pecha Berry,Poison Barb,Razor Fang,Sharp Beak,Shuca Berry,SilverPowder,Sky Plate,Toxic Plate,Zoom Lens
        BOSS L57 MAGMORTAR: BrightPowder,Charcoal,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Choice Specs,Destiny Knot,Flame Plate,Ganlon Berry,Jaboca Berry,Magnet,Meadow Plate,Mental Herb,Miracle Seed,Passho Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Power Herb,Razor Fang,Rose Incense,Shuca Berry,Silk Scarf,Wise Glasses,Wise Glasses,Wise Glasses,Wise Glasses,Zap Plate,Zoom Lens
        BOSS L58 MILOTIC: BrightPowder,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Choice Specs,Destiny Knot,Draco Plate,Dragon Fang,Ganlon Berry,Icicle Plate,Jaboca Berry,Mental Herb,Mind Plate,Mystic Water,NeverMeltIce,Odd Incense,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Razor Fang,Rindo Berry,Sea Incense,Splash Plate,TwistedSpoon,Wacan Berry,Wave Incense,Wise Glasses,Wise Glasses,Wise Glasses,Wise Glasses,Zoom Lens
        BOSS L32 LUCARIO: Big Root,Black Belt,Black Belt,BrightPowder,Choice Band,Choice Band,Choice Band,Choice Band,Chople Berry,Destiny Knot,Earth Plate,Fist Plate,Fist Plate,Ganlon Berry,Iron Plate,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Metal Coat,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Occa Berry,Pecha Berry,Razor Fang,Shuca Berry,Soft Sand,Zoom Lens
        BOSS L50 ELECTIVIRE: BrightPowder,Charcoal,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Destiny Knot,Flame Plate,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Magnet,Mental Herb,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Pecha Berry,Razor Fang,Shuca Berry,Silk Scarf,Silk Scarf,Zap Plate,Zoom Lens
        BOSS L65 SCIZOR: BlackGlasses,BrightPowder,Choice Band,Choice Band,Choice Band,Choice Band,Destiny Knot,Dread Plate,Ganlon Berry,Insect Plate,Iron Plate,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Metal Coat,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Occa Berry,Occa Berry,Occa Berry,Occa Berry,Pecha Berry,Razor Fang,Silk Scarf,SilverPowder,Zoom Lens
        BOSS L71 FLAREON: BrightPowder,Charcoal,Charti Berry,Chilan Berry,Choice Band,Choice Band,Choice Specs,Destiny Knot,Flame Plate,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Mental Herb,Muscle Band,Muscle Band,Passho Berry,Pecha Berry,Petaya Berry,Razor Fang,Shuca Berry,Silk Scarf,Silk Scarf,Wise Glasses,Zoom Lens
        BOSS L74 ROSERADE: Black Sludge,BrightPowder,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Choice Specs,Coba Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Meadow Plate,Mental Herb,Mind Plate,Miracle Seed,Occa Berry,Odd Incense,Payapa Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Poison Barb,Razor Fang,Rose Incense,Spell Tag,Spooky Plate,Toxic Plate,TwistedSpoon,Wise Glasses,Wise Glasses,Wise Glasses,Wise Glasses,Yache Berry,Zoom Lens
        BOSS L58 MAGMORTAR: BrightPowder,Charcoal,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Choice Specs,Destiny Knot,Flame Plate,Ganlon Berry,Jaboca Berry,Magnet,Meadow Plate,Mental Herb,Miracle Seed,Passho Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Power Herb,Razor Fang,Rose Incense,Shuca Berry,Silk Scarf,Wise Glasses,Wise Glasses,Wise Glasses,Wise Glasses,Zap Plate,Zoom Lens
        BOSS-CONSUM L12 GEODUDE: Babiri Berry,Chople Berry,Ganlon Berry,Jaboca Berry,Liechi Berry,Mental Herb,Passho Berry,Passho Berry,Passho Berry,Pecha Berry,Rindo Berry,Rindo Berry,Rindo Berry,Shuca Berry,Yache Berry
        BOSS-CONSUM L53 DRAPION: Chilan Berry,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Pecha Berry,Shuca Berry
        BOSS-CONSUM L57 MAGMORTAR: Charti Berry,Chilan Berry,Ganlon Berry,Jaboca Berry,Mental Herb,Passho Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Shuca Berry
        BOSS-CONSUM L58 MILOTIC: Chilan Berry,Ganlon Berry,Jaboca Berry,Mental Herb,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Rindo Berry,Wacan Berry
        BOSS-CONSUM L32 LUCARIO: Chople Berry,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Occa Berry,Pecha Berry,Shuca Berry
        BOSS-CONSUM L50 ELECTIVIRE: Chilan Berry,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Pecha Berry,Shuca Berry
        BOSS-CONSUM L65 SCIZOR: Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Occa Berry,Occa Berry,Occa Berry,Pecha Berry
        BOSS-CONSUM L71 FLAREON: Charti Berry,Chilan Berry,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Mental Herb,Passho Berry,Pecha Berry,Petaya Berry,Shuca Berry
        BOSS-CONSUM L74 ROSERADE: Chilan Berry,Coba Berry,Ganlon Berry,Jaboca Berry,Mental Herb,Occa Berry,Payapa Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Yache Berry
        BOSS-CONSUM L58 MAGMORTAR: Charti Berry,Chilan Berry,Ganlon Berry,Jaboca Berry,Mental Herb,Passho Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Shuca Berry
        IMP  L7 STARLY: BrightPowder,Charti Berry,Chilan Berry,Choice Band,Destiny Knot,Ganlon Berry,Jaboca Berry,Liechi Berry,Mental Herb,Muscle Band,Pecha Berry,Razor Fang,Silk Scarf,Wacan Berry,Yache Berry,Zoom Lens
        IMP  L42 BRONZOR: BrightPowder,Choice Band,Choice Specs,Choice Specs,Destiny Knot,Ganlon Berry,Iron Plate,Jaboca Berry,Liechi Berry,Mental Herb,Metal Coat,Mind Plate,Muscle Band,Occa Berry,Odd Incense,Pecha Berry,Petaya Berry,Petaya Berry,Razor Fang,Spell Tag,Spooky Plate,TwistedSpoon,Wise Glasses,Wise Glasses,Zoom Lens
        IMP  L32 BUIZEL: BlackGlasses,BrightPowder,Chilan Berry,Choice Band,Choice Band,Choice Band,Destiny Knot,Dread Plate,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Muscle Band,Muscle Band,Muscle Band,Mystic Water,Pecha Berry,Razor Fang,Rindo Berry,Sea Incense,Silk Scarf,Splash Plate,Wacan Berry,Wave Incense,Zoom Lens
        IMP  L35 RAPIDASH: BrightPowder,Charcoal,Charti Berry,Chilan Berry,Choice Band,Choice Band,Choice Specs,Destiny Knot,Flame Plate,Ganlon Berry,Grip Claw,Jaboca Berry,Liechi Berry,Liechi Berry,Mental Herb,Muscle Band,Muscle Band,Passho Berry,Pecha Berry,Petaya Berry,Razor Fang,Shuca Berry,Silk Scarf,Silk Scarf,Wise Glasses,Zoom Lens
        IMP  L44 BRONZOR: BrightPowder,Choice Band,Choice Specs,Destiny Knot,Ganlon Berry,Iron Plate,Jaboca Berry,Liechi Berry,Light Clay,Mental Herb,Metal Coat,Mind Plate,Muscle Band,Occa Berry,Odd Incense,Pecha Berry,Petaya Berry,Razor Fang,TwistedSpoon,Wise Glasses,Zoom Lens
        IMP  L40 RAPIDASH: BrightPowder,Charcoal,Charti Berry,Chilan Berry,Choice Band,Choice Band,Choice Specs,Destiny Knot,Flame Plate,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Mental Herb,Muscle Band,Muscle Band,Passho Berry,Pecha Berry,Petaya Berry,Razor Fang,Shuca Berry,Silk Scarf,Silk Scarf,Wise Glasses,Zoom Lens
        IMP  L61 STARAPTOR: Black Belt,BrightPowder,Charti Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Destiny Knot,Fist Plate,Ganlon Berry,Insect Plate,Iron Plate,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Metal Coat,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Pecha Berry,Razor Fang,Sharp Beak,SilverPowder,Sky Plate,Wacan Berry,Yache Berry,Zoom Lens
        IMP  L71 HERACROSS: Black Belt,BlackGlasses,BrightPowder,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Coba Berry,Coba Berry,Coba Berry,Coba Berry,Destiny Knot,Dread Plate,Fist Plate,Ganlon Berry,Hard Stone,Insect Plate,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Occa Berry,Payapa Berry,Pecha Berry,Razor Fang,Rock Incense,SilverPowder,Stone Plate,Zoom Lens
        IMP  L79 RAPIDASH: BrightPowder,Charcoal,Charti Berry,Chilan Berry,Choice Band,Choice Specs,Destiny Knot,Flame Plate,Ganlon Berry,Heat Rock,Insect Plate,Jaboca Berry,Liechi Berry,Mental Herb,Muscle Band,Passho Berry,Pecha Berry,Petaya Berry,Razor Fang,Shuca Berry,SilverPowder,Wise Glasses,Zoom Lens
        IMP  L60 SKUNTANK: Black Sludge,BlackGlasses,BrightPowder,Charcoal,Chilan Berry,Choice Band,Choice Band,Choice Specs,Destiny Knot,Dread Plate,Flame Plate,Ganlon Berry,Jaboca Berry,Liechi Berry,Liechi Berry,Mental Herb,Muscle Band,Muscle Band,Pecha Berry,Petaya Berry,Poison Barb,Razor Fang,Shuca Berry,Toxic Plate,Wise Glasses,Zoom Lens
        REG  L5 STARLY: BrightPowder,Charti Berry,Chilan Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Mental Herb,Pecha Berry,Razor Fang,Wacan Berry,Yache Berry,Zoom Lens
        REG  L25 SHELLOS: BrightPowder,Chilan Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Mental Herb,Pecha Berry,Razor Fang,Rindo Berry,Wacan Berry,Zoom Lens
        REG  L14 BURMY: BrightPowder,Charti Berry,Chilan Berry,Coba Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Mental Herb,Occa Berry,Pecha Berry,Razor Fang,Zoom Lens
        REG  L5 RATTATA: BrightPowder,Chilan Berry,Chople Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Mental Herb,Pecha Berry,Razor Fang,Zoom Lens
        REG  L51 MANKEY: BrightPowder,Chilan Berry,Coba Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Mental Herb,Payapa Berry,Pecha Berry,Razor Fang,Zoom Lens
        REG  L57 GIBLE: BrightPowder,Chilan Berry,Destiny Knot,Ganlon Berry,Haban Berry,Jaboca Berry,Mental Herb,Pecha Berry,Razor Fang,Yache Berry,Yache Berry,Yache Berry,Yache Berry,Zoom Lens
        REG  L58 DODRIO: BrightPowder,Charti Berry,Chilan Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Mental Herb,Pecha Berry,Razor Fang,Wacan Berry,Yache Berry,Zoom Lens
        REG  L56 PELIPPER: BrightPowder,Charti Berry,Chilan Berry,Destiny Knot,Ganlon Berry,Jaboca Berry,Mental Herb,Pecha Berry,Razor Fang,Wacan Berry,Wacan Berry,Wacan Berry,Wacan Berry,Zoom Lens
        REG  L62 BRELOOM: Black Belt,BrightPowder,Chilan Berry,Choice Band,Choice Band,Choice Band,Coba Berry,Coba Berry,Coba Berry,Coba Berry,Destiny Knot,Fist Plate,Ganlon Berry,Hard Stone,Jaboca Berry,Kebia Berry,Liechi Berry,Liechi Berry,Liechi Berry,Meadow Plate,Mental Herb,Miracle Seed,Muscle Band,Muscle Band,Muscle Band,Occa Berry,Payapa Berry,Pecha Berry,Razor Fang,Rock Incense,Rose Incense,Stone Plate,Yache Berry,Zoom Lens
        REG  L56 EMPOLEON: BrightPowder,Choice Band,Choice Band,Choice Band,Choice Specs,Chople Berry,Destiny Knot,Ganlon Berry,Iron Plate,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mental Herb,Metal Coat,Muscle Band,Muscle Band,Muscle Band,Mystic Water,Pecha Berry,Petaya Berry,Razor Fang,Sea Incense,Sharp Beak,Shuca Berry,Sky Plate,Spell Tag,Splash Plate,Spooky Plate,Wacan Berry,Wave Incense,Wise Glasses,Zoom Lens
        """);

        EXPECTED.put("Black 2", """
        BOSS L70 Zekrom: Air Balloon,Chilan Berry,Custap Berry,Destiny Knot,Haban Berry,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rocky Helmet,Shuca Berry,Yache Berry
        BOSS L72 Froslass: Babiri Berry,Charti Berry,Choice Band,Choice Specs,Choice Specs,Choice Specs,Colbur Berry,Custap Berry,Destiny Knot,Ghost Gem,Ice Gem,Ice Gem,Icicle Plate,Icicle Plate,Jaboca Berry,Kasib Berry,King's Rock,Leppa Berry,Liechi Berry,Mind Plate,Muscle Band,NeverMeltIce,NeverMeltIce,Occa Berry,Odd Incense,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry,Psychic Gem,Razor Fang,Rocky Helmet,Spell Tag,Spooky Plate,TwistedSpoon,Wise Glasses,Wise Glasses,Wise Glasses
        BOSS L22 Swadloon: Bug Gem,Charti Berry,Chilan Berry,Choice Band,Choice Specs,Coba Berry,Coba Berry,Coba Berry,Coba Berry,Custap Berry,Destiny Knot,Eviolite,Grass Gem,Insect Plate,Jaboca Berry,Kebia Berry,King's Rock,Leppa Berry,Liechi Berry,Meadow Plate,Miracle Seed,Muscle Band,Occa Berry,Occa Berry,Occa Berry,Occa Berry,Oran Berry,Petaya Berry,Razor Fang,Rocky Helmet,Rose Incense,SilverPowder,Tanga Berry,Wise Glasses,Yache Berry
        BOSS L57 Druddigon: Black Belt,Charcoal,Chilan Berry,Choice Band,Choice Band,Choice Specs,Choice Specs,Custap Berry,Destiny Knot,Draco Plate,Dragon Fang,Dragon Gem,Fighting Gem,Fire Gem,Fist Plate,Flame Plate,Haban Berry,Hard Stone,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Oran Berry,Petaya Berry,Petaya Berry,Razor Fang,Rock Gem,Rock Incense,Rocky Helmet,Stone Plate,Wise Glasses,Wise Glasses,Yache Berry
        BOSS L67 Simisage: BlackGlasses,Chilan Berry,Choice Band,Choice Band,Choice Specs,Coba Berry,Custap Berry,Dark Gem,Destiny Knot,Dread Plate,Grass Gem,Hard Stone,Jaboca Berry,Kebia Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Meadow Plate,Miracle Seed,Muscle Band,Muscle Band,Occa Berry,Oran Berry,Petaya Berry,Razor Fang,Rock Gem,Rock Incense,Rocky Helmet,Rose Incense,Stone Plate,Tanga Berry,Wise Glasses,Yache Berry
        BOSS L22 Shelmet: Big Root,Bug Gem,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Coba Berry,Custap Berry,Destiny Knot,Eviolite,Grass Gem,Insect Plate,Jaboca Berry,King's Rock,Leppa Berry,Meadow Plate,Miracle Seed,Occa Berry,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry,Poison Barb,Poison Gem,Razor Fang,Rocky Helmet,Rose Incense,SilverPowder,Toxic Plate,Wise Glasses,Wise Glasses,Wise Glasses
        BOSS L56 Golurk: Black Belt,Choice Band,Choice Band,Choice Band,Choice Band,Colbur Berry,Custap Berry,Destiny Knot,Earth Plate,Fighting Gem,Fist Plate,Ghost Gem,Ground Gem,Iron Plate,Jaboca Berry,Kasib Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Metal Coat,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Oran Berry,Passho Berry,Razor Fang,Rindo Berry,Rocky Helmet,Soft Sand,Spell Tag,Spooky Plate,Steel Gem,Yache Berry
        BOSS L73 Froslass: Babiri Berry,Charti Berry,Choice Band,Choice Specs,Choice Specs,Choice Specs,Colbur Berry,Custap Berry,Destiny Knot,Ghost Gem,Ice Gem,Ice Gem,Icicle Plate,Icicle Plate,Jaboca Berry,Kasib Berry,King's Rock,Leppa Berry,Liechi Berry,Mind Plate,Muscle Band,NeverMeltIce,NeverMeltIce,Occa Berry,Odd Incense,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry,Psychic Gem,Razor Fang,Rocky Helmet,Spell Tag,Spooky Plate,TwistedSpoon,Wise Glasses,Wise Glasses,Wise Glasses
        BOSS L77 Aggron: Air Balloon,Choice Band,Choice Band,Choice Band,Chople Berry,Chople Berry,Chople Berry,Chople Berry,Custap Berry,Destiny Knot,Earth Plate,Ground Gem,Hard Stone,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Oran Berry,Passho Berry,Razor Fang,Rock Gem,Rock Incense,Rocky Helmet,Shuca Berry,Shuca Berry,Shuca Berry,Shuca Berry,Silk Scarf,Soft Sand,Stone Plate
        BOSS L75 Glaceon: Babiri Berry,Bug Gem,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Chople Berry,Custap Berry,Destiny Knot,Ghost Gem,Ice Gem,Icicle Plate,Insect Plate,Jaboca Berry,King's Rock,Leppa Berry,NeverMeltIce,Occa Berry,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry,Razor Fang,Rocky Helmet,SilverPowder,Spell Tag,Spooky Plate,Wise Glasses,Wise Glasses,Wise Glasses
        BOSS-CONSUM L70 Zekrom: Air Balloon,Chilan Berry,Custap Berry,Haban Berry,Jaboca Berry,Leppa Berry,Oran Berry,Shuca Berry,Yache Berry
        BOSS-CONSUM L72 Froslass: Babiri Berry,Charti Berry,Colbur Berry,Custap Berry,Ghost Gem,Ice Gem,Ice Gem,Jaboca Berry,Kasib Berry,Leppa Berry,Liechi Berry,Occa Berry,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry,Psychic Gem
        BOSS-CONSUM L22 Swadloon: Bug Gem,Charti Berry,Chilan Berry,Coba Berry,Coba Berry,Coba Berry,Custap Berry,Grass Gem,Jaboca Berry,Kebia Berry,Leppa Berry,Liechi Berry,Occa Berry,Occa Berry,Occa Berry,Oran Berry,Petaya Berry,Tanga Berry,Yache Berry
        BOSS-CONSUM L57 Druddigon: Chilan Berry,Custap Berry,Dragon Gem,Fighting Gem,Fire Gem,Haban Berry,Jaboca Berry,Leppa Berry,Liechi Berry,Liechi Berry,Oran Berry,Petaya Berry,Petaya Berry,Rock Gem,Yache Berry
        BOSS-CONSUM L67 Simisage: Chilan Berry,Coba Berry,Custap Berry,Dark Gem,Grass Gem,Jaboca Berry,Kebia Berry,Leppa Berry,Liechi Berry,Liechi Berry,Occa Berry,Oran Berry,Petaya Berry,Rock Gem,Tanga Berry,Yache Berry
        BOSS-CONSUM L22 Shelmet: Bug Gem,Charti Berry,Chilan Berry,Coba Berry,Custap Berry,Grass Gem,Jaboca Berry,Leppa Berry,Occa Berry,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry,Poison Gem
        BOSS-CONSUM L56 Golurk: Colbur Berry,Custap Berry,Fighting Gem,Ghost Gem,Ground Gem,Jaboca Berry,Kasib Berry,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Oran Berry,Passho Berry,Rindo Berry,Steel Gem,Yache Berry
        BOSS-CONSUM L73 Froslass: Babiri Berry,Charti Berry,Colbur Berry,Custap Berry,Ghost Gem,Ice Gem,Ice Gem,Jaboca Berry,Kasib Berry,Leppa Berry,Liechi Berry,Occa Berry,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry,Psychic Gem
        BOSS-CONSUM L77 Aggron: Air Balloon,Chople Berry,Chople Berry,Chople Berry,Custap Berry,Ground Gem,Jaboca Berry,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Normal Gem,Oran Berry,Passho Berry,Rock Gem,Shuca Berry,Shuca Berry,Shuca Berry
        BOSS-CONSUM L75 Glaceon: Babiri Berry,Bug Gem,Charti Berry,Chilan Berry,Chople Berry,Custap Berry,Ghost Gem,Ice Gem,Jaboca Berry,Leppa Berry,Occa Berry,Oran Berry,Petaya Berry,Petaya Berry,Petaya Berry
        IMP  L8 Tepig: Air Balloon,Charti Berry,Chilan Berry,Choice Band,Custap Berry,Destiny Knot,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Muscle Band,Normal Gem,Oran Berry,Passho Berry,Razor Fang,Rocky Helmet,Shuca Berry,Silk Scarf
        IMP  L23 Pansear: Air Balloon,Charti Berry,Chilan Berry,Custap Berry,Destiny Knot,Eviolite,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Passho Berry,Razor Fang,Rocky Helmet,Shuca Berry
        IMP  L33 Dewott: Chilan Berry,Custap Berry,Destiny Knot,Eviolite,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rindo Berry,Rocky Helmet,Wacan Berry
        IMP  L49 Cryogonal: Babiri Berry,Charti Berry,Chilan Berry,Choice Band,Choice Specs,Chople Berry,Custap Berry,Destiny Knot,Ice Gem,Icicle Plate,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Light Clay,Muscle Band,NeverMeltIce,Normal Gem,Occa Berry,Oran Berry,Petaya Berry,Razor Fang,Rocky Helmet,Silk Scarf,Wise Glasses
        IMP  L55 Bouffalant: Bug Gem,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Chople Berry,Custap Berry,Destiny Knot,Earth Plate,Electric Gem,Ground Gem,Insect Plate,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Magnet,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Oran Berry,Razor Fang,Rocky Helmet,Silk Scarf,SilverPowder,Soft Sand,Zap Plate
        IMP  L62 Eelektross: Air Balloon,BlackGlasses,Charcoal,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Specs,Custap Berry,Dark Gem,Destiny Knot,Draco Plate,Dragon Fang,Dragon Gem,Dread Plate,Electric Gem,Fire Gem,Flame Plate,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Magnet,Muscle Band,Muscle Band,Muscle Band,Oran Berry,Petaya Berry,Razor Fang,Rocky Helmet,Shuca Berry,Wise Glasses,Zap Plate
        IMP  L65 Unfezant: Bug Gem,Charti Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Custap Berry,Destiny Knot,Flying Gem,Insect Plate,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Oran Berry,Razor Fang,Rocky Helmet,Sharp Beak,Silk Scarf,SilverPowder,Sky Plate,Wacan Berry,Yache Berry
        IMP  L65 Flygon: Charcoal,Chilan Berry,Choice Band,Choice Band,Choice Specs,Choice Specs,Custap Berry,Destiny Knot,Draco Plate,Dragon Fang,Dragon Gem,Earth Plate,Fire Gem,Flame Plate,Ground Gem,Haban Berry,Hard Stone,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Oran Berry,Petaya Berry,Petaya Berry,Razor Fang,Rock Gem,Rock Incense,Rocky Helmet,Soft Sand,Stone Plate,Wise Glasses,Wise Glasses,Yache Berry,Yache Berry,Yache Berry,Yache Berry
        IMP  L50 Emboar: Air Balloon,Chilan Berry,Coba Berry,Custap Berry,Destiny Knot,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Passho Berry,Payapa Berry,Razor Fang,Rocky Helmet,Shuca Berry
        IMP  L74 Klinklang: Air Balloon,Choice Band,Choice Band,Choice Band,Chople Berry,Custap Berry,Destiny Knot,Electric Gem,Iron Plate,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Magnet,Metal Coat,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Occa Berry,Oran Berry,Razor Fang,Rocky Helmet,Shuca Berry,Silk Scarf,Steel Gem,Zap Plate
        REG  L26 Blitzle: Air Balloon,Chilan Berry,Custap Berry,Destiny Knot,Eviolite,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rocky Helmet,Shuca Berry
        REG  L60 Seadra: Chilan Berry,Custap Berry,Destiny Knot,Eviolite,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rindo Berry,Rocky Helmet,Wacan Berry
        REG  L62 Scrafty: Black Belt,Chilan Berry,Choice Band,Choice Band,Chople Berry,Coba Berry,Custap Berry,Destiny Knot,Fighting Gem,Fist Plate,Hard Stone,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Oran Berry,Razor Fang,Rock Gem,Rock Incense,Rocky Helmet,Stone Plate
        REG  L39 Basculin: Chilan Berry,Custap Berry,Destiny Knot,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rindo Berry,Rocky Helmet,Wacan Berry
        REG  L61 Deino: Chilan Berry,Chople Berry,Custap Berry,Destiny Knot,Eviolite,Haban Berry,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rocky Helmet,Tanga Berry,Yache Berry
        REG  L47 Scrafty: Chilan Berry,Chople Berry,Coba Berry,Custap Berry,Destiny Knot,Jaboca Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rocky Helmet
        REG  L37 Scolipede: Black Sludge,Charti Berry,Chilan Berry,Coba Berry,Custap Berry,Destiny Knot,Jaboca Berry,King's Rock,Leppa Berry,Occa Berry,Oran Berry,Payapa Berry,Razor Fang,Rocky Helmet
        REG  L64 Forretress: BlackGlasses,Choice Band,Choice Band,Choice Band,Choice Specs,Custap Berry,Dark Gem,Destiny Knot,Dread Plate,Grass Gem,Iron Plate,Jaboca Berry,King's Rock,Leppa Berry,Liechi Berry,Liechi Berry,Liechi Berry,Meadow Plate,Metal Coat,Miracle Seed,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Occa Berry,Occa Berry,Occa Berry,Occa Berry,Oran Berry,Petaya Berry,Power Herb,Razor Fang,Rocky Helmet,Rose Incense,Silk Scarf,Steel Gem,Wise Glasses
        REG  L13 Woobat: Charti Berry,Chilan Berry,Colbur Berry,Custap Berry,Destiny Knot,Jaboca Berry,Kasib Berry,King's Rock,Leppa Berry,Oran Berry,Razor Fang,Rocky Helmet,Wacan Berry,Yache Berry
        REG  L25 Litwick: Air Balloon,Charcoal,Charti Berry,Choice Band,Choice Specs,Colbur Berry,Custap Berry,Destiny Knot,Eviolite,Fire Gem,Flame Plate,Ghost Gem,Jaboca Berry,Kasib Berry,King's Rock,Leppa Berry,Liechi Berry,Muscle Band,Oran Berry,Passho Berry,Petaya Berry,Razor Fang,Rocky Helmet,Shuca Berry,Spell Tag,Spooky Plate,Wise Glasses
        """);

        EXPECTED.put("Alpha Sapphire", """
        BOSS L41 Mightyena: Cheri Berry,Chilan Berry,Chople Berry,Float Stone,Jaboca Berry,Pecha Berry,Quick Claw,Roseli Berry,Salac Berry,Shed Shell,Tanga Berry,Zoom Lens
        BOSS L25 Golbat: Black Sludge,Charti Berry,Cheri Berry,Chilan Berry,Eviolite,Float Stone,Jaboca Berry,Payapa Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Wacan Berry,Yache Berry,Zoom Lens
        BOSS L51 Banette: Assault Vest,Black Glasses,Cheri Berry,Choice Band,Choice Specs,Colbur Berry,Dark Gem,Dread Plate,Float Stone,Ghost Gem,Jaboca Berry,Kasib Berry,Liechi Berry,Muscle Band,Pecha Berry,Petaya Berry,Quick Claw,Salac Berry,Shed Shell,Spell Tag,Spooky Plate,Wise Glasses,Zoom Lens
        BOSS L53 Flygon: Assault Vest,Charcoal,Cheri Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Draco Plate,Dragon Fang,Dragon Gem,Fire Gem,Flame Plate,Float Stone,Haban Berry,Jaboca Berry,Normal Gem,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Quick Claw,Roseli Berry,Salac Berry,Shed Shell,Silk Scarf,Wise Glasses,Wise Glasses,Wise Glasses,Yache Berry,Yache Berry,Yache Berry,Yache Berry,Zoom Lens
        BOSS L19 Magnemite: Air Balloon,Assault Vest,Cheri Berry,Choice Band,Choice Specs,Chople Berry,Electric Gem,Float Stone,Jaboca Berry,Liechi Berry,Magnet,Muscle Band,Normal Gem,Occa Berry,Pecha Berry,Petaya Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Shuca Berry,Shuca Berry,Shuca Berry,Silk Scarf,Wise Glasses,Zap Plate,Zoom Lens
        BOSS L35 Altaria: Assault Vest,Charti Berry,Cheri Berry,Chilan Berry,Choice Band,Choice Specs,Draco Plate,Dragon Fang,Dragon Gem,Earth Plate,Float Stone,Ground Gem,Haban Berry,Jaboca Berry,Liechi Berry,Muscle Band,Pecha Berry,Petaya Berry,Quick Claw,Roseli Berry,Salac Berry,Shed Shell,Soft Sand,Wise Glasses,Yache Berry,Yache Berry,Yache Berry,Yache Berry,Zoom Lens
        BOSS L72 Absol: Assault Vest,Black Glasses,Cheri Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Chople Berry,Dark Gem,Dread Plate,Float Stone,Flying Gem,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mind Plate,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Odd Incense,Pecha Berry,Psychic Gem,Quick Claw,Roseli Berry,Salac Berry,Sharp Beak,Shed Shell,Silk Scarf,Sky Plate,Tanga Berry,Twisted Spoon,Zoom Lens
        BOSS L74 Glalie: Assault Vest,Babiri Berry,Charti Berry,Cheri Berry,Chilan Berry,Choice Band,Choice Specs,Chople Berry,Float Stone,Ice Gem,Ice Gem,Icicle Plate,Icicle Plate,Icy Rock,Jaboca Berry,Liechi Berry,Muscle Band,Never-Melt Ice,Never-Melt Ice,Occa Berry,Pecha Berry,Petaya Berry,Quick Claw,Salac Berry,Shed Shell,Wise Glasses,Zoom Lens
        BOSS L79 Metagross: Air Balloon,Assault Vest,Cheri Berry,Choice Band,Choice Band,Choice Band,Choice Band,Colbur Berry,Float Stone,Iron Plate,Iron Plate,Jaboca Berry,Kasib Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Metal Coat,Metal Coat,Mind Plate,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Occa Berry,Odd Incense,Pecha Berry,Psychic Gem,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Silk Scarf,Steel Gem,Steel Gem,Twisted Spoon,Zoom Lens
        BOSS L57 Milotic: Assault Vest,Cheri Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Draco Plate,Dragon Fang,Dragon Gem,Float Stone,Ice Gem,Icicle Plate,Jaboca Berry,Mystic Water,Never-Melt Ice,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Quick Claw,Rindo Berry,Salac Berry,Sea Incense,Shed Shell,Splash Plate,Wacan Berry,Water Gem,Wave Incense,Wise Glasses,Wise Glasses,Wise Glasses,Zoom Lens
        BOSS-CONSUM L41 Mightyena: Cheri Berry,Chilan Berry,Chople Berry,Jaboca Berry,Pecha Berry,Roseli Berry,Salac Berry,Tanga Berry
        BOSS-CONSUM L25 Golbat: Charti Berry,Cheri Berry,Chilan Berry,Jaboca Berry,Payapa Berry,Pecha Berry,Salac Berry,Wacan Berry,Yache Berry
        BOSS-CONSUM L51 Banette: Assault Vest,Cheri Berry,Colbur Berry,Dark Gem,Ghost Gem,Jaboca Berry,Kasib Berry,Liechi Berry,Pecha Berry,Petaya Berry,Salac Berry
        BOSS-CONSUM L53 Flygon: Assault Vest,Cheri Berry,Chilan Berry,Dragon Gem,Fire Gem,Haban Berry,Jaboca Berry,Normal Gem,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Roseli Berry,Salac Berry,Yache Berry,Yache Berry,Yache Berry
        BOSS-CONSUM L19 Magnemite: Air Balloon,Assault Vest,Cheri Berry,Chople Berry,Electric Gem,Jaboca Berry,Liechi Berry,Normal Gem,Occa Berry,Pecha Berry,Petaya Berry,Salac Berry,Shuca Berry,Shuca Berry,Shuca Berry
        BOSS-CONSUM L35 Altaria: Assault Vest,Charti Berry,Cheri Berry,Chilan Berry,Dragon Gem,Ground Gem,Haban Berry,Jaboca Berry,Liechi Berry,Pecha Berry,Petaya Berry,Roseli Berry,Salac Berry,Yache Berry,Yache Berry,Yache Berry
        BOSS-CONSUM L72 Absol: Assault Vest,Cheri Berry,Chilan Berry,Chople Berry,Dark Gem,Flying Gem,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Normal Gem,Pecha Berry,Psychic Gem,Roseli Berry,Salac Berry,Tanga Berry
        BOSS-CONSUM L74 Glalie: Assault Vest,Babiri Berry,Charti Berry,Cheri Berry,Chilan Berry,Chople Berry,Ice Gem,Ice Gem,Jaboca Berry,Liechi Berry,Occa Berry,Pecha Berry,Petaya Berry,Salac Berry
        BOSS-CONSUM L79 Metagross: Air Balloon,Assault Vest,Cheri Berry,Colbur Berry,Jaboca Berry,Kasib Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Normal Gem,Occa Berry,Pecha Berry,Psychic Gem,Salac Berry,Shuca Berry,Steel Gem,Steel Gem
        BOSS-CONSUM L57 Milotic: Assault Vest,Cheri Berry,Chilan Berry,Dragon Gem,Ice Gem,Jaboca Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Rindo Berry,Salac Berry,Wacan Berry,Water Gem
        IMP  L18 Slugma: Air Balloon,Charti Berry,Cheri Berry,Chilan Berry,Float Stone,Jaboca Berry,Passho Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Zoom Lens
        IMP  L31 Slugma: Air Balloon,Charti Berry,Cheri Berry,Chilan Berry,Eviolite,Float Stone,Jaboca Berry,Passho Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Zoom Lens
        IMP  L33 Combusken: Air Balloon,Cheri Berry,Chilan Berry,Coba Berry,Eviolite,Float Stone,Jaboca Berry,Passho Berry,Payapa Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Zoom Lens
        IMP  L39 Swampert: Cheri Berry,Chilan Berry,Float Stone,Jaboca Berry,Pecha Berry,Quick Claw,Rindo Berry,Rindo Berry,Rindo Berry,Rindo Berry,Salac Berry,Shed Shell,Zoom Lens
        IMP  L46 Magneton: Air Balloon,Assault Vest,Cheri Berry,Choice Specs,Choice Specs,Choice Specs,Chople Berry,Electric Gem,Eviolite,Float Stone,Iron Plate,Jaboca Berry,Magnet,Metal Coat,Normal Gem,Occa Berry,Pecha Berry,Petaya Berry,Petaya Berry,Petaya Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Shuca Berry,Shuca Berry,Shuca Berry,Silk Scarf,Steel Gem,Wise Glasses,Wise Glasses,Wise Glasses,Zap Plate,Zoom Lens
        IMP  L26 Combusken: Air Balloon,Cheri Berry,Chilan Berry,Coba Berry,Eviolite,Float Stone,Jaboca Berry,Passho Berry,Payapa Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Zoom Lens
        IMP  L24 Koffing: Air Balloon,Black Sludge,Cheri Berry,Chilan Berry,Eviolite,Float Stone,Jaboca Berry,Payapa Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Zoom Lens
        IMP  L48 Breloom: Assault Vest,Black Belt,Cheri Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Specs,Coba Berry,Coba Berry,Coba Berry,Coba Berry,Fighting Gem,Fist Plate,Float Stone,Grass Gem,Hard Stone,Jaboca Berry,Kebia Berry,Liechi Berry,Liechi Berry,Liechi Berry,Meadow Plate,Miracle Seed,Muscle Band,Muscle Band,Muscle Band,Occa Berry,Payapa Berry,Pecha Berry,Petaya Berry,Poison Barb,Poison Gem,Quick Claw,Rock Gem,Rock Incense,Rose Incense,Roseli Berry,Salac Berry,Shed Shell,Stone Plate,Toxic Plate,Wise Glasses,Yache Berry,Zoom Lens
        IMP  L50 Blaziken: Air Balloon,Assault Vest,Black Belt,Charcoal,Cheri Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Coba Berry,Fighting Gem,Fire Gem,Fist Plate,Flame Plate,Float Stone,Ghost Gem,Jaboca Berry,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Passho Berry,Payapa Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Silk Scarf,Spell Tag,Spooky Plate,Zoom Lens
        IMP  L81 Gallade: Assault Vest,Black Belt,Cheri Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Coba Berry,Fighting Gem,Fist Plate,Float Stone,Grass Gem,Jaboca Berry,Kasib Berry,Liechi Berry,Liechi Berry,Liechi Berry,Meadow Plate,Mind Plate,Miracle Seed,Muscle Band,Muscle Band,Muscle Band,Odd Incense,Pecha Berry,Psychic Gem,Quick Claw,Rose Incense,Roseli Berry,Salac Berry,Shed Shell,Twisted Spoon,Zoom Lens
        REG  L4 Zigzagoon: Cheri Berry,Chilan Berry,Chople Berry,Float Stone,Jaboca Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Zoom Lens
        REG  L17 Taillow: Charti Berry,Cheri Berry,Chilan Berry,Flame Orb,Float Stone,Jaboca Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Toxic Orb,Wacan Berry,Yache Berry,Zoom Lens
        REG  L51 Golbat: Black Sludge,Charti Berry,Cheri Berry,Chilan Berry,Eviolite,Float Stone,Jaboca Berry,Payapa Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Wacan Berry,Yache Berry,Zoom Lens
        REG  L27 Voltorb: Air Balloon,Cheri Berry,Chilan Berry,Eviolite,Float Stone,Jaboca Berry,Pecha Berry,Quick Claw,Salac Berry,Shed Shell,Shuca Berry,Zoom Lens
        REG  L54 Medicham: Cheri Berry,Chilan Berry,Coba Berry,Float Stone,Jaboca Berry,Kasib Berry,Pecha Berry,Quick Claw,Roseli Berry,Salac Berry,Shed Shell,Zoom Lens
        REG  L27 Feebas: Cheri Berry,Chilan Berry,Eviolite,Float Stone,Jaboca Berry,Pecha Berry,Quick Claw,Rindo Berry,Salac Berry,Shed Shell,Wacan Berry,Zoom Lens
        REG  L25 Mightyena: Assault Vest,Black Glasses,Black Glasses,Cheri Berry,Chilan Berry,Choice Band,Choice Specs,Chople Berry,Dark Gem,Dark Gem,Dread Plate,Dread Plate,Float Stone,Jaboca Berry,Liechi Berry,Muscle Band,Pecha Berry,Petaya Berry,Quick Claw,Roseli Berry,Salac Berry,Shed Shell,Tanga Berry,Wise Glasses,Zoom Lens
        REG  L15 Geodude: Air Balloon,Babiri Berry,Cheri Berry,Chople Berry,Float Stone,Jaboca Berry,Passho Berry,Passho Berry,Passho Berry,Passho Berry,Pecha Berry,Quick Claw,Rindo Berry,Rindo Berry,Rindo Berry,Rindo Berry,Salac Berry,Shed Shell,Shuca Berry,Yache Berry,Zoom Lens
        REG  L5 Makuhita: Cheri Berry,Chilan Berry,Coba Berry,Float Stone,Jaboca Berry,Payapa Berry,Pecha Berry,Quick Claw,Roseli Berry,Salac Berry,Shed Shell,Zoom Lens
        REG  L51 Mightyena: Cheri Berry,Chilan Berry,Chople Berry,Float Stone,Jaboca Berry,Pecha Berry,Quick Claw,Roseli Berry,Salac Berry,Shed Shell,Tanga Berry,Zoom Lens
        """);

        EXPECTED.put("Ultra Sun", """
        BOSS L15 Machop: Apicot Berry,Assault Vest,Black Belt,Black Belt,Chilan Berry,Choice Band,Choice Band,Coba Berry,Custap Berry,Fighting Gem,Fighting Gem,Fist Plate,Fist Plate,Flame Orb,Focus Sash,Lax Incense,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Payapa Berry,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Toxic Orb,Wide Lens
        BOSS L56 Froslass: Apicot Berry,Assault Vest,Babiri Berry,Charti Berry,Choice Band,Choice Specs,Choice Specs,Colbur Berry,Custap Berry,Focus Sash,Ghost Gem,Ice Gem,Ice Gem,Icicle Plate,Icicle Plate,Kasib Berry,Lax Incense,Liechi Berry,Muscle Band,Never-Melt Ice,Never-Melt Ice,Occa Berry,Petaya Berry,Petaya Berry,Razor Claw,Safety Goggles,Salac Berry,Spell Tag,Spooky Plate,Wide Lens,Wise Glasses,Wise Glasses
        BOSS L41 Masquerain: Apicot Berry,Assault Vest,Bug Gem,Charti Berry,Charti Berry,Charti Berry,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Coba Berry,Custap Berry,Flying Gem,Focus Sash,Ice Gem,Icicle Plate,Insect Plate,Lax Incense,Never-Melt Ice,Occa Berry,Petaya Berry,Petaya Berry,Petaya Berry,Razor Claw,Safety Goggles,Salac Berry,Sharp Beak,Silver Powder,Sky Plate,Wacan Berry,Wide Lens,Wise Glasses,Wise Glasses,Wise Glasses,Yache Berry
        BOSS L66 Braviary: Apicot Berry,Assault Vest,Black Belt,Charti Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Custap Berry,Fighting Gem,Fist Plate,Flying Gem,Focus Sash,Lax Incense,Liechi Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Razor Claw,Safety Goggles,Salac Berry,Sharp Beak,Silk Scarf,Sky Plate,Wacan Berry,Wide Lens,Yache Berry
        BOSS L66 Bisharp: Air Balloon,Apicot Berry,Assault Vest,Black Glasses,Bug Gem,Choice Band,Choice Band,Choice Band,Choice Band,Chople Berry,Chople Berry,Chople Berry,Chople Berry,Custap Berry,Dark Gem,Dread Plate,Focus Sash,Insect Plate,Iron Plate,Lax Incense,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Metal Coat,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Occa Berry,Poison Barb,Poison Gem,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Silver Powder,Steel Gem,Toxic Plate,Wide Lens
        BOSS L65 Houndoom: Air Balloon,Apicot Berry,Assault Vest,Black Glasses,Charcoal,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Choice Specs,Chople Berry,Custap Berry,Dark Gem,Dread Plate,Fire Gem,Flame Plate,Focus Sash,Ghost Gem,Lax Incense,Passho Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Poison Barb,Poison Gem,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Spell Tag,Spooky Plate,Toxic Plate,Wide Lens,Wise Glasses,Wise Glasses,Wise Glasses,Wise Glasses
        BOSS L66 Bouffalant: Apicot Berry,Assault Vest,Bug Gem,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Chople Berry,Custap Berry,Earth Plate,Focus Sash,Ground Gem,Insect Plate,Lax Incense,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Normal Gem,Poison Barb,Poison Gem,Razor Claw,Safety Goggles,Salac Berry,Silk Scarf,Silver Powder,Soft Sand,Toxic Plate,Wide Lens
        BOSS L65 Honchkrow: Apicot Berry,Assault Vest,Black Glasses,Charcoal,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Choice Specs,Choice Specs,Custap Berry,Dark Gem,Dread Plate,Fire Gem,Flame Plate,Focus Sash,Ghost Gem,Lax Incense,Mind Plate,Odd Incense,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Psychic Gem,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Spell Tag,Spooky Plate,Twisted Spoon,Wacan Berry,Wide Lens,Wise Glasses,Wise Glasses,Wise Glasses,Wise Glasses,Yache Berry
        BOSS L68 Milotic: Apicot Berry,Assault Vest,Chilan Berry,Choice Band,Choice Specs,Choice Specs,Choice Specs,Custap Berry,Draco Plate,Dragon Fang,Dragon Gem,Focus Sash,Ice Gem,Icicle Plate,Lax Incense,Liechi Berry,Muscle Band,Mystic Water,Never-Melt Ice,Normal Gem,Petaya Berry,Petaya Berry,Petaya Berry,Razor Claw,Rindo Berry,Safety Goggles,Salac Berry,Sea Incense,Silk Scarf,Splash Plate,Wacan Berry,Water Gem,Wave Incense,Wide Lens,Wise Glasses,Wise Glasses,Wise Glasses
        BOSS L63 Crabominable: Apicot Berry,Assault Vest,Babiri Berry,Black Belt,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Chople Berry,Coba Berry,Custap Berry,Earth Plate,Fighting Gem,Fist Plate,Focus Sash,Ground Gem,Hard Stone,Ice Gem,Icicle Plate,Lax Incense,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Never-Melt Ice,Occa Berry,Payapa Berry,Razor Claw,Rock Gem,Rock Incense,Roseli Berry,Safety Goggles,Salac Berry,Soft Sand,Stone Plate,Wide Lens
        BOSS-CONSUM L15 Machop: Apicot Berry,Assault Vest,Chilan Berry,Coba Berry,Custap Berry,Fighting Gem,Fighting Gem,Focus Sash,Liechi Berry,Liechi Berry,Payapa Berry,Roseli Berry,Salac Berry
        BOSS-CONSUM L56 Froslass: Apicot Berry,Assault Vest,Babiri Berry,Charti Berry,Colbur Berry,Custap Berry,Focus Sash,Ghost Gem,Ice Gem,Ice Gem,Kasib Berry,Liechi Berry,Occa Berry,Petaya Berry,Petaya Berry,Salac Berry
        BOSS-CONSUM L41 Masquerain: Apicot Berry,Assault Vest,Bug Gem,Charti Berry,Charti Berry,Charti Berry,Chilan Berry,Coba Berry,Custap Berry,Flying Gem,Focus Sash,Ice Gem,Occa Berry,Petaya Berry,Petaya Berry,Petaya Berry,Salac Berry,Wacan Berry,Yache Berry
        BOSS-CONSUM L66 Braviary: Apicot Berry,Assault Vest,Charti Berry,Chilan Berry,Custap Berry,Fighting Gem,Flying Gem,Focus Sash,Liechi Berry,Liechi Berry,Liechi Berry,Normal Gem,Salac Berry,Wacan Berry,Yache Berry
        BOSS-CONSUM L66 Bisharp: Air Balloon,Apicot Berry,Assault Vest,Bug Gem,Chople Berry,Chople Berry,Chople Berry,Custap Berry,Dark Gem,Focus Sash,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Occa Berry,Poison Gem,Salac Berry,Shuca Berry,Steel Gem
        BOSS-CONSUM L65 Houndoom: Air Balloon,Apicot Berry,Assault Vest,Charti Berry,Chilan Berry,Chople Berry,Custap Berry,Dark Gem,Fire Gem,Focus Sash,Ghost Gem,Passho Berry,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Poison Gem,Salac Berry,Shuca Berry
        BOSS-CONSUM L66 Bouffalant: Apicot Berry,Assault Vest,Bug Gem,Chilan Berry,Chople Berry,Custap Berry,Focus Sash,Ground Gem,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Normal Gem,Poison Gem,Salac Berry
        BOSS-CONSUM L65 Honchkrow: Apicot Berry,Assault Vest,Charti Berry,Chilan Berry,Custap Berry,Dark Gem,Fire Gem,Focus Sash,Ghost Gem,Petaya Berry,Petaya Berry,Petaya Berry,Petaya Berry,Psychic Gem,Roseli Berry,Salac Berry,Wacan Berry,Yache Berry
        BOSS-CONSUM L68 Milotic: Apicot Berry,Assault Vest,Chilan Berry,Custap Berry,Dragon Gem,Focus Sash,Ice Gem,Liechi Berry,Normal Gem,Petaya Berry,Petaya Berry,Petaya Berry,Rindo Berry,Salac Berry,Wacan Berry,Water Gem
        BOSS-CONSUM L63 Crabominable: Apicot Berry,Assault Vest,Babiri Berry,Chilan Berry,Chople Berry,Coba Berry,Custap Berry,Fighting Gem,Focus Sash,Ground Gem,Ice Gem,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Occa Berry,Payapa Berry,Rock Gem,Roseli Berry,Salac Berry
        IMP  L6 Pichu: Air Balloon,Apicot Berry,Chilan Berry,Choice Specs,Custap Berry,Electric Gem,Focus Sash,Lax Incense,Magnet,Petaya Berry,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Wide Lens,Wise Glasses,Zap Plate
        IMP  L14 Noibat: Apicot Berry,Assault Vest,Black Glasses,Charti Berry,Chilan Berry,Choice Band,Choice Band,Custap Berry,Dark Gem,Dread Plate,Focus Sash,Haban Berry,Lax Incense,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Normal Gem,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Silk Scarf,Wide Lens,Yache Berry,Yache Berry,Yache Berry,Yache Berry
        IMP  L29 Raichu: Air Balloon,Apicot Berry,Assault Vest,Chilan Berry,Choice Band,Choice Specs,Choice Specs,Colbur Berry,Custap Berry,Electric Gem,Focus Sash,Kasib Berry,Lax Incense,Liechi Berry,Magnet,Mind Plate,Muscle Band,Normal Gem,Odd Incense,Petaya Berry,Petaya Berry,Psychic Gem,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Silk Scarf,Tanga Berry,Twisted Spoon,Wide Lens,Wise Glasses,Wise Glasses,Zap Plate
        IMP  L44 Bruxish: Apicot Berry,Assault Vest,Black Glasses,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Band,Colbur Berry,Custap Berry,Dark Gem,Dread Plate,Focus Sash,Kasib Berry,Lax Incense,Liechi Berry,Liechi Berry,Liechi Berry,Liechi Berry,Mind Plate,Muscle Band,Muscle Band,Muscle Band,Muscle Band,Mystic Water,Mystic Water,Odd Incense,Psychic Gem,Razor Claw,Rindo Berry,Safety Goggles,Salac Berry,Sea Incense,Sea Incense,Splash Plate,Splash Plate,Tanga Berry,Twisted Spoon,Wacan Berry,Water Gem,Water Gem,Wave Incense,Wave Incense,Wide Lens
        IMP  L67 Togedemaru: Air Balloon,Apicot Berry,Assault Vest,Bug Gem,Choice Band,Choice Band,Chople Berry,Custap Berry,Electric Gem,Focus Sash,Insect Plate,Lax Incense,Liechi Berry,Liechi Berry,Magnet,Muscle Band,Muscle Band,Occa Berry,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Shuca Berry,Shuca Berry,Shuca Berry,Silver Powder,Terrain Extender,Wide Lens,Zap Plate
        IMP  L68 Lucario: Air Balloon,Apicot Berry,Assault Vest,Black Belt,Choice Band,Choice Specs,Choice Specs,Choice Specs,Chople Berry,Custap Berry,Fighting Gem,Fist Plate,Focus Sash,Iron Plate,Lax Incense,Liechi Berry,Metal Coat,Mind Plate,Muscle Band,Normal Gem,Occa Berry,Odd Incense,Petaya Berry,Petaya Berry,Petaya Berry,Psychic Gem,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Silk Scarf,Steel Gem,Twisted Spoon,Wide Lens,Wise Glasses,Wise Glasses,Wise Glasses
        IMP  L41 Noivern: Apicot Berry,Assault Vest,Charti Berry,Chilan Berry,Choice Specs,Choice Specs,Custap Berry,Draco Plate,Dragon Fang,Dragon Gem,Flying Gem,Focus Sash,Haban Berry,Lax Incense,Petaya Berry,Petaya Berry,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Sharp Beak,Sky Plate,Wide Lens,Wise Glasses,Wise Glasses,Yache Berry,Yache Berry,Yache Berry,Yache Berry
        IMP  L68 Braviary: Apicot Berry,Assault Vest,Charti Berry,Chilan Berry,Choice Band,Choice Band,Custap Berry,Flying Gem,Focus Sash,Lax Incense,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Normal Gem,Razor Claw,Safety Goggles,Salac Berry,Sharp Beak,Silk Scarf,Sky Plate,Wacan Berry,Wide Lens,Yache Berry
        IMP  L49 Poipole: Air Balloon,Apicot Berry,Black Sludge,Chilan Berry,Custap Berry,Eviolite,Focus Sash,Lax Incense,Payapa Berry,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Wide Lens
        IMP  L20 Poipole: Air Balloon,Apicot Berry,Black Sludge,Chilan Berry,Custap Berry,Eviolite,Focus Sash,Lax Incense,Payapa Berry,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Wide Lens
        REG  L6 Yungoos: Apicot Berry,Chilan Berry,Chople Berry,Custap Berry,Focus Sash,Lax Incense,Razor Claw,Safety Goggles,Salac Berry,Wide Lens
        REG  L24 Raticate: Apicot Berry,Chilan Berry,Chople Berry,Chople Berry,Chople Berry,Chople Berry,Custap Berry,Focus Sash,Lax Incense,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Tanga Berry,Wide Lens
        REG  L69 Machamp: Apicot Berry,Assault Vest,Black Belt,Black Glasses,Chilan Berry,Choice Band,Choice Band,Choice Band,Coba Berry,Custap Berry,Dark Gem,Dread Plate,Fighting Gem,Fist Plate,Flame Orb,Focus Sash,Lax Incense,Liechi Berry,Liechi Berry,Liechi Berry,Muscle Band,Muscle Band,Muscle Band,Payapa Berry,Poison Barb,Poison Gem,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Toxic Orb,Toxic Plate,Wide Lens
        REG  L5 Yungoos: Apicot Berry,Chilan Berry,Chople Berry,Custap Berry,Focus Sash,Lax Incense,Razor Claw,Safety Goggles,Salac Berry,Wide Lens
        REG  L5 Yungoos: Apicot Berry,Chilan Berry,Chople Berry,Custap Berry,Focus Sash,Lax Incense,Razor Claw,Safety Goggles,Salac Berry,Wide Lens
        REG  L40 Kecleon: Apicot Berry,Chilan Berry,Chople Berry,Custap Berry,Focus Sash,Lax Incense,Razor Claw,Safety Goggles,Salac Berry,Wide Lens
        REG  L14 Grimer: Air Balloon,Apicot Berry,Black Sludge,Chilan Berry,Custap Berry,Focus Sash,Lax Incense,Payapa Berry,Razor Claw,Safety Goggles,Salac Berry,Shuca Berry,Wide Lens
        REG  L36 Whiscash: Apicot Berry,Assault Vest,Chilan Berry,Choice Specs,Choice Specs,Custap Berry,Earth Plate,Focus Sash,Ground Gem,Lax Incense,Mystic Water,Petaya Berry,Petaya Berry,Razor Claw,Rindo Berry,Rindo Berry,Rindo Berry,Rindo Berry,Safety Goggles,Salac Berry,Sea Incense,Soft Sand,Splash Plate,Water Gem,Wave Incense,Wide Lens,Wise Glasses,Wise Glasses
        REG  L58 Dragonite: Apicot Berry,Assault Vest,Charcoal,Charti Berry,Chilan Berry,Choice Band,Choice Band,Choice Band,Choice Specs,Custap Berry,Draco Plate,Dragon Fang,Dragon Gem,Electric Gem,Fire Gem,Flame Plate,Flying Gem,Focus Sash,Haban Berry,Lax Incense,Liechi Berry,Liechi Berry,Liechi Berry,Magnet,Muscle Band,Muscle Band,Muscle Band,Petaya Berry,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Sharp Beak,Sky Plate,Wide Lens,Wise Glasses,Yache Berry,Yache Berry,Yache Berry,Yache Berry,Zap Plate
        REG  L14 Rattata: Apicot Berry,Chilan Berry,Chople Berry,Chople Berry,Chople Berry,Chople Berry,Custap Berry,Focus Sash,Lax Incense,Razor Claw,Roseli Berry,Safety Goggles,Salac Berry,Tanga Berry,Wide Lens
        """);
    }

    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void sensibleHeldItemsGoldenMaster(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        String actual = canonicalBlock(romHandler);

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
        assertEquals(expected.strip(), actual.strip(), "Sensible held items output drifted for " + gameName);
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

    /** One holder so a picked mon carries its owning trainer (for the tier tag) alongside the Pokemon. */
    private record TierMon(Trainer trainer, TrainerPokemon pokemon) {}

    private String canonicalBlock(RomHandler romHandler) {
        StringBuilder sb = new StringBuilder();
        appendTier(sb, romHandler, "BOSS", Trainer::isBoss, false);
        appendTier(sb, romHandler, "BOSS-CONSUM", Trainer::isBoss, true);
        appendTier(sb, romHandler, "IMP ", Trainer::isImportant, false);
        appendTier(sb, romHandler, "REG ", Trainer::isRegular, false);
        return sb.toString().strip();
    }

    private static void appendTier(StringBuilder sb, RomHandler romHandler, String tierLabel,
                                   java.util.function.Predicate<Trainer> inTier, boolean consumableOnly) {
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
        List<Move> moves = romHandler.getMoves();
        Map<Integer, List<MoveLearnt>> movesets = romHandler.getMovesLearnt();
        for (int idx : evenSpread(mons.size(), SAMPLE_PER_TIER)) {
            TrainerPokemon tp = mons.get(idx).pokemon();
            int[] pokeMoves = tp.isResetMoves()
                    ? romHandler.getMovesAtLevel(tp.getSpecies(), movesets, tp.getLevel())
                    : tp.getMoves();
            List<Item> items = romHandler.getSensibleHeldItemsFor(tp, consumableOnly, moves, pokeMoves, new Random(SEED));
            // Sorted rather than insertion order: getTypeTable().against(...) iterates a Map whose
            // order isn't guaranteed stable across JVM invocations, so a couple of weakness berries can
            // swap relative position between runs even though the actual candidate pool (a multiset,
            // since the caller does a uniform random pick regardless of order) is unchanged. Sorting
            // canonicalizes on content instead of incidental iteration order.
            String itemNames = items.stream().map(i -> i == null ? "null" : i.getName())
                    .sorted()
                    .collect(Collectors.joining(","));
            sb.append(tierLabel).append(" L").append(tp.getLevel()).append(' ')
                    .append(tp.getSpecies().getName()).append(": ").append(itemNames).append('\n');
        }
    }

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

    private static void printPasteReadyBlock(String gameName, String actual) {
        System.out.println("\nEXPECTED.put(\"" + gameName + "\", \"\"\"\n" + actual + "\n\"\"\");");
    }
}
