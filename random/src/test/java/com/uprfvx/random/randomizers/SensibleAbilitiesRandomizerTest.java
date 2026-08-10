package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ROM-level tests for the "Sensible Abilities" setting. SensibleAbilitiesRuleTest already covers the
 * rule table itself against hand-built Species; this class covers the part that test cannot reach -
 * that SpeciesAbilityRandomizer actually applies those rules to every species of a real ROM.
 * <br><br>
 * This class exists only on the sensible-abilities branch, which is deliberate: it is the tripwire's
 * attribution handle for this branch (tools/tripwire_config.json). Because master has no baseline for
 * it, a failure here on local-combined-build is reported as NEW rather than being absorbed into the
 * union of branch noise floors.
 * <br><br>
 * Every run sets abilitiesFollowEvolutions to false. With it on, only basic species get a filtered
 * pick and evolutions copy abilities up from their pre-evo, whose types are not necessarily their own
 * - Dratini (Dragon) may legally roll Levitate and Dragonite (Dragon/Flying) then inherits it. That is
 * a known limitation of the feature, not of the plumbing these tests cover, so it is held out of
 * scope here rather than asserted and failed.
 */
public class SensibleAbilitiesRandomizerTest extends RandomizerTest {

    /**
     * The abilities the two GlobalConstants rule tables are expected to hold. Asserted before the
     * invariants below, because those invariants are driven from the tables: an entry silently lost in
     * a merge of GlobalConstants.java (edited by four branches) would otherwise just quietly stop
     * being checked, and the test would still pass.
     */
    private static final List<Integer> EXPECTED_TYPE_LOCKED = List.of(
            AbilityIDs.blaze, AbilityIDs.torrent, AbilityIDs.overgrow, AbilityIDs.swarm,
            AbilityIDs.steelworker);

    private static final List<Integer> EXPECTED_TYPE_REDUNDANT = List.of(
            AbilityIDs.immunity, AbilityIDs.poisonHeal, AbilityIDs.toxicBoost, AbilityIDs.waterVeil,
            AbilityIDs.flareBoost, AbilityIDs.magmaArmor, AbilityIDs.levitate, AbilityIDs.voltAbsorb,
            AbilityIDs.lightningRod, AbilityIDs.motorDrive);

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void typeLockedAbilitiesOnlyGoToSpeciesWithThatType(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.abilitiesPerSpecies() > 0);

        assertEquals(EXPECTED_TYPE_LOCKED.size(), GlobalConstants.typeLockedAbilities.size(),
                "typeLockedAbilities has the wrong number of entries - was one lost in a merge?");
        for (int ability : EXPECTED_TYPE_LOCKED) {
            assertTrue(GlobalConstants.typeLockedAbilities.containsKey(ability),
                    "typeLockedAbilities is missing ability #" + ability);
        }

        randomizeWithSensibleAbilities(true, RND);

        for (Species pk : randomizedSpecies()) {
            for (int ability : abilitiesOf(pk)) {
                List<Type> requiredTypes = GlobalConstants.typeLockedAbilities.get(ability);
                if (requiredTypes == null) {
                    continue;
                }
                assertTrue(requiredTypes.stream().anyMatch(t -> pk.hasType(t, false)),
                        describe(pk, ability) + " - needs one of " + requiredTypes);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getRomNames")
    public void typeRedundantAbilitiesDoNotGoToSpeciesAlreadyImmune(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.abilitiesPerSpecies() > 0);

        assertEquals(EXPECTED_TYPE_REDUNDANT.size(), GlobalConstants.typeRedundantAbilities.size(),
                "typeRedundantAbilities has the wrong number of entries - was one lost in a merge?");
        for (int ability : EXPECTED_TYPE_REDUNDANT) {
            assertTrue(GlobalConstants.typeRedundantAbilities.containsKey(ability),
                    "typeRedundantAbilities is missing ability #" + ability);
        }

        randomizeWithSensibleAbilities(true, RND);

        for (Species pk : randomizedSpecies()) {
            for (int ability : abilitiesOf(pk)) {
                List<Type> redundantTypes = GlobalConstants.typeRedundantAbilities.get(ability);
                if (redundantTypes == null) {
                    continue;
                }
                for (Type redundant : redundantTypes) {
                    assertFalse(pk.hasType(redundant, false),
                            describe(pk, ability) + " - already immune as a " + redundant + "-type");
                }
            }
        }
    }

    /**
     * The rules SpeciesAbilityRandomizer spells out by hand rather than reading from a table, so a
     * table-driven assertion would not cover them. Limber is excluded here because its rule is
     * generation-dependent; it has its own test below.
     */
    @ParameterizedTest
    @MethodSource("getRomNames")
    public void handWrittenWeatherAndOvercoatRulesHold(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.abilitiesPerSpecies() > 0);

        randomizeWithSensibleAbilities(true, RND);

        for (Species pk : randomizedSpecies()) {
            boolean water = pk.hasType(Type.WATER, false);
            boolean fire = pk.hasType(Type.FIRE, false);
            boolean grass = pk.hasType(Type.GRASS, false);

            for (int ability : abilitiesOf(pk)) {
                if (ability == AbilityIDs.drought) {
                    assertFalse(water && !fire, describe(pk, ability) + " - Water without Fire");
                }
                if (ability == AbilityIDs.drizzle) {
                    assertFalse(fire && !water, describe(pk, ability) + " - Fire without Water");
                }
                if (ability == AbilityIDs.sandStream) {
                    assertTrue(pk.hasType(Type.GROUND, false) || pk.hasType(Type.ROCK, false)
                                    || pk.hasType(Type.STEEL, false),
                            describe(pk, ability) + " - not Ground, Rock or Steel");
                }
                if (ability == AbilityIDs.snowWarning) {
                    assertTrue(pk.hasType(Type.ICE, false), describe(pk, ability) + " - not Ice");
                }
                if (ability == AbilityIDs.overcoat) {
                    boolean weatherImmuneType = pk.hasType(Type.GROUND, false) || pk.hasType(Type.ROCK, false)
                            || pk.hasType(Type.STEEL, false) || pk.hasType(Type.ICE, false);
                    assertFalse(grass && weatherImmuneType,
                            describe(pk, ability) + " - Grass plus a chip-immune type");
                }
            }
        }
    }

