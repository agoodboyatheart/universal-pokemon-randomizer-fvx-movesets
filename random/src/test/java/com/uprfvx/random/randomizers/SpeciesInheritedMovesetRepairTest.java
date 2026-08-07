package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the Follow Evolutions inherited-moveset repair (see
 * project_memory/species-movesets-inherited-stab-repair-design.md). An evolved species whose slot count
 * is at most its pre-evolution's never runs randomizeMovesLearntForSpecies at all, so its STAB and
 * attacking guarantees are re-checked afterwards against its own types - real-Pearl data found Scizor
 * (Bug/Steel inheriting a Bug/Flying Scyther's all-Flying picks) and Togekiss (4 slots, truncating
 * Togepi's kit one slot before its only attacker) failing exactly this way. No ROM required: both halves
 * of the check are package-private statics over plain move data.
 */
public class SpeciesInheritedMovesetRepairTest {

    private static final int FLOOR = Randomizer.SPECIES_STAB_FLOOR_LEVEL;

    private final List<Move> allMoves = new ArrayList<>();

    public SpeciesInheritedMovesetRepairTest() {
        allMoves.add(null); // move IDs are 1-based
    }

    private int addMove(Type type, MoveCategory category) {
        Move mv = new Move();
        mv.number = allMoves.size();
        mv.type = type;
        mv.category = category;
        mv.power = category == MoveCategory.STATUS ? 0 : 70;
        allMoves.add(mv);
        return mv.number;
    }

    // Bug/Steel, i.e. Scizor's own typing, whatever its pre-evolution's was.
    private SpeciesLearnsetProfile bugSteelProfile() {
        return new SpeciesLearnsetProfile(0.375, List.of(Type.BUG, Type.STEEL), Set.of(), 1.0, 0.5, null, 1.0);
    }

    private List<MoveLearnt> learnsetOf(int... moveThenLevelPairs) {
        List<MoveLearnt> moves = new ArrayList<>();
        for (int i = 0; i < moveThenLevelPairs.length; i += 2) {
            moves.add(new MoveLearnt(moveThenLevelPairs[i], moveThenLevelPairs[i + 1]));
        }
        return moves;
    }

    @Test
    public void invariantFailsWhenEveryInheritedMoveIsOffType() {
        // Scizor's real failure: Scyther's STAB slots all drew Flying, which Scizor does not share.
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        int skyAttack = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(peck, 1, skyAttack, 25);

        assertFalse(SpeciesMovesetRandomizer.satisfiesInheritedInvariant(
                moves, bugSteelProfile(), true, allMoves));
    }

    @Test
    public void invariantHoldsWhenAnOnTypeDamagingMoveLandsWithinTheFloor() {
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        int bugBite = addMove(Type.BUG, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(peck, 1, bugBite, FLOOR);

        assertTrue(SpeciesMovesetRandomizer.satisfiesInheritedInvariant(
                moves, bugSteelProfile(), true, allMoves));
    }

    @Test
    public void invariantFailsWhenTheOnlyOnTypeMoveIsAboveTheFloor() {
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        int ironHead = addMove(Type.STEEL, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(peck, 1, ironHead, FLOOR + 1);

        assertFalse(SpeciesMovesetRandomizer.satisfiesInheritedInvariant(
                moves, bugSteelProfile(), true, allMoves));
    }

    @Test
    public void invariantFailsWhenEveryInheritedMoveIsStatus() {
        // Togekiss' real failure: a 4-slot learnset truncating a status-heavy Togepi's picks.
        int growl = addMove(Type.NORMAL, MoveCategory.STATUS);
        int charm = addMove(Type.NORMAL, MoveCategory.STATUS);
        List<MoveLearnt> moves = learnsetOf(growl, 1, charm, 1);

        assertFalse(SpeciesMovesetRandomizer.satisfiesInheritedInvariant(
                moves, bugSteelProfile(), true, allMoves));
        assertFalse(SpeciesMovesetRandomizer.satisfiesInheritedInvariant(
                moves, bugSteelProfile(), false, allMoves));
    }

    @Test
    public void invariantIgnoresTypeAndLevelWhenTypeStructureIsOff() {
        // Plain Random: the player asked for type-blind learnsets, so any damaging move satisfies it.
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(peck, FLOOR + 20);

        assertTrue(SpeciesMovesetRandomizer.satisfiesInheritedInvariant(
                moves, bugSteelProfile(), false, allMoves));
    }

    @Test
    public void repairSlotPrefersTheEarliestOffTypeAttackerInTheFloorBand() {
        int growl = addMove(Type.NORMAL, MoveCategory.STATUS);
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        int wingAttack = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(growl, 1, peck, 10, wingAttack, 20);

        // Re-typing an attacker leaves the species' status share exactly where the copy put it.
        assertEquals(1, SpeciesMovesetRandomizer.inheritedRepairSlot(moves, moves.size(), allMoves));
    }

    @Test
    public void repairSlotFallsBackToTheEarliestStatusSlotWhenTheBandHasNoAttacker() {
        int growl = addMove(Type.NORMAL, MoveCategory.STATUS);
        int charm = addMove(Type.NORMAL, MoveCategory.STATUS);
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(growl, 5, charm, 15, peck, FLOOR + 10);

        assertEquals(0, SpeciesMovesetRandomizer.inheritedRepairSlot(moves, moves.size(), allMoves));
    }

    @Test
    public void repairSlotFallsBackToTheEarliestRealSlotWhenNothingIsInTheBand() {
        // No slot at or below the floor at all - placing STAB late still beats never placing it, the same
        // last resort ensureEarlyStabFloor takes.
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        int growl = addMove(Type.NORMAL, MoveCategory.STATUS);
        List<MoveLearnt> moves = learnsetOf(peck, FLOOR + 5, growl, FLOOR + 15);

        assertEquals(0, SpeciesMovesetRandomizer.inheritedRepairSlot(moves, moves.size(), allMoves));
    }

    @Test
    public void repairSlotSkipsEvolutionMoveSlots() {
        int evoMove = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(evoMove, 0, peck, 5);

        assertEquals(1, SpeciesMovesetRandomizer.inheritedRepairSlot(moves, moves.size(), allMoves));
    }

    @Test
    public void repairSlotNeverReachesTheIndependentlyRandomizedTail() {
        // A longer evolved species' leftover slots already ran their own role assignment under its own
        // types; only the inherited prefix is fair game.
        int growl = addMove(Type.NORMAL, MoveCategory.STATUS);
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(growl, 5, peck, 10);

        assertEquals(0, SpeciesMovesetRandomizer.inheritedRepairSlot(moves, 1, allMoves));
    }

    @Test
    public void repairSlotReturnsNoSlotWhenThereIsNothingInherited() {
        int peck = addMove(Type.FLYING, MoveCategory.PHYSICAL);
        List<MoveLearnt> moves = learnsetOf(peck, 5);

        assertEquals(-1, SpeciesMovesetRandomizer.inheritedRepairSlot(moves, 0, allMoves));
    }
}
