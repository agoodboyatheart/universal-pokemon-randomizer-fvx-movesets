package com.uprfvx.random.randomizers;

import com.uprfvx.romio.gamedata.Move;
import com.uprfvx.romio.romhandlers.Generation;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Diagnostic (report-only) harness for the C3 move-categorisation project: dumps the 10
 * previously-"planned" {@code Move} fields (status/crit/contact/punch/sound/trap/charge/
 * recharge/recoil/drain) straight from a real ROM's already-populated data, for a
 * {@code scratchpad} script to mirror into {@code reference\metadata_pokemon_moves.csv}.
 * Asserts nothing - purely a data dump, same spirit as {@link MovesetProfileRandomizerTest}.
 * <p>
 * Only covers Ultra Sun's 728 ROM-backed move IDs (1-728). IDs 729-742 are Let's Go
 * Pikachu/Eevee-exclusive moves with no RomHandler in this fork and are intentionally NOT
 * emitted here - the assembly script sources those 14 from PokeAPI/Showdown instead.
 * <pre>{@code  ./gradlew.bat :random:testROMs --tests "*MoveMetadataExtraction*" }</pre>
 * Output is bracketed by BEGIN/END markers; read it from the test's XML {@code <system-out>}
 * CDATA (not the console) to avoid glyph-mangling on move names, per the project's own
 * {@code -Dgolden.record=true} convention.
 */
public class MoveMetadataExtractionRandomizerTest {

    private static final String ROMS_PATH = System.getProperty("romsPath");
    private static final String GAME_NAME = "Ultra Sun";
    private static final String FILE_BASE_NAME = "Ultra Sun";

    @Test
    public void dumpPlannedMoveMetadata() {
        assumeTrue(ROMS_PATH != null, "romsPath not set (run via the testROMs task)");
        RomHandler rom = tryLoad(GAME_NAME, FILE_BASE_NAME);
        assumeTrue(rom != null, "ROM absent: " + GAME_NAME);

        List<Move> allMoves = rom.getMoves();
        System.out.println("MOVE_METADATA_CSV_BEGIN");
        System.out.println("id,name,status_effect,crit_chance,makesContact,isPunchMove,"
                + "isSoundMove,isTrapMove,isChargeMove,isRechargeMove,hasRecoil,hasDrain");
        for (int i = 1; i < allMoves.size(); i++) {
            Move mv = allMoves.get(i);
            if (mv == null) {
                continue;
            }
            System.out.printf("%d,%s,%s,%s,%b,%b,%b,%b,%b,%b,%b,%b%n",
                    i,
                    csvEscape(mv.name),
                    mv.statusType.name(),
                    mv.criticalChance.name(),
                    mv.makesContact,
                    mv.isPunchMove,
                    mv.isSoundMove,
                    mv.isTrapMove,
                    mv.isChargeMove,
                    mv.isRechargeMove,
                    mv.recoilPercent > 0,
                    mv.absorbPercent > 0);
        }
        System.out.println("MOVE_METADATA_CSV_END");
    }

    // Move names are plain ASCII identifiers with no commas/quotes in this dataset, but guard anyway.
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
