package technology.tabula.json;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.TableGrid;

public final class TableSerializer implements JsonSerializer<Table> {

    public static final TableSerializer INSTANCE = new TableSerializer();

    /** Name of the optional grid property. */
    public static final String GRID_PROPERTY = "grid";

    private TableSerializer() {}

    @Override
    public JsonElement serialize(Table table, Type type, JsonSerializationContext context) {
        return serializationResult(table, context, null);
    }

    /**
     * Serializes a table, adding the optional {@code grid} property when a grid is supplied.
     *
     * @param table the table to serialize
     * @param grid  the grid reconstructed from the bordered cells, or {@code null} for none
     */
    public JsonElement serializeWithGrid(Table table, TableGrid grid) {
        return serializationResult(table, null, grid);
    }

    private JsonElement serializationResult(Table table, JsonSerializationContext context, TableGrid grid) {
        JsonObject json = new JsonObject();
        JsonArray data = new JsonArray();

        json.addProperty("extraction_method", table.getExtractionMethod());
        json.addProperty("page_number", table.getPageNumber());
        json.addProperty("top", table.getTop());
        json.addProperty("left", table.getLeft());
        json.addProperty("width", table.getWidth());
        json.addProperty("height", table.getHeight());
        json.addProperty("right", table.getRight());
        json.addProperty("bottom", table.getBottom());
        json.add("data", data);

        for (List<RectangularTextContainer> tableRow : table.getRows()) {
            JsonArray jsonRow = new JsonArray();
            for (RectangularTextContainer textChunk : tableRow)
                jsonRow.add(serializeCell(textChunk, context));
            data.add(jsonRow);
        }

        if (grid != null) {
            json.add(GRID_PROPERTY, serializeGrid(grid));
        }

        return json;
    }

    private static JsonElement serializeCell(RectangularTextContainer<?> textChunk, JsonSerializationContext context) {
        if (context != null) {
            return context.serialize(textChunk);
        }
        return RectangularTextContainerSerializer.INSTANCE.serialize(textChunk, null, null);
    }

    private static JsonElement serializeGrid(TableGrid grid) {
        JsonObject json = new JsonObject();
        json.addProperty("row_count", grid.getRowCount());
        json.addProperty("col_count", grid.getColCount());
        json.addProperty("tolerance", grid.getTolerance());

        JsonArray rows = new JsonArray();
        for (List<TableGrid.GridCell> gridRow : grid.getRows()) {
            JsonArray jsonRow = new JsonArray();
            for (TableGrid.GridCell cell : gridRow) {
                jsonRow.add(serializeGridCell(cell));
            }
            rows.add(jsonRow);
        }
        json.add("cells", rows);

        JsonArray errors = new JsonArray();
        for (TableGrid.GridError error : grid.getErrors()) {
            JsonObject jsonError = new JsonObject();
            jsonError.addProperty("type", error.getType().name().toLowerCase());
            jsonError.addProperty("page_number", error.getPageNumber());
            jsonError.addProperty("table_index", error.getTableIndex());
            jsonError.addProperty("table_number", error.getTableNumber());
            jsonError.addProperty("message", error.getMessage());
            errors.add(jsonError);
        }
        json.add("errors", errors);

        return json;
    }

    private static JsonElement serializeGridCell(TableGrid.GridCell cell) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", cell.getKind().name().toLowerCase());
        json.addProperty("row", cell.getRow());
        json.addProperty("col", cell.getCol());

        switch (cell.getKind()) {
            case REAL:
                json.addProperty("rowSpan", cell.getRowSpan());
                json.addProperty("colSpan", cell.getColSpan());
                json.addProperty("text", cell.getText());
                json.addProperty("top", cell.getTop());
                json.addProperty("left", cell.getLeft());
                json.addProperty("width", cell.getWidth());
                json.addProperty("height", cell.getHeight());
                break;
            case COVERED:
                JsonObject reference = new JsonObject();
                reference.addProperty("row", cell.getReferenceRow());
                reference.addProperty("col", cell.getReferenceColumn());
                json.add("reference", reference);
                break;
            default:
                // UNCOVERED: no payload besides kind/row/col.
                break;
        }
        return json;
    }

}
