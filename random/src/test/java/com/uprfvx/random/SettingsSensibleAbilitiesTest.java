package com.uprfvx.random;

import com.uprfvx.romio.gamedata.ExpCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsSensibleAbilitiesTest {

    @Test
    void sensibleAbilitiesRoundTripsThroughToStringAndFromString() {
        Settings on = new Settings();
        on.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        on.setRomName("");
        on.setSensibleAbilities(true);
        assertTrue(Settings.fromString(on.toString()).isSensibleAbilities());

        Settings off = new Settings();
        off.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        off.setRomName("");
        off.setSensibleAbilities(false);
        assertFalse(Settings.fromString(off.toString()).isSensibleAbilities());
    }
}
