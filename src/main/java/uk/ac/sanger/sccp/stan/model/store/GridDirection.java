package uk.ac.sanger.sccp.stan.model.store;

/**
 * A traversal order for a 2D grid.
 */
public enum GridDirection {
    /**
     * Right across the top row, then down to the next row, etc.
     */
    RightDown,
    /**
     * Down the leftmost column, then right to the next column, etc.
     */
    DownRight,
    ;
}
