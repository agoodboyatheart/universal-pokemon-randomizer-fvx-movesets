package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Standalone (non-{@link RandomizerTest}) invariant tests for trainer held-item randomization.
 * Named to match {@code *Randomizer*Test} so it runs under {@code testROMs}. Deliberately does NOT
 * extend {@link RandomizerTest}: that base class's shared {@code @BeforeAll} loads every ROM from
 * {@code getRomNames()} (USA region only) into one static fixture, which fails outright in this dev
 * environment because the Gen6/7 dumps present are non-USA-named (e.g. Alpha Sapphire is the Europe
 * decrypted dump) - see the {@code test-roms-harness} memory. Loading only the specific ROMs each
 * test needs, by explicit file name, avoids that entirely (same pattern as
 * {@link BetterMovesetsGoldenMasterRandomizerTest} and {@link TMTutorMoveRandomizerTest}).
 */
public class SensibleHeldItemsInvariantsRandomizerTest {

    private static final long SEED = 20260724L;
    private static final String ROMS_PATH = System.getProperty("romsPath");

    /** One game per generation - reuses the exact set already proven to load in this environment. */
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

    RomHandler loadRom(String gameName, String fileBaseName) {
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

    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void bossAndImportantTrainersGetUniqueHeldItemsByDefault(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        assumeTrue(romHandler.canAddHeldItemsToBossTrainers() || romHandler.canAddHeldItemsToImportantTrainers());

        Settings s = new Settings();
        s.setRandomizeHeldItemsForBossTrainerPokemon(true);
        s.setRandomizeHeldItemsForImportantTrainerPokemon(true);
        s.setRandomizeHeldItemsForRegularTrainerPokemon(false);
        s.setSensibleItemsOnlyForTrainers(true);
        new TrainerPokemonRandomizer(romHandler, s, new Random(SEED)).randomizeTrainerHeldItems();

        for (Trainer tr : romHandler.getTrainers()) {
            if (tr.shouldNotGetBuffs() || !(tr.isBoss() || tr.isImportant())) {
                continue;
            }
            System.out.println(tr);
            for (TrainerPokemon tp : tr.getPokemon()) {
                System.out.println("\t" + tp.getSpecies().getName() + " @ " + tp.getHeldItem());
            }
            assertTrue(tr.pokemonHaveUniqueHeldItems(),
                    tr.getFullDisplayName() + " has a duplicate held item within its team.");
        }
    }
}
