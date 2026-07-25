package com.uprfvx.random;

import com.uprfvx.romio.gamedata.ExpCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSensibleMovesetsTest {

    @Test
    void sensibleMovesetsRoundTripsThroughToStringAndFromString() {
        Settings on = new Settings();
        on.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        on.setRomName("");
        on.setSensibleMovesets(true);
        assertTrue(Settings.fromString(on.toString()).isSensibleMovesets());

        Settings off = new Settings();
        off.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        off.setRomName("");
        off.setSensibleMovesets(false);
        assertFalse(Settings.fromString(off.toString()).isSensibleMovesets());
    }
}
