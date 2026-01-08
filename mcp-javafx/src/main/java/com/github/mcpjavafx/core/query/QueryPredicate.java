package com.github.mcpjavafx.core.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Predicate for filtering nodes in queries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryPredicate(
        List<String> typeIs,
        String idEquals,
        String styleClassHas,
        String textContains,
        Boolean visible,
        Boolean enabled) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> typeIs;
        private String idEquals;
        private String styleClassHas;
        private String textContains;
        private Boolean visible;
        private Boolean enabled;

        public Builder typeIs(String... types) {
            this.typeIs = List.of(types);
            return this;
        }

        public Builder idEquals(String id) {
            this.idEquals = id;
            return this;
        }

        public Builder styleClassHas(String styleClass) {
            this.styleClassHas = styleClass;
            return this;
        }

        public Builder textContains(String text) {
            this.textContains = text;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public QueryPredicate build() {
            return new QueryPredicate(typeIs, idEquals, styleClassHas, textContains, visible, enabled);
        }
    }
}
