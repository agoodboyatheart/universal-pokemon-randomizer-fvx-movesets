package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for backfill/relearner-move detection (species-tmtutor-moveset-redesign.md P11-P12).
 * No ROM required - uses synthetic Species/Evolution fixtures.
 */
public class SpeciesBackfillMoveDetectionTest {

    private static final int VINE_WHIP = 101;
    private static final int TACKLE = 102;
    private static final int GROWL = 103;
    private static final int RAZOR_LEAF = 104; // Ivysaur-only, never learned by Bulbasaur

    @Test
    public void levelOneMoveMatchingPrevoLearnsetIsFlaggedAtTheEvolutionLevel() {
        Species bulbasaur = new Species(1);
        Species ivysaur = new Species(2);
        Evolution evo = new Evolution(bulbasaur, ivysaur, EvolutionType.LEVEL, 16, 16);
        ivysaur.getEvolutionsTo().add(evo);

        Map<Integer, Set<Integer>> vanillaMoveIdsBySpecies = new HashMap<>();
        vanillaMoveIdsBySpecies.put(bulbasaur.getNumber(), new HashSet<>(List.of(TACKLE, GROWL, VINE_WHIP)));

        List<MoveLearnt> ivysaurMoves = new ArrayList<>();
        ivysaurMoves.add(new MoveLearnt(TACKLE, 1));    // backfill: Bulbasaur already knew this
        ivysaurMoves.add(new MoveLearnt(VINE_WHIP, 1)); // backfill: Bulbasaur already knew this
        ivysaurMoves.add(new MoveLearnt(RAZOR_LEAF, 1)); // NOT backfill: genuinely new at level 1
        ivysaurMoves.add(new MoveLearnt(GROWL, 32));    // not level 1, irrelevant either way

        Map<Integer, Integer> result = SpeciesMovesetRandomizer.computeBackfillEffectiveLevels(
                ivysaur, ivysaurMoves, 0, vanillaMoveIdsBySpecies);

        assertEquals(Map.of(0, 16, 1, 16), result);
    }

    @Test
    public void baseStageSpeciesWithNoPrevoIsNeverFlagged() {
        Species bulbasaur = new Species(1); // no getEvolutionsTo() entries added

        List<MoveLearnt> moves = new ArrayList<>();
        moves.add(new MoveLearnt(TACKLE, 1));
        moves.add(new MoveLearnt(GROWL, 1));

        Map<Integer, Integer> result = SpeciesMovesetRandomizer.computeBackfillEffectiveLevels(
                bulbasaur, moves, 0, new HashMap<>());

        assertTrue(result.isEmpty());
    }

    @Test
    public void paddedFakeLevelOneSlotWithMoveIdZeroIsNeverFlagged() {
        Species bulbasaur = new Species(1);
        Species ivysaur = new Species(2);
        Evolution evo = new Evolution(bulbasaur, ivysaur, EvolutionType.LEVEL, 16, 16);
        ivysaur.getEvolutionsTo().add(evo);

        Map<Integer, Set<Integer>> vanillaMoveIdsBySpecies = new HashMap<>();
        vanillaMoveIdsBySpecies.put(bulbasaur.getNumber(), new HashSet<>(List.of(TACKLE)));

        List<MoveLearnt> ivysaurMoves = new ArrayList<>();
        ivysaurMoves.add(new MoveLearnt(0, 1)); // padMovesetSlots-style fake slot, move id 0

        Map<Integer, Integer> result = SpeciesMovesetRandomizer.computeBackfillEffectiveLevels(
                ivysaur, ivysaurMoves, 0, vanillaMoveIdsBySpecies);

        assertFalse(result.containsKey(0));
    }

    @Test
    public void slotsBeforeStartIndexAreIgnored() {
        Species bulbasaur = new Species(1);
        Species ivysaur = new Species(2);
        Evolution evo = new Evolution(bulbasaur, ivysaur, EvolutionType.LEVEL, 16, 16);
        ivysaur.getEvolutionsTo().add(evo);

        Map<Integer, Set<Integer>> vanillaMoveIdsBySpecies = new HashMap<>();
        vanillaMoveIdsBySpecies.put(bulbasaur.getNumber(), new HashSet<>(List.of(TACKLE)));

        List<MoveLearnt> ivysaurMoves = new ArrayList<>();
        ivysaurMoves.add(new MoveLearnt(TACKLE, 1)); // index 0, would match, but startIndex=1 skips it

        Map<Integer, Integer> result = SpeciesMovesetRandomizer.computeBackfillEffectiveLevels(
                ivysaur, ivysaurMoves, 1, vanillaMoveIdsBySpecies);

        assertTrue(result.isEmpty());
    }
}
