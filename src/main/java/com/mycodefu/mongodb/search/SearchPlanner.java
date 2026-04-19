package com.mycodefu.mongodb.search;

import com.mycodefu.model.SearchType;

public final class SearchPlanner {
    private SearchPlanner() {
    }

    public static SearchMode plan(SearchRequest request, boolean vectorSearchAvailable) {
        if (!request.hasMeaningfulText()) {
            return SearchMode.TEXT;
        }

        if (request.is(SearchType.Text)) {
            return SearchMode.TEXT;
        }

        if (!vectorSearchAvailable) {
            throw new IllegalStateException("Vector search index is not available.");
        }

        return request.is(SearchType.Vector) ? SearchMode.VECTOR : SearchMode.COMBINED;
    }
}
