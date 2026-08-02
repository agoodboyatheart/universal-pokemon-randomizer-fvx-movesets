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
import com.uprfvx.romio.gamedata.Encounter;
import com.uprfvx.romio.gamedata.EncounterArea;
import com.uprfvx.romio.gamedata.EncounterType;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.InGameTrade;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.gamedata.MoveLearnt;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.StaticEncounter;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import com.uprfvx.romio.gamedata.basestats.Gen1BaseStats;
import com.uprfvx.romio.romhandlers.RomHandler;

import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final List<StaticEncounter> originalStatics;
    private final List<InGameTrade> originalTrades;
    private final StringBuilder out = new StringBuilder();
    private final JsonWriter w = new JsonWriter(out);

    /**
     * @param originalStatics The pre-randomisation static Pokemon snapshot (see
     *                        {@link RandomizationLogger#getOriginalStatics()}), used to label
     *                        statics by the vanilla species they replaced — the ROM records no
     *                        location data to identify a static encounter by otherwise. Null if
     *                        the game can't have its statics randomised at all.
     * @param originalTrades The pre-randomisation in-game trade snapshot (see
     *                       {@link RandomizationLogger#getOriginalTrades()}), used to label trades
     *                       by the vanilla Pokemon the NPC originally gave — the ROM records no
     *                       location data to identify a trade NPC by otherwise.
     */
    public GameDocumentationWriter(RandomSource randomSource, Settings settings, RomHandler romHandler,
                                    List<StaticEncounter> originalStatics, List<InGameTrade> originalTrades) {
        this.randomSource = randomSource;
        this.settings = settings;
        this.romHandler = romHandler;
        this.originalStatics = originalStatics;
        this.originalTrades = originalTrades;
    }

    public String write() {
        w.beginObject();
        w.name("schemaVersion").value(1);
        writeGame();
        writeCapabilities();
        writeSpecies();
        writeStarters();
        writeTrainers();
        writeEncounters();
        writeTmsAndTutors();
        writeMoves();
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

    private void writeStarters() {
        w.name("starters").beginArray();
        List<Species> starters = romHandler.getStarters();
        List<Item> heldItems = romHandler.getStarterHeldItems();
        for (int i = 0; i < starters.size(); i++) {
            w.beginObject();
            w.name("slot").value(i + 1);
            w.name("species").value(starters.get(i).getNumber());
            Item heldItem = resolveStarterHeldItem(heldItems, starters.size(), i);
            w.name("heldItem").value(heldItem == null ? null : heldItem.getName());
            w.endObject();
        }
        w.endArray();
    }

    /**
     * heldItems is either one entry shared by every starter, one entry per starter, or empty —
     * mirrors the exact shapes RandomizationLogger.logStarters() already handles.
     */
    private static Item resolveStarterHeldItem(List<Item> heldItems, int starterCount, int index) {
        if (heldItems.size() == 1) {
            return heldItems.get(0);
        }
        if (heldItems.size() == starterCount) {
            return heldItems.get(index);
        }
        return null;
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

    /**
     * Trainer.getTag() carries a per-battle suffix ("GYM1-LEADER", "ELITE1-1") that the theme
     * map's keys ("GYM1", "ELITE1") don't have; TrainerPokemonRandomizer.getTrainerGroups()
     * strips the same way, and folds Giovanni's Team Rocket boss tag into his gym's group.
     */
    private static Type resolveTypeTheme(String tag, Map<String, Type> typeThemes) {
        if (tag == null || typeThemes == null) {
            return null;
        }
        String group = tag.contains("-") ? tag.substring(0, tag.indexOf('-')) : tag;
        if (group.startsWith("GIO")) {
            group = "GYM8";
        }
        return typeThemes.get(group);
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

        Type typeTheme = resolveTypeTheme(t.getTag(), typeThemes);
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

    private void writeEncounters() {
        w.name("encounters").beginObject();
        writeEncounterAreas();
        writeStatics();
        writeTrades();
        w.endObject();
    }

    /**
     * Areas with {@code encounterType == UNUSED} are dummy ROM slots (see the identical skip in
     * {@code RandomizationLogger.logWildPokemon()}) and are not real encounters.
     */
    private void writeEncounterAreas() {
        w.name("areas").beginArray();
        List<EncounterArea> areas = romHandler.getSortedEncounters(romHandler.hasTimeBasedEncounters());
        int progressionOrder = 0;
        for (EncounterArea area : areas) {
            if (area.getEncounterType() == EncounterType.UNUSED) {
                continue;
            }
            writeOneArea(area, progressionOrder);
            progressionOrder++;
        }
        w.endArray();
    }

    private void writeOneArea(EncounterArea area, int progressionOrder) {
        w.beginObject();
        w.name("displayName").value(area.getDisplayName());
        w.name("locationTag").value(area.getLocationTag());
        w.name("encounterType").value(area.getEncounterType().name());
        w.name("rate").value(area.getRate());
        w.name("postGame").value(area.isPostGame());
        w.name("progressionOrder").value(progressionOrder);
        w.name("slotCount").value(area.size());

        // Aggregate duplicate slots per species into one entry with a slot count and level range —
        // never a percentage, since no generation's constants file records per-slot probabilities.
        Map<Integer, Integer> slotCountBySpeciesNumber = new LinkedHashMap<>();
        Map<Integer, int[]> levelRangeBySpeciesNumber = new HashMap<>();
        for (Encounter e : area) {
            int number = e.getSpecies().getNumber();
            int lo = e.getLevel();
            int hi = e.getMaxLevel() > 0 ? e.getMaxLevel() : e.getLevel();
            int[] range = levelRangeBySpeciesNumber.get(number);
            if (range == null) {
                levelRangeBySpeciesNumber.put(number, new int[] { lo, hi });
                slotCountBySpeciesNumber.put(number, 1);
            } else {
                range[0] = Math.min(range[0], lo);
                range[1] = Math.max(range[1], hi);
                slotCountBySpeciesNumber.put(number, slotCountBySpeciesNumber.get(number) + 1);
            }
        }

        w.name("species").beginArray();
        for (Map.Entry<Integer, Integer> entry : slotCountBySpeciesNumber.entrySet()) {
            int number = entry.getKey();
            int[] range = levelRangeBySpeciesNumber.get(number);
            w.beginObject();
            w.name("number").value(number);
            w.name("minLevel").value(range[0]);
            w.name("maxLevel").value(range[1]);
            w.name("slots").value(entry.getValue());
            w.endObject();
        }
        w.endArray();

        w.endObject();
    }

    private void writeStatics() {
        w.name("statics").beginArray();
        List<StaticEncounter> statics = romHandler.getStaticPokemon();
        List<Integer> mainGameLegendaries = romHandler.hasMainGameLegendaries()
                ? romHandler.getMainGameLegendaries() : null;
        for (int i = 0; i < statics.size(); i++) {
            writeOneStatic(statics.get(i), i, mainGameLegendaries);
        }
        w.endArray();
    }

    private void writeOneStatic(StaticEncounter se, int index, List<Integer> mainGameLegendaries) {
        w.beginObject();

        Integer vanillaSpecies = null;
        if (originalStatics != null && index < originalStatics.size()) {
            vanillaSpecies = originalStatics.get(index).getSpecies().getNumber();
        }
        writeNullableInt("vanillaSpecies", vanillaSpecies);

        w.name("species").value(se.getSpecies().getNumber());
        w.name("level").value(se.getLevel());
        Item heldItem = se.getHeldItem();
        w.name("heldItem").value(heldItem == null ? null : heldItem.getName());
        w.name("isEgg").value(se.isEgg());
        w.name("isLegendary").value(se.getSpecies().isLegendary());
        boolean mainGame = mainGameLegendaries != null
                && mainGameLegendaries.contains(se.getSpecies().getBaseForme().getNumber());
        w.name("mainGame").value(mainGame);

        w.endObject();
    }

    private void writeTrades() {
        w.name("trades").beginArray();
        List<InGameTrade> trades = romHandler.getInGameTrades();
        for (int i = 0; i < trades.size(); i++) {
            writeOneTrade(trades.get(i), i);
        }
        w.endArray();
    }

    private void writeOneTrade(InGameTrade trade, int index) {
        w.beginObject();

        Integer vanillaGivenSpecies = null;
        if (originalTrades != null && index < originalTrades.size()) {
            vanillaGivenSpecies = originalTrades.get(index).getGivenSpecies().getNumber();
        }
        writeNullableInt("vanillaGivenSpecies", vanillaGivenSpecies);

        Species requested = trade.getRequestedSpecies();
        writeNullableInt("requestedSpecies", requested == null ? null : requested.getNumber());
        w.name("givenSpecies").value(trade.getGivenSpecies().getNumber());
        w.name("nickname").value(trade.getNickname());
        w.endObject();
    }

    private void writeNullableInt(String name, Integer value) {
        w.name(name);
        if (value == null) {
            w.nullValue();
        } else {
            w.value(value.longValue());
        }
    }

    private void writeTmsAndTutors() {
        List<Integer> tmMoves = romHandler.getTMMoves();
        List<Integer> hmMoves = romHandler.getHMMoves();
        boolean hasMoveTutors = romHandler.hasMoveTutors();
        List<Integer> tutorMoves = hasMoveTutors ? romHandler.getMoveTutorMoves() : List.of();

        writeNumberedMoveList("tms", tmMoves);
        writeNumberedMoveList("hms", hmMoves);

        w.name("tutors").beginArray();
        for (int moveId : tutorMoves) {
            w.beginObject();
            w.name("move").value(moveId);
            w.endObject();
        }
        w.endArray();

        writeCompatibility(tmMoves, hmMoves, hasMoveTutors, tutorMoves);
    }

    private void writeNumberedMoveList(String name, List<Integer> moveIds) {
        w.name(name).beginArray();
        for (int i = 0; i < moveIds.size(); i++) {
            w.beginObject();
            w.name("number").value(i + 1);
            w.name("move").value(moveIds.get(i));
            w.endObject();
        }
        w.endArray();
    }

    /**
     * Sparse index lists, not boolean arrays — a dense compatibility matrix bloats the file for no
     * gain. {@code tmhm} indices are 1-based and continuous over the underlying flags array: TMs
     * occupy {@code 1..tmMoves.size()}, HMs continue at {@code tmMoves.size()+1..}. {@code tutor}
     * indices are 1-based over the tutor moves list alone.
     */
    private void writeCompatibility(List<Integer> tmMoves, List<Integer> hmMoves, boolean hasMoveTutors,
                                     List<Integer> tutorMoves) {
        w.name("compatibility").beginObject();

        Map<Species, boolean[]> tmhmCompat = romHandler.getTMHMCompatibility();
        w.name("tmhm").beginObject();
        int tmhmCount = tmMoves.size() + hmMoves.size();
        for (Species pk : romHandler.getSpecies()) {
            if (pk == null) {
                continue;
            }
            boolean[] flags = tmhmCompat.get(pk);
            w.name(String.valueOf(pk.getNumber())).beginArray();
            if (flags != null) {
                for (int i = 1; i <= tmhmCount && i < flags.length; i++) {
                    if (flags[i]) {
                        w.value(i);
                    }
                }
            }
            w.endArray();
        }
        w.endObject();

        w.name("tutor").beginObject();
        if (hasMoveTutors) {
            Map<Species, boolean[]> tutorCompat = romHandler.getMoveTutorCompatibility();
            for (Species pk : romHandler.getSpecies()) {
                if (pk == null) {
                    continue;
                }
                boolean[] flags = tutorCompat.get(pk);
                w.name(String.valueOf(pk.getNumber())).beginArray();
                if (flags != null) {
                    for (int i = 1; i <= tutorMoves.size() && i < flags.length; i++) {
                        if (flags[i]) {
                            w.value(i);
                        }
                    }
                }
                w.endArray();
            }
        }
        w.endObject();

        w.endObject();
    }

    /**
     * {@code category} is already correctly resolved at ROM-load time for the current generation
     * (type-derived pre-split, real per-move data post-split) — no extra logic needed here; the
     * renderer decides how to present it when {@code !hasPhysicalSpecialSplit()}.
     */
    private void writeMoves() {
        w.name("moves").beginArray();
        for (Move m : romHandler.getMoves()) {
            if (m == null) {
                continue;
            }
            w.beginObject();
            w.name("id").value(m.number);
            w.name("name").value(m.name);
            w.name("type").value(m.type == null ? null : m.type.name());
            w.name("category").value(m.category == null ? null : m.category.name());
            w.name("power").value(m.power);
            w.name("accuracy").value(m.hitratio);
            w.name("pp").value(m.pp);
            w.endObject();
        }
        w.endArray();
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
