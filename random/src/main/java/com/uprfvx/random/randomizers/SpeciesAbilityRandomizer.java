package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.constants.AbilityIDs;
import com.uprfvx.romio.constants.Gen3Constants;
import com.uprfvx.romio.constants.GlobalConstants;
import com.uprfvx.romio.gamedata.MegaEvolution;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SpeciesAbilityRandomizer extends Randomizer {

    public SpeciesAbilityRandomizer(RomHandler romHandler, Settings settings, Random random) {
        super(romHandler, settings, random);
    }

    public void randomizeAbilities() {
        boolean evolutionSanity = settings.isAbilitiesFollowEvolutions();
        boolean allowWonderGuard = settings.isAllowWonderGuard();
        boolean banTrappingAbilities = settings.isBanTrappingAbilities();
        boolean banNegativeAbilities = settings.isBanNegativeAbilities();
        boolean banBadAbilities = settings.isBanBadAbilities();
        boolean megaEvolutionSanity = settings.isAbilitiesFollowMegaEvolutions();
        boolean weighDuplicatesTogether = settings.isWeighDuplicateAbilitiesTogether();
        boolean ensureTwoAbilities = settings.isEnsureTwoAbilities();
        boolean isMultiBattleOnly = settings.getBattleStyle().isOnlyMultiBattles();

        // Abilities don't exist in some games...
        if (romHandler.abilitiesPerSpecies() == 0) {
            return;
        }

        final boolean hasHiddenAbilities = (romHandler.abilitiesPerSpecies() == 3);

        final List<Integer> bannedAbilities = romHandler.getUselessAbilities();

        if (!allowWonderGuard) {
            bannedAbilities.add(AbilityIDs.wonderGuard);
        }

        if (banTrappingAbilities) {
            bannedAbilities.addAll(GlobalConstants.battleTrappingAbilities);
        }

        if (banNegativeAbilities) {
            bannedAbilities.addAll(GlobalConstants.negativeAbilities);
        }

        if (banBadAbilities) {
            bannedAbilities.addAll(GlobalConstants.badAbilities);
            if (!isMultiBattleOnly) {
                bannedAbilities.addAll(GlobalConstants.doubleBattleAbilities);
            }
        }

        if (weighDuplicatesTogether) {
            bannedAbilities.addAll(GlobalConstants.duplicateAbilities);
            if (romHandler.generationOfPokemon() == 3) {
                bannedAbilities.add(Gen3Constants.airLockIndex); // Special case for Air Lock in Gen 3
            }
        }

        final int maxAbility = romHandler.highestAbilityIndex();

        // copy abilities straight up evolution lines
        // still keep WG as an exception, though
        copyUpEvolutionsHelper.apply(evolutionSanity, false, pk -> {
            if (pk.getAbility1() != AbilityIDs.wonderGuard && pk.getAbility2() != AbilityIDs.wonderGuard
                    && pk.getAbility3() != AbilityIDs.wonderGuard) {
                // Pick first ability
                pk.setAbility1(pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether));

                // Second ability?
                if (ensureTwoAbilities || random.nextDouble() < 0.5) {
                    // Yes, second ability
                    pk.setAbility2(pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether,
                            pk.getAbility1()));
                } else {
                    // Nope
                    pk.setAbility2(0);
                }

                // Third ability?
                if (hasHiddenAbilities) {
                    pk.setAbility3(pickRandomAbility(maxAbility, bannedAbilities, weighDuplicatesTogether,
                            pk.getAbility1(), pk.getAbility2()));
                }
            }
        }, (evFrom, evTo, toMonIsFinalEvo) -> {
            if (evTo.getAbility1() != AbilityIDs.wonderGuard && evTo.getAbility2() != AbilityIDs.wonderGuard
                    && evTo.getAbility3() != AbilityIDs.wonderGuard) {
                evTo.setAbility1(evFrom.getAbility1());
                evTo.setAbility2(evFrom.getAbility2());
                evTo.setAbility3(evFrom.getAbility3());
            }
        });


        romHandler.getSpeciesSetInclFormes().filter(Species::isEssentiallyCosmetic)
                .forEach(pk -> pk.copyBaseFormeAbilities(pk.getConceptualBaseForme()));

        if (megaEvolutionSanity) {
            for (MegaEvolution megaEvo : romHandler.getMegaEvolutions()) {
                if (megaEvo.getFrom().getMegaEvolutionsFrom().size() > 1)
                    continue;
                megaEvo.getTo().setAbility1(megaEvo.getFrom().getAbility1());
                megaEvo.getTo().setAbility2(megaEvo.getFrom().getAbility2());
                megaEvo.getTo().setAbility3(megaEvo.getFrom().getAbility3());
            }
        }

        changesMade = true;
    }

    /**
     * Sensible Abilities (opt-in): the additional abilities to exclude for this specific species,
     * on top of the global bannedAbilities list, because they are provably non-functional or
     * self-sabotaging given the species' own type. See project_memory\sensible-abilities-design.md
     * for the full rule table and rationale. Pure function (no RomHandler/Settings dependency) so
     * it can be unit-tested directly - see SensibleAbilitiesRuleTest.
     */
    static List<Integer> sensibleBannedAbilitiesFor(Species pk, int generation) {
        List<Integer> banned = new ArrayList<>();

        for (Map.Entry<Integer, List<Type>> entry : GlobalConstants.typeLockedAbilities.entrySet()) {
            boolean hasRequiredType = entry.getValue().stream().anyMatch(t -> pk.hasType(t, false));
            if (!hasRequiredType) {
                banned.add(entry.getKey());
            }
        }

        if (pk.hasType(Type.WATER, false) && !pk.hasType(Type.FIRE, false)) {
            banned.add(AbilityIDs.drought);
        }
        if (pk.hasType(Type.FIRE, false) && !pk.hasType(Type.WATER, false)) {
            banned.add(AbilityIDs.drizzle);
        }
        if (!pk.hasType(Type.GROUND, false) && !pk.hasType(Type.ROCK, false) && !pk.hasType(Type.STEEL, false)) {
            banned.add(AbilityIDs.sandStream);
        }
        if (!pk.hasType(Type.ICE, false)) {
            banned.add(AbilityIDs.snowWarning);
        }

        for (Map.Entry<Integer, List<Type>> entry : GlobalConstants.typeRedundantAbilities.entrySet()) {
            boolean hasRedundantType = entry.getValue().stream().anyMatch(t -> pk.hasType(t, false));
            if (hasRedundantType) {
                banned.add(entry.getKey());
            }
        }
        if (pk.hasType(Type.ELECTRIC, false) && generation >= 6) {
            banned.add(AbilityIDs.limber);
        }
        if (pk.hasType(Type.GRASS, false) && (pk.hasType(Type.GROUND, false) || pk.hasType(Type.ROCK, false)
                || pk.hasType(Type.STEEL, false) || pk.hasType(Type.ICE, false))) {
            banned.add(AbilityIDs.overcoat);
        }

        return banned;
    }

    private int pickRandomAbilityVariation(int selectedAbility, int... alreadySetAbilities) {
        int newAbility = selectedAbility;

        while (true) {
            Map<Integer, List<Integer>> abilityVariations = romHandler.getAbilityVariations();
            for (int baseAbility: abilityVariations.keySet()) {
                if (selectedAbility == baseAbility) {
                    List<Integer> variationsForThisAbility = abilityVariations.get(selectedAbility);
                    newAbility = variationsForThisAbility.get(random.nextInt(variationsForThisAbility.size()));
                    break;
                }
            }

            boolean repeat = false;
            for (int alreadySetAbility : alreadySetAbilities) {
                if (alreadySetAbility == newAbility) {
                    repeat = true;
                    break;
                }
            }

            if (!repeat) {
                break;
            }
        }

        return newAbility;
    }

    private int pickRandomAbility(int maxAbility, List<Integer> bannedAbilities, boolean useVariations,
                                  int... alreadySetAbilities) {
        int newAbility;

        while (true) {
            newAbility = random.nextInt(maxAbility) + 1;

            if (bannedAbilities.contains(newAbility)) {
                continue;
            }

            boolean repeat = false;
            for (int alreadySetAbility : alreadySetAbilities) {
                if (alreadySetAbility == newAbility) {
                    repeat = true;
                    break;
                }
            }

            if (!repeat) {
                if (useVariations) {
                    newAbility = pickRandomAbilityVariation(newAbility, alreadySetAbilities);
                }
                break;
            }
        }

        return newAbility;
    }
}
