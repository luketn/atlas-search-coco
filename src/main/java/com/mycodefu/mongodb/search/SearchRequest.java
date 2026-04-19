package com.mycodefu.mongodb.search;

import com.mycodefu.model.SearchType;

public record SearchRequest(
        String text,
        SearchType searchType,
        int page,
        SearchFilters filters,
        boolean includeLicense
) {
    public SearchRequest {
        searchType = searchType == null ? SearchType.Text : searchType;
        page = Math.max(0, page);
        filters = filters == null ? SearchFilters.empty() : filters;
    }

    public static SearchRequest of(
            String text,
            SearchType searchType,
            Integer page,
            SearchFilters filters,
            boolean includeLicense
    ) {
        return new SearchRequest(
                text,
                searchType,
                page == null ? 0 : page,
                filters,
                includeLicense
        );
    }

    public boolean hasMeaningfulText() {
        return text != null && !text.isBlank();
    }

    public boolean is(SearchType expectedSearchType) {
        return searchType == expectedSearchType;
    }

    public boolean returnStoredSource() {
        return !includeLicense;
    }
}
