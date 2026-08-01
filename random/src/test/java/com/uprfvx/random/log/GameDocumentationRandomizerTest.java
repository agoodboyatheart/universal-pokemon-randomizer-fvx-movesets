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
import com.uprfvx.random.random.RandomSource;
import com.uprfvx.romio.gamedata.ExpCurve;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Named *RandomizerTest so it runs under :random:testROMs (which supplies romsPath) and is
 * excluded from :random:test. Deliberately does NOT extend RandomizerTest — see
 * project_memory/test-roms-harness.md.
 */
public class GameDocumentationRandomizerTest {

    private static final String ROMS = System.getProperty("romsPath");

    // roms/ uses the ini-entry filename convention (see roms/readme.txt), not "Pokemon <Game>";
    // several games have multiple region/version files present, so a specific file is pinned here.
    private static final Map<String, String> ROM_FILE_BASE_NAMES = Map.of(
            "Red", "Red (U)",
            "Fire Red", "Fire Red (U) 1.0",
            "Crystal", "Crystal (U)",
            "Emerald", "Emerald (U)",
            "Platinum", "Platinum (U)",
            "HeartGold", "HeartGold (U)",
            "White 2", "White 2 (U)"
    );

    private RomHandler load(String game) {
        Generation gen = Generation.GAME_TO_GENERATION.get(game);
        String fileBase = ROM_FILE_BASE_NAMES.getOrDefault(game, game + " (U)");
        RomHandler rh = gen.createFactory().create();
        assertTrue(rh.loadRom(ROMS + "/" + fileBase + gen.getFileSuffix()));
        return rh;
    }

    /**
     * A bare {@code new Settings()} NPEs on {@code toString()} — {@code romName} and
     * {@code selectedEXPCurve} are left null by the constructor and only populated by the GUI's
     * {@code createSettingsFromState()} in real use. Mirror the minimum of that here.
     */
    private Settings newSettings(RomHandler rh) {
        Settings settings = new Settings();
        settings.setRomName(rh.getROMName());
        settings.setSelectedEXPCurve(ExpCurve.MEDIUM_FAST);
        return settings;
    }

    @Test
    public void emitsSpeciesEvenWhenNothingWasRandomized() {
        RomHandler rh = load("Fire Red");
        Settings settings = newSettings(rh);   // all-unchanged defaults otherwise
        RandomSource rs = new RandomSource();
        rs.seed(12345L);

        String json = new GameDocumentationWriter(rs, settings, rh).write();

        // The log would omit these entirely under all-unchanged settings; the doc must not.
        assertTrue(json.contains("\"species\""), "species array missing");
        // Case-insensitive: GBA ROMs store species names in ALL CAPS natively (confirmed via
        // Gen3RomHandler's own pokeNames[].toLowerCase() usage elsewhere) — the writer faithfully
        // dumps ROM data rather than reformatting display casing, which is a Phase B concern.
        assertTrue(json.toLowerCase().contains("bulbasaur"), "species names missing");
        assertTrue(json.contains("\"catchRate\""), "catch rate missing");
        assertTrue(json.contains("\"abilities\""), "abilities missing");
        assertTrue(json.contains("\"evolutions\""), "evolutions missing");
    }

    @Test
    public void gen1ReportsNoAbilitiesAndNoEggMoves() {
        RomHandler rh = load("Red");
        RandomSource rs = new RandomSource();
        rs.seed(12345L);

        String json = new GameDocumentationWriter(rs, newSettings(rh), rh).write();

        assertTrue(json.contains("\"abilitiesPerSpecies\":0"),
                "Gen 1 must report zero abilities per species");
        assertTrue(json.contains("\"hasMoveTutors\":false"),
                "Gen 1 has no move tutors");
    }

    @Test
    public void outputIsPureAscii() {
        RomHandler rh = load("Fire Red");
        RandomSource rs = new RandomSource();
        rs.seed(12345L);
        String json = new GameDocumentationWriter(rs, newSettings(rh), rh).write();
        for (int i = 0; i < json.length(); i++) {
            assertTrue(json.charAt(i) <= 0x7E,
                    "non-ASCII at " + i + ": " + json.charAt(i));
        }
    }

    @Test
    public void emitsBossTrainersWithTypeThemesAndProgressionOrder() {
        RomHandler rh = load("Fire Red");
        RandomSource rs = new RandomSource();
        rs.seed(12345L);
        String json = new GameDocumentationWriter(rs, newSettings(rh), rh).write();

        assertTrue(json.contains("\"trainers\""), "trainers array missing");
        assertTrue(json.contains("\"isBoss\":true"), "no boss trainers flagged");
        assertTrue(json.contains("\"typeTheme\""), "type theme key missing");
        assertTrue(json.contains("\"progressionOrder\""), "progression order missing");
        // Regular trainers are out of scope.
        assertFalse(json.contains("\"isBoss\":false,\"isImportant\":false"),
                "regular trainers must not be emitted");
    }
}
