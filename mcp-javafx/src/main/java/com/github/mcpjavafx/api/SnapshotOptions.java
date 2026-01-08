package com.github.mcpjavafx.api;

/**
 * Options for UI snapshot capture.
 *
 * @param depth                 maximum tree traversal depth
 * @param includeBounds         include boundsInParent/boundsInScene
 * @param includeLocalToScreen  include screen coordinates
 * @param includeProperties     include fx properties
 * @param includeVirtualization include virtualization info for list/table
 *                              controls
 * @param includeAccessibility  include accessibility info
 */
public record SnapshotOptions(
        int depth,
        boolean includeBounds,
        boolean includeLocalToScreen,
        boolean includeProperties,
        boolean includeVirtualization,
        boolean includeAccessibility) {
    public static final SnapshotOptions DEFAULT = new SnapshotOptions(
            50, // depth
            true, // includeBounds
            true, // includeLocalToScreen
            false, // includeProperties
            true, // includeVirtualization
            false // includeAccessibility
    );

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int depth = 50;
        private boolean includeBounds = true;
        private boolean includeLocalToScreen = true;
        private boolean includeProperties = false;
        private boolean includeVirtualization = true;
        private boolean includeAccessibility = false;

        public Builder depth(int depth) {
            this.depth = depth;
            return this;
        }

        public Builder includeBounds(boolean includeBounds) {
            this.includeBounds = includeBounds;
            return this;
        }

        public Builder includeLocalToScreen(boolean includeLocalToScreen) {
            this.includeLocalToScreen = includeLocalToScreen;
            return this;
        }

        public Builder includeProperties(boolean includeProperties) {
            this.includeProperties = includeProperties;
            return this;
        }

        public Builder includeVirtualization(boolean includeVirtualization) {
            this.includeVirtualization = includeVirtualization;
            return this;
        }

        public Builder includeAccessibility(boolean includeAccessibility) {
            this.includeAccessibility = includeAccessibility;
            return this;
        }

        public SnapshotOptions build() {
            return new SnapshotOptions(
                    depth, includeBounds, includeLocalToScreen,
                    includeProperties, includeVirtualization, includeAccessibility);
        }
    }
}
