package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the STAB-specific fallback chain added against the real-Pearl Scizor zero-STAB gap
 * (project_memory/species-movesets-shape-and-stab-guarantee-design.md Fix B): STAB must try both of a
 * dual-type species' own types before ever widening to an off-type move. No ROM required -
 * SpeciesMovesetRandomizer.MovePools is directly constructible.
 */
public class SpeciesStabFallbackTest {

    private static final int SEED_COUNT = 200;

    private Move moveOf(int number, Type type, int power) {
        Move mv = new Move();
        mv.number = number;
        mv.type = type;
        mv.category = MoveCategory.PHYSICAL;
        mv.power = power;
        return mv; // hitCount defaults to 1
    }

    @Test
    public void stabCandidatesFallsBackToTheOtherOwnTypeWhenTheFirstIsExhausted() {
        // Scizor-shaped: Bug/Steel, with the Bug pool fully learnt-out and the Steel pool still holding a
        // real move - stabCandidates must never widen off-type while Steel is still available.
        Move bugMove = moveOf(42, Type.BUG, 90);
        Move steelMove = moveOf(99, Type.STEEL, 65);
        Map<Type, List<Move>> typeDamaging = Map.of(Type.BUG, List.of(bugMove), Type.STEEL, List.of(steelMove));
        SpeciesMovesetRandomizer.MovePools pools = new SpeciesMovesetRandomizer.MovePools(
                List.of(bugMove, steelMove), List.of(bugMove, steelMove), List.of(), Map.of(), typeDamaging);
        SpeciesLearnsetProfile profile = new SpeciesLearnsetProfile(0.375, List.of(Type.BUG, Type.STEEL),
                Set.of(), 1.0, 0.5, null, 1.0);
        List<Integer> learnt = List.of(bugMove.number); // Bug's only move already used

        for (long seed = 0; seed < SEED_COUNT; seed++) {
            List<Move> candidates = SpeciesMovesetRandomizer.stabCandidates(profile, learnt, pools, new Random(seed));
            assertEquals(1, candidates.size(), "seed " + seed);
            assertEquals(Type.STEEL, candidates.get(0).type, "seed " + seed + " widened off-type instead of "
                    + "falling back to the species' own Steel pool");
        }
    }

    @Test
    public void stabCandidatesReturnsEmptyOnlyWhenBothOwnTypesAreExhausted() {
        Move bugMove = moveOf(42, Type.BUG, 90);
        Map<Type, List<Move>> typeDamaging = Map.of(Type.BUG, List.of(bugMove));
        SpeciesMovesetRandomizer.MovePools pools = new SpeciesMovesetRandomizer.MovePools(
                List.of(bugMove), List.of(bugMove), List.of(), Map.of(), typeDamaging);
        SpeciesLearnsetProfile profile = new SpeciesLearnsetProfile(0.375, List.of(Type.BUG, Type.STEEL),
                Set.of(), 1.0, 0.5, null, 1.0);
        List<Integer> learnt = List.of(bugMove.number);

        List<Move> candidates = SpeciesMovesetRandomizer.stabCandidates(profile, learnt, pools, new Random(0));
        assertTrue(candidates.isEmpty());
    }

    @Test
    public void stabCandidatesDegradesToTheSingleTypeCheckForAMonoTypeSpecies() {
        Move fireMove = moveOf(10, Type.FIRE, 70);
        Map<Type, List<Move>> typeDamaging = Map.of(Type.FIRE, List.of(fireMove));
        SpeciesMovesetRandomizer.MovePools pools = new SpeciesMovesetRandomizer.MovePools(
                List.of(fireMove), List.of(fireMove), List.of(), Map.of(), typeDamaging);
        SpeciesLearnsetProfile profile = new SpeciesLearnsetProfile(0.375, List.of(Type.FIRE),
                Set.of(), 1.0, 0.5, null, 1.0);

        List<Move> candidates = SpeciesMovesetRandomizer.stabCandidates(profile, List.of(), pools, new Random(0));
        assertEquals(List.of(fireMove), candidates);
    }
}
