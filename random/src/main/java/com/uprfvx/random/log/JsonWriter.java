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

/**
 * A minimal streaming JSON writer. Exists because this project deliberately ships no JSON
 * dependency (the jar is fat, and the documentation writer is the only consumer).
 * Escapes all non-ASCII so the emitted file is pure ASCII regardless of platform encoding.
 */
public class JsonWriter {

    private final StringBuilder out;
    private boolean needsComma = false;

    public JsonWriter(StringBuilder out) {
        this.out = out;
    }

    private void separate() {
        if (needsComma) {
            out.append(',');
        }
        needsComma = true;
    }

    public JsonWriter beginObject() { separate(); out.append('{'); needsComma = false; return this; }
    public JsonWriter endObject()   { out.append('}'); needsComma = true; return this; }
    public JsonWriter beginArray()  { separate(); out.append('['); needsComma = false; return this; }
    public JsonWriter endArray()    { out.append(']'); needsComma = true; return this; }

    public JsonWriter name(String key) {
        separate();
        writeString(key);
        out.append(':');
        needsComma = false;
        return this;
    }

    public JsonWriter value(String v) {
        if (v == null) {
            return nullValue();
        }
        separate();
        writeString(v);
        return this;
    }

    public JsonWriter value(long v)     { separate(); out.append(v); return this; }
    public JsonWriter value(boolean v)  { separate(); out.append(v); return this; }
    public JsonWriter nullValue()       { separate(); out.append("null"); return this; }

    private void writeString(String s) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
