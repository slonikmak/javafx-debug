package com.github.mcpjavafx.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Virtualization information for list/table controls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VirtualizationInfo(
        String kind, // "ListView", "TableView", "TreeView", "TreeTableView"
        int itemsCount,
        VisibleRange visibleRange,
        List<Integer> selectedIndices,
        Integer focusedIndex,
        List<ColumnInfo> columns,
        List<VisibleCell> visibleCells) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VisibleRange(int from, int to) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ColumnInfo(String id, String text) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VisibleCell(
            int index,
            NodeRef rowRef,
            List<CellValue> cells) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CellValue(String columnId, String text) {
    }
}
