package com.uprfvx.random.log;

/*----------------------------------------------------------------------------*/
/*--  Part of "Universal Pokemon Randomizer ZX" by the UPR-ZX team          --*/
/*--  Originally part of "Universal Pokemon Randomizer" by Dabomstew        --*/
/*--  Pokemon and any associated names and the like are                     --*/
/*--  trademark and (C) Nintendo 1996-2020.                                 --*/
/*--                                                                        --*/
/*--  The custom code written here is licensed under the terms of the GPL:  --*/
/*--                                                                        --*/
/*--  This program is free software: you can redistribute it and/or modify  --*/
/*--  it under the terms of the GNU General Public License as published by  --*/
/*--  the Free Software Foundation, either version 3 of the License, or     --*/
/*--  (at your option) any later version.                                   --*/
/*--                                                                        --*/
/*--  This program is distributed in the hope that it will be useful,       --*/
/*--  but WITHOUT ANY WARRANTY; without even the implied warranty of        --*/
/*--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the          --*/
/*--  GNU General Public License for more details.                          --*/
/*--                                                                        --*/
/*--  You should have received a copy of the GNU General Public License     --*/
/*--  along with this program. If not, see <http://www.gnu.org/licenses/>.  --*/
/*----------------------------------------------------------------------------*/

import com.uprfvx.random.Settings;
import com.uprfvx.random.Version;
import com.uprfvx.random.random.RandomSource;
import com.uprfvx.romio.gamedata.BreedingInfo;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import com.uprfvx.romio.gamedata.basestats.Gen1BaseStats;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a live, fully-randomised {@link RomHandler} and emits a complete JSON description of the
 * game as a single String. Unlike {@link RandomizationLogger}, which is a change log gated on
 * {@code isChangesMade()}, this dumps full state unconditionally — the only legitimate reason to
 * omit a field is a capability the current game/generation genuinely lacks.
 */
public class GameDocumentationWriter {

    private final RandomSource randomSource;
    private final Settings settings;
    private final RomHandler romHandler;
    private final StringBuilder out = new StringBuilder();
    private final JsonWriter w = new JsonWriter(out);

    public GameDocumentationWriter(RandomSource randomSource, Settings settings, RomHandler romHandler) {
        this.randomSource = randomSource;
        this.settings = settings;
        this.romHandler = romHandler;
    }

    public String write() {
        w.beginObject();
        w.name("schemaVersion").value(1);
        writeGame();
        writeCapabilities();
        writeSpecies();
        writeTrainers();
        w.endObject();
        return out.toString();
    }

    private void writeGame() {
        w.name("game").beginObject();
        w.name("name").value(romHandler.getROMName());
        w.name("generation").value(romHandler.generationOfPokemon());
        w.name("seed").value(Long.toString(randomSource.getSeed()));
        w.name("randomizerVersion").value(Version.LATEST.branchName + " " + Version.LATEST.name);
        w.name("settingsString").value(settings.toString());
        w.endObject();
    }

    private void writeCapabilities() {
        w.name("capabilities").beginObject();
        w.name("abilitiesPerSpecies").value(romHandler.abilitiesPerSpecies());
        w.name("hasMoveTutors").value(romHandler.hasMoveTutors());
        w.name("hasPhysicalSpecialSplit").value(romHandler.hasPhysicalSpecialSplit());
        w.name("hasTimeBasedEncounters").value(romHandler.hasTimeBasedEncounters());
        w.name("hasTotemPokemon").value(romHandler.hasTotemPokemon());
        w.endObject();
    }

    private void writeSpecies() {
        w.name("species").beginArray();
        boolean gen1 = romHandler.generationOfPokemon() == 1;
        Map<Integer, List<MoveLearnt>> learnsets = romHandler.getMovesLearnt();
        Map<Integer, List<Integer>> eggMoves = romHandler.getEggMoves();
        for (Species pk : romHandler.getSpecies()) {
            if (pk == null) {
                continue;
            }
            writeOneSpecies(pk, gen1, learnsets, eggMoves);
        }
        w.endArray();
    }

