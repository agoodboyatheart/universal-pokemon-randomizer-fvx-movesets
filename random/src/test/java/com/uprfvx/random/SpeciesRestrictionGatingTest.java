package com.uprfvx.random;

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

import com.uprfvx.romio.gamedata.GenRestrictions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpeciesRestrictionGatingTest {

    private static GenRestrictions narrowRestrictions() {
        GenRestrictions restrictions = new GenRestrictions();
        restrictions.setGenAllowed(4, false);
        restrictions.setGenAllowed(5, false);
        restrictions.setGenAllowed(6, false);
        restrictions.setGenAllowed(7, false);
        return restrictions;
    }

    @Test
    public void limitPokemonOff_IgnoresStaleCurrentRestrictions() {
        Settings settings = new Settings();
        settings.setLimitPokemon(false);
        settings.setCurrentRestrictions(narrowRestrictions());

        GenRestrictions effective = GameRandomizer.effectiveRestrictions(settings);

        for (int gen = 1; gen <= GenRestrictions.MAX_GENERATION; gen++) {
            assertTrue(effective.isGenAllowed(gen), "Gen " + gen + " should be allowed when Limit Pokemon is off");
        }
    }

    @Test
    public void limitPokemonOn_AppliesCurrentRestrictions() {
        Settings settings = new Settings();
        settings.setLimitPokemon(true);
        GenRestrictions restrictions = narrowRestrictions();
        settings.setCurrentRestrictions(restrictions);

        GenRestrictions effective = GameRandomizer.effectiveRestrictions(settings);

        assertSame(restrictions, effective);
        assertFalse(effective.isGenAllowed(4));
    }
}
