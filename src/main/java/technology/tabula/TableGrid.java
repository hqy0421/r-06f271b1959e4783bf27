package technology.tabula;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A grid reconstructed from the bordered cells (the {@link Cell} instances found by the
 * lattice extraction algorithm).
 *
 * <p>The reconstruction only uses the <em>visible edges</em> of the boxes: all left/right
 * coordinates and all top/bottom coordinates are clustered independently, in ascending order,
 * merging coordinates that are within a configurable tolerance. The spread (max - min) of every
 * merged group never exceeds that tolerance.</p>
 *
 * <p>Every real box is mapped to the grid cell that contains its top-left corner. Its text is
 * reported only there, together with its {@code rowSpan} and {@code colSpan}. Every other grid
 * cell covered by that box only stores a reference to the top-left cell. Grid cells that are not
 * covered by any real box are reported as {@link Kind#UNCOVERED}.</p>
 *
 * <p>The result does not depend on the order in which the cells are supplied.</p>
 *
 * <p>Two real boxes that would occupy the same grid positions after mapping are a mapping
 * conflict: the box that comes first in a canonical order (smallest area, then top-left position)
 * keeps its place, the other one is skipped and reported as an {@link GridError.Type#OVERLAP}
 * error carrying the page number and the table index. Such overlaps come from extraction
 * anomalies (e.g. an enclosing box also detected as its own cell); the table's original
 * {@code data} is never modified.</p>
 */
public final class TableGrid {

    /** Default tolerance (in PDF points) used when clustering edge coordinates. */
    public static final float DEFAULT_TOLERANCE = 0.1f;

    private static final Comparator<Placement> PLACEMENT_ORDER = new Comparator<Placement>() {
        @Override
        public int compare(Placement a, Placement b) {
            // Smaller boxes come first so that, on a conflict, the more specific box wins and
            // the enclosing/erroneous one is the one reported as overlapping.
            int rv = Double.compare(a.cell.getArea(), b.cell.getArea());
            if (rv != 0) return rv;
            rv = Integer.compare(a.row, b.row);
            if (rv != 0) return rv;
            rv = Integer.compare(a.col, b.col);
            if (rv != 0) return rv;
            rv = Integer.compare(a.rowSpan, b.rowSpan);
            if (rv != 0) return rv;
            rv = Integer.compare(a.colSpan, b.colSpan);
            if (rv != 0) return rv;
            rv = Float.compare(a.cell.getTop(), b.cell.getTop());
            if (rv != 0) return rv;
            rv = Float.compare(a.cell.getLeft(), b.cell.getLeft());
            if (rv != 0) return rv;
            rv = Float.compare(a.cell.getBottom(), b.cell.getBottom());
            if (rv != 0) return rv;
            rv = Float.compare(a.cell.getRight(), b.cell.getRight());
            if (rv != 0) return rv;
            return a.cell.getText().compareTo(b.cell.getText());
        }
    };

    private static final Comparator<Cell> CELL_ORDER = new Comparator<Cell>() {
        @Override
        public int compare(Cell a, Cell b) {
            int rv = Float.compare(a.getTop(), b.getTop());
            if (rv != 0) return rv;
            rv = Float.compare(a.getLeft(), b.getLeft());
            if (rv != 0) return rv;
            rv = Float.compare(a.getBottom(), b.getBottom());
            if (rv != 0) return rv;
            rv = Float.compare(a.getRight(), b.getRight());
            if (rv != 0) return rv;
            return a.getText().compareTo(b.getText());
        }
    };

    private static boolean sameBox(Cell a, Cell b) {
        return Float.compare(a.getTop(), b.getTop()) == 0
                && Float.compare(a.getLeft(), b.getLeft()) == 0
                && Float.compare(a.getBottom(), b.getBottom()) == 0
                && Float.compare(a.getRight(), b.getRight()) == 0
                && a.getText().equals(b.getText());
    }

    /** The kind of a grid position. */
    public enum Kind {
        /** A real bordered box; carries the text and the spans. */
        REAL,
        /** Covered by a real box; only references the real box's top-left grid cell. */
        COVERED,
        /** Not covered by any real box. */
        UNCOVERED
    }

    /** A single position of the reconstructed grid. */
    public static final class GridCell {
        private final Kind kind;
        private final int row, col;
        private final String text;
        private final int rowSpan, colSpan;
        private final int referenceRow, referenceColumn;
        private final float top, left, width, height;

        private GridCell(Kind kind, int row, int col, String text, int rowSpan, int colSpan,
                         int referenceRow, int referenceColumn,
                         float top, float left, float width, float height) {
            this.kind = kind;
            this.row = row;
            this.col = col;
            this.text = text;
            this.rowSpan = rowSpan;
            this.colSpan = colSpan;
            this.referenceRow = referenceRow;
            this.referenceColumn = referenceColumn;
            this.top = top;
            this.left = left;
            this.width = width;
            this.height = height;
        }

        static GridCell real(int row, int col, Cell cell, int rowSpan, int colSpan) {
            return new GridCell(Kind.REAL, row, col, cell.getText(), rowSpan, colSpan,
                    row, col, cell.getTop(), cell.getLeft(), (float) cell.getWidth(), (float) cell.getHeight());
        }

        static GridCell covered(int row, int col, int referenceRow, int referenceColumn) {
            return new GridCell(Kind.COVERED, row, col, null, 0, 0,
                    referenceRow, referenceColumn, 0f, 0f, 0f, 0f);
        }

        static GridCell uncovered(int row, int col) {
            return new GridCell(Kind.UNCOVERED, row, col, null, 0, 0, -1, -1, 0f, 0f, 0f, 0f);
        }

        public Kind getKind() { return kind; }
        public int getRow() { return row; }
        public int getCol() { return col; }
        public String getText() { return text; }
        public int getRowSpan() { return rowSpan; }
        public int getColSpan() { return colSpan; }
        public int getReferenceRow() { return referenceRow; }
        public int getReferenceColumn() { return referenceColumn; }
        public float getTop() { return top; }
        public float getLeft() { return left; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }

        @Override public String toString() {
            switch (kind) {
                case REAL:
                    return String.format("real[row=%d,col=%d,rowSpan=%d,colSpan=%d,text=\"%s\"]",
                            row, col, rowSpan, colSpan, text);
                case COVERED:
                    return String.format("covered[row=%d,col=%d,ref=%d,%d]",
                            row, col, referenceRow, referenceColumn);
                default:
                    return String.format("uncovered[row=%d,col=%d]", row, col);
            }
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof GridCell)) return false;
            GridCell other = (GridCell) obj;
            return kind == other.kind && row == other.row && col == other.col
                    && rowSpan == other.rowSpan && colSpan == other.colSpan
                    && referenceRow == other.referenceRow && referenceColumn == other.referenceColumn
                    && Float.compare(top, other.top) == 0 && Float.compare(left, other.left) == 0
                    && Float.compare(width, other.width) == 0 && Float.compare(height, other.height) == 0
                    && (text == null ? other.text == null : text.equals(other.text));
        }

        @Override public int hashCode() {
            int rv = kind.hashCode();
            rv = 31 * rv + row;
            rv = 31 * rv + col;
            rv = 31 * rv + rowSpan;
            rv = 31 * rv + colSpan;
            rv = 31 * rv + referenceRow;
            rv = 31 * rv + referenceColumn;
            rv = 31 * rv + (text == null ? 0 : text.hashCode());
            return rv;
        }
    }

    /** A mapping problem (a zero span or an overlap between two real boxes). */
    public static final class GridError {
        public enum Type { ZERO_SPAN, OVERLAP }

        private final Type type;
        private final int pageNumber, tableIndex;
        private final String message;

        GridError(Type type, int pageNumber, int tableIndex, String message) {
            this.type = type;
            this.pageNumber = pageNumber;
            this.tableIndex = tableIndex;
            this.message = message;
        }

        public Type getType() { return type; }
        public int getPageNumber() { return pageNumber; }
        public int getTableIndex() { return tableIndex; }
        /** 1-based ordinal, suitable for human readable reporting. */
        public int getTableNumber() { return tableIndex + 1; }
        public String getMessage() { return message; }

        @Override public String toString() {
            return message;
        }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof GridError)) return false;
            GridError other = (GridError) obj;
            return type == other.type && pageNumber == other.pageNumber
                    && tableIndex == other.tableIndex
                    && (message == null ? other.message == null : message.equals(other.message));
        }

        @Override public int hashCode() {
            int rv = type.hashCode();
            rv = 31 * rv + pageNumber;
            rv = 31 * rv + tableIndex;
            rv = 31 * rv + (message == null ? 0 : message.hashCode());
            return rv;
        }
    }

    private final int rowCount, colCount;
    private final float tolerance;
    private final List<List<GridCell>> rows;
    private final List<GridError> errors;

    private TableGrid(int rowCount, int colCount, float tolerance,
                      List<List<GridCell>> rows, List<GridError> errors) {
        this.rowCount = rowCount;
        this.colCount = colCount;
        this.tolerance = tolerance;
        this.rows = rows;
        this.errors = errors;
    }

    public int getRowCount() { return rowCount; }
    public int getColCount() { return colCount; }
    public float getTolerance() { return tolerance; }

    public List<List<GridCell>> getRows() { return rows; }

    /** All grid mapping errors, in deterministic order. */
    public List<GridError> getErrors() { return errors; }

    public GridCell getCell(int row, int col) {
        return rows.get(row).get(col);
    }

    /** Builds a grid using the default tolerance, reporting errors for page 0 and table 0. */
    public static TableGrid build(Collection<? extends Cell> cells) {
        return build(cells, DEFAULT_TOLERANCE, 0, 0);
    }

    /** Builds a grid using the given tolerance, reporting errors for page 0 and table 0. */
    public static TableGrid build(Collection<? extends Cell> cells, float tolerance) {
        return build(cells, tolerance, 0, 0);
    }

    /**
     * Builds a grid from the given bordered cells.
     *
     * @param cells      the already-detected bordered cells
     * @param tolerance  maximum spread of a merged group of edge coordinates (must be &gt;= 0)
     * @param pageNumber page number used when reporting mapping errors
     * @param tableIndex zero-based index of the table, used when reporting mapping errors
     */
    public static TableGrid build(Collection<? extends Cell> cells, float tolerance,
                                  int pageNumber, int tableIndex) {
        if (Float.isNaN(tolerance) || Float.isInfinite(tolerance) || tolerance < 0) {
            throw new IllegalArgumentException("tolerance must be a finite number >= 0");
        }

        List<Cell> input = new ArrayList<>();
        if (cells != null) {
            for (Cell cell : cells) {
                if (cell != null) {
                    input.add(cell);
                }
            }
        }

        // Two distinct Cell instances with exactly the same visible edges are the same real
        // box. Deduplicate them in a deterministic order so the result does not depend on the
        // order in which the cells were supplied.
        Collections.sort(input, CELL_ORDER);
        List<Cell> uniqueInput = new ArrayList<>(input.size());
        Cell previous = null;
        for (Cell cell : input) {
            if (previous != null && sameBox(previous, cell)) {
                continue;
            }
            uniqueInput.add(cell);
            previous = cell;
        }
        input = uniqueInput;

        List<Float> xCoordinates = new ArrayList<>(input.size() * 2);
        List<Float> yCoordinates = new ArrayList<>(input.size() * 2);
        for (Cell cell : input) {
            if (!isFinite(cell)) {
                continue;
            }
            xCoordinates.add(cell.getLeft());
            xCoordinates.add(cell.getRight());
            yCoordinates.add(cell.getTop());
            yCoordinates.add(cell.getBottom());
        }

        List<EdgeGroup> xGroups = cluster(xCoordinates, tolerance);
        List<EdgeGroup> yGroups = cluster(yCoordinates, tolerance);

        int colCount = Math.max(0, xGroups.size() - 1);
        int rowCount = Math.max(0, yGroups.size() - 1);

        List<GridError> errors = new ArrayList<>();
        List<Placement> placements = new ArrayList<>();

        for (Cell cell : input) {
            if (!isFinite(cell)) {
                errors.add(new GridError(GridError.Type.ZERO_SPAN, pageNumber, tableIndex,
                        String.format("Page %d, table %d: cell at top=%s left=%s width=%s height=%s "
                                        + "has non-finite geometry",
                                pageNumber, tableIndex + 1,
                                cell.getTop(), cell.getLeft(), cell.getWidth(), cell.getHeight())));
                continue;
            }
            int left = indexOf(xGroups, cell.getLeft());
            int right = indexOf(xGroups, cell.getRight());
            int top = indexOf(yGroups, cell.getTop());
            int bottom = indexOf(yGroups, cell.getBottom());

            int colSpan = right - left;
            int rowSpan = bottom - top;

            if (colSpan <= 0 || rowSpan <= 0) {
                errors.add(new GridError(GridError.Type.ZERO_SPAN, pageNumber, tableIndex,
                        String.format("Page %d, table %d: cell at top=%s left=%s width=%s height=%s "
                                        + "maps to rowSpan=%d colSpan=%d",
                                pageNumber, tableIndex + 1,
                                cell.getTop(), cell.getLeft(), cell.getWidth(), cell.getHeight(),
                                rowSpan, colSpan)));
                continue;
            }
            placements.add(new Placement(cell, top, left, rowSpan, colSpan));
        }

        // The order of the input cells must not influence the output.
        Collections.sort(placements, PLACEMENT_ORDER);

        Placement[][] owner = new Placement[rowCount][colCount];
        Placement[][] realAt = new Placement[rowCount][colCount];
        List<Placement> placed = new ArrayList<>();

        for (Placement placement : placements) {
            boolean overlaps = false;
            for (Placement other : placed) {
                if (overlap(placement, other)) {
                    overlaps = true;
                    errors.add(new GridError(GridError.Type.OVERLAP, pageNumber, tableIndex,
                            String.format("Page %d, table %d: cells at top=%s left=%s width=%s height=%s "
                                            + "and top=%s left=%s width=%s height=%s overlap after grid mapping "
                                            + "([%d,%d] %dx%d and [%d,%d] %dx%d)",
                                    pageNumber, tableIndex + 1,
                                    placement.cell.getTop(), placement.cell.getLeft(),
                                    placement.cell.getWidth(), placement.cell.getHeight(),
                                    other.cell.getTop(), other.cell.getLeft(),
                                    other.cell.getWidth(), other.cell.getHeight(),
                                    placement.row, placement.col,
                                    placement.rowSpan, placement.colSpan,
                                    other.row, other.col, other.rowSpan, other.colSpan)));
                    break;
                }
            }
            if (overlaps) {
                // Keep the grid deterministic and consistent: the first box in the
                // canonical order wins, the overlapping one is only reported.
                continue;
            }
            placed.add(placement);
            realAt[placement.row][placement.col] = placement;
            for (int r = placement.row; r < placement.row + placement.rowSpan && r < rowCount; r++) {
                for (int c = placement.col; c < placement.col + placement.colSpan && c < colCount; c++) {
                    if (owner[r][c] == null) {
                        owner[r][c] = placement;
                    }
                }
            }
        }

        List<List<GridCell>> rows = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            List<GridCell> row = new ArrayList<>(colCount);
            for (int c = 0; c < colCount; c++) {
                Placement real = realAt[r][c];
                if (real != null) {
                    row.add(GridCell.real(r, c, real.cell, real.rowSpan, real.colSpan));
                } else if (owner[r][c] != null) {
                    Placement ownerPlacement = owner[r][c];
                    row.add(GridCell.covered(r, c, ownerPlacement.row, ownerPlacement.col));
                } else {
                    row.add(GridCell.uncovered(r, c));
                }
            }
            rows.add(Collections.unmodifiableList(row));
        }

        return new TableGrid(rowCount, colCount, tolerance,
                Collections.unmodifiableList(rows),
                Collections.unmodifiableList(errors));
    }

    private static boolean isFinite(Cell cell) {
        return isFinite(cell.getTop()) && isFinite(cell.getLeft())
                && isFinite(cell.getRight()) && isFinite(cell.getBottom());
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static boolean overlap(Placement a, Placement b) {
        return a.row < b.row + b.rowSpan && b.row < a.row + a.rowSpan
                && a.col < b.col + b.colSpan && b.col < a.col + a.colSpan;
    }

    private static final class Placement {
        final Cell cell;
        final int row, col, rowSpan, colSpan;

        Placement(Cell cell, int row, int col, int rowSpan, int colSpan) {
            this.cell = cell;
            this.row = row;
            this.col = col;
            this.rowSpan = rowSpan;
            this.colSpan = colSpan;
        }
    }

    private static final class EdgeGroup {
        boolean empty = true;
        float min, max;

        void add(float value) {
            if (empty) {
                min = max = value;
                empty = false;
            } else {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
    }

    private static List<EdgeGroup> cluster(List<Float> coordinates, float tolerance) {
        List<Float> sorted = new ArrayList<>(coordinates);
        Collections.sort(sorted);

        List<EdgeGroup> groups = new ArrayList<>();
        EdgeGroup current = null;
        for (Float value : sorted) {
            if (current == null
                    || ((double) value) - ((double) current.min) > ((double) tolerance)) {
                current = new EdgeGroup();
                groups.add(current);
            }
            current.add(value);
        }
        return groups;
    }

    private static int indexOf(List<EdgeGroup> groups, float value) {
        int low = 0;
        int high = groups.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            EdgeGroup group = groups.get(mid);
            if (value < group.min) {
                high = mid - 1;
            } else if (value > group.max) {
                low = mid + 1;
            } else {
                return mid;
            }
        }
        return -1;
    }
}
