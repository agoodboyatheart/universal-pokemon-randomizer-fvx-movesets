package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * {@link SensibleHeldItemsGoldenMasterRandomizerTest}, and as the {@code better-movesets} branch's
 * {@code BetterMovesetsGoldenMasterRandomizerTest}/{@code TMTutorMoveRandomizerTest}, not present
 * on this branch).
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

    private static int[] resolveMoveset(RomHandler romHandler, TrainerPokemon tp) {
        return tp.isResetMoves()
                ? romHandler.getMovesAtLevel(tp.getSpecies(), romHandler.getMovesLearnt(), tp.getLevel())
                : tp.getMoves();
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

    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void sensibleItemsDoNotGiveLifeOrbOrAssaultVestBelowLevel20(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        assumeTrue(Generation.GAME_TO_GENERATION.get(gameName).getNumber() >= 4);

        List<Move> moves = romHandler.getMoves();
        for (Trainer tr : romHandler.getTrainers()) {
            for (TrainerPokemon tp : tr.getPokemon()) {
                if (tp.getLevel() >= 20) {
                    continue;
                }
                int[] moveset = resolveMoveset(romHandler, tp);
                List<Item> sensible = romHandler.getSensibleHeldItemsFor(tp, false, moves, moveset, new Random(SEED));
                for (Item item : sensible) {
                    if (item == null) continue;
                    assertFalse(item.getName().equals("Life Orb"), tp.getSpecies().getName() + " at Lv"
                            + tp.getLevel() + " has Life Orb in its sensible-item pool.");
                    assertFalse(item.getName().equals("Assault Vest"), tp.getSpecies().getName() + " at Lv"
                            + tp.getLevel() + " has Assault Vest in its sensible-item pool.");
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("gamesToVerify")
    public void choiceItemsOnlyOfferedToAllAttackingMovesets(String gameName, String fileBaseName) {
        RomHandler romHandler = loadRom(gameName, fileBaseName);
        assumeTrue(Generation.GAME_TO_GENERATION.get(gameName).getNumber() >= 3);

        List<Move> moves = romHandler.getMoves();
        Set<String> choiceItemNames = Set.of("Choice Band", "Choice Specs", "Choice Scarf");
        for (Trainer tr : romHandler.getTrainers()) {
            for (TrainerPokemon tp : tr.getPokemon()) {
                int[] moveset = resolveMoveset(romHandler, tp);
                boolean hasStatusMove = false;
                for (int moveIdx : moveset) {
                    Move move = moves.get(moveIdx);
                    if (move != null && move.category == MoveCategory.STATUS) {
                        hasStatusMove = true;
                        break;
                    }
                }
                if (!hasStatusMove) {
                    continue;
                }
                List<Item> sensible = romHandler.getSensibleHeldItemsFor(tp, false, moves, moveset, new Random(SEED));
                for (Item item : sensible) {
                    if (item == null) continue;
                    assertFalse(choiceItemNames.contains(item.getName()),
                            tp.getSpecies().getName() + " knows a status move but " + item.getName()
                                    + " is still in its sensible-item pool.");
                }
            }
        }
    }
}
