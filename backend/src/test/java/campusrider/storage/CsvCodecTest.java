package campusrider.storage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvCodecTest {

    @Test
    void roundTripsCommasQuotesAndLineBreaks() {
        List<String> row = List.of("plain", "with, comma", "say \"hi\"", "two\nlines", "", "  padded  ");
        String line = CsvCodec.formatRow(row);
        assertEquals("plain,\"with, comma\",\"say \"\"hi\"\"\",\"two\nlines\",,\"  padded  \"", line);
        assertEquals(List.of(row), CsvCodec.parse(line + "\n"));
    }

    @Test
    void neutralisesSpreadsheetFormulasAndRestoresThemOnRead() {
        String line = CsvCodec.formatRow(List.of("=HYPERLINK(\"x\")", "@cmd", "-12.5"));
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\",'@cmd,-12.5", line);
        assertEquals(List.of(List.of("=HYPERLINK(\"x\")", "@cmd", "-12.5")), CsvCodec.parse(line));
    }

    @Test
    void skipsBlankLinesTrimsUnquotedFieldsAndHandlesCrLfAndBom() {
        List<List<String>> rows = CsvCodec.parse("﻿a, b ,c\r\n\r\n  \nd,e,\n");
        assertEquals(List.of(List.of("a", "b", "c"), List.of("d", "e", "")), rows);
    }
}
