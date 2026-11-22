package technology.tabula.writers;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import technology.tabula.Cell;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.TableGrid;
import technology.tabula.TableWithRulingLines;
import technology.tabula.TextChunk;
import technology.tabula.json.RectangularTextContainerSerializer;
import technology.tabula.json.TableSerializer;

import java.io.IOException;
import java.util.List;

import static java.lang.reflect.Modifier.PUBLIC;

public class JSONWriter implements Writer {

    private static final ExclusionStrategy ALL_CLASSES_SKIPPING_NON_PUBLIC_FIELDS = new ExclusionStrategy() {
        @Override
        public boolean shouldSkipClass(Class<?> c) {
            return false;
        }

        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            return !fieldAttributes.hasModifier(PUBLIC);
        }
    };

    private final boolean includeGrid;
    private final float gridTolerance;

    /**
     * The default writer: produces the same JSON as always, without the optional grid.
     */
    public JSONWriter() {
        this(false, TableGrid.DEFAULT_TOLERANCE);
    }

    /**
     * @param includeGrid whether to add the optional grid reconstructed from bordered cells
     */
    public JSONWriter(boolean includeGrid) {
        this(includeGrid, TableGrid.DEFAULT_TOLERANCE);
    }

    /**
     * @param includeGrid   whether to add the optional grid reconstructed from bordered cells
     * @param gridTolerance maximum spread, in PDF points, of a merged group of edge coordinates
     */
    public JSONWriter(boolean includeGrid, float gridTolerance) {
        if (Float.isNaN(gridTolerance) || Float.isInfinite(gridTolerance) || gridTolerance < 0) {
            throw new IllegalArgumentException("gridTolerance must be a finite number >= 0");
        }
        this.includeGrid = includeGrid;
        this.gridTolerance = gridTolerance;
    }

    public boolean isIncludeGrid() {
        return includeGrid;
    }

    public float getGridTolerance() {
        return gridTolerance;
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    @Override
    public void write(Appendable out, Table table) throws IOException {
        Gson gson = gson();
        if (!this.includeGrid) {
            out.append(gson.toJson(table, Table.class));
            return;
        }
        out.append(gson.toJson(serializeTable(table, 0)));
    }

    @Override
    public void write(Appendable out, List<Table> tables) throws IOException {
        Gson gson = gson();
        JsonArray jsonElements = new JsonArray();
        for (int i = 0; i < tables.size(); i++) {
            Table table = tables.get(i);
            if (this.includeGrid) {
                jsonElements.add(serializeTable(table, i));
            } else {
                jsonElements.add(gson.toJsonTree(table, Table.class));
            }
        }
        out.append(gson.toJson(jsonElements));
    }

    private JsonElement serializeTable(Table table, int tableIndex) {
        TableSerializer serializer = TableSerializer.INSTANCE;
        if (table instanceof TableWithRulingLines) {
            TableGrid grid = ((TableWithRulingLines) table).getGrid(this.gridTolerance, tableIndex);
            return serializer.serializeWithGrid(table, grid);
        }
        return serializer.serializeWithGrid(table, null);
    }

    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - //
    private static Gson gson() {
        return new GsonBuilder()
                .addSerializationExclusionStrategy(ALL_CLASSES_SKIPPING_NON_PUBLIC_FIELDS)
                .registerTypeAdapter(Table.class, TableSerializer.INSTANCE)
                .registerTypeAdapter(RectangularTextContainer.class, RectangularTextContainerSerializer.INSTANCE)
                .registerTypeAdapter(Cell.class, RectangularTextContainerSerializer.INSTANCE)
                .registerTypeAdapter(TextChunk.class, RectangularTextContainerSerializer.INSTANCE)
                .create();
    }

}
