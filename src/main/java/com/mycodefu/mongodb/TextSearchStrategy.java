package com.mycodefu.mongodb;

import com.mycodefu.model.ImageSearchResult;

final class TextSearchStrategy implements SearchStrategy {
    @Override
    public ImageSearchResult search(ImageDataAccess dataAccess, SearchRequest request) {
        return dataAccess.executeFacetSearch(dataAccess.buildTextSearchOperator(request), request);
    }
}
