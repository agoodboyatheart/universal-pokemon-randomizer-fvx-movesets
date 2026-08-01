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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonWriterTest {

    @Test
    public void writesNestedObjectsAndArrays() {
        StringBuilder sb = new StringBuilder();
        JsonWriter w = new JsonWriter(sb);
        w.beginObject()
                .name("game").beginObject()
                    .name("name").value("Pokemon Fire Red (U) 1.0")
                    .name("generation").value(3)
                .endObject()
                .name("tms").beginArray()
                    .value("Vine Whip").value("Toxic")
                .endArray()
                .name("ok").value(true)
                .name("missing").nullValue()
            .endObject();
        assertEquals(
            "{\"game\":{\"name\":\"Pokemon Fire Red (U) 1.0\",\"generation\":3},"
            + "\"tms\":[\"Vine Whip\",\"Toxic\"],\"ok\":true,\"missing\":null}",
            sb.toString());
    }

    @Test
    public void escapesControlCharactersAndQuotes() {
        StringBuilder sb = new StringBuilder();
        new JsonWriter(sb).beginObject().name("k").value("a\"b\\c\nd\te").endObject();
        assertEquals("{\"k\":\"a\\\"b\\\\c\\nd\\te\"}", sb.toString());
    }

    @Test
    public void escapesNonAsciiSoOutputIsPureAscii() {
        // Species names carry real non-ASCII: Nidoran-female/male, Farfetch'd's curly apostrophe.
        StringBuilder sb = new StringBuilder();
        new JsonWriter(sb).beginObject().name("k").value("Nidoran♀").endObject();
        assertEquals("{\"k\":\"Nidoran\\u2640\"}", sb.toString());
    }
}
