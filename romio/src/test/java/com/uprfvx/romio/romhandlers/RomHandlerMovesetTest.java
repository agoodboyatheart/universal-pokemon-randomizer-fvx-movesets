package com.uprfvx.romio.romhandlers;

import com.uprfvx.romio.gamedata.Species;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class RomHandlerMovesetTest extends RomHandlerTest {



    @ParameterizedTest
    @MethodSource("getRomNames")
    public void eggMovesDoNoNotChangeWithGetAndSet(String romName) {
        assumeFalse(getGenerationNumberOf(romName) == 1);
        loadROM(romName);
        Map<Integer, List<Integer>> before = deepCopyEggMoves(romHandler.getEggMoves());
        romHandler.setEggMoves(romHandler.getEggMoves());
        Map<Integer, List<Integer>> after = romHandler.getEggMoves();
        assertEquals(before, after);
    }

    private Map<Integer, List<Integer>> deepCopyEggMoves(Map<Integer, List<Integer>> original) {
        return original.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new java.util.ArrayList<>(entry.getValue())
                ));
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void tmhmCompatibilityDoesNotChangeWithGetAndSet(String romName) {
        loadROM(romName);
        Map<Species, boolean[]> inverted = invertAllFlags(romHandler.getTMHMCompatibility());
        Map<Species, List<Boolean>> expected = deepCopyCompatibility(inverted);
        romHandler.setTMHMCompatibility(inverted);
        assertEquals(expected, deepCopyCompatibility(romHandler.getTMHMCompatibility()));
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void moveTutorCompatibilityDoesNotChangeWithGetAndSet(String romName) {
        loadROM(romName);
        // Without tutors both sides are an empty map, so the assertion would pass without testing anything.
        assumeTrue(romHandler.hasMoveTutors());
        Map<Species, boolean[]> inverted = invertAllFlags(romHandler.getMoveTutorCompatibility());
        Map<Species, List<Boolean>> expected = deepCopyCompatibility(inverted);
        romHandler.setMoveTutorCompatibility(inverted);
        assertEquals(expected, deepCopyCompatibility(romHandler.getMoveTutorCompatibility()));
    }

    /**
     * Inverts every compatibility flag, so the data written back differs from what was read.
     * Writing the ROM's own data back is a fixed point for any bug that can only turn flags on,
     * so a plain get-then-set round trip would not detect one.
     */
    private Map<Species, boolean[]> invertAllFlags(Map<Species, boolean[]> original) {
        Map<Species, boolean[]> inverted = new java.util.LinkedHashMap<>();
        for (Map.Entry<Species, boolean[]> entry : original.entrySet()) {
            boolean[] flags = entry.getValue().clone();
            // Index 0 is unused padding - it is never written, so leave it alone.
            for (int i = 1; i < flags.length; i++) {
                flags[i] = !flags[i];
            }
            inverted.put(entry.getKey(), flags);
        }
        return inverted;
    }

    /**
     * Copies compatibility data into a form that compares by value; boolean[] compares by reference,
     * so asserting on the raw maps would always fail.
     */
    private Map<Species, List<Boolean>> deepCopyCompatibility(Map<Species, boolean[]> original) {
        Map<Species, List<Boolean>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<Species, boolean[]> entry : original.entrySet()) {
            List<Boolean> flags = new java.util.ArrayList<>();
            for (boolean flag : entry.getValue()) {
                flags.add(flag);
            }
            copy.put(entry.getKey(), flags);
        }
        return copy;
    }

}
