package campusrider.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV encoder / decoder.
 *
 * - Fields containing a comma, quote, CR or LF are wrapped in double quotes,
 *   and quotes inside are doubled ("" ).
 * - A field starting with = + - @ (spreadsheet formula characters) is written
 *   with a leading apostrophe so opening the file in Excel cannot run a formula.
 *   Values in this application never legitimately start with those characters
 *   except negative numbers, which are written as plain numbers by the caller.
 */
public final class CsvCodec {

    private CsvCodec() {
    }

    public static String formatRow(List<String> fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(fields.get(i)));
        }
        return line.toString();
    }

    public static String escape(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        boolean needsQuotes = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0
                || (!v.isEmpty() && (Character.isWhitespace(v.charAt(0))
                || Character.isWhitespace(v.charAt(v.length() - 1))));
        if (!needsQuotes) {
            return v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /**
     * Splits CSV text into rows of fields. Handles quoted fields that contain
     * commas, quotes and line breaks. Blank lines are skipped. Unquoted fields
     * are trimmed.
     */
    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldWasQuoted = false;
        boolean rowHasContent = false;
        String s = text == null ? "" : text;
        if (s.startsWith("﻿")) {
            s = s.substring(1); // UTF-8 byte order mark written by some editors
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"' && field.toString().isBlank()) {
                field.setLength(0);
                inQuotes = true;
                fieldWasQuoted = true;
                rowHasContent = true;
            } else if (c == ',') {
                row.add(finish(field, fieldWasQuoted));
                field.setLength(0);
                fieldWasQuoted = false;
                rowHasContent = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < s.length() && s.charAt(i + 1) == '\n') {
                    i++;
                }
                if (rowHasContent || !field.toString().isBlank()) {
                    row.add(finish(field, fieldWasQuoted));
                    rows.add(row);
                }
                row = new ArrayList<>();
                field.setLength(0);
                fieldWasQuoted = false;
                rowHasContent = false;
            } else {
                field.append(c);
                if (!Character.isWhitespace(c)) {
                    rowHasContent = true;
                }
            }
        }
        if (rowHasContent || !field.toString().isBlank()) {
            row.add(finish(field, fieldWasQuoted));
            rows.add(row);
        }
        return rows;
    }

    private static String finish(StringBuilder field, boolean quoted) {
        String v = quoted ? field.toString() : field.toString().trim();
        if (v.startsWith("'") && v.length() > 1 && "=+@".indexOf(v.charAt(1)) >= 0) {
            v = v.substring(1);
        }
        return v;
    }
}