    /**
     * Limber is only redundant on an Electric-type from Gen 6 on, when Electric-types became immune to
     * paralysis. Only the positive direction is asserted; SensibleAbilitiesRuleTest already covers that
     * the rule stays off in Gen 3-5, and doing it here would cost ROM runtime for nothing.
     */
    @ParameterizedTest
    @MethodSource("getRomNames")
    public void limberDoesNotGoToElectricTypesFromGenSix(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.generationOfPokemon() >= 6);

        randomizeWithSensibleAbilities(true, RND);

        for (Species pk : randomizedSpecies()) {
            if (pk.hasType(Type.ELECTRIC, false)) {
                assertFalse(abilitiesOf(pk).contains(AbilityIDs.limber),
                        describe(pk, AbilityIDs.limber) + " - Electric-types cannot be paralysed in Gen 6+");
            }
        }
    }

    /**
     * Sensible Abilities must not disturb the Wonder Guard exemption: SpeciesAbilityRandomizer returns
     * early for a species that already has it, and the branch's diff wraps exactly that branch.
     */
    @ParameterizedTest
    @MethodSource("getRomNames")
    public void wonderGuardSpeciesAreLeftAlone(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.abilitiesPerSpecies() > 0);

        Map<Species, List<Integer>> before = new HashMap<>();
        for (Species pk : randomizedSpecies()) {
            if (abilitiesOf(pk).contains(AbilityIDs.wonderGuard)) {
                before.put(pk, abilitiesOf(pk));
            }
        }
        assumeTrue(!before.isEmpty(), "No Wonder Guard species in this ROM");

        randomizeWithSensibleAbilities(true, RND);

        for (Map.Entry<Species, List<Integer>> entry : before.entrySet()) {
            assertEquals(entry.getValue(), abilitiesOf(entry.getKey()),
                    entry.getKey().getFullName() + " had Wonder Guard and should have been skipped");
        }
    }

    /**
     * The gate: with the setting off, the very assignments the other tests forbid must actually occur,
     * otherwise those tests would pass on a ROM where nothing was filtered and prove nothing.
     * Three fixed seeds rather than RND, so this cannot become one of the flaky randomised assertions
     * the tripwire has to work around - and a violation on any one of them is enough.
     */
    @ParameterizedTest
    @MethodSource("getRomNames")
    public void withTheSettingOffNonsensicalAbilitiesDoOccur(String romName) {
        activateRomHandler(romName);
        assumeTrue(romHandler.abilitiesPerSpecies() > 0);

        int violations = 0;
        for (long seed : new long[]{1L, 2L, 3L}) {
            romHandler.reset();
            romHandler.prepare();

            randomizeWithSensibleAbilities(false, new Random(seed));

            for (Species pk : randomizedSpecies()) {
                violations += SpeciesAbilityRandomizer
                        .sensibleBannedAbilitiesFor(pk, romHandler.generationOfPokemon())
                        .stream()
                        .filter(banned -> abilitiesOf(pk).contains(banned))
                        .count();
            }
        }

        assertTrue(violations > 0,
                "Sensible Abilities was off, yet no species got an ability its own type makes useless. "
                        + "The other tests in this class would then hold for the wrong reason.");
    }

    private void randomizeWithSensibleAbilities(boolean sensible, Random random) {
        Settings settings = new Settings();
        settings.setAbilitiesFollowEvolutions(false);
        settings.setAbilitiesFollowMegaEvolutions(false);
        settings.setAllowWonderGuard(false);
        settings.setSensibleAbilities(sensible);

        new SpeciesAbilityRandomizer(romHandler, settings, random).randomizeAbilities();
    }

    /**
     * The species SpeciesAbilityRandomizer actually picks abilities for under these settings. With
     * abilitiesFollowEvolutions off, CopyUpEvolutionsHelper runs the basic action over base formes;
     * everything else either copies from one of those or is left untouched, so asserting over it would
     * be asserting about data this randomizer never wrote.
     */
    private SpeciesSet randomizedSpecies() {
        return romHandler.getSpeciesSetInclFormes().filter(Species::isBaseForme);
    }

    private List<Integer> abilitiesOf(Species pk) {
        List<Integer> abilities = new ArrayList<>();
        for (int ability : new int[]{pk.getAbility1(), pk.getAbility2(), pk.getAbility3()}) {
            if (ability != 0) {
                abilities.add(ability);
            }
        }
        return abilities;
    }

    private String describe(Species pk, int ability) {
        return pk.getFullName() + " (" + pk.getPrimaryType(false)
                + (pk.hasSecondaryType(false) ? "/" + pk.getSecondaryType(false) : "")
                + ") got " + romHandler.abilityName(ability);
    }
}