    private void writeOneSpecies(Species pk, boolean gen1, Map<Integer, List<MoveLearnt>> learnsets,
                                  Map<Integer, List<Integer>> eggMoves) {
        w.beginObject();
        w.name("number").value(pk.getNumber());
        w.name("name").value(pk.getFullName());

        w.name("types").beginArray();
        w.value(pk.getPrimaryType(false).name());
        if (pk.getSecondaryType(false) != null) {
            w.value(pk.getSecondaryType(false).name());
        }
        w.endArray();

        writeBaseStats(pk, gen1);

        w.name("catchRate").value(pk.getCatchRate());
        w.name("expYield").value(pk.getExpYield());
        w.name("growthCurve").value(pk.getGrowthCurve().name());
        w.name("genderRatio").value(pk.getGenderRatio());

        w.name("abilities").beginArray();
        int abilitiesPerSpecies = romHandler.abilitiesPerSpecies();
        if (abilitiesPerSpecies >= 1) {
            w.value(romHandler.abilityName(pk.getAbility1()));
        }
        if (abilitiesPerSpecies >= 2) {
            w.value(romHandler.abilityName(pk.getAbility2()));
        }
        if (abilitiesPerSpecies >= 3) {
            w.value(romHandler.abilityName(pk.getAbility3()));
        }
        w.endArray();

        w.name("eggGroups").beginArray();
        BreedingInfo breedingInfo = pk.getBreedingInfo();
        if (breedingInfo != null) {
            if (breedingInfo.getPrimaryEggGroup() != null) {
                w.value(breedingInfo.getPrimaryEggGroup().name());
            }
            if (breedingInfo.getSecondaryEggGroup() != null) {
                w.value(breedingInfo.getSecondaryEggGroup().name());
            }
        }
        w.endArray();

        w.name("evolutions").beginArray();
        for (Evolution evo : pk.getEvolutionsFrom()) {
            w.beginObject();
            w.name("to").value(evo.getTo().getNumber());
            w.name("type").value(evo.getType().name());
            w.name("extraInfo").value(evo.getExtraInfo());
            w.name("estimatedLevel").value(evo.getEstimatedEvoLvl());
            w.endObject();
        }
        w.endArray();

        w.name("learnset").beginArray();
        List<MoveLearnt> learnt = learnsets.get(pk.getNumber());
        if (learnt != null) {
            for (MoveLearnt ml : learnt) {
                w.beginObject();
                w.name("level").value(ml.level);
                w.name("move").value(ml.move);
                w.endObject();
            }
        }
        w.endArray();

        w.name("eggMoves").beginArray();
        List<Integer> eggMovesForSpecies = eggMoves.get(pk.getNumber());
        if (eggMovesForSpecies != null) {
            for (int moveId : eggMovesForSpecies) {
                w.value(moveId);
            }
        }
        w.endArray();

        w.endObject();
    }

    /**
     * Only trainers where {@code isBoss() || isImportant()} are emitted — regular trainers are
     * deliberately out of scope for the documentation.
     */
    private void writeTrainers() {
        w.name("trainers").beginArray();
        List<Trainer> trainers = romHandler.getTrainers();
        Map<String, Type> typeThemes = romHandler.getGymAndEliteTypeThemes();
        Map<Integer, Integer> progressionOrderByTrainerIndex = buildProgressionOrderIndex();
        boolean gen7EVsAndNature = romHandler.generationOfPokemon() == 7;
        for (Trainer t : trainers) {
            if (!t.isBoss() && !t.isImportant()) {
                continue;
            }
            writeOneTrainer(t, typeThemes, progressionOrderByTrainerIndex, gen7EVsAndNature);
        }
        w.endArray();
    }

