package com.uprfvx.random;

import com.uprfvx.romio.gamedata.ExpCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence tests for the two "Sensible ... Compatibility" settings.
 * <p>
 * A setting that works in code but is not saved is a silent bug: the GUI would show the box ticked,
 * the settings string would not carry it, and a loaded preset would quietly randomize with the old
 * model. The two flags share one byte with four pre-existing flags, so the risk worth testing is not
 * "does a boolean survive a round trip" but "does each one survive <i>independently</i>, without
 * disturbing its neighbours".
 * <p>
 * Deliberately not named {@code *Randomizer*Test}: the {@code testROMs} task claims that pattern and
 * the fast {@code test} task excludes it, and this needs no ROM.
 */
public class SensibleCompatibilitySettingsTest {

    /**
     * A default {@link Settings} cannot serialise at all: {@code toStringWithoutVersion}
     * dereferences both {@code selectedEXPCurve} and {@code romName}, and both start null. That is
     * pre-existing and unrelated to these settings - in the GUI both are always populated before a
     * settings string is produced. Neither value matters here, only that they exist.
     */
    private static Settings serialisableSettings() {
        Settings settings = new Settings();
        settings.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        settings.setRomName("TestRom");
        return settings;
    }

    private static Settings roundTrip(Settings settings) {
        return Settings.fromString(settings.toString());
    }

    @Test
    public void bothSettingsDefaultToOff() {
        Settings settings = serialisableSettings();
        assertFalse(settings.isSensibleTMCompatibility());
        assertFalse(settings.isSensibleTutorCompatibility());
    }

    @Test
    public void bothSettingsSurviveARoundTrip() {
        Settings settings = serialisableSettings();
        settings.setSensibleTMCompatibility(true);
        settings.setSensibleTutorCompatibility(true);

        Settings restored = roundTrip(settings);
        assertTrue(restored.isSensibleTMCompatibility());
        assertTrue(restored.isSensibleTutorCompatibility());
    }

    @Test
    public void offSettingsStayOffThroughARoundTrip() {
        Settings restored = roundTrip(serialisableSettings());
        assertFalse(restored.isSensibleTMCompatibility());
        assertFalse(restored.isSensibleTutorCompatibility());
    }

    @Test
    public void theTwoSettingsAreIndependent() {
        Settings tmOnly = serialisableSettings();
        tmOnly.setSensibleTMCompatibility(true);
        Settings restoredTmOnly = roundTrip(tmOnly);
        assertTrue(restoredTmOnly.isSensibleTMCompatibility());
        assertFalse(restoredTmOnly.isSensibleTutorCompatibility());

        Settings tutorOnly = serialisableSettings();
        tutorOnly.setSensibleTutorCompatibility(true);
        Settings restoredTutorOnly = roundTrip(tutorOnly);
        assertFalse(restoredTutorOnly.isSensibleTMCompatibility());
        assertTrue(restoredTutorOnly.isSensibleTutorCompatibility());
    }

    /**
     * The new flags took two spare bits in the byte that already carried these four. Writing past a
     * byte boundary, or reading the wrong bit index back, would corrupt a neighbour rather than fail
     * outright, so they are asserted together.
     */
    @Test
    public void neighbouringFlagsInTheSameByteAreUndisturbed() {
        Settings settings = serialisableSettings();
        settings.setSensibleTMCompatibility(true);
        settings.setSensibleTutorCompatibility(true);
        settings.setFullHMCompat(true);
        settings.setTmsFollowEvolutions(true);
        settings.setTutorFollowEvolutions(true);
        settings.setGymLeaderTMsFollowTheme(true);

        Settings restored = roundTrip(settings);
        assertTrue(restored.isFullHMCompat());
        assertTrue(restored.isTmsFollowEvolutions());
        assertTrue(restored.isTutorFollowEvolutions());
        assertTrue(restored.isGymLeaderTMsFollowTheme());
        assertTrue(restored.isSensibleTMCompatibility());
        assertTrue(restored.isSensibleTutorCompatibility());
    }

    /**
     * The inverse of the above: with the new flags off, the four originals must still round-trip.
     * This is the case that would have caught a bit index collision with an existing flag.
     */
    @Test
    public void neighbouringFlagsRoundTripWithTheNewOnesOff() {
        Settings settings = serialisableSettings();
        settings.setFullHMCompat(true);
        settings.setGymLeaderTMsFollowTheme(true);

        Settings restored = roundTrip(settings);
        assertTrue(restored.isFullHMCompat());
        assertTrue(restored.isGymLeaderTMsFollowTheme());
        assertFalse(restored.isTmsFollowEvolutions());
        assertFalse(restored.isTutorFollowEvolutions());
        assertFalse(restored.isSensibleTMCompatibility());
        assertFalse(restored.isSensibleTutorCompatibility());
    }
}
