package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Encounter;
import com.uprfvx.romio.gamedata.EncounterArea;
import com.uprfvx.romio.gamedata.EncounterType;
import com.uprfvx.romio.gamedata.GenRestrictions;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.romhandlers.RomHandlerTest;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Diagnostic (report-only) harness for the C3 game-locations-categorisation project: dumps
 * per-location encounter-identity metadata straight from real ROM data, for a {@code scratchpad}
 * script to derive {@code habitat}/{@code region} and join into
 * {@code reference\metadata_game_locations.csv} + {@code reference\metadata_game_location_habitats.csv}.
 * Asserts nothing - purely a data dump, same spirit as {@link MoveMetadataExtractionRandomizerTest}/
 * {@link SpeciesMetadataExtractionRandomizerTest}.
 * <p>
 * Unlike those two (1-2 ROMs each), this is meant to load every vanilla ROM available under
 * {@code romsPath}: a nominal "generation" bucket here can span more than one real-world region
 * (HeartGold/SoulSilver = Kanto+Johto, Diamond/Pearl/Platinum = Sinnoh only, both "Gen4"), so a
 * per-generation snapshot would silently drop whichever region wasn't sampled.
 * <p>
 * Deliberately does not use {@link RomHandlerTest#getRomNames()}/{@link RomHandlerTest#loadROM}:
 * those iterate the full ini-backed {@code Roms} name list, most of which has no ROM present.
 * Scans the folder directly instead and normalizes each filename down to a
 * {@link Generation#GAME_TO_GENERATION} key, so it picks up whatever vanilla ROMs are actually
 * present without a hand-maintained 163-entry filename map, and tolerates the region/version tags
 * in the ini-style names documented by {@code roms\readme.txt} (e.g. {@code "Red (U).gb"}).
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*GameLocationMetadataExtraction*" }</pre>
 * Output is bracketed by BEGIN/END markers; read it from the test's XML {@code <system-out>} CDATA
 * (not the console) to avoid glyph-mangling, per the project's own {@code -Dgolden.record=true}
 * convention.
 */
public class GameLocationMetadataExtractionRandomizerTest extends RomHandlerTest {

    private static final String ROMS_PATH = System.getProperty("romsPath");

    @Test
    public void dumpPlannedGameLocationMetadata() {
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");

        // key = game + "|" + locationTag + "|" + area.getEncounterType() -> set of primary/
        // secondary types seen there, used only to feed the Habitats CSV's type aggregation
        // (never printed as a per-location species roster - that's live randomizer input/output,
        // not stable category metadata). Keyed per EncounterType (not just per location) so a
        // Surfing/Fishing area's species never share a bucket with the same location's Walking
        // area - the join script routes each EncounterType to its own habitat bucket.
        Map<String, Set<Type>> typesByLocation = new HashMap<>();

        System.out.println("GAME_LOCATIONS_METADATA_CSV_BEGIN");
        System.out.println("game,generation,locationTag,displayName,areaCount,"
                + "hasWalkingEncounters,hasSurfingEncounters,hasFishingEncounters,"
                + "hasRockSmashEncounters,hasAmbushEncounters,hasSpecialEncounters,"
                + "postGame,mapIndex,mapIndexReliable");

        for (String fileName : listRomFiles()) {
            String game = resolveGameName(fileName);
            Generation gen = game == null ? null : Generation.GAME_TO_GENERATION.get(game);
            if (gen == null) {
                continue; // not a recognized vanilla ROM filename (e.g. a hack, or an unmapped name)
            }
            String fullPath = ROMS_PATH + "/" + fileName;
            RomHandler.Factory factory = gen.createFactory();
            if (!factory.isLoadable(fullPath)) {
                continue;
            }
            romHandler = factory.create();
            romHandler.loadRom(fullPath);
            romHandler.getRestrictedSpeciesService().setRestrictions(new GenRestrictions());

            List<EncounterArea> areas = new ArrayList<>(romHandler.getEncounters(false));
            areas.removeIf(area -> area.getEncounterType() == EncounterType.UNUSED
                    || "UNUSED".equals(area.getLocationTag()));
            Map<String, List<EncounterArea>> byLocation = EncounterArea.groupAreasByLocation(areas);
            boolean mapIndexReliable = romHandler.hasMapIndices();

            for (Map.Entry<String, List<EncounterArea>> entry : byLocation.entrySet()) {
                String locationTag = entry.getKey();
                List<EncounterArea> group = entry.getValue();

                String displayName = "";
                boolean hasWalking = false;
                boolean hasSurfing = false;
                boolean hasFishing = false;
                boolean hasRockSmash = false;
                boolean hasAmbush = false;
                boolean hasSpecial = false;
                boolean postGame = false;
                int mapIndex = -1;
                for (EncounterArea area : group) {
                    if (displayName.isEmpty() && area.getDisplayName() != null) {
                        displayName = area.getDisplayName();
                    }
                    if (mapIndex < 0 && area.getMapIndex() >= 0) {
                        mapIndex = area.getMapIndex();
                    }
                    postGame |= area.isPostGame();
                    switch (area.getEncounterType()) {
                        case WALKING -> hasWalking = true;
                        case SURFING -> hasSurfing = true;
                        case FISHING -> hasFishing = true;
                        case INTERACT -> hasRockSmash = true;
                        case AMBUSH -> hasAmbush = true;
                        case SPECIAL -> hasSpecial = true;
                        default -> { /* UNUSED already filtered out above */ }
                    }
                    Set<Type> types = typesByLocation.computeIfAbsent(
                            game + "|" + locationTag + "|" + area.getEncounterType(), k -> new HashSet<>());
                    for (Encounter enc : area) {
                        Species sp = enc.getSpecies();
                        types.add(sp.getPrimaryType(false));
                        if (sp.getSecondaryType(false) != null) {
                            types.add(sp.getSecondaryType(false));
                        }
                    }
                }

                System.out.printf("%s,%d,%s,%s,%d,%b,%b,%b,%b,%b,%b,%b,%d,%b%n",
                        csvEscape(game), gen.getNumber(), csvEscape(locationTag), csvEscape(displayName),
                        group.size(), hasWalking, hasSurfing, hasFishing, hasRockSmash, hasAmbush, hasSpecial,
                        postGame, mapIndex, mapIndexReliable);
            }
        }
        System.out.println("GAME_LOCATIONS_METADATA_CSV_END");

        // Second block: dump typesByLocation flattened to one row per (game, locationTag, type) -
        // habitat is derived in the Python join script, not here, matching the moves/species
        // harnesses' division of labor: harness dumps facts, script joins.
        System.out.println("GAME_LOCATIONS_SPECIES_TYPES_BY_LOCATION_CSV_BEGIN");
        System.out.println("game,locationTag,encounterType,primaryOrSecondaryType");
        for (Map.Entry<String, Set<Type>> entry : typesByLocation.entrySet()) {
            String[] parts = entry.getKey().split("\\|", 3);
            for (Type type : entry.getValue()) {
                System.out.printf("%s,%s,%s,%s%n",
                        csvEscape(parts[0]), csvEscape(parts[1]), parts[2], type);
            }
        }
        System.out.println("GAME_LOCATIONS_SPECIES_TYPES_BY_LOCATION_CSV_END");
    }

    private static final String DECRYPTED_SUFFIX = "-decrypted";

    private static List<String> listRomFiles() {
        File[] files = new File(ROMS_PATH).listFiles(File::isFile);
        if (files == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (File f : files) {
            if (!f.getName().endsWith(".txt")) {
                names.add(f.getName());
            }
        }
        return names;
    }

    /**
     * Normalizes a filename under {@code roms\} (e.g. {@code "Ultra Sun.3ds"}, {@code "Red (U).gb"})
     * down to a {@link Generation#GAME_TO_GENERATION} key (e.g. {@code "Ultra Sun"}, {@code "Red"}),
     * or {@code null} if it doesn't look like a recognized ROM file.
     * <p>
     * The legacy {@code "Pokemon "} prefix is tolerated but no longer required: the roms folder now
     * follows the ini-entry naming documented in {@code roms\readme.txt}.
     */
    private static String resolveGameName(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        String base = lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
        if (base.startsWith("Pokemon ")) {
            base = base.substring("Pokemon ".length());
        }
        base = base.split("\\(")[0].trim(); // drop region/version parenthetical suffixes
        if (base.endsWith(DECRYPTED_SUFFIX)) {
            base = base.substring(0, base.length() - DECRYPTED_SUFFIX.length()).trim();
        }
        return base;
    }

    // Game/locationTag strings are plain identifiers with no commas/quotes in this dataset, but guard anyway.
    private static String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