    private Map<Integer, Integer> buildProgressionOrderIndex() {
        Map<Integer, Integer> progressionOrderByTrainerIndex = new HashMap<>();
        List<Integer> mainPlaythroughTrainers = romHandler.getMainPlaythroughTrainers();
        for (int i = 0; i < mainPlaythroughTrainers.size(); i++) {
            progressionOrderByTrainerIndex.put(mainPlaythroughTrainers.get(i), i);
        }
        return progressionOrderByTrainerIndex;
    }

    private void writeOneTrainer(Trainer t, Map<String, Type> typeThemes,
                                  Map<Integer, Integer> progressionOrderByTrainerIndex,
                                  boolean gen7EVsAndNature) {
        w.beginObject();
        w.name("index").value(t.getIndex());
        w.name("name").value(t.getName());
        w.name("fullDisplayName").value(t.getFullDisplayName());
        w.name("tag").value(t.getTag());
        w.name("isBoss").value(t.isBoss());
        w.name("isImportant").value(t.isImportant());

        Type typeTheme = t.getTag() == null ? null : typeThemes.get(t.getTag());
        w.name("typeTheme").value(typeTheme == null ? null : typeTheme.name());

        w.name("levelCap").value(RandomizationLogger.getMaxGymLeaderLevel(t));
        w.name("battleStyle").value(t.getCurrBattleStyle().getStyle().name());

        Integer progressionOrder = progressionOrderByTrainerIndex.get(t.getIndex());
        w.name("progressionOrder").value(progressionOrder == null ? -1 : progressionOrder);

        w.name("pokemon").beginArray();
        for (TrainerPokemon tp : t.getPokemon()) {
            writeOneTrainerPokemon(tp, gen7EVsAndNature);
        }
        w.endArray();

        w.endObject();
    }

    private void writeOneTrainerPokemon(TrainerPokemon tp, boolean gen7EVsAndNature) {
        w.beginObject();
        w.name("level").value(tp.getLevel());
        w.name("species").value(tp.getSpecies().getNumber());

        w.name("moves").beginArray();
        for (int moveId : tp.getMoves()) {
            if (moveId != 0) {
                w.value(moveId);
            }
        }
        w.endArray();

        Item heldItem = tp.getHeldItem();
        w.name("heldItem").value(heldItem == null ? null : heldItem.getName());
        w.name("abilitySlot").value(tp.getAbilitySlot());
        w.name("ivs").value(tp.getIVs());

        // Nature/EVs are only ever written by Gen7RomHandler (USUM); every other generation leaves
        // these fields at their Java default, so emitting them there would be fabricated data.
        if (gen7EVsAndNature) {
            w.name("nature").value(tp.getNature());
            w.name("evs").beginObject();
            w.name("hp").value(tp.getHpEVs());
            w.name("attack").value(tp.getAtkEVs());
            w.name("defense").value(tp.getDefEVs());
            w.name("spatk").value(tp.getSpatkEVs());
            w.name("spdef").value(tp.getSpdefEVs());
            w.name("speed").value(tp.getSpeedEVs());
            w.endObject();
        }

        w.endObject();
    }

    private void writeBaseStats(Species pk, boolean gen1) {
        w.name("bst").value(pk.getBST(false));
        if (gen1) {
            Gen1BaseStats bs = (Gen1BaseStats) pk.getBaseStats();
            w.name("hp").value(bs.getHp());
            w.name("attack").value(bs.getAttack());
            w.name("defense").value(bs.getDefense());
            w.name("special").value(bs.getSpecial());
            w.name("speed").value(bs.getSpeed());
        } else {
            BaseStats bs = pk.getBaseStats();
            w.name("hp").value(bs.getHp());
            w.name("attack").value(bs.getAttack());
            w.name("defense").value(bs.getDefense());
            w.name("spatk").value(bs.getSpatk());
            w.name("spdef").value(bs.getSpdef());
            w.name("speed").value(bs.getSpeed());
        }
    }
}
