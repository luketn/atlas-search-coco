package com.mycodefu.mongodb.search;

public final class SearchPlanner {
    private SearchPlanner() {
    }

    public static SearchMode plan(SearchRequest request, boolean vectorSearchAvailable) {
        if (!request.hasMeaningfulText()) {
            return SearchMode.BROWSE;
        }

        if (request.textOnly()) {
            return SearchMode.TEXT;
        }

        if (!vectorSearchAvailable) {
            throw new IllegalStateException("Vector search index is not available.");
        }

        return request.vectorOnly() ? SearchMode.VECTOR : SearchMode.HYBRID;
    }
}
