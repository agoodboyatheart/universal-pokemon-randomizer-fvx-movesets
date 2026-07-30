package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the "Sensible Movesets" power-banding mechanism: a hard sliding ceiling
 * (species-side {@code applySpeciesPowerCeiling}, mirroring Better Movesets' trainer-side
 * {@code applyPowerBandFilter}) plus a soft sliding floor ({@code speciesLevelAppropriatenessWeight},
 * reusing the trainer path's Regular-tier falloff exponent). Replaces the earlier two-band soft-only
 * guideline (species-tmtutor-moveset-redesign.md P10) per Aaron's 2026-07-29 direction: species
 * learnsets should follow the same level-vs-power banding as trainer movesets, not a bespoke shape.
 * No ROM required.
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

    // --- soft floor (speciesLevelAppropriatenessWeight) ---

    @Test
    public void statusAndFixedDamageMovesAlwaysKeepFullWeight() {
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(statusMove(), 1));
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(statusMove(), 50));
    }

    @Test
    public void movesAtOrAboveTheFloorKeepFullWeight() {
        // centerPower(1) = 45 + 50*(1/50) = 46, floor = 46 * 0.75 = 34.5
        Move atFloor = damagingMove(35);
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(atFloor, 1));
    }

    @Test
    public void movesBelowTheFloorAreDemotedByTheRegularExponent() {
        // centerPower(1) = 46, floor = 34.5; a 20 BP move is well below it.
        Move weak = damagingMove(20);
        double expected = Math.pow(20.0 / 34.5, 0.8);
        assertEquals(expected, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(weak, 1), 1e-9);
        assertTrue(expected < 1.0);
    }

    @Test
    public void floorRisesWithLevel() {
        // A 40 BP move sits at/above the Lv1 floor (34.5) but below the Lv50 floor (95*0.75=71.25).
        Move mv = damagingMove(40);
        assertEquals(1.0, SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(mv, 1));
        assertTrue(SpeciesMovesetRandomizer.speciesLevelAppropriatenessWeight(mv, 50) < 1.0);
    }

    // --- hard ceiling (applySpeciesPowerCeiling) ---

    @Test
    public void ceilingRemovesMovesAboveItAtLowLevel() {
        // powerCeiling(1) = max(60, 46*0.95) = 60.
        List<Move> pool = List.of(damagingMove(40), damagingMove(120));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 1);
        assertEquals(1, capped.size());
        assertEquals(40, capped.get(0).power);
    }

    @Test
    public void ceilingWidensAtHighLevel() {
        // powerCeiling(50) = max(60, 95*1.63) = 154.85, so a 120 BP move clears it at Lv50.
        List<Move> pool = List.of(damagingMove(120));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 50);
        assertEquals(1, capped.size());
    }

    @Test
    public void ceilingFallsBackToWiderPoolWhenNarrowPoolIsAllOverCeiling() {
        // The real Fire-Special/Steel-Physical/Psychic-Special case: a type's whole damaging pool is
        // naturally weak-move-poor (e.g. vanilla Fire Red: only 3/12 Fire-type damaging moves are
        // <=60 BP), but the global damaging pool isn't. powerCeiling(1) = 60.
        List<Move> narrow = List.of(damagingMove(120), damagingMove(150));
        List<Move> wider = List.of(damagingMove(40), damagingMove(120));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(narrow, wider, 1);
        assertEquals(1, capped.size());
        assertEquals(40, capped.get(0).power);
    }

    @Test
    public void ceilingNeverEmptiesWhenBothPoolsAreAllOverCeiling() {
        // Even the wider fallback pool has nothing under the ceiling - must still fall back to the
        // unfiltered narrow pool rather than returning empty and forcing a null pick downstream.
        List<Move> narrow = List.of(damagingMove(120), damagingMove(150));
        List<Move> wider = List.of(damagingMove(130));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(narrow, wider, 1);
        assertFalse(capped.isEmpty());
        assertEquals(2, capped.size());
    }

    @Test
    public void ceilingPrefersNarrowPoolWhenItAlreadySatisfiesTheCeiling() {
        // The wider pool must never override a narrow pool that already has a valid (under-ceiling)
        // candidate - the fallback only kicks in when the narrow pool is exhausted.
        List<Move> narrow = List.of(damagingMove(40));
        List<Move> wider = List.of(damagingMove(35));
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(narrow, wider, 1);
        assertEquals(1, capped.size());
        assertEquals(40, capped.get(0).power);
    }

    @Test
    public void ceilingExemptsStatusAndFixedDamageMoves() {
        List<Move> pool = List.of(statusMove());
        List<Move> capped = SpeciesMovesetRandomizer.applySpeciesPowerCeiling(pool, List.of(), 1);
        assertEquals(1, capped.size());
    }
}
