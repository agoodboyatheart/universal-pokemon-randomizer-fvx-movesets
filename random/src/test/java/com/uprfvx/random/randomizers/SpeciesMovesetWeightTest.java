package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the "Sensible Movesets" two-band power guideline
 * (species-tmtutor-moveset-redesign.md P10). No ROM required.
 */
public class SpeciesMovesetWeightTest {

    private Move damagingMove(int power) {
        Move mv = new Move();
        mv.power = power;
        mv.category = MoveCategory.PHYSICAL;
        return mv; // hitCount defaults to 1
    }

    private Move statusMove() {
        Move mv = new Move();
        mv.power = 0;
        mv.category = MoveCategory.STATUS;
        return mv;
    }

    @Test
    public void statusAndFixedDamageMovesAlwaysKeepFullWeight() {
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(statusMove(), 1));
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(statusMove(), 50));
    }

    @Test
    public void lowBpMovesKeepFullWeightUntilTheLateTaper() {
        Move weak = damagingMove(40);
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(weak, 1));
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(weak, 44));
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(weak, 45));
    }

    @Test
    public void sixtyBpExactlyStillCountsAsLowBand() {
        Move exactlySixty = damagingMove(60);
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(exactlySixty, 1));
    }

    @Test
    public void lowBpMovesTaperToAFloorByLevelFiftyButNeverToZero() {
        Move weak = damagingMove(40);
        assertEquals(0.5, SpeciesMovesetRandomizer.sensibleMovesetWeight(weak, 50), 1e-9);
        assertEquals(0.5, SpeciesMovesetRandomizer.sensibleMovesetWeight(weak, 80), 1e-9);
        double atFortySeven = SpeciesMovesetRandomizer.sensibleMovesetWeight(weak, 47);
        assertTrue(atFortySeven > 0.5 && atFortySeven < 1.0,
                "Expected a mid-taper weight strictly between the floor and full weight, was " + atFortySeven);
    }

    @Test
    public void highBpMovesAreRareButPossibleBelowLevelThirty() {
        Move strong = damagingMove(120);
        assertEquals(0.12, SpeciesMovesetRandomizer.sensibleMovesetWeight(strong, 1), 1e-9);
        assertEquals(0.12, SpeciesMovesetRandomizer.sensibleMovesetWeight(strong, 30), 1e-9);
    }

    @Test
    public void highBpMovesRampToFullWeightByLevelFifty() {
        Move strong = damagingMove(120);
        double atForty = SpeciesMovesetRandomizer.sensibleMovesetWeight(strong, 40);
        assertTrue(atForty > 0.12 && atForty < 1.0,
                "Expected a mid-ramp weight strictly between the floor and full weight, was " + atForty);
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(strong, 50), 1e-9);
        assertEquals(1.0, SpeciesMovesetRandomizer.sensibleMovesetWeight(strong, 80), 1e-9);
    }
}
