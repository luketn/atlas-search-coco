package com.mycodefu.mongodb;

import com.mycodefu.model.SearchType;

import java.util.EnumSet;
import java.util.Set;

public record SearchRequest(
        String text,
        EnumSet<SearchType> searchTypes,
        int page,
        SearchFilters filters,
        double vectorScoreCutoff,
        boolean includeLicense
) {
    public SearchRequest {
        searchTypes = searchTypes == null || searchTypes.isEmpty()
                ? EnumSet.of(SearchType.Text)
                : EnumSet.copyOf(searchTypes);
        page = Math.max(0, page);
        filters = filters == null ? SearchFilters.empty() : filters;
        vectorScoreCutoff = Math.max(0.0, Math.min(1.0, vectorScoreCutoff));
    }

    public static SearchRequest of(
            String text,
            Set<SearchType> searchTypes,
            Integer page,
            SearchFilters filters,
            Double vectorScoreCutoff,
            boolean includeLicense
    ) {
        return new SearchRequest(
                text,
                searchTypes == null || searchTypes.isEmpty() ? EnumSet.of(SearchType.Text) : EnumSet.copyOf(searchTypes),
                page == null ? 0 : page,
                filters,
                vectorScoreCutoff == null ? ImageDataAccess.DEFAULT_VECTOR_SCORE_CUTOFF : vectorScoreCutoff,
                includeLicense
        );
    }

    public boolean hasMeaningfulText() {
        return text != null && !text.isBlank();
    }

    public boolean textOnly() {
        return searchTypes.equals(EnumSet.of(SearchType.Text));
    }

    public boolean vectorOnly() {
        return searchTypes.equals(EnumSet.of(SearchType.Vector));
    }

    public boolean requiresVectorSearch() {
        return searchTypes.contains(SearchType.Vector);
    }

    public boolean returnStoredSource() {
        return !includeLicense;
    }
}
