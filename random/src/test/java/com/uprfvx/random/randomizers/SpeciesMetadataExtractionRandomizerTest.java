package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.EggGroup;
import com.uprfvx.romio.gamedata.Evolution;
import com.uprfvx.romio.gamedata.EvolutionType;
import com.uprfvx.romio.gamedata.Item;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.basestats.BaseStats;
import com.uprfvx.romio.gamedata.basestats.Gen1BaseStats;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Diagnostic (report-only) harness for the C3 species-categorisation project: dumps species/forme
 * metadata plus the evolution graph straight from real ROM data, for a {@code scratchpad} script
 * to join against PokeAPI/Showdown into {@code reference\metadata_pokemon_species.csv} and
 * {@code reference\metadata_pokemon_species_evolutions.csv}. Asserts nothing - purely a data dump,
 * same spirit as {@link MoveMetadataExtractionRandomizerTest}.
 * <p>
 * Primary ROM is Ultra Sun (full Gen 1-7 forme roster, final base stats/types/abilities). A second,
 * minimal Red ROM load exists only to backfill {@code special} - the Gen 1 pre-split stat, which
 * {@code Gen1RomHandler} is the only RomHandler that ever populates; Ultra Sun alone would leave it
 * blank for every species, not just post-Gen-1 ones.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*SpeciesMetadataExtraction*" }</pre>
 * Output is bracketed by BEGIN/END markers; read it from the test's XML {@code <system-out>} CDATA
 * (not the console) to avoid glyph-mangling on species names, per the project's own
 * {@code -Dgolden.record=true} convention.
 */
public class SpeciesMetadataExtractionRandomizerTest {

    private static final String ROMS_PATH = System.getProperty("romsPath");

