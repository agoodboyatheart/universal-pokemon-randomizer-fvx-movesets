package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.InGameTrade;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class TradeRandomizerTest extends RandomizerTest {

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void basicOnlyOnlyPicksBasicPokemon(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        s.setTradeBasicOnly(true);
        new TradeRandomizer(romHandler, s, RND).randomizeIngameTrades();

        for (InGameTrade trade : romHandler.getInGameTrades()) {
            assertBasic(trade.getGivenSpecies());
            if (trade.getRequestedSpecies() != null) {
                assertBasic(trade.getRequestedSpecies());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void noLegendariesExcludesLegendaries(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        s.setTradeNoLegendaries(true);
        new TradeRandomizer(romHandler, s, RND).randomizeIngameTrades();

        for (InGameTrade trade : romHandler.getInGameTrades()) {
            assertFalse(trade.getGivenSpecies().isLegendary(), trade.getGivenSpecies().getFullName() + " is legendary");
            if (trade.getRequestedSpecies() != null) {
                assertFalse(trade.getRequestedSpecies().isLegendary(),
                        trade.getRequestedSpecies().getFullName() + " is legendary");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void similarStrengthDoesNotThrow(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        s.setTradeSimilarStrength(true);
        new TradeRandomizer(romHandler, s, RND).randomizeIngameTrades();
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void combinedFiltersDoNotHangOrThrow(String romName) {
        // Regression test for the drain-and-refill dedup fix: with Basic Only + Exclude Legendaries
        // both on, the species pool can be small, and the old regenerate-until-unique loop risked
        // spinning or throwing on ROMs with many trades.
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN_AND_REQUESTED);
        s.setTradeBasicOnly(true);
        s.setTradeNoLegendaries(true);
        new TradeRandomizer(romHandler, s, RND).randomizeIngameTrades();
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void claimedSpeciesAreExcludedFromGivenTrades(String romName) {
        activateRomHandler(romName);

        List<Species> allSpecies = new ArrayList<>(romHandler.getRestrictedSpeciesService().getAll(false));
        int tradeCount = romHandler.getInGameTrades().size();
        SpeciesSet claimed = new SpeciesSet(allSpecies.subList(0, allSpecies.size() - tradeCount - 5));

        Settings s = new Settings();
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN);
        TradeRandomizer randomizer = new TradeRandomizer(romHandler, s, RND);
        randomizer.setExternallyClaimedSpecies(claimed);
        randomizer.randomizeGivenTrades();

        for (InGameTrade trade : romHandler.getInGameTrades()) {
            assertFalse(claimed.contains(trade.getGivenSpecies()),
                    trade.getGivenSpecies().getFullName() + " was claimed elsewhere but given in a trade");
        }
    }

    private void assertBasic(Species pk) {
        assertEquals(0, pk.getPreEvolvedSpecies(false).size(), pk.getFullName() + " is not a basic Pokemon");
    }

}
