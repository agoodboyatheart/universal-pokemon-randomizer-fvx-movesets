package com.uprfvx.random.randomizers;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the vanilla-ratio-centered statusShare sampling added against
 * project_memory/species-movesets-shape-and-stab-guarantee-design.md Fix A. No ROM or Species object
 * required - sampleStatusShare is a standalone sampling function.
 */
public class SpeciesLearnsetProfileTest {

    @Test
    public void sampleStatusShareTracksItsGivenCenterRatherThanTheGlobalMean() {
        // Deliberately far from the old fixed global mean (0.375), safely inside the clamp bounds
        // (0.08-0.72) so truncation doesn't swamp the signal.
        double target = 0.55;
        Random random = new Random(20260807L);
        double sum = 0;
        int trials = 20000;
        for (int i = 0; i < trials; i++) {
            sum += SpeciesLearnsetProfile.sampleStatusShare(target, random);
        }
        double mean = sum / trials;
        assertTrue(Math.abs(mean - target) < Math.abs(mean - Randomizer.SPECIES_STATUS_SHARE_MEAN),
                "sampled mean " + mean + " tracked the old global mean (" + Randomizer.SPECIES_STATUS_SHARE_MEAN
                        + ") instead of the given target " + target);
    }

    @Test
    public void sampleStatusShareStaysWithinTheClampBounds() {
        Random random = new Random(1L);
        for (int i = 0; i < 5000; i++) {
            // Center pushed above SPECIES_STATUS_SHARE_MAX on purpose to exercise the clamp.
            double share = SpeciesLearnsetProfile.sampleStatusShare(0.95, random);
            assertTrue(share <= Randomizer.SPECIES_STATUS_SHARE_MAX && share >= Randomizer.SPECIES_STATUS_SHARE_MIN,
                    "share " + share + " escaped the clamp bounds");
        }
    }
}
