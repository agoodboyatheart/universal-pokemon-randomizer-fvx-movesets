package com.uprfvx.random;

import com.uprfvx.romio.gamedata.ExpCurve;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class SettingsStaticTradeFilterFlagsTest {

    private Settings newSettings() {
        Settings s = new Settings();
        s.setRomName("Test ROM");
        s.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        return s;
    }

    @Test
    public void staticBasicOnlyRoundTripsTrue() {
        Settings s = newSettings();
        s.setStaticBasicOnly(true);

        Settings restored = Settings.fromString(s.toString());

        assertEquals(true, restored.isStaticBasicOnly());
    }

    @Test
    public void staticBasicOnlyRoundTripsFalse() {
        Settings s = newSettings();
        s.setStaticBasicOnly(false);

        Settings restored = Settings.fromString(s.toString());

        assertFalse(restored.isStaticBasicOnly());
    }

    @Test
    public void staticBasicOnlyDoesNotCorruptByte64Neighbors() {
        Settings s = newSettings();
        s.setBalanceShopPrices(true);
        s.setAddCheapRareCandiesToShops(true);
        s.setStaticBasicOnly(true);

        Settings restored = Settings.fromString(s.toString());

        assertEquals(true, restored.isBalanceShopPrices());
        assertEquals(true, restored.isAddCheapRareCandiesToShops());
        assertEquals(true, restored.isStaticBasicOnly());
    }

    @Test
    public void tradeFiltersRoundTripTrue() {
        Settings s = newSettings();
        s.setTradeSimilarStrength(true);
        s.setTradeBasicOnly(true);
        s.setTradeNoLegendaries(true);

        Settings restored = Settings.fromString(s.toString());

        assertEquals(true, restored.isTradeSimilarStrength());
        assertEquals(true, restored.isTradeBasicOnly());
        assertEquals(true, restored.isTradeNoLegendaries());
    }

    @Test
    public void tradeFiltersRoundTripFalse() {
        Settings s = newSettings();
        s.setTradeSimilarStrength(false);
        s.setTradeBasicOnly(false);
        s.setTradeNoLegendaries(false);

        Settings restored = Settings.fromString(s.toString());

        assertFalse(restored.isTradeSimilarStrength());
        assertFalse(restored.isTradeBasicOnly());
        assertFalse(restored.isTradeNoLegendaries());
    }

    @Test
    public void tradeFiltersDoNotCorruptByte64Neighbors() {
        Settings s = newSettings();
        s.setBalanceShopPrices(true);
        s.setAddCheapRareCandiesToShops(true);
        s.setStaticBasicOnly(true);
        s.setTradeSimilarStrength(true);
        s.setTradeBasicOnly(true);
        s.setTradeNoLegendaries(true);

        Settings restored = Settings.fromString(s.toString());

        assertEquals(true, restored.isBalanceShopPrices());
        assertEquals(true, restored.isAddCheapRareCandiesToShops());
        assertEquals(true, restored.isStaticBasicOnly());
        assertEquals(true, restored.isTradeSimilarStrength());
        assertEquals(true, restored.isTradeBasicOnly());
        assertEquals(true, restored.isTradeNoLegendaries());
    }
}
