package technology.tabula;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.junit.Test;

import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.writers.JSONWriter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TestGridWriter {

    private static final String SPANNING_CELLS_PDF =
            "src/test/resources/technology/tabula/spanning_cells.pdf";

    private List<Table> spanningCellsTables() throws IOException {
        Page page = UtilsForTesting.getPage(SPANNING_CELLS_PDF, 1);
        try {
            return new SpreadsheetExtractionAlgorithm().extract(page);
        } finally {
            page.getPDDoc().close();
        }
    }

    @Test
    public void testDefaultJsonHasNoGrid() throws IOException {
        StringBuilder sb = new StringBuilder();
        new JSONWriter().write(sb, spanningCellsTables());

        assertFalse(sb.toString().contains("\"grid\""));
    }

    @Test
    public void testGridIsOptIn() throws IOException {
        StringBuilder sb = new StringBuilder();
        new JSONWriter(true).write(sb, spanningCellsTables());

        JsonArray tables = JsonParser.parseString(sb.toString()).getAsJsonArray();
        assertEquals(2, tables.size());

        for (int i = 0; i < tables.size(); i++) {
            JsonObject table = tables.get(i).getAsJsonObject();
            // Existing metadata is preserved.
            assertEquals("lattice", table.get("extraction_method").getAsString());
            assertEquals(1, table.get("page_number").getAsInt());
            assertTrue(table.has("top"));
            assertTrue(table.has("left"));
            assertTrue(table.has("width"));
            assertTrue(table.has("height"));
            assertTrue(table.has("right"));
            assertTrue(table.has("bottom"));
            assertTrue(table.has("data"));

            JsonObject grid = table.getAsJsonObject("grid");
            assertNotNull(grid);
            assertTrue(grid.get("row_count").getAsInt() > 0);
            assertTrue(grid.get("col_count").getAsInt() > 0);
            assertTrue(grid.has("cells"));
            assertTrue(grid.has("errors"));

            JsonArray cells = grid.getAsJsonArray("cells");
            assertEquals(grid.get("row_count").getAsInt(), cells.size());
            for (int r = 0; r < cells.size(); r++) {
                JsonArray row = cells.get(r).getAsJsonArray();
                assertEquals(grid.get("col_count").getAsInt(), row.size());
            }
        }

        // The spanning header is a single real box covering the whole row.
        JsonObject firstGrid = tables.get(0).getAsJsonObject().getAsJsonObject("grid");
        JsonObject topLeft = firstGrid.getAsJsonArray("cells").get(0).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("real", topLeft.get("kind").getAsString());
        assertEquals(1, topLeft.get("rowSpan").getAsInt());
        assertEquals(6, topLeft.get("colSpan").getAsInt());
        assertEquals("Improved operation scenario", topLeft.get("text").getAsString());

        JsonObject covered = firstGrid.getAsJsonArray("cells").get(0).getAsJsonArray().get(1).getAsJsonObject();
        assertEquals("covered", covered.get("kind").getAsString());
        assertEquals(0, covered.getAsJsonObject("reference").get("row").getAsInt());
        assertEquals(0, covered.getAsJsonObject("reference").get("col").getAsInt());
    }

    @Test
    public void testGridOutputIsIndependentOfCellOrder() throws IOException {
        List<Table> tables = spanningCellsTables();
        StringBuilder expected = new StringBuilder();
        new JSONWriter(true).write(expected, tables);

        List<Table> reversed = new java.util.ArrayList<>();
        for (Table table : tables) {
            TableWithRulingLines withRulings = (TableWithRulingLines) table;
            List<Cell> cells = new java.util.ArrayList<>(withRulings.getCells());
            java.util.Collections.reverse(cells);
            reversed.add(new TableWithRulingLines(
                    new Rectangle(withRulings.getTop(), withRulings.getLeft(),
                            (float) withRulings.getWidth(), (float) withRulings.getHeight()),
                    cells, new java.util.ArrayList<Ruling>(), new java.util.ArrayList<Ruling>(),
                    new SpreadsheetExtractionAlgorithm(), table.getPageNumber()));
        }

        StringBuilder actual = new StringBuilder();
        new JSONWriter(true).write(actual, reversed);

        JsonArray expectedTables = JsonParser.parseString(expected.toString()).getAsJsonArray();
        JsonArray actualTables = JsonParser.parseString(actual.toString()).getAsJsonArray();
        assertEquals(expectedTables.size(), actualTables.size());
        for (int i = 0; i < expectedTables.size(); i++) {
            assertEquals(expectedTables.get(i).getAsJsonObject().get("grid"),
                    actualTables.get(i).getAsJsonObject().get("grid"));
        }
    }

    @Test
    public void testCommandLineGridFlag() throws ParseException, IOException {
        StringBuilder sb = new StringBuilder();
        CommandLine line = new DefaultParser().parse(CommandLineApp.buildOptions(), new String[] {
                SPANNING_CELLS_PDF, "-p", "1", "-l", "-f", "JSON", "--grid"
        });
        new CommandLineApp(sb, line).extractTables(line);

        assertTrue(sb.toString().contains("\"grid\""));
        assertTrue(sb.toString().contains("\"rowSpan\""));
        assertTrue(sb.toString().contains("\"colSpan\""));
    }

    @Test
    public void testCommandLineWithoutGridFlagIsUnchanged() throws ParseException, IOException {
        StringBuilder sb = new StringBuilder();
        CommandLine line = new DefaultParser().parse(CommandLineApp.buildOptions(), new String[] {
                SPANNING_CELLS_PDF, "-p", "1", "-l", "-f", "JSON"
        });
        new CommandLineApp(sb, line).extractTables(line);

        String expected = UtilsForTesting.loadJson(
                "src/test/resources/technology/tabula/json/spanning_cells.json");
        assertEquals(expected, sb.toString());
    }

    @Test
    public void testGridDoesNotChangeExistingTableMetadata() throws IOException {
        List<Table> tables = spanningCellsTables();

        StringBuilder plain = new StringBuilder();
        new JSONWriter().write(plain, tables);

        StringBuilder gridded = new StringBuilder();
        new JSONWriter(true).write(gridded, tables);

        JsonArray plainTables = JsonParser.parseString(plain.toString()).getAsJsonArray();
        JsonArray gridTables = JsonParser.parseString(gridded.toString()).getAsJsonArray();
        assertEquals(plainTables.size(), gridTables.size());
        for (int i = 0; i < plainTables.size(); i++) {
            JsonObject gridTable = gridTables.get(i).getAsJsonObject();
            assertTrue(gridTable.has("grid"));
            gridTable.remove("grid");
            // Everything except the optional grid must be byte-for-byte the same JSON.
            assertEquals(plainTables.get(i), gridTable);
        }
    }

    @Test
    public void testSingleTableGridMatchesListGrid() throws IOException {
        List<Table> tables = spanningCellsTables();
        Table first = tables.get(0);

        StringBuilder single = new StringBuilder();
        new JSONWriter(true).write(single, first);

        StringBuilder list = new StringBuilder();
        new JSONWriter(true).write(list, java.util.Collections.singletonList(first));

        // A single table is serialized exactly like the one-element list of that table.
        assertEquals("[" + single + "]", list.toString());

        StringBuilder all = new StringBuilder();
        new JSONWriter(true).write(all, tables);
        JsonObject listTable = JsonParser.parseString(all.toString()).getAsJsonArray()
                .get(0).getAsJsonObject();
        assertEquals(listTable, JsonParser.parseString(single.toString()).getAsJsonObject());

        // The default single-table path is still the plain, grid-free JSON.
        StringBuilder plain = new StringBuilder();
        new JSONWriter().write(plain, first);
        assertFalse(plain.toString().contains("\"grid\""));
    }

    @Test
    public void testUncoveredPositionsAreMarked() throws IOException {
        Page page = UtilsForTesting.getPage("src/test/resources/technology/tabula/china.pdf", 1);
        List<Table> tables;
        try {
            tables = new SpreadsheetExtractionAlgorithm().extract(page);
        } finally {
            page.getPDDoc().close();
        }

        StringBuilder sb = new StringBuilder();
        new JSONWriter(true).write(sb, tables);
        assertTrue(sb.toString().contains("\"uncovered\""));
    }

}
