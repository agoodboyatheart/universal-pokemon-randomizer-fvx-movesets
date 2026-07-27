package com.uprfvx.random.randomizers;

import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the "Sensible Abilities" exclusion rules (sensible-abilities-design.md).
 * No ROM required - exercises SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor directly against
 * bare Species objects with hand-set types.
 */
public class SensibleAbilitiesRuleTest {

    private Species species(Type primary, Type secondary) {
        Species pk = new Species(1);
        pk.setPrimaryType(primary);
        pk.setSecondaryType(secondary);
        return pk;
    }

    @Test
    public void blazeExcludedOnNonFireSpecies() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.WATER, null), 7);
        assertTrue(banned.contains(AbilityIDs.blaze));
    }

    @Test
    public void blazeAllowedOnFireSpecies() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.FIRE, null), 7);
        assertFalse(banned.contains(AbilityIDs.blaze));
    }

    @Test
    public void droughtExcludedOnPureWaterType() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.WATER, null), 7);
        assertTrue(banned.contains(AbilityIDs.drought));
    }

    @Test
    public void droughtAllowedOnWaterFireDualType() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.WATER, Type.FIRE), 7);
        assertFalse(banned.contains(AbilityIDs.drought));
    }

    @Test
    public void sandStreamExcludedWhenNotGroundRockOrSteelType() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.NORMAL, null), 7);
        assertTrue(banned.contains(AbilityIDs.sandStream));
    }

    @Test
    public void sandStreamAllowedOnSteelType() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.STEEL, null), 7);
        assertFalse(banned.contains(AbilityIDs.sandStream));
    }

    @Test
    public void snowWarningExcludedWhenNotIceType() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.NORMAL, null), 7);
        assertTrue(banned.contains(AbilityIDs.snowWarning));
    }

    @Test
    public void levitateExcludedOnFlyingType() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.NORMAL, Type.FLYING), 7);
        assertTrue(banned.contains(AbilityIDs.levitate));
    }

    @Test
    public void voltAbsorbExcludedOnGroundType() {
        List<Integer> banned = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.GROUND, null), 7);
        assertTrue(banned.contains(AbilityIDs.voltAbsorb));
    }

    @Test
    public void poisonHealExcludedOnPoisonOrSteelType() {
        assertTrue(SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.POISON, null), 7)
                .contains(AbilityIDs.poisonHeal));
        assertTrue(SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.STEEL, null), 7)
                .contains(AbilityIDs.poisonHeal));
    }

    @Test
    public void limberExcludedOnElectricTypeOnlyFromGenSixOnward() {
        List<Integer> gen7 = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.ELECTRIC, null), 7);
        List<Integer> gen5 = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.ELECTRIC, null), 5);
        assertTrue(gen7.contains(AbilityIDs.limber));
        assertFalse(gen5.contains(AbilityIDs.limber));
    }

    @Test
    public void overcoatExcludedOnlyWhenGrassAndAWeatherImmuneTypeBothPresent() {
        List<Integer> grassSteel = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.GRASS, Type.STEEL), 7);
        List<Integer> grassOnly = SpeciesAbilityRandomizer.sensibleBannedAbilitiesFor(species(Type.GRASS, null), 7);
        assertTrue(grassSteel.contains(AbilityIDs.overcoat));
        assertFalse(grassOnly.contains(AbilityIDs.overcoat));
    }
}