    @Test
    public void dumpPlannedSpeciesMetadata() {
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");
        RomHandler ultraSun = tryLoad("Ultra Sun", "Ultra Sun");
        assumeTrue(ultraSun != null, "ROM absent: Ultra Sun");
        RomHandler red = tryLoad("Red", "Red (U)");
        assumeTrue(red != null, "ROM absent: Red");

        Map<Integer, Integer> gen1Special = new HashMap<>();
        for (Species sp : red.getSpecies()) {
            if (sp != null) {
                gen1Special.put(sp.getNumber(), ((Gen1BaseStats) sp.getBaseStats()).getSpecial());
            }
        }

        List<Species> allSpecies = ultraSun.getSpeciesInclFormes();

        System.out.println("SPECIES_METADATA_CSV_BEGIN");
        System.out.println("number,name,formeSuffix,baseForme,generation,primaryType,secondaryType,"
                + "hp,attack,defense,spatk,spdef,speed,special,bst,catchRate,growthRate,isMega,"
                + "isAlolan,isLegendary,isStrongLegendary,isUltraBeast,ability1,ability2,"
                + "hiddenAbility,eggGroup1,eggGroup2,guaranteedHeldItem,commonHeldItem,rareHeldItem,"
                + "darkGrassHeldItem");
        for (Species sp : allSpecies) {
            if (sp == null || sp.isEssentiallyCosmetic()) {
                continue;
            }
            Integer special = sp.isBaseForme() ? gen1Special.get(sp.getNumber()) : null;
            BaseStats bs = sp.getBaseStats();
            System.out.printf("%d,%s,%s,%s,%d,%s,%s,%d,%d,%d,%d,%d,%d,%s,%d,%d,%s,%b,%b,%b,%b,%b,%s,%s,%s,%s,%s,%s,%s,%s%n",
                    sp.getNumber(),
                    csvEscape(sp.getName()),
                    csvEscape(sp.getFormeSuffix()),
                    csvEscape(sp.isBaseForme() ? "" : sp.getBaseForme().getName()),
                    sp.getGeneration(),
                    sp.getPrimaryType(false),
                    sp.getSecondaryType(false) == null ? "" : sp.getSecondaryType(false),
                    bs.getHp(), bs.getAttack(), bs.getDefense(), bs.getSpatk(), bs.getSpdef(), bs.getSpeed(),
                    special == null ? "" : special.toString(),
                    sp.getBST(false),
                    sp.getCatchRate(),
                    sp.getGrowthCurve() == null ? "" : sp.getGrowthCurve().toString(),
                    sp.isMegaEvolution(),
                    sp.isBaseForme() ? false : sp.isAlolan(),
                    sp.isLegendary(),
                    sp.isStrongLegendary(),
                    sp.isUltraBeast(),
                    abilityName(ultraSun, sp.getAbility1()),
                    abilityName(ultraSun, sp.getAbility2()),
                    abilityName(ultraSun, sp.getAbility3()),
                    eggGroupName(sp.getBreedingInfo() == null ? null : sp.getBreedingInfo().getPrimaryEggGroup()),
                    eggGroupName(sp.getBreedingInfo() == null ? null : sp.getBreedingInfo().getSecondaryEggGroup()),
                    itemName(sp.getGuaranteedHeldItem()),
                    itemName(sp.getCommonHeldItem()),
                    itemName(sp.getRareHeldItem()),
                    itemName(sp.getDarkGrassHeldItem()));
        }
        System.out.println("SPECIES_METADATA_CSV_END");

        System.out.println("SPECIES_EVOLUTIONS_CSV_BEGIN");
        System.out.println("fromNumber,fromFormeSuffix,fromName,toNumber,toFormeSuffix,toName,"
                + "evolutionType,extraInfo,extraInfoResolved,estimatedEvoLvl");
        for (Species sp : allSpecies) {
            if (sp == null || sp.isEssentiallyCosmetic()) {
                continue;
            }
            for (Evolution evo : sp.getEvolutionsFrom()) {
                Species from = evo.getFrom();
                Species to = evo.getTo();
                System.out.printf("%d,%s,%s,%d,%s,%s,%s,%d,%s,%d%n",
                        from.getNumber(), csvEscape(from.getFormeSuffix()), csvEscape(from.getName()),
                        to.getNumber(), csvEscape(to.getFormeSuffix()), csvEscape(to.getName()),
                        evo.getType(),
                        evo.getExtraInfo(),
                        csvEscape(resolveExtraInfo(ultraSun, evo)),
                        evo.getEstimatedEvoLvl());
            }
        }
        System.out.println("SPECIES_EVOLUTIONS_CSV_END");
    }

    private static String resolveExtraInfo(RomHandler rom, Evolution evo) {
        EvolutionType type = evo.getType();
        if (type.usesItem()) {
            Item item = rom.getItems().get(evo.getExtraInfo());
            return item == null ? "" : item.getName();
        } else if (type.usesMove()) {
            return rom.getMoves().get(evo.getExtraInfo()).name;
        } else if (type.usesSpecies()) {
            return rom.getSpecies().get(evo.getExtraInfo()).getFullName();
        } else if (type.usesLocation()) {
            List<String> names = rom.getLocationNamesForEvolution(type);
            return names.isEmpty() ? "" : String.join(";", names);
        }
        return "";
    }

    private static String abilityName(RomHandler rom, int abilityId) {
        return abilityId == 0 ? "" : rom.abilityName(abilityId);
    }

    private static String eggGroupName(EggGroup eggGroup) {
        return eggGroup == null ? "" : eggGroup.name();
    }

    private static String itemName(Item item) {
        return item == null ? "" : item.getName();
    }

    // Species/ability/item names are plain identifiers with no commas/quotes in this dataset, but guard anyway.
    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private RomHandler tryLoad(String gameName, String fileBaseName) {
        Generation gen = Generation.GAME_TO_GENERATION.get(gameName);
        String full = ROMS_PATH + "/" + fileBaseName + gen.getFileSuffix();
        if (!new File(full).exists()) {
            return null;
        }
        RomHandler.Factory factory = gen.createFactory();
        if (!factory.isLoadable(full)) {
            return null;
        }
        RomHandler rom = factory.create();
        rom.loadRom(full);
        return rom;
    }
}
