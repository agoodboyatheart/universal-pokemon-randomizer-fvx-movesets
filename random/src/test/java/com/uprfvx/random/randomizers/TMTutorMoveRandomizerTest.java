package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link TMTutorMoveRandomizer#typeLockGymLeaderTMs}, the engine behind the
 * "Gym Leader TMs Match Type" option.
 * <p>
 * Deliberately standalone (does not extend {@link RandomizerTest}) so it loads only the specific
 * non-3DS ROMs it needs, rather than the full shared ROM set. Runs under the {@code testROMs} Gradle
 * task (its name matches {@code *Randomizer*Test}), which provides the {@code romsPath} property.
 */
public class TMTutorMoveRandomizerTest {

    private static final Random RND = new Random();
    private static final String ROMS_PATH = System.getProperty("romsPath");

    /** Game name (a {@link Generation} key) → the ROM file's base name in the roms folder. */
    static String[][] gamesToVerify() {
        return new String[][]{
                {"Red", "Pokemon Red"},
                {"Crystal", "Pokemon Crystal"},
                {"Emerald", "Pokemon Emerald"},
                {"Fire Red", "Pokemon Fire Red"},
                {"Platinum", "Pokemon Platinum"},
                {"HeartGold", "Pokemon HeartGold"},
                {"White", "Pokemon White"},
                {"White 2", "Pokemon White 2"},
        };
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
        romHandler.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());
        return romHandler;
    }

    /**
     * With a type-theme foe setting active (KEEP_THEMED for deterministic canonical types), each Gym
     * Leader's reward TM must teach a move of that gym's assigned type.
     */
    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void gymLeaderTMsMatchAssignedType_whenKeepThemed(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        Map<String, Integer> gymTMs = romHandler.getGymLeaderTMs();
        assumeFalse(gymTMs.isEmpty(), "No gym-leader TM data for " + gameName);

        Settings s = new Settings();
        s.setTmsMod(Settings.TMsMod.RANDOM);
        s.setTrainersMod(Settings.TrainersMod.KEEP_THEMED);
        s.setGymLeaderTMsFollowTheme(true);

        TMTutorMoveRandomizer tmRandomizer = new TMTutorMoveRandomizer(romHandler, s, RND);
        tmRandomizer.randomizeTMMoves();

        TrainerPokemonRandomizer trainerRandomizer = new TrainerPokemonRandomizer(romHandler, s, RND);
        trainerRandomizer.randomizeTrainerPokes();
        Map<String, Type> themes = trainerRandomizer.getGymAndEliteThemesUsed();

        tmRandomizer.typeLockGymLeaderTMs(themes);

        List<Integer> tmMoves = romHandler.getTMMoves();
        List<Move> allMoves = romHandler.getMoves();
        int tmCount = romHandler.getTMCount();
        int checked = 0;
        for (Map.Entry<String, Integer> entry : gymTMs.entrySet()) {
            Type expected = themes.get(entry.getKey());
            int tmNumber = entry.getValue();
            if (expected == null || tmNumber < 1 || tmNumber > tmCount) {
                continue; // gym has no assigned theme (e.g. HGSS GYM16/Blue), or TM absent in this game
            }
            Move move = allMoves.get(tmMoves.get(tmNumber - 1));
            assertEquals(expected, move.type, gameName + " " + entry.getKey() + " TM" + tmNumber
                    + " should teach a " + expected + " move but was " + move.name + " (" + move.type + ")");
            checked++;
        }
        assertTrue(checked > 0, "Expected at least one gym-leader TM to be type-locked for " + gameName);
    }

    /**
     * The type-lock step must only touch Gym Leader TM slots; every other TM is left exactly as the
     * base randomization produced it.
     */
    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void typeLockOnlyTouchesGymLeaderTMs(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        Map<String, Integer> gymTMs = romHandler.getGymLeaderTMs();
        assumeFalse(gymTMs.isEmpty(), "No gym-leader TM data for " + gameName);

        Settings s = new Settings();
        s.setTmsMod(Settings.TMsMod.RANDOM);
        s.setTrainersMod(Settings.TrainersMod.KEEP_THEMED);
        s.setGymLeaderTMsFollowTheme(true);

        TMTutorMoveRandomizer tmRandomizer = new TMTutorMoveRandomizer(romHandler, s, RND);
        tmRandomizer.randomizeTMMoves();
        List<Integer> tmsBefore = List.copyOf(romHandler.getTMMoves());

        TrainerPokemonRandomizer trainerRandomizer = new TrainerPokemonRandomizer(romHandler, s, RND);
        trainerRandomizer.randomizeTrainerPokes();
        tmRandomizer.typeLockGymLeaderTMs(trainerRandomizer.getGymAndEliteThemesUsed());

        List<Integer> tmsAfter = romHandler.getTMMoves();
        for (int i = 0; i < tmsAfter.size(); i++) {
            if (gymTMs.containsValue(i + 1)) {
                continue; // this TM slot may have been type-locked
            }
            assertEquals(tmsBefore.get(i), tmsAfter.get(i),
                    gameName + ": non-gym TM" + (i + 1) + " should be untouched by the type-lock step");
        }
    }
}
