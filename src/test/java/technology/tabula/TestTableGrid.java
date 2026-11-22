package technology.tabula;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TestTableGrid {

    private static Cell cell(float top, float left, float width, float height, String text) {
        Cell cell = new Cell(top, left, width, height);
        if (text != null) {
            cell.setTextElements(Collections.singletonList(
                    new TextChunk(new TextElement(top, left, width, height, null, 10f, text, 1f))));
        }
        return cell;
    }

    @Test
    public void testSpansFromVisibleEdges() {
        // A 3x3 grid where the top-left 2x2 block is one real box.
        List<Cell> cells = new ArrayList<>();
        cells.add(cell(0, 0, 20, 20, "header"));   // rowSpan 2, colSpan 2
        cells.add(cell(0, 20, 10, 10, "b"));
        cells.add(cell(10, 20, 10, 10, "c"));
        cells.add(cell(20, 0, 10, 10, "d"));
        cells.add(cell(20, 10, 10, 10, "e"));
        cells.add(cell(20, 20, 10, 10, "f"));

        TableGrid grid = TableGrid.build(cells, 0.1f, 7, 3);

        assertEquals(3, grid.getRowCount());
        assertEquals(3, grid.getColCount());
        assertTrue(grid.getErrors().isEmpty());

        TableGrid.GridCell header = grid.getCell(0, 0);
        assertEquals(TableGrid.Kind.REAL, header.getKind());
        assertEquals(2, header.getRowSpan());
        assertEquals(2, header.getColSpan());
        assertEquals("header", header.getText());

        TableGrid.GridCell covered = grid.getCell(1, 1);
        assertEquals(TableGrid.Kind.COVERED, covered.getKind());
        assertEquals(0, covered.getReferenceRow());
        assertEquals(0, covered.getReferenceColumn());

        assertEquals(TableGrid.Kind.REAL, grid.getCell(2, 2).getKind());
        assertEquals("f", grid.getCell(2, 2).getText());
    }

    @Test
    public void testBorderedCellWithoutTextIsStillReal() {
        List<Cell> cells = Arrays.asList(
                cell(0, 0, 10, 10, null),
                cell(0, 10, 10, 10, "x"),
                cell(10, 0, 10, 10, "y"),
                cell(10, 10, 10, 10, "z"));

        TableGrid grid = TableGrid.build(cells, 0.1f, 1, 0);

        TableGrid.GridCell empty = grid.getCell(0, 0);
        assertEquals(TableGrid.Kind.REAL, empty.getKind());
        assertEquals("", empty.getText());
        assertEquals(1, empty.getRowSpan());
        assertEquals(1, empty.getColSpan());
    }

    @Test
    public void testUncoveredPositionIsMarked() {
        // Only the top-left and the bottom row are bordered.
        List<Cell> cells = Arrays.asList(
                cell(0, 0, 20, 10, "top"),
                cell(20, 0, 10, 10, "a"),
                cell(20, 10, 10, 10, "b"));

        TableGrid grid = TableGrid.build(cells, 0.1f, 1, 0);

        assertEquals(TableGrid.Kind.REAL, grid.getCell(0, 0).getKind());
        // (1,0) and (1,1) are not covered by any real box.
        assertEquals(TableGrid.Kind.UNCOVERED, grid.getCell(1, 0).getKind());
        assertEquals(TableGrid.Kind.UNCOVERED, grid.getCell(1, 1).getKind());
    }

    @Test
    public void testResultIsIndependentOfInputOrder() {
        List<Cell> cells = new ArrayList<>();
        cells.add(cell(0, 0, 20, 20, "header"));
        cells.add(cell(0, 20, 10, 10, "b"));
        cells.add(cell(10, 20, 10, 10, "c"));
        cells.add(cell(20, 0, 10, 10, "d"));
        cells.add(cell(20, 10, 10, 10, "e"));
        cells.add(cell(20, 20, 10, 10, "f"));

        TableGrid first = TableGrid.build(cells, 0.1f, 1, 0);

        List<Cell> shuffled = new ArrayList<>(cells);
        Collections.shuffle(shuffled, new java.util.Random(42));
        TableGrid second = TableGrid.build(shuffled, 0.1f, 1, 0);

        assertEquals(first.getRowCount(), second.getRowCount());
        assertEquals(first.getColCount(), second.getColCount());
        for (int r = 0; r < first.getRowCount(); r++) {
            for (int c = 0; c < first.getColCount(); c++) {
                assertEquals(first.getCell(r, c), second.getCell(r, c));
            }
        }
    }

    @Test
    public void testToleranceMergesEdgesWithinSpread() {
        // Two vertical edges 0.05 apart must merge; two 0.5 apart must not.
        List<Cell> cells = Arrays.asList(
                cell(0, 0, 10, 10, "a"),
                cell(0, 10.05f, 10, 10, "b"),
                cell(0, 30.5f, 10, 10, "c"));

        TableGrid tolerant = TableGrid.build(cells, 0.1f, 1, 0);
        assertEquals(4, tolerant.getColCount()); // [0], [10, 10.05], [20.05], [30.5], [40.5]

        TableGrid strict = TableGrid.build(cells, 0f, 1, 0);
        assertEquals(5, strict.getColCount()); // 0, 10, 10.05, 20.05, 30.5, 40.5 are all distinct
    }

    @Test
    public void testZeroSpanIsReportedWithPageAndTable() {
        // A box whose left/right collapse to the same grid edge after the given tolerance.
        List<Cell> cells = Arrays.asList(
                cell(0, 0, 10, 10, "a"),
                cell(20, 0, 10, 10, "b"));
        // Make a degenerate box: width 0.
        Cell degenerate = new Cell(10, 0, 0, 10);
        cells = new ArrayList<>(cells);
        cells.add(degenerate);

        TableGrid grid = TableGrid.build(cells, 0.1f, 12, 2);

        assertEquals(1, grid.getErrors().size());
        TableGrid.GridError error = grid.getErrors().get(0);
        assertEquals(TableGrid.GridError.Type.ZERO_SPAN, error.getType());
        assertEquals(12, error.getPageNumber());
        assertEquals(2, error.getTableIndex());
        assertEquals(3, error.getTableNumber());
        assertTrue(error.getMessage().contains("Page 12"));
        assertTrue(error.getMessage().contains("table 3"));
    }

    @Test
    public void testOverlapIsReportedWithPageAndTable() {
        List<Cell> cells = Arrays.asList(
                cell(0, 0, 20, 20, "a"),
                cell(10, 10, 20, 20, "b"));

        TableGrid grid = TableGrid.build(cells, 0.1f, 5, 1);

        List<TableGrid.GridError> overlaps = new ArrayList<>();
        for (TableGrid.GridError error : grid.getErrors()) {
            if (error.getType() == TableGrid.GridError.Type.OVERLAP) {
                overlaps.add(error);
            }
        }
        assertEquals(1, overlaps.size());
        assertEquals(5, overlaps.get(0).getPageNumber());
        assertEquals(1, overlaps.get(0).getTableIndex());
        assertTrue(overlaps.get(0).getMessage().contains("Page 5"));
        assertTrue(overlaps.get(0).getMessage().contains("table 2"));
    }

    @Test
    public void testToleranceDoesNotChainAcrossGroups() {
        // Coordinates 0, 0.08, 0.16 with tolerance 0.1: 0.16 must not join the first group
        // through 0.08, because the spread of a group is measured against its minimum.
        List<Cell> cells = Arrays.asList(
                cell(0, 0f, 10, 10, "a"),
                cell(0, 0.08f, 10, 10, "b"),
                cell(0, 0.16f, 10, 10, "c"));

        TableGrid grid = TableGrid.build(cells, 0.1f, 1, 0);
        // Groups: [0, 0.08], [0.16], [10, 10.08], [10.16] -> 3 columns. Chaining the
        // coordinates would merge everything into 2 groups (and thus a single column).
        assertEquals(3, grid.getColCount());
    }

    @Test
    public void testIllegalToleranceIsRejected() {
        List<Cell> cells = Arrays.asList(cell(0, 0, 10, 10, "a"));
        for (float tolerance : new float[] {-1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            try {
                TableGrid.build(cells, tolerance, 1, 0);
                throw new AssertionError("expected IllegalArgumentException for " + tolerance);
            } catch (IllegalArgumentException expected) {
                // OK.
            }
        }
    }

    @Test
    public void testNoGridForBasicTables() {
        Table table = new Table(new technology.tabula.extractors.BasicExtractionAlgorithm());
        table.add(new TextChunk(0, 0, 10, 10), 0, 0);

        assertNull(table.getGrid(0.1f, 0));
    }

    @Test
    public void testGridFromTableWithRulingLines() {
        List<Cell> cells = Arrays.asList(
                cell(0, 0, 10, 10, "a"),
                cell(0, 10, 10, 10, "b"),
                cell(10, 0, 10, 10, "c"),
                cell(10, 10, 10, 10, "d"));

        TableWithRulingLines table = new TableWithRulingLines(
                new Rectangle(0, 0, 20, 20), cells,
                new ArrayList<Ruling>(), new ArrayList<Ruling>(),
                new technology.tabula.extractors.SpreadsheetExtractionAlgorithm(), 4);

        TableGrid grid = table.getGrid(0.1f, 0);
        assertNotNull(grid);
        assertEquals(2, grid.getRowCount());
        assertEquals(2, grid.getColCount());
        assertEquals(4, table.getPageNumber());
        assertFalse(grid.getRows().isEmpty());
    }

}
