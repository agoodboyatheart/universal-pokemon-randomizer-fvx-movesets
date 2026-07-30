package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that Starters/Totem/Static/Trade(given) never overlap each other or Wild, when
 * wired in the order GameRandomizer.applyRandomizers() uses. GameRandomizer's own orchestration
 * method is private and untestable directly - this replicates the same call sequence by hand.
 * See project_memory\encounter-anti-duplication-design.md.
 */
public class EncounterAntiDuplicationRandomizerTest extends RandomizerTest {

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void starterTotemStaticTradeAndWildNeverOverlap(String romName) {
        activateRomHandler(romName);

        Settings s = new Settings();
        s.setStartersMod(Settings.StartersMod.COMPLETELY_RANDOM);
        s.setTotemPokemonMod(Settings.TotemPokemonMod.RANDOM);
        // Allies must be randomized too, else they keep their vanilla species - which Starters (picked
        // first) can't have known to avoid, so a coincidental overlap would be reported as a failure.
        s.setAllyPokemonMod(Settings.AllyPokemonMod.RANDOM);
        s.setStaticPokemonMod(Settings.StaticPokemonMod.COMPLETELY_RANDOM);
        s.setInGameTradesMod(Settings.InGameTradesMod.RANDOMIZE_GIVEN);
        s.setRandomizeWildPokemon(true);
        s.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NONE);
        s.setUseTimeBasedEncounters(true);

        SpeciesSet claimed = new SpeciesSet();

        new StarterRandomizer(romHandler, s, RND).randomizeStarters();
        SpeciesSet starterSpecies = new SpeciesSet(romHandler.getStarters());
        claimed.addAll(starterSpecies);

        SpeciesSet totemSpecies = new SpeciesSet();
        if (romHandler.hasTotemPokemon()) {
            StaticPokemonRandomizer totemRandomizer = new StaticPokemonRandomizer(romHandler, s, RND);
            totemRandomizer.setExternallyClaimedSpecies(claimed);
            totemRandomizer.randomizeTotemPokemon();
            for (TotemPokemon totem : romHandler.getTotemPokemon()) {
                totemSpecies.add(totem.getSpecies());
                for (StaticEncounter ally : totem.getAllies().values()) {
                    totemSpecies.add(ally.getSpecies());
                }
            }
            assertDisjoint(starterSpecies, totemSpecies, "Starters", "Totems");
            claimed.addAll(totemSpecies);
        }

        SpeciesSet staticSpecies = new SpeciesSet();
        if (romHandler.canChangeStaticPokemon()) {
            StaticPokemonRandomizer staticRandomizer = new StaticPokemonRandomizer(romHandler, s, RND);
            staticRandomizer.setExternallyClaimedSpecies(claimed);
            staticRandomizer.randomizeStaticPokemon();
            for (StaticEncounter se : romHandler.getStaticPokemon()) {
                staticSpecies.add(se.getSpecies());
            }
            assertDisjoint(starterSpecies, staticSpecies, "Starters", "Statics");
            assertDisjoint(totemSpecies, staticSpecies, "Totems", "Statics");
            claimed.addAll(staticSpecies);
        }

        TradeRandomizer tradeRandomizer = new TradeRandomizer(romHandler, s, RND);
        tradeRandomizer.setExternallyClaimedSpecies(claimed);
        tradeRandomizer.randomizeGivenTrades();
        SpeciesSet tradeGivenSpecies = new SpeciesSet();
        for (InGameTrade trade : romHandler.getInGameTrades()) {
            tradeGivenSpecies.add(trade.getGivenSpecies());
        }
        // Rule 4: the four claimed pools are mutually disjoint.
        assertDisjoint(starterSpecies, tradeGivenSpecies, "Starters", "Trade(given)");
        assertDisjoint(totemSpecies, tradeGivenSpecies, "Totems", "Trade(given)");
        assertDisjoint(staticSpecies, tradeGivenSpecies, "Statics", "Trade(given)");
        claimed.addAll(tradeGivenSpecies);

        WildEncounterRandomizer wildRandomizer = new WildEncounterRandomizer(romHandler, s, RND);
        wildRandomizer.setExternallyClaimedSpecies(claimed);
        wildRandomizer.randomizeEncounters();

        // Rules 2/3: none of the claimed species show up in the wild.
        for (EncounterArea area : romHandler.getEncounters(true)) {
            // UNUSED areas are deliberately left un-randomized (see prepEncounterAreas), so they still
            // hold vanilla species the randomizer never wrote - out of scope for this guarantee.
            if (area.getEncounterType() == EncounterType.UNUSED || "UNUSED".equals(area.getLocationTag())) {
                continue;
            }
            for (Encounter enc : area) {
                assertTrue(!claimed.contains(enc.getSpecies()),
                        enc.getSpecies().getFullName() + " is claimed elsewhere but appears in the wild");
            }
        }
    }

    private void assertDisjoint(SpeciesSet a, SpeciesSet b, String aName, String bName) {
        SpeciesSet overlap = new SpeciesSet(a);
        overlap.retainAll(b);
        assertTrue(overlap.isEmpty(),
                aName + " and " + bName + " both use: " + overlap.stream().map(Species::getFullName).toList());
    }
}
